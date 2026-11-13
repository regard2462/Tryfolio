package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Oel weltweit (0.78): Foerderung je Land und Region, Rohoel der OPEC+, Verbrauch und Lager der OECD je Monat aus dem
// Short-Term Energy Outlook der U.S. Energy Information Administration (gemeinfrei). Mitgeliefert als
// assets/welt/oel.json (tools/daten/oel_welt.py). Mengen in Millionen Barrel am Tag, Lager in Millionen Barrel. Die
// juengsten Monate der Laenderwerte sind Schaetzungen der EIA. Monate als Zahl jahr * 12 + monat - 1.
object OelWelt {

    const val DATEI = "welt/oel.json"
    const val SEITE = "https://www.eia.gov/outlooks/steo/"

    @Serializable
    class Land(val a2: String, val de: String, val en: String, val w: List<Double?>, val opec: Boolean = false, val opecplus: Boolean = false)

    // id: NORDAMERIKA, SUEDAMERIKA, EUROPA, EURASIEN, NAHOST, AFRIKA, ASIEN; w: Summe der Region
    @Serializable
    class Gruppe(val id: String, val w: List<Double?>, val laender: List<Land> = emptyList())

    @Serializable
    class Foerderung(val welt: List<Double?>, val opec: List<Double?> = emptyList(), val opecplus: List<Double?> = emptyList(), val gruppen: List<Gruppe>)

    // kapazitaet und frei: Foerderkapazitaet und freie Kapazitaet der OPEC; ausfallOpec und ausfallAndere: ungeplante Ausfaelle
    @Serializable
    class Rohoel(
        val welt: List<Double?>, val opecplus: List<Double?>, val laender: List<Land>, val kapazitaet: List<Double?> = emptyList(),
        val frei: List<Double?> = emptyList(), val ausfallOpec: List<Double?> = emptyList(), val ausfallAndere: List<Double?> = emptyList(),
    )

    @Serializable
    class Verbrauch(val welt: List<Double?>, val gruppen: List<Gruppe>)

    // Kommerzielle Lager am Monatsende in Millionen Barrel; abbauWelt: Lagerabbau der Welt in Millionen Barrel am Tag
    // (negativ: Aufbau)
    @Serializable
    class Lager(val oecd: List<Double?>, val usa: List<Double?> = emptyList(), val oecdOhneUsa: List<Double?> = emptyList(), val abbauWelt: List<Double?> = emptyList())

    // Prognose der EIA fuer die Monate nach ist
    @Serializable
    class Prognose(val foerderung: List<Double?> = emptyList(), val verbrauch: List<Double?> = emptyList())

    // Vorgaben der OPEC+ in Tausend Barrel am Tag je Land (ISO-Code), gueltig im Monat monat (JJJJ-MM)
    @Serializable
    class Quoten(val monat: String, val laender: Map<String, Double>)

