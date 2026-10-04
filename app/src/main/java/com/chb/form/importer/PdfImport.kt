package com.chb.form.importer

import android.content.Context
import android.net.Uri
import com.chb.form.model.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.*
import java.io.File
import java.util.UUID

object PdfImport {

    data class Report(
        val template: FormTemplate,
        val textCount: Int,
        val segCount: Int,
        val boxCount: Int,
        val acroFields: Int,
        val detectedFields: Int,
        val warnings: List<FormWarning>
    )

    fun import(ctx: Context, uri: Uri): Report {
        val warn = mutableListOf<FormWarning>()
        val local = File(ctx.filesDir, "templates/${System.currentTimeMillis()}.pdf").apply {
            parentFile?.mkdirs()
        }
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                local.outputStream().use { input.copyTo(it) }
            } ?: run {
                warn += FormWarning(WarningLevel.ERROR, "open_fail", "เปิดไฟล์ PDF ไม่ได้")
                return emptyReport(warn)
            }
        } catch (e: Exception) {
            warn += FormWarning(WarningLevel.ERROR, "copy_fail", "คัดลอกไฟล์ไม่สำเร็จ: ${e.message}")
            return emptyReport(warn)
        }
        return try {
            PDDocument.load(local).use { doc ->
                if (doc.numberOfPages < 1) {
                    warn += FormWarning(WarningLevel.ERROR, "no_pages", "PDF ไม่มีหน้า")
                    return emptyReport(warn)
                }
                if (doc.numberOfPages > 1) {
                    warn += FormWarning(WarningLevel.WARN, "multi_page",
                        "เอกสารมี ${doc.numberOfPages} หน้า — นำเข้าเฉพาะหน้าแรก")
                }
                val page = doc.getPage(0)
                val pw = page.mediaBox.width
                val ph = page.mediaBox.height
                val ts = TextScanner()
                runCatching { ts.getText(doc) }.onFailure {
                    warn += FormWarning(WarningLevel.WARN, "text_fail", "อ่านข้อความไม่สำเร็จ: ${it.message}")
                }
                val gs = GraphicsScanner(page)
                runCatching { gs.scan() }.onFailure {
                    warn += FormWarning(WarningLevel.WARN, "gfx_fail", "อ่านเส้น/กรอบไม่สำเร็จ: ${it.message}")
                }
                val acro = readAcroForm(doc, ph)
                if (acro.isEmpty()) {
                    warn += FormWarning(WarningLevel.INFO, "no_acro", "ไม่พบฟิลด์ AcroForm — ใช้การตรวจจับอัตโนมัติ")
                }
                val detected = if (acro.isNotEmpty()) acro else FieldDetector.detect(ts.runs, gs.segs, gs.boxes)
                if (detected.isEmpty()) {
                    warn += FormWarning(WarningLevel.WARN, "no_fields", "ตรวจไม่พบฟิลด์กรอก — ผู้ใช้เพิ่มเองได้ในขั้นตรวจ")
                } else if (acro.isEmpty()) {
                    warn += FormWarning(WarningLevel.INFO, "heuristic",
                        "ตรวจพบฟิลด์โดยประมาณ ${detected.size} ช่อง — ควรตรวจและแก้ก่อนใช้งาน")
                }
                val name = (uri.lastPathSegment ?: "form").substringAfterLast('/').removeSuffix(".pdf").ifBlank { "form" }
                val tpl = FormTemplate(
                    id = UUID.randomUUID().toString().take(8),
                    name = name, pageW = pw, pageH = ph,
                    texts = ts.runs, segs = gs.segs.filter { it.length > 3f }, boxes = gs.boxes,
                    fields = detected, backgroundPdfPath = local.absolutePath, builtinType = null
                )
                Report(tpl, ts.runs.size, tpl.segs.size, gs.boxes.size, acro.size, detected.size, warn)
            }
        } catch (e: Exception) {
            warn += FormWarning(WarningLevel.ERROR, "load_fail", "โหลด PDF ไม่สำเร็จ: ${e.message}")
            emptyReport(warn)
        }
    }

    private fun emptyReport(warn: List<FormWarning>) = Report(
        FormTemplate(id = "empty", name = "empty"), 0, 0, 0, 0, 0, warn
    )

    private fun readAcroForm(doc: PDDocument, ph: Float): List<FieldSpec> {
        val form: PDAcroForm = doc.documentCatalog?.acroForm ?: return emptyList()
        val out = mutableListOf<FieldSpec>()
        fun walk(fields: List<PDField>) {
            fields.forEach { f ->
                if (f is PDNonTerminalField) { walk(f.children); return@forEach }
                f.widgets.forEach { w ->
                    val r = w.rectangle ?: return@forEach
                    val kind = when (f) {
                        is PDCheckBox -> FieldKind.CHECK
                        is PDRadioButton -> FieldKind.RADIO
                        is PDChoice -> FieldKind.CHOICE
                        is PDSignatureField -> FieldKind.SIGNATURE
                        is PDTextField -> if (f.isMultiline) FieldKind.MULTILINE else FieldKind.TEXT
                        else -> FieldKind.TEXT
                    }
                    val opts = (f as? PDChoice)?.options.orEmpty().map { FieldOption(it, it) }
                    out += FieldSpec(
                        key = FieldDetector.sanitize(f.fullyQualifiedName),
                        label = f.alternateFieldName?.takeIf { it.isNotBlank() } ?: f.partialName.orEmpty(),
                        kind = kind,
                        x = r.lowerLeftX, y = ph - r.upperRightY, w = r.width, h = r.height,
                        bounds = Bounds(r.lowerLeftX, ph - r.upperRightY, r.width, r.height),
                        group = (f as? PDRadioButton)?.fullyQualifiedName,
                        options = opts,
                        fontSize = (r.height * 0.62f).coerceIn(6f, 16f),
                        required = f.isRequired,
                        origin = FieldOrigin.ACROFORM, source = "acroform", approved = true
                    )
                }
            }
        }
        runCatching { walk(form.fields) }
        return out
    }
}
