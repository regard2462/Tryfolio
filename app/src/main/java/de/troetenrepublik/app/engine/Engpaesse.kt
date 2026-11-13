package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.LocalDate

// Engpaesse des Seehandels (Ebene „Engpaesse“ im Globus): Durchfahrten pro Tag nach IMF PortWatch (Schiffssignale AIS,
// Dienst Daily_Chokepoints_Data, 28 Engpaesse). Verglichen wird der Schnitt der letzten sieben Tage mit dem Schnitt
// desselben Zeitraums im Vorjahr (28 Tage um denselben Tag). Ein Abruf liefert hoechstens 1000 Zeilen.
object Engpaesse {
    const val DIENST = "https://services9.arcgis.com/weJ1QsnbMYJlCHdG/arcgis/rest/services/Daily_Chokepoints_Data/FeatureServer/0/query"
    const val SEITE = "https://portwatch.imf.org"
    const val GUELTIG_MS = 24 * 3_600_000L

    // Tage im Schnitt der Gegenwart und halbe Breite des Vorjahresfensters
    const val TAGE = 7
    const val VORJAHR_HALB = 14
    // Verlauf im Blatt: so viele Tage zurueck (ein Jahr plus Vorjahresfenster)
    const val VERLAUF_TAGE = 400
    // Rueckgang bzw. Zuwachs in Prozent, bei dem die Farbe voll deckt
    const val VOLL_PROZENT = 40.0
    // Ab diesem Rueckgang in Prozent gilt eine Durchfahrt als deutlich gestoert (Markierung in der Liste)
    const val STOERUNG_PROZENT = -20.0
    const val MIND_VORJAHR = 10.0

    // id: Kennung im Dienst; anrainer: ISO alpha-3 der Staaten am Engpass (fuer den Sanktionshinweis);
    // werte: Kuerzel im Katalog der App (nur vorhandene werden angezeigt)
    class Stelle(val id: String, val lat: Double, val lon: Double, val anrainer: List<String>, val werte: List<String>)

