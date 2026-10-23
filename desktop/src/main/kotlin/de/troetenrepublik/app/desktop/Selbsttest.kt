package de.troetenrepublik.app.desktop

import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.plattform.Artikel
import de.troetenrepublik.app.plattform.Finanzwissen
import de.troetenrepublik.app.plattform.Netz
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.ui.TroeteTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URI

// Prueft im fertigen Paket ohne Fenster, ob die mitgelieferte Java-Laufzeit alles kann, was die App braucht.
// Aufruf: Tryfolio --selbsttest <Ergebnisdatei>. Arbeitet in einem eigenen Ordner neben der Ergebnisdatei,
// nie im Datenordner der App.
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal object Selbsttest {
    fun laufen(ziel: String?): Int {
        val ausgabe = File(ziel ?: "troete-selbsttest.txt").absoluteFile
        val ordner = File(ausgabe.parentFile, "troete-selbsttest-daten").apply { mkdirs() }
        Plattform.init(ordner)
        val zeilen = mutableListOf<String>()
        var fehler = 0
        var netzFehler = 0
        var netzOk = 0
        // Abrufe aus dem Netz koennen am Anbieter scheitern (Cloud-Rechner werden etwa von Yahoo oft abgewiesen);
        // sie zaehlen nur, wenn keiner gelingt. Alles andere muss im Paket klappen.
        fun pruefe(name: String, netz: Boolean = false, block: () -> String) {
            val r = runCatching(block)
            when {
                r.isSuccess && netz -> netzOk++
                r.isFailure && netz -> netzFehler++
                r.isFailure -> fehler++
            }
            zeilen += (if (r.isSuccess) "OK    " else "FEHLER") + " $name: " + (r.getOrNull() ?: r.exceptionOrNull().toString())
        }
        zeilen += "Java ${System.getProperty("java.version")} · ${System.getProperty("os.name")} ${System.getProperty("os.arch")}"
        pruefe("Yahoo-Kurse", netz = true) { lade("https://query1.finance.yahoo.com/v8/finance/chart/SAP.DE?range=1d&interval=5m") }
        pruefe("Yahoo-Schlagzeilen", netz = true) { lade("https://feeds.finance.yahoo.com/rss/2.0/headline?s=SAP.DE&region=DE&lang=de-DE") }
        pruefe("tagesschau", netz = true) { lade("https://www.tagesschau.de/wirtschaft/index~rss2.xml") }
        pruefe("EZB", netz = true) { lade("https://www.ecb.europa.eu/rss/press.html") }
        pruefe("Federal Reserve", netz = true) { lade("https://www.federalreserve.gov/feeds/press_all.xml") }
        pruefe("Logo aus dem Netz", netz = true) {
            val b = Netz.bytes("https://www.google.com/s2/favicons?domain=sap.com&sz=128") ?: error("kein Bild")
            val img = org.jetbrains.skia.Image.makeFromEncoded(b)
            "${img.width}x${img.height}"
        }
        pruefe("Bild lesen (Skia)") {
            val img = org.jetbrains.skia.Image.makeFromEncoded(Plattform.asset("troete.png"))
            "${img.width}x${img.height}"
        }
        pruefe("HTML lesen (Jsoup)") {
            val doc = org.jsoup.Jsoup.parse("<article><p>${"a".repeat(60)}</p><p>${"b".repeat(60)}</p></article>")
            "${Artikel.absaetze(doc).size} Absaetze"
        }
        pruefe("Artikel aus dem Netz", netz = true) {
            val p = Artikel.lesen("https://de.m.wikipedia.org/wiki/SAP", wikipedia = true) ?: error("keine Seite")
            check(p.size >= 2) { "zu wenig Absaetze" }
            "${p.size} Absaetze"
        }
        pruefe("Oberflaeche (Compose und Skia, ohne Fenster)") {
            val scene = ImageComposeScene(400, 200) { TroeteTheme(dark = true) { Text("Tryfolio") } }
            val bild = scene.render()
            scene.close()
            "${bild.width}x${bild.height}"
        }
        pruefe("Deutsche Monatsnamen und Zahlen") {
            val de = java.util.Locale.GERMANY
            val datum = java.time.format.DateTimeFormatter.ofPattern("d. MMM yyyy, EEEE", de).format(java.time.LocalDate.of(2026, 10, 17))
            val zahl = String.format(de, "%,.2f", 1234.5)
            check(datum == "17. Okt. 2026, Samstag" && zahl == "1.234,50") { "$datum · $zahl" }
            "$datum · $zahl"
        }
        pruefe("Katalog") { "${Catalog.get().basiswerte.size} Basiswerte" }
        pruefe("Einstellungen (DataStore)") {
            val key = stringPreferencesKey("test")
            val ds = PreferenceDataStoreFactory.create { File(ordner, "test.preferences_pb") }
            runBlocking {
                ds.edit { it[key] = "ok" }
                ds.data.first()[key] ?: error("nicht gespeichert")
            }
        }
        pruefe("Finanzwissen (Webserver)") {
            val url = Finanzwissen.adresse("dunkel", "de") ?: error("Server startet nicht")
            val html = URI(url).toURL().readText()
            check("<html" in html.lowercase()) { "keine Seite" }
            Finanzwissen.stoppen()
            "${html.length} Zeichen"
        }
        val ok = fehler == 0 && netzOk > 0
        zeilen += when {
            ok && netzFehler == 0 -> "ALLES OK"
            ok -> "OK, $netzFehler Abrufe aus dem Netz gescheitert"
            netzOk == 0 -> "FEHLER: kein Abruf aus dem Netz gelungen"
            else -> "$fehler FEHLER"
        }
        ausgabe.writeText(zeilen.joinToString("\n") + "\n")
        return if (ok) 0 else 1
    }

    private fun lade(url: String): String {
        val b = Netz.bytes(url) ?: error("keine Antwort")
        return "${b.size} Bytes"
    }
}
