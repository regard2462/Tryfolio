package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.LmeLager
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate

// Lagerbestaende der LME. Je Monat eine Excel-Datei, die die Person selbst von der Seite der Boerse laedt und mit der App
// oeffnet; gelesen wird sie in engine/LmeLager.kt und je Monat als kleine Textdatei im Ordner der App gespeichert.
// Die App ruft nichts bei der Boerse ab. Eine weitere Boerse bekommt eigenen Leser und eigenen Ordner neben diesem.
class LmeLagerAbruf(private val api: MarketApi, private val ordner: File, private val jetzt: () -> Long = { System.currentTimeMillis() }) {

    companion object {
        // Hoechstens so viele Monate zurueck suchen: zwoelf Dateien, dazu eine, falls der Vormonat noch fehlt
        const val SUCHE = LmeLager.MONATE + 1

        // Der laufende Monat zaehlt zusaetzlich zu den zwoelf Monaten davor
        const val GELADEN = LmeLager.MONATE + 1
    }

    private val sperre = Mutex()

    private fun datei(m: LmeLager.Monat) = File(ordner, "stocks-${m.schluessel}.txt")

    private fun gespeichert(m: LmeLager.Monat): List<LmeLager.Tag>? {
        val f = datei(m)
        if (!f.exists()) return null
        return runCatching { LmeLager.laden(SichereDatei(f).lesen().toString(Charsets.UTF_8)) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    // Liest eine von der Person gewaehlte Monatsdatei, verteilt die Tage auf ihre Monate und fuegt sie dem Gespeicherten
    // des Monats hinzu (gleiche Tage werden ersetzt, andere Tage des Monats bleiben, auch wenn die Datei nur einen
    // Nachbartag des Monats enthaelt). Rueckgabe: Zahl der gelesenen Handelstage, 0 wenn die Datei nicht erkannt wird.
    fun einlesen(bytes: ByteArray): Int {
        val tage = LmeLager.lesen(bytes)
        if (tage.isEmpty()) return 0
        val monate = tage.groupBy { LocalDate.ofEpochDay(it.tag).let { d -> LmeLager.Monat(d.year, d.monthValue) } }
        runCatching { ordner.mkdirs() }
        for ((m, t) in monate) runCatching {
            val gesamt = LmeLager.zusammenfuehren(gespeichert(m).orEmpty(), t)
            SichereDatei(datei(m)).schreiben(LmeLager.speichern(gesamt).toByteArray(Charsets.UTF_8))
        }
        return tage.size
    }

    // Zustand nach einem Abruf: Tage aller gespeicherten Monate und ob der Server den Zugriff verweigert hat.
    class Ergebnis(val tage: List<LmeLager.Tag>, val gesperrt: Boolean)

    // Tage aus den gespeicherten Dateien: der laufende Monat und bis zu zwoelf Monate davor, ein Tag nur einmal.
    suspend fun laden(heute: LocalDate = LocalDate.now()): Ergebnis = sperre.withLock {
        val monate = listOf(LmeLager.Monat(heute.year, heute.monthValue)) + LmeLager.monate(heute, SUCHE)
        val alle = monate.mapNotNull { gespeichert(it) }.take(GELADEN)
        Ergebnis(LmeLager.zusammenfuehren(emptyList(), alle.flatten()), false)
    }
}
