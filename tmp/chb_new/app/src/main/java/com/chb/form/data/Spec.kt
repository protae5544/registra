package com.chb.form.data
/*ระบบพิกัดต้นฉบับ 1785 × 2576 หน่วย — ค่าทุกตัวยกมาจากไฟล์เดิมโดยไม่แก้ */
object Spec { const val W = 1785f
const val H = 2576f
const val MM_W = 210f
const val MM_H = 303.06f
const val STROKE = 3.6f
const val DOT_W = 4.2f
const val DOT_GAP = 8f
const val CHK = 5f
const val ROW_H = 70f //  2.7174% ของ H
const val VAL_FS = 30f // ขนาดตัวอักษรที่ผู้ใช้กรอก
/*กรอบหลัก: x, y, w, h */
val frames = listOf( floatArrayOf(1258f, 206f, 441f, 506f), floatArrayOf(422f, 253f, 814f, 458f), floatArrayOf(107f, 362f, 290f, 363f), floatArrayOf(1078f, 2034f, 620f, 256f) )
/*ช่องติ๊ก 19 ช่อง (ลำดับเดิม) */
val boxes = listOf( floatArrayOf(1264f, 264f, 35f, 25f), //  0 งานบนที่สูง
floatArrayOf(1265f, 305f, 35f, 24f), //  1 ประกายไฟ
floatArrayOf(1265f, 352f, 35f, 24f), //  2 Spotter
floatArrayOf(1264f, 396f, 36f, 24f), //  3 Lifting
floatArrayOf(1265f, 438f, 35f, 25f), //  4 นั่งร้าน
floatArrayOf(160f, 454f, 34f, 25f), //  5 พื้นที่ CHB1A
floatArrayOf(1264f, 482f, 36f, 25f), //  6 เฝ้าระวังไฟ
floatArrayOf(159f, 514f, 36f, 25f), //  7 พื้นที่ CHB HUB
floatArrayOf(1265f, 562f, 35f, 26f), //  8 ผู้ให้สัญญาณรถ
floatArrayOf(161f, 575f, 34f, 24f), //  9 พื้นที่ CHB 2 A
floatArrayOf(1265f, 603f, 35f, 25f), //  10 PTW
floatArrayOf(159f, 625f, 34f, 27f), //  11 พื้นที่ CHB 3 A
floatArrayOf(1265f, 647f, 36f, 25f), //  12 หลักสูตรอื่นๆ
floatArrayOf(463f, 1505f, 47f, 35f), //  13 ไม่เคย
floatArrayOf(820f, 1506f, 42f, 33f), //  14 เคย
floatArrayOf(645f, 1763f, 42f, 31f), //  15 ธุรกิจส่วนตัว
floatArrayOf(1122f, 1765f, 45f, 32f), //  16 อื่นๆ
floatArrayOf(242f, 1766f, 43f, 25f), //  17 ก่อสร้าง
floatArrayOf(242f, 1834f, 43f, 28f) //  18 ไม่ได้ทำงาน
)
/*เส้นประ: x1, y, x2 */
val dotted = listOf( floatArrayOf(1491.5f, 669.5f, 1536f), floatArrayOf(1351.5f, 669.9f, 1486f), floatArrayOf(502.5f, 849.3f, 1224f), floatArrayOf(445.5f, 912.5f, 1223f), floatArrayOf(578f, 975.1f, 1221f), floatArrayOf(508f, 1038.1f, 1228f), floatArrayOf(764.5f, 1163.4f, 1498.5f), floatArrayOf(1028f, 1226.4f, 1506f), floatArrayOf(569.5f, 1289.2f, 1509.5f), floatArrayOf(610f, 1666.8f, 815.5f), floatArrayOf(1323.5f, 1793.5f, 1477f), floatArrayOf(1086f, 1984.4f, 1350.5f), floatArrayOf(1228.5f, 2168.7f, 1620f), floatArrayOf(1250f, 2249.3f, 1619.5f) )
/*ช่องเติมข้อความ 14 ช่อง: x1, baselineY, x2 */
val fields = listOf( floatArrayOf(1351.5f, 669.7f, 1536f), floatArrayOf(502.5f, 849.3f, 1224f), floatArrayOf(445.5f, 912.5f, 1223f), floatArrayOf(578f, 975.1f, 1221f), floatArrayOf(508f, 1038.1f, 1228f), floatArrayOf(784f, 1100.8f, 1226f), floatArrayOf(764.5f, 1163.4f, 1498.5f), floatArrayOf(1028f, 1226.4f, 1506f), floatArrayOf(569.5f, 1289.2f, 1509.5f), floatArrayOf(610f, 1666.8f, 815.5f), floatArrayOf(1323.5f, 1793.5f, 1477f), floatArrayOf(1086f, 1984.4f, 1350.5f), floatArrayOf(1228.5f, 2168.7f, 1620f), floatArrayOf(1250f, 2249.3f, 1619.5f) )
/*กรอบรูปบัตร: left, top, w, h */
val card = floatArrayOf(0.235294f * W, 0.097438f * H, 0.458263f * W, 0.179348f * H)
/*พื้นที่ว่างสำหรับลายเซ็น (ส่วนเสริม — ปิดได้) */
val signature = floatArrayOf(150f, 2310f, 620f, 190f)
data class T(val l: Float, val t: Float, val w: Float, val html: String)
val texts = listOf( T(7.2269f, 14.0334f, 174f, "พื้นที่ปฏิบัติงาน :"), T(12.8852f, 16.9449f, 75f, "<b>CHB1A</b>"), T(12.8852f, 19.1188f, 101f, "<b>CHB HUB</b>"), T(12.8291f, 21.3315f, 86f, "<b>CHB 2 A</b>"), T(12.8852f, 23.5248f, 86f, "<b>CHB 3 A</b>"), T(74.1737f, 7.8028f, 309f, "<b><u>หลักสูตรเฉพาะที่ต้องการอบรม</u></b>"), T(73.2213f, 9.4138f, 94f, "<b>งานบนที่สูง</b>"), T(73.2213f, 11.0248f, 294f, "<b>งานที่มีประกายไฟ (ตัด เชื่อม เจียร )</b>"), T(73.2773f, 12.8882f, 216f, "<b>ผู้ให้สัญญาณรถ</b> (Spotter)"), T(73.2213f, 14.5380f, 331f, "<b>งานยก</b> Lifting <small>(<b>สำหรับพนักงานที่มี</b> Cer. <b>แล้ว</b>)</small>"), T(73.2213f, 16.0908f, 355f, "<b>งานติดตั้งนั่งร้าน</b> <small>(<b>สำหรับพนักงานที่มี</b> Cor. <b>แล้ว</b>)</small>"), T(73.2773f, 17.9542f, 358f, "<b>ผู้เฝ้าระวังไฟ</b> <small>(<b>สำหรับพนักงานที่มี</b> Cor. <b>ดับเพลิง-</b></small>"), T(71.6527f, 19.4099f, 222f, "<small><b>ขั้นต้น หรือ</b> Cer.<b>ผู้เฝ้าระวังไฟแล้ว)</b></small>"), T(73.2773f, 21.0210f, 132f, "<b>ผู้ให้สัญญาณรถ</b>"), T(73.2773f, 22.7484f, 194f, "<b>PTW</b> (Permit to Work)"), T(73.2213f, 24.3207f, 35f, "<b>อื่นๆ</b>"), T(14.7339f, 31.3082f, 227f, "<b>1.บริษัท</b> Company"), T(14.6218f, 33.6374f, 186f, "<b>2.เบอร์โทร</b> Tel :"), T(14.7339f, 36.1801f, 317f, "<b>3.กรุ๊ปเลือด</b> Blood group :"), T(14.7339f, 38.4705f, 247f, "<b>4.ตำแหน่ง</b> Position :"), T(14.7899f, 41.0520f, 512f, "<b>5.กรณีฉุกเฉินติดต่อ</b> Emergency contract :"), T(20.2801f, 43.3424f, 404f, "<b>5.1 ชื่อ-สกุล</b> Name – Last name :"), T(20.3361f, 45.6522f, 667f, "<b>5.2 ความสัมพันธ์เกี่ยวข้องเป็นอะไรกัน</b> Relationship as :"), T(20.3922f, 48.2143f, 207f, "<b>5.3 เบอร์โทร</b> Tel :"), T(14.7899f, 50.6017f, 603f, "<b>หมายเหตุ เฉพาะพ่อแม่ พี่ น้อง สามี ภรรยา เท่านั้น</b>"), T(14.8459f, 53.3385f, 719f, "Note: Applicable only to father, mother, siblings, spouse."), T(14.8459f, 55.6289f, 1171f, "<b>6.คุณเคยทำงานในงานก่อสร้างหรือไม่</b> Do you have any experience working in the construction"), T(14.7899f, 58.2104f, 115f, "industry?"), T(29.7479f, 57.9969f, 248f, "<b>ไม่เคย</b> No, I haven't."), T(49.1877f, 58.0939f, 178f, "<b>เคย</b> have ever"), T(60.5042f, 58.0357f, 412f, "<b>ตอบข้อ 6.1</b> Answer question 6.1"), T(16.1345f, 60.3261f, 1244f, "<b>6.1กรณีเคยทำงานทำมาแล้ว ทำมาแล้วกี่เดือน, ปี</b> If you have work experience, how many months or"), T(14.7339f, 63.1211f, 310f, "years have you worked?"), T(14.7899f, 65.2368f, 769f, "<b>7. ก่อนที่จะมาทำงานในโครงการนี้คุณทำงานเกี่ยวกับอะไรมาก่อน</b>"), T(16.5826f, 67.7989f, 262f, "<b>ก่อสร้าง</b> Construction"), T(39.4398f, 67.9736f, 335f, "<b>ธุรกิจส่วนตัว</b> Own business"), T(66.3866f, 67.7989f, 134f, "<b>อื่นๆ</b> Other"), T(16.5266f, 70.3028f, 714f, "<b>ไม่ได้ทำงาน</b> not working. <b>ตอบข้อ 7.1</b> Answer question 7.1"), T(16.6387f, 72.6708f, 1191f, "<b>7.1 กรณีไม่ได้ทำงาน คุณหยุดงานสายอาชีพนี้ไปแล้วกี่เดือน ,ปี</b> <small>If you are currently unemployed, please</small>"), T(14.9020f, 75.4852f, 806f, "indicate how many months or years you have been out of this profession"), T(63.6415f, 79.4061f, 211f, "<b>เฉพาะพนักงาน</b> <small>CN</small>"), T(63.5294f, 82.4340f, 86f, "<b>โฟร์แมน</b> :"), T(63.5854f, 85.6949f, 103f, "<b>หัวหน้าชุด</b> :") ) }
