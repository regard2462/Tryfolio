package de.troetenrepublik.app.engine

// [0.78] Phasenzaehler im Ausblick: sieben Groessen, jede zaehlt fuer oder gegen Aktien. Gemessen wird gegen den Wert vor
// einem Jahr:
//   RELATIVZINS    Rendite zehnjaehriger US-Staatsanleihen minus Jahresaenderung der Rohstoffpreise; hoeher als vor einem
//                  Jahr zaehlt positiv
//   ZINSSTRUKTUR   Rendite zehn Jahre minus ein Jahr; ueber null zaehlt positiv
//   RENDITE        Rendite zehnjaehriger US-Staatsanleihen; tiefer als vor einem Jahr zaehlt positiv
//   DOLLAR         Dollar zum Euro; staerker als vor einem Jahr zaehlt positiv
//   ROHSTOFFE      Mittel der Jahresaenderung eines Korbs von Rohstoffen; unter null zaehlt positiv
//   OEL            Jahresaenderung des Oelpreises; unter null zaehlt positiv
//   SAISON         November bis April zaehlt positiv
// Ueber zwei Drittel positiv heisst voll investiert, ab der Haelfte teilweise, ueber zwei Drittel negativ nicht investiert.
// Reihen als (Tag seit 1970, Wert), aufsteigend. Zeigt eine Zaehlung, keine Empfehlung.
object Phasenzaehler {
    enum class Groesse { RELATIVZINS, ZINSSTRUKTUR, RENDITE, DOLLAR, ROHSTOFFE, OEL, SAISON }
    enum class Lage { VOLL, TEILWEISE, ABWARTEND, NICHT }

    // wert und vorjahr je Groesse: Relativzins, Zinsstruktur und Rendite in Prozentpunkten, Dollar, Rohstoffe und Oel als
    // Jahresaenderung in Prozent (vorjahr null), Saison als Monat 1 bis 12. tag: Stand des Werts.
    data class Zeile(val groesse: Groesse, val positiv: Boolean, val wert: Double, val vorjahr: Double?, val tag: Long)

    data class Ergebnis(val lage: Lage, val positiv: Int, val zeilen: List<Zeile>)

    // euroDollar: Dollar je Euro. rohstoffe: je Rohstoff des Korbs eine Preisreihe.
    class Eingaben(
        val rendite10: List<Pair<Long, Double>>, val rendite1: List<Pair<Long, Double>>, val euroDollar: List<Pair<Long, Double>>,
        val rohstoffe: List<List<Pair<Long, Double>>>, val oel: List<Pair<Long, Double>>, val heute: Long,
    )

    const val JAHR = 365L
    // So weit darf ein Messpunkt vom gesuchten Tag entfernt liegen (Feiertage, Wochenenden)
    const val TOLERANZ = 10L
    // Aelter darf der juengste Wert einer Reihe nicht sein
    const val FRISCH = 30L
    // Mindestzahl der Groessen fuer eine Aussage und der Rohstoffe fuer den Korb
    const val MINDEST = 5
    const val MINDEST_KORB = 4

    // Naechster Punkt am oder vor dem Tag, sonst kurz danach; null ausserhalb der Toleranz.
    fun wertAm(reihe: List<Pair<Long, Double>>, tag: Long): Pair<Long, Double>? {
        val davor = reihe.lastOrNull { it.first <= tag }?.takeIf { tag - it.first <= TOLERANZ }
        return davor ?: reihe.firstOrNull { it.first > tag }?.takeIf { it.first - tag <= TOLERANZ }
    }

    // Juengster Punkt bis heute; null, wenn er zu alt ist.
    fun letzter(reihe: List<Pair<Long, Double>>, heute: Long): Pair<Long, Double>? =
        reihe.lastOrNull { it.first <= heute }?.takeIf { heute - it.first <= FRISCH }

