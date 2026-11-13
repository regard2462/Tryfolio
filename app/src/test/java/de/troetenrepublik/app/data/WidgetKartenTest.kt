package de.troetenrepublik.app.data

import de.troetenrepublik.app.data.WidgetKartenBau.Auftrag
import de.troetenrepublik.app.data.WidgetKartenBau.Bewegung
import de.troetenrepublik.app.data.WidgetKartenBau.Durchbruch
import de.troetenrepublik.app.data.WidgetKartenBau.Fund
import de.troetenrepublik.app.data.WidgetKartenBau.Handel
import de.troetenrepublik.app.data.WidgetKartenBau.Plan
import de.troetenrepublik.app.data.WidgetKartenBau.Platz
import de.troetenrepublik.app.data.WidgetKartenBau.Trendwert
import de.troetenrepublik.app.data.WidgetKartenBau.Wette
import de.troetenrepublik.app.data.WidgetKartenBau.WissenLektion
import de.troetenrepublik.app.data.WidgetKartenBau.WissenModul
import de.troetenrepublik.app.i18n.I18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Kategorie-Widgets (0.73): was in welcher Groesse in einer Karte steht, und der Aufbau der Karten.
class WidgetKartenTest {

    @Before
    fun deutsch() { I18n.lang = Lang.DE }

    private fun zeilen(n: Int, unter: Boolean = false) =
        List(n) { WidgetZeile("Wert $it", "+${it + 1},0 %", true, unter = if (unter) "Zeile darunter" else "") }

    // ------------------------------------------------------------ Zuschnitt

    @Test
    fun grossesWidgetZeigtAlleZeilenBisAcht() {
        val k = WidgetKarte("Titel", zeilen(8), stand = "Stand 12:00")
        val z = WidgetKarten.zuschneiden(k, 320, 300)
        assertEquals(8, z.zeilen.size)
        assertTrue(z.titel)
        assertTrue(z.stand)
        // Die wichtigsten Zeilen stehen vorn
        assertEquals(k.zeilen.take(8), z.zeilen)
    }

    @Test
    fun kleinesWidgetZeigtWenigerZeilenAlsPlatzNichtReicht() {
        val k = WidgetKarte("Titel", zeilen(6), stand = "Stand 12:00")
        val klein = WidgetKarten.zuschneiden(k, 146, 146)
        val breit = WidgetKarten.zuschneiden(k, 300, 146)
        // 2 x 2: Titel, Stand und zwei bis drei Zeilen; breiter aendert an der Hoehe nichts
        assertTrue(klein.zeilen.size in 2..3)
        assertEquals(klein.zeilen.size, breit.zeilen.size)
        val hoch = WidgetKarten.zuschneiden(k, 146, 260)
        assertTrue(hoch.zeilen.size > klein.zeilen.size)
    }

    @Test
    fun zeilenPassenImmerInDieHoehe() {
        // Gleiche Rechnung wie der Zuschnitt: Rand, Titel, Stand und die Zeilen duerfen die Hoehe nie ueberschreiten
        for (h in listOf(96, 110, 146, 180, 250, 320)) for (b in listOf(110, 146, 220, 300)) {
            val k = WidgetKarte("Titel", zeilen(6, unter = true), stand = "Stand 12:00")
            val z = WidgetKarten.zuschneiden(k, b, h)
            assertTrue("b=$b h=$h", z.zeilen.size <= 6)
            if (h < 110) assertTrue("b=$b h=$h", z.zeilen.size <= 2)
        }
    }

    @Test
    fun kleineZeilenDarunterFallenWegWennSieZeilenKosten() {
        val k = WidgetKarte("Titel", zeilen(4, unter = true), stand = "Stand 12:00")
        // Hoch genug fuer alle vier mit der kleinen Zeile
        val gross = WidgetKarten.zuschneiden(k, 300, 300)
        assertTrue(gross.mitUnter)
        assertEquals(4, gross.zeilen.size)
        // Kleines Widget: lieber mehr Zeilen ohne die kleine Zeile als eine einzige mit
        val klein = WidgetKarten.zuschneiden(k, 146, 130)
        assertTrue(klein.zeilen.size >= 2)
        if (klein.zeilen.size >= 2 && klein.mitUnter) assertTrue(klein.zeilen.size >= 2)
    }

