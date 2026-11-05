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
    val signal = LocalSignal.current
    val scope = rememberCoroutineScope()

    var showReset by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    val hide = settings.hideBalances

    var exportDone by remember { mutableStateOf<String?>(null) }
    // Sichern und Einlesen ueber den Dateidialog der Plattform (Android: Dokumentauswahl, Desktop: Datei-Dialog)
    val exportieren = rememberSpeichern(inhalt = { state.exportJson() }) { text, ok ->
        exportDone = if (ok) S.exportDone(text.length / 1024 + 1) else S.exportFailed
    }
    val importieren = rememberPortfolioImport(state)

    // Das Profil ist die dritte Seite der Startseite (siehe HomeScreen). Ein Wisch von links nach rechts oder die
    // Zurueck-Geste (Desktop: Escape) fuehren dorthin zurueck, woher man kam.
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
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
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

        // Nur, was es auf der Startseite nicht schon gibt: Orders, Sparplaene, Analytics, Copytrading und Importieren
        // sind Kacheln, Aktivitaet, Statistik und Ueberweisen stehen im Cash-Reiter (seit 5.92).
        item {
            SectionHeader(S.portfolio)
            NavRow(S.documents, S.documentsSubtitle) { onNavigate("documents") }
            NavRow(S.priceAlerts, S.nActive(account.alerts.size)) { onNavigate("alerts") }
            NavRow(S.taxOverview, if (settings.allowance > 0) S.allowanceSet(Fmt.euro(settings.allowance, 0)) else S.allowanceNotSet) { onNavigate("tax") }
            if (wiederanlageMoeglich(state)) {
                ToggleRow(S.wlGlobal, settings.divWiederanlage, S.wlGlobalHinweis) { v -> scope.launch { state.settings.update { it.copy(divWiederanlage = v) } } }
            }
        }

        item {
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
            // Den eigenen Zinssatz waehlt man unter Erweitert
            if (!settings.interestAuto) {
                NavRow(S.customRate, "${Fmt.pct(settings.interestBps / 100.0)} ${S.perAnnum}") { onNavigate(PROFIL_ERWEITERT) }
            }
            val geld = { v: Double -> if (hide) "•••• €" else Fmt.euro(v) }
            KeyValueRow(S.accruedThisMonth, geld(account.interestAccrued))
            KeyValueRow(S.deposited, geld(account.deposited))
            if (account.transferredIn > 0) KeyValueRow(S.transferredIn, geld(account.transferredIn))
            KeyValueRow(S.withdrawn, geld(account.withdrawn))
        }

        // Alltagseinstellungen zuerst: Mitteilungen, Sprache, Darstellung, Land und Waehrung. Fachliches wie
        // Spannen und Kursabruf steht auf der Unterseite Erweitert, Schluessel unter Daten. Die Stufe
        // der News-Mitteilungen steht im Quellenblatt der News, die Glocken im Copytrading.
        item {
            SectionHeader(S.profilEinstellungen)
            // Mitteilungen nach Arten auf eigener Seite (0.68)
            if (Plattform.mitteilungen) NavRow(
                S.notifications,
                S.mtProfil(settings.notifications, de.troetenrepublik.app.engine.Mitteilungsarten.aktiv(settings, Plattform.aktualisierbar), de.troetenrepublik.app.engine.Mitteilungsarten.arten(Plattform.aktualisierbar).size),
            ) { onNavigate(MITTEILUNGEN) }
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
            AufklappText(S.backupHint, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            if (Plattform.kursabruf) NavRow(S.zgTitle, S.zgProfileSubtitle(listOf(settings.keyGuardian, settings.keyFinnhub, settings.keyAlphaVantage, settings.keyEodhd, settings.keyDeepl, settings.keyRedditId, settings.keyFec, settings.keyUspto, settings.keyAgsi, settings.polyAdresse, settings.keyCourtListener, settings.bskyAppPasswort, settings.keyFinraId).count { it.isNotBlank() })) {
                onNavigate("zugaenge")
            }
            if (de.troetenrepublik.app.plattform.Widgets.verfuegbar) NavRow(S.widgetsTitel, S.widgetsUntertitel) { onNavigate("widgets") }
            // Spannen, Kursabruf, eigener Zinssatz, Registerdaten und Export fuer Portfolio Performance
            NavRow(S.profilErweitert, S.erwUntertitel) { onNavigate(PROFIL_ERWEITERT) }
        }

        item {
            SectionHeader(S.aboutTitle)
            AufklappText(S.aboutText, Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            NavRow(S.guideTitle, S.guideSubtitle) { onNavigate("guide") }
            NavRow(S.lizenzenTitel, S.lizenzenUntertitel) { onNavigate(LIZENZEN) }
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
