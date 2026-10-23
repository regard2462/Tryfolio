package de.troetenrepublik.app.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.mausWischen
import de.troetenrepublik.app.plattform.ZurueckTaste
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.unit.lerp as lerpDp

// Kurzanleitung: zuerst ein Ueberblick, dann Bilder, in denen ein gezeichneter Finger die Bedienung vormacht.
// Erscheint einmal nach der Einrichtung und laesst sich im Profil wieder aufrufen.
// systemInsets: die Seite liegt ausserhalb des Scaffolds und haelt die Systemleisten selbst frei.
@Composable
fun GuideScreen(
    initial: String,
    systemInsets: Boolean = false,
    kachelRaster: Boolean = false,
    onKachelRaster: (Boolean) -> Unit = {},
    // Fuer den Knopf, der alle Meldungen vorab laedt; ohne ihn fehlt der Knopf.
    state: de.troetenrepublik.app.AppState? = null,
    onDone: () -> Unit,
) {
    val pages = if (Plattform.desktop) S.guidePagesDesktop else S.guidePages
    // Seite 0 ist der Ueberblick, danach die Bilder
    val pager = rememberPagerState(initialPage = 0) { pages.size + 1 }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.size
    val letter = initial.take(1).ifBlank { "T" }.uppercase()

    ZurueckTaste {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (systemInsets) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.guideTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (!last) {
                Text(
                    S.guideSkip,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(Radius.chip)).clickable(onClick = onDone).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.weight(1f).fillMaxWidth().mausWischen(pager), verticalAlignment = Alignment.Top) { seite ->
            if (seite == 0) {
                Ueberblick(kachelRaster, state)
                return@HorizontalPager
            }
            val page = seite - 1
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                // Echtes Bildschirmfoto der App, sonst die gezeichnete Bedienung
                val foto = GUIDE_FOTOS.getOrNull(page)?.let { if (page == 0 && kachelRaster) "start_raster" else it }
                AnleitungsFoto(foto, Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.66f)) {
                    if (hatZeichnung(page)) GuideFrame { Illustration(page, active = pager.currentPage == seite, letter = letter, kachelRaster = kachelRaster) }
                }
                Text(pages[page].first, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 26.dp))
                Text(
                    pages[page].second,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
                )
                if (page == 0) {
                    // Wahl der Kachelansicht: eine Reihe zum Wischen oder zwei je Reihe untereinander
                    Text(S.kachelnTitel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                    SegmentRow(listOf(S.kachelnWischen, S.kachelnRaster), selected = if (kachelRaster) 1 else 0) { onKachelRaster(it == 1) }
                    Text(S.kachelnHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size + 1) { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (on) 18.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
            }
        }
        FloatingPill(
            label = if (last) S.letsGo else S.next,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
    }
}

// Erste Seite: alles, was die App kann, nach Bereichen.
@Composable
private fun Ueberblick(kachelRaster: Boolean, state: de.troetenrepublik.app.AppState?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Text(S.guideAllesTitel, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            S.guideAllesText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
        )
        if (state != null && state.copy != null) MeldungenVorab(state)
        S.guideFunktionen(widgets = de.troetenrepublik.app.plattform.Widgets.verfuegbar, bildschutz = de.troetenrepublik.app.plattform.bildschutzMoeglich).forEachIndexed { i, (titel, text) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(titel, style = MaterialTheme.typography.titleSmall)
                    // Ganz ausgeklappt: der Ueberblick ist zum Lesen da
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    val foto = UEBERBLICK_FOTOS.getOrNull(i)?.let { if (i == 0 && kachelRaster) "start_raster" else it }
                    AnleitungsFoto(foto, Modifier.padding(top = 10.dp).fillMaxWidth(0.72f)) {}
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// Karte zum Vorabladen aller Meldungen des Repraesentantenhauses, mit Fortschritt.
@Composable
private fun MeldungenVorab(state: de.troetenrepublik.app.AppState) {
    val stand by state.meldungenStand.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        Text(S.guideMeldungenTitel, style = MaterialTheme.typography.titleSmall)
        Text(S.guideMeldungenText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        val s = stand
        when {
            s == null -> FloatingPill(S.guideMeldungenLaden, modifier = Modifier.fillMaxWidth()) { state.alleMeldungenStarten() }
            s.fertig -> Text(S.guideMeldungenFertig(s.gesamt), style = MaterialTheme.typography.bodyMedium)
            else -> Text(S.guideMeldungenLaedt(s.gelesen, s.gesamt), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Echte Bildschirmfotos

// Fotos zu den Bildseiten der Kurzanleitung, in der Reihenfolge von Strings.guidePages.
private val GUIDE_FOTOS = listOf(
    "start_wischen", "suche", "kaufen", "indikatoren", "aktie_werkzeuge", "behoerden", "heatmap", "trends", "analytics",
    "copytrading", "rangliste", "grosse_wetten", "zugaenge", "profil",
)

// Fotos zu den Abschnitten des Ueberblicks, in der Reihenfolge von de.troetenrepublik.app.i18n.Strings.guideFunktionen; Widgets ohne Foto.
private val UEBERBLICK_FOTOS = listOf(
    "start_wischen", "sparplaene", "entdecken", "aktie", "indikatoren", "aktie_werkzeuge", "behoerden", "heatmap", "trends",
    "analytics", "copytrading", "rangliste", "prognose", "news", "rueckblick", "zugaenge", "broker", "sicherheit",
)

// Seitenverhaeltnis der Fotos (Telefon mit 1080 x 2400 Pixeln).
private const val FOTO_SEITE = 0.45f

// Bildschirmfoto der Android-App aus den mitgelieferten Dateien (assets/anleitung/<sprache>/<name>.webp, erzeugt vom
// Ablauf „Anleitungsbilder“ im Emulator). Auf Desktop und im Browser, ohne name oder wenn die Datei fehlt, steht
// stattdessen ersatz.
@Composable
private fun AnleitungsFoto(name: String?, modifier: Modifier = Modifier, ersatz: @Composable () -> Unit) {
    var fehlt by remember(name) { mutableStateOf(name == null || !Plattform.webAnsicht || Plattform.desktop) }
    if (fehlt || name == null) { ersatz(); return }
    val sprache = if (I18n.lang == de.troetenrepublik.app.data.Lang.EN) "en" else "de"
    val form = RoundedCornerShape(18.dp)
    Box(
        modifier.aspectRatio(FOTO_SEITE).clip(form).background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, form).clearAndSetSemantics { },
    ) {
        de.troetenrepublik.app.plattform.LogoBild(
            "asset:anleitung/$sprache/$name.webp", null, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Crop,
            onSuccess = {}, onError = { fehlt = true },
        )
    }
}

// Rahmen und Zeit

// Der „Bildschirm“ im Bild: schwarz wie die App, mit feinem Rand.
@Composable
private fun GuideFrame(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(330.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
            // Nur Bild: Titel und Text darunter sagen dasselbe, Bildschirmleser ueberspringen es
            .clearAndSetSemantics { },
    ) { content() }
}

@Composable
private fun Illustration(page: Int, active: Boolean, letter: String, kachelRaster: Boolean = false) {
    when (GUIDE_FOTOS.getOrNull(page)) {
        "start_wischen" -> BildPortfolio(active, letter, kachelRaster)
        "suche" -> BildSuche(active)
        "kaufen" -> BildKaufen(active)
        "profil" -> BildProfil(active, letter)
    }
}

// Seiten mit gezeichnetem Ersatzbild, wenn kein Foto da ist.
private fun hatZeichnung(page: Int) = GUIDE_FOTOS.getOrNull(page) in setOf("start_wischen", "suche", "kaufen", "profil")

// Laufende Zeit von 0 bis 1 in einer Schleife; steht still, solange das Bild nicht zu sehen ist.
@Composable
private fun loop(active: Boolean, ms: Int): Float {
    val transition = rememberInfiniteTransition(label = "anleitung")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Restart),
        label = "zeit",
    )
    return if (active) t else 0f
}

// Anteil von 0 bis 1 zwischen a und b.
private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

private fun ease(x: Float): Float = FastOutSlowInEasing.transform(x)

// 1 zwischen a und b, an den Raendern kurz ein- und ausgeblendet.
private fun sichtbar(t: Float, a: Float, b: Float): Float = minOf(seg(t, a, a + 0.04f), 1f - seg(t, b - 0.04f, b))

// Druck beim Antippen: steigt zwischen a und b an und faellt wieder.
private fun druck(t: Float, a: Float, b: Float): Float {
    val x = seg(t, a, b)
    return if (x <= 0f || x >= 1f) 0f else 1f - kotlin.math.abs(2f * x - 1f)
}

// Bausteine

@Composable
private fun Mini(text: String, size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    SchrumpfText(
        text,
        style = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = size.sp, fontWeight = weight),
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
        modifier = modifier,
    )
}

@Composable
private fun FingerDot(alpha: Float, modifier: Modifier = Modifier, press: Float = 0f) {
    if (alpha <= 0f) return
    val c = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .size(34.dp)
            .graphicsLayer {
                this.alpha = alpha
                val s = 1f - 0.2f * press
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(c.copy(alpha = 0.25f))
            .border(2.dp, c.copy(alpha = 0.85f), CircleShape),
    )
}

// Legt die Fingerspitze mittig auf ein Element.
@Composable
private fun Tippen(alpha: Float, press: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        content()
        FingerDot(alpha, press = press)
    }
}

@Composable
private fun MiniAvatar(letter: String, size: Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) { Mini(letter, (size.value * 0.4f).toInt(), FontWeight.Bold, MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun MiniLogo(letter: String, bg: Color, fg: Color, size: Dp = 30.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Mini(letter, (size.value * 0.42f).toInt(), FontWeight.Bold, fg)
    }
}

@Composable
private fun MiniPill(label: String, modifier: Modifier = Modifier, filled: Boolean = true, icon: ImageVector? = null) {
    val bg = if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (filled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(Radius.chip)).background(bg).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mini(label, 12, FontWeight.SemiBold, fg, Modifier.weight(1f))
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MiniTile(title: String, value: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Mini(title, 11, FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        if (value != null) {
            Mini(value, 14, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
        } else {
            Box(Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                PlusGlyph(size = 8.dp, color = MaterialTheme.colorScheme.onSurface, stroke = 1.6.dp)
            }
        }
    }
}

@Composable
private fun MiniRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Mini(label, 13, FontWeight.Medium, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        Mini(value, 13, FontWeight.SemiBold, valueColor)
    }
}

@Composable
private fun MiniSegment(items: List<String>, selected: Int) {
    Row(Modifier.clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp)) {
        items.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(Radius.chip))
                    .background(if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) { Mini(s, 11, FontWeight.SemiBold, if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private val KURVE = listOf(0.70f, 0.64f, 0.68f, 0.57f, 0.61f, 0.50f, 0.54f, 0.42f, 0.47f, 0.36f, 0.39f, 0.27f, 0.31f, 0.20f)

// Kursverlauf, der sich mit progress von links nach rechts aufbaut.
@Composable
private fun MiniChart(modifier: Modifier, progress: Float = 1f) {
    val color = LocalSignal.current.line
    Canvas(modifier) {
        val path = Path()
        KURVE.forEachIndexed { i, v ->
            val x = size.width * i / (KURVE.size - 1)
            val y = size.height * v
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        clipRect(right = size.width * progress) {
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// Kopf einer Wertpapierseite mit Kurs und Chart (Bilder „Kaufen“ und „Wischen“).
@Composable
private fun MiniInstrument(chart: Float) {
    val signal = LocalSignal.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniLogo("A", MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surface)
            Spacer(Modifier.width(10.dp))
            Column {
                Mini("Apple Inc.", 14, FontWeight.SemiBold, MaterialTheme.colorScheme.onSurface)
                Mini("AAPL", 11)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Mini(Fmt.euro(214.32), 22, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Mini(Fmt.signedPct(1.24), 12, FontWeight.SemiBold, signal.up, Modifier.padding(bottom = 4.dp))
        }
        MiniChart(Modifier.fillMaxWidth().height(88.dp).padding(vertical = 10.dp), chart)
    }
}

// Die Bilder

// Kacheln als Reihe zum Wischen: breiter als der Bildschirm, gleitet langsam hin und her.
@Composable
private fun MiniKachelReihe(active: Boolean) {
    val t = loop(active, 6000)
    // hin (0.1 bis 0.4), stehen, zurueck (0.6 bis 0.9)
    val weg = ease(seg(t, 0.10f, 0.40f)) - ease(seg(t, 0.60f, 0.90f))
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Row(Modifier.offset(x = (-118).dp * weg).width(560.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniTile(S.newsTile, "4", Modifier.width(104.dp))
            MiniTile(S.watchlist, "3", Modifier.width(104.dp))
            MiniTile(S.copyTile, "2", Modifier.width(104.dp))
            MiniTile(S.analytics, "5", Modifier.width(104.dp))
            MiniTile(S.savingsPlans, "1", Modifier.width(104.dp))
        }
    }
}

// Kacheln untereinander: zwei je Reihe, kompakt mit dem Wert rechts.
@Composable
private fun MiniKachelRaster() {
    @Composable
    fun Kachel(titel: String, wert: String?, m: Modifier) {
        Row(
            m.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mini(titel, 11, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (wert != null) Mini(wert, 12, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
            else PlusGlyph(size = 8.dp, color = MaterialTheme.colorScheme.onSurface, stroke = 1.6.dp)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Kachel(S.newsTile, "4", Modifier.weight(1f)); Kachel(S.watchlist, "3", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Kachel(S.copyTile, "2", Modifier.weight(1f)); Kachel(S.analytics, "5", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Kachel(S.savingsPlans, "1", Modifier.weight(1f)); Kachel(S.orders, null, Modifier.weight(1f))
        }
    }
}

// 1: Portfolio und Cash liegen nebeneinander, ein Wisch wechselt. Die Kacheln stehen wie oben gewaehlt, als Reihe
// zum Wischen oder zwei je Reihe untereinander.
@Composable
private fun BildPortfolio(active: Boolean, letter: String, kachelRaster: Boolean) {
    val t = loop(active, 4400)
    val hin = ease(seg(t, 0.14f, 0.38f))
    val zurueck = ease(seg(t, 0.62f, 0.86f))
    val pos = hin - zurueck
    val on = MaterialTheme.colorScheme.onSurface
    val off = MaterialTheme.colorScheme.onSurfaceVariant
    val signal = LocalSignal.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mini(S.portfolio, 19, FontWeight.Bold, lerpColor(on, off, pos))
                Spacer(Modifier.width(10.dp))
                Mini(S.cash, 19, FontWeight.Bold, lerpColor(off, on, pos))
                Spacer(Modifier.weight(1f))
                MiniAvatar(letter)
            }
            Spacer(Modifier.height(12.dp))
            val panel = w - 36.dp
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                Column(Modifier.width(panel).fillMaxHeight().offset(x = -panel * pos)) {
                    Mini(S.brokerage, 12)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Mini(Fmt.euro(10_482.17), 24, FontWeight.Bold, on)
                        Spacer(Modifier.width(8.dp))
                        Mini(Fmt.signedPct(4.82), 12, FontWeight.SemiBold, signal.up, Modifier.padding(bottom = 4.dp))
                    }
                    MiniChart(Modifier.fillMaxWidth().height(if (kachelRaster) 56.dp else 80.dp).padding(vertical = 10.dp))
                    if (kachelRaster) MiniKachelRaster() else MiniKachelReihe(active)
                }
                Column(Modifier.width(panel).fillMaxHeight().offset(x = panel * (1f - pos))) {
                    Mini(S.available, 12)
                    Mini(Fmt.euro(40_063.20), 24, FontWeight.Bold, on)
                    Spacer(Modifier.height(12.dp))
                    MiniRow(S.txLabel(TxType.INTEREST), Fmt.signedEuro(64.20), signal.up)
                    MiniRow(S.txLabel(TxType.BUY) + " · Apple", Fmt.euro(-10_001.00), on)
                    MiniRow(S.txLabel(TxType.DEPOSIT), Fmt.signedEuro(50_000.0), signal.up)
                }
            }
            Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Auf der Cash-Seite heisst der linke Knopf wie in der App „Investieren“
                MiniPill(if (pos < 0.5f) S.search else S.invest, Modifier.weight(1f), icon = Icons.Filled.Search)
                MiniPill(S.transfer, Modifier.weight(1f), icon = Icons.Filled.SwapVert)
            }
        }
        // Finger: nach links wischen, spaeter wieder zurueck
        val x = if (t < 0.5f) lerpDp(w * 0.78f, w * 0.22f, hin) else lerpDp(w * 0.22f, w * 0.78f, zurueck)
        val alpha = maxOf(sichtbar(t, 0.10f, 0.42f), sichtbar(t, 0.58f, 0.90f))
        FingerDot(alpha, modifier = Modifier.offset(x - 17.dp, h * 0.56f - 17.dp))
    }
}

// 2: Die Suche zeigt Treffer zu „App“, einer wird angetippt; dann von vorn: Suche oeffnen, tippen, Treffer.
@Composable
private fun BildSuche(active: Boolean) {
    // Die Schleife beginnt bei den Treffern, damit sofort Beispielaktien zu sehen sind; ohne Bewegung
    // (Seite nicht sichtbar) stehen ebenfalls die Treffer da
    val roh = loop(active, 5200)
    val t = if (active) (roh + 0.62f) % 1f else 0.62f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Mini(S.portfolio, 19, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Mini(S.brokerage, 12)
            Mini(Fmt.euro(0.0), 24, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tippen(sichtbar(t, 0.08f, 0.24f), druck(t, 0.13f, 0.21f), Modifier.weight(1f)) {
                    MiniPill(S.search, Modifier.fillMaxWidth(), icon = Icons.Filled.Search)
                }
                MiniPill(S.transfer, Modifier.weight(1f), icon = Icons.Filled.SwapVert)
            }
        }
        val rein = ease(seg(t, 0.22f, 0.34f))
        val weg = seg(t, 0.94f, 1f)
        if (rein > 0f) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = (1f - rein) * h.toPx()
                        alpha = 1f - weg
                    }
                    .background(MaterialTheme.colorScheme.background)
                    .padding(18.dp),
            ) {
                val zeichen = (seg(t, 0.36f, 0.52f) * 3.99f).toInt()
                val cursor = if ((t * 12f).toInt() % 2 == 0) 1f else 0f
                Row(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(Radius.chip))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Mini("App".take(zeichen), 14, FontWeight.Medium, MaterialTheme.colorScheme.onSurface)
                    Box(Modifier.padding(start = 1.dp).size(1.5.dp, 16.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = cursor)))
                }
                Column(Modifier.padding(top = 10.dp).graphicsLayer { alpha = seg(t, 0.48f, 0.54f) }) {
                    Tippen(sichtbar(t, 0.62f, 0.80f), druck(t, 0.66f, 0.74f), Modifier.fillMaxWidth()) {
                        Treffer("A", MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surface, "Apple Inc.", "AAPL", Fmt.euro(214.32))
                    }
                    Treffer("A", Color(0xFF2B6CB0), Color.White, "Applied Materials", "AMAT", Fmt.euro(168.10))
                    Treffer("A", Color(0xFF6B46C1), Color.White, "AppLovin", "APP", Fmt.euro(312.45))
                }
            }
        }
    }
}

@Composable
private fun Treffer(letter: String, bg: Color, fg: Color, name: String, sub: String, price: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        MiniLogo(letter, bg, fg)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Mini(name, 13, FontWeight.SemiBold, MaterialTheme.colorScheme.onSurface)
            Mini(sub, 11)
        }
        Mini(price, 13, FontWeight.Medium, MaterialTheme.colorScheme.onSurface)
    }
}

