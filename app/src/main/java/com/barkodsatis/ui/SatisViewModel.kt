package com.barkodsatis.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.barkodsatis.data.AppDatabase
import com.barkodsatis.data.ExcelOkuyucu
import com.barkodsatis.data.OdemeTipi
import com.barkodsatis.data.Satis
import com.barkodsatis.data.SatisSatiri
import com.barkodsatis.data.Sepet
import com.barkodsatis.data.Urun
import com.barkodsatis.fis.FisPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    db.urunDao().kaydetHepsi(okunan.urunler)
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
