package com.chb.form.ui
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
@Composable
fun CameraScreen(onCaptured: (Bitmap) -> Unit, onClose: () -> Unit) { val ctx = LocalContext.current
val owner = LocalLifecycleOwner.current
var granted by remember { mutableStateOf( ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ) }
val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it
}
LaunchedEffect(Unit) { if (!granted)
ask.launch(Manifest.permission.CAMERA) }
if (!granted) { Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { Icon(Icons.Rounded.NoPhotography, null, Modifier.size(48.dp))
Spacer(Modifier.height(12.dp))
Text("ต้องอนุญาตใช้กล้องก่อน")
Spacer(Modifier.height(12.dp))
Button({ ask.launch(Manifest.permission.CAMERA) }) { Text("อนุญาต") }
TextButton(onClose) { Text("ปิด") } }
return
}
val exec = remember { Executors.newSingleThreadExecutor() }
val capture = remember { ImageCapture.Builder() .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY) .build() }
var torch by remember { mutableStateOf(false) }
var camera by remember { mutableStateOf<Camera?>(null) }
var busy by remember { mutableStateOf(false) }
DisposableEffect(Unit) { onDispose { exec.shutdown() } }
Box(Modifier.fillMaxSize()) { androidx.compose.ui.viewinterop.AndroidView( modifier = Modifier.fillMaxSize(), factory = { c -> PreviewView(c).apply { scaleType = PreviewView.ScaleType.FILL_CENTER
val future = ProcessCameraProvider.getInstance(c)
future.addListener({ val provider = future.get()
val preview = Preview.Builder().build() .also { it.setSurfaceProvider(surfaceProvider) }
provider.unbindAll()
camera = provider.bindToLifecycle( owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture ) }, ContextCompat.getMainExecutor(c)) } } )
//  กรอบนำสัดส่วนบัตร
Canvas(Modifier.fillMaxSize()) { val w = size.width * 0.88f
val h = w * 54f / 85.6f
val l = (size.width - w) / 2
val t = (size.height - h) / 2
drawRect(Color.Black.copy(alpha = 0.55f))
drawRoundRect( color = Color.Transparent, topLeft = Offset(l, t), size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f), blendMode = androidx.compose.ui.graphics.BlendMode.Clear )
drawRoundRect( color = Color.White, topLeft = Offset(l, t), size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f), style = Stroke(width = 4f) ) }
TopAppBar( title = { Text("ถ่ายรูปบัตร", color = Color.White) }, navigationIcon = { IconButton(onClose) { Icon(Icons.Rounded.Close, "ปิด", tint = Color.White) } }, actions = { IconButton({ torch = !torch
camera?.cameraControl?.enableTorch(torch) }) { Icon( if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, "ไฟฉาย", tint = Color.White ) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent) )
Column( Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally ) { Text("จัดบัตรให้อยู่ในกรอบแล้วกดถ่าย", color = Color.White)
Spacer(Modifier.height(14.dp))
LargeFloatingActionButton( onClick = { if (busy)
return@LargeFloatingActionButton
busy = true
capture.takePicture(exec, object : ImageCapture.OnImageCapturedCallback() { override fun onCaptureSuccess(image: ImageProxy) { val bmp = image.toBitmapCropped(0.88f)
image.close()
ContextCompat.getMainExecutor(ctx).execute { busy = false
onCaptured(bmp) } }
override fun onError(e: ImageCaptureException) { ContextCompat.getMainExecutor(ctx).execute { busy = false
} } }) } ) { if (busy)
CircularProgressIndicator(Modifier.size(28.dp)) else
Icon(Icons.Rounded.PhotoCamera, "ถ่าย", Modifier.size(34.dp)) } } } }
/*แปลง ImageProxy → Bitmap พร้อมหมุนตามกล้อง และครอปตามกรอบนำ */
private fun ImageProxy.toBitmapCropped(widthFraction: Float): Bitmap { val buf = planes[0].buffer
val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
if (imageInfo.rotationDegrees != 0) { val m = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) }
val cw = (bmp.width * widthFraction).toInt()
val ch = (cw * 54f / 85.6f).toInt().coerceAtMost(bmp.height)
val x = (bmp.width - cw) / 2
val y = (bmp.height - ch) / 2
return Bitmap.createBitmap(bmp, x, y, cw, ch) }
