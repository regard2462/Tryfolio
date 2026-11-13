package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Momentum-Rotation (0.78): Aus 2 bis 10 Werten bleiben die im Depot, die in den letzten Monaten am staerksten
// gestiegen sind. Geordnet wird am letzten Handelstag jedes Monats nach dem Schlusskurs, gehandelt zur Eroeffnung des
// naechsten Handelstags, mit Spread je Seite und Ordergebuehr je Order wie im Strategietest. Verglichen wird mit dem
// einmal gleich gewichtet gekauften und dann gehaltenen Universum. Ergebnisse haben die Form des Strategietests, damit
// Kennzahlen, Aussagekraft, Lern- und Pruefzeitraum und Handelsstil dieselben Funktionen nutzen. Ohne Steuern.
object Rotation {

    const val MIN_WERTE = 2
    const val MAX_WERTE = 10

    // Rueckblick in Monaten zur Wahl
    val MONATE = listOf(1, 3, 6, 9, 12)

    // Handelstage, ueber die die Schwankung fuer die Gewichtung gemessen wird (rund ein Vierteljahr)
    const val SCHWANKUNG_TAGE = 63

    // Umschichtungen eines gehaltenen Werts unter diesem Anteil des Depotwerts entfallen (spart Ordergebuehren)
    const val BAGATELLE = 0.01

    // Mindestzahl Handelstage im Zeitraum
    const val MIN_TAGE = 120

    private const val JAHR_MS = 365.25 * 86_400_000.0

    enum class Gewichtung { GLEICH, SCHWANKUNG }

    // monate: Rueckblick. halten: Zahl der besten Werte im Depot. gewichtung: gleich oder invers zur Schwankung.
    // absolut: ein Wert bleibt nur, wenn seine Rendite im Rueckblick ueber der des Ausweichwerts liegt (ohne
    // Ausweichwert ueber null); freie Plaetze gehen in den Ausweichwert, ohne ihn in Bargeld.
    data class Wahl(val monate: Int = 6, val halten: Int = 2, val gewichtung: Gewichtung = Gewichtung.GLEICH, val absolut: Boolean = false)

    // Kurse der Werte auf gemeinsamen Handelstagen (Taktik.gemeinsam), der Ausweichwert auf denselben Tagen.
    class Daten(val werte: List<StrategieLabor.Daten>, val ausweich: StrategieLabor.Daten? = null) {
        val n: Int = werte.firstOrNull()?.kerzen?.size ?: 0
        init {
            require(werte.all { it.kerzen.size == n })
            require(ausweich == null || ausweich.kerzen.size == n)
        }

        // Alle gehandelten Werte, der Ausweichwert zuletzt
        val alle: List<StrategieLabor.Daten> = werte + listOfNotNull(ausweich)

        // Letzte Handelstage der Monate, aufsteigend
        val monatsenden: IntArray by lazy { if (n == 0) IntArray(0) else (0 until n).filter { werte[0].monatsende(it) }.toIntArray() }
    }

    fun daten(werte: List<List<Candle>>, ausweich: List<Candle>?): Daten {
        val g = Taktik.gemeinsam(werte + listOfNotNull(ausweich))
        return Daten(g.take(werte.size).map { StrategieLabor.Daten(it) }, if (ausweich != null) StrategieLabor.Daten(g.last()) else null)
    }

    // Rang eines Werts an einem Monatsende: Stelle in Daten.werte und Rendite im Rueckblick als Anteil.
    data class Rang(val wert: Int, val rendite: Double)

    // Rangliste am Monatsende mit der Nummer j (in Daten.monatsenden), beste Rendite zuerst; leer, solange der
    // Rueckblick nicht gefuellt ist.
    fun rangliste(d: Daten, j: Int, monate: Int): List<Rang> {
        if (j < monate || j >= d.monatsenden.size) return emptyList()
        val heute = d.monatsenden[j]
        val damals = d.monatsenden[j - monate]
        return d.werte.mapIndexed { k, w -> Rang(k, rendite(w, damals, heute)) }.sortedByDescending { it.rendite }
    }

    private fun rendite(w: StrategieLabor.Daten, von: Int, bis: Int): Double =
        if (w.schluss[von] > 0) w.schluss[bis] / w.schluss[von] - 1.0 else 0.0

