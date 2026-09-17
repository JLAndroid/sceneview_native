# 本独立工程的协作规则

- 用户询问或讨论时只分析；只有明确要求实现/修改/创建时才改文件。
- 修改前简要说明范围、原因和方式。
- 未获得明确的 Android/Gradle 构建、测试、检查、打包授权，不运行 Gradle 或 Wrapper，也不以其他构建方式绕过限制。
- 不修改原业务项目 `D:\androidwork\androidwork\yc_phone_new\ycphonenew`。
- 本工程的目标是原生 Android View/XML；不能引入 Compose runtime、ComposeView、Compose 编译插件或官方 SceneView 二进制依赖。
- 保留包名 io.github.sceneview。Android 3D 功能源码以 upstream/4.35.0 为基准；该目录是原始归档，不直接修改。
- Filament 运行库与 assets/materials 的二进制必须配套升级。不可只改依赖版本。
- 已验证范围见 VALIDATION.md 和 STATIC-VALIDATION.json；不得把结构检查描述成编译或真机验证。
- 修改源码后可执行 `python tools/record_port.py --stamp` 更新归档映射，再执行 `python tools/verify_native.py --report` 做离线结构检查；这两条不是 Android 构建。
