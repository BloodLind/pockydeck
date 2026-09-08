package dev.handheld.launcher.runtime

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

/** Requires the user's existing Shizuku grant; never starts Shizuku or grants access itself. */
@RunWith(AndroidJUnit4::class)
class RunningStatusDeviceTest {
    @Test fun approvedHelperReadsLivePresenceAndRejectsAnotherUser() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Shizuku must already be started and approved by the user",
            Shizuku.pingBinder() && Shizuku.getVersion() >= 13 && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
        val ready = CountDownLatch(1)
        var remote: IProcessStatusService? = null
        val userId = Process.myUid() / 100_000
        val args = Shizuku.UserServiceArgs(ComponentName(context, ProcessStatusService::class.java))
            .daemon(false).processNameSuffix("status-check-$userId").tag("status-check-$userId").version(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                remote = IProcessStatusService.Stub.asInterface(binder)
                ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) { remote = null }
        }
        try {
            instrumentation.runOnMainSync { Shizuku.bindUserService(args, connection) }
            assertTrue("Read-only helper must connect", ready.await(10, TimeUnit.SECONDS))
            val service = remote
            assertNotNull(service)
            val found = service!!.runningPackages(arrayOf(context.packageName, "example.definitely.absent.launcherfixture"), userId).toSet()
            assertEquals(setOf(context.packageName), found)
            assertTrue("A different Android profile must be rejected",
                runCatching { service.runningPackages(arrayOf(context.packageName), userId + 1) }.isFailure)
        } finally {
            instrumentation.runOnMainSync { Shizuku.unbindUserService(args, connection, true) }
        }
    }
}
