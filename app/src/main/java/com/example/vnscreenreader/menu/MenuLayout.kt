package com.example.vnscreenreader.menu

import com.example.vnscreenreader.gesture.GestureAction

/**
 * Danh sách chức năng của trình đơn phần tử do người dùng tự sắp xếp: tối đa [MAX_ITEMS] mục lấy từ [MenuCatalog], không trùng, có
 * thứ tự. Bất biến: mỗi thao tác trả về bố cục mới, hoặc chính nó nếu thao tác không hợp lệ (nên giao diện không cần tự kiểm lại).
 *
 * Chưa sửa thì dùng [DEFAULT_ACTIONS] và không lưu gì ([isCustomized] = false), nên mặc định cải thiện về sau vẫn đến được người chưa
 * tùy chỉnh. Sắp xếp lại đúng như mặc định cũng được coi là chưa tùy chỉnh. Thuần hàm để kiểm thử (MenuLayoutTest).
 */
class MenuLayout private constructor(private val saved: List<GestureAction>?) {

    /** Các chức năng đang dùng, theo thứ tự hiển thị. Luôn có từ một đến [MAX_ITEMS] mục. */
    val actions: List<GestureAction> get() = saved ?: DEFAULT_ACTIONS

    /** Đã khác mặc định (có lưu)? */
    val isCustomized: Boolean get() = saved != null

    /** Đã đủ [MAX_ITEMS] mục, không thêm được nữa. */
    val isFull: Boolean get() = actions.size >= MAX_ITEMS

    operator fun contains(action: GestureAction): Boolean = action in actions

    /** Thêm vào cuối. Không đổi nếu đã đủ [MAX_ITEMS], đã có, hoặc chức năng không có trong danh mục. */
    fun add(action: GestureAction): MenuLayout =
        if (isFull || action in actions || !MenuCatalog.isAvailable(action)) this else of(actions + action)

    /** Xóa một mục. Không xóa mục cuối cùng: trình đơn phải còn ít nhất một chức năng. */
    fun remove(action: GestureAction): MenuLayout =
        if (action !in actions || actions.size <= 1) this else of(actions - action)

    /** Đưa [action] lên đầu danh sách. */
    fun moveToTop(action: GestureAction): MenuLayout = moveTo(action, 0)

    /** Đưa [action] lên trước mục đứng ngay trên nó. Không đổi nếu đã ở đầu. */
    fun moveUp(action: GestureAction): MenuLayout {
        val index = actions.indexOf(action)
        return if (index <= 0) this else moveTo(action, index - 1)
    }

    /** Đưa [action] xuống sau mục đứng ngay dưới nó. Không đổi nếu đã ở cuối. */
    fun moveDown(action: GestureAction): MenuLayout {
        val index = actions.indexOf(action)
        return if (index < 0 || index >= actions.size - 1) this else moveTo(action, index + 1)
    }

    /**
     * Thay [old] bằng [new] ngay tại vị trí của nó. Không đổi nếu [old] không có trong danh sách, [new] đã có (hoặc trùng [old])
     * hoặc [new] không có trong danh mục.
     */
    fun replace(old: GestureAction, new: GestureAction): MenuLayout {
        val index = actions.indexOf(old)
        if (index < 0 || old == new || new in actions || !MenuCatalog.isAvailable(new)) return this
        return of(actions.toMutableList().also { it[index] = new })
    }

    /** Về mặc định. */
    fun reset(): MenuLayout = DEFAULT

    /** Chuỗi để lưu (tên các chức năng, ngăn bằng dấu phẩy); null nếu là mặc định, tức không cần lưu gì. */
    fun serialize(): String? = saved?.joinToString(",") { it.name }

    /** Vị trí (tính từ 1) của [action] trong danh sách, hoặc 0 nếu không có. */
    fun positionOf(action: GestureAction): Int = actions.indexOf(action) + 1

