package com.chb.form.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chb.form.vm.FormViewModel
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormWizard( vm: FormViewModel, onCamera: () -> Unit, onSign: () -> Unit, onExport: () -> Unit ) { val s by vm.state.collectAsStateWithLifecycle()
val pager = rememberPagerState { 5
}
val scope = rememberCoroutineScope()
val steps = listOf("พื้นที่", "บัตร", "ข้อมูล", "ฉุกเฉิน", "ตรวจสอบ")
var confirmReset by remember { mutableStateOf(false) }
Scaffold( topBar = { Column { CenterAlignedTopAppBar( title = { Column { Text("แบบฟอร์มลงทะเบียน", fontWeight = FontWeight.SemiBold)
Text( "กรอกแล้ว ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant ) } }, actions = { IconButton({ confirmReset = true
}) { Icon(Icons.Rounded.RestartAlt, "เริ่มใหม่") } } )
LinearProgressIndicator( progress = { s.progress
}, modifier = Modifier.fillMaxWidth().height(3.dp) )
ScrollableTabRow( selectedTabIndex = pager.currentPage, edgePadding = 12.dp, divider = {} ) { steps.forEachIndexed { i, t -> Tab( selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(t, maxLines = 1) } ) } } } }, bottomBar = { Surface(tonalElevation = 3.dp) { Row( Modifier.fillMaxWidth().navigationBarsPadding() .padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp) ) { if (pager.currentPage > 0) { OutlinedButton( { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, Modifier.weight(1f).height(52.dp) ) { Text("ย้อนกลับ") } }
Button( onClick = { if (pager.currentPage == 4)
onExport() else
scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, modifier = Modifier.weight(1.6f).height(52.dp) ) { if (pager.currentPage == 4) { Icon(Icons.Rounded.PictureAsPdf, null)
Spacer(Modifier.width(8.dp))
Text("สร้าง PDF") } else
Text("ถัดไป") } } } } ) { pad -> HorizontalPager(pager, Modifier.padding(pad).fillMaxSize()) { p -> Column( Modifier.fillMaxSize().verticalScroll(rememberScrollState()) .padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp) ) { Spacer(Modifier.height(4.dp))
when (p) { 0 -> StepArea(s, vm) 1 -> StepCard(s, vm, onCamera) 2 -> StepInfo(s, vm) 3 -> StepEmergency(s, vm) 4 -> StepReview(s, vm, onSign) }
Spacer(Modifier.height(40.dp)) } } }
if (confirmReset) { AlertDialog( onDismissRequest = { confirmReset = false
}, icon = { Icon(Icons.Rounded.WarningAmber, null) }, title = { Text("ล้างข้อมูลทั้งหมด?") }, text = { Text("ข้อมูลที่กรอกไว้และรูปบัตรจะถูกลบ ไม่สามารถกู้คืนได้") }, confirmButton = { TextButton({ vm.reset();
confirmReset = false
}) { Text("ล้างข้อมูล") } }, dismissButton = { TextButton({ confirmReset = false
}) { Text("ยกเลิก") } } ) } }
