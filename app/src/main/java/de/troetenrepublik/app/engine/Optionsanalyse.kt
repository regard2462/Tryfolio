package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.log10

// Optionsanalyse aus der frei abrufbaren Optionskette, Stand letzter Handelstag, offene Kontrakte vom Vortag.
// Ohne Orderbuch laesst sich nur schaetzen, ob gekauft oder verkauft wurde, am letzten Kurs nahe Brief oder Geld.
object Optionsanalyse {

    // verfall in Sekunden wie bei Yahoo, iv als Anteil (0,38 = 38 %). handel: Zeitpunkt des letzten Handels in
    // Sekunden, 0 wenn die Quelle ihn nicht nennt.
    data class Kontrakt(
        val call: Boolean,
        val strike: Double,
        val verfall: Long,
        val letzter: Double,
        val geld: Double,
        val brief: Double,
        val umsatz: Long,
        val offen: Long,
        val iv: Double,
        val handel: Long = 0L,
    ) {
        val mitte: Double get() = if (geld > 0 && brief > 0) (geld + brief) / 2 else letzter
    }

    // Umsatz der Calls und Puts am Tag des juengsten Handels; tag: dessen Zeitpunkt in ms, 0 wenn unbekannt.
    data class Umsatz(val calls: Long, val puts: Long, val tag: Long) {
        val gesamt: Long get() = calls + puts
        val putCall: Double? get() = if (calls > 0) puts.toDouble() / calls else null
    }

    // So viele Verfaelle zaehlen fuer den Optionsumsatz (Long/Short-Balken und Put/Call).
    const val UMSATZ_VERFAELLE = 6
    private const val UMSATZ_TAG_MS = 18 * 3_600_000L

    // Eine Rechnung fuer den Long/Short-Balken und das Put/Call-Verhaeltnis. posten: Call oder Put, Umsatz, Zeitpunkt des
    // letzten Handels in ms. Gezaehlt wird nur der Tag des juengsten Handels, aeltere Kontrakte tragen noch den Umsatz
    // eines frueheren Tages; nennt die Quelle keinen Zeitpunkt, zaehlen alle.
    fun tagesUmsatz(posten: List<Triple<Boolean, Long, Long>>): Umsatz {
        val mit = posten.filter { it.second > 0 }
        val juengster = mit.maxOfOrNull { it.third } ?: 0L
        val heute = if (juengster <= 0L) mit else mit.filter { it.third > 0 && juengster - it.third < UMSATZ_TAG_MS }
        return Umsatz(heute.filter { it.first }.sumOf { it.second }, heute.filter { !it.first }.sumOf { it.second }, juengster)
    }

    // Optionsumsatz einer Kette ueber die noch nicht verfallenen Kontrakte.
    fun umsatz(kontrakte: List<Kontrakt>, jetztS: Long = System.currentTimeMillis() / 1000): Umsatz =
        tagesUmsatz(kontrakte.filter { it.verfall > jetztS - TAG_S }.map { Triple(it.call, it.umsatz, it.handel * 1000L) })

    // Grundregel fuer auffaellige Optionen (Signale, Karte „Optionen“, Werkzeug): Umsatz mindestens so hoch wie die
    // offenen Kontrakte, Verfall in hoechstens 60 Tagen, gehandelte Praemie ab 50.000 Dollar. Das Mindestvolumen legt
    // die jeweilige Stelle fest.
    const val AUFFAELLIG_VERHAELTNIS = 1.0
    const val AUFFAELLIG_TAGE = 60
    const val AUFFAELLIG_PRAEMIE = 50_000.0

    // Gehandelte Praemie des Tages in Dollar: Umsatz mal Mitte mal 100 Aktien je Kontrakt.
    fun praemie(k: Kontrakt): Double = k.umsatz * k.mitte * 100

    fun auffaellig(k: Kontrakt, jetztS: Long): Boolean =
        k.umsatz > 0 && k.verfall - jetztS <= AUFFAELLIG_TAGE * TAG_S &&
            k.umsatz.toDouble() / maxOf(k.offen, 1L) >= AUFFAELLIG_VERHAELTNIS && praemie(k) >= AUFFAELLIG_PRAEMIE

    enum class Seite { KAUF, VERKAUF, UNKLAR }

    data class Auffaellig(val kontrakt: Kontrakt, val praemie: Double, val verhaeltnis: Double, val seite: Seite, val punkte: Double)

