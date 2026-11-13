package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

// Fruehe Bitcoin-Kurse [0.78]: Tagesschlusskurse in US-Dollar ab Juli 2010 aus Coin Metrics Community Data (CC BY-NC 4.0),
// Datei indikatoren/btc_frueh.json (tools/daten/btc_frueh.py). Sie verlaengern die Kursgeschichte fuer Rueckrechnungen nach
// hinten. Der gewoehnliche Kursweg hat Vorrang: aus der Datei kommen nur Tage vor dessen erstem Kurs.
//
// Schnittstelle fuer Rueckrechnungen: verlaengern(symbol, kerzen, datei) liefert die Reihe und den Tag, ab dem der
// gewoehnliche Kursweg gilt (null, wenn nichts ergaenzt wurde).
object BtcFrueh {

    const val DATEI = "indikatoren/btc_frueh.json"
    const val SYMBOL = "BTC-USD"
    const val QUELLE_NAME = "Coin Metrics Community Data (CC BY-NC 4.0)"
    const val QUELLE_ADRESSE = "https://github.com/coinmetrics/data"
    private const val TAG_MS = 86_400_000L

    // kurse: Schlusskurs in US-Dollar je Tag ab `von` (Tage seit 1970), null bei Luecke
    class Datei(val stand: String, val von: Long, val kurse: List<Double?>, val lizenz: String) {
        val bis: Long get() = von + kurse.size - 1
    }

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val von = (w["von"] as? JsonPrimitive)?.content?.let { Alter.tag(it) } ?: return null
        val lizenz = (w["lizenz"] as? JsonPrimitive)?.content.orEmpty()
        val kurse = (w["w"] as? JsonArray)?.map { e ->
            if (e is JsonNull) null else ((e as? JsonPrimitive)?.longOrNull ?: return null).takeIf { it > 0 }?.let { it / 10000.0 }
        } ?: return null
        if (kurse.isEmpty() || kurse.first() == null) null else Datei(stand, von, kurse, lizenz)
    }.getOrNull()

    // uebernahme: erster Tag (seit 1970) aus dem gewoehnlichen Kursweg; ergaenzt: Zahl der vorangestellten Tage
    class Reihe(val kerzen: List<Candle>, val uebernahme: Long?, val ergaenzt: Int)

    // Stellt den Kerzen von BTC-USD die fruehen Tage voran. faktor rechnet US-Dollar in die Waehrung der Kerzen um
    // (1 bei Kerzen in US-Dollar; bei Euro zum heutigen Kurs derselbe feste Faktor wie fuer die uebrigen Kerzen).
    // Andere Symbole, leere Reihen und Reihen, die schon vor der Datei beginnen, bleiben unveraendert.
    fun verlaengern(symbol: String, kerzen: List<Candle>, datei: Datei?, faktor: Double = 1.0): Reihe {
        if (symbol != SYMBOL || datei == null || kerzen.isEmpty() || !faktor.isFinite() || faktor <= 0) return Reihe(kerzen, null, 0)
        val erster = Math.floorDiv(kerzen.first().t, TAG_MS)
        if (erster <= datei.von) return Reihe(kerzen, null, 0)
        val frueh = ArrayList<Candle>()
        for ((i, k) in datei.kurse.withIndex()) {
            val tag = datei.von + i
            if (tag >= erster) break
            val v = (k ?: continue) * faktor
            frueh += Candle(tag * TAG_MS, v, v, v, v)
        }
        if (frueh.isEmpty()) return Reihe(kerzen, null, 0)
        return Reihe(frueh + kerzen, erster, frueh.size)
    }

    // Faktor von US-Dollar in die Waehrung einer umgerechneten Reihe: Verhaeltnis der Schlusskurse am selben Tag (dem ersten
    // gemeinsamen). null, wenn kein Tag in beiden Reihen steht.
    fun faktor(inDollar: List<Candle>, umgerechnet: List<Candle>): Double? {
        val u = umgerechnet.firstOrNull() ?: return null
        val tag = Math.floorDiv(u.t, TAG_MS)
        val d = inDollar.firstOrNull { Math.floorDiv(it.t, TAG_MS) == tag } ?: return null
        return (u.close / d.close).takeIf { it.isFinite() && it > 0 }
    }
}
