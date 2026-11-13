package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.FirmaTexte
import de.troetenrepublik.app.market.Arbeitsschutz
import de.troetenrepublik.app.market.ArbeitsschutzAbruf
import de.troetenrepublik.app.market.Auftragsbestand
import de.troetenrepublik.app.market.AuftragsbestandAbruf
import de.troetenrepublik.app.market.JahresberichtAbruf
import de.troetenrepublik.app.market.Klagen
import de.troetenrepublik.app.market.KlagenAbruf
import de.troetenrepublik.app.market.Pcaob
import de.troetenrepublik.app.market.PcaobAbruf
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.market.Verfahren
import de.troetenrepublik.app.market.VerfahrenAbruf
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

// Drei Bausteine im Block „Unternehmen“ einer US-Aktie: Auftragsbestand, Wirtschaftsprüfer, Rechtsstreit und Strafen. Jeder
// Baustein steht als eigener Ladeeintrag direkt vor seinen Zeilen (UnternehmenBlock.kt) und erscheint nur mit Daten. Die Abrufe
// laufen im Bereich der Seite (SeitenMerker.abrufBisFertig), die Modelle liegen im Merker.

private fun tagText(iso: String): String = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso

private fun heute(): LocalDate = LocalDate.now(ZoneOffset.UTC)

private fun datei(state: AppState, name: String): SichereDatei? = runCatching { SichereDatei(java.io.File(state.store.ordner, name)) }.getOrNull()

// ------------------------------------------------------------ Auftragsbestand

@Composable
internal fun AuftragsbestandKarte(state: AppState, symbol: String, merker: SeitenMerker?) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var erg by gemerkt<Auftragsbestand.Ergebnis?>(merker, "fa-ab/$symbol") { null }
    var geladen by gemerkt(merker, "fa-ab/$symbol/geladen") { false }
    LaunchedEffect(symbol, settings.secKontakt) {
        val lade: suspend CoroutineScope.() -> Unit = lade@{
            val kennung = SecInsider.kennung(settings.secKontakt) ?: return@lade
            val cik = runCatching { state.ipoCik(symbol) }.getOrNull() ?: return@lade
            erg = runCatching { AuftragsbestandAbruf.laden(cik, kennung) }.getOrNull()
            geladen = true
        }
        if (geladen) return@LaunchedEffect
        if (merker != null) merker.abrufBisFertig("fa-ab/$symbol", { geladen }, lade) else lade()
    }
    val modell = remember(erg) { erg?.let { auftragsModell(it) } }
    ModellAusgabe(merker, "fa-auftrag", modell)
}

internal fun auftragsModell(e: Auftragsbestand.Ergebnis): ListenModell {
    val zeilen = ArrayList<ZeilenDaten>()
    zeilen.add(ZeilenDaten("kern") { AuftragsKern(e) })
    if (e.verlauf.size >= 2) zeilen.add(ZeilenDaten("verlauf") { AuftragsLinie(e.verlauf) })
    zeilen.add(ZeilenDaten("stand") { KeyValueRow(FirmaTexte.t("ab.stand"), tagText(e.aktuell.ende)) })
    e.vorjahr?.let { v -> zeilen.add(ZeilenDaten("vorjahr") { KeyValueRow(FirmaTexte.t("ab.vorjahr"), Fmt.dollarKompakt(v.wert)) }) }
    e.umsatz?.let { u -> zeilen.add(ZeilenDaten("umsatz") { KeyValueRow(FirmaTexte.t("ab.umsatz", e.umsatzJahr?.toString().orEmpty()).trim(), Fmt.dollarKompakt(u)) }) }
    return ListenModell(titel = FirmaTexte.t("ab.titel"), gross = true, kern = null, zeilen = zeilen, immerOffen = true, info = FirmaTexte.t("ab.info"))
}

// Kernzeile als Text: „38,6 Mrd. $, das 1,6-Fache des Jahresumsatzes“
internal fun auftragsKernText(e: Auftragsbestand.Ergebnis): String =
    listOfNotNull(Fmt.dollarKompakt(e.aktuell.wert), e.faktor?.let { FirmaTexte.t("ab.faktor", Fmt.num(it, 1)) }).joinToString(", ")

