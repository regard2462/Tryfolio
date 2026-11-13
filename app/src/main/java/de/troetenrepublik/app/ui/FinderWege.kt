package de.troetenrepublik.app.ui

import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Aktienfinder
import de.troetenrepublik.app.engine.Marktmacht
import de.troetenrepublik.app.engine.FinderBranchen
import de.troetenrepublik.app.engine.Vorschlagsdepot
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs

// Marktmacht, Entdecken je Branche und Vorschlagsdepot im Aktienfinder (0.77).

// Gruende der Marktmacht in Stichworten, die staerksten zuerst; nur Bausteine, die klar von 50 abweichen.
internal fun marktmachtTexte(bg: Marktmacht.Ergebnis): List<Pair<Boolean, String>> {
    val a = bg.angaben
    return bg.teile.entries.sortedByDescending { abs(it.value - 50.0) }.filter { abs(it.value - 50.0) >= MARKTMACHT_MINDEST }.mapNotNull { (teil, p) ->
        val gut = p > 50.0
        val text = when (teil) {
            Marktmacht.Teil.STELLUNG -> a.stellung?.let { s ->
                S.mmGrund("STELLUNG", gut, Fmt.pct(s.anteil, if (s.anteil < 10.0) 1 else 0), s.rang.toString(), "")
            }
            Marktmacht.Teil.PREISMACHT -> if (a.marge != null && a.branchenMarge != null) {
                S.mmGrund(
                    if (a.brutto) "PREIS_BRUTTO" else "PREIS_BETRIEB", gut, Fmt.pct(a.marge, 0), Fmt.pct(a.branchenMarge, 0),
                    if (a.jahre >= 2) S.mmJahre(a.jahreUeber, a.jahre) else "",
                )
            } else null
            Marktmacht.Teil.BESTAENDIGKEIT -> a.renditeMin?.let { S.mmGrund("BESTAENDIG", gut, Fmt.pct(it, 0), a.renditeJahre.toString(), "") }
        }
        text?.let { gut to it }
    }
}

private const val MARKTMACHT_MINDEST = 12.0

// Farbe der Stufe: hoch gruen, mittel in Schriftfarbe, gering gedaempft (geringe Marktmacht ist kein Verlustsignal).
@Composable
private fun stufeFarbe(s: Marktmacht.Stufe) = when (s) {
    Marktmacht.Stufe.HOCH -> LocalSignal.current.up
    Marktmacht.Stufe.MITTEL -> MaterialTheme.colorScheme.onSurface
    Marktmacht.Stufe.GERING -> MaterialTheme.colorScheme.onSurfaceVariant
}

// Zeile auf der Wertpapierseite bei den Chancen und Risiken: Stufe, Gruende in Stichworten, ein Satz zum Begriff.
@Composable
internal fun MarktmachtZeile(bg: Marktmacht.Ergebnis) {
    val stufe = bg.stufe ?: return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    // Die Erklärung des Begriffs steht hinter dem Info-Symbol, ein Tipp auf die Zeile zeigt sie
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { info = true }.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.mmTitel, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(16.dp))
            Spacer(Modifier.weight(1f))
            Text(S.mmStufe(stufe.name), style = MaterialTheme.typography.titleSmall, color = stufeFarbe(stufe), maxLines = 1, softWrap = false)
        }
        marktmachtTexte(bg).forEach { (_, t) -> Text(t, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp)) }
    }
    if (info) MarktInfoBlatt(S.mmTitel, S.mmErklaerung) { info = false }
}

