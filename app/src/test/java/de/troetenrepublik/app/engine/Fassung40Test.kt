package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.Derivate
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Sonderleser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// Derivate in den Meldungen und im Portfolioverlauf, dazu die Leser fuer Nachrichten ohne lesbare Seite.
// Alle Geschaefte und Texte sind erfunden, nachgebildet in der Form der echten Meldungen.
class Fassung40Test {

    private fun ms(iso: String) = LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun g(wert: String, typ: String, kuerzel: String? = "AAA", beschreibung: String = "", art: Kongress.Art = Kongress.Art.KAUF, datum: String = "2025-01-14") =
        Kongress.Geschaeft("SP", wert, kuerzel, typ, art, ms(datum), ms(datum), 250_001, 500_000, beschreibung)

    @Test
    fun optionenErkennen() {
        // Senat: Angaben im Namen
        val senat = Derivate.aus(g("Beispiel Corp Common Stock Option Type: Call Strike price: \$75.00 Expires: 2026-08-21", "OP", "BSP"))!!
        assertEquals(Derivate.Art.CALL, senat.art)
        assertEquals(75.0, senat.strike!!, 1e-9)
        assertEquals(ms("2026-08-21"), senat.verfall)
        assertTrue(senat.bewertbar)
        val senatPut = Derivate.aus(g("Beispiel ETF Option Type: Put Strike price: \$45.00 Expires: 09/20/2024", "OP", "BSP"))!!
        assertEquals(Derivate.Art.PUT, senatPut.art)
        assertEquals(ms("2024-09-20"), senatPut.verfall)
        // Repraesentantenhaus: Angaben in der Beschreibung
        val haus = Derivate.aus(g("Beispiel Inc. - Class A Common Stock", "OP", beschreibung = "Purchased 20 call options with a strike price of \$150 and an expiration date of 1/15/27."))!!
        assertEquals(Derivate.Art.CALL, haus.art)
        assertEquals(150.0, haus.strike!!, 1e-9)
        assertEquals(ms("2027-01-15"), haus.verfall)
        assertEquals(20, haus.kontrakte)
        // ohne Angaben: Option, aber nicht bewertbar
        val ohne = Derivate.aus(g("Beispiel Inc.", "OP", beschreibung = "Exercised options."))!!
        assertEquals(Derivate.Art.SONST, ohne.art)
        assertTrue(!ohne.bewertbar)
        // Papiermeldung und OGE: Angaben im Namen, Basiswert aus dem Namen
        val papier = Derivate.aus(g("CALL/JNJ FLEX EURO PM @ 202.5 EXP 02/06/2026", "OP", kuerzel = null))!!
        assertEquals("JNJ", papier.basis)
        assertEquals(202.5, papier.strike!!, 1e-9)
        assertEquals(ms("2026-02-06"), papier.verfall)
        // strukturierte Produkte und kuendbare Anleihen
        assertEquals(Derivate.Art.STRUKTUR, Derivate.aus(g("BEISPIEL BANK PLC LINKED TO BASKET OF INDICES", "OT", kuerzel = null))!!.art)
        assertNull(Derivate.aus(g("BEISPIEL CORP NTS 05.200% 092635 CALL@MW+20BP", "CS", kuerzel = null)))
        assertNull(Derivate.aus(g("Beispiel Inc. Common Stock", "ST")))
    }

    @Test
    fun blackScholes() {
        // Bekannter Wert: S = K = 100, ein Jahr, 20 % Schwankung, 5 % Zins -> Call 10,45, Put 5,57
        assertEquals(10.4506, Derivate.preis(true, 100.0, 100.0, 1.0, 0.2, 0.05), 1e-3)
        assertEquals(5.5735, Derivate.preis(false, 100.0, 100.0, 1.0, 0.2, 0.05), 1e-3)
        // am Verfall der innere Wert
        assertEquals(20.0, Derivate.preis(true, 120.0, 100.0, 0.0, 0.2), 1e-9)
        assertEquals(0.0, Derivate.preis(false, 120.0, 100.0, 0.0, 0.2), 1e-9)
        assertEquals(0.5, Derivate.normal(0.0), 1e-7)
    }

