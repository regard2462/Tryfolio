package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.CompanyInfo
import de.troetenrepublik.app.market.Gleichwerte
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.LogoBild
import de.troetenrepublik.app.plattform.Plattform

// Quellen fuer Firmenzeichen, alle oeffentlich und ohne Schluessel: Logos der Kursdatenanbieter (FMP, EOD),
// das Wikidata-Logo, zuletzt das Seitensymbol der Firmenwebsite. Die Zeichen werden nur geladen, nicht mitgeliefert.
object Logos {

    // Symbol zur Quelle, die tatsaechlich geladen hat, damit Listen nicht flackern.
    private val resolved = java.util.concurrent.ConcurrentHashMap<String, String>()

    // familie: andere Notierungen derselben Aktie (Gleichwerte). Deren schon geladene Logos kommen gleich nach dem
    // eigenen, ihre uebrigen Quellen zuletzt.
    fun urlsFor(symbol: String, info: CompanyInfo?, familie: Set<String> = Gleichwerte.familien.value[symbol].orEmpty()): List<String> {
        val out = mutableListOf<String>()
        resolved[symbol]?.let { out += it }
        val andere = familie.filter { it != symbol }
        andere.forEach { m -> resolved[m]?.let { out += it } }
        quellen(symbol, info, out)
        andere.forEach { m -> quellen(m, firmen.value[m], out) }
        return out.distinct()
    }

    private fun quellen(symbol: String, info: CompanyInfo?, out: MutableList<String>) {
        val type = Symbols.typeOf(symbol)
        when (type) {
            AssetType.CRYPTO -> {
                val code = symbol.substringBefore('-').uppercase()
                out += "https://images.financialmodelingprep.com/symbol/${code}USD.png"
                out += "https://assets.coincap.io/assets/icons/${code.lowercase()}@2x.png"
            }
            AssetType.INDEX, AssetType.COMMODITY, AssetType.CURRENCY -> {}
            else -> {
                out += "https://images.financialmodelingprep.com/symbol/${java.net.URLEncoder.encode(symbol, "UTF-8")}.png"
                eodPath(symbol)?.let { out += "https://eodhd.com/img/logos/$it.png" }
                info?.logoUrl?.takeIf { it.isNotBlank() }?.let { out += it }
            }
        }
    }

    // Pfad beim EOD-Logodienst: Boersenkuerzel/Ticker.
    private fun eodPath(symbol: String): String? {
        val base = symbol.substringBeforeLast('.')
        return when {
            !symbol.contains('.') -> "US/$symbol"
            symbol.endsWith(".DE") -> "XETRA/$base"
            symbol.endsWith(".F") -> "F/$base"
            symbol.endsWith(".L") -> "LSE/$base"
            symbol.endsWith(".PA") -> "PA/$base"
            symbol.endsWith(".AS") -> "AS/$base"
            symbol.endsWith(".SW") -> "SW/$base"
            symbol.endsWith(".MI") -> "MI/$base"
            symbol.endsWith(".MC") -> "MC/$base"
            symbol.endsWith(".CO") -> "CO/$base"
            symbol.endsWith(".ST") -> "ST/$base"
            symbol.endsWith(".TO") -> "TO/$base"
            symbol.endsWith(".HK") -> "HK/$base"
            symbol.endsWith(".BR") -> "BR/$base"
            else -> null
        }
    }

    fun remember(symbol: String, url: String) { resolved[symbol] = url }

    // Gespeicherte Firmendaten (Website, Wikidata-Logo) fuer alle Logos, auch in Listen.
    @Volatile var firmen: kotlinx.coroutines.flow.StateFlow<Map<String, CompanyInfo>> = kotlinx.coroutines.flow.MutableStateFlow(emptyMap())

    // Setzt der App-Zustand, Parameter sind Symbol und Name.
    @Volatile var familieSuchen: ((String, String) -> Unit)? = null
    private val gefragt = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Einmal je Sitzung andere Notierungen derselben Aktie suchen lassen.
    fun ohneLogo(symbol: String, name: String) {
        if (Symbols.typeOf(symbol) != AssetType.STOCK || name.isBlank() || name == symbol) return
        if (gefragt.size < 200 && gefragt.add(symbol)) familieSuchen?.invoke(symbol, name)
    }

