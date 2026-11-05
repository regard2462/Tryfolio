package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.HalterAnzeige
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Halter
import de.troetenrepublik.app.market.MarketApi
import kotlin.math.abs

// Groesste institutionelle Halter (5.93): Anteil von Institutionen und Insidern gesamt, darunter die gemeldeten
// Institute und Fonds (5.96) mit Anteil, Stueckzahl, Wert und Veraenderung zum Vorquartal. Ohne Daten bleibt die
// Karte weg.
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
    val d = daten ?: return
    val u = d.uebersicht
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.hlTitel)
        u?.let {
            KeyValueRow(S.hlInstitutionen, anteilText(it.institutionen, 1))
            KeyValueRow(S.hlInsider, anteilText(it.insider, 1))
            if (it.anzahl > 0) PunktText(S.hlAnzahl(it.anzahl), Modifier.padding(horizontal = 20.dp))
        }
        when (HalterAnzeige.liste(d.halter.size)) {
            HalterAnzeige.Liste.KEINE -> PunktText(S.hlKeineNamen, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            HalterAnzeige.Liste.ZEHN -> Zwischenkopf(S.hlGroesste)
            HalterAnzeige.Liste.TEIL -> Zwischenkopf(
                if (u != null && u.anzahl > d.halter.size) S.hlMitNamen(d.halter.size, u.anzahl) else S.hlGemeldet
            )
        }
        d.halter.forEach { h -> HalterZeile(h, waehrung, seite) }
        if (d.fonds.isNotEmpty()) {
            Zwischenkopf(S.hlFonds)
            d.fonds.forEach { h -> HalterZeile(h, waehrung, seite) }
        }
        if (d.stand > 0) {
            Text(
                S.hlStand(Fmt.date(d.stand)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        AufklappText(
            if (d.fonds.isNotEmpty()) S.hlHinweis + " " + S.hlFondsHinweis else S.hlHinweis,
            Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 14.dp),
        )
    }
}

// Eine Zeile: Name, darunter Anteil, Stueckzahl und Wert, soweit gemeldet; rechts die Veraenderung der Stueckzahl
// zum Vorquartal, "unveraendert" bei rund null, ohne Meldung ohne Aufschrift; rechts gleich gross und gleich stark
// wie die uebrigen Werte des Abschnitts.
@Composable
private fun HalterZeile(h: Halter.Eintrag, waehrung: String, seite: SeitenWaehrung) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            AuslaufText(h.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
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
