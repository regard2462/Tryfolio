package de.troetenrepublik.app.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.troetenrepublik.app.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

// Vermoegen der weiteren Regierungsmitglieder (Fassung 5.92) ohne Fenster: Liste der Regierung mit Amt in eigener
// Zeile und „Vermoegen ueber …“, Seite einer Person mit aufgeklappter Vermoegenszeile. Daten echt aus dem Netz;
// Bilder und Ablauf (regierungvermoegen.txt) unter build/testbilder.
@OptIn(ExperimentalTestApi::class)
class RegierungVermoegenOberflaecheTest : RundgangBasis("regierungvermoegen.txt") {

    @Test
    fun vermoegenRegierung() = runDesktopComposeUiTest(width = 840, height = 1800, testTimeout = 30.minutes) {
        val c = starteMitDepot("regierungvermoegen")
        val direkt = da(S.copyTitle)
        val kachel = if (direkt) S.copyTitle else S.more
        rolleZu(kachel, S.investments)
        kachelZeigen(kachel)
        if (!direkt) { tippe(S.more); assertTrue(warteText(S.copyTitle, ms = 5_000)) }
        tippe(S.copyTitle)
        assertTrue("Copytrading", warteText(S.copyLatestTrades, ms = 60_000))
        tippe(S.copyGovernment)
        warte(120_000) { da(S.ogeTitel) }
        // Die Liste liest die Vermoegensmeldungen nach und nach, oben zuerst; in der Zeile stehen geschuetzte Leerzeichen
        val ueber = S.rgVermoegen(S.copyOver("")).trim().replace(' ', Char(0xA0))
        val gefunden = warte(900_000) { onAllNodesWithText(ueber, substring = true).fetchSemanticsNodes().size >= 4 }
        notiere("Vermoegen in der Liste: $gefunden, Zeilen " + onAllNodesWithText(ueber, substring = true).fetchSemanticsNodes().size)
        zeit(3000)
        foto("p9-1-regierung-vermoegen")
        // Seite der ersten Person mit Vermoegen unter dem Praesidenten
        val name = onAllNodesWithText(ueber, substring = true).fetchSemanticsNodes().drop(1).firstNotNullOfOrNull { n ->
            listOf("Michael Kratsios", "Scott Kupor", "Kevin Warsh", "Eric Ueland", "Sean McMaster", "Stephen Miller").firstOrNull { da(it) }
        } ?: "Michael Kratsios"
        tippe(name)
        val zeile = warte(180_000) { da(S.ogeVmTitel("J", 2026)) || da(S.ogeVmTitel("A", 2025)) || da(S.ogeVmTitel("A", 2026)) }
        notiere("Vermoegenszeile bei $name: $zeile")
        listOf(S.ogeVmTitel("J", 2026), S.ogeVmTitel("A", 2025), S.ogeVmTitel("A", 2026)).firstOrNull { da(it) }?.let { tippe(it) }
        zeit(1500)
        notiere("PDF-Zeile: " + da(S.pdfKurz))
        foto("p9-2-person-vermoegen")
        notiere("fertig")
        c.beenden()
    }
}
