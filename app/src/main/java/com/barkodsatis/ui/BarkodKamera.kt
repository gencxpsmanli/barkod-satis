package com.barkodsatis.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Kamera önizlemesi ve ML Kit ile barkod okuma. Aynı barkod 1,5 sn içinde
 * tekrar okunursa yok sayılır (tek okutmada çift ekleme olmasın diye).
 */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun BarkodKamera(modifier: Modifier = Modifier, okundu: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sonOkundu = rememberUpdatedState(okundu)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val analizci = remember { Executors.newSingleThreadExecutor() }
    val tarayici = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE,
            ).build()
        )
    }

    DisposableEffect(lifecycleOwner) {
        val saglayiciFuture = ProcessCameraProvider.getInstance(context)
        var sonDeger = ""; var sonZaman = 0L
        saglayiciFuture.addListener({
            val saglayici = saglayiciFuture.get()
            val onizleme = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analiz = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analiz.setAnalyzer(analizci) { proxy ->
                val medya = proxy.image
                if (medya == null) { proxy.close(); return@setAnalyzer }
                tarayici.process(InputImage.fromMediaImage(medya, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { barkodlar ->
                        val deger = barkodlar.firstOrNull()?.rawValue
                        val simdi = System.currentTimeMillis()
                        if (deger != null && !(deger == sonDeger && simdi - sonZaman < 1500)) {
                            sonDeger = deger; sonZaman = simdi
                            sonOkundu.value(deger)
                        } else if (deger != null) sonZaman = simdi
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            saglayici.unbindAll()
            saglayici.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, onizleme, analiz)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { saglayiciFuture.get().unbindAll() }
        }
    }
    DisposableEffect(Unit) { onDispose { tarayici.close(); analizci.shutdown() } }

    AndroidView(factory = { previewView }, modifier = modifier)
}
