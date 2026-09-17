// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.audio

import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.length
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Native node with positional audio. Update the listener each frame from your active camera. */
class SpatialAudioNode(
    engine: Engine,
    source: AudioSource,
    falloff: AudioFalloff = AudioFalloff.Inverse(refDistance = 1f, maxDistance = 100f),
    loop: Boolean = false,
    autoPlay: Boolean = false,
    volume: Float = 1f,
    pitch: Float = 1f,
) : Node(engine), AudioController {
    private val player = SpatialAudioPlayer(source, falloff, loop, volume, pitch)
    override val isPlaying: StateFlow<Boolean> = player.isPlayingState.asStateFlow()
    var falloff: AudioFalloff
        get() = player.falloff
        set(value) { player.falloff = value }
    private var sceneActive = false
    private var playbackRequested = autoPlay
    init {
        player.onPlaybackFinished = { playbackRequested = false }
        SpatialAudioEngine.register(player)
    }
    override fun play() { playbackRequested = true; if (sceneActive) player.play() }
    override fun pause() { playbackRequested = false; player.pause() }
    override fun stop() { playbackRequested = false; player.stop() }
    internal fun setSceneActive(active: Boolean) {
        if (isDestroyed || sceneActive == active) return
        sceneActive = active
        if (active && playbackRequested) player.play() else player.pause()
    }
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun setVolume(value: Float) = player.setBaseVolume(value)
    fun setPitch(value: Float) = player.setPitch(value)
    fun setLoop(value: Boolean) = player.setLoop(value)
    override fun onFrame(frameTimeNanos: Long) {
        if (isDestroyed) return
        player.sourcePosition = worldPosition
        setSceneActive(true)
        super.onFrame(frameTimeNanos)
    }
    override fun destroy() {
        if (isDestroyed) return
        player.destroy()
        super.destroy()
    }
}

fun setSpatialAudioListenerPose(position: Position, forward: Position, up: Position) {
    val vector = Float3(forward.x, forward.y, forward.z)
    val direction = if (length(vector) > 1e-4f) normalize(vector) else Position(z = -1f)
    SpatialAudioEngine.setListenerPose(position, direction, rightFromForwardUp(direction, up))
}
