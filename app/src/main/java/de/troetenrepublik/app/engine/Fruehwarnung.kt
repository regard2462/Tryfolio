package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Fruehwarnzeichen fuer den Aktienmarkt, je mit einer Ampel. Keines sagt einen Crash zuverlaessig voraus;
// zusammen zeigen sie, ob sich Risiken haeufen.
// Zinskurve (10 Jahre minus 3 Monate, US-Staatsanleihen, Quelle US-Finanzministerium): Eine Inversion ging allen
// US-Rezessionen seit 1968 voraus, mit 6 bis 24 Monaten Abstand. Gefaehrlich wird es oft, wenn sie wieder steiler wird.
// Sahm-Regel (US-Arbeitslosenquote, Bureau of Labor Statistics): Steigt der Dreimonatsschnitt um 0,5 Punkte ueber
// sein Tief der letzten zwoelf Monate, hatte eine Rezession meist schon begonnen.
// VIX ueber 30 gilt als Panik. Liegt der VIX ueber dem VIX3M, ist der Stress akut.
// Trend: Die grossen Baerenmaerkte verliefen unter der 200-Tage-Linie.
object Fruehwarnung {

    enum class Ampel { GRUEN, GELB, ROT }

    // Ein Zeichen: Schluessel, Ampel, aktueller Wert, Verlauf (Zeit in Millisekunden, aelteste zuerst).
    data class Zeichen(val schluessel: String, val ampel: Ampel, val wert: Double, val verlauf: List<Pair<Long, Double>> = emptyList())

    const val ZINSKURVE = "zinskurve"
    const val SAHM = "sahm"
    const val VIX = "vix"
    const val VIX_STRUKTUR = "vix_struktur"
    const val TREND_SP500 = "trend_sp500"
    const val TREND_DAX = "trend_dax"

    // Reihenfolge der Anzeige
    val ALLE = listOf(ZINSKURVE, SAHM, VIX, VIX_STRUKTUR, TREND_SP500, TREND_DAX)

    fun treasuryAdresse(jahr: Int) =
        "https://home.treasury.gov/resource-center/data-chart-center/interest-rates/daily-treasury-rates.csv/$jahr/all" +
            "?type=daily_treasury_yield_curve&field_tdr_date_value=$jahr&page&_format=csv"

    const val BLS = "https://api.bls.gov/publicAPI/v1/timeseries/data/LNS14000000"

    private const val TAG = 86_400_000L

    // Zinsabstand 10 Jahre minus 3 Monate aus der CSV des US-Finanzministeriums (Datum MM/TT/JJJJ), aelteste zuerst.
    fun treasuryLesen(csv: String): List<Pair<Long, Double>> {
        val zeilen = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (zeilen.isEmpty()) return emptyList()
        val kopf = zeilen[0].split(',').map { it.trim().trim('"').lowercase() }
        val i3 = kopf.indexOf("3 mo"); val i10 = kopf.indexOf("10 yr")
        if (i3 < 0 || i10 < 0) return emptyList()
        return zeilen.drop(1).mapNotNull { z ->
            val f = z.split(',')
            val d = Regex("""^(\d{2})/(\d{2})/(\d{4})$""").matchEntire(f.getOrNull(0)?.trim()?.trim('"').orEmpty()) ?: return@mapNotNull null
            val t = BrokerExport.zeit("${d.groupValues[3]}-${d.groupValues[1]}-${d.groupValues[2]}")
            val a = f.getOrNull(i3)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            val b = f.getOrNull(i10)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            t to (b - a)
        }.sortedBy { it.first }
    }

    // Zinskurve: Gelb, solange sie invertiert ist (unter null); Rot, wenn sie nach einer Inversion in den letzten
    // 18 Monaten wieder ueber null steht; sonst Gruen.
    fun zinskurve(reihe: List<Pair<Long, Double>>): Zeichen? {
        val (t, w) = reihe.lastOrNull() ?: return null
        val invertiertKuerzlich = reihe.any { it.first >= t - 548 * TAG && it.second < 0 }
        val ampel = when {
            w < 0 -> Ampel.GELB
            invertiertKuerzlich -> Ampel.ROT
            else -> Ampel.GRUEN
        }
        return Zeichen(ZINSKURVE, ampel, w, reihe.filter { it.first >= t - 730 * TAG })
    }

