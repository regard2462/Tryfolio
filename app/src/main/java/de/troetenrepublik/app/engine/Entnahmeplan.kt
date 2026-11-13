package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.HistorieJst.Art
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

// Entnahmeplan [0.78]: Ansparen bis zum Zielvermoegen und Entnahme ueber echte Renditefolgen. Alles real, also in heutiger
// Kaufkraft. Eine Reihe traegt je Periode die reale Rendite von Aktien und sicherer Anlage (Staatsanleihen) und die
// Inflation; heute ist eine Periode ein Jahr (HistorieJst), mit perJahr = 12 laufen Monatsreihen durch dieselbe Rechnung
// (dann beginnt in jedem Monat ein Verlauf). Entnommen wird zu Jahresbeginn, danach waechst der Rest mit der Mischung aus
// beiden Anlagen (jaehrlich auf den Aktienanteil zurueckgestellt) abzueglich Kosten.
object Entnahmeplan {

    const val WELT = HistorieRechner.WELT
    const val MAX_JAHRE = 60

    // Blockziehung: so viele Verlaeufe aus Bloecken von BLOCK_JAHRE Jahren, fester Startwert des Zufalls
    const val ZIEHUNGEN = 2_000
    const val MAX_ZIEHUNGEN = 5_000
    const val BLOCK_JAHRE = 5
    const val SAAT = 1871

    // Leitplanken nach Guyton und Klinger (2006): Die Entnahme sinkt um SCHRITT, wenn die Entnahmequote mehr als BAND ueber
    // der anfaenglichen liegt (nicht in den letzten OHNE_KUERZUNG Jahren), und steigt um SCHRITT, wenn sie mehr als BAND darunter liegt.
    const val BAND = 0.20
    const val SCHRITT = 0.10
    const val OHNE_KUERZUNG = 15

    // Zielvermoegen: die feste Entnahme haelt in diesem Anteil der Verlaeufe
    const val ZIEL_ANTEIL = 0.95

    // Fenster fuer das Ansparen in Jahren: das laengste, fuer das die Reihe genug Startpunkte hat
    val HORIZONTE = listOf(60, 50, 40, 30)

    // zeit: laufende Nummer der Periode (Jahr * perJahr + Platz im Jahr), aufsteigend; Luecken sind erlaubt.
    class Reihe(
        val kennung: String, val zeit: IntArray, val aktien: DoubleArray, val sicher: DoubleArray, val inflation: DoubleArray,
        val perJahr: Int = 1, val laender: Int = 1,
    ) {
        init { require(zeit.size == aktien.size && zeit.size == sicher.size && zeit.size == inflation.size && perJahr >= 1 && 12 % perJahr == 0) }
        fun jahr(i: Int): Int = zeit[i] / perJahr
        val erstes: Int get() = jahr(0)
        val letztes: Int get() = jahr(zeit.size - 1)
    }

    // Reihe eines Landes (A3 der Quelle) oder der Welt aus den Jahresrenditen: nur Jahre, in denen Aktien, Anleihen und
    // Inflation einen Wert haben. null unter HistorieRechner.MIN_JAHRE solchen Jahren.
    fun reihe(d: HistorieJst.Datei, kennung: String): Reihe? {
        fun basis(art: Art) = if (kennung == WELT) HistorieRechner.welt(d, art) else HistorieRechner.land(d, kennung, art)
        val a = basis(Art.AKTIEN) ?: return null
        val s = basis(Art.ANLEIHEN) ?: return null
        val i = basis(Art.INFLATION) ?: return null
        val sicher = s.jahre.indices.associate { s.jahre[it] to s.renditen[it] }
        val infl = i.jahre.indices.associate { i.jahre[it] to i.renditen[it] }
        val jahre = ArrayList<Int>()
        val ra = ArrayList<Double>(); val rs = ArrayList<Double>(); val ri = ArrayList<Double>()
        for (k in a.jahre.indices) {
            val j = a.jahre[k]
            val x = sicher[j] ?: continue
            val y = infl[j] ?: continue
            jahre += j; ra += a.renditen[k]; rs += x; ri += y
        }
        if (jahre.size < HistorieRechner.MIN_JAHRE) return null
        return Reihe(kennung, jahre.toIntArray(), ra.toDoubleArray(), rs.toDoubleArray(), ri.toDoubleArray(), 1, minOf(a.laender, s.laender, i.laender))
    }

