package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.RedditAuswertung.Beitrag
import de.troetenrepublik.app.engine.RedditAuswertung.Kandidat
import de.troetenrepublik.app.engine.Tendenz.Richtung
import de.troetenrepublik.app.market.Abrufkontingent
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Markterwartung
import de.troetenrepublik.app.market.NasdaqKalender.Wirtschaftstermin
import de.troetenrepublik.app.market.Prognose
import de.troetenrepublik.app.market.RedditApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Maerkte, Nachbesserung 0.73: Reddit-Zaehlung, Tendenz aus Schwellen, Gegenprobe Asien, Gattungen, Wettkauf,
// Put/Call, Boersenfeiertage, Regionen.
class Fassung073MaerkteTest {

    private val JETZT = 1_900_000_000_000L
    private val STUNDE = 3_600_000L

    private fun katalog(): List<Kandidat> {
        val d = Json { ignoreUnknownKeys = true }.decodeFromString<Catalog.Data>(File(listOf(File("src/main/assets"), File("../app/src/main/assets")).first { it.exists() }, "katalog.json").readText())
        return d.aktien.map { Kandidat(it.s, it.n) } + listOf(
            Kandidat("BTC-EUR", "Bitcoin"), Kandidat("ETH-EUR", "Ethereum"), Kandidat("SOL-EUR", "Solana"),
            Kandidat("XRP-EUR", "XRP"), Kandidat("ADA-EUR", "Cardano"), Kandidat("DOGE-EUR", "Dogecoin"),
        )
    }

    private fun beitrag(id: String, titel: String, stunden: Double, sub: String = "wallstreetbetsGER") =
        Beitrag(id, titel, "", sub, 5, 1, JETZT - (stunden * STUNDE).toLong(), "https://www.reddit.com/r/x/$id")

    // ---------------------------------------------------------------- Reddit: Namenssuche

    @Test
    fun indexnamenUndAlltagswoerterZaehlenNurMitZeichen() {
        val k = katalog()
        val sucher = k.map { RedditAuswertung.Sucher(it) }
        fun treffer(t: String) = RedditAuswertung.werte(beitrag("x", t, 1.0), sucher).map { it.symbol }.toSet()
        // Indexnamen, Plattform, Zahlwort, Farbe, Koerperteil ohne Zeichen: keine Treffer
        assertEquals(emptySet<String>(), treffer("Dow fällt 400 Punkte"))
        assertEquals(emptySet<String>(), treffer("Nasdaq im Plus, S&P / Dow Futures"))
        assertEquals(emptySet<String>(), treffer("Die erste Quartalszahl ist da"))
        assertEquals(emptySet<String>(), treffer("Alles Orange heute"))
        assertEquals(emptySet<String>(), treffer("Mit einem Arm im Gips an die Börse"))
        assertEquals(emptySet<String>(), treffer("Was hat Nvidia mit Reddit gemeinsam?").minus("NVDA"))
        assertEquals(emptySet<String>(), treffer("Visa beantragen für die Reise"))
        // mit eindeutigem Zeichen
        assertTrue("DOW" in treffer("Dow Aktie nach Zahlen"))
        assertTrue("DOW" in treffer("\$DOW zahlt Dividende"))
        assertTrue("ORA.PA" in treffer("Orange Aktie kaufen?"))
        assertTrue("LCID" in treffer("Was geht bei Lucid ab?"))
        assertTrue("NDAQ" in treffer("Nasdaq Aktie halten?"))
        // gewoehnliche Namen bleiben frei
        assertTrue("RHM.DE" in treffer("Rheinmetall ist kein Maimai"))
        assertTrue("NVDA" in treffer("nvidia, die einzelaktie"))
    }

    @Test
    fun laengererNameSchluckt() {
        val sucher = katalog().map { RedditAuswertung.Sucher(it) }
        val w = RedditAuswertung.werte(beitrag("x", "Siemens Energy einsteigen?", 1.0), sucher).map { it.symbol }
        assertEquals(listOf("ENR.DE"), w)
        // Wird Siemens zusaetzlich allein genannt, zaehlt es mit
        val zwei = RedditAuswertung.werte(beitrag("y", "Siemens Energy oder Siemens?", 1.0), sucher).map { it.symbol }.toSet()
        assertEquals(setOf("ENR.DE", "SIE.DE"), zwei)
    }

    @Test
    fun gattungenZaehlenZusammen() {
        val k = listOf(Kandidat("GOOGL", "Alphabet (A)"), Kandidat("GOOG", "Alphabet (C)"), Kandidat("BRK-B", "Berkshire Hathaway (B)"))
        val z = RedditAuswertung.zaehlen(
            listOf(beitrag("1", "Alphabet im Plus", 1.0), beitrag("2", "GOOG und GOOGL", 2.0), beitrag("3", "Berkshire Hathaway Zahlen", 3.0)),
            k, JETZT,
        )
        assertEquals(listOf("GOOGL", "BRK-B"), z.map { it.symbol })
        assertEquals(2, z[0].erwaehnungen)
        assertEquals(1, z[1].erwaehnungen)
    }

