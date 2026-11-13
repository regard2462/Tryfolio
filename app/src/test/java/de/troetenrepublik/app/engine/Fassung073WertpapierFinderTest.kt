package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.BullBear.Thema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

// Korrekturen an Wertpapierseite, Waage und Aktienfinder (0.73): Börsenwert bei Pence-Notierungen, Wachstum bei
// negativem Vorjahr, Stichwortregeln der Waage, Vergleichbarkeit der Finder-Punkte, Vorlagen, Kursspanne bei Krypto.
class Fassung073WertpapierFinderTest {

    // ---- Börsenwert: Yahoo meldet ihn in der Hauptwährung, der Kurs steht in Pence, Agorot oder Cent

    @Test(timeout = 5000)
    fun boersenwertLondonInPfund() {
        // SHEL.L (Abruf 09.11.2026): Kurs 3701 GBp, 5 663 324 316 Aktien, Börsenwert 209 599 627 264 (Pfund)
        val q = Quote("SHEL.L", price = 3701.0, nativeCurrency = "GBp", marketCap = 209_599_627_264.0)
        val eur = ExchangeRates.toEur(q, mapOf("GBP" to 0.87))!!
        assertEquals(209_599_627_264.0 / 0.87, eur.marketCap, 1.0)
        // Kurs mal Aktienzahl in Euro ergibt denselben Börsenwert
        assertEquals(eur.price * 5_663_324_316.0, eur.marketCap, eur.marketCap * 1e-6)
    }

    @Test(timeout = 5000)
    fun boersenwertJeWaehrungseinheit() {
        // (Währung, Kurs in Kurseinheit, Aktien in Mio, Wechselkurs der Hauptwährung je Euro)
        val faelle = listOf(
            Triple("GBp", "GBP", 0.87), Triple("GBX", "GBP", 0.87), Triple("ILA", "ILS", 3.9), Triple("ZAc", "ZAR", 20.1), Triple("USd", "USD", 1.16),
            Triple("USD", "USD", 1.16), Triple("EUR", "EUR", 1.0), Triple("SEK", "SEK", 11.0),
        )
        for ((code, haupt, kurs) in faelle) {
            val rates = if (haupt == "EUR") emptyMap() else mapOf(haupt to kurs)
            val aktien = 1_000_000_000.0
            val faktor = if (code in listOf("GBp", "GBX", "ILA", "ZAc", "USd")) 100.0 else 1.0
            // Kurs 50 Hauptwährungseinheiten; Börsenwert = Kurs * Aktien in Hauptwährung
            val q = Quote("X", price = 50.0 * faktor, nativeCurrency = code, marketCap = 50.0 * aktien)
            val eur = ExchangeRates.toEur(q, rates)!!
            assertEquals("$code Börsenwert", 50.0 * aktien / kurs, eur.marketCap, 1.0)
            assertEquals("$code Kurs mal Aktien", eur.price * aktien, eur.marketCap, eur.marketCap * 1e-9)
        }
    }

    @Test(timeout = 5000)
    fun hauptwaehrungUndRate() {
        assertEquals("GBP", ExchangeRates.hauptwaehrung("GBp"))
        assertEquals("ILS", ExchangeRates.hauptwaehrung("ILA"))
        assertEquals("ZAR", ExchangeRates.hauptwaehrung("ZAc"))
        assertEquals("SEK", ExchangeRates.hauptwaehrung("SEK"))
        val fx = mapOf("GBP" to 0.87)
        assertEquals(0.87, ExchangeRates.rateHaupt("GBp", fx)!!, 1e-12)
        assertEquals(87.0, ExchangeRates.rate("GBp", fx)!!, 1e-9)
        // Börsenwert aus dem Profil (Pfund) in Euro: Pfund durch Pfundkurs, nicht durch den Pence-Kurs
        assertEquals(100.0 / 0.87, 100.0 / ExchangeRates.rateHaupt("GBp", fx)!!, 1e-9)
    }

    // ---- Wachstum bei negativem Vorjahr

