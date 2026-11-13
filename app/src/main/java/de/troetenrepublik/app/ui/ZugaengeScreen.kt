package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
    Dienst(Abrufkontingent.DOL, "Department of Labor", "https://dataportal.dol.gov/registration"),
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
fun ZugaengeScreen(state: AppState, onBack: () -> Unit, oeffnen: (String) -> Unit = {}) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val stand by state.news.stand.collectAsStateWithLifecycle()
    // Schluessel holen braucht eine Anmeldung: immer im Browser (externeLinks)
    val uri = externeLinks()
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    // Eigene Kursdateien (ui/KursDateiImport.kt)
    val kursEinlesen = rememberKursdateiEinlesen(state)
    val kursReihen by de.troetenrepublik.app.market.EigeneKurse.eintraege.collectAsStateWithLifecycle()
    var kursOffen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // Keine Bildschirmfotos, solange die Schluessel hier stehen koennen
    de.troetenrepublik.app.plattform.Bildschutz(settings.screenshots != de.troetenrepublik.app.data.Bildschirmfotos.ERLAUBT)
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.zgTitle, null, onBack) }
        // Alles, was die Person selbst laden oder einlesen muss
        item(key = "anstossen") { NavRow(S.anTitel, S.anUntertitel) { oeffnen(Anstossen.ROUTE) } }
        // Ampel je Quelle; die Seite liegt sonst nur unter Erweitert
        item(key = "datenquellen") { NavRow(S.dqTitel) { oeffnen(DATENQUELLEN) } }
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
                    Abrufkontingent.DOL -> settings.keyDol
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
                Zugang(
                    d.gruppe, d.name, kostenlos = true, gesetzt = gespeichert.isNotBlank(), fehler = meldung != null,
                    verbrauch = "${Fmt.num(verbraucht.toDouble(), 0)}/${Fmt.num(erlaubt.toDouble(), 0)}",
                    anteil = (verbraucht.toDouble() / erlaubt.toDouble().coerceAtLeast(1.0)).toFloat(),
                ) {
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
                        modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri(d.holen) } }.padding(8.dp),
                    )
                }
            }
        }
        item(key = "massive") { MassiveZugang(state, settings.keyMassive, stand.geladenAm) }
        item(key = "tiingo") {
            val tx = de.troetenrepublik.app.i18n.KursRueckfallTexte
            KursAnbieterZugang(
                state, tx.t("name.tiingo"), tx.t("zweck.tiingo"), Abrufkontingent.TIINGO, settings.keyTiingo, stand.geladenAm,
                state.market.tiingoMeldung, de.troetenrepublik.app.market.TiingoKurse.NAME, de.troetenrepublik.app.market.TiingoKurse.ANMELDUNG,
            )
        }
        item(key = "twelvedata") {
            val tx = de.troetenrepublik.app.i18n.KursRueckfallTexte
            KursAnbieterZugang(
                state, tx.t("name.twelve"), tx.t("zweck.twelve"), Abrufkontingent.TWELVEDATA, settings.keyTwelveData, stand.geladenAm,
                state.market.twelveMeldung, de.troetenrepublik.app.market.TwelveDataKurse.NAME, de.troetenrepublik.app.market.TwelveDataKurse.ANMELDUNG,
            )
        }
        // Eigene Kursdateien: Rueckfall hinter den Kursanbietern und Ergaenzung alter Kurse
        eigeneKursdateien(kursEinlesen, kursReihen, kursOffen) { kursOffen = !kursOffen }
        item(key = "agsi") { AgsiZugang(state, settings.keyAgsi) }
        item(key = "entsoe") { StromZugang(state, settings.keyEntsoe, stand.geladenAm) }
        // 5.92 Weitere Quellen: Bundesrichter ueber CourtListener, Erwaehnungen bei Bluesky
        item(key = "courtlistener") { CourtListenerZugang(state, settings.keyCourtListener, stand.geladenAm) }
        item(key = "bluesky") { BlueskyZugang(state, settings.bskyHandle, settings.bskyAppPasswort) }
        item(key = "polymarket") { PolymarketAdresse(state, settings.polyAdresse) }
        item(key = "metaculus") { MetaculusZugang(state, settings.keyMetaculus) }
        item(key = "sec") { SecKontakt(state, settings.secKontakt) }
        item(key = "erklaerer") { ErklaererZugang(state, settings) }
        // [0.78] Flex-Bericht von Interactive Brokers, ui/FlexAbgleich.kt
        item(key = "flex") { FlexZugang(state, settings) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Massive (frueher Polygon.io): zweite Kursquelle fuer US-Werte hinter der Hauptquelle, nur mit eigenem Schluessel.
@Composable
private fun MassiveZugang(state: AppState, gespeichert: String, neu: Long) {
    val uri = externeLinks()
    val t = de.troetenrepublik.app.i18n.KursRueckfallTexte
    val (verbraucht, erlaubt) = remember(neu, gespeichert) { state.kontingent.stand(Abrufkontingent.MASSIVE) }
    val meldung = state.market.kursRueckfallMeldung
    Zugang(
        "massive", t.t("name"), kostenlos = true, gesetzt = gespeichert.isNotBlank(), fehler = meldung != null,
        verbrauch = "${Fmt.num(verbraucht.toDouble(), 0)}/${Fmt.num(erlaubt.toDouble(), 0)}",
        anteil = (verbraucht.toDouble() / erlaubt.toDouble().coerceAtLeast(1.0)).toFloat(),
    ) {
        ZugangZweck(t.t("zweck"))
        ZugangPunkte(t.t("punkte").split('\n'))
        SchluesselFeld(gespeichert, S.zgKey) { state.schluesselSetzen(Abrufkontingent.MASSIVE, it) }
        Text(
            t.t("stand", Fmt.num(verbraucht.toDouble(), 0), Fmt.num(erlaubt.toDouble(), 0)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        if (gespeichert.isNotBlank() && meldung != null) Text(dienstMeldung(meldung), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        Text(
            S.zgGetKey(de.troetenrepublik.app.market.MassiveKurse.NAME), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri(de.troetenrepublik.app.market.MassiveKurse.SEITE) } }.padding(8.dp),
        )
    }
}

// Tiingo und Twelve Data: weitere Kursquellen fuer US-Werte hinter Massive, nur mit eigenem Schluessel; eine Zeile, was sie liefern.
@Composable
private fun KursAnbieterZugang(
    state: AppState, name: String, zweck: String, gruppe: String, gespeichert: String, neu: Long, meldung: String?, anbieter: String, anmeldung: String,
) {
    val uri = externeLinks()
    val (verbraucht, erlaubt) = remember(neu, gespeichert) { state.kontingent.stand(gruppe) }
    Zugang(
        gruppe, name, kostenlos = true, gesetzt = gespeichert.isNotBlank(), fehler = meldung != null,
        verbrauch = "${Fmt.num(verbraucht.toDouble(), 0)}/${Fmt.num(erlaubt.toDouble(), 0)}",
        anteil = (verbraucht.toDouble() / erlaubt.toDouble().coerceAtLeast(1.0)).toFloat(),
    ) {
        ZugangZweck(zweck)
        SchluesselFeld(gespeichert, S.zgKey) { state.schluesselSetzen(gruppe, it) }
        Text(
            de.troetenrepublik.app.i18n.KursRueckfallTexte.t("stand.tag", Fmt.num(verbraucht.toDouble(), 0), Fmt.num(erlaubt.toDouble(), 0)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        if (gespeichert.isNotBlank() && meldung != null) Text(dienstMeldung(meldung), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        Text(
            S.zgGetKey(anbieter), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri(anmeldung) } }.padding(8.dp),
        )
    }
}

// Kennung des Schluessels fuer GIE AGSI+ in AppState.schluesselSetzen.
const val AGSI_SCHLUESSEL = "agsi"

// GIE AGSI+ (5.92): Gasspeicher der EU fuer die fruehen Signale. Die API verlangt eine kostenlose Anmeldung; ohne
// Schluessel zeigt die App statt der Daten einen Hinweis auf diesen Zugang.
@Composable
private fun AgsiZugang(state: AppState, gespeichert: String) {
    val uri = externeLinks()
    Zugang(AGSI_SCHLUESSEL, S.zgAgsiName, kostenlos = true, gesetzt = gespeichert.isNotBlank()) {
        ZugangZweck(S.zgAgsiZweck)
        ZugangPunkte(S.zgAgsiPunkte)
        SchluesselFeld(gespeichert, S.zgKey) { state.schluesselSetzen(AGSI_SCHLUESSEL, it) }
        Text(
            S.zgAgsiHolen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri("https://agsi.gie.eu/account") } }.padding(8.dp),
        )
    }
}

// ENTSO-E Transparency Platform: Strompreise je Land im Laenderblatt, nur mit dem eigenen Token. Die App liefert keinen
// eigenen Token aus und legt kein Konto an.
@Composable
private fun StromZugang(state: AppState, gespeichert: String, neu: Long) {
    val uri = externeLinks()
    val t = de.troetenrepublik.app.i18n.StromTexte
    val (verbraucht, erlaubt) = remember(neu, gespeichert) { state.kontingent.stand(Abrufkontingent.ENTSOE) }
    Zugang(
        Abrufkontingent.ENTSOE, t.t("zg.name"), kostenlos = true, gesetzt = gespeichert.isNotBlank(),
        verbrauch = "${Fmt.num(verbraucht.toDouble(), 0)}/${Fmt.num(erlaubt.toDouble(), 0)}",
        anteil = (verbraucht.toDouble() / erlaubt.toDouble().coerceAtLeast(1.0)).toFloat(),
    ) {
        ZugangZweck(t.t("zg.zweck"))
        ZugangPunkte(t.t("zg.punkte").split('\n'))
        SchluesselFeld(gespeichert, t.t("zg.feld")) { state.schluesselSetzen(Abrufkontingent.ENTSOE, it) }
        Text(
            t.t("zg.stand", Fmt.num(verbraucht.toDouble(), 0), Fmt.num(erlaubt.toDouble(), 0)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        Text(
            t.t("zg.holen"), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri(de.troetenrepublik.app.engine.StromMarkt.ENTSOE_SEITE) } }.padding(8.dp),
        )
        Text(
            t.t("zg.anleitung"), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri(de.troetenrepublik.app.engine.StromMarkt.ENTSOE_ANLEITUNG) } }.padding(8.dp),
        )
    }
}

