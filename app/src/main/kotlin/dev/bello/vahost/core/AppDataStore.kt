package dev.bello.vahost.core

/**
 * Same shape as the KMP app's AppDataStore: a string key/value store, so
 * callers never touch Preferences keys directly.
 */
interface AppDataStore {

    suspend fun setValue(key: String, value: String)

    suspend fun readValue(key: String): String?
}

object DataStoreKeys {
    const val SHOW_SYSTEM_LIBS = "show_system_libs"
}
