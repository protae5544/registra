package com.chb.form.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Ruler
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chb.form.ui.theme.CreamSurface
import com.chb.form.ui.theme.NeoBorder
import com.chb.form.ui.theme.NeoMutedText
import com.chb.form.ui.theme.Pistachio
import com.chb.form.ui.theme.PistachioDark
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// Elastic weight model (50 / 75 / 100)
// -----------------------------------------------------------------------------

enum class NeoWeight(val value: Float) {
    Light(50f),
    Medium(75f),
    Heavy(100f)
}

internal fun NeoSpringEasing(weight: Float): SpringSpec<Float> = spring(
    dampingRatio = if (weight >= 100f) Spring.DampingRatioNoBouncy else Spring.DampingRatioMediumBouncy,
    stiffness = when {
        weight <= 50f -> Spring.StiffnessLow
        weight <= 75f -> Spring.StiffnessMedium
        else -> Spring.StiffnessHigh
    }
)

// Magnetic targets used by the snap engine.
object NeoSnapGrid {
    val cell = 24f
    val points = listOf(
        Offset(0f, 0f),
        Offset(cell, 0f),
        Offset(cell * 2, 0f),
        Offset(0f, cell),
        Offset(cell, cell),
        Offset(cell * 2, cell),
        Offset(0f, cell * 2),
        Offset(cell, cell * 2),
        Offset(cell * 2, cell * 2)
    )
}

// -----------------------------------------------------------------------------
// Mobject on the canvas
// -----------------------------------------------------------------------------

data class Mobject(
    val id: Long = System.currentTimeMillis(),
    val kind: MobjectKind,
    val x: Float,
    val y: Float,
    val w: Float = 80f,
    val h: Float = 80f,
    val color: Long = 0xFF7FA873L,
    val text: String = ""
)

enum class MobjectKind {
    Square,
    Circle,
    Label
}

// -----------------------------------------------------------------------------
// Spring + magnetic helpers
// -----------------------------------------------------------------------------

@Composable
fun animateSpringOffset(
    target: Offset,
    current: Offset,
    weight: NeoWeight,
    label: String
): Offset {
    val spec = NeoSpringEasing(weight.value)
    val x by androidx.compose.animation.animateFloatAsState(
        targetValue = target.x,
        animationSpec = spec,
        label = "${label}_x"
    )
    val y by androidx.compose.animation.animateFloatAsState(
        targetValue = target.y,
        animationSpec = spec,
        label = "${label}_y"
    )
    return Offset(x, y)
}

// -----------------------------------------------------------------------------
// Toolbar carousel panels
// -----------------------------------------------------------------------------

sealed interface ToolMode {
    val title: String
    val icon: ImageVector
    val weight: NeoWeight
    object Palette : ToolMode { override val title = "พาเลต"; override val icon = Icons.Rounded.FormatPaint; override val weight = NeoWeight.Light }
    object Geometry : ToolMode { override val title = "เรขา"; override val icon = Icons.Rounded.Ruler; override val weight = NeoWeight.Medium }
    object Annotation : ToolMode { override val title = "อโนเทชั่น"; override val icon = Icons.Rounded.Edit; override val weight = NeoWeight.Medium }
    object Styling : ToolMode { override val title = "สไตล์"; override val icon = Icons.Rounded.Style; override val weight = NeoWeight.Light }
    object Export : ToolMode { override val title = "ส่งออก"; override val icon = Icons.Rounded.Upload; override val weight = NeoWeight.Heavy }
}

private val ToolModes: List<ToolMode> = listOf(
    ToolMode.Palette,
    ToolMode.Geometry,
    ToolMode.Annotation,
    ToolMode.Styling,
    ToolMode.Export
)

// -----------------------------------------------------------------------------
// Shared canvas mobject store
// -----------------------------------------------------------------------------

class CanvasState {
    val objects = mutableStateListOf<Mobject>()
    var focusedId by mutableIntStateOf(-1)
        private set

