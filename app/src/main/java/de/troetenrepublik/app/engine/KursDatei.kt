package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

// [0.78] Eigene Kursdateien einlesen: Textdateien mit Tageskursen (Kopfzeile mit Ticker, Datum, Eroeffnung, Hoch, Tief,
// Schluss, Umsatz, Datum als JJJJMMTT), CSV mit Komma, Semikolon oder Tabulator, deutschem oder englischem Zahlenformat.
// Spalten werden an den Kopfzeilen erkannt; ein ZIP liefert alle enthaltenen Reihen. Reine Rechnung ohne Netz und ohne
// Dateizugriff. Der Inhalt wird nur gelesen, nie ausgefuehrt; Zeilen, die sich nicht lesen lassen, werden uebersprungen
// und gezaehlt.
object KursDatei {
    // Groesste Datei und groesste Menge entpackter Daten aus einem ZIP
    const val MAX_BYTES = 200L * 1024 * 1024

    // Hoechstens so viele Reihen aus einer Datei; der Rest wird gezaehlt und nicht gespeichert
    const val MAX_REIHEN = 2000

    private const val TAG_MS = 86_400_000L

    class Reihe(val symbol: String, val kerzen: List<Candle>, val waehrung: String)

    // reihen: je Symbol eine Reihe, aelteste Kerze zuerst. uebersprungen: Zeilen, die sich nicht lesen liessen.
    // zuGross: Datei oder entpackte Menge ueber der Grenze. reihenZuViel: Reihen ueber MAX_REIHEN.
    class Ergebnis(val reihen: List<Reihe>, val uebersprungen: Int, val zuGross: Boolean = false, val reihenZuViel: Int = 0)

    private val LEER = Ergebnis(emptyList(), 0)

    fun einlesen(name: String, daten: ByteArray): Ergebnis {
        if (daten.size > MAX_BYTES) return Ergebnis(emptyList(), 0, zuGross = true)
        return if (istZip(daten)) zipLesen(daten) else einzeln(dateiname(name), daten)
    }

    private fun istZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() && b[2].toInt() == 3 && b[3].toInt() == 4

    // Nur der letzte Teil eines Pfades; Pfade aus dem ZIP werden nie uebernommen
    internal fun dateiname(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')

    private val ENDUNGEN = setOf("txt", "csv", "tsv")

    private fun zipLesen(daten: ByteArray): Ergebnis {
        val reihen = LinkedHashMap<String, Reihe>()
        var uebersprungen = 0
        var zuViel = 0
        var gelesen = 0L
        var zuGross = false
        runCatching {
            ZipInputStream(ByteArrayInputStream(daten)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = dateiname(e.name)
                    if (e.isDirectory || n.substringAfterLast('.', "").lowercase() !in ENDUNGEN) continue
                    val aus = java.io.ByteArrayOutputStream()
                    val puffer = ByteArray(65_536)
                    while (true) {
                        val k = z.read(puffer)
                        if (k < 0) break
                        gelesen += k
                        if (gelesen > MAX_BYTES) { zuGross = true; break }
                        aus.write(puffer, 0, k)
                    }
                    if (zuGross) break
                    val r = einzeln(n, aus.toByteArray())
                    uebersprungen += r.uebersprungen
                    for (re in r.reihen) {
                        val alt = reihen[re.symbol]
                        if (alt != null) reihen[re.symbol] = Reihe(re.symbol, zusammen(alt.kerzen, re.kerzen), re.waehrung)
                        else if (reihen.size < MAX_REIHEN) reihen[re.symbol] = re
                        else zuViel++
                    }
                }
            }
        }
        return Ergebnis(reihen.values.toList(), uebersprungen, zuGross, zuViel)
    }

    // Zwei Reihen desselben Symbols: je Tag gilt die zweite
    fun zusammen(alt: List<Candle>, neu: List<Candle>): List<Candle> {
        if (alt.isEmpty()) return neu
        val m = LinkedHashMap<Long, Candle>()
        for (c in alt) m[Math.floorDiv(c.t, TAG_MS)] = c
        for (c in neu) m[Math.floorDiv(c.t, TAG_MS)] = c
        return m.entries.sortedBy { it.key }.map { it.value }
    }

