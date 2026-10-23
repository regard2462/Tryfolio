package de.troetenrepublik.app.engine

// Bullen- oder Baerenmarkt (seit 5.9) nach der ueblichen Faustregel: Ein Baerenmarkt beginnt, wenn ein Index 20 %
// unter seinem letzten Hoch schliesst, und endet, wenn er 20 % ueber seinem Tief steht; dann beginnt ein
// Bullenmarkt. Fallen innerhalb eines Bullenmarkts 10 bis 20 %, gilt das als Korrektur. Dazu die 200-Tage-Linie und
// das letzte Kreuzen der 50- und 200-Tage-Linie (Golden Cross nach oben, Death Cross nach unten).
object Marktphase {
    // Schluessel und Yahoo-Symbol; MSCI World ueber den ETF URTH.
    val INDIZES = listOf(
        "sp500" to "^GSPC", "nasdaq" to "^NDX", "world" to "URTH", "dax" to "^GDAXI",
        "stoxx" to "^STOXX50E", "nikkei" to "^N225", "bitcoin" to "BTC-USD",
    )

    enum class Art { BULLE, BAER }

    data class Phase(
        val schluessel: String,
        val art: Art,
        // Beginn der Phase: das Tief vor einem Bullenmarkt, das Hoch vor einem Baerenmarkt (Millisekunden).
        val seit: Long,
        // Abstand zum hoechsten Schluss der ganzen Reihe (zehn Jahre, meist das Rekordhoch), in Prozent, hoechstens 0.
        val vomHoch: Double,
        // Abstand zum Tief der Phase (Baer) oder dem Tief vor dem Anstieg (Bulle), in Prozent.
        val vomTief: Double,
        val korrektur: Boolean,
        // Kurs gegenueber der 200-Tage-Linie in Prozent; null bei zu kurzer Reihe.
        val zur200: Double?,
        // Letztes Kreuzen der 50- und 200-Tage-Linie: true Golden Cross, false Death Cross; null ohne Kreuzung.
        val golden: Boolean?,
        val kreuzSeit: Long?,
        // Schwelle, ab der die Phase kippt: 80 % des Hochs (Bulle) oder 120 % des Tiefs (Baer).
        val schwelle: Double,
        val verlauf: List<Pair<Long, Double>>,
    )

    fun phase(schluessel: String, kurse: List<Pair<Long, Double>>): Phase? {
        if (kurse.size < 60) return null
        var art = Art.BULLE
        var hoch = kurse.first(); var tief = kurse.first()
        var seit = kurse.first().first
        // Bezugspunkte der laufenden Phase: Hoch vor einem Baerenmarkt, Tief vor einem Bullenmarkt
        var hochVorher = hoch; var tiefVorher = tief
        for (k in kurse) {
            if (art == Art.BULLE) {
                if (k.second > hoch.second) hoch = k
                if (k.second <= hoch.second * 0.8) {
                    art = Art.BAER; seit = hoch.first; hochVorher = hoch; tief = k
                }
            } else {
                if (k.second < tief.second) tief = k
                if (k.second >= tief.second * 1.2) {
                    art = Art.BULLE; seit = tief.first; tiefVorher = tief; hoch = k
                }
            }
        }
        val letzter = kurse.last().second
        val vomPhasenhoch = (letzter / (if (art == Art.BULLE) hoch.second else hochVorher.second) - 1) * 100
        val vomHoch = (letzter / kurse.maxOf { it.second } - 1) * 100
        val vomTief = (letzter / (if (art == Art.BULLE) tiefVorher.second else tief.second) - 1) * 100
        fun schnitt(n: Int, bis: Int) = if (bis + 1 >= n) kurse.subList(bis + 1 - n, bis + 1).sumOf { it.second } / n else null
        val zur200 = schnitt(200, kurse.lastIndex)?.let { (letzter / it - 1) * 100 }
        // Letzte Kreuzung der 50- und 200-Tage-Linie
        var golden: Boolean? = null
        var kreuz: Long? = null
        var vorher: Boolean? = null
        for (i in 199 until kurse.size) {
            val oben = schnitt(50, i)!! > schnitt(200, i)!!
            if (vorher != null && oben != vorher) { golden = oben; kreuz = kurse[i].first }
            vorher = oben
        }
        if (golden == null && vorher != null) golden = vorher
        return Phase(
            schluessel, art, seit, minOf(0.0, vomHoch), vomTief, art == Art.BULLE && vomPhasenhoch <= -10, zur200, golden, kreuz,
            if (art == Art.BULLE) hoch.second * 0.8 else tief.second * 1.2, kurse.takeLast(520),
        )
    }
}
