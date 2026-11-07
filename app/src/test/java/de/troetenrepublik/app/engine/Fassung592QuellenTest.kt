package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Quellenbuendel.Ampel
import de.troetenrepublik.app.engine.Quellenbuendel.Ausgang
import de.troetenrepublik.app.engine.Quellenbuendel.Befund
import de.troetenrepublik.app.engine.Quellenbuendel.Ergebnis
import de.troetenrepublik.app.engine.Quellenbuendel.Fehler
import de.troetenrepublik.app.engine.Quellenbuendel.Gesundheit
import de.troetenrepublik.app.engine.Quellenbuendel.Grund
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.EdECPrivateKeySpec
import java.security.spec.NamedParameterSpec
import kotlin.random.Random

// Fassung 5.92: Selbstreparatur der Datenquellen, Signaturpruefung, Hostliste, Version, Zusammenfuehren, Zaehler.
class Fassung592QuellenTest {

    private val sha512: (ByteArray) -> ByteArray = { MessageDigest.getInstance("SHA-512").digest(it) }
    private fun hex(s: String) = Quellenbuendel.hex(s)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    // Privater Testschluessel aus RFC 8032 (Test 1), nie der echte
    private val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
    private val pk = Ed25519.oeffentlich(seed, sha512)

    private fun signiert(text: String) = hex(Ed25519.signieren(seed, text.toByteArray(), sha512))

    // ------------------------------------------------------------ Ed25519 pruefen

    @Test fun ed25519PruefenRfc8032() {
        val pk2 = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
        val sig2 = hex("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00")
        assertTrue(Ed25519.pruefen(pk2, hex("72"), sig2, sha512))
        assertFalse(Ed25519.pruefen(pk2, hex("73"), sig2, sha512))
        val kaputt = sig2.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        assertFalse(Ed25519.pruefen(pk2, hex("72"), kaputt, sha512))
        // Falscher Schluessel
        assertFalse(Ed25519.pruefen(pk, hex("72"), sig2, sha512))
        // S + L ist mathematisch gleichwertig, muss aber abgelehnt werden
        val l = hex("edd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010")
        val s = sig2.copyOfRange(32, 64)
        var uebertrag = 0
        val sPlusL = ByteArray(32) { i -> val z = (s[i].toInt() and 0xff) + (l[i].toInt() and 0xff) + uebertrag; uebertrag = z shr 8; z.toByte() }
        assertFalse(Ed25519.pruefen(pk2, hex("72"), sig2.copyOfRange(0, 32) + sPlusL, sha512))
        // Falsche Laengen
        assertFalse(Ed25519.pruefen(pk2, hex("72"), sig2.copyOf(63), sha512))
        assertFalse(Ed25519.pruefen(pk2.copyOf(31), hex("72"), sig2, sha512))
    }

    @Test fun ed25519PruefenGegenJava() {
        val r = Random(592)
        repeat(20) {
            val geheim = r.nextBytes(32)
            val text = r.nextBytes(r.nextInt(0, 300))
            val key = KeyFactory.getInstance("Ed25519").generatePrivate(EdECPrivateKeySpec(NamedParameterSpec.ED25519, geheim))
            val sig = Signature.getInstance("Ed25519").apply { initSign(key); update(text) }.sign()
            val oeff = Ed25519.oeffentlich(geheim, sha512)
            assertTrue(Ed25519.pruefen(oeff, text, sig, sha512))
            if (text.isNotEmpty()) assertFalse(Ed25519.pruefen(oeff, text.copyOf().also { it[0] = (it[0] + 1).toByte() }, sig, sha512))
        }
    }

    // Signatur aus tools/quellen/signieren.py (gleicher Testschluessel): Python und Kotlin passen zusammen
    @Test fun signaturAusDemWerkzeug() {
        val text = "{\"version\":2,\"sources\":{\"yahoo\":{\"off\":true}}}"
        val sig = "546e96c7a25509f6b4587ee6fd6f09ab97a3c5cc2bba877ccddf420d72d5be21c09f7758ab6e56e35e482062db16bab969f3e4584edbb8983ae4a20e2af00b00"
        assertEquals("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", hex(pk))
        val e = Quellenbuendel.pruefen(text.toByteArray(), sig + "\n", pk, 1, sha512)
        assertTrue(e is Ergebnis.Angenommen)
        assertTrue((e as Ergebnis.Angenommen).buendel.quelle("yahoo").aus)
    }