    data class Ergebnis(
        val pcrUmsatz: Double?,
        val pcrOffen: Double?,
        val maxPain: Double?,
        val maxPainVerfall: Long?,
        val bewegung: Double?,
        val bewegungVerfall: Long?,
        // Implizite Volatilitaet fuer 30 Tage, zwischen den Verfaellen interpoliert (OptionsVola.iv30)
        val iv30: Double?,
        val auffaellig: List<Auffaellig>,
    )

    private const val TAG_S = 86_400L

    // Stillhalter (5.94): was das Schreiben eines Calls auf gehaltene Aktien (gedeckter Call) oder eines Puts mit
    // bereitgehaltenem Geld (gedeckter Put) bis zum Verfall einbringt. praemie ist der Geldkurs, zu dem sich verkaufen
    // laesst. rendite: Praemie je eingesetztem Betrag (Kurs bei Calls, Basispreis bei Puts), jahr dasselbe aufs Jahr
    // gerechnet. ausgeuebt: bei Calls die Rendite, wenn die Aktie zum Basispreis abgegeben wird. schwelle: Kurs am
    // Verfall, ab dem man ohne Gewinn dasteht (Kurs bzw. Basis minus Praemie). wahrscheinlich: Chance auf Ausuebung am
    // Verfall nach Black-Scholes mit der impliziten Volatilitaet des Kontrakts; null ohne brauchbare Volatilitaet.
    data class StillhalterZeile(
        val strike: Double, val praemie: Double, val rendite: Double, val jahr: Double,
        val ausgeuebt: Double?, val schwelle: Double, val wahrscheinlich: Double?,
    ) {
        // Chance, dass die Option wertlos verfaellt: Gegenstueck zur Ausuebung
        val wertlos: Double? get() = wahrscheinlich?.let { 1.0 - it }
    }

    data class Stillhalter(val verfall: Long, val tage: Int, val calls: List<StillhalterZeile>, val puts: List<StillhalterZeile>)

    // Verfall: der naechste zwischen 25 und 50 Tagen (ueblich fuer Stillhalter), sonst der erste ab 14 Tagen. Je Seite
    // bis zu vier Basispreise aus dem Geld, hoechstens 15 % vom Kurs entfernt, mit Geldkurs.
    fun stillhalter(kurs: Double, kontrakte: List<Kontrakt>, jetztS: Long = System.currentTimeMillis() / 1000, zins: Double = Pricing.RATE): Stillhalter? {
        if (kurs <= 0) return null
        val verfaelle = kontrakte.map { it.verfall }.filter { it > jetztS }.distinct().sorted()
        fun tage(v: Long) = ((v - jetztS) / TAG_S).toInt()
        val verfall = verfaelle.firstOrNull { tage(it) in 25..50 } ?: verfaelle.firstOrNull { tage(it) >= 14 } ?: return null
        val t = tage(verfall).coerceAtLeast(1)
        val jahre = t / 365.0
        fun chance(k: Kontrakt): Double? = ausuebung(kurs, k.strike, k.iv, k.call, jahre, zins)
        val am = kontrakte.filter { it.verfall == verfall && it.geld > 0 }
        val calls = am.filter { it.call && it.strike > kurs && it.strike <= kurs * 1.15 }.sortedBy { it.strike }.take(4).map { k ->
            val r = k.geld / kurs
            StillhalterZeile(k.strike, k.geld, r, r * 365 / t, (k.strike - kurs + k.geld) / kurs, kurs - k.geld, chance(k))
        }
        val puts = am.filter { !it.call && it.strike < kurs && it.strike >= kurs * 0.85 }.sortedByDescending { it.strike }.take(4).map { k ->
            val r = k.geld / k.strike
            StillhalterZeile(k.strike, k.geld, r, r * 365 / t, null, k.strike - k.geld, chance(k))
        }
        if (calls.isEmpty() && puts.isEmpty()) return null
        return Stillhalter(verfall, t, calls, puts)
    }

    // Chance auf Ausuebung am Verfall nach Black-Scholes (N(d2)), null ohne brauchbare Volatilitaet.
    fun ausuebung(kurs: Double, strike: Double, iv: Double, call: Boolean, jahre: Double, zins: Double = Pricing.RATE): Double? {
        if (!(iv > 0.01 && iv < 5) || kurs <= 0 || strike <= 0 || jahre <= 0) return null
        val sig = iv * kotlin.math.sqrt(jahre)
        val d2 = (kotlin.math.ln(kurs / strike) + (zins - iv * iv / 2) * jahre) / sig
        return if (call) Pricing.normCdf(d2) else Pricing.normCdf(-d2)
    }

