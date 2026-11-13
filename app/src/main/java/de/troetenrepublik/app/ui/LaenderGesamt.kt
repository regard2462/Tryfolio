package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.GlobusEbenen
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.GlobusGesamt
import de.troetenrepublik.app.engine.GlobusGesamt.Art
import de.troetenrepublik.app.engine.GlobusGesamt.Bereich
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.GlobusModus
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.ohneAbbruch
import kotlin.math.roundToInt

// [Globus Gesamtbild 0.73] Farbe eines Punktwerts (0 bis 100) auf der Skala des Globus: Rot unter 50, Grau bei 50, Gruen darueber.
@Composable
internal fun gesamtFarbe(punkte: Double): Color {
    val signal = LocalSignal.current
    return Color(GlobusFarbe.farbe(GlobusModus.GESAMT, punkte, signal.up.toArgb(), signal.down.toArgb(), Kategorie.ocker.toArgb()))
}

// Gesamtbild eines Landes im Laenderblatt: Ueberschrift (Tipp zeigt die Erklaerung), Punkte und Platz, darunter die fuenf
// Bereiche mit Punkten und Balken; ein Tipp auf einen Bereich klappt seine Zutaten auf. Ohne Ergebnis entfaellt der Block.
@Composable
internal fun LaenderGesamt(state: AppState, land: GlobusLand, imf: Imf.Datensatz?, jahr: Int) {
    var zusatz by remember { mutableStateOf(state.globusGesamtGespeichert()) }
    LaunchedEffect(state) { ohneAbbruch { state.globusGesamtZusatz { zusatz = it } } }
    var info by remember { mutableStateOf(false) }
    val aufgeklappt = remember(land) { androidx.compose.runtime.mutableStateListOf<Bereich>() }
    val ergebnis = remember(land, imf, jahr, zusatz) { imf?.let { GlobusGesamt.rechnen(it, land.a3, jahr, zusatz?.get(land.a3)) } } ?: return
    val rang = remember(imf, zusatz, jahr) { imf?.let { GlobusGesamt.rangliste(it, GlobusGesamt.laender(it) + zusatz.orEmpty().keys, jahr, zusatz.orEmpty()) }.orEmpty() }
    val platz = GlobusGesamt.platz(GlobusGesamt.nurStaaten(rang), land.a3)
    val staaten = remember(rang) { GlobusGesamt.nurStaaten(rang).size }
    val punkte = ergebnis.punkte.roundToInt()

    val zutaten = ergebnis.bereiche.flatMap { it.zutaten }
    QuellenLeiste(
        buildList {
            if (zutaten.any { it.art in IWF_ARTEN && it.quelle.isEmpty() }) add(LaenderQuellen.iwf(land.a3))
            if (zutaten.any { it.art == Art.LEBENSERWARTUNG || it.art == Art.WGI || it.quelle == "WB" }) add(LaenderQuellen.weltbank(land.a2))
            if (zutaten.any { it.quelle == "UN" }) add(LaenderQuellen.un())
            if (ergebnis.bereich(Bereich.SICHERHEIT) != null) add(LaenderQuellen.ucdp())
        },
    )

    Text(
        S.ggTitel, style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { info = true }
            .padding(start = 20.dp, end = 20.dp).padding(top = 14.dp, bottom = 6.dp),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            S.ggPunkteVon(punkte), style = MaterialTheme.typography.titleMedium, color = gesamtFarbe(ergebnis.punkte),
            maxLines = 1, softWrap = false, modifier = Modifier.weight(1f),
        )
        if (platz != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                S.ggPlatz(platz, staaten), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
            )
        }
    }
    for (b in Bereich.entries) {
        val e = ergebnis.bereich(b)
        BereichZeile(b, e, e != null && b in aufgeklappt) { if (b in aufgeklappt) aufgeklappt.remove(b) else aufgeklappt.add(b) }
        if (e != null && b in aufgeklappt) for (z in e.zutaten) {
            LbZeile(S.ggZutat(z), zutatWert(z) + (if (z.jahr != 0 && z.jahr != jahr) " · " + z.jahr else ""))
        }
    }
    Spacer(Modifier.height(16.dp))
    if (info) MarktInfoBlatt(S.ggTitel, S.ggInfo) { info = false }
}

