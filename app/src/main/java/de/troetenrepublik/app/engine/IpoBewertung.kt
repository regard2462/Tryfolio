package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.market.IpoKalender

// Einschaetzung eines Boersengangs (5.94): erklaerende Ampeln zu Risikomerkmalen aus den Daten des Kalenders, keine
// Kaufempfehlung. Dazu die Branche aus dem Namen, damit die Seite sagen kann, worauf es dort ankommt.
object IpoBewertung {

    enum class Merkmal { SPAC, GROESSE }

    // wert: Zahl zum Merkmal (Dollar), sonst null.
    data class Punkt(val merkmal: Merkmal, val ampel: Ampel, val wert: Double? = null)

    // Kleine Angebote unter 25 Mio. Dollar: wenig Beachtung, starke Schwankungen.
    const val KLEIN_DOLLAR = 25_000_000.0

    fun punkte(a: IpoKalender.Angebot): List<Punkt> {
        val aus = ArrayList<Punkt>()
        if (a.spac) aus += Punkt(Merkmal.SPAC, Ampel.GELB)
        a.volumen?.takeIf { it > 0 && it < KLEIN_DOLLAR }?.let { aus += Punkt(Merkmal.GROESSE, Ampel.GELB, it) }
        return aus
    }

    enum class Branche { KI, SOFTWARE, BIOTECH, BANK, ENERGIE, KONSUM, SPAC }

    private fun r(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    // Reihenfolge zaehlt: Mantel vor allem anderen, Biotech vor KI (KI in der Wirkstoffsuche bleibt Biotech).
    private val REGELN: List<Pair<Branche, Regex>> = listOf(
        Branche.SPAC to r("\\bacquisition\\s+(corp|co\\b|company|ltd|inc)|blank\\s+check|special\\s+purpose\\s+acquisition"),
        Branche.BIOTECH to r("therapeutic|biotech|bioscience|pharma|clinical[- ]stage|oncology|biopharm|drug\\s+candidate|neuroscience"),
        Branche.BANK to r("\\bbank\\b|banking|bancorp|bancshares|savings\\s+and\\s+loan"),
        Branche.KI to r("artificial\\s+intelligence|\\bAI\\b|machine\\s+learning|large\\s+language|frontier\\s+model|\\bGPUs?\\b|ai\\s+cloud"),
        Branche.ENERGIE to r("\\boil\\b|natural\\s+gas|\\benergy\\b|solar|nuclear|uranium|\\bpower\\s+generation|lithium"),
        Branche.SOFTWARE to r("software|\\bSaaS\\b|cloud|cyber\\s*security|platform\\s+for|data\\s+analytics|semiconductor"),
        Branche.KONSUM to r("consumer|restaurant|retail|apparel|wearable|beverage|brand"),
    )

    fun branche(name: String, beschreibung: String?): Branche? {
        val text = name + " " + beschreibung.orEmpty().take(3000)
        return REGELN.firstOrNull { it.second.containsMatchIn(text) }?.first
    }

    // Preisspanne der Quelle ("14.00-16.00", "$14.00 - $16.00", "16.00") als (kleinster, hoechster); Einzelpreis doppelt.
    fun spanne(preis: String?): Pair<Double, Double>? {
        val zahlen = preis?.split('-', '–')?.mapNotNull { IpoKalender.zahl(it) }.orEmpty()
        if (zahlen.isEmpty()) return null
        return zahlen.min() to zahlen.max()
    }

    // Lage eines Werts in der Spanne als Anteil von 0 bis 1.
    fun lage(wert: Double?, von: Double, bis: Double): Double? {
        if (wert == null || bis <= von) return null
        return ((wert - von) / (bis - von)).coerceIn(0.0, 1.0)
    }

    enum class Schritt { EINGEREICHT, PREIS, HANDEL }

    // erreicht: Index des letzten erreichten Schritts (-1 keiner), datumBei: Schritt, unter dem das Datum steht (-1 ohne).
    data class Zeitleiste(val erreicht: Int, val datumBei: Int, val zurueck: Boolean)

    // Schritte der Zeitleiste. Die Nasdaq meldet den Tag der Preisfestsetzung, der Handel beginnt meist am selben oder
    // naechsten Tag; das Datum steht deshalb beim Handel, wenn er schon laeuft.
    fun zeitleiste(status: IpoKalender.Status, tag: Long?, heute: Long): Zeitleiste {
        val z = when (status) {
            IpoKalender.Status.EINGEREICHT -> Zeitleiste(0, 0, false)
            IpoKalender.Status.ANGEKUENDIGT -> Zeitleiste(0, 1, false)
            IpoKalender.Status.PREIS ->
                if (tag != null && tag > heute) Zeitleiste(1, 1, false) else Zeitleiste(2, 2, false)
            IpoKalender.Status.ZURUECK -> Zeitleiste(1, 1, true)
        }
        return if (tag == null) z.copy(datumBei = -1) else z
    }

    // Entwicklung seit Ausgabe in Prozent; null, wenn eine Angabe fehlt oder nicht brauchbar ist.
    fun seitAusgabe(ausgabe: Double?, kurs: Double?): Double? {
        if (ausgabe == null || kurs == null || ausgabe <= 0 || kurs <= 0) return null
        val v = (kurs / ausgabe - 1.0) * 100.0
        return v.takeIf { it.isFinite() }
    }

    // Schlusskurse ab dem Tag abTag (Tage seit 1970) nach Zeit sortiert, hoechstens die letzten 250.
    fun verlaufAb(punkte: List<Pair<Long, Double>>, abTag: Long?): List<Double> {
        if (abTag == null) return emptyList()
        return punkte.filter { it.second.isFinite() && it.second > 0 && it.first / 86_400_000L >= abTag }
            .sortedBy { it.first }.map { it.second }.takeLast(250)
    }
}
