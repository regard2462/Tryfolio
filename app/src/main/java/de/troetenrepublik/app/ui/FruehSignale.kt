package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.Fruehindikatoren.Signal
import de.troetenrepublik.app.engine.Tendenz
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs

// Lager und Wirtschaftsdaten aus oeffentlichen Quellen: als Abschnitt im Reiter "Ausblick" der Maerkte
// und als kleine Karte auf der Wertpapierseite betroffener Werte (Oel, Gas, Getreide, Fluglinien, Oel- und
// Gasfirmen). Jeder Eintrag nutzt die volle Breite: Name mit Wert, Satz, Tendenz, bei Bedarf Balken mit Legende.
// Die Grenzen solcher Daten stehen im Info-Blatt, nicht als Textwand auf der Seite. Unter jedem Signal steht
// "Im Preis?" (ui/ImPreis.kt): die Schaetzung aus der Geschichte oder die Bewegung seit der Meldung.

// Seiten der Quellen fuer Menschen (die Abrufe selbst stehen in engine/Fruehindikatoren.kt).
private fun fiSeite(k: String): String = when (k) {
    Fruehindikatoren.GAS_US -> "https://ir.eia.gov/ngs/ngs.html"
    Fruehindikatoren.GAS_EU -> "https://agsi.gie.eu"
    Fruehindikatoren.ERSTANTRAEGE -> "https://fred.stlouisfed.org/series/ICSA"
    Fruehindikatoren.FLUGGAESTE -> "https://www.tsa.gov/travel/passenger-volumes"
    Fruehindikatoren.DUERRE -> "https://droughtmonitor.unl.edu"
    else -> "https://www.eia.gov/petroleum/supply/weekly/"
}

// Name links, Wert rechtsbuendig in derselben Zeile; passt beides nicht nebeneinander, steht der Wert unter dem
// Namen. Der Wert bricht nie um und bleibt in voller Schriftgroesse.
@Composable
internal fun NameUndWert(name: String, wert: String, modifier: Modifier = Modifier) {
    val stil = MaterialTheme.typography.titleSmall
    if (wert.isEmpty()) {
        Text(name, modifier, style = stil)
        return
    }
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val breite = maxWidth
        val nebeneinander = remember(name, wert, breite, stil) {
            val n = messer.measure(name, stil, softWrap = false, maxLines = 1).size.width
            val w = messer.measure(wert, stil, softWrap = false, maxLines = 1).size.width
            n + with(dichte) { 12.dp.toPx() } + w <= with(dichte) { breite.toPx() }
        }
        if (nebeneinander) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = stil)
                Spacer(Modifier.width(12.dp))
                Text(wert, style = stil, maxLines = 1, softWrap = false)
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Text(name, style = stil)
                Text(wert, style = stil, maxLines = 1, softWrap = false)
            }
        }
    }
}

// Abschnitt im Reiter "Ausblick": je Signal ein Eintrag mit Name, Wert, Satz und Tendenz; ein Tipp zeigt Balken,
// Erklaerung und Quelle. Die EU-Gasspeicher erscheinen nur mit eigenem kostenlosem Zugang, sonst als Hinweis darauf.
@Composable
internal fun FruehSignaleAbschnitt(state: AppState, oeffnen: (String) -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var signale by remember { mutableStateOf<Map<String, Signal>?>(null) }
    LaunchedEffect(settings.keyAgsi) {
        signale = runCatching { state.fruehSignale(Fruehindikatoren.AUSBLICK) }.getOrNull().orEmpty()
    }
    // Im Preis? erst nach den Signalen, damit die Lager schon geladen sind
    var preis by remember { mutableStateOf<Map<String, Fruehindikatoren.ImPreis>>(emptyMap()) }
    var preisGeladen by remember { mutableStateOf(false) }
    LaunchedEffect(settings.keyAgsi, signale != null) {
        if (signale != null) {
            preis = runCatching { state.fruehImPreis(Fruehindikatoren.AUSBLICK) }.getOrNull().orEmpty()
            preisGeladen = true
        }
    }
    val s = signale
    Column(Modifier.fillMaxWidth()) {
        MarktInfoKopf(S.fiTitel, S.fiInfo)
        when {
            s == null -> EmptyHint(S.trLaedt)
            s.isEmpty() -> EmptyHint(S.heatmapLeer)
            else -> {
                var offen by remember { mutableStateOf<String?>(null) }
                for (k in Fruehindikatoren.AUSBLICK) {
                    val x = s[k]
                    when {
                        x != null -> {
                            FruehZeile(x, offen == k, mitBalken = false) { offen = if (offen == k) null else k }
                            val ip = preis[k]
                            if (ip != null) ImPreisZeile(ip, x.lage, state = state, oeffnen = oeffnen)
                            else if (preisGeladen) SeitMeldungZeile(state, x, getreide = null)
                        }
                        k == Fruehindikatoren.GAS_EU && settings.keyAgsi.isBlank() -> FruehOhneZugang { oeffnen("zugaenge") }
                    }
                }
                QuellenZeile(S.fiQuellen)
                // Lager der Metallboersen nur im Browser
                MetallLagerLinks()
            }
        }
    }
}

