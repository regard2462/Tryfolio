package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.Aktienkennzahlen
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.BullBear
import de.troetenrepublik.app.engine.Ertragswert
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.FairesKgv
import de.troetenrepublik.app.engine.GewinnSchnitt
import de.troetenrepublik.app.engine.Levermann
import de.troetenrepublik.app.engine.LynchTyp
import de.troetenrepublik.app.engine.Qualitaet
import de.troetenrepublik.app.engine.Zahlenreaktion
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Quartalszahlen
import de.troetenrepublik.app.market.ohneAbbruch
import de.troetenrepublik.app.marktmachtFuer
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

// Bewertungsverfahren je Aktie (0.78): Levermann-Punkte und Typ nach Lynch als Zeilen im Abschnitt „Bewertung und
// Kennzahlen“, faires KGV und Ertragswert ohne Wachstum im Blatt der Bewertung. Die Rechnungen stehen in engine/
// (Levermann, LynchTyp, FairesKgv, Ertragswert), hier nur das Zusammentragen der Angaben und die Anzeige.

private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

// Was die Levermann-Punkte aus den Tageskursen brauchen.
internal class LevermannKurse(val kurs6: Double?, val kurs12: Double?, val umkehr: List<Double>?, val zahlen: Double?)

// Tageskurse der Aktie und ihres Vergleichsindex, daraus Kurs gegen 6 und 12 Monate, die drei Monate gegen den Index
// und die Reaktion auf die letzten Quartalszahlen gegen den Index.
internal suspend fun levermannKurse(state: AppState, symbol: String, p: MarketApi.Profile): LevermannKurse {
    val kerzen = state.zahlenKerzen(symbol)
    val index = Aktienkennzahlen.index(symbol, p.country)?.let { state.zahlenKerzen(it.first) }.orEmpty()
    val zone = if ('.' in symbol) Quartalszahlen.platz(symbol)?.zone ?: NEW_YORK else NEW_YORK
    val termine = ohneAbbruch { state.nasdaqUeberraschung(symbol) }.getOrNull().orEmpty().map { it.tag }
    val enden = p.quartal?.verlauf.orEmpty().mapNotNull { q -> runCatching { LocalDate.parse(q.ende).toEpochDay() }.getOrNull() }
    val heute = LocalDate.now(zone).toEpochDay()
    val letzte = Zahlenreaktion.reaktionen(kerzen, termine, enden, zone).firstOrNull()?.takeIf { heute - it.tag in 0..Levermann.ZAHLEN_MAX_TAGE }
    return LevermannKurse(
        Levermann.kursGegen(kerzen, 6), Levermann.kursGegen(kerzen, 12),
        if (index.isEmpty()) null else Levermann.umkehrMonate(kerzen, index),
        letzte?.let { Levermann.zahlenGegenIndex(it.schritt, it.tag, index, zone) },
    )
}

// Boersenwert in Euro: der des Kurses, sonst der des Profils aus der Handelswaehrung umgerechnet.
private fun boersenwertEuro(p: MarketApi.Profile, quote: Quote?, fx: Map<String, Double>): Double? =
    quote?.marketCap?.takeIf { it > 0 }
        ?: p.marketCap.takeIf { it > 0 }?.let { mc -> ExchangeRates.rateHaupt(quote?.nativeCurrency.orEmpty(), fx)?.takeIf { it > 0 }?.let { mc / it } }

