package dev.handheld.launcher.rom

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import androidx.work.*
import dev.handheld.launcher.di.LauncherApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Deferrable recovery supplements immediate foreground reconciliation. */
class RomReconciliationWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result = try {
        val container=(applicationContext as LauncherApplication).appContainer
        // A retry can inherit SCANNING/ERROR from an interrupted first pass. Reconcile all
        // registered sources on retries so selective continuation cannot strand those entries.
        container.romScanner.reconcile(
            scanExistingSources=runAttemptCount>0 || !inputData.getBoolean("discoveryOnly",false),
            continueInProcess=false,
        )
        if(container.sharedRomDiscovery.state.value.hasMore) {
            // Persist the next bounded discovery slice before this worker finishes. Known sources
            // need no full rescan on each slice; the scanner still picks up newly discovered roots.
            withContext(Dispatchers.IO) { enqueue(applicationContext,discoveryOnly=true).result.get(30,TimeUnit.SECONDS) }
        }
        Result.success()
    } catch(cancelled:CancellationException) { throw cancelled }
    catch(_:Exception) { if(runAttemptCount<3) Result.retry() else Result.failure() }

    companion object {
        fun enqueue(context:Context,discoveryOnly:Boolean=false):Operation {
            val request=OneTimeWorkRequestBuilder<RomReconciliationWorker>()
                .setInputData(workDataOf("discoveryOnly" to discoveryOnly))
                .setInitialDelay(if(discoveryOnly) 1 else 0,TimeUnit.SECONDS)
                .build()
            return WorkManager.getInstance(context).enqueueUniqueWork("rom-storage-reconciliation",ExistingWorkPolicy.APPEND_OR_REPLACE,request)
        }
        fun schedule(context:Context) {
            val request=PeriodicWorkRequestBuilder<RomReconciliationWorker>(8,TimeUnit.HOURS)
                .setInitialDelay(8,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("rom-folder-reconciliation",ExistingPeriodicWorkPolicy.KEEP,request)
        }
    }
}

/** Mount broadcasts only queue work; no provider or filesystem traversal runs on the receiver. */
class RomStorageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action in ACTIONS) RomReconciliationWorker.enqueue(context)
    }
    private companion object {
        val ACTIONS=setOf(Intent.ACTION_MEDIA_MOUNTED,Intent.ACTION_MEDIA_UNMOUNTED,Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_REMOVED,Intent.ACTION_MEDIA_BAD_REMOVAL)
    }
}
