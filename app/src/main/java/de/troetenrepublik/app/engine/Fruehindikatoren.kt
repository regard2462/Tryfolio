package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

// Fruehe Signale aus oeffentlichen Daten (seit 5.92): Lagerbestaende von Oel und Gas, Erstantraege auf
// Arbeitslosenhilfe, Fluggaeste und Duerre in den USA, mit Schluessel auch die Gasspeicher der EU. Jedes Signal
// bekommt eine Ampel, die zeigt, wie ungewoehnlich der Stand ist: Gruen im ueblichen Rahmen, Gelb deutlich
// abweichend, Rot ausserhalb dessen, was in den letzten fuenf Jahren zur selben Jahreszeit vorkam. Ob ein Signal
// schon im Preis steckt, schaetzt seit 5.92 engine/Eingepreist.kt aus der Geschichte (Abschnitt „Im Preis“ unten).
object Fruehindikatoren {

    const val ROHOEL = "rohoel"
    const val BENZIN = "benzin"
    const val DESTILLAT = "destillat"
    const val CUSHING = "cushing"
    const val SPR = "spr"
    const val GAS_US = "gas_us"
    const val GAS_EU = "gas_eu"
    const val ERSTANTRAEGE = "erstantraege"
    const val FLUGGAESTE = "fluggaeste"
    const val DUERRE = "duerre"

    // Reihenfolge im Abschnitt „Fruehe Signale“ der Maerkte
    val AUSBLICK = listOf(ROHOEL, GAS_US, GAS_EU, ERSTANTRAEGE, FLUGGAESTE, DUERRE)

    // Reihenfolge der Lager auf der Seite von Oel
    val OEL = listOf(ROHOEL, CUSHING, BENZIN, DESTILLAT)

    // Wochenreihen der EIA (Energy Information Administration): Kennung der Reihe und Bereich der Adresse.
    val EIA = mapOf(
        ROHOEL to "WCESTUS1", // Rohoel ohne strategische Reserve, Tausend Barrel
        BENZIN to "WGTSTUS1",
        DESTILLAT to "WDISTUS1", // Diesel und Heizoel
        CUSHING to "W_EPC0_SAX_YCUOK_MBBL", // Lagerort Cushing (Oklahoma), Lieferort des WTI-Kontrakts
        SPR to "WCSSTUS1", // strategische Reserve
        GAS_US to "NW2_EPG0_SWO_R48_BCF", // Arbeitsgas in den unteren 48 Staaten, Milliarden Kubikfuss
    )

    fun eiaAdresse(reihe: String): String =
        "https://www.eia.gov/dnav/" + (if (reihe.startsWith("NW")) "ng" else "pet") + "/hist_xls/${reihe}w.xls"

    // Erstantraege auf Arbeitslosenhilfe (US-Arbeitsministerium), saisonbereinigt, ueber FRED.
    // Abruf mit der Kennung der App.
    fun fredAdresse(von: LocalDate, reihe: String = "ICSA") = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=$reihe&cosd=$von"

    // Fluggaeste an den Kontrollstellen der TSA, eine Seite je Jahr (die laufende ohne Jahreszahl).
    fun tsaAdresse(jahr: Int?) = "https://www.tsa.gov/travel/passenger-volumes" + (jahr?.let { "/$it" } ?: "")

    // Anteil der Flaeche der USA (ohne Alaska und Hawaii) in Duerre (Stufe D1 bis D4), woechentlich.
    fun duerreAdresse(von: LocalDate, bis: LocalDate): String {
        fun us(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}/${d.year}"
        return "https://usdmdataservices.unl.edu/api/USStatistics/GetDroughtSeverityStatisticsByAreaPercent" +
            "?aoi=us&startdate=${us(von)}&enddate=${us(bis)}&statisticsType=1"
    }

