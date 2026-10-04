package com.chb.form

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * จุดเข้าหลัก: โหลด Overlay Composer (ตัวอย่าง UX ที่ใช้งานได้จริง)
 * — โหลด PDF/ภาพเป็นพื้น
 * — วาง / ย้าย / ลบ ฟิลด์ text·image·QR เองบน canvas
 * — กรอกข้อมูลหลายชุด แล้ว export PDF/PNG
 */
class MainActivity : ComponentActivity() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                when {
                    data.clipData != null -> {
                        (0 until data.clipData!!.itemCount).map { data.clipData!!.getItemAt(it).uri }.toTypedArray()
                    }
                    data.data != null -> arrayOf(data.data!!)
                    else -> null
                }
            }
        } else null
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                builtInZoomControls = false
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString().orEmpty()
                    if (url.startsWith("blob:") || url.startsWith("data:")) return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        // เปิดลิงก์ภายนอกในเบราว์เซอร์ถ้าจำเป็น — ตอนนี้ปล่อยใน WebView
                        return false
                    }
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    return try {
                        fileChooserLauncher.launch(intent)
                        true
                    } catch (e: Exception) {
                        this@MainActivity.filePathCallback = null
                        Toast.makeText(this@MainActivity, "เปิดตัวเลือกไฟล์ไม่ได้", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
            }

            addJavascriptInterface(AndroidBridge(), "AndroidBridge")

            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                // รองรับ download จาก data URL ผ่าน bridge เป็นหลัก
                if (url.startsWith("data:")) {
                    saveDataUrl(url, guessFileName(contentDisposition, mimeType))
                }
            }

            loadUrl("file:///android_asset/overlay/index.html")
        }

        setContentView(webView)
    }

    private fun guessFileName(disposition: String?, mime: String?): String {
        val fromDisp = disposition
            ?.substringAfter("filename=", "")
            ?.trim('"', '\'', ' ')
            ?.takeIf { it.isNotBlank() }
        if (fromDisp != null) return fromDisp
        val ext = when {
            mime?.contains("pdf") == true -> "pdf"
            mime?.contains("png") == true -> "png"
            mime?.contains("jpeg") == true || mime?.contains("jpg") == true -> "jpg"
            else -> "bin"
        }
        return "export_${System.currentTimeMillis()}.$ext"
    }

    private fun saveDataUrl(dataUrl: String, fileName: String) {
        try {
            val comma = dataUrl.indexOf(',')
            if (comma < 0) return
            val meta = dataUrl.substring(0, comma)
            val data = dataUrl.substring(comma + 1)
            val bytes = if (meta.contains(";base64")) {
                Base64.decode(data, Base64.DEFAULT)
            } else {
                Uri.decode(data).toByteArray(Charsets.UTF_8)
            }
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir, "export").apply { mkdirs() }
            val out = File(dir, fileName)
            FileOutputStream(out).use { it.write(bytes) }
            shareFile(out)
            runOnUiThread {
                Toast.makeText(this, "บันทึกแล้ว: ${out.name}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "บันทึกไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when {
                file.name.endsWith(".pdf", true) -> "application/pdf"
                file.name.endsWith(".png", true) -> "image/png"
                else -> "application/octet-stream"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "แชร์ไฟล์"))
    }

    /** เรียกจาก JS: AndroidBridge.saveBase64(mime, base64, fileName) */
    inner class AndroidBridge {
        @JavascriptInterface
        fun saveBase64(mime: String, base64: String, fileName: String) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir, "export").apply { mkdirs() }
                val safeName = fileName.ifBlank {
                    "export_${System.currentTimeMillis()}." + when {
                        mime.contains("pdf") -> "pdf"
                        mime.contains("png") -> "png"
                        else -> "bin"
                    }
                }
                val out = File(dir, safeName)
                FileOutputStream(out).use { it.write(bytes) }
                runOnUiThread {
                    shareFile(out)
                    Toast.makeText(this@MainActivity, "บันทึกแล้ว: ${out.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "บันทึกไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
