package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Beitraege
import de.troetenrepublik.app.engine.Durchschau
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.Komponenten
import de.troetenrepublik.app.i18n.EtfTexte
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.engine.KurszielLage
import de.troetenrepublik.app.engine.WatchBreite
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs

// Bausteine des Pakets q78h (0.76): Beitraege zur Tagesbewegung bei ETFs und Indizes, Kursziel-Spanne der Analysten und
// Breite der Watchlist.

@Composable
private fun farbeFuer(v: Double): androidx.compose.ui.graphics.Color {
    val signal = LocalSignal.current
    return when {
        v > 0.0005 -> signal.up
        v < -0.0005 -> signal.down
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

// Eine Zeile je Komponente: Logo, Name (bricht an Wortgrenzen um, nie abgeschnitten), rechts die Tagesaenderung mit
// gezeichnetem Dreieck. Gewicht und Beitrag zeigt die Heatmap darueber (Flaeche und Farbe). trenner: feine Linie oben.
@Composable
internal fun KomponentenZeile(state: AppState, e: Komponenten.Komponente, trenner: Boolean, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        if (trenner) Trennlinie(
            Modifier.padding(horizontal = 20.dp), thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstrumentLogo(state, e.symbol.ifBlank { e.name }, e.name, size = 28)
            Spacer(Modifier.width(12.dp))
            NamenText(e.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            val a = e.aenderung
            if (a != null) MetricValue(a, Fmt.pct(abs(a), 2), style = MaterialTheme.typography.bodyMedium)
            else Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
    }
}

// Mitglieder eines Index mit Gewicht nach Boersenwert und Tagesaenderung, nach Gewicht sortiert. Im Merker der Seite:
// Beim Zurueckscrollen wird nichts neu geholt und gerechnet. Ohne Mitglieder im Katalog bleibt die Liste leer.
@Composable
internal fun indexKomponentenLaden(state: AppState, symbol: String, merker: SeitenMerker): List<Komponenten.Komponente> {
    val schluessel = Beitraege.indexSchluessel(symbol)
    var liste by gemerkt<List<Komponenten.Komponente>>(merker, "index/komponenten") { emptyList() }
    LaunchedEffect(symbol) {
        if (schluessel == null || liste.isNotEmpty()) return@LaunchedEffect
        val w = runCatching { state.heatmap(schluessel) }.getOrDefault(emptyList()).filter { it.aenderung != null && it.gewicht > 0 }
        val anteile = Beitraege.normiert(w.map { it.gewicht })
        liste = Komponenten.sortiert(w.mapIndexed { i, h -> Komponenten.Komponente(h.symbol, h.name, anteile[i], h.aenderung) })
    }
    return liste
}

// Bestand eines ETFs (Positionen, Branchen, Anlageklassen); null, solange er nicht da ist oder es keinen gibt.
@Composable
internal fun etfBestandLaden(state: AppState, symbol: String, merker: SeitenMerker): Durchschau.Bestand? {
    var bestand by gemerkt<Durchschau.Bestand?>(merker, "etf/bestand") { null }
    var geholt by gemerkt(merker, "etf/geholt") { false }
    LaunchedEffect(symbol) {
        if (geholt) return@LaunchedEffect
        bestand = state.etfBestand(symbol)
        geholt = true
    }
    return bestand
}

// Positionen eines ETFs mit der Tagesaenderung ihres Bestands, nach Anteil sortiert; ohne Kurs bleibt die Aenderung leer.
@Composable
internal fun etfKomponentenLaden(state: AppState, symbol: String, merker: SeitenMerker, bestand: Durchschau.Bestand?): List<Komponenten.Komponente> {
    var liste by gemerkt<List<Komponenten.Komponente>>(merker, "etf/komponenten") { emptyList() }
    LaunchedEffect(bestand) {
        val pos = bestand?.positionen.orEmpty().filter { it.anteil.isFinite() && it.anteil > 0 }
        if (pos.isEmpty() || liste.isNotEmpty()) return@LaunchedEffect
        val kurse = state.durchbruchKurse(pos.map { it.symbol }.filter { it.isNotBlank() })
        liste = Komponenten.sortiert(
            pos.map { p -> Komponenten.Komponente(p.symbol, p.name, p.anteil, kurse[p.symbol]?.takeIf { it.prevClose > 0 }?.sitzungPct) },
        )
    }
    return liste
}

// Heatmap unter dem Chart eines Index oder ETFs: Flaeche nach Gewicht, Farbe nach Tagesaenderung, wie die Heatmap der
// Maerkte. Bei ETFs mit nur den groessten Positionen steht der Rest als graue Kachel. Ein Tipp oeffnet den Wert.
@Composable
internal fun KomponentenHeatmap(state: AppState, symbol: String, merker: SeitenMerker, etf: Boolean, onOpen: (String) -> Unit) {
    val liste = if (etf) etfKomponentenLaden(state, symbol, merker, etfBestandLaden(state, symbol, merker)) else indexKomponentenLaden(state, symbol, merker)
    if (liste.size < 2) return
    val uebrige = EtfTexte.t("uebrige")
    val oeffnen by androidx.compose.runtime.rememberUpdatedState(onOpen)
    val kacheln = remember(liste, etf, uebrige) {
        Komponenten.kacheln(liste, etf, uebrige).map { k ->
            val ziel = k.symbol?.takeIf { it.isNotBlank() && Symbols.find(it) != null }
            FirmaKachel(k.symbol?.let { HeatmapBeschriftung.firmenKuerzel(it) }, k.name, k.gewicht, k.aenderung, null, ziel?.let { s -> { oeffnen(s) } })
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) { FirmenTreemap(kacheln, 220.dp) }
}

// Komponenten eines Index: die groessten zehn, ein Tipp zeigt bis zu fuenfzig. Ohne Mitglieder im Katalog bleibt die
// Karte weg.
@Composable
internal fun IndexKomponentenKarte(state: AppState, symbol: String, merker: SeitenMerker, onOpen: (String) -> Unit) {
    if (Beitraege.indexSchluessel(symbol) == null) {
        ModellMelden(merker, "index", null)
        return
    }
    val liste = indexKomponentenLaden(state, symbol, merker)
    val alleZustand = merker.zustand("index/alle") { false }
    if (liste.isEmpty()) {
        ModellMelden(merker, "index", null)
        return
    }
    val alle = alleZustand.value
    val weg = androidx.compose.runtime.rememberUpdatedState(onOpen)
    val modell = remember(liste, alle) {
        val gezeigt = if (alle) liste.take(50) else liste.take(10)
        val rows = ArrayList<ZeilenDaten>()
        gezeigt.forEachIndexed { i, e -> rows.add(ZeilenDaten("k-${e.symbol}") { KomponentenZeile(state, e, trenner = i > 0) { weg.value(e.symbol) } }) }
        if (liste.size > 10) {
            rows.add(ZeilenDaten("mehr") {
                Text(
                    if (alleZustand.value) S.less else S.bbAlle(minOf(50, liste.size)), style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
                    modifier = Modifier.textKlick(20.dp) { alleZustand.value = !alleZustand.value }.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            })
        }
        ListenModell(titel = "", gross = true, kern = null, zeilen = rows, immerOffen = true, kopf = { SectionHeader(S.vbKomponenten) })
    }
    ModellMelden(merker, "index", modell)
}

internal fun LazyListScope.indexKomponentenAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "index")
}

// Kursziel-Spanne der Analysten: Balken vom tiefsten zum hoechsten Ziel, darauf Median und aktueller Kurs.
@Composable
internal fun KurszielSpanne(lage: KurszielLage.Lage, tiefText: String, medianText: String?, hochText: String, kursText: String) {
    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
    val voll = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val signal = LocalSignal.current
    val hell = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val mitte = size.height / 2
            val h = 8.dp.toPx()
            drawRoundRect(spur, Offset(0f, mitte - h / 2), Size(size.width, h), CornerRadius(h / 2, h / 2))
            val x0 = size.width * lage.tiefPos
            val x1 = size.width * lage.hochPos
            drawRoundRect(voll, Offset(x0, mitte - h / 2), Size((x1 - x0).coerceAtLeast(h), h), CornerRadius(h / 2, h / 2))
            lage.medianPos?.let { p ->
                drawRect(hell, Offset(size.width * p - 1.5.dp.toPx(), mitte - 9.dp.toPx()), Size(3.dp.toPx(), 18.dp.toPx()))
            }
            drawCircle(signal.link, 6.dp.toPx(), Offset(size.width * lage.kursPos, mitte))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(S.vbTief + " " + tiefText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            if (medianText != null) Text(S.vbMedian + " " + medianText, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            Text(S.vbHoch + " " + hochText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
        Text(S.vbKurs + " " + kursText, style = MaterialTheme.typography.bodySmall, color = signal.link, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp))
        // Liegt der Kurs unter dem Median der Ziele, ist Luft nach oben (grün), darüber ist er schon weiter (rot)
        lage.abweichung?.let { a ->
            val richtung = if (kotlin.math.abs(a) < 0.5) 0 else if (a < 0) -1 else 1
            Text(
                S.vbKursMedian(Fmt.pct(kotlin.math.abs(a), 0), richtung), style = MaterialTheme.typography.bodySmall,
                color = signal.forChange(-richtung.toDouble()), maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// Breite der Liste: wie viele Werte heute im Plus und im Minus stehen, als zweifarbiger Balken, dazu das Mittel.
@Composable
internal fun WatchBreiteZeile(aenderungen: List<Double?>) {
    val b = WatchBreite.berechne(aenderungen)
    val gesamt = b.steigen + b.fallen + b.gleich
    if (gesamt == 0) return
    val signal = LocalSignal.current
    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(spur, cornerRadius = r)
            val wUp = size.width * b.steigen / gesamt
            val wDown = size.width * b.fallen / gesamt
            if (wUp > 0f) drawRoundRect(signal.up, size = Size(wUp.coerceAtLeast(size.height), size.height), cornerRadius = r)
            if (wDown > 0f) drawRoundRect(signal.down, Offset(size.width - wDown.coerceAtLeast(size.height), 0f), Size(wDown.coerceAtLeast(size.height), size.height), r)
        }
        Row(Modifier.padding(top = 4.dp)) {
            Text(S.vbImPlus(b.steigen), style = MaterialTheme.typography.bodySmall, color = signal.up, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(12.dp))
            Text(S.vbImMinus(b.fallen), style = MaterialTheme.typography.bodySmall, color = signal.down, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            b.mittel?.let { Text("Ø " + Fmt.signedPct(it, 2), style = MaterialTheme.typography.bodySmall, color = farbeFuer(it), maxLines = 1, softWrap = false) }
        }
    }
}
