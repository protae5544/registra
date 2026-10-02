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

        drawHeaderBox(c, data)
        drawApplicants(c, data)
        drawAffiliation(c, data)
        drawIntention(c, data)
        drawGuarantor(c, data)
        drawSignatures(c)
        drawNoteSection(c)

        c.restore()
        doc.finishPage(page)
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun tp(size: Float, bold: Boolean = false): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (bold) Typeface.create(family, Typeface.BOLD) else family
            textSize = size
            color = Color.BLACK
            isSubpixelText = true
        }
    }

    private fun stroke(w: Float = 2.2f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = w
    }

    private fun dash(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 1.8f
        pathEffect = DashPathEffect(floatArrayOf(2f, 5f), 0f)
    }

    private fun checkBox(c: Canvas, x: Float, y: Float, checked: Boolean) {
        c.drawRect(x, y, x + 26f, y + 22f, stroke(2f))
        if (checked) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.BLACK
                strokeWidth = 3.2f
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(x + 4f, y + 11f, x + 10f, y + 17f, p)
            c.drawLine(x + 10f, y + 17f, x + 22f, y + 4f, p)
        }
    }

    private fun drawHeaderBox(c: Canvas, d: DormFormData) {
        val s = stroke()
        c.drawRect(46f, 46f, 1740f, 191f, s)
        c.drawLine(1295f, 46f, 1295f, 191f, s)
        c.drawLine(46f, 118f, 1295f, 118f, s)

        val pTitle = tp(28f, true)
        val pSub = tp(20f)
        val pSmall = tp(17f)

        c.drawCircle(110f, 95f, 38f, stroke(2.5f))
        c.drawText("CN", 92f, 103f, tp(18f, true))

        c.drawText("แบบคำร้องขออนุญาตเปิดห้องพักคนงาน", 320f, 90f, pTitle)
        c.drawText("หน่วยงาน COCONUT 1 (ON.11867)", 380f, 155f, pSub)

        c.drawText("ลำดับที่......................", 1310f, 85f, pSmall)
        c.drawText("(สำหรับเจ้าหน้าที่)", 1310f, 108f, tp(14f))
        c.drawText(d.sequenceNo, 1450f, 85f, tp(18f))

        c.drawText("วันที่......../......../..........", 1310f, 155f, pSmall)
        c.drawText(d.documentDate, 1400f, 155f, tp(18f))
    }

    private fun drawApplicants(c: Canvas, d: DormFormData) {
        val pLabel = tp(20f, true)
        val p = tp(18f)
        val pVal = tp(20f)

        c.drawText("(1) ข้าพเจ้า", 60f, 240f, pLabel)

        val rows = listOf(275f, 320f, 365f)
        d.applicants.forEachIndexed { i, a ->
            val y = rows[i]
            c.drawText("(${i + 1}) นาย/นาง/นางสาว", 80f, y, p)
            c.drawText(a.name, 320f, y, pVal)
            c.drawLine(320f, y + 5f, 1050f, y + 5f, dash())

            c.drawText("ตำแหน่ง", 1080f, y, p)
            c.drawText(a.position, 1180f, y, pVal)
            c.drawLine(1180f, y + 5f, 1700f, y + 5f, dash())
        }
    }

    private fun drawAffiliation(c: Canvas, d: DormFormData) {
        val pLabel = tp(20f, true)
        val p = tp(17f)
        val pVal = tp(19f)

        c.drawText("สังกัดหน่วยงาน", 60f, 420f, pLabel)

        val isCn = d.affiliationType == DormFormData.AffiliationType.CN_COMPANY
        val isCon = d.affiliationType == DormFormData.AffiliationType.CONTRACTOR
        val isOther = d.affiliationType == DormFormData.AffiliationType.OTHER

        checkBox(c, 90f, 440f, isCn)
        c.drawText("บริษัท กริสเตียนีและนีลเส็น (ไทย) จำกัด (มหาชน)", 130f, 458f, p)

        c.drawText("หัวหน้าชุดชื่อ", 130f, 500f, p)
        c.drawText(d.headOfTeamCn, 280f, 500f, pVal)
        c.drawLine(280f, 505f, 700f, 505f, dash())

        c.drawText("โฟร์แมนชื่อ", 130f, 540f, p)
        c.drawText(d.foremanCn, 280f, 540f, pVal)
        c.drawLine(280f, 545f, 700f, 545f, dash())

        checkBox(c, 780f, 440f, isCon)
        c.drawText("ผู้รับเหมางาน", 820f, 458f, p)

        c.drawText("หัวหน้าชุดชื่อ", 820f, 500f, p)
        c.drawText(d.headOfTeamContractor, 980f, 500f, pVal)
        c.drawLine(980f, 505f, 1680f, 505f, dash())

        c.drawText("หัวหน้าคนงานชื่อ", 820f, 540f, p)
        c.drawText(d.supervisor, 1000f, 540f, pVal)
        c.drawLine(1000f, 545f, 1680f, 545f, dash())

        c.drawText("ผู้ทำสัญญาชื่อ", 820f, 580f, p)
        c.drawText(d.contractMaker, 980f, 580f, pVal)
        c.drawLine(980f, 585f, 1680f, 585f, dash())

        checkBox(c, 90f, 575f, isOther)
        c.drawText("อื่นๆ (โปรดระบุ)", 130f, 593f, p)
        c.drawText(d.otherAffiliation, 300f, 593f, pVal)
        c.drawLine(300f, 598f, 700f, 598f, dash())
    }

    private fun drawIntention(c: Canvas, d: DormFormData) {
        val p = tp(17f)
        val pVal = tp(19f)
        var y = 650f

        c.drawText("มีความประสงค์ขอเข้าพักอาศัยในบ้านพักคนงานของหน่วยงาน หน่วยงาน COCONUT 1 จำนวน", 60f, y, p)
        c.drawText(d.days.ifBlank { "....." }, 1420f, y, pVal)
        c.drawText("วัน", 1520f, y, p)
        y += 32f
        c.drawText("โดยจะปฏิบัติตามกฎของบ้านพักคนงานทุกประการ และหากข้าพเจ้าหรือบุคคลที่ข้าพเจ้านำพาเข้ามาอาศัยด้วย", 60f, y, p)
        y += 28f
        c.drawText("กระทำการอื่นใดที่ผิดต่อกฎของบ้านพักคนงาน ข้าพเจ้าและบุคคลเหล่านั้น ยินยอมย้ายออกจากบ้านพักทันที โดยไม่ต้องแจ้งให้หน่วยงานทราบ", 60f, y, p)
        y += 40f
        c.drawText("ข้าพเจ้าตกลงยินยอมจ่ายเงินค่าใช้จ่ายต่างๆ ของบ้านพักคนงานตามรายละเอียดที่กำหนดไว้ โดยมีผู้บังคับบัญชาของข้าพเจ้า คือ", 60f, y, p)
    }

    private fun drawGuarantor(c: Canvas, d: DormFormData) {
        val p = tp(17f)
        val pVal = tp(19f)
        var y = 800f

        c.drawText("นาย/นาง/นางสาว", 60f, y, p)
        c.drawText(d.guarantorName, 250f, y, pVal)
        c.drawLine(250f, y + 5f, 780f, y + 5f, dash())

        c.drawText("ตำแหน่ง", 800f, y, p)
        c.drawText(d.guarantorPosition, 890f, y, pVal)
        c.drawLine(890f, y + 5f, 1200f, y + 5f, dash())

        c.drawText("บริษัท/หน่วยงาน", 1220f, y, p)
        c.drawText(d.guarantorCompany, 1400f, y, pVal)
        c.drawLine(1400f, y + 5f, 1700f, y + 5f, dash())

        y += 40f
        c.drawText("เป็นผู้รับรอง และมีความประสงค์อยู่บ้านพักคนงาน ตั้งแต่วันที่", 60f, y, p)
        c.drawText(d.startDate.ifBlank { "............" }, 720f, y, pVal)
        c.drawLine(720f, y + 5f, 1050f, y + 5f, dash())
        c.drawText("ถึงวันที่", 1070f, y, p)
        c.drawText(d.endDate.ifBlank { "............" }, 1160f, y, pVal)
        c.drawLine(1160f, y + 5f, 1500f, y + 5f, dash())
    }

    private fun drawSignatures(c: Canvas) {
        val p = tp(18f)
        val pSmall = tp(15f)

        c.drawText("จึงเรียนมาเพื่อขออนุญาตเปิดห้องพัก", 60f, 900f, p)

        c.drawText("ลงชื่อ .............................................. ผู้ขออนุญาต", 80f, 1000f, p)
        c.drawText("( ........................................................ )", 100f, 1040f, pSmall)

        c.drawText("ลงชื่อ .............................................. ผู้อนุญาตให้เปิดห้องพัก", 900f, 1000f, p)
        c.drawText("( ........................................................ )", 920f, 1040f, pSmall)

        c.drawRect(46f, 210f, 1740f, 1100f, stroke())
    }

    private fun drawNoteSection(c: Canvas) {
        val pLabel = tp(18f, true)
        val p = tp(16f)
        val s = stroke()

        c.drawRect(46f, 1130f, 1740f, 2450f, s)

        c.drawText("** หมายเหตุ ** เอกสารที่ต้องแนบ", 70f, 1175f, pLabel)
        c.drawText("1. เอกสาร สำเนาบัตรประจำตัวประชาชน คนละ 1 แผ่น พร้อมเบอร์โทรที่ติดต่อได้", 90f, 1215f, p)

        c.drawText("(2) บันทึกของผู้ดูแลบ้านพัก", 70f, 1280f, pLabel)

        c.drawText("1. การย้ายเข้า : หลังที่ ............... ห้องที่ ...............", 90f, 1330f, p)
        c.drawText("เปิดห้องพักให้ เมื่อวันที่ ......../......../..........", 90f, 1375f, p)
        c.drawText("อุปกรณ์ในห้องพัก ........................................................................", 90f, 1420f, p)
        c.drawText("เบอร์โทรติดต่อผู้เข้าห้องพัก ....................................................", 90f, 1465f, p)
        c.drawText("ลงชื่อ .............................................. ผู้บันทึกเข้า", 90f, 1520f, p)

        c.drawText("2. การย้ายออก", 90f, 1600f, pLabel)
        c.drawText("ย้ายออกเมื่อวันที่ ......../......../..........", 90f, 1645f, p)
        c.drawText("อุปกรณ์ในห้องพัก ........................................................................", 90f, 1690f, p)
        c.drawText("ลงชื่อ .............................................. ผู้บันทึก", 90f, 1750f, p)
    }
}
