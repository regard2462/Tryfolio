package de.troetenrepublik.app.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// Laedt die Daten fuer Copytrading: das Jahresverzeichnis des US-Repraesentantenhauses, die einzelnen
// Transaktionsmeldungen (unveraenderlich, daher einmal gelesen und als Geschaefte auf der Platte gemerkt) und fuer
// die Investoren die Liste der SEC-Meldungen. Die PDFs selbst werden nicht gespeichert und nie gezeigt.
class CopyRepository(private val api: MarketApi, private val ordner: File) {

    data class Stand(
        val meldungen: List<Kongress.Meldung> = emptyList(),
        val geladenAm: Long = 0L,
        val laedt: Boolean = false,
        val fehler: Boolean = false,
    )

    private val _stand = MutableStateFlow(Stand())
    val stand: StateFlow<Stand> = _stand.asStateFlow()

    private val lock = Mutex()
    private val gleichzeitig = Semaphore(3)
    private val geschaefte = ConcurrentHashMap<String, List<Kongress.Geschaeft>>()
    private val json = Json { ignoreUnknownKeys = true }

    // Ein Geschaeft auf der Platte (die Meldungen aendern sich nicht mehr).
    @Serializable
    private data class G(val i: String, val w: String, val k: String? = null, val t: String, val a: String, val d: Long, val g: Long, val v: Long, val b: Long? = null, val bs: String = "")

    private fun Kongress.Geschaeft.g() = G(inhaber, wert, kuerzel, typ, art.name, datum, gemeldet, von, bis, beschreibung)
    private fun G.geschaeft() = Kongress.Geschaeft(i, w, k, t, Kongress.Art.valueOf(a), d, g, v, b, bs)

    private val meldungsOrdner get() = File(ordner, "kongress").apply { mkdirs() }

    // Gemerkte Geschaefte tragen die Fassung des Lesers im Dateinamen. Dateien aelterer Fassungen werden einmal
    // geloescht und die Meldungen neu gelesen.
    private val meldungsDateien: File by lazy {
        meldungsOrdner.also { o -> o.listFiles()?.filter { it.name.endsWith(".json") && !it.name.endsWith(LESER) }?.forEach { it.delete() } }
    }

    // Verzeichnis dieses und des letzten Jahres laden (fuer zwoelf Monate Rueckblick). neu fragt auch dann neu ab,
    // wenn der letzte Abruf weniger als sechs Stunden her ist.
    suspend fun verzeichnis(neu: Boolean = false, jetzt: Long = System.currentTimeMillis()) = lock.withLock {
        val alt = _stand.value
        if (!neu && alt.geladenAm > 0 && jetzt - alt.geladenAm < VERZEICHNIS_MS) return@withLock
        _stand.value = alt.copy(laedt = true)
        val jahr = java.time.Instant.ofEpochMilli(jetzt).atZone(java.time.ZoneOffset.UTC).year
        val listen = coroutineScope { listOf(jahr, jahr - 1).map { j -> async { jahrLaden(j, jetzt) } }.awaitAll() }
        val alle = listen.filterNotNull().flatten().distinctBy { it.docId }.sortedByDescending { it.datum }
        _stand.value = if (alle.isEmpty()) alt.copy(laedt = false, fehler = true)
        else Stand(alle, jetzt, laedt = false, fehler = listen.first() == null)
    }

    // Ein Jahresverzeichnis: aus dem Netz, sonst die zuletzt geladene Fassung auf der Platte.
    private suspend fun jahrLaden(jahr: Int, jetzt: Long): List<Kongress.Meldung>? = withContext(Dispatchers.IO) {
        val datei = File(ordner, "verzeichnis-$jahr.xml")
        // Vergangene Jahre aendern sich kaum noch: einmal am Tag genuegt
        val frisch = datei.exists() && jetzt - datei.lastModified() < (if (jahr < java.time.Year.now().value) 86_400_000L else VERZEICHNIS_MS)
        val xml = (if (frisch) null else api.datei(Kongress.indexAdresse(jahr))?.let { Kongress.xmlAusZip(it) })
            ?.also { runCatching { datei.writeText(it) } }
            ?: runCatching { datei.readText() }.getOrNull()
        xml?.let { Kongress.verzeichnis(it) }
    }

