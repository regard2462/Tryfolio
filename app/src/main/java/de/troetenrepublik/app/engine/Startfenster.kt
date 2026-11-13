package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.max

// Sparplan-Variante einer Regel und Auswertung ueber viele Startzeitpunkte (0.78).
object SparplanRegel {

    // Monatsrate in Euro. Bei 1 Euro je Order kostet der Kauf einer Rate 0,2 %.
    const val RATE = 500.0

    // zins: geldgewichtete Rendite pro Jahr als Anteil (interner Zinsfuss der Raten), null bei unter einem Jahr.
    // maxRueckgang: groesster Rueckgang der um Einzahlungen bereinigten Wertentwicklung. imMarkt: Anteil der Tage mit
    // Bestand. verlauf und einzahlungen je Handelstag; leer in der leichten Rechnung.
    class Ergebnis(
        val eingezahlt: Double,
        val endwert: Double,
        val zins: Double?,
        val maxRueckgang: Double,
        val kaeufe: Int,
        val verkaeufe: Int,
        val imMarkt: Double,
        val verlauf: List<Double>,
        val einzahlungen: List<Double>,
    )

    // Monat je Kerze (Jahr mal 12 plus Monat).
    fun monate(kerzen: List<Candle>): IntArray = IntArray(kerzen.size) { i -> StrategieLabor.monatJahr(kerzen[i].t).let { it.second * 12 + it.first } }

    // Am ersten Handelstag des Zeitraums und jedes weiteren Monats fliesst die Rate aufs Verrechnungskonto. Gilt die
    // Lage vom Schluss des Vortags als investiert, wird zur Eroeffnung alles Bargeld gekauft (die Rate, nach einer
    // Pause auch die angesammelten Raten); sonst wird der Bestand zur Eroeffnung verkauft und die Raten sammeln sich.
    // Kosten wie im Strategietest: Spread je Seite und Gebuehr je Order. Immer investiert ergibt stures Sparen.
    fun simulieren(
        d: StrategieLabor.Daten, ziel: BooleanArray, von: Int, bis: Int, kosten: StrategieLabor.Kosten,
        rate: Double = RATE, leicht: Boolean = false, monate: IntArray = monate(d.kerzen),
    ): Ergebnis? {
        if (von < 0 || bis > d.schluss.size || bis - von < 2 || rate <= 0) return null
        val raten = ArrayList<Pair<Long, Double>>()
        val werte = if (leicht) null else ArrayList<Double>(bis - von)
        val gezahlt = if (leicht) null else ArrayList<Double>(bis - von)
        var bar = 0.0
        var stueck = 0.0
        var eingezahlt = 0.0
        var kaeufe = 0
        var verkaeufe = 0
        var tageDrin = 0
        var vorher = 0.0
        var index = 1.0
        var hoechst = 1.0
        var rueckgang = 0.0
        for (i in von until bis) {
            var zufluss = 0.0
            if (i == von || monate[i] != monate[i - 1]) {
                zufluss = rate
                bar += rate
                eingezahlt += rate
                raten += d.kerzen[i].t to rate
            }
            val soll = if (i == 0) ziel[0] else ziel[i - 1]
            val p = d.eroeffnung(i)
            if (soll) {
                val geld = bar - kosten.gebuehr
                val kurs = Pricing.ask(p, kosten.spreadBps)
                if (geld > 0 && kurs > 0) {
                    stueck += geld / kurs
                    bar = 0.0
                    kaeufe++
                }
            } else if (stueck > 0) {
                bar += stueck * Pricing.bid(p, kosten.spreadBps) - kosten.gebuehr
                stueck = 0.0
                verkaeufe++
            }
            if (stueck > 0) tageDrin++
            val wert = bar + stueck * d.schluss[i]
            // Wertentwicklung ohne die Einzahlung des Tags
            val basis = vorher + zufluss
            if (basis > 0) index *= wert / basis
            hoechst = max(hoechst, index)
            rueckgang = max(rueckgang, 1.0 - index / hoechst)
            vorher = wert
            werte?.add(wert)
            gezahlt?.add(eingezahlt)
        }
        val ende = d.kerzen[bis - 1].t
        val jahre = (ende - d.kerzen[von].t) / (365.25 * 86_400_000.0)
        val zins = if (jahre < 1.0) null else Analysis.irr(raten, vorher, ende)?.let { it / 100.0 }
        return Ergebnis(eingezahlt, vorher, zins, rueckgang, kaeufe, verkaeufe, tageDrin.toDouble() / (bis - von), werte ?: emptyList(), gezahlt ?: emptyList())
    }

    // Regel und stures Sparen im selben Zeitraum. Der Ausstieg ist immer der der Regel.
    fun vergleichen(
        kerzen: List<Candle>, ab: Int, kosten: StrategieLabor.Kosten, regel: StrategieLabor.Regel, rate: Double = RATE, taktik: Taktik.Reihen? = null,
    ): Pair<Ergebnis, Ergebnis>? {
        val n = kerzen.size
        // Mit Taktik bildet der Signalwert das Signal; ein Ausweichwert zaehlt hier als Bargeld
        val d = taktik?.daten(n) ?: StrategieLabor.Daten(kerzen)
        if (ab < 0 || n - ab < 20) return null
        val m = monate(kerzen)
        val r = simulieren(d, StrategieLabor.ziel(d, regel), ab, n, kosten, rate, monate = m) ?: return null
        val s = simulieren(d, BooleanArray(n) { true }, ab, n, kosten, rate, monate = m) ?: return null
        return r to s
    }
}

