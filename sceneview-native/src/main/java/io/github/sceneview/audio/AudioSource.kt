// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import androidx.annotation.MainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opaque, *shareable* handle to an audio asset that can be played by one or more
 * [SpatialAudioNode]s.
 *
 * An [AudioSource] holds only the cheap, immutable description of the asset (its `assets`
 * path plus a snapshot of its duration). It deliberately does **not** own an
 * [android.media.MediaPlayer] — a `MediaPlayer` is a single playback bus and cannot be
 * shared between two nodes without volume / start / pause / seek cross-talk. Each
 * [SpatialAudioPlayer] therefore constructs its **own** `MediaPlayer` from this source via
 * [openFd], so two `SpatialAudioNode`s backed by the same source play independently.
 *
 * Because the source carries no native resources it is itself free to share, re-use across
 * recompositions, and pass to several nodes at once.
 *
 * Phase 1 backs playback with Android [android.media.MediaPlayer] — it gives us file-format
 * coverage (WAV / MP3 / OGG / FLAC), seeking, looping and pitch control with zero
 * asset-pipeline work. The phase-2 Spatializer path will instead decode the asset to a PCM
 * buffer and stream it through `AudioTrack`; the public API of `AudioSource` is
 * intentionally minimal so swapping the backing engine does not break callers.
 *
 * Always obtain one with [loadAudioSource]; never construct it directly.
 *
 * @property durationMs Total duration of the asset in milliseconds. `0` if unknown.
 */
class AudioSource internal constructor(
    internal val context: Context,
    internal val assetPath: String,
    internal val durationMs: Long,
) {
    /**
     * Opens a fresh [AssetFileDescriptor] for the backing asset. Each [SpatialAudioPlayer]
     * calls this to feed its own private `MediaPlayer.setDataSource`. The caller owns the
     * returned descriptor and must close it once `setDataSource` has consumed it.
     *
     * Cheap, but performs a file-system lookup — call it off the main thread.
     */
    internal fun openFd(): AssetFileDescriptor = context.assets.openFd(assetPath)
}

/** Load immutable audio metadata off the main thread; failures report duration zero. */
suspend fun loadAudioSource(context: Context, assetPath: String): AudioSource =
    withContext(Dispatchers.IO) {
        AudioSource(context.applicationContext, assetPath, probeDurationMs(context, assetPath))
    }

/**
 * Reads the asset duration with a short-lived `MediaMetadataRetriever`. Returns `0` when
 * the duration cannot be determined. Safe to call on any thread.
 */
private fun probeDurationMs(context: Context, assetPath: String): Long {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        context.assets.openFd(assetPath).use { afd ->
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        }
        retriever
            .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
    } catch (t: Throwable) {
        Log.w(TAG, "Could not read duration for $assetPath: ${t.message}")
        0L
    } finally {
        runCatching { retriever.release() }
    }
}

internal const val TAG = "SpatialAudio"