    @Test(timeout = 5000)
    fun wachstumBeiNegativemVorjahr() {
        // von −10 auf +5: rechnerisch +150 %, die alte Formel zeigte −50 %
        assertEquals(150.0, Bewertungsblick.veraenderung(5.0, -10.0)!!, 1e-9)
        // Verlust halbiert: +50 %, die alte Formel zeigte −150 %
        assertEquals(50.0, Bewertungsblick.veraenderung(-5.0, -10.0)!!, 1e-9)
        assertEquals(20.0, Bewertungsblick.veraenderung(12.0, 10.0)!!, 1e-9)
        assertNull(Bewertungsblick.veraenderung(5.0, 0.0))
    }

    @Test(timeout = 5000)
    fun vorzeichenwechselIstKeinProzentwert() {
        assertEquals(Bewertungsblick.Wechsel.VERLUST_ZU_GEWINN, Bewertungsblick.wechsel(5.0, -10.0))
        assertEquals(Bewertungsblick.Wechsel.GEWINN_ZU_VERLUST, Bewertungsblick.wechsel(-5.0, 10.0))
        assertNull(Bewertungsblick.wechsel(-5.0, -10.0))
        assertNull(Bewertungsblick.wechsel(12.0, 10.0))
        assertNull(Bewertungsblick.wechsel(null, 10.0))
        assertNull(Bewertungsblick.wechsel(0.0, 10.0))
    }

    // ---- Stichwortregeln der Waage

    private fun themen(t: String) = BullBear.themen(t).toSet()

    @Test(timeout = 5000)
    fun betrugAlsVerbIstKeinBetrug() {
        assertEquals(emptySet<Thema>(), themen("Der Umsatz betrug 18 Mrd. Euro"))
        assertEquals(emptySet<Thema>(), themen("Gewinn betrug im dritten Quartal 5 Mrd. Dollar"))
        assertEquals(emptySet<Thema>(), themen("Betrug der Umsatz im Vorjahr noch 5 Mrd."))
        assertTrue(Thema.BILANZ in themen("Bilanzbetrug bei Wirecard: Anklage erhoben"))
        assertTrue(Thema.BILANZ in themen("Betrugsvorwürfe gegen den Vorstand"))
        assertTrue(Thema.BILANZ in themen("Ex-Manager wegen Betrugs angeklagt"))
        assertTrue(Thema.BILANZ in themen("Wegen Betrug verurteilt"))
        assertTrue(Thema.BILANZ in themen("Investor wegen Millionenbetrugs verurteilt"))
    }

    @Test(timeout = 5000)
    fun betrugsVerbVerschiebtDieWaageNicht() {
        // Umsatz +10 % (1,5) und Marge 25 % (1,0) sprechen dafür; eine Zahlenmeldung mit „betrug“ ändert daran nichts
        val punkte = BullBear.auswerten(
            BullBear.Fakten(umsatzWachstum = 0.10, gewinnMarge = 0.25),
            listOf(BullBear.Schlagzeile("Der Umsatz betrug 18 Mrd. Euro", 0L, "a")), jetzt = 1_000_000L,
        )
        assertEquals(100, BullBear.waage(punkte)!!.wert)
    }

    @Test(timeout = 5000)
    fun markenUndRedewendungenSindKeineTreffer() {
        assertEquals(emptySet<Thema>(), themen("Apple Beats Studio Buds im Test"))
        assertEquals(emptySet<Thema>(), themen("Spotify beats Samsung in the streaming race"))
        assertEquals(emptySet<Thema>(), themen("How to deal with a falling market"))
        assertEquals(emptySet<Thema>(), themen("Nasa space probe reaches Jupiter"))
        assertEquals(emptySet<Thema>(), themen("Stromausfall legt Stadt lahm"))
        assertEquals(emptySet<Thema>(), themen("Intuit launches new accounting software"))
        assertEquals(emptySet<Thema>(), themen("Company files its 10-K with the SEC"))
        assertEquals(emptySet<Thema>(), themen("Upgrade your phone before Black Friday"))
        assertEquals(emptySet<Thema>(), themen("Neuzulassungen steigen im Oktober"))
        assertEquals(emptySet<Thema>(), themen("Weltrekord im Marathon"))
        assertEquals(emptySet<Thema>(), themen("Sprinter übertrifft alle Erwartungen"))
        assertEquals(emptySet<Thema>(), themen("Auftragsflaute belastet Maschinenbauer"))
        assertEquals(emptySet<Thema>(), themen("Stiftung Warentest ermittelt beste Zahnpasta"))
        assertTrue(Thema.ZAHLEN in themen("Samsung beats estimates with chip rebound"))
        assertTrue(Thema.ERMITTLUNG in themen("SEC charges former CEO with fraud"))
        assertTrue(Thema.RUECKRUF in themen("AWS cloud outage hits Amazon customers"))
        assertTrue(Thema.AUFSTUFUNG in themen("Morgan Stanley upgrades Tesla to overweight"))
        assertTrue(Thema.ZULASSUNG in themen("Zulassung für neues Krebsmedikament erteilt"))
        assertTrue(Thema.AUFTRAG in themen("Siemens erhält Großauftrag aus Indien"))
    }

