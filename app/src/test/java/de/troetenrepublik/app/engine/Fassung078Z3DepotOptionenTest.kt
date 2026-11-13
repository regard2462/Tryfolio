package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Bewertungsblick.Ampel
import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import de.troetenrepublik.app.engine.Optionsrechner.Art
import de.troetenrepublik.app.engine.Optionsrechner.Leg
import de.troetenrepublik.app.engine.Optionsrechner.Vorlage
import de.troetenrepublik.app.engine.OptionsrechnerPlan.Erwartung
import de.troetenrepublik.app.engine.OptionsrechnerPlan.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Schutz-Vorlagen im Optionsrechner, Schwellen im Depot-Check, Schwelle beim Umschichten, Rueckgaenge der Performance
// und Stabilitaet der Bilanz.
class Fassung078Z3DepotOptionenTest {
    private val tag = 86_400L
    // 15.01.2027, 12:00 UTC
    private val tagNull = 20_833L
    private val jetztS = tagNull * tag + 43_200L

    private fun verfallIn(tage: Int) = (tagNull + tage) * tag

    private fun kette(tage: Int): List<Kontrakt> = listOf(90.0, 95.0, 100.0, 105.0, 110.0).flatMap { k ->
        listOf(true, false).map { call -> Kontrakt(call, k, verfallIn(tage), 3.0, 2.9, 3.1, 10, 10, 0.40) }
    }

    private val ketten = kette(30) + kette(120) + kette(200) + kette(400)

    // ------------------------------------------------------------ Optionsrechner

    @Test
    fun collarHaeltAktieKauftPutVerkauftCall() {
        val legs = Optionsrechner.vorlage(Vorlage.COLLAR, 100.0, ketten, verfallIn(30))!!
        assertEquals(listOf(Leg(Art.AKTIE, 1, 0.0), Leg(Art.PUT, 1, 95.0), Leg(Art.CALL, -1, 105.0)), legs)
        val beine = Optionsrechner.beine(100.0, ketten, verfallIn(30), legs, jetztS)!!
        val (gewinn, verlust) = Optionsrechner.grenzen(beine)
        // Beide Optionen kosten gleich viel: Gewinn bis zum Call, Verlust bis zum Put, je 5 je Aktie
        assertEquals(500.0, gewinn.wert!!, 1e-6)
        assertEquals(-500.0, verlust.wert!!, 1e-6)
        assertEquals(listOf(100.0), Optionsrechner.breakEven(beine).map { Math.round(it * 100) / 100.0 })
        assertEquals(Erwartung.ABSICHERN, OptionsrechnerPlan.erwartung(Vorlage.COLLAR))
    }

    @Test
    fun schutzPutBegrenztDenVerlust() {
        val legs = Optionsrechner.vorlage(Vorlage.SCHUTZ_PUT, 100.0, ketten, verfallIn(200))!!
        assertEquals(listOf(Leg(Art.AKTIE, 1, 0.0), Leg(Art.PUT, 1, 95.0)), legs)
        val beine = Optionsrechner.beine(100.0, ketten, verfallIn(200), legs, jetztS)!!
        val (gewinn, verlust) = Optionsrechner.grenzen(beine)
        assertNull(gewinn.wert)
        // 5 bis zum Basispreis und 3 fuer den Put, je Aktie
        assertEquals(-800.0, verlust.wert!!, 1e-6)
        assertEquals(Erwartung.ABSICHERN, OptionsrechnerPlan.erwartung(Vorlage.SCHUTZ_PUT))
        assertEquals(listOf(Vorlage.COLLAR, Vorlage.SCHUTZ_PUT), OptionsrechnerPlan.vorlagen(Erwartung.ABSICHERN))
    }

    @Test
    fun schutzPutSpringtAufEinHalbesJahr() {
        val termine = listOf(30, 120, 200, 400).map { verfallIn(it) }
        // Zu kurzer Verfall: erster Termin mit mindestens 182 Tagen
        assertEquals(verfallIn(200), Optionsrechner.verfallFuer(Vorlage.SCHUTZ_PUT, termine, verfallIn(30), jetztS))
        // Lang genug: bleibt
        assertEquals(verfallIn(400), Optionsrechner.verfallFuer(Vorlage.SCHUTZ_PUT, termine, verfallIn(400), jetztS))
        // Kein Termin lang genug: der spaeteste
        assertEquals(verfallIn(120), Optionsrechner.verfallFuer(Vorlage.SCHUTZ_PUT, termine.take(2), verfallIn(30), jetztS))
        // Andere Vorlagen haben keine Vorgabe
        assertEquals(verfallIn(30), Optionsrechner.verfallFuer(Vorlage.COLLAR, termine, verfallIn(30), jetztS))
        assertEquals(0, Optionsrechner.mindestTage(Vorlage.LONG_PUT))
        assertEquals(182, Optionsrechner.mindestTage(Vorlage.SCHUTZ_PUT))
    }

