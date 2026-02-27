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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

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
                composable("result/{rawValue}/{type}/{isGs1}") { backStackEntry ->
                    val rawValueEncoded = backStackEntry.arguments?.getString("rawValue") ?: ""
                    val codeType = backStackEntry.arguments?.getString("type") ?: "Unbekannt"
                    val isGs1Code = backStackEntry.arguments?.getString("isGs1")?.toBoolean() ?: false
                    
                    val rawValue = URLDecoder.decode(rawValueEncoded, StandardCharsets.UTF_8.toString())
                    // Für den Parser wandeln wir <GS> zurück in das Steuerzeichen
                    val parserInput = rawValue.replace("<GS>", "\u001d")
                    
                    val parsedData = if (isGs1Code) GS1Parser.parse(parserInput) else emptyMap()

                    BarcodeResultScreen(
                        navController = navController,
                        scannedData = parsedData,
                        displayValue = rawValue,
                        codeType = codeType,
                        isGs1 = isGs1Code
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
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

    fun navigateToResult(barcode: Barcode) {
        val rawBytes = barcode.rawBytes
        val finalValue = if (rawBytes != null) {
            getCodeString(rawBytes, barcode)
        } else {
            barcode.rawValue ?: ""
        }
        
        val barcodeType = getBarcodeType(barcode.format, finalValue)
        val isGs1 = isGS1Code(barcode.format, finalValue)
        val encodedValue = URLEncoder.encode(finalValue, StandardCharsets.UTF_8.toString())
        
        navController.navigate("result/$encodedValue/$barcodeType/$isGs1")
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
                                navigateToResult(barcodes.first())
                            } else {
                                Toast.makeText(context, "Kein Barcode im Bild gefunden", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            Log.e("CameraScreen", "Fehler beim Scan aus Galerie", it)
                        }
                } catch (e: IOException) {
                    Log.e("CameraScreen", "Fehler beim Laden des Bildes", e)
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
                                    navigateToResult(barcodes.first())
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
    scannedData: Map<String, String>,
    displayValue: String,
    codeType: String,
    isGs1: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Scan Ergebnis",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Code Typ:", fontWeight = FontWeight.Bold)
        Text(text = codeType, color = if (isGs1) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(text = "Roh-Daten:", fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.LightGray.copy(alpha = 0.2f))
                .padding(8.dp)
        ) {
            Text(text = displayValue, style = MaterialTheme.typography.bodySmall)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (isGs1) {
            Text(text = "GS1 Aufschlüsselung:", fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            scannedData.forEach { (ai, value) ->
                val (plausibility, message) = GS1Parser.checkPlausibility(ai, value)
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(text = "AI ($ai): ${GS1Parser.aiDefinitions[ai]?.name ?: "Unbekannt"}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(text = value, style = MaterialTheme.typography.bodyLarge)
                    if (!plausibility) {
                        Text(text = "⚠ $message", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text(text = "✓ OK", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        } else {
            Text(text = "Kein GS1-Standard erkannt.", color = Color.Gray)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Neu Scannen")
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

fun isGS1Code(format: Int, value: String): Boolean {
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