    // ------------------------------------------------------------ Lesen und Zusammenfuehren

    @Test fun fehlendeSchluesselNehmenDenEingebautenWert() {
        // Kennung nur bei Yahoo (5.94); bei anderen Quellen faellt sie weg
        val c = Quellenbuendel.lesen("{\"version\":5,\"sources\":{\"sec\":{\"headers\":{\"User-Agent\":\"Mozilla/5.0 Test\",\"X-A\":\"1\"}}}}")!!
        assertNull(c.quelle("sec").kopf["User-Agent"])
        assertEquals("1", c.quelle("sec").kopf["X-A"])
        val b = Quellenbuendel.lesen("{\"version\":5,\"sources\":{\"yahoo\":{\"headers\":{\"User-Agent\":\"Mozilla/5.0 Test\"}}}}")
        assertNotNull(b)
        b!!
        assertEquals(5L, b.version)
        val y = b.quelle("yahoo")
        assertEquals("Mozilla/5.0 Test", y.kopf["User-Agent"])
        // Ersatzhosts und Felder kommen aus dem Eingebauten
        assertEquals(listOf("query2.finance.yahoo.com"), y.ersatz["query1.finance.yahoo.com"])
        assertTrue(y.felder["kennzahlen"]!!.contains("trailingPE"))
        assertEquals(Quellenbuendel.ADRESSEN, b.adressen)
        assertEquals(Quellenbuendel.EINGEBAUT.links, b.links)
        assertFalse(y.aus)
        assertFalse(b.quelle("sec").aus)
    }

    @Test fun falscheFormIstNull() {
        assertNull(Quellenbuendel.lesen("kein json"))
        assertNull(Quellenbuendel.lesen("{\"sources\":{}}"))
        assertNull(Quellenbuendel.lesen("{\"version\":\"2\"}"))
        assertNull(Quellenbuendel.lesen("{\"version\":0}"))
        assertNull(Quellenbuendel.lesen("{\"version\":2,\"sources\":{\"yahoo\":{\"off\":\"ja\"}}}"))
        assertNull(Quellenbuendel.lesen("{\"version\":2,\"sources\":{\"yahoo\":{\"paths\":{\"/v7\":\"../x\"}}}}"))
        assertNull(Quellenbuendel.lesen("{\"version\":2,\"notices\":[{\"title\":\"ohne id\"}]}"))
        // Unbekannte Quelle aus einer spaeteren Fassung stoert nicht
        assertNotNull(Quellenbuendel.lesen("{\"version\":2,\"sources\":{\"neu\":{\"off\":true}}}"))
    }

    @Test fun grenzenUndHinweise() {
        val b = Quellenbuendel.lesen(
            "{\"version\":3,\"sources\":{\"sec\":{\"timeoutMs\":999999,\"gapMs\":-5,\"pauseMs\":120000}}," +
                "\"notices\":[{\"id\":\"a\",\"level\":\"urgent\",\"title\":\"T\",\"body\":\"B\",\"url\":\"http://x\"}],\"tuning\":{\"x\":2.5}}",
        )!!
        assertEquals(120_000, b.quelle("sec").zeitlimitMs)
        assertEquals(0, b.quelle("sec").abstandMs)
        assertEquals(120_000, b.quelle("sec").pauseMs)
        assertTrue(b.hinweise.single().dringend)
        // Nur https-Links
        assertEquals("", b.hinweise.single().link)
        assertEquals(2.5, b.einstellungen["x"]!!, 0.0)
    }

