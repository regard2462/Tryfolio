package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.sqrt

// Rueckblick auf die Tendenz unter „Im Preis?“ (5.92): Taugte „eher long“ oder „eher short“ frueher etwas? An jedem
// Stichtag der Vergangenheit (woechentlich) rechnet die App die Einordnung nur mit Daten bis zu diesem Tag, genau mit
// Fruehindikatoren.einordnungAusReihen und Eingepreist.tendenz, und misst, wie das passende Instrument danach lief.
// Gebaut wird die Bilanz mit :desktop:tendenzRueckblick, mitgeliefert als assets/indikatoren/tendenz_rueckblick.json
// (nur Kennzahlen, keine Kurse). Ein Uebungsvorschlag mit Optionsschein erscheint nur bei belegter Tendenz.
object TendenzRueckblick {

    // Zeitraeume der Messung in Wochen. Datierte Signale (Insider, Analysten, Kongress) ab einem Monat.
    val WOCHEN = listOf(1, 4, 13, 26, 52)
    val WOCHEN_DATIERT = listOf(4, 13, 26, 52)

    // Handelstage zu einem Zeitraum in Wochen (1, 3, 6 und 12 Monate).
    fun handelstage(wochen: Int): Int = when (wochen) {
        4 -> 21
        13 -> 63
        26 -> 126
        52 -> 252
        else -> wochen * 5
    }

    // Mindestens fuenf Jahre Lernzeit vor dem ersten Stichtag.
    const val LERN_TAGE = 1826

    // Tage vom Datenstand bis zum Einstieg: Lager, Erstantraege und Duerre erscheinen erst Tage nach ihrem Stichtag,
    // eine Woche deckt das sicher ab.
    const val VERZUG = 7

    // Belegt nur mit mindestens so vielen nicht ueberlappenden Faellen, einer Trefferquote mindestens so weit ueber
    // der Grundquote und einem t-Wert ueber T_SCHWELLE. Die Schwelle ist streng: je Signal werden 5 Zeitraeume in
    // 3 Gruppen (alle, gelb, rot) geprueft, Bonferroni fuer 15 Tests bei einseitig 5 % ergibt etwa 2,71 statt 1,65.
    const val MIN_UNABHAENGIG = 20
    const val VORSPRUNG = 0.05
    const val T_SCHWELLE = 2.71

    // Status eines Signals in der Datei
    const val GEPRUEFT = "GEPRUEFT"
    const val ZU_KURZ = "ZU_KURZ"
    const val OHNE_ZUGANG = "OHNE_ZUGANG"
    const val OHNE_DATEN = "OHNE_DATEN"

    // Einheit der Rendite: Prozent oder bei Zinsen Prozentpunkte (fallende Rendite positiv)
    const val PROZENT = "pct"
    const val PUNKTE = "pp"

    // Schluessel der datierten Signale wie die Art in ui/ImPreis.kt
    const val INSIDER = "INSIDER"
    const val HOCH = "HOCH"
    const val AB = "AB"
    const val MELDUNG = "MELDUNG"

    // Zusatz der Erstantraege: zyklische gegen defensive Aktien
    const val ZYKLISCH = Fruehindikatoren.ERSTANTRAEGE + ":zyklisch"

    // Schluessel eines fruehen Signals in der Datei; bei der Duerre je Getreide.
    fun schluessel(signal: String, markt: String = ""): String =
        if (signal == Fruehindikatoren.DUERRE) "$signal:${Fruehindikatoren.getreide(markt)}" else signal

    // ------------------------------------------------------------ Stichtage ohne Blick in die Zukunft

    // Ein Stichtag: Tag des Datenstands, Tendenz (+1, -1, 0) und Ampel der Einordnung (null ohne Zusammenhang).
    data class Fall(val tag: Long, val tendenz: Int, val ampel: Fruehwarnung.Ampel?)

