package com.chb.form.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chb.form.model.*
import com.chb.form.pdf.PaperSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicFormScreen(
    template: FormTemplate,
    record: Record,
    recordIndex: Int,
    recordCount: Int,
    warnings: List<FormWarning>,
    paper: PaperSize,
    withBackground: Boolean,
    busy: Boolean,
    onChange: (String, String) -> Unit,
    onApproveField: (String) -> Unit,
    onApproveAll: () -> Unit,
    onAddRecord: () -> Unit,
    onSelectRecord: (Int) -> Unit,
    onPaper: (PaperSize) -> Unit,
    onBackground: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(template.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("ชุดที่ ${recordIndex + 1}/$recordCount · ฟิลด์ ${template.fields.size}",
                            style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "กลับ") }
                },
                actions = {
                    IconButton(onClick = onAddRecord) { Icon(Icons.Rounded.Add, "เพิ่มชุด") }
                    if (recordCount > 1) {
                        IconButton(onClick = { onSelectRecord(recordIndex - 1) }, enabled = recordIndex > 0) { Text("‹") }
                        IconButton(onClick = { onSelectRecord(recordIndex + 1) }, enabled = recordIndex < recordCount - 1) { Text("›") }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = withBackground,
                            onClick = { onBackground(!withBackground) },
                            label = { Text(if (withBackground) "วาดพื้นฟอร์ม" else "เฉพาะข้อมูล") }
                        )
                        var paperMenu by remember { mutableStateOf(false) }
                        Box {
                            FilterChip(selected = true, onClick = { paperMenu = true }, label = { Text(paper.label.take(12)) })
                            DropdownMenu(expanded = paperMenu, onDismissRequest = { paperMenu = false }) {
                                PaperSize.entries.forEach { p ->
                                    DropdownMenuItem(text = { Text(p.label) }, onClick = { onPaper(p); paperMenu = false })
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onApproveAll) { Text("อนุมัติฟิลด์ทั้งหมด") }
                    }
                    Button(onClick = onGenerate, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text("สร้าง PDF")
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (warnings.isNotEmpty()) {
                item { Text("คำเตือน", fontWeight = FontWeight.Bold) }
                items(warnings) { w ->
                    val c = when (w.level) {
                        WarningLevel.ERROR -> MaterialTheme.colorScheme.error
                        WarningLevel.WARN -> MaterialTheme.colorScheme.tertiary
                        WarningLevel.INFO -> MaterialTheme.colorScheme.primary
                    }
                    Text("• ${w.message}", color = c, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(template.fields, key = { it.key }) { f ->
                FieldRow(f, record.str(f.key), { onChange(f.key, it) }, { onApproveField(f.key) })
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun FieldRow(field: FieldSpec, value: String, onChange: (String) -> Unit, onApprove: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(field.label.ifBlank { field.key }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            if (!field.approved) {
                AssistChip(onClick = onApprove, label = { Text("ยังไม่ตรวจ") },
                    leadingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) })
            }
            Text(field.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        when (field.kind) {
            FieldKind.CHECK, FieldKind.RADIO -> {
                val checked = value.lowercase() in setOf("true", "1", "yes", "ใช่", "x")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { onChange(it.toString()) })
                    Text(field.label)
                }
            }
            else -> OutlinedTextField(
                value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
                singleLine = field.kind != FieldKind.MULTILINE,
                isError = field.required && value.isBlank(),
                placeholder = { Text(field.key) }
            )
        }
    }
}