    // Kennungen mit Reihe: USA zuerst, dann die Welt, dann die uebrigen Laender nach Kennung
    fun kennungen(d: HistorieJst.Datei): List<String> {
        val alle = (listOf("USA", WELT) + d.laender.keys.sorted()).distinct()
        return alle.filter { reihe(d, it) != null }
    }

    // Renditen und Inflation je Jahr eines Verlaufs
    class Folge(val aktien: DoubleArray, val sicher: DoubleArray, val inflation: DoubleArray) {
        val jahre: Int get() = aktien.size
        fun umgekehrt() = Folge(aktien.reversedArray(), sicher.reversedArray(), inflation.reversedArray())
    }

    // Plaetze, ab denen `jahre` Jahre ohne Luecke folgen
    fun starts(r: Reihe, jahre: Int): IntArray {
        val n = jahre * r.perJahr
        if (jahre < 1 || n > r.zeit.size) return IntArray(0)
        val aus = ArrayList<Int>()
        for (i in 0..r.zeit.size - n) if (r.zeit[i + n - 1] - r.zeit[i] == n - 1) aus += i
        return aus.toIntArray()
    }

    // Die Jahre ab Platz start, je Jahr aus perJahr Perioden verkettet
    fun folge(r: Reihe, start: Int, jahre: Int): Folge {
        fun jahr(q: DoubleArray, k: Int): Double {
            var w = 1.0
            for (i in 0 until r.perJahr) w *= 1.0 + q[start + k * r.perJahr + i]
            return w - 1.0
        }
        return Folge(DoubleArray(jahre) { jahr(r.aktien, it) }, DoubleArray(jahre) { jahr(r.sicher, it) }, DoubleArray(jahre) { jahr(r.inflation, it) })
    }

    // Wachstum der Mischung in einem Jahr nach Kosten
    private fun wachstum(aktie: Double, sicher: Double, anteil: Double, kosten: Double): Double =
        max(0.0, (anteil * (1.0 + aktie) + (1.0 - anteil) * (1.0 + sicher)) * (1.0 - kosten))

    // Reale Rendite der Mischung je Jahr ueber die ganze Reihe (geometrisches Mittel, Anteil), nach Kosten
    fun mittlereRendite(r: Reihe, aktienanteil: Double, kosten: Double): Double {
        if (r.zeit.isEmpty()) return 0.0
        var summe = 0.0
        for (i in r.zeit.indices) {
            val w = anteilig(r.aktien[i], r.sicher[i], aktienanteil)
            if (w <= 0.0) return -1.0
            summe += kotlin.math.ln(w)
        }
        return kotlin.math.exp(summe / r.zeit.size * r.perJahr) * (1.0 - kosten) - 1.0
    }

    private fun anteilig(aktie: Double, sicher: Double, anteil: Double) = anteil * (1.0 + aktie) + (1.0 - anteil) * (1.0 + sicher)

    enum class Regel { FEST, PROZENT, LEITPLANKEN, RESTLAUFZEIT }

    // Deutsche Steuer auf den Gewinn im verkauften Teil. satz: Abgeltungsteuer mit Soli als Anteil; teilfreistellung:
    // steuerfreier Anteil der Gewinne aus Aktienfonds; pauschbetrag je Jahr in heutiger Kaufkraft; gewinnanteil: Anteil
    // des Vermoegens am Anfang, der Kursgewinn ist.
    data class Steuer(val satz: Double, val teilfreistellung: Double, val pauschbetrag: Double, val gewinnanteil: Double)

