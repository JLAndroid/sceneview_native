// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.utils

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import java.util.Locale

/** Timing of presented frames; frameIntervalMs is not GPU execution time. */
class DebugStats {
    var fps = 0f
        private set
    var frameIntervalMs = 0f
        private set
    var nodeCount = 0
        private set
    private var previous = 0L
    private var elapsed = 0L
    private var frames = 0
    fun onFrame(time: Long, nodeCount: Int = 0) {
        this.nodeCount = nodeCount
        if (previous != 0L) {
            val delta = time - previous
            if (delta in 1..500_000_000L) {
                frameIntervalMs = delta / 1_000_000f
                elapsed += delta
                frames++
                if (elapsed >= 500_000_000L) {
                    fps = frames * 1_000_000_000f / elapsed
                    elapsed = 0; frames = 0
                }
            } else { elapsed = 0; frames = 0; fps = 0f }
        }
        previous = time
    }
}

/** Plain TextView. Call onFrame from SceneView.onFrame; use a normal overlay layout. */
class DebugOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : TextView(context, attrs, defStyleAttr) {
    val stats = DebugStats()
    private var lastRefresh = 0L
    init {
        setTextColor(Color.WHITE)
        setBackgroundColor(0xB0202020.toInt())
        val padding = (8 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        textSize = 12f
    }
    fun onFrame(time: Long, nodeCount: Int = 0) {
        stats.onFrame(time, nodeCount)
        if (time - lastRefresh >= 250_000_000L) {
            text = String.format(Locale.ROOT, "%.1f FPS · %.1f ms · %d nodes",
                stats.fps, stats.frameIntervalMs, stats.nodeCount)
            lastRefresh = time
        }
    }
}
