package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Damodaran

// [Lange Lage] Aktienseite: KGV der Aktie gegen das KGV der US-Branche (Damodaran). Zugeklappt die Kernaussage mit Abstand
// in Prozent, aufgeklappt der Verlauf des Branchen-KGV seit 2015 mit dem Gesamtmarkt und dem heutigen KGV der Aktie als
// Linie. Die Daten liegen mitgeliefert vor, es gibt keinen Abruf. Ohne Zuordnung der Branche oder ohne positives KGV
// zeichnet der Baustein nichts.
@Composable
internal fun BrancheKgvKarte(industry: String, kgv: Double?, merker: SeitenMerker?) {
    val d = LangeLageDaten.damodaran.rememberDatei() ?: return
    if (kgv == null) return
    val b = remember(d, industry) { Damodaran.branche(d, industry) } ?: return
    val aktuell = b.werte.lastOrNull() ?: return
    val v = remember(kgv, aktuell) { Damodaran.vergleich(kgv, aktuell) } ?: return
    val offen = firmaOffen(merker, "branche-kgv")
    val jahr = d.jahre.last()
    val richtung = when (v.lage) {
        Damodaran.Lage.VERGLEICHBAR -> 0.0
        Damodaran.Lage.TEURER -> 1.0
        Damodaran.Lage.GUENSTIGER -> -1.0
    }
    val abstand = Fmt.pct(kotlin.math.abs(v.abstand), 0) + " " + langeText("b.lage." + v.lage.name)
    val kern = KernZeile(
        listOf(
            KernTeil(langeText("b.kern", Fmt.num(aktuell, 1) + "×")),
            if (v.lage == Damodaran.Lage.VERGLEICHBAR) KernTeil(langeText("b.lage.VERGLEICHBAR")) else KernTeil(abstand, richtung, umgekehrt = true),
        ),
        KernTeil(langeText("b.unten", jahr.toString())),
    )
    QuellenMelden(QuelleLink("Aswath Damodaran, NYU Stern", Damodaran.SEITE))
    FirmaAbschnitt(langeText("b.titel"), kern, langeText("b.info", tagDatum(d.stand)), offen) {
        val verlauf = remember(d, b) { d.jahre.mapIndexedNotNull { i, j -> b.werte[i]?.let { j.toDouble() to it } } }
        val markt = remember(d) { d.jahre.mapIndexedNotNull { i, j -> d.gesamt.werte[i]?.let { j.toDouble() to it } } }
        val aktie = remember(d, kgv) { d.jahre.map { it.toDouble() to kgv } }
        if (verlauf.size >= 3) {
            Zwischenkopf(langeText("b.verlauf", d.jahre.first().toString()), oben = 4.dp)
            HistDiagramm(
                listOf(
                    HistLinie(langeText("b.n.branche"), verlauf, MaterialTheme.colorScheme.primary),
                    HistLinie(langeText("b.n.markt"), markt, MaterialTheme.colorScheme.onSurfaceVariant),
                    HistLinie(langeText("b.n.aktie"), aktie, Kategorie.ocker),
                ),
                d.jahre.first().toDouble(), d.jahre.last().toDouble(), log = false, marken = emptyList(),
                achsenText = { Fmt.num(it, 0) }, endeText = { Fmt.num(it, 1) + "×" }, beschreibung = langeText("b.titel"), hoehe = 160.dp,
            )
        }
        Damodaran.mittelBisher(b)?.let { m ->
            KeyValueRow(langeText("b.mittel", d.jahre.first().toString(), (jahr - 1).toString()), Fmt.num(m, 1) + "×", hint = b.name)
        }
        if (b.firmen > 0) KeyValueRow(langeText("b.firmen"), Fmt.num(b.firmen.toDouble(), 0))
    }
}

// [Lange Lage] Ausblick: implizite Risikopraemie der Aktien (Damodaran) seit 1961. Die Ueberschrift mit Info steht im selben
// Eintrag, damit sie nur mit Daten erscheint. Kernaussage: letzter Wert und Rang gegen alle Jahre, darunter der Verlauf
// von Praemie und Zins.
@Composable
internal fun RisikopraemieAbschnitt() {
    val d = LangeLageDaten.damodaran.rememberDatei() ?: return
    val p = d.praemie
    val (jahr, wert) = p.letzte() ?: return
    val rang = Damodaran.praemieRang(p) ?: return
    QuellenMelden(QuelleLink("Aswath Damodaran, NYU Stern", Damodaran.SEITE))
    Column(Modifier.fillMaxWidth()) {
        MarktInfoKopf(langeText("p.titel"), langeText("p.info", tagDatum(d.stand)))
        Text(
            langeText("p.wert", Fmt.num(wert, 2)), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp), maxLines = 1, softWrap = false,
        )
        Text(
            langeText("p.unter", jahr.toString(), Fmt.num(rang, 0), p.von.toString()), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        val praemie = remember(p) { p.erp.mapIndexedNotNull { i, v -> v?.let { (p.von + i).toDouble() to it } } }
        val zins = remember(p) { p.rendite.mapIndexedNotNull { i, v -> v?.let { (p.von + i).toDouble() to it } } }
        if (praemie.size >= 3) {
            HistDiagramm(
                listOfNotNull(
                    HistLinie(langeText("p.n.praemie"), praemie, MaterialTheme.colorScheme.primary),
                    zins.takeIf { it.size >= 3 }?.let { HistLinie(langeText("p.n.zins"), it, Kategorie.ocker) },
                ),
                p.von.toDouble(), p.bis.toDouble(), log = false, marken = emptyList(),
                achsenText = { Fmt.num(it, 0) + " %" }, endeText = { Fmt.num(it, 1) + " %" }, beschreibung = langeText("p.verlauf"), hoehe = 160.dp,
            )
        }
    }
}
