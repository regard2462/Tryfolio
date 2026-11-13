package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Verguetung der Fuehrung aus der Proxy-Meldung (DEF 14A) [0.77]: Die Tabelle „Pay versus Performance“ steht seit 2022
// als XBRL (Taxonomie ecd) in der Meldung und kommt ueber die Einzelabfrage je Begriff bei data.sec.gov
// (api/xbrl/companyconcept), rund 1 KB je Begriff statt der 5 MB grossen Gesamtdatei. Werte, die eine Firma wegen
// mehrerer Vorstandschefs im Jahr nach Personen aufteilt, liefert die Schnittstelle nicht; solche Jahre fehlen.
object Verguetung {
    const val CEO = "PeoTotalCompAmt"
    const val ZUGEFLOSSEN = "PeoActuallyPaidCompAmt"
    const val UEBRIGE = "NonPeoNeoAvgTotalCompAmt"
    const val RENDITE = "TotalShareholderRtnAmt"

    // Ein Wert der Reihe: Geschaeftsjahr von beginn bis ende (JJJJ-MM-TT), Meldung und Tag ihres Eingangs.
    data class Punkt(val beginn: String, val ende: String, val wert: Double, val accn: String, val form: String, val eingang: String)

    // Ein Geschaeftsjahr; rendite in Prozent (Aktie mit Dividenden), null ohne Angabe.
    data class Jahr(val jahr: Int, val ceo: Double?, val zugeflossen: Double?, val uebrige: Double?, val rendite: Double?)

    data class Ergebnis(val jahre: List<Jahr>) {
        val aktuell: Jahr get() = jahre.last()
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun adresse(cik: String, begriff: String): String =
        "https://data.sec.gov/api/xbrl/companyconcept/CIK" + cik.trimStart('0').padStart(10, '0') + "/ecd/$begriff.json"

    // Werte der Antwort (Einheit USD), leer bei Fehlern.
    fun reihe(text: String): List<Punkt> = runCatching {
        val einheiten = json.parseToJsonElement(text).jsonObject["units"]!!.jsonObject.values.first().jsonArray
        einheiten.mapNotNull { e ->
            val o = e.jsonObject
            Punkt(
                beginn = o["start"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                ende = o["end"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                wert = o["val"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null,
                accn = o["accn"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                form = o["form"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                eingang = o["filed"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    // Nur Geschaeftsjahre von etwa einem Jahr (Teiljahre und Quartalswerte fallen weg).
    private fun jahreslang(p: Punkt): Boolean = runCatching {
        if (p.beginn.isBlank()) return true
        val tage = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(p.beginn), java.time.LocalDate.parse(p.ende))
        tage in 340..380
    }.getOrDefault(false)

    private fun endgueltig(p: Punkt) = if (p.form.startsWith("DEF")) 1 else 0

    // Je Geschaeftsjahr (Ende) ein Wert: dieselbe Zahl steht in mehreren Meldungen, es gilt die endgueltige und juengste.
    fun jahresreihe(punkte: List<Punkt>): Map<String, Double> =
        punkte.filter(::jahreslang).groupBy { it.ende }
            .mapValues { (_, g) -> g.maxWith(compareBy<Punkt> { endgueltig(it) }.thenBy { it.eingang }).wert }

    // Rendite je Geschaeftsjahr in Prozent. Die Reihe ist der Wert von 100 $, die zu Beginn des ersten Jahres einer
    // Meldung angelegt wurden; sie gilt nur innerhalb einer Meldung. Es zaehlt die juengste endgueltige Meldung, das
    // erste Jahr rechnet gegen 100.
    fun renditen(punkte: List<Punkt>): Map<String, Double> {
        val gueltig = punkte.filter(::jahreslang)
        val meldung = gueltig.maxWithOrNull(compareBy<Punkt> { endgueltig(it) }.thenBy { it.eingang }.thenBy { it.accn })?.accn ?: return emptyMap()
        val reihe = gueltig.filter { it.accn == meldung }.sortedBy { it.ende }
        val aus = LinkedHashMap<String, Double>()
        var vorher = 100.0
        for (p in reihe) {
            if (vorher > 0 && p.wert >= 0) aus[p.ende] = (p.wert / vorher - 1) * 100
            vorher = p.wert
        }
        return aus
    }

    // Jahre mit Verguetung des Vorstandschefs, die letzten hoechstens fuenf, aelteste zuerst. Ohne CEO-Werte null.
    fun auswerten(ceo: List<Punkt>, zugeflossen: List<Punkt>, uebrige: List<Punkt>, rendite: List<Punkt>, hoechstens: Int = 5): Ergebnis? {
        val c = jahresreihe(ceo)
        if (c.isEmpty()) return null
        val z = jahresreihe(zugeflossen)
        val u = jahresreihe(uebrige)
        val r = renditen(rendite)
        val jahre = c.keys.sorted().takeLast(hoechstens).mapNotNull { ende ->
            val j = ende.take(4).toIntOrNull() ?: return@mapNotNull null
            Jahr(j, c[ende], z[ende], u[ende], r[ende])
        }
        return if (jahre.isEmpty()) null else Ergebnis(jahre)
    }
}
