package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Gesamt13f
import kotlin.math.abs

// Abschnitt "Alle Fonds im Quartal" bei den Investoren (0.77): zugeklappt die Zahl der Melder und das Quartal, aufgeklappt
// Meistgekauft und Meistverkauft aus den Meldungen aller 13F-Melder (Gesamt13f). Je Liste stehen fuenf Zeilen, der Rest
// hinter "weitere". Jede Zeile ist ein Eintrag der LazyColumn.
internal class Gesamt13fZustand(
    val offen: Boolean, val kaufMehr: Boolean, val verkaufMehr: Boolean,
    val umschalten: () -> Unit, val kaufUmschalten: () -> Unit, val verkaufUmschalten: () -> Unit,
)

private const val SICHTBAR = 5

internal fun LazyListScope.gesamt13fAbschnitt(
    state: AppState, datei: Gesamt13f.Datei?, kauf: List<Gesamt13f.Wert>, verkauf: List<Gesamt13f.Wert>,
    z: Gesamt13fZustand, oeffnen: (String) -> Unit,
) {
    if (datei == null || (kauf.isEmpty() && verkauf.isEmpty())) return
    item(key = "g13-kopf", contentType = "kern-kopf") {
        WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.meistgekauft(), Modifier.fillMaxWidth()) {
            KernKopf(
                S.g13Titel, KernZeile(listOf(KernTeil(S.g13KernMelder(Fmt.num(datei.melder.toDouble(), 0), quartalKurz(datei.stichtag))))),
                z.offen, gross = false, onClick = z.umschalten,
            )
        }
    }
    if (!z.offen) return
    liste("g13k", S.g13Gekauft, S.g13Netto(true), kauf, z.kaufMehr, z.kaufUmschalten, state, oeffnen, de.troetenrepublik.app.data.WidgetVorgabe.meistgekauft())
    liste("g13v", S.g13Verkauft, S.g13Netto(false), verkauf, z.verkaufMehr, z.verkaufUmschalten, state, oeffnen)
    item(key = "g13-stand", contentType = "kern-text") {
        Text(
            S.g13Stand(Fmt.num(datei.melder.toDouble(), 0), Fmt.date(runCatching { java.time.LocalDate.parse(datei.bis).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L))),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

private fun LazyListScope.liste(
    id: String, titel: String, spalte: String, werte: List<Gesamt13f.Wert>, mehr: Boolean, umschalten: () -> Unit, state: AppState, oeffnen: (String) -> Unit,
    widget: de.troetenrepublik.app.data.WidgetVorgabe.Vorgabe? = null,
) {
    if (werte.isEmpty()) return
    // Ueberschrift der Liste, rechts die Beschriftung der Zahlenspalte
    item(key = "$id-titel", contentType = "kern-titel") {
        WidgetLangDruck(state, widget, Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Zwischenkopf(titel, Modifier.weight(1f))
                Text(
                    spalte, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false, modifier = Modifier.padding(end = 20.dp, bottom = 5.dp),
                )
            }
        }
    }
    val groesste = werte.maxOf { abs(it.netto) }.coerceAtLeast(1)
    val sichtbar = if (mehr) werte else werte.take(SICHTBAR)
    items(sichtbar.size, key = { "$id-" + sichtbar[it].kuerzel }, contentType = { "g13-zeile" }) { i ->
        Gesamt13fZeile(state, sichtbar[i], groesste, oeffnen)
    }
    if (werte.size > SICHTBAR) item(key = "$id-mehr", contentType = "g13-mehr") {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = umschalten).heightIn(min = 44.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (mehr) S.g13Weniger else S.g13Weitere(werte.size - SICHTBAR), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f), maxLines = 1, softWrap = false,
            )
            AufklappPfeil(mehr)
        }
    }
}

// Name mit Kuerzel, darunter Wertfluss und Zahl der Halter, ein Balken im Verhaeltnis der Netto-Kaeufer zum groessten
// Wert der Liste; rechts Dreieck und Zahl der Netto-Kaeufer bzw. -Verkaeufer.
@Composable
private fun Gesamt13fZeile(state: AppState, w: Gesamt13f.Wert, groesste: Int, oeffnen: (String) -> Unit) {
    val signal = LocalSignal.current
    val sym = CopyRouten.symbol(w.kuerzel)
    val name = CopyRouten.name(w.kuerzel, w.name)
    val farbe = if (w.netto >= 0) signal.up else signal.down
    Row(
        Modifier.fillMaxWidth().clickable { oeffnen("instrument/$sym") }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentLogo(state, sym, name, size = 36)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(name, zusatz = " (${w.kuerzel})", style = MaterialTheme.typography.titleSmall)
            PunktText(S.g13Unten(w.fluss >= 0, Fmt.dollarKompakt(abs(w.fluss)), Fmt.num(w.halter.toDouble(), 0)), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth((abs(w.netto).toFloat() / groesste).coerceIn(0.02f, 1f)).fillMaxSize().background(farbe))
            }
        }
        Spacer(Modifier.width(12.dp))
        ChangeValue(change = w.netto.toDouble(), text = Fmt.num(abs(w.netto).toDouble(), 0), style = MaterialTheme.typography.titleSmall, colored = true)
    }
}
