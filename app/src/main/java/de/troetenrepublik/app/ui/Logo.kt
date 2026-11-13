package de.troetenrepublik.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import de.troetenrepublik.app.engine.LogoFreistellen
import de.troetenrepublik.app.plattform.logoBildErzeugen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.CompanyInfo
import de.troetenrepublik.app.engine.LogoDarstellung
import de.troetenrepublik.app.market.Gleichwerte
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.LogoBild
import de.troetenrepublik.app.plattform.Plattform

// Quellen fuer Firmenzeichen, alle oeffentlich und ohne Schluessel: Logos von FMP, bei Krypto von CoinCap, und das
// Wikidata-Logo. Die Zeichen werden nur geladen, nicht mitgeliefert.
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
                info?.logoUrl?.takeIf { it.isNotBlank() }?.let { out += it }
            }
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

// Logos mit eigener Hintergrundflaeche werden so weit vergroessert, dass sie die abgerundete Flaeche ausfuellen, die
// Flaeche bekommt ihre Randfarbe. Bei freistehenden Zeichen merkt background, ob das Zeichen dunkel (Weiss) oder hell ist.
data class LogoLook(val fill: Boolean, val background: Color)

// Darstellung der Logos nach den Einstellungen (AppState setzt die Werte): farbig oder schwarz-weiss, rund oder eckig,
// Trennlinien zwischen Listenzeilen an oder aus.
object LogoAnzeige {
    var farbig by mutableStateOf(true)
    var rund by mutableStateOf(true)
    var trennlinien by mutableStateOf(true)
    // Falsch: Logos mit eigener einfarbiger Flaeche bleiben, wie sie sind (Darstellung wie in 0.78).
    var hintergrundEntfernen by mutableStateOf(false)
}

// Kennfarben freistehender Zeichen: Weiss = dunkles Zeichen, ZEICHEN_HELL = fast weisses Zeichen, ZEICHEN_BUNT = farbiges Zeichen.
internal val ZEICHEN_HELL = Color(0xFF1C1C1E)
internal val ZEICHEN_BUNT = Color(0xFF808081)

// Geladenes Logo der Plattform (Android: Bitmap aus Coil, Desktop: Skia-Bild): Groesse und eine auf n mal n
// verkleinerte Kopie als ARGB-Werte, Zeile fuer Zeile.
interface LogoPixel {
    val breite: Int
    val hoehe: Int
    fun verkleinert(n: Int): IntArray?
    // Pixel in Originalgroesse oder auf hoechstens maxKante je Seite verkleinert (Seitenverhaeltnis bleibt).
    fun feld(maxKante: Int): LogoFeld?
}

class LogoFeld(val breite: Int, val hoehe: Int, val px: IntArray)

// Freigestelltes Logo mit der Darstellung des freistehenden Zeichens.
class FreiLogo(val bild: ImageBitmap, val look: LogoLook)

// Ergebnisse des Freistellens je Bildadresse, hoechstens 200, das am laengsten ungenutzte faellt zuerst heraus.
// Zugriff nur im Hauptthread. Ein leeres Ergebnis heisst: nicht freistellbar.
object LogoFrei {
    private class Geprueft(val frei: FreiLogo?)
    private val speicher = LinkedHashMap<String, Geprueft>()

    fun gemerkt(url: String): FreiLogo? = speicher.remove(url)?.also { speicher[url] = it }?.frei

    suspend fun holen(url: String, bild: LogoPixel): FreiLogo? {
        speicher.remove(url)?.let { speicher[url] = it; return it.frei }
        val erg = withContext(Dispatchers.Default) { rechnen(bild) }
        speicher.remove(url)
        speicher[url] = Geprueft(erg)
        while (speicher.size > 200) speicher.remove(speicher.keys.first())
        return erg
    }

    private fun rechnen(bild: LogoPixel): FreiLogo? {
        val feld = bild.feld(128) ?: return null
        if (LogoFreistellen.randfarbe(feld.px, feld.breite, feld.hoehe) == null) return null
        val frei = LogoFreistellen.freistellen(feld.px, feld.breite, feld.hoehe)
        if (frei === feld.px) return null
        // Leeren Rand abschneiden, damit das Zeichen so gross wie andere Logos erscheint
        val (zu, zb, zh) = LogoFreistellen.zuschneiden(frei, feld.breite, feld.hoehe)
        val klein = LogoFreistellen.verkleinern(zu, zb, zh, 48).first
        val look = LogoLooks.freistehend(klein) ?: return null
        val img = logoBildErzeugen(LogoFeld(zb, zh, zu)) ?: return null
        return FreiLogo(img, look)
    }
}