// Kernzeile: Betrag, rechts die Veränderung zum Vorjahr mit Dreieck, darunter das Vielfache des Jahresumsatzes
@Composable
private fun AuftragsKern(e: Auftragsbestand.Ergebnis) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.dollarKompakt(e.aktuell.wert), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            e.aenderung?.let { a -> ChangeValue(a, Fmt.signedPct(a, 1), style = MaterialTheme.typography.titleSmall) }
        }
        e.faktor?.let { f -> Text(FirmaTexte.t("ab.faktor", Fmt.num(f, 1)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// Verlauf der Quartalsstände als Linie über die ganze Breite, Anfang und Ende mit Datum und Wert beschriftet
@Composable
private fun AuftragsLinie(punkte: List<Auftragsbestand.Punkt>) {
    val farbe = MaterialTheme.colorScheme.primary
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val werte = remember(punkte) { punkte.map { it.wert } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val min = werte.min()
            val spanne = (werte.max() - min).takeIf { it > 0 } ?: 1.0
            val rand = 6.dp.toPx()
            val xy = werte.mapIndexed { i, v ->
                Offset(
                    rand + (size.width - 2 * rand) * i / (werte.size - 1),
                    rand + (size.height - 2 * rand) * (1f - ((v - min) / spanne).toFloat()),
                )
            }
            val pfad = Path().apply { xy.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(pfad, farbe, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(farbe, 3.5.dp.toPx(), xy.last())
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tagText(punkte.first().ende) + PUNKT + Fmt.dollarKompakt(punkte.first().wert), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
            Text(tagText(punkte.last().ende) + PUNKT + Fmt.dollarKompakt(punkte.last().wert), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
        }
    }
}

// ------------------------------------------------------------ Wirtschaftsprüfer

@Composable
internal fun PrueferKarte(state: AppState, symbol: String, merker: SeitenMerker?) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var bericht by gemerkt<JahresberichtAbruf.Ergebnis?>(merker, "fa-pr/$symbol") { null }
    var cik by gemerkt(merker, "fa-pr/$symbol/cik") { "" }
    var geladen by gemerkt(merker, "fa-pr/$symbol/geladen") { false }
    val indexStand by PcaobAbruf.stand.collectAsStateWithLifecycle()
    val laeuft by PcaobAbruf.laeuft.collectAsStateWithLifecycle()
    val pdatei = remember(state) { datei(state, "pcaob_index.txt") }
    LaunchedEffect(symbol, settings.secKontakt) {
        val lade: suspend CoroutineScope.() -> Unit = lade@{
            val kennung = SecInsider.kennung(settings.secKontakt) ?: return@lade
            val c = runCatching { state.ipoCik(symbol) }.getOrNull() ?: return@lade
            withContext(Dispatchers.Default) { PcaobAbruf.vorhanden(pdatei) }
            cik = c
            bericht = runCatching {
                JahresberichtAbruf.laden(c, kennung, heute().year, datei = datei(state, "jahresbericht_befund.json"))
            }.getOrNull()
            geladen = true
        }
        if (geladen) return@LaunchedEffect
        if (merker != null) merker.abrufBisFertig("fa-pr/$symbol", { geladen }, lade) else lade()
    }
    val partner = remember(cik, indexStand) { if (cik.isEmpty()) null else PcaobAbruf.vorhanden(pdatei)?.fuer(cik) }
    val kannLaden = remember(cik, indexStand, laeuft) { cik.isNotEmpty() && PcaobAbruf.veraltet(pdatei, heute()) }
    val uri = LocalUriHandler.current
    val modell = remember(bericht, partner, kannLaden, laeuft) {
        pruefModell(bericht, partner, if (kannLaden) laeuft else null, uri, { PcaobAbruf.starten(pdatei, heute()) })
    }
    if (partner != null) QuellenMelden(QuelleLink(Pcaob.QUELLE, Pcaob.QUELLE_ADRESSE))
    ModellAusgabe(merker, "fa-pruefer", modell)
}

private const val PCAOB_MB = "13"

// Prüferwechsel (8-K Item 4.01) zeigt schon das Thema „Warnzeichen“ und steht hier nicht doppelt.
// laeuft: null, wenn es nichts zu laden gibt; sonst ob der Abruf gerade läuft
internal fun pruefModell(
    jb: JahresberichtAbruf.Ergebnis?, partner: Pcaob.Ergebnis?, laeuft: Boolean?,
    uri: androidx.compose.ui.platform.UriHandler, laden: () -> Unit,
): ListenModell? {
    val b = jb?.befund
    val name = b?.pruefer?.takeIf { it.isNotBlank() } ?: partner?.aktuell?.firma.orEmpty()
    val warn = listOf(b?.zweifel == true, b?.schwaeche == true).count { it }
    if (name.isBlank() && warn == 0 && (b?.cams.isNullOrEmpty())) return null
    val jahre = b?.seit?.takeIf { it > 0 }?.let { heute().year - it }
    val unten = when {
        warn > 0 -> FirmaTexte.t("pr.warn", warn.toString())
        b != null && b.seit > 0 -> FirmaTexte.t("pr.seitWert", b.seit.toString(), (jahre ?: 0).toString())
        else -> null
    }
    val zeilen = ArrayList<ZeilenDaten>()
    if (b != null && b.seit > 0) zeilen.add(ZeilenDaten("seit") { KeyValueRow(FirmaTexte.t("pr.seit"), FirmaTexte.t("pr.seitWert", b.seit.toString(), (jahre ?: 0).toString())) })
    if (b != null && b.ort.isNotBlank()) zeilen.add(ZeilenDaten("ort") { KeyValueRow(FirmaTexte.t("pr.ort"), b.ort) })
    if (partner != null) {
        val a = partner.aktuell
        if (a.partner.isNotBlank()) zeilen.add(ZeilenDaten("partner") { KeyValueRow(FirmaTexte.t("pr.partner"), a.partner) })
        val seit = partner.partnerSeit
        // Form AP gibt es erst seit 2017, der Index hält die letzten vier Jahre
        val ganz = partner.eintraege.size >= 4 && partner.eintraege.all { it.partnerId == a.partnerId && it.firmaId == a.firmaId }
        if (a.partner.isNotBlank() && seit > 0) zeilen.add(ZeilenDaten("partnerseit") {
            KeyValueRow(FirmaTexte.t("pr.partnerSeit"), if (ganz) FirmaTexte.t("pr.partnerSeitFrueher", seit.toString()) else seit.toString())
        })
        a.andere.forEachIndexed { i, x -> zeilen.add(ZeilenDaten("andere-" + x.firma) { KeyValueRow(x.firma, FirmaTexte.t("pr.anteil", x.ab.toString()), hint = if (i == 0) FirmaTexte.t("pr.andere") else null) }) }
    } else if (laeuft != null) {
        zeilen.add(ZeilenDaten("partner-laden") {
            KeyValueRow(
                FirmaTexte.t("pr.partner"), if (laeuft) FirmaTexte.t("pr.laedt") else FirmaTexte.t("pr.laden", PCAOB_MB),
                valueColor = MaterialTheme.colorScheme.primary, onClick = if (laeuft) null else laden,
            )
        })
    }
    if (b?.zweifel == true) zeilen.add(ZeilenDaten("zweifel") { KeyValueRow(FirmaTexte.t("pr.zweifel"), FirmaTexte.t("pr.zweifelJa"), valueColor = LocalSignal.current.warn) })
    if (b?.schwaeche == true) zeilen.add(ZeilenDaten("schwaeche") { KeyValueRow(FirmaTexte.t("pr.schwaeche"), FirmaTexte.t("pr.schwaecheJa"), valueColor = LocalSignal.current.warn) })
    if (b != null && b.cams.isNotEmpty()) zeilen.add(ZeilenDaten("cams") { TitelListe(FirmaTexte.t("pr.cams"), b.cams) })
    if (jb != null && b != null) zeilen.add(ZeilenDaten("bericht") {
        KeyValueRow(FirmaTexte.t("pr.bericht"), FirmaTexte.t("pr.berichtWert", jb.stichtag.take(4)), onClick = { runCatching { uri.openUri(jb.adresse) } })
    })
    if (zeilen.isEmpty()) return null
    return ListenModell(
        titel = FirmaTexte.t("pr.titel"), gross = true,
        kern = KernZeile(listOf(KernTeil(name.ifBlank { FirmaTexte.t("pr.bericht") })), unten?.let { KernTeil(it) }),
        zeilen = zeilen, immerZu = true, info = FirmaTexte.t("pr.info"),
    )
}

// Bezeichnung mit einer Liste von Titeln darunter; die Titel brechen um und bleiben ganz
@Composable
private fun TitelListe(label: String, titel: List<String>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        titel.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)) }
    }
}

