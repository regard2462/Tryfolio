package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

// Waehrung und Kaufkraft [0.78]: Preisniveau und Kaufkraftparitaet je Land aus den Reihen des IWF, die die App ohnehin laedt
// (BIP je Kopf in Dollar und kaufkraftbereinigt). Das Verhaeltnis beider ist das Preisniveau gegen die USA: Wer je Kopf
// 30.000 Dollar erwirtschaftet und damit so viel kaufen kann wie 60.000 Dollar in den USA, lebt bei halben Preisen.
object Preisniveau {
    const val USA = "USA"
    const val DEUTSCHLAND = "DEU"

    // Preisniveau gegen die USA (1 = wie in den USA); null ohne beide Werte oder wenn Ersatzwerte aus verschiedenen Jahren stammen
    fun niveauUsa(d: Imf.Datensatz, a3: String, jahr: Int): Double? {
        val n = Imf.wert(d, Imf.BIP_KOPF, a3, jahr) ?: return null
        val k = Imf.wert(d, Imf.BIP_KOPF_KKP, a3, jahr) ?: return null
        if (Imf.herkunft(d, Imf.BIP_KOPF, a3, jahr)?.jahr != Imf.herkunft(d, Imf.BIP_KOPF_KKP, a3, jahr)?.jahr) return null
        return if (n > 0 && k > 0 && n.isFinite() && k.isFinite()) n / k else null
    }

    // Preisniveau des Landes in Prozent des Niveaus im Basisland (100 = gleich teuer)
    fun preisniveau(d: Imf.Datensatz, land: String, basis: String, jahr: Int): Double? {
        val l = niveauUsa(d, land, jahr) ?: return null
        val b = niveauUsa(d, basis, jahr) ?: return null
        return l / b * 100.0
    }

    // Abweichung des Wechselkurses von der Kaufkraftparitaet in Prozent: negativ = Waehrung kauft im Land mehr, als ihr Kurs sagt
    fun bewertung(niveauProzent: Double): Double = niveauProzent - 100.0

    // Verlauf des Preisniveaus gegen das Basisland, nur Jahre mit Werten des IWF fuer beide Laender
    fun verlauf(d: Imf.Datensatz, land: String, basis: String, von: Int, bis: Int): List<Pair<Int, Double>> =
        (von..bis).mapNotNull { j ->
            fun n(a3: String): Double? {
                val x = d.werte[Imf.BIP_KOPF]?.get(a3)?.get(j) ?: return null
                val y = d.werte[Imf.BIP_KOPF_KKP]?.get(a3)?.get(j) ?: return null
                return if (x > 0 && y > 0) x / y else null
            }
            val l = n(land) ?: return@mapNotNull null
            val b = n(basis) ?: return@mapNotNull null
            j to l / b * 100.0
        }

    // Was im Basisland `betrag` kostet, kostet im Land so viel
    fun kostetDort(betrag: Double, niveauProzent: Double): Double = betrag * niveauProzent / 100.0

    // `betrag` aus dem Basisland reicht im Land so weit wie dieser Betrag zu Hause
    fun reichtWie(betrag: Double, niveauProzent: Double): Double = betrag * 100.0 / niveauProzent

    // BIP je Kopf in Dollar: zum Wechselkurs und in Preisen des Basislandes (was die Wirtschaftsleistung je Kopf zu Hause kaufen wuerde)
    class Einkommen(val nominal: Double, val kaufkraft: Double)

    fun einkommen(d: Imf.Datensatz, land: String, basis: String, jahr: Int): Einkommen? {
        val n = Imf.wert(d, Imf.BIP_KOPF, land, jahr) ?: return null
        val niveau = preisniveau(d, land, basis, jahr) ?: return null
        return Einkommen(n, n * 100.0 / niveau)
    }

    // Basisland: das Land des Nutzers, wenn der Datensatz sein Preisniveau kennt, sonst Deutschland
    fun basis(d: Imf.Datensatz, nutzerA3: String?, jahr: Int): String =
        nutzerA3?.takeIf { niveauUsa(d, it, jahr) != null } ?: DEUTSCHLAND
}

// Wechselkurs im Jahresschnitt (Landeswaehrung je US-Dollar) und realer effektiver Wechselkurs (2010 = 100) je Land und Jahr aus
// globus/waehrung.json (tools/daten/kaufkraft.py, Weltbank). Staerke heisst hier: Wert der Waehrung gemessen in einer anderen.
object Waehrungsstaerke {
    const val DATEI = "globus/waehrung.json"

    class Land(val kurs: List<Double?>, val reer: List<Double?>)

    class Datei(val stand: String, val ab: Int, val bis: Int, val laender: Map<String, Land>)