    // entnahme: Betrag im ersten Jahr nach Steuer. untergrenze und obergrenze begrenzen die schwankenden Regeln als Anteil
    // der entnahme (0 und unendlich: ohne Grenze). zins: reale Rendite, mit der RESTLAUFZEIT das Vermoegen verteilt.
    data class Plan(
        val vermoegen: Double, val entnahme: Double, val jahre: Int, val aktienanteil: Double, val kosten: Double,
        val regel: Regel = Regel.FEST, val untergrenze: Double = 0.0, val obergrenze: Double = Double.POSITIVE_INFINITY,
        val steuer: Steuer? = null, val zins: Double = 0.0,
    )

    // Anteil des Vermoegens, den eine vorschuessige Rente ueber n Jahre beim Zins r im ersten Jahr auszahlt
    fun rentenAnteil(r: Double, n: Int): Double {
        if (n <= 1) return 1.0
        if (kotlin.math.abs(r) < 1e-9) return 1.0 / n
        return r / (1.0 - (1.0 + r).pow(-n)) / (1.0 + r)
    }

    class Verkauf(val brutto: Double, val steuer: Double, val netto: Double)

    // Verkauf fuer einen Nettobetrag aus einem Bestand mit Durchschnittseinstand: Vom Erloes ist der Anteil
    // (1 - einstand / wert) Gewinn, davon bleibt die Teilfreistellung steuerfrei, vom Rest geht `frei` (Pauschbetrag) ab.
    // Reicht der Bestand nicht, wird alles verkauft und netto ist kleiner als verlangt. Verluste bleiben ohne Wirkung.
    fun verkauf(netto: Double, wert: Double, einstand: Double, satz: Double, teilfreistellung: Double, frei: Double): Verkauf {
        if (netto <= 0.0 || wert <= 0.0) return Verkauf(0.0, 0.0, 0.0)
        val k = max(0.0, 1.0 - einstand / wert) * (1.0 - teilfreistellung)
        fun steuer(brutto: Double) = satz * max(0.0, brutto * k - frei)
        var brutto = if (netto * k <= frei) netto else (netto - satz * frei) / (1.0 - satz * k)
        if (brutto >= wert) brutto = wert
        val s = steuer(brutto)
        return Verkauf(brutto, s, brutto - s)
    }

    // Ein Verlauf. vermoegen: Stand zu Beginn jedes Jahres und am Ende (jahre + 1 Werte); entnahmen: Betrag nach Steuer je
    // Jahr; leerIm: erstes Jahr (ab 0), in dem das Geld fuer die Entnahme nicht reichte.
    class Lauf(val vermoegen: DoubleArray, val entnahmen: DoubleArray, val leerIm: Int?, val steuern: Double)

    fun lauf(p: Plan, f: Folge): Lauf {
        val n = min(p.jahre, f.jahre)
        val verm = DoubleArray(n + 1)
        val ent = DoubleArray(n)
        var v = max(0.0, p.vermoegen)
        var einstand = p.steuer?.let { v * (1.0 - it.gewinnanteil.coerceIn(0.0, 1.0)) } ?: v
        val tf = (p.steuer?.teilfreistellung ?: 0.0) * p.aktienanteil
        val quote0 = if (v > 0.0) p.entnahme / v else 0.0
        var w = max(0.0, p.entnahme)
        var leer: Int? = null
        var steuern = 0.0
        verm[0] = v
        for (k in 0 until n) {
            if (leer == null) {
                when (p.regel) {
                    Regel.FEST -> {}
                    Regel.PROZENT -> w = grenzen(quote0 * v, p)
                    Regel.RESTLAUFZEIT -> w = grenzen(v * rentenAnteil(p.zins, n - k), p)
                    Regel.LEITPLANKEN -> if (k > 0 && v > 0.0) {
                        // Kein Inflationsausgleich nach einem Jahr mit nominalem Verlust, wenn die Quote ueber der anfaenglichen liegt
                        val infl = f.inflation[k - 1]
                        val nominal = wachstum(f.aktien[k - 1], f.sicher[k - 1], p.aktienanteil, p.kosten) * (1.0 + infl) - 1.0
                        if (nominal < 0.0 && infl > 0.0 && w / v > quote0) w /= 1.0 + infl
                        if (w / v > quote0 * (1.0 + BAND)) { if (k < n - OHNE_KUERZUNG) w *= 1.0 - SCHRITT }
                        else if (w / v < quote0 * (1.0 - BAND)) w *= 1.0 + SCHRITT
                    }
                }
                val st = p.steuer
                val verkauf = if (st == null) min(w, v).let { Verkauf(it, 0.0, it) } else verkauf(w, v, einstand, st.satz, tf, st.pauschbetrag)
                if (w > 0.0 && verkauf.netto < w - 1e-6) leer = k
                ent[k] = verkauf.netto
                steuern += verkauf.steuer
                if (v > 0.0) einstand *= 1.0 - verkauf.brutto / v
                v = max(0.0, v - verkauf.brutto)
                if (leer != null) { v = 0.0; einstand = 0.0 }
            }
            v *= wachstum(f.aktien[k], f.sicher[k], p.aktienanteil, p.kosten)
            // Der Einstand ist ein fester Eurobetrag: in heutiger Kaufkraft schrumpft er mit der Inflation
            einstand /= max(0.01, 1.0 + f.inflation[k])
            verm[k + 1] = v
        }
        return Lauf(verm, ent, leer, steuern)
    }