// 3: Kaufen oeffnet die Order, ein Betrag wird eingegeben.
@Composable
private fun BildKaufen(active: Boolean) {
    val t = loop(active, 5400)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            MiniInstrument(chart = seg(t, 0.0f, 0.2f).let { if (active) it else 1f })
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniPill(S.sell, Modifier.weight(1f), filled = false)
                Tippen(sichtbar(t, 0.14f, 0.30f), druck(t, 0.19f, 0.27f), Modifier.weight(1f)) {
                    MiniPill(S.buy, Modifier.fillMaxWidth())
                }
            }
        }
        val rein = ease(seg(t, 0.30f, 0.42f)) * (1f - ease(seg(t, 0.92f, 1f)))
        if (rein > 0f) {
            val betrag = listOf(0.0, 5.0, 50.0, 500.0)[(seg(t, 0.46f, 0.62f) * 3.99f).toInt()]
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(h * 0.68f)
                    .graphicsLayer { translationY = (1f - rein) * size.height }
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(32.dp, 4.dp).clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.height(10.dp))
                Mini(S.buy + " · Apple", 15, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                MiniSegment(listOf(S.amount, S.piecesLabel), 0)
                Mini(Fmt.euro(betrag, 0), 30, FontWeight.Bold, MaterialTheme.colorScheme.onSurface, Modifier.padding(vertical = 8.dp))
                MiniSegment(listOf(S.market, S.limit, S.stop), 0)
                Spacer(Modifier.weight(1f))
                Tippen(sichtbar(t, 0.70f, 0.86f), druck(t, 0.75f, 0.83f), Modifier.fillMaxWidth()) {
                    MiniPill(S.next, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun monat(i: Int): String =
    java.time.Month.of(i + 1).getDisplayName(java.time.format.TextStyle.SHORT, I18n.locale).trimEnd('.')

// 6: Die Initiale oben rechts oeffnet das Profil mit allem Weiteren; der Kreis waechst dabei an seinen Platz im Profil.
@Composable
private fun BildProfil(active: Boolean, letter: String) {
    val t = loop(active, 5000)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val rein = ease(seg(t, 0.26f, 0.38f)) * (1f - seg(t, 0.92f, 1f))
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mini(S.portfolio, 19, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(10.dp))
                Mini(S.cash, 19, FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Tippen(sichtbar(t, 0.08f, 0.26f), druck(t, 0.14f, 0.22f)) {
                    // Solange der Kreis wandert, zeichnet ihn nur die Ebene darueber
                    Box(Modifier.graphicsLayer { alpha = if (rein > 0f) 0f else 1f }) { MiniAvatar(letter) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Mini(S.brokerage, 12)
            Mini(Fmt.euro(10_482.17), 24, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
        }
        if (rein > 0f) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = (1f - rein) * w.toPx()
                        alpha = rein
                    }
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Mini(S.yourProfile, 16, FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                listOf(S.activity, S.documents, S.taxes, S.importPosition, S.guideSettings).forEachIndexed { i, titel ->
                    val hervor = if (i == 3) druck(t, 0.58f, 0.74f) else 0f
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = hervor))
                            .padding(horizontal = 4.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Mini(titel, 13, FontWeight.Medium, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                        ChevronRight(height = 10.dp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    S.aboutTitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Der Kreis waechst aus der Kopfzeile an seinen Platz im Profil (30 dp oben rechts, 40 dp oben links)
            val s = 0.75f + 0.25f * rein
            Box(
                Modifier
                    .offset(lerpDp(w - 18.dp - 30.dp, 18.dp, rein), lerpDp(18.dp, 16.dp, rein))
                    .graphicsLayer { transformOrigin = TransformOrigin(0f, 0f); scaleX = s; scaleY = s },
            ) { MiniAvatar(letter, 40.dp) }
        }
    }
}