// Block in der aufgeklappten Zeile des Aktienfinders: Stufe und Gruende wie die uebrigen Gruende.
@Composable
internal fun MarktmachtBlock(bg: Marktmacht.Ergebnis) {
    val stufe = bg.stufe ?: return
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(S.mmKurz(S.mmStufe(stufe.name)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = stufeFarbe(stufe))
        marktmachtTexte(bg).forEach { (gut, t) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                AmpelPunkt(
                    if (gut) AmpelZeichen.GUT else AmpelZeichen.ACHTUNG, if (gut) LocalSignal.current.up else LocalSignal.current.warn,
                    Modifier.padding(end = 8.dp),
                )
                Text(t, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// Ein Wert in den Wegen Branchen und Vorschlagsdepot: Punktzahl, Name, Marktmacht und Einordnung, der staerkste Grund;
// rechts optional eine Angabe (Gewicht). Ein Tipp oeffnet die Wertpapierseite.
@Composable
private fun WertZeile(e: Aktienfinder.Ergebnis, onOpen: (String) -> Unit, grund: List<String>, rechts: String? = null, mitLand: Boolean = true, waage: Int? = null, depot: Boolean = false) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val d = e.daten
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(d.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            Text(Fmt.num(e.gesamt, 0), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(d.name, style = MaterialTheme.typography.titleSmall)
            // Im Depot zwei kurze graue Zeilen, die Gruende stehen auf der Wertpapierseite
            if (depot) {
                PunktZeile(listOfNotNull(d.marktmacht?.stufe?.let { S.mmKurz(S.mmStufe(it.name)) }, S.industryName(d.branche)), color = grau)
                val zweite = listOfNotNull(S.countryLabel(d.land).takeIf { mitLand }, waage?.let { S.afWaage(it) })
                if (zweite.isNotEmpty()) PunktZeile(zweite, color = grau)
            } else PunktZeile(
                listOfNotNull(
                    d.marktmacht?.stufe?.let { S.mmKurz(S.mmStufe(it.name)) }, S.industryName(d.branche), S.countryLabel(d.land).takeIf { mitLand },
                    waage?.let { S.afWaage(it) },
                ),
                color = grau,
            )
            if (grund.isNotEmpty()) PunktZeile(grund, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
        if (rechts != null) {
            Spacer(Modifier.width(8.dp))
            Text(rechts, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
    }
}

// Weg „Branchen“: je Branche die besten Werte nach Gesamtpunktzahl; ein Tipp auf die Branche zeigt ihre Rangliste.
@Composable
internal fun FinderBranchenAnsicht(state: AppState, ergebnisse: List<Aktienfinder.Ergebnis>, onOpen: (String) -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val gruppen = remember(ergebnisse) { FinderBranchen.gruppen(ergebnisse) }
    var gewaehlt by remember { mutableStateOf<String?>(null) }
    var branchen by remember { mutableIntStateOf(8) }
    var werte by remember(gewaehlt) { mutableIntStateOf(20) }
    fun name(b: String) = if (b == FinderBranchen.SAMMEL) S.fbrSammel else S.industryName(b)
    val g = gewaehlt?.let { k -> gruppen.firstOrNull { it.branche == k } }
    if (g == null) {
        PunktText(S.fbrZahl(gruppen.size), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 4.dp))
        gruppen.take(branchen).forEach { gr ->
            WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.branche(gr.branche), Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable { gewaehlt = gr.branche }.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name(gr.branche), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(S.fbrWerte(gr.werte.size), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(8.dp))
                    ChevronRight()
                }
            }
            gr.werte.take(3).forEachIndexed { i, e -> if (i > 0) Trennlinie(); WertZeile(e, onOpen, listOfNotNull(FinderBranchen.grund(e)?.let { grundText(it) }), mitLand = true) }
        }
        if (gruppen.size > branchen) ActionLineWide(S.afMehrZeigen(minOf(8, gruppen.size - branchen))) { branchen += 8 }
    } else {
        ActionLineWide(S.fbrAlle) { gewaehlt = null }
        WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.branche(g.branche), Modifier.fillMaxWidth()) {
            Text(name(g.branche), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
        }
        PunktText(S.fbrWerte(g.werte.size), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp))
        g.werte.take(werte).forEachIndexed { i, e -> if (i > 0) Trennlinie(); WertZeile(e, onOpen, listOfNotNull(FinderBranchen.grund(e)?.let { grundText(it) })) }
        if (g.werte.size > werte) ActionLineWide(S.afMehrZeigen(minOf(20, g.werte.size - werte))) { werte += 20 }
    }
}

// Weg „Vorschlagsdepot“: Kernaussage, Aufteilung nach Sektor und Land, darunter die Werte mit Gewicht und Gruenden.
@Composable
internal fun FinderVorschlagAnsicht(state: AppState, ergebnisse: List<Aktienfinder.Ergebnis>, onOpen: (String) -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val depot = remember(ergebnisse) { Vorschlagsdepot.zusammenstellen(ergebnisse) }
    var nachbilden by remember { mutableStateOf(false) }
    if (depot.eintraege.isEmpty()) {
        EmptyHint(S.vsdLeer)
        return
    }
    WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.vorschlagsdepot(), Modifier.fillMaxWidth()) {
        PunktZeile(
            listOf(S.vsdWerte(depot.eintraege.size), S.vsdSektoren(depot.sektoren.size), S.vsdLaender(depot.laender.size)),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp),
        )
    }
    // Feste Farbe je Sektor, die Laender in einer ruhigen Einheitsfarbe
    val einheitsFarbe = AnteilsFarben.folge(0)
    Zwischenkopf(S.vsdSektorKopf, Modifier.padding(top = 8.dp))
    BalkenListe(depot.sektoren.map { (s, a) -> BalkenZeile(S.vsdSektor(s.name), a.toFloat(), Fmt.pct(a * 100.0, 0), AnteilsFarben.folge(s.ordinal)) })
    Zwischenkopf(S.vsdLandKopf, Modifier.padding(top = 4.dp))
    BalkenListe(depot.laender.map { (c, a) -> BalkenZeile(S.countryLabel(c), a.toFloat(), Fmt.pct(a * 100.0, 0), einheitsFarbe) })
    // Liste der Positionen, rechts die Beschriftung der Gewichtsspalte
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
        Zwischenkopf(S.vsdPositionen, Modifier.weight(1f))
        Text(
            S.vsdGewicht, style = MaterialTheme.typography.labelSmall, color = grau,
            maxLines = 1, softWrap = false, modifier = Modifier.padding(end = 20.dp, bottom = 5.dp),
        )
    }
    depot.eintraege.forEachIndexed { i, z -> if (i > 0) Trennlinie(); WertZeile(z.ergebnis, onOpen, emptyList(), rechts = Fmt.pct(z.gewicht * 100.0, 1), mitLand = depot.laender.size > 1, waage = z.waage, depot = true) }
    ActionLineWide(S.vsdNachbilden) { nachbilden = true }
    StuecklisteGewichte(state, depot.eintraege.map { Triple(it.ergebnis.symbol, it.ergebnis.daten.name, it.gewicht) }, onOpen)
    // Auswahlregeln hinter dem Info-Symbol
    InfoZeile(
        S.vsdAuswahlKopf, S.vsdAuswahlKopf,
        S.vsdAuswahl(
            Vorschlagsdepot.MIN_PUNKTE.toInt(), Vorschlagsdepot.MINDEST_BEREICH.toInt(), Vorschlagsdepot.JE_BRANCHE, Vorschlagsdepot.JE_SEKTOR,
            Vorschlagsdepot.JE_LAND, (Vorschlagsdepot.DECKEL * 100).toInt(),
        ).joinToString("\n") { STICHPUNKT + it },
        Modifier.padding(top = 4.dp),
    )
    if (nachbilden) NachbildenBlatt(
        state = state, titel = S.vsdTitel, mitAnzahl = false, notiz = S.vsdTitel,
        gewichte = { _ -> depot.eintraege.map { it.ergebnis.symbol to it.gewicht } }, onDismiss = { nachbilden = false },
    )
}