    // Zufaellige Stichprobe echter Beitragstitel (Arctic Shift, 09.11.2026, je 60 zufaellig gezogene aus 1171 deutschen und
    // 887 englischen Titeln der Subreddits Aktien, Finanzen, wallstreetbetsGER, mauerstrassenwetten und wallstreetbets,
    // stocks, investing); erwartet sind die von Hand gepruefte Nennungen.
    @Test
    fun stichprobeEchterTitel() {
        val sucher = katalog().map { RedditAuswertung.Sucher(it) }
        var fehler = ""
        for ((titel, erwartet) in STICHPROBE_DE + STICHPROBE_EN) {
            val ist = RedditAuswertung.werte(beitrag("s", titel, 1.0), sucher).map { it.symbol }.toSet()
            if (ist != erwartet) fehler += "$titel -> $ist statt $erwartet\n"
        }
        assertEquals("", fehler)
        assertTrue(STICHPROBE_DE.size >= 50 && STICHPROBE_EN.size >= 50)
    }

    // ---------------------------------------------------------------- Reddit: Zeitraum und Vorstand

    @Test
    fun vorwertAusGespeichertemStand() {
        val k = listOf(Kandidat("RHM.DE", "Rheinmetall"), Kandidat("SAP.DE", "SAP"))
        val beitraege = listOf(beitrag("1", "Rheinmetall Calls", 1.0), beitrag("2", "SAP heute", 2.0), beitrag("3", "Rheinmetall", 5.0))
        // Beitraege reichen keinen Tag zurueck: ohne Stand kein Vorwert
        assertNull(RedditAuswertung.zaehlen(beitraege, k, JETZT, null, false).first().vorher)
        // Mit dem Stand von gestern: Rheinmetall 1, SAP fehlt dort also 0
        val z = RedditAuswertung.zaehlen(beitraege, k, JETZT, mapOf("RHM.DE" to 1), false)
        assertEquals(2, z[0].erwaehnungen)
        assertEquals(1, z[0].vorher)
        assertEquals(0, z[1].vorher)
    }

    @Test
    fun vorstandSpeichernUndLesen() {
        var v = RedditVorstand.Vorstand()
        v = RedditVorstand.merken(v, JETZT - 26 * STUNDE, mapOf("A" to 3))
        v = RedditVorstand.merken(v, JETZT - 25 * STUNDE, mapOf("A" to 99))
        assertEquals(1, v.staende.size)
        v = RedditVorstand.merken(v, JETZT - 23 * STUNDE, mapOf("A" to 4))
        v = RedditVorstand.merken(v, JETZT, mapOf("A" to 7))
        assertEquals(mapOf("A" to 4), RedditVorstand.vorher(v, JETZT))
        assertNull(RedditVorstand.vorher(RedditVorstand.Vorstand(), JETZT))
        // Alte Staende fallen nach drei Tagen weg
        v = RedditVorstand.merken(v, JETZT + 80 * STUNDE, mapOf("A" to 1))
        assertEquals(1, v.staende.size)
        // Der Stand ueberlebt die Datei
        val json = Json.encodeToString(RedditVorstand.Vorstand.serializer(), v)
        assertEquals(v, Json.decodeFromString(RedditVorstand.Vorstand.serializer(), json))
    }

    private fun seite(ids: IntRange, ab: Long, nach: String?): String {
        val kinder = ids.joinToString(",") { i ->
            """{"kind":"t3","data":{"id":"p$i","title":"Beitrag $i","subreddit":"wallstreetbetsGER","score":1,"num_comments":0,"created_utc":${(ab - (i - ids.first) * 600_000L) / 1000.0},"permalink":"/r/x/$i/","over_18":false,"stickied":false}}"""
        }
        return """{"kind":"Listing","data":{"after":${if (nach == null) "null" else "\"$nach\""},"children":[$kinder]}}"""
    }

    @Test
    fun blaettertSeitenweiseBisZumZiel() = runBlocking {
        val adressen = ArrayList<String>()
        var uhr = JETZT
        // Seite 1: Beitraege von jetzt bis 100 mal 10 Minuten zurueck (16,6 Stunden), Seite 2 bis 33 Stunden
        val api = RedditApi({ url, _, _ ->
            adressen += url
            when {
                url == RedditApi.TOKEN -> MarketApi.Antwort(200, """{"access_token":"t","expires_in":3600}""")
                "after=t3_s1" in url -> MarketApi.Antwort(200, seite(100..199, JETZT - 100 * 600_000L, "t3_s2"))
                "after=t3_s2" in url -> MarketApi.Antwort(200, seite(200..299, JETZT - 200 * 600_000L, "t3_s3"))
                else -> MarketApi.Antwort(200, seite(0..99, JETZT, "t3_s1"))
            }
        }, Abrufkontingent(null) { uhr += 1_000L; uhr }, { JETZT })
        // Ziel 24 Stunden zurueck: die erste Seite reicht 16,5 Stunden, erst die zweite (bis 33 Stunden) genuegt
        val v = api.neueBis("wallstreetbetsGER", JETZT - 24 * STUNDE, "id", "", maxSeiten = 10)!!
        assertEquals(200, v.beitraege.size)
        assertTrue(v.vollstaendig)
        assertEquals(2, adressen.count { it.contains("/r/wallstreetbetsGER/new") })
        // Mit Seitengrenze 1 fehlt der Rest: nicht vollstaendig
        val begrenzt = api.neueBis("wallstreetbetsGER", JETZT - 24 * STUNDE, "id", "", maxSeiten = 1)!!
        assertFalse(begrenzt.vollstaendig)
        assertEquals(100, begrenzt.beitraege.size)
    }