private val IWF_ARTEN = setOf(Art.BIP_KOPF, Art.WACHSTUM, Art.INFLATION, Art.SCHULDEN, Art.HAUSHALT, Art.ARBEITSLOS, Art.LEISTUNG)

// Die Zahl einer Zutat mit Einheit
private fun zutatWert(z: GlobusGesamt.Zutat): String = when (z.art) {
    Art.BIP_KOPF -> Fmt.moneyIn(z.wert, S.kkpDollar, 0)
    Art.LEBENSERWARTUNG -> S.lbJahreKurz(Fmt.num(z.wert, 1))
    Art.WACHSTUM -> Fmt.signedPct(z.wert, 1)
    Art.INFLATION, Art.ARBEITSLOS -> Fmt.pct(z.wert, 1)
    Art.SCHULDEN -> S.ggDesBip(Fmt.pct(z.wert, 0))
    Art.HAUSHALT, Art.LEISTUNG -> S.ggDesBip(Fmt.signedPct(z.wert, 1))
    Art.WGI -> z.wert.roundToInt().toString()
    Art.KRIEG, Art.KONFLIKT, Art.SANKTION_EU, Art.SANKTION_US, Art.REISE -> "−" + GlobusGesamt.abzugVon(z)
    Art.KEINE_ABZUEGE -> "0"
}

// Ein Bereich: Name links, Punkte rechts, darunter ein schmaler Balken von 0 bis 100 in der Farbe der Globus-Skala.
@Composable
private fun BereichZeile(b: Bereich, e: GlobusGesamt.BereichErgebnis?, offen: Boolean, onClick: () -> Unit) {
    val spur = MaterialTheme.colorScheme.surfaceVariant
    val farbe = if (e != null) gesamtFarbe(e.punkte) else spur
    Column(Modifier.fillMaxWidth().then(if (e != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(S.ggBereich(b), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                e?.punkte?.roundToInt()?.toString() ?: "–", style = MaterialTheme.typography.bodyMedium,
                color = if (e != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false,
            )
            if (e != null) {
                Spacer(Modifier.width(8.dp))
                AufklappPfeil(offen)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 20.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(spur, size = size, cornerRadius = r)
            if (e != null) {
                val breite = size.width * (e.punkte / 100.0).coerceIn(0.0, 1.0).toFloat()
                if (breite > 0f) drawRoundRect(farbe, topLeft = Offset.Zero, size = Size(breite.coerceAtLeast(size.height), size.height), cornerRadius = r)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

// Kopf des Blocks "Stärkste Länder insgesamt" unter jeder Ebene: die ersten zehn, darunter ein Schalter fuer die vollstaendige
// Rangliste (die weiteren Zeilen setzt der Aufrufer als eigene Listenelemente, damit sie traege entstehen).
@Composable
internal fun GesamtRangOben(
    rang: List<GlobusEbenen.Rang>, karte: GlobusKarte, offen: Boolean, onOffen: (Boolean) -> Unit,
    mitGebieten: Boolean, onGebiete: (Boolean) -> Unit, onLand: (GlobusLand) -> Unit,
) {
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            S.globusRangGesamt, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { info = true }.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) { Chip(S.globusRangGebiete, mitGebieten) { onGebiete(!mitGebieten) } }
        rang.take(GESAMT_OBEN).forEachIndexed { i, r -> GesamtZeile(i + 1, r, karte, onLand) }
        if (rang.size > GESAMT_OBEN) Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { onOffen(!offen) }.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.globusRangAlle(rang.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            AufklappPfeil(offen, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (info) MarktInfoBlatt(S.globusRangGesamt, S.globusRangGesamtInfo) { info = false }
}

internal const val GESAMT_OBEN = 10

// Eine Zeile der Gesamtrangliste: Platz, Name, Punkte; ein Tipp oeffnet das Laenderblatt.
@Composable
internal fun GesamtZeile(platz: Int, r: GlobusEbenen.Rang, karte: GlobusKarte, onLand: (GlobusLand) -> Unit) {
    val land = karte.nachA3(r.code) ?: return
    Row(
        Modifier.fillMaxWidth().clickable { onLand(land) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            platz.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, softWrap = false, modifier = Modifier.width(36.dp),
        )
        Text(land.name(I18n.locale.language == "de"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            r.wert.roundToInt().toString(), style = MaterialTheme.typography.bodyMedium, color = gesamtFarbe(r.wert),
            maxLines = 1, softWrap = false,
        )
    }
}
