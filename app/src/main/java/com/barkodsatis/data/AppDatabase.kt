package com.barkodsatis.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Urun::class, Satis::class, SatisSatiri::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun urunDao(): UrunDao
    abstract fun satisDao(): SatisDao

    companion object {
        private val GECIS_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE urunler ADD COLUMN stok INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val GECIS_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE satislar ADD COLUMN iptal INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val GECIS_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE satislar ADD COLUMN nakitTutar REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE satislar ADD COLUMN kartTutar REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE satislar ADD COLUMN iadeFisId INTEGER")
                db.execSQL("UPDATE satislar SET nakitTutar = netToplam WHERE odemeTipi = 'NAKIT'")
                db.execSQL("UPDATE satislar SET kartTutar = netToplam WHERE odemeTipi = 'KREDI_KARTI'")
            }
        }

        const val DOSYA = "barkodsatis.db"

        /** Yedek almadan önce bekleyen yazımları ana dosyaya aktarır. */
        fun yedekIcinHazirla(context: Context) {
            get(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        }

        /** Veritabanını kapatır; geri yüklemeden önce çağrılır. */
        fun kapat() = synchronized(this) { instance?.close(); instance = null }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, DOSYA)
                .addMigrations(GECIS_1_2, GECIS_2_3, GECIS_3_4)
                .build().also { instance = it }
        }
    }
}
