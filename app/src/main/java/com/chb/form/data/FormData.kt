package com.chb.form.data

data class FormData(
    val checks: List<Boolean> = List(19) { false },
    val fields: List<String> = List(14) { "" },
    val cardPath: String? = null,
    val signaturePath: String? = null,
    val withSignature: Boolean = true
) {
    fun check(i: Int): Boolean = checks.getOrElse(i) { false }
    fun field(i: Int): String = fields.getOrElse(i) { "" }

    // หมวด 1: พื้นที่และหลักสูตร
    val isAreaCourseComplete: Boolean
        get() = Content.AREA_IDS.any { check(it) } && Content.COURSES.any { check(it.idx) }

    // หมวด 2: บัตรประชาชน
    val isCardComplete: Boolean
        get() = !cardPath.isNullOrBlank()

    // หมวด 3: ข้อมูลทั่วไปและประวัติการทำงาน
    val isInfoComplete: Boolean
        get() = field(F.COMPANY).isNotBlank() &&
                field(F.TEL).length >= 9 &&
                field(F.BLOOD).isNotBlank() &&
                field(F.POSITION).isNotBlank() &&
                Content.EXPERIENCE_IDS.any { check(it) } &&
                Content.PREVIOUS_IDS.any { check(it) }

    // หมวด 4: ผู้ติดต่อฉุกเฉิน
    val isEmergencyComplete: Boolean
        get() = field(F.EMG_NAME).isNotBlank() &&
                field(F.EMG_REL).isNotBlank() &&
                field(F.EMG_TEL).length >= 9

    // หมวด 5: ลายเซ็น
    val isSignatureComplete: Boolean
        get() = !withSignature || !signaturePath.isNullOrBlank()

    val sectionStatuses: List<Boolean>
        get() = listOf(
            isAreaCourseComplete,
            isCardComplete,
            isInfoComplete,
            isEmergencyComplete,
            isSignatureComplete
        )

    val completedSectionsCount: Int
        get() = sectionStatuses.count { it }

    val required: List<Boolean>
        get() = listOf(
            Content.AREA_IDS.any { check(it) },
            Content.COURSES.any { check(it.idx) },
            cardPath != null,
            field(F.COMPANY).isNotBlank(),
            field(F.TEL).length >= 9,
            field(F.BLOOD).isNotBlank(),
            field(F.POSITION).isNotBlank(),
            field(F.EMG_NAME).isNotBlank(),
            field(F.EMG_REL).isNotBlank(),
            field(F.EMG_TEL).length >= 9,
            Content.EXPERIENCE_IDS.any { check(it) },
            Content.PREVIOUS_IDS.any { check(it) },
            !withSignature || signaturePath != null
        )

    val progress: Float
        get() = required.count { it } / required.size.toFloat()

    val complete: Boolean
        get() = required.all { it }
}
