package dev.handheld.launcher.rom

import android.content.Context
import androidx.work.*
import dev.handheld.launcher.di.LauncherApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Deferrable recovery supplements immediate foreground reconciliation. */
class RomReconciliationWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result = try {
        (applicationContext as LauncherApplication).appContainer.romScanner.reconcile()
        Result.success()
    } catch(cancelled:CancellationException) { throw cancelled }
    catch(_:Exception) { if(runAttemptCount<3) Result.retry() else Result.failure() }

    companion object {
        fun schedule(context:Context) {
            val request=PeriodicWorkRequestBuilder<RomReconciliationWorker>(8,TimeUnit.HOURS)
                .setInitialDelay(8,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("rom-folder-reconciliation",ExistingPeriodicWorkPolicy.KEEP,request)
        }
    }
}
