package de.troetenrepublik.app.engine

// Quellen aus den Lektionen des Finanzwissens (0.68), gemeldet von der Seite.
object WissenQuellen {

    // Nur gueltige Web-Adressen (http oder https mit Rechnername), sonst null.
    fun adresse(link: String?): String? {
        val t = link?.trim() ?: return null
        if (t.length > 2000 || t.any { it.isWhitespace() }) return null
        val klein = t.lowercase()
        if (!klein.startsWith("http://") && !klein.startsWith("https://")) return null
        val host = t.substring(t.indexOf("://") + 3).takeWhile { it != '/' && it != '?' && it != '#' }
        if (!host.contains('.') || host.startsWith(".") || host.endsWith(".")) return null
        return t
    }

    // Rechnername klein ohne www. und Port, leer ohne Rechnername.
    fun anbieter(link: String): String {
        val i = link.indexOf("://")
        if (i < 0) return ""
        val rest = link.substring(i + 3)
        return rest.takeWhile { it != '/' && it != '?' && it != '#' && it != ':' }.lowercase().removePrefix("www.")
    }

    // Titel einzeilig und hoechstens 300 Zeichen, an der Wortgrenze gekuerzt.
    fun titel(t: String?): String {
        val s = (t ?: "").replace(Regex("\\s+"), " ").trim()
        if (s.length <= 300) return s
        val ende = if (s[300] == ' ') 300 else s.substring(0, 300).lastIndexOf(' ').let { if (it > 0) it else 300 }
        return s.substring(0, ende).trimEnd()
    }
}
