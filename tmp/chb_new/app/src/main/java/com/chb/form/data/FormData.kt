package com.chb.form.data
import android.graphics.Bitmap
data class FormData( val checks: List<Boolean> = List(19) { false
}, val fields: List<String> = List(14) { ""
}, val cardPath: String? = null, val signaturePath: String? = null, val withSignature: Boolean = true ) { fun check(i: Int) = checks.getOrElse(i) { false
}
fun field(i: Int) = fields.getOrElse(i) { ""
}
/*ความคืบหน้าจากช่องที่จำเป็น */
val required: List<Boolean> get() = listOf( Content.AREA_IDS.any { check(it) }, Content.COURSES.any { check(it.idx) }, cardPath != null, field(F.COMPANY).isNotBlank(), field(F.TEL).length >= 9, field(F.BLOOD).isNotBlank(), field(F.POSITION).isNotBlank(), field(F.EMG_NAME).isNotBlank(), field(F.EMG_REL).isNotBlank(), field(F.EMG_TEL).length >= 9, Content.EXPERIENCE_IDS.any { check(it) }, Content.PREVIOUS_IDS.any { check(it) } )
val progress: Float get() = required.count { it
} / required.size.toFloat()
val complete: Boolean get() = required.all { it
} }
