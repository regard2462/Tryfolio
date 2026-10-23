package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Zeitraum
import de.troetenrepublik.app.i18n.S
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ZeitraumKnopf(aktiv: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (aktiv) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = S.zeitraumEigener },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.EditCalendar, null, modifier = Modifier.size(21.dp),
            tint = if (aktiv) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Ein einzelner Tag steht nur mit seinem Datum.
fun zeitraumText(vonTag: Long, bisTag: Long): String {
    // Mittag UTC liegt in jeder Zeitzone am selben Kalendertag.
    fun tag(t: Long) = Fmt.date(t * Zeitraum.TAG + Zeitraum.TAG / 2)
    return if (vonTag == bisTag) tag(vonTag) else "${tag(minOf(vonTag, bisTag))} – ${tag(maxOf(vonTag, bisTag))}"
}

// Beginn und Ende als Tage seit 1970, hoechstens heute. Ohne gewaehltes Ende gilt der eine Tag.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeitraumDialog(vonTag: Long, bisTag: Long, onDismiss: () -> Unit, onApply: (Long, Long) -> Unit) {
    val heute = remember { LocalDate.now(ZoneId.systemDefault()).toEpochDay() }
    val jahr = remember(heute) { LocalDate.ofEpochDay(heute).year }
    val st = rememberDateRangePickerState(
        initialSelectedStartDateMillis = (if (vonTag > 0) vonTag else heute - 91) * Zeitraum.TAG,
        initialSelectedEndDateMillis = (if (bisTag > 0) bisTag else heute) * Zeitraum.TAG,
        yearRange = 1970..jahr,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis / Zeitraum.TAG <= heute
            override fun isSelectableYear(year: Int): Boolean = year <= jahr
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = st.selectedStartDateMillis != null, onClick = {
                val von = st.selectedStartDateMillis ?: return@TextButton
                val bis = st.selectedEndDateMillis ?: von
                onApply(von / Zeitraum.TAG, bis / Zeitraum.TAG)
            }) { Text(S.apply) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
    ) {
        DateRangePicker(
            state = st,
            modifier = Modifier.weight(1f),
            title = { Text(S.zeitraumTitel, Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}
