package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import java.io.InputStream

// Angaben aus dem Hauptdokument eines Jahresberichts (10-K, 20-F, 40-F), gelesen im Strom: Das HTML wird beim Lesen zu Text
// mit Zeilen je Absatz, die Angaben des Deckblatts zum Pruefer (dei:AuditorName, AuditorFirmId, AuditorLocation, seit 2021)
// bleiben als Marken im Text. Daraus kommen Pruefer, Amtszeit, Zweifel an der Fortfuehrung, wesentliche Schwaeche der
// Kontrollen (Item 9A), Critical Audit Matters (Titel) und die Verfahren aus Item 3 und der Anhangangabe zu Rechtsstreit.
// Reine Funktionen, ohne Netz.
object Jahresbericht {
    // Der Bericht ist groesser als erlaubt (gezaehlt werden Zeichen)
    class ZuGross : RuntimeException("Bericht zu gross")

    const val MAX_ZEICHEN = 30_000_000

    // Ein Verfahren aus dem Bericht: Titel (Zwischenueberschrift), Gericht oder Behoerde, wo genannt, und das Jahr der
    // ersten Datumsangabe im Absatz (Beginn).
    @Serializable
    data class Verfahren(val titel: String, val stelle: String = "", val jahr: Int = 0)

    @Serializable
    data class Befund(
        val pruefer: String = "",
        val pruefernr: String = "",
        val ort: String = "",
        // Jahr, seit dem die Firma den Pruefer hat (Satz im Pruefvermerk), 0 ohne Angabe
        val seit: Int = 0,
        val zweifel: Boolean = false,
        // Item 9A nennt eine wesentliche Schwaeche oder nicht wirksame Kontrollen
        val schwaeche: Boolean = false,
        val cams: List<String> = emptyList(),
        val verfahren: List<Verfahren> = emptyList(),
    )

    // ------------------------------------------------------------ HTML zu Text im Strom

    private const val M_ANF = '\u0001'
    private const val M_MITTE = '\u0002'
    private const val M_ENDE = '\u0003'
    private val BLOCK = setOf("br", "p", "div", "tr", "li", "h1", "h2", "h3", "h4", "h5", "h6", "table", "ul", "ol", "hr")
    private val VERWERFEN = setOf("ix:header", "script", "style", "head")
    private val PRUEFER_ATTR = Regex("(?i)\\bname\\s*=\\s*[\"']dei:(Auditor\\w+)[\"']")

    private class Streifer(private val max: Int) {
        val aus = StringBuilder(1 shl 20)
        private val tag = StringBuilder()
        private var imTag = false
        private var inAnf = false
        private var anf = ' '
        private var skip: String? = null
        private var marke = false
        private var ent: StringBuilder? = null
        private var zeichen = 0

        fun fuettern(buf: CharArray, n: Int) {
            zeichen += n
            if (zeichen > max) throw ZuGross()
            for (i in 0 until n) schritt(buf[i])
        }

        fun ende() {
            ent?.let { text('&'); it.forEach { c -> text(c) } }
            ent = null
        }

        private fun schritt(c: Char) {
            if (imTag) {
                if (inAnf) {
                    tag.append(c)
                    if (c == anf) inAnf = false
                } else if (c == '"' || c == '\'') {
                    inAnf = true; anf = c; tag.append(c)
                } else if (c == '>') {
                    imTag = false
                    tagFertig()
                } else tag.append(c)
                if (tag.length > 20_000) { imTag = false; tag.setLength(0) }
                return
            }
            if (c == '<') {
                ende()
                imTag = true; inAnf = false; tag.setLength(0)
                return
            }
            if (skip != null) return
            val e = ent
            if (e != null) {
                if (c == ';') {
                    ent = null
                    val z = entitaet(e.toString())
                    if (z != null) text(z) else { text('&'); e.forEach { text(it) }; text(';') }
                } else if (e.length > 10 || !(c.isLetterOrDigit() || c == '#')) {
                    ent = null
                    text('&'); e.forEach { text(it) }
                    schritt(c)
                } else e.append(c)
                return
            }
            if (c == '&') { ent = StringBuilder(); return }
            text(c)
        }

