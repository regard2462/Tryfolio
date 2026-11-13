package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Boden
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.Vierer
import de.troetenrepublik.app.i18n.S

// Marktzeichen im Ausblick (0.78): der Vierer-Indikator als Zeile unter den Mustern der Vergangenheit und die
// Bodenzeichen unter dem Blasen-Detektor. Die Rechnung steht in engine/Vierer.kt und engine/Boden.kt.

private const val TAG_MS = 86_400_000L

private fun monatText(jahr: Int, monat: Int): String = Fmt.monthYear(java.time.YearMonth.of(jahr, monat))

// Wert und Datum hinter einer der vier Fragen.
private fun viererHinweis(f: Vierer.Frage): String = when (f.schluessel) {
    Vierer.ZINS -> S.mz("v.h.zins", listOf(Fmt.pct(f.wert, 2), Fmt.pct(f.vergleich, 2), Fmt.date(f.stand * TAG_MS)))
    Vierer.INFLATION -> Fruehwarnung.jahrMonat(f.stand * TAG_MS).let { (j, m) ->
        S.mz("v.h.inflation", listOf(Fmt.pct(f.wert, 1), Fmt.pct(f.vergleich, 1), monatText(j, m)))
    }
    Vierer.DOLLAR -> S.mz("v.h.dollar", listOf(Fmt.num(f.wert, 3), Fmt.num(f.vergleich, 3), Fmt.date(f.stand * TAG_MS)))
    else -> Fruehwarnung.jahrMonat(f.stand * TAG_MS).let { (j, m) -> monatText(j, m) }
}

// Vierer-Indikator: Signal und Zahl der Ja zuerst, darunter seit wann. Ein Tipp zeigt die vier Fragen mit Wert und
// Datum und die Regel.
@Composable
internal fun ViererZeile(e: Vierer.Ergebnis) {
    var auf by rememberSaveable { mutableStateOf(false) }
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val wort = S.mz(when (e.signal) { Vierer.Signal.INVESTIERT -> "v.signal.0"; Vierer.Signal.NICHT_INVESTIERT -> "v.signal.1"; null -> "v.signal.x" })
    val satz = when {
        e.signal == null -> S.mz("v.satz.offen")
        e.uebernommen -> S.mz("v.satz.gleich", listOf(monatText(e.seitJahr, e.seitMonat)))
        else -> S.mz("v.satz.seit", listOf(monatText(e.seitJahr, e.seitMonat)))
    }
    Column(Modifier.fillMaxWidth().clickable { auf = !auf }.animateContentSize().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NameUndWert(S.mz("v.kopf", listOf(wort)), S.mz("v.wert", listOf(e.ja.toString())))
                Text(satz, style = MaterialTheme.typography.bodySmall, color = grau)
            }
            Spacer(Modifier.width(4.dp))
            AufklappPfeil(auf)
        }
        if (auf) {
            for (f in e.fragen) {
                KeyValueRow(
                    S.mz("v.f." + f.schluessel), S.mz(if (f.ja) "v.ja" else "v.nein"),
                    valueColor = if (f.ja) signal.up else signal.down, hint = viererHinweis(f),
                )
            }
            StichpunktText(S.mz("v.regel"), Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
    }
}

// Punkt eines Bodenzeichens: erfuellt gruen mit Haken, nahe gelb mit Warndreieck, sonst ein grauer Ring.
@Composable
internal fun BodenPunkt(lage: Boden.Lage) {
    val groesse = AMPEL_GROESSE
    val signal = LocalSignal.current
    when (lage) {
        Boden.Lage.ERFUELLT -> AmpelPunkt(AmpelZeichen.GUT, signal.up)
        Boden.Lage.NAHE -> AmpelPunkt(AmpelZeichen.ACHTUNG, signal.warn)
        Boden.Lage.FERN -> {
            val grau = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            val text = S.mz("b.l.2")
            Canvas(Modifier.size(groesse).semantics { contentDescription = text }) {
                val d = size.width * 0.12f
                drawCircle(grau, radius = size.width / 2 - d, style = Stroke(width = d))
            }
        }
    }
}

// Wert eines Bodenzeichens in der passenden Einheit.
internal fun bodenWert(z: Boden.Zeichen): String = when (z.schluessel) {
    Boden.PRAEMIE -> (if (z.wert >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(z.wert), 1) + " " + S.fwPunkte
    Boden.RUECKGANG -> Fmt.signedPct(z.wert, 0)
    else -> Fmt.num(z.wert, 1)
}

// Bodenzeichen: Gesamtbild mit Zaehlung und einem Punkt je Zeichen, darunter je Zeichen eine Zeile mit Einordnung und
// Wert. Ein Tipp auf eine Zeile zeigt, wie das Zeichen gemessen wird.
@Composable
internal fun BodenInhalt(zeichen: List<Boden.Zeichen>) {
    val stufe = remember(zeichen) { Boden.gesamt(zeichen) } ?: return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var gewaehlt by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BodenPunkt(when (stufe) { Boden.Stufe.VIELE -> Boden.Lage.ERFUELLT; Boden.Stufe.EINZELNE -> Boden.Lage.NAHE; Boden.Stufe.KEINE -> Boden.Lage.FERN })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(S.mz("b.stufe." + stufe.ordinal), style = MaterialTheme.typography.titleMedium)
                    Text(
                        S.mz("b.satz", listOf(zeichen.count { it.lage == Boden.Lage.ERFUELLT }.toString(), zeichen.size.toString(), zeichen.count { it.lage == Boden.Lage.NAHE }.toString())),
                        style = MaterialTheme.typography.bodySmall, color = grau,
                    )
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (z in zeichen.sortedBy { it.lage.ordinal }) BodenPunkt(z.lage)
            }
        }
        zeichen.forEachIndexed { i, z ->
            if (i > 0) Trennlinie()
            Row(
                Modifier.fillMaxWidth().clickable { gewaehlt = z.schluessel }.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BodenPunkt(z.lage)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(S.mz("b.n." + z.schluessel), style = MaterialTheme.typography.titleSmall)
                    Text(S.mz("b." + z.schluessel + "." + z.lage.ordinal), style = MaterialTheme.typography.bodySmall, color = grau)
                }
                Spacer(Modifier.width(10.dp))
                Text(bodenWert(z), style = MaterialTheme.typography.titleSmall, softWrap = false, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                ChevronRight()
            }
        }
        gewaehlt?.let { k ->
            zeichen.firstOrNull { it.schluessel == k }?.let { z ->
                val zwei = z.zusatz?.takeIf { z.schluessel == Boden.RUECKGANG }?.let { "\n\n" + S.mz("b.zwei", listOf(Fmt.signedPct(it, 0))) }.orEmpty()
                MarktInfoBlatt(S.mz("b.n." + z.schluessel), S.mz("b.e." + z.schluessel) + zwei) { gewaehlt = null }
            }
        }
        Spacer(Modifier.heightIn(min = 10.dp))
    }
}
