package de.troetenrepublik.app.plattform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.troetenrepublik.app.MainActivity
import de.troetenrepublik.app.R
import de.troetenrepublik.app.engine.Stichpunkte
import de.troetenrepublik.app.i18n.S
import java.util.concurrent.atomic.AtomicInteger

// Eigene Kanaele je Art der Mitteilung, damit sie sich einzeln stummschalten lassen.
class Notifier(private val context: Context) {

    companion object {
        const val CHANNEL_TRADES = "trades"
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_CASH = "cash"
        const val CHANNEL_UPDATES = "updates"
        const val CHANNEL_NEWS = "news"
        const val CHANNEL_RUECKBLICK = "rueckblick"
        const val CHANNEL_COPY = "copytrading"
        const val CHANNEL_WELT = "welt"
    }

    @Volatile var enabled: Boolean = true

    // Kanaele, deren Art in den Einstellungen ausgeschaltet ist (0.68).
    @Volatile var gesperrt: Set<String> = emptySet()

    // Auf dem Desktop oeffnet ein Klick auf eine Meldung damit eine Seite; auf Android traegt die Mitteilung das Ziel selbst.
    @Volatile var beiZiel: (String) -> Unit = {}

    private val ids = AtomicInteger(0)

    init { createChannels() }

    private fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TRADES, S.channelTrades, NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, S.priceAlerts, NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_CASH, S.channelCash, NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_UPDATES, S.channelUpdates, NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_NEWS, S.news, NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_RUECKBLICK, S.rbTitel, NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_COPY, S.copyTitle, NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WELT, S.mtWelt, NotificationManager.IMPORTANCE_DEFAULT))
    }

    // Beim Sprachwechsel die Kanalnamen nachziehen.
    fun refreshChannelNames() = createChannels()

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // canNotify prueft die Berechtigung; Lint erkennt das durch die Hilfsfunktion nicht. Mit link oeffnet ein Tipp
    // die Adresse im Browser, mit ziel diese Seite der App, sonst die App.
    @SuppressLint("MissingPermission")
    fun notify(channel: String, title: String, text: String, link: String? = null, ziel: String? = null) {
        if (!enabled || channel in gesperrt || !canNotify()) return
        // Links aus Feeds nur als Webadresse, kein intent:, market: oder anderes Schema
        val launch = link?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            ?.let { Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it)).addCategory(Intent.CATEGORY_BROWSABLE) }
            ?: ziel?.let { z ->
                Intent(context, MainActivity::class.java).putExtra(Widgets.ZIEL, z).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        // Eigene Nummer je Mitteilung: mit einer gemeinsamen ersetzte FLAG_UPDATE_CURRENT das Ziel aelterer Mitteilungen
        val nummer = ids.updateAndGet { de.troetenrepublik.app.engine.Mitteilungsnummer.naechste(it, System.currentTimeMillis() / 1000L) }
        val pending = PendingIntent.getActivity(
            context, nummer, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Stichpunkte: Punkt am Rand, Folgezeilen haengend eingerueckt um die Breite von „• “
        val anzeige = mitEinzug(Stichpunkte.fuerMitteilung(text))
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_troete)
            .setContentTitle(title)
            .setContentText(anzeige)
            .setStyle(NotificationCompat.BigTextStyle().bigText(anzeige))
            .setAutoCancel(true)
            .setContentIntent(pending)
            // Auf dem Sperrbildschirm nur der Titel: Betraege und Schlagzeilen erst nach dem Entsperren
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_troete).setContentTitle(title)
                    .setAutoCancel(true).setContentIntent(pending).build(),
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(nummer, n) }
    }

    // Haengender Einzug je Stichpunkt-Absatz; die Breite misst „•\u00A0“ in der Groesse des Mitteilungstexts (14 sp).
    private fun mitEinzug(text: String): CharSequence {
        val bereiche = Stichpunkte.punktAbsaetze(text)
        if (bereiche.isEmpty()) return text
        val metrik = context.resources.displayMetrics
        val stift = android.text.TextPaint().apply {
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 14f, metrik)
        }
        val breite = kotlin.math.ceil(stift.measureText("•\u00A0")).toInt()
        val s = android.text.SpannableString(text)
        for (b in bereiche) {
            // Absatz-Spans reichen bis einschliesslich Zeilenumbruch oder bis zum Textende
            val ende = minOf(text.length, b.last + 2)
            s.setSpan(android.text.style.LeadingMarginSpan.Standard(0, breite), b.first, ende, android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        return s
    }
}
