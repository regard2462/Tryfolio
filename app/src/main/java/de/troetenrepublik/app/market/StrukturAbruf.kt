package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.IpoProspekt
import de.troetenrepublik.app.engine.Struktur
import de.troetenrepublik.app.engine.StrukturAktion
import de.troetenrepublik.app.engine.StrukturRegeln
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Eigentuemer und Rechte, Abruf: Deckblatt (Gattungen, Aktienzahl), Tabelle der Grossaktionaere (DEF 14A, 20-F), Schedule 13G
// und 13D (Halter, Aktivisten), 8-K Item 5.07 (Hauptversammlung) und Wikidata (Eigentuemer ohne SEC). Alles kommt von der SEC
// ohne Schluessel, nur mit der Kennung der App; grosse Unterlagen werden nur bis zur gesuchten Stelle gelesen. Ergebnis je Wert
// auf dem Geraet: eine Woche, ohne Ergebnis ein Tag. Netz, Speicher, Uhr und Pause sind einspeisbar.
interface StrukturNetz {
    // Antworttext der SEC (Kennung mit Kontakt gehoert in die Umsetzung); null bei Fehler
    suspend fun sec(url: String): String?

    // Dokument der SEC als Text, gelesen bis genug(Text) gilt oder max Bytes (entpackt) erreicht sind; null bei Fehler
    suspend fun secBis(url: String, max: Long, genug: (String) -> Boolean): String?

    // Antworttext von Wikidata ohne Kennung; null bei Fehler
    suspend fun wiki(url: String): String?
}

