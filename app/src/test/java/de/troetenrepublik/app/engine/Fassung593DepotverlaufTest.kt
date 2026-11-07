package de.troetenrepublik.app.engine

import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.ui.VerlaufZeitraum
import de.troetenrepublik.app.ui.punkteVorzeichen
import de.troetenrepublik.app.ui.tageAktuell
import de.troetenrepublik.app.ui.ytdTage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

// Fassung 5.93: Depotverlauf. Jahr bis heute in Tagen und der Abstand zum Vergleich in Prozentpunkten.
class Fassung593DepotverlaufTest {

    @Test fun jahrBisHeuteInTagen() {
        assertEquals(1, ytdTage(LocalDate.of(2025, 1, 1)))
        assertEquals(60, ytdTage(LocalDate.of(2025, 3, 1)))
    }

    @Test fun tageJeZeitraum() {
        assertEquals(60, VerlaufZeitraum.YTD.tageAktuell(LocalDate.of(2025, 3, 1)))
        assertEquals(91, VerlaufZeitraum.M3.tageAktuell())
        assertEquals(0, VerlaufZeitraum.MAX.tageAktuell())
    }

    @Test fun abstandMitVorzeichen() {
        // Fmt.num folgt der Sprache der Oberflaeche, daher der Vergleich mit derselben Quelle
        assertEquals("+" + Fmt.num(1.8, 1), punkteVorzeichen(1.84))
        assertEquals("−" + Fmt.num(0.3, 1), punkteVorzeichen(-0.3))
        assertEquals(Fmt.num(0.0, 1), punkteVorzeichen(0.01))
    }
}
