package com.chb.form.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.chb.form.data.*
import com.chb.form.ui.theme.*
import com.chb.form.vm.FormViewModel
import java.io.File

/**
 * หน้าจอหลักแบบฟอร์มลงทะเบียน (Accordion-Style Form Wizard)
 * จัดกลุ่ม 5 หมวดพับ-ขยายได้ พร้อมระบบเติมข้อมูลโปรไฟล์อัตโนมัติ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormWizard(
    vm: FormViewModel,
    onCamera: () -> Unit,
    onCropRequest: (Bitmap) -> Unit,
    onSign: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit
) {
    val ctx = LocalContext.current
    val s by vm.state.collectAsStateWithLifecycle()
    val employerProfile by vm.employerProfile.collectAsStateWithLifecycle()
    val personalProfile by vm.personalProfile.collectAsStateWithLifecycle()
    val expandedSections by vm.expandedSections.collectAsStateWithLifecycle()

    var showResetDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    // ตัวเลือกรูปจากแกลเลอรี
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            vm.importFromGallery(ctx, uri, onCropReady = onCropRequest)
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CreamBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "แบบฟอร์มลงทะเบียน CHB",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NeoBlack
                        )
                        Text(
                            "กรอกข้อมูลให้ครบถ้วนเพื่อสร้างเอกสารสมัคร",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeoMutedText
                        )
                    }

                    Row {
                        IconButton(onClick = { showProfileDialog = true }) {
                            Icon(Icons.Rounded.AccountCircle, "โปรไฟล์", tint = PistachioDark)
                        }
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(Icons.Rounded.RestartAlt, "ล้างฟอร์ม", tint = NeoMutedText)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // แถบความคืบหน้าแบบแบ่ง 5 เซกเมนต์
                NeoSegmentedProgress(
                    progress = s.progress,
                    totalSegments = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        bottomBar = {
            Surface(
                color = CreamSurface,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NeoBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ปุ่มดูตัวอย่างสด
                    OutlinedButton(
                        onClick = onPreview,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Rounded.Visibility, null, tint = NeoBlack, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ดูตัวอย่าง", color = NeoBlack, fontWeight = FontWeight.Bold)
                    }

                    // ปุ่มส่งออก PDF
                    NeoButton(
                        text = "สร้าง PDF",
                        icon = Icons.Rounded.PictureAsPdf,
                        onClick = onExport,
                        modifier = Modifier.weight(1.4f)
                    )
                }
            }
        },
        containerColor = CreamBg
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // แถบแจ้งเตือนข้อมูลโปรไฟล์ (Autofill Prompt)
            if (employerProfile.isConfigured || personalProfile.isConfigured) {
                NeoCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    containerColor = PistachioContainer,
                    borderColor = PistachioDark,
                    shadowOffset = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Bolt, null, tint = PistachioDark, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "มีข้อมูลโปรไฟล์ที่บันทึกไว้",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PistachioOnContainer
                            )
                            Text(
                                "แตะเพื่อเติมข้อมูลเข้าช่องว่างทันที",
                                fontSize = 12.sp,
                                color = NeoBlack.copy(alpha = 0.8f)
                            )
                        }
                        TextButton(
                            onClick = { vm.autofillAll() },
                            colors = ButtonDefaults.textButtonColors(contentColor = PistachioDark)
                        ) {
                            Text("เติมข้อมูลทันที", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ==========================================
            // หมวด 1: พื้นที่ปฏิบัติงาน & หลักสูตร
            // ==========================================
            NeoSectionCard(
                sectionNumber = 1,
                title = "พื้นที่ปฏิบัติงาน & หลักสูตร",
                subtitle = "เลือก 1 พื้นที่ และหลักสูตรที่ต้องการอบรม",
                isComplete = s.isAreaCourseComplete,
                isExpanded = expandedSections.contains(0),
                onToggle = { vm.toggleSection(0) }
            ) {
                Text("พื้นที่ปฏิบัติงาน (เลือกได้ 1 พื้นที่)", fontWeight = FontWeight.Bold, color = NeoBlack)
                Spacer(Modifier.height(6.dp))
                Content.AREAS.forEach { o ->
                    NeoChoiceRow(
                        title = o.title,
                        subtitle = o.note,
                        selected = s.check(o.idx),
                        isRadio = true,
                        onClick = { vm.pickOne(Content.AREA_IDS, o.idx) }
                    )
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(8.dp))
                Text("หลักสูตรเฉพาะที่ต้องการอบรม (เลือกได้หลายข้อ)", fontWeight = FontWeight.Bold, color = NeoBlack)
                Spacer(Modifier.height(6.dp))
                Content.COURSES.forEach { o ->
                    NeoChoiceRow(
                        title = o.title,
                        subtitle = o.note,
                        selected = s.check(o.idx),
                        isRadio = false,
                        onClick = { vm.toggle(o.idx) }
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (s.check(Content.COURSE_OTHER_BOX)) {
                    Spacer(Modifier.height(6.dp))
                    NeoTextField(
                        value = s.field(F.COURSE_OTHER),
                        onValueChange = { vm.setField(F.COURSE_OTHER, it) },
                        label = "ระบุหลักสูตรอื่นๆ เพิ่มเติม",
                        leadingIcon = Icons.Rounded.EditNote
                    )
                }
            }

            // ==========================================
            // หมวด 2: บัตรประชาชน & OCR
            // ==========================================
            NeoSectionCard(
                sectionNumber = 2,
                title = "บัตรประชาชน & สแกนข้อความ",
                subtitle = "ถ่ายรูปหรือเลือกรูปบัตรเพื่ออ่านชื่ออัตโนมัติ",
                isComplete = s.isCardComplete,
                isExpanded = expandedSections.contains(1),
                onToggle = { vm.toggleSection(1) }
            ) {
                if (s.cardPath != null) {
                    // กรอบแสดงรูปบัตรปัจจุบัน
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(85.6f / 54f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.5.dp, NeoBorder, RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = File(s.cardPath!!),
                            contentDescription = "รูปบัตร",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCamera,
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ถ่ายใหม่")
                        }

                        Button(
                            onClick = { vm.clearCard() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeoError),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Rounded.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ลบรูป", color = Color.White)
                        }
                    }
                } else {
                    // ปุ่มถ่ายหรือเลือกจากแกลเลอรี
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NeoButton(
                            text = "ถ่ายรูปบัตร",
                            icon = Icons.Rounded.PhotoCamera,
                            onClick = onCamera,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeoBorder),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, null, tint = NeoBlack)
                            Spacer(Modifier.width(6.dp))
                            Text("แกลเลอรี", color = NeoBlack, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "คำแนะนำ: วางบัตรให้สว่าง ไม่มีแสงสะท้อนทับตัวหนังสือ",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeoMutedText
                    )
                }
            }

            // ==========================================
            // หมวด 3: ข้อมูลทั่วไปและประวัติการทำงาน
            // ==========================================
            NeoSectionCard(
                sectionNumber = 3,
                title = "ข้อมูลทั่วไป & ประวัติการทำงาน",
                subtitle = "บริษัท, เบอร์โทร, กรุ๊ปเลือด และตำแหน่ง",
                isComplete = s.isInfoComplete,
                isExpanded = expandedSections.contains(2),
                onToggle = { vm.toggleSection(2) }
            ) {
                NeoTextField(
                    value = s.field(F.COMPANY),
                    onValueChange = { vm.setField(F.COMPANY, it) },
                    label = "1. บริษัท / Company",
                    leadingIcon = Icons.Rounded.Business
                )
                Spacer(Modifier.height(10.dp))

                NeoTextField(
                    value = s.field(F.TEL),
                    onValueChange = { vm.setField(F.TEL, it.filter(Char::isDigit).take(10)) },
                    label = "2. เบอร์โทร / Tel",
                    leadingIcon = Icons.Rounded.Phone,
                    keyboardType = KeyboardType.Phone,
                    errorMessage = if (s.field(F.TEL).isNotEmpty() && s.field(F.TEL).length < 9) "เบอร์โทรศัพท์ต้องมีอย่างน้อย 9-10 หลัก" else null
                )
                Spacer(Modifier.height(10.dp))

                Text("3. กรุ๊ปเลือด / Blood group", fontWeight = FontWeight.SemiBold, color = NeoBlack)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Content.BLOOD_GROUPS.forEach { bg ->
                        NeoChip(
                            label = bg,
                            selected = s.field(F.BLOOD) == bg,
                            onClick = { vm.setField(F.BLOOD, if (s.field(F.BLOOD) == bg) "" else bg) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                NeoTextField(
                    value = s.field(F.POSITION),
                    onValueChange = { vm.setField(F.POSITION, it) },
                    label = "4. ตำแหน่ง / Position",
                    leadingIcon = Icons.Rounded.Badge
                )
                Spacer(Modifier.height(12.dp))

                Text("6. ประสบการณ์งานก่อสร้าง", fontWeight = FontWeight.Bold, color = NeoBlack)
                Spacer(Modifier.height(6.dp))
                Content.EXPERIENCE.forEach { o ->
                    NeoChoiceRow(
                        title = o.title,
                        subtitle = o.note,
                        selected = s.check(o.idx),
                        isRadio = true,
                        onClick = { vm.pickOne(Content.EXPERIENCE_IDS, o.idx) }
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (s.check(Content.EXP_YES)) {
                    Spacer(Modifier.height(4.dp))
                    NeoTextField(
                        value = s.field(F.EXP_DURATION),
                        onValueChange = { vm.setField(F.EXP_DURATION, it) },
                        label = "6.1 ทำมาแล้วกี่เดือน / ปี",
                        leadingIcon = Icons.Rounded.Schedule
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("7. งานก่อนหน้าที่จะมาโครงการนี้", fontWeight = FontWeight.Bold, color = NeoBlack)
                Spacer(Modifier.height(6.dp))
                Content.PREVIOUS.forEach { o ->
                    NeoChoiceRow(
                        title = o.title,
                        subtitle = o.note,
                        selected = s.check(o.idx),
                        isRadio = true,
                        onClick = { vm.pickOne(Content.PREVIOUS_IDS, o.idx) }
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (s.check(Content.PREV_OTHER_BOX)) {
                    Spacer(Modifier.height(4.dp))
                    NeoTextField(
                        value = s.field(F.PREV_OTHER),
                        onValueChange = { vm.setField(F.PREV_OTHER, it) },
                        label = "ระบุงานอื่นๆ",
                        leadingIcon = Icons.Rounded.EditNote
                    )
                }

                if (s.check(Content.PREV_NONE_BOX)) {
                    Spacer(Modifier.height(4.dp))
                    NeoTextField(
                        value = s.field(F.GAP_DURATION),
                        onValueChange = { vm.setField(F.GAP_DURATION, it) },
                        label = "7.1 กรณีไม่ได้ทำงาน หยุดไปกี่เดือน / ปี",
                        leadingIcon = Icons.Rounded.HourglassEmpty
                    )
                }
            }

            // ==========================================
            // หมวด 4: ผู้ติดต่อกรณีฉุกเฉิน & CN
            // ==========================================
            NeoSectionCard(
                sectionNumber = 4,
                title = "ผู้ติดต่อกรณีฉุกเฉิน & สังกัด",
                subtitle = "ญาติที่ติดต่อได้ และข้อมูลสังกัด CN",
                isComplete = s.isEmergencyComplete,
                isExpanded = expandedSections.contains(3),
                onToggle = { vm.toggleSection(3) }
            ) {
                NeoTextField(
                    value = s.field(F.EMG_NAME),
                    onValueChange = { vm.setField(F.EMG_NAME, it) },
                    label = "5.1 ชื่อ-สกุล ผู้ติดต่อกรณีฉุกเฉิน",
                    leadingIcon = Icons.Rounded.Person
                )
                Spacer(Modifier.height(10.dp))

                Text("5.2 ความสัมพันธ์เกี่ยวข้อง", fontWeight = FontWeight.SemiBold, color = NeoBlack)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Content.RELATIONS.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { rel ->
                                NeoChip(
                                    label = rel,
                                    selected = s.field(F.EMG_REL) == rel,
                                    onClick = { vm.setField(F.EMG_REL, if (s.field(F.EMG_REL) == rel) "" else rel) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                NeoTextField(
                    value = s.field(F.EMG_REL),
                    onValueChange = { vm.setField(F.EMG_REL, it) },
                    label = "หรือระบุความสัมพันธ์เอง",
                    leadingIcon = Icons.Rounded.Diversity3
                )
                Spacer(Modifier.height(10.dp))

                NeoTextField(
                    value = s.field(F.EMG_TEL),
                    onValueChange = { vm.setField(F.EMG_TEL, it.filter(Char::isDigit).take(10)) },
                    label = "5.3 เบอร์โทรผู้ติดต่อฉุกเฉิน",
                    leadingIcon = Icons.Rounded.PhoneInTalk,
                    keyboardType = KeyboardType.Phone,
                    errorMessage = if (s.field(F.EMG_TEL).isNotEmpty() && s.field(F.EMG_TEL).length < 9) "เบอร์โทรศัพท์ต้องมีอย่างน้อย 9-10 หลัก" else null
                )

                Spacer(Modifier.height(14.dp))
                Text("เฉพาะพนักงาน CN (ถ้ามีข้อมูล)", fontWeight = FontWeight.Bold, color = NeoBlack)
                Spacer(Modifier.height(6.dp))

                NeoTextField(
                    value = s.field(F.FOREMAN),
                    onValueChange = { vm.setField(F.FOREMAN, it) },
                    label = "โฟร์แมน",
                    leadingIcon = Icons.Rounded.Engineering
                )
                Spacer(Modifier.height(8.dp))

                NeoTextField(
                    value = s.field(F.LEADER),
                    onValueChange = { vm.setField(F.LEADER, it) },
                    label = "หัวหน้าชุด",
                    leadingIcon = Icons.Rounded.Groups
                )
            }

            // ==========================================
            // หมวด 5: ลายเซ็นดิจิทัล
            // ==========================================
            NeoSectionCard(
                sectionNumber = 5,
                title = "ลายเซ็นดิจิทัล",
                subtitle = "เซ็นชื่อเพื่อแนบลงในแบบฟอร์มท้ายเอกสาร",
                isComplete = s.isSignatureComplete,
                isExpanded = expandedSections.contains(4),
                onToggle = { vm.toggleSection(4) }
            ) {
                // สวิตช์เปิด/ปิดลายเซ็น
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "แนบลายเซ็นลงในเอกสาร",
                        fontWeight = FontWeight.SemiBold,
                        color = NeoBlack
                    )
                    Switch(
                        checked = s.withSignature,
                        onCheckedChange = { vm.setSignatureEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = PistachioDark)
                    )
                }

                if (s.withSignature) {
                    Spacer(Modifier.height(10.dp))
                    if (s.signaturePath != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, NeoBorder, RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = File(s.signaturePath!!),
                                contentDescription = "ลายเซ็น",
                                modifier = Modifier.fillMaxHeight()
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSign,
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Rounded.Draw, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("เซ็นใหม่")
                            }

                            Button(
                                onClick = { vm.clearSignature() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeoError),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("ลบลายเซ็น", color = Color.White)
                            }
                        }
                    } else {
                        NeoButton(
                            text = "แตะเพื่อเริ่มเซ็นชื่อ",
                            icon = Icons.Rounded.Draw,
                            onClick = onSign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // กล่องสนทนายืนยันล้างข้อมูล
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("ล้างข้อมูลฟอร์มทั้งหมด?") },
            text = { Text("ข้อมูลที่กรอก รูปภาพบัตร และลายเซ็นจะถูกล้างออกจากหน้านี้ (ข้อมูลโปรไฟล์จะไม่ถูกลบ)") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.reset()
                        showResetDialog = false
                    }
                ) {
                    Text("ล้างข้อมูล", color = NeoError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // กล่องสนทนาจัดการโปรไฟล์นายจ้าง / ส่วนบุคคล
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("จัดการข้อมูลโปรไฟล์สำหรับเติมอัตโนมัติ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("เลือกดำเนินการกับข้อมูลโปรไฟล์:", style = MaterialTheme.typography.bodyMedium)

                    Button(
                        onClick = {
                            vm.saveCurrentAsEmployerProfile()
                            showProfileDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PistachioDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Business, null)
                        Spacer(Modifier.width(6.dp))
                        Text("บันทึกข้อมูลนี้เป็นโปรไฟล์นายจ้าง")
                    }

                    Button(
                        onClick = {
                            vm.saveCurrentAsPersonalProfile()
                            showProfileDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Pistachio),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Person, null)
                        Spacer(Modifier.width(6.dp))
                        Text("บันทึกข้อมูลนี้เป็นโปรไฟล์ส่วนตัว")
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = {
                            vm.autofillEmployer()
                            showProfileDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ดึงข้อมูลนายจ้างมาเติมลงฟอร์ม")
                    }

                    OutlinedButton(
                        onClick = {
                            vm.autofillPersonal()
                            showProfileDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ดึงข้อมูลส่วนตัวมาเติมลงฟอร์ม")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("ปิด")
                }
            }
        )
    }
}