    private val ECHT_EN = listOf(
        "Lucid misses Wall Street’s earnings expectations, expands loan from Saudi Arabia fund" to setOf(Thema.WARNUNG),
        "Children’s Place files for bankruptcy protection, seeks buyer" to setOf(Thema.PLEITE),
        "PepsiCo to cut about 1,000 jobs as North American business struggles" to setOf(Thema.PERSONAL),
        "Boeing to cut 5% of commercial airplane workers as production increase takes longer than expected" to setOf(Thema.PERSONAL),
        "Goodyear Tire CEO Mark Stewart steps down as company continues turnaround plan" to setOf(Thema.PERSONAL),
        "Moderna raises 2026 outlook as CEO says newer vaccines are gaining traction" to setOf(Thema.PROGNOSE),
        "Wendy's rejects \$1.4 billion takeover proposal from Nelson Peltz’s Trian" to setOf(Thema.UEBERNAHME),
        "E.l.f. Beauty posts 51% sales increase, raises full-year guidance" to setOf(Thema.PROGNOSE),
        "McDonald's stock rises as earnings beat, U.S. restaurants show improvement" to setOf(Thema.ZAHLEN),
        "\$6.3 billion Binance hack is largest crypto theft on record" to setOf(Thema.RUECKRUF),
        "Ripple strikes deal with Robinhood to expand its RLUSD digital dollar" to setOf(Thema.AUFTRAG),
        "Rocket Lab shares dip after the company’s ‘defining’ quarter misses the mark" to setOf(Thema.WARNUNG),
        "Amazon says it had best-ever Thanksgiving Holiday week with record sales and number of items sold" to setOf(Thema.ZAHLEN),
        "Charter Communications announces buyout deal for Liberty Broadband at terms above its previous proposal" to setOf(Thema.UEBERNAHME),
        "Anheuser-Busch InBev shares slip as sales come up shy of estimates" to setOf(Thema.WARNUNG),
        "Wingstop’s stock slides 12% after profit falls short of estimates" to setOf(Thema.WARNUNG),
        "Novartis hikes sales and profit guidance for the year" to setOf(Thema.PROGNOSE),
        "Trainline boosts guidance for the second time this year" to setOf(Thema.PROGNOSE),
        "Barclays reports 18% profit rise as it lifts income guidance" to setOf(Thema.PROGNOSE),
        "DirecTV to acquire EchoStar’s video distribution business including Dish TV and Sling TV" to setOf(Thema.UEBERNAHME),
        "Moody’s places all of Boeing’s ratings on review for a downgrade" to setOf(Thema.ABSTUFUNG),
        "Microsoft to lay off 650 people from videogame business: WSJ" to setOf(Thema.PERSONAL),
        "ArcBest misses profit expectations, as soft rate environment weighed on revenue" to setOf(Thema.WARNUNG),
        "Hibbett shares fall as Q1 sales miss analysts’ estimate" to setOf(Thema.WARNUNG),
        "Stocks jump as oil tumbles, Nvidia falls amid internal probe" to setOf(Thema.ERMITTLUNG),
        "Earnings call transcript: Onto Innovation tops Q3 2026 estimates, shares jump" to setOf(Thema.ZAHLEN),
        "AMC Global Media earnings missed by \$0.01, revenue fell short of estimates" to setOf(Thema.WARNUNG),
        "Summit Midstream earnings beat by \$0.45, revenue topped estimates" to setOf(Thema.ZAHLEN),
        "As Nvidia probes Jensen Huang affair, here's the board member likely to play a big role" to setOf(Thema.ERMITTLUNG),
        "Nasdaq defies Nvidia impact as Huang faces espionage affair probe" to setOf(Thema.ERMITTLUNG),
        "Nvidia investigates CEO Jensen Huang's relationship with alleged Chinese spy" to setOf(Thema.ERMITTLUNG),
        "Tech companies face mounting child safety lawsuits while pushing AI into schools" to setOf(Thema.KLAGE),
        "Jensen Huang exit odds are low on prediction markets after Nvidia probe" to setOf(Thema.ERMITTLUNG),
        "Berkshire Hathaway more than doubles buybacks in third quarter as earnings decline" to setOf(Thema.RUECKKAUF),
        "Binance’s \$6.3 billion hack raises questions about crypto wallet safety" to setOf(Thema.RUECKRUF),
        "Some Polymarket user funds stolen by hackers; exchange referring matter to U.K. authorities" to setOf(Thema.RUECKRUF),
        "World Bank's Banga: Availability of capital 'not really an issue' as AI demand drives up borrowing costs" to setOf(Thema.KI),
        "Skydance outlines its \$6 billion in planned cost savings. Here's the breakdown" to emptySet<Thema>(),
        "Goldman CEO Solomon says AI financing remains strong despite market jitters" to emptySet<Thema>(),
        "Honda to invest \$3 billion in new vehicle assembly plant in Ohio" to emptySet<Thema>(),
        "Eli Lilly’s Foundayo obesity pill eases sleep apnea in late-stage trial, paving way for approval" to emptySet<Thema>(),
        "Restaurant chains refocus on food quality to bring back diners" to emptySet<Thema>(),
        "Poppi founders are going after PepsiCo's Gatorade with new beverage startup Jetty" to emptySet<Thema>(),
        "Using miles to fly for Thanksgiving? These are the most expensive and cheapest routes" to emptySet<Thema>(),
        "Cosm rides premium moviegoing wave with more immersive screenings and a wider library" to emptySet<Thema>(),
        "Where's the growth? Legacy automakers face a tougher 2027 as costs rise, market tightens" to emptySet<Thema>(),
        "Small retailers brace for 'huge hit' this holiday season as shipping costs spike" to emptySet<Thema>(),
        "Tesla to wind down Cybertruck production in Texas next year to ramp Cybercab, Musk says" to emptySet<Thema>(),
        "Marc Lore's Wonder confidentially files for IPO" to emptySet<Thema>(),
        "Expedia CEO says AI agents aren't changing booking habits — yet" to emptySet<Thema>(),
        "Consumers are spending on physical gifts even as travel tops wish lists, Visa survey finds" to emptySet<Thema>(),
        "CVS Health plans to split itself up. Here's why a breakup could make sense now" to emptySet<Thema>(),
        "New Flutter CEO bets on FanDuel promotions to win customers as profits suffer" to emptySet<Thema>(),
        "CVS to split off drugstore business in major breakup of healthcare giant; shares fall 10%" to emptySet<Thema>(),
        "Trump hammers Fox News, blaming network for GOP losses as ratings slide" to emptySet<Thema>(),
        "Wayfair sales jump 10% as furniture retailer says it's winning share of sluggish market" to emptySet<Thema>(),
        "JetBlue raises bag fees for third time in a year as fuel costs bite" to emptySet<Thema>(),
        "Tokenization platform Securitize gains 10% postmarket on Q3 results, move into asset-backed finance" to emptySet<Thema>(),
        "A massive trade in Nvidia just went off – here are the details" to emptySet<Thema>(),
        "Millions of dollars poured into prediction markets on election night, trading on Democratic control of Congress and Texas governor's race" to emptySet<Thema>(),
        "Top Wall Street analysts like these 3 dividend stocks for consistent income" to emptySet<Thema>(),
        "The AI ‘credit card’ is getting costlier as CoreWeave bond yield tops 16%" to emptySet<Thema>(),
        "CNBC's experts on whether the AI trade is ending and what investors should do next" to emptySet<Thema>(),
        "Democratic sweep puts crypto on defensive in Washington" to emptySet<Thema>(),
        "Fannie Mae, Freddie Mac rally as Ackman presses for conservatorship exit" to emptySet<Thema>(),
        "Santoli: AI bull market almost surely needs tech stocks to recover to keep advancing" to emptySet<Thema>(),
        "Top Wall Street analysts are bullish on these 3 stocks for the long haul" to emptySet<Thema>(),
    )