    @Test fun kopfzeilenBereinigt() {
        assertNotNull(Quellenbuendel.kopfBereinigt("User-Agent", "Mozilla/5.0 (X11)"))
        assertNull(Quellenbuendel.kopfBereinigt("User-Agent", ""))
        assertNull(Quellenbuendel.kopfBereinigt("User-Agent", "x".repeat(401)))
        assertNull(Quellenbuendel.kopfBereinigt("User-Agent", "Zeile\nNeu"))
        assertNull(Quellenbuendel.kopfBereinigt("User-Agent", "Gerät"))
        assertNull(Quellenbuendel.kopfBereinigt("Cookie", "a=b"))
        assertNull(Quellenbuendel.kopfBereinigt("Authorization", "Bearer x"))
        assertNull(Quellenbuendel.kopfBereinigt("Kopf mit Leerzeichen", "x"))
        // Ungueltige Kopfzeile faellt weg, gueltige bleibt
        val b = Quellenbuendel.lesen("{\"version\":2,\"sources\":{\"yahoo\":{\"headers\":{\"Cookie\":\"a=b\",\"Accept\":\"text/html\"}}}}")!!
        assertEquals(mapOf("Accept" to "text/html"), b.quelle("yahoo").kopf)
    }

    // ------------------------------------------------------------ Hosts

    @Test fun quelleZuHost() {
        assertEquals("yahoo", Quellenbuendel.quelleZu("query2.finance.yahoo.com"))
        assertEquals("ezb", Quellenbuendel.quelleZu("data-api.ecb.europa.eu"))
        assertEquals("sec", Quellenbuendel.quelleZu("www.sec.gov"))
        assertEquals("github", Quellenbuendel.quelleZu("raw.githubusercontent.com"))
        assertNull(Quellenbuendel.quelleZu("evilyahoo.com"))
        assertNull(Quellenbuendel.quelleZu("api.kraken.com"))
        assertNull(Quellenbuendel.quelleZu("api-free.deepl.com"))
    }

    @Test fun hostliste() {
        assertTrue(Quellenbuendel.hostsErlaubt(Quellenbuendel.EINGEBAUT))
        fun mit(json: String) = Quellenbuendel.hostsErlaubt(Quellenbuendel.lesen("{\"version\":2,$json}")!!)
        assertTrue(mit("\"sources\":{\"yahoo\":{\"moves\":{\"query1.finance.yahoo.com\":\"query3.finance.yahoo.com\"}}}"))
        assertFalse(mit("\"sources\":{\"yahoo\":{\"moves\":{\"query1.finance.yahoo.com\":\"yahoo.evil.com\"}}}"))
        assertFalse(mit("\"sources\":{\"yahoo\":{\"fallbacks\":{\"query1.finance.yahoo.com\":[\"sec.gov\"]}}}"))
        assertFalse(mit("\"sources\":{\"sec\":{\"fallbacks\":{\"www.sec.gov\":[\"www.sec.gov:8080\"]}}}"))
        assertFalse(mit("\"bundleUrls\":[\"http://raw.githubusercontent.com/x/quellen.json\"]"))
        assertFalse(mit("\"bundleUrls\":[\"https://example.com/quellen.json\"]"))
        assertTrue(mit("\"bundleUrls\":[\"https://codeberg.org/x/quellen.json\"]"))
        assertFalse(mit("\"links\":{\"unbekannt\":\"https://github.com/x\"}"))
        assertFalse(mit("\"links\":{\"repo\":\"https://example.com/x\"}"))
    }

    // ------------------------------------------------------------ Pruefen mit Signatur und Version

