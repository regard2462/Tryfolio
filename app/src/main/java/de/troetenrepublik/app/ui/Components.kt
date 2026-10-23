package de.troetenrepublik.app.ui

import de.troetenrepublik.app.AppState
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import de.troetenrepublik.app.plattform.mausWischen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols

// Emblem mit den Anfangsbuchstaben als Platzhalter fuer ein Firmenlogo.
@Composable
fun InstrumentAvatar(symbol: String, name: String, size: Int = 36) {
    val label = remember(symbol, name) { initials(symbol, name) }
    val bg = remember(symbol) { colorFor(symbol) }
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = (size * 0.36).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun initials(symbol: String, name: String): String {
    val clean = name.trim()
    if (clean.isNotEmpty()) {
        val words = clean.split(" ", "-").filter { it.isNotBlank() && it[0].isLetterOrDigit() }
        if (words.size >= 2) return (words[0].take(1) + words[1].take(1)).uppercase()
        return clean.take(2).uppercase()
    }
    return symbol.filter { it.isLetterOrDigit() }.take(2).uppercase()
}

private val palette = listOf(
    Color(0xFF2D6CDF), Color(0xFF0F9D58), Color(0xFFDB4437), Color(0xFF7A4FCF),
    Color(0xFFE08A1E), Color(0xFF0E8A8A), Color(0xFFB0426B), Color(0xFF4A5A6B),
)

private fun colorFor(symbol: String): Color {
    var h = 0
    for (c in symbol) h = h * 31 + c.code
    return palette[Math.floorMod(h, palette.size)]
}

// Kennzahl mit gezeichnetem Richtungsdreieck davor.
@Composable
fun MetricValue(
    change: Double,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    colored: Boolean = true,
) {
    ChangeValue(change = change, text = text, modifier = modifier, style = style, colored = colored)
}

@Composable
fun SectionHeader(
    title: String,
    chevron: Boolean = false,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (chevron) {
            Spacer(Modifier.width(7.dp))
            ChevronRight(height = 14.dp)
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Radius.chip))
                    .then(if (onTrailingClick != null) Modifier.clickable(onClick = onTrailingClick) else Modifier)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                ChevronDown(width = 10.dp)
            }
        }
    }
}

// rightCaption ist eine kleine Zeile unter der Kennzahl. Ein Tipp auf die Kennzahl schaltet zur naechsten Darstellung.
@Composable
fun AssetRow(
    symbol: String,
    name: String,
    subtitle: String? = null,
    change: Double? = null,
    changeText: String? = null,
    rightPlain: String? = null,
    showAvatar: Boolean = true,
    subtitleColor: Color? = null,
    subtitleLines: Int = 1,
    rightCaption: String? = null,
    onValueClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = if (onValueClick != null) 10.dp else 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAvatar) {
            InstrumentLogo(symbol, name)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            // Name einzeilig, in voller Groesse und ohne Auslassungszeichen, zu lange Namen sinnvoll gekuerzt
            NamenText(name, style = MaterialTheme.typography.titleSmall)
            if (!subtitle.isNullOrBlank()) {
                if (subtitleLines == 1) {
                    VollText(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        val valueModifier = if (onValueClick != null)
            Modifier.clip(RoundedCornerShape(Radius.chip)).clickable(onClick = onValueClick).padding(horizontal = 10.dp, vertical = 8.dp)
        else Modifier
        Column(horizontalAlignment = Alignment.End) {
            when {
                changeText != null && change != null -> MetricValue(change, changeText, modifier = valueModifier)
                rightPlain != null -> Text(rightPlain, style = MaterialTheme.typography.titleSmall, modifier = valueModifier)
            }
            if (rightCaption != null) {
                Text(
                    rightCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(end = if (onValueClick != null) 10.dp else 0.dp),
                )
            }
        }
    }
}

// Zeile mit Bezeichnung und Wert, optional antippbar, etwa zum Kopieren der ISIN.
@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color? = null, onClick: (() -> Unit)? = null, hint: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

// Kachel in der Schnellzugriffszeile unter dem Chart.
@Composable
fun QuickTile(title: String, value: String?, plus: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            // Laengere Titel machen die Kachel breiter statt hoeher
            .widthIn(min = 150.dp)
            .clip(RoundedCornerShape(Radius.tile))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
        if (plus) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                PlusGlyph(size = 14.dp, color = MaterialTheme.colorScheme.onSurface, stroke = 2.dp)
            }
        } else {
            // Gleiche Hoehe wie der Plus-Knopf, damit alle Kacheln der Reihe gleich hoch sind
            Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) {
                Text(value.orEmpty(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// Kachel im Raster der Startseite: Titel links, rechts die Zahl oder ein Plus fuer leere Listen.
@Composable
fun KachelKompakt(title: String, value: String?, plus: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.tile))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VollText(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        when {
            plus -> Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
            !value.isNullOrBlank() -> Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

// Die Lage wird nur beim ersten Aufbau gelesen und nicht beobachtet. Sonst loeste das Fortschreiben beim Scrollen
// je Bildschritt einen Neuaufbau der ganzen Seite aus.
@Composable
fun rememberListeAb(index: () -> Int, versatz: () -> Int): androidx.compose.foundation.lazy.LazyListState =
    androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { androidx.compose.foundation.lazy.LazyListState(index(), versatz()) }
    }

@Composable
fun EmptyHint(text: String, aktion: Pair<String, () -> Unit>? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (aktion != null) Spacer(Modifier.height(12.dp))
        if (aktion != null) FilterPill(aktion.first, onClick = aktion.second)
    }
}

fun typeLabel(type: AssetType): String = S.typeLabel(type)

fun displayName(symbol: String, fallback: String = ""): String =
    Symbols.find(symbol)?.name ?: fallback.ifBlank { symbol }

// Kennzahlen seitenweise, waagerecht blaetterbar: jede Wischbewegung rastet auf der naechsten Spalte ein, Punkte
// darunter zeigen die Seite. Leere Eintraege fallen weg, damit keine Luecken entstehen. Ein Tipp auf eine Kennzahl
// erklaert sie in einfachen Worten (S.kennzahlErklaerung); aktion liefert fuer einzelne Zeilen einen zusaetzlichen
// Knopf im Fenster (etwa „ISIN kopieren“). Zeilen ohne Erklaerung rufen onRowClick direkt auf.
@Composable
fun StatPages(
    pages: List<List<Pair<String, String>>>,
    onRowClick: ((String) -> Unit)? = null,
    aktion: (String) -> Pair<String, () -> Unit>? = { null },
) {
    val filled = pages.filter { it.isNotEmpty() }
    if (filled.isEmpty()) return
    var offen by remember { mutableStateOf<String?>(null) }
    fun tipp(label: String) {
        if (S.kennzahlErklaerung(label) != null) offen = label else onRowClick?.invoke(label)
    }
    val klickbar = onRowClick != null || filled.flatten().any { S.kennzahlErklaerung(it.first) != null }
    val pager = androidx.compose.foundation.pager.rememberPagerState { filled.size }
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Etwas schmaler als der Bildschirm, damit die naechste Seite hervorlugt
            val pageWidth = if (filled.size > 1) maxWidth - 36.dp else maxWidth
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                pageSize = androidx.compose.foundation.pager.PageSize.Fixed(pageWidth),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().mausWischen(pager),
            ) { i ->
                Column(Modifier.width(pageWidth)) {
                    filled[i].forEach { (label, value) -> KeyValueRow(label, value, onClick = if (klickbar) ({ tipp(label) }) else null) }
                }
            }
        }
        if (filled.size > 1) {
            // Punkte fuer die Seiten, der aktuelle kraeftig
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.Center) {
                repeat(filled.size) { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (i == pager.currentPage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
        if (klickbar) {
            Text(
                S.kennzahlTippen, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
    }
    offen?.let { label ->
        val extra = aktion(label)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { offen = null },
            title = { Text(label) },
            text = { Text(S.kennzahlErklaerung(label).orEmpty(), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { offen = null }) { Text(S.ok) } },
            dismissButton = extra?.let { (text, tun) -> { androidx.compose.material3.TextButton(onClick = { tun(); offen = null }) { Text(text) } } },
        )
    }
}

fun statPages(rows: List<Pair<String, String>?>, perPage: Int = 4): List<List<Pair<String, String>>> =
    rows.filterNotNull().chunked(perPage)

@Composable
fun Chip(label: String, active: Boolean, accent: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.chip))
            .background(if (active) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle.isNotBlank()) Text(
                subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 56.dp),
            )
        }
        ChevronRight(height = 13.dp)
    }
}

