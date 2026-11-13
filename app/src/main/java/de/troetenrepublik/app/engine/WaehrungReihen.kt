package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.LangeReihen.Monatsreihe
import de.troetenrepublik.app.engine.LangeReihen.Reihe

// Wechselkurse der langen Reihen: Wert eines US-Dollars in einer anderen Waehrung, monatlich ab 1971 (Monatsmittel des
// H.10 der Federal Reserve). Die Datei traegt die Kurse so, wie die Quelle sie nennt (Yen, Franken, kanadische Dollar und
// D-Mark je US-Dollar, Dollar je Pfund und je Euro); hier werden sie einheitlich als Fremdwaehrung je US-Dollar gelesen.
// Der Euro beginnt 1999, davor gilt die D-Mark zum festen Umrechnungskurs.
object WaehrungReihen {

    // Feste Umrechnung der D-Mark in Euro (1 Euro = 1,95583 Mark)
    const val MARK_JE_EURO = 1.95583

    enum class Waehrung(val kuerzel: String) { EUR("EUR"), GBP("GBP"), JPY("JPY"), CHF("CHF"), CAD("CAD") }

    class Punkt(val monat: Int, val wert: Double)

    // erster und letzter Wert der Reihe, hoechster und tiefster Stand
    class Spanne(val erster: Punkt, val letzter: Punkt, val hoch: Punkt, val tief: Punkt) {
        // Veraenderung des Dollars seit dem ersten Wert in Prozent
        val aenderung: Double get() = (letzter.wert / erster.wert - 1.0) * 100.0
    }

    // Erster Monat, ab dem die D-Mark durch den Euro ersetzt ist
    private val EURO_AB = LangeReihen.monat(1999, 1)

    private fun invers(r: Monatsreihe): Monatsreihe = Monatsreihe(r.ab, r.werte.map { v -> v?.takeIf { it > 0.0 }?.let { 1.0 / it } })

    // Wert eines US-Dollars in der Waehrung je Monat; null, wenn die Datei die Reihe nicht hat.
    fun dollarIn(d: LangeReihen.Datei, w: Waehrung): Monatsreihe? = when (w) {
        Waehrung.EUR -> {
            val mark = d.reihen[Reihe.KURS_DEM]
            val euro = d.reihen[Reihe.KURS_EUR]?.let { invers(it) }
            when {
                mark == null && euro == null -> null
                euro == null -> Monatsreihe(mark!!.ab, mark.werte.map { v -> v?.takeIf { it > 0.0 }?.let { it / MARK_JE_EURO } })
                mark == null -> euro
                else -> {
                    val ab = minOf(mark.ab, euro.ab)
                    val bis = maxOf(mark.bis, euro.bis)
                    Monatsreihe(
                        ab,
                        (ab..bis).map { m ->
                            if (m >= maxOf(EURO_AB, euro.ab)) euro.wert(m)
                            else mark.wert(m)?.takeIf { it > 0.0 }?.let { it / MARK_JE_EURO }
                        },
                    )
                }
            }
        }
        Waehrung.GBP -> d.reihen[Reihe.KURS_GBP]?.let { invers(it) }
        Waehrung.JPY -> d.reihen[Reihe.KURS_JPY]
        Waehrung.CHF -> d.reihen[Reihe.KURS_CHF]
        Waehrung.CAD -> d.reihen[Reihe.KURS_CAD]
    }

    // Waehrungen, die die Datei mit Werten fuehrt
    fun verfuegbar(d: LangeReihen.Datei): List<Waehrung> =
        Waehrung.entries.filter { w -> dollarIn(d, w)?.werte?.any { it != null } == true }

    fun spanne(r: Monatsreihe): Spanne? {
        val punkte = r.werte.mapIndexedNotNull { i, v -> v?.let { Punkt(r.ab + i, it) } }
        if (punkte.size < 2) return null
        return Spanne(punkte.first(), punkte.last(), punkte.maxBy { it.wert }, punkte.minBy { it.wert })
    }

    // Jahr und Monat (1 bis 12) einer Monatszahl
    fun jahr(monat: Int): Int = LangeReihen.jahr(monat)
    fun monatImJahr(monat: Int): Int = monat - LangeReihen.jahr(monat) * 12 + 1
}
