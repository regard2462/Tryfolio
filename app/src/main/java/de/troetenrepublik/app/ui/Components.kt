package de.troetenrepublik.app.ui

import de.troetenrepublik.app.AppState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import de.troetenrepublik.app.plattform.mausWischen
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import androidx.compose.ui.layout.layout

// Emblem mit den Anfangsbuchstaben als Platzhalter fuer ein Firmenlogo.
@Composable
fun InstrumentAvatar(
    symbol: String,
    name: String,
    size: Int = 36,
    farbig: Boolean = LogoAnzeige.farbig,
    rund: Boolean = LogoAnzeige.rund,
) {
    val label = remember(symbol, name) { initials(symbol, name) }
    // Schwarz-weiss: Flaeche in Textfarbe (schwarz, im dunklen Modus weiss), Buchstaben in Seitenfarbe
    val bg = if (farbig) remember(symbol) { colorFor(symbol) } else MaterialTheme.colorScheme.onSurface
    val schrift = if (farbig) aufFarbe(bg) else MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * de.troetenrepublik.app.engine.LogoDarstellung.eckenAnteil(rund)).dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = schrift,
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

// Farbe aus der gemeinsamen Kategorienpalette, ohne Gruen und Rot.
private fun colorFor(symbol: String): Color {
    var h = 0
    for (c in symbol) h = h * 31 + c.code
    return Kategorie.farbe(h)
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

// Oberer Abstand der Abschnittstitel. Eine Seite setzt ihn fuer den Abschnitt kleiner, der direkt unter einer
// Knopfzeile beginnt (AbschnittNah); sonst gilt ueberall derselbe.
val LocalAbschnittOben = androidx.compose.runtime.compositionLocalOf { 28.dp }

// Abstand zwischen einer Knopfzeile und dem Titel des Abschnitts direkt darunter.
val ABSCHNITT_OBEN_NAH = 12.dp

// Abstand unter einem Abschnittstitel, dem direkt eine Zwischenueberschrift folgt; sie selbst steht dann ohne
// eigenen oberen Abstand (Zwischenkopf mit oben = 0), damit sich die beiden Abstaende nicht addieren.
val TITEL_VOR_ZWISCHENKOPF = 8.dp

// Inhalt, dessen Abschnittstitel naeher an das Element darueber ruecken, wenn nah gesetzt ist. Der Inhalt bleibt
// beim Wechsel derselbe, nur der Abstand aendert sich.
@Composable
fun AbschnittNah(nah: Boolean, inhalt: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalAbschnittOben provides if (nah) ABSCHNITT_OBEN_NAH else 28.dp, content = inhalt)
}

