package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

// Levermann-Punkte (0.78): Checkliste nach Susan Levermann mit 13 Kriterien, je +1, 0 oder −1 Punkt. Schwelle der Methode: 4 Punkte
// bei grossen Werten, 7 bei mittleren und kleinen. Finanzwerte (Banken, Versicherer, Finanzdienstleister) ohne
// EBIT-Marge und mit niedrigerer Schwelle der Eigenkapitalquote. Ein Kriterium ohne Angabe zaehlt nicht. Reine Rechnung.
//
// Abweichungen von der Vorlage, weil die App nur diese Angaben laedt:
//  - KGV: das laufende KGV (Gewinn der letzten zwoelf Monate), ersatzweise das erwartete.
//  - KGV im Fuenfjahresschnitt: Kurs durch den mittleren Gewinn je Aktie der letzten bis zu fuenf Geschaeftsjahre
//    (GewinnSchnitt), ohne die Schaetzungen fuer das laufende und das naechste Jahr.
//  - Analystenmeinung: Durchschnittsnote von 1 (stark kaufen) bis 5 (stark verkaufen). Die Schwellen der dreistufigen
//    Skala (ab 2,5 und bis 1,5) entsprechen auf der fuenfstufigen 4,0 und 2,0.
//  - Gewinnrevisionen: Gewinnschaetzung je Aktie fuer das laufende Quartal heute gegen vor 30 Tagen.
object Levermann {

    enum class Kriterium {
        EK_RENDITE, EBIT_MARGE, EK_QUOTE, KGV, KGV_SCHNITT, ANALYSTEN, ZAHLEN, REVISIONEN, KURS6, KURS12, MOMENTUM, UMKEHR, GEWINNWACHSTUM,
    }

    enum class Groesse { GROSS, MITTEL_KLEIN }

    // Ab diesem Boersenwert in Euro gilt ein Wert als gross.
    const val GROSS_AB = 5e9

    const val KAUF_GROSS = 4
    const val KAUF_MITTEL_KLEIN = 7

    // Schwellen je Kriterium: ueber gut ein Punkt, unter schlecht ein Punkt Abzug (Prozent bzw. KGV).
    const val EK_RENDITE_GUT = 20.0
    const val EK_RENDITE_SCHLECHT = 10.0
    const val EBIT_MARGE_GUT = 12.0
    const val EBIT_MARGE_SCHLECHT = 6.0
    const val EK_QUOTE_GUT = 25.0
    const val EK_QUOTE_SCHLECHT = 15.0
    const val EK_QUOTE_FINANZ_GUT = 10.0
    const val EK_QUOTE_FINANZ_SCHLECHT = 5.0
    const val KGV_GUT = 12.0
    const val KGV_SCHLECHT = 16.0
    // Durchschnittsnote 1 bis 5: als Gegenzeichen gibt eine schlechte Note den Punkt
    const val NOTE_KAUFEN = 2.0
    const val NOTE_VERKAUFEN = 4.0
    // Bis zu so vielen Analysten gilt bei mittleren und kleinen Werten die Note ohne Umkehrung
    const val WENIG_ANALYSTEN = 5
    // Kurs am Tag der Quartalszahlen gegen den Index, in Prozentpunkten
    const val ZAHLEN_SCHWELLE = 1.0
    // Gewinnrevision, Kurs gegen 6 und 12 Monate, Gewinnwachstum: Prozent
    const val SCHWELLE = 5.0
    // Die Reaktion auf die Zahlen zaehlt nur, wenn sie hoechstens so viele Tage zurueckliegt
    const val ZAHLEN_MAX_TAGE = 120L

    // Alles in Prozent, null ohne Angabe. kgv: 0 oder negativ heisst Verlust. note: 1 bis 5. zahlen: Kurs am Tag der
    // letzten Quartalszahlen minus Index in Prozentpunkten. revision: Gewinnschaetzung gegen vor 30 Tagen.
    // umkehr: Kurs minus Index je Monat fuer die letzten drei abgeschlossenen Monate. boersenwert in Euro.
    data class Eingabe(
        val ekRendite: Double? = null,
        val ebitMarge: Double? = null,
        val ekQuote: Double? = null,
        val kgv: Double? = null,
        val kgvSchnitt: Double? = null,
        val note: Double? = null,
        val analysten: Int = 0,
        val zahlen: Double? = null,
        val revision: Double? = null,
        val kurs6: Double? = null,
        val kurs12: Double? = null,
        val umkehr: List<Double>? = null,
        val gewinnwachstum: Double? = null,
        val boersenwert: Double? = null,
        val finanzwert: Boolean = false,
    )

    // punkte null: keine Angabe, das Kriterium zaehlt nicht. wert: die Zahl hinter dem Punkt, wo es eine gibt.
    data class Zeile(val kriterium: Kriterium, val punkte: Int?, val wert: Double? = null)

