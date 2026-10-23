package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.market.Symbols

// Text in voller Schriftgroesse, bricht um, wenn er nicht in eine Zeile passt. Lesbare Schrift wird nie kleiner.
@Composable
fun VollText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) = Text(text, modifier = modifier, style = style, color = color, textAlign = textAlign)

// Schrift schrumpft hoechstens bis zur Haelfte, bis die Zeile passt. Nur fuer Zahlen in Grafiken und die grosse
// Summe auf der Startseite, nie fuer Lesetext. Gezeichnet wird erst die passende Groesse, damit nichts springt.
@Composable
fun SchrumpfText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    var skala by remember(text, style) { mutableFloatStateOf(1f) }
    var fertig by remember(text, style) { mutableStateOf(false) }
    val basis = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    Text(
        text,
        modifier = modifier.drawWithContent { if (fertig) drawContent() },
        style = style.copy(fontSize = basis * skala, letterSpacing = if (style.letterSpacing.isSpecified) style.letterSpacing * skala else style.letterSpacing),
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { r ->
            if (r.hasVisualOverflow && skala > 0.5f) skala = (skala - 0.05f).coerceAtLeast(0.5f) else fertig = true
        },
    )
}

private val FONDS = Regex("\\b(UCITS|ETF|ETC|ETN)\\b|\\((Acc|Dist|Inc)\\)", RegexOption.IGNORE_CASE)
private val FONDS_ZUSATZ = Regex(
    "\\b(UCITS|ETF|ETC|ETN|Accumulating|Distributing|thesaurierend|ausschüttend|Acc|Dist|1C|1D|Shares)\\b\\.?|\\((Acc|Dist|Inc|[A-Z]{3}\\s*(Acc|Dist|Hedged)?)\\)|" +
        "\\b(USD|EUR|GBP|CHF|JPY)\\b(?=\\s*(\\(|Acc|Dist|Hedged|$))|\\bHedged\\b",
    RegexOption.IGNORE_CASE,
)

// „Sprott Junior Copper Miners UCITS ETF“ wird „Sprott Junior Copper Miners“. Firmennamen bleiben, wie sie sind.
fun kurzerFondsname(name: String): String {
    if (!FONDS.containsMatchIn(name)) return name
    val kurz = name.replace(FONDS_ZUSATZ, " ").replace(Regex("\\s*[-–]\\s*$"), "").replace(Regex("\\s+"), " ").trim().trimEnd('-', '–', ',').trim()
    return kurz.ifBlank { name }
}

private val RECHTSFORM = Regex(
    "[,\\s]+(Inc\\.?|Incorporated|Corp\\.?|Corporation|Co\\.?|Company|Ltd\\.?|Limited|plc|PLC|AG|SE|N\\.?V\\.?|S\\.?A\\.?|A/S|ASA|AB|Oyj|S\\.?p\\.?A\\.?|KGaA|GmbH|& Co\\.?|Holdings?|Group|Class [A-C]|Cl\\.? [A-C]|Reg\\.?|Registered Shares|Vz\\.?|St\\.?)\\s*$",
    RegexOption.IGNORE_CASE,
)

// Woerter, auf die ein gekuerzter Name nicht enden soll.
private val VERBINDER = setOf("of", "and", "&", "the", "for", "de", "la", "le", "du", "des", "del", "der", "die", "das", "und", "für", "von", "y", "e", "et")

// Laengste zuerst: ohne Fonds-Beiwerk, ohne Rechtsform am Ende, dann Wort fuer Wort von hinten.
// Zuletzt das erste Wort buchstabenweise.
fun namensKuerzungen(name: String): List<String> {
    val aus = LinkedHashSet<String>()
    var n = name.trim()
    aus += n
    n = kurzerFondsname(n); aus += n
    while (true) {
        val ohne = n.replace(RECHTSFORM, "").trim().trimEnd(',', '-', '–', '&').trim()
        if (ohne.isEmpty() || ohne == n) break
        n = ohne; aus += n
    }
    if (n.startsWith("The ", ignoreCase = true) && n.length > 4) { n = n.substring(4); aus += n }
    var woerter = n.split(' ').filter { it.isNotBlank() }
    while (woerter.size > 1) {
        woerter = woerter.dropLast(1)
        // „Bank of America“ wird „Bank“, nicht „Bank of“.
        if (woerter.size > 1 && woerter.last().trimEnd(',', '-', '–', '/').lowercase() in VERBINDER) continue
        aus += woerter.joinToString(" ").trimEnd(',', '-', '–', '&', '/').trim()
    }
    val erstes = woerter.firstOrNull().orEmpty()
    for (l in erstes.length - 1 downTo 3) aus += erstes.take(l)
    return aus.filter { it.isNotBlank() }
}

// Ohne Auslassungszeichen und ohne kleinere Schrift: passt der Name nicht, steht die laengste passende Kurzfassung.
// zusatz bleibt immer stehen.
@Composable
fun NamenText(
    name: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    zusatz: String = "",
) {
    val messer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val breite = constraints.maxWidth
        val text = remember(name, zusatz, breite, style) {
            val k = namensKuerzungen(name)
            if (breite == Constraints.Infinity) name + zusatz
            else (k.firstOrNull { messer.measure(it + zusatz, style, maxLines = 1, softWrap = false).size.width <= breite } ?: k.last()) + zusatz
        }
        Text(text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

// Die erste Fassung, die in voller Schriftgroesse in eine Zeile passt. Passt keine, bricht die letzte um.
@Composable
fun PassenderText(
    kandidaten: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val messer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val breite = constraints.maxWidth
        val text = remember(kandidaten, breite, style) {
            if (breite == Constraints.Infinity) kandidaten.firstOrNull().orEmpty()
            else kandidaten.firstOrNull { messer.measure(it, style, maxLines = 1, softWrap = false).size.width <= breite } ?: kandidaten.lastOrNull().orEmpty()
        }
        VollText(text, style = style, color = color)
    }
}

// Katalogname wie auf der Seite des Werts, sonst der gekuerzte Name von der Boerse.
fun kurzName(symbol: String, name: String): String = Symbols.find(symbol)?.name ?: kurzerFondsname(name.ifBlank { symbol })
