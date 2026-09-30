package com.chb.form.vm

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chb.form.data.*
import com.chb.form.ocr.ExtractedIdCard
import com.chb.form.ocr.ThaiOcrParser
import com.chb.form.pdf.FormPdf
import com.chb.form.ui.ToastMessage
import com.chb.form.ui.ToastType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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

    private val _employerProfile = MutableStateFlow(EmployerProfile())
    val employerProfile: StateFlow<EmployerProfile> = _employerProfile.asStateFlow()

    private val _personalProfile = MutableStateFlow(PersonalProfile())
    val personalProfile: StateFlow<PersonalProfile> = _personalProfile.asStateFlow()

    // จดจำสถานะการเปิด/ปิด Accordion ของแต่ละหมวด (0..4) เพื่อคงสถานะเมื่อสลับหน้าจอ
    private val _expandedSections = MutableStateFlow<Set<Int>>(setOf(0, 1, 2, 3, 4))
    val expandedSections: StateFlow<Set<Int>> = _expandedSections.asStateFlow()

    private val _toast = MutableSharedFlow<ToastMessage>(extraBufferCapacity = 8)
    val toast: SharedFlow<ToastMessage> = _toast.asSharedFlow()

    // เก็บ Hash ของแบบฟอร์มตอนที่สร้าง PDF ล่าสุด เพื่อเช็คว่าแบบฟอร์มเปลี่ยนแปลงไปแล้วหรือไม่ (Staleness Check)
    private val _lastExportedHash = MutableStateFlow<Int?>(null)
    val lastExportedHash: StateFlow<Int?> = _lastExportedHash.asStateFlow()

    private var saveJob: Job? = null
    private var lastToastTime = 0L

    init {
        viewModelScope.launch {
            _state.value = FormStore.load(ctx)
            _employerProfile.value = ProfileStore.loadEmployer(ctx)
            _personalProfile.value = ProfileStore.loadPersonal(ctx)
        }
    }

    // ---------- การจัดการหมวด Accordion ----------
    fun toggleSection(index: Int) {
        _expandedSections.update { current ->
            if (current.contains(index)) current - index else current + index
        }
    }

    fun expandSection(index: Int) {
        _expandedSections.update { it + index }
    }

    // ---------- การแก้ไขข้อมูลฟอร์ม ----------
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

    // ---------- การแจ้งเตือน Toast พร้อมการหน่วงเวลาป้องกันข้อความชนกัน ----------
    fun showToast(msg: String, type: ToastType = ToastType.INFO) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val elapsed = now - lastToastTime
            if (elapsed < 400) {
                delay(400 - elapsed)
            }
            lastToastTime = System.currentTimeMillis()
            _toast.emit(ToastMessage(text = msg, type = type))
        }
    }

    // ---------- นำเข้ารูปจากแกลเลอรี ----------
    fun importFromGallery(context: Context, uri: Uri, onCropReady: (Bitmap) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = decodeUriWithOrientation(context, uri)
            withContext(Dispatchers.Main) {
                if (bmp != null) {
                    onCropReady(bmp)
                } else {
                    showToast("ไม่สามารถเปิดไฟล์รูปภาพได้", ToastType.WARNING)
                }
            }
        }
    }

    // ---------- บันทึกรูปบัตรลง Internal Storage & สแกน OCR ----------
    fun processCroppedCard(
        bmp: Bitmap,
        onOcrSuccess: (ExtractedIdCard) -> Unit,
        onOcrFallback: () -> Unit
    ) {
        viewModelScope.launch {
            _ui.value = Ui.Busy
            val extracted = withContext(Dispatchers.IO) {
                runCatching {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val result = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
                    ThaiOcrParser.parse(result.text)
                }.getOrNull()
            }
            _ui.value = Ui.Idle

            if (extracted != null && extracted.hasUsefulData) {
                onOcrSuccess(extracted)
            } else {
                // บันทึกรูปบัตรทันทีแม้ไม่พบข้อมูลตัวอักษร
                saveCardDirectly(bmp)
                showToast("บันทึกรูปบัตรแล้ว (ไม่พบข้อความจากบัตรที่ชัดเจน)", ToastType.INFO)
                onOcrFallback()
            }
        }
    }

    fun applyOcrCard(
        bmp: Bitmap,
        extracted: ExtractedIdCard,
        applyToEmergency: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // บันทึกลง Internal Storage (filesDir แทน cacheDir ป้องกัน OS ลบไฟล์)
            val dir = File(ctx.filesDir, "drafts/img").apply { mkdirs() }
            val f = File(dir, "card_${System.currentTimeMillis()}.jpg")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }

            _state.update { s ->
                val newFields = s.fields.toMutableList()

                if (applyToEmergency) {
                    if (extracted.nameThai.isNotBlank()) newFields[F.EMG_NAME] = extracted.nameThai
                    if (extracted.phone.isNotBlank()) newFields[F.EMG_TEL] = extracted.phone
                } else {
                    if (extracted.bloodType.isNotBlank() && newFields[F.BLOOD].isBlank()) {
                        newFields[F.BLOOD] = extracted.bloodType
                    }
                    if (extracted.phone.isNotBlank() && newFields[F.TEL].isBlank()) {
                        newFields[F.TEL] = extracted.phone
                    }
                    if (extracted.position.isNotBlank() && newFields[F.POSITION].isBlank()) {
                        newFields[F.POSITION] = extracted.position
                    }
                    // หากช่องผู้ติดต่อฉุกเฉินยังว่าง เติมเป็นตัวเลือกสำรอง
                    if (extracted.nameThai.isNotBlank() && newFields[F.EMG_NAME].isBlank()) {
                        newFields[F.EMG_NAME] = extracted.nameThai
                    }
                }

                s.copy(cardPath = f.absolutePath, fields = newFields)
            }

            FormStore.save(ctx, _state.value)
            showToast("นำเข้าข้อมูลจากบัตรเรียบร้อยแล้ว", ToastType.SUCCESS)
        }
    }

    fun saveCardDirectly(bmp: Bitmap) = viewModelScope.launch(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "drafts/img").apply { mkdirs() }
        val f = File(dir, "card_${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        _state.update { it.copy(cardPath = f.absolutePath) }
        FormStore.save(ctx, _state.value)
    }

    fun clearCard() = update { it.copy(cardPath = null) }

    // ---------- บันทึกลายเซ็นลง Internal Storage ----------
    fun saveSignature(bmp: Bitmap) = viewModelScope.launch(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "drafts/img").apply { mkdirs() }
        val f = File(dir, "sign_${System.currentTimeMillis()}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        _state.update { it.copy(signaturePath = f.absolutePath) }
        FormStore.save(ctx, _state.value)
        showToast("บันทึกลายเซ็นเรียบร้อยแล้ว", ToastType.SUCCESS)
    }

    fun clearSignature() = update { it.copy(signaturePath = null) }

    // ---------- ระบบโปรไฟล์ & Autofill (เติมเฉพาะช่องที่ยังว่าง) ----------
    fun saveCurrentAsEmployerProfile() = viewModelScope.launch {
        val s = _state.value
        val currentArea = Content.AREA_IDS.firstOrNull { s.check(it) }
        val currentCourses = Content.COURSES.map { it.idx }.filter { s.check(it) }

        val p = EmployerProfile(
            company = s.field(F.COMPANY),
            foreman = s.field(F.FOREMAN),
            leader = s.field(F.LEADER),
            defaultAreaIdx = currentArea,
            defaultCourses = currentCourses
        )
        ProfileStore.saveEmployer(ctx, p)
        _employerProfile.value = p
        showToast("บันทึกเป็นโปรไฟล์นายจ้างเรียบร้อยแล้ว", ToastType.SUCCESS)
    }

    fun saveCurrentAsPersonalProfile() = viewModelScope.launch {
        val s = _state.value
        val currentExp = Content.EXPERIENCE_IDS.firstOrNull { s.check(it) }
        val currentPrev = Content.PREVIOUS_IDS.firstOrNull { s.check(it) }

        val p = PersonalProfile(
            name = s.field(F.EMG_NAME),
            tel = s.field(F.TEL),
            blood = s.field(F.BLOOD),
            position = s.field(F.POSITION),
            emgName = s.field(F.EMG_NAME),
            emgRel = s.field(F.EMG_REL),
            emgTel = s.field(F.EMG_TEL),
            expIdx = currentExp,
            expDuration = s.field(F.EXP_DURATION),
            prevIdx = currentPrev
        )
        ProfileStore.savePersonal(ctx, p)
        _personalProfile.value = p
        showToast("บันทึกเป็นโปรไฟล์ส่วนตัวเรียบร้อยแล้ว", ToastType.SUCCESS)
    }

    fun autofillEmployer() {
        val p = _employerProfile.value
        if (!p.isConfigured) {
            showToast("ยังไม่มีข้อมูลโปรไฟล์นายจ้างที่บันทึกไว้", ToastType.WARNING)
            return
        }
        update { s ->
            val newFields = s.fields.toMutableList()
            if (newFields[F.COMPANY].isBlank()) newFields[F.COMPANY] = p.company
            if (newFields[F.FOREMAN].isBlank()) newFields[F.FOREMAN] = p.foreman
            if (newFields[F.LEADER].isBlank()) newFields[F.LEADER] = p.leader

            val newChecks = s.checks.toMutableList()
            if (p.defaultAreaIdx != null && Content.AREA_IDS.none { s.check(it) }) {
                Content.AREA_IDS.forEach { newChecks[it] = (it == p.defaultAreaIdx) }
            }
            if (p.defaultCourses.isNotEmpty() && Content.COURSES.none { s.check(it.idx) }) {
                p.defaultCourses.forEach { newChecks[it] = true }
            }
            s.copy(fields = newFields, checks = newChecks)
        }
        showToast("เติมข้อมูลโปรไฟล์นายจ้างแล้ว", ToastType.SUCCESS)
    }

    fun autofillPersonal() {
        val p = _personalProfile.value
        if (!p.isConfigured) {
            showToast("ยังไม่มีข้อมูลโปรไฟล์ส่วนตัวที่บันทึกไว้", ToastType.WARNING)
            return
        }
        update { s ->
            val newFields = s.fields.toMutableList()
            if (newFields[F.TEL].isBlank()) newFields[F.TEL] = p.tel
            if (newFields[F.BLOOD].isBlank()) newFields[F.BLOOD] = p.blood
            if (newFields[F.POSITION].isBlank()) newFields[F.POSITION] = p.position
            if (newFields[F.EMG_NAME].isBlank()) newFields[F.EMG_NAME] = p.emgName
            if (newFields[F.EMG_REL].isBlank()) newFields[F.EMG_REL] = p.emgRel
            if (newFields[F.EMG_TEL].isBlank()) newFields[F.EMG_TEL] = p.emgTel
            if (newFields[F.EXP_DURATION].isBlank()) newFields[F.EXP_DURATION] = p.expDuration

            val newChecks = s.checks.toMutableList()
            if (p.expIdx != null && Content.EXPERIENCE_IDS.none { s.check(it) }) {
                Content.EXPERIENCE_IDS.forEach { newChecks[it] = (it == p.expIdx) }
            }
            if (p.prevIdx != null && Content.PREVIOUS_IDS.none { s.check(it) }) {
                Content.PREVIOUS_IDS.forEach { newChecks[it] = (it == p.prevIdx) }
            }
            s.copy(fields = newFields, checks = newChecks)
        }
        showToast("เติมข้อมูลโปรไฟล์ส่วนตัวแล้ว", ToastType.SUCCESS)
    }

    fun autofillAll() {
        autofillEmployer()
        autofillPersonal()
    }

    // ---------- สร้าง PDF & ตรวจสอบความถูกต้อง ----------
    fun export() {
        if (_ui.value is Ui.Busy) return
        viewModelScope.launch {
            _ui.value = Ui.Busy
            runCatching {
                withContext(Dispatchers.IO) {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    val name = _state.value.field(F.EMG_NAME)
                        .ifBlank { "form" }
                        .replace(Regex("""[^\p{L}\p{M}\p{N}]"""), "")
                    val outDir = File(ctx.filesDir, "export").apply { mkdirs() }
                    val out = File(outDir, "CHB_${name}_$stamp.pdf")
                    FormPdf(ctx).render(_state.value, out)
                }
            }
                .onSuccess {
                    _lastExportedHash.value = _state.value.hashCode()
                    _ui.value = Ui.Done(it)
                }
                .onFailure {
                    _ui.value = Ui.Error(it.message ?: "สร้างไฟล์ PDF ไม่สำเร็จ")
                }
        }
    }

    fun resetUi() {
        _ui.value = Ui.Idle
    }

    fun reset() = viewModelScope.launch {
        FormStore.clear(ctx)
        _state.value = FormData()
        _lastExportedHash.value = null
        showToast("ล้างข้อมูลฟอร์มเรียบร้อยแล้ว", ToastType.INFO)
    }

    private fun decodeUriWithOrientation(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            var orientation = ExifInterface.ORIENTATION_NORMAL
            context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

            val maxDim = 1920
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

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
}
