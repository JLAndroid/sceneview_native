# SceneView 4.35.0 原生 Android View 版

这是基于 [sceneview/sceneview](https://github.com/sceneview/sceneview) 的独立原生 Android View/XML 移植工程，仓库为 [JLAndroid/sceneview_native](https://github.com/JLAndroid/sceneview_native)。上游基准为 **4.35.0**，本工程不是 SceneView 官方发布物。

以 **SceneView 4.35.0** 的 Android 3D 源码和 **Filament 1.72.1** 为基础，改为 `FrameLayout + TextureView/SurfaceView`、XML、普通 Kotlin 对象与 Android 生命周期。编译模块不声明 Compose 依赖，也没有 Compose 编译插件；Gradle 配置加入了拒绝 Compose 传递依赖的规则。

此前的本地交付记录显示：**已构建两个 release AAR，并用实际发布的 AAR 成功打包示例 APK；尚未做真机验收。** 当时实际依赖解析通过，示例依赖图没有 Compose 组件。该记录不代表此后每次源码修改都已经重新构建。本次仓库整理仅做离线结构检查，没有执行 Gradle。接入先看 [DELIVERY.md](DELIVERY.md)，历史验证范围见 [VALIDATION.md](VALIDATION.md)。

## 通过 JitPack Maven 接入

版本统一为 **`4.35.0`**。此版本需要 JitPack 成功构建后才能下载；当前发布准备与验证状态见 [PUBLISHING.md](PUBLISHING.md)。

在使用方的 `settings.gradle.kts` 中配置：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

在使用方模块的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation("com.github.JLAndroid.sceneview_native:sceneview-native:4.35.0")
}
```

Groovy 项目在 `settings.gradle` 的 `dependencyResolutionManagement.repositories` 中添加：

```groovy
maven { url 'https://jitpack.io' }
```

然后在模块 `build.gradle` 中添加：

```groovy
implementation 'com.github.JLAndroid.sceneview_native:sceneview-native:4.35.0'
```

这是多模块仓库，因此 groupId 为 `com.github.JLAndroid.sceneview_native`，artifactId 为 `sceneview-native`。`sceneview-core` 和第三方依赖会自动传递。

JitPack 是独立于 Maven Central 的 Maven 仓库，所以需要添加一次 `https://jitpack.io`。不再使用 GitHub Raw 地址或 `local.sceneview` 坐标，也不需要 Central 注册、签名或 Token。请移除之前手动放入 `app/libs` 的两个 SceneView Native AAR，以及旧的 SceneView Native 依赖；不要同时依赖官方 SceneView，以免重复定义类。

最低 Android API 21、Java 17，工具链和运行验证范围见下文。库包名仍为 `io.github.sceneview`，不因 Maven 坐标变化而修改。

## 仓库内容与来源

保留 Apache-2.0 的 `LICENSE`、`NOTICE` 和源码版权声明。修改与上游文件的对应关系见 `PORT-MANIFEST.json`；`upstream/` 保存移植依据和哈希校验所需原件，不参与模块编译。

Git 仓库提交源码、必要资源、Gradle Wrapper、配置、文档、校验工具，以及 `maven/` 中保留的旧版分发归档。当前版本由 JitPack 从源码构建。`build/`、`.gradle/`、`.kotlin/`、日志、`local.properties`、本机工具目录 `.build-tools/`、交付目录 `dist/` 以及兼容性示例中的生成 AAR 均不提交。

使用 Android Studio 打开仓库并配置自己的 Android SDK；默认示例直接依赖本地源码模块，不需要 `dist/`。`compatibility-check/` 是独立的 AAR 接入验证工程，需要先准备两个当前版本 AAR，详见 [DELIVERY.md](DELIVERY.md)。

## 文件在哪里

| 路径 | 内容 |
| --- | --- |
| `upstream/4.35.0/` | 未修改的官方 Android 源码包、对应 AAR、解压源码和许可文件 |
| `sceneview-core/` | 数学、碰撞、几何与模型格式解析；合并 commonMain 和 Android 实现 |
| `sceneview-native/` | 原生 View/XML 库、渲染与资源管理、节点和加载器 |
| `sample/` | 普通 Activity + XML 示例，不使用 Compose |
| `jitpack.yml` | JitPack 的 JDK 与两个模块的发布任务 |
| `PUBLISHING.md` | JitPack 发布步骤与验证状态 |
| `maven/` | 之前 GitHub Raw 方案的旧版归档，不是当前版本的发布目录 |
| `tools/prepare_maven_repository.py` | 旧版 GitHub Raw 产物的校验工具，不参与 JitPack 发布 |
| `tools/verify_native.py` | 无需 Gradle 的源码/资源结构检查 |
| `PORTING.md` | 移植范围、API 差异、资源所有权 |
| `VALIDATION.md` | 已检查内容与待验证步骤 |
| `PORT-MANIFEST.json` | 源码和上游映射、修改/新增/省略列表与 SHA-256 |

