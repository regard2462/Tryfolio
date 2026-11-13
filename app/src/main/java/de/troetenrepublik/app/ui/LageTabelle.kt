package de.troetenrepublik.app.ui

import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Durchbrueche
import de.troetenrepublik.app.engine.Enso
import de.troetenrepublik.app.engine.Engpaesse
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.Imf
import de.troetenrepublik.app.engine.Konflikte
import de.troetenrepublik.app.engine.LageErklaerungen
import de.troetenrepublik.app.engine.Lagebild
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import java.time.YearMonth
import kotlin.math.abs

// [0.78] Tabellen der Lage zum Aufklappen, im Stil der Durchbrueche: unter jeder Karte eine getoente Tabelle mit
// Unterabschnitten, die zugeklappt starten; aufgeklappt steht zuerst ein gedaempfter Satz, worum es geht, dann die Zeilen.
// Die letzte Zeile ist der einzige Weg aus der Karte hinaus (Globus, Ausblick, Durchbrueche). Alles andere oeffnet ein
// Blatt ueber der Lage (Land, Engpass, Spannungsgebiet, Durchbruch-Thema) oder klappt einen Unterabschnitt auf.
// Jede Zeile ist ein eigener Eintrag der Liste; die Karte und die getoente Flaeche zeichnen sich Stueck fuer Stueck.

// Was ein Tipp in der Lage ausloest. Nur Seite wechselt die Seite (der Weg am Tabellenende); alles andere bleibt in der Lage.
internal sealed interface LageTipp {
    data class Land(val a3: String) : LageTipp
    data class Engpass(val id: String) : LageTipp
    data class Spannung(val id: String) : LageTipp
    data class Durchbruch(val id: String) : LageTipp
    data class Abschnitt(val schluessel: String) : LageTipp
    data class Seite(val route: String) : LageTipp
}

internal object LageWege {
    // Tipp auf eine Zeile einer Tabelle; null, wenn die Zeile nichts oeffnet (Zinsmonate) oder mehrere Ziele hat
    // (Konflikt mehrerer Laender: dann ist jeder Landesname ein eigener Tipp)
    fun zeile(tabelle: String, z: Lagebild.Zeile): LageTipp? = when (tabelle) {
        "WACHSTUM_LAND", "INFLATION_LAND", "KATASTROPHEN_LAND" -> LageTipp.Land(z.id)
        "KRIEGE", "KONFLIKTE" -> z.teile.singleOrNull()?.let { LageTipp.Land(it) }
        "SPANNUNG" -> LageTipp.Spannung(z.id)
        "ENGPAESSE" -> LageTipp.Engpass(z.id)
        "DURCHBRUCH_THEMA" -> LageTipp.Durchbruch(z.id)
        else -> null
    }

    // Letzte Zeile der Tabelle eines Themas
    fun weg(t: Lagebild.Thema): LageTipp = LageTipp.Seite(Lagebild.weg(t))

    // Schluessel eines Unterabschnitts in der Merkliste der offenen Abschnitte
    fun abschnitt(t: Lagebild.Thema, tabelle: String): String = t.name + "." + tabelle

    // Bleibt die Lage nach dem Tipp sichtbar (Blatt darueber oder Aufklappen)?
    fun bleibtInDerLage(t: LageTipp): Boolean = t !is LageTipp.Seite
}

// Offene Unterabschnitte als Text (Schluessel mit Semikolon getrennt), damit rememberSaveable ihn ohne eigenen Speicher haelt
internal object LageOffenText {
    private const val TRENNER = ";"
    fun enthaelt(text: String, k: String): Boolean = text.split(TRENNER).contains(k)
    fun wechseln(text: String, k: String): String {
        val l = text.split(TRENNER).filter { it.isNotEmpty() }
        return (if (k in l) l - k else l + k).joinToString(TRENNER)
    }
    fun oeffnen(text: String, k: String): String = if (enthaelt(text, k)) text else wechseln(text, k)
}

@Stable
internal class LageOffen(start: String) {
    var text by mutableStateOf(start)
    fun ist(k: String): Boolean = LageOffenText.enthaelt(text, k)
    fun wechseln(k: String) { text = LageOffenText.wechseln(text, k) }
}

