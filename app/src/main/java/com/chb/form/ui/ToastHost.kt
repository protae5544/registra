package com.chb.form.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chb.form.ui.theme.*
import kotlinx.coroutines.delay

enum class ToastType { SUCCESS, INFO, WARNING }

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val type: ToastType = ToastType.INFO
)

/**
 * Toast Notification สไตล์ Neo-brutalist พร้อมอนิเมชันเลื่อนและจางหาย (Slide & Fade)
 * มีการหน่วงเวลาแสดงผล 2.6 วินาที และมีระยะคั่นระหว่างการแจ้งเตือน
 */
@Composable
fun ToastHost(
    message: ToastMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ซ่อนข้อความอัตโนมัติเมื่อครบเวลา
    LaunchedEffect(message?.id) {
        if (message != null) {
            delay(2800)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(250)
        ) + fadeOut(animationSpec = tween(250)),
        modifier = modifier
    ) {
        if (message != null) {
            val (bgColor, borderColor, icon) = when (message.type) {
                ToastType.SUCCESS -> Triple(PistachioContainer, PistachioDark, Icons.Rounded.CheckCircle)
                ToastType.WARNING -> Triple(NeoWarningContainer, NeoWarning, Icons.Rounded.WarningAmber)
                ToastType.INFO -> Triple(CreamSurface, NeoBorder, Icons.Rounded.Info)
            }

            NeoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                borderColor = borderColor,
                containerColor = bgColor,
                shadowOffset = 2.5.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = borderColor,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = message.text,
                        color = NeoBlack,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "ปิด",
                            tint = NeoMutedText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
