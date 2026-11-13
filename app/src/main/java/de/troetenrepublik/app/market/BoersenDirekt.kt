package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.BoersenfeedsMehr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// [Globus Boersen 0.73] Leitindizes, die Yahoo nicht fuehrt, direkt von der Boerse (frei und ohne Anmeldung, die Adressen
// sind die Aufrufe der eigenen Webseiten). Eine Quelle kann mehrere Laender bedienen (BRVM: acht). Jeder Parser liefert die
// Tagesaenderung in Prozent und, soweit die Antwort es hergibt, den Stand und das Datum des Kurses. Ohne lesbare Antwort
// bleibt das Land neutral; Alter und Zusammenfuehren regelt IndexStand.
object BoersenDirekt {
    // datum null: die Antwort nennt kein Datum, der Abruftag gilt
    data class Stand(val prozent: Double, val wert: Double? = null, val datum: LocalDate? = null)

    // formular: POST mit Formularkoerper, json: POST mit JSON-Koerper (auch leer); beides null: GET
    class Quelle(
        val id: String, val laender: List<String>, val index: String, val url: String,
        val formular: List<Pair<String, String>>? = null, val json: String? = null,
        val max: Int = 600_000, val zeitMs: Long = 10_000L, val parse: (String) -> Stand?,
    )

    const val IMOEX_URL = "https://iss.moex.com/iss/engines/stock/markets/index/securities/IMOEX.json?iss.meta=off&iss.only=marketdata"
    const val PFTS_URL = "https://webdata.pfts.ua/sitedata/rest/en/result/lastvalues"
    private const val WIEN = "https://www.wienerborse.at/en/indices/index-values/overview/?ISIN="

