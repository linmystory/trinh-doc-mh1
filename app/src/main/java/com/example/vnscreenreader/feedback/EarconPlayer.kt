package com.example.vnscreenreader.feedback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Phát âm báo ngắn với độ trễ thấp: mỗi loại âm được tổng hợp một lần vào `AudioTrack` chế độ STATIC, lần sau chỉ
 * tua về đầu và phát (không cấp phát, không ghi lại dữ liệu). Dùng luồng USAGE_ASSISTANCE_ACCESSIBILITY nên đi theo
 * âm lượng "Hỗ trợ tiếp cận" của hệ thống. Không thread-safe: gọi trên một luồng.
 */
class EarconPlayer {

    enum class Kind(val frequencyHz: Int, val durationMs: Int) {
        FOCUS(1_300, 25),
        BOUNDARY(420, 90),
        ACTIVATE(1_900, 45),
    }

    private val tracks = HashMap<Kind, AudioTrack>()
    private var volume = DEFAULT_VOLUME

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        tracks.values.forEach { it.setVolume(volume) }
    }

    fun play(kind: Kind) {
        val track = tracks[kind] ?: create(kind)?.also { tracks[kind] = it } ?: return
        try {
            track.stop()
            track.setPlaybackHeadPosition(0)
            track.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack không phát được", e)
        }
    }

    /** Idempotent. */
    fun release() {
        tracks.values.forEach { it.release() }
        tracks.clear()
    }

    private fun create(kind: Kind): AudioTrack? = try {
        val pcm = ToneSynth.sine(kind.frequencyHz, kind.durationMs)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ToneSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also {
                it.write(pcm, 0, pcm.size)
                it.setVolume(volume)
            }
    } catch (e: UnsupportedOperationException) {
        Log.w(TAG, "Không tạo được AudioTrack", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Không tạo được AudioTrack", e)
        null
    }

    private companion object {
        const val TAG = "EarconPlayer"
        const val DEFAULT_VOLUME = 0.4f
    }
}
