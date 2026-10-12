package com.gravasom.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnnotationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: RecordingAnnotationEntity): Long

    @Query("SELECT * FROM recording_annotations WHERE recordingId = :recordingId ORDER BY createdAtMillis DESC")
    suspend fun forRecording(recordingId: Long): List<RecordingAnnotationEntity>

    @Query("SELECT COUNT(*) FROM recording_annotations WHERE recordingId = :recordingId")
    suspend fun countForRecording(recordingId: Long): Int

    @Query("DELETE FROM recording_annotations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
