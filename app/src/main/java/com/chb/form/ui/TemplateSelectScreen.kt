package com.chb.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chb.form.data.TemplateType
import com.chb.form.ui.theme.*

@Composable
fun TemplateSelectScreen(
    onSelect: (TemplateType) -> Unit,
    onImportPdf: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("เลือกแบบฟอร์ม", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = NeoBlack)
        Spacer(Modifier.height(8.dp))
        Text("เลือกเทมเพลตที่มี หรือนำเข้า PDF ใหม่", style = MaterialTheme.typography.bodyMedium, color = NeoMutedText)
        Spacer(Modifier.height(32.dp))
        TemplateCard(TemplateType.TRAINING.displayName, TemplateType.TRAINING.description, Icons.Rounded.School) { onSelect(TemplateType.TRAINING) }
        Spacer(Modifier.height(16.dp))
        TemplateCard(TemplateType.DORMITORY.displayName, TemplateType.DORMITORY.description, Icons.Rounded.Apartment) { onSelect(TemplateType.DORMITORY) }
        Spacer(Modifier.height(16.dp))
        TemplateCard(
            "นำเข้า PDF ฟอร์มใดก็ได้",
            "สกัดข้อความ เส้น กรอบ และฟิลด์อัตโนมัติ — มีคำเตือนถ้าไม่ครบ ผู้ใช้แก้เองได้ก่อนสร้าง",
            Icons.Rounded.UploadFile,
            onImportPdf
        )
    }
}

@Composable
private fun TemplateCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.5.dp, NeoBorder, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        color = CreamSurface, shadowElevation = 2.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PistachioDark, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NeoBlack)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = NeoMutedText, lineHeight = 18.sp)
            }
        }
    }
}
