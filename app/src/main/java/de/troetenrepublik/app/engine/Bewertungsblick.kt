package de.troetenrepublik.app.engine

import kotlin.math.abs

// Bewertung, Bilanz und Wachstum einer Aktie auf einen Blick (5.92): je eine Ampel mit den Gruenden, die die
// Wertpapierseite in Alltagssprache zeigt. Dazu die Richtung der Leerverkaeufe und die Abweichung von Geschaeftszahlen
// zur Erwartung. Reine Rechnung, die Texte stehen in den Sprachdateien.
object Bewertungsblick {

    enum class Ampel { GRUEN, GELB, ROT }

    // Annahmen fuer die Bewertungszeile: zehn Jahre, 8 % Renditeerwartung, danach KGV 15 (oder das der Branche).
    const val JAHRE = 10
    const val RENDITE = 8.0
    const val ZIEL_KGV = 15.0

    // Banken und Versicherer (5.94): Schulden sind ihr Geschaeft, Gewinne schwanken mit Rueckstellungen, das KGV sagt
    // wenig. Bewertet wird nach dem Kurs-Buchwert-Verhaeltnis gegen das, was die Eigenkapitalrendite rechtfertigt:
    // angemessenes KBV = (ROE − g) / (r − g) mit r = 10 % Kapitalkosten und g = 2 % Wachstum. Bis zum angemessenen
    // KBV gruen, bis 30 % darueber gelb, sonst rot; deckt die Rendite das Wachstum nicht, rot.
    const val BANK_KAPITALKOSTEN = 10.0
    const val BANK_WACHSTUM = 2.0
    const val BANK_SPIELRAUM = 1.3

    // Geschaeft mit der eigenen Bilanz (Yahoo-Branchen): Banken, Versicherer, Hypotheken- und Kreditfinanzierer.
    // Vermoegensverwalter und Boersen verdienen an Gebuehren und bleiben beim KGV.
    fun bilanzgeschaeft(branche: String): Boolean {
        val b = branche.lowercase()
        return listOf("bank", "insurance", "mortgage", "credit services").any { b.contains(it) }
    }

    // roe in Prozent; angemessen null, wenn die Rendite das Wachstum nicht deckt.
    data class BankBewertung(val ampel: Ampel, val kbv: Double, val roe: Double, val angemessen: Double?)

    fun bankBewertung(kbv: Double, roe: Double): BankBewertung? {
        if (!(kbv > 0) || !roe.isFinite() || roe == 0.0) return null
        val angemessen = ((roe - BANK_WACHSTUM) / (BANK_KAPITALKOSTEN - BANK_WACHSTUM)).takeIf { it > 0 }
        val ampel = when {
            angemessen == null -> Ampel.ROT
            kbv <= angemessen -> Ampel.GRUEN
            kbv <= angemessen * BANK_SPIELRAUM -> Ampel.GELB
            else -> Ampel.ROT
        }
        return BankBewertung(ampel, kbv, roe, angemessen)
    }

    // Bilanzqualitaet einer Bank: Gesamtkapitalrendite (ab 1 % gut, unter 0,5 % schwach) und Eigenkapitalquote
    // (ab 8 % gut, unter 5 % duenn), beides in Prozent. Die schlechtere Ampel zaehlt; ohne beide Werte null.
    data class BankBilanz(val ampel: Ampel, val roa: Double?, val ekQuote: Double?)

    fun bankBilanz(roa: Double?, ekQuote: Double?): BankBilanz? {
        val a = roa?.takeIf { it.isFinite() && it != 0.0 }
        val e = ekQuote?.takeIf { it.isFinite() && it > 0 && it < 100 }
        if (a == null && e == null) return null
        val stufen = listOfNotNull(
            a?.let { if (it >= 1.0) 0 else if (it >= 0.5) 1 else 2 },
            e?.let { if (it >= 8.0) 0 else if (it >= 5.0) 1 else 2 },
        )
        return BankBilanz(Ampel.entries[stufen.max()], a, e)
    }

    // Eigenkapitalquote in Prozent aus Bilanzsumme und Verbindlichkeiten des letzten Abschlusses.
    fun ekQuote(bilanzsumme: Double?, verbindlichkeiten: Double?): Double? =
        if (bilanzsumme != null && verbindlichkeiten != null && bilanzsumme > 0 && verbindlichkeiten >= 0 && verbindlichkeiten < bilanzsumme)
            (bilanzsumme - verbindlichkeiten) / bilanzsumme * 100 else null

