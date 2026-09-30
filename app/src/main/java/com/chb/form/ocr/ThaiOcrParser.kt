package com.chb.form.ocr

data class ExtractedIdCard(
    val idNumber: String = "",
    val nameThai: String = "",
    val nameEng: String = "",
    val bloodType: String = "",
    val phone: String = "",
    val position: String = "",
    val rawText: String = ""
) {
    val hasUsefulData: Boolean
        get() = nameThai.isNotBlank() || idNumber.isNotBlank() || bloodType.isNotBlank() || phone.isNotBlank() || position.isNotBlank()
}

object ThaiOcrParser {

    private val PREFIXES = listOf(
        "นาย", "นางสาว", "นาง", "น.ส.", "ด.ช.", "ด.ญ.",
        "ว่าที่ร้อยตรี", "ร.ต.", "แพทย์หญิง", "นายแพทย์", "ดร."
    )

    private val BLOOD_TYPES = listOf("AB", "A", "B", "O")

    fun parse(rawText: String): ExtractedIdCard {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val idNum = extractIdNumber(rawText) ?: ""
        val nameTh = extractNameThai(lines) ?: ""
        val nameEn = extractNameEng(lines) ?: ""
        val blood = extractBloodType(lines) ?: ""
        val phone = extractPhone(lines) ?: ""
        val position = extractPosition(lines) ?: ""

        return ExtractedIdCard(
            idNumber = idNum,
            nameThai = nameTh,
            nameEng = nameEn,
            bloodType = blood,
            phone = phone,
            position = position,
            rawText = rawText
        )
    }

    /**
     * ค้นหาเลขประจำตัวประชาชน 13 หลัก (ทั้งแบบมีขีด 1-xxxx-xxxxx-xx-x หรือตัวเลขติดกัน)
     */
    fun extractIdNumber(text: String): String? {
        // รูปแบบมีขีด
        val dashRegex = Regex("""\b([1-8])\s*[-–]\s*(\d{4})\s*[-–]\s*(\d{5})\s*[-–]\s*(\d{2})\s*[-–]\s*(\d)\b""")
        val dashMatch = dashRegex.find(text)
        if (dashMatch != null) {
            return "${dashMatch.groupValues[1]}-${dashMatch.groupValues[2]}-${dashMatch.groupValues[3]}-${dashMatch.groupValues[4]}-${dashMatch.groupValues[5]}"
        }

        // รูปแบบ 13 หลักติดกัน
        val clean = text.replace(Regex("""[^\d]"""), "")
        val match13 = Regex("""[1-8]\d{12}""").find(clean)
        if (match13 != null) {
            val v = match13.value
            return "${v[0]}-${v.substring(1, 5)}-${v.substring(5, 10)}-${v.substring(10, 12)}-${v[12]}"
        }
        return null
    }

