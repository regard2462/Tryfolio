package de.troetenrepublik.app.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Test

// Oberer Teil der Wertpapierseite ohne Fenster (5.92): Glocke und Notiz in der Kopfzeile, Chart ohne Chips darunter,
// Kursspanne als Zeile, Position mit Haltedauer, alle zehn Indikatoren mit Trefferbilanz, Hebelprodukte als Karte und
// der erweiterte Chart mit Einfuehrung. Kurse echt von Yahoo; Bilder und Ablauf (wertpapierkopf.txt) unter
// build/testbilder.
@OptIn(ExperimentalTestApi::class)
class WertpapierKopfOberflaecheTest : RundgangBasis("wertpapierkopf.txt") {

    // Rollt die Liste mit dem Anker um px Pixel weiter.
    private fun DesktopComposeUiTest.rolle(px: Float, anker: String) {
        val liste = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyDescendant(hasText(anker)))
            .fetchSemanticsNodes().last().id
        onAllNodes(SemanticsMatcher("Liste") { it.id == liste })[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, px) }
        zeit(800)
    }

    @Test
    fun wertpapierKopf() = runDesktopComposeUiTest(width = 840, height = 1800) {
        val c = starteMitDepot("wertpapierkopf")
        c.state.notizSetzen("AAPL", "Gekauft wegen der Dienste, nicht wegen der Geräte.")
        c.state.springeZu(WidgetZiel.instrument("AAPL"))
        assertTrue("Seite", warteText(S.sell, ms = 30_000))
        zeit(12_000)
        // Kopfzeile: Notiz gefuellt, Glocke leer; keine Chips mehr unter dem Chart
        assertTrue("Notiz-Symbol", onAllNodesWithContentDescription(S.notiz).fetchSemanticsNodes().isNotEmpty())
        assertTrue("Glocke", onAllNodesWithContentDescription(S.bwPreisalarm).fetchSemanticsNodes().isNotEmpty())
        assertTrue("keine Chips", !da(S.kcSpanne) && !da(S.bwPreisalarm))
        assertTrue("Haltedauer", da(S.p1SeitKauf))
        assertTrue("Sparplan bei der Position", da(S.savingsPlan))
        notiere("Kursspanne als Zeile: " + da("In 30 Tagen", teil = true))
        foto("40-kopf-position")

        // Alle zehn Indikatoren: drei einschalten, Bilanz abwarten
        tippe("1J")
        zeit(4000)
        tippe(S.indButton)
        tippe(S.indName("sma200"))
        tippe(S.indName("rsi"))
        tippe(S.indName("macd"))
        assertTrue("Bilanz", warte(40_000) { da("Aussagekraft hier", teil = true) || da(S.p1BilanzWenig) })
        zeit(1500)
        foto("41-indikatoren-chart")
        rolle(700f, S.indButton)
        foto("41-indikatoren-bilanz")

        // Beschreibung: Pfeil am Ende des gekuerzten Texts statt an der Ueberschrift
        rolleZu(S.information, anker = S.indButton)
        foto("44-informationen")

        // Derivate: Long/Short-Balken, ohne Optionsdaten die Pillen als Rueckfall
        rolleZu(S.derivatives, anker = S.indButton)
        assertTrue("Derivate", da(S.long) || da(S.p1HebelLong))
        notiere("Emittent sichtbar: " + da(S.issuer))
        foto("42-hebelprodukte")
    }

    @Test
    fun erweiterterChart() = runDesktopComposeUiTest(width = 840, height = 1800) {
        val c = starteMitDepot("profichart")
        c.state.springeZu(WidgetZiel.instrument("AAPL"))
        assertTrue("Seite", warteText(S.sell, ms = 30_000))
        zeit(6000)
        tippe(S.chartProfi)
        assertTrue("Erweiterter Chart", warteText(S.pcDarstellung, ms = 20_000))
        zeit(8000)
        assertTrue("Gruppen", da(S.pcEinblenden) && da(S.pcZeichnen))
        foto("43-erweiterter-chart")
    }
}
