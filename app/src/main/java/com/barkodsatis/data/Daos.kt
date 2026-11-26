package com.barkodsatis.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UrunDao {
    @Query("SELECT * FROM urunler WHERE barkod = :barkod LIMIT 1")
    suspend fun bul(barkod: String): Urun?

    @Query("SELECT * FROM urunler WHERE ad LIKE '%' || :q || '%' OR barkod LIKE :q || '%' ORDER BY ad LIMIT 200")
    fun ara(q: String): Flow<List<Urun>>

    @Query("SELECT COUNT(*) FROM urunler")
    suspend fun sayi(): Int

    @Upsert
    suspend fun kaydet(urun: Urun)

    @Upsert
    suspend fun kaydetHepsi(urunler: List<Urun>)
}

@Dao
abstract class SatisDao {
    @Insert
    abstract suspend fun satisEkle(satis: Satis): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun satirlarEkle(satirlar: List<SatisSatiri>)

    @Transaction
    open suspend fun satisKaydet(satis: Satis, satirlar: List<SatisSatiri>): Long {
        val id = satisEkle(satis)
        satirlarEkle(satirlar.map { it.copy(satisId = id) })
        return id
    }

    @Query("SELECT * FROM satislar WHERE zaman BETWEEN :bas AND :son ORDER BY zaman DESC")
    abstract fun aralik(bas: Long, son: Long): Flow<List<Satis>>
}
