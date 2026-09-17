
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}
android {
    namespace = "io.github.sceneview.core"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
    publishing { singleVariant("release") { withSourcesJar() } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}
dependencies {
    api("dev.romainguy:kotlin-math:1.5.3")
    testImplementation("junit:junit:4.13.2")
}

apply(from = rootProject.file("gradle/publish-native.gradle.kts"))
