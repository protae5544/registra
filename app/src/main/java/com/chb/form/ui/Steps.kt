package com.chb.form.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chb.form.data.*
import com.chb.form.vm.FormViewModel
import java.io.File
/* ---------- ขั้น 1: พื้นที่ + หลักสูตร ---------- */
@Composable
fun StepArea(s: FormData, vm: FormViewModel) { SectionTitle("พื้นที่ปฏิบัติงาน", "เลือกได้ 1 พื้นที่")
Content.AREAS.forEach { o -> ChoiceCard(o.title, o.note, s.check(o.idx)) { vm.pickOne(Content.AREA_IDS, o.idx) }
Spacer(Modifier.height(8.dp)) }
SectionTitle("หลักสูตรเฉพาะที่ต้องการอบรม", "เลือกได้หลายรายการ")
Content.COURSES.forEach { o -> ChoiceCard(o.title, o.note, s.check(o.idx)) { vm.toggle(o.idx) }
Spacer(Modifier.height(8.dp)) }
if (s.check(Content.COURSE_OTHER_BOX)) { Field("ระบุหลักสูตรอื่นๆ", s.field(F.COURSE_OTHER), { vm.setField(F.COURSE_OTHER, it) }, icon = Icons.Rounded.EditNote) } }
/* ---------- ขั้น 2: บัตรประชาชน ---------- */
@Composable
fun StepCard(s: FormData, vm: FormViewModel, onCamera: () -> Unit) { SectionTitle("บัตรประชาชน", "วางบัตรให้เต็มกรอบ แสงสม่ำเสมอ ไม่มีเงาทับ")
Card( onClick = onCamera, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().aspectRatio(85.6f / 54f) //  สัดส่วนบัตรจริง
) { if (s.cardPath != null) { Box(Modifier.fillMaxSize()) { AsyncImage( File(s.cardPath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop )
FilledTonalIconButton( { vm.clearCard() }, Modifier.align(Alignment.TopEnd).padding(8.dp) ) { Icon(Icons.Rounded.Close, "ลบรูป") } } } else { Column( Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally ) { Icon(Icons.Rounded.CreditCard, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
Spacer(Modifier.height(10.dp))
Text("แตะเพื่อถ่ายรูปบัตร", style = MaterialTheme.typography.titleMedium)
Text("ระบบจะอ่านชื่อ-สกุลให้อัตโนมัติ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
if (s.cardPath != null) { Spacer(Modifier.height(8.dp))
TextButton(onCamera) { Icon(Icons.Rounded.Refresh, null);
Spacer(Modifier.width(6.dp));
Text("ถ่ายใหม่") } } }
/* ---------- ขั้น 3: ข้อมูลทั่วไป ---------- */
@Composable
fun StepInfo(s: FormData, vm: FormViewModel) { SectionTitle("ข้อมูลทั่วไป")
Field("บริษัท / Company", s.field(F.COMPANY), { vm.setField(F.COMPANY, it) }, icon = Icons.Rounded.Business)
Field("เบอร์โทร / Tel", s.field(F.TEL), { vm.setField(F.TEL, it.filter(Char::isDigit).take(10)) }, KeyboardType.Phone, Icons.Rounded.Phone, error = if (s.field(F.TEL).isNotEmpty() && s.field(F.TEL).length < 9) "เบอร์ยังไม่ครบ" else null)
Text("กรุ๊ปเลือด / Blood group", style = MaterialTheme.typography.labelLarge)
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { Content.BLOOD_GROUPS.forEach { g -> FilterChip( selected = s.field(F.BLOOD) == g, onClick = { vm.setField(F.BLOOD, if (s.field(F.BLOOD) == g) "" else g) }, label = { Text(g) }, modifier = Modifier.weight(1f) ) } }
Field("ตำแหน่ง / Position", s.field(F.POSITION), { vm.setField(F.POSITION, it) }, icon = Icons.Rounded.Badge)
Spacer(Modifier.height(6.dp))
SectionTitle("ข้อ 6 — ประสบการณ์งานก่อสร้าง", "คุณเคยทำงานในงานก่อสร้างหรือไม่")
Content.EXPERIENCE.forEach { o -> ChoiceCard(o.title, o.note, s.check(o.idx)) { vm.pickOne(Content.EXPERIENCE_IDS, o.idx) }
Spacer(Modifier.height(8.dp)) }
if (s.check(Content.EXP_YES)) { Field("6.1 ทำมาแล้วกี่เดือน / ปี", s.field(F.EXP_DURATION), { vm.setField(F.EXP_DURATION, it) }, icon = Icons.Rounded.Schedule) }
Spacer(Modifier.height(6.dp))
SectionTitle("ข้อ 7 — งานก่อนหน้า", "ก่อนมาโครงการนี้คุณทำงานเกี่ยวกับอะไร")
Content.PREVIOUS.forEach { o -> ChoiceCard(o.title, o.note, s.check(o.idx)) { vm.pickOne(Content.PREVIOUS_IDS, o.idx) }
Spacer(Modifier.height(8.dp)) }
if (s.check(Content.PREV_OTHER_BOX)) { Field("ระบุงานอื่นๆ", s.field(F.PREV_OTHER), { vm.setField(F.PREV_OTHER, it) }, icon = Icons.Rounded.EditNote) }
if (s.check(Content.PREV_NONE_BOX)) { Field("7.1 หยุดงานสายอาชีพนี้กี่เดือน / ปี", s.field(F.GAP_DURATION), { vm.setField(F.GAP_DURATION, it) }, icon = Icons.Rounded.HourglassEmpty) } }
/* ---------- ขั้น 4: ผู้ติดต่อฉุกเฉิน ---------- */
@Composable
fun StepEmergency(s: FormData, vm: FormViewModel) { SectionTitle("ผู้ติดต่อกรณีฉุกเฉิน", "เฉพาะพ่อแม่ พี่ น้อง สามี ภรรยา เท่านั้น")
Field("5.1 ชื่อ-สกุล", s.field(F.EMG_NAME), { vm.setField(F.EMG_NAME, it) }, icon = Icons.Rounded.Person)
Text("5.2 ความสัมพันธ์", style = MaterialTheme.typography.labelLarge)
FlowRowChips(Content.RELATIONS, s.field(F.EMG_REL)) { vm.setField(F.EMG_REL, it) }
Field("หรือระบุเอง", s.field(F.EMG_REL), { vm.setField(F.EMG_REL, it) }, icon = Icons.Rounded.Diversity3)
Field("5.3 เบอร์โทร", s.field(F.EMG_TEL), { vm.setField(F.EMG_TEL, it.filter(Char::isDigit).take(10)) }, KeyboardType.Phone, Icons.Rounded.ContactPhone, error = if (s.field(F.EMG_TEL).isNotEmpty() && s.field(F.EMG_TEL).length < 9) "เบอร์ยังไม่ครบ" else null)
Spacer(Modifier.height(10.dp))
SectionTitle("เฉพาะพนักงาน CN", "กรอกเมื่อมีข้อมูล")
Field("โฟร์แมน", s.field(F.FOREMAN), { vm.setField(F.FOREMAN, it) }, icon = Icons.Rounded.Engineering)
Field("หัวหน้าชุด", s.field(F.LEADER), { vm.setField(F.LEADER, it) }, icon = Icons.Rounded.Groups) }
@Composable
private fun FlowRowChips(items: List<String>, selected: String, onPick: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { items.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { row.forEach { r -> FilterChip(selected == r, { onPick(if (selected == r) "" else r) }, { Text(r) }, modifier = Modifier.weight(1f)) }
repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } } } }
/* ---------- ขั้น 5: ตรวจสอบ ---------- (เพิ่ม Dummy ให้คอมไพล์ผ่าน) */
@Composable
fun StepReview(s: FormData, vm: FormViewModel, onSign: () -> Unit) { SectionTitle("ตรวจสอบข้อมูล")
Text("ตรวจสอบข้อมูลก่อนสร้าง PDF")
Button(onClick = onSign) { Text("เพิ่มลายเซ็น") } }
