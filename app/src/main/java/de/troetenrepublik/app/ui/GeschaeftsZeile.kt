package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.CopyUebersicht
import de.troetenrepublik.app.engine.GeschaeftsBilanz
import de.troetenrepublik.app.i18n.GeschaeftTexte
import kotlin.math.abs

// Bausteine der Zeilen zu Kaeufen und Verkaeufen von Personen (Insider, Kongress, Investoren, Copytrading): rechts der
// Kurs seit dem Geschaeft, darunter Betrag, Anteil als Balken, Rest im Bestand und Gewinn. Alles aus den Meldungen,
// ohne Schaetzung; was sie nicht hergeben, fehlt.

private fun geld(v: Double, zeichen: String): String = ungeteilt(Fmt.mitSymbol(Fmt.compact(v), zeichen))

// Rechts neben dem Namen: „seit Verkauf“, gezeichnetes Dreieck und 23 %. Das Dreieck zeigt die Richtung des Kurses, die Farbe
// die Sicht der Person: nach einem Verkauf ist ein gefallener Kurs gruen (gut verkauft), nach einem Kauf ein gestiegener.
@Composable
internal fun SeitGeschaeft(abstand: Double, verkauf: Boolean) {
    val farbe = LocalSignal.current.forChange(GeschaeftsBilanz.sicht(abstand, verkauf))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            GeschaeftTexte.t(if (verkauf) "seit.verkauf" else "seit.kauf"), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
        )
        Spacer(Modifier.width(6.dp))
        DirectionTriangle(abstand, color = farbe)
        Spacer(Modifier.width(4.dp))
        Text(ungeteilt(Fmt.pct(abs(abstand) * 100, 1)), style = MaterialTheme.typography.titleSmall, color = farbe, maxLines = 1, softWrap = false)
    }
}

// Zeile unter dem Namen: Bezeichnung links (bricht um), Wert rechts in einem Stueck.
@Composable
internal fun GeschaeftZeile(links: String, rechts: String, rechtsFarbe: Color? = null) {
    val klein = MaterialTheme.typography.bodySmall
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Top) {
        Text(links, style = klein, color = grau, modifier = Modifier.weight(1f))
        if (rechts.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(ungeteilt(rechts), style = klein, color = rechtsFarbe ?: grau, maxLines = 1, softWrap = false)
        }
    }
}

// Anteil des Geschaefts am Bestand als kleiner Balken (verkauft gegen gehalten), Beschriftung rechts. Blau statt Gruen und
// Rot: Die Farben der Signale gehoeren dem Kurs.
@Composable
internal fun AnteilBalken(anteil: Double, text: String) {
    val fuell = Kategorie.blau
    val spur = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(6.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(spur, cornerRadius = r)
            val b = size.width * anteil.coerceIn(0.0, 1.0).toFloat()
            if (b > 0f) drawRoundRect(fuell, size = Size(maxOf(b, size.height), size.height), cornerRadius = r)
        }
        Spacer(Modifier.width(10.dp))
        Text(ungeteilt(text), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

// Gewinn oder Verlust beim Verkauf: Dreieck, Betrag und Prozent; nur mit belegtem Einstandspreis.
@Composable
internal fun GewinnZeile(g: GeschaeftsBilanz.Gewinn, zeichen: String) {
    val klein = MaterialTheme.typography.bodySmall
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Top) {
        Text(
            GeschaeftTexte.t(if (g.betrag >= 0) "gewinn" else "verlust"), style = klein, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        ChangeValue(g.betrag, ungeteilt(geld(abs(g.betrag), zeichen) + PUNKT + Fmt.pct(abs(g.prozent) * 100, 0)), style = klein)
    }
}

// Rest im Bestand mit Wert zum heutigen Kurs: „Rest 15,99 Mio. Stück“ links, Wert rechts.
@Composable
internal fun RestZeile(stueck: Double, kurs: Double?, zeichen: String) {
    val wert = GeschaeftsBilanz.restWert(stueck, kurs)
    GeschaeftZeile(
        GeschaeftTexte.t("rest", de.troetenrepublik.app.i18n.S.usStueck(Fmt.compact(stueck))),
        wert?.let { geld(it, zeichen) }.orEmpty(),
    )
}

// Verkauf einer Person: Name und Kurs seit dem Verkauf, Tag und Kurs, Erloes, verkaufter Anteil, Rest, Gewinn.
@Composable
internal fun VerkaufsZeile(
    name: String, zusatz: String, v: CopyUebersicht.Verkauf, erloes: String, zeichen: String, onClick: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { NamenText(name, zusatz = zusatz, style = MaterialTheme.typography.titleSmall) }
            v.abstand?.let { Spacer(Modifier.width(12.dp)); SeitGeschaeft(it, verkauf = true) }
        }
        val tag = Fmt.date(v.verkauftAm)
        GeschaeftZeile(
            v.verkaufskurs?.let { GeschaeftTexte.t("verkauf.am", tag, Fmt.price(it, zeichen)) } ?: tag,
            GeschaeftTexte.t("erloes", erloes),
        )
        v.reduziert?.takeIf { it >= 0.005 }?.let { AnteilBalken(it, GeschaeftTexte.t("anteil.verkauft", Fmt.pct(it * 100, 0))) }
        v.restStueck?.takeIf { it > 0 }?.let { RestZeile(it, v.aktuell, zeichen) }
        v.gewinn?.let { GewinnZeile(it, zeichen) }
    }
}

