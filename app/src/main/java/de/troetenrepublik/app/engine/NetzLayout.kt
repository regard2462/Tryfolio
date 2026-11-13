package de.troetenrepublik.app.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Anordnung der Knoten: kraeftebasiert (Abstossung zwischen allen, Anziehung entlang der Kanten) mit festem Zufallskeim,
// danach eine Entzerrung, bis sich keine zwei Kaesten (Name samt Rand) beruehren. Bereits platzierte Knoten bleiben beim
// Erweitern fast an ihrem Platz, damit das Netz stabil bleibt. Reine Rechnung in Pixeln, ohne Oberflaeche.
object NetzLayout {

    class Kasten(val id: String, val breite: Float, val hoehe: Float)

    class Punkt(val x: Float, val y: Float)

    private const val SCHRITTE = 260
    private const val ENTZERRUNG = 800
    private const val GERADEN_RUNDEN = 8

    // Ab diesem Kosinus (rund 160 Grad) gelten zwei Kanten einer Firma als gerade Linie durch sie; gedreht wird auf 140 Grad
    private const val GERADE_COS = -0.94f
    private val ZIEL_WINKEL = 140f * PI.toFloat() / 180f

    // Pseudozufall aus Kennung und Keim (gleiche Eingabe, gleiches Ergebnis auf jeder Plattform)
    private fun zufall(id: String, keim: Int, k: Int): Float {
        var h = id.hashCode() * 31 + keim * 7919 + k * 104729
        h = h * 1103515245 + 12345
        h = h xor (h ushr 13)
        return ((h and 0x7fffffff) % 10000) / 10000f
    }

