package dev.handheld.launcher.core.data.android.apps

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.handheld.launcher.core.data.discovery.fixture.LaunchCallerFixtureActivity
import dev.handheld.launcher.core.data.discovery.fixture.SecondLauncherActivity
import dev.handheld.launcher.core.data.local.LauncherDatabase
import dev.handheld.launcher.core.data.repository.RoomSuccessfulOpenRepository
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.ItemId
import dev.handheld.launcher.core.domain.model.LaunchAcknowledgement
import dev.handheld.launcher.core.domain.model.LaunchFailureReason
import dev.handheld.launcher.core.domain.model.LaunchOperationId
import dev.handheld.launcher.core.domain.model.LaunchRequest
import dev.handheld.launcher.core.domain.model.LaunchTarget
import dev.handheld.launcher.core.domain.model.SuccessfulOpenCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidComponentLaunchDispatcherInstrumentedTest {
    @Test
    fun gatewayBuildsExactMainLauncherIntentWithTaskFlags() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val recordingContext = RecordingStartContext(baseContext)
        val settings = CurrentUserAndroidComponentId(
            "com.android.settings",
            "com.android.settings.Settings",
        )
        val gateway = PackageManagerAndroidComponentLaunchGateway(recordingContext)

        assertEquals(AndroidComponentValidation.Available, gateway.revalidate(settings))
        assertEquals(AndroidComponentStartResult.Started, gateway.start(settings))

        val intent = requireNotNull(recordingContext.startedIntent)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(Intent.CATEGORY_LAUNCHER in requireNotNull(intent.categories))
        assertEquals(ComponentName(settings.packageName, settings.activityClassName), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED != 0)
    }

    @Test
    fun missingAndDisabledComponentsReturnTargetUnavailable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dispatcher = AndroidComponentLaunchDispatcher(context, Dispatchers.IO)
        val missing = CurrentUserAndroidComponentId(
            "dev.fixture.missing",
            "dev.fixture.missing.MainActivity",
        )
        assertEquals(
            LaunchAcknowledgement.Failed(
                LaunchOperationId("missing"),
                LaunchFailureReason.TARGET_UNAVAILABLE,
            ),
            dispatcher.dispatch(request("missing", missing)),
        )

        val fixtureContext = InstrumentationRegistry.getInstrumentation().context
        val packageManager = fixtureContext.packageManager
        val fixtureComponent = ComponentName(fixtureContext, SecondLauncherActivity::class.java)
        val fixtureId = CurrentUserAndroidComponentId(
            fixtureComponent.packageName,
            fixtureComponent.className,
        )
        val originalState = packageManager.getComponentEnabledSetting(fixtureComponent)
        try {
            packageManager.setComponentEnabledSetting(
                fixtureComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            assertEquals(
                AndroidComponentValidation.TargetUnavailable,
                PackageManagerAndroidComponentLaunchGateway(fixtureContext).revalidate(fixtureId),
            )
        } finally {
            packageManager.setComponentEnabledSetting(
                fixtureComponent,
                originalState,
                PackageManager.DONT_KILL_APP,
            )
        }
        assertEquals(originalState, packageManager.getComponentEnabledSetting(fixtureComponent))
    }

    @Test
    fun settingsLaunchUsesExternalTaskAndDoesNotWriteRecency() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val settings = CurrentUserAndroidComponentId(
            "com.android.settings",
            "com.android.settings.Settings",
        )
        val databaseName = "us022-${System.nanoTime()}.db"
        val database = LauncherDatabase.open(context, databaseName)
        val successfulOpens = RoomSuccessfulOpenRepository(database)
        successfulOpens.recordOnce(
            SuccessfulOpenCandidate(
                LaunchOperationId("existing-open"),
                ItemId("android:existing/existing.Main"),
            ),
        )
        val expectedHistory = successfulOpens.records.first()

        try {
            ActivityScenario.launch<LaunchCallerFixtureActivity>(
                Intent(context, LaunchCallerFixtureActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ).use { callerScenario ->
                var callerTaskId = -1
                callerScenario.onActivity { callerTaskId = it.taskId }

                val result = AndroidComponentLaunchDispatcher(context, Dispatchers.IO)
                    .dispatch(request("settings", settings))
                assertEquals(LaunchAcknowledgement.Dispatched(LaunchOperationId("settings")), result)

                val settingsComponent = ComponentName(
                    settings.packageName,
                    settings.activityClassName,
                )
                val launchedDump = awaitTaskDump(settingsComponent)
                val settingsTaskId = requireNotNull(taskIdContaining(launchedDump, settingsComponent))
                assertNotEquals(callerTaskId, settingsTaskId)

                activityManager.appTasks
                    .first { it.taskInfo.taskId == callerTaskId }
                    .moveToFront()
                val returnedDump = awaitResumedActivity(LaunchCallerFixtureActivity::class.java.name)
                assertEquals(settingsTaskId, taskIdContaining(returnedDump, settingsComponent))
                assertTrue(returnedDump.lineSequence().any {
                    it.isResumedActivityLine(LaunchCallerFixtureActivity::class.java.name)
                })
                Log.i(
                    LOG_TAG,
                    "callerTaskId=$callerTaskId settingsTaskId=$settingsTaskId " +
                        "settingsRetained=true callerResumed=true",
                )
            }

            assertEquals(expectedHistory, successfulOpens.records.first())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun awaitTaskDump(component: ComponentName): String = withTimeout(5_000) {
        while (true) {
            val dump = activityDump()
            if (taskIdContaining(dump, component) != null) return@withTimeout dump
            delay(50)
        }
        error("unreachable")
    }

    private suspend fun awaitResumedActivity(activityClassName: String): String = withTimeout(5_000) {
        while (true) {
            val dump = activityDump()
            if (dump.lineSequence().any { it.isResumedActivityLine(activityClassName) }) {
                return@withTimeout dump
            }
            delay(50)
        }
        error("unreachable")
    }

    private fun activityDump(): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys activity activities")
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }

    private fun taskIdContaining(dump: String, component: ComponentName): Int? {
        var currentTaskId: Int? = null
        val componentNames = setOf(component.className, component.flattenToShortString())
        dump.lineSequence().forEach { line ->
            TASK_ID.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { currentTaskId = it }
            if (componentNames.any { it in line } && currentTaskId != null) return currentTaskId
        }
        return null
    }

    private fun String.isResumedActivityLine(activityClassName: String): Boolean =
        activityClassName in this && RESUMED_ACTIVITY_MARKERS.any { marker -> marker in this }

    private fun request(
        operation: String,
        componentId: CurrentUserAndroidComponentId,
    ) = LaunchRequest(
        operationId = LaunchOperationId(operation),
        itemId = componentId.itemId,
        target = LaunchTarget.AndroidComponent(componentId),
    )

    private class RecordingStartContext(base: Context) : ContextWrapper(base) {
        var startedIntent: Intent? = null
            private set

        override fun getApplicationContext(): Context = this

        override fun startActivity(intent: Intent) {
            startedIntent = intent
        }
    }

    private companion object {
        const val LOG_TAG = "F07Dispatch"
        val TASK_ID = Regex("\\* Task\\{[^#]*#(\\d+)")
        val RESUMED_ACTIVITY_MARKERS = listOf(
            "mResumedActivity",
            "topResumedActivity",
            "ResumedActivity:",
        )
    }
}
