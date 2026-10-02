package com.chb.form

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chb.form.data.DormFormData
import com.chb.form.data.TemplateType
import com.chb.form.ocr.ExtractedIdCard
import com.chb.form.pdf.DormPdf
import com.chb.form.ui.*
import com.chb.form.ui.theme.ChbTheme
import com.chb.form.vm.FormViewModel
import com.chb.form.vm.Ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val vm: FormViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChbTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                val formState by vm.state.collectAsStateWithLifecycle()
                val lastExportedHash by vm.lastExportedHash.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()

                var currentToast by remember { mutableStateOf<ToastMessage?>(null) }
                var selectedTemplate by remember { mutableStateOf<TemplateType?>(null) }
                var screen by remember { mutableStateOf<Screen>(Screen.Form) }
                var dormData by remember { mutableStateOf(DormFormData()) }
                var dormBusy by remember { mutableStateOf(false) }
                var dormPdfFile by remember { mutableStateOf<File?>(null) }

                BackHandler(enabled = selectedTemplate != null || screen != Screen.Form) {
                    when {
                        dormPdfFile != null -> dormPdfFile = null
                        selectedTemplate != null && screen != Screen.Form -> {
                            screen = when (screen) {
                                is Screen.Crop -> Screen.Camera
                                is Screen.OcrReview -> Screen.Form
                                else -> Screen.Form
                            }
                        }
                        selectedTemplate != null -> {
                            selectedTemplate = null
                            screen = Screen.Form
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    vm.toast.collect { currentToast = it }
                }

                LaunchedEffect(ui) {
                    when (val u = ui) {
                        is Ui.Done -> {
                            screen = Screen.Preview(u.file)
                            vm.resetUi()
                        }
                        is Ui.Error -> {
                            vm.showToast(u.msg, ToastType.WARNING)
                            vm.resetUi()
                        }
                        else -> Unit
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        when {
                            selectedTemplate == null -> {
                                TemplateSelectScreen(
                                    onSelect = { type ->
                                        selectedTemplate = type
                                        screen = Screen.Form
                                        dormPdfFile = null
                                    }
                                )
                            }
                            selectedTemplate == TemplateType.TRAINING -> {
                                when (val sc = screen) {
                                    Screen.Form -> FormWizard(
                                        vm = vm,
                                        onCamera = { screen = Screen.Camera },
                                        onCropRequest = { rawBmp -> screen = Screen.Crop(rawBmp) },
                                        onSign = { screen = Screen.Sign },
                                        onPreview = { vm.export() },
                                        onExport = { vm.export() }
                                    )
                                    Screen.Camera -> CameraScreen(
                                        onCaptured = { rawBmp -> screen = Screen.Crop(rawBmp) },
                                        onClose = { screen = Screen.Form }
                                    )
                                    is Screen.Crop -> CropScreen(
                                        rawBitmap = sc.rawBitmap,
                                        onCropped = { croppedBmp ->
                                            vm.processCroppedCard(
                                                bmp = croppedBmp,
                                                onOcrSuccess = { extracted ->
                                                    screen = Screen.OcrReview(croppedBmp, extracted)
                                                },
                                                onOcrFallback = { screen = Screen.Form }
                                            )
                                        },
                                        onCancel = { screen = Screen.Form }
                                    )
                                    is Screen.OcrReview -> OcrReviewScreen(
                                        croppedBitmap = sc.croppedBitmap,
                                        initialData = sc.extracted,
                                        onApply = { data, toEmergency ->
                                            vm.applyOcrCard(sc.croppedBitmap, data, toEmergency)
                                            screen = Screen.Form
                                        },
                                        onSkip = {
                                            vm.saveCardDirectly(sc.croppedBitmap)
                                            screen = Screen.Form
                                        }
                                    )
                                    Screen.Sign -> SignaturePad(
                                        onDone = {
                                            vm.saveSignature(it)
                                            screen = Screen.Form
                                        },
                                        onCancel = { screen = Screen.Form }
                                    )
                                    is Screen.Preview -> PreviewScreen(
                                        file = sc.file,
                                        formData = formState,
                                        isStale = (lastExportedHash != null && lastExportedHash != formState.hashCode()),
                                        onRefreshPdf = { vm.export() },
                                        onNavigateToSection = { sectionIndex ->
                                            vm.expandSection(sectionIndex)
                                            screen = Screen.Form
                                        },
                                        onClose = { screen = Screen.Form }
                                    )
                                }
                            }
                            selectedTemplate == TemplateType.DORMITORY -> {
                                if (dormPdfFile != null) {
                                    DormPdfPreview(
                                        file = dormPdfFile!!,
                                        onClose = { dormPdfFile = null },
                                        onBackToForm = { dormPdfFile = null }
                                    )
                                } else {
                                    DormFormScreen(
                                        data = dormData,
                                        onUpdate = { dormData = it },
                                        onBack = { selectedTemplate = null },
                                        onExport = {
                                            scope.launch {
                                                dormBusy = true
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                                        val name = dormData.applicants.firstOrNull { it.name.isNotBlank() }?.name
                                                            ?.replace(Regex("""[^\\p{L}\\p{M}\\p{N}]"""), "")
                                                            ?: "dorm"
                                                        val outDir = File(filesDir, "export").apply { mkdirs() }
                                                        val out = File(outDir, "DORM_${name}_$stamp.pdf")
                                                        DormPdf(this@MainActivity).render(dormData, out)
                                                        out
                                                    }
                                                }.onSuccess {
                                                    dormPdfFile = it
                                                    currentToast = ToastMessage("สร้าง PDF สำเร็จ", ToastType.SUCCESS)
                                                }.onFailure {
                                                    currentToast = ToastMessage(it.message ?: "สร้าง PDF ไม่สำเร็จ", ToastType.WARNING)
                                                }
                                                dormBusy = false
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        ToastHost(
                            message = currentToast,
                            onDismiss = { currentToast = null },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                        )
                    }
                }

                if (ui is Ui.Busy || dormBusy) {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        title = { Text("กำลังประมวลผล...") },
                        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DormPdfPreview(
    file: File,
    onClose: () -> Unit,
    onBackToForm: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "สร้าง PDF สำเร็จ",
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(12.dp))
        Text("ไฟล์: ${file.name}", fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "ตำแหน่ง: ${file.absolutePath}",
            fontSize = 12.sp,
            color = androidx.compose.ui.graphics.Color.Gray
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBackToForm) {
            Text("กลับไปแก้ไข")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onClose) {
            Text("ปิด")
        }
    }
}

sealed interface Screen {
    data object Form : Screen
    data object Camera : Screen
    data class Crop(val rawBitmap: Bitmap) : Screen
    data class OcrReview(val croppedBitmap: Bitmap, val extracted: ExtractedIdCard) : Screen
    data object Sign : Screen
    data class Preview(val file: File) : Screen
}