这次复制的是 Android 3D 库及它需要的 core 源码，**不是整个多平台 Git 仓库**。ARCore/ARSceneView、Compose 多平台、Web、iOS 和官方演示 App 不在本工程中。归档目录保留上游 Compose 源码用于比较，但它不参与任何模块的编译。

版本依据：2026-09-16 保存的 Maven Central 元数据中，`latest` 和 `release` 均为 `4.35.0`，元数据更新时间为 `20260911073651`。记录见 `upstream/maven-metadata.xml` 和 `upstream/provenance.json`。

## 先看示例

用 Android Studio 单独打开本目录。所需构建工具和依赖已在本机完成首次下载；其他机器仍需准备对应环境。

示例源码：`sample/src/main/java/local/sceneview/sample/MainActivity.kt`。

它演示：

- XML 创建透明 TextureView 场景，模型后面有普通 TextView。
- 从 assets 加载自生成的 `learning_cube.glb`，播放名为 `Spin` 的动画。
- 切换棋盘纹理，再恢复 GLB 原始材质。
- 普通 Android 按钮和覆盖层显示在模型上面；打开覆盖层时不会隐藏或移除 SceneView。
- Activity 生命周期驱动渲染暂停和释放。

示例覆盖的是普通 View 层级。你项目里的视频“小屏放大”若本身使用 SurfaceView/独立 Window，还需要在那个实际窗口结构中测试，不能仅凭示例断言已解决。

## 工具链和接入限制

| 项目 | 版本/要求 |
| --- | --- |
| Android Gradle Plugin | 8.2.2 |
| Kotlin 编译插件 | 1.9.0 |
| Kotlin 标准运行库 | 2.0.21（Filament 的传递依赖） |
| Gradle 发行版 | 8.2，已固定 SHA-256 |
| JDK / 字节码目标 | 17 |
| compileSdk | 34 |
| minSdk | 21（Android 5.0） |
| 示例 targetSdk | 34 |
| Filament / gltfio / filament-utils | 统一 1.72.1 |
| kotlin-math | 1.5.3 |

AndroidX 原生适配使用 Activity 1.8.2、Lifecycle 2.7.0、Core 1.12.0、SavedState 1.2.1。Activity 1.13 会间接引入 Compose 注解包，所以没有继续使用；SceneView 和 Filament 版本保持不变。

本版本已对 Kotlin 1.9.0 / AGP 8.2.2 / Java 17 / compileSdk 34 做二进制接入验证，未修改原业务项目。Filament 保持 1.72.1，其传递依赖仍包含 kotlin-stdlib 2.0.21 和协程 1.9.0；不要强行锁回 stdlib 1.9。没有使用跳过 Kotlin 元数据检查的参数。**本版本最低声明为 Android API 21，已做 NewApi/InlinedApi 检查及打包验证；Android 5/6 真机运行尚未验证。**

保留了 `io.github.sceneview` 包名，便于阅读和迁移，因此不能同时引用官方 `sceneview`/`sceneview-core` 的二进制版本，否则会重复定义类。不要同时放入旧 Filament 和本工程的 Filament。

## XML 创建场景

```xml
<io.github.sceneview.TextureSceneView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/sceneView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:svSurfaceType="texture"
    app:svOpaque="false"
    app:svAutoCenter="true"
    app:svAutoFit="false" />
```

`SceneView` 本身也是原生 View，默认同样使用 TextureView。需要 SurfaceView 时，用 `SceneView` 并设置 `app:svSurfaceType="surface"`。渲染表面类型在创建时确定，不提供运行中切换。

透明显示要求宿主 Window 开启硬件加速，同时不要给 scene 设置遮住背景的 skybox。TextureView 参与普通 View 的绘制层级：同一个 FrameLayout 中后添加的同 Z 值兄弟 View 会覆盖它；不同 elevation/translationZ 会影响顺序。这里不使用 `setZOrderOnTop(true)`。

可选属性还有 `svRenderQuality="performance|defaultQuality|cinematic"`。

## Fragment 加载模型

在 `onViewCreated()` 中绑定 **viewLifecycleOwner**：

```kotlin
val sceneView = view.findViewById<io.github.sceneview.SceneView>(R.id.sceneView)
sceneView.bindLifecycle(viewLifecycleOwner.lifecycle)
sceneView.loadModelInstance(
    fileLocation = "models/your_model.glb",
    onError = { error -> android.util.Log.e("Model", "加载失败", error) }
) { instance ->
    val node = io.github.sceneview.node.ModelNode(
        modelInstance = instance,
        autoAnimate = false,
        scaleToUnits = 1.0f,
        centerOrigin = io.github.sceneview.math.Position(0f)
    )
    sceneView.addChildNode(node)
    // 替换为你的 GLB 内真实存在的动画名称。
    node.playAnimation("Idle", loop = true)
}
```

