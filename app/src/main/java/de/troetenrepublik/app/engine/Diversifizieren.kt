package de.troetenrepublik.app.engine

import kotlin.math.abs

// Zielgewichte fuers Umschichten nach einem Streuungsziel. Jede Funktion liefert den Anteil je Positionsschluessel
// (Summe 1) und nimmt nur Positionen mit Wert > 0 auf.
object Diversifizieren {

    // Eine Position: Schluessel, heutiger Wert, Laenderverteilung (alpha-2 zu Anteil, Summe hoechstens 1; leer, wenn
    // unbekannt) und Branche (null, wenn unbekannt).
    class Wert(val key: String, val wert: Double, val land: Map<String, Double> = emptyMap(), val branche: String? = null)

    private fun gueltig(werte: List<Wert>) = werte.filter { it.wert > 0 && it.wert.isFinite() }

    private fun heute(werte: List<Wert>): Map<String, Double> {
        val summe = werte.sumOf { it.wert }
        return werte.associate { it.key to it.wert / summe }
    }

    // Jede Position gleich viel.
    fun gleich(werte: List<Wert>): Map<String, Double> {
        val w = gueltig(werte)
        if (w.isEmpty()) return emptyMap()
        return w.associate { it.key to 1.0 / w.size }
    }

    // Jede vorhandene Branche gleich viel, innerhalb der Branche im heutigen Verhaeltnis. Positionen ohne Branche
    // bilden zusammen eine eigene Gruppe.
    fun branchen(werte: List<Wert>): Map<String, Double> {
        val w = gueltig(werte)
        if (w.isEmpty()) return emptyMap()
        val gruppen = w.groupBy { it.branche ?: "" }
        val aus = HashMap<String, Double>()
        for ((_, g) in gruppen) {
            val summe = g.sumOf { it.wert }
            for (p in g) aus[p.key] = p.wert / summe / gruppen.size
        }
        return aus
    }

    // Hoechstens grenze (Anteil, 0,10 = 10 %) je Position: was darueber liegt, wird gekappt und der Rest im heutigen
    // Verhaeltnis auf die uebrigen verteilt, bis keiner mehr darueber liegt. Reicht die Grenze fuer 100 % nicht aus
    // (Positionen mal Grenze unter 1), bleibt nur die Gleichverteilung.
    fun hoechstens(werte: List<Wert>, grenze: Double): Map<String, Double> {
        val w = gueltig(werte)
        if (w.isEmpty()) return emptyMap()
        if (!(grenze > 0) || grenze * w.size < 1.0 - 1e-9) return gleich(w)
        val a = DoubleArray(w.size) { w[it].wert }.let { v -> val s = v.sum(); DoubleArray(v.size) { v[it] / s } }
        val gekappt = BooleanArray(w.size)
        repeat(w.size + 1) {
            var ueber = 0.0
            var neu = false
            for (i in a.indices) if (!gekappt[i] && a[i] > grenze + 1e-12) { ueber += a[i] - grenze; a[i] = grenze; gekappt[i] = true; neu = true }
            if (!neu) return@repeat
            val frei = a.indices.filter { !gekappt[it] }
            if (frei.isEmpty()) return@repeat
            val summe = frei.sumOf { a[it] }
            for (i in frei) a[i] += if (summe > 0) ueber * a[i] / summe else ueber / frei.size
        }
        return w.indices.associate { w[it].key to a[it] }
    }

    // Laender nach Anteil am Welt-BIP. bip: Anteil je Land (alpha-2). Nur Laender, in denen das Depot eine Position hat,
    // zaehlen; ihre BIP-Anteile werden auf 100 % gebracht. Positionen mit mehreren Laendern (Fonds) werden iterativ
    // so gewichtet, dass die Laenderanteile des Depots den Zielanteilen nahekommen. Positionen ohne Land oder nur in
    // Laendern ohne BIP-Wert behalten ihren heutigen Anteil. Null, wenn keine Position zuzuordnen ist.
    fun laenderBip(werte: List<Wert>, bip: Map<String, Double>): Map<String, Double>? {
        val w = gueltig(werte)
        if (w.isEmpty()) return null
        val heute = heute(w)
        // Laenderanteile je Position, auf Laender mit BIP-Wert beschraenkt und auf 1 normiert
        val verteilung = w.map { p ->
            val m = p.land.filter { (l, a) -> a > 0 && (bip[l] ?: 0.0) > 0 }
            val s = m.values.sum()
            if (s > 0) m.mapValues { it.value / s } else emptyMap()
        }
        val fest = w.indices.filter { verteilung[it].isEmpty() }
        val frei = w.indices.filter { verteilung[it].isNotEmpty() }
        if (frei.isEmpty()) return null
        val laender = frei.flatMap { verteilung[it].keys }.toSet()
        val bipSumme = laender.sumOf { bip.getValue(it) }
        val ziel = laender.associateWith { bip.getValue(it) / bipSumme }
        val festAnteil = fest.sumOf { heute.getValue(w[it].key) }
        val frei0 = frei.sumOf { w[it].wert }
        var g = DoubleArray(frei.size) { w[frei[it]].wert / frei0 }
        repeat(500) {
            val land = HashMap<String, Double>()
            for (j in frei.indices) for ((l, a) in verteilung[frei[j]]) land[l] = (land[l] ?: 0.0) + g[j] * a
            val faktor = laender.associateWith { l -> land[l]?.takeIf { it > 1e-12 }?.let { ziel.getValue(l) / it } ?: 1.0 }
            val neu = DoubleArray(g.size) { j -> g[j] * verteilung[frei[j]].entries.sumOf { (l, a) -> a * faktor.getValue(l) } }
            val s = neu.sum()
            if (!(s > 0)) return@repeat
            for (j in neu.indices) neu[j] /= s
            g = neu
        }
        val aus = HashMap<String, Double>()
        for (i in fest) aus[w[i].key] = heute.getValue(w[i].key)
        for (j in frei.indices) aus[w[frei[j]].key] = g[j] * (1.0 - festAnteil)
        return aus
    }

    // Wie weit die Laenderanteile der Gewichte von den Zielanteilen entfernt sind (halbe Summe der Betraege, 0 bis 1);
    // fuer Tests und Kontrolle.
    fun laenderAbstand(werte: List<Wert>, gewichte: Map<String, Double>, bip: Map<String, Double>): Double {
        val land = HashMap<String, Double>()
        for (p in werte) {
            val g = gewichte[p.key] ?: continue
            for ((l, a) in p.land) if ((bip[l] ?: 0.0) > 0) land[l] = (land[l] ?: 0.0) + g * a
        }
        val s = land.values.sum()
        if (!(s > 0)) return 1.0
        val bs = land.keys.sumOf { bip.getValue(it) }
        return land.keys.sumOf { abs(land.getValue(it) / s - bip.getValue(it) / bs) } / 2.0
    }
}
