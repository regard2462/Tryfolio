package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.HalterAnzeige
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.dc
import de.troetenrepublik.app.i18n.r2
import de.troetenrepublik.app.market.Gesamt13f
import de.troetenrepublik.app.market.Halter
import de.troetenrepublik.app.market.MarketApi
import kotlin.math.abs

/// Groesste institutionelle Halter (5.93): Anteil von Institutionen und Insidern gesamt, darunter die gemeldeten
// Institute und Fonds (5.96) mit Anteil, Stueckzahl, Wert und Veraenderung zum Vorquartal. Ohne Daten bleibt die
// Karte weg. Zugeklappt stehen die Anteile und die Zahl der Institute in der Kernzeile; die Karte laedt und rechnet,
// die Zeilen kommen als Eintraege der Seite (halterAbschnitt).
@Composable
internal fun HalterKarte(state: AppState, symbol: String, waehrung: String, seite: SeitenWaehrung, merker: SeitenMerker? = null) {
    var daten by gemerkt<Halter.Daten?>(merker, "halter/$symbol") { null }
    // Einmal je Seite, auch ohne Ergebnis: beim Zurueckscrollen nicht neu fragen
    var geladen by gemerkt<Boolean>(merker, "halter/$symbol/geladen") { false }
    LaunchedEffect(symbol) {
        if (geladen) return@LaunchedEffect
        daten = runCatching { state.halterFor(symbol) }.getOrNull()
        geladen = true
    }
    val d = daten
    // 13F-Gesamtbild: eingebaute Datei, ohne Netz
    val g13 = remember(symbol) { Gesamt13f.fuer(Gesamt13f.aktuell, symbol) }
    val modell = remember(d, g13, waehrung, seite) { if (d != null || g13 != null) halterModell(d, g13, waehrung, seite) else null }
    ModellAusgabe(merker, "halter", modell)
}

internal fun LazyListScope.halterAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "halter")
}

private fun halterModell(d: Halter.Daten?, g: Gesamt13f.Wert?, waehrung: String, seite: SeitenWaehrung): ListenModell {
    val u = d?.uebersicht
    val zeilen = ArrayList<ZeilenDaten>()
    if (u != null && (u.institutionen > 0.0 || u.insider > 0.0)) zeilen.add(ZeilenDaten("anteile") { HalterAnteile(u.institutionen, u.insider) })
    val g13 = Gesamt13f.aktuell
    if (g != null && g13 != null) gesamtZeilen(g, g13, seite, zeilen, mitGroessten = d == null || d.halter.isEmpty())
    if (d != null) when (HalterAnzeige.liste(d.halter.size)) {
        HalterAnzeige.Liste.KEINE -> zeilen.add(ZeilenDaten("keine") { PunktText(S.hlKeineNamen, Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) })
        HalterAnzeige.Liste.ZEHN -> zeilen.add(ZeilenDaten("kopf") { Zwischenkopf(S.hlGroesste) })
        HalterAnzeige.Liste.TEIL -> zeilen.add(
            ZeilenDaten("kopf") { Zwischenkopf(if (u != null && u.anzahl > d.halter.size) S.hlMitNamen(d.halter.size, u.anzahl) else S.hlGemeldet) },
        )
    }
    d?.halter?.forEachIndexed { i, h -> zeilen.add(ZeilenDaten("h$i") { HalterZeile(h, waehrung, seite) }) }
    if (d != null && d.fonds.isNotEmpty()) {
        zeilen.add(ZeilenDaten("fonds-kopf") { Zwischenkopf(S.hlFonds) })
        d.fonds.forEachIndexed { i, h -> zeilen.add(ZeilenDaten("f$i") { HalterZeile(h, waehrung, seite) }) }
    }
    val hinweis = listOfNotNull(
        S.hlHinweis, if (d != null && d.fonds.isNotEmpty()) S.hlFondsHinweis else null, if (g != null) S.g13Hinweis else null,
    ).joinToString(" ")
    return ListenModell(
        titel = S.hlTitel,
        gross = true,
        kern = when {
            g != null -> KernZeile(
                listOfNotNull(KernTeil(S.g13KernHalter(Fmt.num(g.halter.toDouble(), 0))), u?.let { KernTeil(S.kernHalter(anteilText(it.institutionen, 1))) }),
                KernTeil(S.g13KernNetto(Fmt.num(abs(g.netto).toDouble(), 0), g.netto >= 0), g.netto.toDouble()),
            )
            u != null -> KernZeile(
                listOf(KernTeil(S.kernHalter(anteilText(u.institutionen, 1)))),
                KernTeil(S.kernHalterUnten(anteilText(u.insider, 1), u.anzahl)),
            )
            else -> null
        },
        zeilen = zeilen,
        immerZu = true,
        nachspann = {
            // Beide Staende in einer Zeile, die Meldefrist und Zuordnung hinter dem Info-Symbol
            val stand = listOfNotNull(
                if (g != null && g13 != null) S.g13Stand(Fmt.num(g13.melder.toDouble(), 0), Fmt.date(isoMs(g13.bis))) else null,
                if (d != null && d.stand > 0) S.hlStand(Fmt.date(d.stand)) else null,
            ).joinToString(" · ").ifEmpty { null }
            InfoZeile(stand, S.infoErklaerung, hinweis, Modifier.padding(bottom = 6.dp))
        },
    )
}

