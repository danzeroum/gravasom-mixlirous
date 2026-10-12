package com.gravasom.core.domain.repository

import com.gravasom.core.domain.model.Recording
import com.gravasom.core.domain.model.RecordingOrigin
import kotlinx.coroutines.flow.Flow

/** Contrato de dados: esconde Room + MediaStore. */
interface RecordingRepository {
    fun observeAll(): Flow<List<Recording>>
    fun search(query: String): Flow<List<Recording>>
    fun observeFavorites(): Flow<List<Recording>>
    fun observeTrash(): Flow<List<Recording>>
    suspend fun getById(id: Long): Recording?
    /** Gravação com o MESMO arquivo (mesmo SHA-256), ou null — importação idempotente (F6). */
    suspend fun bySha256(sha256: String): Recording?
    suspend fun insert(recording: Recording): Long
    suspend fun rename(id: Long, newTitle: String)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun setTags(id: Long, tags: List<String>)
    suspend fun moveToTrash(id: Long, nowMillis: Long)
    suspend fun restore(id: Long)
    suspend fun setPublishedUri(id: Long, uri: String?)
    suspend fun deleteForever(id: Long)
    suspend fun purgeTrash(olderThanMillis: Long, nowMillis: Long)
}
