// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.gesture

import android.view.MotionEvent

/** Node callback available on API 21 without linking the platform's API 23 interface. */
fun interface OnContextClickListener {
    fun onContextClick(e: MotionEvent): Boolean
}
