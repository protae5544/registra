package com.chb.form.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chb.form.ocr.ExtractedIdCard
import com.chb.form.ui.theme.*

/**
 * หน้าจอตรวจสอบข้อมูลที่อ่านได้จากบัตร (OCR Review Screen) พร้อมให้แก้ไขก่อนนำเข้าฟอร์ม
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrReviewScreen(
    croppedBitmap: Bitmap,
    initialData: ExtractedIdCard,
    onApply: (ExtractedIdCard, targetEmergency: Boolean) -> Unit,
    onSkip: () -> Unit
) {
    var name by remember { mutableStateOf(initialData.nameThai) }
    var idNum by remember { mutableStateOf(initialData.idNumber) }
    var blood by remember { mutableStateOf(initialData.bloodType) }
    var phone by remember { mutableStateOf(initialData.phone) }
    var position by remember { mutableStateOf(initialData.position) }

    var applyToEmergency by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ตรวจสอบข้อมูลจากบัตร",
                        fontWeight = FontWeight.Bold,
                        color = NeoBlack
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onSkip) {
                        Icon(Icons.Rounded.ArrowBack, "ย้อนกลับ", tint = NeoBlack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CreamBg)
            )
        },
        bottomBar = {
            Surface(
                color = CreamSurface,
                border = border1_5(NeoBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSkip,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Text("ข้ามไม่ใช้ OCR", color = NeoBlack)
                    }

                    NeoButton(
                        text = "นำข้อมูลไปใช้",
                        icon = Icons.Rounded.DoneAll,
                        onClick = {
                            val updated = initialData.copy(
                                nameThai = name,
                                idNumber = idNum,
                                bloodType = blood,
                                phone = phone,
                                position = position
                            )
                            onApply(updated, applyToEmergency)
                        },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ภาพบัตรที่ครอปแล้ว
            NeoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(85.6f / 54f),
                shape = RoundedCornerShape(10.dp),
                shadowOffset = 2.dp
            ) {
                Image(
                    bitmap = croppedBitmap.asImageBitmap(),
                    contentDescription = "รูปบัตรประชาชน",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // คำแนะนำ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PistachioLight, RoundedCornerShape(8.dp))
                    .border(1.dp, Pistachio, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = PistachioDark, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "ระบบตรวจจับข้อมูลจากบัตรอัตโนมัติ กรุณาตรวจสอบและแก้ไขหากมีข้อผิดพลาด",
                    style = MaterialTheme.typography.bodySmall,
                    color = PistachioOnContainer
                )
            }

            // ช่องข้อมูลที่อ่านได้
            NeoCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = CreamSurface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "ฟิลด์ข้อมูลที่ตรวจพบ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeoBlack
                    )

                    NeoTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "ชื่อ-นามสกุล (ไทย)",
                        leadingIcon = Icons.Rounded.Person
                    )

                    NeoTextField(
                        value = idNum,
                        onValueChange = { idNum = it },
                        label = "เลขประจำตัวประชาชน 13 หลัก",
                        leadingIcon = Icons.Rounded.Badge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NeoTextField(
                            value = blood,
                            onValueChange = { blood = it },
                            label = "กรุ๊ปเลือด",
                            leadingIcon = Icons.Rounded.Bloodtype,
                            modifier = Modifier.weight(1f)
                        )

                        NeoTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = "เบอร์โทร",
                            leadingIcon = Icons.Rounded.Phone,
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    NeoTextField(
                        value = position,
                        onValueChange = { position = it },
                        label = "ตำแหน่งงาน",
                        leadingIcon = Icons.Rounded.Work
                    )

                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Color(0xFFE2DCD0))
                    Spacer(Modifier.height(4.dp))

                    // ตัวเลือกเติมลงในผู้ติดต่อฉุกเฉิน
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (applyToEmergency) PistachioLight else CreamBg)
                            .border(1.dp, if (applyToEmergency) PistachioDark else NeoBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = applyToEmergency,
                            onCheckedChange = { applyToEmergency = it },
                            colors = CheckboxDefaults.colors(checkedColor = PistachioDark)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "บันทึกชื่อนี้เป็นผู้ติดต่อกรณีฉุกเฉิน (ข้อ 5.1)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NeoBlack
                            )
                            Text(
                                "หากไม่ใช่ชื่อของเจ้าของบัตรเอง ให้ติ๊กเลือกข้อนี้",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeoMutedText
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun border1_5(c: Color) = androidx.compose.foundation.BorderStroke(1.5.dp, c)