    // Ausgelesene Papiermeldungen, mitgeliefert; leer, wenn die Datei fehlt.
    val papier: Papiermeldungen.Stand by lazy {
        runCatching { Papiermeldungen.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/papier.json").decodeToString()) }.getOrNull()
            ?: Papiermeldungen.Stand("", emptyMap())
    }

    // Mitgelieferte Geschaefte aller Meldungen seit 2024 (copytrading/haus.json), von AppState gesetzt.
    var mitgeliefert: ((String) -> List<Kongress.Geschaeft>?)? = null

    private suspend fun ausLieferung(docId: String): List<Kongress.Geschaeft>? =
        mitgeliefert?.let { f -> withContext(Dispatchers.Default) { runCatching { f(docId) }.getOrNull() } }?.also { geschaefte[docId] = it }

    // true, wenn die Geschaefte der Meldung als Text vorliegen (elektronisch oder ausgelesene Papiermeldung).
    fun lesbar(m: Kongress.Meldung): Boolean = m.elektronisch || papier.enthaelt(m.docId)

    // Geschaefte einer Meldung; Papiermeldungen aus der mitgelieferten Auswertung (leer, wenn sie dort fehlen), null,
    // wenn eine elektronische Meldung nicht geladen werden konnte.
    suspend fun geschaefte(m: Kongress.Meldung): List<Kongress.Geschaeft>? {
        geschaefte[m.docId]?.let { return it }
        ausLieferung(m.docId)?.let { return it }
        if (!m.elektronisch) return papier.geschaefte(m.docId).orEmpty()
        val datei = File(meldungsDateien, "${m.docId}$LESER")
        withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(ListSerializer(G.serializer()), datei.readText()).map { it.geschaeft() } }.getOrNull()
        }?.let { geschaefte[m.docId] = it; return it }
        val pdf = gleichzeitig.withPermit { api.datei(Kongress.meldungAdresse(m.jahr, m.docId)) } ?: return null
        val liste = withContext(Dispatchers.Default) { Kongress.geschaefte(PdfText.zeilen(pdf)) }
        geschaefte[m.docId] = liste
        withContext(Dispatchers.IO) {
            runCatching { datei.writeText(json.encodeToString(ListSerializer(G.serializer()), liste.map { it.g() })) }
        }
        return liste
    }

    // Geschaefte einer Meldung nur aus dem Speicher oder von der Platte, ohne Netz; null, wenn noch nie gelesen.
    suspend fun gemerkt(m: Kongress.Meldung): List<Kongress.Geschaeft>? {
        geschaefte[m.docId]?.let { return it }
        ausLieferung(m.docId)?.let { return it }
        if (!m.elektronisch) return papier.geschaefte(m.docId).orEmpty()
        val datei = File(meldungsDateien, "${m.docId}$LESER")
        return withContext(Dispatchers.IO) {
            if (!datei.exists()) null
            else runCatching { json.decodeFromString(ListSerializer(G.serializer()), datei.readText()).map { it.geschaeft() } }.getOrNull()
        }?.also { geschaefte[m.docId] = it }
    }

    // Geschaefte mehrerer Meldungen, je Meldung mit der Meldung selbst; Papiermeldungen ohne Geschaefte.
    suspend fun geschaefte(meldungen: List<Kongress.Meldung>): List<Pair<Kongress.Meldung, List<Kongress.Geschaeft>?>> = coroutineScope {
        meldungen.map { m -> async { m to geschaefte(m) } }.awaitAll()
    }

    // Meldungen einer Person der letzten tage Tage.
    fun meldungenVon(schluessel: String, jetzt: Long = System.currentTimeMillis(), tage: Int = 365): List<Kongress.Meldung> =
        _stand.value.meldungen.filter { it.person == schluessel && it.datum >= jetzt - tage * 86_400_000L }

    // Truth-Social-Beitraege des Praesidenten aus dem Archiv „Trump's Truth“: die neuesten 100, mit bis
    // (JJJJ-MM-TT) aeltere. Null ohne Antwort; fuenf Minuten im Speicher.
    suspend fun truth(bis: String? = null): List<TruthArchiv.Beitrag>? {
        val adresse = TruthArchiv.adresse(100, bis)
        truthCache[adresse]?.takeIf { System.currentTimeMillis() - it.first < 5 * 60_000L }?.let { return it.second }
        val liste = api.feedBytes(adresse)?.let { TruthArchiv.lesen(Rss.text(it)) } ?: return null
        truthCache[adresse] = System.currentTimeMillis() to liste
        return liste
    }

    private val truthCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<TruthArchiv.Beitrag>>>()

    // Zahl der Meldungen einer Person (Nachname) auf whitehouse.gov; null ohne Antwort.
    suspend fun weissesHausMeldungen(name: String): Int? =
        api.datei(Politik.WEISSES_HAUS, 4_000_000)?.toString(Charsets.UTF_8)?.let { Politik.weissesHausMeldungen(it, name) }

    // Meldungen einer Person bei der OGE; null, wenn die OGE nicht antwortet.
    suspend fun ogeMeldungen(nachname: String, vorname: String): List<Politik.OgeMeldung>? =
        api.datei(Politik.ogeAdresse(nachname), 2_000_000)?.toString(Charsets.UTF_8)?.let { Politik.ogeMeldungen(it, nachname, vorname) }

    // Investoren

    // Stichtag und Meldetag der neuesten 13F-HR bei der SEC; null, wenn die SEC nicht antwortet.
    suspend fun secNeueste(cik: String): Pair<String, String>? =
        api.datei(Investoren.secAdresse(cik), 4_000_000)?.let { Investoren.neuesteMeldung(it.toString(Charsets.UTF_8)) }

    companion object {
        // Verzeichnis hoechstens alle sechs Stunden neu (der Clerk aktualisiert es einmal am Tag).
        const val VERZEICHNIS_MS = 6 * 3_600_000L

        // Endung der gemerkten Geschaefte je Fassung des Lesers.
        private const val LESER = ".l3.json"
    }
}
