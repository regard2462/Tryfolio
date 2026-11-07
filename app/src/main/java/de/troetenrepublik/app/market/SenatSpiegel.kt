package de.troetenrepublik.app.market

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Neuere Senatsmeldungen ueber einen offenen Spiegel auf GitHub; die App bringt einen Stand mit
// (assets/copytrading/senat.json). Das Projekt Congress Trading Monitor (github.com/kadoa-org/congress-trading-monitor,
// MIT-Lizenz) liest dieselben oeffentlichen Meldungen (Periodic Transaction Reports) taeglich aus und legt die
// juengsten 5000 Geschaefte aller Kammern als JSON ab.
// Die App nimmt daraus nur die Senatsmeldungen, die im mitgebrachten Stand fehlen, und bildet sie auf
// Politik.SenatMeldung ab. Kennung einer Meldung ist wie bei eFD die Kennung des Berichts, doppelte fallen weg.
// Die Datei wird nur zur Laufzeit geladen; die Renditen und Kurse des Projekts nutzt die App nicht.
object SenatSpiegel {

    const val ADRESSE = "https://raw.githubusercontent.com/kadoa-org/congress-trading-monitor/main/public/data/trades.json"
    const val SEITE = "https://github.com/kadoa-org/congress-trading-monitor"

    // Groesste Datei, die die App liest (heute gut 4 MB, gepackt etwa 300 kB).
    const val MAX_BYTES = 24_000_000

    @Serializable
    data class Zeile(
        val id: String = "",
        @SerialName("source_id") val quelle: String? = null,
        val chamber: String? = null,
        @SerialName("transaction_date") val datum: String? = null,
        @SerialName("filing_date") val eingang: String? = null,
        val owner: String? = null,
        val ticker: String? = null,
        @SerialName("asset_name") val name: String? = null,
        @SerialName("asset_type") val typ: String? = null,
        @SerialName("transaction_type") val art: String? = null,
        @SerialName("amount_range_low") val von: Long? = null,
        @SerialName("amount_range_high") val bis: Long? = null,
        @SerialName("amount_range_label") val spanne: String? = null,
        val comment: String? = null,
        @SerialName("filer_id") val personId: String? = null,
        @SerialName("filer_name") val personName: String? = null,
        @SerialName("doc_url") val dokument: String? = null,
    )