// Eine Zeile der Tabelle als eigener Eintrag der Liste
internal class LageZeile(val key: String, val inhalt: @Composable () -> Unit)

// Ein Unterabschnitt: Titel, Zusammenfassung rechts in der zugeklappten Zeile, Erklaerung, Kopfzeile, Zeilen (erst beim Aufklappen gebaut)
internal class LageAbschnitt(
    val schluessel: String, val titel: String, val anzahl: Int, val zusammenfassung: String?, val erklaerung: String?,
    val kopf: (@Composable () -> Unit)? = null, val mehrAb: Int = LAGE_SOFORT, val zeilen: () -> List<LageZeile>,
)

// So viele Zeilen stehen sofort da, der Pfeil darunter zeigt den Rest
internal const val LAGE_SOFORT = 15

// Zaehlt die Eintraege der Liste, damit ein Sprung aus dem Gesamtbild die Karte eines Themas findet
internal class LageIndex {
    var n = 0
    val karten = HashMap<String, Int>()
}

internal fun LazyListScope.lageEintrag(ix: LageIndex, key: String, typ: String, inhalt: @Composable () -> Unit) {
    ix.n++
    item(key = key, contentType = typ) { inhalt() }
}

// Stueck einer Karte, die ueber mehrere Eintraege laeuft: oben beginnt sie (gerundet, Abstand), unten endet sie
@Composable
internal fun KartenStueck(oben: Boolean, unten: Boolean, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = if (oben) 6.dp else 0.dp, bottom = if (unten) 6.dp else 0.dp)
            .clip(stueckForm(Radius.card, oben, unten)).background(MaterialTheme.colorScheme.surfaceContainer),
        content = inhalt,
    )
}

private val TABELLE_EINZUG = PaddingValues(start = 12.dp, end = 12.dp)

// Stueck der getoenten Tabelle in der Karte; tief: Stueck der tieferen Flaeche eines offenen Unterabschnitts (oben, unten)
@Composable
private fun TabellenStueck(oben: Boolean = false, unten: Boolean = false, tief: Pair<Boolean, Boolean>? = null, inhalt: @Composable () -> Unit) {
    KartenStueck(false, unten) {
        Getoent(TABELLE_EINZUG, oben, unten) {
            if (tief == null) inhalt() else Tiefer(tief.first, tief.second) { inhalt() }
        }
    }
}

// Teil der tieferen Flaeche eines offenen Unterabschnitts; trenner: feine Linie davor
private class TabellenTeil(val key: String, val typ: String, val trenner: Boolean, val inhalt: @Composable () -> Unit)

// Die Tabelle einer Karte: Unterabschnitte und als letzte Zeile der Weg hinaus
internal fun LazyListScope.lageTabelle(ix: LageIndex, thema: Lagebild.Thema, abschnitte: List<LageAbschnitt>, offen: LageOffen, weg: String, onWeg: () -> Unit) {
    abschnitte.forEachIndexed { i, a ->
        val k = LageWege.abschnitt(thema, a.schluessel)
        val auf = offen.ist(k)
        lageEintrag(ix, "t-$k", "lage-unter") {
            TabellenStueck(oben = i == 0) {
                if (i > 0) Fein()
                Unterabschnitt(a.titel, a.anzahl, auf, a.zusammenfassung, erklaerung = a.erklaerung) { offen.wechseln(k) }
            }
        }
        if (!auf) return@forEachIndexed
        val alle = a.zeilen()
        val gekuerzt = alle.size > a.mehrAb
        val alleOffen = offen.ist("$k.alle")
        // Teile der tieferen Flaeche: Kopf, Zeilen, Pfeil (die Erklaerung steht hinter dem Info-Symbol am Titel); Trennlinie vor jeder Zeile ausser der ersten
        val teile = ArrayList<TabellenTeil>()
        a.kopf?.let { kopf -> teile += TabellenTeil("t-$k-kopf", "lage-kopf-" + a.schluessel, false, kopf) }
        val zeilen = if (gekuerzt && !alleOffen) alle.take(a.mehrAb) else alle
        zeilen.forEachIndexed { j, z -> teile += TabellenTeil("t-$k-z-" + z.key, "lage-zeile-" + a.schluessel, j > 0, z.inhalt) }
        if (gekuerzt) teile += TabellenTeil("t-$k-mehr", "lage-mehr", false) { ListenPfeil(alleOffen) { offen.wechseln("$k.alle") } }
        teile.forEachIndexed { j, teil ->
            lageEintrag(ix, teil.key, teil.typ) {
                TabellenStueck(tief = (j == 0) to (j == teile.lastIndex)) {
                    if (teil.trenner) Fein()
                    teil.inhalt()
                }
            }
        }
    }
    lageEintrag(ix, "t-" + thema.name + "-weg", "lage-weg") {
        TabellenStueck(oben = abschnitte.isEmpty(), unten = true) {
            if (abschnitte.isNotEmpty()) Fein()
            WegZeile(weg, onWeg)
        }
    }
}

