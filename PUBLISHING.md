# JitPack 发布

当前版本：`4.35.0`。构建环境为 JDK 17、Gradle 8.2、AGP 8.2.2、Kotlin 1.9.0。

## 坐标

- `com.github.JLAndroid.sceneview_native:sceneview-native:4.35.0`
- `com.github.JLAndroid.sceneview_native:sceneview-core:4.35.0`

使用方只声明 `sceneview-native`；其 POM/Gradle module metadata 会引入同版本的 core。
多模块仓库的 groupId 按 JitPack 规则包含 GitHub 用户名和仓库名，区分大小写。
原有 `io.github.sceneview` Kotlin/Java 包名不变。

## 构建和发布流程

1. `gradle.properties` 统一保存 `GROUP`、`VERSION_NAME`。两个模块的 Maven 发布配置、示例和库的 BuildConfig 读取同一版本。
2. 推送代码并创建同名的附注 Git 标签，例如 `4.35.0`。推送时带上 `--follow-tags`，或在本仓库设置 `git config push.followTags true`，确保 GitHub Desktop 推送提交时也带上标签。已发布的标签不能用于覆盖不同产物；后续改动发布新版本。
3. 在 [JitPack](https://jitpack.io/#JLAndroid/sceneview_native) 查询此仓库并构建该标签。首次下载 Maven 文件也会触发云端构建。
4. `jitpack.yml` 指定 JDK 17，并把两个 release publication 安装到 JitPack 构建机器的 Maven Local。`$VERSION` 为请求的标签或提交版本，传给 `VERSION_NAME`，确保两个模块的传递依赖版本一致。
5. 云构建成功后，检查两个模块的 `.pom`、`.module`、`.aar` 是否可下载，并确认 native 的 core 依赖使用上述 groupId 和同一版本。

本地等价验证命令（需要明确授权执行 Android/Gradle 构建后才能运行）：

```powershell
.\gradlew.bat --no-daemon :sceneview-core:publishReleasePublicationToMavenLocal :sceneview-native:publishReleasePublicationToMavenLocal "-PVERSION_NAME=4.35.0"
```

不需要 Central 账号、PGP 签名或 GitHub Token。JitPack 按源码重新构建；`maven/` 与 `dist/` 中旧版 AAR 不作为新版本发布物。

## 验证状态

2026-09-17 已完成以下验证：

- JitPack 的 `4.35.0` 标签构建成功，API 状态为 `ok`，源码提交为 `f650cd227e552d8bb9ee218576881b3235cd5abd`，发布模块为 `sceneview-core` 和 `sceneview-native`。
- 本地使用 JDK 17 / Gradle 8.2 执行两个 release publication，构建成功。核对了 POM 坐标、native 到 core 的同版本传递依赖，以及 Gradle metadata 中的产物哈希。
- JitPack 上两个模块的 POM、Gradle module metadata、AAR 均返回 HTTP 200。
- 示例通过 `-PusePackagedAar=true` 使用 JitPack 产物。验证用临时 init script 移除了 settings 中的本地文件仓库，添加 `https://jitpack.io`；没有使用 `mavenLocal()`。实际解析到了两个 `com.github.JLAndroid.sceneview_native:*:4.35.0` AAR，`:sample:assembleDebug` 成功。
- 离线结构检查 1,395 项通过。没有进行真机/GPU/透明叠层运行测试。

云端结果：[构建状态](https://jitpack.io/api/builds/com.github.JLAndroid/sceneview_native/4.35.0)、[构建日志](https://jitpack.io/com/github/JLAndroid/sceneview_native/4.35.0/build.log)。本地日志为 `build-jitpack-publication.log`、`build-jitpack-consumer.log`，不提交到 Git。

`VALIDATION.md` 保留旧版本的历史打包记录；本节记录的是当前 JitPack `4.35.0` 的实际验证结果。版本标签固定在上述提交，之后的验证文档提交不会移动该标签。

参考：[JitPack Android 发布文档](https://docs.jitpack.io/android/)、[多模块与自定义构建](https://docs.jitpack.io/building/)。
