package de.troetenrepublik.app.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// [Globus Gesamtbild 0.73] Rueckfallkette hinter den IWF-Reihen: Fuehrt der IWF eine Reihe fuer ein Land oder Gebiet nicht,
// gilt der juengste Wert 1. der Weltbank (Entwicklungsindikatoren, ein Sammelabruf je Kennzahl), 2. der Vereinten Nationen
// und 3. des CIA World Factbook. UN und Factbook stehen als kleine datierte Datei in assets/globus/ersatz.json:
// {"stand": "2026-11-09", "w": {"CUB": {"NGDP_RPCH": [2022, 1.8, "UN"], ...}, ...}}. Werte, die aelter als MAX_ALTER Jahre sind,
// zaehlen nicht. Das Ergebnis haengt als Datensatz.ersatz am IWF-Datensatz; alle Stellen lesen es ueber Imf.wert.
object ImfErsatz {
    const val DATEI = "globus/ersatz.json"

    // Kennung im IWF-Datensatz -> Kennung der Weltbank und Faktor auf die Einheit der IWF-Reihe
    // (BIP in Milliarden Dollar, Bevoelkerung in Millionen)
    val WELTBANK: Map<String, Pair<String, Double>> = linkedMapOf(
        Imf.WACHSTUM to ("NY.GDP.MKTP.KD.ZG" to 1.0),
        Imf.INFLATION to ("FP.CPI.TOTL.ZG" to 1.0),
        Imf.ARBEITSLOS to ("SL.UEM.TOTL.ZS" to 1.0),
        Imf.SCHULDEN to ("GC.DOD.TOTL.GD.ZS" to 1.0),
        Imf.HAUSHALT to ("GC.NLD.TOTL.GD.ZS" to 1.0),
        Imf.LEISTUNG to ("BN.CAB.XOKA.GD.ZS" to 1.0),
        Imf.BIP_KOPF_KKP to ("NY.GDP.PCAP.PP.CD" to 1.0),
        Imf.BIP_KOPF to ("NY.GDP.PCAP.CD" to 1.0),
        Imf.BIP to ("NY.GDP.MKTP.CD" to 1e-9),
        Imf.BEVOELKERUNG to ("SP.POP.TOTL" to 1e-6),
    )

    val WELTBANK_KENNUNGEN: List<String> get() = WELTBANK.values.map { it.first }

    // Land (alpha-3) -> Kennung -> Wert aus der gebuendelten Datei; leer bei fremdem Format
    class Datei(val stand: String, val werte: Map<String, Map<String, Imf.ErsatzWert>>)

    fun dateiLesen(text: String): Datei? = runCatching {
        val w = Laenderrisiken.json.parseToJsonElement(text).jsonObject
        val aus = (w["w"] as JsonObject).mapValues { (_, je) ->
            (je as JsonObject).mapValues { (_, a) ->
                val z: JsonArray = a.jsonArray
                Imf.ErsatzWert(z[0].jsonPrimitive.intOrNull!!, z[1].jsonPrimitive.doubleOrNull!!, z[2].jsonPrimitive.content)
            }
        }
        Datei(w["stand"]?.jsonPrimitive?.content.orEmpty(), aus)
    }.getOrNull()

    // Setzt Ersatzwerte auf den IWF-Datensatz: nur fuer Reihen, die der IWF fuer das Land nicht oder nur aus laengst vergangenen
    // Jahren fuehrt (kein Wert ab heute - MAX_ALTER), nur fuer Laender der
    // Karte (laender) und nur aus Jahren ab heute - MAX_ALTER. Die Weltbank geht vor, dann die Datei (UN vor Factbook steht dort).
    fun anwenden(d: Imf.Datensatz, laender: Set<String>, wb: Map<String, Weltbank.Reihe?>, datei: Datei?, heute: Int): Imf.Datensatz {
        val grenze = heute - Imf.MAX_ALTER
        val aus = LinkedHashMap<String, MutableMap<String, Imf.ErsatzWert>>()
        fun setze(kennung: String, land: String, e: Imf.ErsatzWert) {
            if (land !in laender || e.jahr < grenze || e.jahr > heute || !e.wert.isFinite()) return
            if (d.werte[kennung]?.get(land)?.keys?.any { it >= grenze } == true) return
            aus.getOrPut(kennung) { LinkedHashMap() }.putIfAbsent(land, e)
        }
        for ((kennung, wbk) in WELTBANK) {
            val reihe = wb[wbk.first] ?: continue
            for ((land, w) in reihe.werte) setze(kennung, land, Imf.ErsatzWert(w.jahr, w.wert * wbk.second, "WB"))
        }
        datei?.werte?.forEach { (land, je) -> je.forEach { (kennung, e) -> setze(kennung, land, e) } }
        return d.copy(ersatz = aus)
    }
}
