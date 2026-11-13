package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import de.troetenrepublik.app.engine.KongressKennzahlen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Symbols
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import de.troetenrepublik.app.engine.RanglisteSpeicher
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.KongressAuswertung
import de.troetenrepublik.app.engine.VermoegenAnzeige
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Vermoegen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Laedt Geschaefte und Kurse aller Abgeordneten und Senatoren einmal (siehe AppState.kongressDaten) und zeigt
// solange einen Fortschrittsbalken.
@Composable
private fun mitKongressDaten(state: AppState): Pair<AppState.KongressDaten?, Float?> {
    var daten by remember { mutableStateOf(state.kongressFertig()) }
    var fortschritt by remember { mutableStateOf<Float?>(if (daten == null) 0f else null) }
    LaunchedEffect(Unit) {
        if (daten != null) return@LaunchedEffect
        daten = withContext(Dispatchers.Default) { runCatching { state.kongressDaten { fortschritt = it } }.getOrNull() }
        fortschritt = null
    }
    return daten to fortschritt
}

@Composable
private fun Laden(f: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
        Text(S.kaLaedt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

// Strategie „Kongress kauft“: Verlauf seit 2024 gegen den S&P 500, heutige Zusammensetzung, Nachbilden.
@Composable
fun KongressKauftScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    katalogBereit()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val (daten, fortschritt) = mitKongressDaten(state)
    var ergebnis by remember { mutableStateOf<KongressAuswertung.Strategie?>(null) }
    var gerechnet by remember { mutableStateOf(false) }
    var nachbilden by remember { mutableStateOf(false) }
    var alleGewichte by remember { mutableStateOf(false) }
    // Aus den Kaeufen welches Zeitraums das Depot besteht
    var wahl by rememberSaveable { mutableStateOf(0) }
    var eigeneMonate by rememberSaveable { mutableStateOf(24f) }
    val fenster = KongressAuswertung.FENSTER.getOrNull(wahl) ?: (eigeneMonate.toInt() * 30)
    LaunchedEffect(daten, fenster) {
        val d = daten ?: return@LaunchedEffect
        ergebnis = withContext(Dispatchers.Default) { KongressAuswertung.strategie(d.handel, d.kurse, d.index, System.currentTimeMillis(), fenster) }
        gerechnet = true
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.kaTitel, null, onBack) }
        item { HinweisInfo(S.kaText2, modifier = Modifier.padding(vertical = 2.dp)) }
        item(key = "fenster") {
            Text(S.kaFenster, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
            ZeitLeiste(KongressAuswertung.FENSTER.map { S.kaFensterKurz(it) } + S.kaFensterEigen, selected = wahl,
                onSelect = { wahl = it }, modifier = Modifier.padding(horizontal = 12.dp))
            // Eigener Zeitraum in Monaten
            if (wahl == KongressAuswertung.FENSTER.size) {
                SliderRow(S.kaFensterMonate, S.kaMonate(eigeneMonate.toInt()), eigeneMonate, 1f..36f, 1f) { eigeneMonate = it }
            }
        }
        val e = ergebnis
        when {
            fortschritt != null -> item { Laden(fortschritt) }
            e == null && gerechnet -> item { EmptyHint(S.copyNoHistory) }
            e == null -> item { CopyLade() }
            else -> {
                item {
                    Kennzahlen(
                        listOf(
                            S.widgetZeitraum to S.copyRange(Fmt.date(e.verlauf.first().first), Fmt.date(e.verlauf.last().first)),
                            S.ptPerYear to (e.renditePa?.let { Fmt.signedPct(it * 100, 1) } ?: "–"),
                            S.kaVergleich to (e.vergleichPa?.let { Fmt.signedPct(it * 100, 1) } ?: "–"),
                            S.copyMaxDrawdown to Fmt.signedPct(e.maxVerlust * 100, 1),
                        ),
                    )
                }
                item {
                    PortfolioKarte(
                        state, titel = S.kaTitel,
                        haupt = e.verlauf to Portfolioverlauf.Waehrung.USD,
                        laedt = false, hinweis = S.kaHinweisVerlauf, eigener = "kongress-kauft",
                        vorauswahl = listOf("m:sp500"),
                    )
                }
                item { SectionHeader(S.kaJetzt) }
                // Gewichte als duenner Anteilsbalken in den ruhigen Farben der Streuung, gegen das groesste Gewicht
                val groesstes = e.gewichte.maxOfOrNull { it.second } ?: 0.0
                val gewichteSicht = if (alleGewichte) e.gewichte else e.gewichte.take(30)
                itemsIndexed(gewichteSicht, key = { _, (s, _) -> "g-$s" }, contentType = { _, _ -> "gewicht" }) { i, (sym, w) ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(sym) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            NamenText(CopyRouten.katalogName(sym) ?: sym, style = MaterialTheme.typography.titleSmall)
                            Text(sym, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            LageBalken(if (groesstes > 0) w / groesstes else 0.0, AnteilsFarben.folge(i))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(Fmt.pct(w * 100, 1), style = MaterialTheme.typography.titleSmall, color = AnteilsFarben.folge(i), maxLines = 1, softWrap = false)
                    }
                }
                if (e.gewichte.size > 30) item(key = "g-mehr", contentType = "pfeil") {
                    ListenPfeil(alleGewichte, label = if (alleGewichte) S.less else S.bbAlle(e.gewichte.size)) { alleGewichte = !alleGewichte }
                }
                if (e.gewichte.isNotEmpty()) item { ActionLineWide(S.copyReplicate) { nachbilden = true } }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    val e = ergebnis
    if (nachbilden && e != null) {
        NachbildenBlatt(
            state, titel = S.kaTitel, mitAnzahl = true, notiz = S.copyNote(S.kaTitel), onDismiss = { nachbilden = false },
            gewichte = { n ->
                val teil = e.gewichte.take(n)
                val s = teil.sumOf { it.second }
                if (s <= 0) emptyList() else teil.map { it.first to it.second / s }
            },
        )
    }
}

private enum class RangOrdnung { VORSPRUNG, QUOTE, KAEUFE, MELDEVERZUG, VERMOEGEN }

// Rangliste: Abgeordnete und Senatoren nach dem Vorsprung ihrer Kaeufe vor dem S&P 500, nach Trefferquote, Zahl der
// Kaeufe und Vermoegen; je Ordnung eine Seite, Wischen wechselt (seit 5.92). Die Seite zeigt sofort den gespeicherten
// Stand und rechnet im Hintergrund neu, wenn er aelter als sechs Stunden ist.
@Composable
fun RanglisteScreen(state: AppState, onBack: () -> Unit, oeffnen: (String) -> Unit) {
    val dismiss = rememberSwipeDownDismiss(onBack)
    val scope = rememberCoroutineScope()
    var stand by remember { mutableStateOf(state.ranglisteGespeichert()) }
    var fortschritt by remember { mutableStateOf<Float?>(null) }
    var fehler by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val alt = stand
        if (alt != null && !RanglisteSpeicher.veraltet(alt.first, System.currentTimeMillis())) return@LaunchedEffect
        fortschritt = 0f
        val neu = withContext(Dispatchers.Default) {
            val d = runCatching { state.kongressDaten { fortschritt = it } }.getOrNull() ?: return@withContext null
            val i = d.index ?: return@withContext null
            KongressAuswertung.rangliste(d.handel, d.kurse, i, System.currentTimeMillis())
        }
        if (neu != null) {
            val jetzt = System.currentTimeMillis()
            stand = jetzt to neu
            withContext(Dispatchers.IO) { state.ranglisteSpeichern(jetzt, neu) }
        } else fehler = true
        fortschritt = null
    }
    val vermoegen = state.vermoegen
    fun vm(r: KongressAuswertung.Rang): Vermoegen.E? = if (r.person.startsWith("k:")) vermoegen[r.person.removePrefix("k:")] else null
    val ordnungen = RangOrdnung.entries
    var gewaehlt by rememberSaveable { mutableStateOf(RangOrdnung.VORSPRUNG.name) }
    val pager = rememberPagerState(initialPage = ordnungen.indexOfFirst { it.name == gewaehlt }.coerceAtLeast(0)) { ordnungen.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { i -> ordnungen.getOrNull(i)?.let { gewaehlt = it.name } } }
    val listen = remember { List(ordnungen.size) { LazyListState() } }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { listen.getOrNull(pager.currentPage)?.canScrollBackward != true },
    ) {
        ScreenHeader(S.rgTitel, stand?.let { S.rgStand(Fmt.relative(it.first)) }, onBack)
        MitlaufendeReiter(
            anzahl = ordnungen.size, pager = pager, titel = { i -> S.rgOrdnung(ordnungen[i].name) },
            modifier = Modifier.padding(vertical = 12.dp),
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        // Beim ersten Mal ohne gespeicherten Stand der Fortschritt, sonst eine duenne Linie beim Aktualisieren
        fortschritt?.let { f ->
            if (stand == null) Laden(f)
            else LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(2.dp))
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager), beyondViewportPageCount = 1) { seite ->
            val ordnung = ordnungen[seite]
            val raenge = stand?.second
            val gezeigt = remember(raenge, ordnung) {
                val l = raenge.orEmpty()
                when (ordnung) {
                    RangOrdnung.VORSPRUNG -> l.sortedByDescending { it.vorsprung }
                    RangOrdnung.QUOTE -> l.sortedWith(compareByDescending<KongressAuswertung.Rang> { it.quote }.thenByDescending { it.kaeufe })
                    RangOrdnung.KAEUFE -> l.sortedByDescending { it.kaeufe }
                    RangOrdnung.MELDEVERZUG -> l.filter { it.verzug != null }.sortedWith(compareByDescending<KongressAuswertung.Rang> { it.verzug }.thenByDescending { it.spaet })
                    RangOrdnung.VERMOEGEN -> l.filter { vm(it) != null }.sortedByDescending { VermoegenAnzeige.mitte(vm(it)!!) }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listen[seite]) {
                item { Text(if (ordnung == RangOrdnung.VERMOEGEN) S.vmRangErklaerung else S.rgErklaerung(ordnung.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp)) }
                when {
                    raenge == null && fortschritt == null && fehler -> item { EmptyHint(S.copyNoHistory) }
                    raenge == null -> item { if (fortschritt == null) CopyLade() }
                    gezeigt.isEmpty() -> item { EmptyHint(S.copyNoHistory) }
                    else -> itemsIndexed(gezeigt, key = { _, r -> r.person }) { i, r -> RangZeile(state, i + 1, r, ordnung, vm(r), oeffnen) }
                }
                item { HinweisInfo(S.rgText2, modifier = Modifier.padding(top = 10.dp)) }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

// Eine Zeile der Rangliste: Platz, Bild, Name mit Partei und Kammer, rechts nur die Kennzahl der gewaehlten Ordnung,
// darunter ein Satz dazu.
@Composable
private fun RangZeile(state: AppState, platz: Int, r: KongressAuswertung.Rang, ordnung: RangOrdnung, v: Vermoegen.E?, oeffnen: (String) -> Unit) {
    val signal = LocalSignal.current
    val haus = r.person.startsWith("k:")
    val schluessel = r.person.substringAfter(':')
    val punkte = (if (r.vorsprung >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(r.vorsprung) * 100, 1)
    val (wert, farbe, unter) = when (ordnung) {
        RangOrdnung.VORSPRUNG -> Triple(S.rgPunkte(punkte), signal.forChange(r.vorsprung), S.rgAusKaeufen(r.kaeufe))
        RangOrdnung.QUOTE -> Triple(Fmt.pct(r.quote * 100, 0), null, S.rgTreffer(Math.round(r.quote * r.kaeufe).toInt(), r.kaeufe))
        RangOrdnung.KAEUFE -> Triple(r.kaeufe.toString(), null, S.rgKaufsumme(Fmt.dollarKompakt(r.volumen)))
        RangOrdnung.MELDEVERZUG -> Triple(
            r.verzug?.let { S.mvTage(it) } ?: "–", null,
            if (r.verzug == null) "" else S.mvUnter(Fmt.pct((r.spaet ?: 0.0) * 100, 0), r.geschaefte90, Fmt.dollarKompakt(r.volumen90)),
        )
        RangOrdnung.VERMOEGEN -> Triple("", null, "")
    }
    // Kammer nur beim Senat, die meisten stehen im Repraesentantenhaus
    val kopf = listOfNotNull(Infos.kurz(Infos.von(state, r.person)), if (haus) null else S.copySenate).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable { oeffnen(if (haus) CopyRouten.person(schluessel) else CopyRouten.senator(schluessel)) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(platz.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        PersonBild(Fotos.von(state, r.person), r.name, size = 38)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NamenText(r.name, style = MaterialTheme.typography.titleSmall)
            PunktText(listOf(kopf, unter).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Spannen sind zu breit fuer rechts: beim Vermoegen unter dem Namen
            if (ordnung == RangOrdnung.VERMOEGEN && v != null) VermoegenRangZeilen(v)
        }
        if (ordnung != RangOrdnung.VERMOEGEN) {
            Spacer(Modifier.width(10.dp))
            Text(wert, style = MaterialTheme.typography.titleSmall, color = farbe ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
        }
        if (r.person in state.gefolgt()) Icon(Icons.Filled.Star, S.copyFollowing, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 6.dp).size(16.dp))
    }
}

// Betrag ohne Vorzeichen, kompakt in der Anzeigewaehrung: ab einer Milliarde zwei Stellen (Mrd./bn), ab einer Million
// eine Stelle in Mio./m, sonst auf zwei gueltige Stellen gerundet.
internal fun vmBetrag(x: Long): String {
    val (a, _) = Fmt.ausDollar(kotlin.math.abs(x).toDouble())
    return when {
        a >= 1e9 -> Fmt.compact(zweiStellen(a))
        a >= 1_000_000 -> Fmt.num(a / 1_000_000.0, 1) + S.copyMillion
        else -> Fmt.num(zweiStellen(a), 0)
    }
}

// Spanne in der Anzeigewaehrung; bis = null: nach oben offen.
internal fun vmSpanneText(s: VermoegenAnzeige.Spanne): String {
    val zeichen = Fmt.ausDollar(s.von.toDouble()).second
    return if (s.bis == null) S.copyOver(Fmt.mitSymbol(vmBetrag(s.von), zeichen))
    else Fmt.mitSymbol(S.vmSpanne(vmBetrag(s.von), vmBetrag(s.bis)), zeichen)
}

internal fun vmEinzel(x: Long): String = Fmt.mitSymbol(vmBetrag(x), Fmt.ausDollar(x.toDouble()).second)

// Netto-Spanne, wenn das Vorzeichen aus den gemeldeten Spannen folgt; sonst null.
internal fun vmNettoText(t: VermoegenAnzeige.Teile): String? = when (t.fall) {
    VermoegenAnzeige.Netto.PLUS -> vmSpanneText(VermoegenAnzeige.Spanne(t.nettoVon!!, t.nettoBis))
    VermoegenAnzeige.Netto.MINUS -> S.vmImMinus(vmSpanneText(VermoegenAnzeige.Spanne(-t.nettoBis!!, t.nettoVon?.let { -it })))
    else -> null
}

// Beim offenen Ausgang: was im guenstigsten Fall bleibt bzw. wie weit die Schulden im unguenstigsten ueberwiegen.
internal fun vmOffenText(t: VermoegenAnzeige.Teile): String? =
    if (t.fall != VermoegenAnzeige.Netto.OFFEN) null
    else S.vmNettoOffen(schlecht = t.nettoVon?.let { if (it < 0) vmEinzel(-it) else null }, gut = t.nettoBis?.let { if (it > 0) vmEinzel(it) else null })

// Kurzform fuer Listen (Praesident, Regierung): Netto bzw. Werte, ohne Vorsatz bei uneindeutigem Vorzeichen.
internal fun vermoegenKurz(v: Vermoegen.E): String {
    val t = VermoegenAnzeige.teile(v)
    return when (t.fall) {
        VermoegenAnzeige.Netto.PLUS -> S.rgVermoegen(vmNettoText(t)!!)
        VermoegenAnzeige.Netto.OHNE_SCHULDEN -> S.rgVermoegen(vmSpanneText(t.werte))
        else -> S.vmWerte + " " + vmSpanneText(t.werte)
    }
}

// Werte in Fliesstextzeilen duerfen nicht umbrechen.
internal fun ungeteilt(s: String): String = s.replace(' ', '\u00A0')

// Zeilen der Rangliste unter dem Namen: Werte, Schulden und Netto, falls das Vorzeichen eindeutig ist.
@Composable
internal fun VermoegenRangZeilen(v: Vermoegen.E) {
    val t = VermoegenAnzeige.teile(v)
    Text(S.vmWerte + " " + ungeteilt(vmSpanneText(t.werte)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    Text(S.vmSchulden + " " + ungeteilt(t.schulden?.let { vmSpanneText(it) } ?: S.vmKeineSchulden), style = MaterialTheme.typography.bodyMedium)
    vmNettoText(t)?.let { Text(S.vmNetto + " " + ungeteilt(it), style = MaterialTheme.typography.bodyMedium) }
}

// Bezeichnung und Wert je Zeile, gemeinsam fuer Abgeordnete und Regierungsmitglieder.
@Composable
internal fun VermoegenTeileZeilen(t: VermoegenAnzeige.Teile) {
    KeyValueRow(S.vmWerte, vmSpanneText(t.werte))
    KeyValueRow(S.vmSchulden, t.schulden?.let { vmSpanneText(it) } ?: S.vmKeineSchulden)
    vmNettoText(t)?.let { KeyValueRow(S.vmNetto, it) }
}

// Zeile auf der Seite eines Abgeordneten: Vermoegen laut neuester Jahresmeldung, aufklappbar mit den Teilen.
@Composable
internal fun VermoegenZeile(state: AppState, person: String) {
    val v = state.vermoegen[person] ?: return
    VermoegenZeile(v, S.vmHinweis)
}

// Dasselbe fuer eine beliebige Spanne mit eigenem Hinweis (seit 5.92 auch fuer den Praesidenten).
@Composable
internal fun VermoegenZeile(v: Vermoegen.E, hinweis: String) {
    var offen by remember { mutableStateOf(false) }
    val t = VermoegenAnzeige.teile(v)
    Column(Modifier.fillMaxWidth().clickable { offen = !offen }.padding(vertical = 8.dp)) {
        Text(S.vmTitel(v.j), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))
        VermoegenTeileZeilen(t)
        if (offen) Column(Modifier.padding(horizontal = 20.dp)) {
            Text(listOfNotNull(vmOffenText(t), S.vmPosten(t.posten)).joinToString(" "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            StichpunktText(hinweis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ------------------------------------------------------------ Kongress-Flow (0.76)

private fun kongressSektor(k: String): String? =
    Catalog.industryOf(k)?.let { S.industryName(it) } ?: Symbols.find(k)?.sector?.takeIf { it.isNotBlank() }?.let { S.sectorName(it) }

// Balken aus Kaeufen (gruen) und Verkaeufen (rot); die Laenge steht im Verhaeltnis zur groessten Summe der Seite.
@Composable
private fun FlussBalken(seite: KongressKennzahlen.Seite, max: Double) {
    val gesamt = seite.summe
    if (gesamt <= 0 || max <= 0) return
    val signal = LocalSignal.current
    val breite = (gesamt / max).toFloat().coerceIn(0.03f, 1f)
    val kauf = (seite.kaufSumme / gesamt).toFloat()
    Row(Modifier.fillMaxWidth(breite).height(8.dp).clip(RoundedCornerShape(4.dp))) {
        if (kauf > 0f) Box(Modifier.weight(kauf).fillMaxHeight().background(signal.up))
        if (kauf < 1f) Box(Modifier.weight(1f - kauf).fillMaxHeight().background(signal.down))
    }
}

@Composable
private fun FlussZeile(titel: String, seite: KongressKennzahlen.Seite, max: Double) {
    if (seite.leer) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(titel, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlussBalken(seite, max)
        Spacer(Modifier.height(6.dp))
        // Summe in der Farbe der Richtung, die Zahl der Geschaefte grau
        if (seite.kaeufe > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionTriangle(1.0)
            Spacer(Modifier.width(6.dp))
            Text(Fmt.dollarKompakt(seite.kaufSumme), style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.up, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            Text(S.flKaeufe(seite.kaeufe), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
        }
        if (seite.verkaeufe > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionTriangle(-1.0)
            Spacer(Modifier.width(6.dp))
            Text(Fmt.dollarKompakt(seite.verkaufSumme), style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.down, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            Text(S.flVerkaeufe(seite.verkaeufe), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
        }
    }
}

// Kaeufe und Verkaeufe der letzten 90 Tage (nach Handelstag): nach Sektor, nach Kammer, selbst und Familie.
@Composable
fun KongressFlowScreen(state: AppState, onBack: () -> Unit) {
    val katalog = katalogBereit()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    var handel by remember { mutableStateOf<List<KongressAuswertung.Handel>?>(null) }
    var fehler by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        handel = withContext(Dispatchers.Default) { runCatching { state.kongressHandel() }.getOrNull() }
        if (handel == null) fehler = true
    }
    val sprache = I18n.lang
    var alleSektoren by remember { mutableStateOf(false) }
    val fluss = remember(handel, katalog, sprache) {
        handel?.let { KongressKennzahlen.fluss(it, System.currentTimeMillis(), ::kongressSektor) }
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.flTitel, null, onBack) }
        val f = fluss
        when {
            f == null && !fehler -> item { CopyLade() }
            f == null || f.gesamt == 0 -> item { EmptyHint(S.vdLeer) }
            else -> {
                if (f.sektoren.isNotEmpty()) {
                    item { WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.kongressFlow(), Modifier.fillMaxWidth()) { SectionHeader(S.flSektoren) } }
                    val max = f.sektoren.maxOf { it.seite.summe }
                    val sektorenSicht = if (alleSektoren) f.sektoren else f.sektoren.take(12)
                    items(sektorenSicht, key = { "fs-" + it.name }, contentType = { "fluss" }) { FlussZeile(it.name, it.seite, max) }
                    if (f.sektoren.size > 12) item(key = "fs-mehr", contentType = "pfeil") {
                        ListenPfeil(alleSektoren, label = if (alleSektoren) S.less else S.bbAlle(f.sektoren.size)) { alleSektoren = !alleSektoren }
                    }
                    if (f.ohneSektor > 0) item { Text(S.flOhneSektor(f.ohneSektor), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
                }
                item { SectionHeader(S.flKammern) }
                val maxK = maxOf(f.haus.summe, f.senat.summe)
                item { FlussZeile(S.flHaus, f.haus, maxK) }
                item { FlussZeile(S.flSenat, f.senat, maxK) }
                if (!f.familie.leer) {
                    item { SectionHeader(S.flEigeneFamilie) }
                    val maxF = maxOf(f.eigene.summe, f.familie.summe)
                    item { FlussZeile(S.flEigene, f.eigene, maxF) }
                    item { FlussZeile(S.flFamilie, f.familie, maxF) }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}
