package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// Ebene, in der ein Blatt liegt: die Startseite oder eine Seite im Stapel. frei: keine Seite liegt mehr
// darueber; aufgedeckt: die Seite darueber wird weggewischt oder laeuft hinaus; bilder: Bilder von Blaettern, die
// die Ebene unter der Seite darueber zeigt.
class Ebene(val frei: () -> Boolean, val aufgedeckt: () -> Boolean, val bilder: SnapshotStateMap<Any, @Composable () -> Unit>)

// Ohne Stapel (Vorschau, Tests): nichts liegt darueber, Bilder werden nicht gezeigt.
val LocalEbene = staticCompositionLocalOf { Ebene(frei = { true }, aufgedeckt = { false }, bilder = mutableStateMapOf()) }

// Ein Blatt, das wiederkommt: oeffnet ein Tipp im Blatt eine Seite, schliesst sich das Blatt. Beim Wegwischen
// der Seite liegt sein Bild darunter, danach oeffnet das Blatt sofort ganz. So landet man nach dem Zurueck
// dort, wo man war.
@Stable
class Wiederkehr(wartet: Boolean) {
    // Das Blatt ist zu, weil eine aus ihm geoeffnete Seite darueber liegt.
    var wartet by mutableStateOf(wartet)
        internal set

    // Das Blatt kommt gerade zurueck: ohne Hereingleiten oeffnen.
    var sofort by mutableStateOf(false)
        internal set

    // Vor dem Oeffnen einer Seite aus dem Blatt: das Blatt schliesst sich und kommt danach wieder.
    fun verlassen() {
        wartet = true
        sofort = false
    }

    // Das Blatt wurde geschlossen (Wischen, Zurueck): beim naechsten Oeffnen gleitet es wieder herein.
    fun vergessen() {
        wartet = false
        sofort = false
    }
}

@Composable
fun rememberWiederkehr(): Wiederkehr {
    val w = rememberSaveable(saver = Saver<Wiederkehr, Boolean>(save = { it.wartet }, restore = { Wiederkehr(it) })) { Wiederkehr(false) }
    val ebene = LocalEbene.current
    LaunchedEffect(w, w.wartet) {
        if (!w.wartet) return@LaunchedEffect
        // Erst muss die Seite darueber liegen (hoechstens zwei Sekunden), dann wartet das Blatt, bis sie wieder fort ist
        if (withTimeoutOrNull(2_000) { snapshotFlow { ebene.frei() }.first { !it } } == null) {
            w.wartet = false
            return@LaunchedEffect
        }
        snapshotFlow { ebene.frei() }.first { it }
        w.sofort = true
        w.wartet = false
    }
    return w
}

// Zustand eines Blatts: gleitet herein oder steht mit sofort gleich ganz offen da (gilt fuer die ganze Lebenszeit).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun blattZustand(sofort: Boolean): SheetState {
    val anfang = remember { sofort }
    val dichte = LocalDensity.current
    val gleitend = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val offen = remember { SheetState(skipPartiallyExpanded = true, density = dichte, initialValue = SheetValue.Expanded) }
    return if (anfang) offen else gleitend
}

// Bild des Blatts unter der aus ihm geoeffneten Seite, solange w wartet: fertig aufgebaut, sobald die Seite steht,
// und gezeichnet, sobald sie weggewischt wird. Das echte Blatt ist ein eigenes Fenster und laesst sich nicht unter den
// Stapel legen.
@Composable
fun BlattBild(w: Wiederkehr, schluessel: Any, inhalt: @Composable ColumnScope.() -> Unit) {
    val ebene = LocalEbene.current
    val aktuell by rememberUpdatedState(inhalt)
    LaunchedEffect(w.wartet, schluessel, ebene) {
        if (w.wartet) {
            // Erst aufbauen, wenn die Seite darueber steht, sonst stoert das Aufbauen ihr Hereingleiten
            delay(Seitenstapel.SHEET_IN_MS + 80L)
            ebene.bilder[schluessel] = { BlattBildRahmen { aktuell() } }
        } else if (schluessel in ebene.bilder) {
            // Kommt das Blatt zurueck, bleibt das Bild noch kurz liegen: das echte Blatt ist ein eigenes Fenster und
            // steht erst nach ein, zwei Bildern. Ohne Nachlauf blitzt dazwischen die Seite ohne Blatt auf.
            delay(BILD_NACHLAUF_MS)
            ebene.bilder.remove(schluessel)
        }
    }
    DisposableEffect(schluessel, ebene) { onDispose { ebene.bilder.remove(schluessel) } }
}

// So lange liegt das Bild noch unter dem zurueckgekehrten Blatt.
private const val BILD_NACHLAUF_MS = 300L

// Form eines offenen Blatts ohne Eingaben: abgedunkelter Grund, oben abgerundet, Griff, darunter der Inhalt.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlattBildRahmen(inhalt: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) } }
            .background(BottomSheetDefaults.ScrimColor),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(BottomSheetDefaults.windowInsets),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BottomSheetDefaults.DragHandle()
            inhalt()
        }
    }
}
