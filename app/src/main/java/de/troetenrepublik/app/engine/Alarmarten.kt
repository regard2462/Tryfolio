package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.market.Wichtig
import java.time.DayOfWeek
import java.time.LocalDate

// Weitere Alarmarten neben Kursmarke und Tagesbewegung (0.76): Kreuzen des 50-Tage-Schnitts, doppeltes Normalvolumen,
// Erinnerung vor dem Ex-Tag der Dividende, Insider-Kauf. Alle bleiben nach der Meldung bestehen.
object Alarmarten {
    const val SCHNITT = "SMA50"
    const val VOLUMEN = "VOL2"
    const val EXDIV = "EXDIV"
    const val INSIDER = "INSIDER"
    // Seit 0.77 (Glocke je Wert): Insiderverkauf, 52-Wochen-Hoch oder -Tief, wichtige Meldungen, Analystenurteil,
    // Quartalszahlen, 8-K-Warnzeichen.
    const val INSIDER_VK = "INSIDERV"
    const val WOCHEN52 = "W52"
    const val NEWS = "NEWS"
    const val ANALYST = "ANALYST"
    const val QUARTAL = "QUARTAL"
    const val ACHTK = "ACHTK"
    val ALLE = listOf(SCHNITT, VOLUMEN, EXDIV, INSIDER, INSIDER_VK, WOCHEN52, NEWS, ANALYST, QUARTAL, ACHTK)

    // Meldungen, deren Text aus der Quelle stammt (Schlagzeile, Haus und Einstufung): er steht im Untertitel der Buchung.
    // Trenner zwischen Zeit und Titel im Merker der Meldungen
    private val TRENNER = 9.toChar()
    val TEXT_TAGS = setOf("ALERT_NEWS", "ALERT_ANALYST_UP", "ALERT_ANALYST_DOWN", RegelGlocke.TAG_KAUF, RegelGlocke.TAG_VERKAUF)

    // Tage fuer den Schnitt der Schlusskurse; das Normalvolumen nimmt die zehn Tage aus RelativesVolumen
    const val SCHNITT_TAGE = 50
    const val VOLUMEN_FAKTOR = 2.0

    fun istArt(a: PriceAlert): Boolean = a.art.isNotEmpty()

    // Mittel der letzten n Schlusskurse; null, wenn weniger da sind.
    fun schnitt(schluesse: List<Double>, n: Int = SCHNITT_TAGE): Double? =
        if (schluesse.size < n) null else schluesse.takeLast(n).average()

    // Normales Tagesvolumen: Mittel der letzten zehn abgeschlossenen Tage mit Umsatz.
    fun normalVolumen(kerzen: List<Candle>): Double? = RelativesVolumen.schnitt(kerzen.map { it.volume })

