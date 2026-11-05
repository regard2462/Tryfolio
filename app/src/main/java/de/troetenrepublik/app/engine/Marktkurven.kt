package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

// Marktkurven (seit 5.91): Zinskurve der US-Staatsanleihen (CSV des US-Finanzministeriums, dieselbe wie bei den
// Fruehwarnzeichen) und der AAA-Staatsanleihen im Euroraum (EZB, Datensatz YC, Spot-Rates), dazu die Kurve der
// VIX-Indizes von einem Tag bis einem Jahr. Seit 5.94 dazu die Kurve der Bundeswertpapiere (Deutsche Bundesbank,
// Zinsstruktur nach Svensson, Tageswerte). Je Kurve heute, vor einem Monat und vor einem Jahr.
// Normal steigt die Zinskurve mit der Laufzeit. Flach heisst: kurze und lange Zinsen liegen fast gleich. Invers
// heisst: kurze Laufzeiten bringen mehr als zehn Jahre, das ging in den USA vielen Rezessionen voraus.
object Marktkurven {

    // Laufzeit in Kalendertagen und Wert (Zins in Prozent oder VIX-Stand).
    @Serializable
    data class Punkt(val tage: Int, val wert: Double)

    // Kurve eines Tages (Tage seit 1970), Punkte nach Laufzeit sortiert.
    @Serializable
    data class Kurve(val tag: Long, val punkte: List<Punkt>) {
        fun wert(tage: Int): Double? = punkte.firstOrNull { it.tage == tage }?.wert
    }

    // Heute, vor einem Monat und vor einem Jahr; fehlt ein Stichtag, bleibt er leer.
    @Serializable
    data class Vergleich(val heute: Kurve, val monat: Kurve? = null, val jahr: Kurve? = null)

    // Zwischengespeicherter Stand beider Zinskurven.
    @Serializable
    data class Zinskurven(val abgerufen: Long = 0L, val usa: Vergleich? = null, val euro: Vergleich? = null, val deutschland: Vergleich? = null)

    enum class Form { NORMAL, FLACH, INVERS }

    enum class VixForm { CONTANGO, FLACH, BACKWARDATION }

    private const val TAG = 86_400_000L
    const val ZEHN_JAHRE = 3650

    // Unter diesem Abstand (Prozentpunkte) zwischen zehn Jahren und der hoechsten kurzen Laufzeit gilt die Kurve als flach.
    const val FLACH_GRENZE = 0.25

    private val json = Json { ignoreUnknownKeys = true }

    fun monate(m: Double): Int = (m * 30.4375).roundToInt()
    fun jahre(j: Int): Int = j * 365

    // Laufzeit aus Angaben wie „3 Mo“, „1.5 Month“, „10 Yr“ (US-Finanzministerium) oder „SR_6M“, „SR_10Y“ (EZB).
    fun laufzeit(text: String): Int? {
        val t = text.trim().trim('"').lowercase().removePrefix("sr_")
        val m = Regex("""^(\d+(?:\.\d+)?)\s*(mo|month|months|m|yr|year|years|y)$""").matchEntire(t) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        return if (m.groupValues[2].startsWith("y")) (n * 365).roundToInt() else monate(n)
    }

    // Alle Laufzeiten je Tag aus der CSV des US-Finanzministeriums (Datum MM/TT/JJJJ), aelteste zuerst. Leere
    // Felder fehlen in der Kurve.
    fun treasuryKurven(csv: String): List<Kurve> {
        val zeilen = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (zeilen.isEmpty()) return emptyList()
        val kopf = zeilen[0].split(',').map { it.trim().trim('"') }
        val spalten = kopf.mapIndexedNotNull { i, k -> if (i == 0) null else laufzeit(k)?.let { i to it } }
        if (spalten.isEmpty()) return emptyList()
        return zeilen.drop(1).mapNotNull { z ->
            val f = z.split(',')
            val d = Regex("""^(\d{2})/(\d{2})/(\d{4})$""").matchEntire(f.getOrNull(0)?.trim()?.trim('"').orEmpty()) ?: return@mapNotNull null
            val tag = Math.floorDiv(BrokerExport.zeit("${d.groupValues[3]}-${d.groupValues[1]}-${d.groupValues[2]}"), TAG)
            val punkte = spalten.mapNotNull { (i, tage) -> f.getOrNull(i)?.trim()?.trim('"')?.toDoubleOrNull()?.let { Punkt(tage, it) } }
                .sortedBy { it.tage }
            if (punkte.isEmpty()) null else Kurve(tag, punkte)
        }.sortedBy { it.tag }.distinctBy { it.tag }
    }

    val EZB_LAUFZEITEN = listOf("3M", "6M", "1Y", "2Y", "3Y", "5Y", "7Y", "10Y", "20Y", "30Y")

