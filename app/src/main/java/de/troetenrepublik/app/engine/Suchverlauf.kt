package de.troetenrepublik.app.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Suchverlauf (0.68): aus der Suche geoeffnete Werte mit Zaehler und letztem Zeitpunkt.
object Suchverlauf {
    const val MAX_GESPEICHERT = 20
    const val MAX_ANGEZEIGT = 5
    const val ZULETZT_PLAETZE = 3
    private const val HALBWERT_TAGE = 30.0
    private const val TAG = 86_400_000L

    // Ein Wert im Verlauf: Symbol, Anzeigename, Suchanzahl, letzter Zeitpunkt in Millisekunden.
    data class Eintrag(val symbol: String, val name: String, val anzahl: Int, val zuletzt: Long)

    // Gespeicherten Text lesen, je Zeile "symbol\tname\tanzahl\tzuletzt"; fehlerhafte Zeilen werden uebersprungen.
    fun lesen(text: String): List<Eintrag> {
        val erg = mutableListOf<Eintrag>()
        for (zeile in text.split('\n')) {
            if (zeile.isBlank()) continue
            val teile = zeile.split('\t')
            if (teile.size < 4) continue
            val symbol = teile[0].trim()
            val anzahl = teile[2].trim().toIntOrNull() ?: continue
            val zuletzt = teile[3].trim().toLongOrNull() ?: continue
            if (symbol.isEmpty() || anzahl < 1) continue
            // Bei doppeltem Symbol bleibt der erste Eintrag
            if (erg.any { it.symbol == symbol }) continue
            erg.add(Eintrag(symbol, teile[1].trim(), anzahl, zuletzt))
        }
        return erg
    }

    // Gegenstueck zu lesen; Tabulatoren und Zeilenumbrueche in symbol und name werden zu Leerzeichen.
    fun text(liste: List<Eintrag>): String {
        fun rein(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
        return liste.joinToString("\n") { rein(it.symbol) + "\t" + rein(it.name) + "\t" + it.anzahl + "\t" + it.zuletzt }
    }

    // Gewicht aus Suchanzahl und Alter, aeltere Suchen zaehlen wegen der Halbwertszeit weniger.
    fun gewicht(e: Eintrag, jetzt: Long): Double {
        val alterTage = max(0L, jetzt - e.zuletzt) / TAG.toDouble()
        return e.anzahl * 0.5.pow(alterTage / HALBWERT_TAGE)
    }

    // Oeffnen merken: vorhandenen Eintrag erhoehen oder neu anlegen, auf MAX_GESPEICHERT begrenzen.
    fun merken(liste: List<Eintrag>, symbol: String, name: String, jetzt: Long): List<Eintrag> {
        val s = symbol.trim()
        if (s.isEmpty()) return liste
        val n = name.trim()
        val alt = liste.firstOrNull { it.symbol == s }
        val neu = if (alt == null) Eintrag(s, n, 1, jetzt) else alt.copy(anzahl = alt.anzahl + 1, zuletzt = jetzt, name = n.ifEmpty { alt.name })
        val ohne = liste.filter { it.symbol != s }
        var erg = ohne + neu
        if (erg.size > MAX_GESPEICHERT) {
            // Kleinste Gewichte fallen heraus, bei Gleichstand der aeltere; der eben gemerkte bleibt
            val uebrig = ohne.sortedWith(compareBy({ gewicht(it, jetzt) }, { it.zuletzt })).drop(erg.size - MAX_GESPEICHERT)
            erg = uebrig + neu
        }
        return erg.sortedByDescending { it.zuletzt }
    }

    // Eintrag zu diesem Symbol streichen.
    fun entfernen(liste: List<Eintrag>, symbol: String): List<Eintrag> = liste.filter { it.symbol != symbol.trim() }

    // Anzeige-Reihenfolge: die juengsten ZULETZT_PLAETZE zuerst, Restplaetze nach Gewicht.
    fun rangfolge(liste: List<Eintrag>, jetzt: Long, max: Int = MAX_ANGEZEIGT): List<Eintrag> {
        if (max <= 0 || liste.isEmpty()) return emptyList()
        val letzte = liste.sortedByDescending { it.zuletzt }.take(min(max, ZULETZT_PLAETZE))
        val letzteSymbole = letzte.map { it.symbol }.toSet()
        val rest = liste.filter { it.symbol !in letzteSymbole }
        val nachGewicht = rest.sortedWith(compareBy({ -gewicht(it, jetzt) }, { it.zuletzt })).take(max - letzte.size)
        return letzte + nachGewicht
    }
}