// Mindestbreite einer Zahlenspalte; breitere Werte machen die Spalte breiter statt umzubrechen
private val SPALTE = 56.dp

// Zeile der Tabelle: Name links (darf umbrechen), rechts optional Vergleichswert und Wert (brechen nie um), darunter
// gedaempfte Zeilen; unten ein eigener Teil (Laendernamen eines Konflikts). Mit onClick ohne Wert ein Pfeil rechts.
@Composable
private fun TabZeile(
    name: String, wert: String? = null, farbe: Color = MaterialTheme.colorScheme.onSurface, vorher: String? = null,
    unter: List<String> = emptyList(), onClick: (() -> Unit)? = null, unten: (@Composable () -> Unit)? = null,
    // Erklaerung der Zeile hinter dem Info-Symbol statt als offener Satz darunter
    erklaerung: (() -> Unit)? = null,
) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.zeilenKlick(onClick = onClick) else Modifier).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (vorher != null) {
                Spacer(Modifier.width(8.dp))
                Text(vorher, style = MaterialTheme.typography.labelLarge, color = grau, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = SPALTE))
            }
            if (wert != null) {
                Spacer(Modifier.width(8.dp))
                Text(wert, style = MaterialTheme.typography.labelLarge, color = farbe, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = SPALTE))
            } else if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                ChevronRight(color = grau.copy(alpha = 0.7f), modifier = Modifier.padding(top = 5.dp))
            }
            if (erklaerung != null) {
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung, tint = grau,
                    modifier = Modifier.size(18.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = erklaerung),
                )
            }
        }
        for (u in unter) Text(u, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
        unten?.invoke()
    }
}

// Kopf der Laendertabellen: Vorjahr und Jahr ueber den Zahlenspalten
@Composable
private fun JahresKopf(jahr: Int) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text((jahr - 1).toString(), style = MaterialTheme.typography.labelSmall, color = grau, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = SPALTE))
        Spacer(Modifier.width(8.dp))
        Text(jahr.toString(), style = MaterialTheme.typography.labelSmall, color = grau, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = SPALTE))
    }
}

private fun deutsch(): Boolean = I18n.lang == Lang.DE

private fun standText(tag: Long?): String? = tag?.takeIf { it > 0 }?.let { S.lbStand(Fmt.date(it * 86_400_000L + 43_200_000L)) }

private fun mitStand(text: String, tag: Long?): String = standText(tag)?.let { "$text $it" } ?: text

// Seite eines Konflikts kurz in der App-Sprache: Regierungen als Laendername (mehrere mit „und“), Gruppen wie in den Daten
internal fun konfliktSeite(seite: String, z: LageZiele, deutsch: Boolean): String {
    if (!seite.startsWith("Government of ")) return seite
    return seite.removePrefix("Government of ").split(", Government of ").joinToString(S.lbUnd) { teil ->
        landKurz(Konflikte.seiteAnzeige("Government of " + teil.trim(), z.karte, deutsch))
    }
}

// Titel eines Konflikts: gepflegter Titel, sonst beide Seiten
internal fun konfliktTitel(id: String, z: LageZiele, erk: LageErklaerungen.Datei?, deutsch: Boolean): String =
    erk?.konflikte?.get(id)?.titel(deutsch)
        ?: id.split("|").let { s -> S.lbGegen(konfliktSeite(s[0], z, deutsch), konfliktSeite(s.getOrElse(1) { "" }, z, deutsch)) }