    // Index nach dem letzten Punkt am Tag t oder davor (obere Grenze in einer nach Tag sortierten Reihe).
    private fun bisIndex(r: List<Pair<Long, Double>>, t: Long): Int {
        var lo = 0
        var hi = r.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (r[m].first <= t) lo = m + 1 else hi = m
        }
        return lo
    }

    // Index des ersten Punkts am Tag t oder danach.
    private fun abIndex(r: List<Pair<Long, Double>>, t: Long): Int {
        var lo = 0
        var hi = r.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (r[m].first < t) lo = m + 1 else hi = m
        }
        return lo
    }

    // Walk-forward: zu jedem Tag von x die Einordnung wie in der App (Fruehindikatoren.einordnungAusReihen), aber nur
    // mit den Punkten von x und y bis zu diesem Tag; die Schaetzung wird jede Woche neu gerechnet. Erst nach lernTage
    // gemeinsamer Geschichte. umgekehrt wie bei den Zinsen. y wird vorab auf die Punkte verduennt, die die Paarung
    // ueberhaupt nutzt (je Tag von x der letzte Punkt davor); das aendert das Ergebnis nicht und spart Rechenzeit.
    fun faelle(
        x: List<Pair<Long, Double>>, y: List<Pair<Long, Double>>, vorzeichen: Int, saison: Boolean, umgekehrt: Boolean = false,
        ab: Long = Long.MIN_VALUE, toleranz: Int = 6, lernTage: Int = LERN_TAGE,
    ): List<Fall> {
        val xs = x.filter { it.second.isFinite() }.distinctBy { it.first }.sortedBy { it.first }
        val ys0 = y.filter { it.second.isFinite() }.sortedBy { it.first }
        val ys = xs.mapNotNull { (t, _) ->
            val i = bisIndex(ys0, t) - 1
            if (i >= 0 && t - ys0[i].first <= 21) ys0[i] else null
        }.distinctBy { it.first }
        val erster = Eingepreist.paaren(xs.filter { it.first >= ab }, ys, toleranz).minOfOrNull { it.tag } ?: return emptyList()
        val aus = ArrayList<Fall>()
        for (i in xs.indices) {
            val t = xs[i].first
            if (t - erster < lernTage) continue
            val j = bisIndex(ys, t)
            if (j == 0) continue
            val e = Fruehindikatoren.einordnungAusReihen(xs.subList(0, i + 1), ys.subList(0, j), vorzeichen, saison, ab, toleranz) ?: continue
            aus += Fall(t, Eingepreist.tendenz(e, umgekehrt), e.ampel)
        }
        return aus
    }

    // Datiertes Signal (Insiderkauf, Hoch- oder Abstufung, Meldung eines Kaufs) an einem Stichtag nachTagen
    // Handelstage nach dem Signal: Einordnung wie in der App (Eingepreist.gelaufen und tendenz), aber nur mit Kursen
    // und Index bis zum Stichtag und nur mit frueheren Signalen, deren Ergebnis da schon feststand. Ergebnis: Zeitpunkt
    // des Stichtags (ms) und Einordnung (Tendenz mit Eingepreist.tendenz); null, wenn die Kurse nicht so weit reichen.
    fun datiert(
        kurse: List<de.troetenrepublik.app.data.Candle>, index: List<de.troetenrepublik.app.data.Candle>?, zeit: Long, richtung: Int,
        frueherZeiten: List<Long>, nachTagen: Int,
    ): Pair<Long, Eingepreist.Gelaufen>? {
        val tag = Math.floorDiv(zeit, 86_400_000L)
        val i = kurse.indexOfFirst { Math.floorDiv(it.t, 86_400_000L) >= tag }
        if (i < 0 || i + nachTagen >= kurse.size) return null
        val stichtag = kurse[i + nachTagen].t
        val bis = kurse.subList(0, i + nachTagen + 1)
        val indexBis = index?.filter { it.t <= stichtag }
        val frueher = Eingepreist.frueher(bis, indexBis, frueherZeiten.filter { it < zeit })
        val g = Eingepreist.gelaufen(bis, indexBis, zeit, richtung, frueher) ?: return null
        return stichtag to g
    }

    // ------------------------------------------------------------ Renditen

    enum class Art { PROZENT, ZINS }

    // Rendite ab dem ersten Kurs am Tag ab oder bis sieben Tage danach, ueber wochen Wochen (letzter Kurs bis dahin,
    // hoechstens sieben Tage vorher). ZINS: Aenderung der Rendite mit umgekehrtem Vorzeichen in Prozentpunkten, denn
    // faellt die Rendite, steigt der Anleihekurs. null, wenn die Kurse nicht so weit reichen.
    fun rendite(kurse: List<Pair<Long, Double>>, ab: Long, wochen: Int, art: Art = Art.PROZENT): Double? {
        val i = abIndex(kurse, ab)
        if (i >= kurse.size || kurse[i].first - ab > 7) return null
        val ziel = kurse[i].first + 7L * wochen
        val j = bisIndex(kurse, ziel) - 1
        if (j <= i || ziel - kurse[j].first > 7) return null
        val a = kurse[i].second
        val b = kurse[j].second
        return when (art) {
            Art.PROZENT -> if (a > 0 && b > 0) b / a - 1 else null
            Art.ZINS -> if (a.isFinite() && b.isFinite()) -(b - a) else null
        }
    }

    // Hinweis, dass zwischen zwei Handelstagen der naechste Kontrakt verfiel: positiv heisst, der neue erste Kontrakt
    // passt besser zum alten zweiten als zum alten ersten. Verglichen werden die Abstaende benachbarter Kontrakte, die
    // sich bei einer Bewegung des ganzen Markts kaum aendern, beim Verfall aber um eine Stelle rutschen. Mit weniger als
    // drei Kontrakten die Kurse selbst.
    fun rollHinweis(a: DoubleArray, b: DoubleArray): Double {
        fun ok(v: DoubleArray, k: Int) = k < v.size && v[k] > 0
        var summe = 0.0
        var n = 0
        for (k in 0 until 2) {
            if (!ok(a, k) || !ok(a, k + 1) || !ok(a, k + 2) || !ok(b, k) || !ok(b, k + 1)) continue
            val neu = ln(b[k + 1] / b[k])
            summe += abs(neu - ln(a[k + 1] / a[k])) - abs(neu - ln(a[k + 2] / a[k + 1]))
            n++
        }
        if (n > 0) return summe
        if (!ok(a, 0) || !ok(a, 1) || !ok(b, 0)) return Double.NEGATIVE_INFINITY
        return abs(ln(b[0] / a[0])) - abs(ln(b[0] / a[1]))
    }

    // Tage mit Verfall (Index des Tags nach dem Verfall): Monatskontrakte verfallen genau einmal im Monat. Ohne mitte
    // je Kalendermonat der Tag mit dem staerksten positiven Hinweis. Mit mitte (ueblicher Tag des Monats, an dem der
    // naechste Kurs schon zum neuen Kontrakt gehoert, etwa 22 bei WTI, 28 bei Erdgas, 1 bei Benzin und Heizoel) je
    // Verfallsperiode der Tag mit dem staerksten Hinweis hoechstens sechs Tage davon entfernt. Einzelne Tage mit grosser
    // Bewegung koennen so nicht als Verfall gelten.
    fun rolltage(r: List<Pair<Long, DoubleArray>>, mitte: Int = 0): Set<Int> {
        val best = HashMap<Long, Pair<Int, Double>>()
        for (i in 1 until r.size) {
            val h = rollHinweis(r[i - 1].second, r[i].second)
            if (if (mitte == 0) !(h > 0) else !h.isFinite()) continue
            val d = LocalDate.ofEpochDay(r[i].first + if (mitte == 0) 0 else 15 - mitte)
            if (mitte != 0 && abs(d.dayOfMonth - 15) > 6) continue
            val m = d.year * 12L + d.monthValue
            val alt = best[m]
            if (alt == null || alt.second < h) best[m] = i to h
        }
        return best.values.map { it.first }.toSet()
    }

    private fun sauber(tabelle: List<Pair<Long, DoubleArray>>) = tabelle.filter { it.second.isNotEmpty() && it.second[0] > 0 }.sortedBy { it.first }

    // Ueblicher Tag fuer rolltage je Kontrakt (Wurzel bei der NYMEX), 0 ohne Angabe.
    fun rollMitte(wurzel: String): Int = when (wurzel) {
        "CL" -> 22
        "NG" -> 28
        "RB", "HO" -> 1
        else -> 0
    }

    // Verlauf einer Position im naechsten Kontrakt aus Tageskursen der Kontrakte 1 bis 4 (EIA), Start bei 100. Mit
    // bereinigt wird am Verfall in den zweiten Kontrakt gerollt: die Rendite ueber den Verfall rechnet vom alten zweiten
    // Kontrakt, sonst entstuende ein Sprung, der nie gehandelt wurde. Ohne bereinigt die einfache Reihe des ersten
    // Kontrakts wie bei Yahoo (CL=F), zum Vergleich.
    fun rollVerlauf(tabelle: List<Pair<Long, DoubleArray>>, bereinigt: Boolean = true, mitte: Int = 0): List<Pair<Long, Double>> {
        val r = sauber(tabelle)
        if (r.isEmpty()) return emptyList()
        val roll = if (bereinigt) rolltage(r, mitte) else emptySet()
        val aus = ArrayList<Pair<Long, Double>>(r.size)
        var stand = 100.0
        aus += r[0].first to stand
        for (i in 1 until r.size) {
            val a = r[i - 1].second
            val vorher = if (i in roll && a.size > 1 && a[1] > 0) a[1] else a[0]
            stand *= r[i].second[0] / vorher
            aus += r[i].first to stand
        }
        return aus
    }

    // Erkannte Verfallstage, zur Kontrolle (etwa zwoelf im Jahr bei Monatskontrakten): Zahl und Tage des Monats.
    fun rollen(tabelle: List<Pair<Long, DoubleArray>>, mitte: Int = 0): Int = rolltage(sauber(tabelle), mitte).size

    fun rollTageImMonat(tabelle: List<Pair<Long, DoubleArray>>, mitte: Int = 0): List<Int> {
        val r = sauber(tabelle)
        return rolltage(r, mitte).map { LocalDate.ofEpochDay(r[it].first).dayOfMonth }
    }

    // ------------------------------------------------------------ Auswertung

    // Ein Fall mit Folgerendite des Instruments (ohne Richtung). gruppe: Wert bei datierten Signalen, damit nicht
    // ueberlappende Faelle je Wert gezaehlt werden.
    data class Messung(val tag: Long, val tendenz: Int, val rendite: Double, val gruppe: String = "")

    // Kennzahlen je Zeitraum. n: Faelle mit Tendenz (ueberlappend), unabhaengig: davon nicht ueberlappende. treffer:
    // Anteil in Tendenzrichtung, grund: Anteil bei beliebigen Stichtagen desselben Zeitraums in dieselben Richtungen.
    // mittel: mittlere Rendite in Tendenzrichtung (Anteil bzw. Prozentpunkte), t: t-Wert mit Newey-West-Fehler,
    // tUnabhaengig: t-Wert nur der nicht ueberlappenden Faelle. haelften: Mittel in der ersten und zweiten Haelfte der
    // Faelle (Stabilitaet). grundMittel: was man mit denselben Richtungen an beliebigen Stichtagen im Schnitt bekommen
    // haette (Drift des Instruments), zum Vergleich mit mittel.
    @Serializable
    data class Zeitraum(
        val wochen: Int,
        val n: Int,
        val unabhaengig: Int,
        val treffer: Double,
        val grund: Double,
        val mittel: Double,
        val t: Double,
        val tUnabhaengig: Double,
        val haelften: List<Double> = emptyList(),
        val belegt: Boolean = false,
        val grundMittel: Double = 0.0,
    )

    // Standardfehler des Mittels nach Newey-West mit Bartlett-Gewichten ueber den zeitlichen Abstand: Faelle, deren
    // Zeitraeume sich ueberlappen, zaehlen gemeinsam (Gewicht 1 minus Abstand durch Laenge). Funktioniert auch bei
    // unregelmaessigen Stichtagen und bei mehreren Werten am selben Tag.
    fun neweyWest(tage: LongArray, z: DoubleArray, laenge: Int): Double {
        val n = z.size
        if (n < 2) return Double.NaN
        val ordnung = tage.indices.sortedBy { tage[it] }
        val t = LongArray(n) { tage[ordnung[it]] }
        val m = z.average()
        val u = DoubleArray(n) { z[ordnung[it]] - m }
        val l = maxOf(1, laenge).toDouble()
        var summe = 0.0
        for (i in 0 until n) {
            summe += u[i] * u[i]
            var j = i + 1
            while (j < n && t[j] - t[i] < l) {
                summe += 2 * (1 - (t[j] - t[i]) / l) * u[i] * u[j]
                j++
            }
        }
        return sqrt(maxOf(summe, 0.0)) / n
    }

    // Nicht ueberlappende Faelle: je Gruppe der Reihe nach, ein Fall zaehlt erst, wenn der vorige Zeitraum vorbei ist.
    fun unabhaengig(faelle: List<Messung>, laenge: Int): List<Messung> =
        faelle.groupBy { it.gruppe }.values.flatMap { g ->
            val aus = ArrayList<Messung>()
            var frei = Long.MIN_VALUE
            for (f in g.sortedBy { it.tag }) if (f.tag >= frei) { aus += f; frei = f.tag + laenge }
            aus
        }

    // Kennzahlen eines Zeitraums aus allen Stichtagen (auch ohne Tendenz, fuer die Grundquote). laenge: Tage, ueber die
    // sich die Messungen ueberlappen (in der Regel 7 mal Wochen). basis: Stichtage fuer die Grundquote, ohne Angabe
    // dieselben (bei den Gruppen gelb und rot alle Stichtage des Signals). null ohne Faelle mit Tendenz.
    fun auswerten(messungen: List<Messung>, wochen: Int, laenge: Int = 7 * wochen, basis: List<Messung> = messungen): Zeitraum? {
        val alle = basis.filter { it.rendite.isFinite() }
        val faelle = messungen.filter { it.rendite.isFinite() && it.tendenz != 0 }
        if (alle.isEmpty()) return null
        if (faelle.size < 2) return null
        val hoch = alle.count { it.rendite > 0 }.toDouble() / alle.size
        val tief = alle.count { it.rendite < 0 }.toDouble() / alle.size
        val lang = faelle.count { it.tendenz > 0 }
        val grund = (lang * hoch + (faelle.size - lang) * tief) / faelle.size
        val grundMittel = (2 * lang - faelle.size).toDouble() / faelle.size * alle.sumOf { it.rendite } / alle.size
        val z = DoubleArray(faelle.size) { faelle[it].tendenz * faelle[it].rendite }
        val treffer = z.count { it > 0 }.toDouble() / z.size
        val mittel = z.average()
        val se = neweyWest(LongArray(faelle.size) { faelle[it].tag }, z, laenge)
        val t = if (se > 0) mittel / se else 0.0
        val frei = unabhaengig(faelle, laenge)
        val zf = frei.map { it.tendenz * it.rendite }
        val tf = if (zf.size >= 2) {
            val m = zf.average()
            val sd = sqrt(zf.sumOf { (it - m) * (it - m) } / (zf.size - 1))
            if (sd > 0) m / (sd / sqrt(zf.size.toDouble())) else 0.0
        } else 0.0
        val sortiert = faelle.sortedBy { it.tag }
        val mitte = sortiert.size / 2
        val haelften = listOf(sortiert.subList(0, mitte), sortiert.subList(mitte, sortiert.size))
            .map { h -> if (h.isEmpty()) 0.0 else h.sumOf { it.tendenz * it.rendite } / h.size }
        // Belegt: genug Faelle, Trefferquote klar ueber der Grundquote, t ueber der strengen Schwelle, in beiden Haelften
        // positiv und besser als die blosse Drift (sonst stammt der Ertrag nur daher, dass der Markt ohnehin stieg)
        val belegt = frei.size >= MIN_UNABHAENGIG && treffer - grund >= VORSPRUNG && t >= T_SCHWELLE && haelften.all { it > 0 } && mittel > grundMittel
        return Zeitraum(wochen, faelle.size, frei.size, treffer, grund, mittel, t, tf, haelften, belegt, grundMittel)
    }

    // ------------------------------------------------------------ Datei

    // Ein Signal: Status, Einheit, gepruefter Zeitraum (JJJJ-MM) und Kennzahlen je Gruppe: alle, gelb und rot bei
    // fruehen Signalen, alle, teil und wenig bei datierten. instrument: woran gemessen wurde.
    @Serializable
    data class Signal(
        val status: String,
        val einheit: String = PROZENT,
        val instrument: String = "",
        val von: String = "",
        val bis: String = "",
        val gruppen: Map<String, List<Zeitraum>> = emptyMap(),
    )

    @Serializable
    data class Datei(val stand: String = "", val schwelle: Double = T_SCHWELLE, val signale: Map<String, Signal> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // Bester Zeitraum: unter den belegten der mit dem hoechsten t-Wert, also dem guenstigsten Verhaeltnis von Ertrag zu
    // Schwankung (stabil in beiden Haelften ist schon Bedingung fuer belegt).
    fun bester(l: List<Zeitraum>): Zeitraum? = l.filter { it.belegt }.maxByOrNull { it.t }

    // Was die App unter der Tendenz zeigt. zeitraum: der beste belegte, sonst null. gruppe: „rot“, wenn heute Rot ist
    // und die roten Faelle fuer sich belegt sind, sonst „alle“. liste: alle Zeitraeume der Gruppe fuer die Einzelheiten.
    data class Bilanz(val signal: Signal, val gruppe: String, val zeitraum: Zeitraum?, val liste: List<Zeitraum>) {
        val belegt: Boolean get() = zeitraum != null
        val geprueft: Boolean get() = signal.status == GEPRUEFT
    }

    fun bilanz(s: Signal?, ampel: Fruehwarnung.Ampel? = null): Bilanz? {
        if (s == null) return null
        val rot = s.gruppen["rot"].orEmpty()
        if (ampel == Fruehwarnung.Ampel.ROT) bester(rot)?.let { return Bilanz(s, "rot", it, rot) }
        val alle = s.gruppen["alle"].orEmpty()
        return Bilanz(s, "alle", bester(alle), alle)
    }

    // ------------------------------------------------------------ Uebungsvorschlag

    // Handelbarer Basiswert zu einem Signal mit Richtungsfaktor: +1 heisst in Richtung der Tendenz, -1 dagegen (zweites
    // Bein bei zyklisch gegen defensiv). Bei den Erstantraegen gilt die Tendenz schon fuer Anleihen: kurze US-Anleihen
    // (SHY), passend zur zweijaehrigen Rendite.
    fun basiswerte(signal: String): List<Pair<String, Int>> = if (signal == ZYKLISCH) {
        listOf(Fruehindikatoren.ZYKLISCH to 1, Fruehindikatoren.DEFENSIV to -1)
    } else when (signal.substringBefore(':')) {
        Fruehindikatoren.ROHOEL, Fruehindikatoren.CUSHING -> listOf("CL=F" to 1)
        Fruehindikatoren.BENZIN -> listOf("RB=F" to 1)
        Fruehindikatoren.DESTILLAT -> listOf("HO=F" to 1)
        Fruehindikatoren.GAS_US -> listOf("NG=F" to 1)
        Fruehindikatoren.DUERRE -> listOf(Fruehindikatoren.getreide(signal.substringAfter(':', "")) to 1)
        Fruehindikatoren.FLUGGAESTE -> listOf(Fruehindikatoren.AIRLINES to 1)
        Fruehindikatoren.ERSTANTRAEGE -> listOf("SHY" to 1)
        // Datierte Signale: der Wert selbst, das weiss nur die Seite
        else -> emptyList()
    }

    // Ein Optionsschein als Uebung: Call bei long, Put bei short, Basispreis am naechsten zum Kurs, Verfall der erste
    // Termin der App nach dem 1,5-fachen des belegten Zeitraums. preis: Wert je Einheit des Basiswerts, hebel: wie
    // stark der Schein heute auf den Basiswert reagiert, zeitwertWoche: Anteil des Preises, der in einer Woche ohne
    // Kursbewegung verloren geht. verfallTag: Mitternacht UTC des Verfalltags (wie Derivatives.warrantFuer).
    data class Wette(
        val symbol: String, val call: Boolean, val strike: Double, val verfall: Long, val verfallTag: Long,
        val wochen: Int, val preis: Double, val hebel: Double, val zeitwertWoche: Double,
    )

    fun laufzeitTage(wochen: Int): Int = ceil(1.5 * 7 * wochen).toInt()

    fun wette(symbol: String, richtung: Int, wochen: Int, spot: Double, vol: Double, jetzt: Long = System.currentTimeMillis()): Wette? {
        if (richtung == 0 || !(spot > 0) || wochen <= 0) return null
        val call = richtung > 0
        val ziel = jetzt + laufzeitTage(wochen) * 86_400_000L
        val termine = Derivatives.expiries(jetzt)
        val verfall = termine.firstOrNull { it >= ziel } ?: termine.lastOrNull() ?: return null
        val strike = Derivatives.strikes(spot).minByOrNull { abs(it - spot) } ?: return null
        val jahre = (verfall - jetzt) / (365.0 * 86_400_000L)
        val v = Pricing.smileVol(if (vol > 0) vol else 0.3, spot, strike, call)
        val g = Pricing.blackScholes(spot, strike, jahre, v, call)
        if (!(g.price > 0)) return null
        val tag = java.time.Instant.ofEpochMilli(verfall).atZone(java.time.ZoneId.of("Europe/Berlin")).toLocalDate()
        return Wette(
            symbol, call, strike, verfall, tag.toEpochDay() * 86_400_000L, wochen, g.price,
            abs(g.delta) * spot / g.price, (-g.theta * 7 / g.price).coerceAtLeast(0.0),
        )
    }
}