// Kennung des Tokens fuer Metaculus in AppState.schluesselSetzen.
const val METACULUS_TOKEN = "metaculus_token"

// Metaculus: Gemeinschaftsprognosen der Prognosemaerkte, nur mit dem Token eines eigenen Kontos.
@Composable
private fun MetaculusZugang(state: AppState, gespeichert: String) {
    val uri = externeLinks()
    val t = de.troetenrepublik.app.i18n.PrognoseQuellenTexte
    Zugang(METACULUS_TOKEN, "Metaculus", kostenlos = true, gesetzt = gespeichert.isNotBlank()) {
        ZugangZweck(t.t("zg.zweck"))
        ZugangPunkte(t.t("zg.punkte").split('\n'))
        SchluesselFeld(gespeichert, t.t("zg.feld")) { state.schluesselSetzen(METACULUS_TOKEN, it) }
        Text(
            t.t("zg.holen"), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri(de.troetenrepublik.app.market.PrognoseWeitere.METACULUS_TOKEN_SEITE) } }.padding(8.dp),
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
        S.zgUsage("", Fmt.num(verbraucht.toDouble(), 0), Fmt.num(erlaubt.toDouble(), 0)),
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
    Zugang(
        Abrufkontingent.COURTLISTENER, S.wqClName, kostenlos = true, gesetzt = gespeichert.isNotBlank(), fehler = state.weitere.clMeldung != null,
        verbrauch = "${Fmt.num(verbraucht.toDouble(), 0)}/${Fmt.num(erlaubt.toDouble(), 0)}",
        anteil = (verbraucht.toDouble() / erlaubt.toDouble().coerceAtLeast(1.0)).toFloat(),
    ) {
        ZugangZweck(S.wqClZweck)
        ZugangPunkte(S.wqClPunkte)
        SchluesselFeld(gespeichert, S.wqClFeld) { state.schluesselSetzen(Abrufkontingent.COURTLISTENER, it) }
        ZugangStand(verbraucht, erlaubt, state.weitere.clMeldung, gespeichert.isNotBlank())
        Text(
            S.wqClHolen, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri(de.troetenrepublik.app.engine.WeitereQuellen.CL_TOKEN_SEITE) } }.padding(8.dp),
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
    Zugang(BSKY_HANDLE, "Bluesky", kostenlos = true, gesetzt = handle.isNotBlank() && passwort.isNotBlank(), fehler = state.weitere.bskyMeldung != null) {
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
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri(de.troetenrepublik.app.engine.WeitereQuellen.BSKY_PASSWOERTER) } }.padding(8.dp),
        )
    }
}

