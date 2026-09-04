package com.sih.ui.screens.inspection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sih.util.Localization
import android.widget.Toast
import com.sih.network.ApiClient
import com.sih.network.dto.ImageQualityResult
import com.sih.network.dto.QualityStatus
import com.sih.repository.InspectionRepository
import com.sih.util.quality.ImageQualityAnalyzer
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    selectedLanguage: String,
    onClose: () -> Unit,
    onCapture: (Uri) -> Unit = {},
    onCaptureComplete: (List<Uri>) -> Unit = { uris -> if (uris.isNotEmpty()) onCapture(uris.first()) }
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraPreviewContent(selectedLanguage, onClose, onCaptureComplete)
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
                TextButton(onClick = onClose) {
                    Text("Cancel", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CameraPreviewContent(
    language: String,
    onClose: () -> Unit,
    onCaptureComplete: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val controller = remember {
        LifecycleCameraController(context).apply {
            bindToLifecycle(lifecycleOwner)
        }
    }
    
    val capturedUris = remember { mutableStateListOf<Uri>() }
    var isCapturing by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var isAnalyzingQuality by remember { mutableStateOf(false) }
    var pendingQualityResult by remember { mutableStateOf<ImageQualityResult?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showQualityRejectDialog by remember { mutableStateOf(false) }
    var showQualityWarningDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Alignment Guide Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 72.dp)
                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (capturedUris.isEmpty()) "Align Package Here" else "Align Next Side / Panel Here",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        // Top Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            if (capturedUris.isNotEmpty()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${capturedUris.size} Side${if (capturedUris.size > 1) "s" else ""} Captured",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
            IconButton(
                onClick = {
                    val nextState = !isTorchOn
                    isTorchOn = nextState
                    controller.enableTorch(nextState)
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isTorchOn) Color(0xFFFFD600).copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.35f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FlashOn,
                        contentDescription = if (isTorchOn) "Torch On" else "Torch Off",
                        tint = if (isTorchOn) Color(0xFFFFD600) else Color.White
                    )
                }
            }
        }
        
        // Indicators
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CameraIndicator(icon = Icons.Default.Lightbulb, label = "Good Light", active = true)
            CameraIndicator(icon = Icons.Default.Warning, label = "Blurry", active = false)
        }
        
        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Captured Images Preview Carousel
            if (capturedUris.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    items(capturedUris.size) { index ->
                        val uri = capturedUris[index]
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                        ) {
                            coil.compose.AsyncImage(
                                model = uri,
                                contentDescription = "Captured side ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Number badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(topEnd = 4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                            }
                            // Delete button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                                    .background(Color.Red.copy(alpha = 0.85f), CircleShape)
                                    .clickable { capturedUris.removeAt(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = if (isAnalyzingQuality) {
                    "Evaluating Image Quality Gate..."
                } else if (isCapturing) {
                    "Saving image..."
                } else if (capturedUris.isEmpty()) {
                    "Tap shutter to capture • Quality Gate checks focus & glare"
                } else {
                    "${capturedUris.size} side(s) added • Capture another side or tap Done"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Shutter & Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Left slot: Retake / Reset
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    if (capturedUris.isNotEmpty()) {
                        TextButton(onClick = { capturedUris.clear() }) {
                            Text("Reset", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Center: Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(if (isCapturing || isAnalyzingQuality) Color.Gray else Color.White, CircleShape)
                        .padding(4.dp)
                        .border(4.dp, Color.Gray, CircleShape)
                ) {
                    IconButton(
                        onClick = {
                            if (!isCapturing && !isAnalyzingQuality) {
                                isCapturing = true
                                takePhoto(
                                    context = context,
                                    controller = controller,
                                    executor = cameraExecutor,
                                    onPhotoCaptured = { uri ->
                                        coroutineScope.launch {
                                            isAnalyzingQuality = true
                                            val quality = ImageQualityAnalyzer.analyze(context, uri)
                                            isAnalyzingQuality = false
                                            isCapturing = false

                                            when (quality.overallStatus) {
                                                QualityStatus.REJECT -> {
                                                    pendingQualityResult = quality
                                                    pendingUri = uri
                                                    showQualityRejectDialog = true
                                                }
                                                QualityStatus.WARNING -> {
                                                    pendingQualityResult = quality
                                                    pendingUri = uri
                                                    showQualityWarningDialog = true
                                                }
                                                QualityStatus.ACCEPT -> {
                                                    InspectionRepository.currentQualityResult = quality
                                                    capturedUris.add(uri)
                                                    Toast.makeText(
                                                        context,
                                                        "✓ Quality Passed: ${(quality.qualityScore * 100).toInt()}%",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    },
                                    onError = {
                                        isCapturing = false
                                        isAnalyzingQuality = false
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        enabled = !isCapturing && !isAnalyzingQuality
                    ) {
                        if (isCapturing || isAnalyzingQuality) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                // Right slot: Done Button
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    if (capturedUris.isNotEmpty()) {
                        Button(
                            onClick = {
                                onCaptureComplete(capturedUris.toList())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(22.dp))
                                Text(
                                    "Done",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quality Gate: REJECTION DIALOG (Blur / Critical Defect)
        if (showQualityRejectDialog && pendingQualityResult != null) {
            AlertDialog(
                onDismissRequest = { /* Force explicit user action */ },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Image Quality Gate: REJECTED",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = pendingQualityResult?.rejectionReason ?: "Image is too blurry for legal text verification.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Blur Score: ${((pendingQualityResult?.blurScore ?: 0f) * 100).toInt()}% • Motion/Autofocus Blur Detected\n\nUnder Legal Metrology evidentiary standards, defective photos cannot be used for statutory compliance audits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            val audited = pendingQualityResult!!.copy(
                                qualityOverride = true,
                                overrideReason = "Officer Field Override - Challenging Lighting / Glossy Surface",
                                overrideOfficer = ApiClient.getTokenManager()?.getUserName() ?: "Field Inspector",
                                overrideTimestamp = java.time.LocalDateTime.now().toString()
                            )
                            InspectionRepository.currentQualityResult = audited
                            pendingUri?.let { capturedUris.add(it) }
                            showQualityRejectDialog = false
                            pendingUri = null
                            pendingQualityResult = null
                            Toast.makeText(context, "Officer Override Logged under Section 65B", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Proceed with Override", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showQualityRejectDialog = false
                            pendingUri = null
                            pendingQualityResult = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("📸 Retake Photo")
                    }
                }
            )
        }

        // Quality Gate: WARNING DIALOG (Localized Glare / Low Contrast)
        if (showQualityWarningDialog && pendingQualityResult != null) {
            AlertDialog(
                onDismissRequest = { /* Force explicit user action */ },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Quality Gate: WARNING",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = pendingQualityResult?.rejectionReason ?: "Localized glare or reflection detected on package.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (pendingQualityResult?.defectRegions?.isNotEmpty() == true) {
                            Surface(
                                color = Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Defect Region: ${pendingQualityResult!!.defectRegions.first().description}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE65100),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        Text(
                            text = "Any declaration inside the glared area will be flagged 'UNABLE TO VERIFY'. Non-glared panels (like manufacturer address) will be audited normally under Rule 6.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            val audited = pendingQualityResult!!.copy(
                                qualityOverride = true,
                                overrideReason = "Inspector Field Override - Specular Foil Pouch",
                                overrideOfficer = ApiClient.getTokenManager()?.getUserName() ?: "Inspector Rajesh Sharma",
                                overrideTimestamp = java.time.LocalDateTime.now().toString()
                            )
                            InspectionRepository.currentQualityResult = audited
                            pendingUri?.let { capturedUris.add(it) }
                            showQualityWarningDialog = false
                            pendingUri = null
                            pendingQualityResult = null
                            Toast.makeText(context, "Officer Override Logged for Section 65B Audit", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Proceed with Override", color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showQualityWarningDialog = false
                            pendingUri = null
                            pendingQualityResult = null
                        }
                    ) {
                        Text("📸 Retake Photo")
                    }
                }
            )
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            try {
                controller.enableTorch(false)
            } catch (e: Exception) {
                // ignore
            }
            cameraExecutor.shutdown()
        }
    }
}

private fun takePhoto(
    context: Context,
    controller: LifecycleCameraController,
    executor: ExecutorService,
    onPhotoCaptured: (Uri) -> Unit,
    onError: (Exception) -> Unit
) {
    val outputDirectory = context.externalCacheDir ?: context.cacheDir
    val photoFile = File(
        outputDirectory,
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    controller.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                ContextCompat.getMainExecutor(context).execute {
                    onPhotoCaptured(savedUri)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraScreen", "Photo capture failed: ${exception.message}", exception)
                ContextCompat.getMainExecutor(context).execute {
                    onError(exception)
                }
            }
        }
    )
}

@Composable
fun CameraIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) Color.Black.copy(alpha = 0.6f) else Color.Red.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun CameraScreenPreview() {
    CameraScreen("English", {}, {})
}
