package com.chb.form.data
/*index ของช่องกรอกข้อความ */
object F { const val COURSE_OTHER = 0
const val COMPANY = 1
const val TEL = 2
const val BLOOD = 3
const val POSITION = 4
const val RESERVED = 5
const val EMG_NAME = 6
const val EMG_REL = 7
const val EMG_TEL = 8
const val EXP_DURATION = 9
const val PREV_OTHER = 10
const val GAP_DURATION = 11
const val FOREMAN = 12
const val LEADER = 13
}
data class Opt(val idx: Int, val title: String, val note: String? = null)
object Content { /*เลือกได้พื้นที่เดียว */
val AREAS = listOf( Opt(5, "CHB1A"), Opt(7, "CHB HUB"), Opt(9, "CHB 2 A"), Opt(11, "CHB 3 A") )
val AREA_IDS = AREAS.map { it.idx
}
/*เลือกได้หลายหลักสูตร */
val COURSES = listOf( Opt(0, "งานบนที่สูง"), Opt(1, "งานที่มีประกายไฟ", "ตัด เชื่อม เจียร"), Opt(2, "ผู้ให้สัญญาณรถ", "Spotter"), Opt(3, "งานยก Lifting", "สำหรับพนักงานที่มี Cer. แล้ว"), Opt(4, "งานติดตั้งนั่งร้าน", "สำหรับพนักงานที่มี Cor. แล้ว"), Opt(6, "ผู้เฝ้าระวังไฟ", "สำหรับพนักงานที่มี Cor. ดับเพลิงขั้นต้น หรือ Cer. ผู้เฝ้าระวังไฟแล้ว"), Opt(8, "ผู้ให้สัญญาณรถ"), Opt(10, "PTW", "Permit to Work"), Opt(12, "อื่นๆ", "ระบุเพิ่มเติมด้านล่าง") )
const val COURSE_OTHER_BOX = 12
/*ข้อ 6 — เลือกได้ข้อเดียว */
val EXPERIENCE = listOf(Opt(13, "ไม่เคย", "No, I haven't."), Opt(14, "เคย", "have ever"))
val EXPERIENCE_IDS = EXPERIENCE.map { it.idx
}
const val EXP_YES = 14
/*ข้อ 7 — เลือกได้ข้อเดียว */
val PREVIOUS = listOf( Opt(17, "ก่อสร้าง", "Construction"), Opt(15, "ธุรกิจส่วนตัว", "Own business"), Opt(16, "อื่นๆ", "Other"), Opt(18, "ไม่ได้ทำงาน", "not working") )
val PREVIOUS_IDS = PREVIOUS.map { it.idx
}
const val PREV_OTHER_BOX = 16
const val PREV_NONE_BOX = 18
val BLOOD_GROUPS = listOf("A", "B", "AB", "O")
val RELATIONS = listOf("บิดา", "มารดา", "พี่", "น้อง", "สามี", "ภรรยา") }
