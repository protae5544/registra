package com.chb.form

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chb.form.ocr.ExtractedIdCard
import com.chb.form.ui.CameraScreen
import com.chb.form.ui.CropScreen
import com.chb.form.ui.FormWizard
import com.chb.form.ui.OcrReviewScreen
import com.chb.form.ui.PreviewScreen
import com.chb.form.ui.SignaturePad
import com.chb.form.ui.ToastHost
import com.chb.form.ui.ToastMessage
import com.chb.form.ui.ToastType
import com.chb.form.ui.theme.ChbTheme
import com.chb.form.vm.FormViewModel
import com.chb.form.vm.Ui
import java.io.File

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

                var currentToast by remember { mutableStateOf<ToastMessage?>(null) }
                var screen by remember { mutableStateOf<Screen>(Screen.Form) }

                // การจัดการปุ่มกดย้อนกลับ (Back Handling)
                BackHandler(enabled = screen != Screen.Form) {
                    screen = when (screen) {
                        is Screen.Crop -> Screen.Camera
                        is Screen.OcrReview -> Screen.Form
                        else -> Screen.Form
                    }
                }

                // ดักจับข้อความแจ้งเตือน Toast
                LaunchedEffect(Unit) {
                    vm.toast.collect { currentToast = it }
                }

                // ดักจับสถานะ Ui State เมื่อสร้างเอกสารเสร็จหรือเกิดข้อผิดพลาด
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

                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        when (val sc = screen) {
                            Screen.Form -> FormWizard(
                                vm = vm,
                                onCamera = { screen = Screen.Camera },
                                onCropRequest = { rawBmp -> screen = Screen.Crop(rawBmp) },
                                onSign = { screen = Screen.Sign },
                                onPreview = {
                                    // หากเคยสร้าง PDF ไว้ ให้เปิดไฟล์เดิม หรือสั่งสร้างใหม่
                                    vm.export()
                                },
                                onExport = { vm.export() }
                            )

                            Screen.Camera -> CameraScreen(
                                onCaptured = { rawBmp ->
                                    screen = Screen.Crop(rawBmp)
                                },
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
                                        onOcrFallback = {
                                            screen = Screen.Form
                                        }
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

                        // แสดง Toast Notification สไตล์ Neo-brutalist ด้านบนหน้าจอ
                        ToastHost(
                            message = currentToast,
                            onDismiss = { currentToast = null },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                        )
                    }
                }

                // กล่องแสดงสถานะกำลังสร้างเอกสาร PDF หรือรัน OCR
                if (ui is Ui.Busy) {
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

sealed interface Screen {
    data object Form : Screen
    data object Camera : Screen
    data class Crop(val rawBitmap: Bitmap) : Screen
    data class OcrReview(val croppedBitmap: Bitmap, val extracted: ExtractedIdCard) : Screen
    data object Sign : Screen
    data class Preview(val file: File) : Screen
}