// Abschnittskopf mit Info-Symbol rechts; die Erklaerung steht erst im Blatt dahinter.
@Composable
fun SectionHeaderMitInfo(title: String, infoTitel: String, info: String) {
    var offen by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        androidx.compose.material3.IconButton(onClick = { offen = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
    if (offen) MarktInfoBlatt(infoTitel, info) { offen = false }
}

@Composable
fun SectionHeader(
    title: String,
    chevron: Boolean = false,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    unten: Dp = 12.dp,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp)
            .padding(top = LocalAbschnittOben.current, bottom = unten),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (chevron) {
            Spacer(Modifier.width(7.dp))
            ChevronRight(height = PfeilGroesse.KOPF)
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .then(if (onTrailingClick != null) Modifier.textKlick(8.dp, onClick = onTrailingClick) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // Platzaenderung vor der Unterzeile: gezeichnetes Dreieck mit Zahl (positiv: besser)
    subtitleRang: Int = 0,
    rightCaption: String? = null,
    onValueClick: (() -> Unit)? = null,
    state: AppState? = null,
    // Unterzeile in einer aufgeklappten Gruppe (etwa Aktien eines Themenkorbs): eingerueckt auf die Textkante der
    // Gruppenzeile, kleineres Logo und kleinere Schrift (0.79)
    eingerueckt: Boolean = false,
    onClick: () -> Unit,
) {
    // Mit state: langes Druecken oeffnet das Menue mit Watchlist und Widget, ein Tipp oeffnet weiter den Wert
    var menue by remember { mutableStateOf(false) }
    val nameStil = if (eingerueckt) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall
    val wertStil = if (eingerueckt) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall
    val haptik = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = if (state != null) ({ haptik.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menue = true }) else null,
                onClick = onClick,
            )
            .padding(
                start = if (eingerueckt) 70.dp else 20.dp, end = if (onValueClick != null) 10.dp else 20.dp,
                top = if (eingerueckt) 9.dp else 14.dp, bottom = if (eingerueckt) 9.dp else 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAvatar) {
            if (eingerueckt) InstrumentLogo(symbol, name, size = 30) else InstrumentLogo(symbol, name)
            Spacer(Modifier.width(if (eingerueckt) 12.dp else 14.dp))
        }
        Column(Modifier.weight(1f)) {
            // Name einzeilig, in voller Groesse und ohne Auslassungszeichen, zu lange Namen sinnvoll gekuerzt
            NamenText(name, style = nameStil)
            if (!subtitle.isNullOrBlank()) {
                if (subtitleRang != 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    ChangeValue(subtitleRang.toDouble(), kotlin.math.abs(subtitleRang).toString(), style = MaterialTheme.typography.bodySmall, triangleWidth = 8.dp, nullAusText = false)
                    Spacer(Modifier.width(8.dp))
                    PunktText(subtitle, color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                } else PunktText(subtitle, color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state != null) AktieMenue(state, symbol, menue) { menue = false }
        Spacer(Modifier.width(10.dp))
        // Mit Unterzeile gehoert die Tippflaeche um beide Zeilen, damit Wert und Datum auf Hoehe von Name und Kurs stehen.
        // Sie bekommt dann keinen Innenabstand oben und unten, sonst wird die Zeile hoeher als ohne Datum (5.98).
        val mitUnterzeile = rightCaption != null
        val valueModifier = if (onValueClick != null)
            Modifier.textKlick(10.dp, voll = mitUnterzeile, onClick = onValueClick)
                .padding(horizontal = 10.dp, vertical = if (mitUnterzeile) 0.dp else 8.dp)
        else Modifier
        Column(if (mitUnterzeile) valueModifier else Modifier, horizontalAlignment = Alignment.End) {
            val m = if (mitUnterzeile) Modifier else valueModifier
            when {
                changeText != null && change != null -> MetricValue(change, changeText, modifier = m, style = wertStil)
                rightPlain != null -> Text(rightPlain, style = wertStil, modifier = m)
            }
            if (rightCaption != null) {
                Text(
                    rightCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

// Menue nach langem Druecken auf einen Wert: Watchlist umschalten, Einzelwert-Widget anlegen (nur wo es Widgets gibt).
@Composable
fun AktieMenue(state: AppState, symbol: String, offen: Boolean, zu: () -> Unit) {
    androidx.compose.material3.DropdownMenu(
        expanded = offen, onDismissRequest = zu, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        DropdownMenuItem(
            text = { Text(if (state.mehrereWatchlisten) S.wllWaehlen else if (state.isWatched(symbol)) S.removeFromWatchlist else S.addToWatchlist) },
            onClick = { zu(); state.watchTippen(symbol) },
        )
        if (state.widgetAnheftbar) DropdownMenuItem(
            text = { Text(S.alsWidget) },
            onClick = { zu(); state.alsWidget(symbol) },
        )
    }
}

// Menue nach langem Druecken auf eine Kategorie (Kachel der Startseite, Reiter in Maerkte): Widget dazu anlegen. Gleiche
// Bedienung wie beim Wert (AktieMenue); art aus WidgetKarten.ANLEGBAR.
@Composable
fun KategorieMenue(state: AppState, art: String, offen: Boolean, einstellung: Map<String, String> = emptyMap(), zu: () -> Unit) {
    androidx.compose.material3.DropdownMenu(
        expanded = offen, onDismissRequest = zu, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        DropdownMenuItem(
            text = { Text(S.alsWidget) },
            onClick = { zu(); state.alsKategorieWidget(art, einstellung) },
        )
    }
}

// Menue zu einer Widget-Vorgabe (WidgetVorgabe): Art samt Voreinstellung der Stelle, an der gedrueckt wurde.
@Composable
fun KategorieMenue(state: AppState, vorgabe: de.troetenrepublik.app.data.WidgetVorgabe.Vorgabe, offen: Boolean, zu: () -> Unit) =
    KategorieMenue(state, vorgabe.art, offen, vorgabe.einstellung, zu)

// Langes Druecken auf einen Bereich (Ueberschrift, Abschnitt, Zeile ohne eigenen Tipp) legt das Widget dazu an (nur Android).
// Ohne Widget-Unterstuetzung oder ohne Vorgabe bleibt der Bereich, wie er ist.
@Composable
fun WidgetLangDruck(
    state: AppState, vorgabe: de.troetenrepublik.app.data.WidgetVorgabe.Vorgabe?, modifier: Modifier = Modifier, inhalt: @Composable () -> Unit,
) {
    // Column: Inhalt aus mehreren Teilen (Kopfzeile mit Pfeil und Titel) steht untereinander statt uebereinander
    if (vorgabe == null || !state.widgetAnheftbar) { Column(modifier) { inhalt() }; return }
    var menue by remember { mutableStateOf(false) }
    val haptik = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier.langDrueckenWidget {
            haptik.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            menue = true
        },
    ) {
        Column { inhalt() }
        KategorieMenue(state, vorgabe, menue) { menue = false }
    }
}

// Erkennt langes Druecken schon in der ersten Runde der Zeigerereignisse, also auch ueber Kindern mit eigenem Tipp (Pillen,
// Zeilen). Kurzes Tippen und Wischen laufen unberuehrt zum Kind; erst nach dem langen Druecken werden die restlichen Ereignisse
// der Geste verbraucht, damit das Kind nicht zusaetzlich antippt.
fun Modifier.langDrueckenWidget(onLong: () -> Unit): Modifier = pointerInput(Unit) {
    while (true) {
        awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val lang = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var abgebrochen = false
                while (!abgebrochen) {
                    val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { change -> change.id == down.id }
                    if (c == null || !c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) abgebrochen = true
                }
                false
            }
            if (lang == null) {
                onLong()
                do {
                    val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    ev.changes.forEach { change -> change.consume() }
                } while (ev.changes.any { change -> change.pressed })
            }
        }
    }
}

// Langes Druecken auf Reiter einer Leiste: ein Zustand fuer Haptik und das Menue des gedrueckten Reiters. langDruecken und
// menue gehen an MitlaufendeReiter; vorgabe liefert je Reiter die Vorgabe (null: kein Widget).
class ReiterWidgetMenue(
    val state: AppState, private val haptik: androidx.compose.ui.hapticfeedback.HapticFeedback,
    private val vorgabe: (Int) -> de.troetenrepublik.app.data.WidgetVorgabe.Vorgabe?,
) {
    var offen by mutableStateOf<Int?>(null)

    // Fuer MitlaufendeReiter.langDruecken: nur auf Android und nur fuer Reiter mit Vorgabe.
    val langDruecken: ((Int) -> Unit)?
        get() = if (!state.widgetAnheftbar) null else { i ->
            if (vorgabe(i) != null) { haptik.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); offen = i }
        }

    // Fuer MitlaufendeReiter.menue.
    @Composable
    fun menue(i: Int) {
        val v = vorgabe(i) ?: return
        KategorieMenue(state, v, offen == i) { offen = null }
    }
}

@Composable
fun rememberReiterWidgetMenue(state: AppState, vorgabe: (Int) -> de.troetenrepublik.app.data.WidgetVorgabe.Vorgabe?): ReiterWidgetMenue {
    val haptik = androidx.compose.ui.platform.LocalHapticFeedback.current
    val aktuell = androidx.compose.runtime.rememberUpdatedState(vorgabe)
    return remember(state, haptik) { ReiterWidgetMenue(state, haptik) { aktuell.value(it) } }
}

// Zeile mit Bezeichnung und Wert, optional antippbar, etwa zum Kopieren der ISIN. Passen beide nebeneinander, stehen
// sie in einer Zeile. Sonst rueckt der ganze Wert rechtsbuendig unter die Bezeichnung, statt sich mit ihr die Breite
// zu teilen und mitten im Betrag umzubrechen (5.94). Nur ein Wert, der allein breiter als die Zeile ist, bricht an
// Leerzeichen um; Zahl und Einheit haengen ueber das geschuetzte Leerzeichen zusammen.
@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color? = null, onClick: (() -> Unit)? = null, hint: String? = null) {
    val stil = MaterialTheme.typography.bodyMedium
    val messer = androidx.compose.ui.text.rememberTextMeasurer()
    val abstand = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.roundToPx() }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        val breite = constraints.maxWidth
        val (art, wertPasst) = remember(label, value, breite, stil) {
            fun w(t: String) = messer.measure(t, stil, maxLines = 1, softWrap = false).size.width
            val wert = w(value)
            de.troetenrepublik.app.engine.ZeilenLayout.art(w(label), wert, breite, abstand) to (wert <= breite)
        }
        // Bricht die Bezeichnung um, steht der Wert rechts auf Hoehe ihrer ersten Zeile
        val nebeneinander = art != de.troetenrepublik.app.engine.ZeilenLayout.Art.WERT_DARUNTER
        val wertText = @Composable { m: Modifier ->
            Text(
                value,
                style = stil,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                softWrap = !wertPasst,
                maxLines = if (wertPasst) 1 else Int.MAX_VALUE,
                modifier = m,
            )
        }
        if (nebeneinander) {
            Row(verticalAlignment = if (art == de.troetenrepublik.app.engine.ZeilenLayout.Art.NEBEN) Alignment.CenterVertically else Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = stil, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                wertText(Modifier)
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = stil, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                wertText(Modifier.fillMaxWidth().padding(top = 2.dp))
            }
        }
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
// Mit state und widget (Art aus WidgetKarten.ANLEGBAR) bietet langes Druecken das Widget zur Kachel an (nur Android).
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun KachelKompakt(
    title: String, value: String?, plus: Boolean = false, modifier: Modifier = Modifier,
    state: AppState? = null, widget: String? = null, onClick: () -> Unit,
) {
    var menue by remember { mutableStateOf(false) }
    val haptik = androidx.compose.ui.platform.LocalHapticFeedback.current
    val mitWidget = state != null && widget != null && state.widgetAnheftbar
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.tile))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                onLongClick = if (mitWidget) ({ haptik.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menue = true }) else null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mitWidget && state != null && widget != null) KategorieMenue(state, widget, menue) { menue = false }
        Beschriftung(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        when {
            plus -> Box(Modifier.padding(start = 6.dp)) { PlusGlyph(size = 14.dp, color = MaterialTheme.colorScheme.onSurfaceVariant, stroke = 2.dp) }
            !value.isNullOrBlank() -> Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
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
                            .background(if (i == pager.currentPage) MaterialTheme.colorScheme.onSurface else LocalSignal.current.rand),
                    )
                }
            }
        }
    }
    offen?.let { label ->
        val extra = aktion(label)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { offen = null },
            title = { Text(label) },
            text = { StichpunktText(S.kennzahlErklaerung(label).orEmpty(), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { offen = null }) { Text(S.ok) } },
            dismissButton = extra?.let { (text, tun) -> { TextButton(onClick = { tun(); offen = null }) { Text(text) } } },
        )
    }
}

