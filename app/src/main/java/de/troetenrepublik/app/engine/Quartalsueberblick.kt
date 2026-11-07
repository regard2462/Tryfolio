package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Gleichwerte
import de.troetenrepublik.app.market.Quartalsblick
import de.troetenrepublik.app.market.Quartalszahlen
import kotlin.math.abs

// Uebersicht zu den Quartalszahlen einer Aktie (5.97): Erwartungen aus dem Ergebniskalender der App und von Yahoo,
// dazu Punkte, die die Zahlen einordnen. Nur beschreiben, nichts empfehlen.
object Quartalsueberblick {

    // Herkunft einer Schaetzung: KALENDER = Ergebniskalender der App, YAHOO = Yahoo Finance
    enum class Quelle { KALENDER, YAHOO }

    // Erwartung eines Werts (Gewinn je Aktie oder Umsatz) von einer Quelle; waehrung als ISO-Code ("USD").
    data class Erwartung(
        val quelle: Quelle,
        val mittel: Double?,
        val tief: Double?,
        val hoch: Double?,
        val analysten: Int?,
        val vorjahr: Double?,
        val waehrung: String,
    )

    // Aenderung der Gewinnschaetzung in den letzten 30 und 90 Tagen (Yahoo epsTrend) und Zahl der Analysten, die
    // in 30 Tagen erhoeht oder gesenkt haben.
    data class Revision(val jetzt: Double, val vor30: Double?, val vor90: Double?, val hoch30: Int?, val runter30: Int?)

    enum class PunktArt {
        GEWINN_WACHSTUM,       // a = erwartet, b = Vorjahr, p = Veraenderung in Prozent
        GEWINN_RUECKGANG,      // a, b, p (p negativ)
        GEWINN_AUS_VERLUST,    // Vorjahr Verlust (b < 0), erwartet Gewinn (a > 0)
        VERLUST_ERWARTET,      // a < 0 (mit oder ohne Vorjahr; b kann null sein)
        UMSATZ_VERAENDERUNG,   // a = erwarteter Umsatz, b = Vorjahr, p = Veraenderung in Prozent
        SPANNE_WEIT,           // a = tief, b = hoch, n = Analysten; Spanne (hoch - tief) / |mittel| > SPANNE_WEIT_AB
        SPANNE_ENG,            // a, b, n; sonst
        SCHAETZUNG_HOCH,       // a = jetzt, b = vor 30 Tagen, p = Veraenderung in Prozent > +1, n = hoch30, m = runter30
        SCHAETZUNG_RUNTER,     // p < -1
        SCHAETZUNG_GLEICH,     // sonst
        TREFFER,               // n = Quartale ueber der Erwartung, m = Quartale mit Vergleich, p = mittlere Abweichung in Prozent
        GEMELDET_UEBER,        // a = gemeldet, b = erwartet, p = Abweichung in Prozent (> +2)
        GEMELDET_UNTER,        // p < -2
        GEMELDET_WIE_ERWARTET, // sonst
    }

    data class Punkt(val art: PunktArt, val a: Double? = null, val b: Double? = null, val p: Double? = null, val n: Int? = null, val m: Int? = null)

    data class Ueberblick(
        val gewinn: List<Erwartung>,      // Gewinn je Aktie, Kalender zuerst
        val umsatz: List<Erwartung>,      // Umsatz des Quartals, Kalender zuerst
        val gemeldet: Double?,            // gemeldeter Gewinn je Aktie (Kalender), nach den Zahlen
        val abweichung: Double?,          // Abweichung gemeldet gegen erwartet in Prozent (Kalender, sonst berechnet)
        val quartal: String?,             // Quartal laut Kalender (etwa "Sep/2026"), sonst Quartalsende von Yahoo
        val revision: Revision?,
        val verlauf: List<Quartalsblick.Quartal>, // hoechstens die letzten VERLAUF Quartale, aelteste zuerst
        val punkte: List<Punkt>,
    )

    const val SPANNE_WEIT_AB = 0.2
    const val VERLAUF = 4

    // Gehoeren die Erwartungen von Yahoo zu diesem Termin? Yahoo fuehrt sie fuer den naechsten Termin; nach den Zahlen
    // gelten sie schon dem Folgequartal. Passend: Yahoo-Termin hoechstens TOLERANZ_TAGE vom Kalendertag entfernt,
    // ohne Yahoo-Termin ein Quartalsende bis 120 Tage vor dem Kalendertag.
    fun passt(t: Quartalszahlen.Termin, y: Quartalsblick.Daten): Boolean {
        y.termin?.let { return abs(Math.floorDiv(it, 86_400_000L) - t.tag) <= TOLERANZ_TAGE }
        val ende = y.quartalsende?.let { runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrNull() } ?: return false
        return t.tag - ende in 0..120
    }

    const val TOLERANZ_TAGE = 7

