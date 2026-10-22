plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun prop(name: String, default: String) = (findProperty(name) as String?) ?: default

android {
    namespace = "com.rentcare.car"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rentcare.car"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", "\"${prop("rentcare.apiBaseUrl", "http://10.0.2.2:5173")}\"")
        buildConfigField("String", "CAR_API_KEY", "\"${prop("rentcare.carApiKey", "dev-car-key")}\"")
        buildConfigField("String", "VENDOR_ID", "\"${prop("rentcare.vendorId", "demo-jeju")}\"")
    }

    buildTypes {
        debug {
            // Cleartext only to the emulator host loopback, see res/xml/network_security_config_debug.xml
            manifestPlaceholders["networkConfig"] = "@xml/network_security_config_debug"
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["networkConfig"] = "@xml/network_security_config"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navi)

    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
}
