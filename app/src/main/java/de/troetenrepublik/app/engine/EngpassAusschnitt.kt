package de.troetenrepublik.app.engine

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Kartenausschnitt um einen Engpass (Blatt „Engpass“): derselbe orthografische Blick wie im Globus, nur dicht herangezoomt.
// Rechnet Begrenzungsrahmen, Radius, Beschriftungsanker und Platzierung der Namen ohne Oberflaeche.
object EngpassAusschnitt {
    // Breite zu Hoehe des Bildes
    const val SEITENVERHAELTNIS = 1.5f
    const val STANDARD_HALB_GRAD = 5.0

    // Halbe Breite des Ausschnitts in Grad Bogen (ab Mitte bis zum Rand), je nach Groesse der Meerenge
    private val HALB_GRAD = mapOf(
        "chokepoint6" to 5.0, "chokepoint4" to 5.0, "chokepoint1" to 4.0, "chokepoint2" to 3.5, "chokepoint5" to 6.0,
        "chokepoint11" to 6.0, "chokepoint3" to 3.5, "chokepoint8" to 4.0, "chokepoint7" to 9.0, "chokepoint9" to 4.0,
        "chokepoint10" to 3.5, "chokepoint28" to 4.5, "chokepoint12" to 5.0, "chokepoint14" to 6.0, "chokepoint17" to 6.0,
        "chokepoint13" to 4.0, "chokepoint15" to 4.0, "chokepoint19" to 4.0, "chokepoint20" to 6.0, "chokepoint16" to 5.0,
        "chokepoint25" to 4.0, "chokepoint27" to 4.0, "chokepoint18" to 6.0, "chokepoint22" to 5.0, "chokepoint23" to 4.0,
        "chokepoint24" to 4.0, "chokepoint21" to 7.0, "chokepoint26" to 7.0,
    )

    fun halbGrad(id: String): Double = HALB_GRAD[id] ?: STANDARD_HALB_GRAD

    // Begrenzungsrahmen in Grad: Mitte, halbe Ausdehnung in Laenge und Breite. Die Laenge wird mit der Breite des Ortes
    // gedehnt (ein Grad Laenge ist dort kuerzer); sie springt ueber 180 Grad hinaus, damit sich der Rahmen nicht teilt.
    class Rahmen(val lon: Double, val lat: Double, val halbLon: Double, val halbLat: Double) {
        val minLon: Double get() = lon - halbLon
        val maxLon: Double get() = lon + halbLon
        val minLat: Double get() = lat - halbLat
        val maxLat: Double get() = lat + halbLat

        fun enthaelt(l: Double, b: Double): Boolean {
            if (b < minLat || b > maxLat) return false
            return lonUeberlappt(l, l)
        }

        // Liegt der Bereich (von, bis) in Laenge und Breite ganz oder teilweise im Rahmen? Laengen auch um 360 Grad versetzt.
        fun schneidet(vonLon: Double, bisLon: Double, vonLat: Double, bisLat: Double): Boolean =
            bisLat >= minLat && vonLat <= maxLat && lonUeberlappt(vonLon, bisLon)

        private fun lonUeberlappt(von: Double, bis: Double): Boolean {
            for (v in doubleArrayOf(-360.0, 0.0, 360.0)) if (bis + v >= minLon && von + v <= maxLon) return true
            return false
        }
    }

    // Breite des Bildes in Pixeln und halbe Breite in Grad Bogen: Radius der Kugel, damit der Rand genau so weit reicht.
    fun radius(breite: Float, halbGrad: Double): Float = breite / 2f / sin(Math.toRadians(halbGrad.coerceIn(0.5, 60.0))).toFloat()

    // Halbe Hoehe in Grad Bogen bei dieser Breite und diesem Seitenverhaeltnis
    fun halbHoeheGrad(halbGrad: Double, seitenverhaeltnis: Float = SEITENVERHAELTNIS): Double {
        val s = sin(Math.toRadians(halbGrad.coerceIn(0.5, 60.0))) / seitenverhaeltnis
        return Math.toDegrees(asin(s.coerceIn(0.0, 1.0)))
    }

    fun rahmen(s: Engpaesse.Stelle, seitenverhaeltnis: Float = SEITENVERHAELTNIS): Rahmen {
        val halb = halbGrad(s.id)
        val dehnung = 1.0 / max(0.2, cos(Math.toRadians(s.lat)))
        return Rahmen(s.lon, s.lat, min(halb * dehnung, 180.0), halbHoeheGrad(halb, seitenverhaeltnis))
    }

    // Blick auf den Engpass: Mitte im Bild, Radius nach der Breite. Rueckgabe: der Rahmen in Grad.
    fun ansicht(s: Engpaesse.Stelle, breite: Float, hoehe: Float, a: GlobusAnsicht): Rahmen {
        val r = radius(breite, halbGrad(s.id))
        a.setze(s.lon, s.lat, breite / 2f, hoehe / 2f, r)
        return rahmen(s, breite / hoehe)
    }

    // Beruehrt das Land den Rahmen? Grobe Auswahl ueber die Umrisskaesten der Ringe, damit nur Laender in der Naehe gezeichnet werden.
    fun beruehrt(l: GlobusLand, r: Rahmen): Boolean {
        for (ring in l.ringe) if (r.schneidet(ring.minLon.toDouble(), ring.maxLon.toDouble(), ring.minLat.toDouble(), ring.maxLat.toDouble())) return true
        return false
    }

