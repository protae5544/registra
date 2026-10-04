package com.chb.form

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
    }
}
