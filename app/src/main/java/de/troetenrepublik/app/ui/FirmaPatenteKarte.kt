package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Uhr
import de.troetenrepublik.app.i18n.FirmaFaktenTexte
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.FirmaName
import de.troetenrepublik.app.market.FirmaPatente
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs

// Baustein „Patente“ auf der Wertpapierseite einer Aktie, nur mit dem USPTO-Schluessel unter Zugaenge: Kernzeile mit den
// erteilten US-Patenten des letzten vollen Jahres und der Aenderung zum Vorjahr. Geladen wird in Stufen: Anmeldernamen und
// die beiden letzten Jahre fuer die Kernzeile sofort, Verlauf ueber zehn Jahre, Technikfelder und Forschung erst beim Aufklappen.
// Ohne Schluessel oder ohne eindeutig zugeordneten Anmelder zeichnet der Baustein nichts.
@Composable
internal fun PatenteKarte(state: AppState, symbol: String, name: String, langerName: String?, merker: SeitenMerker? = null) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val schluessel = settings.keyUspto
    if (schluessel.isBlank()) return
    val repo = state.behoerden
    val firma = remember(symbol, name, langerName) { FirmaName.namenDerSeite(symbol, name, langerName) }
    val letztes = Uhr.heute().year - 1
    var namen by gemerkt<List<String>?>(merker, "fu/pat/namen") { null }
    var jahre by gemerkt<Map<Int, Int>>(merker, "fu/pat/jahre") { emptyMap() }
    var felder by gemerkt<FirmaPatente.Felder?>(merker, "fu/pat/felder") { null }
    var forschung by gemerkt<Pair<Double, String>?>(merker, "fu/pat/forschung") { null }
    var stufe2 by gemerkt(merker, "fu/pat/stufe2") { false }
    val offen = firmaOffen(merker, "fu-pat")

    // Beide Stufen laufen im Bereich der Seite: Scrollt der Eintrag waehrend des Abrufs aus dem Bild, wird er nicht
    // abgebrochen, und ein halber Stand bleibt nicht auf „Wird geladen“ stehen.
    // Stufe 1: Namen und die beiden letzten Jahre
    LaunchedEffect(symbol, schluessel) {
        val fertig1 = { namen != null && (namen!!.isEmpty() || jahre.containsKey(letztes)) }
        val lade: suspend CoroutineScope.() -> Unit = lade@{
            val n = repo.patentNamen(firma, schluessel) ?: return@lade
            if (n.isNotEmpty()) {
                val neu = jahre.toMutableMap()
                for (j in listOf(letztes, letztes - 1)) repo.patentJahr(n, j, schluessel)?.let { neu[j] = it }
                jahre = neu
            }
            namen = n
        }
        if (fertig1()) return@LaunchedEffect
        if (merker != null) merker.abrufBisFertig("fu/pat/1", fertig1, lade) else lade()
    }
    // Stufe 2: erst beim Aufklappen
    LaunchedEffect(symbol, schluessel, offen.value, namen) {
        val n = namen?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        if (!offen.value || stufe2) return@LaunchedEffect
        val lade: suspend CoroutineScope.() -> Unit = {
            for (j in letztes - FirmaPatente.JAHRE + 1..letztes - 2) {
                if (jahre.containsKey(j)) continue
                val z = repo.patentJahr(n, j, schluessel) ?: continue
                jahre = jahre + (j to z)
            }
            felder = repo.patentFelder(n, schluessel)
            forschung = state.guv(symbol)?.takeIf { it.waehrung == "USD" }?.let { g -> g.forschung?.takeIf { it > 0 }?.let { it to g.ende.take(4) } }
            stufe2 = jahre.size >= FirmaPatente.JAHRE
        }
        if (merker != null) merker.abrufBisFertig("fu/pat/2", { stufe2 }, lade) else lade()
    }

    val kern = FirmaPatente.kern(jahre.toList(), letztes) ?: return
    val teile = listOf(KernTeil(firmaText(if (kern.anzahl == 1) "pat.kern.eins" else "pat.kern.mehr", *(if (kern.anzahl == 1) arrayOf(letztes.toString()) else arrayOf(Fmt.num(kern.anzahl.toDouble(), 0), letztes.toString())))))
    val unten = kern.aenderung?.let { a ->
        val rund = Math.round(abs(a))
        KernTeil(firmaText("pat.vorjahr", Fmt.pct(abs(a), 0)), if (rund == 0L) 0.0 else if (a > 0) 1.0 else -1.0)
    }
    FirmaAbschnitt(firmaText("pat.titel"), KernZeile(teile, unten), firmaText("pat.info", namen.orEmpty().joinToString(", ")), offen) {
        val reihe = (letztes - FirmaPatente.JAHRE + 1..letztes).map { it to jahre[it] }
        if (reihe.all { it.second != null }) {
            Zwischenkopf(firmaText("pat.verlauf"), oben = 4.dp)
            FirmaBalken(reihe.map { it.first.toString() to (it.second ?: 0).toDouble() }) { Fmt.num(it, 0) }
        } else {
            Zwischenkopf(S.loading, oben = 4.dp)
        }
        val f = felder
        if (f != null) {
            Zwischenkopf(firmaText("pat.felder"))
            val farbe = MaterialTheme.colorScheme.primary
            f.klassen.take(6).forEach { (code, n) ->
                val anteil = n.toDouble() / f.patente
                Box(Modifier.padding(horizontal = 20.dp)) {
                    LageBalkenZeile(code + " " + FirmaFaktenTexte.cpc(code), Fmt.pct(anteil * 100, 0), anteil, farbe, null)
                }
            }
            Text(
                firmaText("pat.felder.unter", f.patente.toString()), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        val je = forschung?.let { (b, _) -> FirmaPatente.jeMilliarde(kern.anzahl, b) }
        if (je != null && forschung != null) {
            KeyValueRow(
                firmaText("pat.forschung"), Fmt.num(je, 1),
                hint = firmaText("pat.forschung.unter", forschung!!.second, Fmt.dollarKompakt(forschung!!.first)),
            )
        }
    }
}