    private val ECHT_DE = listOf(
        "Untersuchung gegen Nvidia-Chef Huang wegen möglicher Spionin aus China" to setOf(Thema.ERMITTLUNG),
        "Mobilfunkunternehmen Vodafone plant Abbau von 2.300 Stellen" to setOf(Thema.PERSONAL),
        "Commerzbank-Übernahme soll Anfang 2027 durch sein" to setOf(Thema.UEBERNAHME),
        "Weitere Fälle von gehackten Kühlanlagen bekannt geworden" to setOf(Thema.RUECKRUF),
        "Investor Benko wegen Millionenbetrugs zu weiterer Haftstrafe verurteilt" to setOf(Thema.BILANZ),
        "Nvidia: Trump zufolge ermittelt FBI wegen Affäre von Nvidia-Chef Huang" to setOf(Thema.ERMITTLUNG),
        "Cyberangriff: „Beispielloses Ausmaß“ – Frankreich sieht Iran hinter Angriff auf Supermarkt-Kühlschränke" to setOf(Thema.RUECKRUF),
        "Today: Nvidia: Spionage-Skandal beim größten Chipkonzern der Welt – Chinas KI-Boom bröckelt" to setOf(Thema.KI),
        "Vodafone streicht jede fünfte Stelle in Deutschland und setzt vermehrt auf KI" to setOf(Thema.PERSONAL),
        "Online-Betrug: Jeder Neunte betroffen – ein Fall zeigt die neue Dimension" to setOf(Thema.BILANZ),
        "Vodafone: Konzern baut in Deutschland rund 2300 Stellen ab" to setOf(Thema.PERSONAL),
        "Unternehmen will sparen: Vodafone streicht 2300 Stellen in Deutschland" to setOf(Thema.PERSONAL),
        "Affäre mit möglicher Spionin: Nvidia leitet Untersuchungen gegen Chef Huang ein" to setOf(Thema.ERMITTLUNG),
        "Anders als unter Buffett: Berkshire Hathaway kauft mehr eigene Aktien zurück" to setOf(Thema.RUECKKAUF),
        "EU genehmigt MediaMarktSaturn-Übernahme durch JD.com" to setOf(Thema.UEBERNAHME),
        "Kahlschlag bei Schaeffler: Stellenabbau und Standortschließungen" to setOf(Thema.PERSONAL),
        "Starkes iPhone-Geschäft sorgt für Rekordumsatz bei Apple" to setOf(Thema.ZAHLEN),
        "Hochtief: Baukonzern profitiert von KI-Boom - Gewinn um 40 Prozent gesteigert" to setOf(Thema.KI),
        "Shein: Deutsche Umwelthilfe verklagt Fast-Fashion-Händler" to setOf(Thema.KLAGE),
        "Chemie: Evonik lehnt auch nach Austausch mit BASF Übernahme ab" to setOf(Thema.UEBERNAHME),
        "Technologie: Gea bestätigt Prognose und übertrifft Markterwartungen" to setOf(Thema.ZAHLEN),
        "Der Börsen-Tag: Sprung beim Gea-Auftragseingang" to setOf(Thema.AUFTRAG),
        "Der Börsen-Tag: Vodafone baut in Deutschland massiv Stellen ab" to setOf(Thema.PERSONAL),
        "Grenzkontrollsystem: EU verklagt Konsortium um IBM" to setOf(Thema.KLAGE),
        "Frankreich: Iran steckt hinter Cyberangriffen auf Kühlsysteme" to setOf(Thema.RUECKRUF),
        "Vodafone baut in Deutschland rund 2300 Stellen ab" to setOf(Thema.PERSONAL),
        "Russlands Statistikamt nach Cyberangriff mit Problemen, Verlustzahlen öffentlich" to setOf(Thema.RUECKRUF),
        "Brenner-Basistunnel: Cyberangriff auf Baufirma" to setOf(Thema.RUECKRUF),
        "Mindfactory informiert Kunden über Cyberangriff" to setOf(Thema.RUECKRUF),
        "TU München schaltet nach Cyberangriff Campussystem ab" to setOf(Thema.RUECKRUF),
        "Nach US-Zwischenwahl: Skepsis gegenüber KI-Boom löst heftigen Börsenabsturz aus" to setOf(Thema.KI),
        "Jugendschutz in Netz: Google, Meta und TikTok klagen gegen kalifornisches Gesetz" to setOf(Thema.KLAGE),
        "Fall Nvidia: Wie eine Affäre den KI-Boom gefährden kann" to setOf(Thema.KI),
        "Paris: Iran für Kühlanlagen-Cyberangriffe verantwortlich" to setOf(Thema.RUECKRUF),
        "Affäre von Nvidia-Chef Huang: Darum geht es beim Spionage-Verdacht" to emptySet<Thema>(),
        "Nvidia-Affäre lässt Aktie einbrechen" to emptySet<Thema>(),
        "Hoffen auf Ende des Iran-Krieges: Ölpreise deutlich gesunken" to emptySet<Thema>(),
        "Streiks und neue Gespräche in Bayerns Metall- und Elektroindustrie" to emptySet<Thema>(),
        "Viele Gebäude weiterhin ohne Elementarschadenversicherung" to emptySet<Thema>(),
        "Marktbericht: DAX erholt sich kräftig" to emptySet<Thema>(),
        "Welche Folgen die US-Midterms für die deutsche Wirtschaft haben" to emptySet<Thema>(),
        "Chipaktien an den internationalen Börsen im freien Fall" to emptySet<Thema>(),
        "Wie sich der Wahlsieg der Demokraten auf Big Tech auswirkt" to emptySet<Thema>(),
        "Ressourcenverbrauch vieler Rechenzentren nicht öffentlich einsehbar" to emptySet<Thema>(),
        "Tesla will Cybertruck-Produktion in Texas einstellen" to emptySet<Thema>(),
        "Millionen Euro verschwunden: Vucic-nahe Bank in Serbien geschlossen" to emptySet<Thema>(),
        "Industrieproduktion wächst überraschend - Autobranche legt zu" to emptySet<Thema>(),
        "Daimler Truck meldet deutlichen Gewinnsprung wegen USA-Geschäft" to emptySet<Thema>(),
        "Paraguay: Die Bankenkrise wird zur Staatskrise" to emptySet<Thema>(),
        "Supermärkte: Warum Kühlregale leicht zu hacken sind" to emptySet<Thema>(),
        "Bundestags-Votum: Mehr Vorteile für E-Autos im Stadtverkehr möglich" to emptySet<Thema>(),
        "Wirtschaft vor acht vom 09.11.2026" to emptySet<Thema>(),
        "Update Wirtschaft vom 09.11.2026" to emptySet<Thema>(),
        "Bund und Eigentümerfamilien einigen sich auf Kaufpreis bei KNDS" to emptySet<Thema>(),
        "Inflation im Euroraum steigt auf 4,0 Prozent" to emptySet<Thema>(),
        "Deutsche verbrauchen 2026 weniger Energie - das hat einen Grund" to emptySet<Thema>(),
        "Neuer EZB-Chef: Niederlande nominieren Ex-Zentralbanker Knot" to emptySet<Thema>(),
        "Airlines streichen Flüge: Verliert Deutschland den Anschluss?" to emptySet<Thema>(),
        "BMW erwirtschaftet noch weniger Gewinn" to emptySet<Thema>(),
        "Haushalt: Drei Milliarden Euro zusätzlich – Regierung muss Bundesagentur für Arbeit mit neuem Kredit helfen" to emptySet<Thema>(),
        "Nach den Midterms: Trump: Iran-Krieg ist militärisch „effektiv vorüber“" to emptySet<Thema>(),
        "+++ Iran-Krieg +++: Laut Trump ist Iran-Krieg militärisch „praktisch beendet“" to emptySet<Thema>(),
        "Rüstung: Bund zahlt offenbar 6,6 Milliarden Euro für KNDS-Einstieg" to emptySet<Thema>(),
        "Drohende Amtsenthebung: US-Repräsentantenhaus behandelt Amtsenthebung Hegseths" to emptySet<Thema>(),
    )

