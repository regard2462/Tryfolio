package de.troetenrepublik.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Seiten ueber der Startseite als Stapel. Anders als ein NavHost bleibt jede Seite im Stapel aufgebaut, auch
// die Startseite, damit die Seite darunter beim Wegwischen sofort sichtbar ist. Blaetter kommen von unten und
// gleiten nach unten hinaus, die Kurzanleitung blendet ein und aus.
class Seitenstapel internal constructor(private val scope: CoroutineScope, gespeichert: List<Pair<Long, String>>) {

    // Hoehe des Bereichs in Pixeln, der Weg einer Seite von unten bis an ihren Platz.
    var hoehe by mutableFloatStateOf(0f)

    // Kennungen bleiben ueber eine Neuerstellung der Activity gleich, damit der gespeicherte Zustand jeder Seite passt
    private var naechsteId = (gespeichert.maxOfOrNull { it.first } ?: 0L) + 1L

    // Alle Seiten, unten zuerst; auch solche, die gerade hinauslaufen.
    val eintraege = mutableStateListOf<Eintrag>()

    // Bilder von Blaettern der Startseite, die nach einer aus ihnen geoeffneten Seite wiederkommen.
    val heimBilder = mutableStateMapOf<Any, @Composable () -> Unit>()

    // true, wenn ueber der Ebene index (-1 = Startseite) keine Seite mehr liegt, auch keine hinauslaufende.
    fun frei(index: Int): Boolean = eintraege.size <= index + 1

    // true, wenn die Seite direkt ueber der Ebene index weggewischt wird oder hinauslaeuft.
    fun aufgedeckt(index: Int): Boolean {
        val darueber = eintraege.getOrNull(index + 1) ?: return true
        return !darueber.eingang && (darueber.y > 0f || darueber.geht)
    }

    val heimEbene = Ebene(frei = { frei(-1) }, aufgedeckt = { aufgedeckt(-1) }, bilder = heimBilder)

    // Eine Seite als Ebene fuer die Blaetter, die sie oeffnet.
    fun ebene(e: Eintrag): Ebene = Ebene(frei = { frei(eintraege.indexOf(e)) }, aufgedeckt = { aufgedeckt(eintraege.indexOf(e)) }, bilder = e.bilder)

    init {
        gespeichert.forEach { (id, route) -> eintraege += Eintrag(id, route).also { e -> e.eingang = false } }
    }

    // Eine Seite im Stapel. y: Verschiebung nach unten in Pixeln, alpha: Deckkraft.
    inner class Eintrag(val id: Long, val route: String) {
        // Blaetter gleiten von unten herein und lassen sich wegwischen; die Kurzanleitung blendet nur ein.
        val blatt: Boolean = route != "guide"
        var y by mutableFloatStateOf(0f)
        var alpha by mutableFloatStateOf(1f)
        // true, solange die Seite hineingleitet. Die Seite darunter bleibt dann unveraendert sichtbar.
        var eingang by mutableStateOf(true)
        // true, sobald die Seite den Stapel verlaesst, auch wenn die Animation noch laeuft.
        var geht by mutableStateOf(false)
        // Bilder ihrer Blaetter, die nach einer aus ihnen geoeffneten Seite wiederkommen.
        val bilder = mutableStateMapOf<Any, @Composable () -> Unit>()
        private var job: Job? = null

        val hoehe: Float get() = this@Seitenstapel.hoehe.coerceAtLeast(1f)

        // Eine laufende Bewegung anhalten, etwa wenn der Finger die Seite greift.
        fun halten() {
            job?.cancel()
            job = null
            eingang = false
        }

        internal fun bewegen(ziel: Float, spec: AnimationSpec<Float>, v0: Float = 0f, danach: () -> Unit = {}) {
            job?.cancel()
            job = scope.launch {
                animate(y, ziel, v0, spec) { wert, _ -> y = wert }
                y = ziel
                job = null
                danach()
            }
        }

        internal fun blenden(ziel: Float, spec: AnimationSpec<Float>, danach: () -> Unit = {}) {
            job?.cancel()
            job = scope.launch {
                animate(alpha, ziel, 0f, spec) { wert, _ -> alpha = wert }
                alpha = ziel
                job = null
                danach()
            }
        }

        // Nach einer Wischgeste ganz hinaus: die Seite laeuft mit der Fingergeschwindigkeit weiter und wird hoechstens
        // sanft schneller, danach verlaesst sie den Stapel.
        fun schliessen(v0: Float = 0f, fertig: () -> Unit = {}) {
            geht = true
            eingang = false
            val (ms, easing) = auslauf(hoehe - y, v0)
            bewegen(hoehe, tween(ms, easing = easing)) { entfernen(this); fertig() }
        }

        // Nach einer Wischgeste zurueck an den Platz.
        fun zurueck(v0: Float = 0f, fertig: () -> Unit = {}) {
            bewegen(0f, spring(dampingRatio = 1f, stiffness = 700f), v0) { fertig() }
        }
    }

