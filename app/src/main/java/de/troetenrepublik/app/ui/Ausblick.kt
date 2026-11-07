package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Ausblickdeutung
import de.troetenrepublik.app.engine.Blasen
import de.troetenrepublik.app.engine.Branchentrends
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.Tendenz
import de.troetenrepublik.app.engine.Zinserwartung
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Markterwartung
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn

// Reiter „Ausblick“ der Seite Maerkte (seit 5.92): was der Markt erwartet (US-Leitzins aus den Fed-Funds-Futures,
// Wahrscheinlichkeiten von Polymarket zu Zinsen, Rezession, Inflation, Oel, Nahost und Wahlen), Wirtschaftstermine der
// Nasdaq (5.95), Lager und Wirtschaftsdaten aus oeffentlichen Quellen und der Blasen-Detektor. Zeigt und erklaert,
// ohne zu empfehlen.
@Composable
internal fun AusblickReiter(state: AppState, liste: LazyListState, oeffnen: (String) -> Unit) {
    // null: wird noch geladen
    var zins by remember { mutableStateOf<Zinserwartung.Erwartung?>(null) }
    var zinsGeladen by remember { mutableStateOf(false) }
    var erwartet by remember { mutableStateOf<List<AppState.Erwartet>?>(null) }
    var blasen by remember { mutableStateOf<AppState.BlasenLage?>(null) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { zins = runCatching { state.zinsErwartung() }.getOrNull(); zinsGeladen = true }
            launch { erwartet = runCatching { state.marktErwartungen() }.getOrNull().orEmpty() }
            launch { blasen = runCatching { state.blasen() }.getOrNull() ?: AppState.BlasenLage(emptyList(), emptyList(), null, 0L) }
        }
    }
    val uri = appLinks()

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        // Kurzaussage aus Zinserwartung und Rezessionswahrscheinlichkeit von Polymarket
        val rezChance = erwartet?.firstOrNull { it.thema.schluessel == "rezession" }
            ?.let { Markterwartung.ausgaenge(it.ereignis).singleOrNull()?.second }
        val kern = if (zinsGeladen && erwartet != null)
            S.meKern(Ausblickdeutung.zinsRichtung(zins?.schritte), Ausblickdeutung.rezessionStufe(rezChance)) else ""
        if (kern.isNotEmpty()) item(key = "kern") { KernKarte(kern) }
        // Was die Zahlen bedeuten (Wirkungskette), ui/WirkungsKarten.kt
        item(key = "wirkung") { WirkungAbschnitt(state) }
        item(key = "erwartet-kopf") { MarktInfoKopf(S.meTitel, S.meInfo) }
        item(key = "zins") {
            when {
                !zinsGeladen -> EmptyHint(S.trLaedt)
                zins != null -> ZinsKarte(zins!!)
            }
        }
        val e = erwartet
        when {
            e == null -> item(key = "erwartet-laedt") { EmptyHint(S.trLaedt) }
            e.isEmpty() -> item(key = "erwartet-leer") { EmptyHint(S.heatmapLeer) }
            else -> {
                items(e, key = { "e-" + it.thema.schluessel }) { x ->
                    ErwartungZeile(x, zins?.schritte) { oeffnen(PmRouten.ereignis(x.ereignis.quelle, x.ereignis.id)) }
                }
                item(key = "erwartet-quelle") { QuellenZeile(S.meQuelle, onClick = { runCatching { uri.openUri("https://polymarket.com") } }) }
            }
        }

        // Wirtschaftstermine des naechsten Handelstags aus dem Kalender der Nasdaq (5.95), ui/NasdaqSeiten.kt
        item(key = "wirtschaft") { WirtschaftAbschnitt(state, oeffnen) }
        // Fruehe Signale aus oeffentlichen Daten (Lager, Erstantraege, Fluggaeste, Duerre), ui/FruehSignale.kt
        item(key = "fruehsignale") { FruehSignaleAbschnitt(state, oeffnen) }
        // Fracht, Lieferketten und Halbleiter aus offenen Ersatzquellen, ui/ErsatzSignale.kt
        item(key = "ersatzsignale") { ErsatzSignaleAbschnitt(state, oeffnen) }

        item(key = "blasen-kopf") { MarktInfoKopf(S.blTitel, S.blInfo) }
        val b = blasen
        when {
            b == null -> item(key = "blasen-laedt") { EmptyHint(S.trLaedt) }
            b.indikatoren.isEmpty() -> item(key = "blasen-leer") { EmptyHint(S.heatmapLeer) }
            else -> item(key = "blasen") { BlasenInhalt(b) }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Kurzaussage ganz oben im Ausblick: ein Satz, was Zinserwartung und Rezessionswahrscheinlichkeit zusammen bedeuten.
@Composable
private fun KernKarte(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        Text(S.meKernTitel, style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

// Ueberschrift eines Abschnitts mit Info-Symbol, wenn es eine Erklaerung gibt; ein Tipp darauf zeigt die Erklaerung in einem Blatt.
@Composable
internal fun MarktInfoKopf(titel: String, info: String?) {
    var offen by remember { mutableStateOf(false) }
    Row(
        // Mindesthoehe wie mit Info-Symbol, sonst springt die Ueberschrift
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).padding(top = 20.dp, bottom = 4.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (info != null) IconButton(onClick = { offen = true }) {
            Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
    if (offen && info != null) MarktInfoBlatt(titel, info) { offen = false }
}

// Erklaerung als Blatt: Absaetze und Stichpunkte wie im Text.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarktInfoBlatt(titel: String, text: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(titel, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            StichpunktText(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// US-Leitzins: heute und erwartet in drei, sechs und zwoelf Monaten, ein Satz und der Verlauf als Stufen.
@Composable
private fun ZinsKarte(z: Zinserwartung.Erwartung) {
    val signal = LocalSignal.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        Text(S.zeTitel, style = MaterialTheme.typography.titleSmall)
        Text(S.zeSatz(z.schritte), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ZinsSpalte(S.zeHeute, z.heute.zins, null)
            z.in3?.let { ZinsSpalte(S.zeIn(3), it.zins, it.zins - z.heute.zins) }
            z.in6?.let { ZinsSpalte(S.zeIn(6), it.zins, it.zins - z.heute.zins) }
            z.in12?.let { ZinsSpalte(S.zeIn(12), it.zins, it.zins - z.heute.zins) }
        }
        Spacer(Modifier.height(8.dp))
        ZinsStufen(z.pfad, signal.up, signal.down)
        TendenzZeile(Tendenz.ausZins(Ausblickdeutung.zinsRichtung(z.schritte)), Modifier.padding(top = 10.dp))
    }
    QuellenZeile(S.zeQuelle)
}

@Composable
private fun ZinsSpalte(titel: String, zins: Double, aenderung: Double?) {
    Column {
        Text(titel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Fmt.num(zins, 2) + " %", style = MaterialTheme.typography.titleSmall)
        aenderung?.let {
            Text(
                (if (it >= 0.005) "+" else if (it <= -0.005) "−" else "±") + Fmt.num(kotlin.math.abs(it), 2),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Erwarteter Satz je Monat als Balken, beschriftet mit dem Monat; erste Spalte heute.
@Composable
private fun ZinsStufen(pfad: List<Zinserwartung.Monat>, hoch: androidx.compose.ui.graphics.Color, runter: androidx.compose.ui.graphics.Color) {
    if (pfad.size < 2) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val schrift = MaterialTheme.colorScheme.onSurfaceVariant
    val messer = androidx.compose.ui.text.rememberTextMeasurer()
    val klein = MaterialTheme.typography.labelSmall
    val lo = pfad.minOf { it.zins } - 0.25
    val hi = pfad.maxOf { it.zins } + 0.05
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val unten = 14.dp.toPx()
        val flaeche = size.height - unten
        val platz = size.width / pfad.size
        val breite = (platz * 0.6f).coerceAtMost(14.dp.toPx())
        pfad.forEachIndexed { i, m ->
            val h = (flaeche * ((m.zins - lo) / (hi - lo))).toFloat().coerceAtLeast(2.dp.toPx())
            val farbe = when {
                i == 0 -> grau
                m.zins > pfad[0].zins + 0.05 -> hoch
                m.zins < pfad[0].zins - 0.05 -> runter
                else -> grau
            }
            val mitte = platz * i + platz / 2
            drawRoundRect(farbe, Offset(mitte - breite / 2, flaeche - h), Size(breite, h), CornerRadius(2.dp.toPx()))
            // Jeden dritten Monat beschriften
            if (i % 3 == 0) {
                val t = java.time.YearMonth.of(m.jahr, m.monat).format(java.time.format.DateTimeFormatter.ofPattern("MMM", I18n.locale)).removeSuffix(".")
                val g = messer.measure(t, klein.copy(color = schrift))
                drawText(g, topLeft = Offset((mitte - g.size.width / 2).coerceIn(0f, size.width - g.size.width), flaeche + 1.dp.toPx()))
            }
        }
    }
}

// Ein Thema als abgegrenzte Karte: Ueberschrift, Frage bei Polymarket, bis zu drei Ausgaenge mit Balken und eine
// Zeile, was ein Eintreten fuer die Maerkte hiesse. Ein Tipp oeffnet das Ereignis.
@Composable
private fun ErwartungZeile(x: AppState.Erwartet, zinsSchritte: Double?, onClick: () -> Unit) {
    val ausgaenge = remember(x.ereignis) { Markterwartung.ausgaenge(x.ereignis) }
    val tendenz = remember(x.ereignis, zinsSchritte) { Tendenz.erwartung(x.thema.schluessel, Markterwartung.ausgaenge(x.ereignis, 50), zinsSchritte) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.meThema(x.thema.schluessel), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            ChevronRight(height = 12.dp)
        }
        AuslaufText(
            x.ereignis.titel, maxLines = 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            flaeche = MaterialTheme.colorScheme.surfaceContainer,
        )
        for ((name, p) in ausgaenge) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AuslaufText(name, maxLines = 1, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), flaeche = MaterialTheme.colorScheme.surfaceContainer)
                Spacer(Modifier.width(8.dp))
                MarktAnteilBalken(p, Modifier.width(72.dp).height(6.dp))
                Spacer(Modifier.width(8.dp))
                Text(Fmt.num(p * 100, 0) + " %", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        TendenzZeile(tendenz, Modifier.padding(top = 10.dp))
        val falls = S.meFalls(x.thema.schluessel)
        if (falls.isNotEmpty()) Text(falls, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
    }
}

// Tendenz eines Eintrags (5.93): „Bullish fuer Aktien“ in Gruen, „Bearish …“ in Rot, „Neutral …“ in Grau.
@Composable
internal fun TendenzZeile(t: Tendenz.Lage, modifier: Modifier = Modifier) {
    val signal = LocalSignal.current
    val farbe = when (t.richtung) {
        Tendenz.Richtung.BULLISH -> signal.up
        Tendenz.Richtung.BEARISH -> signal.down
        Tendenz.Richtung.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(S.tdText(t.richtung.ordinal, t.ziel), style = MaterialTheme.typography.labelMedium, color = farbe, modifier = modifier)
}

// Duenner Balken von 0 bis 1.
@Composable
internal fun MarktAnteilBalken(anteil: Double, modifier: Modifier) {
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val voll = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(bahn, cornerRadius = r)
        val w = (size.width * anteil.coerceIn(0.0, 1.0)).toFloat()
        if (w > 0) drawRoundRect(voll, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

// Blasen-Detektor: Gesamtbild, dann was dafuer und was dagegen spricht, dann die heissesten Branchen.
@Composable
private fun BlasenInhalt(b: AppState.BlasenLage) {
    val stufe = remember(b) { Blasen.gesamt(b.indikatoren) }
    val rot = b.indikatoren.count { it.ampel == Fruehwarnung.Ampel.ROT }
    val deutsch = I18n.lang == Lang.DE
    val heiss = b.sektoren.firstOrNull { it.ampel != Fruehwarnung.Ampel.GRUEN }
        ?.let { s -> Branchentrends.BRANCHEN.firstOrNull { it.symbol == s.symbol }?.let { if (deutsch) it.de else it.en } }
    Column(Modifier.fillMaxWidth()) {
        if (stufe != null) {
            val ampel = when (stufe) { Blasen.Stufe.GERING -> Fruehwarnung.Ampel.GRUEN; Blasen.Stufe.ERHOEHT -> Fruehwarnung.Ampel.GELB; Blasen.Stufe.HOCH -> Fruehwarnung.Ampel.ROT }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AmpelPunkt(fwAmpelZeichen(ampel), fwAmpelFarbe(ampel), groesse = 22.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(S.blStufe(stufe.ordinal), style = MaterialTheme.typography.titleMedium)
                    Text(S.blSatz(rot, b.indikatoren.size, heiss), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val dafuer = Blasen.DAFUER.mapNotNull { k -> b.indikatoren.firstOrNull { it.schluessel == k } }
        val dagegen = Blasen.DAGEGEN.mapNotNull { k -> b.indikatoren.firstOrNull { it.schluessel == k } }
        var offen by remember { mutableStateOf<String?>(null) }
        if (dafuer.isNotEmpty()) Zwischenkopf(S.blDafuer)
        for (i in dafuer) BlasenZeile(i, b.bipJahr, offen == i.schluessel) { offen = if (offen == i.schluessel) null else i.schluessel }
        if (dagegen.isNotEmpty()) Zwischenkopf(S.blDagegen)
        for (i in dagegen) BlasenZeile(i, b.bipJahr, offen == i.schluessel) { offen = if (offen == i.schluessel) null else i.schluessel }
        if (b.sektoren.isNotEmpty()) {
            Zwischenkopf(S.blBranchen)
            for (s in b.sektoren.take(5)) {
                val name = Branchentrends.BRANCHEN.firstOrNull { it.symbol == s.symbol }?.let { if (deutsch) it.de else it.en } ?: s.symbol
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AmpelPunkt(fwAmpelZeichen(s.ampel), fwAmpelFarbe(s.ampel))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(S.blSektor(Fmt.signedPct(s.jahr, 0), Fmt.signedPct(s.zu200, 0)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        QuellenZeile(S.blQuelle)
        if (b.indikatoren.any { it.schluessel == Blasen.CAPE || it.schluessel == Blasen.MARGIN }) QuellenZeile(S.ersBlQuelle)
    }
}

// Eine Messgroesse: Ampel, Name, Einordnung in einem Satz, Wert; ein Tipp zeigt, was sie misst.
@Composable
private fun BlasenZeile(i: Blasen.Indikator, bipJahr: Int?, auf: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmpelPunkt(fwAmpelZeichen(i.ampel), fwAmpelFarbe(i.ampel))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(blNameAlle(i.schluessel), style = MaterialTheme.typography.titleSmall)
                Text(blLageAlle(i.schluessel, i.ampel.ordinal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Zeichen einer Blase sprechen gegen Aktien
                TendenzZeile(Tendenz.blase(i.ampel))
            }
            Spacer(Modifier.width(10.dp))
            Text(blWert(i), style = MaterialTheme.typography.titleSmall)
        }
        if (auf) Text(
            blErklaerungAlle(i, bipJahr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp, top = 6.dp),
        )
        // Zahlen der FINRA nur im Browser (5.92)
        if (auf && i.schluessel == Blasen.MARGIN) FinraLink()
    }
}

// Wert in der passenden Einheit.
private fun blWert(i: Blasen.Indikator): String = when (i.schluessel) {
    Blasen.KGV, Blasen.CAPE -> Fmt.num(i.wert, 1)
    Blasen.STIMMUNG, Blasen.ERWAEHNUNG -> Fmt.num(i.wert, 0)
    Blasen.BUFFETT, Blasen.KONZENTRATION -> Fmt.pct(i.wert, 0)
    Blasen.PRAEMIE, Blasen.BREITE -> (if (i.wert >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(i.wert), 1) + " " + S.fwPunkte
    else -> Fmt.signedPct(i.wert, 0)
}
