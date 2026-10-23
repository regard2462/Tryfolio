package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Papiermeldungen des Repraesentantenhauses: eingescannte Meldungen ohne Textebene. tools/kongress-papier liest sie
// beim Bau per Texterkennung aus, das Ergebnis liegt unter copytrading/papier.json. Je Meldung (DocID) die Geschaefte
// wie bei elektronischen Meldungen und die Zahl der Zeilen, die sich nicht sicher lesen liessen.
object Papiermeldungen {

    @Serializable
    data class G(val i: String = "", val w: String, val k: String? = null, val t: String = "OT", val a: String, val d: String, val g: String? = null, val v: Long, val b: Long? = null)

    @Serializable
    data class Meldung(val seiten: Int = 0, val unlesbar: Int = 0, val grund: String? = null, val geschaefte: List<G> = emptyList())

    @Serializable
    data class Datei(val format: Int, val stand: String = "", val meldungen: Map<String, Meldung> = emptyMap())

    data class Stand(val stand: String, val meldungen: Map<String, Pair<List<Kongress.Geschaeft>, Int>>) {
        fun geschaefte(docId: String): List<Kongress.Geschaeft>? = meldungen[docId]?.first
        fun unlesbar(docId: String): Int = meldungen[docId]?.second ?: 0
        fun enthaelt(docId: String): Boolean = docId in meldungen
    }

    private val json = Json { ignoreUnknownKeys = true }

    // null bei unbekanntem Format. Geschaefte ohne gueltigen Tag oder Art fallen weg.
    fun lesen(text: String): Stand? {
        val d = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull() ?: return null
        if (d.format != 1) return null
        return Stand(d.stand, d.meldungen.mapValues { (_, m) ->
            m.geschaefte.mapNotNull { g ->
                val art = runCatching { Kongress.Art.valueOf(g.a) }.getOrNull() ?: return@mapNotNull null
                val tag = Politik.tag(g.d).takeIf { it > 0 } ?: return@mapNotNull null
                Kongress.Geschaeft(g.i, g.w, g.k?.takeIf { it.isNotBlank() }, g.t, art, tag, g.g?.let { Politik.tag(it) } ?: 0L, g.v, g.b)
            } to m.unlesbar
        })
    }
}
