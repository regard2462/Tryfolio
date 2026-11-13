package de.troetenrepublik.app

import de.troetenrepublik.app.data.WidgetKarte
import de.troetenrepublik.app.data.WidgetKartenBauAbschnitte
import de.troetenrepublik.app.engine.Aktienfinder
import de.troetenrepublik.app.engine.FinderBranchen
import de.troetenrepublik.app.engine.InsiderSeltenheit
import de.troetenrepublik.app.engine.Konvergenz
import de.troetenrepublik.app.engine.Uhr
import de.troetenrepublik.app.engine.Vorschlagsdepot
import de.troetenrepublik.app.engine.WirktDas
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.AchtK
import de.troetenrepublik.app.market.AchtKAbruf
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Daten
import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.Praesidialdokumente
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.ui.CopyRouten
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.ui.KonvergenzDaten
import de.troetenrepublik.app.ui.istUsAktie
import de.troetenrepublik.app.ui.standEnde
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

// Daten der Widgets zu den Abschnitten von 0.77: Konvergenz, seltene Insiderkaeufe, Insider-Simulator, Vorschlagsdepot, Branche,
// Warnzeichen, Praesidialdokumente. Jede Quelle geht den Weg, den der Abschnitt in der App schon geht (Insider-Stand mit
// Kontaktadresse unter Zugaenge, Kongress-Geschaefte, Buendel der 13F, Speicher des Aktienfinders, 8-K-Abruf, Federal Register);
// gerechnet wird nur, wenn die Frist der Karte um ist (WidgetKartenQuellen.kt). Scheitert ein Abruf, bleibt die letzte Karte;
// ohne letzte Karte steht ein Hinweis, damit das Widget nicht dauernd "Laedt" zeigt.

// Beim Scheitern eines Abrufs: die letzte Karte behalten, ohne sie den Hinweis.
private fun beiFehler(vorher: WidgetKarte?, hinweis: WidgetKarte): WidgetKarte? = if (vorher == null) hinweis else null

// Name wie in den Listen der App; der Katalog muss dafuer geladen sein.
private suspend fun katalogLaden() { withContext(Dispatchers.Default) { runCatching { Catalog.get() } } }

private fun tagMs(iso: String): Long = Politik.tag(iso)

// ------------------------------------------------------------ Konvergenz

internal fun widgetKonvergenz(l: List<Konvergenz.Zeile>, bis: String, am: Long): WidgetKarte =
    WidgetKartenBauAbschnitte.konvergenz(
        l.map { WidgetKartenBauAbschnitte.Konv(widgetFirmenname(CopyRouten.name(it.symbol, it.name)), CopyRouten.symbol(it.symbol), it.insider, it.kongress, it.fonds) },
        tagMs(bis), am,
    )

internal suspend fun AppState.quelleKonvergenz(vorher: WidgetKarte?, jetzt: Long): WidgetKarte? {
    val e = runCatching { KonvergenzDaten.laden(this) }.getOrNull() ?: return beiFehler(vorher, widgetKonvergenz(emptyList(), "", 0L))
    katalogLaden()
    return widgetKonvergenz(e.zeilen, e.fenster.bis, jetzt)
}

// ------------------------------------------------------------ Seltene Insiderkaeufe

// Rechtsform am Ende eines gemeldeten Firmennamens ("Kodiak Sciences Inc.", "ADARx Pharmaceuticals, Inc.")
private val WIDGET_RECHTSFORM = Regex(",?\\s+(Inc|Incorporated|Corp|Corporation|Ltd|Limited|LLC|PLC|Co|LP)\\.?$", RegexOption.IGNORE_CASE)

// Firmenname fuer eine Widget-Zeile: ohne Rechtsform am Ende, damit die Zeile nicht mitten im Zusatz endet.
internal fun widgetFirmenname(name: String): String {
    var s = name.trim()
    repeat(2) { s = s.replace(WIDGET_RECHTSFORM, "").trim().trimEnd(',').trim() }
    return s.ifBlank { name }
}