    // Mitte der Kaesten. fest: bereits platzierte Knoten (bleiben nahezu), mitte: Knoten im Zentrum (bleibt am Ursprung,
    // wenn er noch keinen festen Platz hat), abstand: Mindestluecke zwischen zwei Kaesten.
    fun berechnen(
        kaesten: List<Kasten>, kanten: List<Pair<String, String>>, fest: Map<String, Punkt> = emptyMap(), mitte: String? = null,
        abstand: Float = 16f, keim: Int = 7, kantenBreite: Float = 0f,
    ): Map<String, Punkt> {
        val n = kaesten.size
        if (n == 0) return emptyMap()
        val index = HashMap<String, Int>()
        kaesten.forEachIndexed { i, k -> index[k.id] = i }
        val x = FloatArray(n); val y = FloatArray(n)
        val bekannt = BooleanArray(n)
        for ((i, k) in kaesten.withIndex()) fest[k.id]?.let { x[i] = it.x; y[i] = it.y; bekannt[i] = true }
        val nachbarn = Array(n) { ArrayList<Int>() }
        val paare = ArrayList<IntArray>()
        for ((a, b) in kanten) {
            val i = index[a] ?: continue; val j = index[b] ?: continue
            if (i == j) continue
            nachbarn[i].add(j); nachbarn[j].add(i); paare.add(intArrayOf(i, j))
        }
        val mi = mitte?.let { index[it] }
        if (mi != null && !bekannt[mi]) { x[mi] = 0f; y[mi] = 0f; bekannt[mi] = true }
        val mittlere = kaesten.sumOf { (it.breite + it.hoehe).toDouble() }.toFloat() / (2f * n)
        // kantenBreite: Platz fuer die Beschriftung der Kanten (Breite der laengsten), vergroessert den Sollabstand
        val ideal = 190f + mittlere + kantenBreite
        // Anfangsplaetze: um einen bekannten Nachbarn herum, sonst um den Ursprung
        for (i in 0 until n) {
            if (bekannt[i]) continue
            val anker = nachbarn[i].firstOrNull { bekannt[it] }
            val ax = if (anker != null) x[anker] else 0f
            val ay = if (anker != null) y[anker] else 0f
            val w = (zufall(kaesten[i].id, keim, 1) * 2f * PI).toFloat()
            val r = ideal * (0.8f + zufall(kaesten[i].id, keim, 2) * 0.5f)
            x[i] = ax + cos(w) * r; y[i] = ay + sin(w) * r
            bekannt[i] = true
        }
        // Kraefte
        val dx = FloatArray(n); val dy = FloatArray(n)
        val kontakt = FloatArray(3)
        var temperatur = ideal * 0.5f
        val k2 = ideal * ideal
        for (schritt in 0 until SCHRITTE) {
            dx.fill(0f); dy.fill(0f)
            for (i in 0 until n) for (j in i + 1 until n) {
                var ex = x[i] - x[j]; var ey = y[i] - y[j]
                var d = sqrt(ex * ex + ey * ey)
                if (d < 0.01f) { ex = zufall(kaesten[i].id, keim, 3 + schritt) - 0.5f; ey = zufall(kaesten[j].id, keim, 4 + schritt) - 0.5f; d = 0.5f }
                val f = k2 / d
                dx[i] += ex / d * f; dy[i] += ey / d * f
                dx[j] -= ex / d * f; dy[j] -= ey / d * f
            }
            for (p in paare) {
                val i = p[0]; val j = p[1]
                val ex = x[i] - x[j]; val ey = y[i] - y[j]
                val d = max(sqrt(ex * ex + ey * ey), 0.01f)
                val f = d * d / ideal
                dx[i] -= ex / d * f; dy[i] -= ey / d * f
                dx[j] += ex / d * f; dy[j] += ey / d * f
            }
            // Fremde Knoten und Kanten stossen sich ab: der Knoten weicht seitlich aus, die Kante in die Gegenrichtung
            for (p in paare) for (k in 0 until n) {
                if (k == p[0] || k == p[1] || !kantenKontakt(kaesten, x, y, p[0], p[1], k, abstand, kontakt)) continue
                val f = ideal * 0.8f
                if (k == mi) {
                    dx[p[0]] -= kontakt[0] * f; dy[p[0]] -= kontakt[1] * f
                    dx[p[1]] -= kontakt[0] * f; dy[p[1]] -= kontakt[1] * f
                } else {
                    dx[k] += kontakt[0] * f; dy[k] += kontakt[1] * f
                    dx[p[0]] -= kontakt[0] * f / 2f; dy[p[0]] -= kontakt[1] * f / 2f
                    dx[p[1]] -= kontakt[0] * f / 2f; dy[p[1]] -= kontakt[1] * f / 2f
                }
            }
            for (i in 0 until n) {
                if (i == mi) continue
                val gewicht = if (fest.containsKey(kaesten[i].id)) 0.12f else 1f
                val l = max(sqrt(dx[i] * dx[i] + dy[i] * dy[i]), 0.01f)
                val s = minOf(l, temperatur) * gewicht
                x[i] += dx[i] / l * s; y[i] += dy[i] / l * s
            }
            temperatur *= 0.985f
        }
        entzerren(kaesten, x, y, mi, abstand)
        // Kanten, die noch quer durch einen fremden Kasten laufen, direkt wegschieben, danach wieder entzerren
        val mx = if (mi != null) x[mi] else 0f
        val my = if (mi != null) y[mi] else 0f
        fun freiraeumen() {
            for (runde in 0 until 40) {
                var bewegt = false
                for (p in paare) for (k in 0 until n) {
                    if (k == p[0] || k == p[1] || !kantenKontakt(kaesten, x, y, p[0], p[1], k, abstand, kontakt)) continue
                    bewegt = true
                    val s = kontakt[2] + 2f
                    if (k == mi) {
                        for (e in p) { x[e] -= kontakt[0] * s; y[e] -= kontakt[1] * s }
                    } else {
                        x[k] += kontakt[0] * s / 2f; y[k] += kontakt[1] * s / 2f
                        for (e in p) { x[e] -= kontakt[0] * s / 2f; y[e] -= kontakt[1] * s / 2f }
                    }
                    if (mi != null) { x[mi] = mx; y[mi] = my }
                }
                if (!bewegt) break
                entzerren(kaesten, x, y, mi, abstand)
            }
        }
        freiraeumen()
        // Zwei Kanten einer Firma in genau entgegengesetzte Richtungen sehen wie eine Linie durch die Firma aus: einen Nachbarn drehen
        for (runde in 0 until GERADEN_RUNDEN) {
            if (!ausGerader(kaesten, x, y, nachbarn, mi, fest, abstand)) break
            entzerren(kaesten, x, y, mi, abstand)
            freiraeumen()
        }
        return kaesten.withIndex().associate { (i, k) -> k.id to Punkt(x[i], y[i]) }
    }