    // ---------------------------------------------------------------- Tendenz aus Schwellen

    private fun markt(titel: String, preis: Double, offen: Boolean = true, gewonnen: Boolean = false) = Prognose.Markt(
        Prognose.Quelle.POLYMARKET, titel, titel, titel,
        listOf(Prognose.Seite("Yes", preis, preis, preis), Prognose.Seite("No", 1 - preis, 1 - preis, 1 - preis)),
        null, 1000.0, 0L, offen, gewinner = if (!offen && gewonnen) 0 else null, auszahlung = if (!offen) (if (gewonnen) 1.0 else 0.0) else null,
    )

    private fun ereignis(offen: List<Prognose.Markt>, erreicht: List<Prognose.Markt> = emptyList()) = Prognose.Ereignis(
        Prognose.Quelle.POLYMARKET, "x", "How high will inflation get in 2026?", "", "", "", "", 1000.0, 0.0, 0L, offen, "", erreicht = erreicht,
    )

    @Test
    fun inflationMitSchonErreichtenSchwellen() {
        // Stand 09.11.2026 bei Polymarket: 3 %, 3,5 % und 4 % sind aufgeloest mit Ja, offen bleiben 4,5 % und mehr
        val offen = listOf(
            markt("Above 4.5%", 0.105), markt("Above 5%", 0.045), markt("Above 6%", 0.025), markt("Above 8%", 0.022), markt("Above 10%", 0.016),
        )
        val erreicht = listOf("Above 3%", "Above 3.5%", "Above 4%").map { markt(it, 1.0, offen = false, gewonnen = true) }
        // Ohne die erreichten Schwellen sagte die Rechnung faelschlich „gut fuer Anleihen“
        val alt = Tendenz.erwartung("inflation", Markterwartung.ausgaenge(ereignis(offen), 50), null)
        assertEquals(Richtung.NEUTRAL, alt.richtung)
        val neu = Tendenz.erwartung("inflation", Markterwartung.ausgaenge(ereignis(offen, erreicht), 50), null)
        assertEquals(Tendenz.Lage(Richtung.BEARISH, Tendenz.ANLEIHEN), neu)
        // Die Anzeige mit drei Ausgaengen bleibt bei den offenen
        assertTrue(Markterwartung.ausgaenge(ereignis(offen, erreicht)).none { it.second == 1.0 })
    }

    @Test
    fun keineStufeIstNurBeiNiedrigerSchwelleBullish() {
        // Niedrigste Schwelle 4,5 % bei 10 %: ueber das Niveau sagt das nichts
        val hoch = Markterwartung.ausgaenge(ereignis(listOf(markt("Above 4.5%", 0.1), markt("Above 5%", 0.04), markt("Above 6%", 0.02))), 50)
        assertEquals(Richtung.NEUTRAL, Tendenz.erwartung("inflation", hoch, null).richtung)
        assertEquals(Richtung.BULLISH, Tendenz.erwartung("inflation", listOf("≥3%" to 0.3, "≥4%" to 0.1), null).richtung)
    }

    @Test
    fun aufgeloesteMaerkteBleibenImEreignis() {
        val text = """{"slug":"inflation","title":"How high will inflation get in 2026?","endDate":"2027-02-01T04:59:00Z","markets":[
            {"id":"1","groupItemTitle":"Above 4.5%","question":"q","outcomes":"[\"Yes\",\"No\"]","outcomePrices":"[\"0.105\",\"0.895\"]","closed":false,"active":true,"endDate":"2027-02-01T04:59:00Z"},
            {"id":"2","groupItemTitle":"Above 4%","question":"q","outcomes":"[\"Yes\",\"No\"]","outcomePrices":"[\"1\",\"0\"]","closed":true,"active":true,"umaResolutionStatus":"resolved","endDate":"2027-02-01T04:59:00Z"}]}"""
        val e = Prognose.polyEreignisLesen(Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject)!!
        assertEquals(listOf("Above 4.5%"), e.maerkte.map { it.titel })
        assertEquals(listOf("Above 4%"), e.erreicht.map { it.titel })
    }

    // ---------------------------------------------------------------- Gegenprobe Asien

    @Test
    fun handelstagVonJapanUndChina() {
        val montag = LocalDate.of(2026, 11, 9).toEpochDay()
        val dienstag = montag + 1
        // 18:30 New Yorker Zeit am Montag sind 8:30 Uhr am Dienstag in Tokio
        val jp = Wirtschaftstermin(montag, 18 * 60 + 30, "Japan", "Retail Sales", "1.8%", "1.0%", "0.5%")
        assertEquals(dienstag, Wirkungskette.aussage(montag, Wirkungskette.Region.JP, listOf(jp))!!.handelstag)
        // 22:00 in New York sind 11:00 Uhr am Dienstag in Shanghai
        val cn = Wirtschaftstermin(montag, 22 * 60, "China", "Retail Sales", "5.0%", "3.0%", "3.0%")
        assertEquals(dienstag, Wirkungskette.aussage(montag, Wirkungskette.Region.CN, listOf(cn))!!.handelstag)
        // Vereinigte Staaten bleiben beim New Yorker Tag
        val us = Wirtschaftstermin(montag, 8 * 60 + 30, "United States", "Retail Sales", "1.8%", "1.0%", "0.5%")
        assertEquals(montag, Wirkungskette.aussage(montag, Wirkungskette.Region.US, listOf(us))!!.handelstag)
        // Sonntagabend in New York ist Montagmorgen in Tokio; nach Handelsschluss geht es auf den naechsten Tag
        val sonntag = montag - 1
        val frueh = Wirtschaftstermin(sonntag, 19 * 60, "Japan", "Retail Sales", "1.8%", "1.0%", "0.5%")
        assertEquals(montag, Wirkungskette.aussage(sonntag, Wirkungskette.Region.JP, listOf(frueh))!!.handelstag)
        val spaet = Wirtschaftstermin(montag - 3, 3 * 60, "Japan", "Retail Sales", "1.8%", "1.0%", "0.5%")
        // Freitag 3:00 in New York = 17:00 in Tokio, nach Schluss und dann Wochenende: Montag
        assertEquals(montag, Wirkungskette.aussage(montag - 3, Wirkungskette.Region.JP, listOf(spaet))!!.handelstag)
    }