internal fun levermannEingabe(
    p: MarketApi.Profile, quote: Quote?, jahre: List<Qualitaet.Jahr>?, schnitt: GewinnSchnitt.Ergebnis?, kurse: LevermannKurse?,
    fx: Map<String, Double>,
): Levermann.Eingabe {
    val letztes = jahre?.lastOrNull()
    val marge = letztes?.let { j -> j.ebit?.let { e -> j.umsatz?.takeIf { it > 0 }?.let { e / it * 100.0 } } } ?: p.operatingMargin.takeIf { it != 0.0 }
    val quote1 = Bewertungsblick.ekQuote(letztes?.bilanzsumme, letztes?.verbindlichkeiten)
        ?: if (p.totalAssets > 0 && p.equity > 0 && p.equity < p.totalAssets) p.equity / p.totalAssets * 100.0 else null
    val kgv = Bewertungsblick.kgv(p.trailingPe, quote?.peRatio) ?: p.forwardPe.takeIf { it > 0 } ?: if (p.eps < 0) -1.0 else null
    return Levermann.Eingabe(
        ekRendite = p.returnOnEquity.takeIf { it != 0.0 },
        ebitMarge = marge,
        ekQuote = quote1,
        kgv = kgv,
        kgvSchnitt = schnitt?.kgv,
        note = p.recommendationMean.takeIf { it > 0 },
        analysten = p.analystCount,
        zahlen = kurse?.zahlen,
        revision = p.quartal?.let { Levermann.revision(it.gewinnJetzt, it.gewinnVor30) },
        kurs6 = kurse?.kurs6,
        kurs12 = kurse?.kurs12,
        umkehr = kurse?.umkehr,
        gewinnwachstum = p.mehr.gewinnWachstumFolgejahr.takeIf { it != 0.0 },
        boersenwert = boersenwertEuro(p, quote, fx),
        finanzwert = Qualitaet.ausgenommen(p.sector, p.industry),
    )
}

internal fun lynchEingabe(p: MarketApi.Profile): LynchTyp.Eingabe = LynchTyp.Eingabe(
    gewinne = p.yearly.map { it.earnings },
    umsaetze = p.yearly.map { it.revenue }.filter { it > 0 },
    dividende = p.dividendYield,
    kbv = p.priceToBook,
    zyklisch = BullBear.zyklisch(p.sector, p.industry),
    verschuldung = p.debtToEquity.takeIf { it > 0 }?.let { it * 100.0 },
)

internal fun punkteText(v: Int): String = if (v > 0) "+$v" else if (v < 0) "−${abs(v)}" else "0"

// Zeilen im Abschnitt „Bewertung und Kennzahlen“: Levermann-Punkte (ein Tipp oeffnet die Kriterien) und der Typ nach
// Lynch. jahre und schnitt kommen vom Abschnitt, die Kursgroessen werden hier einmal geholt und gemerkt.
@Composable
internal fun BewertungMethodenZeilen(
    state: AppState, symbol: String, name: String, quote: Quote?, p: MarketApi.Profile,
    jahre: List<Qualitaet.Jahr>?, schnitt: GewinnSchnitt.Ergebnis?, merker: SeitenMerker?,
) {
    val fx by state.market.fxKurse.collectAsStateWithLifecycle()
    var kurse by gemerkt<LevermannKurse?>(merker, "lev/kurse") { null }
    LaunchedEffect(symbol) { if (kurse == null) kurse = levermannKurse(state, symbol, p) }
    var blatt by remember(symbol) { mutableStateOf(false) }
    // Erst mit den Kursen und den Abschluessen, damit die Punktzahl nicht nachtraeglich springt
    val lev = remember(p, quote?.marketCap, quote?.peRatio, jahre, schnitt, kurse, fx) {
        if (kurse == null || jahre == null) null else Levermann.berechne(levermannEingabe(p, quote, jahre, schnitt, kurse, fx))
    }
    if (lev != null && lev.bewertet > 0) {
        val unter = listOfNotNull(
            lev.kaufAb?.let { S.levKaufAbKurz(it) },
            (lev.moeglich - lev.bewertet).takeIf { it > 0 }?.let { S.levOhne(it) },
        ).joinToString(PUNKT).ifEmpty { null }
        AmpelZeile(S.levTitel, Levermann.ampel(lev), S.levSatz(punkteText(lev.punkte), lev.moeglich), unter) { blatt = true }
    }
    val lynch = remember(p) { LynchTyp.berechne(lynchEingabe(p)) }
    if (lynch != null) {
        val wert = when (lynch.grund) {
            LynchTyp.Grund.KBV -> lynch.kbv?.let { Fmt.num(it, 2) }
            LynchTyp.Grund.GEWINNWACHSTUM -> lynch.wachstum?.let { Fmt.signedPct(it, 0) }
            LynchTyp.Grund.UMSATZWACHSTUM -> lynch.umsatzwachstum?.let { Fmt.signedPct(it, 0) }
            else -> null
        }.orEmpty()
        val liste = lynch.pruefliste
        // In der Zeile nur der Grund in Kurzform; Dividende und Pruefliste darunter, kurze Texte stehen ganz da
        val einzelheiten = listOfNotNull(
            lynch.dividende.takeIf { it > 0 && (lynch.typ == LynchTyp.Typ.LANGSAM || lynch.typ == LynchTyp.Typ.STETIG) }?.let { S.lyDividende(Fmt.pct(it, 1)) },
            liste?.verschuldung?.let { S.lySchulden(Fmt.pct(it, 0), liste.schuldenOk == true) },
            S.lyWachstumWarnung.takeIf { liste?.wachstumZuHoch == true },
        ).joinToString("\n")
        val warnt = liste != null && (liste.schuldenOk == false || liste.wachstumZuHoch)
        KeyValueRow(S.lyTitel, S.lyTyp(lynch.typ.name), valueColor = if (warnt) LocalSignal.current.warn else null, hint = S.lyGrund(lynch.grund.name, wert))
        if (einzelheiten.isNotEmpty()) AufklappText(einzelheiten, Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp))
    }
    if (blatt && lev != null) LevermannBlatt(name, lev, p.analystCount) { blatt = false }
}

