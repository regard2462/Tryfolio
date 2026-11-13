package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.engine.Netz
import de.troetenrepublik.app.engine.NetzAussagen
import de.troetenrepublik.app.engine.NetzAusschnitt
import de.troetenrepublik.app.engine.NetzKreislauf
import de.troetenrepublik.app.engine.NetzLayout
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.NetzTexte
import de.troetenrepublik.app.market.NetzAuto
import de.troetenrepublik.app.netzAuto
import de.troetenrepublik.app.netzGespeichert
import de.troetenrepublik.app.netzLeicht

// [0.79] Karte "Verflechtungen" auf der Wertpapierseite: wer liefert, wer abnimmt, wer sich beteiligt oder finanziert, wer im
// Wettbewerb steht, welche Board-Mitglieder und welches Staatsgeld. Oben die Kernaussagen, darunter ein kleines Netz (Radius 1),
// das zur Vollbildansicht fuehrt, und die Listen eingeklappt mit Zusammenfassung. Ohne Kante erscheint die Karte nicht.
// Das gepflegte Buendel liefert die Kanten sofort, SEC, GLEIF und Wikidata kommen je Firma nach (14 Tage zwischengespeichert).

private const val LISTE_MAX = 40

// Netz mit den Meldungen zu einer Firma; die Firma selbst kommt als Knoten dazu, wenn das Buendel sie nicht kennt
internal fun netzGraph(basis: Netz.Graph, firma: Netz.Knoten, zusatz: NetzAuto.Zusatz?): Netz.Graph {
    val eigene = if (basis.knoten[firma.id] == null) listOf(firma) else emptyList()
    return basis.mit(eigene + (zusatz?.knoten ?: emptyList()), zusatz?.kanten ?: emptyList())
}

// Knoten zu einem Kuerzel oder einer Kennung (Route netz/...): bekannter Knoten des Netzes, sonst eine Firma mit diesem Kuerzel
internal fun netzKnoten(basis: Netz.Graph, schluessel: String, name: String): Netz.Knoten =
    basis.knoten[schluessel] ?: basis.knotenFuerSymbol(schluessel) ?: Netz.Knoten(schluessel, Netz.Typ.FIRMA, name, symbole = listOf(schluessel))