    @Test
    fun optionImVerlauf() {
        // Basiswert steigt in 40 Handelstagen von 100 auf 120; ein Call (Basispreis 100) steigt mit Hebel
        val tage = (0 until 60).map { LocalDate.parse("2025-01-01").plusDays(it.toLong()) }.filter { it.dayOfWeek.value <= 5 }
        val kerzen = tage.mapIndexed { i, d -> val c = 100.0 + 20.0 * i / (tage.size - 1); Candle(ms(d.toString()) + 14 * 3_600_000L, c, c, c, c) }
        val kauf = Kongress.Geschaeft("", "Beispiel Inc.", "AAA", "OP", Kongress.Art.KAUF, ms(tage[0].toString()), 0L, 1_001, 15_000,
            "Purchased 10 call options with a strike price of \$100 and an expiration date of 12/19/25.")
        val h = Portfolioverlauf.handelAus(kauf)!!
        assertNotNull(h.derivat)
        assertTrue(h.position != h.kuerzel)
        val mitOption = Portfolioverlauf.kongress(listOf(h), mapOf("AAA" to kerzen), ms(tage.last().toString()))!!
        val alsAktie = Portfolioverlauf.kongress(listOf(h.copy(derivat = null)), mapOf("AAA" to kerzen), ms(tage.last().toString()))!!
        assertEquals(120.0, alsAktie.reihe.last().second, 1e-6)
        assertTrue("Call steigt staerker als der Basiswert: ${mitOption.reihe.last().second}", mitOption.reihe.last().second > 130.0)
        // im Bestand zaehlt die Option beim Basiswert
        assertEquals("AAA", mitOption.bestand.single().first)
        // Unterzweige der Aktuellen Positionen: neben der Aktie jede Option mit Basispreis und Verfall fuer sich
        val aktie = Kongress.Geschaeft("", "Beispiel Inc.", "AAA", "ST", Kongress.Art.KAUF, ms(tage[0].toString()), 0L, 50_001, 100_000, "")
        val beides = Portfolioverlauf.kongress(listOf(h, Portfolioverlauf.handelAus(aktie)!!), mapOf("AAA" to kerzen), ms(tage.last().toString()))!!
        val w = beides.werte.single()
        assertEquals(2, w.teile.size)
        assertEquals(w.usd, w.teile.sumOf { it.usd }, 1e-6)
        assertEquals(w.optionUsd, w.teile.single { it.derivat != null }.usd, 1e-6)
        val call = w.teile.single { it.derivat != null }.derivat!!
        assertEquals(Derivate.Art.CALL, call.art)
        assertEquals(100.0, call.strike!!, 1e-9)
        assertEquals(ms("2025-12-19"), call.verfall)
        assertNull(call.kontrakte)
        assertEquals(120.0 / 100.0 * 75_000.5, w.teile.single { it.derivat == null }.usd, 1e-3)
        // Ausuebung: Aktienkauf mit Beschreibung ohne Leerzeichen (so liefert sie der PDF-Leser) schliesst die Option
        val aus = Kongress.Geschaeft("", "Beispiel Inc.", "AAA", "ST", Kongress.Art.KAUF, ms(tage[30].toString()), 0L, 15_001, 50_000,
            "Exercised10calloptionspurchased1/1/25atastrikepriceof\$100withanexpirationdateof12/19/25.")
        val zwei = Portfolioverlauf.handelAlle(aus)
        assertEquals(2, zwei.size)
        assertEquals(h.position, zwei[0].position)
        assertTrue(!zwei[0].kauf && zwei[0].ganz)
        val danach = Portfolioverlauf.kongress(listOf(h) + zwei, mapOf("AAA" to kerzen), ms(tage.last().toString()))!!
        assertEquals(0, danach.ohneBestand)
        assertEquals("AAA", danach.bestand.single().first)
        assertEquals(1.0, danach.bestand.single().second, 1e-9)
    }

    @Test
    fun beschreibungImMeldungstext() {
        val zeilen = listOf(
            "SP", "Beispiel Inc. - Common Stock", "(BSP) [OP]", "P", "12/30/2025 12/30/2025", "\$250,001 -", "\$500,000",
            "Fʋʎʋʐʉ Sʖʃʖʗʕ: New",
            "Dʇʕʅʔʋʒʖʋʑʐ: Purchased 20 call options with a strike price of \$150 and an expiration date of",
            "1/15/27.",
            "SP", "Beispiel Inc. - Common Stock", "(BSP) [ST]", "S (partial)", "12/24/2025 12/24/2025", "\$1,000,001 -", "\$5,000,000",
        )
        val l = Kongress.geschaefte(zeilen)
        assertEquals(2, l.size)
        assertEquals("Purchased 20 call options with a strike price of \$150 and an expiration date of 1/15/27.", l[0].beschreibung)
        assertEquals("Beispiel Inc. - Common Stock", l[1].wert)
        assertEquals("", l[1].beschreibung)
        assertEquals(ms("2027-01-15"), Derivate.aus(l[0])!!.verfall)
    }