// Unterabschnitte der Tabelle einer Karte, aus den Tabellen der Aussage, Kennzahlen (ENSO) und dem Inhalt der Durchbrueche
internal fun lageAbschnitte(
    a: Lagebild.Aussage, z: LageZiele, erk: LageErklaerungen.Datei?, beschleunigung: Durchbrueche.Beschleunigung?,
    info: (Pair<String, String>) -> Unit,
): List<LageAbschnitt> {
    val de = deutsch()
    val aus = ArrayList<LageAbschnitt>()
    fun tab(s: String) = a.tabellen.firstOrNull { it.schluessel == s }?.takeIf { it.zeilen.isNotEmpty() }
    fun tipp(t: Lagebild.Tabelle, r: Lagebild.Zeile): (() -> Unit)? = LageWege.zeile(t.schluessel, r)?.let { x -> { z.tippen(x) } }
    when (a.thema) {
        Lagebild.Thema.KONJUNKTUR -> {
            tab("WACHSTUM_LAND")?.let { t ->
                val minus = t.zeilen.count { (it.wert ?: 0.0) < 0.0 }
                aus += LageAbschnitt(t.schluessel, S.lbTab(t.schluessel), t.zeilen.size, minus.takeIf { it > 0 }?.let { S.lbImMinus(it) }, S.lbErk(t.schluessel, ""), { JahresKopf(t.jahr) }) {
                    t.zeilen.map { r -> LageZeile(r.id) { LandJahrZeile(r, z, inflation = false, onClick = tipp(t, r)) } }
                }
            }
            tab("INFLATION_LAND")?.let { t ->
                val schwelle = Fmt.pct(Imf.SCHWELLE_INFLATION_ERHOEHT, 0)
                val hoch = t.zeilen.count { (it.wert ?: 0.0) >= Imf.SCHWELLE_INFLATION_ERHOEHT }
                aus += LageAbschnitt(t.schluessel, S.lbTab(t.schluessel), t.zeilen.size, hoch.takeIf { it > 0 }?.let { S.lbAbSchwelle(it, schwelle) }, S.lbErk(t.schluessel, schwelle), { JahresKopf(t.jahr) }) {
                    t.zeilen.map { r -> LageZeile(r.id) { LandJahrZeile(r, z, inflation = true, onClick = tipp(t, r)) } }
                }
            }
        }
        // Zinsen ohne Tabelle: den Satz je Monat zeigt die Zinskarte im Ausblick, der Weg dorthin steht am Ende der Karte
        Lagebild.Thema.ZINSEN -> {}
        Lagebild.Thema.GEOPOLITIK -> {
            for (s in listOf("KRIEGE", "KONFLIKTE")) tab(s)?.let { t ->
                aus += LageAbschnitt(t.schluessel, S.lbTab(t.schluessel), t.zeilen.size, null, S.lbErk(t.schluessel, t.jahr.toString())) {
                    t.zeilen.map { r -> LageZeile(r.id) { KonfliktZeile(r, z, erk, de, tipp(t, r), info) } }
                }
            }
            tab("SPANNUNG")?.let { t ->
                val schwelle = Fmt.num(Lagebild.SPANNUNG_HOCH, 1) + "×"
                val hoechst = t.zeilen.maxOfOrNull { it.wert ?: 0.0 } ?: 1.0
                aus += LageAbschnitt(t.schluessel, S.lbTab(t.schluessel), t.zeilen.size, null, mitStand(S.lbErk(t.schluessel, schwelle), t.stand)) {
                    t.zeilen.map { r -> LageZeile(r.id) { SpannungZeile(r, erk?.gebiete?.get(r.id)?.satz(de), tipp(t, r), hoechst, info) } }
                }
            }
        }
        Lagebild.Thema.ROHSTOFFE -> tab("ENGPAESSE")?.let { t ->
            val schwelle = Fmt.pct(abs(Engpaesse.STOERUNG_PROZENT), 0)
            val gestoert = t.zeilen.count { (it.wert ?: 0.0) <= Engpaesse.STOERUNG_PROZENT && (it.vorher ?: 0.0) >= Engpaesse.MIND_VORJAHR }
            val groesste = t.zeilen.maxOfOrNull { abs(it.wert ?: 0.0) }?.takeIf { it > 0 } ?: 1.0
            aus += LageAbschnitt(t.schluessel, S.lbTab(t.schluessel), t.zeilen.size, gestoert.takeIf { it > 0 }?.let { S.lbGestoert(it) }, mitStand(S.lbErk(t.schluessel, schwelle), t.stand)) {
                t.zeilen.map { r -> LageZeile(r.id) { EngpassZeile(r, erk?.engpaesse?.get(r.id)?.satz(de), tipp(t, r), groesste, info) } }
            }
        }
        Lagebild.Thema.KLIMA -> {
            val zustand = a.kennzahlen.firstOrNull { it.schluessel == "ENSO_ZUSTAND" }
            val index = a.kennzahlen.firstOrNull { it.schluessel == "ENSO_INDEX" }
            if (zustand != null || index != null) {
                aus += LageAbschnitt("ENSO", S.lbTab("ENSO"), 0, zustand?.let { S.ensoZustand(it.zusatz.orEmpty()) }, S.lbErk("ENSO", "")) {
                    listOfNotNull(
                        zustand?.let { k -> LageZeile("status") { TabZeile(S.ensoStatus, S.ensoZustand(k.zusatz.orEmpty()), onClick = { info(S.ensoStatus to S.ensoStatusInfo) }) } },
                        index?.let { k ->
                            LageZeile("index") {
                                TabZeile(S.ensoIndex, lbWertText(k), unter = listOfNotNull(k.posten.lastOrNull()?.name), onClick = { info(S.ensoIndex to S.ensoIndexInfo) })
                            }
                        },
                    )
                }
            }
            tab("KATASTROPHEN_LAND")?.let { t ->
                aus += LageAbschnitt(t.schluessel, S.lbTab(t.schluessel), t.zeilen.size, null, mitStand(S.lbErk(t.schluessel, ""), t.stand)) {
                    t.zeilen.map { r -> LageZeile(r.id) { KatastrophenZeile(r, z, tipp(t, r)) } }
                }
            }
        }
        Lagebild.Thema.TECHNIK -> {
            tab("DURCHBRUCH_THEMA")?.let { t ->
                aus += LageAbschnitt(t.schluessel, S.lbTab(t.schluessel), t.zeilen.size, null, S.lbErk(t.schluessel, "")) {
                    t.zeilen.map { r -> LageZeile(r.id) { DurchbruchZeile(r, de, tipp(t, r)) } }
                }
            }
            val reihen = beschleunigung?.reihen.orEmpty().filter { r -> r.p.count { it.size >= 2 && it[1] > 0 } >= 2 }
            if (reihen.isNotEmpty()) {
                aus += LageAbschnitt("BESCHLEUNIGT", S.lbTab("BESCHLEUNIGT"), reihen.size, null, S.lbErk("BESCHLEUNIGT", "")) {
                    reihen.map { r ->
                        LageZeile(r.id) {
                            val name = r.name(de)
                            TabZeile(
                                name, unter = listOfNotNull(r.verdopplung()?.let { v -> S.dbRate(v.faellt, v.monate) }),
                                erklaerung = r.warum(de).ifBlank { null }?.let { w -> { info(name to w) } },
                            )
                        }
                    }
                }
            }
        }
    }
    return aus
}