    @Test
    fun grosseSchriftSkalaZeigtWenigerZeilen() {
        val k = WidgetKarte("Titel", zeilen(6), stand = "Stand 12:00")
        val normal = WidgetKarten.zuschneiden(k, 220, 200, 1f)
        val gross = WidgetKarten.zuschneiden(k, 220, 200, 1.5f)
        assertTrue(gross.zeilen.size < normal.zeilen.size)
    }

    @Test
    fun langerTextBrichtUmUndZuLangerFaelltWeg() {
        val kurz = WidgetZeile("Apple", "+1,0 %", true)
        val lang = WidgetZeile("Ein sehr langer Name eines Unternehmens mit vielen Woertern darin", "+1,0 %", true)
        val unmoeglich = WidgetZeile("Wortwortwortwortwortwortwortwortwortwortwortwortwortwortwortwortwort " + "x".repeat(300), "+1,0 %", true)
        val k = WidgetKarte("T", listOf(unmoeglich, lang, kurz))
        // Breit genug bricht der lange Name um und bleibt; die unmoegliche Zeile fehlt, statt abgeschnitten zu werden
        val breit = WidgetKarten.zuschneiden(k, 300, 250)
        assertEquals(listOf(lang, kurz), breit.zeilen)
        // Schmal braucht der lange Name mehr als drei Zeilen und faellt weg
        assertEquals(listOf(kurz), WidgetKarten.zuschneiden(k, 146, 250).zeilen)
        assertTrue(WidgetKarten.textZeilen(lang.text, 100f, 13f) > WidgetKarten.textZeilen(lang.text, 300f, 13f))
    }

    @Test
    fun wertRutschtUnterDenTextWennErNebenIhmNichtPasst() {
        val z = WidgetZeile("Wochen", "Uneinheitlich aufwärts", true)
        val schmal = WidgetKarten.zuschneiden(WidgetKarte("T", listOf(z)), 146, 250).zeilen.single()
        assertEquals("", schmal.wert)
        assertEquals("Uneinheitlich aufwärts", schmal.unter)
        assertEquals(true, schmal.unterHoch)
        assertTrue(schmal.gestapelt)
        // Breit steht er rechts daneben, unveraendert
        assertEquals(z, WidgetKarten.zuschneiden(WidgetKarte("T", listOf(z)), 320, 250).zeilen.single())
    }

    @Test
    fun ohneZeilenStehtDerHinweis() {
        val k = WidgetKarte("Orders", emptyList(), hinweis = "Keine offenen Orders und keine Sparpläne")
        val z = WidgetKarten.zuschneiden(k, 146, 146, leer = "Zum Laden die App öffnen")
        assertTrue(z.zeilen.isEmpty())
        assertEquals("Keine offenen Orders und keine Sparpläne", z.hinweis)
        // Ohne Karte der Hinweis des Aufrufers
        assertEquals("Zum Laden die App öffnen", WidgetKarten.zuschneiden(null, 146, 146, leer = "Zum Laden die App öffnen").hinweis)
        // Karte ohne eigenen Hinweis
        assertEquals("leer", WidgetKarten.zuschneiden(WidgetKarte("T"), 146, 146, leer = "leer").hinweis)
    }

    @Test
    fun titelUndStandNurWennSiePassen() {
        val k = WidgetKarte("Ein sehr langer Titel der nie passt", zeilen(2), stand = "Stand 12:00")
        assertFalse(WidgetKarten.zuschneiden(k, 110, 146).titel)
        assertTrue(WidgetKarten.zuschneiden(k, 320, 146).titel)
        // Sehr niedrig: keine Standzeile
        assertFalse(WidgetKarten.zuschneiden(k, 320, 90).stand)
    }

    // ------------------------------------------------------------ Aufbau je Kategorie

