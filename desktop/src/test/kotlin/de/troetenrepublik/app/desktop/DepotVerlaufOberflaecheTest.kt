package de.troetenrepublik.app.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Test

// Depotverlauf ohne Fenster: Testdepot mit SAP und Apple (vor einem Jahr),
// NVIDIA-Aktien und einem Turbo Long auf NVIDIA, der zum Teil wieder verkauft ist. Kurse echt von Yahoo; Bilder und Ablauf
// (depotverlauf.txt) unter build/testbilder.
@OptIn(ExperimentalTestApi::class)
class DepotVerlaufOberflaecheTest : RundgangBasis("depotverlauf.txt") {
    // Die oberste senkrechte Liste um dy Pixel weiterrollen.
    private fun androidx.compose.ui.test.DesktopComposeUiTest.seiteRollen(dy: Float) {
        // Die Liste des Depotverlaufs: im obersten Blatt, also die letzte rollbare Liste
        val listen = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        val n = listen.fetchSemanticsNodes().size
        if (n > 0) listen[n - 1].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) }
        zeit(1200)
    }

    @Test
    fun depotverlauf() = runDesktopComposeUiTest(width = 840, height = 1800) {
        val c = starteMitDepot("depotverlauf")
        val tag = 86_400_000L
        val jetzt = System.currentTimeMillis()
        val turbo = Derivative("ko-nvda-test", "NVDA", "NVIDIA", DerivativeKind.KO_LONG, strike = 120.0, barrier = 120.0, ratio = 10.0,
            ccy = "USD", createdAt = jetzt - 90 * tag)
        c.store.update { a0 ->
            var a = a0
            a = Trading.buy(a, turbo.id, "Turbo Long NVIDIA", "NVDA", 300.0, 5.0, turbo, now = jetzt - 90 * tag).account
            a = Trading.buy(a, "NVDA", "NVIDIA", "NVDA", 10.0, 150.0, now = jetzt - 60 * tag).account
            a = Trading.sell(a, turbo.id, 100.0, 6.0, now = jetzt - 20 * tag).account
            a
        }
        notiere("Testdepot: ${c.state.account.value.positions.joinToString { it.key + ":" + it.qty }}")
        // Der Depotverlauf steht unter Analytics bei den Werkzeugen, mit Beschreibung wie die anderen Werkzeuge
        kachelZeigen(S.analytics)
        tippe(S.analytics)
        assertTrue("Analytics", warteText(S.toolsTitle, ms = 10_000))
        tippe(S.toolsTitle)
        zeit(1500)
        assertTrue("Depotverlauf in den Werkzeugen", sichtbar(S.dvTitle) && sichtbar(S.dvSubtitle))
        assertTrue("Beschreibung der Werkzeuge", sichtbar(S.ptSubtitle("CHECK")))
        foto("23a-analytics-werkzeuge")
        // Der Depotverlauf oeffnet als Blatt ueber Analytics wie die anderen Werkzeuge
        tippe(S.dvTitle)
        assertTrue("Bewegungen", warte(90_000) { da(S.dvMoves) })
        zeit(4000)
        foto("23b-depotverlauf")
        // Ziehen durch den Graphen: Veraenderung des Tages und die Werte dahinter statt der Markentexte
        val legende = onAllNodesWithText(S.ptYourDepot).fetchSemanticsNodes().map { it.boundsInRoot }
            .filter { it.top > 300f && it.width > 0f }.minByOrNull { it.top }
        assertTrue("Legende unter dem Graphen", legende != null)
        val y = legende!!.top - 230f
        // Das Blatt liegt in einem eigenen Fenster ueber dem Hauptfenster: die oberste Wurzel
        val oben = onAllNodes(isRoot()).let { it[it.fetchSemanticsNodes().size - 1] }
        oben.performMouseInput {
            moveTo(Offset(620f, y))
            press()
            repeat(10) { moveBy(Offset(-12f, 0f)); advanceEventTime(16) }
        }
        zeit(800)
        assertTrue("Erklaerung statt Hinweis", !da(S.dvDragHint))
        foto("23b2-depotverlauf-ziehen")
        oben.performMouseInput { release() }
        zeit(800)
        assertTrue("Hinweis nach dem Loslassen", da(S.dvDragHint))
        rolleZu(S.dvRises, S.dvTitle)
        foto("23c-depotverlauf-bewegungen")
        // Den groessten Anstieg aufklappen: alle Werte mit ihrem Anteil
        val ersteUrsache = onAllNodes(hasText(S.dvBy(""), substring = true)).fetchSemanticsNodes().firstOrNull()
        if (ersteUrsache != null) onAllNodes(hasText(S.dvBy(""), substring = true))[0].performClick()
        zeit(1200)
        foto("23d-depotverlauf-aufgeklappt")
        seiteRollen(1200f)
        foto("23e-depotverlauf-beitraege")
        seiteRollen(1400f)
        foto("23f-depotverlauf-geschaefte")
        notiere("fertig")
        c.beenden()
    }
}
