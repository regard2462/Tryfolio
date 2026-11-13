package de.troetenrepublik.app.market

import java.io.InputStream
import kotlin.math.abs

// Umsatz nach Segment, Region und Grosskunde aus der XBRL-Instanz eines Jahresberichts (10-K, 20-F, 40-F). Die Instanz
// wird als Strom gelesen: nur Kontexte mit bekannten Achsen und die Umsatz- und Konzentrationsangaben bleiben im Speicher.
// Die Kontexte tragen Achsenglieder, die jede Firma selbst benennt; die Bezeichnung wird aus dem Gliednamen lesbar gemacht.
object Xbrl {
    // Der Bericht ist groesser als erlaubt (gezaehlt werden die entpackten Bytes)
    class ZuGross : RuntimeException("Bericht zu gross")

    // achsen: lokaler Achsenname zu Glied (mit Namensraum-Vorsatz); von leer bei Stichtagen
    class Kontext(val achsen: Map<String, String>, val von: String, val bis: String)

    class Fakt(val name: String, val ctx: String, val wert: Double)

    class Roh(val kontexte: Map<String, Kontext>, val fakten: List<Fakt>, val dei: Map<String, String>)

    // Ergebnis des Lesens im Strom: Rohdaten oder Ueberschreitung der Grenze
    sealed class Gelesen {
        class Ok(val roh: Roh) : Gelesen()
        object Gross : Gelesen()
    }

    fun lesenGrenze(ein: InputStream, max: Long): Gelesen = try { Gelesen.Ok(lesen(ein, max)) } catch (_: ZuGross) { Gelesen.Gross }

    private const val SEGMENT = "StatementBusinessSegmentsAxis"
    private const val SEGMENT_IFRS = "SegmentsAxis"
    private const val REGION = "StatementGeographicalAxis"
    private const val REGION_IFRS = "GeographicalAreasAxis"
    private const val KUNDEN = "MajorCustomersAxis"
    private const val BENCHMARK = "ConcentrationRiskByBenchmarkAxis"
    private const val ART = "ConcentrationRiskByTypeAxis"
    private val SEGMENT_ACHSEN = setOf(SEGMENT, SEGMENT_IFRS)
    private val REGION_ACHSEN = setOf(REGION, REGION_IFRS)
    private val KONS_ACHSEN = setOf("ConsolidationItemsAxis", "SegmentConsolidationItemsAxis")
    private val ACHSEN = SEGMENT_ACHSEN + REGION_ACHSEN + KONS_ACHSEN + setOf(KUNDEN, BENCHMARK, ART)
    private val UMSATZ = setOf(
        "Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax", "RevenueFromContractWithCustomerIncludingAssessedTax",
        "SalesRevenueNet", "Revenue",
    )
    private const val KONZENTRATION = "ConcentrationRiskPercentage"
    private val DEI = setOf("DocumentPeriodEndDate", "DocumentFiscalYearFocus", "DocumentType")

    // ------------------------------------------------------------ Lesen im Strom

    private class Quelle(private val ein: InputStream, private val max: Long) {
        private val puffer = ByteArray(65_536)
        private var pos = 0
        private var len = 0
        private var summe = 0L

        fun lies(): Int {
            if (pos >= len) {
                len = ein.read(puffer, 0, puffer.size)
                pos = 0
                if (len <= 0) { len = 0; return -1 }
                summe += len
                if (summe > max) throw ZuGross()
            }
            return puffer[pos++].toInt() and 0xFF
        }
    }

    private class Bytes {
        var a = ByteArray(256)
        var n = 0
        fun add(b: Int) {
            if (n == a.size) a = a.copyOf(n * 2)
            a[n++] = b.toByte()
        }
        fun text(): String = a.decodeToString(0, n)
    }