    @Test
    fun beideVorlagenGehenInEinemZugInsDepot() {
        val jetztMs = jetztS * 1000
        for ((v, tage) in listOf(Vorlage.COLLAR to 30, Vorlage.SCHUTZ_PUT to 200)) {
            val legs = Optionsrechner.vorlage(v, 100.0, ketten, verfallIn(tage))!!
            val beine = Optionsrechner.beine(100.0, ketten, verfallIn(tage), legs, jetztS)
            val plan = OptionsrechnerPlan.plan(beine, verfallIn(tage), jetztMs, waehrungPasst = true)
            assertTrue(v.name, plan is Plan.Kaufbar)
            val teile = (plan as Plan.Kaufbar).teile
            // Je Kontrakt 100 Aktien
            assertEquals(100, teile.first { it.art == Art.AKTIE }.stueck)
            assertEquals(100, teile.first { it.art == Art.PUT }.stueck)
            if (v == Vorlage.COLLAR) {
                assertEquals(-100, teile.first { it.art == Art.CALL }.stueck)
                assertTrue(OptionsrechnerPlan.gruppiert(teile))
            } else assertFalse(OptionsrechnerPlan.gruppiert(teile))
        }
    }

    // ------------------------------------------------------------ Depot-Check

    private fun aktien(vararg werte: Double) = werte.mapIndexed { i, w -> DepotCheck.Wert("A$i", w, DepotCheck.Art.AKTIE, "EUR", "B${i % 7}", 0.0) }

    private fun befunde(werte: List<DepotCheck.Wert>) = DepotCheck.pruefen(werte, 0.0, 0.0, 0.0, 0, 0, mitKonto = false).associateBy { it.regel }

    @Test
    fun einzelpositionUeberFuenfUndZehnProzent() {
        assertEquals(DepotCheck.Stufe.GUT, DepotCheck.positionStufe(0.05))
        assertEquals(DepotCheck.Stufe.HINWEIS, DepotCheck.positionStufe(0.051))
        assertEquals(DepotCheck.Stufe.HINWEIS, DepotCheck.positionStufe(0.10))
        assertEquals(DepotCheck.Stufe.ACHTUNG, DepotCheck.positionStufe(0.101))
        assertEquals(DepotCheck.Stufe.KRITISCH, DepotCheck.positionStufe(0.21))
        // 25 Werte zu je 4 %
        val gleich = befunde(aktien(*DoubleArray(25) { 4.0 }))
        assertEquals(DepotCheck.Stufe.GUT, gleich.getValue(DepotCheck.Regel.GROESSTE).stufe)
        assertEquals(0, gleich.getValue(DepotCheck.Regel.GROESSTE).anzahl)
        // Einer mit 7 %, einer mit 6 %, der Rest je 3 %
        val hinweis = befunde(aktien(7.0, 6.0, *DoubleArray(29) { 3.0 }))
        assertEquals(DepotCheck.Stufe.HINWEIS, hinweis.getValue(DepotCheck.Regel.GROESSTE).stufe)
        assertEquals("A0", hinweis.getValue(DepotCheck.Regel.GROESSTE).name)
        assertEquals(2, hinweis.getValue(DepotCheck.Regel.GROESSTE).anzahl)
        // Einer mit 12 %
        val deutlich = befunde(aktien(12.0, *DoubleArray(22) { 4.0 }))
        assertEquals(DepotCheck.Stufe.ACHTUNG, deutlich.getValue(DepotCheck.Regel.GROESSTE).stufe)
    }

