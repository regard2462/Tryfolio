package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.IpoBlatt
import de.troetenrepublik.app.engine.IpoProspekt
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.IpoTexte
import kotlin.math.abs

// Grafiken des Detailblatts eines Boersengangs (0.78): Preisspanne, Groesse gegen die anderen Angebote, der erste
// Handelstag anderer Boersengaenge, ihr Kurs nach dem Start, Anteilseigner und Banken aus dem Prospekt.

internal fun ipoText(key: String, vararg w: String): String = IpoTexte.text(I18n.lang == Lang.DE, key, w.toList())

// Abstand eines Tags zu heute in Worten.
internal fun ipoTageText(tage: Long): String = when {
    tage > 1 -> ipoText("tage.in", Fmt.num(tage.toDouble(), 0))
    tage == 1L -> ipoText("tage.morgen")
    tage == 0L -> ipoText("tage.heute")
    tage == -1L -> ipoText("tage.gestern")
    else -> ipoText("tage.vor", Fmt.num(abs(tage).toDouble(), 0))
}

// Preisspanne: Balken mit Strich in der Mitte, darunter kleinster Preis, Mitte und hoechster Preis.
@Composable
internal fun IpoSpanne(von: Double, bis: Double, modifier: Modifier = Modifier) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val stil = MaterialTheme.typography.bodySmall
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val h = 8.dp.toPx()
            val oben = (size.height - h) / 2
            drawRoundRect(bahn, Offset(0f, oben), Size(size.width, h), CornerRadius(h / 2))
            drawLine(grau, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 1.5.dp.toPx())
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(ipoPreisEinzeln(von), style = stil, maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                de.troetenrepublik.app.i18n.S.ipoSpanneMitte + " " + ipoPreisEinzeln((von + bis) / 2), style = stil, color = grau,
                maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.Center),
            )
            Text(ipoPreisEinzeln(bis), style = stil, maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

// Volumen dieses Angebots, Median der anderen und das groesste andere als Balken auf derselben Skala.
@Composable
internal fun IpoGroesseBalken(g: IpoBlatt.Groesse) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // Dieses Angebot in der ersten, Median und groesstes Angebot in den folgenden Farben der gemeinsamen Folge
        LageBalkenZeile(ipoText("groesse.dieses"), ipoDollar(g.volumen), g.anteil, AnteilsFarben.folge(0), null)
        LageBalkenZeile(ipoText("groesse.median"), ipoDollar(g.median), g.anteilMedian, AnteilsFarben.folge(1), null)
        if (g.ausserSkala) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(g.groesstesName, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.weight(1f))
                Text(ipoDollar(g.groesstes), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            }
        } else LageBalkenZeile(g.groesstesName, ipoDollar(g.groesstes), g.anteilGroesstes, AnteilsFarben.folge(2), null)
    }
}

// Erster Handelstag je Boersengang als Balken um die Nulllinie, aufsteigend; dieses Angebot kraeftig, die anderen
// blass. Unter den Enden stehen der schwaechste und der staerkste Wert.
@Composable
internal fun IpoErsteTageBalken(e: IpoBlatt.ErsteTage) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val stil = MaterialTheme.typography.bodySmall
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val n = e.werte.size
            val luecke = 2.dp.toPx()
            val w = ((size.width - luecke * (n - 1)) / n).coerceAtLeast(1f)
            val hoch = maxOf(e.staerkster, 0.0)
            val tief = minOf(e.schwaechster, 0.0)
            val spanne = (hoch - tief).takeIf { it > 1e-9 } ?: 1.0
            val null_ = (size.height * (hoch / spanne)).toFloat()
            val mindest = 1.dp.toPx()
            e.werte.forEachIndexed { i, v ->
                val h = maxOf((size.height * (abs(v) / spanne)).toFloat(), mindest)
                val farbe = (if (v > 0) signal.up else if (v < 0) signal.down else grau).copy(alpha = if (e.eigen < 0 || i == e.eigen) 1f else 0.45f)
                drawRect(farbe, Offset(i * (w + luecke), if (v >= 0) null_ - h else null_), Size(w, h))
            }
            drawLine(grau.copy(alpha = 0.6f), Offset(0f, null_), Offset(size.width, null_), strokeWidth = 1.dp.toPx())
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                Fmt.signedPct(e.schwaechster, 1), style = stil, color = signal.forChange(e.schwaechster), maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                Fmt.signedPct(e.staerkster, 1), style = stil, color = signal.forChange(e.staerkster), maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        // Der kraeftige Balken bekommt seinen Namen
        if (e.eigen >= 0) {
            val v = e.werte[e.eigen]
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 6.dp, height = 12.dp).background(if (v > 0) signal.up else if (v < 0) signal.down else grau))
                Spacer(Modifier.width(8.dp))
                Text(ipoText("erste.eigen"), style = stil, color = grau)
            }
        }
    }
}