    @Test(timeout = 20000)
    fun echteSchlagzeilenEnglisch() = pruefeStichprobe(ECHT_EN, 40)

    @Test(timeout = 20000)
    fun echteSchlagzeilenDeutsch() = pruefeStichprobe(ECHT_DE, 40)

    private fun pruefeStichprobe(l: List<Pair<String, Set<Thema>>>, mindestens: Int) {
        assertTrue("Stichprobe zu klein", l.size >= mindestens)
        val falsch = l.filter { (t, erwartet) -> themen(t) != erwartet }
        assertTrue("Abweichungen: " + falsch.joinToString { it.first }, falsch.isEmpty())
    }

    @Test(timeout = 5000)
    fun analystenVerhaltenNichtSkeptisch() {
        // Kursziel 20 % über dem Kurs, Note 3,2: kein „Kursziel unter dem Kurs“
        val f = BullBear.Fakten(kurs = 100.0, kursziel = 120.0, analysten = 12, empfehlung = 3.2)
        val k = BullBear.auswerten(f, emptyList(), 0L).mapNotNull { it.kennzahl }
        assertTrue(BullBear.Kennzahl.ANALYSTEN_VERHALTEN in k)
        assertFalse(BullBear.Kennzahl.ANALYSTEN_SKEPTISCH in k)
        val unter = BullBear.auswerten(BullBear.Fakten(kurs = 100.0, kursziel = 90.0, analysten = 12, empfehlung = 3.2), emptyList(), 0L).mapNotNull { it.kennzahl }
        assertTrue(BullBear.Kennzahl.ANALYSTEN_SKEPTISCH in unter)
        assertFalse(BullBear.Kennzahl.ANALYSTEN_VERHALTEN in unter)
        val gut = BullBear.auswerten(BullBear.Fakten(kurs = 100.0, kursziel = 130.0, analysten = 12, empfehlung = 2.0), emptyList(), 0L).mapNotNull { it.kennzahl }
        assertTrue(BullBear.Kennzahl.ANALYSTEN_POSITIV in gut)
    }

