package com.example.vnscreenreader.core

/** Một tính năng cần quyền lúc chạy. */
data class PermissionFeature(
    val id: String,
    val permissions: List<String>,
    /** Tên tính năng hiển thị ở mục "Quyền". */
    val name: S,
    /** Lời giải thích đọc TRƯỚC khi hiện hộp thoại hệ thống. */
    val rationale: S,
)

/**
 * Danh sách tính năng cần quyền. Mỗi tính năng TẮT mặc định và chỉ xin quyền khi người dùng bật nó. Thêm tính năng mới =
 * thêm một mục ở đây VÀ khai báo quyền tương ứng trong manifest; test `ManifestPermissionsTest` buộc hai việc này đi cùng nhau.
 */
object PermissionFeatures {
    /** Đọc người gọi: biết khi nào đổ chuông (READ_PHONE_STATE) và xem số gọi đến (READ_CALL_LOG, bắt buộc từ Android 9). */
    val caller = PermissionFeature(
        id = "caller",
        permissions = listOf("android.permission.READ_PHONE_STATE", "android.permission.READ_CALL_LOG"),
        name = S.PERM_NAME_CALLER,
        rationale = S.PERM_CALLER_RATIONALE,
    )

    /** Đọc tên người gọi từ danh bạ. */
    val contacts = PermissionFeature(
        id = "contacts",
        permissions = listOf("android.permission.READ_CONTACTS"),
        name = S.PERM_NAME_CONTACTS,
        rationale = S.PERM_CONTACTS_RATIONALE,
    )

    /** Nói vị trí hiện tại: GPS (chính xác) và vị trí gần đúng (Android 12+ yêu cầu xin cả hai). */
    val location = PermissionFeature(
        id = "location",
        permissions = listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
        name = S.PERM_NAME_LOCATION,
        rationale = S.PERM_LOCATION_HERE_RATIONALE,
    )

    val all: List<PermissionFeature> = listOf(caller, contacts, location)
}

/** Bước kế tiếp của luồng xin quyền. */
sealed interface PermissionStep {
    /** Tính năng chưa được bật: tuyệt đối không xin quyền. */
    data object NotRequested : PermissionStep

    /** Đã có quyền (hoặc vừa được cấp): làm ngay. */
    data object Proceed : PermissionStep

    /** Chưa có quyền: đọc lời giải thích rồi mới hiện hộp thoại hệ thống. */
    data class Explain(val rationale: S) : PermissionStep

    /** Bị từ chối (còn có thể hỏi lại ở lần bấm sau): đọc thông báo và kết thúc, KHÔNG hỏi lại trong cùng một lần bấm. */
    data object Denied : PermissionStep

    /** Bị từ chối kèm "không hỏi lại": hướng dẫn mở Cài đặt của ứng dụng. */
    data object DeniedPermanently : PermissionStep
}

/** Luồng xin quyền theo tính năng. Thuần hàm, không phụ thuộc Android nên kiểm thử được. */
object PermissionFlow {

    /** Khi người dùng bật/bấm một tính năng. */
    fun start(featureEnabled: Boolean, granted: Boolean, rationale: S): PermissionStep = when {
        !featureEnabled -> PermissionStep.NotRequested
        granted -> PermissionStep.Proceed
        else -> PermissionStep.Explain(rationale)
    }

    /**
     * Sau khi hộp thoại hệ thống đóng. [shouldShowRationale] là kết quả của
     * `shouldShowRequestPermissionRationale`: false sau một lần từ chối nghĩa là "không hỏi lại".
     */
    fun afterSystemDialog(granted: Boolean, shouldShowRationale: Boolean): PermissionStep = when {
        granted -> PermissionStep.Proceed
        shouldShowRationale -> PermissionStep.Denied
        else -> PermissionStep.DeniedPermanently
    }

    /** Chuỗi cần đọc cho bước này; null nếu không có gì để đọc. */
    fun message(step: PermissionStep): S? = when (step) {
        is PermissionStep.Explain -> step.rationale
        PermissionStep.Denied -> S.PERM_DENIED
        PermissionStep.DeniedPermanently -> S.PERM_DENIED_PERMANENTLY
        else -> null
    }

    /** Chỉ khi bị từ chối vĩnh viễn mới có nút "Mở Cài đặt". */
    fun offersOpenSettings(step: PermissionStep): Boolean = step == PermissionStep.DeniedPermanently

    fun statusLabel(granted: Boolean): S = if (granted) S.PERM_STATUS_GRANTED else S.PERM_STATUS_DENIED

    /** Tính năng có nhiều quyền: chỉ coi là đã cấp khi cấp đủ tất cả. */
    fun allGranted(states: List<Boolean>): Boolean = states.isNotEmpty() && states.all { it }
}
