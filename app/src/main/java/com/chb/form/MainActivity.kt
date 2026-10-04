package com.chb.form

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chb.form.pdf.PdfTools
import com.chb.form.ui.*
import com.chb.form.ui.theme.ChbTheme
import com.chb.form.vm.TemplateViewModel

/**
 * จุดเข้าหลักของแอป: นำเข้า PDF ฟอร์มใดก็ได้ → ตรวจจับฟิลด์ → กรอกข้อมูล → สร้าง PDF
 * ไม่ผูกกับแบบฟอร์มฮาร์ดโค้ด (อบรม / ห้องพัก) อีกต่อไป
 */
class MainActivity : ComponentActivity() {

    private val templateVm: TemplateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChbTheme {
                val tplState by templateVm.state.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()

                var currentToast by remember { mutableStateOf<ToastMessage?>(null) }
                // "import" = หน้าเลือก/วิเคราะห์ PDF, "fill" = หน้ากรอกและสร้าง PDF
                var screen by remember { mutableStateOf("import") }

                BackHandler(enabled = screen == "fill") {
                    screen = "import"
                }

                LaunchedEffect(Unit) {
                    templateVm.fileReady.collect { file ->
                        runCatching { PdfTools.share(this@MainActivity, file) }
                        currentToast = ToastMessage(
                            text = "สร้าง PDF แล้ว — เปิดแชร์",
                            type = ToastType.SUCCESS
                        )
                    }
                }
                LaunchedEffect(tplState.message) {
                    tplState.message?.let {
                        currentToast = ToastMessage(text = it, type = ToastType.INFO)
                        templateVm.clearMessage()
                    }
                }

                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        when (screen) {
                            "import" -> ImportScreen(
                                report = tplState.report,
                                busy = tplState.busy,
                                warnings = tplState.warnings,
                                onPdf = { templateVm.importPdf(it) },
                                onJson = { templateVm.importJson(it) },
                                onContinue = { screen = "fill" },
                                onBack = { /* หน้าหลักแล้ว ไม่มีปุ่มกลับ */ }
                            )
                            else -> {
                                val tpl = tplState.template
                                if (tpl != null) {
                                    DynamicFormScreen(
                                        template = tpl,
                                        record = tplState.current,
                                        recordIndex = tplState.index,
                                        recordCount = tplState.records.size,
                                        warnings = tplState.warnings,
                                        paper = tplState.paper,
                                        withBackground = tplState.withBackground,
                                        busy = tplState.busy,
                                        onChange = templateVm::set,
                                        onApproveField = { templateVm.approveField(it) },
                                        onApproveAll = templateVm::approveAllFields,
                                        onAddRecord = templateVm::addRecord,
                                        onSelectRecord = templateVm::selectRecord,
                                        onPaper = templateVm::setPaper,
                                        onBackground = templateVm::setBackground,
                                        onGenerate = {
                                            templateVm.approveAllFields()
                                            templateVm.generate()
                                        },
                                        onBack = { screen = "import" }
                                    )
                                } else {
                                    // ยังไม่มีเทมเพลต → กลับหน้า import
                                    LaunchedEffect(Unit) { screen = "import" }
                                }
                            }
                        }
                        ToastHost(
                            currentToast,
                            { currentToast = null },
                            Modifier.align(Alignment.TopCenter).statusBarsPadding()
                        )
                    }
                }

                if (tplState.busy) {
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
