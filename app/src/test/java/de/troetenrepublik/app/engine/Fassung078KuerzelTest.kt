package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Erwaehnte Aktien (0.78): ein Kuerzel zaehlt nicht, wenn es als gewoehnliches Wort, als Behoerde oder in einem
// durchgehend gross geschriebenen Satz steht.
class Fassung078KuerzelTest {
    private val katalog = listOf("ICE" to "Intercontinental Exchange", "NOW" to "ServiceNow", "NVDA" to "NVIDIA", "MSFT" to "Microsoft", "AMD" to "Advanced Micro Devices")

    @Test fun grossGeschriebenerTextNenntKeineKuerzel() {
        val text = "MEXICO MUST STOP THEM, NOW, and if they do not do so, the Tariffs on Mexico will be IMMEDIATELY DOUBLED. OUR GREAT BORDER PATROL, & ICE, HAVE BEEN SO INFORMED."
        assertTrue(NachrichtEinordnung.erwaehnt(listOf(text), katalog).isEmpty())
        assertFalse(NachrichtEinordnung.kuerzelGenannt("Buy NOW and save", "NOW"))
        assertFalse(NachrichtEinordnung.kuerzelGenannt("ICE agents arrived", "ICE"))
        assertFalse(NachrichtEinordnung.kuerzelGenannt("WE LOVE AMD CHIPS", "AMD"))
    }

    @Test fun gekennzeichneteUndEindeutigeKuerzelZaehlen() {
        assertTrue(NachrichtEinordnung.kuerzelGenannt("ServiceNow (NYSE: NOW) raised guidance", "NOW"))
        assertTrue(NachrichtEinordnung.kuerzelGenannt("Shares of \$ICE rose", "ICE"))
        assertTrue(NachrichtEinordnung.kuerzelGenannt("the exchange operator (ICE) said", "ICE"))
        assertTrue(NachrichtEinordnung.kuerzelGenannt("Analysts lifted NVDA after the report", "NVDA"))
        assertEquals(listOf("ICE", "NOW"), NachrichtEinordnung.erwaehnt(listOf("Intercontinental Exchange and ServiceNow report today"), katalog).map { it.first })
        assertEquals(listOf("AMD"), NachrichtEinordnung.erwaehnt(listOf("Chipmaker AMD gains share"), katalog).map { it.first })
    }
}
