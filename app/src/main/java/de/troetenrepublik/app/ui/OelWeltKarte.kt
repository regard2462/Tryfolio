package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.OelWelt
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.S

// Oel weltweit auf der Seite von Oel (0.78), neben den US-Lagern: Foerderung, Verbrauch, Rohoel der OPEC+, freie
// Kapazitaet und Lager der OECD als Kernzeilen, darunter aufklappbar je Region und Land mit Verlauf seit 2022. Daten aus
// dem Datenbuendel (engine/OelWelt.kt), ohne Abruf. Jede Zeile ist ein Eintrag der Seitenliste.

@Composable
internal fun OelWeltLader(merker: SeitenMerker) {
    val d = OelWelt.datei
    val offen by merker.zustand("oelwelt/offen") { emptySet<String>() }
    val sprache = I18n.lang
    // Aelter als das Profil der Datei erlaubt: kein Abschnitt
    val veraltet = remember(d) { d != null && de.troetenrepublik.app.engine.Alter.zustand(d.stand, de.troetenrepublik.app.engine.Alter.profilFuer(OelWelt.DATEI), System.currentTimeMillis()) == de.troetenrepublik.app.engine.Alter.Zustand.VERALTET }
    if (d != null && !veraltet) QuellenMelden(EIA_QUELLE)
    val modell = remember(d, offen, sprache, veraltet) { if (d == null || veraltet) null else oelWeltModell(merker, d, offen) }
    ModellMelden(merker, "oelwelt", modell)
}

internal fun LazyListScope.oelWeltAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "oelwelt")
}

private fun landName(l: OelWelt.Land) = if (I18n.lang == Lang.DE) l.de else l.en

private fun oelWeltModell(merker: SeitenMerker, d: OelWelt.Datei, offen: Set<String>): ListenModell {
    val zustand = merker.zustand("oelwelt/offen") { emptySet<String>() }
    // Der Lader steht oben in der Liste und ist beim Antippen weiter unten oft schon verworfen: das Modell darum hier neu
    // setzen, sonst bliebe die Zeile zu, bis der Kopf der Karte wieder ins Bild kommt
    fun umschalten(k: String) {
        zustand.value = if (k in zustand.value) zustand.value - k else zustand.value + k
        merker.zustand<ListenModell?>("modell/oelwelt") { null }.value = oelWeltModell(merker, d, zustand.value)
    }
    val zeilen = ArrayList<ZeilenDaten>()
    val istMs = OelWelt.ms(d.istMonat)
    val monat = java.time.LocalDate.ofEpochDay(istMs / 86_400_000L).let { Fmt.monthYear(java.time.YearMonth.of(it.year, it.monthValue)) }
    zeilen.add(ZeilenDaten("unter") { PunktText(S.roh("ow.unter", listOf(monat)), Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp)) })

    // Eine Tabellenzeile aus einer Reihe; stellen: Nachkommastellen des Werts
    fun reihe(key: String, name: String, w: List<Double?>, stellen: Int = 2, stufe: Int = 0, pfeil: Boolean? = null, unter: String? = null, onClick: (() -> Unit)? = null) {
        val wert = OelWelt.wert(w) ?: return
        val verlauf = OelWelt.verlauf(w)
        zeilen.add(ZeilenDaten(key) { OelZeile(name, verlauf, Fmt.num(wert.jetzt, stellen), wert.aenderung, stufe, pfeil, unter, onClick) })
    }
    reihe("k-foerderung", S.roh("ow.foerderung"), d.foerderung.welt)
    reihe("k-verbrauch", S.roh("ow.verbrauch"), d.verbrauch.welt)
    reihe("k-opecplus", S.roh("ow.opecplus"), d.rohoel.opecplus)
    reihe("k-frei", S.roh("ow.frei"), d.rohoel.frei)
    reihe("k-ausfall", S.roh("ow.ausfall"), d.rohoel.ausfallOpec.zip(d.rohoel.ausfallAndere) { a, b -> if (a == null || b == null) null else a + b })
    reihe("k-lager", S.roh("ow.lager"), d.lager.oecd, stellen = 0)
    // Prognose der EIA: Mittel des naechsten vollen Kalenderjahres
    d.prognose?.let { p ->
        val ab = d.istMonat + 1
        OelWelt.jahresmittel(p.foerderung, ab).lastOrNull()?.let { (jahr, v) ->
            zeilen.add(ZeilenDaten("p-foerderung") { KeyValueRow(S.roh("ow.prognoseF", listOf(jahr.toString())), Fmt.num(v, 2)) })
        }
        OelWelt.jahresmittel(p.verbrauch, ab).lastOrNull()?.let { (jahr, v) ->
            zeilen.add(ZeilenDaten("p-verbrauch") { KeyValueRow(S.roh("ow.prognoseV", listOf(jahr.toString())), Fmt.num(v, 2)) })
        }
    }

    // Regionen mit Laendern: Kopfzeile, aufgeklappt je Region eine Zeile, darunter ihre Laender und der Rest
    fun gruppen(kennung: String, gruppen: List<OelWelt.Gruppe>) {
        val auf = kennung in offen
        val laender = gruppen.sumOf { it.laender.size }
        zeilen.add(ZeilenDaten("kopf-$kennung") {
            AufklappZeile(S.roh("ow.jeLand.$kennung"), S.roh("ow.jeLandUnter", listOf(gruppen.size.toString(), laender.toString())), auf) { umschalten(kennung) }
        })
        if (!auf) return
        gruppen.sortedByDescending { OelWelt.wert(it.w)?.jetzt ?: 0.0 }.forEach { g ->
            val gk = "$kennung/${g.id}"
            val gAuf = gk in offen
            val klappbar = g.laender.isNotEmpty()
            reihe(
                "g-$gk", S.roh("ow.r." + g.id), g.w, stufe = 1, pfeil = if (klappbar) gAuf else null,
                onClick = if (klappbar) ({ umschalten(gk) }) else null,
            )
            if (!gAuf) return@forEach
            g.laender.forEach { l -> reihe("l-$gk-${l.a2}", landName(l), l.w, stufe = 2) }
            // Rest der Region als Reihe: Summe minus aufgefuehrte Laender
            val rest = g.w.indices.map { OelWelt.rest(g, it) }
            if ((OelWelt.wert(rest)?.jetzt ?: 0.0) >= 0.005) reihe("l-$gk-rest", S.roh("ow.rest"), rest, stufe = 2)
        }
    }
    gruppen("F", d.foerderung.gruppen)
    gruppen("V", d.verbrauch.gruppen)

    // Rohoel der OPEC+ je Land, mit Vorgabe, wenn die Datei eine traegt
    val auf = "O" in offen
    val opec = d.rohoel.laender.filter { it.opecplus }
    zeilen.add(ZeilenDaten("kopf-O") { AufklappZeile(S.roh("ow.jeLand.O"), S.roh("ow.laender", listOf(opec.size.toString())), auf) { umschalten("O") } })
    if (auf) {
        val erfuellung = OelWelt.erfuellung(d).associateBy { it.land.a2 }
        opec.forEach { l ->
            val e = erfuellung[l.a2]
            val unter = e?.let {
                val abw = Math.round(it.abweichung).toInt()
                val richtung = S.roh(if (abw > 0) "ow.ueber" else if (abw < 0) "ow.unterVorgabe" else "ow.gleichVorgabe", listOf(Fmt.num(kotlin.math.abs(abw).toDouble(), 0)))
                S.roh("ow.vorgabe", listOf(Fmt.num(it.vorgabe / 1000, 2), richtung))
            }
            reihe("o-${l.a2}", landName(l), l.w, stufe = 1, unter = unter)
        }
    }
    return ListenModell(
        titel = S.roh("ow.titel"), gross = true, kern = null, zeilen = zeilen, immerOffen = true,
        info = S.roh("ow.info", listOf(Fmt.date(de.troetenrepublik.app.engine.Alter.tag(d.stand)?.let { it * 86_400_000L + 43_200_000L } ?: istMs))),
    )
}

