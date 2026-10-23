package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Appearance
import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.rememberOeffnen
import de.troetenrepublik.app.plattform.rememberSpeichern
import kotlinx.coroutines.launch

// Profil: Kopf mit Profilkreis und Vermoegen, darunter alle Bereiche als Zeilen.
// Unterseiten kommen als Blatt von unten. avatarShown meldet, ob die Startseite das Profil ganz
// erreicht hat; bis dahin zeigt sie den wandernden Profilkreis selbst (siehe HomePage).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: AppState,
    onOpen: (String) -> Unit,
    onNavigate: (String) -> Unit = {},
    onTransfer: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    avatarShown: () -> Boolean = { true },
) {
    val account by state.account.collectAsStateWithLifecycle()
    val valuation by state.valuation.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val error by state.market.error.collectAsStateWithLifecycle()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val scope = rememberCoroutineScope()

    var showReset by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var editRate by remember { mutableStateOf(false) }
    val hide = settings.hideBalances

    var exportDone by remember { mutableStateOf<String?>(null) }
    // Sichern und Einlesen ueber den Dateidialog der Plattform (Android: Dokumentauswahl, Desktop: Datei-Dialog)
    val exportieren = rememberSpeichern(inhalt = { state.exportJson() }) { text, ok ->
        exportDone = if (ok) S.exportDone(text.length / 1024 + 1) else S.exportFailed
    }
    val importieren = rememberPortfolioImport(state)
    // Selbst heruntergeladene Registerdaten (BSE, Consob, umgewandelte SEDI-Berichte) uebernehmen
    val registerImport = de.troetenrepublik.app.plattform.rememberOeffnenRoh { _, daten ->
        scope.launch { state.showToast(if (daten == null) S.imUnbekannt else state.registerImport(daten)) }
    }
    // Buchungen fuer Portfolio Performance, in der Sprache der App
    val ppSichern = rememberSpeichern(
        inhalt = { de.troetenrepublik.app.engine.PortfolioPerformance.csv(state.account.value, state::isinFor, settings.sprache == Lang.DE) },
        mime = "text/csv",
    ) { _, ok -> exportDone = if (ok) S.ppFertig(de.troetenrepublik.app.engine.PortfolioPerformance.anzahl(state.account.value)) else S.exportFailed }

    // Das Profil ist die dritte Seite der Startseite (siehe HomeScreen). Einen Zurueck-Pfeil gibt es nicht:
    // Ein Wisch von links nach rechts oder die Zurueck-Geste fuehrt dorthin zurueck, woher man kam.
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), state = listState) {
        item {
            Row(
                Modifier.padding(start = HomePage.AVATAR_START, end = 20.dp, top = HomePage.AVATAR_TOP_LARGE),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(
                    initial = settings.ownerName.take(1).ifBlank { "T" }.uppercase(),
                    modifier = Modifier.graphicsLayer { alpha = if (avatarShown()) 1f else 0f },
                ) { editName = true }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).clickable { editName = true }) {
                    Text(settings.ownerName.ifBlank { S.yourProfile }, style = MaterialTheme.typography.headlineSmall)
                    Text(S.practiceSince(Fmt.date(account.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp),
            ) {
                Column {
                    Text(S.totalWealth, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (hide) "•••••• " + Fmt.symbol else Fmt.money(valuation.total), style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.waehrungWechsel(state))
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(S.securities, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (hide) "•••• " + Fmt.symbol else Fmt.money(valuation.securities), style = MaterialTheme.typography.titleSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(S.cash, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (hide) "•••• " + Fmt.symbol else Fmt.money(valuation.cash), style = MaterialTheme.typography.titleSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(S.result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (hide) "••••" else Fmt.signedPct(valuation.profitPct),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (hide) MaterialTheme.colorScheme.onSurface else signal.forChange(valuation.profit),
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(S.depot)
            NavRow(S.activity, S.activitySubtitle) { onNavigate("activity") }
            NavRow(S.documents, S.documentsSubtitle) { onNavigate("documents") }
            NavRow(S.orders, S.nOpen(account.orders.count { it.status == de.troetenrepublik.app.data.OrderStatus.OPEN })) { onNavigate("orders") }
            NavRow(S.savingsPlans, S.planCount(account.plans.size)) { onNavigate("plans") }
            NavRow(S.priceAlerts, S.nActive(account.alerts.size)) { onNavigate("alerts") }
            NavRow(S.analytics, S.analyticsSubtitle) { onNavigate("analytics") }
            NavRow(S.statistics, S.statisticsProfileSubtitle) { onNavigate("statistics") }
            NavRow(S.importPosition, S.importPositionSubtitle) { onNavigate("import") }
        }

        item {
            SectionHeader(S.cash)
            NavRow(S.transfer, S.transferSubtitle, onClick = onTransfer)
            KeyValueRow(S.interestOnCash, "${Fmt.pct(settings.interestPct)} ${S.perAnnum}")
            ToggleRow(S.interestAuto, settings.interestAuto) { v ->
                scope.launch { state.settings.update { it.copy(interestAuto = v) }; if (v) state.refreshEcb(force = true) }
            }
            Text(
                if (settings.ecbBps > 0) S.ecbRate(Fmt.pct(settings.ecbBps / 100.0), isoDateText(settings.ecbSince)) else S.ecbNotLoaded,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!settings.interestAuto) {
                KeyValueRow(S.customRate, "${Fmt.pct(settings.interestBps / 100.0)} ${S.perAnnum}", onClick = { editRate = true })
                BpsRow(listOf(0, 100, 150, 200, 250, 300, 400), settings.interestBps,
                    labels = listOf(0, 100, 150, 200, 250, 300, 400).map { Fmt.num(it / 100.0, if (it % 100 == 0) 0 else 2) + " %" }) { bps ->
                    scope.launch { state.settings.update { it.copy(interestBps = bps) } }
                }
            }
            val geld = { v: Double -> if (hide) "•••• €" else Fmt.euro(v) }
            KeyValueRow(S.accruedThisMonth, geld(account.interestAccrued))
            KeyValueRow(S.deposited, geld(account.deposited))
            if (account.transferredIn > 0) KeyValueRow(S.transferredIn, geld(account.transferredIn))
            KeyValueRow(S.withdrawn, geld(account.withdrawn))
        }

        item {
            SectionHeader(S.taxes)
            NavRow(S.taxOverview, if (settings.allowance > 0) S.allowanceSet(Fmt.euro(settings.allowance, 0)) else S.allowanceNotSet) { onNavigate("tax") }
        }

        // Alltagseinstellungen zuerst: Mitteilungen, Sprache, Darstellung, Land und Waehrung. Fachliches wie
        // Spannen und Kursabruf steht unter Erweitert, Schluessel und Broker unter Daten.
        item {
            SectionHeader(S.profilEinstellungen)
            if (Plattform.mitteilungen) {
                ToggleRow(S.notificationsSwitch, settings.notifications) { v -> scope.launch { state.settings.update { it.copy(notifications = v) } } }
                // Wofuer die Mitteilungen sind, gleich unter dem Schalter
                AufklappText(if (Plattform.desktop) S.notificationsHintDesktop else S.notificationsHint, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                if (settings.notifications) {
                    Text(S.newsPageTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
                    val stufen = listOf(de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE, de.troetenrepublik.app.data.MITTEILUNG_ALLE, de.troetenrepublik.app.data.MITTEILUNG_AUS)
                    SegmentRow(stufen.map { S.newsNotifyKurz(it) }, selected = stufen.indexOf(settings.newsMitteilung).coerceAtLeast(0)) { i -> state.newsMitteilungSetzen(stufen[i]) }
                    NavRow(S.copyBellsTitle, S.copyBellsSubtitle(state.glocken(settings).size)) { onNavigate(CopyRouten.GLOCKEN) }
                }
                if (Plattform.aktualisierbar) ToggleRow(S.updSwitch, settings.updateHinweis, S.updSwitchHint) { v -> scope.launch { state.settings.update { it.copy(updateHinweis = v) } } }
            }
            // Tages- und Wochenrueckblick
            val rueckblicke by state.rueckblicke.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { state.rueckblickeLaden() }
            NavRow(S.rbTitel, S.rbProfil(rueckblicke?.tage?.size ?: 0, settings.tagesrueckblick)) { onNavigate("rueckblick") }
        }

        item {
            SectionHeader(S.language)
            SegmentRow(
                listOf(S.languageSystem, S.languageGerman, S.languageEnglish),
                selected = if (settings.languageAuto) 0 else if (settings.language == Lang.DE) 1 else 2,
            ) { i ->
                when (i) {
                    0 -> state.setLanguage(Lang.system(), auto = true)
                    1 -> state.setLanguage(Lang.DE)
                    else -> state.setLanguage(Lang.EN)
                }
            }
            if (settings.languageAuto) Text(
                S.languageSystemHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            SectionHeader(S.appearance)
            SegmentRow(
                listOf(S.appearanceSystem, S.appearanceLight, S.appearanceDark),
                selected = when (settings.appearance) { Appearance.SYSTEM -> 0; Appearance.LIGHT -> 1; Appearance.DARK -> 2 },
            ) { i -> state.setAppearance(listOf(Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK)[i]) }
            ToggleRow(S.coloredChart, settings.coloredChart) { v -> scope.launch { state.settings.update { it.copy(coloredChart = v) } } }
            // Kacheln der Startseite: Reihe zum Wischen oder untereinander, wie in der Kurzanleitung gewaehlt
            Text(S.kachelnTitel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
            SegmentRow(listOf(S.kachelnWischen, S.kachelnRaster), selected = if (settings.kachelRaster) 1 else 0) { i -> scope.launch { state.settings.update { it.copy(kachelRaster = i == 1) } } }
        }

        // Land, Waehrung und Handelszeiten; welche Waehrung gerade angezeigt wird
        item { RegionAbschnitt(state) }

        item {
            SectionHeader(S.profilSicherheit)
            val sperrArt by state.sperrArt.collectAsStateWithLifecycle()
            NavRow(S.sperreTitel, S.sperreStatus(sperrArt)) { onNavigate("sperre") }
            ToggleRow(S.hideBalances, settings.hideBalances) { v -> scope.launch { state.settings.update { it.copy(hideBalances = v) } } }
            // Schuetteln braucht den Bewegungssensor des Telefons
            if (Plattform.schuetteln) ToggleRow(S.shakeToHide, settings.shakeToHide) { v -> scope.launch { state.settings.update { it.copy(shakeToHide = v) } } }
            if (de.troetenrepublik.app.plattform.bildschutzMoeglich) NavRow(S.screenshotsTitel, S.screenshotsOption(settings.screenshots)) { onNavigate("screenshots") }
        }

        item {
            SectionHeader(S.profilDaten)
            NavRow(S.exportPortfolio, S.exportSubtitle) { exportieren(Backup.fileName()) }
            NavRow(S.importPortfolio, S.importSubtitle) { importieren() }
            NavRow(S.imTitel, S.imUntertitel) { registerImport() }
            val ppZeilen = remember(account.transactions) { de.troetenrepublik.app.engine.PortfolioPerformance.anzahl(account) }
            NavRow(S.ppExport, S.ppExportSub(ppZeilen)) {
                ppSichern("tryfolio-portfolio-performance-" + java.time.LocalDate.now() + ".csv")
            }
            AufklappText(S.ppHinweis, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            val ppUri = androidx.compose.ui.platform.LocalUriHandler.current
            Text(
                S.ppSeite, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                modifier = Modifier.padding(horizontal = 12.dp).clickable { runCatching { ppUri.openUri("https://www.portfolio-performance.info/") } }.padding(8.dp),
            )
            AufklappText(S.backupHint, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            // Eigenes Konto bei einem Broker
            val bz by state.brokerZustand.collectAsStateWithLifecycle()
            NavRow(S.brTitel, S.brProfil(bz.art?.anzeige, bz.live)) { onNavigate("broker") }
            if (Plattform.kursabruf) NavRow(S.zgTitle, S.zgProfileSubtitle(listOf(settings.keyGuardian, settings.keyFinnhub, settings.keyAlphaVantage, settings.keyDeepl, settings.keyRedditId, settings.keyFec, settings.keyUspto, settings.polyAdresse).count { it.isNotBlank() })) {
                onNavigate("zugaenge")
            }
            if (de.troetenrepublik.app.plattform.Widgets.verfuegbar) NavRow(S.widgetsTitel, S.widgetsUntertitel) { onNavigate("widgets") }
        }

        item {
            SectionHeader(S.profilErweitert)
            KeyValueRow(S.spreadStocks, "${settings.spreadStockBps} bp (${Fmt.pct(settings.spreadStockBps / 100.0)})")
            BpsRow(listOf(0, 10, 20, 50, 100), settings.spreadStockBps) { bp -> scope.launch { state.settings.update { it.copy(spreadStockBps = bp) } } }
            KeyValueRow(S.spreadDerivatives, "${settings.spreadDerivativeBps} bp (${Fmt.pct(settings.spreadDerivativeBps / 100.0)})")
            BpsRow(listOf(0, 25, 50, 100, 200), settings.spreadDerivativeBps) { bp -> scope.launch { state.settings.update { it.copy(spreadDerivativeBps = bp) } } }
            AufklappText(S.spreadHint, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            KeyValueRow(S.fee, Fmt.euro(Trading.FEE))
            if (Plattform.kursabruf) {
                KeyValueRow(S.refreshEvery, S.everySeconds(settings.refreshSeconds))
                BpsRow(listOf(5, 15, 30, 60), settings.refreshSeconds, suffix = " s") { s -> scope.launch { state.settings.update { it.copy(refreshSeconds = s) } } }
                KeyValueRow(S.source, "Yahoo Finance · Wikipedia · " + S.ecbAbbrev + " · SEC · ApeWisdom")
                KeyValueRow(
                    S.statusLabel,
                    if (error != null) S.statusLimited else if (quotes.isEmpty()) S.statusLoading else S.statusConnected,
                    valueColor = if (error == null && quotes.isNotEmpty()) signal.up else MaterialTheme.colorScheme.error,
                )
                ActionLineWide(S.refreshNow) { scope.launch { state.market.refresh() } }
            }
        }

        item {
            SectionHeader(S.aboutTitle)
            AufklappText(S.aboutText, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            NavRow(S.guideTitle, S.guideSubtitle) { onNavigate("guide") }
            KeyValueRow(S.version, de.troetenrepublik.app.market.Aktualisierung.FASSUNG)
            KaffeeZeile()
            val neu = settings.updateFassung
            if (Plattform.aktualisierbar && settings.updateLink.isNotBlank() &&
                de.troetenrepublik.app.market.Aktualisierung.neuer(neu, de.troetenrepublik.app.market.Aktualisierung.FASSUNG)
            ) {
                val uri = androidx.compose.ui.platform.LocalUriHandler.current
                NavRow(S.updRowTitle(neu), S.updRowSubtitle) { runCatching { uri.openUri(settings.updateLink) } }
            }
            ActionLineWide(S.resetDepot, danger = true) { showReset = true }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.resetTitle) },
            text = { Text(S.resetText(Fmt.euro(Trading.START_CASH, 0))) },
            confirmButton = { TextButton(onClick = { state.reset(); showReset = false }) { Text(S.reset) } },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text(S.cancel) } },
        )
    }

    if (editName) {
        var text by remember { mutableStateOf(settings.ownerName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.personalData) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, label = { Text(S.firstName) }, singleLine = true, shape = RoundedCornerShape(Radius.field)) },
            confirmButton = { TextButton(onClick = { scope.launch { state.settings.update { it.copy(ownerName = text.trim()) } }; editName = false }) { Text(S.save) } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text(S.cancel) } },
        )
    }


    if (editRate) {
        var text by remember { mutableStateOf(Fmt.num(settings.interestBps / 100.0, 2)) }
        val parsed = text.replace(',', '.').toDoubleOrNull()
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


    exportDone?.let { msg ->
        AlertDialog(
            onDismissRequest = { exportDone = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(Radius.card),
            title = { Text(S.exportTitle) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { exportDone = null }) { Text(S.ok) } },
        )
    }

}

// ISO-Datum der EZB-Reihe ("2026-09-16") in der Schreibweise der Sprache; unbekannte Formate bleiben stehen.
private fun isoDateText(iso: String): String = runCatching {
    // Mittag statt Mitternacht, damit die Zeitzone des Geraets den Kalendertag nicht verschiebt
    java.time.LocalDate.parse(iso).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()?.let { Fmt.date(it) } ?: iso

// Seite zum Unterstuetzen der App bei Buy Me a Coffee.
const val KAFFEE_LINK = "https://buymeacoffee.com/regard2462"

// Zeile mit einer gezeichneten Kaffeetasse auf gelbem Grund; oeffnet die Seite im Browser.
@Composable
private fun KaffeeZeile() {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(KAFFEE_LINK) } }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(36.dp)) {
            val dunkel = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
            drawCircle(androidx.compose.ui.graphics.Color(0xFFFFDD00))
            val w = size.width
            val strich = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.07f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            // Tasse
            drawRoundRect(dunkel, topLeft = androidx.compose.ui.geometry.Offset(w * 0.26f, w * 0.42f),
                size = androidx.compose.ui.geometry.Size(w * 0.38f, w * 0.32f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f))
            // Henkel
            drawArc(dunkel, startAngle = -90f, sweepAngle = 180f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.56f, w * 0.47f), size = androidx.compose.ui.geometry.Size(w * 0.18f, w * 0.18f), style = strich)
            // Dampf
            drawLine(dunkel, androidx.compose.ui.geometry.Offset(w * 0.38f, w * 0.24f), androidx.compose.ui.geometry.Offset(w * 0.38f, w * 0.34f), strokeWidth = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(dunkel, androidx.compose.ui.geometry.Offset(w * 0.50f, w * 0.22f), androidx.compose.ui.geometry.Offset(w * 0.50f, w * 0.34f), strokeWidth = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(S.kaffeeTitel, style = MaterialTheme.typography.titleSmall)
            Text(S.kaffeeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
