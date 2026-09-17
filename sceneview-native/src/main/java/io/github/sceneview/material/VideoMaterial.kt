// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview.material

import android.graphics.SurfaceTexture
import io.github.sceneview.utils.createDetachedSurfaceTexture
import android.view.Surface
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Stream
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.EngineDestroyQueue
import io.github.sceneview.texture.VideoTexture

class VideoMaterial(
    val engine: Engine,
    private val materialLoader: MaterialLoader,
    chromaKeyColor: Int? = null
) {
    /**
     * Images drawn to the Surface will be made available to the Filament Stream.
     */
    val surfaceTexture = createDetachedSurfaceTexture()
    private var destroyed = false

    /**
     * The Android surface.
     */
    val surface = Surface(surfaceTexture)

    /**
     * The Filament Stream.
     */
    val stream = Stream.Builder()
        .stream(surfaceTexture)
        .build(engine)

    /**
     * The Filament Texture diffusing the stream.
     */
    val texture = VideoTexture.Builder()
        .stream(stream)
        .build(engine)

    val instance: MaterialInstance = materialLoader.createVideoInstance(texture, chromaKeyColor)

    init {
        instance.setExternalTexture(texture)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        // Caller must detach any renderables using this instance before destroy().
        materialLoader.destroyMaterialInstance(instance)
        EngineDestroyQueue.of(engine).enqueueTexture(texture)
        EngineDestroyQueue.of(engine).enqueueStream(stream)
        surface.release()
        surfaceTexture.release()
    }
}