// Zahl ohne Nachkommastellen mit Tausenderpunkt
internal fun ipoAnzahl(n: Int): String = Fmt.num(n.toDouble(), 0)

// „Median −3,2 % · 212 Börsengänge“ unter dem Balken einer Frist; jahre: Zeitraum der Stichprobe.
internal fun ipoFristZusatz(f: IpoBlatt.Frist, jahre: String? = null): String = ipoText(
    "verlauf.zusatz", Fmt.signedPct(f.median, 1),
    if (jahre == null) ipoText("verlauf.anzahl", ipoAnzahl(f.anzahl)) else ipoText("verlauf.anzahlJahre", ipoAnzahl(f.anzahl), jahre),
)

// Eine Frist nach dem Start: Anteil unter dem Ausgabepreis als roter Teil des Balkens, der Rest gruen; darunter
// Median und Fallzahl.
@Composable
internal fun IpoFristZeile(f: IpoBlatt.Frist, zusatz: String) {
    val signal = LocalSignal.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        LageBalkenZeile(
            ipoText("verlauf." + f.tage), Fmt.pct(f.anteilUnter * 100.0, 0), 0.0, signal.down, null,
            balken = { LageBalken(1.0, signal.up, vorn = f.anteilUnter, vornFarbe = signal.down) },
        )
        Text(zusatz, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
    }
}

// Ein Anteilseigner: Name, Anteil vor dem Angebot und Balken gegen den groessten.
@Composable
internal fun IpoHalterZeile(h: IpoProspekt.Halter, groesster: Double) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        LageBalkenZeile(h.name, Fmt.pct(h.anteil, 1), if (groesster > 0) h.anteil / groesster else 0.0, AnteilsFarben.folge(h.name.hashCode()), null)
    }
}

// Eine Bank: mit Stueckzahlen ihr Anteil an der Zuteilung als Balken, sonst nur der Name.
@Composable
internal fun IpoBankZeile(b: IpoProspekt.Bank, summe: Long, groesste: Long) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val aktien = b.aktien
        if (aktien != null && summe > 0) {
            LageBalkenZeile(b.name, Fmt.pct(aktien * 100.0 / summe, 0), aktien.toDouble() / groesste, AnteilsFarben.folge(b.name.hashCode()), null)
        } else Text(b.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 5.dp))
    }
}

// Stueckzahl kompakt, ohne Umbruch im Wert
internal fun ipoStueck(n: Long): String = Fmt.compact(n.toDouble()).replace(' ', ' ')

// „7,5 Mio. (60 %)“ rechts neben dem Namen eines Balkens
private fun ipoStueckAnteil(n: Long, ganz: Long, stellen: Int = 0): String =
    ipoStueck(n) + " (" + Fmt.pct(n * 100.0 / ganz, stellen) + ")"

