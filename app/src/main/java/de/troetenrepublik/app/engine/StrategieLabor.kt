package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.MarketApi
import kotlin.math.E
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Strategie-Labor (seit 5.91): einfache Signalregeln auf Tageskerzen im Rueckblick, mit Spread je Seite und
// Ordergebuehr, verglichen mit Kaufen und Halten. Das Signal entsteht am Tagesschluss, gehandelt wird zur
// Eroeffnung des naechsten Handelstags; so kennt keine Regel den Kurs, zu dem sie handelt. Die Indikatoren
// laufen ueber die ganze Reihe, damit sie zu Beginn des Zeitraums schon eingeschwungen sind. Ohne Steuern.
object StrategieLabor {

    const val START = 10_000.0
    const val GEBUEHR = 1.0

    // Anteil der Kerzen im Lernzeitraum, der Rest ist der Pruefzeitraum.
    const val LERNANTEIL = 0.7

    private const val JAHR_MS = 365.25 * 86_400_000.0

    enum class Art { HALTEN, SMA, KREUZ, RSI, MACD, DONCHIAN, BOLLINGER, RHYTHMUS, MOMENTUM, RSI_KURZ, MONATSWECHSEL, ZIELVOLA }

    // Parameter je Art. SMA: Laenge a. KREUZ: kurzer Durchschnitt a, langer b. RSI: Laenge n, Kauf unter a,
    // Verkauf ueber b. DONCHIAN: Kauf ueber dem Hoch der letzten a Tage, Verkauf unter dem Tief der letzten b Tage.
    // BOLLINGER: Baender ueber a Tage mit b Standardabweichungen, Kauf unter dem unteren Band, Verkauf ueber der Mitte.
    // RHYTHMUS: nur in den Wochen 13 bis 15 des 16-Wochen-Zyklus investiert (Zaehlung in Rhythmus16), ohne Parameter.
    // MOMENTUM: am letzten Handelstag des Monats investiert, wenn der Schluss ueber dem vor a Handelstagen liegt.
    // RSI_KURZ: Kauf ueber dem Durchschnitt KURZ_TREND bei RSI(n) unter a, Verkauf bei Schluss ueber dem Durchschnitt b
    // oder nach KURZ_TAGE Handelstagen. MONATSWECHSEL: investiert vom letzten Handelstag des Monats bis zum a-ten des
    // Folgemonats. trend (nur RSI und BOLLINGER): Kauf nur bei Schluss ueber dem Durchschnitt dieser Laenge, 0 = ohne.
    // monatlich (nur SMA): die Lage wechselt nur am letzten Handelstag des Monats.
    // ZIELVOLA: immer investiert, Anteil = Zielschwankung a (Prozent pro Jahr) durch die Schwankung der letzten n
    // Handelstage, hoechstens b Prozent; eigene Rechnung in Zielvolatilitaet.kt (rechnen waehlt sie).
    data class Regel(val art: Art, val a: Int = 0, val b: Int = 0, val n: Int = 0, val trend: Int = 0, val monatlich: Boolean = false)

    const val KURZ_TREND = 200
    const val KURZ_TAGE = 10
    const val TRENDFILTER = 200

    // Die Regeln des Labors in fester Reihenfolge, Kaufen und Halten zuerst.
    val REGELN = listOf(
        Regel(Art.HALTEN),
        Regel(Art.SMA, 200),
        Regel(Art.KREUZ, 50, 200),
        Regel(Art.RSI, 30, 70, 14),
        Regel(Art.MACD),
        Regel(Art.DONCHIAN, 20, 10),
        Regel(Art.BOLLINGER, 20, 2),
        Regel(Art.RHYTHMUS),
        Regel(Art.MOMENTUM, 252),
        Regel(Art.RSI_KURZ, 10, 5, 2),
        Regel(Art.MONATSWECHSEL, 3),
        Regel(Art.DONCHIAN, 55, 20),
        Regel(Art.ZIELVOLA, 15, 100, 20),
    )

    // Regeln, deren Linie und Zeile in der Uebersicht erst nach dem Aufklappen erscheinen.
    val ZUSATZ: Set<Regel> = REGELN.drop(8).toSet()

    // Ausstieg zusaetzlich zum Verkaufssignal der Regel. STOPP: wert Prozent unter dem Einstieg. TRAILING: wert mal
    // ATR(ATR_TAGE) unter dem hoechsten Schluss seit Einstieg, steigt nur. ZEIT: nach wert Handelstagen. ZIEL: Gewinn
    // von wert mal dem Abstand zum Stopp, der ZIEL_STOPP Prozent unter dem Einstieg liegt.
    enum class AusstiegArt { REGEL, STOPP, TRAILING, ZEIT, ZIEL }

    data class Ausstieg(val art: AusstiegArt = AusstiegArt.REGEL, val wert: Int = 0)

    const val ATR_TAGE = 20
    const val ZIEL_STOPP = 8

    val REGELAUSSTIEG = Ausstieg()

    val AUSSTIEGE = listOf(REGELAUSSTIEG) +
        listOf(5, 8, 10, 15).map { Ausstieg(AusstiegArt.STOPP, it) } +
        listOf(2, 3, 4).map { Ausstieg(AusstiegArt.TRAILING, it) } +
        listOf(5, 10, 20).map { Ausstieg(AusstiegArt.ZEIT, it) } +
        listOf(2, 3).map { Ausstieg(AusstiegArt.ZIEL, it) }

    // Ein Kauf mit seinem Verkauf: Zeitpunkte der Kerzen, Ergebnis nach Kosten als Anteil des Einsatzes, Haltedauer
    // in Handelstagen. offen: am Ende des Zeitraums noch gehalten, zum Schlusskurs bewertet.
    data class Geschaeft(val einstieg: Long, val ausstieg: Long, val ergebnis: Double, val tage: Int, val offen: Boolean = false)

    // Kennzahlen je Geschaeft mit denselben Begriffen wie bei den eigenen Verkaeufen (Risiko.bilanz), Ergebnisse als
    // Anteil des Einsatzes. verhaeltnis: mittlerer Gewinn durch mittleren Verlust. haltedauer: Mittel in Handelstagen.
    // verlustserie: meiste Verlustgeschaefte in Folge.
    data class Kennzahlen(val bilanz: Risiko.Verkaeufe, val verhaeltnis: Double?, val haltedauer: Double, val verlustserie: Int)

