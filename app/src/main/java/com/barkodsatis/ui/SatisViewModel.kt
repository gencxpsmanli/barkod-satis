package com.barkodsatis.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.barkodsatis.data.AppDatabase
import com.barkodsatis.data.ExcelOkuyucu
import com.barkodsatis.data.ExcelYazici
import com.barkodsatis.data.RaporSatiri
import com.barkodsatis.data.OdemeTipi
import com.barkodsatis.data.Satis
import com.barkodsatis.data.SatisSatiri
import com.barkodsatis.data.Sepet
import com.barkodsatis.data.Urun
import com.barkodsatis.fis.FisPdf
import com.barkodsatis.fis.TR
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RaporDonemi(val etiket: String) { BUGUN("Bugün"), DUN("Dün"), HAFTA("Son 7 gün"), AY("Bu ay"), TUMU("Tümü") }

/** Rapor ekranında bir ürünün dönem içindeki satış özeti. */
data class UrunOzeti(val barkod: String, val ad: String, val adet: Int, val brut: Double, val iskonto: Double, val net: Double, val stok: Int?)

data class Rapor(
    val donem: RaporDonemi,
    val satirlar: List<RaporSatiri>,
    val urunler: List<UrunOzeti>,
) {
    val satisSayisi: Int get() = satirlar.map { it.satisId }.distinct().size
    val urunAdedi: Int get() = satirlar.sumOf { it.adet }
    val brut: Double get() = satirlar.sumOf { it.brut }
    val iskonto: Double get() = satirlar.sumOf { it.iskontoTutari }
    val net: Double get() = satirlar.sumOf { it.net }
    val nakit: Double get() = satirlar.filter { it.odemeTipi == OdemeTipi.NAKIT }.sumOf { it.net }
    val kart: Double get() = satirlar.filter { it.odemeTipi == OdemeTipi.KREDI_KARTI }.sumOf { it.net }
}

/** Tamamlanan satış: fiş göndermek için saklanır. */
data class TamamlananSatis(val satis: Satis, val satirlar: List<SatisSatiri>)

class SatisViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val prefs = app.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)

    private val _sepet = MutableStateFlow(Sepet())
    val sepet: StateFlow<Sepet> = _sepet.asStateFlow()

    /** 1-9 tuşlarıyla girilen ve bir sonraki okutulan ürüne uygulanacak adet ("" = 1). */
    private val _bekleyenAdet = MutableStateFlow("")
    val bekleyenAdet: StateFlow<String> = _bekleyenAdet.asStateFlow()

    /** Veritabanında olmayan, elle eklenmeyi bekleyen barkod. */
    private val _bilinmeyenBarkod = MutableStateFlow<String?>(null)
    val bilinmeyenBarkod: StateFlow<String?> = _bilinmeyenBarkod.asStateFlow()

    private val _sonSatis = MutableStateFlow<TamamlananSatis?>(null)
    val sonSatis: StateFlow<TamamlananSatis?> = _sonSatis.asStateFlow()

    private val _mesaj = MutableStateFlow<String?>(null)
    val mesaj: StateFlow<String?> = _mesaj.asStateFlow()

    val aramaMetni = MutableStateFlow("")

    // ---- Fiyat gör ----
    /** true iken bir sonraki okutulan ürün sepete eklenmez, fiyatı gösterilir. */
    private val _fiyatGorModu = MutableStateFlow(false)
    val fiyatGorModu: StateFlow<Boolean> = _fiyatGorModu.asStateFlow()

    /** Ekranda gösterilen fiyat kartı. Yeni ürün okutulana veya elle kapatılana kadar kalır. */
    private val _fiyatKarti = MutableStateFlow<Urun?>(null)
    val fiyatKarti: StateFlow<Urun?> = _fiyatKarti.asStateFlow()

    fun fiyatGorDegistir() { _fiyatGorModu.update { !it } }
    fun fiyatKartiKapat() { _fiyatKarti.value = null }

    // ---- Rapor ----
    val raporDonemi = MutableStateFlow(RaporDonemi.BUGUN)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rapor: StateFlow<Rapor?> = raporDonemi.flatMapLatest { d ->
        val (bas, son) = aralik(d)
        combine(db.satisDao().raporSatirlari(bas, son), db.urunDao().stoklarAkis()) { satirlar, stoklar ->
            val stokMap = stoklar.associate { it.barkod to it.stok }
            val urunler = satirlar.groupBy { it.barkod }.map { (barkod, l) ->
                UrunOzeti(barkod, l.first().ad, l.sumOf { it.adet }, l.sumOf { it.brut }, l.sumOf { it.iskontoTutari }, l.sumOf { it.net }, stokMap[barkod])
            }.sortedByDescending { it.adet }
            Rapor(d, satirlar, urunler)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun aralik(d: RaporDonemi): Pair<Long, Long> {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val bugun = c.timeInMillis
        val gun = 24L * 60 * 60 * 1000
        return when (d) {
            RaporDonemi.BUGUN -> bugun to Long.MAX_VALUE
            RaporDonemi.DUN -> c.apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis to bugun - 1
            RaporDonemi.HAFTA -> (bugun - 6 * gun) to Long.MAX_VALUE
            RaporDonemi.AY -> c.apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis to Long.MAX_VALUE
            RaporDonemi.TUMU -> 0L to Long.MAX_VALUE
        }
    }

    fun raporDosyaAdi(): String =
        "satis_raporu_${raporDonemi.value.name.lowercase()}_${SimpleDateFormat("yyyy-MM-dd_HHmm", TR).format(Date())}.xlsx"

    fun raporuExceleAktar(uri: Uri) {
        val r = rapor.value ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val sonuc = runCatching {
                withContext(Dispatchers.IO) {
                    val tarih = SimpleDateFormat("dd.MM.yyyy HH:mm", TR)
                    val ozet = mutableListOf<List<Any?>>(
                        listOf("Satış Raporu", r.donem.etiket),
                        listOf("Oluşturma", tarih.format(Date())),
                        listOf("Satış sayısı", r.satisSayisi),
                        listOf("Satılan ürün adedi", r.urunAdedi),
                        listOf("Brüt toplam", r.brut),
                        listOf("Toplam indirim", r.iskonto),
                        listOf("Net toplam", r.net),
                        listOf("Nakit", r.nakit),
                        listOf("Kredi kartı", r.kart),
                    )
                    val urunler = listOf(listOf<Any?>("Barkod", "Ürün Adı", "Satılan Adet", "Brüt", "İndirim", "Net", "Kalan Stok")) +
                        r.urunler.map { listOf(it.barkod, it.ad, it.adet, it.brut, it.iskonto, it.net, it.stok) }
                    val satislar = listOf(listOf<Any?>("Tarih", "Fiş No", "Ödeme", "Barkod", "Ürün Adı", "Adet", "Birim Fiyat", "İskonto %", "İndirim", "Net")) +
                        r.satirlar.map {
                            listOf(tarih.format(Date(it.zaman)), it.satisId, if (it.odemeTipi == OdemeTipi.NAKIT) "Nakit" else "Kredi Kartı",
                                it.barkod, it.ad, it.adet, it.birimFiyat, it.iskontoYuzde, it.iskontoTutari, it.net)
                        }
                    app.contentResolver.openOutputStream(uri)!!.use {
                        ExcelYazici.yaz(it, listOf(
                            ExcelYazici.Sayfa("Özet", ozet),
                            ExcelYazici.Sayfa("Ürünler", urunler),
                            ExcelYazici.Sayfa("Satışlar", satislar),
                        ))
                    }
                }
            }
            _mesaj.value = sonuc.fold({ "Rapor Excel dosyası olarak kaydedildi." }, { "Kaydedilemedi: ${it.message}" })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val urunler: StateFlow<List<Urun>> = aramaMetni
        .flatMapLatest { db.urunDao().ara(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var isletmeAdi: String
        get() = prefs.getString("isletmeAdi", "").orEmpty()
        set(v) { prefs.edit().putString("isletmeAdi", v).apply() }

    fun mesajGosterildi() { _mesaj.value = null }

    // ---- Adet tuşları ----
    fun rakam(r: Int) = _bekleyenAdet.update { (it + r).trimStart('0').take(4) }
    fun adetTemizle() { _bekleyenAdet.value = "" }
    private fun adetAl(): Int = (_bekleyenAdet.value.toIntOrNull() ?: 1).coerceAtLeast(1).also { _bekleyenAdet.value = "" }

    // ---- Barkod ----
    fun barkodOkundu(ham: String) {
        val barkod = ham.trim()
        if (barkod.isEmpty() || _bilinmeyenBarkod.value != null) return
        viewModelScope.launch {
            val urun = db.urunDao().bul(barkod)
            if (_fiyatGorModu.value) {
                _fiyatGorModu.value = false
                if (urun == null) { _fiyatKarti.value = null; _mesaj.value = "Bu barkod kayıtlı değil: $barkod" }
                else _fiyatKarti.value = urun
                return@launch
            }
            _fiyatKarti.value = null
            if (urun == null) _bilinmeyenBarkod.value = barkod
            else _sepet.update { it.ekle(urun, adetAl()) }
        }
    }

    fun elleEkle(urun: Urun) {
        viewModelScope.launch {
            db.urunDao().kaydet(urun)
            _sepet.update { it.ekle(urun, adetAl()) }
            _bilinmeyenBarkod.value = null
        }
    }

    fun elleEklemeIptal() { _bilinmeyenBarkod.value = null }

    /** Barkodu olmayan bir ürünü elle eklemek için boş form açar. */
    fun yeniUrunFormu() { _bilinmeyenBarkod.value = "" }

    fun adetAyarla(barkod: String, adet: Int) = _sepet.update { it.adetAyarla(barkod, adet) }
    fun sil(barkod: String) = _sepet.update { it.sil(barkod) }
    fun sepetiTemizle() { _sepet.value = Sepet(); _bekleyenAdet.value = "" }

    // ---- Ödeme ----
    fun odemeAl(tip: OdemeTipi) {
        val sepet = _sepet.value
        if (sepet.satirlar.isEmpty()) return
        viewModelScope.launch {
            val (satis, satirlar) = sepet.satisaDonustur(tip, System.currentTimeMillis())
            val id = db.satisDao().satisKaydet(satis, satirlar)
            _sonSatis.value = TamamlananSatis(satis.copy(id = id), satirlar.map { it.copy(satisId = id) })
            _sepet.value = Sepet()
        }
    }

    fun sonSatisKapat() { _sonSatis.value = null }

    fun fisiGonder(context: Context, alici: String?) {
        val t = _sonSatis.value ?: return
        viewModelScope.launch {
            val dosya = withContext(Dispatchers.IO) { FisPdf.olustur(context, t.satis, t.satirlar, isletmeAdi) }
            FisPdf.epostaIleGonder(context, dosya, t.satis.id, alici)
        }
    }

    // ---- Excel ----
    fun excelAktar(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val sonuc = runCatching {
                withContext(Dispatchers.IO) {
                    val ad = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    val okunan = app.contentResolver.openInputStream(uri)!!.use { ExcelOkuyucu.oku(it, ad) }
                    // Excel'de Stok sütunu yoksa mevcut stoklar korunur
                    val kayit = if (okunan.stokSutunuVar) okunan.urunler else {
                        val mevcut = db.urunDao().tumStoklar().associate { it.barkod to it.stok }
                        okunan.urunler.map { it.copy(stok = mevcut[it.barkod] ?: 0) }
                    }
                    db.urunDao().kaydetHepsi(kayit)
                    okunan
                }
            }
            _mesaj.value = sonuc.fold(
                onSuccess = {
                    "${it.urunler.size} ürün aktarıldı." +
                        if (it.atlananSatirlar.isNotEmpty()) " Eksik bilgili ${it.atlananSatirlar.size} satır atlandı (satır: ${it.atlananSatirlar.take(10).joinToString()})." else ""
                },
                onFailure = { "Aktarılamadı: ${it.message}" },
            )
        }
    }

    fun urunKaydet(urun: Urun) = viewModelScope.launch { db.urunDao().kaydet(urun) }
}