// Kennungen des Erklaer-Assistenten in AppState.schluesselSetzen (0.77).
const val ERK_ANBIETER = "erk_anbieter"
const val ERK_ADRESSE = "erk_adresse"
const val ERK_MODELL = "erk_modell"
const val ERK_SCHLUESSEL = "erk_schluessel"

// Erklaer-Assistent: Anbieter, Modell und eigener Schluessel (liegt im Tresor). Kostenpflichtig nach dem Tarif des Anbieters.
@Composable
private fun ErklaererZugang(state: AppState, s: de.troetenrepublik.app.data.Settings) {
    val uri = externeLinks()
    val openai = s.erklaererAnbieter == de.troetenrepublik.app.engine.Erklaerer.OPENAI
    var adresse by remember { mutableStateOf(s.erklaererAdresse) }
    var modell by remember { mutableStateOf(s.erklaererModell) }
    val adresseGueltig = adresse.isBlank() || de.troetenrepublik.app.engine.Erklaerer.basisadresse(adresse) != null
    LaunchedEffect(adresse) {
        if (adresse.trim() == s.erklaererAdresse || !adresseGueltig) return@LaunchedEffect
        delay(700)
        state.schluesselSetzen(ERK_ADRESSE, adresse)
    }
    LaunchedEffect(modell) {
        if (modell.trim() == s.erklaererModell) return@LaunchedEffect
        delay(700)
        state.schluesselSetzen(ERK_MODELL, modell)
    }
    Zugang(ERK_ANBIETER, S.erkTitel, kostenlos = false, gesetzt = s.keyErklaerer.isNotBlank()) {
        ZugangZweck(S.erkZweck)
        ZugangPunkte(S.erkPunkte)
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Chip("Anthropic", !openai) { state.schluesselSetzen(ERK_ANBIETER, de.troetenrepublik.app.engine.Erklaerer.ANTHROPIC) }
            Chip(S.erkOpenai, openai) { state.schluesselSetzen(ERK_ANBIETER, de.troetenrepublik.app.engine.Erklaerer.OPENAI) }
        }
        if (openai) {
            OutlinedTextField(
                value = adresse,
                onValueChange = { adresse = it.trim().take(200) },
                label = { Text(S.erkAdresse) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (!adresseGueltig) Text(S.erkAdresseUngueltig, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            OutlinedTextField(
                value = modell,
                onValueChange = { modell = it.trim().take(80) },
                label = { Text(S.erkModellname) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            )
        } else {
            val aktuell = s.erklaererModell.ifBlank { de.troetenrepublik.app.engine.Erklaerer.VORGABE_MODELL }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                de.troetenrepublik.app.engine.Erklaerer.ANTHROPIC_MODELLE.forEach { m ->
                    Chip(m.removePrefix("claude-").substringBefore('-').replaceFirstChar { it.uppercase() }, aktuell == m) { state.schluesselSetzen(ERK_MODELL, m) }
                }
            }
        }
        SchluesselFeld(s.keyErklaerer, S.zgKey) { state.schluesselSetzen(ERK_SCHLUESSEL, it) }
        if (!openai) Text(
            S.zgGetKey("Anthropic"), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri("https://console.anthropic.com/settings/keys") } }.padding(8.dp),
        )
    }
}