    fun kennzahlen(g: List<Geschaeft>): Kennzahlen? {
        val b = Risiko.bilanz(g.map { it.ergebnis }, 0.0, 1) ?: return null
        val gewinn = b.mittelGewinn
        val verlust = b.mittelVerlust
        var serie = 0
        var laengste = 0
        for (x in g) {
            serie = if (x.ergebnis < 0) serie + 1 else 0
            laengste = max(laengste, serie)
        }
        return Kennzahlen(b, if (gewinn != null && verlust != null && verlust < 0) gewinn / -verlust else null, g.map { it.tage }.average(), laengste)
    }

    // spreadBps: ganzer Abstand von Geld- und Briefkurs in Basispunkten, wie in den Einstellungen.
    data class Kosten(val spreadBps: Double = 0.0, val gebuehr: Double = GEBUEHR)

    // renditeJahr, maxRueckgang, treffer und imMarkt als Anteil (0,1 = 10 %). renditen: Veraenderung des
    // Depotwerts je Handelstag. sharpe je Handelstag (Mittel durch Standardabweichung, ohne Zins).
    data class Ergebnis(
        val endwert: Double,
        val renditeJahr: Double?,
        val maxRueckgang: Double,
        val trades: Int,
        val treffer: Double?,
        val imMarkt: Double,
        val verlauf: List<Pair<Long, Double>>,
        val renditen: List<Double>,
        val sharpe: Double,
        val jahre: Double,
        // Geschaefte in zeitlicher Folge; leer in der leichten Rechnung
        val geschaefte: List<Geschaeft> = emptyList(),
    )

    // Kurse mit zwischengespeicherten Indikatoren, damit viele Varianten schnell rechnen.
    // signal: Wert, auf dem die Regeln ihr Signal bilden, wenn es nicht der gehandelte ist. ausweich: Wert, der bei
    // „Signal aus“ statt Bargeld gehalten wird. Beide liegen auf denselben Handelstagen wie kerzen (engine/Taktik.kt).
    class Daten(val kerzen: List<Candle>, val signal: Daten? = null, val ausweich: Daten? = null) {
        init {
            require(signal == null || signal.kerzen.size == kerzen.size)
            require(ausweich == null || ausweich.kerzen.size == kerzen.size)
        }

        val schluss: List<Double> = kerzen.map { it.close }
        val hoch: List<Double> = kerzen.map { if (it.high.isFinite() && it.high > 0) it.high else it.close }
        val tief: List<Double> = kerzen.map { if (it.low.isFinite() && it.low > 0) it.low else it.close }
        private val smaSpeicher = HashMap<Int, List<Double?>>()
        private val rsiSpeicher = HashMap<Int, List<Double?>>()
        private val donchianSpeicher = HashMap<Int, Indikatoren.Baender>()
        private val bollingerSpeicher = HashMap<Pair<Int, Int>, Indikatoren.Baender>()
        private val atrSpeicher = HashMap<Int, List<Double?>>()
        val macd: Indikatoren.Macd by lazy { Indikatoren.macd(schluss) }

        // Monat je Handelstag (Jahr mal 12 plus Monat), dazu die zwei naechsten Werktage nach der letzten Kerze. Der
        // Handelskalender steht vorab fest; die Monatsregeln duerfen ihn kennen, die Kurse dieser Tage nicht.
        private val monate: IntArray by lazy {
            val n = kerzen.size
            val m = IntArray(n + 2)
            fun nummer(tag: Long): Int = monatJahr(tag * 86_400_000L).let { it.second * 12 + it.first }
            for (i in 0 until n) m[i] = nummer(kerzen[i].t.floorDiv(86_400_000L))
            var tag = if (n > 0) kerzen[n - 1].t.floorDiv(86_400_000L) else 0L
            for (j in 0 until 2) {
                tag++
                // Tag 0 (1.1.1970) war ein Donnerstag: 5 und 6 sind Samstag und Sonntag
                while ((tag + 3).mod(7L) >= 5) tag++
                m[n + j] = nummer(tag)
            }
            m
        }

        // Stelle jedes Handelstags in seinem Monat (1 = erster); 0 im angebrochenen ersten Monat der Reihe.
        private val stellen: IntArray by lazy {
            val s = IntArray(monate.size)
            for (i in 1 until s.size) s[i] = if (monate[i] != monate[i - 1]) 1 else if (s[i - 1] > 0) s[i - 1] + 1 else 0
            s
        }

        // Tag i (bis eine Stelle nach der letzten Kerze) ist der letzte Handelstag seines Monats.
        fun monatsende(i: Int): Boolean = monate[i] != monate[i + 1]

        // Tag i gehoert zu den ersten k Handelstagen seines Monats.
        fun monatsanfang(i: Int, k: Int): Boolean = stellen[i] in 1..k

        fun atr(n: Int): List<Double?> = atrSpeicher.getOrPut(n) { Indikatoren.atr(hoch, tief, schluss, n) }

        fun sma(n: Int): List<Double?> = smaSpeicher.getOrPut(n) { Indikatoren.sma(schluss, n) }
        fun rsi(n: Int): List<Double?> = rsiSpeicher.getOrPut(n) { Indikatoren.rsi(schluss, n) }
        fun donchian(n: Int): Indikatoren.Baender = donchianSpeicher.getOrPut(n) { Indikatoren.donchian(hoch, tief, n) }
        fun bollinger(n: Int, k: Int): Indikatoren.Baender = bollingerSpeicher.getOrPut(n to k) { Indikatoren.bollinger(schluss, n, k.toDouble()) }

        // Ausfuehrungskurs am Tag i: Eroeffnung, ohne sie der Schluss.
        fun eroeffnung(i: Int): Double = kerzen[i].open.takeIf { it.isFinite() && it > 0 } ?: kerzen[i].close
    }

