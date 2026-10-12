package com.gravasom.core.domain.usecase

import com.gravasom.core.domain.model.Recording
import com.gravasom.core.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Fake em memoria para testar os use cases sem Android. */
class FakeRecordingRepository : RecordingRepository {

    private val items = MutableStateFlow<List<Recording>>(emptyList())

    fun seed(vararg recordings: Recording) {
        items.value = recordings.toList()
    }

    override fun observeAll(): Flow<List<Recording>> =
        items.map { list -> list.filter { !it.isDeleted }.sortedByDescending { it.createdAtMillis } }

    override fun search(query: String): Flow<List<Recording>> =
        items.map { list -> list.filter { !it.isDeleted && it.title.contains(query, ignoreCase = true) } }

    override fun observeFavorites(): Flow<List<Recording>> =
        items.map { list -> list.filter { !it.isDeleted && it.isFavorite } }

    override fun observeTrash(): Flow<List<Recording>> =
        items.map { list -> list.filter { it.isDeleted } }

    override suspend fun getById(id: Long): Recording? = items.value.firstOrNull { it.id == id }

    override suspend fun bySha256(sha256: String): Recording? =
        items.value.firstOrNull { it.sha256 == sha256 }

    override suspend fun insert(recording: Recording): Long {
        val id = (items.value.maxOfOrNull { it.id } ?: 0L) + 1
        items.value = items.value + recording.copy(id = id)
        return id
    }

    override suspend fun rename(id: Long, newTitle: String) {
        items.value = items.value.map { if (it.id == id) it.copy(title = newTitle) else it }
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        items.value = items.value.map { if (it.id == id) it.copy(isFavorite = favorite) else it }
    }

    override suspend fun setTags(id: Long, tags: List<String>) {
        items.value = items.value.map { if (it.id == id) it.copy(tags = tags) else it }
    }

    override suspend fun moveToTrash(id: Long, nowMillis: Long) {
        items.value = items.value.map { if (it.id == id) it.copy(deletedAtMillis = nowMillis) else it }
    }

    override suspend fun setPublishedUri(id: Long, uri: String?) {
        items.value = items.value.map { if (it.id == id) it.copy(publishedUri = uri) else it }
    }

    override suspend fun restore(id: Long) {
        items.value = items.value.map { if (it.id == id) it.copy(deletedAtMillis = null) else it }
    }

    override suspend fun deleteForever(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun purgeTrash(olderThanMillis: Long, nowMillis: Long) {
        items.value = items.value.filterNot {
            it.deletedAtMillis != null && it.deletedAtMillis < nowMillis - olderThanMillis
        }
    }
}
