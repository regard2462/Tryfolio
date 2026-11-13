package de.troetenrepublik.app.engine

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

// [0.73] Rechenwerk fuer Unterwasser-Diagramm, Renditeverteilung, rollierende Kennzahlen und das
// Rendite-Risiko-Streudiagramm. Reihen sind Zeitpunkt (ms) und Wert (Kurs oder Index), aufsteigend oder nicht;
// Tage zaehlen als Kalendertage (UTC), wie in Risiko.
object RisikoDiagramme {
    private const val TAG = 86_400_000L
    private const val JAHR = 365.25 * TAG

    // Unterwasser: Abstand zum bisherigen Hoechststand in Prozent (0 = neues Hoch, sonst negativ). Punkte ohne
    // positiven Wert fehlen.
    fun unterwasser(reihe: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        val aus = ArrayList<Pair<Long, Double>>(reihe.size)
        var hoch = 0.0
        for ((t, v) in reihe.sortedBy { it.first }) {
            if (!v.isFinite() || v <= 0.0) continue
            if (v > hoch) hoch = v
            aus += t to (v / hoch - 1.0) * 100.0
        }
        return aus
    }

    // Verteilung von Renditen in gleich breiten Klassen. Die Klassen beginnen bei untergrenze und sind breite breit,
    // anzahl[i] zaehlt Werte von rand(i) einschliesslich bis rand(i + 1) ausschliesslich. Was ausserhalb liegt, steht
    // in der ersten und letzten Klasse (unterlauf, ueberlauf zaehlen diese Werte).
    class Histogramm(val untergrenze: Double, val breite: Double, val anzahl: List<Int>, val unterlauf: Int, val ueberlauf: Int) {
        val n: Int get() = anzahl.sum()
        fun rand(i: Int): Double = untergrenze + i * breite
        val obergrenze: Double get() = rand(anzahl.size)
    }

    // Ab so vielen Renditen zeigt die Verteilung mehr als Zufall; gleich viele braucht der 5-%-Wert in Risiko.
    const val MIN_RENDITEN = 100

    private const val KLASSEN = 24