    // Gewuenschte Lage nach dem Schluss jedes Tags: true = investiert.
    fun ziel(d: Daten, r: Regel): BooleanArray {
        d.signal?.let { return ziel(it, r) }
        val n = d.schluss.size
        val z = BooleanArray(n)
        var drin = false
        var seit = 0
        for (i in 0 until n) {
            val c = d.schluss[i]
            // Kauf nur ueber dem langen Durchschnitt, wenn die Regel den Trendfilter traegt
            val imTrend = r.trend <= 0 || d.sma(r.trend)[i]?.let { c > it } == true
            drin = when (r.art) {
                Art.HALTEN -> true
                Art.SMA -> if (r.monatlich && !d.monatsende(i)) drin else d.sma(r.a)[i]?.let { c > it } ?: false
                Art.KREUZ -> {
                    val k = d.sma(r.a)[i]
                    val l = d.sma(r.b)[i]
                    k != null && l != null && k > l
                }
                Art.RSI -> {
                    val x = d.rsi(r.n)[i]
                    when {
                        x == null -> drin
                        !drin && x < r.a && imTrend -> true
                        drin && x > r.b -> false
                        else -> drin
                    }
                }
                Art.MACD -> {
                    val m = d.macd.linie[i]
                    val s = d.macd.signal[i]
                    m != null && s != null && m > s
                }
                Art.DONCHIAN -> {
                    // Hoch und Tief der Tage davor, ohne den heutigen
                    val o = if (i > 0) d.donchian(r.a).oben[i - 1] else null
                    val u = if (i > 0) d.donchian(r.b).unten[i - 1] else null
                    when {
                        !drin && o != null && c > o -> true
                        drin && u != null && c < u -> false
                        else -> drin
                    }
                }
                Art.BOLLINGER -> {
                    val b = d.bollinger(r.a, r.b)
                    val u = b.unten[i]
                    val m = b.mitte[i]
                    when {
                        !drin && u != null && c < u && imTrend -> true
                        drin && m != null && c > m -> false
                        else -> drin
                    }
                }
                Art.RHYTHMUS -> {
                    // Investiert, wenn der naechste Handelstag in den Wochen 13 bis 15 liegt. Der Kalender steht
                    // vorab fest; nach der letzten Kerze gilt der naechste Werktag.
                    var naechster = Math.floorDiv(d.kerzen[i].t, 86_400_000L) + 1
                    if (i + 1 < n) naechster = Math.floorDiv(d.kerzen[i + 1].t, 86_400_000L)
                    else while (Math.floorMod(naechster - Rhythmus16.ANKER, 7L) >= 5) naechster++
                    Rhythmus16.woche(naechster) in Rhythmus16.INVESTIERT
                }
                Art.MOMENTUM -> if (!d.monatsende(i)) drin else i >= r.a && c > d.schluss[i - r.a]
                Art.RSI_KURZ -> {
                    val x = d.rsi(r.n)[i]
                    val lang = d.sma(KURZ_TREND)[i]
                    val kurz = d.sma(r.b)[i]
                    when {
                        !drin && x != null && lang != null && c > lang && x < r.a -> { seit = i; true }
                        // Gekauft wird am Tag nach dem Signal: KURZ_TAGE Schluesse spaeter sind so viele Handelstage gehalten
                        drin && ((kurz != null && c > kurz) || i - seit >= KURZ_TAGE) -> false
                        else -> drin
                    }
                }
                // Der naechste Handelstag ist der letzte seines Monats oder einer der ersten a des Folgemonats
                Art.MONATSWECHSEL -> d.monatsende(i + 1) || d.monatsanfang(i + 1, r.a)
                // Immer investiert; die Groesse der Position rechnet Zielvolatilitaet.simulieren
                Art.ZIELVOLA -> true
            }
            z[i] = drin
        }
        return z
    }