    @Test fun annehmenNurSigniertErlaubtUndNeuer() {
        val gut = "{\"version\":2,\"sources\":{\"yahoo\":{\"off\":true}}}"
        assertTrue(Quellenbuendel.pruefen(gut.toByteArray(), signiert(gut), pk, 1, sha512) is Ergebnis.Angenommen)
        // Gleiche oder niedrigere Version
        assertEquals(Ergebnis.Abgelehnt(Grund.VERSION), Quellenbuendel.pruefen(gut.toByteArray(), signiert(gut), pk, 2, sha512))
        // Inhalt nach dem Signieren geaendert
        val anders = gut.replace("true", "false")
        assertEquals(Ergebnis.Abgelehnt(Grund.SIGNATUR), Quellenbuendel.pruefen(anders.toByteArray(), signiert(gut), pk, 1, sha512))
        // Signatur in falscher Form
        assertEquals(Ergebnis.Abgelehnt(Grund.SIGNATUR), Quellenbuendel.pruefen(gut.toByteArray(), "abc", pk, 1, sha512))
        // Richtig signiert, aber fremder Host
        val boese = "{\"version\":9,\"sources\":{\"yahoo\":{\"moves\":{\"query1.finance.yahoo.com\":\"evil.example\"}}}}"
        assertEquals(Ergebnis.Abgelehnt(Grund.HOST), Quellenbuendel.pruefen(boese.toByteArray(), signiert(boese), pk, 1, sha512))
        // Richtig signiert, aber kein Buendel
        val muell = "[1,2,3]"
        assertEquals(Ergebnis.Abgelehnt(Grund.FORM), Quellenbuendel.pruefen(muell.toByteArray(), signiert(muell), pk, 1, sha512))
        // Echter Schluessel der App passt nicht zum Testschluessel
        val echt = hex(de.troetenrepublik.app.market.Quellen.SCHLUESSEL)
        assertEquals(Ergebnis.Abgelehnt(Grund.SIGNATUR), Quellenbuendel.pruefen(gut.toByteArray(), signiert(gut), echt, 1, sha512))
    }

    // Die quellen.json im Repo ist lesbar, nennt nur erlaubte Hosts, hat eine hoehere Version als das Eingebaute (sonst
    // uebernimmt die App sie nicht) und haelt dessen Werte
    @Test fun buendelImRepo() {
        val f = listOf(File("../quellen.json"), File("quellen.json")).first { it.exists() }
        val b = Quellenbuendel.lesen(f.readText())!!
        assertTrue(Quellenbuendel.hostsErlaubt(b))
        assertTrue(b.version > Quellenbuendel.EINGEBAUT.version)
        assertEquals(Quellenbuendel.EINGEBAUT.links, b.links)
        assertEquals(Quellenbuendel.EINGEBAUT.adressen, b.adressen)
        assertEquals(Quellenbuendel.EINGEBAUT.quelle("yahoo"), b.quelle("yahoo"))
    }

    // ------------------------------------------------------------ Umschreiben und Ersatz

    @Test fun umzugPfadeUndVersuche() {
        val q = Quellenbuendel.Quelle(
            umzug = mapOf("query1.finance.yahoo.com" to "query3.finance.yahoo.com"),
            pfade = mapOf("/v7/finance/quote" to "/v8/finance/quote", "/v7" to "/v9"),
            ersatz = mapOf("query3.finance.yahoo.com" to listOf("query2.finance.yahoo.com")),
        )
        assertEquals("query3.finance.yahoo.com" to "/v8/finance/quote", Quellenbuendel.umschreiben(q, "query1.finance.yahoo.com", "/v7/finance/quote"))
        assertEquals("query2.finance.yahoo.com" to "/v9/finance/options/X", Quellenbuendel.umschreiben(q, "query2.finance.yahoo.com", "/v7/finance/options/X"))
        assertEquals(listOf("query3.finance.yahoo.com", "query2.finance.yahoo.com"), Quellenbuendel.versuche(q, "query3.finance.yahoo.com", null))
        // Frischer Umweg kommt zuerst
        assertEquals(listOf("query2.finance.yahoo.com", "query3.finance.yahoo.com"), Quellenbuendel.versuche(q, "query3.finance.yahoo.com", "query2.finance.yahoo.com"))
        // Ohne Ersatz nur der Host selbst, ein fremder Umweg zaehlt nicht
        assertEquals(listOf("www.sec.gov"), Quellenbuendel.versuche(Quellenbuendel.Quelle(), "www.sec.gov", "query2.finance.yahoo.com"))
    }

    // ------------------------------------------------------------ Gesundheit

