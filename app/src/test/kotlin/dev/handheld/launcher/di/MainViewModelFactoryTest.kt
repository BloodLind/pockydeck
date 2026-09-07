package dev.handheld.launcher.di

import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelFactoryTest {
    @Test
    fun factoryInjectsTheApplicationScopedRequestPort() {
        val port = InMemoryActivityRequestPort()
        val viewModel = MainViewModelFactory(port).create(MainViewModel::class.java)

        assertEquals(port.pending, viewModel.activityRequests)
        assertEquals(true, viewModel.state.isReady)
    }
}
