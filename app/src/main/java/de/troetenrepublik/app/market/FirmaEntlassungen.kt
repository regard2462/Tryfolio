package de.troetenrepublik.app.market

import java.time.LocalDate

// Entlassungen und Schliessungen einer Firma nach dem WARN Act (Meldungen an die Arbeitsbehoerden der US-Bundesstaaten),
// Rechenteil: zwoelf Monate mit Zahl der Meldungen und Stellen, Verlauf je Quartal, Liste mit Link zur Seite des Staates.
// Die Meldungen kommen aus behoerden/entlassungen.json (WARN Feed, CC BY 4.0, alle Staaten mit freier Liste, auch Texas).
object FirmaEntlassungen {

    class Quartal(val jahr: Int, val q: Int, val meldungen: Int, val stellen: Int)

    class Auswertung(
        // Meldungen der letzten zwoelf Monate, neueste zuerst
        val liste: List<Behoerden.Entlassung>,
        val stellen: Int,
        val schliessungen: Int,
        // Die letzten acht Quartale bis zum laufenden, aelteste zuerst
        val quartale: List<Quartal>,
    ) {
        val meldungen: Int get() = liste.size
    }

    const val QUARTALE = 8

    private fun quartal(d: LocalDate) = (d.monthValue - 1) / 3 + 1

    // Meldungen der Firma, ohne Doppelte (Datum, Staat, Zahl), nur bis heute. Ohne Meldung in den letzten zwoelf Monaten null:
    // dann entfaellt der Abschnitt.
    fun auswerten(alle: List<Behoerden.Entlassung>, heute: LocalDate): Auswertung? {
        val eindeutig = alle.distinctBy { it.d + "|" + it.s + "|" + it.n }.filter { it.d.isNotBlank() && it.d <= heute.toString() }
        val ab = heute.minusYears(1).toString()
        val liste = eindeutig.filter { it.d >= ab }.sortedByDescending { it.d }
        if (liste.isEmpty()) return null
        var j = heute.year
        var q = quartal(heute)
        val folge = ArrayList<Pair<Int, Int>>()
        repeat(QUARTALE) {
            folge.add(j to q)
            if (--q == 0) { q = 4; j-- }
        }
        val je = eindeutig.groupBy { e -> runCatching { LocalDate.parse(e.d.take(10)) }.getOrNull()?.let { it.year to quartal(it) } }
        val quartale = folge.reversed().map { (jahr, qq) ->
            val l = je[jahr to qq].orEmpty()
            Quartal(jahr, qq, l.size, l.sumOf { it.n })
        }
        return Auswertung(liste, liste.sumOf { it.n }, liste.count { it.t == "S" }, quartale)
    }
}