    // Max Pain: Basispreis, bei dem der innere Wert aller Calls und Puts zusammen am kleinsten ist.
    fun maxPain(kontrakte: List<Kontrakt>): Double? {
        val strikes = kontrakte.map { it.strike }.distinct().sorted()
        if (strikes.size < 3 || kontrakte.all { it.offen == 0L }) return null
        return strikes.minByOrNull { s ->
            kontrakte.sumOf { k -> k.offen * if (k.call) maxOf(0.0, s - k.strike) else maxOf(0.0, k.strike - s) }
        }
    }

    // Kette ohne offene Kontrakte: die Quelle fuehrt sie ausserhalb der US-Handelszeit zeitweise mit 0. Dann sagt
    // Umsatz gegen offene Kontrakte nichts. Gilt, wenn weniger als ein Drittel der gehandelten Kontrakte welche nennt.
    fun ohneOffene(kontrakte: List<Kontrakt>): Boolean {
        val gehandelt = kontrakte.filter { it.umsatz > 0 }
        return gehandelt.isNotEmpty() && gehandelt.count { it.offen > 0 } * 3 < gehandelt.size
    }

    // Letzter Kurs am Brief heisst gekauft, am Geld verkauft, sonst unklar.
    fun seite(k: Kontrakt): Seite {
        if (k.geld <= 0 || k.brief <= 0 || k.brief < k.geld) return Seite.UNKLAR
        val spanne = k.brief - k.geld
        return when {
            k.letzter >= k.brief - 0.1 * spanne -> Seite.KAUF
            k.letzter <= k.geld + 0.1 * spanne -> Seite.VERKAUF
            else -> Seite.UNKLAR
        }
    }

    fun auswerten(kurs: Double, kontrakte: List<Kontrakt>, jetztS: Long = System.currentTimeMillis() / 1000): Ergebnis {
        val gueltig = kontrakte.filter { it.verfall > jetztS - TAG_S }
        val calls = gueltig.filter { it.call }
        val puts = gueltig.filter { !it.call }
        val co = calls.sumOf { it.offen }
        val po = puts.sumOf { it.offen }
        val termine = gueltig.map { it.verfall }.distinct().sorted()
        val naechster = termine.firstOrNull { it - jetztS >= 2 * TAG_S } ?: termine.firstOrNull()
        val amNaechsten = gueltig.filter { it.verfall == naechster }
        // Straddle am Geld: Call und Put mit dem Basispreis am naechsten zum Kurs.
        val atm = amNaechsten.map { it.strike }.distinct().minByOrNull { abs(it - kurs) }
        val bewegung = atm?.let { s ->
            val c = amNaechsten.firstOrNull { it.call && it.strike == s }?.mitte
            val p = amNaechsten.firstOrNull { !it.call && it.strike == s }?.mitte
            if (c != null && p != null && c > 0 && p > 0) c + p else null
        }
        // Eine implizite Volatilitaet fuer die Karte, den Block Volatilitaet und die Kursspanne: IV30 interpoliert
        val iv = if (kurs > 0) OptionsVola.iv30(OptionsVola.struktur(kurs, gueltig, jetztS).map { it.second }) else null
        // Auffaellig nach der Grundregel; das Mindestvolumen richtet sich hier nach dem ueblichen Umsatz der Kette.
        val umsaetze = gueltig.map { it.umsatz }.filter { it > 0 }.sorted()
        val median = if (umsaetze.isEmpty()) 0L else umsaetze[umsaetze.size / 2]
        val schwelle = maxOf(200L, minOf(500L, median * 5))
        val auffaellig = (if (ohneOffene(gueltig)) emptyList() else gueltig).filter { it.umsatz >= schwelle && auffaellig(it, jetztS) }.map { k ->
            val praemie = praemie(k)
            val r = k.umsatz.toDouble() / maxOf(k.offen, 1L)
            val abstand = if (kurs > 0) abs(k.strike / kurs - 1) else 0.0
            Auffaellig(k, praemie, r, seite(k), log10(praemie) * minOf(r, 10.0) * (1 + abstand))
        }.sortedByDescending { it.punkte }.take(3)
        return Ergebnis(
            pcrUmsatz = umsatz(gueltig, jetztS).putCall,
            pcrOffen = if (co > 0) po.toDouble() / co else null,
            maxPain = maxPain(amNaechsten),
            maxPainVerfall = naechster,
            bewegung = bewegung,
            bewegungVerfall = naechster,
            iv30 = iv,
            auffaellig = auffaellig,
        )
    }
}
