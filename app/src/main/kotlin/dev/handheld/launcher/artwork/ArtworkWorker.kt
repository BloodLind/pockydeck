package dev.handheld.launcher.artwork

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.*
import dev.handheld.launcher.di.LauncherApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ArtworkWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val container = (applicationContext as LauncherApplication).appContainer
        container.startArtwork()
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        val next = container.artworkRepository.process(online = {
            connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        })
        if (next != null) withContext(Dispatchers.IO) {
            enqueue(applicationContext, append = true, delayMillis = next).result.get(30, TimeUnit.SECONDS)
        }
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }

    companion object {
        fun enqueue(context: Context, append: Boolean = false, delayMillis: Long = 0): Operation {
            val request = OneTimeWorkRequestBuilder<ArtworkWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            return WorkManager.getInstance(context).enqueueUniqueWork("artwork-enrichment",
                if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, request)
        }
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ArtworkRecoveryWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("artwork-recovery", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Recover interrupted work and the small enqueue/worker-completion race without polling the UI. */
class ArtworkRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ArtworkWorker.enqueue(applicationContext)
        return Result.success()
    }
}
