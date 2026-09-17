// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.utils

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build

/** Creates a texture detached from GL so Filament can attach it on its rendering thread. */
internal fun createDetachedSurfaceTexture(): SurfaceTexture {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return SurfaceTexture(false)

    // API 21-25 only has the attached constructor. Use a temporary context, then detach.
    // Preserve the calling thread's EGL state; never terminate the display shared by Filament.
    val previousDisplay = EGL14.eglGetCurrentDisplay()
    val previousContext = EGL14.eglGetCurrentContext()
    val previousDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
    val previousRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
    val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, null, 0, null, 0)) {
        "Cannot initialize EGL for SurfaceTexture"
    }
    var context = EGL14.EGL_NO_CONTEXT
    var surface = EGL14.EGL_NO_SURFACE
    var current = false
    var result: SurfaceTexture? = null
    val textureId = IntArray(1)
    try {
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        ), 0, configs, 0, 1, count, 0) && count[0] > 0) { "No EGL pbuffer config" }
        val config = checkNotNull(configs[0])
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "Cannot create temporary EGL context" }
        surface = EGL14.eglCreatePbufferSurface(display, config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(surface != EGL14.EGL_NO_SURFACE) { "Cannot create EGL pbuffer" }
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "Cannot bind temporary EGL context" }
        current = true
        GLES20.glGenTextures(1, textureId, 0)
        check(textureId[0] != 0) { "Cannot allocate external texture" }
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId[0])
        val texture = SurfaceTexture(textureId[0])
        result = texture
        texture.detachFromGLContext() // Also deletes the temporary GL texture name.
        textureId[0] = 0
        return texture
    } catch (error: Throwable) {
        result?.release()
        result = null
        throw error
    } finally {
        if (current && textureId[0] != 0) GLES20.glDeleteTextures(1, textureId, 0)
        val restored = !current || if (previousContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglMakeCurrent(previousDisplay, previousDraw, previousRead, previousContext)
        } else {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        }
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        if (!restored) {
            result?.release()
            error("Cannot restore EGL state after SurfaceTexture creation")
        }
    }
}
