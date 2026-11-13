package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.i18n.S

// Eingeklappter Abschnitt mit Kernaussage (0.77): Zugeklappt steht eine Zeile mit Zusammenfassung (Anzahl, Summe,
// Richtung) und Pfeil, der Zustand wird je Seite gemerkt. Lange Listen kommen als einzelne Eintraege der LazyColumn
// (listenAbschnitt), kurze als eine Spalte (KernAbschnitt).

// Ein Teil der Kernaussage; richtung != 0 zeichnet das Dreieck davor. umgekehrt: steigend ist schlecht (Leerverkaeufe).
internal class KernTeil(val text: String, val richtung: Double = 0.0, val umgekehrt: Boolean = false)

// Kernaussage: Teile nebeneinander in einer Zeile, darunter klein eine weitere Angabe.
internal class KernZeile(val teile: List<KernTeil>, val unten: KernTeil? = null)

@Composable
private fun KernTeilAnzeige(t: KernTeil, style: TextStyle) {
    val signal = LocalSignal.current
    val farbe = if (t.richtung == 0.0) MaterialTheme.colorScheme.onSurface
    else signal.forChange(if (t.umgekehrt) -t.richtung else t.richtung)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (t.richtung != 0.0) {
            DirectionTriangle(t.richtung, width = 9.dp, color = farbe)
            Spacer(Modifier.width(5.dp))
        }
        Text(t.text, style = style, color = farbe, maxLines = 1, softWrap = false)
    }
}

// Teile mit „ · “ dazwischen nebeneinander; passt ein Teil nicht mehr in die Zeile, beginnt er ganz in der naechsten, ohne
// Trennpunkt am Zeilenrand. Ein einzelner Wert bricht nie um.
@Composable
private fun PunktFluss(teile: List<KernTeil>, style: TextStyle, modifier: Modifier = Modifier) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(
        content = {
            teile.forEachIndexed { i, t ->
                if (i > 0) Text(PUNKT, style = style, color = grau, maxLines = 1, softWrap = false)
                KernTeilAnzeige(t, style)
            }
        },
        modifier = modifier,
    ) { messbar, c ->
        val lose = Constraints(maxWidth = Constraints.Infinity)
        val p = messbar.map { it.measure(lose) }
        val breite = if (c.hasBoundedWidth) c.maxWidth else p.sumOf { it.width }
        // Je Zeile: Liste der Indizes in p (Teile und die Punkte davor)
        val zeilen = ArrayList<MutableList<Int>>()
        var x = 0
        teile.indices.forEach { i ->
            val ti = if (i == 0) 0 else 2 * i
            val w = p[ti].width
            if (i == 0) { zeilen.add(mutableListOf(ti)); x = w; return@forEach }
            val sep = p[ti - 1].width
            if (x + sep + w <= breite) { zeilen.last().add(ti - 1); zeilen.last().add(ti); x += sep + w }
            else { zeilen.add(mutableListOf(ti)); x = w }
        }
        val hoehen = zeilen.map { z -> z.maxOf { p[it].height } }
        val weite = zeilen.maxOf { z -> z.sumOf { p[it].width } }.coerceAtMost(breite)
        layout(weite, hoehen.sum()) {
            var y = 0
            zeilen.forEachIndexed { zi, z ->
                var xx = 0
                z.forEach { k -> p[k].placeRelative(xx, y + (hoehen[zi] - p[k].height) / 2); xx += p[k].width }
                y += hoehen[zi]
            }
        }
    }
}

