package de.troetenrepublik.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Rollstand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// [0.78] Haelt den Rollstand einer rollenden Spalte, die neu aufgebaut wird (Blatt kommt nach einer Aktienseite wieder,
// Bild des Blatts unter der Seite): Der Stand beim Aufbau ist das Ziel. Bis der Inhalt nachgewachsen ist, stuetzt eine
// Mindesthoehe den Stand, damit die Rollflaeche ihn nicht kuerzt und nichts springt.
@Stable
internal class RollstandHalter(val ziel: Int) {
    var halten by mutableStateOf(ziel > 0)
    var inhalt by mutableIntStateOf(0)
}

// Steht hinter verticalScroll(scroll): Modifier.verticalScroll(scroll).rollstandHalten(scroll)
fun Modifier.rollstandHalten(scroll: ScrollState): Modifier = composed {
    val halter = remember(scroll) { RollstandHalter(scroll.value) }
    val ersatz = with(LocalDensity.current) { 1000.dp.roundToPx() }
    LaunchedEffect(halter) {
        if (halter.ziel <= 0) return@LaunchedEffect
        var gerollt = false
        withTimeoutOrNull(Rollstand.STUETZE_MS) {
            snapshotFlow { Rollstand.stuetzeEnde(halter.inhalt, halter.ziel, scroll.viewportSize, scroll.isScrollInProgress) }.first { it }
        }
        gerollt = scroll.isScrollInProgress
        halter.halten = false
        if (gerollt) return@LaunchedEffect
        // Inhalt, der erst spaeter lang genug ist: den Stand einmal nachholen, solange die Person nicht selbst rollt
        withTimeoutOrNull(Rollstand.NACHHOLEN_MS) {
            snapshotFlow { scroll.isScrollInProgress to Rollstand.nachholen(halter.inhalt, halter.ziel, scroll.viewportSize, scroll.value, false) }
                .first { (rollt, jetzt) -> rollt || jetzt || scroll.value >= halter.ziel }
                .let { (rollt, jetzt) -> if (!rollt && jetzt) scroll.scrollTo(halter.ziel) }
        }
    }
    layout { messbar, grenzen ->
        val p = messbar.measure(grenzen)
        halter.inhalt = p.height
        layout(p.width, Rollstand.hoehe(p.height, halter.ziel, scroll.viewportSize, ersatz, halter.halten)) { p.place(0, 0) }
    }
}

// Rollende Spalte eines Blatts mit gemerktem und gehaltenem Stand.
@Composable
fun Modifier.rollenMitStand(): Modifier {
    val scroll = rememberScrollState()
    return this.verticalScroll(scroll).rollstandHalten(scroll)
}

// Dasselbe fuer Listen aus einzelnen Eintraegen: Fehlen beim Wiederaufbau noch Eintraege, wird der gemerkte Eintrag
// angefahren, sobald es ihn gibt.
@Composable
fun RollstandHalten(liste: LazyListState) {
    val ziel = remember(liste) { liste.firstVisibleItemIndex to liste.firstVisibleItemScrollOffset }
    LaunchedEffect(liste) {
        if (ziel.first <= 0 && ziel.second <= 0) return@LaunchedEffect
        withTimeoutOrNull(Rollstand.NACHHOLEN_MS) {
            val rollt = snapshotFlow { liste.isScrollInProgress to Rollstand.eintragErreichbar(ziel.first, liste.layoutInfo.totalItemsCount) }
                .first { it.first || it.second }.first
            if (Rollstand.listeNachholen(ziel.first, ziel.second, liste.firstVisibleItemIndex, liste.firstVisibleItemScrollOffset, liste.layoutInfo.totalItemsCount, rollt)) {
                liste.scrollToItem(ziel.first, ziel.second)
            }
        }
    }
}

// Das wiederkehrende Blatt, in dem ein Inhalt liegt (WiederkehrBlatt); null ausserhalb.
val LocalBlattRueck = staticCompositionLocalOf<Wiederkehr?> { null }

// Zustand eines Blatts in einem WiederkehrBlatt: Beim Wiederkommen steht es sofort offen, sonst gleitet es herein.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun blattZustandHier(): SheetState = blattZustand(LocalBlattRueck.current?.sofort == true)

// Ein Blatt, aus dem sich ein Wertpapier oder eine Unterseite oeffnen laesst: Vor dem Oeffnen w.verlassen() rufen. Das
// Blatt schliesst sich dann, wartet unter der Seite und steht nach dem Zurueck wieder da, mit Rollstand, Reiter und
// aufgeklappten Abschnitten (alles aus rememberSaveable im Blatt). Wird das Blatt wirklich geschlossen, verlaesst der
// Aufrufer diesen Baustein; der gemerkte Zustand endet damit.
// Liegt das Blatt selbst in einem wiederkehrenden Blatt (Blatt ueber Blatt), geht und kommt es mit diesem: beide sind
// eigene Fenster und muessen der Seite weichen. Sein Zustand liegt dann beim aeusseren Blatt; der Aufrufer merkt sich
// mit rememberSaveable, dass es offen ist.
@Composable
fun WiederkehrBlatt(schluessel: Any, w: Wiederkehr? = null, inhalt: @Composable (Wiederkehr) -> Unit) {
    val aussen = LocalBlattRueck.current
    if (w == null && aussen != null) {
        inhalt(aussen)
        return
    }
    val eigen = w ?: rememberWiederkehr()
    val halter = rememberSaveableStateHolder()
    if (!eigen.wartet) halter.SaveableStateProvider(schluessel) {
        CompositionLocalProvider(LocalBlattRueck provides eigen) { inhalt(eigen) }
    }
    DisposableEffect(schluessel) { onDispose { halter.removeState(schluessel) } }
}
