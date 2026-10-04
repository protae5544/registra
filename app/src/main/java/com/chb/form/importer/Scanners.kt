package com.chb.form.importer

import android.graphics.PointF
import com.chb.form.model.Box
import com.chb.form.model.Seg
import com.chb.form.model.TextRun
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs
import kotlin.math.max

class TextScanner : PDFTextStripper() {
    val runs = mutableListOf<TextRun>()

    init {
        sortByPosition = true
        startPage = 1
        endPage = 1
    }

    override fun writeString(text: String, positions: MutableList<TextPosition>) {
        if (text.isBlank() || positions.isEmpty()) return
        val x = positions.minOf { it.xDirAdj }
        val right = positions.maxOf { it.xDirAdj + it.widthDirAdj }
        val p = positions.first()
        val fontName = runCatching { p.font?.name.orEmpty() }.getOrDefault("")
        val weight = runCatching { p.font?.fontDescriptor?.fontWeight ?: 400f }.getOrDefault(400f)
        val size = runCatching {
            p.fontSizeInPt * p.textMatrix.scalingFactorY
        }.getOrDefault(p.fontSizeInPt)

        runs += TextRun(
            text = text.trim(),
            x = x,
            baseline = p.yDirAdj,
            width = (right - x).coerceAtLeast(0.1f),
            size = size,
            bold = fontName.contains("Bold", true) || weight >= 600f
        )
    }
}

class GraphicsScanner(private val page: PDPage) : PDFGraphicsStreamEngine(page) {
    val segs = mutableListOf<Seg>()
    val boxes = mutableListOf<Box>()

    private val pts = mutableListOf<PointF>()
    private var cur = PointF()
    private val ph = page.mediaBox.height

    private fun fy(y: Float) = ph - y

    override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
        val xs = listOf(p0.x, p1.x, p2.x, p3.x)
        val ys = listOf(p0.y, p1.y, p2.y, p3.y)
        val x = xs.min(); val w = xs.max() - x
        val yTop = fy(ys.max()); val h = ys.max() - ys.min()
        if (w > 0.5f && h > 0.5f) {
            val sw = runCatching { graphicsState.lineWidth }.getOrDefault(1f).coerceAtLeast(0.1f)
            boxes += Box(x, yTop, w, h, sw)
        }
        pts.clear()
    }

    override fun moveTo(x: Float, y: Float) {
        cur = PointF(x, y); pts.clear(); pts += cur
    }

    override fun lineTo(x: Float, y: Float) {
        cur = PointF(x, y); pts += cur
    }

    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        cur = PointF(x3, y3); pts += cur
    }

    override fun getCurrentPoint(): PointF = cur

    override fun closePath() {
        if (pts.size > 1) pts += pts.first()
    }

    override fun strokePath() {
        val dash = runCatching {
            val d = graphicsState.lineDashPattern
            d != null && d.dashArray != null && d.dashArray.isNotEmpty() && d.dashArray.any { it > 0f }
        }.getOrDefault(false)
        val sw = runCatching { graphicsState.lineWidth }.getOrDefault(1f).coerceAtLeast(0.1f)
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            if (abs(a.x - b.x) < 0.3f && abs(a.y - b.y) < 0.3f) continue
            segs += Seg(a.x, fy(a.y), b.x, fy(b.y), sw, dash)
        }
        pts.clear()
    }

    override fun fillPath(windingRule: Int) = thinFillAsLine()
    override fun fillAndStrokePath(windingRule: Int) {
        strokePath()
    }

    private fun thinFillAsLine() {
        if (pts.size >= 4) {
            val xs = pts.map { it.x }; val ys = pts.map { it.y }
            val h = ys.max() - ys.min(); val w = xs.max() - xs.min()
            if (h < 2.5f && w > 15f) {
                val y = fy((ys.max() + ys.min()) / 2f)
                segs += Seg(xs.min(), y, xs.max(), y, max(h, 0.6f), false)
            }
        }
        pts.clear()
    }

    override fun endPath() { pts.clear() }
    override fun clip(windingRule: Int) {}
    override fun drawImage(pdImage: PDImage) {}
    override fun shadingFill(shadingName: COSName) {}

    fun scan() {
        processPage(page)
    }
}
