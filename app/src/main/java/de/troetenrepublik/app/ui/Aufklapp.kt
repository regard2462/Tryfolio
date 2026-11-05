package de.troetenrepublik.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.i18n.S
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Farbe der Flaeche, auf der ein Text steht (Karte, Dialog); ungesetzt gilt der Seitenhintergrund.
val LocalFlaeche = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun flaeche(): Color = LocalFlaeche.current.takeOrElse { MaterialTheme.colorScheme.background }

// Zeigt zugeklappt den ersten Satz bzw. die erste Zeile; die letzte Zeile laeuft in eine Blende mit Pfeil aus (0.68).
// Ein Tipp klappt auf und wieder zu. Kurze Texte erscheinen ganz (Rest unter MEHR_AB Zeichen). Listen (nachZeile)
// zeigen zugeklappt nur ihre erste Zeile.
@Composable
fun AufklappText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    nachZeile: Boolean = false,
    flaeche: Color = flaeche(),
) {
    val (kurz, rest) = remember(text, nachZeile) { if (nachZeile) ersteZeile(text) else ersterSatz(text) }
    var offen by rememberSaveable(text) { mutableStateOf(false) }
    if (rest.isEmpty()) {
        StichpunktText(text, modifier, style, color)
        return
    }
    Column(modifier.clickable(role = Role.Button, onClickLabel = if (offen) S.less else S.more) { offen = !offen }) {
        if (offen) StichpunktText(text, style = style, color = color)
        else BlendeText(text, zeilenVon = zugeklappt(kurz), style = style, color = color, flaeche = flaeche)
    }
}

// Zeigt den ganzen Text, aber nur so viele Zeilen, wie der sichtbare Anfang bei gleicher Breite braucht; die letzte
// Zeile laeuft in die Blende. Die Zeilenzahl wird vorab gemessen, damit nichts flackert.
@Composable
private fun BlendeText(text: String, zeilenVon: String, style: TextStyle, color: Color, flaeche: Color) {
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val einzug = remember(style, dichte) { with(dichte) { messer.measure(STICHPUNKT, style).size.width.toSp() } }
    val mitPunkten = text.contains(STICHPUNKT)
    val ganz = remember(text, einzug) { if (mitPunkten) stichpunkte(text, einzug) else AnnotatedString(text) }
    var letzteHoehe by remember { mutableStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val breite = constraints.maxWidth
        val begrenzt = constraints.hasBoundedWidth
        val zeilen = remember(zeilenVon, breite, begrenzt, style, einzug) {
            val anfang = if (mitPunkten) stichpunkte(zeilenVon, einzug) else AnnotatedString(zeilenVon)
            val vorgabe = if (begrenzt) Constraints(maxWidth = breite) else Constraints()
            messer.measure(anfang, style, constraints = vorgabe).lineCount.coerceAtLeast(1)
        }
        Text(
            ganz, style = style, color = color, maxLines = zeilen, overflow = TextOverflow.Clip,
            onTextLayout = { l ->
                letzteHoehe = l.getLineBottom(l.lineCount - 1) - l.getLineTop(l.lineCount - 1)
            },
        )
        Blende(
            flaeche, Modifier.align(Alignment.BottomEnd), klein = true,
            hoehe = if (letzteHoehe > 0f) with(dichte) { letzteHoehe.toDp() } else 18.dp,
        )
    }
}

