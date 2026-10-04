package com.chb.form.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chb.form.importer.PdfImport
import com.chb.form.model.FormWarning
import com.chb.form.model.WarningLevel

@Composable
fun ImportScreen(
    report: PdfImport.Report?,
    busy: Boolean,
    warnings: List<FormWarning>,
    onPdf: (Uri) -> Unit,
    onJson: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit = {}
) {
    var jsonText by remember { mutableStateOf("") }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPdf)
    }

    // fatal = เปิด/โหลดไฟล์ล้มเหลวจริง — ไม่รวม heuristic / no_fields
    val fatalCodes = setOf("open_fail", "copy_fail", "no_pages", "load_fail", "import")
    val hasFatal = warnings.any { it.level == WarningLevel.ERROR && it.code in fatalCodes }
    val canContinue = report != null && report.template.id != "err" && report.template.id != "empty" && !hasFatal

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "นำเข้าแบบฟอร์ม PDF",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "เลือกไฟล์ PDF ระบบจะพยายามตรวจจับฟิลด์อัตโนมัติ " +
                "หากไม่ครบ คุณเพิ่ม/ลบฟิลด์เองได้ในหน้าถัดไป",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.UploadFile, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("เลือกไฟล์ PDF")
        }

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("กำลังวิเคราะห์ PDF...")
        }

        report?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(r.template.name.ifBlank { "ฟอร์มที่นำเข้า" }, fontWeight = FontWeight.SemiBold)
                    Text("ข้อความ ${r.textCount} · เส้น ${r.segCount} · กรอบ ${r.boxCount}")
                    Text("AcroForm ${r.acroFields} · ฟิลด์ที่ตรวจได้ ${r.detectedFields}")
                    Text("ขนาดหน้า ${r.template.pageW.toInt()} × ${r.template.pageH.toInt()} pt")
                    if (r.detectedFields == 0 && !hasFatal) {
                        Text(
                            "ยังไม่พบฟิลด์ — กดไปหน้ากรอก แล้วเพิ่มฟิลด์เองได้",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (warnings.isNotEmpty()) {
            Text("คำเตือน", fontWeight = FontWeight.Bold)
            warnings.forEach { w ->
                val color = when (w.level) {
                    WarningLevel.ERROR -> MaterialTheme.colorScheme.error
                    WarningLevel.WARN -> MaterialTheme.colorScheme.tertiary
                    WarningLevel.INFO -> MaterialTheme.colorScheme.primary
                }
                Text("• ${w.message}", color = color, style = MaterialTheme.typography.bodySmall)
            }
        }

        HorizontalDivider()

        Text("นำเข้าข้อมูล JSON (ไม่บังคับ)", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = jsonText,
            onValueChange = { jsonText = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            placeholder = { Text("{ \"ชื่อ\": \"...\" } หรือ { \"records\": [ ... ] }") },
            maxLines = 8
        )
        OutlinedButton(
            onClick = { if (jsonText.isNotBlank()) onJson(jsonText) },
            enabled = canContinue && jsonText.isNotBlank()
        ) {
            Text("นำเข้า JSON")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ไปหน้ากรอก / เพิ่ม-ลบฟิลด์")
        }
    }
}