    // UTF-8, sonst Einzelbyte-Zeichensatz (Exporte deutscher Programme); Byte-Order-Marke entfernt
    internal fun dekodieren(b: ByteArray): String {
        var s = b.decodeToString()
        if ('�' in s) s = CharArray(b.size) { (b[it].toInt() and 0xFF).toChar() }.concatToString()
        return s.removePrefix("\uFEFF")
    }

    private fun einzeln(name: String, daten: ByteArray): Ergebnis {
        val r = textLesen(name, dekodieren(daten)) ?: return LEER
        return Ergebnis(listOf(r.first), r.second)
    }

    // ------------------------------------------------------------ Lesen einer Datei

    private fun normal(s: String): String {
        val b = StringBuilder()
        for (ch in s.lowercase()) when (ch) {
            'ä' -> b.append("ae"); 'ö' -> b.append("oe"); 'ü' -> b.append("ue"); 'ß' -> b.append("ss")
            in 'a'..'z', in '0'..'9' -> b.append(ch)
            else -> {}
        }
        return b.toString()
    }

    private val DATUM = setOf("date", "datum", "day", "tag", "tradedate")
    private val SCHLUSS = setOf("close", "closelast", "schluss", "schlusskurs", "price", "zuletzt", "last", "kurs", "preis")
    private val SCHLUSS_ANGEP = setOf("adjclose", "adjustedclose", "adjclosepriceadjustedforsplitsanddividends")
    private val ERSTER = setOf("open", "opening", "eroeffn", "eroeffnung", "eroeffnungskurs")
    private val HOCH = setOf("high", "hoch", "tageshoch", "max")
    private val TIEF = setOf("low", "tief", "tagestief", "min")
    private val UMSATZ = setOf("volume", "vol", "volumen", "umsatz")
    private val TICKER = setOf("ticker", "symbol")
    private val PERIODE = setOf("per", "period")
    private val ZEIT = setOf("time", "zeit", "uhrzeit")

    private class Spalten(
        val datum: Int, val schluss: Int, val erster: Int, val hoch: Int, val tief: Int, val umsatz: Int,
        val ticker: Int, val periode: Int, val zeit: Int,
    )

    private fun spalten(kopf: List<String>): Spalten? {
        val n = kopf.map { normal(it) }
        fun finde(namen: Set<String>) = n.indexOfFirst { it in namen }
        val datum = finde(DATUM)
        var schluss = finde(SCHLUSS)
        if (schluss < 0) schluss = finde(SCHLUSS_ANGEP)
        if (datum < 0 || schluss < 0) return null
        return Spalten(datum, schluss, finde(ERSTER), finde(HOCH), finde(TIEF), finde(UMSATZ), finde(TICKER), finde(PERIODE), finde(ZEIT))
    }

    // Felder einer Zeile, Anfuehrungszeichen halten Trennzeichen zusammen
    internal fun felder(zeile: String, trenner: Char): List<String> {
        val aus = ArrayList<String>()
        val b = StringBuilder()
        var innen = false
        for (ch in zeile) when {
            ch == '"' -> innen = !innen
            ch == trenner && !innen -> { aus.add(b.toString().trim()); b.setLength(0) }
            else -> b.append(ch)
        }
        aus.add(b.toString().trim())
        return aus
    }

    private fun trennerWaehlen(zeile: String): Char {
        var innen = false
        var semikolon = 0; var tab = 0; var komma = 0
        for (ch in zeile) when {
            ch == '"' -> innen = !innen
            innen -> {}
            ch == ';' -> semikolon++
            ch == '\t' -> tab++
            ch == ',' -> komma++
        }
        return when {
            semikolon > 0 && semikolon >= komma && semikolon >= tab -> ';'
            tab > 0 && tab >= komma -> '\t'
            else -> ','
        }
    }

    private class Zeile(val tag: Long, val zeit: String, val o: Double, val h: Double, val l: Double, val c: Double, val v: Double)

