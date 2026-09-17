plugins {
    id("com.android.library") version "8.2.2" apply false
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}

subprojects {
    group = "local.sceneview"
    version = "4.35.0-native.3-kotlin1.9"
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val group = requested.group
            if (group.startsWith("androidx.compose") || group.startsWith("org.jetbrains.compose")) {
                throw GradleException("Compose is not allowed in this native View project: $requested")
            }
            // Materials bundled here were compiled for this exact Filament runtime.
            if (group == "com.google.android.filament") {
                useVersion("1.72.1")
                because("Keep Filament native binaries and bundled material packages paired")
            }
        }
    }
}
