// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.
package io.github.sceneview

import android.content.Context
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.android.filament.Engine
import io.github.sceneview.audio.SpatialAudioNode
import io.github.sceneview.audio.setSpatialAudioListenerPose
import io.github.sceneview.collision.HitResult
import io.github.sceneview.environment.Environment
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.gesture.GestureDetector
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.Node
import io.github.sceneview.utils.SurfaceMirrorer
import io.github.sceneview.utils.intervalSeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Native Android frontend for the 4.35.0 rendering core, with no Compose runtime.
 *
 * Main-thread only. XML defaults to transparent TextureView. Bind to the Fragment's VIEW
 * lifecycle. Temporary detach pauses rendering without discarding models; ON_DESTROY calls
 * [destroy]. Without a LifecycleOwner the caller must call [destroy] explicitly.
 *
 * addChildNode transfers node lifecycle ownership to this view. removeChildNode without
 * destroy transfers ownership back to the caller. External engines/environments are borrowed.
 * See README.md for lifecycle, resource ownership and loading examples.
 */
@MainThread
open class SceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    sharedEngine: Engine? = null,
    sharedLifecycle: Lifecycle? = null,
    surfaceType: SurfaceType = SurfaceType.TextureSurface,
    opaque: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttr), DefaultLifecycleObserver {
    init { checkMainThread() }

    private val config = context.obtainStyledAttributes(attrs, R.styleable.SceneView, defStyleAttr, 0)
    val surfaceType = SurfaceType.entries[config.getInt(
        R.styleable.SceneView_svSurfaceType, surfaceType.ordinal
    ).coerceIn(0, SurfaceType.entries.lastIndex)]
    val isSceneOpaque = config.getBoolean(R.styleable.SceneView_svOpaque, opaque)
    private val initialAutoCenter = config.getBoolean(R.styleable.SceneView_svAutoCenter, true)
    private val initialAutoFit = config.getBoolean(R.styleable.SceneView_svAutoFit, false)
    private val initialQuality = RenderQuality.entries[config.getInt(
        R.styleable.SceneView_svRenderQuality, RenderQuality.Default.ordinal
    ).coerceIn(0, RenderQuality.entries.lastIndex)].also { config.recycle() }

    // Lazy resources let the XML editor inflate without trying to load native libraries.
    private val resourceDelegate = lazy(LazyThreadSafetyMode.NONE) {
        NativeSceneResources(context, sharedEngine, isSceneOpaque)
    }
    private val state by resourceDelegate
    val engine get() = state.engine
    val view get() = state.view
    val scene get() = state.scene
    val renderer get() = state.renderer
    val modelLoader get() = state.modelLoader
    val materialLoader get() = state.materialLoader
    val environmentLoader get() = state.environmentLoader
    val collisionSystem get() = state.collisionSystem
    val viewNodeWindowManager get() = state.viewNodeWindowManager
    val cameraNode get() = state.cameraNode
    val mainLightNode get() = state.mainLightNode
    val fillLightNode get() = state.fillLightNode
    private val contentRoot get() = state.contentRoot
    val childNodes: List<Node> get() = contentRoot.childNodes.toList()

    var environment: Environment
        get() = state.environment
        set(value) {
            requireAlive()
            state.environment = value
            scene.indirectLight = value.indirectLight
            scene.skybox = value.skybox
            requestRender()
        }
    var renderQuality = initialQuality
        set(value) { requireAlive(); field = value; view.applyRenderQuality(value); requestRender() }
    var autoCenterContent = initialAutoCenter
        set(value) {
            requireAlive(); field = value
            contentRoot.position = Position(0f)
            resetFraming()
        }
    var autoFitContent = initialAutoFit
        set(value) {
            requireAlive(); field = value
            if (value) cameraManipulator = null
            resetFraming()
        }
    var framingPadding = DEFAULT_FRAMING_PADDING
        set(value) { requireAlive(); require(value >= 0f); field = value; resetFraming() }
    /** False parks the frame loop. Mutations need requestRender() or resumed rendering. */
    var isRendering = true
        set(value) { requireAlive(); field = value; updateFrameScheduling() }
    var cameraManipulator: CameraGestureDetector.CameraManipulator? = null
        set(value) {
            requireAlive(); field = value
            cameraGestureDetector?.cameraManipulator = value
            if (surfaceView.width > 0 && surfaceView.height > 0) {
                value?.setViewport(surfaceView.width, surfaceView.height)
            }
            requestRender()
        }
    var cameraGestureDetector: CameraGestureDetector? = null
        private set
    val gestureDetector = GestureDetector(context, null)
    var onGestureListener: GestureDetector.OnGestureListener?
        get() = gestureDetector.listener
        set(value) { gestureDetector.listener = value }
    var onSceneTouch: ((MotionEvent, HitResult?) -> Boolean)? = null
    /** Called only after a frame actually reaches the surface. */
    var onFrame: ((Long) -> Unit)? = null
    /** Called before scene updates; suitable for animation, physics and camera updates. */
    var onBeforeFrame: ((Long) -> Unit)? = null
    var onRenderError: ((Throwable) -> Unit)? = null
    var surfaceMirrorer: SurfaceMirrorer?
        get() = state.sceneRenderer.surfaceMirrorer
        set(value) { requireAlive(); state.sceneRenderer.surfaceMirrorer = value }

    val surfaceView: View = when (this.surfaceType) {
        SurfaceType.Surface -> SurfaceView(context)
        SurfaceType.TextureSurface -> TextureView(context).apply { isOpaque = isSceneOpaque }
    }
    private var boundLifecycle: Lifecycle? = null
    private var automaticLifecycle = sharedLifecycle == null
    private var resumed = true
    private var destroyed = false
    val isDestroyed: Boolean get() = destroyed
    private var frameInProgress = false
    private var framePosted = false
    private var needsPresent = true
    private var gpuLoadPending = false
    private var previousFrameTime: Long? = null
    private var capturedTouchNode: Node? = null
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val autoCenterState = SceneAutoCenterState()
    private val autoFitState = SceneAutoFitState()
    private val choreographer by lazy { Choreographer.getInstance() }
    private val frameCallback = Choreographer.FrameCallback { renderFrame(it) }

    init {
        addView(surfaceView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (!isInEditMode) {
            view.applyRenderQuality(renderQuality)
            state.sceneRenderer.apply {
                onSurfaceReady = { height ->
                    cameraGestureDetector = CameraGestureDetector(height, cameraManipulator)
                    requestRender()
                }
                onSurfaceResized = { width, height ->
                    if (width > 0 && height > 0) {
                        cameraManipulator?.setViewport(width, height)
                        cameraNode.updateProjection()
                        resetFraming()
                    }
                }
                onSurfaceDestroyed = { cameraGestureDetector = null; stopFrameCallbacks() }
            }
            if (!autoFitContent) {
                cameraManipulator = createDefaultCameraManipulator(
                    eyePosition = cameraNode.position, targetPosition = Position(0f)
                )
            }
            sharedLifecycle?.let(::bindLifecycle)
        }
    }

    fun bindLifecycle(lifecycle: Lifecycle) {
        requireAlive()
        check(lifecycle.currentState != Lifecycle.State.DESTROYED) { "Lifecycle is destroyed" }
        automaticLifecycle = false
        bindLifecycleInternal(lifecycle)
    }
    private fun bindLifecycleInternal(lifecycle: Lifecycle) {
        if (boundLifecycle === lifecycle) return
        boundLifecycle?.removeObserver(this)
        boundLifecycle = lifecycle
        resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        lifecycle.addObserver(this)
        updateFrameScheduling()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (destroyed || isInEditMode) return
        if (automaticLifecycle) findViewTreeLifecycleOwner()?.lifecycle?.let(::bindLifecycleInternal)
        @Suppress("DEPRECATION")
        val targetDisplay = display ?: (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        when (val target = surfaceView) {
            is TextureView -> state.sceneRenderer.attachToTextureView(
                target, isSceneOpaque, context, targetDisplay, ::dispatchSceneTouch
            )
            is SurfaceView -> state.sceneRenderer.attachToSurfaceView(
                target, isSceneOpaque, context, targetDisplay, ::dispatchSceneTouch
            )
        }
        if (resumed) viewNodeWindowManager.resume(this)
        requestRender()
    }
    override fun onDetachedFromWindow() {
        stopFrameCallbacks()
        if (resourceDelegate.isInitialized() && !destroyed) {
            updateAudioActivity(false)
            cancelCapturedTouch()
            viewNodeWindowManager.pause()
            state.sceneRenderer.detach()
        }
        super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow && !isInEditMode && !destroyed) updateFrameScheduling()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // View constructors may call this before subclass fields have been initialized.
        if (isAttachedToWindow && !isInEditMode && !destroyed) updateFrameScheduling()
    }
    override fun onResume(owner: LifecycleOwner) = resume()
    override fun onPause(owner: LifecycleOwner) = pause()
    override fun onDestroy(owner: LifecycleOwner) = destroy()
    fun resume() {
        requireAlive(); resumed = true
        if (isAttachedToWindow) viewNodeWindowManager.resume(this)
        requestRender()
    }
    fun pause() {
        requireAlive(); resumed = false
        updateAudioActivity(false)
        stopFrameCallbacks(); cancelCapturedTouch(); viewNodeWindowManager.pause()
    }

    fun addChildNode(node: Node) {
        requireAlive()
        require(node.engine === engine) { "Node belongs to another Engine" }
        check(!node.isDestroyed) { "Cannot attach a destroyed node" }
        require(node.parent == null || node.parent === contentRoot) { "Remove node from previous parent first" }
        contentRoot.addChildNode(node)
        resetFraming()
    }
    fun removeChildNode(node: Node, destroy: Boolean = false) {
        requireAlive()
        if (node.parent !== contentRoot) return
        contentRoot.removeChildNode(node)
        updateAudioActivity(false, node)
        if (destroy) node.destroy()
        resetFraming()
    }
    fun clearChildNodes(destroy: Boolean = true) {
        childNodes.forEach { removeChildNode(it, destroy) }
    }
    /** Canceled on destroy; success/error callbacks run on the main thread. */
    fun loadModelInstance(
        fileLocation: String,
        onError: (Throwable) -> Unit = { Log.e("NativeSceneView", "Model load failed", it) },
        onLoaded: (ModelInstance) -> Unit,
    ): Job {
        requireAlive()
        return loadScope.launch {
            try {
                val instance = modelLoader.loadModelInstance(fileLocation)
                    ?: error("No model data at $fileLocation")
                if (!destroyed) {
                    gpuLoadPending = true
                    onLoaded(instance)
                    requestRender()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!destroyed) onError(error)
            }
        }
    }
    /** Transfer a texture created on this Engine to the scene's lifetime. Do not destroy it twice. */
    fun ownTexture(texture: com.google.android.filament.Texture): com.google.android.filament.Texture {
        requireAlive()
        return state.ownTexture(texture)
    }
    fun requestRender() {
        if (destroyed || isInEditMode) return
        checkMainThread(); needsPresent = true
        updateFrameScheduling()
    }
    private fun resetFraming() {
        autoCenterState.reset(); autoFitState.reset(); requestRender()
    }
    private fun canRender() = !destroyed && resumed && isAttachedToWindow &&
        windowVisibility == VISIBLE && isShown && surfaceView.width > 0 &&
        surfaceView.height > 0 && state.sceneRenderer.isAttached
    private fun updateAudioActivity(active: Boolean, node: Node = contentRoot) {
        if (node.isDestroyed) return
        (node as? SpatialAudioNode)?.setSceneActive(active)
        node.childNodes.toList().forEach { updateAudioActivity(active, it) }
    }
    private fun updateFrameScheduling() {
        if (isInEditMode || destroyed) return
        updateAudioActivity(canRender())
        if (canRender() && (isRendering || needsPresent)) {
            if (!framePosted) {
                framePosted = true
                choreographer.postFrameCallback(frameCallback)
            }
        } else stopFrameCallbacks()
    }
    private fun stopFrameCallbacks() {
        if (framePosted) choreographer.removeFrameCallback(frameCallback)
        framePosted = false
        previousFrameTime = null
    }
    private fun renderFrame(time: Long) {
        framePosted = false
        if (!canRender()) return
        val target = state.sceneRenderer
        val before = target.presentedFrameCount
        frameInProgress = true
        try {
            target.renderFrame(time, shouldRender = ::canRender) sceneUpdate@{
                modelLoader.updateLoad()
                if (gpuLoadPending && modelLoader.progress >= 1f) gpuLoadPending = false
                onBeforeFrame?.invoke(time)
                if (destroyed) return@sceneUpdate
                contentRoot.onFrame(time)
                if (destroyed) return@sceneUpdate
                cameraNode.onFrame(time)
                mainLightNode.onFrame(time)
                fillLightNode.onFrame(time)
                if (autoCenterContent) autoCenterState.maybeCenter(contentRoot)
                if (autoFitContent && cameraManipulator == null) {
                    autoFitState.maybeFit(cameraNode, contentRoot, padding = framingPadding)
                }
                cameraManipulator?.let {
                    it.update(time.intervalSeconds(previousFrameTime).toFloat())
                    cameraNode.transform = it.getTransform()
                }
                setSpatialAudioListenerPose(cameraNode.worldPosition,
                    cameraNode.forwardDirection, cameraNode.upDirection)
            }
            if (target.presentedFrameCount != before) {
                needsPresent = gpuLoadPending
                onFrame?.invoke(time)
            }
            previousFrameTime = time
            updateFrameScheduling()
        } catch (error: Exception) {
            if (!destroyed) pause()
            onRenderError?.invoke(error) ?: Log.e("NativeSceneView", "Rendering paused after error", error)
        } finally {
            frameInProgress = false
            if (destroyed) releaseResources()
        }
    }
    private fun dispatchSceneTouch(event: MotionEvent) {
        if (destroyed || !resumed) return
        val hit = collisionSystem.hitTest(event).firstOrNull { it.node.isTouchable }
        val node = hit?.node
        if (event.actionMasked == MotionEvent.ACTION_DOWN) cancelCapturedTouch()
        val captured = capturedTouchNode?.takeIf { it !== node }
        var consumed = false
        val intercepted = onSceneTouch?.invoke(event, hit) == true
        if (destroyed) return
        if (!intercepted) {
            consumed = captured?.onCapturedTouchEvent(event) == true ||
                (hit != null && node?.onTouchEvent(event, hit) == true)
            if (destroyed) return
            if (!consumed) {
                gestureDetector.onTouchEvent(event, hit)
                if (destroyed) return
                if (node?.isEditable != true) cameraGestureDetector?.onTouchEvent(event)
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> capturedTouchNode = node.takeIf { consumed }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> capturedTouchNode = null
        }
        requestRender()
    }
    private fun cancelCapturedTouch() {
        capturedTouchNode?.let { node ->
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            try { node.onCapturedTouchEvent(event) } finally { event.recycle() }
        }
        capturedTouchNode = null
    }
    /** Idempotent. Destroyed views cannot be reused. */
    fun destroy() {
        checkMainThread()
        if (destroyed) return
        destroyed = true
        loadScope.cancel()
        stopFrameCallbacks()
        boundLifecycle?.removeObserver(this)
        boundLifecycle = null
        // User callbacks may destroy the view. Keep native resources alive until endFrame.
        if (!frameInProgress) releaseResources()
    }
    private fun releaseResources() {
        if (resourceDelegate.isInitialized()) {
            runCatching { cancelCapturedTouch() }
            state.close()
        }
        surfaceView.setOnTouchListener(null)
        cameraGestureDetector = null
        onFrame = null; onBeforeFrame = null; onSceneTouch = null; onGestureListener = null
        onRenderError = null
    }
    private fun requireAlive() {
        checkMainThread()
        check(!destroyed) { "SceneView has been destroyed" }
    }
    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper()) {
        "SceneView and Filament must be accessed on the main thread"
    }
}

/** XML-friendly name for the default TextureView-based scene. */
class TextureSceneView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : SceneView(context, attrs, defStyleAttr, surfaceType = SurfaceType.TextureSurface)
