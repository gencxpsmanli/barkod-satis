package com.barkodsatis.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AyarlarEkrani(vm: SatisViewModel) {
    val sesAcik by vm.sesAcik.collectAsState()
    val kritikSinir by vm.kritikSinir.collectAsState()
    var isletme by remember { mutableStateOf(vm.isletmeAdi) }
    var kritik by remember { mutableStateOf(kritikSinir.toString()) }
    var sifre1 by remember { mutableStateOf("") }
    var sifre2 by remember { mutableStateOf("") }
    var geriYukleOnay by remember { mutableStateOf(false) }

    val yedekKaydet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.yedekAl(uri)
    }
    val yedekSec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.yedekYukle(uri)
    }

    Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Bolum("Yedekleme") {
            Text("Tüm ürünler, satışlar ve fişler tek bir dosyaya kaydedilir. Dosyayı Google Drive'a veya bilgisayarınıza kopyalayın. " +
                "Telefon değiştiğinde veya bozulduğunda bu dosyayı geri yükleyerek her şeyi geri alırsınız.", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { yedekKaydet.launch(vm.yedekDosyaAdi()) }, modifier = Modifier.weight(1f)) { Text("Yedek al") }
                OutlinedButton(onClick = { geriYukleOnay = true }, modifier = Modifier.weight(1f)) { Text("Yedekten geri yükle") }
            }
        }

        Bolum("Fiş") {
            OutlinedTextField(isletme, { isletme = it; vm.isletmeAdi = it }, label = { Text("İşletme adı (fişte görünür)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        Bolum("Okutma") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Okutunca bip sesi ve titreşim", Modifier.weight(1f))
                Switch(checked = sesAcik, onCheckedChange = vm::sesAyarla)
            }
            Text("Terazi barkodu: 27, 28 veya 29 ile başlayan 13 haneli etiketler otomatik okunur. " +
                "Ürünü barkodun ilk 7 hanesiyle (örn. 2800123) ve kilogram fiyatıyla kaydedin; ağırlık etiketten alınır.",
                fontSize = 12.sp, color = Color.Gray)
        }

        Bolum("Stok") {
            OutlinedTextField(kritik, { v -> kritik = v.filter { it.isDigit() }.take(5); kritik.toIntOrNull()?.let(vm::kritikSinirAyarla) },
                label = { Text("Kritik stok sınırı") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Text("Stoğu bu sayıya eşit veya altında olan ürünler Ürünler sekmesinde kırmızı görünür ve ayrıca listelenebilir.",
                fontSize = 12.sp, color = Color.Gray)
        }

        Bolum("Şifre") {
            Text(if (vm.sifreVar) "Rapor ve Ayarlar sekmeleri şifreli." else "Şifre yok. Şifre koyarsanız Rapor ve Ayarlar sekmeleri şifreyle açılır.",
                fontSize = 13.sp)
            OutlinedTextField(sifre1, { sifre1 = it.filter { c -> c.isDigit() }.take(8) }, label = { Text("Yeni şifre (rakam)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sifre2, { sifre2 = it.filter { c -> c.isDigit() }.take(8) }, label = { Text("Yeni şifre tekrar") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.sifreAyarla(sifre1); sifre1 = ""; sifre2 = "" },
                    enabled = sifre1.length >= 4 && sifre1 == sifre2) { Text("Şifreyi kaydet") }
                if (vm.sifreVar) OutlinedButton(onClick = { vm.sifreAyarla("") }) { Text("Şifreyi kaldır") }
            }
            if (sifre1.isNotEmpty() && sifre1.length < 4) Text("Şifre en az 4 haneli olmalı.", fontSize = 12.sp, color = Color(0xFFC62828))
        }
    }

    if (geriYukleOnay) {
        AlertDialog(
            onDismissRequest = { geriYukleOnay = false },
            title = { Text("Yedekten geri yüklensin mi?") },
            text = { Text("Telefondaki mevcut tüm ürün ve satış kayıtları, seçeceğiniz yedek dosyasındakilerle değiştirilecek. " +
                "Gerekirse önce şimdiki durumun yedeğini alın. Geri yüklemeden sonra uygulama yeniden başlar.") },
            confirmButton = { TextButton(onClick = { geriYukleOnay = false; yedekSec.launch(arrayOf("*/*")) }) { Text("Yedek dosyasını seç") } },
            dismissButton = { TextButton(onClick = { geriYukleOnay = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun Bolum(baslik: String, icerik: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(baslik, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            icerik()
        }
    }
}

/** Şifre sorulan ekran: Rapor ve Ayarlar sekmeleri kilitliyken gösterilir. */
@Composable
fun KilitEkrani(vm: SatisViewModel) {
    var girilen by remember { mutableStateOf("") }
    var hata by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Bu bölüm şifreli", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedTextField(girilen, { girilen = it.filter { c -> c.isDigit() }.take(8); hata = false }, label = { Text("Şifre") },
            singleLine = true, isError = hata, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        if (hata) Text("Şifre yanlış.", color = Color(0xFFC62828))
        Button(onClick = { hata = !vm.kilidiAc(girilen); girilen = "" }) { Text("Aç") }
    }
}
