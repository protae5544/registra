package com.chb.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chb.form.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalReviewScreen(
    template: FormTemplate,
    initial: Map<String, String>,
    warnings: List<FormWarning>,
    onBack: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    val values = remember(initial) { mutableStateMapOf<String, String>().apply { putAll(initial) } }
    val gate = remember(values.toMap(), warnings) {
        val missing = template.fields.filter { it.required && values[it.key].isNullOrBlank() }.map { it.label }
        Gate.evaluate(
            CommitTarget.GENERATE_OUTPUT,
            GateState(
                missingRequired = missing,
                warnings = warnings,
                recordCount = 1,
                templateApproved = true
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ตรวจก่อนสร้างเอกสาร", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "กลับ")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.padding(12.dp)) {
                    if (!gate.allowed) {
                        Text(
                            "ยังสร้างไม่ได้: ${gate.reasons.joinToString(" · ")}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { onConfirm(values.toMap()) },
                        enabled = gate.allowed,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("ยืนยันและสร้างเอกสาร")
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (warnings.isNotEmpty()) {
                item {
                    Text("คำเตือน", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                items(warnings) { w ->
                    WarningCard(w)
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("แก้ไขข้อมูล", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "แก้ค่าได้ที่นี่ก่อนสร้างเอกสาร — ระบบจะไม่สร้างถ้าฟิลด์บังคับยังว่าง",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(template.fields.filter { it.kind != FieldKind.IMAGE && it.kind != FieldKind.SIGNATURE }) { f ->
                when (f.kind) {
                    FieldKind.CHECK, FieldKind.RADIO -> {
                        val checked = values[f.key]?.lowercase() in setOf("true", "1", "yes", "ใช่", "x")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { values[f.key] = it.toString() }
                            )
                            Text(f.label)
                            if (f.required) Text(" *", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    else -> {
                        OutlinedTextField(
                            value = values[f.key].orEmpty(),
                            onValueChange = { values[f.key] = it },
                            label = {
                                Text(buildString {
                                    append(f.label)
                                    if (f.required) append(" *")
                                })
                            },
                            singleLine = f.kind != FieldKind.MULTILINE,
                            modifier = Modifier.fillMaxWidth(),
                            isError = f.required && values[f.key].isNullOrBlank()
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun WarningCard(w: FormWarning) {
    val bg = when (w.level) {
        WarningLevel.ERROR -> Color(0xFFFFEBEE)
        WarningLevel.WARN -> Color(0xFFFFF8E1)
        WarningLevel.INFO -> Color(0xFFE3F2FD)
    }
    val fg = when (w.level) {
        WarningLevel.ERROR -> Color(0xFFB71C1C)
        WarningLevel.WARN -> Color(0xFFF57F17)
        WarningLevel.INFO -> Color(0xFF1565C0)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Rounded.Warning, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(w.message, color = fg, style = MaterialTheme.typography.bodySmall)
            if (w.fieldKey != null) {
                Text("ฟิลด์: ${w.fieldKey}", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
            }
        }
    }
}