        private fun entitaet(s: String): Char? {
            when (s) {
                "amp" -> return '&'; "lt" -> return '<'; "gt" -> return '>'; "quot" -> return '"'; "apos" -> return '\''
                "nbsp" -> return ' '; "ndash", "mdash" -> return '-'; "rsquo", "lsquo" -> return '\''; "ldquo", "rdquo" -> return '"'
            }
            if (s.startsWith("#")) {
                val n = (if (s.startsWith("#x") || s.startsWith("#X")) s.substring(2).toIntOrNull(16) else s.substring(1).toIntOrNull()) ?: return null
                return if (n in 1..0xFFFF) n.toChar() else null
            }
            return null
        }

        private fun tagFertig() {
            val s = tag.toString()
            if (s.isEmpty() || s[0] == '!' || s[0] == '?') return
            val zu = s[0] == '/'
            val name = s.substring(if (zu) 1 else 0).takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
            val sk = skip
            if (sk != null) {
                if (zu && name == sk) skip = null
                return
            }
            if (!zu && name in VERWERFEN && !s.endsWith("/")) { skip = name; return }
            if (name == "ix:nonnumeric") {
                if (!zu) {
                    val m = PRUEFER_ATTR.find(s)
                    if (m != null && !marke) {
                        aus.append(M_ANF).append(m.groupValues[1]).append(M_MITTE)
                        marke = true
                    }
                } else if (marke) {
                    aus.append(M_ENDE)
                    marke = false
                }
                return
            }
            if (name in BLOCK) zeile()
            else if (zu && (name == "td" || name == "th")) leer()
        }

        private fun leer() {
            if (aus.isNotEmpty()) { val l = aus[aus.length - 1]; if (l != ' ' && l != '\n') aus.append(' ') }
        }

        private fun zeile() {
            while (aus.isNotEmpty() && aus[aus.length - 1] == ' ') aus.setLength(aus.length - 1)
            if (aus.isNotEmpty() && aus[aus.length - 1] != '\n') aus.append('\n')
        }

        private fun text(c0: Char) {
            val c = when (c0) {
                '\u00A0', '\u2007', '\u202F' -> ' '
                '\u2019', '\u2018' -> '\''
                '\u201C', '\u201D' -> '"'
                '\uFFFD', '\u2013', '\u2014' -> '-'
                M_ANF, M_MITTE, M_ENDE -> return
                else -> c0
            }
            if (c == '\n' || c == '\r') zeile()
            else if (c.isWhitespace()) leer()
            else aus.append(c)
        }
    }

    // Klartext des Berichts; wirft ZuGross, wenn mehr als max Zeichen kommen.
    fun klartext(ein: InputStream, max: Int = MAX_ZEICHEN): String {
        val s = Streifer(max)
        val u = Utf8Strom(ein)
        val buf = CharArray(32_768)
        while (true) {
            var n = 0
            while (n < buf.size) {
                val c = u.lies()
                if (c < 0) break
                buf[n++] = c.toChar()
            }
            if (n == 0) break
            s.fuettern(buf, n)
            if (n < buf.size) break
        }
        s.ende()
        return s.aus.toString()
    }

    // ------------------------------------------------------------ Auswerten

