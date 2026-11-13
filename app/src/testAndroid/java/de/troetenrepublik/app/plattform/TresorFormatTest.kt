package de.troetenrepublik.app.plattform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Aufbau der Tresordatei auf Android: Eintragsname als AAD, alte Dateien ohne AAD bleiben lesbar.
class TresorFormatTest {
    private val k = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    private fun alt(wert: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k)
        return byteArrayOf(c.iv.size.toByte()) + c.iv + c.doFinal(wert.toByteArray())
    }

    @Test fun rundlaufMitAad() {
        val b = TresorFormat.verschluesseln(k, "schluessel_a", "geheim")
        val g = TresorFormat.entschluesseln(k, "schluessel_a", b)
        assertEquals("geheim", g.wert)
        assertFalse(g.ohneAad)
    }

    @Test fun andererNameWirdAbgelehnt() {
        val b = TresorFormat.verschluesseln(k, "schluessel_a", "geheim")
        assertTrue(runCatching { TresorFormat.entschluesseln(k, "schluessel_b", b) }.exceptionOrNull() is AEADBadTagException)
    }

    @Test fun alteDateiOhneAadWirdGelesenUndGemeldet() {
        val g = TresorFormat.entschluesseln(k, "schluessel_a", alt("alt-wert"))
        assertEquals("alt-wert", g.wert)
        assertTrue(g.ohneAad)
        // Neu geschrieben ist sie gebunden
        val neu = TresorFormat.verschluesseln(k, "schluessel_a", g.wert)
        assertFalse(TresorFormat.entschluesseln(k, "schluessel_a", neu).ohneAad)
        assertTrue(runCatching { TresorFormat.entschluesseln(k, "schluessel_b", neu) }.isFailure)
    }

    @Test fun veraenderteDatenScheitern() {
        val b = TresorFormat.verschluesseln(k, "schluessel_a", "geheim")
        b[b.size - 1] = (b[b.size - 1].toInt() xor 1).toByte()
        assertTrue(runCatching { TresorFormat.entschluesseln(k, "schluessel_a", b) }.isFailure)
    }
}
