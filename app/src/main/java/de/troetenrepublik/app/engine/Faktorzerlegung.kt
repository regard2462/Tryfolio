package de.troetenrepublik.app.engine

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Faktorzerlegung des Depots (0.78): lineare Regression der monatlichen Ueberrendite des Depots auf die Faktoren Markt,
// Groesse, Wert, Qualitaet und Momentum, ueber den ganzen Zeitraum und rollend. Die Faktorrenditen laedt die App zur
// Laufzeit (market/FaktorAbruf.kt); hier stehen nur das Lesen der Dateien und die Rechnung.
// Monate sind als Jahr * 12 + (Monat - 1) verschluesselt, Renditen als Anteil (0,01 = 1 %).
object Faktorzerlegung {

    enum class Faktor { MARKT, GROESSE, WERT, QUALITAET, MOMENTUM }

    // Mindestzahl gemeinsamer Monate fuer eine Regression mit fuenf Faktoren (zwoelf Freiheitsgrade)
    const val MIN_MONATE = 18
    // Fenster der rollenden Regression und Mindestlaenge der Reihe dafuer
    const val FENSTER = 24
    const val ROLLEND_AB = 36
    // Anteil des Depots, den die Werte mit Kursen in einem Monat mindestens abdecken muessen
    const val MIN_ABDECKUNG = 0.8

    private const val TAG = 86_400_000L

    fun monat(jahr: Int, monat: Int): Int = jahr * 12 + (monat - 1)
    fun monat(ms: Long): Int = LocalDate.ofEpochDay(Math.floorDiv(ms, TAG)).let { monat(it.year, it.monthValue) }
    fun jahrVon(m: Int): Int = m.floorDiv(12)
    fun monatVon(m: Int): Int = m.mod(12) + 1

    // Faktorrenditen je Monat; zins ist der Monatszins, der von der Depotrendite abgezogen wird (null: nicht bekannt).
    class Faktordaten(val faktoren: Map<Faktor, Map<Int, Double>>, val zins: Map<Int, Double>) {
        val bis: Int? get() = faktoren.values.mapNotNull { it.keys.maxOrNull() }.minOrNull()
        fun vollstaendig(): Boolean = Faktor.entries.all { !faktoren[it].isNullOrEmpty() }
    }

    // ------------------------------------------------------------ Dateien lesen

    // Monatstabelle der Fama/French-Dateien: Zeilen „JJJJMM, Wert, Wert ...“ in Prozent bis zur Jahrestabelle. Ergibt je
    // Spaltenname (Kopfzeile, etwa Mkt-RF, SMB, HML, RMW, CMA, RF, Mom) die Renditen je Monat. Fehlwerte (-99,99) fehlen.
    fun frenchLesen(text: String): Map<String, Map<Int, Double>> {
        val aus = LinkedHashMap<String, HashMap<Int, Double>>()
        var kopf: List<String>? = null
        for (roh in text.lineSequence()) {
            val zeile = roh.trim()
            if (zeile.startsWith("Annual", ignoreCase = true)) break
            val teile = zeile.split(',').map { it.trim() }
            if (teile.size < 2) continue
            if (teile[0].isEmpty()) {
                // Kopfzeile beginnt mit einem Komma
                if (kopf == null) kopf = teile.drop(1)
                continue
            }
            val k = kopf ?: continue
            if (teile[0].length != 6 || !teile[0].all { it.isDigit() }) continue
            val m = monat(teile[0].take(4).toInt(), teile[0].drop(4).toInt())
            for (i in 1 until teile.size) {
                val name = k.getOrNull(i - 1)?.takeIf { it.isNotEmpty() } ?: continue
                val v = teile[i].toDoubleOrNull() ?: continue
                if (v <= -99.0) continue
                aus.getOrPut(name) { HashMap() }[m] = v / 100.0
            }
        }
        return aus
    }

