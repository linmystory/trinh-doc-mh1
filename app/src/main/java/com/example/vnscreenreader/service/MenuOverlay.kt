package com.example.vnscreenreader.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.vnscreenreader.core.Diag
import com.example.vnscreenreader.menu.MenuTouchMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Trình đơn của dịch vụ vẽ thành DANH SÁCH phủ màn hình (cửa sổ TYPE_ACCESSIBILITY_OVERLAY nhận chạm) để người dùng chạm hoặc lướt
 * ngón tay khám phá như ở mọi màn hình khác: chạm tới hàng nào thì [onRowTouched] được gọi với vị trí hàng đó (dịch vụ chọn mục và
 * đọc lên). Vuốt trái/phải, chạm hai lần, quay lại... vẫn do dịch vụ xử lý như trước; lớp này chỉ thêm đường "chạm để nghe".
 * Hình học các hàng ở [MenuTouchMap] (thuần hàm, có kiểm thử).
 *
 * Mọi hàm công khai gọi được từ luồng bất kỳ (việc thêm/gỡ cửa sổ luôn chạy trên luồng chính). Không thêm được cửa sổ thì
 * [isShowing] vẫn false và trình đơn chỉ còn đọc bằng vuốt (dịch vụ tự trả lời khi bị chạm, xem ReaderSession).
 */
class MenuOverlay(private val context: Context, private val onRowTouched: (Int) -> Unit) {

    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    // Chỉ truy cập trên luồng chính.
    private var view: MenuListView? = null

    /** Cửa sổ đang hiện (luồng khác chỉ đọc). */
    @Volatile
    var isShowing: Boolean = false
        private set

    /** Đặt khi [hideOnMain] thật sự gỡ một cửa sổ; [awaitHidden] đọc và xóa để biết có cần chờ hệ thống cập nhật hay không. */
    private val removedRecently = AtomicBoolean(false)

    /** Hiện danh sách [labels] với mục [selected] đang chọn; đang hiện rồi thì thay nội dung (vào/ra trình đơn con). */
    fun show(labels: List<String>, selected: Int) {
        val copy = labels.toList()
        main.post { showOnMain(copy, selected) }
    }

    /** Đổi mục đang chọn (vuốt chuyển mục): chỉ tô sáng và cuộn cửa sổ nếu danh sách dài. */
    fun select(selected: Int) {
        main.post { view?.select(selected) }
    }

    fun hide() {
        main.post { hideOnMain() }
    }

    /**
     * Gọi NGAY SAU [hide] và trước khi chạy một chức năng của trình đơn: chờ luồng chính gỡ xong lớp, rồi thêm một khoảng ngắn cho hệ thống
     * cập nhật danh sách cửa sổ nhận chạm. Lý do: chức năng sắp chạy có thể phát cử chỉ chạm theo toạ độ (nhấn, nhấn giữ, cuộn, chuyển trang
     * bằng vuốt giả lập); chạm đó mà tới khi lớp còn nằm trên cùng thì rơi vào lớp này chứ không tới ứng dụng bên dưới. Chặn luồng gọi tối đa
     * khoảng [AWAIT_MAIN_MS] + [SETTLE_MS] (quá hạn thì bỏ chờ); gọi từ luồng chính thì không làm gì vì không chờ được chính mình.
     */
    fun awaitHidden() {
        if (Looper.myLooper() == Looper.getMainLooper()) return
        val reached = CountDownLatch(1)
        main.post { reached.countDown() } // hàng đợi tuần tự: chạy sau lệnh gỡ đã đăng trước đó
        try {
            if (!reached.await(AWAIT_MAIN_MS, TimeUnit.MILLISECONDS)) return
            if (removedRecently.getAndSet(false)) Thread.sleep(SETTLE_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt() // giữ cờ ngắt cho chủ luồng; không chờ thêm
        }
    }

    /** Gỡ ngay khi gọi trên luồng chính (đóng dịch vụ), hủy mọi việc đang chờ. */
    fun release() {
        main.removeCallbacksAndMessages(null)
        if (Looper.myLooper() == Looper.getMainLooper()) hideOnMain() else main.post { hideOnMain() }
    }

    private fun showOnMain(labels: List<String>, selected: Int) {
        val existing = view
        if (existing != null) {
            existing.setItems(labels, selected)
            return
        }
        val created = MenuListView(context, onRowTouched).also { it.setItems(labels, selected) }
        // Nhận chạm (khác lớp viền xanh của HighlightOverlay) nhưng KHÔNG lấy tiêu điểm bàn phím nên không làm đổi cửa sổ đang dùng.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        try {
            windowManager.addView(created, params)
            view = created
            isShowing = true
            Diag.log("MENU_OVERLAY") { "shown rows=${labels.size}" }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Không thể thêm lớp trình đơn", e)
            Diag.log("MENU_OVERLAY") { "attach failed ${e.javaClass.simpleName}" }
        }
    }

