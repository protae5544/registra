package com.chb.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.School
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

/**
 * หน้าเลือกเทมเพลตแบบฟอร์ม
 */
@Composable
fun TemplateSelectScreen(
    onSelect: (TemplateType) -> Unit
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
        Text(
            text = "เลือกแบบฟอร์ม",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = NeoBlack
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "เลือกประเภทเอกสารที่ต้องการกรอก",
            style = MaterialTheme.typography.bodyMedium,
            color = NeoMutedText
        )
        Spacer(Modifier.height(32.dp))

        TemplateCard(
            title = TemplateType.TRAINING.displayName,
            subtitle = TemplateType.TRAINING.description,
            icon = Icons.Rounded.School,
            onClick = { onSelect(TemplateType.TRAINING) }
        )
        Spacer(Modifier.height(16.dp))
        TemplateCard(
            title = TemplateType.DORMITORY.displayName,
            subtitle = TemplateType.DORMITORY.description,
            icon = Icons.Rounded.Apartment,
            onClick = { onSelect(TemplateType.DORMITORY) }
        )
    }
}

@Composable
private fun TemplateCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, NeoBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = CreamSurface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PistachioDark,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NeoBlack
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = NeoMutedText,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
