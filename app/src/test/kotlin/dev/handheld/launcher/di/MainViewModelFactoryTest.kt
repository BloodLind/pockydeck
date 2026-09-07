package dev.handheld.launcher.di

import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelFactoryTest {
    @Test
    fun factoryInjectsTheApplicationScopedRequestPort() {
        val container = AppContainer()
        val viewModel = container.mainViewModelFactory().create(MainViewModel::class.java)

        assertEquals(container.activityRequestPort.pending, viewModel.activityRequests)
        assertEquals(true, viewModel.state.isReady)
    }
}
