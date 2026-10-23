package de.troetenrepublik.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Gleiten wie beim Pager: weich, ohne Nachschwingen, mit dem Tempo des Fingers.
private val GLEITEN = spring<Float>(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.5f)

// Seiten zum Wischen ohne Pager, fuer Abschnitte mitten in einer langen Liste. Ein Pager muesste den ganzen
// Abschnitt als einen Listeneintrag aufbauen, dann entsteht alles auf einmal und das Scrollen stockt. Hier bleibt
// jede Zeile ein eigener Eintrag, alle Zeilen teilen sich diesen Zustand und gleiten gemeinsam. An der ersten und
// letzten Seite bleibt eine Bewegung nach aussen beim umgebenden Pager.
@Stable
class Wischwahl internal constructor(start: Int, val anzahl: Int, private val scope: CoroutineScope) {
    // Wechselt erst, wenn die neue Seite ganz zu sehen ist.
    var seite by mutableIntStateOf(start.coerceIn(0, (anzahl - 1).coerceAtLeast(0)))
        private set

    // In Pixeln. Negativ heisst, die naechste Seite kommt von rechts.
    internal var versatz by mutableFloatStateOf(0f)
        private set
    internal var breite by mutableFloatStateOf(0f)

    // Ziel beim Antippen einer Pille, die nicht direkt neben der gewaehlten liegt.
    private var ziel by mutableStateOf<Int?>(null)
    private var lauf: Job? = null

    // Die Seite, die gerade neben der gewaehlten zu sehen ist, null in Ruhe.
    internal fun nachbar(): Int? = ziel ?: when {
        versatz < 0f -> (seite + 1).takeIf { it < anzahl }
        versatz > 0f -> (seite - 1).takeIf { it >= 0 }
        else -> null
    }

    // 0 bis 1
    internal fun anteil(): Float = if (breite <= 0f) 0f else (abs(versatz) / breite).coerceIn(0f, 1f)

    // Seite mit Bruchteil wie currentPage + currentPageOffsetFraction beim Pager, fuer MitlaufendeReiter.
    fun stand(): Float {
        val n = nachbar() ?: return seite.toFloat()
        return seite + (n - seite) * anteil()
    }

    fun zu(i: Int) {
        val z = i.coerceIn(0, anzahl - 1)
        lauf?.cancel()
        if (z == seite) {
            ziel = null
            lauf = scope.launch { gleiten(0f, 0f) }
            return
        }
        if (breite <= 0f) {
            wechseln(z)
            return
        }
        ziel = z
        lauf = scope.launch {
            gleiten(if (z > seite) -breite else breite, 0f)
            wechseln(z)
        }
    }

    // dx positiv nach rechts. Bewegt der Zug hier nichts, gehoert er dem umgebenden Pager.
    internal fun nimmt(dx: Float): Boolean = when {
        versatz != 0f || ziel != null -> true
        dx < 0f -> seite < anzahl - 1
        dx > 0f -> seite > 0
        else -> false
    }

    internal fun ziehenBeginnen() {
        lauf?.cancel()
        lauf = null
        // Ein Sprung per Pille war unterwegs: gleich ans Ziel, dann von dort weiter.
        ziel?.let { wechseln(it) }
        // Ein Gleiten war unterwegs: von der aktuellen Stelle aus weiter.
        einordnen()
    }

    internal fun ziehen(dx: Float) {
        val min = if (seite < anzahl - 1) -breite else 0f
        val max = if (seite > 0) breite else 0f
        versatz = (versatz + dx).coerceIn(min, max)
        einordnen()
    }

    // Wie beim Pager: Ist die Nachbarseite mehr als halb herein, wird sie zur gewaehlten. So geht ein schnelles
    // zweites Wischen zur naechsten Seite weiter, statt an der Nachbarseite haengen zu bleiben.
    private fun einordnen() {
        val b = breite
        if (b <= 0f) return
        var s = seite
        var v = versatz
        while (v < -b / 2 && s < anzahl - 1) { s += 1; v += b }
        while (v > b / 2 && s > 0) { s -= 1; v -= b }
        if (s != seite) Snapshot.withMutableSnapshot { seite = s; versatz = v }
    }