// Viele Startzeitpunkte: Regel und Kaufen und Halten ab jedem Monatsanfang der Kursgeschichte fuer feste Haltedauern,
// jeder Start mit frischem Kapital. Die Fenster ueberlappen sich.
object Startfenster {

    val DAUERN = listOf(3, 5, 10, 20)

    // Eine Haltedauer zaehlt ab so vielen Startmonaten.
    const val MIN_FENSTER = 12

    private const val JAHR_MS = 365.25 * 86_400_000.0

    // Kleinster Wert, Quartile, Median und groesster Wert.
    class Spanne(val min: Double, val q1: Double, val median: Double, val q3: Double, val max: Double)

    fun spanne(werte: List<Double>): Spanne {
        val s = werte.toDoubleArray().also { it.sort() }
        return Spanne(s.first(), Zufallspfade.quantil(s, 0.25), Zufallspfade.quantil(s, 0.5), Zufallspfade.quantil(s, 0.75), s.last())
    }

    // vorn: Starts, bei denen die Regel am Ende mehr Wert hatte als Kaufen und Halten. Rendite pro Jahr und
    // groesster Rueckgang als Anteil; im Sparplan ist die Rendite geldgewichtet.
    class Dauer(
        val jahre: Int,
        val fenster: Int,
        val vorn: Int,
        val regelRendite: Spanne,
        val haltenRendite: Spanne,
        val regelRueckgang: Spanne,
        val haltenRueckgang: Spanne,
    )

    // Startpunkte: erster Handelstag jedes Monats, fruehestens nach StrategieLabor.EINSCHWINGEN Handelstagen (ist die
    // Reihe kuerzer als das Doppelte davon, ab der zweiten Kerze). Ein Fenster reicht bis zur ersten Kerze, die
    // jahre Jahre nach dem Start liegt; Starts, deren Fenster ueber das Ende der Reihe reicht, entfallen. Der Ausstieg
    // gilt nur bei der Einmalanlage. Haltedauern mit weniger als MIN_FENSTER Starts fehlen im Ergebnis.
    fun rechnen(
        kerzen: List<Candle>, kosten: StrategieLabor.Kosten, regel: StrategieLabor.Regel,
        ausstieg: StrategieLabor.Ausstieg = StrategieLabor.REGELAUSSTIEG, sparplan: Boolean = false,
        dauern: List<Int> = DAUERN, abbruch: () -> Unit = {}, taktik: Taktik.Reihen? = null,
    ): List<Dauer> {
        val n = kerzen.size
        if (n < 2) return emptyList()
        // Wie die Hauptrechnung: mit Taktik zaehlen Signalwert und Ausweichwert (auf gemeinsamen Tagen), je Fenster
        // zugeschnitten ueber von und bis. Im Sparplan gilt der Ausweichwert als Bargeld.
        val d = taktik?.daten(n) ?: StrategieLabor.Daten(kerzen)
        val ziel = StrategieLabor.ziel(d, regel)
        val immer = BooleanArray(n) { true }
        val monate = SparplanRegel.monate(kerzen)
        val erster = if (n >= 2 * StrategieLabor.EINSCHWINGEN) StrategieLabor.EINSCHWINGEN else 1
        val starts = (erster until n).filter { monate[it] != monate[it - 1] }
        val letzte = kerzen.last().t
        return dauern.mapNotNull { jahre ->
            val ms = (jahre * JAHR_MS).toLong()
            val rr = ArrayList<Double>()
            val hr = ArrayList<Double>()
            val rd = ArrayList<Double>()
            val hd = ArrayList<Double>()
            var vorn = 0
            var ende = 0
            for (von in starts) {
                val bisZeit = kerzen[von].t + ms
                if (bisZeit > letzte) break
                abbruch()
                while (ende < n && kerzen[ende].t < bisZeit) ende++
                val bis = minOf(ende + 1, n)
                if (sparplan) {
                    val r = SparplanRegel.simulieren(d, ziel, von, bis, kosten, leicht = true, monate = monate) ?: continue
                    val h = SparplanRegel.simulieren(d, immer, von, bis, kosten, leicht = true, monate = monate) ?: continue
                    rr += r.zins ?: continue
                    hr += h.zins ?: run { rr.removeAt(rr.size - 1); continue }
                    rd += r.maxRueckgang
                    hd += h.maxRueckgang
                    if (r.endwert > h.endwert) vorn++
                } else {
                    val r = StrategieLabor.simulieren(d, ziel, von, bis, kosten, leicht = true, ausstieg = ausstieg) ?: continue
                    val h = StrategieLabor.simulieren(d, immer, von, bis, kosten, leicht = true) ?: continue
                    rr += r.renditeJahr ?: continue
                    hr += h.renditeJahr ?: run { rr.removeAt(rr.size - 1); continue }
                    rd += r.maxRueckgang
                    hd += h.maxRueckgang
                    if (r.endwert > h.endwert) vorn++
                }
            }
            if (rr.size < MIN_FENSTER) null else Dauer(jahre, rr.size, vorn, spanne(rr), spanne(hr), spanne(rd), spanne(hd))
        }
    }
}