    // Letzter Kauftag vor dem Ex-Tag: der Handelstag davor, ohne Wochenende und Boersenfeiertage des Landes.
    fun letzterKauftag(exTag: Long, land: String): Long {
        var d = LocalDate.ofEpochDay(exTag - 1)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY || Boersenzeiten.feiertag(land, d)) d = d.minusDays(1)
        return d.toEpochDay()
    }

    // Boersenland fuer die Feiertage aus der Endung des Kuerzels; ohne Endung USA, unbekannt leer.
    fun land(symbol: String): String {
        if ('.' !in symbol) return "US"
        return when (symbol.substringAfterLast('.').uppercase()) {
            "DE", "F", "SG", "BE", "DU", "MU", "HM", "HA" -> "DE"
            "L", "IL" -> "GB"
            "PA" -> "FR"
            "AS" -> "NL"
            "BR" -> "BE"
            "LS" -> "PT"
            "T" -> "JP"
            else -> ""
        }
    }

    // Daten, mit denen ein Alarm geprueft wird. Fehlt etwas, meldet die Art nicht.
    data class Daten(
        val kurs: Double,
        val volumen: Double,
        val schnitt50: Double?,
        val normalVolumen: Double?,
        val exTag: Long?,
        val insider: List<SecInsider.Geschaeft>,
        val heute: Long,
        val land: String,
        // Der Kurs stammt vom heutigen Handelstag.
        val aktuell: Boolean,
        // 52-Wochen-Spanne und Spanne des Tages (alle in derselben Waehrung wie der Kurs); 0 = unbekannt.
        val hoch52: Double = 0.0,
        val tief52: Double = 0.0,
        val tagesHoch: Double = 0.0,
        val tagesTief: Double = 0.0,
        // Tag der naechsten Quartalszahlen (Tage seit 1970).
        val quartalTag: Long? = null,
        // Urteile der Analysten; null = nicht abgerufen.
        val analyst: List<de.troetenrepublik.app.engine.Analystenbilanz.Urteil>? = null,
        // Schluessel (Eingang und Kennung) der zaehlenden 8-K-Warnzeichen; null = nicht abgerufen.
        val achtK: List<String>? = null,
        // Wichtige Schlagzeilen zu diesem Wert, noch nicht gemeldet oder nicht.
        val news: List<NewsTreffer> = emptyList(),
        // Lage einer Regel des Strategietests nach dem letzten Schluss; null = nicht geladen.
        val regelLage: RegelGlocke.Lage? = null,
    )

    data class NewsTreffer(val zeit: Long, val titel: String)

    // neu: der Alarm nach der Pruefung (Stand und Tag der Meldung), gemeldet: ob jetzt eine Meldung faellig ist,
    // extra: der Wert zur Meldung (Schnitt, Faktor, letzter Kauftag als Tag seit 1970, Wert in Dollar).
    data class Ergebnis(val neu: PriceAlert, val gemeldet: Boolean, val extra: Double = 0.0, val tag: String = "", val text: String = "")

    fun pruefen(a: PriceAlert, d: Daten): Ergebnis {
        if (RegelGlocke.istArt(a.art)) return RegelGlocke.pruefen(a, d.regelLage, d.heute)
        when (a.art) {
            SCHNITT -> {
                val s = d.schnitt50
                if (s == null || d.kurs <= 0 || !d.aktuell) return Ergebnis(a, false)
                val seite = if (d.kurs >= s) 1 else -1
                if (a.zustand == 0) return Ergebnis(a.copy(zustand = seite), false)
                if (seite == a.zustand) return Ergebnis(a, false)
                val melden = a.gemeldetAm != d.heute
                return Ergebnis(
                    a.copy(zustand = seite, gemeldetAm = if (melden) d.heute else a.gemeldetAm), melden, s,
                    if (seite > 0) "ALERT_SMA_UP" else "ALERT_SMA_DOWN",
                )
            }
            VOLUMEN -> {
                val n = d.normalVolumen
                val r = n?.let { RelativesVolumen.wert(d.volumen, it) }
                if (r == null || !d.aktuell || r < VOLUMEN_FAKTOR || a.gemeldetAm == d.heute) return Ergebnis(a, false)
                return Ergebnis(a.copy(gemeldetAm = d.heute), true, d.volumen / n, "ALERT_VOLUME")
            }
            EXDIV -> {
                val ex = d.exTag ?: return Ergebnis(a, false)
                if (ex <= d.heute) return Ergebnis(a, false)
                val kauftag = letzterKauftag(ex, d.land)
                // Am letzten Kauftag und am Handelstag davor
                if (d.heute < kauftag - 1 || a.merk == ex.toString()) return Ergebnis(a, false)
                return Ergebnis(a.copy(merk = ex.toString()), true, kauftag.toDouble(), "ALERT_EXDIV")
            }
            INSIDER, INSIDER_VK -> {
                val kauf = a.art == INSIDER
                val seit = LocalDate.ofEpochDay(a.createdAt / 86_400_000L).toString()
                fun schluessel(g: SecInsider.Geschaeft) = g.f.ifEmpty { g.d } + "|" + g.a
                val neuer = d.insider.filter { it.kauf == kauf && it.k.equals(a.symbol, true) && it.f.ifEmpty { it.d } >= seit && schluessel(it) > a.merk }
                    .maxByOrNull { schluessel(it) } ?: return Ergebnis(a, false)
                return Ergebnis(a.copy(merk = schluessel(neuer)), true, neuer.wert, if (kauf) "ALERT_INSIDER" else "ALERT_INSIDER_SELL")
            }
            WOCHEN52 -> {
                if (d.kurs <= 0 || !d.aktuell) return Ergebnis(a, false)
                val hoch = d.hoch52 > 0 && maxOf(d.kurs, d.tagesHoch) >= d.hoch52 * (1 - 1e-6)
                val tief = d.tief52 > 0 && minOf(d.kurs, if (d.tagesTief > 0) d.tagesTief else d.kurs) <= d.tief52 * (1 + 1e-6)
                // Eine Meldung je Richtung und Handelstag
                val (marke, tag, wert) = when {
                    hoch -> Triple("H" + d.heute, "ALERT_HIGH52", d.hoch52)
                    tief -> Triple("T" + d.heute, "ALERT_LOW52", d.tief52)
                    else -> return Ergebnis(a, false)
                }
                if (a.merk == marke) return Ergebnis(a, false)
                return Ergebnis(a.copy(merk = marke), true, wert, tag)
            }
            NEWS -> {
                val letzte = a.merk.substringBefore(TRENNER).toLongOrNull() ?: 0L
                val vorherTitel = a.merk.substringAfter(TRENNER, "")
                val neuer = d.news.filter { it.zeit > maxOf(a.createdAt, letzte) }.maxByOrNull { it.zeit } ?: return Ergebnis(a, false)
                val merk = neuer.zeit.toString() + TRENNER + neuer.titel.replace(TRENNER, ' ').replace(10.toChar(), ' ')
                // Dieselbe Geschichte in anderer Formulierung meldet nicht noch einmal
                if (vorherTitel.isNotEmpty() && Wichtig.gleicheGeschichte(vorherTitel, neuer.titel)) return Ergebnis(a.copy(merk = merk), false)
                return Ergebnis(a.copy(merk = merk), true, 0.0, "ALERT_NEWS", neuer.titel)
            }
            ANALYST -> {
                val urteile = d.analyst ?: return Ergebnis(a, false)
                val letzte = a.merk.toLongOrNull() ?: 0L
                val neuer = urteile.filter { it.zeit > maxOf(a.createdAt, letzte) && Analystenbilanz.richtung(it) != null }.maxByOrNull { it.zeit }
                    ?: return Ergebnis(a.copy(gemeldetAm = d.heute), false)
                val hoch = Analystenbilanz.richtung(neuer)!! > 0
                return Ergebnis(a.copy(merk = neuer.zeit.toString(), gemeldetAm = d.heute), true, 0.0, if (hoch) "ALERT_ANALYST_UP" else "ALERT_ANALYST_DOWN", neuer.haus + " · " + neuer.nach)
            }
            QUARTAL -> {
                val tag = d.quartalTag ?: return Ergebnis(a, false)
                if (tag < d.heute || a.merk == tag.toString()) return Ergebnis(a, false)
                // Am letzten Handelstag vor dem Termin und am Termin selbst
                if (d.heute < letzterKauftag(tag, d.land)) return Ergebnis(a, false)
                return Ergebnis(a.copy(merk = tag.toString()), true, tag.toDouble(), "ALERT_EARNINGS")
            }
            ACHTK -> {
                val schluessel = d.achtK ?: return Ergebnis(a, false)
                val seit = LocalDate.ofEpochDay(a.createdAt / 86_400_000L).toString()
                val neue = schluessel.filter { it.substringBefore('|') >= seit && it > a.merk }
                val geprueft = a.copy(gemeldetAm = d.heute)
                if (neue.isEmpty()) return Ergebnis(geprueft, false)
                return Ergebnis(geprueft.copy(merk = neue.max()), true, neue.size.toDouble(), "ALERT_8K")
            }
        }
        return Ergebnis(a, false)
    }
}