    @Test
    fun reaktionNimmtDenHandelstagDerRegion() {
        val montag = LocalDate.of(2026, 11, 9).toEpochDay()
        fun kerze(tag: Long, c: Double) = Candle(tag * 86_400_000L, c, c, c, c)
        // Nikkei: Schluss Freitag 100, Montag 101, Dienstag 103,02
        val kurse = mapOf("^N225" to listOf(kerze(montag - 3, 100.0), kerze(montag, 101.0), kerze(montag + 1, 103.02)))
        val jp = Wirtschaftstermin(montag, 18 * 60 + 30, "Japan", "Retail Sales", "1.8%", "1.0%", "0.5%")
        val a = Wirkungskette.aussage(montag, Wirkungskette.Region.JP, listOf(jp))!!
        val index = Wirkungskette.reaktion(a, kurse).zeilen.first { it.art == Wirkungskette.ZeilenArt.INDEX }
        assertEquals(2.0, index.aenderung, 1e-6)
    }

    // ---------------------------------------------------------------- Gattungen, Wette, Put/Call, Regionen

    @Test
    fun einKuerzelJeFirma() {
        val namen = mapOf("GOOGL" to "Alphabet (A)", "GOOG" to "Alphabet (C)", "FOXA" to "Fox (A)", "FOX" to "Fox (B)", "NWSA" to "News Corp (A)", "NWS" to "News Corp (B)", "AAPL" to "Apple", "BRK-B" to "Berkshire Hathaway (B)")
        val land = namen.keys.associateWith { "US" as String? }
        val r = Blasen.jeFirma(namen.keys.toList(), namen, land)
        assertEquals(5, r.size)
        assertTrue(!(("GOOG" in r) && ("GOOGL" in r)) && !(("FOX" in r) && ("FOXA" in r)) && !(("NWS" in r) && ("NWSA" in r)))
        // Im ganzen Katalog zaehlen nur Zweitgattungen mit, sonst bleibt alles
        val d = Json { ignoreUnknownKeys = true }.decodeFromString<Catalog.Data>(File(listOf(File("src/main/assets"), File("../app/src/main/assets")).first { it.exists() }, "katalog.json").readText())
        val sp = d.aktien.filter { "SP500" in it.x }
        val je = Blasen.jeFirma(sp.map { it.s }, sp.associate { it.s to it.n }, sp.associate { it.s to it.c })
        assertEquals(sp.size - 3, je.size)
    }

    @Test
    fun kaufNurZuFrischemKursEinesOffenenMarkts() {
        val offen = markt("m", 0.5)
        assertEquals(0.5, Wetten.kaufKurs(offen, 0).first!!, 1e-9)
        assertEquals(Wetten.Fehler.KAUF_NETZ, Wetten.kaufKurs(null, 0).second)
        // Aufgeloest (Ja 0,92 in der Liste, frisch 1,00): kein Kauf
        val zu = markt("m", 1.0, offen = false, gewonnen = true)
        assertEquals(Wetten.Fehler.MARKT_ZU, Wetten.kaufKurs(zu, 0).second)
        assertNull(Wetten.kaufKurs(zu, 0).first)
        assertEquals(Wetten.Fehler.KURS, Wetten.kaufKurs(offen, 5).second)
    }

    @Test
    fun putCallPasstZurEinordnung() {
        fun b(r: Double) = Stimmungsbarometer.putCallBarometer(WeitereQuellen.PutCall(1, null, r, null))!!
        assertEquals(75.0, b(0.5).wert, 1e-9)
        assertEquals(50.0, b(0.65).wert, 1e-9)
        assertEquals(25.0, b(0.8).wert, 1e-9)
        // Das uebliche Band 0,5 bis 0,8 gibt nie extreme Gier oder Angst; darunter und darueber schon
        for (r in listOf(0.5, 0.53, 0.6, 0.7, 0.79)) {
            assertEquals(1, WeitereQuellen.putCallLage(r))
            assertTrue(b(r).stufe != Stimmungsbarometer.Stufe.EXTREME_GIER && b(r).stufe != Stimmungsbarometer.Stufe.EXTREME_ANGST)
        }
        assertEquals(Stimmungsbarometer.Stufe.EXTREME_GIER, b(0.42).stufe)
        assertEquals(Stimmungsbarometer.Stufe.EXTREME_ANGST, b(0.95).stufe)
        assertEquals(0.0, b(1.2).wert, 1e-9)
    }