class StrukturAbruf(
    private val netz: StrukturNetz,
    private val speicher: FirmenSpeicher,
    private val jetzt: () -> Long,
    private val warte: suspend (Long) -> Unit = { delay(it) },
) {
    companion object {
        const val DATEI = "struktur.json"
        const val WOCHE_MS = 7 * 86_400_000L
        const val TAG_MS = 86_400_000L
        const val PAUSE_MS = 500L
        private const val MAX_WERTE = 200
        private const val MAX_COVER = 900_000L
        private const val MAX_TABELLE = 7_000_000L
        private val JAHRESFORMEN = setOf("10-K", "10-Q", "20-F", "40-F")
        private val AUSLAND = setOf("20-F", "40-F")

        private val GRUPPE = Regex("(?i)as(?:\\s|&nbsp;|&#160;|&#xa0;)+a(?:\\s|&nbsp;|&#160;|&#xa0;)+group")
        private val UEBERSCHRIFT = Regex("(?i)security(?:\\s|&nbsp;|&#160;)+ownership(?:\\s|&nbsp;|&#160;)+of(?:\\s|&nbsp;|&#160;)+certain(?:\\s|&nbsp;|&#160;)+beneficial|major(?:\\s|&nbsp;|&#160;)+shareholders")

        // Ist die Tabelle der Grossaktionaere samt ihrem Ende gelesen? Ueberschrift, dann "as a group", dann das Tabellenende.
        fun tabelleFertig(t: String): Boolean {
            val h = UEBERSCHRIFT.findAll(t).map { it.range.first }.lastOrNull() ?: return false
            val m = GRUPPE.find(t, h) ?: return false
            return t.indexOf("</table", m.range.last, ignoreCase = true) >= 0
        }

        fun coverFertig(t: String): Boolean {
            val i = t.lastIndexOf("EntityCommonStockSharesOutstanding")
            return i >= 0 && t.length - i > 6_000
        }

        fun suche13(cik: String, formen: String, ab: String, bis: String): String =
            "https://efts.sec.gov/LATEST/search-index?q=&ciks=" + cik.trimStart('0').padStart(10, '0') +
                "&forms=" + java.net.URLEncoder.encode(formen, "UTF-8") + "&dateRange=custom&startdt=$ab&enddt=$bis"

        fun wikidataEintrag(id: String) = "https://www.wikidata.org/wiki/Special:EntityData/$id.json"
    }

    class Einreichung(val form: String, val acc: String, val doc: String, val tag: String, val items: String)

    private val json = Json { ignoreUnknownKeys = true }

    // Einreichungen aus der Liste der letzten Meldungen
    fun einreichungen(subs: String): List<Einreichung> = runCatching {
        val r = json.parseToJsonElement(subs).jsonObject["filings"]?.jsonObject?.get("recent")?.jsonObject ?: return emptyList()
        fun spalte(k: String) = (r[k] as? JsonArray)?.map { it.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
        val formen = spalte("form"); val nummern = spalte("accessionNumber"); val dok = spalte("primaryDocument")
        val tage = spalte("filingDate"); val items = spalte("items")
        formen.indices.map { Einreichung(formen[it], nummern.getOrElse(it) { "" }, dok.getOrElse(it) { "" }, tage.getOrElse(it) { "" }, items.getOrElse(it) { "" }) }
    }.getOrDefault(emptyList())

    private fun firmenname(subs: String): String = runCatching { json.parseToJsonElement(subs).jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty() }.getOrDefault("")

    private val sperre = Mutex()
    private var stand: MutableMap<String, Struktur.Eintrag>? = null
    private var kuerzel: String? = null
    private var abrufe = 0

    private fun standMap(): MutableMap<String, Struktur.Eintrag> =
        stand ?: (speicher.lesen(DATEI)?.let { Struktur.dateiLesen(it) }?.werte?.toMutableMap() ?: HashMap()).also { stand = it }

    private fun ablegen(m: MutableMap<String, Struktur.Eintrag>, symbol: String, e: Struktur.Eintrag) {
        m[symbol] = e
        if (m.size > MAX_WERTE) m.entries.sortedBy { it.value.abgerufen }.take(m.size - MAX_WERTE).forEach { m.remove(it.key) }
        runCatching { speicher.schreiben(DATEI, Struktur.dateiSchreiben(Struktur.Datei(m))) }
    }

    private suspend fun secText(url: String): String? {
        if (abrufe++ > 0) warte(PAUSE_MS)
        return netz.sec(url)
    }

    // Suche der SEC (efts): antwortet sie einmal mit einem Fehler, einmal nach der Pause erneut fragen
    private suspend fun secSuche(url: String): String? {
        secText(url)?.let { return it }
        warte(PAUSE_MS)
        abrufe++
        return netz.sec(url)
    }

    private suspend fun secDokument(url: String, max: Long, genug: (String) -> Boolean): String? {
        if (abrufe++ > 0) warte(PAUSE_MS)
        return netz.secBis(url, max, genug)
    }

    // Was ohne Abruf bekannt ist
    suspend fun gespeichert(symbol: String): Struktur.Ergebnis? = sperre.withLock { standMap()[symbol]?.ergebnis }

    // Ergebnis aus SEC und Wikidata; mitSec false: keine Kontaktadresse, dann nur Wikidata und der gespeicherte Stand
    suspend fun laden(symbol: String, name: String, isin: String, mitSec: Boolean, sprache: String): Struktur.Ergebnis? = sperre.withLock {
        val m = standMap()
        val alt = m[symbol]
        val t = jetzt()
        val frist = if (alt?.ergebnis != null) WOCHE_MS else TAG_MS
        if (alt != null && t - alt.abgerufen < frist && !(mitSec && alt.cik.isEmpty())) return@withLock alt.ergebnis
        abrufe = 0
        var cik = alt?.cik.orEmpty()
        var sec: Struktur.Ergebnis? = null
        if (mitSec) {
            if (cik.isEmpty() || cik == "-") cik = cikFinden(symbol, name, isin) ?: "-"
            if (cik != "-") sec = runCatching { vonSec(cik, name) }.getOrNull()
            // Kein Netz oder Fehler: der alte Stand bleibt
            if (sec == null && cik != "-" && alt?.ergebnis != null) return@withLock alt.ergebnis
        }
        var e = sec
        if (e == null || e.halter.isEmpty()) {
            val w = runCatching { wikidataHalter(name, sprache) }.getOrNull()
            if (w != null) {
                val basis = e ?: Struktur.Ergebnis(firma = name)
                val b = Struktur.Beleg("Wikidata (CC0)", "https://www.wikidata.org", "")
                e = if (w.isEmpty()) basis.copy(geprueft = basis.geprueft + "WIKIDATA")
                else basis.copy(halter = w.map { it.copy(beleg = basis.belege.size) }, belege = basis.belege + b, geprueft = basis.geprueft + "WIKIDATA")
            }
        }
        // Auch ohne Fund bleibt stehen, was geprueft wurde: fehlende Teile sollen nicht wie "gibt es nicht" wirken
        val fertig = e?.takeIf { !it.leer || it.geprueft.isNotEmpty() }
        ablegen(m, symbol, Struktur.Eintrag(t, if (mitSec) cik else "", fertig))
        fertig
    }

    private suspend fun cikFinden(symbol: String, name: String, isin: String): String? {
        // Bekannte CIK aus dem Speicher der Jahresberichte, sonst die Kuerzelliste der SEC (einmal je Lauf)
        speicher.lesen(Firmenberichte.SEC_DATEI)?.let { SecBericht.lesen(it) }?.werte?.get(symbol)?.cik?.takeIf { it.isNotBlank() }?.let { return it }
        val liste = kuerzel ?: secText(SecFormen.KUERZEL)?.also { kuerzel = it } ?: return null
        return SecBericht.cikAusListe(liste, symbol, name)
    }

    private fun tagText(ms: Long): String = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()

    private fun vorMonaten(n: Long): String = java.time.Instant.ofEpochMilli(jetzt()).atZone(java.time.ZoneOffset.UTC).toLocalDate().minusMonths(n).toString()

    private fun namenGleich(a: String, b: String): Boolean {
        val x = SecBericht.nameKern(a); val y = SecBericht.nameKern(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.contains(y) || y.contains(x))
    }

    private suspend fun vonSec(cik: String, name: String): Struktur.Ergebnis? {
        val subs = secText(SecBericht.submissionsAdresse(cik)) ?: return null
        val einr = einreichungen(subs)
        val belege = ArrayList<Struktur.Beleg>()
        val geprueft = LinkedHashSet<String>()
        fun beleg(n: String, url: String?, stand: String): Int { belege += Struktur.Beleg(n, url, stand); return belege.size - 1 }
        val flags = LinkedHashSet<String>()
        var stand = ""

        // Deckblatt: juengster 10-K, 10-Q, 20-F oder 40-F
        val deck = einr.filter { it.form in JAHRESFORMEN && it.doc.isNotBlank() }.maxByOrNull { it.tag }
        var d: StrukturRegeln.Deckblatt? = null
        var bDeck = -1
        var coverHinweise: Set<String> = emptySet()
        if (deck != null) {
            val html = secDokument(SecBericht.archivAdresse(cik, deck.acc, deck.doc), MAX_COVER, ::coverFertig)
            if (html != null) {
                d = StrukturRegeln.deckblatt(html)
                coverHinweise = StrukturRegeln.hinweise(html).filter { it == Struktur.Flag.ADR }.toSet()
                stand = deck.tag
                bDeck = beleg("SEC Form ${deck.form}, cover page", SecBericht.archivAdresse(cik, deck.acc, deck.doc), deck.tag)
                geprueft += "DECKBLATT"
            }
        }
        // Tabelle der Grossaktionaere und Stimmrecht: 20-F und 40-F tragen sie selbst, sonst der juengste DEF 14A
        val tabDok = if (deck != null && deck.form in AUSLAND) deck
        else einr.filter { it.form == "DEF 14A" && it.doc.isNotBlank() && it.tag >= vorMonaten(16) }.maxByOrNull { it.tag }
        var tabHtml: String? = null
        var bTab = -1
        var hinweise: Set<String> = coverHinweise
        if (tabDok != null) {
            val url = SecBericht.archivAdresse(cik, tabDok.acc, tabDok.doc)
            val html = secDokument(url, MAX_TABELLE, ::tabelleFertig)
            if (html != null) {
                tabHtml = html
                hinweise = hinweise + StrukturRegeln.hinweise(html).filter { it != Struktur.Flag.ADR || deck?.form in AUSLAND }
                bTab = beleg(if (tabDok.form == "DEF 14A") "SEC DEF 14A" else "SEC Form ${tabDok.form}", url, tabDok.tag)
                geprueft += if (tabDok.form == "DEF 14A") "DEF14A" else "F20"
                if (stand.isBlank()) stand = tabDok.tag
            }
        }
        hinweise.forEach { flags += it }
        // Gattungen: eine Stimme je Aktie nur, wenn der Standardsatz im Dokument gelesen ist
        var gattungen: List<Struktur.Gattung> = d?.let { StrukturRegeln.gattungen(it, hinweise) }.orEmpty()
        gattungen = gattungen.map { g ->
            when {
                g.stimmen == 1.0 && bTab >= 0 -> g.copy(beleg = bTab)
                else -> if (bDeck >= 0) g.copy(beleg = bDeck) else g
            }
        }
        if (gattungen.any { it.art == Struktur.GArt.ADS }) flags += Struktur.Flag.ADR

        val halter = ArrayList<Struktur.Halter>()
        val html0 = tabHtml
        if (html0 != null) {
            // Mehrere Klassen: Prozent je Klasse und an den Stimmen aus den Spalten, sonst die eine Prozentspalte
            val spalten = IpoProspekt.eignerSpalten(html0)?.let { StrukturRegeln.halterAusSpalten(it, gattungen, bTab) }
            if (spalten != null) halter += spalten
            else IpoProspekt.eigner(html0)?.let { e -> halter += StrukturRegeln.halterAusTabelle(e, bTab) }
        }
        val einzelne = gattungen.filter { it.art != Struktur.GArt.ADS }
        fun gattungId(titel: String): String? {
            Regex("(?i)\\bclass\\s*([A-Z])\\b").find(titel)?.groupValues?.get(1)?.uppercase()?.let { id -> einzelne.firstOrNull { it.id == id }?.let { return it.id } }
            return einzelne.singleOrNull()?.id
        }
        // 13G und 13D nennen den Anteil an der Gattung, nicht am Kapital: bei mehreren Gattungen als klasse, sonst gleich dem Kapital
        fun mitProzent(name: String, p: Double, titel: String, b: Int, aktivist: Boolean): Struktur.Halter {
            val g = gattungId(titel)
            val art = StrukturRegeln.halterArt(name, true)
            return if (einzelne.size <= 1) Struktur.Halter(name, art, kapital = p, gattung = g, aktivist = aktivist, beleg = b)
            else Struktur.Halter(name, art, klasse = p, gattung = g, aktivist = aktivist, beleg = b)
        }

        val ab = vorMonaten(24)
        val bis = tagText(jetzt())
        // Schedule 13G: je Melder die neueste Meldung
        secSuche(suche13(cik, "SCHEDULE 13G,SCHEDULE 13G/A", ab, bis))?.let { such ->
            geprueft += "13G"
            for (tr in StrukturRegeln.treffer13(such, cik).take(6)) {
                if (halter.any { namenGleich(it.name, tr.melder) }) continue
                val xml = secText(SecBericht.archivAdresse(cik, tr.adsh, "primary_doc.xml")) ?: continue
                val mel = StrukturRegeln.meldung13(xml) ?: continue
                val b = beleg("SEC Schedule 13G, ${tr.melder}", "https://www.sec.gov/Archives/edgar/data/${cik.trimStart('0')}/${tr.adsh.replace("-", "")}/", tr.tag)
                halter += mitProzent(tr.melder, mel.prozent, mel.titel, b, false)
            }
        }
        // Schedule 13D: je Melder bis zu drei Meldungen, die juengste oben, die uebrigen als Verlauf
        val aktivisten = ArrayList<Struktur.Aktivist>()
        secSuche(suche13(cik, "SCHEDULE 13D,SCHEDULE 13D/A", ab, bis))?.let { such ->
            geprueft += "13D"
            val alle = StrukturRegeln.treffer13(such, cik, alle = true)
            val je = alle.groupBy { it.cik }.values.sortedByDescending { l -> l.maxOf { it.tag } }.take(4)
            for (meldungen in je) {
                val verlauf = ArrayList<Struktur.Verlauf>()
                var melder = meldungen.first().melder
                var titel = ""
                for (tr in meldungen.sortedByDescending { it.tag }.take(3)) {
                    val xml = secText(SecBericht.archivAdresse(cik, tr.adsh, "primary_doc.xml")) ?: continue
                    val mel = StrukturRegeln.meldung13(xml) ?: continue
                    if (titel.isEmpty()) titel = mel.titel
                    melder = tr.melder
                    val b = beleg("SEC Schedule 13D, ${tr.melder} (${tr.tag})", "https://www.sec.gov/Archives/edgar/data/${cik.trimStart('0')}/${tr.adsh.replace("-", "")}/", tr.tag)
                    verlauf += Struktur.Verlauf(mel.ereignis.ifBlank { tr.tag }, mel.prozent, StrukturAktion.absicht(mel.zweck), b)
                }
                val p = verlauf.firstOrNull()?.prozent ?: continue
                val a = StrukturAktion.aktivist(melder, verlauf)
                val vorhanden = halter.indexOfFirst { namenGleich(it.name, melder) }
                if (a != null) aktivisten += a
                // Auch ein 13D-Melder ohne gelesene Absicht ist Halter, nur nicht Aktivist
                if (vorhanden >= 0) {
                    if (a != null) halter[vorhanden] = halter[vorhanden].copy(aktivist = true)
                } else halter += mitProzent(melder, p, titel, verlauf.first().beleg, a != null)
            }
        }
        // Hauptversammlung: die beiden juengsten 8-K mit Item 5.07
        val versammlungen = ArrayList<Struktur.Versammlung>()
        if (einr.isNotEmpty()) geprueft += "8K"
        for (e in einr.filter { it.form == "8-K" && it.items.split(',').any { i -> i.trim() == "5.07" } && it.doc.isNotBlank() }.sortedByDescending { it.tag }.take(2)) {
            val url = SecBericht.archivAdresse(cik, e.acc, e.doc)
            val html = secDokument(url, 1_500_000L) { it.contains("</html", true) } ?: continue
            val bs = StrukturAktion.beschluesse(html)
            if (bs.isEmpty()) continue
            versammlungen += Struktur.Versammlung(e.tag, bs, beleg("SEC Form 8-K Item 5.07", url, e.tag))
        }
        return Struktur.Ergebnis(
            firma = firmenname(subs).ifBlank { name }, gattungen = gattungen, halter = halter, belege = belege, flags = flags.toList(),
            stand = stand, versammlungen = versammlungen, aktivisten = aktivisten, geprueft = geprueft.toList(),
        )
    }

    // Eigentuemer aus Wikidata (P127, Anteil P1107), nur mit Beschriftung; Kapital nur, wenn Wikidata einen Anteil nennt
    private suspend fun wikidataHalter(name: String, sprache: String): List<Struktur.Halter>? {
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        val ids = FirmaWikidata.treffer(netz.wiki(FirmaWikidata.suchAdresse(name, sprache, enc)) ?: return null).take(3)
        for (id in ids) {
            val eintrag = netz.wiki(wikidataEintrag(id)) ?: continue
            if (FirmaWikidata.ersteFirma(eintrag, listOf(id)) == null) continue
            val eigner = StrukturRegeln.wikidataEigner(eintrag, id).take(6)
            if (eigner.isEmpty()) return emptyList()
            val namen = netz.wiki(FirmaWikidata.eintraegeAdresse(eigner.map { it.id }.distinct(), "labels", "$sprache|en"))?.let { FirmaWikidata.beschriftungen(it, sprache) }.orEmpty()
            return eigner.mapNotNull { x ->
                val n = namen[x.id] ?: return@mapNotNull null
                Struktur.Halter(n, StrukturRegeln.halterArt(n, true), kapital = x.prozent)
            }
        }
        return emptyList()
    }
}
