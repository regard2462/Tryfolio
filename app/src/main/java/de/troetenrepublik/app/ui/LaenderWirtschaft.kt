package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.engine.LaenderMarkt
import de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl
import de.troetenrepublik.app.engine.Weltbank
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.ohneAbbruch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private fun p1(v: Double?) = if (v == null) null else Fmt.pct(v, 1)
private fun sp1(v: Double?) = if (v == null) null else Fmt.signedPct(v, 1)

// Abschnitt Wirtschaft und Markt im Laenderblatt: Zahlen des IWF mit Einordnung, Wirtschaftsstruktur (Weltbank), Markt
// (Leitzins, Rendite, Waehrung, Laender-ETF). Jede Kennzahl oeffnet beim Antippen ihre Erklaerung.
@Composable
internal fun LaenderWirtschaft(state: AppState, land: GlobusLand, z: Imf.Land?, jahr: Int, onOpen: (String) -> Unit) {
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var struktur by remember(land) { mutableStateOf<LaenderMarkt.Struktur?>(null) }
    var markt by remember(land) { mutableStateOf<LaenderMarkt.Markt?>(null) }
    var struktGeladen by remember(land) { mutableStateOf(false) }
    var marktGeladen by remember(land) { mutableStateOf(false) }
    LaunchedEffect(land) {
        struktur = ohneAbbruch { state.globusStruktur(land.a3) }.getOrNull()
        struktGeladen = true
    }
    LaunchedEffect(land) {
        markt = ohneAbbruch { state.globusMarkt(land.a2) }.getOrNull()
        marktGeladen = true
    }
    val vorher = remember(land) { state.imfGespeichert()?.let { Imf.land(it, land.a3, jahr - 1) } }
    val signal = LocalSignal.current

    fun zeigen(k: Kennzahl, titel: String = S.globusWirtLabel(k), vorjahr: String? = null) {
        info = titel to (S.globusWirtInfo(k) + (vorjahr?.let { "\n\n$it" } ?: ""))
    }

    val imfZeigen = z != null && !z.leer
    if (imfZeigen) {
        Abschnitt(S.globusWirtschaft)
        z!!
        sp1(z.wachstum)?.let { v ->
            KeyValueRow(S.globusWachstumJahr(jahr), v, onClick = { zeigen(Kennzahl.WACHSTUM, S.globusWachstumJahr(jahr), sp1(vorher?.wachstum)?.let { S.globusVorjahr(jahr - 1, it) }) })
        }
        sp1(z.wachstumNaechstes)?.let { v ->
            KeyValueRow(S.globusWachstumJahr(jahr + 1), v, onClick = { zeigen(Kennzahl.WACHSTUM, S.globusWachstumJahr(jahr + 1), sp1(z.wachstum)?.let { S.globusVorjahr(jahr, it) }) })
        }
        p1(z.inflation)?.let { v ->
            KeyValueRow(S.globusInflationJahr(jahr), v, onClick = { zeigen(Kennzahl.INFLATION, S.globusInflationJahr(jahr), p1(vorher?.inflation)?.let { S.globusVorjahr(jahr - 1, it) }) })
        }
        p1(z.arbeitslosigkeit)?.let { v ->
            KeyValueRow(S.globusArbeitslos, v, onClick = { zeigen(Kennzahl.ARBEITSLOS, vorjahr = p1(vorher?.arbeitslosigkeit)?.let { S.globusVorjahr(jahr - 1, it) }) })
        }
        p1(z.schulden)?.let { v ->
            KeyValueRow(S.globusSchulden, v, onClick = { zeigen(Kennzahl.SCHULDEN, vorjahr = p1(vorher?.schulden)?.let { S.globusVorjahr(jahr - 1, it) }) })
        }
        sp1(z.haushalt)?.let { v ->
            KeyValueRow(S.globusWirtLabel(Kennzahl.HAUSHALT), v, onClick = { zeigen(Kennzahl.HAUSHALT, vorjahr = sp1(vorher?.haushalt)?.let { S.globusVorjahr(jahr - 1, it) }) })
        }
        sp1(z.leistungsbilanz)?.let { v ->
            KeyValueRow(S.globusLeistung, v, onClick = { zeigen(Kennzahl.LEISTUNG, vorjahr = sp1(vorher?.leistungsbilanz)?.let { S.globusVorjahr(jahr - 1, it) }) })
        }
        z.bipKopf?.let { v ->
            KeyValueRow(S.globusWirtLabel(Kennzahl.BIP_KOPF), Fmt.moneyIn(v, "$", 0), onClick = {
                zeigen(Kennzahl.BIP_KOPF, vorjahr = vorher?.bipKopf?.let { S.globusVorjahr(jahr - 1, Fmt.moneyIn(it, "$", 0)) })
            })
        }
        z.bipKopfKkp?.let { v ->
            KeyValueRow(S.globusWirtLabel(Kennzahl.BIP_KOPF_KKP), Fmt.moneyIn(v, "Int.-$", 0), onClick = {
                zeigen(Kennzahl.BIP_KOPF_KKP, vorjahr = vorher?.bipKopfKkp?.let { S.globusVorjahr(jahr - 1, Fmt.moneyIn(it, "Int.-$", 0)) })
            })
        }
        z.weltAnteil?.let { v ->
            KeyValueRow(S.globusWirtLabel(Kennzahl.WELT_ANTEIL), Fmt.pct(v, 2), onClick = {
                zeigen(Kennzahl.WELT_ANTEIL, vorjahr = vorher?.weltAnteil?.let { S.globusVorjahr(jahr - 1, Fmt.pct(it, 2)) })
            })
        }
        z.bevoelkerung?.let { v ->
            KeyValueRow(S.globusWirtLabel(Kennzahl.BEVOELKERUNG), S.globusMio(Fmt.num(v, 1)), onClick = {
                zeigen(Kennzahl.BEVOELKERUNG, vorjahr = vorher?.bevoelkerung?.let { S.globusVorjahr(jahr - 1, S.globusMio(Fmt.num(it, 1))) })
            })
        }
        for (h in Imf.einordnen(z)) Text(
            "• " + S.globusHinweis(h.art, hinweisZahl(h.art, h.a), hinweisZahl(h.art, h.b)),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
        )
    }

    // Wirtschaftsstruktur (Weltbank)
    val s = struktur
    if (!struktGeladen) {
        Abschnitt(S.globusStruktur)
        Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    } else if (s != null && s.werte.isNotEmpty()) {
        fun text(k: Kennzahl, w: Weltbank.Wert, fmt: (Double) -> String): String {
            val t = fmt(w.wert)
            return if (LaenderMarkt.jahrZeigen(w, s.ueblich[k], jahr)) "$t (${w.jahr})" else t
        }
        val zweige = listOf(Kennzahl.AGRAR, Kennzahl.INDUSTRIE, Kennzahl.VERARBEITUNG, Kennzahl.DIENSTE).filter { it in s.werte }
        Abschnitt(S.globusStruktur)
        if (zweige.isNotEmpty()) Unterueberschrift(S.globusZweige)
        for (k in zweige) KeyValueRow(S.globusWirtLabel(k), text(k, s.werte[k]!!) { Fmt.pct(it, 1) }, onClick = { zeigen(k) })
        for (k in listOf(Kennzahl.EXPORTE, Kennzahl.TREIBSTOFF, Kennzahl.ROHSTOFF, Kennzahl.HIGHTECH, Kennzahl.DIREKTINVEST, Kennzahl.BOERSENWERT)) {
            val w = s.werte[k] ?: continue
            KeyValueRow(S.globusWirtLabel(k), text(k, w) { Fmt.pct(it, 1) }, onClick = { zeigen(k) })
        }
        s.werte[Kennzahl.FIRMEN]?.let { w -> KeyValueRow(S.globusWirtLabel(Kennzahl.FIRMEN), text(Kennzahl.FIRMEN, w) { Fmt.num(it, 0) }, onClick = { zeigen(Kennzahl.FIRMEN) }) }
        s.werte[Kennzahl.GINI]?.let { w -> KeyValueRow(S.globusWirtLabel(Kennzahl.GINI), text(Kennzahl.GINI, w) { Fmt.num(it, 1) }, onClick = { zeigen(Kennzahl.GINI) }) }
    }

    // Markt
    val m = markt
    val code = LaenderMarkt.waehrungsCode(land.a2)
    val zinsWb = LaenderMarkt.ZINS_WELTBANK.filter { s?.werte?.containsKey(it) == true }
    if (!marktGeladen) {
        Abschnitt(S.globusMarkt)
        Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
    } else if (zinsWb.isNotEmpty() || (m != null && (m.zins != null || m.rendite != null || m.kurs != null || m.etfs.isNotEmpty() || code == "EUR"))) {
        Abschnitt(S.globusMarkt)
        m?.zins?.let { zi ->
            val alt = java.time.temporal.ChronoUnit.DAYS.between(zi.datum, LocalDate.now()) > LaenderMarkt.ZINS_ALT_TAGE
            val text = Fmt.pct(zi.wert, 2) + if (alt) " (${Fmt.date(zi.datum.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())})" else ""
            KeyValueRow(S.globusWirtLabel(Kennzahl.LEITZINS), text, onClick = { zeigen(Kennzahl.LEITZINS) })
        }
        for (k in zinsWb) {
            val w = s?.werte?.get(k) ?: continue
            val t = if (k == Kennzahl.REALZINS) Fmt.signedPct(w.wert, 2) else Fmt.pct(w.wert, 2)
            KeyValueRow(S.globusWirtLabel(k), if (LaenderMarkt.jahrZeigen(w, s?.ueblich?.get(k), jahr)) "$t (${w.jahr})" else t, onClick = { zeigen(k) })
        }
        m?.rendite?.let { (k, r) ->
            val text = Fmt.pct(r.wert, 2) + (r.monat?.let { " (${Fmt.monthShortYear(it)})" } ?: "")
            KeyValueRow(S.globusWirtLabel(k), text, onClick = { zeigen(k) })
        }
        if (code == "EUR") {
            KeyValueRow(S.globusWirtLabel(Kennzahl.WAEHRUNG_EURO), "Euro", onClick = { zeigen(Kennzahl.WAEHRUNG_EURO) })
        } else if (code != null && m?.kurs != null) {
            KeyValueRow(S.globusWirtLabel(Kennzahl.WAEHRUNG), S.globusKurs(code, Fmt.num(m.kurs, if (m.kurs >= 100) 2 else 4)), onClick = { zeigen(Kennzahl.WAEHRUNG) })
            m.kursJahr?.let { v ->
                KeyValueRow(
                    S.globusWaehrungJahrLabel(code), Fmt.signedPct(v, 1), valueColor = if (v >= 0) signal.up else signal.down,
                    onClick = { zeigen(Kennzahl.WAEHRUNG_JAHR, S.globusWaehrungJahrLabel(code)) },
                )
            }
        }
        if (m != null && m.etfs.isNotEmpty()) {
            Unterueberschrift(S.globusEtf)
            for (e in m.etfs) EtfZeile(e, signal.up, signal.down) { onOpen(e.etf.symbol) }
            Text(
                S.globusEtfHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
    if (imfZeigen || (s != null && s.werte.isNotEmpty()) || (m != null)) Text(
        S.globusWirtQuelle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

@Composable
private fun Abschnitt(titel: String) {
    Text(titel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 2.dp))
}

@Composable
private fun Unterueberschrift(titel: String) {
    Text(
        titel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 2.dp),
    )
}

// Zeile eines Laender-ETF: Name und Kuerzel links, Aenderung heute und ueber ein Jahr rechts; Tippen oeffnet das Papier.
@Composable
private fun EtfZeile(e: LaenderMarkt.EtfStand, up: Color, down: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.etf.name, style = MaterialTheme.typography.bodyMedium)
            Text(e.etf.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            e.tag?.let { Aenderung(S.globusHeute, it, up, down, 2) }
            e.jahr?.let { Aenderung(S.globusEinJahr, it, up, down, 1) }
        }
    }
}

@Composable
private fun Aenderung(label: String, v: Double, up: Color, down: Color, stellen: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        Text(
            Fmt.signedPct(v, stellen), style = MaterialTheme.typography.bodyMedium, color = if (v >= 0) up else down,
            maxLines = 1, softWrap = false,
        )
    }
}

// Zahl im Satz der Einordnung: Bilanzen ohne Vorzeichen (der Satz sagt Ueberschuss oder Defizit), sonst wie berechnet.
private fun hinweisZahl(art: Imf.Art, v: Double): String =
    if (art == Imf.Art.UEBERSCHUSS || art == Imf.Art.DEFIZIT) Fmt.pct(abs(v), 1) else Fmt.pct(v, 1)
