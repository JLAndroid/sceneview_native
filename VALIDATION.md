# 验证记录：Kotlin 1.9 兼容版

下述为历史版本 `4.35.0-native.3-kotlin1.9` 的记录；当前 `4.35.0` 的 JitPack 构建状态见 [PUBLISHING.md](PUBLISHING.md)。历史构建日期 2026-09-17。所有操作在独立工程完成，未修改或构建原业务项目。

本次重新打包包含 Consumer 重载移除及 API 21 适配。两个库和消费工程均声明 minSdk 21，没有通过 overrideLibrary 绕过 SDK 合并检查。

## 已完成

- 两个库的 NewApi/InlinedApi Lint 审计通过（tools/api21-audit.gradle，warningsAsErrors，未使用 baseline 或屏蔽规则）。

- 使用 Kotlin 编译插件 1.9.0、AGP 8.2.2、Gradle 8.2、JDK 17.0.17、compileSdk 34，编译两个 release AAR。
- 两个模块的 release Maven publication 成功；sample 使用源码依赖及实际发布 AAR 的 debug APK 均构建成功。
- 独立 `compatibility-check` 工程直接读取 app/libs 的两个 AAR，debug APK 构建成功。没有引入库源码模块或本地 Maven 替代包，也没有继承主工程 subprojects 依赖规则。
- 同一独立工程的 release APK 构建成功，开启 minifyEnabled，执行 AGP 8.2.2 自带的 R8；默认 lintVitalRelease 也通过。完整 lint 尚未执行。
- 3 个数学回归测试通过：向量容差边界及 NaN、矩阵所有分量、平滑动画推进及最终到达目标。
- Maven AAR 示例依赖图 48 个组件，直接 AAR 示例 46 个组件；均没有 Compose 组件。依赖图数量不包含以文件形式加入的两个 AAR。
- 第三方依赖包含 kotlin-stdlib 2.0.21、协程 1.9.0、kotlin-math 1.5.3；Filament 三项组件保持 1.72.1。没有修改 Kotlin 元数据或跳过元数据检查。
- AAR 结构、资源、Java 17 字节码、原始材质、发布物和消费方所用 AAR 哈希均由 tools/package_delivery.py 校验。

这是编译和打包兼容验证，不是“全部依赖都由 Kotlin 1.9 编译”，也不是原业务项目的完整依赖冲突验收。禁止把标准运行库强制降到 1.9.0 来解释本次结果。

## 记录

- build-native3-api21.log
- sceneview-core/build/reports/lint-results-release.xml
- sceneview-native/build/reports/lint-results-release.xml

- build-native3-aar.log
- build-native3-delivery.log
- build-native3-packaged-sample.log
- build-native3-raw-aar.log
- build-native3-raw-aar-release.log
- sceneview-core/build/test-results/testReleaseUnitTest/TEST-io.github.sceneview.math.Kotlin19CompatibilityTest.xml
- dist/BUILD-REPORT.json、dist/dependencies.json、dist/packaged-dependencies.json、dist/raw-aar-dependencies.json、dist/SHA256SUMS.txt

初期失败的 build-native3-attempt*.log 保留排查记录，不作为交付成功证据。

## 静态检查

运行 tools/record_port.py --stamp 与 tools/verify_native.py --report，检查源码映射、Compose 残留、XML、示例 GLB、资源完整性与材质归档一致性。这些静态检查本身不能替代编译或真机测试。

## 尚未执行

设备安装、真机 GPU/透明合成/生命周期测试、全部高级节点逐项验收、仪器测试及完整 Android lint。R8 构建成功不代表已验证混淆包的所有运行功能。

## 可复现命令

准备 JDK 17、Android SDK 34 和对应网络环境：

```powershell
.\gradlew.bat :sceneview-core:lintRelease :sceneview-native:lintRelease -I tools/api21-audit.gradle
.\gradlew.bat :sceneview-core:assembleRelease :sceneview-native:assembleRelease :sceneview-core:testReleaseUnitTest
.\gradlew.bat :sceneview-core:publishReleasePublicationToDeliveryRepository :sceneview-native:publishReleasePublicationToDeliveryRepository :sample:assembleDebug
.\gradlew.bat :sample:assembleDebug verifyNativeDependencies -PusePackagedAar=true -I tools/export-dependencies.gradle
.\gradlew.bat -p compatibility-check :app:assembleDebug :app:assembleRelease
```

独立消费工程的 app/libs 应先更新为最新两个 release AAR；具体依赖配置与交付给用户的 tools/aar-dependencies.gradle 共用。

## 真机验收步骤

1. 打开示例，看到旋转的蓝色立方体。模型四周应能看到后方普通 TextView 和背景；首次加载失败时应显示错误信息。
2. 单指环绕、双指缩放；点击动画按钮停止/重新播放；切换纹理并恢复原始材质。
3. 打开覆盖层，覆盖层完全挡住模型；关闭后模型仍在。期间没有修改 SceneView.visibility。
4. 快速进入/退出 30 次，在加载未完成时退出；查看 native 崩溃、Window 泄漏、持续增长的资源占用。
5. 切后台、锁屏、解锁、前台恢复、旋转，确认表面重建后首帧和手势正常；后台不持续渲染，空间音频暂停。
6. 在 isRendering=false 下加载模型、修改相机并 requestRender，确认最终模型/纹理可见且无持续空转。
7. 单独验证 ViewNode 中 Button 的 DOWN/MOVE/UP/CANCEL、移出模型后的取消，以及 detach/attach 后内容恢复。
8. 把模型场景放到实际播放器界面，在小窗切全屏、Dialog/PopupWindow/新 Activity 等真实窗口层级下验证覆盖关系。
9. 若共享 Engine/Environment/纹理，分别销毁一个场景和最后一个场景，确认借用资源没有提前销毁或重复回收。
10. 为目标发布包检查设备 ABI、native ELF 的 16 KB 页大小对齐和低端 GPU 性能；Android 5/6 的 GPU、纹理及驱动兼容性尚需专项真机验收。

以上步骤目前是待执行清单，不是已经通过的测试结果。
