package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.EuInsider.Geschaeft
import de.troetenrepublik.app.market.InsiderQuellen.Netz
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Insidergeschaefte weiterer europaeischer Laender (seit 5.91): Nasdaq Nordic und Baltikum (Daenemark, Finnland,
// Island, Estland, Lettland, Litauen), Tschechien (ČNB OAM), Luxemburg (LuxSE OAM), Italien (1Info), Zypern (CSE
// Public OAM) und Ungarn (MNB). Die App holt nur die Meldungen der gerade geoeffneten
// Aktie, auf dem Geraet und fuer den eigenen Gebrauch; nichts davon liegt im Programm. Die Meldungen
// sind meist das EU-einheitliche Formular als PDF oder Text, gelesen von MarFormular. Abrufe sind gedrosselt und je
// Oeffnen begrenzt. Von der MNB holt die App nur die Trefferliste; die Meldungen mit ihren PDFs oeffnet die
// Person selbst (mnbMeldungen).
object InsiderEuropa {
    // Laender dieser Quellen und die Yahoo-Endungen ihrer Boersen.
    val LAENDER = listOf("DK", "FI", "IS", "EE", "LV", "LT", "CZ", "LU", "IT", "CY", "HU")

    private val ENDUNG = mapOf(
        ".CO" to "DK", ".HE" to "FI", ".IC" to "IS", ".TL" to "EE", ".RG" to "LV", ".VS" to "LT", ".PR" to "CZ", ".MI" to "IT", ".BD" to "HU",
    )

    // Land und Kuerzel an der Heimatboerse nach der Yahoo-Endung, sonst nach dem Land der ISIN (etwa ArcelorMittal
    // in Amsterdam mit luxemburgischer ISIN).
    fun boerse(symbol: String, isin: String = ""): Pair<String, String>? {
        val s = symbol.uppercase()
        ENDUNG.entries.firstOrNull { s.endsWith(it.key) }?.let { return it.value to s.removeSuffix(it.key) }
        val land = isin.take(2).uppercase()
        if (isin.length == 12 && land in LAENDER) return land to s.substringBefore('.')
        return null
    }

    private const val PAUSE = 400L
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.s(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
    private fun JsonElement.objekte(): List<JsonObject> = runCatching { jsonArray.map { it.jsonObject } }.getOrDefault(emptyList())
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // Abrufe je Oeffnen zaehlen; ist das Kontingent aufgebraucht, holt die App den Rest beim naechsten Oeffnen.
    private class Kontingent(var rest: Int) {
        suspend fun <T> abruf(f: suspend () -> T?): T? {
            if (rest <= 0) return null
            rest--
            val r = f()
            kotlinx.coroutines.delay(PAUSE)
            return r
        }
    }

    // ------------------------------------------------------------ Firmennamen

    private val RECHTSFORM = setOf("oyj", "oy", "abp", "ab", "a/s", "as", "asa", "hf", "plc", "sa", "se", "ag", "nv", "spa", "sia", "uab", "ou", "oü", "aktsiaselts",
        "ltd", "limited", "nyrt", "rt", "zrt", "public", "company", "co", "corporation", "corp", "inc", "holding", "holdings", "group", "grupp", "grupa", "the",
        "akcine", "akcinė", "bendrove", "bendrovė", "nyilvánosan", "működő", "részvénytársaság", "societa", "società", "per", "azioni", "pcl")

    // Kennzeichnende Woerter eines Firmennamens ohne Rechtsform („Elisa Oyj“ und „Elisa“, „Eni S.p.A.“ und „ENI“).
    fun kern(name: String): List<String> = name.lowercase().replace(Regex("[’'`´‘]"), "").replace(Regex("[^\\p{L}\\d/& ]"), " ")
        .split(' ').filter { it.length > 1 && it !in RECHTSFORM }

    fun gleicheFirma(a: String, b: String): Boolean {
        val x = kern(a); val y = kern(b)
        if (x.isEmpty() || y.isEmpty()) return false
        val (k, l) = if (x.size <= y.size) x to y else y to x
        return l.take(k.size) == k || (k.size >= 2 && l.containsAll(k))
    }

    private fun suchwort(firma: String, code: String) = kern(firma).take(2).joinToString(" ").ifBlank { code }

    // ------------------------------------------------------------ Nasdaq Nordic und Baltikum

    // Leser fuer Mitteilungen von Nasdaq Nordic und Baltikum (JSON); der Abruf laeuft ueber NasdaqNordic.kt.

    data class NasdaqMeldung(val id: String, val tag: String, val firma: String, val titel: String, val kategorie: String, val link: String, val pdfs: List<String>,
        val zeit: String = "", val sprache: String = "")

    fun nasdaqLesen(text: String): List<NasdaqMeldung> = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        o["results"]?.jsonObject?.get("item")?.objekte().orEmpty().mapNotNull { m ->
            val link = m.s("messageUrl").ifBlank { return@mapNotNull null }
            val pdfs = m["attachment"]?.objekte().orEmpty().filter { it.s("mimetype").contains("pdf") }.map { it.s("attachmentUrl") }.filter { it.startsWith("https://") }
            NasdaqMeldung(m.s("disclosureId"), m.s("published").take(10), m.s("company"), m.s("headline"), m.s("cnsCategory"), link, pdfs, m.s("published"), m.s("language"))
        }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------ Tschechien (ČNB, Centrální úložiště regulovaných informací)

    private const val CNB = "https://oam.cnb.cz"

    private fun cnbTag(iso: String) = iso.split('-').let { (j, m, t) -> "$t.$m.$j" }

    fun cnbListe(isin: String, emittent: String, von: String, bis: String) =
        "$CNB/xmlpserver/OAM_CNB_CZ/R1_RES.xdo?_xpf=&_xpt=1&_xdo=/OAM_CNB_CZ/R1_RES.xdo&par_emit=${enc(if (isin.isBlank()) "$emittent*" else "EMIT*")}&par_emit_ico=EMIT-ICO*" +
            "&par_emit_lei=EMIT-LEI*&par_osemit=OSEMIT*&par_typ_inf_csv=23&par_typ_zprav_csv=xx&par_ISIN=${enc(isin.ifBlank { "ISIN*" })}&par_datum_od=${cnbTag(von)}" +
            "&par_datum_do=${cnbTag(bis)}&par_oznam=OZNAM*&par_oznam_ico=OZNAM-ICO*&par_dattrans_od=1.8.2009&par_dattrans_do=1.1.4000&par_ses_id=0&par_count=all" +
            "&par_lang=cs&_xt=lay_R1&_xf=html&_xautorun=false"

    fun cnbDetail(id: String) = "$CNB/xmlpserver/OAM_CNB_CZ/R2_FXX.xdo?_xpf=&_xpt=1&_xdo=/OAM_CNB_CZ/R2_FXX.xdo&par_obf_id=$id&par_lang=cs&_xt=lay_R2&_xf=html&_xmode=0"

    // Berichte entstehen im Hintergrund: die Adresse leitet auf eine Ladeseite um, deren Statusabfrage nach
    // „Report Completed“ den fertigen Bericht liefert.
    private suspend fun cnbBericht(netz: Netz, k: Kontingent, url: String): String? {
        val laden = k.abruf { netz.text(url, mapOf("Accept" to "text/html")) } ?: return null
        val status = Regex("var url = \"(/xmlpserver/servlet/xdo\\?[^\"]+)\"").find(laden)?.groupValues?.get(1) ?: return laden.takeIf { "Detail" in it || "Emitent" in it }
        val adresse = CNB + status
        repeat(20) {
            val r = k.abruf { netz.formular(adresse, emptyList(), mapOf("Accept" to "text/html")) } ?: return null
            if (r.isNotBlank()) return k.abruf { netz.formular(adresse, listOf("fromLoadingPage" to "true", "finalRequest" to "true"), mapOf("Accept" to "text/html")) }
            kotlinx.coroutines.delay(700)
        }
        return null
    }

    data class CnbZeile(val id: String, val emittent: String, val person: String, val tag: String, val art: String, val isin: String, val n: Double)

    // Zeilen der Ergebnisliste: Emittent / IČ, LEI, Meldender, Datum, Art, ISIN, Stueckzahl, Detail.
    fun cnbListeLesen(html: String): List<CnbZeile> =
        Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(html).mapNotNull { r ->
            val roh = r.groupValues[1]
            val id = Regex("par_obf_id=([A-Z]?\\d+)").find(roh)?.groupValues?.get(1) ?: return@mapNotNull null
            val z = Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(roh)
                .map { InsiderQuellen.entitaeten(it.groupValues[1].replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim() }.toList()
            if (z.size < 8) return@mapNotNull null
            CnbZeile(id, z[0].substringBefore(" / ").trim(), z[2], MarFormular.datum(z[3]), z[4], z[5], InsiderQuellen.zahl(z[6]) ?: 0.0)
        }.toList()

    // Detailseite: Bezeichnung und Wert als Zellen; je „Popis finančního nástroje“ ein Geschaeft.
    fun cnbDetailLesen(html: String, id: String, link: String, code: String): List<Geschaeft> {
        val z = MarFormular.htmlZeilen(html).flatMap { it.split('\t') }.map { it.trim() }.filter { it.isNotEmpty() }
        val bezeichnungen = setOf("Obchodní firma nebo název", "Identifikační číslo", "LEI", "Jméno, příjmení", "Funkce/postavení", "ID informace", "Datum přijetí", "Čas přijetí",
            "Osobní údaje", "Změna oznámení", "Detail transakce", "Popis finančního nástroje", "Identifikační kód", "Povaha transakce", "Další informace o povaze transakce",
            "Transakce", "Objem", "Jednotková cena", "Měna", "Souhrnný objem/Průměrná jednotková cena", "Datum uskutečnění transakce", "Místo transakce", "Další informace", "Odkazy")
        fun nach(label: String, ab: Int = 0, bis: Int = z.size): String {
            val i = (ab until bis).firstOrNull { z[it].equals(label, true) } ?: return ""
            return z.getOrNull(i + 1)?.takeIf { it !in bezeichnungen }.orEmpty()
        }
        val emittent = nach("Obchodní firma nebo název")
        val person = nach("Jméno, příjmení")
        val funktion = nach("Funkce/postavení")
        val publiziert = MarFormular.datum(nach("Datum přijetí"))
        val starts = z.indices.filter { z[it] == "Popis finančního nástroje" }
        val aus = ArrayList<Geschaeft>()
        for ((k, s) in starts.withIndex()) {
            val e = starts.getOrNull(k + 1) ?: z.size
            val instrument = nach("Popis finančního nástroje", s, e)
            val isin = MarFormular.isinIn(nach("Identifikační kód", s, e))
            val natur = nach("Povaha transakce", s, e) + " " + nach("Další informace o povaze transakce", s, e)
            val tag = MarFormular.datum(nach("Datum uskutečnění transakce", s, e))
            val ort = nach("Místo transakce", s, e)
            // Summe: Objem und Durchschnittspreis hinter „Souhrnný objem/Průměrná jednotková cena“; sonst die Zeilen der Tabelle
            val iSumme = (s until e).firstOrNull { z[it].startsWith("Souhrnný objem") }
            var n = iSumme?.let { InsiderQuellen.zahl(z.getOrNull(it + 1).orEmpty()) } ?: 0.0
            var preis = iSumme?.let { InsiderQuellen.zahl(z.getOrNull(it + 2).orEmpty()) } ?: 0.0
            val iMena = (s until e).firstOrNull { z[it] == "Měna" }
            var waehrung = ""
            if (iMena != null) {
                // Zeilen aus Nummer, Objem, Preis, Waehrung
                var j = iMena + 1; var summe = 0.0; var menge = 0.0
                while (j + 3 < (iSumme ?: e) && z[j].all { it.isDigit() }) {
                    val v = InsiderQuellen.zahl(z[j + 1]) ?: break
                    val p = InsiderQuellen.zahl(z[j + 2]) ?: 0.0
                    if (waehrung.isEmpty()) waehrung = z[j + 3].take(3).uppercase()
                    menge += v; summe += v * p
                    j += 4
                }
                if (n <= 0 && menge > 0) { n = menge; preis = summe / menge }
            }
            if (tag.isEmpty() || n <= 0) continue
            val aktie = MarFormular.istAktie(instrument) || Regex("(?i)^akci").containsMatchIn(instrument)
            val art = if (!aktie || preis <= 0) "S" else MarFormular.art(natur)
            aus += Geschaeft(
                e = emittent, isin = isin, p = person, r = InsiderQuellen.rolle(MarFormular.englisch(funktion)), t = funktion,
                i = if (aktie) "Aktie" else instrument.take(60), art = art, d = tag, v = publiziert, n = n,
                preis = if (art == "S") 0.0 else preis, wert = if (art == "S") 0.0 else n * preis, w = waehrung.ifEmpty { "CZK" }, o = ort,
                id = "CNB-$id", q = link, c = code,
            )
        }
        return aus
    }

    private suspend fun cnbHolen(netz: Netz, code: String, isin: String, firma: String, von: String, bis: String, bekannt: Set<String>): List<Geschaeft>? {
        val k = Kontingent(30)
        val emittent = kern(firma).take(1).joinToString("").replaceFirstChar { it.uppercase() }
        if (isin.isBlank() && emittent.isBlank()) return emptyList()
        val liste = cnbBericht(netz, k, cnbListe(isin, emittent, von, bis))?.let { cnbListeLesen(it) } ?: return null
        val aus = ArrayList<Geschaeft>()
        for (zeile in liste.filter { "CNB-${it.id}" !in bekannt && (isin.isBlank() || it.isin == isin) }.sortedByDescending { it.tag }.distinctBy { it.id }.take(6)) {
            val html = cnbBericht(netz, k, cnbDetail(zeile.id)) ?: break
            aus += cnbDetailLesen(html, zeile.id, cnbDetail(zeile.id), code).ifEmpty { listOf(InsiderQuellen.leer("CNB-${zeile.id}")) }
        }
        return aus
    }

    // ------------------------------------------------------------ Luxemburg (LuxSE, Officially Appointed Mechanism)

    private const val LUXSE = "https://www.luxse.com/api/luxse-graphql"

    fun luxseSuche(isin: String, von: String, bis: String) = "{\"query\":\"query { oamSubmissionsSearch(depositType: 10120, isin: \\\"$isin\\\", publicationStartDate: \\\"$von\\\", " +
        "publicationEndDate: \\\"$bis\\\", pageSize: 50, pageNumber: 1) { totalHits submissions { submissionId publicationDate issuerName } } }\"}"

    fun luxseDetail(id: String) = "{\"query\":\"query { oamSubmissionDetail(submissionId: $id) { documents { fileName url category obsolete } } }\"}"

    // Nur zum Lesen, ohne Original-Link.
    fun luxsePdf(token: String) = "https://dl.luxse.com/dl?v=${enc(token)}"

    // Meldungen: Kennung, Tag und Emittent.
    fun luxseSucheLesen(text: String): List<Triple<String, String, String>> = runCatching {
        json.parseToJsonElement(text).jsonObject["data"]?.jsonObject?.get("oamSubmissionsSearch")?.jsonObject?.get("submissions")?.objekte().orEmpty()
            .map { Triple(it.s("submissionId"), it.s("publicationDate").take(10), it.s("issuerName")) }.filter { it.first.isNotBlank() }
    }.getOrDefault(emptyList())

    fun luxseDetailLesen(text: String): List<String> = runCatching {
        json.parseToJsonElement(text).jsonObject["data"]?.jsonObject?.get("oamSubmissionDetail")?.jsonObject?.get("documents")?.objekte().orEmpty()
            .filter { it.s("obsolete") != "true" }.map { it.s("url") }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private suspend fun luxseHolen(netz: Netz, code: String, isin: String, von: String, bis: String, bekannt: Set<String>): List<Geschaeft>? {
        if (isin.length != 12) return emptyList()
        val k = Kontingent(20)
        val kopf = mapOf("Accept" to "application/json")
        val liste = k.abruf { netz.text(LUXSE, kopf, json = luxseSuche(isin, von, bis)) }?.let { luxseSucheLesen(it) } ?: return null
        val aus = ArrayList<Geschaeft>()
        for ((id, tag, firma) in liste.filter { "LUX-${it.first}" !in bekannt }.sortedByDescending { it.second }.take(8)) {
            val token = k.abruf { netz.text(LUXSE, kopf, json = luxseDetail(id)) }?.let { luxseDetailLesen(it) }?.firstOrNull() ?: break
            val m = MarFormular.Meldung("LUX-$id", "", tag, firma, isin, code)
            aus += k.abruf { netz.bytes(luxsePdf(token)) }?.let { MarFormular.lesen(MarFormular.pdfZeilen(it), m) }.orEmpty().ifEmpty { listOf(InsiderQuellen.leer(m.id)) }
        }
        return aus
    }

    // ------------------------------------------------------------ Italien (1Info, SDIR)

    private const val EINS_INFO = "https://www.1info.it/PORTALE1INFO/API"

    @Volatile private var einsInfoFirmen: Pair<Long, List<Pair<String, String>>>? = null

    fun einsInfoFirmenLesen(text: String): List<Pair<String, String>> = runCatching {
        json.parseToJsonElement(text).objekte().map { it.s("ndg") to it.s("descrizione").trim() }.filter { it.first.isNotBlank() }
    }.getOrDefault(emptyList())

    // Formular der Tabelle (DataTables); ohne die Spaltenangaben antwortet der Dienst mit einem Fehler.
    fun einsInfoFelder(ndg: String, anzahl: Int = 100) = listOf(
        "draw" to "1", "columns[0][data]" to "0", "columns[0][name]" to "", "columns[0][searchable]" to "true", "columns[0][orderable]" to "true",
        "columns[0][search][value]" to "", "columns[0][search][regex]" to "false", "order[0][column]" to "0", "order[0][dir]" to "desc",
        "start" to "0", "length" to "$anzahl", "search[value]" to "", "search[regex]" to "false",
        "SearchFilter[emittente]" to ndg, "SearchFilter[oggetto]" to "internal dealing",
    )

    data class EinsInfoMeldung(val id: String, val tag: String, val jahr: String, val pdf: String, val firma: String)

    fun einsInfoLesen(text: String): List<EinsInfoMeldung> = runCatching {
        json.parseToJsonElement(text).jsonObject["data"]?.objekte().orEmpty().mapNotNull { m ->
            val sek = m.s("dataDiffusione").toLongOrNull() ?: return@mapNotNull null
            val tag = java.time.Instant.ofEpochSecond(sek).atZone(java.time.ZoneId.of("Europe/Rome")).toLocalDate()
            val pdf = m.s("pdf").ifBlank { return@mapNotNull null }
            EinsInfoMeldung(m.s("idRow").ifBlank { pdf }, tag.toString(), tag.year.toString(), pdf, m.s("mittente"))
        }
    }.getOrDefault(emptyList())

    fun einsInfoPdf(m: EinsInfoMeldung, laden: Boolean = true) =
        "https://www.1info.it/PdfViewer/PdfShow.aspx?service=&type=comunicati&year=${m.jahr}&file=${m.pdf}.pdf" + if (laden) "&download=1" else ""

    private suspend fun einsInfoHolen(netz: Netz, code: String, isin: String, firma: String, von: String, bekannt: Set<String>): List<Geschaeft>? {
        val k = Kontingent(14)
        val firmen = einsInfoFirmen?.takeIf { System.currentTimeMillis() - it.first < 12 * 3_600_000L }?.second
            ?: (k.abruf { netz.text("$EINS_INFO/companies/comunicatistoccati", mapOf("Accept" to "application/json")) }?.let { einsInfoFirmenLesen(it) } ?: return null)
                .also { if (it.isNotEmpty()) einsInfoFirmen = System.currentTimeMillis() to it }
        // Nur Emittenten, die ueber 1Info veroeffentlichen; andere nutzen ein anderes SDIR
        val ndg = firmen.firstOrNull { gleicheFirma(it.second, firma) }?.first ?: return emptyList()
        val liste = k.abruf { netz.formular("$EINS_INFO/Comunicati", einsInfoFelder(ndg), mapOf("Accept" to "application/json", "X-Requested-With" to "XMLHttpRequest")) }
            ?.let { einsInfoLesen(it) } ?: return null
        val aus = ArrayList<Geschaeft>()
        for (m in liste.filter { it.tag >= von && "1INFO-${it.id}" !in bekannt }.sortedByDescending { it.tag }.take(10)) {
            val meldung = MarFormular.Meldung("1INFO-${m.id}", einsInfoPdf(m, laden = false), m.tag, m.firma, isin, code)
            aus += k.abruf { netz.bytes(einsInfoPdf(m)) }?.let { MarFormular.lesen(MarFormular.pdfZeilen(it), meldung) }.orEmpty().ifEmpty { listOf(InsiderQuellen.leer(meldung.id)) }
        }
        return aus
    }

    // ------------------------------------------------------------ Zypern (CSE, Public OAM)

    private const val CSE = "https://publicoam.cse.com.cy/xak-public-pages-server/api"

    @Volatile private var cseFirmen: Pair<Long, List<Pair<String, String>>>? = null

    fun cseFirmenLesen(text: String): List<Pair<String, String>> = runCatching {
        json.parseToJsonElement(text).objekte().map { it.s("id") to it.s("nameEnglish").ifBlank { it.s("nameGr") }.trim() }.filter { it.first.isNotBlank() }
    }.getOrDefault(emptyList())

    fun cseListe(firmaId: String) = "$CSE/fetch-listing-versions?page=0&size=100&sort=REGISTRY_TIMESTAMP,DESC&informationCategoryId=46&listedCompanyId=$firmaId"

    data class CseMeldung(val id: String, val tag: String, val englisch: Boolean, val uebersetzung: String, val firma: String)

    // Meldungen der Firma; die Reihenfolge des Dienstes stimmt nicht, deshalb hier nach Datum sortiert.
    fun cseListeLesen(text: String): List<CseMeldung> = runCatching {
        json.parseToJsonElement(text).jsonObject["content"]?.objekte().orEmpty().mapNotNull { m ->
            val ms = m.s("publicationTimestamp").toLongOrNull() ?: return@mapNotNull null
            val tag = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.of("Asia/Nicosia")).toLocalDate().toString()
            CseMeldung(m.s("id"), tag, m.s("infoLanguagesNameEn").equals("English", true), m.s("translationId"), m.s("listedCompanyEnglish").trim())
        }.sortedByDescending { it.tag }
    }.getOrDefault(emptyList())

    // PDFs einer Meldung, im JSON als Base64.
    fun cseDateienLesen(text: String): List<ByteArray> = runCatching {
        json.parseToJsonElement(text).objekte().filter { it.s("fileType").contains("pdf") || it.s("fileName").endsWith(".pdf", true) }
            .mapNotNull { runCatching { java.util.Base64.getDecoder().decode(it.s("file")) }.getOrNull() }
    }.getOrDefault(emptyList())

    private suspend fun cseHolen(netz: Netz, code: String, isin: String, firma: String, von: String, bekannt: Set<String>): List<Geschaeft>? {
        val k = Kontingent(10)
        val kopf = mapOf("Accept" to "application/json")
        val firmen = cseFirmen?.takeIf { System.currentTimeMillis() - it.first < 12 * 3_600_000L }?.second
            ?: (k.abruf { netz.text("$CSE/fetch-listed-companies", kopf) }?.let { cseFirmenLesen(it) } ?: return null)
                .also { if (it.isNotEmpty()) cseFirmen = System.currentTimeMillis() to it }
        val id = firmen.firstOrNull { gleicheFirma(it.second, firma) }?.first ?: return emptyList()
        val liste = k.abruf { netz.text(cseListe(id), kopf) }?.let { cseListeLesen(it) } ?: return null
        // Englische Fassungen; griechische nur, wenn es keine Uebersetzung gibt (das Formular ist dann oft zweisprachig)
        val uebersetzt = liste.filter { !it.englisch && it.uebersetzung != "0" && it.uebersetzung.isNotBlank() }.map { it.uebersetzung }.toSet()
        val auswahl = liste.filter { it.tag >= von && (it.englisch || it.id !in uebersetzt && it.uebersetzung.let { u -> u == "0" || u.isBlank() }) }
            .filter { "CSE-${it.id}" !in bekannt }.take(6)
        val aus = ArrayList<Geschaeft>()
        for (m in auswahl) {
            val meldung = MarFormular.Meldung("CSE-${m.id}", "https://publicoam.cse.com.cy/", m.tag, m.firma, isin, code)
            // Als Bytes geholt: mehrere PDFs in Base64 sind schnell einige Megabyte gross
            val pdfs = k.abruf { netz.bytes("$CSE/fetch-files?listingVersionId=${m.id}") }?.let { cseDateienLesen(it.toString(Charsets.UTF_8)) } ?: break
            aus += pdfs.flatMap { MarFormular.lesen(MarFormular.pdfZeilen(it), meldung) }.ifEmpty { listOf(InsiderQuellen.leer(meldung.id)) }
        }
        return aus
    }

    // ------------------------------------------------------------ Ungarn (MNB, Tőkepiaci közzétételek)

    private const val MNB = "https://kozzetetelek.mnb.hu"

    fun mnbSuche(wort: String) = listOf(
        "selectedInstitutionTypes" to "NyilvanosErtekpapirKibocsato", "DocumentSubType" to "106", "Concerned" to wort,
        "pagesize" to "50", "page" to "1", "orderby" to "10",
    )

    // Zeilen der Trefferliste: Kennung, Tag der Veroeffentlichung, Emittent.
    fun mnbListeLesen(html: String): List<Triple<String, String, String>> =
        Regex("<tr>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(html).mapNotNull { r ->
            val roh = r.groupValues[1]
            val id = Regex("viewid=(K\\d+/\\d{4})").find(roh)?.groupValues?.get(1) ?: return@mapNotNull null
            val z = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(roh).map { InsiderQuellen.entitaeten(it.groupValues[1].replace(Regex("<[^>]+>"), " ")).trim() }.toList()
            Triple(id, MarFormular.datum(z.firstOrNull().orEmpty()), z.getOrNull(2).orEmpty())
        }.toList()

    // Meldung der MNB als Link: Kennung, Tag der Veroeffentlichung, Emittent; die Seite der Meldung fuehrt zu den PDFs.
    data class MnbMeldung(val id: String, val tag: String, val emittent: String) {
        val link: String get() = "$MNB/kozzetetelek?viewid=$id"
    }

    // Meldungen der Firma seit von (JJJJ-MM-TT) aus der Trefferliste, neueste zuerst, hoechstens 20; null, wenn die
    // Suche nicht antwortet. PDFs oeffnet die Person selbst.
    suspend fun mnbMeldungen(netz: Netz, firma: String, von: String): List<MnbMeldung>? {
        val wort = kern(firma).take(2).joinToString(" ").ifBlank { return emptyList() }
        val html = netz.formular("$MNB/search/advanced", mnbSuche(wort), mapOf("Accept" to "text/html")) ?: return null
        return mnbListeLesen(html).filter { it.second >= von && gleicheFirma(it.third, firma) }
            .sortedByDescending { it.second }.take(20).map { MnbMeldung(it.first, it.second, it.third) }
    }

    // ------------------------------------------------------------ Abruf je Aktie

    // Neue Meldungen einer Aktie seit ab (beim ersten Mal ein Jahr); null, wenn die Quelle nicht erreichbar war.
    suspend fun holen(netz: Netz, land: String, code: String, isin: String, firma: String, heute: java.time.LocalDate, ab: java.time.LocalDate?, bekannt: Set<String>): List<Geschaeft>? {
        val von = (ab ?: heute.minusDays(365)).toString()
        val bis = heute.toString()
        val liste = when (land) {
            "DK", "FI", "IS", "EE", "LV", "LT" -> emptyList()
            "CZ" -> cnbHolen(netz, code, isin, firma, von, bis, bekannt)
            "LU" -> luxseHolen(netz, code, isin, von, bis, bekannt)
            "IT" -> einsInfoHolen(netz, code, isin, firma, von, bekannt)
            "CY" -> cseHolen(netz, code, isin, firma, von, bekannt)
            // Ungarn: Meldungen nur als Links (mnbMeldungen)
            "HU" -> emptyList()
            else -> emptyList()
        } ?: return null
        // Geschaefte in anderen Wertpapieren derselben Meldung (Anleihen) bleiben, die Kennung der Aktie kommt dazu
        return liste.map { if (it.c.isBlank() && it.d.isNotEmpty()) it.copy(c = code) else it }
    }
}
