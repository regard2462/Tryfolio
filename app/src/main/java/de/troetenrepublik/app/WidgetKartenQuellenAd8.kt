package de.troetenrepublik.app

import de.troetenrepublik.app.data.WidgetKarte
import de.troetenrepublik.app.data.WidgetKartenBau
import de.troetenrepublik.app.engine.Haeuserliste
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.market.Gesamt13f
import de.troetenrepublik.app.ui.CopyRouten
import de.troetenrepublik.app.ui.HaeuserBestand
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.ui.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Daten der Widgets Meistgekauft und Haus-Rangliste (0.77). Beide lesen nur mitgelieferte Dateien (Gesamtbild der 13F-Melder,
// Rangliste der Analystenhaeuser), es gibt keinen Abruf. Ohne lesbare Datei oder ohne Eintraege steht eine Karte ohne Zeilen
// mit dem Hinweis, damit das Widget nicht dauernd "Laedt" zeigt.

// Laenge, bis zu der ein Name statt des Kuerzels in der Zeile steht (ohne Leerzeichen, damit nichts umbricht).
private const val NAME_MAX = 9

// Name des Wertpapiers, wenn er kurz und ein Wort ist, sonst das Kuerzel; ein reines Zahlenkuerzel ersetzt immer der Name.
internal fun widgetBezeichnung(name: String, kuerzel: String): String {
    val n = name.trim()
    if (n.isNotEmpty() && HeatmapBeschriftung.nurZiffern(kuerzel)) return n
    return if (n.isNotEmpty() && n.length <= NAME_MAX && n.none { it.isWhitespace() }) n else kuerzel.trim().ifBlank { n }
}

// Die fuenf meistgekauften Werte der Datei (Netto-Kaeufer mit Zufluss, wie die Liste Meistgekauft in der App).
internal fun widgetGekaufte(d: Gesamt13f.Datei?): List<WidgetKartenBau.Gekauft> =
    d?.let { Gesamt13f.meistgekauft(it, 8) }.orEmpty().map { w ->
        WidgetKartenBau.Gekauft(widgetBezeichnung(CopyRouten.name(w.kuerzel, w.name), w.kuerzel), w.netto, w.fluss)
    }

internal fun widgetMeistgekauft(d: Gesamt13f.Datei?, jetzt: Long): WidgetKarte =
    WidgetKartenBau.meistgekauft(widgetGekaufte(d), d?.stichtag?.let { Fmt.date(Politik.tag(it)) }.orEmpty(), jetzt)

// Die drei Haeuser mit dem hoechsten Trefferanteil (die Liste steht schon danach geordnet).
internal fun widgetHaeuser(l: Haeuserliste.Liste?): List<WidgetKartenBau.Haus> =
    l?.haeuser.orEmpty().take(6).map { WidgetKartenBau.Haus(it.name, it.quote, it.urteile) }

internal suspend fun quelleMeistgekauft(jetzt: Long): WidgetKarte =
    withContext(Dispatchers.Default) { widgetMeistgekauft(runCatching { Gesamt13f.aktuell }.getOrNull(), jetzt) }

internal suspend fun quelleHaeuser(jetzt: Long): WidgetKarte =
    withContext(Dispatchers.Default) { WidgetKartenBau.haeuser(widgetHaeuser(runCatching { HaeuserBestand.holen() }.getOrNull()), jetzt) }
