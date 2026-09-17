# 移植记录与 API 差异

## 源码依据

使用 Maven Central 发布的 `io.github.sceneview:sceneview:4.35.0` 源码 JAR、AAR，以及 `sceneview-core-android:4.35.0` 源码 JAR。原件和提取树保存在 upstream，SHA-256 记录在 upstream/provenance.json；未以旧版控件冒充新版移植。

许可、NOTICE、版本目录和工具链配置取自 GitHub `sceneview/sceneview` 的 `v4.35.0` 标签，通过 jsDelivr 下载。已保存的上游构建文件只供对照，不会被 Gradle include。Maven 元数据证明核对时最新发布版为 4.35.0；没有追随未发布 main 分支。

core 将 commonMain 与三个 Android 平台实现合并为普通 Android library。Android 文件覆盖对应 expect 文件，移除 actual 修饰符，其余纯 Kotlin 算法保留。`tools/verify_native.py` 同时检查源码完整性、内部导入和没有遗留 expect/actual 声明。

## 主要替换

| 上游入口 | 本工程入口 |
| --- | --- |
| `@Composable SceneView(...) { ... }` | `SceneView` / `TextureSceneView` 原生 FrameLayout，可 XML inflate |
| `SceneScope`、`NodeScope` DSL | `sceneView.addChildNode(node)`、`node.addChildNode(child)` |
| `rememberEngine`、`remember*Loader` | View 创建并持有 engine/modelLoader/materialLoader/environmentLoader |
| `rememberModelInstance` | `sceneView.loadModelInstance(path, onError) { instance -> ... }` 或直接使用加载器 |
| `DisposableEffect`、`withFrameNanos` | LifecycleObserver、Choreographer、显式 destroy/close |
| Compose Color 重载 | Android ARGB Int 或 `io.github.sceneview.math.Color` |
| `rememberCameraManipulator` | `createDefaultCameraManipulator(...)`，赋值给 cameraManipulator |
| Compose AnimationState | `ModelAnimationState(node).snapshots: StateFlow` |
| Compose PhysicsNode | `PhysicsBody(node, ... )` + `PhysicsBinding(body)` |
| Compose DynamicSkyNode | `DynamicSkyNode(engine)`，调用 update，添加到场景 |
| Compose FogNode | `FogNode(sceneView.view).update(...)`，不用 addChildNode |
| Compose ReflectionProbeNode | `ReflectionProbeNode(scene, environment, ...).update(cameraPosition)` |
| Compose SpatialAudioNode | `SpatialAudioNode(engine, source, ...)`，添加到场景 |
| rememberAudioSource | `loadAudioSource(context, assetPath)` 挂起函数 |
| rememberHapticFeedback | `SceneViewHaptic(context)` 工厂，宿主负责 cancel |
| rememberNodeEditingFeedback | `NodeEditingFeedbackState(node)`，注册监听并跟随节点释放 |
| Compose NodeEditingOverlay | 原生 View/Canvas 简单位置圆环和角度/缩放文字 |
| Compose DebugOverlay | 原生 TextView，onFrame 更新 FPS/帧间隔 |
| rememberSurfaceMirrorer | `SurfaceMirrorer()`，设置 sceneView.surfaceMirrorer |
| rememberSplatCloud / Splat DSL | core 的 SplatParser + 原生 SplatNode，调用方管理后台解析 |
| Compose Transition / VectorConverters | Node 的 transform/smoothTransform 或 Android 动画 API，无相同签名替代 |
| ViewNode 的 Compose 内容构造 | 删除；保留 Android View 和 layout resource 构造方式 |

反射探针这里只提供单一区域 IBL 切换，不宣称实现多探针混合或优先级调度。物理保留上游简单重力/地面碰撞积分，不是完整刚体引擎。Splat 保留上游当前实现和限制，不是新增高斯渲染算法。Canvas 编辑提示没有逐像素复刻 Compose 的全部视觉效果。

普通 Activity/Fragment、所有 Android View 均可以参与 UI，项目内没有 Compose runtime、ComposeView 或 compose 编译插件配置。父项目不要重新加官方 SceneView 依赖，否则会引入重复类/Compose 依赖。

## 原生生命周期设计

SceneView 自己创建 Scene、Filament View/Renderer、默认相机和灯光、内容根节点、加载器、SceneRenderer 和 ViewNode 的 WindowManager。默认 OpenGL Engine 由 Filament 管理 GL 上下文，不额外制造长期驻留的 UI 线程 EGL context；ViewNode/VideoNode/VideoMaterial 在 API 26+ 使用 detached SurfaceTexture 构造器；API 21-25 使用临时 EGL 上下文创建后 detach，并恢复调用线程的 EGL 状态。

初始化采用反向释放栈，构造中途失败也会释放已取得的资源。正常释放顺序为：停止帧回调/取消加载 → 释放表面 → 移除离屏 Window → 节点退出场景 → 销毁节点 → 释放环境/模型/材质 → 延迟回收额外纹理 → 释放 Filament View/Scene/Renderer → flush GPU 队列 → 销毁自建 Engine。

