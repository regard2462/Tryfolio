package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

// Zinsprojektionen der Fed [0.78]: Viermal im Jahr nennt jedes Mitglied des Offenmarktausschusses den Leitzins, den es
// zum Ende der naechsten Jahre und auf lange Sicht fuer angemessen haelt (Summary of Economic Projections, Figure 2).
// Die Federal Reserve veroeffentlicht die Zahl der Teilnehmer je Zinssatz als Tabelle; die Datei
// indikatoren/fed_punkte.json (tools/daten/fed_punkte.py) traegt alle Sitzungen seit 2021, eine neue Seite liest
// seiteLesen. Saetze in Prozent (Mitte der Zielspanne); die Datei speichert Tausendstel.
object FedPunkte {

    const val DATEI = "indikatoren/fed_punkte.json"
    const val BASIS = "https://www.federalreserve.gov/monetarypolicy/"
    const val KALENDER = BASIS + "fomccalendars.htm"

    // Spalte des laengerfristigen Satzes
    const val LANG = "L"

    // stimmen: Zahl der Teilnehmer je Spalte der Sitzung
    class Zeile(val satz: Double, val stimmen: List<Int>)

    // spalten: Jahre ("2026") und LANG. zeilen: hoechster Satz zuerst, nur Saetze mit mindestens einer Stimme.
    class Sitzung(val tag: LocalDate, val spalten: List<String>, val zeilen: List<Zeile>)

    // sitzungen: aelteste zuerst
    class Datei(val stand: String, val sitzungen: List<Sitzung>)

    private fun sitzung(o: JsonObject): Sitzung? {
        val tag = (o["tag"] as? JsonPrimitive)?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val spalten = (o["j"] as? JsonArray)?.map { (it as? JsonPrimitive)?.content ?: return null } ?: return null
        if (spalten.isEmpty() || spalten.any { it != LANG && it.toIntOrNull() == null }) return null
        val zeilen = (o["p"] as? JsonArray).orEmpty().mapNotNull { e ->
            val a = (e as? JsonArray)?.map { (it as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null } ?: return@mapNotNull null
            if (a.size != spalten.size + 1 || a.drop(1).any { it < 0 } || a.drop(1).all { it == 0 }) null else Zeile(a[0] / 1000.0, a.drop(1))
        }.sortedByDescending { it.satz }
        return if (zeilen.isEmpty()) null else Sitzung(tag, spalten, zeilen)
    }

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val liste = (w["sitzungen"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::sitzung) }.sortedBy { it.tag }
        if (liste.isEmpty()) null else Datei(stand, liste)
    }.getOrNull()

    // Sitzungen in der Form der Datei (fuer den Zwischenspeicher einer nachgeholten Seite)
    fun schreiben(stand: String, sitzungen: List<Sitzung>): String =
        "{\"stand\":\"$stand\",\"sitzungen\":[" + sitzungen.joinToString(",") { s ->
            "{\"tag\":\"${s.tag}\",\"j\":[" + s.spalten.joinToString(",") { "\"$it\"" } + "],\"p\":[" +
                s.zeilen.joinToString(",") { z -> "[" + (listOf(Math.round(z.satz * 1000).toInt()) + z.stimmen).joinToString(",") + "]" } + "]}"
        } + "]}"

    // ---- Seiten der Federal Reserve