    private fun attr(s: String, name: String): String {
        var i = s.indexOf(name)
        while (i >= 0) {
            val vor = if (i == 0) ' ' else s[i - 1]
            var j = i + name.length
            if (vor <= ' ') {
                while (j < s.length && s[j] == ' ') j++
                if (j < s.length && s[j] == '=') {
                    j++
                    while (j < s.length && s[j] == ' ') j++
                    if (j < s.length && (s[j] == '"' || s[j] == '\'')) {
                        val e = s.indexOf(s[j], j + 1)
                        if (e > 0) return s.substring(j + 1, e)
                    }
                }
            }
            i = s.indexOf(name, i + 1)
        }
        return ""
    }

    private fun lokal(s: String): String = s.substringAfter(':')

    fun lesen(ein: InputStream, max: Long): Roh {
        val q = Quelle(ein, max)
        val tag = Bytes()
        val text = Bytes()
        val kontexte = HashMap<String, Kontext>()
        val fakten = ArrayList<Fakt>()
        val dei = HashMap<String, String>()
        var inKontext = false
        var kId = ""
        var kAchsen = HashMap<String, String>()
        var kFremd = false
        var kVon = ""
        var kBis = ""
        var kStichtag = ""
        // 0 nichts, 1 Achsenglied, 2 Beginn, 3 Ende, 4 Stichtag, 5 Fakt, 6 Angabe zum Dokument
        var erfasse = 0
        var dimension = ""
        var faktName = ""
        var faktKontext = ""
        var deiName = ""
        var c = q.lies()
        while (c >= 0) {
            if (c != '<'.code) {
                if (erfasse != 0 && text.n < 2048) text.add(c)
                c = q.lies()
                continue
            }
            tag.n = 0
            var anf = 0
            while (true) {
                c = q.lies()
                if (c < 0) break
                if (anf != 0) { tag.add(c); if (c == anf) anf = 0 }
                else if (c == '"'.code || c == '\''.code) { anf = c; tag.add(c) }
                else if (c == '>'.code) break
                else tag.add(c)
            }
            if (c < 0) break
            c = q.lies()
            if (tag.n == 0) continue
            val b0 = tag.a[0].toInt()
            if (b0 == '?'.code || b0 == '!'.code) continue
            if (b0 == '/'.code) {
                val name = lokal(tag.text().substring(1).trim())
                when (erfasse) {
                    1 -> if (dimension.isNotEmpty()) kAchsen[lokal(dimension)] = text.text().trim()
                    2 -> kVon = text.text().trim()
                    3 -> kBis = text.text().trim()
                    4 -> kStichtag = text.text().trim()
                    5 -> text.text().trim().toDoubleOrNull()?.let { fakten += Fakt(faktName, faktKontext, it) }
                    6 -> dei[deiName] = text.text().trim()
                }
                erfasse = 0
                if (inKontext && name == "context") {
                    inKontext = false
                    if (!kFremd && kAchsen.keys.all { it in ACHSEN }) {
                        val bis = if (kStichtag.isNotEmpty()) kStichtag else kBis
                        if (bis.isNotEmpty()) kontexte[kId] = Kontext(kAchsen, if (kStichtag.isNotEmpty()) "" else kVon, bis)
                    }
                }
                continue
            }
            val s = tag.text()
            var e = 0
            while (e < s.length && s[e] > ' ' && s[e] != '/') e++
            val voll = s.substring(0, e)
            val selbst = s.endsWith("/")
            val ln = lokal(voll)
            if (!inKontext) {
                if (selbst) continue
                if (ln == "context") {
                    inKontext = true
                    kId = attr(s, "id")
                    kAchsen = HashMap(); kFremd = false; kVon = ""; kBis = ""; kStichtag = ""
                } else {
                    val prefix = if (voll.contains(':')) voll.substringBefore(':') else ""
                    if (prefix == "dei" && ln in DEI) {
                        erfasse = 6; deiName = ln; text.n = 0
                    } else if ((prefix == "us-gaap" || prefix == "ifrs-full") && (ln in UMSATZ || ln.startsWith(KONZENTRATION))) {
                        if (!s.contains("nil=\"true\"") && !s.contains("nil='true'")) {
                            erfasse = 5; faktName = ln; faktKontext = attr(s, "contextRef"); text.n = 0
                        }
                    }
                }
            } else when (ln) {
                "explicitMember" -> if (!selbst) { erfasse = 1; dimension = attr(s, "dimension"); text.n = 0 }
                "typedMember" -> kFremd = true
                "startDate" -> if (!selbst) { erfasse = 2; text.n = 0 }
                "endDate" -> if (!selbst) { erfasse = 3; text.n = 0 }
                "instant" -> if (!selbst) { erfasse = 4; text.n = 0 }
            }
        }
        return Roh(kontexte, fakten, dei)
    }

