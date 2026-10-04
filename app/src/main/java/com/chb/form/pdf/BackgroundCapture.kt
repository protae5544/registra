package com.chb.form.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * แคปพื้นหลังเป็นภาพ แล้ว normalize ให้พอดีขนาดที่ตั้ง
 *
 * ใช้เมื่อไม่ใช้ PDF ต้นฉบับเป็นพื้น — เก็บเป็น JPEG (เทียบเท่า canvas→base64)
 * และรองรับการแนบไฟล์ภาพ (jpg/png/webp) เป็นพื้นหลัง
 */
object BackgroundCapture {
    const val SCALE = 2f
    const val MAX_W = 3000
    const val MAX_H = 4200
    const val JPEG_QUALITY = 88
    const val SOURCE_DPI = 150f

    fun paperPoints(paper: PaperSize, srcW: Float, srcH: Float): Pair<Float, Float> {
        return if (paper == PaperSize.SOURCE || paper.wmm <= 0f) {
            srcW.coerceAtLeast(1f) to srcH.coerceAtLeast(1f)
        } else {
            (paper.wmm / 25.4f * 72f) to (paper.hmm / 25.4f * 72f)
        }
    }

    fun capturePixels(pageW: Float, pageH: Float, scale: Float = SCALE): Pair<Int, Int> {
        val w = Math.round(pageW * scale).coerceIn(1, MAX_W)
        val h = Math.round(pageH * scale).coerceIn(1, MAX_H)
        return w to h
    }

    /** contain/fit ลงกรอบเป้าหมาย — พื้นขาว จัดกลาง ไม่ยืดภาพ */
    fun normalize(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val tw = targetW.coerceAtLeast(1)
        val th = targetH.coerceAtLeast(1)
        if (src.width == tw && src.height == th && src.config == Bitmap.Config.ARGB_8888) return src
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        drawContained(c, src, tw.toFloat(), th.toFloat())
        return out
    }

    fun drawContained(c: Canvas, bmp: Bitmap, destW: Float, destH: Float) {
        if (bmp.isRecycled || destW <= 0f || destH <= 0f) return
        val scale = minOf(destW / bmp.width, destH / bmp.height).coerceAtLeast(0.001f)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        val l = (destW - dw) / 2f
        val t = (destH - dh) / 2f
        c.drawBitmap(
            bmp, null, RectF(l, t, l + dw, t + dh),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
    }

    fun rasterizePdf(path: String?, targetW: Int, targetH: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount < 1) return@runCatching null
                    renderer.openPage(0).use { page ->
                        val rw = (page.width * SCALE).toInt().coerceIn(1, MAX_W)
                        val rh = (page.height * SCALE).toInt().coerceIn(1, MAX_H)
                        val raw = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                        raw.eraseColor(Color.WHITE)
                        page.render(raw, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val out = normalize(raw, targetW, targetH)
                        if (out !== raw && !raw.isRecycled) raw.recycle()
                        out
                    }
                }
            }
        }.getOrNull()
    }

    fun loadFile(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    fun decodeImage(ctx: Context, uri: Uri, maxDim: Int = MAX_W): Bitmap? {
        return runCatching {
            var orientation = ExifInterface.ORIENTATION_NORMAL
            ctx.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                orientation = ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            val bw = bounds.outWidth.coerceAtLeast(1)
            val bh = bounds.outHeight.coerceAtLeast(1)
            while (bw / sample > maxDim || bh / sample > maxDim) sample *= 2

            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decode)
            } ?: return@runCatching null

            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                if (rotated !== bmp && !bmp.isRecycled) bmp.recycle()
                bmp = rotated
            }
            bmp
        }.getOrNull()
    }

    fun saveJpeg(bmp: Bitmap, file: File, quality: Int = JPEG_QUALITY): File {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return file
    }

    /** canvas-style capture: JPEG base64 ตามขนาดที่ normalize แล้ว */
    fun toJpegBase64(bmp: Bitmap, quality: Int = JPEG_QUALITY): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun fromBase64(data: String): Bitmap? {
        val raw = data.substringAfter("base64,", data).trim()
        if (raw.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    fun capturePdfToFile(pdfPath: String?, dest: File, pageW: Float, pageH: Float): File? {
        val (w, h) = capturePixels(pageW, pageH)
        val bmp = rasterizePdf(pdfPath, w, h) ?: return null
        saveJpeg(bmp, dest)
        if (!bmp.isRecycled) bmp.recycle()
        return dest.takeIf { it.exists() && it.length() > 0L }
    }

    fun saveImageUri(ctx: Context, uri: Uri, dest: File): Bitmap? {
        val bmp = decodeImage(ctx, uri) ?: return null
        saveJpeg(bmp, dest)
        return bmp
    }
}
