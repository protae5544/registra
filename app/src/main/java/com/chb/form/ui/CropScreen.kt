package com.chb.form.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.chb.form.ui.theme.CreamBg
import com.chb.form.ui.theme.NeoBorder
import com.chb.form.ui.theme.Pistachio
import com.chb.form.ui.theme.PistachioDark
import kotlin.math.roundToInt

// สัดส่วนบัตรประชาชนมาตรฐาน (85.60 mm x 53.98 mm)
private const val CARD_ASPECT_RATIO = 85.6f / 54.0f

enum class DragHandle { NONE, CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * หน้าจอครอบตัดรูปบัตร (Crop Screen) สัดส่วนล็อกตามบัตรประชาชน พร้อมจุดลากมุม 4 ด้าน
 */
@Composable
fun CropScreen(
    rawBitmap: Bitmap,
    onCropped: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var workingBitmap by remember { mutableStateOf(rawBitmap) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // พิกัด Rect ของการครอปในหน่วย Viewport (0..containerWidth, 0..containerHeight)
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    // คำนวณขอบเขตของรูปภาพที่แสดงใน container (Fit Center)
    val imageRect = remember(containerSize, workingBitmap) {
        if (containerSize.width == 0 || containerSize.height == 0) Rect.Zero
        else {
            val cw = containerSize.width.toFloat()
            val ch = containerSize.height.toFloat()
            val bw = workingBitmap.width.toFloat()
            val bh = workingBitmap.height.toFloat()
            val scale = minOf(cw / bw, ch / bh)
            val dw = bw * scale
            val dh = bh * scale
            val dx = (cw - dw) / 2f
            val dy = (ch - dh) / 2f
            Rect(dx, dy, dx + dw, dy + dh)
        }
    }

    // เริ่มต้นกำหนด cropRect ให้อยู่กลางรูปภาพตามสัดส่วนบัตร
    LaunchedEffect(imageRect) {
        if (!imageRect.isEmpty && cropRect == null) {
            val maxW = imageRect.width * 0.9f
            val maxH = imageRect.height * 0.9f
            val (w, h) = if (maxW / maxH > CARD_ASPECT_RATIO) {
                (maxH * CARD_ASPECT_RATIO) to maxH
            } else {
                maxW to (maxW / CARD_ASPECT_RATIO)
            }
            val l = imageRect.left + (imageRect.width - w) / 2f
            val t = imageRect.top + (imageRect.height - h) / 2f
            cropRect = Rect(l, t, l + w, t + h)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ส่วนหัว
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, "ยกเลิก", tint = Color.White)
            }
            Text(
                "ปรับกรอบบัตรประชาชน",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(
                onClick = {
                    // หมุนภาพ 90 องศา
                    val m = android.graphics.Matrix().apply { postRotate(90f) }
                    workingBitmap = Bitmap.createBitmap(
                        workingBitmap, 0, 0,
                        workingBitmap.width, workingBitmap.height, m, true
                    )
                    cropRect = null // รีเซ็ตกรอบใหม่หลังหมุน
                }
            ) {
                Icon(Icons.AutoMirrored.Rounded.RotateRight, "หมุน", tint = Color.White)
            }
        }

        // พื้นที่รูปภาพและกรอบครอปแบบลากได้
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { containerSize = it }
                .pointerInput(imageRect) {
                    val handleRadius = 45f // ระยะสัมผัสของจุดลากมุม
                    detectDragGestures(
                        onDragStart = { pos ->
                            val r = cropRect ?: return@detectDragGestures
                            activeHandle = when {
                                (pos - Offset(r.left, r.top)).getDistance() < handleRadius -> DragHandle.TOP_LEFT
                                (pos - Offset(r.right, r.top)).getDistance() < handleRadius -> DragHandle.TOP_RIGHT
                                (pos - Offset(r.left, r.bottom)).getDistance() < handleRadius -> DragHandle.BOTTOM_LEFT
                                (pos - Offset(r.right, r.bottom)).getDistance() < handleRadius -> DragHandle.BOTTOM_RIGHT
                                r.contains(pos) -> DragHandle.CENTER
                                else -> DragHandle.NONE
                            }
                        },
                        onDrag = { change, dragAmount ->
                            val r = cropRect ?: return@detectDragGestures
                            if (activeHandle == DragHandle.NONE) return@detectDragGestures
                            change.consume()

                            cropRect = when (activeHandle) {
                                DragHandle.CENTER -> {
                                    val nx = (r.left + dragAmount.x).coerceIn(imageRect.left, imageRect.right - r.width)
                                    val ny = (r.top + dragAmount.y).coerceIn(imageRect.top, imageRect.bottom - r.height)
                                    Rect(nx, ny, nx + r.width, ny + r.height)
                                }
                                DragHandle.BOTTOM_RIGHT -> {
                                    // จุดมุมซ้ายบนเป็นจุดตรึง (Anchor) ปรับขนาดตามสัดส่วนบัตร
                                    val newW = (r.width + dragAmount.x)
                                        .coerceIn(120f, imageRect.right - r.left)
                                    val newH = newW / CARD_ASPECT_RATIO
                                    if (r.top + newH <= imageRect.bottom) {
                                        Rect(r.left, r.top, r.left + newW, r.top + newH)
                                    } else {
                                        val maxH = imageRect.bottom - r.top
                                        Rect(r.left, r.top, r.left + maxH * CARD_ASPECT_RATIO, r.top + maxH)
                                    }
                                }
                                DragHandle.TOP_LEFT -> {
                                    // จุดมุมขวาล่างเป็นจุดตรึง
                                    val newW = (r.width - dragAmount.x)
                                        .coerceIn(120f, r.right - imageRect.left)
                                    val newH = newW / CARD_ASPECT_RATIO
                                    if (r.bottom - newH >= imageRect.top) {
                                        Rect(r.right - newW, r.bottom - newH, r.right, r.bottom)
                                    } else r
                                }
                                DragHandle.TOP_RIGHT -> {
                                    // จุดมุมซ้ายล่างเป็นจุดตรึง
                                    val newW = (r.width + dragAmount.x)
                                        .coerceIn(120f, imageRect.right - r.left)
                                    val newH = newW / CARD_ASPECT_RATIO
                                    if (r.bottom - newH >= imageRect.top) {
                                        Rect(r.left, r.bottom - newH, r.left + newW, r.bottom)
                                    } else r
                                }
                                DragHandle.BOTTOM_LEFT -> {
                                    // จุดมุมขวาบนเป็นจุดตรึง
                                    val newW = (r.width - dragAmount.x)
                                        .coerceIn(120f, r.right - imageRect.left)
                                    val newH = newW / CARD_ASPECT_RATIO
                                    if (r.top + newH <= imageRect.bottom) {
                                        Rect(r.right - newW, r.top, r.right, r.top + newH)
                                    } else r
                                }
                                else -> r
                            }
                        },
                        onDragEnd = { activeHandle = DragHandle.NONE },
                        onDragCancel = { activeHandle = DragHandle.NONE }
                    )
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                if (imageRect.isEmpty) return@Canvas

                // 1. วาดรูปภาพ Bitmap
                drawImage(
                    image = workingBitmap.asImageBitmap(),
                    dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                    dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt())
                )

                // 2. วาดแผ่นฟิล์มสลัวทึบแสงนอกกรอบครอป (Dim Overlay)
                val rect = cropRect ?: return@Canvas
                drawRect(Color.Black.copy(alpha = 0.60f))

                // 3. เจาะรูกรอบครอปให้โปร่งใสเห็นภาพชัดเจน
                drawRect(
                    color = Color.Transparent,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    blendMode = BlendMode.Clear
                )

                // 4. วาดเส้นขอบกรอบและเส้นนำสายตา 1/3 (Grid Lines)
                drawRect(
                    color = Pistachio,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 3.dp.toPx())
                )

                // เส้นกริดแนวนอน
                val hStep = rect.height / 3f
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(rect.left, rect.top + hStep),
                    end = Offset(rect.right, rect.top + hStep),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(rect.left, rect.top + hStep * 2),
                    end = Offset(rect.right, rect.top + hStep * 2),
                    strokeWidth = 1.dp.toPx()
                )

