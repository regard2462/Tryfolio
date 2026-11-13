package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

// [0.78] Schuldentragfaehigkeit im Laenderblatt: vier Blicke auf die Last der Staatsschulden.
//   SCHULDEN_EINNAHMEN   Staatsschulden in Prozent der Staatseinnahmen eines Jahres
//   ZINS_EINNAHMEN       Zinsausgaben in Prozent der Einnahmen
//   ZINS_WACHSTUM        Zins auf die Schulden (Zinsausgaben durch Schulden) minus nominales Wachstum, in Prozentpunkten
//   RESERVEN             Waehrungsreserven in Prozent der Staatsschulden
//   KURZFRISTIG          kurzfristige Auslandsschulden in Prozent der Reserven (nur wo die Weltbank sie fuehrt)
// Einnahmen, Zinsausgaben, Wachstum und Reserven kommen aus der gebuendelten Datei (Weltbank, Zentralstaat), die
// Schuldenquote aus den Wirtschaftsdaten des Blatts (IWF, Gesamtstaat). In Bundesstaaten faellt das Verhaeltnis von Schulden
// zu Einnahmen dadurch hoeher aus, als es fuer den Gesamtstaat waere.
// Ampel: Zins gegen Wachstum nach dem Vorzeichen (liegt der Zins ueber dem Wachstum, steigt die Schuldenquote ohne
// Ueberschuss im Haushalt). Kurzfristige Auslandsschulden ueber den Reserven sind rot (Regel von Guidotti und Greenspan).
// Die uebrigen Kennzahlen haben keine belegte Schwelle; ihre Ampel zeigt das Drittel im Vergleich aller Laender.
object Schuldenlast {
    const val DATEI = "globus/schulden.json"

    class Wert(val jahr: Int, val wert: Double)

    // Alle Groessen in Prozent: einnahmen und reserven des BIP, zinsen der Einnahmen, wachstum zum Vorjahr,
    // kurzfristig der Reserven.
    class Land(val einnahmen: Wert?, val zinsen: Wert?, val wachstum: Wert?, val reserven: Wert?, val kurzfristig: Wert?)

    class Datei(val stand: String, val laender: Map<String, Land>)

    private fun wert(e: Any?): Wert? {
        val a = e as? JsonArray ?: return null
        val j = (a.getOrNull(0) as? JsonPrimitive)?.intOrNull ?: return null
        val v = (a.getOrNull(1) as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() } ?: return null
        return Wert(j, v)
    }

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val aus = LinkedHashMap<String, Land>()
        for ((k, v) in w["l"] as? JsonObject ?: return null) {
            val o = v as? JsonObject ?: return null
            aus[k] = Land(wert(o["e"])?.takeIf { it.wert > 0 }, wert(o["z"])?.takeIf { it.wert >= 0 }, wert(o["w"]), wert(o["r"])?.takeIf { it.wert >= 0 }, wert(o["k"])?.takeIf { it.wert >= 0 })
        }
        if (aus.isEmpty()) null else Datei(stand, aus)
    }.getOrNull()

    enum class Art { SCHULDEN_EINNAHMEN, ZINS_EINNAHMEN, ZINS_WACHSTUM, RESERVEN, KURZFRISTIG }

    // a, b: die beiden Groessen hinter dem Wert (Schulden und Einnahmen in % des BIP; Zins und Wachstum in %; Reserven und
    // Schulden in % des BIP), fuer die Erklaerung. jahr: Jahr der Weltbank-Werte.
    class Kennzahl(val art: Art, val wert: Double, val jahr: Int, val a: Double? = null, val b: Double? = null, val ampel: Fruehwarnung.Ampel? = null)

    // Guidotti-Greenspan: Reserven sollen die kurzfristigen Auslandsschulden decken
    const val SCHWELLE_KURZFRISTIG = 100.0
    // Ab so vielen Laendern mit Wert wird nach Dritteln eingeteilt
    const val MINDEST_VERGLEICH = 30

    // Kennzahlen eines Landes ohne Vergleich. schulden: Schuldenquote in % des BIP fuer ein Jahr, null ohne Wert.
    fun rechnen(l: Land, schulden: (Int) -> Double?): List<Kennzahl> {
        val aus = ArrayList<Kennzahl>()
        val e = l.einnahmen
        val s = e?.let { schulden(it.jahr) }?.takeIf { it > 0 }
        if (e != null && s != null) aus += Kennzahl(Art.SCHULDEN_EINNAHMEN, s / e.wert * 100, e.jahr, s, e.wert)
        l.zinsen?.let { z -> aus += Kennzahl(Art.ZINS_EINNAHMEN, z.wert, z.jahr) }
        val z = l.zinsen
        val w = l.wachstum
        if (e != null && z != null && w != null) {
            // Zinsausgaben in % des BIP durch Schulden in % des BIP, beides im Jahr der Zinsausgaben
            val sz = schulden(z.jahr)?.takeIf { it > 0 }
            if (sz != null) {
                val zins = z.wert * e.wert / 100 / sz * 100
                val d = zins - w.wert
                aus += Kennzahl(Art.ZINS_WACHSTUM, d, z.jahr, zins, w.wert, if (d > 0) Fruehwarnung.Ampel.ROT else Fruehwarnung.Ampel.GRUEN)
            }
        }
        l.reserven?.let { r ->
            val sr = schulden(r.jahr)?.takeIf { it > 0 } ?: return@let
            aus += Kennzahl(Art.RESERVEN, r.wert / sr * 100, r.jahr, r.wert, sr)
        }
        l.kurzfristig?.let { k ->
            aus += Kennzahl(Art.KURZFRISTIG, k.wert, k.jahr, ampel = if (k.wert > SCHWELLE_KURZFRISTIG) Fruehwarnung.Ampel.ROT else Fruehwarnung.Ampel.GRUEN)
        }
        return aus
    }

    // Bei diesen Kennzahlen ist ein hoher Wert die groessere Last; bei den Reserven die kleinere.
    private fun hochIstLast(a: Art) = a != Art.RESERVEN

    // Ampel nach Dritteln: das Drittel der Laender mit der kleinsten Last gruen, das mittlere gelb, das mit der groessten rot.
    fun drittel(wert: Double, alle: List<Double>, hochIstLast: Boolean): Fruehwarnung.Ampel? {
        if (alle.size < MINDEST_VERGLEICH) return null
        val leichter = alle.count { if (hochIstLast) it < wert else it > wert }
        val rang = leichter.toDouble() / alle.size
        return when {
            rang < 1.0 / 3 -> Fruehwarnung.Ampel.GRUEN
            rang < 2.0 / 3 -> Fruehwarnung.Ampel.GELB
            else -> Fruehwarnung.Ampel.ROT
        }
    }

    // Kennzahlen aller Laender, die Kennzahlen ohne feste Schwelle mit der Ampel aus dem Vergleich.
    // schulden: Schuldenquote eines Landes (alpha-3) in einem Jahr.
    fun tabelle(d: Datei, schulden: (String, Int) -> Double?): Map<String, List<Kennzahl>> {
        val roh = d.laender.mapValues { (a3, l) -> rechnen(l) { j -> schulden(a3, j) } }.filterValues { it.isNotEmpty() }
        val je = Art.entries.associateWith { a -> roh.values.mapNotNull { k -> k.firstOrNull { it.art == a }?.wert } }
        return roh.mapValues { (_, liste) ->
            liste.map { k -> if (k.ampel != null) k else Kennzahl(k.art, k.wert, k.jahr, k.a, k.b, drittel(k.wert, je[k.art].orEmpty(), hochIstLast(k.art))) }
        }
    }
}
