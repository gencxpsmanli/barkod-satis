package com.barkodsatis.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barkodsatis.data.Urun
import com.barkodsatis.fis.FisPdf
import com.barkodsatis.fis.para

@Composable
fun UrunlerEkrani(vm: SatisViewModel, satisaGit: () -> Unit) {
    val urunler by vm.urunler.collectAsState()
    val arama by vm.aramaMetni.collectAsState()
    var duzenlenen by remember { mutableStateOf<Urun?>(null) }
    var yeni by remember { mutableStateOf(false) }
    var secili by remember { mutableStateOf(setOf<String>()) }
    val sadeceKritik by vm.sadeceKritik.collectAsState()
    val kritikSayisi by vm.kritikSayisi.collectAsState()
    val kritikSinir by vm.kritikSinir.collectAsState()

    val dosyaSec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.excelAktar(uri)
    }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Excel dosyasının ilk satırında şu başlıklar olmalı: Barkod, Ürün Adı, Satış İskontosu, Fiyatı (isteğe bağlı: Stok). " +
            "ETA'dan Excel'e aktardığınız listeyi de aynı şekilde yükleyebilirsiniz. Aynı barkod tekrar gelirse güncellenir.",
            fontSize = 13.sp, color = Color.DarkGray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                dosyaSec.launch(arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel", "text/csv", "text/comma-separated-values", "*/*",
                ))
            }, modifier = Modifier.weight(1f)) { Text("Excel'den ürün aktar") }
            OutlinedButton(onClick = { yeni = true }) { Text("Yeni ürün") }
        }
        if (kritikSayisi > 0 || sadeceKritik) {
            androidx.compose.material3.FilterChip(
                selected = sadeceKritik, onClick = { vm.sadeceKritik.value = !sadeceKritik },
                label = { Text("Kritik stok: $kritikSayisi ürün (≤ $kritikSinir)", color = Color(0xFFC62828)) },
            )
        }
        OutlinedTextField(arama, { vm.aramaMetni.value = it }, label = { Text("Ürün ara (ad veya barkod)") }, enabled = !sadeceKritik,
            singleLine = true, modifier = Modifier.fillMaxWidth())

        LazyColumn(Modifier.weight(1f)) {
            if (urunler.isEmpty()) item { Text("Ürün yok. Excel'den aktarın veya yeni ürün ekleyin.", Modifier.padding(16.dp), color = Color.Gray) }
            items(urunler, key = { it.barkod }) { u ->
                Row(Modifier.fillMaxWidth().clickable { duzenlenen = u }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = u.barkod in secili, onCheckedChange = { c -> secili = if (c) secili + u.barkod else secili - u.barkod })
                    Column(Modifier.weight(1f)) {
                        Text(u.ad, fontWeight = FontWeight.SemiBold)
                        Text("${u.barkod}   Stok: ${u.stok}", fontSize = 12.sp, color = if (u.stok <= kritikSinir) Color(0xFFC62828) else Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(para(u.fiyat), fontWeight = FontWeight.Bold)
                        if (u.iskontoYuzde > 0) Text("İsk. %${FisPdf.yuzde(u.iskontoYuzde)}", fontSize = 12.sp, color = Color(0xFFC62828))
                    }
                }
                HorizontalDivider()
            }
        }

        if (secili.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    // Arama sonucunda görünmeyen seçili ürünler de eklensin diye seçim barkodlarla tutulur
                    vm.sepeteEkleBarkodlar(secili.toList())
                    secili = emptySet()
                    satisaGit()
                }, modifier = Modifier.weight(1f)) { Text("Seçilenleri satışa aktar (${secili.size})") }
                TextButton(onClick = { secili = emptySet() }) { Text("Seçimi temizle") }
            }
        }
    }

    duzenlenen?.let { u ->
        UrunFormu("Ürünü düzenle", u, barkodDegistirilebilir = true,
            kaydet = { yeniU -> vm.urunGuncelle(u.barkod, yeniU) { ok -> if (ok) { duzenlenen = null; if (u.barkod in secili) secili = secili - u.barkod } } },
            iptal = { duzenlenen = null },
            sil = { vm.urunSil(u.barkod); secili = secili - u.barkod; duzenlenen = null })
    }
    if (yeni) {
        UrunFormu("Yeni ürün", Urun("", "", 0.0, 0.0), barkodDegistirilebilir = true,
            kaydet = { vm.urunKaydet(it); yeni = false }, iptal = { yeni = false })
    }
}
