package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

// Rezessionswahrscheinlichkeit der New York Fed aus der Zinskurve (Rendite 10 Jahre minus 3 Monate), monatlich ab
// 1959, mit der NBER-Spalte derselben Datei (Monate einer US-Rezession nach der Datierung des NBER). Mitgeliefert als
// assets/indikatoren/rezession_nyfed.json (tools/historie/holen.py rezession), beim Nutzer aus allmonth.xls erneuerbar.
// Die Wahrscheinlichkeit einer Zeile gilt fuer ihren Monat und stammt aus der Zinskurve zwoelf Monate davor: Der Wert,
// den man im Monat k sah, steht in Zeile k + 12. Monate als Zahl jahr * 12 + monat - 1.
object RezessionNyFed {

    const val DATEI = "indikatoren/rezession_nyfed.json"
    const val ADRESSE = "https://www.newyorkfed.org/medialibrary/media/research/capital_markets/allmonth.xls"
    const val SEITE = "https://www.newyorkfed.org/research/capital_markets/ycfaq"
    const val BEDINGUNGEN = "https://www.newyorkfed.org/privacy/termsofuse"

    // Vorlauf des Modells in Monaten
    const val VORLAUF = 12

    // Schwelle der Trefferbilanz in Prozent, Fenster vor dem Beginn einer Rezession (Monate, in denen man den Wert sah) und
    // Pause, bis zu der zwei Phasen ueber der Schwelle als eine gelten
    const val SCHWELLE = 30.0
    const val FENSTER = 12
    const val RUHE = 6

    class Reihe(
        // erster Monat von p
        val von: Int,
        // Wahrscheinlichkeit in Prozent je Monat ab von, null ohne Wert
        val p: List<Double?>,
        // Rezessionsmonate als Spannen (erster, letzter Monat)
        val nber: List<Pair<Int, Int>>,
        // letzter Monat mit bekannter NBER-Spalte und letzter Monat mit Zinskurve
        val bisNber: Int,
        val stand: Int,
    ) {
        fun wert(m: Int): Double? = p.getOrNull(m - von)
        val bis: Int get() = von + p.size - 1
    }

    fun monat(jahr: Int, monat: Int) = jahr * 12 + monat - 1
    fun jahr(m: Int) = m.floorDiv(12)
    fun monatImJahr(m: Int) = m.mod(12) + 1

    // "JJJJ-MM" als Monatszahl
    fun monat(text: String): Int? {
        val t = text.trim()
        if (t.length < 7 || t[4] != '-') return null
        val j = t.substring(0, 4).toIntOrNull() ?: return null
        val mo = t.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return monat(j, mo)
    }

