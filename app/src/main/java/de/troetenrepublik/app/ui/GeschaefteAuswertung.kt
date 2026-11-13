package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.engine.Geschaeftsauswertung
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs
import kotlin.math.roundToLong

// Wie viele der letzten Verkaeufe einzeln stehen, bevor „Alle n“ kommt
private const val LETZTE = 8

// Eigene abgeschlossene Geschaefte nach Haltedauer und Ausloeser, zugeklappt unter der Verkaufsbilanz des Depotverlaufs:
// die Zeile nennt mittlere Haltedauer und laengste Verlustserie, aufgeklappt stehen die Klassen als Balken (Summe in Euro,
// Farbe nach Gewinn oder Verlust) und die letzten Verkaeufe.
@Composable
internal fun GeschaefteAuswertung(konto: Account) {
    val a = remember(konto.transactions.size, konto.orders.size) { Geschaeftsauswertung.auswerten(konto.transactions, konto.orders) } ?: return
    var offen by rememberSaveable { mutableStateOf(false) }
    var alle by rememberSaveable { mutableStateOf(false) }
    val signal = LocalSignal.current
    fun tage(t: Double) = S.days(t.roundToLong().coerceAtLeast(0))
    fun anzahl(n: Int) = if (n == 1) S.st("ga.verkauf1") else S.st("ga.verkaeufe", listOf(Fmt.num(n.toDouble(), 0)))
    val serie = anzahl(a.verlustserie)
    AufklappZeile(
        S.st("ga.titel"),
        a.mittelTage?.let { S.st("ga.unter", listOf(tage(it), serie)) } ?: S.st("ga.unterOhne", listOf(serie)),
        offen,
    ) { offen = !offen }
    if (!offen) return

    // Der laengste Balken ist die groesste Summe aller Klassen
    val groesste = (a.dauer.values + a.ausloeser.values).maxOfOrNull { abs(it.summe) } ?: 0.0

    @Composable
    fun zeile(name: String, g: Geschaeftsauswertung.Gruppe) {
        Column {
            ShareBar(name, Fmt.signedEuro(g.summe), if (groesste > 0.0) abs(g.summe) / groesste else 0.0, signal.forChange(g.summe))
            Text(
                S.st(if (g.anzahl == 1) "ga.zeile1" else "ga.zeile", listOf(Fmt.num(g.anzahl.toDouble(), 0), Fmt.pct(g.trefferquote * 100, 0))),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 4.dp),
            )
        }
    }
    if (a.dauer.isNotEmpty()) {
        Zwischenkopf(S.st("ga.dauer"))
        a.dauer.forEach { (d, g) -> zeile(S.st("ga." + d.name), g) }
    }
    Zwischenkopf(S.st("ga.ausloeser"))
    a.ausloeser.forEach { (k, g) -> zeile(S.st("ga." + k.name), g) }
    Zwischenkopf(S.st("ga.letzte"))
    val neueste = a.geschaefte.asReversed()
    (if (alle) neueste else neueste.take(LETZTE)).forEach { g ->
        val wie = S.st("ga." + g.ausloeser.name)
        KeyValueRow(
            g.name, Fmt.signedEuro(g.euro), valueColor = signal.forChange(g.euro),
            hint = g.tage?.let { S.st("ga.gehalten", listOf(tage(it), wie)) } ?: wie,
        )
    }
    if (neueste.size > LETZTE) ListenPfeil(alle, label = if (alle) S.less else S.bbAlle(neueste.size)) { alle = !alle }
}
