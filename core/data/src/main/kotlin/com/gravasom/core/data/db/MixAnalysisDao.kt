package com.gravasom.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Leitura e escrita da analise musical (F2). Upsert por gravacao; nada parcial é salvo. */
@Dao
interface MixAnalysisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MixAnalysisEntity)

    @Query("SELECT * FROM mix_analysis WHERE recordingId = :recordingId")
    suspend fun get(recordingId: Long): MixAnalysisEntity?

    @Query("SELECT * FROM mix_analysis WHERE recordingId = :recordingId")
    fun observe(recordingId: Long): Flow<MixAnalysisEntity?>

    /** Mapa por id para a biblioteca (etiquetas + filtros de BPM/tom). */
    @Query("SELECT * FROM mix_analysis")
    fun observeAll(): Flow<List<MixAnalysisEntity>>

    @Query("SELECT COUNT(*) FROM mix_analysis")
    suspend fun count(): Int

    @Query("DELETE FROM mix_analysis WHERE recordingId = :recordingId")
    suspend fun delete(recordingId: Long)
}
