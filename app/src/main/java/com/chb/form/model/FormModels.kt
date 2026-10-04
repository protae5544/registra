package com.chb.form.model

/**
 * โมเดลกลางรองรับหลายรูปแบบฟอร์ม
 * - ไม่พังเมื่อข้อมูลไม่ครบ: ใช้ Result + Warning แทน throw
 * - ผู้ใช้แก้เองได้ก่อนสร้างผลลัพธ์
 * - ยังไม่มี AI
 */

enum class FieldKind {
    TEXT, MULTILINE, NUMBER, PHONE, DATE, CHECK, RADIO, CHOICE, SIGNATURE, IMAGE
}

enum class FieldOrigin { BUILTIN, ACROFORM, HEURISTIC, MANUAL }

enum class WarningLevel { INFO, WARN, ERROR }

/** คำเตือนในผลลัพธ์ที่ไม่สมบูรณ์ — ไม่ทำให้แอปพัง */
data class FormWarning(
    val level: WarningLevel,
    val code: String,
    val message: String,
    val fieldKey: String? = null
)

data class Bounds(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
)

data class FieldOption(val value: String, val label: String)

data class FieldSpec(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val bounds: Bounds = Bounds(),
    val options: List<FieldOption> = emptyList(),
    val aliases: List<String> = emptyList(),
    val required: Boolean = false,
    val origin: FieldOrigin = FieldOrigin.BUILTIN,
    val approved: Boolean = true,
    val fontSize: Float = 12f,
    val group: String? = null
)

data class FormTemplate(
    val id: String,
    val name: String,
    val revision: Int = 1,
    val pageW: Float = 595f,
    val pageH: Float = 842f,
    val fields: List<FieldSpec> = emptyList(),
    val backgroundPdfPath: String? = null,
    val builtinType: String? = null
)

data class Record(
    val id: String = "",
    val values: Map<String, String> = emptyMap()
) {
    fun str(key: String): String = values[key].orEmpty()
    fun bool(key: String): Boolean =
        values[key]?.lowercase() in setOf("true", "1", "yes", "y", "ใช่", "x", "✓")
}

data class JsonImportResult(
    val records: List<Record>,
    val matchedKeys: Int,
    val unknownKeys: List<String>,
    val warnings: List<FormWarning>
)

enum class CommitTarget {
    SAVE_DRAFT,
    PUBLISH_TEMPLATE,
    GENERATE_OUTPUT
}

data class GateResult(
    val allowed: Boolean,
    val reasons: List<String>
)

data class GateState(
    val sourceAvailable: Boolean = true,
    val busy: Boolean = false,
    val templateApproved: Boolean = true,
    val hasUnapprovedFields: Boolean = false,
    val missingRequired: List<String> = emptyList(),
    val warnings: List<FormWarning> = emptyList(),
    val recordCount: Int = 0
)

object Gate {
    fun evaluate(target: CommitTarget, state: GateState): GateResult {
        val reasons = mutableListOf<String>()

        if (target == CommitTarget.SAVE_DRAFT) {
            if (state.busy) reasons += "ยังมีงานประมวลผลอยู่"
            return GateResult(reasons.isEmpty(), reasons)
        }

        if (!state.sourceAvailable) reasons += "ไม่พบเอกสารต้นฉบับ/เทมเพลต"
        if (state.busy) reasons += "ยังมีงานประมวลผลอยู่"
        if (!state.templateApproved) reasons += "ยังไม่ได้ยืนยันแม่แบบ"
        if (state.hasUnapprovedFields) reasons += "ยังมีฟิลด์ที่ยังไม่ได้ตรวจ"

        if (target == CommitTarget.GENERATE_OUTPUT) {
            if (state.recordCount <= 0) reasons += "ยังไม่มีข้อมูลที่จะสร้างผลลัพธ์"
            state.missingRequired.forEach { reasons += "ฟิลด์บังคับว่าง: $it" }
            state.warnings.filter { it.level == WarningLevel.ERROR }
                .forEach { reasons += it.message }
        }

        return GateResult(allowed = reasons.isEmpty(), reasons = reasons.distinct())
    }
}
