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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class ScannedCode(
    val rawValue: String,
    val type: String,
    val isGs1: Boolean
)

data class ScanHistoryEntry(
    val timestamp: Long,
    val codes: List<ScannedCode>
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
                            Text(stringResource(R.string.camera_permission_required))
                        }
                    }
                }
                                composable("result/{codesJson}/{fromHistory}") { backStackEntry ->
                    val codesJsonEncoded = backStackEntry.arguments?.getString("codesJson") ?: ""
                    val codesJson = URLDecoder.decode(codesJsonEncoded, StandardCharsets.UTF_8.toString())

                    val listType = object : TypeToken<List<ScannedCode>>() {}.type
                    val scannedCodes: List<ScannedCode> = Gson().fromJson(codesJson, listType)
                    val fromHistory = backStackEntry.arguments?.getString("fromHistory")?.toBoolean() ?: false

                    BarcodeResultScreen(
                        navController = navController,
                        scannedCodes = scannedCodes,
                        isDarkThemeEnabled = isDarkThemeEnabled,
                        onDarkThemeToggle = { isEnabled ->
                            isDarkThemeEnabled = isEnabled
                            preferences.edit().putBoolean(darkModeKey, isEnabled).apply()
                        },
                        fromHistory = fromHistory
                    )
                }
                composable("history") {
                    ScanHistoryScreen(navController = navController)
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
        
        appendHistoryEntry(context, scannedCodes)
        val codesJson = Gson().toJson(scannedCodes)
        val encodedJson = URLEncoder.encode(codesJson, StandardCharsets.UTF_8.toString())
        
        navController.navigate("result/$encodedJson/false")
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
                                Toast.makeText(context, context.getString(R.string.no_barcodes_found), Toast.LENGTH_SHORT).show()
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

        IconButton(
            onClick = {
                navController.navigate("history") {
                    launchSingleTop = true
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(top = 8.dp, end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = stringResource(R.string.history_desc),
                tint = Color.White
            )
        }

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
                        contentDescription = stringResource(R.string.open_gallery_desc),
                        tint = Color.White
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.zoom_label), color = Color.White, style = MaterialTheme.typography.labelSmall)
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
                        contentDescription = stringResource(R.string.flash_toggle_desc),
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
    onDarkThemeToggle: (Boolean) -> Unit,
    fromHistory: Boolean
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
            IconButton(onClick = {
                if (fromHistory) {
                    navController.popBackStack()
                } else {
                    navController.navigate("camera") {
                        popUpTo("camera") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_desc),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = stringResource(R.string.scan_results_title, filteredCodes.size, scannedCodes.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = {
                navController.navigate("history") {
                    popUpTo("camera") { inclusive = false }
                    launchSingleTop = true
                }
            }) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = stringResource(R.string.history_desc),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Switch(
                checked = isDarkThemeEnabled,
                onCheckedChange = onDarkThemeToggle
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(R.string.search_label)) },
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
                label = { Text(stringResource(R.string.filter_all)) }
            )
            FilterChip(
                selected = filter == ResultFilter.GS1_ONLY,
                onClick = { filter = ResultFilter.GS1_ONLY },
                label = { Text(stringResource(R.string.filter_gs1_only)) }
            )
            FilterChip(
                selected = filter == ResultFilter.NON_GS1,
                onClick = { filter = ResultFilter.NON_GS1 },
                label = { Text(stringResource(R.string.filter_no_gs1)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredCodes.isEmpty()) {
            Text(
                text = stringResource(R.string.no_results_for_filter),
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

private fun buildPlausibilityHint(context: android.content.Context, ai: String, value: String, parserMessage: String): String {
    val definition = GS1Parser.aiDefinitions[ai]
    return when {
        parserMessage.contains("falsche") && parserMessage.contains("L") -> {
            if (definition == null) {
                context.getString(R.string.plausibility_length_detected, value.length)
            } else if (definition.minLength == definition.maxLength) {
                context.getString(R.string.plausibility_length_expected_fixed, definition.minLength, value.length)
            } else {
                context.getString(R.string.plausibility_length_expected_range, definition.minLength, definition.maxLength, value.length)
            }
        }
        parserMessage.contains("Nur Zahlen") -> context.getString(R.string.plausibility_digits_only)
        parserMessage.contains("Datum muss") -> context.getString(R.string.plausibility_date_format)
        parserMessage.contains("Ung") && parserMessage.contains("Datum") -> context.getString(R.string.plausibility_date_invalid)
        parserMessage.contains("Pr") && parserMessage.contains("ziffer") -> context.getString(R.string.plausibility_checksum_invalid)
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
                    text = stringResource(R.string.type_label, code.type),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = if (code.isGs1) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code.rawValue))
                        Toast.makeText(context, context.getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy_code_desc))
                }
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${code.type}: ${code.rawValue}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_code_chooser)))
                    }
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = stringResource(R.string.share_code_desc))
                }
            }

            if (code.isGs1) {
                val parserInput = code.rawValue.replace("<GS>", "\u001d")
                val parsedDataRaw = GS1Parser.parse(parserInput, formatDatesForDisplay = false)
                val parsedData = GS1Parser.parse(parserInput, formatDatesForDisplay = true)
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.raw_data_label) + "\n")
                        append(buildHighlightedGS1String(code.rawValue, parsedDataRaw))
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                parsedData.forEach { (ai, displayValue) ->
                    val rawValue = parsedDataRaw[ai] ?: displayValue
                    val (plausibility, message) = GS1Parser.checkPlausibility(ai, rawValue)
                    val warningHint = buildPlausibilityHint(context, ai, rawValue, message)

                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = stringResource(R.string.ai_line, ai, GS1Parser.aiDefinitions[ai]?.name ?: stringResource(R.string.unknown_label), displayValue),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(displayValue))
                                    Toast.makeText(context, context.getString(R.string.ai_value_copied), Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_ai_value_desc)
                                )
                            }
                        }
                        if (!plausibility) {
                            Text(
                                text = stringResource(R.string.warning_prefix, warningHint),
                                color = Color.Red,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            } else {
                Text(text = stringResource(R.string.raw_data_value, code.rawValue), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ScanHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(loadHistory(context)) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    clearHistory(context)
                    history = emptyList()
                    showClearAllDialog = false
                    Toast.makeText(context, context.getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                navController.navigate("camera") {
                    popUpTo("camera") { inclusive = false }
                    launchSingleTop = true
                }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_desc),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = stringResource(R.string.history_title, history.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                val csv = historyToCsv(history)
                shareExport(context, csv, "text/csv", "scan_verlauf.csv")
            }) {
                Text(stringResource(R.string.export_csv))
            }
            Button(onClick = {
                val json = Gson().toJson(history)
                shareExport(context, json, "application/json", "scan_verlauf.json")
            }) {
                Text(stringResource(R.string.export_json))
            }
            Button(onClick = {
                showClearAllDialog = true
            }) {
                Text(stringResource(R.string.delete))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(history, key = { it.timestamp }) { entry ->
                    DismissibleHistoryEntry(
                        entry = entry,
                        onOpen = {
                            val codesJson = URLEncoder.encode(
                                Gson().toJson(entry.codes),
                                StandardCharsets.UTF_8.toString()
                            )
                            navController.navigate("result/$codesJson/true")
                        },
                        onDelete = {
                            history = removeHistoryEntry(context, entry)
                            Toast.makeText(context, context.getString(R.string.history_entry_deleted), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleHistoryEntry(
    entry: ScanHistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.6f }
    )

    val timeText = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        .format(Date(entry.timestamp))

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(0xFFE38396))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete_entry_desc),
                    tint = Color.White
                )
            }
        },
        content = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpen
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.history_entry_summary, timeText, entry.codes.size),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    entry.codes.forEach { code ->
                        Text(
                            text = "${code.type}: ${code.rawValue}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    )
}
private const val HISTORY_PREFS_NAME = "scan_history_prefs"
private const val HISTORY_KEY = "scan_history_entries"
private const val HISTORY_MAX_ENTRIES = 200

private fun loadHistory(context: android.content.Context): List<ScanHistoryEntry> {
    val prefs = context.getSharedPreferences(HISTORY_PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val json = prefs.getString(HISTORY_KEY, null) ?: return emptyList()
    return try {
        val type = object : TypeToken<List<ScanHistoryEntry>>() {}.type
        Gson().fromJson<List<ScanHistoryEntry>>(json, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveHistory(context: android.content.Context, entries: List<ScanHistoryEntry>) {
    val prefs = context.getSharedPreferences(HISTORY_PREFS_NAME, android.content.Context.MODE_PRIVATE)
    prefs.edit().putString(HISTORY_KEY, Gson().toJson(entries)).apply()
}

private fun appendHistoryEntry(context: android.content.Context, codes: List<ScannedCode>) {
    if (codes.isEmpty()) return
    val existing = loadHistory(context).toMutableList()
    existing.add(0, ScanHistoryEntry(timestamp = System.currentTimeMillis(), codes = codes))
    if (existing.size > HISTORY_MAX_ENTRIES) {
        existing.subList(HISTORY_MAX_ENTRIES, existing.size).clear()
    }
    saveHistory(context, existing)
}

private fun clearHistory(context: android.content.Context) {
    saveHistory(context, emptyList())
}

private fun removeHistoryEntry(
    context: android.content.Context,
    entry: ScanHistoryEntry
): List<ScanHistoryEntry> {
    val existing = loadHistory(context).toMutableList()
    val index = existing.indexOfFirst {
        it.timestamp == entry.timestamp && it.codes == entry.codes
    }
    if (index >= 0) {
        existing.removeAt(index)
        saveHistory(context, existing)
    }
    return existing
}

private fun historyToCsv(history: List<ScanHistoryEntry>): String {
    val header = "timestamp_iso,type,is_gs1,raw_value"
    val rows = history.flatMap { entry ->
        val timestampIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            .format(Date(entry.timestamp))
        entry.codes.map { code ->
            val escapedRaw = code.rawValue.replace("\"", "\"\"")
            "$timestampIso,${code.type},${code.isGs1},\"$escapedRaw\""
        }
    }
    return (listOf(header) + rows).joinToString("\n")
}

private fun shareExport(
    context: android.content.Context,
    content: String,
    mimeType: String,
    fileName: String
) {
    if (content.isBlank()) {
        Toast.makeText(context, context.getString(R.string.nothing_to_export), Toast.LENGTH_SHORT).show()
        return
    }

    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val outFile = File(exportDir, fileName)
    outFile.writeText(content)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_export_chooser)))
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
















