package dev.handheld.launcher.core.data.foundationfixture

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import android.content.Context

internal fun Context.foundationPreferencesFixture(): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        produceFile = { preferencesDataStoreFile("foundation-fixture.preferences_pb") },
    )
