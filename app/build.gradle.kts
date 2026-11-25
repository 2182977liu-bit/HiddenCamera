plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.hiddencamera"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.hiddencamera"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "2.1"
        // 仅保留中文资源，去掉依赖库携带的其它语言资源
        resourceConfigurations += listOf("zh")
    }

    buildTypes {
        release {
            // 代码混淆 + 无用代码/资源裁剪，显著减小安装包
            isMinifyEnabled = true
            isShrinkResources = true
            // 用 debug 签名让 CI 产出的 release 包可直接安装（学习项目用途）
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // CameraX
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-video:${camerax_version}")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.camera:camera-extensions:${camerax_version}")

    // Lifecycle（项目使用 StateFlow，移除未使用的 LiveData）
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Fragment / 列表（三 Tab 架构）
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
