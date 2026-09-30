package com.chb.form.ui
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
@Composable
fun SignaturePad(onDone: (Bitmap) -> Unit, onCancel: () -> Unit) { val strokes = remember { mutableStateListOf<List<Offset>>() }
var current by remember { mutableStateOf(listOf<Offset>()) }
var canvasW by remember { mutableIntStateOf(1) }
var canvasH by remember { mutableIntStateOf(1) }
Column(Modifier.fillMaxSize().padding(16.dp)) { Text("เซ็นชื่อ", style = MaterialTheme.typography.titleLarge)
Text("ใช้นิ้วเซ็นในกรอบด้านล่าง", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
Spacer(Modifier.height(12.dp))
Box( Modifier.weight(1f).fillMaxWidth() .clip(RoundedCornerShape(16.dp)) .background(Color.White) ) { Canvas( Modifier.fillMaxSize().pointerInput(Unit) { detectDragGestures( onDragStart = { current = listOf(it) }, onDrag = { ch, _ -> current = current + ch.position
}, onDragEnd = { if (current.size > 1)
strokes.add(current);
current = emptyList() } ) } ) { canvasW = size.width.toInt()
canvasH = size.height.toInt() (strokes + listOf(current)).forEach { pts -> if (pts.size < 2)
return@forEach
val path = Path().apply { moveTo(pts[0].x, pts[0].y)
for (i in 1 until pts.size)
lineTo(pts[i].x, pts[i].y) }
drawPath(path, Color.Black, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
HorizontalDivider( Modifier.align(Alignment.BottomCenter).padding(horizontal = 32.dp, vertical = 36.dp), color = Color(0xFFBDBDBD) ) }
Spacer(Modifier.height(14.dp))
Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton({ strokes.clear();
current = emptyList() }, Modifier.weight(1f)) { Text("ล้าง") }
OutlinedButton(onCancel, Modifier.weight(1f)) { Text("ยกเลิก") }
Button( enabled = strokes.isNotEmpty(), onClick = { onDone(render(strokes, canvasW, canvasH)) }, modifier = Modifier.weight(1.4f) ) { Text("บันทึก") } } } }
/*เรนเดอร์เป็น Bitmap โปร่งใสความละเอียดสูง เพื่อไม่ให้แตกใน PDF */
private fun render(strokes: List<List<Offset>>, w: Int, h: Int): Bitmap { val scale = 3f
val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
val c = AndroidCanvas(bmp)
c.drawColor(AndroidColor.TRANSPARENT)
val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.BLACK
style = Paint.Style.STROKE
strokeWidth = 5f * scale
strokeCap = Paint.Cap.ROUND
strokeJoin = Paint.Join.ROUND
}
strokes.forEach { pts -> if (pts.size < 2)
return@forEach
val path = AndroidPath().apply { moveTo(pts[0].x * scale, pts[0].y * scale)
for (i in 1 until pts.size)
lineTo(pts[i].x * scale, pts[i].y * scale) }
c.drawPath(path, p) }
return bmp
}
