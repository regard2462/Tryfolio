package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.InsiderSeltenheit
import de.troetenrepublik.app.engine.InsiderSimulator
import de.troetenrepublik.app.engine.InsiderSimulator.Je
import de.troetenrepublik.app.engine.InsiderSimulator.Regeln
import de.troetenrepublik.app.engine.WirktDas
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Insider-Simulator auf „Wirkt das?“ (0.77): eine Zeile mit den Stellschrauben als Auswahlfelder. Die Standardregel zeigt die
// mitgelieferte Rueckschau, jede andere Regel rechnet im Hintergrund aus den Form-4-Kaeufen (Person, Rolle, Betrag,
// Meldetag) und den Tagesschlusskursen (CopyKurse, auf dem Geraet zwischengespeichert).
internal class SimZustand(
    val regeln: Regeln,
    val setzen: (Regeln) -> Unit,
    // Ergebnis zu den aktuellen Regeln, null waehrend der Rechnung oder ohne auswertbare Cluster
    val ergebnis: WirktDas.Cluster?,
    // Fortschritt beim Laden der Kurse; null, wenn nichts laeuft
    val laedt: Float?,
    // erster Tag der Meldungen (JJJJ-MM-TT), leer, solange nicht gerechnet
    val beginn: String,
)

private val REGELN_SAVER = listSaver<Regeln, Any>(
    save = { listOf(it.mindest, it.fenster, it.betrag.je.ordinal, it.betrag.dollar, it.nurFuehrung, it.halten, it.kosten) },
    restore = { Regeln(it[0] as Int, it[1] as Int, InsiderSimulator.Betrag(Je.entries[it[2] as Int], it[3] as Double), it[4] as Boolean, it[5] as Int, it[6] as Double) },
)

@Composable
internal fun rememberInsiderSimulator(state: AppState): SimZustand {
    var regeln by rememberSaveable(stateSaver = REGELN_SAVER) { mutableStateOf(InsiderSimulator.STANDARD) }
    var fertig by remember { mutableStateOf<Pair<Regeln, WirktDas.Cluster?>?>(null) }
    var laedt by remember { mutableStateOf<Float?>(null) }
    var beginn by remember { mutableStateOf("") }
    LaunchedEffect(regeln) {
        if (regeln.standard) { laedt = null; return@LaunchedEffect }
        laedt = 0f
        val e = withContext(Dispatchers.Default) {
            runCatching { SimulatorLauf.rechnen(state, regeln) { laedt = it } }.getOrNull()
        }
        beginn = e?.second.orEmpty()
        fertig = regeln to e?.first
        laedt = null
    }
    return SimZustand(regeln, { regeln = it }, fertig?.takeIf { it.first == regeln }?.second, laedt, beginn)
}

// Die Rechnung mit den Daten der App: Kaeufe aus dem Stand der Insidergeschaefte, Kurse aus CopyKurse.
internal object SimulatorLauf {
    // Ergebnis und erster Tag der Meldungen; null ohne Meldungen oder Kurse des Index
    suspend fun rechnen(state: AppState, r: Regeln, fortschritt: (Float) -> Unit): Pair<WirktDas.Cluster?, String>? {
        val d = state.sec.value ?: run { state.secOhneNetz(); state.sec.value } ?: return null
        val beginn = InsiderSeltenheit.beginn(d.geschaefte) ?: return null
        val cl = InsiderSimulator.cluster(d.geschaefte, r)
        if (cl.isEmpty()) return null to beginn
        val tag = 86_400_000L
        val bedarf = HashMap<String, Long>()
        for (c in cl) bedarf.merge(CopyRouten.symbol(c.symbol), java.time.LocalDate.parse(c.tag).toEpochDay() * tag) { a, b -> minOf(a, b) }
        bedarf["^GSPC"] = bedarf.values.min()
        val k = CopyKurse.laden(state, bedarf, fortschritt).kurse
        val stand = d.stand.ifBlank { beginn }
        return InsiderSimulator.rechnen(cl, { s -> k[CopyRouten.symbol(s)] }, k["^GSPC"], r, stand, beginn) to beginn
    }
}

private fun dollarText(v: Double): String = Fmt.mitSymbol(Fmt.num(v, 0), "$")

private fun betragText(b: InsiderSimulator.Betrag): String = S.wsBetrag(if (b.keiner) 0 else b.je.ordinal + 1, dollarText(b.dollar))

private fun simKostenText(x: Double): String = Fmt.num(x, if (x % 1.0 == 0.0) 0 else if ((x * 10) % 1.0 == 0.0) 1 else 2)

// Eine Zeile Auswahlfelder, nach rechts wischbar; eine geaenderte Regel faerbt ihr Feld, „Standardregel“ setzt zurueck.
@Composable
internal fun SimulatorZeile(z: SimZustand) {
    val r = z.regeln
    val s = InsiderSimulator.STANDARD
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuePille(
            S.wsMindest(r.mindest), r.mindest != s.mindest, InsiderSimulator.MINDEST.map { S.wsMindest(it) }, InsiderSimulator.MINDEST.indexOf(r.mindest),
        ) { z.setzen(r.copy(mindest = InsiderSimulator.MINDEST[it])) }
        MenuePille(
            S.wsFenster(r.fenster), r.fenster != s.fenster, InsiderSimulator.FENSTER.map { S.wsFenster(it) }, InsiderSimulator.FENSTER.indexOf(r.fenster),
        ) { z.setzen(r.copy(fenster = InsiderSimulator.FENSTER[it])) }
        MenuePille(
            betragText(r.betrag), r.betrag != s.betrag, InsiderSimulator.BETRAEGE.map { betragText(it) },
            InsiderSimulator.BETRAEGE.indexOf(r.betrag),
        ) { z.setzen(r.copy(betrag = InsiderSimulator.BETRAEGE[it])) }
        MenuePille(
            S.wsRolle(r.nurFuehrung), r.nurFuehrung != s.nurFuehrung, listOf(S.wsRolle(false), S.wsRolle(true)), if (r.nurFuehrung) 1 else 0,
        ) { z.setzen(r.copy(nurFuehrung = it == 1)) }
        MenuePille(
            S.wsHalten(r.halten), r.halten != s.halten, InsiderSimulator.HALTEN.map { S.wsHalten(it) }, InsiderSimulator.HALTEN.indexOf(r.halten),
        ) { z.setzen(r.copy(halten = InsiderSimulator.HALTEN[it])) }
        MenuePille(
            S.wsKosten(simKostenText(r.kosten)), r.kosten != s.kosten, InsiderSimulator.KOSTEN.map { S.wsKosten(simKostenText(it)) }, InsiderSimulator.KOSTEN.indexOf(r.kosten),
        ) { z.setzen(r.copy(kosten = InsiderSimulator.KOSTEN[it])) }
        if (!r.standard) FilterPill(S.wsStandard) { z.setzen(s) }
    }
}