// Land mit Wert im Jahr und im Vorjahr; Wachstum unter null in der Verlustfarbe, Inflation ab der Schwelle in der Warnfarbe
@Composable
private fun LandJahrZeile(r: Lagebild.Zeile, z: LageZiele, inflation: Boolean, onClick: (() -> Unit)?) {
    val signal = LocalSignal.current
    val w = r.wert ?: return
    val farbe = when {
        inflation && w >= Imf.SCHWELLE_INFLATION_ERHOEHT -> signal.warn
        !inflation && w < 0.0 -> signal.down
        else -> MaterialTheme.colorScheme.onSurface
    }
    TabZeile(z.landName(r.id), Fmt.pct(w, 1), farbe, vorher = r.vorher?.let { Fmt.pct(it, 1) } ?: "", onClick = onClick)
}

// Konflikt: Titel, darunter der Satz, worum es geht. Fuehren die Daten ihn unter mehreren Laendern, steht jedes Land als
// eigener Tipp darunter, sonst oeffnet die ganze Zeile das Land.
@Composable
private fun KonfliktZeile(r: Lagebild.Zeile, z: LageZiele, erk: LageErklaerungen.Datei?, deutsch: Boolean, onClick: (() -> Unit)?, info: (Pair<String, String>) -> Unit) {
    val satz = erk?.konflikte?.get(r.id)?.satz(deutsch)
    val laender: (@Composable () -> Unit)? = if (r.teile.size < 2) null else {
        {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (a3 in r.teile) {
                    Text(
                        z.landName(a3), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false,
                        modifier = Modifier.textKlick(0.dp, onClick = z.land(a3)).padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
    val titel = konfliktTitel(r.id, z, erk, deutsch)
    TabZeile(titel, onClick = onClick, unten = laender, erklaerung = satz?.let { s -> { info(titel to s) } })
}

@Composable
private fun SpannungZeile(r: Lagebild.Zeile, satz: String?, onClick: (() -> Unit)?, hoechst: Double, info: (Pair<String, String>) -> Unit) {
    val f = r.wert ?: return
    val farbe = if (f >= Lagebild.SPANNUNG_HOCH) LocalSignal.current.warn else MaterialTheme.colorScheme.onSurface
    val name = S.spName(r.id)
    TabZeile(
        name, Fmt.num(f, 1) + "×", farbe, onClick = onClick, erklaerung = satz?.let { s -> { info(name to s) } },
        unten = { Spacer(Modifier.height(4.dp)); LageBalken(if (hoechst > 0) f / hoechst else 0.0, farbe) },
    )
}

@Composable
private fun EngpassZeile(r: Lagebild.Zeile, satz: String?, onClick: (() -> Unit)?, groesste: Double, info: (Pair<String, String>) -> Unit) {
    val signal = LocalSignal.current
    val abw = r.wert
    val farbe = abw?.let { Color(GlobusFarbe.engpass(it, signal.up.toArgb(), signal.down.toArgb())) } ?: MaterialTheme.colorScheme.onSurface
    val name = S.engName(r.id)
    TabZeile(
        name, abw?.let { Fmt.signedPct(it, 0) } ?: Fmt.num(r.zahl.toDouble(), 0), farbe, onClick = onClick, erklaerung = satz?.let { s -> { info(name to s) } },
        unten = if (abw == null) null else ({ Spacer(Modifier.height(4.dp)); LageBalken(kotlin.math.abs(abw) / groesste, farbe) }),
    )
}

@Composable
private fun KatastrophenZeile(r: Lagebild.Zeile, z: LageZiele, onClick: (() -> Unit)?) {
    val signal = LocalSignal.current
    val n = (r.wert ?: 0.0).toInt()
    val text = if (r.zahl > 0) S.lbRot(n.toString(), r.zahl.toString()) else n.toString()
    val arten = r.teile.joinToString(", ") { S.rkKatArt(it) }
    TabZeile(z.landName(r.id), text, if (r.zahl > 0) signal.down else signal.warn, unter = listOfNotNull(arten.ifEmpty { null }), onClick = onClick)
}

@Composable
private fun DurchbruchZeile(r: Lagebild.Zeile, deutsch: Boolean, onClick: (() -> Unit)?) {
    val n = (r.wert ?: 0.0).toInt()
    val name = Durchbrueche.thema(r.id)?.name(deutsch) ?: r.id
    val alter = r.zeit?.let { Durchbrueche.alter(it, System.currentTimeMillis()).let { x -> S.dbAlter(x.wert, x.einheit) } }
    val titel = r.teile.getOrNull(0)?.ifBlank { null }
    val quelle = listOfNotNull(r.teile.getOrNull(1)?.ifBlank { null }, alter).joinToString(" · ").ifBlank { null }
    TabZeile(name, if (n > 0) S.dbNeuAnzahl(n) else null, MaterialTheme.colorScheme.primary, unter = listOfNotNull(titel, quelle), onClick = onClick)
}
