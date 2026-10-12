package com.gravasom.core.data.repository

import com.gravasom.core.data.db.RecordingDao
import com.gravasom.core.data.db.toDomain
import com.gravasom.core.data.db.toEntity
import com.gravasom.core.domain.model.Recording
import com.gravasom.core.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRecordingRepository(private val dao: RecordingDao) : RecordingRepository {

    override fun observeAll(): Flow<List<Recording>> = dao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun search(query: String): Flow<List<Recording>> =
        dao.search(query).map { it.map { e -> e.toDomain() } }

    override fun observeFavorites(): Flow<List<Recording>> =
        dao.observeFavorites().map { it.map { e -> e.toDomain() } }

    override fun observeTrash(): Flow<List<Recording>> =
        dao.observeTrash().map { it.map { e -> e.toDomain() } }

    override suspend fun getById(id: Long): Recording? = dao.getById(id)?.toDomain()

    override suspend fun bySha256(sha256: String): Recording? = dao.getBySha256(sha256)?.toDomain()

    override suspend fun insert(recording: Recording): Long = dao.insert(recording.toEntity())

    override suspend fun rename(id: Long, newTitle: String) = dao.rename(id, newTitle)

    override suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    override suspend fun setTags(id: Long, tags: List<String>) = dao.setTags(id, tags.joinToString(","))

    override suspend fun moveToTrash(id: Long, nowMillis: Long) = dao.moveToTrash(id, nowMillis)

    override suspend fun restore(id: Long) = dao.restore(id)

    override suspend fun setPublishedUri(id: Long, uri: String?) = dao.setPublishedUri(id, uri)

    override suspend fun deleteForever(id: Long) = dao.deleteForever(id)

    override suspend fun purgeTrash(olderThanMillis: Long, nowMillis: Long) =
        dao.purgeTrash(nowMillis - olderThanMillis)
}
