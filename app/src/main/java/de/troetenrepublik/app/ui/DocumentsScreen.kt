package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.docs.Documents
import de.troetenrepublik.app.engine.Tax
import de.troetenrepublik.app.i18n.S
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Kontoauszuege je Monat, Jahressteuerbescheinigung und die Abrechnungen aller Buchungen, in der App lesbar.
@Composable
fun DocumentsScreen(state: AppState, onBack: () -> Unit) {
    val account by state.account.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    var open by remember { mutableStateOf<Documents.Document?>(null) }

    val months = account.transactions.map { YearMonth.from(Instant.ofEpochMilli(it.ts).atZone(zone)) }.distinct().sortedDescending()
    val years = account.transactions.map { Tax.yearOf(it.ts) }.distinct().sortedDescending().ifEmpty { listOf(Tax.yearOf(System.currentTimeMillis())) }
    val settlements = account.transactions.filter { Documents.hasDocument(it) }.sortedByDescending { it.ts }

    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.documents, null, onBack) }
        item { SectionHeader(S.taxSection) }
        itemsIndexed(years, key = { _, y -> "j$y" }, contentType = { _, _ -> "dok" }) { i, y ->
            if (i > 0) Trennlinie()
            DocRow(S.taxCertificateTitle(y)) {
                open = Documents.taxCertificate(account, y, settings.allowance, settings.ownerName)
            }
        }
        item { SectionHeader(S.statements) }
        if (months.isEmpty()) item { EmptyHint(S.noBookings) }
        itemsIndexed(months, key = { _, m -> "m$m" }, contentType = { _, _ -> "dok" }) { i, m ->
            if (i > 0) Trennlinie()
            DocRow(S.statementTitle(Fmt.monthYear(m))) {
                open = Documents.statement(account, m.year, m.monthValue, settings.ownerName)
            }
        }
        item { SectionHeader(S.settlements) }
        itemsIndexed(settlements, key = { _, t -> t.id }, contentType = { _, _ -> "dok" }) { i, tx ->
            if (i > 0) Trennlinie()
            DocRow("${Documents.title(tx)} · ${S.txTitle(tx.tag, tx.title).ifBlank { S.txLabel(tx.type) }}", Fmt.dateTime(tx.ts)) {
                open = Documents.settlement(tx, settings.ownerName)
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    open?.let { DocumentSheet(it, onDismiss = { open = null }) }
}

@Composable
private fun DocRow(title: String, subtitle: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle.isNotBlank()) PunktText(subtitle)
        }
        Spacer(Modifier.width(8.dp))
        ChevronRight()
    }
}
