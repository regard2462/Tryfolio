package de.troetenrepublik.app

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.market.AbrufAblage
import de.troetenrepublik.app.market.AchtKAbruf
import de.troetenrepublik.app.market.FirmaGeschichte
import de.troetenrepublik.app.market.FirmaStandorte
import de.troetenrepublik.app.market.FirmaWikidata
import de.troetenrepublik.app.market.Gleif
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.SecBericht
import de.troetenrepublik.app.market.SecFormen
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

// Anbindung der Bausteine „Standorte“ und „Geschichte“ der Wertpapierseite an Netz, Einstellungen und Speicher. Je Wert
// liegt eine kleine Datei auf dem Geraet (30 Tage gueltig, danach wird im Hintergrund erneuert). Abgerufen wird erst, wenn
// der Hintergrund der Seite aufgeklappt ist. SEC nur mit Kontaktadresse unter Zugaenge, hoechstens zwei Abrufe je
// Sekunde; GLEIF und Wikidata ohne Schluessel.

private val firmaApi by lazy { MarketApi() }
private val firmaSperre = Mutex()
private const val FIRMA_GUELTIG_MS = 30 * 86_400_000L
private const val SEC_PAUSE_MS = 500L
private const val GLEIF_SEITEN = 8
private const val TOCHTER_WIKIDATA = 80
private const val ITEM2_MAX = 6_000_000
private const val ITEM2_SCHRITT = 393_216

private fun AppState.firmaDatei(art: String, symbol: String, endung: String = ""): SichereDatei {
    val ordner = File(store.ordner, "firma-$art").apply { mkdirs() }
    return SichereDatei(File(ordner, symbol.replace(Regex("[^A-Za-z0-9]"), "_") + endung + ".json"))
}

private fun AppState.deutsch() = settingsFlow.value.sprache == Lang.DE

private suspend fun AppState.firmaAufloeser(): FirmaStandorte.Aufloeser {
    val karte = runCatching { globusKarte() }.getOrNull()
    return FirmaStandorte.Aufloeser(karte?.laender.orEmpty().map { FirmaStandorte.Land(it.a2, it.en, it.de) })
}

// CIK des Werts: aus dem gemerkten 8-K-Ergebnis, den Insidermeldungen, sonst aus der Kuerzelliste der SEC
private suspend fun AppState.firmaCik(symbol: String, name: String, sek: suspend (String) -> String?): String? {
    AchtKAbruf.gemerkt(symbol)?.cik?.takeIf { it.isNotBlank() }?.let { return it }
    sec.value?.geschaefte?.firstOrNull { it.k.equals(symbol, true) }?.c?.trimStart('0')?.takeIf { it.isNotBlank() }?.let { return it }
    val liste = sek(SecFormen.KUERZEL) ?: return null
    return SecBericht.cikAusListe(liste, symbol, name)
}

// Wikidata: Land je Eintrag (Alpha-2). Ein Eintrag hat sein Land entweder selbst (P297), ueber P17, P495 oder ueber den Sitz
// (P159, ein Ort mit P17); aufgeloest wird in hoechstens drei Runden.
private suspend fun wikidataLaender(ids: Collection<String>): Map<String, String> {
    val code = HashMap<String, String>()
    val verweis = HashMap<String, String>()
    val geholt = HashSet<String>()
    var offen: Set<String> = ids.toSet()
    repeat(3) {
        val neu = offen.filter { geholt.add(it) }
        if (neu.isEmpty()) return@repeat
        val naechste = HashSet<String>()
        for (teil in neu.chunked(40)) {
            val text = runCatching { firmaApi.anfrage(FirmaWikidata.eintraegeAdresse(teil, "claims"), max = 12_000_000).takeIf { it.ok }?.text }.getOrNull() ?: continue
            val entities = runCatching { Json.parseToJsonElement(text).jsonObject["entities"]?.jsonObject }.getOrNull() ?: continue
            for ((id, e) in entities) {
                val claims = runCatching { e.jsonObject["claims"]?.jsonObject }.getOrNull() ?: continue
                val eigen = FirmaGeschichte.aussagen(claims, "P297").firstNotNullOfOrNull { it.text }?.takeIf { it.length == 2 }
                if (eigen != null) { code[id] = eigen.uppercase(); continue }
                val ref = listOf("P17", "P495", "P159").firstNotNullOfOrNull { p -> FirmaGeschichte.aussagen(claims, p).firstNotNullOfOrNull { it.id } }
                if (ref != null) { verweis[id] = ref; naechste += ref }
            }
            delay(100)
        }
        offen = naechste
    }
    fun auf(id: String): String? {
        var x = id
        repeat(4) { code[x]?.let { return it }; x = verweis[x] ?: return null }
        return code[x]
    }
    return ids.mapNotNull { id -> auf(id)?.let { id to it } }.toMap()
}