    private val MARKEN = Regex("\u0001(\\w+)\u0002(.*?)\u0003", RegexOption.DOT_MATCHES_ALL)
    private val ZEILE = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)

    private fun re(p: String) = Regex(p, ZEILE)

    // Text zwischen dem laengsten Abschnitt, der mit start beginnt und vor ende endet (Inhaltsverzeichnis und Verweise
    // liefern kurze Treffer, der echte Abschnitt ist der laengste).
    private fun laengster(text: String, start: Regex, ende: Regex, max: Int = 200_000): String {
        var beste = ""
        for (m in start.findAll(text)) {
            val e = ende.find(text, m.range.last + 1)?.range?.first ?: minOf(text.length, m.range.first + 20_000)
            val s = text.substring(m.range.first, minOf(e, m.range.first + max))
            if (s.length > beste.length) beste = s
        }
        return beste
    }

    private val SEIT = re("served as (?:the )?[^.\\n]{0,60}?auditor[^.\\n]{0,40}?since\\s+(\\d{4})")
    private val UNTERSCHRIFT = re("^/s/\\s*(.{3,80})$")
    private val BERICHT_KOPF = re("^report of independent registered public accounting firm\\b.*$")
    private val ZWEIFEL = re("substantial doubt[^.\\n]{0,160}going concern|going concern[^.\\n]{0,120}substantial doubt")
    private val ZWEIFEL_NEIN = re("\\b(?:no|not|without|alleviat\\w*|dispel\\w*|resolved|absence of)\\b[^.\\n]{0,60}$")
    private val ICFR_NEIN_WIRKSAM = re(
        "internal control over financial reporting (?:was|were|is|are) not effective|" +
            "did not maintain effective internal control over financial reporting|" +
            "internal control over financial reporting (?:was|were|is|are) (?:ineffective|not operating effectively)",
    )
    private val SCHWAECHE_GENANNT = re("identified (?:a |the following |certain |several |two |three |four |multiple |\\w+ )?material weakness|material weakness(?:es)? (?:was|were) identified|following material weakness")
    private val SCHWAECHE_BEHOBEN = re("(?:was|were|has been|have been) (?:fully )?remediated|completed (?:the |our )?remediation|remediation (?:of|was|is) (?:the )?(?:previously )?(?:identified |reported )?material weakness[^.\\n]{0,40}(?:completed|complete)")

    internal fun pruefer(text: String): Befund {
        val dei = HashMap<String, String>()
        MARKEN.findAll(text).forEach { m -> dei.putIfAbsent(m.groupValues[1], m.groupValues[2].replace(Regex("\\s+"), " ").trim()) }
        val klar = MARKEN.replace(text) { it.groupValues[2] }
        val seit = SEIT.find(klar)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1900..2100 } ?: 0
        val name = dei["AuditorName"]?.takeIf { it.isNotBlank() } ?: UNTERSCHRIFT.find(klar)?.groupValues?.get(1)?.trim().orEmpty()
        val zweifel = berichte(klar).any { b ->
            ZWEIFEL.findAll(b).any { m -> !ZWEIFEL_NEIN.containsMatchIn(b.substring(maxOf(0, m.range.first - 90), m.range.first)) }
        }
        val i9 = laengster(klar, re("^item\\s*9a\\.?"), re("^item\\s*9b\\.?"))
        val schwaeche = ICFR_NEIN_WIRKSAM.containsMatchIn(i9) || (SCHWAECHE_GENANNT.containsMatchIn(i9) && !SCHWAECHE_BEHOBEN.containsMatchIn(i9))
        return Befund(
            pruefer = name, pruefernr = dei["AuditorFirmId"].orEmpty(), ort = dei["AuditorLocation"].orEmpty(),
            seit = seit, zweifel = zweifel, schwaeche = schwaeche, cams = cams(klar),
        )
    }

    // Prueftexte: von der Ueberschrift des Vermerks bis zur Unterschrift (/s/), hoechstens 80 000 Zeichen
    private fun berichte(text: String): List<String> = BERICHT_KOPF.findAll(text).map { m ->
        val rest = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 80_001))
        val e = UNTERSCHRIFT.find(rest)?.range?.first ?: rest.length
        rest.substring(0, e)
    }.toList()

    private val CAM_KOPF = re("^critical audit matters?\\s*$")
    private val CAM_KEINE = re("determined that there (?:are|were) no|no critical audit matters")
    private val CAM_DEF = re("does not alter in any way|not alter our opinion|alter the opinion")
    private val CAM_MARKE = re(
        "^(?:description of the matter|how we addressed the matter in our audit|critical audit matter description|" +
            "how the critical audit matter was addressed in the audit|why the matter is a critical audit matter|the critical audit matter|" +
            "auditing management.s .*|table of contents|report of independent registered public accounting firm|" +
            "opinion on the .*|basis for opinion.*|\\d+|/s/.*)$",
    )
    private val CAM_NACH = re("^(?:description of the matter|critical audit matter description|why the matter is a critical audit matter)")

    // Titel der Critical Audit Matters: die Zeile (hoechstens 16 Woerter, ohne Schlusspunkt) vor einem langen Absatz
    internal fun cams(text: String): List<String> {
        for (m in CAM_KOPF.findAll(text)) {
            var reg = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 60_001))
            UNTERSCHRIFT.find(reg)?.let { reg = reg.substring(0, it.range.first) }
            val zeilen = reg.split('\n').filter { it.isNotBlank() }
            if (CAM_KEINE.containsMatchIn(zeilen.take(3).joinToString(" "))) continue
            var i = 0
            while (i < zeilen.size && i <= 6 && !CAM_DEF.containsMatchIn(zeilen[i])) i++
            i++
            val titel = ArrayList<String>()
            for (k in i until zeilen.size) {
                val l = zeilen[k].trim()
                val w = l.split(' ').size
                if (w < 2 || w > 16 || l.length > 130) continue
                if (l.last() in ".:;," || !l[0].isUpperCase() || CAM_MARKE.matches(l) || l.count { it.isDigit() } > 4) continue
                val nach = zeilen.getOrNull(k + 1).orEmpty()
                if (nach.length > 100 || CAM_NACH.containsMatchIn(nach)) titel.add(l)
            }
            if (titel.isNotEmpty()) return titel.distinct().take(8)
        }
        return emptyList()
    }

    // ------------------------------------------------------------ Verfahren

    private val KOPF_ANHANG = re(
        "^(?:note\\s*\\d{1,2}\\s*[-.:]?\\s*|\\d{1,2}\\.\\s*)(?:commitments and contingencies|legal proceedings|legal matters|litigation|contingencies|" +
            "legal contingencies|litigation and (?:other )?(?:legal )?(?:matters|contingencies)|legal and regulatory(?: matters| proceedings)?|" +
            "contingent liabilities)[^\\n]{0,30}$",
    )
    private val UNTERKOPF = re(
        "^(?:\\d{0,2}\\.?\\s*)(?:legal proceedings|legal matters|litigation|legal contingencies|litigation and (?:other )?(?:legal )?(?:matters|contingencies)|" +
            "legal and regulatory(?: matters| proceedings)?|legal)\\s*$",
    )
    private val UNTERKOPF_OHNE_NR = re(
        "^(?:legal proceedings|legal matters|litigation|legal contingencies|litigation and (?:other )?(?:legal )?(?:matters|contingencies)|" +
            "legal and regulatory(?: matters| proceedings)?)\\s*$",
    )
    private val NAECHSTER_KOPF = re(
        "^(?:note\\s*\\d{1,2}\\b[^\\n]{0,80}|\\d{1,2}\\.\\s*[A-Z][A-Za-z ,&-]{4,60}|index of exhibits|item\\s*\\d+[a-z]?\\.[^\\n]{0,80}|signatures?)$",
    )
    private val ITEM3 = re("^item\\s*3\\.?")
    private val ITEM3_KOPF = re("^item\\s*3\\.?\\s*(?:-\\s*)?(?:legal proceedings)?")
    private val ITEM4 = re("^item\\s*4\\.?")
    private val ALLGEMEIN = re(
        "lease|letters? of credit|purchase (?:obligations|commitments)|indemnif|guarantee|warrant|unconditional|commitments|insurance|restricted cash|" +
            "financing|standby|contractual|surety|table of contents|^other$|^legal$",
    )
    private val GERICHT_US = Regex("(?:U\\.S\\.|United States)\\s+District Court(?: for the ([A-Z][A-Za-z]+(?: [A-Z][A-Za-z]+){0,3}) District of ([A-Z][A-Za-z]+(?: [A-Z][a-z]+)?))?")
    private val GERICHTE = listOf(
        Regex("Delaware Court of Chancery|Court of Chancery"),
        Regex("(?:[A-Z][A-Za-z]+ ){1,3}(?:Superior|Supreme|Circuit|Appeals|County) Court(?: of (?:the )?(?:[A-Z][A-Za-z]+ ?){1,3})?"),
        Regex("Court of Appeal(?: in [A-Z][a-z]+)?|District Court of [A-Z][a-z]+|Supreme Court|Court of Justice of the European Union|General Court of the European Union"),
    )
    private val BEHOERDEN = Regex(
        "Securities and Exchange Commission|Department of Justice|Federal Trade Commission|European Commission|Consumer Financial Protection Bureau|" +
            "Environmental Protection Agency|Competition and Markets Authority|Financial Conduct Authority|Office of Foreign Assets Control|" +
            "Federal Communications Commission|Department of Labor|Equal Employment Opportunity Commission|Department of Transportation|" +
            "Food and Drug Administration|Commodity Futures Trading Commission|Attorneys? General",
    )
    private val JAHR = Regex("\\b(19[89]\\d|20[0-4]\\d)\\b")

    private fun stelle(block: String): String {
        val b = block.take(800)
        GERICHT_US.find(b)?.let { m ->
            val bezirk = m.groupValues[2]
            return if (bezirk.isNotEmpty()) "U.S. District Court, District of $bezirk" else "U.S. District Court"
        }
        for (g in GERICHTE) g.find(b)?.let { return it.value.trim() }
        BEHOERDEN.find(b)?.let { return it.value.replace("Attorneys General", "Attorney General") }
        return ""
    }

    private fun jahr(block: String, bis: Int): Int = JAHR.find(block.take(700))?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it <= bis } ?: 0

    private fun zeilenTitel(region: String): List<Pair<Int, String>> {
        val zeilen = region.split('\n')
        val aus = ArrayList<Pair<Int, String>>()
        var pos = zeilen.firstOrNull()?.length?.plus(1) ?: 0
        for (k in 1 until zeilen.size) {
            val l = zeilen[k].trim()
            val start = pos
            pos += zeilen[k].length + 1
            val w = l.split(' ').size
            if (l.isEmpty() || w < 2 || w > 16 || l.length > 100) continue
            if (l.last() in ".;,:" || !l[0].isUpperCase() || ALLGEMEIN.containsMatchIn(l) || UNTERKOPF.matches(l)) continue
            if (l.count { it.isDigit() } > 4 || l.contains('$')) continue
            if ((zeilen.getOrNull(k + 1)?.length ?: 0) >= 80) aus.add(start to l)
        }
        return aus
    }

    // Verfahren aus Item 3 (nur wenn der Abschnitt mehr ist als ein Verweis auf den Anhang) und aus dem Anhang
    // „Commitments and Contingencies“ bzw. „Legal Proceedings“ (Zwischenueberschriften). heute: laufendes Jahr.
    internal fun verfahren(text: String, heute: Int): List<Verfahren> {
        val regionen = ArrayList<String>()
        val i3 = laengster(text, ITEM3_KOPF, ITEM4)
        if (i3.length > 700) regionen.add(i3)
        val item3Ende = ITEM3.findAll(text).lastOrNull()?.range?.first ?: 0
        var bis = 0
        for (m in KOPF_ANHANG.findAll(text).plus(UNTERKOPF_OHNE_NR.findAll(text)).sortedBy { it.range.first }) {
            if (m.range.first < item3Ende + 200 || m.range.first < bis) continue
            var reg = text.substring(m.range.first, minOf(text.length, m.range.first + 120_000))
            NAECHSTER_KOPF.find(reg, m.value.length)?.let { reg = reg.substring(0, it.range.first) }
            if (reg.length < 600) continue
            UNTERKOPF.find(reg)?.let { reg = reg.substring(it.range.first) }
            // Prosa statt Zahlenspalten: wenige Ziffern
            if (reg.count { it.isDigit() } > reg.length / 12) continue
            regionen.add(reg)
            bis = m.range.first + reg.length
        }
        val aus = ArrayList<Verfahren>()
        for (reg in regionen) {
            val titel = zeilenTitel(reg)
            titel.forEachIndexed { i, (start, t) ->
                val ende = titel.getOrNull(i + 1)?.first ?: minOf(reg.length, start + 4_000)
                val block = reg.substring(start + t.length, minOf(reg.length, maxOf(start + t.length, ende)))
                if (aus.none { it.titel.equals(t, true) }) aus.add(Verfahren(t, stelle(block), jahr(block, heute)))
            }
        }
        return aus.take(12)
    }

    // Alles aus dem Text
    fun auswerten(text: String, heute: Int): Befund = pruefer(text).copy(verfahren = verfahren(MARKEN.replace(text) { it.groupValues[2] }, heute))
}