                // เส้นกริดแนวตั้ง
                val wStep = rect.width / 3f
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(rect.left + wStep, rect.top),
                    end = Offset(rect.left + wStep, rect.bottom),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(rect.left + wStep * 2, rect.top),
                    end = Offset(rect.left + wStep * 2, rect.bottom),
                    strokeWidth = 1.dp.toPx()
                )

                // 5. วาดจุดจับมุม 4 ด้าน (Corner Handles)
                val handleRadius = 10.dp.toPx()
                val corners = listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight)
                for (pt in corners) {
                    drawCircle(color = PistachioDark, radius = handleRadius, center = pt)
                    drawCircle(color = Color.White, radius = handleRadius - 3.dp.toPx(), center = pt)
                }
            }
        }

        // ปุ่มควบคุมด้านล่าง
        Surface(
            color = Color(0xFF141712),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("ยกเลิก")
                }

                Button(
                    onClick = {
                        val r = cropRect
                        if (r != null && !imageRect.isEmpty) {
                            // แปลงพิกัดจาก Viewport กลับเป็นพิกัด Bitmap จริง
                            val scaleX = workingBitmap.width / imageRect.width
                            val scaleY = workingBitmap.height / imageRect.height
                            val bx = ((r.left - imageRect.left) * scaleX).toInt().coerceIn(0, workingBitmap.width - 1)
                            val by = ((r.top - imageRect.top) * scaleY).toInt().coerceIn(0, workingBitmap.height - 1)
                            val bw = (r.width * scaleX).toInt().coerceIn(1, workingBitmap.width - bx)
                            val bh = (r.height * scaleY).toInt().coerceIn(1, workingBitmap.height - by)

                            val cropped = runCatching {
                                Bitmap.createBitmap(workingBitmap, bx, by, bw, bh)
                            }.getOrDefault(workingBitmap)
                            onCropped(cropped)
                        } else {
                            onCropped(workingBitmap)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PistachioDark),
                    modifier = Modifier.weight(1.5f).height(50.dp)
                ) {
                    Icon(Icons.Rounded.Crop, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("เสร็จสิ้น", color = Color.White)
                }
            }
        }
    }
}
