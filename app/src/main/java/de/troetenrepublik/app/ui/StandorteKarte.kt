package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.finderLandHoch
import de.troetenrepublik.app.finderLandRisiko
import de.troetenrepublik.app.firmaStandorte
import de.troetenrepublik.app.firmaStandorteGespeichert
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.StandorteTexte
import de.troetenrepublik.app.market.FirmaStandorte
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

// Standorte einer Firma (Wertpapierseite, Hintergrund): Toechter nach Rechtsordnung (Exhibit 21, GLEIF, Wikidata) und
// Laender aus Item 2 des Jahresberichts. Die Karte laedt erst, wenn der Hintergrund offen ist; ohne Daten entfaellt der
// Abschnitt. Ein Tipp oeffnet den Globus mit den Laendern der Firma (StandorteGlobusBlatt).
@Composable
internal fun StandorteKarte(state: AppState, symbol: String, name: String, onOpenRoute: (String) -> Unit, merker: SeitenMerker? = null) {
    var daten by gemerkt<FirmaStandorte.Daten?>(merker, "standorte/daten") { null }
    var karte by gemerkt<GlobusKarte?>(merker, "standorte/karte") { null }
    var risiko by gemerkt(merker, "standorte/risiko") { 0 }
    var geladen by gemerkt(merker, "standorte/geladen") { false }
    var globus by gemerkt(merker, "standorte/globus") { false }
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    LaunchedEffect(symbol, settings.secKontakt) {
        val lade: suspend CoroutineScope.() -> Unit = {
            try {
                if (daten == null) daten = withContext(Dispatchers.IO) { state.firmaStandorteGespeichert(symbol) }
                karte = state.globusKarte()
                val neu = state.firmaStandorte(symbol, name)
                if (neu != null) daten = neu
                try { state.finderLandRisiko() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                risiko++
            } finally {
                if (isActive) geladen = true
            }
        }
        if (geladen) return@LaunchedEffect
        if (merker != null) merker.abrufBisFertig("standorte", { geladen }, lade) else lade()
    }
    val deutsch = I18n.lang == Lang.DE
    val zeilen = remember(daten, karte, risiko, deutsch) {
        daten?.takeIf { !FirmaStandorte.leer(it) }?.let { d ->
            FirmaStandorte.zeilen(d, { a2 -> karte?.nachA2(a2)?.name(deutsch)?.let { landKurz(it) } ?: a2 }, { finderLandHoch(it) })
        }
    }
    val modell = remember(daten, zeilen, deutsch) {
        val d = daten
        if (d == null || zeilen == null) null else standorteModell(d, zeilen, name) { globus = true }
    }
    // Die Zeilen und das Globusblatt liegen ausserhalb dieses Ladeeintrags (listenAbschnitt, StandorteBlatt): Das Blatt braucht
    // die Zeilen im Merker, weil der Ladeeintrag beim Scrollen aus dem Bild faellt
    val zeilenZiel = merker?.zustand<List<FirmaStandorte.Zeile>?>("standorte/zeilen") { null }
    SideEffect { if (zeilenZiel != null && zeilenZiel.value !== zeilen) zeilenZiel.value = zeilen }
    ModellAusgabe(merker, "standorte", modell)
    val d = daten
    if (modell != null && d != null) {
        val t = FirmaStandorte.toechter(d)
        val quellen = buildList {
            if (d.ex21.isNotEmpty() || FirmaStandorte.item2(d).isNotEmpty()) add(QuelleLink(StandorteTexte.t("quelleSec"), "https://www.sec.gov/edgar/search/"))
            if (d.gleif.isNotEmpty()) add(QuelleLink(StandorteTexte.t("quelleGleif"), "https://www.gleif.org/en/about/open-data"))
            if (d.wikidata.isNotEmpty() || d.sitz.isNotEmpty()) add(QuelleLink(StandorteTexte.t("quelleWikidata"), "https://www.wikidata.org/wiki/Wikidata:Licensing"))
            if (FirmaStandorte.steuerToechter(t) > 0 || FirmaStandorte.STEUERLISTE.any { it in FirmaStandorte.item2(d) }) {
                add(QuelleLink(StandorteTexte.t("quelleSteuer"), "https://data.consilium.europa.eu/doc/document/ST-13854-2026-INIT/en/pdf"))
            }
        }
        QuellenMelden(*quellen.toTypedArray())
    }
    if (merker == null && globus && zeilen != null) {
        StandorteGlobusBlatt(state, StandorteTexte.t("globusTitel", name), zeilen, karte, { onOpenRoute("instrument/$it") }) { globus = false }
    }
}

// Globusblatt der Standorte, ausserhalb der Liste der Seite: Ein Tipp auf eine Zeile setzt den Zustand im Merker, auch wenn
// der Ladeeintrag der Karte gerade nicht im Bild ist.
@Composable
internal fun StandorteBlatt(state: AppState, name: String, onOpenRoute: (String) -> Unit, merker: SeitenMerker) {
    var offen by gemerkt(merker, "standorte/globus") { false }
    val zeilen by gemerkt<List<FirmaStandorte.Zeile>?>(merker, "standorte/zeilen") { null }
    val karte by gemerkt<GlobusKarte?>(merker, "standorte/karte") { null }
    val z = zeilen
    if (offen && z != null) {
        StandorteGlobusBlatt(state, StandorteTexte.t("globusTitel", name), z, karte, { onOpenRoute("instrument/$it") }) { offen = false }
    }
}

// Kernzeile eines Modells als ein Text, Teile mit Mittelpunkt getrennt
internal fun kernAlsText(k: KernZeile): String = (k.teile + listOfNotNull(k.unten)).joinToString(" · ") { it.text }

private fun standorteModell(d: FirmaStandorte.Daten, zeilen: List<FirmaStandorte.Zeile>, name: String, onGlobus: () -> Unit): ListenModell {
    val t = FirmaStandorte.toechter(d)
    val sl = FirmaStandorte.standortLaender(d)
    val risiko = FirmaStandorte.risiko(t, { finderLandHoch(it) })
    val steuer = FirmaStandorte.steuerToechter(t)
    val teile = ArrayList<KernTeil>()
    if (t.isNotEmpty()) teile += KernTeil(if (t.size == 1) StandorteTexte.t("toechter.1") else StandorteTexte.t("toechter.n", t.size.toString()))
    if (FirmaStandorte.item2(d).isNotEmpty()) teile += KernTeil(StandorteTexte.t("standorte.n", sl.size.toString()))
    if (teile.isEmpty()) teile += KernTeil(StandorteTexte.t("nurSitz", zeilen.firstOrNull { it.sitz }?.name ?: name))
    val unten = when {
        risiko.tochterGesamt > 0 && risiko.tochterHoch > 0 ->
            KernTeil(StandorteTexte.t("risiko", Fmt.pct(risiko.tochterHoch * 100.0 / risiko.tochterGesamt, 0)))
        steuer > 0 -> KernTeil(if (steuer == 1) StandorteTexte.t("steuer.1") else StandorteTexte.t("steuer.n", steuer.toString()))
        else -> null
    }
    val maximum = zeilen.maxOfOrNull { it.toechter }?.coerceAtLeast(1) ?: 1
    return ListenModell(
        titel = StandorteTexte.t("titel"), gross = true, kern = KernZeile(teile, unten),
        info = StandorteTexte.t("info"), immerZu = true,
        vorspann = {
            NavRow(StandorteTexte.t("globus"), "", onGlobus)
            if (risiko.tochterHoch > 0 && steuer > 0) {
                Text(
                    if (steuer == 1) StandorteTexte.t("steuer.1") else StandorteTexte.t("steuer.n", steuer.toString()),
                    style = MaterialTheme.typography.bodySmall, color = LocalSignal.current.warn,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        },
        zeilen = zeilen.map { z -> ZeilenDaten(z.a2) { StandortZeileAnsicht(z, maximum, onGlobus) } },
    )
}

private fun markierungen(z: FirmaStandorte.Zeile): String {
    val m = listOfNotNull(
        if (z.sitz) StandorteTexte.t("sitz") else null,
        if (z.hoch) StandorteTexte.t("hoch") else null,
        if (z.steuer) StandorteTexte.t("steuerGebiet") else null,
        if (z.toechter == 0 && !z.sitz) StandorteTexte.t("nurStandort") else null,
    )
    return if (m.isEmpty()) "" else " (" + m.joinToString(", ") + ")"
}

@Composable
private fun StandortZeileAnsicht(z: FirmaStandorte.Zeile, maximum: Int, onClick: () -> Unit) {
    val farbe = if (z.hoch || z.steuer) LocalSignal.current.warn else AnteilsFarben.folge(0)
    Box(Modifier.padding(horizontal = 20.dp)) {
        // Laender ohne Zahl (nur als Standort genannt): gleiche Zeile mit Namen links, ohne Wert und ohne Balken
        if (z.toechter == 0) {
            Row(Modifier.fillMaxWidth().zeilenKlick(onClick = onClick).padding(vertical = 5.dp)) {
                Text(z.name + markierungen(z), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
            return@Box
        }
        LageBalkenZeile(
            name = z.name + markierungen(z), wert = if (z.toechter > 0) z.toechter.toString() else "–",
            anteil = z.toechter.toDouble() / maximum, farbe = farbe, onClick = onClick,
        )
    }
}

// Globus mit den Laendern der Firma: Laender mit Toechtern in Ocker (je mehr, desto kraeftiger), Laender mit hohem Risiko
// oder auf der EU-Liste in der Warnfarbe, reine Standorte blass. Tipp auf ein Land oeffnet das Laenderblatt.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StandorteGlobusBlatt(
    state: AppState, titel: String, zeilen: List<FirmaStandorte.Zeile>, karte: GlobusKarte?, onOpen: (String) -> Unit, onDismiss: () -> Unit,
) {
    val signal = LocalSignal.current
    val gp = signal.globus
    val ocker = Kategorie.ocker.toArgb()
    val rot = signal.down.toArgb()
    var land by remember { mutableStateOf<GlobusLand?>(null) }
    val staende by state.globusStaende.collectAsStateWithLifecycle()
    val imf = remember { state.imfGespeichert() }
    val jahr = remember { java.time.LocalDate.now().year }
    val maximum = zeilen.maxOfOrNull { it.toechter }?.coerceAtLeast(1) ?: 1
    val nachA2 = remember(zeilen) { zeilen.associateBy { it.a2 } }
    val farben = remember(karte, nachA2, signal) {
        val k = karte
        if (k == null) emptyArray<Color>() else Array(k.laender.size) { i ->
            val z = nachA2[k.laender[i].a2]
            Color(
                when {
                    z == null -> gp.ohneDaten
                    z.hoch || z.steuer -> GlobusFarbe.mischen(gp.nullwert, rot, 0.45f + 0.55f * (z.toechter.toFloat() / maximum))
                    else -> GlobusFarbe.mischen(gp.nullwert, ocker, if (z.toechter > 0) 0.4f + 0.6f * (z.toechter.toFloat() / maximum) else 0.3f)
                },
            )
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(false),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item(key = "titel", contentType = "kopf") {
                Text(titel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            item(key = "kugel", contentType = "kugel") {
                if (karte == null) Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(gp.hintergrund)), contentAlignment = Alignment.Center) {
                    Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = Color(gp.ladeSchrift))
                } else GlobusAnsichtFlaeche(karte, farben, null, emptyList(), {}) { land = it }
            }
            item(key = "legende", contentType = "legende") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LegendeEintrag(Color(ocker), StandorteTexte.t("legendeNormal"))
                    if (zeilen.any { it.hoch }) LegendeEintrag(signal.down, StandorteTexte.t("hoch"))
                }
            }
            items(zeilen, key = { "z-" + it.a2 }, contentType = { "zeile" }) { z ->
                StandortZeileAnsicht(z, maximum) { karte?.nachA2(z.a2)?.let { land = it } }
            }
            item(key = "abstand", contentType = "abstand") { Box(Modifier.size(24.dp)) }
        }
    }
    land?.let { l ->
        val scroll = remember(l.a3) { LazyListState() }
        LaenderBlatt(
            state, l, staende, imf, jahr, scroll, false,
            onOpen = { s -> land = null; onDismiss(); onOpen(s) },
        ) { land = null }
    }
}

@Composable
private fun LegendeEintrag(farbe: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(farbe, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
    }
}
