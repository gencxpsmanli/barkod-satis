package com.barkodsatis.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.barkodsatis.data.OdemeTipi
import com.barkodsatis.data.SepetSatiri
import com.barkodsatis.data.Urun
import com.barkodsatis.fis.FisPdf
import com.barkodsatis.fis.para

private val Yesil = Color(0xFF2E7D32)
private val Mavi = Color(0xFF1565C0)
private val Kirmizi = Color(0xFFC62828)

@Composable
fun SatisEkrani(vm: SatisViewModel) {
    val context = LocalContext.current
    val sepet by vm.sepet.collectAsState()
    val bekleyenAdet by vm.bekleyenAdet.collectAsState()
    val bilinmeyen by vm.bilinmeyenBarkod.collectAsState()
    val sonSatis by vm.sonSatis.collectAsState()

    var barkodMetni by remember { mutableStateOf("") }
    var kameraAcik by rememberSaveable { mutableStateOf(false) }
    val odak = remember { FocusRequester() }
    val izinIste = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { kameraAcik = it }
    val listeDurumu = rememberLazyListState()

    fun gonder() { vm.barkodOkundu(barkodMetni); barkodMetni = "" }

    // Harici/Bluetooth okuyucu klavye gibi yazar: barkod alanı hep odakta kalsın.
    LaunchedEffect(bilinmeyen, sonSatis) { if (bilinmeyen == null && sonSatis == null) runCatching { odak.requestFocus() } }
    LaunchedEffect(sepet.satirlar.size) { if (sepet.satirlar.isNotEmpty()) listeDurumu.animateScrollToItem(sepet.satirlar.lastIndex) }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        // Barkod girişi
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = barkodMetni,
                onValueChange = { v ->
                    // Bazı okuyucular sonuna satır sonu ekler
                    if (v.endsWith("\n") || v.endsWith("\r")) { barkodMetni = v.trim(); gonder() } else barkodMetni = v
                },
                label = { Text("Barkod okutun veya yazın") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { gonder() }),
                modifier = Modifier.weight(1f).focusRequester(odak).onPreviewKeyEvent {
                    if ((it.key == Key.Enter || it.key == Key.NumPadEnter) && it.type == KeyEventType.KeyUp) { gonder(); true }
                    else it.key == Key.Enter || it.key == Key.NumPadEnter
                },
            )
            IconToggleButton(checked = kameraAcik, onCheckedChange = { ac ->
                if (!ac) kameraAcik = false
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) kameraAcik = true
                else izinIste.launch(Manifest.permission.CAMERA)
            }) { Icon(Icons.Default.PhotoCamera, "Kamera", tint = if (kameraAcik) Mavi else Color.Gray) }
            IconButton(onClick = vm::yeniUrunFormu) { Icon(Icons.Default.Add, "Elle ürün ekle") }
        }

        if (kameraAcik) {
            BarkodKamera(Modifier.fillMaxWidth().height(160.dp).padding(top = 4.dp)) { vm.barkodOkundu(it) }
        }

        // 1-9 adet tuşları
        AdetTuslari(bekleyenAdet, vm::rakam, vm::adetTemizle)

        // Sepet
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listeDurumu) {
            if (sepet.satirlar.isEmpty()) item {
                Text("Sepet boş. Ürün okutun.", Modifier.padding(24.dp), color = Color.Gray)
            }
            items(sepet.satirlar, key = { it.urun.barkod }) { satir ->
                SepetSatirGorunumu(satir,
                    arttir = { vm.adetAyarla(satir.urun.barkod, satir.adet + 1) },
                    azalt = { vm.adetAyarla(satir.urun.barkod, satir.adet - 1) },
                    sil = { vm.sil(satir.urun.barkod) },
                    bekleyenAdetUygula = if (bekleyenAdet.isNotEmpty()) ({
                        vm.adetAyarla(satir.urun.barkod, bekleyenAdet.toInt()); vm.adetTemizle()
                    }) else null,
                )
                HorizontalDivider()
            }
        }

        // Toplamlar
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(10.dp)) {
                ToplamSatiri("Ürün adedi", sepet.urunAdedi.toString())
                ToplamSatiri("Ara toplam", para(sepet.brutToplam))
                ToplamSatiri("Toplam indirim", if (sepet.iskontoToplam > 0) "-" + para(sepet.iskontoToplam) else para(0.0), Kirmizi)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOPLAM", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(para(sepet.netToplam), fontWeight = FontWeight.Bold, fontSize = 26.sp)
                }
            }
        }

        // Ödeme
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.odemeAl(OdemeTipi.NAKIT) }, enabled = sepet.satirlar.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Yesil),
                modifier = Modifier.weight(1f).height(64.dp)) { Text("NAKİT", fontSize = 20.sp) }
            Button(onClick = { vm.odemeAl(OdemeTipi.KREDI_KARTI) }, enabled = sepet.satirlar.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Mavi),
                modifier = Modifier.weight(1f).height(64.dp)) { Text("KREDİ KARTI", fontSize = 20.sp) }
        }
        if (sepet.satirlar.isNotEmpty()) {
            TextButton(onClick = vm::sepetiTemizle, modifier = Modifier.align(Alignment.End)) { Text("Sepeti temizle", color = Kirmizi) }
        }
    }

    bilinmeyen?.let { barkod ->
        UrunFormu(
            baslik = if (barkod.isEmpty()) "Elle ürün ekle" else "Ürün bulunamadı, elle ekleyin",
            baslangic = Urun(barkod, "", 0.0, 0.0),
            barkodDegistirilebilir = barkod.isEmpty(),
            kaydet = vm::elleEkle,
            iptal = vm::elleEklemeIptal,
        )
    }

    sonSatis?.let { t ->
        var eposta by remember(t.satis.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = vm::sonSatisKapat,
            title = { Text("Satış tamamlandı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${if (t.satis.odemeTipi == OdemeTipi.NAKIT) "Nakit" else "Kredi kartı"}: ${para(t.satis.netToplam)}",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (t.satis.iskontoToplam > 0) Text("Uygulanan indirim: ${para(t.satis.iskontoToplam)}")
                    Text("Müşteri fiş isterse e-posta adresini yazıp gönderin (mali değeri yoktur).")
                    OutlinedTextField(eposta, { eposta = it.trim() }, label = { Text("E-posta (isteğe bağlı)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
                }
            },
            confirmButton = { TextButton(onClick = { vm.fisiGonder(context, eposta) }) { Text("Fişi e-postayla gönder") } },
            dismissButton = { TextButton(onClick = vm::sonSatisKapat) { Text("Yeni satış") } },
        )
    }
}

@Composable
private fun AdetTuslari(bekleyen: String, rakam: (Int) -> Unit, temizle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (bekleyen.isEmpty()) "Adet: 1" else "Adet: $bekleyen  (sonraki ürüne uygulanır veya satıra dokunun)",
                fontWeight = if (bekleyen.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                color = if (bekleyen.isEmpty()) Color.Gray else Mavi,
                modifier = Modifier.weight(1f), fontSize = 13.sp,
            )
            if (bekleyen.isNotEmpty()) TextButton(onClick = temizle) { Text("Sil") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 1..9) {
                FilledTonalButton(onClick = { rakam(r) }, modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("$r", fontSize = 20.sp)
                }
            }
            OutlinedButton(onClick = { rakam(0) }, modifier = Modifier.weight(1f).height(48.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("0", fontSize = 20.sp) }
        }
    }
}

@Composable
private fun SepetSatirGorunumu(
    satir: SepetSatiri, arttir: () -> Unit, azalt: () -> Unit, sil: () -> Unit, bekleyenAdetUygula: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (bekleyenAdetUygula != null) Modifier.clickable(onClick = bekleyenAdetUygula).background(Mavi.copy(alpha = 0.06f)) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(satir.urun.ad, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${satir.adet} x ${para(satir.urun.fiyat)}", fontSize = 13.sp, color = Color.DarkGray)
            if (satir.urun.iskontoYuzde > 0) {
                Text("İskonto %${FisPdf.yuzde(satir.urun.iskontoYuzde)}  →  -${para(satir.iskontoTutari)}",
                    fontSize = 13.sp, color = Kirmizi)
            }
        }
        IconButton(onClick = azalt) { Icon(Icons.Default.Remove, "Azalt") }
        Text("${satir.adet}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = arttir) { Icon(Icons.Default.Add, "Artır") }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(92.dp)) {
            if (satir.urun.iskontoYuzde > 0) Text(para(satir.brut), fontSize = 12.sp, color = Color.Gray,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
            Text(para(satir.net), fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = sil) { Icon(Icons.Default.Delete, "Sil", tint = Kirmizi) }
    }
}

@Composable
private fun ToplamSatiri(etiket: String, deger: String, renk: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiket, color = renk); Text(deger, color = renk)
    }
}