    // Standardabweichung der Tagesrenditen der letzten SCHWANKUNG_TAGE Handelstage bis einschliesslich Tag i.
    internal fun schwankung(w: StrategieLabor.Daten, i: Int): Double? {
        if (i < SCHWANKUNG_TAGE) return null
        val r = (i - SCHWANKUNG_TAGE + 1..i).map { if (w.schluss[it - 1] > 0) w.schluss[it] / w.schluss[it - 1] - 1.0 else 0.0 }
        val sd = sqrt(StrategieLabor.varianz(r))
        return sd.takeIf { it > 1e-9 }
    }

    // Zielgewichte am Monatsende j: ein Gewicht je Wert, dahinter (mit Ausweichwert) das des Ausweichwerts. Die Summe
    // liegt bei hoechstens 1, der Rest ist Bargeld. Null, solange der Rueckblick nicht gefuellt ist.
    fun gewichte(d: Daten, j: Int, w: Wahl): DoubleArray? {
        val rang = rangliste(d, j, w.monate)
        if (rang.isEmpty()) return null
        val heute = d.monatsenden[j]
        val plaetze = w.halten.coerceIn(1, d.werte.size)
        val schwelle = when {
            !w.absolut -> Double.NEGATIVE_INFINITY
            d.ausweich != null -> rendite(d.ausweich, d.monatsenden[j - w.monate], heute)
            else -> 0.0
        }
        val gewaehlt = rang.take(plaetze).filter { it.rendite > schwelle }
        val g = DoubleArray(d.alle.size)
        val anteil = gewaehlt.size.toDouble() / plaetze
        val invers = if (w.gewichtung == Gewichtung.SCHWANKUNG) gewaehlt.map { r -> schwankung(d.werte[r.wert], heute)?.let { 1.0 / it } } else emptyList()
        if (invers.isNotEmpty() && invers.all { it != null }) {
            val summe = invers.sumOf { it!! }
            gewaehlt.forEachIndexed { i, r -> g[r.wert] = anteil * invers[i]!! / summe }
        } else {
            gewaehlt.forEach { g[it.wert] = 1.0 / plaetze }
        }
        if (d.ausweich != null) g[d.werte.size] = max(0.0, 1.0 - g.sum())
        return g
    }

    // Zielgewichte nach dem Schluss jedes Tags; null an Tagen ohne Entscheidung.
    fun ziele(d: Daten, w: Wahl): Array<DoubleArray?> {
        val z = arrayOfNulls<DoubleArray>(d.n)
        for (j in d.monatsenden.indices) gewichte(d, j, w)?.let { z[d.monatsenden[j]] = it }
        return z
    }

    // Erster Tag, an dem die Rotation handeln kann: der Handelstag nach der ersten Entscheidung. d.n ohne Entscheidung.
    fun ersterTag(d: Daten, w: Wahl): Int = if (d.monatsenden.size > w.monate) d.monatsenden[w.monate] + 1 else d.n

    // Spielt die Rotation von (einschliesslich) bis bis (ausschliesslich) mit frischem Startkapital durch. Am ersten
    // Tag gilt die juengste Entscheidung davor, danach wird am Tag nach jeder Entscheidung umgeschichtet.
    fun simulieren(d: Daten, ziele: Array<DoubleArray?>, von: Int, bis: Int, kosten: StrategieLabor.Kosten, start: Double = StrategieLabor.START): StrategieLabor.Ergebnis? {
        if (von < 1 || bis > d.n) return null
        val anfang = (von - 1 downTo 0).firstNotNullOfOrNull { ziele[it] } ?: return null
        return lauf(d, von, bis, kosten, start) { i -> if (i == von) anfang else ziele[i - 1] }
    }

    // Kaufen und Halten: am ersten Tag alle Werte des Universums zu gleichen Teilen, danach keine Order.
    fun halten(d: Daten, von: Int, bis: Int, kosten: StrategieLabor.Kosten, start: Double = StrategieLabor.START): StrategieLabor.Ergebnis? {
        if (d.werte.isEmpty()) return null
        val gleich = DoubleArray(d.alle.size) { if (it < d.werte.size) 1.0 / d.werte.size else 0.0 }
        return lauf(d, von, bis, kosten, start) { i -> if (i == von) gleich else null }
    }