// ------------------------------------------------------------ Rechtsstreit und Strafen

@Composable
internal fun RechtsstreitKarte(state: AppState, symbol: String, firma: String, merker: SeitenMerker?) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var erg by gemerkt<Verfahren.Ergebnis?>(merker, "fa-rs/$symbol") { null }
    var geladen by gemerkt(merker, "fa-rs/$symbol/geladen") { false }
    var secFertig by gemerkt(merker, "fa-rs/$symbol/sec") { false }
    // OSHA-Strafen (Arbeitsministerium), nur mit Schluessel unter Zugaenge; dolFertig: fuer diesen Schluessel abgefragt
    var osha by gemerkt<Arbeitsschutz.Ergebnis?>(merker, "fa-rs/$symbol/osha") { null }
    var dolFertig by gemerkt(merker, "fa-rs/$symbol/dol") { "" }
    // Klagen und Urteile: frei (EU-Gerichte, CMA, Justizministerium) und mit Token (CourtListener, Bundesgerichte)
    var frei by gemerkt<Klagen.Ergebnis?>(merker, "fa-rs/$symbol/kl") { null }
    var freiFertig by gemerkt(merker, "fa-rs/$symbol/klf") { false }
    var gericht by gemerkt<Klagen.Ergebnis?>(merker, "fa-rs/$symbol/cl") { null }
    var clFertig by gemerkt(merker, "fa-rs/$symbol/clt") { "" }
    LaunchedEffect(symbol, settings.secKontakt, settings.keyDol, settings.keyCourtListener) {
        val dol = settings.keyDol.trim()
        val cl = settings.keyCourtListener.trim()
        val kennung = SecInsider.kennung(settings.secKontakt)
        val fertig: () -> Boolean = { geladen && (kennung == null || secFertig) && (dol.isEmpty() || dolFertig == dol) && (cl.isEmpty() || clFertig == cl) }
        val lade: suspend CoroutineScope.() -> Unit = lade@{ kotlinx.coroutines.coroutineScope {
            // Klagen und Urteile laufen neben dem SEC-Teil; Treffer aus CMA und DOJ erscheinen vor der langsamen EU-Abfrage
            val freiAbruf = if (freiFertig) null else async {
                frei = runCatching { KlagenAbruf.laden(firma) { t -> frei = t.takeIf { !it.leer } } }.getOrNull()?.takeIf { !it.leer } ?: frei
                freiFertig = true
            }
            if (kennung != null && !secFertig) {
                val cik = runCatching { state.ipoCik(symbol) }.getOrNull()
                if (cik != null) {
                    val jb = runCatching {
                        JahresberichtAbruf.laden(cik, kennung, heute().year, datei = datei(state, "jahresbericht_befund.json"))
                    }.getOrNull()
                    erg = runCatching {
                        VerfahrenAbruf.laden(cik, firma, kennung, jb?.befund?.verfahren.orEmpty(), jb?.adresse.orEmpty())
                    }.getOrNull()?.takeIf { !it.leer }
                }
                secFertig = true
            }
            if (dol.isNotEmpty() && dolFertig != dol) {
                osha = runCatching { ArbeitsschutzAbruf.laden(firma, dol, state.kontingent) }.getOrNull()
                dolFertig = dol
            }
            if (cl.isNotEmpty() && clFertig != cl) {
                gericht = runCatching { KlagenAbruf.gericht(firma, cl, holen = { u -> state.weitere.gerichtsakten(u, cl) }) }.getOrNull()?.takeIf { !it.leer }
                clFertig = cl
            }
            freiAbruf?.await()
            geladen = true
        } }
        if (fertig()) return@LaunchedEffect
        if (merker != null) merker.abrufBisFertig("fa-rs/$symbol", fertig, lade) else lade()
    }
    val uri = LocalUriHandler.current
    if (osha != null) QuellenMelden(NennQuellen.OSHA)
    val klagen = remember(frei, gericht) { Klagen.zusammen(frei, gericht) }
    val quellen = remember(klagen) { klagen?.faelle.orEmpty().map { it.quelle }.distinct().map { klagenQuelle(it) } }
    if (quellen.isNotEmpty()) QuellenMelden(*quellen.toTypedArray())
    val modell = remember(erg, osha, klagen) {
        if (erg == null && osha == null && klagen == null) null
        else rechtsModell((erg ?: Verfahren.Ergebnis(emptyList(), null, null, null)).copy(arbeitsschutz = osha), firma, uri, klagen, erg != null)
    }
    ModellAusgabe(merker, "fa-recht", modell)
}

