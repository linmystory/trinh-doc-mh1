# -*- coding: utf-8 -*-
import re, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from table_a import A
from table_b import B
from table_c import C
from table_d import D
from table_plurals import PLURALS, QUANTITIES
ALL_ROWS = A + B + C + D
# Chuỗi có số đếm xuất ra <plurals>, không phải <string>; vẫn có khóa S như mọi chuỗi khác.
ROWS = [r for r in ALL_ROWS if r[0] not in PLURALS]
PLURAL_KEYS = [r[0] for r in ALL_ROWS if r[0] in PLURALS]
assert set(PLURAL_KEYS) == set(PLURALS), set(PLURALS) - set(PLURAL_KEYS)
LANGS = ["en", "vi", "fr", "es", "ru", "ar", "zh"]
FOLDERS = {"en": "values", "vi": "values-vi", "fr": "values-fr", "es": "values-es",
           "ru": "values-ru", "ar": "values-ar", "zh": "values-zh"}
import zh_hant
PROJ = sys.argv[1]
RES = f"{PROJ}/app/src/main/res"
KT = f"{PROJ}/app/src/main/java/com/example/vnscreenreader"

def placeholders(s):
    return sorted(m[0] + "$" + m[1] for m in re.findall(r"%(\d+)\$\d*([sd])", s))

for r in ROWS:
    assert len(r) == 8, r[0]
    base = placeholders(r[1])
    for i, l in enumerate(LANGS):
        assert placeholders(r[1 + i]) == base, (r[0], l, "placeholder mismatch")
        assert r[1 + i].strip() == r[1 + i], (r[0], l, "leading/trailing whitespace is trimmed by aapt")

def placeholders_set(s):
    return sorted(set(placeholders(s)))

for k, (idx, per_lang) in PLURALS.items():
    assert set(per_lang) == set(LANGS), (k, "thiếu ngôn ngữ")
    for l, forms in per_lang.items():
        assert set(forms) == set(QUANTITIES[l]), (k, l, "sai tập quantity", sorted(forms))
        for q, text in forms.items():
            assert text.strip() == text, (k, l, q)
            assert set(placeholders(text)) <= set(placeholders(per_lang["en"]["other"])) | {"1$d", "2$d", "1$s", "3$s"}, (k, l, q)

