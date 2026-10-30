# -*- coding: utf-8 -*-
"""Chuỗi có số đếm (plurals). Mỗi mục: khóa -> (chỉ số đối số (1-based) làm quantity, {ngôn ngữ: {quantity: văn bản}}).

Quy tắc CLDR: en one/other; vi other; fr one/many/other; es one/many/other; ru one/few/many/other;
ar zero/one/two/few/many/other; zh other. Placeholder phải giống nhau giữa mọi quantity (trừ khi chỉ dùng một
phần: Java bỏ qua đối số thừa).
"""

def fill(tpl, forms):
    """Thay {N} trong mẫu bằng dạng danh từ theo từng quantity; quantity thiếu lấy 'other'."""
    out = {q: tpl.replace("{N}", f) for q, f in forms.items()}
    return out

QUANTITIES = {
    "en": ["one", "other"], "vi": ["other"], "fr": ["one", "many", "other"], "es": ["one", "many", "other"],
    "ru": ["one", "few", "many", "other"], "ar": ["zero", "one", "two", "few", "many", "other"], "zh": ["other"],
}

def build(idx, tpl, nouns):
    """tpl: {lang: mẫu có {N}}; nouns: {lang: {quantity: dạng danh từ}} (đã gồm số nếu cần)."""
    return idx, {l: fill(tpl[l], nouns[l]) for l in tpl}

# ----- Danh từ kèm số ("%d" đã nằm trong dạng): chỉ số đối số là N
def forms(n):  # n = "%2$d" hoặc "%1$d"
    return n

def mk(n):
    return {
        "item": {
            "en": {"one": f"{n} item", "other": f"{n} items"},
            "vi": {"other": f"{n} mục"},
            "fr": {"one": f"{n} élément", "many": f"{n} éléments", "other": f"{n} éléments"},
            "es": {"one": f"{n} elemento", "many": f"{n} de elementos", "other": f"{n} elementos"},
            "ru": {"one": f"{n} пункт", "few": f"{n} пункта", "many": f"{n} пунктов", "other": f"{n} пункта"},
            "ar": {"zero": f"{n} عنصر", "one": "عنصر واحد", "two": "عنصران", "few": f"{n} عناصر",
                   "many": f"{n} عنصرًا", "other": f"{n} عنصر"},
            "zh": {"other": f"{n}项"},
        },
        "finger": {
            "en": {"one": f"{n} finger", "other": f"{n} fingers"},
            "vi": {"other": f"{n} ngón"},
            "fr": {"one": f"{n} doigt", "many": f"{n} doigts", "other": f"{n} doigts"},
            "es": {"one": f"{n} dedo", "many": f"{n} de dedos", "other": f"{n} dedos"},
            "ru": {"one": f"{n} палец", "few": f"{n} пальца", "many": f"{n} пальцев", "other": f"{n} пальца"},
            "ar": {"zero": f"{n} أصابع", "one": "إصبع واحد", "two": "إصبعان", "few": f"{n} أصابع",
                   "many": f"{n} إصبعًا", "other": f"{n} إصبع"},
            "zh": {"other": f"{n} 指"},
        },
        "package": {
            "en": {"one": f"{n} package", "other": f"{n} packages"},
            "vi": {"other": f"{n} package"},
            "fr": {"one": f"{n} package", "many": f"{n} packages", "other": f"{n} packages"},
            "es": {"one": f"{n} paquete", "many": f"{n} de paquetes", "other": f"{n} paquetes"},
            "ru": {"one": f"{n} пакета", "few": f"{n} пакетов", "many": f"{n} пакетов", "other": f"{n} пакета"},
            "ar": {"zero": f"{n} حزم", "one": "حزمة واحدة", "two": "حزمتان", "few": f"{n} حزم",
                   "many": f"{n} حزمة", "other": f"{n} حزمة"},
            "zh": {"other": f"{n} 个包名"},
        },
        "char": {
            "en": {"one": f"{n} character", "other": f"{n} characters"},
            "vi": {"other": f"{n} ký tự"},
            "fr": {"one": f"{n} caractère", "many": f"{n} caractères", "other": f"{n} caractères"},
            "es": {"one": f"{n} carácter", "many": f"{n} de caracteres", "other": f"{n} caracteres"},
            "ru": {"one": f"{n} символа", "few": f"{n} символов", "many": f"{n} символов", "other": f"{n} символа"},
            "ar": {"zero": f"{n} أحرف", "one": "حرف واحد", "two": "حرفان", "few": f"{n} أحرف",
                   "many": f"{n} حرفًا", "other": f"{n} حرف"},
            "zh": {"other": f"{n} 个字符"},
        },
    }

