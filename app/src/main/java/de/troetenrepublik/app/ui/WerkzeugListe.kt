package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.i18n.S

// Werkzeugliste in Analytics: Gruppen mit Ueberschrift, je Werkzeug ein gezeichnetes Symbol (WerkzeugSymbole.kt),
// Bezeichnung und eine Unterzeile mit dem Inhalt; zwischen den Werkzeugen einer Gruppe eine Trennlinie.
// Die Seiten des Depot-Checks (Streuung, Umschichten, Crash-Test, Was waere, wenn) stehen als eigene Zeilen da und oeffnen
// den Depot-Check auf ihrer Seite.

// Schluessel fuer den Depotverlauf, der kein PortfolioTool ist.
internal const val WZ_VERLAUF = "VERLAUF"
// Zeile „Optionen“: fuehrt zur Derivate-Liste der Suche mit den Optionswerkzeugen.
// Optionen verkaufen, auffaellige Kontrakte und Optionsrechner
internal val OPTIONS_WERKZEUGE_NAMEN = listOf("STILLHALTER", "AUFFAELLIG", "OPTIONS")

// Ein onOpen mit diesem Praefix oeffnet eine Seite des Stapels statt eines Werts, etwa „seite:activity/handel“.
internal const val SEITEN_PRAEFIX = "seite:"
// Aktivitaet mit dem Filter Handel und Depotverlauf am Abschnitt der abgeschlossenen Verkaeufe.
internal const val AKTIVITAET_HANDEL = "activity/handel"
internal const val DEPOTVERLAUF_VERKAEUFE = "depotverlauf/verkaeufe"

// Gruppen und Reihenfolge: die einfachsten und meistgebrauchten zuerst.
internal val WERKZEUG_GRUPPEN: List<Pair<() -> String, List<String>>> = listOf(
    { S.wzGruppeDepot } to listOf(
        WZ_VERLAUF, PortfolioTool.CHECK.name, PortfolioTool.DIVERSIFICATION.name, PortfolioTool.REBALANCE.name, PortfolioTool.CRASH.name,
        PortfolioTool.SCENARIO.name, PortfolioTool.DIVIDENDS.name, PortfolioTool.PERFORMANCE.name, PortfolioTool.GRENZE.name, PortfolioTool.FAKTOREN.name,
    ),
    { S.wzGruppePlanen } to listOf(PortfolioTool.WEALTH.name, PortfolioTool.SPARPLAN.name, PortfolioTool.BACKTEST.name, PortfolioTool.WITHDRAWAL.name, PortfolioTool.LANGSICHT.name, PortfolioTool.MARKT.name, PortfolioTool.ROTATION.name, PortfolioTool.HEBEL.name),
    { S.wzGruppeFinden } to listOf(PortfolioTool.FINDER.name, PortfolioTool.STUFEN.name, PortfolioTool.HAEUSER.name) + OPTIONS_WERKZEUGE_NAMEN,
)

// Unterzeile eines Werkzeugs in der Liste; die Seiten des Depot-Checks haben hier eine eigene kurze Aussage statt des
// Untertitels ihrer Seite.
internal fun werkzeugUnterzeile(key: String): String = S.dp("wz.$key").ifBlank { S.ptSubtitle(key) }

// Zeile ohne Symbolfeld: Symbol in einem festen Platz, daneben Name und Unterzeile (beide brechen um), rechts der Pfeil.
@Composable
internal fun WerkzeugZeile(schluessel: String, titel: String, unterzeile: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { WerkzeugSymbol(schluessel) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.titleSmall)
            Text(unterzeile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        ChevronRight()
    }
}

// Kleine Gruppenueberschrift.
@Composable
internal fun WerkzeugGruppe(titel: String) {
    Text(
        titel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
    )
}

