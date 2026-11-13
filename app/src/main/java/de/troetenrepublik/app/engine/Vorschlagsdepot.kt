package de.troetenrepublik.app.engine

import kotlin.math.max

// Vorschlagsdepot aus Einzelaktien (0.77): feste, nachvollziehbare Auswahl aus den Werten des gewaehlten Markts.
// Zugelassen sind Werte mit hoher Gesamtpunktzahl, nicht negativer Waage (ohne Waage: kein Bereich klar im Minus) und
// mindestens schmalem Marktmacht. Zur Streuung hoechstens zwei Werte je Branche und Obergrenzen je Sektor und Land;
// gewichtet wird nach Punktzahl mit Deckel je Wert. Reine Rechnung.
object Vorschlagsdepot {

    // Mindestpunktzahl, Zahl der Werte, Grenzen der Streuung
    const val MIN_PUNKTE = 60.0
    const val MAX_WERTE = 20
    const val JE_BRANCHE = 2
    const val JE_SEKTOR = 5
    const val JE_LAND = 8

    // Deckel je Wert; bei weniger als dreizehn Werten reicht er nicht fuer 100 %, dann gilt der gleiche Anteil.
    const val DECKEL = 0.08

    // Ohne Waage darf kein Bereich mit Daten unter dieser Punktzahl liegen.
    const val MINDEST_BEREICH = 35.0

    private val PRUEF_BEREICHE = listOf(Aktienfinder.Bereich.BEWERTUNG, Aktienfinder.Bereich.QUALITAET, Aktienfinder.Bereich.ANALYSTEN, Aktienfinder.Bereich.TREND)

    enum class Sektor { TECHNOLOGIE, GESUNDHEIT, FINANZEN, INDUSTRIE, KONSUM_ZYKLISCH, KONSUM_BASIS, ENERGIE, ROHSTOFFE, VERSORGER, IMMOBILIEN, KOMMUNIKATION }

    // Sektor der Katalogbranchen; null fuer unbekannte Schluessel.
    fun sektor(branche: String): Sektor? = when (branche) {
        "SOFTWARE", "HALBLEITER", "COMPUTER", "ELEKTRO" -> Sektor.TECHNOLOGIE
        "GESUNDHEIT", "PHARMA", "BIOTECH" -> Sektor.GESUNDHEIT
        "BANKEN", "VERSICHERUNG", "HOLDINGS" -> Sektor.FINANZEN
        "MASCHINEN", "BAU", "LUFTFAHRT", "VERKEHR", "DIENSTLEISTUNGEN" -> Sektor.INDUSTRIE
        "FAHRZEUGE", "EINZELHANDEL", "TEXTIL", "GASTGEWERBE", "KONSUM" -> Sektor.KONSUM_ZYKLISCH
        "NAHRUNG", "ALKOHOL_TABAK", "HANDEL" -> Sektor.KONSUM_BASIS
        "OEL_GAS" -> Sektor.ENERGIE
        "BERGBAU", "CHEMIE", "FORST" -> Sektor.ROHSTOFFE
        "VERSORGER", "UMWELT" -> Sektor.VERSORGER
        "IMMOBILIEN" -> Sektor.IMMOBILIEN
        "TELEKOM", "MEDIEN" -> Sektor.KOMMUNIKATION
        else -> null
    }

    class Eintrag(val ergebnis: Aktienfinder.Ergebnis, val sektor: Sektor, val gewicht: Double, val waage: Int?)

    class Depot(val eintraege: List<Eintrag>, val zugelassen: Int) {
        val sektoren: List<Pair<Sektor, Double>> = eintraege.groupBy { it.sektor }.map { (s, l) -> s to l.sumOf { it.gewicht } }.sortedByDescending { it.second }
        val laender: List<Pair<String, Double>> = eintraege.groupBy { it.ergebnis.daten.land }.map { (c, l) -> c to l.sumOf { it.gewicht } }.sortedByDescending { it.second }
        val branchen: Int = eintraege.map { it.ergebnis.daten.branche }.distinct().size
    }

