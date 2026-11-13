package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.HistorieJst
import de.troetenrepublik.app.engine.HistorieRechner
import de.troetenrepublik.app.engine.Regionen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S

// [0.78] Historische Annahmen in den Rechnern (Vermoegensaufbau, Entnahmeplan): Auswahl „Eigene“, „Historisch Welt“ und
// „Historisch <Land der Einstellungen>“. Mit einer historischen Wahl stehen statt der Regler fuer Rendite und Schwankung
// die gerechneten Werte mit Zeitraum und der Bandbreite ueber alle Zeitraeume der gewaehlten Laenge; dazu die Wahl zwischen
// Zufall und historischen Folgen. Die Rechnung steckt in engine/HistorieRechner.kt.

// Welt und das Land der Einstellungen (null, wenn die Quelle fuer das Land keine Aktienrenditen hat); zitat: Zitat und Lizenz
// der Quelle, wie sie es verlangt
internal class HistBasen(val welt: HistorieRechner.Basis?, val land: HistorieRechner.Basis?, val landName: String, val zitat: String) {
    fun basis(wahl: Int): HistorieRechner.Basis? = when (wahl) { 1 -> welt; 2 -> land; else -> null }
    fun name(b: HistorieRechner.Basis): String = if (b.kennung == HistorieRechner.WELT) S.hrWeltName else landName
}

@Composable
internal fun rememberHistBasen(state: AppState): HistBasen? {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var st by remember { mutableStateOf(HistorieLader.gespeichert()) }
    LaunchedEffect(Unit) { if (st == null) st = HistorieLader.laden() }
    val d: HistorieJst.Datei = st?.jst ?: return null
    return remember(d, settings.land, I18n.locale) {
        val code = settings.land.ifBlank { "DE" }
        HistBasen(
            HistorieRechner.welt(d), HistorieRechner.A3[code]?.let { HistorieRechner.land(d, it) },
            Regionen.land(code).name(I18n.locale.language == "de"), quellenText(d),
        ).takeIf { it.welt != null || it.land != null }
    }
}

// Auswahl der Annahmen: 0 eigene Regler, 1 Welt, 2 Land. Ohne Daten keine Zeile.
@Composable
internal fun HistAnnahmenWahl(b: HistBasen?, wahl: Int, onWahl: (Int) -> Unit) {
    if (b == null) return
    val optionen = listOfNotNull(S.hrEigene to 0, b.welt?.let { S.hrWelt to 1 }, b.land?.let { S.hrLand(b.landName) to 2 })
    // Gleich breite Felder in einer Zeile, Namen brechen an Wortgrenzen um
    ToolFelder(S.hrAnnahmen, optionen, wahl, spalten = optionen.size, innen = 4.dp, onSelect = onWahl)
}

// Gerechnete Werte statt der Regler: Rendite und Schwankung mit Zeitraum, schlechtester und bester Zeitraum von `jahre`
// Jahren, und die Wahl der Verlaeufe, wenn es genug Startjahre gibt.
@Composable
internal fun HistAnnahmenZeilen(b: HistBasen, basis: HistorieRechner.Basis, jahre: Int, folgen: Boolean, onFolgen: (Boolean) -> Unit) {
    val signal = LocalSignal.current
    val k = remember(basis) { HistorieRechner.kennzahlen(basis) }
    val band = remember(basis, jahre) { HistorieRechner.bandbreite(basis, jahre) }
    KeyValueRow(S.hrRendite, Fmt.signedPct(k.rendite, 1), hint = S.hrSpanne(b.name(basis), k.erstes, k.letztes))
    KeyValueRow(S.toolVolatility, Fmt.pct(k.schwankung, 0))
    if (band != null && band.faelle >= HistorieRechner.MIN_FAELLE) {
        KeyValueRow(
            S.hrSchlecht(jahre), Fmt.signedPct(band.schlecht.rendite, 1), if (band.schlecht.rendite >= 0) signal.up else signal.down,
            hint = S.hrZeitraumReal(band.schlecht.von, band.schlecht.bis),
        )
        KeyValueRow(
            S.hrBest(jahre), Fmt.signedPct(band.best.rendite, 1), if (band.best.rendite >= 0) signal.up else signal.down,
            hint = S.hrZeitraumReal(band.best.von, band.best.bis),
        )
        ToolChoice(S.hrVerlaeufe, listOf(S.hrZufall to false, S.hrFolgen to true), folgen, onFolgen)
    }
}

// Hinweis unter dem Ergebnis: woher die Zahlen stammen und was sie nicht leisten
internal fun histNote(b: HistBasen, basis: HistorieRechner.Basis, folgen: HistFolgen?, zusatz: String = ""): String {
    val welt = if (basis.kennung == HistorieRechner.WELT) " " + S.hrWeltInfo(basis.laender) else ""
    val kopf = if (folgen != null) S.hrNoteFolgen(b.name(basis), folgen.ersterStart, folgen.letzterStart, folgen.jahre)
    else S.hrNoteZufall(b.name(basis), basis.erstes, basis.letztes)
    return kopf + welt + (if (zusatz.isEmpty()) "" else " $zusatz") + " " + S.hrNoteEnde + "\n\n" + b.zitat
}

// Erstes und letztes Startjahr und die Laenge der Folgen (Jahre)
internal class HistFolgen(val ersterStart: Int, val letzterStart: Int, val jahre: Int)

// Quelle der historischen Renditen mit Lizenz, fuer den Anhang am Ende des Rechners
internal fun histQuelle(): QuelleLink = LaenderQuellen.jst().let { (n, u) -> pflichtQuelle(n, u) ?: QuelleLink(n, u) }