    // moeglich: Zahl der Kriterien (13, bei Finanzwerten 12); bewertet: davon mit Angabe. kaufAb und kauf sind null,
    // wenn der Boersenwert fehlt. kauf gilt nur fuer die bewerteten Kriterien.
    data class Ergebnis(
        val punkte: Int, val bewertet: Int, val moeglich: Int, val groesse: Groesse?, val kaufAb: Int?, val zeilen: List<Zeile>,
    ) {
        val kauf: Boolean? get() = kaufAb?.let { punkte >= it }
    }

    fun groesse(boersenwert: Double?): Groesse? = when {
        boersenwert == null || !boersenwert.isFinite() || boersenwert <= 0 -> null
        boersenwert >= GROSS_AB -> Groesse.GROSS
        else -> Groesse.MITTEL_KLEIN
    }

    // +1 ueber gut, −1 unter schlecht, sonst 0.
    private fun hoch(v: Double?, gut: Double, schlecht: Double): Int? = v?.takeIf { it.isFinite() }?.let { if (it > gut) 1 else if (it < schlecht) -1 else 0 }

    // KGV: unter 12 ein Punkt, ueber 16 oder bei Verlust ein Punkt Abzug.
    fun kgvPunkte(kgv: Double?): Int? = kgv?.takeIf { it.isFinite() }?.let { if (it <= 0 || it > KGV_SCHLECHT) -1 else if (it < KGV_GUT) 1 else 0 }

    // Analystenmeinung als Gegenzeichen: Verkaufsempfehlungen geben den Punkt, Kaufempfehlungen den Abzug. Bei
    // mittleren und kleinen Werten mit hoechstens fuenf Analysten gilt die Note ohne Umkehrung.
    fun analystenPunkte(note: Double?, analysten: Int, groesse: Groesse?): Int? {
        if (note == null || !note.isFinite() || note < 1.0 || note > 5.0 || analysten <= 0 || groesse == null) return null
        val gegen = if (note >= NOTE_VERKAUFEN) 1 else if (note <= NOTE_KAUFEN) -1 else 0
        return if (groesse == Groesse.MITTEL_KLEIN && analysten <= WENIG_ANALYSTEN) -gegen else gegen
    }

    // Kursmomentum steigend: das Halbjahr gibt einen Punkt und das Jahr keinen (Trend dreht nach oben), umgekehrt Abzug.
    fun momentumPunkte(p6: Int?, p12: Int?): Int? {
        if (p6 == null || p12 == null) return null
        return if (p6 == 1 && p12 <= 0) 1 else if (p6 == -1 && p12 >= 0) -1 else 0
    }

    // Dreimonats-Umkehr, nur grosse Werte: in jedem der drei Monate schwaecher als der Index ein Punkt, in jedem staerker
    // ein Abzug. Mittlere und kleine Werte bekommen 0.
    fun umkehrPunkte(monate: List<Double>?, groesse: Groesse?): Int? {
        if (groesse == null) return null
        if (groesse == Groesse.MITTEL_KLEIN) return 0
        if (monate == null || monate.size != 3 || monate.any { !it.isFinite() }) return null
        return if (monate.all { it < 0 }) 1 else if (monate.all { it > 0 }) -1 else 0
    }

    fun berechne(e: Eingabe): Ergebnis {
        val g = groesse(e.boersenwert)
        val p6 = hoch(e.kurs6, SCHWELLE, -SCHWELLE)
        val p12 = hoch(e.kurs12, SCHWELLE, -SCHWELLE)
        val zeilen = buildList {
            add(Zeile(Kriterium.EK_RENDITE, hoch(e.ekRendite, EK_RENDITE_GUT, EK_RENDITE_SCHLECHT), e.ekRendite))
            if (!e.finanzwert) add(Zeile(Kriterium.EBIT_MARGE, hoch(e.ebitMarge, EBIT_MARGE_GUT, EBIT_MARGE_SCHLECHT), e.ebitMarge))
            add(Zeile(
                Kriterium.EK_QUOTE,
                if (e.finanzwert) hoch(e.ekQuote, EK_QUOTE_FINANZ_GUT, EK_QUOTE_FINANZ_SCHLECHT) else hoch(e.ekQuote, EK_QUOTE_GUT, EK_QUOTE_SCHLECHT),
                e.ekQuote,
            ))
            add(Zeile(Kriterium.KGV, kgvPunkte(e.kgv), e.kgv))
            add(Zeile(Kriterium.KGV_SCHNITT, kgvPunkte(e.kgvSchnitt), e.kgvSchnitt))
            add(Zeile(Kriterium.ANALYSTEN, analystenPunkte(e.note, e.analysten, g), e.note))
            add(Zeile(Kriterium.ZAHLEN, hoch(e.zahlen, ZAHLEN_SCHWELLE, -ZAHLEN_SCHWELLE), e.zahlen))
            add(Zeile(Kriterium.REVISIONEN, hoch(e.revision, SCHWELLE, -SCHWELLE), e.revision))
            add(Zeile(Kriterium.KURS6, p6, e.kurs6))
            add(Zeile(Kriterium.KURS12, p12, e.kurs12))
            add(Zeile(Kriterium.MOMENTUM, momentumPunkte(p6, p12)))
            add(Zeile(Kriterium.UMKEHR, umkehrPunkte(e.umkehr, g)))
            add(Zeile(Kriterium.GEWINNWACHSTUM, hoch(e.gewinnwachstum, SCHWELLE, -SCHWELLE), e.gewinnwachstum))
        }
        val kaufAb = when (g) { Groesse.GROSS -> KAUF_GROSS; Groesse.MITTEL_KLEIN -> KAUF_MITTEL_KLEIN; null -> null }
        return Ergebnis(zeilen.sumOf { it.punkte ?: 0 }, zeilen.count { it.punkte != null }, zeilen.size, g, kaufAb, zeilen)
    }

