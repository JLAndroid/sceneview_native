package io.github.sceneview.math

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Mat4
import io.github.sceneview.animation.SmoothTransformTRSState
import io.github.sceneview.animation.SmoothTransformTRSTarget
import io.github.sceneview.animation.updateSmoothTransform
import org.junit.Assert.*
import org.junit.Test

class Kotlin19CompatibilityTest {
    @Test fun vectorToleranceIncludesBoundaryAndRejectsNaN() {
        assertTrue(Float3(0f).equals(Float3(0.125f), 0.125f))
        assertFalse(Float3(0f).equals(Float3(0f, 0f, 0.25f), 0.125f))
        assertFalse(Float3(0f).equals(Float3(Float.NaN, 0f, 0f), 0.125f))
    }

    @Test fun matrixToleranceChecksEveryComponent() {
        for (column in 0..3) for (row in 0..3) {
            val initial = Mat4()
            val changed = Mat4()
            changed[column][row] += 0.125f
            assertTrue(initial.equals(changed, 0.125f))
            assertFalse(initial.equals(changed, 0.0625f))
        }
    }

    @Test fun smoothAnimationMovesAndEventuallySnapsToTarget() {
        val target = SmoothTransformTRSTarget(Position(3f, 2f, 1f),
            dev.romainguy.kotlin.math.Quaternion(), Scale(2f))
        var state = SmoothTransformTRSState(Position(0f), target = target)
        val first = updateSmoothTransform(state, 1.0 / 60.0)
        assertFalse(first.arrived)
        assertTrue(first.state.position.x > 0f && first.state.position.x < 3f)
        state = first.state
        var arrived = false
        repeat(600) {
            val next = updateSmoothTransform(state, 1.0 / 60.0)
            arrived = arrived || next.arrived
            state = next.state
        }
        assertTrue(arrived)
        assertNull(state.target)
        assertEquals(target.position, state.position)
        assertEquals(target.scale, state.scale)
    }
}
