package de.troetenrepublik.app.engine

// [Globus Gesten 0.70] Weltwirtschaft aus den IWF-Reihen: Wachstum je Jahr, Verschiebung der Anteile am Welt-BIP
// (Kaufkraft) und Aufsteiger. Rechnet nur mit dem Datensatz, ohne Oberflaeche.
object WeltAusblick {
    // Wirtschaftsraeume der Anteilsgrafik; EURO fasst die Mitglieder des Euroraums zusammen.
    const val EURO = "EURO"
    val RAEUME = listOf("CHN", "USA", "IND", EURO, "JPN")
    private val EURO_LAENDER = listOf(
        "AUT", "BEL", "BGR", "HRV", "CYP", "EST", "FIN", "FRA", "DEU", "GRC", "IRL", "ITA", "LVA", "LTU", "LUX", "MLT", "NLD", "PRT", "SVK", "SVN", "ESP",
    )

    // Anzahl Jahre vor dem laufenden, die der Balkenverlauf zeigt
    const val JAHRE_ZURUECK = 5

    // Mindestgroesse (Milliarden Dollar im laufenden Jahr) fuer die Liste der schnellsten Wachser
    const val MIN_BIP = 100.0

    data class Balken(val jahr: Int, val wert: Double, val prognose: Boolean)

    // Weltwachstum von jahr - JAHRE_ZURUECK bis zum letzten Jahr der Prognose. Ab dem laufenden Jahr gilt ein Wert als Prognose.
    fun balken(d: Imf.Datensatz, jahr: Int): List<Balken> {
        val reihe = d.werte[Imf.WACHSTUM]?.get(Imf.WELT).orEmpty()
        return reihe.keys.filter { it >= jahr - JAHRE_ZURUECK }.sorted().map { Balken(it, reihe.getValue(it), it >= jahr) }
    }

    fun letztesJahr(d: Imf.Datensatz): Int? = d.werte[Imf.WACHSTUM]?.get(Imf.WELT)?.keys?.maxOrNull()

    // Durchschnitt pro Jahr und Gesamtzuwachs in Prozent ueber die Jahre nach `von` bis `bis` (Zinseszins)
    data class Zuwachs(val von: Int, val bis: Int, val proJahr: Double, val gesamt: Double)

    fun zuwachs(d: Imf.Datensatz, von: Int, bis: Int): Zuwachs? {
        val reihe = d.werte[Imf.WACHSTUM]?.get(Imf.WELT) ?: return null
        if (bis <= von) return null
        var faktor = 1.0
        for (j in von + 1..bis) faktor *= 1.0 + (reihe[j] ?: return null) / 100.0
        return Zuwachs(von, bis, (Math.pow(faktor, 1.0 / (bis - von)) - 1.0) * 100.0, (faktor - 1.0) * 100.0)
    }

    // Anteil am Welt-BIP (Kaufkraft) in Prozent; fuer EURO die Summe der Mitglieder. null ohne Zahlen.
    fun anteil(d: Imf.Datensatz, raum: String, jahr: Int): Double? {
        val reihe = d.werte[Imf.WELT_ANTEIL] ?: return null
        val codes = if (raum == EURO) EURO_LAENDER else listOf(raum)
        val werte = codes.mapNotNull { reihe[it]?.get(jahr) }
        return if (werte.isEmpty()) null else werte.sum()
    }

    data class Verschiebung(val raum: String, val anteile: List<Pair<Int, Double>>) {
        val aenderung: Double get() = anteile.last().second - anteile.first().second
    }

    // Anteile der Raeume in den gegebenen Jahren; Raeume ohne Zahlen fallen weg.
    fun verschiebung(d: Imf.Datensatz, jahre: List<Int>): List<Verschiebung> = RAEUME.mapNotNull { r ->
        val a = jahre.mapNotNull { j -> anteil(d, r, j)?.let { j to it } }
        if (a.size == jahre.size) Verschiebung(r, a) else null
    }

    data class Schnitt(val code: String, val wert: Double)

    // Grosse Laender mit dem hoechsten durchschnittlichen Wachstum von jahr + 1 bis bis.
    fun schnellste(d: Imf.Datensatz, jahr: Int, bis: Int, n: Int = 3): List<Schnitt> {
        if (bis <= jahr) return emptyList()
        return Imf.laenderCodes(d).mapNotNull { c ->
            if ((Imf.wert(d, Imf.BIP, c, jahr) ?: 0.0) < MIN_BIP) return@mapNotNull null
            val w = (jahr + 1..bis).map { Imf.wert(d, Imf.WACHSTUM, c, it) ?: return@mapNotNull null }
            Schnitt(c, w.average())
        }.sortedByDescending { it.wert }.take(n)
    }

