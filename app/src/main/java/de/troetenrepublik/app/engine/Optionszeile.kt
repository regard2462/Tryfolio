package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.roundToLong

// Aufbau der Optionszeilen in den Werkzeugen und auf der Wertpapierseite: Name eines Werts, Nachkommastellen der
// Praemie, Warnschwelle der Geld-Brief-Spanne und die Aufteilung von Bezeichnung-Wert-Paaren auf Spalten.
object Optionszeile {

    // Ab dieser Spanne zwischen Geld und Brief (Anteil an der Mitte) gilt ein Kontrakt als schwer handelbar.
    const val SPANNE_WARNUNG = 0.20

    fun spanneHoch(spanne: Double?): Boolean = spanne != null && spanne > SPANNE_WARNUNG

    // Zwei Nachkommastellen, wenn der Preis nicht feiner notiert; sonst so viele, wie er traegt, hoechstens vier.
    fun praemieStellen(v: Double): Int {
        var faktor = 100.0
        for (n in 2..3) {
            val x = abs(v) * faktor
            if (abs(x - x.roundToLong()) < 1e-6) return n
            faktor *= 10
        }
        return 4
    }

    // Name fuer den Kopf: Katalog, sonst der Name der Position, sonst der aus dem Kurs. Das Kuerzel steht nur da,
    // wenn kein Name bekannt ist.
    fun name(symbol: String, katalog: String?, position: String?, kurs: String?): String =
        listOf(katalog, position, kurs).firstOrNull { !it.isNullOrBlank() && it != symbol } ?: symbol

    // Paare je Zeile: alle nebeneinander, sonst die Haelfte, sonst zwei, sonst eines (Bezeichnung links, Wert rechts).
    // breiten: Platzbedarf je Paar (das breitere von Bezeichnung und Wert), gesamt: Breite der Zeile, abstand:
    // Luecke zwischen zwei Spalten. Die Spalten sind gleich breit; es passt, wenn das breiteste Paar in eine passt.
    fun spalten(breiten: List<Int>, gesamt: Int, abstand: Int): Int {
        val n = breiten.size
        if (n <= 1) return 1
        val groesste = breiten.max()
        return listOf(n, (n + 1) / 2, 2).distinct().firstOrNull { k -> k > 1 && (gesamt - (k - 1) * abstand) / k >= groesste } ?: 1
    }

    // Tabelle mit festen Spalten: passt, wenn alle Spalten in ihrer groessten Breite samt Luecken nebeneinander stehen.
    fun tabellePasst(spalten: List<Int>, gesamt: Int, abstand: Int): Boolean =
        spalten.isNotEmpty() && spalten.sum() + (spalten.size - 1) * abstand <= gesamt
}