    // Bewertung: noetig ist das im Kurs steckende Gewinnwachstum pro Jahr (ReverseDcf), bisher das Wachstum der
    // letzten Jahresgewinne, erwartet das der Analysten (alles in Prozent). Gruen, wenn das bisherige (sonst das
    // erwartete) Wachstum reicht, gelb bis 5 Punkte darueber, sonst rot. Ohne Vergleich zaehlt das noetige Wachstum
    // allein: bis 3 % gruen, bis 10 % gelb.
    data class Bewertung(val ampel: Ampel, val noetig: Double, val vergleich: Double?)

    fun bewertung(noetig: Double, bisher: Double?, erwartet: Double?): Bewertung {
        val vergleich = bisher ?: erwartet
        val ampel = if (vergleich != null) {
            val luecke = noetig - vergleich
            when {
                luecke <= 0.0 -> Ampel.GRUEN
                luecke <= 5.0 -> Ampel.GELB
                else -> Ampel.ROT
            }
        } else when {
            noetig <= 3.0 -> Ampel.GRUEN
            noetig <= 10.0 -> Ampel.GELB
            else -> Ampel.ROT
        }
        return Bewertung(ampel, noetig, vergleich)
    }

    // Wachstum fuer den fairen Wert: das bisherige, begrenzt auf 0 bis 25 % pro Jahr, damit einzelne Ausreisser
    // (ein Verlustjahr am Anfang) keinen unsinnigen Wert ergeben.
    fun annahme(bisher: Double?): Double? = bisher?.coerceIn(0.0, 25.0)

    // Fairer Wert je Aktie bei wachstum Prozent Gewinnwachstum mit denselben Annahmen wie ReverseDcf: Kurs mal Wert
    // heute je Gewinn, geteilt durch das heutige KGV. Bei genau dem eingepreisten Wachstum ist er gleich dem Kurs.
    // null ohne positives KGV.
    fun fairerWert(kurs: Double, kgv: Double, wachstum: Double, zielKgv: Double = ZIEL_KGV): Double? {
        if (!(kurs > 0) || !(kgv > 0) || !(zielKgv > 0)) return null
        return kurs * ReverseDcf.wertHeute(wachstum, JAHRE, zielKgv, RENDITE) / kgv
    }

    // Gruende fuer die Bilanzampel in der Reihenfolge, in der sie im Satz stehen.
    enum class Grund(val gut: Boolean) {
        PROFITABEL(true), VERLUST(false), SCHULDEN_SINKEN(true), SCHULDEN_STEIGEN(false), GEWINNE_STEIGEN(true), GEWINNE_SINKEN(false),
        GELD_FLIESST(true), GELD_FEHLT(false), PLEITE_RISIKO(false), NEUE_AKTIEN(false),
    }

    data class Bilanz(val ampel: Ampel, val gruende: List<Grund>)

    // Bilanz aus Piotroski und Altman: gruen bei starkem F-Score ohne Gefaehrdung, rot bei schwachem F-Score oder
    // gefaehrdeter Altman-Zone, sonst gelb. Bis zu drei Gruende, bei Gruen die guten zuerst, sonst die schlechten.
    fun bilanz(f: Qualitaet.Piotroski?, z: Qualitaet.Altman?): Bilanz? {
        val stufe = f?.let { Qualitaet.stufe(it) }
        if (stufe == null && z == null) return null
        val ampel = when {
            stufe == Qualitaet.Stufe.SCHWACH || z?.zone == Qualitaet.Zone.GEFAEHRDET -> Ampel.ROT
            (stufe == Qualitaet.Stufe.STARK || stufe == null) && (z == null || z.zone == Qualitaet.Zone.SICHER) -> Ampel.GRUEN
            else -> Ampel.GELB
        }
        fun erfuellt(p: Qualitaet.Punkt): Boolean? = f?.pruefungen?.firstOrNull { it.punkt == p }?.erfuellt
        val alle = buildList {
            when (erfuellt(Qualitaet.Punkt.RENDITE)) { true -> add(Grund.PROFITABEL); false -> add(Grund.VERLUST); null -> {} }
            when (erfuellt(Qualitaet.Punkt.SCHULDEN_SINKEN)) { true -> add(Grund.SCHULDEN_SINKEN); false -> add(Grund.SCHULDEN_STEIGEN); null -> {} }
            when (erfuellt(Qualitaet.Punkt.RENDITE_STEIGT)) { true -> add(Grund.GEWINNE_STEIGEN); false -> add(Grund.GEWINNE_SINKEN); null -> {} }
            when (erfuellt(Qualitaet.Punkt.CASHFLOW)) { true -> add(Grund.GELD_FLIESST); false -> add(Grund.GELD_FEHLT); null -> {} }
            if (z?.zone == Qualitaet.Zone.GEFAEHRDET) add(Grund.PLEITE_RISIKO)
            if (erfuellt(Qualitaet.Punkt.KEINE_NEUEN_AKTIEN) == false) add(Grund.NEUE_AKTIEN)
        }
        val gute = alle.filter { it.gut }
        val schlechte = alle.filter { !it.gut }
        val gruende = when (ampel) {
            Ampel.GRUEN -> gute + schlechte
            Ampel.ROT -> schlechte + gute
            // Gelb: ein guter Punkt, dann die schlechten
            Ampel.GELB -> gute.take(1) + schlechte + gute.drop(1)
        }.take(3)
        return Bilanz(ampel, gruende)
    }