    // Eine Abfrage fuer alle Laufzeiten ab start (JJJJ-MM-TT), nur Daten ohne Beschreibung.
    fun ezbAdresse(start: String): String =
        "https://data-api.ecb.europa.eu/service/data/YC/B.U2.EUR.4F.G_N_A.SV_C_YM." +
            EZB_LAUFZEITEN.joinToString("+") { "SR_$it" } + "?format=csvdata&detail=dataonly&startPeriod=$start"

    // Kurven je Tag aus der CSV der EZB (eine Zeile je Laufzeit und Tag), aelteste zuerst.
    fun ezbKurven(csv: String): List<Kurve> {
        val zeilen = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (zeilen.size < 2) return emptyList()
        val kopf = zeilen[0].split(',').map { it.trim().trim('"') }
        val iArt = kopf.indexOf("DATA_TYPE_FM"); val iZeit = kopf.indexOf("TIME_PERIOD"); val iWert = kopf.indexOf("OBS_VALUE")
        val iSchluessel = kopf.indexOf("KEY")
        if (iZeit < 0 || iWert < 0 || (iArt < 0 && iSchluessel < 0)) return emptyList()
        val jeTag = HashMap<Long, MutableList<Punkt>>()
        for (z in zeilen.drop(1)) {
            val f = z.split(',')
            val art = if (iArt >= 0) f.getOrNull(iArt) else f.getOrNull(iSchluessel)?.substringAfterLast('.')
            val tage = art?.let { laufzeit(it) } ?: continue
            val datum = f.getOrNull(iZeit)?.trim()?.trim('"') ?: continue
            if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(datum)) continue
            val wert = f.getOrNull(iWert)?.trim()?.trim('"')?.toDoubleOrNull() ?: continue
            jeTag.getOrPut(Math.floorDiv(BrokerExport.zeit(datum), TAG)) { ArrayList() } += Punkt(tage, wert)
        }
        return jeTag.map { (tag, p) -> Kurve(tag, p.distinctBy { it.tage }.sortedBy { it.tage }) }.sortedBy { it.tag }
    }

    // Restlaufzeiten der Bundesbank-Reihen in Jahren (Reihenschluessel R01XX bis R30XX)
    val BUNDESBANK_JAHRE = listOf(1, 2, 3, 5, 7, 10, 20, 30)

    // Zinsstruktur der boersennotierten Bundeswertpapiere (Svensson, Tageswerte), alle Laufzeiten in einer Abfrage ab
    // start (JJJJ-MM-TT). Die Statistik-Schnittstelle nutzt eine Zertifikatskette, die jedes Android ab 7 kennt.
    fun bundesbankAdresse(start: String): String =
        "https://api.statistiken.bundesbank.de/rest/data/BBSIS/D.I.ZST.ZI.EUR.S1311.B.A604." +
            BUNDESBANK_JAHRE.joinToString("+") { "R%02dXX".format(it) } + ".R.A.A._Z._Z.A?format=csv&lang=en&startPeriod=$start"

    // Kurven je Tag aus der CSV der Bundesbank: Kopf mit den Reihenschluesseln (je Reihe eine Wert- und eine
    // FLAGS-Spalte), danach Metadaten und eine Zeile je Kalendertag; „.“ heisst kein Wert (Wochenende, Feiertag).
    fun bundesbankKurven(csv: String): List<Kurve> {
        val zeilen = csv.trimStart('\uFEFF').lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val kopf = zeilen.firstOrNull()?.split(',')?.map { it.trim().trim('"') } ?: return emptyList()
        val spalten = kopf.mapIndexedNotNull { i, k ->
            if (k.endsWith("_FLAGS")) null else Regex("""\.R(\d{2})XX\.""").find(k)?.groupValues?.get(1)?.toIntOrNull()?.let { i to jahre(it) }
        }
        if (spalten.isEmpty()) return emptyList()
        return zeilen.drop(1).mapNotNull { z ->
            val f = z.split(',')
            val datum = f.getOrNull(0)?.trim()?.trim('"').orEmpty()
            if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(datum)) return@mapNotNull null
            val punkte = spalten.mapNotNull { (i, tage) -> f.getOrNull(i)?.trim()?.trim('"')?.toDoubleOrNull()?.let { Punkt(tage, it) } }.sortedBy { it.tage }
            if (punkte.isEmpty()) null else Kurve(Math.floorDiv(BrokerExport.zeit(datum), TAG), punkte)
        }.sortedBy { it.tag }.distinctBy { it.tag }
    }

    // Juengste Kurve und die letzten Kurven auf oder vor 30 und 365 Tagen davor. Liegt die naechste fruehere Kurve
    // mehr als zehn Tage vor dem Stichtag, fehlt er.
    fun vergleich(kurven: List<Kurve>): Vergleich? {
        val sortiert = kurven.filter { it.punkte.size >= 2 }.sortedBy { it.tag }
        val heute = sortiert.lastOrNull() ?: return null
        fun am(ziel: Long) = sortiert.lastOrNull { it.tag <= ziel }?.takeIf { ziel - it.tag <= 10 }
        return Vergleich(heute, am(heute.tag - 30), am(heute.tag - 365))
    }

    // Kurze Laufzeiten bis zwei Jahre.
    private fun kurze(k: Kurve) = k.punkte.filter { it.tage <= 730 }

    // Form der Kurve aus dem Abstand zwischen zehn Jahren und der hoechsten kurzen Laufzeit (bis zwei Jahre).
    fun form(k: Kurve): Form? {
        val zehn = k.wert(ZEHN_JAHRE) ?: return null
        val hoechste = kurze(k).maxOfOrNull { it.wert } ?: return null
        val abstand = zehn - hoechste
        return when {
            abstand < 0 -> Form.INVERS
            abstand < FLACH_GRENZE -> Form.FLACH
            else -> Form.NORMAL
        }
    }

    // Kurze Laufzeiten (bis zwei Jahre), die mehr bringen als zehn Jahre.
    fun invertiert(k: Kurve): List<Punkt> {
        val zehn = k.wert(ZEHN_JAHRE) ?: return emptyList()
        return kurze(k).filter { it.wert > zehn }
    }

    // Zehn Jahre minus die kuerzeste Laufzeit ab drei Monaten, in Prozentpunkten.
    fun abstand(k: Kurve): Pair<Int, Double>? {
        val zehn = k.wert(ZEHN_JAHRE) ?: return null
        val kurz = k.punkte.filter { it.tage >= monate(3.0) && it.tage < ZEHN_JAHRE }.minByOrNull { it.tage } ?: return null
        return kurz.tage to zehn - kurz.wert
    }

    fun lesen(text: String): Zinskurven? = runCatching { json.decodeFromString(Zinskurven.serializer(), text) }.getOrNull()
    fun schreiben(z: Zinskurven): String = json.encodeToString(Zinskurven.serializer(), z)

    // VIX-Kurve

    // Yahoo-Symbole der VIX-Indizes mit ihrer Laufzeit in Tagen.
    val VIX_INDIZES = listOf("^VIX1D" to 1, "^VIX9D" to 9, "^VIX" to 30, "^VIX3M" to 93, "^VIX6M" to 183, "^VIX1Y" to 365)

    // Kurve heute (letzter Schluss je Index, hoechstens fuenf Tage alt) und vor einem Monat (letzter Schluss auf oder
    // vor 30 Tagen vor dem juengsten Kurs, hoechstens sieben Tage davor). reihen: je Symbol (Zeit in ms, Stand),
    // aelteste zuerst.
    fun vixKurven(reihen: Map<String, List<Pair<Long, Double>>>): Pair<Kurve, Kurve?>? {
        val letzte = reihen.values.mapNotNull { it.lastOrNull()?.first }.maxOrNull() ?: return null
        val heute = Kurve(Math.floorDiv(letzte, TAG), VIX_INDIZES.mapNotNull { (s, tage) ->
            reihen[s]?.lastOrNull { it.second > 0 }?.takeIf { letzte - it.first <= 5 * TAG }?.let { Punkt(tage, it.second) }
        })
        if (heute.punkte.size < 2) return null
        val ziel = letzte - 30 * TAG
        val monat = Kurve(Math.floorDiv(ziel, TAG), VIX_INDIZES.mapNotNull { (s, tage) ->
            reihen[s]?.lastOrNull { it.first <= ziel && it.second > 0 }?.takeIf { ziel - it.first <= 7 * TAG }?.let { Punkt(tage, it.second) }
        })
        return heute to monat.takeIf { it.punkte.size >= 2 }
    }

    // VIX zu VIX3M wie bei den Fruehwarnzeichen: ueber 1 faellt die Kurve (Backwardation, akute Angst), ab 0,95 ist sie
    // flach, sonst steigt sie (Contango, der Normalfall). Ohne einen der beiden: kuerzeste gegen laengste Laufzeit.
    fun vixForm(k: Kurve): VixForm? {
        val kurz = k.wert(30) ?: k.punkte.firstOrNull()?.wert ?: return null
        val lang = k.wert(93) ?: k.punkte.lastOrNull()?.wert ?: return null
        if (lang <= 0 || k.punkte.size < 2) return null
        val q = kurz / lang
        return when {
            q > 1.0 -> VixForm.BACKWARDATION
            q >= 0.95 -> VixForm.FLACH
            else -> VixForm.CONTANGO
        }
    }
}
