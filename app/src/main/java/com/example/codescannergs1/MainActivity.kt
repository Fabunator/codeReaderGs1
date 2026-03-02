package com.example.codescannergs1

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

data class ScannedCode(
    val rawValue: String,
    val type: String,
    val isGs1: Boolean
)

@ExperimentalGetImage
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val navController = rememberNavController()
            var hasCameraPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }
            val requestPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted ->
                    hasCameraPermission = isGranted
                }
            )

            LaunchedEffect(key1 = true) {
                if (!hasCameraPermission) {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            NavHost(navController = navController, startDestination = "camera") {
                composable("camera") {
                    if (hasCameraPermission) {
                        CameraScreen(navController = navController)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Kameraberechtigung wird benötigt...")
                        }
                    }
                }
                composable("result/{codesJson}") { backStackEntry ->
                    val codesJsonEncoded = backStackEntry.arguments?.getString("codesJson") ?: ""
                    val codesJson = URLDecoder.decode(codesJsonEncoded, StandardCharsets.UTF_8.toString())
                    
                    val listType = object : TypeToken<List<ScannedCode>>() {}.type
                    val scannedCodes: List<ScannedCode> = Gson().fromJson(codesJson, listType)
                    
                    BarcodeResultScreen(
                        navController = navController,
                        scannedCodes = scannedCodes
                    )
                }
            }
        }
    }
}

@Composable
@ExperimentalGetImage
fun CameraScreen(navController: NavController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var zoomValue by remember { mutableFloatStateOf(0f) }

    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }

    fun navigateToResults(barcodes: List<Barcode>) {
        val scannedCodes = barcodes.map { barcode ->
            val rawBytes = barcode.rawBytes
            val finalValue = if (rawBytes != null) {
                getCodeString(rawBytes, barcode)
            } else {
                barcode.rawValue ?: ""
            }
            ScannedCode(
                rawValue = finalValue,
                type = getBarcodeType(barcode.format, finalValue),
                isGs1 = isGS1Code(finalValue)
            )
        }
        
        val codesJson = Gson().toJson(scannedCodes)
        val encodedJson = URLEncoder.encode(codesJson, StandardCharsets.UTF_8.toString())
        
        navController.navigate("result/$encodedJson")
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val image = InputImage.fromFilePath(context, uri)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            if (barcodes.isNotEmpty()) {
                                navigateToResults(barcodes)
                            } else {
                                Toast.makeText(context, "Keine Barcodes gefunden", Toast.LENGTH_SHORT).show()
                            }
                        }
                } catch (e: IOException) {
                    Log.e("CameraScreen", "Fehler beim Laden", e)
                }
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val preview = Preview.Builder().build()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                
                preview.surfaceProvider = previewView.surfaceProvider
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val image = imageProxy.image
                    if (image != null) {
                        val processImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(processImage)
                            .addOnSuccessListener { barcodes ->
                                if (barcodes.isNotEmpty()) {
                                    imageAnalysis.clearAnalyzer()
                                    navigateToResults(barcodes)
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }
                }

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Kamera-Steuerungen
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        isFlashOn = !isFlashOn
                        camera?.cameraControl?.enableTorch(isFlashOn)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "Flash Toggle",
                        tint = if (isFlashOn) Color.Yellow else Color.White
                    )
                }
                
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Zoom", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = zoomValue,
                        onValueChange = {
                            zoomValue = it
                            camera?.cameraControl?.setLinearZoom(it)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Barcode scannen oder Bild wählen",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Galerie öffnen")
            }
        }
    }
}

@Composable
fun BarcodeResultScreen(
    navController: NavController,
    scannedCodes: List<ScannedCode>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Scan Ergebnisse (${scannedCodes.size})",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(scannedCodes) { code ->
                CodeResultItem(code)
            }
        }
        
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Neu Scannen")
        }
    }
}

@Composable
fun CodeResultItem(code: ScannedCode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Typ: ${code.type}", fontWeight = FontWeight.Bold, color = if (code.isGs1) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary)
            Text(text = "Roh-Daten: \n ${code.rawValue}", style = MaterialTheme.typography.bodySmall)
            
            if (code.isGs1) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                val parserInput = code.rawValue.replace("<GS>", "\u001d")
                val parsedData = GS1Parser.parse(parserInput)
                
                parsedData.forEach { (ai, value) ->
                    val (plausibility, message) = GS1Parser.checkPlausibility(ai, value)
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(text = "AI ($ai) ${GS1Parser.aiDefinitions[ai]?.name ?: "Unbekannt"}: \n$value", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        if (!plausibility) {
                            Text(text = "⚠ $message", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

fun getCodeString(rawBytes: ByteArray, barcode: Barcode): String {
    val rawString = String(rawBytes, StandardCharsets.UTF_8)
    
    // Steuerzeichen für die Anzeige sichtbar machen
    val displayValue = rawString.replace("\u001d", "<GS>")

    return when (barcode.format) {
        Barcode.FORMAT_DATA_MATRIX -> {
            if (rawBytes.isNotEmpty() && rawBytes[0].toInt() == 29) {
                if (displayValue.startsWith("<GS>")) "]d2" + displayValue.substring(4) else "]d2$displayValue"
            } else {
                if (displayValue.startsWith("<GS>")) "]d1" + displayValue.substring(4) else "]d1$displayValue"
            }
        }
        Barcode.FORMAT_CODE_128 -> displayValue

        else -> displayValue
    }
}

fun isGS1Code(value: String): Boolean {
    return value.startsWith("]d2") || value.startsWith("]C1") || value.contains("<GS>")
}

fun getBarcodeType(format: Int, value: String): String {
    return when (format) {
        Barcode.FORMAT_CODE_128 -> if (value.startsWith("]C1")) "GS1-128" else "Code 128"
        Barcode.FORMAT_DATA_MATRIX -> if (value.startsWith("]d2")) "GS1 DataMatrix" else "DataMatrix"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codebar"
        else -> "Format: $format"
    }
}
