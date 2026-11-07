package de.troetenrepublik.app.market

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Fassung 5.92: keine E-Mail-Adresse in Datenabrufen.
class Fassung592OhneAdresseTest {
    private fun kopf(vararg p: Pair<String, String>) = p.toList()

    @Test fun nurSecAusgenommen() {
        assertTrue(OhneAdresse.sec("data.sec.gov"))
        assertTrue(OhneAdresse.sec("www.sec.gov"))
        assertFalse(OhneAdresse.sec("notsec.gov"))
        assertFalse(OhneAdresse.sec("sec.gov.example.org"))
    }

    @Test fun adresseInKopfzeileErkannt() {
        assertTrue(OhneAdresse.enthaelt("https://data.sec.gov/x".toHttpUrl(), kopf("User-Agent" to "Tryfolio/5.92 jemand@example.org")))
    }

    @Test fun adresseInAbfrageErkannt() {
        assertTrue(OhneAdresse.enthaelt("https://example.org/api?mail=jemand%40example.org".toHttpUrl(), kopf()))
        assertTrue(OhneAdresse.enthaelt("https://example.org/api?mail=jemand@example.org".toHttpUrl(), kopf()))
    }

    @Test fun normaleAbrufeBleiben() {
        assertFalse(OhneAdresse.enthaelt("https://cdn.jsdelivr.net/gh/regard2462/Tryfolio@main/quellen.json".toHttpUrl(),
            kopf("User-Agent" to "Tryfolio/5.92 (open-source practice depot)")))
        assertFalse(OhneAdresse.enthaelt("https://query1.finance.yahoo.com/v8/finance/chart/SAP.DE?range=1y&interval=1d".toHttpUrl(),
            kopf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "Accept-Language" to "de-DE,de;q=0.9")))
    }

    // Keine feste Kennung im Code enthaelt eine Adresse; Noreply-Adressen von Code-Hostern zaehlen auch.
    @Test fun keineAdresseInKennungen() {
        val wurzel = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val treffer = listOf("app/src/main", "app/src/android", "desktop/src/main").map { File(wurzel, it) }.filter { it.exists() }
            .flatMap { d -> d.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .flatMap { f -> f.readLines().filter { "User-Agent" in it || "UA =" in it }.filter { OhneAdresse.enthaelt(it) }.map { f.name + ": " + it.trim() } }
        assertTrue(treffer.joinToString("\n"), treffer.isEmpty())
    }
}
