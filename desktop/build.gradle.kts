import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.Duration

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// MSI verlangt drei Zahlen. Muss zu versionName in app/build.gradle.kts passen.
val troeteVersion = "5.9.0"

// Der gemeinsame Code liegt im Android-Modul unter app/src/main/java.
// Was nur auf Android laeuft, liegt in app/src/android/java. Die gleichnamigen Desktop-Gegenstuecke
// liegen hier in src/main/kotlin (Paket de.troetenrepublik.app.plattform).
kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDirs("../app/src/main/java", "src/main/kotlin")
    sourceSets["test"].kotlin.srcDirs("../app/src/test/java", "src/test/kotlin")
}

sourceSets {
    // assets der App (Katalog, Finanzwissen) liegen im Klassenpfad an der Wurzel
    main { resources.srcDir("../app/src/main/assets") }
    test { resources.srcDir("../app/src/test/resources") }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.animation)
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences-core:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jsoup:jsoup:1.18.3")
    // Eingebettetes Chromium fuer Seiten, die nur ein Browser lesen darf. Die nativen Teile laedt die App bei Bedarf
    implementation("me.friwi:jcefmaven:152.0.6")

    testImplementation("junit:junit:4.13.2")
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

tasks.test {
    // Bildschirmtests rendern ohne Fenster, Daten nur im Testordner
    systemProperty("java.awt.headless", "true")
    systemProperty("troete.browser", "aus")
    systemProperty("troete.daten", layout.buildDirectory.dir("testdaten").get().asFile.absolutePath)
    systemProperty("troete.bilder", layout.buildDirectory.dir("testbilder").get().asFile.absolutePath)
    maxHeapSize = "2g"
    jvmArgs("--add-exports=java.desktop/sun.awt=ALL-UNNAMED", "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED", "--add-exports=java.desktop/sun.awt.windows=ALL-UNNAMED")
    // Haertetest der Kongress-Meldungen nur mit -Pkongress.ordner=<Ordner mit PDFs>
    providers.gradleProperty("kongress.ordner").orNull?.let { systemProperty("kongress.ordner", it) }
    // Ohne die Oberflaechen-Rundgaenge: auf den Windows-Rechnern von GitHub ohne Grafikkarte braucht ein Rundgang ueber
    // 40 Minuten. Die Rundgaenge laufen auf Linux und Mac
    if (providers.gradleProperty("troete.ohneRundgang").isPresent) filter { excludeTestsMatching("*OberflaecheTest") }
    // Auf GitHub jeden Test mit Namen ins Protokoll, damit ein haengender Test zu sehen ist. Nach 30 Minuten
    // bricht Gradle ab, statt stundenlang zu warten
    if (System.getenv("GITHUB_ACTIONS") == "true") {
        testLogging {
            events("started", "failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        timeout.set(Duration.ofMinutes(30))
    }
}

// Mitgelieferter Stand der Insidergeschaefte eines Landes (Werkzeug, nicht Teil der App):
// gradlew :desktop:insiderBuendel -Pland=FR -Pziel=../app/src/main/assets/copytrading/insider_fr.json -Ptage=365
tasks.register<JavaExec>("insiderBuendel") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.troetenrepublik.app.werkzeuge.InsiderBuendelKt")
    systemProperty("java.awt.headless", "true")
    systemProperty("troete.daten", layout.buildDirectory.dir("werkzeugdaten").get().asFile.absolutePath)
    args(
        providers.gradleProperty("land").getOrElse(""),
        providers.gradleProperty("ziel").map { file(it).absolutePath }.getOrElse(""),
        providers.gradleProperty("tage").getOrElse("365"),
    )
}

// Die Zeichenflaeche von JCEF (JOGL) braucht unter Java 17 Zugriff auf interne AWT-Pakete.
// Fehlt ein Paket auf einem System, etwa sun.awt.windows ausserhalb von Windows, warnt Java nur
val browserExports = listOf("sun.awt", "sun.java2d", "sun.awt.windows", "sun.awt.X11", "sun.lwawt", "sun.lwawt.macosx")
    .map { "--add-exports=java.desktop/$it=ALL-UNNAMED" }

compose.desktop {
    application {
        mainClass = "de.troetenrepublik.app.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xmx1g") + browserExports
        nativeDistributions {
            // Der MSI-Installer braucht WiX, das das Compose-Plugin selbst laedt. Lokal laesst er sich mit -Ptroete.ohneMsi
            // weglassen, zum Pruefen reicht die Programmfassung aus createDistributable
            val ohneMsi = providers.gradleProperty("troete.ohneMsi").isPresent
            targetFormats(*listOfNotNull(TargetFormat.Msi.takeUnless { ohneMsi }, TargetFormat.Deb).toTypedArray())
            packageName = "Tryfolio"
            packageVersion = troeteVersion
            description = "Übungsdepot mit echten Kursen"
            vendor = "Tryfolio"
            copyright = "GPL-3.0-or-later"
            licenseFile.set(rootProject.file("LICENSE"))
            // HTTPS mit EC-Zertifikaten, eingebauter Webserver fuer Finanzwissen, DataStore/Okio. jdk.localedata fuer deutsche
            // Monatsnamen und Zahlen, ohne das Modul kennt Java nur Englisch (Oct, 1,234.50)
            modules("java.instrument", "java.naming", "java.net.http", "jdk.crypto.ec", "jdk.httpserver", "jdk.unsupported", "java.management", "java.sql", "jdk.localedata")
            windows {
                menuGroup = "Tryfolio"
                upgradeUuid = "6b0c2f43-7a7e-4c39-9d65-2f5e8f1d9a21"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menu = true
                iconFile.set(project.file("icons/troete.ico"))
            }
            macOS {
                // Mac: Programm zum Entpacken, ohne Installation und ohne Signatur eines Entwicklerkontos
                bundleID = "de.troetenrepublik.desktop"
                appCategory = "public.app-category.finance"
                iconFile.set(project.file("icons/troete.icns"))
            }
            linux {
                packageName = "tryfolio"
                appCategory = "Office"
                menuGroup = "Office"
                shortcut = true
                iconFile.set(project.file("icons/troete.png"))
            }
        }
    }
}
