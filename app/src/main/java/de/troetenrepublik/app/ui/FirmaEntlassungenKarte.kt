package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Uhr
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Behoerden
import de.troetenrepublik.app.market.FirmaEntlassungen
import de.troetenrepublik.app.market.Politik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// So viele Meldungen der Liste stehen zuerst da, der Rest hinter „Alle“
private const val LISTE_KURZ = 12

// Meldungen der letzten zwoelf Monate aus den mitgelieferten Daten; null ohne Meldung. Auch der Block „Unternehmen“ nutzt es,
// um schon zugeklappt zu wissen, ob es das Thema gibt.
internal suspend fun entlassungenLesen(state: AppState, symbol: String): FirmaEntlassungen.Auswertung? = withContext(Dispatchers.Default) {
    FirmaEntlassungen.auswerten(state.behoerden.entlassungen?.firmen?.get(symbol).orEmpty(), Uhr.heute())
}

// Baustein „Entlassungen“ auf der Wertpapierseite einer Aktie: Kernzeile mit Zahl der Meldungen und Stellen der letzten zwoelf
// Monate (WARN Act), aufgeklappt der Verlauf je Quartal und die eingeklappte Liste der Meldungen mit Link zur Seite des Staates.
// Die Daten liegen mitgeliefert vor (behoerden/entlassungen.json), es gibt keinen Abruf. Ohne Meldung in zwoelf Monaten
// zeichnet der Baustein nichts.
@Composable
internal fun EntlassungenKarte(state: AppState, symbol: String, merker: SeitenMerker? = null) {
    var auswertung by gemerkt<FirmaEntlassungen.Auswertung?>(merker, "fu/entl") { null }
    var gelesen by gemerkt(merker, "fu/entl/gelesen") { false }
    LaunchedEffect(symbol) {
        if (gelesen) return@LaunchedEffect
        auswertung = entlassungenLesen(state, symbol)
        gelesen = true
    }
    val offen = firmaOffen(merker, "fu-entl")
    val listeOffen = gemerkt(merker, "fu/entl/liste") { false }
    val alle = gemerkt(merker, "fu/entl/alle") { false }
    val uri = appLinks()
    val a = auswertung ?: return
    QuellenMelden(NennQuellen.WARN)
    val meldungen = firmaText(if (a.meldungen == 1) "entl.kern.eins" else "entl.kern.mehr", Fmt.num(a.meldungen.toDouble(), 0))
    val teile = buildList {
        add(KernTeil(meldungen))
        if (a.stellen > 0) add(KernTeil(firmaText("entl.stellen", Fmt.num(a.stellen.toDouble(), 0))))
    }
    val unten = when (a.schliessungen) {
        0 -> firmaText("entl.unten")
        1 -> firmaText("entl.unten.eins")
        else -> firmaText("entl.unten.mehr", a.schliessungen.toString())
    }
    FirmaAbschnitt(firmaText("entl.titel"), KernZeile(teile, KernTeil(unten)), firmaText("entl.info"), offen) {
        Zwischenkopf(firmaText("entl.verlauf"), oben = 4.dp)
        FirmaBalken(a.quartale.map { firmaText("quartal", it.q.toString(), (it.jahr % 100).toString().padStart(2, '0')) to it.stellen.toDouble() }) { Fmt.num(it, 0) }
        AufklappZeile(firmaText("entl.liste"), meldungen, listeOffen.value) { listeOffen.value = !listeOffen.value }
        if (listeOffen.value) {
            (if (alle.value) a.liste else a.liste.take(LISTE_KURZ)).forEach { e -> MeldungZeile(e) { link -> runCatching { uri.openUri(link) } } }
            if (a.liste.size > LISTE_KURZ) ListenPfeil(alle.value, label = if (alle.value) S.less else S.bbAlle(a.liste.size)) { alle.value = !alle.value }
        }
    }
}

// Eine Meldung: Datum, Staat und Ort links, Stellen rechts, darunter die Art; ein Tipp oeffnet die Seite des Staates.
@Composable
private fun MeldungZeile(e: Behoerden.Entlassung, oeffnen: (String) -> Unit) {
    val link = Behoerden.warnSeite(e.s)
    val wo = listOf(Fmt.date(Politik.tag(e.d)), e.s, e.o).filter { it.isNotBlank() }.joinToString(" · ")
    val art = e.t.takeIf { it.isNotBlank() }?.let { firmaText("entl.art.$it") }
    KeyValueRow(
        wo, if (e.n > 0) firmaText("entl.stellen", Fmt.num(e.n.toDouble(), 0)) else "–",
        onClick = link?.let { l -> { oeffnen(l) } }, hint = art,
    )
}
