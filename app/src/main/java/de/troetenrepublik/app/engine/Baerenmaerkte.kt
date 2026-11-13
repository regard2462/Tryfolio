package de.troetenrepublik.app.engine

// Baerenmaerkte: alle Rueckgaenge eines Index ab 20 % vom Hoch mit Beginn, Tiefe, Dauer bis zum Tief und bis zur Erholung.
// Die Rueckgaenge selbst rechnet der Warnzeichen-Rueckblick (WarnRueckblick.ereignisse, dort ab 10 %) aus der Tagesreihe;
// hier wird nur ausgewaehlt und gemittelt.
object Baerenmaerkte {

    // Rueckgang ab 20 % vom Hoch: Tag des Hochs (Tage seit 1970), Tiefe in Prozent, Tage bis zum Tief und bis der Kurs das
    // alte Hoch wieder erreichte (null: noch nicht)
    data class Baer(val hoch: Int, val tiefe: Double, val bisTief: Int, val bisErholt: Int?)

    data class Bild(val liste: List<Baer>, val vonJahr: Int, val mittelTiefe: Double, val mittelBisTief: Int, val mittelBisErholt: Int?)

    const val AB = 20.0

    // Alle Rueckgaenge ab 20 % eines Index aus dem Warnzeichen-Rueckblick (dort schon ab 10 % gerechnet), aelteste zuerst
    fun aus(d: WarnRueckblick.Datei?, id: String): Bild? {
        val x = d?.indizes?.firstOrNull { it.id == id } ?: return null
        val liste = x.ereignisse.filter { it.tiefe >= AB }.sortedBy { it.hoch }
            .map { Baer(it.hoch, it.tiefe, it.tief - it.hoch, it.erholt?.let { e -> e - it.hoch }) }
        if (liste.isEmpty()) return null
        val erholt = liste.mapNotNull { it.bisErholt }
        return Bild(
            liste, java.time.LocalDate.ofEpochDay(x.von.toLong()).plusDays(7).year, liste.map { it.tiefe }.average(),
            Math.round(liste.map { it.bisTief }.average()).toInt(), if (erholt.isEmpty()) null else Math.round(erholt.average()).toInt(),
        )
    }

    // Tage als volle Monate (kaufmaennisch gerundet, mindestens 1)
    fun monate(tage: Int): Int = Math.round(tage / 30.4375).toInt().coerceAtLeast(1)
}