    // Seiten, die nicht gerade hinauslaufen.
    val offen: List<Eintrag> get() = eintraege.filter { !it.geht }

    val leer: Boolean get() = eintraege.none { !it.geht }

    fun oberste(): Eintrag? = eintraege.lastOrNull { !it.geht }

    // Neue Seite oben auf den Stapel: Blaetter gleiten von unten herein, die Kurzanleitung blendet ein.
    fun oeffnen(route: String) {
        val e = Eintrag(naechsteId++, route)
        if (e.blatt) {
            e.y = e.hoehe
            e.bewegen(0f, tween(SHEET_IN_MS, easing = EASE_IN_PLACE)) { e.eingang = false }
        } else {
            e.alpha = 0f
            e.blenden(1f, tween(FADE_MS)) { e.eingang = false }
        }
        eintraege += e
    }

    // Eine Seite schliessen ohne Geste (Pfeil, Zurueck-Taste): sanft los, dann zuegig hinaus.
    fun schliessen(e: Eintrag? = oberste()) {
        val ziel = e ?: return
        if (ziel.geht) return
        ziel.geht = true
        ziel.eingang = false
        if (ziel.blatt) ziel.bewegen(ziel.hoehe, tween(SHEET_OUT_MS, easing = EASE_OUT_OF_VIEW)) { entfernen(ziel) }
        else ziel.blenden(0f, tween(FADE_MS)) { entfernen(ziel) }
    }

    // Zurueck-Geste des Systems mit Vorschau: die oberste Seite folgt dem Fortschritt (0 bis 1).
    fun vorschau(e: Eintrag, fortschritt: Float) {
        e.halten()
        val p = fortschritt.coerceIn(0f, 1f)
        if (e.blatt) e.y = EASE_OUT_OF_VIEW.transform(p) * e.hoehe else e.alpha = 1f - p
    }

    // Zurueck-Geste des Systems abgebrochen: Seite an ihren Platz.
    fun vorschauAbbrechen(e: Eintrag) {
        if (e.blatt) e.bewegen(0f, tween(SHEET_OUT_MS, easing = EASE_IN_PLACE)) else e.blenden(1f, tween(SHEET_OUT_MS))
    }

    private fun entfernen(e: Eintrag) {
        eintraege.remove(e)
    }

    // Gezeichnet werden die oberste Seite und die darunter, die sichtbar wird, sobald sich die oberste bewegt.
    fun sichtbar(index: Int): Boolean {
        // Index -1 = Startseite. Hinauslaufende Seiten liegen oben und werden gezeichnet, bis sie fort sind.
        return index >= eintraege.indexOfLast { !it.geht } - 1
    }

