package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import kotlin.math.abs

// Rechenteil von Paket q78e (0.76): Bewertung gegen die eigene Geschichte (Vielfache je Stichtag als TTM, mittlere 80 %,
// Median), Verlaeufe aus Jahres- und Quartalszahlen, Leerverkaufsreihen der Nasdaq, Fail-to-Deliver der SEC und
// Reg-SHO-Liste. Alles ohne Netz pruefbar; Tage sind Tage seit 1970.
object Historie078 {

    data class Punkt(val tag: Long, val wert: Double)

    enum class Art { KGV, KUV, KCV, EV_EBITDA }

    // ------------------------------------------------------------ Spanne

    // unten/oben: mittlere 80 Prozent der Stichtage (10. und 90. Perzentil); von/bis: Tage der ersten und letzten Reihe.
    class Spanne(
        val unten: Double, val oben: Double, val median: Double, val heute: Double, val n: Int, val von: Long, val bis: Long,
        val min: Double, val max: Double,
    ) {
        // Abstand von heute zum Median in Prozent (positiv: ueber dem Median)
        val abstand: Double get() = (heute / median - 1.0) * 100.0
    }

    const val MINDEST_PUNKTE = 6

    // Linear interpolierte Perzentile einer sortierten Liste (p von 0 bis 1).
    fun perzentil(sortiert: List<Double>, p: Double): Double {
        if (sortiert.size == 1) return sortiert[0]
        val pos = p.coerceIn(0.0, 1.0) * (sortiert.size - 1)
        val i = pos.toInt()
        val frac = pos - i
        return if (i + 1 < sortiert.size) sortiert[i] + (sortiert[i + 1] - sortiert[i]) * frac else sortiert[i]
    }

    fun spanne(reihe: List<Punkt>, heute: Double?, mindest: Int = MINDEST_PUNKTE): Spanne? {
        if (heute == null || !heute.isFinite() || heute <= 0 || reihe.size < mindest) return null
        val werte = reihe.map { it.wert }.sorted()
        val median = perzentil(werte, 0.5)
        if (median <= 0) return null
        return Spanne(
            perzentil(werte, 0.1), perzentil(werte, 0.9), median, heute, reihe.size, reihe.minOf { it.tag }, reihe.maxOf { it.tag },
            werte.first(), werte.last(),
        )
    }

    // Anteil der Stichtage, an denen das Vielfache niedriger war als heute, in Prozent.
    fun anteilDarunter(reihe: List<Punkt>, heute: Double): Double =
        if (reihe.isEmpty()) 0.0 else reihe.count { it.wert < heute } * 100.0 / reihe.size

    // ------------------------------------------------------------ Stichtage aus Yahoo

    // Ein Stichtag mit den TTM-Werten (Gewinn je Aktie, Umsatz, operativer Cashflow, EBITDA) und den Bestaenden zum
    // Stichtag (Aktienzahl, Schulden, Kasse), alles in der Berichtswaehrung.
    class Basis(
        val tag: Long, val eps: Double?, val umsatz: Double?, val cashflow: Double?, val ebitda: Double?,
        val aktien: Double?, val schulden: Double?, val kasse: Double?,
    )

    // Typen des Yahoo-Endpunkts ws/fundamentals-timeseries, aus denen die Stichtage entstehen: TTM-Typen (trailing) fuer die
    // Quartale, Jahreswerte fuer die Jahresenden davor.
    val YAHOO_TYPEN = listOf(
        "trailingDilutedEPS", "annualDilutedEPS", "trailingTotalRevenue", "annualTotalRevenue",
        "trailingOperatingCashFlow", "annualOperatingCashFlow", "trailingEBITDA", "annualEBITDA",
        "quarterlyOrdinarySharesNumber", "annualOrdinarySharesNumber", "quarterlyTotalDebt", "annualTotalDebt",
        "quarterlyCashCashEquivalentsAndShortTermInvestments", "annualCashCashEquivalentsAndShortTermInvestments",
    )

