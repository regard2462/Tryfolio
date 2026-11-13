package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Browserkennung
import de.troetenrepublik.app.market.Quellen
import de.troetenrepublik.app.engine.Quellenbuendel.Ergebnis
import de.troetenrepublik.app.engine.Quellenbuendel.Grund
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.time.LocalDate

// Haupt- und Ersatzschluessel, Mindestfassung, gezielte Hinweise, mitlaufende Browser-Kennung.
class AlterungSchluesselTest {

    private val sha512: (ByteArray) -> ByteArray = { MessageDigest.getInstance("SHA-512").digest(it) }
    private fun tag(s: String): Long = LocalDate.parse(s).toEpochDay() * 86_400_000L + 3_600_000L

    @After fun zurueck() { Quellen.fuerTest(null); Uhr.vorstellenTage(0) }

    // Signaturen aus tools/quellen/signieren.py ueber diesen Text: einmal mit dem Hauptschluessel, einmal mit dem Ersatz
    private val text = """{"version":9,"notices":[{"id":"ersatz-test","title":"T"}]}"""
    private val sigHaupt = "4b7e27ab1ba8e5f2fe7d80697941c08fe87f4805be962bde3c4d8c7718c71d2e181c18ec0bad577bec6dc3ec0d64ae8686b888e94a39b7a20bacf8f254fb930d"
    private val sigErsatz = "d563c01f6b99df66ac0ce1e617b4d6d6a9f9ce186e69fd735283f2c0aa9a9092e60c059962aaa5e8e6ae918acf7eb17f84fd8f02215df73996e62b358948d309"

    @Test fun buendelGiltMitHauptOderErsatzschluessel() {
        assertEquals(2, Quellen.schluessel.size)
        assertTrue(Quellenbuendel.pruefen(text.toByteArray(), sigHaupt, Quellen.schluessel, 1, sha512) is Ergebnis.Angenommen)
        assertTrue(Quellenbuendel.pruefen(text.toByteArray(), sigErsatz, Quellen.schluessel, 1, sha512) is Ergebnis.Angenommen)
        // Mit nur dem Ersatzschluessel: die Signatur des Hauptschluessels gilt nicht und umgekehrt
        val nurErsatz = listOf(Quellen.schluessel[1])
        assertEquals(Ergebnis.Abgelehnt(Grund.SIGNATUR), Quellenbuendel.pruefen(text.toByteArray(), sigHaupt, nurErsatz, 1, sha512))
        assertTrue(Quellenbuendel.pruefen(text.toByteArray(), sigErsatz, nurErsatz, 1, sha512) is Ergebnis.Angenommen)
        // Anderer Text, gleiche Signatur
        assertEquals(Ergebnis.Abgelehnt(Grund.SIGNATUR), Quellenbuendel.pruefen((text + " ").toByteArray(), sigErsatz, Quellen.schluessel, 1, sha512))
        // Unbekannter Schluessel
        val fremd = Ed25519.oeffentlich(ByteArray(32) { 7 }, sha512)
        assertEquals(Ergebnis.Abgelehnt(Grund.SIGNATUR), Quellenbuendel.pruefen(text.toByteArray(), sigErsatz, listOf(fremd), 1, sha512))
    }

    @Test fun beideSchluesselSindVerschieden() {
        assertFalse(Quellen.SCHLUESSEL == Quellen.ERSATZ_SCHLUESSEL)
        assertEquals(64, Quellen.ERSATZ_SCHLUESSEL.length)
    }

    @Test fun mindestfassungDesBuendels() {
        val b = Quellenbuendel.lesen("""{"version":3,"minApp":"1.0"}""")!!
        assertEquals("1.0", b.minApp)
        // Die Signatur wird zuerst geprueft; hier eine Pruefung ohne Signaturteil ueber lesen + Vergleich
        assertTrue(de.troetenrepublik.app.market.Aktualisierung.neuer(b.minApp, "0.73"))
        assertFalse(de.troetenrepublik.app.market.Aktualisierung.neuer("0.73", "0.73"))
        assertEquals("", Quellenbuendel.lesen("""{"version":3}""")!!.minApp)
    }