// Kopfzeile: Titel, darunter die Kernaussage, rechts der Pfeil. Werte brechen nie um, ganze Teile rutschen in die naechste Zeile.
@Composable
internal fun KernKopf(titel: String, kern: KernZeile?, offen: Boolean, gross: Boolean = true, onInfo: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (offen) S.less else S.more, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 20.dp).padding(top = if (gross) 22.dp else 8.dp, bottom = if (gross) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titel, style = if (gross) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                // Erklaerung: kleines Info-Symbol direkt hinter dem Titel
                if (onInfo != null) Icon(
                    Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).clickable(interactionSource = null, indication = Tippflaeche(TippForm.KREIS), role = Role.Button, onClick = onInfo).padding(6.dp).size(18.dp),
                )
            }
            if (kern != null) {
                PunktFluss(kern.teile, MaterialTheme.typography.bodyMedium, Modifier.padding(top = 2.dp))
                // Untere Angabe: mehrere mit „ · “ verbundene Werte rutschen als Ganzes in die naechste Zeile
                kern.unten?.let { u ->
                    val stuecke = u.text.split(PUNKT).filter { it.isNotBlank() }
                    val teile = if (stuecke.size <= 1) listOf(u) else stuecke.mapIndexed { i, s -> KernTeil(s, if (i == 0) u.richtung else 0.0, u.umgekehrt) }
                    PunktFluss(teile, MaterialTheme.typography.bodySmall, Modifier.padding(top = 1.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        AufklappPfeil(offen)
    }
}

// Kurze Liste in einer Spalte: zugeklappt nur die Kopfzeile. Lohnt sich das Aufklappen nicht (lohnt = false), steht
// der Inhalt gleich da, mit einfacher Ueberschrift und ohne Pfeil.
@Composable
internal fun KernAbschnitt(
    titel: String,
    kern: KernZeile?,
    merker: SeitenMerker?,
    schluessel: String,
    gross: Boolean = true,
    lohnt: Boolean = true,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    // Ohne Merker der Seite bleibt der Zustand ueber das Herausscrollen erhalten (rememberSaveable)
    val lokal = rememberSaveable(schluessel) { mutableStateOf(false) }
    val zustand = merker?.zustand<Boolean>("kern/$schluessel") { false } ?: lokal
    var offen by zustand
    if (!lohnt) {
        if (gross) SectionHeader(titel) else Zwischenkopf(titel)
        Column { inhalt() }
        return
    }
    KernKopf(titel, kern, offen, gross) { offen = !offen }
    if (offen) Column { inhalt() }
}

// Kernaussage einer Meldungsliste: Anzahl der Kaeufe und Verkaeufe mit Dreieck, darunter das Netto. Summen nur, wenn
// alle Betraege in der Anzeigewaehrung vorliegen (null sonst).
internal fun meldungsKern(kaeufe: Int, verkaeufe: Int, kauf: Double?, verkauf: Double?, ziel: String): KernZeile {
    val teile = listOf(
        KernTeil(S.kernKaeufe(kaeufe), if (kaeufe > 0) 1.0 else 0.0),
        KernTeil(S.kernVerkaeufe(verkaeufe), if (verkaeufe > 0) -1.0 else 0.0),
    )
    val netto = de.troetenrepublik.app.engine.ListenKern.netto(kauf, verkauf)
    val unten = if (netto != null && kaeufe + verkaeufe > 0) KernTeil(
        S.kernNetto(Fmt.mitSymbol(Fmt.compact(kotlin.math.abs(netto)), de.troetenrepublik.app.engine.Derivatives.currencySymbol(ziel))),
        de.troetenrepublik.app.engine.ListenKern.richtung(netto).toDouble(),
    ) else null
    return KernZeile(teile, unten)
}

// Summe der umgerechneten Betraege, nur wenn keiner ohne Kurs in Fremdwaehrung blieb.
internal fun summeOderNull(betraege: List<Pair<Double, String>>, ziel: String): Double? =
    if (de.troetenrepublik.app.engine.InsiderBetraege.ohneKurs(betraege, ziel) > 0) null
    else de.troetenrepublik.app.engine.InsiderBetraege.summe(betraege, ziel)

// Eine Zeile einer langen Liste: Schluessel fuer die LazyColumn und der fertig vorbereitete Inhalt.
internal class ZeilenDaten(val key: String, val inhalt: @Composable () -> Unit)

// Abschnitt aus vorbereiteten Zeilen. kopf steht immer da (Ueberschrift, Trend), vorspann und nachspann nur
// aufgeklappt. immerZu: auch kurze Listen bleiben zu.
internal class ListenModell(
    val titel: String,
    val gross: Boolean,
    val kern: KernZeile?,
    val zeilen: List<ZeilenDaten>,
    val kopf: (@Composable () -> Unit)? = null,
    val vorspann: (@Composable () -> Unit)? = null,
    val nachspann: (@Composable () -> Unit)? = null,
    val immerZu: Boolean = false,
    // Ohne Kernzeile: Die Zeilen stehen immer da (Abschnitte, die jede Zeile selbst aufklappen)
    val immerOffen: Boolean = false,
    // Erklaerung hinter dem Info-Symbol am Titel statt als offener Text ueber den Zeilen
    val info: String? = null,
    // Langes Druecken auf die Ueberschrift legt das Widget zu diesem Abschnitt an (nur mit beiden Angaben)
    val widgetState: de.troetenrepublik.app.AppState? = null,
    val widget: de.troetenrepublik.app.data.WidgetVorgabe.Vorgabe? = null,
    // So viele Zeilen stehen zuerst da; sind es mehr, folgt am Ende „Alle n“ zum Aufklappen
    val kappe: Int = LISTEN_SICHTBAR,
    // Beim Aufklappen ueber die Kernzeile: startet Abrufe, die erst offen laufen sollen (unabhaengig davon, ob die ladende
    // Karte gerade im Bild ist)
    val beimOeffnen: (() -> Unit)? = null,
) {
    val erste: List<ZeilenDaten> get() = zeilen.take(kappe)
    val rest: List<ZeilenDaten> get() = zeilen.drop(kappe)
}

// Standard: wie viele Zeilen einer langen Liste zuerst sichtbar sind
internal const val LISTEN_SICHTBAR = 30

// „Alle n“-Pfeil unter einer gekappten Liste
@Composable
private fun AlleZeile(offen: Boolean, gesamt: Int, onClick: () -> Unit) {
    ListenPfeil(offen, label = if (offen) S.less else S.bbAlle(gesamt), onClick = onClick)
}

// Ueberschrift des Modells, mit dem Langdruck fuer das Widget, wenn das Modell eins hat.
@Composable
private fun ModellWidget(m: ListenModell, inhalt: @Composable () -> Unit) {
    val s = m.widgetState
    val w = m.widget
    if (s != null && w != null) WidgetLangDruck(s, w, Modifier.fillMaxWidth(), inhalt) else inhalt()
}

// Einfache Titelzeile eines Modells (ohne Kernzeile), mit Info-Symbol rechts, wenn es eine Erklaerung hat
@Composable
private fun ModellTitel(m: ListenModell) {
    val info = m.info
    val kopf: @Composable () -> Unit = { if (m.gross) SectionHeader(m.titel) else Zwischenkopf(m.titel) }
    if (info == null) { kopf(); return }
    var offen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { kopf() }
        IconButton(onClick = { offen = true }, modifier = Modifier.padding(end = 8.dp)) {
            Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
    if (offen) MarktInfoBlatt(m.titel, info) { offen = false }
}

// Kernzeile eines Modells; die Erklaerung liegt hinter dem Info-Symbol am Titel
@Composable
private fun ModellKern(m: ListenModell, offen: Boolean, onClick: () -> Unit) {
    val info = m.info
    var infoOffen by remember { mutableStateOf(false) }
    KernKopf(m.titel, m.kern, offen, m.gross, onInfo = if (info != null) ({ infoOffen = true }) else null, onClick = onClick)
    if (infoOffen && info != null) MarktInfoBlatt(m.titel, info) { infoOffen = false }
}

// Gibt das fertige Modell einer Karte an die Seite weiter (Merker); die LazyColumn liest es und baut daraus je Zeile
// einen Eintrag. Die Karte selbst zeichnet nichts.
@Composable
internal fun ModellMelden(merker: SeitenMerker?, schluessel: String, modell: ListenModell?) {
    if (merker == null) return
    val ziel = merker.zustand<ListenModell?>("modell/$schluessel") { null }
    SideEffect { if (ziel.value !== modell) ziel.value = modell }
}

// Eintraege eines Modells in der LazyColumn: Kopf, Kernzeile mit Pfeil, aufgeklappt Vorspann, je Zeile ein Eintrag
// (key, contentType) und Nachspann. Ohne Modell nichts.
internal fun LazyListScope.listenAbschnitt(merker: SeitenMerker, id: String, schluessel: String = id) {
    val m = merker.zustand<ListenModell?>("modell/$schluessel") { null }.value ?: return
    m.kopf?.let { k -> item(key = "$id-kopf", contentType = "kern-kopf") { k() } }
    val lohnt = !m.immerOffen && (m.immerZu || de.troetenrepublik.app.engine.ListenKern.lohnt(m.zeilen.size))
    val offen = merker.zustand<Boolean>("kern/$id") { false }
    val sichtbar = !lohnt || offen.value
    if (lohnt) {
        val istOffen = offen.value
        item(key = "$id-kern", contentType = "kern-kopf") { ModellWidget(m) { ModellKern(m, istOffen) { offen.value = !offen.value; if (offen.value) m.beimOeffnen?.invoke() } } }
    } else if (m.titel.isNotBlank()) {
        item(key = "$id-titel", contentType = "kern-titel") { ModellWidget(m) { ModellTitel(m) } }
    }
    if (!sichtbar) return
    m.vorspann?.let { v -> item(key = "$id-vor", contentType = "kern-text") { v() } }
    items(m.erste, key = { "$id-${it.key}" }, contentType = { "kern-zeile" }) { it.inhalt() }
    if (m.rest.isNotEmpty()) {
        val alle = merker.zustand<Boolean>("alle/$id") { false }
        val istAlle = alle.value
        item(key = "$id-alle", contentType = "pfeil") { AlleZeile(istAlle, m.zeilen.size) { alle.value = !alle.value } }
        if (istAlle) items(m.rest, key = { "$id-${it.key}" }, contentType = { "kern-zeile" }) { it.inhalt() }
    }
    m.nachspann?.let { n -> item(key = "$id-nach", contentType = "kern-text") { n() } }
}

// Dasselbe Modell in einer Spalte, fuer Stellen ohne LazyColumn (Blaetter, Kaesten).
@Composable
internal fun ModellSpalte(m: ListenModell?, id: String) {
    if (m == null) return
    var offen by remember(id) { mutableStateOf(false) }
    m.kopf?.invoke()
    val lohnt = !m.immerOffen && (m.immerZu || de.troetenrepublik.app.engine.ListenKern.lohnt(m.zeilen.size))
    if (lohnt) ModellWidget(m) { ModellKern(m, offen) { offen = !offen } }
    else if (m.titel.isNotBlank()) ModellWidget(m) { ModellTitel(m) }
    if (!lohnt || offen) {
        m.vorspann?.invoke()
        m.erste.forEach { it.inhalt() }
        if (m.rest.isNotEmpty()) {
            var alle by remember(id) { mutableStateOf(false) }
            AlleZeile(alle, m.zeilen.size) { alle = !alle }
            if (alle) m.rest.forEach { it.inhalt() }
        }
        m.nachspann?.invoke()
    }
}

// Auf der Wertpapierseite (mit Merker) gibt die Karte das Modell an die LazyColumn, sonst zeichnet sie es selbst.
@Composable
internal fun ModellAusgabe(merker: SeitenMerker?, id: String, modell: ListenModell?) {
    if (merker == null) ModellSpalte(modell, id) else ModellMelden(merker, id, modell)
}
