package com.chb.form.engine

import com.chb.form.data.DormFormData
import com.chb.form.data.FormData
import com.chb.form.model.FormWarning
import com.chb.form.model.Record
import com.chb.form.model.WarningLevel

object SafeApply {

    fun toDorm(rec: Record): Pair<DormFormData, List<FormWarning>> {
        val w = mutableListOf<FormWarning>()
        fun s(k: String) = rec.str(k)

        val applicants = listOf(
            DormFormData.Applicant(s("applicant1_name"), s("applicant1_position")),
            DormFormData.Applicant(s("applicant2_name"), s("applicant2_position")),
            DormFormData.Applicant(s("applicant3_name"), s("applicant3_position"))
        )
        if (applicants[0].name.isBlank()) {
            w += FormWarning(WarningLevel.WARN, "no_applicant", "ยังไม่มีชื่อผู้ขอคนที่ 1")
        }

        val affRaw = s("affiliation").ifBlank { s("affiliationType") }
        val aff = when (affRaw.uppercase()) {
            "CN_COMPANY", "CN", "บริษัท" -> DormFormData.AffiliationType.CN_COMPANY
            "CONTRACTOR", "ผู้รับเหมา" -> DormFormData.AffiliationType.CONTRACTOR
            "OTHER", "อื่นๆ" -> DormFormData.AffiliationType.OTHER
            "" -> DormFormData.AffiliationType.NONE
            else -> {
                w += FormWarning(WarningLevel.INFO, "aff_unknown", "สังกัดไม่รู้จัก: $affRaw — ใช้ NONE")
                DormFormData.AffiliationType.NONE
            }
        }

        val data = DormFormData(
            applicants = applicants,
            affiliationType = aff,
            headOfTeamCn = s("head_team_cn").ifBlank { s("headOfTeamCn") },
            foremanCn = s("foreman_cn").ifBlank { s("foremanCn") },
            headOfTeamContractor = s("head_team_contractor").ifBlank { s("headOfTeamContractor") },
            supervisor = s("supervisor"),
            contractMaker = s("contract_maker").ifBlank { s("contractMaker") },
            otherAffiliation = s("other_affiliation").ifBlank { s("otherAffiliation") },
            days = s("days"),
            startDate = s("start_date").ifBlank { s("startDate") },
            endDate = s("end_date").ifBlank { s("endDate") },
            guarantorName = s("guarantor_name").ifBlank { s("guarantorName") },
            guarantorPosition = s("guarantor_position").ifBlank { s("guarantorPosition") },
            guarantorCompany = s("guarantor_company").ifBlank { s("guarantorCompany") },
            sequenceNo = s("sequence_no").ifBlank { s("sequenceNo") },
            documentDate = s("document_date").ifBlank { s("documentDate") }
        )
        return data to w
    }

    fun toTraining(rec: Record, base: FormData = FormData()): Pair<FormData, List<FormWarning>> {
        val w = mutableListOf<FormWarning>()
        val fields = base.fields.toMutableList()
        val map = listOf(
            0 to listOf("company", "บริษัท"),
            1 to listOf("fullname", "ชื่อ", "name"),
            2 to listOf("position", "ตำแหน่ง"),
            3 to listOf("phone", "tel", "โทร"),
            4 to listOf("id_card", "cid", "บัตร"),
            5 to listOf("blood", "กรุ๊ป"),
            6 to listOf("emergency_name", "ฉุกเฉิน"),
            7 to listOf("emergency_phone")
        )
        map.forEach { (idx, keys) ->
            if (idx >= fields.size) return@forEach
            val v = keys.firstNotNullOfOrNull { k -> rec.str(k).takeIf { it.isNotBlank() } }
            if (v != null) fields[idx] = v
        }
        if (fields.getOrNull(1).isNullOrBlank()) {
            w += FormWarning(WarningLevel.WARN, "no_name", "ยังไม่มีชื่อผู้ลงทะเบียน")
        }
        return base.copy(fields = fields) to w
    }
}
