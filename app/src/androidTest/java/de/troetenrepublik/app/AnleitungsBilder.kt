package de.troetenrepublik.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// Bildschirmfotos fuer die Kurzanleitung: startet die App im Emulator mit Beispieldepot und fotografiert die Seiten.
// Laeuft im GitHub-Ablauf „Anleitungsbilder“ mit Argument sprache=de oder en. Ablage: files/anleitung/<sprache>.
// Ein gescheiterter Schritt wird uebersprungen.
@RunWith(AndroidJUnit4::class)
class AnleitungsBilder {

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val geraet = UiDevice.getInstance(inst)
    private val app = inst.targetContext.applicationContext as TroeteApp
    private val sprache = InstrumentationRegistry.getArguments().getString("sprache") ?: "de"
    private val ordner = File(app.getExternalFilesDir(null), "anleitung/$sprache").apply { mkdirs() }

    private fun warte(ms: Long) = Thread.sleep(ms)

    // Ablaufnotiz, landet neben den Fotos.
    private fun notiz(text: String) {
        println(text)
        runCatching { File(ordner, "ablauf.txt").appendText(text + "\n") }
    }

    // Systemdialoge des Emulators verdecken die App und fangen Taps ab.
    private fun stoererWeg() {
        repeat(3) {
            val knopf = listOf("Wait", "Warten", "Close app", "App schließen").firstNotNullOfOrNull { geraet.findObject(By.text(it)) }
                ?: return
            notiz("Systemdialog weggeklickt: ${knopf.text}")
            knopf.click()
            warte(1500)
        }
    }

    private fun da(text: String, ms: Long = 20_000): Boolean = geraet.wait(Until.hasObject(By.text(text)), ms) == true

    private fun tippe(text: String, ms: Long = 20_000): Boolean {
        stoererWeg()
        if (!da(text, ms)) { notiz("nicht gefunden: $text"); return false }
        geraet.findObject(By.text(text))?.click() ?: return false
        notiz("tippe $text")
        warte(1500)
        return true
    }

    private fun foto(name: String) {
        warte(2500)
        stoererWeg()
        geraet.takeScreenshot(File(ordner, "$name.png"))
        notiz("Foto $name")
    }

    private fun schritt(name: String, block: () -> Unit) {
        stoererWeg()
        runCatching(block).onFailure { notiz("Schritt $name gescheitert: $it") }
    }

    private fun zurueck(mal: Int = 1) = repeat(mal) { geraet.pressBack(); warte(1200) }

    private fun nachOben(mal: Int = 1) = repeat(mal) {
        geraet.swipe(geraet.displayWidth / 2, geraet.displayHeight * 3 / 4, geraet.displayWidth / 2, geraet.displayHeight / 4, 30)
        warte(800)
    }

    // Wartet hoechstens ms Millisekunden.
    private fun bisWeg(text: String, ms: Long = 60_000) {
        geraet.wait(Until.gone(By.text(text)), ms)
    }

    private fun profilOffen(): Boolean = geraet.hasObject(By.text("Alex")) || (tippe("A") && da("Alex", 10_000))

    private fun starten() {
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        da(S.investments, 60_000)
    }

    private fun kacheln(raster: Boolean) = runBlocking { app.container.settings.update { it.copy(kachelRaster = raster) } }.also { warte(1500) }