fun statPages(rows: List<Pair<String, String>?>, perPage: Int = 4): List<List<Pair<String, String>>> =
    rows.filterNotNull().chunked(perPage)

// Einheitlicher Stil der Auswahl-Chips (Chip, BpsRow, SegmentRow, FilterPill, Filter in Entdecken, Zeitraeume der
// Statistik): aktiv gefuellt, sonst auf surfaceContainerHigh, mindestens 48 dp hoch, damit sie sich gut antippen lassen.
@Composable
fun auswahlChipFlaeche(active: Boolean, fill: Color = MaterialTheme.colorScheme.onSurface): Color =
    if (active) fill else MaterialTheme.colorScheme.surfaceContainerHigh

// Schrift passend zur Flaeche: auf der gefuellten Grundfarbe die Seitenfarbe, auf eigenen Farben Schwarz oder Weiss.
@Composable
fun auswahlChipSchrift(active: Boolean, fill: Color = MaterialTheme.colorScheme.onSurface): Color = when {
    !active -> MaterialTheme.colorScheme.onSurface
    fill == MaterialTheme.colorScheme.onSurface -> MaterialTheme.colorScheme.surface
    else -> aufFarbe(fill)
}

@Composable
fun Modifier.auswahlChip(
    active: Boolean,
    fill: Color = MaterialTheme.colorScheme.onSurface,
    horizontal: androidx.compose.ui.unit.Dp = 16.dp,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = this
    .heightIn(min = 48.dp)
    .clip(RoundedCornerShape(Radius.chip))
    .background(auswahlChipFlaeche(active, fill))
    .clickable(role = role, onClick = onClick)
    .padding(horizontal = horizontal, vertical = 10.dp)

@Composable
fun Chip(label: String, active: Boolean, accent: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Box(Modifier.auswahlChip(active, accent, onClick = onClick), contentAlignment = Alignment.Center) {
        // Auswahlpille bleibt einzeilig, ein Umbruch saehe falsch aus
        Text(label, style = MaterialTheme.typography.labelLarge, color = auswahlChipSchrift(active, accent), maxLines = 1, softWrap = false)
    }
}

@Composable
fun NavRow(title: String, subtitle: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle.isNotBlank()) PunktText(subtitle, modifier = Modifier.padding(end = 56.dp))
        }
        ChevronRight()
    }
}