    @Test
    fun wenigerAlsZwanzigEinzelaktien() {
        assertEquals(DepotCheck.Stufe.HINWEIS, befunde(aktien(*DoubleArray(19) { 5.0 })).getValue(DepotCheck.Regel.ANZAHL).stufe)
        assertEquals(DepotCheck.Stufe.GUT, befunde(aktien(*DoubleArray(20) { 5.0 })).getValue(DepotCheck.Regel.ANZAHL).stufe)
        assertEquals(DepotCheck.Stufe.ACHTUNG, befunde(aktien(*DoubleArray(14) { 5.0 })).getValue(DepotCheck.Regel.ANZAHL).stufe)
        assertEquals(DepotCheck.Stufe.KRITISCH, befunde(aktien(*DoubleArray(7) { 5.0 })).getValue(DepotCheck.Regel.ANZAHL).stufe)
        // Zur Haelfte in ETFs: gestreut, kein Hinweis
        val mitEtf = aktien(*DoubleArray(10) { 5.0 }) + DepotCheck.Wert("Welt", 50.0, DepotCheck.Art.ETF, "EUR", null, 0.0)
        assertEquals(DepotCheck.Stufe.GUT, befunde(mitEtf).getValue(DepotCheck.Regel.ANZAHL).stufe)
        assertEquals(20, DepotCheck.ANZAHL_HINWEIS)
        assertEquals(0.05, DepotCheck.POSITION_HINWEIS, 0.0)
        assertEquals(0.10, DepotCheck.POSITION_DEUTLICH, 0.0)
    }

    // ------------------------------------------------------------ Umschichten

    @Test
    fun schwelleZehnPunkteOderHaelfteDesZiels() {
        assertEquals(0.10, Umschichtschwelle.schwelle(0.50), 1e-12)
        assertEquals(0.10, Umschichtschwelle.schwelle(0.25), 1e-12)
        // Kleine Bausteine: die Haelfte des Ziels, hoechstens 10 Punkte
        assertEquals(0.10, Umschichtschwelle.schwelle(0.20), 1e-12)
        assertEquals(0.05, Umschichtschwelle.schwelle(0.10), 1e-12)
        assertEquals(0.0, Umschichtschwelle.schwelle(0.0), 1e-12)
        assertTrue(Umschichtschwelle.imRahmen(0.58, 0.50))
        assertFalse(Umschichtschwelle.imRahmen(0.61, 0.50))
        assertTrue(Umschichtschwelle.imRahmen(0.14, 0.10))
        assertFalse(Umschichtschwelle.imRahmen(0.16, 0.10))
        assertFalse(Umschichtschwelle.imRahmen(0.04, 0.10))
    }

    @Test
    fun umschichtenErstAusserhalbDerSchwelle() {
        val werte = listOf("A" to 58.0, "B" to 42.0)
        val gewichte = listOf(50.0, 50.0)
        val trades = PortfolioTools.rebalance(werte, gewichte, 0.0, true)
        val innen = Umschichtschwelle.anteile(werte.map { it.second }, gewichte)
        assertFalse(Umschichtschwelle.noetig(innen))
        assertEquals(listOf(true, true), Umschichtschwelle.imRahmen(innen))
        assertTrue(Umschichtschwelle.orders(trades, false).all { it.amount == 0.0 })

        val weit = listOf("A" to 62.0, "B" to 30.0, "C" to 8.0)
        val ziel = listOf(50.0, 40.0, 10.0)
        val bausteine = Umschichtschwelle.anteile(weit.map { it.second }, ziel)
        // A liegt 12 Punkte ueber dem Ziel, B genau 10 darunter, C zwei Punkte unter einem kleinen Ziel
        assertEquals(listOf(false, true, true), Umschichtschwelle.imRahmen(bausteine))
        assertTrue(Umschichtschwelle.noetig(bausteine))
        val alle = PortfolioTools.rebalance(weit, ziel, 0.0, true)
        // Dann wird das ganze Depot aufs Ziel gebracht
        assertEquals(alle, Umschichtschwelle.orders(alle, true))
        assertEquals(-12.0, alle[0].amount, 1e-9)
        // Listen, die nicht zusammenpassen
        assertTrue(Umschichtschwelle.anteile(listOf(1.0), ziel).isEmpty())
        assertFalse(Umschichtschwelle.noetig(emptyList()))
    }

    // ------------------------------------------------------------ Performance

    @Test
    fun zeitUnterHochUndMittlererRueckgang() {
        val reihe = listOf(0L to 1.0, 1L to 1.1, 2L to 0.99, 3L to 1.1, 4L to 1.2)
        val r = Performanceanalyse.rueckgaenge(reihe)!!
        // Ein Abschnitt von vier liegt 10 % unter dem Hoch
        assertEquals(0.25, r.anteilUnterHoch, 1e-9)
        assertEquals(0.025, r.mittlerer, 1e-9)
        // Abschnitte zaehlen mit ihrer Dauer
        val lang = Performanceanalyse.rueckgaenge(listOf(0L to 1.0, 10L to 0.8, 40L to 0.9, 50L to 1.0))!!
        assertEquals(0.8, lang.anteilUnterHoch, 1e-9)
        assertEquals((0.2 * 10 + 0.1 * 30) / 50, lang.mittlerer, 1e-9)
        // Nur steigend
        val hoch = Performanceanalyse.rueckgaenge(listOf(0L to 1.0, 1L to 1.1, 2L to 1.2))!!
        assertEquals(0.0, hoch.anteilUnterHoch, 0.0)
        assertEquals(0.0, hoch.mittlerer, 0.0)
        assertNull(Performanceanalyse.rueckgaenge(listOf(0L to 1.0)))
        assertNull(Performanceanalyse.rueckgaenge(emptyList()))
    }

