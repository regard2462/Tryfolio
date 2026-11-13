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
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.engine.LaenderMarkt
import de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl
import de.troetenrepublik.app.engine.LaenderZusatz
import de.troetenrepublik.app.engine.Weltbank
import de.troetenrepublik.app.i18n.S
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private fun p1(v: Double?) = if (v == null) null else Fmt.pct(v, 1)
private fun sp1(v: Double?) = if (v == null) null else Fmt.signedPct(v, 1)

// [0.78] Dollarbetrag der Quelle (IWF) in der Anzeigewaehrung, ohne Nachkommastellen; ohne Wechselkurs bleibt er in Dollar.
private fun ausDollar(v: Double): String = Fmt.ausDollar(v).let { (x, zeichen) -> Fmt.moneyIn(x, zeichen, 0) }

// Zusatz zur Erklaerung eines umgerechneten Dollarbetrags; null, solange in Dollar angezeigt wird.
private fun umgerechnetHinweis(): String? =
    if (Fmt.anzeigeWaehrung != "USD" && Fmt.usdJeEuro > 0) S.lbUmgerechnet(S.waehrungName(Fmt.anzeigeWaehrung)) else null

// Texte der Waehrungsangaben zur Vergleichswaehrung (Heimatwaehrung des Nutzers); zum Euro die bisherigen Texte
private fun waehrungZu(code: String, gegen: String): String = if (gegen == "EUR") S.lbKopfZumEuro(code) else S.ml("vw.zu", listOf(code, gegen))

private fun waehrungJahrLabel(code: String, gegen: String): String =
    if (gegen == "EUR") S.globusWaehrungJahrLabel(code) else S.ml("vw.zu.jahr", listOf(code, gegen))

private fun waehrungKurs(code: String, gegen: String, kurs: Double): String {
    val k = Fmt.num(kurs, if (kurs >= 100) 2 else 4)
    return if (gegen == "EUR") S.globusKurs(code, k) else S.ml("vw.kurs", listOf(gegen, k, code))
}

private fun waehrungKursInfo(gegen: String): String = if (gegen == "EUR") S.globusWirtInfo(Kennzahl.WAEHRUNG) else S.ml("vw.info.kurs", listOf(gegen))

private fun waehrungJahrInfo(gegen: String): String = if (gegen == "EUR") S.globusWirtInfo(Kennzahl.WAEHRUNG_JAHR) else S.ml("vw.info.jahr", listOf(gegen))