private fun dojAdresse(firma: String): String = "https://www.justice.gov/search?keys=" + java.net.URLEncoder.encode(Verfahren.kurzname(firma), "UTF-8")

// klagen: Klagen, Urteile und Behoerdenentscheidungen (market/FirmaKlagen.kt); us: Ergebnis aus den SEC-Quellen liegt vor
internal fun rechtsModell(
    e: Verfahren.Ergebnis, firma: String, uri: androidx.compose.ui.platform.UriHandler,
    klagen: Klagen.Ergebnis? = null, us: Boolean = false, stichtag: LocalDate = heute(),
): ListenModell {
    val anzahl = e.eintraege.count { it.art != Verfahren.Art.UMWELT && it.art != Verfahren.Art.ARBEITSSCHUTZ }
    val teile = ArrayList<KernTeil>()
    if (klagen != null && klagen.offeneBund > 0) {
        teile.add(KernTeil(FirmaTexte.t(if (klagen.mehr) "rs.offenBundMind" else "rs.offenBund", klagen.offeneBund.toString())))
    }
    if (anzahl > 0) teile.add(KernTeil(FirmaTexte.t("rs.verfahren", anzahl.toString())))
    e.rueckstellung?.let { teile.add(KernTeil(FirmaTexte.t("rs.rueck", Fmt.dollarKompakt(it)))) }
    val osha = e.arbeitsschutz
    if (osha != null) teile.add(KernTeil(FirmaTexte.t(if (osha.mindestens) "rs.oshaMind" else "rs.osha", Fmt.dollarKompakt(osha.summe))))
    val rest = ArrayList<String>()
    e.umwelt?.let { rest.add(FirmaTexte.t("rs.bussgeld", Fmt.dollarKompakt(it))) }
    val vergleiche = klagen?.strafen(stichtag) ?: 0.0
    if (vergleiche > 0) rest.add(FirmaTexte.t("rs.vergleiche", Fmt.dollarKompakt(vergleiche)))
    if (teile.isEmpty() && rest.isNotEmpty()) teile.add(KernTeil(rest.removeAt(0)))
    if (teile.isEmpty() && klagen != null && !klagen.leer) teile.add(KernTeil(FirmaTexte.t("rs.verfahren", klagen.faelle.size.toString())))
    val unten = if (rest.isEmpty()) null else KernTeil(rest.joinToString(PUNKT))
    val zeilen = ArrayList<ZeilenDaten>()
    e.rueckstellung?.let { r -> zeilen.add(ZeilenDaten("rueck") { KeyValueRow(FirmaTexte.t("rs.rueckZeile"), Fmt.dollarKompakt(r)) }) }
    e.moeglich?.let { m -> zeilen.add(ZeilenDaten("moeglich") { KeyValueRow(FirmaTexte.t("rs.moeglich"), Fmt.dollarKompakt(m)) }) }
    // Grafiken und Faelle: SEC-Meldungen zaehlen in den Verfahren je Jahr mit, OSHA-Strafen in den Strafen je Jahr
    if (klagen != null) {
        val sec = e.eintraege.filter { it.art == Verfahren.Art.SEC_URTEIL || it.art == Verfahren.Art.SEC_VERFAHREN }.map { it.jahr }.filter { it > 0 }
        val osStrafen = osha?.faelle.orEmpty().mapNotNull { f -> f.datum.take(4).toIntOrNull()?.let { it to f.strafe } }
        zeilen.addAll(klagenZeilen(klagen, sec, osStrafen, stichtag, uri))
    }
    e.sortiert.forEachIndexed { i, v -> zeilen.add(ZeilenDaten("v$i-" + v.titel) { VerfahrenZeile(v, uri) }) }
    if (osha != null) {
        zeilen.add(ZeilenDaten("osha-zahl") { KeyValueRow(FirmaTexte.t("rs.oshaInsp"), osha.inspektionen.toString()) })
        osha.faelle.forEach { f -> zeilen.add(ZeilenDaten("osha-" + f.nummer) { VerfahrenZeile(oshaEintrag(f), uri) }) }
    }
    if (us && Klagen.eindeutig(firma)) {
        zeilen.add(ZeilenDaten("cl-web") { KeyValueRow(FirmaTexte.t("rs.cl"), FirmaTexte.t("rs.clWert"), valueColor = MaterialTheme.colorScheme.primary, onClick = { runCatching { uri.openUri(Klagen.clWeb(firma)) } }) })
    }
    zeilen.add(ZeilenDaten("doj") { KeyValueRow(FirmaTexte.t("rs.doj"), FirmaTexte.t("rs.dojWert"), valueColor = MaterialTheme.colorScheme.primary, onClick = { runCatching { uri.openUri(dojAdresse(firma)) } }) })
    return ListenModell(
        titel = FirmaTexte.t("rs.titel"), gross = true,
        kern = if (teile.isEmpty()) null else KernZeile(teile, unten),
        zeilen = zeilen, immerZu = teile.isNotEmpty(),
        info = FirmaTexte.t("rs.info") + (if (osha != null) " " + FirmaTexte.t("rs.infoOsha") else "") + (if (klagen != null) " " + FirmaTexte.t("rs.infoKlagen") else ""),
        kappe = 14,
    )
}