    // Fuenf Faktoren und Momentum aus den beiden Fama/French-Dateien; Qualitaet ist dort die Profitabilitaet (RMW).
    fun ausFrench(fuenf: String, momentum: String): Faktordaten? {
        val f = frenchLesen(fuenf)
        val mom = frenchLesen(momentum).entries.firstOrNull { it.key.equals("Mom", ignoreCase = true) }?.value
        val d = Faktordaten(
            mapOf(
                Faktor.MARKT to f["Mkt-RF"].orEmpty(), Faktor.GROESSE to f["SMB"].orEmpty(), Faktor.WERT to f["HML"].orEmpty(),
                Faktor.QUALITAET to f["RMW"].orEmpty(), Faktor.MOMENTUM to mom.orEmpty(),
            ),
            f["RF"].orEmpty(),
        )
        return d.takeIf { it.vollstaendig() && it.zins.isNotEmpty() }
    }

    // Dateien mit den Spalten name, date (JJJJ-MM-TT) und ret (Anteil): je Name die Renditen je Monat.
    fun jkpLesen(text: String): Map<String, Map<Int, Double>> {
        val zeilen = text.lineSequence().iterator()
        if (!zeilen.hasNext()) return emptyMap()
        val kopf = zeilen.next().trim().split(',').map { it.trim().lowercase() }
        val iName = kopf.indexOf("name")
        val iDatum = kopf.indexOf("date")
        val iRet = kopf.indexOf("ret")
        if (iName < 0 || iDatum < 0 || iRet < 0) return emptyMap()
        val aus = LinkedHashMap<String, HashMap<Int, Double>>()
        while (zeilen.hasNext()) {
            val t = zeilen.next().split(',')
            if (t.size <= maxOf(iName, iDatum, iRet)) continue
            val d = t[iDatum].trim()
            val jahr = d.take(4).toIntOrNull() ?: continue
            val mon = d.drop(5).take(2).toIntOrNull()?.takeIf { it in 1..12 } ?: continue
            val v = t[iRet].trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
            aus.getOrPut(t[iName].trim().lowercase()) { HashMap() }[monat(jahr, mon)] = v
        }
        return aus
    }

    // Markt (Ueberrendite) und die Themen Groesse, Wert, Qualitaet, Momentum; der Zins kommt von aussen.
    fun ausJkp(markt: String, themen: String, zins: Map<Int, Double>): Faktordaten? {
        val m = jkpLesen(markt)["mkt"].orEmpty()
        val t = jkpLesen(themen)
        val d = Faktordaten(
            mapOf(
                Faktor.MARKT to m, Faktor.GROESSE to t["size"].orEmpty(), Faktor.WERT to t["value"].orEmpty(),
                Faktor.QUALITAET to t["quality"].orEmpty(), Faktor.MOMENTUM to t["momentum"].orEmpty(),
            ),
            zins,
        )
        return d.takeIf { it.vollstaendig() && zins.isNotEmpty() }
    }

    // ------------------------------------------------------------ Depotrenditen

