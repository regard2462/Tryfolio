package de.troetenrepublik.app.plattform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.troetenrepublik.app.HeatmapQuelle
import de.troetenrepublik.app.MainActivity
import de.troetenrepublik.app.R
import de.troetenrepublik.app.TroeteApp
import de.troetenrepublik.app.data.WidgetDaten
import de.troetenrepublik.app.data.WidgetHeatmap
import de.troetenrepublik.app.data.WidgetKarte
import de.troetenrepublik.app.data.WidgetKarten
import de.troetenrepublik.app.data.WidgetKartenBau
import de.troetenrepublik.app.data.WidgetNachricht
import de.troetenrepublik.app.data.WidgetWert
import de.troetenrepublik.app.data.WidgetSpalten
import de.troetenrepublik.app.data.WidgetGrafik
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.engine.GlobusAnsicht
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import de.troetenrepublik.app.engine.GlobusLesen
import de.troetenrepublik.app.engine.GlobusModus
import de.troetenrepublik.app.engine.Erscheinung
import de.troetenrepublik.app.engine.GlobusPalette
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.WidgetWahl
import de.troetenrepublik.app.engine.Treemap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

// Widgets auf dem Startbildschirm. Die App legt den fertig formatierten Inhalt ab, die Widgets zeigen ihn nur an
// und richten sich nach ihrer Groesse. WidgetPruefung holt etwa alle 30 Minuten neue Kurse, solange Widgets da sind.
// Seit 5.4 hat jedes Widget eigene Einstellungen (Grund, Schrift, Betrag oder Prozent, Zeitraum, Liste), und die
// Listen-Widgets (Werte, Märkte, News) lassen sich rollen.
object Widgets {
    val verfuegbar: Boolean = true

    // Extra im Intent an MainActivity: die Seite, die ein Tipp oeffnen soll (siehe WidgetZiel).
    const val ZIEL = "de.troetenrepublik.app.ZIEL"

    // Quelle des Widgets „Werte“: Watchlist (Vorgabe), Depot oder eine eigene Liste.
    const val QUELLE_WATCHLIST = "watchlist"
    const val QUELLE_DEPOT = "depot"
    const val QUELLE_LISTE = "liste"

    const val ART_DEPOT = "depot"
    const val ART_WERTE = "werte"
    const val ART_EINZEL = "einzelwert"
    const val ART_MOVER = "mover"
    const val ART_DIVIDENDEN = "dividenden"
    const val ART_SCHNELL = "schnell"
    const val ART_MAERKTE = "maerkte"
    const val ART_STIMMUNG = "stimmung"
    const val ART_NEWS = "news"
    const val ART_HEATMAP = "heatmap"
    const val ART_GLOBUS = "globus"

    // Kategorie-Widgets (0.73), Namen wie WidgetKarten.
    const val ART_AUSBLICK = WidgetKarten.AUSBLICK
    const val ART_BEWEGUNGEN = WidgetKarten.BEWEGUNGEN
    const val ART_TRENDS = WidgetKarten.TRENDS
    const val ART_DURCHBRUECHE = WidgetKarten.DURCHBRUECHE
    const val ART_AKTIENFINDER = WidgetKarten.AKTIENFINDER
    const val ART_COPYTRADING = WidgetKarten.COPYTRADING
    const val ART_PROGNOSE = WidgetKarten.PROGNOSE
    const val ART_ORDERS = WidgetKarten.ORDERS
    const val ART_ANALYTICS = WidgetKarten.ANALYTICS
    const val ART_WISSEN = WidgetKarten.WISSEN

    // Seit 0.77: Lage, Fruehwarnzeichen, Marktphase, Blasen-Gefahr, Termine und die Werkzeuge des Depots.
    const val ART_LAGE = WidgetKarten.LAGE
    const val ART_FRUEHWARNUNG = WidgetKarten.FRUEHWARNUNG
    const val ART_MARKTPHASE = WidgetKarten.MARKTPHASE
    const val ART_BLASEN = WidgetKarten.BLASEN
    const val ART_TERMINE = WidgetKarten.TERMINE
    const val ART_PERFORMANCE = WidgetKarten.PERFORMANCE
    const val ART_DEPOTCHECK = WidgetKarten.DEPOTCHECK
    const val ART_STREUUNG = WidgetKarten.STREUUNG
    const val ART_UMSCHICHTEN = WidgetKarten.UMSCHICHTEN
    const val ART_CRASHTEST = WidgetKarten.CRASHTEST

    // Seit 0.78: Themenkoerbe, Beitrag zum Index, Sektoren, Kongress-Flow, Stillhalter, Signale und Volatilitaet eines Werts.
    const val ART_THEMEN = WidgetKarten.THEMEN
    const val ART_INDEXBEWEGER = WidgetKarten.INDEXBEWEGER
    const val ART_SEKTOREN = WidgetKarten.SEKTOREN
    const val ART_KONGRESSFLOW = WidgetKarten.KONGRESSFLOW
    const val ART_STILLHALTER = WidgetKarten.STILLHALTER
    const val ART_SIGNALE = WidgetKarten.SIGNALE
    const val ART_VOLATILITAET = WidgetKarten.VOLATILITAET

    // Seit 0.77 (AD8): Meistgekauft aus dem 13F-Gesamtbild und die Haus-Rangliste der Analysten.
    const val ART_MEISTGEKAUFT = WidgetKarten.MEISTGEKAUFT
    const val ART_HAEUSER = WidgetKarten.HAEUSER

    // Seit 0.77: Konvergenz, seltene Insiderkaeufe, Insider-Simulator, Vorschlagsdepot, Branche, Warnzeichen, Praesidialdokumente.
    const val ART_KONVERGENZ = WidgetKarten.KONVERGENZ
    const val ART_INSIDERSELTEN = WidgetKarten.INSIDERSELTEN
    const val ART_INSIDERSIM = WidgetKarten.INSIDERSIM
    const val ART_VORSCHLAG = WidgetKarten.VORSCHLAG
    const val ART_BRANCHE = WidgetKarten.BRANCHE
    const val ART_WARNZEICHEN = WidgetKarten.WARNZEICHEN
    const val ART_PRAESIDIAL = WidgetKarten.PRAESIDIAL

    // Grund des Widgets: deckend, durchscheinend oder ohne.
    const val GRUND_VOLL = "voll"
    const val GRUND_HALB = "halb"
    const val GRUND_LEER = "leer"

    // Aktion fuer „jetzt neu laden“ beim Tipp auf die Standzeile.
    const val NEU_LADEN = "de.troetenrepublik.app.WIDGETS_NEU_LADEN"

    // Aktion fuer den Tipp auf Titel oder Wert: zwischen Betrag und Prozent umschalten.
    const val UMSCHALTEN = "de.troetenrepublik.app.WIDGET_UMSCHALTEN"

    // Aktion fuer den Tipp auf die linke oder rechte Seite des Globus: um 30 Grad drehen (EXTRA_RICHTUNG -1 West, +1 Ost).
    const val GLOBUS_DREHEN = "de.troetenrepublik.app.WIDGET_GLOBUS_DREHEN"
    const val EXTRA_RICHTUNG = "de.troetenrepublik.app.RICHTUNG"

    private const val ABLAGE = "widgets"
    private const val SCHLUESSEL = "daten"
    private val json = Json { ignoreUnknownKeys = true }

    private val HOCH = 0xFF02CA50.toInt()
    private val RUNTER = 0xFFFF3D47.toInt()

    private val KLASSEN: Map<Class<*>, String> = linkedMapOf(
        DepotWidget::class.java to ART_DEPOT, WerteWidget::class.java to ART_WERTE, EinzelwertWidget::class.java to ART_EINZEL,
        MoverWidget::class.java to ART_MOVER, DividendenWidget::class.java to ART_DIVIDENDEN, SchnellWidget::class.java to ART_SCHNELL,
        MaerkteWidget::class.java to ART_MAERKTE, StimmungWidget::class.java to ART_STIMMUNG, NewsWidget::class.java to ART_NEWS,
        HeatmapWidget::class.java to ART_HEATMAP, GlobusWidget::class.java to ART_GLOBUS,
        AusblickWidget::class.java to ART_AUSBLICK, BewegungenWidget::class.java to ART_BEWEGUNGEN, TrendsWidget::class.java to ART_TRENDS,
        DurchbruecheWidget::class.java to ART_DURCHBRUECHE, AktienfinderWidget::class.java to ART_AKTIENFINDER, CopytradingWidget::class.java to ART_COPYTRADING,
        PrognoseWidget::class.java to ART_PROGNOSE, OrdersWidget::class.java to ART_ORDERS, AnalyticsWidget::class.java to ART_ANALYTICS,
        WissenWidget::class.java to ART_WISSEN,
        LageWidget::class.java to ART_LAGE, FruehwarnungWidget::class.java to ART_FRUEHWARNUNG, MarktphaseWidget::class.java to ART_MARKTPHASE,
        BlasenWidget::class.java to ART_BLASEN, TermineWidget::class.java to ART_TERMINE, PerformanceWidget::class.java to ART_PERFORMANCE,
        DepotcheckWidget::class.java to ART_DEPOTCHECK, StreuungWidget::class.java to ART_STREUUNG, UmschichtenWidget::class.java to ART_UMSCHICHTEN,
        CrashtestWidget::class.java to ART_CRASHTEST,
        ThemenWidget::class.java to ART_THEMEN, IndexBewegerWidget::class.java to ART_INDEXBEWEGER, SektorenWidget::class.java to ART_SEKTOREN,
        KongressFlowWidget::class.java to ART_KONGRESSFLOW, StillhalterWidget::class.java to ART_STILLHALTER,
        SignaleWidget::class.java to ART_SIGNALE, VolatilitaetWidget::class.java to ART_VOLATILITAET,
        MeistgekauftWidget::class.java to ART_MEISTGEKAUFT, HaeuserWidget::class.java to ART_HAEUSER,
        KonvergenzWidget::class.java to ART_KONVERGENZ, InsiderSeltenWidget::class.java to ART_INSIDERSELTEN, InsiderSimulatorWidget::class.java to ART_INSIDERSIM,
        VorschlagsdepotWidget::class.java to ART_VORSCHLAG, BrancheWidget::class.java to ART_BRANCHE, WarnzeichenWidget::class.java to ART_WARNZEICHEN,
        PraesidialWidget::class.java to ART_PRAESIDIAL,
    )

    // Widgets mit rollbarer Liste (ListView ueber WidgetListenDienst).
    private val LISTEN = setOf(ART_WERTE, ART_MAERKTE, ART_NEWS)

    private fun ablage(c: Context) = c.getSharedPreferences(ABLAGE, Context.MODE_PRIVATE)

    // Unveraenderter Inhalt zeichnet nichts neu.
    fun aktualisieren(daten: WidgetDaten) {
        val c = Plattform.kontext
        if (!vorhanden(c)) return
        val text = json.encodeToString(WidgetDaten.serializer(), daten)
        if (ablage(c).getString(SCHLUESSEL, null) == text) return
        ablage(c).edit().putString(SCHLUESSEL, text).apply()
        zeichnen(c)
    }

    fun laden(c: Context): WidgetDaten? =
        ablage(c).getString(SCHLUESSEL, null)?.let { runCatching { json.decodeFromString(WidgetDaten.serializer(), it) }.getOrNull() }

