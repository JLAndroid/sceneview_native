// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.gesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import io.github.sceneview.SceneView
import io.github.sceneview.utils.worldToScreen
import java.util.Locale

/**
 * Native Canvas editing badge. Place at the exact same bounds as SceneView, above it.
 * Set sceneView/feedback, then call invalidate() from SceneView.onFrame. Does not consume touches.
 * This is a simple native badge, not a pixel-for-pixel copy of the upstream Compose overlay.
 */
class NodeEditingOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    var sceneView: SceneView? = null
        set(value) { field = value; invalidate() }
    var feedback: NodeEditingFeedbackState? = null
        set(value) { field = value; invalidate() }
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13 * resources.displayMetrics.scaledDensity
        strokeWidth = 2 * density
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scene = sceneView?.takeUnless { it.isDestroyed } ?: return
        val state = feedback?.takeUnless { it.node.isDestroyed } ?: return
        if (!state.isPressed && !state.isEditing) return
        val point = scene.view.worldToScreen(state.node.worldPosition) ?: return
        paint.style = Paint.Style.STROKE
        paint.color = if (state.scaleLimit != null) Color.YELLOW else Color.WHITE
        canvas.drawCircle(point.x, point.y, 24 * density, paint)
        paint.style = Paint.Style.FILL
        canvas.drawText(String.format(Locale.ROOT, "%.0f%%  %.0f°", state.scalePercent, state.yawDegrees),
            point.x + 30 * density, point.y, paint)
    }
}
