package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Geometrie des Globus ohne Oberflaeche (seit 0.69): Umrisse aus assets/globus/laender.json, orthografische
// Projektion, Treffertest. Compose-Darstellung und Widget-Bild benutzen denselben Code.

// Ein Ring auf der Kugel: Laenge und Breite in Grad (fuer den Treffertest) und die verdichteten Punkte als
// Einheitsvektoren x, y, z (fuer das Zeichnen), dazu die kleinste Kappe um alle Punkte.
class GlobusRing(val lonLat: FloatArray, val vek: FloatArray) {
    val minLon: Float
    val maxLon: Float
    val minLat: Float
    val maxLat: Float

    init {
        var a = Float.MAX_VALUE; var b = -Float.MAX_VALUE; var c = Float.MAX_VALUE; var d = -Float.MAX_VALUE
        var i = 0
        while (i < lonLat.size) {
            val x = lonLat[i]; val y = lonLat[i + 1]
            if (x < a) a = x
            if (x > b) b = x
            if (y < c) c = y
            if (y > d) d = y
            i += 2
        }
        minLon = a; maxLon = b; minLat = c; maxLat = d
    }

    // Gerade-ungerade Regel in der Ebene aus Laenge und Breite.
    fun enthaelt(lon: Double, lat: Double): Boolean {
        if (lon < minLon || lon > maxLon || lat < minLat || lat > maxLat) return false
        var innen = false
        val n = lonLat.size / 2
        var k = n - 1
        for (i in 0 until n) {
            val xi = lonLat[i * 2]; val yi = lonLat[i * 2 + 1]
            val xk = lonLat[k * 2]; val yk = lonLat[k * 2 + 1]
            if ((yi > lat) != (yk > lat) && lon < (xk - xi) * (lat - yi) / (yk - yi) + xi) innen = !innen
            k = i
        }
        return innen
    }
}

// Eine Linie (Bogen) als Vektoren, mit Kappe zum schnellen Aussortieren.
class GlobusLinie(val vek: FloatArray) {
    val kappe = GlobusKappe.von(vek)
}

// Mittelpunkt (Einheitsvektor) und halber Oeffnungswinkel (als Sinus) um eine Punktmenge.
class GlobusKappe(val x: Float, val y: Float, val z: Float, val sinR: Float) {
    // Kann irgendein Punkt der Menge von der Mitte (fx, fy, fz) aus sichtbar sein?
    fun sichtbar(fx: Float, fy: Float, fz: Float): Boolean = x * fx + y * fy + z * fz > -sinR

    companion object {
        fun von(vek: FloatArray): GlobusKappe {
            var sx = 0.0; var sy = 0.0; var sz = 0.0
            var i = 0
            while (i < vek.size) { sx += vek[i]; sy += vek[i + 1]; sz += vek[i + 2]; i += 3 }
            val l = sqrt(sx * sx + sy * sy + sz * sz)
            // Umschliessen die Punkte die Kugel gleichmaessig, gibt es keine Mitte: nie aussortieren
            if (l < 1e-6) return GlobusKappe(0f, 0f, 1f, 2f)
            val mx = sx / l; val my = sy / l; val mz = sz / l
            var kleinster = 1.0
            i = 0
            while (i < vek.size) {
                val d = mx * vek[i] + my * vek[i + 1] + mz * vek[i + 2]
                if (d < kleinster) kleinster = d
                i += 3
            }
            val winkel = acos(kleinster.coerceIn(-1.0, 1.0)) + 0.01
            // ab einem Viertelkreis Oeffnung gibt es nichts mehr auszusortieren
            return GlobusKappe(mx.toFloat(), my.toFloat(), mz.toFloat(), if (winkel >= Math.PI / 2) 2f else sin(winkel).toFloat())
        }
    }
}

