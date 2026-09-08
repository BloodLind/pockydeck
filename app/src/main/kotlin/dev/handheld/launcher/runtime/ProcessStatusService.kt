package dev.handheld.launcher.runtime

import android.content.Context
import android.os.Binder
import androidx.annotation.Keep
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Runs only through the explicitly approved Shizuku UserService, never as an exported service. */
@Keep
class ProcessStatusService(context: Context) : IProcessStatusService.Stub() {
    private val clientUid = context.applicationInfo.uid
    private val timeout = Executors.newSingleThreadScheduledExecutor { work ->
        Thread(work, "process-status-timeout").apply { isDaemon = true }
    }

    override fun runningPackages(packages: Array<out String>, userId: Int): Array<String> {
        check(Binder.getCallingUid() == clientUid) { "Unexpected caller" }
        require(userId == clientUid / 100_000 && packages.size <= 4096)
        require(packages.all(::validProcessPackage))
        // Fixed argv, no command interpolation. Return only matches requested by this launcher.
        val process = ProcessBuilder("/system/bin/ps", "-A", "-o", "UID,NAME").redirectErrorStream(true).start()
        val watchdog = timeout.schedule({ process.destroyForcibly() }, 2, TimeUnit.SECONDS)
        return try {
            val found = process.inputStream.bufferedReader().use { reader ->
                processPresence(reader.lineSequence(), packages.toSet(), userId)
            }
            check(process.waitFor(250, TimeUnit.MILLISECONDS) && process.exitValue() == 0) { "Process reading unavailable" }
            found.toTypedArray()
        } finally {
            watchdog.cancel(false)
            process.destroy()
        }
    }

    override fun destroy() {
        val caller = Binder.getCallingUid()
        check(caller == clientUid || caller == 0 || caller == 2000)
        timeout.shutdownNow()
        exitProcess(0) // Only this helper's own process, as required by Shizuku's lifecycle.
    }
}
