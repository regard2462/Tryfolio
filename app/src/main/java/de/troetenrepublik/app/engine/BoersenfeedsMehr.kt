package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Boersenfeeds.Notierung
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Boersenwerte aus Zagreb, Ljubljana, Ho-Chi-Minh-Stadt, Hanoi (HNX, UPCoM), Zypern, Trinidad, Baku, Lima und Kasachstan
// (Globus-Laenderblatt). Je Leser eine echte Antwort der Boerse. Mehrstufige Quellen (Aktienzahl und Kurs getrennt) rechnen
// Kurs mal Aktienzahl; die Aktienzahlen speichert die App einen Monat, Kurse einen Tag. Notierung.symbol ist "<Boerse>:<Kuerzel>",
// damit Kandidaten ueber ihre Kennung (gleiche Schreibweise) zugeordnet werden koennen.
object BoersenfeedsMehr {
    // Aktienzahl einer Firma; name schon von Rechtsformen befreit
    data class Aktie(val code: String, val name: String, val anzahl: Double)

    // Kurs aus Hanoi mit englischem Namen, soweit die Zeile ihn nennt
    data class HnxKurs(val code: String, val preis: Double, val name: String)

    // Kurs eines Papiers an der Boerse Lima (waehrung ISO-Code), umsatz = Handelsumsatz des Tages als Rangmass
    data class PeruKurs(val firma: String, val nemonico: String, val name: String, val preis: Double, val waehrung: String, val umsatz: Double, val segment: String = "")

    // Aktien eines Emittenten in Lima: Aktienzahl je Kuerzel des Papiers
    data class PeruEmittent(val firma: String, val name: String, val aktien: Map<String, Double>)

    val ZAGREB = Boersenfeeds.Quelle("zse", "Zagreb Stock Exchange", setOf("HR"), "", false, false, mehrstufig = true)
    val LJUBLJANA = Boersenfeeds.Quelle("ljse", "Ljubljana Stock Exchange", setOf("SI"), "", false, false, mehrstufig = true)
    val HOSE = Boersenfeeds.Quelle("hose", "Ho Chi Minh Stock Exchange", setOf("VN"), "", false, false, mehrstufig = true)
    val HNX = Boersenfeeds.Quelle("hnx", "Hanoi Stock Exchange", setOf("VN"), "", false, false, mehrstufig = true)
    val UPCOM = Boersenfeeds.Quelle("upcom", "UPCoM", setOf("VN"), "", false, false, mehrstufig = true)
    val ZYPERN = Boersenfeeds.Quelle("cse-cy", "Cyprus Stock Exchange", setOf("CY"), "https://www.cse.com.cy/en-GB/regulated-market/market-indices/current-prices/market-capitalization/", false, true)
    val TRINIDAD = Boersenfeeds.Quelle("ttse", "Trinidad and Tobago Stock Exchange", setOf("TT"), "https://www.stockex.co.tt/values-and-index-summary/", false, true)
    val BAKU = Boersenfeeds.Quelle("bfb", "Baku Stock Exchange", setOf("AZ"), "https://www.bfb.az/en/market-watch", false, true)
    val LIMA = Boersenfeeds.Quelle("bvl", "Bolsa de Valores de Lima", setOf("PE"), "", false, false, mehrstufig = true)
    val KASE = Boersenfeeds.Quelle("kase", "Kazakhstan Stock Exchange", setOf("KZ"), "https://kase.kz/en/indexes-and-indicators/shares/kase-index", false, true)
    val ALLE = listOf(ZAGREB, LJUBLJANA, HOSE, HNX, UPCOM, ZYPERN, TRINIDAD, BAKU, LIMA, KASE)

