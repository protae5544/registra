#!/data/data/com.termux/files/usr/bin/bash
# รันที่ root ของ repo registra:  bash apply_fix.sh
set -e
if [ ! -f settings.gradle.kts ] || [ ! -d app/src/main/java/com/chb/form ]; then
  echo "ผิดที่: ต้อง cd เข้าโฟลเดอร์ registra (ที่มี settings.gradle.kts) ก่อน"; exit 1
fi

mkdir -p "$(dirname ".github/workflows/android-ci.yml")"
cat > '.github/workflows/android-ci.yml' <<'CHB_EOF_MARK'
name: Android CI

on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v5

      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: 'zulu'
          java-version: '17'
          cache: 'gradle'

      - name: Make gradlew executable
        run: chmod +x ./gradlew

      # สร้าง debug APK อย่างเดียว (ไม่รัน lint / R8 ของ release ที่ไม่จำเป็นสำหรับการติดตั้งใช้งาน)
      - name: Build debug APK
        run: ./gradlew :app:assembleDebug --stacktrace

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: chb-form-debug-apk
          path: app/build/outputs/apk/debug/*.apk
CHB_EOF_MARK
echo "เขียนแล้ว: .github/workflows/android-ci.yml"

mkdir -p "$(dirname "app/src/main/java/com/chb/form/MainActivity.kt")"
cat > 'app/src/main/java/com/chb/form/MainActivity.kt' <<'CHB_EOF_MARK'
package com.chb.form

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chb.form.ui.CameraScreen
import com.chb.form.ui.FormWizard
import com.chb.form.ui.PreviewScreen
import com.chb.form.ui.SignaturePad
import com.chb.form.ui.theme.ChbTheme
import com.chb.form.vm.FormViewModel
import com.chb.form.vm.Ui
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: FormViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChbTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                val snack = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                var screen by remember { mutableStateOf<Screen>(Screen.Form) }

                BackHandler(enabled = screen != Screen.Form) { screen = Screen.Form }

                LaunchedEffect(Unit) { vm.toast.collect { snack.showSnackbar(it) } }

                LaunchedEffect(ui) {
                    when (val u = ui) {
                        is Ui.Done -> {
                            screen = Screen.Preview(u.file)
                            vm.resetUi()
                        }
                        is Ui.Error -> {
                            scope.launch { snack.showSnackbar(u.msg) }
                            vm.resetUi()
                        }
                        else -> Unit
                    }
                }

                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        when (val sc = screen) {
                            Screen.Form -> FormWizard(
                                vm = vm,
                                onCamera = { screen = Screen.Camera },
                                onSign = { screen = Screen.Sign },
                                onExport = { vm.export() }
                            )
                            Screen.Camera -> CameraScreen(
                                onCaptured = {
                                    vm.saveCard(it)
                                    screen = Screen.Form
                                },
                                onClose = { screen = Screen.Form }
                            )
                            Screen.Sign -> SignaturePad(
                                onDone = {
                                    vm.saveSignature(it)
                                    screen = Screen.Form
                                },
                                onCancel = { screen = Screen.Form }
                            )
                            is Screen.Preview -> PreviewScreen(sc.file) { screen = Screen.Form }
                        }
                        SnackbarHost(
                            snack,
                            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        )
                    }
                }

                if (ui is Ui.Busy) {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        title = { Text("กำลังสร้างเอกสาร") },
                        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    )
                }
            }
        }
    }
}

sealed interface Screen {
    data object Form : Screen
    data object Camera : Screen
    data object Sign : Screen
    data class Preview(val file: File) : Screen
}
CHB_EOF_MARK
echo "เขียนแล้ว: app/src/main/java/com/chb/form/MainActivity.kt"

mkdir -p "$(dirname "app/src/main/java/com/chb/form/vm/FormViewModel.kt")"
cat > 'app/src/main/java/com/chb/form/vm/FormViewModel.kt' <<'CHB_EOF_MARK'
package com.chb.form.vm

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chb.form.data.F
import com.chb.form.data.FormData
import com.chb.form.data.FormStore
import com.chb.form.pdf.FormPdf
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Ui {
    data object Idle : Ui
    data object Busy : Ui
    data class Done(val file: File) : Ui
    data class Error(val msg: String) : Ui
}

class FormViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application = app

    private val _state = MutableStateFlow(FormData())
    val state: StateFlow<FormData> = _state.asStateFlow()

    private val _ui = MutableStateFlow<Ui>(Ui.Idle)
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch { _state.value = FormStore.load(ctx) }
    }

    // ---------- แก้ไขข้อมูล ----------
    fun setField(i: Int, v: String) = update { s ->
        s.copy(fields = s.fields.toMutableList().also { l -> l[i] = v })
    }

    fun toggle(i: Int) = update { s ->
        s.copy(checks = s.checks.toMutableList().also { l -> l[i] = !l[i] })
    }

    /** เลือกได้ค่าเดียวในกลุ่ม — แตะซ้ำเพื่อยกเลิก */
    fun pickOne(group: List<Int>, i: Int) = update { s ->
        val on = !s.check(i)
        s.copy(checks = s.checks.mapIndexed { n, v -> if (n in group) (n == i && on) else v })
    }

    fun setSignatureEnabled(b: Boolean) = update { it.copy(withSignature = b) }

    private fun update(f: (FormData) -> FormData) {
        _state.update(f)
        autosave()
    }

    private fun autosave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350)
            FormStore.save(ctx, _state.value)
        }
    }

    // ---------- รูปบัตร ----------
    fun saveCard(bmp: Bitmap) = viewModelScope.launch(Dispatchers.IO) {
        val f = File(ctx.cacheDir, "img/card_${System.currentTimeMillis()}.jpg")
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        _state.update { it.copy(cardPath = f.absolutePath) }
        FormStore.save(ctx, _state.value)
        ocr(bmp)
    }

    fun clearCard() = update { it.copy(cardPath = null) }

    /** อ่านชื่อ-สกุลจากบัตรแล้วเติมช่อง 5.1 ให้อัตโนมัติ */
    private suspend fun ocr(bmp: Bitmap) {
        runCatching {
            val result = TextRecognition
                .getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(InputImage.fromBitmap(bmp, 0))
                .await()
            val prefixes = listOf("นาย", "นาง", "น.ส.", "นางสาว")
            val name: String? = result.textBlocks
                .flatMap { it.lines }
                .map { it.text.trim() }
                .firstOrNull { line -> prefixes.any { line.startsWith(it) } }
            if (!name.isNullOrBlank() && _state.value.field(F.EMG_NAME).isBlank()) {
                _state.update { s ->
                    s.copy(fields = s.fields.toMutableList().also { l -> l[F.EMG_NAME] = name })
                }
                _toast.emit("อ่านชื่อจากบัตรแล้ว — ตรวจสอบความถูกต้องอีกครั้ง")
            }
        }
    }

    // ---------- ลายเซ็น ----------
    fun saveSignature(bmp: Bitmap) = viewModelScope.launch(Dispatchers.IO) {
        val f = File(ctx.cacheDir, "img/sign_${System.currentTimeMillis()}.png")
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        _state.update { it.copy(signaturePath = f.absolutePath) }
        FormStore.save(ctx, _state.value)
    }

    fun clearSignature() = update { it.copy(signaturePath = null) }

    // ---------- สร้าง PDF ----------
    fun export() {
        if (_ui.value is Ui.Busy) return
        viewModelScope.launch {
            _ui.value = Ui.Busy
            runCatching {
                withContext(Dispatchers.IO) {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    val name = _state.value.field(F.EMG_NAME)
                        .ifBlank { "form" }
                        .replace(Regex("[^\\p{L}\\p{M}\\p{N}]"), "")
                    val out = File(ctx.cacheDir, "pdf/CHB$name$stamp.pdf")
                    FormPdf(ctx).render(_state.value, out)
                }
            }
                .onSuccess { _ui.value = Ui.Done(it) }
                .onFailure { _ui.value = Ui.Error(it.message ?: "สร้างไฟล์ไม่สำเร็จ") }
        }
    }

    fun resetUi() {
        _ui.value = Ui.Idle
    }

    fun reset() = viewModelScope.launch {
        FormStore.clear(ctx)
        _state.value = FormData()
    }
}
CHB_EOF_MARK
echo "เขียนแล้ว: app/src/main/java/com/chb/form/vm/FormViewModel.kt"

