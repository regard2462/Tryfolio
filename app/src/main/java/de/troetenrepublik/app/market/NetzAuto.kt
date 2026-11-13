package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Netz
import de.troetenrepublik.app.engine.NetzZulieferer
import de.troetenrepublik.app.engine.Struktur
import de.troetenrepublik.app.i18n.NetzTexte
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Automatische Kanten je Firma (0.79), gemeldet statt gepflegt: Grosskunden aus den SEC-Konzentrationsangaben (auch anonyme als
// Sammelknoten), Mutter und Toechter aus GLEIF, Eigentuemer, Mutter, Toechter und Beteiligungen aus Wikidata (Special:EntityData),
// die Tochterliste aus Anlage 21 des Jahresberichts und Halter aus dem Dienst fuer Eigentuemer und Rechte. Reines Lesen und Ordnen,
// ohne Netz; der Abruf mit Zwischenspeicher steht in NetzLader.kt.
object NetzAuto {

    class Zusatz(val knoten: List<Netz.Knoten>, val kanten: List<Netz.Kante>) {
        val leer: Boolean get() = kanten.isEmpty()
        operator fun plus(o: Zusatz): Zusatz = Zusatz((knoten + o.knoten).distinctBy { it.id }, kanten + o.kanten)
    }

    val LEER = Zusatz(emptyList(), emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    // Obergrenzen je Quelle, damit ein Knoten das Netz nicht flutet
    const val MAX_TOECHTER = 25
    const val MAX_HALTER = 8
    const val MAX_WIKIDATA = 25

    private fun slug(s: String): String = Netz.nameVoll(s).ifEmpty { s.lowercase() }.replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifEmpty { "x" }

    private fun kante(
        id: String, von: String, nach: String, art: Netz.Art, umfang: Netz.Umfang?, guete: Netz.Guete, quelle: Netz.Quelle, stand: String,
        seit: String? = null, status: Netz.Status = Netz.Status.DEFINITIV, rolle: String? = null, programm: String? = null, programmEn: String? = null,
        hinweis: String? = null, hinweisEn: String? = null,
    ) = Netz.Kante(
        id, von, nach, art, umfang, seit, null, status, guete, quelle, emptyList(), stand, null, hinweis, hinweisEn,
        false, null, false, Netz.Herkunft.GEMELDET, rolle, programm, programmEn,
    )

    // Knoten fuer einen genannten Namen: bekannte Firma des Netzes, sonst ein neuer Knoten mit Kennung nach Praefix
    private fun knotenFuer(graph: Netz.Graph, name: String, praefix: String, typ: Netz.Typ = Netz.Typ.FIRMA): Netz.Knoten {
        graph.knotenFuerName(name)?.let { return it }
        return Netz.Knoten(praefix + slug(name), typ, name)
    }

    // ------------------------------------------------------------ SEC: Grosskunden

    // Kunden aus der Auswertung des Jahresberichts: genannte Firmen als Knoten, anonyme ("Kunde A", "3 Kunden") als Sammelknoten.
    fun kunden(firma: Netz.Knoten, b: SecBericht.Auswertung, graph: Netz.Graph, quellUrl: String, standAbruf: String): Zusatz {
        val stand = b.stichtag.takeIf { it.length == 10 } ?: standAbruf
        val knoten = ArrayList<Netz.Knoten>()
        val kanten = ArrayList<Netz.Kante>()
        val quelle = Netz.Quelle(NetzTexte.text(true, "quelle.sec.bericht", listOf(b.jahr.toString())), quellUrl, true)
        for (p in b.kunden) {
            if (p.w <= 0.0) continue
            val ziel: Netz.Knoten = when (p.k) {
                1 -> Netz.Knoten(
                    "kunde:${firma.id}:${p.nr}", Netz.Typ.SAMMEL, NetzTexte.text(true, "kunde", listOf(p.nr, firma.name)),
                    NetzTexte.text(false, "kunde", listOf(p.nr, firma.name)),
                )
                2 -> Netz.Knoten(
                    "kunden:${firma.id}:${p.nr}", Netz.Typ.SAMMEL, NetzTexte.text(true, "kunden", listOf(p.nr, firma.name)),
                    NetzTexte.text(false, "kunden", listOf(p.nr, firma.name)),
                )
                else -> knotenFuer(graph, p.n, "name:")
            }
            if (ziel.id == firma.id) continue
            if (graph.knoten[ziel.id] == null) knoten += ziel
            kanten += kante(
                "sec:${firma.id}:${ziel.id}", firma.id, ziel.id, Netz.Art.LIEFERT,
                Netz.Umfang(p.w * 100.0, Netz.Einheit.PCT, Netz.Bezug.UMSATZ), Netz.Guete.A, quelle, stand,
            )
        }
        return Zusatz(knoten, kanten)
    }

    // ------------------------------------------------------------ SEC: Zulieferer rueckwaerts (Datei netz/zulieferer.json)

    const val MAX_ZULIEFERER = 15
    const val MAX_KUNDEN_DATEI = 10

    // Zulieferer, die die Firma als Grosskunden melden (Kante liefert, Guete B, Link auf die Einreichung), und, wenn die Firma selbst
    // Berichtende der Datei ist, ihre genannten Kunden. Kundennamen gleichen streng ab: voller normierter Name der Firma (oder ihr
    // Name ohne Zusaetze), nie ein Teilwort. Gehoert derselbe Name auch einer anderen Katalogfirma, bleibt er weg.
    fun zulieferer(firma: Netz.Knoten, d: NetzZulieferer.Daten, katalog: List<Pair<String, String>>, graph: Netz.Graph): Zusatz {
        if (firma.typ != Netz.Typ.FIRMA) return LEER
        val knoten = ArrayList<Netz.Knoten>()
        val kanten = ArrayList<Netz.Kante>()
        fun neu(n: Netz.Knoten) { if (graph.knoten[n.id] == null && knoten.none { it.id == n.id }) knoten += n }
        fun quelle(z: NetzZulieferer.Zeile) = Netz.Quelle(NetzTexte.text(true, "quelle.sec.bericht", listOf(z.jahr.toString())), einreichungsseite(z.cik, z.adsh), true)
        fun hinweis(deutsch: Boolean, wer: String, z: NetzZulieferer.Zeile) = NetzTexte.text(deutsch, "hinweis.kunde", listOf(wer, z.jahr.toString()))
        fun umfang(z: NetzZulieferer.Zeile) = Netz.Umfang(z.prozent, Netz.Einheit.PCT, Netz.Bezug.UMSATZ)

        // Rueckwaerts: andere Firmen nennen diese als Kunden
        val eigene = NetzZulieferer.schluesselVon(firma.name)
        val eigeneSymbole = firma.symbole.map { Netz.symbolSchluessel(it) }.toSet()
        val eigeneCik = firma.cik?.trimStart('0')
        val zeilen = eigene.flatMap { d.nachKunde[it].orEmpty() }.distinct()
            .filter { it.cik != eigeneCik && (it.symbol.isEmpty() || Netz.symbolSchluessel(it.symbol) !in eigeneSymbole) }
        val eigenerName = gattungslos(firma.name)
        val mehrdeutig = eigene.any { k ->
            katalog.any { (name, sym) ->
                NetzZulieferer.schluesselVon(name).contains(k) && Netz.symbolSchluessel(sym) !in eigeneSymbole && gattungslos(name) != eigenerName
            }
        }
        if (!mehrdeutig) {
            val je = HashMap<String, NetzZulieferer.Zeile>()
            for (z in zeilen) {
                val alt = je[z.cik]
                if (alt == null || z.stichtag > alt.stichtag) je[z.cik] = z
            }
            for (z in je.values.sortedByDescending { it.prozent }.take(MAX_ZULIEFERER)) {
                val sym = z.symbol.takeIf { it.isNotEmpty() }
                val n = graph.knotenFuerKennung(cik = z.cik, symbol = sym)
                    ?: Netz.Knoten(sym ?: "cik:${z.cik}", Netz.Typ.FIRMA, zuliefererName(z, katalog), symbole = listOfNotNull(sym), cik = z.cik)
                if (n.id == firma.id) continue
                neu(n)
                val wer = n.name
                kanten += kante(
                    "sec:${n.id}:${firma.id}", n.id, firma.id, Netz.Art.LIEFERT, umfang(z), Netz.Guete.B, quelle(z), z.stichtag,
                    hinweis = hinweis(true, wer, z), hinweisEn = hinweis(false, wer, z),
                )
            }
        }

        // Vorwaerts: die Firma selbst nennt Kunden (auch Firmen ausserhalb des Katalogs und des Landes)
        val meine = (eigeneCik?.let { d.nachCik[it] }.orEmpty() + firma.symbole.flatMap { s -> d.zeilen.filter { it.symbol.isNotEmpty() && Netz.symbolSchluessel(it.symbol) == Netz.symbolSchluessel(s) } }).distinct()
        val juengste = HashMap<String, NetzZulieferer.Zeile>()
        for (z in meine) {
            val k = NetzZulieferer.schluessel(z.kunde)
            if (k.isEmpty()) continue
            val alt = juengste[k]
            if (alt == null || z.stichtag > alt.stichtag) juengste[k] = z
        }
        for (z in juengste.values.sortedByDescending { it.prozent }.take(MAX_KUNDEN_DATEI)) {
            val ziel = knotenFuer(graph, z.kunde, "name:")
            if (ziel.id == firma.id) continue
            neu(ziel)
            kanten += kante(
                "sec:${firma.id}:${ziel.id}", firma.id, ziel.id, Netz.Art.LIEFERT, umfang(z), Netz.Guete.B, quelle(z), z.stichtag,
                hinweis = hinweis(true, firma.name, z), hinweisEn = hinweis(false, firma.name, z),
            )
        }
        return Zusatz(knoten, kanten.distinctBy { it.id })
    }

    // Name ohne Gattung ("Class A", "Series B") in Kleinbuchstaben und ohne Satzzeichen: dieselbe Firma mit mehreren Kuerzeln hat denselben Namen
    private fun gattungslos(name: String): String =
        name.replace(Regex("(?i),?\\s+(?:class|cl|series|ser)\\.?\\s+\\w\\.?\\s*$"), "").lowercase().filter { it.isLetterOrDigit() }

    // Anzeigename eines Zulieferers: Katalogname zum Kuerzel, sonst der Name der SEC lesbar und ohne Rechtsform
    private fun zuliefererName(z: NetzZulieferer.Zeile, katalog: List<Pair<String, String>>): String {
        if (z.symbol.isNotEmpty()) katalog.firstOrNull { Netz.symbolSchluessel(it.second) == Netz.symbolSchluessel(z.symbol) }?.let { return it.first }
        val roh = Edgar13f.lesbar(z.name.replace(Regex("\\s*/[A-Za-z]{2,3}/?$"), "").trim())
        // Schreibweise aus dem Katalog, wenn der Name dort unter einem anderen Kuerzel steht („STMicroelectronics“ statt „Stmicroelectronics“)
        val schl = NetzZulieferer.schluesselVon(z.name)
        katalog.firstOrNull { (name, _) -> NetzZulieferer.schluesselVon(name).any { it in schl } }?.let { return it.first }
        return Firmenname.bereinigt(roh).ifEmpty { roh }
    }

    // ------------------------------------------------------------ GLEIF: Mutter und Toechter

    class GleifSatz(val lei: String, val name: String, val land: String)

    private fun gleifQuelle(lei: String) = Netz.Quelle(NetzTexte.text(true, "quelle.gleif.satz"), "https://search.gleif.org/#/record/$lei", true)

    // Datensaetze einer Liste (direct-children): Kennung, Name, Land
    fun gleifListe(text: String): List<GleifSatz> = runCatching {
        json.parseToJsonElement(text).jsonObject["data"]!!.jsonArray.mapNotNull { e ->
            val a = (e as? JsonObject)?.get("attributes")?.jsonObject ?: return@mapNotNull null
            val lei = a["lei"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val en = a["entity"]?.jsonObject ?: return@mapNotNull null
            val name = en["legalName"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            GleifSatz(lei, name, en["legalAddress"]?.jsonObject?.get("country")?.jsonPrimitive?.contentOrNull.orEmpty())
        }
    }.getOrDefault(emptyList())

    fun gleifKinderAdresse(lei: String) = "${Gleif.BASIS}/lei-records/$lei/direct-children?page%5Bsize%5D=$MAX_TOECHTER"

    // Ausgeschriebene Rechtsformen aus dem GLEIF-Register in ihrer ueblichen Kurzform
    private val RECHTSFORMEN = listOf(
        "sp\u00f3\u0142ka z ograniczon\u0105 odpowiedzialno\u015bci\u0105" to "Sp. z o.o.", "sp\u00f3\u0142ka akcyjna" to "S.A.",
        "gesellschaft mit beschr\u00e4nkter haftung" to "GmbH", "aktiengesellschaft" to "AG", "kommanditgesellschaft" to "KG",
        "societ\u00e0 a responsabilit\u00e0 limitata" to "S.r.l.", "societ\u00e0 per azioni" to "S.p.A.",
        "sociedad limitada" to "S.L.", "sociedad an\u00f3nima" to "S.A.", "besloten vennootschap" to "B.V.", "naamloze vennootschap" to "N.V.",
        "soci\u00e9t\u00e9 par actions simplifi\u00e9e" to "SAS", "soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e" to "SARL", "soci\u00e9t\u00e9 anonyme" to "SA",
    ).map { (lang, kurz) -> Regex("(?iu)(?<![\\p{L}])" + lang + "(?![\\p{L}])") to kurz }

    // Name aus dem GLEIF-Register: lesbar statt Grossbuchstaben, Rechtsform kurz, Woerter aus dem Namen der Firma in deren
    // Schreibweise ("RWE Investco" statt "Rwe Investco")
    internal fun gleifName(roh: String, firma: String): String {
        var n = Edgar13f.lesbar(roh.trim())
        for ((r, kurz) in RECHTSFORMEN) n = r.replace(n, kurz)
        for (w in firma.split(' ', '-').filter { it.length >= 2 && it.any { c -> c.isLetter() } }) {
            n = Regex("(?iu)(?<![\\p{L}])" + Regex.escape(w) + "(?![\\p{L}])").replace(n) { w }
        }
        return n
    }

    // Mutter (Konsolidierung, ueber 50 %) und Toechter einer Firma; gleiche Namen wie die Firma selbst fallen weg
    fun gleif(firma: Netz.Knoten, eigeneLei: String?, mutter: Gleif.Mutter?, kinder: List<GleifSatz>, graph: Netz.Graph, stand: String): Zusatz {
        val knoten = ArrayList<Netz.Knoten>()
        val kanten = ArrayList<Netz.Kante>()
        if (mutter != null && !gleicherName(mutter.name, firma.name)) {
            val m = graph.knotenFuerKennung(lei = mutter.lei) ?: graph.knotenFuerName(mutter.name) ?: Netz.Knoten("lei:${mutter.lei}", Netz.Typ.FIRMA, gleifName(mutter.name, firma.name), lei = mutter.lei)
            if (m.id != firma.id) {
                if (graph.knoten[m.id] == null) knoten += m
                kanten += kante("gleif:${m.id}:${firma.id}", m.id, firma.id, Netz.Art.TOCHTER, null, Netz.Guete.A, gleifQuelle(mutter.lei), stand)
            }
        }
        for (k in kinder.take(MAX_TOECHTER)) {
            if (k.lei == eigeneLei || gleicherName(k.name, firma.name)) continue
            val n = graph.knotenFuerKennung(lei = k.lei) ?: graph.knotenFuerName(k.name) ?: Netz.Knoten("lei:${k.lei}", Netz.Typ.FIRMA, gleifName(k.name, firma.name), lei = k.lei)
            if (n.id == firma.id) continue
            if (graph.knoten[n.id] == null && knoten.none { it.id == n.id }) knoten += n
            kanten += kante("gleif:${firma.id}:${n.id}", firma.id, n.id, Netz.Art.TOCHTER, null, Netz.Guete.A, gleifQuelle(k.lei), stand)
        }
        return Zusatz(knoten, kanten.distinctBy { it.id })
    }

    // ------------------------------------------------------------ Wikidata

    fun wikidataAdresse(q: String) = "https://www.wikidata.org/wiki/Special:EntityData/$q.json"

    class WdVerweis(val ziel: String, val anteil: Double?)

    class WdAngaben(
        val eigentuemer: List<WdVerweis>, val mutter: List<WdVerweis>, val toechter: List<WdVerweis>, val besitzt: List<WdVerweis>,
        val mandate: List<WdVerweis> = emptyList(),
    ) {
        val ids: List<String> get() = (eigentuemer + mutter + toechter + besitzt + mandate).map { it.ziel }.distinct()
    }

    private fun verweise(claims: JsonObject, prop: String): List<WdVerweis> =
        (claims[prop] as? JsonArray).orEmpty().mapNotNull { c ->
            val o = c as? JsonObject ?: return@mapNotNull null
            // Aussagen mit Endzeitpunkt (P582) gelten nicht mehr
            if ((o["qualifiers"] as? JsonObject)?.containsKey("P582") == true) return@mapNotNull null
            val ziel = (((o["mainsnak"] as? JsonObject)?.get("datavalue") as? JsonObject)?.get("value") as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            // P1107: Anteil als Bruchteil (0 bis 1)
            val anteil = (((o["qualifiers"] as? JsonObject)?.get("P1107") as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.let { (it["datavalue"] as? JsonObject)?.get("value") as? JsonObject }?.get("amount")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            WdVerweis(ziel, anteil?.takeIf { it > 0.0 && it <= 1.0 })
        }

    // Eigentuemer (P127), Mutter (P749), Toechter (P355) und Beteiligungen (P1830) aus der Datei eines Eintrags
    fun wikidataLesen(text: String, q: String): WdAngaben? = runCatching {
        val claims = json.parseToJsonElement(text).jsonObject["entities"]!!.jsonObject[q]!!.jsonObject["claims"]!!.jsonObject
        WdAngaben(
            verweise(claims, "P127"), verweise(claims, "P749"), verweise(claims, "P355").take(MAX_WIKIDATA), verweise(claims, "P1830").take(MAX_WIKIDATA),
            verweise(claims, "P3320").take(MAX_WIKIDATA),
        )
    }.getOrNull()

    // Kanten aus den Angaben; namen: Beschriftungen der Verweise (Kennung zu Name). Eigentuemer (P127) kommen aus dem Dienst fuer
    // Eigentuemer und Rechte, nicht von hier. Knoten gleichen zuerst ueber die Wikidata-Kennung ab, Personen nur darueber.
    fun wikidata(firma: Netz.Knoten, q: String, a: WdAngaben, namen: Map<String, String>, graph: Netz.Graph, stand: String): Zusatz {
        val knoten = ArrayList<Netz.Knoten>()
        val kanten = ArrayList<Netz.Kante>()
        fun knotenZu(id: String): Netz.Knoten? {
            val name = namen[id] ?: return null
            val n = graph.knotenFuerKennung(wikidata = id) ?: graph.knotenFuerName(name) ?: Netz.Knoten("wd:$id", Netz.Typ.FIRMA, name, wikidata = id)
            if (n.id == firma.id) return null
            if (graph.knoten[n.id] == null && knoten.none { it.id == n.id }) knoten += n
            return n
        }
        fun quelle(id: String) = Netz.Quelle(NetzTexte.text(true, "quelle.wikidata.satz"), "https://www.wikidata.org/wiki/$id", false)
        fun anteil(v: WdVerweis) = v.anteil?.let { Netz.Umfang(it * 100.0, Netz.Einheit.PCT, Netz.Bezug.KAPITAL) }
        for (v in a.mutter) { val n = knotenZu(v.ziel) ?: continue; kanten += kante("wd:${n.id}:${firma.id}:t", n.id, firma.id, Netz.Art.TOCHTER, null, Netz.Guete.C, quelle(q), stand) }
        for (v in a.toechter) { val n = knotenZu(v.ziel) ?: continue; kanten += kante("wd:${firma.id}:${n.id}:t", firma.id, n.id, Netz.Art.TOCHTER, null, Netz.Guete.C, quelle(q), stand) }
        for (v in a.besitzt) { val n = knotenZu(v.ziel) ?: continue; kanten += kante("wd:${firma.id}:${n.id}:h", firma.id, n.id, Netz.Art.HAELT_ANTEIL, anteil(v), Netz.Guete.C, quelle(q), stand) }
        // Board-Mitglieder (P3320): nie ueber den Namen mit anderen Personen zusammengefuehrt, nur ueber die Wikidata-Kennung
        for (v in a.mandate) {
            val name = namen[v.ziel] ?: continue
            val p = graph.knotenFuerKennung(wikidata = v.ziel) ?: Netz.Knoten("wd:${v.ziel}", Netz.Typ.PERSON, name, wikidata = v.ziel)
            if (graph.knoten[p.id] == null && knoten.none { it.id == p.id }) knoten += p
            kanten += kante("wd:${p.id}:${firma.id}:m", p.id, firma.id, Netz.Art.MANDAT, null, Netz.Guete.C, quelle(q), stand)
        }
        return Zusatz(knoten, kanten.distinctBy { it.id })
    }

    // Board-Mitglieder aus Wikidata, die schon als Director aus Form 4 an derselben Firma haengen: gleicher Nachname und gleicher
    // erster Buchstabe des Vornamens ("Tim Cook" und "Timothy D Cook"). Nur innerhalb einer Firma, sonst bleiben gleiche Namen getrennt.
    fun ohneDoppelteMandate(firma: Netz.Knoten, form4: Zusatz, wd: Zusatz, graph: Netz.Graph): Zusatz {
        fun schluessel(name: String): String? {
            val w = name.replace(Regex("[.,]"), " ").split(' ').filter { it.isNotBlank() }
                .filterNot { it.lowercase() in setOf("jr", "sr", "ii", "iii", "iv", "dr", "mr", "ms", "mrs") }
            if (w.size < 2) return null
            return w.last().lowercase() + "|" + w.first().first().lowercaseChar()
        }
        val personen = form4.knoten.filter { it.typ == Netz.Typ.PERSON }.associateBy { it.id }
        val vorhanden = form4.kanten.filter { it.art == Netz.Art.MANDAT && it.nach == firma.id }
            .mapNotNull { (personen[it.von] ?: graph.knoten[it.von])?.name?.let(::schluessel) }.toSet()
        if (vorhanden.isEmpty()) return wd
        // Wikidata-Personen ueber ihre Mandatskanten an dieser Firma, auch wenn der Knoten schon im Netz steht
        val wdPersonen = wd.knoten.associateBy { it.id }
        val weg = wd.kanten.filter { it.art == Netz.Art.MANDAT && it.nach == firma.id }
            .map { it.von }.filter { id -> (wdPersonen[id] ?: graph.knoten[id])?.let { it.typ == Netz.Typ.PERSON && schluessel(it.name) in vorhanden } == true }
            .toSet()
        if (weg.isEmpty()) return wd
        return Zusatz(wd.knoten.filter { it.id !in weg }, wd.kanten.filter { !(it.von in weg && it.nach == firma.id && it.art == Netz.Art.MANDAT) })
    }

    // ------------------------------------------------------------ SEC: Anlage 21 (Tochterliste)

    private val FIRMENFORM = Regex(
        "(?i)(\\b(inc|incorporated|llc|l\\.l\\.c|ltd|limited|gmbh|b\\.v|bv|s\\.a|sa|ag|plc|corp|corporation|co|kk|k\\.k|s\\.r\\.l|srl|pte|pty|oy|ab|nv|n\\.v|sarl|s\\.a\\.s|lp|l\\.p|ulc|kft|spa|s\\.p\\.a)\\b\\.?|a/s)\\s*$",
    )

    // Namen aus dem HTML der Anlage 21: Zellen mit einer Rechtsform am Ende, ohne Wiederholung
    fun exhibit21Namen(html: String): List<String> {
        val text = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("<[^>]+>"), "\u0001")
            .replace("&amp;", "&").replace("&nbsp;", " ").replace("&#160;", " ").replace("&#8217;", "'").replace("&apos;", "'")
        val aus = LinkedHashMap<String, String>()
        for (teil in text.split('\u0001')) {
            val s = teil.replace(Regex("\\s+"), " ").trim().trimEnd(',', ';')
            if (s.length < 4 || s.length > 70 || !FIRMENFORM.containsMatchIn(s)) continue
            if (s.contains("subsidiar", true) || s.contains("jurisdiction", true) || s.contains("exhibit", true)) continue
            aus.putIfAbsent(Netz.nameKern(s), s)
        }
        return aus.values.take(MAX_TOECHTER)
    }

    // Datei der Anlage 21 im Verzeichnis einer Einreichung (index.json)
    fun exhibit21Datei(indexJson: String): String? = runCatching {
        json.parseToJsonElement(indexJson).jsonObject["directory"]!!.jsonObject["item"]!!.jsonArray
            .mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
            .firstOrNull { Regex("(?i)(^|[^0-9])(ex|exhibit)[-_]?0?21(?:[-_.]?0?1)?([^0-9]|$)").containsMatchIn(it) && (it.endsWith(".htm", true) || it.endsWith(".html", true)) }
    }.getOrNull()

    // Toechter aus Anlage 21: nur Namen, nie ueber den Namen an boersennotierte Knoten geheftet (die Kennung enthaelt die Mutter)
    fun exhibit21(firma: Netz.Knoten, namen: List<String>, quellUrl: String, graph: Netz.Graph, stand: String): Zusatz {
        val knoten = ArrayList<Netz.Knoten>()
        val kanten = ArrayList<Netz.Kante>()
        val q = Netz.Quelle(NetzTexte.text(true, "quelle.sec.ex21"), quellUrl, true)
        for (name in namen) {
            if (gleicherName(name, firma.name)) continue
            val n = Netz.Knoten("ex21:${firma.id}:${slug(name)}", Netz.Typ.FIRMA, name)
            if (graph.knoten[n.id] == null && knoten.none { it.id == n.id }) knoten += n
            kanten += kante("ex21:${firma.id}:${n.id}", firma.id, n.id, Netz.Art.TOCHTER, null, Netz.Guete.B, q, stand)
        }
        return Zusatz(knoten, kanten.distinctBy { it.id })
    }

    // ------------------------------------------------------------ Halter aus dem Dienst fuer Eigentuemer und Rechte

    // Halter mit Anteil und Beleg aus Struktur.Ergebnis (SEC, gepflegte Faelle, Institutionen). Nur Halter, deren Beleg auf ein
    // einzelnes Dokument zeigt. Anteil am Kapital, an den Stimmen oder (13G) an der Gattung, je so beschriftet. Personen werden
    // nie ueber den Namen mit anderen Knoten zusammengefuehrt.
    fun halter(firma: Netz.Knoten, e: Struktur.Ergebnis, graph: Netz.Graph, stand: String): Zusatz {
        val knoten = ArrayList<Netz.Knoten>()
        val kanten = ArrayList<Netz.Kante>()
        for (h in e.halter) {
            if (h.name.isBlank() || h.art in setOf(Struktur.Art.STREUBESITZ, Struktur.Art.INSTITUTIONEN, Struktur.Art.GRUPPE, Struktur.Art.MUTTER)) continue
            val b = e.belege.getOrNull(h.beleg) ?: continue
            val url = b.url ?: continue
            if (!Netz.einzeldokument(url)) continue
            val typ = when (h.art) {
                Struktur.Art.PERSON, Struktur.Art.FAMILIE -> Netz.Typ.PERSON
                Struktur.Art.STAAT -> Netz.Typ.STAAT
                Struktur.Art.STIFTUNG, Struktur.Art.FONDS, Struktur.Art.TRUST -> Netz.Typ.FONDS
                else -> Netz.Typ.FIRMA
            }
            val knotenH = (if (typ == Netz.Typ.PERSON) null else graph.knotenFuerName(h.name))
                ?: Netz.Knoten("halter:${firma.id}:${slug(h.name)}", typ, h.name)
            if (knotenH.id == firma.id) continue
            if (graph.knoten[knotenH.id] == null && knoten.none { it.id == knotenH.id }) knoten += knotenH
            val quelle = Netz.Quelle(b.name, url, true)
            val tag = b.stand.takeIf { it.length == 10 } ?: stand
            fun neu(kennzeichen: String, wert: Double, bezug: Netz.Bezug) {
                if (wert <= 0.0 || wert > 100.0) return
                kanten += kante("st:${knotenH.id}:${firma.id}:$kennzeichen", knotenH.id, firma.id, Netz.Art.HAELT_ANTEIL, Netz.Umfang(wert, Netz.Einheit.PCT, bezug), Netz.Guete.B, quelle, tag)
            }
            h.kapital?.let { neu("k", it, Netz.Bezug.KAPITAL) }
            h.stimmen?.let { neu("s", it, Netz.Bezug.STIMMEN) }
            if (h.kapital == null && h.stimmen == null) h.klasse?.let { neu("g", it, Netz.Bezug.KLASSE) }
        }
        return Zusatz(knoten, kanten.distinctBy { it.id })
    }

    // Gleicher voller Name (nie leer)
    private fun gleicherName(a: String, b: String): Boolean = Netz.nameVoll(a).let { it.isNotEmpty() && it == Netz.nameVoll(b) }

    // Seite der Einreichung selbst (Filing-Index), nicht die Firmenuebersicht
    fun einreichungsseite(cik: String, acc: String): String = "https://www.sec.gov/Archives/edgar/data/${cik.trimStart('0')}/${acc.replace("-", "")}/$acc-index.htm"

    // ------------------------------------------------------------ SEC: Wettbewerber (10-K, Item 1, Abschnitt Competition)

    private val WETTBEWERB_KOPF = Regex("(?<![A-Za-z])(Competition|Competitors)(?![A-Za-z])")

    // Woerter, die als Firmenname vorkommen, im Text aber meist etwas anderes bedeuten
    private val GEMEINE_NAMEN = setOf("target", "block", "match", "square", "general", "american", "national", "first", "global", "united", "southern", "western", "pioneer", "sun", "gap", "visa", "unity", "shell", "ball", "ross", "fox", "coach", "chase", "discover", "apple", "oracle")

    const val MAX_WETTBEWERBER = 12

    // Wie die Konzerne sich selbst nennen: Wort im Text zum Kuerzel der Firma
    private val ALIASE = mapOf("Google" to "GOOGL", "Azure" to "MSFT", "Facebook" to "META", "Amazon" to "AMZN", "Microsoft" to "MSFT", "Nvidia" to "NVDA", "NVIDIA" to "NVDA")

    // Text hinter den Ueberschriften "Competition" und "Competitors" (hoechstens drei Fenster zu je 2500 Zeichen)
    fun wettbewerbAbschnitt(klar: String): String {
        val fenster = WETTBEWERB_KOPF.findAll(klar).map { m -> klar.substring(m.range.last + 1, minOf(klar.length, m.range.last + 2501)) }
            .filter { it.contains("compet", ignoreCase = true) }.take(3).toList()
        return fenster.joinToString("\n")
    }

    // Genannte Firmen im Abschnitt: Name (bereinigt, mindestens vier Zeichen) kommt als ganzes Wort vor und gehoert genau einer
    // Firma des Netzes oder des Katalogs (Name zu Kuerzel); mehrdeutige Namen fallen weg.
    fun wettbewerber(firma: Netz.Knoten, abschnitt: String, graph: Netz.Graph, katalog: List<Pair<String, String>>, quellUrl: String, stand: String): Zusatz {
        if (abschnitt.isBlank()) return LEER
        val je = HashMap<String, MutableMap<String, Netz.Knoten>>()
        fun merke(name: String, k: Netz.Knoten) {
            val n = Firmenname.bereinigt(name)
            if (n.length >= 4 && n.lowercase() !in GEMEINE_NAMEN && abschnitt.contains(n)) je.getOrPut(n) { HashMap() }[k.id] = k
        }
        for (k in graph.knoten.values) if (k.typ == Netz.Typ.FIRMA) merke(k.name, k)
        for ((wort, sym) in ALIASE) {
            val k = graph.knotenFuerSymbol(sym) ?: continue
            if (abschnitt.contains(wort)) je.getOrPut(wort) { HashMap() }[k.id] = k
        }
        for ((name, sym) in katalog) {
            val k = graph.knotenFuerSymbol(sym) ?: Netz.Knoten(sym, Netz.Typ.FIRMA, name, symbole = listOf(sym))
            merke(name, k)
        }
        val knoten = ArrayList<Netz.Knoten>()
        val kanten = ArrayList<Netz.Kante>()
        val quelle = Netz.Quelle(NetzTexte.text(true, "quelle.sec.wettbewerb"), quellUrl, true)
        for ((n, kandidaten) in je) {
            val k = kandidaten.values.singleOrNull() ?: continue
            if (k.id == firma.id || !Regex("(?<![A-Za-z])" + Regex.escape(n) + "(?![A-Za-z])").containsMatchIn(abschnitt)) continue
            if (graph.knoten[k.id] == null && knoten.none { it.id == k.id }) knoten += k
            val a = if (firma.id < k.id) firma.id else k.id
            val b = if (firma.id < k.id) k.id else firma.id
            kanten += kante("wb:$a:$b", a, b, Netz.Art.KONKURRIERT, null, Netz.Guete.C, quelle, stand)
            if (kanten.size >= MAX_WETTBEWERBER) break
        }
        return Zusatz(knoten, kanten.distinctBy { it.id })
    }

    // ------------------------------------------------------------ SEC: Board-Mitglieder (Form 4, Rolle Director)

    class Form4Treffer(val besitzerCik: String, val adsh: String, val datei: String, val eingang: String)

    fun form4Adresse(emittentCik: String, ab: String, bis: String): String =
        "https://efts.sec.gov/LATEST/search-index?q=&forms=4&ciks=" + emittentCik.trimStart('0').padStart(10, '0') + "&dateRange=custom&startdt=$ab&enddt=$bis"

    // Je Meldender (nicht der Emittent) die juengste Meldung aus der Trefferliste
    fun form4Treffer(suche: String, emittentCik: String, hoechstens: Int = MAX_HALTER): List<Form4Treffer> = runCatching {
        val emittent = emittentCik.trimStart('0')
        val hits = json.parseToJsonElement(suche).jsonObject["hits"]!!.jsonObject["hits"]!!.jsonArray
        val je = LinkedHashMap<String, Form4Treffer>()
        for (h in hits) {
            val o = h as? JsonObject ?: continue
            val q = o["_source"]?.jsonObject ?: continue
            val id = o["_id"]?.jsonPrimitive?.contentOrNull ?: continue
            val adsh = id.substringBefore(':')
            val datei = id.substringAfter(':', "").substringAfterLast('/')
            if (datei.isEmpty() || !datei.endsWith(".xml", true)) continue
            val ciks = q["ciks"]?.jsonArray?.map { it.jsonPrimitive.content.trimStart('0') }.orEmpty()
            if (emittent !in ciks) continue
            val eingang = q["file_date"]?.jsonPrimitive?.contentOrNull.orEmpty()
            for (c in ciks) {
                if (c == emittent) continue
                val alt = je[c]
                if (alt == null || eingang > alt.eingang) je[c] = Form4Treffer(c, adsh, datei, eingang)
            }
        }
        je.values.sortedByDescending { it.eingang }.take(hoechstens)
    }.getOrDefault(emptyList())

    fun form4Dokument(emittentCik: String, t: Form4Treffer): String =
        "https://www.sec.gov/Archives/edgar/data/${emittentCik.trimStart('0')}/${t.adsh.replace("-", "")}/${t.datei}"

    class Direktor(val name: String, val rolle: String)

    private val IST_DIREKTOR = Regex("<isDirector>\\s*(1|true)\\s*</isDirector>", RegexOption.IGNORE_CASE)
    private val EIGNER_NAME = Regex("<rptOwnerName>\\s*([^<]+?)\\s*</rptOwnerName>")
    private val AMTSTITEL = Regex("<officerTitle>\\s*([^<]+?)\\s*</officerTitle>")

    // Name und Rolle, wenn die Meldung einen Director nennt ("Last First Middle" wird "First Middle Last"); sonst null
    fun direktor(xml: String): Direktor? {
        if (!IST_DIREKTOR.containsMatchIn(xml)) return null
        val roh = EIGNER_NAME.find(xml)?.groupValues?.get(1)?.replace("&amp;", "&") ?: return null
        val teile = Edgar13f.lesbar(roh).split(' ').filter { it.isNotEmpty() }
        if (teile.isEmpty()) return null
        val name = (teile.drop(1) + teile.first()).joinToString(" ")
        val titel = AMTSTITEL.find(xml)?.groupValues?.get(1)?.replace("&amp;", "&")
        return Direktor(name, if (titel.isNullOrBlank()) "Director" else "Director, $titel")
    }

    // Personen nur ueber die CIK des Meldenden (Form 4): zwei Menschen gleichen Namens bleiben zwei Knoten
    fun direktoren(firma: Netz.Knoten, liste: List<Triple<Form4Treffer, Direktor, String>>, graph: Netz.Graph, stand: String): Zusatz {
        val knoten = ArrayList<Netz.Knoten>()
        val kanten = ArrayList<Netz.Kante>()
        for ((t, d, url) in liste) {
            val p = graph.knotenFuerKennung(cik = t.besitzerCik)?.takeIf { it.typ == Netz.Typ.PERSON }
                ?: Netz.Knoten("person:${t.besitzerCik}", Netz.Typ.PERSON, d.name, cik = t.besitzerCik)
            if (graph.knoten[p.id] == null && knoten.none { it.id == p.id }) knoten += p
            kanten += kante(
                "f4:${p.id}:${firma.id}", p.id, firma.id, Netz.Art.MANDAT, null, Netz.Guete.B,
                Netz.Quelle(NetzTexte.text(true, "quelle.sec.form4"), url, true), stand, seit = null, rolle = d.rolle,
            )
        }
        return Zusatz(knoten, kanten.distinctBy { it.id })
    }

    // ------------------------------------------------------------ USAspending: Zuschuesse und Kredite (Anfrage und Leser in Behoerden)

    // Je Zuschuss oder Kredit ueber einer Million Dollar eine Kante mit Link auf die Vergabe; nur Empfaenger, die zur Firma passen
    // (Behoerden.passtEmpfaenger). Die Behoerde ist ein Staatsknoten.
    fun zuschuesse(firma: Netz.Knoten, antworten: List<String>, graph: Netz.Graph, stand: String): Zusatz {
        val stamm = Behoerden.stamm(firma.name)
        val vergaben = antworten.flatMap { Behoerden.auftraegeListeLesen(it) }
            .filter { it.betrag >= 1_000_000.0 && it.intern.isNotBlank() && Behoerden.passtEmpfaenger(it.empfaenger, stamm) }
            .distinctBy { it.intern }.sortedByDescending { it.betrag }.take(4)
        val knoten = ArrayList<Netz.Knoten>()
        val kanten = ArrayList<Netz.Kante>()
        for (a in vergaben) {
            val n = Netz.Knoten("usa:${slug(a.behoerde)}", Netz.Typ.STAAT, "${a.behoerde} (USA)", "${a.behoerde} (USA)")
            if (graph.knoten[n.id] == null && knoten.none { it.id == n.id }) knoten += n
            val quelle = Netz.Quelle(NetzTexte.text(true, "quelle.usaspending") + ", " + a.id, Behoerden.auftragAdresse(a.intern), true)
            val beschreibung = a.beschreibung.trim().takeIf { it.isNotEmpty() }
            kanten += kante(
                "usa:${a.intern}", n.id, firma.id, Netz.Art.STAAT_FINANZIERT, Netz.Umfang(a.betrag, Netz.Einheit.USD), Netz.Guete.A, quelle,
                a.beginn.takeIf { it.length >= 10 }?.take(10) ?: stand, seit = a.beginn.takeIf { it.length >= 10 }?.take(10), status = Netz.Status.ZUGESAGT,
                programm = beschreibung ?: NetzTexte.text(true, "programm.usaspending"), programmEn = beschreibung ?: NetzTexte.text(false, "programm.usaspending"),
            )
        }
        return Zusatz(knoten, kanten)
    }

    // ------------------------------------------------------------ Zwischenspeicher: Zusatz als Text (Format des Buendels)

    private fun umfangJson(u: Netz.Umfang) = buildJsonObject {
        put("wert", u.wert); put("einheit", u.einheit.name)
        u.bezug?.let { put("bezug", it.name) }
        if (u.hoechstens) put("hoechstens", true)
    }

    // Schreibt Zusatz samt der Knoten, die seine Kanten brauchen (benoetigte: Knoten aus dem Netz), im Format des Buendels
    fun schreiben(z: Zusatz, benoetigte: Collection<Netz.Knoten>): String {
        val alle = (benoetigte + z.knoten).distinctBy { it.id }
        val o = buildJsonObject {
            put("schema", 1)
            put("stand", "")
            put("knoten", buildJsonArray {
                for (k in alle) add(buildJsonObject {
                    put("id", k.id); put("typ", k.typ.schluessel); put("name", k.name)
                    k.nameEn?.let { put("nameEn", it) }
                    if (k.symbole.isNotEmpty()) put("symbole", buildJsonArray { k.symbole.forEach { add(JsonPrimitive(it)) } })
                    k.cik?.let { put("cik", it) }; k.lei?.let { put("lei", it) }; k.wikidata?.let { put("wikidata", it) }
                })
            })
            put("kanten", buildJsonArray {
                for (e in z.kanten) add(buildJsonObject {
                    put("id", e.id); put("von", e.von); put("nach", e.nach); put("art", e.art.schluessel)
                    e.umfang?.let { put("umfang", umfangJson(it)) }
                    e.seit?.let { put("seit", it) }
                    e.rolle?.let { put("rolle", it) }; e.programm?.let { put("programm", it) }; e.programmEn?.let { put("programmEn", it) }
                    e.hinweis?.let { put("hinweis", it) }; e.hinweisEn?.let { put("hinweisEn", it) }
                    put("status", e.status.schluessel); put("guete", e.guete.name); put("stand", e.stand)
                    put("quelle", buildJsonObject { put("name", e.quelle.name); put("url", e.quelle.url); put("primaer", e.quelle.primaer) })
                })
            })
        }
        return o.toString()
    }

    // Liest einen gespeicherten Zusatz; nur die Knoten, die das Netz noch nicht kennt, kommen in den Zusatz
    fun lesen(text: String, graph: Netz.Graph): Zusatz? {
        val r = Netz.lesen(text, Netz.Herkunft.GEMELDET) ?: return null
        return Zusatz(r.graph.knoten.values.filter { graph.knoten[it.id] == null }, r.graph.kanten)
    }
}