    // Ampel der Kernzeile (Anzeige der App): gruen ab der Kaufschwelle, rot bei negativer Summe, sonst gelb.
    fun ampel(e: Ergebnis): Bewertungsblick.Ampel? = when {
        e.bewertet == 0 -> null
        e.kauf == true -> Bewertungsblick.Ampel.GRUEN
        e.punkte < 0 -> Bewertungsblick.Ampel.ROT
        else -> Bewertungsblick.Ampel.GELB
    }

    // ---------------------------------------------------------------- Kursgroessen aus Tageskursen (aelteste zuerst)

    // Veraenderung des letzten Schlusskurses gegen den Schluss vor monate Monaten in Prozent; null, wenn die Reihe nicht
    // so weit zurueckreicht (der Vergleichskurs darf hoechstens zehn Tage aelter sein als der Stichtag).
    fun kursGegen(kerzen: List<Candle>, monate: Int): Double? {
        fun tagVon(t: Long) = Instant.ofEpochMilli(t).atZone(ZoneOffset.UTC).toLocalDate()
        val letzte = kerzen.lastOrNull { it.close > 0 } ?: return null
        val stichtag = tagVon(letzte.t).minusMonths(monate.toLong()).toEpochDay()
        val alt = kerzen.lastOrNull { it.close > 0 && tagVon(it.t).toEpochDay() <= stichtag } ?: return null
        if (stichtag - tagVon(alt.t).toEpochDay() > 10) return null
        return (letzte.close / alt.close - 1.0) * 100.0
    }

    // Letzter Schlusskurs je Kalendermonat (UTC), Schluessel Jahr mal 12 plus Monat.
    private fun monatsenden(kerzen: List<Candle>): Map<Int, Double> {
        val aus = LinkedHashMap<Int, Double>()
        for (k in kerzen) {
            if (!(k.close > 0)) continue
            val d = Instant.ofEpochMilli(k.t).atZone(ZoneOffset.UTC).toLocalDate()
            aus[d.year * 12 + d.monthValue - 1] = k.close
        }
        return aus
    }

    // Kurs minus Index in Prozentpunkten fuer die letzten drei abgeschlossenen Kalendermonate, aeltester zuerst. Der
    // laufende Monat ist der des letzten Kurses. null, wenn einem der vier Monatsenden bei Aktie oder Index der Kurs fehlt.
    fun umkehrMonate(aktie: List<Candle>, index: List<Candle>): List<Double>? {
        val a = monatsenden(aktie)
        val i = monatsenden(index)
        val jetzt = a.keys.maxOrNull() ?: return null
        return (3 downTo 1).map { n ->
            val m = jetzt - n
            val a0 = a[m - 1] ?: return null
            val a1 = a[m] ?: return null
            val i0 = i[m - 1] ?: return null
            val i1 = i[m] ?: return null
            (a1 / a0 - 1.0) * 100.0 - (i1 / i0 - 1.0) * 100.0
        }
    }

    // Kursschritt der Aktie am Reaktionstag minus Schritt des Index am selben Tag, in Prozentpunkten. tag: Handelstag der
    // Reaktion (Tage seit 1970 in zone), schritt: Kursschritt der Aktie in Prozent. null, wenn der Index an dem Tag
    // keinen Kurs hat.
    fun zahlenGegenIndex(schritt: Double, tag: Long, index: List<Candle>, zone: ZoneId): Double? {
        fun tagVon(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toEpochDay()
        val i = index.indexOfLast { tagVon(it.t) <= tag }
        if (i < 1) return null
        // Der Index darf am Reaktionstag oder dem Handelstag davor notieren (Feiertag am anderen Platz)
        if (tag - tagVon(index[i].t) > 1) return null
        val vor = index[i - 1].close
        val nach = index[i].close
        if (!(vor > 0) || !(nach > 0) || !schritt.isFinite()) return null
        return schritt - (nach / vor - 1.0) * 100.0
    }

    // Veraenderung einer Schaetzung in Prozent, bezogen auf den Betrag des alten Werts; null ohne alten Wert.
    fun revision(jetzt: Double?, vorher: Double?): Double? =
        if (jetzt != null && vorher != null && jetzt.isFinite() && vorher.isFinite() && vorher != 0.0) (jetzt - vorher) / abs(vorher) * 100.0 else null
}
