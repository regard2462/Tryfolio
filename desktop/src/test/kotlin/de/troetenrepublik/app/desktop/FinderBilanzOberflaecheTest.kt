package de.troetenrepublik.app.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.troetenrepublik.app.AktienfinderSpeicher
import de.troetenrepublik.app.FinderBilanzSpeicher
import de.troetenrepublik.app.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Test

// Aktienfinder mit Bilanzqualitaet ohne Fenster und mit echten Daten: DAX einordnen, Bilanzdaten im Hintergrund
// abwarten, Filter „nur solide Bilanz“ und eine Aktie aufklappen. Im Ablauf (finderbilanz.txt) stehen die Dauer des
// Durchlaufs und wie viele Werte eine Bilanz bekommen; Bilder unter build/testbilder.
@OptIn(ExperimentalTestApi::class)
class FinderBilanzOberflaecheTest : RundgangBasis("finderbilanz.txt") {

    private fun stand() = FinderBilanzSpeicher.stand(AktienfinderSpeicher.daten)

    @Test
    fun finderMitBilanz() = runDesktopComposeUiTest(width = 840, height = 1800) {
        val c = starteMitDepot("finderbilanz")
        rolleZu(S.afTitel)
        nachOben(S.afTitel)
        assertTrue("Kachel Aktienfinder", warteText(S.afTitel, ms = 10_000))
        tippe(S.afTitel)
        zeit(1500)
        leisteZu("DAX", S.dfUniversumName("ALLE"))
        tippe("DAX")
        val t0 = System.currentTimeMillis()
        // Hat ein frueherer Test im selben Lauf den DAX schon geladen, zeigt der Reiter die Liste ohne Start-Knopf
        if (warte(5_000) { da(S.afStart) }) tippe(S.afStart)
        assertTrue("Finder geladen", warte(180_000) { !da(S.afLaedt) && AktienfinderSpeicher.universum == "DAX" && AktienfinderSpeicher.daten.isNotEmpty() })
        notiere("DAX Kennzahlen nach ${(System.currentTimeMillis() - t0) / 1000} s")
        // Waehrend die Bilanzdaten eintreffen
        warte(60_000) { da(S.afBilanzStand(0, 0).substringBefore(' '), teil = true) && stand().first > 3 }
        foto("p10-finder-bilanz-laedt")
        assertTrue("DAX Bilanz fertig", warte(600_000) { stand().let { it.second > 0 && it.first == it.second } && !da(S.afDetailsLaedt) })
        zeit(2500)
        val dax = System.currentTimeMillis() - t0
        val daten = AktienfinderSpeicher.daten
        notiere("DAX fertig nach ${dax / 1000} s: ${daten.size} Werte, Stand ${stand()}, F-Score ${daten.count { it.fScore != null }}, Zone ${daten.count { it.zone != null }}, " +
            "Ampel gruen ${daten.count { it.bilanz?.name == "GRUEN" }}, gelb ${daten.count { it.bilanz?.name == "GELB" }}, rot ${daten.count { it.bilanz?.name == "ROT" }}")
        val erste = de.troetenrepublik.app.engine.Aktienfinder.bewerten(daten).sortedByDescending { it.gesamt }
        erste.take(6).forEach { r -> notiere("  ${r.symbol} ${"%.0f".format(r.gesamt)} Q=${r.bereiche[de.troetenrepublik.app.engine.Aktienfinder.Bereich.QUALITAET]?.let { "%.0f".format(it) }} F=${r.daten.fScore?.let { "%.1f".format(it) }} Z=${r.daten.zone} ${r.daten.bilanz} Datenlage ${"%.0f".format(r.abdeckung * 100)} %") }
        // Aufklappen: Bilanzzeile mit Ampel und Satz
        val oben = erste.firstOrNull { it.daten.bilanz != null }
        if (oben != null) {
            tippe(S.afNurSolide)
            zeit(1000)
            foto("p10-finder-solide")
            tippe(S.afNurSolide)
            zeit(1000)
            tippe(oben.daten.name)
            zeit(800)
            foto("p10-finder-bilanz-offen")
        }

        notiere("fertig")
        c.beenden()
    }

    // Die Liste mit dem Text um 700 Punkte weiterschieben, damit die Kachel nicht unter den schwebenden Knoepfen liegt.
    private fun androidx.compose.ui.test.DesktopComposeUiTest.nachOben(text: String) {
        runCatching {
            val id = onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) and
                    androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasText(text)),
            ).fetchSemanticsNodes().last().id
            onAllNodes(androidx.compose.ui.test.SemanticsMatcher("Liste") { it.id == id })[0]
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 700f) }
        }
        zeit(800)
    }
}
