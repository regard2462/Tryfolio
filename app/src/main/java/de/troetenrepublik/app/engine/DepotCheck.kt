package de.troetenrepublik.app.engine

// Regeln mit Ampel, wie sie viele Ratgeber fuer ein Depot nennen. Die Schwellen sind Faustregeln, keine Beratung.
// Breit streuende ETFs gelten als gestreut: Klumpen zaehlen nur bei Einzelwerten, Derivaten und Krypto.
object DepotCheck {

    enum class Art { AKTIE, ETF, KRYPTO, DERIVAT, SONST }

    enum class Stufe { GUT, ACHTUNG, KRITISCH, HINWEIS }

    enum class Regel { GROESSTE, ANZAHL, BRANCHE, WAEHRUNG, HEBEL, KRYPTO, KOSTEN, FONDSKOSTEN, TER_GESTIEGEN, RESERVE, SPARPLAN, VERLUSTE }

    // Schwellen der Einzelposition als Anteil am Depot: Hinweis, deutlich, kritisch.
    const val POSITION_HINWEIS = 0.05
    const val POSITION_DEUTLICH = 0.10
    const val POSITION_KRITISCH = 0.20

    // Schwellen der Zahl der Einzelaktien: darunter kritisch, deutlich, Hinweis. Ab ETF_GESTREUT in ETFs gilt das Depot
    // als gestreut.
    const val ANZAHL_KRITISCH = 8
    const val ANZAHL_DEUTLICH = 15
    const val ANZAHL_HINWEIS = 20
    const val ETF_GESTREUT = 0.5

    fun positionStufe(anteil: Double): Stufe = when {
        anteil > POSITION_KRITISCH -> Stufe.KRITISCH
        anteil > POSITION_DEUTLICH -> Stufe.ACHTUNG
        anteil > POSITION_HINWEIS -> Stufe.HINWEIS
        else -> Stufe.GUT
    }

    fun anzahlStufe(anzahl: Int, etfAnteil: Double): Stufe = when {
        etfAnteil >= ETF_GESTREUT -> Stufe.GUT
        anzahl < ANZAHL_KRITISCH -> Stufe.KRITISCH
        anzahl < ANZAHL_DEUTLICH -> Stufe.ACHTUNG
        anzahl < ANZAHL_HINWEIS -> Stufe.HINWEIS
        else -> Stufe.GUT
    }

    // ter: laufende Kosten eines Fonds in Prozent pro Jahr (5.94), null ohne Angabe. terVorher: die TER vor einer
    // Erhoehung im letzten Jahr, sonst null.
    // symbol: fuer die Durchschau der Fonds bei der Fremdwaehrung (0.78), leer ohne.
    data class Wert(
        val name: String, val wert: Double, val art: Art, val waehrung: String, val branche: String?, val ergebnisProzent: Double,
        val ter: Double? = null, val terVorher: Double? = null, val symbol: String = "",
    )

    // Fremdwaehrung (0.78): Anteil ueber dem das Depot als einseitig gilt. Mit der Durchschau der Fonds liegt ein
    // Welt-ETF bei rund 0,9; die Schwelle liegt darueber, damit er allein nicht anschlaegt.
    const val FREMD_ACHTUNG = 0.95

    // Anteil des Depots ausserhalb der Heimatwaehrung, ohne Hebelprodukte. Fonds mit Laenderverteilung (etf) zaehlen nach
    // der Waehrung ihrer Laender, wie die Waehrungen auf der Seite Streuung (DepotStreuung.waehrungen); alles andere
    // zaehlt in seiner Handelswaehrung.
    fun fremdwaehrung(werte: List<Wert>, heim: String, etf: (String) -> Map<String, Double>? = { null }): Double {
        val depot = werte.filter { it.wert > 0 }
        val summe = depot.sumOf { it.wert }
        if (summe <= 0) return 0.0
        val h = Waehrungsschock.code(heim)
        val nach = DepotStreuung.waehrungen(
            depot.filter { it.art != Art.DERIVAT }.map { Triple(it.symbol, it.wert, Waehrungsschock.code(it.waehrung)) },
        ) { s -> if (s.isEmpty()) null else etf(s) }
        return nach.filterKeys { it != h }.values.sum() / summe
    }

    // Laufende Kosten der Fonds (0.78), eine Rechnung fuer die Faustregel und die Zeile auf der Seite Streuung:
    // ter in Prozent pro Jahr gewichtet mit dem Wert, kosten in Euro pro Jahr. posten: Wert und TER in Prozent.
    data class Fondskosten(val ter: Double, val kosten: Double)

    fun fondskosten(posten: List<Pair<Double, Double>>): Fondskosten? {
        val p = posten.filter { it.first > 0 }
        val w = p.sumOf { it.first }
        if (w <= 0) return null
        val kosten = p.sumOf { it.first * it.second / 100 }
        return Fondskosten(kosten / w * 100, kosten)
    }

    // Stand der laufenden Kosten eines Fonds (5.94): zuletzt gesehene TER in Prozent, seit wann sie gilt und die vorige,
    // wenn sie sich geaendert hat. Aenderungen unter 0,005 Prozentpunkten zaehlen nicht (Rundung der Datenquelle).
    @kotlinx.serialization.Serializable
    data class TerStand(val ter: Double, val seit: Long, val vorher: Double? = null)

    fun terAbgleich(alt: TerStand?, neu: Double, jetzt: Long): TerStand = when {
        alt == null -> TerStand(neu, jetzt)
        kotlin.math.abs(neu - alt.ter) < 0.005 -> alt
        else -> TerStand(neu, jetzt, alt.ter)
    }

