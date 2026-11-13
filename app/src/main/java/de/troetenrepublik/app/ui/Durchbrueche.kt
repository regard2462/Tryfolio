package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.engine.Durchbrueche
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

// Durchbrueche im Reiter Trends: Die Themen stehen in der Liste „Branchen und Themen“ (TrendListe.kt) und zeigen dort die
// Tabelle eines Themas (DurchbruchTabelle): Neu (streng gefilterte Schlagzeilen), Erwartet, In Entwicklung und Rueckblick
// (kuratiert, je Eintrag mit Link). Hier bleibt die eigene Liste „Beschleunigt sich selbst“: je Kennzahlreihe eine Zeile,
// ein Tipp zeigt die Kurve auf logarithmischer Skala; dazu Beispiele, in denen die Entwicklung ihren eigenen Fortschritt
// antreibt (KI entwirft Chips und Wirkstoffe). Es ist nur eine Zeile zugleich offen.
@Composable
internal fun BeschleunigtAbschnitt(state: AppState, inhalt: Durchbrueche.Inhalt?) {
    val b = inhalt?.beschleunigung ?: return
    val reihen = remember(b) { b.reihen.filter { r -> r.p.count { it.size >= 2 && it[1] > 0 } >= 2 } }
    if (reihen.isEmpty() && b.eintraege.isEmpty()) return
    val deutsch = I18n.lang == Lang.DE
    var offen by rememberSaveable { mutableStateOf<String?>(null) }
    val links = appLinks()
    Column(Modifier.fillMaxWidth()) {
        WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.abschnitt("durchbrueche"), Modifier.fillMaxWidth()) { SectionHeader(S.dbBeschleunigt) }
        reihen.forEach { r ->
            key(r.id) {
                val auf = offen == r.id
                Column(Modifier.fillMaxWidth().animateContentSize()) {
                    ThemenZeile(r.name(deutsch), null, auf) { offen = if (auf) null else r.id }
                    if (auf) Getoent { Kostenkurve(r, deutsch) { if (r.u.isNotEmpty()) links.openUri(r.u) } }
                }
            }
        }
        if (b.eintraege.isNotEmpty()) {
            val auf = offen == BESCHLEUNIGT
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                ThemenZeile(S.tlBeispiele, b.eintraege.size.toString(), auf) { offen = if (auf) null else BESCHLEUNIGT }
                if (auf) Getoent {
                    b.eintraege.forEachIndexed { i, e ->
                        if (i > 0) Fein()
                        InhaltZeile(e, deutsch) { links.openUri(e.u) }
                    }
                }
            }
        }
        // Pflichtnennung der frei lizenzierten Kennzahlreihen (CC BY)
        val quellen = remember(b) {
            b.reihen.filter { it.lz.contains("CC BY") }.map { r ->
                val ourWorld = r.q.contains("Our World in Data")
                val name = when {
                    ourWorld -> "Our World in Data"
                    r.q.contains("Epoch AI") -> "Epoch AI"
                    r.q.contains("METR") -> "METR"
                    else -> r.q.substringBefore(",")
                }
                QuelleLink("$name (CC BY 4.0)", if (ourWorld) "https://ourworldindata.org" else r.u)
            }.distinctBy { it.name }
        }
        Spacer(Modifier.height(8.dp))
        QuellenAnhang(quellen)
    }
}

private const val BESCHLEUNIGT = "beschleunigt"

internal enum class NeuStand { LAEDT, GELADEN, FEHLT }

// Zeile einer Kennzahlreihe oder der Beispiele: Name (lange Namen brechen um), rechts optional ein Zaehler, dann der Pfeil.
@Composable
private fun ThemenZeile(name: String, zaehler: String?, offen: Boolean, onKlick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onKlick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (zaehler != null) {
            Spacer(Modifier.width(12.dp))
            Text(zaehler, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.width(8.dp))
        AufklappPfeil(offen)
    }
}

// Form einer Flaeche, die ueber mehrere Eintraege einer Liste laufen kann: gerundet nur an den Enden (oben, unten)
internal fun stueckForm(r: Dp, oben: Boolean, unten: Boolean) =
    RoundedCornerShape(topStart = if (oben) r else 0.dp, topEnd = if (oben) r else 0.dp, bottomStart = if (unten) r else 0.dp, bottomEnd = if (unten) r else 0.dp)