    // Gespeicherter Stand auf dem Geraet: Zeit des Abrufs und die neuen Meldungen.
    @Serializable
    data class Stand(val am: Long, val meldungen: List<Politik.SenatMeldung> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun standLesen(text: String): Stand? = runCatching { json.decodeFromString<Stand>(text) }.getOrNull()
    fun standText(s: Stand): String = json.encodeToString(Stand.serializer(), s)

    private val KENNUNG = Regex("""/ptr/([0-9a-fA-F-]{36})/""")

    // Kennung des Berichts: aus der Adresse des Dokuments, sonst aus der Zeilenkennung („senate_<kennung>_t0“).
    fun bericht(z: Zeile): String? =
        z.dokument?.let { KENNUNG.find(it)?.groupValues?.get(1) }
            ?: z.id.removePrefix("senate_").substringBeforeLast("_t").takeIf { it.length == 36 }

    private fun normal(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    // Nachname ohne Namenszusatz („Justice, II“ wird „Justice“).
    private fun ohneZusatz(n: String) = n.substringBefore(',').trim()

    // Geschaefte im Format von Politik.G: Inhaber, Art und Spanne wie im Werkzeug tools/senat/holen.py.
    private fun geschaeft(z: Zeile): Politik.G? {
        val a = when (z.art?.trim()?.lowercase()) {
            "purchase" -> "K"
            "sale (full)", "sale" -> "V"
            "sale (partial)" -> "T"
            "exchange" -> "X"
            else -> return null
        }
        val i = when (z.owner?.trim()?.lowercase()) {
            "spouse" -> "SP"
            "joint" -> "JT"
            "child", "dependent child" -> "DC"
            else -> ""
        }
        val datum = z.datum?.trim()?.takeIf { Politik.tag(it) > 0 } ?: return null
        val von = z.von?.takeIf { it > 0 } ?: return null
        val offen = z.spanne?.contains("over", true) == true || z.bis == null || z.bis <= von
        return Politik.G(
            d = datum, i = i, k = Politik.kuerzel(z.ticker), n = (z.name ?: "").replace(Regex("\\s+"), " ").trim(),
            t = z.typ?.trim().orEmpty().ifBlank { "Other" }, a = a, von = von, bis = if (offen) null else z.bis, c = z.comment?.trim()?.ifBlank { null },
        )
    }

    // Neue Senatsmeldungen aus der Datei des Spiegels, die in bekannt fehlen. Personen werden ueber die Kennung des
    // Spiegels („senate_vorname_nachname“) oder den vollen Namen dem mitgebrachten Stand zugeordnet, damit Glocke,
    // Seite und Rangliste dieselbe Person sehen; Unbekannte bekommen Vor- und Nachname aus dem Namen. Null, wenn die
    // Datei nicht lesbar ist.
    fun neue(text: String, bekannt: List<Politik.SenatMeldung>): List<Politik.SenatMeldung>? {
        val zeilen = runCatching { json.decodeFromString<List<Zeile>>(text) }.getOrNull() ?: return null
        val ids = bekannt.map { it.id }.toSet()
        // Juengste Schreibweise je Person zuerst, damit Gross- und Kleinschreibung der neuesten Meldung gilt
        val sortiert = bekannt.sortedByDescending { Politik.tag(it.eingang) }
        val nachKennung = HashMap<String, Politik.SenatMeldung>()
        val nachName = HashMap<String, Politik.SenatMeldung>()
        for (m in sortiert) {
            nachKennung.putIfAbsent(normal(m.vorname) + "_" + normal(m.nachname), m)
            nachName.putIfAbsent(normal(m.vorname + ohneZusatz(m.nachname)), m)
        }
        return zeilen.asSequence()
            .filter { it.chamber.equals("senate", true) || it.quelle?.startsWith("senate", true) == true }
            .mapNotNull { z -> bericht(z)?.let { it to z } }
            .filter { it.first !in ids }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (id, liste) ->
                val erste = liste.first()
                val eingang = erste.eingang?.trim()?.takeIf { Politik.tag(it) > 0 } ?: return@mapNotNull null
                val pid = erste.personId?.removePrefix("senate_").orEmpty()
                val name = erste.personName?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                val vorbild = nachKennung[pid] ?: nachName[normal(name)]
                val (vorname, nachname) = when {
                    vorbild != null -> vorbild.vorname to vorbild.nachname
                    name.contains(' ') -> name.substringBeforeLast(' ') to name.substringAfterLast(' ')
                    name.isNotBlank() -> "" to name
                    else -> return@mapNotNull null
                }
                val g = liste.mapNotNull(::geschaeft)
                if (g.isEmpty()) return@mapNotNull null
                Politik.SenatMeldung(id, vorname, nachname, "", eingang, "Periodic Transaction Report", papier = false, geschaefte = g)
            }
            .sortedByDescending { Politik.tag(it.eingang) }
    }

    // Mitgebrachter Stand und neue Meldungen zusammen, neueste zuerst; Stand ist die juengste Meldung.
    fun zusammen(basis: Politik.SenatDatei?, neu: List<Politik.SenatMeldung>): Politik.SenatDatei? {
        if (neu.isEmpty()) return basis
        val alt = basis?.meldungen.orEmpty()
        val ids = alt.map { it.id }.toSet()
        val alle = (alt + neu.filter { it.id !in ids }).sortedByDescending { Politik.tag(it.eingang) }
        val stand = (listOfNotNull(basis?.stand) + neu.map { it.eingang }).maxByOrNull { Politik.tag(it) } ?: ""
        return (basis ?: Politik.SenatDatei(stand)).copy(stand = stand, meldungen = alle)
    }
}