    // Adressen der mehrstufigen Quellen
    private const val ZSE_PFAD = "https://rest.zse.hr/web/Bvt9fe2peQ7pwpyYqODM/"
    private const val LJSE_PFAD = "https://rest.ljse.si/web/Bvt9fe2peQ7pwpyYqODM/"
    fun zseWertpapiere() = ZSE_PFAD + "securities/XZAG/json"
    fun zsePreisliste(datum: String) = ZSE_PFAD + "price-list/XZAG/$datum/json"
    fun ljseWertpapiere() = LJSE_PFAD + "securities/XLJU/json"
    fun ljsePreisliste(datum: String) = LJSE_PFAD + "price-list/XLJU/$datum/json"
    fun hoseAktienAdresse(seite: Int) = "https://api.hsx.vn/l/api/v1/2/securities/stock?pageIndex=$seite&pageSize=50"
    fun hoseKurseAdresse(datum: String) = "https://api.hsx.vn/mk/api/v1/market/quote-report?tradingBy=VNINDEX&date=$datum"
    const val HNX_AKTIEN = "https://www.hnx.vn/ModuleIssuer/List/ListSearch_Datas"
    const val UPCOM_AKTIEN = "https://www.hnx.vn/ModuleIssuer/UC_Issuer/ListSearch_Datas"
    const val HNX_KURSE = "https://banggia.hnx.vn/Home/GetStockInBasket"
    val HNX_BUCHSTABEN = listOf("ABC", "DEF", "GHI", "JKL", "MNO", "PQR", "STUV", "YZ")
    const val LIMA_KURSE = "https://dataondemand.bvl.com.pe/v1/stock-quote/market"
    fun limaEmittent(firma: String) = "https://dataondemand.bvl.com.pe/v1/issuers/$firma"

    // Formularfelder der Emittentenliste in Hanoi; markt leer = HNX, "UC" = UPCoM
    fun hnxFormular(markt: String) = listOf(
        "p_issearch" to "0", "p_keysearch" to "", "p_market_code" to markt, "p_orderby" to "", "p_ordertype" to "",
        "p_currentpage" to "1", "p_record_on_page" to "1000",
    )

    fun hnxKursFormular(boerse: String, buchstaben: String) = listOf("pFloorCode" to boerse, "pKeyAreas" to buchstaben)

    private val json = Json { ignoreUnknownKeys = true }

    private fun zahl(s: String): Double? = s.replace(Regex("[\\s\\u00a0\\u202f,]"), "").toDoubleOrNull()

    // Zahl mit Komma als Dezimaltrenner und Leerzeichen als Tausendertrenner ("5 410,01")
    private fun zahlKomma(s: String): Double? = s.replace(Regex("[\\s\\u00a0\\u202f]"), "").replace(',', '.').toDoubleOrNull()

    private fun klartext(s: String): String = entitaeten(s.replace(Regex("<[^>]*>"), " ")).replace(Regex("[\\s\\u00a0]+"), " ").trim()

    fun entitaeten(s: String): String = Regex("&#(\\d+);").replace(s) { it.groupValues[1].toIntOrNull()?.takeIf { c -> c in 1..0x10FFFF }?.let { c -> String(Character.toChars(c)) } ?: "" }
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    private val RECHTSFORM = setOf("dd", "doo", "jsc", "ojsc", "cjsc", "saa", "sa", "sac", "plc", "ltd", "limited", "pcl", "ad", "public", "company")

    // Name ohne Rechtsform und Anhaengsel: "INA, d.d." wird "INA", "Integratel Peru S.A.A." wird "Integratel Peru", "(CR with VR_C)" faellt weg
    fun reinigen(name: String): String {
        var s = entitaeten(name).replace(Regex("\\(.*?\\)"), " ").replace("\"", "")
        s = s.replace(Regex("(?i)[,\\s]+d\\.\\s?[do]\\.?(o\\.?)?(\\s.*)?$"), "")
        s = s.replace(Regex("(?i)\\bjoint stock commercial bank\\b"), "Bank").replace(Regex("(?i)\\b(open )?joint stock company\\b"), " ")
        val teile = s.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        fun norm(t: String) = t.lowercase().replace(Regex("[.,]"), "")
        while (teile.size > 1 && norm(teile.last()) in RECHTSFORM) teile.removeAt(teile.lastIndex)
        while (teile.size > 1 && norm(teile.first()) == "the") teile.removeAt(0)
        return teile.joinToString(" ").trim(' ', ',', '-', '.')
    }

