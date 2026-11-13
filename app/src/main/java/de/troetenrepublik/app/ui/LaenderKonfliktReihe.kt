package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Gpr
import de.troetenrepublik.app.engine.KonfliktReihe
import de.troetenrepublik.app.i18n.S
import kotlin.math.max

// [Lange Lage] Laenderblatt: Konflikte seit 1946 (UCDP/PRIO) als Saeulen je Jahr und das Geopolitische Risiko je Land seit 1900.
// Beide erscheinen nur fuer Laender, die die Dateien kennen.

// Konflikte seit 1946: Kacheln mit Jahren, Kriegsjahren und der aktuellen Lage, darunter ein Saeulenband je Jahr
// (Hoehe = Zahl der Konflikte, Farbe = schwerste Stufe).
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LaenderKonfliktReihe(land: GlobusLand) {
    val d = LangeLageDaten.konflikte.rememberDatei() ?: return
    val l = d.laender[land.a3] ?: return
    val k = remember(d, l) { KonfliktReihe.kennzahlen(d, l) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val signal = LocalSignal.current
    val titel = langeText("k.titel", d.von.toString())
    val infoText = remember(d, de.troetenrepublik.app.i18n.I18n.locale) {
        langeText("k.info") + "\n\n" + KONFLIKT_ZITAT + "\n" + "UCDP/PRIO Armed Conflict Dataset " + d.version + ". " + S.histAbruf(tagDatum(d.stand))
    }
    val quelle = QuelleLink("UCDP/PRIO Armed Conflict Dataset (CC BY 4.0)", KonfliktReihe.SEITE)
    val liste = LocalQuellenSammler.current
    SideEffect { if (liste != null && liste.none { it.name == quelle.name }) liste.add(quelle) }

    ReihenKopf(titel) { info = titel to infoText }
    val kacheln = listOf(
        KachelDaten(langeText("k.jahre"), langeText("k.jahreWert", k.jahreMitKonflikt.toString(), k.jahre.toString())) { info = titel to infoText },
        KachelDaten(
            langeText("k.krieg"), k.kriegsjahre.toString(), langeText("k.kriegUnter"), if (k.kriegsjahre > 0) signal.down else null,
        ) { info = titel to infoText },
        if (k.aktivSeit != null) KachelDaten(
            langeText("k.aktiv"), k.aktivSeit.toString(), langeText("k.aktivUnter", (d.bis - k.aktivSeit + 1).toString()), signal.down,
        ) { info = titel to infoText }
        else KachelDaten(
            langeText("k.zuletzt"), (k.letztesJahr ?: d.von).toString(), langeText("k.zuletztUnter", (d.bis - (k.letztesJahr ?: d.von)).toString()),
        ) { info = titel to infoText },
    )
    KachelRaster(kacheln, 3)
    KonfliktBand(d, l, langeText("k.band"))
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        KonfliktLegende(Kategorie.ocker, langeText("k.leg1"))
        KonfliktLegende(signal.down, langeText("k.leg2"))
    }
    info?.let { (t, text) -> MarktInfoBlatt(t, text) { info = null } }
}

// Titel eines Abschnitts ohne Aufklappen: Tipp oder Info-Symbol oeffnet die Erklaerung
@Composable
private fun ReihenKopf(titel: String, onInfo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onInfo) {
            androidx.compose.material3.Icon(
                Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
            )
        }
    }
}

private const val KONFLIKT_ZITAT =
    "Davies, Shawn, Therese Pettersson, and Magnus Oberg, Organized violence 1989-2025, and violent political protests, Journal of Peace Research, 2026. " +
        "Gleditsch, Nils Petter, Peter Wallensteen, Mikael Eriksson, Margareta Sollenberg, and Havard Strand (2002), Armed Conflict 1946-2001: A New Dataset, Journal of Peace Research 39(5)."