    private fun grenzen(w: Double, p: Plan): Double {
        val unten = p.untergrenze * p.entnahme
        val oben = if (p.obergrenze.isFinite()) p.obergrenze * p.entnahme else Double.POSITIVE_INFINITY
        return max(0.0, if (oben < unten) unten else w.coerceIn(unten, oben))
    }

    private fun perzentil(sortiert: DoubleArray, q: Double): Double {
        if (sortiert.isEmpty()) return 0.0
        val pos = q * (sortiert.size - 1)
        val i = floor(pos).toInt().coerceIn(0, sortiert.size - 1)
        val j = min(i + 1, sortiert.size - 1)
        return sortiert[i] + (sortiert[j] - sortiert[i]) * (pos - i)
    }

    // Alle historischen Verlaeufe eines Plans. p10 bis p90: Vermoegen je Jahr; entMitte und entTief: mittlere und zehntschlechteste
    // Entnahme je Jahr; entMin: kleinste Entnahme ueberhaupt mit Startjahr und Jahr im Verlauf (ab 1); entMedian: mittlere
    // Entnahme ueber alle Jahre aller Verlaeufe. schlecht: der Start mit dem fruehesten Ende, sonst mit dem kleinsten Rest.
    class Ergebnis(
        val jahre: Int, val faelle: Int, val reicht: Int, val ersterStart: Int, val letzterStart: Int,
        val p10: DoubleArray, val p25: DoubleArray, val p50: DoubleArray, val p75: DoubleArray, val p90: DoubleArray,
        val entMitte: DoubleArray, val entTief: DoubleArray,
        val entMin: Double, val entMinStart: Int, val entMinJahr: Int, val entMedian: Double,
        val schlechtStart: Int, val schlechtPlatz: Int, val schlechtLeerIm: Int?, val schlechtEnde: Double,
        val steuerMitte: Double,
    ) {
        val anteil: Double get() = if (faelle > 0) reicht.toDouble() / faelle else 0.0
    }

