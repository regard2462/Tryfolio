package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Ersatzquellen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import java.time.LocalDate
import kotlin.math.abs

// Ersatzquellen in der Oberflaeche (seit 5.92): ein eigener Abschnitt im Reiter „Ausblick“ mit Lieferketten, Fracht,
// Bahn und Halbleitern und die Texte der Messgroessen im Blasen-Detektor. Der Aufbau folgt den fruehen Signalen in
// ui/FruehSignale.kt.

@Composable
// onEngpass: Tipp auf einen Engpass der Uebersicht (Kennung); vorerst ohne Wirkung, der Aufrufer verbindet das Engpass-Blatt.
internal fun ErsatzSignaleAbschnitt(state: AppState, oeffnen: (String) -> Unit = {}, onEngpass: (String) -> Unit = {}) {
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
        EngpassUebersicht(state, oeffnen, onEngpass)
        FrachtBrowserLinks()
    }
}

// Eine Zeile: Ampel, Name mit Wert in der Titelzeile (GSCPI bzw. Veraenderung zum Vorjahresmonat), Satz. Aufgeklappt:
// Stand, Vormonat und Erklaerung.
@Composable
private fun ErsatzZeile(x: Ersatzquellen.Signal, auf: Boolean, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val gscpi = x.schluessel == Ersatzquellen.LIEFERKETTEN
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            AmpelPunkt(fwAmpelZeichen(x.ampel), fwAmpelFarbe(x.ampel))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NameUndWert(S.ersName(x.schluessel), if (gscpi) ersVorzeichen(x.wert, 2) else x.jahr?.let { Fmt.signedPct(it, 1) } ?: "")
                Text(S.ersSatz(x.schluessel, x.ampel.ordinal), style = MaterialTheme.typography.bodySmall, color = grau)
                TendenzZeile(de.troetenrepublik.app.engine.Tendenz.ersatz(x.schluessel, x.ampel), Modifier.padding(top = 2.dp), ausblickStaerke(x.schluessel))
            }
        }
        if (auf) {
            val zeile = listOfNotNull(
                if (gscpi) x.jahr?.let { S.ersVorjahr(ersVorzeichen(it, 2)) } else null,
                S.ersStand(ersMonat(x.stand)),
            )
            PunktText(zeile.joinToString(" · "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 28.dp, top = 6.dp), color = androidx.compose.ui.graphics.Color.Unspecified)
            StichpunktText(S.ersErklaerung(x.schluessel), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(start = 28.dp, top = 4.dp))
        }
    }
}

// Zahl mit Vorzeichen (echtes Minuszeichen).
internal fun ersVorzeichen(v: Double, stellen: Int): String =
    (if (v > 0) "+" else if (v < 0) "−" else "±") + Fmt.num(abs(v), stellen)

// Monat und Jahr eines Tages seit 1970, etwa „September 2026“.
internal fun ersMonat(tag: Long): String =
    LocalDate.ofEpochDay(tag).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", I18n.locale))

// Texte der Messgroessen im Blasen-Detektor: CAPE und Margin-Kredite aus ersBl*, die uebrigen aus bl*.
internal fun blNameAlle(k: String): String = S.ersBlName(k) ?: S.blName(k)

internal fun blLageAlle(k: String, ampel: Int): String = S.ersBlLage(k, ampel) ?: S.blLage(k, ampel)

// [Engpaesse] Kleine Uebersicht im Ausblick: die gestoerten Engpaesse des Seehandels (gleiche Regel wie in der Lage) mit der
// Veraenderung der Durchfahrten gegen das Vorjahr, je als Balken von der Mitte aus in den Farben der Globusskala. Ist
// keiner gestoert, steht das in einer Zeile. Ein Tipp auf eine Zeile meldet die Kennung an onEngpass. Ohne Stand (noch
// nie geladen und ohne Netz) fehlt der Abschnitt.
@Composable
private fun EngpassUebersicht(state: AppState, oeffnen: (String) -> Unit, onEngpass: (String) -> Unit) {
    var stand by remember { mutableStateOf<de.troetenrepublik.app.engine.Engpaesse.Uebersicht?>(null) }
    LaunchedEffect(Unit) {
        stand = state.engpassGespeichert()
        stand = runCatching { state.engpassUebersicht() }.getOrNull() ?: stand
    }
    val u = stand ?: return
    val liste = remember(u) { de.troetenrepublik.app.engine.Engpaesse.gestoerte(u) }
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth()) {
        Zwischenkopf(S.engUebersichtTitel)
        Text(
            S.engUebersichtUnter(Fmt.date(u.tag * 86_400_000L + 43_200_000L)), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (liste.isEmpty()) Text(
            S.ml("vw.eng.keiner"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        for ((s, lage) in liste) {
            val abw = lage.abw ?: continue
            Column(Modifier.fillMaxWidth().clickable { onEngpass(s.id) }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                NameUndWert(S.engName(s.id), Fmt.signedPct(abw, 0))
                EngpassBalken(abw, androidx.compose.ui.graphics.Color(de.troetenrepublik.app.engine.GlobusFarbe.engpass(abw, signal.up.toArgb(), signal.down.toArgb())), Modifier.padding(top = 6.dp))
            }
        }
        Text(
            S.engAlleGlobus, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.fillMaxWidth().clickable { oeffnen("globus/engpaesse") }.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

// Veraenderung als Balken von der Mitte aus, voll bei -100 % bzw. +100 %, damit -54 % und -100 % verschieden lang sind.
// Rund nur am aeusseren Ende, an der Mitte gerade.
@Composable
internal fun EngpassBalken(abw: Double, farbe: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val mitteStrich = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        drawRoundRect(bahn, cornerRadius = r)
        val halb = size.width / 2
        val anteil = (abw / 100.0).coerceIn(-1.0, 1.0).toFloat()
        val w = (halb * kotlin.math.abs(anteil)).coerceAtLeast(size.height)
        val links = if (anteil < 0) halb - w else halb
        drawRoundRect(farbe, androidx.compose.ui.geometry.Offset(links, 0f), androidx.compose.ui.geometry.Size(w, size.height), r)
        val ecke = size.height / 2
        drawRect(farbe, androidx.compose.ui.geometry.Offset(if (anteil < 0) halb - ecke else halb, 0f), androidx.compose.ui.geometry.Size(ecke, size.height))
        drawRect(mitteStrich, androidx.compose.ui.geometry.Offset(halb - 0.5.dp.toPx(), -2.dp.toPx()), androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height + 4.dp.toPx()))
    }
}

