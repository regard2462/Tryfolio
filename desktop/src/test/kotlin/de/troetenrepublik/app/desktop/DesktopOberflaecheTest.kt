package de.troetenrepublik.app.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.market.Prognose
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.plattform.Browser
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.ZurueckVerteiler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

// Die ganze Desktop-App ohne Fenster: Oberflaeche in einem unsichtbaren Bild (420 x 900 dp, doppelte Dichte),
// echte Daten aus dem Netz, frischer Testordner unter build/. Der Browser oeffnet sich nicht (troete.browser=aus).
// Bildschirmfotos und Ablaufprotokoll landen in build/testbilder. Die Testuhr laeuft von Hand mit der echten Zeit,
// weil Dauer-Animationen wie der Live-Punkt ein „warten, bis alles ruht“ nie enden liessen.
@OptIn(ExperimentalTestApi::class)
abstract class RundgangBasis(protokollDatei: String) {
    protected val bilder = File(System.getProperty("troete.bilder") ?: "build/testbilder").apply { mkdirs() }
    protected val protokoll = File(bilder, protokollDatei).apply { writeText("") }

    init {
        // Bleibt ein Rundgang haengen, stehen nach 20 Minuten alle Threads im Protokoll. Ohne das endet der Lauf nur
        // mit Gradles Zeitlimit ohne Hinweis.
        Thread {
            try { Thread.sleep(20 * 60_000L) } catch (_: InterruptedException) { return@Thread }
            val sb = StringBuilder("Waechter: Test laeuft seit 20 Minuten, Threads:\n")
            Thread.getAllStackTraces().forEach { (th, st) ->
                sb.append("--- ").append(th.name).append(' ').append(th.state).append('\n')
                st.take(30).forEach { sb.append("    at ").append(it).append('\n') }
            }
            runCatching { protokoll.appendText(sb.toString()) }
        }.apply { isDaemon = true; name = "Waechter" }.start()
    }
    protected val start = System.currentTimeMillis()

    protected fun notiere(text: String) = protokoll.appendText("%6.1f s  %s\n".format((System.currentTimeMillis() - start) / 1000.0, text))

    protected fun DesktopComposeUiTest.zeit(ms: Long) {
        var rest = ms
        while (rest > 0) {
            val t = minOf(rest, 50L)
            Thread.sleep(t)
            mainClock.advanceTimeBy(t)
            rest -= t
        }
    }

    protected fun DesktopComposeUiTest.foto(name: String) {
        zeit(300)
        // Ganze Szene, auch offene Blaetter mit eigener Wurzel.
        ImageIO.write(captureToImage().toAwtImage(), "png", File(bilder, "$name.png"))
        notiere("Foto $name")
    }

    protected fun DesktopComposeUiTest.da(text: String, teil: Boolean = false) =
        onAllNodesWithText(text, substring = teil).fetchSemanticsNodes().isNotEmpty()

