package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Blasen
import de.troetenrepublik.app.engine.Boden
import de.troetenrepublik.app.engine.ZeichenRueckblick
import de.troetenrepublik.app.i18n.S

// Rueckblick im Ausblick (0.78): unter den Bodenzeichen die grossen Boeden des S&P 500 mit dem damaligen Stand der
// Zeichen, unter dem Blasen-Detektor die Hochs vor Einbruechen mit dem Stand der Messgroessen. Je eine Kernzeile mit
// dem juengsten Fall; ein Tipp klappt die Liste auf (juengster zuerst), ein Tipp auf einen Fall zeigt die Werte.
// Die Liste steht als einzelne Eintraege in der LazyColumn des Reiters. Rechnung und Datei: engine/ZeichenRueckblick.kt.

internal class ZeichenRueckblickZustand(
    val datei: ZeichenRueckblick.Datei?, val bodenAuf: MutableState<Boolean>, val blasenAuf: MutableState<Boolean>,
    val fall: MutableState<String?>,
) {
    // juengster zuerst
    val boeden: List<ZeichenRueckblick.Tief> = datei?.boeden.orEmpty().sortedByDescending { it.tief }
    val hochs: List<ZeichenRueckblick.Hoch> = datei?.blasen.orEmpty().sortedByDescending { it.hoch }
    val alarme: List<ZeichenRueckblick.Alarm> = datei?.let { ZeichenRueckblick.alarme(it) }.orEmpty()
}

@Composable
internal fun rememberZeichenRueckblick(): ZeichenRueckblickZustand {
    val bodenAuf = rememberSaveable { mutableStateOf(false) }
    val blasenAuf = rememberSaveable { mutableStateOf(false) }
    val fall = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { ZeichenRueckblickZustand(ZeichenRueckblick.datei, bodenAuf, blasenAuf, fall) }
}

private fun monat(tag: Int): String = ZeichenRueckblick.jahrMonat(tag).let { (j, m) -> Fmt.monthYear(java.time.YearMonth.of(j, m)) }

private fun jahr(tag: Int): String = ZeichenRueckblick.jahrMonat(tag).first.toString()

// Eintraege unter den Bodenzeichen; Rueckgabe: Zahl der Eintraege
internal fun LazyListScope.bodenRueckblick(z: ZeichenRueckblickZustand): Int {
    val letzter = z.boeden.firstOrNull() ?: return 0
    val auf = z.bodenAuf.value
    item(key = "boden-rueck", contentType = "zr-kern") {
        val (e, n) = remember(letzter) { ZeichenRueckblick.zaehlung(letzter) }
        KernZeile(
            S.mz("zr.b.kern", listOf(monat(letzter.tief))),
            S.mz("zr.b.satz", listOf(e.toString(), n.toString(), z.boeden.size.toString(), jahr(z.boeden.last().tief))), auf,
        ) { z.bodenAuf.value = !auf }
    }
    if (!auf) return 1
    var zahl = 1
    if (z.alarme.isNotEmpty()) {
        zahl++
        item(key = "boden-rueck-phasen", contentType = "zr-phasen") {
            Column(Modifier.fillMaxWidth()) {
                Zwischenkopf(S.mz("zr.b.phasen"), oben = 4.dp)
                for (a in z.alarme) KeyValueRow(
                    if (a.id == ZeichenRueckblick.VIELE) S.mz("b.stufe.2") else S.mz("b.n." + a.id),
                    S.mz("zr.b.fehl", listOf(a.fehlalarme.toString(), a.phasen.toString())),
                    hint = S.mz("zr.b.treffer", listOf(a.erfuellt.toString(), a.boeden.toString(), Fmt.pct(a.zeit * 100, 0), jahr(a.ab))),
                )
                Zwischenkopf(S.mz("zr.b.liste"))
            }
        }
    }
    items(z.boeden, key = { "boden-rueck-" + it.tief }, contentType = { "zr-boden" }) { b ->
        val k = "b" + b.tief
        BodenFall(b, z.fall.value == k) { z.fall.value = if (z.fall.value == k) null else k }
    }
    return zahl + z.boeden.size
}

