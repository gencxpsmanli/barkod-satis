package com.barkodsatis.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.math.BigDecimal
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * .xlsx dosyasından ürün listesi okur. Başlık satırında şu sütunlar aranır
 * (sıra önemsiz, büyük/küçük harf önemsiz): Barkod, Ürün Adı, Satış İskontosu, Fiyatı.
 * Dışarıdan kütüphane kullanmaz: xlsx bir zip dosyasıdır, içindeki XML'ler doğrudan okunur.
 * Ayrıca noktalı virgül veya virgülle ayrılmış .csv dosyaları da okunur.
 */
object ExcelOkuyucu {

    data class Sonuc(val urunler: List<Urun>, val atlananSatirlar: List<Int>)

    class BicimHatasi(mesaj: String) : Exception(mesaj)

    fun oku(girdi: InputStream, dosyaAdi: String?): Sonuc {
        val bytes = girdi.readBytes()
        val zipMi = bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val satirlar = when {
            zipMi -> xlsxSatirlari(bytes)
            dosyaAdi?.lowercase()?.endsWith(".xls") == true ->
                throw BicimHatasi("Eski .xls biçimi desteklenmiyor. Dosyayı Excel'de \"Farklı Kaydet > .xlsx\" ile kaydedip tekrar deneyin.")
            else -> csvSatirlari(String(bytes, Charsets.UTF_8).removePrefix("﻿"))
        }
        return satirlardanUrunler(satirlar)
    }

    // ---- Başlık eşleme ----

    private fun normal(s: String) = s.lowercase(Locale("tr"))
        .replace('ı', 'i').replace('ü', 'u').replace('ş', 's').replace('ğ', 'g')
        .replace('ö', 'o').replace('ç', 'c').replace("i̇", "i")
        .filter { it.isLetterOrDigit() }

    private fun satirlardanUrunler(satirlar: List<List<String>>): Sonuc {
        val basIndex = satirlar.indexOfFirst { satir -> satir.any { normal(it).contains("barkod") } }
        if (basIndex < 0) throw BicimHatasi("\"Barkod\" başlığı bulunamadı. İlk satırda Barkod, Ürün Adı, Satış İskontosu, Fiyatı başlıkları olmalı.")
        val baslik = satirlar[basIndex].map(::normal)
        fun sutun(vararg anahtar: String) = baslik.indexOfFirst { h -> anahtar.any { h.contains(it) } }
        val cBarkod = sutun("barkod")
        val cAd = sutun("urunadi", "urunad", "adi", "urun", "stokadi", "aciklama")
        val cIsk = sutun("iskonto", "indirim")
        val cFiyat = sutun("fiyat", "tutar")
        if (cAd < 0 || cFiyat < 0) throw BicimHatasi("\"Ürün Adı\" ve \"Fiyatı\" başlıkları bulunamadı.")

        val urunler = LinkedHashMap<String, Urun>()
        val atlanan = mutableListOf<Int>()
        for (i in basIndex + 1 until satirlar.size) {
            val s = satirlar[i]
            fun hucre(c: Int) = if (c >= 0) s.getOrNull(c)?.trim().orEmpty() else ""
            val barkod = barkodDuzelt(hucre(cBarkod))
            val ad = hucre(cAd)
            if (barkod.isEmpty() && ad.isEmpty()) continue // boş satır
            val fiyat = sayi(hucre(cFiyat))
            if (barkod.isEmpty() || ad.isEmpty() || fiyat == null) { atlanan += i + 1; continue }
            val isk = sayi(hucre(cIsk).removePrefix("%").removeSuffix("%"))?.let {
                // Excel'de %10 hücresi 0.1 olarak saklanır
                if (it > 0 && it < 1 && !hucre(cIsk).contains("%")) it * 100 else it
            } ?: 0.0
            urunler[barkod] = Urun(barkod, ad, isk.coerceIn(0.0, 100.0), fiyat)
        }
        return Sonuc(urunler.values.toList(), atlanan)
    }

    /** "8.69000000017E12" gibi bilimsel gösterimi ve "123.0" sonlarını düzeltir. */
    private fun barkodDuzelt(s: String): String {
        if (s.isEmpty()) return s
        if (s.all { it.isDigit() }) return s
        return runCatching {
            val bd = BigDecimal(s.replace(',', '.'))
            if (bd.stripTrailingZeros().scale() <= 0) bd.toBigInteger().toString() else s
        }.getOrDefault(s)
    }

