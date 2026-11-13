package de.troetenrepublik.app.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.data.SettingsStore
import de.troetenrepublik.app.data.TresorLesen
import de.troetenrepublik.app.data.TresorSpeicher
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.ui.LogoAnzeige
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.79: Hintergrund einfarbiger Logos entfernen (Flutfuellung vom Rand) und die Vorgaben der Logo-Darstellung.
class Fassung079LogoFreistellenTest {

    private val weiss = 0xFFFFFFFF.toInt()
    private val schwarz = 0xFF000000.toInt()

    private class LeererTresor : TresorSpeicher {
        override var verfuegbar = true
        override fun lesen(name: String): TresorLesen = TresorLesen.Leer
        override fun schreiben(name: String, wert: String?) {}
    }

    private class SpeicherStore : DataStore<Preferences> {
        private val stand = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = stand
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(stand.value).also { stand.value = it }
    }

    // 20 mal 20: weisser Rand von 4 Pixeln, innen ein schwarzes Quadrat (Zeichen), optional mit weissem Loch.
    private fun bild(loch: Boolean = false): IntArray {
        val px = IntArray(400) { weiss }
        for (y in 4..15) for (x in 4..15) px[y * 20 + x] = schwarz
        if (loch) for (y in 8..11) for (x in 8..11) px[y * 20 + x] = weiss
        return px
    }

    @Test fun randWirdTransparentZeichenBleibt() {
        val aus = LogoFreistellen.freistellen(bild(), 20, 20)
        assertEquals(0, aus[0] ushr 24)
        assertEquals(0, aus[2 * 20 + 2] ushr 24)
        assertEquals(0, aus[19 * 20 + 19] ushr 24)
        assertEquals(schwarz, aus[5 * 20 + 5])
        assertEquals(schwarz, aus[10 * 20 + 4])
    }

    @Test fun weissesLochImZeichenBleibt() {
        val aus = LogoFreistellen.freistellen(bild(loch = true), 20, 20)
        assertEquals(0, aus[0] ushr 24)
        assertEquals(weiss, aus[9 * 20 + 9])
        assertEquals(schwarz, aus[6 * 20 + 6])
    }

    @Test fun aehnlicheFarbenInnerhalbDerToleranzGehoerenZurFlaeche() {
        val px = bild()
        // fast weisser Rand (Abweichung 20 je Kanal) bleibt Flaeche
        for (i in px.indices) if (px[i] == weiss) px[i] = 0xFFEBEBEB.toInt()
        val aus = LogoFreistellen.freistellen(px, 20, 20)
        assertEquals(0, aus[0] ushr 24)
        assertEquals(schwarz, aus[10 * 20 + 10])
    }

    @Test fun unEinheitlicherRandBleibtUnveraendert() {
        val px = bild()
        // linke Haelfte des Randes dunkelgrau: kein einheitlicher Rand
        for (y in 0 until 20) for (x in 0 until 4) px[y * 20 + x] = 0xFF404040.toInt()
        for (y in 0 until 20) for (x in 4 until 10) if (y < 4 || y > 15) px[y * 20 + x] = 0xFF404040.toInt()
        val kopie = px.copyOf()
        assertNull(LogoFreistellen.randfarbe(px, 20, 20))
        val aus = LogoFreistellen.freistellen(px, 20, 20)
        assertTrue(kopie.contentEquals(aus))
    }

    @Test fun farbverlaufBleibtUnveraendert() {
        val px = IntArray(400) { i -> val x = i % 20; val g = x * 12; (0xFF shl 24) or (g shl 16) or (g shl 8) or g }
        val kopie = px.copyOf()
        assertTrue(kopie.contentEquals(LogoFreistellen.freistellen(px, 20, 20)))
    }

    @Test fun abgerundeteEckenDurchsichtigSindErlaubt() {
        val px = bild()
        for ((x, y) in listOf(0 to 0, 19 to 0, 0 to 19, 19 to 19, 1 to 0, 0 to 1)) px[y * 20 + x] = 0
        assertNotNull(LogoFreistellen.randfarbe(px, 20, 20))
        val aus = LogoFreistellen.freistellen(px, 20, 20)
        assertEquals(0, aus[10] ushr 24)
        assertEquals(schwarz, aus[10 * 20 + 10])
    }