    fun historisch(r: Reihe, p: Plan, check: () -> Unit = {}): Ergebnis? {
        val y = p.jahre
        if (y < 1 || y > MAX_JAHRE) return null
        val s = starts(r, y)
        if (s.size < HistorieRechner.MIN_FAELLE) return null
        val n = s.size
        val laeufe = Array(n) { check(); lauf(p, folge(r, s[it], y)) }
        fun spalte(k: Int, feld: (Lauf) -> DoubleArray) = DoubleArray(n) { feld(laeufe[it])[k] }.also { it.sort() }
        val p10 = DoubleArray(y + 1); val p25 = DoubleArray(y + 1); val p50 = DoubleArray(y + 1)
        val p75 = DoubleArray(y + 1); val p90 = DoubleArray(y + 1)
        for (k in 0..y) {
            val sp = spalte(k) { it.vermoegen }
            p10[k] = perzentil(sp, 0.10); p25[k] = perzentil(sp, 0.25); p50[k] = perzentil(sp, 0.50)
            p75[k] = perzentil(sp, 0.75); p90[k] = perzentil(sp, 0.90)
        }
        val entMitte = DoubleArray(y); val entTief = DoubleArray(y)
        for (k in 0 until y) {
            val sp = spalte(k) { it.entnahmen }
            entMitte[k] = perzentil(sp, 0.50); entTief[k] = perzentil(sp, 0.10)
        }
        var minWert = Double.MAX_VALUE; var minLauf = 0; var minJahr = 0
        val alle = DoubleArray(n * y)
        for (i in 0 until n) for (k in 0 until y) {
            val e = laeufe[i].entnahmen[k]
            alle[i * y + k] = e
            if (e < minWert) { minWert = e; minLauf = i; minJahr = k }
        }
        alle.sort()
        val schlecht = (0 until n).minWithOrNull(compareBy<Int> { laeufe[it].leerIm ?: Int.MAX_VALUE }.thenBy { laeufe[it].vermoegen[y] })!!
        val steuern = DoubleArray(n) { laeufe[it].steuern }.also { it.sort() }
        return Ergebnis(
            y, n, laeufe.count { it.leerIm == null }, r.jahr(s.first()), r.jahr(s.last()),
            p10, p25, p50, p75, p90, entMitte, entTief,
            minWert, r.jahr(s[minLauf]), minJahr + 1, perzentil(alle, 0.50),
            r.jahr(s[schlecht]), s[schlecht], laeufe[schlecht].leerIm, laeufe[schlecht].vermoegen[y],
            perzentil(steuern, 0.50),
        )
    }

    private fun folgen(r: Reihe, jahre: Int): List<Folge> = starts(r, jahre).map { folge(r, it, jahre) }

    private fun anteilReicht(folgen: List<Folge>, p: Plan): Double =
        if (folgen.isEmpty()) 0.0 else folgen.count { lauf(p, it).leerIm == null }.toDouble() / folgen.size

    // Hoechste Entnahme im ersten Jahr, mit der das Geld in mindestens `anteil` der historischen Verlaeufe reicht.
    // null ohne genug Verlaeufe.
    fun hoechsteEntnahme(r: Reihe, p: Plan, anteil: Double, check: () -> Unit = {}): Double? {
        val f = folgen(r, p.jahre)
        if (f.size < HistorieRechner.MIN_FAELLE || p.jahre > MAX_JAHRE) return null
        if (p.vermoegen <= 0.0) return 0.0
        var lo = 0.0
        var hi = p.vermoegen
        repeat(34) {
            check()
            val mid = (lo + hi) / 2
            if (anteilReicht(f, p.copy(entnahme = mid)) >= anteil) lo = mid else hi = mid
        }
        return lo
    }

    // Kleinstes Vermoegen, mit dem die feste Entnahme in mindestens `anteil` der historischen Verlaeufe ueber p.jahre haelt.
    // p.vermoegen zaehlt nicht. null ohne genug Verlaeufe oder wenn auch das 400-Fache der Entnahme nicht reicht.
    fun zielvermoegen(r: Reihe, p: Plan, anteil: Double = ZIEL_ANTEIL, check: () -> Unit = {}): Double? {
        val f = folgen(r, p.jahre)
        if (f.size < HistorieRechner.MIN_FAELLE || p.jahre > MAX_JAHRE) return null
        if (p.entnahme <= 0.0) return 0.0
        val fest = p.copy(regel = Regel.FEST)
        var lo = 0.0
        var hi = p.entnahme * 400.0
        if (anteilReicht(f, fest.copy(vermoegen = hi)) < anteil) return null
        repeat(40) {
            check()
            val mid = (lo + hi) / 2
            if (anteilReicht(f, fest.copy(vermoegen = mid)) >= anteil) hi = mid else lo = mid
        }
        return hi
    }

    // Blockziehung: Verlaeufe aus zufaellig gezogenen Bloecken echter Jahre (BLOCK_JAHRE am Stueck), mit festem Startwert
    // des Zufalls, damit dieselbe Eingabe dasselbe Ergebnis gibt.
    class Ziehung(val verlaeufe: Int, val reicht: Int, val block: Int) {
        val anteil: Double get() = if (verlaeufe > 0) reicht.toDouble() / verlaeufe else 0.0
    }