class GlobusLand(
    val a2: String,
    val a3: String,
    val de: String,
    val en: String,
    // Alle Ringe aller Teilflaechen, Fuellregel gerade-ungerade
    val ringe: Array<GlobusRing>,
    // Je Teilflaeche der Bereich in `ringe` (von, bis ausschliesslich), fuer den Treffertest mit Loechern
    val teile: IntArray,
) {
    val kappe: GlobusKappe = GlobusKappe.von(FloatArray(ringe.sumOf { it.vek.size }).also { alle ->
        var p = 0
        for (r in ringe) { r.vek.copyInto(alle, p); p += r.vek.size }
    })

    fun name(deutsch: Boolean): String = if (deutsch) de else en

    fun enthaelt(lon: Double, lat: Double): Boolean {
        var t = 0
        while (t < teile.size) {
            val von = teile[t]; val bis = teile[t + 1]
            var innen = false
            for (i in von until bis) if (ringe[i].enthaelt(lon, lat)) innen = !innen
            if (innen) return true
            t += 2
        }
        return false
    }
}

class GlobusKarte(val laender: List<GlobusLand>, val linien: List<GlobusLinie>) {
    // Teilen sich mehrere Gebiete ein Kuerzel (Brasilien und eine Insel, Australien und seine Aussengebiete), steht das
    // Hauptgebiet fuer das Kuerzel: das mit den meisten Stuetzpunkten.
    private val nachA3 = hauptgebiete(laender) { it.a3 }
    private val nachA2 = hauptgebiete(laender.filter { it.a2.isNotEmpty() }) { it.a2 }

    fun nachA3(code: String): GlobusLand? = nachA3[code]
    fun nachA2(code: String): GlobusLand? = nachA2[code]

    // Das Land, das ein Gebiet auf der Karte vertritt: bei geteiltem Kuerzel das Hauptgebiet, sonst das Gebiet selbst.
    fun haupt(land: GlobusLand): GlobusLand = nachA3[land.a3] ?: land

    private fun hauptgebiete(liste: List<GlobusLand>, kuerzel: (GlobusLand) -> String): Map<String, GlobusLand> =
        liste.groupBy(kuerzel).mapValues { (_, gebiete) -> gebiete.maxByOrNull { g -> g.ringe.sumOf { it.lonLat.size } }!! }

    // Das Land an einer Stelle; sonst das naechste Land, dessen Umriss innerhalb von toleranzGrad liegt (kleine
    // Staaten und Inseln lassen sich mit dem Finger nicht genau treffen). Nebengebiete eines Landes liefern das Hauptland.
    fun landBei(lon: Double, lat: Double, toleranzGrad: Double = 0.0): GlobusLand? = naechstesGebiet(lon, lat, toleranzGrad)?.let(::haupt)

    private fun naechstesGebiet(lon: Double, lat: Double, toleranzGrad: Double): GlobusLand? {
        for (l in laender) if (l.enthaelt(lon, lat)) return l
        if (toleranzGrad <= 0.0) return null
        val bl = Math.toRadians(lat); val bo = Math.toRadians(lon)
        val x = cos(bl) * cos(bo); val y = cos(bl) * sin(bo); val z = sin(bl)
        val grenze = cos(Math.toRadians(toleranzGrad))
        var best: GlobusLand? = null
        var bestD = grenze
        for (l in laender) {
            // Kappe: liegt die Stelle weiter als Radius plus Toleranz von der Mitte, kann nichts treffen
            val k = l.kappe
            if (k.sinR < 1.5f) {
                val d = (k.x * x + k.y * y + k.z * z).toDouble().coerceIn(-1.0, 1.0)
                if (acos(d) > asin(k.sinR.toDouble()) + Math.toRadians(toleranzGrad) + 0.02) continue
            }
            for (r in l.ringe) {
                val v = r.vek
                var i = 0
                while (i < v.size) {
                    val d = v[i] * x + v[i + 1] * y + v[i + 2] * z
                    if (d > bestD) { bestD = d; best = l }
                    i += 3
                }
            }
        }
        return best
    }
}

// Blickrichtung und Bildausschnitt: Blickmitte (Laenge, Breite) in Grad, Mittelpunkt und Radius der Kugel in Pixeln.
class GlobusAnsicht {
    var fx = 1f; var fy = 0f; var fz = 0f
    var rx = 0f; var ry = 1f
    var ux = 0f; var uy = 0f; var uz = 1f
    var cx = 0f; var cy = 0f; var radius = 1f
    var lon = 0.0; var lat = 0.0

