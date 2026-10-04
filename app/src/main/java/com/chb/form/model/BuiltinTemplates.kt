package com.chb.form.model

object BuiltinTemplates {

    fun training(): FormTemplate = FormTemplate(
        id = "training",
        name = "แบบฟอร์มลงทะเบียนอบรม CHB",
        builtinType = "training",
        fields = listOf(
            FieldSpec("company", "บริษัท/หน่วยงาน", FieldKind.TEXT, aliases = listOf("บริษัท", "Company"), required = true),
            FieldSpec("fullname", "ชื่อ-นามสกุล", FieldKind.TEXT, aliases = listOf("ชื่อ", "name", "full_name"), required = true),
            FieldSpec("position", "ตำแหน่ง", FieldKind.TEXT, aliases = listOf("ตำแหน่ง", "job")),
            FieldSpec("phone", "เบอร์โทร", FieldKind.PHONE, aliases = listOf("โทร", "tel", "มือถือ")),
            FieldSpec("id_card", "เลขบัตรประชาชน", FieldKind.TEXT, aliases = listOf("บัตร", "cid", "id")),
            FieldSpec("blood", "กรุ๊ปเลือด", FieldKind.TEXT, aliases = listOf("blood_group", "กรุ๊ป")),
            FieldSpec("emergency_name", "ผู้ติดต่อฉุกเฉิน", FieldKind.TEXT, aliases = listOf("ฉุกเฉิน")),
            FieldSpec("emergency_phone", "เบอร์ฉุกเฉิน", FieldKind.PHONE),
            FieldSpec("area", "พื้นที่ปฏิบัติงาน", FieldKind.TEXT),
            FieldSpec("course", "หลักสูตร", FieldKind.TEXT),
            FieldSpec("experience", "ประสบการณ์", FieldKind.TEXT),
            FieldSpec("card_path", "รูปบัตร", FieldKind.IMAGE),
            FieldSpec("sign_path", "ลายเซ็น", FieldKind.SIGNATURE)
        )
    )

    fun dormitory(): FormTemplate = FormTemplate(
        id = "dormitory",
        name = "แบบคำร้องขออนุญาตเปิดห้องพักคนงาน",
        builtinType = "dormitory",
        fields = listOf(
            FieldSpec("applicant1_name", "ชื่อผู้ขอ 1", FieldKind.TEXT, aliases = listOf("ข้าพเจ้า", "ชื่อ1"), required = true),
            FieldSpec("applicant1_position", "ตำแหน่งผู้ขอ 1", FieldKind.TEXT),
            FieldSpec("applicant2_name", "ชื่อผู้ขอ 2", FieldKind.TEXT),
            FieldSpec("applicant2_position", "ตำแหน่งผู้ขอ 2", FieldKind.TEXT),
            FieldSpec("applicant3_name", "ชื่อผู้ขอ 3", FieldKind.TEXT),
            FieldSpec("applicant3_position", "ตำแหน่งผู้ขอ 3", FieldKind.TEXT),
            FieldSpec("affiliation", "สังกัด", FieldKind.CHOICE,
                options = listOf(
                    FieldOption("CN_COMPANY", "บริษัท กริสเตียนีและนีลเส็น"),
                    FieldOption("CONTRACTOR", "ผู้รับเหมา"),
                    FieldOption("OTHER", "อื่นๆ")
                )),
            FieldSpec("head_team_cn", "หัวหน้าชุด (CN)", FieldKind.TEXT),
            FieldSpec("foreman_cn", "โฟร์แมน (CN)", FieldKind.TEXT),
            FieldSpec("head_team_contractor", "หัวหน้าชุด (ผู้รับเหมา)", FieldKind.TEXT),
            FieldSpec("supervisor", "หัวหน้าคนงาน", FieldKind.TEXT),
            FieldSpec("contract_maker", "ผู้ทำสัญญา", FieldKind.TEXT),
            FieldSpec("other_affiliation", "สังกัดอื่นๆ", FieldKind.TEXT),
            FieldSpec("days", "จำนวนวัน", FieldKind.NUMBER),
            FieldSpec("start_date", "ตั้งแต่วันที่", FieldKind.DATE),
            FieldSpec("end_date", "ถึงวันที่", FieldKind.DATE),
            FieldSpec("guarantor_name", "ชื่อผู้ค้ำ", FieldKind.TEXT, aliases = listOf("ผู้รับรอง")),
            FieldSpec("guarantor_position", "ตำแหน่งผู้ค้ำ", FieldKind.TEXT),
            FieldSpec("guarantor_company", "บริษัทผู้ค้ำ", FieldKind.TEXT),
            FieldSpec("sequence_no", "ลำดับที่", FieldKind.TEXT),
            FieldSpec("document_date", "วันที่เอกสาร", FieldKind.DATE)
        )
    )

    fun forType(typeId: String): FormTemplate = when (typeId) {
        "dormitory" -> dormitory()
        else -> training()
    }
}