    @Test
    fun ausblickZeigtWochenMonateUndGruende() {
        val k = WidgetKartenBau.ausblick(0, 3, listOf("Zinserwartung" to true, "Blasenzeichen" to false, "Lager" to true, "Vierter" to true), "Stand 10:00", 5L)
        assertEquals("Ausblick · Aktien", k.titel)
        assertEquals(listOf("Wochen", "Monate", "Zinserwartung", "Blasenzeichen", "Lager", "Vierter"), k.zeilen.map { it.text })
        assertEquals("aufwärts", k.zeilen[0].wert)
        assertEquals(true, k.zeilen[0].hoch)
        assertEquals("gemischt", k.zeilen[1].wert)
        assertNull(k.zeilen[1].hoch)
        assertEquals("dafür", k.zeilen[2].wert)
        assertEquals(false, k.zeilen[3].hoch)
        assertEquals("maerkte/ausblick", k.ziel)
        // Kleinstes Widget: nur die beiden Horizonte und der erste Grund
        val z = WidgetKarten.zuschneiden(k, 146, 146)
        assertEquals("Wochen", z.zeilen.first().text)
        assertEquals("Monate", z.zeilen[1].text)
    }

    @Test
    fun bewegungenWechselnGewinnerUndVerlierer() {
        val g = listOf(Bewegung("A", "Alpha", 12.0), Bewegung("B", "Beta", 8.0), Bewegung("C", "Gamma", 5.0), Bewegung("D", "Delta", 4.0))
        val v = listOf(Bewegung("X", "Xi", -9.0), Bewegung("Y", "Ypsilon", -3.0))
        val k = WidgetKartenBau.bewegungen(g, v, "Stand 10:00", 1L)
        assertEquals(listOf("Alpha", "Xi", "Beta", "Ypsilon", "Gamma"), k.zeilen.map { it.text })
        assertEquals(listOf(true, false, true, false, true), k.zeilen.map { it.hoch })
        assertEquals("instrument/A", k.zeilen[0].ziel)
        // Schon zwei Zeilen zeigen beide Seiten
        assertEquals(listOf("Alpha", "Xi"), WidgetKarten.zuschneiden(k, 146, 146).zeilen.map { it.text }.take(2))
        assertEquals("maerkte/bewegungen", k.ziel)
        // Ohne Kurse der Hinweis
        assertTrue(WidgetKartenBau.bewegungen(emptyList(), emptyList(), "", 0L).hinweis.isNotBlank())
    }

    @Test
    fun trendsZeigenRichtungDerErwaehnungen() {
        val k = WidgetKartenBau.trends(
            listOf(Trendwert("NVDA", "Nvidia", 300, 200), Trendwert("GME", "GameStop", 90, 180), Trendwert("NEU", "Neuling", 40, null)), "Stand 10:00", 1L,
        )
        assertEquals("+50\u00a0%", k.zeilen[0].wert)
        assertEquals(true, k.zeilen[0].hoch)
        assertEquals(false, k.zeilen[1].hoch)
        assertEquals("neu", k.zeilen[2].wert)
        assertNull(k.zeilen[2].hoch)
        assertEquals("300 Erwähnungen", k.zeilen[0].unter)
        assertEquals("maerkte/trends", k.ziel)
    }

    @Test
    fun durchbrueche() {
        val neu = listOf(Durchbruch("Erster Impfstoff gegen X zugelassen", "Medizin", "vor 3 Std."))
        val k = WidgetKartenBau.durchbrueche(neu, listOf(Durchbruch("Erster Fusionsstrom", "Energie", "2030")), "Stand 10:00", 1L)
        assertEquals(1, k.zeilen.size)
        assertEquals("Medizin · vor 3 Std.", k.zeilen[0].unter)
        // Ohne neue der naechste erwartete Meilenstein
        val m = WidgetKartenBau.durchbrueche(emptyList(), listOf(Durchbruch("Erster Fusionsstrom", "Energie", "2030"), Durchbruch("B", "T", "2031")), "", 1L)
        assertEquals("Erster Fusionsstrom", m.zeilen[0].text)
        assertEquals("Erwartet · 2030 · Energie", m.zeilen[0].unter)
        // Ohne beides der Hinweis
        assertTrue(WidgetKartenBau.durchbrueche(emptyList(), emptyList(), "", 0L).zeilen.isEmpty())
    }

    @Test
    fun aktienfinderMitPunkten() {
        val k = WidgetKartenBau.aktienfinder("Deutschland", listOf(Fund("SAP.DE", "SAP", 78.4), Fund("X", "X AG", 50.0), Fund("Y", "Y AG", 35.0)), "Stand 10:00", 1L)
        assertEquals(listOf("78", "50", "35"), k.zeilen.map { it.wert })
        assertEquals(listOf<Boolean?>(true, null, false), k.zeilen.map { it.hoch })
        assertEquals("Deutschland · Stand 10:00", k.stand)
        assertEquals("aktienfinder", k.ziel)
    }

