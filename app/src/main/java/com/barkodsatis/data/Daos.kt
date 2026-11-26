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

    @Query("SELECT barkod, stok FROM urunler")
    suspend fun tumStoklar(): List<BarkodStok>

    @Query("SELECT barkod, stok FROM urunler")
    fun stoklarAkis(): Flow<List<BarkodStok>>
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
        satirlar.forEach { stokDus(it.barkod, it.adet) }
        return id
    }

    @Query("UPDATE urunler SET stok = stok - :adet WHERE barkod = :barkod")
    abstract suspend fun stokDus(barkod: String, adet: Int)

    @Query("""SELECT l.satisId, s.zaman, s.odemeTipi, l.barkod, l.ad, l.adet, l.birimFiyat, l.iskontoYuzde
        FROM satis_satirlari l JOIN satislar s ON s.id = l.satisId
        WHERE s.zaman BETWEEN :bas AND :son ORDER BY s.zaman DESC, l.id""")
    abstract fun raporSatirlari(bas: Long, son: Long): Flow<List<RaporSatiri>>

    @Query("SELECT * FROM satislar WHERE zaman BETWEEN :bas AND :son ORDER BY zaman DESC")
    abstract fun aralik(bas: Long, son: Long): Flow<List<Satis>>
}
