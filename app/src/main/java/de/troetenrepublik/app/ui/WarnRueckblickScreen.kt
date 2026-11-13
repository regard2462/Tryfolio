package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Blasen
import de.troetenrepublik.app.engine.Fruehindikator
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.WarnRueckblick
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.S
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ln

// Rueckblick „Haette man es sehen koennen?“ [0.73]: Einbrueche der Indizes, wie die Warnzeichen der App davor standen,
// Bilanz je Zeichen, Kapitalkurve „Warnung befolgen gegen Halten“. Die Daten kommen aus der mitgelieferten Datei
// (engine/WarnRueckblick.datei); neben jedem Zeichen steht sein heutiger Stand aus den Daten, die die App ohnehin laedt.

private fun monat(tag: Int): String = LocalDate.ofEpochDay(tag.toLong()).let { "${it.year}-${it.monthValue.toString().padStart(2, '0')}" }

private fun zahl(w: Double, stellen: Int = 1): String = Fmt.num(w, stellen)

// Kleine Zeile unter einem Warnzeichen: Treffer und Fehlalarme des Rueckblicks. Sie steht ueber die volle Breite unter Titel
// und Wert (nie in der schmalen Spalte neben dem Wert) und bricht bei Bedarf um, statt abgeschnitten zu werden.
@Composable
internal fun WarnKurzzeile(zeichen: String, index: String = "sp500", modifier: Modifier = Modifier) {
    val b = remember(zeichen, index) { WarnRueckblick.bilanz(WarnRueckblick.datei, index, zeichen) } ?: return
    Text(
        S.wrKurzzeile(b.treffer, b.auswertbar, b.fehlalarme), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier,
    )
}

// Eintrag auf der Seite Stimmung, oeffnet das Blatt.
@Composable
internal fun WarnRueckblickEintrag(state: AppState) {
    if (remember { WarnRueckblick.datei } == null) return
    var offen by remember { mutableStateOf(false) }
    NavRow(S.wrTitel, S.wrUntertitel) { offen = true }
    if (offen) WarnRueckblickSheet(state) { offen = false }
}

