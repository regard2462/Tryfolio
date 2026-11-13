package de.troetenrepublik.app.engine

// Beneish-M-Score (0.78): acht Kennzahlen aus zwei Jahresabschluessen, die zusammen schaetzen, ob das Zahlenbild dem von
// Firmen aehnelt, die ihre Gewinne geschoent hatten (Messod D. Beneish, „The Detection of Earnings Manipulation“,
// Financial Analysts Journal 55 (5), 1999). Gewichte der ungewichteten Probit-Schaetzung, Schwelle -1,78 (dort bei
// Fehlerkosten von 20:1 bis 30:1). Ein Wert ueber der Schwelle ist ein statistischer Hinweis, kein Befund.
// Gerechnet wird nur, wenn alle Angaben beider Jahre vorliegen. Abweichungen von der Studie durch die Datenlage:
// Abschreibungen enthalten die auf immaterielle Werte; fehlen kurzfristige Finanzschulden oder Steuerschulden in der
// Bilanz, zaehlen sie als null. Banken, Versicherer und Finanzwerte sind wie in der Studie ausgenommen
// (Qualitaet.ausgenommen, prueft der Aufrufer).
object Beneish {

    const val KONSTANTE = -4.840
    const val SCHWELLE = -1.78

    // DSRI: Forderungen je Umsatz. GMI: Bruttomarge des Vorjahrs durch die des Jahres. AQI: Anteil des Vermoegens ausser
    // Umlaufvermoegen und Sachanlagen. SGI: Umsatz. DEPI: Abschreibungsquote des Vorjahrs durch die des Jahres.
    // SGAI: Vertriebs- und Verwaltungskosten je Umsatz. TATA: Abgrenzungen je Bilanzsumme (kein Index).
    // LVGI: langfristige Schulden plus kurzfristige Verbindlichkeiten je Bilanzsumme. Alle Indizes: Jahr durch Vorjahr.
    enum class Kennzahl(val gewicht: Double) {
        DSRI(0.920), GMI(0.528), AQI(0.404), SGI(0.892), DEPI(0.115), SGAI(-0.172), TATA(4.679), LVGI(-0.327)
    }

    // wert: die Kennzahl, beitrag: Gewicht mal Wert
    data class Teil(val kennzahl: Kennzahl, val wert: Double, val beitrag: Double)

    data class Ergebnis(val jahr: String, val vorjahr: String, val m: Double, val teile: List<Teil>) {
        val auffaellig: Boolean get() = m > SCHWELLE
    }

    private fun teil(a: Double?, b: Double?): Double? = if (a != null && b != null && b != 0.0) a / b else null

    private fun pos(x: Double?): Double? = x?.takeIf { it.isFinite() && it > 0 }

    // Abgrenzungen wie in der Studie: Veraenderung des Umlaufvermoegens ohne Kasse, minus Veraenderung der kurzfristigen
    // Verbindlichkeiten ohne kurzfristige Finanzschulden und Steuerschulden, minus Abschreibungen.
    fun abgrenzungen(j: Qualitaet.Jahr, v: Qualitaet.Jahr): Double? {
        val ua = j.umlaufvermoegen ?: return null
        val uv = v.umlaufvermoegen ?: return null
        val ka = j.kasse ?: return null
        val kv = v.kasse ?: return null
        val va = j.kurzfristSchulden ?: return null
        val vv = v.kurzfristSchulden ?: return null
        val ab = j.abschreibungen ?: return null
        val finanz = (j.kurzfristFinanzschulden ?: 0.0) - (v.kurzfristFinanzschulden ?: 0.0)
        val steuer = (j.steuerschuld ?: 0.0) - (v.steuerschuld ?: 0.0)
        return ((ua - uv) - (ka - kv)) - ((va - vv) - finanz - steuer) - ab
    }

    // Anteil des Vermoegens ausser Umlaufvermoegen und Sachanlagen an der Bilanzsumme s
    private fun rest(x: Qualitaet.Jahr, s: Double): Double? {
        val u = x.umlaufvermoegen ?: return null
        val sach = x.sachanlagen ?: return null
        return 1 - (u + sach) / s
    }

    // Abschreibungen durch Abschreibungen plus Sachanlagen
    private fun quote(x: Qualitaet.Jahr): Double? {
        val ab = pos(x.abschreibungen) ?: return null
        val sach = x.sachanlagen?.takeIf { it >= 0 } ?: return null
        return ab / (ab + sach)
    }

    private fun hebel(x: Qualitaet.Jahr, s: Double): Double? = x.kurzfristSchulden?.let { ((x.langfristSchulden ?: 0.0) + it) / s }

    // Die acht Kennzahlen des Jahres j gegen das Vorjahr v; null, sobald eine fehlt oder ein Nenner nicht positiv ist.
    fun kennzahlen(j: Qualitaet.Jahr, v: Qualitaet.Jahr): Map<Kennzahl, Double>? {
        val umsatz = pos(j.umsatz) ?: return null
        val umsatzV = pos(v.umsatz) ?: return null
        val summe = pos(j.bilanzsumme) ?: return null
        val summeV = pos(v.bilanzsumme) ?: return null
        val dsri = teil(teil(j.forderungen, umsatz), pos(teil(v.forderungen, umsatzV))) ?: return null
        val gmi = teil(teil(v.bruttogewinn, umsatzV), pos(teil(j.bruttogewinn, umsatz))) ?: return null
        val aqi = teil(rest(j, summe), pos(rest(v, summeV))) ?: return null
        val sgi = umsatz / umsatzV
        val depi = teil(quote(v), quote(j)) ?: return null
        val sgai = teil(teil(j.vertriebVerwaltung, umsatz), pos(teil(v.vertriebVerwaltung, umsatzV))) ?: return null
        val lvgi = teil(hebel(j, summe), pos(hebel(v, summeV))) ?: return null
        val tata = (abgrenzungen(j, v) ?: return null) / summe
        val alle = mapOf(
            Kennzahl.DSRI to dsri, Kennzahl.GMI to gmi, Kennzahl.AQI to aqi, Kennzahl.SGI to sgi, Kennzahl.DEPI to depi,
            Kennzahl.SGAI to sgai, Kennzahl.TATA to tata, Kennzahl.LVGI to lvgi,
        )
        return alle.takeIf { m -> m.values.all { it.isFinite() } }
    }

    fun score(k: Map<Kennzahl, Double>): Double = KONSTANTE + Kennzahl.entries.sumOf { it.gewicht * k.getValue(it) }

    // Juengstes Jahr gegen das Vorjahr (jahre: aelteste zuerst); null mit weniger als zwei Jahren oder fehlenden Angaben.
    fun berechne(jahre: List<Qualitaet.Jahr>): Ergebnis? {
        if (jahre.size < 2) return null
        val j = jahre[jahre.size - 1]
        val v = jahre[jahre.size - 2]
        val k = kennzahlen(j, v) ?: return null
        return Ergebnis(j.ende, v.ende, score(k), Kennzahl.entries.map { Teil(it, k.getValue(it), it.gewicht * k.getValue(it)) })
    }

    // Lage des Werts auf der Skala der Anzeige von SKALA_VON bis SKALA_BIS, 0 bis 1
    const val SKALA_VON = -4.0
    const val SKALA_BIS = 0.0

    fun lage(m: Double): Double = ((m - SKALA_VON) / (SKALA_BIS - SKALA_VON)).coerceIn(0.0, 1.0)
}
