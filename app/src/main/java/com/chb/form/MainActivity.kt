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
 * โฟลว์หลัก: นำเข้า PDF → ตรวจ/เพิ่ม/ลบฟิลด์ → กรอก → สร้าง PDF
 */
class MainActivity : ComponentActivity() {

    private val templateVm: TemplateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChbTheme {
                val tplState by templateVm.state.collectAsStateWithLifecycle()
                var currentToast by remember { mutableStateOf<ToastMessage?>(null) }
                var screen by remember { mutableStateOf("import") }

                BackHandler(enabled = screen == "fill") { screen = "import" }

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
                    tplState.message?.let { msg ->
                        currentToast = ToastMessage(text = msg, type = ToastType.INFO)
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
                                onImage = { templateVm.importImage(it) },
                                onJson = { templateVm.importJson(it) },
                                onContinue = { screen = "fill" }
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
                                        background = tplState.background,
                                        busy = tplState.busy,
                                        onChange = templateVm::set,
                                        onApproveField = { templateVm.approveField(it) },
                                        onApproveAll = templateVm::approveAllFields,
                                        onAddField = templateVm::addField,
                                        onRemoveField = templateVm::removeField,
                                        onRenameField = templateVm::renameField,
                                        onAddRecord = templateVm::addRecord,
                                        onSelectRecord = templateVm::selectRecord,
                                        onPaper = templateVm::setPaper,
                                        onBackground = templateVm::setBackground,
                                        onAttachImage = templateVm::attachBackgroundImage,
                                        onGenerate = {
                                            templateVm.approveAllFields()
                                            templateVm.generate()
                                        },
                                        onBack = { screen = "import" }
                                    )
                                } else {
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
