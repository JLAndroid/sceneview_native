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
.\gradlew.bat --no-daemon :sceneview-core:publishReleasePublicationToMavenLocal :sceneview-native:publishReleasePublicationToMavenLocal -PVERSION_NAME=4.35.0
```

不需要 Central 账号、PGP 签名或 GitHub Token。JitPack 按源码重新构建；`maven/` 与 `dist/` 中旧版 AAR 不作为新版本发布物。

## 验证状态

发布配置调整后尚未执行本地或 JitPack Gradle 构建。`VALIDATION.md` 中旧版本的历史打包结果不能替代 `4.35.0` 的构建结果。静态检查也不等于 Maven 在线依赖已验证可用。

参考：[JitPack Android 发布文档](https://docs.jitpack.io/android/)、[多模块与自定义构建](https://docs.jitpack.io/building/)。
