import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.time.Duration

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// MSI verlangt drei Zahlen. Muss zu versionName in app/build.gradle.kts passen.
val troeteVersion = "0.79.0"

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
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
    implementation("androidx.datastore:datastore-preferences-core:1.2.1")

    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jsoup:jsoup:1.24.1")
    // Eingebettetes Chromium fuer MarketWatch-Artikel und das Finanzwissen. Die nativen Teile laedt die App bei Bedarf
    implementation("me.friwi:jcefmaven:152.0.6")

    testImplementation("junit:junit:4.13.2")
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

// Abhaengigkeiten der Laufzeit festgeschrieben (desktop/gradle.lockfile). Erneuern nach einer Aenderung:
// ./gradlew :desktop:dependencies --write-locks. Die nativen Teile von Compose und Skiko sind je Betriebssystem andere
// Bausteine (Windows, Linux, macOS) und daher ausgenommen; ihre Version folgt Compose.
configurations.matching { it.name == "runtimeClasspath" }.configureEach {
    resolutionStrategy.activateDependencyLocking()
}
dependencyLocking {
    ignoredDependencies.add("org.jetbrains.compose.desktop:desktop-jvm-*")
    ignoredDependencies.add("org.jetbrains.skiko:skiko-awt-runtime-*")
}

tasks.test {
    // Bildschirmtests rendern ohne Fenster, Daten nur im Testordner
    // Gesundheitscheck der Datenquellen nur mit -Dtryfolio.live=true (Fassung592QuellenLiveTest)
    (System.getProperty("tryfolio.live") ?: providers.gradleProperty("tryfolio.live").orNull)?.let { systemProperty("tryfolio.live", it) }
    systemProperty("java.awt.headless", "true")
    systemProperty("troete.browser", "aus")
    // Die Rundgaenge dauern laenger als die Vorgabe von einer Minute je Test
    systemProperty("kotlinx.coroutines.test.default_timeout", "30m")
    systemProperty("troete.daten", layout.buildDirectory.dir("testdaten").get().asFile.absolutePath)
    systemProperty("troete.bilder", layout.buildDirectory.dir("testbilder").get().asFile.absolutePath)
    maxHeapSize = "2g"
    jvmArgs("--add-exports=java.desktop/sun.awt=ALL-UNNAMED", "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED", "--add-exports=java.desktop/sun.awt.windows=ALL-UNNAMED")
    // Haertetest der Kongress-Meldungen nur mit -Pkongress.ordner=<Ordner mit PDFs>
    providers.gradleProperty("kongress.ordner").orNull?.let { systemProperty("kongress.ordner", it) }
    // Bilder fuer die README nur mit -Ptroete.readme=alle oder mit einzelnen Motiven (-Ptroete.readme=behoerden,news)
    providers.gradleProperty("troete.readme").orNull?.let { systemProperty("troete.readme", it) }
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
// gradlew :desktop:indikatorAllgemein -Pziel=../app/src/main/assets/indikatoren/allgemein.json -Panzahl=150
tasks.register<JavaExec>("indikatorAllgemein") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.troetenrepublik.app.werkzeuge.IndikatorAllgemeinBauenKt")
    systemProperty("java.awt.headless", "true")
    systemProperty("troete.daten", layout.buildDirectory.dir("werkzeugdaten").get().asFile.absolutePath)
    args(
        providers.gradleProperty("ziel").map { file(it).absolutePath }.getOrElse(""),
        providers.gradleProperty("anzahl").getOrElse("150"),
        rootProject.file("app/src/main/assets/katalog.json").absolutePath,
    )
}

// Rueckblick auf die Tendenz unter „Im Preis?“ (Werkzeug, nicht Teil der App), Rohdaten unter build/werkzeugdaten:
// gradlew :desktop:tendenzRueckblick -Pziel=../app/src/main/assets/indikatoren/tendenz_rueckblick.json
tasks.register<JavaExec>("tendenzRueckblick") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.troetenrepublik.app.werkzeuge.TendenzRueckblickBauenKt")
    systemProperty("java.awt.headless", "true")
    systemProperty("troete.daten", layout.buildDirectory.dir("werkzeugdaten").get().asFile.absolutePath)
    maxHeapSize = "2g"
    args(
        providers.gradleProperty("ziel").map { file(it).absolutePath }.getOrElse(""),
        rootProject.file("app/src/main/assets/katalog.json").absolutePath,
        rootProject.file("app/src/main/assets").absolutePath,
        providers.gradleProperty("teil").getOrElse("alle"),
    )
}

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

