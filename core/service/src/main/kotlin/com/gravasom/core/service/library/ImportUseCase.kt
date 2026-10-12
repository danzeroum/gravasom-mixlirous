package com.gravasom.core.service.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.gravasom.core.data.metrics.UsageMetrics
import com.gravasom.core.data.preferences.PreferencesRepository
import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.metrics.UsageEventType
import com.gravasom.core.domain.model.Recording
import com.gravasom.core.domain.model.RecordingOrigin
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.core.service.MediaProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Resultado da importação (SAF ou ACTION_SEND). [Duplicata] carrega o id da
 * gravação que JÁ tem o mesmo arquivo (mesmo SHA-256) — a tela mostra "Já existe".
 */
sealed interface ImportOutcome {
    data class Imported(val id: Long) : ImportOutcome
    data class Duplicata(val idExistente: Long) : ImportOutcome
    data object Unsupported : ImportOutcome
    data object Invalid : ImportOutcome
}

/**
 * Importação de áudio do celular (F6/Importação) — o fluxo único usado pela
 * porta SAF (picker "Importar" na Library) e pelo ACTION_SEND.
 *
 * Contratos do roteiro:
 * - Scoped Storage: NUNCA acessa `/storage/emulated/0/…` direto; só o
 *   ContentResolver do [Uri] recebido (SAF/MediaStore);
 * - `takePersistableUriPermission` mantém o acesso ao documento (porta SAF);
 * - o arquivo é COPIADO para `files/imports/AAAA-MM-DD_HHMMSS.ext` com o
 *   SHA-256 calculado na própria cópia (uma passada, sem reler);
 * - idempotência: mesmo SHA-256 já presente → NÃO duplica (arquivo parcial
 *   apagado) e devolve [ImportOutcome.Duplicata];
 * - metadados (título/artista/duração) vêm do `MediaMetadataRetriever`, com
 *   fallback no nome do arquivo e na duração medida pelo [MediaProbe];
 * - o cofre continua sendo respeitado: com a preferência ligada, a cópia em
 *   claro é cifrada ([VaultStore.protect]) como qualquer gravação.
 * Com cofre desligado o arquivo fica em `files/imports/` (privado do app).
 */
