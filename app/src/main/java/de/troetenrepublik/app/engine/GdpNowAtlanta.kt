package de.troetenrepublik.app.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

// GDPNow direkt von der Federal Reserve Bank of Atlanta: Die Seite der Quelle verlinkt eine Excel-Datei ohne Schluessel
// (gut zehn Megabyte). Gelesen werden nur zwei Blaetter: CurrentQtrEvolution (Datum, Anlass, Schaetzung fuer das
// laufende Quartal) und TrackRecord (letzte Schaetzung je abgeschlossenem Quartal). Die Datei bleibt nur im Speicher.
object GdpNowAtlanta {

    const val SEITE = "https://www.atlantafed.org/research-and-data/data/gdpnow"
    const val DATEI = "https://www.atlantafed.org/-/media/Project/Atlanta/FRBA/Documents/research-and-data/data/gdpnow/GDPTrackingModelDataAndForecasts.xlsx"
    const val MAX_BYTES = 20_000_000
    const val BLATT_VERLAUF = "CurrentQtrEvolution"
    const val BLATT_BILANZ = "TrackRecord"

    private const val XML_MAX = 40_000_000
    private const val EXCEL_ZU_EPOCHE = 25_569L

    private fun eintraege(xlsx: ByteArray, namen: Set<String>): Map<String, String> {
        val aus = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(xlsx)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name !in namen) continue
                val out = ByteArrayOutputStream()
                val buf = ByteArray(65_536)
                while (true) {
                    val n = z.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > XML_MAX) return emptyMap()
                }
                aus[e.name] = out.toByteArray().toString(Charsets.UTF_8)
                if (aus.size == namen.size) break
            }
        }
        return aus
    }

    private val BLATT = Regex("<sheet\\b[^>]*?name=\"([^\"]+)\"[^>]*?r:id=\"([^\"]+)\"")
    private val BEZIEHUNG = Regex("<Relationship\\b[^>]*>")
    private val ID = Regex("\\bId=\"([^\"]+)\"")
    private val ZIEL = Regex("\\bTarget=\"([^\"]+)\"")
    private val ZEILE = Regex("<row\\b[^>]*?(?:/>|>(.*?)</row>)", RegexOption.DOT_MATCHES_ALL)
    private val ZELLE = Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
    private val BEZUG = Regex("\\br=\"([A-Z]+)\\d*\"")
    private val ART = Regex("\\bt=\"([A-Za-z]+)\"")
    private val WERT = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
    private val TEXT = Regex("<t\\b[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    private val EINTRAG = Regex("<si\\b[^>]*?(?:/>|>(.*?)</si>)", RegexOption.DOT_MATCHES_ALL)
    private val QUARTAL = Regex("(\\d{2}):Q([1-4])")

    private fun spalte(b: String): Int = b.fold(0) { a, c -> a * 26 + (c - 'A' + 1) } - 1

    private fun entschluesseln(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")

    // Pfad des Blatts in der Datei je Name, aus workbook.xml und den Beziehungen
    internal fun blattPfade(arbeitsmappe: String, beziehungen: String): Map<String, String> {
        val ziele = HashMap<String, String>()
        for (b in BEZIEHUNG.findAll(beziehungen)) {
            val id = ID.find(b.value)?.groupValues?.get(1) ?: continue
            val ziel = ZIEL.find(b.value)?.groupValues?.get(1) ?: continue
            ziele[id] = if (ziel.startsWith("/")) ziel.removePrefix("/") else "xl/$ziel"
        }
        return BLATT.findAll(arbeitsmappe).mapNotNull { m -> ziele[m.groupValues[2]]?.let { entschluesseln(m.groupValues[1]) to it } }.toMap()
    }

    internal fun texte(xml: String): List<String> =
        EINTRAG.findAll(xml).map { e -> TEXT.findAll(e.groupValues[1]).joinToString("") { entschluesseln(it.groupValues[1]) } }.toList()

    // Je Zeile: Spalte -> Text oder Zahl als Text
    internal fun zeilen(xml: String, texte: List<String>): List<Map<Int, String>> {
        val aus = ArrayList<Map<Int, String>>()
        for (r in ZEILE.findAll(xml)) {
            val m = HashMap<Int, String>()
            for (z in ZELLE.findAll(r.groupValues[1])) {
                val kopf = z.groupValues[1]
                val sp = BEZUG.find(kopf)?.groupValues?.get(1)?.let(::spalte) ?: continue
                val roh = WERT.find(z.groupValues[2])?.groupValues?.get(1) ?: continue
                val wert = if (ART.find(kopf)?.groupValues?.get(1) == "s") roh.toIntOrNull()?.let { texte.getOrNull(it) } else roh
                if (!wert.isNullOrBlank()) m[sp] = wert.trim()
            }
            aus += m
        }
        return aus
    }

    private fun tag(s: String?): Long? = s?.toDoubleOrNull()?.takeIf { it > EXCEL_ZU_EPOCHE }?.toLong()?.minus(EXCEL_ZU_EPOCHE)

    private fun prozent(s: String?): Double? = s?.toDoubleOrNull()?.takeIf { it.isFinite() && it > -100 && it < 100 }

    // Der Stand aus den beiden Blaettern; null, wenn die Datei nicht den erwarteten Aufbau hat.
    fun lesen(xlsx: ByteArray): GdpNow.Stand? = runCatching {
        val kopf = eintraege(xlsx, setOf("xl/workbook.xml", "xl/_rels/workbook.xml.rels"))
        val pfade = blattPfade(kopf["xl/workbook.xml"] ?: return null, kopf["xl/_rels/workbook.xml.rels"] ?: return null)
        val verlaufPfad = pfade[BLATT_VERLAUF] ?: return null
        val bilanzPfad = pfade[BLATT_BILANZ]
        val teile = eintraege(xlsx, setOfNotNull("xl/sharedStrings.xml", verlaufPfad, bilanzPfad))
        val texte = teile["xl/sharedStrings.xml"]?.let(::texte).orEmpty()
        auswerten(zeilen(teile[verlaufPfad] ?: return null, texte), bilanzPfad?.let { teile[it] }?.let { zeilen(it, texte) }.orEmpty())
    }.getOrNull()

    // verlauf: Zeilen von CurrentQtrEvolution (Spalte A Datum, B Anlass, C Schaetzung), die erste Zeile ist die Kopfzeile;
    // bilanz: Zeilen von TrackRecord (Spalte A Quartalsende, B letzte Schaetzung vor der ersten Meldung des BEA).
    internal fun auswerten(verlauf: List<Map<Int, String>>, bilanz: List<Map<Int, String>>): GdpNow.Stand? {
        val reihe = LinkedHashMap<Long, Double>()
        var quartalStart: Long? = null
        for (z in verlauf.drop(1)) {
            val t = tag(z[0]) ?: continue
            val w = prozent(z[2]) ?: continue
            if (quartalStart == null) {
                // Das Quartal steht im Anlass der ersten Zeile („Initial GDPNow 26:Q4 forecast“)
                QUARTAL.find(z[1].orEmpty())?.let { m ->
                    quartalStart = LocalDate.of(2000 + m.groupValues[1].toInt(), (m.groupValues[2].toInt() - 1) * 3 + 1, 1).toEpochDay()
                }
            }
            reihe[t] = w
        }
        val start = quartalStart ?: return null
        if (reihe.isEmpty()) return null
        val liste = reihe.toList().sortedBy { it.first }
        val wert = liste.last().second
        val stand = liste.asReversed().takeWhile { it.second == wert }.last().first
        val vor = bilanz.drop(1).mapNotNull { z -> tag(z[0])?.takeIf { it < start }?.let { t -> prozent(z[1])?.let { t to it } } }
            .maxByOrNull { it.first }?.second
        return GdpNow.Stand(start, wert, stand, liste, vor, GdpNow.Quelle.ATLANTA)
    }
}
