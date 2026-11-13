package de.troetenrepublik.app.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

// Lagerbestaende der London Metal Exchange. Die LME stellt je Monat eine Excel-Datei mit den taeglichen Schlussbestaenden
// bereit (ein Blatt "Closing Stock", Texte als inlineStr). Hier wird nur gelesen und gerechnet, Speicherung steht in
// market/LmeLagerAbruf.kt.
object LmeLager {

    const val BERICHT = "https://www.lme.com/en/market-data/reports-and-data/warehouse-and-stocks-reports"

    // Monatsdatei rund 57 KB, das Blatt entpackt rund 750 KB
    const val XLSX_MAX = 2_000_000
    const val XML_MAX = 20_000_000

    // Zwoelf Monatsdateien, beginnend beim Vormonat
    const val MONATE = 12

    // Tage zwischen 1899-12-30 (Nullpunkt von Excel) und 1970-01-01
    private const val EXCEL_ZU_EPOCHE = 25_569L

    // Reihenfolge der Anzeige, der Rest folgt nach Kuerzel
    val REIHENFOLGE = listOf("CA", "AH", "ZS", "NI", "PB", "SN")

    // Monat der Datei: Jahr und Monat (1 bis 12)
    data class Monat(val jahr: Int, val monat: Int) {
        val schluessel: String get() = "%04d-%02d".format(jahr, monat)
    }

    // Die letzten n Monate vor dem Monat von heute, neuester zuerst; der erste ist der Vormonat.
    fun monate(heute: LocalDate, n: Int = MONATE): List<Monat> {
        var m = heute.withDayOfMonth(1).minusMonths(1)
        val l = ArrayList<Monat>()
        repeat(n) { l += Monat(m.year, m.monthValue); m = m.minusMonths(1) }
        return l
    }

    // Ein Handelstag: Tag seit 1970-01-01 und Bestand in Tonnen je Kuerzel
    data class Tag(val tag: Long, val bestand: Map<String, Double>)

    // ------------------------------------------------------------ Lesen der Datei

