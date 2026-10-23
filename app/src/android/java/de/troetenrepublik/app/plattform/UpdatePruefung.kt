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

// Neue Fassung der App auch bei geschlossener App: Android fuehrt die Pruefung etwa einmal am Tag aus,
// sobald das Geraet online ist. Ob sie faellig ist, entscheidet de.troetenrepublik.app.AppState.updatePruefen.
class UpdatePruefung(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TroeteApp ?: return Result.success()
        runCatching { app.container.state.updatePruefen() }
        return Result.success()
    }

    companion object {
        fun planen(context: Context) {
            val auftrag = PeriodicWorkRequestBuilder<UpdatePruefung>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(2, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("update-pruefung", ExistingPeriodicWorkPolicy.KEEP, auftrag)
        }
    }
}
