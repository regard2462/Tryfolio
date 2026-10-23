package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Alle Transaktionsmeldungen des Repraesentantenhauses seit 2024 als mitgelieferter Stand (copytrading/haus.json).
// desktop/src/test/.../HausSammlung liest dafuer die Jahresverzeichnisse und PDFs des Clerk mit demselben Leser wie
// die App. Papiermeldungen stehen nur mit ihren Angaben zur Person darin, ihre Geschaefte liegen in papier.json.
// Grundlage fuer die Strategie „Kongress kauft“ und die Rangliste; neuere Meldungen liest die App selbst.
object HausDaten {

    @Serializable
    data class M(
        val id: String,
        val n: String,
        val v: String,
        val w: String,
        // Tag der Einreichung JJJJ-MM-TT.
        val d: String,
        val j: Int,
        val p: Boolean = false,
        val g: List<Papiermeldungen.G> = emptyList(),
    )

    @Serializable
    data class Datei(val format: Int, val stand: String = "", val meldungen: List<M> = emptyList())

    data class Stand(val stand: String, val meldungen: List<Kongress.Meldung>, val geschaefte: Map<String, List<Kongress.Geschaeft>>)

    private val json = Json { ignoreUnknownKeys = true }

    fun schreiben(d: Datei): String = Json.encodeToString(Datei.serializer(), d)

    fun g(x: Kongress.Geschaeft): Papiermeldungen.G = Papiermeldungen.G(
        x.inhaber, x.wert, x.kuerzel, x.typ, x.art.name, iso(x.datum), x.gemeldet.takeIf { it > 0 }?.let { iso(it) }, x.von, x.bis,
    )

    fun iso(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()

    // null bei unbekanntem Format. Geschaefte ohne gueltigen Tag oder Art fallen weg.
    fun lesen(text: String): Stand? {
        val d = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull() ?: return null
        if (d.format != 1) return null
        val meldungen = d.meldungen.mapNotNull { m ->
            val tag = Politik.tag(m.d).takeIf { it > 0 } ?: return@mapNotNull null
            Kongress.Meldung(m.id, m.n, m.v, "", m.w, tag, m.j)
        }
        val geschaefte = d.meldungen.filter { !it.p }.associate { m ->
            m.id to m.g.mapNotNull { g ->
                val art = runCatching { Kongress.Art.valueOf(g.a) }.getOrNull() ?: return@mapNotNull null
                val tag = Politik.tag(g.d).takeIf { it > 0 } ?: return@mapNotNull null
                Kongress.Geschaeft(g.i, g.w, g.k?.takeIf { it.isNotBlank() }, g.t, art, tag, g.g?.let { Politik.tag(it) } ?: 0L, g.v, g.b)
            }
        }
        return Stand(d.stand, meldungen.sortedByDescending { it.datum }, geschaefte)
    }
}