// Eingerueckte, leicht getoente Flaeche unter einer Zeile. Laeuft die Tabelle ueber mehrere Eintraege einer Liste (Lage),
// zeichnet jeder Eintrag sein Stueck: oben und unten sagen, ob hier die Flaeche anfaengt bzw. endet.
@Composable
internal fun Getoent(
    einzug: PaddingValues = PaddingValues(start = 28.dp, end = 20.dp), oben: Boolean = true, unten: Boolean = true, inhalt: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(einzug).padding(bottom = if (unten) 8.dp else 0.dp)
            .clip(stueckForm(12.dp, oben, unten)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp).padding(top = if (oben) 4.dp else 0.dp, bottom = if (unten) 4.dp else 0.dp),
    ) { inhalt() }
}

// Eine Stufe weiter eingerückt und etwas dunkler getönt als die Unterabschnittszeile
@Composable
internal fun Tiefer(oben: Boolean = true, unten: Boolean = true, inhalt: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, bottom = if (unten) 8.dp else 0.dp)
            .clip(stueckForm(10.dp, oben, unten)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp).padding(top = if (oben) 2.dp else 0.dp, bottom = if (unten) 2.dp else 0.dp),
    ) { inhalt() }
}

@Composable
internal fun Fein() {
    Trennlinie(Modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

// Letzte Zeile einer Tabelle: Weg zu einer anderen Seite, mit Pfeil nach rechts
@Composable
internal fun WegZeile(text: String, onKlick: () -> Unit) {
    Row(Modifier.fillMaxWidth().zeilenKlick(onClick = onKlick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        ChevronRight()
    }
}

// Getoente Tabelle eines Themas: Neu, Erwartet, In Entwicklung, Rueckblick, Weg zur Wirkung, Fonds. Auch im Blatt der Lage.
@Composable
internal fun DurchbruchTabelle(
    state: AppState,
    b: Durchbrueche.Block,
    jetzt: Long,
    deutsch: Boolean,
    neuStand: NeuStand,
    unter: String?,
    wechsel: (String) -> Unit,
    onOpen: (String) -> Unit,
    onArtikel: (MarketApi.NewsItem) -> Unit,
    onLink: (String) -> Unit,
    einzug: PaddingValues = PaddingValues(start = 28.dp, end = 20.dp),
) {
    fun alterText(zeit: Long): String = Durchbrueche.alter(zeit, jetzt).let { S.dbAlter(it.wert, it.einheit) }
    val inh = b.inhalt
    val erwartet = remember(b, jetzt) { b.erwartet(jetzt) }
    Getoent(einzug) {
        // Ohne neue, aber mit aelteren Meldungen steht „Zuletzt“ an Stelle von „Neu“, nicht darin verschachtelt (0.79)
        val nurZuletzt = b.neu.isEmpty() && b.zuletzt.isNotEmpty()
        Unterabschnitt(if (nurZuletzt) S.dbZuletzt else S.dbNeu, if (nurZuletzt) b.zuletzt.size else b.neu.size, unter == "n") { wechsel("n") }
        if (unter == "n") Tiefer {
            if (nurZuletzt) ZuletztZeilen(b, jetzt, onArtikel)
            else if (b.neu.isEmpty()) {
                val leer = when (neuStand) {
                    NeuStand.GELADEN -> S.dbKeineNeuen
                    NeuStand.FEHLT -> S.dbNichtGeladen
                    NeuStand.LAEDT -> S.trLaedt
                }
                Text(leer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
            }
            b.neu.forEachIndexed { i, m ->
                if (i > 0) Fein()
                Column(
                    Modifier.fillMaxWidth()
                        .zeilenKlick { onArtikel(MarketApi.NewsItem(title = m.titel, link = m.link, published = m.zeit, source = m.quelle)) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(m.titel, style = MaterialTheme.typography.bodyMedium.schlagzeile())
                    PunktText(
                        listOfNotNull(m.quelle.takeIf { it.isNotBlank() }, alterText(m.zeit)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (inh != null) {
            listOf(
                Triple("e", S.dbErwartet, erwartet), Triple("w", S.dbEntwicklung, inh.w), Triple("r", S.dbRueckblick, inh.r),
            ).filter { it.third.isNotEmpty() }.forEach { (k, titel, liste) ->
                Fein()
                Unterabschnitt(titel, liste.size, unter == k) { wechsel(k) }
                if (unter == k) Tiefer {
                    liste.forEachIndexed { i, e ->
                        if (i > 0) Fein()
                        InhaltZeile(e, deutsch) { onLink(e.u) }
                    }
                }
            }
        }
        Fein()
        // Weg zur Wirkungsseite des Themas als Zeile der Tabelle
        WegZeile(S.wnTitel) { onOpen(WIRKUNG_PRAEFIX + "durchbruch:" + b.thema.id) }
        Fein()
        DurchbruchFonds(state, b.thema.etfs, onOpen)
    }
}

// Ohne neue Meldung, aber mit aelteren Treffern (bis zu einem Jahr): die Meldungen mit Datum unter der Zeile „Zuletzt“;
// ein Tipp oeffnet den Artikel.
@Composable
private fun ZuletztZeilen(b: Durchbrueche.Block, jetzt: Long, onArtikel: (MarketApi.NewsItem) -> Unit) {
    b.zuletzt.forEachIndexed { i, m ->
        val d = Durchbrueche.datum(m.zeit)
        if (i > 0) Fein()
        Column(
            Modifier.fillMaxWidth()
                .zeilenKlick { onArtikel(MarketApi.NewsItem(title = m.titel, link = m.link, published = m.zeit, source = m.quelle)) }
                .padding(vertical = 8.dp),
        ) {
            Text(m.titel, style = MaterialTheme.typography.bodyMedium.schlagzeile())
            PunktText(
                listOfNotNull(m.quelle.takeIf { it.isNotBlank() }, S.dbZuletztDatum(d.jahr, d.monat, d.tag, Durchbrueche.datumMitTag(m.zeit, jetzt))).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Zeile eines Unterabschnitts: Titel (darf umbrechen), rechts die Anzahl oder eine kurze Zusammenfassung, dann der Pfeil
@Composable
internal fun Unterabschnitt(titel: String, anzahl: Int, offen: Boolean, zusammenfassung: String? = null, erklaerung: String? = null, onKlick: () -> Unit) {
    val rechts = zusammenfassung ?: anzahl.takeIf { it > 0 }?.toString()
    var info by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().zeilenKlick(onClick = onKlick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(titel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
            // Erklaerung hinter dem Info-Symbol direkt hinter dem Titel
            if (erklaerung != null) androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).clickable(interactionSource = null, indication = Tippflaeche(TippForm.KREIS), role = androidx.compose.ui.semantics.Role.Button) { info = true }
                    .padding(4.dp).size(16.dp),
            )
        }
        if (rechts != null) {
            Spacer(Modifier.width(12.dp))
            Text(rechts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
        }
        AufklappPfeil(offen)
    }
    if (info && erklaerung != null) MarktInfoBlatt(titel, erklaerung) { info = false }
}

// Ein kuratierter Eintrag: Jahr oder Stand fett vorn, dann der Text; antippbar, oeffnet den Link des Eintrags.
@Composable
private fun InhaltZeile(e: Durchbrueche.Eintrag, deutsch: Boolean, onKlick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val marke = e.marke(deutsch).replace(' ', ' ')
    val farbe = MaterialTheme.colorScheme.primary
    val artText = S.dbArt(e.art)
    val klein = MaterialTheme.typography.labelSmall.toSpanStyle().copy(color = grau)
    val text = buildAnnotatedString {
        if (marke.isNotEmpty()) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = farbe)) { append(marke) }
            // Art des erwarteten Termins als kleine Marke hinter der Zeitangabe
            if (artText.isNotEmpty()) withStyle(klein) { append(" · $artText") }
            append("  ")
        }
        append(e.text(deutsch))
    }
    Row(
        Modifier.fillMaxWidth().then(if (e.u.isNotEmpty()) Modifier.zeilenKlick(onClick = onKlick) else Modifier).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (e.u.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            ChevronRight(color = grau.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// Kurve einer Reihe auf logarithmischer Achse, Anfangs- und Endwert direkt darunter
@Composable
private fun Kostenkurve(r: Durchbrueche.Reihe, deutsch: Boolean, onKlick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val linie = MaterialTheme.colorScheme.primary
    val punkte = r.p.filter { it.size >= 2 && it[1] > 0 }
    val erst = punkte.first()
    val letzt = punkte.last()
    Column(Modifier.fillMaxWidth().zeilenKlick(onClick = onKlick).padding(vertical = 10.dp)) {
        PunktText(r.einheit(deutsch) + " · " + S.dbLog + " · " + S.dbBis(r.endjahr()), style = MaterialTheme.typography.bodySmall, color = grau)
        Canvas(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(72.dp)) {
            val x0 = erst[0]
            val x1 = letzt[0].coerceAtLeast(x0 + 1.0)
            val lo = punkte.minOf { ln(it[1]) }
            val hi = punkte.maxOf { ln(it[1]) }.coerceAtLeast(lo + 0.1)
            val rand = 6.dp.toPx()
            fun pos(p: List<Double>) = Offset(
                rand + ((p[0] - x0) / (x1 - x0)).toFloat() * (size.width - 2 * rand),
                rand + (1f - ((ln(p[1]) - lo) / (hi - lo)).toFloat()) * (size.height - 2 * rand),
            )
            val ps = punkte.map { pos(it) }
            for (i in 0 until ps.size - 1) drawLine(linie, ps[i], ps[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            ps.forEach { drawCircle(linie, 3.dp.toPx(), it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(erst[0].toInt().toString() + "  " + kennzahl(erst[1], deutsch), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
            Text(letzt[0].toInt().toString() + "  " + kennzahl(letzt[1], deutsch), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
        }
        // Halbierungs- oder Verdopplungszeit, aus den Werten der Reihe gerechnet
        r.verdopplung()?.let { v ->
            Text(S.dbRate(v.faellt, v.monate), style = MaterialTheme.typography.titleSmall, color = linie, modifier = Modifier.padding(top = 6.dp))
        }
        val warum = r.warum(deutsch)
        if (warum.isNotEmpty()) Text(warum, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        val hinweis = r.hinweis(deutsch)
        if (hinweis.isNotEmpty()) Text(hinweis, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 4.dp))
    }
}

private const val HOCH = "⁰¹²³⁴⁵⁶⁷⁸⁹"

// Zahl kurz: Millionen mit „Mio.“, grosse Gleitkommazahlen als „3 × 10²³“, sonst gerundet mit Tausenderpunkt
internal fun kennzahl(v: Double, deutsch: Boolean): String = when {
    v >= 1e12 -> {
        val e = floor(kotlin.math.log10(v)).toInt()
        val m = v / 10.0.pow(e)
        val mant = if (kotlin.math.abs(m - m.toLong()) < 0.05) m.toLong().toString() else Fmt.num(m, 1)
        mant + " × 10" + e.toString().map { HOCH[it - '0'] }.joinToString("")
    }
    v >= 1e6 -> Fmt.num(v / 1e6, 1) + if (deutsch) " Mio." else " M"
    v >= 1000 -> Fmt.num(v, 0)
    v >= 100 -> Fmt.num(v, 0)
    v >= 10 -> Fmt.num(v, 1)
    else -> Fmt.num(v, 2)
}

// Die zugeordneten Fonds in einer Zeile mit dem Tageskurs; ein Tipp oeffnet die Detailseite.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurchbruchFonds(state: AppState, symbole: List<String>, onOpen: (String) -> Unit) {
    var kurse by remember(symbole) { mutableStateOf<Map<String, Quote>>(emptyMap()) }
    LaunchedEffect(symbole) { kurse = state.durchbruchKurse(symbole) }
    // Jeder Fonds rutscht als Ganzes in die naechste Zeile, nie mitten im Eintrag
    FlowRow(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        symbole.forEach { s ->
            Row(Modifier.textKlick(0.dp) { onOpen(s) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                kurse[s]?.let { q ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Fmt.signedPct(q.changePct, 1), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                        color = if (q.changePct >= 0) LocalSignal.current.up else LocalSignal.current.down,
                    )
                }
            }
        }
    }
}
