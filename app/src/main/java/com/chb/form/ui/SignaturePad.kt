package com.chb.form.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun SignaturePad(
    onSignatureChange: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier
) {
    var points by remember { mutableStateOf(listOf<Offset>()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var paths by remember { mutableStateOf(listOf<Path>()) }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = Path().apply { moveTo(offset.x, offset.y) }
                            points = points + offset
                        },
                        onDrag = { change, _ ->
                            currentPath.lineTo(change.position.x, change.position.y)
                            points = points + change.position
                        },
                        onDragEnd = {
                            paths = paths + currentPath
                            onSignatureChange(points)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                paths.forEach { p ->
                    drawPath(p, Color.Black, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                drawPath(currentPath, Color.Black, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            points = emptyList()
            paths = emptyList()
            currentPath = Path()
            onSignatureChange(emptyList())
        }) { Text("ล้างลายเซ็น") }
    }
}