// Ein Fall aus den OSHA-Daten als Zeile: Betrieb, Strafe rechts, darunter Ort, Art des Verstosses mit Vorschrift und Datum
private fun oshaEintrag(f: Arbeitsschutz.Fall): Verfahren.Eintrag {
    val art = when (f.art) {
        "W" -> FirmaTexte.t("rs.vW")
        "R" -> FirmaTexte.t("rs.vR")
        "S" -> FirmaTexte.t("rs.vS")
        "O" -> FirmaTexte.t("rs.vO")
        else -> ""
    }
    val menge = if (f.verstoesse == 1) FirmaTexte.t("rs.verstoss1") else FirmaTexte.t("rs.verstoss", f.verstoesse.toString())
    return Verfahren.Eintrag(
        Verfahren.Art.ARBEITSSCHUTZ, Arbeitsschutz.lesbar(f.betrieb), listOf(f.ort.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }, f.staat)
            .filter { it.isNotBlank() }.joinToString(", "),
        f.datum.take(4).toIntOrNull() ?: 0, f.strafe, f.link, f.datum, listOf(art, menge, f.norm).filter { it.isNotBlank() }.joinToString(", "),
    )
}

private fun artText(a: Verfahren.Art): String = FirmaTexte.t(
    when (a) {
        Verfahren.Art.BERICHT -> "rs.bericht"
        Verfahren.Art.SEC_URTEIL -> "rs.secUrteil"
        Verfahren.Art.SEC_VERFAHREN -> "rs.secVerfahren"
        Verfahren.Art.UMWELT -> "rs.umwelt"
        Verfahren.Art.ARBEITSSCHUTZ -> "rs.arbeit"
    },
)

// Titel (bricht um) mit Betrag rechts in einer Zeile, darunter Quelle, Gericht oder Behörde und Jahr
@Composable
private fun VerfahrenZeile(v: Verfahren.Eintrag, uri: androidx.compose.ui.platform.UriHandler) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (v.link.isNotBlank()) Modifier.clickable { runCatching { uri.openUri(v.link) } } else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(v.titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            v.betrag?.let { b ->
                Text(Fmt.dollarKompakt(b), style = MaterialTheme.typography.titleSmall, color = LocalSignal.current.warn, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 12.dp))
            }
        }
        PunktZeile(
            listOf(artText(v.art), v.stelle, v.zusatz, if (v.datum.isNotBlank()) tagText(v.datum) else if (v.jahr > 0) v.jahr.toString() else ""),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
