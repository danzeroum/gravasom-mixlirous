package com.gravasom.core.data.db

import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.model.Recording
import com.gravasom.core.domain.model.RecordingOrigin
import com.gravasom.core.domain.preset.FormatPreset

fun RecordingEntity.toDomain(): Recording = Recording(
    id = id,
    title = title,
    path = path,
    format = runCatching { RecordingFormat.valueOf(format) }.getOrDefault(RecordingFormat.M4A),
    durationMillis = durationMillis,
    sizeBytes = sizeBytes,
    createdAtMillis = createdAtMillis,
    isFavorite = isFavorite,
    folder = folder,
    tags = tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    deletedAtMillis = deletedAtMillis,
    publishedUri = publishedUri,
    preset = FormatPreset.fromId(preset),
    sha256 = sha256,
    origin = RecordingOrigin.fromId(origin), // nulo (linhas antigas) = gravada no app
    artist = artist,
)

fun Recording.toEntity(): RecordingEntity = RecordingEntity(
    id = id,
    title = title,
    path = path,
    format = format.name,
    durationMillis = durationMillis,
    sizeBytes = sizeBytes,
    createdAtMillis = createdAtMillis,
    isFavorite = isFavorite,
    folder = folder,
    tagsCsv = tags.joinToString(","),
    deletedAtMillis = deletedAtMillis,
    publishedUri = publishedUri,
    preset = preset?.id,
    sha256 = sha256,
    origin = origin?.id,
    artist = artist,
)
