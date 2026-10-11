package com.gravasom.di

import android.content.Context
import androidx.work.WorkManager
import com.gravasom.core.data.db.GravasomDatabase
import com.gravasom.core.data.db.MixAnalysisStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** F2/F3 (Mixlirous): analise musical no Room v6 e WorkManager para os workers. */
@Module
@InstallIn(SingletonComponent::class)
object CriarModule {

    @Provides @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides @Singleton
    fun mixAnalysisStore(db: GravasomDatabase): MixAnalysisStore = MixAnalysisStore(db.mixAnalysisDao())
}
