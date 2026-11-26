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

@Composable
fun RaporEkrani(vm: SatisViewModel) {
    val donem by vm.raporDonemi.collectAsState()
    val rapor by vm.rapor.collectAsState()
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
                Satir("Toplam indirim", "-" + para(r.iskonto), Color(0xFFC62828))
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
        Text("Satılan ürünler", fontWeight = FontWeight.Bold)
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
                        color = if ((u.stok ?: 1) <= 0) Color(0xFFC62828) else Color.Unspecified)
                    Text(para(u.net))
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Satir(etiket: String, deger: String, renk: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiket, color = renk); Text(deger, color = renk)
    }
}
