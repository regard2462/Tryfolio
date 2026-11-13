package de.troetenrepublik.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Die Wischsperre gilt nur fuer die Seite, auf der die Flaeche liegt, und nur solange sie aktiv ist.
class WischSperreTest {
    private val angemeldet = ArrayList<WischSperre.Flaeche>()
    private val seiteA = Any()
    private val seiteB = Any()
    private val flaeche = Rect(0f, 200f, 400f, 600f)

    private fun melden(seite: Any?, aktiv: () -> Boolean = { true }, rechteck: Rect? = flaeche) =
        WischSperre.anmelden(seite, aktiv) { rechteck }.also { angemeldet += it }

    @After
    fun aufraeumen() { angemeldet.forEach { WischSperre.abmelden(it) } }

    @Test
    fun sperrtNurInnerhalbDerFlaeche() {
        melden(seiteA)
        assertTrue(WischSperre.enthaelt(Offset(100f, 300f), seiteA))
        assertFalse(WischSperre.enthaelt(Offset(100f, 100f), seiteA))
        assertFalse(WischSperre.enthaelt(Offset(100f, 700f), seiteA))
    }

    @Test
    fun seiteDarueberIstNichtGesperrt() {
        // Globus auf Seite A (Maerkte), darueber liegt Seite B (Aktie): deren Wisch gilt dort wie ueberall
        melden(seiteA)
        assertFalse(WischSperre.enthaelt(Offset(100f, 300f), seiteB))
    }

    @Test
    fun inaktiveFlaecheSperrtNicht() {
        // Heatmap ungezoomt
        var gezoomt = false
        melden(seiteA, { gezoomt })
        assertFalse(WischSperre.enthaelt(Offset(100f, 300f), seiteA))
        gezoomt = true
        assertTrue(WischSperre.enthaelt(Offset(100f, 300f), seiteA))
    }

    @Test
    fun abgemeldeteOderUnsichtbareFlaecheSperrtNicht() {
        val f = melden(seiteA)
        WischSperre.abmelden(f)
        assertFalse(WischSperre.enthaelt(Offset(100f, 300f), seiteA))
        melden(seiteA, rechteck = null)
        assertFalse(WischSperre.enthaelt(Offset(100f, 300f), seiteA))
    }

    @Test
    fun ohneStapelGehoertAllesDerselbenSeite() {
        melden(null)
        assertTrue(WischSperre.enthaelt(Offset(100f, 300f), null))
    }
}