// Streift den Jahresbericht (HTML) und bricht ab, sobald Item 2 vollstaendig gelesen ist
private fun item2Lesen(ein: java.io.InputStream): String? {
    val sb = StringBuilder()
    val puffer = ByteArray(32_768)
    var naechste = ITEM2_SCHRITT
    var gesamt = 0
    while (gesamt < ITEM2_MAX) {
        val n = ein.read(puffer)
        if (n < 0) break
        sb.append(String(puffer, 0, n, Charsets.ISO_8859_1))
        gesamt += n
        if (gesamt >= naechste) {
            naechste = gesamt + ITEM2_SCHRITT
            FirmaStandorte.item2Text(sb.toString())?.let { return it }
        }
    }
    return FirmaStandorte.item2Text(sb.toString())
}

// Was ohne Abruf bekannt ist (auch abgelaufen); null ohne gespeicherten Stand
fun AppState.firmaStandorteGespeichert(symbol: String): FirmaStandorte.Daten? =
    runCatching { FirmaStandorte.lesen(firmaDatei("standorte", symbol).lesen().toString(Charsets.UTF_8)) }.getOrNull()

fun firmaStandorteFrisch(d: FirmaStandorte.Daten?): Boolean = d != null && System.currentTimeMillis() - d.abgerufen < FIRMA_GUELTIG_MS

// Standorte laden: Stand von der Platte, sonst SEC (Exhibit 21, Item 2), GLEIF und Wikidata
suspend fun AppState.firmaStandorte(symbol: String, name: String): FirmaStandorte.Daten? = withContext(Dispatchers.IO) {
    val alt = firmaStandorteGespeichert(symbol)
    if (firmaStandorteFrisch(alt)) return@withContext alt
    firmaSperre.withLock {
        firmaStandorteGespeichert(symbol)?.takeIf { firmaStandorteFrisch(it) }?.let { return@withLock it }
        val aufloeser = firmaAufloeser()
        val kennung = SecInsider.kennung(settingsFlow.value.secKontakt)
        var netz = false
        var ex21 = emptyMap<String, Int>()
        var item2 = emptyList<String>()
        var bericht = ""; var berichtTag = ""

        // SEC: Liste der Toechter und Item 2 des juengsten Jahresberichts
        if (kennung != null) {
            val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
            suspend fun sek(url: String): String? {
                if (AbrufAblage.gesperrt("q:sec")) return null
                delay(SEC_PAUSE_MS)
                return runCatching { firmaApi.anfrage(url, kopf, max = 3_000_000).takeIf { it.ok }?.text }.getOrNull()
            }
            val cik = firmaCik(symbol, name) { sek(it) }
            val subs = cik?.let { sek(SecBericht.submissionsAdresse(it)) }
            val jb = subs?.let { SecBericht.juengsterJahresbericht(it) }
            if (cik != null && subs != null && jb != null) {
                netz = true
                bericht = jb.form; berichtTag = jb.eingang
                val index = sek(SecBericht.verzeichnisAdresse(cik, jb.acc))
                val datei = index?.let { FirmaStandorte.listenDatei(it, jb.form) }
                if (datei != null) sek(SecBericht.archivAdresse(cik, jb.acc, datei))?.let { dok ->
                    ex21 = FirmaStandorte.tochterListe(dok, aufloeser).groupingBy { it.a2 }.eachCount()
                }
                val haupt = SecBericht.hauptdokument(subs, jb.acc)
                if (haupt != null && jb.form == "10-K" && !AbrufAblage.gesperrt("q:sec")) {
                    delay(SEC_PAUSE_MS)
                    val text = runCatching { firmaApi.strom(SecBericht.archivAdresse(cik, jb.acc, haupt.datei), kopf + ("Accept" to "text/html, */*")) { item2Lesen(it) } }.getOrNull()
                    if (text != null) item2 = aufloeser.genannt(text).sorted()
                }
            }
        }

        // GLEIF Level 2: Toechter mit Sitzland
        var gleif = emptyMap<String, Int>()
        var gleifGesamt = 0
        val isin = isinFor(symbol)
        if (isin.length == 12) {
            val gk = mapOf("Accept" to "application/vnd.api+json")
            val lei = runCatching { firmaApi.anfrage(Gleif.leiSuche(isin), gk).takeIf { it.ok }?.text }.getOrNull()?.let { Gleif.satzLesen(it)?.lei }
            if (lei != null) {
                netz = true
                val je = HashMap<String, Int>()
                var seite = 1
                var letzte = 1
                while (seite <= minOf(letzte, GLEIF_SEITEN)) {
                    val k = runCatching { firmaApi.anfrage(FirmaStandorte.kinderAdresse(lei, seite), gk, max = 6_000_000).takeIf { it.ok }?.text }.getOrNull()
                        ?.let { FirmaStandorte.kinderLesen(it) } ?: break
                    k.laender.forEach { (l, n) -> je[l] = (je[l] ?: 0) + n }
                    gleifGesamt = k.gesamt; letzte = k.letzteSeite
                    seite++
                    delay(150)
                }
                gleif = je
            }
        }

        // Wikidata: Sitzland und Toechter (P355) mit Land
        var sitz = ""
        var wikidata = emptyMap<String, Int>()
        val wd = runCatching { firmaWikidata(name) }.getOrNull()
        val qid = wd?.kennung?.takeIf { it.isNotBlank() }
        if (qid != null) {
            val text = runCatching { firmaApi.anfrage(FirmaGeschichte.eintragAdresse(qid), max = 12_000_000).takeIf { it.ok }?.text }.getOrNull()
            val claims = text?.let { FirmaGeschichte.anspruch(it, qid) }
            if (claims != null) {
                netz = true
                val hq = FirmaGeschichte.aussagen(claims, "P159").filter { it.ende == null }.firstNotNullOfOrNull { it.id }
                val eigenLand = FirmaGeschichte.aussagen(claims, "P17").firstNotNullOfOrNull { it.id }
                val tochter = FirmaGeschichte.aussagen(claims, "P355").filter { it.ende == null }.mapNotNull { it.id }.distinct().take(TOCHTER_WIKIDATA)
                val land = wikidataLaender(listOfNotNull(hq, eigenLand) + tochter)
                sitz = hq?.let { land[it] } ?: eigenLand?.let { land[it] }.orEmpty()
                wikidata = tochter.mapNotNull { land[it] }.groupingBy { it }.eachCount()
            }
        }

        if (!netz) return@withLock alt
        val neu = FirmaStandorte.Daten(
            System.currentTimeMillis(), sitz, ex21, gleif, wikidata, item2, bericht, berichtTag, gleifGesamt,
        )
        runCatching { firmaDatei("standorte", symbol).schreiben(FirmaStandorte.schreiben(neu).toByteArray(Charsets.UTF_8)) }
        neu
    }
}

