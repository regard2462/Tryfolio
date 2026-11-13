package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.DepotStreuung
import de.troetenrepublik.app.engine.Durchschau
import de.troetenrepublik.app.engine.EtfStreuung
import de.troetenrepublik.app.engine.Komponenten
import de.troetenrepublik.app.etfLaenderDurchschau
import de.troetenrepublik.app.etfLandPunkte
import de.troetenrepublik.app.i18n.EtfTexte
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.landNamen
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.market.ohneAbbruch

// ETF-Durchschau auf der Seite eines ETFs: zuerst die Streuung (Einordnung, Leiste der groessten Positionen mit Rest),
// dann Positionen, Laender mit Landrisiko, Waehrungen, Branchen und Anlageklassen; haelt das Depot mehrere Werte,
// darunter die Firmen dieses ETFs, die im ganzen Depot den groessten Anteil haben. Ohne Daten bleibt sie weg.
// oeffnen: Weg zu einem Wertpapier ("instrument/…") oder Land ("land/…").
@Composable
internal fun EtfDurchschauKarte(state: AppState, symbol: String, merker: SeitenMerker, oeffnen: (String) -> Unit = {}) {
    // Alles im Merker der Seite: Beim Zurueckscrollen wird nichts neu geholt, und die Karte springt nicht
    // Bestand und Positionen mit Tagesaenderung teilt sich die Karte mit der Heatmap unter dem Chart (Vorbild078H.kt)
    val bestand = etfBestandLaden(state, symbol, merker)
    val komponenten = etfKomponentenLaden(state, symbol, merker, bestand)
    var wirkung by gemerkt<List<Durchschau.Wirkung>>(merker, "etf/wirkung") { emptyList() }
    var wirkungGeholt by gemerkt(merker, "etf/wirkung-geholt") { false }
    LaunchedEffect(symbol) {
        if (wirkungGeholt) return@LaunchedEffect
        wirkung = state.depotDurchschau()
        wirkungGeholt = true
    }
    // Laender des Fonds (Index aus dem Buendel, sonst Sitz der Positionen) und ihr Landrisiko
    var laender by gemerkt<EtfStreuung.Laender?>(merker, "etf/laender") { null }
    var namen by gemerkt<Map<String, Pair<String, String>>>(merker, "etf/namen") { emptyMap() }
    var punkte by gemerkt<Map<String, Double>>(merker, "etf/punkte") { emptyMap() }
    var landGeholt by gemerkt(merker, "etf/land-geholt") { false }
    LaunchedEffect(bestand) {
        val b = bestand ?: return@LaunchedEffect
        if (landGeholt) return@LaunchedEffect
        val l = ohneAbbruch { state.etfLaenderDurchschau(symbol, b.positionen) }.getOrNull()
        if (l != null) {
            namen = state.landNamen()
            laender = l
            punkte = ohneAbbruch { state.etfLandPunkte(l.liste.map { it.a2 }) }.getOrDefault(emptyMap())
        }
        landGeholt = true
    }
    val b = bestand
    // Nur die Kosten bekannt (5.94): die Karte bleibt weg, die TER nutzt der Depot-Check
    if (b == null || (b.positionen.isEmpty() && b.branchen.isEmpty() && b.aktien + b.anleihen + b.bar <= 0)) {
        ModellMelden(merker, "etf", null)
        return
    }
    val allePos = merker.zustand("etf/alle-pos") { false }
    val alleLaender = merker.zustand("etf/alle-laender") { false }
    val alleBranchen = merker.zustand("etf/alle-branchen") { false }
    val alleDepot = merker.zustand("etf/alle-depot") { false }
    val weg = rememberUpdatedState(oeffnen)
    val l0 = laender
    val modell = remember(b, wirkung, komponenten, l0, namen, punkte, allePos.value, alleLaender.value, alleBranchen.value, alleDepot.value) {
        etfModell(state, b, wirkung, komponenten, l0, namen, punkte, allePos, alleLaender, alleBranchen, alleDepot) { weg.value(it) }
    }
    ModellMelden(merker, "etf", modell)
}

internal fun LazyListScope.etfDurchschauAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "etf")
}

