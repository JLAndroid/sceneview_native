// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package local.sceneview.sample

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.texture.ImageTexture

/** XML, normal Views and callbacks only; no Compose dependencies. */
class MainActivity : ComponentActivity() {
    private lateinit var scene: SceneView
    private var model: ModelNode? = null
    private var originalMaterials: List<List<MaterialInstance>> = emptyList()
    private var checkerMaterial: MaterialInstance? = null
    private var useChecker = false
    private var playing = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        scene = findViewById(R.id.scene)
        scene.bindLifecycle(lifecycle)

        val controls = findViewById<View>(R.id.controls)
        val padding = controls.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(controls) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(padding + bars.left, padding, padding + bars.right, padding + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(controls)

        val status = findViewById<TextView>(R.id.status)
        val animationButton = findViewById<Button>(R.id.animation)
        val textureButton = findViewById<Button>(R.id.texture)
        scene.onRenderError = { status.text = "渲染已暂停：${it.message}" }
        scene.loadModelInstance("models/learning_cube.glb", onError = {
            status.text = "加载失败：${it.message}"
        }) { instance ->
            val node = ModelNode(instance, autoAnimate = false, scaleToUnits = 1.5f,
                centerOrigin = Position(0f))
            model = node
            originalMaterials = node.materialInstances.map { it.toList() }
            scene.addChildNode(node)
            node.playAnimation("Spin", loop = true)
            animationButton.isEnabled = true
            textureButton.isEnabled = true
            status.text = "GLB 已加载 · 动画：Spin · 透明 TextureView"
        }
        animationButton.setOnClickListener {
            val node = model ?: return@setOnClickListener
            playing = !playing
            if (playing) node.playAnimation("Spin", loop = true) else node.stopAnimation("Spin")
            animationButton.text = if (playing) "停止 Spin 动画" else "重新播放 Spin 动画"
        }
        textureButton.setOnClickListener {
            val node = model ?: return@setOnClickListener
            if (checkerMaterial == null) {
                // This tiny image is local. Decode large user images off the main thread.
                val texture = scene.ownTexture(ImageTexture.Builder()
                    .bitmap(assets, "textures/checker.png").build(scene.engine))
                checkerMaterial = scene.materialLoader.createTextureInstance(texture)
            }
            useChecker = !useChecker
            if (useChecker) {
                val material = checkNotNull(checkerMaterial)
                node.renderableNodes.forEach { it.setMaterialInstances(material) }
            } else node.materialInstances = originalMaterials
            // The material loader owns both the replacement instance and its material.
            // ownTexture defers texture release until all nodes/materials are released.
            scene.requestRender()
        }
        val overlay = findViewById<View>(R.id.overlay)
        findViewById<Button>(R.id.openOverlay).setOnClickListener { overlay.visibility = View.VISIBLE }
        findViewById<Button>(R.id.closeOverlay).setOnClickListener { overlay.visibility = View.GONE }
    }

    override fun onDestroy() {
        // Idempotent even if the Lifecycle observer already called destroy().
        if (::scene.isInitialized) scene.destroy()
        model = null
        checkerMaterial = null
        originalMaterials = emptyList()
        super.onDestroy()
    }
}