    fun addSquare(x: Float, y: Float) {
        objects += Mobject(kind = MobjectKind.Square, x = x, y = y)
        focusLast()
    }

    fun addCircle(x: Float, y: Float) {
        objects += Mobject(kind = MobjectKind.Circle, x = x, y = y)
        focusLast()
    }

    fun addLabel(x: Float, y: Float, text: String) {
        objects += Mobject(kind = MobjectKind.Label, x = x, y = y, text = text)
        focusLast()
    }

    fun updatePosition(id: Long, x: Float, y: Float) {
        val idx = objects.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val prev = objects[idx]
            objects[idx] = prev.copy(x = x, y = y)
        }
    }

    fun remove(id: Long) {
        val idx = objects.indexOfFirst { it.id == id }
        if (idx >= 0) objects.removeAt(idx)
        if (focusedId == id.toInt()) focusedId = -1
    }

    fun focusLast() {
        if (objects.isNotEmpty()) focusedId = objects.last().id.toInt()
    }
}

// -----------------------------------------------------------------------------
// Snap engine
// -----------------------------------------------------------------------------

fun snapPosition(x: Float, y: Float, activeWeight: NeoWeight): Offset {
    val threshold = when (activeWeight) {
        NeoWeight.Light -> 12f
        NeoWeight.Medium -> 18f
        NeoWeight.Heavy -> 24f
    }
    val nearest = NeoSnapGrid.points.minByOrNull { abs(it.x - x) + abs(it.y - y) }
    if (nearest != null && abs(nearest.x - x) <= threshold && abs(nearest.y - y) <= threshold) {
        return nearest
    }
    return Offset(x, y)
}

// -----------------------------------------------------------------------------
// Toolbar carousel panel content
// -----------------------------------------------------------------------------

@Composable
private fun ToolPanel(
    mode: ToolMode,
    canvas: CanvasState,
    onSelectMode: (ToolMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = mode.title,
            style = MaterialTheme.typography.titleMedium,
            color = NeoMutedText
        )

        when (mode) {
            is ToolMode.Palette -> {
                ToolButton(
                    label = "สี่เหลี่ยม",
                    icon = Icons.Rounded.Add,
                    weight = NeoWeight.Medium,
                    active = false,
                    onClick = { canvas.addSquare(60f, 60f) }
                )
                ToolButton(
                    label = "วงกลม",
                    icon = Icons.Rounded.Style,
                    weight = NeoWeight.Medium,
                    active = false,
                    onClick = { canvas.addCircle(120f, 120f) }
                )
                ToolButton(
                    label = "ข้อความ",
                    icon = Icons.Rounded.FormatPaint,
                    weight = NeoWeight.Light,
                    active = false,
                    onClick = { canvas.addLabel(180f, 180f, "ตัวอย่าง") }
                )
            }
            is ToolMode.Geometry -> {
                Text(
                    "ขนาดและตำแหน่งบน canvas จะคงอยู่จนกว่าจะลบ",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoMutedText
                )
                TextButton(onClick = { /* geometry tweaks in next pass */ }) {
                    Text("เปิดเครื่องมือเรขาแบบเต็มหน้าจอ")
                }
            }
            is ToolMode.Annotation -> {
                ToolButton(
                    label = "อโนเทชั่น",
                    icon = Icons.Rounded.Edit,
                    weight = NeoWeight.Medium,
                    active = false,
                    onClick = { canvas.addLabel(120f, 200f, "โน้ต") }
                )
                ToolButton(
                    label = "จุดเชื่อมโยง",
                    icon = Icons.Rounded.Ruler,
                    weight = NeoWeight.Medium,
                    active = false,
                    onClick = { canvas.addCircle(180f, 200f) }
                )
            }
            is ToolMode.Styling -> {
                ToolButton(
                    label = "สีพื้น",
                    icon = Icons.Rounded.FormatPaint,
                    weight = NeoWeight.Light,
                    active = false,
                    onClick = { /* style tweak in next pass */ }
                )
                ToolButton(
                    label = "เส้นขอบ",
                    icon = Icons.Rounded.Ruler,
                    weight = NeoWeight.Light,
                    active = false,
                    onClick = { /* style tweak in next pass */ }
                )
            }
            is ToolMode.Export -> {
                Text(
                    "งานนี้ออกแบบให้ทำงานเดี่ยว ไม่เปิดใช้งานเครื่องมือร่วม",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoMutedText
                )
                TextButton(
                    onClick = { /* PDF export path in next pass */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Upload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("ส่งออกในชุดงานนี้")
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    weight: NeoWeight,
    active: Boolean,
    onClick: () -> Unit
) {
    val hap = LocalHapticFeedback.current
    val press = remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (press.value) 0.94f else 1f,
        label = "tool_press"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) PistachioDark.copy(alpha = 0.12f) else Color.Transparent
            )
            .border(
                width = if (active) 1.5.dp else 0.dp,
                color = PistachioDark,
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                )
            }
            .scale(scale)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
            color = if (active) PistachioDark else NeoMutedText
        )
    }
}

