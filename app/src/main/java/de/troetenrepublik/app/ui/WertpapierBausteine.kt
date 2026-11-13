package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.BehoerdenRepository
import de.troetenrepublik.app.market.Politik
import androidx.compose.ui.semantics.Role

// Bausteine der Wertpapierseite (5.91): Kopf eines aufklappbaren Abschnitts, offene Orders des Werts, das Blatt mit
// allen Kennzahlen und die gemeinsame Karte der Leerverkaeufe. Preisalarm und Notiz stehen seit 5.92 in der Kopfzeile.

// Themen im eingeklappten Abschnitt „Hintergrund“ in der Reihenfolge der Karten.
internal enum class HintergrundThema { TRENDS, HANDELNDE, FUEHRUNG, HALTER, STRUKTUR, FDA, BEHOERDEN, LOBBY, STUDIEN, FAHRZEUGE, PRODUKTE, BESCHWERDEN, ACHTK_WARN, ACHTK_DEAL, FINANZIERUNG, KETTE }

// Kopf eines Abschnitts, der sich per Tipp auf- und zuklappt: Titel wie SectionHeader, darunter eine graue Zeile,
// was drinsteht, rechts der Pfeil.
@Composable
internal fun AufklappKopf(titel: String, untertitel: String, offen: Boolean, maxZeilen: Int = 2, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp)
            .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.titleLarge)
            if (untertitel.isNotBlank()) PunktText(untertitel, maxZeilen = maxZeilen, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        AufklappPfeil(offen, hoehe = PfeilGroesse.KOPF)
    }
}

// Aufklappbare Zeile im Abschnitt Werkzeuge (5.93): sieht aus wie eine Werkzeugzeile, der Pfeil zeigt nach unten
// und dreht sich beim Aufklappen.
@Composable
internal fun AufklappZeile(titel: String, untertitel: String, offen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.titleSmall)
            if (untertitel.isNotBlank()) PunktText(untertitel, modifier = Modifier.padding(end = 56.dp))
        }
        AufklappPfeil(offen)
    }
}