    @Test fun kanteWirdWeich() {
        val px = bild()
        // Pixel mit Abstand 30 (zwischen halber und voller Toleranz) wird teiltransparent
        px[3 * 20 + 10] = 0xFFE1E1E1.toInt()
        val aus = LogoFreistellen.freistellen(px, 20, 20)
        val a = aus[3 * 20 + 10] ushr 24
        assertTrue(a in 1..254)
    }

    @Test fun einfarbigesBildBleibt() {
        val px = IntArray(400) { weiss }
        assertTrue(px.contentEquals(LogoFreistellen.freistellen(px, 20, 20)))
    }

    @Test fun verkleinernBehaeltSeitenverhaeltnis() {
        val px = IntArray(400 * 200) { schwarz }
        val (aus, b, h) = LogoFreistellen.verkleinern(px, 400, 200, 100)
        assertEquals(100, b); assertEquals(50, h)
        assertEquals(b * h, aus.size)
        assertEquals(schwarz, aus[0])
    }

    @Test fun vorgabenEntsprechenDerLetztenVeroeffentlichtenFassung() {
        val s = Settings()
        assertTrue(s.logosFarbig); assertTrue(s.logoRund); assertTrue(s.trennlinien)
        assertFalse(s.logoHintergrundEntfernen)
        assertTrue(LogoAnzeige.farbig); assertTrue(LogoAnzeige.rund); assertTrue(LogoAnzeige.trennlinien)
        assertFalse(LogoAnzeige.hintergrundEntfernen)
        // Kreis, Zeichen mit 0.68 auf der Flaeche, Logos mit eigener Flaeche 1,14 vergroessert
        assertEquals(0.5f, LogoDarstellung.eckenAnteil(s.logoRund), 0f)
        assertEquals(0.68f, LogoDarstellung.ZEICHEN_AUF_FLAECHE, 0f)
        assertEquals(1.14f, LogoDarstellung.FLAECHE_VERGROESSERUNG, 0f)
    }

    @Test fun einstellungWirdGespeichertUndGeladen() = runBlocking {
        val ds = SpeicherStore()
        val store = SettingsStore(ds, LeererTresor())
        assertFalse(store.flow.first().logoHintergrundEntfernen)
        store.update { it.copy(logoHintergrundEntfernen = true) }
        val neu = SettingsStore(ds, LeererTresor()).flow.first()
        assertTrue(neu.logoHintergrundEntfernen)
        assertTrue(neu.logosFarbig); assertTrue(neu.logoRund)
        store.update { it.copy(logoHintergrundEntfernen = false) }
        assertFalse(SettingsStore(ds, LeererTresor()).flow.first().logoHintergrundEntfernen)
    }

    @Test fun textePassenInBeideSprachen() {
        for (s in listOf(De, En)) {
            listOf(s.logoHintergrund, s.hintergrundBehalten, s.hintergrundEntfernen).forEach {
                assertTrue(it.isNotBlank())
                listOf("—", "…").forEach { z -> assertFalse(it.contains(z)) }
            }
        }
        assertEquals("Logo background", En.logoHintergrund)
        assertEquals("Keep", En.hintergrundBehalten)
        assertEquals("Remove", En.hintergrundEntfernen)
        assertEquals("Behalten", De.hintergrundBehalten)
        assertEquals("Entfernen", De.hintergrundEntfernen)
    }

