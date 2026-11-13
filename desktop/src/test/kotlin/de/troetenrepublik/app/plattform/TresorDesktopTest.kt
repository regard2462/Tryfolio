package de.troetenrepublik.app.plattform

import de.troetenrepublik.app.data.TresorLesen
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Tresor: Eintragsname als AAD, alte Eintraege ohne AAD, Schluesselschutz mit Uebernahme der Klartextdatei.
class TresorDesktopTest {
    // Umkehrbarer Ersatz fuer DPAPI
    private class Umkehr : Tresor.Schluesselschutz {
        var kaputt = false
        override fun schuetzen(klar: ByteArray): ByteArray {
            check(!kaputt) { "kaputt" }
            return klar.map { (it.toInt() xor 0x5a).toByte() }.toByteArray() + byteArrayOf(1)
        }
        override fun oeffnen(geschuetzt: ByteArray): ByteArray =
            geschuetzt.copyOf(geschuetzt.size - 1).map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    }

    private val vorher = Tresor.schluesselSchutz
    private lateinit var dir: File
    private val tresor get() = File(dir, "tresor")

    @Before fun an() {
        dir = Files.createTempDirectory("tresor-test").toFile()
        Plattform.init(dir)
        Tresor.zuruecksetzen(null)
    }

    @After fun ab() {
        Tresor.zuruecksetzen(vorher)
        dir.deleteRecursively()
    }

    private fun wert(name: String) = (Tresor.lesen(name) as? TresorLesen.Wert)?.text

    // Datei im Aufbau der Fassung 0.75: kein AAD
    private fun alteDatei(name: String, schluessel: ByteArray, wert: String) {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(schluessel, "AES"), GCMParameterSpec(128, iv))
        File(tresor, "$name.bin").writeBytes(iv + c.doFinal(wert.toByteArray()))
    }

    @Test fun schreibenUndLesen() {
        Tresor.schreiben("schluessel_a", "geheim")
        assertEquals("geheim", wert("schluessel_a"))
        Tresor.schreiben("schluessel_a", null)
        assertEquals(TresorLesen.Leer, Tresor.lesen("schluessel_a"))
    }

    @Test fun eintragIstAnSeinenNamenGebunden() {
        Tresor.schreiben("schluessel_a", "geheim")
        Tresor.schreiben("schluessel_b", "anders")
        File(tresor, "schluessel_a.bin").copyTo(File(tresor, "schluessel_b.bin"), overwrite = true)
        assertEquals(TresorLesen.Fehler, Tresor.lesen("schluessel_b"))
        assertEquals("geheim", wert("schluessel_a"))
    }

    @Test fun alterEintragOhneAadWirdGelesenUndNeuGeschrieben() {
        Tresor.schreiben("schluessel_a", "x")
        val schluessel = File(tresor, "schluessel").readBytes()
        alteDatei("schluessel_alt", schluessel, "alt-wert")
        val vorherBytes = File(tresor, "schluessel_alt.bin").readBytes()
        assertEquals("alt-wert", wert("schluessel_alt"))
        val danach = File(tresor, "schluessel_alt.bin").readBytes()
        assertFalse(vorherBytes.contentEquals(danach))
        // Jetzt mit AAD: unter anderem Namen nicht mehr lesbar, unter dem eigenen weiterhin
        assertFalse(Tresor.entschluesseln(SecretKeySpec(schluessel, "AES"), "schluessel_alt", danach).second)
        assertEquals("alt-wert", wert("schluessel_alt"))
        assertEquals("x", wert("schluessel_a"))
    }

    @Test fun neuerEintragTraegtAad() {
        Tresor.schreiben("schluessel_a", "geheim")
        val k = SecretKeySpec(File(tresor, "schluessel").readBytes(), "AES")
        val b = File(tresor, "schluessel_a.bin").readBytes()
        val (w, ohne) = Tresor.entschluesseln(k, "schluessel_a", b)
        assertEquals("geheim", w); assertFalse(ohne)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, b.copyOfRange(0, 12)))
        assertTrue(runCatching { c.doFinal(b.copyOfRange(12, b.size)) }.isFailure)
    }

    @Test fun klartextSchluesselWirdGeschuetztUebernommenUndGeloescht() {
        Tresor.schreiben("schluessel_a", "geheim")
        val klar = File(tresor, "schluessel").readBytes()
        assertEquals(32, klar.size)
        // Neustart mit Schutz
        Tresor.zuruecksetzen(Umkehr())
        assertEquals("geheim", wert("schluessel_a"))
        assertFalse(File(tresor, "schluessel").exists())
        assertTrue(File(tresor, "schluessel.dpapi").exists())
        assertFalse(File(tresor, "schluessel.dpapi").readBytes().copyOf(32).contentEquals(klar))
        // Nochmals neu starten: Schluessel kommt aus der geschuetzten Datei
        Tresor.zuruecksetzen(Umkehr())
        assertEquals("geheim", wert("schluessel_a"))
        Tresor.schreiben("schluessel_b", "zwei")
        assertEquals("zwei", wert("schluessel_b"))
    }

    @Test fun scheitertDerSchutzBleibtDieKlartextDatei() {
        Tresor.schreiben("schluessel_a", "geheim")
        Tresor.zuruecksetzen(Umkehr().apply { kaputt = true })
        assertEquals("geheim", wert("schluessel_a"))
        assertTrue(File(tresor, "schluessel").exists())
        assertFalse(File(tresor, "schluessel.dpapi").exists())
    }

    @Test fun neuerSchluesselMitSchutzLiegtNurGeschuetzt() {
        Tresor.zuruecksetzen(Umkehr())
        Tresor.schreiben("schluessel_a", "geheim")
        assertFalse(File(tresor, "schluessel").exists())
        assertTrue(File(tresor, "schluessel.dpapi").exists())
        Tresor.zuruecksetzen(Umkehr())
        assertEquals("geheim", wert("schluessel_a"))
    }

    @Test fun echtesDpapiUnterWindows() {
        assumeTrue(System.getProperty("os.name").orEmpty().startsWith("Windows"))
        Tresor.zuruecksetzen(vorher)
        Tresor.schreiben("schluessel_a", "geheim")
        assertFalse(File(tresor, "schluessel").exists())
        val datei = File(tresor, "schluessel.dpapi").readBytes()
        assertTrue(datei.size > 32)
        Tresor.zuruecksetzen(vorher)
        assertEquals("geheim", wert("schluessel_a"))
        assertArrayEquals(datei, File(tresor, "schluessel.dpapi").readBytes())
    }
}
