package de.troetenrepublik.app.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.Plattform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Analyse auf der Wertpapierseite ohne Fenster (5.92): Bewertung und Kennzahlen mit drei Ampeln, Chancen und Risiken mit
// Analysten und Stimmung, das Blatt der Analysten mit Trefferbilanz, Ereignisse und News. Kurse und Urteile echt von
// Yahoo; Bilder und Ablauf (wertpapieranalyse.txt) unter build/testbilder.
@OptIn(ExperimentalTestApi::class)
class WertpapierAnalyseOberflaecheTest : RundgangBasis("wertpapieranalyse.txt") {

    private fun DesktopComposeUiTest.starte(): DesktopContainer {
        mainClock.autoAdvance = false
        val ordner = File(System.getProperty("troete.daten") ?: "build/testdaten", "wertpapieranalyse-" + System.currentTimeMillis())
        Plattform.init(ordner)
        val c = DesktopContainer(ordner)
        I18n.lang = Lang.DE
        setContent { CompositionLocalProvider(LocalDensity provides Density(2f)) { DesktopApp(c) } }
        runBlocking { c.settings.update { it.copy(onboarded = true, guidePending = false, ownerName = "Alex", language = Lang.DE, languageAuto = false) } }
        c.state.reset(10_000.0, announce = false)
        c.state.start()
        c.state.resume()
        assertTrue("Depot", warteText(S.investments, ms = 30_000))
        zeit(1500)
        return c
    }

    @Test
    fun analyse() = runDesktopComposeUiTest(width = 840, height = 1800) {
        val c = starte()
        c.state.springeZu(WidgetZiel.instrument("AAPL"))
        assertTrue("Seite", warteText(S.buy, ms = 30_000))
        // Profil, Jahresabschluesse, Branchen-KGV und Optionen kommen aus dem Netz
        zeit(15_000)
        rolleZu(S.bkTitel, anker = S.information)
        assertTrue("Bewertung und Kennzahlen", warteText(S.bkTitel, ms = 10_000))
        assertTrue("Bilanzampel", warteText(S.balance, ms = 20_000))
        foto("p2-1-bewertung")
        notiere("Bewertungszeile: ${da(S.bkBewertung)}, Wachstum: ${da(S.bkWachstum)}")

        // Bewertung im Blatt
        tippe(S.bkBewertung)
        assertTrue("Blatt Bewertung", warteText(S.bkFairSelbst, ms = 10_000))
        foto("p2-1b-bewertung-blatt")
        escape()

        // Alle Kennzahlen in Gruppen
        rolleZu(S.bwAlleKennzahlen, anker = S.bkTitel)
        tippe(S.bwAlleKennzahlen)
        assertTrue("Blatt mit allen Kennzahlen", warteText(S.bkGruppe("RENTABILITAET"), ms = 10_000))
        foto("p2-5-alle-kennzahlen")
        escape()

        rolleZu(S.bwFuerUndWider, anker = S.bkTitel)
        assertTrue("Chancen und Risiken", warteText(S.bwFuerUndWider, ms = 10_000))
        zeit(3000)
        foto("p2-2-chancen")

        // Analysten im Blatt: Verteilung, bereinigte Einschaetzung, Haeuser
        // Oben in die Karte tippen, die Mitte waere das Kursziel (Waehrungswechsel)
        onAllNodesWithText(S.analysts)[0].performTouchInput { click(Offset(120f, 40f)) }
        zeit(1000)
        assertTrue("Analysten geladen", warteText(S.anVerteilung, ms = 60_000))
        warteText(S.anBereinigt, ms = 60_000)
        foto("p2-3-analysten")
        rolleZu(S.anHaeuser, anker = S.anVerteilung)
        foto("p2-3b-analysten-haeuser")
        escape()

        rolleZu(S.news, anker = S.bwFuerUndWider)
        foto("p2-4-ereignisse-news")

        // Wie tief kann es fallen
        rolleZu(S.rrTitel, anker = S.news)
        tippe(S.rrTitel)
        warteText(S.rrErholung, ms = 30_000)
        foto("p2-6-wie-tief")
    }
}
