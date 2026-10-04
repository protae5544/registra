package com.chb.form.importer

import com.chb.form.model.*
import kotlin.math.abs

object FieldDetector {

    private const val MIN_LINE = 24f
    private const val BASE_TOL = 7f
    private const val LABEL_GAP = 90f

    fun detect(texts: List<TextRun>, segs: List<Seg>, boxes: List<Box>): List<FieldSpec> {
        val out = mutableListOf<FieldSpec>()

        boxes.filter { b ->
            b.w in 5f..22f && b.h in 5f..22f && b.w / b.h in 0.70f..1.42f
        }.forEachIndexed { i, b ->
            val label = nearestLabel(texts, b.x + b.w, b.y + b.h / 2f, rightSide = true)
            out += FieldSpec(
                key = sanitize(label ?: "check_$i"),
                label = label ?: "ช่องติ๊ก ${i + 1}",
                kind = FieldKind.CHECK,
                x = b.x, y = b.y, w = b.w, h = b.h,
                bounds = Bounds(b.x, b.y, b.w, b.h),
                origin = FieldOrigin.HEURISTIC,
                source = "box",
                approved = false
            )
        }

        val lines = segs.filter { it.horizontal && it.length >= MIN_LINE }
            .sortedWith(compareBy({ it.y1 }, { it.x1 }))

        merge(lines).forEachIndexed { i, s ->
            val label = nearestLabel(texts, s.x1, s.y1, rightSide = false)
            val h = 14f
            out += FieldSpec(
                key = sanitize(label ?: "field_$i"),
                label = label ?: "ช่องกรอก ${i + 1}",
                kind = guessKind(label),
                x = s.x1, y = s.y1 - h + 2f,
                w = s.x2 - s.x1, h = h,
                bounds = Bounds(s.x1, s.y1 - h + 2f, s.x2 - s.x1, h),
                fontSize = 10f,
                origin = FieldOrigin.HEURISTIC,
                source = "line",
                approved = false
            )
        }

        boxes.filter { it.w > 120f && it.h > 60f }
            .filter { b -> texts.none { inside(it.x, it.baseline, b) } }
            .forEachIndexed { i, b ->
                out += FieldSpec(
                    key = "image_$i",
                    label = "พื้นที่รูปภาพ/ลายเซ็น ${i + 1}",
                    kind = FieldKind.SIGNATURE,
                    x = b.x, y = b.y, w = b.w, h = b.h,
                    bounds = Bounds(b.x, b.y, b.w, b.h),
                    origin = FieldOrigin.HEURISTIC,
                    source = "box",
                    approved = false
                )
            }

        return dedupeKeys(out)
    }

    private fun merge(lines: List<Seg>): List<Seg> {
        val res = mutableListOf<Seg>()
        lines.forEach { s ->
            val last = res.lastOrNull()
            if (last != null && abs(last.y1 - s.y1) < 1.5f && s.x1 - last.x2 < 12f) {
                res[res.lastIndex] = last.copy(x2 = maxOf(last.x2, s.x2))
            } else res += s
        }
        return res.filter { it.x2 - it.x1 >= MIN_LINE }
    }

    private fun nearestLabel(
        texts: List<TextRun>,
        anchorX: Float,
        anchorY: Float,
        rightSide: Boolean
    ): String? =
        texts.filter { abs(it.baseline - anchorY) < BASE_TOL }
            .filter {
                if (rightSide) it.x >= anchorX - 2f
                else it.x + it.width <= anchorX + 2f
            }
            .minByOrNull {
                if (rightSide) it.x - anchorX else anchorX - (it.x + it.width)
            }
            ?.takeIf {
                val d = if (rightSide) it.x - anchorX else anchorX - (it.x + it.width)
                d in -4f..LABEL_GAP
            }
            ?.text
            ?.trim()
            ?.trimEnd(':', '：', '-', '–')
            ?.trim()
            ?.takeIf { it.length in 1..60 }

    private fun guessKind(label: String?): FieldKind {
        val l = label.orEmpty().lowercase()
        return when {
            listOf("โทร", "tel", "phone", "มือถือ").any { l.contains(it) } -> FieldKind.PHONE
            listOf("วันที่", "date", "ว/ด/ป").any { l.contains(it) } -> FieldKind.DATE
            listOf("จำนวน", "อายุ", "เลขที่", "amount", "qty", "no.").any { l.contains(it) } -> FieldKind.NUMBER
            else -> FieldKind.TEXT
        }
    }

    private fun inside(x: Float, y: Float, b: Box) =
        x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h

    fun sanitize(s: String?): String {
        val base = s.orEmpty().trim().lowercase()
            .replace(Regex("[\\s\\-./:()]+"), "_")
            .replace(Regex("[^\\p{L}\\p{N}_]"), "")
            .trim('_')
        return base.ifBlank { "field" }
    }

    private fun dedupeKeys(list: List<FieldSpec>): List<FieldSpec> {
        val seen = mutableMapOf<String, Int>()
        return list.map { f ->
            val n = seen.merge(f.key, 1, Int::plus)!!
            if (n == 1) f else f.copy(key = "${f.key}_$n")
        }
    }
}