// Zeilen des 13F-Gesamtbilds eines Wertpapiers: Zahl der Halter, Netto-Kaeufer, neue und ausgestiegene, Aufstocker und
// Abbauer, Veraenderung der Stueckzahl, Bestand und aufklappbar die fuenf groessten Halter (Dollarwerte in Anzeigewaehrung).
private fun gesamtZeilen(g: Gesamt13f.Wert, d: Gesamt13f.Datei, seite: SeitenWaehrung, zeilen: MutableList<ZeilenDaten>, mitGroessten: Boolean) {
    zeilen.add(ZeilenDaten("g13-kopf") { Zwischenkopf(S.g13Kopf(quartalKurz(d.stichtag))) })
    zeilen.add(ZeilenDaten("g13-halter") { KeyValueRow(S.g13Halter, Fmt.num(g.halter.toDouble(), 0)) })
    zeilen.add(ZeilenDaten("g13-netto") { RichtungsZeile(S.g13Netto(g.netto >= 0), g.netto.toDouble(), Fmt.num(abs(g.netto).toDouble(), 0)) })
    zeilen.add(ZeilenDaten("g13-neu") { ZweiWerteZeile(r2("g13.neu"), g.neu, r2("g13.weg"), g.weg) })
    zeilen.add(ZeilenDaten("g13-auf") { ZweiWerteZeile(r2("g13.auf"), g.auf, r2("g13.ab"), g.ab) })
    g.aenderung?.let { a -> zeilen.add(ZeilenDaten("g13-stueck") { RichtungsZeile(S.g13Stueck, a, Fmt.pct(abs(a), 1)) }) }
    zeilen.add(ZeilenDaten("g13-bestand") { KeyValueRow(S.g13Bestand, seite.kompakt(g.wert, "USD")) })
    // Steht darunter schon die Liste der groessten Halter, entfaellt die zweite Liste aus den 13F-Meldungen
    if (mitGroessten && g.top.isNotEmpty()) zeilen.add(ZeilenDaten("g13-top") { GroessteHalter(g, seite) })
}

// Anteile von Institutionen und Insidern als ein Balken, der Rest ist der uebrige Streubesitz. Summen ueber 100 Prozent
// (Meldungen mit Mehrfachzaehlung) werden auf die Breite des Balkens gestaucht.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HalterAnteile(institutionen: Double, insider: Double) {
    val inst = institutionen.coerceAtLeast(0.0)
    val ins = insider.coerceAtLeast(0.0)
    val gesamt = maxOf(100.0, inst + ins)
    val rest = (gesamt - inst - ins).coerceAtLeast(0.0)
    val fInst = AnteilsFarben.folge(0)
    val fIns = AnteilsFarben.folge(1)
    val fRest = MaterialTheme.colorScheme.surfaceContainerHigh
    fun teil(v: Double) = (v / gesamt).toFloat().coerceAtLeast(0.001f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(fRest)) {
            if (inst > 0.0) Box(Modifier.weight(teil(inst)).fillMaxHeight().background(fInst))
            if (ins > 0.0) Box(Modifier.weight(teil(ins)).fillMaxHeight().background(fIns))
            if (rest > 0.0) Spacer(Modifier.weight(teil(rest)))
        }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            @Composable
            fun eintrag(farbe: Color, text: String) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(farbe))
                    Spacer(Modifier.width(6.dp))
                    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                }
            }
            if (inst > 0.0) eintrag(fInst, S.kernHalter(anteilText(inst, 1)))
            if (ins > 0.0) eintrag(fIns, dc("hl.insider", anteilText(ins, 1)))
            if (rest > 0.0) eintrag(fRest, dc("hl.rest", Fmt.pct(rest, 1)))
        }
    }
}