    @Test fun hinweiseGeziltNachFassung() {
        val b = Quellenbuendel.lesen(
            """{"version":2,"notices":[{"id":"alt","title":"A","maxApp":"0.72"},{"id":"neu","title":"N","minApp":"0.80"},""" +
                """{"id":"mitte","title":"M","minApp":"0.70","maxApp":"0.75"},{"id":"alle","title":"X"}]}""",
        )!!
        val ids = b.hinweise.filter { it.gilt("0.73") }.map { it.id }
        assertEquals(listOf("mitte", "alle"), ids)
        assertEquals(listOf("alt", "alle"), b.hinweise.filter { it.gilt("0.60") }.map { it.id })
    }

    @Test fun chromeKennungLaeuftMit() {
        assertEquals(154, Browserkennung.hauptAb(154, "2026-11-09", tag("2026-11-09")))
        assertEquals(154, Browserkennung.hauptAb(154, "2026-11-09", tag("2026-12-06")))
        assertEquals(155, Browserkennung.hauptAb(154, "2026-11-09", tag("2026-12-07")))
        // nach einem, drei und fuenf Jahren
        assertEquals(154 + 13, Browserkennung.hauptAb(154, "2026-11-09", tag("2027-11-09")))
        assertEquals(154 + 39, Browserkennung.hauptAb(154, "2026-11-09", tag("2029-11-09")))
        assertEquals(154 + 65, Browserkennung.hauptAb(154, "2026-11-09", tag("2031-11-09")))
        assertEquals(154 + 65, Browserkennung.hauptAb(154, "2026-11-09", tag("2040-01-01")))
        // Uhr vor dem Ausgangspunkt oder kaputtes Datum
        assertEquals(154, Browserkennung.hauptAb(154, "2026-11-09", tag("2020-01-01")))
        assertEquals(0, Browserkennung.hauptAb(154, "kaputt", tag("2027-01-01")))
        assertTrue(Browserkennung.windows(tag("2029-11-09")).contains("Chrome/193.0.0.0"))
        assertTrue(Browserkennung.android(tag("2029-11-09")).contains("Chrome/193.0.0.0 Mobile Safari"))
        assertTrue(Browserkennung.ios(tag("2025-10-01")).contains("Version/26.0 Mobile"))
        assertTrue(Browserkennung.ios(tag("2026-03-01")).contains("Version/26.0 Mobile"))
        assertTrue(Browserkennung.ios(tag("2026-10-01")).contains("Version/27.0 Mobile"))
        assertTrue(Browserkennung.ios(tag("2029-10-01")).contains("Version/30.0 Mobile"))
    }

    @Test fun chromeKennungAusDemBuendel() {
        // Neuerer Ausgangspunkt aus dem Buendel gewinnt, ein aelterer nicht
        val neu = Quellenbuendel.lesen("""{"version":2,"browser":{"chrome":200,"ab":"2028-01-01"}}""")!!
        assertEquals(200, neu.browserChrome)
        Quellen.fuerTest(neu)
        assertEquals(200, Browserkennung.chromeHaupt(tag("2028-01-01")))
        assertEquals(207, Browserkennung.chromeHaupt(tag("2028-08-01")))
        val alt = Quellenbuendel.lesen("""{"version":3,"browser":{"chrome":120,"ab":"2024-12-01"}}""")!!
        Quellen.fuerTest(alt)
        assertEquals(154, Browserkennung.chromeHaupt(tag("2026-11-09")))
        // Unsinn im Buendel wird ignoriert
        val kaputt = Quellenbuendel.lesen("""{"version":4,"browser":{"chrome":5,"ab":"gestern"}}""")!!
        assertEquals(0, kaputt.browserChrome)
        Quellen.fuerTest(kaputt)
        assertEquals(154, Browserkennung.chromeHaupt(tag("2026-11-09")))
    }

    @Test fun kennungDerAppFolgtDerUhr() {
        val vorher = Browserkennung.chromeHaupt()
        Uhr.vorstellenTage(1095)
        assertTrue(Browserkennung.chromeHaupt() >= vorher + 38)
        assertTrue(de.troetenrepublik.app.market.Ersatzabrufe.KENNUNG.contains("Chrome/${Browserkennung.chromeHaupt()}.0.0.0"))
    }
}