    // Klassenbreite aus 1, 2, 2,5 oder 5 mal einer Zehnerpotenz, die den Bereich in etwa klassen Klassen teilt
    // (die naechste Stufe darueber). So liegt die Null immer auf einem Rand.
    fun schoeneBreite(spanne: Double, klassen: Int = KLASSEN): Double {
        val roh = spanne / klassen
        if (!(roh > 0.0) || !roh.isFinite()) return 0.0
        val hoch = floor(log10(roh)).toInt()
        val zehner = 10.0.pow(hoch)
        val m = roh / zehner
        val stufe = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= m - 1e-9 }
        return stufe * zehner
    }

    // Der Bereich reicht vom 1. bis zum 99. Prozentpunkt der Renditen (und weiter, falls einschliessen es verlangt, etwa
    // VaR und ES), auf Klassenraender gerundet. Null bei weniger als minimum Renditen oder ohne Streuung.
    fun histogramm(
        renditen: List<Double>, breite: Double? = null, minimum: Int = MIN_RENDITEN, einschliessen: List<Double> = emptyList(),
    ): Histogramm? {
        val r = renditen.filter { it.isFinite() }
        if (r.size < minimum || r.isEmpty()) return null
        val sortiert = r.sorted()
        var lo = Risiko.quantil(sortiert, 0.01)
        var hi = Risiko.quantil(sortiert, 0.99)
        einschliessen.filter { it.isFinite() }.forEach { lo = minOf(lo, it); hi = maxOf(hi, it) }
        val w = breite ?: schoeneBreite(hi - lo)
        if (!(w > 0.0)) return null
        // Ein kleiner Zuschlag gegen Rundungsfehler: 0,06 / 0,01 ergibt 5,999...
        val eps = 1e-9
        val kLo = floor(lo / w + eps).toInt()
        var kHi = ceil(hi / w - eps).toInt()
        if (kHi <= kLo) kHi = kLo + 1
        val klassen = kHi - kLo
        val zaehler = IntArray(klassen)
        var unter = 0
        var ueber = 0
        for (v in r) {
            val k = floor(v / w + eps).toInt() - kLo
            when {
                k < 0 -> { zaehler[0]++; unter++ }
                k >= klassen -> { zaehler[klassen - 1]++; ueber++ }
                else -> zaehler[k]++
            }
        }
        return Histogramm(kLo * w, w, zaehler.toList(), unter, ueber)
    }

    // Rollierende Rendite ueber fensterTage Kalendertage: je Punkt der Wert gegen den letzten Punkt am oder vor dem Tag
    // fensterTage davor. Liegt dieser mehr als toleranzTage davor (Luecke in den Daten) oder gibt es keinen, fehlt der
    // Punkt. Anteil, 0,1 = 10 %.
    fun rollierendeRendite(reihe: List<Pair<Long, Double>>, fensterTage: Int = 365, toleranzTage: Int = 7): List<Pair<Long, Double>> {
        val r = Risiko.taeglich(reihe).filter { it.second.isFinite() && it.second > 0.0 }
        val aus = ArrayList<Pair<Long, Double>>()
        var j = 0
        for (i in r.indices) {
            val ziel = r[i].first - fensterTage * TAG
            while (j + 1 < r.size && r[j + 1].first <= ziel) j++
            if (r[j].first > ziel || ziel - r[j].first > toleranzTage * TAG) continue
            aus += r[i].first to (r[i].second / r[j].second - 1.0)
        }
        return aus
    }

    // Rollierende Schwankung pro Jahr: Standardabweichung der logarithmierten Tagesrenditen der letzten fenster
    // Renditen, mal Wurzel aus proJahr. Ein Fenster zaehlt nur, wenn alle Renditen darin gueltig sind und zwischen
    // zwei Punkten nicht mehr als maxLueckeTage liegen. Anteil, 0,25 = 25 %.
    fun rollierendeSchwankung(reihe: List<Pair<Long, Double>>, fenster: Int = 60, proJahr: Double = 252.0, maxLueckeTage: Int = 10): List<Pair<Long, Double>> {
        if (fenster < 2) return emptyList()
        val r = Risiko.taeglich(reihe)
        val rendite = arrayOfNulls<Double>(r.size)
        for (k in 1 until r.size) {
            val a = r[k - 1].second
            val b = r[k].second
            if (a.isFinite() && b.isFinite() && a > 0.0 && b > 0.0 && r[k].first - r[k - 1].first <= maxLueckeTage * TAG) rendite[k] = ln(b / a)
        }
        val aus = ArrayList<Pair<Long, Double>>()
        var gueltig = 0
        for (k in 1 until r.size) {
            gueltig = if (rendite[k] != null) gueltig + 1 else 0
            if (gueltig < fenster) continue
            var summe = 0.0
            for (m in k - fenster + 1..k) summe += rendite[m]!!
            val mittel = summe / fenster
            var quadrate = 0.0
            for (m in k - fenster + 1..k) quadrate += (rendite[m]!! - mittel).pow(2)
            aus += r[k].first to sqrt(quadrate / (fenster - 1)) * sqrt(proJahr)
        }
        return aus
    }

    // Rendite pro Jahr aus erstem und letztem Punkt; null, wenn die Reihe kuerzer als mindestJahre ist.
    fun jahresrendite(reihe: List<Pair<Long, Double>>, mindestJahre: Double = 1.0): Double? {
        val r = reihe.filter { it.second.isFinite() && it.second > 0.0 }.sortedBy { it.first }
        if (r.size < 2) return null
        val jahre = (r.last().first - r.first().first) / JAHR
        if (jahre <= 0.0 || jahre < mindestJahre - 1e-9) return null
        return (r.last().second / r.first().second).pow(1.0 / jahre) - 1.0
    }

    // Ein Punkt im Rendite-Risiko-Diagramm: Schwankung und Rendite pro Jahr (Anteile).
    class RenditeRisiko(val schwankung: Double, val rendite: Double)

    // Schwankung und Rendite pro Jahr der Reihe ab von. Die Reihe muss fast so weit zurueckreichen (deckung = Anteil
    // des Zeitraums bis zum letzten Punkt, der durch Kurse belegt sein muss), sonst wuerde ein junger Wert auf ein Jahr
    // hochgerechnet. Die Schwankung ist die Standardabweichung der logarithmierten Tagesrenditen mal Wurzel aus der Zahl
    // der Renditen pro Jahr. Null ohne 60 Renditen oder unter einem halben Jahr.
    fun renditeRisiko(reihe: List<Pair<Long, Double>>, von: Long, deckung: Double = 0.9): RenditeRisiko? {
        val p = Risiko.taeglich(reihe.filter { it.first >= von && it.second.isFinite() && it.second > 0.0 })
        if (p.size < 61) return null
        val bis = p.last().first
        if (p.first().first - von > (1.0 - deckung) * (bis - von)) return null
        val jahre = (bis - p.first().first) / JAHR
        val rendite = jahresrendite(p, 0.5) ?: return null
        val l = (1 until p.size).map { ln(p[it].second / p[it - 1].second) }
        val mittel = l.average()
        val sd = sqrt(l.sumOf { (it - mittel).pow(2) } / (l.size - 1))
        return RenditeRisiko(sd * sqrt(l.size / jahre), rendite)
    }

    // Beschriftungen ohne Ueberdeckung

    // Rechteck in Pixeln; links, oben, rechts, unten.
    class Rechteck(val l: Float, val o: Float, val r: Float, val u: Float) {
        fun schneidet(a: Rechteck): Boolean = l < a.r && a.l < r && o < a.u && a.o < u
        fun schneidetKreis(x: Float, y: Float, radius: Float): Boolean {
            val dx = x - x.coerceIn(l, r)
            val dy = y - y.coerceIn(o, u)
            return dx * dx + dy * dy < radius * radius
        }
    }

    // Punkt mit Mittelpunkt und Radius; text ist die Groesse der gewuenschten Beschriftung (Breite, Hoehe) oder null.
    class Marke(val x: Float, val y: Float, val radius: Float, val text: Pair<Float, Float>?)

    // Platz fuer die Beschriftungen: In der Reihenfolge prioritaet (Indizes in marken, wichtigste zuerst) bekommen
    // hoechstens maxAnzahl Marken mit Text den ersten Platz rechts, links, oberhalb oder unterhalb (dann schraeg), an dem
    // die Beschriftung in flaeche bleibt und weder eine andere Beschriftung noch irgendeinen Punkt oder ein gesperrtes
    // Rechteck beruehrt. Wo nichts frei ist, fehlt die Beschriftung. Ergebnis: Index zur linken oberen Ecke.
    fun beschriftungen(
        marken: List<Marke>, prioritaet: List<Int>, flaeche: Rechteck, gesperrt: List<Rechteck> = emptyList(),
        abstand: Float = 3f, maxAnzahl: Int = Int.MAX_VALUE, fern: List<Float> = emptyList(),
    ): Map<Int, Pair<Float, Float>> {
        val belegt = ArrayList<Rechteck>(gesperrt)
        val aus = LinkedHashMap<Int, Pair<Float, Float>>()
        for (i in prioritaet) {
            if (aus.size >= maxAnzahl) break
            val m = marken.getOrNull(i) ?: continue
            val (w, h) = m.text ?: continue
            val a = abstand
            // Zuerst direkt am Punkt, dann (nur mit fern) in groesserem Abstand
            val kandidaten = (listOf(0f) + fern).flatMap { mehr ->
                val rad = m.radius + mehr
                val d = rad * 0.7f
                listOf(
                    (m.x + rad + a) to (m.y - h / 2),
                    (m.x - rad - a - w) to (m.y - h / 2),
                    (m.x - w / 2) to (m.y - rad - a - h),
                    (m.x - w / 2) to (m.y + rad + a),
                    (m.x + d + a) to (m.y - d - a - h),
                    (m.x + d + a) to (m.y + d + a),
                    (m.x - d - a - w) to (m.y - d - a - h),
                    (m.x - d - a - w) to (m.y + d + a),
                )
            }
            val frei = kandidaten.firstOrNull { (x, y) ->
                val r = Rechteck(x, y, x + w, y + h)
                r.l >= flaeche.l && r.o >= flaeche.o && r.r <= flaeche.r && r.u <= flaeche.u &&
                    belegt.none { it.schneidet(r) } && marken.none { r.schneidetKreis(it.x, it.y, it.radius) }
            } ?: continue
            belegt += Rechteck(frei.first, frei.second, frei.first + w, frei.second + h)
            aus[i] = frei
        }
        return aus
    }

    // Naechster Punkt zu (x, y) innerhalb von reichweite Pixeln (mindestens sein Radius); null, wenn keiner nah genug ist.
    fun naechster(marken: List<Marke>, x: Float, y: Float, reichweite: Float): Int? {
        var best: Int? = null
        var bestAbstand = Float.MAX_VALUE
        marken.forEachIndexed { i, m ->
            val d = sqrt((m.x - x) * (m.x - x) + (m.y - y) * (m.y - y))
            if (d <= maxOf(reichweite, m.radius) && d < bestAbstand) { best = i; bestAbstand = d }
        }
        return best
    }
}
