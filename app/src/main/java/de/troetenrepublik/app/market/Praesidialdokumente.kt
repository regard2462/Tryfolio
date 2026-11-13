package de.troetenrepublik.app.market

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Praesidialdokumente aus dem Federal Register [0.77]: Executive Orders, Proklamationen und Memoranden der letzten zwoelf
// Monate (offene Schnittstelle ohne Schluessel, ein Abruf mit allen Eintraegen). Die Schnittstelle liefert bei
// Praesidialdokumenten nur den Titel (Kurzfassung und Themen sind leer), deshalb ergibt sich ein Bezug zu Wertpapieren
// nur aus dem Titel: eine Firma aus dem Katalog mit mindestens zwei Namensteilen oder ein Erzeugnis, das eine feste
// Liste einer Branche zuordnet. Ohne sicheren Bezug steht das Dokument nur in der allgemeinen Liste.
object Praesidialdokumente {
    enum class Art(val code: String) { ERLASS("executive_order"), PROKLAMATION("proclamation"), MEMORANDUM("memorandum") }

    // tag: Tag der Unterzeichnung (JJJJ-MM-TT), sonst der Veroeffentlichung.
    data class Dokument(val nummer: String, val tag: String, val art: Art, val titel: String, val adresse: String, val kurzfassung: String = "")

    fun adresse(ab: String): String =
        "https://www.federalregister.gov/api/v1/documents.json?conditions[type][]=PRESDOCU" +
            Art.entries.joinToString("") { "&conditions[presidential_document_type][]=" + it.code } +
            "&conditions[publication_date][gte]=$ab&per_page=1000&order=newest" +
            listOf("title", "document_number", "publication_date", "signing_date", "subtype", "html_url", "abstract").joinToString("") { "&fields[]=$it" }

    private val json = Json { ignoreUnknownKeys = true }

    private fun art(subtype: String): Art? = when (subtype) {
        "Executive Order" -> Art.ERLASS
        "Proclamation" -> Art.PROKLAMATION
        "Memorandum" -> Art.MEMORANDUM
        else -> null
    }

    // Neueste zuerst; Eintraege ohne Titel, Art oder Adresse entfallen. Nur Adressen des Federal Register zaehlen.
    fun lesen(text: String): List<Dokument> = runCatching {
        json.parseToJsonElement(text).jsonObject["results"]!!.jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
            val a = art(s("subtype")) ?: return@mapNotNull null
            val adr = s("html_url").takeIf { it.startsWith("https://www.federalregister.gov/") } ?: return@mapNotNull null
            val titel = s("title").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Dokument(s("document_number"), s("signing_date").ifBlank { s("publication_date") }, a, titel, adr, s("abstract").trim())
        }.sortedWith(compareByDescending<Dokument> { it.tag }.thenByDescending { it.nummer })
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------ Zuordnung

    // Erzeugnis im Titel, die betroffene Branche nach Yahoo-Branche (industrie) und, wo die Kategorie der App genau
    // dasselbe meint, nach Katalogkategorie (codes). Bergbau und Maschinen sind in der App zu grob und fehlen bewusst.
    class Thema(val id: String, val titel: Regex, val industrie: Regex, val codes: Set<String> = emptySet())

