package com.chb.form.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chb.form.ui.theme.Pistachio
import java.io.InputStream
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    onCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val bmp = decodeUriWithOrientation(ctx, uri)
            if (bmp != null) onCaptured(bmp)
        }
    }

    LaunchedEffect(Unit) {
        if (!granted) ask.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        CameraContent(
            onCaptured = onCaptured,
            onGallery = { galleryLauncher.launch("image/*") },
            onClose = onClose
        )
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF1E241C))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.NoPhotography, null, Modifier.size(56.dp), tint = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(
                "จำเป็นต้องอนุญาตการเข้าถึงกล้อง",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "หรือท่านสามารถเลือกรูปภาพบัตรที่มีอยู่แล้วจากแกลเลอรีได้ทันที",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { ask.launch(Manifest.permission.CAMERA) },
                colors = ButtonDefaults.buttonColors(containerColor = Pistachio)
            ) {
                Text("อนุญาตใช้กล้อง", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Rounded.PhotoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text("เลือกรูปจากแกลเลอรี")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose) {
                Text("ยกเลิก", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraContent(
    onCaptured: (Bitmap) -> Unit,
    onGallery: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val exec = remember { Executors.newSingleThreadExecutor() }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { exec.shutdown() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                val pv = PreviewView(c)
                pv.scaleType = PreviewView.ScaleType.FILL_CENTER
                val future = ProcessCameraProvider.getInstance(c)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(pv.surfaceProvider)
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        owner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                }, ContextCompat.getMainExecutor(c))
                pv
            }
        )

        // กรอบไกด์นำสัดส่วนบัตรประชาชน 85.6 : 54 พร้อมเจาะรูโปร่งใส
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val w = size.width * 0.88f
            val h = w * 54f / 85.6f
            val l = (size.width - w) / 2f
            val t = (size.height - h) / 2f - 30f // เลื่อนขึ้นเล็กน้อยเพื่อเผื่อปุ่มชัตเตอร์ด้านล่าง
            drawRect(Color.Black.copy(alpha = 0.55f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(l, t),
                size = Size(w, h),
                cornerRadius = CornerRadius(20f),
                blendMode = BlendMode.Clear
            )
            drawRoundRect(
                color = Pistachio,
                topLeft = Offset(l, t),
                size = Size(w, h),
                cornerRadius = CornerRadius(20f),
                style = Stroke(width = 4f)
            )
        }

        TopAppBar(
            title = { Text("ถ่ายรูปบัตรประชาชน", color = Color.White, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, "ปิด", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = {
                    torch = !torch
                    camera?.cameraControl?.enableTorch(torch)
                }) {
                    Icon(
                        if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                        "ไฟฉาย",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "จัดบัตรให้อยู่ในกรอบ และระวังเงาสะท้อน",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ปุ่มเลือกรูปจากแกลเลอรี
                IconButton(
                    onClick = onGallery,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.2f), shape = MaterialTheme.shapes.medium)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, "แกลเลอรี", tint = Color.White)
                }

                // ปุ่มชัตเตอร์ขนาดใหญ่
                LargeFloatingActionButton(
                    onClick = {
                        if (!busy) {
                            busy = true
                            capture.takePicture(
                                exec,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bmp = runCatching { image.toRotatedBitmap() }.getOrNull()
                                        image.close()
                                        ContextCompat.getMainExecutor(ctx).execute {
                                            busy = false
                                            if (bmp != null) onCaptured(bmp)
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        ContextCompat.getMainExecutor(ctx).execute { busy = false }
                                    }
                                }
                            )
                        }
                    },
                    containerColor = Pistachio,
                    contentColor = Color.Black
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black)
                    } else {
                        Icon(Icons.Rounded.PhotoCamera, "ถ่ายรูป", Modifier.size(34.dp))
                    }
                }

                // ตัวจัดกึ่งกลางให้สมดุล
                Spacer(Modifier.size(52.dp))
            }
        }
    }
}

/** แปลง ImageProxy เป็น Bitmap พร้อมหมุนตาม orientation ของกล้อง */
private fun ImageProxy.toRotatedBitmap(): Bitmap {
    val buf = planes[0].buffer
    val bytes = ByteArray(buf.remaining())
    buf.get(bytes)
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("decode failed")
    if (imageInfo.rotationDegrees != 0) {
        val m = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    return bmp
}

/** ถอดรหัสรูปจากแกลเลอรี ปรับขนาดและแก้ไขทิศทาง EXIF ป้องกันรูปกลับหัว */
private fun decodeUriWithOrientation(ctx: Context, uri: Uri): Bitmap? {
    return runCatching {
        // 1. อ่านทิศทาง EXIF
        var orientation = ExifInterface.ORIENTATION_NORMAL
        ctx.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
            val exif = ExifInterface(stream)
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }

        // 2. คำนวณขนาดภาพ (Downscale ป้องกัน Out of Memory)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

        val maxDim = 1920
        var sampleSize = 1
        while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        // 3. หมุนภาพตามค่า EXIF
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }
        bmp
    }.getOrNull()
}
