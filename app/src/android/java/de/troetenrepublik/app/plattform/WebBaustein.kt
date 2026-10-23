package de.troetenrepublik.app.plattform

// Eingebetteter Browser fuer Seiten, die nur ein echter Browser lesen darf.
// Android nutzt dafuer seine Web-Ansicht, hier gibt es nichts zu laden.
object WebBaustein {
    val groesseMb: Int = 0
    fun noetig(link: String): Boolean = false
    val installiert: Boolean get() = true
    suspend fun installieren(fortschritt: (Float) -> Unit): Boolean = true
    suspend fun lesen(link: String): List<String>? = null
    val bestaetigungNoetig: Boolean = false
    suspend fun bestaetigen(link: String, titel: String): Boolean = false
}
