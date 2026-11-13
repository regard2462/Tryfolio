package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import de.troetenrepublik.app.engine.Optionsrechner.Art
import de.troetenrepublik.app.engine.Optionsrechner.Bein
import de.troetenrepublik.app.engine.Optionsrechner.Vorlage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// Reine Rechnung rund um den Optionsrechner: Erwartung je Vorlage, freies Verfallsdatum (Kontrakte aus den
// Nachbarterminen), Gewinnbereich fuer die Kernaussage und der Plan fuers Depot (Optionsschein je gekaufter Option,
// verkaufte Option je verkaufter, Aktienbein).
object OptionsrechnerPlan {

    private const val TAG_S = 86_400L
    private const val JAHR_S = 365.0 * TAG_S
    private val zone = java.time.ZoneId.of("Europe/Berlin")

    // ------------------------------------------------------------ Erwartung

    enum class Erwartung { STEIGT, FAELLT, SEITWAERTS, BEWEGUNG, ABSICHERN }

    // Jede Vorlage gehoert genau einer Erwartung, EIGENE keiner.
    fun erwartung(v: Vorlage): Erwartung? = when (v) {
        Vorlage.LONG_CALL, Vorlage.BULL_CALL_SPREAD, Vorlage.CASH_SECURED_PUT -> Erwartung.STEIGT
        Vorlage.LONG_PUT, Vorlage.BEAR_PUT_SPREAD -> Erwartung.FAELLT
        Vorlage.COVERED_CALL, Vorlage.IRON_CONDOR, Vorlage.BUTTERFLY -> Erwartung.SEITWAERTS
        Vorlage.STRADDLE, Vorlage.STRANGLE -> Erwartung.BEWEGUNG
        Vorlage.COLLAR, Vorlage.SCHUTZ_PUT -> Erwartung.ABSICHERN
        Vorlage.EIGENE -> null
    }

    fun vorlagen(e: Erwartung): List<Vorlage> = Vorlage.entries.filter { erwartung(it) == e }

    // ------------------------------------------------------------ Start mit einer verkauften Option

    // Vorbelegung aus einer Zeile „Optionen verkaufen“: der Call auf gehaltene Aktien oder der Put mit bereitgehaltenem
    // Geld zu diesem Basispreis und Verfall (Sekunden).
    data class Start(val call: Boolean, val strike: Double, val verfall: Long)

    fun startVorlage(s: Start): Vorlage = if (s.call) Vorlage.COVERED_CALL else Vorlage.CASH_SECURED_PUT

    fun startBeine(s: Start): List<Optionsrechner.Leg> =
        if (s.call) listOf(Optionsrechner.Leg(Art.AKTIE, 1, 0.0), Optionsrechner.Leg(Art.CALL, -1, s.strike))
        else listOf(Optionsrechner.Leg(Art.PUT, -1, s.strike))

    // Der Start gilt nur, wenn die Kette den Kontrakt fuehrt.
    fun startPasst(s: Start?, kontrakte: List<Kontrakt>): Boolean =
        s != null && kontrakte.any { it.verfall == s.verfall && it.call == s.call && it.strike == s.strike }

    // ------------------------------------------------------------ Freies Verfallsdatum

    // Verfallstermine, die noch nicht vorbei sind (Sekunden, aufsteigend).
    fun termine(kontrakte: List<Kontrakt>, gelistet: List<Long>, jetztS: Long): List<Long> =
        (kontrakte.map { it.verfall } + gelistet).filter { it > jetztS - TAG_S / 2 }.distinct().sorted()

    // Implizite Volatilitaet eines Basispreises zu einem Datum zwischen zwei Terminen: die Varianz (Volatilitaet im
    // Quadrat mal Laufzeit) wird ueber die Zeit linear verbunden. Ausserhalb der Termine gilt der naechste.
    // null, wenn der Basispreis in keinem Termin eine Volatilitaet hat.
    fun ivFrei(kontrakte: List<Kontrakt>, call: Boolean, strike: Double, verfall: Long, jetztS: Long): Double? {
        val ks = kontrakte.filter { it.call == call && it.strike == strike && it.iv in 0.01..5.0 && it.verfall > jetztS }
            .groupBy { it.verfall }.map { (v, l) -> v to l.map { it.iv }.average() }
        if (ks.isEmpty()) return null
        val unten = ks.filter { it.first <= verfall }.maxByOrNull { it.first }
        val oben = ks.filter { it.first > verfall }.minByOrNull { it.first }
        if (unten != null && oben != null) {
            val t1 = (unten.first - jetztS) / JAHR_S
            val t2 = (oben.first - jetztS) / JAHR_S
            val t = max((verfall - jetztS) / JAHR_S, 1.0 / 365.0)
            val w1 = unten.second * unten.second * t1
            val w2 = oben.second * oben.second * t2
            val w = w1 + (w2 - w1) * (t - t1) / (t2 - t1)
            return sqrt(max(w, 1e-12) / t)
        }
        return (unten ?: oben ?: ks.minBy { abs(it.first - verfall) }).second
    }

