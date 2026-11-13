package de.troetenrepublik.app.engine

// Sprung von der Indikator-Ansicht in den erweiterten Chart (0.76): Der Chart oeffnet in der Kerzen-Darstellung auf
// dem Zeitraum um ein Kerzenmuster und markiert dessen Kerzen. Der Sprung steckt in der Route des Charts:
// chart/<Kuerzel>?muster=<Name>&von=<Tag>&bis=<Tag>, Tage als Tage seit 1970 (erster und letzter Tag des Musters).
object ChartSprung {

    class Muster(val name: String, val vonTag: Long, val bisTag: Long)

    // Der Zeitraum muss laenger als 100 Tage sein, damit der Chart Tageskerzen holt (Zeitraum.intervall).
    const val TAGE_DAVOR = 110L
    const val TAGE_DANACH = 10L

    fun route(kuerzel: String, m: Kerzenmuster.Marke): String = "chart/$kuerzel?muster=${m.muster}&von=${m.vonTag}&bis=${m.bisTag}"

    // Zerlegt eine Chart-Route (mit oder ohne "chart/"-Vorsatz) in Kuerzel und Muster; ohne gueltige Angaben ist das
    // Muster null.
    fun lesen(route: String): Pair<String, Muster?> {
        val r = route.removePrefix("chart/")
        val kuerzel = r.substringBefore('?')
        if (!r.contains('?')) return kuerzel to null
        val q = r.substringAfter('?').split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val name = q["muster"]?.takeIf { it in Kerzenmuster.MUSTER }
        val von = q["von"]?.toLongOrNull()
        val bis = q["bis"]?.toLongOrNull()
        return kuerzel to if (name != null && von != null && bis != null && von <= bis) Muster(name, von, bis) else null
    }

    // Zeitraum des Charts in Tagen seit 1970 (von, bis).
    fun zeitraum(m: Muster): Pair<Long, Long> = (m.vonTag - TAGE_DAVOR) to (m.bisTag + TAGE_DANACH)
}