// Faires KGV und Ertragswert ohne Wachstum im Blatt der Bewertung. Ohne Grundlage bleibt der jeweilige Teil weg.
@Composable
internal fun FairesKgvUndErtragswert(state: AppState, symbol: String, quote: Quote?, profile: MarketApi.Profile?) {
    val p = profile ?: return
    val fx by state.market.fxKurse.collectAsStateWithLifecycle()
    var jahre by remember(symbol) { mutableStateOf<List<Qualitaet.Jahr>?>(null) }
    // Punktzahl der Marktmacht, nur wenn sie eine Stufe hat (ab zwei Bausteinen)
    var marktmacht by remember(symbol) { mutableStateOf<Double?>(null) }
    var geholt by remember(symbol) { mutableStateOf(false) }
    LaunchedEffect(symbol) {
        jahre = state.jahresabschluesse(symbol)
        marktmacht = ohneAbbruch { state.marktmachtFuer(symbol) }.getOrNull()?.takeIf { it.stufe != null }?.punkte
        geholt = true
    }
    val j = jahre ?: return
    if (!geholt) return
    val handel = quote?.nativeCurrency.orEmpty()
    val kurs = quote?.nativePrice ?: 0.0
    // Betraege in der Waehrung, in der die Seite Kurse zeigt
    val ziel = Fmt.anzeigeWaehrung
    val seite = remember(ziel, fx) { SeitenWaehrung(ziel, fx) }

    val kgv = Bewertungsblick.kgv(p.trailingPe, quote?.peRatio)
    val fair = remember(j, p, marktmacht) {
        val ausAbschluss = j.takeLast(5).mapNotNull { it.nettogewinn }
        val gewinne = if (ausAbschluss.size >= FairesKgv.MIN_JAHRE) ausAbschluss else p.yearly.map { it.earnings }
        FairesKgv.berechne(gewinne, marktmacht, p.profitMargin.takeIf { it != 0.0 }, p.returnOnEquity.takeIf { it != 0.0 })
    }
    if (fair != null) {
        SectionHeader(S.fkTitel)
        val abstand = FairesKgv.abstand(kgv, fair.fair)
        AmpelZeile(
            S.levName(Levermann.Kriterium.KGV.name), FairesKgv.ampel(abstand), S.fkSatz("${Fmt.num(fair.fair, 1)}×", kgv?.let { "${Fmt.num(it, 1)}×" }),
            abstand?.let { S.fkAbstand(Fmt.pct(abs(it), 0), it >= 0) }, null,
        )
        // Jede Zeile erklaert sich hinter dem Info-Symbol, mit den Werten, aus denen sie entsteht
        InfoWertZeile(S.fkSockel, Fmt.num(FairesKgv.SOCKEL, 1), S.fkSockel, S.fkSockelInfo)
        InfoWertZeile(
            S.fkStabil, "+${Fmt.num(fair.stabil, 1)}", S.fkStabil,
            S.fkStabilInfo + "\n\n" + S.fkStabilHinweis(fair.schwankung?.let { Fmt.num(it, 2) }, fair.jahre),
        )
        InfoWertZeile(
            S.fkMarkt, "+${Fmt.num(fair.markt, 1)}", S.fkMarkt,
            S.fkMarktInfo + "\n\n" + S.fkMarktHinweis(fair.ausMarktmacht, if (fair.ausMarktmacht) Fmt.num(fair.marktWert, 0) else Fmt.pct(fair.marktWert, 1)),
        )
        InfoWertZeile(
            S.fkRendite, "×${Fmt.num(fair.faktor, 2)}", S.fkRendite,
            S.fkRenditeInfo + "\n\n" + S.fkRenditeHinweis(Fmt.pct(fair.rendite, 1)),
        )
    }

    if (Qualitaet.ausgenommen(p.sector, p.industry)) return
    val aktien = p.mehr.aktien.takeIf { it > 0 } ?: j.lastOrNull()?.aktien
    val ew = remember(j, p, aktien) { Ertragswert.berechne(j, p.totalCash.takeIf { it > 0 }, p.totalDebt.takeIf { it > 0 }, aktien) } ?: return
    val bericht = j.lastOrNull()?.waehrung?.ifBlank { null } ?: p.financialCurrency.ifBlank { handel }
    // Ertragswert und Kurs in der Waehrung der Seite; ohne Wechselkurs bleibt jeder in seiner Waehrung und der Vergleich
    // entfaellt, wenn beide dann verschieden sind
    val (wert, waehrung) = seite.umrechnen(ew.jeAktie, bericht)
    val (kursSeite, kursWaehrung) = seite.umrechnen(kurs, handel)
    val vergleichbar = waehrung == kursWaehrung && kurs > 0
    SectionHeader(S.ewTitel)
    if (ew.jeAktie <= 0) AmpelZeile(S.ewJeAktie, Bewertungsblick.Ampel.ROT, S.ewNegativ, null, null)
    else if (vergleichbar) {
        val abstand = Ertragswert.abstand(kursSeite, wert) ?: 0.0
        AmpelZeile(S.ewKurs, Ertragswert.ampel(kursSeite, wert), S.ewSatz(Fmt.pct(abs(abstand), 0), abstand >= 0), S.ewUnter(abstand >= 0), null)
    }
    InfoWertZeile(S.ewJeAktie, seite.preis(ew.jeAktie, bericht), S.ewJeAktie, S.ewJeAktieHinweis(Fmt.pct(Ertragswert.KAPITALKOSTEN, 0)))
    if (vergleichbar) KeyValueRow(S.ewKurs, seite.preis(kurs, handel))
    InfoWertZeile(
        S.ewGewinn, seite.kompakt(ew.gewinn, bericht), S.ewGewinn,
        S.ewGewinnHinweis(Fmt.pct(ew.marge, 1), ew.jahre, Fmt.pct(ew.steuersatz * 100.0, 0)),
    )
    Ertragswert.buchwertJeAktie(j, aktien)?.takeIf { it > 0 }?.let { buch ->
        val ueber = if (ew.jeAktie > 0) (ew.jeAktie / buch - 1.0) * 100.0 else null
        KeyValueRow(S.ewBuchwert, seite.preis(buch, bericht), hint = ueber?.let { S.ewBuchwertHinweis(Fmt.pct(abs(it), 0), it >= 0) })
    }
}
