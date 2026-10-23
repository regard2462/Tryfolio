package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Neue Fassungen der App: einmal am Tag fragt die App bei GitHub nach der neuesten veroeffentlichten
// Fassung und meldet jede neuere genau einmal. Die Anfrage traegt nur die offene Kennung der App, nichts ueber die
// Person; der Link fuehrt auf die Seite der Fassung mit allen Paketen.
object Aktualisierung {
    // Fassung dieser App. Muss zu versionName in app/build.gradle.kts passen (Fassung39Test prueft das).
    const val FASSUNG = "5.9"

    // Neueste Fassung des Repos ueber seine feste Nummer bei GitHub (bleibt auch bei Umbenennung gleich).
    const val QUELLE = "https://api.github.com/repositories/1431183164/releases/latest"

    const val ABSTAND_MS = 24 * 60 * 60_000L

    // Nach einem Fehler (kein Netz) spaetestens so bald noch einmal.
    const val WIEDERHOLUNG_MS = 60 * 60_000L

    data class Neu(val fassung: String, val link: String)

    // Antwort von GitHub (releases/latest): Fassung ohne „v“ und Seite der Fassung; Entwuerfe und Vorabfassungen nicht.
    fun lesen(text: String): Neu? = runCatching {
        val o = Json.parseToJsonElement(text) as? JsonObject ?: return null
        if (o["draft"]?.jsonPrimitive?.booleanOrNull == true || o["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().removePrefix("v").removePrefix("V")
        val link = o["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (zahlen(tag).isEmpty() || !link.startsWith("https://github.com/")) return null
        Neu(tag, link)
    }.getOrNull()

    // Zahlen einer Fassung: „3.10.1“ zu 3, 10, 1; Anhaenge wie „-beta“ zaehlen nicht.
    private fun zahlen(f: String): List<Int> =
        f.substringBefore('-').split('.').map { it.toIntOrNull() ?: return emptyList() }

    // Fehlende Stellen zaehlen als 0, „3.9“ und „3.9.0“ sind gleich.
    fun neuer(a: String, b: String): Boolean {
        val x = zahlen(a)
        val y = zahlen(b)
        if (x.isEmpty() || y.isEmpty()) return false
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    // geprueft ist der letzte Versuch, ok ob er eine Antwort bekam.
    fun faellig(jetzt: Long, geprueft: Long, ok: Boolean): Boolean {
        if (geprueft <= 0L || jetzt < geprueft) return true
        return jetzt - geprueft >= if (ok) ABSTAND_MS else WIEDERHOLUNG_MS
    }

    // Ergebnis einer Abfrage: neuere Fassung (leer, wenn keine) und ob sie noch gemeldet werden muss.
    data class Ergebnis(val fassung: String, val link: String, val melden: Boolean)

    fun auswerten(neu: Neu?, aktuell: String, gemeldet: String, hinweis: Boolean): Ergebnis {
        if (neu == null || !neuer(neu.fassung, aktuell)) return Ergebnis("", "", false)
        return Ergebnis(neu.fassung, neu.link, hinweis && neu.fassung != gemeldet)
    }

    // Antwort von GitHub; neu ist null, wenn sie keine Fassung enthielt (etwa 404).
    data class Abfrage(val neu: Neu?)

    // Neueste Fassung abfragen; null ohne Antwort (kein Netz).
    suspend fun abfragen(api: MarketApi): Abfrage? {
        val a = api.anfrage(QUELLE, mapOf("Accept" to "application/vnd.github+json"), max = 200_000)
        if (a.code == 0) return null
        return Abfrage(if (a.ok) lesen(a.text.orEmpty()) else null)
    }
}
