package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.roundToInt

// Reine Logik des Watchlist-Blatts: Seite des Pagers und Watchlist gehoeren zusammen, das „+“ am Ende der Reiter
// ist keine Seite. Alle Indizes zaehlen ab 0.
object WatchBlatt {

    // Aktive Liste in den Grenzen der vorhandenen Listen.
    fun aktiv(gemerkt: Int, listen: Int): Int = gemerkt.coerceIn(0, (listen - 1).coerceAtLeast(0))

    // Seite des Pagers zu einem Reiter; das „+“ (Index == listen) und alles darueber ist keine Seite.
    fun seiteFuerReiter(reiter: Int, listen: Int): Int? = reiter.takeIf { it in 0 until listen }

    // Seite, auf der eine Wischgeste endet: ab der halben Seite oder bei genug Tempo (Pixel je Sekunde, positiv =
    // nach der naechsten Seite hin) rastet sie auf der Nachbarseite ein, nie ueber die Enden hinaus.
    fun zielSeite(seite: Int, anteil: Float, tempo: Float, listen: Int, schnell: Float = 600f): Int {
        val letzte = (listen - 1).coerceAtLeast(0)
        val pos = seite + anteil
        val ziel = when {
            abs(tempo) >= schnell && anteil != 0f -> if (tempo > 0f) seite + (if (anteil > 0f) 1 else 0) else seite + (if (anteil > 0f) 0 else -1)
            abs(tempo) >= schnell -> seite + (if (tempo > 0f) 1 else -1)
            else -> pos.roundToInt()
        }
        return ziel.coerceIn(0, letzte)
    }
}
