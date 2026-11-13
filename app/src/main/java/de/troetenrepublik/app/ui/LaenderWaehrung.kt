package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.BigMac
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.engine.LaenderMarkt
import de.troetenrepublik.app.engine.Preisniveau
import de.troetenrepublik.app.engine.Ranglisten
import de.troetenrepublik.app.engine.Regionen
import de.troetenrepublik.app.engine.Waehrungsstaerke
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// [Waehrung und Kaufkraft 0.78] Laenderblatt: Preisniveau und Kaufkraftparitaet (IWF), Staerke der Waehrung und realer
// effektiver Wechselkurs (Weltbank), Big-Mac-Index (The Economist) und die Ranglisten der Laenderindizes (UNDP, Weltbank).
// Je Abschnitt steht die Kernzeile immer da, Zeilen und Verlaeufe klappen auf.

// Die drei Dateien einmal gelesen, gelten fuer alle Laender und fuer die Ebenen des Globus
internal object WaehrungLader {
    class Stand(val waehrung: Waehrungsstaerke.Datei?, val bigmac: BigMac.Datei?, val rang: Ranglisten.Datei?)

    @Volatile private var stand: Stand? = null

    // Basisland der Preisvergleiche (alpha-3), wie das Laenderblatt es zuletzt bestimmt hat
    @Volatile var basis: String = Preisniveau.DEUTSCHLAND

    fun gespeichert(): Stand? = stand

    suspend fun laden(): Stand {
        stand?.let { return it }
        val s = withContext(Dispatchers.Default) {
            fun text(n: String) = de.troetenrepublik.app.market.Daten.text(n)
            Stand(
                runCatching { Waehrungsstaerke.lesen(text(Waehrungsstaerke.DATEI)) }.getOrNull(),
                runCatching { BigMac.lesen(text(BigMac.DATEI)) }.getOrNull(),
                runCatching { Ranglisten.lesen(text(Ranglisten.DATEI)) }.getOrNull(),
            )
        }
        stand = s
        return s
    }
}

internal object WaehrungQuellen {
    private fun de() = I18n.locale.language == "de"
    fun iwf(a3: String) = QuelleLink(if (de()) "IWF" else "IMF", "https://www.imf.org/external/datamapper/profile/$a3")
    fun weltbank() = QuelleLink((if (de()) "Weltbank" else "World Bank") + " (CC BY 4.0)", "https://data.worldbank.org/")
    fun economist() = QuelleLink("The Economist, The Big Mac index (CC BY 4.0)", "https://github.com/TheEconomist/big-mac-data")
    fun undp() = QuelleLink("UNDP, Human Development Report (CC BY 3.0 IGO)", "https://hdr.undp.org/data-center")
}

// Meldet Quellen an das Blatt; sie stehen dort einmal unten im Quellen-Anhang.
@Composable
private fun QuellenMelden(quellen: List<QuelleLink>) {
    val sammler = LocalQuellenSammler.current ?: return
    SideEffect { for (q in quellen) if (sammler.none { it.name == q.name }) sammler.add(q) }
}

@Composable
private fun WkKopf(titel: String, offen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 4.dp)
            .defaultMinSize(minHeight = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        AufklappPfeil(offen)
    }
}

// Dollarbetrag in der Anzeigewaehrung ohne Nachkommastellen
private fun ausDollar(v: Double): String = Fmt.ausDollar(v).let { (x, s) -> Fmt.moneyIn(x, s, 0) }

// Runder Betrag in der Anzeigewaehrung (das Verhaeltnis der Preise gilt in jeder Waehrung)
private fun betrag(v: Double): String = Fmt.moneyIn(v, Fmt.symbol, 0)

private fun waehrungsName(code: String): String = when (code) {
    "USD" -> S.kkDollar
    "EUR" -> S.kkEuro
    else -> code
}

// Basisland der Preisvergleiche: Land des Nutzers (Einstellung), sonst Deutschland
@Composable
private fun rememberBasis(state: AppState, imf: Imf.Datensatz?, jahr: Int): Pair<String, GlobusKarte?> {
    var karte by remember { mutableStateOf<GlobusKarte?>(null) }
    LaunchedEffect(Unit) { karte = ohneAbbruch { state.globusKarte() }.getOrNull() }
    val nutzer = karte?.nachA2(Regionen.aktuell)?.a3
    val basis = if (imf != null) Preisniveau.basis(imf, nutzer, jahr) else nutzer ?: Preisniveau.DEUTSCHLAND
    SideEffect { WaehrungLader.basis = basis }
    return basis to karte
}

