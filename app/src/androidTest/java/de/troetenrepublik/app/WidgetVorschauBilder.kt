package de.troetenrepublik.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.WidgetKarten
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.plattform.Widgets
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// Vorschaubilder der Widget-Auswahl: jedes Widget wird mit dem Code der echten Widgets (Widgets.vorschauAnsicht) und echten
// Daten erzeugt, im Testprozess aufgeblasen, in der Standardgroesse des Widgets gemessen und samt abgerundetem Grund als
// webp gespeichert. Ablage: files/vorschau/<sprache>/widget_bild_<name>.webp, Protokoll vorschau.txt daneben.
// Aufruf: am instrument -w -e class de.troetenrepublik.app.WidgetVorschauBilder -e sprache de|en
@RunWith(AndroidJUnit4::class)
class WidgetVorschauBilder {

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val app = inst.targetContext.applicationContext as TroeteApp
    private val sprache = InstrumentationRegistry.getArguments().getString("sprache") ?: "en"
    private val ordner = File(app.getExternalFilesDir(null), "vorschau/$sprache").apply { mkdirs() }

    // Zellgroesse des Startbildschirms (dp) und Rand je Widget, wie beim Pixel-Launcher
    private val zelleB = 74
    private val zelleH = 94
    private val rand = 16
    // Radius des Widget-Grunds (widget_hintergrund)
    private val radiusDp = 20f

    private fun notiz(text: String) {
        println(text)
        runCatching { File(ordner, "vorschau.txt").appendText(text + "\n") }
    }

