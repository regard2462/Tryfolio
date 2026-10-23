package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Gleichwerte
import de.troetenrepublik.app.market.Handelnde
import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.Politik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Wer einen Wert handelt: Abgeordnete, Senatoren, Regierungsmitglieder und Investoren, und ob sie eher kaufen
// oder verkaufen. Die Wertpapierseite nutzt HandelndeAbschnitt; diese Seite dient Verweisen aus dem Copytrading.

internal object HandelndeLader {

    data class Ergebnis(val eintraege: List<Handelnde.Eintrag>, val hausOffen: List<Kongress.Meldung>, val hausGesamt: Int) {
        val bild: Handelnde.Bild get() = Handelnde.bild(eintraege)
    }

    // Senat und Regierung aendern sich nur mit einer neuen Fassung der App, daher einmal aufbereitet.
    @Volatile private var fest: Pair<Any?, List<Handelnde.Person>>? = null

    private fun festePersonen(state: AppState): List<Handelnde.Person> {
        val schluessel = state.senat to state.regierung
        fest?.takeIf { it.first == schluessel }?.let { return it.second }
        val l = ArrayList<Handelnde.Person>()
        state.senat?.meldungen?.groupBy { it.person }?.forEach { (k, ml) ->
            val name = ml.maxBy { Politik.tag(it.eingang) }.name
            l += Handelnde.Person(Handelnde.Gruppe.SENAT, CopyRouten.senator(k), name,
                ml.filter { !it.papier }.flatMap { m -> m.geschaefte.map { Politik.geschaeft(it, Politik.tag(m.eingang)) } }, kennung = "n:$k")
        }
        state.regierung?.personen?.forEach { p ->
            if (p.meldungen.isNotEmpty()) l += Handelnde.Person(Handelnde.Gruppe.REGIERUNG, CopyRouten.regierung(p.id), p.name, Politik.daten(p).flatMap { it.second.orEmpty() }, kennung = "r:${p.id}")
        }
        fest = schluessel to l
        return l
    }

    // Kuerzel aus den Meldungen, die zu symbol gehoeren: der Wert selbst und seine anderen Notierungen.
    fun passt(symbol: String): (String) -> Boolean {
        val symbole = setOf(symbol) + Gleichwerte.familien.value[symbol].orEmpty()
        return { k -> CopyRouten.symbol(k) in symbole }
    }

    // Vom Repraesentantenhaus zaehlen die schon gelesenen Meldungen der letzten zwoelf Monate (auf dem Geraet
    // gemerkt); hausOffen sind die noch ungelesenen.
    suspend fun laden(state: AppState, symbol: String): Ergebnis = withContext(Dispatchers.Default) {
        val passt = passt(symbol)
        val jetzt = System.currentTimeMillis()
        val personen = ArrayList(festePersonen(state))
        var offen = emptyList<Kongress.Meldung>()
        var gesamt = 0
        state.copy?.let { repo ->
            runCatching { repo.verzeichnis() }
            val ml = repo.stand.value.meldungen.filter { it.elektronisch && it.datum >= jetzt - 365L * 86_400_000L }
            gesamt = ml.size
            val gelesen = ml.map { it to repo.gemerkt(it) }
            offen = gelesen.filter { it.second == null }.map { it.first }
            gelesen.filter { it.second != null }.groupBy { it.first.person }.forEach { (k, l) ->
                personen += Handelnde.Person(Handelnde.Gruppe.HAUS, CopyRouten.person(k), l.first().first.name, l.flatMap { it.second.orEmpty() }, kennung = "k:$k")
            }
        }
        val handel = Handelnde.geschaefte(personen, passt, jetzt)
        // Bestand nur, wenn dieselbe Person den Wert nicht ohnehin in den zwoelf Monaten gehandelt hat
        val bestaende = state.regierung?.personen.orEmpty().filter { it.bestand != null }
            .mapNotNull { p -> Handelnde.bestand(Handelnde.Gruppe.REGIERUNG, CopyRouten.regierung(p.id), p.name, p.bestand, passt, kennung = "r:${p.id}") }
            .filter { b -> handel.none { it.kennung == b.kennung } }
        val e = handel + bestaende + Handelnde.investoren(state.investoren, passt)
        Ergebnis(e, offen, gesamt)
    }
}