// Schwarz-weisse Fassung von Logos mit eigener Flaeche je Bildadresse (Grund und Zeichen nach Abstand zur
// Flaechenfarbe), hoechstens 200, Zugriff nur im Hauptthread. Ein leeres Ergebnis heisst: nicht berechenbar.
object LogoSw {
    private class Geprueft(val bild: ImageBitmap?)
    private val speicher = LinkedHashMap<String, Geprueft>()

    fun gemerkt(url: String): ImageBitmap? = speicher.remove(url)?.also { speicher[url] = it }?.bild

    suspend fun holen(url: String, bild: LogoPixel): ImageBitmap? {
        speicher.remove(url)?.let { speicher[url] = it; return it.bild }
        val erg = withContext(Dispatchers.Default) {
            val feld = bild.feld(160) ?: return@withContext null
            // Ohne einheitlichen Rand (Verlauf, zweifarbiger Rand) trifft keine Flaechenfarbe: Zeichen nach Helligkeitsgruppen
            val rand = LogoFreistellen.randfarbe(feld.px, feld.breite, feld.hoehe)
            val sw = if (rand != null) LogoFreistellen.schwarzWeiss(feld.px, rand) else LogoFreistellen.schwarzWeissNachHelligkeit(feld.px)
            logoBildErzeugen(LogoFeld(feld.breite, feld.hoehe, sw))
        }
        speicher.remove(url)
        speicher[url] = Geprueft(erg)
        while (speicher.size > 200) speicher.remove(speicher.keys.first())
        return erg
    }
}

// Schwarz-weiss ohne Hintergrund: nur das Zeichen als Maske je Bildadresse, hoechstens 200, Zugriff im Hauptthread.
// Ein leeres Ergebnis heisst: nichts Brauchbares uebrig, dann bleibt die schwarz-weisse Fassung mit Flaeche.
object LogoTinte {
    private class Geprueft(val bild: ImageBitmap?)
    private val speicher = LinkedHashMap<String, Geprueft>()

    fun gemerkt(url: String): ImageBitmap? = speicher.remove(url)?.also { speicher[url] = it }?.bild

    suspend fun holen(url: String, bild: LogoPixel, look: LogoLook?): ImageBitmap? {
        speicher.remove(url)?.let { speicher[url] = it; return it.bild }
        val erg = withContext(Dispatchers.Default) { tinteFeld(bild, look)?.let { logoBildErzeugen(it) } }
        speicher.remove(url)
        speicher[url] = Geprueft(erg)
        while (speicher.size > 200) speicher.remove(speicher.keys.first())
        return erg
    }
}

// Schwarz-weisse Fassungen einfacher Symbole aus fremden Quellen (Prognosemaerkte, Copytrading) je Bildadresse,
// hoechstens 200, Zugriff im Hauptthread. Ein leeres Ergebnis heisst: Foto, Flagge oder sonst kein einfaches Symbol.
object LogoSymbol {
    // sw: Bild mit Grund (hell: weisser Grund), maske: nur das Zeichen zum Einfaerben
    class Fassung(val sw: ImageBitmap, val hell: Boolean, val maske: ImageBitmap?)
    private class Geprueft(val f: Fassung?)
    private val speicher = LinkedHashMap<String, Geprueft>()

    fun gemerkt(url: String): Fassung? = speicher.remove(url)?.also { speicher[url] = it }?.f

    // immer: sicher ein Logo (Kennzeichen der Quelle), ohne Pruefung auf Foto oder Flagge
    suspend fun holen(url: String, bild: LogoPixel, immer: Boolean): Fassung? {
        speicher.remove(url)?.let { speicher[url] = it; return it.f }
        val erg = withContext(Dispatchers.Default) { symbolRechnen(bild, immer) }
        speicher.remove(url)
        speicher[url] = Geprueft(erg)
        while (speicher.size > 200) speicher.remove(speicher.keys.first())
        return erg
    }

