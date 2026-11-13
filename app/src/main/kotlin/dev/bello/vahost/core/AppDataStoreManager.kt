package dev.bello.vahost.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private const val APP_DATASTORE = "va_host_settings"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(APP_DATASTORE)

class AppDataStoreManager(private val context: Context) : AppDataStore {

    override suspend fun setValue(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun readValue(key: String): String? =
        context.dataStore.data.first()[stringPreferencesKey(key)]
}
