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
import de.troetenrepublik.app.finderImHintergrund
import java.util.concurrent.TimeUnit

// Laedt bei geschlossener App etwa alle zwoelf Stunden den zuletzt gewaehlten Markt des Aktienfinders nach, vor
// allem die Jahresabschluesse. Ohne gewaehlten Markt endet er sofort.
class FinderVorladen(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TroeteApp ?: return Result.success()
        runCatching { app.container.state.finderImHintergrund() }
        return Result.success()
    }

    companion object {
        fun planen(context: Context) {
            val auftrag = PeriodicWorkRequestBuilder<FinderVorladen>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("finder-vorladen", ExistingPeriodicWorkPolicy.KEEP, auftrag)
        }
    }
}