// Karte auf der Wertpapierseite: nur, wenn es zum Wert ein Signal gibt. Bei Oel stehen alle Lager mit Balken offen,
// darunter die staatliche Reserve als Satz.
@Composable
internal fun FruehSignalKarte(state: AppState, symbol: String, branche: String, onOpenRoute: (String) -> Unit) {
    val schluessel = remember(symbol, branche) { Fruehindikatoren.zuWert(de.troetenrepublik.app.engine.Rohstoffe.schluessel(symbol), branche, symbol) }
    if (schluessel.isEmpty()) return
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val oel = schluessel == Fruehindikatoren.OEL
    var signale by remember(schluessel) { mutableStateOf<Map<String, Signal>?>(null) }
    LaunchedEffect(schluessel, settings.keyAgsi) {
        signale = runCatching { state.fruehSignale(if (oel) schluessel + Fruehindikatoren.SPR else schluessel) }.getOrNull().orEmpty()
    }
    var preis by remember(schluessel) { mutableStateOf<Map<String, Fruehindikatoren.ImPreis>>(emptyMap()) }
    var preisGeladen by remember(schluessel) { mutableStateOf(false) }
    LaunchedEffect(schluessel, settings.keyAgsi, signale != null) {
        if (signale != null) {
            preis = runCatching { state.fruehImPreis(schluessel, getreide = symbol) }.getOrNull().orEmpty()
            preisGeladen = true
        }
    }
    val s = signale ?: return
    val ohneZugang = Fruehindikatoren.GAS_EU in schluessel && settings.keyAgsi.isBlank()
    if (schluessel.none { it in s } && !ohneZugang) return
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth()) {
        // Bei Rohstoffen die Lager selbst, bei Aktien ein Signal fuer ihre Branche
        MarktInfoKopf(if (de.troetenrepublik.app.engine.Rohstoffe.schluessel(symbol) != null) S.fiTitel else S.fiBrancheTitel, S.fiInfo)
        var offen by remember { mutableStateOf<String?>(null) }
        for (k in schluessel) {
            val x = s[k]
            when {
                x != null -> {
                    FruehZeile(x, offen == k, mitBalken = true) { offen = if (offen == k) null else k }
                    val ip = preis[k]
                    if (ip != null) ImPreisZeile(ip, x.lage, state = state, oeffnen = onOpenRoute)
                    else if (preisGeladen) SeitMeldungZeile(state, x, getreide = symbol)
                }
                k == Fruehindikatoren.GAS_EU && ohneZugang -> FruehOhneZugang { onOpenRoute("zugaenge") }
            }
        }
        if (oel) s[Fruehindikatoren.SPR]?.let { r ->
            Text(
                S.fiSpr(fiWert(r), r.aenderung?.let { fiAenderung(r.schluessel, it) }), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Text(S.fiDeutung(Fruehindikatoren.SPR), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp))
        }
        val erste = schluessel.firstOrNull { it in s } ?: schluessel.first()
        QuellenZeile(S.fiQuelle(erste), onClick = { runCatching { uri.openUri(fiSeite(erste)) } })
    }
}

