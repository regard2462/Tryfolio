package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToLong

// Optionskette der Nasdaq als Ersatz (0.77): ausserhalb der US-Handelszeit fuehrt die erste Quelle offene Kontrakte,
// Geld und Brief zeitweise mit 0 und damit eine unbrauchbare implizite Volatilitaet. Die Nasdaq nennt dann den Stand
// des letzten Handelstags. Ein Abruf fuer alle Verfaelle der Kette, die Zeile fuehrt Call und Put eines Basispreises.
object NasdaqKette {

    class Zeile(val letzter: Double, val geld: Double, val brief: Double, val umsatz: Long, val offen: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val KONTRAKT = Regex("--(\\d{2})(\\d{2})(\\d{2})[cp](\\d{8})$")
    private const val TAG_S = 86_400L

    fun adresse(symbol: String, etf: Boolean, vonS: Long, bisS: Long): String =
        "https://api.nasdaq.com/api/quote/$symbol/option-chain?assetclass=${if (etf) "etf" else "stocks"}&limit=4000" +
            "&fromdate=${NasdaqGriechen.tagText(vonS)}&todate=${NasdaqGriechen.tagText(bisS)}&excode=oprac&callput=callput&money=all&type=all"

    // Tage seit 1970 eines Kalendertags, ohne java.time
    private fun tagNr(jahr: Int, monat: Int, tag: Int): Long {
        val y = (if (monat <= 2) jahr - 1 else jahr).toLong()
        val era = y.floorDiv(400L)
        val yoe = y - era * 400
        val doy = (153 * (monat + (if (monat > 2) -3 else 9)) + 2) / 5 + tag - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    private fun zahl(z: JsonObject, feld: String): Double =
        (z[feld] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.replace(",", "")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 0.0

    // Null bei unlesbarer Antwort, leer, wenn die Nasdaq den Wert in dieser Anlageklasse nicht fuehrt
    fun lesen(text: String): Map<NasdaqGriechen.Schluessel, Zeile>? {
        val wurzel = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val daten = wurzel["data"] as? JsonObject ?: return emptyMap()
        val zeilen = ((daten["table"] as? JsonObject)?.get("rows") as? JsonArray) ?: return emptyMap()
        val aus = HashMap<NasdaqGriechen.Schluessel, Zeile>()
        for (e in zeilen) {
            val z = e as? JsonObject ?: continue
            val url = (z["drillDownURL"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: continue
            val t = KONTRAKT.find(url) ?: continue
            val verfall = tagNr(2000 + t.groupValues[1].toInt(), t.groupValues[2].toInt(), t.groupValues[3].toInt()) * TAG_S
            val strike = t.groupValues[4].toLongOrNull()?.div(10) ?: continue
            for (call in listOf(true, false)) {
                val v = if (call) "c_" else "p_"
                aus[NasdaqGriechen.Schluessel(verfall, strike, call)] = Zeile(
                    zahl(z, v + "Last"), zahl(z, v + "Bid"), zahl(z, v + "Ask"), zahl(z, v + "Volume").toLong(), zahl(z, v + "Openinterest").toLong(),
                )
            }
        }
        return aus
    }

    // Braucht die Kette Ersatz? Von den gehandelten Kontrakten hat weniger als ein Drittel offene Kontrakte oder
    // weniger als ein Drittel Geld und Brief.
    fun luecke(kontrakte: List<Kontrakt>): Boolean {
        if (Optionsanalyse.ohneOffene(kontrakte)) return true
        val gehandelt = kontrakte.filter { it.umsatz > 0 }
        return gehandelt.isNotEmpty() && gehandelt.count { it.geld > 0 && it.brief > 0 } * 3 < gehandelt.size
    }

    // Fuellt, was der Kette fehlt: offene Kontrakte, Geld und Brief, letzter Kurs, Umsatz. Wo Geld und Brief von der
    // Nasdaq kommen, rechnet die implizite Volatilitaet aus ihrem Mittelkurs (0 = unbekannt).
    fun ergaenzen(kurs: Double, kontrakte: List<Kontrakt>, nasdaq: Map<NasdaqGriechen.Schluessel, Zeile>, jetztS: Long): List<Kontrakt> {
        if (nasdaq.isEmpty()) return kontrakte
        return kontrakte.map { k ->
            val n = nasdaq[NasdaqGriechen.Schluessel(k.verfall.floorDiv(TAG_S) * TAG_S, (k.strike * 100).roundToLong(), k.call)] ?: return@map k
            val ohneKurse = !(k.geld > 0 && k.brief > 0)
            val neu = k.copy(
                letzter = if (k.letzter > 0) k.letzter else n.letzter,
                geld = if (ohneKurse) n.geld else k.geld,
                brief = if (ohneKurse) n.brief else k.brief,
                umsatz = if (k.umsatz > 0) k.umsatz else n.umsatz,
                offen = if (k.offen > 0) k.offen else n.offen,
            )
            if (ohneKurse) neu.copy(iv = iv(kurs, neu, jetztS) ?: 0.0) else neu
        }
    }

    // Implizite Volatilitaet aus dem Mittelkurs nach Black-Scholes (Bisektion zwischen 1 und 500 Prozent); null ohne
    // Geld und Brief, am Verfallstag oder wenn der Mittelkurs ausserhalb der Spanne liegt (unter dem inneren Wert).
    fun iv(kurs: Double, k: Kontrakt, jetztS: Long): Double? {
        if (!(k.geld > 0 && k.brief > 0) || kurs <= 0 || k.strike <= 0) return null
        val preis = (k.geld + k.brief) / 2
        // Handelsschluss 16 Uhr New York, rund 21 Uhr UTC
        val jahre = (k.verfall + 21 * 3_600L - jetztS) / (365.0 * TAG_S)
        if (jahre < 1.0 / 365) return null
        fun wert(vol: Double) = Pricing.blackScholes(kurs, k.strike, jahre, vol, k.call).price
        var unten = 0.01
        var oben = 5.0
        if (wert(unten) > preis || wert(oben) < preis) return null
        repeat(40) {
            val mitte = (unten + oben) / 2
            if (wert(mitte) < preis) unten = mitte else oben = mitte
        }
        return (unten + oben) / 2
    }
}
