package de.troetenrepublik.app.plattform

// Eingebetteter Browser fuer MarketWatch-Artikel und das Finanzwissen am Rechner (WebBaustein der Desktop-Fassung).
// Auf Android uebernimmt das die Web-Ansicht, hier gibt es nichts zu laden.
object WebBaustein {
    val groesseMb: Int = 0
    fun noetig(link: String): Boolean = false
    val installiert: Boolean get() = true
    suspend fun installieren(fortschritt: (Float) -> Unit): Boolean = true
    suspend fun artikelOeffnen(link: String, titel: String): Boolean = false
    val artikelGeladen: Boolean = false
    val artikelOffen: Boolean = false
    suspend fun artikelText(): List<String>? = null
}