    // Reihenfolge nach Bedeutung fuer Anleger: erst die sieben grossen Engpaesse der Energie- und Containerrouten
    val STELLEN: List<Stelle> = listOf(
        Stelle("chokepoint6", 26.3, 56.9, listOf("IRN", "OMN", "ARE"), listOf("BZ=F", "CL=F", "NG=F", "FRO.OL", "HAFNI.OL", "BWLPG.OL", "QDVF.DE", "XOM", "SHEL.L", "DAL", "UAL", "LHA.DE", "EXV7.DE")),
        Stelle("chokepoint4", 12.8, 43.3, listOf("YEM", "DJI", "ERI"), listOf("MAERSK-B.CO", "HLAG.DE", "DSV.CO", "KNIN.SW", "9101.T", "9104.T", "9107.T", "BZ=F", "FRO.OL")),
        Stelle("chokepoint1", 30.6, 32.4, listOf("EGY"), listOf("MAERSK-B.CO", "HLAG.DE", "DSV.CO", "KNIN.SW", "9101.T", "9104.T", "9107.T", "BZ=F", "NG=F")),
        Stelle("chokepoint2", 9.1, -79.8, listOf("PAN"), listOf("MAERSK-B.CO", "HLAG.DE", "FDX", "UPS", "ZW=F", "ZC=F", "NG=F", "WAWI.OL", "HAUTO.OL")),
        Stelle("chokepoint5", 1.5, 102.7, listOf("MYS", "IDN", "SGP"), listOf("BZ=F", "MAERSK-B.CO", "HLAG.DE", "FRO.OL", "HAFNI.OL", "9101.T", "9104.T", "SB=F", "EXV7.DE")),
        Stelle("chokepoint11", 24.7, 119.8, listOf("TWN", "CHN"), listOf("TSM", "VVSM.DE", "SEC0.DE", "NVDA", "005930.KS", "MAERSK-B.CO", "HLAG.DE", "DFEN.DE")),
        Stelle("chokepoint3", 41.2, 29.1, listOf("TUR"), listOf("ZW=F", "ZC=F", "BZ=F", "ADM", "BG", "YAR.OL", "CF")),
        Stelle("chokepoint8", 35.9, -5.8, listOf("ESP", "MAR"), listOf("MAERSK-B.CO", "HLAG.DE", "DSV.CO", "BZ=F", "NG=F")),
        Stelle("chokepoint7", -34.9, 20.9, listOf("ZAF"), listOf("MAERSK-B.CO", "HLAG.DE", "BZ=F", "FRO.OL", "9101.T")),
        Stelle("chokepoint9", 51.0, 1.5, listOf("GBR", "FRA"), listOf("MAERSK-B.CO", "HLAG.DE", "DSV.CO", "DHL.DE", "KNIN.SW")),
        Stelle("chokepoint10", 55.5, 12.9, listOf("DNK", "SWE"), listOf("MAERSK-B.CO", "BZ=F", "DSV.CO")),
        Stelle("chokepoint28", 45.3, 36.5, listOf("RUS", "UKR"), listOf("ZW=F", "ZC=F", "BZ=F", "ADM", "BG")),
        Stelle("chokepoint12", 34.1, 129.2, listOf("KOR", "JPN"), listOf("005930.KS", "9101.T", "9104.T", "9107.T", "HLAG.DE", "TSM")),
        Stelle("chokepoint14", 20.5, 121.4, listOf("PHL", "TWN"), listOf("TSM", "VVSM.DE", "SEC0.DE", "MAERSK-B.CO", "HLAG.DE")),
        Stelle("chokepoint17", 38.4, 120.9, listOf("CHN"), listOf("BZ=F", "MAERSK-B.CO", "HLAG.DE")),
        Stelle("chokepoint13", 41.3, 140.4, listOf("JPN"), listOf("9101.T", "9104.T", "9107.T")),
        Stelle("chokepoint15", -8.4, 115.8, listOf("IDN"), listOf("BZ=F", "9101.T", "9104.T", "FRO.OL")),
        Stelle("chokepoint19", -6.0, 105.8, listOf("IDN"), listOf("BZ=F", "9101.T", "9104.T")),
        Stelle("chokepoint20", 0.4, 119.3, listOf("IDN"), listOf("BZ=F", "9101.T", "9104.T")),
        Stelle("chokepoint16", -8.4, 125.1, listOf("IDN", "TLS"), listOf("9101.T", "9104.T")),
        Stelle("chokepoint25", 7.4, 117.1, listOf("PHL", "MYS"), listOf("9101.T", "9104.T")),
        Stelle("chokepoint27", 12.5, 120.4, listOf("PHL"), listOf("9101.T", "9104.T")),
        Stelle("chokepoint18", -9.9, 142.2, listOf("AUS", "PNG"), listOf("9101.T", "9104.T")),
        Stelle("chokepoint22", 21.8, -85.6, listOf("CUB", "MEX"), listOf("BZ=F", "XOM", "NG=F")),
        Stelle("chokepoint23", 20.0, -73.7, listOf("CUB", "HTI"), listOf("MAERSK-B.CO", "HLAG.DE")),
        Stelle("chokepoint24", 18.4, -67.7, listOf("DOM", "USA"), listOf("MAERSK-B.CO", "HLAG.DE")),
        Stelle("chokepoint21", -52.6, -69.6, listOf("CHL", "ARG"), listOf("9101.T")),
        Stelle("chokepoint26", 66.0, -165.5, listOf("RUS", "USA"), listOf("9101.T")),
    )

    private val NACH_ID = STELLEN.associateBy { it.id }
    fun stelle(id: String): Stelle? = NACH_ID[id]

    // Ein Tag eines Engpasses: Tag seit 1970, alle Schiffe, Tanker, Containerschiffe
    @Serializable
    class Tag(val t: Int, val n: Int, val k: Int, val c: Int)

    // Eine Zeile der Antwort mit der Kennung des Engpasses
    class Zeile(val id: String, val tag: Tag)

    // Abruf-Adressen. Datumsfelder des Dienstes sind reine Daten (DateOnly), die Abfrage nimmt Text mit Bindestrichen.
    // cacheHint=true laesst den Dienst gleiche Abfragen aus seinem Zwischenspeicher beantworten; ohne den Zusatz antwortet er
    // oft mit „Too many requests“ (Mengenbegrenzung je Minute fuer alle Nutzer).
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun adresse(wo: String, sortiert: Boolean): String =
        DIENST + "?where=" + enc(wo) + "&outFields=" + enc("portid,date,n_total,n_tanker,n_container") +
            (if (sortiert) "&orderByFields=" + enc("date DESC") else "") + "&returnGeometry=false&cacheHint=true&f=json"

