package de.troetenrepublik.app.plattform

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.troetenrepublik.app.TroeteApp
import java.util.concurrent.TimeUnit

// Laeuft etwa alle sechs Stunden bei bestehender Verbindung, auch bei geschlossener App.
// Ohne Personen mit Glocke endet sie sofort.
class CopyPruefung(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TroeteApp ?: return Result.success()
        runCatching { app.container.state.copyPruefen() }
        return Result.success()
    }

    companion object {
        fun planen(context: Context) {
            val auftrag = PeriodicWorkRequestBuilder<CopyPruefung>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("copy-pruefung", ExistingPeriodicWorkPolicy.KEEP, auftrag)
        }
    }
}
