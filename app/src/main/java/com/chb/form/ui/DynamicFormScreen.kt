package com.chb.form.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chb.form.model.*
import com.chb.form.pdf.BackgroundKind
import com.chb.form.pdf.PaperSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicFormScreen(
    template: FormTemplate,
    record: Record,
    recordIndex: Int,
    recordCount: Int,
    warnings: List<FormWarning>,
    paper: PaperSize,
    background: BackgroundKind,
    busy: Boolean,
    onChange: (String, String) -> Unit,
    onApproveField: (String) -> Unit,
    onApproveAll: () -> Unit,
    onAddField: (String) -> Unit,
    onRemoveField: (String) -> Unit,
    onRenameField: (String, String) -> Unit,
    onAddRecord: () -> Unit,
    onSelectRecord: (Int) -> Unit,
    onPaper: (PaperSize) -> Unit,
    onBackground: (BackgroundKind) -> Unit,
    onAttachImage: (Uri) -> Unit,
    onGenerate: () -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newFieldLabel by remember { mutableStateOf("") }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAttachImage)
    }
    val hasPdf = !template.backgroundPdfPath.isNullOrBlank()
    val hasImage = !template.backgroundImagePath.isNullOrBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(template.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "ชุดที่ ${recordIndex + 1}/$recordCount · ฟิลด์ ${template.fields.size}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "กลับ") }
                },
                actions = {
                    IconButton(onClick = onAddRecord) { Icon(Icons.Rounded.Add, "เพิ่มชุดข้อมูล") }
                    if (recordCount > 1) {
                        IconButton(
                            onClick = { onSelectRecord(recordIndex - 1) },
                            enabled = recordIndex > 0
                        ) { Text("‹") }
                        IconButton(
                            onClick = { onSelectRecord(recordIndex + 1) },
                            enabled = recordIndex < recordCount - 1
                        ) { Text("›") }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = background == BackgroundKind.PDF,
                            onClick = { onBackground(BackgroundKind.PDF) },
                            enabled = hasPdf,
                            label = { Text("พื้น PDF") }
                        )
                        FilterChip(
                            selected = background == BackgroundKind.IMAGE,
                            onClick = { onBackground(BackgroundKind.IMAGE) },
                            label = { Text("พื้นภาพ") }
                        )
                        FilterChip(
                            selected = background == BackgroundKind.NONE,
                            onClick = { onBackground(BackgroundKind.NONE) },
                            label = { Text("เฉพาะข้อมูล") }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var paperMenu by remember { mutableStateOf(false) }
                        Box {
                            FilterChip(
                                selected = true,
                                onClick = { paperMenu = true },
                                label = { Text(paper.label.take(16)) }
                            )
                            DropdownMenu(expanded = paperMenu, onDismissRequest = { paperMenu = false }) {
                                PaperSize.entries.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.label) },
                                        onClick = { onPaper(p); paperMenu = false }
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { pickImage.launch(arrayOf("image/*")) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Image, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (hasImage) "เปลี่ยนภาพ" else "แนบภาพ")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onApproveAll) { Text("อนุมัติทั้งหมด") }
                    }
                    Button(
                        onClick = onGenerate,
                        enabled = !busy && (template.fields.isNotEmpty() || hasImage || hasPdf),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text("สร้าง PDF")
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    newFieldLabel = ""
                    showAddDialog = true
                },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("เพิ่มฟิลด์") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasImage) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ภาพพื้นหลัง (normalize ตามขนาดที่เลือก)", fontWeight = FontWeight.SemiBold)
                            AsyncImage(
                                model = File(template.backgroundImagePath!!),
                                contentDescription = "พื้นหลัง",
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                when (background) {
                                    BackgroundKind.PDF -> "กำลังใช้ PDF ต้นฉบับเป็นพื้น"
                                    BackgroundKind.IMAGE -> "กำลังใช้ภาพที่แคป/แนบเป็นพื้น"
                                    BackgroundKind.NONE -> "ไม่วาดพื้น — เฉพาะข้อมูลที่กรอก"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

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

            if (template.fields.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("ยังไม่มีฟิลด์", fontWeight = FontWeight.Bold)
                            Text(
                                "ระบบตรวจจับอัตโนมัติไม่พบช่องกรอก — กด \"เพิ่มฟิลด์\" เพื่อกำหนดเอง แล้วกรอกข้อมูลก่อนสร้าง PDF",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(template.fields, key = { it.key }) { f ->
                FieldRow(
                    field = f,
                    value = record.str(f.key),
                    onChange = { onChange(f.key, it) },
                    onApprove = { onApproveField(f.key) },
                    onRemove = { onRemoveField(f.key) },
                    onRename = { onRenameField(f.key, it) }
                )
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("เพิ่มฟิลด์") },
            text = {
                OutlinedTextField(
                    value = newFieldLabel,
                    onValueChange = { newFieldLabel = it },
                    label = { Text("ชื่อฟิลด์") },
                    placeholder = { Text("เช่น ชื่อ-นามสกุล") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddField(newFieldLabel.ifBlank { "ฟิลด์ใหม่" })
                    showAddDialog = false
                }) { Text("เพิ่ม") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("ยกเลิก") }
            }
        )
    }
}

@Composable
private fun FieldRow(
    field: FieldSpec,
    value: String,
    onChange: (String) -> Unit,
    onApprove: () -> Unit,
    onRemove: () -> Unit,
    onRename: (String) -> Unit
) {
    var editingLabel by remember(field.key) { mutableStateOf(false) }
    var labelDraft by remember(field.key) { mutableStateOf(field.label) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editingLabel) {
                    OutlinedTextField(
                        value = labelDraft,
                        onValueChange = { labelDraft = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = {
                                onRename(labelDraft)
                                editingLabel = false
                            }) { Icon(Icons.Rounded.Check, "บันทึกชื่อ") }
                        }
                    )
                } else {
                    Text(
                        field.label.ifBlank { field.key },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        labelDraft = field.label.ifBlank { field.key }
                        editingLabel = true
                    }) { Text("แก้ชื่อ") }
                }
                if (!field.approved) {
                    AssistChip(
                        onClick = onApprove,
                        label = { Text("ยังไม่ตรวจ") },
                        leadingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, "ลบฟิลด์", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "${field.source} · ${field.kind.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            when (field.kind) {
                FieldKind.CHECK, FieldKind.RADIO -> {
                    val checked = value.lowercase() in setOf("true", "1", "yes", "ใช่", "x")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { onChange(it.toString()) })
                        Text(field.label.ifBlank { field.key })
                    }
                }
                else -> OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = field.kind != FieldKind.MULTILINE,
                    isError = field.required && value.isBlank(),
                    placeholder = { Text("กรอก ${field.label.ifBlank { field.key }}") }
                )
            }
        }
    }
}