    // Dreht je Firma einen von zwei fast entgegengesetzten Nachbarn um die Firma auf rund 140 Grad zum anderen (Abstand bleibt).
    // Gedreht wird der Nachbar, der nicht die Mitte ist, ohne festen Platz, bevorzugt mit weniger Nachbarn; die Drehrichtung
    // waehlt, wo weniger Kaesten im Weg stehen. Gibt an, ob etwas bewegt wurde.
    private fun ausGerader(
        kaesten: List<Kasten>, x: FloatArray, y: FloatArray, nachbarn: Array<ArrayList<Int>>, mi: Int?, fest: Map<String, Punkt>, abstand: Float,
    ): Boolean {
        var bewegt = false
        val n = kaesten.size
        for (c in 0 until n) {
            val nb = nachbarn[c].distinct()
            if (nb.size < 2) continue
            for (ai in nb.indices) for (bi in ai + 1 until nb.size) {
                val a = nb[ai]; val b = nb[bi]
                val ax = x[a] - x[c]; val ay = y[a] - y[c]
                val bx = x[b] - x[c]; val bq = y[b] - y[c]
                val la = sqrt(ax * ax + ay * ay); val lb = sqrt(bx * bx + bq * bq)
                if (la < 1f || lb < 1f || (ax * bx + ay * bq) / (la * lb) > GERADE_COS) continue
                val kand = listOf(a, b).filter { it != mi }.minWithOrNull(compareBy<Int>({ fest.containsKey(kaesten[it].id) }, { nachbarn[it].size })) ?: continue
                // Bereits platzierte Knoten bleiben, wo sie sind
                if (fest.containsKey(kaesten[kand].id)) continue
                val anker = if (kand == a) b else a
                val wAnker = atan2(y[anker] - y[c], x[anker] - x[c])
                val r = if (kand == a) la else lb
                val wAlt = atan2(y[kand] - y[c], x[kand] - x[c])
                var besteX = x[kand]; var besteY = y[kand]; var besterWert = Int.MAX_VALUE
                for (vorzeichen in floatArrayOf(1f, -1f)) {
                    val w = wAnker + vorzeichen * ZIEL_WINKEL
                    val px = x[c] + cos(w) * r; val py = y[c] + sin(w) * r
                    var im_weg = 0
                    for (j in 0 until n) {
                        if (j == kand) continue
                        val ox = (kaesten[kand].breite + kaesten[j].breite) / 2f + abstand - abs(px - x[j])
                        val oy = (kaesten[kand].hoehe + kaesten[j].hoehe) / 2f + abstand - abs(py - y[j])
                        if (ox > 0f && oy > 0f) im_weg++
                    }
                    // Bei Gleichstand die kleinere Drehung gegenueber dem alten Platz
                    val dreh = abs(atan2(sin(w - wAlt), cos(w - wAlt)))
                    val wert = im_weg * 1000 + (dreh * 100f).toInt()
                    if (wert < besterWert) { besterWert = wert; besteX = px; besteY = py }
                }
                x[kand] = besteX; y[kand] = besteY
                bewegt = true
            }
        }
        return bewegt
    }

    // Fuer die kleine Ansicht: die Firma in der Mitte, links die Nachbarn, deren Kante zu ihr fuehrt, rechts die uebrigen (Kanten
    // von ihr weg, ungerichtete). Die Nachbarn stehen in Spalten untereinander, bei Schieflage wandert der letzte der volleren
    // Spalte hinueber. Keine Kante laeuft durch einen fremden Kasten und nichts ueberlappt. luecke: Platz zwischen Spalte und
    // Firma fuer die Linien. Mitte der Firma ist (0, 0).
    fun faecher(kaesten: List<Kasten>, kanten: List<Pair<String, String>>, mitte: String, luecke: Float, abstand: Float = 10f): Map<String, Punkt> {
        val m = kaesten.firstOrNull { it.id == mitte } ?: return emptyMap()
        val andere = kaesten.filter { it.id != mitte }
        val links = ArrayList<Kasten>(); val rechts = ArrayList<Kasten>()
        for (k in andere) {
            val hinein = kanten.any { it.first == k.id && it.second == mitte }
            val hinaus = kanten.any { it.first == mitte && it.second == k.id }
            when {
                hinein -> links += k
                hinaus -> rechts += k
                else -> if (links.size <= rechts.size) links += k else rechts += k
            }
        }
        while (links.size - rechts.size >= 2) rechts += links.removeAt(links.size - 1)
        while (rechts.size - links.size >= 2) links += rechts.removeAt(rechts.size - 1)
        val pos = LinkedHashMap<String, Punkt>()
        pos[mitte] = Punkt(0f, 0f)
        fun spalte(liste: List<Kasten>, seite: Float) {
            val gesamt = liste.sumOf { it.hoehe.toDouble() }.toFloat() + abstand * (liste.size - 1).coerceAtLeast(0)
            var oben = -gesamt / 2f
            for (k in liste) {
                pos[k.id] = Punkt(seite * (m.breite / 2f + luecke + k.breite / 2f), oben + k.hoehe / 2f)
                oben += k.hoehe + abstand
            }
        }
        spalte(links, -1f); spalte(rechts, 1f)
        return pos
    }