    // Depot aus mehreren Werten: zielAm(i) nennt die Gewichte, auf die zur Eroeffnung des Tags i umgeschichtet wird
    // (null = keine Order). Erst Verkaeufe, dann Kaeufe aus dem freien Geld. Ein Geschaeft ist ein Wert des Universums
    // vom ersten Kauf bis zum vollstaendigen Verkauf; sein Ergebnis sind alle Erloese durch alle Kaufbetraege, nach
    // Kosten. Der Ausweichwert zaehlt nicht als Geschaeft.
    private fun lauf(d: Daten, von: Int, bis: Int, kosten: StrategieLabor.Kosten, start: Double, zielAm: (Int) -> DoubleArray?): StrategieLabor.Ergebnis? {
        if (von < 0 || bis > d.n || bis - von < 2) return null
        val m = d.alle.size
        val universum = d.werte.size
        val zeit = d.werte[0].kerzen
        val stueck = DoubleArray(m)
        val einsatz = DoubleArray(m)
        val erloes = DoubleArray(m)
        val kaufTag = IntArray(m)
        val geschaefte = ArrayList<StrategieLabor.Geschaeft>()
        var bar = start
        var trades = 0
        var gewinner = 0
        var tageDrin = 0
        val werte = DoubleArray(bis - von)
        val renditen = ArrayList<Double>(bis - von)
        var hoechst = start
        var rueckgang = 0.0
        var vorher = start

        fun schliessen(k: Int, i: Int, offen: Boolean) {
            if (k < universum && einsatz[k] > 0) {
                if (erloes[k] > einsatz[k]) gewinner++
                geschaefte += StrategieLabor.Geschaeft(zeit[kaufTag[k]].t, zeit[i].t, erloes[k] / einsatz[k] - 1.0, i - kaufTag[k], offen)
            }
            stueck[k] = 0.0
            einsatz[k] = 0.0
            erloes[k] = 0.0
        }

        for (i in von until bis) {
            val ziel = zielAm(i)
            if (ziel != null) {
                val kurs = DoubleArray(m) { d.alle[it].eroeffnung(i) }
                val gesamt = bar + (0 until m).sumOf { stueck[it] * kurs[it] }
                val diff = DoubleArray(m) { gesamt * ziel[it] - stueck[it] * kurs[it] }
                for (k in 0 until m) {
                    if (stueck[k] <= 0.0 || diff[k] >= 0) continue
                    val ganz = ziel[k] <= 0.0
                    if (!ganz && -diff[k] < BAGATELLE * gesamt) continue
                    val menge = if (ganz) stueck[k] else minOf(stueck[k], -diff[k] / kurs[k])
                    val geld = menge * Pricing.bid(kurs[k], kosten.spreadBps) - kosten.gebuehr
                    bar += geld
                    erloes[k] += geld
                    stueck[k] -= menge
                    if (ganz) schliessen(k, i, false)
                }
                val kaeufe = (0 until m).filter { diff[it] > 0 && kurs[it] > 0 && (stueck[it] <= 0.0 || diff[it] >= BAGATELLE * gesamt) }
                val bedarf = kaeufe.sumOf { diff[it] }
                val faktor = if (bedarf > bar && bedarf > 0) max(0.0, bar) / bedarf else 1.0
                for (k in kaeufe) {
                    val geld = diff[k] * faktor
                    if (geld <= kosten.gebuehr) continue
                    if (stueck[k] <= 0.0) {
                        kaufTag[k] = i
                        if (k < universum) trades++
                    }
                    stueck[k] += (geld - kosten.gebuehr) / Pricing.ask(kurs[k], kosten.spreadBps)
                    einsatz[k] += geld
                    bar -= geld
                }
            }
            if ((0 until universum).any { stueck[it] > 0 }) tageDrin++
            val wert = bar + (0 until m).sumOf { stueck[it] * d.alle[it].schluss[i] }
            werte[i - von] = wert
            hoechst = max(hoechst, wert)
            if (hoechst > 0) rueckgang = max(rueckgang, 1.0 - wert / hoechst)
            renditen += if (vorher > 0) wert / vorher - 1.0 else 0.0
            vorher = wert
        }
        // Offene Werte zaehlen zum Schlusskurs und als Geschaeft
        for (k in 0 until universum) {
            if (stueck[k] <= 0.0) continue
            erloes[k] += stueck[k] * Pricing.bid(d.alle[k].schluss[bis - 1], kosten.spreadBps) - kosten.gebuehr
            schliessen(k, bis - 1, true)
        }
        val endwert = werte.last()
        val jahre = (zeit[bis - 1].t - zeit[von].t) / JAHR_MS
        return StrategieLabor.Ergebnis(
            endwert = endwert,
            renditeJahr = if (jahre < 0.25) null else if (endwert <= 0) -1.0 else (endwert / start).pow(1.0 / jahre) - 1.0,
            maxRueckgang = rueckgang,
            trades = trades,
            treffer = if (trades > 0) gewinner.toDouble() / trades else null,
            imMarkt = tageDrin.toDouble() / (bis - von),
            verlauf = (von until bis).map { zeit[it].t to werte[it - von] },
            renditen = renditen,
            sharpe = StrategieLabor.sharpe(renditen),
            jahre = jahre,
            geschaefte = geschaefte.sortedBy { it.ausstieg },
        )
    }