def xml_escape(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    if s[:1] in "@?":
        s = "\\" + s
    return s

for i, l in enumerate(LANGS):
    d = f"{RES}/{FOLDERS[l]}"
    os.makedirs(d, exist_ok=True)
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for r in ROWS:
        out.append(f'    <string name="{r[0]}">{xml_escape(r[1 + i])}</string>')
    for k in PLURAL_KEYS:
        out.append(f'    <plurals name="{k}">')
        for q in QUANTITIES[l]:
            out.append(f'        <item quantity="{q}">{xml_escape(PLURALS[k][1][l][q])}</item>')
        out.append("    </plurals>")
    out.append("</resources>")
    open(f"{d}/strings.xml", "w", encoding="utf-8").write("\n".join(out) + "\n")

def kt_str(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", "\\n") + '"'

def enum_name(k):
    return k.upper()

# S.kt
lines = ["package com.example.vnscreenreader.core", "",
         "/** Khóa chuỗi giao diện/giọng đọc. SINH TỰ ĐỘNG từ bảng dịch: không sửa tay. */",
         "enum class S {"]
for r in ALL_ROWS:
    lines.append(f"    {enum_name(r[0])},")
lines.append("}")
open(f"{KT}/core/S.kt", "w", encoding="utf-8").write("\n".join(lines) + "\n")

# FallbackTexts.kt (tiếng Việt: dùng khi chưa cài provider, ví dụ trong unit test JVM)
lines = ["package com.example.vnscreenreader.core", "", "import java.util.Locale", "",
         "/** Bộ chuỗi tiếng Việt dự phòng cho môi trường không có tài nguyên Android. SINH TỰ ĐỘNG. */",
         "object FallbackTexts : TextProvider {",
         "    override val language: String = \"vi\"",
         "    private val table: Map<S, String> = mapOf("]
for r in ROWS:
    lines.append(f"        S.{enum_name(r[0])} to {kt_str(r[2])},")
for k in PLURAL_KEYS:
    lines.append(f"        S.{enum_name(k)} to {kt_str(PLURALS[k][1]['vi']['other'])},")
lines += ["    )", "",
          "    override fun get(key: S, args: Array<out Any>): String {",
          "        val raw = table.getValue(key)",
          "        return if (args.isEmpty()) raw else String.format(Locale.ROOT, raw, *args)",
          "    }", "}"]
open(f"{KT}/core/FallbackTexts.kt", "w", encoding="utf-8").write("\n".join(lines) + "\n")

# ResourceTexts.kt (Android)
os.makedirs(f"{KT}/i18n", exist_ok=True)
lines = ["package com.example.vnscreenreader.i18n", "",
         "import android.content.Context",
         "import com.example.vnscreenreader.R",
         "import com.example.vnscreenreader.core.S",
         "import com.example.vnscreenreader.core.TextProvider", "",
         "/** Lấy chuỗi từ tài nguyên Android theo ngôn ngữ hiện hành của ứng dụng. SINH TỰ ĐỘNG. */",
         "class ResourceTexts(private val context: Context) : TextProvider {",
         "    override val language: String get() = context.resources.configuration.locales[0].language",
         "    override fun get(key: S, args: Array<out Any>): String = when (key) {"]
for k in PLURAL_KEYS:
    lines.append(f"        S.{enum_name(k)} -> plural(R.plurals.{k}, {PLURALS[k][0]}, args)")
lines += ["        else -> if (args.isEmpty()) context.getString(idOf(key)) else context.getString(idOf(key), *args)",
          "    }", "",
         "    private fun plural(id: Int, quantityArg: Int, args: Array<out Any>): String =",
         "        context.resources.getQuantityString(id, (args[quantityArg - 1] as Number).toInt(), *args)", "",
         "    private fun idOf(key: S): Int = when (key) "+"{"]
for r in ROWS:
    lines.append(f"        S.{enum_name(r[0])} -> R.string.{r[0]}")
lines.append("        else -> error(\"Khóa plurals đã xử lý ở get(): $key\")")
lines += ["    }", "}"]
open(f"{KT}/i18n/ResourceTexts.kt", "w", encoding="utf-8").write("\n".join(lines) + "\n")

# ---- Biến thể vùng miền
GB = [("recogniz", "recognis"), ("customizable", "customisable"), ("Unlabeled", "Unlabelled"), ("unlabeled", "unlabelled"),
      ("labeled", "labelled"), ("Labeled", "Labelled"), ("optimization", "optimisation"), ("optimized", "optimised"),
      ("Optimization", "Optimisation")]
def gb(text):
    for a, b in GB:
        text = text.replace(a, b)
    return text
d = f"{RES}/values-en-rGB"; os.makedirs(d, exist_ok=True)
out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
n_gb = 0
for r in ROWS:
    g = gb(r[1])
    if g != r[1]:
        out.append(f'    <string name="{r[0]}">{xml_escape(g)}</string>'); n_gb += 1
out.append("</resources>")
open(f"{d}/strings.xml", "w", encoding="utf-8").write("\n".join(out) + "\n")
d = f"{RES}/values-b+zh+Hant"; os.makedirs(d, exist_ok=True)
out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
for r in ROWS:
    out.append(f'    <string name="{r[0]}">{xml_escape(zh_hant.to_trad(r[7]))}</string>')
for k in PLURAL_KEYS:
    out.append(f'    <plurals name="{k}">')
    out.append(f'        <item quantity="other">{xml_escape(zh_hant.to_trad(PLURALS[k][1]["zh"]["other"]))}</item>')
    out.append("    </plurals>")
out.append("</resources>")
open(f"{d}/strings.xml", "w", encoding="utf-8").write("\n".join(out) + "\n")
print(len(ALL_ROWS), "strings (" + str(len(PLURAL_KEYS)) + " plurals),", len(LANGS), "languages + zh-Hant + en-GB (" + str(n_gb) + " strings)")