    fun ziehung(r: Reihe, p: Plan, verlaeufe: Int = ZIEHUNGEN, block: Int = BLOCK_JAHRE, saat: Int = SAAT, check: () -> Unit = {}): Ziehung? {
        val y = p.jahre
        if (y < 1 || y > MAX_JAHRE) return null
        val b = block.coerceIn(1, y)
        val s = starts(r, b)
        if (s.isEmpty()) return null
        val bloecke = s.map { folge(r, it, b) }
        val anzahl = verlaeufe.coerceIn(1, MAX_ZIEHUNGEN)
        val zufall = Random(saat)
        var reicht = 0
        val a = DoubleArray(y); val si = DoubleArray(y); val inf = DoubleArray(y)
        repeat(anzahl) {
            check()
            var k = 0
            while (k < y) {
                val bl = bloecke[zufall.nextInt(bloecke.size)]
                var i = 0
                while (i < b && k < y) { a[k] = bl.aktien[i]; si[k] = bl.sicher[i]; inf[k] = bl.inflation[i]; i++; k++ }
            }
            if (lauf(p, Folge(a, si, inf)).leerIm == null) reicht++
        }
        return Ziehung(anzahl, reicht, b)
    }

    // Ansparen bis zum Ziel: je Startpunkt ein Verlauf ueber hoechstens `horizont` Jahre. Die Sparrate fliesst am Monatsende
    // hinein und behaelt ihre Kaufkraft. dauer: Jahre bis zum Ziel je Verlauf, NaN wenn nicht erreicht. min, max und
    // median ueber die Verlaeufe; median ist null, wenn die Mehrheit das Ziel im Fenster nicht erreicht.
    // gewinnanteil: mittlerer Anteil der Kursgewinne am Vermoegen im Monat des Erreichens.
    class Ansparen(
        val ziel: Double, val horizont: Int, val faelle: Int, val erreicht: Int, val ersterStart: Int, val letzterStart: Int,
        val min: Double?, val max: Double?, val median: Double?, val schnellStart: Int?, val langsamStart: Int?,
        val gewinnanteil: Double?, val p10: DoubleArray, val p50: DoubleArray, val p90: DoubleArray,
    )

    fun ansparen(
        r: Reihe, start: Double, einstand: Double, monatlich: Double, ziel: Double, aktienanteil: Double, kosten: Double,
        check: () -> Unit = {},
    ): Ansparen? {
        val horizont = HORIZONTE.firstOrNull { starts(r, it).size >= HistorieRechner.MIN_FAELLE } ?: return null
        val s = starts(r, horizont)
        val n = s.size
        val teile = 12 / r.perJahr
        val dauer = DoubleArray(n) { Double.NaN }
        val gewinn = ArrayList<Double>()
        val jeJahr = Array(horizont + 1) { DoubleArray(n) }
        val kostenTeil = (1.0 - kosten).pow(1.0 / 12.0)
        for (p in 0 until n) {
            check()
            var v = max(0.0, start)
            var b = min(v, max(0.0, einstand))
            jeJahr[0][p] = v
            if (v >= ziel) { dauer[p] = 0.0; gewinn += if (v > 0) 1.0 - b / v else 0.0 }
            var monat = 0
            for (i in 0 until horizont * r.perJahr) {
                val q = s[p] + i
                val g = max(0.0, anteilig(r.aktien[q], r.sicher[q], aktienanteil)).pow(1.0 / teile) * kostenTeil
                val infl = max(0.01, 1.0 + r.inflation[q]).pow(1.0 / teile)
                repeat(teile) {
                    monat++
                    v = v * g + monatlich
                    b = b / infl + monatlich
                    if (dauer[p].isNaN() && v >= ziel) { dauer[p] = monat / 12.0; gewinn += if (v > 0) max(0.0, 1.0 - b / v) else 0.0 }
                    if (monat % 12 == 0) jeJahr[monat / 12][p] = v
                }
            }
        }
        val mit = (0 until n).filter { !dauer[it].isNaN() }
        val sortiert = mit.map { dauer[it] }.sorted()
        val noetig = (n + 1) / 2
        val schnell = mit.minByOrNull { dauer[it] }
        val langsam = mit.maxByOrNull { dauer[it] }
        val p10 = DoubleArray(horizont + 1); val p50 = DoubleArray(horizont + 1); val p90 = DoubleArray(horizont + 1)
        for (k in 0..horizont) {
            val sp = jeJahr[k].sortedArray()
            p10[k] = perzentil(sp, 0.10); p50[k] = perzentil(sp, 0.50); p90[k] = perzentil(sp, 0.90)
        }
        return Ansparen(
            ziel, horizont, n, mit.size, r.jahr(s.first()), r.jahr(s.last()),
            sortiert.firstOrNull(), sortiert.lastOrNull(), if (sortiert.size >= noetig) sortiert[noetig - 1] else null,
            schnell?.let { r.jahr(s[it]) }, langsam?.let { r.jahr(s[it]) },
            if (gewinn.isEmpty()) null else gewinn.sorted()[gewinn.size / 2], p10, p50, p90,
        )
    }