    // Gemerkte Familien unter ~ plus erstes Kuerzel, Wert sind die Kuerzel mit Komma. Sie liegen bei den
    // Logo-Darstellungen, weil sie nur den Logos dienen.
    fun familienLaden() {
        runCatching {
            Plattform.logoLooksLaden().forEach { (k, v) ->
                if (k.startsWith("~") && !k.startsWith("~!")) Gleichwerte.verbinden(v.split(',').filter { it.isNotBlank() }.toSet(), merken = false)
            }
        }
        Gleichwerte.ablage = { g -> runCatching { Plattform.logoLookSpeichern("~" + g.sorted().first(), g.sorted().joinToString(",")) } }
    }

    // Tag der letzten erfolglosen Suche, damit nicht jeder Start erneut sucht.
    fun gesucht(symbol: String): Long? = runCatching { Plattform.logoLooksLaden()["~!$symbol"]?.toLongOrNull() }.getOrNull()

    fun gesuchtMerken(symbol: String, tag: Long) { runCatching { Plattform.logoLookSpeichern("~!$symbol", tag.toString()) } }
}

// Logos mit eigener Hintergrundflaeche werden so weit vergroessert, dass sie den Kreis ausfuellen, der Kreis bekommt
// ihre Randfarbe. Freistehende Zeichen sitzen auf einer abgesetzten Flaeche. So entsteht kein Quadrat im Kreis.
data class LogoLook(val fill: Boolean, val background: Color)

// Geladenes Logo der Plattform (Android: Bitmap aus Coil, Desktop: Skia-Bild): Groesse und eine auf n mal n
// verkleinerte Kopie als ARGB-Werte, Zeile fuer Zeile.
interface LogoPixel {
    val breite: Int
    val hoehe: Int
    fun verkleinert(n: Int): IntArray?
}

object LogoLooks {
    val looks = mutableStateMapOf<String, LogoLook>()
    @Volatile private var geladen = false

    // Gemerkte Darstellungen laden, damit Logos schon beim ersten Bild richtig sitzen.
    fun ensure() {
        if (geladen) return
        geladen = true
        Plattform.logoLooksLaden().forEach { (symbol, s) ->
            if (symbol.startsWith("~")) return@forEach
            val parts = s.split('|')
            if (parts.size == 2) parts[1].toLongOrNull(16)?.let { looks[symbol] = LogoLook(parts[0] == "F", Color(it.toInt())) }
        }
    }

    fun store(symbol: String, look: LogoLook) {
        if (looks[symbol] == look) return
        looks[symbol] = look
        Plattform.logoLookSpeichern(symbol, (if (look.fill) "F" else "M") + "|" + Integer.toHexString(look.background.toArgbInt()))
    }

    private fun Color.toArgbInt(): Int =
        ((alpha * 255).toInt() shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

    // Rand deckend und einheitlich heisst eigene Flaeche, sonst freistehendes Zeichen.
    fun analyze(bild: LogoPixel?): LogoLook? {
        if (bild == null || bild.breite < 8 || bild.hoehe < 8) return null
        val n = 48
        val small = bild.verkleinert(n) ?: return null
        val inset = 3
        val points = mutableListOf<Pair<Int, Int>>()
        for (f in listOf(0.25, 0.5, 0.75)) {
            val k = (f * (n - 1)).toInt()
            points += k to inset; points += k to (n - 1 - inset); points += inset to k; points += (n - 1 - inset) to k
        }
        val opaque = points.map { (x, y) -> small[y * n + x] }.filter { ((it ushr 24) and 0xFF) > 200 }
        if (opaque.size >= 9) {
            val r = opaque.sumOf { (it shr 16) and 0xFF } / opaque.size
            val g = opaque.sumOf { (it shr 8) and 0xFF } / opaque.size
            val b = opaque.sumOf { it and 0xFF } / opaque.size
            return LogoLook(true, Color(r, g, b))
        }
        // Freistehendes Zeichen: helle Flaeche fuer dunkle oder bunte Zeichen, dunkle fuer helle
        var lum = 0.0; var count = 0
        for (y in 0 until n) for (x in 0 until n) {
            val p = small[y * n + x]
            if (((p ushr 24) and 0xFF) < 128) continue
            lum += (0.2126 * ((p shr 16) and 0xFF) + 0.7152 * ((p shr 8) and 0xFF) + 0.0722 * (p and 0xFF)) / 255.0
            count++
        }
        if (count == 0) return null
        return LogoLook(false, if (lum / count < 0.62) Color.White else Color(0xFF1C1C1E))
    }
}

// Markenfarbe je Symbol aus dem geladenen Logo, fuer die Akzente im Derivate-Blatt.
object LogoColors {
    val colors = mutableStateMapOf<String, Color>()

