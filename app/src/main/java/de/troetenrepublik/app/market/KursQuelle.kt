package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.Splits

// [0.73] Weitere Kursquelle je Wert: Werte von Boersen ohne Yahoo-Abdeckung tragen die Quelle im Kuerzel ("MOEX:GAZP",
// Praefix, Doppelpunkt, Kennung an der Boerse). MarketApi leitet Kurs, Kerzen, Dividenden, Splits und Suche solcher Werte an
// die Quelle; alles andere an der Wertpapierseite, das Yahoo braucht, bleibt dafuer leer. Neue Boersen brauchen nur eine
// weitere KursQuelle und ihr Praefix in KursQuellen.PRAEFIXE.
interface KursQuelle {
    // Praefix im Kuerzel, z. B. "MOEX"
    val praefix: String

    // Handelswaehrung der Werte (ISO), z. B. "RUB"
    val waehrung: String

    // Kurse zu Kennungen (ohne Praefix); Schluessel der Antwort ist das volle Kuerzel mit Praefix. Preise in Handelswaehrung.
    suspend fun kurse(kennungen: List<String>): Map<String, Quote>

    // Kerzen im Format der Yahoo-Abfragen der App (range wie "1d", "5d", "1mo", "1y", "10y"; interval wie "2m", "15m", "1d", "1wk")
    suspend fun kerzen(kennung: String, range: String, interval: String): Series

    // Kerzen zwischen zwei Zeitpunkten in Millisekunden
    suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String): Series

    // Ausgeschuettete Dividenden (Ex-Tag in Millisekunden, Betrag je Stueck in Handelswaehrung); leer, wenn es keine gibt
    suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent>

    suspend fun splits(kennung: String): List<Splits.Split>

    suspend fun suche(text: String): List<MarketApi.SearchHit>

    // Einheiten der Handelswaehrung je Euro, leer ohne Antwort
    suspend fun wechselkurse(): Map<String, Double>

    // true, wenn die Quelle mehrfach nacheinander nicht geantwortet hat (Netzfilter, VPN, Stoerung); eine Antwort setzt es zurueck
    val antwortetNicht: Boolean get() = false
}

object KursQuellen {
    const val MOEX = "MOEX"

    // Praefixe bekannter weiterer Kursquellen
    val PRAEFIXE: Set<String> = setOf(MOEX)

    fun symbol(praefix: String, kennung: String): String = "$praefix:$kennung"

    // Praefix und Kennung eines Kuerzels dieser Art; null fuer jedes andere Kuerzel (auch fuer ein unbekanntes Praefix)
    fun teile(symbol: String): Pair<String, String>? {
        val i = symbol.indexOf(':')
        if (i <= 0) return null
        val p = symbol.substring(0, i)
        val k = symbol.substring(i + 1)
        if (p !in PRAEFIXE || k.isEmpty() || k.length > 24 || !k.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }) return null
        return p to k
    }

    fun istExtern(symbol: String): Boolean = teile(symbol) != null

    // Kuerzel fuer eine Firma der Laenderliste: nur Laender mit einer Kursquelle, sonst null
    fun symbolFuerLand(a2: String, kennung: String): String? = when {
        kennung.isBlank() -> null
        a2 == "RU" -> teile(symbol(MOEX, kennung))?.let { symbol(MOEX, kennung) }
        else -> null
    }
}
