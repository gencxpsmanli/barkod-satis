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

enum class OdemeTipi(val etiket: String) { NAKIT("Nakit"), KREDI_KARTI("Kredi kartı"), KARISIK("Karışık") }

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
    /** Ödemenin nakit ve kart kısmı (karışık ödemede ikisi de dolu). İadede eksi değerlidir. */
    @ColumnInfo(defaultValue = "0") val nakitTutar: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val kartTutar: Double = 0.0,
    /** Bu bir iade fişiyse, iade edilen asıl fişin numarası. İade satırlarında adet eksidir. */
    val iadeFisId: Long? = null,
) {
    val iadeMi: Boolean get() = iadeFisId != null
    fun odemeMetni(): String = when (odemeTipi) {
        OdemeTipi.KARISIK -> "Nakit ${com.barkodsatis.fis.para(nakitTutar)} + Kart ${com.barkodsatis.fis.para(kartTutar)}"
        else -> odemeTipi.etiket
    }
}

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
    val iadeFisId: Long?,
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