    companion object {
        // Auslauf ueber weg Pixel bei v0 Pixeln pro Sekunde: gleichbleibend, wenn der Finger schnell genug war, sonst
        // gleichmaessig beschleunigt, so dass die Seite nach hoechstens 320 ms unten ist. Ergebnis: Dauer in ms, Verlauf.
        fun auslauf(weg: Float, v0: Float): Pair<Int, Easing> {
            if (weg <= 1f) return 1 to LinearEasing
            val v = v0.coerceAtLeast(0f)
            val linear = if (v > 0f) weg / v else Float.MAX_VALUE
            val sek = linear.coerceIn(AUSLAUF_MIN_S, AUSLAUF_MAX_S)
            // Anfangssteigung im Verlauf von 0 bis 1: Fingergeschwindigkeit mal Dauer durch Weg, 1 = gleichmaessig
            val s0 = (v * sek / weg).coerceIn(0f, 1f)
            return (sek * 1000f).toInt().coerceAtLeast(1) to Easing { t -> s0 * t + (1f - s0) * t * t }
        }

        private const val AUSLAUF_MIN_S = 0.12f
        private const val AUSLAUF_MAX_S = 0.32f

        fun saver(scope: CoroutineScope): Saver<Seitenstapel, Any> = Saver(
            save = { stapel -> ArrayList(stapel.eintraege.filter { !it.geht }.map { "${it.id}|${it.route}" }) },
            restore = { gespeichert ->
                val liste = (gespeichert as? List<*>).orEmpty().mapNotNull { zeile ->
                    val text = zeile as? String ?: return@mapNotNull null
                    val id = text.substringBefore('|').toLongOrNull() ?: return@mapNotNull null
                    id to text.substringAfter('|')
                }
                Seitenstapel(scope, liste)
            },
        )

        const val SHEET_IN_MS = 360
        const val SHEET_OUT_MS = 280
        const val FADE_MS = 700

        // Hereingleiten: schnell los, sanft zur Ruhe.
        val EASE_IN_PLACE = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        // Hinausgleiten ohne Geste: sanft los, dann zuegig hinaus.
        val EASE_OUT_OF_VIEW = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    }
}

@Composable
fun rememberSeitenstapel(): Seitenstapel {
    val scope = rememberCoroutineScope()
    return rememberSaveable(saver = Seitenstapel.saver(scope)) { Seitenstapel(scope, emptyList()) }
}

// Die Seite, in der ein Inhalt liegt; null ausserhalb des Stapels (dann ohne Wischgeste).
val LocalSeite = staticCompositionLocalOf<Seitenstapel.Eintrag?> { null }

// Lage und Deckkraft beim Zeichnen, undurchsichtiger Grund, Beruehrungen erreichen die Seiten darunter nicht.
// Verdeckte Seiten werden nicht gezeichnet. Der Grund deckt auch die Statusleiste, der Inhalt haelt die
// Systemleisten selbst frei und verbraucht sie, damit Kopfzeilen sie nicht noch einmal einrechnen.
@Composable
fun SeitenRahmen(eintrag: Seitenstapel.Eintrag, ebene: Ebene, sichtbar: Boolean, oben: Boolean, grund: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSeite provides eintrag, LocalEbene provides ebene) {
        Box(Modifier.fillMaxSize()) {
            // Beim Wegwischen der Suche bleibt der Grund dunkel, die Seite darunter erscheint erst am Ende
            if (eintrag.route == "search") {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = eintrag.y / eintrag.hoehe
                            alpha = if (eintrag.eingang || eintrag.y <= 0f || !sichtbar) 0f else (1f - ((p - 0.8f) / 0.2f)).coerceIn(0f, 1f)
                        }
                        .background(Color.Black),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = eintrag.y
                        alpha = if (sichtbar) eintrag.alpha else 0f
                    }
                    .then(if (oben) Modifier else Modifier.clearAndSetSemantics {})
                    // Empfaenger ueber die ganze Flaeche, damit Fingertipps nicht an die Seite darunter gehen
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) } }
                    .background(grund)
                    .systemBarsPadding(),
            ) { content() }
            // Bild eines Blatts dieser Seite, solange die daraus geoeffnete Seite darueber liegt: fertig aufgebaut,
            // gezeichnet erst beim Wegwischen, damit die Seite beim Ansetzen nicht stockt
            if (eintrag.bilder.isNotEmpty()) {
                Box(Modifier.fillMaxSize().graphicsLayer { translationY = eintrag.y; alpha = if (sichtbar && ebene.aufgedeckt()) eintrag.alpha else 0f }) {
                    eintrag.bilder.forEach { (k, bild) -> key(k) { bild() } }
                }
            }
        }
    }
}
