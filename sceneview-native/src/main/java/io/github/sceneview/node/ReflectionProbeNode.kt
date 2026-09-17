// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.node

import com.google.android.filament.Scene
import dev.romainguy.kotlin.math.length
import io.github.sceneview.environment.Environment
import io.github.sceneview.math.Position

/** Single active IBL zone. Caller owns environment; call update as the camera moves. */
class ReflectionProbeNode(
    private val filamentScene: Scene,
    val environment: Environment,
    var position: Position = Position(0f),
    var radius: Float = 0f,
) : AutoCloseable {
    private val previous = filamentScene.indirectLight
    private var active = false
    private var closed = false
    fun update(cameraPosition: Position) {
        check(!closed)
        val next = radius <= 0f || length(cameraPosition - position) <= radius
        if (next == active) return
        active = next
        if (active) filamentScene.indirectLight = environment.indirectLight
        else if (filamentScene.indirectLight === environment.indirectLight) filamentScene.indirectLight = previous
    }
    override fun close() {
        if (closed) return
        closed = true
        if (active && filamentScene.indirectLight === environment.indirectLight) filamentScene.indirectLight = previous
    }
}