    @Test fun zaehlerUndAmpel() {
        val t = 1_000_000_000_000L
        assertEquals(Ampel.GRAU, Quellenbuendel.ampel(null, false, t))
        var g = Quellenbuendel.erfolg(Gesundheit(), t)
        assertEquals(Ampel.GRUEN, Quellenbuendel.ampel(g, false, t))
        assertEquals(Ampel.GRAU, Quellenbuendel.ampel(g, true, t))
        g = Quellenbuendel.fehler(g, t + 1, Fehler.GESPERRT, 429)
        assertEquals(Ampel.GELB, Quellenbuendel.ampel(g, false, t + 1))
        g = Quellenbuendel.fehler(Quellenbuendel.fehler(g, t + 2, Fehler.NETZ), t + 3, Fehler.ZEIT)
        assertEquals(3, g.inFolge)
        // Drei Fehler, aber der letzte Erfolg ist frisch: noch gelb
        assertEquals(Ampel.GELB, Quellenbuendel.ampel(g, false, t + 4))
        assertEquals(Ampel.ROT, Quellenbuendel.ampel(g, false, t + 7 * 3_600_000L))
        assertEquals(1, g.erfolge)
        assertEquals(3, g.fehler)
        assertEquals(Fehler.ZEIT, g.art)
        g = Quellenbuendel.erfolg(g, t + 10, "query2.finance.yahoo.com")
        assertEquals(0, g.inFolge)
        assertEquals("query2.finance.yahoo.com", Quellenbuendel.umwegAktiv(g, t + 60_000))
        assertNull(Quellenbuendel.umwegAktiv(g, t + 31 * 60_000L))
        // Speichern und Laden
        val m = mapOf("yahoo" to g, "sec" to Quellenbuendel.fehler(Gesundheit(), t, Fehler.FORM))
        assertEquals(m, Quellenbuendel.gesundheitLesen(Quellenbuendel.gesundheitText(m)))
        assertEquals(emptyMap<String, Gesundheit>(), Quellenbuendel.gesundheitLesen("kaputt"))
    }

    @Test fun einordnenPauseStreuungBefund() {
        assertEquals(Ausgang.OK, Quellenbuendel.einordnen(200, false))
        assertEquals(Ausgang.LEER, Quellenbuendel.einordnen(200, true))
        assertEquals(Ausgang.OK, Quellenbuendel.einordnen(204, true))
        assertEquals(Ausgang.GESPERRT, Quellenbuendel.einordnen(429, false))
        assertEquals(Ausgang.GESPERRT, Quellenbuendel.einordnen(403, false))
        assertEquals(Ausgang.SERVER, Quellenbuendel.einordnen(503, false))
        // 404 fuer ein unbekanntes Kuerzel macht die Quelle nicht krank
        assertEquals(Ausgang.NEUTRAL, Quellenbuendel.einordnen(404, false))
        assertEquals(Ausgang.NEUTRAL, Quellenbuendel.einordnen(401, false))
        assertEquals(30_000L, Quellenbuendel.pause("30", 60_000))
        assertEquals(60_000L, Quellenbuendel.pause("Wed, 21 Oct 2026 07:28:00 GMT", 60_000))
        assertEquals(3_600_000L, Quellenbuendel.pause("99999", 60_000))
        assertEquals(750L, Quellenbuendel.streuung(1000, 0.0))
        assertEquals(1250L, Quellenbuendel.streuung(1000, 1.0))
        assertEquals(1000L, Quellenbuendel.streuung(1000, 0.5))
        assertEquals(Befund.OK, Quellenbuendel.befund(true, Fehler.GESPERRT))
        assertEquals(Befund.BLOCKED, Quellenbuendel.befund(false, Fehler.GESPERRT))
        assertEquals(Befund.BLOCKED, Quellenbuendel.befund(false, Fehler.NETZ))
        assertEquals(Befund.DRIFT, Quellenbuendel.befund(false, Fehler.FORM))
        assertEquals(Befund.DRIFT, Quellenbuendel.befund(false, null))
    }
}