    // Thao tác nào đáng hiện cho một mục. Giao diện chỉ hiện những thao tác này, nên người dùng không bao giờ gặp nút mà bấm vào
    // không làm gì (trình đọc màn hình đọc từng nút, mỗi nút thừa là một lần vuốt thừa).

    /** "Chuyển lên đầu" chỉ khác "Chuyển lên" khi mục cách đầu từ hai bậc (ở vị trí 2 thì hai thao tác giống hệt nhau). */
    fun canMoveToTop(action: GestureAction): Boolean = positionOf(action) > 2

    /** Còn mục nào phía trên để đổi chỗ không. */
    fun canMoveUp(action: GestureAction): Boolean = positionOf(action) > 1

    /** Còn mục nào phía dưới để đổi chỗ không. */
    fun canMoveDown(action: GestureAction): Boolean = positionOf(action) in 1 until actions.size

    /** Xóa được không: mục có trong danh sách và không phải mục cuối cùng (xem [remove]). */
    fun canRemove(action: GestureAction): Boolean = action in actions && actions.size > 1

    private fun moveTo(action: GestureAction, target: Int): MenuLayout {
        val current = actions.indexOf(action)
        if (current < 0 || current == target) return this
        val list = actions.toMutableList()
        list.removeAt(current)
        list.add(target, action)
        return of(list)
    }

    override fun equals(other: Any?): Boolean = other is MenuLayout && other.actions == actions

    override fun hashCode(): Int = actions.hashCode()

    override fun toString(): String = "MenuLayout(${actions.joinToString { it.name }})"

    companion object {
        /** Số chức năng tối đa của trình đơn phần tử: đủ dùng mà vẫn ngắn để vuốt nhanh. */
        const val MAX_ITEMS = 12

        /** Chuỗi lưu dài hơn mức này chắc chắn hỏng (12 tên chức năng chỉ cỡ vài trăm ký tự): chỉ đọc phần đầu. */
        private const val MAX_RAW_CHARS = 1_000

        /** Trình đơn phần tử mặc định: đúng 12 mục và thứ tự của các bản trước. */
        val DEFAULT_ACTIONS: List<GestureAction> = listOf(
            GestureAction.ACTIVATE, GestureAction.LONG_CLICK_FOCUSED, GestureAction.UNIT_NEXT, GestureAction.UNIT_PREVIOUS,
            GestureAction.CYCLE_GRANULARITY, GestureAction.COPY_UNIT, GestureAction.COPY_FOCUSED, GestureAction.SPELL_FOCUSED,
            GestureAction.LABEL_FOCUSED, GestureAction.DESCRIBE_FOCUSED, GestureAction.OCR_FOCUSED, GestureAction.DESCRIBE_TECHNICAL,
        )

        val DEFAULT: MenuLayout = MenuLayout(null)

        private val byName: Map<String, GestureAction> = GestureAction.entries.associateBy { it.name }

        /**
         * Dựng từ danh sách bất kỳ: bỏ mục không có trong danh mục và mục trùng, cắt còn [MAX_ITEMS]. Rỗng hoặc đúng bằng mặc định
         * thì là [DEFAULT] (không lưu gì).
         */
        fun of(actions: List<GestureAction>): MenuLayout {
            val clean = actions.filter { MenuCatalog.isAvailable(it) }.distinct().take(MAX_ITEMS)
            return if (clean.isEmpty() || clean == DEFAULT_ACTIONS) DEFAULT else MenuLayout(clean)
        }

        /**
         * Đọc từ chuỗi đã lưu. Chuỗi hỏng, tên lạ (bản ứng dụng khác), trùng lặp hoặc quá dài không làm sập: bỏ phần sai, và nếu
         * không còn gì dùng được thì trả về mặc định.
         */
        fun parse(raw: String?): MenuLayout {
            if (raw.isNullOrBlank()) return DEFAULT
            return of(raw.take(MAX_RAW_CHARS).split(',').mapNotNull { byName[it.trim()] })
        }
    }
}
