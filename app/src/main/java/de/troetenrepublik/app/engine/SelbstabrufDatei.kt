package de.troetenrepublik.app.engine

// Hilfsfunktionen fuer den Selbstabruf der Quellen (seit 5.93): pruefen, ob eine Datei zur Quelle passt, den
// Dateinamen aus Content-Disposition oder URL bestimmen, data:-Adressen dekodieren und den Downloads-Ordner aus
// der XDG-Konfiguration lesen. Reine Funktionen, von Android und Desktop gemeinsam genutzt.

object SelbstabrufDatei {

    // Anhaenge von Teildateien, die der Browser noch schreibt
    private val TEILDATEI = listOf(".crdownload", ".part", ".tmp", ".download")

    // Passt der Name zur Quelle? Endung kleingeschrieben in endungen, Muster (falls gesetzt) muss im Namen stecken.
    // Teildateien des Browsers und versteckte Dateien zaehlen nie.
    fun passt(name: String, endungen: List<String>, muster: String): Boolean {
        if (name.isEmpty() || name.startsWith(".")) return false
        val klein = name.lowercase()
        if (TEILDATEI.any { klein.endsWith(it) }) return false
        val endung = klein.substringAfterLast('.', "")
        if (endung.isEmpty() || endung !in endungen) return false
        return muster.isEmpty() || Regex(muster, RegexOption.IGNORE_CASE).containsMatchIn(name)
    }

    // Eine Datei wird erst gelesen, wenn ihre Groesse in zwei Runden gleich und nicht null war.
    fun stabil(vorher: Long?, jetzt: Long): Boolean = vorher != null && vorher == jetzt && jetzt > 0

    // Dateiname aus Content-Disposition (zuerst filename*=UTF-8''.., sonst filename=..) oder dem letzten
    // Pfadabschnitt der URL. data: und blob: haben keinen Pfad, dann heisst die Datei download.
    fun dateiname(inhalt: String?, url: String): String {
        var name = ""
        if (inhalt != null) {
            val dekodiert = Regex("(?i)filename\\*=\\s*[^;]*?UTF-8''([^;]+)").find(inhalt)?.groupValues?.get(1)
            if (dekodiert != null) name = dekodiere(dekodiert)
            else name = Regex("(?i)filename\\s*=\\s*\"?([^\";]+)\"?").find(inhalt)?.groupValues?.get(1).orEmpty().trim()
        }
        if (name.isBlank()) {
            val klein = url.lowercase()
            if (!klein.startsWith("data:") && !klein.startsWith("blob:")) {
                name = dekodiere(url.substringBefore('?').substringBefore('#').substringAfterLast('/'))
            }
        }
        val sauber = name.trim().ifBlank { "download" }
        return if (sauber.length <= 120) sauber else sauber.take(120)
    }

    // Bytes einer data:-Adresse. Base64 nur, wenn es im Kopf steht; sonst Prozentkodierung byteweise (ISO-8859-1
    // bleibt 1:1). null bei falscher Adresse oder leeren Daten.
    fun dataUrl(url: String): ByteArray? {
        if (!url.startsWith("data:", true)) return null
        val komma = url.indexOf(',')
        if (komma < 0) return null
        val kopf = url.substring(5, komma).lowercase()
        val rest = url.substring(komma + 1)
        val bytes = if (kopf.contains(";base64")) {
            runCatching { java.util.Base64.getMimeDecoder().decode(rest) }.getOrNull()
        } else {
            runCatching { java.net.URLDecoder.decode(rest.replace("+", "%2B"), "ISO-8859-1").toByteArray(Charsets.ISO_8859_1) }.getOrNull()
        }
        return bytes?.takeIf { it.isNotEmpty() }
    }

    // Wert von XDG_DOWNLOAD_DIR aus user-dirs.dirs, $HOME ersetzt; null ohne die Zeile oder ohne Wert.
    fun downloadOrdnerAusXdg(inhalt: String, heim: String): String? {
        val zeile = Regex("(?m)^\\s*XDG_DOWNLOAD_DIR\\s*=\\s*\"?([^\"\\n]+)\"?\\s*$").find(inhalt) ?: return null
        val wert = zeile.groupValues[1].trim().replace("\$HOME", heim).trim()
        return wert.ifEmpty { null }
    }

    // Prozentweise dekodieren; ein Plus bleibt Plus (erst %2B daraus machen, URLDecoder macht sonst Leerzeichen daraus).
    private fun dekodiere(text: String): String =
        runCatching { java.net.URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") }.getOrDefault(text)
}
