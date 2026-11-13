package de.troetenrepublik.app.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

// „Im Preis?“ ohne Fenster (5.92): die Zeile unter den Lagern auf der Seite von Rohoel (WTI), aufgeklappt mit
// Terminkurve, und die Zeilen im Abschnitt „Fruehe Signale“ des Ausblicks. Daten echt aus dem Netz; Bilder und Ablauf
// (eingepreist.txt) unter build/testbilder.
@OptIn(ExperimentalTestApi::class)
class EingepreistOberflaecheTest : RundgangBasis("eingepreist.txt") {

    @Test
    fun imPreis() = runDesktopComposeUiTest(width = 840, height = 1800, testTimeout = 30.minutes) {
        val c = starteMitDepot("eingepreist")

        // Rohoel: Lager mit „Im Preis?“, die erste Zeile aufgeklappt
        c.state.springeZu(WidgetZiel.instrument("CL=F"))
        assertTrue("Seite Rohoel", warteText(S.indButton, ms = 30_000))
        zeit(4000)
        rolleZu(S.fiName(Fruehindikatoren.CUSHING), anker = S.indButton)
        assertTrue("Lager", warte(60_000) { da(S.fiName(Fruehindikatoren.ROHOEL)) })
        val imPreis = warte(120_000) { da(S.epTitel) }
        notiere("Im Preis auf der Seite von Rohoel: $imPreis")
        if (imPreis) {
            tippe(S.epTitel)
            zeit(1500)
            // Rueckblick (5.92): Bilanz unter der Tendenz, aufgeklappt die Zeitraeume
            notiere("Rueckblick: ohne Vorteil " + da(S.trOhneVorteil) + ", belegt " + da("Früher:", teil = true) + ", Einzelheiten " + da(S.trDetailTitel) +
                ", Vorschlag " + da("Übungsidee", teil = true))
        }
        rolleZu(S.fiName(Fruehindikatoren.ROHOEL), anker = S.indButton)
        zeit(1000)
        foto("90-rohoel-im-preis")
        escape()

        // Ausblick der Maerkte: Lager und Wirtschaftsdaten mit „Im Preis?“
        kachelZeigen(S.maerkteTitel)
        tippe(S.maerkteTitel)
        assertTrue("Maerkte", warteText(S.maerkteAusblick, ms = 10_000))
        leisteZu(S.maerkteAusblick, S.maerkteBewegungen)
        tippe(S.maerkteAusblick)
        assertTrue("Ausblick", warteText(S.agTitel, ms = 60_000))
        rolleZu(S.fiTitel, anker = S.agTitel)
        assertTrue("Lager und Wirtschaftsdaten", warte(60_000) { da(S.fiName(Fruehindikatoren.ROHOEL)) })
        notiere("Im Preis im Ausblick: " + warte(120_000) { da(S.epKeine) || da(S.epTitel) })
        rolleZu(S.fiName(Fruehindikatoren.ERSTANTRAEGE), anker = S.agTitel)
        zeit(1500)
        foto("91-ausblick-im-preis")
    }
}
