package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Netz
import de.troetenrepublik.app.engine.NetzAussagen
import de.troetenrepublik.app.engine.NetzAusschnitt
import de.troetenrepublik.app.engine.NetzKreislauf
import de.troetenrepublik.app.engine.NetzLayout
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.NetzTexte
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.netzAuto
import de.troetenrepublik.app.plattform.ZurueckTaste
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

// [0.79] Vollbildansicht der Verflechtungen: die Firma in der Mitte, Nachbarn ringsum, Pfeile in Richtung der Kante, Art und Betrag
// an der Kante, Guete-Marke daneben. Ein Tipp auf einen Knoten laedt dessen Meldungen (SEC, GLEIF, Wikidata) und erweitert das Netz um
// seine Nachbarn, so weit man will; ist alles sichtbar, oeffnet der Tipp die Wertpapierseite (ohne Aktienseite das Firmenblatt), ebenso
// ein langes Druecken. Ein Tipp auf eine Kante zeigt Einzelheiten und Quelle. Verschieben und Zoomen mit Gesten, Filter nach Art der
// Kante, Kreislaeufe lassen sich hervorheben, Zurueck nimmt die letzte Erweiterung zurueck. Ein Doppeltipp wechselt zwischen dem
// ganzen Netz und der lesbaren Groesse.

// Kleinste Skala der ersten Anordnung, bei der Namen und Beschriftungen lesbar bleiben
private const val LESBAR_SKALA = 0.8f

