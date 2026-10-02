package com.chb.form.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chb.form.data.DormFormData
import com.chb.form.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DormFormScreen(
    data: DormFormData,
    onUpdate: (DormFormData) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CreamBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "กลับ", tint = NeoBlack)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "แบบคำร้องเปิดห้องพัก",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = NeoBlack
                        )
                        Text(
                            "COCONUT 1 · Christiani & Nielsen",
                            fontSize = 12.sp,
                            color = NeoMutedText
                        )
                    }
                }
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
                    Button(
                        onClick = onExport,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PistachioDark)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, null, tint = androidx.compose.ui.graphics.Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("สร้าง PDF", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = CreamBg
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(title = "ข้อมูลเอกสาร") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = data.sequenceNo,
                        onValueChange = { onUpdate(data.copy(sequenceNo = it)) },
                        label = { Text("ลำดับที่") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = data.documentDate,
                        onValueChange = { onUpdate(data.copy(documentDate = it)) },
                        label = { Text("วันที่ (วว/ดด/ปปปป)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            SectionCard(title = "ผู้ขอเข้าพัก (สูงสุด 3 คน)") {
                data.applicants.forEachIndexed { index, applicant ->
                    Text("ผู้ขอคนที่ ${index + 1}", fontWeight = FontWeight.SemiBold, color = NeoBlack)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = applicant.name,
                        onValueChange = {
                            val newList = data.applicants.toMutableList()
                            newList[index] = applicant.copy(name = it)
                            onUpdate(data.copy(applicants = newList))
                        },
                        label = { Text("ชื่อ-นามสกุล") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = applicant.position,
                        onValueChange = {
                            val newList = data.applicants.toMutableList()
                            newList[index] = applicant.copy(position = it)
                            onUpdate(data.copy(applicants = newList))
                        },
                        label = { Text("ตำแหน่ง") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (index < 2) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = NeoBorder.copy(alpha = 0.3f))
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            SectionCard(title = "สังกัดหน่วยงาน") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = data.affiliationType == DormFormData.AffiliationType.CN_COMPANY,
                        onClick = { onUpdate(data.copy(affiliationType = DormFormData.AffiliationType.CN_COMPANY)) },
                        label = { Text("บริษัท CN") }
                    )
                    FilterChip(
                        selected = data.affiliationType == DormFormData.AffiliationType.CONTRACTOR,
                        onClick = { onUpdate(data.copy(affiliationType = DormFormData.AffiliationType.CONTRACTOR)) },
                        label = { Text("ผู้รับเหมา") }
                    )
                    FilterChip(
                        selected = data.affiliationType == DormFormData.AffiliationType.OTHER,
                        onClick = { onUpdate(data.copy(affiliationType = DormFormData.AffiliationType.OTHER)) },
                        label = { Text("อื่นๆ") }
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (data.affiliationType == DormFormData.AffiliationType.CN_COMPANY || data.affiliationType == DormFormData.AffiliationType.NONE) {
                    OutlinedTextField(value = data.headOfTeamCn, onValueChange = { onUpdate(data.copy(headOfTeamCn = it)) }, label = { Text("หัวหน้าชุด (CN)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = data.foremanCn, onValueChange = { onUpdate(data.copy(foremanCn = it)) }, label = { Text("โฟร์แมน (CN)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }

                if (data.affiliationType == DormFormData.AffiliationType.CONTRACTOR || data.affiliationType == DormFormData.AffiliationType.NONE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = data.headOfTeamContractor, onValueChange = { onUpdate(data.copy(headOfTeamContractor = it)) }, label = { Text("หัวหน้าชุด (ผู้รับเหมา)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = data.supervisor, onValueChange = { onUpdate(data.copy(supervisor = it)) }, label = { Text("หัวหน้าคนงาน") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = data.contractMaker, onValueChange = { onUpdate(data.copy(contractMaker = it)) }, label = { Text("ผู้ทำสัญญา") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }

                if (data.affiliationType == DormFormData.AffiliationType.OTHER) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = data.otherAffiliation, onValueChange = { onUpdate(data.copy(otherAffiliation = it)) }, label = { Text("ระบุสังกัดอื่นๆ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }

            SectionCard(title = "รายละเอียดการพัก") {
                OutlinedTextField(value = data.days, onValueChange = { onUpdate(data.copy(days = it)) }, label = { Text("จำนวนวัน") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = data.startDate, onValueChange = { onUpdate(data.copy(startDate = it)) }, label = { Text("ตั้งแต่วันที่") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = data.endDate, onValueChange = { onUpdate(data.copy(endDate = it)) }, label = { Text("ถึงวันที่") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }

            SectionCard(title = "ผู้ค้ำประกัน / ผู้รับรอง") {
                OutlinedTextField(value = data.guarantorName, onValueChange = { onUpdate(data.copy(guarantorName = it)) }, label = { Text("ชื่อ-นามสกุล ผู้ค้ำ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = data.guarantorPosition, onValueChange = { onUpdate(data.copy(guarantorPosition = it)) }, label = { Text("ตำแหน่ง") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = data.guarantorCompany, onValueChange = { onUpdate(data.copy(guarantorCompany = it)) }, label = { Text("บริษัท / หน่วยงาน") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CreamSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, NeoBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NeoBlack)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
