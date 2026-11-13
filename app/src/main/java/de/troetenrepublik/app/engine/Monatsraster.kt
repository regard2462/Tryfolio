package de.troetenrepublik.app.engine

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

// Raster Jahr mal Monat: Rendite je Kalendermonat und je Jahr aus einer Kursreihe oder aus fertigen Monatswerten.
// Reine Rechnung ohne Oberflaeche (ui/MonatsRaster.kt zeichnet sie).
object Monatsraster {

    // monate: Rendite als Anteil (0,05 = 5 %), Index 0 ist der Januar, null ohne Wert; rendite: Rendite des Kalenderjahres
    class Zeile(val jahr: Int, val monate: List<Double?>, val rendite: Double?)

    // Zeilen mit dem neuesten Jahr zuerst
    class Raster(val zeilen: List<Zeile>)

    // Der erste Monat der Reihe zaehlt nur, wenn die Reihe in den ersten
    // Tagen des Monats beginnt, sonst fehlt ihm der Bezugskurs.
    const val ANFANGSTAGE = 5

    // Monatsrenditen aus einer Reihe (Zeitpunkt in ms, Kurs): Schlusskurs des Monats gegen Schlusskurs des Vormonats. Der
    // erste Monat vergleicht mit dem ersten Kurs, wenn der in den ersten Tagen liegt. Ein Monat ohne Vormonat in der Reihe
    // (Luecke) bleibt leer. Jahresrendite: die Monate eines Jahres verkettet, vom Januar an ohne Luecke; sonst leer.
    fun aus(reihe: List<Pair<Long, Double>>, zone: ZoneId = ZoneOffset.UTC): Raster? {
        val punkte = reihe.filter { it.second.isFinite() && it.second > 0.0 }.sortedBy { it.first }
        if (punkte.size < 2) return null
        class Monat(val schluss: Double, val erster: Double, val ersterTag: Int)
        val je = HashMap<Int, Monat>()
        for ((t, v) in punkte) {
            val d = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            val k = d.year * 12 + d.monthValue - 1
            val alt = je[k]
            je[k] = if (alt == null) Monat(v, v, d.dayOfMonth) else Monat(v, alt.erster, alt.ersterTag)
        }
        val erster = je.keys.min()
        val monate = LinkedHashMap<Pair<Int, Int>, Double>()
        val luecken = HashSet<Int>()
        for (k in je.keys.sorted()) {
            val m = je.getValue(k)
            val bezug = je[k - 1]?.schluss ?: if (k == erster && m.ersterTag <= ANFANGSTAGE) m.erster else null
            val jahr = k.floorDiv(12)
            if (bezug == null) { if (k != erster) luecken += jahr; continue }
            monate[jahr to k - jahr * 12 + 1] = m.schluss / bezug - 1.0
        }
        return ausMonaten(monate, null, luecken)
    }

    // Raster aus fertigen Monatswerten (Jahr zu Monat 1 bis 12 zu Anteil); jahre: fertige Jahresrenditen, sonst verkettet
    // (nicht fuer Jahre in ohneJahr, in denen ein Monat der Reihe keinen Bezug hatte).
    fun ausMonaten(monate: Map<Pair<Int, Int>, Double>, jahre: Map<Int, Double>? = null, ohneJahr: Set<Int> = emptySet()): Raster? {
        val werte = monate.filter { it.key.second in 1..12 && it.value.isFinite() }
        val alleJahre = (werte.keys.map { it.first } + (jahre?.keys ?: emptySet())).distinct().sorted()
        if (alleJahre.isEmpty() || werte.isEmpty()) return null
        val zeilen = alleJahre.map { j ->
            val m = List(12) { werte[j to it + 1] }
            Zeile(j, m, jahre?.get(j) ?: if (j in ohneJahr) null else verkettet(m))
        }
        return Raster(zeilen.sortedByDescending { it.jahr })
    }

    // Vom Januar an ohne Luecke bis zum letzten Monat mit Wert verkettet; ohne Januar oder mit Luecke null
    internal fun verkettet(m: List<Double?>): Double? {
        val letzter = m.indexOfLast { it != null }
        if (letzter < 0 || m[0] == null) return null
        var stand = 1.0
        for (i in 0..letzter) stand *= 1.0 + (m[i] ?: return null)
        return stand - 1.0
    }
}