    private fun symbolRechnen(bild: LogoPixel, immer: Boolean): Fassung? {
        val feld = bild.feld(160) ?: return null
        if (!immer && !LogoFreistellen.einfachesSymbol(feld.px, feld.breite, feld.hoehe)) return null
        // Weich auslaufender Rand faellt weg, Grund ist die Flaeche (bei durchsichtigem Rand null)
        val t = LogoFreistellen.symbolTeile(feld.px, feld.breite, feld.hoehe)
        // Durchsichtiger Grund: auf Weiss gelegt
        val px = if (t.grund != null) t.px else IntArray(t.px.size) { i ->
            val p = t.px[i]
            val a = (p ushr 24) and 0xFF
            fun k(v: Int) = (v * a + 255 * (255 - a)) / 255
            (0xFF shl 24) or (k((p shr 16) and 0xFF) shl 16) or (k((p shr 8) and 0xFF) shl 8) or k(p and 0xFF)
        }
        val grund = t.grund ?: -1
        val swPx = LogoFreistellen.schwarzWeiss(px, grund)
        val sw = logoBildErzeugen(LogoFeld(t.breite, t.hoehe, swPx)) ?: return null
        // Nur das Zeichen: die kleinere der beiden Helligkeitsgruppen (auch bei Kacheln mit Verlauf)
        val m = LogoFreistellen.zeichenNachHelligkeit(t.px)
        val maske = if (m.none { (it ushr 24) > 24 }) null
            else LogoFreistellen.zuschneiden(m, t.breite, t.hoehe).let { (zu, zb, zh) -> logoBildErzeugen(LogoFeld(zb, zh, zu)) }
        return Fassung(sw, LogoFreistellen.grundHell(grund), maske)
    }
}

