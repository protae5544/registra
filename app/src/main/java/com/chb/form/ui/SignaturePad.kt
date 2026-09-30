package com.chb.form.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private const val PEN_PX = 6f

/** หน้าเซ็นลายเซ็นเต็มจอ — กด "บันทึก" จะได้ Bitmap โปร่งใส ครอปพอดีลายเซ็น */
@Composable
fun SignaturePad(onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("เซ็นชื่อในกรอบ", style = MaterialTheme.typography.titleMedium)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start -> current = listOf(start) },
                        onDrag = { change, _ -> current = current + change.position },
                        onDragEnd = {
                            if (current.isNotEmpty()) strokes.add(current)
                            current = emptyList()
                        },
                        onDragCancel = { current = emptyList() }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                strokes.forEach { drawStroke(it) }
                drawStroke(current)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("ยกเลิก")
            }
            OutlinedButton(
                onClick = {
                    strokes.clear()
                    current = emptyList()
                },
                modifier = Modifier.weight(1f).height(52.dp)
            ) { Text("ล้าง") }
            Button(
                onClick = { onDone(renderSignature(strokes.toList(), size.width, size.height)) },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1.4f).height(52.dp)
            ) { Text("บันทึก") }
        }
    }
}

private fun DrawScope.drawStroke(points: List<Offset>) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(Color.Black, radius = PEN_PX / 2f, center = points[0])
        return
    }
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
    drawPath(
        path,
        Color.Black,
        style = Stroke(width = PEN_PX, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/** วาดเส้นลง Bitmap (พื้นโปร่งใส) แล้วครอปเหลือเฉพาะบริเวณลายเซ็น */
private fun renderSignature(strokes: List<List<Offset>>, w: Int, h: Int): Bitmap {
    val full = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(full)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.BLACK
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = PEN_PX
    paint.strokeCap = android.graphics.Paint.Cap.ROUND
    paint.strokeJoin = android.graphics.Paint.Join.ROUND

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = 0f
    var maxY = 0f
    for (pts in strokes) {
        if (pts.isEmpty()) continue
        if (pts.size == 1) {
            canvas.drawPoint(pts[0].x, pts[0].y, paint)
        } else {
            val path = android.graphics.Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            canvas.drawPath(path, paint)
        }
        for (p in pts) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
    }

    val pad = 12
    val x0 = (minX.toInt() - pad).coerceIn(0, full.width - 1)
    val y0 = (minY.toInt() - pad).coerceIn(0, full.height - 1)
    val x1 = (maxX.toInt() + pad).coerceIn(x0 + 1, full.width)
    val y1 = (maxY.toInt() + pad).coerceIn(y0 + 1, full.height)
    return Bitmap.createBitmap(full, x0, y0, x1 - x0, y1 - y0)
}
