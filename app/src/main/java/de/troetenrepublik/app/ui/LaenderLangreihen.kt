package de.troetenrepublik.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.Langreihen
import de.troetenrepublik.app.engine.Langreihen.Art
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// [Lange Reihen 0.78] Laenderblatt: Verbraucherpreise, kurzfristiger Zins und Staatsanleihen ueber Jahrhunderte aus den
// historischen Statistiken der Notenbanken. Kernzeile mit Inflation und mittleren Zinsen, aufgeklappt Preisniveau
// (logarithmisch), Zinsverlauf und die Inflation je Jahrhundert. Gilt fuer jedes Land der Datei.

// Datei einmal gelesen, gilt fuer alle Laender
private object LangreihenLader {
    @Volatile private var stand: Langreihen.Datei? = null
    @Volatile private var versucht = false

    fun gespeichert(): Langreihen.Datei? = stand

    suspend fun laden(): Langreihen.Datei? {
        if (versucht) return stand
        val d = withContext(Dispatchers.Default) {
            runCatching { Langreihen.lesen(de.troetenrepublik.app.market.Daten.text(Langreihen.DATEI)) }.getOrNull()
        }
        stand = d
        versucht = true
        return d
    }
}

@Composable
internal fun LaenderLangreihen(land: GlobusLand) {
    var datei by remember { mutableStateOf(LangreihenLader.gespeichert()) }
    LaunchedEffect(Unit) { if (datei == null) datei = LangreihenLader.laden() }
    val d = datei ?: return
    val l = d.laender[land.a3] ?: return
    val preise = l.reihen[Art.PREISE]
    val zins = l.reihen[Art.ZINS]
    val anleihe = l.reihen[Art.ANLEIHE]
    val beginn = l.reihen.values.minOf { it.von }
    var offen by rememberSaveable("lang-reihen") { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val signal = LocalSignal.current

    // Die Quelle verlangt die Nennung: einmal im Anhang des Blatts
    val sammler = LocalQuellenSammler.current
    SideEffect { if (sammler != null && sammler.none { it.name == l.quelle }) sammler.add(QuelleLink(l.quelle, l.adresse)) }
    val quelle = remember(l, d, I18n.locale) {
        val t = d.stand.split("-")
        val tag = if (t.size == 3 && I18n.locale.language == "de") t[2] + "." + t[1] + "." + t[0] else d.stand
        forschungText("lr.info") + "\n\n" + forschungText("lr.quelle", l.quelle) + "\n" + l.zitat.joinToString("\n\n") + "\n\n" +
            forschungText("lr.gerundet") + " " + S.histAbruf(tag)
    }

    val teuerung = remember(preise) { preise?.let { Langreihen.teuerung(it) } }
    val zinsMittel = remember(zins) { zins?.let { Langreihen.mittel(it) } }
    val anleiheMittel = remember(anleihe) { anleihe?.let { Langreihen.mittel(it) } }
    val kacheln = listOfNotNull(
        teuerung?.let { t ->
            KachelDaten(forschungText("lr.inflation"), Fmt.signedPct(t.proJahr, 1), forschungText("lr.proJahrAb", t.erstes.toString())) {
                info = forschungText("lr.inflation") to quelle
            }
        },
        zinsMittel?.let { m ->
            KachelDaten(forschungText("lr.zins"), Fmt.pct(m.wert, 1), forschungText("lr.mittelAb", m.erstes.toString())) { info = forschungText("lr.zins") to quelle }
        },
        anleiheMittel?.let { m ->
            KachelDaten(forschungText("lr.anleihe"), Fmt.pct(m.wert, 1), forschungText("lr.mittelAb", m.erstes.toString())) { info = forschungText("lr.anleihe") to quelle }
        },
    )
    if (kacheln.isEmpty()) return
    HistorieKopf(forschungText("lr.titel", beginn.toString()), offen) { offen = !offen }
    KachelRaster(kacheln, 3)
    if (offen) {
        if (preise != null) {
            val verlauf = remember(preise) { Langreihen.preisVerlauf(preise).map { it.first.toDouble() to it.second } }
            if (verlauf.size >= 3) {
                val titel = forschungText("lr.preise", preise.von.toString())
                LbUnterkopf(titel)
                HistDiagramm(
                    listOf(HistLinie(forschungText("lr.preisLinie"), verlauf, MaterialTheme.colorScheme.primary)),
                    verlauf.first().first, verlauf.last().first, log = true, marken = emptyList(),
                    achsenText = { "×" + Fmt.num(it, if (it < 1.0) 1 else 0) }, endeText = { "×" + Fmt.num(it, 0) }, beschreibung = titel,
                )
            }
        }
        val linien = listOfNotNull(
            zins?.let { r -> HistLinie(forschungText("lr.kurz"), Langreihen.punkte(r).map { it.first.toDouble() to it.second }, MaterialTheme.colorScheme.primary) },
            anleihe?.let { r -> HistLinie(forschungText("lr.lang"), Langreihen.punkte(r).map { it.first.toDouble() to it.second }, MaterialTheme.colorScheme.tertiary) },
        ).filter { it.punkte.size >= 3 }
        if (linien.isNotEmpty()) {
            val titel = forschungText("lr.zinsen")
            LbUnterkopf(titel)
            HistDiagramm(
                linien, linien.minOf { it.punkte.first().first }, linien.maxOf { it.punkte.last().first }, log = false, marken = emptyList(),
                achsenText = { Fmt.num(it, 0) }, endeText = { Fmt.num(it, 1) }, beschreibung = titel,
            )
        }
        if (preise != null) {
            val abschnitte = remember(preise) { Langreihen.jahrhunderte(preise) }
            if (abschnitte.isNotEmpty()) {
                LbUnterkopf(forschungText("lr.jahrhunderte"))
                for (t in abschnitte) LbZeile(
                    forschungText("lr.zeitraum", t.erstes.toString(), t.letztes.toString()), Fmt.signedPct(t.proJahr, 1),
                    if (t.proJahr > 0) signal.down else signal.up,
                )
            }
            remember(preise) { Langreihen.staerkstesJahr(preise) }?.let { (jahr, wert) ->
                LbZeile(forschungText("lr.staerkstes", jahr.toString()), Fmt.signedPct(wert, 0), signal.down)
            }
        }
    }
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}