    fun setze(lonGrad: Double, latGrad: Double, mitteX: Float, mitteY: Float, r: Float) {
        lon = lonGrad; lat = latGrad
        val lo = Math.toRadians(lonGrad); val la = Math.toRadians(latGrad)
        fx = (cos(la) * cos(lo)).toFloat(); fy = (cos(la) * sin(lo)).toFloat(); fz = sin(la).toFloat()
        rx = (-sin(lo)).toFloat(); ry = cos(lo).toFloat()
        ux = (-sin(la) * cos(lo)).toFloat(); uy = (-sin(la) * sin(lo)).toFloat(); uz = cos(la).toFloat()
        cx = mitteX; cy = mitteY; radius = r
    }

    fun tiefe(x: Float, y: Float, z: Float): Float = fx * x + fy * y + fz * z
    fun px(x: Float, y: Float, z: Float): Float = cx + radius * (rx * x + ry * y)
    fun py(x: Float, y: Float, z: Float): Float = cy - radius * (ux * x + uy * y + uz * z)

    // Bildpunkt eines Ortes (Grad) nach aus[0], aus[1]; false, wenn er auf der abgewandten Seite liegt.
    fun bildpunkt(lonGrad: Double, latGrad: Double, aus: FloatArray): Boolean {
        val lo = Math.toRadians(lonGrad); val la = Math.toRadians(latGrad)
        val x = (cos(la) * cos(lo)).toFloat(); val y = (cos(la) * sin(lo)).toFloat(); val z = sin(la).toFloat()
        if (tiefe(x, y, z) <= 0.02f) return false
        aus[0] = px(x, y, z); aus[1] = py(x, y, z)
        return true
    }

    // Bildpunkt zurueck auf die Kugel: Laenge und Breite in Grad nach aus[0], aus[1]. false neben der Kugel.
    fun rueck(sx: Float, sy: Float, aus: DoubleArray): Boolean {
        val nx = ((sx - cx) / radius).toDouble()
        val ny = (-(sy - cy) / radius).toDouble()
        val r2 = nx * nx + ny * ny
        if (r2 > 1.0) return false
        val nz = sqrt(1.0 - r2)
        val vx = nx * rx + ny * ux + nz * fx
        val vy = nx * ry + ny * uy + nz * fy
        val vz = ny * uz + nz * fz
        aus[0] = Math.toDegrees(atan2(vy, vx))
        aus[1] = Math.toDegrees(asin(vz.coerceIn(-1.0, 1.0)))
        return true
    }
}

// Ziel fuer Pfade: Compose-Path, android.graphics.Path oder Test.
interface GlobusPfad {
    fun nach(x: Float, y: Float)
    fun linie(x: Float, y: Float)
    fun schliessen()
}

object GlobusZeichner {

    // Fuellflaeche eines Landes als geschlossene Pfade der sichtbaren Halbkugel. Punkte hinter dem Rand wandern auf den
    // Rand. minAbstand: Punkte naeher als so viele Pixel am letzten werden ausgelassen. false, wenn nichts sichtbar ist.
    fun flaeche(land: GlobusLand, a: GlobusAnsicht, pfad: GlobusPfad, minAbstand: Float): Boolean {
        val k = land.kappe
        if (!k.sichtbar(a.fx, a.fy, a.fz)) return false
        var etwas = false
        for (ring in land.ringe) if (ring(ring, a, pfad, minAbstand)) etwas = true
        return etwas
    }

