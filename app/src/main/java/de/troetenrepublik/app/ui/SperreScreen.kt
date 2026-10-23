package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Sperre
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.Krypto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Sperrbildschirm der App-Sperre: liegt ueber allem und faengt jede Beruehrung ab. PIN ueber das Ziffernfeld,
// Passwort ueber die Tastatur. Wer PIN oder Passwort vergessen hat, kann nur neu beginnen.
@Composable
fun SperrBildschirm(state: AppState) {
    val art by state.sperrArt.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pin = art != Sperre.PASSWORT
    var eingabe by remember { mutableStateOf("") }
    var meldung by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }
    var zuruecksetzen by remember { mutableStateOf(false) }
    // Wartezeit nach Fehlversuchen sichtbar herunterzaehlen
    var warten by remember { mutableStateOf(state.sperrWartezeit()) }
    LaunchedEffect(meldung) { while (true) { warten = state.sperrWartezeit(); if (warten <= 0) break; delay(500) } }

    fun pruefen() {
        if (laeuft || eingabe.isEmpty()) return
        laeuft = true
        val e = eingabe
        scope.launch {
            meldung = state.entsperren(e)
            eingabe = ""
            laeuft = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            // Beruehrungen gehen nicht an die App darunter
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.statusBarsPadding().widthIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Tryfolio", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(S.sperreGesperrt, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))
            if (pin) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(18.dp)) {
                    repeat(maxOf(eingabe.length, 4)) { i ->
                        Box(
                            Modifier.size(14.dp).clip(CircleShape)
                                .background(if (i < eingabe.length) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Keypad(allowDecimal = false, onDigit = { d -> if (eingabe.length < 8) eingabe += d; meldung = null }, onBackspace = { eingabe = eingabe.dropLast(1) })
            } else {
                OutlinedTextField(
                    value = eingabe, onValueChange = { eingabe = it.take(200); meldung = null }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { pruefen() }),
                    shape = RoundedCornerShape(Radius.field), modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
            (if (warten > 0) S.sperreWarten((warten + 999) / 1000) else meldung)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (laeuft) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                FloatingPill(S.sperreEntsperren, enabled = !laeuft && eingabe.isNotEmpty() && warten <= 0, modifier = Modifier.padding(start = 8.dp)) { pruefen() }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                S.sperreVergessen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                modifier = Modifier.clip(RoundedCornerShape(Radius.chip)).clickable { zuruecksetzen = true }.padding(8.dp),
            )
        }
    }

    if (zuruecksetzen) AlertDialog(
        onDismissRequest = { zuruecksetzen = false },
        title = { Text(S.sperreZuruecksetzenTitel) },
        text = { Text(S.sperreZuruecksetzenText) },
        confirmButton = {
            TextButton(onClick = { zuruecksetzen = false; scope.launch { state.sperreZuruecksetzen() } }) {
                Text(S.sperreZuruecksetzen, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { zuruecksetzen = false }) { Text(S.cancel) } },
    )
}

// Einstellungen der App-Sperre: einschalten mit PIN oder Passwort, aendern, ausschalten.
@Composable
fun SperreScreen(state: AppState, onBack: () -> Unit) {
    val art by state.sperrArt.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberScrollState()
    // "pin" / "passwort": einschalten bzw. auf diese Art aendern; "aus": ausschalten
    var modus by remember { mutableStateOf<String?>(null) }
    var alt by remember { mutableStateOf("") }
    var neu by remember { mutableStateOf("") }
    var nochmal by remember { mutableStateOf("") }
    var meldung by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }

    fun zuruecksetzenFelder() { alt = ""; neu = ""; nochmal = ""; meldung = null }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { liste.value == 0 }.verticalScroll(liste),
    ) {
        ScreenHeader(S.sperreTitel, S.sperreStatus(art), onBack)
        // Ein Satz, der Rest (Verschluesselung, PIN gegen Passwort) hinter Mehr; die Warnung vor dem Vergessen bleibt sichtbar
        AufklappText(S.sperreText + " " + S.sperreHinweisPin, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        if (!Krypto.verfuegbar) {
            Text(S.sperreNurApp, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
            return@Column
        }
        Text(S.sperreVergessenHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        Spacer(Modifier.height(8.dp))
        if (modus == null) {
            if (art == null) {
                NavRow(S.sperrePinFestlegen, "") { zuruecksetzenFelder(); modus = Sperre.PIN }
                NavRow(S.sperrePasswortFestlegen, "") { zuruecksetzenFelder(); modus = Sperre.PASSWORT }
            } else {
                NavRow(S.sperreAendern, S.sperrePinFestlegen + " / " + S.sperrePasswortFestlegen) { zuruecksetzenFelder(); modus = art }
                NavRow(S.sperreAusschalten, "") { zuruecksetzenFelder(); modus = "aus" }
            }
        } else {
            val ausschalten = modus == "aus"
            val neuPin = modus == Sperre.PIN
            if (art != null) {
                Feld(S.sperreAlt, alt, art == Sperre.PIN) { alt = it; meldung = null }
            }
            if (!ausschalten) {
                if (art != null) Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill(S.sperrePinFestlegen, active = neuPin) { modus = Sperre.PIN; neu = ""; nochmal = "" }
                    FilterPill(S.sperrePasswortFestlegen, active = !neuPin) { modus = Sperre.PASSWORT; neu = ""; nochmal = "" }
                }
                Feld(S.sperreNeu(neuPin), neu, neuPin) { neu = it; meldung = null }
                Feld(S.sperreWiederholen, nochmal, neuPin) { nochmal = it; meldung = null }
            }
            meldung?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (laeuft) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                FloatingPill(S.cancel, filled = false) { modus = null; zuruecksetzenFelder() }
                FloatingPill(if (ausschalten) S.sperreAusschalten else S.sperreSpeichern, enabled = !laeuft) {
                    val gueltig = if (neuPin) neu.length in 4..8 && neu.all { it.isDigit() } else neu.length >= 8
                    when {
                        !ausschalten && !gueltig -> meldung = S.sperreZuKurz(neuPin)
                        !ausschalten && neu != nochmal -> meldung = S.sperreUngleich
                        art != null && alt.isEmpty() -> meldung = S.sperreFalsch(0)
                        else -> {
                            laeuft = true
                            val m = modus!!
                            scope.launch {
                                meldung = when {
                                    m == "aus" -> state.sperreAusschalten(alt)
                                    art == null -> state.sperreEinrichten(neu, m)
                                    else -> state.sperreAendern(alt, neu, m)
                                }
                                laeuft = false
                                if (meldung == null) { modus = null; zuruecksetzenFelder() }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Feld(titel: String, wert: String, pin: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = wert, onValueChange = { v -> onChange(if (pin) v.filter { it.isDigit() }.take(8) else v.take(200)) },
        label = { Text(titel) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (pin) KeyboardType.NumberPassword else KeyboardType.Password, autoCorrectEnabled = false),
        shape = RoundedCornerShape(Radius.field), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

// Screenshots erlauben oder sperren: ueberall, nur nicht auf der Broker-Seite, oder nirgends.
@Composable
fun ScreenshotsScreen(state: AppState, onBack: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberScrollState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { liste.value == 0 }.verticalScroll(liste)) {
        ScreenHeader(S.screenshotsTitel, S.screenshotsOption(settings.screenshots), onBack)
        Text(S.screenshotsText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            de.troetenrepublik.app.data.Bildschirmfotos.ALLE.forEach { m ->
                OptionRow(S.screenshotsOption(m), S.screenshotsHinweis(m), settings.screenshots == m) {
                    scope.launch { state.settings.update { it.copy(screenshots = m) } }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