// Offene Orders dieses Werts als kurze Liste; ein Tipp oeffnet die Seite aller Orders zum Aendern oder Stornieren.
@Composable
internal fun OffeneOrdersKurz(orders: List<Order>, onClick: () -> Unit) {
    if (orders.isEmpty()) return
    Zwischenkopf(S.bwOffeneOrders)
    orders.take(4).forEach { o ->
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PunktText(orderKurz(o), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            ChevronRight()
        }
    }
    if (orders.size > 4) Text(
        S.bbAlle(orders.size), style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
        modifier = Modifier.textKlick(20.dp, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

// Art, Stueckzahl und Preisgrenze einer Order in einer Zeile, wie in der Liste der Orders.
private fun orderKurz(o: Order): String = buildString {
    append(if (o.side == Side.BUY) S.buy else S.sell)
    append(" · ").append(Fmt.qty(o.qty)).append(" ").append(S.pieces)
    when (o.type) {
        OrderType.LIMIT -> append(" · ").append(S.limit).append(" ").append(Fmt.priceEuro(o.limitPrice))
        OrderType.STOP -> append(" · ").append(S.stop).append(" ").append(Fmt.priceEuro(o.limitPrice))
        OrderType.TRAILING_STOP -> append(" · ").append(S.trailingInfo(Fmt.pct(o.trailPct), Fmt.priceEuro(o.limitPrice)))
        OrderType.MARKET -> {}
    }
}

// Alle Kennzahlen in einem Blatt, bei Aktien in Gruppen (5.92); ein Tipp erklaert die Kennzahl, die ISIN laesst sich
// kopieren.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlleKennzahlenBlatt(
    name: String,
    gruppen: List<Pair<String?, List<Pair<String, String>>>>,
    onRowClick: (String) -> Unit,
    aktion: (String) -> Pair<String, () -> Unit>?,
    onDismiss: () -> Unit,
    ende: @Composable () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(S.bwAlleKennzahlen, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            gruppen.forEach { (titel, zeilen) ->
                if (titel != null) Zwischenkopf(titel, Modifier.padding(top = 8.dp))
                StatPages(listOf(zeilen), onRowClick = onRowClick, aktion = aktion)
            }
            ende()
        }
    }
}

// Erklaerung zum Aufklappen (5.97): eine ruhige Zeile mit kurzer Beschriftung und Pfeil rechts; ein Tipp auf Zeile oder Text klappt auf und wieder zu.
// Aufgeklappt stehen Stichpunkte mit haengendem Einzug (0.68).
@Composable
internal fun ErklaerungAufklappen(text: String, modifier: Modifier = Modifier, titel: String = S.infoErklaerung, zusatz: String? = null) {
    var offen by rememberSaveable(text) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { offen = !offen }.heightIn(min = 48.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(titel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (zusatz != null) Text(zusatz, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            AufklappPfeil(offen)
        }
        if (offen) StichpunktText(
            text,
            modifier = Modifier.textKlick(20.dp) { offen = !offen }.padding(horizontal = 20.dp).padding(bottom = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Hinweis ohne FINRA-Zugang: wo man ihn bekommt und die Seiten von FINRA im Browser.
@Composable
private fun FinraOhneZugang(kopf: Boolean, oeffnen: ((String) -> Unit)?) {
    val uri = appLinks()
    Column(Modifier.fillMaxWidth()) {
        if (kopf) SectionHeader(S.bhLeer)
        oeffnen?.let { NavRow(S.bhFinraOhne) { it("zugaenge") } }
        QuellenZeile(S.bhFinraLeerLink, onClick = { runCatching { uri.openUri(de.troetenrepublik.app.market.Finra.LEER_SEITE) } })
        QuellenZeile(S.bhFinraAtsLink, onClick = { runCatching { uri.openUri(de.troetenrepublik.app.market.Finra.ATS_SEITE) } })
    }
}

// Handel ueber Dark Pools (ATS) je Woche (5.94), nur mit eigenem FINRA-Zugang: Anteil am Umsatz der Woche, Verlauf
// ueber ein halbes Jahr und die groessten Plattformen der juengsten Woche.
@Composable
internal fun DarkPoolKarte(state: AppState, symbol: String) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    if (settings.keyFinraId.isBlank() || settings.keyFinraSecret.isBlank()) return
    var dp by remember(symbol) { mutableStateOf<BehoerdenRepository.DarkPool?>(null) }
    var kerzen by remember(symbol) { mutableStateOf<List<de.troetenrepublik.app.data.Candle>>(emptyList()) }
    LaunchedEffect(symbol, settings.keyFinraId, settings.keyFinraSecret) {
        dp = runCatching { state.behoerden.darkPool(symbol.replace('-', '.'), settings.keyFinraId, settings.keyFinraSecret) }.getOrNull()
        if (dp != null) kerzen = state.dailyCloses(symbol)
    }
    val d = dp ?: return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val anteile = remember(d, kerzen) { de.troetenrepublik.app.market.Finra.anteile(d.wochen, kerzen) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(S.dpTitel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        val letzte = d.wochen.last()
        val anteil = anteile.lastOrNull()?.takeIf { it.first == letzte.woche }?.second
        val schnitt = anteile.takeIf { it.size >= 4 }?.let { l -> l.sumOf { it.second } / l.size }
        Text(
            S.dpWoche(Fmt.date(Politik.tag(letzte.woche)), Fmt.num(letzte.aktien, 0), anteil?.let { Fmt.pct(it * 100, 1) }, schnitt?.let { Fmt.pct(it * 100, 1) }, anteile.size),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (anteile.size >= 4) {
            val punkte = remember(anteile) { anteile.mapNotNull { (w, a) -> Politik.tag(w).takeIf { it > 0 }?.let { it to a * 100 } } }
            Column(Modifier.padding(horizontal = 20.dp)) {
                StandSeitZeile(punkte)
                VerlaufFrei(punkte, punkte.minOf { it.second })
            }
        }
        if (d.plaetze.isNotEmpty()) {
            val summe = d.plaetze.sumOf { it.aktien }.coerceAtLeast(1.0)
            Zwischenkopf(S.dpPlattformen)
            d.plaetze.take(3).forEachIndexed { i, p ->
                ShareBar(p.name, Fmt.pct(p.aktien / summe * 100, 0), p.aktien / summe, AnteilsFarben.folge(i))
            }
        }
        Text(S.dpVerzug(d.stufe), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        ErklaerungAufklappen(S.dpErklaerung, Modifier.padding(bottom = 4.dp))
    }
}

// Leerverkaeufe in einer Karte: bei US-Aktien Anteil am Umsatz und offene Leerverkaeufe laut FINRA, sonst die
// veroeffentlichten Leerverkaufspositionen aus Paris, Stockholm und London. Ohne Daten bleibt die Karte weg.
@Composable
internal fun LeerverkaufKarte(state: AppState, symbol: String, isin: String, usWert: Boolean, kopf: Boolean = true, oeffnen: ((String) -> Unit)? = null) {
    LeerverkaufKern(state, symbol, isin, usWert, kopf, oeffnen)
    // Leihgebuehr bei US-Aktien: nur der Link auf die Berichtsseite, kein Abruf
    if (usWert) {
        val uri = appLinks()
        QuellenZeile(S.bhLeihgebuehr, onClick = { runCatching { uri.openUri("https://www.iborrowdesk.com/report/" + symbol.uppercase()) } })
    }
}

@Composable
private fun LeerverkaufKern(state: AppState, symbol: String, isin: String, usWert: Boolean, kopf: Boolean, oeffnen: ((String) -> Unit)?) {
    if (!usWert) {
        if (isin.length == 12 && !isin.startsWith("US")) LeerpositionenKarte(state, isin, kopf)
        return
    }
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    // Ohne eigenen FINRA-Zugang (5.94) nur ein Hinweis und der Weg zu den Zahlen im Browser
    if (settings.keyFinraId.isBlank() || settings.keyFinraSecret.isBlank()) {
        FinraOhneZugang(kopf, oeffnen)
        return
    }
    var l by remember(symbol) { mutableStateOf<BehoerdenRepository.Leerverkauf?>(null) }
    LaunchedEffect(symbol, settings.keyFinraId, settings.keyFinraSecret) {
        l = runCatching { state.behoerden.leerverkauf(symbol.replace('-', '.'), settings.keyFinraId, settings.keyFinraSecret) }.getOrNull()
    }
    val d = l ?: return
    if (d.tage.isEmpty() && d.bestand.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        if (kopf) SectionHeader(S.bhLeer)
        if (d.tage.isNotEmpty()) {
            val zehn = d.tage.takeLast(10)
            KeyValueRow(
                S.bhLeerAnteil(zehn.size), Fmt.pct(zehn.sumOf { it.leer } / zehn.sumOf { it.gesamt }.coerceAtLeast(1.0) * 100, 0),
                hint = S.bhLeerAnteilHinweis,
            )
        }
        d.bestand.lastOrNull()?.let { b ->
            val aenderung = if (b.vorher > 0) (b.aktien.toDouble() / b.vorher - 1) * 100 else null
            KeyValueRow(S.bhLeerOffen(Fmt.date(Politik.tag(b.stichtag))), Fmt.num(b.aktien.toDouble(), 0))
            aenderung?.let { KeyValueRow(S.bhLeerAenderung, Fmt.signedPct(it, 1)) }
            b.tageEindecken?.let { KeyValueRow(S.bhLeerEindecken, Fmt.num(it, 1)) }
        }
        // Verlauf der offenen Leerverkaeufe ueber bis zu drei Jahre (5.94), in Millionen Aktien
        if (d.bestand.size >= 3) {
            val punkte = remember(d) { d.bestand.mapNotNull { b -> Politik.tag(b.stichtag).takeIf { it > 0 }?.let { it to b.aktien / 1e6 } } }
            Text(
                S.bhLeerVerlauf(Fmt.date(punkte.first().first), Fmt.num(punkte.minOf { it.second }, 1), Fmt.num(punkte.maxOf { it.second }, 1)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp),
            )
            Column(Modifier.padding(horizontal = 20.dp)) {
                StandSeitZeile(punkte)
                VerlaufFrei(punkte, punkte.minOf { it.second })
            }
        }
        ErklaerungAufklappen(S.bhLeerErklaerung, Modifier.padding(bottom = 4.dp))
    }
}
