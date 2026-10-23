package de.troetenrepublik.app.plattform

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.troetenrepublik.app.ui.LogoPixel

// Laedt ein Logo mit Coil. onSuccess bekommt die Pixel fuer Darstellung und Markenfarbe.
@Composable
fun LogoBild(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onSuccess: (LogoPixel?) -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            // asset: steht fuer mitgelieferte Bilder.
            .data(if (url.startsWith("asset:")) "file:///android_asset/" + url.removePrefix("asset:") else url)
            // Wikimedia (Wikidata-Logos, Profilbilder) lehnt die Standardkennung der Bildbibliothek ab
            .addHeader("User-Agent", BROWSER)
            .allowHardware(false)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onSuccess = { state -> onSuccess((state.result.drawable as? BitmapDrawable)?.bitmap?.let { BitmapPixel(it) }) },
        onError = { onError() },
    )
}

// Allgemeine Browserkennung wie bei den Kursabrufen, ohne Angaben zu Nutzer oder Geraet.
private const val BROWSER = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

private class BitmapPixel(private val bitmap: Bitmap) : LogoPixel {
    override val breite: Int get() = bitmap.width
    override val hoehe: Int get() = bitmap.height

    override fun verkleinert(n: Int): IntArray? {
        val small = runCatching { Bitmap.createScaledBitmap(bitmap, n, n, true) }.getOrNull() ?: return null
        val px = IntArray(n * n)
        small.getPixels(px, 0, n, 0, 0, n, n)
        return px
    }
}
