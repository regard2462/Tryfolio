package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate

// Strom in Europa: Day-Ahead-Grosshandelspreise je Gebotszone (ENTSO-E Transparency Platform, XML, nur mit dem eigenen
// Token der Person) und Stromerzeugung und Verbrauch in Deutschland (SMARD der Bundesnetzagentur, JSON, ohne Schluessel).
// Hier steht nur die Rechnung: Adressen, Lesen der Antworten, Zusammenfassen. Der Abruf liegt in market/StromAbruf.kt.
object StromMarkt {

    private const val TAG_MS = 86_400_000L

    // ------------------------------------------------------------ Gebotszonen

    // Gebotszone eines Landes mit ihrer EIC-Kennung; zusatz nennt die Zone, wenn ein Land mehrere hat (die App zeigt dann
    // die groesste oder die mit dem staerksten Bezug zum Landesschnitt).
    class Zone(val a2: String, val eic: String, val zusatz: String? = null)

    val ZONEN: List<Zone> = listOf(
        Zone("DE", "10Y1001A1001A82H", "DE-LU"),
        Zone("FR", "10YFR-RTE------C"),
        Zone("NL", "10YNL----------L"),
        Zone("BE", "10YBE----------2"),
        Zone("AT", "10YAT-APG------L"),
        Zone("CH", "10YCH-SWISSGRIDZ"),
        Zone("ES", "10YES-REE------0"),
        Zone("PT", "10YPT-REN------W"),
        Zone("IT", "10Y1001A1001A73I", "Nord"),
        Zone("PL", "10YPL-AREA-----S"),
        Zone("CZ", "10YCZ-CEPS-----N"),
        Zone("SK", "10YSK-SEPS-----K"),
        Zone("HU", "10YHU-MAVIR----U"),
        Zone("SI", "10YSI-ELES-----O"),
        Zone("HR", "10YHR-HEP------M"),
        Zone("RO", "10YRO-TEL------P"),
        Zone("BG", "10YCA-BULGARIA-R"),
        Zone("GR", "10YGR-HTSO-----Y"),
        Zone("DK", "10YDK-1--------W", "DK1"),
        Zone("NO", "10YNO-2--------T", "NO2"),
        Zone("SE", "10Y1001A1001A46L", "SE3"),
        Zone("FI", "10YFI-1--------U"),
        Zone("EE", "10Y1001A1001A39I"),
        Zone("LV", "10YLV-1001A00074"),
        Zone("LT", "10YLT-1001A0008Q"),
        Zone("IE", "10Y1001A1001A59C", "SEM"),
    )

    fun zone(a2: String): Zone? = ZONEN.firstOrNull { it.a2.equals(a2, ignoreCase = true) }

    // Ein Token der Transparency Platform: Buchstaben, Ziffern und Bindestriche, nie Leerzeichen oder Sonderzeichen,
    // damit nichts in die Adresse gelangt, was dort nicht hingehoert.
    private val TOKEN = Regex("^[A-Za-z0-9_-]{8,80}$")
    fun tokenGueltig(t: String): Boolean = TOKEN.matches(t.trim())

    const val ENTSOE_HOST = "web-api.tp.entsoe.eu"
    const val ENTSOE_SEITE = "https://transparency.entsoe.eu/"
    const val ENTSOE_ANLEITUNG = "https://documenter.getpostman.com/view/7009892/2s93JtP3F6"
    const val SMARD_SEITE = "https://www.smard.de/"

    private fun stempel(d: LocalDate) = d.year.toString().padStart(4, '0') + d.monthValue.toString().padStart(2, '0') + d.dayOfMonth.toString().padStart(2, '0') + "0000"

    // Day-Ahead-Preise (documentType A44) einer Zone; Zeitraum von (einschliesslich) bis bis (ausschliesslich), in UTC.
    fun preisAdresse(zone: Zone, von: LocalDate, bis: LocalDate, token: String): String =
        "https://$ENTSOE_HOST/api?securityToken=${token.trim()}&documentType=A44&in_Domain=${zone.eic}&out_Domain=${zone.eic}" +
            "&periodStart=${stempel(von)}&periodEnd=${stempel(bis)}"

