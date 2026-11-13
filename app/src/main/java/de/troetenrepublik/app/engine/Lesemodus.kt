package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Rss

// Lesemodus der Nachrichtenansicht (5.95): welcher Knopf steht ueber der Originalseite bzw. bei der Textansicht.
object Lesemodus {
    enum class Knopf { NUR_TEXT, ORIGINAL, LAEDT, KEINER }

    // true: die App liest den Text einer Seite selbst mit; false bei Rss.keinLesemodus und Rss.ohneLesemodus (dort nur auf
    // Tipp aus der geladenen Seite bzw. gar nicht).
    fun automatisch(link: String, quelle: String = ""): Boolean = !Rss.keinLesemodus(link, quelle) && !Rss.ohneLesemodus(link, quelle)

    // webAnsicht: Plattform zeigt Seiten in der App; adresse: Link beginnt mit http; original: Seite ist zu sehen;
    // geladen: Plattform meldet das Ladeende; text: Absaetze liegen vor; automatisch: die App liest selbst mit
    // (nicht bei Rss.ohneLesemodus); laedt: automatisches Lesen laeuft; liest: Auslese auf Tipp laeuft;
    // gelesen: Auslese auf Tipp ist fertig; erlaubt: die Quelle erlaubt das Auslesen.
    fun knopf(webAnsicht: Boolean, adresse: Boolean, original: Boolean, geladen: Boolean, text: Boolean,
              automatisch: Boolean, laedt: Boolean, liest: Boolean, gelesen: Boolean, erlaubt: Boolean = true): Knopf {
        if (!webAnsicht || !adresse) return Knopf.KEINER
        // Ohne Erlaubnis kein Auslesen und kein Nur-Text-Knopf; in der Textansicht nur der Rueckweg zum Original
        if (!erlaubt) return if (original) Knopf.KEINER else Knopf.ORIGINAL
        if (original) {
            if (liest) return Knopf.LAEDT
            if (text || geladen) return Knopf.NUR_TEXT
            if (laedt && automatisch) return Knopf.LAEDT
            return Knopf.KEINER
        }
        return if (text || gelesen || !laedt) Knopf.ORIGINAL else Knopf.KEINER
    }

    // Auf den Tipp „Nur Text anzeigen“: true liest aus der geladenen Seite, false zeigt den vorhandenen Text.
    fun ausSeiteLesen(text: Boolean, geladen: Boolean): Boolean = !text && geladen

    // Zwischenseiten von Google News und der Einwilligung von Google sind nicht die Seite der Zeitung.
    fun zwischenseite(adresse: String?): Boolean {
        if (adresse == null) return false
        val mitHost = adresse.substringAfter("://", "")
        if (mitHost.isEmpty()) return false
        val host = mitHost.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':').lowercase()
        return host == "news.google.com" || host == "consent.google.com"
    }
}
