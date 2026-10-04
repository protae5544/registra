package com.chb.form.data

import com.chb.form.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object JsonIo {

    fun parse(raw: String, fields: List<FieldSpec>): JsonImportResult {
        val warnings = mutableListOf<FormWarning>()
        val unknown = linkedSetOf<String>()
        var matched = 0

        if (raw.isBlank()) {
            warnings += FormWarning(WarningLevel.WARN, "empty_json", "JSON ว่าง")
            return JsonImportResult(emptyList(), 0, emptyList(), warnings)
        }

        val keyIndex = fields.associateBy { it.key }
        val aliasIndex = buildAliasIndex(fields)

        fun mapOne(o: JSONObject): Record {
            val m = mutableMapOf<String, String>()
            o.keys().forEach { k ->
                val target = keyIndex[k]?.key ?: aliasIndex[norm(k)]
                if (target == null) {
                    unknown += k
                    return@forEach
                }
                matched++
                val v = o.opt(k)
                m[target] = when (v) {
                    null, JSONObject.NULL -> ""
                    is Boolean -> v.toString()
                    is JSONArray -> (0 until v.length()).joinToString(", ") {
                        runCatching { v.getString(it) }.getOrDefault("")
                    }
                    else -> v.toString()
                }
            }
            return Record(id = UUID.randomUUID().toString().take(8), values = m)
        }

        val records = runCatching {
            val t = raw.trim()
            when {
                t.startsWith("[") -> {
                    val a = JSONArray(t)
                    (0 until a.length()).mapNotNull { i ->
                        runCatching { mapOne(a.getJSONObject(i)) }.getOrNull()
                    }
                }
                else -> {
                    val o = JSONObject(t)
                    val arr = o.optJSONArray("records")
                    when {
                        arr != null -> (0 until arr.length()).mapNotNull { i ->
                            runCatching { mapOne(arr.getJSONObject(i)) }.getOrNull()
                        }
                        o.has("fields") && o.opt("fields") is JSONObject ->
                            listOf(mapOne(o.getJSONObject("fields")))
                        else -> listOf(mapOne(o))
                    }
                }
            }
        }.getOrElse { e ->
            warnings += FormWarning(
                WarningLevel.ERROR, "json_parse",
                "อ่าน JSON ไม่สำเร็จ: ${e.message ?: "unknown"}"
            )
            emptyList()
        }

        if (unknown.isNotEmpty()) {
            warnings += FormWarning(
                WarningLevel.WARN, "unknown_keys",
                "คีย์ที่ไม่รู้จัก (ไม่ถูกนำเข้า): ${unknown.joinToString(", ")}"
            )
        }
        if (records.isEmpty() && warnings.none { it.level == WarningLevel.ERROR }) {
            warnings += FormWarning(WarningLevel.WARN, "no_records", "ไม่พบชุดข้อมูลใน JSON")
        }
        if (matched == 0 && records.isNotEmpty()) {
            warnings += FormWarning(
                WarningLevel.WARN, "no_match",
                "มีชุดข้อมูลแต่ไม่มีคีย์ที่จับคู่กับฟิลด์เทมเพลต — ตรวจ aliases หรือชื่อคีย์"
            )
        }

        fields.filter { it.required }.forEach { f ->
            records.forEachIndexed { i, r ->
                if (r.str(f.key).isBlank()) {
                    warnings += FormWarning(
                        WarningLevel.WARN, "required_empty",
                        "ชุดที่ ${i + 1}: ฟิลด์บังคับว่าง — ${f.label}",
                        fieldKey = f.key
                    )
                }
            }
        }

        return JsonImportResult(records, matched, unknown.toList(), warnings)
    }

    fun export(templateName: String, fields: List<FieldSpec>, rec: Record): String =
        JSONObject().apply {
            put("template", templateName)
            put("fields", JSONObject().also { o ->
                fields.forEach { f ->
                    when (f.kind) {
                        FieldKind.CHECK, FieldKind.RADIO -> o.put(f.key, rec.bool(f.key))
                        else -> o.put(f.key, rec.str(f.key))
                    }
                }
            })
        }.toString(2)

    fun exportBatch(templateName: String, fields: List<FieldSpec>, records: List<Record>): String =
        JSONObject().apply {
            put("template", templateName)
            put("records", JSONArray().also { arr ->
                records.forEach { rec ->
                    arr.put(JSONObject().also { o ->
                        fields.forEach { f ->
                            when (f.kind) {
                                FieldKind.CHECK, FieldKind.RADIO -> o.put(f.key, rec.bool(f.key))
                                else -> o.put(f.key, rec.str(f.key))
                            }
                        }
                    })
                }
            })
        }.toString(2)

    fun emptyTemplate(fields: List<FieldSpec>): String =
        JSONObject().apply {
            put("records", JSONArray().put(JSONObject().also { o ->
                fields.forEach { f ->
                    when (f.kind) {
                        FieldKind.CHECK, FieldKind.RADIO -> o.put(f.key, false)
                        else -> o.put(f.key, "")
                    }
                }
            }))
        }.toString(2)

    private fun buildAliasIndex(fields: List<FieldSpec>): Map<String, String> {
        val m = mutableMapOf<String, String>()
        fields.forEach { f ->
            m[norm(f.key)] = f.key
            m[norm(f.label)] = f.key
            f.aliases.forEach { a -> m[norm(a)] = f.key }
        }
        return m
    }

    private fun norm(s: String): String = s.lowercase()
        .replace(Regex("[\\s_\\-./:()]+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]"), "")
}