    // Waage des Werts, wo die Daten sie tragen.
    private fun waage(d: Aktienfinder.Daten, jetzt: Long): Int? = FinderVorlagen.wert(d, FinderVorlagen.Feld.WAAGE, jetzt)?.let { Math.round(it).toInt() }

    // Ob der Wert die festen Bedingungen erfuellt (ohne Streuung).
    fun geeignet(e: Aktienfinder.Ergebnis, jetzt: Long): Boolean {
        val d = e.daten
        if (e.gesamt < MIN_PUNKTE || sektor(d.branche) == null) return false
        val stufe = d.marktmacht?.stufe
        if (stufe != Marktmacht.Stufe.HOCH && stufe != Marktmacht.Stufe.MITTEL) return false
        val w = waage(d, jetzt)
        return if (w != null) w >= 0 else PRUEF_BEREICHE.all { b -> (e.bereiche[b] ?: 100.0) >= MINDEST_BEREICH }
    }

    // Anteile nach Punktzahl, jeder hoechstens deckel (mindestens der gleiche Anteil); Summe 1.
    fun gewichte(punkte: List<Double>, deckel: Double = DECKEL): List<Double> {
        val n = punkte.size
        if (n == 0) return emptyList()
        val grenze = max(deckel, 1.0 / n)
        val fest = BooleanArray(n)
        val w = DoubleArray(n)
        while (true) {
            val frei = (0 until n).filter { !fest[it] }
            if (frei.isEmpty()) break
            val rest = 1.0 - (0 until n).filter { fest[it] }.sumOf { w[it] }
            val summe = frei.sumOf { punkte[it].coerceAtLeast(1.0) }
            var neu = false
            for (i in frei) {
                w[i] = rest * punkte[i].coerceAtLeast(1.0) / summe
                if (w[i] > grenze + 1e-12) { fest[i] = true; w[i] = grenze; neu = true }
            }
            if (!neu) break
        }
        return w.toList()
    }

    // Stellt das Depot zusammen: die Besten der zugelassenen Werte, solange Branche, Sektor und Land noch Platz haben.
    // Die Obergrenze je Land gilt nur, wenn die Zugelassenen aus mindestens drei Laendern kommen (ein Markt aus einem
    // Land haette sonst nie genug Werte).
    fun zusammenstellen(liste: List<Aktienfinder.Ergebnis>, jetzt: Long = System.currentTimeMillis()): Depot {
        val zugelassen = liste.filter { geeignet(it, jetzt) }
            .sortedWith(compareByDescending<Aktienfinder.Ergebnis> { it.gesamt }.thenBy { it.symbol })
        val mitLandgrenze = zugelassen.map { it.daten.land }.distinct().size >= 3
        val je = ArrayList<Aktienfinder.Ergebnis>()
        val branche = HashMap<String, Int>()
        val sektor = HashMap<Sektor, Int>()
        val land = HashMap<String, Int>()
        for (e in zugelassen) {
            if (je.size >= MAX_WERTE) break
            val d = e.daten
            val s = sektor(d.branche) ?: continue
            if ((branche[d.branche] ?: 0) >= JE_BRANCHE || (sektor[s] ?: 0) >= JE_SEKTOR) continue
            if (mitLandgrenze && (land[d.land] ?: 0) >= JE_LAND) continue
            je += e
            branche.merge(d.branche, 1, Int::plus)
            sektor.merge(s, 1, Int::plus)
            land.merge(d.land, 1, Int::plus)
        }
        val w = gewichte(je.map { it.gesamt })
        return Depot(je.mapIndexed { i, e -> Eintrag(e, sektor(e.daten.branche)!!, w[i], waage(e.daten, jetzt)) }, zugelassen.size)
    }
}
