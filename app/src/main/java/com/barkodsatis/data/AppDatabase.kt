package com.barkodsatis.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Urun::class, Satis::class, SatisSatiri::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun urunDao(): UrunDao
    abstract fun satisDao(): SatisDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "barkodsatis.db")
                .build().also { instance = it }
        }
    }
}
