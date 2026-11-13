package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.InsiderHistorie
import de.troetenrepublik.app.engine.InsiderSeltenheit
import de.troetenrepublik.app.engine.Uhr
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.SecInsider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Seltene Insiderkaeufe (0.77): Liste „Seltene Kaeufe“ im Reiter Insider (letzte 30 Tage, seltenste zuerst, je Wert eine
// Aussage) und die Kernaussage auf der Wertpapierseite. Die Rechnung steht in InsiderSeltenheit; die Daten reichen so weit
// wie der mitgelieferte Stand der Form-4-Meldungen (18 Monate), das steht in der Aussage.
internal class SeltenStand(val funde: List<InsiderSeltenheit.Fund>, val ende: String, val beginn: String, val mitHistorie: Boolean = false)

// Letzter Tag, den die Insider-Daten abdecken: ihr Stand, hoechstens heute.
internal fun standEnde(d: SecInsider.Datei): String {
    val heute = Uhr.heute().toString()
    val letzte = d.stand.ifBlank { d.geschaefte.maxOfOrNull { it.f.ifBlank { it.d } } ?: heute }
    return minOf(heute, letzte)
}

// Aussagen zu allen Firmen, im Hintergrund gerechnet; null ohne Daten oder ohne seltene Kaeufe.
@Composable
internal fun rememberSelten(daten: SecInsider.Datei?): SeltenStand? {
    val s by produceState<SeltenStand?>(null, daten) {
        value = if (daten == null) null else withContext(Dispatchers.Default) {
            runCatching {
                val beginn = InsiderSeltenheit.beginn(daten.geschaefte) ?: return@runCatching null
                val ende = standEnde(daten)
                val historie = InsiderHistorie.app()
                val funde = InsiderSeltenheit.besteJeFirma(InsiderSeltenheit.funde(daten.geschaefte, ende, beginn, historie = historie))
                SeltenStand(funde, ende, historie?.von ?: beginn, historie != null).takeIf { funde.isNotEmpty() }
            }.getOrNull()
        }
    }
    return s
}

// Aussagen zu einer Firma (Wertpapierseite), seltenste zuerst; leer, wenn nichts selten ist.
internal fun seltenFuerWert(d: SecInsider.Datei, eigen: List<SecInsider.Geschaeft>): List<InsiderSeltenheit.Fund> {
    val beginn = InsiderSeltenheit.beginn(d.geschaefte) ?: return emptyList()
    return InsiderSeltenheit.fuerFirma(eigen, standEnde(d), beginn, historie = InsiderHistorie.app())
}

private fun monat(iso: String): String = runCatching { Fmt.monthYear(java.time.YearMonth.parse(iso.take(7))) }.getOrDefault(iso)

private fun tag(iso: String): String = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso

internal fun seltenText(f: InsiderSeltenheit.Fund): String = S.skAussage(f.art.ordinal, f.monate, f.seitBeginn, f.zahl, Fmt.num(f.faktor, 1), f.seitJahr)

// Zugeklappt eine Zeile mit der Zahl der Werte und dem seltensten, aufgeklappt je Wert eine Zeile. Jede Zeile ist ein
// Eintrag der LazyColumn.
internal fun LazyListScope.seltenAbschnitt(state: AppState, stand: SeltenStand?, offen: Boolean, umschalten: () -> Unit, oeffnen: (String) -> Unit) {
    if (stand == null) return
    val erster = stand.funde.first()
    item(key = "sk-kopf", contentType = "kern-kopf") {
        WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.insiderSelten(), Modifier.fillMaxWidth()) {
            KernKopf(
                S.skTitel, KernZeile(listOf(KernTeil(S.skKern(stand.funde.size)), KernTeil(CopyRouten.name(erster.symbol, erster.name)))),
                offen, gross = false, onClick = umschalten,
            )
        }
    }
    if (!offen) return
    item(key = "sk-stand", contentType = "kern-text") {
        Text(
            S.skKopf(tag(stand.ende), if (stand.mitHistorie) stand.beginn.take(4) else monat(stand.beginn)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
    // Alle Werte, die der Kopf nennt; die Zeilen sind einzelne Eintraege der LazyColumn
    val sichtbar = stand.funde
    items(sichtbar.size, key = { "sk-" + sichtbar[it].symbol }, contentType = { "selten-zeile" }) { i -> SeltenZeile(state, sichtbar[i], oeffnen) }
}

// Name mit Kuerzel, darunter die Aussage; rechts Betrag und Tag des Kaufs.
@Composable
private fun SeltenZeile(state: AppState, f: InsiderSeltenheit.Fund, oeffnen: (String) -> Unit) {
    val sym = CopyRouten.symbol(f.symbol)
    val name = CopyRouten.name(f.symbol, f.name)
    Row(
        Modifier.fillMaxWidth().clickable { oeffnen("instrument/$sym") }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentLogo(state, sym, name, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NamenText(name, zusatz = " (${f.symbol})", style = MaterialTheme.typography.titleSmall)
            // Die Aussage bricht um, Betrag und Tag rechts bleiben einzeilig
            Text(seltenText(f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.dollarKompakt(f.betrag), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            Text(tag(f.tag), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
    }
}

// Kernaussage auf der Wertpapierseite im Insider-Abschnitt: je seltene Aussage eine Zeile mit Tag und Betrag darunter.
@Composable
internal fun SeltenKern(funde: List<InsiderSeltenheit.Fund>) {
    if (funde.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        funde.forEach { f ->
            Text(seltenText(f), style = MaterialTheme.typography.bodyMedium)
            Text(
                tag(f.tag) + " · " + Fmt.dollarKompakt(f.betrag), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}
