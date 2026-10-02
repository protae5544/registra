package com.chb.form.data

/**
 * ประเภทเทมเพลตแบบฟอร์มที่รองรับ
 * เพิ่มเทมเพลตใหม่ได้โดยเพิ่มค่าใน enum นี้
 */
enum class TemplateType(
    val id: String,
    val displayName: String,
    val shortName: String,
    val description: String
) {
    TRAINING(
        id = "training",
        displayName = "แบบฟอร์มลงทะเบียนอบรม CHB",
        shortName = "อบรม CHB",
        description = "แบบฟอร์มลงทะเบียนหลักสูตรความปลอดภัยและพื้นที่ปฏิบัติงาน"
    ),
    DORMITORY(
        id = "dormitory",
        displayName = "แบบคำร้องขออนุญาตเปิดห้องพักคนงาน",
        shortName = "ห้องพัก",
        description = "แบบคำร้องขออนุญาตเปิดห้องพักคนงาน หน่วยงาน COCONUT 1 (Christiani & Nielsen)"
    );

    companion object {
        fun fromId(id: String): TemplateType =
            entries.find { it.id == id } ?: TRAINING
    }
}
