package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.IpoKalender.Status

// Rechnungen des Detailblatts eines Boersengangs (0.78): grosser Wert im Kopf, Abstand zum Termin, Groesse gegen die
// anderen Angebote des Kalenders, erster Handelstag der juengsten Boersengaenge und die Kurse seit der Erstnotiz.
// Alles aus den Daten, die die Liste schon geladen hat.
object IpoBlatt {

    private const val TAG_MS = 86_400_000L

    // Vergleich und Verteilung erst ab so vielen anderen Angeboten.
    const val MIN_VERGLEICH = 5

    enum class WertArt { KURS, AUSGABE, MITTE, PREIS }

    // Grosser Wert im Kopf: Kurs nach der Erstnotiz (seit: Prozent gegen den Ausgabepreis), sonst Ausgabepreis, Mitte
    // der Spanne oder der eine genannte Preis.
    data class Kopfwert(val art: WertArt, val wert: Double, val seit: Double? = null)

    fun kopfwert(a: IpoKalender.Angebot, kurs: Double?): Kopfwert? {
        val preis = a.preisZahl?.takeIf { it.isFinite() && it > 0 }
        if (a.status == Status.PREIS) {
            if (kurs != null && kurs.isFinite() && kurs > 0) return Kopfwert(WertArt.KURS, kurs, IpoBewertung.seitAusgabe(preis, kurs))
            return preis?.let { Kopfwert(WertArt.AUSGABE, it) }
        }
        if (preis == null) return null
        val spanne = IpoBewertung.spanne(a.preis)
        return if (spanne != null && spanne.first < spanne.second) Kopfwert(WertArt.MITTE, preis) else Kopfwert(WertArt.PREIS, preis)
    }

    // Tage vom heutigen Tag bis zum Tag des Angebots: positiv in der Zukunft, negativ in der Vergangenheit.
    fun tageBis(tag: Long?, heute: Long): Long? = tag?.let { it - heute }

    // Gleiche Art: Mantelgesellschaften nur gegen Mantelgesellschaften, alle anderen untereinander.
    private fun gleicheArt(a: IpoKalender.Angebot, b: IpoKalender.Angebot) = a.spac == b.spac

    // Volumen, das ein Angebotsvolumen ist (kein Platzhalter des Prospekts).
    private fun echtesVolumen(a: IpoKalender.Angebot): Double? =
        a.volumen?.takeIf { it.isFinite() && it > 0 && !IpoUebersicht.platzhalter(a) }

    // rang: Platz dieses Angebots nach Volumen unter allen verglichenen (1 = groesstes), anzahl mit diesem Angebot.
    const val AUSSER_SKALA_AB = 20.0

    data class Groesse(
        val volumen: Double, val median: Double, val groesstes: Double, val groesstesName: String, val rang: Int, val anzahl: Int,
    ) {
        // Ein Riesenangebot unter den anderen wuerde dieses Angebot und den Median auf einen Punkt druecken: dann
        // messen sich nur diese beiden, das groesste steht als Zahl ohne Balken.
        val ausserSkala get() = groesstes > AUSSER_SKALA_AB * maxOf(volumen, median)
        // Laenge der Balken von 0 bis 1 gegen den groessten der gezeigten Werte
        private val skala get() = if (ausserSkala) maxOf(volumen, median) else maxOf(volumen, groesstes)
        val anteil get() = volumen / skala
        val anteilMedian get() = median / skala
        val anteilGroesstes get() = (groesstes / skala).coerceAtMost(1.0)
    }

    // Volumen dieses Angebots gegen die anderen anstehenden, eingereichten und gestarteten derselben Art. null ohne
    // eigenes Volumen oder mit weniger als MIN_VERGLEICH anderen.
    fun groesse(a: IpoKalender.Angebot, alle: List<IpoKalender.Angebot>): Groesse? {
        val eigen = echtesVolumen(a) ?: return null
        val andere = alle.filter { it.deal != a.deal && it.status != Status.ZURUECK && gleicheArt(a, it) }
            .mapNotNull { b -> echtesVolumen(b)?.let { b to it } }
        if (andere.size < MIN_VERGLEICH) return null
        val gross = andere.maxWith(compareBy<Pair<IpoKalender.Angebot, Double>> { it.second }.thenByDescending { it.first.name })
        return Groesse(
            volumen = eigen, median = median(andere.map { it.second }), groesstes = gross.second, groesstesName = gross.first.name,
            rang = 1 + andere.count { it.second > eigen }, anzahl = andere.size + 1,
        )
    }

    fun median(werte: List<Double>): Double {
        val s = werte.sorted()
        if (s.isEmpty()) return Double.NaN
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }

    // werte: Ergebnis des ersten Handelstags in Prozent, aufsteigend; eigen: Stelle dieses Angebots darin oder -1.
    data class ErsteTage(val werte: List<Double>, val eigen: Int, val imPlus: Int, val median: Double) {
        val schwaechster get() = werte.first()
        val staerkster get() = werte.last()
    }

