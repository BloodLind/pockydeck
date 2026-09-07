package dev.handheld.launcher

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.handheld.launcher.contract.ActivityRequestAcknowledgement
import dev.handheld.launcher.di.FoundationUiState
import dev.handheld.launcher.di.LauncherApplication
import dev.handheld.launcher.di.MainViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
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
            FoundationScreen(viewModel.state)
        }
    }
}

@Composable
private fun FoundationScreen(state: FoundationUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF232230))
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = stringResource(R.string.app_name),
            style = TextStyle(color = Color(0xFFF4F5F8), fontSize = 28.sp),
        )
        BasicText(
            text = if (state.isReady) stringResource(R.string.foundation_ready) else "",
            modifier = Modifier.padding(top = 12.dp),
            style = TextStyle(color = Color(0xFFA6ACB8), fontSize = 14.sp),
        )
    }
}
