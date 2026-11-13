package de.troetenrepublik.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.SpanStyle
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
import de.troetenrepublik.app.engine.Erklaertext
import de.troetenrepublik.app.i18n.S
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// Farbe der Flaeche, auf der ein Text steht (Karte, Dialog); ungesetzt gilt der Seitenhintergrund.
val LocalFlaeche = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun flaeche(): Color = LocalFlaeche.current.takeOrElse { MaterialTheme.colorScheme.background }

// Zeigt zugeklappt den ersten Satz bzw. die erste Zeile; die letzte Zeile laeuft in eine Blende mit Pfeil aus (0.68).
// Ein Tipp klappt auf und wieder zu. Aufgeklappt wird nur wirklich viel Text (0.77): Fliesstext mit hoechstens
// GANZ_BIS_ZEILEN Zeilen steht sofort ganz da, ohne Blende und ohne Tippflaeche. Listen (nachZeile) stehen ganz da, wenn
// sie hoechstens eine Zeile mehr brauchen als der sichtbare Anfang (UEBERHANG_MAX).
// Die Zeilen werden vor dem Zeichnen gemessen, damit nichts von gekuerzt auf ganz springt. Listen (nachZeile) zeigen
// zugeklappt nur ihre erste Zeile.
@Composable
fun AufklappText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    nachZeile: Boolean = false,
    flaeche: Color = flaeche(),
) {
    // Hinweisblock (erste Zeile, darunter Stichpunkte): zugeklappt nur die erste Zeile, nie der erste Satz mit Stichpunkten
    val (kurz, rest) = remember(text, nachZeile) { if (nachZeile || istHinweisblock(text)) ersteZeile(text) else ersterSatz(text) }
    var offen by rememberSaveable(text) { mutableStateOf(false) }
    if (rest.isEmpty()) {
        StichpunktText(text, modifier, style, color)
        return
    }
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val einzug = remember(style, dichte) { with(dichte) { messer.measure(STICHPUNKT, style).size.width.toSp() } }
    val mitPunkten = Erklaertext.ausgezeichnet(text)
    val luecke = erklaerLuecke(style)
    val kopf = MaterialTheme.colorScheme.onSurfaceVariant
    val ganz = remember(text, einzug, luecke, kopf) { if (mitPunkten) stichpunkte(text, einzug, luecke, kopf) else AnnotatedString(text) }
    // Abstaende zwischen Absaetzen zaehlen nicht als Textzeilen
    val luecken = remember(text) { if (mitPunkten) Erklaertext.zaehlen(text).luecken else 0 }
    BoxWithConstraints(modifier) {
        val breite = constraints.maxWidth
        val begrenzt = constraints.hasBoundedWidth
        val anfang = zugeklappt(kurz)
        // Zeilen des sichtbaren Anfangs und Zeilen, die der ganze Text mehr braucht
        val mass = remember(text, anfang, breite, begrenzt, style, einzug) {
            val vorgabe = if (begrenzt) Constraints(maxWidth = breite) else Constraints()
            val a = if (mitPunkten) stichpunkte(anfang, einzug, luecke, kopf) else AnnotatedString(anfang)
            val zk = messer.measure(a, style, constraints = vorgabe).lineCount.coerceAtLeast(1)
            val zg = messer.measure(ganz, style, constraints = vorgabe, maxLines = maxOf(zk + UEBERHANG_MAX, GANZ_BIS_ZEILEN) + luecken + 1).lineCount
            Triple(zk, zg - zk, zg)
        }
        if (mass.second <= UEBERHANG_MAX || (!nachZeile && mass.third - luecken <= GANZ_BIS_ZEILEN)) {
            StichpunktText(text, Modifier, style, color)
        } else {
            Column(Modifier.clickable(role = Role.Button, onClickLabel = if (offen) S.less else S.more) { offen = !offen }) {
                if (offen) StichpunktText(text, style = style, color = color)
                else BlendeText(ganz, zeilen = mass.first, style = style, color = color, flaeche = flaeche)
            }
        }
    }
}

// So viele verborgene Zeilen darf ein Text haben, damit er noch ganz erscheint; ab einer mehr gibt es Blende und Aufklappen.
const val UEBERHANG_MAX = 1

// Bis zu so vielen Zeilen steht ein Fliesstext ganz da; erst darueber wird er gekuerzt und aufklappbar (0.77, 0.79: 10).
const val GANZ_BIS_ZEILEN = 10

