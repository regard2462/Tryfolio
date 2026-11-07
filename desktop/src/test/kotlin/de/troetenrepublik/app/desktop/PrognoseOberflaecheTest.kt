package de.troetenrepublik.app.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.Test

// Prediction Markets ohne Fenster: echte Listen von Polymarket und Kalshi, ein Ereignis mit Verlauf, eine
// Uebungswette und „Meine Wetten“. Bilder und Ablauf (prognose.txt) wie beim Rundgang unter build/testbilder.
@OptIn(ExperimentalTestApi::class)
class PrognoseOberflaecheTest : RundgangBasis("prognose.txt") {
    @Test
    fun prognose() = runDesktopComposeUiTest(width = 840, height = 1800) {
        val c = starteMitDepot("prognose")
        prognoseSchritte(c)
        notiere("fertig")
        c.beenden()
    }
}
