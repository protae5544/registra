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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chb.form.data.DormFormData
import com.chb.form.data.TemplateType
import com.chb.form.model.BuiltinTemplates
import com.chb.form.model.FormWarning
import com.chb.form.model.WarningLevel
import com.chb.form.ocr.ExtractedIdCard
import com.chb.form.pdf.DormPdf
import com.chb.form.ui.*
import com.chb.form.ui.ToastMessage
import com.chb.form.ui.ToastType
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
                var dormReview by remember { mutableStateOf(false) }
                var dormWarnings by remember { mutableStateOf<List<FormWarning>>(emptyList()) }

                BackHandler(enabled = selectedTemplate != null || screen != Screen.Form || dormReview) {
                    when {
                        dormReview -> dormReview = false
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

                fun buildDormWarnings(d: DormFormData): List<FormWarning> {
                    val w = mutableListOf<FormWarning>()
                    if (d.applicants.none { it.name.isNotBlank() }) {
                        w += FormWarning(WarningLevel.ERROR, "no_applicant", "ต้องมีชื่อผู้ขออย่างน้อย 1 คน")
                    }
                    if (d.affiliationType == DormFormData.AffiliationType.NONE) {
                        w += FormWarning(WarningLevel.WARN, "no_aff", "ยังไม่ได้เลือกสังกัดหน่วยงาน")
                    }
                    if (d.startDate.isBlank() || d.endDate.isBlank()) {
                        w += FormWarning(WarningLevel.WARN, "no_dates", "ยังไม่มีวันที่เริ่ม/สิ้นสุดการพัก")
                    }
                    if (d.guarantorName.isBlank()) {
                        w += FormWarning(WarningLevel.INFO, "no_guarantor", "ยังไม่มีชื่อผู้ค้ำ/ผู้รับรอง")
                    }
                    return w
                }

                fun dormToMap(d: DormFormData): Map<String, String> = mapOf(
                    "applicant1_name" to d.applicants.getOrNull(0)?.name.orEmpty(),
                    "applicant1_position" to d.applicants.getOrNull(0)?.position.orEmpty(),
                    "applicant2_name" to d.applicants.getOrNull(1)?.name.orEmpty(),
                    "applicant2_position" to d.applicants.getOrNull(1)?.position.orEmpty(),
                    "applicant3_name" to d.applicants.getOrNull(2)?.name.orEmpty(),
                    "applicant3_position" to d.applicants.getOrNull(2)?.position.orEmpty(),
                    "affiliation" to d.affiliationType.name,
                    "head_team_cn" to d.headOfTeamCn,
                    "foreman_cn" to d.foremanCn,
                    "head_team_contractor" to d.headOfTeamContractor,
                    "supervisor" to d.supervisor,
                    "contract_maker" to d.contractMaker,
                    "other_affiliation" to d.otherAffiliation,
                    "days" to d.days,
                    "start_date" to d.startDate,
                    "end_date" to d.endDate,
                    "guarantor_name" to d.guarantorName,
                    "guarantor_position" to d.guarantorPosition,
                    "guarantor_company" to d.guarantorCompany,
                    "sequence_no" to d.sequenceNo,
                    "document_date" to d.documentDate
                )

                fun mapToDorm(m: Map<String, String>, base: DormFormData): DormFormData {
                    fun s(k: String) = m[k].orEmpty()
                    val aff = runCatching {
                        DormFormData.AffiliationType.valueOf(s("affiliation").ifBlank { "NONE" })
                    }.getOrDefault(DormFormData.AffiliationType.NONE)
                    return base.copy(
                        applicants = listOf(
                            DormFormData.Applicant(s("applicant1_name"), s("applicant1_position")),
                            DormFormData.Applicant(s("applicant2_name"), s("applicant2_position")),
                            DormFormData.Applicant(s("applicant3_name"), s("applicant3_position"))
                        ),
                        affiliationType = aff,
                        headOfTeamCn = s("head_team_cn"),
                        foremanCn = s("foreman_cn"),
                        headOfTeamContractor = s("head_team_contractor"),
                        supervisor = s("supervisor"),
                        contractMaker = s("contract_maker"),
                        otherAffiliation = s("other_affiliation"),
                        days = s("days"),
                        startDate = s("start_date"),
                        endDate = s("end_date"),
                        guarantorName = s("guarantor_name"),
                        guarantorPosition = s("guarantor_position"),
                        guarantorCompany = s("guarantor_company"),
                        sequenceNo = s("sequence_no"),
                        documentDate = s("document_date")
                    )
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
                                        dormReview = false
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
                                when {
                                    dormPdfFile != null -> {
                                        DormPdfResultScreen(
                                            file = dormPdfFile!!,
                                            onBackToForm = { dormPdfFile = null },
                                            onClose = { dormPdfFile = null }
                                        )
                                    }
                                    dormReview -> {
                                        FinalReviewScreen(
                                            template = BuiltinTemplates.dormitory(),
                                            initial = dormToMap(dormData),
                                            warnings = dormWarnings,
                                            onBack = { dormReview = false },
                                            onConfirm = { edited ->
                                                dormData = mapToDorm(edited, dormData)
                                                dormReview = false
                                                scope.launch {
                                                    dormBusy = true
                                                    runCatching {
                                                        withContext(Dispatchers.IO) {
                                                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                                            val name = dormData.applicants.firstOrNull { it.name.isNotBlank() }?.name
                                                                ?.filter { it.isLetterOrDigit() || it.isWhitespace() }
                                                                ?.trim()
                                                                ?.replace(" ", "_")
                                                                ?: "dorm"
                                                            val outDir = File(filesDir, "export").apply { mkdirs() }
                                                            val out = File(outDir, "DORM_${name}_$stamp.pdf")
                                                            DormPdf(this@MainActivity).render(dormData, out)
                                                            out
                                                        }
                                                    }.onSuccess {
                                                        dormPdfFile = it
                                                        currentToast = ToastMessage(
                                                            text = if (dormWarnings.isEmpty()) "สร้าง PDF สำเร็จ"
                                                            else "สร้าง PDF สำเร็จ (มีคำเตือน ${dormWarnings.size} รายการ)",
                                                            type = if (dormWarnings.any { w -> w.level == WarningLevel.ERROR })
                                                                ToastType.WARNING else ToastType.SUCCESS
                                                        )
                                                    }.onFailure {
                                                        currentToast = ToastMessage(
                                                            text = it.message ?: "สร้าง PDF ไม่สำเร็จ",
                                                            type = ToastType.WARNING
                                                        )
                                                    }
                                                    dormBusy = false
                                                }
                                            }
                                        )
                                    }
                                    else -> {
                                        DormFormScreen(
                                            data = dormData,
                                            onUpdate = { dormData = it },
                                            onBack = { selectedTemplate = null },
                                            onExport = {
                                                dormWarnings = buildDormWarnings(dormData)
                                                dormReview = true
                                            }
                                        )
                                    }
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

sealed interface Screen {
    data object Form : Screen
    data object Camera : Screen
    data class Crop(val rawBitmap: Bitmap) : Screen
    data class OcrReview(val croppedBitmap: Bitmap, val extracted: ExtractedIdCard) : Screen
    data object Sign : Screen
    data class Preview(val file: File) : Screen
}
