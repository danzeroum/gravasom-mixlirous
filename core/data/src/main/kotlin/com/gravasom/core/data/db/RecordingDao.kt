package com.gravasom.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** O contrato de um DAO exige uma funcao por consulta/atualizacao: o limite de funcoes por interface nao se aplica aqui. */
@Suppress("TooManyFunctions")
@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings WHERE deletedAtMillis IS NULL ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE deletedAtMillis IS NULL AND title LIKE '%' || :query || '%' ORDER BY createdAtMillis DESC")
    fun search(query: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE deletedAtMillis IS NULL AND isFavorite = 1 ORDER BY createdAtMillis DESC")
    fun observeFavorites(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE deletedAtMillis IS NOT NULL ORDER BY deletedAtMillis DESC")
    fun observeTrash(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    /** true quando já existe gravação com o MESMO arquivo (importação idempotente — F6). */
    @Query("SELECT EXISTS(SELECT 1 FROM recordings WHERE sha256 = :sha256)")
    suspend fun existsBySha256(sha256: String): Boolean

    @Query("SELECT * FROM recordings WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getBySha256(sha256: String): RecordingEntity?

    @Insert
    suspend fun insert(entity: RecordingEntity): Long

    @Update
    suspend fun update(entity: RecordingEntity)

    @Query("UPDATE recordings SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE recordings SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE recordings SET tagsCsv = :tagsCsv WHERE id = :id")
    suspend fun setTags(id: Long, tagsCsv: String)

    @Query("UPDATE recordings SET deletedAtMillis = :now WHERE id = :id")
    suspend fun moveToTrash(id: Long, now: Long)

    @Query("UPDATE recordings SET publishedUri = :uri WHERE id = :id")
    suspend fun setPublishedUri(id: Long, uri: String?)

    @Query("UPDATE recordings SET deletedAtMillis = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteForever(id: Long)

    @Query("DELETE FROM recordings WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :cutoff")
    suspend fun purgeTrash(cutoff: Long)
}