    private val EINGEBAUT: List<Quelle> = listOf(
        Quelle("moex", listOf("RU"), "IMOEX", IMOEX_URL, parse = ::moex),
        Quelle("pfts", listOf("UA"), "PFTS Index", PFTS_URL, parse = ::pfts),
        Quelle("brvm", listOf("BJ", "BF", "CI", "GW", "ML", "NE", "SN", "TG"), "BRVM Composite", "https://www.brvm.org/en/indices", parse = ::brvm),
        Quelle("psx", listOf("PK"), "KSE-100", "https://dps.psx.com.pk/", parse = ::psx),
        Quelle("dse-bd", listOf("BD"), "DSEX", "https://www.dsebd.org/", parse = ::dsex),
        Quelle("cse-lk", listOf("LK"), "ASPI", "https://www.cse.lk/api/aspiData", formular = listOf("x" to "1"), parse = ::aspi),
        Quelle("nse-ke", listOf("KE"), "NSE All Share (NASI)", "https://www.nse.co.ke/", max = 1_000_000, parse = ::nasi),
        Quelle("bse-bg", listOf("BG"), "SOFIX", "https://www.bse-sofia.bg/en/", max = 2_500_000, parse = ::sofix),
        Quelle("zse-hr", listOf("HR"), "CROBEX", "https://zse.hr/json/WebHomeData", max = 400_000, parse = ::crobex),
        Quelle("ljse-si", listOf("SI"), "SBITOP", "https://www.ljse.si/json/WebHomeData", max = 400_000, parse = ::sbitop),
        Quelle("dse-tz", listOf("TZ"), "DSEI", "https://dse.co.tz/", max = 1_000_000, parse = ::dsei),
        Quelle("bsse-sk", listOf("SK"), "SAX", "https://www.bsse.sk/", max = 800_000, parse = ::sax),
        Quelle("kase", listOf("KZ"), "KASE Index", "https://kase.kz/en/", max = 1_000_000, parse = ::kase),
        Quelle("jse-jm", listOf("JM"), "JSE Main Index", "https://www.jamstockex.com/", max = 600_000, parse = ::jamaika),
        Quelle("bisx", listOf("BS"), "BISX All Share", "https://www.bisxbahamas.com/", max = 600_000, parse = ::bisx),
        Quelle("kse-kg", listOf("KG"), "KSE Index", "https://www.kse.kg/", max = 600_000, parse = ::kseKg),
        Quelle("mse-mk", listOf("MK"), "MBI10", "https://www.mse.mk/en/indicies/MBI10/values", max = 600_000, parse = ::mbi10),
        Quelle("bgdx", listOf("RS"), "BELEX15", "https://bgdx.rs/", max = 400_000, parse = ::belex15),
        Quelle("wien-ba", listOf("BA"), "BATX (Wiener Börse, EUR)", WIEN + "AT0000A0FSC7&ID_NOTATION=32133504", max = 800_000, parse = ::wiener),
        Quelle("wien-rs", listOf("RS"), "SRX (Wiener Börse, EUR)", WIEN + "AT0000A02WW9&ID_NOTATION=17120514", max = 800_000, parse = ::wiener),
        Quelle("ngx", listOf("NG"), "NGX All Share", "https://ngxgroup.com/wp-json/ngx-data-widgets/v1/home/indices-page?index=ASI", max = 1_500_000, zeitMs = 40_000L, parse = ::ngx),
        Quelle("sgbv", listOf("DZ"), "DZAIRINDEX", "https://www.sgbv.dz/", max = 1_500_000, parse = ::dzair),
        Quelle("gse-gh", listOf("GH"), "GSE-CI", "https://gse.com.gh/", max = 1_500_000, parse = ::gseGh),
        Quelle("bse-bw", listOf("BW"), "DCI", "https://apis.bse.co.bw/api/v1/index-chart", json = "", max = 800_000, parse = ::dciBw),
        Quelle("msm-mt", listOf("MT"), "MSE Equity Price Index", "https://www.borzamalta.com.mt/api/graph/MSE", max = 400_000, parse = ::malta),
        Quelle("latinex", listOf("PA"), "LTXSI", "https://www.latinexbolsa.com/chart/home/get/chart-W", max = 400_000, parse = ::latinex),
        Quelle("csx", listOf("KH"), "CSX Index", "https://csx.com.kh/api/v1/website/market-data/index/daily-closing-index/current-index", max = 100_000, parse = ::csx),
        Quelle("uzse", listOf("UZ"), "UCI", "https://uzse.uz/price_indices?locale=ru", max = 800_000, zeitMs = 40_000L, parse = ::uci),
        Quelle("nepse", listOf("NP"), "NEPSE Index", "https://merolagani.com/Indices.aspx", max = 1_500_000, parse = ::nepse),
        Quelle("ysx", listOf("MM"), "MYANPIX", "https://ysx-mm.com/", max = 1_500_000, parse = ::myanpix),
        Quelle("bvg", listOf("EC"), "IRECU-BVG", "https://www.bolsadevaloresguayaquil.com/", max = 1_000_000, parse = ::guayaquil),
        Quelle("luse", listOf("ZM"), "LuSE All Share Index (LASI)", "https://luse.co.zm/trading/market-data/", max = 500_000, parse = ::luse),
    )

    // Eingebaute Quellen, danach die zusaetzlichen (BoersenZusatz)
    val QUELLEN: List<Quelle> = EINGEBAUT + BoersenZusatz.QUELLEN

    // Land -> Index (erste Quelle gewinnt), fuer Name und Anzeige
    fun laender(): Map<String, Quelle> {
        val m = LinkedHashMap<String, Quelle>()
        for (q in QUELLEN) for (l in q.laender) m.putIfAbsent(l, q)
        return m
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------------------------------------------------------------- Hilfen

    // Zahl in angelsaechsischer ("1,400.44") oder europaeischer Schreibweise ("1.400,44"); Leerzeichen als Tausendertrenner
    // fallen weg, das Unicode-Minus gilt als Minus.
    internal fun zahl(roh: String, euro: Boolean = false): Double? {
        var t = roh.replace('−', '-').replace(" ", "").replace(" ", "").replace(" ", "").trim().trimStart('+')
        t = if (euro) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun aenderung(jetzt: Double, vorher: Double): Double? = if (vorher > 0.0 && jetzt > 0.0) (jetzt / vorher - 1.0) * 100.0 else null

    // Seitentext ohne Skripte, Kommentare und Auszeichnung, Leerraum zusammengezogen
    internal fun klartext(html: String): String =
        BoersenfeedsMehr.entitaeten(
            html.replace(Regex("(?is)<(script|style|noscript)\\b.*?</\\1>"), " ").replace(Regex("(?s)<!--.*?-->"), " ").replace(Regex("<[^>]*>"), " ").replace(Regex("&nbsp;?"), " "),
        ).replace(Regex("[\\s\\u00a0\\u202f]+"), " ").trim()

    private val MONATE = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private fun monat(s: String): Int? = MONATE.indexOf(s.take(3).lowercase()).takeIf { it >= 0 }?.plus(1)

    private fun datum(j: Int, m: Int, t: Int): LocalDate? = runCatching { LocalDate.of(j, m, t) }.getOrNull()

    private fun isoDatum(s: String?): LocalDate? = s?.trim()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // 09.11.2026
    private fun punktDatum(s: String?): LocalDate? =
        s?.let { Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})").find(it) }?.destructured?.let { (t, m, j) -> datum(j.toInt(), m.toInt(), t.toInt()) }

    // 09/11/2026 (Tag zuerst)
    private fun schraegDatum(s: String?): LocalDate? =
        s?.let { Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4})").find(it) }?.destructured?.let { (t, m, j) -> datum(j.toInt(), m.toInt(), t.toInt()) }

