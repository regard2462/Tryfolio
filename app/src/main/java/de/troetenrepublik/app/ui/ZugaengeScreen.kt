package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
    Dienst(Abrufkontingent.EODHD, "EODHD", "https://eodhd.com/register"),
    Dienst(Abrufkontingent.DEEPL, "DeepL", "https://www.deepl.com/pro-api"),
    Dienst(Abrufkontingent.REDDIT, "Reddit", de.troetenrepublik.app.market.RedditApi.APPS),
    Dienst(Abrufkontingent.FEC, "FEC (api.data.gov)", "https://api.data.gov/signup/"),
    Dienst(Abrufkontingent.USPTO, "USPTO Open Data Portal", "https://data.uspto.gov/apis/getting-started"),
    Dienst(Abrufkontingent.FINRA, "FINRA", de.troetenrepublik.app.market.Finra.KONSOLE),
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
    // Schluessel holen braucht eine Anmeldung: immer im Browser (externeLinks)
    val uri = externeLinks()
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    // Keine Bildschirmfotos, solange die Schluessel hier stehen koennen
    de.troetenrepublik.app.plattform.Bildschutz(settings.screenshots != de.troetenrepublik.app.data.Bildschirmfotos.ERLAUBT)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.zgTitle, null, onBack) }
        // Knappe Stichpunkte
        item { ZugangPunkte(S.zgIntroPunkte) }
        DIENSTE.forEach { d ->
            item(key = d.gruppe) {
                val gespeichert = when (d.gruppe) {
                    Abrufkontingent.GUARDIAN -> settings.keyGuardian
                    Abrufkontingent.FINNHUB -> settings.keyFinnhub
                    Abrufkontingent.ALPHAVANTAGE -> settings.keyAlphaVantage
                    Abrufkontingent.EODHD -> settings.keyEodhd
                    Abrufkontingent.REDDIT -> settings.keyRedditId
                    Abrufkontingent.FEC -> settings.keyFec
                    Abrufkontingent.USPTO -> settings.keyUspto
                    Abrufkontingent.FINRA -> settings.keyFinraId
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
                    Abrufkontingent.FINRA -> state.behoerden.finraMeldung
                    else -> stand.meldungen[d.gruppe]
                }
                val reddit = d.gruppe == Abrufkontingent.REDDIT
                val finra = d.gruppe == Abrufkontingent.FINRA
                Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    ZugangKopf(d.name, kostenlos = true)
                    // Wozu der Dienst gut ist, darunter die Bedingungen des Anbieters als Stichpunkte
                    ZugangZweck(S.zgZweck(d.gruppe))
                    ZugangPunkte(S.zgPunkte(d.gruppe))
                    OutlinedTextField(
                        value = eingabe,
                        onValueChange = { eingabe = it.trim().take(120) },
                        label = { Text(if (reddit || finra) S.zgRedditId else S.zgKey) },
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
                    if (finra) SchluesselFeld(settings.keyFinraSecret, S.zgFinraSecret) { state.schluesselSetzen(FINRA_SECRET, it) }
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
                        if (reddit) S.zgRedditHolen else if (finra) S.zgFinraHolen else S.zgGetKey(d.name), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                        modifier = Modifier.padding(horizontal = 12.dp).clickable { runCatching { uri.openUri(d.holen) } }.padding(8.dp),
                    )
                }
            }
        }
        item(key = "agsi") { AgsiZugang(state, settings.keyAgsi) }
        // 5.92 Weitere Quellen: Bundesrichter ueber CourtListener, Erwaehnungen bei Bluesky
        item(key = "courtlistener") { CourtListenerZugang(state, settings.keyCourtListener, stand.geladenAm) }
        item(key = "bluesky") { BlueskyZugang(state, settings.bskyHandle, settings.bskyAppPasswort) }
        item(key = "polymarket") { PolymarketAdresse(state, settings.polyAdresse) }
        item(key = "sec") { SecKontakt(state, settings.secKontakt) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Kennung des Schluessels fuer GIE AGSI+ in AppState.schluesselSetzen.
const val AGSI_SCHLUESSEL = "agsi"

// GIE AGSI+ (5.92): Gasspeicher der EU fuer die fruehen Signale. Die API verlangt eine kostenlose Anmeldung; ohne
// Schluessel zeigt die App statt der Daten einen Hinweis auf diesen Zugang.
@Composable
private fun AgsiZugang(state: AppState, gespeichert: String) {
    val uri = externeLinks()
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        ZugangKopf(S.zgAgsiName, kostenlos = true)
        ZugangZweck(S.zgAgsiZweck)
        ZugangPunkte(S.zgAgsiPunkte)
        SchluesselFeld(gespeichert, S.zgKey) { state.schluesselSetzen(AGSI_SCHLUESSEL, it) }
        Text(
            S.zgAgsiHolen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).clickable { runCatching { uri.openUri("https://agsi.gie.eu/account") } }.padding(8.dp),
        )
    }
}

// Kennungen von Handle und App-Passwort fuer Bluesky in AppState.schluesselSetzen (5.92).
const val BSKY_HANDLE = "bsky_handle"
const val BSKY_PASSWORT = "bsky_passwort"