    private fun reihe(e: Any?, n: Int): List<Double?>? {
        val a = e as? JsonArray ?: return List(n) { null }
        if (a.size != n) return null
        return a.map { if (it is JsonNull) null else (it as? JsonPrimitive)?.doubleOrNull?.takeIf { v -> v > 0 && v.isFinite() } }
    }

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val ab = (w["ab"] as? JsonPrimitive)?.intOrNull ?: return null
        val bis = (w["bis"] as? JsonPrimitive)?.intOrNull ?: return null
        val n = bis - ab + 1
        if (n < 2) return null
        val aus = LinkedHashMap<String, Land>()
        for ((k, v) in w["l"] as? JsonObject ?: return null) {
            val o = v as? JsonObject ?: return null
            aus[k] = Land(reihe(o["f"], n) ?: return null, reihe(o["r"], n) ?: return null)
        }
        if (aus.isEmpty()) null else Datei(stand, ab, bis, aus)
    }.getOrNull()

    fun kurs(d: Datei, a3: String, jahr: Int): Double? = d.laender[a3]?.kurs?.getOrNull(jahr - d.ab)

    // Wert einer Einheit der Waehrung von land, gemessen in der Waehrung von gegen
    fun wert(d: Datei, land: String, gegen: String, jahr: Int): Double? {
        val l = kurs(d, land, jahr) ?: return null
        val g = kurs(d, gegen, jahr) ?: return null
        return g / l
    }

    // Letztes Jahr, in dem beide Laender einen Kurs haben
    fun letztesJahr(d: Datei, land: String, gegen: String): Int? = (d.bis downTo d.ab).firstOrNull { wert(d, land, gegen, it) != null }

    // Eine Aufwertung zum Dollar um mehr als 150 % in einem Jahr macht kein Markt: Dahinter steht eine neue Waehrung oder
    // gestrichene Nullen. Ueber so einen Bruch hinweg wird nichts gerechnet.
    const val BRUCH = 0.4

    private fun bruch(d: Datei, a3: String, von: Int, bis: Int): Boolean = (von + 1..bis).any { j ->
        val a = kurs(d, a3, j - 1) ?: return@any true
        val b = kurs(d, a3, j) ?: return@any true
        b / a < BRUCH
    }

    // Veraenderung des Werts der Waehrung von land gegen die von gegen ueber `jahre` Jahre bis `bis`, in Prozent
    fun nominal(d: Datei, land: String, gegen: String, bis: Int, jahre: Int): Double? {
        val von = bis - jahre
        if (von < d.ab || bruch(d, land, von, bis) || bruch(d, gegen, von, bis)) return null
        val a = wert(d, land, gegen, von) ?: return null
        val b = wert(d, land, gegen, bis) ?: return null
        return (b / a - 1.0) * 100.0
    }

    // Real: nominale Veraenderung mal dem Verhaeltnis der Preisanstiege (Inflation im Land gegen Inflation im Vergleichsland,
    // je Jahr in Prozent). Steigen die Preise im Land schneller, wertet die Waehrung real auf, auch wenn ihr Kurs steht.
    fun real(nominalProzent: Double, inflationLand: List<Double>, inflationGegen: List<Double>): Double {
        var f = 1.0 + nominalProzent / 100.0
        for (i in inflationLand) f *= 1.0 + i / 100.0
        for (i in inflationGegen) f /= 1.0 + i / 100.0
        return (f - 1.0) * 100.0
    }

    // Inflation der Jahre von+1 bis bis aus dem IWF-Datensatz; null, wenn ein Jahr fehlt
    fun inflation(imf: Imf.Datensatz, a3: String, von: Int, bis: Int): List<Double>? {
        val r = imf.werte[Imf.INFLATION]?.get(a3) ?: return null
        return (von + 1..bis).map { r[it]?.takeIf { v -> v > -100.0 } ?: return null }
    }

    class Zeile(val jahre: Int, val nominal: Double, val real: Double?)

    val SPANNEN = listOf(1, 5, 10)

    fun zeilen(d: Datei, imf: Imf.Datensatz?, land: String, gegen: String): List<Zeile> {
        val bis = letztesJahr(d, land, gegen) ?: return emptyList()
        return SPANNEN.mapNotNull { n ->
            val nom = nominal(d, land, gegen, bis, n) ?: return@mapNotNull null
            val il = imf?.let { inflation(it, land, bis - n, bis) }
            val ig = imf?.let { inflation(it, gegen, bis - n, bis) }
            Zeile(n, nom, if (il != null && ig != null) real(nom, il, ig) else null)
        }
    }

    // Vergleichsland fuer die Staerke: das Basisland; teilen sich beide die Waehrung, die USA, und fuer Dollarlaender Deutschland
    fun gegen(land: String, basis: String, waehrungLand: String?, waehrungBasis: String?): String = when {
        land != basis && (waehrungLand == null || waehrungLand != waehrungBasis) -> basis
        waehrungLand == "USD" || land == Preisniveau.USA -> Preisniveau.DEUTSCHLAND
        else -> Preisniveau.USA
    }

    // Der Jahresschnitt lag `jahre` Jahre lang in einem Band von hoechstens +-TOLERANZ um seine Mitte: Der Kurs ist gebunden.
    const val TOLERANZ = 0.015

    fun fest(d: Datei, land: String, gegen: String, bis: Int, jahre: Int = 10): Boolean {
        val w = (bis - jahre..bis).map { wert(d, land, gegen, it) ?: return false }
        val lo = w.min(); val hi = w.max()
        return hi / lo - 1.0 <= 2 * TOLERANZ
    }

    // Bindung, soweit sie aus den Kursen der letzten zehn Jahre folgt (keine amtliche Einstufung): Anker USA oder DEU (Euro)
    fun anker(d: Datei, land: String): String? {
        val bis = letztesJahr(d, land, Preisniveau.USA) ?: return null
        return listOf(Preisniveau.USA, Preisniveau.DEUTSCHLAND).firstOrNull { it != land && fest(d, land, it, bis) }
    }

    // Realer effektiver Wechselkurs: letzter Wert, sein Jahr, der Schnitt der zehn Jahre bis dahin und die Abweichung in Prozent
    class Reer(val wert: Double, val jahr: Int, val schnitt: Double, val abweichung: Double)

    fun reerVerlauf(d: Datei, land: String): List<Pair<Int, Double>> =
        d.laender[land]?.reer.orEmpty().mapIndexedNotNull { i, v -> v?.let { (d.ab + i) to it } }

    fun reer(d: Datei, land: String): Reer? {
        val v = reerVerlauf(d, land)
        val l = v.lastOrNull() ?: return null
        val fenster = v.filter { it.first > l.first - 10 }
        if (fenster.size < 8) return null
        val schnitt = fenster.sumOf { it.second } / fenster.size
        return Reer(l.second, l.first, schnitt, (l.second / schnitt - 1.0) * 100.0)
    }
}