    // Ziel und Weg zusammen: Das Zielvermoegen haengt mit Steuer vom Gewinnanteil beim Erreichen ab, der Gewinnanteil vom
    // Weg dorthin. Drei Durchgaenge reichen, danach aendert sich das Ziel kaum noch. p traegt Entnahme, Dauer, Aktienanteil,
    // Kosten und Steuer der Entnahmezeit; p.vermoegen zaehlt nicht.
    class Freiheit(val ziel: Double, val ansparen: Ansparen)

    fun freiheit(r: Reihe, start: Double, einstand: Double, monatlich: Double, p: Plan, check: () -> Unit = {}): Freiheit? {
        var steuer = p.steuer
        var aus: Freiheit? = null
        val durchgaenge = if (steuer == null) 1 else 3
        repeat(durchgaenge) {
            val ziel = zielvermoegen(r, p.copy(steuer = steuer), check = check) ?: return aus
            val weg = ansparen(r, start, einstand, monatlich, ziel, p.aktienanteil, p.kosten, check) ?: return aus
            aus = Freiheit(ziel, weg)
            steuer = steuer?.let { st -> st.copy(gewinnanteil = weg.gewinnanteil ?: st.gewinnanteil) }
        }
        return aus
    }

    // Zielvermoegen fuer die Anzeige: auf 1.000 Euro gerundet (ab 10.000), darunter auf 100; die Rechnung bleibt genau
    fun zielAnzeige(v: Double): Double {
        val schritt = if (kotlin.math.abs(v) >= 10_000.0) 1_000.0 else 100.0
        return kotlin.math.round(v / schritt) * schritt
    }

    // Jahre aufgerundet, fuer die Anzeige einer Dauer
    fun volleJahre(dauer: Double): Int = ceil(dauer - 1e-9).toInt().coerceAtLeast(0)

    class Quelle(val name: String, val url: String)

    // Arbeiten, aus denen die Entnahmeregeln stammen
    val QUELLEN = listOf(
        Quelle("William P. Bengen: Determining Withdrawal Rates Using Historical Data. Journal of Financial Planning (1994)", "https://www.financialplanningassociation.org/sites/default/files/2021-04/MAR04%20Determining%20Withdrawal%20Rates%20Using%20Historical%20Data.pdf"),
        Quelle("Philip L. Cooley, Carl M. Hubbard, Daniel T. Walz: Retirement Savings: Choosing a Withdrawal Rate That Is Sustainable. AAII Journal (1998)", "https://www.aaii.com/journal/article/retirement-savings-choosing-a-withdrawal-rate-that-is-sustainable"),
        Quelle("Jonathan T. Guyton, William J. Klinger: Decision Rules and Maximum Initial Withdrawal Rates. Journal of Financial Planning (2006)", "https://www.financialplanningassociation.org/sites/default/files/2021-11/2006%20-%20Guyton%20and%20Klinger%20-%20Decision%20Rules%20and%20SWR%20%281%29.PDF"),
    )
}