@Composable
internal fun VerflechtungenKarte(
    state: AppState, symbol: String, name: String, art: AssetType, merker: SeitenMerker, onOpenRoute: (String) -> Unit,
) {
    if (art != AssetType.STOCK) return
    val basis = remember { Netz.basis() }
    val firma = remember(symbol, name, basis) { netzKnoten(basis, symbol, name) }
    val zustand = merker.zustand<NetzAuto.Zusatz?>("netz/zusatz") { null }
    val geladen = merker.zustand("netz/geladen") { false }
    val offenZustand = merker.zustand("netz/offen") { false }
    val offen = offenZustand.value
    var gewaehlt by remember { mutableStateOf<Netz.Kante?>(null) }

    // Zuerst der gespeicherte Stand, sonst die Grosskunden aus dem schon gespeicherten Firmenbericht. Ist damit nichts bekannt,
    // ruft die Seite die Meldungen gleich ab (sonst bliebe die Karte bei Firmen ohne gepflegte Kanten unsichtbar), sonst erst beim
    // Aufklappen. Der Abruf laeuft im Bereich der Seite und hoechstens alle 14 Tage je Firma.
    val abruf: () -> Unit = {
        merker.starte("netz/$symbol/abruf") {
            val frisch = state.netzAuto(firma, basis)
            if (!frisch.leer) zustand.value = frisch
        }
    }
    LaunchedEffect(symbol) {
        if (geladen.value) return@LaunchedEffect
        val bekannt = state.netzGespeichert(firma, basis)?.takeIf { !it.leer } ?: state.netzLeicht(firma, basis).takeIf { !it.leer }
        zustand.value = bekannt
        geladen.value = true
        if (bekannt == null && basis.kanten(firma.id).isEmpty()) abruf()
    }
    LaunchedEffect(offen) {
        if (offen) abruf()
    }

    val zusatz = zustand.value
    val g = merker.gerechnet("netz/graph", listOf(Ident(basis), Ident(zusatz), firma.id)) { netzGraph(basis, firma, zusatz) }
    val heute = remember { NetzAnzeige.heute() }
    val alle = g.kanten(firma.id)
    if (alle.isEmpty()) {
        ModellMelden(merker, "netz", null)
        return
    }
    val zul = g.zulieferer(firma.id)
    val abn = g.abnehmer(firma.id)
    val kap = g.kapital(firma.id)
    val wett = g.wettbewerber(firma.id)
    val man = g.mandate(firma.id)
    val sg = g.staatsgeld(firma.id)
    val kreise = merker.gerechnet("netz/kreise", listOf(Ident(g), firma.id)) { NetzKreislauf.suche(g, firma.id, heute).size }
    fun firmen(l: List<Netz.Kante>) = l.filter { it.aktiv(heute) }.map { it.andere(firma.id) }.distinct().size
    val teile = listOfNotNull(
        firmen(zul).takeIf { it > 0 }?.let { NetzTexte.zahl("zulieferer", it) },
        firmen(abn).takeIf { it > 0 }?.let { NetzTexte.zahl("abnehmer", it) },
        kreise.takeIf { it > 0 }?.let { NetzTexte.zahl("kreis", it) },
    ).ifEmpty {
        listOfNotNull(
            firmen(kap).takeIf { it > 0 }?.let { NetzTexte.zahl("bet", it) },
            firmen(wett).takeIf { it > 0 }?.let { NetzTexte.zahl("wett", it) },
        )
    }
    val unter = teile.joinToString(PUNKT)
    val kopf: @Composable () -> Unit = { AufklappKopf(NetzTexte.t("titel"), unter, offen) { offenZustand.value = !offenZustand.value } }
    if (!offen) {
        ModellMelden(merker, "netz", ListenModell(titel = "", gross = true, kern = null, zeilen = emptyList(), kopf = kopf, immerOffen = true))
        return
    }

    val ausschnitt = merker.gerechnet("netz/ausschnitt", listOf(Ident(g), firma.id)) { NetzAusschnitt.start(g, firma.id, NetzAusschnitt.ALLE, heute) }
    val aussagen = merker.gerechnet("netz/aussagen", listOf(Ident(g), firma.id)) { NetzAussagen.ableiten(g, ausschnitt.sichtbar, heute, firma.id) }
    val route = "netz/" + (firma.symbol ?: firma.id)
    val rows = ArrayList<ZeilenDaten>()
    if (aussagen.isNotEmpty()) rows.add(ZeilenDaten("aussagen") { AussagenBlock(g, aussagen) })
    rows.add(ZeilenDaten("netz") { MiniNetz(g, firma.id, ausschnitt.sichtbar.toList(), heute) { onOpenRoute(route) } })
    fun liste(key: String, kanten: List<Netz.Kante>) {
        if (kanten.isEmpty()) return
        rows.add(ZeilenDaten("liste-$key") { NetzListe(NetzTexte.t("liste.$key"), g, firma.id, kanten, heute) { gewaehlt = it } })
    }
    liste("zulieferer", zul); liste("abnehmer", abn); liste("kapital", kap); liste("wettbewerber", wett); liste("mandate", man); liste("staat", sg)
    // Quellen der gezeigten Kanten gesammelt am Seitenende
    val quellen = alle.sortedByDescending { g.gewicht(it) }.map { it.quelle }.distinctBy { it.name }.take(15).map { QuelleLink(it.name, it.url) }
    QuellenMelden(*quellen.toTypedArray())
    ModellMelden(merker, "netz", ListenModell(titel = "", gross = true, kern = null, zeilen = rows, kopf = kopf, immerOffen = true))
    gewaehlt?.let { k ->
        NetzKantenBlatt(
            g, k, heute, kreislaeufeMit(g, ausschnitt.sichtbar, k, heute), onAndere = { gewaehlt = it },
            oeffnen = { n -> gewaehlt = null; n.symbol?.let { onOpenRoute("instrument/$it") } }, onDismiss = { gewaehlt = null },
        )
    }
}

