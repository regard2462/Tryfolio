plugins {
    kotlin("multiplatform") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
}

// Tryfolio fuer iPhone und iPad: derselbe gemeinsame Code wie Android und Desktop (app/src/main/java), uebersetzt mit
// Kotlin/Native. Was dort Java-Bibliotheken braucht (Datum, Dateien, Netz ueber OkHttp), liefert src/iosMain/kotlin/shims
// mit gleichen Namen nach. Die iOS-Gegenstuecke der Plattformobjekte liegen in src/iosMain/kotlin/.../plattform.
// Das Ergebnis ist das statische Framework TryfolioKit, das die Xcode-Huelle in iosApp/ einbindet.

val gemeinsam = rootDir.resolve("../app/src/main/java").normalize()

/** Je Paket des gemeinsamen Codes die JVM-Teile der Standardbibliothek (System, Math, String.format ...). */
val iosJvm by tasks.registering {
    val vorlage = file("jvm-vorlage.kt")
    val ziel = layout.buildDirectory.dir("generated/iosJvm")
    inputs.file(vorlage)
    inputs.dir(gemeinsam)
    outputs.dir(ziel)
    doLast {
        val text = vorlage.readText()
        val pakete = gemeinsam.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.parentFile.relativeTo(gemeinsam).path.replace(File.separatorChar, '.') }.toSet() + "de.troetenrepublik.app.plattform"
        val wurzel = ziel.get().asFile.apply { deleteRecursively(); mkdirs() }
        for (p in pakete) File(wurzel, p.replace('.', '/') + "/Jvm.kt").apply { parentFile.mkdirs() }.writeText(text.replace("package PAKET", "package $p"))
    }
}

// Stellen im gemeinsamen Code, deren Compose-Aufruf sich zwischen Compose 1.7 (Android, Desktop) und Compose 1.9
// (iOS) unterscheidet. Beim Bauen entsteht eine angepasste Kopie der Datei; aendert sich die Stelle im gemeinsamen Code,
// bricht der Bau mit einem Hinweis ab, statt still etwas anderes zu uebersetzen.
val anpassungen: Map<String, List<Pair<String, String>>> = mapOf(
    // SheetState kennt in Material3 1.9 statt der Dichte die beiden Schwellen (Werte wie BottomSheetDefaults)
    "de/troetenrepublik/app/ui/Wiederkehr.kt" to listOf(
        "SheetState(skipPartiallyExpanded = true, density = dichte, initialValue = SheetValue.Expanded)" to
            "SheetState(skipPartiallyExpanded = true, positionalThreshold = { with(dichte) { androidx.compose.ui.unit.Dp(56f).toPx() } }, " +
            "velocityThreshold = { with(dichte) { androidx.compose.ui.unit.Dp(125f).toPx() } }, initialValue = SheetValue.Expanded)",
    ),
)

val iosAnpassung by tasks.registering {
    val ziel = layout.buildDirectory.dir("generated/iosAnpassung")
    inputs.files(anpassungen.keys.map { File(gemeinsam, it) })
    inputs.property("anpassungen", anpassungen.toString())
    outputs.dir(ziel)
    doLast {
        val wurzel = ziel.get().asFile.apply { deleteRecursively(); mkdirs() }
        for ((pfad, ersetzungen) in anpassungen) {
            var text = File(gemeinsam, pfad).readText()
            for ((alt, neu) in ersetzungen) {
                check(text.contains(alt)) { "$pfad hat sich geaendert, die iOS-Anpassung passt nicht mehr: $alt" }
                text = text.replace(alt, neu)
            }
            File(wurzel, pfad).apply { parentFile.mkdirs() }.writeText(text)
        }
    }
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { ziel ->
        // Statisch wie in der Vorlage von JetBrains; die Xcode-Huelle bindet es ueber embedAndSignAppleFrameworkForXcode ein
        ziel.binaries.framework {
            baseName = "TryfolioKit"
            isStatic = true
            binaryOption("bundleId", "de.troetenrepublik.app.kit")
        }
    }

    compilerOptions {
        optIn.addAll(
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlinx.cinterop.BetaInteropApi",
            "kotlin.experimental.ExperimentalNativeApi",
        )
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        iosMain {
            kotlin.srcDir(gemeinsam)
            kotlin.srcDir(iosJvm)
            kotlin.srcDir(iosAnpassung)
            // Ersetzt durch die angepassten Kopien oben
            kotlin.exclude { e -> e.file.toPath().startsWith(gemeinsam.toPath()) && e.file.relativeTo(gemeinsam).invariantSeparatorsPath in anpassungen }
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.9.3")
                implementation("org.jetbrains.compose.foundation:foundation:1.9.3")
                implementation("org.jetbrains.compose.ui:ui:1.9.3")
                implementation("org.jetbrains.compose.animation:animation:1.9.3")
                implementation("org.jetbrains.compose.material3:material3:1.9.0")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
                implementation("androidx.datastore:datastore-preferences-core:1.1.7")
                implementation("com.squareup.okio:okio:3.9.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:atomicfu:0.29.0")
            }
        }
    }
}
