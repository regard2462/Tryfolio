package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Blasen
import de.troetenrepublik.app.engine.Ersatzquellen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import java.time.LocalDate
import kotlin.math.abs

// Ersatzquellen in der Oberflaeche (seit 5.92): ein eigener Abschnitt im Reiter „Ausblick“ mit Lieferketten, Fracht,
// Bahn und Halbleitern und die Texte der Messgroessen im Blasen-Detektor. Der Aufbau folgt den fruehen Signalen in
// ui/FruehSignale.kt.

@Composable
internal fun ErsatzSignaleAbschnitt(state: AppState) {
    var signale by remember { mutableStateOf<Map<String, Ersatzquellen.Signal>?>(null) }
    LaunchedEffect(Unit) { signale = state.ersatzSignale() }
    // Containerfracht, US-Bahnen und Halbleiterumsaetze aus den weiteren Quellen (5.92, ui/WeitereQuellen.kt)
    var weitere by remember { mutableStateOf<AppState.WeitereSignale?>(null) }
    LaunchedEffect(Unit) { weitere = runCatching { state.weitereSignale() }.getOrNull() }
    // Im Preis? (5.94) erst nach den Signalen, die Schaetzung laedt lange Reihen
    var preis by remember { mutableStateOf<Map<String, de.troetenrepublik.app.engine.Fruehindikatoren.ImPreis>>(emptyMap()) }
    LaunchedEffect(signale != null) {
        if (signale != null) preis = runCatching { state.fruehImPreis(de.troetenrepublik.app.engine.Fruehindikatoren.ERSATZ_MARKT.keys) }.getOrNull().orEmpty()
    }
    val s = signale.orEmpty()
    val w = weitere?.takeIf { it.wci != null || it.bahn != null || it.halbleiter != null }
    // Ohne Daten bleibt der Abschnitt weg, die fruehen Signale darueber zeigen schon „wird geladen“
    if (s.isEmpty() && w == null) return
    Column(Modifier.fillMaxWidth()) {
        MarktInfoKopf(S.ersTitel, S.ersInfo)
        var offen by remember { mutableStateOf<String?>(null) }
        for (k in Ersatzquellen.SIGNALE) {
            val x = s[k] ?: continue
            ErsatzZeile(x, offen == k) { offen = if (offen == k) null else k }
            preis[k]?.let { ImPreisZeile(it, null, state = state) }
        }
        if (w != null) WeitereSignalZeilen(w)
        if (s.isNotEmpty()) QuellenZeile(S.ersQuellen)
        FrachtBrowserLinks()
    }
}

// Eine Zeile: Ampel, Name mit Wert in der Titelzeile (GSCPI bzw. Veraenderung zum Vorjahresmonat), Satz. Aufgeklappt:
// Stand, Vormonat, Erklaerung und Quelle.
@Composable
private fun ErsatzZeile(x: Ersatzquellen.Signal, auf: Boolean, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val uri = LocalUriHandler.current
    val gscpi = x.schluessel == Ersatzquellen.LIEFERKETTEN
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            AmpelPunkt(fwAmpelZeichen(x.ampel), fwAmpelFarbe(x.ampel))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NameUndWert(S.ersName(x.schluessel), if (gscpi) ersVorzeichen(x.wert, 2) else x.jahr?.let { Fmt.signedPct(it, 1) } ?: "")
                Text(S.ersSatz(x.schluessel, x.ampel.ordinal), style = MaterialTheme.typography.bodySmall, color = grau)
                TendenzZeile(de.troetenrepublik.app.engine.Tendenz.ersatz(x.schluessel, x.ampel), Modifier.padding(top = 2.dp))
            }
        }
        if (auf) {
            val zeile = listOfNotNull(
                if (gscpi) x.jahr?.let { S.ersVorjahr(ersVorzeichen(it, 2)) } else null,
                S.ersStand(ersMonat(x.stand)),
            )
            PunktText(zeile.joinToString(" · "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 28.dp, top = 6.dp), color = androidx.compose.ui.graphics.Color.Unspecified)
            Text(S.ersErklaerung(x.schluessel), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(start = 28.dp, top = 4.dp))
            QuellenZeile(S.ersQuelle(x.schluessel, java.time.LocalDate.ofEpochDay(x.stand).year), Modifier.padding(start = 8.dp), onClick = { runCatching { uri.openUri(Ersatzquellen.seite(x.schluessel)) } })
        }
    }
}

// Zahl mit Vorzeichen (echtes Minuszeichen).
private fun ersVorzeichen(v: Double, stellen: Int): String =
    (if (v > 0) "+" else if (v < 0) "−" else "±") + Fmt.num(abs(v), stellen)

// Monat und Jahr eines Tages seit 1970, etwa „September 2026“.
internal fun ersMonat(tag: Long): String =
    LocalDate.ofEpochDay(tag).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", I18n.locale))

// Texte der Messgroessen im Blasen-Detektor: CAPE und Margin-Kredite aus ersBl*, die uebrigen aus bl*.
internal fun blNameAlle(k: String): String = S.ersBlName(k) ?: S.blName(k)

internal fun blLageAlle(k: String, ampel: Int): String = S.ersBlLage(k, ampel) ?: S.blLage(k, ampel)

internal fun blErklaerungAlle(i: Blasen.Indikator, bipJahr: Int?): String {
    val zusatz = when (i.schluessel) {
        Blasen.CAPE -> i.zusatz?.let { Fmt.num(it, 0) + " %" }
        Blasen.MARGIN -> i.zusatz?.let { S.ersMrdDollar(Fmt.num(it, 0)) }
        else -> null
    }
    val stand = i.stand?.let { t ->
        val d = LocalDate.ofEpochDay(t)
        if (i.schluessel == Blasen.MARGIN || i.schluessel == Blasen.BUFFETT) S.ersQuartal((d.monthValue - 1) / 3 + 1, d.year) else ersMonat(t)
    }
    return S.ersBlErklaerung(i.schluessel, zusatz, stand)
        ?: S.blErklaerung(i.schluessel, bipJahr, stand.takeIf { i.schluessel == Blasen.BUFFETT })
}
