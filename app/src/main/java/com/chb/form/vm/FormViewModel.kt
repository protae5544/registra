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
