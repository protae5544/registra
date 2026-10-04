package com.chb.form.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.chb.form.R
import com.chb.form.model.*
import java.io.File
import java.io.FileOutputStream

enum class PaperSize(val label: String, val wmm: Float, val hmm: Float) {
    SOURCE("ขนาดต้นฉบับ", 0f, 0f),
    A4("A4 · 210 × 297 มม.", 210f, 297f),
    A5("A5 · 148 × 210 มม.", 148f, 210f)
}

enum class BackgroundKind(val label: String) {
    PDF("พื้น PDF"),
    IMAGE("พื้นภาพ"),
    NONE("เฉพาะข้อมูล")
}

class TemplateRenderer(private val ctx: Context) {
    private val face: Typeface =
        ResourcesCompat.getFont(ctx, R.font.sarabun_regular) ?: Typeface.DEFAULT

    fun render(
        tpl: FormTemplate,
        records: List<Record>,
        out: File,
        paper: PaperSize = PaperSize.SOURCE,
        background: BackgroundKind = BackgroundKind.PDF
    ): File {
        out.parentFile?.mkdirs()
        val doc = PdfDocument()
        val (ptW, ptH) = BackgroundCapture.paperPoints(paper, tpl.pageW, tpl.pageH).let {
            Math.round(it.first).coerceAtLeast(1) to Math.round(it.second).coerceAtLeast(1)
        }

        val bgBitmap: Bitmap? = loadBackground(tpl, background)

        val list = records.ifEmpty { listOf(Record()) }
        list.forEachIndexed { i, rec ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(ptW, ptH, i + 1).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            c.save()
            val s = minOf(ptW / tpl.pageW, ptH / tpl.pageH).coerceAtLeast(0.01f)
            c.translate((ptW - tpl.pageW * s) / 2f, (ptH - tpl.pageH * s) / 2f)
            c.scale(s, s)

            if (background != BackgroundKind.NONE) {
                if (bgBitmap != null && !bgBitmap.isRecycled) {
                    // normalize: contain ลงหน้าเทมเพลต ตามขนาดที่ set — ไม่ยืดภาพ
                    BackgroundCapture.drawContained(c, bgBitmap, tpl.pageW, tpl.pageH)
                } else {
                    drawBoxes(c, tpl)
                    drawSegs(c, tpl)
                    drawTexts(c, tpl)
                }
            }
            drawValues(c, tpl, rec)
            c.restore()
            doc.finishPage(page)
        }

        if (bgBitmap != null && !bgBitmap.isRecycled) bgBitmap.recycle()

        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    /**
     * พื้น PDF = raster จากไฟล์ต้นฉบับ
     * พื้นภาพ = ภาพที่แคป/แนบ (JPEG) normalize contain ตามขนาดหน้า
     *          ถ้ายังไม่มีภาพ ให้แคปจาก PDF หรือวาดเส้น/ข้อความเป็นภาพ
     */
    private fun loadBackground(tpl: FormTemplate, kind: BackgroundKind): Bitmap? {
        val (cw, ch) = BackgroundCapture.capturePixels(tpl.pageW, tpl.pageH)
        return when (kind) {
            BackgroundKind.NONE -> null
            BackgroundKind.PDF -> {
                BackgroundCapture.rasterizePdf(tpl.backgroundPdfPath, cw, ch)
                    ?: BackgroundCapture.loadFile(tpl.backgroundImagePath)
            }
            BackgroundKind.IMAGE -> {
                BackgroundCapture.loadFile(tpl.backgroundImagePath)
                    ?: BackgroundCapture.rasterizePdf(tpl.backgroundPdfPath, cw, ch)
                    ?: captureReconstructed(tpl, cw, ch)
            }
        }
    }

    private fun captureReconstructed(t: FormTemplate, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val s = minOf(w / t.pageW, h / t.pageH).coerceAtLeast(0.01f)
        c.translate((w - t.pageW * s) / 2f, (h - t.pageH * s) / 2f)
        c.scale(s, s)
        drawBoxes(c, t)
        drawSegs(c, t)
        drawTexts(c, t)
        return bmp
    }

    private fun drawBoxes(c: Canvas, t: FormTemplate) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK }
        t.boxes.forEach {
            p.strokeWidth = it.stroke.coerceAtLeast(0.5f)
            c.drawRect(it.x, it.y, it.x + it.w, it.y + it.h, p)
        }
    }

    private fun drawSegs(c: Canvas, t: FormTemplate) {
        val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.BLACK; strokeCap = Paint.Cap.ROUND
        }
        val dash = Paint(solid).apply { pathEffect = DashPathEffect(floatArrayOf(1.5f, 3f), 0f) }
        t.segs.forEach {
            val p = if (it.dashed) dash else solid
            p.strokeWidth = it.stroke.coerceAtLeast(0.5f)
            c.drawLine(it.x1, it.y1, it.x2, it.y2, p)
        }
    }

    private fun drawTexts(c: Canvas, t: FormTemplate) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; isSubpixelText = true }
        t.texts.forEach { r ->
            p.typeface = if (r.bold) Typeface.create(face, Typeface.BOLD) else face
            p.textSize = r.size.coerceAtLeast(6f)
            p.textScaleX = 1f
            val w = p.measureText(r.text)
            if (w > 0.5f && r.width > 0.5f) p.textScaleX = (r.width / w).coerceIn(0.5f, 2f)
            c.drawText(r.text, r.x, r.baseline, p)
        }
    }

    private fun drawValues(c: Canvas, t: FormTemplate, rec: Record) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face; color = Color.BLACK; isSubpixelText = true
        }
        val chk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.BLACK
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        t.fields.forEach { f ->
            val fx = if (f.w > 0) f.x else f.bounds.x
            val fy = if (f.h > 0) f.y else f.bounds.y
            val fw = if (f.w > 0) f.w else f.bounds.width
            val fh = if (f.h > 0) f.h else f.bounds.height
            when (f.kind) {
                FieldKind.CHECK, FieldKind.RADIO -> {
                    if (!rec.bool(f.key) && rec.str(f.key) != f.label) return@forEach
                    chk.strokeWidth = (minOf(fw, fh) * 0.16f).coerceAtLeast(0.8f)
                    c.drawPath(Path().apply {
                        moveTo(fx + fw * 0.20f, fy + fh * 0.52f)
                        lineTo(fx + fw * 0.44f, fy + fh * 0.80f)
                        lineTo(fx + fw * 0.85f, fy + fh * 0.16f)
                    }, chk)
                }
                FieldKind.SIGNATURE, FieldKind.IMAGE -> {
                    val path = rec.str(f.key); if (path.isBlank()) return@forEach
                    val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return@forEach
                    val sc = minOf(fw / bmp.width, fh / bmp.height)
                    val dw = bmp.width * sc; val dh = bmp.height * sc
                    c.drawBitmap(
                        bmp, null,
                        RectF(fx + (fw - dw) / 2, fy + (fh - dh) / 2, fx + (fw + dw) / 2, fy + (fh + dh) / 2),
                        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    )
                    if (!bmp.isRecycled) bmp.recycle()
                }
                FieldKind.MULTILINE -> {
                    val v = rec.str(f.key); if (v.isBlank()) return@forEach
                    tp.textSize = f.fontSize; tp.textScaleX = 1f
                    var y = fy + f.fontSize
                    wrap(v, tp, fw - 3f).forEach { line ->
                        if (y <= fy + fh) { c.drawText(line, fx + 2f, y, tp); y += f.fontSize * 1.32f }
                    }
                }
                else -> {
                    val v = rec.str(f.key).trim(); if (v.isEmpty()) return@forEach
                    tp.textSize = f.fontSize.coerceAtLeast(8f); tp.textScaleX = 1f
                    val maxW = (fw - 4f).coerceAtLeast(20f)
                    val mw = tp.measureText(v)
                    if (mw > maxW) tp.textScaleX = (maxW / mw).coerceAtLeast(0.55f)
                    // ถ้าฟิลด์ manual ที่ยังไม่มีตำแหน่งชัด วางใกล้หัวหน้า
                    val drawY = if (fh > 1f) fy + fh * 0.78f else fy + f.fontSize
                    c.drawText(v, fx + 2f, drawY, tp)
                }
            }
        }
    }

    private fun wrap(text: String, p: TextPaint, maxW: Float): List<String> {
        if (maxW <= 0) return listOf(text)
        val out = mutableListOf<String>()
        var line = StringBuilder()
        text.forEach { ch ->
            if (p.measureText(line.toString() + ch) > maxW && line.isNotEmpty()) {
                out += line.toString(); line = StringBuilder()
            }
            line.append(ch)
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }
}