// Angebot laut Prospekt: frei handelbarer Anteil, Aufteilung neue Aktien gegen Verkauf der Altaktionaere,
// Mehrzuteilung, Gattungen mit Stimmen und die Aktien, die mit dem Ende der Sperrfrist dazukommen. Was der
// Prospekt nicht nennt, hat keine Zeile. ende: Datum, an dem eine Frist nach so vielen Tagen ablaeuft (falls bekannt).
internal fun LazyListScope.ipoAngebot(prospekt: IpoProspekt.Auszug, ende: (Int) -> String?) {
    val ang = prospekt.angebot ?: return
    val angeboten = ang.angeboten?.takeIf { it > 0 }
    val danach = ang.danach
    val frei = ang.frei
    val neu = ang.neu
    val verkauf = ang.verkauf
    val teilung = angeboten != null && neu != null && verkauf != null && neu + verkauf == angeboten
    val gattungen = if (danach != null && danach > 0 && abs(ang.gattungen.sumOf { it.aktien } - danach) <= danach / 10_000) ang.gattungen.filter { it.aktien * 10_000L >= danach } else emptyList()
    val freigabe = if (prospekt.sperrfristTage != null) IpoProspekt.freigabe(ang, prospekt.gesperrt) else null
    if (frei == null && danach == null && !teilung && ang.mehrzuteilung == null && gattungen.isEmpty() && freigabe == null) return
    item(key = "ang-titel") { SectionHeader(ipoText("angebot.titel")) }
    if (frei != null) item(key = "ang-frei") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            LageBalkenZeile(ipoText("angebot.frei"), Fmt.pct(frei, 1), frei / 100.0, AnteilsFarben.folge(0), null)
        }
    }
    if (danach != null) item(key = "ang-danach") { KeyValueRow(ipoText("angebot.danach"), ipoStueck(danach)) }
    if (teilung && angeboten != null && neu != null && verkauf != null) {
        if (neu > 0 && verkauf > 0) item(key = "ang-teilung") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                LageBalkenZeile(ipoText("angebot.neu"), ipoStueckAnteil(neu, angeboten), neu.toDouble() / angeboten, AnteilsFarben.folge(1), null)
                LageBalkenZeile(ipoText("angebot.verkauf"), ipoStueckAnteil(verkauf, angeboten), verkauf.toDouble() / angeboten, AnteilsFarben.folge(2), null)
            }
        } else item(key = "ang-teilung") {
            val nurNeu = neu > 0
            KeyValueRow(
                ipoText("angebot.herkunft"), ipoText(if (nurNeu) "angebot.nurNeu" else "angebot.nurVerkauf"),
                hint = ipoText(if (nurNeu) "angebot.nurNeuHint" else "angebot.nurVerkaufHint"),
            )
        }
    }
    ang.mehrzuteilung?.let { m ->
        item(key = "ang-mehr") {
            KeyValueRow(
                ipoText("angebot.mehr"), ipoStueck(m),
                hint = angeboten?.let { ipoText("angebot.mehrAnteil", Fmt.pct(m * 100.0 / it, 0)) },
            )
        }
    }
    if (gattungen.isNotEmpty()) {
        item(key = "ang-gattungen") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                gattungen.forEachIndexed { i, g ->
                    val name = g.stimmen?.let { s ->
                        ipoText("angebot.klasseMit", g.name, if (s == 1) ipoText("angebot.stimme1") else ipoText("angebot.stimmen", s.toString()))
                    } ?: ipoText("angebot.klasse", g.name)
                    LageBalkenZeile(name, ipoStueckAnteil(g.aktien, danach ?: 1L, 1), g.aktien.toDouble() / (danach ?: 1L), AnteilsFarben.folge(3 + i), null)
                }
            }
        }
        ang.stimmenAnteil?.let { x -> item(key = "ang-stimmen") { KeyValueRow(ipoText("angebot.stimmanteil"), Fmt.pct(x, 1)) } }
    }
    if (freigabe != null && danach != null && danach > 0) item(key = "ang-freigabe") {
        val tage = prospekt.sperrfristTage ?: 0
        val wann = ende(tage)?.let { ipoText("freigabe.ab", it) } ?: ipoText("freigabe.nach", Fmt.num(tage.toDouble(), 0))
        KeyValueRow(
            ipoText("freigabe"), ipoStueck(freigabe),
            hint = ipoText("freigabe.hint", Fmt.pct(freigabe * 100.0 / danach, 1), wann),
        )
    }
}

// Gestaffelte Freigabe der Altaktien: je Stufe die Tage und der Anteil, der bis dahin frei wird. Nur bei eindeutigen
// Stufen im Prospekt.
internal fun LazyListScope.ipoStufen(prospekt: IpoProspekt.Auszug, ende: (Int) -> String?) {
    if (prospekt.stufen.isEmpty()) return
    item(key = "stufen-titel") { SectionHeader(ipoText("stufen.titel")) }
    items(prospekt.stufen, key = { "stufe-" + it.tage }, contentType = { "stufe" }) { st ->
        KeyValueRow(
            ipoText("stufen.zeile", Fmt.num(st.tage.toDouble(), 0)), ipoText("stufen.bis", Fmt.pct(st.anteil, 0)),
            hint = ende(st.tage)?.let { ipoText("freigabe.ab", it) },
        )
    }
}