// Wer den Wert handelt, auf der Wertpapierseite: die Tendenz, darunter jede Person mit ihren Geschaeften.
// Zuerst die PERSONEN_KURZ juengsten, auf Tipp alle. Ein Tipp auf eine Person oeffnet ihre Seite.
@Composable
internal fun HandelndeAbschnitt(state: AppState, symbol: String, oeffnen: (String) -> Unit) {
    var e by remember(symbol) { mutableStateOf<HandelndeLader.Ergebnis?>(null) }
    var neu by remember { mutableIntStateOf(0) }
    var liest by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var allePersonen by remember(symbol) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(symbol, neu) { e = runCatching { HandelndeLader.laden(state, symbol) }.getOrNull() ?: e }
    SectionHeader(S.copyWhoTitle)
    val r = e
    if (r == null) {
        CopyHinweis(S.copyWhoLoading)
        return
    }
    CopyHinweis(if (r.eintraege.isEmpty()) S.copyWhoNone else tendenzText(r.bild))
    val sortiert = remember(r) {
        r.eintraege.sortedWith(compareBy<Handelnde.Eintrag> { it.gruppe.ordinal }.thenByDescending { it.letzte }.thenByDescending { it.anteil })
    }
    val gezeigt = if (allePersonen) sortiert else sortiert.take(PERSONEN_KURZ)
    var gruppe: Handelnde.Gruppe? = null
    gezeigt.forEach { x ->
        if (x.gruppe != gruppe) {
            gruppe = x.gruppe
            Text(
                gruppenName(x.gruppe), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp),
            )
        }
        HandelndeEintrag(state, x) { oeffnen(if (x.gruppe == Handelnde.Gruppe.INVESTOR) CopyRouten.investor(x.schluessel) else x.schluessel) }
    }
    if (sortiert.size > PERSONEN_KURZ) {
        Text(
            if (allePersonen) S.copyWhoWeniger else S.copyWhoAllePersonen(sortiert.size),
            style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
            modifier = Modifier.clickable { allePersonen = !allePersonen }.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
    // Noch ungelesene Meldungen des Repraesentantenhauses: auf Tipp lesen, wie auf der eigenen Seite
    if (r.hausOffen.isNotEmpty()) {
        CopyHinweis(S.copyWhoHouseOpen(r.hausOffen.size, r.hausGesamt))
        val l = liest
        if (l != null) CopyHinweis(S.copyWhoHouseReading(l.first, l.second))
        else ActionLineWide(S.copyWhoHouseLoad) {
            val repo = state.copy ?: return@ActionLineWide
            val offen = r.hausOffen
            scope.launch {
                liest = 0 to offen.size
                var fertig = 0
                offen.chunked(12).forEach { teil ->
                    repo.geschaefte(teil)
                    fertig += teil.size
                    liest = fertig to offen.size
                }
                liest = null
                neu++
            }
        }
    }
}

// So viele Personen stehen auf der Wertpapierseite, bevor „Alle zeigen“ kommt.
private const val PERSONEN_KURZ = 6

private fun gruppenName(g: Handelnde.Gruppe): String = when (g) {
    Handelnde.Gruppe.HAUS -> S.copyCongress
    Handelnde.Gruppe.SENAT -> S.copySenate
    Handelnde.Gruppe.REGIERUNG -> S.copyGovernment
    Handelnde.Gruppe.INVESTOR -> S.copyInvestors
}

private fun tendenzText(b: Handelnde.Bild): String {
    val t = when (b.tendenz) {
        Handelnde.Richtung.KAUF -> S.copyTendBuy
        Handelnde.Richtung.VERKAUF -> S.copyTendSell
        Handelnde.Richtung.GEMISCHT -> S.copyTendMixed
        else -> null
    }
    return listOfNotNull(t, S.copyWhoCounts(b.kaeufer, b.verkaeufer, b.halten + b.gemischt)).joinToString(" · ")
}

@Composable
fun HandelndeScreen(state: AppState, symbol: String, onBack: () -> Unit, oeffnen: (String) -> Unit) {
    katalogBereit()
    val name = remember(symbol) { CopyRouten.katalogName(symbol) ?: de.troetenrepublik.app.market.Symbols.name(symbol) }
    var e by remember(symbol) { mutableStateOf<HandelndeLader.Ergebnis?>(null) }
    var neu by remember { mutableIntStateOf(0) }
    var liest by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(symbol, neu) { e = runCatching { HandelndeLader.laden(state, symbol) }.getOrNull() ?: e ?: HandelndeLader.Ergebnis(emptyList(), emptyList(), 0) }
    val dismiss = rememberSwipeDownDismiss(onBack)
    val liste = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { ScreenHeader(S.copyWhoTitle, name, onBack) }
        item { CopyHinweis(S.copyWhoIntro) }
        val r = e
        if (r == null) {
            item { CopyLade() }
        } else {
            val b = r.bild
            item {
                Kennzahlen(listOf(
                    S.copyWhoTendency to (when (b.tendenz) {
                        Handelnde.Richtung.KAUF -> S.copyTendBuy
                        Handelnde.Richtung.VERKAUF -> S.copyTendSell
                        Handelnde.Richtung.GEMISCHT -> S.copyTendMixed
                        else -> "–"
                    }),
                    S.copyWhoPersons to r.eintraege.size.toString(),
                    S.copyWhoBuyers to b.kaeufer.toString(),
                    S.copyWhoSellers to b.verkaeufer.toString(),
                ))
            }
            if (r.hausOffen.isNotEmpty()) {
                item { CopyHinweis(S.copyWhoHouseOpen(r.hausOffen.size, r.hausGesamt)) }
                val l = liest
                if (l != null) item { CopyHinweis(S.copyWhoHouseReading(l.first, l.second)) }
                else item {
                    ActionLineWide(S.copyWhoHouseLoad) {
                        val repo = state.copy ?: return@ActionLineWide
                        val offen = r.hausOffen
                        scope.launch {
                            liest = 0 to offen.size
                            var fertig = 0
                            offen.chunked(12).forEach { teil ->
                                repo.geschaefte(teil)
                                fertig += teil.size
                                liest = fertig to offen.size
                            }
                            liest = null
                            neu++
                        }
                    }
                }
            }
            if (r.eintraege.isEmpty()) item { EmptyHint(S.copyWhoNone) }
            Handelnde.Gruppe.entries.forEach { g ->
                val l = r.eintraege.filter { it.gruppe == g }
                    .sortedWith(compareByDescending<Handelnde.Eintrag> { it.letzte }.thenByDescending { it.anteil })
                if (l.isEmpty()) return@forEach
                item {
                    SectionHeader(when (g) {
                        Handelnde.Gruppe.HAUS -> S.copyCongress
                        Handelnde.Gruppe.SENAT -> S.copySenate
                        Handelnde.Gruppe.REGIERUNG -> S.copyGovernment
                        Handelnde.Gruppe.INVESTOR -> S.copyInvestors
                    })
                }
                items(l, key = { "h-${g.name}-${it.schluessel}" }) { x ->
                    HandelndeEintrag(state, x) {
                        oeffnen(if (g == Handelnde.Gruppe.INVESTOR) CopyRouten.investor(x.schluessel) else x.schluessel)
                    }
                }
            }
        }
        item { Quelle(S.copyWhoSource) }
    }
}

