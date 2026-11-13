package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.DepotStreuung
import de.troetenrepublik.app.engine.Einflussverzeichnis
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Firmenberichte
import de.troetenrepublik.app.market.SecBericht
import de.troetenrepublik.app.market.SecBericht.Posten
import de.troetenrepublik.app.market.SecBericht.Status

// [Grosses Ganzes 0.74] Abschnitt "Wovon sie lebt" der Karte "Abhaengigkeiten und Risiken": Umsatz nach Segment, Region
// und Grosskunde aus dem juengsten Jahresbericht. Je Gruppe hoechstens fuenf Zeilen mit Balken und Anteil rechts, der Rest
// als "Uebrige"; die Marke "gemeldet" und das Geschaeftsjahr stehen einmal im Kopf der Gruppe. Balken und Anteil stehen in
// festen Spalten. [0.76] Fehlt die Gliederung, steht eine knappe Zeile statt eines ganzen Unterabschnitts. Die Anzeige
// kennt nur Firmenberichte.Ergebnis: Eine weitere Quelle (Europa, Asien) liefert dieselbe Datenform (Status.OK mit
// SecBericht.Auswertung) und erscheint ohne Umbau; ohne Quelle bleibt es bei einer Zeile mit Erklaerung.

private const val MAX_ZEILEN = 5

@Composable
internal fun WovonSieLebt(ergebnis: Firmenberichte.Ergebnis?, namen: Map<String, Pair<String, String>>) {
    when (ergebnis?.status) {
        null -> Hinweis(S.wsLuecke(S.wsLaedt))
        Status.OK -> {
            val b = ergebnis?.bericht
            if (b == null) Hinweis(S.wsLuecke(S.wsNichtLesbar)) else Auswertung(b, namen)
        }
        Status.KEIN_SEC -> Luecke(S.wsLuecke(S.wsNichtVerfuegbar), S.wsInfo("KEIN_SEC"))
        Status.KEIN_BERICHT -> Luecke(S.wsLuecke(S.wsNichtVerfuegbar), S.wsInfo("KEIN_BERICHT"))
        Status.OHNE_KONTAKT -> Luecke(S.wsLuecke(S.wsOhneKontakt), S.wsInfo("OHNE_KONTAKT"))
        Status.ZU_GROSS -> Hinweis(S.wsLuecke(S.wsZuGross))
        Status.NICHT_LESBAR -> Hinweis(S.wsLuecke(S.wsNichtLesbar))
        Status.FEHLER -> Hinweis(S.wsLuecke(S.wsFehler))
    }
}

@Composable
private fun Auswertung(b: SecBericht.Auswertung, namen: Map<String, Pair<String, String>>) {
    val leer = b.segmente.isEmpty() && b.regionen.isEmpty() && b.kunden.isEmpty() && b.segmenteStatus == 2 && b.regionenStatus == 2
    if (leer) {
        Luecke(S.wsLuecke(S.wsNichtVerfuegbar), S.wsInfo("LEER"))
        return
    }
    Zwischenkopf(S.wsTitel)
    Gruppe("SEGMENTE", b.segmente, b.segmenteStatus, b.jahr, namen, mitUebrige = true)
    Gruppe("REGIONEN", b.regionen, b.regionenStatus, b.jahr, namen, mitUebrige = true)
    Gruppe("KUNDEN", b.kunden, if (b.kunden.isEmpty()) 2 else 0, b.jahr, namen, mitUebrige = false)
}

// Bezeichnung eines Postens in der App-Sprache
internal fun postenName(p: Posten, namen: Map<String, Pair<String, String>>, gebiet: Boolean): String = when (p.k) {
    1 -> S.wsKunde(p.nr)
    2 -> S.wsKundenGruppe(p.nr)
    3 -> landName(namen, p.l)
    4 -> S.wsSitzland
    5 -> S.wsNichtUsa
    else -> if (gebiet) S.wsGebiet(p.n) else p.n
}

internal fun anteilText(a: Double): String = (a * 100).let { Fmt.pct(it, if (it < 10) 1 else 0) }

// status: 0 vorhanden, 1 nicht eindeutig gemeldet, 2 nicht gemeldet (die Gruppe fehlt dann)
@Composable
private fun Gruppe(key: String, posten: List<Posten>, status: Int, jahr: Int, namen: Map<String, Pair<String, String>>, mitUebrige: Boolean) {
    if (status == 2) return
    if (status == 1) {
        Luecke(S.wsGruppe(key) + ": " + S.wsNichtEindeutig, S.wsInfo(key))
        return
    }
    GruppenKopf(S.wsGruppe(key), S.wsInfo(key), S.wsStand(jahr))
    val sichtbar: List<Triple<String, Double, DepotStreuung.Region?>> =
        if (mitUebrige && posten.size > MAX_ZEILEN) {
            posten.take(MAX_ZEILEN - 1).map { Triple(postenName(it, namen, key == "REGIONEN"), it.w, regionVon(key, it)) } +
                Triple(S.wsUebrige, posten.drop(MAX_ZEILEN - 1).sumOf { it.w }, null)
        } else posten.take(MAX_ZEILEN).map { Triple(postenName(it, namen, key == "REGIONEN"), it.w, regionVon(key, it)) }
    sichtbar.forEachIndexed { i, (name, anteil, region) ->
        val farbe = when {
            name == S.wsUebrige -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            region != null -> AnteilsFarben.region(region)
            else -> AnteilsFarben.folge(i)
        }
        BalkenZeile(name, anteil, farbe)
    }
}

// Region eines Postens der Gruppe REGIONEN (gleiche Farbe wie in der Streuung); Segmente und Kunden folgen der Farbfolge
private fun regionVon(key: String, p: Posten): DepotStreuung.Region? =
    if (key != "REGIONEN") null else AnteilsFarben.regionVon(p.n, if (p.k == 3) p.l else "")

// Zeile mit Bezeichnung, Balken und Anteil; Balken und Anteil stehen in festen Spalten, lange Bezeichnungen laufen in eine zweite Zeile
@Composable
private fun BalkenZeile(name: String, anteil: Double, farbe: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Balken(anteil, farbe, Modifier.width(96.dp))
        Spacer(Modifier.width(8.dp))
        Text(anteilText(anteil), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 44.dp))
    }
}

@Composable
private fun Balken(anteil: Double, farbe: Color, modifier: Modifier) {
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(anteil.toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(farbe))
    }
}

// Titel einer Gruppe mit der Marke "gemeldet" und dem Geschaeftsjahr rechts; ein Tipp zeigt die Erklaerung
@Composable
private fun GruppenKopf(titel: String, info: String, stand: String) {
    var offen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { offen = true }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtMarke(Einflussverzeichnis.Art.GEMELDET)
        Spacer(Modifier.width(10.dp))
        Text(titel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.weight(1f))
        Text(stand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), maxLines = 1, softWrap = false)
    }
    if (offen) MarktInfoBlatt(titel, info) { offen = false }
}

// Knappe Zeile mit einer Aussage ueber die Quelle; ein Tipp zeigt den Grund
@Composable
private fun Luecke(text: String, info: String) {
    var offen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { offen = true }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
    }
    if (offen) MarktInfoBlatt(S.wsTitel, info) { offen = false }
}

@Composable
private fun Hinweis(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}