    // Haeufigste kraeftige Farbe: Pixel mit wenig Saettigung oder Deckkraft auslassen, Farben grob quantisieren
    // und die groesste Gruppe mitteln.
    fun dominant(bild: LogoPixel?): Color? {
        if (bild == null || bild.breite == 0 || bild.hoehe == 0) return null
        val n = 32
        val small = bild.verkleinert(n) ?: return null
        val bins = HashMap<Int, IntArray>()   // key zu count, rSum, gSum, bSum
        for (y in 0 until n) for (x in 0 until n) {
            val p = small[y * n + x]
            val a = (p ushr 24) and 0xFF
            if (a < 128) continue
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val max = maxOf(r, g, b); val min = minOf(r, g, b)
            val sat = if (max == 0) 0f else (max - min) / max.toFloat()
            if (sat < 0.25f || max < 40 || (max > 240 && min > 200)) continue
            val key = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
            val e = bins.getOrPut(key) { IntArray(4) }
            e[0]++; e[1] += r; e[2] += g; e[3] += b
        }
        val best = bins.values.maxByOrNull { it[0] } ?: return null
        if (best[0] < 6) return null
        return Color(best[1] / best[0], best[2] / best[0], best[3] / best[0])
    }
}

// Laedt die Quellen der Reihe nach und zeigt zuletzt das Buchstabenfeld. Nebenbei wird die Markenfarbe bestimmt.
@Composable
fun InstrumentLogo(
    symbol: String,
    name: String,
    size: Int = 36,
    info: CompanyInfo? = null,
) {
    val alle by Logos.firmen.collectAsStateWithLifecycle()
    val familien by Gleichwerte.familien.collectAsStateWithLifecycle()
    val firma = info ?: alle[symbol]
    val familie = familien[symbol].orEmpty()
    val urls = remember(symbol, firma?.logoUrl, firma?.website, familie) { Logos.urlsFor(symbol, firma, familie) }
    var index by remember(symbol, urls) { mutableStateOf(0) }
    var failed by remember(symbol, urls) { mutableStateOf(false) }

    if (urls.isEmpty() || failed || index > urls.lastIndex) {
        // Vielleicht hat eine andere Notierung derselben Aktie ein Logo
        androidx.compose.runtime.LaunchedEffect(symbol) { Logos.ohneLogo(symbol, name) }
        InstrumentAvatar(symbol, name, size)
        return
    }

    LogoLooks.ensure()
    val look = LogoLooks.looks[symbol]
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(look?.background ?: MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val url = urls[index]
        LogoBild(
            url = url,
            contentDescription = name,
            // Leicht vergroessert, damit keine Ecken bleiben. Freistehende Zeichen sitzen mit Rand in der Mitte.
            modifier = if (look?.fill == true) Modifier.fillMaxSize().graphicsLayer { scaleX = 1.14f; scaleY = 1.14f }
                else Modifier.size((size * 0.68).dp),
            contentScale = if (look?.fill == true) ContentScale.Crop else ContentScale.Fit,
            onSuccess = { bild ->
                Logos.remember(symbol, url)
                LogoLooks.analyze(bild)?.let { LogoLooks.store(symbol, it) }
                if (!LogoColors.colors.containsKey(symbol)) {
                    LogoColors.dominant(bild)?.let { LogoColors.colors[symbol] = it }
                }
            },
            onError = {
                if (index < urls.lastIndex) index += 1 else failed = true
            },
        )
    }
}

// Logo mit den Stammdaten aus dem Zustand (ISIN, Wikidata-Logo).
@Composable
fun InstrumentLogo(state: AppState, symbol: String, name: String, size: Int = 36) {
    val companies by state.companies.collectAsStateWithLifecycle()
    InstrumentLogo(symbol, name, size, companies[symbol])
}
