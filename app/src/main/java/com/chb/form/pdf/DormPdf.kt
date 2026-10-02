package com.chb.form.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.chb.form.R
import com.chb.form.data.DormFormData
import com.chb.form.data.DormSpec
import java.io.File
import java.io.FileOutputStream

class DormPdf(private val ctx: Context) {

    private val family: Typeface =
        ResourcesCompat.getFont(ctx, R.font.sarabun_regular) ?: Typeface.DEFAULT

    private val ptW = Math.round(DormSpec.MM_W / 25.4f * 72f)
    private val ptH = Math.round(DormSpec.MM_H / 25.4f * 72f)

    fun render(data: DormFormData, out: File): File {
        out.parentFile?.mkdirs()
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(ptW, ptH, 1).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)
        c.save()
        c.scale(ptW / DormSpec.W, ptH / DormSpec.H)

        drawHeader(c, data)
        drawMainFrame(c)
        drawApplicants(c, data)
        drawAffiliation(c, data)
        drawIntention(c, data)
        drawGuarantor(c, data)
        drawSignatures(c)
        drawBottomSection(c)

        c.restore()
        doc.finishPage(page)
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun tp(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (bold) Typeface.create(family, Typeface.BOLD) else family
        textSize = size
        color = Color.BLACK
        isSubpixelText = true
    }

    private fun stroke(w: Float = 2.0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = w
    }

