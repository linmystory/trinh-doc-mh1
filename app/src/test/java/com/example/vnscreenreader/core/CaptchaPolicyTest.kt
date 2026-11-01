package com.example.vnscreenreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptchaPolicyTest {

    private fun image(
        description: String? = null,
        viewId: String? = null,
        className: String = "android.widget.ImageView",
        text: String? = null,
        editable: Boolean = false,
        password: Boolean = false,
    ) = NodeSnapshot(
        contentDescription = description,
        text = text,
        viewId = viewId,
        className = className,
        isEditable = editable,
        isPassword = password,
    )

    private fun box(node: NodeSnapshot, left: Int, top: Int, width: Int, height: Int) =
        BoundedNode(node, left, top, left + width, top + height)

    // ---- Nhận diện ảnh thường

    @Test fun plainImageClasses() {
        assertTrue(CaptchaPolicy.isPlainImage("android.widget.ImageView"))
        assertTrue(CaptchaPolicy.isPlainImage("androidx.appcompat.widget.AppCompatImageView"))
        assertTrue(CaptchaPolicy.isPlainImage("android.widget.Image")) // ảnh trong trang web của Chrome
        assertFalse(CaptchaPolicy.isPlainImage("android.widget.ImageButton"))
        assertFalse(CaptchaPolicy.isPlainImage("android.widget.TextView"))
        assertFalse(CaptchaPolicy.isPlainImage(null))
    }

    // ---- Nhận diện ảnh xác thực

    @Test fun detectsByDescriptionInSeveralLanguages() {
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Ảnh mã xác thực")))
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Mã bảo mật")))
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Captcha image")))
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(description = "验证码图片")))
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Введите символы с картинки")))
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(description = "كابتشا")))
    }

    @Test fun detectsByViewIdAndIgnoresCaseAndDiacritics() {
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(viewId = "com.shop:id/img_captcha")))
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(viewId = "com.shop:id/captchaImage")))
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(viewId = "com.shop:id/iv_ma_xac_thuc")))
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(description = "MA XAC THUC")))
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Mã Xác Thực")))
    }

    @Test fun rejectsChallengesThatAreNotTextToRead() {
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "reCAPTCHA")))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "hCaptcha challenge")))
    }

    @Test fun rejectsRefreshAudioAndSmsControls() {
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Làm mới mã xác thực")))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Refresh captcha")))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Nghe mã xác thực qua âm thanh")))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Mã xác thực SMS")))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(viewId = "com.shop:id/btn_refresh_captcha")))
    }

    @Test fun otpWordMatchesWholeWordsOnly() {
        // "otp" nằm GIỮA một từ khác thì không được coi là mã OTP.
        assertTrue(CaptchaPolicy.looksLikeCaptchaImage(image(description = "captcha hotpot")))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "captcha otp")))
    }

    @Test fun notAnImageOrNotReadable() {
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Mã xác thực", className = "android.widget.TextView")))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Mã xác thực", className = "android.widget.ImageButton")))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "captcha", editable = true)))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "captcha", password = true)))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = "Logo cửa hàng")))
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image()))
    }

    @Test fun longTextIsContentNotLabel() {
        val paragraph = "Chúng tôi dùng captcha để bảo vệ trang. ".repeat(10)
        assertFalse(CaptchaPolicy.looksLikeCaptchaImage(image(description = paragraph)))
    }

    @Test fun anchorIsTheFieldOrTextThatAsksForTheCode() {
        val field = NodeSnapshot(hintText = "Nhập mã xác thực", className = "android.widget.EditText", isEditable = true)
        assertTrue(CaptchaPolicy.isAnchor(field))
        assertTrue(CaptchaPolicy.isAnchor(NodeSnapshot(text = "Nhập mã trong hình", className = "android.view.View")))
        assertFalse(CaptchaPolicy.isAnchor(image(description = "captcha"))) // ảnh không phải neo
        assertFalse(CaptchaPolicy.isAnchor(field.copy(isPassword = true)))
        assertFalse(CaptchaPolicy.isAnchor(NodeSnapshot(hintText = "Nhập mã SMS xác thực", className = "android.widget.EditText")))
    }

    // ---- Kích thước

    @Test fun plausibleSize() {
        val n = image()
        assertTrue(CaptchaPolicy.isPlausibleSize(box(n, 0, 0, 300, 100)))
        assertTrue(CaptchaPolicy.isPlausibleSize(box(n, 0, 0, 100, 100)))
        assertFalse(CaptchaPolicy.isPlausibleSize(box(n, 0, 0, 40, 40))) // biểu tượng nhỏ
        assertFalse(CaptchaPolicy.isPlausibleSize(box(n, 0, 0, 300, 10)))
        assertFalse(CaptchaPolicy.isPlausibleSize(box(n, 0, 0, 2000, 100))) // ảnh nền rất rộng
        assertFalse(CaptchaPolicy.isPlausibleSize(box(n, 0, 0, 1500, 100))) // quá dẹt
        assertFalse(CaptchaPolicy.isPlausibleSize(box(n, 0, 0, 100, 800)))
    }

    @Test fun capturableImageNeedsRealBoundsAndPlainImage() {
        assertTrue(CaptchaPolicy.isCapturableImage(box(image(), 0, 0, 10, 10)))
        assertFalse(CaptchaPolicy.isCapturableImage(box(image(), 0, 0, 0, 10)))
        assertFalse(CaptchaPolicy.isCapturableImage(box(image(password = true), 0, 0, 10, 10)))
        assertFalse(CaptchaPolicy.isCapturableImage(box(image(className = "android.widget.TextView"), 0, 0, 10, 10)))
    }

    // ---- Chọn ảnh

    private val maxDistance = 500 // khoảng cách tối đa (px) từ ô nhắc tới ảnh, dùng trong các ca bên dưới

    @Test fun focusedImageWins() {
        val nodes = listOf(
            box(image(description = "captcha"), 0, 0, 300, 100),
            box(image(description = "Ảnh sản phẩm"), 0, 200, 300, 100),
        )
        assertEquals(1, CaptchaFinder.pick(nodes, focusedIndex = 1, maxAnchorDistancePx = maxDistance))
    }

    @Test fun declaredCaptchaNearestToFocusedElement() {
        val nodes = listOf(
            box(image(description = "captcha"), 0, 100, 300, 100),
            box(NodeSnapshot(text = "Gửi", className = "android.widget.Button"), 0, 600, 300, 100),
            box(image(description = "captcha"), 0, 450, 300, 100),
        )
        assertEquals(2, CaptchaFinder.pick(nodes, focusedIndex = 1, maxAnchorDistancePx = maxDistance))
    }

    @Test fun declaredCaptchaWithoutFocusTakesTheFirst() {
        val nodes = listOf(
            box(NodeSnapshot(text = "Tiêu đề", className = "android.widget.TextView"), 0, 0, 300, 50),
            box(image(description = "captcha"), 0, 100, 300, 100),
            box(image(description = "captcha"), 0, 400, 300, 100),
        )
        assertEquals(1, CaptchaFinder.pick(nodes, focusedIndex = null, maxAnchorDistancePx = maxDistance))
    }

    @Test fun unlabeledImageNextToCodeFieldIsFound() {
        val nodes = listOf(
            box(image(), 0, 0, 300, 100), // logo ở xa
            box(NodeSnapshot(hintText = "Nhập mã xác thực", className = "android.widget.EditText", isEditable = true), 0, 500, 600, 60),
            box(image(), 0, 420, 300, 100), // sát ô nhập
        )
        assertEquals(2, CaptchaFinder.pick(nodes, focusedIndex = null, maxAnchorDistancePx = maxDistance))
    }

    @Test fun focusOnTheFieldStillFindsTheImageNextToIt() {
        val nodes = listOf(
            box(NodeSnapshot(hintText = "Nhập mã bảo mật", className = "android.widget.EditText", isEditable = true), 0, 500, 600, 60),
            box(image(), 0, 420, 300, 100),
        )
        assertEquals(1, CaptchaFinder.pick(nodes, focusedIndex = 0, maxAnchorDistancePx = maxDistance))
    }

    @Test fun nothingWhenNoKeywordAndNoFocusedImage() {
        val nodes = listOf(
            box(image(), 0, 0, 300, 100),
            box(NodeSnapshot(text = "Đăng nhập", className = "android.widget.Button"), 0, 200, 300, 60),
        )
        assertNull(CaptchaFinder.pick(nodes, focusedIndex = null, maxAnchorDistancePx = maxDistance))
    }

    @Test fun nothingWhenAnchorIsTooFar() {
        val nodes = listOf(
            box(NodeSnapshot(hintText = "Nhập mã xác thực", className = "android.widget.EditText", isEditable = true), 0, 900, 600, 60),
            box(image(), 0, 0, 300, 100),
        )
        assertNull(CaptchaFinder.pick(nodes, focusedIndex = null, maxAnchorDistancePx = 200))
    }

    @Test fun recaptchaIsNeverPicked() {
        val nodes = listOf(box(image(description = "reCAPTCHA"), 0, 0, 300, 100))
        assertNull(CaptchaFinder.pick(nodes, focusedIndex = null, maxAnchorDistancePx = maxDistance))
    }

    @Test fun passwordFieldsAndTinyIconsAreNeverPicked() {
        val nodes = listOf(
            box(image(description = "captcha", password = true), 0, 0, 300, 100),
            box(image(description = "captcha"), 0, 200, 30, 30), // biểu tượng nhỏ
        )
        assertNull(CaptchaFinder.pick(nodes, focusedIndex = null, maxAnchorDistancePx = maxDistance))
    }

    @Test fun badFocusedIndexIsIgnored() {
        val nodes = listOf(box(image(description = "captcha"), 0, 0, 300, 100))
        assertEquals(0, CaptchaFinder.pick(nodes, focusedIndex = 99, maxAnchorDistancePx = maxDistance))
        assertNull(CaptchaFinder.pick(emptyList(), focusedIndex = 0, maxAnchorDistancePx = maxDistance))
    }

    // ---- Lời đọc của phần tử

    @Test fun captchaImageHasItsOwnRole() {
        val text = SpeechTextBuilder.buildNodeDescription(image(description = "captcha"))
        assertEquals("captcha, ảnh xác thực", text)
        val plain = SpeechTextBuilder.buildNodeDescription(image(description = "Logo"))
        assertFalse(plain.contains("xác thực"))
    }
}