    data class Platz(val code: String, val von: Int, val bis: Int)

    // Laender, die in der Rangliste nach Wirtschaftsleistung (Dollar) bis zum Jahr `bis` am meisten Plaetze gewinnen.
    fun platzgewinner(d: Imf.Datensatz, jahr: Int, bis: Int, oben: Int = 30, n: Int = 3): List<Platz> {
        fun rang(j: Int): Map<String, Int> =
            Imf.laenderCodes(d).mapNotNull { c -> Imf.wert(d, Imf.BIP, c, j)?.let { c to it } }
                .sortedByDescending { it.second }.mapIndexed { i, p -> p.first to i + 1 }.toMap()
        if (bis <= jahr) return emptyList()
        val a = rang(jahr); val b = rang(bis)
        return a.mapNotNull { (c, r0) -> b[c]?.let { r1 -> if (r1 < r0 && r1 <= oben) Platz(c, r0, r1) else null } }
            .sortedWith(compareByDescending<Platz> { it.von - it.bis }.thenBy { it.bis }).take(n)
    }

    // Beitrag eines Raums zum Weltwachstum: Wachstum mal Anteil an der Weltwirtschaftsleistung, in Prozentpunkten pro Jahr.
    // schnitt: Wachstum des Raums im Mittel der Prognosejahre, frueher: im Mittel der fuenf Jahre davor.
    data class Beitrag(val raum: String, val punkte: Double, val schnitt: Double, val frueher: Double?) {
        // Unterschied des Wachstums gegenueber den letzten fuenf Jahren in Prozentpunkten
        val aenderung: Double? get() = frueher?.let { schnitt - it }
    }

    // Wachstum eines Raums im Jahr j; fuer EURO der nach Anteil gewichtete Mittelwert der Mitglieder mit Zahlen.
    fun wachstum(d: Imf.Datensatz, raum: String, j: Int): Double? {
        if (raum != EURO) return Imf.wert(d, Imf.WACHSTUM, raum, j)
        var summe = 0.0; var gewicht = 0.0
        for (c in EURO_LAENDER) {
            val w = Imf.wert(d, Imf.WACHSTUM, c, j) ?: continue
            val a = Imf.wert(d, Imf.WELT_ANTEIL, c, j) ?: continue
            summe += w * a; gewicht += a
        }
        return if (gewicht > 0.0) summe / gewicht else null
    }

    private fun mittel(werte: List<Double?>): Double? = werte.takeIf { it.isNotEmpty() && it.all { w -> w != null } }?.map { it!! }?.average()

    // Beitraege der groessten Raeume zum Weltwachstum der Jahre jahr + 1 bis bis (Anteil im Jahr `jahr`). Der Rest zum
    // Weltwachstum steht als Beitrag der Uebrigen am Ende. null, wenn Weltwachstum oder Anteile fehlen.
    fun beitraege(d: Imf.Datensatz, jahr: Int, bis: Int): Pair<List<Beitrag>, Double>? {
        if (bis <= jahr) return null
        val welt = zuwachs(d, jahr, bis)?.proJahr ?: return null
        val liste = RAEUME.mapNotNull { r ->
            val a = anteil(d, r, jahr) ?: return@mapNotNull null
            val nun = mittel((jahr + 1..bis).map { wachstum(d, r, it) }) ?: return@mapNotNull null
            val frueher = mittel((jahr - JAHRE_ZURUECK until jahr).map { wachstum(d, r, it) })
            Beitrag(r, nun * a / 100.0, nun, frueher)
        }.sortedByDescending { it.punkte }
        if (liste.isEmpty()) return null
        return liste to (welt - liste.sumOf { it.punkte })
    }

    // Der feste Begruendungstext des IWF gilt nur fuer seinen Ausblick: nach sieben Monaten oder mit einem spaeteren
    // Prognosehorizont im Datensatz (neuer Ausblick) entfaellt er. Der Oktober-Ausblick 2026 reicht bis 2031.
    const val AUSBLICK_JAHR = 2026
    const val AUSBLICK_MONAT = 10
    const val AUSBLICK_HORIZONT = 2031
    const val AUSBLICK_MONATE = 7

    fun begruendungGilt(heute: java.time.LocalDate, letztesJahr: Int?): Boolean {
        if (letztesJahr != null && letztesJahr > AUSBLICK_HORIZONT) return false
        val ende = java.time.LocalDate.of(AUSBLICK_JAHR, AUSBLICK_MONAT, 1).plusMonths(AUSBLICK_MONATE.toLong())
        return heute.isBefore(ende)
    }
}
