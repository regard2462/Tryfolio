package de.troetenrepublik.app.market

import okhttp3.HttpUrl
import okhttp3.Interceptor
import java.io.IOException

// Datenabrufe senden nie eine E-Mail-Adresse, weder in der Adresse noch in einer Kopfzeile (seit 5.92).
// Laeuft als letzter Schritt vor dem Senden; eine Anfrage mit Adresse wird verworfen statt gesendet.
// Einzige Ausnahme: Abrufe bei der SEC (sec.gov) mit der Kontaktadresse, die der Nutzer unter Zugaenge selbst eintraegt.
object OhneAdresse {
    private val ADRESSE = Regex("[A-Za-z0-9._%+-]+(@|%40)[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")

    fun enthaelt(text: String) = ADRESSE.containsMatchIn(text)

    // Pfad und Abfrage der Adresse sowie alle Kopfzeilen ausser Cookies (die setzt der Server selbst); ein Nutzerteil zaehlt mit.
    fun enthaelt(url: HttpUrl, kopf: Iterable<Pair<String, String>>): Boolean =
        enthaelt(url.encodedPath) || url.encodedQuery?.let { enthaelt(it) } == true ||
            url.encodedUsername.isNotEmpty() || kopf.any { (name, wert) -> !name.equals("Cookie", true) && enthaelt(wert) }

    fun sec(host: String) = host == "sec.gov" || host.endsWith(".sec.gov")

    val sperre = Interceptor { chain ->
        val r = chain.request()
        if (!sec(r.url.host) && enthaelt(r.url, r.headers)) throw IOException("request with e-mail address blocked")
        chain.proceed(r)
    }
}
