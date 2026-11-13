package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Regel „Zielvolatilitaet“ des Strategietests (0.78): immer investiert, aber mit wechselnder Groesse. Der Anteil am
// Depotwert ist Zielschwankung durch gemessene Schwankung der letzten n Handelstage (aufs Jahr gerechnet), hoechstens
// der Deckel. Bei ruhigem Markt ist die Position gross, bei unruhigem klein. Die Idee folgt Moreira und Muir (2017,
// „Volatility-Managed Portfolios“); dort wird bei ruhigem Markt gehebelt, hier ist bei 100 % Schluss.
// Wie bei allen Regeln entsteht die Vorgabe am Tagesschluss, gehandelt wird zur Eroeffnung des naechsten Handelstags,
// mit Spread je Seite und Ordergebuehr. Angepasst wird erst, wenn der Anteil um mehr als BAND von der Vorgabe abweicht;
// sonst fraessen taegliche Kleinstorders das Ergebnis auf.
// Parameter der Regel (StrategieLabor.Regel): a = Zielschwankung in Prozent pro Jahr, b = Deckel in Prozent,
// n = Handelstage der Messung.
object Zielvolatilitaet {

    const val HANDELSTAGE = 252

    // Abstand zwischen Anteil und Vorgabe, ab dem gehandelt wird (0,1 = 10 Prozentpunkte)
    const val BAND = 0.10

    // Schwankung der letzten n Tagesrenditen bis zum Schluss jedes Tages, aufs Jahr gerechnet (0,2 = 20 %); null, solange
    // weniger als n Renditen vorliegen.
    fun schwankung(schluss: List<Double>, n: Int): List<Double?> {
        val aus = ArrayList<Double?>(schluss.size)
        if (n < 2) return schluss.map { null }
        val r = DoubleArray(schluss.size)
        for (i in 1 until schluss.size) r[i] = if (schluss[i - 1] > 0) schluss[i] / schluss[i - 1] - 1.0 else 0.0
        for (i in schluss.indices) {
            if (i < n) { aus += null; continue }
            var summe = 0.0
            for (k in i - n + 1..i) summe += r[k]
            val mittel = summe / n
            var quadrate = 0.0
            for (k in i - n + 1..i) quadrate += (r[k] - mittel) * (r[k] - mittel)
            aus += sqrt(quadrate / (n - 1)) * sqrt(HANDELSTAGE.toDouble())
        }
        return aus
    }

    // Vorgabe fuer den Anteil am Depotwert (0 bis deckel) aus Zielschwankung und gemessener Schwankung; ohne Messung 0,
    // ohne Schwankung der Deckel.
    fun anteil(ziel: Double, gemessen: Double?, deckel: Double): Double = when {
        gemessen == null || !gemessen.isFinite() || ziel <= 0 || deckel <= 0 -> 0.0
        gemessen <= 0 -> deckel
        else -> (ziel / gemessen).coerceIn(0.0, deckel)
    }

    // Vorgabe nach dem Schluss jedes Tages
    fun anteile(schluss: List<Double>, r: StrategieLabor.Regel): DoubleArray {
        val s = schwankung(schluss, r.n)
        return DoubleArray(schluss.size) { anteil(r.a / 100.0, s[it], r.b / 100.0) }
    }

    // Spielt die Regel im Bereich von (einschliesslich) bis bis (ausschliesslich) mit frischem Startkapital durch. Am
    // Tag i gilt die Vorgabe vom Schluss des Vortags. trades zaehlt die Orders (Kaeufe und Verkaeufe), imMarkt ist der
    // mittlere Anteil am Depotwert zum Tagesschluss. Treffer und Geschaefte gibt es nicht: die Position wird nie ganz
    // geschlossen. Mit leicht=true bleiben verlauf und renditen leer.
    fun simulieren(
        d: StrategieLabor.Daten, r: StrategieLabor.Regel, von: Int, bis: Int, kosten: StrategieLabor.Kosten,
        start: Double = StrategieLabor.START, leicht: Boolean = false, band: Double = BAND,
    ): StrategieLabor.Ergebnis? {
        if (von < 0 || bis > d.schluss.size || bis - von < 2) return null
        val vorgabe = anteile(d.schluss, r)
        var bar = start
        var stueck = 0.0
        var orders = 0
        var summeAnteil = 0.0
        val werte = if (leicht) null else DoubleArray(bis - von)
        val renditen = if (leicht) null else ArrayList<Double>(bis - von)
        var wert = start
        var hoechst = start
        var rueckgang = 0.0
        var vorher = start
        var anzahl = 0
        var mittel = 0.0
        var summeQuadrate = 0.0
        for (i in von until bis) {
            val soll = if (i == 0) vorgabe[0] else vorgabe[i - 1]
            val p = d.eroeffnung(i)
            val gesamt = bar + stueck * p
            if (p > 0 && gesamt > 0) {
                val ist = stueck * p / gesamt
                // Erster Kauf und voller Ausstieg ohne Band, sonst erst ab dem Abstand
                val handeln = abs(soll - ist) > band || (stueck == 0.0 && soll > 0) || (soll == 0.0 && stueck > 0)
                if (handeln) {
                    val betrag = soll * gesamt - stueck * p
                    val brief = Pricing.ask(p, kosten.spreadBps)
                    if (betrag > kosten.gebuehr && brief > 0) {
                        val geld = minOf(betrag, bar)
                        if (geld > kosten.gebuehr) {
                            stueck += (geld - kosten.gebuehr) / brief
                            bar -= geld
                            orders++
                        }
                    } else if (betrag < 0 && stueck > 0) {
                        val ab = minOf(stueck, -betrag / p)
                        bar += ab * Pricing.bid(p, kosten.spreadBps) - kosten.gebuehr
                        stueck -= ab
                        if (stueck < 1e-12) stueck = 0.0
                        orders++
                    }
                }
            }
            wert = bar + stueck * d.schluss[i]
            if (wert > 0) summeAnteil += stueck * d.schluss[i] / wert
            werte?.set(i - von, wert)
            hoechst = max(hoechst, wert)
            if (hoechst > 0) rueckgang = max(rueckgang, 1.0 - wert / hoechst)
            val x = if (vorher > 0) wert / vorher - 1.0 else 0.0
            renditen?.add(x)
            vorher = wert
            anzahl++
            val abstand = x - mittel
            mittel += abstand / anzahl
            summeQuadrate += abstand * (x - mittel)
        }
        val jahre = (d.kerzen[bis - 1].t - d.kerzen[von].t) / (365.25 * 86_400_000.0)
        val renditeJahr = if (jahre < 0.25) null else if (wert <= 0) -1.0 else (wert / start).pow(1.0 / jahre) - 1.0
        val abweichung = if (anzahl > 1) sqrt(summeQuadrate / (anzahl - 1)) else 0.0
        return StrategieLabor.Ergebnis(
            endwert = wert,
            renditeJahr = renditeJahr,
            maxRueckgang = rueckgang,
            trades = orders,
            treffer = null,
            imMarkt = summeAnteil / (bis - von),
            verlauf = if (werte == null) emptyList() else (von until bis).map { d.kerzen[it].t to werte[it - von] },
            renditen = renditen ?: emptyList(),
            sharpe = if (anzahl < 2 || abweichung < 1e-12) 0.0 else mittel / abweichung,
            jahre = jahre,
        )
    }
}
