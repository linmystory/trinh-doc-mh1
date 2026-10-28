package com.example.vnscreenreader.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech

/** Một engine TTS đã cài trên máy. */
data class TtsEngineInfo(val packageName: String, val label: String)

object TtsEngines {

    /** Liệt kê engine TTS (cần `<queries>` TTS_SERVICE trong manifest từ Android 11). Sắp theo tên hiển thị. */
    fun list(context: Context): List<TtsEngineInfo> {
        val pm = context.packageManager
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val services = pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        return services
            .mapNotNull { info ->
                val pkg = info.serviceInfo?.packageName ?: return@mapNotNull null
                TtsEngineInfo(pkg, info.loadLabel(pm).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
