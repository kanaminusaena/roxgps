package com.roxgps.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.roxgps.module.utils.ScopeQualifiers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@Database(entities = [Favorite::class], version = 1,exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    class Callback @Inject constructor(@ApplicationScope private val applicationScope: CoroutineScope) : RoomDatabase.Callback(){
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            applicationScope.launch {

            }
        }
    }
}