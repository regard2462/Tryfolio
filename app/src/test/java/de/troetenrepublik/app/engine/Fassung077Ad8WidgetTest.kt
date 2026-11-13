package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.WidgetKarte
import de.troetenrepublik.app.data.WidgetKarten
import de.troetenrepublik.app.data.WidgetKartenBau
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.data.WidgetZeile
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.Gesamt13f
import de.troetenrepublik.app.widgetBezeichnung
import de.troetenrepublik.app.widgetGekaufte
import de.troetenrepublik.app.widgetHaeuser
import de.troetenrepublik.app.widgetMeistgekauft
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Widgets Meistgekauft (13F-Gesamtbild) und Haus-Rangliste (0.77, Teil AD8): was die Karte bei vorhandenen, leeren und
// kaputten Daten zeigt, Ziele und Vorgaben, Zuschnitt in kleiner und breiter Groesse.
class Fassung077Ad8WidgetTest {

    @Before fun deutsch() { I18n.lang = Lang.DE }

    // Gekuerzter Aufbau der Datei copytrading/gesamt13f.json: h Halter, auf/neu/ab/weg, w Wert, f Fluss in Dollar, n Name.
    // AAPL und NVDA: viele Netto-Kaeufer mit Zufluss; TSLA: Netto-Kaeufer, aber Abfluss; SMALL: zu wenige Halter;
    // XOM: Netto-Verkaeufer; ABBV: Name mit Leerzeichen; SPLIT: Sonderfall (Abspaltung).
    private val dateiText = """{"stichtag":"2026-06-30","vorher":"2026-03-31","bis":"2026-08-31","melder":8827,"melderVorher":8839,"gruppe":8696,"aktien":{
        "AAPL":{"h":6112,"hv":6000,"neu":300,"weg":100,"auf":2500,"ab":1700,"w":9.9e11,"s":1e10,"sg":1e10,"sv":9e9,"f":3.2e10,"t":[],"n":"Apple"},
        "NVDA":{"h":5400,"hv":5300,"neu":250,"weg":60,"auf":2300,"ab":1800,"w":7.9e11,"s":1e10,"sg":1e10,"sv":9e9,"f":2.1e10,"t":[],"n":"NVIDIA Corp"},
        "TSLA":{"h":4000,"hv":3900,"neu":200,"weg":50,"auf":2000,"ab":1700,"w":4e11,"s":1e10,"sg":1e10,"sv":9e9,"f":-1.0e9,"t":[],"n":"Tesla"},
        "SMALL":{"h":20,"hv":20,"neu":5,"weg":0,"auf":10,"ab":2,"w":1e6,"s":1e5,"sg":1e5,"sv":9e4,"f":1e5,"t":[],"n":"Small Co"},
        "XOM":{"h":3000,"hv":3000,"neu":10,"weg":200,"auf":900,"ab":1500,"w":1e11,"s":1e9,"sg":1e9,"sv":1.1e9,"f":-5e9,"t":[],"n":"Exxon"},
        "ABBV":{"h":3500,"hv":3400,"neu":120,"weg":20,"auf":1500,"ab":1200,"w":1e11,"s":1e9,"sg":1e9,"sv":9e8,"f":4e9,"t":[],"n":"AbbVie Inc"},
        "SPLIT":{"h":3500,"hv":3400,"neu":500,"weg":20,"auf":1500,"ab":1200,"w":1e11,"s":1e9,"sg":1e9,"sv":9e8,"f":9e9,"t":[],"n":"Split","x":1}
    }}"""

    private fun datei() = Gesamt13f.lesen(dateiText)!!

    // ------------------------------------------------------------ Meistgekauft

    @Test fun meistgekauftNimmtNurNettoKaeuferMitZufluss() {
        val l = widgetGekaufte(datei())
        // TSLA (Abfluss), SMALL (zu wenige Halter), XOM (Verkaeufer) und SPLIT (Sonderfall) fehlen
        assertEquals(3, l.size)
        assertEquals(listOf(1000, 690, 400), l.map { it.netto })
        assertTrue(l.all { it.zufluss > 0 })
    }

