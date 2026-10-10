package com.chb.form.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chb.form.importer.PdfImport
import com.chb.form.model.FormWarning
import com.chb.form.model.WarningLevel
import java.io.File

private val MinTouch = 56.dp

@Composable
fun ImportScreen(
    report: PdfImport.Report?,
    busy: Boolean,
    warnings: List<FormWarning>,
    onPdf: (Uri) -> Unit,
    onImage: (Uri) -> Unit,
    onJson: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit = {}
) {
    var jsonText by remember { mutableStateOf("") }
    var showJson by remember { mutableStateOf(false) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPdf)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImage)
    }

    val fatalCodes = setOf("open_fail", "copy_fail", "no_pages", "load_fail", "import")
    val hasFatal = warnings.any { it.level == WarningLevel.ERROR && it.code in fatalCodes }
    val canContinue = report != null && report.template.id != "err" && report.template.id != "empty" && !hasFatal

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1) What to do
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("เลือกแบบฟอร์ม", style = MaterialTheme.typography.headlineSmall)
            Text(
                "เลือกไฟล์ PDF หรือรูปถ่ายแบบฟอร์มที่ต้องการกรอก แล้วไปกรอกข้อมูลในขั้นถัดไป",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)
        ) {
            Icon(Icons.Rounded.UploadFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("เลือกไฟล์ PDF")
        }

        OutlinedButton(
            onClick = { pickImage.launch(arrayOf("image/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)
        ) {
            Icon(Icons.Rounded.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ใช้รูปภาพแบบฟอร์ม")
        }

        if (busy) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "กำลังอ่านแบบฟอร์ม...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2) What we found, in plain language
        report?.let { r ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        r.template.name.ifBlank { "แบบฟอร์มที่เลือก" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    r.template.backgroundImagePath?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "ตัวอย่างแบบฟอร์ม",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    val found = r.detectedFields
                    if (found > 0) {
                        Text(
                            "พบช่องกรอก $found ช่อง",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (!hasFatal) {
                        Text(
                            "ยังไม่พบช่องกรอกอัตโนมัติ คุณเพิ่มช่องเองได้ในขั้นถัดไป",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (r.template.backgroundPdfPath != null) {
                        Text(
                            "ใช้ไฟล์ PDF เป็นต้นฉบับ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (r.template.backgroundImagePath != null) {
                        Text(
                            "ใช้รูปภาพเป็นพื้นหลัง",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3) Next step sits right under the result
        Button(
            onClick = onContinue,
            enabled = canContinue && !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch)
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ไปกรอกข้อมูล")
        }

        if (warnings.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ข้อควรระวัง", style = MaterialTheme.typography.titleSmall)
                warnings.forEach { w ->
                    val color = when (w.level) {
                        WarningLevel.ERROR -> MaterialTheme.colorScheme.error
                        WarningLevel.WARN -> MaterialTheme.colorScheme.tertiary
                        WarningLevel.INFO -> MaterialTheme.colorScheme.primary
                    }
                    Text("• ${w.message}", color = color, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider()

        // 4) Advanced option, collapsed by default
        TextButton(
            onClick = { showJson = !showJson },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("นำเข้าข้อมูลจาก JSON (ไม่บังคับ)")
            Spacer(Modifier.width(4.dp))
            Icon(
                if (showJson) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null
            )
        }
        AnimatedVisibility(visible = showJson) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    placeholder = { Text("{ \"ชื่อ\": \"...\" } หรือ { \"records\": [ ... ] }") },
                    maxLines = 8
                )
                if (!canContinue) {
                    Text(
                        "เลือกแบบฟอร์มก่อน จึงจะนำเข้าข้อมูลได้",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { if (jsonText.isNotBlank()) onJson(jsonText) },
                    enabled = canContinue && jsonText.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("นำเข้า JSON")
                }
            }
        }
    }
}
