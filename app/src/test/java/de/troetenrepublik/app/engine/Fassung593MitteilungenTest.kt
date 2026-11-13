// Fassung 5.93: weniger Mitteilungen ab Werk. Vor dem Marker fallen Glocken weg, die zugleich gefolgt werden
// (Schluessel k: oder i:); die Mitteilung zum Rueckblick ist vor dem ersten Speichern immer aus, danach gilt
// die eigene Wahl.
package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.data.glockenLesen
import de.troetenrepublik.app.data.rueckblickMitteilungLesen
import org.junit.Assert.assertEquals
import org.junit.Test

class Fassung593MitteilungenTest {

    @Test fun glockenGefolgtKUndIWerdenEntfernt() {
        assertEquals("n:x\nr:y", glockenLesen("k:a|b\ni:12\nn:x\nr:y\nk:z", "k:a|b\ni:12\nk:z", false))
    }

    @Test fun nichtGefolgteGlockeBleibt() {
        assertEquals("k:q\nn:x", glockenLesen("k:q\nn:x", "k:a|b", false))
    }

    @Test fun mitMarkerBleibtAlles() {
        assertEquals("k:a|b\nn:x", glockenLesen("k:a|b\nn:x", "k:a|b", true))
    }

    @Test fun leereEingabe() {
        assertEquals("", glockenLesen("", "k:a|b", false))
    }

    @Test fun rueckblickVorMarkerImmerAus() {
        assertEquals(false, rueckblickMitteilungLesen(true, false))
    }

    @Test fun rueckblickOhneGespeicherteWahlAus() {
        assertEquals(false, rueckblickMitteilungLesen(null, true))
    }

    @Test fun rueckblickGespeicherteWahlGilt() {
        assertEquals(true, rueckblickMitteilungLesen(true, true))
        assertEquals(false, rueckblickMitteilungLesen(false, true))
    }

    @Test fun vorgabenAbWerk() {
        assertEquals(false, Settings().rueckblickMitteilung)
        assertEquals(MITTEILUNG_WICHTIGE, Settings().newsMitteilung)
    }
}
