package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.engine.Testdepot
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

private const val START_BETRAG = 1000.0

// Betrag fuer das Eingabefeld: ganze Euro ohne Nachkommastellen, sonst mit zwei, ohne Tausendertrennung.
internal fun testBetragText(b: Double): String {
    val c = (b * 100).roundToLong()
    return if (c % 100 == 0L) (c / 100).toString() else "${c / 100}.${(c % 100).toString().padStart(2, '0')}"
}

internal fun testBetragLesen(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

// Bearbeiten des Testdepots im Crash-Test: Suche, Betrag je Wert, Entfernen per Antippen, Start aus dem echten Depot.
@Composable
internal fun TestdepotEditor(state: AppState, eintraege: List<Testdepot.Eintrag>, eigenes: List<ValuedPosition>, onChange: (List<Testdepot.Eintrag>) -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var suche by rememberSaveable { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(suche) {
        val q = suche.trim()
        val lokal = if (q.length >= 2) Symbols.search(q).map { it.symbol to it.name.ifBlank { it.symbol } } else emptyList()
        treffer = lokal.take(8)
        if (q.length < 2) return@LaunchedEffect
        try {
            delay(350)
            val bekannt = lokal.map { it.first }.toSet()
            val fern = state.market.search(q).filter { (it.type == "EQUITY" || it.type == "ETF" || it.type == "CRYPTOCURRENCY") && it.symbol !in bekannt }
                .map { it.symbol to it.name.ifBlank { it.symbol } }
            treffer = (lokal + fern).distinctBy { it.first }.take(10)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }
    OutlinedTextField(
        value = suche, onValueChange = { suche = it.take(40) }, singleLine = true, placeholder = { Text(S.ptTestSuche) },
        shape = RoundedCornerShape(Radius.field), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
    if (suche.trim().length >= 2) {
        treffer.filter { (s, _) -> eintraege.none { it.symbol == s } }.forEach { (symbol, name) ->
            TestdepotTreffer(name, symbol) {
                onChange(eintraege + Testdepot.Eintrag(symbol, name, START_BETRAG))
                suche = ""
            }
        }
    }
    val uebernehmbar = Testdepot.ausDepot(eigenes)
    if (uebernehmbar.isNotEmpty()) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp)) {
            Chip(S.ptTestUebernehmen, active = false) { onChange(uebernehmbar) }
        }
    }
    eintraege.forEach { e ->
        var text by remember(e.symbol) { mutableStateOf(testBetragText(e.betrag)) }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(e.name, style = MaterialTheme.typography.bodyMedium)
                Text(e.symbol, style = MaterialTheme.typography.bodySmall, color = grau)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { neu ->
                    text = neu.take(12)
                    testBetragLesen(text)?.let { b -> onChange(eintraege.map { if (it.symbol == e.symbol) it.copy(betrag = b) else it }) }
                },
                singleLine = true, suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(Radius.field), modifier = Modifier.width(140.dp).semantics { contentDescription = S.ptTestBetrag },
            )
            // Gezeichnetes Kreuz statt Textzeichen
            androidx.compose.foundation.Canvas(
                Modifier.textKlick(14.dp) { onChange(eintraege.filter { it.symbol != e.symbol }) }.padding(horizontal = 14.dp, vertical = 12.dp)
                    .size(14.dp).semantics { contentDescription = S.ptTestEntfernen },
            ) {
                val d = 1.8.dp.toPx()
                drawLine(grau, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = d, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(grau, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = d, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
    if (eintraege.isNotEmpty()) KeyValueRow(S.ptTestSumme, Fmt.euro(eintraege.sumOf { it.betrag }, 0))
}

@Composable
private fun TestdepotTreffer(name: String, symbol: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