    @Test
    fun boersenfeiertage() {
        val ny = ZoneId.of("America/New_York")
        fun offen(land: String, datum: LocalDate, h: Int, zone: String) =
            Boersenzeiten.offen(land, datum.atTime(h, 0).atZone(ZoneId.of(zone)).toInstant())!!
        // USA: Thanksgiving 2026, Karfreitag, Ersatztag fuer den 4. Juli, Weihnachten; Columbus Day ist Boersentag
        assertFalse(offen("US", LocalDate.of(2026, 11, 26), 11, "America/New_York"))
        assertTrue(offen("US", LocalDate.of(2026, 11, 25), 11, "America/New_York"))
        assertTrue(Boersenzeiten.feiertag("US", LocalDate.of(2026, 4, 3)))
        assertTrue(Boersenzeiten.feiertag("US", LocalDate.of(2026, 7, 3)))
        assertTrue(Boersenzeiten.feiertag("US", LocalDate.of(2026, 12, 25)))
        assertTrue(Boersenzeiten.feiertag("US", LocalDate.of(2026, 1, 1)))
        assertTrue(Boersenzeiten.feiertag("US", LocalDate.of(2026, 6, 19)))
        assertFalse(Boersenzeiten.feiertag("US", LocalDate.of(2026, 10, 12)))
        // Neujahr am Samstag: der Freitag davor bleibt offen
        assertFalse(Boersenzeiten.feiertag("US", LocalDate.of(2021, 12, 31)))
        // Offen heisst naechster Beginn: Thanksgiving mittags, naechster Beginn Freitag 9:30
        val s = Boersenzeiten.status("US", LocalDate.of(2026, 11, 26).atTime(12, 0).atZone(ny).toInstant())!!
        assertFalse(s.offen)
        assertEquals(LocalDate.of(2026, 11, 27), s.wechsel.toLocalDate())
        // Deutschland
        assertEquals(LocalDate.of(2026, 4, 5), Boersenzeiten.ostern(2026))
        assertTrue(Boersenzeiten.feiertag("DE", LocalDate.of(2026, 4, 3)))
        assertTrue(Boersenzeiten.feiertag("DE", LocalDate.of(2026, 4, 6)))
        assertTrue(Boersenzeiten.feiertag("DE", LocalDate.of(2026, 12, 24)))
        assertTrue(Boersenzeiten.feiertag("DE", LocalDate.of(2026, 12, 31)))
        assertFalse(Boersenzeiten.feiertag("DE", LocalDate.of(2026, 10, 3)))
        assertFalse(Boersenzeiten.feiertag("DE", LocalDate.of(2026, 5, 14)))
        // Grossbritannien: Boxing Day 2026 faellt auf Samstag, Ersatztag Montag 28.12.; Summer Bank Holiday 31.8.
        assertTrue(Boersenzeiten.feiertag("GB", LocalDate.of(2026, 12, 28)))
        assertFalse(Boersenzeiten.feiertag("GB", LocalDate.of(2026, 12, 29)))
        assertTrue(Boersenzeiten.feiertag("GB", LocalDate.of(2026, 8, 31)))
        assertTrue(Boersenzeiten.feiertag("GB", LocalDate.of(2026, 5, 25)))
        // Japan: Fruehlingsanfang 2026 am 20.3., Ersatztag 6.5., Tag zwischen Feiertagen 22.9., Boersenschluss 31.12.
        assertTrue(Boersenzeiten.feiertag("JP", LocalDate.of(2026, 3, 20)))
        assertTrue(Boersenzeiten.feiertag("JP", LocalDate.of(2026, 5, 6)))
        assertTrue(Boersenzeiten.feiertag("JP", LocalDate.of(2026, 9, 22)))
        assertTrue(Boersenzeiten.feiertag("JP", LocalDate.of(2026, 12, 31)))
        assertTrue(Boersenzeiten.feiertag("JP", LocalDate.of(2026, 1, 2)))
        assertFalse(Boersenzeiten.feiertag("JP", LocalDate.of(2026, 5, 7)))
        assertTrue(Boersenzeiten.feiertag("JP", LocalDate.of(2026, 11, 23)))
        assertTrue(offen("JP", LocalDate.of(2026, 11, 24), 10, "Asia/Tokyo"))
        assertFalse(offen("JP", LocalDate.of(2026, 11, 23), 10, "Asia/Tokyo"))
        // Euronext
        assertTrue(Boersenzeiten.feiertag("FR", LocalDate.of(2026, 5, 1)))
        assertFalse(Boersenzeiten.feiertag("FR", LocalDate.of(2026, 12, 24)))
        // Ohne Regeln (China): kein Feiertag
        assertFalse(Boersenzeiten.feiertag("CN", LocalDate.of(2026, 2, 17)))
    }

    @Test
    fun regionenOhneEuropaAlsAuffangbecken() {
        assertEquals("SCHWELLENLAENDER", de.troetenrepublik.app.market.Region.ofCountry("UY"))
        assertEquals("SCHWELLENLAENDER", de.troetenrepublik.app.market.Region.ofCountry("IL"))
        assertEquals("EUROPA", de.troetenrepublik.app.market.Region.ofCountry("AT"))
        assertEquals("EUROPA", de.troetenrepublik.app.market.Region.ofCountry("GG"))
        assertEquals("GLOBAL", de.troetenrepublik.app.market.Region.ofCountry("ZZ"))
        // Jedes Land des Katalogs landet in einer benannten Region ausser GLOBAL
        val d = Json { ignoreUnknownKeys = true }.decodeFromString<Catalog.Data>(File(listOf(File("src/main/assets"), File("../app/src/main/assets")).first { it.exists() }, "katalog.json").readText())
        assertTrue(d.aktien.filter { it.c.isNotBlank() }.none { de.troetenrepublik.app.market.Region.ofCountry(it.c) == "GLOBAL" })
    }

