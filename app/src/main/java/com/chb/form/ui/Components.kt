package com.chb.form.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chb.form.ui.theme.*

val NeoCutShape: Shape = CutCornerShape(topStart = 0.dp, topEnd = 8.dp, bottomEnd = 0.dp, bottomStart = 8.dp)
val NeoRoundShape: Shape = RoundedCornerShape(8.dp)

/**
 * กล่อง Neo-brutalist สไตล์ Flat พร้อมเส้นขอบคมชัดและเงากล่องเลเยอร์ (Layered Shadow Box)
 */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    shape: Shape = NeoRoundShape,
    borderWidth: Dp = 2.dp,
    borderColor: Color = NeoBorder,
    shadowColor: Color = NeoBorder,
    shadowOffset: Dp = 3.dp,
    containerColor: Color = CreamSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val pressOffset = if (isPressed && onClick != null) shadowOffset else 0.dp

    Box(modifier = modifier) {
        // เงากล่องเลเยอร์ด้านหลัง (Hard Drop Box)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(shadowColor, shape)
        )
        // กล่องเนื้อหาด้านหน้า
        Box(
            modifier = Modifier
                .offset(x = pressOffset, y = pressOffset)
                .border(borderWidth, borderColor, shape)
                .background(containerColor, shape)
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                    } else Modifier
                ),
            content = content
        )
    }
}

/**
 * ปุ่มกดสไตล์ Neo-brutalist พร้อมเอฟเฟกต์ยุบตัวเมื่อกด (Pressable Offset)
 */
@Composable
fun NeoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    shape: Shape = NeoRoundShape
) {
    val hap = LocalHapticFeedback.current
    val container = when {
        !enabled -> CreamMuted
        isPrimary -> PistachioDark
        else -> CreamSurface
    }
    val contentColor = when {
        !enabled -> NeoMutedText
        isPrimary -> Color.White
        else -> NeoBlack
    }

    NeoCard(
        modifier = modifier.height(50.dp),
        shape = shape,
        shadowOffset = if (enabled) 3.dp else 1.dp,
        containerColor = container,
        onClick = if (enabled) {
            {
                hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        } else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

/**
 * ชิปตัวเลือกสไตล์ Neo-brutalist พร้อมอนิเมชันสีและการกด
 */
@Composable
fun NeoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val hap = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) PistachioContainer else CreamSurface,
        animationSpec = tween(180),
        label = "chip_bg"
    )
    val borderColor = if (selected) PistachioDark else NeoBorder

    NeoCard(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(6.dp),
        borderWidth = if (selected) 2.dp else 1.5.dp,
        borderColor = borderColor,
        shadowOffset = if (selected) 2.dp else 1.5.dp,
        containerColor = bgColor,
        onClick = {
            hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, null, tint = PistachioDark, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            } else if (icon != null) {
                Icon(icon, null, tint = NeoMutedText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) PistachioOnContainer else NeoBlack
            )
        }
    }
}

/**
 * แถบ Progress Bar แบบแบ่งเซกเมนต์ตามหมวดพร้อมอนิเมชันเติมเต็ม
 */