    // Kontrakte fuer ein Datum ohne Boersentermin: Basispreise vom naechstgelegenen Termin, Volatilitaet je Basispreis
    // aus ivFrei, Preis nach Black-Scholes (letzter Preis). Leer ohne Termine.
    fun frei(kurs: Double, kontrakte: List<Kontrakt>, verfall: Long, jetztS: Long): List<Kontrakt> {
        val gelistet = kontrakte.map { it.verfall }.filter { it > jetztS }.distinct()
        val nah = gelistet.minByOrNull { abs(it - verfall) } ?: return emptyList()
        val basis = kontrakte.filter { it.verfall == nah }
        val ersatz = Optionsrechner.ivAmGeld(kurs, kontrakte, nah) ?: 0.30
        val t = max(Optionsrechner.tage(verfall, jetztS), 0) / 365.0
        return basis.map { it.call to it.strike }.distinct().mapNotNull { (call, strike) ->
            val iv = ivFrei(kontrakte, call, strike, verfall, jetztS) ?: ersatz
            val preis = Pricing.blackScholes(kurs, strike, t, iv, call).price
            if (preis <= 0.0) null else Kontrakt(call, strike, verfall, preis, 0.0, 0.0, 0L, 0L, iv)
        }
    }

    // Frueheste und spaeteste waehlbare Tage fuer ein eigenes Datum (Sekunden, Mitternacht UTC): ab morgen bis zum
    // spaetesten gelisteten Termin plus zwei Jahre.
    fun datumsgrenzen(termine: List<Long>, jetztS: Long): Pair<Long, Long> {
        val morgen = (jetztS / TAG_S + 1) * TAG_S
        val letzter = termine.maxOrNull() ?: jetztS
        return morgen to (letzter / TAG_S + 730) * TAG_S
    }

    // ------------------------------------------------------------ Kernaussage

    sealed interface Gewinn {
        data class Ueber(val a: Double) : Gewinn
        data class Unter(val a: Double) : Gewinn
        data class Zwischen(val a: Double, val b: Double) : Gewinn
        data class Ausserhalb(val a: Double, val b: Double) : Gewinn
        data object Immer : Gewinn
        data object Nie : Gewinn
    }

    // Kursbereiche bei Verfall, in denen die Strategie im Gewinn liegt.
    fun gewinnbereich(beine: List<Bein>, roots: List<Double> = Optionsrechner.breakEven(beine)): Gewinn {
        val punkte = listOf(0.0) + roots
        val positiv = ArrayList<Pair<Double, Double?>>()
        for (i in punkte.indices) {
            val von = punkte[i]
            val bis = punkte.getOrNull(i + 1)
            val probe = if (bis == null) max(von * 1.5, von + 1.0) else (von + bis) / 2
            if (Optionsrechner.guvVerfall(beine, probe) > 1e-9) {
                val letzt = positiv.lastOrNull()
                if (letzt != null && letzt.second == von) positiv[positiv.size - 1] = letzt.first to bis else positiv += von to bis
            }
        }
        return when {
            positiv.isEmpty() -> Gewinn.Nie
            positiv.size == 1 -> {
                val (von, bis) = positiv[0]
                when {
                    von <= 0.0 && bis == null -> Gewinn.Immer
                    von <= 0.0 -> Gewinn.Unter(bis!!)
                    bis == null -> Gewinn.Ueber(von)
                    else -> Gewinn.Zwischen(von, bis)
                }
            }
            else -> {
                val a = positiv[0]
                val b = positiv.last()
                if (a.first <= 0.0 && b.second == null) Gewinn.Ausserhalb(a.second!!, b.first) else Gewinn.Zwischen(a.first, a.second ?: b.first)
            }
        }
    }

    // ------------------------------------------------------------ Kauf

    // Ein Teil der Strategie fuers Depot: stueck sind Aktien bzw. Optionen (Kontrakte mal 100), verkauft negativ.
    data class Teil(val art: Art, val stueck: Int, val strike: Double, val verfall: Long, val iv: Double, val preis: Double) {
        val verkauft: Boolean get() = stueck < 0
    }

    sealed interface Plan {
        data object Keiner : Plan
        // Verkaufte Aktien: Leerverkauf fuehrt das Depot nicht.
        data object Leerverkauf : Plan
        data class Kaufbar(val teile: List<Teil>) : Plan
    }