// Eigene Farben: mit den Vorgabefarben des Schemas waeren Knopf, Bahn und Rand im ausgeschalteten Zustand
// gleich grau und der Knopf unsichtbar.
@Composable
fun ToggleRow(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
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
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
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
    Row(Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, v ->
            val active = current == v
            Box(Modifier.auswahlChip(active) { onPick(v) }, contentAlignment = Alignment.Center) {
                Text(labels?.getOrNull(i) ?: "$v$suffix", style = MaterialTheme.typography.labelLarge, color = auswahlChipSchrift(active))
            }
        }
    }
}

@Composable
fun SegmentRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            // Schmaler Innenrand, damit auch drei oder vier Teile nebeneinander Platz haben
            Box(
                Modifier.weight(1f).auswahlChip(active, horizontal = 8.dp) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = auswahlChipSchrift(active), textAlign = TextAlign.Center)
            }
        }
    }
}

// Quellenangabe unter Karten und Listen, einheitlich fuer die ganze App (0.68): kleine graue Zeile „Quelle · Nasdaq“,
// antippbar mit kleinem Pfeil am Ende; der Link oeffnet ueber LocalUriHandler, also im Betrachter der App (AppLinks).
@Composable
fun QuellenZeile(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.then(if (onClick != null) Modifier.textKlick(20.dp, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(quellenText(text), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.weight(1f, fill = false))
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            ChevronRight(color = grau.copy(alpha = 0.7f))
        }
    }
}

