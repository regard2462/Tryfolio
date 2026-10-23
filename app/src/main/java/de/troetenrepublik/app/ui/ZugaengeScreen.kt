package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Abrufkontingent
import de.troetenrepublik.app.market.NewsRepository
import kotlinx.coroutines.delay

// Ein Dienst mit eigenem Schluessel: Gruppe des Kontingents, Name, Seite zum Holen des Schluessels.
private data class Dienst(val gruppe: String, val name: String, val holen: String)

private val DIENSTE = listOf(
    Dienst(Abrufkontingent.GUARDIAN, "The Guardian", "https://open-platform.theguardian.com/access/"),
    Dienst(Abrufkontingent.FINNHUB, "Finnhub", "https://finnhub.io/register"),
    Dienst(Abrufkontingent.ALPHAVANTAGE, "Alpha Vantage", "https://www.alphavantage.co/support/#api-key"),
    Dienst(Abrufkontingent.DEEPL, "DeepL", "https://www.deepl.com/pro-api"),
    Dienst(Abrufkontingent.REDDIT, "Reddit", de.troetenrepublik.app.market.RedditApi.APPS),
    Dienst(Abrufkontingent.FEC, "FEC (api.data.gov)", "https://api.data.gov/signup/"),
    Dienst(Abrufkontingent.USPTO, "USPTO Open Data Portal", "https://data.uspto.gov/apis/getting-started"),
)

// Meldung eines Dienstes fuer die Anzeige (Limit, abgelehnter Schluessel oder der Text des Anbieters).
fun dienstMeldung(m: String): String = when (m) {
    NewsRepository.LIMIT -> S.zgLimit
    NewsRepository.SCHLUESSEL -> S.zgKeyRejected
    else -> S.zgProviderSays(m.take(300))
}

