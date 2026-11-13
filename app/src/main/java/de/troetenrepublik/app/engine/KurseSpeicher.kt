package de.troetenrepublik.app.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// Zwischenspeicher der Tagesschlusskurse fuer die Uebersicht im Copytrading: je Aktie die Kurse, der Zeitpunkt des
// Abrufs und wie viele Tage zurueck sie reichen. Eine leere Reihe heisst: Yahoo kennt die Aktie nicht (wird bis zum
// Ablauf nicht neu gefragt). Binaerformat, gzip-gepackt, damit tausend Aktien auf dem Geraet wenig Platz brauchen.
object KurseSpeicher {

    private const val KENNUNG = 0x4B533031
    private const val MAX_EINTRAEGE = 20_000
    private const val MAX_PUNKTE = 5_000

    class Eintrag(val symbol: String, val am: Long, val tage: Int, val kurse: KongressAuswertung.Kurse)

    fun schreiben(eintraege: Collection<Eintrag>): ByteArray {
        val roh = ByteArrayOutputStream()
        GZIPOutputStream(roh).use { gz ->
            DataOutputStream(gz).use { o ->
                o.writeInt(KENNUNG)
                o.writeInt(eintraege.size)
                for (e in eintraege) {
                    o.writeUTF(e.symbol)
                    o.writeLong(e.am)
                    o.writeInt(e.tage)
                    val n = e.kurse.anzahl
                    o.writeInt(n)
                    for (i in 0 until n) o.writeInt(e.kurse.tagBei(i))
                    for (i in 0 until n) o.writeFloat(e.kurse.wertBei(i))
                }
            }
        }
        return roh.toByteArray()
    }

    // null bei fremdem Format oder beschaedigter Datei.
    fun lesen(bytes: ByteArray): List<Eintrag>? = runCatching {
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { i ->
            if (i.readInt() != KENNUNG) return@runCatching null
            val anzahl = i.readInt()
            if (anzahl !in 0..MAX_EINTRAEGE) return@runCatching null
            val aus = ArrayList<Eintrag>(anzahl)
            repeat(anzahl) {
                val symbol = i.readUTF()
                val am = i.readLong()
                val tage = i.readInt()
                val n = i.readInt()
                if (n !in 0..MAX_PUNKTE) return@runCatching null
                val t = IntArray(n) { i.readInt() }
                val w = FloatArray(n) { i.readFloat() }
                aus += Eintrag(symbol, am, tage, KongressAuswertung.Kurse(t, w))
            }
            aus
        }
    }.getOrNull()

    // Abruf-Bereich (Yahoo range) fuer so viele Tage zurueck, samt den Tagen, die er abdeckt.
    fun bereich(tageZurueck: Long): Pair<String, Int> = when {
        tageZurueck <= 150 -> "6mo" to 180
        tageZurueck <= 335 -> "1y" to 365
        tageZurueck <= 700 -> "2y" to 730
        else -> "5y" to 1825
    }

    // Frisch genug: nicht aelter als stunden und weit genug zurueck.
    fun frisch(e: Eintrag?, jetzt: Long, noetigeTage: Int, stunden: Int = 6): Boolean =
        e != null && jetzt - e.am in 0..(stunden * 3_600_000L) && e.tage >= noetigeTage
}