    // Monatliche Arbeitslosenquote aus der Antwort der BLS (Monat als Monatsmitte), aelteste zuerst.
    fun blsLesen(text: String): List<Pair<Long, Double>> = runCatching {
        val w = Json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        val serie = ((w["Results"] as? JsonObject)?.get("series") as? JsonArray)?.firstOrNull() as? JsonObject ?: return emptyList()
        (serie["data"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val jahr = (o["year"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return@mapNotNull null
            val monat = (o["period"] as? JsonPrimitive)?.content?.removePrefix("M")?.toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
            val wert = (o["value"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
            BrokerExport.zeit("$jahr-${monat.toString().padStart(2, '0')}-15") to wert
        }.sortedBy { it.first }
    }.getOrDefault(emptyList())

    // Sahm-Regel: Dreimonatsschnitt der Arbeitslosenquote minus sein Tief der vorangegangenen zwoelf Monate. Ab 0,5
    // Rot, ab 0,3 Gelb. Braucht mindestens 15 Monate.
    fun sahm(quote: List<Pair<Long, Double>>): Zeichen? {
        if (quote.size < 15) return null
        val schnitt = (2 until quote.size).map { i -> quote[i].first to (quote[i].second + quote[i - 1].second + quote[i - 2].second) / 3 }
        val reihe = (12 until schnitt.size).map { i ->
            schnitt[i].first to schnitt[i].second - schnitt.subList(i - 12, i).minOf { it.second }
        }
        val w = reihe.lastOrNull()?.second ?: return null
        val ampel = when {
            w >= 0.5 - 1e-9 -> Ampel.ROT
            w >= 0.3 - 1e-9 -> Ampel.GELB
            else -> Ampel.GRUEN
        }
        return Zeichen(SAHM, ampel, w, reihe)
    }

    // VIX: ab 30 Rot, ab 20 Gelb.
    fun vix(kurse: List<Pair<Long, Double>>): Zeichen? {
        val w = kurse.lastOrNull()?.second ?: return null
        return Zeichen(VIX, if (w >= 30) Ampel.ROT else if (w >= 20) Ampel.GELB else Ampel.GRUEN, w, kurse.takeLast(260))
    }

    // VIX zu VIX3M an gemeinsamen Tagen: ueber 1 Rot (kurzfristig mehr Angst als auf drei Monate), ab 0,95 Gelb.
    fun vixStruktur(vix: List<Pair<Long, Double>>, vix3m: List<Pair<Long, Double>>): Zeichen? {
        val drei = vix3m.associate { it.first / TAG to it.second }
        val reihe = vix.mapNotNull { (t, v) -> drei[t / TAG]?.takeIf { it > 0 }?.let { t to v / it } }
        val w = reihe.lastOrNull()?.second ?: return null
        return Zeichen(VIX_STRUKTUR, if (w > 1.0) Ampel.ROT else if (w >= 0.95) Ampel.GELB else Ampel.GRUEN, w, reihe.takeLast(260))
    }

    // Abstand des Index zu seiner 200-Tage-Linie in Prozent: darunter Rot, bis 3 Prozent darueber Gelb, sonst Gruen.
    // Verlauf: der Abstand je Tag.
    fun trend(schluessel: String, kurse: List<Pair<Long, Double>>): Zeichen? {
        if (kurse.size < 200) return null
        val reihe = (199 until kurse.size).map { i ->
            val linie = kurse.subList(i - 199, i + 1).sumOf { it.second } / 200
            kurse[i].first to (kurse[i].second / linie - 1) * 100
        }
        val w = reihe.last().second
        return Zeichen(schluessel, if (w < 0) Ampel.ROT else if (w < 3) Ampel.GELB else Ampel.GRUEN, w, reihe.takeLast(260))
    }

    // AAII-Umfrage

    const val AAII = "https://www.aaii.com/sentimentsurvey/sent_results"
    const val AAII_SEITE = "https://www.aaii.com/sentimentsurvey"

    // Eine Woche der AAII-Umfrage: Anteile in Prozent.
    data class Umfrage(val zeit: Long, val bullisch: Double, val neutral: Double, val baerisch: Double)

    private val MONATE = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    // Jahr und Monat (1 bis 12) eines Zeitpunkts in UTC.
    fun jahrMonat(ms: Long): Pair<Int, Int> {
        val z = Math.floorDiv(ms, TAG) + 719_468L
        val era = Math.floorDiv(z, 146_097L)
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return y to m
    }

    // Wochen der AAII-Umfrage aus der Ergebnisseite (Tabelle mit Datum ohne Jahr, Bullish, Neutral, Bearish), neueste
    // zuerst. Das Jahr ergibt sich rueckwaerts ab jetzt.
    fun aaiiLesen(html: String, jetzt: Long): List<Umfrage> {
        val zelle = Regex("""<td[^>]*class="tableTxt"[^>]*>\s*([^<]*?)\s*</td>""", RegexOption.IGNORE_CASE)
        val werte = zelle.findAll(html).map { it.groupValues[1].trim() }.toList()
        val (heuteJahr, heuteMonat) = jahrMonat(jetzt)
        var jahr = heuteJahr
        var letzterMonat = 13
        val aus = ArrayList<Umfrage>()
        var i = 0
        while (i + 3 < werte.size) {
            val m = Regex("""^([A-Za-z]{3})[a-z]*\.?\s+(\d{1,2})$""").matchEntire(werte[i])
            val b = werte[i + 1].removeSuffix("%").trim().toDoubleOrNull()
            val n = werte[i + 2].removeSuffix("%").trim().toDoubleOrNull()
            val s = werte[i + 3].removeSuffix("%").trim().toDoubleOrNull()
            if (m == null || b == null || n == null || s == null) { i++; continue }
            val monat = MONATE.indexOf(m.groupValues[1].lowercase()) + 1
            if (monat == 0) { i++; continue }
            // Die Tabelle laeuft rueckwaerts; springt der Monat nach oben, beginnt das Vorjahr
            if (monat > letzterMonat || (aus.isEmpty() && monat > heuteMonat)) jahr--
            letzterMonat = monat
            val tag = m.groupValues[2].padStart(2, '0')
            aus += Umfrage(BrokerExport.zeit("$jahr-${monat.toString().padStart(2, '0')}-$tag"), b, n, s)
            i += 4
        }
        return aus
    }
}