@Composable
private fun HandelndeEintrag(state: AppState, x: Handelnde.Eintrag, onClick: () -> Unit) {
    val signal = LocalSignal.current
    val unter = when {
        x.gruppe == Handelnde.Gruppe.INVESTOR -> listOfNotNull(
            x.firma.ifBlank { null },
            wandelText(x.wandel),
            if (x.anteil > 0) S.copyWhoShare(Fmt.pct(x.anteil * 100)) else null,
            S.copyHoldingsAsOf(Fmt.date(Politik.tag(x.stichtag))),
        ).joinToString(" · ")
        x.wandel != null -> S.copyHoldingsAsOf(Fmt.date(Politik.tag(x.stichtag)))
        else -> listOf(
            S.copyWhoTrades(x.kaeufe, x.verkaeufe),
            S.copyLast(Fmt.date(x.letzte)),
        ).joinToString(" · ")
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PersonBild(Fotos.von(state, x.kennung), x.name, size = 38)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NamenText(x.name, style = MaterialTheme.typography.titleSmall)
            VollText(unter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        val (t, farbe) = when (x.richtung) {
            Handelnde.Richtung.KAUF -> S.copyBuy to signal.up
            Handelnde.Richtung.VERKAUF -> S.copySell to signal.down
            Handelnde.Richtung.GEMISCHT -> S.copyWhoMixed to MaterialTheme.colorScheme.onSurfaceVariant
            Handelnde.Richtung.HALTEN -> S.copyWhoHolds to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(t, style = MaterialTheme.typography.labelMedium, color = farbe)
    }
    // Jedes Geschaeft mit Datum und Spanne, bei Investoren die Stueck im Quartal
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    if (x.gruppe == Handelnde.Gruppe.INVESTOR && x.wandel != null) {
        val stichtag = Fmt.date(Politik.tag(x.stichtag))
        val vorher = x.stueckVorher
        val text = when {
            x.wandel == Investoren.Wandel.VERKAUFT && vorher != null -> S.copyWhoStueckWeg(stichtag, Fmt.num(vorher, 0))
            x.wandel == Investoren.Wandel.NEU || vorher == null -> S.copyWhoStueckNeu(stichtag, Fmt.num(x.stueck, 0), Fmt.dollarKompakt(x.wert))
            else -> S.copyWhoStueck(stichtag, (if (x.stueck >= vorher) "+" else "−") + Fmt.num(kotlin.math.abs(x.stueck - vorher), 0),
                Fmt.num(x.stueck, 0), Fmt.dollarKompakt(x.wert))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(start = 52.dp, top = 6.dp))
    }
    if (x.geschaefte.isNotEmpty()) {
        var alle by remember(x.schluessel) { mutableStateOf(false) }
        val gezeigt = if (alle) x.geschaefte else x.geschaefte.take(GESCHAEFTE_KURZ)
        // Datumsspalte so breit wie das breiteste Datum in dieser Schrift, damit es bei grosser Systemschrift
        // nicht in die Spalte daneben ragt
        val datumStil = MaterialTheme.typography.bodySmall
        val messer = androidx.compose.ui.text.rememberTextMeasurer()
        val dichte = androidx.compose.ui.platform.LocalDensity.current
        val datumBreite = remember(datumStil, dichte) {
            // Breitestes Datum eines Jahres (im Englischen haben die Monate verschiedene Laengen)
            val breitestes = (0 until 12).maxOf { m -> messer.measure(Fmt.date(1_798_243_200_000L + m * 2_629_800_000L), datumStil).size.width }
            with(dichte) { breitestes.toDp() } + 10.dp
        }
        Column(Modifier.fillMaxWidth().padding(start = 52.dp, top = 6.dp)) {
            gezeigt.forEach { g ->
                // Eine Zeile je Geschaeft: Datum, Art, Spanne; bei Optionen darunter welche
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.date(g.datum), style = datumStil, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.width(datumBreite))
                    VollText(
                        artText(g.art),
                        style = MaterialTheme.typography.labelMedium,
                        color = when { g.kauf -> signal.up; g.verkauf -> signal.down; else -> grau },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    VollText(spanne(g.von, g.bis), style = MaterialTheme.typography.bodySmall, color = grau)
                }
                optionText(g)?.let { o ->
                    VollText(o, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = datumBreite, bottom = 2.dp))
                }
            }
            if (x.geschaefte.size > GESCHAEFTE_KURZ) {
                Text(
                    if (alle) S.copyWhoWeniger else S.copyWhoAlle(x.geschaefte.size),
                    style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link,
                    modifier = Modifier.clickable { alle = !alle }.padding(vertical = 6.dp),
                )
            }
        }
    }
    }
}

// So viele Geschaefte stehen unter einer Person, bevor „Alle zeigen“ kommt.
private const val GESCHAEFTE_KURZ = 5

private fun wandelText(w: Investoren.Wandel?): String? = when (w) {
    Investoren.Wandel.NEU -> S.copyWhoNewPos
    Investoren.Wandel.ERHOEHT -> S.copyWhoIncreased
    Investoren.Wandel.VERRINGERT -> S.copyWhoReduced
    Investoren.Wandel.VERKAUFT -> S.copyWhoSoldOut
    Investoren.Wandel.GLEICH -> S.copyWhoUnchanged
    null -> null
}
