package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.Regionen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S

// Ergebnis der Einrichtung; das Startguthaben in Euro, wie die App bucht.
data class Einrichtung(
    val name: String,
    val kapitalEuro: Double,
    val land: String,
    val waehrung: String,
    val handelszeiten: String,
    val handelVon: Int,
    val handelBis: Int,
)

// Betrag auf eine Ziffer gerundet, damit Startguthaben in jeder Waehrung glatt aussehen (58.000 -> 60.000).
internal fun glatt(v: Double): Double {
    if (v <= 0) return v
    val stelle = Math.pow(10.0, Math.floor(Math.log10(v)))
    return Math.round(v / stelle) * stelle
}

// Erster Start: Sprache, Name, Land (Vorschlag nach der Zeitzone), Waehrung, Handelszeiten und Startguthaben.
// kurse: Wechselkurse je Euro fuer das Startguthaben in der gewaehlten Waehrung; ohne Kurs in Euro.
@Composable
fun Onboarding(kurse: Map<String, Double> = emptyMap(), onLanguage: (Lang) -> Unit = {}, onDone: (Einrichtung) -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var capital by rememberSaveable { mutableStateOf(50_000.0) }
    var land by rememberSaveable { mutableStateOf(Regionen.vorschlag()) }
    var waehrung by rememberSaveable { mutableStateOf(Regionen.land(land).waehrung) }
    var zeiten by rememberSaveable { mutableStateOf(MarketHours.LAND) }
    var von by rememberSaveable { mutableStateOf(8 * 60) }
    var bis by rememberSaveable { mutableStateOf(22 * 60) }
    val schritte = 3

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp)
                .padding(top = 60.dp, bottom = if (step == 1) 112.dp else 28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(S.stepOf(step + 1, schritte), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lang.entries.forEach { l ->
                        val active = I18n.lang == l
                        Text(
                            l.code.uppercase(), style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(Radius.chip))
                                .background(if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { I18n.lang = l; onLanguage(l) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            when (step) {
                0 -> {
                    Text(S.welcome, style = MaterialTheme.typography.headlineLarge)
                    Text(S.welcomeText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 28.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text(S.firstName) },
                        singleLine = true,
                        shape = RoundedCornerShape(Radius.field),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(32.dp))
                    FloatingPill(label = S.next, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { step = 1 }
                }
                1 -> {
                    Text(S.regLandTitel, style = MaterialTheme.typography.headlineLarge)
                    Text(S.regLandText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                    LandAuswahl(land) { neu -> land = neu; waehrung = Regionen.land(neu).waehrung }
                }
                2 -> {
                    Text(S.startCapital, style = MaterialTheme.typography.headlineLarge)
                    Text(S.startCapitalText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Betraege in der gewaehlten Waehrung, glatt gerundet; gebucht wird der Gegenwert in Euro
                        val kurs = if (waehrung == "EUR") 1.0 else kurse[waehrung]?.takeIf { it > 0 }
                        listOf(1_000.0, 10_000.0, 50_000.0, 100_000.0).forEach { v ->
                            val active = capital == v
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                                    .background(if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { capital = v }.padding(horizontal = 20.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (kurs == null) Fmt.euro(v, 0) else Fmt.moneyIn(glatt(v * kurs), Derivatives.currencySymbol(waehrung), 0), style = MaterialTheme.typography.titleMedium,
                                    color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                if (active) CheckMark(size = 18.dp, color = MaterialTheme.colorScheme.surface)
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f)) { step = 1 }
                        FloatingPill(label = S.letsGo, modifier = Modifier.weight(1f)) {
                            val kurs = if (waehrung == "EUR") 1.0 else kurse[waehrung]?.takeIf { it > 0 }
                            val euro = if (kurs == null) capital else glatt(capital * kurs) / kurs
                            onDone(Einrichtung(name.trim(), euro, land, waehrung, zeiten, von, bis))
                        }
                    }
                }
            }
        }
        // Die Laenderliste ist lang: Zurueck und Weiter stehen fest am unteren Rand, ein Land ist immer gewaehlt
        if (step == 1) {
            Weiter(Modifier.align(Alignment.BottomCenter), onBack = { step = 0 }) { step = 2 }
        }
    }
}

// Zurueck und Weiter am unteren Rand der Einrichtung.
@Composable
private fun Weiter(modifier: Modifier, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding()
            .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FloatingPill(label = S.back, filled = false, modifier = Modifier.weight(1f), onClick = onBack)
        FloatingPill(label = S.next, modifier = Modifier.weight(1f), onClick = onNext)
    }
}

// Einrichtung mit den Wechselkursen der App; am Ende bucht AppState.einrichten Startguthaben und Einstellungen.
@Composable
fun EinrichtungSeite(state: AppState, onLanguage: (Lang) -> Unit) {
    val kurse by state.market.fxKurse.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { state.market.dollarKurs() } }
    Onboarding(kurse, onLanguage) { state.einrichten(it) }
}
