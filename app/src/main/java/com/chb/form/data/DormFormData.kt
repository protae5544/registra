package com.chb.form.data

/**
 * ข้อมูลสำหรับแบบคำร้องขออนุญาตเปิดห้องพักคนงาน
 */
data class DormFormData(
    // ผู้ขอเข้าพัก (สูงสุด 3 คน)
    val applicants: List<Applicant> = listOf(Applicant(), Applicant(), Applicant()),

    // สังกัดหน่วยงาน
    val affiliationType: AffiliationType = AffiliationType.NONE,
    val headOfTeamCn: String = "",          // หัวหน้าชุด (บริษัท CN)
    val foremanCn: String = "",             // โฟร์แมน (บริษัท CN)
    val headOfTeamContractor: String = "",  // หัวหน้าชุด (ผู้รับเหมา)
    val supervisor: String = "",            // หัวหน้าคนงาน
    val contractMaker: String = "",         // ผู้ทำสัญญา
    val otherAffiliation: String = "",      // อื่นๆ

    // รายละเอียดการพัก
    val days: String = "",                  // จำนวนวัน
    val startDate: String = "",
    val endDate: String = "",

    // ผู้ค้ำประกัน
    val guarantorName: String = "",
    val guarantorPosition: String = "",
    val guarantorCompany: String = "",

    // หัวเอกสาร
    val sequenceNo: String = "",
    val documentDate: String = "",

    // รูปและลายเซ็น
    val cardPath: String? = null,
    val signatureApplicantPath: String? = null,
    val signatureApproverPath: String? = null,
    val withSignature: Boolean = true
) {
    data class Applicant(
        val name: String = "",
        val position: String = ""
    )

    enum class AffiliationType {
        NONE, CN_COMPANY, CONTRACTOR, OTHER
    }

    val isBasicComplete: Boolean
        get() = applicants.any { it.name.isNotBlank() } &&
                (affiliationType != AffiliationType.NONE) &&
                days.isNotBlank()

    val isGuarantorComplete: Boolean
        get() = guarantorName.isNotBlank()

    val isCardComplete: Boolean
        get() = !cardPath.isNullOrBlank()

    val isSignatureComplete: Boolean
        get() = !withSignature || !signatureApplicantPath.isNullOrBlank()

    val progress: Float
        get() {
            var done = 0
            var total = 5
            if (applicants.any { it.name.isNotBlank() }) done++
            if (affiliationType != AffiliationType.NONE) done++
            if (days.isNotBlank() && startDate.isNotBlank()) done++
            if (guarantorName.isNotBlank()) done++
            if (isSignatureComplete) done++
            return done / total.toFloat()
        }

    val complete: Boolean
        get() = isBasicComplete && isGuarantorComplete && isSignatureComplete
}
