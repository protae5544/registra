package com.chb.form.pdf
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
object PdfTools {
fun preview(file: File, widthPx: Int): Bitmap? = runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { r -> r.openPage(0).use { pg -> val h = (widthPx.toLong() * pg.height / pg.width).toInt()
val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
bmp.eraseColor(Color.WHITE)
pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
bmp
} } } }.getOrNull()
private fun uri(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
fun share(ctx: Context, f: File) { val i = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"
putExtra(Intent.EXTRA_STREAM, uri(ctx, f))
putExtra(Intent.EXTRA_SUBJECT, f.name)
addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
ctx.startActivity(Intent.createChooser(i, "ส่งแบบฟอร์ม")) }
fun open(ctx: Context, f: File) { val i = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri(ctx, f), "application/pdf")
addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }
runCatching { ctx.startActivity(i) } }
fun print(ctx: Context, f: File) { val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
pm.print(f.nameWithoutExtension, FileAdapter(f), PrintAttributes.Builder() .setMediaSize(PrintAttributes.MediaSize.ISO_A4) .setMinMargins(PrintAttributes.Margins.NO_MARGINS) .build()) }
private class FileAdapter(val file: File) : PrintDocumentAdapter() { override fun onLayout( old: PrintAttributes?, new: PrintAttributes?, signal: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle? ) { if (signal?.isCanceled == true) { cb.onLayoutCancelled();
return
}
cb.onLayoutFinished( PrintDocumentInfo.Builder(file.name) .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT) .setPageCount(1).build(), true ) }
override fun onWrite( pages: Array<out PageRange>?, dest: ParcelFileDescriptor, signal: CancellationSignal?, cb: WriteResultCallback ) { FileInputStream(file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) } } }