    // Renditen je Kalendermonat aus Tageskursen (letzter Kurs des Monats gegen den des Vormonats). wechsel: Kurs der
    // Handelswaehrung in der Zielwaehrung ueber die Zeit; der Kurs wird mit dem letzten Wechselkurs bis zu seinem Tag
    // umgerechnet. Der laufende, noch offene Monat (ab jetzt) zaehlt nicht.
    fun monatsrenditen(kurse: List<Pair<Long, Double>>, wechsel: List<Pair<Long, Double>>? = null, jetzt: Long = Long.MAX_VALUE): Map<Int, Double> {
        val k = kurse.filter { it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        if (k.size < 2) return emptyMap()
        val w = wechsel?.filter { it.second.isFinite() && it.second > 0 }?.sortedBy { it.first }
        if (w != null && w.isEmpty()) return emptyMap()
        var wi = 0
        val schluss = LinkedHashMap<Int, Double>()
        for ((t, v) in k) {
            var wert = v
            if (w != null) {
                while (wi + 1 < w.size && w[wi + 1].first <= t + TAG) wi++
                if (w[wi].first > t + TAG) continue
                wert *= w[wi].second
            }
            schluss[monat(t)] = wert
        }
        val offen = if (jetzt == Long.MAX_VALUE) null else monat(jetzt)
        val aus = LinkedHashMap<Int, Double>()
        for ((m, v) in schluss) {
            if (m == offen) continue
            val vor = schluss[m - 1] ?: continue
            aus[m] = v / vor - 1
        }
        return aus
    }

    class Bestand(val gewicht: Double, val renditen: Map<Int, Double>)

    // Monatsrenditen des Depots in heutiger Zusammensetzung: gewichtete Summe der Werte mit Kursen im Monat, auf ihre
    // Gewichte bezogen. Monate, in denen weniger als MIN_ABDECKUNG des Depots Kurse hat, fehlen. Ergebnis: Rendite und
    // abgedeckter Anteil je Monat.
    fun depotMonate(bestand: List<Bestand>, mindest: Double = MIN_ABDECKUNG): Map<Int, Pair<Double, Double>> {
        val summe = bestand.sumOf { max(0.0, it.gewicht) }
        if (summe <= 0) return emptyMap()
        val monate = bestand.flatMap { it.renditen.keys }.distinct().sorted()
        val aus = LinkedHashMap<Int, Pair<Double, Double>>()
        for (m in monate) {
            var g = 0.0
            var r = 0.0
            for (b in bestand) {
                val x = b.renditen[m] ?: continue
                val w = max(0.0, b.gewicht)
                g += w; r += w * x
            }
            if (g <= 0 || g / summe < mindest) continue
            aus[m] = (r / g) to (g / summe)
        }
        return aus
    }

    // ------------------------------------------------------------ Regression

    // Kleinste Quadrate mit Achsenabschnitt. koeffizienten[0] ist der Achsenabschnitt, danach je Spalte von x einer;
    // fehler sind die Standardfehler dazu. null, wenn die Spalten linear abhaengig sind oder Beobachtungen fehlen.
    class Regression(val koeffizienten: DoubleArray, val fehler: DoubleArray, val r2: Double, val n: Int) {
        val freiheitsgrade: Int get() = n - koeffizienten.size
    }

    fun regression(y: DoubleArray, x: List<DoubleArray>): Regression? {
        val n = y.size
        val k = x.size + 1
        if (n <= k || x.any { it.size != n }) return null
        fun spalte(j: Int, i: Int): Double = if (j == 0) 1.0 else x[j - 1][i]
        // Normalgleichungen (X'X) b = X'y, geloest mit Gauss-Jordan samt Inverser
        val a = Array(k) { DoubleArray(2 * k) }
        val xty = DoubleArray(k)
        for (p in 0 until k) {
            for (q in 0 until k) { var s = 0.0; for (i in 0 until n) s += spalte(p, i) * spalte(q, i); a[p][q] = s }
            a[p][k + p] = 1.0
            var s = 0.0; for (i in 0 until n) s += spalte(p, i) * y[i]; xty[p] = s
        }
        val groesse = (0 until k).maxOf { abs(a[it][it]) }
        for (p in 0 until k) {
            var best = p
            for (z in p + 1 until k) if (abs(a[z][p]) > abs(a[best][p])) best = z
            if (abs(a[best][p]) < 1e-12 * max(1.0, groesse)) return null
            val tausch = a[p]; a[p] = a[best]; a[best] = tausch
            val teiler = a[p][p]
            for (q in 0 until 2 * k) a[p][q] /= teiler
            for (z in 0 until k) {
                if (z == p) continue
                val f = a[z][p]
                if (f == 0.0) continue
                for (q in 0 until 2 * k) a[z][q] -= f * a[p][q]
            }
        }
        val b = DoubleArray(k) { p -> (0 until k).sumOf { q -> a[p][k + q] * xty[q] } }
        val mittel = y.average()
        var rest = 0.0
        var gesamt = 0.0
        for (i in 0 until n) {
            var schaetzung = 0.0
            for (p in 0 until k) schaetzung += b[p] * spalte(p, i)
            rest += (y[i] - schaetzung).pow(2)
            gesamt += (y[i] - mittel).pow(2)
        }
        if (gesamt <= 1e-18) return null
        val varianz = rest / (n - k)
        val fehler = DoubleArray(k) { p -> sqrt(max(0.0, varianz * a[p][k + p])) }
        return Regression(b, fehler, (1 - rest / gesamt).coerceIn(0.0, 1.0), n)
    }

    // Zweiseitiger 95-%-Wert der t-Verteilung (Naeherung ueber die Normalverteilung, ab fuenf Freiheitsgraden auf zwei
    // Stellen genau).
    fun tWert95(freiheitsgrade: Int): Double {
        val z = 1.959964
        val d = max(1, freiheitsgrade).toDouble()
        return z + (z.pow(3) + z) / (4 * d) + (5 * z.pow(5) + 16 * z.pow(3) + 3 * z) / (96 * d * d) +
            (3 * z.pow(7) + 19 * z.pow(5) + 17 * z.pow(3) - 15 * z) / (384 * d * d * d)
    }

    // Ladung eines Faktors mit 95-%-Spanne; beitragJahr: Ladung mal mittlere Faktorrendite, aufs Jahr gerechnet.
    data class Ladung(val faktor: Faktor, val wert: Double, val fehler: Double, val von: Double, val bis: Double, val beitragJahr: Double) {
        val belegt: Boolean get() = von > 0 || bis < 0
    }

    // alphaJahr: nicht erklaerte Rendite im Jahr (Achsenabschnitt mal zwoelf) mit 95-%-Spanne. zinsJahr und
    // renditeJahr: mittlerer Zins und mittlere Depotrendite im Zeitraum, aufs Jahr gerechnet (Summe der Monate).
    data class Ergebnis(
        val ladungen: List<Ladung>, val alphaJahr: Double, val alphaVon: Double, val alphaBis: Double, val r2: Double,
        val monate: Int, val von: Int, val bis: Int, val zinsJahr: Double, val renditeJahr: Double,
    ) {
        val alphaBelegt: Boolean get() = alphaVon > 0 || alphaBis < 0
        fun ladung(f: Faktor): Ladung? = ladungen.firstOrNull { it.faktor == f }
    }

    // Regression ueber die gemeinsamen Monate von Depot und Faktoren, hoechstens die letzten monateMax. depot: Rendite
    // des Depots je Monat (nicht Ueberrendite). null bei weniger als MIN_MONATE gemeinsamen Monaten.
    fun zerlegen(depot: Map<Int, Double>, daten: Faktordaten, monateMax: Int = Int.MAX_VALUE, mindest: Int = MIN_MONATE): Ergebnis? {
        val gemeinsam = gemeinsameMonate(depot, daten).takeLast(monateMax)
        return rechnen(depot, daten, gemeinsam, mindest)
    }

    fun gemeinsameMonate(depot: Map<Int, Double>, daten: Faktordaten): List<Int> =
        depot.keys.filter { m -> m in daten.zins && Faktor.entries.all { f -> daten.faktoren[f]?.containsKey(m) == true } }.sorted()

    private fun rechnen(depot: Map<Int, Double>, daten: Faktordaten, monate: List<Int>, mindest: Int): Ergebnis? {
        if (monate.size < mindest) return null
        val y = DoubleArray(monate.size) { depot.getValue(monate[it]) - daten.zins.getValue(monate[it]) }
        val x = Faktor.entries.map { f -> DoubleArray(monate.size) { daten.faktoren.getValue(f).getValue(monate[it]) } }
        val r = regression(y, x) ?: return null
        val t = tWert95(r.freiheitsgrade)
        val ladungen = Faktor.entries.mapIndexed { i, f ->
            val b = r.koeffizienten[i + 1]
            val se = r.fehler[i + 1]
            Ladung(f, b, se, b - t * se, b + t * se, b * x[i].average() * 12)
        }
        val a = r.koeffizienten[0] * 12
        val se = r.fehler[0] * 12
        return Ergebnis(
            ladungen, a, a - t * se, a + t * se, r.r2, monate.size, monate.first(), monate.last(),
            monate.map { daten.zins.getValue(it) }.average() * 12, monate.map { depot.getValue(it) }.average() * 12,
        )
    }

    // Rollende Regression: je Endmonat die Ladungen ueber die letzten fenster Monate. Leer unter ROLLEND_AB Monaten.
    fun rollend(depot: Map<Int, Double>, daten: Faktordaten, monateMax: Int = Int.MAX_VALUE, fenster: Int = FENSTER): List<Pair<Int, Ergebnis>> {
        val gemeinsam = gemeinsameMonate(depot, daten).takeLast(monateMax)
        if (gemeinsam.size < max(ROLLEND_AB, fenster + 1)) return emptyList()
        return (fenster..gemeinsam.size).mapNotNull { ende ->
            rechnen(depot, daten, gemeinsam.subList(ende - fenster, ende), fenster)?.let { gemeinsam[ende - 1] to it }
        }
    }
}