    @Serializable
    class Datei(
        val stand: String, val quelle: String = "", val ab: String, val ist: String, val foerderung: Foerderung, val rohoel: Rohoel,
        val verbrauch: Verbrauch, val lager: Lager, val prognose: Prognose? = null, val quoten: Quoten? = null,
    ) {
        // Erster Monat der Reihen und letzter Monat mit Bestand oder Schaetzung
        val abMonat: Int get() = monat(ab) ?: 0
        val istMonat: Int get() = monat(ist) ?: 0
        val monate: Int get() = foerderung.welt.size
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun monat(text: String): Int? {
        val t = text.trim()
        if (t.length < 7 || t[4] != '-') return null
        val j = t.substring(0, 4).toIntOrNull() ?: return null
        val m = t.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return j * 12 + m - 1
    }

    // Erster Tag des Monats in Millisekunden (UTC)
    fun ms(m: Int): Long = java.time.LocalDate.of(m.floorDiv(12), m.mod(12) + 1, 1).toEpochDay() * 86_400_000L

    // Datei lesen; null, wenn sie nicht passt (Monate fehlen, Reihen verschieden lang, keine Region).
    fun lesen(text: String): Datei? = runCatching {
        val d = json.decodeFromString(Datei.serializer(), text)
        val n = d.foerderung.welt.size
        val ab = monat(d.ab) ?: return null
        val ist = monat(d.ist) ?: return null
        if (n < 13 || ist - ab + 1 != n || d.foerderung.gruppen.isEmpty()) return null
        val alle = d.foerderung.gruppen.flatMap { g -> listOf(g.w) + g.laender.map { it.w } } + d.verbrauch.gruppen.flatMap { g -> listOf(g.w) + g.laender.map { it.w } } +
            d.rohoel.laender.map { it.w } + listOf(d.rohoel.opecplus, d.verbrauch.welt, d.lager.oecd)
        if (alle.any { it.size != n }) return null
        d
    }.getOrNull()

    val datei: Datei? by lazy { runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() }

    // Letzter Wert einer Reihe, der Wert zwoelf Monate davor und die Aenderung in Prozent; platz: Stelle des Werts.
    class Wert(val jetzt: Double, val vorjahr: Double?, val platz: Int) {
        val aenderung: Double? get() = vorjahr?.takeIf { it > 0 }?.let { (jetzt / it - 1) * 100 }
    }

    fun wert(reihe: List<Double?>): Wert? {
        val i = reihe.indexOfLast { it != null }
        if (i < 0) return null
        return Wert(reihe[i] ?: return null, reihe.getOrNull(i - 12), i)
    }

    // Anteil eines Werts an einer Summe in Prozent; null ohne Summe
    fun anteil(teil: Double, summe: Double?): Double? = summe?.takeIf { it > 0 }?.let { teil / it * 100 }

    // Rest einer Region: Summe minus aufgefuehrte Laender, nie negativ; null, wenn ein Wert fehlt
    fun rest(g: Gruppe, platz: Int): Double? {
        val summe = g.w.getOrNull(platz) ?: return null
        val laender = g.laender.sumOf { it.w.getOrNull(platz) ?: return null }
        return (summe - laender).coerceAtLeast(0.0)
    }

    // Reihe ohne Luecken fuer eine kleine Verlaufslinie
    fun verlauf(reihe: List<Double?>): List<Double> = reihe.filterNotNull()

    // Mittel der vollen Kalenderjahre einer Monatsreihe: (Jahr, Mittel)
    fun jahresmittel(reihe: List<Double?>, abMonat: Int): List<Pair<Int, Double>> =
        reihe.withIndex().groupBy { (abMonat + it.index).floorDiv(12) }.mapNotNull { (jahr, l) ->
            val w = l.mapNotNull { it.value }
            if (w.size == 12) jahr to w.average() else null
        }.sortedBy { it.first }

    // Foerderung eines Landes gegen seine Vorgabe: Abweichung in Tausend Barrel am Tag (positiv: ueber der Vorgabe).
    // foerderung in Millionen Barrel am Tag, vorgabe in Tausend Barrel am Tag.
    fun gegenVorgabe(foerderung: Double, vorgabe: Double): Double = foerderung * 1000 - vorgabe

    // Rohoel der OPEC+ je Land im Monat der Vorgaben gegen die Vorgabe: (Land, Foerderung, Vorgabe, Abweichung); leer ohne
    // Vorgaben oder wenn ihr Monat nicht in den Reihen liegt.
    class Erfuellung(val land: Land, val foerderung: Double, val vorgabe: Double, val abweichung: Double)

    fun erfuellung(d: Datei): List<Erfuellung> {
        val q = d.quoten ?: return emptyList()
        val platz = (monat(q.monat) ?: return emptyList()) - d.abMonat
        // Vorgaben fuer einen Monat nach den Daten: der letzte Monat mit Wert gilt
        val p = if (platz >= d.monate) d.monate - 1 else platz
        if (p < 0) return emptyList()
        return d.rohoel.laender.mapNotNull { l ->
            val v = q.laender[l.a2] ?: return@mapNotNull null
            val f = l.w.getOrNull(p) ?: return@mapNotNull null
            Erfuellung(l, f, v, gegenVorgabe(f, v))
        }
    }

    // Stand der Boersenlager eines Metalls gegen den Wert etwa einen Monat davor (25 bis 45 Tage): Aenderung in Prozent;
    // null ohne passenden Vergleichstag. verlauf: (Tag seit 1970, Bestand), aelteste zuerst.
    fun gegenVormonat(verlauf: List<Pair<Long, Double>>): Double? {
        val letzter = verlauf.lastOrNull() ?: return null
        val vor = verlauf.lastOrNull { letzter.first - it.first in 25..45 }?.second?.takeIf { it > 0 } ?: return null
        return (letzter.second / vor - 1) * 100
    }
}
