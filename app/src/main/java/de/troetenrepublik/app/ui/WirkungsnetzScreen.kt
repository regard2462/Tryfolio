package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Einflussverzeichnis
import de.troetenrepublik.app.engine.Einflussverzeichnis.Bestand
import de.troetenrepublik.app.engine.Empfindlichkeit
import de.troetenrepublik.app.engine.Wirkungsnetz
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.landNamen
import de.troetenrepublik.app.wirkungsBestand
import de.troetenrepublik.app.wirkungsMessungen
import java.time.LocalDate

// [Grosses Ganzes 0.73] "Was heisst das fuer mich?": von einer Einflussgroesse zu Laendern, Branchen und den eigenen
// Werten. Ohne Kennung die Uebersicht aller Groessen, die mindestens einen eigenen Wert treffen; mit Kennung (Route
// wirkung/<id>) das Blatt einer Groesse. Eine Richtung steht nur, wo sie belegt oder gemessen ist.

private class Netz(val depot: List<Bestand>, val watch: List<Bestand>, val messungen: Map<String, Empfindlichkeit.Ergebnis>)

@Composable
fun WirkungsnetzScreen(state: AppState, id: String?, onBack: () -> Unit, onOpen: (String) -> Unit, oeffnen: (String) -> Unit) {
    val index = remember { Einflussverzeichnis.geladen }
    val heute = remember { LocalDate.now().toEpochDay() }
    var netz by remember { mutableStateOf<Netz?>(null) }
    var namen by remember { mutableStateOf<Map<String, Pair<String, String>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        namen = state.landNamen()
        val (d, w) = state.wirkungsBestand()
        netz = Netz(d, w, emptyMap())
        // Die Messungen kommen nach: die Liste steht schon, die Richtung ergaenzt sich
        val m = state.wirkungsMessungen(d + w)
        netz = Netz(d, w, m)
    }
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    val n = netz
    val titel = when {
        id == null -> S.wnTitel
        id.startsWith("land:") -> landName(namen, id.removePrefix("land:"))
        else -> groesseName(index, id)
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(titel, if (id == null) null else index.groessen[id]?.stand?.takeIf { it > 0 }?.let { S.abhStand(monatJahr(it)) }, onBack) }
        if (n == null) {
            item { EmptyHint(S.wnLaedt) }
        } else if (id == null) {
            uebersicht(index, n, heute, oeffnen)
        } else {
            blatt(index, n, id, heute, namen, onOpen, oeffnen)
        }
        item { HinweisInfo(S.wnInfo) }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 40.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.uebersicht(
    index: Einflussverzeichnis.Index, n: Netz, heute: Long, oeffnen: (String) -> Unit,
) {
    val alle = Wirkungsnetz.uebersicht(index, n.depot, n.watch, n.messungen, heute)
    val treffer = Wirkungsnetz.mitTreffern(alle)
    if (treffer.isEmpty()) item { EmptyHint(S.wnLeer) }
    // [0.75] Abgelaufene Groessen fehlen in der Liste; das steht da (Anzahl, Ablauf)
    Wirkungsnetz.ausgeblendet(index, heute)?.let { (zahl, bis) ->
        item(key = "abgelaufen") { WeltZeile(S.abhAbgelaufen, zahl.toString(), listOf(S.abhAbgelaufenHinweis(Fmt.date(bis * 86_400_000L)))) }
    }
    items(treffer, key = { "t" + it.groesse.id }) { e ->
        WeltZeile(
            groesseName(e.groesse), "",
            listOfNotNull(S.wnBetrifft(e.depot.size, e.watchlist.size, Fmt.pct(e.depotAnteil * 100, 0)), e.groesse.kette(istDeutsch()).takeIf { it.isNotBlank() }),
            S.wnTyp(e.groesse.typ.name), onClick = { oeffnen("wirkung/" + e.groesse.id) },
        )
    }
    val ohne = alle.filter { it.treffer == 0 }
    if (ohne.isNotEmpty()) item(key = "ohne") {
        var offen by rememberSaveable { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth()) {
            Zwischenkopf(S.wnOhneTreffer)
            ListenPfeil(offen, label = if (offen) null else S.bbAlle(ohne.size)) { offen = !offen }
            if (offen) ohne.forEach { e ->
                WeltZeile(groesseName(e.groesse), "", kennzeichnung = S.wnTyp(e.groesse.typ.name), onClick = { oeffnen("wirkung/" + e.groesse.id) })
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.blatt(
    index: Einflussverzeichnis.Index, n: Netz, id: String, heute: Long, namen: Map<String, Pair<String, String>>,
    onOpen: (String) -> Unit, oeffnen: (String) -> Unit,
) {
    val e = Wirkungsnetz.eintrag(index, id, n.depot, n.watch, n.messungen, heute)
    if (e == null) {
        // [0.75] Eine abgelaufene Groesse sagt, bis wann sie galt, statt nur "offen"
        val bis = Wirkungsnetz.abgelaufen(index, id, heute)
        item { EmptyHint(if (bis != null) S.abhAbgelaufenHinweis(Fmt.date(bis * 86_400_000L)) else S.lbOffen) }
        return
    }
    val g = e.groesse
    g.kette(istDeutsch()).takeIf { it.isNotBlank() }?.let { k ->
        item { Zwischenkopf(S.wnWirkung); Text(k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) }
    }
    val zur = S.wnZur(g.typ.name)
    val route = when (g.typ) {
        Einflussverzeichnis.Typ.ENGPASS, Einflussverzeichnis.Typ.SPANNUNG, Einflussverzeichnis.Typ.KLIMA -> "globus/engpaesse"
        Einflussverzeichnis.Typ.DURCHBRUCH -> "maerkte/trends"
        Einflussverzeichnis.Typ.ZINS -> "maerkte/ausblick"
        else -> null
    }
    if (zur.isNotEmpty() && route != null) item { NavRow(zur, "") { oeffnen(route) } }
    if (e.laender.isNotEmpty() && g.typ != Einflussverzeichnis.Typ.LAND) {
        item { Zwischenkopf(S.wnLaender) }
        items(e.laender, key = { "l$it" }) { a2 -> WeltZeile(landName(namen, a2), "", onClick = { oeffnen("land/$a2") }) }
    }
    if (e.branchen.isNotEmpty()) {
        item { Zwischenkopf(S.wnBranchen) }
        items(e.branchen, key = { "b$it" }) { b -> WeltZeile(S.industryName(b), "") }
    }
    wirkungen(S.wnDepot, "d", e.depot, onOpen, true)
    wirkungen(S.wnWatchlist, "w", e.watchlist, onOpen, false)
    if (e.depot.isEmpty() && e.watchlist.isEmpty()) item { EmptyHint(S.wnLeer) }
    if (e.belege.isNotEmpty()) {
        item { Zwischenkopf(S.wnBelege) }
        items(e.belege, key = { "q$it" }) { b -> Text(b, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.wirkungen(titel: String, vorsatz: String, liste: List<Wirkungsnetz.Wirkung>, onOpen: (String) -> Unit, mitAnteil: Boolean) {
    if (liste.isEmpty()) return
    item(key = "k$vorsatz") { Zwischenkopf(titel) }
    items(liste, key = { vorsatz + it.bestand.wert.symbol }) { w ->
        val richtung = when (w.richtung) { 1 -> S.abhAussage("LAEUFT_MIT"); -1 -> S.abhAussage("LAEUFT_GEGEN"); else -> S.abhBetroffen }
        val ueber = if (w.gemessen != null) null else when (w.kante.ziel.art) { Einflussverzeichnis.ZielArt.WERT -> S.wnGenannt; Einflussverzeichnis.ZielArt.PROFIL -> S.wnUeberProfil; else -> S.wnUeberBranche }
        WeltZeile(
            w.bestand.name, if (mitAnteil) Fmt.pct(w.bestand.gewicht * 100, 1) else "",
            listOfNotNull(richtung, ueber).filter { it.isNotBlank() },
            artStand(w.art, w.kante.stand.takeIf { w.gemessen == null }), onClick = { onOpen(w.bestand.wert.symbol) },
        )
    }
}
