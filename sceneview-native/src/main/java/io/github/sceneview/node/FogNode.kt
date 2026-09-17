// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.node

import com.google.android.filament.View
import io.github.sceneview.math.Color
import io.github.sceneview.math.colorOf

/** Per-view fog controller. Call update after changing parameters; close restores prior options. */
class FogNode(private val view: View) : AutoCloseable {
    private val previous = view.fogOptions
    private var closed = false
    fun update(
        density: Float = 0.05f,
        heightFalloff: Float = 1f,
        color: Color = colorOf(r = 0.8f, g = 0.867f, b = 1f),
        enabled: Boolean = true,
    ) {
        check(!closed)
        val rgb = floatArrayOf(color.x, color.y, color.z)
        view.fogOptions = View.FogOptions().apply {
            this.enabled = enabled
            this.density = density.coerceIn(0f, 1f)
            this.heightFalloff = heightFalloff
            distance = 0.5f
            cutOffDistance = 40f
            fogColorFromIbl = false
            this.color = rgb
        }
    }
    override fun close() {
        if (closed) return
        closed = true
        view.fogOptions = previous
    }
}
