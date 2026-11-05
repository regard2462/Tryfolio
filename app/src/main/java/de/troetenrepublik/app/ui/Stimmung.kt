package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.Gesamtstimmung
import de.troetenrepublik.app.engine.Stimmungsbarometer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer
import de.troetenrepublik.app.engine.Stimmungsbarometer.Stufe
import de.troetenrepublik.app.i18n.S
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity

// Marktlage im Reiter „Stimmung“ der Seite Maerkte. Ganz oben die Gesamtstimmung (5.95) als Ueberblick, darunter Stimmung:
// Fear & Greed von CNN, alternative.me, AAII-Umfrage, Put/Call der Cboe, Tradestie fuer
// r/wallstreetbets, Sorgen auf Wikipedia, VIX (Yahoo Finance) und sichere Haefen (Aktien gegen Staatsanleihen). Fruehwarnzeichen als Ampeln.
// Ein Tipp zeigt Verlauf und Einzelheiten. Die Quelle steht jeweils direkt bei den Daten.
@Composable
internal fun StimmungsAbschnitt(state: AppState, onOpen: (String) -> Unit) {
    var st by remember { mutableStateOf<AppState.Stimmungen?>(null) }
    var fw by remember { mutableStateOf<List<Fruehwarnung.Zeichen>?>(null) }
    LaunchedEffect(Unit) { st = runCatching { state.stimmungen() }.getOrNull() ?: AppState.Stimmungen(null, null, null, null) }
    LaunchedEffect(Unit) { fw = runCatching { state.fruehwarnung() }.getOrNull().orEmpty() }
    // Sorgen auf Wikipedia kommen langsamer (Wikimedia begrenzt die Abfragen) und werden nachgereicht
    var sorgen by remember { mutableStateOf<Stimmungsbarometer.Barometer?>(null) }
    // Merker: Quelle fertig geladen (Antwort darf auch null sein); fuer die Ladeanzeige der Gesamtstimmung
    var sorgenFertig by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sorgen = runCatching { state.sorgenBarometer() }.getOrNull(); sorgenFertig = true }
    var pos by remember { mutableStateOf<List<de.troetenrepublik.app.engine.Positionierung.Lage>?>(null) }
    LaunchedEffect(Unit) { pos = runCatching { state.positionierung() }.getOrNull().orEmpty() }
    var phasen by remember { mutableStateOf<List<de.troetenrepublik.app.engine.Marktphase.Phase>?>(null) }
    LaunchedEffect(Unit) { phasen = runCatching { state.marktphase() }.getOrNull().orEmpty() }
    // Put/Call-Verhaeltnis der Cboe als achtes Barometer
    var putCall by remember { mutableStateOf<de.troetenrepublik.app.engine.WeitereQuellen.PutCall?>(null) }
    LaunchedEffect(Unit) { putCall = state.putCall() }
    // Zusaetzliche Bestandteile der Gesamtstimmung: VVIX, MOVE, Marktbreite, Umfrage, Margin
    var zusatz by remember { mutableStateOf<Map<String, Gesamtstimmung.Wert>?>(null) }
    LaunchedEffect(Unit) { zusatz = runCatching { state.stimmungZusatz() }.getOrNull().orEmpty() }
    // Abrufzeitpunkt der Verbraucherbefragung fuer das FRED-Zitat
    val umichAbruf = remember(zusatz) { state.umichAbruf }
    val stimmung = st?.let { s -> sorgen?.let { s.copy(sorgen = it) } ?: s }
    val gesamt = remember(stimmung, fw, pos, zusatz) { stimmung?.let { state.gesamtstimmungAus(it, fw.orEmpty(), pos.orEmpty(), zusatz.orEmpty()) } }
    val gesamtLaedt = stimmung == null || fw == null || pos == null || zusatz == null || !sorgenFertig
    StimmungsInhalt(stimmung, onOpen, putCall, gesamt, gesamtLaedt, umichAbruf)
    MarktphaseInhalt(phasen)
    FruehwarnInhalt(fw)
    // Zinskurven und VIX-Kurve in eigenem Blatt (seit 5.91)
    var kurven by remember { mutableStateOf(false) }
    NavRow(S.mkTitel, S.mkUntertitel) { kurven = true }
    if (kurven) MarktkurvenSheet(state) { kurven = false }
    PositionierungInhalt(pos)
}

