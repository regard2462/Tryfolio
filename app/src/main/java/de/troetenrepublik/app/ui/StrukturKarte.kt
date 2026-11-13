package de.troetenrepublik.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.eigentuemerStruktur
import de.troetenrepublik.app.engine.Struktur
import de.troetenrepublik.app.engine.StrukturAktion
import de.troetenrepublik.app.engine.StrukturRegeln
import de.troetenrepublik.app.gepflegteStruktur
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.StrukturTexte
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs

// Eigentuemer und Rechte (Hintergrund, nach "Grosse Halter"): Organigramm mit Haltern, Gattungen und Firma, die Gattungen mit
// ihren Rechten, "Deine Rechte" fuer die Gattung der Seite, die Hauptversammlung (8-K Item 5.07) und Aktivisten (Schedule 13D).
// Zugeklappt steht eine Kernzeile. Die Karte laedt und rechnet, die Zeilen kommen als Eintraege der Seite (strukturAbschnitt).

private fun t(key: String, vararg w: String): String = StrukturTexte.t(key, *w)

// Anteil in Prozent: unter zehn mit zwei Stellen, sonst mit einer
private fun pct(v: Double): String = Fmt.pct(v, if (abs(v) >= 10.0) 1 else 2)

private fun pctOder(v: Double?): String = v?.let { pct(it) } ?: "–"

private fun zahl(v: Double): String = Fmt.num(v, if (v % 1.0 == 0.0) 0 else if (v < 1.0) 4 else 3)

