package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.KryptoHintergrund
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.KryptoHgTexte
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

// Hintergrund zu Kryptowerten (0.78): Finanzierungssatz und offene Kontrakte der unbefristeten Kontrakte und das in
// DeFi gebundene Kapital der Kette auf der Seite des Werts, Umlauf der Stablecoins und gebundenes Kapital gesamt
// unter „Krypto“ bei Entdecken. Je Kennzahl eine Kernzeile, aufgeklappt der Verlauf, die Erklaerung hinter dem
// Info-Symbol.

internal fun kyText(key: String, vararg w: String): String = KryptoHgTexte.text(I18n.lang == Lang.DE, key, w.toList())

private fun dollar(v: Double): String = Fmt.mitSymbol(Fmt.compact(v), "$")

internal val LLAMA_QUELLE = QuelleLink(KryptoHintergrund.LLAMA, KryptoHintergrund.LLAMA_SEITE)

private fun boersenQuelle(name: String) =
    QuelleLink(name, if (name == KryptoHintergrund.KRAKEN) KryptoHintergrund.KRAKEN_SEITE else KryptoHintergrund.DERIBIT_SEITE)

// Verlauf mit einer Zeile darunter, die sagt, was die Linie zeigt.
@Composable
private fun VerlaufMitZeile(punkte: List<Pair<Long, Double>>, farbe: Color, format: (Double) -> String, zeile: String) {
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 10.dp)) {
        VerlaufDiagramm(punkte, farbe, format, fuellen = true)
        Text(zeile, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

// Kennzahl in Dollar mit Verlauf ueber ein Jahr: Kernzeile mit Stand und Veraenderung in 30 Tagen, aufgeklappt die Linie.
private fun reihenModell(titel: String, reihe: List<Pair<Long, Double>>, info: String, farbe: Color, gross: Boolean): ListenModell? {
    val stand = reihe.lastOrNull()?.second ?: return null
    val d = KryptoHintergrund.veraenderung(reihe)
    val kern = KernZeile(listOfNotNull(KernTeil(dollar(stand)), d?.let { KernTeil(kyText("tage30", Fmt.pct(abs(it), 1)), it) }))
    val zeilen = if (reihe.size < 2) emptyList() else listOf(ZeilenDaten("verlauf") { VerlaufMitZeile(reihe, farbe, ::dollar, kyText("jahr")) })
    return ListenModell(titel, gross, kern, zeilen, immerZu = true, info = info)
}

// ------------------------------------------------------------ Seite eines Kryptowerts

// Finanzierungssatz und offene Kontrakte: laedt beim Aufklappen des Hintergrunds, ohne Kontrakt an den Boersen
// erscheint nichts.
@Composable
internal fun KryptoFinanzierungKarte(state: AppState, symbol: String, merker: SeitenMerker?) {
    var daten by gemerkt<KryptoHintergrund.Finanzierung?>(merker, "kyf/$symbol/daten") { null }
    var geladen by gemerkt(merker, "kyf/$symbol/geladen") { false }
    LaunchedEffect(symbol) {
        if (geladen) return@LaunchedEffect
        daten = state.krypto.finanzierung(symbol)
        geladen = true
    }
    val f = daten
    if (f != null) QuellenMelden(boersenQuelle(f.quelle))
    val farbe = MaterialTheme.colorScheme.primary
    val modell = remember(f, farbe, I18n.lang) {
        if (f == null) null else {
            val unten = f.offenDollar?.let { KernTeil(kyText("fin.offen.kern", dollar(it))) }
            val kern = KernZeile(listOf(KernTeil(kyText("fin.satz8h", Fmt.signedPct(f.satz8h, 3))), KernTeil(kyText("fin.jahr", Fmt.signedPct(f.jahr, 1)))), unten)
            val zeilen = buildList {
                if (f.verlauf.size >= 2) add(ZeilenDaten("verlauf") { VerlaufMitZeile(f.verlauf, farbe, { Fmt.signedPct(it, 1) }, kyText("fin.verlauf")) })
                f.offenDollar?.let { o -> add(ZeilenDaten("offen") { KeyValueRow(kyText("fin.offen"), dollar(o), hint = kyText("fin.kontrakt", f.quelle)) }) }
            }
            ListenModell(kyText("fin.titel"), true, kern, zeilen, immerZu = true, info = kyText("fin.info"))
        }
    }
    ModellAusgabe(merker, "kyf", modell)
}

// In DeFi gebundenes Kapital der Kette, nur fuer Werte, die selbst eine Kette sind.
@Composable
internal fun KryptoKetteKarte(state: AppState, symbol: String, name: String, merker: SeitenMerker?) {
    val kette = remember(symbol) { KryptoHintergrund.kette(KryptoHintergrund.basis(symbol)) } ?: return
    var reihe by gemerkt<List<Pair<Long, Double>>?>(merker, "kyk/$symbol/daten") { null }
    var geladen by gemerkt(merker, "kyk/$symbol/geladen") { false }
    LaunchedEffect(symbol) {
        if (geladen) return@LaunchedEffect
        reihe = state.krypto.tvl(kette)
        geladen = true
    }
    val r = reihe
    if (!r.isNullOrEmpty()) QuellenMelden(LLAMA_QUELLE)
    val farbe = MaterialTheme.colorScheme.primary
    val modell = remember(r, farbe, name, I18n.lang) { r?.let { reihenModell(kyText("tvl.kette", name), it, kyText("tvl.info"), farbe, gross = true) } }
    ModellAusgabe(merker, "kyk", modell)
}

// Kernaussage fuer die Unterzeile des Hintergrunds: Finanzierungssatz aufs Jahr und Veraenderung des gebundenen Kapitals der
// Kette in 30 Tagen, je nachdem, was es gibt; null ohne beides.
internal suspend fun kryptoHintergrundKern(state: AppState, symbol: String): String? = coroutineScope {
    val kette = KryptoHintergrund.kette(KryptoHintergrund.basis(symbol))
    val f = async { state.krypto.finanzierung(symbol) }
    val t = async { kette?.let { state.krypto.tvl(it) } }
    val teile = listOfNotNull(
        f.await()?.let { kyText("hg.fin", Fmt.signedPct(it.jahr, 1)) },
        t.await()?.let { KryptoHintergrund.veraenderung(it) }?.let { kyText("hg.tvl", Fmt.signedPct(it, 1)) },
    )
    if (teile.isEmpty()) null else teile.joinToString(" · ")
}

internal fun LazyListScope.kryptoHintergrundAbschnitte(merker: SeitenMerker) {
    listenAbschnitt(merker, "kyf")
    listenAbschnitt(merker, "kyk")
}

// ------------------------------------------------------------ Krypto bei Entdecken

// Umlauf der Stablecoins und gebundenes Kapital gesamt; null, solange nichts geladen ist.
internal class KryptoMarkt(val stable: List<Pair<Long, Double>>?, val tvl: List<Pair<Long, Double>>?) {
    val vorhanden: Boolean get() = !stable.isNullOrEmpty() || !tvl.isNullOrEmpty()
}

internal suspend fun kryptoMarktLaden(state: AppState): KryptoMarkt = coroutineScope {
    val s = async { state.krypto.stablecoins() }
    val t = async { state.krypto.tvl() }
    KryptoMarkt(s.await(), t.await())
}

@Composable
internal fun KryptoMarktKarten(markt: KryptoMarkt) {
    val farbe = MaterialTheme.colorScheme.primary
    val stable = remember(markt, farbe, I18n.lang) { markt.stable?.let { reihenModell(kyText("st.titel"), it, kyText("st.info"), farbe, gross = false) } }
    val tvl = remember(markt, farbe, I18n.lang) { markt.tvl?.let { reihenModell(kyText("tvl.titel"), it, kyText("tvl.info"), farbe, gross = false) } }
    Column {
        ModellSpalte(stable, "kys")
        ModellSpalte(tvl, "kyt")
    }
}