    // Branche der App aus Wortteilen des Namens (mehrsprachig); leer, wenn nichts passt
    private val BRANCHEN = listOf(
        "VERSICHERUNG" to Regex("insur|osiguranj|zavarovaln|assur|seguro|baohiem|bao viet|bao minh|pvi\\b"),
        "BANKEN" to Regex("bank|banka|banco|securities|finance|financial"),
        "TELEKOM" to Regex("telecom|telekom|kcell|telephone|viettel|fpt telecom"),
        "OEL_GAS" to Regex("petrol|oil|\\bgas\\b|naft|petrovietnam|janaf|\\bina\\b|refiner"),
        "VERSORGER" to Regex("electric|elektr|power|energy|energi|water|hydro|kegoc|\\bpv power"),
        "BERGBAU" to Regex("mining|mineral|steel|iron|hoa phat|atomprom|gold|copper|metal|rudnik|aluminium|aluminum"),
        "BAU" to Regex("cement|construction|gradnja|building|ximang|concrete"),
        "IMMOBILIEN" to Regex("real estate|property|properties|land\\b|vinhomes|nvl|vingroup|imobil|nekretnin"),
        "GASTGEWERBE" to Regex("hotel|touris|riviera|laguna|resort|turist|travel"),
        "PHARMA" to Regex("pharm|krka|farm|medic|drug"),
        "NAHRUNG" to Regex("food|dairy|milk|vinamilk|seafood|sugar|podravka|atlantic grupa|agro|vissan"),
        "ALKOHOL_TABAK" to Regex("beer|brew|tobacco|spirits|sabeco|habeco|angostura|west indian tobacco"),
        "VERKEHR" to Regex("airline|air astana|airport|port\\b|luka|shipping|logistic|transport|railway|vietjet|vietnam airlines|acv\\b|haiphong"),
        "CHEMIE" to Regex("chemic|fertili|kemij|cinkarna|plastic|phan bon"),
        "EINZELHANDEL" to Regex("retail|mobile world|konzum|supermarket|store"),
    )

    fun brancheAusName(name: String): String {
        val n = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replace(de.troetenrepublik.app.engine.Muster.re("\\p{M}+"), "").lowercase()
        return BRANCHEN.firstOrNull { (_, rx) -> rx.containsMatchIn(n) }?.first.orEmpty()
    }

    private fun notierung(praefix: String, code: String, name: String, wert: Double, waehrung: String, land: String) =
        Notierung(name, "$praefix:$code", wert, waehrung, land, brancheAusName(name))

    // ---------- Zagreb und Ljubljana ----------

