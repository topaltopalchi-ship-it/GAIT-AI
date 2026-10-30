plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.gaitai.analyzer"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.gaitai.analyzer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.21")
}

val poseModelFile = layout.projectDirectory.file("src/main/assets/pose_landmarker_lite.task")
val downloadPoseModel by tasks.registering {
    outputs.file(poseModelFile)
    doLast {
        val target = poseModelFile.asFile
        if (!target.exists() || target.length() < 1_000_000L) {
            target.parentFile.mkdirs()
            val url = java.net.URL(
                "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
            )
            url.openStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        }
        check(target.exists() && target.length() > 1_000_000L) {
            "Pose model download failed. Check internet access and retry."
        }
    }
}
tasks.named("preBuild").configure { dependsOn(downloadPoseModel) }
