package de.troetenrepublik.app.engine

// Welche Links die App selbst zeigt (0.68): Webseiten ja; Dateien, Downloads, Anmeldung,
// Bezahlung und Stores oeffnen extern.
object AppLinkRegel {
    private val dateiEndungen = listOf(
        ".pdf", ".zip", ".apk", ".aab", ".csv", ".xlsx", ".xls", ".ods", ".json", ".xml", ".txt",
        ".dmg", ".msi", ".exe", ".deb", ".rpm", ".gz", ".tgz", ".7z",
    )
    private val externeRechner = listOf(
        "github.com", "githubusercontent.com", "buymeacoffee.com", "x.com", "twitter.com",
        "play.google.com", "apps.apple.com", "accounts.google.com",
    )
    private val kontoTeile = setOf(
        "login", "signin", "sign-in", "signup", "sign-up", "register", "registration", "account",
        "accounts", "settings", "oauth", "auth", "apikey", "api-key", "app-passwords",
    )

    // Pfad ohne Protokoll und Rechner, ohne Query und Anker, klein
    private fun pfad(adresse: String): String {
        val ohneProtokoll = adresse.substringAfter("://")
        val i = ohneProtokoll.indexOf('/')
        if (i < 0) return ""
        return ohneProtokoll.substring(i).substringBefore('#').substringBefore('?').lowercase()
    }

    fun imApp(link: String): Boolean {
        val a = WissenQuellen.adresse(link) ?: return false
        val pf = pfad(a)
        if (dateiEndungen.any { pf.endsWith(it) }) return false
        val host = WissenQuellen.anbieter(a)
        if (externeRechner.any { host == it || host.endsWith(".$it") }) return false
        if (pf.split('/').any { it in kontoTeile }) return false
        return true
    }
}