// Zeigt den ganzen Text, aber nur so viele Zeilen, wie der sichtbare Anfang bei gleicher Breite braucht; die letzte
// Zeile laeuft in die Blende.
@Composable
private fun BlendeText(ganz: AnnotatedString, zeilen: Int, style: TextStyle, color: Color, flaeche: Color) {
    val dichte = LocalDensity.current
    var letzteHoehe by remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxWidth()) {
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
// Aufbau wie bei einem einfachen Text bleibt (Gewicht, feste Breite, Ausrichtung). Ab zwei Zeilen Grenze steht auch
// ein Text ganz da, der nur eine Zeile ueber der Grenze liegt (UEBERHANG_MAX); gemessen wird im Layout, vor dem Zeichnen.
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
    if (maxLines < 2) {
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
        return
    }
    val messer = rememberTextMeasurer()
    val mass = remember(messer, text, maxLines, style, textAlign, fontWeight) {
        // Aufrufer ohne Grenze geben Int.MAX_VALUE; die Rechnung "Grenze plus Ueberhang" darf nicht ueberlaufen
        AuslaufMessMass(messer, text, maxLines.coerceAtMost(100_000), style.copy(textAlign = textAlign, fontWeight = fontWeight ?: style.fontWeight))
    }
    Layout(
        content = {
            Text(text, style = style, color = color, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip, textAlign = textAlign, fontWeight = fontWeight)
            Blende(flaeche)
        },
        modifier = modifier,
        measurePolicy = mass,
    )
}

// Mass des AuslaufText ab zwei Zeilen: Die Zeilen bestimmt der TextMeasurer. Hat der Text hoechstens UEBERHANG_MAX
// Zeilen mehr als erlaubt, steht er ganz; sonst ist er auf die erlaubten Zeilen begrenzt und die Blende sitzt unten
// rechts darauf.
private class AuslaufMessMass(
    private val messer: androidx.compose.ui.text.TextMeasurer,
    private val text: String,
    private val maxLines: Int,
    private val stil: TextStyle,
) : MeasurePolicy {
    private class Plan(val hoehe: Int, val ersteBasis: Int, val letzteBasis: Int, val blende: Boolean)

    private fun plan(d: androidx.compose.ui.unit.Density, richtung: androidx.compose.ui.unit.LayoutDirection, maxBreite: Int): Plan {
        val vorgabe = if (maxBreite == Constraints.Infinity) Constraints() else Constraints(maxWidth = maxBreite)
        val r = messer.measure(
            text, stil, overflow = TextOverflow.Clip, maxLines = maxLines + UEBERHANG_MAX + 1,
            constraints = vorgabe, layoutDirection = richtung, density = d,
        )
        val ganz = r.lineCount <= maxLines + UEBERHANG_MAX
        val z = if (ganz) r.lineCount else maxLines
        return Plan(
            kotlin.math.ceil(r.getLineBottom(z - 1)).toInt(), kotlin.math.ceil(r.getLineBaseline(0)).toInt(),
            kotlin.math.ceil(r.getLineBaseline(z - 1)).toInt(), !ganz,
        )
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val p = plan(this, layoutDirection, constraints.maxWidth)
        val hoehe = p.hoehe.coerceAtMost(constraints.maxHeight)
        val t = measurables[0].measure(Constraints(constraints.minWidth, constraints.maxWidth, 0, hoehe))
        val b = if (p.blende) measurables.getOrNull(1)?.measure(Constraints(maxWidth = t.width, maxHeight = t.height)) else null
        return layout(
            t.width, t.height.coerceAtLeast(constraints.minHeight).coerceAtMost(constraints.maxHeight),
            alignmentLines = mapOf(FirstBaseline to p.ersteBasis, LastBaseline to p.letzteBasis),
        ) {
            t.placeRelative(0, 0)
            b?.placeRelative(t.width - b.width, t.height - b.height)
        }
    }
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = measurables[0].minIntrinsicWidth(height)
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = measurables[0].maxIntrinsicWidth(height)
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = plan(this, layoutDirection, width).hoehe
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = plan(this, layoutDirection, width).hoehe
}

// Mass des AuslaufText bis eine Zeile: Groesse und Grundlinien des Texts, die Blende sitzt unten rechts darauf. Die Eigenmasse
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
fun AufklappPfeil(offen: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, hoehe: Dp = PfeilGroesse.ZEILE) {
    val dreh by animateFloatAsState(if (offen) 90f else 0f)
    Box(modifier.size(16.dp), contentAlignment = Alignment.Center) {
        ChevronRight(height = hoehe, color = color, modifier = Modifier.rotate(dreh))
    }
}

// Pfeil unter einer gekuerzten Liste (5.97): ein Tipp zeigt die weiteren Eintraege, ein weiterer klappt sie wieder zu.
@Composable
fun ListenPfeil(offen: Boolean, modifier: Modifier = Modifier, label: String? = null, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (offen) S.less else S.more, onClick = onClick)
            .semantics { contentDescription = if (offen) S.less else S.more }.heightIn(min = 48.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mit Beschriftung steht der Pfeil nicht allein da, sondern hinter einem Wort
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
        }
        AufklappPfeil(offen)
    }
}

