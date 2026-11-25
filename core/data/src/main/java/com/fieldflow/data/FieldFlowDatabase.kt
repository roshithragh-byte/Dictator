package com.fieldflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FolderSlotEntity::class,
        RecordingEntity::class,
        TranscriptSegmentEntity::class,
        StructuredDocEntity::class,
        RecordingFtsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FieldFlowDatabase : RoomDatabase() {
    abstract fun dao(): FieldFlowDao

    companion object {
        @Volatile
        private var instance: FieldFlowDatabase? = null

        fun getInstance(context: Context): FieldFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FieldFlowDatabase::class.java,
                    "fieldflow.db"
                ).build().also { instance = it }
            }
    }
}
