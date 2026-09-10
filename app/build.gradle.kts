plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.speedtrans.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.speedtrans.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "3.8"
    }

    signingConfigs {
        getByName("debug") {
            // 固定签名密钥随仓库保存：任何环境构建的 APK 均可互相覆盖安装
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        // 按设计关闭的检查（中文单语言 App / 照片图标 / targetSdk 有意冻结 34 / arm64-only），避免报告噪音
        disable.addAll(
            setOf(
                "HardcodedText", "SetTextI18n",
                "IconLauncherShape", "MonochromeLauncherIcon",
                "OldTargetApi", "ChromeOsAbiSupport",
                "ButtonStyle", "Autofill", "Overdraw",
                "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable",
                // 待 UI 轮：换 SwitchCompat + 补 thumb/track tint（让开关跟随主题）后重新启用
                "UseSwitchCompatOrMaterialXml"
            )
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
}
