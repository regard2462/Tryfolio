package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Marktkurven
import de.troetenrepublik.app.engine.VxKurve
import de.troetenrepublik.app.i18n.S

// Marktkurven (seit 5.91): Zinskurve der USA und des Euroraums, darunter die VIX-Kurve. Je Kurve eine Linie fuer
// heute, vor einem Monat und vor einem Jahr; kurze Laufzeiten ueber dem Zins fuer zehn Jahre tragen eine rote Marke.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarktkurvenSheet(state: AppState, onDismiss: () -> Unit) {
    // null: wird noch geladen
    var zins by remember { mutableStateOf<Marktkurven.Zinskurven?>(null) }
    var vix by remember { mutableStateOf<Pair<Marktkurven.Kurve, Marktkurven.Kurve?>?>(null) }
    var vixGeladen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { zins = runCatching { state.zinskurven() }.getOrNull() ?: Marktkurven.Zinskurven() }
    LaunchedEffect(Unit) { vix = runCatching { state.vixKurven() }.getOrNull(); vixGeladen = true }
    // 5.95: Terminkurve des VIX aus den Abrechnungspreisen der Cboe Futures Exchange
    var vxTermin by remember { mutableStateOf<VxKurve.Stand?>(null) }
    var vxTerminGeladen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vxTermin = state.vxKurve(); vxTerminGeladen = true }
    val uri = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text(S.mkTitel, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 20.dp))
            SectionHeader(S.mkZins)
            val z = zins
            when {
                z == null -> EmptyHint(S.trLaedt)
                z.usa == null && z.euro == null && z.deutschland == null -> EmptyHint(S.mkKeineDaten)
                else -> {
                    z.usa?.let { ZinsBlock(S.mkUsa, it, S.mkQuelleUsa) }
                    z.euro?.let { ZinsBlock(S.mkEuro, it, S.mkQuelleEuro) }
                    z.deutschland?.let { ZinsBlock(S.mkDeutschland, it, S.mkQuelleDeutschland) }
                }
            }
            AufklappText(S.mkZinsMehr, Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))

            SectionHeader(S.mkVix)
            val v = vix
            when {
                v == null && !vixGeladen -> EmptyHint(S.trLaedt)
                v == null -> EmptyHint(S.mkKeineDaten)
                else -> {
                    Marktkurven.vixForm(v.first)?.let { f ->
                        Text(
                            S.mkVixForm(f.name), style = MaterialTheme.typography.titleSmall,
                            color = if (f == Marktkurven.VixForm.BACKWARDATION) LocalSignal.current.warn else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                    KurvenChart(listOfNotNull(v.first, v.second), { Fmt.num(it, 1) }, emptyList())
                    Text(S.mkVixQuelle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            AufklappText(S.mkVixMehr, Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))

            // 5.95: Terminkurve des VIX; das Verhaeltnis der ersten beiden Monatskontrakte nennt Contango oder Angst
            SectionHeader(S.q5VxTitel)
            val vxT = vxTermin
            when {
                vxT == null && !vxTerminGeladen -> EmptyHint(S.trLaedt)
                vxT == null -> EmptyHint(S.mkKeineDaten)
                else -> {
                    val form = VxKurve.verhaeltnis(vxT)?.let { VxKurve.form(it) }
                    if (form != null) Text(
                        S.mkVixForm(form.name), style = MaterialTheme.typography.titleSmall,
                        color = if (form == Marktkurven.VixForm.BACKWARDATION) LocalSignal.current.warn else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    VxKurve.verhaeltnis(vxT)?.let { q ->
                        Text(S.q5VxVerhaeltnis(Fmt.num(q, 2)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    KurvenChart(listOf(VxKurve.kurve(vxT)), { Fmt.num(it, 2) }, emptyList())
                    Text(S.q5VxQuelle(Fmt.date(vxT.tag.toEpochDay() * 86_400_000L)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    QuellenZeile(S.q5VxLink, Modifier.padding(horizontal = 20.dp), onClick = { runCatching { uri.openUri(VxKurve.SEITE) } })
                }
            }
            AufklappText(S.q5VxMehr, Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
        }
    }
}

// Eine Zinskurve mit Form, Abstand zehn Jahre zu drei Monaten, invertierten Laufzeiten, Chart und Quelle.
@Composable
private fun ZinsBlock(titel: String, v: Marktkurven.Vergleich, quelle: String) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(titel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        val form = Marktkurven.form(v.heute)
        if (form != null) Text(
            S.mkForm(form.name), style = MaterialTheme.typography.titleSmall,
            color = if (form == Marktkurven.Form.INVERS) signal.warn else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        Marktkurven.abstand(v.heute)?.let { (kurz, wert) ->
            Text(S.mkAbstand(S.mkLaufzeit(kurz), (if (wert >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(wert), 2)),
                style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp))
        }
        val zehn = v.heute.wert(Marktkurven.ZEHN_JAHRE)
        val invertiert = Marktkurven.invertiert(v.heute)
        val texte = if (zehn == null) emptyList() else invertiert.map { p -> S.mkInvertiert(S.mkLaufzeit(p.tage), Fmt.pct(p.wert, 2), Fmt.pct(zehn, 2)) }
        if (texte.isNotEmpty()) Text(
            texte.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = signal.warn,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        KurvenChart(listOfNotNull(v.heute, v.monat, v.jahr), { Fmt.pct(it, 2) }, invertiert.map { it.tage }, texte)
        Text(quelle, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
}

// Kurven auf einer Achse der Laufzeiten in gleichen Abstaenden (kurze Laufzeiten blieben sonst gedraengt am Rand).
// Die erste Kurve ist die aktuelle; markiert traegt an diesen Laufzeiten eine rote Marke mit Text fuer das Fadenkreuz.
@Composable
private fun KurvenChart(kurven: List<Marktkurven.Kurve>, format: (Double) -> String, markiert: List<Int>, markenTexte: List<String> = emptyList()) {
    if (kurven.isEmpty()) return
    val achse = remember(kurven) { kurven.flatMap { k -> k.punkte.map { it.tage } }.distinct().sorted() }
    val farben = listOf(MaterialTheme.colorScheme.onSurface, VERGLEICHS_FARBEN[0], VERGLEICHS_FARBEN[1])
    val linien = kurven.mapIndexed { i, k ->
        Linie(
            S.mkStichtag(i, Fmt.date(k.tag * 86_400_000L)),
            k.punkte.map { p -> achse.indexOf(p.tage).toLong() to p.wert },
            farben.getOrElse(i) { Kategorie.farbe(i) },
        )
    }
    val marken = markiert.mapIndexed { i, tage -> Marke(achse.indexOf(tage).toLong(), markenTexte.getOrElse(i) { "" }, kauf = false) }
    VergleichsChart(
        linien = linien,
        format = format,
        hoehe = 190.dp,
        marken = marken,
        zeit = { t -> achse.getOrNull(t.toInt().coerceIn(0, achse.size - 1))?.let { S.mkLaufzeit(it) } ?: "" },
    )
}