    // Aenderung in Prozent gegen den Wert ein Jahr vor dem Tag.
    fun jahresaenderung(reihe: List<Pair<Long, Double>>, tag: Long): Double? {
        val jetzt = wertAm(reihe, tag)?.second ?: return null
        val vor = wertAm(reihe, tag - JAHR)?.second?.takeIf { it > 0 } ?: return null
        return (jetzt / vor - 1) * 100
    }

    // Mittel der Jahresaenderungen des Korbs; null mit zu wenigen Rohstoffen.
    fun korbAenderung(korb: List<List<Pair<Long, Double>>>, tag: Long): Double? {
        val a = korb.mapNotNull { jahresaenderung(it, tag) }
        return if (a.size < MINDEST_KORB) null else a.average()
    }

    fun lage(positiv: Int, gesamt: Int): Lage {
        val negativ = gesamt - positiv
        return when {
            positiv * 3 > gesamt * 2 -> Lage.VOLL
            negativ * 3 > gesamt * 2 -> Lage.NICHT
            positiv * 2 >= gesamt -> Lage.TEILWEISE
            else -> Lage.ABWARTEND
        }
    }

    // Monat (1 bis 12) eines Tags seit 1970.
    fun monat(tag: Long): Int = java.time.LocalDate.ofEpochDay(tag).monthValue

    fun saisonPositiv(monat: Int): Boolean = monat >= 11 || monat <= 4

    // Alle Groessen, fuer die Daten da sind; null mit weniger als MINDEST.
    fun auswerten(e: Eingaben): Ergebnis? {
        val z = ArrayList<Zeile>()
        val r10 = letzter(e.rendite10, e.heute)
        val r10Vor = r10?.let { wertAm(e.rendite10, it.first - JAHR) }
        val korbTag = e.rohstoffe.mapNotNull { letzter(it, e.heute)?.first }.maxOrNull()
        val korb = korbTag?.let { korbAenderung(e.rohstoffe, it) }
        if (r10 != null && r10Vor != null && korbTag != null && korb != null) {
            korbAenderung(e.rohstoffe, korbTag - JAHR)?.let { korbVor ->
                val jetzt = r10.second - korb
                val vor = r10Vor.second - korbVor
                z += Zeile(Groesse.RELATIVZINS, jetzt > vor, jetzt, vor, minOf(r10.first, korbTag))
            }
        }
        if (r10 != null) {
            wertAm(e.rendite1, r10.first)?.let { r1 ->
                val vor = r10Vor?.let { a -> wertAm(e.rendite1, a.first)?.let { a.second - it.second } }
                val jetzt = r10.second - r1.second
                z += Zeile(Groesse.ZINSSTRUKTUR, jetzt > 0, jetzt, vor, r10.first)
            }
            if (r10Vor != null) z += Zeile(Groesse.RENDITE, r10.second < r10Vor.second, r10.second, r10Vor.second, r10.first)
        }
        letzter(e.euroDollar, e.heute)?.let { k ->
            val vor = wertAm(e.euroDollar, k.first - JAHR)?.second ?: return@let
            if (k.second <= 0) return@let
            // Wert des Dollar in Euro gegen das Vorjahr
            val a = (vor / k.second - 1) * 100
            z += Zeile(Groesse.DOLLAR, a > 0, a, null, k.first)
        }
        if (korbTag != null && korb != null) z += Zeile(Groesse.ROHSTOFFE, korb < 0, korb, null, korbTag)
        letzter(e.oel, e.heute)?.let { k ->
            jahresaenderung(e.oel, k.first)?.let { a -> z += Zeile(Groesse.OEL, a < 0, a, null, k.first) }
        }
        val m = monat(e.heute)
        z += Zeile(Groesse.SAISON, saisonPositiv(m), m.toDouble(), null, e.heute)
        if (z.size < MINDEST) return null
        val positiv = z.count { it.positiv }
        return Ergebnis(lage(positiv, z.size), positiv, z)
    }
}
