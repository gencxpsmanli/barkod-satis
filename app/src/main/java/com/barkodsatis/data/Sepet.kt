package com.barkodsatis.data

import kotlin.math.round

/** Para tutarlarını kuruşa yuvarlar. */
fun Double.kurus(): Double = round(this * 100) / 100

data class SepetSatiri(
    val urun: Urun,
    val adet: Int,
) {
    val brut: Double get() = (urun.fiyat * adet).kurus()
    val iskontoTutari: Double get() = (brut * urun.iskontoYuzde / 100).kurus()
    val net: Double get() = (brut - iskontoTutari).kurus()
}

/** Satış ekranındaki sepet. Değişmez yapı: her işlem yeni bir Sepet döner. */
data class Sepet(val satirlar: List<SepetSatiri> = emptyList()) {
    val brutToplam: Double get() = satirlar.sumOf { it.brut }.kurus()
    val iskontoToplam: Double get() = satirlar.sumOf { it.iskontoTutari }.kurus()
    val netToplam: Double get() = satirlar.sumOf { it.net }.kurus()
    val urunAdedi: Int get() = satirlar.sumOf { it.adet }

    /** Aynı barkod sepette varsa adedini artırır, yoksa yeni satır ekler. */
    fun ekle(urun: Urun, adet: Int = 1): Sepet {
        require(adet > 0)
        val i = satirlar.indexOfFirst { it.urun.barkod == urun.barkod }
        return if (i >= 0) {
            copy(satirlar = satirlar.toMutableList().also { it[i] = it[i].copy(adet = it[i].adet + adet) })
        } else {
            copy(satirlar = satirlar + SepetSatiri(urun, adet))
        }
    }

    fun adetAyarla(barkod: String, adet: Int): Sepet =
        if (adet <= 0) sil(barkod)
        else copy(satirlar = satirlar.map { if (it.urun.barkod == barkod) it.copy(adet = adet) else it })

    fun sil(barkod: String): Sepet = copy(satirlar = satirlar.filterNot { it.urun.barkod == barkod })

    /** nakit + kart = netToplam olmalı. Tek tip ödemede diğeri 0'dır. */
    fun satisaDonustur(odeme: OdemeTipi, zaman: Long, nakit: Double, kart: Double): Pair<Satis, List<SatisSatiri>> =
        Satis(
            zaman = zaman, odemeTipi = odeme,
            brutToplam = brutToplam, iskontoToplam = iskontoToplam, netToplam = netToplam,
            nakitTutar = nakit.kurus(), kartTutar = kart.kurus(),
        ) to satirlar.map {
            SatisSatiri(
                satisId = 0, barkod = it.urun.barkod, ad = it.urun.ad, adet = it.adet,
                birimFiyat = it.urun.fiyat, iskontoYuzde = it.urun.iskontoYuzde,
            )
        }
}