// Abschnitte "Waehrung und Kaufkraft" und "Ranglisten" des Laenderblatts
@Composable
internal fun LaenderWaehrung(state: AppState, land: GlobusLand, imf: Imf.Datensatz?, jahr: Int) {
    var st by remember { mutableStateOf(WaehrungLader.gespeichert()) }
    LaunchedEffect(Unit) { if (st == null) st = WaehrungLader.laden() }
    val (basis, karte) = rememberBasis(state, imf, jahr)
    val de = I18n.locale.language == "de"
    val basisName = karte?.nachA3(basis)?.name(de) ?: if (basis == Preisniveau.DEUTSCHLAND) S.kkDeutschland else basis
    WaehrungUndKaufkraft(land, imf, jahr, st, basis, basisName, karte?.nachA3(basis)?.a2)
    st?.rang?.let { LaenderRanglisten(land, it) }
}

@Composable
private fun WaehrungUndKaufkraft(land: GlobusLand, imf: Imf.Datensatz?, jahr: Int, st: WaehrungLader.Stand?, basis: String, basisName: String, basisA2: String?) {
    val signal = LocalSignal.current
    var offen by rememberSaveable("wk") { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val heimat = land.a3 == basis
    val niveau = remember(imf, land, basis, jahr) { imf?.let { Preisniveau.preisniveau(it, land.a3, basis, jahr) } }
    // Kaufkraftparitaet zum Dollar, fuer die USA zum Euro
    val kkpGegen = if (land.a3 == Preisniveau.USA) Preisniveau.DEUTSCHLAND else Preisniveau.USA
    val kkpCode = if (kkpGegen == Preisniveau.USA) "USD" else "EUR"
    val kkp = remember(imf, land, jahr) { imf?.let { Preisniveau.preisniveau(it, land.a3, kkpGegen, jahr) }?.let { Preisniveau.bewertung(it) } }
    val einkommen = remember(imf, land, basis, jahr) { imf?.let { Preisniveau.einkommen(it, land.a3, basis, jahr) } }
    val code = LaenderMarkt.waehrungsCode(land.a2)
    val datei = st?.waehrung
    val gegen = Waehrungsstaerke.gegen(land.a3, basis, code, basisA2?.let { LaenderMarkt.waehrungsCode(it) })
    val gegenCode = when (gegen) {
        Preisniveau.USA -> "USD"
        Preisniveau.DEUTSCHLAND -> "EUR"
        else -> basisA2?.let { LaenderMarkt.waehrungsCode(it) } ?: gegen
    }
    val zeilen = remember(datei, imf, land, gegen) { datei?.let { Waehrungsstaerke.zeilen(it, imf, land.a3, gegen) }.orEmpty() }
    val reer = remember(datei, land) { datei?.let { Waehrungsstaerke.reer(it, land.a3) } }
    val mac = remember(st, land) { st?.bigmac?.let { d -> BigMac.fuer(d, land.a3, code)?.let { d to it } } }
    if (niveau == null && kkp == null && zeilen.isEmpty() && reer == null && mac == null) return

    val quellen = ArrayList<QuelleLink>()
    if (niveau != null || kkp != null) quellen += WaehrungQuellen.iwf(land.a3)
    if (zeilen.isNotEmpty() || reer != null) quellen += WaehrungQuellen.weltbank()
    if (mac != null) quellen += WaehrungQuellen.economist()
    QuellenMelden(quellen)

    WkKopf(S.kkTitel, offen) { offen = !offen }
    val niveauInfo = S.kkPreisniveau to S.kkInfoNiveau(basisName)
    val kkpInfo = S.kkZum(kkpCode) to S.kkInfoKkp
    val kacheln = ArrayList<KachelDaten>()
    if (niveau != null && !heimat) kacheln += KachelDaten(S.kkPreisniveau, Fmt.pct(niveau, 0), S.kkVon(basisName)) { info = niveauInfo }
    if (kkp != null) kacheln += KachelDaten(
        S.kkZum(kkpCode), Fmt.signedPct(kkp, 0), if (kkp.roundToInt() < 0) S.kkUnterKkp else if (kkp.roundToInt() > 0) S.kkUeberKkp else S.kkAufKkp,
    ) { info = kkpInfo }
    if (kacheln.isEmpty()) zeilen.lastOrNull()?.let { z ->
        kacheln += KachelDaten(S.kkStaerke(waehrungsName(gegenCode)), Fmt.signedPct(z.nominal, 0), S.kkJahre(z.jahre), if (z.nominal >= 0) signal.up else signal.down) {
            info = S.kkStaerke(waehrungsName(gegenCode)) to S.kkInfoStaerke
        }
    }
    if (kacheln.isEmpty()) reer?.let { r ->
        kacheln += KachelDaten(S.kkReer, Fmt.signedPct(r.abweichung, 1), S.kkReerHinweis(r.jahr)) { info = S.kkReer to S.kkInfoReer }
    }
    if (kacheln.isEmpty()) mac?.let { (_, m) ->
        m.first.rohDollar.lastOrNull { it != null }?.let { v ->
            kacheln += KachelDaten(S.bmTitel, Fmt.signedPct(v, 0), S.bmGegenDollar) { info = S.bmTitel to S.bmInfo }
        }
    }
    KachelRaster(kacheln, 2)

    if (offen) {
        if (niveau != null && !heimat) {
            LbUnterkopf(S.kkKaufkraft)
            KeyValueRow(S.kkPreisniveau, Fmt.pct(niveau, 0), onClick = { info = niveauInfo }, hint = S.kkBasisJahr(basisName, jahr))
            KeyValueRow(
                S.kkKorb(betrag(1000.0)), betrag(Preisniveau.kostetDort(1000.0, niveau)),
                onClick = { info = S.kkKorb(betrag(1000.0)) to S.kkInfoKorb(basisName) }, hint = S.kkKorbHinweis(basisName, betrag(1000.0)),
            )
            KeyValueRow(
                S.kkReise(betrag(100.0)), betrag(Preisniveau.reichtWie(100.0, niveau)),
                onClick = { info = S.kkReise(betrag(100.0)) to S.kkInfoKorb(basisName) }, hint = S.kkReiseHinweis(basisName),
            )
        }
        if (einkommen != null) {
            if (heimat || niveau == null) LbUnterkopf(S.kkKaufkraft)
            KeyValueRow(S.kkBipKopf, ausDollar(einkommen.nominal), onClick = { info = S.kkBipKopf to S.kkInfoBip(basisName) }, hint = S.kkBipKopfHinweis(jahr))
            if (!heimat) KeyValueRow(
                S.kkBipKaufkraft, ausDollar(einkommen.kaufkraft), onClick = { info = S.kkBipKaufkraft to S.kkInfoBip(basisName) },
                hint = S.kkBipKaufkraftHinweis(basisName),
            )
        }
        if (kkp != null) KeyValueRow(
            S.kkZum(kkpCode), Fmt.signedPct(kkp, 0), onClick = { info = kkpInfo },
            hint = if (kkp.roundToInt() < 0) S.kkUnterKkp else if (kkp.roundToInt() > 0) S.kkUeberKkp else S.kkAufKkp,
        )
        if (imf != null && !heimat) {
            val heute = remember { java.time.LocalDate.now().year }
            val verlauf = remember(imf, land, basis) { Preisniveau.verlauf(imf, land.a3, basis, Imf.ERSTES_JAHR, heute) }
            if (verlauf.size >= 3) {
                val titel = S.kkNiveauVerlauf(verlauf.first().first, basisName)
                LbUnterkopf(titel)
                HistDiagramm(
                    listOf(HistLinie("", verlauf.map { it.first.toDouble() to it.second }, MaterialTheme.colorScheme.primary)),
                    verlauf.first().first.toDouble(), verlauf.last().first.toDouble(), log = false, marken = emptyList(),
                    achsenText = { Fmt.num(it, 0) }, endeText = { Fmt.num(it, 0) }, beschreibung = titel, hoehe = 150.dp,
                )
            }
        }

        if (zeilen.isNotEmpty() && datei != null) {
            val titel = S.kkStaerke(waehrungsName(gegenCode))
            LbUnterkopf(titel)
            StaerkeTabelle(zeilen, signal.up, signal.down) { info = titel to S.kkInfoStaerke }
            Waehrungsstaerke.letztesJahr(datei, land.a3, gegen)?.let { LbHinweis(S.kkSchnitt(it)) }
        }
        // Bindung an Dollar oder Euro; wer die Waehrung selbst fuehrt, hat keine Bindung
        val anker = remember(datei, land) { datei?.let { Waehrungsstaerke.anker(it, land.a3) } }?.takeIf { code != if (it == Preisniveau.USA) "USD" else "EUR" }
        anker?.let { a ->
            KeyValueRow(
                S.kkBindung, if (a == Preisniveau.USA) S.kkFestDollar else S.kkFestEuro,
                onClick = { info = S.kkBindung to S.kkInfoBindung }, hint = S.kkBindungHinweis,
            )
        }
        if (reer != null && datei != null) {
            KeyValueRow(
                S.kkReer, Fmt.signedPct(reer.abweichung, 1), onClick = { info = S.kkReer to S.kkInfoReer },
                hint = S.kkReerHinweis(reer.jahr),
            )
            val verlauf = remember(datei, land) { Waehrungsstaerke.reerVerlauf(datei, land.a3) }
            if (verlauf.size >= 3) HistDiagramm(
                listOf(HistLinie("", verlauf.map { it.first.toDouble() to it.second }, MaterialTheme.colorScheme.tertiary)),
                verlauf.first().first.toDouble(), verlauf.last().first.toDouble(), log = false, marken = emptyList(),
                achsenText = { Fmt.num(it, 0) }, endeText = { Fmt.num(it, 0) }, beschreibung = S.kkReerVerlauf, hoehe = 150.dp,
            )
        }

        mac?.let { (d, paar) ->
            val (m, euroraum) = paar
            val bmInfo = { info = S.bmTitel to S.bmInfo }
            LbUnterkopf(S.bmTitel)
            val monat = trendMonat(m.tag)
            KeyValueRow(
                S.bmPreis, Fmt.num(m.preis, if (m.preis >= 1000) 0 else 2) + " " + m.waehrung, onClick = bmInfo,
                hint = if (euroraum) S.bmStandEuroraum(monat) else monat,
            )
            if (m.waehrung != "EUR") BigMac.euroPreis(d, m)?.let { KeyValueRow(S.bmUmgerechnet, Fmt.money(it, 2), onClick = bmInfo, hint = S.bmKursStichtag) }
            // Gegen den Euro; der Euroraum selbst gegen den Dollar
            val roh = if (euroraum || m.waehrung == "EUR") m.rohDollar.lastOrNull { it != null } else m.rohEuro
            val bereinigt = if (euroraum || m.waehrung == "EUR") m.bereinigtDollar else m.bereinigtEuro
            val gegenTitel = if (euroraum || m.waehrung == "EUR") S.bmGegenDollar else S.bmGegenEuro
            if (roh != null) KeyValueRow(gegenTitel, Fmt.signedPct(roh, 1), onClick = bmInfo, hint = S.bmRoh)
            if (bereinigt != null) KeyValueRow(gegenTitel, Fmt.signedPct(bereinigt, 1), onClick = bmInfo, hint = S.bmBereinigt)
            val verlauf = remember(d, m) { BigMac.verlauf(d, m) }
            if (verlauf.size >= 3 && m.waehrung != "USD") {
                val titel = S.bmVerlauf(verlauf.first().first.toInt())
                LbUnterkopf(titel)
                HistDiagramm(
                    listOf(HistLinie("", verlauf, Kategorie.ocker)), verlauf.first().first, verlauf.last().first, log = false, marken = emptyList(),
                    achsenText = { Fmt.signedPct(it, 0) }, endeText = { Fmt.signedPct(it, 0) }, beschreibung = titel, hoehe = 150.dp,
                )
            }
        }
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Drei Spalten: Zeitraum, nominal, real. Werte brechen nicht um.
@Composable
private fun StaerkeTabelle(zeilen: List<Waehrungsstaerke.Zeile>, up: Color, down: Color, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(S.kkZeitraum, style = MaterialTheme.typography.labelLarge, color = grau, modifier = Modifier.weight(1f))
            Text(S.kkNominal, style = MaterialTheme.typography.labelLarge, color = grau, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            Text(S.kkReal, style = MaterialTheme.typography.labelLarge, color = grau, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
        }
        for (z in zeilen) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(S.kkJahre(z.jahre), style = MaterialTheme.typography.bodyMedium, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            Text(
                Fmt.signedPct(z.nominal, 1), style = MaterialTheme.typography.bodyMedium, color = if (z.nominal >= 0) up else down,
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f),
            )
            Text(
                z.real?.let { Fmt.signedPct(it, 1) } ?: "–", style = MaterialTheme.typography.bodyMedium,
                color = z.real?.let { if (it >= 0) up else down } ?: grau, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f),
            )
        }
    }
}

// Abschnitt "Ranglisten": je Index Wert, Rang und Jahr; ein Tipp erklaert den Index.
@Composable
private fun LaenderRanglisten(land: GlobusLand, d: Ranglisten.Datei) {
    val plaetze = remember(d, land) { Ranglisten.plaetze(d, land.a3) }
    if (plaetze.isEmpty()) return
    var offen by rememberSaveable("rl") { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val quellen = ArrayList<QuelleLink>()
    if (plaetze.any { it.index == Ranglisten.Index.HDI || it.index == Ranglisten.Index.IHDI || it.index == Ranglisten.Index.GESCHLECHTER }) quellen += WaehrungQuellen.undp()
    if (plaetze.any { it.index == Ranglisten.Index.GINI || it.index == Ranglisten.Index.LOGISTIK || it.index == Ranglisten.Index.HUMANKAPITAL }) quellen += WaehrungQuellen.weltbank()
    QuellenMelden(quellen)

    fun wert(p: Ranglisten.Platz) = Fmt.num(p.wert.wert, p.index.stellen)
    WkKopf(S.rlTitel, offen) { offen = !offen }
    KachelRaster(
        plaetze.take(3).map { p -> KachelDaten(S.rlKurz(p.index), S.rlPlatz(p.rang), S.rlVon(p.von)) { info = S.rlName(p.index) to S.rlInfo(p.index) } },
        3,
    )
    if (offen) {
        for (p in plaetze) KeyValueRow(
            S.rlName(p.index), wert(p), onClick = { info = S.rlName(p.index) to S.rlInfo(p.index) }, hint = S.rlRang(p.rang, p.von, p.wert.jahr),
        )
        val verlauf = remember(d, land) { Ranglisten.hdiVerlauf(d, land.a3) }
        if (verlauf.size >= 3) {
            val titel = S.rlHdiVerlauf(verlauf.first().first)
            LbUnterkopf(titel)
            HistDiagramm(
                listOf(HistLinie("", verlauf.map { it.first.toDouble() to it.second }, MaterialTheme.colorScheme.primary)),
                verlauf.first().first.toDouble(), verlauf.last().first.toDouble(), log = false, marken = emptyList(),
                achsenText = { Fmt.num(it, 2) }, endeText = { Fmt.num(it, 3) }, beschreibung = titel, hoehe = 150.dp,
            )
        }
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Zeilen fuer den Laendervergleich: BIP je Kopf zum Wechselkurs und in Preisen des Basislandes, dazu das Preisniveau
internal fun vergleichKaufkraft(a: GlobusLand, b: GlobusLand, imf: Imf.Datensatz, jahr: Int): List<Triple<String, String?, String?>> {
    val basis = Preisniveau.basis(imf, WaehrungLader.basis, jahr)
    val x = Preisniveau.einkommen(imf, a.a3, basis, jahr)
    val y = Preisniveau.einkommen(imf, b.a3, basis, jahr)
    fun niveau(l: GlobusLand) = Preisniveau.preisniveau(imf, l.a3, basis, jahr)?.let { Fmt.pct(it, 0) }
    return listOf(
        Triple(S.kkVglBip, x?.let { ausDollar(it.nominal) }, y?.let { ausDollar(it.nominal) }),
        Triple(S.kkVglKaufkraft, x?.let { ausDollar(it.kaufkraft) }, y?.let { ausDollar(it.kaufkraft) }),
        Triple(S.kkPreisniveau, niveau(a), niveau(b)),
    )
}

// Unter der Vergleichstabelle: was ein Betrag aus dem ersten Land im zweiten kaufen kann
@Composable
internal fun VergleichUmrechnung(a: GlobusLand, b: GlobusLand, imf: Imf.Datensatz, jahr: Int) {
    val niveau = remember(imf, a, b, jahr) { Preisniveau.preisniveau(imf, b.a3, a.a3, jahr) } ?: return
    val de = I18n.locale.language == "de"
    LbAbsatz(S.kkVglUmrechnung(betrag(1000.0), a.name(de), b.name(de), betrag(Preisniveau.reichtWie(1000.0, niveau))))
}