// Nach einem Kauf ganz verkaufte Position: Kurs seit dem Verkauf (ohne Verkaufskurs der Kurs seit dem Kauf), Tag und
// Verkaufskurs, Einstieg, Gewinn gegen den Einstieg.
@Composable
internal fun AusgestiegenZeile(name: String, zusatz: String, z: CopyUebersicht.Einstand, zeichen: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { NamenText(name, zusatz = zusatz, style = MaterialTheme.typography.titleSmall) }
            Spacer(Modifier.width(12.dp))
            val vk = z.verkaufskurs
            if (vk != null && vk > 0) SeitGeschaeft(z.aktuell / vk - 1, verkauf = true) else SeitGeschaeft(z.abstand, verkauf = false)
        }
        val tag = Fmt.date(z.verkauftAm)
        GeschaeftZeile(
            z.verkaufskurs?.let { GeschaeftTexte.t("verkauf.am", tag, Fmt.price(it, zeichen)) } ?: tag,
            de.troetenrepublik.app.i18n.S.ieEinstieg(Fmt.price(z.einstieg, zeichen)),
        )
        GeschaeftsBilanz.gewinnAusKauf(z.betrag, z.einstieg, z.verkaufskurs)?.let { GewinnZeile(it, zeichen) }
    }
}

// Gehaltener Kauf einer Person: Kurs seit dem Kauf, Hinweis (Tag, Einstieg), Kaufbetrag und Wert heute, bei Teilverkauf der
// verkaufte Anteil als Balken. hint ohne den Kaufbetrag, der steht in der Zeile darunter.
@Composable
internal fun KaeuferZeile(name: String, zusatz: String, hint: String, z: CopyUebersicht.Einstand, zeichen: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { NamenText(name, zusatz = zusatz, style = MaterialTheme.typography.titleSmall) }
            Spacer(Modifier.width(12.dp))
            SeitGeschaeft(z.abstand, verkauf = false)
        }
        val farbe = LocalSignal.current.forChange(z.abstand)
        val heute = remember(z.betrag, z.abstand) { GeschaeftsBilanz.kaufWertHeute(z.betrag, z.abstand) }
        GeschaeftZeile(hint, "")
        GeschaeftZeile(GeschaeftTexte.t("kaufbetrag", geld(z.betrag, zeichen)), GeschaeftTexte.t("wert.heute", geld(heute, zeichen)), farbe)
        z.reduziert.takeIf { it >= 0.005 && it < 1.0 }?.let { AnteilBalken(it, GeschaeftTexte.t("anteil.verkauft", Fmt.pct(it * 100, 0))) }
    }
}