    @Test fun bezeichnungIstKurzerNameOderKuerzel() {
        assertEquals("Apple", widgetBezeichnung("Apple", "AAPL"))
        // mehr als neun Zeichen oder mehr als ein Wort: das Kuerzel
        assertEquals("NVDA", widgetBezeichnung("NVIDIA Corp", "NVDA"))
        assertEquals("ABBV", widgetBezeichnung("AbbVie Inc", "ABBV"))
        assertEquals("BRK-B", widgetBezeichnung("Berkshire Hathaway", "BRK-B"))
        assertEquals("XYZ", widgetBezeichnung("", "XYZ"))
        val l = widgetGekaufte(datei())
        // "NVIDIA Corp" verliert den Zusatz und ist dann kurz genug
        assertEquals(listOf("Apple", "NVIDIA"), l.map { it.bezeichnung }.take(2))
        assertTrue(l[2].bezeichnung in setOf("ABBV", "AbbVie"))
    }

    @Test fun meistgekauftKarteMitDaten() {
        val k = widgetMeistgekauft(datei(), 5L)
        assertEquals("Meistgekauft", k.titel)
        assertEquals(3, k.zeilen.size)
        assertEquals(5L, k.am)
        // Zahl der Netto-Kaeufer als Wert in Kaufgruen, Zufluss darunter, Balken im Verhaeltnis zum staerksten
        assertEquals("1.000", k.zeilen[0].wert)
        assertEquals(true, k.zeilen[0].hoch)
        assertTrue(k.zeilen[0].unter.startsWith("Fonds · Zufluss "))
        assertEquals(listOf(1.0), k.zeilen[0].balken)
        assertEquals(listOf(690.0 / 1000), k.zeilen[1].balken)
        assertTrue(k.zeilen.all { it.einzeilig })
        // Stichtag des Quartals in der Standzeile
        assertEquals("Quartal bis 30.06.2026", k.stand)
        assertEquals("copytrading", k.ziel)
        assertEquals("copytrading", WidgetZiel.pruefen(k.ziel))
    }

    @Test fun meistgekauftAufEnglisch() {
        I18n.lang = Lang.EN
        val k = widgetMeistgekauft(datei(), 1L)
        assertEquals("Quarter to 30 Jun 2026", k.stand)
        assertEquals("1,000", k.zeilen[0].wert)
        assertTrue(k.zeilen[0].unter.startsWith("funds · Inflow "))
        assertEquals("No 13F data", widgetMeistgekauft(null, 1L).hinweis)
    }

    @Test fun meistgekauftOhneDatenHatKeineZeilenUndEinenHinweis() {
        for (d in listOf(null, Gesamt13f.lesen("""{"stichtag":"2026-06-30","aktien":{}}"""), Gesamt13f.lesen("kaputt{"))) {
            val k = widgetMeistgekauft(d, 7L)
            assertTrue(k.zeilen.isEmpty())
            assertEquals("Keine 13F-Daten", k.hinweis)
            assertEquals("", k.stand)
            assertEquals(7L, k.am)
        }
        // Eine Datei, in der niemand netto kauft: ebenfalls keine Zeilen
        val nurVerkauf = Gesamt13f.lesen(
            """{"stichtag":"2026-06-30","aktien":{"XOM":{"h":3000,"hv":3000,"neu":10,"weg":200,"auf":900,"ab":1500,"w":1e11,"s":1e9,"sg":1e9,"sv":1.1e9,"f":-5e9,"t":[],"n":"Exxon"}}}""",
        )
        assertTrue(widgetMeistgekauft(nurVerkauf, 1L).zeilen.isEmpty())
    }

    // ------------------------------------------------------------ Haus-Rangliste

    private val haeuserText = """{"schema":1,"stand":"2026-11-11","von":"2011-12-08","bis":"2026-08-13","aktien":525,"urteile":28268,"minUrteile":20,
        "haeuser":[
        {"h":"Melius Research","u":32,"hoch":19,"ab":13,"q":0.5957,"v":3.1,"a":12,"z":"2024-08"},
        {"h":"Cleveland Research","u":39,"hoch":21,"ab":18,"q":0.6496,"v":5.37,"a":29,"z":"2024-08"},
        {"h":"Summit Insights Group","u":1048,"hoch":22,"ab":26,"q":0.6042,"v":4.7,"a":20,"z":"2024-08"},
        {"h":"Ohne Anteil","u":10,"hoch":1,"ab":1,"v":1.0,"a":1,"z":"2024-08"},
        {"h":"Vierter","u":50,"hoch":20,"ab":30,"q":0.4,"v":0.5,"a":9,"z":"2024-08"}
        ]}"""

