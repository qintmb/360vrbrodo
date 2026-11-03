package com.elbruz.vrvideo.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elbruz.vrvideo.model.SearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Lightweight preference store using Jetpack DataStore.
 * Stores: last visited URL, active search-engine id, the list of
 * search engines (JSON-serialized), and boolean flags.
 */
private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPrefs private constructor(private val context: Context) {

    val lastUrlFlow: Flow<String?> =
        context.dataStore.data.map { it[KEY_LAST_URL] }

    val searchEnginesFlow: Flow<List<SearchEngine>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_ENGINES_JSON]
            if (raw.isNullOrEmpty()) {
                SearchEngine.DEFAULT_ENGINES
            } else {
                try {
                    kotlinx.serialization.json.Json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(SearchEngine.serializer()),
                        raw,
                    )
                } catch (_: Exception) {
                    SearchEngine.DEFAULT_ENGINES
                }
            }
        }

    val currentEngineIdFlow: Flow<String> =
        context.dataStore.data.map { it[KEY_CURRENT_ENGINE] ?: "google" }

    suspend fun saveLastUrl(url: String) {
        context.dataStore.edit { it[KEY_LAST_URL] = url }
    }

    suspend fun saveEngines(engines: List<SearchEngine>) {
        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(SearchEngine.serializer()),
            engines,
        )
        context.dataStore.edit { it[KEY_ENGINES_JSON] = json }
    }

    suspend fun setCurrentEngine(id: String) {
        context.dataStore.edit { it[KEY_CURRENT_ENGINE] = id }
    }

    companion object {
        val KEY_LAST_URL = stringPreferencesKey("last_url")
        val KEY_ENGINES_JSON = stringPreferencesKey("engines_json")
        val KEY_CURRENT_ENGINE = stringPreferencesKey("current_engine")
        val KEY_ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")

        @Volatile private var INSTANCE: UserPrefs? = null

        fun get(context: Context): UserPrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserPrefs(context.applicationContext).also { INSTANCE = it }
            }
    }

    suspend fun setBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun getBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, default: Boolean = false): Boolean {
        return context.dataStore.data.map { it[key] ?: default }.first()
    }

    suspend fun getString(key: androidx.datastore.preferences.core.Preferences.Key<String>, default: String = ""): String {
        return context.dataStore.data.map { it[key] ?: default }.first()
    }

    suspend fun setString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }
}