internal fun widgetSelten(funde: List<InsiderSeltenheit.Fund>, ende: String, am: Long): WidgetKarte =
    WidgetKartenBauAbschnitte.selten(
        funde.map {
            WidgetKartenBauAbschnitte.Selten(
                widgetFirmenname(CopyRouten.name(it.symbol, it.name)), CopyRouten.symbol(it.symbol), it.art.ordinal, tagMs(it.tag), it.betrag, it.monate, it.seitBeginn, it.zahl,
                Fmt.num(it.faktor, 1), it.seitJahr,
            )
        },
        tagMs(ende), am,
    )

internal suspend fun AppState.quelleInsiderSelten(vorher: WidgetKarte?, jetzt: Long): WidgetKarte? {
    val d = runCatching { secLaden() }.getOrNull()
    if (d == null || d.geschaefte.isEmpty()) return beiFehler(vorher, widgetSelten(emptyList(), "", 0L))
    katalogLaden()
    return withContext(Dispatchers.Default) {
        runCatching {
            val beginn = InsiderSeltenheit.beginn(d.geschaefte) ?: return@runCatching null
            val ende = standEnde(d)
            widgetSelten(InsiderSeltenheit.besteJeFirma(InsiderSeltenheit.funde(d.geschaefte, ende, beginn, historie = de.troetenrepublik.app.engine.InsiderHistorie.app())), ende, jetzt)
        }.getOrNull()
    }
}

// ------------------------------------------------------------ Insider-Simulator

// Standardregel: die mitgelieferte Rueckschau (kein Netz, kein Rechnen mit Kursen).
internal fun widgetInsiderSim(c: WirktDas.Cluster?, am: Long): WidgetKarte =
    WidgetKartenBauAbschnitte.insiderSim(
        c?.let {
            WidgetKartenBauAbschnitte.Sim(
                it.rendite, it.index, it.treffer, it.halten, it.ausgewertet, it.ab.take(4), it.median, it.schlechtester.r, it.schlechtester.k,
            )
        },
        am,
    )

internal suspend fun quelleInsiderSim(jetzt: Long): WidgetKarte =
    withContext(Dispatchers.Default) { widgetInsiderSim(runCatching { WirktDas.clusterLesen(Daten.text("copytrading/cluster.json")) }.getOrNull(), jetzt) }

// ------------------------------------------------------------ Vorschlagsdepot und Branche

// Ergebnis des Aktienfinders mit den Standardgewichten aus dem Speicher der App (das zuletzt geladene Universum); null, solange
// der Aktienfinder noch nichts geladen hat. Das Widget laedt nichts.
private fun finderErgebnisse(): List<Aktienfinder.Ergebnis>? {
    val daten = FinderLauf.zustand.value.daten.ifEmpty { AktienfinderSpeicher.daten }.takeIf { it.isNotEmpty() } ?: return null
    return Aktienfinder.bewerten(daten)
}

internal fun widgetVorschlag(depot: Vorschlagsdepot.Depot?, am: Long): WidgetKarte =
    WidgetKartenBauAbschnitte.vorschlag(
        depot?.eintraege.orEmpty().map { WidgetKartenBauAbschnitte.Position(it.ergebnis.daten.name, it.ergebnis.symbol, it.gewicht, S.vsdSektor(it.sektor.name)) },
        depot?.eintraege?.size ?: 0, depot?.sektoren?.size ?: 0, depot != null, am,
    )

internal suspend fun quelleVorschlag(vorher: WidgetKarte?, jetzt: Long): WidgetKarte? = withContext(Dispatchers.Default) {
    val erg = finderErgebnisse()
    // Ohne Daten bleibt die letzte Karte; ohne letzte Karte steht der Hinweis (am = 0: gleich neu, sobald der Finder geladen hat)
    if (erg == null) return@withContext if (vorher == null || vorher.zeilen.isEmpty()) widgetVorschlag(null, 0L) else null
    widgetVorschlag(Vorschlagsdepot.zusammenstellen(erg), jetzt)
}

// Die Gruppe der Branche (schluessel; null: die staerkste) mit ihren Werten.
internal fun widgetBranche(ergebnisse: List<Aktienfinder.Ergebnis>?, schluessel: String?, am: Long): WidgetKarte {
    val gruppen = ergebnisse?.let { FinderBranchen.gruppen(it) }.orEmpty()
    val g = if (schluessel == null) gruppen.firstOrNull() else gruppen.firstOrNull { it.branche == schluessel }
    val titel = when {
        g != null && g.branche == FinderBranchen.SAMMEL -> S.fbrSammel
        g != null -> S.industryName(g.branche)
        schluessel != null -> S.industryName(schluessel)
        else -> S.afAnsichtBranchen
    }
    return WidgetKartenBauAbschnitte.branche(
        titel, g?.werte.orEmpty().map { WidgetKartenBauAbschnitte.BrancheWert(it.daten.name, it.symbol, it.gesamt) }, g?.werte?.size ?: 0, ergebnisse != null, am,
    )
}

