package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Handelsstil
import de.troetenrepublik.app.engine.Rotation
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.engine.Taktik
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Werkzeug Momentum-Rotation (0.78): Kernzahl gegen Kaufen und Halten, Verlauf mit Vergleichsindex, die heutige
// Auswahl, darunter eingeklappt Werte, Regel und die Pruefungen des Strategietests. Rechnung in engine/Rotation.kt.

private const val JAHR_MS = 365L * 86_400_000L

// Wahl und geladene Kurse bleiben, solange die App laeuft.
private object RotationStand {
    var werte by mutableStateOf<List<String>?>(null)
    var ausweich by mutableStateOf<String?>(null)
    var wahl by mutableStateOf(Rotation.Wahl())
    // Jahre des Zeitraums, 0 = ganze gemeinsame Kursgeschichte
    var jahre by mutableStateOf(10)
    var index by mutableStateOf(Portfolioverlauf.MASSSTAEBE.first().id)
    val offen = mutableStateMapOf<String, Boolean>()
    val kerzen = HashMap<String, List<Candle>>()
}

// symbole: Werte mit Kursen in der Reihenfolge von d.werte. ohne: Werte ohne brauchbare Kurse.
private class RotationKurse(val symbole: List<String>, val ausweich: String?, val d: Rotation.Daten?, val ohne: List<String>, val ohneNetz: Boolean, val kuerzeste: String?)

private suspend fun rotationKerzen(state: AppState, symbol: String): Pair<List<Candle>, Boolean> {
    RotationStand.kerzen[symbol]?.let { return it to false }
    val (k, ohneNetz) = ladeTestKerzen(state, symbol)
    if (k.size >= 300) RotationStand.kerzen[symbol] = k
    return k to ohneNetz
}

private fun jahrText(v: Double?): String = v?.let { Fmt.signedPct(it * 100, 1) } ?: "–"

private fun monateText(m: Int): String = if (m == 1) taktikText("rot.monat1") else taktikText("rot.monate", m.toString())

// Aus dem Strategietest geoeffnet: eigenes Blatt mit Titel und Unterzeile wie in der Werkzeugliste.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RotationBlatt(state: AppState, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(S.ptTitle(PortfolioTool.ROTATION.name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            Text(
                S.ptSubtitle(PortfolioTool.ROTATION.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(4.dp))
            RotationTool(state, null)
        }
    }
}