    fun erstellen(t: Quartalszahlen.Termin, yAlle: Quartalsblick.Daten?): Ueberblick {
        // Erwartungen von Yahoo nur, wenn sie zu diesem Termin gehoeren; der Verlauf gilt immer
        val y = yAlle?.takeIf { passt(t, it) }
        // Kalender zuerst, dahinter Yahoo; fehlt die Yahoo-Spanne, ersatzweise die Werte aus calendarEvents
        val gewinn = ArrayList<Erwartung>()
        if (t.epsErwartet != null || t.epsVorjahr != null) {
            gewinn += Erwartung(Quelle.KALENDER, t.epsErwartet, null, null, t.schaetzungen, t.epsVorjahr, "USD")
        }
        val yGewinn = y?.let { it.gewinn ?: it.gewinnMittel?.let { m -> Quartalsblick.Spanne(m, it.gewinnTief, it.gewinnHoch, null, null, null) } }
        if (yGewinn != null && yGewinn.mittel != null) {
            gewinn += Erwartung(Quelle.YAHOO, yGewinn.mittel, yGewinn.tief, yGewinn.hoch, yGewinn.analysten, yGewinn.vorjahr, y?.gewinnWaehrung ?: "USD")
        }
        val umsatz = ArrayList<Erwartung>()
        if (t.umsatz != null) {
            umsatz += Erwartung(Quelle.KALENDER, t.umsatz, null, null, null, null, "USD")
        }
        val yUmsatz = y?.let { it.umsatz ?: it.umsatzMittel?.let { m -> Quartalsblick.Spanne(m, it.umsatzTief, it.umsatzHoch, null, null, null) } }
        if (yUmsatz != null && yUmsatz.mittel != null) {
            umsatz += Erwartung(Quelle.YAHOO, yUmsatz.mittel, yUmsatz.tief, yUmsatz.hoch, yUmsatz.analysten, yUmsatz.vorjahr, y?.umsatzWaehrung ?: y?.gewinnWaehrung ?: "USD")
        }
        val gemeldet = t.eps
        val abweichung = t.abweichung ?: gemeldet?.let { g -> t.epsErwartet?.takeIf { it != 0.0 }?.let { e -> (g - e) / abs(e) * 100.0 } }
        val revision = y?.let { d -> d.gewinnJetzt?.let { Revision(it, d.gewinnVor30, d.gewinnVor90, d.hoch30, d.runter30) } }
        val verlauf = yAlle?.verlauf.orEmpty().takeLast(VERLAUF)
        val punkte = ArrayList<Punkt>()

        // 1. gemeldete Zahl gegen die Erwartung (Kalender, sonst Yahoo in Dollar wie die gemeldete Zahl)
        val erwartet = gewinn.firstOrNull { it.quelle == Quelle.KALENDER && it.mittel != null }?.mittel
            ?: gewinn.firstOrNull { it.quelle == Quelle.YAHOO && it.mittel != null && it.waehrung == "USD" }?.mittel
        if (gemeldet != null && erwartet != null) {
            val p = t.abweichung ?: (gemeldet - erwartet) / abs(erwartet) * 100.0
            punkte += Punkt(
                when {
                    p > 2.0 -> PunktArt.GEMELDET_UEBER
                    p < -2.0 -> PunktArt.GEMELDET_UNTER
                    else -> PunktArt.GEMELDET_WIE_ERWARTET
                },
                a = gemeldet,
                b = erwartet,
                p = p,
            )
        }

        // 2. erwarteter Gewinn gegen das Vorjahr
        val mitVorjahr = gewinn.firstOrNull { it.mittel != null && it.vorjahr != null }
        if (mitVorjahr != null) {
            val a = mitVorjahr.mittel!!
            val b = mitVorjahr.vorjahr!!
            when {
                a < 0 -> punkte += Punkt(PunktArt.VERLUST_ERWARTET, a = a, b = b)
                b < 0 -> punkte += Punkt(PunktArt.GEWINN_AUS_VERLUST, a = a, b = b)
                abs(b) >= 0.01 -> {
                    val p = (a - b) / abs(b) * 100.0
                    punkte += Punkt(if (p >= 0.0) PunktArt.GEWINN_WACHSTUM else PunktArt.GEWINN_RUECKGANG, a = a, b = b, p = p)
                }
            }
        } else {
            val a = gewinn.firstNotNullOfOrNull { it.mittel }
            if (a != null && a < 0) punkte += Punkt(PunktArt.VERLUST_ERWARTET, a = a, b = null)
        }

        // 3. erwarteter Umsatz gegen das Vorjahr
        umsatz.firstOrNull { it.mittel != null && it.vorjahr != null && it.vorjahr > 0 }?.let {
            val a = it.mittel!!
            val b = it.vorjahr!!
            punkte += Punkt(PunktArt.UMSATZ_VERAENDERUNG, a = a, b = b, p = (a - b) / b * 100.0)
        }

        // 4. Breite der Schaetzung (Yahoo), Analystenzahl null zaehlt als ausreichend
        gewinn.firstOrNull { it.quelle == Quelle.YAHOO }?.let { e ->
            val tief = e.tief
            val hoch = e.hoch
            val mittel = e.mittel
            if (tief != null && hoch != null && mittel != null && abs(mittel) >= 0.01 && tief < hoch &&
                (e.analysten == null || e.analysten >= 2)
            ) {
                val art = if ((hoch - tief) / abs(mittel) > SPANNE_WEIT_AB) PunktArt.SPANNE_WEIT else PunktArt.SPANNE_ENG
                punkte += Punkt(art, a = tief, b = hoch, n = e.analysten)
            }
        }

        // 5. Bewegung der Gewinnschaetzung gegen vor 30 Tagen
        val vor30 = revision?.vor30
        if (revision != null && vor30 != null && abs(vor30) >= 0.01) {
            val p = (revision.jetzt - vor30) / abs(vor30) * 100.0
            punkte += Punkt(
                when {
                    p > 1.0 -> PunktArt.SCHAETZUNG_HOCH
                    p < -1.0 -> PunktArt.SCHAETZUNG_RUNTER
                    else -> PunktArt.SCHAETZUNG_GLEICH
                },
                a = revision.jetzt,
                b = vor30,
                p = p,
                n = revision.hoch30,
                m = revision.runter30,
            )
        }

        // 6. gemeldete Quartale gegen die jeweilige Erwartung
        val verglichen = verlauf.filter { it.ist != null && it.erwartet != null }
        val werteDa = verglichen.any { it.abweichung != null || it.erwartet != 0.0 }
        if (verglichen.size >= 2 && werteDa) {
            val werte = verglichen.mapNotNull { q ->
                q.abweichung ?: q.erwartet!!.takeIf { it != 0.0 }?.let { (q.ist!! - it) / abs(it) * 100.0 }
            }
            punkte += Punkt(
                PunktArt.TREFFER,
                n = verglichen.count { it.ist!! > it.erwartet!! },
                m = verglichen.size,
                p = werte.takeIf { it.isNotEmpty() }?.average(),
            )
        }

        return Ueberblick(gewinn, umsatz, gemeldet, abweichung, t.quartal ?: y?.quartalsende, revision, verlauf, punkte)
    }