    // Waagerechte Leiste zu einem Eintrag rollen, der noch ausserhalb des Bilds liegt.
    protected fun DesktopComposeUiTest.leisteZu(text: String, anker: String) {
        val leiste = runCatching {
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyDescendant(hasText(anker)))[0].fetchSemanticsNode().id
        }.getOrNull() ?: return
        for (richtung in listOf(-250f, 250f)) repeat(12) {
            if (sichtbarWaagerecht(text)) return
            runCatching { onAllNodes(SemanticsMatcher("Leiste") { it.id == leiste })[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(richtung, 0f) } }
            zeit(250)
        }
    }

    // In kleinen Schritten wie eine echte Hand.
    protected fun DesktopComposeUiTest.mausZiehen(von: Offset, dx: Float) {
        onAllNodes(isRoot())[0].performMouseInput {
            moveTo(von)
            press()
            repeat(20) { moveBy(Offset(dx / 20f, 0f)); advanceEventTime(16) }
            release()
        }
        notiere("Maus gezogen ab $von um $dx")
        zeit(1500)
    }

    protected fun DesktopComposeUiTest.sichtbarWaagerecht(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().any { n -> n.boundsInRoot.let { it.width > 0f && it.left >= 0f && it.right <= 840f } }

    protected fun DesktopComposeUiTest.sichtbar(text: String, teil: Boolean = false) =
        onAllNodesWithText(text, substring = teil).fetchSemanticsNodes().any { n -> n.boundsInRoot.let { it.width > 0f && it.top >= 0f && it.bottom <= 1700f } }

    protected fun DesktopComposeUiTest.warte(ms: Long = 30_000, bedingung: () -> Boolean): Boolean {
        val ende = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < ende) {
            if (runCatching(bedingung).getOrDefault(false)) return true
            zeit(150)
        }
        return false
    }

    protected fun DesktopComposeUiTest.warteText(text: String, teil: Boolean = false, ms: Long = 30_000): Boolean =
        warte(ms) { da(text, teil) }.also { notiere("warte „$text“: $it") }

    // Bei mehreren Treffern den ersten im Bild, denn der Pager haelt Nachbarseiten bereit.
    protected fun DesktopComposeUiTest.tippe(text: String, teil: Boolean = false) {
        val knoten = onAllNodesWithText(text, substring = teil)
        val i = knoten.fetchSemanticsNodes().indexOfFirst { n ->
            val b = n.boundsInRoot
            b.left >= 0f && b.right <= 840f && b.top >= 0f && b.bottom <= 1800f && b.width > 0f
        }.coerceAtLeast(0)
        notiere("Treffer „$text“: " + knoten.fetchSemanticsNodes().joinToString { it.boundsInRoot.toString() } + " -> $i")
        knoten[i].performClick()
        notiere("tippe „$text“")
        zeit(600)
    }

    // Knopf ohne Text ueber seine Beschreibung antippen. False, wenn er nicht da ist.
    protected fun DesktopComposeUiTest.tippeSymbol(beschreibung: String): Boolean {
        val knoten = onAllNodesWithContentDescription(beschreibung)
        val i = knoten.fetchSemanticsNodes().indexOfFirst { n -> n.boundsInRoot.let { it.width > 0f && it.top >= 0f && it.bottom <= 1800f } }
        notiere("Symbol „$beschreibung“: $i")
        if (i < 0) return false
        knoten[i].performClick()
        zeit(600)
        return true
    }

    // Escape wie im echten Fenster: Die Taste geht erst an die Oberflaeche, offene Blaetter und Dialoge schliessen
    // sich selbst. Nimmt sie dort niemand, bekommt sie das Fenster (Main.kt, onKeyEvent) und damit der ZurueckVerteiler.
    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    protected fun DesktopComposeUiTest.escape(): String {
        val ergebnis = when {
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)) -> "Oberflaeche"
            ZurueckVerteiler.zurueck() -> "Fenster"
            else -> "niemand"
        }
        notiere("Escape: $ergebnis")
        zeit(900)
        return ergebnis
    }

    // Rollt nur, falls der Eintrag schon zusammengesetzt ist.
    protected fun DesktopComposeUiTest.rolleZu(text: String, anker: String = S.pvTitle) {
        // Listen bauen Eintraege erst beim Hinrollen auf, daher die Liste mit dem Anker schrittweise weiterschieben.
        val liste = runCatching {
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyDescendant(hasText(anker)))
                .fetchSemanticsNodes().last().id
        }.getOrNull()
        if (liste != null) repeat(25) {
            if (onAllNodesWithText(text).fetchSemanticsNodes().any { n -> n.boundsInRoot.let { it.width > 0f && it.top >= 0f && it.top <= 900f } }) return@repeat
            runCatching {
                onAllNodes(SemanticsMatcher("Liste") { it.id == liste })[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 500f) }
            }
            zeit(250)
        }
        zeit(600)
    }

    // Erste Wett-Pille „Ja 63 %“ antippen. False, wenn keine da ist.
    protected fun DesktopComposeUiTest.tippeJaPille(): Boolean {
        val pille = SemanticsMatcher("Ja-Pille") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.any { Regex("^${S.pmYes} \\S.*%$").matches(it.text) } == true
        }
        val knoten = onAllNodes(pille)
        val n = knoten.fetchSemanticsNodes()
        notiere("Ja-Pillen: ${n.size}")
        if (n.isEmpty()) return false
        knoten[0].performClick()
        zeit(800)
        return true
    }

    protected fun DesktopComposeUiTest.escapeImBlatt(textImBlatt: String, textDarunter: String): Boolean {
        escape()
        val ok = !da(textImBlatt) && da(textDarunter, teil = true)
        notiere("Blatt zu, Seite darunter offen: $ok")
        return ok
    }

    // Die sichtbare Breite entspricht der ganzen Breite des Elements.
    protected fun DesktopComposeUiTest.ganzSichtbarWaagerecht(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().any { n ->
            n.boundsInRoot.let { it.width > 0f && it.left >= 0f && it.right <= 840f && it.width >= n.size.width - 1f }
        }

    // Schiebt die innerste waagerechte Leiste mit der Kachel schrittweise.
    protected fun DesktopComposeUiTest.kachelZeigen(titel: String) {
        val kacheln = runCatching {
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyDescendant(hasText(titel)))
                .fetchSemanticsNodes().last().id
        }.getOrNull()
        if (kacheln != null) repeat(10) {
            if (ganzSichtbarWaagerecht(titel)) return@repeat
            runCatching { onAllNodes(SemanticsMatcher("Kacheln") { it.id == kacheln })[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(300f, 0f) } }
            zeit(250)
        }
        zeit(800)
    }

    // Frische App mit Testdepot (SAP, Apple) auf der Startseite.
    protected fun DesktopComposeUiTest.starteMitDepot(name: String): DesktopContainer {
        mainClock.autoAdvance = false
        val ordner = File(System.getProperty("troete.daten") ?: "build/testdaten", "$name-" + System.currentTimeMillis())
        Plattform.init(ordner)
        val c = DesktopContainer(ordner)
        I18n.lang = Lang.DE
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) { DesktopApp(c) }
        }
        runBlocking { c.settings.update { it.copy(onboarded = true, guidePending = false, ownerName = "Alex", language = Lang.DE, languageAuto = false) } }
        c.state.reset(10_000.0, announce = false)
        val vorEinemJahr = System.currentTimeMillis() - 365L * 86_400_000
        c.state.importPosition("SAP.DE", "SAP", 10.0, 180.0, vorEinemJahr)
        c.state.importPosition("AAPL", "Apple", 5.0, 150.0, vorEinemJahr)
        c.state.start()
        c.state.resume()
        assertTrue("Depot", warteText("Investments", ms = 30_000))
        zeit(2000)
        c.state.clearToast()
        return c
    }

    protected fun DesktopComposeUiTest.prognoseSchritte(c: DesktopContainer) {
        // Polymarket-Liste, ein Ereignis mit Verlauf, Wette mit Uebungsgeld, Meine Wetten.
        assertTrue("PM-Karte", warteText(S.pmTile, ms = 10_000))
        kachelZeigen(S.pmTile)
        foto("17c-start-kacheln")
        tippe(S.pmTile)
        zeit(1500)
        foto("17d-pm-geoeffnet")
        assertTrue("PM-Liste", warte(60_000) { !da(S.pmLoading) && (da(S.pmUnreachable) || da(S.pmNoResults) || da(" %", teil = true)) })
        zeit(3000)
        foto("18-pm-polymarket")
        val ereignis = runBlocking { c.state.prognose.liste(Prognose.Quelle.POLYMARKET, "") }?.ereignisse
            // Nur ein gerade angezeigtes Ereignis, die Liste ist live und kann sich zwischen zwei Abrufen aendern.
            ?.firstOrNull { e -> e.maerkte.firstOrNull()?.let { it.offen && it.seiten[0].kauf in 0.03..0.97 } == true && sichtbar(e.titel) }
        notiere("PM-Ereignis: ${ereignis?.titel}")
        var gewettet = false
        if (ereignis != null) {
            tippe(ereignis.titel)
            warte(30_000) { da(S.pmRules) }
            zeit(5000)
            foto("19-pm-ereignis")
            if (tippeJaPille()) {
                assertTrue("Wettblatt", warteText(S.pmPlaceBet, ms = 5_000))
                foto("20-pm-wettblatt")
                tippe(S.pmPlaceBet)
                gewettet = warte(30_000) { c.state.account.value.wetten.any { it.offen } }
                notiere("Wette platziert: $gewettet")
                zeit(1500)
                foto("20b-pm-gewettet")
                if (da(S.pmPlaceBet)) escape()
            }
            escape()
            foto("20c-pm-zurueck")
        }
        tippe(S.pmMyBets, teil = true)
        zeit(2000)
        foto("21-pm-meine-wetten")
        if (gewettet) {
            rolleZu(ereignis!!.titel, S.pmHistory)
            foto("21b-pm-meine-wetten-liste")
            assertTrue("Wette in Meine Wetten", warte(10_000) { da(ereignis.titel) })
        }
        tippe("Kalshi")
        warte(60_000) { !da(S.pmLoading) && (da(S.pmUnreachable) || da(S.pmNoResults) || da(" %", teil = true)) }
        zeit(3000)
        foto("22-pm-kalshi")
        escape()
    }
}

