package com.chb.form.ui
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chb.form.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(file: File, onClose: () -> Unit) { val ctx = LocalContext.current
var bmp by remember { mutableStateOf<Bitmap?>(null) }
var scale by remember { mutableFloatStateOf(1f) }
var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
LaunchedEffect(file) { bmp = withContext(Dispatchers.IO) { PdfTools.preview(file, 1800) } }
Scaffold( topBar = { TopAppBar( title = { Text("ตัวอย่างเอกสาร") }, navigationIcon = { IconButton(onClose) { Icon(Icons.Rounded.ArrowBack, "กลับ") } }, actions = { IconButton({ PdfTools.print(ctx, file) }) { Icon(Icons.Rounded.Print, "พิมพ์") }
IconButton({ PdfTools.open(ctx, file) }) { Icon(Icons.Rounded.OpenInNew, "เปิด") } } ) }, bottomBar = { Surface(tonalElevation = 3.dp) { Row( Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp) ) { OutlinedButton({ scale = 1f;
offset = androidx.compose.ui.geometry.Offset.Zero
}, Modifier.weight(1f)) { Text("รีเซ็ตซูม") }
Button({ PdfTools.share(ctx, file) }, Modifier.weight(1.6f).height(52.dp)) { Icon(Icons.Rounded.Share, null);
Spacer(Modifier.width(8.dp));
Text("แชร์ไฟล์") } } } } ) { pad -> Box( Modifier.padding(pad).fillMaxSize().background(Color(0xFF3A3A3A)) .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 6f)
offset += pan
} }, contentAlignment = Alignment.Center ) { bmp?.let { Image( it.asImageBitmap(), "ตัวอย่างเอกสาร", Modifier.fillMaxWidth().padding(8.dp) .graphicsLayer( scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y ) ) } ?: CircularProgressIndicator() } } }
