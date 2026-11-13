package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Blattaufbau
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.LaenderZusatz
import de.troetenrepublik.app.engine.LaenderZusatz.Einheit
import de.troetenrepublik.app.engine.LaenderZusatz.Gruppe
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi

// Text eines Werts ohne Jahr; das Jahr steht in der Zusatzzeile der Kachel.
private fun wertText(z: LaenderZusatz.Zeile): String {
    val k = z.kennzahl
    val v = z.wert
    return when (k.einheit) {
        Einheit.PROZENT -> if (k.vorzeichen && v < 0) Fmt.signedPct(v, 1) else Fmt.pct(v, 1)
        Einheit.JAHRE -> S.lbJahreKurz(Fmt.num(v, 1))
        Einheit.ZAHL -> Fmt.num(v, 2)
        Einheit.TONNEN -> Fmt.num(v, 1) + " t"
    }
}

// Abschnitt Land: Landesdaten (Wikidata) und Steckbrief (Flaeche, Zeitzonen, Amtssprachen) als Felder.
@Composable
internal fun LaenderSteckbrief(state: AppState, land: GlobusLand, g: LaenderGeladen) {
    val deutsch = I18n.locale.language == "de"
    val daten = remember(land.a2, S) { state.globusLandesdaten(land.a2) }?.second
    var info by remember(land) { mutableStateOf<Pair<String, String>?>(null) }
    val steck = g.mehr?.steck
    val felder = ArrayList<FeldDaten>()
    if (daten != null) {
        daten.hauptstadt?.let { felder += FeldDaten(S.globusHauptstadt, it) }
        daten.waehrung?.let { felder += FeldDaten(S.globusWaehrung, it) }
        daten.form?.let { f -> val t = S.globusRegierungsform; felder += FeldDaten(t, S.globusFormName(f, daten.bundesstaat), onClick = { info = t to S.globusInfoRegierungsform }) }
    }
    val flaeche = steck?.flaeche?.takeIf { it > 0 }
    if (flaeche != null) felder += FeldDaten(S.globusMehrFlaeche, Fmt.num(flaeche, if (flaeche < 100) 1 else 0) + " km²", onClick = { info = S.globusMehrFlaeche to S.globusMehrFlaecheInfo })
    if (daten != null) {
        if (daten.oberhaupt != null && daten.oberhaupt == daten.regierungschef) {
            val t = S.globusOberhauptChef
            felder += FeldDaten(t, daten.oberhaupt, onClick = { info = t to S.globusInfoOberhauptChef })
        } else {
            daten.oberhaupt?.let { w -> val t = S.globusOberhaupt; felder += FeldDaten(t, w, onClick = { info = t to S.globusInfoOberhaupt }) }
            daten.regierungschef?.let { w -> val t = S.globusRegierungschef; felder += FeldDaten(t, w, onClick = { info = t to S.globusInfoRegierungschef }) }
        }
    }
    val versaetze = steck?.let { LaenderZusatz.versaetze(it.zonen, System.currentTimeMillis()) }.orEmpty()
    if (versaetze.isNotEmpty()) {
        val text = if (versaetze.size <= 3) versaetze.joinToString(", ") { LaenderZusatz.utcText(it) }
        else LaenderZusatz.utcText(versaetze.first()) + " " + S.globusMehrBis + " " + LaenderZusatz.utcText(versaetze.last())
        val titel = S.globusMehrZeitzone(versaetze.size)
        felder += FeldDaten(titel, text, onClick = { info = titel to S.globusMehrZeitzoneInfo })
    }
    val sprachen = steck?.sprachen.orEmpty()
    if (sprachen.isNotEmpty() && steck != null) {
        val namen = sprachen.joinToString(", ") { if (deutsch) it.second else it.first } + if (steck.weitere > 0) " " + S.globusMehrWeitere(steck.weitere) else ""
        val titel = S.globusMehrSprachen(sprachen.size + steck.weitere)
        felder += FeldDaten(titel, namen, onClick = { info = titel to S.globusMehrSprachenInfo })
    }
    if (daten != null && daten.mitglieder.isNotEmpty()) {
        val t = S.globusMitgliedschaften
        felder += FeldDaten(t, daten.mitglieder.joinToString(", ") { S.globusOrgName(it) }, onClick = { info = t to S.globusInfoMitgliedschaften })
    }
    if (felder.isEmpty()) return
    LaenderAbschnitt(S.globusLandTitel, "land") {
        FeldRaster(felder)
        QuellenLeiste(listOf(LaenderQuellen.wikidata()))
    }
    info?.let { (t, x) -> MarktInfoBlatt(t, x) { info = null } }
}