// Big-Mac-Index von The Economist aus globus/bigmac.json: Preis eines Big Mac je Land, in Dollar umgerechnet, und die
// Abweichung vom Preis im Vergleichsland, roh und um das BIP je Kopf bereinigt. Gut 50 Laender und der Euroraum.
object BigMac {
    const val DATEI = "globus/bigmac.json"
    const val EURORAUM = "EUZ"

    // Abweichungen in Prozent; rohDollar je Stichtag der Datei
    class Land(
        val waehrung: String, val tag: String, val preis: Double, val dollarPreis: Double, val rohDollar: List<Double?>,
        val rohEuro: Double?, val bereinigtDollar: Double?, val bereinigtEuro: Double?,
    )

    class Datei(val stand: String, val tage: List<String>, val laender: Map<String, Land>)

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val tage = (w["tage"] as? JsonArray)?.map { (it as JsonPrimitive).content.takeIf { t -> Alter.tag(t) != null } ?: return null } ?: return null
        val aus = LinkedHashMap<String, Land>()
        fun promille(o: JsonObject, k: String): Double? = (o[k] as? JsonPrimitive)?.doubleOrNull?.div(10.0)
        for ((k, v) in w["l"] as? JsonObject ?: return null) {
            val o = v as? JsonObject ?: return null
            val t = (o["t"] as? JsonPrimitive)?.intOrNull?.takeIf { it in tage.indices } ?: return null
            val roh = (o["ur"] as? JsonArray)?.map { if (it is JsonNull) null else (it as JsonPrimitive).doubleOrNull?.div(10.0) } ?: return null
            if (roh.size != tage.size) return null
            aus[k] = Land(
                (o["w"] as? JsonPrimitive)?.content ?: return null, tage[t],
                (o["lp"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 } ?: return null,
                (o["dp"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 } ?: return null,
                roh, promille(o, "er"), promille(o, "ua"), promille(o, "ea"),
            )
        }
        if (aus.isEmpty() || tage.isEmpty()) null else Datei(stand, tage, aus)
    }.getOrNull()

    // Eintrag des Landes; Laender mit dem Euro stehen in der Quelle gemeinsam als Euroraum (zweiter Wert: true)
    fun fuer(d: Datei, a3: String, waehrung: String?): Pair<Land, Boolean>? =
        d.laender[a3]?.let { it to false } ?: (if (waehrung == "EUR") d.laender[EURORAUM]?.let { it to true } else null)

    // Preis in Euro zum Kurs des Stichtags: Dollarpreis mal Europreis durch Dollarpreis im Euroraum am selben Stichtag
    fun euroPreis(d: Datei, l: Land): Double? {
        val e = d.laender[EURORAUM]?.takeIf { it.tag == l.tag } ?: return null
        return l.dollarPreis * e.preis / e.dollarPreis
    }

    // Stichtag "2026-07-01" als Jahr mit Bruchteil (2026,5)
    fun jahrBruch(tag: String): Double = tag.substring(0, 4).toInt() + (tag.substring(5, 7).toInt() - 1) / 12.0

    // Verlauf der rohen Abweichung zum Dollar in Prozent
    fun verlauf(d: Datei, l: Land): List<Pair<Double, Double>> =
        l.rohDollar.mapIndexedNotNull { i, v -> v?.let { jahrBruch(d.tage[i]) to it } }
}

// Laenderindizes aus globus/ranglisten.json: je Index und Land der juengste Wert mit Jahr, dazu der Verlauf des Index der
// menschlichen Entwicklung. Der Rang wird hier gezaehlt: Platz 1 hat den besten Wert.
object Ranglisten {
    const val DATEI = "globus/ranglisten.json"

