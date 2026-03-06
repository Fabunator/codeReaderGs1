package com.example.codescannergs1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.FocusMeteringAction
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.codescannergs1.ui.theme.CodeScannerGS1Theme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors

data class ScannedCode(
    val rawValue: String,
    val type: String,
    val isGs1: Boolean
)

enum class ResultFilter { ALL, GS1_ONLY, NON_GS1 }

@ExperimentalGetImage
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val preferences = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        val darkModeKey = "dark_mode_enabled"

        setContent {
            val navController = rememberNavController()
            var isDarkThemeEnabled by remember {
                mutableStateOf(preferences.getBoolean(darkModeKey, false))
            }
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

            DisposableEffect(isDarkThemeEnabled) {
                window.decorView.setBackgroundColor(
                    if (isDarkThemeEnabled) AndroidColor.BLACK else AndroidColor.WHITE
                )
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDarkThemeEnabled
                    isAppearanceLightNavigationBars = !isDarkThemeEnabled
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
                onDispose { }
            }

            CodeScannerGS1Theme(darkTheme = isDarkThemeEnabled) {
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
                        scannedCodes = scannedCodes,
                        isDarkThemeEnabled = isDarkThemeEnabled,
                        onDarkThemeToggle = { isEnabled ->
                            isDarkThemeEnabled = isEnabled
                            preferences.edit().putBoolean(darkModeKey, isEnabled).apply()
                        }
                    )
                }
            }
        }
        }
    }
}