    // ------------------------------------------------------------ Auswerten

    // Tage seit 1970 aus JJJJ-MM-TT; -1 bei unlesbarer Angabe (ohne java.time)
    internal fun tage(iso: String): Long {
        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(iso) ?: return -1
        val (y0, mo, d) = m.destructured.let { Triple(it.component1().toInt(), it.component2().toInt(), it.component3().toInt()) }
        val y = if (mo <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (mo > 2) mo - 3 else mo + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    private fun jahresZeitraum(k: Kontext, stichtag: String): Boolean {
        if (k.von.isEmpty() || k.bis != stichtag) return false
        val t = tage(k.bis) - tage(k.von)
        return t in 340..380
    }

    private fun anteilWort(s: String): String = when (s.lowercase()) {
        "one" -> "1"; "two" -> "2"; "three" -> "3"; "four" -> "4"; "five" -> "5"; "six" -> "6"; "seven" -> "7"; "eight" -> "8"
        "nine" -> "9"; "ten" -> "10"; else -> s.uppercase()
    }

    // CamelCase in Woerter trennen: „ProductivityAndBusinessProcesses“ zu „Productivity And Business Processes“
    internal fun woerter(s: String): String {
        val sb = StringBuilder()
        for (i in s.indices) {
            val ch = s[i]
            if (i > 0) {
                val vor = s[i - 1]
                val danach = s.getOrNull(i + 1)
                val bruch = (vor.isLowerCase() || vor.isDigit()) && ch.isUpperCase() ||
                    vor.isUpperCase() && ch.isUpperCase() && danach != null && danach.isLowerCase()
                if (bruch) sb.append(' ')
            }
            sb.append(ch)
        }
        return sb.toString()
    }

    private fun kern(glied: String): String = lokal(glied).removeSuffix("Member")

    // Art der Zeile: 0 Name, 1 Kunde (nr: A, 1 ...), 2 Kundengruppe (nr: Anzahl), 3 Land (l: Kuerzel), 4 Sitzland, 5 ausserhalb der USA
    private fun posten(glied: String, wert: Double, kunde: Boolean): SecBericht.Posten {
        val k = kern(glied)
        val klein = k.lowercase()
        if (kunde) {
            Regex("^(?:Major)?Customer([A-Z]|\\d+|One|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten)$").find(k)?.let {
                return SecBericht.Posten(woerter(k), wert, k = 1, nr = anteilWort(it.groupValues[1]))
            }
            Regex("^(One|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten|\\d+)(?:Largest|Major|Significant)?Customers$").find(k)?.let {
                return SecBericht.Posten(woerter(k), wert, k = 2, nr = anteilWort(it.groupValues[1]))
            }
            return SecBericht.Posten(woerter(k), wert)
        }
        if (Regex("^[A-Z]{2}$").matches(k)) return SecBericht.Posten(k, wert, l = k, k = 3)
        if (klein == "countryofdomicile" || klein == "domestic" || klein == "domesticcountry") return SecBericht.Posten(woerter(k), wert, k = 4)
        if (klein == "nonus" || klein == "nonunitedstates" || klein == "foreigncountries") return SecBericht.Posten(woerter(k), wert, k = 5)
        val name = woerter(k.removeSuffix("Segments").removeSuffix("Segment")).ifBlank { woerter(k) }
        return SecBericht.Posten(name, wert)
    }

    // Glieder, die keine eigene Zeile sein sollen: Summen, Eliminierungen, Ueberleitungen, Zentrale ohne Umsatz
    private fun ausgeschlossen(glied: String, wert: Double): Boolean {
        val k = kern(glied).lowercase()
        if (wert <= 0.0) return true
        if (k.contains("elimination") || k.contains("reconcil") || k.contains("intersegment")) return true
        if (k.contains("total") || k.startsWith("consolidated") || k == "allsegments") return true
        return false
    }

    private fun operativ(glied: String): Boolean {
        val k = kern(glied).lowercase()
        return k.contains("operatingsegment") || k.contains("reportablesegment")
    }

    private fun aggregatAus(m: Pair<String, Double>, rest: List<Pair<String, Double>>): Boolean {
        val andere = rest.filter { it !== m }.map { it.second }
        val n = andere.size
        if (n < 2) return false
        val tol = maxOf(m.second * 0.002, 1.0)
        for (maske in 1 until (1 shl n)) {
            if (maske.countOneBits() < 2) continue
            var s = 0.0
            for (i in 0 until n) if ((maske shr i) and 1 == 1) s += andere[i]
            if (abs(s - m.second) <= tol) return true
        }
        return false
    }

    // Glieder einer Gruppe: Summen und Eliminierungen weg, Oberbegriffe (Glieder, die Summe anderer sind) weg, Summe zwischen
    // 90 und 110 % des Gesamtumsatzes. Null, wenn das nicht aufgeht.
    private fun bereinigen(roh: Map<String, Double>, gesamt: Double): List<Pair<String, Double>>? {
        var w = roh.filter { !ausgeschlossen(it.key, it.value) }.toList().sortedByDescending { it.second }
        if (w.isEmpty()) return null
        while (w.sumOf { it.second } > 1.1 * gesamt) {
            if (w.size > 16) return null
            val oberbegriff = w.firstOrNull { aggregatAus(it, w) } ?: return null
            w = w.filter { it !== oberbegriff }
        }
        val s = w.sumOf { it.second }
        return if (s >= 0.9 * gesamt && s <= 1.1 * gesamt) w else null
    }

    // Eine Gruppe: Posten, Status (0 vorhanden, 1 nicht eindeutig, 2 nicht gemeldet)
    private fun gruppe(einfach: Map<String, Double>, kreuz: Map<String, Double>, gesamt: Double): Pair<List<SecBericht.Posten>, Int> {
        if (einfach.isEmpty() && kreuz.isEmpty()) return emptyList<SecBericht.Posten>() to 2
        for (m in listOf(einfach, kreuz)) {
            if (m.isEmpty()) continue
            val w = bereinigen(m, gesamt) ?: continue
            val summe = w.sumOf { it.second }
            return w.map { posten(it.first, it.second / summe, false) } to 0
        }
        return emptyList<SecBericht.Posten>() to 1
    }

    private fun kunden(roh: Roh, stichtag: String, gesamt: Double): List<SecBericht.Posten> {
        val erlaubt = setOf(KUNDEN, BENCHMARK, ART)
        val anteile = HashMap<String, Double>()
        for (f in roh.fakten) {
            if (!f.name.startsWith(KONZENTRATION)) continue
            val k = roh.kontexte[f.ctx] ?: continue
            if (!jahresZeitraum(k, stichtag) || KUNDEN !in k.achsen || !k.achsen.keys.all { it in erlaubt }) continue
            val b = k.achsen[BENCHMARK]?.let { kern(it).lowercase() } ?: continue
            if ((!b.contains("revenue") && !b.contains("sales")) || b.contains("receivable")) continue
            if (k.achsen[ART]?.let { kern(it).lowercase().contains("customer") } == false) continue
            val v = if (f.wert > 1.0) f.wert / 100.0 else f.wert
            if (v <= 0.0 || v > 1.0) continue
            val g = k.achsen.getValue(KUNDEN)
            anteile[g] = maxOf(anteile[g] ?: 0.0, v)
        }
        if (anteile.isEmpty() && gesamt > 0) {
            for (f in roh.fakten) {
                if (f.name !in UMSATZ) continue
                val k = roh.kontexte[f.ctx] ?: continue
                if (!jahresZeitraum(k, stichtag) || KUNDEN !in k.achsen || !k.achsen.keys.all { it in erlaubt }) continue
                val v = f.wert / gesamt
                if (v <= 0.0 || v > 1.0) continue
                val g = k.achsen.getValue(KUNDEN)
                anteile[g] = maxOf(anteile[g] ?: 0.0, v)
            }
        }
        if (anteile.isEmpty() || anteile.values.sum() > 1.05) return emptyList()
        return anteile.entries.sortedByDescending { it.value }.map { posten(it.key, it.value, true) }
    }

    // Auswertung der Rohdaten; null, wenn weder Stichtag noch Gesamtumsatz zu finden sind. Der Wert eines Postens ist bei
    // Segmenten und Regionen der Anteil an der Summe der Gruppe, bei Grosskunden der Anteil am Umsatz.
    fun auswerten(roh: Roh): SecBericht.Auswertung? {
        val stichtag = roh.dei["DocumentPeriodEndDate"]?.let { Regex("\\d{4}-\\d{2}-\\d{2}").find(it)?.value }
            ?: roh.fakten.mapNotNull { f -> roh.kontexte[f.ctx]?.takeIf { f.name in UMSATZ && it.achsen.isEmpty() && it.von.isNotEmpty() && tage(it.bis) - tage(it.von) in 340..380 }?.bis }.maxOrNull()
            ?: return null
        val umsatz = roh.fakten.filter { it.name in UMSATZ }.mapNotNull { f ->
            val k = roh.kontexte[f.ctx] ?: return@mapNotNull null
            if (jahresZeitraum(k, stichtag)) k to f.wert else null
        }
        val gesamt = umsatz.filter { it.first.achsen.isEmpty() }.maxOfOrNull { it.second } ?: return null
        if (gesamt <= 0.0) return null

        // Einachsig: genau eine Achse der Gruppe, daneben nur Glieder der operativen Segmente. Kreuz: Summe ueber die andere
        // Achse (Segment mal Region), einschliesslich der Eliminierungen je Segment.
        fun sammeln(achsen: Set<String>, andere: Set<String>): Pair<Map<String, Double>, Map<String, Double>> {
            val einfach = HashMap<String, Double>()
            val kreuz = HashMap<String, Double>()
            for ((k, w) in umsatz) {
                val a = k.achsen.keys.filter { it in achsen }
                if (a.size != 1) continue
                val glied = k.achsen.getValue(a[0])
                val rest = k.achsen.filterKeys { it != a[0] }
                if (rest.isEmpty() || rest.all { it.key in KONS_ACHSEN && operativ(it.value) }) {
                    einfach[glied] = maxOf(einfach[glied] ?: Double.NEGATIVE_INFINITY, w)
                } else if (rest.keys.all { it in andere || it in KONS_ACHSEN }) {
                    kreuz[glied] = (kreuz[glied] ?: 0.0) + w
                }
            }
            return einfach to kreuz
        }
        val (segEin, segKreuz) = sammeln(SEGMENT_ACHSEN, REGION_ACHSEN)
        val (regEin, regKreuz) = sammeln(REGION_ACHSEN, SEGMENT_ACHSEN)
        val (seg, segStatus) = gruppe(segEin, segKreuz, gesamt)
        val (reg, regStatus) = gruppe(regEin, regKreuz, gesamt)
        val jahr = roh.dei["DocumentFiscalYearFocus"]?.trim()?.toIntOrNull() ?: stichtag.substring(0, 4).toInt()
        return SecBericht.Auswertung(
            form = roh.dei["DocumentType"].orEmpty(), stichtag = stichtag, jahr = jahr, gesamt = gesamt,
            segmente = seg, regionen = reg, kunden = kunden(roh, stichtag, gesamt),
            segmenteStatus = segStatus, regionenStatus = regStatus,
        )
    }
}
