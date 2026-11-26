package com.barkodsatis.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Urun::class, Satis::class, SatisSatiri::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun urunDao(): UrunDao
    abstract fun satisDao(): SatisDao

    companion object {
        private val GECIS_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE urunler ADD COLUMN stok INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "barkodsatis.db")
                .addMigrations(GECIS_1_2)
                .build().also { instance = it }
        }
    }
}
