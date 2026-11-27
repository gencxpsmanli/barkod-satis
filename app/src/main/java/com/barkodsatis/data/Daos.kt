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

    /** Barkod alanına yazılan metne göre öneri (ad içinde veya barkod başında). */
    @Query("SELECT * FROM urunler WHERE ad LIKE '%' || :q || '%' OR barkod LIKE :q || '%' ORDER BY ad LIMIT 8")
    suspend fun oneri(q: String): List<Urun>

    @Query("DELETE FROM urunler WHERE barkod = :barkod")
    suspend fun sil(barkod: String)

    @Query("SELECT * FROM urunler WHERE stok <= :sinir ORDER BY stok, ad")
    fun kritikler(sinir: Int): Flow<List<Urun>>

    @Query("SELECT COUNT(*) FROM urunler WHERE stok <= :sinir")
    fun kritikSayisi(sinir: Int): Flow<Int>

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

    @Query("""SELECT l.satisId, s.zaman, s.odemeTipi, s.iadeFisId, l.barkod, l.ad, l.adet, l.birimFiyat, l.iskontoYuzde
        FROM satis_satirlari l JOIN satislar s ON s.id = l.satisId
        WHERE s.iptal = 0 AND s.zaman BETWEEN :bas AND :son ORDER BY s.zaman DESC, l.id""")
    abstract fun raporSatirlari(bas: Long, son: Long): Flow<List<RaporSatiri>>

    @Query("SELECT * FROM satis_satirlari WHERE satisId = :satisId ORDER BY id")
    abstract suspend fun satirlar(satisId: Long): List<SatisSatiri>

    @Query("UPDATE satislar SET iptal = 1 WHERE id = :satisId AND iptal = 0")
    abstract suspend fun iptalIsaretle(satisId: Long): Int

    @Query("SELECT * FROM satislar WHERE id = :id")
    abstract suspend fun satis(id: Long): Satis?

    @Query("SELECT * FROM satislar WHERE iadeFisId = :id")
    abstract suspend fun iadeleri(id: Long): List<Satis>

    @Query("DELETE FROM satislar WHERE id = :id")
    abstract suspend fun satisSilKayit(id: Long)

    @Query("DELETE FROM satis_satirlari WHERE satisId = :id")
    abstract suspend fun satirlariSil(id: Long)

    @androidx.room.Update
    abstract suspend fun satisGuncelleKayit(satis: Satis)

    /** Fişin stok etkisini geri alır (satışta düşen stoğu ekler, iadede eklenen stoğu düşer). */
    private suspend fun stokGeriAl(satisId: Long) {
        satirlar(satisId).forEach { stokDus(it.barkod, -it.adet) }
    }

    /** Fişi ve ona bağlı iade fişlerini iptal eder, stokları geri alır. */
    @Transaction
    open suspend fun fisIptal(satisId: Long): Boolean {
        val ilgili = listOf(satisId) + iadeleri(satisId).map { it.id }
        var oldu = false
        for (id in ilgili) {
            if (iptalIsaretle(id) > 0) { stokGeriAl(id); oldu = true }
        }
        return oldu
    }

    /** Fişi (ve iadelerini) tamamen siler. İptal edilmemişse stokları geri alınır. */
    @Transaction
    open suspend fun fisSil(satisId: Long) {
        val ilgili = listOfNotNull(satis(satisId)) + iadeleri(satisId)
        for (f in ilgili) {
            if (!f.iptal) stokGeriAl(f.id)
            satisSilKayit(f.id)
        }
    }

    /** Fişin içeriğini yenisiyle değiştirir: eski stok etkisi geri alınır, yenisi uygulanır. */
    @Transaction
    open suspend fun fisDuzenle(yeni: Satis, satirlar: List<SatisSatiri>) {
        val eski = satis(yeni.id) ?: return
        if (!eski.iptal) stokGeriAl(eski.id)
        satirlariSil(eski.id)
        satisGuncelleKayit(yeni.copy(zaman = eski.zaman, iptal = false, iadeFisId = eski.iadeFisId))
        satirlarEkle(satirlar.map { it.copy(id = 0, satisId = eski.id) })
        satirlar.forEach { stokDus(it.barkod, it.adet) }
    }

    /** Bir fişin her barkodu için daha önce iade edilmiş adet (pozitif). */
    @Query("""SELECT l.barkod AS barkod, -SUM(l.adet) AS stok FROM satis_satirlari l JOIN satislar s ON s.id = l.satisId
        WHERE s.iadeFisId = :id AND s.iptal = 0 GROUP BY l.barkod""")
    abstract suspend fun iadeEdilenler(id: Long): List<BarkodStok>

    @Query("SELECT * FROM satislar WHERE zaman BETWEEN :bas AND :son ORDER BY zaman DESC")
    abstract fun aralik(bas: Long, son: Long): Flow<List<Satis>>
}