    @Test
    fun marktphaseOhneBaerenmarktMisstVomTiefDerReihe() {
        // Reihe steigt von 100 auf 200, ohne je 20 % unter ein Hoch zu fallen
        val kurse = (0 until 100).map { it * 86_400_000L to 100.0 + it }
        val p = Marktphase.phase("x", kurse)!!
        assertEquals(Marktphase.Art.BULLE, p.art)
        assertEquals((199.0 / 100.0 - 1) * 100, p.vomTief, 1e-9)
        // Reihe beginnt hoch, faellt auf 90 (kein Baer) und steigt auf 120: vom Tief 90, nicht vom Anfang 100
        val w = (0 until 30).map { it * 86_400_000L to 100.0 - it / 3.0 } + (30 until 100).map { it * 86_400_000L to 90.0 + (it - 30) * 30.0 / 70 }
        val q = Marktphase.phase("x", w)!!
        assertEquals(((90.0 + 69 * 30.0 / 70) / 90.0 - 1) * 100, q.vomTief, 1e-6)
    }

    // ---------------------------------------------------------------- Verlaufsdiagramme

    @Test
    fun linieErstAbZehnPunkten() {
        assertEquals(Verlaufsdarstellung.Art.KEINE, Verlaufsdarstellung.art(0))
        assertEquals(Verlaufsdarstellung.Art.BALKEN, Verlaufsdarstellung.art(2))
        assertEquals(Verlaufsdarstellung.Art.BALKEN, Verlaufsdarstellung.art(9))
        assertEquals(Verlaufsdarstellung.Art.LINIE, Verlaufsdarstellung.art(10))
    }

    @Test
    fun aufloesungJeZeitraum() {
        assertEquals(de.troetenrepublik.app.data.Range.D1, Verlaufsdarstellung.rangeFuer(1))
        assertEquals(de.troetenrepublik.app.data.Range.W1, Verlaufsdarstellung.rangeFuer(7))
        assertEquals(de.troetenrepublik.app.data.Range.M1, Verlaufsdarstellung.rangeFuer(30))
        assertEquals(de.troetenrepublik.app.data.Range.M3, Verlaufsdarstellung.rangeFuer(91))
        assertEquals(de.troetenrepublik.app.data.Range.M6, Verlaufsdarstellung.rangeFuer(182))
        assertEquals(de.troetenrepublik.app.data.Range.Y1, Verlaufsdarstellung.rangeFuer(365))
        assertEquals(24, Verlaufsdarstellung.korbAnzahl(1))
        assertEquals(28, Verlaufsdarstellung.korbAnzahl(7))
        assertEquals(30, Verlaufsdarstellung.korbAnzahl(30))
        // Jeder gewaehlte Zeitraum der Trends hat mindestens zehn Punkte oder Koerbe
        for (tage in listOf(1, 7, 30, 91, 182, 365)) assertTrue(tage.toString(), Verlaufsdarstellung.korbAnzahl(tage) >= 24 || tage > 30)
    }

    @Test
    fun beitraegeInKoerben() {
        val zeiten = listOf(JETZT - 30 * 60_000L, JETZT - 90 * 60_000L, JETZT - 100 * 60_000L, JETZT - 25 * STUNDE, JETZT + 1)
        val k = Verlaufsdarstellung.koerbe(zeiten, JETZT, 24, STUNDE)
        assertEquals(24, k.size)
        assertEquals(1, k[23])
        assertEquals(2, k[22])
        assertEquals(3, k.sum())
    }

    @Test
    fun kursAufVortagesschlussBezogen() {
        // Tagesschluesse 100 und 110, aktueller Kurs 220 in anderer Waehrung: Vortagesschluss entsprechend 200
        val taeglich = listOf(Candle(0, 100.0, 100.0, 100.0, 100.0), Candle(1, 110.0, 110.0, 110.0, 110.0))
        assertEquals(200.0, Verlaufsdarstellung.vortagesschluss(taeglich, 220.0)!!, 1e-9)
        assertNull(Verlaufsdarstellung.vortagesschluss(taeglich.take(1), 220.0))
        val k = listOf(1000L to 200.0, 2000L to 210.0, 3000L to 220.0).map { Candle(it.first, it.second, it.second, it.second, it.second) }
        val v = Verlaufsdarstellung.verlauf(k, 0L, 200.0)
        assertEquals(listOf(1000.0, 1050.0, 1100.0), v.map { it.second })
        // Ohne Bezug gilt der erste Kurs des Zeitraums, Kerzen davor zaehlen nicht
        assertEquals(1000.0, Verlaufsdarstellung.verlauf(k, 1500L, null).first().second, 1e-9)
    }