    @Test fun zuschneidenEntferntLeerenRand() {
        // 10 x 10 durchsichtig, Zeichen 4 x 2 bei x 3..6, y 5..6
        val px = IntArray(100)
        for (y in 5..6) for (x in 3..6) px[y * 10 + x] = 0xFF000000.toInt()
        val (aus, b, h) = LogoFreistellen.zuschneiden(px, 10, 10, luft = 0.0)
        org.junit.Assert.assertEquals(4, b)
        org.junit.Assert.assertEquals(2, h)
        org.junit.Assert.assertTrue(aus.all { it == 0xFF000000.toInt() })
        val leer = IntArray(16)
        org.junit.Assert.assertEquals(4, LogoFreistellen.zuschneiden(leer, 4, 4).second)
    }
    @Test fun schwarzWeissMachtDunkleFlaecheSchwarzUndZeichenWeiss() {
        val blau = 0xFF1E3A6E.toInt(); val hellblau = 0xFF7FB2E5.toInt()
        val aus = LogoFreistellen.schwarzWeiss(intArrayOf(blau, hellblau), blau)
        assertEquals(0xFF000000.toInt(), aus[0])
        assertEquals(0xFFFFFFFF.toInt(), aus[1])
    }

    @Test fun schwarzWeissMachtHelleFlaecheWeissUndZeichenSchwarz() {
        val gelb = 0xFFFFD400.toInt(); val weiss = 0xFFFFFFFF.toInt(); val halb = 0x80C80000.toInt()
        val aus = LogoFreistellen.schwarzWeiss(intArrayOf(gelb, weiss, halb), gelb)
        assertEquals(0xFFFFFFFF.toInt(), aus[0])
        // auch ein helleres Zeichen hebt sich ab, Deckkraft bleibt
        assertEquals(0xFF000000.toInt(), aus[1])
        assertEquals(0x80000000.toInt(), aus[2])
    }
    @Test fun zeichenMitGrossemWeissemTeilBekommtDunkleFlaeche() {
        // weisses a mit orangem Bogen, sonst durchsichtig
        val weiss = 0xFFFFFFFF.toInt(); val orange = 0xFFFF9900.toInt()
        val px = IntArray(100) { i -> when { i < 40 -> weiss; i < 70 -> orange; else -> 0 } }
        assertEquals(de.troetenrepublik.app.ui.ZEICHEN_HELL, de.troetenrepublik.app.ui.LogoLooks.freistehend(px)!!.background)
        // nur bunt bleibt bunt
        val bunt = IntArray(100) { i -> if (i < 60) orange else 0 }
        assertEquals(de.troetenrepublik.app.ui.ZEICHEN_BUNT, de.troetenrepublik.app.ui.LogoLooks.freistehend(bunt)!!.background)
    }
    // Hilfen fuer die Masken-Tests: 60 x 60, Scheibe mit Radius 26 um die Mitte
    private val g = 60
    private fun inScheibe(x: Int, y: Int, r: Int = 26) = (x - 30) * (x - 30) + (y - 30) * (y - 30) <= r * r
    private fun tinte(m: IntArray, x: Int, y: Int) = (m[y * g + x] ushr 24) > 128

