package de.troetenrepublik.app.plattform

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.troetenrepublik.app.TroeteApp
import de.troetenrepublik.app.engine.Tagesrueckblick
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// Erstellt den Tagesrueckblick zur gewaehlten Uhrzeit auch bei geschlossener App und meldet ihn; ohne Netz
// wartet der Auftrag, bis das Geraet online ist. Verpasstes holt die Pruefung mit den Nachrichten (NewsPruefung) nach.
class RueckblickPlanung(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TroeteApp ?: return Result.success()
        runCatching { app.container.state.rueckblickPruefen() }
        // Naechste Ausfuehrung planen; der laufende Auftrag bricht sich nicht selbst ab
        val s = runCatching { app.container.settings.flow.first() }.getOrNull()
        if (s != null && s.tagesrueckblick) einreihen(applicationContext, s.rueckblickMinute, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val NAME = "rueckblick-uhrzeit"

        // Planen; aus heisst: Auftrag loeschen. ersetzen nur bei einer Aenderung, beim Start bleibt ein geplanter
        // (oder gerade laufender) Auftrag stehen.
        fun planen(context: Context, an: Boolean, ab: Int, ersetzen: Boolean = true) {
            if (an) einreihen(context, ab, if (ersetzen) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
            else WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        private fun einreihen(context: Context, ab: Int, policy: ExistingWorkPolicy) {
            val jetzt = System.currentTimeMillis()
            val auftrag = OneTimeWorkRequestBuilder<RueckblickPlanung>()
                .setInitialDelay(maxOf(0L, Tagesrueckblick.naechsteAusfuehrung(jetzt, ab) - jetzt), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, auftrag)
        }
    }
}
