// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview

import android.content.Context
import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.google.android.filament.Texture
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import io.github.sceneview.environment.Environment
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.ViewNode

/** Transactional acquisition, inverse-order release, including partial initialization failures. */
internal class NativeSceneResources(context: Context, sharedEngine: Engine?, opaque: Boolean) : AutoCloseable {
    private val cleanup = ArrayDeque<() -> Unit>()
    private var closed = false
    private val ownedTextures = linkedSetOf<Texture>()
    fun ownTexture(texture: Texture): Texture {
        check(!closed)
        ownedTextures += texture
        return texture
    }
    private fun <T> own(value: T, release: (T) -> Unit): T {
        cleanup.addFirst { release(value) }
        return value
    }
    val engine: Engine
    val view: View
    val scene: com.google.android.filament.Scene
    val renderer: Renderer
    val materialLoader: MaterialLoader
    val modelLoader: ModelLoader
    val environmentLoader: EnvironmentLoader
    var environment: Environment
    val cameraNode: CameraNode
    val mainLightNode: LightNode
    val fillLightNode: LightNode
    val contentRoot: Node
    val collisionSystem: io.github.sceneview.collision.CollisionSystem
    val nodeManager: SceneNodeManager
    val viewNodeWindowManager: ViewNode.WindowManager
    val sceneRenderer: SceneRenderer

    init {
        try {
            Filament.init(); Gltfio.init(); Utils.init()
            engine = sharedEngine ?: own(Engine.create(Engine.Backend.OPENGL)) { it.safeDestroy().getOrThrow() }
            own(Unit) {
                engine.flushAndWait()
                repeat(EngineDestroyQueue.GRACE_FRAMES) { EngineDestroyQueue.of(engine).drain() }
            }
            scene = own(createScene(engine)) { engine.destroyScene(it) }
            view = own(createView(engine)) { engine.destroyView(it) }
            view.colorGrading?.let { grading -> own(grading) {
                if (view.colorGrading === it) view.colorGrading = null
                engine.destroyColorGrading(it)
            } }
            renderer = own(createRenderer(engine)) { engine.destroyRenderer(it) }
            // Release model/material instances before reclaiming their caller-created textures.
            own(ownedTextures) { textures ->
                textures.forEach { EngineDestroyQueue.of(engine).enqueueTexture(it) }
                textures.clear()
            }
            materialLoader = own(MaterialLoader(engine, context)) { it.destroy() }
            modelLoader = own(ModelLoader(engine, context)) { it.destroy() }
            environmentLoader = own(EnvironmentLoader(engine, context)) { it.destroy() }
            environment = environmentLoader.createKTX1Environment(
                iblAssetFile = "environments/neutral/neutral_ibl.ktx", skyboxAssetFile = null
            ).also { it.indirectLight?.intensity = DEFAULT_IBL_INTENSITY }
            cameraNode = own(createCameraNode(engine)) { it.destroy() }
            mainLightNode = own(createMainLightNode(engine)) { it.destroy() }
            fillLightNode = own(createFillLightNode(engine)) { it.destroy() }
            contentRoot = own(Node(engine)) { it.destroy() }
            view.scene = scene
            view.camera = cameraNode.camera
            view.blendMode = if (opaque) View.BlendMode.OPAQUE else View.BlendMode.TRANSLUCENT
            renderer.clearOptions = Renderer.ClearOptions().apply {
                clear = true
                clearColor = doubleArrayOf(0.0, 0.0, 0.0, if (opaque) 1.0 else 0.0)
            }
            scene.indirectLight = environment.indirectLight
            scene.skybox = environment.skybox
            cameraNode.setView(view)
            collisionSystem = createCollisionSystem(view)
            nodeManager = SceneNodeManager(scene, collisionSystem)
            listOf(cameraNode, mainLightNode, fillLightNode, contentRoot).forEach(nodeManager::addNode)
            own(nodeManager) { manager ->
                listOf(contentRoot, cameraNode, mainLightNode, fillLightNode).forEach(manager::removeNode)
                scene.indirectLight = null
                scene.skybox = null
                view.camera = null
                view.scene = null
            }
            viewNodeWindowManager = own(createViewNodeManager(context)) { it.destroy() }
            sceneRenderer = own(SceneRenderer(engine, view, renderer)) { it.destroy() }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }
    override fun close() {
        if (closed) return
        closed = true
        while (cleanup.isNotEmpty()) {
            runCatching { cleanup.removeFirst().invoke() }
                .onFailure { Log.e("NativeSceneView", "Resource cleanup failed", it) }
        }
    }
}
