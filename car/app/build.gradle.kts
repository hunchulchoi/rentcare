import java.net.URI

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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "VENDOR_ID", "\"${prop("rentcare.vendorId", "demo-jeju")}\"")
    }

    buildTypes {
        debug {
            // Cleartext only to the emulator host loopback, see res/xml/network_security_config_debug.xml
            manifestPlaceholders["networkConfig"] = "@xml/network_security_config_debug"
            buildConfigField("String", "API_BASE_URL", "\"${prop("rentcare.apiBaseUrl", "http://10.0.2.2:5173")}\"")
            buildConfigField("String", "CAR_API_KEY", "\"${prop("rentcare.carApiKey", "dev-car-key")}\"")
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["networkConfig"] = "@xml/network_security_config"
            // No default: a release must name its HTTPS domain explicitly (Pleos security guide).
            buildConfigField("String", "API_BASE_URL", "\"${prop("rentcare.releaseApiBaseUrl", "")}\"")
            buildConfigField("String", "CAR_API_KEY", "\"${prop("rentcare.releaseCarApiKey", "")}\"")
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

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(kotlin("test"))
}

// Fail release builds that would ship an HTTP / IP / empty server address.
val checkReleaseConfig by tasks.registering {
    val url = prop("rentcare.releaseApiBaseUrl", "")
    val key = prop("rentcare.releaseCarApiKey", "")
    doLast {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        val isIp = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host) || host.contains(':') || !host.contains('.')
        require(url.startsWith("https://") && host.isNotEmpty() && !isIp && host != "localhost") {
            "rentcare.releaseApiBaseUrl must be an https:// domain (got '$url')"
        }
        require(key.isNotBlank()) { "rentcare.releaseCarApiKey is required for release" }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseConfig) }
