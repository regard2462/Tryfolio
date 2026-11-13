package de.troetenrepublik.app.engine

// Abruf eines Flex-Berichts ueber den Flex Web Service von Interactive Brokers (Version 3) in zwei Schritten:
// SendRequest liefert eine Referenznummer, GetStatement den Bericht. Solange der Bericht entsteht, antwortet der
// Dienst mit einer Fehlernummer (1019 und verwandte); dann wartet der Abruf und fragt erneut.
// Grenzen des Dienstes je Token: eine Anfrage je Sekunde, zehn je Minute. Der Takt bleibt darunter.
// Token und Abfragenummer stehen nur in der Adresse an den Rechner des Brokers und in keiner Meldung.
// holen: Antworttext oder null ohne Antwort. uhr und warten sind fuer Tests austauschbar.
class FlexAbruf(
    private val holen: suspend (String) -> String?,
    private val uhr: () -> Long,
    private val warten: suspend (Long) -> Unit,
) {
    sealed interface Ergebnis {
        data class Ok(val xml: String) : Ergebnis
        // code: Fehlernummer des Dienstes, 0 ohne.
        data class Fehler(val art: FlexBericht.Fehlerart, val code: Int) : Ergebnis
        // Keine Antwort oder eine unlesbare.
        data object KeineAntwort : Ergebnis
        // Token oder Abfragenummer haben nicht die erwartete Form; es ging nichts hinaus.
        data object Eingabe : Ergebnis
    }

    // Zeitpunkte der letzten Anfragen, fuer den Takt.
    private val gesendet = ArrayDeque<Long>()

    // Millisekunden bis zur naechsten erlaubten Anfrage.
    internal fun pause(jetzt: Long): Long {
        while (gesendet.isNotEmpty() && jetzt - gesendet.first() >= FENSTER_MS) gesendet.removeFirst()
        var p = 0L
        gesendet.lastOrNull()?.let { p = maxOf(p, it + ABSTAND_MS - jetzt) }
        if (gesendet.size >= JE_FENSTER) p = maxOf(p, gesendet.first() + FENSTER_MS - jetzt)
        return p.coerceAtLeast(0L)
    }

    private suspend fun anfrage(url: String): String? {
        if (!erlaubt(url)) return null
        val p = pause(uhr())
        if (p > 0) warten(p)
        gesendet.addLast(uhr())
        return holen(url)
    }

    suspend fun bericht(token: String, abfrage: String): Ergebnis {
        val t = token.trim()
        val q = abfrage.trim()
        if (!TOKEN.matches(t) || !NUMMER.matches(q)) return Ergebnis.Eingabe

        // Schritt 1: Bericht anfordern
        var auftrag: FlexBericht.Antwort? = null
        for (versuch in 0..SENDEN_WIEDERHOLEN.size) {
            val text = anfrage("$BASIS/SendRequest?t=$t&q=$q&v=3") ?: return Ergebnis.KeineAntwort
            val a = FlexBericht.antwort(text) ?: return Ergebnis.KeineAntwort
            if (a.ok) { auftrag = a; break }
            val art = FlexBericht.fehlerart(a.fehler)
            if (art != FlexBericht.Fehlerart.WARTEN || versuch == SENDEN_WIEDERHOLEN.size) return Ergebnis.Fehler(art, a.fehler)
            warten(SENDEN_WIEDERHOLEN[versuch])
        }
        val erhalten = auftrag ?: return Ergebnis.KeineAntwort
        val referenz = erhalten.referenz.takeIf { NUMMER.matches(it) } ?: return Ergebnis.KeineAntwort
        // Die Adresse zum Abholen nennt der Dienst; sie gilt nur, wenn sie beim Broker liegt
        val abholen = erhalten.adresse.takeIf { erlaubt(it) && '?' !in it && it.endsWith("/GetStatement") } ?: "$BASIS/GetStatement"

        // Schritt 2: abholen, solange der Bericht entsteht mit wachsender Pause
        var letzter = 0
        for (pause in ABHOLEN_PAUSEN) {
            warten(pause)
            val text = anfrage("$abholen?t=$t&q=$referenz&v=3") ?: return Ergebnis.KeineAntwort
            val a = FlexBericht.antwort(text)
            if (a == null) return if ("<FlexQueryResponse" in text || "<FlexStatement " in text) Ergebnis.Ok(text) else Ergebnis.KeineAntwort
            val art = FlexBericht.fehlerart(a.fehler)
            if (art != FlexBericht.Fehlerart.WARTEN) return Ergebnis.Fehler(art, a.fehler)
            letzter = a.fehler
        }
        return Ergebnis.Fehler(FlexBericht.Fehlerart.WARTEN, letzter)
    }

    companion object {
        const val RECHNER = "ndcdyn.interactivebrokers.com"
        private const val BASIS = "https://$RECHNER/AccountManagement/FlexWebService"
        private const val DOMAIN = "interactivebrokers.com"

        // Unter den Grenzen des Dienstes (1 je Sekunde, 10 je Minute)
        private const val ABSTAND_MS = 1_500L
        private const val FENSTER_MS = 60_000L
        private const val JE_FENSTER = 8
        private val SENDEN_WIEDERHOLEN = listOf(6_000L, 12_000L)
        private val ABHOLEN_PAUSEN = listOf(3_000L, 5_000L, 8_000L, 12_000L, 15_000L)

        private val TOKEN = Regex("^[A-Za-z0-9]{6,120}$")
        private val NUMMER = Regex("^[A-Za-z0-9]{1,40}$")

        fun tokenGueltig(s: String): Boolean = TOKEN.matches(s.trim())
        fun nummerGueltig(s: String): Boolean = NUMMER.matches(s.trim())

        // Nur https zu einem Rechner des Brokers, ohne Anmeldeteil und ohne Port.
        fun erlaubt(url: String): Boolean {
            if (!url.startsWith("https://")) return false
            val host = url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
            if (host.isEmpty() || '@' in host || ':' in host || '\\' in host || host.any { it.isWhitespace() }) return false
            return host == DOMAIN || host.endsWith(".$DOMAIN")
        }
    }
}
