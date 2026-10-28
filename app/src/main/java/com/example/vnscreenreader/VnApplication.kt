package com.example.vnscreenreader

import android.app.Application
import com.example.vnscreenreader.core.Texts
import com.example.vnscreenreader.i18n.ResourceTexts

/** Cài nguồn chuỗi theo tài nguyên Android trước mọi Activity/Service. */
class VnApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Texts.provider = ResourceTexts(this)
    }
}
