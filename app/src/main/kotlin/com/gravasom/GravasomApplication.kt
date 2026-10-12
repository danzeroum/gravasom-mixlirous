package com.gravasom

import android.app.Application
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.core.service.BootRecovery
import com.gravasom.core.service.MediaProbe
import com.gravasom.core.service.RecordingStorage
import com.gravasom.core.service.library.ImportCleanupWorker
import com.gravasom.core.service.library.RecordingFiles
import com.gravasom.core.service.library.RecordingIngestor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class GravasomApplication : Application() {

    @Inject lateinit var ingestor: RecordingIngestor
    @Inject lateinit var repo: RecordingRepository
    @Inject lateinit var files: RecordingFiles

    // Expostos para os testes instrumentados (mesmo grafo Hilt de producao; nao ha grafo de teste separado).
    @Inject lateinit var preferences: com.gravasom.core.data.preferences.PreferencesRepository
    @Inject lateinit var usageMetrics: com.gravasom.core.data.metrics.UsageMetrics
    @Inject lateinit var database: com.gravasom.core.data.db.GravasomDatabase
    @Inject lateinit var analyzer: com.gravasom.core.service.analysis.RecordingAnalyzer
    @Inject lateinit var markers: com.gravasom.core.domain.marker.MarkerRepository
    @Inject lateinit var clips: com.gravasom.core.service.clip.ClipService

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        ingestor.start(appScope)
        appScope.launch {
            files.clearTemp() // copias em claro do cofre que sobraram de uma sessao anterior
            recoverInterrupted()
            purgeOldTrash()
        }
        // F6/Importação: parciais .tmp de importações interrompidas expiram em 24 h.
        ImportCleanupWorker.agendar(this)
    }

    /** Recupera gravacoes interrompidas (kill do processo) e as coloca na biblioteca. */
    private fun recoverInterrupted() {
        BootRecovery(
            tempDir = RecordingStorage.dir(this),
            isPlayable = MediaProbe::isPlayable,
            minAgeMillis = RECOVERY_MIN_AGE_MS,
        ) { file -> appScope.launch { ingestor.ingest(file) } }.run()
    }

    /** Lixeira de 30 dias: depois disso o audio some de verdade (arquivo, copia publica e linha). */
    private suspend fun purgeOldTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_DAYS * DAY_MS
        repo.observeTrash().first().filter { (it.deletedAtMillis ?: Long.MAX_VALUE) < cutoff }.forEach { files.deleteForever(it) }
    }

    private companion object {
        const val RECOVERY_MIN_AGE_MS = 15_000L
        const val TRASH_DAYS = 30L
        const val DAY_MS = 86_400_000L
    }
}
