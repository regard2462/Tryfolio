plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "de.troetenrepublik.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "de.troetenrepublik.app"
        minSdk = 26
        targetSdk = 36
        // Beim Bau als Eigenschaft gesetzt und mit jeder Fassung um eins hoeher (5.2 = 221), damit Android die APK als
        // Aktualisierung annimmt.
        versionCode = providers.gradleProperty("troete.versionCode").orNull?.toIntOrNull() ?: 43
        versionName = "0.79"
        // Fuer den Fotolauf der Kurzanleitung im Emulator
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release-Signatur mit dem Schluessel aus TROETE_KEYSTORE_DATEI (GitHub-Secrets, .github/workflows/apk.yml). Ohne
    // Schluessel brechen die Release-Aufgaben ab, ausser lokal mit -PdebugSigniert=true (Debug-Schluessel, nicht ausliefern).
    val debugSigniert = providers.gradleProperty("debugSigniert").orNull == "true"
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
            signingConfig = when {
                schluessel != null -> signingConfigs.getByName("troete")
                debugSigniert -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }
    // Lint soll den Release-Bau nicht aufhalten.
    lint { checkReleaseBuilds = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // [0.71] Neue Compiler-Options-DSL (kotlinOptions entfaellt mit Kotlin 2.4)
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures { compose = true }
    // Gemeinsamer Code liegt in src/main/java, Android-eigener in src/android/java.
    sourceSets["main"].java.srcDir("src/android/java")
    // Tests fuer Android-eigenen Code; der Desktop kompiliert src/test/java mit und kennt diesen Code nicht.
    sourceSets["test"].java.srcDir("src/testAndroid/java")
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
    // Die Sprache wird in der App umgeschaltet, deshalb kein Aufteilen des Bundles nach Sprache.
    bundle { language { enableSplit = false } }
}

// Kein Release-Paket ohne Schluessel: sonst entstuende still eine mit dem Debug-Schluessel signierte APK
gradle.taskGraph.whenReady {
    val betroffen = allTasks.filter {
        it.project == project && Regex("(assemble|bundle|package|sign)(.*)Release.*").matches(it.name)
    }
    if (betroffen.isNotEmpty() && System.getenv("TROETE_KEYSTORE_DATEI")?.takeIf { f -> f.isNotBlank() && file(f).exists() } == null &&
        providers.gradleProperty("debugSigniert").orNull != "true"
    ) {
        throw GradleException(
            "Release ohne Schluessel: TROETE_KEYSTORE_DATEI setzen (Datei muss existieren) oder fuer einen lokalen " +
                "Testbau -PdebugSigniert=true angeben. Betroffen: " + betroffen.joinToString { it.path },
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.webkit:webkit:1.17.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Abhaengigkeiten der Laufzeit festgeschrieben (app/gradle.lockfile). Erneuern nach einer Aenderung der Abhaengigkeiten:
// ./gradlew :app:dependencies --write-locks. Streng: eine Abhaengigkeit ausserhalb der Datei bricht den Bau ab.
configurations.matching { it.name == "debugRuntimeClasspath" || it.name == "releaseRuntimeClasspath" }.configureEach {
    resolutionStrategy.activateDependencyLocking()
}
dependencyLocking { lockMode.set(LockMode.STRICT) }

// Gesundheitscheck der Datenquellen gegen die echten Quellen nur mit -Dtryfolio.live=true (Fassung592QuellenLiveTest);
// die Ausgabe HEALTH|... steht dann im Testbericht und mit --info in der Konsole.
tasks.withType<Test>().configureEach {
    (System.getProperty("tryfolio.live") ?: providers.gradleProperty("tryfolio.live").orNull)?.let { systemProperty("tryfolio.live", it) }
}