def join(tpl_by_lang, noun_forms):
    """tpl_by_lang: {lang: mẫu chứa {N}}; noun_forms: kết quả mk()[loại]."""
    res = {}
    for l, tpl in tpl_by_lang.items():
        res[l] = {q: tpl.replace("{N}", f) for q, f in noun_forms[l].items()}
    return res

n1, n2 = mk("%1$d"), mk("%2$d")

PLURALS = {
    "menu_opening": (2, join({
        "en": "%1$s, {N}.", "vi": "%1$s, {N}.", "fr": "%1$s, {N}.", "es": "%1$s, {N}.", "ru": "%1$s, {N}.",
        "ar": "%1$s، {N}.", "zh": "%1$s，共{N}。"}, n2["item"])),
    "gs_n_fingers": (1, join({l: "{N}" for l in QUANTITIES}, n1["finger"])),
    "script_err_packages": (1, join({
        "en": "At most {N}", "vi": "Tối đa {N}", "fr": "{N} maximum", "es": "Máximo {N}", "ru": "Не более {N}",
        "ar": "الحد الأقصى: {N}", "zh": "最多 {N}"}, n1["package"])),
    "script_err_name": (1, join({
        "en": "name must not be empty and at most {N}", "vi": "name không được rỗng và tối đa {N}",
        "fr": "name ne doit pas être vide et fait {N} maximum", "es": "name no puede estar vacío y admite un máximo de {N}",
        "ru": "name не должно быть пустым и содержать не более {N}", "ar": "يجب ألا يكون name فارغًا وبحد أقصى {N}",
        "zh": "name 不能为空，且最长 {N}"}, n1["char"])),
    "script_err_message": (1, join({
        "en": "message may be at most {N}", "vi": "message dài tối đa {N}", "fr": "message : {N} maximum",
        "es": "message: máximo {N}", "ru": "message — не более {N}", "ar": "يجب ألا يزيد message عن {N}",
        "zh": "message 最长 {N}"}, n1["char"])),
    "script_err_pattern": (2, join({
        "en": "%1$s may be at most {N}", "vi": "%1$s dài tối đa {N}", "fr": "%1$s : {N} maximum",
        "es": "%1$s: máximo {N}", "ru": "%1$s — не более {N}", "ar": "يجب ألا يزيد %1$s عن {N}",
        "zh": "%1$s 最长 {N}"}, n2["char"])),
    "notif_burst": (2, {
        "en": {"one": "New notification from %1$s: %2$d. Latest: %3$s", "other": "New notifications from %1$s: %2$d. Latest: %3$s"},
        "vi": {"other": "%2$d thông báo mới từ %1$s, mới nhất: %3$s"},
        "fr": {"one": "Nouvelle notification de %1$s : %2$d. Dernière : %3$s", "many": "Nouvelles notifications de %1$s : %2$d. Dernière : %3$s", "other": "Nouvelles notifications de %1$s : %2$d. Dernière : %3$s"},
        "es": {"one": "Notificación nueva de %1$s: %2$d. Última: %3$s", "many": "Notificaciones nuevas de %1$s: %2$d. Última: %3$s", "other": "Notificaciones nuevas de %1$s: %2$d. Última: %3$s"},
        "ru": {"one": "Новое уведомление от %1$s: %2$d. Последнее: %3$s", "few": "Новые уведомления от %1$s: %2$d. Последнее: %3$s", "many": "Новые уведомления от %1$s: %2$d. Последнее: %3$s", "other": "Новые уведомления от %1$s: %2$d. Последнее: %3$s"},
        "ar": {"zero": "إشعارات جديدة من %1$s: %2$d. الأحدث: %3$s", "one": "إشعار جديد من %1$s. الأحدث: %3$s", "two": "إشعاران جديدان من %1$s. الأحدث: %3$s", "few": "إشعارات جديدة من %1$s: %2$d. الأحدث: %3$s", "many": "إشعارات جديدة من %1$s: %2$d. الأحدث: %3$s", "other": "إشعارات جديدة من %1$s: %2$d. الأحدث: %3$s"},
        "zh": {"other": "%1$s的新通知：%2$d 条。最新：%3$s"},
    }),
}
