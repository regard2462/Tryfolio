package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Rechenregeln fuer den erweiterten Chart [0.77]: Darstellung, Marken in der Achsenspalte, Ausweichen der Achsenzahlen,
// Kerzengeometrie in ganzen Pixeln. Reine Funktionen ohne Zeichnen.
object ChartLayout {

    val ARTEN = listOf("kerzen", "linie", "heikin")

    // Gespeicherte Wahl oder, solange keine getroffen ist, Heikin-Ashi.
    fun art(wahl: String): String = if (wahl in ARTEN) wahl else "heikin"

    // Linke Kante eines Achsentexts: rechts neben dem Zeichenbereich, bei Platzmangel nach links geschoben, damit der
    // Text innerhalb der Gesamtbreite bleibt.
    fun achsenTextX(zeichenBreite: Float, gesamt: Float, textBreite: Float, abstand: Float, rand: Float): Float =
        min(zeichenBreite + abstand, gesamt - textBreite - rand).coerceAtLeast(0f)

    // Senkrechte Mitten von Marken, die sich nicht ueberdecken: sortiert nach Soll, mit mindestens hoehe Abstand,
    // innerhalb von 0 bis gesamt. Rueckgabe in der Reihenfolge der Eingabe.
    fun markenMitten(soll: List<Float>, hoehe: Float, gesamt: Float): List<Float> {
        if (soll.isEmpty()) return emptyList()
        val ordnung = soll.indices.sortedBy { soll[it] }
        val ys = FloatArray(soll.size)
        val unten = gesamt - hoehe / 2f
        val oben = hoehe / 2f
        var vorher = Float.NEGATIVE_INFINITY
        ordnung.forEachIndexed { k, i ->
            var y = max(soll[i], oben)
            if (k > 0) y = max(y, vorher + hoehe)
            ys[k] = y; vorher = y
        }
        // Reicht der Platz unten nicht, rueckwaerts nach oben schieben
        var grenze = unten
        for (k in ordnung.indices.reversed()) {
            if (ys[k] > grenze) ys[k] = grenze
            grenze = ys[k] - hoehe
        }
        val ergebnis = FloatArray(soll.size)
        ordnung.forEachIndexed { k, i -> ergebnis[i] = ys[k] }
        return ergebnis.toList()
    }

    // Welche Achsenzahlen sichtbar bleiben: eine Zahl weicht, sobald ihre Zeile eine Marke beruehrt.
    fun achsenSichtbar(achsenY: List<Float>, achsenHoehe: Float, markenY: List<Float>, markenHoehe: Float): List<Boolean> =
        achsenY.map { y -> markenY.none { abs(it - y) < (achsenHoehe + markenHoehe) / 2f } }

    // Ob eine Bezeichnung samt Wert noch in die Achsenspalte passt; sonst steht sie in der Kopfzeile.
    fun bezeichnungPasst(spalte: Float, wertBreite: Float, bezeichnungBreite: Float, abstand: Float): Boolean =
        wertBreite + bezeichnungBreite + abstand <= spalte

    // Kaufkurs fuer die Ringmarke: der gebuchte Kurs, in die Spanne der Kerze gezogen; ohne Kurs der Schluss.
    fun markenKurs(kurs: Double, tief: Double, hoch: Double, schluss: Double): Double =
        if (kurs.isFinite() && kurs > 0) kurs.coerceIn(min(tief, hoch), max(tief, hoch)) else schluss

    // Waagerechte Lage einer Kerzenspalte in ganzen Pixeln. links und breite gelten fuer den Koerper, dochtLinks und
    // dochtBreite fuer den Docht, mittig im Koerper.
    class Spalte(val links: Float, val breite: Float, val dochtLinks: Float, val dochtBreite: Float)

    // mitte: Mitte der Kerze, schritt: Abstand zweier Kerzen, dp: ein dp in Pixeln. Koerper etwa 70 % des Abstands,
    // Docht 1 dp. Unter 3 px Abstand fuellt der Koerper die Zelle, Nachbarn grenzen ohne Luecke aneinander.
    fun spalte(mitte: Float, schritt: Float, dp: Float): Spalte {
        val eins = max(1, dp.roundToInt()).toFloat()
        if (schritt < 3f) {
            val l = floor(mitte - schritt / 2f)
            val r = floor(mitte + schritt / 2f)
            val b = max(1f, r - l)
            val d = if (b > 1f && eins < b) 1f else b
            return Spalte(l, b, l + floor((b - d) / 2f), d)
        }
        val b = max(1f, (schritt * 0.7f).roundToInt().toFloat())
        val l = (mitte - b / 2f).roundToInt().toFloat()
        var d = min(eins, b)
        if (((b - d).toInt() % 2) != 0 && d < b) d += 1f
        return Spalte(l, b, l + (b - d) / 2f, d)
    }

    // Hoehe eines Kerzenkoerpers: mindestens 1 dp, damit ein Doji als Strich bleibt.
    fun koerperHoehe(oben: Float, unten: Float, dp: Float): Float = max(unten - oben, max(1f, dp))
}
