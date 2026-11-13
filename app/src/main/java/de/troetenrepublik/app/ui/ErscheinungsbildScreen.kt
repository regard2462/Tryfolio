package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Appearance
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.WichtigThemen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.WichtigTexte
import kotlinx.coroutines.launch

const val ERSCHEINUNGSBILD = "erscheinungsbild"

// Name des gewaehlten Designs, fuer die Zeile im Profil.
@Composable
internal fun designName(a: Appearance): String = when (a) {
    Appearance.SYSTEM -> S.appearanceSystem
    Appearance.LIGHT -> S.appearanceLight
    Appearance.DARK -> S.appearanceDark
    Appearance.SEPIA -> S.appearanceSepia
}

// Design, Logos, Trennlinien, Chartfarbe und Kategorien bei Wichtigste als Wahlkarten mit Beispielbild; jede Wahl gilt sofort.
@Composable
fun ErscheinungsbildScreen(state: AppState, onBack: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val konto by state.account.collectAsStateWithLifecycle()
    val beispielWerte = remember(konto.positions, konto.watchlist) { darstellungBeispiele(konto) }
    val scope = rememberCoroutineScope()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberScrollState()
    val signal = LocalSignal.current
    val designs = listOf(Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK, Appearance.SEPIA)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { liste.value == 0 }.verticalScroll(liste)) {
        ScreenHeader(S.appearance, null, onBack)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            WahlGruppe(S.esDesign, designs.map { designName(it) }, designs.indexOf(settings.appearance), { state.setAppearance(designs[it]) }) { i ->
                DesignVorschau(designs[i])
            }
            DarstellungWahl(
                beispielWerte = beispielWerte,
                farbig = settings.logosFarbig, rund = settings.logoRund, linien = settings.trennlinien, entfernen = settings.logoHintergrundEntfernen,
                onFarbig = { state.setLogosFarbig(it) }, onRund = { state.setLogoRund(it) },
                onLinien = { state.setTrennlinien(it) }, onEntfernen = { state.setLogoHintergrundEntfernen(it) },
            )
            WahlGruppe(S.coloredChart, listOf(S.logosFarbig, S.esNeutral), if (settings.coloredChart) 0 else 1, { i -> scope.launch { state.settings.update { it.copy(coloredChart = i == 0) } } }) { i ->
                // Dieselbe Regel wie im Theme: Kurve nach Richtung gefaerbt oder in der neutralen Linienfarbe
                KurvenVorschau(signal.copy(coloredLine = i == 0).lineFor(1.0))
            }
            WahlGruppe(S.esWichtigKategorie, listOf(S.logosFarbig, S.trennAus), if (settings.newsWichtigFarbig) 0 else 1, { state.newsWichtigFarbigSetzen(it == 0) }) { i ->
                SchlagzeileVorschau(mitEtikett = i == 0)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// Karte eines Designs: Grund, zwei Textzeilen und eine kleine Kurve in Gruen und Rot. System teilt die Karte in Hell und Dunkel.
@Composable
private fun DesignVorschau(a: Appearance) {
    Canvas(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))) {
        when (a) {
            Appearance.SYSTEM -> {
                designZeichnen(DESIGN_HELL, 0f, size.width / 2f)
                designZeichnen(DESIGN_DUNKEL, size.width / 2f, size.width / 2f)
            }
            Appearance.LIGHT -> designZeichnen(DESIGN_HELL, 0f, size.width)
            Appearance.DARK -> designZeichnen(DESIGN_DUNKEL, 0f, size.width)
            Appearance.SEPIA -> designZeichnen(DESIGN_SEPIA, 0f, size.width)
        }
    }
}

private fun DrawScope.designZeichnen(f: DesignFarben, x0: Float, breite: Float) {
    val dp = density
    val rand = 8f * dp
    drawRect(f.grund, Offset(x0, 0f), Size(breite, size.height))
    val balken = CornerRadius(2f * dp)
    drawRoundRect(f.text, Offset(x0 + rand, 8f * dp), Size(breite * 0.5f, 5f * dp), balken)
    drawRoundRect(f.text2, Offset(x0 + rand, 17f * dp), Size(breite * 0.32f, 4f * dp), balken)
    // Flaeche der Karten mit der Kurve darauf
    val karteX = x0 + 4f * dp
    val karteB = breite - 8f * dp
    drawRoundRect(f.flaeche, Offset(karteX, 25f * dp), Size(karteB, 15f * dp), CornerRadius(4f * dp))
    val links = karteX + 5f * dp
    val b = karteB - 10f * dp
    fun punkt(x: Float, y: Float) = Offset(links + b * x, 28f * dp + 9f * dp * y)
    val strich = Stroke(2f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val hoch = Path().apply {
        val p = listOf(0f to 0.9f, 0.2f to 0.55f, 0.35f to 0.7f, 0.55f to 0.1f)
        moveTo(punkt(p[0].first, p[0].second).x, punkt(p[0].first, p[0].second).y)
        p.drop(1).forEach { (x, y) -> lineTo(punkt(x, y).x, punkt(x, y).y) }
    }
    val runter = Path().apply {
        val p = listOf(0.55f to 0.1f, 0.72f to 0.45f, 0.82f to 0.35f, 1f to 0.9f)
        moveTo(punkt(p[0].first, p[0].second).x, punkt(p[0].first, p[0].second).y)
        p.drop(1).forEach { (x, y) -> lineTo(punkt(x, y).x, punkt(x, y).y) }
    }
    drawPath(hoch, f.up, style = strich)
    drawPath(runter, f.down, style = strich)
}

// Kleine steigende Kurve in der gegebenen Linienfarbe.
@Composable
private fun KurvenVorschau(farbe: Color) {
    Canvas(Modifier.fillMaxWidth().height(36.dp)) {
        val dp = density
        val punkte = listOf(0f to 0.85f, 0.14f to 0.65f, 0.28f to 0.75f, 0.45f to 0.4f, 0.6f to 0.5f, 0.8f to 0.15f, 1f to 0.05f)
        val pfad = Path()
        val rand = 4f * dp
        punkte.forEachIndexed { k, (x, y) ->
            val px = rand + (size.width - 2f * rand) * x
            val py = rand + (size.height - 2f * rand) * y
            if (k == 0) pfad.moveTo(px, py) else pfad.lineTo(px, py)
        }
        drawPath(pfad, farbe, style = Stroke(2.5f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// Eine Schlagzeilenzeile mit oder ohne farbiges Kategorie-Etikett, wie unter News, Wichtigste.
@Composable
private fun SchlagzeileVorschau(mitEtikett: Boolean) {
    val kat = WichtigThemen.Kat.MAERKTE
    val farbe = Kategorie.farbe(kat.ordinal)
    val balken = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        if (mitEtikett) {
            Text(
                WichtigTexte.kat(I18n.lang == Lang.DE, kat), style = MaterialTheme.typography.labelSmall, color = farbe, maxLines = 1, softWrap = false,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(farbe.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(balken))
        Spacer(Modifier.height(5.dp))
        Row { Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(balken)) }
    }
}