    private fun blattAusZip(xlsx: ByteArray): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(xlsx)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name != "xl/worksheets/sheet1.xml") continue
                val out = ByteArrayOutputStream()
                val buf = ByteArray(65_536)
                while (true) {
                    val n = z.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > XML_MAX) return@use null
                }
                return@use out.toByteArray().toString(Charsets.UTF_8)
            }
            null
        }
    }.getOrNull()

    private val ZEILE = Regex("<row\\b[^>]*?(?:/>|>(.*?)</row>)", RegexOption.DOT_MATCHES_ALL)
    private val ZELLE = Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
    private val BEZUG = Regex("\\br=\"([A-Z]+)\\d*\"")
    private val ART = Regex("\\bt=\"([A-Za-z]+)\"")
    private val TEXT = Regex("<t\\b[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    private val WERT = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)

    private fun spalte(buchstaben: String): Int = buchstaben.fold(0) { a, c -> a * 26 + (c - 'A' + 1) } - 1

    private fun entschluesseln(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")

    // Zellen einer Zeile: Spaltennummer zu Text (inlineStr) oder Zahl als Text
    private fun zellen(zeile: String): Map<Int, String> {
        val m = HashMap<Int, String>()
        for (z in ZELLE.findAll(zeile)) {
            val kopf = z.groupValues[1]
            val sp = BEZUG.find(kopf)?.groupValues?.get(1)?.let { spalte(it) } ?: continue
            val inhalt = z.groupValues[2]
            val text = if (ART.find(kopf)?.groupValues?.get(1) == "inlineStr") TEXT.find(inhalt)?.groupValues?.get(1)?.let { entschluesseln(it) }
            else WERT.find(inhalt)?.groupValues?.get(1)
            if (!text.isNullOrBlank()) m[sp] = text.trim()
        }
        return m
    }

    // Tageszeilen aus dem Text des Blatts. Die Kopfzeile wird gesucht (Zelle BusinessDate), die Metallspalten stehen
    // darunter unter ihrem Kuerzel aus zwei Grossbuchstaben (Total zaehlt nicht). Zeilen ohne Datum, Leerzeilen und
    // fehlende Spalten werden uebergangen.
    fun blattLesen(xml: String): List<Tag> {
        var datum = -1
        var spalten: Map<Int, String> = emptyMap()
        val tage = ArrayList<Tag>()
        for (r in ZEILE.findAll(xml)) {
            val z = zellen(r.groupValues[1])
            if (z.isEmpty()) continue
            if (datum < 0) {
                val d = z.entries.firstOrNull { it.value == "BusinessDate" }?.key ?: continue
                datum = d
                spalten = z.filter { (k, v) -> k != d && v.length == 2 && v.all { it in 'A'..'Z' } }
                continue
            }
            val tag = z[datum]?.toDoubleOrNull()?.takeIf { it > EXCEL_ZU_EPOCHE }?.toLong()?.minus(EXCEL_ZU_EPOCHE) ?: continue
            val b = HashMap<String, Double>()
            for ((k, kuerzel) in spalten) z[k]?.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }?.let { b[kuerzel] = it }
            if (b.isNotEmpty()) tage += Tag(tag, b)
        }
        return tage.sortedBy { it.tag }.distinctBy { it.tag }
    }

    // Tageszeilen einer Monatsdatei; leer, wenn sie keine LME-Datei ist.
    fun lesen(xlsx: ByteArray): List<Tag> = blattAusZip(xlsx)?.let { blattLesen(it) }.orEmpty()

    // Tage zweier Quellen zu einer Reihe nach Tag sortiert; bei gleichem Tag gilt der neue Eintrag.
    fun zusammenfuehren(alt: List<Tag>, neu: List<Tag>): List<Tag> =
        (alt + neu).associateBy { it.tag }.values.sortedBy { it.tag }

    // ------------------------------------------------------------ Gespeicherte Form

    // Eine Zeile je Tag: Tag, dann Kuerzel=Tonnen durch Semikolon.
    fun speichern(tage: List<Tag>): String = tage.joinToString("\n") { t ->
        t.tag.toString() + t.bestand.entries.sortedBy { it.key }.joinToString("") { ";" + it.key + "=" + it.value.toLong() }
    }

    fun laden(text: String): List<Tag> = text.lineSequence().mapNotNull { l ->
        val f = l.split(';')
        val tag = f.firstOrNull()?.trim()?.toLongOrNull() ?: return@mapNotNull null
        val b = HashMap<String, Double>()
        for (p in f.drop(1)) {
            val kv = p.split('=')
            if (kv.size == 2) kv[1].toDoubleOrNull()?.let { b[kv[0]] = it }
        }
        if (b.isEmpty()) null else Tag(tag, b)
    }.toList()

    // ------------------------------------------------------------ Auswertung

    // Ein Metall: Bestand am letzten Tag, Veraenderung gegen den Schluss des Vormonats in Prozent (null ohne Vergleich
    // oder bei Vormonatsbestand null) und der Verlauf aller geladenen Tage.
    class Metall(val kuerzel: String, val tonnen: Double, val aenderung: Double?, val verlauf: List<Pair<Long, Double>>)

    class Stand(val tag: Long, val metalle: List<Metall>)

    fun rang(kuerzel: String): Int = REIHENFOLGE.indexOf(kuerzel).let { if (it < 0) REIHENFOLGE.size else it }

    private fun monatVon(tag: Long): Long = LocalDate.ofEpochDay(tag).let { it.year * 12L + it.monthValue }

    fun auswerten(tage: List<Tag>): Stand? {
        val t = zusammenfuehren(emptyList(), tage)
        val letzter = t.lastOrNull() ?: return null
        val mon = monatVon(letzter.tag)
        val liste = ArrayList<Metall>()
        for (k in t.flatMap { it.bestand.keys }.distinct()) {
            val jetzt = letzter.bestand[k] ?: continue
            val verlauf = t.mapNotNull { x -> x.bestand[k]?.let { x.tag to it } }
            // Schluss des Vormonats: letzter Wert im Kalendermonat vor dem des Stands
            val vor = verlauf.lastOrNull { monatVon(it.first) < mon }?.takeIf { monatVon(it.first) == mon - 1 }?.second
            val aenderung = vor?.takeIf { it > 0 }?.let { (jetzt / it - 1.0) * 100.0 }
            liste += Metall(k, jetzt, aenderung, verlauf)
        }
        if (liste.isEmpty()) return null
        return Stand(letzter.tag, liste.sortedWith(compareBy({ rang(it.kuerzel) }, { it.kuerzel })))
    }
}
