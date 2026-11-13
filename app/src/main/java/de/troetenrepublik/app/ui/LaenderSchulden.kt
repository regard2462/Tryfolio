package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Alter
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.engine.Schuldenlast
import de.troetenrepublik.app.engine.Schuldenlast.Art
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// [0.78] Schuldentragfaehigkeit im Laenderblatt: Schulden und Zinsen gegen die Einnahmen, Zins gegen Wachstum, Reserven
// gegen Schulden, je Kennzahl mit Ampel. Rechnung in engine/Schuldenlast.kt; Einnahmen, Zinsen, Wachstum und Reserven aus
// der gebuendelten Datei (Weltbank), die Schuldenquote aus den Wirtschaftsdaten des Blatts (IWF).

// Die Datei einmal gelesen, gilt fuer alle Laender
internal object SchuldenLader {
    @Volatile private var datei: Schuldenlast.Datei? = null

    fun gespeichert(): Schuldenlast.Datei? = datei

    suspend fun laden(): Schuldenlast.Datei? {
        datei?.let { return it }
        val d = withContext(Dispatchers.Default) {
            runCatching { Schuldenlast.lesen(de.troetenrepublik.app.market.Daten.text(Schuldenlast.DATEI)) }.getOrNull()
        }
        datei = d
        return d
    }
}

@Composable
internal fun LaenderSchulden(land: GlobusLand, imf: Imf.Datensatz?) {
    var datei by remember { mutableStateOf(SchuldenLader.gespeichert()) }
    LaunchedEffect(Unit) { if (datei == null) datei = SchuldenLader.laden() }
    val d = datei ?: return
    if (imf == null) return
    val tabelle = remember(d, imf) { Schuldenlast.tabelle(d) { a3, jahr -> Imf.wert(imf, Imf.SCHULDEN, a3, jahr) } }
    val zeilen = tabelle[land.a3].orEmpty()
    if (zeilen.isEmpty()) return
    var info by remember(land) { mutableStateOf<Pair<String, String>?>(null) }
    val sammler = LocalQuellenSammler.current
    SideEffect {
        if (sammler != null) for (q in listOf(WaehrungQuellen.weltbank(), WaehrungQuellen.iwf(land.a3))) if (sammler.none { it.name == q.name }) sammler.add(q)
    }
    LaenderAbschnitt(S.ml("sl.titel"), "schulden") {
        for (k in zeilen) {
            val name = S.ml("sl." + k.art.name)
            SchuldenZeile(name, slWert(k), k.ampel) { info = name to slInfo(k) }
        }
        val alt = Alter.zustand(d.stand, Alter.profilFuer(Schuldenlast.DATEI), System.currentTimeMillis()) != Alter.Zustand.FRISCH
        // Der Hinweis auf den Zentralstaat nur, wenn eine Zeile mit Einnahmen oder Zinsen rechnet
        val zentral = zeilen.any { it.art == Art.SCHULDEN_EINNAHMEN || it.art == Art.ZINS_EINNAHMEN || it.art == Art.ZINS_WACHSTUM }
        val hinweis = listOfNotNull(S.ml("sl.hinweis").takeIf { zentral }, S.ml("sl.stand", listOf(Alter.marke(d.stand))).takeIf { alt }).joinToString(" ")
        if (hinweis.isNotEmpty()) LbHinweis(hinweis)
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

private fun slWert(k: Schuldenlast.Kennzahl): String = when (k.art) {
    Art.ZINS_WACHSTUM -> Fmt.signedPunkte(k.wert, 1)
    Art.ZINS_EINNAHMEN -> Fmt.pct(k.wert, 1)
    else -> Fmt.pct(k.wert, 0)
}

// Erklaerung mit den Groessen hinter dem Wert; bei Kennzahlen ohne feste Schwelle der Hinweis auf den Laendervergleich.
private fun slInfo(k: Schuldenlast.Kennzahl): String {
    val b = k.b?.let { if (k.art == Art.ZINS_WACHSTUM) Fmt.signedPct(it, 1) else Fmt.pct(it, 1) }.orEmpty()
    val text = S.ml("sl.info." + k.art.name, listOf(k.a?.let { Fmt.pct(it, 1) }.orEmpty(), b, k.jahr.toString()))
    val vergleich = k.art == Art.SCHULDEN_EINNAHMEN || k.art == Art.ZINS_EINNAHMEN || k.art == Art.RESERVEN
    return if (vergleich && k.ampel != null) text + "\n\n" + S.ml("sl.ampel.drittel") else text
}

// Zeile mit Ampel: Bezeichnung links (bricht bei Platzmangel um), Wert einzeilig rechts.
@Composable
private fun SchuldenZeile(label: String, wert: String, ampel: Fruehwarnung.Ampel?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ampel != null) AmpelPunkt(fwAmpelZeichen(ampel), fwAmpelFarbe(ampel))
        else AmpelPunkt(AmpelZeichen.HINWEIS, MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(wert, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
    }
}
