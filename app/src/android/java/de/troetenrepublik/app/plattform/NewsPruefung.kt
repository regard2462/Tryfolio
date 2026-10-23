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

// Prueft bei geschlossener App etwa alle 30 Minuten auf neue Schlagzeilen, sobald das Geraet online ist.
// Sind diese Mitteilungen aus, ruft sie keine Feeds ab.
class NewsPruefung(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TroeteApp ?: return Result.success()
        runCatching { app.container.state.newsMelden(laden = true) }
        // Trendverlauf von Reddit
        runCatching { app.container.state.trendsMitschreiben() }
        // Tagesrueckblick am Abend, auch bei geschlossener App
        runCatching { app.container.state.rueckblickPruefen() }
        return Result.success()
    }

    companion object {
        fun planen(context: Context) {
            val auftrag = PeriodicWorkRequestBuilder<NewsPruefung>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("news-pruefung", ExistingPeriodicWorkPolicy.KEEP, auftrag)
        }
    }
}