// „Quelle: Nasdaq“ wird „Quelle · Nasdaq“: ein einzelnes Wort vor dem ersten Doppelpunkt (bis 12 Zeichen) gilt als
// Beschriftung. Anderer Text bleibt, wie er ist.
fun quellenText(text: String): String {
    val i = text.indexOf(": ")
    if (i <= 0 || i > 12) return text
    val wort = text.substring(0, i)
    if (wort.any { it.isWhitespace() || it.isDigit() }) return text
    return wort + PUNKT + text.substring(i + 2)
}

// Trennzeichen zwischen Kurzangaben in Untertiteln und Zweitzeilen.
const val PUNKT = " · "

// Kurzangaben mit „ · “ dazwischen. Umgebrochen wird nur zwischen den Teilen, damit keine Angabe mitten im Wort oder
// zwischen Zahl und Einheit auseinanderreisst; der Punkt bleibt am Zeilenende weg. Ein Teil, der allein breiter ist als
// die Zeile, bricht in sich um. maxZeilen begrenzt die Zeilen, was nicht mehr passt, entfaellt.
@Composable
fun PunktZeile(
    teile: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxZeilen: Int = Int.MAX_VALUE,
    flaeche: Color = flaeche(),
) {
    val liste = teile.filter { it.isNotBlank() }
    if (liste.isEmpty()) return
    if (liste.size == 1) {
        AuslaufText(liste[0], maxLines = maxZeilen, modifier = modifier, style = style, color = color, flaeche = flaeche)
        return
    }
    androidx.compose.ui.layout.Layout(
        content = {
            liste.forEach { Text(it, style = style, color = color) }
            repeat(liste.size - 1) { Text(PUNKT, style = style, color = color, maxLines = 1, softWrap = false) }
        },
        modifier = modifier,
    ) { messbar, c ->
        val breite = c.maxWidth
        val frei = c.copy(minWidth = 0, minHeight = 0)
        val teil = messbar.take(liste.size).map { it.measure(frei) }
        val punkt = messbar.drop(liste.size).map { it.measure(frei) }
        // Zeilen bilden: je Zeile die Indizes der Teile
        val zeilen = ArrayList<MutableList<Int>>()
        var x = 0
        teil.forEachIndexed { i, p ->
            val mitPunkt = x + (punkt.getOrNull(i - 1)?.width ?: 0) + p.width
            if (zeilen.isEmpty() || (breite != androidx.compose.ui.unit.Constraints.Infinity && mitPunkt > breite)) {
                zeilen += mutableListOf(i); x = p.width
            } else {
                zeilen.last() += i; x = mitPunkt
            }
        }
        val sichtbar = zeilen.take(maxZeilen.coerceAtLeast(1))
        val hoehen = sichtbar.map { z -> z.maxOf { teil[it].height } }
        val weite = sichtbar.maxOf { z -> z.sumOf { teil[it].width } + z.drop(1).sumOf { punkt[it - 1].width } }
        val w = if (breite == androidx.compose.ui.unit.Constraints.Infinity) weite else weite.coerceIn(c.minWidth, breite)
        layout(w, hoehen.sum().coerceAtLeast(c.minHeight)) {
            var y = 0
            sichtbar.forEachIndexed { zi, z ->
                var px = 0
                z.forEachIndexed { k, i ->
                    if (k > 0) { punkt[i - 1].placeRelative(px, y); px += punkt[i - 1].width }
                    teil[i].placeRelative(px, y); px += teil[i].width
                }
                y += hoehen[zi]
            }
        }
    }
}

