package de.troetenrepublik.app.engine

// Tresornamen, die beim Start einmalig geloescht werden (AppState.tresorBereinigen): welche Eintraege je Art im
// Tresor der Plattform stehen koennen.
object TresorBereinigung {
    // Art zu den Feldern, die je Art im Tresor stehen koennen (bei IBKR inklusive zertifikat).
    val ARTEN: Map<String, List<String>> = mapOf(
        "ALPACA" to listOf("schluessel", "geheim"),
        "TRADIER" to listOf("token"),
        "TRADING212" to listOf("schluessel", "geheim"),
        "KRAKEN" to listOf("schluessel", "geheim"),
        "ROBINHOOD" to listOf("schluessel", "privat"),
        "COMDIRECT" to listOf("clientId", "clientSecret", "zugang", "pin"),
        "IBKR" to listOf("adresse", "zertifikat"),
    )

    // Tresornamen einer Art im Schema broker_<art>_<feld>; unbekannte Art: leere Liste.
    fun tresorNamen(art: String): List<String> =
        ARTEN[art]?.map { feld -> "broker_${art.lowercase()}_$feld" } ?: emptyList()

    // Alle Tresornamen aller Arten; so loescht die App sie auch ohne Eintrag in den Einstellungen.
    fun alleTresorNamen(): List<String> = ARTEN.keys.flatMap { tresorNamen(it) }
}