    @Test fun haeuserNimmtDieMitHoechstemTrefferanteil() {
        val l = widgetHaeuser(Haeuserliste.lesen(haeuserText))
        assertEquals(listOf("Cleveland Research", "Summit Insights Group", "Melius Research", "Vierter"), l.map { it.name })
        // Ein Haus ohne Trefferanteil ist nicht in der Liste
        assertFalse(l.any { it.name == "Ohne Anteil" })
    }

    @Test fun haeuserKarteMitDaten() {
        val k = WidgetKartenBau.haeuser(widgetHaeuser(Haeuserliste.lesen(haeuserText)), 9L)
        assertEquals("Haus-Rangliste", k.titel)
        assertEquals(4, k.zeilen.size)
        assertEquals(de.troetenrepublik.app.ui.Fmt.pct(64.96, 0), k.zeilen[0].wert)
        assertTrue(k.zeilen[0].wert.startsWith("65"))
        assertEquals("39 Urteile", k.zeilen[0].unter)
        // Tausender mit Punkt, Wert bricht nie um
        assertEquals("1.048 Urteile", k.zeilen[1].unter)
        assertEquals(listOf(0.6496), k.zeilen[0].balken)
        assertTrue(k.zeilen.all { it.einzeilig })
        assertEquals("Trefferanteil", k.stand)
        assertEquals(WidgetZiel.HAEUSER, k.ziel)
        assertEquals(WidgetZiel.HAEUSER, WidgetZiel.pruefen(k.ziel))
        assertEquals(9L, k.am)
    }

    @Test fun haeuserAufEnglisch() {
        I18n.lang = Lang.EN
        val k = WidgetKartenBau.haeuser(widgetHaeuser(Haeuserliste.lesen(haeuserText)), 1L)
        assertEquals("House ranking", k.titel)
        assertEquals("39 ratings", k.zeilen[0].unter)
        assertEquals("Hit rate", k.stand)
        assertEquals("65%", k.zeilen[0].wert)
    }

    @Test fun haeuserOhneDatenHatKeineZeilenUndEinenHinweis() {
        for (l in listOf(null, Haeuserliste.lesen("kaputt{"), Haeuserliste.lesen("""{"haeuser":[]}"""))) {
            val k = WidgetKartenBau.haeuser(widgetHaeuser(l), 3L)
            assertTrue(k.zeilen.isEmpty())
            assertEquals("Keine Rangliste verfügbar", k.hinweis)
            assertEquals("", k.stand)
        }
    }

    // ------------------------------------------------------------ Ziele und Vorgaben

    @Test fun artenSindAnlegbarMitTitelUndZiel() {
        for (a in WidgetKarten.ARTEN_AD8) {
            assertTrue(a in WidgetKarten.ARTEN)
            assertTrue(a in WidgetKarten.ANLEGBAR)
            assertTrue(WidgetKarten.titel(a).isNotBlank())
            assertEquals(a, WidgetKarten.ziel(a), WidgetZiel.pruefen(WidgetKarten.ziel(a)))
        }
        assertEquals(WidgetKarten.ARTEN.size, WidgetKarten.ARTEN.toSet().size)
        assertEquals("Meistgekauft", WidgetKarten.titel(WidgetKarten.MEISTGEKAUFT))
        assertEquals("Haus-Rangliste", WidgetKarten.titel(WidgetKarten.HAEUSER))
        assertEquals("analytics/HAEUSER", WidgetKarten.ziel(WidgetKarten.HAEUSER))
        assertNull(WidgetZiel.pruefen("analytics/ANDERES"))
    }