    // Spielt die Regel im Bereich von (einschliesslich) bis bis (ausschliesslich) mit frischem Startkapital durch.
    // Am Tag i gilt die Lage vom Schluss des Vortags; liegt die beim Start schon auf „investiert“, wird zur ersten
    // Eroeffnung gekauft. Eine am Ende offene Position zaehlt zum Schlusskurs und als Trade. Mit leicht=true bleiben
    // verlauf und renditen leer, damit viele Varianten gleichzeitig den Speicher nicht fuellen; Rueckgang und
    // Sharpe-Quote laufen dann ohne Zwischenlisten mit.
    // ausstieg kommt zum Verkaufssignal der Regel dazu. Stopp und Ziel gelten ab dem Kauftag: Unterschreitet das
    // Tagestief den Stopp, wird zum Stopp verkauft, bei Eroeffnung darunter zur Eroeffnung; erreicht das Tageshoch das
    // Ziel, zum Ziel, bei Eroeffnung darueber zur Eroeffnung. Trifft beides am selben Tag, zaehlt der Stopp. Der
    // Trailing-Stopp eines Tages stammt aus Schluss und ATR der Tage davor. Der Zeitstopp verkauft zur Eroeffnung.
    // Nach einem solchen Ausstieg wird erst wieder gekauft, wenn die Regel zwischendurch draussen war und neu auf
    // investiert wechselt.
    // Mit d.ausweich liegt freies Geld im Ausweichwert: gekauft zur Eroeffnung des Tags, an dem der gehandelte Wert
    // verkauft ist (nach einem Stopp im Tagesverlauf am naechsten Tag), verkauft zur Eroeffnung des Kauftags, jeweils
    // mit Spread und Ordergebuehr. Geschaefte, Trefferquote und Zeit im Markt zaehlen nur den gehandelten Wert.
    fun simulieren(
        d: Daten, ziel: BooleanArray, von: Int, bis: Int, kosten: Kosten, start: Double = START, leicht: Boolean = false,
        ausstieg: Ausstieg = REGELAUSSTIEG,
    ): Ergebnis? {
        if (von < 0 || bis > d.schluss.size || bis - von < 2) return null
        val mit = ausstieg.art != AusstiegArt.REGEL
        val atr = if (ausstieg.art == AusstiegArt.TRAILING) d.atr(ATR_TAGE) else null
        val geschaefte = if (leicht) null else ArrayList<Geschaeft>()
        var kaufTag = 0
        var stopp = 0.0
        var zielKurs = 0.0
        var hoechster = 0.0
        var gesperrt = false
        val aus = d.ausweich
        var park = 0.0
        var bar = start
        var stueck = 0.0
        var einsatz = 0.0
        var trades = 0
        var gewinner = 0
        var tageDrin = 0
        val werte = if (leicht) null else DoubleArray(bis - von)
        val renditen = if (leicht) null else ArrayList<Double>(bis - von)
        var wert = start
        var hoechst = start
        var rueckgang = 0.0
        var vorher = start
        var anzahl = 0
        var mittel = 0.0
        var summeQuadrate = 0.0
        for (i in von until bis) {
            val soll = if (i == 0) ziel[0] else ziel[i - 1]
            val p = d.eroeffnung(i)
            if (!soll) gesperrt = false
            if (soll && stueck == 0.0) {
                val frei = if (aus != null && park > 0.0) park * Pricing.bid(aus.eroeffnung(i), kosten.spreadBps) - kosten.gebuehr else bar
                val geld = frei - kosten.gebuehr
                val kurs = Pricing.ask(p, kosten.spreadBps)
                if (geld > 0 && kurs > 0 && !gesperrt) {
                    stueck = geld / kurs
                    einsatz = frei
                    bar = 0.0
                    park = 0.0
                    trades++
                    kaufTag = i
                    if (mit) {
                        stopp = 0.0
                        zielKurs = 0.0
                        hoechster = p
                        when (ausstieg.art) {
                            AusstiegArt.STOPP -> stopp = p * (1.0 - ausstieg.wert / 100.0)
                            AusstiegArt.TRAILING -> if (i > 0) atr?.get(i - 1)?.let { stopp = p - ausstieg.wert * it }
                            AusstiegArt.ZIEL -> {
                                stopp = p * (1.0 - ZIEL_STOPP / 100.0)
                                zielKurs = p * (1.0 + ausstieg.wert * ZIEL_STOPP / 100.0)
                            }
                            else -> Unit
                        }
                    }
                }
            } else if (stueck > 0 && (!soll || (ausstieg.art == AusstiegArt.ZEIT && i - kaufTag >= ausstieg.wert))) {
                bar = stueck * Pricing.bid(p, kosten.spreadBps) - kosten.gebuehr
                if (bar > einsatz) gewinner++
                geschaefte?.add(Geschaeft(d.kerzen[kaufTag].t, d.kerzen[i].t, bar / einsatz - 1.0, i - kaufTag))
                stueck = 0.0
                gesperrt = soll
            }
            if (aus != null && stueck == 0.0 && bar > kosten.gebuehr) {
                val kurs = Pricing.ask(aus.eroeffnung(i), kosten.spreadBps)
                if (kurs > 0) {
                    park += (bar - kosten.gebuehr) / kurs
                    bar = 0.0
                }
            }
            if (mit && stueck > 0) {
                val raus = when {
                    stopp > 0 && d.tief[i] <= stopp -> minOf(stopp, p)
                    zielKurs > 0 && d.hoch[i] >= zielKurs -> max(zielKurs, p)
                    else -> 0.0
                }
                if (raus > 0) {
                    bar = stueck * Pricing.bid(raus, kosten.spreadBps) - kosten.gebuehr
                    if (bar > einsatz) gewinner++
                    geschaefte?.add(Geschaeft(d.kerzen[kaufTag].t, d.kerzen[i].t, bar / einsatz - 1.0, i - kaufTag))
                    stueck = 0.0
                    gesperrt = true
                } else if (atr != null) {
                    hoechster = max(hoechster, d.schluss[i])
                    atr[i]?.let { stopp = max(stopp, hoechster - ausstieg.wert * it) }
                }
            }
            if (stueck > 0) tageDrin++
            wert = bar + stueck * d.schluss[i] + (if (aus != null && park > 0.0) park * aus.schluss[i] else 0.0)
            werte?.set(i - von, wert)
            hoechst = max(hoechst, wert)
            if (hoechst > 0) rueckgang = max(rueckgang, 1.0 - wert / hoechst)
            val r = if (vorher > 0) wert / vorher - 1.0 else 0.0
            renditen?.add(r)
            vorher = wert
            anzahl++
            val abstand = r - mittel
            mittel += abstand / anzahl
            summeQuadrate += abstand * (r - mittel)
        }
        if (stueck > 0) {
            val erloes = stueck * Pricing.bid(d.schluss[bis - 1], kosten.spreadBps) - kosten.gebuehr
            if (erloes > einsatz) gewinner++
            geschaefte?.add(Geschaeft(d.kerzen[kaufTag].t, d.kerzen[bis - 1].t, erloes / einsatz - 1.0, bis - 1 - kaufTag, offen = true))
        }
        val endwert = wert
        val jahre = (d.kerzen[bis - 1].t - d.kerzen[von].t) / JAHR_MS
        val renditeJahr = if (jahre < 0.25) null else if (endwert <= 0) -1.0 else (endwert / start).pow(1.0 / jahre) - 1.0
        val abweichung = if (anzahl > 1) sqrt(summeQuadrate / (anzahl - 1)) else 0.0
        return Ergebnis(
            endwert = endwert,
            renditeJahr = renditeJahr,
            maxRueckgang = rueckgang,
            trades = trades,
            treffer = if (trades > 0) gewinner.toDouble() / trades else null,
            imMarkt = tageDrin.toDouble() / (bis - von),
            verlauf = if (werte == null) emptyList() else (von until bis).map { d.kerzen[it].t to werte[it - von] },
            renditen = renditen ?: emptyList(),
            sharpe = if (anzahl < 2 || abweichung < 1e-12) 0.0 else mittel / abweichung,
            jahre = jahre,
            geschaefte = geschaefte ?: emptyList(),
        )
    }

    // Regeln ohne Verkaufssignal (Kaufen und Halten, Zielvolatilitaet): kein zusaetzlicher Ausstieg, kein Signalwechsel.
    fun ohneSignal(r: Regel): Boolean = r.art == Art.HALTEN || r.art == Art.ZIELVOLA

    // Rechnung der Regel im Bereich: die Zielvolatilitaet mit wechselnder Positionsgroesse, alle anderen ueber ihre Lage z.
    fun rechnen(d: Daten, r: Regel, z: BooleanArray, von: Int, bis: Int, kosten: Kosten, ausstieg: Ausstieg = REGELAUSSTIEG, leicht: Boolean = false): Ergebnis? =
        if (r.art == Art.ZIELVOLA) Zielvolatilitaet.simulieren(d, r, von, bis, kosten, leicht = leicht)
        else simulieren(d, z, von, bis, kosten, leicht = leicht, ausstieg = ausstieg)

    // Mittel durch Standardabweichung je Zeitschritt; 0 ohne Schwankung.
    fun sharpe(r: List<Double>): Double {
        if (r.size < 2) return 0.0
        val m = r.average()
        val sd = sqrt(r.sumOf { (it - m) * (it - m) } / (r.size - 1))
        return if (sd < 1e-12) 0.0 else m / sd
    }

    // Grenze zwischen Lern- und Pruefzeitraum im Bereich ab bis Ende.
    fun teilung(ab: Int, n: Int): Int = ab + ((n - ab) * LERNANTEIL).toInt()

    // Aussagekraft eines Ergebnisses nach Zahl der Geschaefte und Laenge des Zeitraums. Die Grenzen sind Faustwerte.
    enum class Aussage { ZU_WENIG, BEGRENZT, BRAUCHBAR }

    const val MIN_GESCHAEFTE = 30
    const val MIN_JAHRE = 5.0
    private const val SEHR_WENIG_GESCHAEFTE = 10
    private const val SEHR_WENIG_JAHRE = 2.0

    fun aussage(trades: Int, jahre: Double): Aussage = when {
        trades < SEHR_WENIG_GESCHAEFTE || jahre < SEHR_WENIG_JAHRE -> Aussage.ZU_WENIG
        trades < MIN_GESCHAEFTE || jahre < MIN_JAHRE -> Aussage.BEGRENZT
        else -> Aussage.BRAUCHBAR
    }

