package com.barkodsatis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.barkodsatis.data.ExcelOkuyucu
import com.barkodsatis.data.Urun
import com.barkodsatis.data.kurus
import com.barkodsatis.fis.FisPdf
import com.barkodsatis.fis.para

/** Ürün ekleme / düzenleme penceresi. İskonto yüzde olarak girilir, indirim tutarı anında gösterilir. */
@Composable
fun UrunFormu(
    baslik: String,
    baslangic: Urun,
    barkodDegistirilebilir: Boolean,
    kaydet: (Urun) -> Unit,
    iptal: () -> Unit,
) {
    var barkod by remember { mutableStateOf(baslangic.barkod) }
    var ad by remember { mutableStateOf(baslangic.ad) }
    var fiyat by remember { mutableStateOf(if (baslangic.fiyat > 0) baslangic.fiyat.toString().replace('.', ',') else "") }
    var iskonto by remember { mutableStateOf(if (baslangic.iskontoYuzde > 0) FisPdf.yuzde(baslangic.iskontoYuzde) else "") }

    val f = ExcelOkuyucu.sayi(fiyat)
    val i = if (iskonto.isBlank()) 0.0 else ExcelOkuyucu.sayi(iskonto)
    val gecerli = barkod.isNotBlank() && ad.isNotBlank() && f != null && f > 0 && i != null && i in 0.0..100.0

    AlertDialog(
        onDismissRequest = iptal,
        title = { Text(baslik) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(barkod, { barkod = it.trim() }, label = { Text("Barkod") },
                    enabled = barkodDegistirilebilir, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(ad, { ad = it }, label = { Text("Ürün adı") }, singleLine = true)
                OutlinedTextField(fiyat, { fiyat = it }, label = { Text("Fiyat (₺)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(iskonto, { iskonto = it }, label = { Text("İskonto oranı (%)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (f != null && i != null && i > 0) {
                    val ind = (f * i / 100).kurus()
                    Text("İndirim: ${para(ind)}  →  İndirimli fiyat: ${para(f - ind)}")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = gecerli, onClick = { kaydet(Urun(barkod.trim(), ad.trim(), i ?: 0.0, f!!)) }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = iptal) { Text("Vazgeç") } },
    )
}
