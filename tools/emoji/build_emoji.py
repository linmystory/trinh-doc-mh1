#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sinh app/src/main/java/.../core/EmojiData.kt và EmojiNames.kt.

Nguồn dữ liệu (cùng thư mục):
  emoji-data.txt        Unicode Emoji Data (thuộc tính Emoji / Emoji_Presentation), Emoji 15.1.
  names_curated.tsv     Tên tiếng Việt + Anh đã duyệt tay (ưu tiên cao nhất).
  names_vi_extra.txt    "MÃ tên tiếng Việt" cho các emoji còn lại; tên tiếng Anh lấy từ Unicode (có sửa ở bảng EN_OVERRIDE).

Chạy:   python3 tools/emoji/build_emoji.py          (ghi đè hai tệp Kotlin)
        python3 tools/emoji/build_emoji.py --check  (chỉ kiểm tra độ phủ và trùng tên, không ghi tệp)

Bảo đảm khi chạy: MỌI emoji trong emoji-data.txt (trừ ký tự ASCII/Latin-1) đều có tên Việt và Anh, và không có hai ký tự
đơn nào trùng tên (trùng tên làm "★★★★☆" bị đọc thành "ngôi sao, 5 lần").
"""
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
KT = HERE.parent.parent / "app/src/main/java/com/example/vnscreenreader/core"
CHUNK_LINES = 150  # mỗi chuỗi hằng không quá giới hạn 65535 byte của tệp class

# --------------------------------------------------------------------------------------------- Unicode
def parse_props(path):
    props = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.split("#")[0].strip()
        if not line:
            continue
        rng, prop = [x.strip() for x in line.split(";")]
        a, _, b = rng.partition("..")
        a = int(a, 16)
        b = int(b, 16) if b else a
        props.setdefault(prop, set()).update(range(a, b + 1))
    return props


def to_ranges(cps):
    out = []
    for cp in sorted(cps):
        if out and cp == out[-1][1] + 1:
            out[-1][1] = cp
        else:
            out.append([cp, cp])
    return out


PROPS = parse_props(HERE / "emoji-data.txt")
EMOJI = PROPS["Emoji"]
PRESENTATION = PROPS["Emoji_Presentation"]

# Emoji 16.0 (2024) chưa có trong emoji-data.txt 15.1: thêm tay (mã, Việt, Anh).
EMOJI16 = [
    (0x1FA89, "đàn hạc", "harp"), (0x1FA8F, "cái xẻng", "shovel"), (0x1FABE, "cây trụi lá", "leafless tree"),
    (0x1FAC6, "dấu vân tay", "fingerprint"), (0x1FADC, "củ rễ", "root vegetable"),
    (0x1FADF, "vệt bắn tung tóe", "splatter"), (0x1FAE9, "mặt có quầng thâm mắt", "face with bags under eyes"),
]

# Ký tự thường được viết ở dạng CHỮ (không kèm U+FE0F) nhưng người dùng hiểu là emoji: vẫn đọc tên.
PLAIN_TEXT_EMOJI = [
    0x2764, 0x263A, 0x2639, 0x26A0, 0x270C, 0x261D, 0x270D, 0x2714, 0x2716, 0x2744, 0x2763, 0x2600, 0x2601, 0x2602,
    0x2603, 0x260E, 0x2708, 0x2709, 0x2702, 0x270F, 0x2618, 0x2660, 0x2663, 0x2665, 0x2666, 0x2712, 0x2733, 0x2734,
]

# Ký hiệu KHÔNG phải emoji nhưng hay gặp trong giao diện (gạch đầu dòng, tích chọn, mũi tên sắp xếp...): (mã, Việt, Anh).
SYMBOLS = [
    (0x25CF, "chấm tròn đen", "black circle"), (0x25CB, "vòng tròn rỗng", "white circle"),
    (0x25C6, "hình thoi đặc", "black diamond"), (0x25C7, "hình thoi rỗng", "white diamond"),
    (0x25A0, "hình vuông đặc", "black square"), (0x25A1, "hình vuông rỗng", "white square"),
    (0x25B2, "tam giác đặc hướng lên", "black up-pointing triangle"),
    (0x25BC, "tam giác đặc hướng xuống", "black down-pointing triangle"),
    (0x25B3, "tam giác rỗng hướng lên", "white up-pointing triangle"),
    (0x25BD, "tam giác rỗng hướng xuống", "white down-pointing triangle"),
    (0x2713, "dấu tích", "check mark"), (0x2717, "dấu x chéo", "ballot x"),
    (0x2718, "dấu x chéo đậm", "heavy ballot x"), (0x27A4, "mũi tên nhọn hướng phải", "rightwards arrowhead"),
    (0x279C, "mũi tên đậm hướng phải", "heavy rightwards arrow"), (0x2610, "ô vuông trống", "empty checkbox"),
    (0x2612, "ô vuông có dấu x", "checkbox with x"), (0x2726, "ngôi sao bốn cánh đặc", "black four pointed star"),
    (0x2727, "ngôi sao bốn cánh rỗng", "white four pointed star"),
]

# Sửa tên (Việt, Anh) cho các ký tự đã có trong bảng cũ nhưng bị TRÙNG tên với ký tự khác.
OVERRIDE = {
    0x2605: ("ngôi sao đặc", "solid star"),
    0x2606: ("ngôi sao rỗng", "hollow star"),
    0x2B50: ("ngôi sao vàng", "yellow star"),
    0x2639: ("mặt cau mày", "frowning face"),
    0x1F508: ("loa âm lượng nhỏ", "speaker low volume"),
    0x1F509: ("loa âm lượng vừa", "speaker medium volume"),
    0x2714: ("dấu tích đậm", "heavy check mark"),
}

SKIN = [(0x1F3FB, "màu da rất sáng", "light skin tone"), (0x1F3FC, "màu da sáng", "medium-light skin tone"),
        (0x1F3FD, "màu da trung bình", "medium skin tone"), (0x1F3FE, "màu da sẫm", "medium-dark skin tone"),
        (0x1F3FF, "màu da rất sẫm", "dark skin tone")]

ZODIAC = [("Bạch Dương", "Aries"), ("Kim Ngưu", "Taurus"), ("Song Tử", "Gemini"), ("Cự Giải", "Cancer"),
          ("Sư Tử", "Leo"), ("Xử Nữ", "Virgo"), ("Thiên Bình", "Libra"), ("Bọ Cạp", "Scorpio"),
          ("Nhân Mã", "Sagittarius"), ("Ma Kết", "Capricorn"), ("Bảo Bình", "Aquarius"), ("Song Ngư", "Pisces")]

NUM_EN = ["twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven"]

# Tên tiếng Anh: Unicode đặt tên kỹ thuật cho một số ký tự, thay bằng tên đọc được.
EN_OVERRIDE = {
    0x2194: "left-right arrow", 0x2195: "up-down arrow", 0x2196: "up-left arrow", 0x2197: "up-right arrow",
    0x2198: "down-right arrow", 0x2199: "down-left arrow", 0x21A9: "right arrow curving left",
    0x21AA: "left arrow curving right", 0x23E9: "fast-forward button", 0x23EA: "fast reverse button",
    0x23EB: "fast up button", 0x23EC: "fast down button", 0x23ED: "next track button",
    0x23EE: "last track button", 0x23EF: "play or pause button", 0x23FA: "record button", 0x25B6: "play button",
    0x25C0: "reverse button", 0x2934: "right arrow curving up", 0x2935: "right arrow curving down",
    0x2B05: "left arrow", 0x2B06: "up arrow", 0x2B07: "down arrow", 0x27A1: "right arrow",
    0x24C2: "circled M", 0x1F51A: "end arrow", 0x1F51B: "on arrow", 0x1F51C: "soon arrow", 0x1F51D: "top arrow",
    0x1F51F: "keycap 10", 0x1F520: "input latin uppercase", 0x1F521: "input latin lowercase",
    0x1F522: "input numbers", 0x1F523: "input symbols", 0x1F524: "input latin letters", 0x1F52A: "kitchen knife",
    0x1F52F: "dotted six-pointed star", 0x1F346: "eggplant", 0x1F385: "Santa Claus", 0x1F936: "Mrs. Claus",
    0x1F4F2: "mobile phone with arrow", 0x1F3F3: "white flag", 0x1F3F4: "black flag", 0x1F4A9: "pile of poo",
    0x1F357: "poultry leg", 0x1F1E6: "regional indicator A", 0x1F9D1: "person", 0x26F9: "person bouncing ball",
    0x1F574: "person in suit levitating", 0x1F575: "detective", 0x1F46F: "people with bunny ears",
    0x1F483: "woman dancing", 0x1F57A: "man dancing", 0x1F479: "ogre", 0x1F47A: "goblin", 0x1F572: "man with chinese cap",
    0x1F201: "Japanese “here” button", 0x1F202: "Japanese “service charge” button",
    0x1F21A: "Japanese “free of charge” button", 0x1F22F: "Japanese “reserved” button",
    0x1F232: "Japanese “prohibited” button", 0x1F233: "Japanese “vacancy” button",
    0x1F234: "Japanese “passing grade” button", 0x1F235: "Japanese “no vacancy” button",
    0x1F236: "Japanese “not free of charge” button", 0x1F237: "Japanese “monthly amount” button",
    0x1F238: "Japanese “application” button", 0x1F239: "Japanese “discount” button",
    0x1F23A: "Japanese “open for business” button", 0x1F250: "Japanese “bargain” button",
    0x1F251: "Japanese “acceptable” button", 0x3297: "Japanese “congratulations” button",
    0x3299: "Japanese “secret” button", 0x1F170: "A button (blood type)", 0x1F171: "B button (blood type)",
    0x1F17E: "O button (blood type)", 0x1F17F: "P button", 0x1F18E: "AB button (blood type)",
    0x1F191: "CL button", 0x1F192: "COOL button", 0x1F193: "FREE button", 0x1F194: "ID button",
    0x1F196: "NG button", 0x1F198: "SOS button", 0x1F199: "UP! button", 0x1F19A: "VS button",
}


def ucd_en(cp):
    name = unicodedata.name(chr(cp), "").lower()
    if name.startswith("emoji component "):
        name = name[len("emoji component "):]
    return name


# --------------------------------------------------------------------------------------------- nạp bảng
def hexkey(cps):
    return "-".join(f"{c:X}" for c in cps)


def build_names():
    vi, en = {}, {}  # key: tuple mã -> tên

    for line in (HERE / "names_curated.tsv").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        key, v, e = (line.split("\t") + ["", ""])[:3]
        k = tuple(int(x, 16) for x in key.split("-"))
        vi[k] = v
        if e:
            en[k] = e

    for line in (HERE / "names_vi_extra.txt").read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        h, _, v = line.partition(" ")
        vi[(int(h, 16),)] = v.strip()

    for cp, (v, e) in OVERRIDE.items():
        vi[(cp,)], en[(cp,)] = v, e
    for cp, v, e in EMOJI16 + SYMBOLS + SKIN:
        vi[(cp,)], en[(cp,)] = v, e

    # Họ có quy tắc
    for i in range(12):
        hour = i + 1
        vi[(0x1F550 + i,)] = f"đồng hồ {hour} giờ"
        vi[(0x1F55C + i,)] = f"đồng hồ {hour} giờ rưỡi"
        en[(0x1F550 + i,)] = f"{NUM_EN[hour % 12]} o’clock"
        en[(0x1F55C + i,)] = f"{NUM_EN[hour % 12]}-thirty"
    for i, (v, e) in enumerate(ZODIAC):
        vi[(0x2648 + i,)] = f"chòm sao {v}"
        en[(0x2648 + i,)] = e
    for i in range(26):
        letter = chr(ord("A") + i)
        vi[(0x1F1E6 + i,)] = f"chữ cái {letter} của cờ"
        en[(0x1F1E6 + i,)] = f"regional indicator {letter}"

    # Tiếng Anh cho phần còn lại: lấy từ tên Unicode (có sửa).
    for k in list(vi):
        if len(k) == 1 and k not in en:
            en[k] = EN_OVERRIDE.get(k[0]) or ucd_en(k[0])
    for cp, e in EN_OVERRIDE.items():
        if (cp,) in vi:
            en[(cp,)] = e

    add_sequences(vi, en)
    return vi, en


PERSONS = [(0x1F9D1, "", ""), (0x1F468, " nam", "man "), (0x1F469, " nữ", "woman ")]
ROLES = [
    (0x2695, "nhân viên y tế", "health worker"), (0x1F393, "sinh viên", "student"), (0x1F3EB, "giáo viên", "teacher"),
    (0x2696, "thẩm phán", "judge"), (0x1F33E, "nông dân", "farmer"), (0x1F373, "đầu bếp", "cook"),
    (0x1F527, "thợ sửa máy", "mechanic"), (0x1F3ED, "công nhân nhà máy", "factory worker"),
    (0x1F4BC, "nhân viên văn phòng", "office worker"), (0x1F52C, "nhà khoa học", "scientist"),
    (0x1F4BB, "lập trình viên", "technologist"), (0x1F3A4, "ca sĩ", "singer"), (0x1F3A8, "họa sĩ", "artist"),
    (0x2708, "phi công", "pilot"), (0x1F680, "phi hành gia", "astronaut"), (0x1F692, "lính cứu hỏa", "firefighter"),
    (0x1F9AF, "người cầm gậy dò đường", "with white cane"), (0x1F9BC, "người dùng xe lăn điện", "in motorized wheelchair"),
    (0x1F9BD, "người dùng xe lăn tay", "in manual wheelchair"),
]
ZWJ = 0x200D
HEART, KISS = 0x2764, 0x1F48B
M, W, B, G = 0x1F468, 0x1F469, 0x1F466, 0x1F467


def add_sequences(vi, en):
    def seq(cps, v, e):
        vi[tuple(cps)] = v
        en[tuple(cps)] = e

    def z(*parts):
        out = []
        for i, p in enumerate(parts):
            if i:
                out.append(ZWJ)
            out.append(p)
        return out

    for person, vsuffix, eprefix in PERSONS:
        for role, rv, re_ in ROLES:
            seq(z(person, role), rv + vsuffix, f"{eprefix}{re_}")
    # Gia đình
    fam = [((M, W, B), "gia đình bố mẹ và con trai", "family: man, woman, boy"),
           ((M, W, G), "gia đình bố mẹ và con gái", "family: man, woman, girl"),
           ((M, W, G, B), "gia đình bố mẹ, con gái và con trai", "family: man, woman, girl, boy"),
           ((M, W, B, B), "gia đình bố mẹ và hai con trai", "family: man, woman, boy, boy"),
           ((M, W, G, G), "gia đình bố mẹ và hai con gái", "family: man, woman, girl, girl"),
           ((M, M, B), "gia đình hai bố và con trai", "family: man, man, boy"),
           ((M, M, G), "gia đình hai bố và con gái", "family: man, man, girl"),
           ((W, W, B), "gia đình hai mẹ và con trai", "family: woman, woman, boy"),
           ((W, W, G), "gia đình hai mẹ và con gái", "family: woman, woman, girl"),
           ((M, B), "bố và con trai", "family: man, boy"), ((M, G), "bố và con gái", "family: man, girl"),
           ((W, B), "mẹ và con trai", "family: woman, boy"), ((W, G), "mẹ và con gái", "family: woman, girl")]
    for parts, v, e in fam:
        seq(z(*parts), v, e)
    # Cặp đôi, nụ hôn
    for a, b, v, e in [(W, M, "nam nữ", "woman, man"), (M, M, "hai nam", "man, man"), (W, W, "hai nữ", "woman, woman")]:
        seq(z(a, HEART, b), f"cặp đôi {v} với trái tim", f"couple with heart: {e}")
        seq(z(a, HEART, KISS, b), f"nụ hôn {v}", f"kiss: {e}")
    seq(z(0x1F9D1, HEART, 0x1F9D1), "cặp đôi với trái tim", "couple with heart")
    # Cờ và biểu tượng ghép
    seq(z(0x1F3F3, 0x1F308), "cờ cầu vồng", "rainbow flag")
    seq(z(0x1F3F3, 0x26A7), "cờ chuyển giới", "transgender flag")
    seq(z(0x1F3F4, 0x2620), "cờ cướp biển", "pirate flag")
    seq(z(HEART, 0x1F525), "trái tim rực lửa", "heart on fire")
    seq(z(HEART, 0x1FA79), "trái tim được băng bó", "mending heart")
    seq(z(0x1F62E, 0x1F4A8), "mặt thở phào", "face exhaling")
    seq(z(0x1F635, 0x1F4AB), "mặt chóng mặt xoay tròn", "face with spiral eyes")
    seq(z(0x1F636, 0x1F32B), "mặt trong sương mù", "face in clouds")
    seq(z(0x1F642, 0x2194), "mặt lắc đầu", "head shaking horizontally")
    seq(z(0x1F642, 0x2195), "mặt gật đầu", "head shaking vertically")
    seq(z(0x1F43B, 0x2744), "gấu Bắc cực", "polar bear")
    seq(z(0x1F415, 0x1F9BA), "chó dịch vụ", "service dog")
    seq(z(0x1F408, 0x2B1B), "mèo đen", "black cat")
    seq(z(0x1F426, 0x2B1B), "chim đen", "black bird")
    seq(z(0x1F426, 0x1F525), "chim phượng hoàng", "phoenix")
    seq(z(0x1F441, 0x1F5E8), "mắt trong bong bóng thoại", "eye in speech bubble")
    seq(z(0x1F9D1, 0x1F91D, 0x1F9D1), "hai người nắm tay", "people holding hands")
    seq(z(0x1F34B, 0x1F7E9), "quả chanh xanh", "lime")
    seq(z(0x1F426, 0x2B1B), "chim đen", "black bird")


TAG_FLAGS = [("gbeng", "cờ nước Anh", "flag of England"), ("gbsct", "cờ Scotland", "flag of Scotland"),
             ("gbwls", "cờ xứ Wales", "flag of Wales")]

# --------------------------------------------------------------------------------------------- kiểm tra
def check(vi, en):
    problems = []
    targets = {c for c in EMOJI if c > 0xFF} | {c for c, _, _ in EMOJI16}
    for cp in sorted(targets):
        if (cp,) not in vi:
            problems.append(f"thiếu tên Việt U+{cp:X}")
        if (cp,) not in en:
            problems.append(f"thiếu tên Anh U+{cp:X}")
    for lang, table in (("vi", vi), ("en", en)):
        seen = {}
        for k, name in table.items():
            if len(k) != 1:
                continue
            seen.setdefault(name.strip().lower(), []).append(k[0])
        for name, cps in seen.items():
            if len(cps) > 1:
                problems.append(f"trùng tên {lang} '{name}': " + ", ".join(f"U+{c:X}" for c in cps))
    return problems


# --------------------------------------------------------------------------------------------- ghi Kotlin
HEADER = ("// SINH TỰ ĐỘNG bởi tools/emoji/build_emoji.py từ tools/emoji/*. KHÔNG sửa tay: sửa bảng nguồn rồi chạy lại.\n")


def ints(values):
    lines, cur = [], "        "
    for v in values:
        item = f"0x{v:X}, "
        if len(cur) + len(item) > 118:
            lines.append(cur.rstrip())
            cur = "        "
        cur += item
    lines.append(cur.rstrip())
    return "\n".join(lines)


def write_data():
    pres = [x for r in to_ranges(PRESENTATION) for x in r]
    allr = [x for r in to_ranges(EMOJI | {c for c, _, _ in EMOJI16}) for x in r]
    text = f'''package com.example.vnscreenreader.core

{HEADER}
/**
 * Thuộc tính emoji theo Unicode (Emoji 15.1 + các emoji 16.0 đã biết). Dùng để nhận diện CHÍNH XÁC thay cho các dải mã
 * rộng: dải cũ coi hàng nghìn ký tự không phải emoji (● ✓ ➤ ☐ ...) là emoji và đọc thành "biểu tượng cảm xúc".
 */
object EmojiData {{

    /** Cặp [từ, đến] đã sắp xếp: ký tự mặc định hiển thị dạng hình (Emoji_Presentation). */
    private val PRESENTATION = intArrayOf(
{ints(pres)}
    )

    /** Cặp [từ, đến] đã sắp xếp: mọi ký tự có thuộc tính Emoji (kể cả loại mặc định hiển thị dạng chữ). */
    private val ALL = intArrayOf(
{ints(allr)}
    )

    /** Ký tự dạng chữ (không có U+FE0F) mà người dùng vẫn hiểu là emoji: đọc tên. */
    private val PLAIN_TEXT = intArrayOf(
{ints(sorted(PLAIN_TEXT_EMOJI))}
    )

    fun isPresentation(cp: Int): Boolean = inRanges(PRESENTATION, cp)

    fun isEmoji(cp: Int): Boolean = inRanges(ALL, cp)

    fun isPlainTextEmoji(cp: Int): Boolean = PLAIN_TEXT.binarySearch(cp) >= 0

    /** Duyệt mọi ký tự Emoji_Presentation (dùng để kiểm thử độ phủ của bảng tên). */
    internal fun forEachPresentation(action: (Int) -> Unit) {{
        var i = 0
        while (i < PRESENTATION.size) {{
            for (cp in PRESENTATION[i]..PRESENTATION[i + 1]) action(cp)
            i += 2
        }}
    }}

    private fun inRanges(ranges: IntArray, cp: Int): Boolean {{
        var lo = 0
        var hi = ranges.size / 2 - 1
        while (lo <= hi) {{
            val mid = (lo + hi) ushr 1
            when {{
                cp < ranges[mid * 2] -> hi = mid - 1
                cp > ranges[mid * 2 + 1] -> lo = mid + 1
                else -> return true
            }}
        }}
        return false
    }}
}}
'''
    (KT / "EmojiData.kt").write_text(text, encoding="utf-8")


def lines_for(table, tags):
    rows = [f"{hexkey(k)}={v}" for k, v in sorted(table.items())]
    rows += [f"T:{code}={name}" for code, name in tags]
    return rows


def chunks(rows):
    out = []
    for i in range(0, len(rows), CHUNK_LINES):
        body = "\n".join(rows[i:i + CHUNK_LINES])
        assert '"""' not in body and "$" not in body and "\\" not in body
        out.append('        """\n' + body + '\n"""')
    return ",\n".join(out)