    // ------------------------------------------------------------ ENTSO-E lesen

    // Preis eines Liefertags in EUR/MWh: Mittel aller Zeitscheiben (stuendlich oder viertelstuendlich), niedrigster und
    // hoechster Wert. tag ist die Tageszahl seit 1970 des Liefertags am Ort der Zone.
    class Tag(val tag: Long, val mittel: Double, val min: Double, val max: Double)

    private val PERIODE = Regex("<Period>(.*?)</Period>", RegexOption.DOT_MATCHES_ALL)
    private val PUNKT = Regex("<Point>(.*?)</Point>", RegexOption.DOT_MATCHES_ALL)
    private val START = Regex("<start>\\s*([^<\\s]+)\\s*</start>")
    private val ENDE = Regex("<end>\\s*([^<\\s]+)\\s*</end>")
    private val AUFLOESUNG = Regex("<resolution>\\s*([^<\\s]+)\\s*</resolution>")
    private val POSITION = Regex("<position>\\s*(\\d+)\\s*</position>")
    private val PREIS = Regex("<price\\.amount>\\s*([^<\\s]+)\\s*</price\\.amount>")
    private val ZEIT = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})(?::\\d{2})?Z$")
    private val DAUER = Regex("^PT(?:(\\d+)H)?(?:(\\d+)M)?$")
    private val GRUND = Regex("<Reason>.*?<code>\\s*(\\d+)\\s*</code>", RegexOption.DOT_MATCHES_ALL)
    private val GRUND_TEXT = Regex("<Reason>.*?<text>(.*?)</text>", RegexOption.DOT_MATCHES_ALL)

    // "2026-11-12T23:00Z" als Millisekunden seit 1970 (UTC); null bei anderer Form
    internal fun zeitLesen(s: String): Long? {
        val m = ZEIT.find(s.trim()) ?: return null
        val (j, mo, t, h, mi) = m.destructured
        val tag = runCatching { LocalDate.of(j.toInt(), mo.toInt(), t.toInt()).toEpochDay() }.getOrNull() ?: return null
        return tag * TAG_MS + h.toLong() * 3_600_000L + mi.toLong() * 60_000L
    }

    // "PT60M", "PT15M", "PT1H" als Millisekunden; null bei anderer Form
    internal fun dauerLesen(s: String): Long? {
        val m = DAUER.find(s.trim()) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val mi = m.groupValues[2].toLongOrNull() ?: 0L
        return ((h * 60 + mi) * 60_000L).takeIf { it > 0 }
    }

    // Tagespreise aus einem Publication_MarketDocument. Je Zeitreihe gilt: fehlende Positionen wiederholen den vorigen Preis
    // (Kurventyp A03), die Zeitreihe reicht bis zum Ende ihres Zeitraums. Ein Tag, den mehrere Zeitreihen liefern
    // (stuendlich und viertelstuendlich), nimmt die feinere. Unlesbares oder ein Acknowledgement ergibt eine leere Liste.
    fun preiseLesen(xml: String): List<Tag> {
        if (!xml.contains("<Publication_MarketDocument")) return emptyList()
        // Je Liefertag: Zeitscheibe in Millisekunden und die Preise; die feinste Zeitscheibe gewinnt
        val tage = HashMap<Long, Pair<Long, List<Double>>>()
        for (p in PERIODE.findAll(xml)) {
            val block = p.groupValues[1]
            val start = START.find(block)?.groupValues?.get(1)?.let(::zeitLesen) ?: continue
            val ende = ENDE.find(block)?.groupValues?.get(1)?.let(::zeitLesen)
            val schritt = AUFLOESUNG.find(block)?.groupValues?.get(1)?.let(::dauerLesen) ?: continue
            val punkte = HashMap<Int, Double>()
            for (pt in PUNKT.findAll(block)) {
                val b = pt.groupValues[1]
                val pos = POSITION.find(b)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val preis = PREIS.find(b)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
                if (pos in 1..100_000 && preis.isFinite()) punkte[pos] = preis
            }
            if (punkte.isEmpty()) continue
            val anzahl = maxOf(punkte.keys.max(), ende?.let { ((it - start) / schritt).toInt() } ?: 0).coerceAtMost(10_000)
            val werte = ArrayList<Double>(anzahl)
            var vorher = punkte[1] ?: continue
            for (i in 1..anzahl) {
                vorher = punkte[i] ?: vorher
                werte += vorher
            }
            // Ein Zeitraum von rund 24 Stunden ist ein Tag (23 und 25 Stunden an Zeitumstellungen); laenger: je 24 Stunden ein Tag
            val gesamt = anzahl * schritt
            val teile = maxOf(1, Math.round(gesamt.toDouble() / TAG_MS).toInt())
            val erster = Math.floorDiv(start + TAG_MS / 2, TAG_MS)
            for (k in 0 until teile) {
                val von = k * anzahl / teile
                val bis = (k + 1) * anzahl / teile
                if (bis <= von) continue
                val tag = erster + k
                val alt = tage[tag]
                if (alt == null || schritt < alt.first) tage[tag] = schritt to werte.subList(von, bis).toList()
            }
        }
        return tage.entries.sortedBy { it.key }.map { (tag, v) ->
            val w = v.second
            Tag(tag, w.average(), w.min(), w.max())
        }
    }

    // Kennzahl und Text einer Absage des Dienstes (Acknowledgement_MarketDocument); null bei einer normalen Antwort.
    // Code 999: im Zeitraum liegen keine Daten vor.
    fun absage(xml: String): Pair<Int, String>? {
        if (!xml.contains("<Acknowledgement_MarketDocument")) return null
        val code = GRUND.find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val text = GRUND_TEXT.find(xml)?.groupValues?.get(1)?.trim().orEmpty().take(200)
        return code to text
    }

    // Einordnung des juengsten Tages gegen den Vortag und den Schnitt der Tage davor
    class Lage(val tag: Tag, val vortag: Tag?, val schnitt: Double?, val anzahl: Int) {
        // Abstand zum Vortag in Prozent des Vortags; null ohne Vortag oder bei Vortagspreis nahe null
        val gegenVortag: Double? get() = vortag?.takeIf { kotlin.math.abs(it.mittel) >= 1.0 }?.let { (tag.mittel - it.mittel) / kotlin.math.abs(it.mittel) * 100.0 }
        val gegenSchnitt: Double? get() = schnitt?.takeIf { kotlin.math.abs(it) >= 1.0 }?.let { (tag.mittel - it) / kotlin.math.abs(it) * 100.0 }
    }

    // Juengster Tag bis einschliesslich heute (heute: Tageszahl); Tage danach (morgen) zaehlen nicht als Tageswert.
    // schnitt: Mittel der bis zu 30 Tage davor.
    fun lage(tage: List<Tag>, heute: Long): Lage? {
        val bisher = tage.filter { it.tag <= heute }.sortedBy { it.tag }
        val jetzt = bisher.lastOrNull() ?: return null
        val davor = bisher.dropLast(1).takeLast(30)
        val vortag = davor.lastOrNull()?.takeIf { it.tag == jetzt.tag - 1 }
        return Lage(jetzt, vortag, davor.takeIf { it.size >= 5 }?.map { it.mittel }?.average(), davor.size)
    }

    // Preis von morgen, sobald der Markt ihn veroeffentlicht hat (meist ab Mittag), sonst null
    fun morgen(tage: List<Tag>, heute: Long): Tag? = tage.firstOrNull { it.tag == heute + 1 }

    // ------------------------------------------------------------ Verlauf ablegen (Zwischenspeicher auf dem Geraet)

    // Eine Zeile je Tag: Tageszahl, Mittel, niedrigster, hoechster Wert, durch Semikolon getrennt
    fun tageSpeichern(tage: List<Tag>): String = tage.joinToString("\n") { "${it.tag};${it.mittel};${it.min};${it.max}" }

    fun tageLaden(text: String): List<Tag> = text.lineSequence().mapNotNull { z ->
        val t = z.trim().split(';')
        if (t.size != 4) return@mapNotNull null
        val tag = t[0].toLongOrNull() ?: return@mapNotNull null
        val w = t.drop(1).map { it.toDoubleOrNull() ?: return@mapNotNull null }
        Tag(tag, w[0], w[1], w[2])
    }.sortedBy { it.tag }.toList()

    // Neue Tage ersetzen gleiche alte, aeltere als keep Tage vor dem neuesten fallen weg
    fun zusammenfuehren(alt: List<Tag>, neu: List<Tag>, behalten: Int = 120): List<Tag> {
        val m = LinkedHashMap<Long, Tag>()
        for (t in alt) m[t.tag] = t
        for (t in neu) m[t.tag] = t
        val alle = m.values.sortedBy { it.tag }
        return alle.takeLast(behalten)
    }

    // ------------------------------------------------------------ SMARD (Deutschland)

    // Filter der SMARD-Reihen: Erzeugung nach Quelle und Verbrauch (Netzlast), jeweils in MW je Stunde
    class Erzeuger(val filter: Int, val schluessel: String, val erneuerbar: Boolean)

    val ERZEUGER: List<Erzeuger> = listOf(
        Erzeuger(4068, "pv", true),
        Erzeuger(4067, "windOn", true),
        Erzeuger(1225, "windOff", true),
        Erzeuger(1226, "wasser", true),
        Erzeuger(4066, "biomasse", true),
        Erzeuger(1228, "sonstErneuerbar", true),
        Erzeuger(4071, "gas", false),
        Erzeuger(1223, "braunkohle", false),
        Erzeuger(4069, "steinkohle", false),
        Erzeuger(4070, "pumpspeicher", false),
        Erzeuger(1227, "sonstKonventionell", false),
    )
    const val VERBRAUCH = 410
    const val SMARD_REGION = "DE"

    fun smardIndexAdresse(filter: Int) = "https://www.smard.de/app/chart_data/$filter/$SMARD_REGION/index_hour.json"
    fun smardBlockAdresse(filter: Int, start: Long) = "https://www.smard.de/app/chart_data/$filter/$SMARD_REGION/${filter}_${SMARD_REGION}_hour_$start.json"

    private val json = Json { ignoreUnknownKeys = true }

    // Wochenanfaenge (Millisekunden) aus index_hour.json, aufsteigend; leer bei unlesbarer Antwort
    fun smardIndex(text: String): List<Long> = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        (o["timestamps"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }?.sorted().orEmpty()
    }.getOrDefault(emptyList())

    // Stundenwerte (Millisekunden, MW) eines Wochenblocks; Stunden ohne Wert (null, noch nicht gemeldet) fallen weg
    fun smardReihe(text: String): List<Pair<Long, Double>> = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        (o["series"] as? JsonArray).orEmpty().mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            if (a.size < 2) return@mapNotNull null
            val t = (a[0] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val v = a[1]
            if (v is JsonNull) return@mapNotNull null
            val w = (v as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            if (w.isFinite()) t to w else null
        }.sortedBy { it.first }
    }.getOrDefault(emptyList())

    class Anteil(val schluessel: String, val erneuerbar: Boolean, val mw: Double, val prozent: Double)

    // Erzeugung der letzten 24 Stunden: Anteile je Quelle am Mittel, Anteil der Erneuerbaren, Ende des Zeitraums
    class Mix(val bis: Long, val anteile: List<Anteil>, val erneuerbar: Double, val gesamtMw: Double)

    private const val STUNDE_MS = 3_600_000L

    // reihen: Schluessel der Erzeuger aus ERZEUGER. Das Fenster endet bei der juengsten Stunde, zu der alle Quellen einen
    // Wert haben; es braucht mindestens 12 Stunden, sonst null. Quellen ohne Werte zaehlen als 0.
    fun mix(reihen: Map<String, List<Pair<Long, Double>>>): Mix? {
        val vorhanden = ERZEUGER.filter { reihen[it.schluessel].orEmpty().isNotEmpty() }
        if (vorhanden.size < 6) return null
        val mengen: List<Set<Long>> = vorhanden.map { e -> reihen.getValue(e.schluessel).map { it.first }.toSet() }
        val gemeinsam: Set<Long> = mengen.reduce { a, b -> a intersect b }
        val ende = gemeinsam.maxOrNull() ?: return null
        val von = ende - 23 * STUNDE_MS
        val stunden = gemeinsam.filter { it in von..ende }.toHashSet()
        if (stunden.size < 12) return null
        val mittel = vorhanden.associate { e ->
            val w = reihen.getValue(e.schluessel).filter { it.first in stunden }.map { it.second.coerceAtLeast(0.0) }
            e to w.average()
        }
        val summe = mittel.values.sum()
        if (summe <= 0.0) return null
        val anteile = mittel.map { (e, mw) -> Anteil(e.schluessel, e.erneuerbar, mw, mw / summe * 100.0) }.sortedByDescending { it.mw }
        return Mix(ende, anteile, anteile.filter { it.erneuerbar }.sumOf { it.prozent }, summe)
    }

    // Anteil der Erneuerbaren an der Erzeugung je Stunde in Prozent, fuer die letzten tage Tage bis zur juengsten
    // vollstaendigen Stunde
    fun erneuerbarVerlauf(reihen: Map<String, List<Pair<Long, Double>>>, tage: Int = 7): List<Pair<Long, Double>> {
        val vorhanden = ERZEUGER.filter { reihen[it.schluessel].orEmpty().isNotEmpty() }
        if (vorhanden.size < 6) return emptyList()
        val nachZeit = vorhanden.associate { e -> e to reihen.getValue(e.schluessel).toMap() }
        val mengen: List<Set<Long>> = nachZeit.values.map { it.keys }
        val gemeinsam = mengen.reduce { a, b -> a intersect b }.sorted()
        val ende = gemeinsam.lastOrNull() ?: return emptyList()
        return gemeinsam.filter { it > ende - tage * 24L * STUNDE_MS }.mapNotNull { t ->
            var alle = 0.0
            var gruen = 0.0
            for ((e, m) in nachZeit) {
                val w = (m[t] ?: 0.0).coerceAtLeast(0.0)
                alle += w
                if (e.erneuerbar) gruen += w
            }
            if (alle > 0.0) t to gruen / alle * 100.0 else null
        }
    }

    // Verbrauch (Netzlast): Wert der juengsten Stunde und Mittel der letzten 24 Stunden in MW; null ohne Werte
    class Last(val jetzt: Double, val mittel24: Double, val zeit: Long)

    fun last(reihe: List<Pair<Long, Double>>): Last? {
        val l = reihe.lastOrNull() ?: return null
        val fenster = reihe.filter { it.first > l.first - 24 * STUNDE_MS }
        if (fenster.size < 12) return null
        return Last(l.second, fenster.map { it.second }.average(), l.first)
    }

    // Ablage: je Reihe eine Zeile "Schluessel;Zeit:Wert,Zeit:Wert"
    fun smardSpeichern(reihen: Map<String, List<Pair<Long, Double>>>): String =
        reihen.entries.joinToString("\n") { (k, r) -> "$k;" + r.joinToString(",") { "${it.first}:${it.second}" } }

    fun smardLaden(text: String): Map<String, List<Pair<Long, Double>>> {
        val m = LinkedHashMap<String, List<Pair<Long, Double>>>()
        for (z in text.lineSequence()) {
            val i = z.indexOf(';')
            if (i <= 0) continue
            val punkte = z.substring(i + 1).split(',').mapNotNull { p ->
                val t = p.split(':')
                if (t.size != 2) null else (t[0].toLongOrNull() ?: return@mapNotNull null) to (t[1].toDoubleOrNull() ?: return@mapNotNull null)
            }
            m[z.substring(0, i)] = punkte.sortedBy { it.first }
        }
        return m
    }
}