    // Eine Datei: Reihe und Zahl der uebersprungenen Zeilen; null, wenn keine Kopfzeile mit Datum und Schluss erkannt wird
    // und die erste Zeile auch kein Kurs ist.
    internal fun textLesen(name: String, text: String): Pair<Reihe, Int>? {
        val zeilen = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (zeilen.isEmpty()) return null
        val trenner = trennerWaehlen(zeilen[0])
        val kopf = spalten(felder(zeilen[0], trenner).map { it.trim('<', '>') })
        val sp: Spalten
        val ab: Int
        if (kopf != null) {
            sp = kopf; ab = 1
        } else {
            // Ohne Kopfzeile: Datum, Eroeffnung, Hoch, Tief, Schluss, Umsatz oder nur Datum und Schluss
            val f = felder(zeilen[0], trenner)
            if (f.size < 2 || datumTage(f[0], false) == null) return null
            sp = if (f.size >= 5) Spalten(0, 4, 1, 2, 3, if (f.size > 5) 5 else -1, -1, -1, -1) else Spalten(0, 1, -1, -1, -1, -1, -1, -1, -1)
            ab = 0
        }
        val daten = zeilen.subList(ab, zeilen.size).map { felder(it, trenner) }
        val tagZuerst = tagZuerstErkennen(daten.mapNotNull { it.getOrNull(sp.datum) })
        val dezimalKomma = trenner != ',' && daten.take(60).any { z ->
            listOf(sp.schluss, sp.erster, sp.hoch, sp.tief).any { i -> z.getOrNull(i)?.contains(',') == true }
        }
        val rohe = ArrayList<Zeile>(daten.size)
        var uebersprungen = 0
        var ticker = ""
        val intraday = sp.periode >= 0 || sp.zeit >= 0
        for (f in daten) {
            val tag = f.getOrNull(sp.datum)?.let { datumTage(it, tagZuerst) }
            val c = f.getOrNull(sp.schluss)?.let { zahl(it, dezimalKomma) }?.takeIf { it > 0 }
            if (tag == null || c == null) { uebersprungen++; continue }
            fun preis(i: Int) = (if (i >= 0) f.getOrNull(i)?.let { zahl(it, dezimalKomma) } else null)?.takeIf { it > 0 }
            val o = preis(sp.erster) ?: c
            val h = maxOf(preis(sp.hoch) ?: c, o, c)
            val l = minOf(preis(sp.tief) ?: c, o, c)
            val v = (if (sp.umsatz >= 0) f.getOrNull(sp.umsatz)?.let { zahl(it, dezimalKomma) } else null)?.takeIf { it > 0 } ?: 0.0
            if (ticker.isEmpty() && sp.ticker >= 0) ticker = f.getOrNull(sp.ticker).orEmpty()
            rohe.add(Zeile(tag, if (sp.zeit >= 0) f.getOrNull(sp.zeit).orEmpty() else "", o, h, l, c, v))
        }
        if (rohe.isEmpty()) return null
        val sortiert = if (intraday) rohe.sortedWith(compareBy<Zeile>({ it.tag }, { it.zeit })) else rohe
        val tage = LinkedHashMap<Long, Candle>()
        for (z in sortiert) {
            val alt = tage[z.tag]
            tage[z.tag] = if (alt == null || !intraday) Candle(z.tag * TAG_MS, z.o, z.h, z.l, z.c, z.v)
            else Candle(alt.t, alt.open, maxOf(alt.high, z.h), minOf(alt.low, z.l), z.c, alt.volume + z.v)
        }
        val kerzen = tage.entries.sortedBy { it.key }.map { it.value }
        val symbol = symbolNormal(ticker).ifEmpty { symbolAusName(name) }
        return Reihe(symbol, kerzen, waehrung(symbol, ticker)) to uebersprungen
    }

    // ------------------------------------------------------------ Symbol und Waehrung

    // Stooq nennt Werte mit Landesendung: aapl.us wird AAPL, sap.de bleibt SAP.DE, vod.uk wird VOD.L
    private val ENDUNG = mapOf("UK" to ".L", "JP" to ".T", "PL" to ".WA", "HU" to ".BD", "CZ" to ".PR", "DE" to ".DE", "HK" to ".HK")

