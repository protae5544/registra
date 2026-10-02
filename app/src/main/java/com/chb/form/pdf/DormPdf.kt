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

        drawOuterFrames(c)
        drawHeader(c, data)
        drawApplicants(c, data)
        drawAffiliation(c, data)
        drawIntention(c, data)
        drawGuarantor(c, data)
        drawSignatures(c)
        drawNoteAndRecord(c)

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

    private fun stroke(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = DormSpec.STROKE
    }

    private fun drawOuterFrames(c: Canvas) {
        val s = stroke()
        DormSpec.frames.forEach {
            c.drawRect(it[0], it[1], it[0] + it[2], it[1] + it[3], s)
        }
    }

    private fun drawHeader(c: Canvas, d: DormFormData) {
        val pTitle = tp(DormSpec.TITLE_FS, true)
        val pNormal = tp(DormSpec.STATIC_FS)
        val pSmall = tp(DormSpec.SMALL_FS)

        c.drawText("CHRISTIANI & NIELSEN", 70f, 85f, pTitle)
        c.drawText("แบบคำร้องขออนุญาตเปิดห้องพักคนงาน", 70f, 130f, pTitle)
        c.drawText("หน่วยงาน COCONUT 1 (ON.11867)", 70f, 165f, pNormal)

        c.drawText("ลำดับที่", 1480f, 75f, pSmall)
        c.drawText(d.sequenceNo.ifBlank { "...................." }, 1480f, 105f, pNormal)
        c.drawText("วันที่", 1480f, 135f, pSmall)
        c.drawText(d.documentDate.ifBlank { "....../....../........" }, 1480f, 165f, pNormal)
    }

    private fun drawApplicants(c: Canvas, d: DormFormData) {
        val pLabel = tp(DormSpec.STATIC_FS, true)
        val pVal = tp(DormSpec.VAL_FS)
        val pSmall = tp(DormSpec.SMALL_FS)

        c.drawText("(1) ข้าพเจ้า", 70f, 250f, pLabel)

        val labels = listOf("(1)", "(2)", "(3)")
        var y = 300f
        d.applicants.forEachIndexed { i, a ->
            c.drawText("${labels[i]} นาย/นาง/นางสาว", 80f, y, pSmall)
            c.drawText(a.name, 340f, y, pVal)
            c.drawText("ตำแหน่ง", 1100f, y, pSmall)
            c.drawText(a.position, 1280f, y, pVal)
            c.drawLine(340f, y + 6f, 1050f, y + 6f, dash())
            c.drawLine(1280f, y + 6f, 1680f, y + 6f, dash())
            y += 50f
        }
    }

    private fun drawAffiliation(c: Canvas, d: DormFormData) {
        val pLabel = tp(DormSpec.STATIC_FS, true)
        val pVal = tp(DormSpec.VAL_FS)
        val pSmall = tp(DormSpec.SMALL_FS)
        val s = stroke()

        c.drawText("สังกัดหน่วยงาน", 70f, 460f, pLabel)

        fun checkBox(x: Float, y: Float, checked: Boolean) {
            c.drawRect(x, y, x + 28f, y + 24f, s)
            if (checked) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = Color.BLACK
                    strokeWidth = 3.5f
                    strokeCap = Paint.Cap.ROUND
                }
                c.drawLine(x + 5f, y + 12f, x + 11f, y + 19f, p)
                c.drawLine(x + 11f, y + 19f, x + 23f, y + 5f, p)
            }
        }

        val isCn = d.affiliationType == DormFormData.AffiliationType.CN_COMPANY
        val isContractor = d.affiliationType == DormFormData.AffiliationType.CONTRACTOR
        val isOther = d.affiliationType == DormFormData.AffiliationType.OTHER

        checkBox(90f, 480f, isCn)
        c.drawText("บริษัท กริสเตียนีและนีลเส็น (ไทย) จำกัด (มหาชน)", 130f, 500f, pSmall)

        checkBox(920f, 480f, isContractor)
        c.drawText("ผู้รับเหมางาน", 960f, 500f, pSmall)

        c.drawText("หัวหน้าชุดชื่อ", 90f, 545f, pSmall)
        c.drawText(d.headOfTeamCn, 300f, 545f, pVal)
        c.drawLine(300f, 551f, 700f, 551f, dash())

        c.drawText("โฟร์แมนชื่อ", 90f, 585f, pSmall)
        c.drawText(d.foremanCn, 300f, 585f, pVal)
        c.drawLine(300f, 591f, 700f, 591f, dash())

        c.drawText("หัวหน้าชุดชื่อ", 920f, 545f, pSmall)
        c.drawText(d.headOfTeamContractor, 1150f, 545f, pVal)
        c.drawLine(1150f, 551f, 1650f, 551f, dash())

        c.drawText("หัวหน้าคนงานชื่อ", 920f, 585f, pSmall)
        c.drawText(d.supervisor, 1150f, 585f, pVal)
        c.drawLine(1150f, 591f, 1650f, 591f, dash())

        c.drawText("ผู้ทำสัญญาชื่อ", 920f, 625f, pSmall)
        c.drawText(d.contractMaker, 1150f, 625f, pVal)
        c.drawLine(1150f, 631f, 1650f, 631f, dash())

        checkBox(90f, 640f, isOther)
        c.drawText("อื่นๆ (โปรดระบุ)", 130f, 660f, pSmall)
        c.drawText(d.otherAffiliation, 300f, 660f, pVal)
        c.drawLine(300f, 666f, 700f, 666f, dash())
    }

    private fun drawIntention(c: Canvas, d: DormFormData) {
        val p = tp(DormSpec.STATIC_FS)
        val pVal = tp(DormSpec.VAL_FS)
        var y = 720f

        c.drawText("มีความประสงค์ขอเข้าพักอาศัยในบ้านพักคนงานของหน่วยงาน หน่วยงาน COCONUT 1 จำนวน", 70f, y, p)
        c.drawText(d.days.ifBlank { "....." }, 1450f, y, pVal)
        c.drawText("วัน", 1550f, y, p)
        y += 35f

        c.drawText("โดยจะปฏิบัติตามกฎของบ้านพักคนงานทุกประการ และหากข้าพเจ้าหรือบุคคลที่ข้าพเจ้านำพาเข้ามาอาศัยด้วย", 70f, y, p)
        y += 32f
        c.drawText("กระทำการอื่นใดที่ผิดต่อกฎของบ้านพักคนงาน ข้าพเจ้าและบุคคลเหล่านั้น ยินยอมย้ายออกจากบ้านพักทันที โดยไม่ต้องแจ้งให้หน่วยงานทราบ", 70f, y, p)
        y += 45f

        c.drawText("ข้าพเจ้าตกลงยินยอมจ่ายเงินค่าใช้จ่ายต่างๆ ของบ้านพักคนงานตามรายละเอียดที่กำหนดไว้ โดยมีผู้บังคับบัญชาของข้าพเจ้า คือ", 70f, y, p)
    }

    private fun drawGuarantor(c: Canvas, d: DormFormData) {
        val pSmall = tp(DormSpec.SMALL_FS)
        val pVal = tp(DormSpec.VAL_FS)
        var y = 870f

        c.drawText("นาย/นาง/นางสาว", 70f, y, pSmall)
        c.drawText(d.guarantorName, 280f, y, pVal)
        c.drawLine(280f, 876f, 900f, 876f, dash())

        c.drawText("ตำแหน่ง", 920f, y, pSmall)
        c.drawText(d.guarantorPosition, 1020f, y, pVal)
        c.drawLine(1020f, 876f, 1350f, 876f, dash())

        c.drawText("บริษัท/หน่วยงาน", 1370f, y, pSmall)
        c.drawText(d.guarantorCompany, 1550f, y, pVal)
        c.drawLine(1550f, 876f, 1680f, 876f, dash())

        y += 45f
        c.drawText("เป็นผู้รับรอง และมีความประสงค์อยู่บ้านพักคนงาน ตั้งแต่วันที่", 70f, y, pSmall)
        c.drawText(d.startDate.ifBlank { "............" }, 780f, y, pVal)
        c.drawLine(780f, y + 6f, 1100f, y + 6f, dash())
        c.drawText("ถึงวันที่", 1120f, y, pSmall)
        c.drawText(d.endDate.ifBlank { "............" }, 1220f, y, pVal)
        c.drawLine(1220f, y + 6f, 1550f, y + 6f, dash())
    }

    private fun drawSignatures(c: Canvas) {
        val p = tp(DormSpec.STATIC_FS)
        val pSmall = tp(DormSpec.SMALL_FS)

        c.drawText("จึงเรียนมาเพื่อขออนุญาตเปิดห้องพัก", 70f, 1000f, p)

        c.drawText("ลงชื่อ .............................................. ผู้ขออนุญาต", 100f, 1120f, p)
        c.drawText("( ........................................................ )", 120f, 1160f, pSmall)

        c.drawText("ลงชื่อ .............................................. ผู้อนุญาตให้เปิดห้องพัก", 950f, 1120f, p)
        c.drawText("( ........................................................ )", 970f, 1160f, pSmall)
    }

    private fun drawNoteAndRecord(c: Canvas) {
        val pLabel = tp(DormSpec.STATIC_FS, true)
        val p = tp(DormSpec.SMALL_FS)
        val s = stroke()

        c.drawLine(50f, 1280f, 1735f, 1280f, s)

        c.drawText("** หมายเหตุ ** เอกสารที่ต้องแนบ", 70f, 1320f, pLabel)
        c.drawText("1. เอกสาร สำเนาบัตรประจำตัวประชาชน คนละ 1 แผ่น พร้อมเบอร์โทรที่ติดต่อได้", 90f, 1355f, p)

        c.drawText("(2) บันทึกของผู้ดูแลบ้านพัก", 70f, 1410f, pLabel)

        c.drawText("1. การย้ายเข้า : หลังที่ ............... ห้องที่ ...............", 90f, 1450f, p)
        c.drawText("เปิดห้องพักให้ เมื่อวันที่ ....../....../..........", 90f, 1485f, p)
        c.drawText("อุปกรณ์ในห้องพัก ........................................................................", 90f, 1520f, p)
        c.drawText("เบอร์โทรติดต่อผู้เข้าห้องพัก ....................................................", 90f, 1555f, p)
        c.drawText("ลงชื่อ .............................................. ผู้บันทึกเข้า", 90f, 1600f, p)

        c.drawText("2. การย้ายออก", 90f, 1660f, pLabel)
        c.drawText("ย้ายออกเมื่อวันที่ ....../....../..........", 90f, 1695f, p)
        c.drawText("อุปกรณ์ในห้องพัก ........................................................................", 90f, 1730f, p)
        c.drawText("ลงชื่อ .............................................. ผู้บันทึก", 90f, 1775f, p)
    }

    private fun dash(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = DormSpec.DOT_W
        pathEffect = DashPathEffect(floatArrayOf(1f, DormSpec.DOT_GAP), 0f)
    }
}
