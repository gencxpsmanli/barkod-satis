package com.barkodsatis.fis

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.barkodsatis.data.Satis
import com.barkodsatis.data.SatisSatiri
import com.barkodsatis.data.kurus
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val TR = Locale("tr", "TR")
fun para(d: Double): String = NumberFormat.getCurrencyInstance(TR).format(d)

/** "Mali değeri yoktur" ibareli bilgi fişini PDF olarak üretir ve e-posta ile paylaşır. */
object FisPdf {

    fun olustur(context: Context, satis: Satis, satirlar: List<SatisSatiri>, isletmeAdi: String): File {
        val genislik = 300 // pt, fiş genişliği (~105 mm)
        val satirYuksekligi = 14f
        val yukseklik = (220 + satirlar.size * satirYuksekligi * 2).toInt()
        val doc = PdfDocument()
        val sayfa = doc.startPage(PdfDocument.PageInfo.Builder(genislik, yukseklik, 1).create())
        val c = sayfa.canvas
        val normal = Paint().apply { textSize = 9f; isAntiAlias = true }
        val kalin = Paint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val baslik = Paint(kalin).apply { textSize = 13f; textAlign = Paint.Align.CENTER }
        val orta = Paint(normal).apply { textAlign = Paint.Align.CENTER }
        val sag = Paint(normal).apply { textAlign = Paint.Align.RIGHT }
        val sagKalin = Paint(kalin).apply { textAlign = Paint.Align.RIGHT; textSize = 11f }
        val sol = 14f; val sagX = genislik - 14f; val ortaX = genislik / 2f
        var y = 26f

        c.drawText(isletmeAdi.ifBlank { "SATIŞ FİŞİ" }, ortaX, y, baslik); y += 16
        c.drawText("BİLGİ FİŞİ - MALİ DEĞERİ YOKTUR", ortaX, y, Paint(kalin).apply { textAlign = Paint.Align.CENTER }); y += 14
        if (satis.iadeFisId != null) {
            c.drawText("İADE FİŞİ (Asıl fiş No: ${satis.iadeFisId})", ortaX, y, Paint(kalin).apply { textAlign = Paint.Align.CENTER }); y += 14
        }
        c.drawText(SimpleDateFormat("dd.MM.yyyy HH:mm", TR).format(Date(satis.zaman)) + "   Fiş No: ${satis.id}", ortaX, y, orta); y += 10
        c.drawLine(sol, y, sagX, y, normal); y += 14

        for (s in satirlar) {
            val brut = (s.birimFiyat * s.adet).kurus()
            val isk = (brut * s.iskontoYuzde / 100).kurus()
            c.drawText(s.ad.take(42), sol, y, normal); y += satirYuksekligi - 2
            val detay = "${s.adet} x ${para(s.birimFiyat)}" +
                if (s.iskontoYuzde > 0) "  İsk. %${yuzde(s.iskontoYuzde)} (-${para(isk)})" else ""
            c.drawText(detay, sol + 6, y, Paint(normal).apply { textSize = 8f })
            c.drawText(para(brut - isk), sagX, y, sag); y += satirYuksekligi + 2
        }
        c.drawLine(sol, y - 6, sagX, y - 6, normal); y += 6

        fun toplamSatiri(etiket: String, deger: String, p: Paint = sag) {
            c.drawText(etiket, sol, y, if (p === sagKalin) kalin else normal); c.drawText(deger, sagX, y, p); y += 14
        }
        toplamSatiri("Ara Toplam", para(satis.brutToplam))
        if (satis.iskontoToplam != 0.0) toplamSatiri("Toplam İndirim", "-" + para(satis.iskontoToplam))
        toplamSatiri("TOPLAM", para(satis.netToplam), sagKalin)
        toplamSatiri(if (satis.iadeFisId != null) "İade şekli" else "Ödeme", satis.odemeMetni())
        y += 10
        c.drawText("Bu belge bilgi amaçlıdır, mali değeri yoktur.", ortaX, y, orta); y += 12
        c.drawText("Teşekkür ederiz.", ortaX, y, orta)

        doc.finishPage(sayfa)
        val klasor = File(context.cacheDir, "fisler").apply { mkdirs() }
        val dosya = File(klasor, "satis_fisi_${satis.id}.pdf")
        dosya.outputStream().use { doc.writeTo(it) }
        doc.close()
        return dosya
    }

    fun epostaIleGonder(context: Context, dosya: File, satisId: Long, alici: String?) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", dosya)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Satış Fişi No: $satisId")
            putExtra(Intent.EXTRA_TEXT, "Satış fişiniz ektedir. Bu belge bilgi amaçlıdır, mali değeri yoktur.")
            if (!alici.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(alici.trim()))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Fişi gönder").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun yuzde(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(TR, "%.2f", d)
}