    // Erster Handelstag der gestarteten Angebote derselben Art. ersterTag: Ergebnis je Angebot (Schluessel deal), wie
    // die Liste es schon berechnet hat. null mit weniger als MIN_VERGLEICH Werten.
    fun ersteTage(a: IpoKalender.Angebot, alle: List<IpoKalender.Angebot>, ersterTag: Map<String, Double>): ErsteTage? {
        val paare = alle.filter { it.status == Status.PREIS && gleicheArt(a, it) }.distinctBy { it.deal }
            .mapNotNull { b -> ersterTag[b.deal]?.takeIf { it.isFinite() }?.let { b.deal to it } }
            .sortedWith(compareBy<Pair<String, Double>> { it.second }.thenBy { it.first })
        if (paare.size < MIN_VERGLEICH) return null
        val werte = paare.map { it.second }
        return ErsteTage(werte, paare.indexOfFirst { it.first == a.deal }, werte.count { it > 0 }, median(werte))
    }

    // ------------------------------------------------------------ Kurs nach dem Start

    // Abstaende zum Start in Tagen: ein Monat, ein halbes Jahr, ein Jahr.
    val FRISTEN = listOf(30, 182, 365)

    // Eine Frist erscheint erst ab so vielen Boersengaengen mit Kurs.
    const val MIN_VERLAUF = 10

    // So viele Monate reicht der Rueckblick; ein Jahr Abstand gibt es damit fuer die Boersengaenge eines Jahres.
    const val RUECKBLICK_MONATE = 24

    // Ein gestarteter Boersengang: Tag der Preisfestsetzung (Tage seit 1970), Ausgabepreis und seine Tageskurse.
    class Gestartet(val tag: Long, val preis: Double, val kerzen: List<Candle>)

    // Kurs gegen den Ausgabepreis in Prozent, tage nach dem Start. null, wenn die Frist noch nicht um ist, der erste
    // Handelstag in den Kursen fehlt (anderes Papier unter dem Kuerzel) oder der erste Kurs nicht zum Ausgabepreis
    // passt (die Kursreihe ist nach einer Aktienzusammenlegung umgerechnet, der Ausgabepreis nicht).
    fun nachStart(g: Gestartet, tage: Int, heute: Long): Double? {
        if (!g.preis.isFinite() || g.preis <= 0 || heute < g.tag + tage) return null
        val kerzen = g.kerzen.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
        val erste = kerzen.firstOrNull { it.t >= g.tag * TAG_MS } ?: return null
        if (erste.t >= (g.tag + 4) * TAG_MS) return null
        val start = erste.close / g.preis
        if (start <= 0.25 || start >= 4.0) return null
        val ziel = kerzen.firstOrNull { it.t >= (g.tag + tage) * TAG_MS } ?: return null
        if (ziel.t >= (g.tag + tage + 7) * TAG_MS) return null
        return (ziel.close / g.preis - 1.0) * 100.0
    }

    // unter: Anzahl unter dem Ausgabepreis, median: mittlere Entwicklung in Prozent.
    data class Frist(val tage: Int, val anzahl: Int, val unter: Int, val median: Double) {
        val anteilUnter get() = unter.toDouble() / anzahl
    }

    // Je Frist die Boersengaenge, deren Kurs dort bekannt ist; Fristen mit zu wenigen fehlen.
    fun verlauf(liste: List<Gestartet>, heute: Long): List<Frist> = FRISTEN.mapNotNull { f ->
        val w = liste.mapNotNull { nachStart(it, f, heute) }
        if (w.size < MIN_VERLAUF) null else Frist(f, w.size, w.count { it < 0 }, median(w))
    }

    // Tageskurse ab dem Tag der Erstnotiz (Tage seit 1970), nach Zeit sortiert.
    fun kerzenAb(kerzen: List<Candle>, abTag: Long?): List<Candle> {
        if (abTag == null) return emptyList()
        return kerzen.filter { it.close.isFinite() && it.close > 0 && it.t / TAG_MS >= abTag }.sortedBy { it.t }
    }
}

// Langfristige Zahlen aus der Forschung, fest hinterlegt: Jay R. Ritter (University of Florida), „Initial Public
// Offerings: Updated Long-run Statistics“, Fassung vom 5. Oktober 2026. US-Boersengaenge ohne Mantelgesellschaften,
// ohne Ausgabepreise unter 5 Dollar, ADRs, Fonds und Banken. Einmal im Jahr gegen die neue Fassung pruefen.
object IpoForschung {
    const val STAND = "2026-10-05"
    const val LINK = "https://site.warrington.ufl.edu/ritter/files/IPOs-long-run-returns-on-IPOs.pdf"

    // Tabelle 16e, Teil A: Rendite ueber drei Jahre ab dem Ausgabepreis samt Dividenden, Boersengaenge 1975 bis 2021.
    // Bis minus 50 Prozent: 3215, darueber bis null: 1939.
    const val DREI_VON = 1975
    const val DREI_BIS = 2021
    const val DREI_ANZAHL = 9195
    const val DREI_UNTER = 3215 + 1939
    const val DREI_MEDIAN = -16.6

    // Tabelle 20-1: erstes Jahr ab dem ersten Schlusskurs, Boersengaenge 1980 bis 2024, gegen Firmen gleicher Groesse.
    const val JAHR_VON = 1980
    const val JAHR_BIS = 2024
    const val JAHR_ANZAHL = 9254
    const val JAHR_IPO = 5.6
    const val JAHR_VERGLEICH = 11.4

    val dreiJahre get() = IpoBlatt.Frist(3 * 365, DREI_ANZAHL, DREI_UNTER, DREI_MEDIAN)
}
