plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.remote"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.remote"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // ↓↓↓ ВСТАВЬ СЮДА СВОИ ДАННЫЕ ↓↓↓
        buildConfigField("String", "BOT_TOKEN", "\"ВСТАВЬ_ТОКЕН_СЮДА\"")
        buildConfigField("Long",   "OWNER_ID",  "0L") // замени 0 на свой chat_id
        // ↑↑↑ БОЛЬШЕ НИЧЕГО МЕНЯТЬ НЕ НАДО ↑↑↑
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
}