    // Zuegig gewischt geht es in Wischrichtung weiter, oder zurueck, wenn die Bewegung zur gewaehlten Seite zeigt.
    // Langsam geht es zur Seite, von der mehr als ein Viertel zu sehen ist.
    internal fun loslassen(tempo: Float, schwelle: Float) {
        val b = breite
        val neu = when {
            tempo < -schwelle -> if (versatz < 0f && seite < anzahl - 1) seite + 1 else seite
            tempo > schwelle -> if (versatz > 0f && seite > 0) seite - 1 else seite
            versatz < 0f && seite < anzahl - 1 && -versatz > b / 4 -> seite + 1
            versatz > 0f && seite > 0 && versatz > b / 4 -> seite - 1
            else -> seite
        }
        lauf?.cancel()
        lauf = scope.launch {
            gleiten(if (neu > seite) -b else if (neu < seite) b else 0f, tempo)
            if (neu != seite) wechseln(neu)
        }
    }

    private suspend fun gleiten(nach: Float, tempo: Float) {
        // Nie ueber die Nachbarseite hinaus, sonst schiesst die Feder bei schnellem Wischen darueber.
        val b = breite
        animate(versatz, nach, tempo, GLEITEN) { v, _ -> versatz = if (b > 0f) v.coerceIn(-b, b) else v }
    }

    // Seite und Versatz zugleich umstellen, damit kein Bild mit nur einem von beiden entsteht.
    private fun wechseln(neu: Int) {
        Snapshot.withMutableSnapshot {
            seite = neu
            versatz = 0f
            ziel = null
        }
    }
}

@Composable
fun rememberWischwahl(anzahl: Int, start: Int = 0): Wischwahl {
    val scope = rememberCoroutineScope()
    return remember(anzahl) { Wischwahl(start, anzahl, scope) }
}

// Beim Wischen steht daneben der Inhalt der Nachbarseite. Die Hoehe geht stetig von einer Zeile zur anderen
// ueber, damit nichts springt, wenn die Seiten verschieden lang sind.
@Composable
fun WischZeile(wahl: Wischwahl, modifier: Modifier = Modifier, inhalt: @Composable (seite: Int) -> Unit) {
    val seite = wahl.seite
    // Aufgebaut sind die sichtbaren Seiten und je eine daneben, gemessen am Stand, nicht an der gewaehlten Seite.
    // So entsteht beim Wechsel mitten im Wischen nichts Neues. key() haelt jede Seite fest, wenn die gewaehlte wechselt.
    val seiten by remember(wahl) {
        derivedStateOf {
            val f = wahl.stand()
            val von = kotlin.math.floor(f).toInt() - 1
            val bis = kotlin.math.ceil(f).toInt() + 1
            (von..bis).filter { it in 0 until wahl.anzahl }
        }
    }
    Layout(
        content = {
            seiten.forEach { s ->
                key(s) {
                    Box(Modifier.graphicsLayer { translationX = wahl.versatz + (s - wahl.seite) * wahl.breite }) { inhalt(s) }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { if (it.width > 0) wahl.breite = it.width.toFloat() }
            .wischbar(wahl),
    ) { teile, c ->
        val gemessen = teile.map { it.measure(c) }
        val haupt = gemessen[seiten.indexOf(seite)]
        val n = wahl.nachbar()
        val neben = n?.let { seiten.indexOf(it) }?.takeIf { it >= 0 }?.let { gemessen[it] }
        val hoehe = if (neben == null) haupt.height else (haupt.height + (neben.height - haupt.height) * wahl.anteil()).roundToInt()
        layout(haupt.width, hoehe) { gemessen.forEach { it.placeRelative(0, 0) } }
    }
}

// Mit Finger und Maus. Genommen wird nur eine Bewegung, die hier etwas verschiebt. An den Raendern bleibt sie
// fuer den umgebenden Pager, senkrechte Bewegungen fuer die Liste.
internal fun Modifier.wischbar(wahl: Wischwahl): Modifier = pointerInput(wahl) {
    val schwelle = 400.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tempo = VelocityTracker()
        tempo.addPosition(down.uptimeMillis, down.position)
        var vorab = 0f
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { c, ueber ->
            if (wahl.nimmt(ueber)) {
                c.consume()
                vorab = ueber
            }
        } ?: return@awaitEachGesture
        wahl.ziehenBeginnen()
        wahl.ziehen(vorab)
        tempo.addPosition(start.uptimeMillis, start.position)
        horizontalDrag(start.id) { c ->
            wahl.ziehen(c.positionChange().x)
            tempo.addPosition(c.uptimeMillis, c.position)
            c.consume()
        }
        wahl.loslassen(tempo.calculateVelocity().x, schwelle)
    }
}

// Die Pille gleitet zum gewaehlten index, statt zu springen.
@Composable
fun rememberGleitStand(index: Int): () -> Float {
    val stand = remember { Animatable(index.toFloat()) }
    LaunchedEffect(index) { stand.animateTo(index.toFloat(), GLEITEN) }
    return { stand.value }
}
