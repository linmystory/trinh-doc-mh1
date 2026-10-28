package com.example.vnscreenreader.feedback

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Rung ngắn bằng hiệu ứng có sẵn của hệ thống (nhẹ, đồng bộ với phần cứng). Cần quyền VIBRATE (loại thường). */
class HapticPlayer(context: Context) {

    private val vibrator: Vibrator? = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    enum class Kind { TICK, CLICK, HEAVY }

    fun play(kind: Kind) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when (kind) {
            Kind.TICK -> VibrationEffect.EFFECT_TICK
            Kind.CLICK -> VibrationEffect.EFFECT_CLICK
            Kind.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
        }
        v.vibrate(VibrationEffect.createPredefined(effect))
    }
}