    fun symbolNormal(roh: String): String {
        var s = roh.trim().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '^' || it == '=' || it == '-' }
        if (s.endsWith(".US")) s = s.removeSuffix(".US")
        else ENDUNG[s.substringAfterLast('.', "")]?.takeIf { '.' in s }?.let { s = s.substringBeforeLast('.') + it }
        return s.take(24)
    }

    fun symbolAusName(name: String): String {
        var n = dateiname(name).trim()
        val ende = n.substringAfterLast('.', "").lowercase()
        if (ende in ENDUNGEN || ende == "zip" || ende == "dat") n = n.substringBeforeLast('.')
        return symbolNormal(n.substringBefore(' '))
    }

    private val EURO = setOf("DE", "F", "BE", "HM", "HA", "MU", "SG", "DU", "PA", "MI", "AS", "MC", "BR", "VI", "HE", "LS", "IR", "AT")
    private val ENDUNG_WAEHRUNG = mapOf("L" to "GBp", "SW" to "CHF", "T" to "JPY", "HK" to "HKD", "TO" to "CAD", "V" to "CAD", "AX" to "AUD", "ST" to "SEK", "OL" to "NOK", "CO" to "DKK", "WA" to "PLN")

    // Handelswaehrung nach der Endung des Symbols (Yahoo-Schreibweise); ohne Endung US-Dollar. Bei Stooq-Endungen .uk, .jp
    // und weiteren zaehlt die Endung der Datei.
    fun waehrung(symbol: String, roh: String = ""): String {
        val rohEnde = roh.trim().uppercase().substringAfterLast('.', "")
        when (rohEnde) { "US" -> return "USD"; "UK" -> return "GBp"; "JP" -> return "JPY"; "PL" -> return "PLN"; "HU" -> return "HUF"; "CZ" -> return "CZK" }
        val e = symbol.substringAfterLast('.', "")
        if ('.' !in symbol) return "USD"
        return if (e in EURO) "EUR" else ENDUNG_WAEHRUNG[e] ?: "USD"
    }

    // ------------------------------------------------------------ Datum und Zahl

    private val MONATE = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "mär" to 3, "mrz" to 3, "apr" to 4, "may" to 5, "mai" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10, "nov" to 11, "dec" to 12, "dez" to 12,
    )

    private fun teile(s: String): List<String> {
        val aus = ArrayList<String>()
        val b = StringBuilder()
        for (ch in s.trim()) {
            if (ch.isLetterOrDigit()) b.append(ch) else if (b.isNotEmpty()) { aus.add(b.toString()); b.setLength(0) }
        }
        if (b.isNotEmpty()) aus.add(b.toString())
        return aus
    }

    private fun alsMonat(t: String): Int? = t.toIntOrNull()?.takeIf { it in 1..12 } ?: if (t.length >= 3 && t.all { it.isLetter() }) MONATE[t.take(3).lowercase()] else null

    // Bei Zahlen wie 03/04/2026 entscheidet die Datei: Ein Tag ueber 12 vorn heisst Tag zuerst, hinten Monat zuerst;
    // sonst gilt Monat zuerst, mit Punkten als Trenner immer Tag zuerst.
    internal fun tagZuerstErkennen(daten: List<String>): Boolean {
        for (s in daten.take(400)) {
            val t = teile(s)
            if (t.size < 3 || t[0].length == 4 || t.any { !it.all { c -> c.isDigit() } }) continue
            val a = t[0].toIntOrNull() ?: continue
            val b = t[1].toIntOrNull() ?: continue
            if (a > 12) return true
            if (b > 12) return false
        }
        return false
    }

    // Tage seit 1970 aus Datum in den ueblichen Schreibweisen (JJJJMMTT, JJJJ-MM-TT, TT.MM.JJJJ, MM/TT/JJJJ, Nov 12, 2026)
    internal fun datumTage(s: String, tagZuerst: Boolean): Long? {
        val t = teile(s)
        if (t.isEmpty()) return null
        if (t.size == 1) {
            val x = t[0]
            if (x.length != 8 || !x.all { it.isDigit() }) return null
            return tageSeit1970(x.substring(0, 4).toInt(), x.substring(4, 6).toInt(), x.substring(6, 8).toInt())
        }
        if (t.size < 3) return null
        val punkt = '.' in s
        val j: Int; val m: Int; val d: Int
        when {
            t[0].length == 4 && t[0].all { it.isDigit() } -> {
                j = t[0].toInt(); m = alsMonat(t[1]) ?: return null; d = t[2].toIntOrNull() ?: return null
            }
            alsMonat(t[0]) != null && t[0].any { it.isLetter() } -> {
                m = alsMonat(t[0])!!; d = t[1].toIntOrNull() ?: return null; j = jahr(t[2]) ?: return null
            }
            alsMonat(t[1]) != null && t[1].any { it.isLetter() } -> {
                d = t[0].toIntOrNull() ?: return null; m = alsMonat(t[1])!!; j = jahr(t[2]) ?: return null
            }
            else -> {
                val a = t[0].toIntOrNull() ?: return null
                val b = t[1].toIntOrNull() ?: return null
                j = jahr(t[2]) ?: return null
                if (punkt || tagZuerst) { d = a; m = b } else { m = a; d = b }
            }
        }
        return tageSeit1970(j, m, d)
    }

    private fun jahr(t: String): Int? {
        val n = t.toIntOrNull() ?: return null
        return when (t.length) { 4 -> n; 2 -> if (n < 70) 2000 + n else 1900 + n; else -> null }
    }

    private fun tageImMonat(j: Int, m: Int): Int = when (m) {
        2 -> if (j % 4 == 0 && (j % 100 != 0 || j % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    fun tageSeit1970(j: Int, m: Int, t: Int): Long? {
        if (j !in 1900..2100 || m !in 1..12 || t < 1 || t > tageImMonat(j, m)) return null
        val y = if (m <= 2) j - 1 else j
        val era = y.floorDiv(400)
        val yoe = y - era * 400
        val doy = (153 * ((m + 9) % 12) + 2) / 5 + t - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    // Jahr, Monat, Tag aus Tagen seit 1970
    fun datumAusTagen(tage: Long): Triple<Int, Int, Int> {
        val z = tage + 719_468L
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val j = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return Triple(j, m, d)
    }

    // Zahl in deutscher oder englischer Schreibweise; K, M, B hinter Mengen (1,2M). Mit Komma als Trennzeichen der Datei
    // steht das Komma nur als Tausendertrennung in Anfuehrungszeichen.
    internal fun zahl(roh: String, dezimalKomma: Boolean): Double? {
        var s = roh.trim().replace(" ", "").replace(" ", "").replace("'", "").replace("%", "")
        if (s.isEmpty() || s == "-" || s == "null" || s.equals("nan", true)) return null
        var faktor = 1.0
        when (s.last().lowercaseChar()) { 'k' -> faktor = 1e3; 'm' -> faktor = 1e6; 'b' -> faktor = 1e9 }
        if (faktor != 1.0) s = s.dropLast(1)
        s = if (dezimalKomma) s.replace(".", "").replace(',', '.') else when {
            // Beide Zeichen: das letzte ist das Dezimalzeichen
            ',' in s && '.' in s -> if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            s.count { it == ',' } > 1 -> s.replace(",", "")
            ',' in s -> if (s.length - s.indexOf(',') - 1 == 3 && s.indexOf(',') > 0) s.replace(",", "") else s.replace(',', '.')
            else -> s
        }
        return s.toDoubleOrNull()?.takeIf { it.isFinite() }?.times(faktor)
    }

    // ------------------------------------------------------------ Ergaenzen

    // Eigene Tageskerzen schliessen Luecken einer anderen Reihe: Tage, die dort fehlen (auch vor dem ersten und nach dem
    // letzten Tag), im Fenster von abMs bis bisMs. Passt der Massstab nicht (Splits, andere Waehrung), kommt nichts dazu.
    fun ergaenzen(basis: List<Candle>, eigen: List<Candle>, abMs: Long, bisMs: Long = Long.MAX_VALUE): List<Candle> {
        if (eigen.isEmpty()) return basis
        // Boersen im Pazifik tragen den Handelstag in der Reihe des Anbieters einen Tag frueher ein
        val v = verschiebung(basis, eigen)
        val passend = if (v == 0) eigen else eigen.map { it.copy(t = it.t + v * TAG_MS) }
        val kand = passend.filter { it.t in abMs..bisMs }
        if (kand.isEmpty()) return basis
        if (basis.isEmpty()) return kand
        val tage = HashSet<Long>(basis.size * 2)
        for (c in basis) tage.add(Math.floorDiv(c.t, TAG_MS))
        val neu = kand.filter { Math.floorDiv(it.t, TAG_MS) !in tage }
        if (neu.isEmpty()) return basis
        val f = massstab(basis, passend, neu.first()) ?: return basis
        val skaliert = if (f == 1.0) neu else neu.map { Candle(it.t, it.open * f, it.high * f, it.low * f, it.close * f, it.volume) }
        return (basis + skaliert).sortedBy { it.t }
    }

    // Um wie viele Tage der Tag einer eigenen Kerze gegenueber der Basis verschoben ist (-1, 0, 1): Dort, wo die Schlusskurse
    // beider Reihen in gleichem Verhaeltnis zueinander stehen. Ohne klaren Befund 0.
    internal fun verschiebung(basis: List<Candle>, eigen: List<Candle>): Int {
        val b = HashMap<Long, Double>(basis.size * 2)
        for (c in basis) b[Math.floorDiv(c.t, TAG_MS)] = c.close
        var beste = 0
        var besteZahl = 0
        var nullZahl = 0
        for (s in -1..1) {
            val r = eigen.mapNotNull { c -> if (c.close > 0) b[Math.floorDiv(c.t, TAG_MS) + s]?.let { it / c.close } else null }
            if (r.size < 3) continue
            val med = r.sorted()[r.size / 2]
            if (!(med > 0)) continue
            val n = r.count { kotlin.math.abs(it / med - 1.0) < 0.01 }
            if (s == 0) nullZahl = n
            if (n > besteZahl) { besteZahl = n; beste = s }
        }
        return if (beste != 0 && besteZahl >= 3 && besteZahl > nullZahl) beste else 0
    }

    // Faktor, mit dem eigene Kurse zur Basis passen: aus den gemeinsamen Tagen (Mittelwert muss in 2 Prozent liegen, sonst
    // Umrechnung nur, wenn alle Verhaeltnisse eng beieinander liegen); ohne gemeinsame Tage muss der Anschluss an den
    // naechsten Basiskurs innerhalb von 30 Prozent liegen. null: nicht zusammenfuegen.
    internal fun massstab(basis: List<Candle>, eigen: List<Candle>, naechste: Candle): Double? {
        val b = HashMap<Long, Double>()
        for (c in basis) b[Math.floorDiv(c.t, TAG_MS)] = c.close
        val r = ArrayList<Double>()
        for (c in eigen) b[Math.floorDiv(c.t, TAG_MS)]?.takeIf { c.close > 0 }?.let { r.add(it / c.close) }
        if (r.size >= 3) {
            val s = r.sorted()
            val med = s[s.size / 2]
            if (!(med > 0)) return null
            if (kotlin.math.abs(med - 1.0) < 0.02) return 1.0
            return if (s.all { kotlin.math.abs(it / med - 1.0) < 0.02 }) med else null
        }
        val d = Math.floorDiv(naechste.t, TAG_MS)
        val nah = basis.minByOrNull { kotlin.math.abs(Math.floorDiv(it.t, TAG_MS) - d) } ?: return null
        val q = nah.close / naechste.close
        return if (q in 0.7..1.4) 1.0 else null
    }

    // Tageskerzen zu Wochen (Montag bis Sonntag) oder Monaten verdichtet; jeder andere Abstand bleibt bei Tagen.
    fun verdichten(kerzen: List<Candle>, interval: String): List<Candle> {
        val schluessel: (Candle) -> Long = when (interval) {
            "1wk" -> { c -> Math.floorDiv(Math.floorDiv(c.t, TAG_MS) + 3, 7L) }
            "1mo" -> { c -> datumAusTagen(Math.floorDiv(c.t, TAG_MS)).let { it.first * 12L + it.second } }
            else -> return kerzen
        }
        return kerzen.groupBy(schluessel).values.map { g ->
            Candle(g.first().t, g.first().open, g.maxOf { it.high }, g.minOf { it.low }, g.last().close, g.sumOf { it.volume })
        }.sortedBy { it.t }
    }

    // Ergaenzung einer Reihe mit Abstand "1d", "1wk" oder "1mo" (Wochen und Monate nur vor der ersten Kerze, ohne die
    // Woche oder den Monat der ersten Kerze)
    fun ergaenzenMitAbstand(basis: List<Candle>, eigenTage: List<Candle>, abMs: Long, bisMs: Long, interval: String): List<Candle> {
        if (eigenTage.isEmpty()) return basis
        if (interval == "1d") return ergaenzen(basis, eigenTage, abMs, bisMs)
        if (basis.isEmpty()) return verdichten(eigenTage.filter { it.t in abMs..bisMs }, interval)
        val erste = basis.first()
        val fruehere = eigenTage.filter { it.t in abMs..bisMs && it.t < erste.t }
        if (fruehere.isEmpty()) return basis
        val f = massstab(basis, fruehere, fruehere.last()) ?: return basis
        val skaliert = if (f == 1.0) fruehere else fruehere.map { Candle(it.t, it.open * f, it.high * f, it.low * f, it.close * f, it.volume) }
        val vorn = verdichten(skaliert + erste, interval).dropLast(1)
        return vorn + basis
    }
}