    private fun ids(c: Context, klasse: Class<*>): IntArray =
        if (vorschau) intArrayOf(vorschauId(klasse)) else runCatching { AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, klasse)) }.getOrNull() ?: IntArray(0)

    private fun ids(c: Context, art: String): List<Int> = KLASSEN.filterValues { it == art }.keys.flatMap { ids(c, it).toList() }

    fun vorhanden(c: Context): Boolean = KLASSEN.keys.any { ids(c, it).isNotEmpty() }

    fun vorhanden(): Boolean = vorhanden(Plattform.kontext)

    // Arten auf dem Startbildschirm, damit die App nur laedt, was gebraucht wird.
    fun arten(): Set<String> {
        val c = Plattform.kontext
        return KLASSEN.filter { (klasse, _) -> ids(c, klasse).isNotEmpty() }.values.toSet()
    }

    // Art eines Widgets aus seiner Kennung; null, wenn der Startbildschirm es nicht kennt.
    fun art(c: Context, id: Int): String? {
        vorschauArt(id)?.let { return it }
        val name = runCatching { AppWidgetManager.getInstance(c).getAppWidgetInfo(id)?.provider?.className }.getOrNull() ?: return null
        return KLASSEN.entries.firstOrNull { it.key.name == name }?.value
    }

    private fun klasse(art: String): Class<*>? = KLASSEN.entries.firstOrNull { it.value == art }?.key

    // ------------------------------------------------------------ Einstellungen je Widget

    private val EINSTELLUNGEN = listOf("quelle", "einzel", "grund", "schrift", "zeitraum", "nurProzent", "betrag", "liste", "name", "sortiert", "barometer", "news", "schnell", "heatmapQuelle", "heatmapZeit", "globusModus", "globusMitte", "globusDreh", "untertitel", "variante")

    private fun lesen(c: Context, id: Int, name: String): String? {
        if (vorschauArt(id) != null) return vorschauVorgabe(id, name)
        uebernehmen(c, id)
        return ablage(c).getString("${name}_$id", null)
    }

    private fun schreiben(c: Context, id: Int, name: String, wert: String?) {
        val e = ablage(c).edit()
        if (wert.isNullOrEmpty()) e.remove("${name}_$id") else e.putString("${name}_$id", wert)
        e.apply()
    }

    fun quelle(c: Context, id: Int): String = lesen(c, id, "quelle") ?: QUELLE_WATCHLIST

    // Untertitel (Fusszeile mit Name und Stand) seit 0.76 standardmaessig aus
    fun untertitel(c: Context, id: Int): Boolean = lesen(c, id, "untertitel") == "1"
    fun untertitelSetzen(c: Context, id: Int, an: Boolean) = schreiben(c, id, "untertitel", if (an) "1" else null)

    // Ohne Untertitel bleibt nur die Quelle, die deren Bedingungen verlangen (alternative.me beim Krypto-Barometer)
    private fun pflichtQuelle(c: Context, id: Int, art: String): String? {
        if (art != ART_STIMMUNG) return null
        val wahl = barometer(c, id)
        return if (wahl == WidgetDaten.STIMMUNG_KRYPTO || wahl == WidgetDaten.STIMMUNG_ALLE) de.troetenrepublik.app.i18n.S.sbAnbieter(WidgetDaten.STIMMUNG_KRYPTO) else null
    }
    fun quelleSetzen(c: Context, id: Int, quelle: String) = schreiben(c, id, "quelle", quelle)

    fun einzel(c: Context, id: Int): String? = lesen(c, id, "einzel")

    // Unterseite eines Widgets (Reiter, Markt, Terminart; WidgetVorgabe.VARIANTE), nur wenn die Art sie kennt.
    fun variante(c: Context, id: Int): String? = WidgetVorgabe.gueltig(art(c, id).orEmpty(), lesen(c, id, "variante"))
    fun varianteSetzen(c: Context, id: Int, variante: String?) = schreiben(c, id, "variante", variante)

    // Schluessel der Karten aller Widgets auf dem Startbildschirm (Art, bei einer Variante mit ihr), damit die App nur rechnet,
    // was gebraucht wird.
    fun kartenSchluessel(): Set<String> {
        val c = Plattform.kontext
        val aus = HashSet<String>()
        for ((klasse, art) in KLASSEN) if (art in WidgetKarten.ARTEN) for (id in ids(c, klasse)) aus += WidgetVorgabe.kartenSchluessel(art, variante(c, id))
        return aus
    }

    // Widget aus der App anlegen (langes Druecken auf einen Wert oder eine Kategorie): der Startbildschirm fragt nach, das
    // neue Widget uebernimmt die Einstellungen beim ersten Zugriff (uebernehmen). Nur Widgets, die es beim Anfragen noch
    // nicht gab, uebernehmen sie. Ohne Unterstuetzung des Startbildschirms false.
    private fun anheften(klasse: Class<*>, einstellung: Map<String, String>): Boolean {
        val c = Plattform.kontext
        val m = AppWidgetManager.getInstance(c) ?: return false
        if (!m.isRequestPinAppWidgetSupported) return false
        ablage(c).edit()
            .putString("anheften_klasse", klasse.name)
            .putString("anheften_einst", einstellung.entries.joinToString("\n") { it.key + "=" + it.value })
            .putLong("anheften_zeit", System.currentTimeMillis())
            .putString("anheften_bekannt", ids(c, klasse).joinToString(","))
            .apply()
        val ok = runCatching { m.requestPinAppWidget(ComponentName(c, klasse), null, null) }.getOrDefault(false)
        if (!ok) ablage(c).edit().remove("anheften_klasse").remove("anheften_einst").apply()
        return ok
    }

    fun einzelAnheften(symbol: String): Boolean = anheften(EinzelwertWidget::class.java, mapOf("einzel" to symbol))

    // Widget zu einer Kategorie der App anlegen (WidgetKarten.ANLEGBAR); bestehende Widgets mit der passenden Wahl.
    // einstellung ueberschreibt die Voreinstellung der Art (Schluessel wie EINSTELLUNGEN, etwa die Variante).
    fun anheften(art: String, einstellung: Map<String, String> = emptyMap()): Boolean {
        val vorgabe: Pair<Class<*>, Map<String, String>> = when (art) {
            WidgetKarten.WERTE -> WerteWidget::class.java to mapOf("quelle" to QUELLE_WATCHLIST)
            WidgetKarten.NEWS -> NewsWidget::class.java to mapOf("news" to WidgetDaten.NEWS_WICHTIG)
            WidgetKarten.MAERKTE -> MaerkteWidget::class.java to emptyMap()
            WidgetKarten.HEATMAP -> HeatmapWidget::class.java to mapOf("heatmapQuelle" to HeatmapQuelle.MARKT)
            WidgetKarten.GLOBUS -> GlobusWidget::class.java to emptyMap()
            WidgetKarten.STIMMUNG -> StimmungWidget::class.java to emptyMap()
            else -> (klasse(art) ?: return false) to emptyMap()
        }
        val erlaubt = einstellung.filter { (k, _) -> k in EINSTELLUNGEN }
        return anheften(vorgabe.first, vorgabe.second + erlaubt)
    }

    // Einstellungen eines eben angelegten Widgets aus der Anfrage der App uebernehmen (hoechstens drei Minuten nach der Anfrage).
    private fun uebernehmen(c: Context, id: Int) {
        val a = ablage(c)
        val klasse = a.getString("anheften_klasse", null) ?: return
        if (System.currentTimeMillis() - a.getLong("anheften_zeit", 0L) > 180_000L) {
            a.edit().remove("anheften_klasse").remove("anheften_einst").apply()
            return
        }
        if (id.toString() in a.getString("anheften_bekannt", "").orEmpty().split(',')) return
        val name = runCatching { AppWidgetManager.getInstance(c).getAppWidgetInfo(id)?.provider?.className }.getOrNull() ?: return
        if (name != klasse) return
        val einst = a.getString("anheften_einst", "").orEmpty()
        a.edit().remove("anheften_klasse").remove("anheften_einst").apply()
        einst.lines().filter { '=' in it }.forEach { z -> schreiben(c, id, z.substringBefore('='), z.substringAfter('=')) }
        jetztLaden(c)
    }
    fun einzelSetzen(c: Context, id: Int, symbol: String) = schreiben(c, id, "einzel", symbol)

    fun grund(c: Context, id: Int): String = lesen(c, id, "grund") ?: GRUND_VOLL
    fun grundSetzen(c: Context, id: Int, grund: String) = schreiben(c, id, "grund", grund)

    // Dunkle Schrift (auf hellem Grund) statt heller.
    fun dunkleSchrift(c: Context, id: Int): Boolean = lesen(c, id, "schrift") == "dunkel"
    fun dunkleSchriftSetzen(c: Context, id: Int, an: Boolean) = schreiben(c, id, "schrift", if (an) "dunkel" else null)

    // Zeitraum des Depot-Widgets (WidgetDaten.ZEIT_*).
    fun zeitraum(c: Context, id: Int): String = lesen(c, id, "zeitraum") ?: WidgetDaten.ZEIT_HEUTE
    fun zeitraumSetzen(c: Context, id: Int, zeit: String) = schreiben(c, id, "zeitraum", zeit)

    // Depot und Top & Flop ohne Betraege, nur Prozent.
    fun nurProzent(c: Context, id: Int): Boolean = lesen(c, id, "nurProzent") == "1"
    fun nurProzentSetzen(c: Context, id: Int, an: Boolean) = schreiben(c, id, "nurProzent", if (an) "1" else null)

    // Listen und Einzelwert: Tagesaenderung als Betrag statt in Prozent.
    fun betrag(c: Context, id: Int): Boolean = lesen(c, id, "betrag") == "1"
    fun betragSetzen(c: Context, id: Int, an: Boolean) = schreiben(c, id, "betrag", if (an) "1" else null)

    // Eigene Liste des Widgets „Werte“ (Symbole in Reihenfolge) und ihr Name.
    fun liste(c: Context, id: Int): List<String> = lesen(c, id, "liste")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    fun listeSetzen(c: Context, id: Int, symbole: List<String>) = schreiben(c, id, "liste", symbole.distinct().joinToString(","))
    fun name(c: Context, id: Int): String = lesen(c, id, "name").orEmpty()
    fun nameSetzen(c: Context, id: Int, name: String) = schreiben(c, id, "name", name.trim().take(40))

    // Liste nach Tagesaenderung ordnen.
    fun sortiert(c: Context, id: Int): Boolean = lesen(c, id, "sortiert") == "1"
    fun sortiertSetzen(c: Context, id: Int, an: Boolean) = schreiben(c, id, "sortiert", if (an) "1" else null)

    // Stimmungsbarometer des Widgets (WidgetDaten.STIMMUNG_*).
    // Ein neues Widget zeigt die Gesamtstimmung (0.76).
    fun barometer(c: Context, id: Int): String = WidgetWahl.stimmung(lesen(c, id, "barometer"))

    // Laeuft einmal beim ersten Prozessstart der neuen Fassung (TroeteApp.onCreate), also bevor ein neu angelegtes
    // Widget gezeichnet wird. 0.77 stellt Widgets, die 0.76 fest auf Aktien USA gesetzt hat, auf die Gesamtstimmung zurueck.
    fun barometerAltbestand(c: Context) {
        val a = ablage(c)
        if (a.getBoolean("barometer077", false)) return
        val e = a.edit().putBoolean("barometer077", true).putBoolean("barometer076", true)
        for (id in ids(c, StimmungWidget::class.java)) WidgetWahl.uebernahme077(a.getString("barometer_$id", null))?.let { e.putString("barometer_$id", it) }
        e.apply()
    }
    fun barometerSetzen(c: Context, id: Int, schluessel: String) = schreiben(c, id, "barometer", schluessel)

    // Einfaerbung (GlobusModus.name) und Blickmitte (MITTEN) des Widgets „Globus“ (0.69).
    fun globusModus(c: Context, id: Int): GlobusModus =
        lesen(c, id, "globusModus")?.let { n -> GlobusModus.entries.firstOrNull { it.name == n } } ?: GlobusModus.BOERSE
    fun globusModusSetzen(c: Context, id: Int, modus: GlobusModus) = schreiben(c, id, "globusModus", modus.name)
    val MITTEN = listOf("europa" to (12.0 to 42.0), "amerika" to (-80.0 to 15.0), "asien" to (95.0 to 30.0))
    fun globusMitte(c: Context, id: Int): String = lesen(c, id, "globusMitte")?.takeIf { k -> MITTEN.any { it.first == k } } ?: "europa"
    fun globusMitteSetzen(c: Context, id: Int, mitte: String) {
        schreiben(c, id, "globusMitte", mitte)
        schreiben(c, id, "globusDreh", null)
    }

    // Drehung des Globus in Grad nach Tipps auf die linke und rechte Seite (0.76), zur Blickmitte der Wahl addiert.
    fun globusDreh(c: Context, id: Int): Int = WidgetWahl.dreh(lesen(c, id, "globusDreh"))
    fun globusDrehSetzen(c: Context, id: Int, grad: Int) = schreiben(c, id, "globusDreh", grad.takeIf { it != 0 }?.toString())
    fun globusDrehen(c: Context, id: Int, richtung: Int) {
        if (art(c, id) != ART_GLOBUS) return
        globusDrehSetzen(c, id, WidgetWahl.weiterdrehen(globusDreh(c, id), richtung))
        zeichnen(c, id)
    }

    // Einfaerbungen aller Globus-Widgets, damit die App nur diese Werte laedt.
    fun globusModi(): Set<String> {
        val c = Plattform.kontext
        return ids(c, GlobusWidget::class.java).mapTo(HashSet()) { globusModus(c, it).name }
    }

    // Inhalt des News-Widgets (WidgetDaten.NEWS_*), seit 5.94.
    fun newsArt(c: Context, id: Int): String = lesen(c, id, "news") ?: WidgetDaten.NEWS_DEPOT
    fun newsArtSetzen(c: Context, id: Int, art: String) = schreiben(c, id, "news", art)

    // Quelle und Zeitraum des Heatmap-Widgets (0.68): HeatmapQuelle oder Index-Schluessel, Zeitraum als Index in HEATMAP_ZEITRAEUME.
    fun heatmapQuelle(c: Context, id: Int): String = lesen(c, id, "heatmapQuelle") ?: HeatmapQuelle.MARKT
    fun heatmapQuelleSetzen(c: Context, id: Int, quelle: String) = schreiben(c, id, "heatmapQuelle", quelle)
    fun heatmapZeitraum(c: Context, id: Int): Int = lesen(c, id, "heatmapZeit")?.toIntOrNull()?.coerceIn(0, 5) ?: 0
    fun heatmapZeitraumSetzen(c: Context, id: Int, zeitraum: Int) = schreiben(c, id, "heatmapZeit", zeitraum.coerceIn(0, 5).toString())

    // Quelle und Zeitraum aller Heatmap-Widgets, damit die App nur diese Heatmaps laedt.
    fun heatmapAuswahl(): List<Pair<String, Int>> {
        val c = Plattform.kontext
        return ids(c, HeatmapWidget::class.java).map { heatmapQuelle(c, it) to heatmapZeitraum(c, it) }.distinct()
    }

    // Gewaehlte Inhalte aller News-Widgets; ausser NEWS_DEPOT laedt die App dafuer die Feeds (0.68).
    fun newsArten(): Set<String> {
        val c = Plattform.kontext
        return ids(c, NewsWidget::class.java).mapTo(HashSet()) { newsArt(c, it) }
    }

    // Titel, Hinweis bei leerer Liste und Schlagzeilen eines News-Widgets je Inhalt (0.68)
    private fun newsTitel(d: WidgetDaten?, art: String): String? = when (art) {
        WidgetDaten.NEWS_DEPOT -> d?.titelNews
        WidgetDaten.NEWS_WICHTIG -> d?.titelNewsWichtig
        else -> d?.newsListenTitel?.get(art)
    }?.ifBlank { null }

    private fun newsHinweis(d: WidgetDaten, art: String): String = when (art) {
        WidgetDaten.NEWS_DEPOT -> d.newsHinweis
        WidgetDaten.NEWS_WICHTIG -> d.newsWichtigHinweis
        else -> if (art in d.newsListen) d.newsListenKeine else d.newsListenLaedt
    }

    private fun newsListe(d: WidgetDaten, art: String): List<WidgetNachricht> = when (art) {
        WidgetDaten.NEWS_DEPOT -> d.news
        WidgetDaten.NEWS_WICHTIG -> d.newsWichtig
        else -> d.newsListen[art].orEmpty()
    }

    // Ob ein Stimmungs-Widget dieses Barometer zeigt (5.95).
    fun barometerGewaehlt(schluessel: String): Boolean {
        val c = Plattform.kontext
        return ids(c, StimmungWidget::class.java).any { barometer(c, it).let { b -> b == schluessel || b == WidgetDaten.STIMMUNG_ALLE } }
    }

    // Vier Ziele des Schnellzugriffs (aus WidgetDaten.SCHNELL_AUSWAHL); sonst vier feste Ziele.
    fun schnellZiele(c: Context, id: Int): List<String> {
        val l = lesen(c, id, "schnell")?.split(',')?.map { it.trim() }?.filter { it in WidgetDaten.SCHNELL_AUSWAHL }?.distinct().orEmpty()
        return (l + WidgetDaten.SCHNELL_ZIELE.filter { it !in l }).take(4)
    }
    fun schnellZieleSetzen(c: Context, id: Int, ziele: List<String>) = schreiben(c, id, "schnell", ziele.distinct().take(4).joinToString(","))

    // Die in Einzelwert-Widgets gewaehlten Symbole (fuer Kurse und Tagesverlaeufe).
    fun einzelSymbole(): List<String> {
        val c = Plattform.kontext
        return ids(c, EinzelwertWidget::class.java).toList().mapNotNull { einzel(c, it) }.distinct()
    }

    // Symbole aller eigenen Listen (fuer Kurse).
    fun listenSymbole(): List<String> {
        val c = Plattform.kontext
        return ids(c, WerteWidget::class.java).filter { quelle(c, it) == QUELLE_LISTE }.flatMap { liste(c, it) }.distinct()
    }

    // Zeitraeume, die Depot-Widgets zeigen (fuer Verlaeufe).
    fun depotZeitraeume(): Set<String> {
        val c = Plattform.kontext
        return ids(c, DepotWidget::class.java).map { zeitraum(c, it) }.toSet()
    }

    // Einstellungen entfernter Widgets loeschen.
    fun vergessen(c: Context, ids: IntArray) {
        val e = ablage(c).edit()
        ids.forEach { id -> EINSTELLUNGEN.forEach { e.remove("${it}_$id") } }
        e.apply()
    }

    // ------------------------------------------------------------ Vorschaubilder (Test)

    // Im Vorschaumodus hat jede Art genau ein gedachtes Widget (eigene Kennung, Vorgaben statt Einstellungen), damit die App
    // alle Inhalte laedt und jede Ansicht ohne Startbildschirm entsteht. Nur fuer den Test der Vorschaubilder.
    @Volatile private var vorschau = false
    private const val VORSCHAU_BASIS = -7000

    fun vorschauModus(an: Boolean) { vorschau = an }

    // Breite (dp) des Vorschaubilds, das gerade entsteht; die Zeilen der Listen richten ihre Spalten danach aus.
    @Volatile private var vorschauBreite = 0

    private fun vorschauId(klasse: Class<*>): Int = VORSCHAU_BASIS - KLASSEN.keys.indexOf(klasse)

    private fun vorschauArt(id: Int): String? = if (vorschau && id <= VORSCHAU_BASIS) KLASSEN.values.elementAtOrNull(VORSCHAU_BASIS - id) else null

    // Vorgaben des gedachten Widgets: News „wichtig“ wie beim Anlegen aus der App, bei Signalen, Volatilitaet und Warnzeichen ein Wert.
    private fun vorschauVorgabe(id: Int, name: String): String? = when (name) {
        "variante" -> "AAPL".takeIf { vorschauArt(id) in setOf(ART_SIGNALE, ART_VOLATILITAET, ART_WARNZEICHEN) }
        "news" -> WidgetDaten.NEWS_WICHTIG
        else -> null
    }

    fun vorschauKlassen(): List<Class<*>> = KLASSEN.keys.toList()

    // Ansicht der Art in der Groesse breite x hoehe (dp) mit den Vorgaben; vorher vorschauModus(true).
    fun vorschauAnsicht(c: Context, klasse: Class<*>, d: WidgetDaten?, breite: Int, hoehe: Int): RemoteViews? {
        val art = KLASSEN[klasse] ?: return null
        vorschauBreite = breite
        return ansicht(c, art, vorschauId(klasse), d, breite, hoehe)
    }

    // Zeilen der rollbaren Listen (Werte, Maerkte, News) fuer das Vorschaubild.
    fun vorschauZeilen(c: Context, klasse: Class<*>, d: WidgetDaten?): List<RemoteViews> {
        val art = KLASSEN[klasse] ?: return emptyList()
        val id = vorschauId(klasse)
        return listenInhalt(c, id, art, d).map { zeilenAnsicht(c, id, it) }
    }

    // ------------------------------------------------------------ Grund und Schrift

    private class Stil(val grund: Int, val schrift: Int, val schrift2: Int, val schrift3: Int, val kurs: Int, val trenner: Int, val hoch: Int, val runter: Int)

    private fun stil(c: Context, id: Int): Stil {
        val dunkel = dunkleSchrift(c, id)
        val grund = when (grund(c, id)) {
            GRUND_LEER -> R.drawable.widget_grund_leer
            GRUND_HALB -> if (dunkel) R.drawable.widget_grund_hell_halb else R.drawable.widget_grund_dunkel_halb
            else -> if (dunkel) R.drawable.widget_grund_hell else R.drawable.widget_hintergrund
        }
        return if (dunkel) {
            Stil(grund, 0xFF141416.toInt(), 0xB3141416.toInt(), 0x8C141416.toInt(), 0xD9141416.toInt(), 0x33141416, 0xFF1B873F.toInt(), 0xFFD92633.toInt())
        } else {
            Stil(grund, 0xFFFFFFFF.toInt(), 0xB3FFFFFF.toInt(), 0x80FFFFFF.toInt(), 0xD9FFFFFF.toInt(), 0x33FFFFFF, HOCH, RUNTER)
        }
    }

    private fun grundSetzen(v: RemoteViews, s: Stil) = v.setInt(R.id.widget_wurzel, "setBackgroundResource", s.grund)

    private fun farbe(v: RemoteViews, farbe: Int, vararg ids: Int) = ids.forEach { v.setTextColor(it, farbe) }

    // ------------------------------------------------------------ Zeichnen

    fun zeichnen(c: Context) {
        if (vorschau) return
        val m = AppWidgetManager.getInstance(c) ?: return
        val d = laden(c)
        for ((klasse, art) in KLASSEN) for (id in ids(c, klasse)) {
            runCatching { m.updateAppWidget(id, ansicht(c, m, art, id, d)) }
            if (art in LISTEN) runCatching { listeNeu(m, id) }
        }
    }

    // Nach geaenderter Groesse oder Einstellung.
    fun zeichnen(c: Context, id: Int) {
        if (vorschau) return
        val m = AppWidgetManager.getInstance(c) ?: return
        val art = art(c, id) ?: return
        runCatching { m.updateAppWidget(id, ansicht(c, m, art, id, laden(c))) }
        if (art in LISTEN) runCatching { listeNeu(m, id) }
    }

    @Suppress("DEPRECATION")
    private fun listeNeu(m: AppWidgetManager, id: Int) = m.notifyAppWidgetViewDataChanged(id, R.id.widget_liste)

    // Groesse in dp (Hochformat: kleinste Breite, groesste Hoehe); 0, wenn der Startbildschirm nichts meldet.
    private fun groesse(m: AppWidgetManager, id: Int): Pair<Int, Int> {
        val o: Bundle = runCatching { m.getAppWidgetOptions(id) }.getOrNull() ?: return 0 to 0
        // Ab Android 12 meldet der Startbildschirm die echten Groessen je Ausrichtung; sonst verzerrt fitXY Bilder wie den Globus
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val liste = runCatching { o.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES) }.getOrNull()
            if (!liste.isNullOrEmpty()) {
                val hoch = android.content.res.Resources.getSystem().configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val g = if (hoch) liste.maxByOrNull { it.height / it.width.coerceAtLeast(1f) } else liste.maxByOrNull { it.width / it.height.coerceAtLeast(1f) }
                if (g != null && g.width > 0f && g.height > 0f) return g.width.toInt() to g.height.toInt()
            }
        }
        return o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) to o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    }

    private fun ansicht(c: Context, m: AppWidgetManager, art: String, id: Int, d: WidgetDaten?): RemoteViews {
        val (b, h) = groesse(m, id)
        return ansicht(c, art, id, d, if (b > 0) b else 180, if (h > 0) h else 110)
    }

    private fun ansicht(c: Context, art: String, id: Int, d: WidgetDaten?, breite: Int, hoehe: Int): RemoteViews {
        val s = stil(c, id)
        return when (art) {
            ART_DEPOT -> depot(c, d, id, s, breite, hoehe)
            ART_WERTE, ART_MAERKTE, ART_NEWS -> liste(c, d, art, id, s)
            ART_EINZEL -> einzelwert(c, d, id, s, breite, hoehe)
            ART_MOVER -> mover(c, d, id, s, breite, hoehe)
            ART_DIVIDENDEN -> dividenden(c, d, s, hoehe)
            ART_STIMMUNG -> stimmung(c, d, id, s, breite, hoehe)
            ART_HEATMAP -> heatmap(c, d, id, s, breite, hoehe)
            ART_GLOBUS -> globus(c, d, id, s, breite, hoehe)
            in WidgetKarten.ARTEN -> karte(c, d, art, id, s, breite, hoehe)
            else -> schnell(c, d, id, s, breite, hoehe)
        }.also { v ->
            if (art != ART_SCHNELL) runCatching { v.setOnClickPendingIntent(R.id.widget_stand, neuLaden(c)) }
            if (art != ART_SCHNELL && !untertitel(c, id)) {
                val pflicht = pflichtQuelle(c, id, art)
                if (pflicht != null) v.setTextViewText(R.id.widget_stand, pflicht) else v.setViewVisibility(R.id.widget_stand, View.GONE)
            }
        }
    }

    // Tipp auf die Standzeile: Kurse sofort im Hintergrund holen, ohne die App zu oeffnen.
    private fun neuLaden(c: Context): PendingIntent {
        val i = Intent(c, DepotWidget::class.java).setAction(NEU_LADEN)
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Tipp auf Titel oder Wert: Betrag und Prozent tauschen, nur bei diesem Widget.
    private fun umschalten(c: Context, art: String, id: Int): PendingIntent {
        val i = Intent(c, klasse(art) ?: DepotWidget::class.java).setAction(UMSCHALTEN)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .setData(Uri.parse("troete-widget://umschalten/$id"))
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Tipp auf die linke (-1, nach Westen) oder rechte Seite (+1, nach Osten) des Globus: dieses Widget um 30 Grad drehen.
    private fun globusDrehung(c: Context, id: Int, richtung: Int): PendingIntent {
        val i = Intent(c, GlobusWidget::class.java).setAction(GLOBUS_DREHEN)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(EXTRA_RICHTUNG, richtung)
            .setData(Uri.parse("troete-widget://globus/$id/$richtung"))
        return PendingIntent.getBroadcast(c, id * 2 + (if (richtung < 0) 0 else 1), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Umschalten ausfuehren und das Widget neu zeichnen.
    fun umschalten(c: Context, id: Int) {
        when (art(c, id)) {
            ART_DEPOT, ART_MOVER -> nurProzentSetzen(c, id, !nurProzent(c, id))
            ART_WERTE, ART_MAERKTE, ART_EINZEL -> betragSetzen(c, id, !betrag(c, id))
            else -> return
        }
        zeichnen(c, id)
    }

    // Einmalige Pruefung sofort, zusaetzlich zur regelmaessigen.
    fun jetztLaden(c: Context) {
        if (!vorhanden(c)) return
        val auftrag = androidx.work.OneTimeWorkRequestBuilder<WidgetPruefung>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("widgets-jetzt", androidx.work.ExistingWorkPolicy.REPLACE, auftrag)
    }

    // Oeffnet die App, mit ziel gleich die passende Seite. Eigene Adresse je Ziel, damit Android die Intents trennt.
    private fun oeffnen(c: Context, ziel: String?): PendingIntent {
        val i = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (!ziel.isNullOrBlank()) {
            i.putExtra(ZIEL, ziel)
            i.data = Uri.parse("troete-widget://ziel/" + Uri.encode(ziel))
        }
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Vorlage fuer Tipps auf Listenzeilen: die Zeile ergaenzt Ziel und Adresse. Muss veraenderbar sein, sonst
    // uebernimmt Android die Angaben der Zeile nicht; das Ziel ist fest die App.
    private fun listenVorlage(c: Context): PendingIntent {
        val i = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val veraenderbar = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getActivity(c, 2, i, veraenderbar or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Angaben einer Listenzeile fuer die Vorlage.
    fun zeilenZiel(ziel: String): Intent =
        Intent().putExtra(ZIEL, ziel).setData(Uri.parse("troete-widget://ziel/" + Uri.encode(ziel)))

    private val ZEILEN = intArrayOf(R.id.zeile1, R.id.zeile2, R.id.zeile3, R.id.zeile4, R.id.zeile5, R.id.zeile6)
    private val NAMEN = intArrayOf(R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5, R.id.name6)
    private val KURSE = intArrayOf(R.id.kurs1, R.id.kurs2, R.id.kurs3, R.id.kurs4, R.id.kurs5, R.id.kurs6)
    private val PROZENT = intArrayOf(R.id.proz1, R.id.proz2, R.id.proz3, R.id.proz4, R.id.proz5, R.id.proz6)

    // Zeilen ab ab mit liste fuellen, hoechstens anzahl. Der Rest bleibt verborgen.
    private fun zeilen(
        c: Context, v: RemoteViews, s: Stil, liste: List<WidgetWert>, ab: Int, bis: Int, anzahl: Int,
        mitProzent: Boolean = true, ohneKurs: Boolean = false,
    ) {
        for (i in ab until bis) {
            val w = liste.getOrNull(i - ab)?.takeIf { i - ab < anzahl }
            if (w == null) { v.setViewVisibility(ZEILEN[i], View.GONE); continue }
            v.setViewVisibility(ZEILEN[i], View.VISIBLE)
            v.setTextViewText(NAMEN[i], w.name)
            v.setTextViewText(KURSE[i], if (ohneKurs) "" else WidgetSpalten.fest(w.kurs))
            v.setTextColor(NAMEN[i], s.schrift)
            v.setTextColor(KURSE[i], s.kurs)
            if (mitProzent) {
                v.setTextViewText(PROZENT[i], WidgetSpalten.fest(w.aenderung))
                v.setTextColor(PROZENT[i], if (w.hoch) s.hoch else s.runter)
            }
            v.setOnClickPendingIntent(ZEILEN[i], oeffnen(c, w.ziel))
        }
    }

    private fun hinweis(v: RemoteViews, s: Stil, text: String) {
        v.setViewVisibility(R.id.widget_hinweis, if (text.isBlank()) View.GONE else View.VISIBLE)
        v.setTextViewText(R.id.widget_hinweis, text)
        v.setTextColor(R.id.widget_hinweis, s.schrift2)
    }

    // Zeilen, die in hoehe dp passen: ungefaehr 22 dp je Zeile nach Titel, Stand und Rand (rest).
    private fun zeilenFuer(hoehe: Int, rest: Int, max: Int): Int = ((hoehe - rest) / 22).coerceIn(1, max)

    // Hoechstens etwa 500 x 160 Pixel (rund 300 KB), weil Android das Bild ueber eine Verbindung mit etwa 1 MB
    // weiterreicht. Das Widget streckt es auf seine Groesse.
    private fun kurve(c: Context, werte: List<Double>, farbe: Int, breiteDp: Int, hoeheDp: Int): Bitmap? {
        val v = werte.filter { it.isFinite() }
        if (v.size < 2) return null
        val dichte = c.resources.displayMetrics.density.coerceAtMost(2f)
        val b = (breiteDp * dichte).toInt().coerceIn(40, 500)
        val h = (hoeheDp * dichte).toInt().coerceIn(20, 160)
        val bild = Bitmap.createBitmap(b, h, Bitmap.Config.ARGB_8888)
        val leinwand = Canvas(bild)
        val min = v.min()
        val spanne = (v.max() - min).takeIf { it > 0 } ?: 1.0
        val rand = 2f * dichte
        val linie = Path()
        v.forEachIndexed { i, wert ->
            val x = i.toFloat() / (v.size - 1) * (b - 1)
            val y = rand + (1f - ((wert - min) / spanne).toFloat()) * (h - 2 * rand)
            if (i == 0) linie.moveTo(x, y) else linie.lineTo(x, y)
        }
        val flaeche = Path(linie).apply { lineTo((b - 1).toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close() }
        leinwand.drawPath(flaeche, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), (farbe and 0x00FFFFFF) or 0x40000000, farbe and 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        leinwand.drawPath(linie, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * dichte
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = farbe
        })
        return bild
    }

    // Kurslinie zeigen, wenn das Widget hoch genug ist (ab etwa zwei Zeilen) und ein Verlauf da ist.
    private fun kurveSetzen(c: Context, v: RemoteViews, werte: List<Double>, farbe: Int, breite: Int, hoehe: Int) {
        val bild = if (hoehe >= 100) kurve(c, werte, farbe, breite - 28, (hoehe - 96).coerceIn(24, 120)) else null
        if (bild == null) { v.setViewVisibility(R.id.widget_kurve, View.GONE); return }
        v.setImageViewBitmap(R.id.widget_kurve, bild)
        v.setViewVisibility(R.id.widget_kurve, View.VISIBLE)
    }

    // Mit App-Sperre: Schloss und Hinweis statt Betraegen (gilt fuer alle Widgets mit Werten).
    private fun gesperrt(d: WidgetDaten?): Boolean = d?.gesperrt == true

    private fun depot(c: Context, d: WidgetDaten?, id: Int, s: Stil, breite: Int, hoehe: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_depot)
        grundSetzen(v, s)
        farbe(v, s.schrift, R.id.widget_wert)
        farbe(v, s.schrift3, R.id.widget_stand)
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, null))
        v.setOnClickPendingIntent(R.id.widget_wert, umschalten(c, ART_DEPOT, id))
        // Fehlt der gewaehlte Zeitraum (Verlauf noch nicht geladen), zeigt das Widget den heutigen statt nur den Wert
        val gewaehlt = zeitraum(c, id)
        val zeit = if (d?.depotZeitraeume?.containsKey(gewaehlt) == false && d.depotZeitraeume.containsKey(WidgetDaten.ZEIT_HEUTE)) WidgetDaten.ZEIT_HEUTE else gewaehlt
        val z = d?.depotZeitraeume?.get(zeit)
        val nurProzent = nurProzent(c, id)
        val skala = c.resources.configuration.fontScale
        // Der grosse Betrag steht immer mit Waehrungszeichen und bricht nie um; passt er nicht, kommt er ohne Nachkommastellen
        val betrag = if (d == null) "" else WidgetSpalten.betragWahl(listOf(d.depotWert, d.depotWertKurz), breite, 22f, skala)
        v.setViewVisibility(R.id.widget_aenderung2, View.GONE)
        when {
            d == null -> {
                v.setTextViewText(R.id.widget_wert, "–")
                v.setTextViewText(R.id.widget_aenderung, c.getString(R.string.widget_oeffnen))
                v.setTextColor(R.id.widget_aenderung, s.schrift2)
            }
            gesperrt(d) -> {
                v.setTextViewText(R.id.widget_wert, "🔒")
                v.setTextViewText(R.id.widget_aenderung, d.hinweis)
                v.setTextColor(R.id.widget_aenderung, s.schrift2)
            }
            z == null -> {
                v.setTextViewText(R.id.widget_wert, if (nurProzent) "–" else betrag)
                v.setTextViewText(R.id.widget_aenderung, "")
            }
            nurProzent -> {
                // Nur Prozent: keine Betraege, die grosse Zahl ist die Aenderung
                v.setTextViewText(R.id.widget_wert, WidgetSpalten.fest(z.prozent))
                v.setTextColor(R.id.widget_wert, if (z.hoch) s.hoch else s.runter)
                v.setTextViewText(R.id.widget_aenderung, z.titel)
                v.setTextColor(R.id.widget_aenderung, s.schrift2)
            }
            else -> {
                v.setTextViewText(R.id.widget_wert, betrag)
                // Betrag und Prozent mit Trenner in einer Zeile, wenn beides passt, sonst untereinander; nie ein loser Trenner
                val a = WidgetSpalten.aenderung(z.betrag, z.prozent, breite, skala)
                val farbe = if (z.hoch) s.hoch else s.runter
                v.setTextViewText(R.id.widget_aenderung, a.erste)
                v.setTextColor(R.id.widget_aenderung, farbe)
                if (a.zweite.isNotEmpty()) {
                    v.setViewVisibility(R.id.widget_aenderung2, View.VISIBLE)
                    v.setTextViewText(R.id.widget_aenderung2, a.zweite)
                    v.setTextColor(R.id.widget_aenderung2, farbe)
                }
            }
        }
        val offen = d != null && !gesperrt(d) && z != null
        kurveSetzen(c, v, if (offen) z!!.kurve else emptyList(), if (z?.hoch != false) s.hoch else s.runter, breite, hoehe)
        // Der Zeitraum steht vor dem Stand, solange er nicht „heute“ ist
        v.setTextViewText(R.id.widget_stand, listOfNotNull(z?.titel?.takeIf { gewaehlt != WidgetDaten.ZEIT_HEUTE }, d?.stand?.ifBlank { null }).joinToString(" · "))
        return v
    }

    // Werte, Märkte und News: Titel, rollbare Liste (WidgetListenDienst), Hinweis bei leerer Liste, Stand.
    private fun liste(c: Context, d: WidgetDaten?, art: String, id: Int, s: Stil): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_liste)
        grundSetzen(v, s)
        farbe(v, s.schrift3, R.id.widget_stand)
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, if (art == ART_NEWS) "news" else null))
        val titel = when (art) {
            ART_MAERKTE -> d?.titelMaerkte?.ifBlank { null } ?: c.getString(R.string.widget_maerkte_name)
            ART_NEWS -> newsTitel(d, newsArt(c, id)) ?: c.getString(R.string.widget_news_name)
            else -> when (quelle(c, id)) {
                QUELLE_LISTE -> name(c, id).ifBlank { null } ?: d?.titelWerte?.ifBlank { null } ?: c.getString(R.string.widget_werte_name)
                QUELLE_DEPOT -> d?.titelBestand?.ifBlank { null } ?: c.getString(R.string.widget_werte_name)
                else -> d?.titelWerte?.ifBlank { null } ?: c.getString(R.string.widget_werte_name)
            }
        }
        val dienst = Intent(c, WidgetListenDienst::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        dienst.data = Uri.parse(dienst.toUri(Intent.URI_INTENT_SCHEME))
        // Vorschaubilder fuellen die Liste selbst (vorschauZeilen); ohne Startbildschirm gibt es keinen Listendienst
        if (!vorschau) {
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(R.id.widget_liste, dienst)
            v.setPendingIntentTemplate(R.id.widget_liste, listenVorlage(c))
        }
        v.setEmptyView(R.id.widget_liste, R.id.widget_hinweis)
        val leer = listenInhalt(c, id, art, d).isEmpty()
        v.setTextViewText(R.id.widget_hinweis, when {
            d == null -> c.getString(R.string.widget_oeffnen)
            gesperrt(d) -> d.hinweis
            !leer -> ""
            art == ART_NEWS -> newsHinweis(d, newsArt(c, id))
            art == ART_MAERKTE -> c.getString(R.string.widget_oeffnen)
            quelle(c, id) == QUELLE_DEPOT -> d.bestandHinweis
            quelle(c, id) == QUELLE_LISTE && liste(c, id).isEmpty() -> c.getString(R.string.widget_liste_leer)
            else -> d.hinweis
        })
        v.setTextColor(R.id.widget_hinweis, s.schrift2)
        // Ohne Titelzeile: Name der eigenen Liste und Inhalt der News stehen vor dem Stand
        val kopf = when {
            art == ART_NEWS -> titel
            art != ART_MAERKTE && quelle(c, id) == QUELLE_LISTE -> name(c, id).ifBlank { null }
            else -> null
        }
        v.setTextViewText(R.id.widget_stand, if (d != null && !gesperrt(d) && !leer) listOfNotNull(kopf, d.stand.ifBlank { null }).joinToString(" · ") else "")
        return v
    }

    // Eine Zeile der rollbaren Listen: ein Wert oder eine Schlagzeile.
    sealed interface Listenzeile {
        // mitKurs false: die Breite reicht nicht fuer Kurs und ganze Namen, die Kursspalte entfaellt in der ganzen Liste
        data class Wert(val w: WidgetWert, val mitKurs: Boolean = true) : Listenzeile
        data class Nachricht(val n: WidgetNachricht) : Listenzeile
    }

    // Inhalt der Liste eines Widgets, geordnet wie eingestellt.
    fun listenInhalt(c: Context, id: Int, art: String?, d: WidgetDaten?): List<Listenzeile> {
        if (d == null || gesperrt(d)) return emptyList()
        if (art == ART_NEWS) {
            // Nur Meldungen, die samt Quellzeile ganz in die Hoehe passen; eine halbe am Rand bleibt weg
            val alle = newsListe(d, newsArt(c, id))
            val m = AppWidgetManager.getInstance(c)
            val (b, h) = if (m == null) 0 to 0 else groesse(m, id)
            val n = if (b > 0 && h > 0) WidgetKarten.nachrichtenAnzahl(alle.map { it.titel }, b, h, c.resources.configuration.fontScale) else alle.size
            return alle.take(n).map { Listenzeile.Nachricht(it) }
        }
        val werte = when {
            art == ART_MAERKTE -> d.maerkte
            quelle(c, id) == QUELLE_DEPOT -> d.bestand
            quelle(c, id) == QUELLE_LISTE -> liste(c, id).mapNotNull { d.listenWerte[it] }
            else -> d.werte
        }
        val geordnet = WidgetDaten.ordnen(werte, art != ART_MAERKTE && sortiert(c, id))
        val breite = if (vorschau && id <= VORSCHAU_BASIS) vorschauBreite else AppWidgetManager.getInstance(c)?.let { groesse(it, id).first } ?: 0
        val betraege = betrag(c, id)
        val mitKurs = WidgetSpalten.kursSichtbar(
            geordnet.map { it.name }, geordnet.map { it.kurs },
            geordnet.map { if (betraege && it.betrag.isNotBlank()) it.betrag else it.aenderung }, breite, c.resources.configuration.fontScale,
        )
        return geordnet.map { Listenzeile.Wert(it, mitKurs) }
    }

    // Eine Listenzeile als Ansicht (fuer WidgetListenDienst).
    fun zeilenAnsicht(c: Context, id: Int, z: Listenzeile): RemoteViews {
        val s = stil(c, id)
        return when (z) {
            is Listenzeile.Wert -> RemoteViews(c.packageName, R.layout.widget_listenzeile).apply {
                val w = z.w
                setTextViewText(R.id.zeile_name, w.name)
                setTextViewText(R.id.zeile_kurs, if (z.mitKurs) WidgetSpalten.fest(w.kurs) else "")
                setViewVisibility(R.id.zeile_kurs, if (z.mitKurs && w.kurs.isNotBlank()) View.VISIBLE else View.GONE)
                setTextViewText(R.id.zeile_proz, WidgetSpalten.fest(if (betrag(c, id) && w.betrag.isNotBlank()) w.betrag else w.aenderung))
                setTextColor(R.id.zeile_name, s.schrift)
                setTextColor(R.id.zeile_kurs, s.kurs)
                setTextColor(R.id.zeile_proz, if (w.hoch) s.hoch else s.runter)
                if (w.ziel.isNotBlank()) setOnClickFillInIntent(R.id.zeile, zeilenZiel(w.ziel))
            }
            is Listenzeile.Nachricht -> RemoteViews(c.packageName, R.layout.widget_nachricht).apply {
                setTextViewText(R.id.nachricht_titel, z.n.titel)
                setTextViewText(R.id.nachricht_zeile, z.n.zeile)
                setTextColor(R.id.nachricht_titel, s.schrift)
                setTextColor(R.id.nachricht_zeile, s.schrift3)
                if (z.n.ziel.isNotBlank()) setOnClickFillInIntent(R.id.zeile, zeilenZiel(z.n.ziel))
            }
        }
    }

    private fun einzelwert(c: Context, d: WidgetDaten?, id: Int, s: Stil, breite: Int, hoehe: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_einzel)
        grundSetzen(v, s)
        farbe(v, s.schrift2, R.id.widget_titel)
        farbe(v, s.schrift, R.id.widget_wert)
        farbe(v, s.schrift3, R.id.widget_stand)
        val symbol = einzel(c, id) ?: d?.einzelStandard.orEmpty()
        val w = d?.einzel?.get(symbol)?.takeIf { !gesperrt(d) }
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, w?.ziel))
        v.setOnClickPendingIntent(R.id.widget_wert, umschalten(c, ART_EINZEL, id))
        when {
            d == null || w == null -> {
                v.setTextViewText(R.id.widget_titel, c.getString(R.string.widget_einzel_name))
                v.setTextViewText(R.id.widget_wert, if (d != null && gesperrt(d)) "🔒" else "–")
                v.setTextViewText(R.id.widget_aenderung, if (d != null && gesperrt(d)) d.hinweis else c.getString(R.string.widget_oeffnen))
                v.setTextColor(R.id.widget_aenderung, s.schrift2)
            }
            else -> {
                v.setTextViewText(R.id.widget_titel, w.name)
                v.setTextViewText(R.id.widget_wert, w.kurs)
                v.setTextViewText(R.id.widget_aenderung, if (betrag(c, id) && w.betrag.isNotBlank()) w.betrag else w.aenderung)
                v.setTextColor(R.id.widget_aenderung, if (w.hoch) s.hoch else s.runter)
            }
        }
        kurveSetzen(c, v, w?.kurve.orEmpty(), if (w?.hoch != false) s.hoch else s.runter, breite, hoehe)
        v.setTextViewText(R.id.widget_stand, if (w != null) d?.stand.orEmpty() else "")
        return v
    }

    private fun mover(c: Context, d: WidgetDaten?, id: Int, s: Stil, breite: Int, hoehe: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_mover)
        grundSetzen(v, s)
        farbe(v, s.schrift3, R.id.widget_stand)
        v.setInt(R.id.widget_trenner, "setBackgroundColor", s.trenner)
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, null))
        val offen = d != null && !gesperrt(d)
        val gewinner = if (offen) d!!.gewinner else emptyList()
        val verlierer = if (offen) d!!.verlierer else emptyList()
        // je Seite so viele Zeilen, wie in die Hoehe passen (eine bis drei)
        val jeSeite = (zeilenFuer(hoehe, 70, 6) / 2).coerceIn(1, 3)
        // Reicht die Breite nicht fuer Kurs und ganze Namen, entfaellt die Kursspalte
        val sichtbar = (gewinner + verlierer).let { l ->
            WidgetSpalten.kursSichtbar(l.map { it.name }, l.map { it.kurs }, l.map { it.aenderung }, breite, c.resources.configuration.fontScale)
        }
        val ohneBetrag = nurProzent(c, id) || !sichtbar
        zeilen(c, v, s, gewinner, 0, 3, jeSeite, ohneKurs = ohneBetrag)
        zeilen(c, v, s, verlierer, 3, 6, jeSeite, ohneKurs = ohneBetrag)
        v.setViewVisibility(R.id.widget_trenner, if (gewinner.isNotEmpty() && verlierer.isNotEmpty()) View.VISIBLE else View.GONE)
        hinweis(v, s, when {
            d == null -> c.getString(R.string.widget_oeffnen)
            gesperrt(d) -> d.hinweis
            else -> d.moverHinweis
        })
        v.setTextViewText(R.id.widget_stand, if (offen) d!!.stand else "")
        return v
    }

    private fun dividenden(c: Context, d: WidgetDaten?, s: Stil, hoehe: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_dividenden)
        grundSetzen(v, s)
        farbe(v, s.schrift2, R.id.widget_titel, R.id.div_erhalten_text, R.id.div_erwartet_text)
        v.setTextViewText(R.id.widget_titel, d?.titelDividenden?.ifBlank { null } ?: c.getString(R.string.widget_dividenden_name))
        farbe(v, s.schrift, R.id.div_erhalten, R.id.div_erwartet)
        farbe(v, s.schrift3, R.id.widget_stand)
        v.setInt(R.id.widget_trenner, "setBackgroundColor", s.trenner)
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, null))
        val offen = d != null && !gesperrt(d)
        v.setTextViewText(R.id.div_erhalten_text, if (offen) d!!.divErhaltenText else "")
        v.setTextViewText(R.id.div_erhalten, if (offen) d!!.divErhalten else "🔒".takeIf { d != null } ?: "–")
        v.setTextViewText(R.id.div_erwartet_text, if (offen) d!!.divErwartetText else "")
        v.setTextViewText(R.id.div_erwartet, if (offen) d!!.divErwartet else "")
        // naechste Zahlungen, so viele wie unter die beiden Betraege passen
        val naechste = if (offen) d!!.divNaechste else emptyList()
        val platz = ((hoehe - 140) / 22).coerceIn(0, 4)
        zeilen(c, v, s, naechste, 0, 4, platz, mitProzent = false)
        v.setViewVisibility(R.id.widget_trenner, if (naechste.isNotEmpty() && platz > 0) View.VISIBLE else View.GONE)
        hinweis(v, s, when {
            d == null -> c.getString(R.string.widget_oeffnen)
            gesperrt(d) -> d.hinweis
            else -> d.divHinweis.takeIf { naechste.isEmpty() }.orEmpty()
        })
        v.setTextViewText(R.id.widget_stand, if (offen) d!!.stand else "")
        return v
    }

    // Stimmung: Name des Barometers, Tacho von extremer Angst bis extremer Gier, darunter Wert und Stufe. Mit der Wahl
    // „Alle“ (0.69) stehen die vorhandenen Barometer kompakt untereinander: Name, Stufe und Wert, gefaerbt nach Stufe;
    // so viele Zeilen, wie in die Hoehe passen (WidgetWahl.alleZeilen).
    private fun stimmung(c: Context, d: WidgetDaten?, id: Int, s: Stil, breite: Int, hoehe: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_stimmung)
        grundSetzen(v, s)
        farbe(v, s.schrift, R.id.widget_wert)
        farbe(v, s.schrift2, R.id.widget_name)
        farbe(v, s.schrift3, R.id.widget_stand)
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, WidgetZiel.STIMMUNG))
        val wahl = barometer(c, id)
        val mitStand = untertitel(c, id)
        val alle = if (wahl == WidgetDaten.STIMMUNG_ALLE) d?.stimmung?.let { WidgetWahl.alleZeilen(it, hoehe, mitStand) }.orEmpty() else emptyList()
        val b = if (wahl == WidgetDaten.STIMMUNG_ALLE) null else d?.stimmung?.get(wahl)
        v.setViewVisibility(R.id.widget_alle, if (alle.isNotEmpty()) View.VISIBLE else View.GONE)
        v.removeAllViews(R.id.widget_alle)
        if (alle.isNotEmpty()) {
            v.setViewVisibility(R.id.widget_name, View.GONE)
            v.setViewVisibility(R.id.widget_wert, View.GONE)
            v.setViewVisibility(R.id.widget_aenderung, View.GONE)
            v.setViewVisibility(R.id.widget_tacho, View.GONE)
            val mitStufe = WidgetWahl.alleMitStufe(breite)
            for (e in alle) {
                val farbe = STUFEN[e.stufeNr.coerceIn(0, 4)]
                val z = RemoteViews(c.packageName, R.layout.widget_stimmung_zeile)
                z.setTextViewText(R.id.alle_name, e.titel)
                z.setTextViewText(R.id.alle_stufe, e.stufe)
                z.setViewVisibility(R.id.alle_stufe, if (mitStufe) View.VISIBLE else View.GONE)
                z.setTextViewText(R.id.alle_wert, e.wert.toString())
                z.setTextColor(R.id.alle_name, s.schrift)
                z.setTextColor(R.id.alle_stufe, farbe)
                z.setTextColor(R.id.alle_wert, farbe)
                v.addView(R.id.widget_alle, z)
            }
            v.setTextViewText(R.id.widget_stand, d?.stand.orEmpty())
            return v
        }
        // Der Name steht immer da, auch ohne Untertitel und ohne Daten
        v.setViewVisibility(R.id.widget_name, View.VISIBLE)
        v.setTextViewText(R.id.widget_name, b?.titel ?: de.troetenrepublik.app.i18n.S.sbKachel(wahl).ifBlank { de.troetenrepublik.app.i18n.S.widgetStimmung })
        v.setViewVisibility(R.id.widget_wert, View.VISIBLE)
        v.setViewVisibility(R.id.widget_aenderung, View.VISIBLE)
        if (b == null) {
            v.setTextViewText(R.id.widget_wert, "–")
            v.setTextViewText(R.id.widget_aenderung, if (d == null) c.getString(R.string.widget_oeffnen) else "")
            v.setTextColor(R.id.widget_aenderung, s.schrift2)
            v.setViewVisibility(R.id.widget_tacho, View.GONE)
        } else {
            v.setTextViewText(R.id.widget_wert, b.wert.toString())
            v.setTextViewText(R.id.widget_aenderung, b.stufe)
            v.setTextColor(R.id.widget_aenderung, STUFEN[b.stufeNr.coerceIn(0, 4)])
            // Eine Zeile fuer den Namen kostet Hoehe: der Tacho wird niedriger, die Schrift bleibt
            tacho(c, b.wert, s, breite - 28, (hoehe - 86).coerceIn(24, 120))?.let {
                v.setImageViewBitmap(R.id.widget_tacho, it)
                v.setViewVisibility(R.id.widget_tacho, View.VISIBLE)
            } ?: v.setViewVisibility(R.id.widget_tacho, View.GONE)
        }
        // Unter dem Barometer Quelle und Stand (nur mit Untertitel); beim Gesamtwert nennt der Name schon alles
        v.setTextViewText(R.id.widget_stand, if (b != null) listOf(
            if (b.quelle == de.troetenrepublik.app.i18n.S.sgAnbieter) "" else b.quelle,
            d?.stand.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString("\n") else "")
        return v
    }

    // Farben der fuenf Stufen von extremer Angst bis extremer Gier.
    private val STUFEN = intArrayOf(0xFFFF3D47.toInt(), 0xFFF5A524.toInt(), 0xFF9BA1A6.toInt(), 0xFF7FD17A.toInt(), 0xFF30A46C.toInt())

    // Halbkreis in fuenf Abschnitten mit Zeiger auf wert (0 bis 100).
    private fun tacho(c: Context, wert: Int, s: Stil, breiteDp: Int, hoeheDp: Int): Bitmap? {
        if (breiteDp <= 0 || hoeheDp <= 0) return null
        val dichte = c.resources.displayMetrics.density.coerceAtMost(2f)
        val hMax = (hoeheDp * dichte).toInt().coerceIn(30, 220)
        val b = (minOf(breiteDp * dichte, hMax * 2f)).toInt().coerceIn(60, 440)
        // Bild nur so hoch wie der Bogen, sonst steht ueber ihm Leerraum
        val h = minOf(hMax, (b * 0.5225f).toInt() + 1)
        val bild = Bitmap.createBitmap(b, h, Bitmap.Config.ARGB_8888)
        bild.density = (dichte * 160).toInt()
        val leinwand = Canvas(bild)
        val dicke = b * 0.09f
        val r = minOf(b / 2f, h.toFloat()) - dicke / 2 - 2
        val mx = b / 2f
        val my = h - 2f - dicke / 4
        val bogen = RectF(mx - r, my - r, mx + r, my + r)
        val pinsel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dicke; strokeCap = Paint.Cap.BUTT }
        // Abschnitte wie die Stufen: 0–25, 25–45, 45–55, 55–75, 75–100
        val grenzen = listOf(0, 25, 45, 55, 75, 100)
        for (i in 0 until 5) {
            pinsel.color = STUFEN[i]
            val start = 180f + grenzen[i] * 1.8f
            leinwand.drawArc(bogen, start + 0.8f, (grenzen[i + 1] - grenzen[i]) * 1.8f - 1.6f, false, pinsel)
        }
        val winkel = Math.toRadians(180.0 + wert.coerceIn(0, 100) * 1.8)
        val laenge = r - dicke * 0.9f
        val zeiger = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.schrift; strokeWidth = 2.4f * dichte; strokeCap = Paint.Cap.ROUND }
        leinwand.drawLine(mx, my, mx + (laenge * cos(winkel)).toFloat(), my + (laenge * sin(winkel)).toFloat(), zeiger)
        leinwand.drawCircle(mx, my, 4f * dichte, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.schrift })
        return bild
    }

    // Heatmap: Titel, Kacheln als Bild, Hinweis ohne Daten, Stand. Ein Tipp oeffnet die Heatmap mit derselben Auswahl.
    private fun heatmap(c: Context, d: WidgetDaten?, id: Int, s: Stil, breite: Int, hoehe: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_heatmap)
        grundSetzen(v, s)
        farbe(v, s.schrift3, R.id.widget_stand)
        val quelle = heatmapQuelle(c, id)
        val zeit = heatmapZeitraum(c, id)
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, WidgetZiel.heatmap(quelle, zeit)))
        val h = d?.heatmaps?.get(WidgetDaten.heatmapSchluessel(quelle, zeit))?.takeIf { !gesperrt(d) }
        val bild = if (h != null && h.kacheln.isNotEmpty()) heatmapBild(c, h, id, breite - 28, hoehe - 56) else null
        if (bild != null) {
            v.setImageViewBitmap(R.id.widget_bild, bild)
            v.setViewVisibility(R.id.widget_bild, View.VISIBLE)
        } else {
            v.setViewVisibility(R.id.widget_bild, View.GONE)
        }
        hinweis(v, s, when {
            d == null -> c.getString(R.string.widget_oeffnen)
            gesperrt(d) -> d.hinweis
            h == null -> d.heatmapHinweis
            bild == null -> h.hinweis
            else -> ""
        })
        // Quelle und Zeitraum stehen vor dem Stand
        v.setTextViewText(R.id.widget_stand, if (bild != null) listOfNotNull(h?.titel?.ifBlank { null }, d?.stand?.ifBlank { null }).joinToString(" · ") else "")
        return v
    }

    // Farben der Heatmap wie in der App: dunkles Thema bei heller Schrift, helles bei dunkler
    private val HEAT_HELL_HOCH = 0xFF02CA50.toInt()
    private val HEAT_HELL_RUNTER = 0xFFFF3D47.toInt()
    private val HEAT_HELL_NEUTRAL = 0xFF2E2F30.toInt()
    private val HEAT_DUNKEL_HOCH = Erscheinung.HELL_GRUEN
    private val HEAT_DUNKEL_RUNTER = Erscheinung.HELL_ROT
    private val HEAT_DUNKEL_NEUTRAL = 0xFFB5B7BA.toInt()

    // Heatmap als Bild (0.68): Kacheln wie in der App (Treemap ueber alle Kacheln), Farbe nach Aenderung, darin Kuerzel und
    // Prozent nach HeatmapBeschriftung. Seit 0.69 in voller Pixelgroesse des Widgets (Groesse in dp mal Dichte); vorher
    // hoechstens 130 000 Pixel, das Widget streckte das Bild und es wirkte pixelig. Gedeckelt auf die Bildschirmflaeche
    // (RemoteViews nehmen hoechstens das Eineinhalbfache davon); die Schrift ist in sp gesetzt.
    private fun heatmapBild(c: Context, h: WidgetHeatmap, id: Int, breiteDp: Int, hoeheDp: Int): Bitmap? {
        if (breiteDp < 40 || hoeheDp < 30) return null
        val dichte = bildDichte(c, breiteDp, hoeheDp)
        val b = (breiteDp * dichte).toInt()
        val hp = (hoeheDp * dichte).toInt()
        if (b < 10 || hp < 10) return null
        val sp = dichte * c.resources.configuration.fontScale
        val bild = Bitmap.createBitmap(b, hp, Bitmap.Config.ARGB_8888)
        val leinwand = Canvas(bild)
        val rechtecke = Treemap.anordnen(h.kacheln.map { it.gewicht }, b.toFloat(), hp.toFloat())
        val luecke = 1f * dichte
        val radius = 3f * dichte
        val innen = 8f * dichte
        val dunkel = dunkleSchrift(c, id)
        val hoch = if (dunkel) HEAT_DUNKEL_HOCH else HEAT_HELL_HOCH
        val runter = if (dunkel) HEAT_DUNKEL_RUNTER else HEAT_HELL_RUNTER
        val neutral = if (dunkel) HEAT_DUNKEL_NEUTRAL else HEAT_HELL_NEUTRAL
        val gross = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f * sp; typeface = Typeface.DEFAULT_BOLD }
        val klein = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f * sp; typeface = Typeface.DEFAULT_BOLD }
        val proz = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f * sp }
        val fuellung = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        fun masz(p: Paint, t: String) = HeatmapBeschriftung.Text(p.measureText(t), p.fontMetrics.let { it.descent - it.ascent })
        val bezugGross = gross.measureText(HeatmapBeschriftung.BEZUG_KUERZEL)
        val bezugKlein = klein.measureText(HeatmapBeschriftung.BEZUG_KUERZEL)
        val bezugProzent = proz.measureText(de.troetenrepublik.app.ui.Fmt.signedPct(1.0, 2))
        h.kacheln.forEachIndexed { i, k ->
            val r = rechtecke.getOrNull(i) ?: return@forEachIndexed
            if (r.w <= 2 * luecke || r.h <= 2 * luecke) return@forEachIndexed
            val farbe = HeatmapBeschriftung.farbe(k.aenderung, h.voll, hoch, runter, neutral)
            fuellung.color = farbe
            leinwand.drawRoundRect(RectF(r.x + luecke, r.y + luecke, r.x + r.w - luecke, r.y + r.h - luecke), radius, radius, fuellung)
            // Ohne Beschriftung (Zahlenkuerzel ohne Namen) bleibt die Kachel leer
            if (k.kuerzel.isBlank()) return@forEachIndexed
            val prozente = listOf(k.prozent, k.prozentKurz).filter { it.isNotBlank() }
            val wahl = HeatmapBeschriftung.waehlen(
                r.w, r.h, innen,
                HeatmapBeschriftung.Masse(
                    masz(gross, k.kuerzel), masz(klein, k.kuerzel), prozente.map { masz(proz, it) },
                    bezugGross = bezugGross, bezugKlein = bezugKlein, bezugProzent = bezugProzent,
                ),
                gezoomt = false,
            )
            val schrift = if (HeatmapBeschriftung.dunkleSchrift(farbe)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            val schriftProzent = (schrift and 0x00FFFFFF) or 0xE6000000.toInt()
            when (wahl.stufe) {
                HeatmapBeschriftung.Stufe.KUERZEL_PROZENT -> {
                    val text = prozente.getOrNull(wahl.prozent) ?: return@forEachIndexed
                    val gh = gross.fontMetrics.let { it.descent - it.ascent }
                    val ph = proz.fontMetrics.let { it.descent - it.ascent }
                    val y0 = r.y + (r.h - (gh + ph)) / 2
                    gross.color = schrift
                    proz.color = schriftProzent
                    leinwand.drawText(k.kuerzel, r.x + (r.w - gross.measureText(k.kuerzel)) / 2, y0 - gross.fontMetrics.ascent, gross)
                    leinwand.drawText(text, r.x + (r.w - proz.measureText(text)) / 2, y0 + gh - proz.fontMetrics.ascent, proz)
                }
                HeatmapBeschriftung.Stufe.KUERZEL -> {
                    val kh = klein.fontMetrics.let { it.descent - it.ascent }
                    klein.color = schrift
                    leinwand.drawText(k.kuerzel, r.x + (r.w - klein.measureText(k.kuerzel)) / 2, r.y + (r.h - kh) / 2 - klein.fontMetrics.ascent, klein)
                }
                else -> {}
            }
        }
        return bild
    }

    @Volatile private var globusKarte: GlobusKarte? = null

    // Umrisse aus dem Asset der App, einmal gelesen.
    private fun karte(): GlobusKarte? = globusKarte ?: runCatching {
        GlobusLesen.lesen(Plattform.asset("globus/laender.json").decodeToString())
    }.getOrNull()?.also { globusKarte = it }

    private val GLOBUS_NEUTRAL = 0xFFB7791F.toInt()

    // Globus (0.69): Kugel in voller Pixelgroesse des Widgets, schwarzer Grund (bei durchscheinendem oder leerem Grund
    // durchsichtig), graue Grenzen, Laender nach der gewaehlten Einfaerbung wie im Reiter Globus. Ein Tipp oeffnet den Reiter.
    private fun globus(c: Context, d: WidgetDaten?, id: Int, s: Stil, breite: Int, hoehe: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_globus)
        val voll = grund(c, id) == GRUND_VOLL
        // Heller Grund (dunkle Schrift): heller Ozean und Grund, dunkle Beschriftung; sonst wie bisher
        val pal = if (dunkleSchrift(c, id)) GlobusPalette.HELL else GlobusPalette.DUNKEL
        if (!voll) grundSetzen(v, s) else v.setInt(R.id.widget_wurzel, "setBackgroundColor", pal.hintergrund)
        farbe(v, s.schrift2, R.id.widget_hinweis)
        farbe(v, s.schrift2, R.id.widget_stand)
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, WidgetZiel.GLOBUS))
        val modus = globusModus(c, id)
        val werte = d?.globus?.get(modus.name)?.takeIf { !gesperrt(d) }
        val karte = karte()
        val mitte = MITTEN.firstOrNull { it.first == globusMitte(c, id) }?.second ?: MITTEN.first().second
        val laenge = WidgetWahl.laenge(mitte.first, globusDreh(c, id))
        v.setOnClickPendingIntent(R.id.globus_links, globusDrehung(c, id, -1))
        v.setOnClickPendingIntent(R.id.globus_rechts, globusDrehung(c, id, 1))
        val landFarbe = { l: GlobusLand -> GlobusFarbe.farbe(modus, werte?.get(l.a3), s.hoch, s.runter, GLOBUS_NEUTRAL, pal) }
        // Ab Android 12 mit vollem Grund: wischbarer Stapel statt eines festen Bildes
        val stapel = !vorschau && Build.VERSION.SDK_INT >= 31 && voll && karte != null && breite > 20 && hoehe > 20
        // Der Stapel liegt unter dem Tippraster: im Stapel bleiben die Zellen auf der Kugel frei, damit Wischen und Tippen
        // dort ankommen; die Raender und die Zellen neben der Kugel drehen in beiden Faellen
        globusZellen(c, v, id, karte, laenge, mitte.second, breite, hoehe, nurDrehen = stapel)
        if (stapel) globusStapel(c, v, karte!!, laenge, mitte.second, breite, hoehe, landFarbe, pal)
        v.setViewVisibility(R.id.globus_stapel, if (stapel) View.VISIBLE else View.GONE)
        val dichte = bildDichte(c, breite.coerceAtLeast(1), hoehe.coerceAtLeast(1))
        val bild = if (!stapel && karte != null && breite > 20 && hoehe > 20) {
            GlobusBild.zeichne(
                karte, (breite * dichte).toInt(), (hoehe * dichte).toInt(), laenge, mitte.second,
                landFarbe,
                if (voll) pal.hintergrund else 0,
                palette = pal,
            )
        } else null
        if (bild != null) {
            v.setImageViewBitmap(R.id.widget_bild, bild)
            v.setViewVisibility(R.id.widget_bild, View.VISIBLE)
        } else v.setViewVisibility(R.id.widget_bild, View.GONE)
        val hinweis = when {
            d == null -> c.getString(R.string.widget_oeffnen)
            gesperrt(d) -> d.hinweis
            werte == null -> d.heatmapHinweis
            else -> ""
        }
        v.setViewVisibility(R.id.widget_hinweis, if (hinweis.isBlank()) View.GONE else View.VISIBLE)
        v.setTextViewText(R.id.widget_hinweis, hinweis)
        v.setTextViewText(R.id.widget_stand, if (d != null && werte != null) listOfNotNull(d.globusTitel[modus.name], d.stand.ifBlank { null }).joinToString(" · ") else "")
        return v
    }

    private val GLOBUS_ZELLEN = arrayOf(intArrayOf(R.id.globus_z00, R.id.globus_z01, R.id.globus_z02, R.id.globus_z03, R.id.globus_z04, R.id.globus_z05), intArrayOf(R.id.globus_z10, R.id.globus_z11, R.id.globus_z12, R.id.globus_z13, R.id.globus_z14, R.id.globus_z15), intArrayOf(R.id.globus_z20, R.id.globus_z21, R.id.globus_z22, R.id.globus_z23, R.id.globus_z24, R.id.globus_z25), intArrayOf(R.id.globus_z30, R.id.globus_z31, R.id.globus_z32, R.id.globus_z33, R.id.globus_z34, R.id.globus_z35), intArrayOf(R.id.globus_z40, R.id.globus_z41, R.id.globus_z42, R.id.globus_z43, R.id.globus_z44, R.id.globus_z45), intArrayOf(R.id.globus_z50, R.id.globus_z51, R.id.globus_z52, R.id.globus_z53, R.id.globus_z54, R.id.globus_z55), intArrayOf(R.id.globus_z60, R.id.globus_z61, R.id.globus_z62, R.id.globus_z63, R.id.globus_z64, R.id.globus_z65), intArrayOf(R.id.globus_z70, R.id.globus_z71, R.id.globus_z72, R.id.globus_z73, R.id.globus_z74, R.id.globus_z75))

    // Ansichten im Stapel: einmal um die Erde in Schritten von 30 Grad.
    private const val GLOBUS_ANSICHTEN = 12

    // Wischbarer Globus (0.77): Der Startbildschirm gibt Wischen nur an Sammelelemente weiter, und nur senkrecht. Der
    // Stapel haelt deshalb die Kugel in zwoelf Drehungen; Wischen nach oben oder unten blaettert zur naechsten.
    // Der Stapel zeigt seine Ansichten um ein Zehntel versetzt hintereinander: Er ist um ein Neuntel groesser als das
    // Widget und nach oben rechts hinausgeschoben, so fuellt die vorderste Ansicht das Widget und verdeckt die anderen.
    @androidx.annotation.RequiresApi(31)
    private fun globusStapel(c: Context, v: RemoteViews, karte: GlobusKarte, laenge: Double, breiteGrad: Double, breite: Int, hoehe: Int, farbe: (GlobusLand) -> Int?, pal: GlobusPalette) {
        val m = c.resources.displayMetrics
        // Bildspeicher: Android laesst je Widget das Anderthalbfache des Bildschirms zu (6 Byte je Bildschirmpixel); die
        // Ansichten teilen sich zwei Drittel davon, mit 2 Byte je Pixel (RGB_565, der Grund ist deckend)
        val wunsch = minOf(breite, hoehe) * m.density
        fun seiteMax(n: Int) = kotlin.math.sqrt(m.widthPixels.toFloat() * m.heightPixels * 4f / n / 2f)
        // Grosse Widgets: weniger Ansichten (45 oder 60 Grad je Schritt), dafuer jede in voller Aufloesung
        val ansichten = intArrayOf(GLOBUS_ANSICHTEN, 8, 6).firstOrNull { seiteMax(it) >= wunsch } ?: 6
        val schritt = 360 / ansichten
        val seite = minOf(wunsch, seiteMax(ansichten)).toInt().coerceAtLeast(64)
        val offen = zeilenZiel(WidgetZiel.GLOBUS)
        val liste = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
        for (k in 0 until ansichten) {
            val lon = WidgetWahl.laenge(laenge, k * schritt)
            val blatt = RemoteViews(c.packageName, R.layout.widget_globus_blatt)
            blatt.setInt(R.id.widget_wurzel, "setBackgroundColor", pal.hintergrund)
            val gross = GlobusBild.zeichne(karte, seite, seite, lon, breiteGrad, farbe, pal.hintergrund, palette = pal)
            val bild = gross.copy(Bitmap.Config.RGB_565, false) ?: gross
            if (bild !== gross) gross.recycle()
            blatt.setImageViewBitmap(R.id.widget_bild, bild)
            blatt.setOnClickFillInIntent(R.id.widget_wurzel, offen)
            blatt.setOnClickFillInIntent(R.id.globus_links, offen)
            blatt.setOnClickFillInIntent(R.id.globus_rechts, offen)
            globusLaender(karte, lon, breiteGrad, breite, hoehe) { zelle, a2, _, _ ->
                blatt.setOnClickFillInIntent(zelle, if (a2 != null) zeilenZiel(WidgetZiel.land(a2)) else offen)
            }
            liste.addItem(k.toLong(), blatt)
        }
        v.setRemoteAdapter(R.id.globus_stapel, liste.build())
        v.setPendingIntentTemplate(R.id.globus_stapel, listenVorlage(c))
        // Jede Ansicht sitzt in einem Rahmen mit 4 dp Rand (StackView.FRAME_PADDING): auch der liegt ausserhalb des Widgets
        val rand = 4f
        v.setViewLayoutMargin(R.id.globus_stapel, RemoteViews.MARGIN_LEFT, -rand, TypedValue.COMPLEX_UNIT_DIP)
        v.setViewLayoutMargin(R.id.globus_stapel, RemoteViews.MARGIN_BOTTOM, -rand, TypedValue.COMPLEX_UNIT_DIP)
        v.setViewLayoutMargin(R.id.globus_stapel, RemoteViews.MARGIN_TOP, -(hoehe / 9f + rand * 11f / 9f), TypedValue.COMPLEX_UNIT_DIP)
        v.setViewLayoutMargin(R.id.globus_stapel, RemoteViews.MARGIN_RIGHT, -(breite / 9f + rand * 11f / 9f), TypedValue.COMPLEX_UNIT_DIP)
    }

    // Land unter der Mitte jeder Zelle des Tipprasters: (Zelle, Laenderkuerzel oder null, auf der Kugel, x-Anteil).
    private fun globusLaender(karte: GlobusKarte?, laenge: Double, breiteGrad: Double, breite: Int, hoehe: Int, je: (Int, String?, Boolean, Double) -> Unit) {
        val b = breite.coerceAtLeast(1); val h = hoehe.coerceAtLeast(1)
        val a = GlobusAnsicht()
        a.setze(laenge, breiteGrad, b / 2f, h / 2f, minOf(b, h) / 2f * 0.96f)
        val aus = DoubleArray(2)
        val tol = Math.toDegrees(10.0 / (minOf(b, h) / 2.0 * 0.96)).coerceAtMost(4.0)
        for (z in 0 until WidgetWahl.GLOBUS_ZEILEN) for (s in 0 until WidgetWahl.GLOBUS_SPALTEN) {
            val (fx, fy) = WidgetWahl.zellenMitte(s, z)
            val aufKugel = a.rueck((fx * b).toFloat(), (fy * h).toFloat(), aus)
            val land = if (karte != null && aufKugel) karte.landBei(aus[0], aus[1], tol) else null
            je(GLOBUS_ZELLEN[z][s], land?.a2?.takeIf { it.length == 2 }, aufKugel, fx)
        }
    }

    // Tippraster ueber der Kugel: jede Zelle oeffnet das Land unter ihrer Mitte (Laenderansicht der App), ohne Land den Reiter Globus.
    // Das Raster fuellt die Mitte zwischen den Raendern, die die Kugel drehen. Die Kugel hat dieselbe Lage wie im Bild.
    private fun globusZellen(c: Context, v: RemoteViews, id: Int, karte: GlobusKarte?, laenge: Double, breiteGrad: Double, breite: Int, hoehe: Int, nurDrehen: Boolean = false) {
        val offen = oeffnen(c, WidgetZiel.GLOBUS)
        globusLaender(karte, laenge, breiteGrad, breite, hoehe) { zelle, a2, aufKugel, fx ->
            // Neben der Kugel dreht der Tipp wie an den Raendern (0.77, ohne Pfeile)
            if (nurDrehen && aufKugel) return@globusLaender
            v.setOnClickPendingIntent(zelle, when {
                a2 != null -> oeffnen(c, WidgetZiel.land(a2))
                !aufKugel -> globusDrehung(c, id, if (fx < 0.5) -1 else 1)
                else -> offen
            })
        }
    }

    // Pixel je dp fuer ein Bild in voller Aufloesung: die Dichte des Geraets, bei mehr Pixeln als der Bildschirm hat verkleinert.
    private fun bildDichte(c: Context, breiteDp: Int, hoeheDp: Int): Float {
        val m = c.resources.displayMetrics
        val flaeche = maxOf(m.widthPixels.toFloat() * m.heightPixels, 1f)
        return minOf(m.density, kotlin.math.sqrt(flaeche / (breiteDp.toFloat() * hoeheDp).coerceAtLeast(1f)))
    }

    // ------------------------------------------------------------ Kategorie-Widgets (0.73)

    private val KARTE_ZEILEN = intArrayOf(R.id.kz1, R.id.kz2, R.id.kz3, R.id.kz4, R.id.kz5, R.id.kz6, R.id.kz7, R.id.kz8)
    private val KARTE_TEXTE = intArrayOf(R.id.kt1, R.id.kt2, R.id.kt3, R.id.kt4, R.id.kt5, R.id.kt6, R.id.kt7, R.id.kt8)
    private val KARTE_WERTE = intArrayOf(R.id.kw1, R.id.kw2, R.id.kw3, R.id.kw4, R.id.kw5, R.id.kw6, R.id.kw7, R.id.kw8)
    private val KARTE_UNTER = intArrayOf(R.id.ku1, R.id.ku2, R.id.ku3, R.id.ku4, R.id.ku5, R.id.ku6, R.id.ku7, R.id.ku8)
    private val KARTE_BALKEN = intArrayOf(R.id.kb1, R.id.kb2, R.id.kb3, R.id.kb4, R.id.kb5, R.id.kb6, R.id.kb7, R.id.kb8)

    // Karte einer Kategorie: Titel, so viele Zeilen wie in die Groesse passen (WidgetKarten.zuschneiden), Hinweis ohne Daten,
    // Stand. Ein Tipp auf eine Zeile oeffnet deren Seite, sonst die Kategorie.
    private fun karte(c: Context, d: WidgetDaten?, art: String, id: Int, s: Stil, breite: Int, hoehe: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_karte)
        grundSetzen(v, s)
        farbe(v, s.schrift2, R.id.karte_titel)
        farbe(v, s.schrift3, R.id.widget_stand, R.id.karte_haupt_unter)
        val offen = d != null && !gesperrt(d)
        val schluessel = WidgetVorgabe.kartenSchluessel(art, variante(c, id))
        val roh = if (!offen) null else if (art == ART_WISSEN) wissenKarte(c) else d?.karten?.get(schluessel)
        // Karten ohne eigenen Stand (aus dem Konto gerechnet) zeigen den Stand der Widgets
        val karte = roh?.let { if (it.stand.isBlank()) it.copy(stand = d?.stand.orEmpty()) else it }
        val ziel = karte?.ziel?.ifBlank { null } ?: WidgetKarten.ziel(art).ifBlank { null }
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, ziel))
        val leer = when {
            d == null -> c.getString(R.string.widget_oeffnen)
            gesperrt(d) -> d.hinweis
            // Signale und Volatilitaet brauchen einen Wert; ohne gewaehlten und ohne Standardwert gibt es keine Karte
            karte == null && (art == ART_SIGNALE || art == ART_VOLATILITAET || art == ART_WARNZEICHEN) && variante(c, id) == null -> de.troetenrepublik.app.i18n.S.widgetWertWaehlen
            else -> d.kartenHinweis
        }
        val z = WidgetKarten.zuschneiden(karte, breite, hoehe, c.resources.configuration.fontScale, leer)
        v.setViewVisibility(R.id.karte_titel, if (z.titel) View.VISIBLE else View.GONE)
        if (z.titel) v.setTextViewText(R.id.karte_titel, karte?.titel.orEmpty())
        // Hauptaussage gross, mit kleiner Zeile darunter
        v.setViewVisibility(R.id.karte_haupt, if (z.haupt) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.karte_haupt_unter, if (z.haupt && karte?.hauptUnter?.isNotBlank() == true) View.VISIBLE else View.GONE)
        if (z.haupt && karte != null) {
            v.setTextViewText(R.id.karte_haupt, karte.haupt)
            v.setTextColor(R.id.karte_haupt, tonFarbe(karte.hauptTon, s))
            v.setTextViewText(R.id.karte_haupt_unter, karte.hauptUnter)
        }
        val grafik = if (z.grafikHoehe > 0) karte?.grafik else null
        val bild = grafik?.let { grafikBild(c, it, s, breite - 28, z.grafikHoehe) }
        if (bild != null) {
            v.setImageViewBitmap(R.id.karte_grafik, bild)
            v.setViewVisibility(R.id.karte_grafik, View.VISIBLE)
        } else v.setViewVisibility(R.id.karte_grafik, View.GONE)
        for (i in KARTE_ZEILEN.indices) {
            val e = z.zeilen.getOrNull(i)
            if (e == null) { v.setViewVisibility(KARTE_ZEILEN[i], View.GONE); continue }
            v.setViewVisibility(KARTE_ZEILEN[i], View.VISIBLE)
            v.setTextViewText(KARTE_TEXTE[i], e.text)
            v.setInt(KARTE_TEXTE[i], "setMaxLines", if (e.klemmen > 0) e.klemmen else if (e.einzeilig) 1 else 3)
            v.setTextColor(KARTE_TEXTE[i], s.schrift)
            v.setViewVisibility(KARTE_WERTE[i], if (e.wert.isBlank()) View.GONE else View.VISIBLE)
            v.setTextViewText(KARTE_WERTE[i], e.wert)
            v.setTextColor(KARTE_WERTE[i], when (e.hoch) { true -> s.hoch; false -> s.runter; null -> s.schrift })
            val unter = (z.mitUnter || e.gestapelt) && e.unter.isNotBlank()
            v.setViewVisibility(KARTE_UNTER[i], if (unter) View.VISIBLE else View.GONE)
            if (unter) {
                v.setTextViewText(KARTE_UNTER[i], e.unter)
                v.setTextColor(KARTE_UNTER[i], when (e.unterHoch) { true -> s.hoch; false -> s.runter; null -> s.schrift3 })
            }
            val zeilenZiel = WidgetZiel.pruefen(e.ziel.ifBlank { null })
            v.setOnClickPendingIntent(KARTE_ZEILEN[i], oeffnen(c, zeilenZiel ?: ziel))
            // Balken unter der Zeile (0.78)
            val bar = if (e.balken.isEmpty()) null else balkenBild(c, e.balken, e.hoch, s, breite - 28)
            if (bar != null) {
                v.setImageViewBitmap(KARTE_BALKEN[i], bar)
                v.setViewVisibility(KARTE_BALKEN[i], View.VISIBLE)
            } else v.setViewVisibility(KARTE_BALKEN[i], View.GONE)
        }
        hinweis(v, s, z.hinweis)
        v.setTextViewText(R.id.widget_stand, if (z.stand) karte?.stand.orEmpty() else "")
        return v
    }

    // Schmaler Balken unter einer Zeile (5 dp hoch). Ein Wert: von links, in der Farbe des Werts (hoch). Zwei Werte: von der
    // Mitte aus, links rot, rechts gruen. Dahinter eine blasse Bahn, die die volle Laenge zeigt.
    private fun balkenBild(c: Context, werte: List<Double>, hoch: Boolean?, s: Stil, breiteDp: Int): Bitmap? {
        if (breiteDp < 40 || werte.isEmpty()) return null
        val dichte = bildDichte(c, breiteDp, 5).coerceAtMost(2f)
        val b = (breiteDp * dichte).toInt().coerceIn(40, 500)
        val h = (5f * dichte).toInt().coerceAtLeast(4)
        val bild = Bitmap.createBitmap(b, h, Bitmap.Config.ARGB_8888)
        val leinwand = Canvas(bild)
        val pinsel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val r = h / 2f
        pinsel.color = (s.schrift and 0x00FFFFFF) or 0x26000000
        leinwand.drawRoundRect(RectF(0f, 0f, b.toFloat(), h.toFloat()), r, r, pinsel)
        val mindest = 2f * dichte
        fun streifen(von: Float, bis: Float, farbe: Int) {
            if (bis - von <= 0f) return
            pinsel.color = farbe
            leinwand.drawRoundRect(RectF(von, 0f, maxOf(bis, von + mindest), h.toFloat()), r, r, pinsel)
        }
        if (werte.size == 1) {
            val farbe = when (hoch) { true -> s.hoch; false -> s.runter; null -> (s.schrift and 0x00FFFFFF) or 0xB3000000.toInt() }
            val w = (werte[0].coerceIn(0.0, 1.0) * b).toFloat()
            streifen(0f, if (werte[0] > 0.0) w else 0f, farbe)
        } else {
            val mitte = b / 2f
            val luecke = dichte
            val links = (werte[0].coerceIn(0.0, 1.0) * (mitte - luecke)).toFloat()
            val rechts = (werte[1].coerceIn(0.0, 1.0) * (mitte - luecke)).toFloat()
            if (werte[0] > 0.0) streifen(mitte - luecke - links, mitte - luecke, s.runter)
            if (werte[1] > 0.0) streifen(mitte + luecke, mitte + luecke + rechts, s.hoch)
            pinsel.color = (s.schrift and 0x00FFFFFF) or 0x80000000.toInt()
            leinwand.drawRect(mitte - dichte / 2, 0f, mitte + dichte / 2, h.toFloat(), pinsel)
        }
        return bild
    }

    private val TON_GELB = 0xFFF5A524.toInt()

    // Farbe eines Tons der Grafik (WidgetGrafik): Neutral in Schriftfarbe, Gruen und Rot wie Gewinn und Verlust, Gelb als Warnung,
    // die Werte ab 4 als abgestufte Toene der Schriftfarbe.
    private fun tonFarbe(ton: Int, s: Stil): Int = when (ton) {
        WidgetGrafik.GRUEN -> s.hoch
        WidgetGrafik.GELB -> TON_GELB
        WidgetGrafik.ROT -> s.runter
        WidgetGrafik.NEUTRAL -> s.schrift
        else -> {
            val stufen = intArrayOf(0xFF, 0xCC, 0x99, 0x73, 0x4D, 0x33)
            ((stufen[(ton - 4).coerceIn(0, stufen.size - 1)] shl 24) or (s.schrift and 0x00FFFFFF))
        }
    }

    // Grafik der Karte (Tacho, Ampelreihe, Balken, Kurve) in hoeheDp; in Pixelgroesse des Geraets, gedeckelt.
    private fun grafikBild(c: Context, g: WidgetGrafik, s: Stil, breiteDp: Int, hoeheDp: Int): Bitmap? {
        if (breiteDp < 40 || hoeheDp < 8) return null
        val dichte = bildDichte(c, breiteDp, hoeheDp).coerceAtMost(2f)
        val h = (hoeheDp * dichte).toInt().coerceAtLeast(8)
        return when (g.art) {
            WidgetGrafik.TACHO -> {
                val b = (breiteDp * dichte).toInt().coerceAtLeast(40)
                val bild = Bitmap.createBitmap(b, h, Bitmap.Config.ARGB_8888)
                val leinwand = Canvas(bild)
                val dicke = (h * 0.26f).coerceAtMost(14f * dichte)
                val r = minOf(b / 2f - dicke / 2 - 2, h - dicke / 2 - 2f)
                val mx = b / 2f
                val my = h - 2f - dicke / 4
                val bogen = RectF(mx - r, my - r, mx + r, my + r)
                val pinsel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dicke; strokeCap = Paint.Cap.BUTT }
                val summe = g.werte.sum().takeIf { it > 0 } ?: 1.0
                var von = 0.0
                g.werte.forEachIndexed { i, w ->
                    pinsel.color = tonFarbe(g.ton.getOrElse(i) { WidgetGrafik.NEUTRAL }, s)
                    val start = 180f + (von / summe * 180).toFloat()
                    val breiteGrad = (w / summe * 180).toFloat()
                    leinwand.drawArc(bogen, start + 0.8f, breiteGrad - 1.6f, false, pinsel)
                    von += w
                }
                val winkel = Math.toRadians(180.0 + g.marke.coerceIn(0.0, 1.0) * 180.0)
                val laenge = r - dicke * 0.75f
                val zeiger = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.schrift; strokeWidth = 2.4f * dichte; strokeCap = Paint.Cap.ROUND }
                leinwand.drawLine(mx, my, mx + (laenge * cos(winkel)).toFloat(), my + (laenge * sin(winkel)).toFloat(), zeiger)
                leinwand.drawCircle(mx, my, 3.5f * dichte, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.schrift })
                bild
            }
            WidgetGrafik.AMPELN -> {
                val n = g.ton.size.coerceAtLeast(1)
                // Punkte so gross wie die Hoehe, hoechstens 14 dp, mit Abstand bis 10 dp
                val punkt = minOf(h.toFloat(), 14f * dichte)
                val abstand = minOf(10f * dichte, ((breiteDp * dichte) - n * punkt) / n).coerceAtLeast(2f)
                val breitePx = (n * punkt + (n - 1) * abstand).toInt().coerceAtLeast(1)
                val bild = Bitmap.createBitmap(breitePx, h, Bitmap.Config.ARGB_8888)
                val leinwand = Canvas(bild)
                val farbe = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                g.ton.forEachIndexed { i, t ->
                    farbe.color = tonFarbe(t, s)
                    leinwand.drawCircle(i * (punkt + abstand) + punkt / 2, h / 2f, punkt / 2, farbe)
                }
                bild
            }
            WidgetGrafik.BALKEN -> {
                val b = (breiteDp * dichte).toInt().coerceAtLeast(40)
                val bild = Bitmap.createBitmap(b, h, Bitmap.Config.ARGB_8888)
                val leinwand = Canvas(bild)
                val summe = g.werte.sum().takeIf { it > 0 } ?: 1.0
                val farbe = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                val luecke = 2f * dichte
                var x = 0f
                g.werte.forEachIndexed { i, w ->
                    val breite = (w / summe * b).toFloat()
                    farbe.color = tonFarbe(g.ton.getOrElse(i) { 4 + i }, s)
                    leinwand.drawRoundRect(RectF(x, 0f, (x + breite - luecke).coerceAtLeast(x + 1f), h.toFloat()), h / 2f, h / 2f, farbe)
                    x += breite
                }
                bild
            }
            else -> kurve(c, g.werte, tonFarbe(g.ton.firstOrNull() ?: WidgetGrafik.NEUTRAL, s), breiteDp, hoeheDp)
        }
    }

    // Kurs des Finanzwissens je Sprache, einmal gelesen: Module mit ihren Lektionen.
    @Volatile private var wissenKurs: Pair<String, List<WidgetKartenBau.WissenModul>>? = null

    private fun wissenModule(): List<WidgetKartenBau.WissenModul> {
        val sprache = de.troetenrepublik.app.i18n.I18n.lang.code
        wissenKurs?.takeIf { it.first == sprache }?.let { return it.second }
        val pfad = if (sprache == "de") "finanzwissen/data/kurs.json" else "finanzwissen/data/en/kurs.json"
        val module = runCatching {
            val wurzel = json.parseToJsonElement(Plattform.asset(pfad).decodeToString()).jsonObject
            wurzel["module"]!!.jsonArray.map { m ->
                val o = m.jsonObject
                WidgetKartenBau.WissenModul(
                    o["nr"]!!.jsonPrimitive.content.toInt(), o["titel"]!!.jsonPrimitive.content,
                    o["lektionen"]!!.jsonArray.map { l ->
                        val x = l.jsonObject
                        WidgetKartenBau.WissenLektion(x["id"]!!.jsonPrimitive.content, x["titel"]!!.jsonPrimitive.content, x["minuten"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
                    },
                )
            }
        }.getOrDefault(emptyList())
        if (module.isNotEmpty()) wissenKurs = sprache to module
        return module
    }

    // Karte „Finanzwissen“ aus dem Fortschritt, den die Lernseite meldet (wissenFortschritt), und dem Kurs.
    private fun wissenKarte(c: Context): WidgetKarte? {
        val module = wissenModule().takeIf { it.isNotEmpty() } ?: return null
        val a = ablage(c)
        val fertig = a.getString("wissen_fertig", "").orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        return WidgetKartenBau.wissen(module, fertig, a.getString("wissen_zuletzt", null)?.ifBlank { null }, 0L)
    }

    // Die Lernseite meldet Fortschritt: zuletzt abgeschlossene Lektion und alle abgeschlossenen (durch Komma getrennt).
    fun wissenFortschritt(c: Context, zuletzt: String, fertig: String) {
        val ok = Regex("[A-Za-z0-9,_-]{0,6000}")
        if (!ok.matches(zuletzt) || !ok.matches(fertig)) return
        ablage(c).edit().putString("wissen_zuletzt", zuletzt).putString("wissen_fertig", fertig).apply()
        if (ids(c, WissenWidget::class.java).isNotEmpty()) zeichnen(c)
    }

    private val KACHELN = intArrayOf(R.id.schnell1, R.id.schnell2, R.id.schnell3, R.id.schnell4, R.id.schnell5, R.id.schnell6, R.id.schnell7, R.id.schnell8)
    private val KACHEL_BILDER = intArrayOf(R.id.schnell_bild1, R.id.schnell_bild2, R.id.schnell_bild3, R.id.schnell_bild4, R.id.schnell_bild5, R.id.schnell_bild6, R.id.schnell_bild7, R.id.schnell_bild8)
    private val KACHEL_TEXTE = intArrayOf(R.id.schnell_text1, R.id.schnell_text2, R.id.schnell_text3, R.id.schnell_text4, R.id.schnell_text5, R.id.schnell_text6, R.id.schnell_text7, R.id.schnell_text8)
    private val KACHEL_ZEILEN = intArrayOf(R.id.schnell_zeile1, R.id.schnell_zeile2)

    private val SCHNELL_SYMBOLE = mapOf(
        "search" to R.drawable.ic_widget_search, "orders" to R.drawable.ic_widget_orders, "maerkte" to R.drawable.ic_widget_maerkte,
        "news" to R.drawable.ic_widget_news, "heatmap" to R.drawable.ic_widget_heatmap, "activity" to R.drawable.ic_widget_activity,
        "plans" to R.drawable.ic_widget_plans, "rueckblick" to R.drawable.ic_widget_rueckblick, "depotverlauf" to R.drawable.ic_widget_depotverlauf,
    )

    // Schnellzugriff: die vier gewaehlten Ziele als Kacheln mit einfarbigem Symbol; je nach Breite eine bis vier Kacheln je Zeile,
    // bei Platz in der Hoehe zwei Zeilen (WidgetWahl.kacheln). Mit App-Sperre oeffnet die Seite erst nach dem Entsperren.
    private fun schnell(c: Context, d: WidgetDaten?, id: Int, s: Stil, breite: Int, hoehe: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_schnell)
        grundSetzen(v, s)
        val texte = d?.schnell.orEmpty()
        val ziele = schnellZiele(c, id)
        val spalten = WidgetWahl.spalten(breite)
        val anzahl = WidgetWahl.kacheln(breite, hoehe)
        for (zeile in 0 until 2) {
            var belegt = false
            for (k in 0 until 4) {
                val platz = zeile * 4 + k
                val t = zeile * spalten + k
                if (k >= spalten) { v.setViewVisibility(KACHELN[platz], View.GONE); continue }
                if (t >= anzahl || t >= ziele.size) { v.setViewVisibility(KACHELN[platz], View.INVISIBLE); continue }
                belegt = true
                val ziel = ziele[t]
                v.setViewVisibility(KACHELN[platz], View.VISIBLE)
                v.setImageViewResource(KACHEL_BILDER[platz], SCHNELL_SYMBOLE[ziel] ?: R.drawable.ic_widget_search)
                v.setInt(KACHEL_BILDER[platz], "setColorFilter", s.schrift)
                v.setTextViewText(KACHEL_TEXTE[platz], d?.schnellNamen?.get(ziel) ?: texte.getOrNull(WidgetDaten.SCHNELL_ZIELE.indexOf(ziel)) ?: "")
                v.setTextColor(KACHEL_TEXTE[platz], s.schrift)
                v.setOnClickPendingIntent(KACHELN[platz], oeffnen(c, ziel))
            }
            v.setViewVisibility(KACHEL_ZEILEN[zeile], if (belegt) View.VISIBLE else View.GONE)
        }
        return v
    }

    // Hintergrundpruefung einplanen, solange Widgets da sind; sonst beenden.
    fun planen(c: Context) {
        val wm = WorkManager.getInstance(c)
        if (!vorhanden(c)) { wm.cancelUniqueWork("widgets"); return }
        val auftrag = PeriodicWorkRequestBuilder<WidgetPruefung>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork("widgets", ExistingPeriodicWorkPolicy.KEEP, auftrag)
    }
}