    private fun re(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    val THEMEN: List<Thema> = listOf(
        Thema("STAHL", re("\\bsteel\\b"), re("^steel$")),
        Thema("ALUMINIUM", re("\\baluminum\\b|\\baluminium\\b"), re("^aluminum$")),
        Thema("KUPFER", re("\\bcopper\\b"), re("^copper$")),
        Thema("HALBLEITER", re("\\bsemiconductors?\\b"), re("semiconductor"), setOf("HALBLEITER")),
        Thema("PHARMA", re("\\bpharmaceuticals?\\b"), re("^drug manufacturers"), setOf("PHARMA")),
        Thema("FLUGZEUGE", re("\\bcommercial aircraft\\b|\\bjet engines?\\b"), re("^aerospace & defense$"), setOf("LUFTFAHRT")),
        Thema("DROHNEN", re("\\bunmanned aircraft\\b"), re("^aerospace & defense$"), setOf("LUFTFAHRT")),
        Thema("SOLAR", re("\\bpolysilicon\\b"), re("^solar$")),
        Thema("HOLZ", re("\\btimber\\b|\\blumber\\b"), re("^lumber & wood production$"), setOf("FORST")),
        Thema("DUENGER", re("\\bfertilizers?\\b|\\bphosphate\\b|\\bglyphosate\\b"), re("^agricultural inputs$")),
        Thema("KOHLE", re("\\bcoal\\b"), re("^(thermal|coking) coal$")),
        Thema("OEL_GAS", re("\\boil\\b"), re("^oil & gas"), setOf("OEL_GAS")),
    )

    // Was im Titel (und in der Kurzfassung, falls vorhanden) genannt ist.
    fun themen(d: Dokument): List<Thema> {
        val text = d.titel + " " + d.kurzfassung
        return THEMEN.filter { it.titel.containsMatchIn(text) }
    }

    // Kleinbuchstaben, nur Buchstaben und Ziffern, Woerter einzeln, mit Leerzeichen an beiden Enden.
    private fun norm(s: String): String = " " + s.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]+"), " ").trim() + " "

    // Firmen aus namen, deren Name mit mindestens zwei Woertern im Titel vorkommt. Einzelwort-Namen („Visa“, „Next“)
    // treffen auch gewoehnliche Woerter und zaehlen nicht.
    fun firmen(d: Dokument, namen: Collection<String>): List<String> {
        val text = norm(d.titel + " " + d.kurzfassung)
        return namen.filter { n ->
            val k = norm(n)
            k.trim().split(' ').size >= 2 && k.trim().length >= 8 && text.contains(k)
        }.distinct()
    }

    // Bezug eines Dokuments zu einem Wert: Firma im Titel oder Branche der Aktie laut Themen.
    class Bezug(val firma: Boolean, val themen: List<Thema>)

    fun bezug(d: Dokument, name: String, industrie: String, katalogCode: String): Bezug? {
        val firma = firmen(d, listOf(name)).isNotEmpty()
        val t = themen(d).filter { th ->
            if (industrie.isNotBlank()) th.industrie.containsMatchIn(industrie.trim()) else katalogCode in th.codes
        }
        return if (firma || t.isNotEmpty()) Bezug(firma, t) else null
    }

    // Dokumente der letzten zwoelf Monate (ab ist der erste gueltige Tag) mit Bezug zum Wert, neueste zuerst.
    fun fuerWert(liste: List<Dokument>, ab: String, name: String, industrie: String, katalogCode: String): List<Pair<Dokument, Bezug>> =
        liste.filter { it.tag >= ab }.mapNotNull { d -> bezug(d, name, industrie, katalogCode)?.let { d to it } }

    // ------------------------------------------------------------ Abruf

    private val api by lazy { MarketApi() }
    private val sperre = Mutex()
    @Volatile private var stand: Pair<Long, List<Dokument>>? = null
    private const val GUELTIG_MS = 6 * 3_600_000L

    // Dokumente ab einem Tag (JJJJ-MM-TT), sechs Stunden im Speicher; null bei Netzfehlern.
    suspend fun laden(ab: String, jetzt: Long = System.currentTimeMillis()): List<Dokument>? = sperre.withLock {
        stand?.takeIf { jetzt - it.first < GUELTIG_MS }?.let { return@withLock it.second }
        val a = runCatching { api.anfrage(adresse(ab), max = 3_000_000) }.getOrNull()
        if (a == null || !a.ok) return@withLock null
        val liste = lesen(a.text.orEmpty())
        if (liste.isEmpty()) return@withLock null
        stand = jetzt to liste
        liste
    }
}