    // Gasspeicher der EU (GIE AGSI+), nur mit eigenem kostenlosem Schluessel: ohne Datum die letzten 30 Gastage,
    // mit Datum genau dieser Tag.
    fun agsiAdresse(datum: LocalDate? = null) = "https://agsi.gie.eu/api?type=eu" + (datum?.let { "&date=$it" } ?: "")

    // ------------------------------------------------------------ Einordnung

    enum class Lage { DARUNTER, KNAPP, UEBLICH, REICHLICH, DARUEBER }

    // Spanne derselben Jahreszeit in den Vorjahren: kleinster, groesster und mittlerer Wert, Zahl der Jahre.
    data class Band(val min: Double, val max: Double, val schnitt: Double, val jahre: Int)

    // Ein Signal. wert in der Einheit der Quelle, stand als Tag seit 1970. abweichung: bei Lagern gegen den
    // Fuenfjahresschnitt (in % oder bei Anteilen in Punkten), bei Erstantraegen der Anstieg ueber das Jahrestief in %,
    // bei Fluggaesten die Veraenderung zum Vorjahr in %. aenderung: zur Vorwoche, in der Einheit von wert.
    data class Signal(
        val schluessel: String,
        val ampel: Fruehwarnung.Ampel,
        val wert: Double,
        val stand: Long,
        val abweichung: Double?,
        val aenderung: Double? = null,
        val band: Band? = null,
        val lage: Lage? = null,
    )

    // Werte der Vorjahre um denselben Kalendertag: je Jahr der naechste Punkt hoechstens toleranz Tage entfernt.
    // Mindestens drei Jahre, sonst null.
    fun band(reihe: List<Pair<Long, Double>>, jahre: Int = 5, toleranz: Int = 4): Band? {
        val letzter = reihe.lastOrNull() ?: return null
        val tag = LocalDate.ofEpochDay(letzter.first)
        val werte = (1..jahre).mapNotNull { j ->
            val ziel = tag.minusYears(j.toLong()).toEpochDay()
            reihe.filter { kotlin.math.abs(it.first - ziel) <= toleranz }.minByOrNull { kotlin.math.abs(it.first - ziel) }?.second
        }
        if (werte.size < 3) return null
        return Band(werte.min(), werte.max(), werte.average(), werte.size)
    }

    // Lagerbestand gegen die Vorjahre. punkte: der Wert ist selbst ein Anteil in %, verglichen wird in Punkten.
    // nurHoch: nur ein hoher Stand ist auffaellig (Duerre), ein niedriger bleibt gruen.
    fun lager(schluessel: String, reihe: List<Pair<Long, Double>>, punkte: Boolean = false, nurHoch: Boolean = false): Signal? {
        val sauber = reihe.filter { it.second.isFinite() }.sortedBy { it.first }
        val letzter = sauber.lastOrNull() ?: return null
        val b = band(sauber) ?: return null
        val abw = if (punkte) letzter.second - b.schnitt else if (b.schnitt > 0) (letzter.second / b.schnitt - 1) * 100 else return null
        val lage = when {
            letzter.second < b.min -> Lage.DARUNTER
            letzter.second > b.max -> Lage.DARUEBER
            abw <= -5 -> Lage.KNAPP
            abw >= 5 -> Lage.REICHLICH
            else -> Lage.UEBLICH
        }
        val ampel = when (lage) {
            Lage.DARUNTER -> if (nurHoch) Fruehwarnung.Ampel.GRUEN else Fruehwarnung.Ampel.ROT
            Lage.KNAPP -> if (nurHoch) Fruehwarnung.Ampel.GRUEN else Fruehwarnung.Ampel.GELB
            Lage.UEBLICH -> Fruehwarnung.Ampel.GRUEN
            Lage.REICHLICH -> Fruehwarnung.Ampel.GELB
            Lage.DARUEBER -> Fruehwarnung.Ampel.ROT
        }
        val vorher = sauber.getOrNull(sauber.size - 2)?.takeIf { letzter.first - it.first in 1..10 }
        return Signal(schluessel, ampel, letzter.second, letzter.first, abw, vorher?.let { letzter.second - it.second }, b, lage)
    }