// Rundgang durch die ganze Oberflaeche. Prediction Markets laufen in PrognoseOberflaecheTest.
@OptIn(ExperimentalTestApi::class)
class DesktopOberflaecheTest : RundgangBasis("ablauf.txt") {
    @Test
    fun rundgang() = runDesktopComposeUiTest(width = 840, height = 1800) {
        mainClock.autoAdvance = false
        val ordner = File(System.getProperty("troete.daten") ?: "build/testdaten", "lauf-" + System.currentTimeMillis())
        Plattform.init(ordner)
        val c = DesktopContainer(ordner)
        I18n.lang = Lang.DE
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) { DesktopApp(c) }
        }

        // Neue Installation: Einrichtung
        assertTrue("Einrichtung", warteText("Tryfolio", teil = true, ms = 10_000))
        foto("01-einrichtung")

        // Testdepot ohne Einrichtung: zwei Positionen, ein Wert auf der Watchlist.
        // Kacheln zum Wischen, dann steht das Depot ohne Rollen im Bild.
        runBlocking { c.settings.update { it.copy(onboarded = true, guidePending = false, ownerName = "Alex", language = Lang.DE, languageAuto = false, kachelRaster = false) } }
        c.state.reset(10_000.0, announce = false)
        val vorEinemJahr = System.currentTimeMillis() - 365L * 86_400_000
        c.state.importPosition("SAP.DE", "SAP", 10.0, 180.0, vorEinemJahr)
        c.state.importPosition("AAPL", "Apple", 5.0, 150.0, vorEinemJahr)
        c.state.toggleWatch("NVDA")
        c.state.start()
        c.state.resume()
        assertTrue("Depot", warteText("Watchlist"))
        warte(20_000) { c.state.quotes.value.containsKey("SAP.DE") }
        zeit(2000)
        c.state.clearToast()
        zeit(500)
        foto("02-depot")

        // Unterhalb des Charts ziehen, der Chart zieht selbst.
        mausZiehen(von = Offset(700f, 1290f), dx = -500f)
        foto("02b-cash-per-maus")
        val aufCash = sichtbar(S.interest, teil = true) || sichtbar(S.transactions, teil = true)
        notiere("Cash per Maus: $aufCash")
        mausZiehen(von = Offset(140f, 240f), dx = 500f)
        assertTrue("Zurueck auf Portfolio", warte(5_000) { da(S.search) }) // Pille liegt unter der Sichtgrenze von sichtbar()

        // Depotwert antippen wechselt zu US-Dollar, erneutes Antippen zurueck zum Euro.
        val wechsel = SemanticsMatcher("Waehrungswechsel") { SemanticsActions.OnClick in it.config && it.config[SemanticsActions.OnClick].label == S.switchCurrency }
        onAllNodes(wechsel)[0].performClick()
        assertTrue("Dollar", warte(20_000) { Fmt.inUsd && da(" $", teil = true) })
        zeit(1500)
        foto("02c-depot-dollar")
        tippe("Cash")
        zeit(1000)
        foto("02d-cash-dollar")
        tippe("Portfolio")
        zeit(800)
        onAllNodes(wechsel)[0].performClick()
        assertTrue("Euro", warte(10_000) { !Fmt.inUsd })
        zeit(800)

        // News: Karte links neben der Watchlist, Kategorien, Zurueck mit Escape.
        tippe("News")
        assertTrue("News-Seite", warteText("tagesschau"))
        leisteZu("Notenbanken", "tagesschau")
        assertTrue("Reiter Notenbanken", warteText("Notenbanken", ms = 5_000))
        warte(45_000) { c.state.news.stand.value.eintraege.size > 20 }
        zeit(1000)
        foto("03-news")
        val schlagzeilen = c.state.news.stand.value.eintraege.size
        notiere("Schlagzeilen: $schlagzeilen")
        val wichtige = Rss.wichtige(c.state.news.stand.value.eintraege)
        notiere("Wichtige Meldungen: ${wichtige.size} von $schlagzeilen: " +
            c.state.news.stand.value.eintraege.filter { it.link in wichtige }.take(8).joinToString(" | ") { it.titel })
        // Mit der Maus von „Alle“ zu Yahoo Finance wischen, die Pille wandert mit.
        mausZiehen(von = Offset(600f, 1000f), dx = -420f)
        foto("03b-news-gewischt")
        leisteZu("Notenbanken", "tagesschau")
        tippe("Notenbanken")
        foto("04-news-notenbanken")
        tippe("EZB")
        foto("04b-news-notenbanken-ezb")
        leisteZu("Yahoo Finance", "Notenbanken")
        tippe("Yahoo Finance")
        foto("04c-news-yahoo")
        // Dienste mit eigenem Schluessel: ohne Schluessel steht der Weg zu den Zugaengen da.
        leisteZu("The Guardian", "Yahoo Finance")
        tippe("The Guardian")
        assertTrue("Guardian ohne Schluessel", warteText(S.newsKeyEnter, ms = 5_000))
        foto("04d-news-guardian-ohne-schluessel")
        tippe(S.newsKeyEnter)
        assertTrue("Zugaenge", warteText(S.zgTitle, ms = 5_000))
        zeit(800)
        foto("04e-zugaenge")
        escape()
        zeit(600)
        leisteZu("Notenbanken", "Yahoo Finance")
        tippe("Notenbanken")

        // Text direkt aus der Seite gelesen, sonst der Hinweis mit „Im Browser öffnen“.
        val erste = c.state.news.stand.value.eintraege.firstOrNull { "ezb" in it.quellen && runCatching { sichtbar(it.titel) }.getOrDefault(false) }
        notiere("Meldung: ${erste?.titel}")
        if (erste != null) {
            tippe(erste.titel)
            warteText("Im Browser öffnen", ms = 20_000)
            zeit(5000)
            foto("05-news-artikel")
            assertTrue("Escape schliesst nur das Artikelblatt", escapeImBlatt(S.openInBrowser, "Notenbanken"))
        }
        escape()

        // Copytrading: Kongress mit echten Meldungen, Person, Investoren, Strategien.
        assertTrue("Copytrading-Karte", warteText(S.copyTile, ms = 10_000))
        // „Verlauf“ steht davor, daher die Kachel erst ins Bild holen.
        kachelZeigen(S.copyTile)
        tippe(S.copyTile)
        zeit(4000)
        foto("10-copy-start")
        assertTrue("Copytrading", warteText(S.copyLatestTrades, ms = 60_000))
        zeit(3000)
        foto("11-copy-kongress")
        onAllNodes(hasSetTextAction())[0].performTextInput("Pelosi")
        zeit(1500)
        foto("11b-copy-suche")
        val pelosi = warte(20_000) { da("Nancy Pelosi") }
        notiere("Pelosi im Verzeichnis: $pelosi")
        if (pelosi) {
            tippe("Nancy Pelosi")
            warte(60_000) { da(S.copyMostTraded) }
            zeit(5000)
            foto("12-copy-pelosi")
            rolleZu(S.pvTitle, "Nancy Pelosi")
            warte(120_000) { da(S.pvTitle) && !da(S.pvLoading) }
            zeit(3000)
            foto("12b-copy-pelosi-verlauf")
            notiere("Pelosi-Verlauf: " + listOf(S.pvNone, S.pvCongressNoBuys).firstOrNull { da(it) }.let { it ?: "Graph" })
            if (tippeSymbol(S.calToggle)) {
                assertTrue("Kalender der Geschaefte", warteText(S.ctcBuys, ms = 10_000))
                zeit(1500)
                foto("12c-copy-pelosi-kalender")
                tippeSymbol(S.calToggle)
            }
            tippe(S.copyReplicate)
            zeit(1500)
            foto("13-copy-nachbilden")
            escape()
            // Optionen als Unterzweige der aktuellen Positionen.
            rolleZu(S.copyPositionsTitle, "Nancy Pelosi")
            notiere("Pelosi-Positionen: " + da(S.copyPositionsTitle))
            if (da(S.copyPositionsTitle) && tippeSymbol(S.copyPartsToggle(false))) {
                notiere("Pelosi-Unterzweige: " + da(S.copyPartShares))
                zeit(1500)
                foto("12d-copy-pelosi-positionen")
            }
            escape()
        }
        // Senat und Regierung: mitgelieferte Staende, Personenseiten mit Verlauf, Bestand des Vizepraesidenten.
        val senatDaten = de.troetenrepublik.app.market.Politik.senat(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/senat.json").decodeToString())
        assertTrue("Senatsdaten", senatDaten != null)
        tippe(S.copySenate)
        // Die Einleitung steht am Ende der Liste, oben die neuesten Geschaefte.
        assertTrue("Senat", warteText(S.copyLatestTrades, ms = 10_000))
        zeit(1500)
        foto("13b-copy-senat")
        val senator = de.troetenrepublik.app.market.Politik.personen(senatDaten!!, System.currentTimeMillis()).first().name
        notiere("Senat, aktivste Person: $senator")
        rolleZu(senator, S.copyLatestTrades)
        tippe(senator)
        warte(30_000) { da(S.copyMostTraded) }
        rolleZu(S.pvTitle, senator)
        warte(120_000) { da(S.pvTitle) && !da(S.pvLoading) }
        zeit(3000)
        foto("13c-copy-senator")
        escape()
        tippe(S.copyGovernment)
        zeit(1500)
        foto("13d-copy-regierung")
        if (warte(5_000) { da("Donald J. Trump") }) {
            tippe("Donald J. Trump")
            // Mit Foto und langem Hinweis liegt „Meistgehandelt“ unter dem Bild: erst Kennzahlen, dann hinrollen.
            assertTrue("Trump", warteText(S.copyBuysEst, ms = 30_000))
            zeit(3000)
            foto("13e-copy-trump")
            // Truth Social: Seite mit den Beitraegen aus dem Archiv „Trump's Truth“.
            assertTrue("Truth Social", warteText(S.truthTitle, ms = 10_000))
            tippe(S.truthTitle)
            // Beitraege erkennt man an ihrer Zeit. „Aeltere Beitraege“ steht erst am Ende der Liste.
            val beitrag = SemanticsMatcher("Beitragszeit") { n ->
                n.config.getOrNull(SemanticsProperties.Text)?.any { Regex("^\\d{2}\\.\\d{2}\\.\\d{4}, \\d{2}:\\d{2}").containsMatchIn(it.text) } == true
            }
            assertTrue("Archiv", warte(45_000) { onAllNodes(beitrag).fetchSemanticsNodes().isNotEmpty() || da(S.truthNone) })
            notiere("Truth Social: " + if (da(S.truthNone)) "Archiv nicht erreichbar" else "${onAllNodes(beitrag).fetchSemanticsNodes().size} Beitraege sichtbar")
            zeit(3000)
            foto("13e2-copy-truth")
            escape()
            assertTrue("zurueck bei Trump", warteText(S.copyBuysEst, ms = 10_000))
            rolleZu(S.pvTitle, S.copyBuysEst)
            warte(180_000) { da(S.pvTitle) && !da(S.pvLoading) }
            zeit(3000)
            foto("13f-copy-trump-verlauf")
            // Vorberechneter Verlauf aus Jahresmeldung und Einzelmeldungen.
            assertTrue("Hinweis zum Praesidentenverlauf", da("aus der Jahresmeldung 2025", teil = true))
            if (tippeSymbol(S.calToggle)) {
                assertTrue("Kalender beim Praesidenten", warteText(S.ctcBuys, ms = 10_000))
                zeit(1500)
                foto("13f2-copy-trump-kalender")
                tippeSymbol(S.calToggle)
            }
            rolleZu(S.copyMostTraded, S.copyBuysEst)
            assertTrue("Trump meistgehandelt", warteText(S.copyMostTraded, ms = 30_000))
            notiere("Trump: Meistgehandelt sichtbar")
            escape()
        }
        if (warte(3_000) { da("JD Vance") }) {
            tippe("JD Vance")
            assertTrue("Vance", warteText(S.copyHoldings, ms = 10_000))
            zeit(3000)
            foto("13g-copy-vance")
            escape()
        }
        tippe(S.copyInvestors)
        zeit(1500)
        foto("14-copy-investoren")
        tippe("Warren Buffett")
        assertTrue("Buffett", warteText(S.copyHoldings, ms = 10_000))
        zeit(5000)
        foto("15-copy-buffett")
        rolleZu(S.pvTitle, "Warren Buffett")
        warte(60_000) { !da(S.pvLoading) }
        zeit(3000)
        foto("15b-copy-buffett-verlauf")
        tippe("+ " + S.pvCompare)
        zeit(1200)
        foto("15c-vergleich-auswahl")
        tippe("S&P 500")
        zeit(600)
        escape()
        warte(30_000) { da("S&P 500") && !da("S&P 500 …", teil = true) }
        zeit(3000)
        foto("15d-buffett-mit-sp500")
        // Sharpe und weitere je Linie, Beta gegen den S&P 500.
        rolleZu(S.pvMoreStats, "Warren Buffett")
        tippe(S.pvMoreStats)
        assertTrue("Kennzahlen", warteText("Sharpe", teil = true, ms = 10_000))
        assertTrue("Beta gegen S&P 500", warteText("Beta", teil = true, ms = 10_000))
        zeit(1000)
        foto("15d2-buffett-kennzahlen")
        rolleZu(S.pvMoves, "Warren Buffett")
        zeit(800)
        foto("15e-buffett-quartale")
        escape()
        tippe(S.pvCompareInvestors)
        warte(60_000) { da(S.pvCompareScreen) && !da(S.pvLoading) }
        zeit(4000)
        foto("15f-investoren-vergleich")
        escape()
        tippe(S.copyStrategies)
        zeit(1000)
        foto("16-copy-strategien")
        tippe(S.pvCompareStrategies)
        warte(60_000) { da(S.pvCompareScreen) && !da(S.pvLoading) }
        zeit(6000)
        foto("16b-strategien-vergleich")
        escape()
        tippe("Permanent Portfolio")
        warte(60_000) { da(S.copyReturnPa) || da(S.copyNoHistory) }
        zeit(2000)
        foto("17-copy-permanent")
        rolleZu(S.pvTitle, "Permanent Portfolio")
        zeit(3000)
        foto("17b-copy-permanent-verlauf")
        escape()
        escape()

        // Aktie: SAP aus dem Depot, Logo und Chart.
        tippe("SAP")
        assertTrue("Aktienseite", warteText("Kaufen", teil = true))
        zeit(6000)
        foto("06-aktie-sap")

        // Firmenbeschreibung mit Quelle Wikipedia oder Yahoo.
        val quelle = listOf("Quelle: Yahoo Finance", "Quelle: Wikipedia").firstOrNull { q -> warte(15_000) { da(q) } }
        notiere("Quelle: $quelle")
        if (quelle != null) {
            // Schrittweise hinrollen, bis die Zeile in der oberen Haelfte steht. performScrollTo wartet mit
            // angehaltener Testuhr auf ein Rollen ohne Ende.
            rolleZu(quelle, quelle)
            zeit(800)
            tippe(quelle)
            zeit(6000)
            foto("07-quelle")
            assertTrue("Escape schliesst nur das Quellenblatt", escapeImBlatt(S.openInBrowser, "Kaufen"))
        }
        escape()

        // Finanzwissen oeffnet im Browser, der eingebaute Webserver liefert die Seite.
        Browser.zuletzt.clear()
        // Profil: Zugaenge, Sprache.
        tippe("A")
        assertTrue("Profil", warteText("Alex", ms = 10_000))
        zeit(500)
        foto("08-profil")
        escape()
        zeit(800)
        assertTrue("Finanzwissen-Kachel", warteText(S.knowledge, ms = 5_000))
        kachelZeigen(S.knowledge)
        tippe(S.knowledge)
        assertTrue("Finanzwissen-Seite", warteText("in deinem Browser", teil = true, ms = 10_000))
        foto("09-finanzwissen")
        val adresse = Browser.zuletzt.lastOrNull { "/finanzwissen/" in it }
        notiere("Finanzwissen: $adresse")
        assertTrue("Finanzwissen-Adresse", adresse != null)
        val html = URI(adresse!!).toURL().readText()
        assertTrue("Finanzwissen-HTML", "<html" in html.lowercase())
        escape()

        c.state.setLanguage(Lang.EN)
        assertTrue("Englisch", warteText("Search"))
        foto("10-englisch")

        notiere("fertig")
        assertTrue("Schlagzeilen geladen ($schlagzeilen)", schlagzeilen > 20)
        c.beenden()
    }
}
