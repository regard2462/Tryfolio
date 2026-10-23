package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Sperre
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.ui.ersterSatz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Kuerzen der Buchungen, erster Satz der aufklappbaren Hinweise, Zeichencodes in Feeds, Tausch und Fehlversuche.
class Fassung48Test {

    private fun tx(i: Int, typ: TxType) = Transaction("t$i", i * 1000L, typ, "Buchung $i", amount = 1.0)

    @Test
    fun kuerzenBehaeltDividenden() {
        // 3002 Buchungen, die aelteste und die dritte sind Dividenden: beide bleiben, dafuer fallen die zwei aeltesten uebrigen weg
        val liste = (0 until 3002).map { i -> tx(i, if (i == 0 || i == 2) TxType.DIVIDEND else TxType.DEPOSIT) }
        val gekuerzt = Trading.kuerzen(liste.shuffled())
        assertEquals(3000, gekuerzt.size)
        assertTrue(gekuerzt.any { it.id == "t0" })
        assertTrue(gekuerzt.any { it.id == "t2" })
        assertFalse(gekuerzt.any { it.id == "t1" })
        assertFalse(gekuerzt.any { it.id == "t3" })
        assertEquals(gekuerzt.sortedBy { it.ts }, gekuerzt)
        // Unter der Grenze bleibt alles, nur sortiert
        assertEquals(3, Trading.kuerzen(listOf(tx(2, TxType.BUY), tx(1, TxType.SELL), tx(3, TxType.DIVIDEND))).size)
    }

    @Test
    fun ersterSatzTrenntNurEchteSaetze() {
        val text = "Die Gründe sind Vermutungen, die aus den Schlagzeilen des Tages entstehen. Ohne passende Schlagzeile steht da, ob der Wert mit seinem Markt lief. Keine Empfehlung."
        val (kurz, rest) = ersterSatz(text)
        assertEquals("Die Gründe sind Vermutungen, die aus den Schlagzeilen des Tages entstehen.", kurz)
        assertTrue(rest.startsWith("Ohne passende"))
        // Abkuerzungen und kurze Texte bleiben ganz
        assertEquals("" , ersterSatz("Zinsen von 2 % p. a. gelten ab heute.").second)
        assertEquals("", ersterSatz("Kurz. Und noch kürzer.").second)
        val lang = "Das ist z. B. ein Satz mit einer Abkürzung mitten im Text und genug Länge. Danach folgt ein zweiter Satz, der auch lang genug ist."
        assertEquals("Das ist z. B. ein Satz mit einer Abkürzung mitten im Text und genug Länge.", ersterSatz(lang).first)
    }

    @Test
    fun rssIgnoriertUngueltigeZeichencodes() {
        assertEquals("A&#1114112;B", Rss.klartext("A&#1114112;B"))
        assertEquals("AäB", Rss.klartext("A&#228;B"))
        assertEquals("AäB", Rss.klartext("A&#xE4;B"))
    }

    @Test
    fun tauschIstKeinGeldVonAussen() {
        val tausch = Transaction("x", 1L, TxType.BUY, "Neu", amount = 0.0, qty = 10.0, price = 5.0, tag = "EXCHANGE_IN")
        assertEquals(0.0, Performanceanalyse.vonAussen(tausch), 1e-9)
        val uebernommen = tausch.copy(tag = "TRANSFER_IN")
        assertEquals(50.0, Performanceanalyse.vonAussen(uebernommen), 1e-9)
    }

    @Test
    fun fehlversucheUeberlebenNeustart() {
        val dir = File(System.getProperty("java.io.tmpdir"), "troete-sperre-" + System.nanoTime()).apply { mkdirs() }
        try {
            val s = Sperre(dir)
            s.setzen("1234", Sperre.PIN, ByteArray(32) { it.toByte() })
            s.fehlversucheMerken(3, 123_456L)
            val neu = Sperre(dir)
            assertEquals(3, neu.fehlversuche)
            assertEquals(123_456L, neu.wartenBis)
            assertNotNull(neu.oeffnen("1234"))
            neu.fehlversucheMerken(0, 0L)
            assertEquals(0, Sperre(dir).fehlversuche)
        } finally {
            dir.deleteRecursively()
        }
    }
}