    // ---- Kursspanne bei Krypto

    @Test(timeout = 5000)
    fun kursspanneKryptoMit365Tagen() {
        // Tagesrenditen abwechselnd ±3 %: Standardabweichung gleich in beiden Rechnungen, nur der Jahresfaktor unterscheidet sich
        val kurse = ArrayList<Double>()
        var k = 100.0
        for (i in 0..60) { kurse += k; k *= if (i % 2 == 0) 1.03 else 1 / 1.03 }
        val aktie = Kursspanne.sigmaHistorisch(kurse, tageProJahr = Kursspanne.tageProJahr(false))!!
        val krypto = Kursspanne.sigmaHistorisch(kurse, tageProJahr = Kursspanne.tageProJahr(true))!!
        assertEquals(sqrt(365.0 / 252.0), krypto / aktie, 1e-9)
        assertEquals(365.0, Kursspanne.tageProJahr(true), 0.0)
        assertEquals(252.0, Kursspanne.tageProJahr(false), 0.0)
    }

    // ---- Vorlagen

    @Test(timeout = 5000)
    fun vorlageHaltenMitObergrenzen() {
        val v = FinderVorlagen.vorlage("HALTEN")!!
        val peg = v.grenzen.single { it.feld == FinderVorlagen.Feld.PEG }
        val beta = v.grenzen.single { it.feld == FinderVorlagen.Feld.BETA }
        assertNull(peg.min); assertEquals(1.0, peg.max!!, 0.0)
        assertNull(beta.min); assertEquals(1.5, beta.max!!, 0.0)
        assertTrue(peg.passt(0.8)); assertFalse(peg.passt(1.4))
        assertTrue(beta.passt(1.2)); assertFalse(beta.passt(1.9))
    }