// onOpen: oeffnet einen Wert aus der Auswahl; null, wo das Blatt ueber einem anderen liegt.
@Composable
internal fun RotationTool(state: AppState, onOpen: ((String) -> Unit)?) {
    val stand = RotationStand
    val signal = LocalSignal.current
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val konto by state.account.collectAsStateWithLifecycle()
    val kosten = StrategieLabor.Kosten(settings.spreadStockBps.toDouble())
    val etfAuswahl = remember { Taktik.rotationAuswahl(Catalog.get().etfs) }
    val eigene = remember(konto.positions, konto.watchlist) {
        Taktik.vorbelegung(
            konto.positions.filter { !it.isDerivative }.map { it.symbol }.filter { Symbols.typeOf(it).directlyTradable },
            konto.watchlist.filter { Symbols.typeOf(it).directlyTradable }, etfAuswahl,
        )
    }
    val werte = stand.werte ?: eigene
    var versuch by remember { mutableStateOf(0) }
    // 1 = Wert hinzufuegen, 2 = Ausweichwert
    var suche by remember { mutableStateOf(0) }

    val geladen by produceState<RotationKurse?>(null, werte, stand.ausweich, versuch) {
        value = null
        val reihen = HashMap<String, List<Candle>>()
        val ohne = ArrayList<String>()
        var ohneNetz = false
        for (s in werte + listOfNotNull(stand.ausweich)) {
            val (k, netz) = rotationKerzen(state, s)
            if (netz) ohneNetz = true
            if (k.size >= 300) reihen[s] = k else ohne += s
        }
        val da = werte.filter { it in reihen }
        val aus = stand.ausweich?.takeIf { it in reihen }
        val d = if (da.size < Rotation.MIN_WERTE) null else withContext(Dispatchers.Default) {
            Rotation.daten(da.map { reihen.getValue(it) }, aus?.let { reihen.getValue(it) })
        }
        value = RotationKurse(da, aus, d, ohne, ohneNetz && d == null, (da + listOfNotNull(aus)).maxByOrNull { reihen.getValue(it).first().t })
    }

    val g = geladen
    val d = g?.d?.takeIf { it.n > 0 }
    val zeit = d?.werte?.first()?.kerzen
    val wahl = stand.wahl.copy(halten = stand.wahl.halten.coerceIn(1, (g?.symbole?.size ?: werte.size).coerceAtLeast(1)))
    // Zeitraeume, die die gemeinsame Kursgeschichte hergibt; 0 = alles
    val zeiten = remember(zeit) {
        val vorhanden = if (zeit == null) 0.0 else (zeit.last().t - zeit.first().t) / JAHR_MS.toDouble()
        listOf(3, 5, 10, 20).filter { it <= vorhanden + 0.05 } + 0
    }
    val jahre = stand.jahre.takeIf { it in zeiten } ?: 0
    val berechnet by produceState<List<Rotation.Bericht>?>(null, d, wahl, jahre, kosten) {
        value = null
        if (d == null || zeit == null) return@produceState
        val ab = if (jahre == 0) 0 else StrategieLabor.abIndex(zeit, zeit.last().t - jahre * JAHR_MS)
        value = withContext(Dispatchers.Default) { listOfNotNull(Rotation.hauptbericht(d, ab, kosten, wahl)) }
    }
    val bericht = berechnet?.firstOrNull()
    val massstab = Portfolioverlauf.MASSSTAEBE.firstOrNull { it.id == stand.index } ?: Portfolioverlauf.MASSSTAEBE.first()
    val index by produceState<StrategieLabor.Ergebnis?>(null, massstab, bericht, kosten) {
        value = null
        val b = bericht ?: return@produceState
        val k = rotationKerzen(state, massstab.symbol).first
        value = withContext(Dispatchers.Default) { Rotation.index(k, b.gesamt.verlauf.first().first, b.gesamt.verlauf.last().first, kosten) }
    }

    val farbeRotation = Kategorie.blau
    val farbeHalten = Kategorie.farben.last()
    val farbeIndex = Kategorie.ocker
    when {
        g == null -> ToolNote(S.toolLoading)
        g.ohneNetz -> EmptyHint(S.ohneVerbindung, S.erneutVersuchen to { versuch++ })
        d == null -> ToolNote(taktikText("rot.zuwenigWerte"))
        berechnet == null -> ToolNote(S.toolLoading)
        bericht == null -> ToolNote(taktikText("rot.zuwenig"))
        else -> {
            val rot = bericht.gesamt
            val halten = bericht.haltenGesamt
            val vorsprung = rot.renditeJahr?.let { r -> halten.renditeJahr?.let { r - it } }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
                Text(taktikText("rot.kern.titel"), style = MaterialTheme.typography.titleSmall)
                Text(
                    vorsprung?.let { taktikText("rot.kern.zahl", Fmt.signedPunkte(it * 100, 1)) } ?: "–",
                    style = MaterialTheme.typography.headlineSmall, color = vorsprung?.let { signal.forChange(it) } ?: Color.Unspecified, maxLines = 1, softWrap = false,
                )
                Text(
                    taktikText("rot.kern.satz", Fmt.date(rot.verlauf.first().first), Fmt.date(rot.verlauf.last().first)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinesChart(
                listOfNotNull(
                    index?.let { StrategieLabor.ausduennen(it.verlauf.map { p -> p.second }) to farbeIndex },
                    StrategieLabor.ausduennen(halten.verlauf.map { it.second }) to farbeHalten,
                    StrategieLabor.ausduennen(rot.verlauf.map { it.second }) to farbeRotation,
                ),
                Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                baseline = StrategieLabor.START, scrubben = false,
            )
            // Je Linie eine Zeile mit Punkt in ihrer Farbe, Endwert und Rendite pro Jahr in festen Spalten
            val messer = rememberTextMeasurer()
            val dichte = LocalDensity.current
            val stilWert = MaterialTheme.typography.titleSmall
            val stilProzent = MaterialTheme.typography.bodySmall
            val zeilen = listOf(rot, halten) + listOfNotNull(index)
            val (breiteWert, breiteProzent) = remember(zeilen, stilWert, stilProzent, dichte) {
                val wert = zeilen.maxOf { messer.measure(Fmt.euro(it.endwert, 0), stilWert, maxLines = 1, softWrap = false).size.width }
                val prozent = zeilen.maxOf { messer.measure(jahrText(it.renditeJahr), stilProzent, maxLines = 1, softWrap = false).size.width }
                with(dichte) { wert.toDp() to prozent.toDp() }
            }
            @Composable
            fun linie(farbe: Color, name: String, e: StrategieLabor.Ergebnis?, unter: String? = null, onClick: (() -> Unit)? = null) {
                Row(
                    Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                        .padding(start = 12.dp, end = 20.dp).heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = 30.dp, height = 40.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).clip(CircleShape).background(farbe)) }
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                            if (onClick != null) { Spacer(Modifier.width(6.dp)); ChevronDown(width = 10.dp) }
                        }
                        if (unter != null) Text(unter, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(e?.let { Fmt.euro(it.endwert, 0) } ?: "–", style = stilWert, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(breiteWert))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        jahrText(e?.renditeJahr), style = stilProzent, color = e?.renditeJahr?.let { signal.forChange(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(breiteProzent),
                    )
                }
            }
            linie(farbeRotation, taktikText("rot.name"), rot)
            linie(farbeHalten, S.slHalten, halten)
            var indexMenue by remember { mutableStateOf(false) }
            Box {
                linie(farbeIndex, S.ptBenchName(massstab.schluessel), index, taktikText("rot.index")) { indexMenue = true }
                androidx.compose.material3.DropdownMenu(
                    expanded = indexMenue, onDismissRequest = { indexMenue = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Portfolioverlauf.MASSSTAEBE.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(S.ptBenchName(m.schluessel)) },
                            trailingIcon = if (m.id == massstab.id) { { CheckMark(size = 15.dp) } } else null,
                            onClick = { stand.index = m.id; indexMenue = false },
                        )
                    }
                }
            }
        }
    }

    if (zeit != null && zeiten.size > 1) ZeitLeiste(
        zeiten.map { if (it == 0) S.toolMax else S.toolYearsKurz(it) }, selected = zeiten.indexOf(jahre).coerceAtLeast(0),
        onSelect = { stand.jahre = zeiten[it] }, modifier = Modifier.padding(horizontal = 12.dp).padding(top = 6.dp),
    )

    // Werte des Universums
    val werteOffen = stand.offen["werte"] == true
    EinklappKopf(taktikText("rot.werte"), taktikText("rot.werte.kurz", werte.size.toString()), werteOffen) { stand.offen["werte"] = !werteOffen }
    if (werteOffen) {
        werte.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(wertName(s), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                if (werte.size > Rotation.MIN_WERTE) IconButton(onClick = { stand.werte = werte - s }) {
                    Icon(Icons.Filled.Close, taktikText("rot.entfernen", wertName(s)), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (werte.size < Rotation.MAX_WERTE) FilterPill(taktikText("rot.hinzu")) { suche = 1 }
            if (werte != eigene && eigene != etfAuswahl) FilterPill(taktikText("rot.depot")) { stand.werte = null }
            if (werte != etfAuswahl && etfAuswahl.size >= Rotation.MIN_WERTE) FilterPill(taktikText("rot.etfs")) { stand.werte = etfAuswahl }
        }
    }

    // Regel der Rotation
    val regelOffen = stand.offen["regel"] == true
    EinklappKopf(
        taktikText("rot.regel"), taktikText("rot.regel.kurz", monateText(wahl.monate), wahl.halten.toString(), taktikText("rot." + wahl.gewichtung.name)), regelOffen,
    ) { stand.offen["regel"] = !regelOffen }
    if (regelOffen) {
        ToolChoice(taktikText("rot.rueckblick"), Rotation.MONATE.map { taktikText("rot.m", it.toString()) to it }, wahl.monate) { stand.wahl = wahl.copy(monate = it) }
        ToolChoice(taktikText("rot.plaetze"), (1..minOf(werte.size, 5)).map { it.toString() to it }, wahl.halten) { stand.wahl = wahl.copy(halten = it) }
        ToolChoice(taktikText("rot.gewichtung"), Rotation.Gewichtung.entries.map { taktikText("rot." + it.name) to it }, wahl.gewichtung) { stand.wahl = wahl.copy(gewichtung = it) }
        Spacer(Modifier.height(6.dp))
        SchalterZeile(taktikText("rot.absolut"), wahl.absolut) { stand.wahl = wahl.copy(absolut = it) }
        NavRow(taktikText("rot.ausweich"), stand.ausweich?.let { wertName(it) } ?: taktikText("tk.bargeld")) { suche = 2 }
    }

    if (g != null && bericht != null && d != null) RotationEinzelheiten(g, d, bericht, wahl, jahre, kosten, onOpen)
    if (g != null && g.ohne.isNotEmpty()) ToolNote(taktikText("rot.ohne", g.ohne.joinToString(", ") { wertName(it) }))
    // Gemeinsamer Starttag und Methode in einem Hinweisblock am Ende: erste Zeile sichtbar, der Rest dahinter
    val start = if (zeit != null && g?.kuerzeste != null) "\n" + taktikText("rot.start", Fmt.date(zeit.first().t), wertName(g.kuerzeste)) else ""
    HinweisInfo(taktikText("rot.methode", Fmt.pct(kosten.spreadBps / 200.0, 2)) + start)

    when (suche) {
        1 -> WertWahlBlatt(
            state, taktikText("rot.hinzu"), etfAuswahl + eigene, werte.toSet() + listOfNotNull(stand.ausweich), null, alleArten = false,
            onWahl = { if (it != null) stand.werte = (werte + it).distinct().take(Rotation.MAX_WERTE); suche = 0 }, onDismiss = { suche = 0 },
        )
        2 -> WertWahlBlatt(
            state, taktikText("rot.ausweich"), remember { Taktik.ausweichVorschlaege(Catalog.get().etfs) }, werte.toSet(), taktikText("tk.bargeld"), alleArten = false,
            onWahl = { stand.ausweich = it; suche = 0 }, onDismiss = { suche = 0 },
        )
    }
}

// Heutige Auswahl, Vergleich mit Kaufen und Halten in Lern- und Pruefzeitraum, Aussagekraft, Handelsstil; eingeklappt
// die Kennzahlen je Geschaeft, die Rangliste und dieselbe Rotation mit den anderen Rueckblicken.
@Composable
private fun RotationEinzelheiten(
    g: RotationKurse, d: Rotation.Daten, b: Rotation.Bericht, wahl: Rotation.Wahl, jahre: Int, kosten: StrategieLabor.Kosten, onOpen: ((String) -> Unit)?,
) {
    val signal = LocalSignal.current
    val offen = RotationStand.offen
    SectionHeader(taktikText("rot.heute", Fmt.date(b.entscheidung)))
    // Gewichte als Balken: je Wert eine dezente eigene Farbe (feste Stelle im Universum), Ausweichwert und Bargeld grau
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        g.symbole.forEachIndexed { k, s ->
            if (b.heute[k] > 0.0005) LageBalkenZeile(wertName(s), Fmt.pct(b.heute[k] * 100, 0), b.heute[k], AnteilsFarben.folge(k), onOpen?.let { o -> { o(s) } })
        }
        val ausweichAnteil = if (g.ausweich != null && b.heute.size > g.symbole.size) b.heute[g.symbole.size] else 0.0
        if (g.ausweich != null && ausweichAnteil > 0.0005) LageBalkenZeile(wertName(g.ausweich), Fmt.pct(ausweichAnteil * 100, 0), ausweichAnteil, grau, onOpen?.let { o -> { o(g.ausweich) } })
        val bargeld = 1.0 - b.heute.sum()
        if (bargeld > 0.0005) LageBalkenZeile(taktikText("tk.bargeld"), Fmt.pct(bargeld * 100, 0), bargeld, grau, null)
    }

    val rot = b.gesamt
    val halten = b.haltenGesamt
    val grenze = b.pruef.verlauf.first().first
    // Spalten wie die Linien im Diagramm oben: Rotation blau, Kaufen und Halten grau
    val spalten = listOf(VergleichSpalte(taktikText("rot.name"), Kategorie.blau), VergleichSpalte(S.slHalten, Kategorie.farben.last()))
    Spacer(Modifier.height(6.dp))
    VergleichTabelle(
        spalten,
        listOf(
            VergleichZeile(S.copyMaxDrawdown, listOf("−" + Fmt.pct(rot.maxRueckgang * 100, 1), "−" + Fmt.pct(halten.maxRueckgang * 100, 1))),
            VergleichZeile(
                taktikText("vt.lern"), listOf(jahrText(b.lern.renditeJahr), jahrText(b.haltenLern.renditeJahr)),
                renditeFarben(b.lern.renditeJahr, b.haltenLern.renditeJahr), hint = taktikText("vt.lern.hint", Fmt.date(grenze)),
            ),
            VergleichZeile(
                taktikText("vt.pruef"), listOf(jahrText(b.pruef.renditeJahr), jahrText(b.haltenPruef.renditeJahr)),
                renditeFarben(b.pruef.renditeJahr, b.haltenPruef.renditeJahr), hint = taktikText("vt.pruef.hint", Fmt.date(grenze)),
            ),
        ),
        info = S.slLernText, infoTitel = taktikText("vt.info.titel"),
    )
    AussageZeile(taktikText("vt.aussage.gesamt"), rot)
    AussageZeile(S.slAussagePruef, b.pruef)
    val stil = remember(rot) { Handelsstil.rotation(rot) }
    KeyValueRow(S.st("hs.zeile"), S.st("hs." + stil.name), hint = S.st("hs.dauer." + stil.name))

    fun anteil(v: Double?) = v?.let { Fmt.pct(it * 100, 0) } ?: "–"
    PruefAbschnitt(S.slKennzahlen, S.slKennzahlenText, offen, "kennzahlen", taktikText("vt.kz.kurz", anteil(rot.treffer), anteil(halten.treffer))) {
        val kr = remember(rot) { StrategieLabor.kennzahlen(rot.geschaefte) }
        val kh = remember(halten) { StrategieLabor.kennzahlen(halten.geschaefte) }
        var trefferInfo by remember { mutableStateOf(false) }
        fun zahl(v: Double?) = v?.let { Fmt.num(it, 2) } ?: "–"
        fun erwartung(k: StrategieLabor.Kennzahlen?) = k?.let { Fmt.signedPct(it.bilanz.erwartung * 100, 1) } ?: "–"
        VergleichTabelle(
            spalten,
            listOf(
                VergleichZeile(S.calTrades, listOf(rot.trades.toString(), halten.trades.toString())),
                VergleichZeile(S.slTreffer, listOf(anteil(rot.treffer), anteil(halten.treffer)), onClick = { trefferInfo = true }),
                VergleichZeile(S.slGv, listOf(zahl(kr?.verhaeltnis), zahl(kh?.verhaeltnis))),
                VergleichZeile(S.slErwartung, listOf(erwartung(kr), erwartung(kh))),
                VergleichZeile(S.slGewinnfaktor, listOf(zahl(kr?.bilanz?.gewinnfaktor), zahl(kh?.bilanz?.gewinnfaktor))),
                VergleichZeile(S.slHaltedauer, listOf(kr?.let { Fmt.num(it.haltedauer, 0) } ?: "–", kh?.let { Fmt.num(it.haltedauer, 0) } ?: "–")),
                VergleichZeile(S.slVerlustserie, listOf(kr?.verlustserie?.toString() ?: "–", kh?.verlustserie?.toString() ?: "–")),
                VergleichZeile(S.slImMarkt, listOf(Fmt.pct(rot.imMarkt * 100, 0), Fmt.pct(halten.imMarkt * 100, 0))),
            ),
        )
        if (trefferInfo) MarktInfoBlatt(S.slTreffer, taktikText("vt.treffer")) { trefferInfo = false }
    }
    val vorn = b.rang.firstOrNull()?.let { r ->
        g.symbole.getOrNull(r.wert)?.let { s -> taktikText("vt.rang.kurz", wertName(s), Fmt.signedPct(r.rendite * 100, 0)) }
    } ?: ""
    PruefAbschnitt(taktikText("rot.rang"), taktikText("rot.rang.info"), offen, "rang", vorn) {
        // Balken im Verhaeltnis zur groessten Bewegung, Farbe nach Vorzeichen
        val groesste = b.rang.maxOfOrNull { kotlin.math.abs(it.rendite) }?.takeIf { it > 0 } ?: 1.0
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            b.rang.forEach { r ->
                g.symbole.getOrNull(r.wert)?.let { s ->
                    LageBalkenZeile(wertName(s), Fmt.signedPct(r.rendite * 100, 1), kotlin.math.abs(r.rendite) / groesste, signal.forChange(r.rendite), onOpen?.let { o -> { o(s) } })
                }
            }
        }
    }
    PruefAbschnitt(taktikText("rot.andere"), taktikText("rot.andere.info"), offen, "andere", taktikText("vt.andere.kurz")) {
        val liste by produceState<List<Pair<Int, Rotation.Bericht>>?>(null, d, wahl, jahre, kosten) {
            value = null
            val zeit = d.werte.first().kerzen
            val ab = if (jahre == 0) 0 else StrategieLabor.abIndex(zeit, zeit.last().t - jahre * JAHR_MS)
            value = withContext(Dispatchers.Default) { Rotation.rueckblicke(d, ab, kosten, wahl) }
        }
        val l = liste
        when {
            l == null -> ToolNote(S.toolLoading)
            l.isEmpty() -> ToolNote(taktikText("rot.zuwenig"))
            else -> {
                // Rendite pro Jahr im ganzen Zeitraum und in der Pruefzeit, Balken fuer den ganzen Zeitraum nach Vorzeichen;
                // der gewaehlte Rueckblick ist hervorgehoben, Kaufen und Halten steht als Vergleich darunter
                val h = l.first().second
                val groesste = (l.map { it.second.gesamt.renditeJahr } + h.haltenGesamt.renditeJahr).maxOfOrNull { kotlin.math.abs(it ?: 0.0) }?.takeIf { it > 0 } ?: 1.0
                VergleichTabelle(
                    listOf(VergleichSpalte(taktikText("vt.gesamt")), VergleichSpalte(taktikText("vt.pruefzeit"))),
                    l.map { (m, e) ->
                        val gewaehlt = m == wahl.monate
                        VergleichZeile(
                            monateText(m), listOf(jahrText(e.gesamt.renditeJahr), jahrText(e.pruef.renditeJahr)),
                            renditeFarben(e.gesamt.renditeJahr, e.pruef.renditeJahr),
                            balken = kotlin.math.abs(e.gesamt.renditeJahr ?: 0.0) / groesste,
                            balkenFarbe = signal.forChange(e.gesamt.renditeJahr ?: 0.0), balkenAlpha = if (gewaehlt) 1f else 0.5f, fett = gewaehlt,
                        )
                    } + VergleichZeile(
                        S.slHalten, listOf(jahrText(h.haltenGesamt.renditeJahr), jahrText(h.haltenPruef.renditeJahr)),
                        renditeFarben(h.haltenGesamt.renditeJahr, h.haltenPruef.renditeJahr),
                        balken = kotlin.math.abs(h.haltenGesamt.renditeJahr ?: 0.0) / groesste, balkenFarbe = Kategorie.farben.last(), balkenAlpha = 0.5f,
                    ),
                )
            }
        }
    }
}
