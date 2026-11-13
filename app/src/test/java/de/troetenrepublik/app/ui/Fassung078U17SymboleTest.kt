package de.troetenrepublik.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Gezeichnete Symbole der Werkzeugliste: eines je Werkzeug, im Raster, mit Hauptform.
class Fassung078U17SymboleTest {
    private val schluessel = (WERKZEUG_GRUPPEN.flatMap { it.second }).distinct()

    @Test
    fun jedesWerkzeugHatEinSymbol() {
        assertEquals(24, schluessel.size)
        schluessel.forEach { assertTrue("ohne Symbol: $it", werkzeugFormen(it).isNotEmpty()) }
        // Kein Symbol ohne Werkzeug
        assertEquals(schluessel.toSet(), WERKZEUG_SYMBOLE.keys)
    }

    @Test
    fun keineZweiGleichenSymbole() {
        val formen = schluessel.map { werkzeugFormen(it) }
        assertEquals(formen.size, formen.toSet().size)
    }

    @Test
    fun jedesSymbolHatEineHauptform() {
        schluessel.forEach { k -> assertTrue("ohne Hauptform: $k", werkzeugFormen(k).any { it.ton == SymbolTon.HAUPT }) }
    }

    @Test
    fun alleFormenLiegenImRaster() {
        // Runde Enden und Endpunkte duerfen eine Rastereinheit ueberstehen
        val rand = 1f
        schluessel.forEach { k ->
            werkzeugFormen(k).forEach { f ->
                val (links, oben, rechts, unten) = f.rahmen()
                assertTrue("$k: $f", links >= -rand && oben >= -rand && rechts <= SYMBOL_RASTER + rand && unten <= SYMBOL_RASTER + rand)
                assertTrue("$k: $f", rechts > links || unten > oben)
            }
        }
    }

    @Test
    fun formenSindVollstaendig() {
        schluessel.forEach { k ->
            werkzeugFormen(k).forEach { f ->
                when (f) {
                    is SymbolForm.Linie -> assertTrue("$k: $f", f.punkte.size >= 4 && f.punkte.size % 2 == 0)
                    is SymbolForm.Kurve -> assertEquals("$k: $f", 8, f.punkte.size)
                    is SymbolForm.Flaeche -> {
                        assertTrue("$k: $f", f.punkte.size >= 6 && f.punkte.size % 2 == 0)
                        assertTrue("$k: $f", f.deckkraft > 0f && f.deckkraft <= 1f)
                    }
                    is SymbolForm.Rechteck -> assertTrue("$k: $f", f.breite > 0f && f.hoehe > 0f && f.radius * 2f <= minOf(f.breite, f.hoehe))
                    is SymbolForm.Kreis -> assertTrue("$k: $f", f.radius > 0f)
                }
            }
        }
    }

    @Test
    fun gestricheltNurBeiLinien() {
        // Strichlinien gibt es bei Ziellinie, Szenarien, Vergleichslinie und Nulllinie der Optionen
        val mit = schluessel.filter { k -> werkzeugFormen(k).any { it is SymbolForm.Linie && it.gestrichelt } }
        assertEquals(setOf("REBALANCE", "SCENARIO", "PERFORMANCE", "STILLHALTER", "OPTIONS"), mit.toSet())
    }
}