    private val SEITE = Regex("fomcprojtabl(\\d{4})(\\d{2})(\\d{2})\\.htm")
    private val REIHE = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val ZELLE = Regex("<t[hd][^>]*>(.*?)</t[hd]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val MARKE = Regex("<[^>]+>")

    private fun zelle(s: String): String = MARKE.replace(s, "").replace("&nbsp;", " ").replace(' ', ' ').trim()

    // Juengste Seite mit Projektionen im Sitzungskalender: Tag der Sitzung und Dateiname; null ohne Treffer
    fun neuesteSeite(kalender: String): Pair<LocalDate, String>? =
        SEITE.findAll(kalender).mapNotNull { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()?.let { it to m.value }
        }.maxByOrNull { it.first }

    // Tag aus einem Dateinamen wie fomcprojtabl20260916.htm
    fun tagAus(name: String): LocalDate? = neuesteSeite(name)?.first

    // Figure 2 einer Projektionsseite: Kopfzeile mit Jahren und "Longer run", je Zeile der Satz und die Zahl der
    // Teilnehmer je Spalte (leere Zellen zaehlen null). null, wenn die Tabelle fehlt oder anders aufgebaut ist.
    fun seiteLesen(html: String, tag: LocalDate): Sitzung? {
        val i = html.indexOf("Midpoint of target range or target level for the federal funds rate")
        if (i < 0) return null
        val a = html.indexOf("<table", i)
        if (a < 0) return null
        val b = html.indexOf("</table>", a)
        if (b < 0) return null
        val reihen = REIHE.findAll(html.substring(a, b)).map { r -> ZELLE.findAll(r.groupValues[1]).map { zelle(it.groupValues[1]) }.toList() }.toList()
        if (reihen.size < 2 || reihen[0].size < 2) return null
        val spalten = reihen[0].drop(1).map { k ->
            when {
                k.length == 4 && k.toIntOrNull() != null -> k
                k.startsWith("Longer", ignoreCase = true) -> LANG
                else -> return null
            }
        }
        val zeilen = reihen.drop(1).mapNotNull { t ->
            if (t.size != spalten.size + 1) return@mapNotNull null
            val satz = t[0].toDoubleOrNull()?.takeIf { it.isFinite() && it in -5.0..30.0 } ?: return@mapNotNull null
            val stimmen = t.drop(1).map { if (it.isEmpty()) 0 else it.toIntOrNull()?.takeIf { n -> n in 0..40 } ?: return@mapNotNull null }
            if (stimmen.all { it == 0 }) null else Zeile(satz, stimmen)
        }.sortedByDescending { it.satz }
        return if (zeilen.isEmpty()) null else Sitzung(tag, spalten, zeilen)
    }

    // Datei mit einer weiteren Sitzung; eine Sitzung desselben Tags wird ersetzt
    fun mit(d: Datei, neu: Sitzung): Datei = Datei(d.stand, (d.sitzungen.filter { it.tag != neu.tag } + neu).sortedBy { it.tag })

    // ---- Auswertung

    // Punkte einer Spalte: Satz und Zahl der Teilnehmer, hoechster Satz zuerst
    fun punkte(s: Sitzung, spalte: String): List<Pair<Double, Int>> {
        val i = s.spalten.indexOf(spalte)
        if (i < 0) return emptyList()
        return s.zeilen.mapNotNull { z -> z.stimmen[i].takeIf { it > 0 }?.let { z.satz to it } }
    }

    fun teilnehmer(s: Sitzung, spalte: String): Int = punkte(s, spalte).sumOf { it.second }

    // Median der Teilnehmer einer Spalte; bei gerader Zahl das Mittel der beiden mittleren Angaben (wie die Fed)
    fun median(s: Sitzung, spalte: String): Double? {
        val p = punkte(s, spalte).sortedBy { it.first }
        val n = p.sumOf { it.second }
        if (n == 0) return null
        fun an(k: Int): Double {
            var bisher = 0
            for ((satz, zahl) in p) { bisher += zahl; if (k < bisher) return satz }
            return p.last().first
        }
        return if (n % 2 == 1) an(n / 2) else (an(n / 2 - 1) + an(n / 2)) / 2.0
    }

    // Kernzeile: Median zum Ende des ersten Jahres der juengsten Sitzung; vorher: Median derselben Spalte in der Sitzung
    // davor (null, wenn sie das Jahr nicht fuehrt).
    class Kern(val sitzung: Sitzung, val jahr: Int, val median: Double, val teilnehmer: Int, val vorher: Double?, val vorherTag: LocalDate?)

    fun kern(d: Datei): Kern? {
        val s = d.sitzungen.lastOrNull() ?: return null
        val spalte = s.spalten.firstOrNull { it != LANG } ?: return null
        val m = median(s, spalte) ?: return null
        val davor = d.sitzungen.dropLast(1).lastOrNull()
        val alt = davor?.let { median(it, spalte) }
        return Kern(s, spalte.toInt(), m, teilnehmer(s, spalte), alt, davor?.tag.takeIf { alt != null })
    }

    // Was der Markt fuer den Dezember eines Jahres erwartet (Fed-Funds-Futures); null, wenn der Pfad nicht so weit reicht
    fun marktEnde(pfad: List<Zinserwartung.Monat>, jahr: Int): Double? = pfad.firstOrNull { it.jahr == jahr && it.monat == 12 }?.zins
}
