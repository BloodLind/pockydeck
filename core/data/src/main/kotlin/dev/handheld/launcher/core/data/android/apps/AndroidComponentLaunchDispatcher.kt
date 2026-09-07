package dev.handheld.launcher.core.data.android.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.LaunchAcknowledgement
import dev.handheld.launcher.core.domain.model.LaunchFailureReason
import dev.handheld.launcher.core.domain.model.LaunchRequest
import dev.handheld.launcher.core.domain.model.LaunchTarget
import dev.handheld.launcher.core.domain.repository.LaunchDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Revalidates and dispatches current-user Android components with launcher task semantics. */
class AndroidComponentLaunchDispatcher private constructor(
    private val ownPackageName: String,
    private val gateway: AndroidComponentLaunchGateway,
    private val workDispatcher: CoroutineDispatcher,
) : LaunchDispatcher {
    constructor(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        ownPackageName = context.applicationContext.packageName,
        gateway = PackageManagerAndroidComponentLaunchGateway(context.applicationContext),
        workDispatcher = dispatcher,
    )

    internal constructor(
        ownPackageName: String,
        gateway: AndroidComponentLaunchGateway,
        dispatcher: CoroutineDispatcher,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit,
    ) : this(ownPackageName, gateway, dispatcher)

    override suspend fun dispatch(request: LaunchRequest): LaunchAcknowledgement =
        withContext(workDispatcher) {
            val target = request.target as? LaunchTarget.AndroidComponent
                ?: return@withContext request.failed(LaunchFailureReason.REJECTED)
            val componentId = target.componentId
            if (componentId.packageName == ownPackageName) {
                return@withContext request.failed(LaunchFailureReason.REJECTED)
            }

            when (gateway.revalidate(componentId)) {
                AndroidComponentValidation.Available -> when (gateway.start(componentId)) {
                    AndroidComponentStartResult.Started ->
                        LaunchAcknowledgement.Dispatched(request.operationId)

                    AndroidComponentStartResult.TargetUnavailable ->
                        request.failed(LaunchFailureReason.TARGET_UNAVAILABLE)

                    AndroidComponentStartResult.Rejected ->
                        request.failed(LaunchFailureReason.REJECTED)

                    AndroidComponentStartResult.Failed ->
                        request.failed(LaunchFailureReason.DISPATCH_FAILED)
                }

                AndroidComponentValidation.TargetUnavailable ->
                    request.failed(LaunchFailureReason.TARGET_UNAVAILABLE)

                AndroidComponentValidation.Rejected ->
                    request.failed(LaunchFailureReason.REJECTED)

                AndroidComponentValidation.Failed ->
                    request.failed(LaunchFailureReason.DISPATCH_FAILED)
            }
        }
}

internal interface AndroidComponentLaunchGateway {
    fun revalidate(componentId: CurrentUserAndroidComponentId): AndroidComponentValidation

    fun start(componentId: CurrentUserAndroidComponentId): AndroidComponentStartResult
}

internal enum class AndroidComponentValidation {
    Available,
    TargetUnavailable,
    Rejected,
    Failed,
}

internal enum class AndroidComponentStartResult {
    Started,
    TargetUnavailable,
    Rejected,
    Failed,
}

internal class PackageManagerAndroidComponentLaunchGateway(
    context: Context,
) : AndroidComponentLaunchGateway {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    override fun revalidate(
        componentId: CurrentUserAndroidComponentId,
    ): AndroidComponentValidation = try {
        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(componentId.packageName)
        val exactMatch = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(0L),
        ).any { resolved ->
            val activityInfo = resolved.activityInfo ?: return@any false
            val resolvedClassName = activityInfo.name.toFullyQualifiedClassName(
                activityInfo.packageName,
            ) ?: return@any false
            activityInfo.packageName == componentId.packageName &&
                resolvedClassName == componentId.activityClassName &&
                activityInfo.enabled &&
                activityInfo.applicationInfo.enabled &&
                activityInfo.exported
        }
        if (exactMatch) {
            AndroidComponentValidation.Available
        } else {
            AndroidComponentValidation.TargetUnavailable
        }
    } catch (_: SecurityException) {
        AndroidComponentValidation.Rejected
    } catch (_: RuntimeException) {
        AndroidComponentValidation.Failed
    }

    override fun start(
        componentId: CurrentUserAndroidComponentId,
    ): AndroidComponentStartResult = try {
        val component = ComponentName(componentId.packageName, componentId.activityClassName)
        val intent = Intent.makeMainActivity(component).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        applicationContext.startActivity(intent)
        AndroidComponentStartResult.Started
    } catch (_: ActivityNotFoundException) {
        AndroidComponentStartResult.TargetUnavailable
    } catch (_: SecurityException) {
        AndroidComponentStartResult.Rejected
    } catch (_: RuntimeException) {
        AndroidComponentStartResult.Failed
    }
}

private fun String.toFullyQualifiedClassName(packageName: String): String? {
    val rawClassName = trim()
    if (rawClassName.isEmpty() || rawClassName == "." || '/' in rawClassName) return null
    return when {
        rawClassName.startsWith('.') -> packageName + rawClassName
        '.' !in rawClassName -> "$packageName.$rawClassName"
        else -> rawClassName
    }
}

private fun LaunchRequest.failed(reason: LaunchFailureReason) =
    LaunchAcknowledgement.Failed(operationId, reason)
