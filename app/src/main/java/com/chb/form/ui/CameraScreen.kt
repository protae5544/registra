package com.chb.form.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraScreen(onCaptured: (Bitmap) -> Unit, onClose: () -> Unit) {
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
    LaunchedEffect(Unit) {
        if (!granted) ask.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        CameraContent(onCaptured, onClose)
    } else {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.NoPhotography, null, Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("ต้องอนุญาตใช้กล้องก่อน")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("อนุญาต") }
            TextButton(onClick = onClose) { Text("ปิด") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraContent(onCaptured: (Bitmap) -> Unit, onClose: () -> Unit) {
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

    DisposableEffect(Unit) { onDispose { exec.shutdown() } }

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

        // กรอบนำสัดส่วนบัตร (เจาะรูโปร่งใสตรงกลาง)
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val w = size.width * 0.88f
            val h = w * 54f / 85.6f
            val l = (size.width - w) / 2f
            val t = (size.height - h) / 2f
            drawRect(Color.Black.copy(alpha = 0.55f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(l, t),
                size = Size(w, h),
                cornerRadius = CornerRadius(20f),
                blendMode = BlendMode.Clear
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(l, t),
                size = Size(w, h),
                cornerRadius = CornerRadius(20f),
                style = Stroke(width = 4f)
            )
        }

        TopAppBar(
            title = { Text("ถ่ายรูปบัตร", color = Color.White) },
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
            Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("จัดบัตรให้อยู่ในกรอบแล้วกดถ่าย", color = Color.White)
            Spacer(Modifier.height(14.dp))
            LargeFloatingActionButton(
                onClick = {
                    if (!busy) {
                        busy = true
                        capture.takePicture(
                            exec,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bmp = runCatching { image.toBitmapCropped(0.88f) }.getOrNull()
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
                }
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                } else {
                    Icon(Icons.Rounded.PhotoCamera, "ถ่าย", Modifier.size(34.dp))
                }
            }
        }
    }
}

/** แปลง ImageProxy (JPEG) → Bitmap พร้อมหมุนตามกล้อง และครอปตามกรอบนำ */
private fun ImageProxy.toBitmapCropped(widthFraction: Float): Bitmap {
    val buf = planes[0].buffer
    val bytes = ByteArray(buf.remaining())
    buf.get(bytes)
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: error("decode failed")
    if (imageInfo.rotationDegrees != 0) {
        val m = Matrix()
        m.postRotate(imageInfo.rotationDegrees.toFloat())
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    val cw = (bmp.width * widthFraction).toInt()
    val ch = (cw * 54f / 85.6f).toInt().coerceAtMost(bmp.height)
    val x = (bmp.width - cw) / 2
    val y = (bmp.height - ch) / 2
    return Bitmap.createBitmap(bmp, x, y, cw, ch)
}