这是带生命周期取消的回调接口；返回 `Job`，可提前 `cancel()`。文件读取/转换由加载器切到后台，节点操作和成功/错误回调在主线程。控件 `destroy()` 时取消自己启动的任务。纹理可能还在异步上传，回调并不代表首帧已经完整显示。

也保留了 `modelLoader.loadModelInstance()` 挂起函数和 `createModelInstance()` 同步函数。直接使用加载器时，调用方必须把协程绑定到当前 View 生命周期，且不要在销毁后操作它。网络资源由宿主声明 INTERNET 权限；本地示例不需要网络权限。

## 相机、大小和灯光

不设置 `scaleToUnits` 时，模型保持文件原尺寸；移动相机仍能改变它在屏幕中的大小。手势控制器默认启用，它会每帧更新相机，所以手动控制前先关闭它：

```kotlin
sceneView.cameraManipulator = null
sceneView.autoFitContent = false
sceneView.cameraNode.position = io.github.sceneview.math.Position(0f, 0.5f, 4f)
sceneView.cameraNode.lookAt(io.github.sceneview.math.Position(0f))
sceneView.mainLightNode.intensity = 10_000f
sceneView.fillLightNode.intensity = 3_000f
sceneView.environment.indirectLight?.intensity = 10_000f
```

要让相机自动框住模型，设置 `autoFitContent = true`；此操作会关闭手势相机控制器。XML 的 `svAutoFit="true"` 同样有效。若重新设置 `cameraManipulator`，则以手势控制器为准。`autoCenterContent` 会移动公共内容根节点，让整体几何中心靠近原点；需要精确世界坐标布局时设为 false。

位置、旋转、缩放可直接改 `node.position / rotation / scale`。平滑过渡使用保留的 `node.transform(...)`/`smoothTransform` API，也可用 Android ValueAnimator 更新属性；不再使用 Compose Transition。

## 渲染与资源生命周期

- 所有 SceneView/Node/Filament 对象的创建、修改、释放都放在主线程。
- 默认连续渲染；`isRendering = false` 后只处理已请求的帧。静态修改后调用 `requestRender()`。本控件的加载便捷方法会继续推进待上传模型；直接操作底层加载器时，建议加载完成前保持连续渲染。
- `onBeforeFrame` 在更新场景前执行；`onFrame` 只在实际提交显示帧后执行。
- `ON_PAUSE` 停止帧循环；`ON_RESUME` 恢复；`ON_DESTROY` 释放。暂时 detach 保留模型，重新 attach 后恢复。
- GLB 动画沿用上游时钟算法，后台期间不绘制，恢复时可能跳到当前时间对应姿态；这不是冻结播放时间的播放器。
- 不绑定生命周期且找不到 ViewTreeLifecycleOwner 时，需要自己调用 `pause()/resume()/destroy()`。`destroy()` 可重复调用，但已销毁的 View 不能复用。
- `addChildNode` 后节点由场景负责销毁；`removeChildNode(node, destroy = false)` 把节点交还调用方；跨场景转移时还需要保证相同 Engine 和模型加载器仍存活。
- 模型资产、加载器创建的材质由对应加载器持有。移除节点不等于立即释放模型资产，若需提前回收，先移除所有相关实例节点，再调用 `modelLoader.destroyModel(node.model)`。
- 自行创建纹理可用 `sceneView.ownTexture(texture)` 转交生命周期；它会在节点和材质释放后回收。不要再自行销毁同一纹理，也不要转交加载器本来拥有的 GLB 纹理。
- 外部共享 Engine 不由本控件销毁。外部 Environment 必须属于相同 Engine；谁创建谁管理，使用它的场景解除引用前不能释放。
- 自行创建的 `FogNode`/`ReflectionProbeNode` 控制器应在 SceneView 销毁前 `close()`。`PhysicsBinding`、`ModelAnimationState`、`NodeEditingFeedbackState` 已绑定节点生命周期，也支持手动 close。
- 空间音频节点随所属场景可见性/生命周期暂停；VideoNode 借用的 MediaPlayer、震动实例由调用方暂停和释放。多个场景的空间音频仍沿用上游全局 listener，最后更新的相机生效。

## 保留和替换了哪些能力

保留核心源码中的模型/格式解析、骨骼和形变动画、几何节点、PBR 材质、纹理、相机、灯光、环境、拾取与编辑手势、ViewNode、视频、Splat、基础物理和触觉相关实现。

Compose 状态改为普通属性或 StateFlow；雾、反射探针、空间音频和动画状态提供原生对象入口。编辑提示改为简单 Canvas View；DebugOverlay 改为 TextView。上游 Compose DSL、remember 工厂、Transition/Modifier 接口不再可用，也没有隐藏的 ComposeView。

具体 API 映射和未逐项验证的功能见 `PORTING.md`。Splat、视频等高级能力保留源代码，并不代表本次已经逐项完成真机功能验收。

许可为 Apache-2.0，保留 `LICENSE`、`NOTICE` 和上游版权声明。本工程是独立移植版本，版本名为 `4.35.0`，不是官方发布物。
