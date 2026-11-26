package com.barkodsatis.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SepetTest {
    private val su = Urun("8690000000017", "Su 0,5 L", 0.0, 10.0)
    private val cay = Urun("8690000000024", "Çay 1 kg", 10.0, 250.0)

    @Test fun ayniBarkodAdetArtirir() {
        val s = Sepet().ekle(su).ekle(su, 3)
        assertEquals(1, s.satirlar.size)
        assertEquals(4, s.urunAdedi)
        assertEquals(40.0, s.netToplam, 0.001)
    }

    @Test fun iskontoHesabi() {
        val s = Sepet().ekle(cay, 2).ekle(su)
        assertEquals(510.0, s.brutToplam, 0.001)
        assertEquals(50.0, s.iskontoToplam, 0.001)
        assertEquals(460.0, s.netToplam, 0.001)
    }

    @Test fun sifirAdetSiler() {
        val s = Sepet().ekle(su).adetAyarla(su.barkod, 0)
        assertEquals(0, s.satirlar.size)
    }
}
