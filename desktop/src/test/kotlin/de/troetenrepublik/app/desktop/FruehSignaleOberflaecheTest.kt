package de.troetenrepublik.app.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

// Fruehe Signale ohne Fenster (5.92): Abschnitt im Reiter „Ausblick“ der Maerkte, die Lager auf der Seite von Rohoel
// (WTI) und das Signal auf der Seite einer Fluglinie. Daten echt aus dem Netz; Bilder und Ablauf (fruehsignale.txt)
// unter build/testbilder.
@OptIn(ExperimentalTestApi::class)
class FruehSignaleOberflaecheTest : RundgangBasis("fruehsignale.txt") {

    @Test
    fun fruehSignale() = runDesktopComposeUiTest(width = 840, height = 1800, testTimeout = 30.minutes) {
        val c = starteMitDepot("fruehsignale")

        // Maerkte, Reiter Ausblick, bis zu den fruehen Signalen
        kachelZeigen(S.maerkteTitel)
        tippe(S.maerkteTitel)
        assertTrue("Maerkte", warteText(S.maerkteAusblick, ms = 10_000))
        leisteZu(S.maerkteAusblick, S.maerkteBewegungen)
        tippe(S.maerkteAusblick)
        assertTrue("Ausblick", warteText(S.agTitel, ms = 60_000))
        rolleZu(S.fiTitel, anker = S.agTitel)
        assertTrue("Lager und Wirtschaftsdaten", warte(60_000) { da(S.fiName(Fruehindikatoren.ROHOEL)) })
        rolleZu(S.fiName(Fruehindikatoren.DUERRE), anker = S.agTitel)
        tippe(S.fiName(Fruehindikatoren.ROHOEL))
        zeit(1500)
        notiere("Hinweis EU-Gas ohne Zugang: " + da(S.fiOhneZugang))
        foto("80-ausblick-fruehe-signale")
        escape()

        // Rohoel: Lager mit Fuenfjahresbereich unter der Rohstoff-Erklaerung
        c.state.springeZu(WidgetZiel.instrument("CL=F"))
        assertTrue("Seite Rohoel", warteText(S.indButton, ms = 30_000))
        zeit(4000)
        rolleZu(S.fiName(Fruehindikatoren.CUSHING), anker = S.indButton)
        assertTrue("Lager", warte(60_000) { da(S.fiName(Fruehindikatoren.ROHOEL)) })
        rolleZu(S.fiName(Fruehindikatoren.DESTILLAT), anker = S.indButton)
        zeit(1500)
        foto("81-rohoel-lager")
        escape()

        // Fluglinie: Fluggaeste der TSA
        c.state.springeZu(WidgetZiel.instrument("DAL"))
        assertTrue("Seite Delta", warteText(S.indButton, ms = 30_000))
        zeit(6000)
        rolleZu(S.fiName(Fruehindikatoren.FLUGGAESTE), anker = S.indButton)
        val flug = warte(60_000) { da(S.fiName(Fruehindikatoren.FLUGGAESTE)) }
        notiere("Fluggaeste auf der Seite: $flug")
        zeit(1000)
        foto("82-fluglinie")
    }
}
