package com.example.vnscreenreader.core

/**
 * Chuyển từng kho SharedPreferences từ nơi cũ sang nơi mới theo [StoragePolicy.planMove], nhớ kho nào đã xong để các lần gọi sau không tốn thao tác tệp.
 * Phần Android thật (đường dẫn, `moveSharedPreferencesFrom`) nằm sau [Site] nên toàn bộ luồng quyết định kiểm thử được bằng JVM: đây là nơi một lỗi nhỏ
 * (nhầm chiều chuyển, đánh dấu "xong" khi chưa xong, ghi đè bản mới) có thể làm mất dữ liệu của người dùng mà không ai hay.
 *
 * Bất biến:
 * - Máy chưa mở khóa: không đụng gì và chưa tính là xong (đích ở bộ nhớ thường chỉ mở được sau khi mở khóa).
 * - Chỉ tính là xong khi thật sự xong; thất bại (trả false, hoặc ném RuntimeException) thì lần gọi sau thử lại.
 * - Không bao giờ ném RuntimeException ra ngoài: lỗi được chuyển cho [report] (chuyển kho hỏng thì dữ liệu ở lại chỗ cũ, ứng dụng chạy tiếp, không sập
 *   lúc khởi động). Lỗi nặng ([Error]) vẫn đi tiếp.
 * - Không bao giờ chuyển đè khi nơi mới đã có bản (xem [StoragePolicy.PrefsMove.DISCARD_OLD]).
 * An toàn khi gọi từ nhiều luồng; mỗi kho được chuyển tối đa một lần thành công.
 */
class PrefsRelocation(private val report: (name: String, error: RuntimeException) -> Unit = { _, _ -> }) {

    /** Các thao tác với hai nơi lưu; [DeviceStorage] cài đặt bằng Context của Android. */
    interface Site {
        /** Người dùng đã mở khóa (nơi đích mở được)? */
        fun isUnlocked(): Boolean

        /** Nơi cũ còn dữ liệu của kho [name]? */
        fun oldExists(name: String): Boolean

        /** Nơi mới đã có dữ liệu của kho [name]? */
        fun newExists(name: String): Boolean

        /** Chuyển kho [name] từ nơi cũ sang nơi mới (sao chép rồi xóa bản gốc). @return true nếu xong. */
        fun move(name: String): Boolean

        /** Bỏ bản cũ của kho [name] mà không chuyển. @return true nếu không còn bản cũ nào. */
        fun discardOld(name: String): Boolean
    }

    private val settled = HashSet<String>() // chỉ truy cập khi giữ khóa của đối tượng

    /** @return true nếu kho [name] đã ở đúng chỗ (xong từ trước hoặc xong ngay lần này). */
    @Synchronized
    fun settle(name: String, site: Site): Boolean {
        if (name in settled) return true
        if (!site.isUnlocked()) return false
        val done = try {
            when (StoragePolicy.planMove(site.oldExists(name), site.newExists(name))) {
                StoragePolicy.PrefsMove.NOTHING -> true
                StoragePolicy.PrefsMove.MOVE -> site.move(name)
                StoragePolicy.PrefsMove.DISCARD_OLD -> site.discardOld(name)
            }
        } catch (e: RuntimeException) {
            try {
                report(name, e)
            } catch (reporting: RuntimeException) {
                // báo lỗi cũng hỏng: bỏ qua, không được làm sập lúc khởi động
            }
            false
        }
        if (done) settled += name
        return done
    }
}
