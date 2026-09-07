package dev.handheld.launcher.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.handheld.launcher.contract.ActivityRequestPort
import kotlinx.coroutines.flow.StateFlow

data class FoundationUiState(
    val isReady: Boolean,
)

class MainViewModel(
    val activityRequests: StateFlow<dev.handheld.launcher.contract.PendingActivityRequest?>,
) : ViewModel() {
    val state = FoundationUiState(isReady = true)
}

class MainViewModelFactory(
    private val activityRequestPort: ActivityRequestPort,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
            "Unsupported ViewModel: ${modelClass.name}"
        }
        return MainViewModel(activityRequestPort.pending) as T
    }
}
