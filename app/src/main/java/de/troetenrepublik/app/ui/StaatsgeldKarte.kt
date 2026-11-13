package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.Uhr
import de.troetenrepublik.app.i18n.StaatsgeldTexte
import de.troetenrepublik.app.market.Behoerden
import de.troetenrepublik.app.market.FirmaName
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Staatsgeld
import de.troetenrepublik.app.market.StaatsgeldAbruf
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs

// Baustein „Staatsgeld“ im Block „Unternehmen“ einer Aktie: was die Firma als Auftragnehmer und als Empfaenger von
// Zuschuessen, Zahlungen und Krediten vom Staat bekommt. Kernzeile: Summe der letzten zwoelf Monate, Veraenderung zu den
// zwoelf davor, Anteil am Umsatz. Aufgeklappt: Verlauf je Jahr, Aufteilung in Auftraege und Zuschuesse, die Quellen
// (USA, EU, Grossbritannien, EU-Haushalt) und eingeklappt die groessten Auftraggeber, Programme und Auftraege. Die Abrufe
// laufen im Bereich der Seite, das Modell liegt im Merker (listenAbschnitt).

// Dollar je Einheit einer Waehrung aus den Wechselkursen (Einheiten je Euro); null ohne Kurs
internal fun staatsgeldUsdJe(waehrung: String, fx: Map<String, Double>): Double? {
    if (waehrung == "USD") return 1.0
    val usd = ExchangeRates.rate("USD", fx) ?: return null
    val w = ExchangeRates.rate(waehrung, fx) ?: return null
    return usd / w
}

@Composable
internal fun StaatsgeldKarte(state: AppState, symbol: String, name: String, langerName: String?, profil: MarketApi.Profile?, merker: SeitenMerker?) {
    val fx by state.market.fxKurse.collectAsStateWithLifecycle()
    var erg by gemerkt<StaatsgeldAbruf.Ergebnis?>(merker, "fa-sg/$symbol") { null }
    var geladen by gemerkt(merker, "fa-sg/$symbol/geladen") { false }
    LaunchedEffect(symbol) {
        if (geladen) return@LaunchedEffect
        val lade: suspend CoroutineScope.() -> Unit = {
            erg = runCatching { state.behoerden.staatsgeld(symbol, FirmaName.namenDerSeite(symbol, name, langerName)) }.getOrNull()
            // Ohne jede Antwort bleibt der Abruf offen und startet beim naechsten Aufruf neu
            if (erg != null) geladen = true
        }
        if (merker != null) merker.abrufBisFertig("fa-sg/$symbol", { geladen }, lade) else lade()
    }
    val umsatz = remember(profil?.revenue, profil?.financialCurrency, fx) {
        val p = profil
        if (p == null || p.revenue <= 0 || p.financialCurrency.isBlank()) null else staatsgeldUsdJe(p.financialCurrency, fx)?.let { p.revenue * it }
    }
    val modell = remember(erg, fx, umsatz) { erg?.let { staatsgeldModell(it, fx, umsatz, symbol, merker) } }
    val quellen = remember(erg) {
        val q = erg?.posten?.map { it.quelle }.orEmpty().toSet()
        buildList {
            if (Staatsgeld.Quelle.TED in q) add(QuelleLink(StaatsgeldTexte.t("q.ted"), "https://ted.europa.eu/en/"))
            if (Staatsgeld.Quelle.UK in q) add(QuelleLink(StaatsgeldTexte.t("q.uk"), "https://www.contractsfinder.service.gov.uk/"))
            if (Staatsgeld.Quelle.EU in q) add(QuelleLink(StaatsgeldTexte.t("q.eu"), "https://ec.europa.eu/budget/financial-transparency-system/"))
        }
    }
    if (modell != null && quellen.isNotEmpty()) QuellenMelden(*quellen.toTypedArray())
    ModellAusgabe(merker, "fa-staatsgeld", modell)
}