def write_names(vi, en):
    symbol_cps = sorted(k[0] for k in vi if len(k) == 1 and k[0] not in EMOJI and k[0] not in {c for c, _, _ in EMOJI16})
    text = f'''package com.example.vnscreenreader.core

{HEADER}
/**
 * Tên biểu tượng cảm xúc theo ngôn ngữ. Khóa là chuỗi emoji ĐÃ CHUẨN HÓA (bỏ U+FE0F, màu da, thẻ); cờ quốc gia và
 * khung phím không nằm trong bảng mà do [EmojiText] dựng từ mã vùng / ký tự. Bảng được nạp lười từ chuỗi dữ liệu để
 * không vượt giới hạn kích thước phương thức của JVM.
 */
object EmojiNames {{

    val VI: Map<String, String> by lazy(LazyThreadSafetyMode.PUBLICATION) {{ parse(VI_DATA) }}

    val EN: Map<String, String> by lazy(LazyThreadSafetyMode.PUBLICATION) {{ parse(EN_DATA) }}

    /** Ký hiệu KHÔNG thuộc nhóm emoji nhưng có tên (gạch đầu dòng ●, dấu tích ✓, ngôi sao ★...). */
    val SYMBOL_CODE_POINTS: Set<Int> = setOf({", ".join(f"0x{c:X}" for c in symbol_cps)})

    private fun parse(chunks: Array<String>): Map<String, String> {{
        val out = HashMap<String, String>(2048)
        for (chunk in chunks) {{
            for (line in chunk.lineSequence()) {{
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                out[keyOf(line.substring(0, eq))] = line.substring(eq + 1)
            }}
        }}
        return out
    }}

    /** "1F468-200D-1F469" -> chuỗi emoji; "T:gbeng" -> "tag:gbeng". */
    private fun keyOf(hex: String): String {{
        if (hex.startsWith("T:")) return "tag:" + hex.substring(2)
        val sb = StringBuilder()
        for (part in hex.split('-')) sb.appendCodePoint(part.toInt(16))
        return sb.toString()
    }}

    private val VI_DATA: Array<String> = arrayOf(
{chunks(lines_for(vi, [(c, v) for c, v, _ in TAG_FLAGS]))}
    )

    private val EN_DATA: Array<String> = arrayOf(
{chunks(lines_for(en, [(c, e) for c, _, e in TAG_FLAGS]))}
    )
}}
'''
    (KT / "EmojiNames.kt").write_text(text, encoding="utf-8")


def main():
    vi, en = build_names()
    problems = check(vi, en)
    print(f"VI {len(vi)} mục, EN {len(en)} mục, Emoji>0xFF: {len([c for c in EMOJI if c > 0xFF])}")
    if problems:
        print("\n".join(problems))
        print(f"{len(problems)} vấn đề")
        if "--check" in sys.argv or problems:
            sys.exit(1)
    if "--check" in sys.argv:
        print("OK (chỉ kiểm tra)")
        return
    write_data()
    write_names(vi, en)
    print("Đã ghi EmojiData.kt và EmojiNames.kt")


if __name__ == "__main__":
    main()
