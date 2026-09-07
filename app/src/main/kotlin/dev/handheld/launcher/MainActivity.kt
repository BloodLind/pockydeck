package dev.handheld.launcher

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.handheld.launcher.contract.ActivityRequestAcknowledgement
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.di.FoundationUiState
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.di.MainViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var reducedMotion by mutableStateOf(false)

    private val appContainer
        get() = (application as LauncherApplication).appContainer

    private val viewModel: MainViewModel by viewModels {
        appContainer.mainViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activityRequests.filterNotNull().collect { pending ->
                    val claim = appContainer.activityRequestPort.claim(pending.id)
                        ?: return@collect
                    // Concrete launch and HOME-role effects arrive in F07/F08. Claim first so
                    // lifecycle recollection or a competing observer cannot replay an effect.
                    appContainer.activityRequestPort.complete(
                        claim,
                        ActivityRequestAcknowledgement.Unsupported,
                    )
                }
            }
        }
        setContent {
            LauncherTheme(reducedMotion = reducedMotion) {
                FoundationScreen(viewModel.state)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reducedMotion = !ValueAnimator.areAnimatorsEnabled()
    }
}

@Composable
private fun FoundationScreen(state: FoundationUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LauncherTheme.colors.backgroundGradient())
            .padding(LauncherTheme.spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = stringResource(R.string.app_name),
            style = LauncherTheme.typography.homeTitle.copy(
                color = LauncherTheme.colors.textPrimary,
            ),
        )
        BasicText(
            text = if (state.isReady) stringResource(R.string.foundation_ready) else "",
            modifier = Modifier.padding(top = LauncherTheme.spacing.sm),
            style = LauncherTheme.typography.body.copy(
                color = LauncherTheme.colors.textSecondary,
            ),
        )
    }
}