    private fun ring(ring: GlobusRing, a: GlobusAnsicht, pfad: GlobusPfad, minAbstand: Float): Boolean {
        val v = ring.vek
        val n = v.size / 3
        var sichtbar = false
        var i = 0
        while (i < n) {
            if (a.tiefe(v[i * 3], v[i * 3 + 1], v[i * 3 + 2]) > 0f) { sichtbar = true; break }
            i++
        }
        if (!sichtbar) return false
        val m2 = minAbstand * minAbstand
        var lx = 0f; var ly = 0f
        var erster = true
        for (j in 0 until n) {
            val x = v[j * 3]; val y = v[j * 3 + 1]; val z = v[j * 3 + 2]
            var sx = a.px(x, y, z); var sy = a.py(x, y, z)
            if (a.tiefe(x, y, z) <= 0f) {
                val dx = sx - a.cx; val dy = sy - a.cy
                val l = hypot(dx, dy)
                if (l > 1e-4f) { sx = a.cx + dx / l * a.radius; sy = a.cy + dy / l * a.radius }
            }
            if (erster) { pfad.nach(sx, sy); erster = false; lx = sx; ly = sy; continue }
            val ddx = sx - lx; val ddy = sy - ly
            if (ddx * ddx + ddy * ddy < m2 && j != n - 1) continue
            pfad.linie(sx, sy); lx = sx; ly = sy
        }
        pfad.schliessen()
        return true
    }

    // Linien der sichtbaren Halbkugel; an der Kante der Kugel werden sie genau abgeschnitten.
    fun linien(linien: List<GlobusLinie>, a: GlobusAnsicht, pfad: GlobusPfad, minAbstand: Float) {
        val m2 = minAbstand * minAbstand
        for (l in linien) {
            if (!l.kappe.sichtbar(a.fx, a.fy, a.fz)) continue
            val v = l.vek
            val n = v.size / 3
            var lastD = 0f
            var lastX = 0f; var lastY = 0f; var lastZ = 0f
            var offen = false
            var lx = 0f; var ly = 0f
            for (j in 0 until n) {
                val x = v[j * 3]; val y = v[j * 3 + 1]; val z = v[j * 3 + 2]
                val d = a.tiefe(x, y, z)
                if (j > 0 && (d > 0f) != (lastD > 0f)) {
                    // Schnitt mit dem Rand: Punkt zwischen den beiden, zurueck auf die Kugel gesetzt
                    val t = lastD / (lastD - d)
                    var cx = lastX + t * (x - lastX); var cy = lastY + t * (y - lastY); var cz = lastZ + t * (z - lastZ)
                    val len = sqrt(cx * cx + cy * cy + cz * cz)
                    if (len > 1e-6f) { cx /= len; cy /= len; cz /= len }
                    val sx = a.px(cx, cy, cz); val sy = a.py(cx, cy, cz)
                    if (d > 0f) { pfad.nach(sx, sy); offen = true } else if (offen) pfad.linie(sx, sy)
                    lx = sx; ly = sy
                }
                if (d > 0f) {
                    val sx = a.px(x, y, z); val sy = a.py(x, y, z)
                    if (!offen) { pfad.nach(sx, sy); offen = true; lx = sx; ly = sy }
                    else {
                        val dx = sx - lx; val dy = sy - ly
                        if (dx * dx + dy * dy >= m2 || j == n - 1) { pfad.linie(sx, sy); lx = sx; ly = sy }
                    }
                } else offen = false
                lastD = d; lastX = x; lastY = y; lastZ = z
            }
        }
    }
}

object GlobusLesen {
    private const val SCHRITT_GRAD = 1.5