    // ------------------------------------------------------------ Stabilitaet

    private fun zeile(l: List<Stabilitaet.Zeile>, k: Stabilitaet.Kennzahl) = l.first { it.kennzahl == k }

    @Test
    fun stabilitaetMitSchwellen() {
        // Nettoschulden 60
        val gut = Stabilitaet.berechne(100.0, 40.0, 300.0, 40.0, 30.0)
        assertEquals(20.0, zeile(gut, Stabilitaet.Kennzahl.GEARING).wert, 1e-9)
        assertEquals(Ampel.GRUEN, zeile(gut, Stabilitaet.Kennzahl.GEARING).ampel)
        assertEquals(1.5, zeile(gut, Stabilitaet.Kennzahl.TILGUNG).wert, 1e-9)
        assertEquals(Ampel.GRUEN, zeile(gut, Stabilitaet.Kennzahl.TILGUNG).ampel)
        assertEquals(2.0, zeile(gut, Stabilitaet.Kennzahl.EBITDA).wert, 1e-9)
        assertEquals(Ampel.GRUEN, zeile(gut, Stabilitaet.Kennzahl.EBITDA).ampel)

        val mittel = Stabilitaet.berechne(100.0, 40.0, 100.0, 20.0, 20.0)
        assertEquals(Ampel.GELB, zeile(mittel, Stabilitaet.Kennzahl.GEARING).ampel)
        assertEquals(Ampel.GELB, zeile(mittel, Stabilitaet.Kennzahl.TILGUNG).ampel)
        // Ab 3 kritisch
        assertEquals(3.0, zeile(mittel, Stabilitaet.Kennzahl.EBITDA).wert, 1e-9)
        assertEquals(Ampel.ROT, zeile(mittel, Stabilitaet.Kennzahl.EBITDA).ampel)

        val schwach = Stabilitaet.berechne(100.0, 40.0, 50.0, 12.0, 10.0)
        assertEquals(Ampel.ROT, zeile(schwach, Stabilitaet.Kennzahl.GEARING).ampel)
        // Ab 5 Jahren kritisch
        assertEquals(5.0, zeile(schwach, Stabilitaet.Kennzahl.TILGUNG).wert, 1e-9)
        assertEquals(Ampel.ROT, zeile(schwach, Stabilitaet.Kennzahl.TILGUNG).ampel)
        assertEquals(Ampel.GELB, Stabilitaet.gearingAmpel(100.0))
        assertEquals(Ampel.ROT, Stabilitaet.gearingAmpel(100.1))
        assertEquals(Ampel.GELB, Stabilitaet.tilgungAmpel(2.0))
    }

    @Test
    fun stabilitaetOhneNettoschuldenUndMitLuecken() {
        // Kasse deckt die Schulden
        val frei = Stabilitaet.berechne(100.0, 150.0, 300.0, -5.0, 0.0)
        assertEquals(3, frei.size)
        assertTrue(frei.all { it.lage == Stabilitaet.Lage.KEINE_NETTOSCHULDEN && it.ampel == Ampel.GRUEN })
        // Freier Cashflow negativ, EBITDA fehlt, Eigenkapital negativ
        val luecke = Stabilitaet.berechne(100.0, 0.0, -20.0, -5.0, 0.0)
        assertEquals(listOf(Stabilitaet.Kennzahl.GEARING, Stabilitaet.Kennzahl.TILGUNG), luecke.map { it.kennzahl })
        assertTrue(luecke.all { it.lage == Stabilitaet.Lage.NENNER_NEGATIV && it.ampel == Ampel.ROT })
        // Ohne Schuldenangabe keine Zeile
        assertTrue(Stabilitaet.berechne(0.0, 50.0, 300.0, 40.0, 30.0).isEmpty())
        assertTrue(Stabilitaet.berechne(null, null, null, null, null).isEmpty())
        assertEquals(60.0, Stabilitaet.nettoschulden(100.0, 40.0)!!, 0.0)
    }
}
