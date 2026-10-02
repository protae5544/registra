package com.chb.form.data

/**
 * พิกัดและเลย์เอาต์สำหรับแบบคำร้องขออนุญาตเปิดห้องพักคนงาน
 * Christiani & Nielsen - หน่วยงาน COCONUT 1 (ON.11867)
 * อ้างอิงจากต้นฉบับจริง
 */
object DormSpec {
    const val W = 1785f
    const val H = 2520f
    const val MM_W = 210f
    const val MM_H = 297f
    const val STROKE = 2.4f
    const val DOT_W = 2.8f
    const val DOT_GAP = 6f
    const val CHK = 4.2f
    const val VAL_FS = 24f
    const val STATIC_FS = 22f
    const val TITLE_FS = 32f
    const val SMALL_FS = 18f

    val frames = listOf(
        floatArrayOf(50f, 40f, 1685f, 150f),
        floatArrayOf(50f, 210f, 1685f, 1050f),
        floatArrayOf(50f, 1290f, 1685f, 1180f)
    )

    val boxes = listOf(
        floatArrayOf(90f, 480f, 30f, 26f),
        floatArrayOf(920f, 480f, 30f, 26f),
        floatArrayOf(90f, 580f, 30f, 26f)
    )

    val dotted = listOf(
        floatArrayOf(340f, 300f, 1050f),
        floatArrayOf(1280f, 300f, 1680f),
        floatArrayOf(340f, 350f, 1050f),
        floatArrayOf(1280f, 350f, 1680f),
        floatArrayOf(340f, 400f, 1050f),
        floatArrayOf(1280f, 400f, 1680f),
        floatArrayOf(300f, 530f, 700f),
        floatArrayOf(300f, 570f, 700f),
        floatArrayOf(1150f, 530f, 1650f),
        floatArrayOf(1150f, 570f, 1650f),
        floatArrayOf(1150f, 610f, 1650f),
        floatArrayOf(300f, 620f, 700f),
        floatArrayOf(580f, 720f, 720f),
        floatArrayOf(400f, 900f, 950f),
        floatArrayOf(1100f, 900f, 1400f),
        floatArrayOf(1550f, 900f, 1680f),
        floatArrayOf(700f, 950f, 1050f),
        floatArrayOf(1250f, 950f, 1600f)
    )

    val fields = listOf(
        floatArrayOf(340f, 298f, 1050f),
        floatArrayOf(1280f, 298f, 1680f),
        floatArrayOf(340f, 348f, 1050f),
        floatArrayOf(1280f, 348f, 1680f),
        floatArrayOf(340f, 398f, 1050f),
        floatArrayOf(1280f, 398f, 1680f),
        floatArrayOf(300f, 528f, 700f),
        floatArrayOf(300f, 568f, 700f),
        floatArrayOf(1150f, 528f, 1650f),
        floatArrayOf(1150f, 568f, 1650f),
        floatArrayOf(1150f, 608f, 1650f),
        floatArrayOf(300f, 618f, 700f),
        floatArrayOf(580f, 718f, 720f),
        floatArrayOf(400f, 898f, 950f),
        floatArrayOf(1100f, 898f, 1400f),
        floatArrayOf(1550f, 898f, 1680f),
        floatArrayOf(700f, 948f, 1050f),
        floatArrayOf(1250f, 948f, 1600f),
        floatArrayOf(1480f, 80f, 1680f),
        floatArrayOf(1480f, 130f, 1680f)
    )

    val signatureApplicant = floatArrayOf(100f, 1120f, 450f, 100f)
    val signatureApprover = floatArrayOf(1000f, 1120f, 450f, 100f)
    val card = floatArrayOf(1400f, 230f, 280f, 170f)
}
