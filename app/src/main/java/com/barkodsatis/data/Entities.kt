package com.barkodsatis.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ürün kartı. Fiyat iskontosuz liste fiyatıdır, iskonto yüzde olarak tutulur (ör. 10.0 = %10). */
@Entity(tableName = "urunler")
data class Urun(
    @PrimaryKey val barkod: String,
    val ad: String,
    val iskontoYuzde: Double,
    val fiyat: Double,
)

enum class OdemeTipi { NAKIT, KREDI_KARTI }

@Entity(tableName = "satislar")
data class Satis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val zaman: Long,
    val odemeTipi: OdemeTipi,
    val brutToplam: Double,
    val iskontoToplam: Double,
    val netToplam: Double,
)

@Entity(
    tableName = "satis_satirlari",
    foreignKeys = [ForeignKey(
        entity = Satis::class,
        parentColumns = ["id"],
        childColumns = ["satisId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("satisId")],
)
data class SatisSatiri(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val satisId: Long,
    val barkod: String,
    val ad: String,
    val adet: Int,
    val birimFiyat: Double,
    val iskontoYuzde: Double,
)
