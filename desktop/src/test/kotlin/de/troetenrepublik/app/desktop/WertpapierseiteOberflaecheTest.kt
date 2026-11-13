package de.troetenrepublik.app.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
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
import kotlin.time.Duration.Companion.minutes

// Wertpapierseite ohne Fenster (5.91, Kopf seit 5.92): sechs Kennzahlen mit Blatt, eingeklappter
// Hintergrund, dessen Karten erst nach dem Aufklappen entstehen. Misst dabei die Laenge der Seite bis zum Hintergrund
// bei Apple und SAP. Kurse echt von Yahoo; Bilder und Ablauf (wertpapierseite.txt) unter build/testbilder.
@OptIn(ExperimentalTestApi::class)
class WertpapierseiteOberflaecheTest : RundgangBasis("wertpapierseite.txt") {

    private fun DesktopComposeUiTest.starte(): DesktopContainer {
        mainClock.autoAdvance = false
        val ordner = File(System.getProperty("troete.daten") ?: "build/testdaten", "wertpapierseite-" + System.currentTimeMillis())
        Plattform.init(ordner)
        val c = DesktopContainer(ordner)
        I18n.lang = Lang.DE
        setContent { CompositionLocalProvider(LocalDensity provides Density(2f)) { DesktopApp(c) } }
        runBlocking { c.settings.update { it.copy(onboarded = true, guidePending = false, ok13107 = true, ownerName = "Alex", language = Lang.DE, languageAuto = false) } }
        c.state.reset(10_000.0, announce = false)
        c.state.start()
        c.state.resume()
        assertTrue("Depot", warteText(S.investments, ms = 30_000))
        zeit(1500)
        return c
    }

    // Rollt die Liste mit dem Anker in Schritten von 200 Pixeln, bis ziel ganz im Bild steht, und gibt die Strecke bis
    // zur Unterkante von ziel in dp zurueck (Dichte 2), ohne die Kopfzeile; -1, wenn ziel nicht auftaucht.
    private fun DesktopComposeUiTest.streckeBis(ziel: String, anker: String): Float {
        val liste = runCatching {
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyDescendant(hasText(anker)))
                .fetchSemanticsNodes().last().id
        }.getOrNull() ?: return -1f
        var gerollt = 0f
        repeat(150) {
            val b = onAllNodesWithText(ziel).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            if (b != null && b.width > 0f && b.top >= 0f && b.bottom <= 1750f) return (gerollt + b.bottom) / 2f - 64f
            runCatching { onAllNodes(SemanticsMatcher("Liste") { it.id == liste })[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 200f) } }
            gerollt += 200f
            zeit(200)
        }
        return -1f
    }

    private fun DesktopComposeUiTest.pruefeSeite(c: DesktopContainer, symbol: String, bild: String) {
        c.state.springeZu(WidgetZiel.instrument(symbol))
        assertTrue("Seite $symbol", warteText(S.buy, ms = 30_000))
        // Profil, News und Kennzahlen kommen aus dem Netz
        zeit(12_000)
        foto("$bild-oben")
        // Seit 5.92: Sparplan schwebend neben Kaufen, Notiz als Symbol in der Kopfzeile
        assertTrue("Sparplan und Notiz", da(S.savingsPlan) && onAllNodesWithContentDescription(S.notizHinzufuegen).fetchSemanticsNodes().isNotEmpty())
        rolleZu(S.bkTitel, anker = S.indButton)
        assertTrue("Kennzahlen", da(S.bkTitel) || warteText(S.bkTitel, ms = 5_000))
        // Alle Kennzahlen im Blatt, mit der ISIN zum Kopieren
        rolleZu(S.bwAlleKennzahlen, anker = S.indButton)
        tippe(S.bwAlleKennzahlen)
        assertTrue("Blatt mit allen Kennzahlen", warteText(S.kind, ms = 5_000))
        foto("$bild-alle-kennzahlen")
        escape()
        assertTrue("Blatt zu", warte(5_000) { !da(S.kind) })
        val strecke = streckeBis(S.bwHintergrund, anker = S.bkTitel)
        notiere("Seitenlaenge $symbol bis Hintergrund: ${strecke.toInt()} dp (Bild 900 dp hoch)")
        assertTrue("Hintergrund erreicht", strecke > 0f)
        // Eingeklappt: keine Karten aus dem Hintergrund
        assertTrue("Hintergrund eingeklappt", !da(S.bhTitel) && !da(S.copyWhoTitle) && !da(S.vdKarteTitel))
        foto("$bild-hintergrund-zu")
        tippe(S.bwHintergrund)
        zeit(15_000)
        val gezeigt = listOf(S.opTitel, S.trendsTitel, S.copyWhoTitle, S.vdKarteTitel, S.grTitel, S.bhLeer, S.lpTitel, S.bhTitel, S.lwTitel)
            .filter { t -> warte(500) { da(t) } || run { rolleZu(t, anker = S.bwHintergrund); da(t) } }
        notiere("Hintergrund $symbol aufgeklappt, Karten: $gezeigt")
        foto("$bild-hintergrund-auf")
    }

    @Test
    fun wertpapierseite() = runDesktopComposeUiTest(width = 840, height = 1800, testTimeout = 30.minutes) {
        val c = starte()
        pruefeSeite(c, "AAPL", "30-apple")
        escape()
        zeit(1500)
        pruefeSeite(c, "SAP.DE", "31-sap")
    }
}