// Rueckblick-Test des Aktienfinders (Werkzeug, nicht Teil der App), schreibt nur Kennzahlen:
// gradlew :desktop:finderRueckblick [-Pfacts=<companyfacts.zip>] [-Pbericht=<Datei fuer Tabellen>] [-Puniversum=ndx100]
// Mit -Puniversum=ndx100 gegen die Mitglieder des Nasdaq-100 (indizes/ndx100_mitglieder.json), Ziel finder_rueckblick_ndx100.json.
tasks.register<JavaExec>("finderRueckblick") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.troetenrepublik.app.werkzeuge.FinderRueckblickBauenKt")
    systemProperty("java.awt.headless", "true")
    systemProperty("troete.daten", layout.buildDirectory.dir("werkzeugdaten").get().asFile.absolutePath)
    maxHeapSize = "2g"
    args(
        providers.gradleProperty("ziel").map { file(it).absolutePath }.getOrElse(
            rootProject.file("app/src/main/assets/indikatoren/finder_rueckblick" + providers.gradleProperty("universum").map { "_$it" }.getOrElse("") + ".json").absolutePath,
        ),
        providers.gradleProperty("bericht").map { file(it).absolutePath }.getOrElse(""),
        providers.gradleProperty("facts").map { file(it).absolutePath }.getOrElse(""),
        rootProject.file("app/src/main/assets/katalog.json").absolutePath,
        rootProject.file("app/src/main/assets/copytrading/sec.json").absolutePath,
        rootProject.file("app/src/main/assets/indizes/" + providers.gradleProperty("universum").getOrElse("sp500") + "_mitglieder.json").absolutePath,
        providers.gradleProperty("universum").getOrElse(""),
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
            copyright = "AGPL-3.0-or-later"
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

// Rueckblick „Haette man es sehen koennen?“ (Werkzeug, nicht Teil der App): rechnet aus den Rohreihen, die
// tools/warnrueckblick/holen.py holt, die mitgelieferte Datei:
// gradlew :desktop:warnzeichenRueckblick -Pdaten=<Ordner> [-Pziel=<Datei>] [-Pketten=<ketten.json>]
tasks.register<JavaExec>("warnzeichenRueckblick") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.troetenrepublik.app.werkzeuge.WarnzeichenRueckblickBauenKt")
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "2g"
    args(
        providers.gradleProperty("daten").map { file(it).absolutePath }.getOrElse(""),
        providers.gradleProperty("ziel").map { file(it).absolutePath }.getOrElse(rootProject.file("app/src/main/assets/indikatoren/warnzeichen_rueckblick.json").absolutePath),
        providers.gradleProperty("ketten").map { file(it).absolutePath }.getOrElse(rootProject.file("tools/warnrueckblick/ketten.json").absolutePath),
    )
}

// Praesidentschaftszyklus und Saisonmuster (Werkzeug, nicht Teil der App): rechnet aus den Tagesreihen, die
// tools/warnrueckblick/holen.py holt, die mitgelieferte Datei: gradlew :desktop:kursmuster -Pdaten=<Ordner> [-Pziel=<Datei>]
// [-Phalbjahr=<Datei>]; die Halbjahresregel steht in assets/indikatoren/halbjahr.json
tasks.register<JavaExec>("kursmuster") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.troetenrepublik.app.werkzeuge.KursmusterBauenKt")
    systemProperty("java.awt.headless", "true")
    args(
        providers.gradleProperty("daten").map { file(it).absolutePath }.getOrElse(""),
        providers.gradleProperty("ziel").map { file(it).absolutePath }.getOrElse(rootProject.file("app/src/main/assets/indikatoren/kursmuster.json").absolutePath),
        providers.gradleProperty("halbjahr").map { file(it).absolutePath }.getOrElse(rootProject.file("app/src/main/assets/indikatoren/halbjahr.json").absolutePath),
    )
}

// Rueckblick fuer Bodenzeichen und Blasen-Detektor (Werkzeug, nicht Teil der App): rechnet aus den Rohreihen, die
// tools/warnrueckblick/holen.py holt, die mitgelieferte Datei:
// gradlew :desktop:zeichenRueckblick -Pdaten=<Ordner> [-Pziel=<Datei>] [-Pbericht=<Datei>]
tasks.register<JavaExec>("zeichenRueckblick") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.troetenrepublik.app.werkzeuge.ZeichenRueckblickBauenKt")
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "2g"
    args(
        providers.gradleProperty("daten").map { file(it).absolutePath }.getOrElse(""),
        providers.gradleProperty("ziel").map { file(it).absolutePath }.getOrElse(rootProject.file("app/src/main/assets/indikatoren/zeichen_rueckblick.json").absolutePath),
        providers.gradleProperty("bericht").map { file(it).absolutePath }.getOrElse(""),
    )
}
