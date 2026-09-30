package com.chb.form.pdf
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import com.chb.form.R
import com.chb.form.data.FormData
import com.chb.form.data.Spec
import java.io.File
import java.io.FileOutputStream
class FormPdf(ctx: Context) {
private val family: Typeface = ResourcesCompat.getFont(ctx, R.font.sarabun_regular) ?: Typeface.DEFAULT
//  1 pt = 1/72 นิ้ว
private val ptW = Math.round(Spec.MM_W / 25.4f * 72f) //  595
private val ptH = Math.round(Spec.MM_H / 25.4f * 72f) // 859
fun render(data: FormData, out: File): File { out.parentFile?.mkdirs()
val doc = PdfDocument()
val page = doc.startPage(PdfDocument.PageInfo.Builder(ptW, ptH, 1).create())
val c = page.canvas
c.drawColor(Color.WHITE)
c.save()
c.scale(ptW / Spec.W, ptH / Spec.H) // จากนี้ใช้พิกัด 1785×2576 ได้ตรงๆ
drawFrames(c)
drawDotted(c)
drawStatic(c)
drawCard(c, data)
drawChecks(c, data)
drawValues(c, data)
drawSignature(c, data)
c.restore()
doc.finishPage(page)
FileOutputStream(out).use { doc.writeTo(it) }
doc.close()
return out
}
//  ---------- กรอบ ----------
private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE;
color = Color.BLACK;
strokeWidth = Spec.STROKE
}
private fun drawFrames(c: Canvas) { Spec.frames.forEach { c.drawRect(it[0], it[1], it[0] + it[2], it[1] + it[3], stroke) }
Spec.boxes.forEach { c.drawRoundRect(it[0], it[1], it[0] + it[2], it[1] + it[3], 3f, 3f, stroke) } }
//  ---------- เส้นประ ----------
private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE;
color = Color.BLACK
strokeWidth = Spec.DOT_W;
strokeCap = Paint.Cap.ROUND
pathEffect = DashPathEffect(floatArrayOf(0.1f, Spec.DOT_GAP), 0f) }
private fun drawDotted(c: Canvas) = Spec.dotted.forEach { c.drawLine(it[0], it[1], it[2], it[1], dot) }
//  ---------- ข้อความคงที่ ----------
private fun drawStatic(c: Canvas) = Spec.texts.forEach { t -> drawFitted(c, t.html, t.l / 100f * Spec.W, t.t / 100f * Spec.H + Spec.ROW_H / 2f, t.w) }
/*วาด HTML ให้กว้างเท่า targetW พอดี ด้วยการปรับ textScaleX — ตำแหน่งไม่เพี้ยนจากต้นฉบับ */
private fun drawFitted(c: Canvas, html: String, x: Float, centerY: Float, targetW: Float) { val sp = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
if (sp.isEmpty())
return
val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = family;
color = Color.BLACK
textSize = 30f;
isSubpixelText = true;
isLinearText = true
}
val natural = StaticLayout.Builder.obtain(sp, 0, sp.length, p, 1 shl 22).build()
val w = natural.getLineWidth(0)
if (w <= 0.1f)
return
p.textScaleX = targetW / w
val lay = StaticLayout.Builder .obtain(sp, 0, sp.length, p, Math.ceil(targetW.toDouble()).toInt() + 16).build()
val fm = p.fontMetrics
val baseline = centerY - (fm.ascent + fm.descent) / 2f
c.save()
c.translate(x, baseline - lay.getLineBaseline(0))
lay.draw(c)
c.restore() }
//  ---------- รูปบัตร ----------
private fun drawCard(c: Canvas, d: FormData) { val bmp = d.cardPath?.let { BitmapFactory.decodeFile(it) } ?: return
val r = Spec.card
val box = RectF(r[0] + 6, r[1] + 6, r[0] + r[2] - 6, r[1] + r[3] - 6)
val s = minOf(box.width() / bmp.width, box.height() / bmp.height)
val dw = bmp.width * s
val dh = bmp.height * s
val dst = RectF( box.centerX() - dw / 2, box.centerY() - dh / 2, box.centerX() + dw / 2, box.centerY() + dh / 2 )
c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
bmp.recycle() }
//  ---------- เครื่องหมายถูก ----------
private fun drawChecks(c: Canvas, d: FormData) { val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE;
color = Color.BLACK
strokeWidth = Spec.CHK
strokeCap = Paint.Cap.ROUND;
strokeJoin = Paint.Join.ROUND
}
Spec.boxes.forEachIndexed { i, b -> if (!d.check(i))
return@forEachIndexed
val x = b[0];
val y = b[1];
val w = b[2];
val h = b[3]
val path = Path().apply { moveTo(x + w * 0.20f, y + h * 0.52f)
lineTo(x + w * 0.44f, y + h * 0.80f)
lineTo(x + w * 0.85f, y + h * 0.16f) }
c.drawPath(path, p) } }
//  ---------- ค่าที่ผู้ใช้กรอก ----------
private fun drawValues(c: Canvas, d: FormData) { val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = family;
color = Color.BLACK
textSize = Spec.VAL_FS;
isSubpixelText = true;
isLinearText = true
}
Spec.fields.forEachIndexed { i, f -> val v = d.field(i).trim()
if (v.isEmpty())
return@forEachIndexed
val maxW = f[2] - f[0] - 10f
p.textScaleX = 1f
val w = p.measureText(v)
if (w > maxW)
p.textScaleX = (maxW / w).coerceAtLeast(0.55f)
c.drawText(v, f[0] + 5f, f[1] - 7f, p) //  นั่งบนเส้นประพอดี
} }
//  ---------- ลายเซ็น (ส่วนเสริม ปิดได้) ----------
private fun drawSignature(c: Canvas, d: FormData) { if (!d.withSignature)
return
val bmp = d.signaturePath?.let { BitmapFactory.decodeFile(it) } ?: return
val r = Spec.signature
val s = minOf(r[2] / bmp.width, r[3] / bmp.height)
val dw = bmp.width * s;
val dh = bmp.height * s
c.drawBitmap( bmp, null, RectF(r[0], r[1] + (r[3] - dh) / 2, r[0] + dw, r[1] + (r[3] + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) )
bmp.recycle() } }