    // securities/<MIC>/json: {"securities":[{"symbol":"HT","name":"HT d.d.","listed_quantity":76550000,"segment_delisting_date":null,
    // "security_class":"EQTY","security_type":"ORD-SHARE",...}]}. Nur Stammaktien, die nicht ausgelistet sind.
    fun zseAktien(text: String): List<Aktie> = runCatching {
        json.parseToJsonElement(text).jsonObject["securities"]!!.jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            if (o["security_class"]?.jsonPrimitive?.contentOrNull != "EQTY" || o["security_type"]?.jsonPrimitive?.contentOrNull != "ORD-SHARE") return@mapNotNull null
            if (o["segment_delisting_date"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull != null) return@mapNotNull null
            val code = o["symbol"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val n = o["listed_quantity"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            if (n <= 0) return@mapNotNull null
            Aktie(code, reinigen(o["name"]?.jsonPrimitive?.contentOrNull ?: code), n)
        }
    }.getOrDefault(emptyList())

    // price-list/<MIC>/<Datum>/json: Schlusskurse ("close_price":"21.9000", Euro) der Stammaktien mit Handel an dem Tag; leer an Feiertagen
    fun zsePreise(text: String): Map<String, Double> = runCatching {
        val aus = HashMap<String, Double>()
        for (e in json.parseToJsonElement(text).jsonObject["securities"]!!.jsonArray) {
            val o = e as? JsonObject ?: continue
            if (o["security_class"]?.jsonPrimitive?.contentOrNull != "EQTY" || o["security_type"]?.jsonPrimitive?.contentOrNull != "ORD-SHARE") continue
            val code = o["symbol"]?.jsonPrimitive?.contentOrNull ?: continue
            val p = o["close_price"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue
            if (p > 0) aus[code] = p
        }
        aus
    }.getOrDefault(emptyMap())

    // ---------- Ho-Chi-Minh-Stadt (HOSE) ----------

    // securities/stock?pageIndex=N: {"data":{"list":[{"code":"AAA","name":"An Phat ... Joint Stock Company","securitiesType":1,"outStanding":"393,742,730"}]}}.
    // securitiesType 1 = Aktie
    fun hoseAktien(text: String): List<Aktie> = runCatching {
        json.parseToJsonElement(text).jsonObject["data"]!!.jsonObject["list"]!!.jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            if (o["securitiesType"]?.jsonPrimitive?.contentOrNull != "1") return@mapNotNull null
            val code = o["code"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val n = o["outStanding"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.let { zahl(it) } ?: return@mapNotNull null
            if (n <= 0) return@mapNotNull null
            Aktie(code, reinigen(o["name"]?.jsonPrimitive?.contentOrNull ?: code), n)
        }
    }.getOrDefault(emptyList())

    // Anzahl der Eintraege einer Seite der Aktienliste (auch Nicht-Aktien), um das Seitenende zu erkennen
    fun hoseSeitenGroesse(text: String): Int = runCatching { json.parseToJsonElement(text).jsonObject["data"]!!.jsonObject["list"]!!.jsonArray.size }.getOrDefault(0)

    // quote-report: {"data":[{"securitySymbol":"AAA","closePrice":"7.08","priorClosePrice":"7.17",...}]}, Preise in 1000 Dong;
    // Ergebnis in Dong. Ohne Schlusskurs (Sitzung laeuft) gilt der Vortagesschluss.
    fun hoseKurse(text: String): Map<String, Double> = runCatching {
        val aus = HashMap<String, Double>()
        for (e in json.parseToJsonElement(text).jsonObject["data"]!!.jsonArray) {
            val o = e as? JsonObject ?: continue
            val code = o["securitySymbol"]?.jsonPrimitive?.contentOrNull ?: continue
            val p = listOf("closePrice", "priorClosePrice").firstNotNullOfOrNull { k ->
                o[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.let { zahl(it) }?.takeIf { it > 0 }
            } ?: continue
            aus[code] = p * 1000.0
        }
        aus
    }.getOrDefault(emptyMap())

    // ---------- Hanoi (HNX) und UPCoM ----------

    // ListSearch_Datas: {"Content":"<html>...<tr>...<td>Nr</td><td>Kuerzel</td><td>Name</td>[<td>Branche</td>]<td>Datum</td><td>KLNY</td><td>KLLH</td></tr>"}.
    // Die letzte Zelle ist die Zahl der Aktien (Punkte als Tausendertrenner).
    fun hnxAktien(text: String): List<Aktie> = runCatching {
        val html = json.parseToJsonElement(text).jsonObject["Content"]!!.jsonPrimitive.content
        Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(html).mapNotNull { r ->
            val z = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(r.groupValues[1]).map { klartext(it.groupValues[1]) }.toList()
            if (z.size < 6) return@mapNotNull null
            val n = z.last().replace(".", "").replace(Regex("[\\s,]"), "").toDoubleOrNull() ?: return@mapNotNull null
            if (n <= 0 || z[1].isBlank()) null else Aktie(z[1], reinigen(z[2]), n)
        }.toList()
    }.getOrDefault(emptyList())

    // GetStockInBasket: {"success":1,"obj":["|ADC|ADC_stock_name*ADC|...|ADC_close_price*18.0*4|...|<Name vi>|<Name en>|NY|LIS"]}.
    // Kurs in 1000 Dong; Ergebnis in Dong. Ohne Handel steht dort der Referenzpreis.
    fun hnxKurse(text: String): List<HnxKurs> = runCatching {
        json.parseToJsonElement(text).jsonObject["obj"]!!.jsonArray.mapNotNull { e ->
            val zeile = (e as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val code = Regex("^\\|(\\w+)\\|").find(zeile)?.groupValues?.get(1) ?: return@mapNotNull null
            val p = Regex(Regex.escape(code) + "_close_price\\*([0-9.]+)\\*").find(zeile)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            if (p <= 0) return@mapNotNull null
            val teile = zeile.split('|')
            val name = teile.getOrNull(teile.size - 3)?.takeIf { teile.size > 4 && !it.contains('*') && it.isNotBlank() }?.let { reinigen(it) }.orEmpty()
            HnxKurs(code, p * 1000.0, name)
        }
    }.getOrDefault(emptyList())

    // ---------- Zusammenfuehren (Kurs mal Aktienzahl) ----------

    fun malAnzahl(aktien: List<Aktie>, preise: Map<String, Double>, praefix: String, waehrung: String, land: String): List<Notierung> =
        aktien.mapNotNull { a ->
            val p = preise[a.code] ?: return@mapNotNull null
            val w = p * a.anzahl
            if (w > 0 && w.isFinite()) notierung(praefix, a.code, a.name, w, waehrung, land) else null
        }

    fun hnxMalAnzahl(aktien: List<Aktie>, kurse: List<HnxKurs>, praefix: String, land: String): List<Notierung> {
        val nachCode = aktien.associateBy { it.code }
        return kurse.mapNotNull { k ->
            val a = nachCode[k.code] ?: return@mapNotNull null
            val w = k.preis * a.anzahl
            if (w > 0 && w.isFinite()) notierung(praefix, k.code, k.name.ifBlank { a.name }, w, "VND", land) else null
        }
    }

    // ---------- Zypern ----------

    // Tabellen "Main market", "Alternative market", "Surveillance market" mit Zeilen Kuerzel, Name, Datum, Aktienzahl, Kurs, Faktor,
    // Boersenwert in Euro (Komma als Tausendertrenner); danach folgen Anleihen. Fonds und Zweitnotierungen auslaendischer Banken
    // ("CR with VR") fallen weg.
    fun zypern(html: String): List<Notierung> {
        val aktien = html.substringBefore("TREASURY", html)
        val aus = ArrayList<Notierung>()
        for (r in Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(aktien)) {
            val z = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(r.groupValues[1]).map { klartext(it.groupValues[1]) }.toList()
            if (z.size < 7 || z[0].isBlank() || z[0] == "Code") continue
            val v = zahl(z[6]) ?: continue
            if (v <= 0 || Regex("(?i)\\bfund\\b|CR with VR").containsMatchIn(z[1])) continue
            aus += notierung("CSE", z[0], reinigen(z[1]), v, "EUR", "CY")
        }
        return aus
    }

    // ---------- Trinidad und Tobago ----------

    // Namen der groessten Werte (die Tabelle nennt nur Kuerzel)
    private val TT_NAMEN = mapOf(
        "RFHL" to "Republic Financial Holdings", "CIBC" to "CIBC Caribbean", "FCGFH" to "First Citizens Group Financial Holdings",
        "SBTT" to "Scotiabank Trinidad and Tobago", "NCBFG" to "NCB Financial Group", "AMCL" to "ANSA McAL", "MASSY" to "Massy Holdings",
        "GHL" to "Guardian Holdings", "AGL" to "Agostini's", "NEL" to "National Enterprises", "GKC" to "GraceKennedy",
        "AMBL" to "ANSA Merchant Bank", "AHL" to "Angostura Holdings", "WCO" to "West Indian Tobacco", "TCL" to "Trinidad Cement",
        "PHL" to "Prestige Holdings", "NGL" to "NGL", "JMMBGL" to "JMMB Group", "UCL" to "Unilever Caribbean", "NFM" to "National Flour Mills",
        "OCM" to "One Caribbean Media", "GML" to "Guardian Media",
    )

    // Tabellen je Sektor (values-and-index-summary): Zeilen "Kuerzel | Issued Share Capital | Capitalisation Value | ..." (TTD), jede Tabelle
    // endet mit einer Zeile "<Sektor> Sector Totals". Summenzeilen, Fonds, Dollarwerte und Sonstiges ("NON-SECTOR") fallen weg.
    fun trinidad(html: String): List<Notierung> {
        val aus = ArrayList<Notierung>()
        var offen = ArrayList<Pair<String, Double>>()
        for (r in Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(html)) {
            val z = Regex("<t[dh][^>]*>(.*?)</t[dh]>", RegexOption.DOT_MATCHES_ALL).findAll(r.groupValues[1]).map { klartext(it.groupValues[1]) }.toList()
            val k = z.getOrNull(0)?.takeIf { it.isNotBlank() } ?: continue
            if (k == "Security" || k == "Symbol") { offen = ArrayList(); continue }
            if (k.contains("Totals", ignoreCase = true)) {
                val abschnitt = k.substringBefore("Totals").trim()
                if (!Regex("(?i)mutual funds|usd equity|non[- ]sector").containsMatchIn(abschnitt)) {
                    for ((code, v) in offen) aus += notierung("TTSE", code, TT_NAMEN[code] ?: code, v, "TTD", "TT")
                }
                offen = ArrayList()
                continue
            }
            val v = z.getOrNull(2)?.let { zahl(it) } ?: continue
            if (v > 0) offen += k.replace(Regex("\\s*\\((S|XD|XR|XC)\\)$"), "") to v
        }
        return aus
    }

    // ---------- Aserbaidschan ----------

    // Tabellen "Main market" und "Standard market": <tr class="issuer ..."> mit ISIN, Name (Link), Datum, Kurs, Datum, Makler, Menge, Referenzkurs,
    // Nennwert, Boersenwert (AZN, Leerzeichen als Tausendertrenner)
    fun baku(html: String): List<Notierung> {
        val aus = ArrayList<Notierung>()
        for (r in Regex("<tr class=\"issuer[^\"]*\"[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(html)) {
            val z = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(r.groupValues[1]).map { klartext(it.groupValues[1]) }.toList()
            if (z.size < 8) continue
            val v = zahl(z.last()) ?: continue
            if (v > 0) aus += notierung("BFB", z[0], reinigen(z[1]), v, "AZN", "AZ")
        }
        return aus
    }

    // ---------- Kasachstan ----------

    // Tabelle "Representative list of KASE Index": Nr, Kuerzel, Name, Kurs, Trend, Schlusskurs, Gewicht, Aktienzahl, Streubesitz, Faktor
    // (Leerzeichen als Tausendertrenner, Komma als Dezimaltrenner); Boersenwert = Kurs mal Aktienzahl in Tenge
    fun kase(html: String): List<Notierung> {
        val ab = html.substringAfter("Representative list of KASE Index", "")
        val tabelle = ab.substringAfter("<tbody", "").substringBefore("</tbody>")
        val aus = ArrayList<Notierung>()
        for (r in Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(tabelle)) {
            val z = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(r.groupValues[1]).map { klartext(it.groupValues[1]) }.toList()
            if (z.size < 8) continue
            val preis = zahlKomma(z[3]) ?: zahlKomma(z[5]) ?: continue
            val anzahl = zahlKomma(z[7]) ?: continue
            val w = preis * anzahl
            if (w > 0) aus += notierung("KASE", z[1], reinigen(z[2]), w, "KZT", "KZ")
        }
        return aus
    }

    // ---------- Lima ----------

    // stock-quote/market: {"content":[{"companyCode":"21400","nemonico":"ALICORC1","shortName":"Alicorp","last":13.3,"previous":13.3,"currency":"S/",
    // "negotiatedNationalAmount":85197.0}]}; companyCode "XXX" = auslaendisches Papier. Preis: letzter Kurs, sonst Vortag.
    fun peruKurse(text: String): List<PeruKurs> = runCatching {
        json.parseToJsonElement(text).jsonObject["content"]!!.jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val firma = o["companyCode"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (firma == "XXX" || firma.isBlank()) return@mapNotNull null
            val nem = o["nemonico"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val p = listOf("last", "previous").firstNotNullOfOrNull { k -> o[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 } } ?: return@mapNotNull null
            val w = when (o["currency"]?.jsonPrimitive?.contentOrNull?.trim()) { "S/", "S/." -> "PEN"; "US$" -> "USD"; else -> return@mapNotNull null }
            val umsatz = o["negotiatedNationalAmount"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull ?: 0.0
            PeruKurs(firma, nem, o["shortName"]?.jsonPrimitive?.contentOrNull.orEmpty(), p, w, umsatz, o["segment"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }
    }.getOrDefault(emptyList())

    // issuers/<companyCode>: {"companyCode":"70500","companyName":"INTEGRATEL PERU S.A.A.","listValue":[{"listStock":[{"nemonico":"INTPEBC1","quantity":"16501333878"}]}]}
    fun peruEmittent(text: String): PeruEmittent? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val firma = o["companyCode"]!!.jsonPrimitive.content
        val aktien = HashMap<String, Double>()
        for (v in o["listValue"]?.jsonArray.orEmpty()) for (s in (v as? JsonObject)?.get("listStock") as? JsonArray ?: JsonArray(emptyList())) {
            val so = s as? JsonObject ?: continue
            val nem = so["nemonico"]?.jsonPrimitive?.contentOrNull ?: continue
            val q = so["quantity"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.let { zahl(it) } ?: continue
            if (q > 0) aktien[nem] = q
        }
        PeruEmittent(firma, reinigen(o["companyName"]?.jsonPrimitive?.contentOrNull ?: firma), aktien)
    }.getOrNull()

    // Boersenwert je Emittent: Summe aus Kurs mal Aktienzahl aller Gattungen mit Kurs, in der Waehrung der Gattung mit dem groessten Wert
    fun peruWerte(kurse: List<PeruKurs>, emittenten: Map<String, PeruEmittent>): List<Notierung> {
        val aus = ArrayList<Notierung>()
        for ((firma, liste) in kurse.groupBy { it.firma }) {
            val e = emittenten[firma] ?: continue
            val teile = liste.mapNotNull { k -> e.aktien[k.nemonico]?.let { q -> k to k.preis * q } }
            if (teile.isEmpty()) continue
            val waehrung = teile.maxBy { it.second }.first.waehrung
            val w = teile.filter { it.first.waehrung == waehrung }.sumOf { it.second }
            if (w > 0 && w.isFinite()) aus += notierung("BVL", firma, e.name.ifBlank { liste.first().name }, w, waehrung, "PE")
        }
        return aus
    }

    // Zwischenspeicher der Aktienzahlen: eine Zeile je Aktie, Felder durch Tabulator
    fun aktienSpeichern(l: List<Aktie>): String = l.joinToString("\n") { it.code + "\t" + it.name + "\t" + it.anzahl.toLong() }

    fun aktienLaden(text: String): List<Aktie> = text.lines().mapNotNull { z ->
        val f = z.split('\t'); if (f.size < 3) return@mapNotNull null
        Aktie(f[0], f[1], f[2].toDoubleOrNull() ?: return@mapNotNull null)
    }

    // Zwischenspeicher der Emittenten in Lima: eine Zeile je Emittent "Firma TAB Name TAB Kuerzel=Anzahl;Kuerzel=Anzahl"
    fun emittentenSpeichern(m: Map<String, PeruEmittent>): String = m.values.joinToString("\n") { e ->
        e.firma + "\t" + e.name + "\t" + e.aktien.entries.joinToString(";") { it.key + "=" + it.value.toLong() }
    }

    fun emittentenLaden(text: String): Map<String, PeruEmittent> = text.lines().mapNotNull { z ->
        val f = z.split('\t'); if (f.size < 3) return@mapNotNull null
        val a = f[2].split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toDoubleOrNull() ?: return@mapNotNull null) } }.toMap()
        PeruEmittent(f[0], f[1], a)
    }.associateBy { it.firma }
}