mkdir -p "$(dirname "app/src/main/java/com/chb/form/ui/SignaturePad.kt")"
cat > 'app/src/main/java/com/chb/form/ui/SignaturePad.kt' <<'CHB_EOF_MARK'
package com.chb.form.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private const val PEN_PX = 6f

/** หน้าเซ็นลายเซ็นเต็มจอ — กด "บันทึก" จะได้ Bitmap โปร่งใส ครอปพอดีลายเซ็น */
@Composable
fun SignaturePad(onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("เซ็นชื่อในกรอบ", style = MaterialTheme.typography.titleMedium)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start -> current = listOf(start) },
                        onDrag = { change, _ -> current = current + change.position },
                        onDragEnd = {
                            if (current.isNotEmpty()) strokes.add(current)
                            current = emptyList()
                        },
                        onDragCancel = { current = emptyList() }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                strokes.forEach { drawStroke(it) }
                drawStroke(current)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("ยกเลิก")
            }
            OutlinedButton(
                onClick = {
                    strokes.clear()
                    current = emptyList()
                },
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("ล้าง") }
            Button(
                onClick = { onDone(renderSignature(strokes.toList(), size.width, size.height)) },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1.4f).height(52.dp)
            ) { Text("บันทึก") }
        }
    }
}

private fun DrawScope.drawStroke(points: List<Offset>) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(Color.Black, radius = PEN_PX / 2f, center = points[0])
        return
    }
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
    drawPath(
        path,
        Color.Black,
        style = Stroke(width = PEN_PX, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/** วาดเส้นลง Bitmap (พื้นโปร่งใส) แล้วครอปเหลือเฉพาะบริเวณลายเซ็น */
private fun renderSignature(strokes: List<List<Offset>>, w: Int, h: Int): Bitmap {
    val full = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(full)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.BLACK
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = PEN_PX
    paint.strokeCap = android.graphics.Paint.Cap.ROUND
    paint.strokeJoin = android.graphics.Paint.Join.ROUND

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = 0f
    var maxY = 0f
    for (pts in strokes) {
        if (pts.isEmpty()) continue
        if (pts.size == 1) {
            canvas.drawPoint(pts[0].x, pts[0].y, paint)
        } else {
            val path = android.graphics.Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            canvas.drawPath(path, paint)
        }
        for (p in pts) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
    }

    val pad = 12
    val x0 = (minX.toInt() - pad).coerceIn(0, full.width - 1)
    val y0 = (minY.toInt() - pad).coerceIn(0, full.height - 1)
    val x1 = (maxX.toInt() + pad).coerceIn(x0 + 1, full.width)
    val y1 = (maxY.toInt() + pad).coerceIn(y0 + 1, full.height)
    return Bitmap.createBitmap(full, x0, y0, x1 - x0, y1 - y0)
}
CHB_EOF_MARK
echo "เขียนแล้ว: app/src/main/java/com/chb/form/ui/SignaturePad.kt"

mkdir -p "$(dirname "app/src/main/java/com/chb/form/ui/CameraScreen.kt")"
cat > 'app/src/main/java/com/chb/form/ui/CameraScreen.kt' <<'CHB_EOF_MARK'
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
CHB_EOF_MARK
echo "เขียนแล้ว: app/src/main/java/com/chb/form/ui/CameraScreen.kt"

echo "เสร็จ — ต่อด้วย: git add -A && git commit -m 'fix: compile errors' && git push"
