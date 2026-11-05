package de.troetenrepublik.app.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.troetenrepublik.app.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.92 ohne Fenster: Regierung mit Bildern und Vermoegen des Praesidenten, Seite einer Abgeordneten mit
// Partei, Ausschuessen und PDFs, Rangliste mit Reitern, Filter der Insiderliste und auffaellige Wetten erfolgreicher
// Polymarket-Haendler. Daten echt aus dem Netz; Bilder und Ablauf (fassung592.txt) unter build/testbilder.
@OptIn(ExperimentalTestApi::class)
class Fassung592OberflaecheTest : RundgangBasis("fassung592.txt") {

    // Kachel direkt oder hinter „Mehr“.
    private fun androidx.compose.ui.test.DesktopComposeUiTest.kachel(titel: String) {
        val direkt = da(titel)
        val kachel = if (direkt) titel else S.more
        rolleZu(kachel, S.investments)
        kachelZeigen(kachel)
        if (!direkt) { tippe(S.more); assertTrue(titel, warteText(titel, ms = 5_000)) }
        tippe(titel)
        zeit(2000)
    }

    @Test
    fun copytradingUndWetten() = runDesktopComposeUiTest(width = 840, height = 1800) {
        val c = starteMitDepot("fassung592")
        kachel(S.copyTitle)
        assertTrue("Copytrading", warteText(S.copyLatestTrades, ms = 60_000))

        // Regierung: Praesident mit Vermoegen, weitere Mitglieder mit Bild
        tippe(S.copyGovernment)
        warte(90_000) { da("Scott Bessent") }
        zeit(5000)
        notiere("Vermoegen in der Liste: " + da(S.rgVermoegen("").trim(), teil = true))
        foto("p5-1-regierung")
        rolleZu("Scott Bessent", S.copyGovernment)
        zeit(3000)
        foto("p5-1b-regierung-unten")

        // Abgeordnete: Partei, Staat, Ausschuesse, Meldungen als PDF
        tippe(S.copyCongress)
        zeit(1500)
        onAllNodes(hasSetTextAction())[0].performTextInput("Gottheimer")
        zeit(1500)
        if (warte(30_000) { da("Josh Gottheimer") }) {
            tippe("Josh Gottheimer")
            warte(60_000) { da(S.copyMostTraded) }
            zeit(4000)
            notiere("Partei und Ausschuesse: " + da(S.ausschuesse) + " " + da("Demokraten", teil = true))
            foto("p5-2-gottheimer")
            rolleZu(S.copyAllTrades, "Josh Gottheimer")
            zeit(1500)
            notiere("PDF-Link: " + da(S.pdfKurz))
            foto("p5-2b-gottheimer-meldungen")
            escape()
        }
        onAllNodes(hasSetTextAction())[0].performTextClearance()
        zeit(1000)

        // Rangliste: Reiter, gespeicherter Stand
        rolleZu(S.rgTitel, S.copyLatestTrades)
        tippe(S.rgTitel)
        val fertig = warte(600_000) { da(" Pkt.", teil = true) }
        notiere("Rangliste gerechnet: $fertig")
        zeit(3000)
        foto("p5-3-rangliste")
        tippe(S.rgOrdnung("VERMOEGEN"))
        zeit(2500)
        foto("p5-3b-rangliste-vermoegen")
        escape()

        // Insider: Filterblatt
        tippe(S.copyExecutives)
        if (warte(60_000) { da(S.filterTitle) }) {
            tippe(S.filterTitle)
            zeit(1500)
            foto("p5-4-insider-filter")
            escape()
        }
        escape()

        // Prognosemaerkte: auffaellige Wetten erfolgreicher Haendler
        kachel(S.pmTitle)
        tippe(S.pmGross)
        zeit(1500)
        tippe(S.pmProfis)
        val profis = warte(240_000) { !da(S.pmProfisLaedt) }
        notiere("Profis geladen: $profis, keine: " + da(S.pmProfisKeine))
        zeit(4000)
        foto("p5-5-profis")
        tippe(S.pmGrossGroesste)
        warte(60_000) { !da(S.pmLoading) }
        zeit(3000)
        foto("p5-5b-grosse-wetten")
        notiere("fertig")
        c.beenden()
    }
}
