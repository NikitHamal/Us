package com.us.copilot.di

import android.content.Context
import androidx.room.Room
import com.us.copilot.data.local.crypto.DatabaseKeyProvider
import com.us.copilot.data.local.dao.AnalysisDao
import com.us.copilot.data.local.dao.CheckInDao
import com.us.copilot.data.local.dao.MemoryDao
import com.us.copilot.data.local.dao.ProfileDao
import com.us.copilot.data.local.db.UsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): UsDatabase {
        SQLiteDatabase.loadLibs(context)
        val factory = SupportFactory(keyProvider.passphraseBytes(), null, false)
        return Room.databaseBuilder(context, UsDatabase::class.java, UsDatabase.NAME)
            .openHelperFactory(factory)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides fun provideProfileDao(db: UsDatabase): ProfileDao = db.profileDao()
    @Provides fun provideMemoryDao(db: UsDatabase): MemoryDao = db.memoryDao()
    @Provides fun provideCheckInDao(db: UsDatabase): CheckInDao = db.checkInDao()
    @Provides fun provideAnalysisDao(db: UsDatabase): AnalysisDao = db.analysisDao()
}
