import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Ondertekening: op GitHub uit de geheime instellingen, lokaal uit
// ../iptimtv-release.properties (staat buiten de repo).
val localSigning = Properties().apply {
    val file = rootProject.file("../iptimtv-release.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signing(name: String): String? = System.getenv(name) ?: localSigning.getProperty(name)

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "nl.timvanbergen.iptimtv"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "nl.timvanbergen.iptimtv"
        minSdk = 26
        targetSdk = 37
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Twee apps uit dezelfde code: één voor de tv (afstandsbediening) en één voor de telefoon (touch).
    flavorDimensions += "device"
    productFlavors {
        create("tv") {
            dimension = "device"
            buildConfigField("boolean", "MOBILE", "false")
            buildConfigField("String", "APK_NAME", "\"IPTIMTV.apk\"")
        }
        create("mobile") {
            dimension = "device"
            applicationIdSuffix = ".mobile"
            buildConfigField("boolean", "MOBILE", "true")
            buildConfigField("String", "APK_NAME", "\"IPTIMTV-mobile.apk\"")
        }
    }

    signingConfigs {
        create("release") {
            signing("IPTIMTV_KEYSTORE")?.let { storeFile = file(it) }
            storePassword = signing("IPTIMTV_KEYSTORE_PASSWORD")
            keyAlias = signing("IPTIMTV_KEY_ALIAS")
            keyPassword = signing("IPTIMTV_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Geoptimaliseerde code: Compose is zonder R8 merkbaar trager op tv-chips.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val media3 = "1.8.0"
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    // Telefoon: Material 3 voor een echte telefoon-app, en casten naar Chromecast.
    implementation("androidx.compose.material3:material3")
    implementation("androidx.media3:media3-cast:$media3")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    // Laat Android de app na installatie vooraf compileren (snellere start en soepeler scrollen).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