    // Erster Tag des Monats in Millisekunden (UTC)
    fun ms(m: Int): Long = java.time.LocalDate.of(jahr(m), monatImJahr(m), 1).toEpochDay() * 86_400_000L

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun lesen(text: String): Reihe? = runCatching {
        val o = Json.parseToJsonElement(text) as? JsonObject ?: return null
        val von = o.text("von")?.let(::monat) ?: return null
        val p = (o["p"] as? JsonArray ?: return null).map { e -> if (e is JsonNull) null else (e as? JsonPrimitive)?.doubleOrNull }
        val nber = (o["nber"] as? JsonArray).orEmpty().mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            val x = (a.getOrNull(0) as? JsonPrimitive)?.content?.let(::monat) ?: return@mapNotNull null
            val y = (a.getOrNull(1) as? JsonPrimitive)?.content?.let(::monat) ?: return@mapNotNull null
            if (y < x) null else x to y
        }
        val bisNber = o.text("bisNber")?.let(::monat) ?: return null
        val stand = o.text("stand")?.let(::monat) ?: return null
        if (p.none { it != null }) return null
        Reihe(von, p, nber, bisNber, stand)
    }.getOrNull()

    // Aus den Zeilen von allmonth.xls (XlsLeser.tabelle mit sechs Spalten): Datum (Monatsende als Tag seit 1970), dann
    // 10 Jahre, 3 Monate, 3 Monate als Anleiherendite, Spread, Rec_prob (Anteil), NBER_Rec (0 oder 1). Fehlende Zellen NaN.
    fun ausTabelle(zeilen: List<Pair<Long, DoubleArray>>): Reihe? {
        if (zeilen.size < 24) return null
        val je = zeilen.associate { (tag, w) ->
            val d = java.time.LocalDate.ofEpochDay(tag)
            monat(d.year, d.monthValue) to w
        }
        val von = je.keys.min()
        val bis = je.keys.max()
        if (bis - von + 1 != je.size) return null
        fun zahl(w: DoubleArray?, i: Int) = w?.getOrNull(i)?.takeIf { it.isFinite() }
        val p = (von..bis).map { m -> zahl(je[m], 4)?.let { Math.round(it * 1000) / 10.0 } }
        val n = (von..bis).map { m -> zahl(je[m], 5) }
        val bisNber = (von..bis).lastOrNull { n[it - von] != null } ?: return null
        val stand = (von..bis).lastOrNull { zahl(je[it], 3) != null } ?: return null
        val spannen = ArrayList<Pair<Int, Int>>()
        var an = -1
        for (m in von..bisNber) {
            val r = n[m - von] == 1.0
            if (r && an < 0) an = m
            if (!r && an >= 0) { spannen += an to m - 1; an = -1 }
        }
        if (an >= 0) spannen += an to bisNber
        if (p.none { it != null }) return null
        return Reihe(von, p, spannen, bisNber, stand)
    }

    // Dieselbe Form wie die mitgelieferte Datei (fuer den Stand auf dem Geraet)
    fun schreiben(r: Reihe): String {
        fun t(m: Int) = "\"%04d-%02d\"".format(jahr(m), monatImJahr(m))
        val p = r.p.joinToString(",") { it?.toString() ?: "null" }
        val n = r.nber.joinToString(",") { (a, b) -> "[" + t(a) + "," + t(b) + "]" }
        return "{\"stand\":${t(r.stand)},\"von\":${t(r.von)},\"bisNber\":${t(r.bisNber)},\"p\":[$p],\"nber\":[$n]}"
    }

    // Die neuere von zwei Reihen (spaeterer Stand der Zinskurve); bei Gleichstand die erste.
    fun neuere(a: Reihe?, b: Reihe?): Reihe? = when {
        a == null -> b
        b == null -> a
        b.stand > a.stand -> b
        else -> a
    }

    // Mitgelieferte Datei, einmal gelesen
    val datei: Reihe? by lazy { runCatching { lesen(de.troetenrepublik.app.market.Daten.text(DATEI)) }.getOrNull() }

    // ------------------------------------------------------------ Anzeige

    // Wahrscheinlichkeit, die heute gilt: der letzte Monat der Reihe (zwoelf Monate nach der letzten Zinskurve)
    fun aktuell(r: Reihe): Pair<Int, Double>? = (r.bis downTo r.von).firstNotNullOfOrNull { m -> r.wert(m)?.let { m to it } }

    // Verlauf so, wie man ihn sah: im Monat k der Wert fuer k + 12, als (Zeit in ms, Prozent)
    fun gesehen(r: Reihe): List<Pair<Long, Double>> = (r.von..r.bis - VORLAUF).mapNotNull { k -> r.wert(k + VORLAUF)?.let { ms(k) to it } }

    // Trefferbilanz: In wie vielen Rezessionen lag der gesehene Wert in den FENSTER Monaten vor dem Beginn mindestens
    // einmal ueber der Schwelle (nur Rezessionen, vor denen das ganze Fenster Werte hat), und wie viele Phasen ueber der
    // Schwelle blieben ohne Rezession (bis zu VORLAUF Monate nach ihrem Ende kein Rezessionsmonat; offen, solange die
    // NBER-Spalte so weit noch nicht reicht).
    data class Bilanz(val schwelle: Double, val treffer: Int, val von: Int, val fehlalarme: Int, val offen: Int, val abJahr: Int)

    fun bilanz(r: Reihe, schwelle: Double = SCHWELLE): Bilanz? {
        fun gesehen(k: Int) = r.wert(k + VORLAUF)
        var treffer = 0
        var gezaehlt = 0
        var erstes = Int.MAX_VALUE
        for ((beginn, _) in r.nber) {
            val werte = (beginn - FENSTER until beginn).map { gesehen(it) }
            if (werte.any { it == null }) continue
            gezaehlt++
            erstes = minOf(erstes, beginn)
            if (werte.any { it!! > schwelle }) treffer++
        }
        if (gezaehlt == 0) return null
        val rezession = HashSet<Int>()
        for ((a, b) in r.nber) for (m in a..b) rezession += m
        val phasen = ArrayList<IntArray>()
        for (k in r.von..r.bis - VORLAUF) {
            val v = gesehen(k) ?: continue
            if (v <= schwelle) continue
            val letzte = phasen.lastOrNull()
            if (letzte != null && k - letzte[1] <= RUHE) letzte[1] = k else phasen += intArrayOf(k, k)
        }
        var fehl = 0
        var offen = 0
        for (ph in phasen) {
            if ((ph[0]..ph[1] + VORLAUF).any { it in rezession }) continue
            if (ph[1] + VORLAUF <= r.bisNber) fehl++ else offen++
        }
        return Bilanz(schwelle, treffer, gezaehlt, fehl, offen, jahr(erstes))
    }

    // Stufe des Werts fuer Satz und Farbe: 0 unwahrscheinlich (bis zur Schwelle der Messung, 30 %), 1 moeglich (unter 40 %),
    // 2 wahrscheinlich. Die untere Grenze ist die Schwelle der Tendenz (NyFedMessung.lage), damit Farbe und Tendenz
    // zusammenpassen.
    fun stufe(p: Double): Int = when {
        p <= SCHWELLE -> 0
        p < 40 -> 1
        else -> 2
    }
}