// UTF-8 aus einem Byte-Strom, Zeichen fuer Zeichen (ohne java.io.Reader, den es auf iOS nicht gibt). Ungueltige Folgen und
// Zeichen ausserhalb der Grundebene werden zu U+FFFD.
internal class Utf8Strom(private val ein: InputStream) {
    private val buf = ByteArray(32_768)
    private var n = 0
    private var p = 0

    private fun byte(): Int {
        if (p >= n) {
            n = ein.read(buf, 0, buf.size)
            p = 0
            if (n <= 0) { n = 0; return -1 }
        }
        return buf[p++].toInt() and 0xFF
    }

    // Naechstes Zeichen oder -1 am Ende
    fun lies(): Int {
        val b = byte()
        if (b < 0x80) return b
        val folge = when {
            b in 0xC2..0xDF -> 1
            b in 0xE0..0xEF -> 2
            b in 0xF0..0xF4 -> 3
            else -> return 0xFFFD
        }
        var cp = when (folge) { 1 -> b and 0x1F; 2 -> b and 0x0F; else -> b and 0x07 }
        var ok = true
        repeat(folge) {
            val c = byte()
            if (c < 0) return 0xFFFD
            if ((c and 0xC0) != 0x80) { ok = false } else cp = (cp shl 6) or (c and 0x3F)
        }
        return if (!ok || cp > 0xFFFF) 0xFFFD else cp
    }
}