// Eintraege unter dem Blasen-Detektor; Rueckgabe: Zahl der Eintraege
internal fun LazyListScope.blasenRueckblick(z: ZeichenRueckblickZustand): Int {
    val letztes = z.hochs.firstOrNull() ?: return 0
    val auf = z.blasenAuf.value
    item(key = "blasen-rueck", contentType = "zr-kern") {
        val (r, n) = remember(letztes) { ZeichenRueckblick.zaehlung(letztes) }
        KernZeile(
            S.mz("zr.h.kern", listOf(monat(letztes.hoch))),
            S.mz("zr.h.satz", listOf(r.toString(), n.toString(), z.hochs.size.toString(), jahr(z.hochs.last().hoch))), auf,
        ) { z.blasenAuf.value = !auf }
    }
    if (!auf) return 1
    items(z.hochs, key = { "blasen-rueck-" + it.hoch }, contentType = { "zr-hoch" }) { h ->
        val k = "h" + h.hoch
        HochFall(h, z.fall.value == k) { z.fall.value = if (z.fall.value == k) null else k }
    }
    item(key = "blasen-rueck-ohne", contentType = "zr-ohne") {
        Text(
            S.mz("zr.h.ohne", listOf(ZeichenRueckblick.BLASEN_OHNE_REIHE.joinToString(", ") { blNameAlle(it) })),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
    return z.hochs.size + 2
}

// Kernaussage mit Aufklapp-Pfeil
@Composable
private fun KernZeile(titel: String, satz: String, auf: Boolean, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.titleSmall)
            Text(satz, style = MaterialTheme.typography.bodySmall, color = grau)
        }
        Spacer(Modifier.width(4.dp))
        AufklappPfeil(auf)
    }
}

// Kopf eines Falls: Monat und Zaehlung, darunter der Satz; aufgeklappt der Inhalt
@Composable
private fun Fall(name: String, wert: String, satz: String, auf: Boolean, onClick: () -> Unit, inhalt: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        NameUndWert(name, wert)
        Text(satz, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (auf) {
            Spacer(Modifier.height(4.dp))
            inhalt()
        }
    }
}

// Zeile eines Zeichens im Fall: Punkt, Name (bricht um), Wert rechts; zusatz klein unter dem Namen
@Composable
private fun WertZeile(punkt: @Composable () -> Unit, name: String, wert: String, zusatz: String? = null) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        punkt()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (zusatz != null) Text(zusatz, style = MaterialTheme.typography.labelSmall, color = grau)
        }
        Spacer(Modifier.width(10.dp))
        Text(wert, style = MaterialTheme.typography.bodyMedium, softWrap = false, maxLines = 1)
    }
}

@Composable
private fun OhneAngabe(namen: List<String>) {
    if (namen.isEmpty()) return
    Text(
        S.mz("zr.keine", listOf(namen.joinToString(", "))), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun BodenFall(b: ZeichenRueckblick.Tief, auf: Boolean, onClick: () -> Unit) {
    val (e, n) = remember(b) { ZeichenRueckblick.zaehlung(b) }
    val satz = S.mz("zr.b.fall", listOf(Fmt.signedPct(-b.rueckgang, 0))) + (b.danach?.let { ", " + S.mz("zr.b.danach", listOf(Fmt.signedPct(it, 0))) } ?: "")
    Fall(monat(b.tief), S.mz("zr.von", listOf(e.toString(), n.toString())), satz, auf, onClick) {
        for (w in b.werte) {
            val lage = ZeichenRueckblick.lage(w) ?: continue
            val zeichen = Boden.Zeichen(w.id, w.wert, lage, w.zusatz)
            WertZeile(
                { BodenPunkt(lage) }, S.mz("b.n." + w.id), bodenWert(zeichen),
                w.zusatz?.takeIf { w.id == Boden.RUECKGANG }?.let { S.mz("b.zwei", listOf(Fmt.signedPct(it, 0))) },
            )
        }
        OhneAngabe(remember(b) { ZeichenRueckblick.ohneDaten(b) }.map { S.mz("b.n.$it") })
    }
}

@Composable
private fun HochFall(h: ZeichenRueckblick.Hoch, auf: Boolean, onClick: () -> Unit) {
    val (r, n) = remember(h) { ZeichenRueckblick.zaehlung(h) }
    val name = S.mz("zr.name." + jahr(h.hoch))
    val satz = (if (name.isEmpty()) "" else "$name, ") + S.mz("zr.h.fall", listOf(Fmt.signedPct(-h.rueckgang, 0)))
    Fall(monat(h.hoch), S.mz("zr.von", listOf(r.toString(), n.toString())), satz, auf, onClick) {
        for (w in h.werte) {
            if (w.id !in ZeichenRueckblick.BLASEN_MIT_REIHE) continue
            val ampel = ZeichenRueckblick.ampel(w)
            val davor = w.zusatz?.takeIf { ZeichenRueckblick.nurDavor(w) }?.let { S.mz("zr.h.davor", listOf(blWert(Blasen.Indikator(w.id, it, ampel)))) }
            WertZeile(
                { AmpelPunkt(fwAmpelZeichen(ampel), fwAmpelFarbe(ampel)) }, blNameAlle(w.id),
                blWert(Blasen.Indikator(w.id, w.wert, ampel)), davor,
            )
        }
        OhneAngabe(remember(h) { ZeichenRueckblick.ohneDaten(h) }.map { blNameAlle(it) })
    }
}
