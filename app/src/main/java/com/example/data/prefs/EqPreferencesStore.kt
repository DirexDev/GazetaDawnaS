package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.songQDataStore by preferencesDataStore(name = "songq_prefs")

data class StoredAudioPrefs(
    val darkTheme: Boolean = false,
    val playMusicMode: Boolean = true,
    val activePresetName: String = "Studio Flat",
    val bandGainsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val bassDb: Float = 0f,
    val midsDb: Float = 0f,
    val trebleDb: Float = 0f,
    val bassBoostStrength: Int = 250,
    val virtualizerStrength: Int = 200,
    val preampDb: Float = 0f,
    val eqEnabled: Boolean = true,
    val explorerTreeUri: String = "",
    val preferredMusicAppPackage: String = "",
    val preferredMusicAppLabel: String = ""
)

class EqPreferencesStore(private val context: Context) {
    private val keyDarkTheme = booleanPreferencesKey("dark_theme")
    private val keyPlayMusicMode = booleanPreferencesKey("play_music_mode")
    private val keyPreset = stringPreferencesKey("eq_preset")
    private val keyBands = stringPreferencesKey("eq_bands")
    private val keyBassDb = floatPreferencesKey("eq_bass_db")
    private val keyMidsDb = floatPreferencesKey("eq_mids_db")
    private val keyTrebleDb = floatPreferencesKey("eq_treble_db")
    private val keyBass = intPreferencesKey("eq_bass")
    private val keyVirtualizer = intPreferencesKey("eq_virtualizer")
    private val keyPreamp = floatPreferencesKey("eq_preamp")
    private val keyEqEnabled = booleanPreferencesKey("eq_enabled")
    private val keyExplorerTreeUri = stringPreferencesKey("explorer_tree_uri")
    private val keyPreferredMusicPkg = stringPreferencesKey("preferred_music_pkg")
    private val keyPreferredMusicLabel = stringPreferencesKey("preferred_music_label")

    val prefsFlow: Flow<StoredAudioPrefs> = context.songQDataStore.data.map { prefs ->
        val rawBands = prefs[keyBands]
        val parsedBands = rawBands?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 5 }
            ?: listOf(0f, 0f, 0f, 0f, 0f)
        StoredAudioPrefs(
            darkTheme = prefs[keyDarkTheme] ?: false,
            playMusicMode = prefs[keyPlayMusicMode] ?: true,
            activePresetName = prefs[keyPreset] ?: "Studio Flat",
            bandGainsDb = parsedBands,
            bassDb = prefs[keyBassDb] ?: ((parsedBands[0] + parsedBands[1]) / 2f),
            midsDb = prefs[keyMidsDb] ?: parsedBands[2],
            trebleDb = prefs[keyTrebleDb] ?: ((parsedBands[3] + parsedBands[4]) / 2f),
            bassBoostStrength = prefs[keyBass] ?: 250,
            virtualizerStrength = prefs[keyVirtualizer] ?: 200,
            preampDb = prefs[keyPreamp] ?: 0f,
            eqEnabled = prefs[keyEqEnabled] ?: true,
            explorerTreeUri = prefs[keyExplorerTreeUri] ?: "",
            preferredMusicAppPackage = prefs[keyPreferredMusicPkg] ?: "",
            preferredMusicAppLabel = prefs[keyPreferredMusicLabel] ?: ""
        )
    }

    suspend fun saveDarkTheme(enabled: Boolean) {
        context.songQDataStore.edit { it[keyDarkTheme] = enabled }
    }

    suspend fun savePlayMusicMode(enabled: Boolean) {
        context.songQDataStore.edit { it[keyPlayMusicMode] = enabled }
    }

    suspend fun saveExplorerTreeUri(uriString: String) {
        context.songQDataStore.edit { it[keyExplorerTreeUri] = uriString }
    }

    suspend fun savePreferredMusicApp(packageName: String, label: String) {
        context.songQDataStore.edit {
            it[keyPreferredMusicPkg] = packageName
            it[keyPreferredMusicLabel] = label
        }
    }

    suspend fun saveEqSettings(
        presetName: String,
        bandGainsDb: List<Float>,
        bassDb: Float,
        midsDb: Float,
        trebleDb: Float,
        bassBoostStrength: Int,
        virtualizerStrength: Int,
        preampDb: Float,
        eqEnabled: Boolean
    ) {
        context.songQDataStore.edit { prefs ->
            prefs[keyPreset] = presetName
            prefs[keyBands] = bandGainsDb.joinToString(",")
            prefs[keyBassDb] = bassDb
            prefs[keyMidsDb] = midsDb
            prefs[keyTrebleDb] = trebleDb
            prefs[keyBass] = bassBoostStrength
            prefs[keyVirtualizer] = virtualizerStrength
            prefs[keyPreamp] = preampDb
            prefs[keyEqEnabled] = eqEnabled
        }
    }
}
