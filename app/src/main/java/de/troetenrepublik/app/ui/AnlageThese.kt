package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.These
import de.troetenrepublik.app.engine.Anlagethese
import de.troetenrepublik.app.i18n.S

// Anlage-These und eigene Depotregeln (seit 5.91): Karte auf der Wertpapierseite, Regeln und Auswertung im Depot-Check.

// Text zu einem Regelverstoss, fuer die Ordermaske und den Depot-Check.
fun theseVerstossText(b: Anlagethese.Befund): String =
    S.thVerstoss(b.verstoss.name, Fmt.pct(b.ist, 1), Fmt.pct(b.grenze, 0), b.name)

// These eines Werts mit Ausstieg, Obergrenze und Prueftermin. Ist der Termin erreicht und der Wert noch im Depot,
// steht dort die Frage „Grund noch gueltig?“ mit Ja (neuer Termin in drei Monaten) und „Verkaufen erwaegen“.
@Composable
fun TheseKarte(state: AppState, symbol: String, t: These, gehalten: Boolean, onVerkaufen: () -> Unit) {
    val valuation by state.valuation.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val jetzt = System.currentTimeMillis()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        Text(S.thTitel, style = MaterialTheme.typography.titleSmall)
        Text(S.thSeit(Fmt.date(t.seit)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (t.ausstieg.isNotBlank()) {
            Text(S.thAusstiegLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            Text(t.ausstieg, style = MaterialTheme.typography.bodyMedium)
        }
        if (t.obergrenze > 0) {
            val depot = valuation.securities + valuation.cash
            val anteil = if (depot > 0) valuation.positions.filter { it.key == symbol }.sumOf { it.value } / depot * 100 else 0.0
            Text(
                S.thObergrenzeWert(Fmt.pct(t.obergrenze, 0), Fmt.pct(anteil, 1)), style = MaterialTheme.typography.bodyMedium,
                color = if (anteil > t.obergrenze) signal.warn else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (t.pruefAm > 0) {
            Text(S.thPruefAm(Fmt.date(t.pruefAm)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
        if (gehalten && Anlagethese.offen(t, jetzt)) {
            Text(S.thFrage, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FloatingPill(label = S.pmYes, modifier = Modifier.weight(1f)) { state.theseBestaetigen(symbol) }
                FloatingPill(label = S.thVerkaufenErwaegen, filled = false, modifier = Modifier.weight(1f)) {
                    state.theseErwogen(symbol)
                    onVerkaufen()
                }
            }
        }
        Text(
            S.thEntfernen, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(Radius.chip)).clickable { state.theseEntfernen(symbol) }.padding(vertical = 4.dp),
        )
    }
}

// Eigene Regeln einstellen, Stand des Depots dazu und die Auswertung der Thesen. Nur fuer das eigene Depot.
@Composable
internal fun EigeneRegeln(state: AppState) {
    val account by state.account.collectAsStateWithLifecycle()
    val valuation by state.valuation.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val r = account.depotRegeln
    SectionHeader(S.thRegelnTitel)
    ToolNote(S.thRegelnText)
    ToolChoice(S.thRegelPosition, listOf(0.0, 10.0, 20.0, 30.0, 50.0).map { (if (it == 0.0) S.thAus else Fmt.pct(it, 0)) to it }, r.maxPosition) {
        state.depotRegelnSetzen(r.copy(maxPosition = it))
    }
    ToolChoice(S.thRegelHebel, listOf(S.thAus to false, S.thAn to true), r.keineHebel) { state.depotRegelnSetzen(r.copy(keineHebel = it)) }
    ToolChoice(S.thRegelBargeld, listOf(0.0, 5.0, 10.0, 20.0).map { (if (it == 0.0) S.thAus else Fmt.pct(it, 0)) to it }, r.minBargeld) {
        state.depotRegelnSetzen(r.copy(minBargeld = it))
    }
    if (r.aktiv) {
        val posten = valuation.positions.filter { it.value > 0 }.map { Anlagethese.Posten(it.position.name, it.value, it.position.isDerivative) }
        val befunde = Anlagethese.depotPruefen(r, posten, valuation.cash)
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            if (befunde.isEmpty()) Text(S.thRegelnOk, style = MaterialTheme.typography.bodyMedium, color = signal.up)
            else befunde.forEach { Text(theseVerstossText(it), style = MaterialTheme.typography.bodyMedium, color = signal.warn, modifier = Modifier.padding(bottom = 4.dp)) }
        }
    }
    val z = account.theseZaehler
    val jetzt = System.currentTimeMillis()
    SectionHeader(S.thAuswertung)
    KeyValueRow(S.ptBuys, z.kaeufe.toString())
    KeyValueRow(S.thMitThese, if (z.kaeufe > 0) "${z.mitThese} (${Fmt.pct(z.mitThese * 100.0 / z.kaeufe, 0)})" else z.mitThese.toString())
    KeyValueRow(S.thBestaetigt, z.bestaetigt.toString())
    KeyValueRow(S.thErwogen, z.erwogen.toString())
    KeyValueRow(S.thRegelbrueche, z.regelbrueche.toString())
    KeyValueRow(S.thOffeneTermine, account.thesen.values.count { Anlagethese.offen(it, jetzt) }.toString())
    ToolNote(S.thAuswertungText)
}
