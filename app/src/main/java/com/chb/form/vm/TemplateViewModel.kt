package com.chb.form.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chb.form.data.JsonIo
import com.chb.form.importer.PdfImport
import com.chb.form.model.*
import com.chb.form.pdf.BackgroundCapture
import com.chb.form.pdf.BackgroundKind
import com.chb.form.pdf.PaperSize
import com.chb.form.pdf.TemplateRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class TemplateUiState(
    val busy: Boolean = false,
    val template: FormTemplate? = null,
    val report: PdfImport.Report? = null,
    val records: List<Record> = listOf(Record(id = "1")),
    val index: Int = 0,
    val warnings: List<FormWarning> = emptyList(),
    val message: String? = null,
    val paper: PaperSize = PaperSize.SOURCE,
    val background: BackgroundKind = BackgroundKind.PDF,
    val lastPdf: File? = null
) {
    val current: Record get() = records.getOrElse(index) { Record() }
}

class TemplateViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(TemplateUiState())
    val state = _state.asStateFlow()
    private val _fileReady = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val fileReady = _fileReady.asSharedFlow()

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val report = withContext(Dispatchers.IO) {
                runCatching { PdfImport.import(getApplication(), uri) }.getOrElse { e ->
                    PdfImport.Report(
                        FormTemplate(id = "err", name = "error"), 0, 0, 0, 0, 0,
                        listOf(FormWarning(WarningLevel.ERROR, "import", e.message ?: "import failed"))
                    )
                }
            }
            // กรอง ERROR ที่เป็น fatal เท่านั้น — คำเตือน heuristic ไม่บล็อกการใช้งาน
            val fatal = report.warnings.filter {
                it.level == WarningLevel.ERROR && it.code in setOf("open_fail", "copy_fail", "no_pages", "load_fail", "import")
            }
            val emptyRecord = Record(
                id = UUID.randomUUID().toString().take(8),
                values = report.template.fields.associate { it.key to "" }
            )
            _state.update {
                it.copy(
                    busy = false,
                    template = report.template,
                    report = report,
                    records = listOf(emptyRecord),
                    index = 0,
                    warnings = report.warnings,
                    background = BackgroundKind.PDF,
                    message = if (fatal.isNotEmpty()) {
                        fatal.first().message
                    } else {
                        "นำเข้า: ข้อความ ${report.textCount} · เส้น ${report.segCount} · กรอบ ${report.boxCount} · ฟิลด์ ${report.detectedFields}" +
                            if (report.warnings.isNotEmpty()) " · คำเตือน ${report.warnings.size}" else "" +
                            if (report.detectedFields == 0) " — เพิ่มฟิลด์เองได้ในหน้าถัดไป" else ""
                    }
                )
            }
        }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val paper = _state.value.paper
            val report = withContext(Dispatchers.IO) {
                runCatching { PdfImport.importImage(getApplication(), uri, paper) }.getOrElse { e ->
                    PdfImport.Report(
                        FormTemplate(id = "err", name = "error"), 0, 0, 0, 0, 0,
                        listOf(FormWarning(WarningLevel.ERROR, "import", e.message ?: "import failed"))
                    )
                }
            }
            val fatal = report.warnings.filter {
                it.level == WarningLevel.ERROR && it.code in setOf("open_fail", "copy_fail", "load_fail", "import")
            }
            val emptyRecord = Record(
                id = UUID.randomUUID().toString().take(8),
                values = report.template.fields.associate { it.key to "" }
            )
            _state.update {
                it.copy(
                    busy = false,
                    template = report.template,
                    report = report,
                    records = listOf(emptyRecord),
                    index = 0,
                    warnings = report.warnings,
                    background = BackgroundKind.IMAGE,
                    message = if (fatal.isNotEmpty()) {
                        fatal.first().message
                    } else {
                        "แนบภาพพื้นหลังแล้ว · หน้า ${report.template.pageW.toInt()}×${report.template.pageH.toInt()} pt — เพิ่มฟิลด์เองได้"
                    }
                )
            }
        }
    }

    /** แทนที่ภาพพื้นหลังของเทมเพลตปัจจุบัน โดยคงฟิลด์เดิม */
    fun attachBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            val tpl = _state.value.template ?: run {
                importImage(uri); return@launch
            }
            _state.update { it.copy(busy = true, message = null) }
            val path = withContext(Dispatchers.IO) {
                val bmp = BackgroundCapture.decodeImage(getApplication(), uri) ?: return@withContext null
                val dest = File(getApplication<Application>().filesDir, "templates/${tpl.id}_bg.jpg")
                BackgroundCapture.saveJpeg(bmp, dest)
                if (!bmp.isRecycled) bmp.recycle()
                dest.takeIf { it.exists() }?.absolutePath
            }
            if (path == null) {
                _state.update { it.copy(busy = false, message = "เปิดไฟล์ภาพไม่ได้") }
                return@launch
            }
            _state.update {
                it.copy(
                    busy = false,
                    template = tpl.copy(backgroundImagePath = path),
                    background = BackgroundKind.IMAGE,
                    message = "แนบภาพพื้นหลังแล้ว — จะ normalize ตามขนาดที่เลือกตอนสร้าง PDF"
                )
            }
        }
    }

    fun importJson(raw: String) {
        val tpl = _state.value.template ?: run {
            _state.update { it.copy(message = "ยังไม่มีเทมเพลต — นำเข้า PDF ก่อน") }; return
        }
        val result = JsonIo.parse(raw, tpl.fields)
        val recs = result.records.ifEmpty {
            listOf(Record(id = "1", values = tpl.fields.associate { it.key to "" }))
        }
        _state.update {
            it.copy(
                records = recs, index = 0,
                warnings = (it.warnings + result.warnings).distinctBy { w -> w.code + w.message },
                message = "JSON: จับคู่ ${result.matchedKeys} · ไม่รู้จัก ${result.unknownKeys.size} · ชุด ${recs.size}"
            )
        }
    }

    fun set(key: String, value: String) {
        _state.update { st ->
            val recs = st.records.toMutableList()
            val i = st.index.coerceIn(0, recs.lastIndex.coerceAtLeast(0))
            if (recs.isEmpty()) return@update st
            recs[i] = recs[i].copy(values = recs[i].values + (key to value))
            st.copy(records = recs)
        }
    }

    fun approveField(key: String, approved: Boolean = true) {
        _state.update { st ->
            val tpl = st.template ?: return@update st
            st.copy(template = tpl.copy(fields = tpl.fields.map {
                if (it.key == key) it.copy(approved = approved) else it
            }))
        }
    }

    fun approveAllFields() {
        _state.update { st ->
            val tpl = st.template ?: return@update st
            st.copy(template = tpl.copy(fields = tpl.fields.map { it.copy(approved = true) }))
        }
    }

    /** เพิ่มฟิลด์ใหม่ — ผู้ใช้กำหนดเองเมื่อตรวจจับอัตโนมัติไม่ครบ */
    fun addField(label: String = "ฟิลด์ใหม่") {
        _state.update { st ->
            val tpl = st.template ?: return@update st
            val key = "field_" + UUID.randomUUID().toString().take(6)
            val y = (tpl.fields.maxOfOrNull { it.y + it.h } ?: 40f) + 8f
            val field = FieldSpec(
                key = key,
                label = label.ifBlank { key },
                kind = FieldKind.TEXT,
                x = 40f, y = y.coerceAtMost(tpl.pageH - 24f),
                w = (tpl.pageW * 0.45f).coerceAtLeast(80f),
                h = 18f,
                bounds = Bounds(40f, y.coerceAtMost(tpl.pageH - 24f), (tpl.pageW * 0.45f).coerceAtLeast(80f), 18f),
                fontSize = 12f,
                origin = FieldOrigin.MANUAL,
                source = "manual",
                approved = true,
                required = false
            )
            val newFields = tpl.fields + field
            val recs = st.records.map { r ->
                r.copy(values = r.values + (key to ""))
            }
            st.copy(
                template = tpl.copy(fields = newFields),
                records = recs.ifEmpty { listOf(Record(id = "1", values = mapOf(key to ""))) },
                message = "เพิ่มฟิลด์ \"${field.label}\" แล้ว"
            )
        }
    }

    fun removeField(key: String) {
        _state.update { st ->
            val tpl = st.template ?: return@update st
            val newFields = tpl.fields.filterNot { it.key == key }
            val recs = st.records.map { r ->
                r.copy(values = r.values - key)
            }
            st.copy(
                template = tpl.copy(fields = newFields),
                records = recs,
                message = "ลบฟิลด์แล้ว"
            )
        }
    }

    fun renameField(key: String, newLabel: String) {
        if (newLabel.isBlank()) return
        _state.update { st ->
            val tpl = st.template ?: return@update st
            st.copy(template = tpl.copy(fields = tpl.fields.map {
                if (it.key == key) it.copy(label = newLabel.trim()) else it
            }))
        }
    }

    fun selectRecord(i: Int) = _state.update {
        it.copy(index = i.coerceIn(0, (it.records.size - 1).coerceAtLeast(0)))
    }

    fun addRecord() {
        _state.update { st ->
            val blank = Record(
                id = UUID.randomUUID().toString().take(8),
                values = st.template?.fields?.associate { it.key to "" }.orEmpty()
            )
            st.copy(records = st.records + blank, index = st.records.size)
        }
    }

    fun setPaper(p: PaperSize) = _state.update { it.copy(paper = p) }

    fun setBackground(kind: BackgroundKind) {
        _state.update { it.copy(background = kind) }
        if (kind != BackgroundKind.IMAGE) return
        val tpl = _state.value.template ?: return
        if (!tpl.backgroundImagePath.isNullOrBlank()) return
        val pdfPath = tpl.backgroundPdfPath ?: return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val dest = File(getApplication<Application>().filesDir, "templates/${tpl.id}_bg.jpg")
                BackgroundCapture.capturePdfToFile(pdfPath, dest, tpl.pageW, tpl.pageH)
            }
            if (saved != null) {
                _state.update { cur ->
                    val t = cur.template ?: return@update cur
                    cur.copy(
                        template = t.copy(backgroundImagePath = saved.absolutePath),
                        message = "แคปพื้นหลังเป็นภาพตามขนาดหน้าแล้ว"
                    )
                }
            }
        }
    }

    fun generate() {
        val st = _state.value
        val tpl = st.template ?: run {
            _state.update { it.copy(message = "ยังไม่มีเทมเพลต") }; return
        }
        if (tpl.fields.isEmpty() &&
            tpl.backgroundImagePath.isNullOrBlank() &&
            tpl.backgroundPdfPath.isNullOrBlank()
        ) {
            _state.update { it.copy(message = "ยังไม่มีฟิลด์ — กดเพิ่มฟิลด์ก่อนสร้าง PDF") }; return
        }
        // บล็อกเฉพาะฟิลด์บังคับว่าง — ไม่บล็อกจากคำเตือนนำเข้าอีกต่อไป
        val missing = tpl.fields.filter { it.required && st.current.str(it.key).isBlank() }
            .map { it.label.ifBlank { it.key } }
        if (missing.isNotEmpty()) {
            _state.update {
                it.copy(message = "ฟิลด์บังคับว่าง: " + missing.joinToString())
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    val safeName = tpl.name.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifBlank { "form" }
                    val dir = File(getApplication<Application>().filesDir, "export").apply { mkdirs() }
                    val out = File(dir, "${safeName}_$stamp.pdf")
                    TemplateRenderer(getApplication()).render(tpl, st.records, out, st.paper, st.background)
                }.getOrElse { e ->
                    _state.update { it.copy(busy = false, message = "สร้าง PDF ไม่สำเร็จ: ${e.message}") }
                    null
                }
            }
            if (file != null) {
                _state.update {
                    it.copy(
                        busy = false, lastPdf = file,
                        message = "สร้าง PDF สำเร็จ" + if (st.warnings.isNotEmpty()) " (คำเตือน ${st.warnings.size})" else ""
                    )
                }
                _fileReady.emit(file)
            }
        }
    }

    fun jsonTemplate(): String? {
        val tpl = _state.value.template ?: return null
        return JsonIo.emptyTemplate(tpl.fields)
    }
}