// Zwei Zaehlwerte mit Namen in einer Zeile: links der steigende (gruen, Dreieck hoch), rechts der fallende (rot, Dreieck runter).
@Composable
private fun ZweiWerteZeile(linksName: String, links: Int, rechtsName: String, rechts: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(linksName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            ChangeValue(change = 1.0, text = Fmt.num(links.toDouble(), 0), style = MaterialTheme.typography.bodyMedium, colored = true, nullAusText = false)
        }
        Spacer(Modifier.width(20.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(rechtsName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            ChangeValue(change = -1.0, text = Fmt.num(rechts.toDouble(), 0), style = MaterialTheme.typography.bodyMedium, colored = true, nullAusText = false)
        }
    }
}

// Bezeichnung links, rechts Dreieck und Zahl in Signalfarbe.
@Composable
private fun RichtungsZeile(label: String, richtung: Double, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        ChangeValue(change = richtung, text = text, style = MaterialTheme.typography.bodyMedium, colored = true)
    }
}

// Die fuenf groessten 13F-Halter, zugeklappt eine Zeile mit Pfeil; der Zustand bleibt erhalten.
@Composable
private fun GroessteHalter(g: Gesamt13f.Wert, seite: SeitenWaehrung) {
    var offen by rememberSaveable(g.kuerzel) { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { offen = !offen }.heightIn(min = 44.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(S.g13Groesste, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            AufklappPfeil(offen)
        }
        if (offen) g.top.forEach { h -> HalterZeile(Halter.Eintrag(h.name, 0.0, h.stueck, h.wert, 0L, h.aenderung), "USD", seite) }
    }
}

private fun isoMs(s: String): Long = runCatching { java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)

// Eine Zeile: Name, darunter Anteil, Stueckzahl und Wert, soweit gemeldet; rechts die Veraenderung der Stueckzahl
// zum Vorquartal, "unveraendert" bei rund null, ohne Meldung ohne Aufschrift; rechts gleich gross und gleich stark
// wie die uebrigen Werte des Abschnitts.
@Composable
private fun HalterZeile(h: Halter.Eintrag, waehrung: String, seite: SeitenWaehrung) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(de.troetenrepublik.app.market.Edgar13f.lesbar(h.name), style = MaterialTheme.typography.bodyMedium)
            PunktText(
                listOfNotNull(
                    if (h.anteil > 0) anteilText(h.anteil, 2) else null,
                    if (h.stueck > 0) Fmt.compact(h.stueck) + " " + S.pieces else null,
                    if (h.wert > 0) seite.kompakt(h.wert, waehrung) else null,
                ).joinToString(" · ")
            )
        }
        val a = HalterAnzeige.aenderung(h.aenderung)
        if (a != null) {
            Spacer(Modifier.width(12.dp))
            if (a == 0.0) {
                Text(
                    S.hlUnveraendert,
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false,
                )
            } else {
                ChangeValue(change = a, text = Fmt.pct(abs(a), 1), style = MaterialTheme.typography.titleSmall, colored = true)
            }
        }
    }
}

// Anteil als Prozenttext; ein positiver Anteil, der auf null runden wuerde, steht als "unter" der kleinsten Stelle.
private fun anteilText(p: Double, stellen: Int): String = HalterAnzeige.anteil(p, stellen).let { a -> if (a.unter) S.hlUnter(Fmt.pct(a.wert, stellen)) else Fmt.pct(a.wert, stellen) }

// Aus dem Quartalsende (etwa 2025-09-30) wird Q3 25, andere Bezeichnungen bleiben kurz. Das Quartalsende nennt das
// Quartal nur ungefaehr, Yahoo benennt die Quartale genau so.
internal fun quartalKurz(label: String): String {
    val m = Regex("(\\d{4})-(\\d{2})-\\d{2}").find(label) ?: return label.take(7)
    val monat = m.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 } ?: return label.take(7)
    return "Q${(monat - 1) / 3 + 1} " + m.groupValues[1].takeLast(2)
}
