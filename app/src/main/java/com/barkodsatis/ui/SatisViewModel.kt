package com.barkodsatis.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.barkodsatis.data.AppDatabase
import com.barkodsatis.data.ExcelOkuyucu
import com.barkodsatis.data.ExcelYazici
import com.barkodsatis.data.OdemeTipi
import com.barkodsatis.data.RaporSatiri
import com.barkodsatis.data.Satis
import com.barkodsatis.data.SatisSatiri
import com.barkodsatis.data.Sepet
import com.barkodsatis.data.Urun
import com.barkodsatis.data.kurus
import com.barkodsatis.fis.FisPdf
import com.barkodsatis.fis.TR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

enum class RaporDonemi(val etiket: String) { BUGUN("Bugün"), DUN("Dün"), HAFTA("Son 7 gün"), AY("Bu ay"), TUMU("Tümü") }

/** Rapor ekranında bir ürünün dönem içindeki satış özeti (iadeler düşülmüş). */
data class UrunOzeti(val barkod: String, val ad: String, val adet: Int, val brut: Double, val iskonto: Double, val net: Double, val stok: Int?)

data class Rapor(
    val donem: RaporDonemi,
    val satirlar: List<RaporSatiri>,
    val urunler: List<UrunOzeti>,
    /** Dönemdeki iptal edilmemiş fişler (iadeler dahil). */
    val fisler: List<Satis>,
) {
    val satisSayisi: Int get() = fisler.count { !it.iadeMi }
    val iadeSayisi: Int get() = fisler.count { it.iadeMi }
    val urunAdedi: Int get() = satirlar.sumOf { it.adet }
    val brut: Double get() = fisler.sumOf { it.brutToplam }.kurus()
    val iskonto: Double get() = fisler.sumOf { it.iskontoToplam }.kurus()
    val iadeToplam: Double get() = fisler.filter { it.iadeMi }.sumOf { it.netToplam }.kurus()
    val net: Double get() = fisler.sumOf { it.netToplam }.kurus()
    val nakit: Double get() = fisler.sumOf { it.nakitTutar }.kurus()
    val kart: Double get() = fisler.sumOf { it.kartTutar }.kurus()
}

/** Tamamlanan satış: fiş göndermek için saklanır. alinan: nakitte müşterinin verdiği para. */
data class TamamlananSatis(val satis: Satis, val satirlar: List<SatisSatiri>, val alinan: Double? = null) {
    val paraUstu: Double? get() = alinan?.let { (it - satis.nakitTutar).kurus() }
}

data class BekleyenSepet(val sepet: Sepet, val zaman: Long)

/** İade penceresi için: fiş, satırları ve her barkoddan daha önce iade edilen adet. */
data class IadeHazirligi(val fis: TamamlananSatis, val iadeEdilen: Map<String, Int>)

