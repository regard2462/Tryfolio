package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Lange Reihen fuer Kernanlageklassen [0.78].
//
// Monatlich aus indikatoren/lange_reihen.json (tools/daten/lange_reihen.py), alles in US-Dollar:
//   Verbraucherpreise der USA ab 1913 (Bureau of Labor Statistics), Schatzwechsel mit drei Monaten ab 1934 und Renditen von
//   Staatsanleihen konstanter Laufzeit mit 5 und 10 Jahren ab 1953 (Federal Reserve, H.15), Gold ab 1960 (Weltbank).
//   Die Datei traegt die Werte der Quellen; Verlaeufe mit Ertrag rechnet dieses Objekt. Die Anleihereihen sind Modellreihen,
//   weil die Quelle nur Renditen nennt (Rechenweg bei anleiheMonat). Zinsen und Gold sind Monatsmittel, kein Monatsschluss.
// Jaehrlich aus HistorieJst (ab 1871): reale Renditen von Aktien, Anleihen und Geldmarkt je Land.
//
// Ein Monat ist die Zahl Jahr * 12 + Monat - 1, ein Tag die Zahl der Tage seit dem 1.1.1970.
object LangeReihen {

    const val DATEI = "indikatoren/lange_reihen.json"

    enum class Reihe(val schluessel: String, val quelle: String) {
        PREISE("cpi", "cpi"), SCHATZWECHSEL("tb3", "zins"), RENDITE_5("y5", "zins"), RENDITE_10("y10", "zins"), GOLD("gold", "gold"),
        ROHSTOFFE("comm", "rohstoffe"),
        // Wechselkurse (Federal Reserve, H.10), Monatsmittel; Auswertung in engine/WaehrungReihen.kt
        KURS_DEM("fx_dem", "kurse"), KURS_EUR("fx_eur", "kurse"), KURS_GBP("fx_gbp", "kurse"), KURS_JPY("fx_jpy", "kurse"),
        KURS_CHF("fx_chf", "kurse"), KURS_CAD("fx_cad", "kurse"),
    }

    fun monat(jahr: Int, monat: Int): Int = jahr * 12 + monat - 1
    fun jahr(monat: Int): Int = abgerundet(monat.toLong(), 12L).toInt()

    // Ganzzahlige Teilung, zur kleineren Zahl gerundet
    private fun abgerundet(a: Long, b: Long): Long = if (a >= 0) a / b else -((-a + b - 1) / b)

    // Werte je Monat ab `ab`, null bei Luecke
    class Monatsreihe(val ab: Int, val werte: List<Double?>) {
        val bis: Int get() = ab + werte.size - 1
        fun wert(monat: Int): Double? = werte.getOrNull(monat - ab)
    }

    class Quelle(val name: String, val url: String, val reihe: String, val lizenz: String)