    /** "1.250,50" , "1250.50", "1250,5", "₺ 12" gibi biçimleri okur. */
    internal fun sayi(ham: String): Double? {
        var s = ham.replace("₺", "").replace("TL", "", ignoreCase = true).replace(" ", "").trim()
        if (s.isEmpty()) return null
        val virgul = s.lastIndexOf(','); val nokta = s.lastIndexOf('.')
        s = when {
            virgul >= 0 && nokta >= 0 && virgul > nokta -> s.replace(".", "").replace(',', '.')
            virgul >= 0 && nokta >= 0 -> s.replace(",", "")
            virgul >= 0 -> s.replace(',', '.')
            else -> s
        }
        return s.toDoubleOrNull()
    }

    // ---- CSV ----

    private fun csvSatirlari(metin: String): List<List<String>> {
        val satirlar = metin.lines().filter { it.isNotBlank() }
        val ayirici = if (satirlar.firstOrNull()?.contains(';') == true) ';' else ','
        return satirlar.map { satir ->
            val alanlar = mutableListOf<String>(); val sb = StringBuilder(); var tirnak = false
            var i = 0
            while (i < satir.length) {
                val c = satir[i]
                when {
                    c == '"' && tirnak && i + 1 < satir.length && satir[i + 1] == '"' -> { sb.append('"'); i++ }
                    c == '"' -> tirnak = !tirnak
                    c == ayirici && !tirnak -> { alanlar += sb.toString(); sb.clear() }
                    else -> sb.append(c)
                }
                i++
            }
            alanlar += sb.toString(); alanlar
        }
    }

    // ---- XLSX ----

    private fun xlsxSatirlari(bytes: ByteArray): List<List<String>> {
        val dosyalar = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == "xl/sharedStrings.xml" || e.name.startsWith("xl/worksheets/sheet")) {
                    dosyalar[e.name] = zip.readBytes()
                }
            }
        }
        val paylasilan = dosyalar["xl/sharedStrings.xml"]?.let(::paylasilanMetinler) ?: emptyList()
        val sayfa = dosyalar["xl/worksheets/sheet1.xml"]
            ?: dosyalar.keys.filter { it.endsWith(".xml") }.minOrNull()?.let { dosyalar[it] }
            ?: throw BicimHatasi("Excel dosyasında sayfa bulunamadı.")
        return sayfaOku(sayfa, paylasilan)
    }

    private fun parser(b: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(b.inputStream(), "UTF-8")
    }

    private fun paylasilanMetinler(b: ByteArray): List<String> {
        val p = parser(b); val sonuc = mutableListOf<String>()
        var sb: StringBuilder? = null; var tIcinde = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> sb = StringBuilder()
                    "t" -> tIcinde = true
                }
                XmlPullParser.TEXT -> if (tIcinde) sb?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> tIcinde = false
                    "si" -> { sonuc += sb.toString(); sb = null }
                }
            }
        }
        return sonuc
    }

    private fun sutunNo(ref: String): Int {
        var n = 0
        for (c in ref) { if (!c.isLetter()) break; n = n * 26 + (c.uppercaseChar() - 'A' + 1) }
        return n - 1
    }

    private fun sayfaOku(b: ByteArray, paylasilan: List<String>): List<List<String>> {
        val p = parser(b); val satirlar = mutableListOf<List<String>>()
        var satir: MutableList<String>? = null
        var hucreTip: String? = null; var hucreSutun = 0
        var deger: StringBuilder? = null; var metinIcinde = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> satir = mutableListOf()
                    "c" -> {
                        hucreTip = p.getAttributeValue(null, "t")
                        hucreSutun = p.getAttributeValue(null, "r")?.let(::sutunNo) ?: (satir?.size ?: 0)
                        deger = StringBuilder()
                    }
                    "v", "t" -> metinIcinde = true
                }
                XmlPullParser.TEXT -> if (metinIcinde) deger?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> metinIcinde = false
                    "c" -> {
                        val ham = deger?.toString().orEmpty()
                        val metin = if (hucreTip == "s") ham.toIntOrNull()?.let { paylasilan.getOrNull(it) }.orEmpty() else ham
                        satir?.let { r ->
                            while (r.size < hucreSutun) r += ""
                            if (r.size == hucreSutun) r += metin else r[hucreSutun] = metin
                        }
                        deger = null
                    }
                    "row" -> { satir?.let { satirlar += it }; satir = null }
                }
            }
        }
        return satirlar
    }
}