@Composable
private fun KonfliktLegende(farbe: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(8.dp).background(farbe, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun KonfliktBand(d: KonfliktReihe.Datei, l: KonfliktReihe.Land, beschreibung: String) {
    val messer = rememberTextMeasurer()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val raster = MaterialTheme.colorScheme.outlineVariant
    val signal = LocalSignal.current
    val leicht = Kategorie.ocker
    val klein = TextStyle(fontSize = 10.sp, color = grau)
    val n = d.bis - d.von + 1
    val hoechste = max(l.anzahl.maxOrNull() ?: 1, 1)
    // Ueberschrift wie bei den anderen Jahresbalken: die Hoehe ist die Zahl der Konflikte
    Text(
        if (hoechste > 1) "$beschreibung (${langeText("k.bandMax", hoechste.toString())})" else beschreibung,
        style = MaterialTheme.typography.labelMedium, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp),
    )
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 6.dp).height(96.dp).semantics { contentDescription = beschreibung }) {
        val dichte = density
        val oben = 4f * dichte
        val unten = 16f * dichte
        val h = size.height - oben - unten
        val spalte = size.width / n
        for (i in 0 until n) {
            val stufe = l.stufen[i]
            if (stufe == 0) continue
            val hoehe = max(h * l.anzahl[i].coerceAtLeast(1) / hoechste, 3f * dichte)
            drawRect(if (stufe == 2) signal.down else leicht, Offset(spalte * i + spalte * 0.1f, oben + h - hoehe), Size(spalte * 0.8f, hoehe))
        }
        drawLine(raster, Offset(0f, oben + h), Offset(size.width, oben + h), strokeWidth = 1f * dichte)
        // Jahreszahlen: erstes Jahr links, danach alle 20 Jahre
        val erste = messer.measure(d.von.toString(), klein, maxLines = 1, softWrap = false)
        drawText(erste, topLeft = Offset(0f, oben + h + 3f * dichte))
        var j = (d.von / 20 + 1) * 20
        while (j <= d.bis) {
            val z = messer.measure(j.toString(), klein, maxLines = 1, softWrap = false)
            val mitte = spalte * (j - d.von) + spalte / 2f
            if (mitte - z.size.width / 2f > erste.size.width + 6f * dichte && mitte + z.size.width / 2f <= size.width) {
                drawText(z, topLeft = Offset(mitte - z.size.width / 2f, oben + h + 3f * dichte))
            }
            j += 20
        }
    }
}

// Geopolitisches Risiko seit 1900 je Land: Kacheln mit letztem Jahr, hoechstem Jahr und Mittel, aufgeklappt der Jahresverlauf.
@Composable
internal fun LaenderGprLand(land: GlobusLand) {
    val d = LangeLageDaten.gpr.rememberDatei() ?: return
    val j = d.lj[land.a3] ?: return
    val punkte = remember(j) { j.werte.mapIndexedNotNull { i, v -> v?.let { (j.von + i).toDouble() to it } } }
    if (punkte.size < 10) return
    var offen by rememberSaveable("gpr-land") { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val titel = langeText("g.titel")
    val infoText = remember(d, de.troetenrepublik.app.i18n.I18n.locale) { langeText("g.info") + "\n\n" + d.zitat + "\n" + S.histAbruf(tagDatum(d.stand)) }
    val liste = LocalQuellenSammler.current
    val quelle = QuelleLink("Geopolitical Risk Index (CC BY), Caldara und Iacoviello", Gpr.SEITE)
    SideEffect { if (liste != null && liste.none { it.name == quelle.name }) liste.add(quelle) }

    val letzte = punkte.last()
    val hoch = punkte.maxByOrNull { it.second } ?: return
    val mittel = punkte.map { it.second }.average()
    HistorieKopf(titel, offen) { offen = !offen }
    KachelRaster(
        listOf(
            KachelDaten(langeText("g.letztes"), Fmt.num(letzte.second, 2), letzte.first.toInt().toString()) { info = titel to infoText },
            KachelDaten(langeText("g.hoechst"), Fmt.num(hoch.second, 2), hoch.first.toInt().toString()) { info = titel to infoText },
            KachelDaten(langeText("g.mittel", punkte.first().first.toInt().toString()), Fmt.num(mittel, 2)) { info = titel to infoText },
        ),
        3,
    )
    if (offen) {
        HistDiagramm(
            listOf(HistLinie(land.name(de.troetenrepublik.app.i18n.I18n.locale.language == "de"), punkte, MaterialTheme.colorScheme.primary)),
            punkte.first().first, punkte.last().first, log = false, marken = emptyList(),
            achsenText = { Fmt.num(it, 1) }, endeText = { Fmt.num(it, 2) }, beschreibung = titel, hoehe = 150.dp,
        )
    }
    info?.let { (t, text) -> MarktInfoBlatt(t, text) { info = null } }
}
