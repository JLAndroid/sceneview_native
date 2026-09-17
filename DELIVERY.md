# Kotlin 1.9.0 兼容版 AAR 接入

本说明适用于已生成的本地 AAR 交付包。`dist/` 和 `compatibility-check/app/libs/*.aar` 不纳入 Git，全新克隆仓库时这些文件不存在。源码使用者可以直接打开根工程，默认 `sample` 依赖源码模块。

独立的 `compatibility-check/` 工程用于验证手动 AAR 接入。准备好下列两个当前版本 AAR 后，将它们放入 `compatibility-check/app/libs/`，并为该工程配置自己的 Android SDK。`tools/package_delivery.py` 依赖此前构建产生的日志、测试报告和依赖报告；清理构建产物后，需要重新完成相应构建流程才能运行它。

本次交付直接使用两个 AAR，不需要解压 ZIP：

- `dist/aar/sceneview-native-4.35.0-native.3-kotlin1.9.aar`
- `dist/aar/sceneview-core-4.35.0-native.3-kotlin1.9.aar`

两个必须同时使用。它们不是 fat AAR，Filament、AndroidX、协程和数学库仍需通过 Gradle 声明。

## 放入 app/libs

1. 将上述两个 AAR 放入 `app/libs`，移除之前 native.1/native.2 的两个 AAR，避免重复类。
2. 将 `dist/aar-dependencies.gradle` 复制到 app 目录，在 app 的 Groovy `build.gradle` 末尾添加 `apply from: 'aar-dependencies.gradle'`，或者把其中配置合并到现有 dependencies/configurations。
3. 移除官方 SceneView/sceneview-core 二进制依赖，保留 Kotlin 插件 1.9.0、AGP 8.2.2，使用 Java 17、compileSdk 34、minSdk 至少 21。

不要把整个 dist/aar 目录里的旧版 AAR 一起导入。本次文件名包含 `native.3-kotlin1.9`。

新包已声明 minSdk 21，不再需要为 `io.github.sceneview` 和 `io.github.sceneview.core` 添加 SDK 忽略项。原业务 Fragment 仍有低于 API 24 时显示静态封面的判断；本次仅交付新库，未修改该判断。

## 已验证的版本组合

- Kotlin 编译插件 **1.9.0**，AGP **8.2.2**，Gradle **8.2**，JDK **17**。
- 本次两个 AAR 的字节码目标 **Java 17**，compileSdk **34**，minSdk **21**。
- Filament / gltfio / filament-utils **1.72.1**，与内置材质匹配。
- kotlin-math **1.5.3**，协程 **1.9.0**。
- Activity **1.8.2**、Lifecycle **2.7.0**、Core **1.12.0**、SavedState **1.2.1**。
- 不需要任何 Compose 插件、Compose UI 或 Compose runtime。

**Kotlin 编译插件和 kotlin-stdlib 是两个不同的依赖。** 编译插件保持 1.9.0；Filament 的标准运行库依赖仍会解析为 **2.0.21**。该组合已实际编译 APK，不需要跳过元数据版本检查。不要强行将 stdlib 降到 1.9.0；若项目存在 force、严格版本约束或更高版本的传递依赖，需要按真实依赖图另行处理。

库保留 `io.github.sceneview` 包名，不可同时引入官方 SceneView。Activity/Window 必须开启硬件加速；网络模型需要宿主 INTERNET 权限。

## 可选：本地 Maven

若使用附带的 dist/repository，在 settings 中注册该仓库后声明 `implementation 'local.sceneview:sceneview-native:4.35.0-native.3-kotlin1.9'` 即可自动带入 core 与第三方依赖，不再同时添加 app/libs 中的两个 AAR。

## XML 与加载

```xml
<io.github.sceneview.TextureSceneView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/sceneView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:svOpaque="false" />
```

Fragment 的 `onViewCreated()`：

```kotlin
val sceneView = view.findViewById<io.github.sceneview.SceneView>(R.id.sceneView)
sceneView.bindLifecycle(viewLifecycleOwner.lifecycle)
sceneView.loadModelInstance("models/your_model.glb", onError = {
    android.util.Log.e("Model", "加载失败", it)
}) { instance ->
    sceneView.addChildNode(io.github.sceneview.node.ModelNode(
        instance,
        autoAnimate = true,
        scaleToUnits = 1.0f,
        centerOrigin = io.github.sceneview.math.Position(0f)
    ))
}
```

Activity 中改绑 `lifecycle`。不绑定任何生命周期时，调用方必须管理 `pause/resume/destroy`。详细 API 和资源所有权见 README.md/PORTING.md。

## 验证范围

实际发布的 Maven AAR 与直接 app/libs 的 AAR 均在 Kotlin 1.9.0 / AGP 8.2.2 下验证。详细结果见 VALIDATION.md 与 dist/BUILD-REPORT.json。尚未安装到设备，透明叠层、GPU 和生命周期运行行为仍需真机测试。原业务项目没有修改或构建。
