package de.troetenrepublik.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Ausblickgesamt
import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.Ausblickgesamt.Beitrag
import de.troetenrepublik.app.engine.Ausblickgesamt.Markt
import de.troetenrepublik.app.engine.Ausblickgesamt.Horizont
import de.troetenrepublik.app.engine.Ausblickgesamt.Urteil
import de.troetenrepublik.app.engine.Ersatzquellen
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.Tendenz
import de.troetenrepublik.app.engine.WeitereQuellen
import de.troetenrepublik.app.engine.Zinserwartung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Markterwartung

// [Ausblick 0.70] Gesamtaussage oben im Reiter Ausblick: Richtung fuer Aktien nach Wochen und Monaten, weitere
// Maerkte, darunter die Waage der Signale mit den staerksten Gruenden dafuer und dagegen, je Horizont. Die Rechnung
// steckt in engine/Ausblickgesamt.kt, hier nur das Einsammeln der Eintraege und die Darstellung.

// Alle Eintraege des Reiters mit ihrer Tendenz. Wahlen haben keine Marktrichtung und fehlen. alter: Tage seit dem Stand.
internal fun ausblickBeitraege(
    zins: Zinserwartung.Erwartung?,
    erwartet: List<AppState.Erwartet>?,
    fruehe: Map<String, Fruehindikatoren.Signal>?,
    ersatz: Map<String, Ersatzquellen.Signal>?,
    weitere: AppState.WeitereSignale?,
    heute: Long,
    nyFed: de.troetenrepublik.app.engine.RezessionNyFed.Reihe? = de.troetenrepublik.app.nyFedRezessionBekannt(),
): List<Beitrag> {
    val l = ArrayList<Beitrag>()
    // wert: Messwert zur Anzeige in den staerksten Gruenden; der Zustand in Worten kommt aus der Richtung.
    fun add(k: String, name: String, t: Tendenz.Lage, stand: Long? = null, wert: String = "") {
        val zustand = if (t.richtung == Tendenz.Richtung.NEUTRAL) "" else S.agZustand(k, t.richtung == Tendenz.Richtung.BULLISH)
        l += Beitrag(k, name, t.ziel, t.richtung, if (stand == null) 0L else heute - stand, zustand, wert)
    }
    zins?.let { add("zins", S.zeTitel, Tendenz.ausZins(de.troetenrepublik.app.engine.Ausblickdeutung.zinsRichtung(it.schritte)), wert = Fmt.num((it.in12 ?: it.heute).zins, 2) + " %") }
    erwartet?.forEach { x ->
        val k = x.thema.schluessel
        if (k == "wahlen") return@forEach
        val ausgaenge = Markterwartung.ausgaenge(x.ereignis, 50)
        val ja = ausgaenge.singleOrNull()?.second ?: ausgaenge.maxByOrNull { it.second }?.second
        add(k, S.meThema(k), Tendenz.erwartung(k, ausgaenge, zins?.schritte), wert = ja?.let { Fmt.num(it * 100, 0) + " %" }.orEmpty())
    }
    // Rezessionswahrscheinlichkeit der New York Fed; Stand ist das Ende des Monats der letzten Zinskurve
    nyFed?.let { r ->
        de.troetenrepublik.app.engine.RezessionNyFed.aktuell(r)?.let { (_, p) ->
            val k = de.troetenrepublik.app.engine.NyFedMessung.SCHLUESSEL
            add(k, S.nyName, de.troetenrepublik.app.engine.NyFedMessung.lage(p), de.troetenrepublik.app.engine.RezessionNyFed.ms(r.stand + 1) / 86_400_000L, Fmt.num(p, 0) + " %")
        }
    }
    fruehe?.values?.forEach { x -> if (x.schluessel in Fruehindikatoren.AUSBLICK) add(x.schluessel, S.fiName(x.schluessel), Tendenz.fruehsignal(x), x.stand, fiWert(x)) }
    ersatz?.values?.forEach { x ->
        val wert = if (x.schluessel == Ersatzquellen.LIEFERKETTEN) ersVorzeichen(x.wert, 2) else x.jahr?.let { Fmt.signedPct(it, 1) }.orEmpty()
        add(x.schluessel, S.ersName(x.schluessel), Tendenz.ersatz(x.schluessel, x.ampel), x.stand, wert)
    }
    weitere?.wci?.let { add("wci", S.wqName("wci"), Tendenz.containerfracht(it.woche), it.tag, Fmt.signedPct(it.woche, 1)) }
    weitere?.bahn?.let { add("aar", S.wqName("aar"), Tendenz.ersatz("aar", WeitereQuellen.vorjahrAmpel(it.gesamt)), it.ende, Fmt.signedPct(it.gesamt, 1)) }
    weitere?.halbleiter?.let { add("wsts", S.wqName("wsts"), Tendenz.ersatz("wsts", WeitereQuellen.vorjahrAmpel(it.vorjahr)), it.stand, Fmt.signedPct(it.vorjahr, 1)) }
    return l
}

