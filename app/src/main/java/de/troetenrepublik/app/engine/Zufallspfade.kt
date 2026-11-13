package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.max
import kotlin.math.min

// Zufallspfade (0.78): neue Kursverlaeufe aus den echten Handelstagen eines Zeitraums. Jeder Tag ist ein Baustein aus
// Eroeffnung, Hoch, Tief und Schluss im Verhaeltnis zum Schluss des Vortags; gezogen wird immer der ganze Tag, damit
// Regeln auf Eroeffnung, Hoch und Tief weiter rechnen. Die Kerzen vor dem Zeitraum bleiben echt (die Indikatoren
// schwingen daran ein), die Zeitpunkte bleiben die echten. Alles mit festem Startwert wiederholbar.
object Zufallspfade {

    // EINFACH: jeder Tag einzeln mit Zuruecklegen gezogen. BLOCK: stationaere Blockziehung, die Blocklaenge ist
    // geometrisch verteilt. UMKEHR: die Tage bleiben in ihrer Folge, abschnittsweise sind Steigen und Fallen vertauscht.
    enum class Verfahren { EINFACH, BLOCK, UMKEHR }

    // Mittlere Blocklaengen der Blockziehung in Handelstagen; die Pfade verteilen sich gleichmaessig darauf.
    val BLOCKLAENGEN = listOf(10, 20, 60, 120)

    // Mittlere Laenge eines Abschnitts gleicher Richtung bei der Trendumkehr: je Tag wechselt die Richtung mit
    // Wahrscheinlichkeit 1 durch diesen Wert, der erste Abschnitt beginnt mit gleicher Chance gedreht oder echt.
    const val UMKEHR_BLOCK = 60

    // Ab dieser Quote der Pfade gilt ein Vorsprung vor Kaufen und Halten als gehalten.
    const val HAELT_AB = 0.5

    private const val MIN_TAGE = 250

    // Tage start bis bis (ausschliesslich) im Verhaeltnis zum Schluss des Vortags. Fehlende Eroeffnung, Hoch oder Tief
    // ersetzt der Schluss; Hoch und Tief umschliessen immer Eroeffnung und Schluss.
    class Bausteine(val kerzen: List<Candle>, val start: Int, val bis: Int) {
        val tage = bis - start
        val schluss = DoubleArray(tage)
        val eroeffnung = DoubleArray(tage)
        val hoch = DoubleArray(tage)
        val tief = DoubleArray(tage)

        init {
            for (i in start until bis) {
                val k = kerzen[i]
                val vor = kerzen[i - 1].close
                val c = k.close / vor
                val o = (k.open.takeIf { it.isFinite() && it > 0 } ?: k.close) / vor
                val h = (k.high.takeIf { it.isFinite() && it > 0 } ?: k.close) / vor
                val l = (k.low.takeIf { it.isFinite() && it > 0 } ?: k.close) / vor
                schluss[i - start] = c
                eroeffnung[i - start] = o
                hoch[i - start] = max(h, max(o, c))
                tief[i - start] = min(l, min(o, c))
            }
        }
    }

    // Gezogene Tage eines Pfads: quelle[j] ist der Baustein fuer Tag j, gedreht[j] ob er gespiegelt wird. Bei Taktik
    // gilt derselbe Zug fuer alle Reihen (gehandelter Wert, Signalwert, Ausweichwert), der Zusammenhang bleibt erhalten.
    class Zug(val quelle: IntArray, val gedreht: BooleanArray)

    // Zufallszug fuer m Tage. Verbrauch des Zufallsstroms wie bisher: erst (bei UMKEHR) die Startrichtung, dann je Tag.
    fun zug(m: Int, verfahren: Verfahren, rng: PortfolioTools.Rng, block: Int = UMKEHR_BLOCK): Zug {
        val wechsel = 1.0 / max(block, 1)
        val quelle = IntArray(m)
        val gedreht = BooleanArray(m)
        var q = 0
        var g = verfahren == Verfahren.UMKEHR && rng.nextDouble() < 0.5
        for (j in 0 until m) {
            when (verfahren) {
                Verfahren.EINFACH -> q = zug(rng, m)
                Verfahren.BLOCK -> q = if (j == 0 || rng.nextDouble() < wechsel) zug(rng, m) else (q + 1) % m
                Verfahren.UMKEHR -> {
                    q = j
                    if (j > 0 && rng.nextDouble() < wechsel) g = !g
                }
            }
            quelle[j] = q
            gedreht[j] = g
        }
        return Zug(quelle, gedreht)
    }