    @Test
    fun vorschauBilder() {
        ordner.listFiles()?.forEach { it.delete() }
        val lang = if (sprache == "en") Lang.EN else Lang.DE
        I18n.lang = lang
        val state = app.container.state
        // Einem mitgelieferten Fonds (13F-Melder) folgen, damit Copytrading Geschaefte statt der Rangliste zeigt
        val folgt = listOf("soros", "dalio", "buffett").firstNotNullOfOrNull { k -> state.investoren.firstOrNull { it.id == k && it.aktuell != null } }
            ?: state.investoren.firstOrNull { it.aktuell != null }
        runBlocking {
            app.container.settings.update {
                it.copy(
                    onboarded = true, guidePending = false, ownerName = "Alex", language = lang, languageAuto = false,
                    copyGefolgt = folgt?.let { f -> "i:" + f.id }.orEmpty(),
                    // Normale Einrichtung der App: je Installation eine zufaellige Adresse unter example.org (keine echte Adresse)
                    secKontakt = it.secKontakt.ifBlank { SecInsider.zufallsKontakt() }, secKontaktErzeugt = true,
                )
            }
        }
        // Testdepot wie in WidgetBilder
        state.reset(10_000.0, announce = false)
        val vorEinemJahr = System.currentTimeMillis() - 365L * 86_400_000
        state.importPosition("SAP.DE", "SAP", 12.0, 180.0, vorEinemJahr)
        state.importPosition("AAPL", "Apple", 8.0, 160.0, vorEinemJahr)
        state.importPosition("NVDA", "NVIDIA", 20.0, 95.0, vorEinemJahr)
        state.importPosition("EUNL.DE", "iShares Core MSCI World", 40.0, 85.0, vorEinemJahr)
        state.importPosition("BTC-EUR", "Bitcoin", 0.05, 45_000.0, vorEinemJahr)
        for (s in listOf("MSFT", "TSLA", "AMZN", "^GDAXI")) state.toggleWatch(s)
        // Eine offene Limit-Order (Kauf deutlich unter dem Kurs, bleibt offen) und zwei Sparplaene, erfundene Werte
        state.placeOrder("MSFT", "Microsoft", "MSFT", Side.BUY, OrderType.LIMIT, 5.0, 250.0)
        state.addPlan("EUNL.DE", "iShares Core MSCI World", 200.0, 15, PlanInterval.MONTHLY)
        state.addPlan("AAPL", "Apple", 50.0, 1, PlanInterval.MONTHLY)

        // Alle Widgets als vorhanden denken, damit die App alle Inhalte laedt
        Widgets.vorschauModus(true)
        try {
            val sprachKontext = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale(sprache)) })
            var daten = state.widgetDaten()
            for (runde in 1..4) {
                val ok = runBlocking { withTimeoutOrNull(240_000L) { state.widgetsAuffrischen() } }
                if (ok == null) notiz("Laden Runde $runde: Zeitgrenze erreicht")
                daten = state.widgetDaten()
                val fehlt = fehlendeKarten(daten)
                notiz("Laden Runde $runde: Daten ${if (daten != null) "da" else "fehlen"}, Karten ohne Inhalt: $fehlt")
                if (daten != null && fehlt.isEmpty()) break
                // Eine gescheiterte Karte gilt in der App bis zur naechsten Frist; fuer den Test neu abrufen
                if (runde < 4) runCatching { state.widgetKartenVergessen() }
                Thread.sleep(10_000)
            }
            val fehler = ArrayList<String>()
            val m = AppWidgetManager.getInstance(app)
            val infos = m.getInstalledProvidersForPackage(app.packageName, null).associateBy { it.provider.className }
            val dichte = sprachKontext.resources.displayMetrics.density
            var anzahl = 0
            for (klasse in Widgets.vorschauKlassen()) {
                val name = klasse.simpleName
                try {
                    val info = infos[klasse.name]
                    if (info == null) { fehler += "$name: kein Provider"; continue }
                    if (info.previewImage == 0) { fehler += "$name: kein previewImage"; continue }
                    val datei = sprachKontext.resources.getResourceEntryName(info.previewImage) + ".webp"
                    val (b, h) = groesse(info, dichte)
                    val bild = zeichne(sprachKontext, klasse, daten, b, h, dichte)
                    File(ordner, datei).outputStream().use { o ->
                        bild.compress(webp(), 90, o)
                    }
                    anzahl++
                    notiz("$datei ${b}x$h dp (${bild.width}x${bild.height} px)")
                } catch (e: Throwable) {
                    fehler += "$name: $e"
                }
            }
            notiz("Bilder: $anzahl von ${Widgets.vorschauKlassen().size}")
            fehler.forEach { notiz("FEHLER $it") }
            assertTrue("Vorschaubilder fehlgeschlagen: $fehler", fehler.isEmpty())
        } finally {
            Widgets.vorschauModus(false)
        }
    }

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    // Karten der Kategorie-Widgets ohne Inhalt (nach dem Laden); die Auswahl kennt dieselben Schluessel wie das Widget.
    // Wissen steht nicht in den Karten der Daten: das Widget baut es beim Zeichnen aus dem gespeicherten Lernstand.
    private fun fehlendeKarten(d: de.troetenrepublik.app.data.WidgetDaten?): List<String> {
        if (d == null) return listOf("alle")
        val mitWert = setOf(WidgetKarten.SIGNALE, WidgetKarten.VOLATILITAET, WidgetKarten.WARNZEICHEN)
        return WidgetKarten.ARTEN.filter { it != WidgetKarten.WISSEN }.filter { art ->
            val k = d.karten[WidgetVorgabe.kartenSchluessel(art, "AAPL".takeIf { art in mitWert })]
            k == null || (k.zeilen.isEmpty() && k.haupt.isBlank())
        }
    }

    // Standardgroesse in dp: Zellen des Startbildschirms, mindestens die Mindestgroesse der Info-Datei.
    private fun groesse(info: AppWidgetProviderInfo, dichte: Float): Pair<Int, Int> {
        val minB = (info.minWidth / dichte).roundToInt()
        val minH = (info.minHeight / dichte).roundToInt()
        val zb = if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) info.targetCellWidth * zelleB - rand else 0
        val zh = if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) info.targetCellHeight * zelleH - rand else 0
        return max(zb, minB) to max(zh, minH)
    }

    // Echte Ansicht aufblasen, auf die Groesse bringen und mit abgerundeten Ecken in ein Bitmap zeichnen.
    private fun zeichne(
        ctx: android.content.Context, klasse: Class<*>, daten: de.troetenrepublik.app.data.WidgetDaten?, bDp: Int, hDp: Int, dichte: Float,
    ): Bitmap {
        var ergebnis: Bitmap? = null
        var fehler: Throwable? = null
        inst.runOnMainSync {
            try {
                val bPx = (bDp * dichte).roundToInt()
                val hPx = (hDp * dichte).roundToInt()
                val rv = Widgets.vorschauAnsicht(ctx, klasse, daten, bDp, hDp) ?: error("keine Ansicht")
                val wurzel = FrameLayout(ctx)
                val v = rv.apply(ctx, wurzel)
                val liste = v.findViewById<ListView>(R.id.widget_liste)
                if (liste != null) {
                    val zeilen = Widgets.vorschauZeilen(ctx, klasse, daten)
                    liste.adapter = object : BaseAdapter() {
                        override fun getCount() = zeilen.size
                        override fun getItem(position: Int) = zeilen[position]
                        override fun getItemId(position: Int) = position.toLong()
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = zeilen[position].apply(ctx, parent)
                    }
                }
                v.measure(View.MeasureSpec.makeMeasureSpec(bPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
                v.layout(0, 0, bPx, hPx)
                val roh = Bitmap.createBitmap(bPx, hPx, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(roh))
                // Ecken glatt abrunden (ein Clip auf der Leinwand hat keine Kantenglaettung)
                val aus = Bitmap.createBitmap(bPx, hPx, Bitmap.Config.ARGB_8888)
                val farbe = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(roh, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
                val r = radiusDp * dichte
                Canvas(aus).drawRoundRect(RectF(0f, 0f, bPx.toFloat(), hPx.toFloat()), r, r, farbe)
                roh.recycle()
                ergebnis = aus
            } catch (e: Throwable) {
                fehler = e
            }
        }
        fehler?.let { throw it }
        return ergebnis!!
    }
}
