package de.troetenrepublik.app.plattform

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import de.troetenrepublik.app.ios.Sperre
import de.troetenrepublik.app.ui.LogoPixel
import kotlinx.coroutines.withContext

// Bereits geladene Logos (und Adressen, die nichts lieferten), damit Listen nicht neu laden. Hoechstens 400 Bilder,
// das am laengsten nicht gebrauchte faellt zuerst heraus.
private object LogoSpeicher {
    private val bilder = LinkedHashMap<String, ImageBitmap>()
    private val fehler = HashSet<String>()

    fun bild(url: String): ImageBitmap? = Sperre.mit { bilder.remove(url)?.also { bilder[url] = it } }
    fun merken(url: String, b: ImageBitmap) = Sperre.mit {
        bilder.remove(url)
        bilder[url] = b
        while (bilder.size > 400) bilder.remove(bilder.keys.first())
    }
    fun fehlt(url: String): Boolean = Sperre.mit { url in fehler }
    fun fehler(url: String) = Sperre.mit { fehler += url }
}

// Logo aus dem Netz: laden, mit Skia lesen, anzeigen.
@Composable
fun LogoBild(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onSuccess: (LogoPixel?) -> Unit,
    onError: () -> Unit,
) {
    var bild by remember(url) { mutableStateOf(LogoSpeicher.bild(url)) }
    val ok by rememberUpdatedState(onSuccess)
    val fehler by rememberUpdatedState(onError)
    LaunchedEffect(url) {
        val geladen = bild ?: if (LogoSpeicher.fehlt(url)) null else withContext(de.troetenrepublik.app.ios.ioDispatcher) {
            // asset: mitgelieferte Bilder
            (if (url.startsWith("asset:")) runCatching { Plattform.asset(url.removePrefix("asset:")) }.getOrNull()
            else Netz.bytes(url, max = 2 * 1024 * 1024))?.let { bytes ->
                // Nur PNG und JPEG bis 2048 Pixel je Seite, wie auf den anderen Plattformen
                if (!sicheresBild(bytes)) return@let null
                runCatching {
                    val codec = org.jetbrains.skia.Codec.makeFromData(org.jetbrains.skia.Data.makeFromBytes(bytes))
                    if (codec.width !in 1..2048 || codec.height !in 1..2048) null
                    else org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull()
            }
        }
        if (geladen == null || geladen.width == 0 || geladen.height == 0) {
            LogoSpeicher.fehler(url)
            fehler()
        } else {
            LogoSpeicher.merken(url, geladen)
            bild = geladen
            ok(BildPixel(geladen))
        }
    }
    bild?.let { Image(it, contentDescription, modifier, contentScale = contentScale) }
}

private class BildPixel(private val bild: ImageBitmap) : LogoPixel {
    override val breite: Int get() = bild.width
    override val hoehe: Int get() = bild.height

    override fun verkleinert(n: Int): IntArray? = runCatching {
        val ziel = ImageBitmap(n, n)
        Canvas(ziel).drawImageRect(
            bild, srcSize = IntSize(bild.width, bild.height), dstSize = IntSize(n, n),
            paint = Paint().apply { filterQuality = FilterQuality.Low },
        )
        IntArray(n * n).also { ziel.readPixels(it) }
    }.getOrNull()
}

// PNG oder JPEG an den ersten Bytes erkannt.
internal fun sicheresBild(b: ByteArray): Boolean = b.size > 8 && (
    (b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte()) ||
        (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte())
    )
