package com.example.codescannergs1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Camera permission granted.")
        } else {
            Log.d("MainActivity", "Camera permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            CameraScreen()
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var scannedData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var barcodeType by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) { // Use Box for layering
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val preview = Preview.Builder().build()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()

                val scanner = BarcodeScanning.getClient(options)

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    val image = imageProxy.image
                    if (image != null) {
                        val processImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(processImage)
                            .addOnSuccessListener { barcodes ->
                                if (barcodes.isNotEmpty()) {
                                    val barcode = barcodes.first()
                                    barcodeType = getBarcodeType(barcode.format)
                                    val rawValue = barcode.rawValue ?: ""
                                    scannedData = if (isGS1Code(barcode.format, rawValue)) {
                                        GS1Parser.parse(rawValue)
                                    } else {
                                        mapOf("Raw Value" to rawValue)
                                    }
                                }
                            }
                            .addOnFailureListener {
                                Log.e("CameraScreen", "Error scanning barcodes", it)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }
                }

                try {
                    cameraProviderFuture.get().bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                previewView
            },
            modifier = Modifier.fillMaxSize() // Camera fills the whole screen
        )

        // Display scanned data and test button at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter) // Align to the bottom of the Box
                .fillMaxWidth() // Take full width
                .background(Color.Black.copy(alpha = 0.5f)) // Semi-transparent background
                .padding(16.dp)
        ) {
            if (barcodeType != null) {
                Text(
                    text = "Barcode Type: $barcodeType",
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = Color.White // White text for better contrast
                )
            }
            if (scannedData.isEmpty()) {
                Text(
                    text = "Scan something or use test value!",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                scannedData.forEach { (ai, value) ->
                    Text(
                        text = "$ai: $value",
                        color = Color.White
                    )
                }
            }
            Button(
                onClick = {
                    val testData = "]C1010123456789012317251231"
                    barcodeType = "GS1-128 (Test)"
                    scannedData = GS1Parser.parse(testData)
                },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text("Use Test Value")
            }
        }
    }
}

fun isGS1Code(format: Int, rawValue: String): Boolean {
    return when (format) {
        Barcode.FORMAT_CODE_128 -> rawValue.startsWith("]C1")
        Barcode.FORMAT_DATA_MATRIX -> rawValue.startsWith("]d2")
        Barcode.FORMAT_QR_CODE -> rawValue.contains(GS1Parser.FNC1)
        Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_ITF -> true
        else -> false
    }
}

fun getBarcodeType(format: Int): String {
    return when (format) {
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        else -> "Unknown"
    }
}
