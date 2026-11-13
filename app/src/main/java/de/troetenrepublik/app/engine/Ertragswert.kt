package de.troetenrepublik.app.engine

// Ertragswert ohne Wachstum (0.78): was die Firma wert ist, wenn sie ihren heutigen Gewinn dauerhaft haelt und nicht
// mehr waechst. Rechnung in der Berichtswaehrung:
//   nachhaltiger Gewinn = mittlere EBIT-Marge der letzten bis zu fuenf Geschaeftsjahre × Umsatz des letzten Jahres
//                         × (1 − mittlerer Steuersatz dieser Jahre)
//   Ertragswert = nachhaltiger Gewinn ÷ Kapitalkosten + Kasse − Finanzschulden, geteilt durch die Aktien im Umlauf
// Kapitalkosten: 8 % wie die Renditeerwartung der Bewertung (Bewertungsblick.RENDITE). Die mittlere Marge glaettet
// gute und schlechte Jahre. Liegt der Ertragswert ueber dem Buchwert, traegt die Firma einen Wettbewerbsvorteil;
// liegt der Kurs ueber dem Ertragswert, bezahlt der Kaeufer Wachstum. Banken und Versicherer sind ausgenommen (ihr
// EBIT sagt wenig). Fehlt eine Angabe, gibt es kein Ergebnis. Reine Rechnung.
object Ertragswert {

    const val KAPITALKOSTEN = Bewertungsblick.RENDITE
    const val MAX_JAHRE = 5
    const val MIN_JAHRE = 3

    // jeAktie und gewinn in der Berichtswaehrung; marge in Prozent, steuersatz als Anteil.
    data class Ergebnis(val jeAktie: Double, val gewinn: Double, val marge: Double, val steuersatz: Double, val jahre: Int)

    // kasse: null ohne Angabe (dann kein Ergebnis); schulden: null heisst keine Finanzschulden gemeldet.
    fun berechne(jahre: List<Qualitaet.Jahr>, kasse: Double?, schulden: Double?, aktien: Double?): Ergebnis? {
        val a = aktien?.takeIf { it > 0 && it.isFinite() } ?: return null
        val k = kasse?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val letzte = jahre.sortedBy { it.ende }.takeLast(MAX_JAHRE)
        val margen = letzte.mapNotNull { j ->
            val u = j.umsatz?.takeIf { it > 0 } ?: return@mapNotNull null
            j.ebit?.let { it / u }?.takeIf { it.isFinite() }
        }
        if (margen.size < MIN_JAHRE) return null
        val umsatz = letzte.lastOrNull()?.umsatz?.takeIf { it > 0 } ?: return null
        val saetze = letzte.mapNotNull { j -> j.steuersatz?.takeIf { it in 0.0..0.5 } }
        if (saetze.isEmpty()) return null
        val marge = margen.average()
        val satz = saetze.average()
        val gewinn = marge * umsatz * (1.0 - satz)
        val wert = gewinn / (KAPITALKOSTEN / 100.0) + k - (schulden?.takeIf { it.isFinite() && it > 0 } ?: 0.0)
        val je = wert / a
        if (!je.isFinite()) return null
        return Ergebnis(je, gewinn, marge * 100.0, satz, margen.size)
    }

    // Kurs gegen den Ertragswert in Prozent (positiv: Kurs liegt darueber); null bei nicht positivem Ertragswert.
    fun abstand(kurs: Double, wert: Double): Double? = if (kurs > 0 && wert > 0) (kurs / wert - 1.0) * 100.0 else null

    // Buchwert je Aktie aus dem letzten Abschluss: Bilanzsumme minus Verbindlichkeiten, durch die Aktien im Umlauf.
    fun buchwertJeAktie(jahre: List<Qualitaet.Jahr>, aktien: Double?): Double? {
        val j = jahre.maxByOrNull { it.ende } ?: return null
        val a = aktien?.takeIf { it > 0 && it.isFinite() } ?: return null
        val summe = j.bilanzsumme ?: return null
        val schulden = j.verbindlichkeiten ?: return null
        return ((summe - schulden) / a).takeIf { it.isFinite() }
    }

    // Ampel der Zeile (Anzeige der App): gruen, wenn der Kurs hoechstens beim Ertragswert liegt, sonst gelb; rot ohne
    // positiven Ertragswert.
    fun ampel(kurs: Double, wert: Double): Bewertungsblick.Ampel = when {
        wert <= 0 -> Bewertungsblick.Ampel.ROT
        kurs <= wert -> Bewertungsblick.Ampel.GRUEN
        else -> Bewertungsblick.Ampel.GELB
    }
}
