package com.barkodsatis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.barkodsatis.ui.SatisEkrani
import com.barkodsatis.ui.SatisViewModel
import com.barkodsatis.ui.UrunlerEkrani

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val vm: SatisViewModel = viewModel()
                var sekme by rememberSaveable { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                val mesaj by vm.mesaj.collectAsState()
                LaunchedEffect(mesaj) { mesaj?.let { snackbar.showSnackbar(it); vm.mesajGosterildi() } }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(selected = sekme == 0, onClick = { sekme = 0 },
                                icon = { Icon(Icons.Default.PointOfSale, null) }, label = { Text("Satış") })
                            NavigationBarItem(selected = sekme == 1, onClick = { sekme = 1 },
                                icon = { Icon(Icons.Default.Inventory2, null) }, label = { Text("Ürünler") })
                        }
                    },
                ) { ic ->
                    Surface(Modifier.padding(ic)) {
                        if (sekme == 0) SatisEkrani(vm) else UrunlerEkrani(vm)
                    }
                }
            }
        }
    }
}
