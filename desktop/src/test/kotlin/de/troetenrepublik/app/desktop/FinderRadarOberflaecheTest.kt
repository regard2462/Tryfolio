package de.troetenrepublik.app.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.troetenrepublik.app.AktienfinderSpeicher
import de.troetenrepublik.app.RadarSpeicher
import de.troetenrepublik.app.engine.Aktienfinder
import de.troetenrepublik.app.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

// Aktienfinder und Radar ohne Fenster mit echten Daten: DAX einordnen, eine Aktie aufklappen, Radar durchlaufen.
// Bilder und Ablauf (finder.txt) unter build/testbilder; im Ablauf stehen Abdeckung und die ersten Treffer.
@OptIn(ExperimentalTestApi::class)
class FinderRadarOberflaecheTest : RundgangBasis("finder.txt") {
    @Test
    fun finderUndRadar() = runDesktopComposeUiTest(width = 840, height = 1800, testTimeout = 30.minutes) {
        val c = starteMitDepot("finder")
        rolleZu(S.afTitel)
        nachOben(S.afTitel)
        assertTrue("Kachel Aktienfinder", warteText(S.afTitel, ms = 10_000))
        tippe(S.afTitel)
        zeit(1500)
        leisteZu("DAX", S.dfUniversumName("ALLE"))
        tippe("DAX")
        // Hat ein frueherer Test im selben Lauf den DAX schon geladen, zeigt der Reiter die Liste ohne Start-Knopf
        if (warte(5_000) { da(S.afStart) }) tippe(S.afStart)
        assertTrue("Finder geladen", warte(180_000) { !da(S.afLaedt) && AktienfinderSpeicher.universum == "DAX" && AktienfinderSpeicher.daten.isNotEmpty() })
        warte(180_000) { !da(S.afDetailsLaedt) }
        zeit(1500)
        val daten = AktienfinderSpeicher.daten
        notiere("Daten: ${daten.size}, KGV ${daten.count { it.kgv != null }}, Note ${daten.count { it.note != null }}, Jahr ${daten.count { it.jahr != null }}, " +
            "Insider ${daten.count { it.insider != null }}, Leer ${daten.count { it.leer != null }}, Details ${daten.count { it.mitDetails }}, Bilanz ${daten.count { it.bilanz != null }}")
        val e = Aktienfinder.bewerten(daten).sortedByDescending { it.gesamt }
        e.take(8).forEach { r -> notiere("  ${r.symbol} ${"%.0f".format(r.gesamt)} ${r.einordnung} ${r.bereiche.entries.joinToString { "${it.key}=${it.value?.let { v -> "%.0f".format(v) }}" }} | ${r.gruende.joinToString { it.kennzahl.name }}") }
        foto("p6-aktienfinder")
        e.firstOrNull()?.let { tippe(it.daten.name); foto("p6-aktienfinder-offen") }

        // Die Einzelheiten klappen in der Liste auf; Escape verliesse hier den Aktienfinder
        tippe(S.afAnsichtRadar)
        assertTrue("Radar fertig", warte(300_000) { !da(S.radLaedt) && RadarSpeicher.stand != null })
        zeit(1500)
        val st = RadarSpeicher.stand!!
        notiere("Radar: ${st.treffer.size} Werte, Quellen ${st.quellen}")
        st.treffer.take(10).forEach { t -> notiere("  ${t.symbol} ${"%.0f".format(t.punkte)} ${t.signale.joinToString { "${it.art}(${"%.2f".format(it.staerke)})" }}") }
        // Die Ansicht im Finder zeigt nur Werte des gewaehlten Universums
        val imUniversum = e.map { it.symbol }.toSet()
        st.treffer.firstOrNull { it.symbol in imUniversum }?.let { t -> tippe("(${t.symbol})", teil = true) }
        foto("p6-radar")
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