// Bullen- oder Baerenmarkt (seit 5.9, knapp seit 5.92): je Index eine Zeile mit Ampel, Phase seit wann und Abstand zum
// Hoch. Ein Tipp zeigt zwei Jahre Kursverlauf mit der Schwelle, an der die Phase kippen wuerde, die 200-Tage-Linie und
// das letzte Golden oder Death Cross. Die Erklaerung steht hinter dem Info-Symbol.
@Composable
internal fun MarktphaseInhalt(phasen: List<de.troetenrepublik.app.engine.Marktphase.Phase>?) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth()) {
        MarktInfoKopf(S.mpTitel, S.mpHinweis)
        when {
            phasen == null -> EmptyHint(S.trLaedt)
            phasen.isEmpty() -> EmptyHint(S.heatmapLeer)
            else -> {
                val bullen = phasen.count { it.art == de.troetenrepublik.app.engine.Marktphase.Art.BULLE && !it.korrektur }
                Text(S.mpGesamt(bullen, phasen.size), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp))
                var offen by remember { mutableStateOf<String?>(null) }
                for (p in phasen) {
                    val auf = offen == p.schluessel
                    val ampel = when {
                        p.art == de.troetenrepublik.app.engine.Marktphase.Art.BAER -> Fruehwarnung.Ampel.ROT
                        p.korrektur -> Fruehwarnung.Ampel.GELB
                        else -> Fruehwarnung.Ampel.GRUEN
                    }
                    val seit = java.time.Instant.ofEpochMilli(p.seit).atZone(java.time.ZoneOffset.UTC).let { java.time.YearMonth.of(it.year, it.monthValue) }
                    Column(Modifier.fillMaxWidth().clickable { offen = if (auf) null else p.schluessel }.animateContentSize()
                        .padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AmpelPunkt(fwAmpelZeichen(ampel), fwAmpelFarbe(ampel))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(S.mpIndex(p.schluessel), style = MaterialTheme.typography.titleSmall)
                                Text(S.mpPhase(p.art.name, p.korrektur, Fmt.monthYear(seit)), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.signedPct(p.vomHoch, 1), style = MaterialTheme.typography.titleSmall)
                                Text(S.mpVomHochKurz, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (auf) {
                            Spacer(Modifier.height(6.dp))
                            VerlaufFrei(p.verlauf, p.schwelle)
                            p.zur200?.let {
                                Text(S.mp200(Fmt.signedPct(it, 1)), style = MaterialTheme.typography.bodySmall,
                                    color = if (it >= 0) signal.up else signal.down)
                            }
                            Text(S.mpDetail(p.art.name, Fmt.signedPct(p.vomTief, 1), p.golden, p.kreuzSeit?.let { Fmt.date(it) }),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

// Positionierung an den Terminmaerkten (seit 5.8, als Balken seit 5.92): oben die Kernaussage, je Markt ein Satz und
// ein Balken, wo die Nettoposition grosser Spekulanten im Vergleich zu drei Jahren steht. Ein Tipp zeigt Verlauf und Zahlen.
@Composable
internal fun PositionierungInhalt(lagen: List<de.troetenrepublik.app.engine.Positionierung.Lage>?) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth()) {
        MarktInfoKopf(S.cotTitel, S.cotInfo)
        when {
            lagen == null -> EmptyHint(S.trLaedt)
            lagen.isEmpty() -> EmptyHint(S.heatmapLeer)
            else -> {
                val extrem = lagen.filter { it.index >= 80 || it.index <= 20 }.maxByOrNull { kotlin.math.abs(it.index - 50) }
                Text(S.cotKern(extrem?.let { S.cotMarkt(it.schluessel) }, extrem?.index), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp))
                var offen by remember { mutableStateOf<String?>(null) }
                for (l in lagen) {
                    val auf = offen == l.schluessel
                    Column(Modifier.fillMaxWidth().clickable { offen = if (auf) null else l.schluessel }.animateContentSize()
                        .padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(S.cotMarkt(l.schluessel), style = MaterialTheme.typography.titleSmall)
                                Text(S.cotSatz(l.index), style = MaterialTheme.typography.bodySmall,
                                    color = when { l.index >= 80 -> signal.up; l.index <= 20 -> signal.down; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                            }
                            Spacer(Modifier.width(10.dp))
                            PerzentilBalken(l.index, Modifier.width(COT_BALKEN).height(16.dp))
                        }
                        if (auf) {
                            Spacer(Modifier.height(6.dp))
                            VerlaufFrei(l.verlauf, 0.0)
                            val d = l.anteil - l.vorwoche
                            Text(
                                S.cotDetail(Fmt.signedPct(l.anteil, 1), (if (d >= 0.05) "+" else if (d <= -0.05) "−" else "±") + Fmt.num(kotlin.math.abs(d), 1), l.index),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                QuellenZeile(S.cotQuelle(lagen.maxOf { it.stand }.let { Fmt.date(it) }))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

private val COT_BALKEN = 112.dp

// Wo ein Wert von 0 bis 100 liegt: Bahn mit den Randbereichen (unter 20 und ueber 80) eingefaerbt, darauf ein Punkt.
@Composable
private fun PerzentilBalken(index: Int, modifier: Modifier) {
    val signal = LocalSignal.current
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val punkt = MaterialTheme.colorScheme.onSurface
    val rand = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        val h = 6.dp.toPx()
        val y = size.height / 2
        val r = androidx.compose.ui.geometry.CornerRadius(h / 2)
        drawRoundRect(bahn, Offset(0f, y - h / 2), Size(size.width, h), r)
        drawRoundRect(signal.down.copy(alpha = 0.55f), Offset(0f, y - h / 2), Size(size.width * 0.2f, h), r)
        drawRoundRect(signal.up.copy(alpha = 0.55f), Offset(size.width * 0.8f, y - h / 2), Size(size.width * 0.2f, h), r)
        val x = (size.width * index.coerceIn(0, 100) / 100f).coerceIn(h, size.width - h)
        drawCircle(rand, radius = 7.dp.toPx(), center = Offset(x, y))
        drawCircle(punkt, radius = 5.dp.toPx(), center = Offset(x, y))
    }
}

// Eine Kachel der Stimmung: Titel, Barometer, Zeile unter der Skala, Quelle, Einzelheiten beim Aufklappen.
private class StimmungsKachel(
    val schluessel: String, val name: String, val b: Barometer, val zeile: String, val quelle: String, val link: String?,
    val mehr: @Composable () -> Unit,
)

// null: wird noch geladen.
@Composable
internal fun StimmungsInhalt(s: AppState.Stimmungen?, onOpen: (String) -> Unit, putCall: de.troetenrepublik.app.engine.WeitereQuellen.PutCall? = null, gesamt: Gesamtstimmung.Ergebnis? = null, gesamtLaedt: Boolean = false, umichAbruf: Long = 0L) {
    Column(Modifier.fillMaxWidth()) {
        // Die Erklaerung der Barometer steht im aufgeklappten Teil der Gesamtkachel
        MarktInfoKopf(S.sbTitel, null)
        // Ohne Gesamtkachel steht die Erklaerung direkt unter dem Kopf
        if (gesamt == null && !gesamtLaedt) AufklappText(S.sbHinweis, Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), nachZeile = true)
        // Gesamtstimmung (5.95) als Kachel ueber die volle Breite, fester Platz direkt unter dem Kopf
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
            when {
                gesamt != null -> GesamtKachel(gesamt, gesamtLaedt, umichAbruf)
                gesamtLaedt -> GesamtPlatzhalter()
            }
        }
        val kacheln = if (s == null) emptyList() else listOfNotNull(
            s.cnn?.let { b ->
                StimmungsKachel("cnn", S.sbCnn, b, S.sbStufe(b.stufe.ordinal) + " · " + Fmt.num(b.wert, 0), S.sbQuelleCnn, Stimmungsbarometer.CNN_SEITE) {
                    Verlaufslinie(b.verlauf)
                    for (t in b.teile) PunktText(
                        S.sbTeil(t.schluessel) + ": " + Fmt.num(t.wert, 0) + " · " + S.sbStufe(t.stufe.ordinal),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            s.aaii?.let { a ->
                StimmungsKachel("aaii", S.sbAaii, a.barometer, S.sbStufe(a.barometer.stufe.ordinal) + " · " + Fmt.num(a.barometer.wert, 0), S.sbQuelleAaii, Fruehwarnung.AAII_SEITE) {
                    Verlaufslinie(a.barometer.verlauf)
                    Text(S.sbAaiiMehr(Fmt.num(a.woche.bullisch, 1), Fmt.num(a.woche.neutral, 1), Fmt.num(a.woche.baerisch, 1), Fmt.date(a.woche.zeit)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            s.krypto?.let { b ->
                StimmungsKachel("krypto", S.sbKrypto, b, S.sbStufe(b.stufe.ordinal) + " · " + Fmt.num(b.wert, 0), S.sbQuelleKrypto, Stimmungsbarometer.KRYPTO_SEITE) {
                    Verlaufslinie(b.verlauf)
                }
            },
            s.wsb?.let { r ->
                val lage = r.lage
                val anlass = lage?.anlass
                // Seit 5.93: Anlass zuerst nennen, wenn ein Kurs stark lief; Richtung nur bei genug klaren Kommentaren
                val zeile = when {
                    anlass != null -> if (anlass.aenderung < 0) S.sbWsbNachFall else S.sbWsbNachAnstieg
                    lage?.unklar == true -> S.sbWsbUnklar
                    else -> S.sbSteigend(Math.round(r.barometer.wert).toInt())
                }
                StimmungsKachel("wsb", S.sbWsb, r.barometer, zeile, S.sbQuelleWsb, Stimmungsbarometer.WSB_SEITE) {
                    anlass?.let { a ->
                        Text(S.sbWsbAnlass(a.ticker, Fmt.signedPct(a.aenderung, 0)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (lage?.unklar == true) Text(
                        S.sbWsbUnklarMehr(Math.round((lage?.klarAnteil ?: 0.0) * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else Text(S.sbRedditWert(Math.round(r.barometer.wert).toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val steigend = r.werte.filter(Stimmungsbarometer::wsbSteigendKlar).take(5)
                    val fallend = r.werte.filter(Stimmungsbarometer::wsbFallendKlar).take(5)
                    val offen = r.werte.filter { !Stimmungsbarometer.wsbKlar(it) }.take(5)
                    if (steigend.isNotEmpty()) WsbListe(S.sbWsbSteigend, steigend, r.aenderungen, onOpen)
                    if (fallend.isNotEmpty()) WsbListe(S.sbWsbFallend, fallend, r.aenderungen, onOpen)
                    if (offen.isNotEmpty()) WsbListe(S.sbWsbOffen, offen, r.aenderungen, onOpen)
                }
            },
            s.sorgen?.let { b ->
                StimmungsKachel("sorgen", S.sbSorgen, b, S.sbStufe(b.stufe.ordinal) + " · " + Fmt.num(b.wert, 0), S.sbQuelleSorgen, null) {
                    Verlaufslinie(b.verlauf)
                    Text(S.sbSorgenMehr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            s.vix?.let { b ->
                StimmungsKachel("vix", S.sbVix, b, S.sbStufe(b.stufe.ordinal) + " · " + Fmt.num(b.wert, 0), S.sbQuelleVix, null) {
                    Verlaufslinie(b.verlauf)
                    Text(S.sbVixMehr(Fmt.num(b.teile.firstOrNull()?.wert ?: 0.0, 1)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            s.sicher?.let { b ->
                StimmungsKachel("sicher", S.sbSicher, b, S.sbStufe(b.stufe.ordinal) + " · " + Fmt.num(b.wert, 0), S.sbQuelleSicher, null) {
                    Verlaufslinie(b.verlauf)
                    Text(S.sbSicherMehr(Fmt.signedPct(b.teile.firstOrNull()?.wert ?: 0.0, 1)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            putCall?.let { p -> Stimmungsbarometer.putCallBarometer(p)?.let { b -> p to b } }?.let { (p, b) ->
                val r = Fmt.num(b.teile.first().wert, 2)
                StimmungsKachel("putcall", S.sbPutCall, b, S.sbStufe(b.stufe.ordinal) + " · " + r, S.wqPcQuelle, de.troetenrepublik.app.engine.WeitereQuellen.CBOE_SEITE) {
                    Text(S.sbPutCallMehr(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        S.wqPcZeile(p.gesamt?.takeIf { p.aktien != null }?.let { Fmt.num(it, 2) }, p.index?.let { Fmt.num(it, 2) }, Fmt.date(p.tag * 86_400_000L)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        when {
            s == null -> EmptyHint(S.trLaedt)
            kacheln.isEmpty() -> { if (gesamt == null && !gesamtLaedt) EmptyHint(S.heatmapLeer) }
            else -> {
                var offen by remember { mutableStateOf(-1) }
                val uri = LocalUriHandler.current
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    kacheln.chunked(2).forEachIndexed { reihe, paar ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            paar.forEachIndexed { j, k ->
                                val i = reihe * 2 + j
                                KachelBarometer(k, i == offen, Modifier.weight(1f)) { offen = if (offen == i) -1 else i }
                            }
                            if (paar.size == 1) Spacer(Modifier.weight(1f))
                        }
                        // Einzelheiten der gewaehlten Kachel direkt unter ihrer Reihe
                        val k = kacheln.getOrNull(offen)
                        if (k != null && offen / 2 == reihe) {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surfaceContainer)
                                    .animateContentSize().padding(16.dp),
                            ) {
                                NamenText(k.name, style = MaterialTheme.typography.titleSmall)
                                vergleich(k.b)?.let { PunktText(it) }
                                Spacer(Modifier.height(6.dp))
                                k.mehr()
                            }
                            // Quelle direkt bei den Daten, bei alternative.me Bedingung fuer die Nutzung
                            QuellenZeile(k.quelle, onClick = k.link?.let { l -> { runCatching { uri.openUri(l) }; Unit } })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun KachelBarometer(k: StimmungsKachel, gewaehlt: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(Radius.card))
            .background(if (gewaehlt) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Skala(k.b.wert, Modifier.size(width = 72.dp, height = 44.dp))
        Spacer(Modifier.height(8.dp))
        NamenText(S.sbKachel(k.schluessel), style = MaterialTheme.typography.labelLarge)
        NamenText(k.zeile, style = MaterialTheme.typography.bodySmall, color = stufenFarbe(k.b.stufe))
        NamenText(S.sbAnbieter(k.schluessel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Platzhalter der Gesamtkachel mit gleichem Aufbau wie die zugeklappte Kachel, damit beim Erscheinen nichts springt
@Composable
private fun GesamtPlatzhalter() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(S.sgTitel, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            Box(Modifier.padding(top = 10.dp).size(width = 112.dp, height = 68.dp))
            Text(S.trLaedt, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 6.dp))
            Text(S.sgLaedtNoch, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

// Gewichte als ausgerichtete Liste, Name links, Wert rechtsbuendig; zwei Spalten nur, wenn jeder Eintrag in eine halbe Breite passt
@Composable
private fun GewichtListe(eintraege: List<Pair<String, String>>) {
    val stil = MaterialTheme.typography.bodySmall
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val breite = maxWidth
        // Messung nur neu, wenn sich Eintraege oder Breite aendern
        val zweiSpalten = remember(eintraege, breite) {
            val halb = (breite - 16.dp) / 2
            eintraege.isNotEmpty() && eintraege.all { (name, wert) ->
                val bName = with(dichte) { messer.measure(name, stil, maxLines = 1, softWrap = false).size.width.toDp() }
                val bWert = with(dichte) { messer.measure(wert, stil, maxLines = 1, softWrap = false).size.width.toDp() }
                bName + 8.dp + bWert <= halb
            }
        }
        if (zweiSpalten) {
            // Spaltenweise fuellen, damit die Liste von oben nach unten absteigend gelesen wird
            val teil = (eintraege.size + 1) / 2
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    for ((name, wert) in eintraege.take(teil)) GewichtZeile(name, wert)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    for ((name, wert) in eintraege.drop(teil)) GewichtZeile(name, wert)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                for ((name, wert) in eintraege) GewichtZeile(name, wert)
            }
        }
    }
}

// Eine Zeile der Gewichtsliste, Name darf umbrechen, Wert bleibt einzeilig am rechten Rand
@Composable
private fun GewichtZeile(name: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(wert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
    }
}

// Ruhige Zeile zum Auf- und Zuklappen eines Abschnitts in der Kachel
@Composable
private fun KachelAufklappen(titel: String, inhalt: @Composable () -> Unit) {
    var offen by remember(titel) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (offen) S.less else S.more) { offen = !offen }.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        AufklappPfeil(offen)
    }
    if (offen) inhalt()
}

// Gesamtstimmung (5.95) als Kachel ueber die volle Breite, mittig Titel, Barometer und Stufe.
// Ein Tipp auf das Barometer zeigt Zusammensetzung und Gewichte als Liste, kurze Stichpunkte und zum Aufklappen die Rechnung, die Barometer und alle Bestandteile; ein weiterer Tipp klappt zu.
@Composable
private fun GesamtKachel(g: Gesamtstimmung.Ergebnis, laedt: Boolean, umichAbruf: Long = 0L) {
    var offen by remember { mutableStateOf(false) }
    fun prozent(anteil: Double) = Fmt.pct(anteil * 100, 0)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card))
            .background(if (offen) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
            .animateContentSize().padding(16.dp),
    ) {
        // Kopf mittig, ein Tipp auf Barometer oder Titel klappt die Zusammensetzung auf und wieder zu
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card)).clickable(role = Role.Button, onClickLabel = if (offen) S.less else S.more) { offen = !offen },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(S.sgTitel, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            Skala(g.wert, Modifier.padding(top = 10.dp).size(width = 112.dp, height = 68.dp))
            // Wert und Stufe auf eigener Zeile ueber die volle Kachelbreite, ohne Umbruch oder Kuerzung
            Text(S.sbStufe(g.stufe.ordinal) + " · " + Fmt.num(g.wert, 0), style = MaterialTheme.typography.titleMedium, color = stufenFarbe(g.stufe), maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 6.dp))
            if (laedt) Text(S.sgLaedtNoch, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (offen) {
            Spacer(Modifier.height(12.dp))
            PunktText(S.sgAnzahl(g.vorhanden, g.moeglich))
            Spacer(Modifier.height(10.dp))
            Text(S.sgGewichte, style = MaterialTheme.typography.labelMedium)
            // Gewichte der Gruppen absteigend als ausgerichtete Liste
            GewichtListe(g.gruppen.sortedByDescending { it.second }.map { S.sgGruppe(it.first) to prozent(it.second) })
            Spacer(Modifier.height(10.dp))
            // Kurze Stichpunkte sichtbar, Details zum Aufklappen
            StichpunktText(S.sgKurz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            KachelAufklappen(S.sgRechnungTitel) {
                StichpunktText(S.sgRechnung, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            KachelAufklappen(S.sgBarometerTitel) {
                // Der erste Absatz von sbHinweis wiederholt die Skala aus sgKurz, daher nur der Rest
                StichpunktText(S.sbHinweis.substringAfter("\n\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            KachelAufklappen(S.sgTeileTitel) {
                Column {
                    // Je Gruppe Gewicht und Begruendung, darunter ihre Bestandteile in der Reihenfolge des Ergebnisses
                    for ((k, anteil) in g.gruppen) {
                        Row(Modifier.padding(top = 12.dp)) {
                            NamenText(S.sgGruppe(k), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(prozent(anteil), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
                        }
                        Text(S.sgGruppeWarum(k), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        for (t in g.teile.filter { it.gruppe == k }) {
                            // Name in eigener Zeile, er darf an Wortgrenzen umbrechen und enthaelt keinen Wert
                            Text(S.sgTeil(t.schluessel), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                            Text(Fmt.num(t.wert, 0) + " · " + S.sbStufe(Stimmungsbarometer.stufe(t.wert).ordinal), style = MaterialTheme.typography.bodyMedium, color = stufenFarbe(Stimmungsbarometer.stufe(t.wert)), maxLines = 1, softWrap = false)
                            PunktText(listOfNotNull(sgRohText(t), S.sgGewicht(Fmt.pct(t.anteil * 100, 1)), t.stand.takeIf { it > 0 }?.let { S.sgStand(Fmt.date(it)) }).joinToString(" · "))
                            Text(S.sgMethode(t.schluessel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (g.fehlen.isNotEmpty()) Text(S.sgFehlen(g.fehlen.joinToString(", ") { S.sgTeil(it) }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            Text(S.sgQuellen, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            // FRED verlangt bei der Verbraucherbefragung das woertliche Zitat mit Abrufdatum, direkt darunter mit Link
            if (umichAbruf > 0 && g.teile.any { it.schluessel == "umich" }) {
                val uri = LocalUriHandler.current
                QuellenZeile(S.sgZitatUmich(Fmt.date(umichAbruf)), onClick = { runCatching { uri.openUri(Gesamtstimmung.UMICH_SEITE) }; Unit })
            }
        }
    }
}

// Rohwert eines Bestandteils mit Einheit; null, wenn es keinen gibt oder der Wert selbst schon die Zahl ist
private fun sgRohText(t: Gesamtstimmung.Bestandteil): String? {
    val roh = t.roh ?: return null
    return when (t.schluessel) {
        "vix", "vvix", "move", "umich" -> S.sgRoh(t.schluessel, Fmt.num(roh, 1))
        "vix_struktur", "stress", "hochzins" -> S.sgRoh(t.schluessel, Fmt.num(roh, 2))
        "trend_sp500", "trend_dax", "sicher", "cot_sp500", "cot_nasdaq", "margin" -> S.sgRoh(t.schluessel, Fmt.signedPct(roh, 1))
        "breite200" -> S.sgRoh(t.schluessel, Fmt.pct(roh, 0))
        "hochtief" -> S.sgHochTief(roh.toInt(), (t.roh2 ?: 0.0).toInt())
        "zinskurve", "sahm" -> S.sgRoh(t.schluessel, (if (roh >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(roh), 2))
        else -> null
    }
}

// null: wird noch geladen.
@Composable
internal fun FruehwarnInhalt(zeichen: List<Fruehwarnung.Zeichen>?) {
    Column(Modifier.fillMaxWidth()) {
        MarktInfoKopf(S.fwTitel, S.fwHinweis)
        when {
            zeichen == null -> EmptyHint(S.trLaedt)
            zeichen.isEmpty() -> EmptyHint(S.heatmapLeer)
            else -> {
                val rot = zeichen.count { it.ampel == Fruehwarnung.Ampel.ROT }
                val gelb = zeichen.count { it.ampel == Fruehwarnung.Ampel.GELB }
                Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (z in zeichen) {
                        AmpelPunkt(fwAmpelZeichen(z.ampel), fwAmpelFarbe(z.ampel), Modifier.padding(end = 6.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(S.fwZusammen(rot, gelb, zeichen.size), style = MaterialTheme.typography.titleSmall)
                }
                var offen by remember { mutableStateOf<String?>(null) }
                for (z in zeichen) {
                    val auf = offen == z.schluessel
                    Column(Modifier.fillMaxWidth().clickable { offen = if (auf) null else z.schluessel }.animateContentSize()
                        .padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AmpelPunkt(fwAmpelZeichen(z.ampel), fwAmpelFarbe(z.ampel))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(S.fwName(z.schluessel), style = MaterialTheme.typography.titleSmall)
                                Text(S.fwLage(z.schluessel, z.ampel.ordinal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(fwWert(z), style = MaterialTheme.typography.titleSmall)
                        }
                        if (auf) {
                            Spacer(Modifier.height(6.dp))
                            VerlaufFrei(z.verlauf, if (z.schluessel == Fruehwarnung.VIX_STRUKTUR) 1.0 else if (z.schluessel == Fruehwarnung.VIX) 20.0 else 0.0)
                            Text(S.fwErklaerung(z.schluessel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(S.fwQuelle(z.schluessel) + if (z.abgerufen > 0) " " + S.blsHinweis(Fmt.date(z.abgerufen)) else "",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

// Wert eines Zeichens in der passenden Einheit.
private fun fwWert(z: Fruehwarnung.Zeichen): String = when (z.schluessel) {
    Fruehwarnung.ZINSKURVE, Fruehwarnung.SAHM -> (if (z.wert >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(z.wert), 2) + " " + S.fwPunkte
    Fruehwarnung.VIX -> Fmt.num(z.wert, 1)
    Fruehwarnung.VIX_STRUKTUR -> Fmt.num(z.wert, 2)
    Fruehwarnung.STRESS -> (if (z.wert >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(z.wert), 2)
    Fruehwarnung.HOCHZINS -> Fmt.num(z.wert, 2) + " " + S.fwPunkte
    else -> (if (z.wert >= 0) "+" else "−") + Fmt.pct(kotlin.math.abs(z.wert), 1)
}

internal fun fwAmpelZeichen(a: Fruehwarnung.Ampel): String = when (a) {
    Fruehwarnung.Ampel.GRUEN -> AmpelZeichen.GUT
    Fruehwarnung.Ampel.GELB -> AmpelZeichen.ACHTUNG
    Fruehwarnung.Ampel.ROT -> AmpelZeichen.KRITISCH
}

@Composable
internal fun fwAmpelFarbe(a: Fruehwarnung.Ampel): Color = when (a) {
    Fruehwarnung.Ampel.GRUEN -> LocalSignal.current.up
    Fruehwarnung.Ampel.GELB -> LocalSignal.current.warn
    Fruehwarnung.Ampel.ROT -> LocalSignal.current.down
}

// Verlauf mit eigener Skala und einer Linie bei schwelle.
@Composable
internal fun VerlaufFrei(punkte: List<Pair<Long, Double>>, schwelle: Double) {
    if (punkte.size < 2) return
    val farbe = MaterialTheme.colorScheme.onSurface
    val linie = MaterialTheme.colorScheme.outlineVariant
    val lo = minOf(punkte.minOf { it.second }, schwelle)
    val hi = maxOf(punkte.maxOf { it.second }, schwelle).let { if (it - lo < 1e-9) lo + 1 else it }
    Canvas(Modifier.fillMaxWidth().height(52.dp).padding(vertical = 4.dp)) {
        val t0 = punkte.first().first.toFloat()
        val t1 = punkte.last().first.toFloat().coerceAtLeast(t0 + 1f)
        fun x(t: Long) = (t - t0) / (t1 - t0) * size.width
        fun y(v: Double) = size.height * (1f - ((v - lo) / (hi - lo)).toFloat())
        drawLine(linie, Offset(0f, y(schwelle)), Offset(size.width, y(schwelle)), strokeWidth = 1.5f)
        val p = Path()
        punkte.forEachIndexed { i, (t, v) -> if (i == 0) p.moveTo(x(t), y(v)) else p.lineTo(x(t), y(v)) }
        drawPath(p, farbe, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun vergleich(b: Barometer): String? = listOfNotNull(
    b.vortag?.let { S.sbVortag + " " + Fmt.num(it, 0) },
    b.woche?.let { S.sbWoche + " " + Fmt.num(it, 0) },
    b.monat?.let { S.sbMonat + " " + Fmt.num(it, 0) },
    b.jahr?.let { S.year + " " + Fmt.num(it, 0) },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

@Composable
private fun stufenFarbe(s: Stufe): Color {
    val sig = LocalSignal.current
    return when (s) {
        Stufe.EXTREME_ANGST, Stufe.ANGST -> sig.down
        Stufe.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        Stufe.GIER, Stufe.EXTREME_GIER -> sig.up
    }
}

// Halbrunde Skala von 0 (links, Angst) bis 100 (rechts, Gier) mit Zeiger.
@Composable
private fun Skala(wert: Double, modifier: Modifier) {
    val sig = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val zeiger = MaterialTheme.colorScheme.onSurface
    val farben = listOf(sig.down, sig.down.copy(alpha = 0.55f), grau.copy(alpha = 0.6f), sig.up.copy(alpha = 0.55f), sig.up)
    // Grenzen der Stufen wie bei CNN
    val grenzen = listOf(0.0, 25.0, 45.0, 55.0, 75.0, 100.0)
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val dicke = size.height * 0.16f
            val r = minOf(size.width / 2f, size.height) - dicke / 2f
            val mitte = Offset(size.width / 2f, size.height - dicke / 4f)
            val ecke = Offset(mitte.x - r, mitte.y - r)
            for (i in 0 until 5) {
                val von = 180f + (grenzen[i] / 100.0 * 180.0).toFloat() + 1.5f
                val weite = ((grenzen[i + 1] - grenzen[i]) / 100.0 * 180.0).toFloat() - 3f
                drawArc(farben[i], von, weite, useCenter = false, topLeft = ecke, size = Size(2 * r, 2 * r), style = Stroke(dicke, cap = StrokeCap.Butt))
            }
            val w = (PI * (1.0 - wert.coerceIn(0.0, 100.0) / 100.0)).toFloat()
            val spitze = Offset(mitte.x + cos(w) * r * 0.82f, mitte.y - sin(w) * r * 0.82f)
            drawLine(zeiger, mitte, spitze, strokeWidth = dicke * 0.45f, cap = StrokeCap.Round)
            drawCircle(zeiger, radius = dicke * 0.45f, center = mitte)
        }
    }
}

// Verlauf als duenne Linie von 0 bis 100.
@Composable
private fun Verlaufslinie(punkte: List<Pair<Long, Double>>) {
    if (punkte.size < 2) return
    val farbe = MaterialTheme.colorScheme.onSurface
    val mitte = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(44.dp).padding(vertical = 4.dp)) {
        val t0 = punkte.first().first.toFloat()
        val t1 = punkte.last().first.toFloat().coerceAtLeast(t0 + 1f)
        fun x(t: Long) = (t - t0) / (t1 - t0) * size.width
        fun y(v: Double) = size.height * (1f - (v.coerceIn(0.0, 100.0) / 100.0).toFloat())
        drawLine(mitte, Offset(0f, y(50.0)), Offset(size.width, y(50.0)), strokeWidth = 1f)
        val p = Path()
        punkte.forEachIndexed { i, (t, v) -> if (i == 0) p.moveTo(x(t), y(v)) else p.lineTo(x(t), y(v)) }
        drawPath(p, farbe, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun WsbListe(titel: String, werte: List<Stimmungsbarometer.WsbWert>, aenderungen: Map<String, Double>, onOpen: (String) -> Unit) {
    Text(titel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    // Je Wert Kuerzel und Kursaenderung ueber fuenf Handelstage (5.94), damit Ton und Kurs nebeneinander stehen;
    // Eintraege umbrechen als Ganzes in die naechste Zeile
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth()) {
        for (w in werte) Row(
            Modifier.clickable { onOpen(w.ticker) }.padding(end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(w.ticker, style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.link, maxLines = 1, softWrap = false)
            aenderungen[w.ticker.uppercase()]?.let { c ->
                Text(
                    "\u00A0" + Fmt.signedPct(c, 0), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false,
                    color = if (c >= 0) LocalSignal.current.up else LocalSignal.current.down,
                )
            }
        }
    }
}
