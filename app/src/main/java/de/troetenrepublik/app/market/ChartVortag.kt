package de.troetenrepublik.app.market

// [Chart-Rueckfall 0.73] Vortagsschluss aus der Chart-Antwort von Yahoo (range=5d, interval=1d). Die Kerze des laufenden Tages
// hat oft noch keinen Schluss (null); sie zaehlt nicht. Entscheidend ist der Tag der letzten Kerze mit Schluss gegen den Tag
// der Marktzeit (in der Zeit der Boerse, gmtoffset): liegt die letzte Kerze auf dem Tag des Kurses, ist sie der laufende
// oder letzte Handelstag und der Vortag die Kerze davor; liegt sie davor, ist sie selbst der Vortag. Mit nur einer Kerze
// gilt chartPreviousClose, das dann der echte Vortagsschluss ist.
object ChartVortag {
    fun schluss(
        zeiten: List<Long>?, schluesse: List<Double?>?, marktzeit: Long?, versatz: Long?,
        previousClose: Double?, chartPreviousClose: Double?, preis: Double,
    ): Double {
        previousClose?.takeIf { it > 0.0 }?.let { return it }
        val basis = chartPreviousClose?.takeIf { it > 0.0 } ?: preis
        val reihe = ArrayList<Pair<Long, Double>>()
        if (schluesse != null) for (i in schluesse.indices) {
            val c = schluesse[i]?.takeIf { it > 0.0 } ?: continue
            reihe += (zeiten?.getOrNull(i) ?: continue) to c
        }
        if (reihe.isEmpty()) return basis
        if (marktzeit == null || marktzeit <= 0L) return if (reihe.size >= 2) reihe[reihe.size - 2].second else basis
        val off = versatz ?: 0L
        fun tag(t: Long) = (t + off).floorDiv(86_400L)
        val letzte = reihe.last()
        return if (tag(letzte.first) >= tag(marktzeit)) {
            if (reihe.size >= 2) reihe[reihe.size - 2].second else basis
        } else letzte.second
    }
}