    // Letzte Tage aller Engpaesse (liefert den juengsten Tag)
    fun adresseNeu(heute: LocalDate): String = adresse("date >= '" + heute.minusDays(21) + "'", true)

    // Vorjahresfenster um den Tag `letzter` (Tage seit 1970)
    fun adresseVorjahr(letzter: Int): String {
        val mitte = LocalDate.ofEpochDay(letzter.toLong()).minusDays(365)
        return adresse("date >= '" + mitte.minusDays(VORJAHR_HALB.toLong()) + "' AND date <= '" + mitte.plusDays(VORJAHR_HALB.toLong()) + "'", false)
    }

    // Ein Jahr eines Engpasses
    fun adresseVerlauf(id: String, heute: LocalDate): String =
        adresse("portid = '" + id.replace("'", "") + "' AND date >= '" + heute.minusDays(VERLAUF_TAGE.toLong()) + "'", false)

    // Antwort des Dienstes. null bei Fehlern (auch die Meldung „Too many requests“ steckt in einem Objekt „error“).
    fun lesen(text: String): List<Zeile>? = runCatching {
        val w = Laenderrisiken.json.parseToJsonElement(text).jsonObject
        if ("error" in w) return null
        val liste = w["features"] as? JsonArray ?: return null
        val aus = ArrayList<Zeile>(liste.size)
        for (f in liste) {
            val a = (f as? JsonObject)?.get("attributes") as? JsonObject ?: continue
            val id = a["portid"]?.jsonPrimitive?.contentOrNull ?: continue
            val d = a["date"]?.jsonPrimitive ?: continue
            val tag = if (d.isString) LocalDate.parse(d.content.take(10)).toEpochDay() else Math.floorDiv(d.content.toLong(), 86_400_000L)
            val n = a["n_total"]?.jsonPrimitive?.doubleOrNull ?: continue
            aus += Zeile(id, Tag(tag.toInt(), n.toInt(), (a["n_tanker"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toInt(), (a["n_container"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toInt()))
        }
        if (aus.isEmpty()) null else aus
    }.getOrNull()

    // Schnitt je Tag und Vergleichswert im Vorjahr; abw in Prozent (null ohne Vorjahreswert)
    @Serializable
    class Lage(val n: Double, val k: Double, val c: Double, val vn: Double?, val vk: Double?, val vc: Double?) {
        val abw: Double? get() = vn?.takeIf { it > 0.0 }?.let { (n / it - 1.0) * 100.0 }
    }

    // tag: juengster Tag der Daten (seit 1970)
    @Serializable
    class Uebersicht(val abgerufen: Long, val tag: Int, val lage: Map<String, Lage>)

    private fun mittel(tage: List<Tag>, von: Int, bis: Int, mind: Int, f: (Tag) -> Int): Double? {
        val x = tage.filter { it.t in von..bis }
        return if (x.size < mind) null else x.sumOf { f(it) }.toDouble() / x.size
    }

    // Lage eines Engpasses aus seinen Tagen (aufsteigend oder nicht) zum Stichtag `letzter`
    fun lage(tage: List<Tag>, letzter: Int): Lage? {
        val von = letzter - TAGE + 1
        val n = mittel(tage, von, letzter, TAGE - 3) { it.n } ?: return null
        val vl = letzter - 365
        fun v(f: (Tag) -> Int) = mittel(tage, vl - VORJAHR_HALB, vl + VORJAHR_HALB, 14, f)
        return Lage(n, mittel(tage, von, letzter, TAGE - 3) { it.k } ?: 0.0, mittel(tage, von, letzter, TAGE - 3) { it.c } ?: 0.0, v { it.n }, v { it.k }, v { it.c })
    }

    // Aus Abruf „neu“ (letzte Tage) und Abruf „Vorjahr“ die Lage aller Engpaesse
    fun uebersicht(neu: List<Zeile>, vorjahr: List<Zeile>?, jetzt: Long): Uebersicht? {
        val letzter = neu.maxOfOrNull { it.tag.t } ?: return null
        val je = HashMap<String, MutableList<Tag>>()
        for (z in neu) je.getOrPut(z.id) { ArrayList() } += z.tag
        vorjahr?.forEach { z -> je.getOrPut(z.id) { ArrayList() } += z.tag }
        val aus = HashMap<String, Lage>()
        for ((id, tage) in je) lage(tage, letzter)?.let { aus[id] = it }
        return if (aus.isEmpty()) null else Uebersicht(jetzt, letzter, aus)
    }

    // Ein Engpass ueber ein Jahr: Tage aufsteigend
    @Serializable
    class Verlauf(val abgerufen: Long, val tage: List<Tag>)

    fun verlauf(zeilen: List<Zeile>, id: String, jetzt: Long): Verlauf? {
        val t = zeilen.filter { it.id == id }.map { it.tag }.distinctBy { it.t }.sortedBy { it.t }
        return if (t.size < 30) null else Verlauf(jetzt, t)
    }

    // Gleitender Schnitt ueber 7 Tage fuer die Linie des Verlaufs, hoechstens `tage` letzte Tage: Paare (Tag, Wert).
    fun glatt(tage: List<Tag>, tageZurueck: Int = 365): List<Pair<Int, Double>> {
        if (tage.isEmpty()) return emptyList()
        val letzter = tage.last().t
        val aus = ArrayList<Pair<Int, Double>>()
        var summe = 0
        for (i in tage.indices) {
            summe += tage[i].n
            if (i >= TAGE) summe -= tage[i - TAGE].n
            if (i >= TAGE - 1 && tage[i].t >= letzter - tageZurueck) aus += tage[i].t to summe.toDouble() / TAGE
        }
        return aus
    }

    // Rangfolge nach Abweichung, groesster Rueckgang zuerst; Engpaesse ohne Vorjahreswert am Ende
    fun sortiert(u: Uebersicht): List<Pair<Stelle, Lage>> =
        STELLEN.mapNotNull { s -> u.lage[s.id]?.let { s to it } }
            .sortedBy { it.second.abw ?: Double.MAX_VALUE }

    // Der Engpass mit der schwaechsten Entwicklung gegen das Vorjahr. Nur Engpaesse mit mindestens 10 Durchfahrten pro Tag
    // im Vorjahr: bei wenigen Schiffen schwankt der Schnitt zu stark.
    fun schlechtester(u: Uebersicht): Pair<Stelle, Lage>? =
        sortiert(u).firstOrNull { it.second.abw != null && (it.second.vn ?: 0.0) >= MIND_VORJAHR }

    // Gestoerte Engpaesse, staerkster Rueckgang zuerst: Rueckgang ab STOERUNG_PROZENT bei mindestens MIND_VORJAHR
    // Durchfahrten pro Tag im Vorjahr. Eine Regel fuer Lage, Ausblick und Meldungen.
    fun gestoert(l: Lage): Boolean = (l.abw ?: 0.0) <= STOERUNG_PROZENT && (l.vn ?: 0.0) >= MIND_VORJAHR

    fun gestoerte(u: Uebersicht): List<Pair<Stelle, Lage>> = sortiert(u).filter { gestoert(it.second) }

    // Ab dieser Abweichung in Prozent (Betrag) gilt ein Engpass in der Uebersicht als auffaellig
    const val AUFFAELLIG_PROZENT = 10.0

    // Die auffaelligsten Engpaesse fuer die Uebersicht im Ausblick: groesste Abweichung gegen das Vorjahr (Rueckgang wie
    // Zuwachs), nur mit Vorjahreswert und mindestens MIND_VORJAHR Durchfahrten am Tag. Es kommen alle ab AUFFAELLIG_PROZENT,
    // hoechstens `hoechstens`; gibt es weniger als `mindestens`, werden die naechstgroessten Abweichungen aufgefuellt.
    fun auffaellige(u: Uebersicht, hoechstens: Int = 5, mindestens: Int = 3): List<Pair<Stelle, Lage>> {
        val tauglich = STELLEN.mapNotNull { s -> u.lage[s.id]?.let { s to it } }
            .filter { (_, l) -> l.abw != null && (l.vn ?: 0.0) >= MIND_VORJAHR }
            .sortedByDescending { kotlin.math.abs(it.second.abw ?: 0.0) }
        val stark = tauglich.takeWhile { kotlin.math.abs(it.second.abw ?: 0.0) >= AUFFAELLIG_PROZENT }
        val n = stark.size.coerceIn(minOf(mindestens, tauglich.size), hoechstens)
        return tauglich.take(n)
    }
}