    /**
     * สกัดชื่อภาษาไทยจากคำนำหน้าชื่อ และตรวจจับรูปแบบชื่อ-นามสกุล
     */
    fun extractNameThai(lines: List<String>): String? {
        for (line in lines) {
            val trimmed = line.trim()
            for (p in PREFIXES) {
                if (trimmed.startsWith(p)) {
                    // ลบคำว่า "ชื่อ" หรือ "ชื่อตัวและชื่อสกุล" ที่อาจติดมาข้างหน้า
                    val clean = trimmed
                        .replace("ชื่อ-สกุล", "")
                        .replace("ชื่อตัวและชื่อสกุล", "")
                        .replace("ชื่อ", "")
                        .trim()
                    // ตรวจสอบว่ามีวรรคคั่นระหว่างชื่อกับนามสกุล
                    val parts = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
                    if (parts.isNotEmpty()) {
                        return parts.joinToString(" ")
                    }
                }
            }
        }

        // กรณีคำนำหน้าแยกบรรทัดกับชื่อ
        for (i in 0 until lines.size - 1) {
            val curr = lines[i].trim()
            val next = lines[i + 1].trim()
            for (p in PREFIXES) {
                if (curr == p || curr.endsWith(p)) {
                    val parts = next.split(Regex("""\s+""")).filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        return "$p ${parts.joinToString(" ")}"
                    }
                }
            }
        }
        return null
    }

    /**
     * สกัดชื่อภาษาอังกฤษ (Name / Mr. / Miss / Mrs.)
     */
    fun extractNameEng(lines: List<String>): String? {
        val engRegex = Regex("""(?:Name|Mr\.|Mrs\.|Miss)\s+([A-Za-z]+)\s+([A-Za-z]+)""", RegexOption.IGNORE_CASE)
        for (line in lines) {
            val m = engRegex.find(line)
            if (m != null) {
                return "${m.groupValues[1]} ${m.groupValues[2]}"
            }
        }
        return null
    }

    /**
     * ค้นหาหมู่โลหิตด้วย Fuzzy Label Patterns เช่น "หมู่โลหิต", "กรุ๊ปเลือด", "Blood", "Blood Group"
     */
    fun extractBloodType(lines: List<String>): String? {
        val labelPatterns = listOf("หมู่โลหิต", "กรุ๊ปเลือด", "โลหิต", "blood group", "blood")
        for (line in lines) {
            val lower = line.lowercase()
            for (pat in labelPatterns) {
                if (lower.contains(pat)) {
                    val after = line.substring(lower.indexOf(pat) + pat.length).trim()
                    for (b in BLOOD_TYPES) {
                        if (after.startsWith(b, ignoreCase = true) || after.contains(b, ignoreCase = true)) {
                            return b
                        }
                    }
                }
            }
        }
        // ตรวจหาตัวอักษรหมู่เลือดเดี่ยวๆ ที่อาจอยู่บรรทัดถัดไป
        for (i in lines.indices) {
            val lower = lines[i].lowercase()
            if (labelPatterns.any { lower.contains(it) } && i + 1 < lines.size) {
                val next = lines[i + 1].trim().uppercase()
                for (b in BLOOD_TYPES) {
                    if (next == b || next.startsWith(b)) return b
                }
            }
        }
        return null
    }

    /**
     * ค้นหาเบอร์โทรศัพท์ด้วยรูปแบบป้ายกำกับที่ยืดหยุ่น (โทร, เบอร์, Tel, Phone, Mobile)
     */
    fun extractPhone(lines: List<String>): String? {
        val telLabels = listOf("โทร", "เบอร์", "โทรศัพท์", "มือถือ", "tel", "phone", "mobile")
        for (line in lines) {
            val lower = line.lowercase()
            for (lbl in telLabels) {
                if (lower.contains(lbl)) {
                    val digits = line.replace(Regex("""[^\d]"""), "")
                    val phoneMatch = Regex("""0[689]\d{8}|0\d{8,9}""").find(digits)
                    if (phoneMatch != null) {
                        return phoneMatch.value
                    }
                }
            }
            // ตรวจสอบเบอร์ 10 หลักขึ้นต้นด้วย 06, 08, 09 ในบรรทัดทั่วไป
            val directMatch = Regex("""\b(0[689]\d{8})\b""").find(line.replace(Regex("""[- ]"""), ""))
            if (directMatch != null) {
                return directMatch.value
            }
        }
        return null
    }

    /**
     * ค้นหาตำแหน่งงานหรืออาชีพด้วยคำสำคัญ
     */
    fun extractPosition(lines: List<String>): String? {
        val posLabels = listOf("ตำแหน่ง", "position", "อาชีพ", "occupation")
        for (line in lines) {
            val lower = line.lowercase()
            for (lbl in posLabels) {
                if (lower.contains(lbl)) {
                    val after = line.substring(lower.indexOf(lbl) + lbl.length)
                        .replace(Regex("""[:\-–]"""), "")
                        .trim()
                    if (after.length in 2..30) {
                        return after
                    }
                }
            }
        }
        return null
    }
}
