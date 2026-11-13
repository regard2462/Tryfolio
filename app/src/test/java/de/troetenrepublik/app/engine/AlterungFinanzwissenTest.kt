package de.troetenrepublik.app.engine

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

// Node-Tests fuer das Finanzwissen: Tarif, Basiszins, Steuerwerte aus der App und Rechtsstand bei vorgestellter Uhr
// (alterung.test.mjs) sowie ein Rechen-Smoke-Test aller Rechner (rechner.test.mjs). Ohne Node uebersprungen.
class AlterungFinanzwissenTest {

    private fun node(): String? = listOf("node", "node.exe").firstOrNull {
        runCatching { ProcessBuilder(it, "--version").redirectErrorStream(true).start().also { p -> p.inputStream.readBytes(); p.waitFor(20, TimeUnit.SECONDS) }.exitValue() == 0 }.getOrDefault(false)
    }

    private fun lauf(name: String) {
        val knoten = node()
        assumeTrue("node nicht gefunden", knoten != null)
        val datei = listOf(File("src/test/js/$name"), File("app/src/test/js/$name"), File("../app/src/test/js/$name")).first { it.exists() }
        val p = ProcessBuilder(knoten, "--test", datei.absolutePath).redirectErrorStream(true).start()
        val ausgabe = p.inputStream.readBytes().decodeToString()
        assertTrue(p.waitFor(120, TimeUnit.SECONDS))
        assertTrue(ausgabe, p.exitValue() == 0)
    }

    @Test fun jsZeitreise() = lauf("alterung.test.mjs")

    @Test fun jsRechner() = lauf("rechner.test.mjs")
}