// Ein Eintrag ueber die volle Breite: Name mit Wert, Satz und Tendenz. mitBalken: der Balken mit Enden und Legende
// steht immer offen (Wertpapierseite), sonst erst nach dem Tipp. Aufgeklappt: Stichtag (ohne Balken), Erklaerung
// und Quelle.
@Composable
private fun FruehZeile(x: Signal, auf: Boolean, mitBalken: Boolean, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val uri = LocalUriHandler.current
    val k = x.schluessel
    val tendenz = Tendenz.fruehsignal(x)
    val signal = LocalSignal.current
    val punkt = when (tendenz.richtung) {
        Tendenz.Richtung.BULLISH -> signal.up
        Tendenz.Richtung.BEARISH -> signal.down
        Tendenz.Richtung.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        NameUndWert(S.fiName(k), fiWert(x))
        Text(fiSatz(x), style = MaterialTheme.typography.bodySmall, color = grau)
        TendenzZeile(tendenz, Modifier.padding(top = 2.dp))
        val b = x.band
        if (b != null && (mitBalken || auf)) {
            val f = balkenFarben()
            BandBalken(x.wert, b, punkt, Modifier.padding(top = 8.dp).fillMaxWidth().height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(S.fiRichtung(k, false), style = MaterialTheme.typography.labelSmall, color = grau)
                Spacer(Modifier.weight(1f))
                Text(S.fiRichtung(k, true), style = MaterialTheme.typography.labelSmall, color = grau)
            }
            LegendeZeile(zeichen = {
                val h = 7.dp.toPx()
                drawRoundRect(f.bereich, Offset(0f, (size.height - h) / 2), Size(size.width, h), CornerRadius(h / 2))
            }) {
                Text(
                    S.fiBereich(fiZahl(k, b.min), fiZahl(k, b.max), b.jahre),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = grau,
                )
            }
            LegendeZeile(zeichen = {
                drawRect(f.strich, Offset(size.width / 2 - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
            }) {
                Text(
                    S.fiLegendeSchnitt(fiZahl(k, b.schnitt)),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = grau,
                )
            }
            LegendeZeile(zeichen = {
                val mitte = Offset(size.width / 2, size.height / 2)
                drawCircle(f.rand, 5.dp.toPx(), mitte)
                drawCircle(punkt, 3.dp.toPx(), mitte)
            }) {
                PunktText(
                    listOfNotNull(
                        S.fiLegendeStand(Fmt.date(x.stand * 86_400_000L + 43_200_000L), fiZahl(k, x.wert)),
                        x.aenderung?.let { S.fiWoche(fiAenderung(k, it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.weight(1f),
                )
            }
        }
        if (auf) {
            if (b == null) {
                val zeile = listOfNotNull(
                    x.aenderung?.let { S.fiWoche(fiAenderung(k, it)) },
                    S.fiStand(Fmt.date(x.stand * 86_400_000L + 43_200_000L)),
                )
                PunktText(zeile.joinToString(" · "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp), color = Color.Unspecified)
            }
            Text(S.fiErklaerung(k), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 4.dp))
            // QuellenZeile hat innen 20.dp Rand, so steht sie buendig mit dem Text
            QuellenZeile(S.fiQuelle(k), Modifier.offset(x = (-20).dp), onClick = { runCatching { uri.openUri(fiSeite(k)) } })
        }
    }
}

// Eine Legendenzeile: kleines gezeichnetes Zeichen links, Text rechts daneben.
@Composable
private fun LegendeZeile(zeichen: DrawScope.() -> Unit, inhalt: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(16.dp, 14.dp)) { zeichen() }
        Spacer(Modifier.width(6.dp))
        inhalt()
    }
}

// Hinweis bei fehlendem kostenlosem Zugang; ein Tipp oeffnet die Zugaenge.
@Composable
private fun FruehOhneZugang(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(S.fiName(Fruehindikatoren.GAS_EU), style = MaterialTheme.typography.titleSmall)
            Text(S.fiOhneZugang, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link)
        }
        ChevronRight(height = 12.dp)
    }
}

// Farben von Balken und Legende, damit beide gleich aussehen.
private class BalkenFarben(val bahn: Color, val bereich: Color, val strich: Color, val rand: Color)

@Composable
private fun balkenFarben() = BalkenFarben(
    bahn = MaterialTheme.colorScheme.surfaceContainerHighest,
    bereich = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    strich = MaterialTheme.colorScheme.onSurface,
    rand = MaterialTheme.colorScheme.surface,
)

// Fuenfjahresbereich als Bahn: heller Bereich von Tief bis Hoch der Vorjahre, Strich beim Schnitt, Punkt beim Stand
// in der Farbe der Tendenz.
@Composable
private fun BandBalken(wert: Double, b: Fruehindikatoren.Band, farbe: Color, modifier: Modifier) {
    val f = balkenFarben()
    Canvas(modifier) {
        val spanne = (b.max - b.min).coerceAtLeast(abs(b.schnitt) * 0.02)
        val lo = minOf(b.min, wert) - spanne * 0.25
        val hi = maxOf(b.max, wert) + spanne * 0.25
        fun x(v: Double) = (size.width * ((v - lo) / (hi - lo))).toFloat()
        val h = size.height * 0.5f
        val oben = (size.height - h) / 2
        drawRoundRect(f.bahn, Offset(0f, oben), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(f.bereich, Offset(x(b.min), oben), Size((x(b.max) - x(b.min)).coerceAtLeast(2f), h), CornerRadius(h / 2))
        drawRect(f.strich, Offset(x(b.schnitt) - 1.dp.toPx(), oben - 2.dp.toPx()), Size(2.dp.toPx(), h + 4.dp.toPx()))
        val r = size.height / 2
        drawCircle(f.rand, r, Offset(x(wert).coerceIn(r, size.width - r), size.height / 2))
        drawCircle(farbe, r - 2.dp.toPx(), Offset(x(wert).coerceIn(r, size.width - r), size.height / 2))
    }
}

// Satz zum Signal in der Sprache der Oberflaeche.
private fun fiSatz(x: Signal): String {
    val a = x.abweichung ?: 0.0
    return when (x.schluessel) {
        Fruehindikatoren.ERSTANTRAEGE -> if (a < 2) S.fiAntraegeTief else S.fiAntraegeSatz(Fmt.num(a, 0) + " %", x.ampel.ordinal)
        Fruehindikatoren.FLUGGAESTE -> S.fiFlugSatz(Fmt.num(abs(a), 1) + " %", a >= 0)
        Fruehindikatoren.DUERRE -> S.fiDuerreSatz(Fmt.num(abs(a), 0), a >= 0, x.lage == Fruehindikatoren.Lage.DARUEBER, x.band?.jahre ?: 5)
        else -> {
            val betrag = if (x.schluessel == Fruehindikatoren.GAS_EU) Fmt.num(abs(a), 0) + " " + S.fiPunkte
            else Fmt.num(abs(a), if (abs(a) < 10) 1 else 0) + " %"
            S.fiLagerSatz(x.lage?.ordinal ?: 2, betrag, a < 0, x.band?.jahre ?: 5)
        }
    }
}

// Zahl in der Einheit des Signals (ohne Einheit fuer den Bereich unter dem Balken).
private fun fiZahl(k: String, v: Double): String = when (k) {
    Fruehindikatoren.GAS_US, Fruehindikatoren.ERSTANTRAEGE -> Fmt.num(v, 0)
    Fruehindikatoren.GAS_EU, Fruehindikatoren.DUERRE -> Fmt.num(v, 0) + " %"
    Fruehindikatoren.FLUGGAESTE -> Fmt.num(v / 1e6, 2)
    else -> Fmt.num(v / 1_000, 1)
}

// Stand mit Einheit.
internal fun fiWert(x: Signal): String = when (x.schluessel) {
    Fruehindikatoren.GAS_US -> S.fiMrdKubikfuss(Fmt.num(x.wert, 0))
    Fruehindikatoren.GAS_EU -> Fmt.num(x.wert, 1) + " %"
    Fruehindikatoren.DUERRE -> Fmt.num(x.wert, 0) + " %"
    Fruehindikatoren.ERSTANTRAEGE -> Fmt.num(x.wert, 0)
    Fruehindikatoren.FLUGGAESTE -> S.fiMioTag(Fmt.num(x.wert / 1e6, 2))
    else -> S.fiMioBarrel(Fmt.num(x.wert / 1_000, 1))
}

// Aenderung zur Vorwoche mit Vorzeichen und Einheit.
private fun fiAenderung(k: String, d: Double): String {
    val z = if (d > 0) "+" else if (d < 0) "−" else "±"
    return z + when (k) {
        Fruehindikatoren.GAS_US -> S.fiMrdKubikfuss(Fmt.num(abs(d), 0))
        Fruehindikatoren.GAS_EU -> Fmt.num(abs(d), 1) + " " + S.fiPunkte
        Fruehindikatoren.DUERRE -> Fmt.num(abs(d), 1) + " " + S.fiPunkte
        Fruehindikatoren.ERSTANTRAEGE -> Fmt.num(abs(d), 0)
        else -> S.fiMioBarrel(Fmt.num(abs(d) / 1_000, 1))
    }
}
