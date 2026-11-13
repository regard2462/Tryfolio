package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Epu
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.Gpr
import de.troetenrepublik.app.engine.Monatsreihe
import de.troetenrepublik.app.engine.MonatsreiheRechnung
import de.troetenrepublik.app.i18n.S

// [Lange Lage] Politische Unsicherheit in der Weltlage: Geopolitisches Risiko (Caldara und Iacoviello), Unsicherheit der
// Wirtschaftspolitik und Aktienmarkt in den Nachrichten (Baker, Bloom und Davis) als Monatsreihen. Zugeklappt ein Balken je
// Index mit dem Rang des letzten Werts gegen alle Monate der Reihe, aufgeklappt die Verlaeufe seit 1900 bzw. 1985.

private class UnsicherheitZeile(val schluessel: String, val reihe: Monatsreihe, val ab: Int, val lage: Gpr.Lage)

private fun stufenFarbe(s: MonatsreiheRechnung.Stufe, warn: Color, ocker: Color, blau: Color): Color = when (s) {
    MonatsreiheRechnung.Stufe.HOCH -> warn
    MonatsreiheRechnung.Stufe.ERHOEHT -> ocker
    else -> blau
}

@Composable
internal fun UnsicherheitAbschnitt() {
    val gpr = LangeLageDaten.gpr.rememberDatei()
    val epu = LangeLageDaten.epu.rememberDatei()
    if (gpr == null && epu == null) return
    var offen by rememberSaveable("unsicherheit") { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val signal = LocalSignal.current
    val ocker = Kategorie.ocker
    val blau = Kategorie.blau
    val zeilen = remember(gpr, epu) {
        buildList<UnsicherheitZeile> {
            gpr?.let { d -> Gpr.lage(d.g)?.let { add(UnsicherheitZeile("gpr", d.g, d.g.von, it)) } }
            epu?.let { d ->
                Epu.lage(d.us, Epu.AB)?.let { add(UnsicherheitZeile("epu", d.us, Epu.AB, it)) }
                Epu.lage(d.gl)?.let { add(UnsicherheitZeile("gl", d.gl, d.gl.von, it)) }
                Epu.lage(d.emv, Epu.AB)?.let { add(UnsicherheitZeile("emv", d.emv, Epu.AB, it)) }
            }
        }
    }
    if (zeilen.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(langeText("u.titel"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { info = true }) {
            androidx.compose.material3.Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        AufklappPfeil(offen)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        for (z in zeilen) {
            LageBalkenZeile(
                langeText("u.zeile", langeText("u." + z.schluessel), langeText("u.stufe." + z.lage.stufe.name)),
                Fmt.num(z.lage.wert, 0), (z.lage.perzentil / 100.0).coerceIn(0.0, 1.0),
                stufenFarbe(z.lage.stufe, signal.warn, ocker, blau), { offen = !offen }, marke = 0.5,
            )
        }
    }
    if (offen) {
        val primaer = MaterialTheme.colorScheme.primary
        if (gpr != null) {
            // Seit 1900 (drei Zeitungen) als Gesamtbild, dann Drohungen und Handlungen getrennt seit 1985
            val lang = remember(gpr) { MonatsreiheRechnung.punkte(gpr.h) }
            UnsicherheitVerlauf(langeText("u.c.gpr"), listOf(HistLinie(langeText("u.n.gpr"), lang, primaer)))
            val droh = remember(gpr) { MonatsreiheRechnung.punkte(gpr.t) }
            val hand = remember(gpr) { MonatsreiheRechnung.punkte(gpr.d) }
            UnsicherheitVerlauf(langeText("u.c.td"), listOf(HistLinie(langeText("u.n.droh"), droh, primaer), HistLinie(langeText("u.n.hand"), hand, ocker)))
            zeilen.firstOrNull { it.schluessel == "gpr" }?.let { UnsicherheitHoechster(it) }
        }
        if (epu != null) {
            val us = remember(epu) { MonatsreiheRechnung.punkte(epu.us, Epu.AB) }
            UnsicherheitVerlauf(langeText("u.c.epu"), listOf(HistLinie(langeText("u.n.epu"), us, primaer)))
            zeilen.firstOrNull { it.schluessel == "epu" }?.let { UnsicherheitHoechster(it) }
            val gl = remember(epu) { MonatsreiheRechnung.punkte(epu.gl) }
            UnsicherheitVerlauf(langeText("u.c.gl"), listOf(HistLinie(langeText("u.n.gl"), gl, primaer)))
            zeilen.firstOrNull { it.schluessel == "gl" }?.let { UnsicherheitHoechster(it) }
            val emv = remember(epu) { MonatsreiheRechnung.punkte(epu.emv, Epu.AB) }
            UnsicherheitVerlauf(langeText("u.c.emv"), listOf(HistLinie(langeText("u.n.emv"), emv, primaer)))
            zeilen.firstOrNull { it.schluessel == "emv" }?.let { UnsicherheitHoechster(it) }
        }
    }
    if (info) {
        val quellen = listOfNotNull(gpr?.zitat) + epu?.zitat.orEmpty()
        val abruf = listOfNotNull(gpr?.stand, epu?.stand).maxOrNull()?.let { S.histAbruf(tagDatum(it)) }.orEmpty()
        MarktInfoBlatt(
            langeText("u.titel"),
            langeText("u.info") + "\n\n" + langeText("u.quellen", quellen.joinToString("\n")) + "\n\n" + abruf,
        ) { info = false }
    }
}

// Verlauf einer oder zweier Reihen ueber die ganze Breite, Namen und letzte Werte am Ende der Linien
@Composable
private fun UnsicherheitVerlauf(titel: String, linien: List<HistLinie>) {
    val von = linien.minOf { it.punkte.first().first }
    val bis = linien.maxOf { it.punkte.last().first }
    LbUnterkopf(titel)
    HistDiagramm(
        linien, von, bis, log = false, marken = emptyList(),
        achsenText = { Fmt.num(it, 0) }, endeText = { Fmt.num(it, 0) }, beschreibung = titel, hoehe = 150.dp,
    )
}

// Zeile unter dem Verlauf: wann der Index zuletzt so hoch stand, mit dem Monat des letzten Werts darunter
@Composable
private fun UnsicherheitHoechster(z: UnsicherheitZeile) {
    val seit = z.lage.hoechsterSeit
    KeyValueRow(
        langeText("u.seit"),
        if (seit != null) Monatsreihe.marke(seit) else langeText("u.rekord", Monatsreihe.jahr(maxOf(z.ab, z.reihe.von)).toString()),
        hint = langeText("u.stand", Monatsreihe.marke(z.lage.monat)),
    )
}

// [Lange Lage] Spannungsblatt: Geopolitisches Risiko der beteiligten Laender (Monatswerte seit 1985). Zeigt die beiden
// Laender der Region mit dem hoechsten Rang. Gibt zurueck, ob etwas gezeigt wurde (fuer die Quellenangabe).
@Composable
internal fun GprLaender(laender: List<String>, karte: GlobusKarte?, deutsch: Boolean): Boolean {
    val d = LangeLageDaten.gpr.rememberDatei() ?: return false
    val lagen = remember(d, laender) { Gpr.laenderLage(d, laender, 2) }
    if (lagen.isEmpty()) return false
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val farben = listOf(MaterialTheme.colorScheme.primary, Kategorie.ocker)
    val namen = lagen.map { (iso, _) -> karte?.nachA3(iso)?.name(deutsch) ?: iso }
    val linien = lagen.mapIndexed { i, (iso, _) -> HistLinie(namen[i], MonatsreiheRechnung.punkte(d.lm.getValue(iso)), farben[i % farben.size]) }
    AbschnittKopf(langeText("g.sp"))
    lagen.forEachIndexed { i, (_, lage) ->
        val stufe = langeText("u.stufe." + lage.stufe.name)
        KeyValueRow(
            namen[i], stufe,
            onClick = { info = namen[i] to (langeText("g.sp.zeile", namen[i], stufe, Fmt.num(lage.perzentil, 0)) + "\n\n" + langeText("g.sp.info") + "\n\n" + d.zitat + "\n" + S.histAbruf(tagDatum(d.stand))) },
        )
    }
    HistDiagramm(
        linien, linien.minOf { it.punkte.first().first }, linien.maxOf { it.punkte.last().first }, log = false, marken = emptyList(),
        achsenText = { Fmt.num(it, 1) }, endeText = { Fmt.num(it, 1) }, beschreibung = langeText("g.sp"), hoehe = 150.dp,
    )
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
    return true
}
