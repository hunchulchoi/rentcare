import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun prop(name: String, default: String) = (findProperty(name) as String?) ?: default

// Server address and car key come from web/.env (one place for both sides). Gradle -P overrides.
// Move hosts by editing PUBLIC_BASE_URL there, e.g. https://rentcare.devfor.link
val webEnv: Map<String, String> = rootProject.file("../web/.env").takeIf { it.exists() }?.readLines()
    ?.mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() && !it.startsWith("#") && '=' in it } }
    ?.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    .orEmpty()
val envBaseUrl = webEnv["PUBLIC_BASE_URL"].orEmpty()
val envCarKey = webEnv["CAR_API_KEY"].orEmpty()

android {
    namespace = "com.rentcare.car"
    compileSdk = 35
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.rentcare.car"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "VENDOR_ID", "\"${prop("rentcare.vendorId", "demo-jeju")}\"")
    }

    // Release signing: keystore lives outside the repo; secrets come from Gradle properties or env.
    //   rentcare.signing.storeFile / RENTCARE_SIGNING_STORE_FILE
    //   rentcare.signing.storePassword / RENTCARE_SIGNING_STORE_PASSWORD
    //   rentcare.signing.keyAlias / RENTCARE_SIGNING_KEY_ALIAS
    //   rentcare.signing.keyPassword / RENTCARE_SIGNING_KEY_PASSWORD
    fun secret(p: String, env: String) = (findProperty(p) as String?) ?: System.getenv(env)
    val storeFilePath = secret("rentcare.signing.storeFile", "RENTCARE_SIGNING_STORE_FILE")
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = secret("rentcare.signing.storePassword", "RENTCARE_SIGNING_STORE_PASSWORD")
                keyAlias = secret("rentcare.signing.keyAlias", "RENTCARE_SIGNING_KEY_ALIAS")
                keyPassword = secret("rentcare.signing.keyPassword", "RENTCARE_SIGNING_KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true // key rotation / lineage (Pleos: why minSdk >= 28)
            }
        }
    }

    buildTypes {
        debug {
            // Cleartext only to the emulator host loopback, see res/xml/network_security_config_debug.xml
            manifestPlaceholders["networkConfig"] = "@xml/network_security_config_debug"
            buildConfigField("String", "API_BASE_URL", "\"${prop("rentcare.apiBaseUrl", envBaseUrl.ifEmpty { "http://10.0.2.2:5173" })}\"")
            buildConfigField("String", "CAR_API_KEY", "\"${prop("rentcare.carApiKey", envCarKey.ifEmpty { "dev-car-key" })}\"")
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
            manifestPlaceholders["networkConfig"] = "@xml/network_security_config"
            // No default: a release must name its HTTPS domain explicitly (Pleos security guide).
            buildConfigField("String", "API_BASE_URL", "\"${prop("rentcare.releaseApiBaseUrl", envBaseUrl)}\"")
            buildConfigField("String", "CAR_API_KEY", "\"${prop("rentcare.releaseCarApiKey", envCarKey)}\"")
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
    val url = prop("rentcare.releaseApiBaseUrl", envBaseUrl)
    val key = prop("rentcare.releaseCarApiKey", envCarKey)
    doLast {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        val isIp = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host) || host.contains(':') || !host.contains('.')
        require(url.startsWith("https://") && host.isNotEmpty() && !isIp && host != "localhost") {
            "rentcare.releaseApiBaseUrl must be an https:// domain (got '$url')"
        }
        require(key.isNotBlank()) { "rentcare.releaseCarApiKey is required for release" }
        require(project.findProperty("rentcare.allowUnsigned") == "true" ||
            ((findProperty("rentcare.signing.storeFile") ?: System.getenv("RENTCARE_SIGNING_STORE_FILE")) != null)) {
            "release signing is not configured (set rentcare.signing.* or -Prentcare.allowUnsigned=true for a local check)"
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseConfig) }

// Contest naming: <team>_<app>_release.apk (RENTCARE-30). Set rentcare.team, default "team".
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { out ->
            (out as? com.android.build.api.variant.impl.VariantOutputImpl)?.outputFileName?.set(
                "${prop("rentcare.team", "team")}_RentCare_release.apk",
            )
        }
    }
}