    fun aussage(e: Ergebnis): Aussage = aussage(e.trades, e.jahre)

    // Kerzen mit wieder angelegten Ausschuettungen (Gesamtrendite). Ausschuettungen, die mehr als die Haelfte des
    // Kurses ausmachen, gelten als Datenfehler (etwa Pence gegen Pfund) und zaehlen nicht. Eine Regel, die am
    // Ex-Tag nicht im Markt ist, bekommt die Ausschuettung so nicht: der Faktor steckt im Kurs, nicht im Bestand.
    fun ertragsreihe(kerzen: List<Candle>, ausschuettungen: List<MarketApi.DividendEvent>): List<Candle> {
        if (kerzen.isEmpty() || ausschuettungen.isEmpty()) return kerzen
        val brauchbar = ausschuettungen.filter { e ->
            val kurs = Analysis.closeFrom(kerzen, e.exDate)?.close
            kurs != null && e.amount.isFinite() && e.amount > 0 && e.amount < 0.5 * kurs
        }
        return Analysis.thesaurierend(kerzen, brauchbar)
    }

    data class Bericht(val regel: Regel, val gesamt: Ergebnis, val lern: Ergebnis, val pruef: Ergebnis)

    // Alle Regeln ab Kerze ab: ganzer Zeitraum, Lernzeitraum (erste 70 %) und Pruefzeitraum (letzte 30 %),
    // jeder Teil mit frischem Startkapital. ausstieg gilt fuer alle Regeln ausser Kaufen und Halten.
    fun pruefen(kerzen: List<Candle>, ab: Int, kosten: Kosten, regeln: List<Regel> = REGELN, ausstieg: Ausstieg = REGELAUSSTIEG): List<Bericht> =
        pruefen(Daten(kerzen), ab, kosten, regeln, ausstieg)

    fun pruefen(d: Daten, ab: Int, kosten: Kosten, regeln: List<Regel> = REGELN, ausstieg: Ausstieg = REGELAUSSTIEG): List<Bericht> {
        val n = d.kerzen.size
        if (ab < 0 || n - ab < 20) return emptyList()
        val s = teilung(ab, n)
        return regeln.mapNotNull { r ->
            val z = ziel(d, r)
            val a = if (ohneSignal(r)) REGELAUSSTIEG else ausstieg
            val g = rechnen(d, r, z, ab, n, kosten, a) ?: return@mapNotNull null
            val l = rechnen(d, r, z, ab, s, kosten, a) ?: return@mapNotNull null
            val p = rechnen(d, r, z, s, n, kosten, a) ?: return@mapNotNull null
            Bericht(r, g, l, p)
        }
    }

    // Backtest-Lotterie

    enum class Familie { DURCHSCHNITT, KREUZ, RSI, ALLE }

    // Varianten einer Familie: Durchschnitte 5 bis 250 Tage in Fuenferschritten, Kreuze aus kurzem (5 bis 100) und
    // langem Durchschnitt (20 bis 250, mindestens 10 Tage laenger), RSI mit 7, 14, 21 Tagen und Schwellen 10 bis 40
    // bzw. 60 bis 90.
    fun varianten(f: Familie): List<Regel> = when (f) {
        Familie.DURCHSCHNITT -> (5..250 step 5).map { Regel(Art.SMA, it) }
        Familie.KREUZ -> (5..100 step 5).flatMap { k -> (20..250 step 10).filter { it >= k + 10 }.map { l -> Regel(Art.KREUZ, k, l) } }
        Familie.RSI -> listOf(7, 14, 21).flatMap { n -> (10..40 step 5).flatMap { a -> (60..90 step 5).map { b -> Regel(Art.RSI, a, b, n) } } }
        Familie.ALLE -> varianten(Familie.DURCHSCHNITT) + varianten(Familie.KREUZ) + varianten(Familie.RSI)
    }

    // anzahl: getestete Varianten. dsr: Deflated Sharpe Ratio der besten Variante (0 bis 1), sr0: erwartetes Maximum
    // der Sharpe-Quoten aus reinem Zufall, beide je Handelstag.
    data class Lotterie(
        val anzahl: Int,
        val beste: Regel,
        val besteLern: Ergebnis,
        val bestePruef: Ergebnis,
        val haltenLern: Ergebnis,
        val haltenPruef: Ergebnis,
        val dsr: Double,
        val sr0: Double,
        // Rendite pro Jahr jeder getesteten Variante im Lern- und im Pruefzeitraum (Parameter-Landschaft)
        val zellen: List<Zelle> = emptyList(),
    )

    // Rendite pro Jahr einer Variante im Lern- und im Pruefzeitraum; null bei unter einem Vierteljahr.
    data class Zelle(val regel: Regel, val lernJahr: Double?, val pruefJahr: Double?)

    // Nachbarn einer Variante auf dem Raster ihrer Familie: Durchschnitt a +- 5, Kreuz (a +- 5, b +- 10), RSI mit
    // gleicher Laenge n und a +- 5, b +- 5. Nur Varianten, die es in zellen gibt.
    fun nachbarn(zellen: List<Zelle>, r: Regel): List<Zelle> {
        val kandidaten: List<Regel> = when (r.art) {
            Art.SMA -> listOf(-5, 5).map { r.copy(a = r.a + it) }
            Art.KREUZ -> (-1..1).flatMap { i -> (-1..1).map { j -> r.copy(a = r.a + 5 * i, b = r.b + 10 * j) } }.filter { it != r }
            Art.RSI -> (-1..1).flatMap { i -> (-1..1).map { j -> r.copy(a = r.a + 5 * i, b = r.b + 5 * j) } }.filter { it != r }
            else -> emptyList()
        }
        val nach = zellen.associateBy { it.regel }
        return kandidaten.mapNotNull { nach[it] }
    }

    // Mittlerer Vorsprung der Nachbarn der gewaehlten Variante vor Kaufen und Halten (Rendite pro Jahr), im Lern- und
    // im Pruefzeitraum; null ohne Nachbarn mit Wert.
    fun nachbarVorsprung(l: Lotterie): Pair<Double?, Double?> {
        val n = nachbarn(l.zellen, l.beste)
        val lern = n.mapNotNull { z -> z.lernJahr?.let { a -> l.haltenLern.renditeJahr?.let { h -> a - h } } }
        val pruef = n.mapNotNull { z -> z.pruefJahr?.let { a -> l.haltenPruef.renditeJahr?.let { h -> a - h } } }
        return (if (lern.isEmpty()) null else lern.average()) to (if (pruef.isEmpty()) null else pruef.average())
    }