    @Test
    fun copytradingMitGeschaeftenSonstRangliste() {
        val h = listOf(
            Handel("Alt", "Kauf AAPL", true, "01.11.2026", 1L),
            Handel("Neu", "Verkauf NVDA +1", false, "05.11.2026", 5L),
            Handel("Buch", "13F", null, "03.11.2026", 3L),
        )
        val k = WidgetKartenBau.copytrading(h, emptyList(), "Stand 10:00", 1L)
        assertEquals(listOf("Neu", "Buch", "Alt"), k.zeilen.map { it.text })
        assertEquals(false, k.zeilen[0].hoch)
        assertEquals("05.11.2026", k.zeilen[0].unter)
        val r = WidgetKartenBau.copytrading(emptyList(), listOf(Platz("Frau A", 0.123, 14), Platz("Herr B", -0.04, 6)), "Stand 10:00", 1L)
        assertEquals("+12,3 Pkt.", r.zeilen[0].wert)
        assertEquals("−4,0 Pkt.", r.zeilen[1].wert)
        assertEquals(false, r.zeilen[1].hoch)
        assertTrue(r.stand.startsWith("Rangliste"))
        // Weder noch: Hinweis
        val leer = WidgetKartenBau.copytrading(emptyList(), emptyList(), "", 0L)
        assertTrue(leer.zeilen.isEmpty() && leer.hinweis.isNotBlank())
    }

    @Test
    fun prognoseMitWahrscheinlichkeitUndVeraenderung() {
        val k = WidgetKartenBau.prognose(
            listOf(
                Wette("Zinssenkung im Dezember?", 0.62, 3.4, "", "pm-e/POLYMARKET/abc"),
                Wette("Wer gewinnt die Wahl?", 0.004, 0.2, "Kandidat A", "pm-e/POLYMARKET/def"),
                Wette("Rezession?", 0.995 + 0.002, -6.0, ""),
            ),
            "Stand 10:00", 1L,
        )
        assertEquals("62\u00a0%", k.zeilen[0].wert)
        assertEquals("+3 Pkt.", k.zeilen[0].unter)
        assertEquals(true, k.zeilen[0].unterHoch)
        assertEquals("< 1 %", k.zeilen[1].wert)
        // Ausgang statt Veraenderung, wenn die Wette mehrere Ausgaenge hat; kleine Veraenderungen zaehlen nicht
        assertEquals("Kandidat A", k.zeilen[1].unter)
        assertEquals("> 99 %", k.zeilen[2].wert)
        assertEquals("−6 Pkt.", k.zeilen[2].unter)
        assertEquals(false, k.zeilen[2].unterHoch)
        assertEquals("pm-e/POLYMARKET/abc", WidgetZiel.pruefen(k.zeilen[0].ziel))
    }

    @Test
    fun ordersMitPlaenenNachTermin() {
        val a = listOf(Auftrag("Apple", "AAPL", "Kaufen · 3 Stk. · Limit", "150,00 €"))
        val p = listOf(
            Plan("Welt-ETF", "100,00 €", 200L, "02.12."), Plan("Gold", "50,00 €", 100L, "16.11."), Plan("Pausiert", "10,00 €", 0L, ""),
        )
        val k = WidgetKartenBau.orders(a, p, "", 0L)
        assertEquals(listOf("Apple", "Gold", "Welt-ETF", "Pausiert"), k.zeilen.map { it.text })
        assertEquals("Nächste Ausführung 16.11.", k.zeilen[1].unter)
        assertEquals("", k.zeilen[3].unter)
        assertEquals("orders", k.ziel)
        // Zwei Zeilen im kleinsten Widget: die Order und der naechste Plan
        assertEquals(listOf("Apple", "Gold"), WidgetKarten.zuschneiden(k, 146, 146).zeilen.map { it.text }.take(2))
    }