// Ein Untertitel, der mit „ · “ zusammengesetzt ist, als PunktZeile.
@Composable
fun PunktText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxZeilen: Int = Int.MAX_VALUE,
    flaeche: Color = flaeche(),
) = PunktZeile(remember(text) { text.split(PUNKT) }, modifier, style, color, maxZeilen, flaeche)

// Klickflaeche eines Textknopfs: seitlich 10 dp breiter als die Schrift, die Bestaetigung ist das abgerundete Feld
// aus Tippflaeche.kt. Im Aufbau belegt der Knopf nur die Breite der Schrift, die Flaeche ragt seitlich hinaus.
internal fun Modifier.textKnopf(vertikal: Dp = 6.dp, onClick: () -> Unit): Modifier = this
    .layout { messbar, grenzen ->
        val rand = TEXTKNOPF_RAND.roundToPx()
        val weit = if (grenzen.hasBoundedWidth) grenzen.copy(maxWidth = grenzen.maxWidth + 2 * rand) else grenzen
        val p = messbar.measure(weit)
        layout((p.width - 2 * rand).coerceAtLeast(0), p.height) { p.place(-rand, 0) }
    }
    .textKlick(TEXTKNOPF_RAND, role = Role.Button, onClick = onClick).padding(horizontal = TEXTKNOPF_RAND, vertical = vertikal)

private val TEXTKNOPF_RAND = 10.dp

// Zwischenueberschrift innerhalb eines Abschnitts (unter SectionHeader), ueberall gleich gross und gleich eingerueckt.
@Composable
fun Zwischenkopf(text: String, modifier: Modifier = Modifier, oben: Dp = 12.dp) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier.padding(start = 20.dp, end = 20.dp, top = oben, bottom = 4.dp))
}

