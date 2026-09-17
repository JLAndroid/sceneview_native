// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Imperative control surface for a [SpatialAudioNode].
 *
 * The composable exposes a controller through its `apply` lambda so callers can drive
 * playback (mute on tap, scrub a timeline, react to game state) from outside the Compose
 * tree without rebuilding the node.
 *
 * Methods are safe to call from the main thread only — every implementation forwards to
 * `MediaPlayer` / `AudioTrack`, both of which are `@MainThread`.
 *
 * Native usage examples: see README.md.
 */
interface AudioController {

    /** Starts (or resumes) playback. Idempotent — calling twice has no extra effect. */
    fun play()

    /** Pauses playback at the current position. */
    fun pause()

    /** Stops playback and rewinds to position `0`. */
    fun stop()

    /**
     * Seeks to [positionMs] (milliseconds). Clamped to `[0, durationMs]`.
     *
     * On the Spatializer path the seek is best-effort (an `AudioTrack` only supports
     * loop-point seeks); on the MediaPlayer fallback the seek is sample-accurate.
     */
    fun seekTo(positionMs: Long)

    /** Reactive Compose state — `true` while audio is actively playing. */
    val isPlaying: StateFlow<Boolean>
}
