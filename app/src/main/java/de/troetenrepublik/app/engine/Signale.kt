package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.market.Handelnde
import de.troetenrepublik.app.market.SecInsider

// Signale-Kasten auf der Wertpapierseite und Kennzahlen der Insiderliste (0.76). Reine Funktionen ohne Netz:
// Die Oberflaeche liefert die geladenen Daten und zeigt nur, was vorhanden ist.
object Signale {

    private const val TAG = 86_400_000L

    const val KONGRESS_TAGE = 90
    const val STUFUNG_TAGE = 30

    const val INSIDER_TAGE = 90

    // Letzter Tag, den die Insider-Daten abdecken (JJJJ-MM-TT): ihr Stand, sonst die juengste Meldung, hoechstens heute.
    fun datenEnde(stand: String, juengste: String?, heute: String): String = minOf(heute, stand.ifBlank { juengste ?: heute })

    // Fenster der Zeilen Insider und Kongress: 90 Tage bis zum Ende der Insider-Daten, dasselbe Fenster wie bei der
    // Konvergenz. Ohne Insider-Daten (ende null oder unlesbar) endet es heute.
    fun fenster(ende: String?, heute: java.time.LocalDate): Konvergenz.Fenster {
        val tag = ende?.let { runCatching { java.time.LocalDate.parse(it.trim().take(10)) }.getOrNull() }?.takeIf { !it.isAfter(heute) } ?: heute
        return Konvergenz.fenster(tag)
    }

    // Erster Tag des Fensters der Insider-Zeile (JJJJ-MM-TT, Handelstag): bei US-Meldungen 90 Tage vor dem Ende der
    // Insider-Daten (secEnde, wie bei der Konvergenz), bei den uebrigen Registern 90 Tage vor heute.
    fun insiderAb(secEnde: String?, heute: java.time.LocalDate): String =
        if (secEnde != null) fenster(secEnde, heute).ab else heute.minusDays(INSIDER_TAGE.toLong()).toString()

    // Insider-Trend der Zeile: US-Meldungen zuerst, dann BaFin, dann die Register weiterer Laender; je Quelle nur, wenn
    // sie Geschaefte des Werts fuehrt und im Fenster ein Kauf oder Verkauf liegt. Kasten, Waage, Widget und die
    // Blaetter dahinter rechnen mit dieser Funktion.
    fun insider(
        sec: List<SecInsider.Geschaeft>?, bafin: List<de.troetenrepublik.app.market.Bafin.Geschaeft>?,
        welt: List<de.troetenrepublik.app.market.EuInsider.Geschaeft>?, secEnde: String?, heute: java.time.LocalDate,
    ): InsiderTrend.Ergebnis? =
        sec?.takeIf { it.isNotEmpty() }?.let { SecInsider.trend(it, insiderAb(secEnde, heute)) }
            ?: bafin?.takeIf { it.isNotEmpty() }?.let { de.troetenrepublik.app.market.Bafin.trend(it, insiderAb(null, heute)) }
            ?: welt?.takeIf { it.isNotEmpty() }?.let { de.troetenrepublik.app.market.EuInsider.trend(it, insiderAb(null, heute)) }

    data class Kongress(val kaeufe: Int, val verkaeufe: Int)

    // Geschaefte von Abgeordneten und Senatoren, die im Fenster oeffentlich wurden (Tag der Meldung, wie bei der
    // Konvergenz: bekannt wird ein Geschaeft erst mit der Meldung); null ohne Geschaeft.
    fun kongress(eintraege: List<Handelnde.Eintrag>, f: Konvergenz.Fenster): Kongress? {
        val g = kongressGeschaefte(eintraege, f)
        val k = g.count { it.kauf }
        val v = g.count { it.verkauf }
        return if (k + v == 0) null else Kongress(k, v)
    }

    // Die Geschaefte hinter der Kongress-Zeile, neueste Meldung zuerst.
    fun kongressGeschaefte(eintraege: List<Handelnde.Eintrag>, f: Konvergenz.Fenster): List<de.troetenrepublik.app.market.Kongress.Geschaeft> =
        eintraege.filter { it.gruppe == Handelnde.Gruppe.HAUS || it.gruppe == Handelnde.Gruppe.SENAT }
            .flatMap { it.geschaefte }.filter { (it.kauf || it.verkauf) && it.oeffentlich in f.abMs..f.bisMs }.sortedByDescending { it.oeffentlich }

    data class Stufen(val hoch: Int, val ab: Int)

    // Hoch- und Abstufungen der letzten tage Tage; Bestaetigungen und neutrale Erstbewertungen zaehlen nicht.
    fun stufungen(urteile: List<Analystenbilanz.Urteil>, jetzt: Long, tage: Int = STUFUNG_TAGE): Stufen? {
        val ab = jetzt - tage * TAG
        val r = urteile.filter { it.zeit in ab..jetzt }.mapNotNull { Analystenbilanz.richtung(it) }
        val hoch = r.count { it > 0 }
        val runter = r.count { it < 0 }
        return if (hoch + runter == 0) null else Stufen(hoch, runter)
    }

    // Auffaellige Optionen: Zahl, Aufteilung nach Calls und Puts, Summe der Praemien (Dollar).
    data class Optionen(val anzahl: Int, val calls: Int, val puts: Int, val praemie: Double)

    fun optionen(e: Optionsanalyse.Ergebnis): Optionen? {
        if (e.auffaellig.isEmpty()) return null
        val c = e.auffaellig.count { it.kontrakt.call }
        return Optionen(e.auffaellig.size, c, e.auffaellig.size - c, e.auffaellig.sumOf { it.praemie })
    }

    // ------------------------------------------------------------ Insiderliste

    // Kurs und Marktwert in US-Dollar aus einer Kursantwort in Euro (nativePrice zu price ist der Wechselkurs);
    // null, wenn der Wert nicht in Dollar notiert ist.
    data class Dollar(val kurs: Double, val marktwert: Double)

    fun dollar(q: Quote?): Dollar? {
        if (q == null || q.nativeCurrency != "USD" || q.price <= 0 || q.nativePrice <= 0) return null
        return Dollar(q.nativePrice, q.marketCap * q.nativePrice / q.price)
    }

    // Kursaenderung seit dem Geschaeft (0,1 = +10 %); null ohne Preis.
    fun seitKauf(preis: Double, kurs: Double): Double? = if (preis > 0 && kurs > 0) kurs / preis - 1 else null

    // Wert des Geschaefts am Marktwert des Unternehmens (0,001 = 0,1 %); null ohne Marktwert.
    fun vomMarktwert(wert: Double, marktwert: Double): Double? = if (wert > 0 && marktwert > 0) wert / marktwert else null

    // Anteil der Verkaeufe aus vorab festgelegten Plaenen (Rule 10b5-1) an der Verkaufssumme; null ohne Verkauf.
    fun planAnteil(l: List<SecInsider.Geschaeft>): Double? {
        val v = l.filter { !it.kauf && it.wert > 0 }
        val summe = v.sumOf { it.wert }
        return if (summe <= 0) null else v.filter { it.plan }.sumOf { it.wert } / summe
    }
}
