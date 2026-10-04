package com.chb.form.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chb.form.data.JsonIo
import com.chb.form.importer.PdfImport
import com.chb.form.model.*
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
    val withBackground: Boolean = true,
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
            val emptyRecord = Record(
                id = UUID.randomUUID().toString().take(8),
                values = report.template.fields.associate { it.key to "" }
            )
            _state.update {
                it.copy(
                    busy = false, template = report.template, report = report,
                    records = listOf(emptyRecord), index = 0, warnings = report.warnings,
                    message = "นำเข้า: ข้อความ ${report.textCount} · เส้น ${report.segCount} · กรอบ ${report.boxCount} · ฟิลด์ ${report.detectedFields}" +
                        if (report.warnings.isNotEmpty()) " · คำเตือน ${report.warnings.size}" else ""
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
    fun setBackground(v: Boolean) = _state.update { it.copy(withBackground = v) }

    fun generate() {
        val st = _state.value
        val tpl = st.template ?: run {
            _state.update { it.copy(message = "ยังไม่มีเทมเพลต") }; return
        }
        val missing = tpl.fields.filter { it.required && st.current.str(it.key).isBlank() }.map { it.label }
        val hardBlock = missing.isNotEmpty() || st.warnings.any { it.level == WarningLevel.ERROR }
        if (hardBlock) {
            _state.update {
                it.copy(message = "ยังสร้างไม่ได้: " + missing.joinToString { "ฟิลด์บังคับว่าง: $it" })
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    val out = File(getApplication<Application>().filesDir, "export/${tpl.name}_$stamp.pdf")
                    TemplateRenderer(getApplication()).render(tpl, st.records, out, st.paper, st.withBackground)
                }.getOrElse { e ->
                    _state.update { it.copy(busy = false, message = "สร้าง PDF ไม่สำเร็จ: ${e.message}") }
                    null
                }
            }
            if (file != null) {
                _state.update {
                    it.copy(busy = false, lastPdf = file,
                        message = "สร้าง PDF สำเร็จ" + if (st.warnings.isNotEmpty()) " (คำเตือน ${st.warnings.size})" else "")
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
