package com.gravasom.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.preset.FormatPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gravasom_prefs")

/** Preferencias do usuario (formato, qualidade, privacidade, telemetria). */
class PreferencesRepository(private val context: Context) {

    val format: Flow<RecordingFormat> = context.dataStore.data.map { prefs ->
        runCatching { RecordingFormat.valueOf(prefs[KEY_FORMAT] ?: "M4A") }
            .getOrDefault(RecordingFormat.M4A)
    }

    val sampleRate: Flow<Int> = context.dataStore.data.map { it[KEY_SAMPLE_RATE] ?: 44_100 }
    val bitrate: Flow<Int> = context.dataStore.data.map { it[KEY_BITRATE] ?: 128 }
    val channels: Flow<Int> = context.dataStore.data.map { it[KEY_CHANNELS] ?: 1 }

    /** Preset de formato escolhido (Voz, Aula, Entrevista, Musica); null = ajustes manuais ("personalizado"). */
    val preset: Flow<FormatPreset?> = context.dataStore.data.map { FormatPreset.fromId(it[KEY_PRESET]) }
    val mp3Enabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_MP3] ?: false }
    val privateVault: Flow<Boolean> = context.dataStore.data.map { it[KEY_VAULT] ?: false }
    val telemetryOptIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_TELEMETRY] ?: false }

    /** "Pular silencio" ao ouvir (ADR-014). Desligado por padrao: o usuario liga no player e a escolha fica. */
    val skipSilence: Flow<Boolean> = context.dataStore.data.map { it[KEY_SKIP_SILENCE] ?: false }

    /**
     * Paleta de cores (F8, "Aparência"): id guardado na DataStore ("mixlirous" |
     * "gravasom" | "sistema"); o valor default é a Mixlirous (identidade do app).
     * O id é resolvido para [com.gravasom.core.ui.Paleta] na camada de UI.
     */
    val palette: Flow<String> = context.dataStore.data.map { it[KEY_PALETTE] ?: PALETA_PADRAO_ID }

    /** Sensibilidade do pulo = modo do VAD (1 suave, 2 normal, 3 forte). */
    val skipSensitivity: Flow<Int> = context.dataStore.data.map { it[KEY_SKIP_SENSITIVITY] ?: DEFAULT_SKIP_SENSITIVITY }

    val preferredInputId: Flow<Int> = context.dataStore.data.map { it[KEY_INPUT] ?: NO_INPUT }

    suspend fun setPreferredInputId(id: Int) = context.dataStore.edit { it[KEY_INPUT] = id }
    /** Ajuste manual: sai do preset (vira "personalizado"), porque o formato ja nao e o do preset. */
    suspend fun setFormat(format: RecordingFormat) = context.dataStore.edit { it[KEY_FORMAT] = format.name; it.remove(KEY_PRESET) }
    suspend fun setSampleRate(hz: Int) = context.dataStore.edit { it[KEY_SAMPLE_RATE] = hz; it.remove(KEY_PRESET) }
    suspend fun setBitrate(kbps: Int) = context.dataStore.edit { it[KEY_BITRATE] = kbps; it.remove(KEY_PRESET) }

    /** Aplica o preset de uma vez (formato, taxa, bitrate e canais) e o guarda como escolhido. */
    suspend fun setPreset(preset: FormatPreset) = context.dataStore.edit {
        it[KEY_FORMAT] = preset.spec.format.name
        it[KEY_SAMPLE_RATE] = preset.spec.sampleRateHz
        it[KEY_BITRATE] = preset.spec.bitrateKbps
        it[KEY_CHANNELS] = preset.spec.channels
        it[KEY_PRESET] = preset.id
    }
    suspend fun setMp3Enabled(enabled: Boolean) = context.dataStore.edit { it[KEY_MP3] = enabled }
    suspend fun setPrivateVault(enabled: Boolean) = context.dataStore.edit { it[KEY_VAULT] = enabled }
    suspend fun setSkipSilence(enabled: Boolean) = context.dataStore.edit { it[KEY_SKIP_SILENCE] = enabled }
    suspend fun setPalette(id: String) = context.dataStore.edit { it[KEY_PALETTE] = id }
    suspend fun setSkipSensitivity(vadMode: Int) = context.dataStore.edit { it[KEY_SKIP_SENSITIVITY] = vadMode }
    suspend fun setTelemetryOptIn(enabled: Boolean) = context.dataStore.edit { it[KEY_TELEMETRY] = enabled }

    companion object {
        const val NO_INPUT = -1
        const val PALETA_PADRAO_ID = "mixlirous"
        const val DEFAULT_SKIP_SENSITIVITY = 2
        private val KEY_INPUT = intPreferencesKey("preferred_input")
        private val KEY_FORMAT = stringPreferencesKey("format")
        private val KEY_SAMPLE_RATE = intPreferencesKey("sample_rate")
        private val KEY_BITRATE = intPreferencesKey("bitrate")
        private val KEY_CHANNELS = intPreferencesKey("channels")
        private val KEY_PRESET = stringPreferencesKey("format_preset")
        private val KEY_MP3 = booleanPreferencesKey("mp3_enabled")
        private val KEY_VAULT = booleanPreferencesKey("private_vault")
        private val KEY_SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        private val KEY_PALETTE = stringPreferencesKey("palette_key")
        private val KEY_SKIP_SENSITIVITY = intPreferencesKey("skip_sensitivity")
        private val KEY_TELEMETRY = booleanPreferencesKey("telemetry_opt_in")
    }
}
