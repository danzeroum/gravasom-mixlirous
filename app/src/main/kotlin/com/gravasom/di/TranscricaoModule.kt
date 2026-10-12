package com.gravasom.di

import android.content.Context
import com.gravasom.core.data.db.AnnotationDao
import com.gravasom.core.data.db.GravasomDatabase
import com.gravasom.core.data.modelmanager.ModelManager
import com.gravasom.core.data.modelmanager.ModelManagerImpl
import com.gravasom.core.data.modelmanager.ModelStore
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.feature.transcricao.TranscricaoUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** F7 — Transcrição local: ModelManager (ADR-017) + UseCase com a anotação do Room. */
@Module
@InstallIn(SingletonComponent::class)
object TranscricaoModule {

    @Provides @Singleton
    fun modelStore(@ApplicationContext context: Context): ModelStore = ModelStore.de(context)

    @Provides @Singleton
    fun modelManager(@ApplicationContext context: Context, store: ModelStore): ModelManager = ModelManagerImpl(context, store)

    @Provides
    fun annotationDao(db: GravasomDatabase): AnnotationDao = db.annotationDao()

    @Provides @Singleton
    fun transcricaoUseCase(repo: RecordingRepository, annotationDao: AnnotationDao): TranscricaoUseCase =
        TranscricaoUseCase(repo, annotationDao)
}