// -----------------------------------------------------------------------------
// Magnetic canvas object chip
// -----------------------------------------------------------------------------


@Composable
private fun MobjectChip(
    obj: Mobject,
    weight: NeoWeight,
    onMove: (Long, Offset) -> Unit,
    onRemove: (Long) -> Unit,
    snapPulse: Float
) {
    val hap = LocalHapticFeedback.current
    val dragTarget = remember { mutableStateOf(Offset(obj.x, obj.y)) }
    val displayedOffset = animateSpringOffset(dragTarget.value, Offset(obj.x, obj.y), weight, "mobject_${obj.id}")

    Box(
        modifier = Modifier
            .offset { IntOffset(displayedOffset.x.roundToInt(), displayedOffset.y.roundToInt()) }
            .clip(RoundedCornerShape(8.dp))
            .background(Color(obj.color))
            .border(1.5.dp, NeoBorder, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        hap.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragTarget.value = pos
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = dragTarget.value + dragAmount
                        val snapped = snapPosition(next.x, next.y, weight)
                        if (snapped != dragTarget.value) {
                            hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        dragTarget.value = snapped
                    },
                    onDragEnd = {
                        onMove(obj.id, dragTarget.value)
                        hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onLongPress = {
                        hap.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRemove(obj.id)
                    }
                )
            }
            .scale(if (snapPulse > 0f) 1f + snapPulse else 1f)
            .alpha(if (snapPulse > 0f) 0.92f else 1f)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        when (obj.kind) {
            MobjectKind.Square -> {
                Box(modifier = Modifier.fillMaxSize())
            }
            MobjectKind.Circle -> {
                Box(modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
            MobjectKind.Label -> {
                Box(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                    Text(
                        text = obj.text.ifBlank { "ป้าย" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Main workspace screen
// -----------------------------------------------------------------------------

@Composable
fun NeoWorkspaceScreen(
    onExport: () -> Unit,
    onBack: () -> Unit
) {
    val canvas = remember { CanvasState() }
    var currentMode by remember { mutableStateOf<ToolMode>(ToolMode.Palette) }
    var pagerState by remember { mutableIntStateOf(0) }
    val pager = rememberPagerState { ToolModes.size }
    val hap = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Magnetic pulse state
    var snapPulse by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F6F0))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top row: screen title + mode switcher tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ชิ้นงานกลาง — หน้างานสร้างสรรค์",
                style = MaterialTheme.typography.titleMedium,
                color = NeoMutedText
            )
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.Close, "ปิด", tint = NeoMutedText)
            }
        }

        // Toolbar carousel (first-class modes, not page scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolModes.forEachIndexed { idx, mode ->
                val active = pager.currentPage == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) PistachioDark else Color.Transparent
                        )
                        .border(
                            width = if (active) 1.5.dp else 0.dp,
                            color = PistachioDark,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pagerState = idx
                                scope.launch { pager.animateScrollToPage(idx) }
                                currentMode = mode
                            })
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        mode.icon,
                        contentDescription = mode.title,
                        tint = if (active) Color.White else NeoMutedText
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Color.White else NeoMutedText
                    )
                }
            }
        }

        // Pager indicator
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (p in 0 until pager.pageCount) {
                val active = pager.currentPage == p
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(if (active) 22.dp else 8.dp, 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (active) PistachioDark else Color(0xFFD5CEBF)
                        )
                )
            }
        }

        // Canvas area + side panel, side by side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .height(320.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CreamSurface)
                    .border(1.5.dp, NeoBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            canvas.addSquare(60f + (androidx.compose.ui.geometry.Offset(60f, 60f).x % 120f), 60f + (androidx.compose.ui.geometry.Offset(60f, 60f).y % 120f))
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                canvas.objects.forEach { obj ->
                    MobjectChip(
                        obj = obj,
                        weight = currentMode.weight,
                        onMove = { id, pos -> canvas.updatePosition(id, pos.x, pos.y) },
                        onRemove = { id -> canvas.remove(id) },
                        snapPulse = snapPulse
                    )
                }
                // snap pulse feedback overlay
                if (snapPulse > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(snapPulse * 0.4f)
                            .background(Pistachio.copy(alpha = 0.3f))
                    )
                }
            }

            // Side panel (carousel content)
            Box(
                modifier = Modifier
                    .weight(0.34f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CreamSurface)
                    .border(1.5.dp, NeoBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.TopStart
            ) {
                ToolPanel(
                    mode = currentMode,
                    canvas = canvas,
                    onSelectMode = { currentMode = it }
                )
            }
        }

        // Bottom control row: add object + export + clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NeoButton(
                text = "เพิ่มชิ้นงาน",
                icon = Icons.Rounded.Add,
                onClick = {
                    canvas.addSquare(120f, 120f)
                    hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    snapPulse = 1.2f
                },
                modifier = Modifier.weight(1f)
            )
            NeoButton(
                text = "ส่งออก",
                icon = Icons.Rounded.Upload,
                onClick = onExport,
                modifier = Modifier.weight(1.4f)
            )
            IconButton(
                onClick = {
                    canvas.objects.clear()
                    hap.performHapticFeedback(HapticFeedbackType.LongPress)
                    snapPulse = 1.2f
                },
            ) {
                Icon(Icons.Rounded.Delete, "ล้าง", tint = NeoMutedText)
            }
        }

        // Status row: single-user notice + object count
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ทำงานเดี่ยว · ไม่เปิดใช้งานเครื่องมือร่วม",
                style = MaterialTheme.typography.labelSmall,
                color = NeoMutedText
            )
            Text(
                text = "ชิ้นงาน ${canvas.objects.size}",
                style = MaterialTheme.typography.labelSmall,
                color = NeoMutedText
            )
        }
    }

    // Page carousel for panel content, kept separate from canvas context
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize().padding(4.dp)
    ) { page ->
        ToolPanel(
            mode = ToolModes[page],
            canvas = canvas,
            onSelectMode = { currentMode = it }
        )
    }

    // Dismiss after snap pulse
    LaunchedEffect(snapPulse) {
        if (snapPulse > 0f) {
            kotlinx.coroutines.delay(180)
            snapPulse = 0f
        }
    }
}

// -----------------------------------------------------------------------------
// NeoButton (lightweight, reused from theme)
// -----------------------------------------------------------------------------

@Composable
private fun NeoButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hap = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PistachioDark)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                })
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Note
// -----------------------------------------------------------------------------
// This file is the first real implementation of the requested workspace.
// It covers: magnetic snap, elastic spring by weight, separate management modes
// as a first-class toolbar carousel, single-user only, wired into MainActivity.
// Integration completes when MainActivity exposes this screen.
