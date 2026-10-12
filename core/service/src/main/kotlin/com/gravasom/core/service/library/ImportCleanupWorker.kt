package com.gravasom.core.service.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Limpeza da importação (F6): os parciais `.tmp` de `files/imports/` que sobraram
 * de cópias interrompidas (app morto no meio da cópia) expiram em 24 h — regra do
 * roteiro. O arquivo FINAL importado NUNCA é tocado (só o que casa com `.tmp`).
 */
class ImportCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dir = File(applicationContext.filesDir, ImportUseCase.IMPORT_DIR)
        if (!dir.isDirectory) return Result.success()
        val agora = System.currentTimeMillis()
        var removidos = 0
        dir.listFiles()?.forEach { arquivo ->
            val venceu = agora - arquivo.lastModified() > ImportUseCase.TEMP_EXPIRA_MS
            if (ImportUseCase.eTemporario(arquivo) && venceu && arquivo.delete()) removidos++
        }
        return Result.success()
    }

    companion object {
        const val TAG = "import_cleanup"

        /** Agenda única (KEEP): roda ~1× por dia; a janela 24 h é do próprio critério de expiração. */
        fun agendar(context: Context) {
            val request = PeriodicWorkRequestBuilder<ImportCleanupWorker>(24, TimeUnit.HOURS)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