// Bild aus fremder Quelle in einer Kachel. Fotos, Flaggen und Bilder mit Verlauf bleiben immer im Original. Nur einfache
// Symbole (etwa Parteizeichen) folgen Schwarz-weiss: mit Hintergrund schwarz-weiss, ohne Hintergrund nur das Zeichen.
@Composable
internal fun SymbolBild(
    url: String,
    name: String?,
    groesse: androidx.compose.ui.unit.Dp,
    form: androidx.compose.ui.graphics.Shape,
    grund: Color,
    innen: androidx.compose.ui.unit.Dp,
    contentScale: ContentScale,
    immerSymbol: Boolean = false,
    onError: () -> Unit,
) {
    // Vorfuehrung: neutrale Flaeche ohne Bild
    if (Vorfuehrung.aktiv) {
        Box(Modifier.size(groesse).clip(form).background(grund))
        return
    }
    val farbig = LogoAnzeige.farbig
    var fassung by remember(url, farbig) { mutableStateOf(if (!farbig) LogoSymbol.gemerkt(url) else null) }
    val scope = rememberCoroutineScope()
    val f = fassung
    if (f != null && LogoAnzeige.hintergrundEntfernen && f.maske != null) {
        Box(Modifier.size(groesse), contentAlignment = Alignment.Center) {
            Image(f.maske, name, Modifier.size(groesse * LogoDarstellung.zeichenAnteil(LogoAnzeige.rund, false)), contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
        }
        return
    }
    Box(
        Modifier.size(groesse).clip(form).background(if (f == null) grund else if (f.hell) Color.White else Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val bildModifier = Modifier.fillMaxSize().then(if (innen > 0.dp) Modifier.padding(innen) else Modifier)
        if (f != null) Image(f.sw, name, bildModifier, contentScale = contentScale)
        else LogoBild(
            url = url,
            contentDescription = name,
            modifier = bildModifier,
            contentScale = contentScale,
            onSuccess = { bild -> if (!farbig && bild != null) scope.launch { LogoSymbol.holen(url, bild, immerSymbol)?.let { fassung = it } } },
            onError = onError,
        )
    }
}

// Maske des Zeichens ohne jeden Hintergrund, auf den Inhalt zugeschnitten. Bei eigener Flaeche ist die Randfarbe der
// Grund (ohne einheitlichen Rand die gemittelte Flaechenfarbe), sonst entscheidet LogoFreistellen.tinteMaske selbst.
internal fun tinteFeld(bild: LogoPixel, look: LogoLook?): LogoFeld? {
    val feld = bild.feld(160) ?: return null
    val rand = if (look?.fill == true) LogoFreistellen.randfarbe(feld.px, feld.breite, feld.hoehe) else null
    // Eigene Flaeche ohne einheitlichen Rand (Verlauf, zweifarbiger Rand): das Zeichen ist die kleinere Helligkeitsgruppe
    val maske = if (look?.fill == true && rand == null) LogoFreistellen.zeichenNachHelligkeit(feld.px)
        else LogoFreistellen.tinteMaske(feld.px, feld.breite, feld.hoehe, rand)
    if (maske.none { (it ushr 24) > 24 }) return null
    val (zu, zb, zh) = LogoFreistellen.zuschneiden(maske, feld.breite, feld.hoehe)
    return LogoFeld(zb, zh, zu)
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
        return freistehend(small)
    }

    // Freistehendes Zeichen: helle Flaeche fuer dunkle oder bunte Zeichen, dunkle fuer helle
    // Als hell gilt nur ein fast weisses, unbuntes Zeichen. Bunte Zeichen (gruen, orange) bleiben, wie sie sind.
    fun freistehend(small: IntArray): LogoLook? {
        var lum = 0.0; var saett = 0.0; var count = 0; var weiss = 0
        for (p in small) {
            if (((p ushr 24) and 0xFF) < 128) continue
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val l = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
            lum += l
            val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
            val s = if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
            saett += s
            if (l >= 0.85 && s < 0.2) weiss++
            count++
        }
        if (count == 0) return null
        // Ein Zeichen mit grossem weissem Teil (weisses a mit orangem Bogen) braucht die dunkle Flaeche, sonst verschwindet das Weiss
        val hell = (lum / count >= 0.75 && saett / count < 0.2) || weiss >= count * 0.3
        val dunkel = lum / count < 0.3
        return LogoLook(false, if (hell) ZEICHEN_HELL else if (dunkel) Color.White else ZEICHEN_BUNT)
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

// Grund schwarz-weisser Logos mit eigener Flaeche: weiss bei heller Flaeche, sonst schwarz (wie LogoFreistellen.schwarzWeiss)
private fun swGrund(c: Color): Color =
    if (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue >= 0.6f) Color.White else Color.Black

// Farbe einer Flaeche nach derselben Regel wie tintenMatrix
private fun tinteFarbe(c: Color, dunkel: Boolean): Color {
    val l = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    val v = (1f - 8f * (0.97f - l)).coerceIn(0f, 1f)
    val w = if (dunkel) 1f - v else v
    return Color(w, w, w)
}

private fun grau(c: Color): Color {
    val l = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return Color(l, l, l)
}

// Laedt die Quellen der Reihe nach und zeigt zuletzt das Buchstabenfeld. Nebenbei wird die Markenfarbe bestimmt.
@Composable
fun InstrumentLogo(
    symbol: String,
    name: String,
    size: Int = 36,
    info: CompanyInfo? = null,
    farbig: Boolean = LogoAnzeige.farbig,
    rund: Boolean = LogoAnzeige.rund,
    ohneHintergrund: Boolean = LogoAnzeige.hintergrundEntfernen,
) {
    val alle by Logos.firmen.collectAsStateWithLifecycle()
    val familien by Gleichwerte.familien.collectAsStateWithLifecycle()
    val firma = info ?: alle[symbol]
    val familie = familien[symbol].orEmpty()
    val urls = remember(symbol, firma?.logoUrl, firma?.website, familie) { Logos.urlsFor(symbol, firma, familie) }
    var index by remember(symbol, urls) { mutableStateOf(0) }
    var failed by remember(symbol, urls) { mutableStateOf(false) }

    if (urls.isEmpty() || failed || index > urls.lastIndex || Vorfuehrung.aktiv) {
        // Vielleicht hat eine andere Notierung derselben Aktie ein Logo
        androidx.compose.runtime.LaunchedEffect(symbol) { if (!Vorfuehrung.aktiv) Logos.ohneLogo(symbol, name) }
        InstrumentAvatar(symbol, name, size, farbig = farbig, rund = rund)
        return
    }

    LogoLooks.ensure()
    val url = urls[index]
    // Freigestelltes Bild (nur bei Hintergrund entfernen): sofort aus dem Zwischenspeicher, sonst nach der Rechnung
    var frei by remember(url, ohneHintergrund) { mutableStateOf(if (ohneHintergrund) LogoFrei.gemerkt(url) else null) }
    // Schwarz-weisse Fassung bei Logos mit eigener Flaeche (nur ohne Farbe)
    var swBild by remember(url, farbig) { mutableStateOf(if (!farbig) LogoSw.gemerkt(url) else null) }
    val scope = rememberCoroutineScope()
    val dunkel = MaterialTheme.colorScheme.background.luminance() < 0.5f
    fun geladen(bild: LogoPixel?) {
        Logos.remember(symbol, url)
        LogoLooks.analyze(bild)?.let { LogoLooks.store(symbol, it) }
        if (!LogoColors.colors.containsKey(symbol)) {
            LogoColors.dominant(bild)?.let { LogoColors.colors[symbol] = it }
        }
    }

    // Schwarz-weiss ohne Hintergrund: nur das Zeichen, schwarz (im dunklen Modus weiss), ohne Flaeche oder Plaettchen
    val nurZeichen = ohneHintergrund && !farbig
    var maske by remember(url, nurZeichen) { mutableStateOf(if (nurZeichen) LogoTinte.gemerkt(url) else null) }
    var maskeFehlt by remember(url, nurZeichen) { mutableStateOf(false) }
    if (nurZeichen && !maskeFehlt) {
        val zm = Modifier.size((size * LogoDarstellung.zeichenAnteil(rund, false)).dp)
        Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
            val m = maske
            if (m != null) {
                Image(m, name, zm, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(if (dunkel) Color.White else Color.Black))
            } else LogoBild(
                url = url,
                contentDescription = name,
                // unsichtbar laden, bis die Maske gerechnet ist
                modifier = zm.graphicsLayer { alpha = 0f },
                contentScale = ContentScale.Fit,
                onSuccess = { bild ->
                    geladen(bild)
                    if (bild == null) maskeFehlt = true
                    else scope.launch { LogoTinte.holen(url, bild, LogoLooks.looks[symbol]).let { if (it != null) maske = it else maskeFehlt = true } }
                },
                onError = { if (index < urls.lastIndex) index += 1 else failed = true },
            )
        }
        return
    }

    val look = (if (ohneHintergrund) frei?.look else null) ?: LogoLooks.looks[symbol]
    val eigeneFlaeche = look != null && look.fill
    val form = RoundedCornerShape((size * LogoDarstellung.eckenAnteil(rund)).dp)
    val flaeche: Color?
    val faerbung: Color?
    val graustufen: Boolean
    val zeichenAnteil: Float
    if (!ohneHintergrund) {
        // Behalten: Darstellung wie in 0.78. Immer eine Flaeche (Randfarbe, bei freistehenden Zeichen hell oder dunkel),
        // Logos mit eigener Flaeche fuellen sie aus, freistehende Zeichen sitzen mit 0.68 darauf.
        val grund = look?.background?.let { if (it == ZEICHEN_BUNT) Color.White else it } ?: MaterialTheme.colorScheme.surfaceContainerHigh
        graustufen = !farbig
        flaeche = if (!graustufen) grund else if (look == null) grau(grund) else if (look.fill) swGrund(grund) else tinteFarbe(grund, false)
        // Helles Zeichen auf dunklem Plaettchen: in Schwarz-weiss als ganze Form weiss
        faerbung = if (!farbig && look != null && !look.fill && look.background == ZEICHEN_HELL) Color.White else null
        zeichenAnteil = LogoDarstellung.ZEICHEN_AUF_FLAECHE
    } else {
        // Entfernen: Zeichen ohne eigene Flaeche stehen frei auf der Seite. Nur ein dunkles Zeichen im dunklen Modus
        // bekommt eine dezente helle Flaeche; ein helles Zeichen im hellen Modus wird dunkel eingefaerbt.
        val freistehend = look != null && !look.fill
        val art = LogoDarstellung.art(farbig, eigeneFlaeche, freistehend)
        val zeichenDunkel = freistehend && look!!.background == Color.White
        val zeichenHell = freistehend && look!!.background == ZEICHEN_HELL
        // Schwarz-weiss ohne Hintergrund: Graustufen, damit Aussparungen im Zeichen erhalten bleiben. Ganz eingefaerbt wird
        // nur ein Zeichen, das sonst auf dem Grund verschwinden wuerde (hell auf hell, dunkel auf dunkel).
        graustufen = art == LogoDarstellung.Art.GRAUSTUFEN || art == LogoDarstellung.Art.EINFARBIG
        flaeche = when {
            look != null && look.fill -> if (!farbig) swGrund(look.background) else look.background
            else -> null
        }
        faerbung = when {
            zeichenHell && (!dunkel || !farbig) -> MaterialTheme.colorScheme.onSurface
            // Ohne Hintergrund: ein dunkles Zeichen wird im dunklen Modus hell eingefaerbt statt hinterlegt
            zeichenDunkel && dunkel -> MaterialTheme.colorScheme.onSurface
            else -> null
        }
        zeichenAnteil = LogoDarstellung.zeichenAnteil(rund, flaeche != null)
    }
    // Schwarz-weiss: alle Logos werden Tinte. Farbige Teile schwarz, helle Aussparungen bleiben hell. Nur freistehende
    // Zeichen ohne Hintergrund im dunklen Modus umgekehrt (weiss), Plaettchen und eigene Flaechen bleiben schwarz-weiss.
    val tinte = !farbig && look?.fill != true
    val tinteUmgekehrt = ohneHintergrund && dunkel && look?.fill != true
    val bildFilter: Modifier = when {
        faerbung != null -> Modifier.graphicsLayer { colorFilter = ColorFilter.tint(faerbung); compositingStrategy = CompositingStrategy.Offscreen }
        tinte -> Modifier.graphicsLayer {
            colorFilter = ColorFilter.colorMatrix(tintenMatrix(tinteUmgekehrt)); compositingStrategy = CompositingStrategy.Offscreen
        }
        graustufen -> Modifier.graphicsLayer {
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }); compositingStrategy = CompositingStrategy.Offscreen
        }
        else -> Modifier
    }
    Box(
        Modifier
            .size(size.dp)
            .clip(form)
            .then(if (flaeche != null) Modifier.background(flaeche) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Leicht vergroessert, damit keine Ecken bleiben. Freistehende Zeichen sitzen mit Rand in der Mitte.
        val bildModifier = (if (eigeneFlaeche) Modifier.fillMaxSize().graphicsLayer { scaleX = LogoDarstellung.FLAECHE_VERGROESSERUNG; scaleY = LogoDarstellung.FLAECHE_VERGROESSERUNG }
            else Modifier.size((size * zeichenAnteil).dp)).then(bildFilter)
        val freigestellt = frei
        val sw = swBild
        if (ohneHintergrund && freigestellt != null) {
            Image(freigestellt.bild, name, bildModifier, contentScale = ContentScale.Fit)
        } else if (!farbig && eigeneFlaeche && sw != null) {
            Image(sw, name, Modifier.fillMaxSize().graphicsLayer { scaleX = LogoDarstellung.FLAECHE_VERGROESSERUNG; scaleY = LogoDarstellung.FLAECHE_VERGROESSERUNG },
                contentScale = ContentScale.Crop)
        } else LogoBild(
            url = url,
            contentDescription = name,
            modifier = bildModifier,
            contentScale = if (eigeneFlaeche) ContentScale.Crop else ContentScale.Fit,
            onSuccess = { bild ->
                geladen(bild)
                if (ohneHintergrund && bild != null && LogoLooks.looks[symbol]?.fill == true) {
                    scope.launch { LogoFrei.holen(url, bild)?.let { frei = it } }
                }
                val l = LogoLooks.looks[symbol]
                if (!farbig && bild != null && l != null && l.fill) {
                    scope.launch { LogoSw.holen(url, bild)?.let { swBild = it } }
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

// Helligkeit steil auf Schwarz oder Weiss: alles unter fast Weiss wird Tinte, Weiss bleibt Grund (hell = 1 - 8 * (0.97 - L)).
// Im dunklen Modus umgekehrt. Deckkraft bleibt, damit Kanten weich sind.
internal fun tintenMatrix(dunkel: Boolean): ColorMatrix {
    val k = if (dunkel) -8f else 8f
    val e = (if (dunkel) 1f + 8f * 0.97f else 1f - 8f * 0.97f) * 255f
    val r = 0.299f * k; val g = 0.587f * k; val b = 0.114f * k
    return ColorMatrix(floatArrayOf(
        r, g, b, 0f, e,
        r, g, b, 0f, e,
        r, g, b, 0f, e,
        0f, 0f, 0f, 1f, 0f,
    ))
}

// Warme Toenung fuer farbige Logos im Sepia-Modus (volle Sepia-Matrix).
internal fun sepiaMatrix(): ColorMatrix {
    val a = 1f; val o = 1f - a
    return ColorMatrix(floatArrayOf(
        o + a * 0.393f, a * 0.769f, a * 0.189f, 0f, 0f,
        a * 0.349f, o + a * 0.686f, a * 0.168f, 0f, 0f,
        a * 0.272f, a * 0.534f, o + a * 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
}