    // Pfad aus einem Zug: echte Kerzen bis start, danach neu zusammengesetzt. Bei der Umkehr wird ein Tag im
    // Logarithmus gespiegelt: aus dem Faktor f wird 1 / f, Hoch und Tief tauschen.
    fun pfad(b: Bausteine, z: Zug): List<Candle> {
        val aus = ArrayList<Candle>(b.bis)
        for (i in 0 until b.start) aus.add(b.kerzen[i])
        var vor = b.kerzen[b.start - 1].close
        for (j in 0 until b.tage) {
            val quelle = z.quelle[j]
            val c: Double
            val o: Double
            val h: Double
            val l: Double
            if (z.gedreht[j]) {
                c = 1.0 / b.schluss[quelle]
                o = 1.0 / b.eroeffnung[quelle]
                h = 1.0 / b.tief[quelle]
                l = 1.0 / b.hoch[quelle]
            } else {
                c = b.schluss[quelle]
                o = b.eroeffnung[quelle]
                h = b.hoch[quelle]
                l = b.tief[quelle]
            }
            val echt = b.kerzen[b.start + j]
            aus.add(Candle(echt.t, vor * o, vor * h, vor * l, vor * c, echt.volume))
            vor *= c
        }
        return aus
    }

    // Ein Pfad: echte Kerzen bis start, danach neu zusammengesetzt. block: mittlere Blocklaenge (BLOCK) oder mittlere
    // Laenge eines Abschnitts gleicher Richtung (UMKEHR); bei EINFACH ohne Bedeutung.
    fun pfad(b: Bausteine, verfahren: Verfahren, rng: PortfolioTools.Rng, block: Int = UMKEHR_BLOCK): List<Candle> =
        pfad(b, zug(b.tage, verfahren, rng, block))

    private fun zug(rng: PortfolioTools.Rng, m: Int): Int = (rng.nextDouble() * m).toInt().coerceIn(0, m - 1)

    // Ergebnis eines Verfahrens. rendite und rueckgang: Rendite pro Jahr und groesster Rueckgang der Regel je Pfad,
    // aufsteigend. mindestens: Anteil der Pfade mit mindestens der echten Rendite. hoechstens: Anteil mit hoechstens
    // dem echten Rueckgang. beides: beides auf demselben Pfad. lageRendite: Anteil der Pfade unter der echten Rendite,
    // lageRueckgang: Anteil mit groesserem Rueckgang als echt. schlaegtRendite und schlaegtRueckgang: Anteil der
    // Pfade, auf denen die Regel mehr Rendite bzw. einen kleineren Rueckgang hatte als Kaufen und Halten auf
    // demselben Pfad.
    class Auswertung(
        val verfahren: Verfahren,
        val pfade: Int,
        val rendite: DoubleArray,
        val rueckgang: DoubleArray,
        val mindestens: Double,
        val hoechstens: Double,
        val beides: Double,
        val lageRendite: Double,
        val lageRueckgang: Double,
        val schlaegtRendite: Double,
        val schlaegtRueckgang: Double,
    )

    // Echte Geschichte (Regel und Kaufen und Halten im selben Zeitraum) und die drei Verfahren.
    class Ergebnis(
        val regelRendite: Double,
        val regelRueckgang: Double,
        val haltenRendite: Double,
        val haltenRueckgang: Double,
        val verfahren: List<Auswertung>,
    )

    // Pfade je Verfahren nach Laenge des Zeitraums: rund 600.000 Handelstage je Verfahren, mindestens 200, hoechstens
    // 1.000 Pfade. Auf einem Rechner dauert das unter einer Sekunde, ein Handy bleibt bei wenigen Sekunden.
    fun pfadzahl(tage: Int): Int = (600_000 / max(tage, 1)).coerceIn(200, 1000)

