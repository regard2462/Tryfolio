package de.troetenrepublik.app.market

import kotlinx.coroutines.delay

// Gemeinsamer Abruf bei der SEC fuer die Firmenbausteine Auftragsbestand, Pruefer und Rechtsstreit: hoechstens fuenf
// Abrufe je Sekunde, kein Abruf waehrend einer Sperre nach 403 oder 429.
internal object FirmaSec {
    private val api by lazy { MarketApi() }

    // Die SEC hat abgelehnt, dann gilt die Sperre der App
    class Gesperrt : Exception()

    // Kopf mit der Kennung der App (Kontakt nur bei der SEC)
    fun kopf(kennung: String, accept: String = "application/json") = mapOf("User-Agent" to kennung, "Accept" to accept)

    suspend fun anfrage(url: String, kennung: String, max: Int = 3_000_000, accept: String = "application/json"): MarketApi.Antwort {
        if (AbrufAblage.gesperrt("q:sec")) throw Gesperrt()
        delay(200)
        return api.anfrage(url, kopf(kennung, accept), max = max)
    }

    // Antwort als Strom, etwa fuer das Hauptdokument eines Jahresberichts; null bei Netzfehler oder Fehlerstatus
    suspend fun <T> strom(url: String, kennung: String, lesen: (java.io.InputStream) -> T): T? {
        if (AbrufAblage.gesperrt("q:sec")) throw Gesperrt()
        delay(200)
        return api.strom(url, kopf(kennung, "text/html, */*"), lesen)
    }
}
