package com.barkodsatis.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.barkodsatis.fis.para
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import com.barkodsatis.data.OdemeTipi
import com.barkodsatis.data.kurus
import com.barkodsatis.fis.FisPdf
import com.barkodsatis.fis.TR
import java.text.SimpleDateFormat
import java.util.Date

private val Kirmizi = Color(0xFFC62828)

@Composable
fun RaporEkrani(vm: SatisViewModel) {
    val donem by vm.raporDonemi.collectAsState()
    val rapor by vm.rapor.collectAsState()
    val fisler by vm.fisler.collectAsState()
    val acikFis by vm.acikFis.collectAsState()
    var liste by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val saat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", TR) }
    val kaydet = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null) vm.raporuExceleAktar(uri) }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RaporDonemi.entries.forEach { d ->
                FilterChip(selected = d == donem, onClick = { vm.raporDonemi.value = d }, label = { Text(d.etiket) })
            }
        }
        val r = rapor
        if (r == null || r.donem != donem) { Text("Yükleniyor…", color = Color.Gray); return@Column }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Satir("Satış sayısı", r.satisSayisi.toString())
                Satir("Satılan ürün adedi", r.urunAdedi.toString())
                Satir("Brüt toplam", para(r.brut))
                Satir("Toplam indirim", "-" + para(r.iskonto), Kirmizi)
                Satir("Nakit", para(r.nakit))
                Satir("Kredi kartı", para(r.kart))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("NET TOPLAM", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(para(r.net), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
        Button(onClick = { kaydet.launch(vm.raporDosyaAdi()) }, enabled = r.satirlar.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileDownload, null); Text("  Excel'e aktar")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = liste == 0, onClick = { liste = 0 }, label = { Text("Satılan ürünler") })
            FilterChip(selected = liste == 1, onClick = { liste = 1 }, label = { Text("Fişler (${fisler.size})") })
        }
        if (liste == 0) {
            Row(Modifier.fillMaxWidth()) {
                Text("Ürün", Modifier.weight(1f), fontSize = 12.sp, color = Color.Gray)
                Text("Satılan", Modifier.padding(horizontal = 8.dp), fontSize = 12.sp, color = Color.Gray)
                Text("Kalan stok", Modifier.padding(horizontal = 8.dp), fontSize = 12.sp, color = Color.Gray)
                Text("Net", fontSize = 12.sp, color = Color.Gray)
            }
            LazyColumn(Modifier.weight(1f)) {
                if (r.urunler.isEmpty()) item { Text("Bu dönemde satış yok.", Modifier.padding(16.dp), color = Color.Gray) }
                items(r.urunler, key = { it.barkod }) { u ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.ad, fontWeight = FontWeight.SemiBold)
                            Text(u.barkod, fontSize = 11.sp, color = Color.Gray)
                        }
                        Text("${u.adet}", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
                        Text(u.stok?.toString() ?: "-", Modifier.padding(horizontal = 16.dp),
                            color = if ((u.stok ?: 1) <= 0) Kirmizi else Color.Unspecified)
                        Text(para(u.net))
                    }
                    HorizontalDivider()
                }
            }
        } else {
            Text("Fişe dokunarak içeriğini görebilir, tekrar gönderebilir veya iptal edebilirsiniz.", fontSize = 12.sp, color = Color.Gray)
            LazyColumn(Modifier.weight(1f)) {
                if (fisler.isEmpty()) item { Text("Bu dönemde fiş yok.", Modifier.padding(16.dp), color = Color.Gray) }
                items(fisler, key = { it.id }) { f ->
                    Row(Modifier.fillMaxWidth().clickable { vm.fisAc(f) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fiş No ${f.id}", fontWeight = FontWeight.SemiBold,
                                textDecoration = if (f.iptal) TextDecoration.LineThrough else null)
                            Text(saat.format(Date(f.zaman)) + "  ·  " + if (f.odemeTipi == OdemeTipi.NAKIT) "Nakit" else "Kredi kartı",
                                fontSize = 12.sp, color = Color.Gray)
                        }
                        if (f.iptal) Text("İPTAL  ", color = Kirmizi, fontWeight = FontWeight.Bold)
                        Text(para(f.netToplam), fontWeight = FontWeight.Bold,
                            color = if (f.iptal) Color.Gray else Color.Unspecified,
                            textDecoration = if (f.iptal) TextDecoration.LineThrough else null)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    acikFis?.let { t ->
        var iptalOnay by remember(t.satis.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = vm::fisKapat,
            title = { Text("Fiş No ${t.satis.id}" + if (t.satis.iptal) " (İPTAL)" else "") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(saat.format(Date(t.satis.zaman)) + "  ·  " + if (t.satis.odemeTipi == OdemeTipi.NAKIT) "Nakit" else "Kredi kartı")
                    Spacer(Modifier.height(6.dp))
                    t.satirlar.forEach { l ->
                        val brut = (l.birimFiyat * l.adet).kurus()
                        val net = (brut - (brut * l.iskontoYuzde / 100).kurus()).kurus()
                        Row(Modifier.fillMaxWidth()) {
                            Text("${l.adet} x ${l.ad}" + if (l.iskontoYuzde > 0) " (İsk. %${FisPdf.yuzde(l.iskontoYuzde)})" else "",
                                Modifier.weight(1f), fontSize = 13.sp)
                            Text(para(net), fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    if (t.satis.iskontoToplam > 0) Satir("İndirim", "-" + para(t.satis.iskontoToplam), Kirmizi)
                    Satir("TOPLAM", para(t.satis.netToplam))
                    if (!t.satis.iptal) {
                        Spacer(Modifier.height(10.dp))
                        if (!iptalOnay) {
                            OutlinedButton(onClick = { iptalOnay = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Fişi iptal et", color = Kirmizi)
                            }
                        } else {
                            Text("Bu fiş iptal edilecek, ürünler stoğa geri eklenecek ve rapordan düşülecek. Emin misiniz?", color = Kirmizi)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.fisIptal(t.satis.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Kirmizi)) { Text("Evet, iptal et") }
                                TextButton(onClick = { iptalOnay = false }) { Text("Vazgeç") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!t.satis.iptal) TextButton(onClick = { vm.fisGonder(context, t) }) { Text("Fişi gönder") }
            },
            dismissButton = { TextButton(onClick = vm::fisKapat) { Text("Kapat") } },
        )
    }
}

@Composable
private fun Satir(etiket: String, deger: String, renk: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiket, color = renk); Text(deger, color = renk)
    }
}