从渲染回调中调用 SceneView.destroy 时，立即标记销毁并取消调度，但推迟 native 释放至当前帧结束。SceneRenderer 在场景更新后重新检查表面，避免回调 detach 后使用旧 SwapChain；beginFrame 成功后通过 finally 调用 endFrame。

子节点遍历使用快照，避免回调中改树导致集合并发修改。ModelNode 在节点销毁后跳过后续模型更新。Node 可以持有多个帧监听，不会让物理绑定/动画状态监听覆盖用户原来的 onFrame。

ON_PAUSE、View 不可见或 detach 时暂停本场景的 SpatialAudioNode。其显式 play/pause/stop 意图与场景暂停分开，恢复时只恢复请求播放的音频。自行创建的 MediaPlayer/触觉资源仍归调用方，不会猜测外部播放器的生命周期。

上游多数节点、数学、材质和加载器代码保持不变；本次静态复核不是对上游全部实现做运行正确性保证。精确改动清单见 PORT-MANIFEST.json。

## Kotlin 1.9 兼容适配（2026-09-17）

此次历史构建版本为 `4.35.0-native.3-kotlin1.9`；当前发布版本统一为 `4.35.0`，Maven 坐标与发布方式见 README。工具链改为 Kotlin 编译插件 1.9.0、AGP 8.2.2、Gradle 8.2、JDK/字节码 17、compileSdk 34。移除了只适用于 AGP 9 的 builtInKotlin/newDsl 开关。Wrapper 发行版 URL 与 SHA-256 已更新，本轮使用本机缓存的官方 Gradle 8.2 运行。

数学库从 1.8.0 调整为 1.5.3，恢复了 Float2/Float3/Float4/Mat4 的容差比较扩展，保持平滑动画收敛语义。新增 3 个回归测试检查边界、NaN、矩阵全部分量与动画最终到达目标。SplatBuffers 的一处 KDoc 区间写法调整为 Kotlin 1.9 解析器可接受的文本。

协程改为 1.9.0。AndroidX 使用 Activity 1.8.2、Lifecycle 2.7.0、Core 1.12.0、SavedState 1.2.1，支持 compileSdk 34。Filament utilities 仍依赖 kotlin-stdlib 2.0.21；本版本保证的是 Kotlin **编译插件** 1.9.0 的接入，不是所有依赖均用 Kotlin 1.9 编译。没有修改第三方 Kotlin 元数据、使用 skip-metadata-version-check，或强行降低标准运行库。

Filament、gltfio、filament-utils 保持 1.72.1；材质二进制和默认 IBL 与归档一致。升级引擎必须同步匹配 filamat。仍为两个普通 AAR，需要外部依赖，不是 fat AAR。

除 sample 使用发布后的 Maven AAR 验证外，`compatibility-check` 是独立 Gradle 工程：只读取 app/libs 中的两个 AAR 和交付的依赖配置，不依赖两个库的源码模块，也不继承主工程的依赖规则。示例 UI 源码和资源从 sample 共用。该工程用于验证 Kotlin 1.9.0 / AGP 8.2.2 的实际二进制接入。

原始上游归档 `upstream/4.35.0` 保持不变。接入说明见 DELIVERY.md，验证范围见 VALIDATION.md。

## API 21 适配及 native.3 打包

两个库及验证工程的 minSdk 均改为 21。移除 `intersectsAll(Collider, java.util.function.Consumer<Collider>)` 弃用重载，保留 Kotlin 回调版本；内部 Node 调用无需调整。

低版本 API 检查还发现并修复了以下调用：

- ModelAnimation 的 FloatProperty/IntProperty 改为 API 14 起可用的通用 Property，保持读写和动画插值行为。
- Node、NodeGestureDelegate、OnNodeGestureListener 改用库内 OnContextClickListener 接口，避免类加载时依赖 API 23 的平台接口；默认监听器提供独立实现。
- 震动权限检查使用 ContextCompat，预定义震动效果的现有 SDK 判断增加 ChecksSdkIntAtLeast 标注，让 Lint 识别实际版本保护。
- API 21-25 的 SurfaceTexture 在临时 EGL pbuffer/context 上创建并 detach，然后恢复原 EGL context 和 draw/read surface；API 26+ 使用原生 detached 构造器。临时 context 和 surface 释放，不终止 Filament 共享 display。

公共 JVM 接口有变化：Consumer 重载删除、TIME_POSITION 字段类型改为 Property、节点右键回调接口改为库内接口。使用这些旧接口的调用方应修改并重新编译。本次 AAR 包含上述修改，不应与旧 native.1/native.2 同时引入。

NewApi/InlinedApi 审计与 minSdk 21 消费工程构建通过，只能证明检查和打包结果；尚未证明全部节点在 Android 5/6 真机上运行正常，尤其是 EGL/视频/ViewNode 路径。