    private fun hideOnMain() {
        val current = view ?: return
        view = null
        isShowing = false
        try {
            windowManager.removeViewImmediate(current)
            removedRecently.set(true)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Không thể gỡ lớp trình đơn", e)
        }
    }

    /**
     * Một khung vẽ duy nhất: nền gần đen, mỗi mục một hàng chữ trắng, mục đang chọn tô vàng chữ đen (cùng bảng màu tương phản cao của
     * màn hình cài đặt). Tự không nằm trong cây trợ năng (người dùng nghe bằng giọng của dịch vụ, không qua nút trợ năng).
     */
    private class MenuListView(context: Context, private val onRow: (Int) -> Unit) : View(context) {
        private var labels: List<String> = emptyList()
        private var selected = -1
        private var first = 0
        private var map = MenuTouchMap(0, 0, 1)
        private var lastRow = -1

        private val metrics = context.resources.displayMetrics
        private val minRowPx = (MIN_ROW_DP * metrics.density).toInt()
        private val paddingPx = TEXT_PADDING_DP * metrics.density
        private val backgroundPaint = Paint().apply { color = Color.argb(235, 0, 0, 0) }
        private val selectedPaint = Paint().apply { color = Color.rgb(255, 235, 59) }
        private val dividerPaint = Paint().apply { color = Color.rgb(80, 80, 80) }
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP, metrics)
        }

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun setItems(newLabels: List<String>, newSelected: Int) {
            labels = newLabels
            selected = newSelected
            lastRow = -1
            first = 0
            rebuild()
            invalidate()
        }

        fun select(newSelected: Int) {
            selected = newSelected
            first = map.firstFor(newSelected.coerceAtLeast(0), first)
            invalidate()
        }

        private fun rebuild() {
            map = MenuTouchMap(labels.size, height, minRowPx)
            first = map.firstFor(selected.coerceAtLeast(0), first)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuild()
        }

        // Khám phá bằng cảm ứng: hệ thống gửi chạm một ngón dưới dạng sự kiện "hover" (có toạ độ). Chỉ báo khi sang HÀNG KHÁC hoặc khi
        // nhấc ngón rồi chạm lại (lastRow được đặt lại), nên rê ngón trong cùng một hàng không đọc lặp.
        override fun onHoverEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                    val row = map.rowAt(event.y, first)
                    if (row >= 0 && row != lastRow) {
                        lastRow = row
                        selected = row
                        invalidate()
                        onRow(row)
                    }
                }
                MotionEvent.ACTION_HOVER_EXIT -> lastRow = -1
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            canvas.drawRect(0f, 0f, w, height.toFloat(), backgroundPaint)
            for (local in 0 until map.visible) {
                val index = first + local
                val label = labels.getOrNull(index) ?: continue
                val top = map.rowTop(local).toFloat()
                val bottom = map.rowBottom(local).toFloat()
                val isSelected = index == selected
                if (isSelected) canvas.drawRect(0f, top, w, bottom, selectedPaint)
                textPaint.color = if (isSelected) Color.BLACK else Color.WHITE
                val shown = TextUtils.ellipsize(label, textPaint, w - 2 * paddingPx, TextUtils.TruncateAt.END).toString()
                val metricsText = textPaint.fontMetrics
                canvas.drawText(shown, paddingPx, (top + bottom) / 2f - (metricsText.ascent + metricsText.descent) / 2f, textPaint)
                canvas.drawRect(0f, bottom - 1f, w, bottom, dividerPaint)
            }
        }
    }

    private companion object {
        const val TAG = "MenuOverlay"

        /** Hàng cao tối thiểu (dp): đủ lớn để chạm chính xác không cần nhìn. */
        const val MIN_ROW_DP = 56f
        const val TEXT_SP = 22f
        const val TEXT_PADDING_DP = 20f

        /** Chờ tối đa bấy nhiêu ms để luồng chính xử lý xong lệnh gỡ ([awaitHidden]). */
        const val AWAIT_MAIN_MS = 250L

        /** Khoảng chờ sau khi gỡ (xấp xỉ ba khung hình ở 60 Hz) cho hệ thống bỏ cửa sổ khỏi danh sách nhận chạm. */
        const val SETTLE_MS = 48L
    }
}