// Hinweisblock: eine Einleitungszeile, direkt darunter Stichpunkte mit „• “.
fun istHinweisblock(text: String): Boolean {
    val z = text.split('\n')
    return z.size >= 2 && !z[0].startsWith(STICHPUNKT) && z[1].startsWith(STICHPUNKT)
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

// Erklaertext mit leichter Auszeichnung (Erklaertext): Absaetze mit Abstand, Stichpunkte mit haengendem Einzug, damit
// eine Folgezeile unter dem Text steht und nicht unter dem Punkt, hervorgehobene Begriffe, Zwischenueberschriften und
// Stufenskalen. Text ohne Auszeichnung bleibt, wie er ist.
@Composable
fun StichpunktText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (!Erklaertext.ausgezeichnet(text)) {
        Text(text, modifier = modifier, style = style, color = color)
        return
    }
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val einzug = remember(style, dichte) { with(dichte) { messer.measure(STICHPUNKT, style).size.width.toSp() } }
    val luecke = erklaerLuecke(style)
    val kopf = MaterialTheme.colorScheme.onSurfaceVariant
    val teile = remember(text) { erklaerTeile(Erklaertext.zerlegen(text)) }
    if (teile.size == 1 && teile[0].skala == null) {
        Text(remember(text, einzug, luecke, kopf) { stichpunkte(teile[0].bloecke, einzug, luecke, kopf) }, modifier = modifier, style = style, color = color)
        return
    }
    Column(modifier) {
        for (teil in teile) {
            if (teil.skala != null) ErklaerSkala(teil.skala)
            else Text(remember(teil, einzug, luecke, kopf) { stichpunkte(teil.bloecke, einzug, luecke, kopf) }, style = style, color = color)
        }
    }
}

// Abstand zwischen zwei Absaetzen: eine halbe Zeile.
fun erklaerLuecke(style: TextStyle): TextUnit = when {
    style.lineHeight.isSp -> style.lineHeight * 0.5f
    style.fontSize.isSp -> style.fontSize * 0.7f
    else -> TextUnit.Unspecified
}

// Ein Stueck Erklaertext: entweder Textbloecke oder eine Skala.
class ErklaerTeil(val bloecke: List<Erklaertext.Block>, val skala: Erklaertext.Skala?)

// Teilt die Bloecke an den Skalen; Abstaende direkt vor und nach einer Skala entfallen, die Skala bringt ihren eigenen mit.
fun erklaerTeile(bloecke: List<Erklaertext.Block>): List<ErklaerTeil> {
    val aus = ArrayList<ErklaerTeil>()
    var lauf = ArrayList<Erklaertext.Block>()
    fun schliessen() {
        while (lauf.isNotEmpty() && lauf.last() is Erklaertext.Luecke) lauf.removeAt(lauf.size - 1)
        while (lauf.isNotEmpty() && lauf.first() is Erklaertext.Luecke) lauf.removeAt(0)
        if (lauf.isNotEmpty()) aus.add(ErklaerTeil(lauf, null))
        lauf = ArrayList()
    }
    for (b in bloecke) {
        if (b is Erklaertext.Skala) { schliessen(); aus.add(ErklaerTeil(emptyList(), b)) } else lauf.add(b)
    }
    schliessen()
    return aus
}

// Je Block ein Absatz: Stichpunkte mit Einzug ab der zweiten Zeile und hervorgehobenem Begriff, Zwischenueberschriften
// gedaempft, Leerzeilen als halbhoher Abstand. Eine Skala steht hier nur als Zeile ihrer Stufen (zum Messen).
fun stichpunkte(text: String, einzug: TextUnit, luecke: TextUnit = TextUnit.Unspecified, kopf: Color = Color.Unspecified): AnnotatedString =
    stichpunkte(Erklaertext.zerlegen(text), einzug, luecke, kopf)

fun stichpunkte(bloecke: List<Erklaertext.Block>, einzug: TextUnit, luecke: TextUnit = TextUnit.Unspecified, kopf: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    val punkt = ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = einzug))
    val normal = ParagraphStyle()
    for (b in bloecke) when (b) {
        is Erklaertext.Punkt -> withStyle(punkt) {
            append(STICHPUNKT)
            if (b.begriff != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(b.begriff); append(":") }
                append(" ")
            }
            append(b.text)
        }
        is Erklaertext.Kopf -> withStyle(normal) { withStyle(SpanStyle(color = kopf, fontWeight = FontWeight.Medium)) { append(b.text) } }
        is Erklaertext.Absatz -> withStyle(normal) { append(b.text) }
        is Erklaertext.Skala -> withStyle(normal) { append(b.stufen.joinToString(" · ")) }
        Erklaertext.Luecke ->
            if (luecke.isSp) withStyle(ParagraphStyle(lineHeight = luecke)) { withStyle(SpanStyle(fontSize = luecke * 0.6f)) { append(" ") } }
            else withStyle(normal) { append(" ") }
    }
}
