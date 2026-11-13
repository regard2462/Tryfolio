package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.gruppeSchliessen
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Valuation
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.engine.StillhalterDepot
import de.troetenrepublik.app.i18n.S

// Verkaufte Optionen auf der Derivateseite (0.77): Position mit Praemie, Rueckkaufpreis und Bindung, darunter die Beine
// der zusammen angelegten Strategie mit dem Knopf, der sie zusammen schliesst.

// Art eines Handels: bei verkauften Optionen Verkauf zur Eroeffnung bzw. Rueckkauf.
fun handelsArt(t: Transaction): String =
    if (t.verkaufteOption && (t.type == TxType.BUY || t.type == TxType.SELL)) S.shGeschaeft(t.type == TxType.SELL) else S.txLabel(t.type)

// Name einer Position in Listen: verkaufte Optionen mit der Richtung davor.
fun positionsName(p: Position): String = if (p.verkauft) S.shVerkauft + " · " + p.name else p.name

// Position einer verkauften Option. brief: aktueller Briefkurs je Stueck (Rueckkauf).
@Composable
internal fun StillhalterAbschnitt(account: Account, vp: ValuedPosition, brief: Double) {
    val signal = LocalSignal.current
    val p = vp.position
    val stueck = -p.qty
    SectionHeader(S.yourPosition)
    KeyValueRow(S.shVerkauft, "${Fmt.qty(stueck)} ${S.pieces}")
    KeyValueRow(S.shPraemie, Fmt.money(-vp.invested), hint = S.shJeStueck(Fmt.priceEuro(p.avgPrice)))
    if (brief > 0) KeyValueRow(S.shRueckkaufpreis, Fmt.money(brief * stueck), hint = S.shJeStueck(Fmt.priceEuro(brief)))
    KeyValueRow(S.value, Fmt.money(vp.value))
    KeyValueRow(S.result, "${Fmt.signedMoney(vp.profit)}  ${Fmt.signedPct(vp.profitPct)}", valueColor = signal.forChange(vp.profit))
    StillhalterDepot.bindung(account, p.derivative?.gruppe.orEmpty())?.let { b ->
        if (b.cash > 0.005) KeyValueRow(S.shBindung, Fmt.money(b.cash))
        if (b.stueck > 1e-9) KeyValueRow(S.shGebundeneAktien, "${Fmt.qty(b.stueck)} ${S.pieces}")
    }
}

// Die Beine der Strategie, zu der key gehoert; nichts, wenn die Position allein steht. Ein Tipp oeffnet ein Bein.
@Composable
internal fun StillhalterGruppe(state: AppState, account: Account, valuation: Valuation, key: String, onOpen: (String) -> Unit, onGeschlossen: () -> Unit) {
    val gruppe = account.positions.firstOrNull { it.key == key }?.derivative?.gruppe.orEmpty()
    val beine = valuation.positions.filter { gruppe.isNotBlank() && it.position.derivative?.gruppe == gruppe }
    if (beine.size < 2) return
    SectionHeader(S.shStrategie)
    beine.forEach { vp ->
        KeyValueRow(positionsName(vp.position), Fmt.money(vp.value), onClick = if (vp.key == key) null else { { onOpen(vp.key) } })
    }
    KeyValueRow(S.result, Fmt.signedMoney(beine.sumOf { it.profit }), valueColor = LocalSignal.current.forChange(beine.sumOf { it.profit }))
    var fragen by remember { mutableStateOf(false) }
    FloatingPill(S.shGruppeSchliessen, filled = false, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { fragen = true }
    if (fragen) StillhalterBestaetigen(S.shGruppeSchliessen, { fragen = false }) { fragen = false; if (state.gruppeSchliessen(gruppe)) onGeschlossen() }
}

// Rueckfrage vor dem Schliessen: ein Tipp allein fuehrt kein Geschaeft aus.
@Composable
internal fun StillhalterBestaetigen(titel: String, onNein: () -> Unit, onJa: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onNein,
        title = { androidx.compose.material3.Text(titel) },
        confirmButton = { TextButton(onClick = onJa) { androidx.compose.material3.Text(titel) } },
        dismissButton = { TextButton(onClick = onNein) { androidx.compose.material3.Text(S.cancel) } },
    )
}