// Kennung der SEC-Kontaktadresse in AppState.schluesselSetzen.
const val SEC_KONTAKT = "sec_kontakt"

// Kontaktadresse fuer die SEC: nur damit holt die App neue US-Insidermeldungen; sie steht in der Kennung der Abrufe.
@Composable
private fun SecKontakt(state: AppState, gespeichert: String) {
    var eingabe by remember(gespeichert) { mutableStateOf(gespeichert) }
    val t = eingabe.trim()
    val gueltig = t.isEmpty() || de.troetenrepublik.app.market.SecInsider.kennung(t) != null
    LaunchedEffect(t) {
        if (t == gespeichert || !gueltig) return@LaunchedEffect
        delay(700)
        state.schluesselSetzen(SEC_KONTAKT, t)
    }
    Zugang(SEC_KONTAKT, S.zgSecTitel, kostenlos = true, gesetzt = gespeichert.isNotBlank()) {
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
    Zugang(POLY_ADRESSE, "Polymarket", kostenlos = true, gesetzt = gespeichert.isNotBlank()) {
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

// Ein Zugang als eine Zeile mit Name und Zustand (nicht gesetzt, aktiv mit Verbrauch, Fehler); Zweck, Bedingungen und
// Eingabe erst beim Aufklappen. Der Zustand der Zeile wird gemerkt.
@Composable
private fun Zugang(
    key: String, name: String, kostenlos: Boolean, gesetzt: Boolean, fehler: Boolean = false, verbrauch: String? = null,
    anteil: Float? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    var offen by androidx.compose.runtime.saveable.rememberSaveable(key) { mutableStateOf(false) }
    val signal = LocalSignal.current
    val zustand = when {
        !gesetzt -> S.zgZustandNein
        fehler -> S.zgZustandFehler
        else -> S.zgZustandJa
    }
    val farbe = when {
        !gesetzt -> MaterialTheme.colorScheme.onSurfaceVariant
        fehler -> MaterialTheme.colorScheme.error
        else -> signal.up
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                if (!kostenlos) Text(S.zgKostenpflichtig, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Verbrauch im Abruffenster als Balken mit der Zahl dahinter
                if (gesetzt && verbrauch != null && anteil != null) {
                    val spur = MaterialTheme.colorScheme.surfaceContainerHigh
                    val balken = if (anteil >= 0.9f) signal.down else AnteilsFarben.folge(0)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Canvas(Modifier.weight(1f).height(5.dp)) {
                            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
                            drawRoundRect(spur, cornerRadius = r)
                            val w = size.width * anteil.coerceIn(0f, 1f)
                            if (w > 0f) drawRoundRect(balken, size = androidx.compose.ui.geometry.Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(verbrauch, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(zustand, style = MaterialTheme.typography.labelLarge, color = farbe, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            AufklappPfeil(offen)
        }
        if (offen) {
            inhalt()
            Spacer(Modifier.height(8.dp))
        }
        Trennlinie(Modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