    const val STUECK_JE_KONTRAKT = 100

    // Zeitpunkt (ms), bis zu dem der Schein gehandelt wird: 17:30 Uhr am Verfallstag.
    fun ablauf(verfall: Long): Long =
        java.time.LocalDate.ofEpochDay(verfall / TAG_S).atTime(17, 30).atZone(zone).toInstant().toEpochMilli()

    // Volatilitaet, bei der die Formel den Preis des Kontrakts trifft (Halbierung). null, wenn Kurs, Preis oder
    // Restlaufzeit fehlen oder der Preis unter dem inneren Wert oder ueber dem Kurs liegt.
    internal fun passendeVola(teil: Teil, spot: Double, jahre: Double): Double? {
        if (!(spot > 0.0) || !(teil.preis > 0.0) || !(jahre > 0.0)) return null
        val call = teil.art == Art.CALL
        fun preis(v: Double) = Pricing.blackScholes(spot, teil.strike, jahre, v, call).price
        var lo = 0.01
        var hi = 5.0
        if (preis(lo) > teil.preis || preis(hi) < teil.preis) return null
        repeat(60) {
            val m = (lo + hi) / 2
            if (preis(m) < teil.preis) lo = m else hi = m
        }
        return (lo + hi) / 2
    }

    // Plan der Strategie. Keiner: kein Preis, kein Kontrakt, Verfall vorbei oder Waehrung nicht passend.
    // Leerverkauf: verkauft Aktien. Verkaufte Optionen legt das Depot als Stillhalterposition an (Deckung prueft
    // StillhalterDepot beim Anlegen).
    fun plan(beine: List<Bein>?, verfall: Long, jetztMs: Long, waehrungPasst: Boolean): Plan {
        if (beine.isNullOrEmpty() || !waehrungPasst) return Plan.Keiner
        if (ablauf(verfall) <= jetztMs) return Plan.Keiner
        if (beine.any { it.art != Art.AKTIE && !(it.preis > 0.0) }) return Plan.Keiner
        if (beine.any { it.art == Art.AKTIE && it.menge < 0 }) return Plan.Leerverkauf
        return Plan.Kaufbar(beine.map { Teil(it.art, it.menge * STUECK_JE_KONTRAKT, it.strike, verfall, it.iv, it.preis) })
    }

    // Gruppe fuer die zusammen angelegten Beine: kurz und eindeutig je Zeitpunkt.
    fun gruppe(jetztMs: Long): String = "G" + jetztMs.toString(36).uppercase()

    // Teile, die eine Gruppe bilden: mehr als eine Option oder eine verkaufte. Eine einzelne gekaufte Option geht ueber
    // die Kaufseite des Scheins.
    fun gruppiert(teile: List<Teil>): Boolean = teile.count { it.art != Art.AKTIE } > 1 || teile.any { it.art != Art.AKTIE && it.verkauft }

    // Optionsschein zu einer gekauften Option bzw. verkaufte Option: gleicher Basiswert, Basispreis und Verfall,
    // Volatilitaet des Kontrakts, Bezugsverhaeltnis 1 (ein Stueck je Aktie, 100 je Kontrakt).
    // spot: Kurs des Basiswerts, zu dem der Rechner gerechnet hat. Mit ihm wird die Volatilitaet so gewaehlt, dass der
    // Schein zum Preis des Kontrakts startet und die Kaufseite denselben Einsatz zeigt wie der Rechner.
    // gruppe: Beine einer zusammen angelegten Strategie tragen sie in Produkt und Kennung, damit sie als eigene Positionen
    // erkennbar bleiben; verkaufte Optionen haben zusaetzlich eine eigene Kennung.
    fun optionsschein(symbol: String, name: String, ccy: String, teil: Teil, jetztMs: Long, spot: Double = 0.0, gruppe: String = ""): Derivative {
        val kind = if (teil.art == Art.CALL) DerivativeKind.CALL else DerivativeKind.PUT
        val expiry = ablauf(teil.verfall)
        val vol = passendeVola(teil, spot, (expiry - jetztMs) / (JAHR_S * 1000.0)) ?: teil.iv
        val zusatz = gruppe + if (teil.verkauft) "V" else ""
        return Derivative(
            id = Derivatives.idOf(kind, symbol, teil.strike, expiry, 1.0, zusatz),
            underlying = symbol, underlyingName = name, kind = kind,
            strike = teil.strike, ratio = 1.0, expiry = expiry, vol = vol,
            createdAt = jetztMs, ccy = ccy, issuer = Derivatives.HOUSE,
            verkauft = teil.verkauft, gruppe = gruppe, ivKontrakt = true,
        )
    }
}