    // Erstantraege: Vierwochenschnitt gegen sein Tief der letzten 52 Wochen. Ein Anstieg um 20 % und mehr ging
    // frueher oft einer Rezession voraus (rot), ab 10 % gelb.
    fun erstantraege(reihe: List<Pair<Long, Double>>): Signal? {
        val r = reihe.filter { it.second > 0 }.sortedBy { it.first }
        if (r.size < 56) return null
        val schnitte = (3 until r.size).map { i -> (i - 3..i).sumOf { r[it].second } / 4 }
        val jetzt = schnitte.last()
        val tief = schnitte.takeLast(52).min()
        val anstieg = (jetzt / tief - 1) * 100
        val ampel = when {
            anstieg >= 20 -> Fruehwarnung.Ampel.ROT
            anstieg >= 10 -> Fruehwarnung.Ampel.GELB
            else -> Fruehwarnung.Ampel.GRUEN
        }
        return Signal(ERSTANTRAEGE, ampel, r.last().second, r.last().first, anstieg, r.last().second - r[r.size - 2].second)
    }

    // Fluggaeste: Summe der letzten 28 Tage gegen dieselben Wochentage ein Jahr zuvor (364 Tage), damit Wochenenden
    // auf Wochenenden treffen. wert ist der Tagesschnitt der letzten sieben Tage. Ab 3 % weniger rot, weniger gelb.
    fun fluggaeste(tage: List<Pair<Long, Double>>): Signal? {
        val k = tage.filter { it.second > 0 }.associate { it.first to it.second }
        val letzter = k.keys.maxOrNull() ?: return null
        val paare = (0 until 28).mapNotNull { i -> val t = letzter - i; k[t]?.let { a -> k[t - 364]?.let { b -> a to b } } }
        if (paare.size < 25) return null
        val veraenderung = (paare.sumOf { it.first } / paare.sumOf { it.second } - 1) * 100
        val woche = (0 until 7).mapNotNull { k[letzter - it] }
        val ampel = when {
            veraenderung >= 0 -> Fruehwarnung.Ampel.GRUEN
            veraenderung > -3 -> Fruehwarnung.Ampel.GELB
            else -> Fruehwarnung.Ampel.ROT
        }
        return Signal(FLUGGAESTE, ampel, woche.average(), letzter, veraenderung)
    }

    // Gasspeicher der EU: Fuellstand in % gegen dieselbe Zeit der Vorjahre, Wochenaenderung in Punkten.
    fun gasEu(reihe: List<Pair<Long, Double>>): Signal? = lager(GAS_EU, reihe, punkte = true)?.let { s ->
        val vor = reihe.firstOrNull { it.first == s.stand - 7 }
        s.copy(aenderung = vor?.let { s.wert - it.second })
    }

    // ------------------------------------------------------------ Leser

    // FRED-CSV (Kopfzeile, dann Datum JJJJ-MM-TT und Wert; fehlende Werte als Punkt).
    fun fredLesen(csv: String): List<Pair<Long, Double>> =
        csv.lineSequence().drop(1).mapNotNull { z ->
            val f = z.split(',')
            val t = runCatching { LocalDate.parse(f.getOrNull(0)?.trim().orEmpty()).toEpochDay() }.getOrNull() ?: return@mapNotNull null
            val w = f.getOrNull(1)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            t to w
        }.distinctBy { it.first }.sortedBy { it.first }.toList()

    private val TSA_ZEILE = Regex("""<td[^>]*>\s*(\d{1,2})/(\d{1,2})/(\d{4})\s*</td>\s*<td[^>]*>\s*([\d,.]+)\s*</td>""")

