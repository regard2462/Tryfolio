package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.93, Nacharbeit: Rangaenderung bei Reddit als ganzer Satz in beiden Sprachen und die
// ueberarbeiteten englischen Hinweise zu Mitteilungen und Glocken.
class Fassung593NacharbeitTest {

    @Test
    fun rangAenderungDeutsch() {
        assertEquals("1 Platz höher als vor 24 Stunden", De.trRangAenderung(1, true))
        assertEquals("1 Platz tiefer als vor 24 Stunden", De.trRangAenderung(1, false))
        assertEquals("3 Plätze höher als vor 24 Stunden", De.trRangAenderung(3, true))
        assertEquals("3 Plätze tiefer als vor 24 Stunden", De.trRangAenderung(3, false))
    }

    @Test
    fun rangAenderungEnglisch() {
        assertEquals("1 place higher than 24 hours ago", En.trRangAenderung(1, true))
        assertEquals("1 place lower than 24 hours ago", En.trRangAenderung(1, false))
        assertEquals("3 places higher than 24 hours ago", En.trRangAenderung(3, true))
        assertEquals("3 places lower than 24 hours ago", En.trRangAenderung(3, false))
    }

    @Test
    fun rangAenderungOhneGedankenstrich() {
        for (t in listOf(
            De.trRangAenderung(1, true), De.trRangAenderung(1, false), De.trRangAenderung(3, true), De.trRangAenderung(3, false),
            En.trRangAenderung(1, true), En.trRangAenderung(1, false), En.trRangAenderung(3, true), En.trRangAenderung(3, false),
        )) assertFalse(t, t.contains("–") || t.contains("—"))
    }

    @Test
    fun englischeHinweiseNeuFormuliert() {
        for (t in listOf(En.notifWerkHinweis, En.rbMitteilung)) {
            assertFalse(t, t.contains("Off are"))
            assertFalse(t, t.contains("Nobody has a bell"))
        }
        assertEquals("Review notification", En.rbMitteilung)
    }
}
