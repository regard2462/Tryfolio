package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.DerivateAnzeige
import de.troetenrepublik.app.engine.Kursspanne
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi

// Bausteine des oberen Teils der Wertpapierseite (5.92): Symbole der Kopfzeile, kleine Schalter unter dem Chart,
// die Zeile zur erwarteten Kursspanne, die eigene Notiz, die aufklappbare Beschreibung und die Derivate-Karte (5.93).

// Rundes Symbol in der Kopfzeile wie das Plus der Watchlist: 48 dp Tippflaeche, darin ein Kreis.
@Composable
internal fun KopfSymbol(icon: ImageVector, beschreibung: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// Kleiner Schalter in grauer Schrift wie „Indikatoren“; eingeschaltet mit Haken davor.
@Composable
internal fun KleinerSchalter(text: String, an: Boolean, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clip(RoundedCornerShape(Radius.chip)).clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (an) {
            CheckMark(size = 10.dp, color = grau, stroke = 1.5.dp)
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (an) MaterialTheme.colorScheme.onSurface else grau, maxLines = 1)
    }
}

// Erwartete Kursspanne in 30 Tagen als eine Zeile unter dem Chart; ein Tipp zeigt Grundlage und Erklaerung. Ohne
// Daten zur Schwankung bleibt die Zeile weg.
@Composable
internal fun KursspanneZeile(spanne: Kursspanne.Spanne?, geladen: Boolean, kf: KursFormat) {
    if (spanne == null || !geladen) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var offen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                S.p1Spanne(kf.preis(spanne.unten), kf.preis(spanne.oben), spanne.tage),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            ChevronDown(width = 8.dp, color = grau.copy(alpha = 0.6f), modifier = Modifier.rotate(if (offen) 180f else 0f))
        }
        if (offen) {
            Text(
                S.kcSpanneQuelle(spanne.quelle == Kursspanne.Quelle.IMPLIZIT, Fmt.pct(spanne.sigma * 100, 0)),
                style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp),
            )
            Text(S.p1SpanneMehr, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// Eigene Notiz zum Wert unter der Position; ein Tipp oeffnet sie. Ohne Notiz steht hier nichts, angelegt wird sie
// ueber das Symbol in der Kopfzeile.
@Composable
internal fun NotizZeile(notiz: String, onClick: () -> Unit) {
    if (notiz.isBlank()) return
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(S.notiz, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AuslaufText(notiz, maxLines = 4, style = MaterialTheme.typography.bodyMedium)
    }
}

// Beschreibungstext, zugeklappt drei Zeilen. Ist er laenger, steht am Ende der dritten Zeile ein kleiner Pfeil nach
// unten, der in den Text hinein auslaeuft; ein Tipp klappt auf und wieder zu.
@Composable
internal fun GekuerzterText(text: String, offen: Boolean, onClick: () -> Unit) {
    var gekuerzt by remember(text) { mutableStateOf(false) }
    val grund = flaeche()
    // Weiche Trennstriche aus der Quelle entfernen, damit kein Wort getrennt wird
    val ohneTrennung = remember(text) { text.replace(Char(0x00AD).toString(), "") }
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp)) {
        Text(
            ohneTrennung,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (offen) 200 else 3,
            overflow = TextOverflow.Clip,
            lineHeight = 24.sp,
            onTextLayout = { if (!offen) gekuerzt = it.hasVisualOverflow },
        )
        if (!offen && gekuerzt) Blende(grund, Modifier.align(Alignment.BottomEnd), klein = false)
    }
}

// Derivate: gruener und roter Long/Short-Balken wie im Detailblatt der Optionen, bei auslaendischen Aktien
// ueber die US-Notierung; ohne Optionsdaten nur die antippbare Zeile (5.96).
@Composable
internal fun HebelKarte(state: AppState, symbol: String, mitOptionen: Boolean, onLong: () -> Unit, onShort: () -> Unit) {
    // Zustand: geladen = Ergebnis liegt vor (auch ohne Daten). Start aus dem Zwischenspeicher, damit nichts springt.
    val vorab = remember(symbol, mitOptionen) { if (mitOptionen) state.market.optionVolumesCached(symbol) else Pair(true, null) }
    var optionen by remember(symbol, mitOptionen) { mutableStateOf<MarketApi.OptionVolumes?>(vorab?.second) }
    var geladen by remember(symbol, mitOptionen) { mutableStateOf(vorab != null) }
    LaunchedEffect(symbol, mitOptionen) {
        if (!mitOptionen) { optionen = null; geladen = true; return@LaunchedEffect }
        if (geladen) return@LaunchedEffect
        optionen = runCatching { state.market.optionVolumes(symbol) }.getOrNull()
        geladen = true
    }
    val o = optionen?.takeIf { it.total > 0 }
    // Erwartung: der Wert selbst hat ein US-Optionskuerzel, dann liegen Optionsdaten fast immer vor.
    val erwartet = remember(symbol, mitOptionen) { mitOptionen && state.market.optionenDirekt(symbol) }
    val zeigen = DerivateAnzeige.zeigen(geladen, o != null, erwartet)
    SectionHeader(
        S.derivatives, chevron = true, onClick = onLong,
        unten = if (zeigen == DerivateAnzeige.Zeigen.NUR_ZEILE) 0.dp else 12.dp,
    )
    when (zeigen) {
        DerivateAnzeige.Zeigen.PLATZHALTER -> LongShortPlatzhalter(onLong)
        DerivateAnzeige.Zeigen.BALKEN -> LongShortBars(o?.longPct, onLong, onShort)
        DerivateAnzeige.Zeigen.NUR_ZEILE -> {}
    }
}