    // Laeuft die Gerade zwischen den Mitten von i und j durch den Kasten k (mit Luecke)? Dann steht in out[0], out[1] die Richtung
    // vom naechsten Punkt der Kante zur Mitte von k (Einheitsvektor) und in out[2] die Tiefe des Eindringens.
    private fun kantenKontakt(kaesten: List<Kasten>, x: FloatArray, y: FloatArray, i: Int, j: Int, k: Int, abstand: Float, out: FloatArray): Boolean {
        val ex = x[j] - x[i]; val ey = y[j] - y[i]
        val l2 = ex * ex + ey * ey
        if (l2 < 1f) return false
        val t = (((x[k] - x[i]) * ex + (y[k] - y[i]) * ey) / l2).coerceIn(0f, 1f)
        var vx = x[k] - (x[i] + t * ex); var vy = y[k] - (y[i] + t * ey)
        var d = sqrt(vx * vx + vy * vy)
        if (d < 0.01f) {
            // Mitte genau auf der Kante: senkrecht zur Kante ausweichen
            val l = sqrt(l2)
            vx = -ey / l; vy = ex / l; d = 0f
        } else { vx /= d; vy /= d }
        val tiefe = abs(vx) * kaesten[k].breite / 2f + abs(vy) * kaesten[k].hoehe / 2f + abstand / 2f - d
        if (tiefe <= 0f) return false
        out[0] = vx; out[1] = vy; out[2] = tiefe
        return true
    }

    // Schiebt ueberlappende Kaesten auseinander, bis keine zwei sich beruehren (mit Mindestluecke abstand)
    private fun entzerren(kaesten: List<Kasten>, x: FloatArray, y: FloatArray, mi: Int?, abstand: Float) {
        val n = kaesten.size
        for (runde in 0 until ENTZERRUNG) {
            var bewegt = false
            for (i in 0 until n) for (j in i + 1 until n) {
                val ox = (kaesten[i].breite + kaesten[j].breite) / 2f + abstand - abs(x[i] - x[j])
                val oy = (kaesten[i].hoehe + kaesten[j].hoehe) / 2f + abstand - abs(y[i] - y[j])
                if (ox <= 0f || oy <= 0f) continue
                bewegt = true
                val anteilI = if (i == mi) 0f else if (j == mi) 1f else 0.5f
                val anteilJ = 1f - anteilI
                if (ox < oy) {
                    val vorzeichen = if (x[i] > x[j] || (x[i] == x[j] && i > j)) 1f else -1f
                    x[i] += vorzeichen * (ox + 0.5f) * anteilI; x[j] -= vorzeichen * (ox + 0.5f) * anteilJ
                } else {
                    val vorzeichen = if (y[i] > y[j] || (y[i] == y[j] && i > j)) 1f else -1f
                    y[i] += vorzeichen * (oy + 0.5f) * anteilI; y[j] -= vorzeichen * (oy + 0.5f) * anteilJ
                }
            }
            if (!bewegt) return
        }
        // Rest: Kasten nach rechts schieben, bis er frei steht
        for (i in 0 until n) {
            if (i == mi) continue
            var versuche = 0
            while (versuche++ < 400 && (0 until n).any { j -> j != i && beruehrt(kaesten, x, y, i, j, abstand) }) x[i] += 8f
        }
    }

    private fun beruehrt(kaesten: List<Kasten>, x: FloatArray, y: FloatArray, i: Int, j: Int, abstand: Float): Boolean {
        val ox = (kaesten[i].breite + kaesten[j].breite) / 2f + abstand - abs(x[i] - x[j])
        val oy = (kaesten[i].hoehe + kaesten[j].hoehe) / 2f + abstand - abs(y[i] - y[j])
        return ox > 0f && oy > 0f
    }

    // Beruehren sich zwei Kaesten (ohne Luecke)? Fuer Pruefungen.
    fun ueberlappen(a: Kasten, pa: Punkt, b: Kasten, pb: Punkt): Boolean =
        abs(pa.x - pb.x) < (a.breite + b.breite) / 2f && abs(pa.y - pb.y) < (a.hoehe + b.hoehe) / 2f
}
