package com.example.vnscreenreader.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.t

/** Khung viền xanh lá quanh phần tử đang chọn, vẽ bằng cửa sổ TYPE_ACCESSIBILITY_OVERLAY. */
class HighlightOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val view = HighlightView(context)
    private var attached = false

    fun attach() {
        if (attached) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { title = t(S.OVERLAY_TITLE) }
        try {
            windowManager.addView(view, params)
            attached = true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Không thể thêm overlay", e)
        }
    }

    fun show(bounds: Rect) = view.setTarget(Rect(bounds))

    fun clear() = view.setTarget(null)

    fun detach() {
        if (!attached) return
        try {
            windowManager.removeViewImmediate(view)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không thể gỡ overlay", e)
        }
        attached = false
    }

    private class HighlightView(context: Context) : View(context) {
        private val strokeWidthPx = 4f * context.resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 230, 118)
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
        }
        private val location = IntArray(2)
        @Volatile private var target: Rect? = null

        init {
            // Lớp vẽ thuần trang trí: ẩn khỏi cây trợ năng và không nhận chạm.
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }

        fun setTarget(rect: Rect?) {
            target = rect
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val rect = target ?: return
            getLocationOnScreen(location)
            canvas.drawRect(
                (rect.left - location[0]).toFloat(),
                (rect.top - location[1]).toFloat(),
                (rect.right - location[0]).toFloat(),
                (rect.bottom - location[1]).toFloat(),
                paint,
            )
        }
    }

    private companion object {
        const val TAG = "HighlightOverlay"
    }
}
