package com.chb.form

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * หน้าแรกของแอป: Overlay Composer — เครื่องมือสร้างเทมเพลตเอกสาร
 * (assets/overlay/index.html) ใน WebView
 * - เลือกไฟล์/ภาพ/PDF ผ่าน onShowFileChooser (input[type=file] ของหน้าเว็บ)
 * - บันทึก PNG/PDF/HTML/JSON ผ่าน JS interface ChbAndroid.saveBase64 แล้วเปิดแชร์
 */
class OverlayActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var pendingFilePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = pendingFilePathCallback
            pendingFilePathCallback = null
            if (cb == null) return@registerForActivityResult
            val data = result.data
            val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK && data != null) {
                val single = data.data
                val clip = data.clipData
                when {
                    single != null -> arrayOf(single)
                    clip != null && clip.itemCount > 0 -> {
                        (0 until clip.itemCount)
                            .mapNotNull { clip.getItemAt(it).uri }
                            .toTypedArray()
                            .ifEmpty { null }
                    }
                    else -> null
                }
            } else null
            cb.onReceiveValue(uris)
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                pendingFilePathCallback?.onReceiveValue(null)
                pendingFilePathCallback = callback
                val types = params?.acceptTypes.orEmpty().filter { it.contains("/") }
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    if (types.isNotEmpty()) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    pendingFilePathCallback = null
                    callback?.onReceiveValue(null)
                    Toast.makeText(this@OverlayActivity, "เปิดตัวเลือกไฟล์ไม่ได้", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
        webView.addJavascriptInterface(SaveBridge(), "ChbAndroid")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAF7EE")) // พื้นครีมธีม Quiet Power เดียวกับหน้าเว็บ (--bg)
        }
        root.addView(buildBar(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        // กัน UI ไม่ให้ล้นใต้ status bar (Android 15 edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl("file:///android_asset/overlay/index.html")
    }

    private fun buildBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#4A8C3F")) // สีแบรนด์เขียวธีม Quiet Power เดียวกับธีมเว็บ
            setPadding(dp(12), dp(8), dp(8), dp(8))
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        bar.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        // แบบฟอร์มเดิม (MainActivity) — ต้องเข้าถึงได้จากหน้าแรกเสมอ ไม่เช่นนั้นฟีเจอร์จะเป็นทางตัน
        bar.addView(barButton("แบบฟอร์มเดิม") {
            runCatching { startActivity(Intent(this, MainActivity::class.java)) }
                .onFailure {
                    Toast.makeText(this, "เปิดแบบฟอร์มเดิมไม่ได้", Toast.LENGTH_SHORT).show()
                }
        })
        bar.addView(barButton("รีเฟรช") { webView.reload() })
        return bar
    }

    private fun barButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#FFFFFF"))
            }
            setOnClickListener { onClick() }
        }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density + 0.5f).toInt()

    /** เรียกจากหน้าเว็บ: ChbAndroid.saveBase64(name, b64, mime) → บันทึกไฟล์แล้วเปิดแชร์ */
    inner class SaveBridge {
        @JavascriptInterface
        fun saveBase64(name: String, b64: String, mime: String): String = try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "export.bin" }
            val dir = File(filesDir, "export").apply { mkdirs() }
            val file = File(dir, safe)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(
                this@OverlayActivity, "$packageName.fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime.ifBlank { "application/octet-stream" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runOnUiThread {
                runCatching { startActivity(Intent.createChooser(send, "บันทึก $safe")) }
                    .onFailure {
                        Toast.makeText(this@OverlayActivity, "เปิดแชร์ไม่ได้", Toast.LENGTH_SHORT).show()
                    }
            }
            "ok"
        } catch (e: Exception) {
            e.message ?: "save failed"
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
