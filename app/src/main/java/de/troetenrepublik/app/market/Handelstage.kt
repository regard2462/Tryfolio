package de.troetenrepublik.app.market

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

// Kalender der gemeldeten Geschaefte einer Person, je Tag des Geschaefts, nicht der Meldung.
// Betraege sind wie ueberall in Copytrading die Mitte der gemeldeten Spanne.
object Handelstage {

    data class Tag(val datum: LocalDate, val geschaefte: List<Kongress.Geschaeft>) {
        val kaeufe: Int = geschaefte.count { it.kauf }
        val verkaeufe: Int = geschaefte.count { it.verkauf }
        val kaufSumme: Double = geschaefte.filter { it.kauf }.sumOf { it.mitte }
        val verkaufSumme: Double = geschaefte.filter { it.verkauf }.sumOf { it.mitte }
        val netto: Double get() = kaufSumme - verkaufSumme
        val volumen: Double get() = kaufSumme + verkaufSumme
    }

    data class Monat(val geschaefte: Int, val tage: Int, val kaeufe: Int, val verkaeufe: Int, val kaufSumme: Double, val verkaufSumme: Double)

    // Die Daten der Meldungen stehen auf Mitternacht UTC.
    fun datum(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

    // Innerhalb eines Tags Kaeufe vor Verkaeufen, jeweils die groessten zuerst.
    fun tage(geschaefte: List<Kongress.Geschaeft>): Map<LocalDate, Tag> =
        geschaefte.filter { it.datum > 0L }.groupBy { datum(it.datum) }.mapValues { (d, l) ->
            Tag(d, l.sortedWith(compareBy<Kongress.Geschaeft>({ if (it.kauf) 0 else if (it.verkauf) 1 else 2 }, { -it.mitte })))
        }

    fun monat(tage: Map<LocalDate, Tag>, ym: YearMonth): Monat {
        val drin = tage.values.filter { YearMonth.from(it.datum) == ym }
        return Monat(
            geschaefte = drin.sumOf { it.geschaefte.size },
            tage = drin.size,
            kaeufe = drin.sumOf { it.kaeufe },
            verkaeufe = drin.sumOf { it.verkaeufe },
            kaufSumme = drin.sumOf { it.kaufSumme },
            verkaufSumme = drin.sumOf { it.verkaufSumme },
        )
    }

    // Vom ersten bis zum letzten Monat mit Geschaeften, ohne Geschaefte nur heute.
    fun spanne(tage: Map<LocalDate, Tag>, heute: YearMonth): ClosedRange<YearMonth> {
        val erster = tage.keys.minOrNull()?.let { YearMonth.from(it) } ?: return heute..heute
        val letzter = tage.keys.maxOrNull()?.let { YearMonth.from(it) } ?: heute
        return erster..letzter
    }
}
