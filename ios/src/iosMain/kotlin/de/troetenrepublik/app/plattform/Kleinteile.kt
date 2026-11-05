package de.troetenrepublik.app.plattform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import de.troetenrepublik.app.data.WidgetDaten
import de.troetenrepublik.app.ios.Dateien
import java.io.File
import java.io.IOException

// Kleine Plattformteile fuer iPhone und iPad. Gleiche Namen und oeffentliche Teile wie unter Android und am Desktop.

// Datei, die beim Schreiben nie halb fertig liegen bleibt: iOS schreibt in eine Nachbardatei und benennt dann um.
class SichereDatei(private val datei: File) {
    fun lesen(): ByteArray = datei.readBytes()

    fun schreiben(bytes: ByteArray) = de.troetenrepublik.app.ios.Sperre.mit {
        datei.parentFile?.mkdirs()
        if (!Dateien.schreiben(datei.path, bytes)) throw IOException("${datei.path} (write failed)")
    }
}

// Auf iOS ohne Widgets.
object Widgets {
    val verfuegbar: Boolean = false

    fun aktualisieren(daten: WidgetDaten) {}

    fun vorhanden(): Boolean = false

    fun einzelSymbole(): List<String> = emptyList()

    const val ART_DEPOT = "depot"
    const val ART_EINZEL = "einzelwert"
    const val ART_DIVIDENDEN = "dividenden"
    const val ART_MAERKTE = "maerkte"
    const val ART_STIMMUNG = "stimmung"
    const val ART_NEWS = "news"
    const val ART_HEATMAP = "heatmap"

    fun arten(): Set<String> = emptySet()

    fun listenSymbole(): List<String> = emptyList()

    fun newsArten(): Set<String> = emptySet()

    fun barometerGewaehlt(schluessel: String): Boolean = false

    fun depotZeitraeume(): Set<String> = emptySet()

    fun heatmapAuswahl(): List<Pair<String, Int>> = emptyList()
}

// Die Einstellung „Screenshots“ gibt es nur unter Android.
val bildschutzMoeglich: Boolean = false

@Composable
fun Bildschutz(aktiv: Boolean) {}

// Unter Android: kein Bild in der App-Uebersicht, solange aktiv. Hier ohne Wirkung.
@Composable
fun VorschauSchutz(aktiv: Boolean) {}

// PNG- oder JPEG-Daten als Bild; null, wenn sie sich nicht lesen lassen.
fun pngBild(daten: ByteArray): ImageBitmap? = runCatching { org.jetbrains.skia.Image.makeFromEncoded(daten).toComposeImageBitmap() }.getOrNull()

// Auf dem Telefon wischt der Finger. Gegenstuecke fuer die Maus: MausScrollen.kt im Desktop-Modul.
fun Modifier.horizontalScrollMaus(state: ScrollState, senkrechtesRad: Boolean = true): Modifier = horizontalScroll(state)

// Auf dem Telefon wischt der Finger, das kann der Pager selbst. Die Mausvariante liegt im Desktop-Modul.
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun Modifier.mausWischen(pager: PagerState): Modifier = this

// Eingebetteter Browser fuer MarketWatch (am Rechner). Auf iOS oeffnen solche Seiten in Safari; hier gibt es
// nichts zu laden.
object WebBaustein {
    val groesseMb: Int = 0
    fun noetig(link: String): Boolean = false
    val installiert: Boolean get() = true
    suspend fun installieren(fortschritt: (Float) -> Unit): Boolean = true
    suspend fun artikelOeffnen(link: String, titel: String): Boolean = false
    val artikelGeladen: Boolean = false
    val artikelOffen: Boolean = false
    suspend fun artikelText(): List<String>? = null
    fun beenden() {}
}