    fun datum(iso: String): Long? = runCatching { LocalDate.parse(iso.trim().take(10)).toEpochDay() }.getOrNull()

    fun tagZuMs(tag: Long): Long = tag * 86_400_000L

    // Liest die Antwort von ws/fundamentals-timeseries: je Typ die Punkte (asOfDate, reportedValue.raw), nach Tag sortiert.
    fun yahooReihen(text: String): Map<String, List<Punkt>> {
        val wurzel = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyMap()
        val ergebnis = ((wurzel["timeseries"] as? JsonObject)?.get("result") as? JsonArray).orEmpty()
        val aus = HashMap<String, List<Punkt>>()
        for (e in ergebnis) {
            val o = e as? JsonObject ?: continue
            val typ = ((o["meta"] as? JsonObject)?.get("type") as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.content } ?: continue
            val punkte = ArrayList<Punkt>()
            for (p in (o[typ] as? JsonArray).orEmpty()) {
                val po = p as? JsonObject ?: continue
                val tag = (po["asOfDate"] as? JsonPrimitive)?.content?.let { datum(it) } ?: continue
                val roh = ((po["reportedValue"] as? JsonObject)?.get("raw") as? JsonPrimitive)?.content?.toDoubleOrNull() ?: continue
                if (roh.isFinite()) punkte += Punkt(tag, roh)
            }
            if (punkte.isNotEmpty()) aus[typ] = punkte.sortedBy { it.tag }
        }
        return aus
    }