    private fun dash() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.DKGRAY
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(1.5f, 4.5f), 0f)
    }

    private fun check(c: Canvas, x: Float, y: Float, on: Boolean) {
        c.drawRect(x, y, x + 24f, y + 20f, stroke(1.8f))
        if (on) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.BLACK
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(x + 4f, y + 10f, x + 9f, y + 15f, p)
            c.drawLine(x + 9f, y + 15f, x + 20f, y + 4f, p)
        }
    }

    private fun drawHeader(c: Canvas, d: DormFormData) {
        val s = stroke(2.2f)
        c.drawRect(40f, 35f, 1745f, 180f, s)
        c.drawLine(1260f, 35f, 1260f, 180f, s)
        c.drawLine(40f, 107f, 1260f, 107f, s)

        c.drawCircle(105f, 90f, 36f, stroke(2.5f))
        c.drawText("CN", 88f, 97f, tp(16f, true))
        c.drawText("CHRISTIANI & NIELSEN", 55f, 155f, tp(11f))

        c.drawText("แบบคำร้องขออนุญาตเปิดห้องพักคนงาน", 300f, 82f, tp(26f, true))
        c.drawText("หน่วยงาน COCONUT 1 (ON.11867)", 360f, 150f, tp(18f))

        c.drawText("ลำดับที่......................", 1275f, 75f, tp(15f))
        c.drawText("(สำหรับเจ้าหน้าที่)", 1275f, 95f, tp(12f))
        if (d.sequenceNo.isNotBlank()) c.drawText(d.sequenceNo, 1420f, 75f, tp(16f))

        c.drawText("วันที่......../......../..........", 1275f, 145f, tp(15f))
        if (d.documentDate.isNotBlank()) c.drawText(d.documentDate, 1380f, 145f, tp(16f))
    }

    private fun drawMainFrame(c: Canvas) {
        c.drawRect(40f, 190f, 1745f, 1580f, stroke(2.2f))
    }

    private fun drawApplicants(c: Canvas, d: DormFormData) {
        c.drawText("(1) ข้าพเจ้า", 55f, 230f, tp(18f, true))

        val ys = listOf(270f, 315f, 360f)
        d.applicants.forEachIndexed { i, a ->
            val y = ys[i]
            c.drawText("(${i + 1}) นาย/นาง/นางสาว", 70f, y, tp(16f))
            c.drawText(a.name, 310f, y, tp(18f))
            c.drawLine(310f, y + 4f, 1020f, y + 4f, dash())
            c.drawText("ตำแหน่ง", 1040f, y, tp(16f))
            c.drawText(a.position, 1140f, y, tp(18f))
            c.drawLine(1140f, y + 4f, 1700f, y + 4f, dash())
        }
    }

    private fun drawAffiliation(c: Canvas, d: DormFormData) {
        c.drawText("สังกัดหน่วยงาน", 55f, 410f, tp(18f, true))

        val cn = d.affiliationType == DormFormData.AffiliationType.CN_COMPANY
        val con = d.affiliationType == DormFormData.AffiliationType.CONTRACTOR
        val other = d.affiliationType == DormFormData.AffiliationType.OTHER

        check(c, 80f, 425f, cn)
        c.drawText("บริษัท กริสเตียนีและนีลเส็น (ไทย) จำกัด (มหาชน)", 115f, 442f, tp(15f))
        c.drawText("หัวหน้าชุดชื่อ", 115f, 480f, tp(15f))
        c.drawText(d.headOfTeamCn, 260f, 480f, tp(17f))
        c.drawLine(260f, 484f, 700f, 484f, dash())
        c.drawText("โฟร์แมนชื่อ", 115f, 515f, tp(15f))
        c.drawText(d.foremanCn, 250f, 515f, tp(17f))
        c.drawLine(250f, 519f, 700f, 519f, dash())

        check(c, 780f, 425f, con)
        c.drawText("ผู้รับเหมางาน", 815f, 442f, tp(15f))
        c.drawText("หัวหน้าชุดชื่อ", 815f, 480f, tp(15f))
        c.drawText(d.headOfTeamContractor, 960f, 480f, tp(17f))
        c.drawLine(960f, 484f, 1680f, 484f, dash())
        c.drawText("หัวหน้าคนงานชื่อ", 815f, 515f, tp(15f))
        c.drawText(d.supervisor, 980f, 515f, tp(17f))
        c.drawLine(980f, 519f, 1680f, 519f, dash())
        c.drawText("ผู้ทำสัญญาชื่อ", 815f, 550f, tp(15f))
        c.drawText(d.contractMaker, 960f, 550f, tp(17f))
        c.drawLine(960f, 554f, 1680f, 554f, dash())

        check(c, 80f, 550f, other)
        c.drawText("อื่นๆ (โปรดระบุ)", 115f, 567f, tp(15f))
        c.drawText(d.otherAffiliation, 280f, 567f, tp(17f))
        c.drawLine(280f, 571f, 700f, 571f, dash())
    }

    private fun drawIntention(c: Canvas, d: DormFormData) {
        var y = 620f
        val p = tp(15.5f)
        c.drawText("มีความประสงค์ขอเข้าพักอาศัยในบ้านพักคนงานของหน่วยงาน หน่วยงาน COCONUT 1 จำนวน", 55f, y, p)
        c.drawText(d.days.ifBlank { "....." }, 1400f, y, tp(17f))
        c.drawText("วัน", 1500f, y, p)
        y += 30f
        c.drawText("โดยจะปฏิบัติตามกฎของบ้านพักคนงานทุกประการ และหากข้าพเจ้าหรือบุคคลที่ข้าพเจ้านำพาเข้ามาอาศัยด้วย", 55f, y, p)
        y += 26f
        c.drawText("กระทำการอื่นใดที่ผิดต่อกฎของบ้านพักคนงาน ข้าพเจ้าและบุคคลเหล่านั้น ยินยอมย้ายออกจากบ้านพักทันที โดยไม่ต้องแจ้งให้หน่วยงานทราบ", 55f, y, p)
        y += 36f
        c.drawText("ข้าพเจ้าตกลงยินยอมจ่ายเงินค่าใช้จ่ายต่างๆ ของบ้านพักคนงานตามรายละเอียดที่กำหนดไว้ โดยมีผู้บังคับบัญชาของข้าพเจ้า คือ", 55f, y, p)
    }

    private fun drawGuarantor(c: Canvas, d: DormFormData) {
        var y = 760f
        val p = tp(15.5f)
        c.drawText("นาย/นาง/นางสาว", 55f, y, p)
        c.drawText(d.guarantorName, 230f, y, tp(17f))
        c.drawLine(230f, y + 4f, 750f, y + 4f, dash())
        c.drawText("ตำแหน่ง", 770f, y, p)
        c.drawText(d.guarantorPosition, 860f, y, tp(17f))
        c.drawLine(860f, y + 4f, 1150f, y + 4f, dash())
        c.drawText("บริษัท/หน่วยงาน", 1170f, y, p)
        c.drawText(d.guarantorCompany, 1350f, y, tp(17f))
        c.drawLine(1350f, y + 4f, 1700f, y + 4f, dash())

        y += 38f
        c.drawText("เป็นผู้รับรอง และมีความประสงค์อยู่บ้านพักคนงาน ตั้งแต่วันที่", 55f, y, p)
        c.drawText(d.startDate.ifBlank { "............" }, 700f, y, tp(17f))
        c.drawLine(700f, y + 4f, 1020f, y + 4f, dash())
        c.drawText("ถึงวันที่", 1040f, y, p)
        c.drawText(d.endDate.ifBlank { "............" }, 1130f, y, tp(17f))
        c.drawLine(1130f, y + 4f, 1480f, y + 4f, dash())
    }

    private fun drawSignatures(c: Canvas) {
        c.drawText("จึงเรียนมาเพื่อขออนุญาตเปิดห้องพัก", 55f, 870f, tp(16f))

        c.drawText("ลงชื่อ .............................................. ผู้ขออนุญาต", 80f, 980f, tp(16f))
        c.drawText("( ........................................................ )", 100f, 1020f, tp(14f))

        c.drawText("ลงชื่อ .............................................. ผู้อนุญาตให้เปิดห้องพัก", 900f, 980f, tp(16f))
        c.drawText("( ........................................................ )", 920f, 1020f, tp(14f))
    }

    private fun drawBottomSection(c: Canvas) {
        val s = stroke(2.2f)
        c.drawRect(40f, 1605f, 1745f, 2480f, s)

        c.drawText("** หมายเหตุ ** เอกสารที่ต้องแนบ", 60f, 1650f, tp(16f, true))
        c.drawText("1. เอกสาร สำเนาบัตรประจำตัวประชาชน คนละ 1 แผ่น พร้อมเบอร์โทรที่ติดต่อได้", 80f, 1690f, tp(14.5f))

        c.drawText("(2) บันทึกของผู้ดูแลบ้านพัก", 60f, 1755f, tp(16f, true))

        c.drawText("1. การย้ายเข้า : หลังที่ ............... ห้องที่ ...............", 80f, 1805f, tp(14.5f))
        c.drawText("เปิดห้องพักให้ เมื่อวันที่ ......../......../..........", 80f, 1845f, tp(14.5f))
        c.drawText("อุปกรณ์ในห้องพัก ........................................................................", 80f, 1885f, tp(14.5f))
        c.drawText("เบอร์โทรติดต่อผู้เข้าห้องพัก ....................................................", 80f, 1925f, tp(14.5f))
        c.drawText("ลงชื่อ .............................................. ผู้บันทึกเข้า", 80f, 1980f, tp(14.5f))

        c.drawText("2. การย้ายออก", 80f, 2055f, tp(15f, true))
        c.drawText("ย้ายออกเมื่อวันที่ ......../......../..........", 80f, 2095f, tp(14.5f))
        c.drawText("อุปกรณ์ในห้องพัก ........................................................................", 80f, 2135f, tp(14.5f))
        c.drawText("ลงชื่อ .............................................. ผู้บันทึก", 80f, 2190f, tp(14.5f))
    }
}