    class Datei(val stand: String, val reihen: Map<Reihe, Monatsreihe>, val quellen: Map<String, Quelle>) {
        fun quelle(r: Reihe): Quelle? = quellen[r.quelle]
    }

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val quellen = HashMap<String, Quelle>()
        for ((k, v) in w["quellen"] as? JsonObject ?: return null) {
            val o = v.jsonObject
            fun feld(n: String) = (o[n] as? JsonPrimitive)?.content.orEmpty()
            quellen[k] = Quelle(feld("name"), feld("url"), feld("reihe"), feld("lizenz"))
        }
        val r = w["r"] as? JsonObject ?: return null
        val reihen = HashMap<Reihe, Monatsreihe>()
        for (art in Reihe.entries) {
            val o = r[art.schluessel] as? JsonObject ?: continue
            val ab = (o["ab"] as? JsonPrimitive)?.content?.split("-")?.takeIf { it.size == 2 } ?: return null
            val werte = (o["w"] as? JsonArray)?.map { if (it is JsonNull) null else (it as? JsonPrimitive)?.doubleOrNull } ?: return null
            reihen[art] = Monatsreihe(monat(ab[0].toInt(), ab[1].toInt()), werte)
        }
        if (reihen.isEmpty() || reihen.keys.any { it.quelle !in quellen }) null else Datei(stand, reihen, quellen)
    }.getOrNull()

    // ------------------------------------------------------------ Monatsertraege aus Zinsen

    // Ertrag eines Monats mit Schatzwechseln beim Diskontsatz d (Prozent im Jahr, auf 360 Tage): Ein Wechsel mit 91 Tagen
    // kostet 1 - d/100 * 91/360 und zahlt 1; ueber drei Monate verteilt ergibt das je Monat die dritte Wurzel.
    fun wechselMonat(diskontPct: Double): Double {
        val kurs = 1.0 - diskontPct / 100.0 * 91.0 / 360.0
        return if (kurs <= 0.0) 0.0 else (1.0 / kurs).pow(1.0 / 3.0) - 1.0
    }

    // Kurs einer Anleihe mit halbjaehrlichem Kupon (kupon und rendite als Anteil im Jahr) und der Restlaufzeit in Jahren,
    // Nennwert 1: Barwert der Kupons und der Tilgung bei halbjaehrlicher Verzinsung.
    fun anleiheKurs(kupon: Double, rendite: Double, jahre: Double): Double {
        if (rendite == 0.0) return 1.0 + kupon * jahre
        val abzins = (1.0 + rendite / 2.0).pow(-2.0 * jahre)
        return kupon / rendite * (1.0 - abzins) + abzins
    }

    // Ertrag eines Monats mit einer Anleihe konstanter Laufzeit (Modell): Zu Monatsbeginn wird eine Anleihe zum Nennwert
    // gekauft, ihr Kupon ist die Rendite des Vormonats. Am Monatsende hat sie einen Monat weniger Laufzeit und wird zur neuen
    // Rendite bewertet. Ertrag = Kupon fuer einen Monat + Kursaenderung. Danach wird in eine neue Anleihe getauscht.
    // Renditen in Prozent im Jahr.
    fun anleiheMonat(renditeVorPct: Double, renditePct: Double, laufzeitJahre: Int): Double {
        val kupon = renditeVorPct / 100.0
        return kupon / 12.0 + anleiheKurs(kupon, renditePct / 100.0, laufzeitJahre - 1.0 / 12.0) - 1.0
    }

    // ------------------------------------------------------------ Verlaeufe mit Ertrag (nominal, US-Dollar)

    // modell: aus Renditen gerechnet statt aus Kursen gemessen
    enum class Klasse(val modell: Boolean) { GELDMARKT(false), ANLEIHEN_5(true), ANLEIHEN_10(true), GOLD(false) }

    // Verlauf mit Ertrag je Monat, 1 im ersten Monat; endet vor der ersten Luecke der Quelle. Gold ist der Preis selbst.
    fun verlauf(d: Datei, klasse: Klasse): Monatsreihe? = when (klasse) {
        Klasse.GOLD -> d.reihen[Reihe.GOLD]?.let { ohneLuecke(it) }
        Klasse.GELDMARKT -> d.reihen[Reihe.SCHATZWECHSEL]?.let { r ->
            val z = ohneLuecke(r)
            val aus = ArrayList<Double?>(z.werte.size + 1)
            var stand = 1.0
            aus += stand
            for (x in z.werte) { stand *= 1.0 + wechselMonat(x!!); aus += stand }
            // Stand am Ende des Monats; der erste Punkt liegt einen Monat vor dem ersten Zins
            Monatsreihe(z.ab - 1, aus)
        }
        Klasse.ANLEIHEN_5 -> d.reihen[Reihe.RENDITE_5]?.let { anleihen(it, 5) }
        Klasse.ANLEIHEN_10 -> d.reihen[Reihe.RENDITE_10]?.let { anleihen(it, 10) }
    }

    private fun anleihen(r: Monatsreihe, laufzeit: Int): Monatsreihe {
        val z = ohneLuecke(r)
        val aus = ArrayList<Double?>(z.werte.size)
        var stand = 1.0
        aus += stand
        for (i in 1 until z.werte.size) { stand *= 1.0 + anleiheMonat(z.werte[i - 1]!!, z.werte[i]!!, laufzeit); aus += stand }
        return Monatsreihe(z.ab, aus)
    }

    // Der Teil ab dem ersten Wert bis vor die erste Luecke
    private fun ohneLuecke(r: Monatsreihe): Monatsreihe {
        val erster = r.werte.indexOfFirst { it != null }
        if (erster < 0) return Monatsreihe(r.ab, emptyList())
        var ende = erster
        while (ende < r.werte.size && r.werte[ende] != null) ende++
        return Monatsreihe(r.ab + erster, r.werte.subList(erster, ende))
    }

    // ------------------------------------------------------------ Real und nominal

    // Kaufkraft: jeder Wert geteilt durch den Preisindex seines Monats, bezogen auf den Preisstand im Monat `basis`
    // (ohne Angabe der letzte Monat mit Preisindex). Monate ohne Preisindex bleiben leer.
    fun real(r: Monatsreihe, preise: Monatsreihe, basis: Int? = null): Monatsreihe {
        val b = basis ?: (preise.ab + preise.werte.indexOfLast { it != null })
        val p0 = preise.wert(b) ?: return Monatsreihe(r.ab, r.werte.map { null })
        return Monatsreihe(r.ab, r.werte.mapIndexed { i, x -> val p = preise.wert(r.ab + i); if (x == null || p == null || p <= 0.0) null else x * p0 / p })
    }

    // Umkehrung von real: aus Kaufkraft im Preisstand von `basis` wieder der Betrag des Monats
    fun nominal(r: Monatsreihe, preise: Monatsreihe, basis: Int? = null): Monatsreihe {
        val b = basis ?: (preise.ab + preise.werte.indexOfLast { it != null })
        val p0 = preise.wert(b) ?: return Monatsreihe(r.ab, r.werte.map { null })
        return Monatsreihe(r.ab, r.werte.mapIndexed { i, x -> val p = preise.wert(r.ab + i); if (x == null || p == null || p0 <= 0.0) null else x * p / p0 })
    }

    // Rendite je Kalenderjahr (Anteil): Dezember gegen Dezember des Vorjahrs; Jahre ohne beide Werte fehlen.
    fun jahresrenditen(r: Monatsreihe): Map<Int, Double> {
        val aus = LinkedHashMap<Int, Double>()
        for (j in jahr(r.ab)..jahr(r.bis)) {
            val a = r.wert(monat(j - 1, 12)) ?: continue
            val b = r.wert(monat(j, 12)) ?: continue
            if (a > 0.0) aus[j] = b / a - 1.0
        }
        return aus
    }

    // ------------------------------------------------------------ Verkettung mit echten Kursen

    // Monat eines Tages (Tage seit 1.1.1970), gregorianisch
    fun monatVon(tag: Long): Int {
        val z = tag + 719468
        val aera = abgerundet(z, 146097L)
        val doe = z - aera * 146097L
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val m = if (mp < 10) mp + 3 else mp - 9
        val j = yoe + aera * 400 + (if (m <= 2) 1 else 0)
        return monat(j.toInt(), m.toInt())
    }

    // Letzter Tag eines Monats als Tage seit 1.1.1970
    fun letzterTag(monat: Int): Long = ersterTag(monat + 1) - 1

    private fun ersterTag(monat: Int): Long {
        val jahr = jahr(monat)
        val m = monat - jahr * 12 + 1
        val j = (if (m <= 2) jahr - 1 else jahr).toLong()
        val aera = abgerundet(j, 400L)
        val yoe = j - aera * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return aera * 146097 + doe - 719468
    }

    // Verkettete Reihe: tage aufsteigend. Bis echtAb (ausschliesslich) Monatswerte der langen Reihe am letzten Tag ihres
    // Monats, auf den echten Kurs umgerechnet (mal faktor); ab echtAb die echten Tageskurse unveraendert.
    class Verkettet(val tage: LongArray, val werte: DoubleArray, val echtAb: Long, val faktor: Double) {
        fun echt(i: Int): Boolean = tage[i] >= echtAb
        // Zahl der Punkte aus der langen Reihe
        val vorlauf: Int get() = tage.indexOfFirst { it >= echtAb }
    }

    // Lange Monatsreihe bis zum Start echter Tageskurse, danach die echten Kurse. Angelegt wird am letzten Kurs des ersten
    // Monats, in dem beide einen Wert haben: Dort gilt lange Reihe * faktor = echter Kurs. Aus der langen Reihe kommen nur
    // die Monate davor ohne Luecke; echte Kurse vor dem Anlegetag entfallen (hoechstens ein Monat). null ohne gemeinsamen
    // Monat. Beide Reihen muessen dieselbe Sache messen (beide mit oder beide ohne Ertraege, beide nominal oder beide real).
    fun verketten(lang: Monatsreihe, tage: LongArray, kurse: DoubleArray): Verkettet? {
        if (tage.isEmpty() || tage.size != kurse.size) return null
        var anker = -1
        var ankerMonat = 0
        var i = 0
        while (i < tage.size) {
            val m = monatVon(tage[i])
            var ende = i
            while (ende + 1 < tage.size && monatVon(tage[ende + 1]) == m) ende++
            val l = lang.wert(m)
            if (l != null && l > 0.0 && kurse[ende] > 0.0) { anker = ende; ankerMonat = m; break }
            i = ende + 1
        }
        if (anker < 0) return null
        val faktor = kurse[anker] / lang.wert(ankerMonat)!!
        var erster = ankerMonat
        while (lang.wert(erster - 1) != null) erster--
        val vor = ankerMonat - erster
        val n = vor + tage.size - anker
        val t = LongArray(n)
        val w = DoubleArray(n)
        for (k in 0 until vor) { t[k] = letzterTag(erster + k); w[k] = lang.wert(erster + k)!! * faktor }
        for (k in anker until tage.size) { t[vor + k - anker] = tage[k]; w[vor + k - anker] = kurse[k] }
        return Verkettet(t, w, tage[anker], faktor)
    }

    // ------------------------------------------------------------ Lange Sicht: Mischungen in Jahresschritten, real

    // ROHSTOFFE: Preisindex plus Geldmarktzins (Naeherung an voll besicherte Terminkontrakte, ohne Rollertrag).
    // AKTIEN_KLEIN_WERT: Small-Cap-Value der USA, nur mit geladenen Fama/French-Daten (kleinWert in anlagen).
    enum class Anlage { AKTIEN_USA, AKTIEN_WELT, ANLEIHEN, GELDMARKT, GOLD, ROHSTOFFE, AKTIEN_KLEIN_WERT }

    // Welt ohne USA: ein Jahr zaehlt ab so vielen Laendern mit Wert
    const val MIN_LAENDER = 8
    const val USA = "USA"

    // Reale Rendite je Jahr (Anteil) ab `von`, null bei Luecke
    class Jahre(val von: Int, val renditen: List<Double?>) {
        fun wert(jahr: Int): Double? = renditen.getOrNull(jahr - von)
    }

    // Reale Jahresrenditen je Anlage. Aktien USA, Anleihen und Geldmarkt: USA aus HistorieJst. Aktien Welt: Mittel der
    // uebrigen Laender mit Wert im Jahr (jedes in eigener Waehrung und mit eigener Inflation), ab MIN_LAENDER Laendern.
    // Gold: Preis im Dezember gegen Dezember des Vorjahrs, mit dem Preisindex der USA in Kaufkraft gerechnet.
    // kleinWert: nominale Jahresrenditen (Anteil) der Small-Cap-Value-Aktien der USA je Kalenderjahr, siehe kleinWertLesen.
    fun anlagen(jst: HistorieJst.Datei, d: Datei?, kleinWert: Map<Int, Double>? = null): Map<Anlage, Jahre> {
        val aus = LinkedHashMap<Anlage, Jahre>()
        val usa = jst.laender[USA]
        fun ausJst(art: HistorieJst.Art): Jahre? {
            val l = usa ?: return null
            return l.reihen[art]?.let { Jahre(l.von, it) }
        }
        ausJst(HistorieJst.Art.AKTIEN)?.let { aus[Anlage.AKTIEN_USA] = it }
        val uebrige = jst.laender.values.filter { it.a3 != USA && it.reihen[HistorieJst.Art.AKTIEN] != null }
        if (uebrige.isNotEmpty()) {
            val von = uebrige.minOf { it.von }
            val welt = (von..jst.ende).map { j ->
                val werte = uebrige.mapNotNull { l -> l.reihen[HistorieJst.Art.AKTIEN]?.getOrNull(j - l.von) }
                if (werte.size < MIN_LAENDER) null else werte.average()
            }
            if (welt.any { it != null }) aus[Anlage.AKTIEN_WELT] = Jahre(von, welt)
        }
        ausJst(HistorieJst.Art.ANLEIHEN)?.let { aus[Anlage.ANLEIHEN] = it }
        ausJst(HistorieJst.Art.ZINS)?.let { aus[Anlage.GELDMARKT] = it }
        val gold = d?.reihen?.get(Reihe.GOLD)
        val preise = d?.reihen?.get(Reihe.PREISE)
        if (gold != null && preise != null) {
            val je = jahresrenditen(real(gold, preise))
            if (je.isNotEmpty()) {
                val von = je.keys.first()
                aus[Anlage.GOLD] = Jahre(von, (von..je.keys.last()).map { je[it] })
            }
        }
        rohstoffeMitZins(d)?.let { r ->
            val je = if (preise != null) jahresrenditen(real(r, preise)) else emptyMap()
            if (je.isNotEmpty()) {
                val von = je.keys.first()
                aus[Anlage.ROHSTOFFE] = Jahre(von, (von..je.keys.last()).map { je[it] })
            }
        }
        if (kleinWert != null && preise != null) {
            val je = kleinWertReal(kleinWert, preise)
            if (je.isNotEmpty()) {
                val von = je.keys.min()
                aus[Anlage.AKTIEN_KLEIN_WERT] = Jahre(von, (von..je.keys.max()).map { je[it] })
            }
        }
        return aus
    }

    // Rohstoffe angenaehert: Je Monat die Preisaenderung des Gesamtindex der Weltbank (Spot) plus der Monatsertrag der
    // Schatzwechsel. Voll besicherte Terminkontrakte bringen etwa das, das Rollergebnis fehlt. Nominal, ab dem ersten
    // Monat, in dem beide Reihen einen Wert haben, bis vor die erste Luecke.
    fun rohstoffeMitZins(d: Datei?): Monatsreihe? {
        val dd = d ?: return null
        val preis = ohneLuecke(dd.reihen[Reihe.ROHSTOFFE] ?: return null)
        val bills = dd.reihen[Reihe.SCHATZWECHSEL] ?: return null
        if (preis.werte.size < 2) return null
        val aus = ArrayList<Double?>()
        var stand = 1.0
        aus += stand
        for (i in 1 until preis.werte.size) {
            val z = bills.wert(preis.ab + i) ?: break
            stand *= 1.0 + (preis.werte[i]!! / preis.werte[i - 1]!! - 1.0) + wechselMonat(z)
            aus += stand
        }
        return Monatsreihe(preis.ab, aus)
    }

    // Jahresrenditen der Small-Cap-Value-Aktien (SMALL HiBM, wertgewichtet) aus dem Abschnitt „Average Value Weighted
    // Returns -- Annual“ der Fama/French-Datei 6_Portfolios_2x3 (Jahr, Rendite in Prozent), als Anteile je Jahr.
    fun kleinWertLesen(csv: String): Map<Int, Double> {
        val aus = LinkedHashMap<Int, Double>()
        var spalte = -1
        var im = false
        for (roh in csv.lineSequence()) {
            val zeile = roh.trim()
            if (!im) {
                if (zeile.contains("Value Weighted Returns", ignoreCase = true) && zeile.contains("Annual", ignoreCase = true)) im = true
                continue
            }
            val teile = zeile.split(',').map { it.trim() }
            if (spalte < 0) {
                if (teile.size > 1 && teile[0].isEmpty()) spalte = teile.indexOfFirst { it.equals("SMALL HiBM", ignoreCase = true) }
                continue
            }
            val jahr = teile[0].takeIf { it.length == 4 && it.all { c -> c.isDigit() } }?.toInt()
            if (jahr == null) { if (aus.isNotEmpty()) break else continue }
            val v = teile.getOrNull(spalte)?.toDoubleOrNull() ?: continue
            if (v <= -99.0) continue
            aus[jahr] = v / 100.0
        }
        return aus
    }

    // Reale Jahresrenditen aus nominalen: durch die Preisaenderung Dezember gegen Dezember des Vorjahrs
    fun kleinWertReal(nominal: Map<Int, Double>, preise: Monatsreihe): Map<Int, Double> {
        val aus = LinkedHashMap<Int, Double>()
        for ((j, n) in nominal.toSortedMap()) {
            val a = preise.wert(monat(j - 1, 12)) ?: continue
            val b = preise.wert(monat(j, 12)) ?: continue
            if (a > 0.0) aus[j] = (1.0 + n) * a / b - 1.0
        }
        return aus
    }

    // Tiefster Rueckgang auf Jahresendstaenden: tiefe in Prozent (negativ), hoch = Jahr, an dessen Ende der Hoechststand lag,
    // tief = Jahr des Tiefstands, erholt = Jahr, an dessen Ende der Hoechststand wieder erreicht war (null: bis zum Ende nicht).
    class Rueckgang(val tiefe: Double, val hoch: Int, val tief: Int, val erholt: Int?) {
        val jahreBisErholt: Int? get() = erholt?.let { it - hoch }
    }

    // Verteilung der realen Rendite je Jahr (Prozent) ueber alle Zeitraeume von `jahre` Jahren; verlust: Anteil der Zeitraeume
    // mit realem Verlust (0 bis 1).
    class Haltedauer(
        val jahre: Int, val faelle: Int, val min: Double, val q1: Double, val median: Double, val q3: Double, val max: Double, val verlust: Double,
    )

    // verlauf: reales Vermoegen, 1 am Ende des Jahres vor `von`, danach je Jahr ein Stand (Laenge = Jahre + 1).
    // rendite: geometrisches Mittel je Jahr in Prozent.
    class Ergebnis(
        val von: Int, val bis: Int, val renditen: DoubleArray, val verlauf: DoubleArray, val rendite: Double,
        val rueckgang: Rueckgang?, val haltedauern: List<Haltedauer>,
    )

    val HALTEDAUERN = listOf(1, 5, 10, 20, 30)

    // Mischung mit festen Anteilen (Summe 1), am Ende jedes Jahres auf die Anteile zurueckgestellt, ohne Kosten und Steuern.
    // Gerechnet wird ueber die laengste lueckenlose Folge von Jahren, in denen jede gewaehlte Anlage einen Wert hat und die
    // im letzten gemeinsamen Jahr endet. null ohne solche Jahre oder bei ungueltigen Anteilen.
    fun mischung(anlagen: Map<Anlage, Jahre>, anteile: Map<Anlage, Double>, dauern: List<Int> = HALTEDAUERN): Ergebnis? {
        val teile = anteile.filter { it.value > 0.0 }
        val summe = teile.values.sum()
        if (teile.isEmpty() || summe <= 0.0 || teile.keys.any { it !in anlagen }) return null
        val reihen = teile.map { (a, w) -> anlagen.getValue(a) to w / summe }
        fun mix(j: Int): Double? {
            var r = 0.0
            for ((reihe, w) in reihen) r += w * (reihe.wert(j) ?: return null)
            return r
        }
        val spaetestes = reihen.minOf { it.first.von + it.first.renditen.size - 1 }
        val fruehestes = reihen.maxOf { it.first.von }
        var bis = spaetestes
        while (bis >= fruehestes && mix(bis) == null) bis--
        if (bis < fruehestes) return null
        var von = bis
        while (von - 1 >= fruehestes && mix(von - 1) != null) von--
        val r = DoubleArray(bis - von + 1) { mix(von + it)!! }
        return ausRenditen(von, r, dauern)
    }

    // Ergebnis aus den Jahresrenditen der Mischung ab dem Jahr `von` (Anteile, nicht Prozent)
    fun ausRenditen(von: Int, r: DoubleArray, dauern: List<Int> = HALTEDAUERN): Ergebnis? {
        if (r.isEmpty()) return null
        val verlauf = DoubleArray(r.size + 1)
        verlauf[0] = 1.0
        for (i in r.indices) verlauf[i + 1] = verlauf[i] * (1.0 + r[i])
        if (verlauf.last() <= 0.0) return null
        val rendite = (verlauf.last().pow(1.0 / r.size) - 1.0) * 100.0
        return Ergebnis(von, von + r.size - 1, r, verlauf, rendite, rueckgang(verlauf, von), dauern.mapNotNull { haltedauer(verlauf, it) })
    }

    // Ausschnitt eines Ergebnisses von Jahr `von` bis Jahr `bis` (beide eingeschlossen), neu gerechnet; null ausserhalb
    fun zuschnitt(e: Ergebnis, von: Int, bis: Int, dauern: List<Int> = HALTEDAUERN): Ergebnis? {
        if (von > bis || von < e.von || bis > e.bis) return null
        if (von == e.von && bis == e.bis) return e
        return ausRenditen(von, e.renditen.copyOfRange(von - e.von, bis - e.von + 1), dauern)
    }

    // verlauf[0] gilt am Ende des Jahres von - 1
    fun rueckgang(verlauf: DoubleArray, von: Int): Rueckgang? {
        var hoch = 0
        var tiefe = 0.0
        var bestHoch = 0
        var bestTief = 0
        for (i in verlauf.indices) {
            if (verlauf[i] >= verlauf[hoch]) hoch = i
            val d = verlauf[i] / verlauf[hoch] - 1.0
            if (d < tiefe) { tiefe = d; bestHoch = hoch; bestTief = i }
        }
        if (tiefe >= 0.0) return null
        val erholt = (bestTief + 1 until verlauf.size).firstOrNull { verlauf[it] >= verlauf[bestHoch] }
        return Rueckgang(tiefe * 100.0, von - 1 + bestHoch, von - 1 + bestTief, erholt?.let { von - 1 + it })
    }

    fun haltedauer(verlauf: DoubleArray, jahre: Int): Haltedauer? {
        val n = verlauf.size - jahre
        if (jahre < 1 || n < 1) return null
        val werte = DoubleArray(n) { ((verlauf[it + jahre] / verlauf[it]).pow(1.0 / jahre) - 1.0) * 100.0 }
        werte.sort()
        return Haltedauer(
            jahre, n, werte.first(), quantil(werte, 0.25), quantil(werte, 0.5), quantil(werte, 0.75), werte.last(),
            werte.count { it < 0.0 }.toDouble() / n,
        )
    }

    // Quantil einer sortierten Reihe, zwischen den Nachbarn geradlinig
    private fun quantil(sortiert: DoubleArray, q: Double): Double {
        val pos = q * (sortiert.size - 1)
        val i = floor(pos).toInt().coerceIn(0, sortiert.size - 1)
        val j = min(i + 1, sortiert.size - 1)
        return sortiert[i] + (sortiert[j] - sortiert[i]) * (pos - i)
    }

    // Bestandteile einer Vorgabe; Anleihen sind lange US-Staatsanleihen, Geldmarkt Schatzwechsel
    enum class Teil { AKTIEN, ANLEIHEN, GELDMARKT, GOLD, ROHSTOFFE, KLEIN_WERT }

    // Vorgaben der Anzeige: Anteile in Prozent (Summe 100). naeherungen: Schluessel der Naeherungen, die die Anzeige hinter
    // dem Info-Symbol nennt (ROH: Rohstoffe mit Zins, MITTEL: mittlere Anleihen als Mischung, KURZ: kurze Anleihen als
    // Geldmarkt, KLEIN: Small-Cap-Value aus den Fama/French-Daten).
    class Vorgabe(val name: String, val prozent: Map<Teil, Double>, val naeherungen: List<String> = emptyList()) {
        constructor(name: String, aktien: Int, anleihen: Int, geldmarkt: Int, gold: Int) : this(
            name, mapOf(Teil.AKTIEN to aktien.toDouble(), Teil.ANLEIHEN to anleihen.toDouble(), Teil.GELDMARKT to geldmarkt.toDouble(), Teil.GOLD to gold.toDouble()),
        )

        val aktien: Int get() = (prozent[Teil.AKTIEN] ?: 0.0).roundToInt()
        val anleihen: Int get() = (prozent[Teil.ANLEIHEN] ?: 0.0).roundToInt()
        val geldmarkt: Int get() = (prozent[Teil.GELDMARKT] ?: 0.0).roundToInt()
        val gold: Int get() = (prozent[Teil.GOLD] ?: 0.0).roundToInt()

        fun anteile(aktien: Anlage): Map<Anlage, Double> {
            val aus = LinkedHashMap<Anlage, Double>()
            for (t in Teil.entries) {
                val w = prozent[t] ?: continue
                if (w <= 0.0) continue
                aus[when (t) {
                    Teil.AKTIEN -> aktien; Teil.ANLEIHEN -> Anlage.ANLEIHEN; Teil.GELDMARKT -> Anlage.GELDMARKT
                    Teil.GOLD -> Anlage.GOLD; Teil.ROHSTOFFE -> Anlage.ROHSTOFFE; Teil.KLEIN_WERT -> Anlage.AKTIEN_KLEIN_WERT
                }] = w / 100.0
            }
            return aus
        }
    }

    // All Weather: 30 % Aktien, 40 % lange und 15 % mittlere Anleihen, je 7,5 % Gold und Rohstoffe. Mittlere Anleihen gibt es als
    // Reihe nicht und stehen je zur Haelfte fuer lange Anleihen und Geldmarkt.
    // Golden Butterfly: je 20 % Aktien, Small-Cap-Value, lange Anleihen, kurze Anleihen (als Geldmarkt) und Gold.
    val VORGABEN = listOf(
        Vorgabe("A100", 100, 0, 0, 0), Vorgabe("A80", 80, 20, 0, 0), Vorgabe("A60", 60, 40, 0, 0), Vorgabe("A40", 40, 60, 0, 0),
        Vorgabe("GOLD10", 60, 30, 0, 10), Vorgabe("VIER", 25, 25, 25, 25),
        Vorgabe(
            "ALLWEATHER", mapOf(Teil.AKTIEN to 30.0, Teil.ANLEIHEN to 47.5, Teil.GELDMARKT to 7.5, Teil.GOLD to 7.5, Teil.ROHSTOFFE to 7.5),
            listOf("ROH", "MITTEL"),
        ),
        Vorgabe(
            "GOLDEN", mapOf(Teil.AKTIEN to 20.0, Teil.KLEIN_WERT to 20.0, Teil.ANLEIHEN to 20.0, Teil.GELDMARKT to 20.0, Teil.GOLD to 20.0),
            listOf("KURZ", "KLEIN"),
        ),
    )
}