fun AppState.firmaGeschichteGespeichert(symbol: String): FirmaGeschichte.Daten? =
    runCatching { FirmaGeschichte.lesen(firmaDatei("geschichte", symbol, if (deutsch()) ".de" else ".en").lesen().toString(Charsets.UTF_8)) }.getOrNull()

// Geschichte laden: Wikidata (Zeitaussagen), fruehere Namen laut SEC und Aktiensplits; die 8-K kommen aus AchtKAbruf
suspend fun AppState.firmaGeschichte(symbol: String, name: String): FirmaGeschichte.Daten? = withContext(Dispatchers.IO) {
    val alt = firmaGeschichteGespeichert(symbol)
    if (alt != null && System.currentTimeMillis() - alt.abgerufen < FIRMA_GUELTIG_MS) return@withContext alt
    firmaSperre.withLock {
        val sprache = if (deutsch()) "de" else "en"
        var netz = false
        val liste = ArrayList<FirmaGeschichte.Ereignis>()
        var secNamen = emptyList<FirmaGeschichte.Ereignis>()
        var kennungWd = ""

        val wd = runCatching { firmaWikidata(name) }.getOrNull()
        val qid = wd?.kennung?.takeIf { it.isNotBlank() }
        if (qid != null) {
            val text = runCatching { firmaApi.anfrage(FirmaGeschichte.eintragAdresse(qid), max = 12_000_000).takeIf { it.ok }?.text }.getOrNull()
            val claims = text?.let { FirmaGeschichte.anspruch(it, qid) }
            if (claims != null) {
                netz = true
                kennungWd = qid
                val ids = FirmaGeschichte.benoetigteKennungen(claims, qid).take(200)
                val namen = HashMap<String, String>()
                for (teil in ids.chunked(50)) {
                    val l = runCatching { firmaApi.anfrage(FirmaWikidata.eintraegeAdresse(teil, "labels", "$sprache|en")).takeIf { it.ok }?.text }.getOrNull() ?: continue
                    namen.putAll(FirmaWikidata.beschriftungen(l, sprache))
                    delay(100)
                }
                liste += FirmaGeschichte.ausWikidata(claims, qid) { namen[it] }
            }
        }

        val kennung = SecInsider.kennung(settingsFlow.value.secKontakt)
        if (kennung != null && !AbrufAblage.gesperrt("q:sec")) {
            val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
            suspend fun sek(url: String): String? {
                if (AbrufAblage.gesperrt("q:sec")) return null
                delay(SEC_PAUSE_MS)
                return runCatching { firmaApi.anfrage(url, kopf, max = 3_000_000).takeIf { it.ok }?.text }.getOrNull()
            }
            val cik = firmaCik(symbol, name) { sek(it) }
            val subs = cik?.let { sek(SecBericht.submissionsAdresse(it)) }
            if (cik != null && subs != null) { netz = true; secNamen = FirmaGeschichte.secName(subs, cik) }
        }

        val splits = runCatching { market.splits(symbol, "max") }.getOrNull()
        if (splits != null) { netz = true; liste += FirmaGeschichte.ausSplits(splits) }

        if (!netz) return@withLock alt
        val neu = FirmaGeschichte.Daten(System.currentTimeMillis(), kennungWd, FirmaGeschichte.zusammenfuehren(liste, secNamen))
        runCatching { firmaDatei("geschichte", symbol, if (deutsch()) ".de" else ".en").schreiben(FirmaGeschichte.schreiben(neu).toByteArray(Charsets.UTF_8)) }
        neu
    }
}
