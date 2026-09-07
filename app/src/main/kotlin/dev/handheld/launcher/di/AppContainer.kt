package dev.handheld.launcher.di

import dev.handheld.launcher.contract.ActivityRequestPort

class AppContainer(
    val activityRequestPort: ActivityRequestPort = InMemoryActivityRequestPort(),
) {
    fun mainViewModelFactory(): MainViewModelFactory =
        MainViewModelFactory(activityRequestPort)
}