// Die Karte als einzelne Eintraege der LazyColumn: je Kennzahl, Position, Land, Waehrung, Branche und Depotfirma eine Zeile.
private fun etfModell(
    state: AppState, b: Durchschau.Bestand, wirkung: List<Durchschau.Wirkung>, komponenten: List<Komponenten.Komponente>,
    laender: EtfStreuung.Laender?, namen: Map<String, Pair<String, String>>, punkte: Map<String, Double>,
    allePos: MutableState<Boolean>, alleLaender: MutableState<Boolean>, alleBranchen: MutableState<Boolean>, alleDepot: MutableState<Boolean>,
    oeffnen: (String) -> Unit,
): ListenModell {
    fun pct(v: Double) = if (v > 0.0005) Fmt.pct(v * 100, 1) else ""
    val rows = ArrayList<ZeilenDaten>()
    val positionen = b.positionen.sortedByDescending { it.anteil }
    val k = EtfStreuung.kennzahlen(positionen)
    val risiko = laender?.let { EtfStreuung.risiko(it, punkte::get) }
    val riskant = risiko?.laender.orEmpty().map { it.a2 }.toSet()
    fun land(a2: String) = landKurz(landName(namen, a2))

    // Kernaussage: Einordnung der Streuung, rechts das groesste Land des Index
    if (k != null) {
        val erstes = laender?.takeIf { it.ausIndex }?.liste?.firstOrNull()
        rows.add(ZeilenDaten("kern") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    EtfTexte.t("stufe." + k.stufe.name), style = MaterialTheme.typography.titleMedium,
                    color = if (k.stufe == EtfStreuung.Stufe.STARK) LocalSignal.current.warn else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                if (erstes != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        land(erstes.a2) + " " + Fmt.pct(erstes.anteil * 100, 1), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        })
        val teile = EtfStreuung.leiste(k, positionen)
        if (teile.size > 1) rows.add(ZeilenDaten("leiste") { StapelLeiste(teile, mitRest = k.rest > 0) })
        rows.add(ZeilenDaten("top") {
            LbZeile(if (k.genannt == 1) EtfTexte.t("top.eine") else EtfTexte.t("top", k.genannt.toString()), Fmt.pct(k.top * 100, 1))
        })
        if (k.rest > 0.0005) rows.add(ZeilenDaten("rest") { LbZeile(EtfTexte.t("rest"), Fmt.pct(k.rest * 100, 1)) })
        k.anzahl?.let { n -> rows.add(ZeilenDaten("anzahl") { LbZeile(EtfTexte.t("anzahl"), Fmt.num(n.toDouble(), 0)) }) }
        k.wirksam?.let { w -> rows.add(ZeilenDaten("wirksam") { LbZeile(EtfTexte.t("wirksam"), Fmt.num(w, 1)) }) }
    }

    // Anlageklassen: Leiste nur bei gemischten Fonds, die Zeile nennt die Anteile
    val klassen = S.etfKlassen(pct(b.aktien), pct(b.anleihen), pct(b.bar))
    if (klassen.isNotBlank()) {
        val k3 = listOf(b.aktien, b.anleihen, b.bar).filter { it > 0.0005 }
        val gemischt = k3.size > 1 && k3.sortedDescending()[1] >= 0.05 && k3.sum() <= EtfStreuung.SUMME_HOECHSTENS
        rows.add(ZeilenDaten("klassen") {
            Column {
                if (gemischt) StapelLeiste(k3 + listOfNotNull((1.0 - k3.sum()).takeIf { it > 0.0005 }), mitRest = 1.0 - k3.sum() > 0.0005)
                Text(
                    klassen, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        })
    }

    // Groesste Positionen: eine Zeile je Position mit Logo, Name und Tagesaenderung, nach Gewicht sortiert. Gewicht
    // und Beitrag zeigt die Heatmap unter dem Chart.
    if (positionen.isNotEmpty()) {
        rows.add(ZeilenDaten("top-kopf") { Zwischenkopf(S.etfTop) })
        val zeilen = komponenten.ifEmpty { positionen.map { Komponenten.Komponente(it.symbol, it.name, it.anteil, null) } }
        val zuerst = EtfStreuung.zuerst(zeilen.size)
        (if (allePos.value) zeilen else zeilen.take(zuerst)).forEachIndexed { i, p ->
            val bekannt = p.symbol.isNotBlank() && Symbols.find(p.symbol) != null
            rows.add(ZeilenDaten("pos-$i") {
                KomponentenZeile(state, p, trenner = i > 0, onClick = if (bekannt) ({ oeffnen("instrument/${p.symbol}") }) else null)
            })
        }
        if (zeilen.size > zuerst) rows.add(ZeilenDaten("pos-mehr") { MehrZeile(allePos, zeilen.size) })
    }

    // Laender: Balken im Verhaeltnis zum groessten Land; Laender mit hohem Landrisiko in der Warnfarbe
    if (laender != null && laender.liste.isNotEmpty()) {
        rows.add(ZeilenDaten("land-kopf") { Zwischenkopf(EtfTexte.t("laender")) })
        if (risiko != null && risiko.anteil > 0.0005) {
            val genannt = risiko.laender.take(RISIKO_NAMEN).joinToString(", ") { land(it.a2) }
            val mehr = risiko.laender.size - RISIKO_NAMEN
            val hint = if (risiko.laender.isEmpty()) null else if (mehr > 0) genannt + ", " + EtfTexte.t("weitere", mehr.toString()) else genannt
            rows.add(ZeilenDaten("land-risiko") {
                KeyValueRow(
                    EtfTexte.t("risiko"), Fmt.pct(risiko.anteil * 100, 1),
                    valueColor = if (risiko.anteil > 0.0005) LocalSignal.current.warn else null, hint = hint,
                )
            })
        }
        val groesst = laender.liste.first().anteil
        val zuerst = EtfStreuung.zuerst(laender.liste.size)
        (if (alleLaender.value) laender.liste else laender.liste.take(zuerst)).forEachIndexed { i, l ->
            rows.add(ZeilenDaten("land-${l.a2}") {
                AnteilZeile(
                    land(l.a2), Fmt.pct(l.anteil * 100, 1), l.anteil / groesst,
                    if (l.a2 in riskant) LocalSignal.current.warn else AnteilsFarben.folge(i),
                    onClick = if (l.a2 in namen) ({ oeffnen("land/${l.a2}") }) else null,
                )
            })
        }
        if (laender.liste.size > zuerst) rows.add(ZeilenDaten("land-mehr") { MehrZeile(alleLaender, laender.liste.size) })

        // Waehrungen nur aus der Verteilung des Index (die gezeigten Positionen allein sagen dazu zu wenig)
        val waehrungen = if (laender.ausIndex) EtfStreuung.waehrungen(laender, DepotStreuung::waehrungVon).filter { it.second >= 0.01 }.take(WAEHRUNGEN_HOECHSTENS) else emptyList()
        if (waehrungen.isNotEmpty()) {
            rows.add(ZeilenDaten("waehrung-kopf") { Zwischenkopf(EtfTexte.t("waehrungen")) })
            val max = waehrungen.first().second
            waehrungen.forEachIndexed { i, (code, a) ->
                rows.add(ZeilenDaten("waehrung-$code") { AnteilZeile(code, Fmt.pct(a * 100, 1), a / max, AnteilsFarben.folge(i)) })
            }
        }
    }

    if (b.branchen.isNotEmpty()) {
        rows.add(ZeilenDaten("branchen-kopf") { Zwischenkopf(S.etfBranchen) })
        val max = b.branchen.maxOf { it.second }
        val zuerst = EtfStreuung.zuerst(b.branchen.size)
        (if (alleBranchen.value) b.branchen else b.branchen.take(zuerst)).forEachIndexed { i, (name, a) ->
            rows.add(ZeilenDaten("branche-$i") { AnteilZeile(S.sectorName(name), Fmt.pct(a * 100, 1), a / max, AnteilsFarben.folge(i)) })
        }
        if (b.branchen.size > zuerst) rows.add(ZeilenDaten("branche-mehr") { MehrZeile(alleBranchen, b.branchen.size) })
    }
    // Nur die Firmen dieses ETFs, die im Depot zusammen den groessten Anteil haben: eine Zeile je Firma, Name links,
    // rechts der Anteil am Depot; der Weg ueber mehrere ETFs steht klein davor
    val eigene = run {
        val hier = b.positionen.map { Durchschau.gleich(it.symbol) }.toSet()
        wirkung.filter { it.symbol in hier && it.gesamt >= 0.005 }
    }
    if (eigene.isNotEmpty()) {
        rows.add(ZeilenDaten("depot-kopf") { KopfMitInfo(S.etfDepotTitel, S.etfDepotHinweis, gross = false) })
        (if (alleDepot.value) eigene else eigene.take(DEPOT_ZUERST)).forEachIndexed { i, w ->
            rows.add(ZeilenDaten("depot-$i") { DepotZeile(w.name, Fmt.pct(w.gesamt * 100, 1), if (w.etfs.size > 1) EtfTexte.t("ueber", w.etfs.size.toString()) else null) })
        }
        if (eigene.size > DEPOT_ZUERST) rows.add(ZeilenDaten("depot-mehr") { MehrZeile(alleDepot, eigene.size) })
    }
    // Erklaerung hinter dem Info-Symbol am Titel: Quelle, Schwellen der Einordnung, Herkunft der Laender
    val hinweis = buildList {
        add(S.etfHinweis)
        if (k != null) add(EtfTexte.t("hinweis.stufe", Fmt.pct(EtfStreuung.KONZENTRIERT_AB * 100, 0), Fmt.pct(EtfStreuung.STARK_AB * 100, 0)))
        if (k?.wirksam != null) add(EtfTexte.t("hinweis.wirksam"))
        if (laender != null) add(EtfTexte.t(if (laender.ausIndex) "hinweis.index" else "hinweis.gezeigt"))
        if (risiko != null) add(EtfTexte.t("hinweis.risiko", Fmt.num(EtfStreuung.RISIKO_UNTER, 0)))
    }.joinToString("\n\n")
    return ListenModell(titel = "", gross = true, kern = null, zeilen = rows, immerOffen = true, kopf = { KopfMitInfo(S.etfTitel, hinweis, gross = true) })
}

// So viele Laender mit hohem Landrisiko nennt die Zeile mit Namen, der Rest steht als Zahl dahinter
private const val RISIKO_NAMEN = 5
private const val WAEHRUNGEN_HOECHSTENS = 5
private const val DEPOT_ZUERST = 5

// Ton einer Position in Leiste und Liste: die groesste voll, jede weitere etwas heller
private fun stufenFarbe(farbe: Color, i: Int, n: Int): Color =
    farbe.copy(alpha = if (n <= 1) 1f else 1f - 0.6f * i / (n - 1))

// Gestapelte Leiste ueber die ganze Breite: je Anteil ein Abschnitt, groesster zuerst; mitRest: der letzte Abschnitt ist
// der Rest und steht im Ton der Spur.
@Composable
private fun StapelLeiste(teile: List<Double>, mitRest: Boolean) {
    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
    val farbe = Kategorie.blau
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(14.dp).clip(RoundedCornerShape(4.dp))) {
        val luecke = 1.5.dp.toPx()
        val n = if (mitRest) teile.size - 1 else teile.size
        var x = 0f
        teile.forEachIndexed { i, t ->
            val w = (size.width * t).toFloat()
            val rest = mitRest && i == teile.lastIndex
            val breite = if (i == teile.lastIndex) size.width - x else (w - luecke).coerceAtLeast(1f)
            if (breite > 0f) drawRect(if (rest) spur else stufenFarbe(farbe, i, n), Offset(x, 0f), Size(breite, size.height))
            x += w
        }
    }
}

// Eine Zeile einer Anteilsliste: Bezeichnung links (bricht um, steht immer ganz da), daneben ein schmaler Balken im
// Verhaeltnis zum groessten Wert der Liste, rechts der Wert in einer Zeile. name: Firmenname, der die laengste passende
// Fassung zeigt.
@Composable
private fun AnteilZeile(label: String, wert: String, anteil: Double, farbe: Color, name: Boolean = false, onClick: (() -> Unit)? = null) {
    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (name) NamenText(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        else Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.width(72.dp).height(6.dp)) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(spur, cornerRadius = r)
            val w = (size.width * anteil.coerceIn(0.0, 1.0)).toFloat()
            if (w > 0f) drawRoundRect(farbe, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
        }
        Text(
            wert, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 64.dp),
        )
    }
}

// Aufklappen einer langen Liste, wie bei den Komponenten eines Index
@Composable
private fun MehrZeile(alle: MutableState<Boolean>, n: Int) {
    Text(
        if (alle.value) S.less else S.bbAlle(n), style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link,
        modifier = Modifier.textKlick(20.dp) { alle.value = !alle.value }.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

// Titel eines Abschnitts mit Info-Symbol rechts; die Hinweise zum Abschnitt stehen erst im Blatt dahinter.
@Composable
private fun KopfMitInfo(titel: String, info: String, gross: Boolean) {
    var offen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { if (gross) SectionHeader(titel) else Zwischenkopf(titel) }
        androidx.compose.material3.IconButton(onClick = { offen = true }, modifier = Modifier.padding(end = 8.dp)) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
            )
        }
    }
    if (offen) MarktInfoBlatt(titel, info) { offen = false }
}

// Eine Firma im Depot: Name links (bricht um), rechts Anteil am Depot; der Weg ueber mehrere ETFs klein davor.
@Composable
private fun DepotZeile(name: String, wert: String, zusatz: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        NamenText(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        if (zusatz != null) {
            Text(zusatz, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
        }
        Text(wert, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
    }
}
