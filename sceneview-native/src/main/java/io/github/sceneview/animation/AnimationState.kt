// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.animation

import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class ModelAnimationSnapshot(
    val playingAnimationCount: Int = 0,
    val currentAnimationIndex: Int = -1,
    val currentAnimationName: String? = null,
    val progress: Float = 0f,
    val isPlaying: Boolean = false,
)

/** Native animation observer; collect snapshots with lifecycleScope, or read current values. */
class ModelAnimationState(private val modelNode: ModelNode) : AutoCloseable {
    private val mutable = MutableStateFlow(ModelAnimationSnapshot())
    val snapshots: StateFlow<ModelAnimationSnapshot> = mutable.asStateFlow()
    val playingAnimationCount get() = mutable.value.playingAnimationCount
    val currentAnimationIndex get() = mutable.value.currentAnimationIndex
    val currentAnimationName get() = mutable.value.currentAnimationName
    val progress get() = mutable.value.progress
    val isPlaying get() = mutable.value.isPlaying
    private val subscription = modelNode.addFrameListener(::update)
    init { modelNode.ownBinding(this) }

    private fun update(time: Long) {
        val playing = modelNode.playingAnimations
        val first = playing.entries.firstOrNull()
        if (first == null) { mutable.value = ModelAnimationSnapshot(); return }
        val (index, animation) = first
        val duration = modelNode.animator.getAnimationDuration(index)
        val elapsed = ((time - animation.startTime) / 1_000_000_000.0 * abs(animation.speed)).toFloat()
        val progress = if (duration <= 0f) 0f else if (animation.loop) {
            (elapsed % duration) / duration
        } else (elapsed / duration).coerceIn(0f, 1f)
        mutable.value = ModelAnimationSnapshot(playing.size, index,
            modelNode.animator.getAnimationName(index), progress, true)
    }
    override fun close() = subscription.close()
}