// Waagerechter Verlauf in die Flaechenfarbe mit kleinem Pfeil nach unten; grosse Form fuer Beschreibungen, kleine
// fuer kurze Hinweistexte.
@Composable
fun Blende(flaeche: Color, modifier: Modifier = Modifier, klein: Boolean = true, hoehe: Dp = if (klein) 18.dp else 24.dp) {
    Row(
        modifier.background(Brush.horizontalGradient(listOf(flaeche.copy(alpha = 0f), flaeche, flaeche)))
            .padding(start = if (klein) 28.dp else 36.dp).heightIn(min = hoehe),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChevronDown(width = if (klein) 8.dp else 11.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Text mit Zeilengrenze: ist er gekuerzt, laeuft die letzte Zeile in die Blende aus (0.68), ohne Auslassungszeichen.
// Das Verhalten beim Tippen bleibt beim Aufrufer. Mass und Grundlinie sind die des Texts, damit der
// Aufbau wie bei einem einfachen Text bleibt (Gewicht, feste Breite, Ausrichtung).
@Composable
fun AuslaufText(
    text: String,
    maxLines: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = Color.Unspecified,
    flaeche: Color = flaeche(),
    textAlign: TextAlign = TextAlign.Unspecified,
    fontWeight: FontWeight? = null,
) {
    var gekuerzt by remember(text, maxLines) { mutableStateOf(false) }
    Layout(
        content = {
            Text(
                text, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Clip, textAlign = textAlign,
                fontWeight = fontWeight, onTextLayout = { gekuerzt = it.hasVisualOverflow },
            )
            if (gekuerzt) Blende(flaeche)
        },
        modifier = modifier,
        measurePolicy = AuslaufMass,
    )
}

// Mass des AuslaufText: Groesse und Grundlinien des Texts, die Blende sitzt unten rechts darauf. Die Eigenmasse
// kommen vom Text, damit Zeilen mit IntrinsicSize weiter rechnen koennen.
private object AuslaufMass : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val t = measurables[0].measure(constraints)
        val b = measurables.getOrNull(1)?.measure(Constraints(maxWidth = t.width, maxHeight = t.height))
        return layout(t.width, t.height, alignmentLines = mapOf(FirstBaseline to t[FirstBaseline], LastBaseline to t[LastBaseline])) {
            t.placeRelative(0, 0)
            b?.placeRelative(t.width - b.width, t.height - b.height)
        }
    }
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = measurables[0].minIntrinsicWidth(height)
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = measurables[0].maxIntrinsicWidth(height)
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = measurables[0].minIntrinsicHeight(width)
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = measurables[0].maxIntrinsicHeight(width)
}

// Ganzer Text, dessen Ende gekuerzt ist (etwa ein Auszug oder Anriss): die letzte Zeile laeuft in die Blende aus.
@Composable
fun MitBlende(gekuerzt: Boolean, modifier: Modifier = Modifier, flaeche: Color = flaeche(), klein: Boolean = true, inhalt: @Composable () -> Unit) {
    Box(modifier) {
        inhalt()
        if (gekuerzt) Blende(flaeche, Modifier.align(Alignment.BottomEnd), klein = klein)
    }
}

// Ab so vielen Zeichen im verborgenen Teil lohnt das Aufklappen; darunter erscheint der Text ganz (etwa zwei Zeilen).
const val MEHR_AB = 160

// Sichtbarer Anfang eines zugeklappten Textes, ohne Zeichen am Ende; dass es weitergeht, zeigt die Blende.
fun zugeklappt(kurz: String): String = kurz.trimEnd()

// Pfeil zum Aufklappen (5.97): zeigt zugeklappt nach rechts und dreht sich beim Aufklappen nach unten.
@Composable
fun AufklappPfeil(offen: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val dreh by animateFloatAsState(if (offen) 90f else 0f)
    Box(modifier.size(16.dp), contentAlignment = Alignment.Center) {
        ChevronRight(height = 13.dp, color = color, modifier = Modifier.rotate(dreh))
    }
}

// Pfeil unter einer gekuerzten Liste (5.97): ein Tipp zeigt die weiteren Eintraege, ein weiterer klappt sie wieder zu.
@Composable
fun ListenPfeil(offen: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (offen) S.less else S.more, onClick = onClick)
            .semantics { contentDescription = if (offen) S.less else S.more }.heightIn(min = 48.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { AufklappPfeil(offen) }
}

// Erste Zeile und Rest, etwa fuer Aufzaehlungen mit „•“. Einzeilige Texte und kurze Reste bleiben ganz.
fun ersteZeile(text: String): Pair<String, String> {
    val schnitt = text.indexOf('\n')
    if (schnitt < 0) return text to ""
    if (text.length - schnitt - 1 < MEHR_AB) return text to ""
    return text.substring(0, schnitt) to text.substring(schnitt + 1)
}

private val SATZENDE = Regex("(?<=[a-zäöüß0-9%)“”\"]{2})[.!?](?=\\s+[A-ZÄÖÜ„\"(0-9])")

// Einbuchstabige Abkuerzungen wie „z. B.“ trennen nicht. Zu kurze Texte bleiben ganz.
fun ersterSatz(text: String): Pair<String, String> {
    // Ein sehr kurzer erster Satz nimmt den naechsten mit.
    val m = SATZENDE.findAll(text).firstOrNull { it.range.first + 1 >= 40 } ?: return text to ""
    val schnitt = m.range.first + 1
    if (text.length - schnitt < MEHR_AB) return text to ""
    return text.substring(0, schnitt) to text.substring(schnitt).trim()
}

// Zeichen am Anfang einer Stichpunktzeile.
const val STICHPUNKT = "• "

// Text mit Stichpunkten: jede Zeile, die mit „• “ beginnt, ist ein eigener Absatz mit haengendem Einzug, damit eine
// Folgezeile unter dem Text steht und nicht unter dem Punkt. Andere Zeilen bleiben, wie sie sind.
@Composable
fun StichpunktText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (!text.contains(STICHPUNKT)) {
        Text(text, modifier = modifier, style = style, color = color)
        return
    }
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val einzug = remember(style, dichte) { with(dichte) { messer.measure(STICHPUNKT, style).size.width.toSp() } }
    Text(remember(text, einzug) { stichpunkte(text, einzug) }, modifier = modifier, style = style, color = color)
}

// Je Zeile ein Absatz, Stichpunktzeilen mit Einzug ab der zweiten Zeile. Leere Zeilen bleiben als Abstand stehen.
fun stichpunkte(text: String, einzug: TextUnit): AnnotatedString = buildAnnotatedString {
    val punkt = ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = einzug))
    val normal = ParagraphStyle()
    text.split('\n').forEach { zeile ->
        withStyle(if (zeile.startsWith(STICHPUNKT)) punkt else normal) { append(zeile.ifEmpty { " " }) }
    }
}