    // Liest laender.json (Format v 1, siehe tools/globus beim Erzeugen): Boegen als Ganzzahlen in 1/q Grad, ab dem
    // zweiten Punkt Unterschiede; Laender als [a2, a3, deutsch, englisch, Teilflaechen > Ringe > Bogenverweise], ein
    // negativer Verweis ~i heisst Bogen i rueckwaerts. null bei fremdem Format.
    fun lesen(text: String): GlobusKarte? = runCatching {
        val w = Json.parseToJsonElement(text).let { it as kotlinx.serialization.json.JsonObject }
        if (w["v"]?.jsonPrimitive?.int != 1) return null
        val q = w["q"]!!.jsonPrimitive.int.toDouble()
        val boegen = w["arcs"]!!.jsonArray.map { b ->
            val e = b.jsonArray
            val n = e.size / 2
            val out = FloatArray(n * 2)
            var lon = 0; var lat = 0
            for (i in 0 until n) {
                lon += e[i * 2].jsonPrimitive.int; lat += e[i * 2 + 1].jsonPrimitive.int
                out[i * 2] = (lon / q).toFloat(); out[i * 2 + 1] = (lat / q).toFloat()
            }
            out
        }
        val linien = boegen.map { GlobusLinie(verdichten(it, false)) }
        val laender = w["c"]!!.jsonArray.map { c ->
            val e = c.jsonArray
            val ringe = ArrayList<GlobusRing>()
            val teile = ArrayList<Int>()
            for (teil in e[4].jsonArray) {
                val von = ringe.size
                for (ring in teil.jsonArray) ringe += ring(ring.jsonArray, boegen)
                teile += von; teile += ringe.size
            }
            GlobusLand(
                e[0].jsonPrimitive.content, e[1].jsonPrimitive.content, e[2].jsonPrimitive.content, e[3].jsonPrimitive.content,
                ringe.toTypedArray(), teile.toIntArray(),
            )
        }
        GlobusKarte(laender, linien)
    }.getOrNull()

    private fun ring(refs: JsonArray, boegen: List<FloatArray>): GlobusRing {
        var gesamt = 0
        for (r in refs) { val i = r.jsonPrimitive.int; gesamt += boegen[if (i < 0) i.inv() else i].size / 2 }
        val ll = FloatArray(gesamt * 2)
        var j = 0
        for ((k, r) in refs.withIndex()) {
            val i = r.jsonPrimitive.int
            val b = boegen[if (i < 0) i.inv() else i]
            val n = b.size / 2
            for (s in 0 until n) {
                // der gemeinsame Endpunkt aufeinanderfolgender Boegen steht nur einmal
                if (k > 0 && s == 0) continue
                val p = if (i < 0) n - 1 - s else s
                ll[j++] = b[p * 2]; ll[j++] = b[p * 2 + 1]
            }
        }
        val punkte = ll.copyOf(j)
        return GlobusRing(punkte, verdichten(punkte, true))
    }

    // Lange Kanten in Schritten von hoechstens SCHRITT_GRAD teilen (auf Laenge und Breite geradlinig), damit sie auf
    // der Kugel krumm laufen; Ergebnis als Einheitsvektoren. geschlossen: auch die Kante vom letzten zum ersten Punkt.
    fun verdichten(lonLat: FloatArray, geschlossen: Boolean): FloatArray {
        val n = lonLat.size / 2
        if (n == 0) return FloatArray(0)
        val aus = ArrayList<Float>(n * 3)
        fun punkt(lon: Double, lat: Double) {
            val lo = Math.toRadians(lon); val la = Math.toRadians(lat)
            aus.add((cos(la) * cos(lo)).toFloat()); aus.add((cos(la) * sin(lo)).toFloat()); aus.add(sin(la).toFloat())
        }
        val kanten = if (geschlossen) n else n - 1
        for (i in 0 until kanten) {
            val l0 = lonLat[i * 2].toDouble(); val b0 = lonLat[i * 2 + 1].toDouble()
            val k = (i + 1) % n
            var l1 = lonLat[k * 2].toDouble(); val b1 = lonLat[k * 2 + 1].toDouble()
            var dl = l1 - l0
            if (dl > 180) dl -= 360 else if (dl < -180) dl += 360
            l1 = l0 + dl
            val teile = max(1, Math.ceil(max(Math.abs(dl), Math.abs(b1 - b0)) / SCHRITT_GRAD).toInt())
            for (s in 0 until teile) {
                val t = s.toDouble() / teile
                punkt(l0 + (l1 - l0) * t, b0 + (b1 - b0) * t)
            }
        }
        if (!geschlossen) punkt(lonLat[(n - 1) * 2].toDouble(), lonLat[(n - 1) * 2 + 1].toDouble())
        val out = FloatArray(aus.size)
        for (i in aus.indices) out[i] = aus[i]
        return out
    }
}
