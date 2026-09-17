pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("dist/repository")
            content { includeGroup("local.sceneview") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "SceneViewNative"
include(":sceneview-core", ":sceneview-native", ":sample")
