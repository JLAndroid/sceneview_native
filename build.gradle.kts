plugins {
    id("com.android.library") version "8.2.2" apply false
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

subprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
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
