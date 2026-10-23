package de.troetenrepublik.app.market.broker

import de.troetenrepublik.app.market.MarketApi

// Verbindbare Broker. felder sind die Kennungen der Eingabefelder (Texte in Strings.brokerFeld),
// im Tresor liegen sie unter broker_<art>_<feld>.
enum class BrokerArt(
    val anzeige: String,
    val felder: List<String>,
    // Der Broker hat ein Papier- oder Demokonto.
    val papier: Boolean,
    // Seite, auf der man den Schluessel bekommt.
    val schluesselSeite: String,
    // Kurz, Texte in Strings.brokerAngebot.
    val angebot: String,
) {
    ALPACA("Alpaca", listOf("schluessel", "geheim"), true, "https://app.alpaca.markets/", "us_krypto"),
    TRADIER("Tradier", listOf("token"), true, "https://web.tradier.com/user/api", "us"),
    TRADING212("Trading 212", listOf("schluessel", "geheim"), true, "https://www.trading212.com/", "eu"),
    KRAKEN("Kraken", listOf("schluessel", "geheim"), false, "https://www.kraken.com/u/security/api", "krypto"),
    ROBINHOOD("Robinhood", listOf("schluessel", "privat"), false, "https://robinhood.com/account/crypto", "us_krypto"),
    COMDIRECT("comdirect", listOf("clientId", "clientSecret", "zugang", "pin"), false, "https://www.comdirect.de/cms/kontakt-zugaenge-api.html", "de"),
    IBKR("Interactive Brokers", listOf("adresse"), true, "https://www.interactivebrokers.com/campus/ibkr-api-page/cpapi-v1/", "welt");

    // Die Felder und bei IBKR der bestaetigte Fingerabdruck des Gateways.
    val gespeichert: List<String> get() = if (this == IBKR) felder + "zertifikat" else felder

    // isin liefert die ISIN zu einem App-Symbol, fuer Broker, die Wertpapiere nur ueber die ISIN kennen.
    fun erstellen(api: MarketApi, werte: Map<String, String>, live: Boolean, isin: (String) -> String = { "" }): Broker = when (this) {
        ALPACA -> AlpacaBroker(api, werte["schluessel"].orEmpty(), werte["geheim"].orEmpty(), live)
        TRADIER -> TradierBroker(api, werte["token"].orEmpty(), live)
        TRADING212 -> Trading212Broker(api, werte["schluessel"].orEmpty(), werte["geheim"].orEmpty(), live)
        KRAKEN -> KrakenBroker(api, werte["schluessel"].orEmpty(), werte["geheim"].orEmpty())
        ROBINHOOD -> RobinhoodBroker(werte["schluessel"].orEmpty(), werte["privat"].orEmpty())
        IBKR -> IbkrBroker(api, werte["adresse"].orEmpty(), werte["zertifikat"].orEmpty(), live)
        COMDIRECT -> ComdirectBroker(api, werte["clientId"].orEmpty(), werte["clientSecret"].orEmpty(), werte["zugang"].orEmpty(), werte["pin"].orEmpty(), isin)
    }
}