// Heutiger Stand je Zeichen als Ampelcode (1 gruen, 2 gelb, 3 rot); fehlt ein Zeichen, hat die App es heute nicht.
private fun heutigeStaende(
    fw: List<Fruehwarnung.Zeichen>, bl: List<Blasen.Indikator>, index: String, cli: Fruehindikator.Phase?, antraege: Fruehwarnung.Ampel?,
): Map<String, Int> {
    val m = HashMap<String, Int>()
    antraege?.let { m[WarnRueckblick.ERSTANTRAEGE] = it.ordinal + 1 }
    cli?.let { m[WarnRueckblick.FRUEHINDIKATOR] = when (it) { Fruehindikator.Phase.ABSCHWUNG -> 3; Fruehindikator.Phase.ABSCHWAECHUNG -> 2; else -> 1 } }
    fun code(a: Fruehwarnung.Ampel) = a.ordinal + 1
    for (z in fw) when (z.schluessel) {
        Fruehwarnung.ZINSKURVE -> m[WarnRueckblick.ZINSKURVE] = code(z.ampel)
        Fruehwarnung.SAHM -> m[WarnRueckblick.SAHM] = code(z.ampel)
        Fruehwarnung.VIX -> m[WarnRueckblick.VIX] = code(z.ampel)
        Fruehwarnung.VIX_STRUKTUR -> m[WarnRueckblick.VIX_STRUKTUR] = code(z.ampel)
        Fruehwarnung.STRESS -> m[WarnRueckblick.STRESS] = code(z.ampel)
        Fruehwarnung.HOCHZINS -> m[WarnRueckblick.HOCHZINS] = code(z.ampel)
        Fruehwarnung.TREND_SP500 -> if (index != "dax") m[WarnRueckblick.TREND] = code(z.ampel)
        Fruehwarnung.TREND_DAX -> if (index == "dax") m[WarnRueckblick.TREND] = code(z.ampel)
    }
    for (i in bl) when (i.schluessel) {
        Blasen.CAPE -> m[WarnRueckblick.CAPE] = code(i.ampel)
        Blasen.KGV -> m[WarnRueckblick.KGV] = code(i.ampel)
        Blasen.PRAEMIE -> m[WarnRueckblick.PRAEMIE] = code(i.ampel)
        Blasen.TREND -> m[WarnRueckblick.HITZE] = code(i.ampel)
        Blasen.MARGIN -> m[WarnRueckblick.MARGIN] = code(i.ampel)
        Blasen.BUFFETT -> m[WarnRueckblick.BUFFETT] = code(i.ampel)
    }
    return m
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WarnRueckblickSheet(state: AppState, onDismiss: () -> Unit) {
    val datei = remember { WarnRueckblick.datei }
    var fw by remember { mutableStateOf<List<Fruehwarnung.Zeichen>>(emptyList()) }
    var bl by remember { mutableStateOf<List<Blasen.Indikator>>(emptyList()) }
    LaunchedEffect(Unit) { fw = runCatching { state.fruehwarnung() }.getOrNull().orEmpty() }
    LaunchedEffect(Unit) { bl = runCatching { state.blasen().indikatoren }.getOrNull().orEmpty() }
    var antraege by remember { mutableStateOf<Fruehwarnung.Ampel?>(null) }
    LaunchedEffect(Unit) {
        antraege = runCatching { state.fruehSignale(listOf(Fruehindikatoren.ERSTANTRAEGE))[Fruehindikatoren.ERSTANTRAEGE]?.ampel }.getOrNull()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text(S.wrTitel, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
            if (datei == null) EmptyHint(S.heatmapLeer) else WarnRueckblickInhalt(state, datei, fw, bl, antraege)
        }
    }
}

@Composable
private fun WarnRueckblickInhalt(
    state: AppState, datei: WarnRueckblick.Datei, fw: List<Fruehwarnung.Zeichen>, bl: List<Blasen.Indikator>, antraege: Fruehwarnung.Ampel?,
) {
    var indexId by remember { mutableStateOf(datei.indizes.first().id) }
    val ix = datei.indizes.firstOrNull { it.id == indexId } ?: datei.indizes.first()
    var gewaehlt by remember { mutableStateOf<String?>(null) }
    var ereignis by remember { mutableStateOf<Int?>(null) }
    val cli = remember(indexId) {
        runCatching { Fruehindikator.phase(state.fruehindikatorGespeichert(), if (indexId == "dax") "DEU" else "USA", java.time.YearMonth.now()) }.getOrNull()
    }
    val heute = remember(fw, bl, indexId, cli, antraege) { heutigeStaende(fw, bl, indexId, cli, antraege) }
    val infos = remember(datei) { datei.zeichen.associateBy { it.id } }
    val mitDaten = remember(ix) { ix.zeichen.filter { it.auswertbar > 0 } }
    val aktuell = gewaehlt?.takeIf { g -> mitDaten.any { it.id == g } } ?: mitDaten.firstOrNull { it.id == WarnRueckblick.ZINSKURVE }?.id ?: mitDaten.firstOrNull()?.id
    Column(Modifier.fillMaxWidth()) {
        HinweisInfo(S.wrFest, kurz = hinweisText("hist"))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in datei.indizes) Chip(S.wrIndex(i.id), i.id == indexId) { indexId = i.id; ereignis = null }
        }
        Text(
            S.wrStand(Fmt.date(LocalDate.parse(datei.stand).toEpochDay() * 86_400_000L), Fmt.date(ix.bis.toLong() * 86_400_000L)),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        // (a) Zeitleiste und Einbrueche
        MarktInfoKopf(S.wrZeitleiste, S.wrInfo)
        val einbrueche = ix.ereignisse.filter { it.stufe == 20 }
        val rueckgaenge = ix.ereignisse.filter { it.stufe == 10 }
        Zeitleiste(ix, ereignis) { ereignis = if (ereignis == it) null else it }
        for (e in einbrueche) EreignisZeile(datei, ix, e, ereignis == e.hoch, infos) { ereignis = if (ereignis == e.hoch) null else e.hoch }
        if (rueckgaenge.isNotEmpty()) {
            var auf by remember(indexId) { mutableStateOf(false) }
            SectionHeader(S.wrRueckgaenge, chevron = true, onClick = { auf = !auf })
            if (auf) for (e in rueckgaenge) EreignisZeile(datei, ix, e, ereignis == e.hoch, infos) { ereignis = if (ereignis == e.hoch) null else e.hoch }
        }

        // (b) Bilanz je Zeichen mit heutigem Stand, (d) ein Satz zum gewaehlten Zeichen
        SectionHeader(S.wrTabelle)
        for (b in mitDaten) {
            val info = infos[b.id]
            val stand = heute[b.id]
            val abgeleitet = if (b.id.startsWith(WarnRueckblick.ZUSAMMEN)) zusammenHeute(heute, b.id, infos) else stand
            val auf = aktuell == b.id
            Column(Modifier.fillMaxWidth().clickable { gewaehlt = b.id }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(S.wrZeichen(b.id), style = MaterialTheme.typography.titleSmall)
                        // Zwei feste Zeilen: erkannt, darunter Warnungen und Vorlauf
                        for (z in S.wrZeile(b.treffer, b.auswertbar, b.fehlalarme, b.vorlaufMittel?.let { Math.round(it).toInt() }).split('\n')) Text(
                            z, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    val code = abgeleitet ?: 0
                    val warnt = info != null && code >= info.warnAb
                    Column(horizontalAlignment = Alignment.End) {
                        if (code > 0) AmpelPunkt(
                            if (warnt) AmpelZeichen.KRITISCH else AmpelZeichen.GUT,
                            if (warnt) LocalSignal.current.down else LocalSignal.current.up,
                        )
                        Text(
                            S.wrHeute(if (code == 0) 0 else if (warnt) 2 else 1), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
                        )
                    }
                }
                if (auf) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        S.wrSatz(b.treffer, b.auswertbar, b.fehlalarme, b.phasen, b.spanneVon, b.spanneBis, Math.round(b.zeitAnteil * 100).toInt()), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    info?.let { S.wrHinweisZeichen(it.id, it.ersatz, it.revidiert, it.eigeneRegel) }?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // (c) Kapitalkurve des gewaehlten Zeichens
        val b = mitDaten.firstOrNull { it.id == aktuell }
        if (b?.folgen != null && b.halten != null && b.kurve.size > 2) {
            SectionHeader(S.wrKurve)
            Text(S.wrZeichen(b.id), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp))
            KapitalKurve(ix, b)
            VergleichTabelle(
                listOf(VergleichSpalte(S.wrBefolgen, Kategorie.farbe(0)), VergleichSpalte(S.wrHalten, MaterialTheme.colorScheme.onSurfaceVariant)),
                listOf(
                    VergleichZeile(S.toolPerYear, listOf(Fmt.pct(b.folgen.rendite, 1), Fmt.pct(b.halten.rendite, 1))),
                    VergleichZeile(S.maxDrawdown, listOf(Fmt.signedPct(-abs(b.folgen.maxRueck), 0), Fmt.signedPct(-abs(b.halten.maxRueck), 0))),
                    VergleichZeile(S.wrWechsel, listOf(b.folgen.wechsel.toString(), "–")),
                ),
            )
            Text(S.wrKosten, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }

        if (datei.fehlend.isNotEmpty()) {
            Zwischenkopf(S.wrFehlendTitel)
            Text(
                S.wrFehlend(datei.fehlend.joinToString(", ") { S.wrZeichen(it.id) }), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        Text(
            S.blsHinweis(Fmt.date(LocalDate.parse(datei.stand).toEpochDay() * 86_400_000L)), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        QuellenAnhang(
            listOf(
                QuelleLink("Robert J. Shiller", "http://www.econ.yale.edu/~shiller/data.htm"),
                QuelleLink("OECD (CC BY 4.0)", "https://www.oecd.org"),
                QuelleLink("Federal Reserve Bank of Chicago (NFCI)", "https://www.chicagofed.org/research/data/nfci/current-data"),
                QuelleLink("BLS", "https://www.bls.gov"),
                QuelleLink("Federal Reserve Board (Z.1, Excess Bond Premium)", "https://www.federalreserve.gov/releases/z1/"),
                QuelleLink("BEA", "https://www.bea.gov"),
                QuelleLink("U.S. Department of Labor", "https://oui.doleta.gov/unemploy/claims.asp"),
                nyFedQuelle(),
            ),
        )
    }
}

// Zusammenschau heute: wie viele der zaehlenden Zeichen warnen, sofern genug heutige Stande vorliegen
private fun zusammenHeute(heute: Map<String, Int>, id: String, infos: Map<String, WarnRueckblick.ZeichenInfo>): Int? {
    val k = id.substringAfterLast('_').toIntOrNull() ?: return null
    val bekannt = WarnRueckblick.ZAEHLT.filter { heute.containsKey(it) }
    if (bekannt.size < WarnRueckblick.MIN_VERFUEGBAR) return null
    val warnt = bekannt.count { (heute[it] ?: 0) >= (infos[it]?.warnAb ?: 3) }
    return if (warnt >= k) 3 else 1
}

// Achse von Hoch zu Hoch; je Ereignis ein Balken von seinem Hoch bis zum Tief, Hoehe nach Tiefe. Antippen waehlt aus.
@Composable
private fun Zeitleiste(ix: WarnRueckblick.IndexErgebnis, gewaehlt: Int?, onWahl: (Int) -> Unit) {
    val signal = LocalSignal.current
    val achse = MaterialTheme.colorScheme.outlineVariant
    val schrift = MaterialTheme.colorScheme.onSurfaceVariant
    val messer = rememberTextMeasurer()
    val stil = TextStyle(fontSize = 10.sp, color = schrift)
    val rzStil = rezessionsStil()
    Canvas(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(120.dp).pointerInput(ix.id) {
            detectTapGestures { p ->
                val w = size.width.toFloat()
                val spanne = (ix.bis - ix.von).toFloat()
                val tag = ix.von + p.x / w * spanne
                // nächstes Ereignis, dessen Balken (mit Rand) getroffen wird
                val rand = 12.dp.toPx() / w * spanne
                ix.ereignisse.filter { tag >= it.hoch - rand && tag <= it.tief + rand }
                    .minByOrNull { abs((it.hoch + it.tief) / 2f - tag) }?.let { onWahl(it.hoch) }
            }
        },
    ) {
        val w = size.width
        val h = size.height - 18.dp.toPx()
        val spanne = (ix.bis - ix.von).toFloat()
        fun x(t: Int) = (t - ix.von) / spanne * w
        val maxTiefe = (ix.ereignisse.maxOfOrNull { it.tiefe } ?: 50.0).coerceAtLeast(30.0)
        // US-Rezessionen hinter den Balken, Beschriftung am oberen Rand
        rezessionsBaender(
            ix.von * WarnRueckblick.TAG_MS, ix.bis * WarnRueckblick.TAG_MS, { t -> (t / WarnRueckblick.TAG_MS - ix.von) / spanne * w }, 0f, h, rzStil, messer, 0f,
        )
        drawLine(achse, Offset(0f, h), Offset(w, h), 1.dp.toPx())
        // Jahrzehnte
        val von = LocalDate.ofEpochDay(ix.von.toLong()).year
        val bis = LocalDate.ofEpochDay(ix.bis.toLong()).year
        var jahr = (von / 10 + 1) * 10
        while (jahr <= bis) {
            val tx = x(LocalDate.of(jahr, 1, 1).toEpochDay().toInt())
            drawLine(achse, Offset(tx, h), Offset(tx, h + 4.dp.toPx()), 1.dp.toPx())
            val t = messer.measure(jahr.toString(), stil)
            drawText(t, topLeft = Offset((tx - t.size.width / 2f).coerceIn(0f, w - t.size.width), h + 4.dp.toPx()))
            jahr += 20
        }
        for (e in ix.ereignisse) {
            val bx = x(e.hoch)
            val bw = (x(e.tief) - bx).coerceAtLeast(3.dp.toPx())
            val bh = (e.tiefe / maxTiefe * (h - 14.dp.toPx())).toFloat()
            val ausgewaehlt = gewaehlt == e.hoch
            val farbe = if (e.stufe == 20) signal.down else signal.down.copy(alpha = 0.45f)
            drawRect(farbe.copy(alpha = if (ausgewaehlt || gewaehlt == null) farbe.alpha else farbe.alpha * 0.5f), Offset(bx, 0f + (h - bh)), Size(bw, bh))
            if (ausgewaehlt) {
                val t = messer.measure("−" + zahl(e.tiefe, 0) + " %", stil.copy(color = schrift))
                drawText(t, topLeft = Offset((bx + bw / 2 - t.size.width / 2f).coerceIn(0f, w - t.size.width), (h - bh - t.size.height - 1.dp.toPx()).coerceAtLeast(0f)))
            }
        }
    }
}

// Eine Zeile je Ereignis; aufgeklappt die Matrix der Zeichen und die Wirkungskette.
@Composable
private fun EreignisZeile(
    datei: WarnRueckblick.Datei, ix: WarnRueckblick.IndexErgebnis, e: WarnRueckblick.Ereignis, auf: Boolean,
    infos: Map<String, WarnRueckblick.ZeichenInfo>, onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                S.toolDrawdownRange(monat(e.hoch), monat(e.tief)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                maxLines = 1, softWrap = false,
            )
            Text("−" + zahl(e.tiefe, 0) + " %", style = MaterialTheme.typography.titleSmall, color = LocalSignal.current.down, maxLines = 1, softWrap = false)
        }
        if (auf) {
            Text(
                S.wrEreignis(monat(e.hoch), monat(e.tief), "−" + zahl(e.tiefe, 0) + " %", e.monate), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            Matrix(ix, e)
            Text(S.wrLegende, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            val kette = datei.ketten.firstOrNull { it.hoch == monat(e.hoch) }
            Text(S.wrKetteTitel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
            if (kette == null) {
                Text(S.wrKeineKette, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(if (I18n.lang == Lang.DE) kette.de else kette.en, style = MaterialTheme.typography.bodySmall)
                val oeffnen = LocalUriHandler.current
                Text(
                    S.wrBeleg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp).textKlick(0.dp) { runCatching { oeffnen.openUri(kette.quelle) } },
                )
            }
        }
    }
}

// Matrix: je Zeichen eine Zeile, je Stichtag (12, 6, 3, 1 Monat vor dem Hoch) ein Feld
@Composable
private fun Matrix(ix: WarnRueckblick.IndexErgebnis, e: WarnRueckblick.Ereignis) {
    val signal = LocalSignal.current
    val rand = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        for (m in WarnRueckblick.STICHTAGE) {
            Text(
                m.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, softWrap = false,
            )
        }
    }
    for (b in ix.zeichen) {
        val s = e.stand[b.id] ?: continue
        if (s.all { it == '-' }) continue
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.wrZeichen(b.id), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            for (c in s) {
                Canvas(Modifier.width(34.dp).height(18.dp)) {
                    val kante = 14.dp.toPx()
                    val o = Offset((size.width - kante) / 2, (size.height - kante) / 2)
                    when (c) {
                        'w' -> drawRect(signal.down, o, Size(kante, kante))
                        'r' -> drawRect(rand, o, Size(kante, kante), style = Stroke(1.5.dp.toPx()))
                        else -> drawCircle(rand.copy(alpha = 0.6f), 2.dp.toPx(), Offset(size.width / 2, size.height / 2))
                    }
                }
            }
        }
    }
}

// Kapitalkurve: Warnung befolgen und Halten (log. Achse, Start 100), Warnphasen als Band, Fingerlesung per Wischen.
@Composable
private fun KapitalKurve(ix: WarnRueckblick.IndexErgebnis, b: WarnRueckblick.Bilanz) {
    val tage = remember(ix, b) { ix.kurvenTage.drop(b.kurveAb).take(b.kurve.size) }
    val folgen = b.kurve
    val halten = remember(ix, b) {
        val basis = ix.halten.getOrNull(b.kurveAb) ?: 100.0
        ix.halten.drop(b.kurveAb).take(b.kurve.size).map { it / basis * 100 }
    }
    if (tage.size < 3 || halten.size != folgen.size) return
    var lese by remember(ix, b) { mutableStateOf<Int?>(null) }
    val farbeF = Kategorie.farbe(0)
    val farbeH = MaterialTheme.colorScheme.onSurfaceVariant
    val band = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val achse = MaterialTheme.colorScheme.outlineVariant
    val grund = MaterialTheme.colorScheme.background
    val messer = rememberTextMeasurer()
    val stil = TextStyle(fontSize = 10.sp)
    val rzStil = rezessionsStil()
    val lo = ln(minOf(folgen.min(), halten.min()).coerceAtLeast(1.0))
    val hi = ln(maxOf(folgen.max(), halten.max()))
    // Fester Aufbau: Monat links, rechts je Reihe Kurzname und Wert
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val stilZeile = MaterialTheme.typography.labelMedium
        Text(lese?.let { monat(tage[it]) } ?: " ", style = stilZeile, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
        lese?.let { i ->
            Text(S.wrBefolgen + " " + Fmt.num(folgen[i], 0), style = stilZeile, color = farbeF, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(12.dp))
            Text(S.wrHalten + " " + Fmt.num(halten[i], 0), style = stilZeile, color = farbeH, maxLines = 1, softWrap = false)
        }
    }
    Canvas(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(192.dp).pointerInput(ix.id, b.id) {
            fun auf(x: Float) = ((x / size.width) * (tage.size - 1)).toInt().coerceIn(0, tage.size - 1)
            detectHorizontalDragGestures(
                onDragStart = { lese = auf(it.x) },
                onDragEnd = { lese = null },
                onDragCancel = { lese = null },
                onHorizontalDrag = { c, _ -> lese = auf(c.position.x) },
            )
        },
    ) {
        val w = size.width
        // Unter der Achse: Leiste der US-Rezessionen (die Flaeche ueber der Achse tragen die Warnphasen), darunter die Jahre
        val h = size.height - 28.dp.toPx()
        val t0 = tage.first().toFloat()
        val spanne = (tage.last() - tage.first()).toFloat()
        fun x(t: Int) = (t - t0) / spanne * w
        rezessionsBaender(
            tage.first() * WarnRueckblick.TAG_MS, tage.last() * WarnRueckblick.TAG_MS, { t -> x((t / WarnRueckblick.TAG_MS).toInt()) },
            h + 3.dp.toPx(), h + 8.dp.toPx(), rzStil, messer, h + 10.dp.toPx(),
        )
        fun y(v: Double) = (h - ((ln(v.coerceAtLeast(1.0)) - lo) / (hi - lo).coerceAtLeast(1e-6) * h)).toFloat()
        // Warnphasen als Band am unteren Rand
        var i = 0
        while (i + 1 < b.phasenListe.size) {
            val a = x(b.phasenListe[i].coerceAtLeast(tage.first())).coerceIn(0f, w)
            val z = x(b.phasenListe[i + 1].coerceAtLeast(tage.first())).coerceIn(0f, w)
            if (z > a) drawRect(band, Offset(a, 0f), Size(z - a, h))
            i += 2
        }
        drawLine(achse, Offset(0f, h), Offset(w, h), 1.dp.toPx())
        fun linie(werte: List<Double>, farbe: Color, breite: Float) {
            val p = Path()
            werte.forEachIndexed { k, v -> if (k == 0) p.moveTo(x(tage[k]), y(v)) else p.lineTo(x(tage[k]), y(v)) }
            drawPath(p, farbe, style = Stroke(breite))
        }
        linie(halten, farbeH, 1.5.dp.toPx())
        linie(folgen, farbeF, 2.dp.toPx())
        // Beschriftung am Linienende und Jahre an der Achse
        // Beide Beschriftungen halten mindestens eine Zeilenhoehe Abstand, auch wenn beide Linien am Rand enden
        val tF = messer.measure(S.wrFolgen + " " + Fmt.num(folgen.last(), 0), stil.copy(color = farbeF))
        val tH = messer.measure(S.wrHalten + " " + Fmt.num(halten.last(), 0), stil.copy(color = farbeH))
        val vorn = folgen.last() >= halten.last()
        val oben = if (vorn) tF else tH
        val unten = if (vorn) tH else tF
        val luecke = oben.size.height + 2.dp.toPx()
        var yOben = (y(if (vorn) folgen.last() else halten.last()) - oben.size.height / 2f).coerceIn(0f, h - oben.size.height)
        var yUnten = (y(if (vorn) halten.last() else folgen.last()) - unten.size.height / 2f).coerceIn(0f, h - unten.size.height)
        if (yUnten - yOben < luecke) {
            yUnten = yOben + luecke
            if (yUnten > h - unten.size.height) {
                yUnten = h - unten.size.height
                yOben = (yUnten - luecke).coerceAtLeast(0f)
            }
        }
        // Hintergrund in Blattfarbe, damit die Linien die Werte nicht durchkreuzen
        val rand = 3.dp.toPx()
        for ((t, ty) in listOf(oben to yOben, unten to yUnten)) {
            drawRect(grund.copy(alpha = 0.85f), Offset(w - t.size.width - rand, ty), Size(t.size.width + rand, t.size.height.toFloat()))
            drawText(t, topLeft = Offset(w - t.size.width, ty))
        }
        for (jahr in listOf(LocalDate.ofEpochDay(tage.first().toLong()).year, LocalDate.ofEpochDay(tage.last().toLong()).year)) {
            val tx = x(LocalDate.of(jahr, 7, 1).toEpochDay().toInt().coerceIn(tage.first(), tage.last()))
            val t = messer.measure(jahr.toString(), stil.copy(color = farbeH))
            drawText(t, topLeft = Offset((tx - t.size.width / 2f).coerceIn(0f, w - t.size.width), h + 10.dp.toPx()))
        }
        lese?.let { k ->
            val lx = x(tage[k])
            drawLine(farbeH, Offset(lx, 0f), Offset(lx, h), 1.dp.toPx())
            drawCircle(farbeF, 3.dp.toPx(), Offset(lx, y(folgen[k])))
            drawCircle(farbeH, 3.dp.toPx(), Offset(lx, y(halten[k])))
        }
    }
}