    // Letzter Bestand am Tag oder davor, hoechstens maxAlter Tage alt.
    fun bestandZum(reihe: List<Punkt>, tag: Long, maxAlter: Long = 200L): Double? {
        var lo = 0
        var hi = reihe.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (reihe[mid].tag <= tag) lo = mid + 1 else hi = mid
        }
        val p = reihe.getOrNull(lo - 1) ?: return null
        return if (tag - p.tag <= maxAlter) p.wert else null
    }

    private fun mischen(vararg reihen: List<Punkt>?): List<Punkt> {
        val m = HashMap<Long, Double>()
        // Die erste Reihe hat Vorrang: zuletzt schreiben
        for (r in reihen.reversed()) r?.forEach { m[it.tag] = it.wert }
        return m.map { Punkt(it.key, it.value) }.sortedBy { it.tag }
    }

    fun basenYahoo(r: Map<String, List<Punkt>>): List<Basis> {
        val eps = mischen(r["trailingDilutedEPS"], r["annualDilutedEPS"])
        val umsatz = mischen(r["trailingTotalRevenue"], r["annualTotalRevenue"])
        val cash = mischen(r["trailingOperatingCashFlow"], r["annualOperatingCashFlow"])
        val ebitda = mischen(r["trailingEBITDA"], r["annualEBITDA"])
        val aktien = mischen(r["quarterlyOrdinarySharesNumber"], r["annualOrdinarySharesNumber"])
        val schulden = mischen(r["quarterlyTotalDebt"], r["annualTotalDebt"])
        val kasse = mischen(r["quarterlyCashCashEquivalentsAndShortTermInvestments"], r["annualCashCashEquivalentsAndShortTermInvestments"])
        return basen(eps, umsatz, cash, ebitda, aktien, schulden, kasse)
    }

    fun basen(
        eps: List<Punkt>, umsatz: List<Punkt>, cash: List<Punkt>, ebitda: List<Punkt>,
        aktien: List<Punkt>, schulden: List<Punkt>, kasse: List<Punkt>,
    ): List<Basis> {
        fun karte(l: List<Punkt>) = l.associate { it.tag to it.wert }
        val e = karte(eps); val u = karte(umsatz); val c = karte(cash); val b = karte(ebitda)
        val tage = (e.keys + u.keys + c.keys + b.keys).toSortedSet()
        return tage.map { t ->
            Basis(t, e[t], u[t], c[t], b[t], bestandZum(aktien, t), bestandZum(schulden, t), bestandZum(kasse, t))
        }
    }

    // ------------------------------------------------------------ Vielfache

    // Vielfaches an einem Stichtag zum Kurs (in der Berichtswaehrung). Nur positive, sinnvolle Werte.
    fun vielfach(art: Art, b: Basis, kurs: Double): Double? {
        if (!kurs.isFinite() || kurs <= 0) return null
        val wert = b.aktien?.takeIf { it > 0 }?.let { kurs * it }
        val v = when (art) {
            Art.KGV -> b.eps?.takeIf { it > 0 }?.let { kurs / it }
            Art.KUV -> wert?.let { w -> b.umsatz?.takeIf { it > 0 }?.let { w / it } }
            Art.KCV -> wert?.let { w -> b.cashflow?.takeIf { it > 0 }?.let { w / it } }
            Art.EV_EBITDA -> wert?.let { w -> b.ebitda?.takeIf { it > 0 }?.let { (w + (b.schulden ?: 0.0) - (b.kasse ?: 0.0)) / it } }
        }
        return v?.takeIf { it.isFinite() && it > 0 && it < 1000 }
    }

    // Reihe je Stichtag; kurse in Handelswaehrung, faktor rechnet den Kurs in die Berichtswaehrung um.
    fun reihe(art: Art, basen: List<Basis>, kurse: List<Candle>, faktor: Double): List<Punkt> =
        basen.mapNotNull { b ->
            val k = FinderRueckblick.kursZum(kurse, b.tag)?.times(faktor) ?: return@mapNotNull null
            vielfach(art, b, k)?.let { Punkt(b.tag, it) }
        }

    // Faktor vom Kurs in der Handelswaehrung zur Berichtswaehrung, wenn beide dieselbe Waehrung meinen (Pence, Agorot,
    // Cent als Unterwaehrung); sonst null (dann keine Spanne, ein Wechselkurs wuerde die Stichtage verzerren).
    fun waehrungsFaktor(handel: String, bericht: String): Double? {
        if (handel.isBlank() || bericht.isBlank() || handel == bericht) return 1.0
        return when {
            (handel == "GBp" || handel == "GBX") && bericht == "GBP" -> 0.01
            handel == "ILA" && bericht == "ILS" -> 0.01
            handel == "ZAc" && bericht == "ZAR" -> 0.01
            else -> null
        }
    }

    // Marktwert je Stichtag (Kurs mal Aktienzahl) in der Handelswaehrung; kurse in Handelswaehrung. Aktienzahl in
    // Berichtswaehrung ist eine Stueckzahl und braucht keine Umrechnung.
    fun marktwerte(basen: List<Basis>, kurse: List<Candle>): List<Punkt> =
        basen.mapNotNull { b ->
            val a = b.aktien?.takeIf { it > 0 } ?: return@mapNotNull null
            val k = FinderRueckblick.kursZum(kurse, b.tag) ?: return@mapNotNull null
            Punkt(b.tag, k * a)
        }.distinctBy { it.tag }

    // ------------------------------------------------------------ SEC XBRL (companyconcept)

    // periodenwert: start null bei Bestandsgroessen; eingereicht: Tag des Berichts, aus dem der Wert stammt.
    class Fakt(val start: Long?, val ende: Long, val wert: Double, val eingereicht: Long)

    fun conceptAdresse(cik: String, namensraum: String, begriff: String) =
        "https://data.sec.gov/api/xbrl/companyconcept/CIK" + cik.trimStart('0').padStart(10, '0') + "/$namensraum/$begriff.json"

    // Liest die Antwort von companyconcept; es gilt die genannte Einheit, sonst die mit den meisten Eintraegen. Je Periode zaehlt der juengste Bericht.
    fun faktenLesen(text: String, einheit: String? = null): List<Fakt> {
        val wurzel = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
        val einheiten = wurzel["units"] as? JsonObject ?: return emptyList()
        val liste = (if (einheit != null) einheiten[einheit] as? JsonArray else einheiten.values.mapNotNull { it as? JsonArray }.maxByOrNull { it.size })
            ?: return emptyList()
        val aus = HashMap<Pair<Long?, Long>, Fakt>()
        for (e in liste) {
            val o = e as? JsonObject ?: continue
            val ende = (o["end"] as? JsonPrimitive)?.content?.let { datum(it) } ?: continue
            val start = (o["start"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.let { datum(it) }
            val wert = (o["val"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
            val filed = (o["filed"] as? JsonPrimitive)?.content?.let { datum(it) } ?: ende
            val k = start to ende
            val alt = aus[k]
            if (alt == null || filed >= alt.eingereicht) aus[k] = Fakt(start, ende, wert, filed)
        }
        return aus.values.sortedBy { it.ende }
    }

    // Werte je Aktie mit Splits nach dem Berichtstag auf den heutigen Aktienstand: Gewinn je Aktie durch den Faktor,
    // Aktienzahl mal Faktor.
    fun splitAngepasst(f: List<Fakt>, splits: List<Splits.Split>, jeAktie: Boolean): List<Fakt> =
        if (splits.isEmpty()) f else f.map {
            val faktor = FinderRueckblick.splitFaktor(splits, it.eingereicht)
            if (faktor == 1.0) it else Fakt(it.start, it.ende, if (jeAktie) it.wert / faktor else it.wert * faktor, it.eingereicht)
        }

    private fun tage(f: Fakt): Long = f.ende - (f.start ?: f.ende)

    // TTM je Periodenende: ein Geschaeftsjahr ist sein eigener TTM-Wert; ein Zeitraum seit Jahresbeginn (drei, sechs, neun
    // Monate) ergibt sich als Jahr davor plus Zeitraum minus gleicher Zeitraum des Vorjahres. Fehlt ein Teil, entfaellt das Ende.
    fun ttm(fakten: List<Fakt>): List<Punkt> {
        val dauer = fakten.filter { it.start != null }
        val jahre = dauer.filter { tage(it) in 350..380 }
        val aus = HashMap<Long, Double>()
        for (f in dauer) {
            val d = tage(f)
            if (d !in 80..290) continue
            val s = f.start ?: continue
            val vorjahr = jahre.firstOrNull { abs(it.ende - (s - 1)) <= 12 } ?: continue
            val gleich = dauer.firstOrNull { abs((it.start ?: -1000) - (s - 365)) <= 12 && abs(it.ende - (f.ende - 365)) <= 12 && abs(tage(it) - d) <= 12 } ?: continue
            aus[f.ende] = vorjahr.wert + f.wert - gleich.wert
        }
        for (j in jahre) aus[j.ende] = j.wert
        return aus.map { Punkt(it.key, it.value) }.sortedBy { it.tag }
    }

    fun bestaende(fakten: List<Fakt>): List<Punkt> =
        fakten.filter { it.start == null }.map { Punkt(it.ende, it.wert) }.sortedBy { it.tag }

    // Summe zweier TTM-Reihen an den gemeinsamen Tagen (EBIT plus Abschreibungen).
    fun summe(a: List<Punkt>, b: List<Punkt>): List<Punkt> {
        val m = b.associate { it.tag to it.wert }
        return a.mapNotNull { p -> m[p.tag]?.let { Punkt(p.tag, p.wert + it) } }
    }

    fun zusammen(vararg reihen: List<Punkt>?): List<Punkt> = mischen(*reihen)

    // ------------------------------------------------------------ Leerverkauf (Nasdaq)

    // aktien: offene Leerverkaeufe zum Stichtag; volumen: durchschnittliches Tagesvolumen; deckung: Tage bis Deckung.
    class Leer(val tag: Long, val aktien: Double, val volumen: Double, val deckung: Double?)

    fun leerAdresse(symbol: String) = "https://api.nasdaq.com/api/quote/" + symbol.trim().uppercase() + "/short-interest?assetClass=stocks"

    private fun zahl(s: String?): Double? = s?.replace(",", "")?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    // Stichtag als MM/TT/JJJJ
    fun usDatum(s: String): Long? {
        val t = s.trim().split('/')
        if (t.size != 3) return null
        return runCatching { LocalDate.of(t[2].toInt(), t[0].toInt(), t[1].toInt()).toEpochDay() }.getOrNull()
    }

    // Liest short-interest der Nasdaq; null bei unlesbarem Text oder Fehler-rCode. Nur an der Nasdaq notierte Werte haben
    // Daten: dort ist data null (Antwort 200), das ergibt eine leere Liste. Aelteste zuerst.
    fun leerLesen(text: String): List<Leer>? {
        val wurzel = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val rc = ((wurzel["status"] as? JsonObject)?.get("rCode") as? JsonPrimitive)?.content?.toIntOrNull()
        if (rc != null && rc !in 200..299) return null
        val daten = wurzel["data"] as? JsonObject ?: return if (wurzel.containsKey("data")) emptyList() else null
        val zeilen = ((daten["shortInterestTable"] as? JsonObject)?.get("rows") as? JsonArray).orEmpty()
        return zeilen.mapNotNull { z ->
            val o = z as? JsonObject ?: return@mapNotNull null
            val tag = (o["settlementDate"] as? JsonPrimitive)?.content?.let { usDatum(it) } ?: return@mapNotNull null
            val a = zahl((o["interest"] as? JsonPrimitive)?.content) ?: return@mapNotNull null
            val v = zahl((o["avgDailyShareVolume"] as? JsonPrimitive)?.content) ?: 0.0
            val d = zahl((o["daysToCover"] as? JsonPrimitive)?.content)
            Leer(tag, a, v, d)
        }.sortedBy { it.tag }
    }

    // Streubesitz aus Yahoo: offene Leerverkaeufe geteilt durch den Anteil am Streubesitz (in Prozent) desselben Stands.
    fun streubesitz(leerAktien: Double, leerQuotePct: Double): Double? =
        if (leerAktien > 0 && leerQuotePct > 0) leerAktien / (leerQuotePct / 100.0) else null

    // Nasdaq-Kennungen bei Yahoo (NMS, NGM, NCM, sowie NasdaqGS und aehnlich)
    fun nasdaqBoerse(kuerzel: String): Boolean {
        val k = kuerzel.trim().uppercase()
        return k == "NMS" || k == "NGM" || k == "NCM" || k.startsWith("NASDAQ")
    }

    // ------------------------------------------------------------ Fail-to-Deliver (SEC)

    class Ftd(val tag: Long, val menge: Double)

    const val FTD_BASIS = "https://www.sec.gov/files/data/fails-deliver-data/"

    // Dateinamen der letzten Halbmonate, neueste zuerst. Die SEC stellt eine Datei etwa zwei Wochen nach dem Ende ihres
    // Zeitraums ein; erste Haelfte „a“ (1. bis 15.), zweite „b“.
    fun ftdAdressen(heute: LocalDate, anzahl: Int): List<String> {
        var d = heute.minusDays(14)
        var jahr = d.year
        var monat = d.monthValue
        var haelfte = if (d.dayOfMonth <= 15) 0 else 1
        val aus = ArrayList<String>()
        repeat(anzahl) {
            aus += FTD_BASIS + "cnsfails" + jahr + monat.toString().padStart(2, '0') + (if (haelfte == 0) "a" else "b") + ".zip"
            if (haelfte == 1) haelfte = 0 else {
                haelfte = 1
                monat -= 1
                if (monat == 0) { monat = 12; jahr -= 1 }
            }
        }
        return aus
    }

    // Zeilen im Format TAG|CUSIP|KUERZEL|MENGE|BESCHREIBUNG|KURS mit Tag als JJJJMMTT; behalten wird nur das gesuchte Kuerzel.
    // text: der ganze Inhalt der Datei, zeilenweise gelesen.
    fun ftdLesen(text: String, kuerzel: Set<String>): List<Ftd> {
        val aus = ArrayList<Ftd>()
        var i = 0
        val n = text.length
        while (i < n) {
            var j = text.indexOf('\n', i)
            if (j < 0) j = n
            // Zeile i bis j, nur wenn das Kuerzel (dritte Spalte) vorkommt, in Felder zerlegen
            val z = text.substring(i, j).trimEnd('\r')
            i = j + 1
            val f = z.split('|')
            if (f.size < 4 || f[2].trim().uppercase() !in kuerzel) continue
            val t = f[0].trim()
            if (t.length != 8) continue
            val tag = runCatching { LocalDate.of(t.substring(0, 4).toInt(), t.substring(4, 6).toInt(), t.substring(6, 8).toInt()).toEpochDay() }.getOrNull() ?: continue
            val m = f[3].trim().toDoubleOrNull() ?: continue
            aus += Ftd(tag, m)
        }
        return aus
    }

    // Kuerzel in den Schreibweisen der SEC (Punkt oder Strich als Klassentrenner, oder ohne)
    fun ftdKuerzel(symbol: String): Set<String> {
        val s = symbol.trim().uppercase()
        return setOf(s, s.replace('-', '.'), s.replace('.', '-'), s.replace("-", "").replace(".", ""))
    }

    // ------------------------------------------------------------ Reg-SHO-Schwellenliste (Nasdaq Trader)

    fun regShoAdresse(tag: LocalDate): String =
        "https://www.nasdaqtrader.com/dynamic/symdir/regsho/nasdaqth" + tag.year + tag.monthValue.toString().padStart(2, '0') + tag.dayOfMonth.toString().padStart(2, '0') + ".txt"

    // Letzte Handelstage vor heute (ohne Wochenende), neueste zuerst; die Liste eines Tages erscheint am Abend.
    fun regShoTage(heute: LocalDate, anzahl: Int): List<LocalDate> {
        val aus = ArrayList<LocalDate>()
        var d = heute
        while (aus.size < anzahl) {
            val w = d.dayOfWeek.value
            if (w < 6) aus += d
            d = d.minusDays(1)
        }
        return aus
    }

    // Kuerzel der Liste (Spalte Symbol, Flag Y); null, wenn der Text keine Kopfzeile der Liste hat.
    fun regShoLesen(text: String): Set<String>? {
        val zeilen = text.lines()
        if (zeilen.firstOrNull()?.startsWith("Symbol|") != true) return null
        return zeilen.drop(1).mapNotNull { z ->
            val f = z.split('|')
            if (f.size >= 4 && f[3].trim() == "Y") f[0].trim().uppercase() else null
        }.toSet()
    }

    // ------------------------------------------------------------ Export als CSV

    private fun feld(s: String, trenner: Char) =
        if (s.any { it == trenner || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun zahlText(v: Double, deutsch: Boolean): String {
        if (!v.isFinite()) return ""
        val s = java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
        return if (deutsch) s.replace('.', ',') else s
    }

    // Kurse als OHLCV, ein Tag je Zeile: Datum, Eroeffnung, Hoch, Tief, Schluss, Volumen.
    fun ohlcvCsv(kerzen: List<Candle>, deutsch: Boolean): String {
        val t = if (deutsch) ';' else ','
        val sb = StringBuilder()
        sb.append(if (deutsch) "Datum;Eroeffnung;Hoch;Tief;Schluss;Volumen" else "Date,Open,High,Low,Close,Volume").append("\r\n")
        for (c in kerzen) {
            val tag = Math.floorDiv(c.t, 86_400_000L)
            sb.append(listOf(LocalDate.ofEpochDay(tag).toString(), zahlText(c.open, deutsch), zahlText(c.high, deutsch), zahlText(c.low, deutsch), zahlText(c.close, deutsch), zahlText(c.volume, deutsch))
                .joinToString(t.toString()) { feld(it, t) }).append("\r\n")
        }
        return sb.toString()
    }

    // Finanzen als CSV: je Zeile ein Berichtszeitraum mit Gewinnrechnung, Bilanz und Cashflow. Was die Quelle nicht nennt,
    // bleibt leer (keine 0). waehrung nur, wenn sie von der Berichtswaehrung des Aufrufs abweicht.
    class FinZeile(
        val art: String, val ende: String, val umsatz: Double?, val brutto: Double?, val betrieb: Double?, val gewinn: Double?,
        val bilanzsumme: Double? = null, val verbindlichkeiten: Double? = null, val eigenkapital: Double? = null,
        val langfristSchulden: Double? = null, val cashflow: Double? = null, val investitionen: Double? = null,
        val freierCashflow: Double? = null, val waehrung: String? = null,
    )

    // Jahreszeilen aus den vollstaendigen Abschluessen (Bilanz und Cashflow dabei); fehlen sie, die Jahre des Profils.
    // Quartale kommen aus dem Profil.
    fun finZeilen(jahre: List<Qualitaet.Jahr>, profilJahre: List<FinZeile>, profilQuartale: List<FinZeile>): List<FinZeile> {
        val ausAbschluss = jahre.filter { it.umsatz != null || it.nettogewinn != null || it.bilanzsumme != null }.map { j ->
            FinZeile(
                "Y", j.ende, j.umsatz, j.bruttogewinn, j.ebit, j.nettogewinn,
                bilanzsumme = j.bilanzsumme, verbindlichkeiten = j.verbindlichkeiten,
                eigenkapital = j.bilanzsumme?.let { b -> j.verbindlichkeiten?.let { b - it } },
                langfristSchulden = j.langfristSchulden, cashflow = j.cashflow, investitionen = j.capex,
                freierCashflow = j.freierCashflow ?: j.cashflow?.let { c -> j.capex?.let { k -> c - kotlin.math.abs(k) } },
                waehrung = j.waehrung.ifBlank { null },
            )
        }
        return ausAbschluss.ifEmpty { profilJahre } + profilQuartale
    }

    fun finanzenCsv(zeilen: List<FinZeile>, waehrung: String, deutsch: Boolean): String {
        val t = if (deutsch) ';' else ','
        val sb = StringBuilder()
        sb.append(
            if (deutsch) "Art;Periode;Waehrung;Umsatz;Bruttogewinn;Betriebsergebnis;Gewinn;Bilanzsumme;Verbindlichkeiten;Eigenkapital;Langfristige Schulden;Operativer Cashflow;Investitionen;Freier Cashflow"
            else "Type,Period,Currency,Revenue,Gross profit,Operating income,Net income,Total assets,Total liabilities,Equity,Long-term debt,Operating cash flow,Capital expenditure,Free cash flow",
        ).append("\r\n")
        fun z(v: Double?) = v?.let { zahlText(it, deutsch) }.orEmpty()
        for (e in zeilen) {
            sb.append(
                listOf(
                    e.art, e.ende, e.waehrung ?: waehrung, z(e.umsatz), z(e.brutto), z(e.betrieb), z(e.gewinn), z(e.bilanzsumme), z(e.verbindlichkeiten),
                    z(e.eigenkapital), z(e.langfristSchulden), z(e.cashflow), z(e.investitionen), z(e.freierCashflow),
                ).joinToString(t.toString()) { feld(it, t) },
            ).append("\r\n")
        }
        return sb.toString()
    }
}
