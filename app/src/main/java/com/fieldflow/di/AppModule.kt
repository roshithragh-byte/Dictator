package com.fieldflow.di

import android.content.Context
import com.fieldflow.data.FieldFlowDao
import com.fieldflow.data.FieldFlowDatabase
import com.fieldflow.data.NoteRepository
import com.fieldflow.export.AndroidDocumentExporter
import com.fieldflow.export.DocumentExporter
import com.fieldflow.transform.DocumentTransformer
import com.fieldflow.transform.LocalAiClient
import com.fieldflow.transform.MlKitGeminiNanoClient
import com.fieldflow.watcher.AtomicTranscriptFileWatcher
import com.fieldflow.watcher.TranscriptFileWatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FieldFlowDatabase {
        return FieldFlowDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideFieldFlowDao(database: FieldFlowDatabase): FieldFlowDao {
        return database.dao()
    }

    @Provides
    @Singleton
    fun provideNoteRepository(dao: FieldFlowDao): NoteRepository {
        return NoteRepository(dao)
    }

    @Provides
    @Singleton
    fun provideAtomicTranscriptFileWatcher(@ApplicationContext context: Context): AtomicTranscriptFileWatcher {
        return AtomicTranscriptFileWatcher(context)
    }

    @Provides
    @Singleton
    fun provideTranscriptFileWatcher(watcher: AtomicTranscriptFileWatcher): TranscriptFileWatcher {
        return watcher
    }

    @Provides
    @Singleton
    fun provideMlKitGeminiNanoClient(): MlKitGeminiNanoClient {
        return MlKitGeminiNanoClient()
    }

    @Provides
    @Singleton
    fun provideLocalAiClient(nanoClient: MlKitGeminiNanoClient): LocalAiClient {
        return nanoClient
    }

    @Provides
    @Singleton
    fun provideDocumentTransformer(aiClient: LocalAiClient): DocumentTransformer {
        return DocumentTransformer(aiClient)
    }

    @Provides
    @Singleton
    fun provideAndroidDocumentExporter(): AndroidDocumentExporter {
        return AndroidDocumentExporter()
    }

    @Provides
    @Singleton
    fun provideDocumentExporter(exporter: AndroidDocumentExporter): DocumentExporter {
        return exporter
    }
}