// Rezessionsbaender fuer Langzeitcharts (gemeinsamer Rechenteil zu ui/Rezessionsbaender.kt): Spannen der US-Rezessionen
// in Millisekunden aus der NBER-Spalte der NY-Fed-Datei. Gezeichnet wird nur in Charts, die mindestens zehn Jahre zeigen.
object Rezessionen {

    // Spanne vom ersten Tag des ersten bis vor den ersten Tag nach dem letzten Rezessionsmonat
    data class Spanne(val von: Long, val bis: Long)

    // Zehn Jahre, mit etwas Spiel fuer Reihen, die „10 Jahre“ ab einem Handelstag zaehlen
    const val MIN_SPANNE_MS = 3_600L * 86_400_000L

    fun spannen(r: RezessionNyFed.Reihe?): List<Spanne> =
        r?.nber.orEmpty().map { (a, b) -> Spanne(RezessionNyFed.ms(a), RezessionNyFed.ms(b + 1)) }

    // Beginn der Abdeckung (erster Monat der Datei) in ms
    fun ab(r: RezessionNyFed.Reihe?): Long? = r?.let { RezessionNyFed.ms(it.von) }

    val alle: List<Spanne> by lazy { spannen(RezessionNyFed.datei) }
    val abMs: Long? by lazy { ab(RezessionNyFed.datei) }

    // Zeigt ein Chart von von bis bis (ms) lang genug, um Baender zu tragen?
    fun lang(von: Long, bis: Long): Boolean = bis - von >= MIN_SPANNE_MS

    // Spannen, die in den Bereich fallen, auf ihn beschnitten
    fun imBereich(von: Long, bis: Long, liste: List<Spanne> = alle): List<Spanne> =
        liste.filter { it.bis > von && it.von < bis }.map { Spanne(maxOf(it.von, von), minOf(it.bis, bis)) }
}
