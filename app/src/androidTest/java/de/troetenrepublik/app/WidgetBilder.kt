package de.troetenrepublik.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.plattform.DepotWidget
import de.troetenrepublik.app.plattform.GlobusWidget
import de.troetenrepublik.app.plattform.HeatmapWidget
import de.troetenrepublik.app.plattform.MoverWidget
import de.troetenrepublik.app.plattform.NewsWidget
import de.troetenrepublik.app.plattform.StimmungWidget
import de.troetenrepublik.app.plattform.Widgets
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

// Foto des Startbildschirms mit echten Widgets (0.68): die App bittet den Launcher, Depot, Heatmap, Top & Flop und
// Stimmung anzulegen, der Test bestaetigt den Dialog, laedt die Widgetdaten und fotografiert den Startbildschirm.
// Ablage: files/anleitung/<sprache>/widgets.png. Der Launcher muss aktiv sein.
@RunWith(AndroidJUnit4::class)
class WidgetBilder {

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val geraet = UiDevice.getInstance(inst)
    private val app = inst.targetContext.applicationContext as TroeteApp
    private val sprache = InstrumentationRegistry.getArguments().getString("sprache") ?: "en"
    // Mit -e news <Inhalt> nur ein News-Widget mit diesem Inhalt (WidgetDaten.NEWS_*), Foto news-<Inhalt>.png
    private val news = InstrumentationRegistry.getArguments().getString("news")
    // Mit -e neu 1 nur das Widget Globus, Foto neu.png
    private val neu = InstrumentationRegistry.getArguments().getString("neu") == "1"
    private val ordner = File(app.getExternalFilesDir(null), "anleitung/$sprache").apply { mkdirs() }

    private fun warte(ms: Long) = Thread.sleep(ms)

    private fun notiz(text: String) {
        println(text)
        runCatching { File(ordner, "widgets.txt").appendText(text + "\n") }
    }

    @Test
    fun widgets() {
        val lang = if (sprache == "en") Lang.EN else Lang.DE
        I18n.lang = lang
        val state = app.container.state
        runBlocking {
            app.container.settings.update {
                it.copy(onboarded = true, guidePending = false, ownerName = "Alex", language = lang, languageAuto = false)
            }
        }
        state.reset(10_000.0, announce = false)
        val vorEinemJahr = System.currentTimeMillis() - 365L * 86_400_000
        state.importPosition("SAP.DE", "SAP", 12.0, 180.0, vorEinemJahr)
        state.importPosition("AAPL", "Apple", 8.0, 160.0, vorEinemJahr)
        state.importPosition("NVDA", "NVIDIA", 20.0, 95.0, vorEinemJahr)
        state.importPosition("EUNL.DE", "iShares Core MSCI World", 40.0, 85.0, vorEinemJahr)
        state.importPosition("BTC-EUR", "Bitcoin", 0.05, 45_000.0, vorEinemJahr)

        // App einmal starten, damit Kurse und Depotwert da sind
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        warte(20_000)

        val m = AppWidgetManager.getInstance(app)
        notiz("Anheften moeglich: ${m.isRequestPinAppWidgetSupported}")
        val knopf = Pattern.compile("(?i)(add( to home screen)?|hinzuf.*|zum startbildschirm.*)")
        val klassen = if (neu) listOf(GlobusWidget::class.java) else if (news != null) listOf(NewsWidget::class.java) else listOf(DepotWidget::class.java, MoverWidget::class.java, HeatmapWidget::class.java, StimmungWidget::class.java)
        for (klasse in klassen) {
            geraet.pressHome(); warte(3000)
            val ok = m.requestPinAppWidget(ComponentName(app, klasse), null, null)
            notiz("Anheften ${klasse.simpleName}: $ok")
            if (geraet.wait(Until.hasObject(By.text(knopf)), 15_000) == true) {
                geraet.findObject(By.text(knopf))?.click()
                notiz("bestaetigt ${klasse.simpleName}")
            } else notiz("kein Dialog fuer ${klasse.simpleName}")
            warte(4000)
        }

        if (news != null) for (wid in m.getAppWidgetIds(ComponentName(app, NewsWidget::class.java))) Widgets.newsArtSetzen(app, wid, news)
        // Daten fuer die nun vorhandenen Widgets laden und zeichnen; die Heatmap braucht den Sammelabruf
        repeat(3) {
            runCatching { runBlocking { state.widgetsAuffrischen() } }.onFailure { notiz("Auffrischen: $it") }
            Widgets.zeichnen(app)
            warte(15_000)
        }
        notiz("Widgetarten: ${Widgets.arten()}")
        geraet.pressHome(); warte(5000)
        geraet.takeScreenshot(File(ordner, if (neu) "neu.png" else if (news != null) "news-$news.png" else "widgets.png"))
        notiz("Foto widgets")
    }
}