    // Testet alle Varianten im Lernzeitraum, waehlt die mit der hoechsten Sharpe-Quote und rechnet sie im
    // Pruefzeitraum nach. Die Varianten rechnen leicht (ohne verlauf und renditen), abbruch wird je Variante
    // aufgerufen und kann den Lauf abbrechen; nur die beste Variante wird voll gerechnet.
    fun lotterie(kerzen: List<Candle>, ab: Int, kosten: Kosten, familie: Familie, abbruch: () -> Unit = {}): Lotterie? =
        lotterie(Daten(kerzen), ab, kosten, familie, abbruch)

    fun lotterie(d: Daten, ab: Int, kosten: Kosten, familie: Familie, abbruch: () -> Unit = {}): Lotterie? {
        val n = d.kerzen.size
        if (ab < 0 || n - ab < 60) return null
        val s = teilung(ab, n)
        val alle = varianten(familie).mapNotNull { r ->
            abbruch()
            val z = ziel(d, r)
            val l = simulieren(d, z, ab, s, kosten, leicht = true) ?: return@mapNotNull null
            Kandidat(r, z, l, simulieren(d, z, s, n, kosten, leicht = true)?.renditeJahr)
        }
        if (alle.isEmpty()) return null
        val gewaehlt = alle.maxBy { it.lern.sharpe }
        val beste = gewaehlt.regel
        val z = gewaehlt.ziel
        val lern = simulieren(d, z, ab, s, kosten) ?: return null
        val pruef = simulieren(d, z, s, n, kosten) ?: return null
        val halten = ziel(d, Regel(Art.HALTEN))
        val hl = simulieren(d, halten, ab, s, kosten) ?: return null
        val hp = simulieren(d, halten, s, n, kosten) ?: return null
        val srs = alle.map { it.lern.sharpe }
        val varianz = varianz(srs)
        val sr0 = erwartetesMaximum(srs.size, varianz)
        val dsr = deflatedSharpe(lern.sharpe, sr0, lern.renditen.size, schiefe(lern.renditen), woelbung(lern.renditen))
        val zellen = alle.map { Zelle(it.regel, it.lern.renditeJahr, it.pruefJahr) }
        return Lotterie(srs.size, beste, lern, pruef, hl, hp, dsr, sr0, zellen)
    }

    private class Kandidat(val regel: Regel, val ziel: BooleanArray, val lern: Ergebnis, val pruefJahr: Double?)

    // Zufallsvergleich: dieselbe Regel, um eine zufaellige Zahl von Tagen zyklisch verschoben. Das Muster der Lagen
    // behaelt Zeit im Markt und (bis auf den Rand) die Zahl der Geschaefte und verliert den Bezug zu den Kursen.
    // verteilung: Rendite pro Jahr der verschobenen Varianten, aufsteigend. anteilBesser: Anteil der verschobenen
    // Varianten mit mindestens der Rendite der Regel.
    class Zufallsvergleich(
        val regelJahr: Double,
        val haltenJahr: Double?,
        val verteilung: DoubleArray,
        val anteilBesser: Double,
        val imMarkt: Double,
        val trades: Int,
        val laeufe: Int,
    )

    // Schreibt die um v Tage verschobenen Lagen des Bereichs start bis bis (ausschliesslich) in puffer. Am Tag i gilt
    // die Lage ziel[i - 1]; die Lagen der Tage start bis bis - 1 laufen im Kreis. start muss mindestens 1 sein.
    internal fun verschieben(ziel: BooleanArray, puffer: BooleanArray, start: Int, bis: Int, v: Int) {
        val m = bis - start
        for (i in start until bis) puffer[i - 1] = ziel[start - 1 + (i - start + v) % m]
    }

    // Verschiebt um mindestens ein Zehntel und hoechstens neun Zehntel des Zeitraums, damit keine Variante der
    // Regel selbst gleicht. Null bei weniger als 500 Tagen oder ohne Rendite pro Jahr. Mit festem seed
    // wiederholbar; abbruch wird je Lauf aufgerufen.
    fun zufallsvergleich(
        d: Daten, ziel: BooleanArray, von: Int, bis: Int, kosten: Kosten,
        laeufe: Int = 1000, seed: Long = 11L, abbruch: () -> Unit = {},
    ): Zufallsvergleich? {
        val start = max(von, 1)
        if (bis > d.schluss.size || ziel.size < d.schluss.size) return null
        val m = bis - start
        if (m < 500) return null
        val regel = simulieren(d, ziel, start, bis, kosten, leicht = true) ?: return null
        val regelJahr = regel.renditeJahr ?: return null
        val halten = simulieren(d, BooleanArray(d.schluss.size) { true }, start, bis, kosten, leicht = true)?.renditeJahr
        val rng = PortfolioTools.Rng(seed)
        val puffer = BooleanArray(d.schluss.size)
        val unter = m / 10
        val spanne = m - 2 * unter
        val werte = DoubleArray(laeufe)
        var n = 0
        repeat(laeufe) {
            abbruch()
            val v = unter + (rng.nextDouble() * spanne).toInt().coerceIn(0, spanne - 1)
            verschieben(ziel, puffer, start, bis, v)
            simulieren(d, puffer, start, bis, kosten, leicht = true)?.renditeJahr?.let { werte[n++] = it }
        }
        if (n == 0) return null
        val sortiert = werte.copyOf(n).also { it.sort() }
        val besser = sortiert.count { it >= regelJahr - 1e-12 }
        return Zufallsvergleich(regelJahr, halten, sortiert, besser.toDouble() / n, regel.imMarkt, regel.trades, n)
    }

    // Walk-Forward: Aus einem Lernfenster wird die Variante mit der hoechsten Sharpe-Quote gewaehlt, sie handelt
    // das folgende Pruefjahr, dann rueckt das Fenster vor. Das Signal vom Schluss des letzten Lerntags fuehrt zum
    // Handel am ersten Pruefjahrstag; so kennt die Wahl nichts aus dem Pruefzeitraum.
    // lern: gewaehlte Variante im Lernfenster, pruef und halten: im Pruefjahr, jeweils mit frischem Kapital.
    class Fenster(
        val lernVon: Int, val pruefVon: Int, val pruefBis: Int,
        val regel: Regel, val lern: Ergebnis, val pruef: Ergebnis, val halten: Ergebnis,
    )