    // Notierung, die beim Tipp auf "Zur Aktie" geoeffnet wird. symbol: Kuerzel im Kalender (US-Notierung, bei
    // Auslandswerten oft ein Hinterlegungsschein), name: Firmenname laut Kalender, eigene: Symbole aus Depot und
    // Merkliste, familie: bekannte andere Notierungen derselben Aktie, katalog: Aktien aus dem Verzeichnis der App.
    fun notierung(symbol: String, name: String, eigene: Set<String>, familie: Set<String>, katalog: List<Catalog.Stock>): String {
        if (symbol in eigene) return symbol
        val verwandte = familie.filter { it != symbol }
        verwandte.filter { it in eigene }.minWithOrNull(VERWANDT)?.let { return it }
        if (katalog.any { it.s == symbol }) return symbol
        verwandte.filter { k -> katalog.any { it.s == k } }.minWithOrNull(VERWANDT)?.let { return it }
        // auslaendische Stammaktie desselben Namens, wenn es genau eine im Verzeichnis gibt
        val treffer = katalog.filter { it.c.isNotEmpty() && it.c != "US" && it.s.contains('.') && gleicherName(name, it.n) }
        if (treffer.size == 1) return treffer[0].s
        return symbol
    }

    // Haeuser, die die Aktie in den letzten tage Tagen eingestuft haben, neueste zuerst und jedes nur einmal
    fun haeuser(urteile: List<Analystenbilanz.Urteil>, jetzt: Long, tage: Int = 90, hoechstens: Int = 8): List<String> =
        urteile.filter { it.zeit in (jetzt - tage * 86_400_000L)..jetzt }.sortedByDescending { it.zeit }
            .map { it.haus.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(hoechstens)

    // Kuerzel mit Punkt zuerst, dann alphabetisch
    private val VERWANDT = compareBy<String> { !it.contains('.') }.thenBy { it }

    // Gleicher Firmenname; namenGleich braucht genug Buchstaben, darum zaehlen bei kurzen Namen wie SAP auch
    // gleiche Stammwoerter.
    private fun gleicherName(name: String, katalogName: String): Boolean =
        Gleichwerte.namenGleich(name, katalogName) ||
            (Gleichwerte.stamm(name) == Gleichwerte.stamm(katalogName) && Gleichwerte.stamm(name).isNotEmpty() && !Gleichwerte.incSperre(name, katalogName))
}
