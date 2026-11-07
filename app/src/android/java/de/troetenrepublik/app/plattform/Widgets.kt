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
import de.troetenrepublik.app.data.WidgetNachricht
import de.troetenrepublik.app.data.WidgetWert
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLesen
import de.troetenrepublik.app.engine.GlobusModus
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.Treemap
import kotlinx.serialization.json.Json
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

    // Grund des Widgets: deckend, durchscheinend oder ohne.
    const val GRUND_VOLL = "voll"
    const val GRUND_HALB = "halb"
    const val GRUND_LEER = "leer"

    // Aktion fuer „jetzt neu laden“ beim Tipp auf die Standzeile.
    const val NEU_LADEN = "de.troetenrepublik.app.WIDGETS_NEU_LADEN"

    // Aktion fuer den Tipp auf Titel oder Wert: zwischen Betrag und Prozent umschalten.
    const val UMSCHALTEN = "de.troetenrepublik.app.WIDGET_UMSCHALTEN"

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
        runCatching { AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, klasse)) }.getOrNull() ?: IntArray(0)

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
        val name = runCatching { AppWidgetManager.getInstance(c).getAppWidgetInfo(id)?.provider?.className }.getOrNull() ?: return null
        return KLASSEN.entries.firstOrNull { it.key.name == name }?.value
    }

    private fun klasse(art: String): Class<*>? = KLASSEN.entries.firstOrNull { it.value == art }?.key

    // ------------------------------------------------------------ Einstellungen je Widget

    private val EINSTELLUNGEN = listOf("quelle", "einzel", "grund", "schrift", "zeitraum", "nurProzent", "betrag", "liste", "name", "sortiert", "barometer", "news", "schnell", "heatmapQuelle", "heatmapZeit", "globusModus", "globusMitte")

    private fun lesen(c: Context, id: Int, name: String): String? = ablage(c).getString("${name}_$id", null)

    private fun schreiben(c: Context, id: Int, name: String, wert: String?) {
        val e = ablage(c).edit()
        if (wert.isNullOrEmpty()) e.remove("${name}_$id") else e.putString("${name}_$id", wert)
        e.apply()
    }

    fun quelle(c: Context, id: Int): String = lesen(c, id, "quelle") ?: QUELLE_WATCHLIST
    fun quelleSetzen(c: Context, id: Int, quelle: String) = schreiben(c, id, "quelle", quelle)

    fun einzel(c: Context, id: Int): String? = lesen(c, id, "einzel") ?: vorgemerkt(c, id)

    // Einzelwert-Widget aus der App anlegen (langes Druecken auf einen Wert): der Startbildschirm fragt nach, das neue
    // Widget uebernimmt das Symbol beim ersten Zeichnen. Nur Widgets, die es beim Anfragen noch nicht gab, uebernehmen es.
    fun einzelAnheften(symbol: String): Boolean {
        val c = Plattform.kontext
        val m = AppWidgetManager.getInstance(c) ?: return false
        if (!m.isRequestPinAppWidgetSupported) return false
        ablage(c).edit()
            .putString("anheften_symbol", symbol)
            .putLong("anheften_zeit", System.currentTimeMillis())
            .putString("anheften_bekannt", ids(c, EinzelwertWidget::class.java).joinToString(","))
            .apply()
        val ok = runCatching { m.requestPinAppWidget(ComponentName(c, EinzelwertWidget::class.java), null, null) }.getOrDefault(false)
        if (!ok) ablage(c).edit().remove("anheften_symbol").apply()
        return ok
    }

    private fun vorgemerkt(c: Context, id: Int): String? {
        val a = ablage(c)
        val symbol = a.getString("anheften_symbol", null) ?: return null
        if (System.currentTimeMillis() - a.getLong("anheften_zeit", 0L) > 180_000L) {
            a.edit().remove("anheften_symbol").apply()
            return null
        }
        if (id.toString() in a.getString("anheften_bekannt", "").orEmpty().split(',')) return null
        if (art(c, id) != ART_EINZEL) return null
        a.edit().remove("anheften_symbol").apply()
        schreiben(c, id, "einzel", symbol)
        jetztLaden(c)
        return symbol
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
    fun barometer(c: Context, id: Int): String = lesen(c, id, "barometer") ?: WidgetDaten.STIMMUNG_AKTIEN
    fun barometerSetzen(c: Context, id: Int, schluessel: String) = schreiben(c, id, "barometer", schluessel)

    // Einfaerbung (GlobusModus.name) und Blickmitte (MITTEN) des Widgets „Globus“ (0.69).
    fun globusModus(c: Context, id: Int): GlobusModus =
        lesen(c, id, "globusModus")?.let { n -> GlobusModus.entries.firstOrNull { it.name == n } } ?: GlobusModus.BOERSE
    fun globusModusSetzen(c: Context, id: Int, modus: GlobusModus) = schreiben(c, id, "globusModus", modus.name)
    val MITTEN = listOf("europa" to (12.0 to 42.0), "amerika" to (-80.0 to 15.0), "asien" to (95.0 to 30.0))
    fun globusMitte(c: Context, id: Int): String = lesen(c, id, "globusMitte")?.takeIf { k -> MITTEN.any { it.first == k } } ?: "europa"
    fun globusMitteSetzen(c: Context, id: Int, mitte: String) = schreiben(c, id, "globusMitte", mitte)

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
        val m = AppWidgetManager.getInstance(c) ?: return
        val d = laden(c)
        for ((klasse, art) in KLASSEN) for (id in ids(c, klasse)) {
            runCatching { m.updateAppWidget(id, ansicht(c, m, art, id, d)) }
            if (art in LISTEN) runCatching { listeNeu(m, id) }
        }
    }

    // Nach geaenderter Groesse oder Einstellung.
    fun zeichnen(c: Context, id: Int) {
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
        return o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) to o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    }

    private fun ansicht(c: Context, m: AppWidgetManager, art: String, id: Int, d: WidgetDaten?): RemoteViews {
        val (b, h) = groesse(m, id)
        val breite = if (b > 0) b else 180
        val hoehe = if (h > 0) h else 110
        val s = stil(c, id)
        return when (art) {
            ART_DEPOT -> depot(c, d, id, s, breite, hoehe)
            ART_WERTE, ART_MAERKTE, ART_NEWS -> liste(c, d, art, id, s)
            ART_EINZEL -> einzelwert(c, d, id, s, breite, hoehe)
            ART_MOVER -> mover(c, d, id, s, hoehe)
            ART_DIVIDENDEN -> dividenden(c, d, s, hoehe)
            ART_STIMMUNG -> stimmung(c, d, id, s, breite, hoehe)
            ART_HEATMAP -> heatmap(c, d, id, s, breite, hoehe)
            ART_GLOBUS -> globus(c, d, id, s, breite, hoehe)
            else -> schnell(c, d, id)
        }.also { v -> if (art != ART_SCHNELL) runCatching { v.setOnClickPendingIntent(R.id.widget_stand, neuLaden(c)) } }
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
            v.setTextViewText(KURSE[i], if (ohneKurs) "" else w.kurs)
            v.setTextColor(NAMEN[i], s.schrift)
            v.setTextColor(KURSE[i], s.kurs)
            if (mitProzent) {
                v.setTextViewText(PROZENT[i], w.aenderung)
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
        val zeit = zeitraum(c, id)
        val z = d?.depotZeitraeume?.get(zeit)
        val nurProzent = nurProzent(c, id)
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
                v.setTextViewText(R.id.widget_wert, if (nurProzent) "–" else d.depotWert)
                v.setTextViewText(R.id.widget_aenderung, "")
            }
            nurProzent -> {
                // Nur Prozent: keine Betraege, die grosse Zahl ist die Aenderung
                v.setTextViewText(R.id.widget_wert, z.prozent)
                v.setTextColor(R.id.widget_wert, if (z.hoch) s.hoch else s.runter)
                v.setTextViewText(R.id.widget_aenderung, z.titel)
                v.setTextColor(R.id.widget_aenderung, s.schrift2)
            }
            else -> {
                v.setTextViewText(R.id.widget_wert, d.depotWert)
                v.setTextViewText(R.id.widget_aenderung, listOf(z.betrag, z.prozent).filter { it.isNotBlank() }.joinToString(" · "))
                v.setTextColor(R.id.widget_aenderung, if (z.hoch) s.hoch else s.runter)
            }
        }
        val offen = d != null && !gesperrt(d) && z != null
        kurveSetzen(c, v, if (offen) z!!.kurve else emptyList(), if (z?.hoch != false) s.hoch else s.runter, breite, hoehe)
        // Der Zeitraum steht vor dem Stand, solange er nicht „heute“ ist
        v.setTextViewText(R.id.widget_stand, listOfNotNull(z?.titel?.takeIf { zeit != WidgetDaten.ZEIT_HEUTE }, d?.stand?.ifBlank { null }).joinToString(" · "))
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
        @Suppress("DEPRECATION")
        v.setRemoteAdapter(R.id.widget_liste, dienst)
        v.setEmptyView(R.id.widget_liste, R.id.widget_hinweis)
        v.setPendingIntentTemplate(R.id.widget_liste, listenVorlage(c))
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
        data class Wert(val w: WidgetWert) : Listenzeile
        data class Nachricht(val n: WidgetNachricht) : Listenzeile
    }

    // Inhalt der Liste eines Widgets, geordnet wie eingestellt.
    fun listenInhalt(c: Context, id: Int, art: String?, d: WidgetDaten?): List<Listenzeile> {
        if (d == null || gesperrt(d)) return emptyList()
        if (art == ART_NEWS) return newsListe(d, newsArt(c, id)).map { Listenzeile.Nachricht(it) }
        val werte = when {
            art == ART_MAERKTE -> d.maerkte
            quelle(c, id) == QUELLE_DEPOT -> d.bestand
            quelle(c, id) == QUELLE_LISTE -> liste(c, id).mapNotNull { d.listenWerte[it] }
            else -> d.werte
        }
        return WidgetDaten.ordnen(werte, art != ART_MAERKTE && sortiert(c, id)).map { Listenzeile.Wert(it) }
    }

    // Eine Listenzeile als Ansicht (fuer WidgetListenDienst).
    fun zeilenAnsicht(c: Context, id: Int, z: Listenzeile): RemoteViews {
        val s = stil(c, id)
        return when (z) {
            is Listenzeile.Wert -> RemoteViews(c.packageName, R.layout.widget_listenzeile).apply {
                val w = z.w
                setTextViewText(R.id.zeile_name, w.name)
                setTextViewText(R.id.zeile_kurs, w.kurs)
                setTextViewText(R.id.zeile_proz, if (betrag(c, id) && w.betrag.isNotBlank()) w.betrag else w.aenderung)
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

    private fun mover(c: Context, d: WidgetDaten?, id: Int, s: Stil, hoehe: Int): RemoteViews {
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
        val ohneBetrag = nurProzent(c, id)
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
        farbe(v, s.schrift2, R.id.div_erhalten_text, R.id.div_erwartet_text)
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
        val platz = ((hoehe - 120) / 22).coerceIn(0, 4)
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

    private val ALLE_ZEILEN = intArrayOf(R.id.alle1, R.id.alle2, R.id.alle3)
    private val ALLE_NAMEN = intArrayOf(R.id.alle_name1, R.id.alle_name2, R.id.alle_name3)
    private val ALLE_STUFEN = intArrayOf(R.id.alle_stufe1, R.id.alle_stufe2, R.id.alle_stufe3)
    private val ALLE_WERTE = intArrayOf(R.id.alle_wert1, R.id.alle_wert2, R.id.alle_wert3)

    // Stimmung: Tacho von extremer Angst bis extremer Gier, darunter Wert und Stufe. Mit der Wahl „Alle“ (0.69) stehen
    // alle Barometer kompakt untereinander: Name, Stufe und Wert, gefaerbt nach Stufe; so viele Zeilen, wie in die Hoehe passen.
    private fun stimmung(c: Context, d: WidgetDaten?, id: Int, s: Stil, breite: Int, hoehe: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_stimmung)
        grundSetzen(v, s)
        farbe(v, s.schrift, R.id.widget_wert)
        farbe(v, s.schrift3, R.id.widget_stand)
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, WidgetZiel.STIMMUNG))
        val wahl = barometer(c, id)
        val alle = if (wahl == WidgetDaten.STIMMUNG_ALLE) WidgetDaten.STIMMUNG_REIHE.mapNotNull { d?.stimmung?.get(it) } else emptyList()
        val b = if (wahl == WidgetDaten.STIMMUNG_ALLE) null else d?.stimmung?.get(wahl)
        v.setViewVisibility(R.id.widget_alle, if (alle.isNotEmpty()) View.VISIBLE else View.GONE)
        if (alle.isNotEmpty()) {
            v.setViewVisibility(R.id.widget_wert, View.GONE)
            v.setViewVisibility(R.id.widget_aenderung, View.GONE)
            v.setViewVisibility(R.id.widget_tacho, View.GONE)
            val anzahl = ((hoehe - 56) / 30).coerceIn(1, 3)
            for (i in ALLE_ZEILEN.indices) {
                val e = alle.getOrNull(i)?.takeIf { i < anzahl }
                if (e == null) { v.setViewVisibility(ALLE_ZEILEN[i], View.GONE); continue }
                val farbe = STUFEN[e.stufeNr.coerceIn(0, 4)]
                v.setViewVisibility(ALLE_ZEILEN[i], View.VISIBLE)
                v.setTextViewText(ALLE_NAMEN[i], e.titel)
                v.setTextViewText(ALLE_STUFEN[i], e.stufe)
                v.setTextViewText(ALLE_WERTE[i], e.wert.toString())
                v.setTextColor(ALLE_NAMEN[i], s.schrift)
                v.setTextColor(ALLE_STUFEN[i], farbe)
                v.setTextColor(ALLE_WERTE[i], farbe)
            }
            v.setTextViewText(R.id.widget_stand, d?.stand.orEmpty())
            return v
        }
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
            tacho(c, b.wert, s, breite - 28, (hoehe - 70).coerceIn(30, 120))?.let {
                v.setImageViewBitmap(R.id.widget_tacho, it)
                v.setViewVisibility(R.id.widget_tacho, View.VISIBLE)
            } ?: v.setViewVisibility(R.id.widget_tacho, View.GONE)
        }
        // Name des Barometers und Quelle direkt bei den Daten (Bedingung von alternative.me), dann der Stand
        v.setTextViewText(R.id.widget_stand, if (b != null) listOf(b.titel, b.quelle, d?.stand.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ") else "")
        return v
    }

    // Farben der fuenf Stufen von extremer Angst bis extremer Gier.
    private val STUFEN = intArrayOf(0xFFFF3D47.toInt(), 0xFFF5A524.toInt(), 0xFF9BA1A6.toInt(), 0xFF7FD17A.toInt(), 0xFF30A46C.toInt())

    // Halbkreis in fuenf Abschnitten mit Zeiger auf wert (0 bis 100).
    private fun tacho(c: Context, wert: Int, s: Stil, breiteDp: Int, hoeheDp: Int): Bitmap? {
        if (breiteDp <= 0 || hoeheDp <= 0) return null
        val dichte = c.resources.displayMetrics.density.coerceAtMost(2f)
        val h = (hoeheDp * dichte).toInt().coerceIn(30, 220)
        val b = (minOf(breiteDp * dichte, h * 2f)).toInt().coerceIn(60, 440)
        val bild = Bitmap.createBitmap(b, h, Bitmap.Config.ARGB_8888)
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
    private val HEAT_DUNKEL_HOCH = 0xFF1A7F37.toInt()
    private val HEAT_DUNKEL_RUNTER = 0xFFD92633.toInt()
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
        h.kacheln.forEachIndexed { i, k ->
            val r = rechtecke.getOrNull(i) ?: return@forEachIndexed
            if (r.w <= 2 * luecke || r.h <= 2 * luecke) return@forEachIndexed
            val farbe = HeatmapBeschriftung.farbe(k.aenderung, h.voll, hoch, runter, neutral)
            fuellung.color = farbe
            leinwand.drawRoundRect(RectF(r.x + luecke, r.y + luecke, r.x + r.w - luecke, r.y + r.h - luecke), radius, radius, fuellung)
            val prozente = listOf(k.prozent, k.prozentKurz).filter { it.isNotBlank() }
            val wahl = HeatmapBeschriftung.waehlen(
                r.w, r.h, innen,
                HeatmapBeschriftung.Masse(masz(gross, k.kuerzel), masz(klein, k.kuerzel), prozente.map { masz(proz, it) }),
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
        if (!voll) grundSetzen(v, s)
        farbe(v, s.schrift2, R.id.widget_hinweis)
        farbe(v, s.schrift2, R.id.widget_stand)
        v.setOnClickPendingIntent(R.id.widget_wurzel, oeffnen(c, WidgetZiel.GLOBUS))
        val modus = globusModus(c, id)
        val werte = d?.globus?.get(modus.name)?.takeIf { !gesperrt(d) }
        val karte = karte()
        val mitte = MITTEN.firstOrNull { it.first == globusMitte(c, id) }?.second ?: MITTEN.first().second
        val dichte = bildDichte(c, breite.coerceAtLeast(1), hoehe.coerceAtLeast(1))
        val bild = if (karte != null && breite > 20 && hoehe > 20) {
            GlobusBild.zeichne(
                karte, (breite * dichte).toInt(), (hoehe * dichte).toInt(), mitte.first, mitte.second,
                { l -> GlobusFarbe.farbe(modus, werte?.get(l.a3), HOCH, RUNTER, GLOBUS_NEUTRAL) },
                if (voll) GlobusFarbe.HINTERGRUND else 0,
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

    // Pixel je dp fuer ein Bild in voller Aufloesung: die Dichte des Geraets, bei mehr Pixeln als der Bildschirm hat verkleinert.
    private fun bildDichte(c: Context, breiteDp: Int, hoeheDp: Int): Float {
        val m = c.resources.displayMetrics
        val flaeche = maxOf(m.widthPixels.toFloat() * m.heightPixels, 1f)
        return minOf(m.density, kotlin.math.sqrt(flaeche / (breiteDp.toFloat() * hoeheDp).coerceAtLeast(1f)))
    }

    private val KACHELN = intArrayOf(R.id.schnell1, R.id.schnell2, R.id.schnell3, R.id.schnell4)
    private val KACHEL_TEXTE = intArrayOf(R.id.schnell_text1, R.id.schnell_text2, R.id.schnell_text3, R.id.schnell_text4)

    // Schnellzugriff: vier Kacheln; mit App-Sperre oeffnet die Seite erst nach dem Entsperren.
    private fun schnell(c: Context, d: WidgetDaten?, id: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_schnell)
        val texte = d?.schnell.orEmpty()
        schnellZiele(c, id).forEachIndexed { i, ziel ->
            val text = d?.schnellNamen?.get(ziel) ?: texte.getOrNull(WidgetDaten.SCHNELL_ZIELE.indexOf(ziel)) ?: ""
            v.setTextViewText(KACHEL_TEXTE[i], text)
            v.setOnClickPendingIntent(KACHELN[i], oeffnen(c, ziel))
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
