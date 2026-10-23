plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "de.troetenrepublik.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "de.troetenrepublik.app"
        minSdk = 26
        targetSdk = 35
        // Beim Bau als Eigenschaft gesetzt und mit jeder Fassung um eins hoeher (5.2 = 221), damit Android die APK als
        // Aktualisierung annimmt.
        versionCode = providers.gradleProperty("troete.versionCode").orNull?.toIntOrNull() ?: 43
        versionName = "5.9"
        // Fuer den Fotolauf der Kurzanleitung im Emulator
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release-Signatur nur mit Schluessel aus den GitHub-Secrets (.github/workflows/apk.yml), sonst bleibt es bei Debug.
    val schluessel = System.getenv("TROETE_KEYSTORE_DATEI")?.takeIf { it.isNotBlank() && file(it).exists() }
    signingConfigs {
        if (schluessel != null) create("troete") {
            storeFile = file(schluessel)
            storePassword = System.getenv("TROETE_STORE_PW")
            keyAlias = System.getenv("TROETE_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "troete"
            keyPassword = System.getenv("TROETE_KEY_PW")?.takeIf { it.isNotBlank() } ?: System.getenv("TROETE_STORE_PW")
        }
    }

    buildTypes {
        release {
            // Mit R8 und ohne Debug-Schalter: Compose laeuft damit beim Scrollen und Wischen deutlich fluessiger
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Ohne eigenen Schluessel mit dem Debug-Schluessel, damit die APK die bisherigen Fassungen ersetzt
            signingConfig = if (schluessel != null) signingConfigs.getByName("troete") else signingConfigs.getByName("debug")
        }
    }
    // Lint soll den Release-Bau nicht aufhalten.
    lint { checkReleaseBuilds = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // Gemeinsamer Code liegt in src/main/java, Android-eigener in src/android/java.
    sourceSets["main"].java.srcDir("src/android/java")
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
    // Die Sprache wird in der App umgeschaltet, deshalb kein Aufteilen des Bundles nach Sprache.
    bundle { language { enableSplit = false } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.webkit:webkit:1.12.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
