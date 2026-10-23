package de.troetenrepublik.app.engine

// Regeln mit Ampel, wie sie viele Ratgeber fuer ein Depot nennen. Die Schwellen sind Faustregeln, keine Beratung.
// Breit streuende ETFs gelten als gestreut: Klumpen zaehlen nur bei Einzelwerten, Derivaten und Krypto.
object DepotCheck {

    enum class Art { AKTIE, ETF, KRYPTO, DERIVAT, SONST }

    enum class Stufe { GUT, ACHTUNG, KRITISCH, HINWEIS }

    enum class Regel { GROESSTE, ANZAHL, BRANCHE, WAEHRUNG, HEBEL, KRYPTO, KOSTEN, RESERVE, SPARPLAN, VERLUSTE }

    data class Wert(val name: String, val wert: Double, val art: Art, val waehrung: String, val branche: String?, val ergebnisProzent: Double)

    // Ergebnis einer Regel: anteil die gepruefte Kennzahl als Anteil (0 bis 1) oder Zahl, name etwa die groesste
    // Position oder Branche.
    data class Befund(val regel: Regel, val stufe: Stufe, val anteil: Double = 0.0, val name: String = "", val anzahl: Int = 0)

    // gebuehren und volumen: Summe der Ordergebuehren und des Ordervolumens der letzten zwoelf Monate.
    // heim: Waehrung, die nicht als Fremdwaehrung zaehlt. Ohne mitKonto fehlen die Regeln zu Guthaben, Kosten und
    // Sparplaenen, etwa beim Depot einer Person im Copytrading.
    fun pruefen(werte: List<Wert>, cash: Double, gebuehren: Double, volumen: Double, orders: Int, sparplaene: Int, heim: String = "EUR", mitKonto: Boolean = true): List<Befund> {
        val depot = werte.filter { it.wert > 0 }
        val summe = depot.sumOf { it.wert }
        if (summe <= 0) return emptyList()
        val aus = ArrayList<Befund>()
        val einzeln = depot.filter { it.art != Art.ETF }

        // Groesste Einzelposition
        einzeln.maxByOrNull { it.wert }?.let { g ->
            val a = g.wert / summe
            aus += Befund(Regel.GROESSTE, if (a > 0.20) Stufe.KRITISCH else if (a > 0.10) Stufe.ACHTUNG else Stufe.GUT, a, g.name)
        }

        // Zahl der Einzelwerte; mit mindestens der Haelfte in ETFs ist das Depot schon gestreut
        val etfAnteil = depot.filter { it.art == Art.ETF }.sumOf { it.wert } / summe
        val anzahl = einzeln.size
        aus += Befund(
            Regel.ANZAHL,
            when { etfAnteil >= 0.5 -> Stufe.GUT; anzahl < 5 -> Stufe.KRITISCH; anzahl < 10 -> Stufe.ACHTUNG; else -> Stufe.GUT },
            etfAnteil, anzahl = anzahl,
        )

        // Groesste Branche unter den Aktien, gemessen am ganzen Depot
        depot.filter { it.art == Art.AKTIE && it.branche != null }.groupBy { it.branche!! }.map { (k, l) -> k to l.sumOf { it.wert } / summe }
            .maxByOrNull { it.second }?.let { (b, a) ->
                aus += Befund(Regel.BRANCHE, if (a > 0.40) Stufe.KRITISCH else if (a > 0.25) Stufe.ACHTUNG else Stufe.GUT, a, b)
            }

        // Fremdwaehrung: Schwankungen des Wechselkurses kommen zum Kurs dazu
        val fremd = depot.filter { it.waehrung != heim && it.art != Art.DERIVAT }.sumOf { it.wert } / summe
        aus += Befund(Regel.WAEHRUNG, if (fremd > 0.7) Stufe.ACHTUNG else Stufe.GUT, fremd)

        // Hebelprodukte und Krypto
        val hebel = depot.filter { it.art == Art.DERIVAT }.sumOf { it.wert } / summe
        aus += Befund(Regel.HEBEL, if (hebel > 0.25) Stufe.KRITISCH else if (hebel > 0.10) Stufe.ACHTUNG else Stufe.GUT, hebel)
        val krypto = depot.filter { it.art == Art.KRYPTO }.sumOf { it.wert } / summe
        if (krypto > 0) aus += Befund(Regel.KRYPTO, if (krypto > 0.20) Stufe.KRITISCH else if (krypto > 0.10) Stufe.ACHTUNG else Stufe.GUT, krypto)

        // Kosten der Orders im letzten Jahr im Verhaeltnis zum gehandelten Betrag
        if (mitKonto && orders > 0 && volumen > 0) {
            val q = gebuehren / volumen
            aus += Befund(Regel.KOSTEN, if (q > 0.01) Stufe.KRITISCH else if (q > 0.005) Stufe.ACHTUNG else Stufe.GUT, q, anzahl = orders)
        }

        // Guthaben: etwas Reserve fuer Nachkaeufe, aber nicht das meiste ungenutzt
        if (mitKonto) {
            val reserve = cash / (summe + cash)
            aus += Befund(Regel.RESERVE, if (reserve < 0.02) Stufe.ACHTUNG else if (reserve > 0.5) Stufe.HINWEIS else Stufe.GUT, reserve)
            aus += Befund(Regel.SPARPLAN, if (sparplaene > 0) Stufe.GUT else Stufe.HINWEIS, anzahl = sparplaene)
        }

        // Tiefe Verluste: Positionen mehr als 50 Prozent im Minus
        val tief = depot.filter { it.ergebnisProzent < -50 }
        if (tief.isNotEmpty()) aus += Befund(Regel.VERLUSTE, Stufe.HINWEIS, name = tief.sortedBy { it.ergebnisProzent }.joinToString(", ") { it.name }, anzahl = tief.size)
        return aus
    }
}