abstract class TroeteWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Widgets.NEU_LADEN -> { Widgets.jetztLaden(context); return }
            Widgets.GLOBUS_DREHEN -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) Widgets.globusDrehen(context, id, intent.getIntExtra(Widgets.EXTRA_RICHTUNG, 0))
                return
            }
            Widgets.UMSCHALTEN -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) Widgets.umschalten(context, id)
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Aktuellen Stand aus der App nachladen, sonst bleibt der zuletzt abgelegte.
        runCatching { (context.applicationContext as? TroeteApp)?.container?.state?.widgetsNachladen() }
        Widgets.zeichnen(context)
        Widgets.planen(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        Widgets.zeichnen(context, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = Widgets.vergessen(context, appWidgetIds)

    override fun onDisabled(context: Context) = Widgets.planen(context)
}

// Widget „Depot“: Wert der Wertpapiere, Aenderung im gewaehlten Zeitraum und (ab zwei Zeilen Hoehe) der Verlauf.
class DepotWidget : TroeteWidget()

// Widget „Werte“: Watchlist, Depot-Positionen oder eine eigene Liste, rollbar.
class WerteWidget : TroeteWidget()

// Widget „Einzelwert“: ein Wert gross mit Kurs, Tagesaenderung und Tagesverlauf.
class EinzelwertWidget : TroeteWidget()

// Widget „Top & Flop“: die besten und schlechtesten Positionen des Tages.
class MoverWidget : TroeteWidget()

// Widget „Dividenden“: erhalten im Jahr, erwartet in zwoelf Monaten, die naechsten Zahlungen.
class DividendenWidget : TroeteWidget()

// Widget „Schnellzugriff“: Suche, Orders, Heatmap und News.
class SchnellWidget : TroeteWidget()

// Widget „Märkte“: DAX, S&P 500, Nasdaq 100, Euro Stoxx 50, Gold und Bitcoin.
class MaerkteWidget : TroeteWidget()

// Widget „Stimmung“: Angst-und-Gier-Barometer fuer Aktien oder Krypto als Tacho.
class StimmungWidget : TroeteWidget()

// Widget „News“: Schlagzeilen zu den eigenen Positionen, rollbar.
class NewsWidget : TroeteWidget()

// Widget „Heatmap“ (0.68): ein Markt, das Depot oder die Watchlist als Kacheln mit Kuerzel und Aenderung.
class HeatmapWidget : TroeteWidget()

// Widget „Globus“ (0.69): Laender eingefaerbt nach Boerse heute, Wachstum, Ausblick oder Inflation.
class GlobusWidget : TroeteWidget()

// Kategorie-Widgets (0.73): je Kategorie der App eine Karte mit wenigen, grossen Angaben.
// Widget „Ausblick“: Richtung fuer Aktien nach Wochen und Monaten mit den gewichtigsten Gruenden.
class AusblickWidget : TroeteWidget()

// Widget „Bewegungen“: groesste Gewinner und Verlierer des Tages.
class BewegungenWidget : TroeteWidget()

// Widget „Trends“: meistbesprochene Werte mit Richtung.
class TrendsWidget : TroeteWidget()

// Widget „Durchbrueche“: neueste Durchbrueche mit Thema, sonst der naechste erwartete Meilenstein.
class DurchbruecheWidget : TroeteWidget()

// Widget „Aktienfinder“: bestbewertete Werte des gewaehlten Markts.
class AktienfinderWidget : TroeteWidget()

// Widget „Copytrading“: neueste Geschaefte der gefolgten Personen, sonst die Rangliste.
class CopytradingWidget : TroeteWidget()

// Widget „Prognosemaerkte“: wichtigste Wetten mit Wahrscheinlichkeit und Veraenderung.
class PrognoseWidget : TroeteWidget()

// Widget „Orders“: offene Orders und Sparplaene mit naechstem Termin.
class OrdersWidget : TroeteWidget()

// Widget „Analytics“: Rendite, groesster Rueckgang und groesste Position.
class AnalyticsWidget : TroeteWidget()

// Widget „Finanzwissen“: naechste Lektion des begonnenen Moduls.
class WissenWidget : TroeteWidget()

// Seit 0.77: Widgets zu Unterseiten und Werkzeugen.
// Widget „Lage“: die drei wichtigsten Ereignisse der Weltlage.
class LageWidget : TroeteWidget()

// Widget „Frühwarnzeichen“: Ampelreihe mit der Zahl der roten und gelben Zeichen.
class FruehwarnungWidget : TroeteWidget()

// Widget „Marktphase“: Bullen- oder Bärenmarkt mit Abstand zum Hoch.
class MarktphaseWidget : TroeteWidget()

// Widget „Blasen-Gefahr“: Stufe als Tacho, rote Anzeichen und die stärksten mit Wert.
class BlasenWidget : TroeteWidget()

// Widget „Termine“: die nächsten Dividenden, Wirtschaftstermine, Quartalszahlen oder Börsengänge.
class TermineWidget : TroeteWidget()

// Widget „Performance“: Rendite seit Jahresbeginn mit Kurve, Zinsfuß und größter Rückgang.
class PerformanceWidget : TroeteWidget()

// Widget „Depot-Check“: Regeln im grünen Bereich als Ampelreihe, die schlechtesten Befunde.
class DepotcheckWidget : TroeteWidget()

// Widget „Streuung“: größte Position und Anlageklassen als Balken.
class StreuungWidget : TroeteWidget()

// Widget „Umschichten“: Abweichung von den Zielgewichten je Kategorie.
class UmschichtenWidget : TroeteWidget()

// Widget „Crash-Test“: der schlimmste Fall des Depots in den Krisen.
class CrashtestWidget : TroeteWidget()

// Seit 0.78
class ThemenWidget : TroeteWidget()
class IndexBewegerWidget : TroeteWidget()
class SektorenWidget : TroeteWidget()
class KongressFlowWidget : TroeteWidget()
class StillhalterWidget : TroeteWidget()
class SignaleWidget : TroeteWidget()
class VolatilitaetWidget : TroeteWidget()

// Seit 0.77 (AD8)
class MeistgekauftWidget : TroeteWidget()
class HaeuserWidget : TroeteWidget()

// Seit 0.77 (Abschnitte)
class KonvergenzWidget : TroeteWidget()
class InsiderSeltenWidget : TroeteWidget()
class InsiderSimulatorWidget : TroeteWidget()
class VorschlagsdepotWidget : TroeteWidget()
class BrancheWidget : TroeteWidget()
class WarnzeichenWidget : TroeteWidget()
class PraesidialWidget : TroeteWidget()

// Zeilen der rollbaren Listen. Liest den abgelegten Inhalt und die Einstellungen des Widgets.
class WidgetListenDienst : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return Fabrik(applicationContext, id)
    }

    private class Fabrik(private val c: Context, private val id: Int) : RemoteViewsFactory {
        private var zeilen: List<Widgets.Listenzeile> = emptyList()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            zeilen = runCatching { Widgets.listenInhalt(c, id, Widgets.art(c, id), Widgets.laden(c)) }.getOrDefault(emptyList())
        }

        override fun onDestroy() { zeilen = emptyList() }

        override fun getCount(): Int = zeilen.size

        override fun getViewAt(position: Int): RemoteViews =
            zeilen.getOrNull(position)?.let { Widgets.zeilenAnsicht(c, id, it) } ?: RemoteViews(c.packageName, R.layout.widget_listenzeile)

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 2

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}

// Nach einem Update der App: Widgets sofort neu zeichnen und frische Kurse holen, sonst bleiben sie bis zur naechsten
// Pruefung leer oder alt.
class WidgetsNachUpdate : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Widgets.zeichnen(context)
        Widgets.planen(context)
        Widgets.jetztLaden(context)
    }
}

// Neue Kurse fuer die Widgets bei geschlossener App (etwa alle 30 Minuten, nur mit Netz).
class WidgetPruefung(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? TroeteApp ?: return Result.success()
        if (!Widgets.vorhanden(app)) return Result.success()
        runCatching { app.container.state.widgetsAuffrischen() }
        return Result.success()
    }
}