    // Tabelle der TSA-Seite: Datum (M/T/JJJJ) und Zahl der Reisenden mit Tausenderkomma.
    fun tsaLesen(html: String): List<Pair<Long, Double>> =
        TSA_ZEILE.findAll(html).mapNotNull { m ->
            val (mo, ta, ja) = m.destructured
            val t = runCatching { LocalDate.of(ja.toInt(), mo.toInt(), ta.toInt()).toEpochDay() }.getOrNull() ?: return@mapNotNull null
            val w = m.groupValues[4].replace(",", "").replace(".", "").toDoubleOrNull() ?: return@mapNotNull null
            t to w
        }.distinctBy { it.first }.sortedBy { it.first }.toList()

    // CSV des Drought Monitor (kumuliert): Spalte D1 ist der Anteil der Flaeche mit mindestens maessiger Duerre.
    fun duerreLesen(csv: String): List<Pair<Long, Double>> {
        val zeilen = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val kopf = zeilen.firstOrNull()?.split(',')?.map { it.trim().trim('"') } ?: return emptyList()
        val iDatum = kopf.indexOf("MapDate")
        val iD1 = kopf.indexOf("D1")
        if (iDatum < 0 || iD1 < 0) return emptyList()
        return zeilen.drop(1).mapNotNull { z ->
            val f = z.split(',').map { it.trim().trim('"') }
            val d = f.getOrNull(iDatum)?.takeIf { it.length == 8 } ?: return@mapNotNull null
            val t = runCatching { LocalDate.of(d.take(4).toInt(), d.substring(4, 6).toInt(), d.substring(6).toInt()).toEpochDay() }.getOrNull()
                ?: return@mapNotNull null
            val w = f.getOrNull(iD1)?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 } ?: return@mapNotNull null
            t to w
        }.distinctBy { it.first }.sortedBy { it.first }
    }

    // Antwort von AGSI: data mit gasDayStart und full (Fuellstand in %).
    fun agsiLesen(json: String): List<Pair<Long, Double>> {
        val wurzel = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return emptyList()
        return (wurzel["data"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val tag = (o["gasDayStart"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val t = runCatching { LocalDate.parse(tag).toEpochDay() }.getOrNull() ?: return@mapNotNull null
            val w = (o["full"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it in 0.0..110.0 } ?: return@mapNotNull null
            t to w
        }.distinctBy { it.first }.sortedBy { it.first }
    }

    // ------------------------------------------------------------ Im Preis (5.92)

    // Woran der Markt gemessen wird: Terminkurve (Kontrakt 1 gegen 4), Preis gegen seinen Jahresschnitt,
    // Airline-Aktien gegen den S&P 500, die zweijaehrige US-Rendite unter ihrem Jahreshoch.
    // BRANCHE (5.94): Branchenaktien gegen den S&P 500 ueber ein Jahr, fuer Lieferketten, Fracht und Halbleiter.
    enum class Mass { KURVE, PREIS, AIRLINES, ZINS, BRANCHE }

    // Ergebnis „Im Preis?“ zu einem Signal. zusatz: bei Erstantraegen zyklische gegen defensive Aktien. kurve:
    // heutige Kurse der vier naechsten Kontrakte. markt: Kuerzel bei Yahoo bzw. Wurzel der Kontrakte.
    data class ImPreis(
        val schluessel: String,
        val mass: Mass,
        val einordnung: Eingepreist.Einordnung,
        val zusatz: Eingepreist.Einordnung? = null,
        val kurve: List<Pair<Eingepreist.Kontrakt, Double>> = emptyList(),
        val markt: String = "",
    )

    // Futures-Kurse der EIA (Kontrakte 1 bis 4, Wochenschnitte, gemeinfrei). Die EIA fuehrt sie nur bis zum
    // 5. April 2024, sie dienen allein der Schaetzung; die heutige Kurve kommt von Yahoo.
    const val EIA_FUTURES_OEL = "https://www.eia.gov/dnav/pet/xls/PET_PRI_FUT_S1_W.xls"
    const val EIA_FUTURES_GAS = "https://www.eia.gov/dnav/ng/xls/NG_PRI_FUT_S1_W.xls"

    // Datei, Blatt (0 ist das Inhaltsblatt), Kuerzel bei Yahoo und plausibler Kursbereich des ersten Kontrakts.
    data class Termin(val datei: String, val blatt: Int, val wurzel: String, val boerse: String, val min: Double, val max: Double)

    val TERMIN = mapOf(
        ROHOEL to Termin(EIA_FUTURES_OEL, 1, "CL", "NYM", 5.0, 500.0), // WTI Cushing, Dollar je Barrel
        CUSHING to Termin(EIA_FUTURES_OEL, 1, "CL", "NYM", 5.0, 500.0),
        BENZIN to Termin(EIA_FUTURES_OEL, 3, "RB", "NYM", 0.2, 20.0), // RBOB New York, Dollar je Gallone
        DESTILLAT to Termin(EIA_FUTURES_OEL, 4, "HO", "NYM", 0.2, 20.0), // Heizoel bzw. Diesel New York
        GAS_US to Termin(EIA_FUTURES_GAS, 2, "NG", "NYM", 0.5, 50.0), // Henry Hub, Dollar je Million BTU
    )

    // Passt die Tabelle zum Rohstoff? Mittlerer erster Kontrakt im plausiblen Bereich (sonst falsches Blatt).
    fun terminPlausibel(t: Termin, tabelle: List<Pair<Long, DoubleArray>>): Boolean {
        val erste = tabelle.mapNotNull { it.second.firstOrNull()?.takeIf { v -> v.isFinite() && v > 0 } }.sorted()
        if (erste.size < 100) return false
        return erste[erste.size / 2] in t.min..t.max
    }

    // Getreide zur Duerre: fortlaufender Kontrakt bei Yahoo; ohne passenden Wert Mais.
    val GETREIDE = listOf("ZC=F", "ZW=F", "ZS=F")

    fun getreide(symbol: String?): String = symbol?.uppercase()?.takeIf { it in GETREIDE } ?: "ZC=F"

    // Fluggaeste: Airline-ETF gegen den S&P 500 ueber ein Jahr. Geschaetzt erst ab Mitte 2022, weil die Vergleiche
    // zum Vorjahr davor nur die Pandemie und ihre Erholung zeigen.
    const val AIRLINES = "JETS"
    const val BREITER_MARKT = "SPY"
    val FLUG_AB: LocalDate = LocalDate.of(2022, 7, 1)

    // Erstantraege: zweijaehrige US-Rendite (FRED DGS2) und zyklische (XLY) gegen defensive Konsumwerte (XLP).
    const val ZWEI_JAHRE = "DGS2"
    const val ZYKLISCH = "XLY"
    const val DEFENSIV = "XLP"

    // Ersatzquellen (5.94): Lieferketten und Fracht gegen US-Transportwerte, Halbleiterproduktion gegen Chipwerte,
    // jeweils ueber ein Jahr gegen den S&P 500. Mehr Fracht und mehr Chipproduktion sollten der Branche helfen; beim
    // Lieferkettendruck ist die Richtung offen (2021 verdienten Reedereien daran, Spediteure litten).
    val ERSATZ_MARKT = mapOf(Ersatzquellen.LIEFERKETTEN to "IYT", Ersatzquellen.FRACHT to "IYT", Ersatzquellen.HALBLEITER to "SOXX")
    val ERSATZ_VORZEICHEN = mapOf(Ersatzquellen.LIEFERKETTEN to 0, Ersatzquellen.FRACHT to 1, Ersatzquellen.HALBLEITER to 1)

    // Monatsdaten erscheinen ein bis drei Monate spaeter; so weit darf der Markt voraus sein.
    const val ERSATZ_ABSTAND = 120

    // EU-Gas: Erdgas am niederlaendischen Handelspunkt TTF.
    const val TTF = "TTF=F"

    // Lager gegen die Terminkurve: Abweichungen aus der Lagerreihe, Spannen aus der EIA-Tabelle; heute die
    // Abweichung des Signals und die Spanne der Kurve von Yahoo.
    fun imPreisLager(
        schluessel: String, lager: List<Pair<Long, Double>>, tabelle: List<Pair<Long, DoubleArray>>,
        kurve: List<Pair<Eingepreist.Kontrakt, Double>>, heute: Long,
    ): ImPreis? {
        val jetzt = lager(schluessel, lager)?.abweichung ?: return null
        val punkte = Eingepreist.paaren(Eingepreist.abweichungen(lager), Eingepreist.spannen(tabelle), 6)
        val s = Eingepreist.schaetzen(punkte, saison = true, vorzeichen = -1) ?: return null
        val y = Eingepreist.kurvenSpanne(kurve) ?: return null
        return ImPreis(schluessel, Mass.KURVE, Eingepreist.einordnen(s, jetzt, y, heute), kurve = kurve, markt = TERMIN[schluessel]?.wurzel.orEmpty())
    }

    // Allgemein aus zwei Reihen, heute jeweils der letzte Wert. ab: erst ab diesem Tag schaetzen. Liegen die
    // letzten Werte mehr als abstand Tage (sonst drei Wochen) auseinander, gibt es keine Einordnung.
    fun einordnungAusReihen(
        x: List<Pair<Long, Double>>, y: List<Pair<Long, Double>>, vorzeichen: Int, saison: Boolean, ab: Long = Long.MIN_VALUE, toleranz: Int = 6,
        abstand: Int = 21,
    ): Eingepreist.Einordnung? {
        val xs = x.sortedBy { it.first }
        val ys = y.sortedBy { it.first }
        val heuteX = xs.lastOrNull() ?: return null
        val heuteY = ys.lastOrNull() ?: return null
        if (kotlin.math.abs(heuteX.first - heuteY.first) > abstand) return null
        val s = Eingepreist.schaetzen(Eingepreist.paaren(xs.filter { it.first >= ab }, ys, toleranz), saison, vorzeichen) ?: return null
        return Eingepreist.einordnen(s, heuteX.second, heuteY.second, maxOf(heuteX.first, heuteY.first))
    }

    // Zahl der Seiten einer AGSI-Antwort (last_page), 1 ohne Angabe.
    fun agsiSeiten(json: String): Int = runCatching {
        ((Json.parseToJsonElement(json) as? JsonObject)?.get("last_page") as? JsonPrimitive)?.content?.toIntOrNull()
    }.getOrNull() ?: 1

    // Gasspeicher der EU fuer einen Zeitraum, 300 Tage je Seite (mit eigenem Schluessel).
    fun agsiZeitraum(von: LocalDate, bis: LocalDate, seite: Int) = "https://agsi.gie.eu/api?type=eu&from=$von&to=$bis&size=300&page=$seite"

    // ------------------------------------------------------------ Wertpapiere

    // Fruehes Signal fuer eine Branche (Yahoo-Branchenname), leer ohne passendes. Rohstoff-Schluessel aus
    // engine/Rohstoffe.kt haben Vorrang; Sojabohnen (ZS=F) haben dort keinen Schluessel und zaehlen ueber symbol.
    fun zuWert(rohstoff: String?, branche: String, symbol: String = ""): List<String> = when (rohstoff) {
        "OEL" -> OEL
        "GAS" -> listOf(GAS_US, GAS_EU)
        "WEIZEN", "MAIS" -> listOf(DUERRE)
        null -> when {
            symbol.equals("ZS=F", true) -> listOf(DUERRE)
            branche.equals("Airlines", true) || branche.equals("Travel Services", true) -> listOf(FLUGGAESTE)
            branche.startsWith("Oil & Gas", true) -> listOf(ROHOEL)
            branche.equals("Utilities - Regulated Gas", true) -> listOf(GAS_US)
            branche.equals("Agricultural Inputs", true) || branche.equals("Farm Products", true) -> listOf(DUERRE)
            else -> emptyList()
        }
        else -> emptyList()
    }
}