    @Test fun langdruckLegtDieWidgetsOhneEinstellungAn() {
        assertEquals(WidgetKarten.MEISTGEKAUFT, WidgetVorgabe.meistgekauft().art)
        assertEquals(WidgetKarten.HAEUSER, WidgetVorgabe.haeuser().art)
        assertTrue(WidgetVorgabe.meistgekauft().einstellung.isEmpty())
        // Das Werkzeug Haus-Rangliste in der Werkzeugliste von Analytics
        assertEquals(WidgetKarten.HAEUSER, WidgetVorgabe.werkzeug("HAEUSER")!!.art)
        assertEquals("meistgekauft", WidgetVorgabe.kartenSchluessel(WidgetKarten.MEISTGEKAUFT, "x"))
    }

    // ------------------------------------------------------------ Zuschnitt

    private fun gekauftKarte() = WidgetKartenBau.meistgekauft(
        listOf(
            WidgetKartenBau.Gekauft("Apple", 1100, 3.2e10), WidgetKartenBau.Gekauft("NVDA", 960, 2.1e10),
            WidgetKartenBau.Gekauft("Tesla", 800, 1.0e10), WidgetKartenBau.Gekauft("AMZN", 700, 9.0e9), WidgetKartenBau.Gekauft("MSFT", 650, 8.0e9),
        ),
        "Q2 26", 1L,
    )

    @Test fun kleinstesWidgetZeigtWenigeZeilenOhneUmbruch() {
        val z = WidgetKarten.zuschneiden(gekauftKarte(), 110, 110)
        assertTrue(z.titel)
        assertTrue(z.zeilen.isNotEmpty())
        assertTrue(z.zeilen.size < 5)
        // Jede gezeigte Zeile ist einzeilig; passt der Wert nicht neben den Namen, steht er in der kleinen Zeile
        assertTrue(z.zeilen.all { it.einzeilig })
        assertEquals("", z.hinweis)
    }

    @Test fun breitesWidgetZeigtMehrZeilenMitKleinerZeile() {
        val klein = WidgetKarten.zuschneiden(gekauftKarte(), 110, 110).zeilen.size
        val gross = WidgetKarten.zuschneiden(gekauftKarte(), 250, 330)
        assertTrue(gross.zeilen.size >= klein)
        assertEquals(5, gross.zeilen.size)
        assertTrue(gross.mitUnter)
        assertTrue(gross.stand)
    }

    @Test fun langerHausnameBrichtNieUm() {
        val k = WidgetKartenBau.haeuser(widgetHaeuser(Haeuserliste.lesen(haeuserText)), 1L)
        val schmal = WidgetKarten.zuschneiden(k, 110, 200)
        // Was in einer Zeile keinen Platz hat, fehlt, statt umzubrechen
        assertFalse(schmal.zeilen.any { it.text == "Summit Insights Group" })
        val breit = WidgetKarten.zuschneiden(k, 320, 330)
        assertEquals(4, breit.zeilen.size)
        assertTrue(breit.zeilen.any { it.text == "Summit Insights Group" })
    }

    @Test fun mehrzeiligeZeilenBleibenFuerAndereWidgetsErlaubt() {
        // Ohne einzeilig darf ein Text wie bisher bis zu drei Zeilen brechen
        val k = WidgetKarte("T", listOf(WidgetZeile("Cleveland Research Group", "65 %")))
        assertEquals(1, WidgetKarten.zuschneiden(k, 150, 200).zeilen.size)
        val einzeilig = WidgetKarte("T", listOf(WidgetZeile("Cleveland Research Group", "65 %", einzeilig = true)))
        assertEquals(0, WidgetKarten.zuschneiden(einzeilig, 150, 200).zeilen.size)
    }

    @Test fun karteUeberlebtSpeichernMitNeuemFeld() {
        val json = Json { ignoreUnknownKeys = true }
        val k = gekauftKarte()
        val zurueck = json.decodeFromString(WidgetKarte.serializer(), json.encodeToString(WidgetKarte.serializer(), k))
        assertEquals(k, zurueck)
        assertTrue(zurueck.zeilen.all { it.einzeilig })
        // Eine gespeicherte Karte ohne das Feld (vor diesem Stand) bleibt lesbar
        val alt = json.decodeFromString(WidgetKarte.serializer(), """{"titel":"T","zeilen":[{"text":"A","wert":"1"}]}""")
        assertFalse(alt.zeilen[0].einzeilig)
    }
}