@Composable
private fun urteilFarbe(u: Urteil): Color = when (u) {
    Urteil.AUFWAERTS -> LocalSignal.current.up
    Urteil.ABWAERTS -> LocalSignal.current.down
    else -> MaterialTheme.colorScheme.onSurface
}

// Quellen mit Gewicht und Takt als Zeilen fuer den Infotext (die Gewichte sind gesetzt, siehe S.agInfo).
internal fun ausblickInfoText(): String {
    fun name(k: String): String = when {
        k == "zins" -> S.zeTitel
        k == de.troetenrepublik.app.engine.NyFedMessung.SCHLUESSEL -> S.nyName
        k in Fruehindikatoren.AUSBLICK -> S.fiName(k)
        k == "wci" || k == "aar" || k == "wsts" -> S.wqName(k)
        k in setOf(Ersatzquellen.LIEFERKETTEN, Ersatzquellen.FRACHT, Ersatzquellen.BAHN, Ersatzquellen.HALBLEITER) -> S.ersName(k)
        else -> S.meThema(k)
    }
    val zeilen = Ausblickgesamt.tabelle().joinToString("\n") { (k, q) -> S.agQuelleZeile(name(k), Fmt.num(q.basis, 1), q.takt <= 7) }
    return S.agInfo + "\n" + zeilen + "\n" + S.agInfoRest(Fmt.num(Ausblickgesamt.quelle("").basis, 1)) + "\n\n" + S.agStaerkeInfo
}

// Karte mit der Aussage: Aktien nach Wochen und Monaten, darunter die weiteren Maerkte in einer Tabelle.
// blasenLage: Blasenzeichen als eigener Risikohinweis, ohne Einfluss auf die Richtung. zumDetektor: Sprung zum Blasen-Detektor.
@Composable
internal fun GesamtKarte(beitraege: List<Beitrag>, blasenLage: AppState.BlasenLage?, zumDetektor: () -> Unit) {
    val blasen = blasenLage?.indikatoren.orEmpty()
    val blasenRot = blasen.count { it.ampel == Fruehwarnung.Ampel.ROT }
    val einheiten = remember(beitraege) { Ausblickgesamt.einheiten(beitraege) }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    fun erg(m: Markt, h: Horizont?) = Ausblickgesamt.auswerten(einheiten, m, h)
    val monate = erg(Markt.AKTIEN, Horizont.MITTEL)
    val wochen = erg(Markt.AKTIEN, Horizont.KURZ)
    val weitere = listOf(Markt.OEL, Markt.GAS, Markt.ANLEIHEN, Markt.GOLD, Markt.GETREIDE).filter { m -> einheiten.any { it.markt == m } }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        Text(S.agMarkt(Markt.AKTIEN.ordinal), style = MaterialTheme.typography.titleSmall)
        ZeitBlock(false, wochen, Modifier.padding(top = 4.dp))
        ZeitBlock(true, monate, Modifier)
        if (blasenLage != null && blasen.isNotEmpty()) BlasenHinweis(blasenRot, blasen.size, zumDetektor)
        if (weitere.isNotEmpty()) {
            Text(S.agWeitere, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 18.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(S.agWochen, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.width(WORT_BREITE), textAlign = TextAlign.End)
                Text(S.agMonate, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.width(WORT_BREITE), textAlign = TextAlign.End)
            }
            for (m in weitere) MarktZeile(m, erg(m, Horizont.KURZ), erg(m, Horizont.MITTEL))
            if (weitere.any { m -> erg(m, Horizont.KURZ).urteil == Urteil.ZU_WENIG || erg(m, Horizont.MITTEL).urteil == Urteil.ZU_WENIG }) {
                Text(S.agZuWenig, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 6.dp), maxLines = 1, softWrap = false)
            }
        }
        Text(S.agHinweis, style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 14.dp))
    }
}

private val WORT_BREITE = 84.dp

// Risikohinweis zu den Blasenzeichen: nur die Zahl der roten Zeichen. Ein Tipp springt zum Blasen-Detektor weiter unten,
// der die Zeichen einzeln zeigt.
@Composable
private fun BlasenHinweis(rot: Int, gesamt: Int, zumDetektor: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = zumDetektor).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(S.agBlasenTitel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            S.agBlasenZahl(rot, gesamt), style = MaterialTheme.typography.labelLarge,
            color = if (rot > 0) LocalSignal.current.warn else grau, maxLines = 1, softWrap = false,
        )
        Spacer(Modifier.width(8.dp))
        ChevronRight()
    }
}

