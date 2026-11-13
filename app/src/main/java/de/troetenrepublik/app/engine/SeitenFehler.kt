package de.troetenrepublik.app.engine

// Abgewiesene Artikelseite in der eingebauten Web-Ansicht: Status 0 steht fuer einen Ladefehler des Hauptdokuments
// (kein Netz, Zeitueberschreitung, Zertifikat), ab 400 fuer die Antwort der Seite.
object SeitenFehler {
    enum class Art { ABGELEHNT, WEG, ZU_OFT, NICHT_ERREICHBAR, LADEFEHLER }

    fun art(status: Int): Art? = when (status) {
        0 -> Art.LADEFEHLER
        404, 410 -> Art.WEG
        429 -> Art.ZU_OFT
        in 500..599 -> Art.NICHT_ERREICHBAR
        in 400..499 -> Art.ABGELEHNT
        else -> null
    }

    // Ein Fehler der Zwischenseiten von Google News gilt nicht als Antwort der Zeitung: nur ein Ladefehler meldet dort.
    fun melden(status: Int, adresse: String?): Boolean {
        if (art(status) == null) return false
        return status == 0 || !Lesemodus.zwischenseite(adresse)
    }

    // Zweite Zeile nur bei 403.
    fun mitVpnZeile(status: Int): Boolean = status == 403
}