// Abschnitt Handel: Exportgueter und Abnehmer als Balken (aus der gebuendelten Datei von UN Comtrade). Die Balken zeigen
// den Anteil im Massstab 0 bis 100 %.
@Composable
internal fun LaenderHandel(land: GlobusLand, g: LaenderGeladen) {
    val deutsch = I18n.locale.language == "de"
    val d = g.mehr ?: return
    val h = d.handel ?: return
    var info by remember(land) { mutableStateOf<Pair<String, String>?>(null) }
    val partner = h.partner.mapNotNull { (a3, p) -> d.karte?.nachA3(a3)?.let { it.name(deutsch) to p } }
    if (h.gueter.isEmpty() && partner.isEmpty()) return
    val spiegel = if (h.spiegel) " " + S.globusMehrSpiegel else ""
    LaenderAbschnitt(S.globusMehrHandel, "handel") {
        if (h.wert > 0) {
            val titel = S.globusMehrExporte(h.jahr)
            LbZeile(titel, Fmt.dollarKompakt(h.wert)) { info = titel to S.globusMehrExporteInfo }
        }
        if (h.gueter.isNotEmpty()) {
            LbUnterkopf(S.globusMehrGueter)
            BalkenListe(h.gueter.mapIndexed { i, (gr, p) ->
                val titel = S.globusMehrGruppe(gr)
                BalkenZeile(titel, Blattaufbau.balkenAnteil(p / 10.0), Fmt.pct(p / 10.0, 1), AnteilsFarben.folge(i)) { info = titel to S.globusMehrGruppeInfo(gr, h.jahr) + spiegel }
            })
        }
        if (partner.isNotEmpty()) {
            LbUnterkopf(S.globusMehrPartner)
            BalkenListe(partner.mapIndexed { i, (name, p) ->
                BalkenZeile(name, Blattaufbau.balkenAnteil(p / 10.0), Fmt.pct(p / 10.0, 1), AnteilsFarben.folge(i)) { info = name to S.globusMehrPartnerInfo(name, h.jahr) + spiegel }
            })
        }
        if (h.spiegel) LbHinweis(S.globusMehrSpiegel)
        QuellenLeiste(listOf(LaenderQuellen.comtrade()))
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Abschnitte Gesellschaft, Staat, Energie und Klima als Kacheln, dazu Feiertage (aufklappbar) und Schlagzeilen.
@Composable
internal fun LaenderMehr(land: GlobusLand, g: LaenderGeladen) {
    val deutsch = I18n.locale.language == "de"
    var info by remember(land) { mutableStateOf<Pair<String, String>?>(null) }
    var artikel by remember(land) { mutableStateOf<MarketApi.NewsItem?>(null) }
    val jahr = remember { java.time.LocalDate.now().year }
    if (!g.mehrFertig) {
        Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        return
    }
    val d = g.mehr ?: return
    val steck = d.steck
    fun zeilen(gr: Gruppe) = LaenderZusatz.zeilen(gr, land.a3, d.reihen, steck?.medianalter, jahr)
    fun kachel(z: LaenderZusatz.Zeile): KachelDaten {
        val titel = S.globusMehrLabel(z.kennzahl)
        return KachelDaten(titel, wertText(z), if (z.mitJahr) "${z.jahr}" else null) { info = titel to S.globusMehrInfo(z.kennzahl) }
    }
    val wb = LaenderQuellen.weltbank(land.a2)

    val gesellschaft = zeilen(Gruppe.GESELLSCHAFT)
    if (gesellschaft.isNotEmpty()) LaenderAbschnitt(S.globusMehrGesellschaft, "gesellschaft") {
        KachelRaster(gesellschaft.map(::kachel), 2)
        QuellenLeiste(listOfNotNull(wb, LaenderQuellen.unWpp().takeIf { gesellschaft.any { it.kennzahl == LaenderZusatz.Kennzahl.MEDIANALTER } }))
    }
    val staat = zeilen(Gruppe.STAAT)
    if (staat.isNotEmpty()) LaenderAbschnitt(S.globusMehrStaat, "staat") {
        KachelRaster(staat.map(::kachel), 2)
        QuellenLeiste(listOf(wb))
    }
    val energie = zeilen(Gruppe.ENERGIE)
    if (energie.isNotEmpty()) LaenderAbschnitt(S.globusMehrEnergie, "energie") {
        val erneuerbar = energie.firstOrNull { it.kennzahl == LaenderZusatz.Kennzahl.ERNEUERBAR }
        if (erneuerbar != null && erneuerbar.wert in 0.0..100.0) {
            val titel = S.globusMehrLabel(erneuerbar.kennzahl)
            RingDiagramm(
                listOf(
                    RingTeil(titel, erneuerbar.wert, Kategorie.farbe(3), Fmt.pct(erneuerbar.wert, 1)) { info = titel to S.globusMehrInfo(erneuerbar.kennzahl) },
                    RingTeil(S.lbUebrige, 100.0 - erneuerbar.wert, MaterialTheme.colorScheme.outlineVariant, Fmt.pct(100.0 - erneuerbar.wert, 1)),
                ),
                mitte = Fmt.pct(erneuerbar.wert, 0),
            )
        }
        KachelRaster(energie.filter { it !== erneuerbar }.map(::kachel), 2)
        QuellenLeiste(listOf(wb))
    }

    // Feiertage
    if (d.feiertage.isNotEmpty()) LaenderAbschnitt(S.globusMehrFeiertage, "feiertage", startOffen = false) {
        for (f in d.feiertage) {
            val titel = LaenderZusatz.feiertagName(f, land.a2, deutsch)
            val ts = f.datum.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            LbZeile(titel, Fmt.date(ts)) { info = titel to S.globusMehrFeiertagInfo }
        }
        QuellenLeiste(listOf(LaenderQuellen.nager()))
    }

    // Schlagzeilen: antippen oeffnet den Artikel wie in den News
    if (d.meldungen.isNotEmpty()) LaenderAbschnitt(S.globusMehrSchlagzeilen, "schlagzeilen") {
        d.meldungen.forEachIndexed { i, m ->
            if (i > 0) Trennlinie()
            Column(Modifier.fillMaxWidth().clickable { artikel = MarketApi.NewsItem(title = m.titel, link = m.link, published = m.zeit, source = m.quelle) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(m.titel, style = MaterialTheme.typography.titleSmall.schlagzeile(), lineHeight = 22.sp)
                PunktText(
                    listOfNotNull(m.zeit.takeIf { it > 0 }?.let { Fmt.relative(it) }, m.quelle.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        QuellenLeiste(listOf(LaenderQuellen.news()))
    }

    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
    artikel?.let { NewsSheet(it, onDismiss = { artikel = null }) }
}
