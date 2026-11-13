package de.troetenrepublik.app.market

import java.time.DayOfWeek
import java.time.LocalDate

// [Globus Boersen 0.73] Stand des Leitindex je Land mit Herkunft, Datum des Kurses und Zeitpunkt des Abrufs.
// Je Land und Quelle steht ein Wert; gefaerbt wird nur, was nicht aelter als vier Handelstage ist.
object IndexStand {
    // quelle: "yahoo", "yahoo-etf", "direkt:<id>" oder "selbst:<id>"
    data class Wert(
        val land: String, val quelle: String, val prozent: Double, val wert: Double?, val kursDatum: LocalDate, val abruf: Long,
    ) {
        val rang: Int get() = when {
            quelle == "yahoo" -> 0
            quelle.startsWith("direkt:") -> 1
            quelle.startsWith("selbst:") -> 2
            else -> 3 // Laender-ETF: ein echter Index geht vor
        }
    }

    const val MAX_HANDELSTAGE = 4

    // Werktage (Montag bis Freitag) nach dem Kursdatum bis einschliesslich heute; Feiertage zaehlen mit
    fun handelstageSeit(kurs: LocalDate, heute: LocalDate): Int {
        var n = 0
        var d = kurs.plusDays(1)
        while (!d.isAfter(heute)) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) n++
            d = d.plusDays(1)
        }
        return n
    }

    fun frisch(w: Wert, heute: LocalDate): Boolean = handelstageSeit(w.kursDatum, heute) <= MAX_HANDELSTAGE

    // Aus den Werten eines Landes der gueltige: der frische mit der besten Herkunft (Yahoo vor Direktabruf vor Selbstabruf vor Laender-ETF),
    // bei gleicher Herkunft der juengere; ist keiner frisch, der juengste (er wird grau gezeigt).
    fun beste(kandidaten: Collection<Wert>, heute: LocalDate): Wert? {
        val frische = kandidaten.filter { frisch(it, heute) }
        if (frische.isNotEmpty()) return frische.sortedWith(compareBy<Wert>({ it.rang }, { reihenfolge(it) }, { -it.kursDatum.toEpochDay() }, { -it.abruf })).first()
        return kandidaten.sortedWith(compareBy<Wert>({ -it.kursDatum.toEpochDay() }, { it.rang })).firstOrNull()
    }

    // Bei mehreren Direktquellen eines Landes gilt die frueher in BoersenDirekt.QUELLEN eingetragene zuerst
    private fun reihenfolge(w: Wert): Int =
        if (w.quelle.startsWith("direkt:")) BoersenDirekt.QUELLEN.indexOfFirst { "direkt:" + it.id == w.quelle } else 0

    fun zusammenfuehren(alle: Map<String, Map<String, Wert>>, heute: LocalDate): Map<String, Wert> {
        val aus = LinkedHashMap<String, Wert>()
        for ((land, werte) in alle) beste(werte.values, heute)?.let { aus[land] = it }
        return aus
    }

    // Nur die frischen, als Tagesaenderung je Land (Einfaerbung der Ebene und Widget)
    fun frischeProzente(staende: Map<String, Wert>, heute: LocalDate): Map<String, Double> =
        staende.filterValues { frisch(it, heute) }.mapValues { it.value.prozent }

    // Zwischenspeicher: je Zeile land;quelle;abruf;kursDatum;prozent;wert
    fun schreiben(alle: Map<String, Map<String, Wert>>): String =
        alle.values.flatMap { it.values }.joinToString("\n") { w ->
            listOf(w.land, w.quelle, w.abruf, w.kursDatum, w.prozent, w.wert ?: "").joinToString(";")
        }

    fun lesen(text: String): Map<String, Map<String, Wert>> {
        val aus = LinkedHashMap<String, MutableMap<String, Wert>>()
        for (z in text.lines()) {
            val f = z.split(';')
            if (f.size < 6) continue
            val abruf = f[2].toLongOrNull() ?: continue
            val datum = runCatching { LocalDate.parse(f[3]) }.getOrNull() ?: continue
            val p = f[4].toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
            // Wert einer Selbstabruf-Quelle, die es nicht mehr gibt
            if (f[1].startsWith("selbst:") && IndexSelbst.QUELLEN.none { "selbst:" + it.id == f[1] }) continue
            aus.getOrPut(f[0]) { LinkedHashMap() }[f[1]] = Wert(f[0], f[1], p, f[5].toDoubleOrNull(), datum, abruf)
        }
        return aus
    }

    // Laender ohne Wertpapierboerse (Recherche vom 09.11.2026); alle anderen ohne Kurs: kein frei abrufbarer Index
    val OHNE_BOERSE: Set<String> = setOf(
        "AD", "LI", "MC", "SM", "VA", "KP", "CU", "TL", "TV", "NR", "KI", "PW", "MH", "FM", "ST", "KM", "DJ", "ER", "SO", "SS",
        "BI", "LR", "SL", "GM", "GN", "MR", "LS", "TM", "YE", "GY", "HT", "BZ", "AF", "BN", "MG", "CD", "WS", "TO",
    )
}