    // zusammen: alle Pruefjahre aneinandergehaengt als eine durchgehende Rechnung mit echten Wechseln und Kosten
    // (das Kapital laeuft von Jahr zu Jahr weiter), halten im selben Zeitraum. besserJahre: Fenster, in denen die
    // gewaehlte Regel mehr Endwert hatte als Halten.
    class WalkForward(
        val fenster: List<Fenster>,
        val zusammen: Ergebnis,
        val halten: Ergebnis,
        val varianten: Int,
        val besserJahre: Int,
    ) {
        // Mittlere Sharpe-Quote der Pruefjahre im Verhaeltnis zur mittleren im Lernen; null, wenn Lernen nicht positiv.
        val pruefZuLern: Double?
            get() {
                val l = fenster.map { it.lern.sharpe }.average()
                val p = fenster.map { it.pruef.sharpe }.average()
                return if (l > 1e-9) p / l else null
            }
    }

    // Handelstage, die die Indikatoren (laengster Durchschnitt 250 Tage) vor dem ersten Lernfenster einschwingen
    const val EINSCHWINGEN = 250

    // Kuerzestes Pruefjahr am Ende der Reihe, das noch zaehlt
    private const val MIN_PRUEFTAGE = 126

    // Mindestzahl Kerzen im Lernfenster
    private const val MIN_LERNTAGE = 120

    // Rollierend (lernJahre Lernen, pruefJahre Pruefen, Schritt pruefJahre) oder verankert (Lernfenster waechst ab
    // dem ersten). Die Fenster richten sich nach Kalenderzeit, nicht nach Zahl der Kerzen, Luecken in den Kursen
    // verschieben sie also nicht. Null, wenn nicht einmal ein Fenster hineinpasst. fortschritt meldet 0 bis 1,
    // abbruch wird je Variante aufgerufen.
    fun walkForward(
        kerzen: List<Candle>, ab: Int, kosten: Kosten, familie: Familie,
        lernJahre: Int = 5, pruefJahre: Int = 1, verankert: Boolean = false,
        fortschritt: (Float) -> Unit = {}, abbruch: () -> Unit = {},
    ): WalkForward? = walkForward(Daten(kerzen), ab, kosten, familie, lernJahre, pruefJahre, verankert, fortschritt, abbruch)

    fun walkForward(
        d: Daten, ab: Int, kosten: Kosten, familie: Familie,
        lernJahre: Int = 5, pruefJahre: Int = 1, verankert: Boolean = false,
        fortschritt: (Float) -> Unit = {}, abbruch: () -> Unit = {},
    ): WalkForward? {
        val kerzen = d.kerzen
        val n = kerzen.size
        val start0 = ab + EINSCHWINGEN
        if (ab < 0 || start0 >= n) return null
        val lernMs = (lernJahre * JAHR_MS).toLong()
        val pruefMs = (pruefJahre * JAHR_MS).toLong()
        val t0 = kerzen[start0].t
        // Grenzen der Pruefjahre als Kerzenindizes: [von, bis)
        val grenzen = ArrayList<Triple<Int, Int, Int>>()
        var k = 0
        while (true) {
            val von = abIndex(kerzen, t0 + lernMs + k * pruefMs)
            if (von >= n) break
            val bis = minOf(n, abIndex(kerzen, t0 + lernMs + (k + 1) * pruefMs))
            if (bis - von < MIN_PRUEFTAGE) break
            val lernVon = if (verankert) start0 else abIndex(kerzen, t0 + k * pruefMs)
            if (von - lernVon >= MIN_LERNTAGE) grenzen += Triple(lernVon, von, bis)
            if (bis >= n) break
            k++
        }
        if (grenzen.isEmpty()) return null
        val regeln = varianten(familie)
        val ziele = regeln.map { abbruch(); ziel(d, it) }
        val haltenZiel = ziel(d, Regel(Art.HALTEN))
        val verbunden = BooleanArray(n)
        val schritte = grenzen.size * regeln.size
        var erledigt = 0
        val fenster = ArrayList<Fenster>()
        for ((lernVon, von, bis) in grenzen) {
            var besteIndex = -1
            var besteSharpe = Double.NEGATIVE_INFINITY
            for (i in regeln.indices) {
                abbruch()
                val e = simulieren(d, ziele[i], lernVon, von, kosten, leicht = true)
                if (e != null && e.sharpe > besteSharpe) { besteSharpe = e.sharpe; besteIndex = i }
                erledigt++
                if (erledigt % 16 == 0) fortschritt(erledigt.toFloat() / schritte)
            }
            if (besteIndex < 0) continue
            val z = ziele[besteIndex]
            val lern = simulieren(d, z, lernVon, von, kosten, leicht = true) ?: continue
            val pruef = simulieren(d, z, von, bis, kosten, leicht = true) ?: continue
            val halten = simulieren(d, haltenZiel, von, bis, kosten, leicht = true) ?: continue
            // Am Tag i gilt die Lage vom Schluss des Vortags
            for (i in (von - 1) until (bis - 1)) verbunden[i] = z[i]
            fenster += Fenster(lernVon, von, bis, regeln[besteIndex], lern, pruef, halten)
        }
        fortschritt(1f)
        if (fenster.isEmpty()) return null
        val erster = fenster.first().pruefVon
        val letzter = fenster.last().pruefBis
        // Fenster, die uebersprungen wurden, machen die Rechnung unterbrochen; sie laeuft dann nur ueber den Block
        // aneinandergrenzender Fenster ab dem ersten
        var ende = erster
        for (f in fenster) { if (f.pruefVon == ende) ende = f.pruefBis else break }
        val zusammen = simulieren(d, verbunden, erster, minOf(ende, letzter), kosten) ?: return null
        val halten = simulieren(d, haltenZiel, erster, minOf(ende, letzter), kosten) ?: return null
        val besser = fenster.count { it.pruef.endwert > it.halten.endwert }
        return WalkForward(fenster, zusammen, halten, regeln.size, besser)
    }

    // Monat (1 bis 12) und Jahr eines Zeitpunkts in Millisekunden seit 1970, UTC.
    fun monatJahr(ms: Long): Pair<Int, Int> {
        val tage = ms.floorDiv(86_400_000L) + 719_468L
        val era = tage.floorDiv(146_097L)
        val tagImEra = tage - era * 146_097L
        val jahrImEra = (tagImEra - tagImEra / 1_460 + tagImEra / 36_524 - tagImEra / 146_096) / 365
        val jahr = jahrImEra + era * 400
        val tagImJahr = tagImEra - (365 * jahrImEra + jahrImEra / 4 - jahrImEra / 100)
        val mp = (5 * tagImJahr + 2) / 153
        val monat = (if (mp < 10) mp + 3 else mp - 9).toInt()
        return monat to (if (monat <= 2) jahr + 1 else jahr).toInt()
    }

    // Deflated Sharpe Ratio

    private const val EULER_MASCHERONI = 0.5772156649015329

