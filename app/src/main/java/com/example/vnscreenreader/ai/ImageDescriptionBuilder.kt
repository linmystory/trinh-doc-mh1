package com.example.vnscreenreader.ai

import com.example.vnscreenreader.core.S
import com.example.vnscreenreader.core.Texts
import com.example.vnscreenreader.core.t

/**
 * Biến nhãn tiếng Anh của bộ phân loại ảnh ML Kit (chạy offline) thành câu mô tả. Từ điển dịch hiện chỉ có
 * tiếng Việt; ngôn ngữ khác đọc nhãn tiếng Anh gốc trong câu đã dịch.
 * Mô hình chỉ cho biết "có gì trong ảnh" (không viết thành câu như AI sinh văn bản), nên câu luôn
 * dùng lời dè dặt "có thể" để không khẳng định sai với người không nhìn được ảnh.
 */
object ImageDescriptionBuilder {

    const val DEFAULT_MIN_CONFIDENCE = 0.6f
    const val DEFAULT_MAX_LABELS = 4

    fun build(
        labels: List<LabelScore>,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
        maxLabels: Int = DEFAULT_MAX_LABELS,
    ): String {
        val confident = labels.filter { it.confidence >= minConfidence }.sortedByDescending { it.confidence }
        if (confident.isEmpty()) return t(S.IMG_NONE)

        if (Texts.provider.language != "vi") {
            // Chưa có từ điển cho ngôn ngữ này: đọc nhãn gốc (tiếng Anh) trong câu đã được dịch.
            val raw = confident.map { it.text.trim() }.distinct().take(maxLabels)
            return t(S.IMG_MAY_BE, raw.joinToString(", "))
        }
        val translated = confident.mapNotNull { LabelTranslator.toVietnamese(it.text) }.distinct().take(maxLabels)
        if (translated.isEmpty()) {
            return t(S.IMG_UNTRANSLATED, confident.take(maxLabels).joinToString(", ") { it.text })
        }
        return t(S.IMG_MAY_BE, translated.joinToString(", "))
    }
}

/** Từ điển nhãn phổ biến của ML Kit Image Labeling (so khớp không phân biệt hoa/thường). */
object LabelTranslator {

    fun toVietnamese(label: String): String? = DICTIONARY[label.trim().lowercase()]

    private val DICTIONARY: Map<String, String> = mapOf(
        "person" to "người", "people" to "nhiều người", "face" to "khuôn mặt", "smile" to "nụ cười",
        "hair" to "tóc", "child" to "trẻ em", "baby" to "em bé", "man" to "người đàn ông", "woman" to "người phụ nữ",
        "selfie" to "ảnh tự chụp", "crowd" to "đám đông", "hand" to "bàn tay", "finger" to "ngón tay",
        "clothing" to "quần áo", "shirt" to "áo", "dress" to "váy", "hat" to "mũ", "glasses" to "kính", "shoe" to "giày",
        "dog" to "chó", "cat" to "mèo", "bird" to "chim", "fish" to "cá", "horse" to "ngựa", "animal" to "động vật",
        "pet" to "thú cưng", "insect" to "côn trùng", "butterfly" to "bướm",
        "food" to "đồ ăn", "dish" to "món ăn", "cuisine" to "ẩm thực", "fruit" to "trái cây", "vegetable" to "rau củ",
        "cake" to "bánh", "pizza" to "pizza", "coffee" to "cà phê", "drink" to "đồ uống", "tableware" to "bát đĩa",
        "plate" to "đĩa", "bowl" to "bát", "cup" to "cốc", "bottle" to "chai",
        "car" to "ô tô", "vehicle" to "phương tiện", "motorcycle" to "xe máy", "bicycle" to "xe đạp", "bus" to "xe buýt",
        "truck" to "xe tải", "train" to "tàu hỏa", "airplane" to "máy bay", "boat" to "thuyền", "road" to "đường",
        "street" to "phố", "wheel" to "bánh xe",
        "building" to "tòa nhà", "house" to "ngôi nhà", "architecture" to "kiến trúc", "bridge" to "cây cầu",
        "tower" to "tòa tháp", "city" to "thành phố", "room" to "căn phòng", "furniture" to "đồ nội thất", "table" to "bàn",
        "chair" to "ghế", "sofa" to "ghế sofa", "bed" to "giường", "door" to "cửa", "window" to "cửa sổ",
        "tree" to "cây", "plant" to "thực vật", "flower" to "hoa", "grass" to "cỏ", "leaf" to "lá", "forest" to "rừng",
        "mountain" to "núi", "sky" to "bầu trời", "cloud" to "mây", "sea" to "biển", "beach" to "bãi biển", "sun" to "mặt trời",
        "water" to "nước", "river" to "sông", "lake" to "hồ", "sunset" to "hoàng hôn", "snow" to "tuyết", "nature" to "thiên nhiên",
        "text" to "chữ viết", "font" to "văn bản", "screenshot" to "ảnh chụp màn hình", "logo" to "biểu trưng", "icon" to "biểu tượng",
        "poster" to "áp phích", "document" to "tài liệu", "paper" to "giấy", "book" to "sách", "handwriting" to "chữ viết tay",
        "mobile phone" to "điện thoại", "gadget" to "thiết bị điện tử", "computer" to "máy tính", "laptop" to "máy tính xách tay",
        "television" to "ti vi", "camera" to "máy ảnh", "electronics" to "đồ điện tử", "technology" to "công nghệ",
        "art" to "tác phẩm nghệ thuật", "painting" to "bức tranh", "drawing" to "hình vẽ", "cartoon" to "hoạt hình",
        "toy" to "đồ chơi", "game" to "trò chơi", "sports" to "thể thao", "ball" to "quả bóng", "music" to "âm nhạc",
        "fun" to "cảnh vui vẻ", "event" to "sự kiện", "party" to "bữa tiệc", "wedding" to "đám cưới", "product" to "sản phẩm",
        "pattern" to "họa tiết", "circle" to "hình tròn", "rectangle" to "hình chữ nhật", "line" to "đường kẻ",
        "money" to "tiền", "jewellery" to "đồ trang sức", "fashion" to "thời trang", "bag" to "túi",
    )
}