internal fun staatsgeldModell(e: StaatsgeldAbruf.Ergebnis, fx: Map<String, Double>, umsatzUsd: Double?, symbol: String, merker: SeitenMerker?): ListenModell? {
    val euro = staatsgeldUsdJe("EUR", fx)
    val st = Staatsgeld.auswerten(
        e.posten, Uhr.heute(), { staatsgeldUsdJe(it, fx) }, e.usaAuftraggeber, e.usaProgramme,
        if (euro == null) emptyList() else e.euProgramme.map { it.first to it.second * euro },
    ) ?: return null
    val anteil = Staatsgeld.anteil(st.zwoelf, umsatzUsd)
    val teile = buildList {
        add(KernTeil(if (st.zwoelf > 0) StaatsgeldTexte.t("kern.zwoelf", Fmt.dollarKompakt(st.zwoelf)) else StaatsgeldTexte.t("kern.keine")))
        st.aenderung?.let { a ->
            val rund = Math.round(abs(a))
            add(KernTeil(Fmt.pct(abs(a), 0), if (rund == 0L) 0.0 else if (a > 0) 1.0 else -1.0))
        }
    }
    val unten = when {
        anteil != null -> KernTeil(if (anteil < 0.1) StaatsgeldTexte.t("kern.anteil.klein") else StaatsgeldTexte.t("kern.anteil", Fmt.pct(anteil, 1)))
        st.auftraege > 0 && st.zuschuesse > 0 -> KernTeil(StaatsgeldTexte.t("kern.unten", Fmt.dollarKompakt(st.auftraege), Fmt.dollarKompakt(st.zuschuesse)))
        st.auftraege > 0 -> KernTeil(StaatsgeldTexte.t("kern.unten.auftraege", Fmt.dollarKompakt(st.auftraege)))
        st.zuschuesse > 0 -> KernTeil(StaatsgeldTexte.t("kern.unten.zuschuesse", Fmt.dollarKompakt(st.zuschuesse)))
        else -> null
    }
    val zeilen = ArrayList<ZeilenDaten>()
    if (st.jahre.size >= 2) zeilen.add(ZeilenDaten("verlauf") {
        Column {
            Zwischenkopf(StaatsgeldTexte.t("verlauf"), oben = 4.dp)
            FirmaBalken(st.jahre.map { it.first.toString() to it.second }) { Fmt.dollarKompakt(it) }
        }
    })
    if (st.auftraege > 0 && st.zuschuesse > 0) zeilen.add(ZeilenDaten("anteil") { AuftragZuschussBalken(st.auftraege, st.zuschuesse) })
    if (st.auftraege > 0) zeilen.add(ZeilenDaten("auftraege") { FarbZeile(MaterialTheme.colorScheme.primary, StaatsgeldTexte.t("auftraege"), Fmt.dollarKompakt(st.auftraege)) })
    if (st.zuschuesse > 0) zeilen.add(ZeilenDaten("zuschuesse") { FarbZeile(MaterialTheme.colorScheme.tertiary, StaatsgeldTexte.t("zuschuesse"), Fmt.dollarKompakt(st.zuschuesse)) })
    if (st.kredite > 0) zeilen.add(ZeilenDaten("kredite") { KeyValueRow(StaatsgeldTexte.t("kredite"), Fmt.dollarKompakt(st.kredite), hint = StaatsgeldTexte.t("kredite.hinweis")) })
    if (st.zeilen.size >= 2) for (z in st.zeilen) {
        val q = z.quelle.name
        val label = if (z.quelle == Staatsgeld.Quelle.EU) StaatsgeldTexte.t("q.EU", z.jahr.toString()) else StaatsgeldTexte.t("q.$q")
        zeilen.add(ZeilenDaten("q-$q") { KeyValueRow(label, if (z.summe > 0) Fmt.dollarKompakt(z.summe) else "–", hint = StaatsgeldTexte.t("q.$q.hinweis")) })
    }
    if (st.auftraggeber.isNotEmpty()) zeilen.add(ZeilenDaten("auftraggeber") { EinklappListe(StaatsgeldTexte.t("auftraggeber"), st.auftraggeber, "fu/sg/$symbol/auftraggeber", merker) })
    if (st.programme.isNotEmpty()) zeilen.add(ZeilenDaten("programme") { EinklappListe(StaatsgeldTexte.t("programme"), st.programme, "fu/sg/$symbol/programme", merker) })
    if (e.groesste.isNotEmpty()) zeilen.add(ZeilenDaten("groesste") { GroessteAuftraege(e.groesste, "fu/sg/$symbol/groesste", merker) })
    return ListenModell(
        titel = StaatsgeldTexte.t("titel"), gross = true, kern = KernZeile(teile, unten), zeilen = zeilen,
        immerZu = true, info = StaatsgeldTexte.t("info"),
    )
}

// Anteile von Auftraegen und Zuschuessen an der Summe als zweifarbiger Balken
@Composable
private fun AuftragZuschussBalken(auftraege: Double, zuschuesse: Double) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))) {
        Box(Modifier.weight(auftraege.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Box(Modifier.weight(zuschuesse.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
    }
}

// Zeile mit Farbpunkt der Balkenfarbe, Bezeichnung links, Wert rechts in einer Zeile
@Composable
private fun FarbZeile(farbe: Color, bezeichnung: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(farbe))
        Spacer(Modifier.width(10.dp))
        Text(bezeichnung, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(wert, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
    }
}

// Eingeklappte Liste: Titel mit dem groessten Eintrag als Zusatz, aufgeklappt Name und Betrag je Zeile
@Composable
private fun EinklappListe(titel: String, liste: List<Pair<String, Double>>, schluessel: String, merker: SeitenMerker?) {
    val offen = gemerkt(merker, schluessel) { false }
    Column {
        AufklappZeile(titel, liste.first().first, offen.value) { offen.value = !offen.value }
        if (offen.value) liste.forEach { (n, b) -> KeyValueRow(n, Fmt.dollarKompakt(b)) }
    }
}

// Groesste Auftraege der letzten zwoelf Monate (Betrag der ganzen Vergabe) mit Link auf die Vergabe
@Composable
private fun GroessteAuftraege(liste: List<Behoerden.Auftrag>, schluessel: String, merker: SeitenMerker?) {
    val offen = gemerkt(merker, schluessel) { false }
    val links = appLinks()
    Column {
        AufklappZeile(StaatsgeldTexte.t("groesste"), Fmt.dollarKompakt(liste.first().betrag), offen.value) { offen.value = !offen.value }
        if (offen.value) liste.forEach { x ->
            KeyValueRow(
                x.behoerde.ifBlank { x.empfaenger }, Fmt.dollarKompakt(x.betrag),
                onClick = x.intern.takeIf { it.isNotBlank() }?.let { i -> { links.openUri(Behoerden.auftragAdresse(i)) } },
                hint = x.beginn.take(10).takeIf { it.isNotBlank() },
            )
        }
    }
}