internal suspend fun quelleBranche(schluessel: String?, vorher: WidgetKarte?, jetzt: Long): WidgetKarte? = withContext(Dispatchers.Default) {
    val erg = finderErgebnisse()
    if (erg == null) return@withContext if (vorher == null || vorher.zeilen.isEmpty()) widgetBranche(null, schluessel, 0L) else null
    widgetBranche(erg, schluessel, jetzt)
}

// ------------------------------------------------------------ Warnzeichen

internal fun widgetWarnzeichen(symbol: String, liste: List<AchtK.Warnzeichen>, stand: String, am: Long): WidgetKarte =
    WidgetKartenBauAbschnitte.warnzeichen(
        symbol,
        // Boersenwechsel und Entwarnung zaehlen nicht (AchtK.zaehlt); eine gelesene Notierungsmeldung traegt ihre genaue Einordnung
        liste.filter { AchtK.zaehlt(it) }.map { w ->
            val titel = w.detail?.unterart?.let { S.achtArt(it.name) } ?: if (w.art == AchtK.Art.FUEHRUNG) S.achtRolle(w.rolle.name) else S.achtArt(w.art.name)
            WidgetKartenBauAbschnitte.Warn(titel, tagMs(w.tag), w.art.schwer)
        },
        stand, am,
    )

// 8-K-Meldungen der letzten 24 Monate wie in der Karte der Wertpapierseite (AchtKAbruf: Liste der SEC, hoechstens zehn Dokumente,
// zwoelf Stunden gemerkt). Nur mit Kontaktadresse unter Zugaenge, nur fuer US-Aktien.
internal suspend fun AppState.quelleWarnzeichen(symbol: String, vorher: WidgetKarte?, stand: String, jetzt: Long): WidgetKarte? {
    val kennung = SecInsider.kennung(settingsFlow.value.secKontakt)
        ?: return WidgetKartenBauAbschnitte.warnzeichenHinweis(symbol, S.widgetWarnOhneKennung, 0L)
    runCatching { secOhneNetz() }
    val quote = runCatching { market.snapshot(listOf(symbol))[symbol] }.getOrNull()
    if (!istUsAktie(symbol, quote?.nativeCurrency)) return WidgetKartenBauAbschnitte.warnzeichenHinweis(symbol, S.widgetWarnNurUs, jetzt)
    val cik = sec.value?.geschaefte?.firstOrNull { it.k.equals(symbol, true) }?.c?.ifBlank { null }
    val erg = runCatching { AchtKAbruf.laden(symbol, kennung, cik, LocalDate.now(ZoneOffset.UTC)) }.getOrNull()
        ?: return beiFehler(vorher, WidgetKartenBauAbschnitte.warnzeichenHinweis(symbol, S.widgetWarnFehler, jetzt))
    return widgetWarnzeichen(symbol, erg.warnzeichen, stand, jetzt)
}

// ------------------------------------------------------------ Praesidialdokumente

internal fun widgetPraesidial(liste: List<Praesidialdokumente.Dokument>, stand: String, am: Long): WidgetKarte =
    WidgetKartenBauAbschnitte.praesidial(liste.map { WidgetKartenBauAbschnitte.Dok(it.titel, S.pdArt(it.art.name), tagMs(it.tag)) }, stand, am)

// Federal Register, ein Abruf mit den Dokumenten der letzten zwoelf Monate (sechs Stunden im Speicher), wie die Liste im Copytrading.
internal suspend fun quellePraesidial(vorher: WidgetKarte?, stand: String, jetzt: Long): WidgetKarte? {
    val ab = Uhr.heute().minusYears(1).toString()
    val liste = runCatching { Praesidialdokumente.laden(ab, jetzt) }.getOrNull() ?: return beiFehler(vorher, widgetPraesidial(emptyList(), stand, jetzt))
    return widgetPraesidial(liste, stand, jetzt)
}