    @Test
    fun fotos() {
        val lang = if (sprache == "en") Lang.EN else Lang.DE
        I18n.lang = lang
        val state = app.container.state
        runBlocking {
            app.container.settings.update {
                it.copy(onboarded = true, guidePending = false, ownerName = "Alex", language = lang, languageAuto = false, kachelRaster = false)
            }
        }
        state.reset(10_000.0, announce = false)
        val vorEinemJahr = System.currentTimeMillis() - 365L * 86_400_000
        state.importPosition("SAP.DE", "SAP", 12.0, 180.0, vorEinemJahr)
        state.importPosition("AAPL", "Apple", 8.0, 160.0, vorEinemJahr)
        state.importPosition("NVDA", "NVIDIA", 20.0, 95.0, vorEinemJahr)
        state.importPosition("EUNL.DE", "iShares Core MSCI World", 40.0, 85.0, vorEinemJahr)
        state.importPosition("BTC-EUR", "Bitcoin", 0.05, 45_000.0, vorEinemJahr)
        state.addPlan("EUNL.DE", "iShares Core MSCI World", 100.0, 1)

        starten()
        da(S.groupStocksEtfs, 60_000)
        warte(8000)

        schritt("start") {
            // Ein Jahr zeigt mehr vom Verlauf als der erste Handelstag am Morgen.
            tippe(S.rangeLabel(Range.Y1)); warte(4000)
            foto("start_wischen")
            kacheln(true)
            foto("start_raster")
        }
        // Fuer die Navigation bleiben die Kacheln untereinander, dann sind alle ohne Wischen zu sehen.
        schritt("ueberweisung") { if (tippe(S.transfer)) { foto("ueberweisung"); zurueck() } }
        schritt("suche") {
            if (tippe(S.search)) {
                warte(4000)
                foto("entdecken")
                val feld = geraet.findObject(By.clazz("android.widget.EditText"))
                feld?.click(); warte(500)
                feld?.text = "Apple"
                warte(6000)
                // Tastatur schliessen, damit die Treffer zu sehen sind.
                geraet.pressBack(); warte(1500)
                foto("suche")
                zurueck()
            }
        }
        schritt("aktie") {
            state.springeZu(de.troetenrepublik.app.data.WidgetZiel.instrument("AAPL"))
            da(S.buy, 30_000); warte(3000)
            tippe(S.rangeLabel(Range.Y1)); warte(5000)
            foto("aktie")
            if (tippe(S.buy)) {
                listOf("5", "0", "0").forEach { tippe(it) }
                foto("kaufen"); zurueck()
            }
            if (tippe(S.indButton)) {
                listOf("sma50", "bb", "macd").forEach { tippe(S.indName(it)) }
                warte(6000)
                geraet.swipe(geraet.displayWidth / 2, geraet.displayHeight * 5 / 8, geraet.displayWidth / 2, geraet.displayHeight * 7 / 16, 120)
                foto("indikatoren")
            }
            nachOben(2); foto("aktie_kennzahlen")
            var n = 0
            while (n < 12 && !geraet.hasObject(By.text(S.toolsTitle))) { nachOben(); n++ }
            nachOben(); foto("aktie_werkzeuge")
            // Politik und Behoerden steht ueber den Werkzeugen
            var m = 0
            while (m < 6 && !geraet.hasObject(By.text(S.bhTitel))) {
                geraet.swipe(geraet.displayWidth / 2, geraet.displayHeight / 3, geraet.displayWidth / 2, geraet.displayHeight * 2 / 3, 120); warte(900); m++
            }
            if (tippe(S.bhLobbying)) foto("behoerden")
            zurueck()
        }
        schritt("trends") {
            if (tippe(S.watchlist)) {
                warte(2000)
                if (tippe(S.trendsTitel)) { warte(10_000); foto("trends") }
                if (tippe(S.heatmap)) { warte(20_000); foto("heatmap") }
                zurueck()
            }
        }
        schritt("analytics") { if (tippe(S.analytics)) { warte(6000); foto("analytics"); zurueck() } }
        schritt("copytrading") {
            if (tippe(S.copyTile)) {
                warte(12_000); foto("copytrading")
                // Die Rangliste laedt die Kurse aller gekauften Aktien; das dauert
                if (tippe(S.rgTitel)) { warte(10_000); bisWeg(S.kaLaedt, 240_000); warte(3000); foto("rangliste"); zurueck() }
                zurueck()
            }
        }
        schritt("prognose") {
            if (tippe(S.pmTile)) {
                warte(12_000); foto("prognose")
                if (tippe(S.pmGross)) { warte(8000); foto("grosse_wetten") }
                zurueck()
            }
        }
        schritt("news") { if (tippe(S.newsTile)) { warte(3000); bisWeg(S.newsLoading); warte(4000); foto("news"); zurueck() } }
        schritt("rueckblick") {
            state.springeZu("rueckblick"); warte(5000)
            foto("rueckblick"); zurueck()
        }
        schritt("profil") {
            if (profilOffen()) {
                warte(2000); foto("profil")
                if (tippe(S.savingsPlans)) { foto("sparplaene"); zurueck() }
            }
        }
        // Langsam rollen, schnelles Wischen schiesst ueber die Zeile hinaus.
        for ((titel, name) in listOf(S.sperreTitel to "sicherheit", S.brTitel to "broker", S.zgTitle to "zugaenge")) schritt(name) {
            starten(); warte(3000)
            if (profilOffen()) {
                var n = 0
                while (n < 30 && !geraet.hasObject(By.text(titel))) {
                    geraet.swipe(geraet.displayWidth / 2, geraet.displayHeight * 2 / 3, geraet.displayWidth / 2, geraet.displayHeight / 3, 120)
                    warte(900); n++
                }
                if (tippe(titel)) { warte(4000); foto(name); zurueck() }
            }
        }
        kacheln(false)
    }
}
