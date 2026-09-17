# SceneView Native HTTPS Maven repository

Public repository URL:

```text
https://raw.githubusercontent.com/JLAndroid/sceneview_native/main/maven
```

Published coordinates:

```text
local.sceneview:sceneview-native:4.35.0-native.3-kotlin1.9
local.sceneview:sceneview-core:4.35.0-native.3-kotlin1.9
```

Depend on `sceneview-native`; its POM and Gradle module metadata bring in
`sceneview-core` and the third-party dependencies automatically. `local.sceneview`
is the Maven group ID; it does not require local files or `mavenLocal()`.

These are the previously built and verified delivery artifacts. Their source
archives match the library Kotlin sources in commit `0435b5d`; publishing this
repository does not constitute a new Android build or device test. See the root
`VALIDATION.md` for the historical validation scope and `LICENSE` / `NOTICE` for
licensing. This is an independent View/XML port of SceneView, not an official release.

After explicitly authorized builds produce a new verified `dist/` delivery,
run `python tools/prepare_maven_repository.py` from the repository root, then commit
and push the new Maven files. The script verifies delivery checksums, Kotlin
source archives, assets and dependency metadata without invoking Gradle.
It refuses to replace existing release files; changed binaries require a new version.
`--check` verifies the staged repository against the local delivery without writes.

Only current release files are included; historical local-only versions are not advertised.
Consumers need network access to `raw.githubusercontent.com`, Google Maven, and
Maven Central. No GitHub account or token is required to download this public repository.
