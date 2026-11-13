package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Breite Stichprobe wie sie in Feeds vorkommt, je Sprache zur Haelfte wichtige Meldungen und zur Haelfte Schlagzeilen,
// die Teilwoerter, Alltagswoerter oder Kuerzel falsch treffen koennten. Das Depot enthaelt Werte mit Alltagskuerzeln.
class WichtigStichprobeTest {

    private val depot = Wichtig.depotBegriffe(
        listOf("ServiceNow Inc", "Allstate Corp", "Caterpillar Inc", "KeyCorp", "Target Corp", "Block, Inc.", "Apple Inc.", "NVIDIA Corporation", "Gap Inc."),
        listOf("NOW", "ALL", "CAT", "KEY", "TGT", "XYZ", "AAPL", "NVDA", "GAP", "LOW", "FAST", "ARE"),
    )

    private fun punkte(titel: String, her: String = "reuters.com") = Wichtig.bewerten(Rss.Eintrag(titel, "https://x.test/" + titel.hashCode(), 1L, herausgeber = her), depot)

    private val wichtigEn = listOf(
        "Fed cuts interest rates by a quarter point as inflation cools",
        "Oil prices surge after missile strike on Saudi refinery",
        "Wall Street plunges as S&P 500 suffers worst selloff since March",
        "Russia launches fresh attack on Ukraine energy grid",
        "Iran seizes tanker in Strait of Hormuz after drone attack",
        "US inflation rate rises to 3.4% in October",
        "Nvidia shares tumble 9% after record revenue, guidance disappoints",
        "Bitcoin falls below \$60,000 in biggest crash since 2022",
        "Trump announces 25% tariffs on imports from Mexico and Canada",
        "Bank of England raises interest rates to 5.5%",
        "ECB holds rates steady, signals no cut in December",
        "Eurozone GDP shrinks in third quarter as recession fears grow",
        "Lufthansa files for bankruptcy protection",
        "OPEC agrees surprise output cut, sending oil higher",
        "Missile strike hits Kyiv as Russia escalates war",
        "Shutdown looms as US debt ceiling talks collapse",
        "Breaking: Powell to resign as Fed chair",
        "Microsoft to acquire Activision for \$69 billion",
        "Gold hits record high as investors flee to safety",
        "Moody's downgrades US credit rating as yields jump",
        "Israel strikes on Iran oil facilities spark oil price jump",
        "ServiceNow (NYSE: NOW) shares plunge 12% after earnings miss",
        "Allstate (NYSE: ALL) beats estimates and raises guidance",
        "Taiwan reports Chinese warship movements, missile launch feared",
    )

    private val nichtWichtigEn = listOf(
        "Investors weigh strike risk amid market turmoil",
        "Trump attacks Senator Warren over bank rules",
        "Analysts strike bullish tone on Tirana airport IPO",
        "Storm season: all eyes on insurers as Florida braces for hurricane",
        "Cat videos drive record traffic for streaming site",
        "Now is the time to rethink retirement savings, advisers say",
        "Low rates, high anxiety: homebuyers wait on the sidelines",
        "Key takeaways from the climate summit",
        "Fast fashion retailer opens 40 new stores in Europe",
        "Target date funds lose appeal as retirees rethink",
        "Marvel's new film breaks box office record in opening weekend",
        "Star Wars series cancelled after one season",
        "Bidding war erupts for historic Manhattan townhouse",
        "Price war between delivery apps hits restaurant margins",
        "Apple harvest in Washington state hit by early frost",
        "Chip shortage eases as automakers restock",
        "Tesla fans gather for annual meeting in Texas",
        "Tanker driver strike disrupts fuel deliveries across Britain",
        "Workers strike at Boeing plant over pay",
        "Cyber attack on retailer exposes customer data",
        "Russell 2000 ends higher as small caps rally",
        "Senator weighs bill to ban stock trading by lawmakers",
        "Where to eat in Tirana, Albania: a food lover's guide",
        "Block party planned for Saturday on Main Street",
    )

    private val wichtigDe = listOf(
        "EZB senkt Leitzins um 0,25 Prozentpunkte",
        "Fed belässt Leitzins unverändert",
        "Inflationsrate in Deutschland steigt auf 3,1 Prozent",
        "DAX bricht ein: Ausverkauf an den Börsen erfasst Banken und Autowerte",
        "Russland greift Kiew mit Raketen an",
        "Raketenangriff auf Öltanker im Roten Meer",
        "Iran und Israel: Waffenstillstand gebrochen",
        "Eilmeldung: Bundesregierung kündigt Rücktritt an",
        "Trump verhängt neue Zölle auf Stahl aus der EU",
        "Lufthansa meldet Insolvenz an",
        "Bayer senkt Prognose und streicht Dividende",
        "Ölpreis springt nach Drohnenangriff auf Raffinerie in Saudi-Arabien",
        "Wirtschaft schrumpft im dritten Quartal: Deutschland in der Rezession",
        "Arbeitslosigkeit steigt: Arbeitslosenquote auf höchstem Stand seit 2015",
        "Rheinmetall übernimmt Munitionshersteller für 2 Milliarden Euro",
        "Siemens-Aktie bricht nach Gewinnwarnung ein",
        "Bitcoin stürzt unter 50.000 Dollar",
        "Gold erreicht Rekordhoch",
        "Bundesbank warnt vor Rezession",
        "Nato-Gipfel: Beschuss von Handelsschiffen im Schwarzen Meer",
        "Notenbank in Japan erhöht Zinsen erstmals seit 17 Jahren",
        "Zahlungsausfall: Evergrande kann Anleihen nicht bedienen",
        "Allstate-Aktie bricht nach Gewinnwarnung ein (NYSE: ALL)",
        "Kriegsschiff trifft Tanker im Roten Meer: Ölpreis steigt",
    )

