package com.barkodsatis.data

import androidx.room.ColumnInfo
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
    /** Eldeki stok adedi. Her satışta düşer, eksiye inebilir. */
    @ColumnInfo(defaultValue = "0") val stok: Int = 0,
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
    /** İptal edilen fiş raporlara girmez, stoğu geri eklenmiştir. */
    @ColumnInfo(defaultValue = "0") val iptal: Boolean = false,
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

/** Rapor için satış satırı + bağlı olduğu satışın bilgileri. */
data class RaporSatiri(
    val satisId: Long,
    val zaman: Long,
    val odemeTipi: OdemeTipi,
    val barkod: String,
    val ad: String,
    val adet: Int,
    val birimFiyat: Double,
    val iskontoYuzde: Double,
) {
    val brut: Double get() = (birimFiyat * adet).kurus()
    val iskontoTutari: Double get() = (brut * iskontoYuzde / 100).kurus()
    val net: Double get() = (brut - iskontoTutari).kurus()
}

data class BarkodStok(val barkod: String, val stok: Int)
