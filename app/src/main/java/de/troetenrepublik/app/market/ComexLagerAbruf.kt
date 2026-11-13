package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.ComexLager
import de.troetenrepublik.app.plattform.SichereDatei
import java.io.File

// Lagerbestaende der COMEX. Je Metall eine Excel-Datei (.xls), die die Person selbst im Browser laedt und mit der App
// oeffnet; gelesen wird sie in engine/ComexLager.kt und je Metall als kleine Textdatei im Ordner der App gespeichert, mit
// einer Zeile je Berichtstag. Die App ruft nichts bei der Boerse ab. Ein eigener Abruf kann einlesen(bytes) mit den
// geladenen Bytes aufrufen.
class ComexLagerAbruf(private val ordner: File) {

    private val sperre = Any()

    private fun datei(metall: String) = File(ordner, "$metall.txt")

    private fun gespeichert(metall: String): List<ComexLager.Eintrag> {
        val f = datei(metall)
        if (!f.exists()) return emptyList()
        return runCatching { ComexLager.laden(metall, SichereDatei(f).lesen().toString(Charsets.UTF_8)) }.getOrDefault(emptyList())
    }

    // Liest eine von der Person gewaehlte Datei und fuegt jeden Metallabschnitt als Berichtstag seinem Metall hinzu (ein
    // erneut gelesener Tag ersetzt den alten). Rueckgabe: Zahl der gespeicherten Metallabschnitte, 0 wenn die Datei nicht
    // erkannt wird.
    fun einlesen(bytes: ByteArray): Int = synchronized(sperre) {
        val neu = ComexLager.lesen(bytes)
        if (neu.isEmpty()) return 0
        runCatching { ordner.mkdirs() }
        var n = 0
        for (e in neu) {
            val alle = ComexLager.zusammenfuehren(gespeichert(e.metall), e)
            if (runCatching { SichereDatei(datei(e.metall)).schreiben(ComexLager.speichern(alle).toByteArray(Charsets.UTF_8)) }.isSuccess) n++
        }
        n
    }

    // Alle gespeicherten Tage je Metall, ohne Eintrag fuer Metalle ohne Daten.
    fun laden(): Map<String, List<ComexLager.Eintrag>> = synchronized(sperre) {
        ComexLager.REIHENFOLGE.associateWith { gespeichert(it) }.filterValues { it.isNotEmpty() }
    }
}
