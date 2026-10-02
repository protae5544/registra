package com.chb.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chb.form.ui.theme.*

/**
 * หน้า Placeholder สำหรับเทมเพลตห้องพัก
 * จะถูกแทนที่ด้วยหน้ากรอกจริงในขั้นตอนถัดไป
 */
@Composable
fun DormPlaceholderScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "กลับ", tint = NeoBlack)
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Construction,
                contentDescription = null,
                tint = PistachioDark,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "แบบคำร้องขออนุญาตเปิดห้องพักคนงาน",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NeoBlack,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "กำลังพัฒนาหน้ากรอกข้อมูล\nโครงสร้างพื้นฐานพร้อมแล้ว\nจะเปิดใช้งานได้ในเวอร์ชันถัดไป",
                style = MaterialTheme.typography.bodyMedium,
                color = NeoMutedText,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(32.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = PistachioContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, PistachioDark)
            ) {
                Text(
                    text = "โครงสร้างข้อมูล + Spec + Storage พร้อมแล้ว",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontWeight = FontWeight.Medium,
                    color = PistachioOnContainer,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}