    // von: erster gerechneter Tag, grenze: erster Tag des Pruefzeitraums. Rotation und Kaufen und Halten je im ganzen
    // Zeitraum, im Lernzeitraum und im Pruefzeitraum, jeder Teil mit frischem Startkapital. heute: Gewichte der
    // juengsten Entscheidung (wie in gewichte), rang: Rangliste dazu.
    class Bericht(
        val von: Int, val grenze: Int,
        val gesamt: StrategieLabor.Ergebnis, val lern: StrategieLabor.Ergebnis, val pruef: StrategieLabor.Ergebnis,
        val haltenGesamt: StrategieLabor.Ergebnis, val haltenLern: StrategieLabor.Ergebnis, val haltenPruef: StrategieLabor.Ergebnis,
        val heute: DoubleArray, val rang: List<Rang>, val entscheidung: Long,
    )

    // Rechnet ab Kerze ab, fruehestens ab dem ersten Tag mit einer Entscheidung und ab fruehestens (damit mehrere
    // Rueckblicke denselben Zeitraum vergleichen). Null bei unter MIN_TAGE Handelstagen.
    fun pruefen(d: Daten, ab: Int, kosten: StrategieLabor.Kosten, w: Wahl, fruehestens: Int = 0): Bericht? {
        if (d.werte.size < MIN_WERTE || d.werte.size > MAX_WERTE) return null
        val von = maxOf(ab, ersterTag(d, w), fruehestens, 1)
        val n = d.n
        if (n - von < MIN_TAGE) return null
        val z = ziele(d, w)
        val s = StrategieLabor.teilung(von, n)
        val letzte = d.monatsenden.indices.lastOrNull { z[d.monatsenden[it]] != null } ?: return null
        return Bericht(
            von, s,
            simulieren(d, z, von, n, kosten) ?: return null,
            simulieren(d, z, von, s, kosten) ?: return null,
            simulieren(d, z, s, n, kosten) ?: return null,
            halten(d, von, n, kosten) ?: return null,
            halten(d, von, s, kosten) ?: return null,
            halten(d, s, n, kosten) ?: return null,
            z[d.monatsenden[letzte]]!!, rangliste(d, letzte, w.monate), d.werte[0].kerzen[d.monatsenden[letzte]].t,
        )
    }

    // Erster Tag, ab dem alle Rueckblicke aus MONATE eine Entscheidung haben (Start des laengsten Rueckblicks).
    fun gemeinsamerStart(d: Daten, w: Wahl): Int = ersterTag(d, w.copy(monate = MONATE.max()))

    // Hauptrechnung im selben Zeitraum wie die Zeilen der anderen Rueckblicke, damit die Zeile des gewaehlten Rueckblicks
    // dieselbe Rendite zeigt wie die Kernzahl. Reicht die Geschichte dafuer nicht, gilt der eigene Start des Rueckblicks.
    fun hauptbericht(d: Daten, ab: Int, kosten: StrategieLabor.Kosten, w: Wahl): Bericht? =
        pruefen(d, ab, kosten, w, gemeinsamerStart(d, w)) ?: pruefen(d, ab, kosten, w)

    // Dieselbe Wahl mit jedem Rueckblick aus MONATE im selben Zeitraum (ab dem ersten Tag des laengsten Rueckblicks).
    fun rueckblicke(d: Daten, ab: Int, kosten: StrategieLabor.Kosten, w: Wahl): List<Pair<Int, Bericht>> {
        val gemeinsam = gemeinsamerStart(d, w)
        return MONATE.mapNotNull { m -> pruefen(d, ab, kosten, w.copy(monate = m), gemeinsam)?.let { m to it } }
    }

    // Kaufen und Halten eines Vergleichsindex im selben Zeitraum; null ohne Kurse darin.
    fun index(kerzen: List<Candle>, vonMs: Long, bisMs: Long, kosten: StrategieLabor.Kosten): StrategieLabor.Ergebnis? {
        val (teil, ab) = StrategieLabor.ausschnitt(kerzen, vonMs, bisMs) ?: return null
        return StrategieLabor.pruefen(teil, ab, kosten, listOf(StrategieLabor.Regel(StrategieLabor.Art.HALTEN))).firstOrNull()?.gesamt
    }
}
