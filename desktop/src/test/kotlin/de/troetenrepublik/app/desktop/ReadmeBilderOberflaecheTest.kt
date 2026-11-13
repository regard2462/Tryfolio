package de.troetenrepublik.app.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.Plattform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.minutes

// Bilder fuer die README ohne Fenster: dieselben Motive in Deutsch und Englisch, Hoehe im Seitenverhaeltnis eines
// Telefons (9 : 20). Beispieldepot mit neutralem Namen, Kurse und Meldungen echt aus dem Netz. Bilder und Ablauf
// (readmebilder.txt) unter build/testbilder, Namen readme-<sprache>-<motiv>.png. Nur mit -Ptroete.readme=alle.
@OptIn(ExperimentalTestApi::class)
class ReadmeBilderOberflaecheTest : RundgangBasis("readmebilder.txt") {

    private lateinit var sprache: String

    // Nur diese Motive aufnehmen, null fuer alle.
    private var nur: Set<String>? = null

    private fun DesktopComposeUiTest.bild(motiv: String) = foto("readme-$sprache-$motiv")

    // Frische App mit Beispieldepot: sieben bekannte Aktien seit einem Jahr, ein Sparplan, zwei Werte auf der Watchlist.
    private fun DesktopComposeUiTest.starteBeispieldepot(lang: Lang): DesktopContainer {
        mainClock.autoAdvance = false
        val ordner = File(System.getProperty("troete.daten") ?: "build/testdaten", "readme-$lang-" + System.currentTimeMillis())
        Plattform.init(ordner)
        val c = DesktopContainer(ordner)
        I18n.lang = lang
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) { DesktopApp(c) }
        }
        runBlocking { c.settings.update { it.copy(onboarded = true, guidePending = false, ok13107 = true, ownerName = "Alex", language = lang, languageAuto = false) } }
        c.state.reset(10_000.0, announce = false)
        val vorEinemJahr = System.currentTimeMillis() - 365L * 86_400_000
        listOf(
            Triple("AAPL", "Apple", 18.0 to 190.0), Triple("MSFT", "Microsoft", 10.0 to 380.0),
            Triple("NVDA", "NVIDIA", 45.0 to 105.0), Triple("AMZN", "Amazon", 12.0 to 175.0),
            Triple("GOOGL", "Alphabet", 25.0 to 150.0), Triple("ASML.AS", "ASML", 3.0 to 650.0),
            Triple("V", "Visa", 8.0 to 270.0),
        ).forEach { (s, n, mp) -> c.state.importPosition(s, n, mp.first, mp.second, vorEinemJahr) }
        c.state.addPlan("AAPL", "Apple", 100.0, 1)
        c.state.toggleWatch("SAP.DE")
        c.state.toggleWatch("TSLA")
        c.state.start()
        c.state.resume()
        assertTrue("Depot", warteText(S.investments, ms = 30_000))
        warte(30_000) { c.state.quotes.value.keys.containsAll(listOf("AAPL", "NVDA", "V")) }
        zeit(4000)
        c.state.clearToast()
        return c
    }

    // Rollt die Liste mit dem Anker, bis text hoechstens oben Pixel unter dem oberen Rand steht.
    private fun DesktopComposeUiTest.rolleBis(text: String, anker: String, oben: Float = 160f) {
        val liste = runCatching {
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyDescendant(hasText(anker)))
                .fetchSemanticsNodes().maxByOrNull { it.size.height }!!.id
        }.getOrNull() ?: return
        repeat(40) {
            val top = onAllNodesWithText(text).fetchSemanticsNodes().map { it.boundsInRoot.top }.filter { it >= -1000f }.minOrNull()
            if (top != null && top <= oben) {
                // Zuruecknehmen, wenn die Zeile unter den Kopf gerutscht ist
                if (top < oben - 100f) runCatching { onAllNodes(SemanticsMatcher("Liste") { it.id == liste })[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, top - oben) } }
                zeit(600)
                return
            }
            val schritt = if (top != null) minOf(500f, top - oben + 10f) else 500f
            runCatching { onAllNodes(SemanticsMatcher("Liste") { it.id == liste })[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, schritt) } }
            zeit(250)
        }
        zeit(600)
    }

    // Kachel der Startseite oeffnen.
    private fun DesktopComposeUiTest.kachel(titel: String) {
        rolleZu(titel, S.investments)
        kachelZeigen(titel)
        tippe(titel)
        zeit(2000)
    }

    // Zurueck zur Startseite: so oft Escape, bis niemand es mehr nimmt.
    private fun DesktopComposeUiTest.heim() {
        repeat(8) { if (escape() == "niemand") return }
    }

    // Taste des Ziffernblocks: der unterste Knoten mit diesem Text.
    private fun DesktopComposeUiTest.taste(z: String) {
        val knoten = onAllNodesWithText(z)
        val i = knoten.fetchSemanticsNodes().withIndex().maxByOrNull { it.value.boundsInRoot.top }?.index ?: return
        knoten[i].performClick()
        zeit(400)
    }

    // Ein Motiv ohne Abbruch des ganzen Laufs: Fehler landen im Protokoll.
    private fun DesktopComposeUiTest.motiv(name: String, schritte: DesktopComposeUiTest.() -> Unit) {
        if (nur != null && name !in nur!!) return
        notiere("Motiv $name")
        runCatching { schritte() }.onFailure { notiere("Motiv $name fehlgeschlagen: $it") }
    }

    private fun DesktopComposeUiTest.motive(lang: Lang) {
        sprache = if (lang == Lang.DE) "de" else "en"
        val jahr = if (lang == Lang.DE) "1J" else "1Y"
        val c = starteBeispieldepot(lang)

        motiv("depot") {
            tippe(jahr)
            zeit(6000)
            bild("depot")
            rolleBis(S.investments, S.investments, oben = 120f)
            zeit(3000)
            bild("positionen")
            rolleZu(S.watchlist, S.investments)
            bild("kacheln")
        }

        motiv("aktie") {
            c.state.springeZu(WidgetZiel.instrument("AAPL"))
            assertTrue("Seite", warteText(S.sell, ms = 30_000))
            zeit(10_000)
            tippe(jahr)
            zeit(5000)
            bild("aktie")
            tippe(S.indButton)
            tippe(S.indName("macd"))
            warte(40_000) { da(S.p1BilanzWenig) || da(S.indName("macd"), teil = true) }
            zeit(4000)
            bild("indikatoren")
            rolleBis(S.indButton, S.indButton, oben = 300f)
            bild("indikatoren2")
            tippe(S.indName("macd"))
            tippe(S.indButton)
            zeit(1000)
        }

        motiv("kaufen") {
            tippe(S.buy)
            zeit(2500)
            listOf("5", "0", "0").forEach { taste(it) }
            zeit(1500)
            bild("kaufen")
            escape()
        }

        motiv("behoerden") {
            if (nur != null) {
                c.state.springeZu(WidgetZiel.instrument("AAPL"))
                assertTrue("Seite", warteText(S.sell, ms = 30_000))
                zeit(10_000)
            }
            // Der Abschnitt Hintergrund ist eingeklappt; die Karte steht als letztes Thema darin
            rolleZu(S.bwHintergrund, S.indButton)
            tippe(S.bwHintergrund)
            zeit(1500)
            rolleBis(S.bhTitel, S.bwHintergrund)
            warte(90_000) { da(S.bhTitel) && !da(S.loading) }
            zeit(5000)
            // Die Karte steht am Ende der Seite; weiter als bis zum Ende rollt die Liste nicht
            rolleBis(S.bhTitel, S.bhTitel, oben = 110f)
            bild("behoerden")
        }
        heim()

        motiv("copytrading") {
            kachel(S.copyTitle)
            assertTrue("Copytrading", warteText(S.copyLatestTrades, ms = 60_000))
            zeit(6000)
            bild("copytrading")
            rolleZu(S.rgTitel, S.copyLatestTrades)
            tippe(S.rgTitel)
            val fertig = warte(600_000) { da(S.rgPunkte("").trim(), teil = true) }
            notiere("Rangliste gerechnet: $fertig")
            zeit(4000)
            bild("rangliste")
            escape()
            rolleZu(S.kaTitel, S.copyLatestTrades)
            tippe(S.kaTitel)
            warte(180_000) { !da(S.pvLoading) && da("S&P 500", teil = true) }
            zeit(5000)
            bild("kongress")
        }
        heim()

        motiv("maerkte") {
            kachel(S.maerkteTitel)
            zeit(3000)
            listOf("stimmung" to S.widgetStimmung, "ausblick" to S.maerkteAusblick, "trends" to S.trendsTitel).forEach { (n, reiter) ->
                tippe(reiter)
                zeit(15_000)
                bild(n)
            }
            tippe(S.heatmap)
            // Kacheln laden (der fruehere Zaehler steigt/faellt ist entfallen)
            zeit(60_000)
            zeit(8000)
            bild("heatmap")
        }
        heim()

        motiv("prognose") {
            kachel(S.pmTitle)
            warte(60_000) { !da(S.pmLoading) && (da(" %", teil = true) || da("\u00A0%", teil = true)) }
            zeit(6000)
            bild("prognose")
            tippe(S.pmGross)
            zeit(1500)
            warte(90_000) { !da(S.pmLoading) }
            zeit(6000)
            bild("wetten")
        }
        heim()

        motiv("analytics") {
            kachel(S.analytics)
            tippe(S.toolsTitle)
            zeit(2000)
            bild("werkzeuge")
            tippe(S.ptTitle("PERFORMANCE"))
            zeit(12_000)
            bild("performance")
        }
        heim()

        motiv("news") {
            kachel(S.news)
            warte(60_000) { c.state.news.stand.value.eintraege.size > 20 }
            zeit(5000)
            bild("news")
        }
        notiere("fertig $sprache")
        c.beenden()
    }

    // Laeuft nur auf Wunsch (-Ptroete.readme=alle oder Motive mit Komma), sonst uebersprungen: der Lauf dauert lange.
    private fun DesktopComposeUiTest.aufWunsch(lang: Lang) {
        val wahl = System.getProperty("troete.readme")
        Assume.assumeTrue("ohne -Ptroete.readme", wahl != null)
        nur = wahl!!.takeIf { it != "alle" }?.split(',')?.map { it.trim() }?.toSet()
        motive(lang)
    }

    @Test
    fun readmeBilderDe() = runDesktopComposeUiTest(width = 840, height = 1867, testTimeout = 30.minutes) { aufWunsch(Lang.DE) }

    @Test
    fun readmeBilderEn() = runDesktopComposeUiTest(width = 840, height = 1867, testTimeout = 30.minutes) { aufWunsch(Lang.EN) }
}
