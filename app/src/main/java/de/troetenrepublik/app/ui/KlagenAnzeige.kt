package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.i18n.FirmaTexte
import de.troetenrepublik.app.market.Klagen
import de.troetenrepublik.app.market.Politik
import java.time.LocalDate

// Klagen, Urteile und Behoerdenentscheidungen im Baustein „Rechtsstreit und Strafen“ (market/FirmaKlagen.kt): Verlaufsgrafiken
// je Jahr und eine Liste der wichtigsten Faelle. Die Zeilen haengen an das Modell aus FirmaKarten.kt.

// Quellenzeile fuer den Anhang am Seitenende, je Quelle mit Lizenz
internal fun klagenQuelle(q: Klagen.Quelle): QuelleLink = when (q) {
    Klagen.Quelle.COURTLISTENER -> QuelleLink("CourtListener, Free Law Project", "https://www.courtlistener.com")
    Klagen.Quelle.EU -> QuelleLink("EUR-Lex, Publications Office of the European Union (CC BY 4.0)", "https://eur-lex.europa.eu")
    Klagen.Quelle.CMA -> QuelleLink("Competition and Markets Authority, GOV.UK (Open Government Licence v3.0)", "https://www.gov.uk/cma-cases")
    Klagen.Quelle.DOJ -> QuelleLink("U.S. Department of Justice, Press Releases", "https://www.justice.gov/news")
}

private fun klagenTag(iso: String): String = Politik.tag(iso).takeIf { it > 0 }?.let { Fmt.date(it) } ?: iso

// Zeilen fuer Grafiken und Faelle. zusatzJahre: Jahre weiterer Verfahren (SEC), strafenSonst: Strafen mit Jahr aus anderen
// Quellen (OSHA). Grafiken nur, wenn mindestens zwei Jahre Werte haben; Faelle: die wichtigsten, offene zuerst.
internal fun klagenZeilen(
    k: Klagen.Ergebnis, zusatzJahre: List<Int>, strafenSonst: List<Pair<Int, Double>>, heute: LocalDate, uri: UriHandler,
): List<ZeilenDaten> {
    val zeilen = ArrayList<ZeilenDaten>()
    val jahre = Klagen.neuJeJahr(k.faelle, zusatzJahre, heute)
    if (jahre.count { it.second > 0 } >= 2) zeilen.add(ZeilenDaten("kl-jahre") {
        JahresBalken(FirmaTexte.t("rs.jahreTitel"), jahre.map { it.first to it.second.toDouble() }, MaterialTheme.colorScheme.primary, alleWerte = true) { it.toInt().toString() }
    })
    val strafen = Klagen.strafenJeJahr(k.faelle.mapNotNull { f -> f.betrag?.let { f.jahr to it } } + strafenSonst, heute)
    if (strafen.count { it.second > 0 } >= 2) zeilen.add(ZeilenDaten("kl-strafen") {
        JahresBalken(FirmaTexte.t("rs.strafenTitel"), strafen, LocalSignal.current.warn, alleWerte = false) { Fmt.dollarKompakt(it) }
    })
    k.sortiert.take(KLAGEN_ZEILEN).forEach { f -> zeilen.add(ZeilenDaten("kl-" + f.quelle.name + "-" + f.link.ifBlank { f.titel }) { FallZeile(f, uri) }) }
    return zeilen
}

private const val KLAGEN_ZEILEN = 20

// Balken je Jahr (zehn Jahre), Jahr unter dem Balken, Wert darueber: bei Zaehlungen an jedem Balken, bei Betraegen nur am hoechsten
@Composable
internal fun JahresBalken(titel: String, werte: List<Pair<Int, Double>>, farbe: Color, alleWerte: Boolean, text: (Double) -> String) {
    val max = werte.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: return
    val spitze = werte.indexOfFirst { it.second == max }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom,
        ) {
            werte.forEachIndexed { i, (jahr, v) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (v > 0 && (alleWerte || i == spitze)) {
                        // Am Rand buendig mit dem Balken, damit der Wert nicht ueber den Bildrand ragt
                        val ausrichtung = when {
                            alleWerte -> Alignment.CenterHorizontally
                            i >= werte.size - 2 -> Alignment.End
                            i <= 1 -> Alignment.Start
                            else -> Alignment.CenterHorizontally
                        }
                        Text(text(v), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth().wrapContentWidth(ausrichtung, unbounded = true))
                    }
                    if (v > 0) Box(Modifier.fillMaxWidth().height(maxOf(2f, 54f * (v / max).toFloat()).dp).background(farbe, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                    Text(
                        "'" + (jahr % 100).toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun stelleText(f: Klagen.Fall): String = when (f.stelle) {
    "EuGH" -> FirmaTexte.t("rs.eugh")
    "EuG" -> FirmaTexte.t("rs.eug")
    else -> f.stelle
}

// Titel (bricht um) mit Betrag rechts, darunter Art, Rolle, Gericht oder Behoerde, Sachgebiet, Datum und Stand
@Composable
private fun FallZeile(f: Klagen.Fall, uri: UriHandler) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (f.link.isNotBlank()) Modifier.clickable { runCatching { uri.openUri(f.link) } } else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(f.titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            f.betrag?.let { b ->
                Text(Fmt.dollarKompakt(b), style = MaterialTheme.typography.titleSmall, color = LocalSignal.current.warn, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 12.dp))
            }
        }
        PunktZeile(
            listOf(
                FirmaTexte.t("rs.kArt" + f.art.name),
                when (f.rolle) {
                    Klagen.Rolle.KLAEGER -> FirmaTexte.t("rs.kKlaeger")
                    Klagen.Rolle.BEKLAGTER -> FirmaTexte.t("rs.kBeklagter")
                    Klagen.Rolle.UNBEKANNT -> ""
                },
                stelleText(f),
                if (f.gebiet == Klagen.Gebiet.SONSTIGE) "" else FirmaTexte.t("rs.g" + f.gebiet.name),
                if (f.datum.isNotBlank()) klagenTag(f.datum) else "",
                when (f.stand) {
                    Klagen.Stand.OFFEN -> FirmaTexte.t("rs.kOffen")
                    Klagen.Stand.ABGESCHLOSSEN -> FirmaTexte.t("rs.kAbgeschlossen")
                    Klagen.Stand.UNBEKANNT -> ""
                },
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
