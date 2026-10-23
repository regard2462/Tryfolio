package de.troetenrepublik.app.engine

// Flaechen fuer die Heatmap: „squarified treemap“ nach Bruls, Huizing und van Wijk. Jeder Wert bekommt
// eine Flaeche im Verhaeltnis zu seinem Gewicht; die Kacheln werden so gelegt, dass sie moeglichst quadratisch sind.
object Treemap {

    data class Rechteck(val x: Float, val y: Float, val w: Float, val h: Float) {
        operator fun contains(p: Pair<Float, Float>): Boolean = p.first >= x && p.first < x + w && p.second >= y && p.second < y + h
    }

    // Rechtecke fuer gewichte in einer Flaeche breite x hoehe ab (x, y), in der Reihenfolge der Eingabe.
    // Gewichte <= 0 (oder ungueltige) bekommen ein leeres Rechteck.
    fun anordnen(gewichte: List<Double>, breite: Float, hoehe: Float, x: Float = 0f, y: Float = 0f): List<Rechteck> {
        val aus = MutableList(gewichte.size) { Rechteck(x, y, 0f, 0f) }
        val gueltig = gewichte.indices.filter { gewichte[it].isFinite() && gewichte[it] > 0 }.sortedByDescending { gewichte[it] }
        val summe = gueltig.sumOf { gewichte[it] }
        if (gueltig.isEmpty() || breite <= 0f || hoehe <= 0f || summe <= 0) return aus
        val flaeche = breite.toDouble() * hoehe
        val f = gueltig.map { gewichte[it] / summe * flaeche }

        var rx = x.toDouble(); var ry = y.toDouble(); var rw = breite.toDouble(); var rh = hoehe.toDouble()
        var i = 0
        while (i < f.size) {
            val kurz = minOf(rw, rh)
            // Reihe so lange verlaengern, wie das schlechteste Seitenverhaeltnis besser wird
            var ende = i + 1
            var reihe = f[i]
            var schlechtest = schlechtestes(f, i, ende, reihe, kurz)
            while (ende < f.size) {
                val neu = schlechtestes(f, i, ende + 1, reihe + f[ende], kurz)
                if (neu > schlechtest) break
                reihe += f[ende]; schlechtest = neu; ende++
            }
            // Reihe entlang der kurzen Seite legen
            val dicke = if (kurz > 0) reihe / kurz else 0.0
            var pos = 0.0
            for (k in i until ende) {
                val laenge = if (dicke > 0) f[k] / dicke else 0.0
                aus[gueltig[k]] = if (rw >= rh) Rechteck(rx.toFloat(), (ry + pos).toFloat(), dicke.toFloat(), laenge.toFloat())
                else Rechteck((rx + pos).toFloat(), ry.toFloat(), laenge.toFloat(), dicke.toFloat())
                pos += laenge
            }
            if (rw >= rh) { rx += dicke; rw -= dicke } else { ry += dicke; rh -= dicke }
            i = ende
        }
        return aus
    }

    // Schlechtestes Seitenverhaeltnis (>= 1) der Flaechen f[von, bis) mit Summe s, gelegt entlang einer Seite w.
    private fun schlechtestes(f: List<Double>, von: Int, bis: Int, s: Double, w: Double): Double {
        var max = 0.0
        var min = Double.MAX_VALUE
        for (k in von until bis) { if (f[k] > max) max = f[k]; if (f[k] < min) min = f[k] }
        if (s <= 0 || min <= 0 || w <= 0) return Double.MAX_VALUE
        val w2 = w * w
        val s2 = s * s
        return maxOf(w2 * max / s2, s2 / (w2 * min))
    }
}
