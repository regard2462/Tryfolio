package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.IpoSperrfristen
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.IpoKalender

// Reiter „Sperrfristen“ der Boersengaenge (0.78): zurueckliegende Boersengaenge, deren Sperrfrist laut Prospekt in den
// naechsten 90 Tagen endet, das naechste Ende zuerst. Je Zeile Firma, „in x Tagen“ als Marke, Kuerzel und der
// gesperrte Anteil, soweit der Prospekt ihn nennt; rechts das Enddatum, darunter die Laenge der Frist. Ein Tipp
// oeffnet das Blatt des Boersengangs. Die Erklaerung steht hinter dem Info-Symbol.
@Composable
internal fun IpoSperrfristSeite(
    liste: LazyListState, eintraege: List<IpoSperrfristen.Eintrag>?, geladen: Boolean, mitKontakt: Boolean,
    onAngebot: (IpoKalender.Angebot) -> Unit,
) {
    val voraus = Fmt.num(IpoSperrfristen.VORAUS_TAGE.toDouble(), 0)
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item(key = "kopf", contentType = "kopf") {
            MarktInfoKopf(ipoText("sperr.titel", voraus), ipoText("sperr.info", Fmt.num(IpoSperrfristen.KANDIDATEN.toDouble(), 0)))
        }
        val e = eintraege.orEmpty()
        when {
            !mitKontakt -> item(key = "kontakt") { EmptyHint(ipoText("sperr.kontakt")) }
            e.isEmpty() && !geladen -> item(key = "laedt") { EmptyHint(S.trLaedt) }
            e.isEmpty() -> item(key = "leer") { EmptyHint(ipoText("sperr.leer", voraus)) }
            else -> items(e, key = { IpoSperrfristen.schluessel(it.angebot) }, contentType = { "sperrfrist" }) { x -> IpoSperrfristZeile(x) { onAngebot(x.angebot) } }
        }
        if (mitKontakt && e.isNotEmpty()) item(key = "quellen") { QuellenAnhang(listOf(QuelleLink("SEC EDGAR", "https://www.sec.gov/edgar"))) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun IpoSperrfristZeile(x: IpoSperrfristen.Eintrag, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val a = x.angebot
    // Die naechsten zwei Wochen fallen auf
    val farbe = if (x.inTagen <= 14) LocalSignal.current.warn else Kategorie.farbe(0)
    val anteil = x.anteil?.let { ipoText(if (x.nachAngebot) "sperr.nach" else "sperr.vor", Fmt.pct(it, 0)) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IpoLogo(a)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(a.name, style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                IpoMarke(ipoTageText(x.inTagen), farbe)
                Spacer(Modifier.width(8.dp))
                Text(a.symbol.orEmpty(), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
            }
            if (anteil != null) Text(anteil, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.date(x.ende * IPO_TAG_MS + 12 * 3_600_000L), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            Text(ipoText("sperr.frist", Fmt.num(x.tage.toDouble(), 0)), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
        }
    }
}