@Composable
@ExperimentalGetImage
fun CameraScreen(
    navController: NavController
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
                    setBackgroundColor(AndroidColor.BLACK)
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    // hohe Auflösung bevorzugen, falls verfügbar
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    // optional: Mindestverhältnis oder Größenbereich setzen
                    .setAspectRatioStrategy(
                        AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                    )
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()

                val selector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                
                preview.surfaceProvider = previewView.surfaceProvider
                
                // WICHTIG: Hier wird die Auflösung für die Analyse hochgeschraubt
                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val lastDetectedBarcodes = mutableListOf<Barcode>()
                var lastDetectionTime = System.currentTimeMillis()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val image = imageProxy.image
                    if (image != null) {
                        val processImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(processImage)
                            .addOnSuccessListener { barcodes ->
                                var newCodeAdded = false
                                if (barcodes.isNotEmpty()) {

                                    barcodes.forEach { newBarcode ->

                                        val alreadyExists = lastDetectedBarcodes.any {
                                            it.rawValue == newBarcode.rawValue
                                        }

                                        if (!alreadyExists) {
                                            lastDetectedBarcodes.add(newBarcode)
                                            newCodeAdded = true
                                        }

                                        if (newCodeAdded) {
                                            lastDetectionTime = System.currentTimeMillis()
                                        }
                                    }
                                }

                                if (lastDetectedBarcodes.isNotEmpty() &&
                                    System.currentTimeMillis() - lastDetectionTime > 1000
                                ) {
                                    imageAnalysis.clearAnalyzer()
                                    navigateToResults(lastDetectedBarcodes)
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
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

                        camera?.cameraControl?.startFocusAndMetering(
                            FocusMeteringAction.Builder(
                                previewView.meteringPointFactory.createPoint(
                                    previewView.width / 2f,
                                    previewView.height / 2f
                                )
                            ).build()
                        )

                        camera?.cameraControl?.enableTorch(false)
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
                .background(Color.Transparent)
                .safeDrawingPadding()
                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 45.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Kamera-Steuerungen
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = "Galerie öffnen",
                        tint = Color.White
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

                IconButton(
                    onClick = {
                        isFlashOn = !isFlashOn
                        camera?.cameraControl?.enableTorch(isFlashOn)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "Flash Toggle",
                        tint = if (isFlashOn) Color.Yellow else Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun BarcodeResultScreen(
    navController: NavController,
    scannedCodes: List<ScannedCode>,
    isDarkThemeEnabled: Boolean,
    onDarkThemeToggle: (Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ResultFilter.ALL) }

    val filteredCodes = remember(scannedCodes, searchQuery, filter) {
        val normalizedQuery = searchQuery.trim().lowercase(Locale.getDefault())
        scannedCodes.filter { code ->
            val matchesFilter = when (filter) {
                ResultFilter.ALL -> true
                ResultFilter.GS1_ONLY -> code.isGs1
                ResultFilter.NON_GS1 -> !code.isGs1
            }
            val matchesQuery = normalizedQuery.isEmpty() ||
                code.type.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                code.rawValue.lowercase(Locale.getDefault()).contains(normalizedQuery)
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurueck",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = "Scan Ergebnisse (${filteredCodes.size}/${scannedCodes.size})",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Switch(
                checked = isDarkThemeEnabled,
                onCheckedChange = onDarkThemeToggle
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Suchen (Typ oder Inhalt)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == ResultFilter.ALL,
                onClick = { filter = ResultFilter.ALL },
                label = { Text("Alle") }
            )
            FilterChip(
                selected = filter == ResultFilter.GS1_ONLY,
                onClick = { filter = ResultFilter.GS1_ONLY },
                label = { Text("Nur GS1") }
            )
            FilterChip(
                selected = filter == ResultFilter.NON_GS1,
                onClick = { filter = ResultFilter.NON_GS1 },
                label = { Text("Ohne GS1") }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredCodes.isEmpty()) {
            Text(
                text = "Keine Ergebnisse fuer die aktuelle Suche/Filter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredCodes) { code ->
                    CodeResultItem(code)
                }
            }
        }
    }
}

fun buildHighlightedGS1String(value: String, parsedData: Map<String, String>): AnnotatedString {
    return buildAnnotatedString {

        var i = 0

        while (i < value.length) {

            // <GS> hervorheben
            if (value.startsWith("<GS>", i)) {
                withStyle(
                    SpanStyle(
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("<GS>")
                }
                i += 4
                continue
            }
            // AI erkennen (2-3 Ziffern am Anfang eines Segments)
            parsedData.forEach { (ai, aiValue) ->
                if (value[i].isDigit()) {
                    if (value.startsWith(ai, i) && value.startsWith(aiValue, i + ai.length)) {
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(ai)
                        }
                        i += ai.length
                        continue
                    }
                }
            }
            append(value[i])
            i++
        }
    }
}

private fun buildPlausibilityHint(ai: String, value: String, parserMessage: String): String {
    val definition = GS1Parser.aiDefinitions[ai]
    return when {
        parserMessage.contains("falsche") && parserMessage.contains("L") -> {
            if (definition == null) {
                "Laenge unplausibel: erkannt ${value.length} Zeichen."
            } else if (definition.minLength == definition.maxLength) {
                "Laenge unplausibel: erwartet ${definition.minLength}, erkannt ${value.length}."
            } else {
                "Laenge unplausibel: erwartet ${definition.minLength}-${definition.maxLength}, erkannt ${value.length}."
            }
        }
        parserMessage.contains("Nur Zahlen") -> "Nur Ziffern erlaubt. Bitte auf Sonderzeichen achten."
        parserMessage.contains("Datum muss") -> "Datumsformat ungueltig. Erwartet JJMMTT (z.B. 260822)."
        parserMessage.contains("Ung") && parserMessage.contains("Datum") -> "Datum ist kalendarisch ungueltig (JJMMTT)."
        parserMessage.contains("Pr") && parserMessage.contains("ziffer") -> "Pruefziffer ungueltig. Code ist evtl. unvollstaendig oder falsch gescannt."
        else -> parserMessage
    }
}

@Composable
fun CodeResultItem(code: ScannedCode) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Typ: ${code.type}",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = if (code.isGs1) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code.rawValue))
                        Toast.makeText(context, "Code kopiert", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Code kopieren")
                }
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${code.type}: ${code.rawValue}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Code teilen"))
                    }
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = "Code teilen")
                }
            }

            if (code.isGs1) {
                val parserInput = code.rawValue.replace("<GS>", "\u001d")
                val parsedDataRaw = GS1Parser.parse(parserInput, formatDatesForDisplay = false)
                val parsedData = GS1Parser.parse(parserInput, formatDatesForDisplay = true)
                Text(
                    text = buildAnnotatedString {
                        append("Roh-Daten:\n")
                        append(buildHighlightedGS1String(code.rawValue, parsedDataRaw))
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                parsedData.forEach { (ai, displayValue) ->
                    val rawValue = parsedDataRaw[ai] ?: displayValue
                    val (plausibility, message) = GS1Parser.checkPlausibility(ai, rawValue)
                    val warningHint = buildPlausibilityHint(ai, rawValue, message)

                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "($ai) ${GS1Parser.aiDefinitions[ai]?.name ?: "Unbekannt"}:\n$displayValue",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(displayValue))
                                    Toast.makeText(context, "AI-Wert kopiert", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "AI-Wert kopieren"
                                )
                            }
                        }
                        if (!plausibility) {
                            Text(
                                text = "Warnung: $warningHint",
                                color = Color.Red,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            } else {
                Text(text = "Roh-Daten: \n ${code.rawValue}", style = MaterialTheme.typography.bodySmall)
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