// Zugaenge: eigene Schluessel fuer The Guardian, Finnhub, Alpha Vantage und DeepL, mit Zweck, Limits,
// Bedingungen und dem Verbrauch im laufenden Fenster. Die Schluessel bleiben auf dem Geraet.
@Composable
fun ZugaengeScreen(state: AppState, onBack: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val stand by state.news.stand.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    // Wie bei den Broker-Schluesseln: keine Bildschirmfotos, solange die Schluessel hier stehen koennen
    de.troetenrepublik.app.plattform.Bildschutz(settings.screenshots != de.troetenrepublik.app.data.Bildschirmfotos.ERLAUBT)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.zgTitle, null, onBack) }
        item {
            AufklappText(S.zgIntro, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        DIENSTE.forEach { d ->
            item(key = d.gruppe) {
                val gespeichert = when (d.gruppe) {
                    Abrufkontingent.GUARDIAN -> settings.keyGuardian
                    Abrufkontingent.FINNHUB -> settings.keyFinnhub
                    Abrufkontingent.ALPHAVANTAGE -> settings.keyAlphaVantage
                    Abrufkontingent.REDDIT -> settings.keyRedditId
                    Abrufkontingent.FEC -> settings.keyFec
                    Abrufkontingent.USPTO -> settings.keyUspto
                    else -> settings.keyDeepl
                }
                var eingabe by remember(d.gruppe) { mutableStateOf(gespeichert) }
                var zeigen by remember(d.gruppe) { mutableStateOf(false) }
                // Eingaben kurz nach dem Tippen speichern
                LaunchedEffect(eingabe) {
                    if (eingabe.trim() == gespeichert) return@LaunchedEffect
                    delay(700)
                    state.schluesselSetzen(d.gruppe, eingabe)
                }
                val (verbraucht, erlaubt) = remember(stand.geladenAm, gespeichert) { state.kontingent.stand(d.gruppe) }
                val meldung = when (d.gruppe) {
                    Abrufkontingent.DEEPL -> state.uebersetzer.meldung
                    Abrufkontingent.REDDIT -> state.reddit.meldung
                    else -> stand.meldungen[d.gruppe]
                }
                val reddit = d.gruppe == Abrufkontingent.REDDIT
                Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    SectionHeader(d.name)
                    // Wozu der Dienst gut ist; die Bedingungen des Anbieters erst hinter Mehr
                    AufklappText(S.zgPurpose(d.gruppe) + " " + S.zgTerms(d.gruppe), Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    OutlinedTextField(
                        value = eingabe,
                        onValueChange = { eingabe = it.trim().take(120) },
                        label = { Text(if (reddit) S.zgRedditId else S.zgKey) },
                        singleLine = true,
                        visualTransformation = if (zeigen) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { zeigen = !zeigen }) { Text(if (zeigen) S.zgHide else S.zgShow) }
                        },
                        shape = RoundedCornerShape(Radius.field),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    // Reddit: zur Client-ID das Secret, das nur Apps vom Typ „script“ und „web app“ haben
                    if (reddit) SchluesselFeld(settings.keyRedditSecret, S.zgRedditSecret) { state.schluesselSetzen(REDDIT_SECRET, it) }
                    Text(
                        S.zgUsage(d.gruppe, Fmt.num(verbraucht.toDouble(), 0), Fmt.num(erlaubt.toDouble(), 0)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                    if (gespeichert.isNotBlank() && meldung != null) {
                        Text(dienstMeldung(meldung), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                    }
                    Text(
                        if (reddit) S.zgRedditHolen else S.zgGetKey(d.name), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.padding(horizontal = 12.dp).clickable { runCatching { uri.openUri(d.holen) } }.padding(8.dp),
                    )
                }
            }
        }
        item(key = "polymarket") { PolymarketAdresse(state, settings.polyAdresse) }
        item(key = "sec") { SecKontakt(state, settings.secKontakt) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Kennung der SEC-Kontaktadresse in AppState.schluesselSetzen.
const val SEC_KONTAKT = "sec_kontakt"

// Kontaktadresse fuer die SEC: nur damit holt die App neue US-Insidermeldungen; sie steht in der Kennung der Abrufe.
@Composable
private fun SecKontakt(state: AppState, gespeichert: String) {
    var eingabe by remember { mutableStateOf(gespeichert) }
    val t = eingabe.trim()
    val gueltig = t.isEmpty() || de.troetenrepublik.app.market.SecInsider.kennung(t) != null
    LaunchedEffect(t) {
        if (t == gespeichert || !gueltig) return@LaunchedEffect
        delay(700)
        state.schluesselSetzen(SEC_KONTAKT, t)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        SectionHeader(S.zgSecTitel)
        AufklappText(S.zgSecZweck, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        OutlinedTextField(
            value = eingabe,
            onValueChange = { eingabe = it.trim().take(120) },
            label = { Text(S.zgSecFeld) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(Radius.field),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (!gueltig) Text(S.zgSecUngueltig, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
    }
}

// Kennung der Polymarket-Adresse in AppState.schluesselSetzen.
const val POLY_ADRESSE = "poly_adresse"

// Polymarket: nur die oeffentliche Adresse, offen sichtbar. Was nach privatem Schluessel oder Seed-Woertern aussieht,
// wird sofort verworfen und nie gespeichert.
@Composable
private fun PolymarketAdresse(state: AppState, gespeichert: String) {
    var eingabe by remember { mutableStateOf(gespeichert) }
    var abgewiesen by remember { mutableStateOf(false) }
    val t = eingabe.trim()
    val gueltig = t.isEmpty() || de.troetenrepublik.app.market.Prognose.adresseGueltig(t)
    LaunchedEffect(t) {
        if (t.lowercase() == gespeichert || !gueltig) return@LaunchedEffect
        delay(700)
        state.schluesselSetzen(POLY_ADRESSE, t)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        SectionHeader("Polymarket")
        AufklappText(S.zgPolyZweck, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        OutlinedTextField(
            value = eingabe,
            onValueChange = { neu ->
                abgewiesen = de.troetenrepublik.app.market.Prognose.geheimnisVerdacht(neu)
                eingabe = if (abgewiesen) "" else neu.trim().take(42)
            },
            label = { Text(S.zgPolyAdresse) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            shape = RoundedCornerShape(Radius.field),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val meldung = when {
            abgewiesen -> S.zgPolyGeheim
            !gueltig -> S.zgPolyUngueltig
            else -> null
        }
        if (meldung != null) Text(meldung, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
    }
}

// Kennung fuer das Secret der Reddit-App in AppState.schluesselSetzen.
const val REDDIT_SECRET = "reddit_secret"

// Ein weiteres verdecktes Eingabefeld, das kurz nach dem Tippen speichert.
@Composable
private fun SchluesselFeld(gespeichert: String, titel: String, speichern: (String) -> Unit) {
    var eingabe by remember { mutableStateOf(gespeichert) }
    var zeigen by remember { mutableStateOf(false) }
    LaunchedEffect(eingabe) {
        if (eingabe.trim() == gespeichert) return@LaunchedEffect
        delay(700)
        speichern(eingabe)
    }
    OutlinedTextField(
        value = eingabe,
        onValueChange = { eingabe = it.trim().take(120) },
        label = { Text(titel) },
        singleLine = true,
        visualTransformation = if (zeigen) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { TextButton(onClick = { zeigen = !zeigen }) { Text(if (zeigen) S.zgHide else S.zgShow) } },
        shape = RoundedCornerShape(Radius.field),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
    )
}
