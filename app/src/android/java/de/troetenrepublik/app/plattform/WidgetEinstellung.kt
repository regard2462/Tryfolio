package de.troetenrepublik.app.plattform

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.TroeteApp
import de.troetenrepublik.app.data.Appearance
import de.troetenrepublik.app.data.WidgetDaten
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ui.FloatingPill
import de.troetenrepublik.app.ui.NamenText
import de.troetenrepublik.app.ui.Radius
import de.troetenrepublik.app.ui.SectionHeader
import de.troetenrepublik.app.ui.ToggleRow
import de.troetenrepublik.app.ui.TroeteTheme

// Einstellung eines Widgets (seit 5.4 fuer alle ausser dem Schnellzugriff): Inhalt je nach Art, dazu Grund und
// Schrift. Jede Wahl gilt sofort; „Fertig“ zeichnet das Widget neu und bestaetigt es dem Startbildschirm.
// Bei gesperrter App zeigt die Seite nichts aus dem Depot, nur die Darstellung.
class WidgetEinstellung : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Ohne Bestaetigung legt der Startbildschirm ein neues Widget nicht an
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val state = (application as TroeteApp).container.state
        val art = Widgets.art(this, id)
        val c = this
        // Zurueck ohne „Fertig“: die Einstellungen gelten trotzdem, das Widget wird angelegt bzw. bleibt
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = fertig(id)
        })

        setContent {
            val settings by state.settingsFlow.collectAsStateWithLifecycle()
            val gesperrt by state.gesperrt.collectAsStateWithLifecycle()
            val dark = when (settings.appearance) {
                Appearance.DARK -> true
                Appearance.LIGHT -> false
                Appearance.SYSTEM -> isSystemInDarkTheme()
            }
            // Stand der Einstellungen; jede Aenderung wird sofort abgelegt
            var quelle by remember { mutableStateOf(Widgets.quelle(c, id)) }
            var einzel by remember { mutableStateOf(Widgets.einzel(c, id)) }
            var liste by remember { mutableStateOf(Widgets.liste(c, id)) }
            var name by remember { mutableStateOf(Widgets.name(c, id)) }
            var sortiert by remember { mutableStateOf(Widgets.sortiert(c, id)) }
            var betrag by remember { mutableStateOf(Widgets.betrag(c, id)) }
            var nurProzent by remember { mutableStateOf(Widgets.nurProzent(c, id)) }
            var zeitraum by remember { mutableStateOf(Widgets.zeitraum(c, id)) }
            var barometer by remember { mutableStateOf(Widgets.barometer(c, id)) }
            var grund by remember { mutableStateOf(Widgets.grund(c, id)) }
            var dunkel by remember { mutableStateOf(Widgets.dunkleSchrift(c, id)) }

            TroeteTheme(dark = dark, coloredChart = settings.coloredChart) {
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    Column(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(S.widgetEinstellung, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp))
                        if (gesperrt) Hinweis(S.widgetEinstellungGesperrt)
                        val auswahl = if (!gesperrt && (art == Widgets.ART_EINZEL || art == Widgets.ART_WERTE)) state.widgetEinzelAuswahl() else emptyList()

                        if (!gesperrt) when (art) {
                            Widgets.ART_DEPOT -> {
                                SectionHeader(S.widgetZeitraum)
                                Wahl(
                                    listOf(WidgetDaten.ZEIT_HEUTE to S.widgetZeitHeute, WidgetDaten.ZEIT_JAHR to S.widgetZeitJahr, WidgetDaten.ZEIT_KAUF to S.widgetZeitKauf),
                                    zeitraum,
                                ) { zeitraum = it; Widgets.zeitraumSetzen(c, id, it) }
                                ToggleRow(S.widgetNurProzent, nurProzent, S.widgetNurProzentHinweis) { nurProzent = it; Widgets.nurProzentSetzen(c, id, it) }
                            }
                            Widgets.ART_MOVER -> {
                                SectionHeader(S.widgetAnzeige)
                                ToggleRow(S.widgetNurProzent, nurProzent, S.widgetNurProzentHinweis) { nurProzent = it; Widgets.nurProzentSetzen(c, id, it) }
                            }
                            Widgets.ART_MAERKTE -> {
                                SectionHeader(S.widgetAnzeige)
                                ToggleRow(S.widgetBetrag, betrag, S.widgetBetragHinweis) { betrag = it; Widgets.betragSetzen(c, id, it) }
                            }
                            Widgets.ART_STIMMUNG -> {
                                SectionHeader(S.widgetBarometer)
                                Wahl(
                                    listOf(WidgetDaten.STIMMUNG_AKTIEN to S.sbKachel(WidgetDaten.STIMMUNG_AKTIEN), WidgetDaten.STIMMUNG_KRYPTO to S.sbKachel(WidgetDaten.STIMMUNG_KRYPTO)),
                                    barometer,
                                ) { barometer = it; Widgets.barometerSetzen(c, id, it) }
                            }
                            Widgets.ART_EINZEL -> {
                                SectionHeader(S.widgetEinzelWahl)
                                if (auswahl.isEmpty()) Hinweis(S.widgetEinstellungLeer)
                                Wahl(auswahl, einzel ?: state.widgetDaten()?.einzelStandard.orEmpty()) { einzel = it; Widgets.einzelSetzen(c, id, it) }
                                ToggleRow(S.widgetBetrag, betrag, S.widgetBetragHinweis) { betrag = it; Widgets.betragSetzen(c, id, it) }
                            }
                            Widgets.ART_WERTE -> {
                                SectionHeader(S.widgetQuelle)
                                Wahl(
                                    listOf(Widgets.QUELLE_WATCHLIST to S.widgetQuelleWatchlist, Widgets.QUELLE_DEPOT to S.widgetQuelleDepot, Widgets.QUELLE_LISTE to S.widgetQuelleListe),
                                    quelle,
                                ) { quelle = it; Widgets.quelleSetzen(c, id, it) }
                                if (quelle == Widgets.QUELLE_LISTE) {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it.take(40); Widgets.nameSetzen(c, id, name) },
                                        label = { Text(S.widgetListenName) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(Radius.field),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                    )
                                    Hinweis(S.widgetListeWahl)
                                    if (auswahl.isEmpty()) Hinweis(S.widgetEinstellungLeer)
                                    auswahl.forEach { (symbol, titel) ->
                                        val an = symbol in liste
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                liste = if (an) liste - symbol else liste + symbol
                                                Widgets.listeSetzen(c, id, liste)
                                            }.padding(horizontal = 12.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(checked = an, onCheckedChange = {
                                                liste = if (an) liste - symbol else liste + symbol
                                                Widgets.listeSetzen(c, id, liste)
                                            })
                                            NamenText(titel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                SectionHeader(S.widgetAnzeige)
                                ToggleRow(S.widgetSortiert, sortiert, null) { sortiert = it; Widgets.sortiertSetzen(c, id, it) }
                                ToggleRow(S.widgetBetrag, betrag, S.widgetBetragHinweis) { betrag = it; Widgets.betragSetzen(c, id, it) }
                            }
                        }

                        SectionHeader(S.widgetDarstellung)
                        Hinweis(S.widgetGrund)
                        Wahl(
                            listOf(Widgets.GRUND_VOLL to S.widgetGrundVoll, Widgets.GRUND_HALB to S.widgetGrundHalb, Widgets.GRUND_LEER to S.widgetGrundLeer),
                            grund,
                        ) { grund = it; Widgets.grundSetzen(c, id, it) }
                        Hinweis(S.widgetSchrift)
                        Wahl(listOf("hell" to S.widgetSchriftHell, "dunkel" to S.widgetSchriftDunkel), if (dunkel) "dunkel" else "hell") {
                            dunkel = it == "dunkel"; Widgets.dunkleSchriftSetzen(c, id, dunkel)
                        }

                        Spacer(Modifier.height(16.dp))
                        FloatingPill(label = S.done, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { fertig(id) }
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun Hinweis(text: String) {
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }

    // Eine Auswahl aus mehreren Moeglichkeiten (Schluessel, Text).
    @Composable
    private fun Wahl(optionen: List<Pair<String, String>>, gewaehlt: String, onWahl: (String) -> Unit) {
        optionen.forEach { (schluessel, text) ->
            Row(
                Modifier.fillMaxWidth().clickable { onWahl(schluessel) }.padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = schluessel == gewaehlt, onClick = { onWahl(schluessel) })
                NamenText(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // Widget neu zeichnen, Kurse und Verlauf nachladen und die Einstellung bestaetigen.
    private fun fertig(id: Int) {
        Widgets.zeichnen(this)
        runCatching { (application as TroeteApp).container.state.widgetsNachladen() }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