    // Wachstum im letzten Geschaeftsjahr (Prozent zum Vorjahr): gruen, wenn der Umsatz um mehr als 3 % steigt und der
    // Gewinn nicht faellt; rot bei schrumpfendem Umsatz (unter -3 %) oder einem Gewinnrueckgang von mehr als 15 %.
    fun wachstum(umsatz: Double?, gewinn: Double?): Ampel? {
        if (umsatz == null && gewinn == null) return null
        return when {
            (umsatz != null && umsatz < -3.0) || (gewinn != null && gewinn < -15.0) -> Ampel.ROT
            (umsatz == null || umsatz > 3.0) && (gewinn == null || gewinn >= 0.0) -> Ampel.GRUEN
            else -> Ampel.GELB
        }
    }

    // Veraenderung zum Vorjahr in Prozent, bezogen auf den Betrag des Vorjahres (von -10 auf +5 sind +150 %);
    // null ohne Vorjahr oder bei Vorjahr 0.
    fun veraenderung(jetzt: Double?, vorher: Double?): Double? =
        if (jetzt != null && vorher != null && vorher != 0.0) (jetzt - vorher) / abs(vorher) * 100.0 else null

    enum class Trend { STEIGEND, FALLEND, GLEICH }

    // Stufe der Leerverkaeufe fuer ein Urteil in Worten (5.94): 0 wenige, 1 einige, 2 viele, 3 sehr viele Wetten auf
    // fallende Kurse. US-Werte: Anteil an den frei handelbaren Aktien (unter 5, 10, 20 %). Sonst die Summe der
    // gemeldeten Positionen ab 0,5 %, die nur die groessten Wetten erfasst und daher niedriger liegt (unter 1, 3, 5 %).
    fun leerStufe(anteil: Double, us: Boolean): Int {
        val grenzen = if (us) listOf(5.0, 10.0, 20.0) else listOf(1.0, 3.0, 5.0)
        return grenzen.count { anteil >= it }
    }

    // Richtung der Leerverkaeufe: mehr als 5 % mehr leer verkaufte Aktien als vorher gilt als steigend, mehr als 5 %
    // weniger als fallend. null ohne Vergleichswert.
    fun leerTrend(jetzt: Double, vorher: Double?): Trend? {
        if (vorher == null || !(vorher > 0) || jetzt < 0) return null
        val r = jetzt / vorher - 1.0
        return when {
            r > 0.05 -> Trend.STEIGEND
            r < -0.05 -> Trend.FALLEND
            else -> Trend.GLEICH
        }
    }

    // Geschaeftszahlen gegen die Erwartung: Abweichung in Prozent der Erwartung; ueber +2 % besser (gruen), unter -2 %
    // schlechter (rot), dazwischen wie erwartet (gelb). null ohne Erwartung.
    data class Ueberraschung(val ampel: Ampel, val abweichung: Double)

    fun ueberraschung(ist: Double, erwartet: Double): Ueberraschung? {
        if (erwartet == 0.0 || !erwartet.isFinite() || !ist.isFinite()) return null
        val a = (ist - erwartet) / abs(erwartet) * 100.0
        val ampel = when {
            a > 2.0 -> Ampel.GRUEN
            a < -2.0 -> Ampel.ROT
            else -> Ampel.GELB
        }
        return Ueberraschung(ampel, a)
    }
}