// Tabellenzeile: Name (darf umbrechen), kleine Verlaufslinie, Wert und Aenderung zum Vorjahr in festen Spalten, damit die
// Zahlen untereinander stehen und nie umbrechen. stufe rueckt ein (Region, Land).
@Composable
private fun OelZeile(name: String, verlauf: List<Double>, wert: String, aenderung: Double?, stufe: Int, pfeil: Boolean?, unter: String?, onClick: (() -> Unit)?) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 40.dp)
            .padding(start = 20.dp + 12.dp * stufe, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f, fill = false), style = if (stufe == 2) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall)
                if (pfeil != null) {
                    Spacer(Modifier.width(6.dp))
                    AufklappPfeil(pfeil)
                }
            }
            unter?.let { PunktText(it) }
        }
        Spacer(Modifier.width(8.dp))
        VerlaufsLinie(verlauf, Modifier.size(width = 44.dp, height = 18.dp))
        Text(wert, Modifier.width(62.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
        Box(Modifier.width(74.dp), contentAlignment = Alignment.CenterEnd) {
            if (aenderung != null) ChangeValue(aenderung, Fmt.pct(kotlin.math.abs(aenderung), 1), style = MaterialTheme.typography.bodySmall, colored = false)
        }
    }
}

// Kleine Linie des Verlaufs ohne Achsen, in der Farbe der Schrift
@Composable
private fun VerlaufsLinie(werte: List<Double>, modifier: Modifier) {
    val farbe = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        if (werte.size < 2) return@Canvas
        val min = werte.min()
        val max = werte.max()
        val spanne = (max - min).takeIf { it > 0 } ?: 1.0
        val rand = 1.5.dp.toPx()
        val pfad = Path()
        werte.forEachIndexed { i, v ->
            val x = size.width * i / (werte.size - 1)
            val y = rand + (size.height - 2 * rand) * (1f - ((v - min) / spanne).toFloat())
            if (i == 0) pfad.moveTo(x, y) else pfad.lineTo(x, y)
        }
        drawPath(pfad, farbe, style = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val letzter = werte.last()
        drawCircle(farbe, 1.75.dp.toPx(), Offset(size.width - rand, rand + (size.height - 2 * rand) * (1f - ((letzter - min) / spanne).toFloat())))
    }
}