    @Test(timeout = 5000)
    fun vorlagenSortierungPasstZumNamen() {
        // Bewertungsvorlagen: niedrigstes KGV zuerst
        for (id in listOf("HALTEN", "DIVIDENDENWACHSTUM", "NIEDRIGES_KGV")) {
            val v = FinderVorlagen.vorlage(id)!!
            assertTrue("$id aufsteigend", v.aufsteigend)
            assertTrue(v.sortierung == FinderVorlagen.Feld.KGV || v.sortierung == FinderVorlagen.Feld.KGV_ERWARTET)
        }
        // Lücken: größte Lücke zuerst, bei Lücke nach unten die negativste
        assertFalse(FinderVorlagen.vorlage("LUECKE_AUF")!!.aufsteigend)
        assertTrue(FinderVorlagen.vorlage("LUECKE_AB")!!.aufsteigend)
        assertEquals(FinderVorlagen.Feld.REL_VOLUMEN, FinderVorlagen.vorlage("REL_VOLUMEN")!!.sortierung)
        assertFalse(FinderVorlagen.vorlage("REL_VOLUMEN")!!.aufsteigend)
        // Überverkauft: der niedrigste RSI zuerst
        assertEquals(FinderVorlagen.Feld.RSI, FinderVorlagen.vorlage("UMKEHR")!!.sortierung)
        assertTrue(FinderVorlagen.vorlage("UMKEHR")!!.aufsteigend)
    }