// Kreislaeufe des Ausschnitts, in denen die Kante vorkommt, als Namenslisten
internal fun kreislaeufeMit(g: Netz.Graph, sichtbar: Set<String>, k: Netz.Kante, heute: String): List<String> =
    NetzKreislauf.alle(g, sichtbar, heute).filter { c -> c.kanten.any { it.id == k.id } }
        .map { c -> NetzTexte.t("kreis.name", c.knoten.joinToString(", ") { NetzAnzeige.name(g, it) }) }

// Die Karte als Eintraege der LazyColumn
internal fun LazyListScope.verflechtungenAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "netz")
}

// "Was das Netz zeigt": Kernaussagen als Stichpunkte; Angaben der Guete B und C tragen eine kleine Zeile
@Composable
internal fun AussagenBlock(g: Netz.Graph, aussagen: List<NetzAussagen.Aussage>, modifier: Modifier = Modifier) {
    if (aussagen.isEmpty()) return
    var info by remember { mutableStateOf(false) }
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().clickable { info = true }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(NetzTexte.t("aussagen.titel"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.Info, S_INFO, tint = sv, modifier = Modifier.size(16.dp))
        }
        aussagen.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Box(Modifier.padding(top = 7.dp).size(7.dp).clip(CircleShape).background(aussagenFarbe(a, sv)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(NetzAnzeige.aussage(g, a), style = MaterialTheme.typography.bodyMedium)
                    if (a.gemischt) Text(NetzTexte.t("a.gemischt"), style = MaterialTheme.typography.labelSmall, color = sv, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
    if (info) MarktInfoBlatt(NetzTexte.t("aussagen.titel"), NetzTexte.t("aussagen.info")) { info = false }
}

// Punkt je Aussage: gelb Kreislauf, orange bedingte Zusagen, grau alles andere
internal fun aussagenFarbe(a: NetzAussagen.Aussage, grau: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color = when (a.typ) {
    NetzAussagen.Typ.KREISLAUF -> androidx.compose.ui.graphics.Color(0xFFF5C33B)
    NetzAussagen.Typ.BEDINGT -> androidx.compose.ui.graphics.Color(0xFFE5883A)
    else -> grau
}

private val S_INFO: String get() = de.troetenrepublik.app.i18n.S.infoErklaerung

// Kleines Netz (Radius 1): die Firma in der Mitte, links die Nachbarn mit Kante zur Firma, rechts die uebrigen, bis zu acht in
// zwei Spalten. Die Namen stehen in voller Schrift ohne Logo, die Kaesten brechen den Namen an Wortgrenzen um und passen in
// die Breite; es laufen nur Kanten von und zur Firma. Ein Tipp oeffnet die Vollbildansicht.
// Bis zu acht Nachbarn fuer das kleine Bild, reihum aus den Gruppen (Zulieferer, Abnehmer, Beteiligungen, Wettbewerber,
// Mandate, Staat), je Gruppe die gewichtigsten zuerst. So verdraengen 28 Toechter nicht die vier Zulieferer.
internal fun miniAuswahl(g: Netz.Graph, id: String, sichtbar: List<String>, heute: String, max: Int = 8): List<String> {
    val gruppen = listOf(g.zulieferer(id), g.abnehmer(id), g.kapital(id), g.wettbewerber(id), g.mandate(id), g.staatsgeld(id)).map { l ->
        l.sortedWith(compareByDescending<Netz.Kante> { it.aktiv(heute) }.thenByDescending { g.gewicht(it) })
            .map { it.andere(id) }.filter { it != id && g.knoten[it] != null }.distinct().toMutableList()
    }
    val aus = LinkedHashSet<String>()
    while (aus.size < max && gruppen.any { it.isNotEmpty() }) {
        for (gr in gruppen) {
            while (gr.isNotEmpty() && gr.first() in aus) gr.removeAt(0)
            if (gr.isNotEmpty() && aus.size < max) aus.add(gr.removeAt(0))
        }
    }
    for (s in sichtbar) if (aus.size < max && s != id) aus.add(s)
    return listOf(id) + aus
}

@Composable
private fun MiniNetz(g: Netz.Graph, id: String, sichtbar: List<String>, heute: String, onOpen: () -> Unit) {
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current.density
    val typo = MaterialTheme.typography
    val schrift = remember(messer, dichte, typo) { ZSchrift(messer, typo.labelMedium, typo.labelSmall, typo.labelSmall, dichte) }
    val sprache = I18n.lang
    val ids = remember(g, sichtbar, id, heute) { miniAuswahl(g, id, sichtbar, heute) }
    val kanten = remember(g, ids) { g.kanten.filter { (it.von == id || it.nach == id) && it.von in ids && it.nach in ids } }
    val farben = netzFarben()
    val pad = 14f * dichte
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        val breite = constraints.maxWidth.toFloat()
        val aufbau = remember(g, ids, kanten, sprache, schrift, breite) {
            val nutz = breite - 2 * pad
            val luecke = 26f * dichte
            val mitte = messeKnoten(schrift, g, id, maxBox = nutz * 0.38f, logo = false)
            val spalte = (nutz - mitte.b - 2 * luecke) / 2f
            val gr = ids.associateWith { if (it == id) mitte else messeKnoten(schrift, g, it, maxBox = spalte, logo = false) }
            val pos = NetzLayout.faecher(ids.map { NetzLayout.Kasten(it, gr.getValue(it).b, gr.getValue(it).h) }, kanten.map { it.von to it.nach }, id, luecke, 10f * dichte)
            baueNetz(schrift, g, ids, kanten, pos, gr, id, emptySet(), null, heute, false)
        }
        val bw = aufbau.rechts - aufbau.links + 2 * pad
        val bh = aufbau.unten - aufbau.oben + 2 * pad
        val s = minOf(1f, breite / bw, 340f * dichte / bh)
        val hoch = with(LocalDensity.current) { (bh * s).toDp() }
        val versatzX = (breite - bw * s) / 2f
        Box(
            Modifier.fillMaxWidth().height(hoch).clickable(role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = NetzTexte.t("bild", NetzAnzeige.name(g, id)) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                translate(versatzX, 0f) {
                    scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                        translate(-aufbau.links + pad, -aufbau.oben + pad) { zeichneNetz(aufbau, farben, dichte, false) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun netzFarben(): ZFarben {
    val cs = MaterialTheme.colorScheme
    val sig = LocalSignal.current
    return ZFarben(cs.onSurface, cs.surfaceContainer, cs.outline, cs.onSurfaceVariant, sig.up, sig.warn, sig.down, cs.primary, cs.surfaceContainerHigh, cs.onPrimary, cs.background)
}

// Eine Liste der Karte, eingeklappt: Bezeichnung mit Zahl, darunter die ersten Namen; aufgeklappt eine Zeile je Kante
@Composable
private fun NetzListe(titel: String, g: Netz.Graph, aus: String, kanten: List<Netz.Kante>, heute: String, onKante: (Netz.Kante) -> Unit) {
    val sortiert = remember(kanten) { kanten.sortedWith(compareByDescending<Netz.Kante> { it.aktiv(heute) }.thenByDescending { g.gewicht(it) }).take(LISTE_MAX) }
    val gegen = remember(sortiert) { sortiert.map { it.andere(aus) }.distinct() }
    // Zahl aller Gegenueber, auch ueber die gezeigten hinaus
    val zahl = remember(kanten) { kanten.map { it.andere(aus) }.distinct().size }
    val namen = gegen.take(3).joinToString(", ") { NetzAnzeige.name(g, it) }
    val unter = if (zahl > 3) namen + " " + NetzTexte.zahl("weitere", zahl - 3).replace(" ", " ") else namen
    KartenZeile(label = titel, wert = zahl.toString(), unter = unter, details = {
        sortiert.forEach { k -> NetzKantenZeile(g, k, aus, heute) { onKante(k) } }
    })
}

// Zeile einer Kante in der aufgeklappten Liste: Gegenueber, Betrag rechts, darunter Rolle, Teil, Status und Guete
@Composable
private fun NetzKantenZeile(g: Netz.Graph, k: Netz.Kante, aus: String, heute: String, onClick: () -> Unit) {
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    val rolle = if (k.art.gruppe == Netz.Gruppe.LIEFERUNG && k.art != Netz.Art.LIZENZ) NetzAnzeige.artName(k.art)
        else if (k.art == Netz.Art.KONKURRIERT) null else NetzAnzeige.rolle(k, aus)
    val status = k.status.takeIf { it != Netz.Status.DEFINITIV && it != Netz.Status.ZUGESAGT }?.let { NetzAnzeige.statusName(k) }
    val sub = listOfNotNull(
        rolle, k.teil?.let { NetzAnzeige.teil(it) }, k.rolle, status, if (k.guete != Netz.Guete.A) NetzTexte.t("d.guete") + " " + k.guete.name else null,
    ).joinToString(PUNKT)
    Column(Modifier.fillMaxWidth().alpha(if (k.aktiv(heute)) 1f else 0.55f).clickable(role = Role.Button, onClick = onClick).padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(NetzAnzeige.name(g, k.andere(aus)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            val w = NetzAnzeige.umfangText(k.umfang)
            if (w != null) {
                Spacer(Modifier.width(12.dp))
                Text(w, style = MaterialTheme.typography.bodySmall, softWrap = false, maxLines = 1, textAlign = TextAlign.End)
            }
        }
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = sv)
    }
}

// Einzelheiten einer Kante: Art, Betrag, Status, Stand, Quelle (Link), Guete mit Warnzeile, Teil von Kreislaeufen, weitere
// Verbindungen zwischen den beiden Firmen und der Weg zu den Firmen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetzKantenBlatt(
    g: Netz.Graph, k: Netz.Kante, heute: String, kreise: List<String>, onAndere: (Netz.Kante) -> Unit, oeffnen: (Netz.Knoten) -> Unit, onDismiss: () -> Unit,
) {
    val sv = MaterialTheme.colorScheme.onSurfaceVariant
    val warn = LocalSignal.current.warn
    val links = appLinks()
    val andere = remember(g, k) { g.kanten.filter { it.id != k.id && ((it.von == k.von && it.nach == k.nach) || (it.von == k.nach && it.nach == k.von)) } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(NetzAnzeige.artName(k.art), style = MaterialTheme.typography.headlineSmall)
            Text(
                NetzTexte.t("liste.und", NetzAnzeige.name(g, k.von), NetzAnzeige.name(g, k.nach)), style = MaterialTheme.typography.bodyMedium, color = sv,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            if (k.art.gerichtet) {
                BlattZeile(NetzTexte.t("d.von"), NetzAnzeige.name(g, k.von))
                BlattZeile(NetzTexte.t("d.nach"), NetzAnzeige.name(g, k.nach))
            }
            NetzAnzeige.umfangText(k.umfang)?.let { BlattZeile(NetzTexte.t("d.umfang"), it + (if (k.bedingt) PUNKT + NetzTexte.t("bedingt") else "")) }
            BlattZeile(NetzTexte.t("d.status"), NetzAnzeige.statusName(k))
            val zeitraum = when {
                k.seit != null && k.bis != null -> NetzTexte.t("d.von.bis", NetzAnzeige.datum(k.seit), NetzAnzeige.datum(k.bis))
                k.seit != null -> NetzTexte.t("d.seit", NetzAnzeige.datum(k.seit))
                else -> null
            }
            zeitraum?.let { BlattZeile(NetzTexte.t("d.zeitraum"), it) }
            k.rolle?.let { BlattZeile(NetzTexte.t("d.rolle"), it) }
            NetzAnzeige.programm(k)?.let { BlattZeile(NetzTexte.t("d.programm"), it) }
            k.teil?.let { BlattZeile(NetzAnzeige.artName(k.art), NetzAnzeige.teil(it)) }
            BlattZeile(NetzTexte.t("d.stand"), NetzAnzeige.datum(k.stand) + (if (k.ungeprueft(heute)) PUNKT + NetzTexte.t("d.ungeprueft", NetzAnzeige.datum(k.ablauf.orEmpty())) else ""))
            BlattZeile(NetzTexte.t("d.guete"), NetzTexte.t("guete." + k.guete.name))
            kreise.forEach { BlattZeile(NetzTexte.t("d.teilvon"), it) }
            // Absichtserklaerung oder bedingt: Warnkasten
            if (k.status == Netz.Status.ABSICHT || k.bedingt) {
                Text(
                    NetzTexte.t(if (k.status == Netz.Status.ABSICHT) "d.absicht.warn" else "d.bedingt.warn"), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(warn.copy(alpha = 0.16f)).padding(12.dp),
                )
            }
            if (k.guete != Netz.Guete.A) Text(NetzTexte.t("warn." + k.guete.name), style = MaterialTheme.typography.bodySmall, color = warn, modifier = Modifier.padding(top = 6.dp))
            if (k.herkunft == Netz.Herkunft.GEMELDET) Text(NetzTexte.t("d.gemeldet"), style = MaterialTheme.typography.bodySmall, color = sv, modifier = Modifier.padding(top = 4.dp))
            NetzAnzeige.hinweis(k)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp)) }
            if (andere.isNotEmpty()) {
                Text(
                    NetzTexte.t("d.weitere.verb", NetzTexte.t("liste.und", NetzAnzeige.name(g, k.von), NetzAnzeige.name(g, k.nach))),
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp),
                )
                andere.forEach { o ->
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onAndere(o) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 18.dp, height = 3.dp).background(NetzAnzeige.farbe(o.art)))
                        Spacer(Modifier.width(10.dp))
                        Text(NetzAnzeige.kantenText(o), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(NetzAnzeige.statusName(o) + PUNKT + o.guete.name, style = MaterialTheme.typography.labelSmall, color = sv, softWrap = false)
                    }
                }
            }
            listOf(k.nach, k.von).distinct().mapNotNull { g.knoten[it] }.filter { it.typ == Netz.Typ.FIRMA }.forEach { n ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { oeffnen(n) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(NetzTexte.t("aktion.firma", n.anzeige(NetzAnzeige.deutsch())), style = MaterialTheme.typography.bodyLarge, color = LocalSignal.current.link, modifier = Modifier.weight(1f))
                    ChevronRight()
                }
            }
            Text(NetzTexte.t("d.quelle"), style = MaterialTheme.typography.labelLarge, color = sv, modifier = Modifier.padding(top = 10.dp))
            (listOf(k.quelle) + k.weitere).forEach { q ->
                Text(
                    q.name, style = MaterialTheme.typography.bodyMedium, color = LocalSignal.current.link, textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(top = 4.dp).textKlick(0.dp, role = Role.Button) { runCatching { links.openUri(q.url) } },
                )
            }
        }
    }
}

@Composable
private fun BlattZeile(label: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(wert, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(2f))
    }
}