private fun tag(iso: String): String = runCatching {
    Fmt.date(java.time.LocalDate.parse(iso.take(10)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
}.getOrDefault(iso)

private fun halterName(h: Struktur.Halter): String = h.name.ifBlank {
    when (h.art) {
        Struktur.Art.GRUPPE -> t("org.gruppe")
        Struktur.Art.INSTITUTIONEN -> t("org.institutionen")
        Struktur.Art.STREUBESITZ -> t("org.uebrige")
        else -> ""
    }
}

// Halter mit eigenem Firmenblatt (Wikidata), wenn man ihn antippt
private fun antippbar(h: Struktur.Halter): Boolean = h.name.isNotBlank() && h.art in setOf(
    Struktur.Art.HOLDING, Struktur.Art.FONDS, Struktur.Art.STIFTUNG, Struktur.Art.STAAT, Struktur.Art.MUTTER, Struktur.Art.TRUST,
)

private fun belegLink(e: Struktur.Ergebnis, i: Int): String? = e.belege.getOrNull(i)?.url

// ------------------------------------------------------------ Lader

@Composable
internal fun StrukturKarte(state: AppState, symbol: String, name: String, isin: String, stueck: Double?, merker: SeitenMerker?) {
    var daten by gemerkt<Struktur.Ergebnis?>(merker, "struktur/$symbol") { null }
    var geladen by gemerkt<Boolean>(merker, "struktur/$symbol/geladen") { false }
    var blatt by gemerkt<String?>(merker, "struktur/blatt") { null }
    val offen = merker?.zustand<Boolean>("kern/struktur") { false }?.value ?: false
    // Gepflegte Angaben stehen ohne Netz da. Die Abrufe (SEC, Wikidata, GLEIF) laufen erst, wenn der Abschnitt aufgeklappt wird,
    // und zwar im Bereich der Seite: Diese Karte steckt im gemeinsamen Ladeeintrag oben im Hintergrund, der beim Scrollen zum
    // Abschnitt aus dem Bild faellt und verworfen wird.
    val abruf: () -> Unit = {
        merker?.starte("struktur/$symbol/abruf") {
            daten = runCatching { state.eigentuemerStruktur(symbol, name) }.getOrNull() ?: daten
            geladen = true
        }
    }
    LaunchedEffect(symbol, offen) {
        if (daten == null) {
            val heute = java.time.LocalDate.now().toString()
            daten = runCatching { StrukturRegeln.zusammen(gepflegteStruktur(symbol, isin), null, heute) }.getOrNull()
        }
        if (!offen || geladen) return@LaunchedEffect
        if (merker != null) abruf()
        else {
            daten = runCatching { state.eigentuemerStruktur(symbol, name) }.getOrNull() ?: daten
            geladen = true
        }
    }
    val d = daten
    if (offen && d != null) {
        val quellen = d.belege.map { b -> QuelleLink(if (b.stand.isBlank() || b.name.contains(b.stand)) b.name else b.name + " (" + b.stand + ")", b.url) }
        QuellenMelden(*quellen.toTypedArray())
    }
    val links = appLinks()
    // Nicht gemerkt: die Zeilen lesen den Zustand der Aufklapp-Knoepfe (Merker) und werden mit jedem Aufbau neu gesetzt
    val einstellungen by state.settingsFlow.collectAsState()
    val ohneSec = de.troetenrepublik.app.market.SecInsider.kennung(einstellungen.secKontakt) == null
    val modell = if (d == null || d.leer) leeresModell(geladen, ohneSec, d, abruf)
    else strukturModell(d, symbol, isin, stueck, merker, { n -> blatt = n }, { u -> runCatching { links.openUri(u) } }, abruf)
    ModellAusgabe(merker, "struktur", modell)
    blatt?.let { n -> FirmaBlatt(state, FirmaDaten(n, "", "", false, null, null, null, "")) { blatt = null } }
}

// Ohne Daten: nur der Kopf zum Aufklappen. Aufgeklappt steht, ob noch geladen wird oder nichts gefunden wurde, und was geprueft ist.
private fun leeresModell(geladen: Boolean, ohneSec: Boolean, d: Struktur.Ergebnis?, beimOeffnen: () -> Unit): ListenModell {
    val zeilen = listOf(
        ZeilenDaten("leer") {
            if (!geladen) EmptyHint(S.trLaedt)
            else Column {
                Hinweistext(t("leer"))
                if (ohneSec) Hinweistext(t("leerOhneSec"))
            }
        },
    )
    return ListenModell(
        titel = t("titel"), gross = true, kern = null, zeilen = zeilen, immerZu = true, kappe = Int.MAX_VALUE, beimOeffnen = beimOeffnen,
        nachspann = { Abdeckung(d?.geprueft.orEmpty()) },
    )
}

// Eine Zeile am Ende: welche Quellen fuer diese Aktie geprueft wurden
@Composable
private fun Abdeckung(geprueft: List<String>) {
    if (geprueft.isEmpty()) return
    Hinweistext(t("geprueft", geprueft.joinToString(", ") { t("q.$it") }))
}

internal fun LazyListScope.strukturAbschnitt(merker: SeitenMerker) {
    listenAbschnitt(merker, "struktur")
}

// ------------------------------------------------------------ Modell

private fun strukturModell(
    e: Struktur.Ergebnis, symbol: String, isin: String, stueck: Double?, merker: SeitenMerker?,
    oeffneFirma: (String) -> Unit, oeffneLink: (String) -> Unit, beimOeffnen: () -> Unit,
): ListenModell {
    val v = StrukturRegeln.verteilung(e.gattungen)
    val halter = halterListe(e, v)
    val rechte = StrukturRegeln.rechte(e, symbol, isin, stueck)
    val zeilen = ArrayList<ZeilenDaten>()

    // Organigramm
    if (e.leer) zeilen.add(ZeilenDaten("leer") { Hinweistext(t("leer")) })
    else zeilen.add(ZeilenDaten("organigramm") { Organigramm(e, halter, v, merker, oeffneFirma) })
    if (halter.isNotEmpty()) zeilen.add(ZeilenDaten("institutionen") { Hinweistext(t("org.institutionenSiehe")) })
    // Gattungen mit Rechten
    if (e.gattungen.isNotEmpty()) {
        zeilen.add(ZeilenDaten("g-kopf") { Zwischenkopf(t("g.titel")) })
        e.gattungen.forEachIndexed { i, g -> zeilen.add(ZeilenDaten("g-${g.id}-$i") { GattungsZeile(e, g, i, v) }) }
    }
    e.notizen.forEachIndexed { i, n -> zeilen.add(ZeilenDaten("n-$i") { Hinweistext(n.je(I18n.lang == de.troetenrepublik.app.data.Lang.DE)) }) }
    // Deine Rechte
    if (rechte != null) {
        zeilen.add(ZeilenDaten("r-kopf") { Zwischenkopf(t("r.titel")) })
        rechteZeilen(e, rechte).forEachIndexed { i, z -> zeilen.add(ZeilenDaten("r-$i", z)) }
    }
    // Hauptversammlung
    e.versammlungen.forEachIndexed { i, vs -> versammlungZeilen(vs, i, merker).forEach { zeilen.add(it) } }
    // Aktivisten
    if (e.aktivisten.isNotEmpty()) {
        zeilen.add(ZeilenDaten("a-kopf") { Zwischenkopf(t("akt.titel")) })
        e.aktivisten.forEachIndexed { i, a -> zeilen.add(ZeilenDaten("a-$i") { AktivistZeile(e, a, oeffneLink) }) }
    }

    // Kernzeile
    val kontrolle = StrukturRegeln.kontrolleVon(e.halter, v)
    val kontrollAnteil = e.halter.filter { it.name == kontrolle }.mapNotNull { StrukturRegeln.halterAnteile(it, v).second }.maxOrNull()
    val teile = ArrayList<KernTeil>()
    when {
        kontrolle != null && kontrollAnteil != null -> teile += KernTeil(t("kern.kontrolle", kontrolle.ifBlank { t("org.gruppe") }, pct(kontrollAnteil)))
        e.halter.any { StrukturRegeln.halterAnteile(it, v).second != null } -> teile += KernTeil(t("kern.keineKontrolle"))
    }
    val echte = e.gattungen.count { it.art != Struktur.GArt.ADS }
    teile += KernTeil(if (echte > 1) t("kern.gattungen", echte.toString()) else t("kern.eineGattung"))
    val unten = kernUnten(e)
    return ListenModell(
        titel = t("titel"),
        gross = true,
        kern = KernZeile(teile, unten),
        zeilen = zeilen,
        immerZu = true,
        kappe = Int.MAX_VALUE,
        beimOeffnen = beimOeffnen,
        nachspann = {
            val stand = when {
                e.gepflegt && e.stand.isNotBlank() -> t("foot.gepflegt", tag(e.stand))
                e.stand.isNotBlank() -> t("foot.stand", tag(e.stand))
                else -> null
            }
            Abdeckung(e.geprueft)
            InfoZeile(listOfNotNull(stand, if (e.veraltet) t("foot.veraltet") else null).joinToString(PUNKT).ifEmpty { null }, t("titel"), t("info"), Modifier.padding(bottom = 6.dp))
        },
    )
}

// Zeile unter der Kernzeile: Aktivist, sonst Vergütung und Anträge der letzten Hauptversammlung
private fun kernUnten(e: Struktur.Ergebnis): KernTeil? {
    val a = e.aktivisten.firstOrNull { StrukturAktion.aktiv(it.aktuell.absicht) }
    if (a != null) {
        val absicht = a.aktuell.absicht.filter { it != Struktur.Absicht.BETEILIGUNG }.take(2).joinToString(if (I18n.lang == de.troetenrepublik.app.data.Lang.DE) " und " else " and ") { t("akt.$it") }
        return KernTeil(t("kern.aktivist", a.name, a.aktuell.prozent?.let { pct(it) } ?: "–", absicht))
    }
    val vs = e.versammlungen.firstOrNull() ?: return null
    val k = StrukturAktion.kern(vs)
    val teile = listOfNotNull(
        k.verguetung?.let { t("kern.verguetung", Fmt.pct(it, 0)) },
        if (k.angenommen > 0) t("kern.antraegeAngenommen", k.angenommen.toString()) else null,
        if (k.abgelehnt > 0) t("kern.antraegeAbgelehnt", k.abgelehnt.toString()) else null,
    )
    return teile.joinToString(", ").takeIf { it.isNotBlank() }?.let { KernTeil(it) }
}

// Halter fuers Organigramm: gepflegte und abgerufene, dazu die Sammelzeile der Institutionen und der Streubesitz
private fun halterListe(e: Struktur.Ergebnis, v: StrukturRegeln.Verteilung): List<Struktur.Halter> {
    val wert = { h: Struktur.Halter -> StrukturRegeln.halterAnteile(h, v).let { maxOf(it.first ?: 0.0, it.second ?: 0.0) } }
    val benannt = e.halter.filter { it.art != Struktur.Art.INSTITUTIONEN && it.art != Struktur.Art.STREUBESITZ && it.art != Struktur.Art.GRUPPE }
        .sortedByDescending(wert)
    val gruppe = e.halter.filter { it.art == Struktur.Art.GRUPPE }
    val inst = e.halter.filter { it.art == Struktur.Art.INSTITUTIONEN }
    val vorhandenStreu = e.halter.filter { it.art == Struktur.Art.STREUBESITZ }
    val aus = ArrayList<Struktur.Halter>(benannt + gruppe + inst)
    if (vorhandenStreu.isNotEmpty()) aus += vorhandenStreu
    else if (e.halter.isNotEmpty()) {
        val (k, s) = StrukturRegeln.streubesitz(e.halter, v)
        if (k != null || s != null) aus += Struktur.Halter("", Struktur.Art.STREUBESITZ, kapital = k, stimmen = s)
    }
    return aus
}

// ------------------------------------------------------------ Organigramm

private const val OFFEN = 5

@Composable
private fun Organigramm(e: Struktur.Ergebnis, halter: List<Struktur.Halter>, v: StrukturRegeln.Verteilung, merker: SeitenMerker?, oeffne: (String) -> Unit) {
    val linie = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    val alle = merker?.zustand<Boolean>("struktur/weitere") { false } ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val sichtbar = if (alle.value) halter else halter.take(OFFEN)
    val echteGattungen = e.gattungen.filter { it.art != Struktur.GArt.ADS }
    // Die Spalte „Stimmen“ steht nur da, wo sie etwas zusaetzlich sagt: bei einer stimmberechtigten Gattung mit gleicher Stimme je
    // Aktie ist der Stimmenanteil gleich dem Kapitalanteil, dann entfaellt sie fuer alle Zeilen
    val mitStimmen = halter.any { h -> StrukturRegeln.halterAnteile(h, v).let { (k, s) -> stimmenAnzeige(k, s) != null } } ||
        echteGattungen.any { g -> stimmenAnzeige(v.kapital[g.id], v.stimmen[g.id]) != null }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val schmal = maxWidth < 400.dp
        Box(Modifier.fillMaxWidth().drawBehind {
            val x = 30.dp.toPx()
            drawLine(linie, Offset(x, 6.dp.toPx()), Offset(x, size.height - 6.dp.toPx()), 1.5.dp.toPx())
        }) {
            Column(Modifier.fillMaxWidth()) {
                if (e.mutter.isNotBlank()) {
                    Ebene { Knoten(e.mutter, t("org.mutter"), null, null, null, false, schmal, true) { oeffne(e.mutter) } }
                }
                if (sichtbar.isNotEmpty()) {
                    SpaltenKopf(schmal, mitStimmen)
                    sichtbar.forEach { h ->
                        val (k, s) = StrukturRegeln.halterAnteile(h, v)
                        val gi = echteGattungen.indexOfFirst { it.id == h.gattung }
                        val unter = listOfNotNull(
                            t("art.${h.art}"),
                            h.gattung?.let { id -> echteGattungen.firstOrNull { it.id == id }?.let { g -> g.kuerzel.ifBlank { g.id } } },
                            h.klasse?.let { t("org.klasse", pct(it)) },
                            h.klassen.takeIf { it.isNotEmpty() }?.let { m -> t("org.klassen", m.entries.joinToString(", ") { it.key + " " + pct(it.value) }) },
                            if (h.aktivist) t("org.aktivist") else null,
                        ).joinToString(PUNKT)
                        Ebene {
                            Knoten(
                                halterName(h), unter, k, stimmenAnzeige(k, s), if (gi >= 0) AnteilsFarben.folge(gi) else null,
                                h.aktivist, schmal, antippbar(h), mitStimmen = mitStimmen,
                            ) { oeffne(h.name) }
                        }
                    }
                    if (halter.size > OFFEN) ListenPfeil(alle.value, label = if (alle.value) S.less else t("org.weitere", (halter.size - OFFEN).toString())) { alle.value = !alle.value }
                }
                if (echteGattungen.isNotEmpty()) {
                    echteGattungen.forEachIndexed { i, g ->
                        val kap = v.kapital[g.id]
                        val sti = v.stimmen[g.id]
                        val unter = listOfNotNull(
                            g.kuerzel.ifBlank { null },
                            t("gart.${g.art}"),
                            g.stimmen?.let { t("g.stimmenJe") + " " + zahl(it) },
                        ).joinToString(PUNKT)
                        Ebene { Knoten(g.titel.ifBlank { g.id }, unter, kap, stimmenAnzeige(kap, sti), AnteilsFarben.folge(i), false, schmal, false, mitStimmen = mitStimmen) {} }
                    }
                }
                Ebene { Knoten(e.firma.ifBlank { t("org.firma") }, t("org.firma"), null, null, null, false, schmal, false, hervor = true) {} }
            }
        }
    }
}

// Eine Zeile des Organigramms mit Anschluss an die senkrechte Linie
@Composable
private fun Ebene(inhalt: @Composable () -> Unit) {
    val linie = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    Row(
        Modifier.fillMaxWidth().drawBehind {
            val y = size.height / 2
            drawLine(linie, Offset(30.dp.toPx(), y), Offset(44.dp.toPx(), y), 1.5.dp.toPx())
        }.padding(start = 46.dp, end = 20.dp, top = 3.dp, bottom = 3.dp),
    ) { inhalt() }
}

private val SPALTE: Dp = 64.dp

// Stimmenanteil einer Zeile, nur wenn er vom Kapitalanteil abweicht (oder es keinen Kapitalanteil gibt)
private fun stimmenAnzeige(kapital: Double?, stimmen: Double?): Double? =
    if (StrukturRegeln.weichtAb(kapital, stimmen) || kapital == null) stimmen else null

@Composable
private fun SpaltenKopf(schmal: Boolean, mitStimmen: Boolean) {
    if (schmal) return
    Row(Modifier.fillMaxWidth().padding(start = 46.dp, end = 20.dp, top = 6.dp)) {
        Spacer(Modifier.weight(1f))
        Text(t("org.kapital"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(SPALTE + 12.dp))
        if (mitStimmen) Text(t("org.stimmen"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(SPALTE + 12.dp))
    }
}

// Knoten: Name (bricht um), darunter Art und Gattung; rechts Kapital und Stimmen nebeneinander, auf schmalen Bildschirmen darunter
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Knoten(
    name: String, unter: String?, kapital: Double?, stimmen: Double?, farbe: Color?, aktivist: Boolean, schmal: Boolean, tippbar: Boolean,
    hervor: Boolean = false, mitStimmen: Boolean = true, onClick: () -> Unit,
) {
    val signal = LocalSignal.current
    val rand = if (aktivist) signal.warn else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val flaeche = if (hervor) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    val form = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().clip(form).background(flaeche).border(BorderStroke(if (aktivist) 1.5.dp else 1.dp, rand), form)
            .then(if (tippbar) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (farbe != null) {
                Box(Modifier.padding(top = 5.dp, end = 8.dp).size(8.dp).clip(CircleShape).background(farbe))
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = if (hervor) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
                if (!unter.isNullOrBlank()) PunktText(unter)
            }
            if (!schmal && (kapital != null || stimmen != null)) {
                Spacer(Modifier.width(12.dp))
                Text(pctOder(kapital), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(SPALTE))
                if (mitStimmen) {
                    Spacer(Modifier.width(12.dp))
                    Text(stimmen?.let { pct(it) } ?: "", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(SPALTE))
                }
            }
        }
        if (schmal && (kapital != null || stimmen != null)) {
            FlowRow(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                kapital?.let { Text(t("org.kapital") + " " + pct(it), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) }
                stimmen?.let { Text(t("org.stimmen") + " " + pct(it), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) }
            }
        }
    }
}

// ------------------------------------------------------------ Gattungen

@Composable
private fun Hinweistext(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

@Composable
private fun GattungsZeile(e: Struktur.Ergebnis, g: Struktur.Gattung, index: Int, v: StrukturRegeln.Verteilung) {
    val deutsch = I18n.lang == de.troetenrepublik.app.data.Lang.DE
    val ads = g.art == Struktur.GArt.ADS
    val sti = if (ads) null else v.stimmen[g.id]
    val kap = if (ads) null else v.kapital[g.id]
    val unter = listOfNotNull(
        g.kuerzel.ifBlank { null }, t("gart.${g.art}"), if (!g.boerse) t("g.nichtBoerse") else null,
        g.ads?.let { t("g.adsJe", zahl(it)) },
    ).joinToString(PUNKT)
    KartenZeile(
        label = g.titel.ifBlank { g.id },
        wert = if (ads) "" else sti?.let { pct(it) } ?: "",
        unter = unter,
        anteil = sti?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
        balkenFarbe = AnteilsFarben.folge(index),
        details = {
            if (!ads) {
                DetailZeile(t("g.stimmenJe"), g.stimmen?.let { if (it == 0.0) t("g.keineStimme") else zahl(it) } ?: t("g.nichtGeprueft"))
                DetailZeile(t("g.stimmenAnteil"), sti?.let { pct(it) } ?: t("g.nichtGeprueft"))
                DetailZeile(t("g.kapitalAnteil"), if (!g.wirtschaftlich) t("g.ohneAnspruch") else kap?.let { pct(it) } ?: t("g.nichtGeprueft"))
                g.aktien?.let { DetailZeile(t("g.aktien"), Fmt.compact(it)) }
            }
            g.dividende?.let { DetailZeile(t("g.dividende"), it.je(deutsch)) }
            g.board?.let { DetailZeile(t("g.board"), it.je(deutsch)) }
            g.notizen.forEach { DetailText(it.je(deutsch)) }
            // Halter dieser Gattung mit Prozent der Gattung
            val dazu = e.halter.filter { it.gattung == g.id && it.klasse != null }
            if (dazu.isNotEmpty()) dazu.forEach { h -> DetailZeile(halterName(h), t("org.klasse", pct(h.klasse ?: 0.0))) }
        },
    )
}

// ------------------------------------------------------------ Deine Rechte

private fun rechteZeilen(e: Struktur.Ergebnis, r: StrukturRegeln.Rechte): List<@Composable () -> Unit> {
    val aus = ArrayList<@Composable () -> Unit>()
    val g = r.gattung
    val deutsch = I18n.lang == de.troetenrepublik.app.data.Lang.DE
    aus.add { KeyValueRow(t("r.gattung"), g.titel.ifBlank { g.id }) }
    val je = if (r.ads) r.stimmenJeAktie?.let { it * (r.adsJeAktie ?: 1.0) } else r.stimmenJeAktie
    aus.add { KeyValueRow(if (r.ads) t("r.stimmenJeAds") else t("r.stimmenJe"), je?.let { if (it == 0.0) t("g.keineStimme") else zahl(it) } ?: t("r.nichtGeprueft")) }
    aus.add { KeyValueRow(t("r.anteilKlasse"), r.stimmenAnteil?.let { pct(it) } ?: t("r.nichtGeprueft")) }
    val basisG = if (r.ads) e.gattungen.firstOrNull { it.id == g.basis } else g
    val board = basisG?.board?.je(deutsch) ?: when (r.board) {
        StrukturRegeln.Board.JA -> t("r.boardJa")
        StrukturRegeln.Board.NEIN_KONTROLLE -> if (r.kontrolle.isNullOrBlank()) t("r.boardGruppe") else t("r.boardKontrolle", r.kontrolle)
        StrukturRegeln.Board.NEIN_OHNE_STIMME -> t("r.boardOhne")
        StrukturRegeln.Board.UNBEKANNT -> t("r.nichtGeprueft")
    }
    aus.add { KeyValueRow(t("r.board"), board) }
    // Abstimmungen: worueber Halter dieser Gattung bei der letzten Hauptversammlung abstimmen durften
    e.versammlungen.firstOrNull()?.let { vs ->
        val wert = if (r.stimmenJeAktie == 0.0) t("r.abstimmungNein") else t("r.abstimmungJa", vs.beschluesse.size.toString(), tag(vs.tag))
        aus.add { KeyValueRow(t("r.abstimmung"), wert) }
    }
    if (r.ads) aus.add { Hinweistext(t("r.adsText")) }
    if (r.vie) aus.add { Hinweistext(t("r.vieText")) }
    if (r.vorzugOhneStimme) aus.add { Hinweistext(t("r.vorzugText")) }
    if (r.genuss) aus.add { Hinweistext(t("r.genussText")) }
    // Hinweise aus dem Text der Unterlagen, wo die Zahl der Stimmen nicht geprueft ist
    if (r.stimmenJeAktie == null) e.flags.filter { it == Struktur.Flag.MEHRSTIMMEN || it == Struktur.Flag.OHNE_STIMME || it == Struktur.Flag.GOLDEN }.forEach { f -> aus.add { Hinweistext(t("flag.$f")) } }
    g.notizen.forEach { n -> aus.add { Hinweistext(n.je(deutsch)) } }
    r.eigen?.let { ei ->
        aus.add { KeyValueRow(t("r.eigenStueck"), Fmt.num(ei.stueck, if (ei.stueck % 1.0 == 0.0) 0 else 4)) }
        aus.add { KeyValueRow(t("r.eigenKapital"), ei.kapital?.let { Fmt.pct(it, 6) } ?: t("r.nichtGeprueft")) }
        aus.add { KeyValueRow(t("r.eigenStimmen"), ei.stimmen?.let { Fmt.pct(it, 6) } ?: t("r.nichtGeprueft")) }
    }
    return aus
}

// ------------------------------------------------------------ Hauptversammlung

private fun versammlungZeilen(vs: Struktur.Versammlung, index: Int, merker: SeitenMerker?): List<ZeilenDaten> {
    val aus = ArrayList<ZeilenDaten>()
    aus += ZeilenDaten("hv$index-kopf") { Zwischenkopf(t("hv.titel") + PUNKT + t("hv.stand", tag(vs.tag))) }
    val auff = vs.beschluesse.filter { StrukturAktion.auffaellig(it) }
    val verg = vs.beschluesse.firstOrNull { it.art == Struktur.BArt.VERGUETUNG }
    val oben = (listOfNotNull(verg) + auff).distinct()
    oben.forEachIndexed { i, b -> aus += ZeilenDaten("hv$index-o$i") { BeschlussZeile(b) } }
    val rest = vs.beschluesse.filter { it !in oben }
    if (rest.isNotEmpty()) {
        val alle = merker?.zustand<Boolean>("struktur/hv$index") { false }
        aus += ZeilenDaten("hv$index-alle") {
            val z = alle ?: remember { androidx.compose.runtime.mutableStateOf(false) }
            ListenPfeil(z.value, label = if (z.value) S.less else t("hv.alle", vs.beschluesse.size.toString())) { z.value = !z.value }
        }
        if (alle?.value == true) rest.forEachIndexed { i, b -> aus += ZeilenDaten("hv$index-r$i") { BeschlussZeile(b) } }
    }
    return aus
}

@Composable
private fun BeschlussZeile(b: Struktur.Beschluss) {
    val signal = LocalSignal.current
    val z = b.zustimmung
    val auff = StrukturAktion.auffaellig(b)
    val status = when {
        z == null -> ""
        b.art == Struktur.BArt.ANTRAG -> if (z > 50.0) t("hv.angenommen") else t("hv.abgelehnt")
        b.art == Struktur.BArt.VERGUETUNG -> if (z > 50.0) t("hv.gebilligt") else t("hv.abgelehnt")
        else -> ""
    }
    val unter = listOfNotNull(t("hv.${b.art}"), status.ifBlank { null }, if (auff) t("hv.auffaellig") else null).joinToString(PUNKT)
    KartenZeile(
        label = b.titel,
        wert = z?.let { Fmt.pct(it, 1) } ?: "–",
        unter = unter,
        anteil = z?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
        balkenFarbe = if (auff) signal.warn else MaterialTheme.colorScheme.primary,
        details = { DetailText(t("hv.detail", Fmt.compact(b.fuer), Fmt.compact(b.gegen), Fmt.compact(b.enthaltung), Fmt.compact(b.broker))) },
    )
}

// ------------------------------------------------------------ Aktivisten

@Composable
private fun AktivistZeile(e: Struktur.Ergebnis, a: Struktur.Aktivist, oeffne: (String) -> Unit) {
    val deutsch = I18n.lang == de.troetenrepublik.app.data.Lang.DE
    val und = if (deutsch) " und " else " and "
    fun absicht(l: List<String>) = l.joinToString(und) { t("akt.$it") }
    KartenZeile(
        label = a.name,
        wert = a.aktuell.prozent?.let { pct(it) } ?: "–",
        unter = listOf(absicht(a.aktuell.absicht), tag(a.aktuell.tag)).joinToString(PUNKT),
        anteil = a.aktuell.prozent?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
        balkenFarbe = LocalSignal.current.warn,
        details = {
            a.verlauf.forEach { vl -> DetailZeile(tag(vl.tag) + PUNKT + absicht(vl.absicht), vl.prozent?.let { pct(it) } ?: "–") }
            belegLink(e, a.aktuell.beleg)?.let { u -> VerweisText(t("akt.original"), Modifier.padding(top = 2.dp)) { oeffne(u) } }
        },
    )
}