    private fun msDatum(ms: Double?, stunden: Int = 0): LocalDate? =
        ms?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it.toLong() + stunden * 3_600_000L).atZone(ZoneOffset.UTC).toLocalDate() }

    private fun wurzel(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()

    private fun JsonElement?.obj(k: String): JsonElement? = (this as? JsonObject)?.get(k)

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.content?.takeIf { it != "null" }

    private fun JsonElement?.zahl(): Double? = text()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    // ---------------------------------------------------------------- Parser

    // MOEX ISS: marketdata mit Spaltennamen und Zeilen; LASTCHANGEPRC ist die Tagesaenderung in Prozent.
    fun moex(text: String): Stand? {
        val markt = wurzel(text).obj("marketdata") as? JsonObject ?: return null
        val spalten = (markt["columns"] as? JsonArray)?.map { it.text() } ?: return null
        val zeile = (markt["data"] as? JsonArray)?.firstOrNull() as? JsonArray ?: return null
        fun feld(n: String) = spalten.indexOf(n).takeIf { it in zeile.indices }?.let { zeile[it] }
        val p = feld("LASTCHANGEPRC").zahl() ?: return null
        return Stand(p, feld("LASTVALUE").zahl() ?: feld("CURRENTVALUE").zahl(), isoDatum(feld("TRADEDATE").text() ?: feld("SYSTIME").text()))
    }

    // PFTS Kiew: Liste der Marktwerte, gesucht ist die Zeile „PFTS Index“ mit Wert und Aenderung in Punkten.
    fun pfts(text: String): Stand? {
        val zeilen = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return null
        for (z in zeilen) {
            val name = z.obj("caption").text() ?: continue
            if (!name.contains("PFTS Index", ignoreCase = true)) continue
            val wert = z.obj("value").zahl() ?: return null
            val vorher = wert - (z.obj("change").zahl() ?: 0.0)
            return Stand(aenderung(wert, vorher) ?: return null, wert, punktDatum(z.obj("tradedate").text()))
        }
        return null
    }

    // BRVM (acht Laender der Westafrikanischen Wirtschafts- und Waehrungsunion): Tabellenzeile mit Vortag und Schluss, Komma als Dezimaltrenner.
    fun brvm(html: String): Stand? {
        val m = Regex("<td>\\s*BRVM - COMPOSITE\\s*</td>\\s*<td[^>]*>([\\d\\s.,]+)</td>\\s*<td[^>]*>([\\d\\s.,]+)</td>").find(html) ?: return null
        val vorher = zahl(m.groupValues[1], true) ?: return null
        val wert = zahl(m.groupValues[2], true) ?: return null
        val d = Regex("Last update:\\s*\\w+,\\s*(\\d{1,2})\\s+(\\w+),\\s*(\\d{4})").find(html)?.destructured?.let { (t, mo, j) -> monat(mo)?.let { datum(j.toInt(), it, t.toInt()) } }
        return Stand(aenderung(wert, vorher) ?: return null, wert, d)
    }

    // PSX Karachi: Block KSE100 der Startseite, Prozent in Klammern; Zeitstempel der Tagesdaten im Panel
    fun psx(html: String): Stand? {
        val m = Regex("topIndices__item__name\">KSE100</div>\\s*<div class=\"topIndices__item__val\">([\\d,.]+)</div>.*?topIndices__item__changep\">\\(([^)%]*)%\\)", RegexOption.DOT_MATCHES_ALL).find(html) ?: return null
        val d = Regex("data-name=\"KSE100\"[^>]*data-date=\"(\\d{4}-\\d{2}-\\d{2})").find(html)?.groupValues?.get(1)?.let { isoDatum(it) }
        return Stand(zahl(m.groupValues[2]) ?: return null, zahl(m.groupValues[1]), d)
    }

    // DSE Dhaka: „DSEX Broad index 5,392.82 ▼ 9.86 · − 0.18 %“ im Seitentext
    fun dsex(html: String): Stand? {
        val t = klartext(html)
        val m = Regex("DSEX Broad index ([\\d,.]+)\\s*([▲▼])?\\s*([\\d,.]+)?\\s*·?\\s*([+\\-\\u2212])?\\s*([\\d.]+)\\s*%").find(t) ?: return null
        val minus = m.groupValues[2] == "▼" || m.groupValues[4].isNotEmpty() && m.groupValues[4] != "+"
        val p = zahl(m.groupValues[5]) ?: return null
        return Stand(if (minus) -p else p, zahl(m.groupValues[1]))
    }

    // CSE Colombo: POST, JSON mit value, percentage und Zeitstempel in Millisekunden (Sri-Lanka-Zeit)
    fun aspi(text: String): Stand? {
        val o = wurzel(text) ?: return null
        return Stand(o.obj("percentage").zahl() ?: return null, o.obj("value").zahl(), msDatum(o.obj("timestamp").zahl(), 5))
    }

    // NSE Nairobi: Wert und Aenderung in Punkten, daraus das Prozent
    fun nasi(html: String): Stand? {
        val m = Regex("<h3>\\s*NSE ALL SHARE INDEX\\s*</h3>.*?<p>\\s*([\\d,.]+)\\s*<span[^>]*>\\s*([+\\-\\u2212]?[\\d,.]+)\\s*</span>", RegexOption.DOT_MATCHES_ALL).find(html) ?: return null
        val wert = zahl(m.groupValues[1]) ?: return null
        val punkte = zahl(m.groupValues[2]) ?: return null
        val d = Regex("As of (\\d{1,2})-([A-Za-z]{3})-(\\d{2})").find(html)?.destructured?.let { (t, mo, j) -> monat(mo)?.let { datum(2000 + j.toInt(), it, t.toInt()) } }
        return Stand(aenderung(wert, wert - punkte) ?: return null, wert, d)
    }

    // Boerse Sofia: Block des SOFIX mit Wert (Leerzeichen als Tausendertrenner) und Prozent
    fun sofix(html: String): Stand? {
        val m = Regex("data-link=\"/indices/SOFIX\">SOFIX</div>\\s*<div class=\"top5_issue_size[^\"]*\">([\\d\\s.,]+)</div>.*?class=\"change[^\"]*\">\\s*([+\\-\\u2212]?[\\d.,]+)\\s*%", RegexOption.DOT_MATCHES_ALL).find(html) ?: return null
        return Stand(zahl(m.groupValues[2]) ?: return null, zahl(m.groupValues[1]))
    }

    private fun zagrebLjubljana(text: String, passt: (JsonElement) -> Boolean): Stand? {
        val o = wurzel(text) ?: return null
        val liste = o.obj("indices") as? JsonArray ?: return null
        val z = liste.firstOrNull(passt) ?: return null
        return Stand(z.obj("change_prev_close_percentage").zahl() ?: return null, z.obj("last_value").zahl(), isoDatum(o.obj("timestamp").text()))
    }

    // Boerse Zagreb: Objekt mit short_name CBX
    fun crobex(text: String): Stand? = zagrebLjubljana(text) { it.obj("short_name").text() == "CBX" }

    // Boerse Ljubljana (gleiche Plattform): Objekt mit Name SBITOP
    fun sbitop(text: String): Stand? = zagrebLjubljana(text) { it.obj("name").text() == "SBITOP" }

    // DSE Daressalam: „DSEI -0.03 % 4,375.13“ im Seitentext
    fun dsei(html: String): Stand? {
        val m = Regex("\\bDSEI\\s+([+\\-\\u2212]?[\\d.]+)\\s*%\\s+([\\d,.]+)").find(klartext(html)) ?: return null
        return Stand(zahl(m.groupValues[1]) ?: return null, zahl(m.groupValues[2]))
    }

    // Boerse Bratislava: „SAX Index 321,08 0,00%“ (Komma als Dezimaltrenner)
    fun sax(html: String): Stand? {
        val m = Regex("SAX Index\\s+([\\d ]+,\\d+)\\s*([+\\-\\u2212]?\\d+(?:,\\d+)?)\\s*%").find(klartext(html)) ?: return null
        return Stand(zahl(m.groupValues[2], true) ?: return null, zahl(m.groupValues[1], true))
    }

    // KASE Almaty: „KASE Index +0,73 % 7 872,77“
    fun kase(html: String): Stand? {
        val t = klartext(html)
        // Reihenfolge je Widget verschieden: Prozent vor dem Wert oder Wert vor dem Prozent
        Regex("KASE Index\\s+([+\\-\\u2212]?\\d+(?:,\\d+)?)\\s*%\\s+([\\d ]+,\\d+)").find(t)?.let { m ->
            return Stand(zahl(m.groupValues[1], true) ?: return null, zahl(m.groupValues[2], true))
        }
        val m = Regex("KASE Index\\s+([\\d ]+,\\d+)\\s+([+\\-\\u2212]?\\d+(?:,\\d+)?)\\s*%").find(t) ?: return null
        return Stand(zahl(m.groupValues[2], true) ?: return null, zahl(m.groupValues[1], true))
    }

    // Jamaica Stock Exchange: „Main Index 10,461,421 Vol 385,479.64 -1,601.56“ (Wert und Aenderung in Punkten)
    fun jamaika(html: String): Stand? {
        val m = Regex("Main Index\\s+[\\d,]+\\s*Vol\\s+([\\d,.]+)\\s+([+\\-\\u2212]?[\\d,.]+)").find(klartext(html)) ?: return null
        val wert = zahl(m.groupValues[1]) ?: return null
        val punkte = zahl(m.groupValues[2]) ?: return null
        return Stand(aenderung(wert, wert - punkte) ?: return null, wert)
    }

    // BISX Nassau: „All Share Index 3,365.59 ... Change: 0 / 0%“
    fun bisx(html: String): Stand? {
        val m = Regex("All Share Index\\s+([\\d,.]+).{0,200}?Change:\\s*[+\\-\\u2212]?[\\d,.]+\\s*/\\s*([+\\-\\u2212]?[\\d.,]+)\\s*%").find(klartext(html)) ?: return null
        return Stand(zahl(m.groupValues[2]) ?: return null, zahl(m.groupValues[1]))
    }

    // Boerse Bischkek: „Значения на 09.11.2026 KSE 8408.35 % 0“
    fun kseKg(html: String): Stand? {
        val m = Regex("Значения на (\\d{2}\\.\\d{2}\\.\\d{4})\\s+KSE\\s+([\\d.,]+)\\s*%\\s*([+\\-\\u2212]?[\\d.,]+)").find(klartext(html)) ?: return null
        return Stand(zahl(m.groupValues[3]) ?: return null, zahl(m.groupValues[2]), punktDatum(m.groupValues[1]))
    }

    // Boerse Skopje: Kopf der Seite des MBI10 „MBI10 8,923.49 -0.14% 11/9/2026 1:54:15 PM“ (Monat/Tag/Jahr)
    fun mbi10(html: String): Stand? {
        val m = Regex("MBI10\\s+([\\d,.]+)\\s+([+\\-\\u2212]?[\\d.]+)\\s*%\\s+(\\d{1,2})/(\\d{1,2})/(\\d{4})").find(klartext(html)) ?: return null
        return Stand(zahl(m.groupValues[2]) ?: return null, zahl(m.groupValues[1]), datum(m.groupValues[5].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt()))
    }

    // Belgrader Boerse (bgdx.rs): Kasten des BELEX15 mit Wert (id v15, Punkt als Tausender, Komma als Dezimaltrenner), Prozent
    // mit Pfeilbild und Zeitstempel (tt.mm.jjjj. hh:mm:ss)
    fun belex15(html: String): Stand? {
        val wert = Regex("id=\"v15\">\\s*([\\d.,]+)\\s*<").find(html)?.groupValues?.get(1)?.let { zahl(it, true) } ?: return null
        val p = Regex("id=\"v15\".*?arr_(down|up)\\.gif'/>\\s*([+\\-\\u2212]?[\\d.,]+)\\s*%", RegexOption.DOT_MATCHES_ALL).find(html) ?: return null
        var prozent = zahl(p.groupValues[2], true) ?: return null
        if (p.groupValues[1] == "down" && prozent > 0) prozent = -prozent
        val d = Regex("id=\"t15\">\\s*(\\d{2}\\.\\d{2}\\.\\d{4})").find(html)?.groupValues?.get(1)?.let { punktDatum(it) }
        return Stand(prozent, wert, d)
    }

    // Wiener Boerse, Laenderindizes in Euro (Bosnien BATX, Serbien SRX): „Last Close“ und „Daily Performance“
    fun wiener(html: String): Stand? {
        val wert = Regex("<th>\\s*Last Close\\s*</th>\\s*<td[^>]*>\\s*([\\d,.]+)\\s*</td>").find(html)?.groupValues?.get(1)?.let { zahl(it) }
        val p = Regex("<th>\\s*Daily Performance\\s*</th>\\s*<td[^>]*>\\s*(?:<span[^>]*>)?\\s*([+\\-\\u2212]?[\\d.,]+)\\s*%").find(html)?.groupValues?.get(1)?.let { zahl(it) } ?: return null
        return Stand(p, wert)
    }

    // NGX Lagos: Indexseite der Boerse, primary.quote mit Kurs, Aenderung in Prozent und Datum
    fun ngx(text: String): Stand? {
        val q = wurzel(text).obj("primary").obj("quote")
        return Stand(q.obj("changePercentage").zahl() ?: return null, q.obj("currentPrice").zahl(), isoDatum(q.obj("currentDateTime").text()))
    }

    // SGBV Algier: Diagrammreihe „Dzair Indice“ der Startseite als Paare aus Zeit und Wert; die letzten beiden ergeben die Aenderung
    fun dzair(html: String): Stand? {
        val reihe = Regex("name\\s*:\\s*'Dzair Indice',\\s*data\\s*:\\s*(\\[\\[.*?\\]\\])", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: return null
        val paare = (runCatching { json.parseToJsonElement(reihe).jsonArray }.getOrNull() ?: return null).mapNotNull { p ->
            val a = p as? JsonArray ?: return@mapNotNull null
            val w = a.getOrNull(1).zahl() ?: return@mapNotNull null
            (a.getOrNull(0).zahl() ?: return@mapNotNull null) to w
        }
        if (paare.size < 2) return null
        val (zeit, wert) = paare.last()
        return Stand(aenderung(wert, paare[paare.size - 2].second) ?: return null, wert, msDatum(zeit, 1))
    }

    // GSE Accra: Tabelle „PREVIOUS (tt/mm/jjjj)“ und „CURRENT (tt/mm/jjjj)“, dritte Spalte GSE-CI
    fun gseGh(html: String): Stand? {
        fun zeile(was: String) = Regex("$was \\((\\d{2}/\\d{2}/\\d{4})\\)\\s*</td>\\s*<td[^>]*>\\s*([\\d,.]+)\\s*</td>").find(html)
        val jetzt = zeile("CURRENT") ?: return null
        val vorher = zeile("PREVIOUS") ?: return null
        val w = zahl(jetzt.groupValues[2]) ?: return null
        return Stand(aenderung(w, zahl(vorher.groupValues[2]) ?: return null) ?: return null, w, schraegDatum(jetzt.groupValues[1]))
    }

    // BSE Gaborone: POST ohne Koerper, message[0].indices.data[] mit indexname DCI und history (Preis, Datum)
    fun dciBw(text: String): Stand? {
        val daten = wurzel(text).obj("message").let { (it as? JsonArray)?.firstOrNull() }.obj("indices").obj("data") as? JsonArray ?: return null
        val h = (daten.firstOrNull { it.obj("indexname").text() == "DCI" }.obj("history") as? JsonArray) ?: return null
        if (h.size < 2) return null
        val letzte = h.last(); val davor = h[h.size - 2]
        val w = letzte.obj("price").zahl() ?: return null
        return Stand(aenderung(w, davor.obj("price").zahl() ?: return null) ?: return null, w, isoDatum(letzte.obj("date").text()))
    }

    // Boerse Malta: Reihe items mit x (Zeit in ms) und y (Wert)
    fun malta(text: String): Stand? {
        val items = wurzel(text).obj("items") as? JsonArray ?: return null
        if (items.size < 2) return null
        val w = items.last().obj("y").zahl() ?: return null
        return Stand(aenderung(w, items[items.size - 2].obj("y").zahl() ?: return null) ?: return null, w, msDatum(items.last().obj("x").zahl()))
    }

    // Latinex Panama: data ist ein JSON-Text mit fecha (tt-mm-jjjj) und indice; letztes Element gegen das vorletzte
    fun latinex(text: String): Stand? {
        val innen = wurzel(text).obj("data").text() ?: return null
        val liste = runCatching { json.parseToJsonElement(innen).jsonArray }.getOrNull() ?: return null
        if (liste.size < 2) return null
        val w = liste.last().obj("indice").zahl() ?: return null
        val d = liste.last().obj("fecha").text()?.let { Regex("(\\d{2})-(\\d{2})-(\\d{4})").find(it) }?.destructured?.let { (t, m, j) -> datum(j.toInt(), m.toInt(), t.toInt()) }
        return Stand(aenderung(w, liste[liste.size - 2].obj("indice").zahl() ?: return null) ?: return null, w, d)
    }

    // CSX Phnom Penh: data.value und changePercent, das Vorzeichen steht in changeUpDown
    fun csx(text: String): Stand? {
        val d = wurzel(text).obj("data")
        val p = d.obj("changePercent").zahl()?.let { kotlin.math.abs(it) } ?: return null
        val minus = d.obj("changeUpDown").text().equals("down", true)
        val datum = d.obj("date").text()?.let { Regex("(\\d{4})/(\\d{2})/(\\d{2})").find(it) }?.destructured?.let { (j, m, t) -> datum(j.toInt(), m.toInt(), t.toInt()) }
        return Stand(if (minus) -p else p, d.obj("value").zahl(), datum)
    }

    // UZSE Taschkent: q-price mit Wert, chg mit Punkten und Prozent in Klammern, Datum im Kopf
    fun uci(html: String): Stand? {
        val m = Regex("class=\"q-price\">\\s*([\\d,.]+)\\s*</p>.*?<span class=\"chg\\s*(\\w*)\">\\s*[+\\-\\u2212]?[\\d,.]+\\s*\\(\\s*([+\\-\\u2212]?[\\d.,]+)\\s*%\\s*\\)", RegexOption.DOT_MATCHES_ALL).find(html) ?: return null
        var p = zahl(m.groupValues[3]) ?: return null
        if (m.groupValues[2] == "down" && p > 0) p = -p
        val d = Regex("<span class=\"dt\">\\s*(\\d{2}\\.\\d{2}\\.\\d{4})").find(html)?.groupValues?.get(1)?.let { punktDatum(it) }
        return Stand(p, zahl(m.groupValues[1]), d)
    }

    // Nepal (NEPSE ueber Merolagani): neueste Zeile der Indextabelle mit Datum (jjjj/mm/tt) und Wert; die Aenderung aus der zweiten Zeile
    fun nepse(html: String): Stand? {
        val zeilen = Regex("<td[^>]*>\\s*(\\d{4}/\\d{2}/\\d{2})\\s*</td>\\s*<td[^>]*>\\s*([\\d,.]+)\\s*</td>\\s*<td[^>]*>\\s*([+\\-\\u2212]?[\\d,.]+)\\s*</td>\\s*<td[^>]*>\\s*([+\\-\\u2212]?[\\d.,]+)\\s*%").findAll(html).toList()
        val erste = zeilen.firstOrNull() ?: return null
        val w = zahl(erste.groupValues[2]) ?: return null
        val p = zeilen.getOrNull(1)?.let { aenderung(w, zahl(it.groupValues[2]) ?: return@let null) } ?: zahl(erste.groupValues[4]) ?: return null
        val d = Regex("(\\d{4})/(\\d{2})/(\\d{2})").find(erste.groupValues[1])?.destructured?.let { (j, m, t) -> datum(j.toInt(), m.toInt(), t.toInt()) }
        return Stand(p, w, d)
    }

    // YSX Yangon: Wert im Block des MYANPIX, daneben Punkte und ein Pfeilbild (up oder down); „As of 1:00 pm, 9th Nov. 2026“
    fun myanpix(html: String): Stand? {
        val m = Regex("MYANPIX-1\\.jpg.*?<strong>\\s*([\\d,.]+)\\s*</strong>.*?src=\"[^\"]*img_(\\w+?)(?:green|red)?\\.png\"[^>]*>\\s*<span[^>]*>\\s*([\\d,.]+)", RegexOption.DOT_MATCHES_ALL).find(html) ?: return null
        val wert = zahl(m.groupValues[1]) ?: return null
        val punkte = zahl(m.groupValues[3]) ?: return null
        val hoch = m.groupValues[2].startsWith("up")
        val vorher = if (hoch) wert - punkte else wert + punkte
        val d = Regex("As of [^,]*,\\s*(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]{3})[a-z]*\\.?\\s+(\\d{4})").find(html)?.destructured?.let { (t, mo, j) -> monat(mo)?.let { datum(j.toInt(), it, t.toInt()) } }
        return Stand(aenderung(wert, vorher) ?: return null, wert, d)
    }

    // LuSE Lusaka: Marktbericht „The LuSE All Share Index (LASI) closed at 24,613.67, 0.05% drop from the previous session“
    // (drop, fall, decline, down: negativ, sonst positiv); Rueckfall die Kopfzeile „LASI Current Value 24613.67 Change -12.68“
    // (Punkte). Datum aus „Market Data as of tt/mm/jjjj“, sonst gilt der Abruftag. Eine Pruefseite enthaelt beides nicht: null.
    fun luse(html: String): Stand? {
        val t = klartext(html)
        val d = Regex("Market Data as of (\\d{1,2}/\\d{1,2}/\\d{4})").find(t)?.groupValues?.get(1)?.let { schraegDatum(it) }
        Regex("LASI\\) closed at ([\\d,.]+),\\s*([\\d.]+)\\s*%\\s*([A-Za-z]+)").find(t)?.let { m ->
            val wert = zahl(m.groupValues[1])
            val p = zahl(m.groupValues[2])
            if (wert != null && p != null) {
                val minus = Regex("^(drop|fall|fell|declin|down|lower|lost)", RegexOption.IGNORE_CASE).containsMatchIn(m.groupValues[3])
                return Stand(if (minus) -p else p, wert, d)
            }
        }
        val m = Regex("LASI Current Value ([\\d,.]+) Change ([+\\-\\u2212]?[\\d,.]+)").find(t) ?: return null
        val wert = zahl(m.groupValues[1]) ?: return null
        val punkte = zahl(m.groupValues[2]) ?: return null
        return Stand(aenderung(wert, wert - punkte) ?: return null, wert, d)
    }

    // Boerse Guayaquil: Tabelle mit Spalten BVG INDEX, IRECU-BVG, IPECU-BVG; Zeilen „CIERRE HOY“ und „CIERRE DÍA ANTERIOR“ (zweite Spalte IRECU)
    fun guayaquil(html: String): Stand? {
        fun zeile(was: String) = Regex("<strong>\\s*$was\\s*</strong>\\s*</td>\\s*<td[^>]*>\\s*([\\d,.]+)\\s*</td>\\s*<td[^>]*>\\s*([\\d,.]+)\\s*</td>").find(html)
        val heute = zeile("CIERRE HOY") ?: return null
        val gestern = zeile("CIERRE D[IÍ]A ANTERIOR") ?: return null
        val w = zahl(heute.groupValues[2]) ?: return null
        return Stand(aenderung(w, zahl(gestern.groupValues[2]) ?: return null) ?: return null, w)
    }
}
