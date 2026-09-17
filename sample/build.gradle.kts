
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "local.sceneview.sample"
    compileSdk = 34
    defaultConfig {
        applicationId = "local.sceneview.native.sample"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += "glb" }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}
dependencies {
    if (providers.gradleProperty("usePackagedAar").orNull == "true") {
        implementation("${project.group}:sceneview-native:${project.version}")
    } else {
        implementation(project(":sceneview-native"))
    }
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
}
