package com.chb.form.data

/**
 * พิกัดและเลย์เอาต์สำหรับแบบคำร้องขออนุญาตเปิดห้องพักคนงาน
 * Christiani & Nielsen - หน่วยงาน COCONUT 1 (ON.11867)
 *
 * ระบบพิกัดอ้างอิง A4 (210mm x 297mm) แปลงเป็นหน่วย logical 1785 x 2520
 * (ปรับสัดส่วนจากต้นฉบับเพื่อให้ฟอนต์และช่องกรอกดูเป็นธรรมชาติมากขึ้น)
 */
object DormSpec {
    const val W = 1785f
    const val H = 2520f          // ปรับให้ใกล้เคียงสัดส่วน A4 มากขึ้น
    const val MM_W = 210f
    const val MM_H = 297f
    const val STROKE = 2.8f
    const val DOT_W = 3.2f
    const val DOT_GAP = 7f
    const val CHK = 4.5f
    const val VAL_FS = 26f       // ขนาดตัวอักษรค่าที่กรอก (ลดลงเล็กน้อยเพื่อไม่ให้อ้วน)
    const val STATIC_FS = 24f    // ขนาดข้อความคงที่

    // กรอบหลักของเอกสาร
    val frames = listOf(
        // กรอบหัวเรื่อง
        floatArrayOf(40f, 40f, 1705f, 160f),
        // กรอบส่วนที่ 1 (ข้อมูลผู้ขอ)
        floatArrayOf(40f, 220f, 1705f, 980f),
        // กรอบส่วนหมายเหตุ + บันทึกผู้ดูแล
        floatArrayOf(40f, 1220f, 1705f, 1260f)
    )

    // ช่องติ๊ก (checkboxes)
    // 0 = บริษัท กริสเตียนีและนีลเส็น (ไทย) จำกัด (มหาชน)
    // 1 = ผู้รับเหมางาน
    // 2 = อื่นๆ (สังกัด)
    val boxes = listOf(
        floatArrayOf(80f, 420f, 32f, 28f),   // 0 บริษัท CN
        floatArrayOf(900f, 420f, 32f, 28f),  // 1 ผู้รับเหมา
        floatArrayOf(80f, 520f, 32f, 28f)    // 2 อื่นๆ
    )

    // เส้นประสำหรับช่องกรอก
    val dotted = listOf(
        // ชื่อผู้ขอ 1
        floatArrayOf(320f, 280f, 1100f),
        floatArrayOf(1280f, 280f, 1680f), // ตำแหน่ง 1
        // ชื่อผู้ขอ 2
        floatArrayOf(320f, 330f, 1100f),
        floatArrayOf(1280f, 330f, 1680f), // ตำแหน่ง 2
        // ชื่อผู้ขอ 3
        floatArrayOf(320f, 380f, 1100f),
        floatArrayOf(1280f, 380f, 1680f), // ตำแหน่ง 3
        // หัวหน้าชุด (CN)
        floatArrayOf(280f, 470f, 700f),
        // โฟร์แมน (CN)
        floatArrayOf(280f, 510f, 700f),
        // หัวหน้าชุด (ผู้รับเหมา)
        floatArrayOf(1100f, 470f, 1600f),
        // หัวหน้าคนงาน
        floatArrayOf(1100f, 510f, 1600f),
        // ผู้ทำสัญญา
        floatArrayOf(1100f, 550f, 1600f),
        // อื่นๆ สังกัด
        floatArrayOf(280f, 560f, 700f),
        // จำนวนวัน
        floatArrayOf(620f, 640f, 720f),
        // ชื่อผู้ค้ำ
        floatArrayOf(380f, 820f, 900f),
        // ตำแหน่งผู้ค้ำ
        floatArrayOf(1050f, 820f, 1400f),
        // บริษัทผู้ค้ำ
        floatArrayOf(1500f, 820f, 1680f),
        // ตั้งแต่วันที่
        floatArrayOf(700f, 870f, 1050f),
        // ถึงวันที่
        floatArrayOf(1200f, 870f, 1550f)
    )

    // ช่องกรอกข้อความ (x1, baselineY, x2)
    val fields = listOf(
        floatArrayOf(320f, 278f, 1100f),   // 0 ชื่อ1
        floatArrayOf(1280f, 278f, 1680f),  // 1 ตำแหน่ง1
        floatArrayOf(320f, 328f, 1100f),   // 2 ชื่อ2
        floatArrayOf(1280f, 328f, 1680f),  // 3 ตำแหน่ง2
        floatArrayOf(320f, 378f, 1100f),   // 4 ชื่อ3
        floatArrayOf(1280f, 378f, 1680f),  // 5 ตำแหน่ง3
        floatArrayOf(280f, 468f, 700f),    // 6 หัวหน้าชุด CN
        floatArrayOf(280f, 508f, 700f),    // 7 โฟร์แมน CN
        floatArrayOf(1100f, 468f, 1600f),  // 8 หัวหน้าชุด ผู้รับเหมา
        floatArrayOf(1100f, 508f, 1600f),  // 9 หัวหน้าคนงาน
        floatArrayOf(1100f, 548f, 1600f),  // 10 ผู้ทำสัญญา
        floatArrayOf(280f, 558f, 700f),    // 11 อื่นๆ
        floatArrayOf(620f, 638f, 720f),    // 12 จำนวนวัน
        floatArrayOf(380f, 818f, 900f),    // 13 ชื่อผู้ค้ำ
        floatArrayOf(1050f, 818f, 1400f),  // 14 ตำแหน่งผู้ค้ำ
        floatArrayOf(1500f, 818f, 1680f),  // 15 บริษัทผู้ค้ำ
        floatArrayOf(700f, 868f, 1050f),   // 16 ตั้งแต่วันที่
        floatArrayOf(1200f, 868f, 1550f),  // 17 ถึงวันที่
        floatArrayOf(1450f, 80f, 1680f),   // 18 ลำดับที่
        floatArrayOf(1450f, 130f, 1680f)   // 19 วันที่
    )

    // พื้นที่สำหรับลายเซ็น
    val signatureApplicant = floatArrayOf(120f, 1050f, 400f, 120f)   // ผู้ขออนุญาต
    val signatureApprover = floatArrayOf(1000f, 1050f, 400f, 120f)   // ผู้อนุญาต

    // พื้นที่รูปบัตร (ถ้ามี)
    val card = floatArrayOf(1400f, 230f, 300f, 180f)

    data class T(val l: Float, val t: Float, val w: Float, val html: String)

    val texts = listOf(
        T(5f, 3f, 400f, "<b>CHRISTIANI & NIELSEN</b>"),
        T(25f, 5f, 900f, "<b>แบบคำร้องขออนุญาตเปิดห้องพักคนงาน</b>"),
        T(25f, 7.5f, 700f, "หน่วยงาน COCONUT 1 (ON.11867)"),
        T(75f, 3f, 200f, "ลำดับที่"),
        T(75f, 5.5f, 200f, "วันที่"),
        T(5f, 10f, 200f, "(1) ข้าพเจ้า"),
        T(8f, 11.5f, 80f, "(1)"),
        T(8f, 13.5f, 80f, "(2)"),
        T(8f, 15.5f, 80f, "(3)"),
        T(5f, 17f, 300f, "สังกัดหน่วยงาน")
    )
}