// Ampelzeichen als eigene Form (5.94), damit die Stufe ohne Farbe und ohne Nachlesen erkennbar ist: gruener Kreis mit
// Haken (gut), gelbes Warndreieck mit Ausrufezeichen (Achtung), roter Kreis mit Kreuz (kritisch), grauer Kreis mit i
// (Hinweis). Gezeichnet statt als Schriftzeichen, damit es auch klein scharf bleibt.
@Composable
fun AmpelPunkt(zeichen: String, farbe: Color, modifier: Modifier = Modifier) {
    val groesse = AMPEL_GROESSE
    val strich = aufFarbe(farbe)
    val beschreibung = when (zeichen) {
        AmpelZeichen.GUT -> S.ampelGut; AmpelZeichen.ACHTUNG -> S.ampelAchtung; AmpelZeichen.KRITISCH -> S.ampelKritisch; else -> S.ampelHinweis
    }
    androidx.compose.foundation.Canvas(modifier.size(groesse).semantics { contentDescription = beschreibung }) {
        val w = size.width
        val d = w * 0.12f
        val stift = androidx.compose.ui.graphics.drawscope.Stroke(width = d, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun p(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(w * x, w * y)
        when (zeichen) {
            AmpelZeichen.ACHTUNG -> {
                // Warndreieck mit abgerundeten Ecken
                val dreieck = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.5f, w * 0.07f); lineTo(w * 0.97f, w * 0.9f); lineTo(w * 0.03f, w * 0.9f); close() }
                drawPath(dreieck, farbe)
                drawPath(dreieck, farbe, style = androidx.compose.ui.graphics.drawscope.Stroke(width = d, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawLine(strich, p(0.5f, 0.38f), p(0.5f, 0.6f), strokeWidth = d, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawCircle(strich, radius = d * 0.62f, center = p(0.5f, 0.76f))
            }
            AmpelZeichen.GUT -> {
                drawCircle(farbe)
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.28f, w * 0.52f); lineTo(w * 0.44f, w * 0.68f); lineTo(w * 0.73f, w * 0.36f) }, strich, style = stift)
            }
            AmpelZeichen.KRITISCH -> {
                drawCircle(farbe)
                drawLine(strich, p(0.33f, 0.33f), p(0.67f, 0.67f), strokeWidth = d, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(strich, p(0.67f, 0.33f), p(0.33f, 0.67f), strokeWidth = d, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            else -> {
                drawCircle(farbe)
                drawCircle(strich, radius = d * 0.6f, center = p(0.5f, 0.3f))
                drawLine(strich, p(0.5f, 0.46f), p(0.5f, 0.72f), strokeWidth = d, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

// Schluessel der Ampelstufen; AmpelPunkt zeichnet dazu Haken, Warndreieck, Kreuz und i. Es gibt kein Textzeichen dafuer.
object AmpelZeichen {
    const val GUT = "gut"
    const val ACHTUNG = "achtung"
    const val KRITISCH = "kritisch"
    const val HINWEIS = "hinweis"
}

// Ein Ampelzeichen ist ueberall gleich gross.
val AMPEL_GROESSE = 18.dp

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
            if (subtitle.isNotBlank()) PunktText(subtitle)
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
        if (!danger) ChevronRight()
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
        if (!danger) ChevronRight()
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
        PunktText(subtitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
}

// Betrag antippen schaltet die Anzeige zwischen Euro und US-Dollar um.
fun Modifier.waehrungWechsel(state: AppState): Modifier = this
    .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = S.switchCurrency) { state.waehrungUmschalten() }

// Betrag antippen mit eigener Folge, auf der Wertpapierseite Euro, Dollar und Heimatwaehrung.
fun Modifier.waehrungWechsel(onClick: () -> Unit): Modifier = this
    .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = S.switchCurrency, onClick = onClick)

// Faengt waagerechtes Wischen ab, das ein innerer Bereich am Rand uebrig laesst, damit eine Wischseite darueber nicht
// weiterblaettert (Familien im Strategie-Labor, breites Sankey-Diagramm in den Finanzen).
internal val WischenBehalten = object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override fun onPostScroll(
        consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
    ) = androidx.compose.ui.geometry.Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) =
        androidx.compose.ui.unit.Velocity(available.x, 0f)
}

// Wie WischenBehalten fuer waagerecht rollende Zeilen (Reiter, Chips, Kacheln): Was die Zeile am Rand uebrig laesst,
// bleibt bei ihr, sonst wandert eine Wischseite dahinter mit, sobald die Zeile am Ende steht. Passt die Zeile ganz
// ins Bild, geht der Wisch wie bisher an die Seite.
class ZeileBehaelt(private val state: androidx.compose.foundation.ScrollState) : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override fun onPostScroll(
        consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
    ) = if (state.maxValue > 0) androidx.compose.ui.geometry.Offset(available.x, 0f) else androidx.compose.ui.geometry.Offset.Zero

    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) =
        if (state.maxValue > 0) androidx.compose.ui.unit.Velocity(available.x, 0f) else androidx.compose.ui.unit.Velocity.Zero
}