    // ---- Aktienfinder: Nachladen der Einzelabrufe darf die Punkte nicht verschieben

    private fun aktie(i: Int, mitDetails: Boolean) = Aktienfinder.Daten(
        symbol = "S$i", name = "S$i", branche = if (i % 2 == 0) "TECH" else "AUTO",
        kgv = 8.0 + i, kgvErwartet = 7.0 + i, kbv = 1.0 + i / 5.0, gewinn = 2.0, gewinnErwartet = 2.4, buchwert = 10.0, boersenwert = 1e9 * (i + 1),
        note = 2.0 + (i % 5) / 4.0, jahr = i * 3.0 - 20.0, linie200 = i - 10.0,
        kuv = if (mitDetails) 1.0 + i else null, evEbitda = if (mitDetails) 6.0 + i else null,
        marge = if (mitDetails) 5.0 + i else null, verschuldung = if (mitDetails) 0.3 * (i % 4) else null,
        kursziel = if (mitDetails) 10.0 else null, mitDetails = mitDetails,
    )

    @Test(timeout = 10000)
    fun nachladenVerschiebtDiePunkteNicht() {
        val n = 40
        val vorher = (0 until n).map { aktie(it, false) }
        // Details für die ersten sechs (15 %): zählen noch nicht
        val nachher = (0 until n).map { aktie(it, it < 6) }
        assertTrue(Aktienfinder.zaehlende(nachher).isEmpty())
        val a = Aktienfinder.bewerten(vorher).associateBy { it.symbol }
        val b = Aktienfinder.bewerten(nachher).associateBy { it.symbol }
        for (i in 0 until 6) {
            val x = a.getValue("S$i"); val y = b.getValue("S$i")
            // gleiche Zahlen, gleiche Punkte
            assertEquals("Gesamt S$i", x.gesamt, y.gesamt, 1e-9)
            assertEquals("Bewertung S$i", x.bereiche[Aktienfinder.Bereich.BEWERTUNG]!!, y.bereiche[Aktienfinder.Bereich.BEWERTUNG]!!, 1e-9)
            assertEquals("Abdeckung S$i", x.abdeckung, y.abdeckung, 1e-9)
            assertEquals(x.einordnung, y.einordnung)
            // die neuen Kennzahlen stehen trotzdem in der Aufklappzeile
            assertTrue(Aktienfinder.Kennzahl.KUV in y.kennzahlen)
        }
        for (i in 6 until n) assertEquals(a.getValue("S$i").gesamt, b.getValue("S$i").gesamt, 1e-9)
    }

    @Test(timeout = 10000)
    fun einzelabrufZaehltAbHaelfteDerWerte() {
        val n = 40
        val ganz = (0 until n).map { aktie(it, it < 20) }
        assertTrue(Aktienfinder.zaehlende(ganz).containsAll(Aktienfinder.NUR_EINZELABRUF - Aktienfinder.Kennzahl.SCHULDEN))
        val knapp = (0 until n).map { aktie(it, it < 19) }
        assertTrue(Aktienfinder.zaehlende(knapp).isEmpty())
        // bei Abdeckung zählt die Kennzahl und der Wert hat sie im Bereich
        val e = Aktienfinder.bewerten(ganz).first { it.symbol == "S3" }
        assertNotNull(e.kennzahlen[Aktienfinder.Kennzahl.KUV])
    }
}
