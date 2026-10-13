package com.gravasom.feature.record

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gravasom.core.data.db.UsageEventDao
import com.gravasom.core.data.db.UsageEventEntity
import com.gravasom.core.data.metrics.UsageMetrics
import com.gravasom.core.data.preferences.PreferencesRepository
import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.preset.FormatPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * "WAV por gravacao no modo avancado" (Onda 2): escolher o chip de formato NA MAO — fora do preset — deixa o modo
 * personalizado e a proxima gravacao sai em WAV (o servico le o formato das preferencias ao iniciar).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR")
class FormatoManualTest {

    private lateinit var prefs: PreferencesRepository
    private lateinit var vm: RecordViewModel
    private lateinit var escopoDataStore: kotlinx.coroutines.CoroutineScope

    private class DaoZero : UsageEventDao {
        override suspend fun insert(event: UsageEventEntity): Long = 0L
        override fun observeCount(type: String): Flow<Int> = MutableStateFlow(0)
        override suspend fun all(): List<UsageEventEntity> = emptyList()
        override suspend fun values(type: String): List<Long> = emptyList()
        override suspend fun clear() = Unit
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // DataStore ISOLADA por teste: a singleton `preferencesDataStore` é por PROCESSO e vazava
        // estado entre testes (o resultado dependia da ordem — falha vista na suíte completa).
        val pasta = java.nio.file.Files.createTempDirectory("formato_manual").toFile()
        val (store, escopo) = PreferencesRepository.dataStoreDeTeste(pasta)
        escopoDataStore = escopo
        prefs = PreferencesRepository(context, store)
        vm = RecordViewModel(context, prefs, UsageMetrics(DaoZero(), prefs.telemetryOptIn))
    }

    @org.junit.After
    fun soltar() {
        PreferencesRepository.fechar(escopoDataStore)
    }

    @Test
    fun `escolher WAV na mao sai do preset e o formato passa a ser WAV`() = runBlocking {
        prefs.setPreset(FormatPreset.VOICE) // comecou num preset (M4A 24 kHz)
        assertEquals(RecordingFormat.M4A, prefs.format.first())

        vm.selectFormat(RecordingFormat.WAV)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(RecordingFormat.WAV, prefs.format.first()) // a proxima gravacao sai em WAV
        assertNull(prefs.preset.first()) // fora de preset: modo personalizado
        assertEquals(RecordingFormat.WAV, vm.state.value.format) // o chip da tela acompanha
    }

    @Test
    fun `escolher um preset ajusta o formato dele e voltar para WAV na mao volta ao personalizado`() = runBlocking {
        prefs.setPreset(FormatPreset.INTERVIEW)
        assertEquals(RecordingFormat.M4A, prefs.format.first())
        assertNotNull(prefs.preset.first())

        vm.selectFormat(RecordingFormat.WAV)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(prefs.preset.first())
        assertEquals(RecordingFormat.WAV, vm.state.value.format)
    }
}