// Verbrauch im laengsten Fenster und die letzte Meldung des Dienstes.
@Composable
private fun ZugangStand(verbraucht: Long, erlaubt: Long, meldung: String?, gesetzt: Boolean) {
    Text(
        S.wqVerbrauch(Fmt.num(verbraucht.toDouble(), 0), Fmt.num(erlaubt.toDouble(), 0)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )
    if (gesetzt && meldung != null) Text(S.wqMeldung(meldung), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
}

// CourtListener (5.92): Finanzerklaerungen der US-Bundesrichter, nur mit eigenem kostenlosem Token.
@Composable
private fun CourtListenerZugang(state: AppState, gespeichert: String, neu: Long) {
    val uri = externeLinks()
    val (verbraucht, erlaubt) = remember(neu, gespeichert) { state.kontingent.stand(Abrufkontingent.COURTLISTENER) }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        ZugangKopf(S.wqClName, kostenlos = true)
        ZugangZweck(S.wqClZweck)
        ZugangPunkte(S.wqClPunkte)
        SchluesselFeld(gespeichert, S.wqClFeld) { state.schluesselSetzen(Abrufkontingent.COURTLISTENER, it) }
        ZugangStand(verbraucht, erlaubt, state.weitere.clMeldung, gespeichert.isNotBlank())
        Text(
            S.wqClHolen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).clickable { runCatching { uri.openUri(de.troetenrepublik.app.engine.WeitereQuellen.CL_TOKEN_SEITE) } }.padding(8.dp),
        )
    }
}

// Bluesky (5.92): Handle und ein App-Passwort, das Bluesky fuer Drittanwendungen ausgibt. Alles andere als die Form
// xxxx-xxxx-xxxx-xxxx wird nicht gespeichert, damit nie ein Hauptpasswort in der App landet.
@Composable
private fun BlueskyZugang(state: AppState, handle: String, passwort: String) {
    val uri = externeLinks()
    var h by remember { mutableStateOf(handle) }
    var pw by remember { mutableStateOf(passwort) }
    var zeigen by remember { mutableStateOf(false) }
    val hGueltig = h.isBlank() || de.troetenrepublik.app.engine.WeitereQuellen.handleBereinigt(h) != null
    val pwGueltig = pw.isBlank() || de.troetenrepublik.app.engine.WeitereQuellen.appPasswortGueltig(pw)
    LaunchedEffect(h) {
        if (h.trim() == handle || !hGueltig) return@LaunchedEffect
        delay(700)
        state.schluesselSetzen(BSKY_HANDLE, h)
    }
    LaunchedEffect(pw) {
        if (pw.trim() == passwort || !pwGueltig) return@LaunchedEffect
        delay(700)
        state.schluesselSetzen(BSKY_PASSWORT, pw)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        ZugangKopf("Bluesky", kostenlos = true)
        ZugangZweck(S.wqBskyZweck)
        ZugangPunkte(S.wqBskyPunkte)
        OutlinedTextField(
            value = h,
            onValueChange = { h = it.trim().take(80) },
            label = { Text(S.wqBskyHandle) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(Radius.field),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (!hGueltig) Text(S.wqBskyHandleUngueltig, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        OutlinedTextField(
            value = pw,
            // Nur die Zeichen eines App-Passworts, hoechstens 19
            onValueChange = { pw = it.trim().lowercase().take(19) },
            label = { Text(S.wqBskyPasswort) },
            singleLine = true,
            visualTransformation = if (zeigen) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { TextButton(onClick = { zeigen = !zeigen }) { Text(if (zeigen) S.zgHide else S.zgShow) } },
            shape = RoundedCornerShape(Radius.field),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (!pwGueltig) Text(S.wqBskyUngueltig, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        val (verbraucht, erlaubt) = remember(handle, passwort) { state.kontingent.stand(Abrufkontingent.BLUESKY) }
        ZugangStand(verbraucht, erlaubt, state.weitere.bskyMeldung, handle.isNotBlank() && passwort.isNotBlank())
        Text(
            S.wqBskyHolen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).clickable { runCatching { uri.openUri(de.troetenrepublik.app.engine.WeitereQuellen.BSKY_PASSWOERTER) } }.padding(8.dp),
        )
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
        ZugangKopf(S.zgSecTitel, kostenlos = true)
        ZugangZweck(S.zgZweck(SEC_KONTAKT))
        ZugangPunkte(S.zgPunkte(SEC_KONTAKT))
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
        ZugangKopf("Polymarket", kostenlos = true)
        ZugangZweck(S.zgZweck(POLY_ADRESSE))
        ZugangPunkte(S.zgPunkte(POLY_ADRESSE))
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

// Kennung fuer das Secret des FINRA-Zugangs (5.94).
const val FINRA_SECRET = "finra_secret"

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

// Kopf eines Zugangs: Name und daneben klar, ob der Zugang kostenlos oder kostenpflichtig ist (seit 5.92).
@Composable
private fun ZugangKopf(name: String, kostenlos: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(10.dp))
        Text(
            if (kostenlos) S.free else S.zgKostenpflichtig,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ZugangZweck(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
}

// Stichpunkte, je Punkt eine Zeile; umbrechende Punkte bleiben hinter dem Punktzeichen eingerueckt.
@Composable
private fun ZugangPunkte(punkte: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        punkte.forEach { p ->
            Row(Modifier.padding(vertical = 1.dp)) {
                Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(14.dp))
                Text(p, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