    @Test fun maskeMuenzeMitWeissemZeichenZeigtNurDasZeichen() {
        val rot = 0xFFD64035.toInt(); val weiss = 0xFFFFFFFF.toInt()
        // rote Scheibe, weisser Balken in der Mitte
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            when { !inScheibe(x, y) -> 0; x in 24..36 && y in 15..45 -> weiss; else -> rot } }
        val m = LogoFreistellen.tinteMaske(px, g, g, null)
        assertTrue(tinte(m, 30, 30))
        assertFalse(tinte(m, 30, 8))
    }

    @Test fun maskeAusgespartesZeichenWirdTinte() {
        val gruen = 0xFF1FB15AL.toInt()
        // gruene Scheibe mit zwei durchsichtigen Balken als Zeichen
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            when { !inScheibe(x, y) -> 0; (x in 18..22 || x in 34..38) && y in 20..40 -> 0; else -> gruen } }
        val m = LogoFreistellen.tinteMaske(px, g, g, null)
        assertTrue(tinte(m, 20, 30))
        assertFalse(tinte(m, 28, 30))
        assertFalse(tinte(m, 30, 8))
    }

    @Test fun maskeRingBleibtRing() {
        val gruen = 0xFF81B4A1L.toInt()
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            if (inScheibe(x, y) && !inScheibe(x, y, 14)) gruen else 0 }
        val m = LogoFreistellen.tinteMaske(px, g, g, null)
        assertTrue(tinte(m, 30, 8))
        assertFalse(tinte(m, 30, 30))
    }

    @Test fun maskeSchriftAusMehrerenTeilenBleibt() {
        val schwarz = 0xFF000000.toInt()
        // drei Buchstabenbloecke mit Innenraum
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            val lx = x % 20
            if (y in 20..40 && lx in 2..17 && !(lx in 7..12 && y in 26..34)) schwarz else 0 }
        val m = LogoFreistellen.tinteMaske(px, g, g, null)
        assertTrue(tinte(m, 4, 30))
        assertFalse(tinte(m, 10, 30))
    }

    @Test fun maskeMitFlaecheEntferntGrundUndInnenraeume() {
        val blau = 0xFF0044C9.toInt(); val weiss = 0xFFFFFFFF.toInt()
        // blaue Flaeche mit weissem O (Innenraum wieder blau)
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            if (inScheibe(x, y, 18) && !inScheibe(x, y, 10)) weiss else blau }
        val m = LogoFreistellen.tinteMaske(px, g, g, blau)
        assertTrue(tinte(m, 30, 15))
        assertFalse(tinte(m, 30, 30))
        assertFalse(tinte(m, 2, 2))
    }

    // Parteizeichen: blaue Scheibe mit weissem Buchstaben auf weissem Grund
    @Test fun symbolAufWeissIstEinfach() {
        val blau = 0xFF1A4FA0.toInt()
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            if (inScheibe(x, y, 22) && !(x in 26..33 && y in 18..42)) blau else weiss }
        assertTrue(LogoFreistellen.einfachesSymbol(px, g, g))
    }

    @Test fun symbolOhneGrundIstEinfach() {
        val orange = 0xFFF7931A.toInt()
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            if (inScheibe(x, y, 24)) (if (x in 27..32 && y in 20..40) weiss else orange) else 0 }
        assertTrue(LogoFreistellen.einfachesSymbol(px, g, g))
    }

    // Kategorie-Kachel: weisses Zeichen auf orangem Feld
    @Test fun zeichenAufFarbigerKachelIstSymbol() {
        val orange = 0xFFF7931A.toInt()
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            if (x in 22..37 && y in 15..45) weiss else orange }
        assertTrue(LogoFreistellen.einfachesSymbol(px, g, g))
    }

    // Streifenflagge: Rand nicht einheitlich
    @Test fun streifenflaggeIstKeinSymbol() {
        val blau = 0xFF0057B7.toInt(); val gelb = 0xFFFFD700.toInt()
        val px = IntArray(g * g) { if (it / g < g / 2) blau else gelb }
        assertFalse(LogoFreistellen.einfachesSymbol(px, g, g))
    }

    @Test fun senkrechteTrikoloreIstKeinSymbol() {
        val farben = intArrayOf(0xFF002395.toInt(), weiss, 0xFFED2939.toInt())
        val px = IntArray(g * g) { farben[(it % g) * 3 / g] }
        assertFalse(LogoFreistellen.einfachesSymbol(px, g, g))
    }

    // Kachel mit weich auslaufendem Rand (Verlauf ins Weisse), innen gruenes Feld mit weissem Zeichen
    @Test fun symbolMitWeichemSaumIstEinfach() {
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            val r = minOf(x, y, g - 1 - x, g - 1 - y)
            if (r < 6) {
                val t = r / 6.0
                val rr = (255 - (255 - 0x76) * t).toInt(); val gg = (255 - (255 - 0xB9) * t).toInt(); val bb = (255 - 255 * t).toInt()
                (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            } else if (x in 20..39 && y in 22..37) weiss else 0xFF76B900.toInt() }
        assertTrue(LogoFreistellen.einfachesSymbol(px, g, g))
    }

    // Weicher Saum faellt weg, Grund ist das gruene Feld; die Maske enthaelt nur das weisse Zeichen
    @Test fun symbolMitSaumWirdZumZeichen() {
        val gruen = 0xFF76B900.toInt()
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            val r = minOf(x, y, g - 1 - x, g - 1 - y)
            // ungleichmaessig ins Weisse auslaufend
            val s = (r * 2 + (x + y) % 5) .coerceAtMost(14)
            if (r < 6) (0xFF shl 24) or ((255 - (255 - 0x76) * s / 14) shl 16) or ((255 - (255 - 0xB9) * s / 14) shl 8) or (255 - 255 * s / 14)
            else if (x in 20..39 && y in 22..37) weiss else gruen }
        assertNull(LogoFreistellen.randfarbe(px, g, g))
        val t = LogoFreistellen.symbolTeile(px, g, g)
        assertTrue(t.breite < g)
        assertNotNull(t.grund)
        val m = LogoFreistellen.zeichenNachHelligkeit(t.px)
        fun deckend(x: Int, y: Int) = (m[(y - 7) * t.breite + (x - 7)] ushr 24) > 128
        assertTrue(deckend(30, 30))
        assertFalse(deckend(10, 10))
    }

    // Kachel mit Farbverlauf: das weisse Zeichen ist die kleinere Helligkeitsgruppe, der Verlauf faellt ganz weg
    @Test fun zeichenNachHelligkeitTrenntVerlauf() {
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            if (x in 20..39 && y in 22..37) weiss
            else { val b = 120 + x * 2; (0xFF shl 24) or (20 shl 16) or ((40 + y) shl 8) or b } }
        val m = LogoFreistellen.zeichenNachHelligkeit(px)
        assertTrue((m[30 * g + 30] ushr 24) > 200)
        assertTrue((m[5 * g + 5] ushr 24) < 30)
        assertTrue((m[55 * g + 55] ushr 24) < 30)
    }

    // Gelbe Flaeche, weisser Kranz, gruener Winkel (drei Toene): Weiss ist Papier, alles Farbige Tinte
    @Test fun mehrereFarbtoeneWerdenTinte() {
        val gelb = 0xFFFFC94A.toInt(); val gruen = 0xFF007A5E.toInt()
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            if (x >= 50 || y >= 50) gruen else if (x in 15..40 && y in 15..40) weiss else gelb }
        assertTrue(LogoFreistellen.mehrereFarbtoene(px))
        val m = LogoFreistellen.zeichenNachHelligkeit(px)
        assertTrue((m[5 * g + 5] ushr 24) > 200)
        assertTrue((m[55 * g + 55] ushr 24) > 200)
        assertTrue((m[30 * g + 30] ushr 24) < 30)
        val sw = LogoFreistellen.schwarzWeissNachHelligkeit(px)
        assertEquals(0, sw[5 * g + 5] and 0xFF)
        assertEquals(255, sw[30 * g + 30] and 0xFF)
    }

    // Verlauf in einem Ton mit weissem Zeichen zaehlt als ein Farbton
    @Test fun verlaufInEinemTonIstEinFarbton() {
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            if (x in 20..39 && y in 22..37) weiss
            else (0xFF shl 24) or ((20 + x) shl 16) or ((40 + y) shl 8) or (150 + x) }
        assertFalse(LogoFreistellen.mehrereFarbtoene(px))
    }

    // Foto: viele Farbtoene, auch vor weissem Grund
    @Test fun fotoIstKeinSymbol() {
        val px = IntArray(g * g) { val x = it % g; val y = it / g
            if (x < 6 || y < 6 || x > g - 7 || y > g - 7) weiss
            else (0xFF shl 24) or ((x * 4 + y) % 256 shl 16) or ((y * 3 + x * 2) % 256 shl 8) or ((x * y) % 256) }
        assertFalse(LogoFreistellen.einfachesSymbol(px, g, g))
    }

    // Breites Bild (Flaggenformat) gilt nie als Symbol
    @Test fun breitesBildIstKeinSymbol() {
        val b = 90; val h = 60
        val px = IntArray(b * h) { val x = it % b; val y = it / b
            if ((x - 45) * (x - 45) + (y - 30) * (y - 30) < 300) 0xFFBC002D.toInt() else weiss }
        assertFalse(LogoFreistellen.einfachesSymbol(px, b, h))
    }
}