class SatisViewModel(app: Application) : AndroidViewModel(app) {
    private val db get() = AppDatabase.get(getApplication())
    private val prefs = app.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)
    private val ses = Ses(app)

    override fun onCleared() { ses.kapat() }

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
    fun mesajGosterildi() { _mesaj.value = null }

    // ================= Ayarlar =================
    var isletmeAdi: String
        get() = prefs.getString("isletmeAdi", "").orEmpty()
        set(v) { prefs.edit().putString("isletmeAdi", v).apply() }

    private val _kritikSinir = MutableStateFlow(prefs.getInt("kritikSinir", 5))
    val kritikSinir: StateFlow<Int> = _kritikSinir.asStateFlow()
    fun kritikSinirAyarla(n: Int) { _kritikSinir.value = n; prefs.edit().putInt("kritikSinir", n).apply() }

    private val _sesAcik = MutableStateFlow(prefs.getBoolean("sesAcik", true))
    val sesAcik: StateFlow<Boolean> = _sesAcik.asStateFlow()
    fun sesAyarla(acik: Boolean) { _sesAcik.value = acik; prefs.edit().putBoolean("sesAcik", acik).apply() }

    private fun bip() { if (_sesAcik.value) ses.basarili() }
    private fun hataSesi() { if (_sesAcik.value) ses.hata() }

    // ---- Şifre (Rapor ve Ayarlar için) ----
    private val sifre: String get() = prefs.getString("sifre", "").orEmpty()
    val sifreVar: Boolean get() = sifre.isNotEmpty()
    private val _kilitAcik = MutableStateFlow(!sifreVar)
    val kilitAcik: StateFlow<Boolean> = _kilitAcik.asStateFlow()

    fun kilidiAc(girilen: String): Boolean = (girilen == sifre).also { if (it) _kilitAcik.value = true }
    fun kilitle() { if (sifreVar) _kilitAcik.value = false }
    fun sifreAyarla(yeni: String) {
        prefs.edit().putString("sifre", yeni.trim()).apply()
        _mesaj.value = if (yeni.isBlank()) "Şifre kaldırıldı." else "Şifre kaydedildi."
    }

    // ================= Fiyat gör =================
    private val _fiyatGorModu = MutableStateFlow(false)
    val fiyatGorModu: StateFlow<Boolean> = _fiyatGorModu.asStateFlow()
    private val _fiyatKarti = MutableStateFlow<Urun?>(null)
    val fiyatKarti: StateFlow<Urun?> = _fiyatKarti.asStateFlow()
    fun fiyatGorDegistir() { _fiyatGorModu.update { !it } }
    fun fiyatKartiKapat() { _fiyatKarti.value = null }

    // ================= Adet tuşları =================
    fun rakam(r: Int) = _bekleyenAdet.update { (it + r).trimStart('0').take(4) }
    fun adetTemizle() { _bekleyenAdet.value = "" }
    private fun adetAl(): Int = (_bekleyenAdet.value.toIntOrNull() ?: 1).coerceAtLeast(1).also { _bekleyenAdet.value = "" }

    // ================= Barkod =================
    val barkodSorgu = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val oneriler: StateFlow<List<Urun>> = barkodSorgu
        .debounce(200)
        .mapLatest { q -> val t = q.trim(); if (t.length < 2) emptyList() else db.urunDao().oneri(t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Terazi barkodu: 27/28/29 ile başlayan 13 haneli EAN. İlk 7 hane ürün kodu (veya 3-7. haneler),
     * 8-12. haneler gram cinsinden ağırlıktır. Ürünün fiyatı kilogram fiyatı olarak kaydedilmelidir.
     */
    private suspend fun teraziUrunu(barkod: String): Urun? {
        if (barkod.length != 13 || !barkod.all { it.isDigit() } || barkod.take(2) !in setOf("27", "28", "29")) return null
        val u = db.urunDao().bul(barkod.take(7)) ?: db.urunDao().bul(barkod.substring(2, 7)) ?: return null
        val gram = barkod.substring(7, 12).toInt()
        if (gram <= 0) return null
        val kg = gram / 1000.0
        return u.copy(
            barkod = barkod,
            ad = "${u.ad} (${String.format(TR, "%.3f", kg)} kg)",
            fiyat = (u.fiyat * kg).kurus(),
        )
    }

    fun barkodOkundu(ham: String) {
        val barkod = ham.trim()
        barkodSorgu.value = ""
        if (barkod.isEmpty() || _bilinmeyenBarkod.value != null) return
        viewModelScope.launch {
            val urun = db.urunDao().bul(barkod) ?: teraziUrunu(barkod)
            if (urun == null) {
                hataSesi()
                if (_fiyatGorModu.value) {
                    _fiyatGorModu.value = false; _fiyatKarti.value = null
                    _mesaj.value = "Bu barkod kayıtlı değil: $barkod"
                } else {
                    _fiyatKarti.value = null
                    _bilinmeyenBarkod.value = barkod
                }
            } else urunIsle(urun)
        }
    }

    fun oneriSecildi(urun: Urun) { barkodSorgu.value = ""; urunIsle(urun) }

    private fun urunIsle(urun: Urun) {
        bip()
        if (_fiyatGorModu.value) {
            _fiyatGorModu.value = false
            _fiyatKarti.value = urun
            return
        }
        _fiyatKarti.value = null
        _sepet.update { it.ekle(urun, adetAl()) }
    }

    fun sepeteEkleBarkodlar(barkodlar: List<String>) = viewModelScope.launch {
        val urunler = barkodlar.mapNotNull { db.urunDao().bul(it) }
        _sepet.update { s -> urunler.fold(s) { acc, u -> acc.ekle(u, 1) } }
        _mesaj.value = "${urunler.size} ürün satış ekranına eklendi."
    }

    fun elleEkle(urun: Urun) {
        viewModelScope.launch {
            db.urunDao().kaydet(urun)
            _sepet.update { it.ekle(urun, adetAl()) }
            _bilinmeyenBarkod.value = null
        }
    }

    fun elleEklemeIptal() { _bilinmeyenBarkod.value = null }
    fun yeniUrunFormu() { _bilinmeyenBarkod.value = "" }

    fun adetAyarla(barkod: String, adet: Int) = _sepet.update { it.adetAyarla(barkod, adet) }
    fun sil(barkod: String) = _sepet.update { it.sil(barkod) }
    fun sepetiTemizle() { _sepet.value = Sepet(); _bekleyenAdet.value = ""; _duzenlenenFis.value = null }

    // ================= Sepeti beklet =================
    private val _bekleyenler = MutableStateFlow<List<BekleyenSepet>>(emptyList())
    val bekleyenler: StateFlow<List<BekleyenSepet>> = _bekleyenler.asStateFlow()

    fun sepetiBeklet() {
        val s = _sepet.value
        if (s.satirlar.isEmpty() || _duzenlenenFis.value != null) return
        _bekleyenler.update { it + BekleyenSepet(s, System.currentTimeMillis()) }
        _sepet.value = Sepet()
        _mesaj.value = "Sepet bekletmeye alındı."
    }

    /** Bekleyen sepeti geri getirir; o an sepette ürün varsa o da bekletmeye alınır. */
    fun bekleyeniAl(b: BekleyenSepet) {
        val simdiki = _sepet.value
        _bekleyenler.update { l -> (l - b) + if (simdiki.satirlar.isNotEmpty()) listOf(BekleyenSepet(simdiki, System.currentTimeMillis())) else emptyList() }
        _sepet.value = b.sepet
    }

    fun bekleyeniSil(b: BekleyenSepet) = _bekleyenler.update { it - b }

    // ================= Ödeme =================
    /** Düzenlenmekte olan eski fiş. Doluyken ödeme yeni fiş açmaz, bu fişi günceller. */
    private val _duzenlenenFis = MutableStateFlow<Satis?>(null)
    val duzenlenenFis: StateFlow<Satis?> = _duzenlenenFis.asStateFlow()

    /**
     * Satışı tamamlar. nakit + kart toplamı sepet tutarına eşit olmalıdır.
     * alinan: nakitte müşterinin verdiği para (para üstü için), yoksa null.
     */
    fun odemeAl(tip: OdemeTipi, nakit: Double, kart: Double, alinan: Double? = null) {
        val sepet = _sepet.value
        if (sepet.satirlar.isEmpty()) return
        viewModelScope.launch {
            val (satis, satirlar) = sepet.satisaDonustur(tip, System.currentTimeMillis(), nakit, kart)
            val duzenlenen = _duzenlenenFis.value
            val id = if (duzenlenen != null) {
                db.satisDao().fisDuzenle(satis.copy(id = duzenlenen.id), satirlar); duzenlenen.id
            } else db.satisDao().satisKaydet(satis, satirlar)
            val kayitli = db.satisDao().satis(id) ?: satis.copy(id = id)
            _sonSatis.value = TamamlananSatis(kayitli, db.satisDao().satirlar(id), alinan)
            _sepet.value = Sepet()
            _duzenlenenFis.value = null
            if (duzenlenen != null) _mesaj.value = "Fiş No $id güncellendi."
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

    // ================= Ürünler =================
    val aramaMetni = MutableStateFlow("")
    val sadeceKritik = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val urunler: StateFlow<List<Urun>> = combine(aramaMetni, sadeceKritik, _kritikSinir) { q, k, n -> Triple(q, k, n) }
        .flatMapLatest { (q, k, n) -> if (k) db.urunDao().kritikler(n) else db.urunDao().ara(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val kritikSayisi: StateFlow<Int> = _kritikSinir.flatMapLatest { db.urunDao().kritikSayisi(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun excelAktar(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val sonuc = runCatching {
                withContext(Dispatchers.IO) {
                    val ad = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    val okunan = app.contentResolver.openInputStream(uri)!!.use { ExcelOkuyucu.oku(it, ad) }
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

    fun urunGuncelle(eskiBarkod: String, urun: Urun, bitti: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (urun.barkod != eskiBarkod && db.urunDao().bul(urun.barkod) != null) {
                _mesaj.value = "Bu barkod başka bir üründe kayıtlı: ${urun.barkod}"
                bitti(false); return@launch
            }
            val d = db
            d.withTransaction {
                if (urun.barkod != eskiBarkod) d.urunDao().sil(eskiBarkod)
                d.urunDao().kaydet(urun)
            }
            _sepet.update { s ->
                s.copy(satirlar = s.satirlar.map { if (it.urun.barkod == eskiBarkod) it.copy(urun = urun) else it })
            }
            bitti(true)
        }
    }

    fun urunSil(barkod: String) = viewModelScope.launch {
        db.urunDao().sil(barkod)
        _mesaj.value = "Ürün silindi."
    }

    // ================= Rapor =================
    val raporDonemi = MutableStateFlow(RaporDonemi.BUGUN)

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

    /** Dönemdeki tüm fişler (iptal edilenler dahil), yeniden eskiye. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val fisler: StateFlow<List<Satis>> = raporDonemi
        .flatMapLatest { d -> val (b, e) = aralik(d); db.satisDao().aralik(b, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val rapor: StateFlow<Rapor?> = raporDonemi.flatMapLatest { d ->
        val (bas, son) = aralik(d)
        combine(db.satisDao().raporSatirlari(bas, son), db.urunDao().stoklarAkis(), db.satisDao().aralik(bas, son)) { satirlar, stoklar, fisler ->
            val stokMap = stoklar.associate { it.barkod to it.stok }
            val urunler = satirlar.groupBy { it.barkod }.map { (barkod, l) ->
                UrunOzeti(barkod, l.first().ad, l.sumOf { it.adet }, l.sumOf { it.brut }.kurus(), l.sumOf { it.iskontoTutari }.kurus(), l.sumOf { it.net }.kurus(), stokMap[barkod])
            }.sortedByDescending { it.adet }
            Rapor(d, satirlar, urunler, fisler.filter { !it.iptal })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun raporDosyaAdi(): String =
        "satis_raporu_${raporDonemi.value.name.lowercase()}_${SimpleDateFormat("yyyy-MM-dd_HHmm", TR).format(Date())}.xlsx"

    fun raporuExceleAktar(uri: Uri) {
        val r = rapor.value ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val sonuc = runCatching {
                withContext(Dispatchers.IO) {
                    val tarih = SimpleDateFormat("dd.MM.yyyy HH:mm", TR)
                    val ozet = listOf<List<Any?>>(
                        listOf("Satış Raporu", r.donem.etiket),
                        listOf("Oluşturma", tarih.format(Date())),
                        listOf("Satış sayısı", r.satisSayisi),
                        listOf("İade sayısı", r.iadeSayisi),
                        listOf("Satılan ürün adedi (iadeler düşülmüş)", r.urunAdedi),
                        listOf("Brüt toplam", r.brut),
                        listOf("Toplam indirim", r.iskonto),
                        listOf("İadeler", r.iadeToplam),
                        listOf("Net toplam", r.net),
                        listOf("Nakit", r.nakit),
                        listOf("Kredi kartı", r.kart),
                    )
                    val urunler = listOf(listOf<Any?>("Barkod", "Ürün Adı", "Satılan Adet", "Brüt", "İndirim", "Net", "Kalan Stok")) +
                        r.urunler.map { listOf(it.barkod, it.ad, it.adet, it.brut, it.iskonto, it.net, it.stok) }
                    val fisMap = r.fisler.associateBy { it.id }
                    val satislar = listOf(listOf<Any?>("Tarih", "Fiş No", "Tür", "Ödeme", "Barkod", "Ürün Adı", "Adet", "Birim Fiyat", "İskonto %", "İndirim", "Net")) +
                        r.satirlar.map {
                            listOf(tarih.format(Date(it.zaman)), it.satisId, if (it.iadeFisId != null) "İade (Fiş ${it.iadeFisId})" else "Satış",
                                fisMap[it.satisId]?.odemeMetni() ?: it.odemeTipi.etiket,
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

    // ================= Fiş işlemleri =================
    private val _acikFis = MutableStateFlow<TamamlananSatis?>(null)
    val acikFis: StateFlow<TamamlananSatis?> = _acikFis.asStateFlow()

    fun fisAc(satis: Satis) = viewModelScope.launch {
        _acikFis.value = TamamlananSatis(satis, db.satisDao().satirlar(satis.id))
    }

    fun fisKapat() { _acikFis.value = null }

    fun fisIptal(satisId: Long) = viewModelScope.launch {
        val oldu = db.satisDao().fisIptal(satisId)
        _acikFis.value = null
        _mesaj.value = if (oldu) "Fiş No $satisId iptal edildi, stoklar geri alındı." else "Fiş zaten iptal edilmiş."
    }

    fun fisSil(satisId: Long) = viewModelScope.launch {
        db.satisDao().fisSil(satisId)
        _acikFis.value = null
        _mesaj.value = "Fiş No $satisId silindi."
    }

    /** Eski fişi satış ekranına yükler; ödeme alınınca fiş güncellenir. */
    fun fisDuzenle(t: TamamlananSatis, satisaGit: () -> Unit) = viewModelScope.launch {
        if (t.satis.iadeMi || db.satisDao().iadeleri(t.satis.id).any { !it.iptal }) {
            _mesaj.value = "İadesi olan fişler ve iade fişleri düzenlenemez. İptal edip yeniden satış yapabilirsiniz."
            return@launch
        }
        if (_sepet.value.satirlar.isNotEmpty() && _duzenlenenFis.value == null) sepetiBeklet()
        var s = Sepet()
        for (l in t.satirlar) {
            val stok = db.urunDao().bul(l.barkod)?.stok ?: 0
            s = s.ekle(Urun(l.barkod, l.ad, l.iskontoYuzde, l.birimFiyat, stok), l.adet)
        }
        _sepet.value = s
        _duzenlenenFis.value = t.satis
        _acikFis.value = null
        satisaGit()
    }

    fun duzenlemeyiBirak() { _duzenlenenFis.value = null; _sepet.value = Sepet() }

    fun fisGonder(context: Context, t: TamamlananSatis) {
        viewModelScope.launch {
            val dosya = withContext(Dispatchers.IO) { FisPdf.olustur(context, t.satis, t.satirlar, isletmeAdi) }
            FisPdf.epostaIleGonder(context, dosya, t.satis.id, null)
        }
    }

    // ---- Ürün bazında iade ----
    private val _iade = MutableStateFlow<IadeHazirligi?>(null)
    val iade: StateFlow<IadeHazirligi?> = _iade.asStateFlow()

    fun iadeBaslat(t: TamamlananSatis) = viewModelScope.launch {
        val edilen = db.satisDao().iadeEdilenler(t.satis.id).associate { it.barkod to it.stok }
        _acikFis.value = null
        _iade.value = IadeHazirligi(t, edilen)
    }

    fun fisKapatIade() { _iade.value = null }

    /** Seçilen adetler için eksi adetli bir iade fişi oluşturur; stoklar geri eklenir. */
    fun iadeYap(adetler: Map<String, Int>) {
        val h = _iade.value ?: return
        val asil = h.fis.satis
        val satirlar = h.fis.satirlar.mapNotNull { l ->
            val n = adetler[l.barkod] ?: 0
            if (n <= 0) null else l.copy(id = 0, satisId = 0, adet = -n)
        }
        if (satirlar.isEmpty()) return
        viewModelScope.launch {
            var brut = 0.0; var isk = 0.0
            satirlar.forEach {
                val b = (it.birimFiyat * it.adet).kurus(); brut += b; isk += (b * it.iskontoYuzde / 100).kurus()
            }
            val net = (brut - isk).kurus()
            val tip = if (asil.odemeTipi == OdemeTipi.KREDI_KARTI) OdemeTipi.KREDI_KARTI else OdemeTipi.NAKIT
            val iadeFisi = Satis(
                zaman = System.currentTimeMillis(), odemeTipi = tip,
                brutToplam = brut.kurus(), iskontoToplam = isk.kurus(), netToplam = net,
                nakitTutar = if (tip == OdemeTipi.NAKIT) net else 0.0,
                kartTutar = if (tip == OdemeTipi.KREDI_KARTI) net else 0.0,
                iadeFisId = asil.id,
            )
            val id = db.satisDao().satisKaydet(iadeFisi, satirlar)
            _iade.value = null
            _mesaj.value = "İade fişi No $id oluşturuldu: ${com.barkodsatis.fis.para(-net)} ${tip.etiket.lowercase()} iade edilecek."
        }
    }

    // ================= Yedekleme =================
    fun yedekDosyaAdi(): String = "barkodsatis_yedek_${SimpleDateFormat("yyyy-MM-dd_HHmm", TR).format(Date())}.db"

    fun yedekAl(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val sonuc = runCatching {
                withContext(Dispatchers.IO) {
                    AppDatabase.yedekIcinHazirla(app)
                    val dosya = app.getDatabasePath(AppDatabase.DOSYA)
                    app.contentResolver.openOutputStream(uri)!!.use { out -> dosya.inputStream().use { it.copyTo(out) } }
                }
            }
            _mesaj.value = sonuc.fold({ "Yedek kaydedildi. Bu dosyayı Drive'a veya bilgisayarınıza kopyalayın." }, { "Yedek alınamadı: ${it.message}" })
        }
    }

    /** Yedek dosyasını geri yükler ve uygulamayı yeniden başlatır. */
    fun yedekYukle(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val sonuc = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    require(bytes.size > 100 && String(bytes, 0, 15, Charsets.US_ASCII) == "SQLite format 3") {
                        "Seçilen dosya bir Barkod Satış yedeği değil."
                    }
                    AppDatabase.kapat()
                    val hedef = app.getDatabasePath(AppDatabase.DOSYA)
                    File(hedef.path + "-wal").delete(); File(hedef.path + "-shm").delete()
                    hedef.writeBytes(bytes)
                }
            }
            sonuc.onSuccess {
                val i = app.packageManager.getLaunchIntentForPackage(app.packageName)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                app.startActivity(i)
                Runtime.getRuntime().exit(0)
            }.onFailure { _mesaj.value = "Geri yüklenemedi: ${it.message}" }
        }
    }
}