    @Test
    fun beginnDerSitzung() {
        fun c(t: Long) = Candle(t, 1.0, 1.0, 1.0, 1.0)
        // Gestern 14 bis 21 Uhr, heute ab 14 Uhr (Luecke ueber sechs Stunden): beim Tag zaehlt nur heute
        val gestern = (14..21).map { c(it * STUNDE) }
        val heute = (38..41).map { c(it * STUNDE) }
        assertEquals(38 * STUNDE, Verlaufsdarstellung.beginn(gestern + heute, 1))
        assertEquals(41 * STUNDE - 7 * 24 * STUNDE, Verlaufsdarstellung.beginn(gestern + heute, 7))
    }

    @Test
    fun gemeinsameZeitachseKursUndErwaehnungen() {
        val start = 1_000_000L
        // Sieben Tagesbalken ab start: Zeitpunkt in den Korb, ausserhalb null
        val tag = 86_400_000L
        assertEquals(0, Verlaufsdarstellung.korbVon(start, start, tag, 7))
        assertEquals(3, Verlaufsdarstellung.korbVon(start + 3 * tag + 5, start, tag, 7))
        assertEquals(6, Verlaufsdarstellung.korbVon(start + 7 * tag - 1, start, tag, 7))
        assertNull(Verlaufsdarstellung.korbVon(start + 7 * tag, start, tag, 7))
        assertNull(Verlaufsdarstellung.korbVon(start - 1, start, tag, 7))
        // Naechster Kurspunkt zu einer Stelle der Achse
        assertEquals(1, Verlaufsdarstellung.naechster(listOf(0L, 100L, 300L), 160L))
        assertEquals(2, Verlaufsdarstellung.naechster(listOf(0L, 100L, 300L), 250L))
        assertNull(Verlaufsdarstellung.naechster(emptyList(), 5L))
    }

    // ---------------------------------------------------------------- Stichprobe

    private val STICHPROBE_DE: List<Pair<String, Set<String>>> = listOf(
        "LYFT INC. AKTIE" to setOf(),
        "Haus als Ferienhaus oder Vermietung" to setOf(),
        "Öl" to setOf(),
        "Schauen wir mal was wird…" to setOf(),
        "Welche Titel nachkaufen?" to setOf(),
        "Lohnt sich Payback noch?" to setOf(),
        "Täglich im Täglichen" to setOf(),
        "Yolo RocketLab" to setOf(),
        "Eindeutig nicht nur wegen der Bronchitis hart geschwitzt" to setOf(),
        "Hebel gibt" to setOf(),
        "22 Uhr sk Ausverkauf ????" to setOf(),
        "bitcoin raus?" to setOf("BTC-EUR"),
        "Richtiger Sparplan" to setOf(),
        "Welchen Broker benutzt ihr zum Hebel traden" to setOf(),
        "Welche von diesen Unternehmen würdet ihr momentan mit langfristigem Zeithorizont nachkaufen?" to setOf(),
        "25000 € Einmalbetrag: Tagesgeld/Festgeld, ETF oder doch was anderes?" to setOf(),
        "Abgeltungssteuer vermeiden ?" to setOf(),
        "Aufgewacht" to setOf(),
        "S&P ca. 6% unter Allzeithoch: Nach ATH lag der Median-Rücksetzer im Folgejahr historisch bei 5,4%" to setOf(),
        "TSMC Long?" to setOf(),
        "Koalition will Tabaksteuer stärker anheben als bisher geplant" to setOf(),
        "Giro Wechsel ING zur VB Berlin?" to setOf("INGA.AS"),
        "HIMS & HERS heute Abend" to setOf("HIMS"),
        "Busticket verschlechtert den Schufa-Score" to setOf(),
        "18M first hysa" to setOf(),
        "Ist 12k Abfindung ok?" to setOf(),
        "Was passiert mit der Weltwirtschaft, wenn die KI-Blase platzt?" to setOf(),
        "Pump Trump rede weiter" to setOf(),
        "Altersvorsorge für meinen Sohn" to setOf(),
        "Finanzen Berufsanfänger. Röstet mich" to setOf(),
        "Vermögenswirksame Leistungen" to setOf(),
        "Anthropic will pay SpaceX AI \$3.1 BILLION per MONTH for compute starting December." to setOf("SPCX"),
        "Bei uns allen heute so" to setOf(),
        "Gesetzliche Kassen erleiden Millionen-Verluste mit Fondsanlagen" to setOf(),
        "Meine Autokosten der letzen 24 Monate" to setOf(),
        "Alles nach Plan" to setOf(),
        "Welcher Online Broker passt zu mir" to setOf(),
        "Festgeld bzw. Termingeld vorzeitig auszahlen lassen" to setOf(),
        "Cloudflare Einstieg" to setOf("NET"),
        "95k Vermögen mit 32" to setOf(),
        "Commerzbank App/Website" to setOf("CBK.DE"),
        "NVIDIA gibt, Hebel gibt" to setOf("NVDA"),
        "Rocket Lab!" to setOf("RKLB"),
        "Bester Infrastruktur aktie auf Trade Republic?" to setOf(),
        "Earnings RLB / T2" to setOf(),
        "NEUE PODCASTFOLGE 🚀 October 29, 2026" to setOf(),
        "die bären kommen!" to setOf(),
        "Apple mit +100k (+1000%)" to setOf("AAPL"),
        "Angeblich hat Huan bereits vor einem Jahr eine Firma beauftragt das Publicity Desaster abzufedern." to setOf(),
        "Carnival die fetten Reisen doch trotzdem weiter ?!" to setOf("CCL"),
        "Tech ist in den Bullenmarkt eingetreten" to setOf(),
        "Siemens Energy" to setOf("ENR.DE"),
        "Wenn der Affe recht hat" to setOf(),
        "Warum steigt der Hsmetall jetzt?" to setOf(),
        "Nvida Gamble" to setOf(),
        "MoneY" to setOf(),
        "Irgendwie dachte ich alles soll fallen ?!?!?" to setOf(),
        "Iren gibt mir gemischte Signale" to setOf(),
        "Umstieg auf Europäische ETFS?" to setOf(),
        "Hat wer Lust auf ein Altersvorosrgedepot bei der KI Bank des Vertrauens?" to setOf(),
    )