// [Laenderblatt 0.70] Kopf des Blatts: die wichtigsten Kennzahlen als Kacheln (IWF, Markt, Weltbank), ohne Umbruch.
// Jede Kachel oeffnet beim Antippen die Erklaerung samt Vorjahr.
@Composable
internal fun LaenderKopf(land: GlobusLand, z: Imf.Land?, imf: Imf.Datensatz?, jahr: Int, g: LaenderGeladen) {
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val signal = LocalSignal.current
    val vorher = remember(land, imf) { imf?.let { Imf.land(it, land.a3, jahr - 1) } }
    fun zeigen(k: Kennzahl, titel: String = S.globusWirtLabel(k), vorjahr: String? = null, davor: String? = null) {
        info = titel to ((davor?.let { "$it\n\n" } ?: "") + S.globusWirtInfo(k) + (vorjahr?.let { "\n\n$it" } ?: ""))
    }
    // Jahr unter dem Wert: das gewaehlte Jahr, bei einem Ersatzwert (Weltbank, UN, Factbook) das Jahr des Werts
    fun jz(kennung: String): String = (imf?.let { Imf.herkunft(it, kennung, land.a3, jahr)?.jahr } ?: jahr).toString()
    val kacheln = ArrayList<KachelDaten>()
    if (z != null && !z.leer) {
        z.wachstum?.let { v ->
            kacheln += KachelDaten(S.globusWachstum, Fmt.signedPct(v, 1), jz(Imf.WACHSTUM), if (v >= 0) signal.up else signal.down) {
                zeigen(Kennzahl.WACHSTUM, S.globusWachstumJahr(jahr), sp1(vorher?.wachstum)?.let { S.globusVorjahr(jahr - 1, it) })
            }
        }
        z.inflation?.let { v ->
            kacheln += KachelDaten(S.globusInflation, Fmt.pct(v, 1), jz(Imf.INFLATION)) {
                zeigen(Kennzahl.INFLATION, S.globusInflationJahr(jahr), p1(vorher?.inflation)?.let { S.globusVorjahr(jahr - 1, it) })
            }
        }
        z.arbeitslosigkeit?.let { v ->
            kacheln += KachelDaten(S.lbKopfArbeitslos, Fmt.pct(v, 1), jz(Imf.ARBEITSLOS)) {
                zeigen(Kennzahl.ARBEITSLOS, vorjahr = p1(vorher?.arbeitslosigkeit)?.let { S.globusVorjahr(jahr - 1, it) })
            }
        }
        z.schulden?.let { v ->
            kacheln += KachelDaten(S.lbKopfSchulden, Fmt.pct(v, 0), jz(Imf.SCHULDEN)) {
                zeigen(Kennzahl.SCHULDEN, vorjahr = p1(vorher?.schulden)?.let { S.globusVorjahr(jahr - 1, it) })
            }
        }
        z.bipKopf?.let { v ->
            kacheln += KachelDaten(S.lbKopfBipKopf, ausDollar(v), jz(Imf.BIP_KOPF)) {
                val vorjahr = vorher?.bipKopf?.let { S.globusVorjahr(jahr - 1, ausDollar(it)) }
                zeigen(Kennzahl.BIP_KOPF, vorjahr = listOfNotNull(umgerechnetHinweis(), vorjahr).joinToString("\n\n").takeIf { it.isNotEmpty() })
            }
        }
        z.bevoelkerung?.let { v ->
            val text = if (v >= 1000) S.lbMrd(Fmt.num(v / 1000, 2)) else S.globusMio(Fmt.num(v, 1))
            kacheln += KachelDaten(S.lbKopfEinwohner, text, jz(Imf.BEVOELKERUNG)) {
                zeigen(Kennzahl.BEVOELKERUNG, vorjahr = vorher?.bevoelkerung?.let { S.globusVorjahr(jahr - 1, S.globusMio(Fmt.num(it, 1))) })
            }
        }
    }
    // Waehrung zur Heimatwaehrung des Nutzers, wie im Abschnitt "Wert der Waehrung"; mit der Heimatwaehrung selbst nur der Name
    val code = LaenderMarkt.waehrungsCode(land.a2)
    val gegen = LaenderMarkt.vergleichsCode(code, LaenderMarkt.waehrungsCode(de.troetenrepublik.app.engine.Regionen.aktuell))
    val m = g.markt
    if (code != null && gegen == null) {
        if (code == "EUR") kacheln += KachelDaten(S.lbKopfWaehrung, "Euro", null) { zeigen(Kennzahl.WAEHRUNG_EURO) }
        else kacheln += KachelDaten(S.lbKopfWaehrung, code, null)
    } else if (code != null && gegen != null && m?.kurs != null && m.kursJahr != null && m.gegen == gegen) {
        val v = m.kursJahr
        kacheln += KachelDaten(S.lbKopfWaehrung, Fmt.signedPct(v, 1), waehrungZu(code, gegen), if (v >= 0) signal.up else signal.down) {
            info = waehrungJahrLabel(code, gegen) to (waehrungKurs(code, gegen, m.kurs) + "\n\n" + waehrungJahrInfo(gegen))
        }
    }
    m?.zins?.let { zi ->
        val alt = java.time.temporal.ChronoUnit.DAYS.between(zi.datum, LocalDate.now()) > LaenderMarkt.ZINS_ALT_TAGE
        val datum = if (alt) Fmt.date(zi.datum.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) else null
        kacheln += KachelDaten(S.lbKopfLeitzins, Fmt.pct(zi.wert, 2), datum) { zeigen(Kennzahl.LEITZINS) }
    }
    g.mehr?.let { md ->
        md.reihen[LaenderZusatz.Kennzahl.LEBENSERWARTUNG.kennung]?.werte?.get(land.a3)?.let { w ->
            val titel = S.globusMehrLabel(LaenderZusatz.Kennzahl.LEBENSERWARTUNG)
            kacheln += KachelDaten(S.lbKopfLeben, S.lbJahreKurz(Fmt.num(w.wert, 1)), "${w.jahr}") { info = titel to S.globusMehrInfo(LaenderZusatz.Kennzahl.LEBENSERWARTUNG) }
        }
    }
    KachelRaster(kacheln, 3)
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Abschnitt Wirtschaft: Verlauf von Wachstum und Inflation als Balken, dazu die uebrigen Zahlen des IWF und die Einordnung.
@Composable
internal fun LaenderWirtschaft(land: GlobusLand, z: Imf.Land?, imf: Imf.Datensatz?, jahr: Int) {
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val signal = LocalSignal.current
    val vorher = remember(land, imf) { imf?.let { Imf.land(it, land.a3, jahr - 1) } }
    fun zeigen(k: Kennzahl, titel: String = S.globusWirtLabel(k), vorjahr: String? = null) {
        info = titel to (S.globusWirtInfo(k) + (vorjahr?.let { "\n\n$it" } ?: ""))
    }
    fun reihe(kennung: String) = imf?.werte?.get(kennung)?.get(land.a3)?.toSortedMap()?.entries?.map { it.key to it.value }.orEmpty()
    val wachstum = remember(imf, land) { reihe(Imf.WACHSTUM) }
    val inflation = remember(imf, land) { reihe(Imf.INFLATION) }
    val imfZeigen = z != null && !z.leer
    if (!imfZeigen && wachstum.size < 3 && inflation.size < 3) return
    LaenderAbschnitt(S.globusWirtschaft, "wirtschaft") {
        JahresBalken(
            S.lbVerlaufWachstum, wachstum, jahr, { if (it >= 0) signal.up else signal.down }, { Fmt.signedPct(it, 1) },
            onClick = { zeigen(Kennzahl.WACHSTUM) },
        )
        JahresBalken(S.lbVerlaufInflation, inflation, jahr, { Kategorie.ocker }, { Fmt.pct(it, 1) }, onClick = { zeigen(Kennzahl.INFLATION) })
        if (z != null && imfZeigen) {
            sp1(z.haushalt)?.let { v ->
                LbZeile(S.globusWirtLabel(Kennzahl.HAUSHALT), v) { zeigen(Kennzahl.HAUSHALT, vorjahr = sp1(vorher?.haushalt)?.let { S.globusVorjahr(jahr - 1, it) }) }
            }
            sp1(z.leistungsbilanz)?.let { v ->
                LbZeile(S.globusLeistung, v) { zeigen(Kennzahl.LEISTUNG, vorjahr = sp1(vorher?.leistungsbilanz)?.let { S.globusVorjahr(jahr - 1, it) }) }
            }
            z.bipKopfKkp?.let { v ->
                LbZeile(S.globusWirtLabel(Kennzahl.BIP_KOPF_KKP), Fmt.moneyIn(v, S.kkpDollar, 0)) {
                    zeigen(Kennzahl.BIP_KOPF_KKP, vorjahr = vorher?.bipKopfKkp?.let { S.globusVorjahr(jahr - 1, Fmt.moneyIn(it, S.kkpDollar, 0)) })
                }
            }
            z.weltAnteil?.let { v ->
                LbZeile(S.globusWirtLabel(Kennzahl.WELT_ANTEIL), Fmt.pct(v, 2)) {
                    zeigen(Kennzahl.WELT_ANTEIL, vorjahr = vorher?.weltAnteil?.let { S.globusVorjahr(jahr - 1, Fmt.pct(it, 2)) })
                }
            }
            for (h in Imf.einordnen(z)) LbStichpunkt(S.globusHinweis(h.art, hinweisZahl(h.art, h.a), hinweisZahl(h.art, h.b)))
        }
        QuellenLeiste(listOf(LaenderQuellen.iwf(land.a3)))
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Abschnitt Wirtschaftsstruktur (Weltbank): Ring der drei Sektoren, darunter selteneres aufklappbar.
@Composable
internal fun LaenderStruktur(land: GlobusLand, jahr: Int, g: LaenderGeladen) {
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val s = g.struktur
    if (!g.struktGeladen) {
        LaenderAbschnitt(S.globusStruktur, "struktur") {
            Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        return
    }
    if (s == null || s.werte.isEmpty()) return
    fun text(k: Kennzahl, w: Weltbank.Wert, mitJahr: Boolean = true, fmt: (Double) -> String): String {
        val t = fmt(w.wert)
        return if (mitJahr && LaenderMarkt.jahrZeigen(w, s.ueblich[k], jahr)) "$t (${w.jahr})" else t
    }
    fun zeigen(k: Kennzahl) { info = S.globusWirtLabel(k) to S.globusWirtInfo(k) }
    val zweige = listOf(Kennzahl.AGRAR, Kennzahl.INDUSTRIE, Kennzahl.DIENSTE).filter { it in s.werte }
    // Der Gini-Index steht im Abschnitt Ranglisten; nur ohne Platz dort bleibt er hier
    var rang by remember { mutableStateOf(WaehrungLader.gespeichert()?.rang) }
    LaunchedEffect(Unit) { if (WaehrungLader.gespeichert() == null) rang = WaehrungLader.laden().rang }
    val giniInRangliste = remember(rang, land) { rang?.let { de.troetenrepublik.app.engine.Ranglisten.platz(it, de.troetenrepublik.app.engine.Ranglisten.Index.GINI, land.a3) } != null }
    val weitere = listOf(Kennzahl.EXPORTE, Kennzahl.TREIBSTOFF, Kennzahl.ROHSTOFF, Kennzahl.HIGHTECH, Kennzahl.DIREKTINVEST, Kennzahl.BOERSENWERT, Kennzahl.FIRMEN, Kennzahl.GINI)
        .filter { it in s.werte && !(it == Kennzahl.GINI && giniInRangliste) }
    LaenderAbschnitt(S.globusStruktur, "struktur") {
        if (zweige.isNotEmpty()) {
            // Gilt fuer alle Zeilen des Rings dasselbe Jahr, steht es einmal darunter statt in jeder Zeile
            val imRing = zweige + listOfNotNull(Kennzahl.VERARBEITUNG.takeIf { it in s.werte })
            val gemeinsam = de.troetenrepublik.app.engine.Blattaufbau.gemeinsamesJahr(
                imRing.map { k -> s.werte[k]!!.let { w -> w.jahr.takeIf { LaenderMarkt.jahrZeigen(w, s.ueblich[k], jahr) } } },
            )
            val jeZeile = gemeinsam == null
            val teile = ArrayList<RingTeil>()
            zweige.forEachIndexed { i, k ->
                val w = s.werte[k]!!
                teile += RingTeil(S.globusWirtLabel(k), w.wert, Kategorie.farbe(i), text(k, w, jeZeile) { Fmt.pct(it, 1) }) { zeigen(k) }
            }
            val rest = 100.0 - teile.sumOf { it.wert }
            if (rest >= 2.0) teile += RingTeil(S.lbUebrige, rest, MaterialTheme.colorScheme.outlineVariant, Fmt.pct(rest, 1))
            RingDiagramm(teile)
            s.werte[Kennzahl.VERARBEITUNG]?.let { w -> LbZeile(S.globusWirtLabel(Kennzahl.VERARBEITUNG), text(Kennzahl.VERARBEITUNG, w, jeZeile) { Fmt.pct(it, 1) }) { zeigen(Kennzahl.VERARBEITUNG) } }
            gemeinsam?.let { LbHinweis(S.lbStandJahr(it)) }
        }
        if (weitere.isNotEmpty()) LaenderAbschnitt(S.lbWirtWeitere, "struktur-weitere", startOffen = false, tief = true) {
            for (k in weitere) {
                val w = s.werte[k] ?: continue
                val t = if (k == Kennzahl.FIRMEN) text(k, w) { Fmt.num(it, 0) } else if (k == Kennzahl.GINI) text(k, w) { Fmt.num(it, 1) } else text(k, w) { Fmt.pct(it, 1) }
                LbZeile(S.globusWirtLabel(k), t) { zeigen(k) }
            }
        }
        QuellenLeiste(listOf(LaenderQuellen.weltbank(land.a2)))
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Abschnitt Markt: Zinsen, Rendite, Wechselkurs und Laender-ETF. Leitzins und Waehrung ueber ein Jahr stehen nur als Kachel im Kopf.
@Composable
internal fun LaenderMarktAbschnitt(land: GlobusLand, jahr: Int, onOpen: (String) -> Unit, g: LaenderGeladen) {
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val signal = LocalSignal.current
    val s = g.struktur
    val m = g.markt
    val code = LaenderMarkt.waehrungsCode(land.a2)
    val gegen = LaenderMarkt.vergleichsCode(code, LaenderMarkt.waehrungsCode(de.troetenrepublik.app.engine.Regionen.aktuell))
    val zinsWb = LaenderMarkt.ZINS_WELTBANK.filter { s?.werte?.containsKey(it) == true }
    fun zeigen(k: Kennzahl, titel: String = S.globusWirtLabel(k)) { info = titel to S.globusWirtInfo(k) }
    if (!g.marktGeladen) {
        LaenderAbschnitt(S.globusMarkt, "markt") {
            Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        return
    }
    val kurs = m?.kurs?.takeIf { gegen != null && m.gegen == gegen }
    val etwas = zinsWb.isNotEmpty() || (m != null && (m.rendite != null || kurs != null || m.etfs.isNotEmpty())) || (code == "EUR" && gegen == null)
    if (!etwas) {
        // Der Leitzins steht dann nur als Kachel; seine Quelle bleibt im Anhang
        if (m?.zins != null) QuellenLeiste(listOf(LaenderQuellen.bis()))
        return
    }
    LaenderAbschnitt(S.globusMarkt, "markt") {
        for (k in zinsWb) {
            val w = s?.werte?.get(k) ?: continue
            val t = if (k == Kennzahl.REALZINS) Fmt.signedPct(w.wert, 2) else Fmt.pct(w.wert, 2)
            LbZeile(S.globusWirtLabel(k), if (LaenderMarkt.jahrZeigen(w, s?.ueblich?.get(k), jahr)) "$t (${w.jahr})" else t) { zeigen(k) }
        }
        m?.rendite?.let { (k, r) ->
            val text = Fmt.pct(r.wert, 2) + (r.monat?.let { " (${Fmt.monthShortYear(it)})" } ?: "")
            LbZeile(S.globusWirtLabel(k), text) { zeigen(k) }
        }
        if (code == "EUR" && gegen == null) {
            LbZeile(S.globusWirtLabel(Kennzahl.WAEHRUNG_EURO), "Euro") { zeigen(Kennzahl.WAEHRUNG_EURO) }
        } else if (code != null && gegen != null && kurs != null) {
            LbZeile(S.globusWirtLabel(Kennzahl.WAEHRUNG), waehrungKurs(code, gegen, kurs)) { info = S.globusWirtLabel(Kennzahl.WAEHRUNG) to waehrungKursInfo(gegen) }
        }
        if (m != null && m.etfs.isNotEmpty()) {
            LbUnterkopf(S.globusEtf)
            for (e in m.etfs) EtfZeile(e, signal.up, signal.down) { onOpen(e.etf.symbol) }
            LbHinweis(S.globusEtfHinweis)
        }
        val quellen = ArrayList<Pair<String, String>>()
        if (m?.zins != null) quellen += LaenderQuellen.bis()
        if (zinsWb.isNotEmpty()) quellen += LaenderQuellen.weltbank(land.a2)
        when (m?.rendite?.first) {
            Kennzahl.RENDITE_EZB -> quellen += LaenderQuellen.ezb()
            Kennzahl.RENDITE_FRED -> quellen += fredQuelle("IRLTLT01${land.a2}M156N").let { it.name to it.url.orEmpty() }
            Kennzahl.RENDITE_USA -> quellen += LaenderQuellen.yahoo("^TNX")
            else -> {}
        }
        if (kurs != null || m?.etfs?.isNotEmpty() == true) quellen += LaenderQuellen.yahoo(m?.etfs?.firstOrNull()?.etf?.symbol)
        QuellenLeiste(quellen.distinctBy { it.first })
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Zeile eines Laender-ETF: Name und Kuerzel links (der Name steht ganz da und bricht bei Platzmangel um), Aenderung heute
// und ueber ein Jahr rechts auf Hoehe der ersten Zeile.
@Composable
private fun EtfZeile(e: LaenderMarkt.EtfStand, up: Color, down: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