    @Test
    fun papiermeldungenLesen() {
        val text = """{"format":1,"stand":"2026-10-17","meldungen":{
            "8220001":{"seiten":3,"unlesbar":2,"geschaefte":[
              {"i":"DC","w":"BEISPIEL RENTALS, INC. CMN","k":"BSP","t":"ST","a":"KAUF","d":"2026-02-06","g":"2026-03-02","v":15001,"b":50000},
              {"i":"","w":"CALL/BSP FLEX EURO PM @ 202.5 EXP 02/06/2026","k":"BSP","t":"OP","a":"TEILVERKAUF","d":"2026-02-06","v":1001,"b":15000},
              {"w":"ohne Datum","a":"KAUF","d":"","v":1001},
              {"w":"falsche Art","a":"X","d":"2026-02-06","v":1001}]},
            "8220002":{"seiten":2,"unlesbar":14,"grund":"handschriftlich","geschaefte":[]}}}"""
        val s = de.troetenrepublik.app.market.Papiermeldungen.lesen(text)!!
        val g = s.geschaefte("8220001")!!
        assertEquals(2, g.size)
        assertEquals(Kongress.Art.KAUF, g[0].art)
        assertEquals(ms("2026-02-06"), g[0].datum)
        assertEquals(ms("2026-03-02"), g[0].gemeldet)
        assertEquals(32_500.5, g[0].mitte, 1e-9)
        assertEquals(Kongress.Art.TEILVERKAUF, g[1].art)
        assertEquals(202.5, Derivate.aus(g[1])!!.strike!!, 1e-9)
        assertEquals(2, s.unlesbar("8220001"))
        assertTrue(s.enthaelt("8220002"))
        assertEquals(0, s.geschaefte("8220002")!!.size)
        assertNull(s.geschaefte("9999"))
        assertNull(de.troetenrepublik.app.market.Papiermeldungen.lesen("""{"format":2}"""))
    }

    @Test
    fun mitgelieferteePapiermeldungen() {
        val wurzel = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "app/build.gradle.kts").isFile }
        val s = de.troetenrepublik.app.market.Papiermeldungen.lesen(java.io.File(wurzel, "app/src/main/assets/copytrading/papier.json").readText())!!
        assertEquals(119, s.meldungen.size)
        val alle = s.meldungen.values.flatMap { it.first }
        assertTrue("Geschaefte: ${alle.size}", alle.size > 11_000)
        // alle mit gueltigem Tag; Optionen erkennt die App mit Basispreis und Verfall
        assertTrue(alle.all { it.datum > 0 })
        assertTrue(alle.count { Derivate.aus(it)?.bewertbar == true } > 100)
    }

    @Test
    fun nachrichtenOhneLesbareSeite() {
        assertEquals(
            "https://ec.europa.eu/commission/presscorner/api/documents?reference=IP/26/2186&language=en",
            Sonderleser.euAdresse("https://ec.europa.eu/commission/presscorner/detail/en/ip_26_2186"),
        )
        assertNull(Sonderleser.euAdresse("https://www.tagesschau.de/wirtschaft/"))
        val json = """{"docuLanguageResource":{"title":"T","htmlContent":"<p>Die Kommission hat heute <a href=\"x\">Preise</a> vergeben.</p><ul><li>Ant&oacute;nio &amp; Co.</li></ul><p></p>"}}"""
        assertEquals(listOf("Die Kommission hat heute Preise vergeben.", "António & Co."), Sonderleser.euText(json))
        val html = """<a href="/de/x.html">x</a><a href="https://andere.org/a.pdf">fremd</a><a href="/public/asset/rede.en.pdf">Rede</a>"""
        assertEquals("https://www.beispiel.ch/public/asset/rede.en.pdf", Sonderleser.pdfLink(html, "https://www.beispiel.ch/de/reden/1"))
        val absaetze = Sonderleser.pdfAbsaetze(listOf("Die Lage", "der Wirtschaft", "1", "Die Preise steigen weiter. " + "Wort ".repeat(60).trim() + ".", "• Erstens", "ein Punkt."))
        assertEquals("Die Lage der Wirtschaft Die Preise steigen weiter. " + "Wort ".repeat(60).trim() + ".", absaetze[0])
        assertEquals("• Erstens ein Punkt.", absaetze[1])
    }
}