private val KI_START =listOf("NVDA", "OPENAI", "MSFT", "AMZN", "GOOGL", "ANTHROPIC", "AMD", "ORCL", "CRWV", "META", "AVGO", "SOFTBANK")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerflechtungenScreen(state: AppState, schluessel: String?, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val basis = remember { Netz.basis() }
    val heute = remember { NetzAnzeige.heute() }
    val ki = schluessel == null || schluessel == "ki"
    val startKnoten = remember(schluessel, basis) {
        if (ki) basis.knoten["NVDA"] ?: Netz.Knoten("NVDA", Netz.Typ.FIRMA, "Nvidia", symbole = listOf("NVDA"))
        else netzKnoten(basis, schluessel!!, Symbols.name(schluessel))
    }
    var graph by remember(schluessel) { mutableStateOf(netzGraph(basis, startKnoten, null)) }
    var ausschnitt by remember(schluessel) {
        mutableStateOf(
            if (ki) NetzAusschnitt.ausListe(startKnoten.id, KI_START.filter { it in graph.knoten })
            else NetzAusschnitt.start(graph, startKnoten.id, NetzAusschnitt.ALLE, heute),
        )
    }
    var filter by remember(schluessel) { mutableStateOf(NetzAusschnitt.ALLE) }
    var kreiseAn by remember(schluessel) { mutableStateOf(ki) }
    var positionen by remember(schluessel) { mutableStateOf<Map<String, NetzLayout.Punkt>>(emptyMap()) }
    var geladen by remember(schluessel) { mutableStateOf(setOf<String>()) }
    var laden by remember(schluessel) { mutableStateOf(setOf<String>()) }
    var gewaehlt by remember { mutableStateOf<Netz.Kante?>(null) }
    var aussagenOffen by remember { mutableStateOf(false) }
    var firmenblatt by remember { mutableStateOf<Netz.Knoten?>(null) }
    var skala by remember(schluessel) { mutableStateOf(1f) }
    var versatz by remember(schluessel) { mutableStateOf(Offset.Zero) }
    var flaeche by remember { mutableStateOf(IntSize.Zero) }
    var eingepasst by remember(schluessel) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current.density
    val typo = MaterialTheme.typography
    val schrift = remember(messer, dichte, typo) { ZSchrift(messer, typo.labelLarge, typo.labelSmall, typo.labelSmall, dichte) }
    val sprache = I18n.lang
    val groessen = remember(sprache, schrift) { HashMap<String, ZGroesse>() }
    val farben = netzFarben()

    // Meldungen zum Startknoten (SEC, GLEIF, Wikidata), danach der Ausschnitt mit den neuen Nachbarn
    LaunchedEffect(schluessel) {
        if (ki) return@LaunchedEffect
        laden = laden + startKnoten.id
        val zus = runCatching { state.netzAuto(startKnoten, graph) }.getOrNull()
        if (zus != null && !zus.leer) {
            graph = graph.mit(zus.knoten, zus.kanten)
            if (ausschnitt.schritte.isEmpty()) ausschnitt = NetzAusschnitt.start(graph, startKnoten.id, filter, heute)
        }
        geladen = geladen + startKnoten.id
        laden = laden - startKnoten.id
    }

    val sichtbar = ausschnitt.sichtbar
    val ids = remember(sichtbar) { sichtbar.toList() }
    val kanten = remember(graph, sichtbar, filter) { graph.kanten.filter { it.von in sichtbar && it.nach in sichtbar && it.art.gruppe in filter } }

    // Anordnung: bei jeder Aenderung des Ausschnitts, der Kanten oder des Filters; bekannte Plaetze bleiben nahezu stehen
    LaunchedEffect(ids, kanten, sprache) {
        for (id in ids) if (id !in groessen) groessen[id] = messeKnoten(schrift, graph, id)
        val kaesten = ids.map { NetzLayout.Kasten(it, groessen.getValue(it).b, groessen.getValue(it).h) }
        val paare = kanten.map { it.von to it.nach }
        val alt = positionen
        // Sollabstand wachsen lassen, damit die Beschriftungen zwischen den Knoten Platz haben
        val beschriftung = kanten.maxOfOrNull { schrift.messer.measure(NetzAnzeige.kantenText(it), schrift.kante).size.width.toFloat() } ?: 0f
        val neu = withContext(Dispatchers.Default) { NetzLayout.berechnen(kaesten, paare, fest = alt, mitte = startKnoten.id, kantenBreite = minOf(beschriftung, 240f * dichte) * 0.8f) }
        positionen = neu
    }

    val kreisKanten = remember(graph, sichtbar, kreiseAn) {
        if (!kreiseAn) null else NetzKreislauf.alle(graph, sichtbar, heute).flatMap { c -> c.kanten.map { it.id } }.toSet()
    }
    val mehr = remember(graph, sichtbar, filter) {
        sichtbar.filter { id -> ausschnitt.rest(graph, id, filter, heute).isNotEmpty() }.toSet()
    }
    val aussagen = remember(graph, sichtbar) { NetzAussagen.ableiten(graph, sichtbar, heute, startKnoten.id) }
    val znetz = remember(positionen, kanten, ids, mehr, kreisKanten, sprache, graph) {
        baueNetz(schrift, graph, ids.filter { it in positionen && it in groessen }, kanten, positionen, groessen, startKnoten.id, mehr, kreisKanten, heute, true)
    }

    // Erste Anordnung: ganz einpassen, solange die Schrift lesbar bleibt. Ist das Netz groesser, steht die Ausgangsfirma in der
    // Mitte in lesbarer Groesse und der Rest liegt per Wischen und Zoomen bereit.
    LaunchedEffect(positionen, flaeche) {
        if (eingepasst || positionen.isEmpty() || flaeche.width == 0 || znetz.knoten.isEmpty()) return@LaunchedEffect
        val rand = 24f * dichte
        val bw = znetz.rechts - znetz.links + 2 * rand
        val bh = znetz.unten - znetz.oben + 2 * rand
        val ganz = minOf(1f, flaeche.width / bw, flaeche.height / bh)
        val s = ganz.coerceAtLeast(LESBAR_SKALA)
        skala = s
        val start = znetz.knoten.firstOrNull { it.start }
        versatz = if (ganz >= LESBAR_SKALA || start == null) Offset(
            (flaeche.width - (znetz.rechts + znetz.links) * s) / 2f,
            (flaeche.height - (znetz.unten + znetz.oben) * s) / 2f,
        ) else Offset(flaeche.width / 2f - start.x * s, flaeche.height / 2f - start.y * s)
        eingepasst = true
    }

    // Doppeltipp: aus der lesbaren Groesse ins ganze Netz, aus der Uebersicht an der getippten Stelle zurueck in Lesegroesse
    fun doppeltipp(p: Offset) {
        val z = znetz
        if (z.knoten.isEmpty() || flaeche.width == 0) return
        val rand = 24f * dichte
        val ganz = minOf(1f, flaeche.width / (z.rechts - z.links + 2 * rand), flaeche.height / (z.unten - z.oben + 2 * rand))
        if (skala >= LESBAR_SKALA - 0.01f && ganz < LESBAR_SKALA - 0.01f) {
            skala = ganz
            versatz = Offset((flaeche.width - (z.rechts + z.links) * ganz) / 2f, (flaeche.height - (z.unten + z.oben) * ganz) / 2f)
        } else {
            val ziel = maxOf(1f, skala * 1.5f).coerceAtMost(3f)
            versatz = (versatz - p) * (ziel / skala) + p
            skala = ziel
        }
    }

    fun oeffne(k: Netz.Knoten) {
        val sym = k.symbol
        when {
            sym != null -> onOpen(sym)
            k.typ == Netz.Typ.FIRMA -> firmenblatt = k
        }
    }

    // Tipp: Meldungen laden, erweitern; ist nichts mehr zu erweitern, die Seite des Knotens oeffnen
    fun tippe(id: String) {
        val k = graph.knoten[id] ?: return
        scope.launch {
            if (k.typ == Netz.Typ.FIRMA && id !in geladen && id !in laden) {
                laden = laden + id
                val zus = runCatching { state.netzAuto(k, graph) }.getOrNull()
                if (zus != null && !zus.leer) graph = graph.mit(zus.knoten, zus.kanten)
                geladen = geladen + id
                laden = laden - id
            }
            val neu = ausschnitt.erweitern(graph, id, filter, heute)
            if (neu !== ausschnitt) ausschnitt = neu else oeffne(k)
        }
    }

    fun zurueck() {
        val vorher = ausschnitt.zurueck()
        if (vorher != null) ausschnitt = vorher else onBack()
    }
    ZurueckTaste(enabled = ausschnitt.schritte.isNotEmpty()) { ausschnitt.zurueck()?.let { ausschnitt = it } }

    val aktuell = rememberUpdatedState(znetz)
    val skalaJetzt = rememberUpdatedState(skala)
    val versatzJetzt = rememberUpdatedState(versatz)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { zurueck() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, de.troetenrepublik.app.i18n.S.back) }
            Text(
                if (ki) NetzTexte.t("start.ki") else NetzAnzeige.name(graph, startKnoten.id),
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
        }
        // Filter nach Art der Kante und Hervorhebung der Kreislaeufe
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Netz.Gruppe.entries.forEach { g ->
                FilterChip(
                    selected = g in filter,
                    onClick = { filter = if (g in filter) filter - g else filter + g },
                    label = { Text(NetzAnzeige.gruppeName(g), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false) },
                    leadingIcon = { Box(Modifier.size(9.dp).clip(CircleShape).background(NetzAnzeige.farbe(g))) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            FilterChip(
                selected = kreiseAn, onClick = { kreiseAn = !kreiseAn },
                label = { Text(NetzTexte.t("filter.kreise"), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false) },
            )
        }
        // Aussagenkasten: eine Zeile mit der ersten Aussage, aufgeklappt alle
        if (aussagen.isNotEmpty()) {
            val sv = MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer).clickable { aussagenOffen = !aussagenOffen }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(aussagenFarbe(aussagen.first(), sv)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(NetzAnzeige.aussage(graph, aussagen.first()), style = MaterialTheme.typography.bodyMedium)
                    if (aussagen.size > 1) Text(NetzTexte.t("aussagen.weitere", (aussagen.size - 1).toString()), style = MaterialTheme.typography.labelSmall, color = sv)
                }
                Spacer(Modifier.width(8.dp))
                AufklappPfeil(aussagenOffen)
            }
            if (aussagenOffen) Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) { AussagenBlock(graph, aussagen) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().wischSperre().onSizeChanged { flaeche = it }) {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { p ->
                                val w = (p - versatzJetzt.value) / skalaJetzt.value
                                val z = aktuell.value
                                val n = z.knotenAn(w.x, w.y)
                                if (n != null) tippe(n.id)
                                else z.kanteAn(w.x, w.y, 14f * dichte / skalaJetzt.value)?.let { gewaehlt = it.kante }
                            },
                            onDoubleTap = { p -> doppeltipp(p) },
                            onLongPress = { p ->
                                val w = (p - versatzJetzt.value) / skalaJetzt.value
                                aktuell.value.knotenAn(w.x, w.y)?.let { n -> graph.knoten[n.id]?.let { oeffne(it) } }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { mitte, pan, zoom, _ ->
                            val alt = skalaJetzt.value
                            val neu = (alt * zoom).coerceIn(0.08f, 3f)
                            val faktor = neu / alt
                            versatz = (versatzJetzt.value - mitte) * faktor + mitte + pan
                            skala = neu
                        }
                    },
            ) {
                withTransform({
                    translate(versatz.x, versatz.y)
                    scale(skala, skala, pivot = Offset.Zero)
                }) { zeichneNetz(znetz, farben, dichte, true) }
            }
            // Logos nur fuer Knoten im Ausschnitt; derselbe Versatz und dieselbe Skala wie der Canvas
            val imBild by remember(znetz, flaeche, dichte) {
                derivedStateOf {
                    val rand = 60f * dichte
                    val s = skala; val v = versatz
                    znetz.knoten.filter { n ->
                        n.logoD > 0f &&
                            n.x * s + v.x + n.b / 2f * s >= -rand && n.x * s + v.x - n.b / 2f * s <= flaeche.width + rand &&
                            n.y * s + v.y + n.h / 2f * s >= -rand && n.y * s + v.y - n.h / 2f * s <= flaeche.height + rand
                    }
                }
            }
            NetzLogos(imBild) {
                translationX = versatz.x
                translationY = versatz.y
                scaleX = skala
                scaleY = skala
            }
            if (laden.isNotEmpty()) {
                Text(
                    NetzTexte.t("laedt"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                )
            }
        }
        NetzLegende()
        val quellen = remember(kanten) { kanten.map { it.quelle }.distinctBy { it.name }.take(20).map { QuelleLink(it.name, it.url) } }
        QuellenAnhang(quellen)
    }

    gewaehlt?.let { k ->
        NetzKantenBlatt(
            graph, k, heute, kreislaeufeMit(graph, sichtbar, k, heute), onAndere = { gewaehlt = it },
            oeffnen = { n -> gewaehlt = null; oeffne(n) }, onDismiss = { gewaehlt = null },
        )
    }
    firmenblatt?.let { k -> FirmaBlatt(state, FirmaDaten(k.anzeige(NetzAnzeige.deutsch()), "", "", false, null, null, null, "")) { firmenblatt = null } }
}

// Knappe Legende: Farben der Arten, gestrichelt angekuendigt, gelb hinterlegt Kreislauf
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NetzLegende() {
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) {
        Netz.Gruppe.entries.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 16.dp, height = 3.dp).background(NetzAnzeige.farbe(g)))
                Spacer(Modifier.width(5.dp))
                Text(NetzAnzeige.gruppeName(g), style = MaterialTheme.typography.labelSmall, color = sv, maxLines = 1, softWrap = false)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("- - -", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFFE5883A), maxLines = 1, softWrap = false)
            Spacer(Modifier.width(5.dp))
            Text(NetzTexte.t("legende.angekuendigt"), style = MaterialTheme.typography.labelSmall, color = sv, maxLines = 1, softWrap = false)
        }
        Text(NetzTexte.t("legende.kreis"), style = MaterialTheme.typography.labelSmall, color = sv)
        Text(NetzTexte.t("legende.mehr"), style = MaterialTheme.typography.labelSmall, color = sv)
    }
}