    // Vorige TER, wenn die laufenden Kosten in den letzten zwoelf Monaten gestiegen sind; sonst null.
    fun terGestiegen(s: TerStand?, jetzt: Long): Double? =
        s?.vorher?.takeIf { s.ter > it && jetzt - s.seit < 365 * 86_400_000L }

    // Ergebnis einer Regel: anteil die gepruefte Kennzahl als Anteil (0 bis 1) oder Zahl, name etwa die groesste
    // Position oder Branche.
    data class Befund(val regel: Regel, val stufe: Stufe, val anteil: Double = 0.0, val name: String = "", val anzahl: Int = 0, val namenListe: List<String> = emptyList())

    // gebuehren und volumen: Summe der Ordergebuehren und des Ordervolumens der letzten zwoelf Monate.
    // heim: Waehrung, die nicht als Fremdwaehrung zaehlt. Ohne mitKonto fehlen die Regeln zu Guthaben, Kosten und
    // Sparplaenen, etwa beim Depot einer Person im Copytrading.
    // etf: Laenderverteilung eines Fonds fuer die Fremdwaehrung, null ohne Durchschau.
    fun pruefen(
        werte: List<Wert>, cash: Double, gebuehren: Double, volumen: Double, orders: Int, sparplaene: Int, heim: String = "EUR", mitKonto: Boolean = true,
        etf: (String) -> Map<String, Double>? = { null },
    ): List<Befund> {
        val depot = werte.filter { it.wert > 0 }
        val summe = depot.sumOf { it.wert }
        if (summe <= 0) return emptyList()
        val aus = ArrayList<Befund>()
        val einzeln = depot.filter { it.art != Art.ETF }

        // Groesste Einzelposition; anzahl: Einzelpositionen ueber der Hinweisschwelle
        einzeln.maxByOrNull { it.wert }?.let { g ->
            val a = g.wert / summe
            aus += Befund(Regel.GROESSTE, positionStufe(a), a, g.name, einzeln.count { it.wert / summe > POSITION_HINWEIS })
        }

        // Zahl der Einzelaktien (Krypto und Hebelprodukte streuen nicht); mit mindestens der Haelfte in ETFs ist das
        // Depot schon gestreut. Schwellen nach dem Text: ab etwa 15 bis 20 Werten sinkt das Einzelrisiko deutlich.
        val etfAnteil = depot.filter { it.art == Art.ETF }.sumOf { it.wert } / summe
        val anzahl = depot.count { it.art == Art.AKTIE }
        aus += Befund(
            Regel.ANZAHL,
            anzahlStufe(anzahl, etfAnteil),
            etfAnteil, anzahl = anzahl,
        )

        // Groesste Branche unter den Aktien, gemessen am ganzen Depot
        depot.filter { it.art == Art.AKTIE && it.branche != null }.groupBy { it.branche!! }.map { (k, l) -> k to l.sumOf { it.wert } / summe }
            .maxByOrNull { it.second }?.let { (b, a) ->
                aus += Befund(Regel.BRANCHE, if (a > 0.40) Stufe.KRITISCH else if (a > 0.25) Stufe.ACHTUNG else Stufe.GUT, a, b)
            }

        // Fremdwaehrung: Schwankungen des Wechselkurses kommen zum Kurs dazu
        val fremd = fremdwaehrung(depot, heim, etf)
        aus += Befund(Regel.WAEHRUNG, if (fremd > FREMD_ACHTUNG) Stufe.ACHTUNG else Stufe.GUT, fremd)

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

        // Laufende Fondskosten (5.94): TER der ETFs gewichtet mit ihrem Wert; anzahl sind die Kosten pro Jahr in Euro,
        // name der teuerste Fonds. Bis 0,3 % gut, bis 0,6 % mittel, darueber teuer.
        val mitTer = depot.filter { it.art == Art.ETF && it.ter != null }
        fondskosten(mitTer.map { it.wert to it.ter!! })?.let { f ->
            aus += Befund(
                Regel.FONDSKOSTEN, if (f.ter > 0.6) Stufe.KRITISCH else if (f.ter > 0.3) Stufe.ACHTUNG else Stufe.GUT,
                f.ter / 100, mitTer.maxBy { it.ter!! }.name, Math.round(f.kosten).toInt(),
            )
        }

        // Gestiegene Fondskosten (5.94): anteil ist der groesste Anstieg, name die betroffenen Fonds
        val teurer = depot.filter { it.art == Art.ETF && it.ter != null && it.terVorher != null && it.ter > it.terVorher }
        if (teurer.isNotEmpty()) aus += Befund(
            Regel.TER_GESTIEGEN, Stufe.ACHTUNG, teurer.maxOf { it.ter!! - it.terVorher!! } / 100,
            teurer.joinToString(", ") { it.name }, teurer.size, teurer.map { it.name },
        )

        // Guthaben: etwas Reserve fuer Nachkaeufe, aber nicht das meiste ungenutzt
        if (mitKonto) {
            val reserve = cash / (summe + cash)
            aus += Befund(Regel.RESERVE, if (reserve < 0.02) Stufe.ACHTUNG else if (reserve > 0.5) Stufe.HINWEIS else Stufe.GUT, reserve)
            aus += Befund(Regel.SPARPLAN, if (sparplaene > 0) Stufe.GUT else Stufe.HINWEIS, anzahl = sparplaene)
        }

        // Tiefe Verluste: Positionen mehr als 50 Prozent im Minus
        val tief = depot.filter { it.ergebnisProzent < -50 }
        if (tief.isNotEmpty()) aus += Befund(Regel.VERLUSTE, Stufe.HINWEIS, name = tief.sortedBy { it.ergebnisProzent }.joinToString(", ") { it.name }, anzahl = tief.size, namenListe = tief.sortedBy { it.ergebnisProzent }.map { it.name })
        return aus
    }
}