    // Regel und Kaufen und Halten auf pfade Pfaden je Verfahren, mit denselben Kosten und demselben Ausstieg wie in
    // der echten Geschichte. Null bei unter 250 Tagen oder ohne Rendite pro Jahr. abbruch wird je Pfad aufgerufen.
    fun rechnen(
        kerzen: List<Candle>, ab: Int, kosten: StrategieLabor.Kosten, regel: StrategieLabor.Regel,
        ausstieg: StrategieLabor.Ausstieg = StrategieLabor.REGELAUSSTIEG,
        pfade: Int = pfadzahl(kerzen.size - max(ab, 1)), seed: Long = 7L, abbruch: () -> Unit = {},
        taktik: Taktik.Reihen? = null,
    ): Ergebnis? {
        val n = kerzen.size
        val start = max(ab, 1)
        if (n - start < MIN_TAGE || pfade < 1) return null
        val halten = StrategieLabor.Regel(StrategieLabor.Art.HALTEN)
        // Mit Taktik rechnen Signal und Ausweichwert auf denselben Handelstagen mit; die Pfade ziehen alle Reihen gemeinsam
        val d = taktik?.daten(n) ?: StrategieLabor.Daten(kerzen)
        val echt = StrategieLabor.simulieren(d, StrategieLabor.ziel(d, regel), start, n, kosten, leicht = true, ausstieg = ausstieg) ?: return null
        val echtHalten = StrategieLabor.simulieren(d, StrategieLabor.ziel(d, halten), start, n, kosten, leicht = true) ?: return null
        val echtRendite = echt.renditeJahr ?: return null
        val haltenRendite = echtHalten.renditeJahr ?: return null
        val b = Bausteine(kerzen, start, n)
        val bSignal = taktik?.signal?.let { Bausteine(it.subList(0, n), start, n) }
        val bAusweich = taktik?.ausweich?.let { Bausteine(it.subList(0, n), start, n) }
        val immer = BooleanArray(n) { true }
        val liste = Verfahren.entries.map { v ->
            // Eigener Zufallsstrom je Verfahren: das Ergebnis eines Verfahrens haengt nicht von den anderen ab
            val rng = PortfolioTools.Rng(seed * 1_000_003L + v.ordinal + 1)
            val renditen = DoubleArray(pfade)
            val rueckgaenge = DoubleArray(pfade)
            var anzahl = 0
            var mindestens = 0
            var hoechstens = 0
            var beides = 0
            var schlaegtRendite = 0
            var schlaegtRueckgang = 0
            for (p in 0 until pfade) {
                abbruch()
                val block = if (v == Verfahren.BLOCK) BLOCKLAENGEN[p % BLOCKLAENGEN.size] else UMKEHR_BLOCK
                val pd = if (bSignal == null && bAusweich == null) {
                    StrategieLabor.Daten(pfad(b, v, rng, block))
                } else {
                    val z = zug(b.tage, v, rng, block)
                    StrategieLabor.Daten(pfad(b, z), signal = bSignal?.let { StrategieLabor.Daten(pfad(it, z)) }, ausweich = bAusweich?.let { StrategieLabor.Daten(pfad(it, z)) })
                }
                val r = StrategieLabor.simulieren(pd, StrategieLabor.ziel(pd, regel), start, n, kosten, leicht = true, ausstieg = ausstieg) ?: continue
                val h = StrategieLabor.simulieren(pd, immer, start, n, kosten, leicht = true) ?: continue
                val rj = r.renditeJahr ?: continue
                val hj = h.renditeJahr ?: continue
                renditen[anzahl] = rj
                rueckgaenge[anzahl] = r.maxRueckgang
                anzahl++
                val gut = rj >= echtRendite - 1e-12
                val klein = r.maxRueckgang <= echt.maxRueckgang + 1e-12
                if (gut) mindestens++
                if (klein) hoechstens++
                if (gut && klein) beides++
                if (rj > hj) schlaegtRendite++
                if (r.maxRueckgang < h.maxRueckgang) schlaegtRueckgang++
            }
            if (anzahl == 0) return null
            val z = anzahl.toDouble()
            Auswertung(
                v, anzahl,
                renditen.copyOf(anzahl).also { it.sort() }, rueckgaenge.copyOf(anzahl).also { it.sort() },
                mindestens / z, hoechstens / z, beides / z,
                1.0 - mindestens / z, 1.0 - hoechstens / z,
                schlaegtRendite / z, schlaegtRueckgang / z,
            )
        }
        return Ergebnis(echtRendite, echt.maxRueckgang, haltenRendite, echtHalten.maxRueckgang, liste)
    }

    // Kernaussage zu einer Groesse: ob die Regel in der echten Geschichte vor Kaufen und Halten lag, und in welchen
    // Verfahren sie auf mindestens der Haelfte der Pfade vorn lag (haelt) oder nicht (faellt).
    class Kern(val echtVorn: Boolean, val haelt: List<Verfahren>, val faellt: List<Verfahren>)

    fun kernRendite(e: Ergebnis): Kern =
        Kern(e.regelRendite > e.haltenRendite, e.verfahren.filter { it.schlaegtRendite >= HAELT_AB }.map { it.verfahren }, e.verfahren.filter { it.schlaegtRendite < HAELT_AB }.map { it.verfahren })

    fun kernRueckgang(e: Ergebnis): Kern =
        Kern(e.regelRueckgang < e.haltenRueckgang, e.verfahren.filter { it.schlaegtRueckgang >= HAELT_AB }.map { it.verfahren }, e.verfahren.filter { it.schlaegtRueckgang < HAELT_AB }.map { it.verfahren })

    // Wert an der Stelle q (0 bis 1) einer aufsteigenden Reihe, zwischen den Nachbarn linear.
    fun quantil(sortiert: DoubleArray, q: Double): Double {
        if (sortiert.isEmpty()) return Double.NaN
        val x = q.coerceIn(0.0, 1.0) * (sortiert.size - 1)
        val i = x.toInt().coerceAtMost(sortiert.size - 1)
        val j = min(i + 1, sortiert.size - 1)
        return sortiert[i] + (sortiert[j] - sortiert[i]) * (x - i)
    }
}