class ImportUseCase(
    private val context: Context,
    private val repo: RecordingRepository,
    private val metrics: UsageMetrics,
    private val prefs: PreferencesRepository,
    private val vaultStore: VaultStore,
) {

    suspend fun importFromUri(uri: Uri, persistable: Boolean = true): ImportOutcome =
        withContext(Dispatchers.IO) {
            // 1. Acesso persistível ao documento (só a porta SAF tem a concessão persistível;
            //    no ACTION_SEND a concessão morre com a Activity — tentar aqui lançaria SecurityException).
            if (persistable) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        PERSISTABLE_FLAGS,
                    )
                }
            }

            // 2. Formato suportado (mime e/ou extensão) — antes de copiar qualquer byte.
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            val nome = displayName(uri)
            val format = AudioImporter.formatFor(mime, nome) ?: return@withContext ImportOutcome.Unsupported

            // 3. Metadados ANTES da cópia (o retriever lê o documento via ContentResolver).
            val meta = metadados(uri)

            // 4. Cópia para a área privada com SHA-256 calculado na passada única.
            val destinoParcial = arquivoDestino(format)
            val sha256: String
            try {
                sha256 = copiarComHash(uri, destinoParcial)
            } catch (t: Throwable) {
                destinoParcial.delete()
                return@withContext ImportOutcome.Invalid
            }
            if (destinoParcial.length() == 0L) {
                destinoParcial.delete()
                return@withContext ImportOutcome.Invalid
            }

            // 5. Idempotência: mesmo arquivo duas vezes NÃO duplica.
            val existente = repo.bySha256(sha256)
            if (existente != null) {
                destinoParcial.delete()
                return@withContext ImportOutcome.Duplicata(existente.id)
            }

            // 6. Duração confiável: metadado do retriever, senão o probe decodifica o cabeçalho.
            val duracao = (meta.duracaoMs.takeIf { it > 0 } ?: MediaProbe.durationMillis(destinoParcial))
                ?: return@withContext ImportOutcome.Invalid.also { destinoParcial.delete() }
            if (duracao <= 0L) {
                destinoParcial.delete()
                return@withContext ImportOutcome.Invalid
            }

            // 6b. Parcial → nome final (renomear antes de inserir: sem linha apontando pra .tmp).
            val definitivo = File(destinoParcial.parentFile, destinoParcial.name.removeSuffix(TEMP_SUFFIX))
            if (!destinoParcial.renameTo(definitivo)) {
                destinoParcial.delete()
                return@withContext ImportOutcome.Invalid
            }

            // 7. Cofre ligado → cifra (a cópia em claro some); desligado → fica em files/imports/.
            val final = if (prefs.privateVault.first()) vaultStore.protect(definitivo) ?: definitivo else definitivo
            val agora = System.currentTimeMillis()
            val titulo = meta.titulo
                ?: nome?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: RecordingIngestor.defaultTitle(agora)
            val id = repo.insert(
                Recording(
                    title = titulo,
                    path = final.path,
                    format = format,
                    durationMillis = duracao,
                    sizeBytes = final.length(),
                    createdAtMillis = agora,
                    sha256 = sha256,
                    origin = RecordingOrigin.IMPORTED,
                    artist = meta.artista,
                ),
            )
            metrics.record(UsageEventType.IMPORTADO, duracao, format)
            ImportOutcome.Imported(id)
        }

    // ——— Internos ———

    private data class Metadados(val titulo: String?, val artista: String?, val duracaoMs: Long)

    /** Título/artista/duração do MediaMetadataRetriever; falha de metadado NÃO invalida a importação. */
    private fun metadados(uri: Uri): Metadados {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            Metadados(
                titulo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
                    ?.takeIf { it.isNotBlank() },
                artista = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                    ?.takeIf { it.isNotBlank() },
                duracaoMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
            )
        }.getOrElse { Metadados(null, null, 0L) }
            .also { runCatching { retriever.release() } }
    }

    /** `files/imports/AAAA-MM-DD_HHMMSS.ext` (roteiro §2); a cópia passa por `.tmp` e vira o nome final. */
    internal fun arquivoDestino(format: RecordingFormat): File {
        val dir = File(context.filesDir, IMPORT_DIR).apply { mkdirs() }
        val stamp = ZonedDateTime.now(ZoneId.systemDefault()).format(STAMP)
        var f = File(dir, "$stamp.${format.extension}")
        var n = 1
        while (f.exists()) f = File(dir, "$stamp-${n++}.${format.extension}") // mesma sekunde → sufixo
        return File(dir, "${f.name}$TEMP_SUFFIX")
    }

    /**
     * Copia o documento para [destino] calculando o SHA-256 na MESMA passada
     * (sem reler o arquivo — economia de IO e de bateria em arquivos grandes).
     * Qualquer erro de leitura propaga (o chamador apaga o parcial).
     */
    private fun copiarComHash(uri: Uri, destino: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { entrada ->
            FileOutputStream(destino).use { saida ->
                val buffer = ByteArray(COPIA_BUFFER)
                var lidos = entrada.read(buffer)
                while (lidos >= 0) {
                    digest.update(buffer, 0, lidos)
                    saida.write(buffer, 0, lidos)
                    lidos = entrada.read(buffer)
                }
            }
        } ?: throw IllegalStateException("abrir o documento falhou")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    companion object {
        const val IMPORT_DIR = "imports"
        const val TEMP_SUFFIX = ".tmp"

        /** Porta SAF: o picker concede leitura persistível (o write não é pedido). */
        const val PERSISTABLE_FLAGS =
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

        /** Roteiro: temporários de importação expiram em 24 h (limpos pelo ImportCleanupWorker). */
        const val TEMP_EXPIRA_MS = 24 * 60 * 60 * 1000L

        private const val COPIA_BUFFER = 64 * 1024

        private val STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

        /** Prefixo dos parciais — o worker de limpeza só apaga o que casa. */
        @VisibleForTesting
        fun eTemporario(arquivo: File): Boolean = arquivo.name.endsWith(TEMP_SUFFIX)
    }
}