// Eigene Farben: mit den Vorgabefarben des Schemas waeren Knopf, Bahn und Rand im ausgeschalteten Zustand
// gleich grau und der Knopf unsichtbar.
@Composable
fun ToggleRow(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    val signal = LocalSignal.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = signal.up,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun BpsRow(options: List<Int>, current: Int, suffix: String = "", labels: List<String>? = null, onPick: (Int) -> Unit) {
    val signal = LocalSignal.current
    Row(Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, v ->
            val active = current == v
            Box(
                Modifier.clip(RoundedCornerShape(Radius.chip))
                    .background(if (active) signal.up.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onPick(v) }.padding(horizontal = 13.dp, vertical = 9.dp),
            ) { Text(labels?.getOrNull(i) ?: "$v$suffix", style = MaterialTheme.typography.labelMedium, color = if (active) signal.up else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun SegmentRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.chip))
                    .background(if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelect(i) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// Bezugsverhaeltnis in der ueblichen Schreibweise: 10 Scheine je Basiswert ergibt 0,1.
fun ratioText(ratio: Double): String =
    if (ratio <= 0) "–" else Fmt.num(1.0 / ratio, 3).trimEnd('0').trimEnd(Fmt.decimalSep)

@Composable
fun OptionRow(title: String, subtitle: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) CheckMark(size = 17.dp)
    }
}

@Composable
fun ActionLine(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!danger) ChevronRight(height = 13.dp)
    }
}

@Composable
fun ActionLineWide(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!danger) ChevronRight(height = 13.dp)
    }
}

@Composable
@Suppress("ModifierParameter") // handle gilt nur der Kopfzeile mit dem Pfeil, nicht dem ganzen Kopf
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    handle: Modifier = Modifier,
) {
    // handle macht die Kopfzeile zum Griff, an dem sich die Seite nach unten wegwischen laesst
    Row(Modifier.fillMaxWidth().then(handle).padding(start = 8.dp, top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
    Text(title, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp), style = MaterialTheme.typography.headlineLarge)
    if (subtitle != null) {
        Text(
            subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

// Betrag antippen schaltet die Anzeige zwischen Euro und US-Dollar um.
fun Modifier.waehrungWechsel(state: AppState): Modifier = this
    .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = S.switchCurrency) { state.waehrungUmschalten() }

// Betrag antippen mit eigener Folge, auf der Wertpapierseite Euro, Dollar und Heimatwaehrung.
fun Modifier.waehrungWechsel(onClick: () -> Unit): Modifier = this
    .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = S.switchCurrency, onClick = onClick)