@Composable
fun NeoSegmentedProgress(
    progress: Float,
    totalSegments: Int = 5,
    modifier: Modifier = Modifier
) {
    val activeCount = (progress * totalSegments).toInt().coerceIn(0, totalSegments)
    val percentText = (progress * 100).toInt()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ความสมบูรณ์ของแบบฟอร์ม",
                style = MaterialTheme.typography.labelSmall,
                color = NeoMutedText,
                fontWeight = FontWeight.Medium
            )
            Text(
                "$percentText% ($activeCount/$totalSegments ส่วน)",
                style = MaterialTheme.typography.labelSmall,
                color = PistachioDark,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0 until totalSegments) {
                val filled = i < activeCount
                val segmentColor by animateColorAsState(
                    targetValue = if (filled) Pistachio else CreamMuted,
                    animationSpec = tween(300),
                    label = "seg_color_$i"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.5.dp, NeoBorder, RoundedCornerShape(3.dp))
                        .background(segmentColor, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

/**
 * กล่องเซกชันแบบ Accordion พับ-ขยายได้ พร้อมป้ายสถานะ Badge
 */
@Composable
fun NeoSectionCard(
    sectionNumber: Int,
    title: String,
    subtitle: String? = null,
    isComplete: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "chevron_rot"
    )

    NeoCard(
        modifier = modifier.fillMaxWidth(),
        shape = NeoRoundShape,
        shadowOffset = 3.dp,
        containerColor = CreamSurface
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ส่วนหัวของ Accordion ที่แตะเพื่อพับ/กางได้
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ตัวเลขลำดับกล่อง
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .border(1.5.dp, NeoBorder, RoundedCornerShape(6.dp))
                        .background(if (isComplete) PistachioContainer else CreamMuted, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isComplete) {
                        Icon(Icons.Rounded.Check, null, tint = PistachioDark, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            "$sectionNumber",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = NeoBlack
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeoBlack
                    )
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = NeoMutedText
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // ป้ายสถานะ
                NeoBadge(
                    text = if (isComplete) "ครบถ้วน" else "รอข้อมูล",
                    isSuccess = isComplete
                )

                Spacer(Modifier.width(6.dp))

                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "ย่อ" else "ขยาย",
                    modifier = Modifier.rotate(rotation).size(22.dp),
                    tint = NeoBlack
                )
            }

            // เนื้อหาที่ขยายออก
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFFE2DCD0))
                    Spacer(Modifier.height(12.dp))
                    content()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * แถบเลือกรายการสไตล์ Neo-brutalist (Choice Row)
 */
@Composable
fun NeoChoiceRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRadio: Boolean = false
) {
    val hap = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) PistachioLight else CreamSurface,
        label = "choice_bg"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.5.dp,
                color = if (selected) PistachioDark else NeoBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .background(bgColor, RoundedCornerShape(8.dp))
            .clickable {
                hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // กล่องเครื่องหมาย Check/Radio
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        1.5.dp,
                        if (selected) PistachioDark else NeoBorder,
                        if (isRadio) RoundedCornerShape(11.dp) else RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (selected) PistachioDark else Color.Transparent,
                        if (isRadio) RoundedCornerShape(11.dp) else RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = NeoBlack
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = NeoMutedText
                    )
                }
            }
        }
    }
}

/**
 * ช่องกรอกข้อความสไตล์ Neo-brutalist
 */
@Composable
fun NeoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    errorMessage: String? = null,
    singleLine: Boolean = true
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontWeight = FontWeight.Medium) },
            placeholder = placeholder?.let { { Text(it, color = NeoMutedText) } },
            leadingIcon = leadingIcon?.let {
                { Icon(it, null, tint = PistachioDark, modifier = Modifier.size(20.dp)) }
            },
            trailingIcon = trailingIcon ?: if (value.isNotEmpty()) {
                {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Rounded.Cancel, "ลบ", tint = NeoMutedText, modifier = Modifier.size(18.dp))
                    }
                }
            } else null,
            isError = errorMessage != null,
            singleLine = singleLine,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CreamSurface,
                unfocusedContainerColor = CreamSurface,
                focusedBorderColor = PistachioDark,
                unfocusedBorderColor = NeoBorder,
                errorBorderColor = NeoError,
                focusedLabelColor = PistachioDark,
                unfocusedLabelColor = NeoMutedText
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onNext = { onImeAction() }
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = NeoError,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}

/**
 * ป้ายกำกับ Badge / Tag สไตล์ Neo-brutalist
 */
@Composable
fun NeoBadge(
    text: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    val bg = if (isSuccess) PistachioContainer else CreamMuted
    val fg = if (isSuccess) PistachioOnContainer else NeoMutedText
    val border = if (isSuccess) PistachioDark else NeoBorder

    Box(
        modifier = modifier
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}
