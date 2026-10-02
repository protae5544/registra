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
        drawApplicants(c, data)
        drawAffiliation(c, data)
        drawStayDetails(c, data)
        drawGuarantor(c, data)
        drawFooter(c)

        c.restore()
        doc.finishPage(page)
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun textPaint(size: Float, bold: Boolean = false): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (bold) Typeface.create(family, Typeface.BOLD) else family
            textSize = size
            color = Color.BLACK
            isSubpixelText = true
        }
    }

    private fun drawHeader(c: Canvas, d: DormFormData) {
        val pTitle = textPaint(36f, true)
        val pNormal = textPaint(26f)

        c.drawText("CHRISTIANI & NIELSEN", 60f, 70f, pTitle)
        c.drawText("แบบคำร้องขออนุญาตเปิดห้องพักคนงาน", 60f, 115f, pTitle)
        c.drawText("หน่วยงาน COCONUT 1 (ON.11867)", 60f, 155f, pNormal)

        c.drawText("ลำดับที่: ${d.sequenceNo}", 1200f, 70f, pNormal)
        c.drawText("วันที่: ${d.documentDate}", 1200f, 110f, pNormal)

        c.drawLine(40f, 180f, 1745f, 180f, Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        })
    }

    private fun drawApplicants(c: Canvas, d: DormFormData) {
        val pLabel = textPaint(26f, true)
        val pVal = textPaint(26f)
        var y = 230f

        c.drawText("(1) ข้าพเจ้า", 60f, y, pLabel)
        y += 45f

        d.applicants.forEachIndexed { i, a ->
            if (a.name.isNotBlank() || a.position.isNotBlank()) {
                c.drawText("(${i + 1}) นาย/นาง/นางสาว  ${a.name}", 80f, y, pVal)
                c.drawText("ตำแหน่ง  ${a.position}", 1100f, y, pVal)
                y += 40f
            }
        }
    }

    private fun drawAffiliation(c: Canvas, d: DormFormData) {
        val pLabel = textPaint(26f, true)
        val pVal = textPaint(26f)
        var y = 420f

        c.drawText("สังกัดหน่วยงาน", 60f, y, pLabel)
        y += 40f

        val typeText = when (d.affiliationType) {
            DormFormData.AffiliationType.CN_COMPANY -> "☑ บริษัท กริสเตียนีและนีลเส็น (ไทย) จำกัด (มหาชน)"
            DormFormData.AffiliationType.CONTRACTOR -> "☑ ผู้รับเหมางาน"
            DormFormData.AffiliationType.OTHER -> "☑ อื่นๆ (${d.otherAffiliation})"
            else -> "☐ บริษัท CN    ☐ ผู้รับเหมา    ☐ อื่นๆ"
        }
        c.drawText(typeText, 80f, y, pVal)
        y += 40f

        if (d.headOfTeamCn.isNotBlank()) {
            c.drawText("หัวหน้าชุด: ${d.headOfTeamCn}", 80f, y, pVal)
            y += 35f
        }
        if (d.foremanCn.isNotBlank()) {
            c.drawText("โฟร์แมน: ${d.foremanCn}", 80f, y, pVal)
            y += 35f
        }
        if (d.headOfTeamContractor.isNotBlank()) {
            c.drawText("หัวหน้าชุด (ผู้รับเหมา): ${d.headOfTeamContractor}", 80f, y, pVal)
            y += 35f
        }
        if (d.supervisor.isNotBlank()) {
            c.drawText("หัวหน้าคนงาน: ${d.supervisor}", 80f, y, pVal)
            y += 35f
        }
        if (d.contractMaker.isNotBlank()) {
            c.drawText("ผู้ทำสัญญา: ${d.contractMaker}", 80f, y, pVal)
        }
    }

    private fun drawStayDetails(c: Canvas, d: DormFormData) {
        val pLabel = textPaint(26f, true)
        val pVal = textPaint(26f)
        var y = 700f

        c.drawText("มีความประสงค์ขอเข้าพักอาศัยในบ้านพักคนงานของหน่วยงาน", 60f, y, pLabel)
        y += 40f
        c.drawText("จำนวน ${d.days.ifBlank { "....." }} วัน", 80f, y, pVal)
        y += 40f
        c.drawText("ตั้งแต่วันที่ ${d.startDate.ifBlank { "............" }}  ถึงวันที่ ${d.endDate.ifBlank { "............" }}", 80f, y, pVal)
    }

    private fun drawGuarantor(c: Canvas, d: DormFormData) {
        val pLabel = textPaint(26f, true)
        val pVal = textPaint(26f)
        var y = 850f

        c.drawText("ผู้บังคับบัญชา / ผู้ค้ำประกัน", 60f, y, pLabel)
        y += 40f
        c.drawText("ชื่อ-นามสกุล: ${d.guarantorName}", 80f, y, pVal)
        y += 35f
        c.drawText("ตำแหน่ง: ${d.guarantorPosition}", 80f, y, pVal)
        y += 35f
        c.drawText("บริษัท/หน่วยงาน: ${d.guarantorCompany}", 80f, y, pVal)
    }

    private fun drawFooter(c: Canvas) {
        val p = textPaint(24f)
        c.drawText("ลงชื่อ ........................................ ผู้ขออนุญาต", 80f, 1100f, p)
        c.drawText("ลงชื่อ ........................................ ผู้อนุญาตให้เปิดห้องพัก", 900f, 1100f, p)
        c.drawText("** หมายเหตุ ** เอกสารที่ต้องแนบ: สำเนาบัตรประจำตัวประชาชน คนละ 1 แผ่น พร้อมเบอร์โทรที่ติดต่อได้", 60f, 1200f, textPaint(22f))
    }
}