    private val STICHPROBE_EN: List<Pair<String, Set<String>>> = listOf(
        "Roth IRA Question selling VMFXX" to setOf(),
        "Roast me." to setOf(),
        "What is a REIT (Real Estate Investment Trust)?" to setOf(),
        "THE HUANG BANG (2026, colorized)" to setOf(),
        "What do you think of SCHD + VUG + VXUS combo?" to setOf(),
        "Cybersecurity sector skyrocketed amid AI worries and regulation" to setOf(),
        "Where to put money during a crash?" to setOf(),
        "SEC Approves Removal of Pattern Day Trader Rule, Easing Entry for Retail Traders" to setOf(),
        "Free share worth up to 100€" to setOf(),
        "32 in New York - Net Worth" to setOf(),
        "LUCID GROUP INC" to setOf(),
        "Semicon, AI & Photonics Portfolio Advice" to setOf(),
        "Roast me" to setOf(),
        "CoStar Group (CSGP) at \$28.77. The market is valuing the commercial real estate business and giving you the rest for almost nothing." to setOf("CSGP"),
        "Lets slow down AI" to setOf(),
        "Launch Vector - Failing Business?" to setOf(),
        "What Are Your Moves Tomorrow, April 15, 2026" to setOf(),
        "Semi’s and Technology taking over the S and P…" to setOf(),
        "Why can't I post my deep dive on Amazon." to setOf("AMZN"),
        "Why is CCJ trending down?" to setOf("CCJ"),
        "Has anyone tried rivo? Auto invest your checking account but can't find any reviews" to setOf(),
        "Stocks/Investing：What is the oldest holding in your portfolio?" to setOf(),
        "This Is Fine: Paper Markets vs. Physical Reality" to setOf(),
        "Arc’s mainnet launch on Wed gonna be the catalyst for Circle" to setOf(),
        "What Are Your Moves Tomorrow, September 15, 2026" to setOf(),
        "I'm the first of my family to be white collar, however during my time working I taught myself to trade." to setOf(),
        "What are your favorite websites or tools for researching stocks, and why?" to setOf(),
        "E-Trade vs Schwab - Which Would you choose?" to setOf(),
        "I want to see hedge funds fight in a hunger game kind of scenario" to setOf(),
        "If AI wipes out humanity Resident Evil style then who is today's Umbrella Corporation? Because I like to invest in them." to setOf(),
        "Is there investing potential in a casino becoming a data center in a small town?" to setOf(),
        "AI Rejection and Share Prices" to setOf(),
        "Advice for my portfolio" to setOf(),
        "Looking for ETFs with Growth Potential" to setOf(),
        "요즘 시장 분위기, 다들 어떻게 느끼시나요?" to setOf(),
        "21, recently married. Any advice for a new-ish investor like myself?" to setOf(),
        "27 y/o - Starting my investing journey to generate wealth for the long run - advice please" to setOf(),
        "LCID Weirdness around a speculative bankruptcy report." to setOf("LCID"),
        "投资TALK君Who would believe that AI training needs to slow down?" to setOf(),
        "Has anyone here actually recovered value from a suspended Russian ADR?" to setOf(),
        "Circular Financing Cycle Bust Overdue?" to setOf(),
        "Something Wong with Nvda today" to setOf(),
        "Context has never worked so hard to save man’s career 😭" to setOf(),
        "IBM's 25% one-day crash: the mechanism (customers front-running memory prices out of a fixed IT budget) matters more than the headline miss)" to setOf("IBM"),
        "The Argument for VGT in a Boglehead Portfolio" to setOf(),
        "Berkshire B shares- explain volatility" to setOf(),
        "Tuesday Tape: yesterday looked less like a breakout call and more like a participation check" to setOf(),
        "Simple investment idea for someone with guaranteed high income for life" to setOf(),
        "Help me out, thank you so much🥰" to setOf(),
        "Trump warned CEO of major AI company that he will go to jail (source: Unaired 60 Minutes interview)" to setOf(),
        "NFLX & Chill 😎" to setOf("NFLX"),
        "AZCU" to setOf(),
        "Buying a PS5 now and sell it when GTA 6 comes out - investment oppurtunity?" to setOf(),
        "What to do with SnDK" to setOf(),
        "Tomorrow, Wednesday, July 15th, whether the market opens higher or lower, these three AI-infrastructure stocks deserve your attention." to setOf(),
        "10Y hit 5% today. Then reports surfaced about a peace deal. 10Y dumped, markets pumped. How long can yields continue to be contained?" to setOf(),
        "Stock / option valuation tools" to setOf(),
        "Which 10 stocks would you pick?" to setOf(),
        "Should I sell United Airlines (UAL)" to setOf("UAL"),
        "Fast Growers portfolio: rddt, figr, onon, app" to setOf(),
    )
}