// Ein Zeitraum: Bezeichnung links (Staerke klein darunter), Richtung rechts in der Farbe des Urteils. Die Anteile
// stehen in der Waage darunter.
@Composable
private fun ZeitBlock(monate: Boolean, e: Ausblickgesamt.Ergebnis, modifier: Modifier) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val staerke = if (e.urteil == Urteil.AUFWAERTS || e.urteil == Urteil.ABWAERTS) e.staerke.ordinal else null
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(S.agZeile(monate), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (staerke != null) Text(S.agStaerke(staerke), style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(S.agRichtung(e.urteil.ordinal), style = MaterialTheme.typography.titleMedium, color = urteilFarbe(e.urteil), maxLines = 1, softWrap = false)
    }
}

// Ein Markt: Name links, Wort fuer Wochen und Monate rechts, jeweils eine Zeile.
@Composable
private fun MarktZeile(m: Markt, kurz: Ausblickgesamt.Ergebnis, mittel: Ausblickgesamt.Ergebnis) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(S.agMarkt(m.ordinal), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        // Ohne genug Signale steht ein grauer Strich, die Zeile unter der Tabelle nennt den Grund
        for (e in listOf(kurz, mittel)) Text(
            if (e.urteil == Urteil.ZU_WENIG) "–" else S.agWort(e.urteil.ordinal), style = MaterialTheme.typography.labelLarge,
            color = if (e.urteil == Urteil.ZU_WENIG) MaterialTheme.colorScheme.onSurfaceVariant else urteilFarbe(e.urteil),
            modifier = Modifier.width(WORT_BREITE), textAlign = TextAlign.End, maxLines = 1, softWrap = false,
        )
    }
}

// Waage: Balken der Gewichte von positiv, neutral und negativ mit den Anteilen darunter und den staerksten Gruenden
// je Seite, fuer Wochen oder Monate wie die Aussage darueber. Ein Tipp auf einen Grund springt zu seinem Eintrag.
@Composable
internal fun WaageKarte(beitraege: List<Beitrag>, zumEintrag: (String) -> Unit) {
    var monate by remember { mutableStateOf(true) }
    val e = remember(beitraege, monate) { Ausblickgesamt.berechnen(beitraege, Markt.AKTIEN, if (monate) Horizont.MITTEL else Horizont.KURZ) }
    val sonstige = remember(beitraege) { Ausblickgesamt.berechnen(beitraege, Markt.AKTIEN).anzahl }
    if (sonstige == 0) return
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val neutral = grau.copy(alpha = 0.4f)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        Text(S.agWaageTitel, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(S.agWochen, active = !monate) { monate = false }
            Chip(S.agMonate, active = monate) { monate = true }
        }
        if (e.anzahl == 0) return@Column
        val gesamt = e.gewichtPositiv + e.gewichtNeutral + e.gewichtNegativ
        Canvas(Modifier.fillMaxWidth().padding(top = 10.dp).height(12.dp)) {
            val luecke = 2.dp.toPx()
            val teile = listOf(e.gewichtPositiv to signal.up, e.gewichtNeutral to neutral, e.gewichtNegativ to signal.down).filter { it.first > 0 }
            val nutz = size.width - luecke * (teile.size - 1)
            var x = 0f
            for ((g, f) in teile) {
                val w = (nutz * (g / gesamt)).toFloat()
                drawRoundRect(f, Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2))
                x += w + luecke
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(S.agPositiv(e.prozentPositiv), style = MaterialTheme.typography.labelMedium, color = signal.up, maxLines = 1, softWrap = false)
            Text(S.agNeutral(e.prozentNeutral), style = MaterialTheme.typography.labelMedium, color = grau, maxLines = 1, softWrap = false)
            Text(S.agNegativ(e.prozentNegativ), style = MaterialTheme.typography.labelMedium, color = signal.down, maxLines = 1, softWrap = false)
        }
        if (e.treiberPositiv.isNotEmpty()) TreiberListe(S.agDafuer, e.treiberPositiv, zumEintrag)
        if (e.treiberNegativ.isNotEmpty()) TreiberListe(S.agDagegen, e.treiberNegativ, zumEintrag)
    }
}

@Composable
private fun TreiberListe(titel: String, liste: List<Ausblickgesamt.Einheit>, zumEintrag: (String) -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Text(titel, style = MaterialTheme.typography.labelMedium, color = grau, modifier = Modifier.padding(top = 14.dp))
    for (x in liste) Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { zumEintrag(x.abschnitt) }.heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(x.zustand.ifEmpty { x.name }, style = MaterialTheme.typography.bodyMedium)
            StaerkeBalken(ausblickStaerke(x.schluessel), Modifier.padding(top = 3.dp))
        }
        if (x.wertText.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(x.wertText, style = MaterialTheme.typography.labelLarge, color = grau, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.width(8.dp))
        ChevronRight()
    }
}
