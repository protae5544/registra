package com.chb.form.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chb.form.data.FormData
import com.chb.form.data.Spec
import com.chb.form.pdf.PdfTools
import com.chb.form.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// โซนสัมผัสบนเอกสารต้นฉบับ 1785 x 2576
private data class DocumentHitZone(
    val sectionIndex: Int,
    val name: String,
    val rect: Rect
)

private val HIT_ZONES = listOf(
    // หมวด 1: พื้นที่และหลักสูตร
    DocumentHitZone(0, "พื้นที่ปฏิบัติงาน", Rect(90f, 350f, 410f, 730f)),
    DocumentHitZone(0, "หลักสูตรเฉพาะ", Rect(1240f, 190f, 1720f, 730f)),
    // หมวด 2: รูปบัตรประชาชน
    DocumentHitZone(1, "บัตรประชาชน", Rect(410f, 240f, 1245f, 725f)),
    // หมวด 3: ข้อมูลทั่วไป & ประวัติ
    DocumentHitZone(2, "ข้อมูลทั่วไป", Rect(100f, 800f, 1550f, 1140f)),
    DocumentHitZone(2, "ประวัติการทำงาน", Rect(100f, 1440f, 1600f, 2020f)),
    // หมวด 4: กรณีฉุกเฉิน & CN
    DocumentHitZone(3, "ผู้ติดต่อฉุกเฉิน", Rect(100f, 1140f, 1650f, 1440f)),
    DocumentHitZone(3, "เฉพาะพนักงาน CN", Rect(1050f, 2020f, 1720f, 2340f)),
    // หมวด 5: ลายเซ็น
    DocumentHitZone(4, "ลายเซ็น", Rect(120f, 2240f, 800f, 2560f))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    file: File,
    formData: FormData,
    isStale: Boolean,
    onRefreshPdf: () -> Unit,
    onNavigateToSection: (Int) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // สลับเปิด/ปิดเลเยอร์ไฮไลต์ช่องที่ยังไม่ได้กรอก
    var highlightMissing by remember { mutableStateOf(false) }

    // โหลดพรีวิว PDF ในพื้นหลัง
    LaunchedEffect(file) {
        bmp = withContext(Dispatchers.IO) {
            PdfTools.preview(file, 1800)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ตัวอย่างเอกสารใบสมัคร", fontWeight = FontWeight.Bold)
                        Text(
                            file.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = NeoMutedText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "กลับ", tint = NeoBlack)
                    }
                },
                actions = {
                    // ปุ่มเปิด/ปิดไฮไลต์
                    IconButton(onClick = { highlightMissing = !highlightMissing }) {
                        Icon(
                            if (highlightMissing) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "ไฮไลต์ช่องว่าง",
                            tint = if (highlightMissing) NeoWarning else NeoBlack
                        )
                    }
                    IconButton(onClick = { PdfTools.print(ctx, file) }) {
                        Icon(Icons.Rounded.Print, "พิมพ์", tint = NeoBlack)
                    }
                    IconButton(onClick = { PdfTools.open(ctx, file) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, "เปิด", tint = NeoBlack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CreamBg)
            )
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
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("รีเซ็ตซูม", color = NeoBlack)
                    }

                    NeoButton(
                        text = "แชร์ไฟล์ PDF",
                        icon = Icons.Rounded.Share,
                        onClick = { PdfTools.share(ctx, file) },
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        },
        containerColor = Color(0xFF2C3229)
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            // แถบแจ้งเตือนเมื่อฟอร์มถูกแก้ไขหลังสร้าง PDF ล่าสุด (Staleness Check)
            AnimatedVisibility(visible = isStale) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NeoWarningContainer)
                        .border(1.dp, NeoWarning)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = NeoWarning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ฟอร์มมีการแก้ไขใหม่หลังสร้างเอกสารนี้",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NeoBlack
                        )
                    }
                    TextButton(onClick = onRefreshPdf) {
                        Text("อัปเดต PDF", color = PistachioDark, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // คำแนะนำการแตะเพื่อกระโดดไปยังฟิลด์
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF20251E))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "แตะที่ส่วนใดของเอกสารเพื่อไปยังหมวดกรอกข้อมูลนั้นๆ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            // พื้นที่แสดงรูปเอกสารและการตรวจจับพิกัด
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { containerSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val currentBmp = bmp
                if (currentBmp != null && containerSize.width > 0 && containerSize.height > 0) {
                    val cw = containerSize.width.toFloat()
                    val ch = containerSize.height.toFloat()
                    val bw = currentBmp.width.toFloat()
                    val bh = currentBmp.height.toFloat()
                    val fitScale = minOf((cw - 24f) / bw, (ch - 24f) / bh)
                    val dispW = bw * fitScale
                    val dispH = bh * fitScale

                    Box(
                        modifier = Modifier
                            .size((dispW).dp, (dispH).dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .pointerInput(currentBmp) {
                                detectTapGestures { tapOffset ->
                                    // แปลงพิกัดการแตะบนหน้าจอกลับเป็นพิกัดเอกสาร Spec.W x Spec.H
                                    val docX = (tapOffset.x / dispW) * Spec.W
                                    val docY = (tapOffset.y / dispH) * Spec.H
                                    val touchedZone = HIT_ZONES.firstOrNull { it.rect.contains(Offset(docX, docY)) }
                                    if (touchedZone != null) {
                                        onNavigateToSection(touchedZone.sectionIndex)
                                    }
                                }
                            }
                    ) {
                        Image(
                            bitmap = currentBmp.asImageBitmap(),
                            contentDescription = "เอกสาร",
                            modifier = Modifier.fillMaxSize()
                        )

                        // เลเยอร์วาดไฮไลต์ช่องที่ยังไม่ได้กรอก
                        if (highlightMissing) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sX = size.width / Spec.W
                                val sY = size.height / Spec.H

                                fun drawMissing(r: Rect) {
                                    val left = r.left * sX
                                    val top = r.top * sY
                                    val w = r.width * sX
                                    val h = r.height * sY
                                    drawRect(
                                        color = Color(0x66FF5722),
                                        topLeft = Offset(left, top),
                                        size = Size(w, h)
                                    )
                                    drawRect(
                                        color = Color(0xFFFF5722),
                                        topLeft = Offset(left, top),
                                        size = Size(w, h),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }

                                if (!formData.isAreaCourseComplete) {
                                    drawMissing(Rect(90f, 350f, 410f, 730f))
                                    drawMissing(Rect(1240f, 190f, 1720f, 730f))
                                }
                                if (!formData.isCardComplete) {
                                    drawMissing(Rect(410f, 240f, 1245f, 725f))
                                }
                                if (!formData.isInfoComplete) {
                                    drawMissing(Rect(100f, 800f, 1550f, 1140f))
                                    drawMissing(Rect(100f, 1440f, 1600f, 2020f))
                                }
                                if (!formData.isEmergencyComplete) {
                                    drawMissing(Rect(100f, 1140f, 1650f, 1440f))
                                }
                            }
                        }
                    }
                } else {
                    CircularProgressIndicator(color = Pistachio)
                }
            }
        }
    }
}