    // hochGut: hoeher ist besser (bei Ungleichheit zaehlt der niedrigere Wert als Platz 1). stellen: Nachkommastellen der Anzeige.
    enum class Index(val kuerzel: String, val hochGut: Boolean, val stellen: Int) {
        HDI("hdi", true, 3), IHDI("ihdi", true, 3), GESCHLECHTER("gii", false, 3), GINI("gini", false, 1), LOGISTIK("lpi", true, 1),
        HUMANKAPITAL("hci", true, 2),
    }

    class Wert(val jahr: Int, val wert: Double)

    class Datei(val stand: String, val indizes: Map<Index, Map<String, Wert>>, val hdiAb: Int, val hdi: Map<String, List<Double?>>)

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val i = w["i"] as? JsonObject ?: return null
        val aus = LinkedHashMap<Index, Map<String, Wert>>()
        for (x in Index.entries) {
            val je = i[x.kuerzel] as? JsonObject ?: continue
            val werte = LinkedHashMap<String, Wert>()
            for ((land, v) in je) {
                val a = v as? JsonArray ?: return null
                werte[land] = Wert((a[0] as JsonPrimitive).intOrNull ?: return null, (a[1] as JsonPrimitive).doubleOrNull?.takeIf { it.isFinite() } ?: return null)
            }
            if (werte.isNotEmpty()) aus[x] = werte
        }
        val hdi = (w["hdi"] as? JsonObject).orEmpty().mapValues { (_, v) ->
            (v as JsonArray).map { if (it is JsonNull) null else (it as JsonPrimitive).doubleOrNull?.div(1000.0) }
        }
        if (aus.isEmpty()) null else Datei(stand, aus, (w["hdiAb"] as? JsonPrimitive)?.intOrNull ?: 0, hdi)
    }.getOrNull()

    // Werte, die mehr als so viele Jahre hinter dem juengsten Jahr des Index liegen, zaehlen nicht mehr
    const val MAX_ALTER = 10

    class Platz(val index: Index, val wert: Wert, val rang: Int, val von: Int)

    private fun gueltig(d: Datei, x: Index): Map<String, Wert> {
        val alle = d.indizes[x].orEmpty()
        val neu = alle.values.maxOfOrNull { it.jahr } ?: return emptyMap()
        return alle.filterValues { neu - it.jahr <= MAX_ALTER }
    }

    // Rang des Landes: 1 plus die Zahl der Laender mit besserem Wert (gleiche Werte teilen den Platz)
    fun platz(d: Datei, x: Index, a3: String): Platz? {
        val alle = gueltig(d, x)
        val w = alle[a3] ?: return null
        val besser = alle.values.count { if (x.hochGut) it.wert > w.wert + 1e-12 else it.wert < w.wert - 1e-12 }
        return Platz(x, w, besser + 1, alle.size)
    }

    fun plaetze(d: Datei, a3: String): List<Platz> = Index.entries.mapNotNull { platz(d, it, a3) }

    // Wert eines Index fuer die Einfaerbung des Globus
    fun wert(d: Datei, x: Index, a3: String): Double? = gueltig(d, x)[a3]?.wert

    fun hdiVerlauf(d: Datei, a3: String): List<Pair<Int, Double>> =
        d.hdi[a3].orEmpty().mapIndexedNotNull { i, v -> v?.let { (d.hdiAb + i) to it } }

    // Abstand zum besten und schlechtesten Wert: Anteil von 0 (letzter Platz) bis 1 (erster Platz) fuer den Balken der Zeile
    fun lage(p: Platz): Float = if (p.von <= 1) 1f else 1f - (p.rang - 1).toFloat() / (p.von - 1).toFloat()

}
