package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.PortfolioPerformance
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.rememberOeffnenRoh
import de.troetenrepublik.app.plattform.rememberSpeichern
import kotlinx.coroutines.launch

// Route der Unterseite „Erweitert“ im Profil.
const val PROFIL_ERWEITERT = "profil-erweitert"

// Dieselbe Seite, aufgeschlagen beim Abschnitt „Zinsen auf Cash“ (Zeile im Profil).
const val PROFIL_ERWEITERT_ZINS = "profil-erweitert/zins"

// ISO-Datum der EZB-Reihe ("2026-09-16") in der Schreibweise der Sprache; unbekannte Formate bleiben stehen.
private fun isoDateText(iso: String): String = runCatching {
    // Mittag statt Mitternacht, damit die Zeitzone des Geraets den Kalendertag nicht verschiebt
    java.time.LocalDate.parse(iso).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()?.let { Fmt.date(it) } ?: iso

// Waehlbare eigene Zinssaetze in Basispunkten.
private val ZINS_BPS = listOf(0, 100, 150, 200, 250, 300, 400)

// Unterseite „Erweitert“ des Profils: Handelskosten, Kursabruf, eigener Zinssatz, Registerdaten und Export fuer
// Portfolio Performance. Selten gebraucht, darum nicht auf der Profilseite selbst; die Einstellungen sind dieselben.
@Composable
fun ProfilErweitertScreen(state: AppState, onBack: () -> Unit, zuZins: Boolean = false, oeffnen: (String) -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val error by state.market.error.collectAsStateWithLifecycle()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val account by state.account.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val scope = rememberCoroutineScope()
    var editRate by remember { mutableStateOf(false) }
    var meldung by remember { mutableStateOf<String?>(null) }

    // Selbst heruntergeladene Registerdaten (BSE, Consob, umgewandelte SEDI-Berichte) uebernehmen
    val registerImport = rememberOeffnenRoh { _, daten ->
        scope.launch { state.showToast(if (daten == null) S.imUnbekannt else state.registerImport(daten)) }
    }
    // Buchungen fuer Portfolio Performance, in der Sprache der App
    val ppSichern = rememberSpeichern(
        inhalt = { PortfolioPerformance.csv(state.account.value, state::isinFor, settings.sprache == Lang.DE) },
        mime = "text/csv",
    ) { _, ok -> meldung = if (ok) S.ppFertig(PortfolioPerformance.anzahl(state.account.value)) else S.exportFailed }

    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberScrollState()
    val zinsStelle = remember { BringIntoViewRequester() }
    LaunchedEffect(zuZins) { if (zuZins) { kotlinx.coroutines.delay(300); runCatching { zinsStelle.bringIntoView() } } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { liste.value == 0 }.verticalScroll(liste)) {
        ScreenHeader(S.profilErweitert, null, onBack)

        // Spannen und Fremdkosten beim Handeln
        SectionHeader(S.erwHandel)
        KeyValueRow(S.spreadStocks, "${settings.spreadStockBps} bp (${Fmt.pct(settings.spreadStockBps / 100.0)})")
        BpsRow(listOf(0, 10, 20, 50, 100), settings.spreadStockBps) { bp -> scope.launch { state.settings.update { it.copy(spreadStockBps = bp) } } }
        KeyValueRow(S.spreadDerivatives, "${settings.spreadDerivativeBps} bp (${Fmt.pct(settings.spreadDerivativeBps / 100.0)})")
        BpsRow(listOf(0, 25, 50, 100, 200), settings.spreadDerivativeBps) { bp -> scope.launch { state.settings.update { it.copy(spreadDerivativeBps = bp) } } }
        AufklappText(S.spreadHint, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        KeyValueRow(S.fee, Fmt.euro(Trading.FEE))

        // Kursabruf: Takt, Quellen, Zustand
        if (Plattform.kursabruf) {
            SectionHeader(S.erwKurse)
            KeyValueRow(S.refreshEvery, S.everySeconds(settings.refreshSeconds))
            BpsRow(listOf(5, 15, 30, 60), settings.refreshSeconds, suffix = " s") { s -> scope.launch { state.settings.update { it.copy(refreshSeconds = s) } } }
            KeyValueRow(
                S.status,
                if (error != null) S.statusLimited else if (quotes.isEmpty()) S.toolLoading else S.statusConnected,
                valueColor = if (error == null && quotes.isNotEmpty()) signal.up else MaterialTheme.colorScheme.error,
            )
            ActionLineWide(S.refreshNow) { scope.launch { state.market.refresh() } }
        }

        // Zinsen auf Cash: geltender Satz, Kopplung an den Einlagesatz der EZB, eigener Satz. Eine Wahl loest den Zins
        // vom Leitzins der EZB. Im Profil steht nur eine Zeile mit dem geltenden Satz.
        Column(Modifier.bringIntoViewRequester(zinsStelle)) {
            SectionHeader(S.interestOnCash)
            KeyValueRow(S.wlZinssatz, "${Fmt.pct(settings.interestPct)} ${S.perAnnum}")
            ToggleRow(S.interestAuto, settings.interestAuto) { v ->
                scope.launch { state.settings.update { it.copy(interestAuto = v) }; if (v) state.refreshEcb(force = true) }
            }
            Text(
                if (settings.ecbBps > 0) S.ecbRate(Fmt.pct(settings.ecbBps / 100.0), isoDateText(settings.ecbSince)) else S.ecbNotLoaded,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.interestAuto) AufklappText(S.erwZinsAuto, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            // Bei aktiver EZB-Kopplung gilt der eigene Satz nicht: Strich statt eines Werts, der wie ein zweiter Zins wirkt
            KeyValueRow(S.customRate, if (settings.interestAuto) "–" else "${Fmt.pct(settings.interestBps / 100.0)} ${S.perAnnum}", onClick = { editRate = true })
            BpsRow(ZINS_BPS, if (settings.interestAuto) -1 else settings.interestBps,
                labels = ZINS_BPS.map { Fmt.num(it / 100.0, if (it % 100 == 0) 0 else 2) + " %" }) { bps ->
                scope.launch { state.settings.update { it.copy(interestBps = bps, interestAuto = false) } }
            }
            KeyValueRow(S.accruedThisMonth, if (settings.hideBalances) "•••• €" else Fmt.euro(account.interestAccrued))
        }

        // Selbst geholte Registerdaten und wo es sie gibt
        SectionHeader(S.erwRegister)
        NavRow(S.imTitel, S.imUntertitel) { registerImport() }
        NavRow(S.saTitel, S.saUntertitel) { oeffnen("selbstabruf") }
        NavRow(S.dqTitel) { oeffnen(DATENQUELLEN) }

        SectionHeader(S.erwPp)
        val ppZeilen = remember(account.transactions) { PortfolioPerformance.anzahl(account) }
        NavRow(S.ppExport, S.ppExportSub(ppZeilen)) {
            ppSichern("tryfolio-portfolio-performance-" + java.time.LocalDate.now() + ".csv")
        }
        AufklappText(S.ppHinweis, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        val uri = appLinks()
        Text(
            S.ppSeite, style = MaterialTheme.typography.labelMedium, color = signal.link,
            modifier = Modifier.padding(horizontal = 12.dp).textKlick(8.dp) { runCatching { uri.openUri("https://www.portfolio-performance.info/") } }.padding(8.dp),
        )
        Spacer(Modifier.height(40.dp))
    }

    if (editRate) {
        var text by remember { mutableStateOf(Fmt.num(settings.interestBps / 100.0, 2)) }
        val parsed = betragLesen(text)
        val valid = parsed != null && parsed >= 0 && parsed <= 20
        AlertDialog(
            onDismissRequest = { editRate = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.customRate) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                    label = { Text(S.enterRate) }, singleLine = true, shape = RoundedCornerShape(Radius.field), isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), suffix = { Text("%") },
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    scope.launch { state.settings.update { it.copy(interestBps = kotlin.math.round(parsed!! * 100).toInt(), interestAuto = false) } }
                    editRate = false
                }) { Text(S.save) }
            },
            dismissButton = { TextButton(onClick = { editRate = false }) { Text(S.cancel) } },
        )
    }

    meldung?.let { msg ->
        AlertDialog(
            onDismissRequest = { meldung = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.exportTitle) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { meldung = null }) { Text(S.ok) } },
        )
    }
}
