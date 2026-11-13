package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Gespeicherter Stand der Rangliste (seit 5.92): Die Rechnung braucht Kurse von ueber tausend Aktien und dauert; die
// Seite zeigt deshalb sofort den zuletzt gerechneten Stand und rechnet nur im Hintergrund neu, wenn er veraltet ist.
object RanglisteSpeicher {

    // Nach so vielen Stunden rechnet die Seite neu.
    const val STUNDEN = 6

    @Serializable
    private data class Zeile(val p: String, val n: String, val g: String, val k: Int, val v: Double, val q: Double, val u: Double,
        val z: Int = -1, val s: Double = -1.0, val a: Int = 0, val w: Double = 0.0)

    @Serializable
    private data class Datei(val format: Int = 3, val am: Long, val raenge: List<Zeile>)

    private val json = Json { ignoreUnknownKeys = true }

    fun schreiben(am: Long, raenge: List<KongressAuswertung.Rang>): String =
        json.encodeToString(Datei.serializer(), Datei(3, am, raenge.map { Zeile(it.person, it.name, it.gruppe.toString(), it.kaeufe, it.vorsprung, it.quote, it.volumen, it.verzug ?: -1, it.spaet ?: -1.0, it.geschaefte90, it.volumen90) }))

    // Zeitpunkt der Rechnung und Raenge; null bei fremdem Format oder kaputter Datei.
    fun lesen(text: String): Pair<Long, List<KongressAuswertung.Rang>>? = runCatching {
        val d = json.decodeFromString(Datei.serializer(), text)
        if (d.format != 3) return null
        d.am to d.raenge.map { KongressAuswertung.Rang(it.p, it.n, it.g.firstOrNull() ?: 'H', it.k, it.v, it.q, it.u, it.z.takeIf { z -> z >= 0 }, it.s.takeIf { x -> x >= 0 }, it.a, it.w) }
    }.getOrNull()

    fun veraltet(am: Long, jetzt: Long): Boolean = jetzt - am > STUNDEN * 3_600_000L || am > jetzt + 60_000L
}
