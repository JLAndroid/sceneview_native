
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}
android {
    namespace = "io.github.sceneview"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "VERSION_NAME", "\"4.35.0-native.3-kotlin1.9\"")
    }
    buildFeatures { buildConfig = true }
    publishing { singleVariant("release") { withSourcesJar() } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += listOf("filamat", "ktx", "glb", "wav", "mp3", "ogg", "flac") }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}
dependencies {
    api(project(":sceneview-core"))
    api("com.google.android.filament:filament-android:1.72.1") { version { strictly("1.72.1") } }
    api("com.google.android.filament:gltfio-android:1.72.1") { version { strictly("1.72.1") } }
    api("com.google.android.filament:filament-utils-android:1.72.1") { version { strictly("1.72.1") } }
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    api("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.annotation:annotation:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    // Activity 1.13 transitively brings a Compose annotation via navigationevent.
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-android:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
}

apply(from = rootProject.file("gradle/publish-native.gradle.kts"))