    @Test
    fun analyticsKennzahlen() {
        val k = WidgetKartenBau.analytics(12.34, -8.1, "Apple", 23.4, "", 0L)
        assertEquals(listOf("Rendite", "Größter Rückgang", "Apple"), k.zeilen.map { it.text })
        assertEquals("+12,3\u00a0%", k.zeilen[0].wert)
        assertEquals(true, k.zeilen[0].hoch)
        assertEquals("−8,1\u00a0%", k.zeilen[1].wert)
        assertEquals(false, k.zeilen[1].hoch)
        assertEquals("23\u00a0%", k.zeilen[2].wert)
        assertEquals("Größte Position", k.zeilen[2].unter)
        assertEquals("analytics", WidgetZiel.pruefen(k.ziel))
        // Ohne Depot nichts, nur der Hinweis
        val leer = WidgetKartenBau.analytics(null, null, null, null, "", 0L)
        assertTrue(leer.zeilen.isEmpty() && leer.hinweis.isNotBlank())
    }

    private val module = listOf(
        WissenModul(1, "Geld", listOf(WissenLektion("m1-l1", "Zins", 8), WissenLektion("m1-l2", "Inflation", 6), WissenLektion("m1-l3", "Risiko", 7))),
        WissenModul(2, "Aktien", listOf(WissenLektion("m2-l1", "Bilanz", 9), WissenLektion("m2-l2", "Dividende", 5))),
    )

    @Test
    fun wissenWeiterNachDerLetztenLektion() {
        val k = WidgetKartenBau.wissen(module, setOf("m1-l1"), "m1-l1", 0L)
        assertEquals(listOf("Inflation", "Risiko"), k.zeilen.map { it.text })
        assertEquals("6 Min.", k.zeilen[0].wert)
        assertEquals("Modul 1 · Geld", k.zeilen[0].unter)
        assertEquals("1 von 5 Lektionen", k.stand)
        assertEquals("knowledge", k.ziel)
    }

    @Test
    fun wissenOhneFortschrittBeginntVorn() {
        val k = WidgetKartenBau.wissen(module, emptySet(), null, 0L)
        assertEquals("Zins", k.zeilen.first().text)
        assertEquals("0 von 5 Lektionen", k.stand)
    }

    @Test
    fun wissenGehtImNaechstenModulWeiterWennEinsFertigIst() {
        val k = WidgetKartenBau.wissen(module, setOf("m1-l1", "m1-l2", "m1-l3"), "m1-l3", 0L)
        assertEquals(listOf("Bilanz", "Dividende"), k.zeilen.map { it.text })
        assertEquals("Modul 2 · Aktien", k.zeilen[0].unter)
    }

    @Test
    fun wissenLaesstAuchUeberspringenZuUndMeldetFertig() {
        // Nach der Lektion in der Mitte weiter, danach die uebersprungene
        val k = WidgetKartenBau.wissen(module, setOf("m1-l2"), "m1-l2", 0L)
        assertEquals(listOf("Risiko", "Zins"), k.zeilen.map { it.text })
        val fertig = WidgetKartenBau.wissen(module, module.flatMap { m -> m.lektionen.map { it.id } }.toSet(), "m2-l2", 0L)
        assertTrue(fertig.zeilen.isEmpty())
        assertEquals("Alle Lektionen abgeschlossen", fertig.hinweis)
    }

    // ------------------------------------------------------------ Ziele und Ablage

    @Test
    fun kategorieZieleWerdenAngenommen() {
        for (art in WidgetKarten.ARTEN) {
            val z = WidgetKarten.ziel(art)
            assertEquals(art, z, WidgetZiel.pruefen(z))
        }
        assertNull(WidgetZiel.pruefen("maerkte/sonstwas"))
        assertNull(WidgetZiel.pruefen("pm-e/ANDERE/abc"))
        assertNull(WidgetZiel.pruefen("pm-e/POLYMARKET/"))
    }

    @Test
    fun kartenUeberstehenDieAblage() {
        val d = WidgetDaten(karten = mapOf("orders" to WidgetKartenBau.orders(listOf(Auftrag("A", "AAPL", "Kaufen", "1 €")), emptyList(), "S", 3L)))
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        assertEquals(d, json.decodeFromString(WidgetDaten.serializer(), json.encodeToString(WidgetDaten.serializer(), d)))
        // alte Ablage ohne Karten
        assertEquals(emptyMap<String, WidgetKarte>(), json.decodeFromString(WidgetDaten.serializer(), "{}").karten)
    }

    @Test
    fun jedeAnlegbareKategorieHatEinenTitel() {
        for (art in WidgetKarten.ANLEGBAR) assertTrue(art, WidgetKarten.titel(art).isNotBlank() && WidgetKarten.titel(art) != art)
    }
}