    private val nichtWichtigDe = listOf(
        "Messerangriff in Köln: Polizei sucht Täter",
        "Mercedes-Benz Zölle: Angriff auf Marge",
        "Gewinn bis Ende Juni: Siemens hebt Ausblick",
        "Heizkosten bis zu 20 Prozent: Experten warnen",
        "Alle Zahlen im Überblick: So lief der Börsentag",
        "Jetzt schon an die Rente denken: Was Berufseinsteiger wissen sollten",
        "Wetter: Sturm legt Bahnverkehr in Norddeutschland lahm",
        "Krieg der Sterne: Neue Serie startet im Dezember",
        "Preiskrieg im Supermarkt: Discounter senken Preise",
        "Tankerkönig: Spritpreise sinken vor dem Wochenende",
        "Streik bei der Bahn: Lokführer legen Arbeit nieder",
        "Russell 2000 schließt höher",
        "Teure Pipeline-Panne: Gasleck legt Dorf lahm",
        "Köln bekommt neues Stadion: Ölraffinerie weicht Neubau",
        "Allstate-Aktie: Analysten sehen weiter Luft nach oben",
        "Jetzt oder nie: Immobilienpreise im Check",
        "Alle Jahre wieder: Der Weihnachtsmarkt eröffnet",
        "Nach dem Beschuss der Kritik: Minister verteidigt Entwurf",
        "Angriff ist die beste Verteidigung: Bayern spielt gegen Dortmund",
        "Nato-Mitglied Albanien: Urlaub in Tirana wird teurer",
        "Senator Meier besucht Hannover",
        "Köln: Kölsch-Preise steigen zum Karneval",
        "Zölle auf Kaffee: Was Verbraucher jetzt wissen müssen",
        "Block-Heizkraftwerk in Bochum wird saniert",
    )

    @Test(timeout = 20_000)
    fun stichprobeHatDieVorgabe() {
        assertTrue(wichtigEn.size >= 20 && nichtWichtigEn.size >= 20 && wichtigEn.size + nichtWichtigEn.size >= 40)
        assertTrue(wichtigDe.size >= 20 && nichtWichtigDe.size >= 20 && wichtigDe.size + nichtWichtigDe.size >= 40)
    }

    @Test(timeout = 20_000)
    fun englischeSchlagzeilen() {
        val falsch = wichtigEn.filter { punkte(it).punkte < Wichtig.SCHWELLE }.map { "fehlt: $it " + punkte(it) } +
            nichtWichtigEn.filter { punkte(it).punkte >= Wichtig.SCHWELLE }.map { "zu viel: $it " + punkte(it) }
        assertEquals(emptyList<String>(), falsch)
    }

    @Test(timeout = 20_000)
    fun deutscheSchlagzeilen() {
        val falsch = wichtigDe.filter { punkte(it, "tagesschau.de").punkte < Wichtig.SCHWELLE }.map { "fehlt: $it " + punkte(it, "tagesschau.de") } +
            nichtWichtigDe.filter { punkte(it, "tagesschau.de").punkte >= Wichtig.SCHWELLE }.map { "zu viel: $it " + punkte(it, "tagesschau.de") }
        assertEquals(emptyList<String>(), falsch)
    }

    @Test(timeout = 20_000)
    fun kuerzelNurMitGrossschreibungOderKurszeichen() {
        assertTrue("\$NOW" in depot && "\$AAPL" in depot)
        assertFalse(Wichtig.trifft("All tariffs will rise", "\$ALL"))
        assertFalse(Wichtig.trifft("It is now or never", "\$NOW"))
        assertFalse(Wichtig.trifft("ALL tariffs will rise", "\$ALL"))
        assertTrue(Wichtig.trifft("Allstate (NYSE: ALL) beats estimates", "\$ALL"))
        assertTrue(Wichtig.trifft("\$NOW jumps after earnings", "\$NOW"))
        assertTrue(Wichtig.trifft("ServiceNow (NOW) raises outlook", "\$NOW"))
        // Kein Alltagswort: Grossbuchstaben genuegen, Kleinschreibung nicht
        assertTrue(Wichtig.trifft("NVDA jumps 5%", "\$NVDA"))
        assertFalse(Wichtig.trifft("nvda jumps 5%", "\$NVDA"))
        assertFalse(Wichtig.trifft("Nvidia jumps 5%", "\$NVDA"))
        assertTrue(Wichtig.trifft("Nvidia jumps 5%", "NVIDIA"))
    }

    @Test(timeout = 20_000)
    fun namenAusAlltagswoertern() {
        assertFalse(Wichtig.trifft("Target date funds lose appeal", "Target"))
        assertFalse(Wichtig.trifft("The target price is unchanged", "Target"))
        assertTrue(Wichtig.trifft("Target shares fall after weak results", "Target"))
        assertFalse(Wichtig.trifft("target shares fall", "Target"))
        assertTrue(Wichtig.trifft("Apple harvest hit by frost", "Apple"))
    }

    @Test(timeout = 20_000)
    fun alltagskuerzelGebenKeinenDepotZuschlag() {
        val b = punkte("All tariffs on steel to take effect Friday, White House says")
        assertFalse("im Depot" in b.gruende)
        assertTrue("im Depot" in punkte("Caterpillar jumps as Fed cuts rates").gruende)
    }
}