    // Ein Name im Bild: Mitte (x, y) und Groesse in Pixeln, vorrang fuer die Anrainer des Engpasses
    class Name(val land: GlobusLand, val x: Float, val y: Float, val vorrang: Boolean)

    // Ankerpunkt eines Landes im Bild (Pixel): Mitte des sichtbaren Teils. Die Punkte aller Ringe mit mindestens einem Punkt im
    // Bild werden auf den Bildrand gezogen; die Mitte des Kastens darum liegt dort, wo das Land im Bild sitzt. Liegt sie
    // nicht auf dem Land, gilt der Schwerpunkt der Umrisspunkte im Bild. null, wenn kein Punkt im Bild liegt.
    fun anker(l: GlobusLand, a: GlobusAnsicht, breite: Float, hoehe: Float): FloatArray? {
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        var sx = 0.0; var sy = 0.0; var n = 0
        for (ring in l.ringe) {
            val v = ring.vek
            var drin = false
            var i = 0
            while (i < v.size) {
                if (a.tiefe(v[i], v[i + 1], v[i + 2]) > 0f) {
                    val px = a.px(v[i], v[i + 1], v[i + 2]); val py = a.py(v[i], v[i + 1], v[i + 2])
                    if (px in 0f..breite && py in 0f..hoehe) { drin = true; sx += px; sy += py; n++ }
                }
                i += 3
            }
            if (!drin) continue
            i = 0
            while (i < v.size) {
                if (a.tiefe(v[i], v[i + 1], v[i + 2]) > 0f) {
                    val px = a.px(v[i], v[i + 1], v[i + 2]).coerceIn(0f, breite); val py = a.py(v[i], v[i + 1], v[i + 2]).coerceIn(0f, hoehe)
                    if (px < x0) x0 = px
                    if (px > x1) x1 = px
                    if (py < y0) y0 = py
                    if (py > y1) y1 = py
                }
                i += 3
            }
        }
        if (n == 0) return null
        val mitte = floatArrayOf((x0 + x1) / 2f, (y0 + y1) / 2f)
        val tmp = DoubleArray(2)
        if (a.rueck(mitte[0], mitte[1], tmp) && l.enthaelt(tmp[0], tmp[1])) return mitte
        return floatArrayOf((sx / n).toFloat(), (sy / n).toFloat())
    }

    // Namen der Laender im Bild: Anrainer zuerst, danach die uebrigen nach Zahl der Umrisspunkte im Bild.
    fun namen(karte: GlobusKarte, s: Engpaesse.Stelle, a: GlobusAnsicht, breite: Float, hoehe: Float, rahmen: Rahmen): List<Name> {
        val aus = ArrayList<Pair<Name, Int>>()
        for (l in karte.laender) {
            if (!beruehrt(l, rahmen)) continue
            val an = anker(l, a, breite, hoehe) ?: continue
            val vorrang = karte.haupt(l).a3 in s.anrainer
            aus += Name(l, an[0], an[1], vorrang) to l.ringe.sumOf { it.lonLat.size }
        }
        return aus.sortedWith(compareByDescending<Pair<Name, Int>> { it.first.vorrang }.thenByDescending { it.second }).map { it.first }
    }

    // Ein zu setzender Text: Mitte und Groesse in Pixeln
    class Platz(val x: Float, val y: Float, val w: Float, val h: Float, val vorrang: Boolean)

    // Platziert Texte der Reihe nach im Bild (breite x hoehe) mit Abstand `luft` zu schon gesetzten und zu den Sperrflaechen
    // (Kasten x0, y0, x1, y1). Ein Text mit Vorrang weicht senkrecht aus, bis er frei steht, die uebrigen entfallen bei
    // Ueberdeckung. Jeder Text bleibt ganz im Bild. Ergebnis je Platz: obere linke Ecke oder null.
    fun platzieren(plaetze: List<Platz>, sperren: List<FloatArray>, breite: Float, hoehe: Float, luft: Float): List<FloatArray?> {
        val belegt = ArrayList<FloatArray>(sperren)
        val aus = ArrayList<FloatArray?>(plaetze.size)
        for (p in plaetze) {
            if (p.w > breite || p.h > hoehe) { aus += null; continue }
            val x0 = (p.x - p.w / 2f).coerceIn(luft, max(luft, breite - p.w - luft))
            var gesetzt: FloatArray? = null
            val schritte = if (p.vorrang) intArrayOf(0, 1, -1, 2, -2, 3, -3) else intArrayOf(0)
            for (k in schritte) {
                val y0 = (p.y - p.h / 2f + k * (p.h + luft)).coerceIn(luft, max(luft, hoehe - p.h - luft))
                if (belegt.none { ueberdeckt(x0, y0, p.w, p.h, it, luft) }) { gesetzt = floatArrayOf(x0, y0); break }
            }
            if (gesetzt != null) belegt += floatArrayOf(gesetzt[0], gesetzt[1], gesetzt[0] + p.w, gesetzt[1] + p.h)
            aus += gesetzt
        }
        return aus
    }

    private fun ueberdeckt(x0: Float, y0: Float, w: Float, h: Float, b: FloatArray, luft: Float): Boolean =
        x0 - luft < b[2] && x0 + w + luft > b[0] && y0 - luft < b[3] && y0 + h + luft > b[1]
}
