package de.troetenrepublik.app.engine

// [0.78] Zustand von Blaettern als Text, damit er eine aus dem Blatt geoeffnete Seite uebersteht: das Blatt verlaesst
// den Aufbau, solange die Seite darueber liegt, und liest seinen Zustand beim Wiederkommen zurueck.
object Blattzustand {
    private const val TRENNER = "|"

    // Ein Bein des Optionsrechners: Art, Menge (negativ = verkauft), Strike.
    fun beinText(art: String, menge: Int, strike: Double): String = art + TRENNER + menge + TRENNER + strike

    // Gegenstueck zu beinText; null bei unlesbarem Text.
    fun beinLesen(text: String): Triple<String, Int, Double>? {
        val teile = text.split(TRENNER)
        if (teile.size != 3 || teile[0].isBlank()) return null
        val menge = teile[1].toIntOrNull() ?: return null
        val strike = teile[2].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return Triple(teile[0], menge, strike)
    }

    // Eine Schlagzeile des Tagesrueckblicks: Titel, Quelle, Adresse, Zeit. Eine leere Liste steht fuer „keine offen“.
    fun nachrichtFelder(n: Tagesrueckblick.Nachricht?): List<String> =
        if (n == null) emptyList() else listOf(n.titel, n.quelle, n.link, n.zeit.toString())

    fun nachrichtLesen(felder: List<String>): Tagesrueckblick.Nachricht? {
        if (felder.size != 4) return null
        val zeit = felder[3].toLongOrNull() ?: return null
        return Tagesrueckblick.Nachricht(felder[0], felder[1], felder[2], zeit)
    }
}