    // Erwartetes Maximum von n Sharpe-Quoten, wenn keine Variante wirklich etwas kann (Bailey und Lopez de Prado
    // 2014): Wurzel der Varianz der Quoten mal ((1 - g) x Quantil(1 - 1/n) + g x Quantil(1 - 1/(n e))), g die
    // Euler-Mascheroni-Konstante.
    fun erwartetesMaximum(n: Int, varianz: Double): Double {
        if (n < 2 || varianz <= 0) return 0.0
        val g = EULER_MASCHERONI
        return sqrt(varianz) * ((1 - g) * normInv(1.0 - 1.0 / n) + g * normInv(1.0 - 1.0 / (n * E)))
    }

    // Wahrscheinlichkeit, dass die echte Sharpe-Quote ueber sr0 liegt, mit t Renditen, ihrer Schiefe und Woelbung
    // (Normalverteilung: 0 und 3).
    fun deflatedSharpe(sr: Double, sr0: Double, t: Int, schiefe: Double, woelbung: Double): Double {
        if (t < 2) return 0.0
        val nenner = 1.0 - schiefe * sr + (woelbung - 1.0) / 4.0 * sr * sr
        if (nenner <= 1e-12) return if (sr > sr0) 1.0 else 0.0
        return Pricing.normCdf((sr - sr0) * sqrt((t - 1).toDouble()) / sqrt(nenner))
    }

    fun varianz(x: List<Double>): Double {
        if (x.size < 2) return 0.0
        val m = x.average()
        return x.sumOf { (it - m) * (it - m) } / (x.size - 1)
    }

    fun schiefe(x: List<Double>): Double {
        if (x.size < 3) return 0.0
        val m = x.average()
        val s2 = x.sumOf { (it - m) * (it - m) } / x.size
        if (s2 < 1e-24) return 0.0
        return x.sumOf { (it - m).pow(3) } / x.size / s2.pow(1.5)
    }

    // Woelbung (Kurtosis), nicht die Ueberschusswoelbung: Normalverteilung = 3.
    fun woelbung(x: List<Double>): Double {
        if (x.size < 4) return 3.0
        val m = x.average()
        val s2 = x.sumOf { (it - m) * (it - m) } / x.size
        if (s2 < 1e-24) return 3.0
        return x.sumOf { (it - m).pow(4) } / x.size / (s2 * s2)
    }

    // Quantil der Standardnormalverteilung nach Acklam (Fehler unter 1,2e-9).
    fun normInv(p: Double): Double {
        if (p <= 0.0) return Double.NEGATIVE_INFINITY
        if (p >= 1.0) return Double.POSITIVE_INFINITY
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val e = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
        val unten = 0.02425
        return when {
            p < unten -> {
                val q = sqrt(-2 * ln(p))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((e[0] * q + e[1]) * q + e[2]) * q + e[3]) * q + 1)
            }
            p <= 1 - unten -> {
                val q = p - 0.5
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
            }
            else -> {
                val q = sqrt(-2 * ln(1 - p))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((e[0] * q + e[1]) * q + e[2]) * q + e[3]) * q + 1)
            }
        }
    }

    // Erste Kerze ab dem Zeitpunkt von; 0, wenn die Reihe spaeter beginnt.
    fun abIndex(kerzen: List<Candle>, von: Long): Int {
        val i = kerzen.indexOfFirst { it.t >= von }
        return if (i < 0) kerzen.size else i
    }

    // Kerzen bis zum Ende bisMs und Index der ersten Kerze ab vonMs (fuer einen eigenen Zeitraum). Null ohne Kerzen im Zeitraum.
    fun ausschnitt(kerzen: List<Candle>, vonMs: Long, bisMs: Long): Pair<List<Candle>, Int>? {
        val ende = kerzen.indexOfLast { it.t <= bisMs } + 1
        if (ende <= 0) return null
        val teil = kerzen.subList(0, ende)
        val ab = abIndex(teil, vonMs)
        return if (ab >= teil.size) null else teil to ab
    }

    // Variante der Regel fuer die Einzelansicht: Trendfilter nur bei RSI und Bollinger, Monatsende nur beim Durchschnitt.
    fun variante(r: Regel, trendfilter: Boolean, monatsende: Boolean): Regel = when (r.art) {
        Art.RSI, Art.BOLLINGER -> r.copy(trend = if (trendfilter) TRENDFILTER else 0)
        Art.SMA -> r.copy(monatlich = monatsende)
        else -> r
    }

    // Vorsprung der Regel vor Kaufen und Halten in Rendite pro Jahr auf einer anderen Kursreihe im selben Zeitraum
    // (vonMs bis bisMs); null bei unter 300 Kerzen oder ohne Rendite pro Jahr.
    fun vorsprung(kerzen: List<Candle>, vonMs: Long, bisMs: Long, regel: Regel, ausstieg: Ausstieg, kosten: Kosten): Double? {
        if (kerzen.size < 300) return null
        val (teil, ab) = ausschnitt(kerzen, vonMs, bisMs) ?: return null
        val b = pruefen(teil, ab, kosten, listOf(Regel(Art.HALTEN), regel), ausstieg)
        if (b.size < 2) return null
        val halten = b[0].gesamt.renditeJahr ?: return null
        return b[1].gesamt.renditeJahr?.let { it - halten }
    }

    // Die anderen Werte fuer denselben Test: erst Depot, dann Watchlist, ohne Doppelte und ohne den gezeigten Wert.
    fun andereWerte(depot: List<String>, watchlist: List<String>, ohne: String, hoechstens: Int = 20): List<String> =
        (depot + watchlist).distinct().filter { it != ohne }.take(hoechstens)

    // Wie viele der Vorspruenge ueber null liegen.
    fun schlaegtHalten(vorspruenge: List<Double>): Int = vorspruenge.count { it > 0 }

    // Linien im Uebersichtschart (5.96): r wechselt zwischen sichtbar und ausgeblendet.
    fun umschalten(aus: Set<Regel>, r: Regel): Set<Regel> = if (r in aus) aus - r else aus + r

    // Die sichtbaren Berichte in der Reihenfolge der Liste.
    fun sichtbare(liste: List<Bericht>, aus: Set<Regel>): List<Bericht> = liste.filter { it.regel !in aus }

    // Duennt einen Verlauf fuer den Chart auf hoechstens max Punkte aus; der letzte bleibt.
    fun ausduennen(werte: List<Double>, max: Int = 240): List<Double> {
        if (werte.size <= max) return werte
        val schritt = (werte.size - 1).toDouble() / (max - 1)
        return (0 until max).map { werte[(it * schritt).toInt().coerceAtMost(werte.size - 1)] }.dropLast(1) + werte.last()
    }
}
