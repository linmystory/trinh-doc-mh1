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

def mk_more(n):
    """Danh từ bổ sung: phần trăm, cấp (độ sâu), nhãn."""
    return {
        "percent": {
            "en": {"one": f"{n} percent", "other": f"{n} percent"},
            "vi": {"other": f"{n} phần trăm"},
            "fr": {"one": f"{n} pour cent", "many": f"{n} pour cent", "other": f"{n} pour cent"},
            "es": {"one": f"{n} por ciento", "many": f"{n} por ciento", "other": f"{n} por ciento"},
            "ru": {"one": f"{n} процент", "few": f"{n} процента", "many": f"{n} процентов", "other": f"{n} процента"},
            "ar": {q: f"{n} بالمئة" for q in ["zero", "one", "two", "few", "many", "other"]},
            "zh": {"other": f"百分之{n}"},
        },
        "level": {
            "en": {"one": f"{n} level", "other": f"{n} levels"},
            "vi": {"other": f"{n} cấp"},
            "fr": {"one": f"{n} niveau", "many": f"{n} niveaux", "other": f"{n} niveaux"},
            "es": {"one": f"{n} nivel", "many": f"{n} de niveles", "other": f"{n} niveles"},
            "ru": {"one": f"{n} уровень", "few": f"{n} уровня", "many": f"{n} уровней", "other": f"{n} уровня"},
            "ar": {"zero": f"{n} مستويات", "one": "مستوى واحد", "two": "مستويان", "few": f"{n} مستويات",
                   "many": f"{n} مستوى", "other": f"{n} مستوى"},
            "zh": {"other": f"{n} 层"},
        },
        "label": {
            "en": {"one": f"{n} label", "other": f"{n} labels"},
            "vi": {"other": f"{n} nhãn"},
            "fr": {"one": f"{n} étiquette", "many": f"{n} étiquettes", "other": f"{n} étiquettes"},
            "es": {"one": f"{n} etiqueta", "many": f"{n} de etiquetas", "other": f"{n} etiquetas"},
            "ru": {"one": f"{n} подписи", "few": f"{n} подписей", "many": f"{n} подписей", "other": f"{n} подписи"},
            "ar": {"zero": f"{n} تسميات", "one": "تسمية واحدة", "two": "تسميتان", "few": f"{n} تسميات",
                   "many": f"{n} تسمية", "other": f"{n} تسمية"},
            "zh": {"other": f"{n} 个标签"},
        },
    }


def mk_functions(n):
    """Danh từ "chức năng" (một chức năng của ứng dụng). "function": dạng đứng sau số ở chủ cách ("đã có N chức năng");
    "function_upto": dạng sau "tối đa / không quá" và sau giới từ (tiếng Nga dùng sở hữu cách; tiếng Ả Rập dùng cách phụ
    thuộc, số đôi là "وظيفتين")."""
    plain = {
        "en": {"one": f"{n} function", "other": f"{n} functions"},
        "vi": {"other": f"{n} chức năng"},
        "fr": {"one": f"{n} fonction", "many": f"{n} fonctions", "other": f"{n} fonctions"},
        "es": {"one": f"{n} función", "many": f"{n} de funciones", "other": f"{n} funciones"},
        "ru": {"one": f"{n} функция", "few": f"{n} функции", "many": f"{n} функций", "other": f"{n} функции"},
        "ar": {"zero": f"{n} وظائف", "one": "وظيفة واحدة", "two": "وظيفتان", "few": f"{n} وظائف",
               "many": f"{n} وظيفة", "other": f"{n} وظيفة"},
        "zh": {"other": f"{n}项功能"},
    }
    upto = dict(plain)
    upto["ru"] = {"one": f"{n} функции", "few": f"{n} функций", "many": f"{n} функций", "other": f"{n} функции"}
    upto["ar"] = {"zero": f"{n} وظائف", "one": "وظيفة واحدة", "two": "وظيفتين", "few": f"{n} وظائف",
                  "many": f"{n} وظيفة", "other": f"{n} وظيفة"}
    return {"function": plain, "function_upto": upto}


def join(tpl_by_lang, noun_forms):
    """tpl_by_lang: {lang: mẫu chứa {N}}; noun_forms: kết quả mk()[loại]."""
    res = {}
    for l, tpl in tpl_by_lang.items():
        res[l] = {q: tpl.replace("{N}", f) for q, f in noun_forms[l].items()}
    return res

n1, n2 = mk("%1$d"), mk("%2$d")
m1 = mk_more("%1$d")
m1f = mk_functions("%1$d")

PLURALS = {
    "sp_state_percent": (1, join({l: "{N}" for l in QUANTITIES}, m1["percent"])),
    "script_err_depth": (1, join({
        "en": "JSON is nested too deeply (max {N})", "vi": "JSON lồng quá sâu (tối đa {N})",
        "fr": "JSON imbriqué trop profondément (max. {N})", "es": "JSON anidado demasiado profundo (máx. {N})",
        "ru": "Слишком глубокая вложенность JSON (максимум {N})", "ar": "تداخل JSON عميق جدًا (الحد الأقصى {N})",
        "zh": "JSON 嵌套过深（最多 {N}）"}, m1["level"])),
    "script_err_rules_count": (1, join({
        "en": "rules must have between 1 and {N}", "vi": "rules phải có từ 1 đến {N}",
        "fr": "rules doit contenir de 1 à {N}", "es": "rules debe tener entre 1 y {N}",
        "ru": "rules должно содержать от 1 до {N}", "ar": "يجب أن تحتوي rules على عنصر واحد إلى {N}",
        "zh": "rules 必须包含 1 到 {N}"}, n1["item"])),
    "lb_limits": (1, join({
        "en": "Only buttons with an internal identifier can be labeled. At most {N}.",
        "vi": "Chỉ nút có mã định danh nội bộ mới gắn được. Tối đa {N}.",
        "fr": "Seuls les boutons ayant un identifiant interne peuvent être étiquetés. {N} au maximum.",
        "es": "Solo se pueden etiquetar botones con un identificador interno. Máximo {N}.",
        "ru": "Подписать можно только кнопки с внутренним идентификатором. Не более {N}.",
        "ar": "يمكن تسمية الأزرار ذات المعرّف الداخلي فقط. الحد الأقصى {N}.",
        "zh": "只有带内部标识符的按钮才能添加标签。最多 {N}。"}, m1["label"])),
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
    # Trình đơn phần tử tùy chỉnh (v59): số chức năng tối đa / hiện có là SỐ LƯỢNG nên là <plurals>.
    "mn_local_desc": (1, join({
        "en": "Your own list of up to {N}, in the order you choose. Tap an item to move, replace or remove it.",
        "vi": "Danh sách riêng của bạn, tối đa {N}, xếp theo thứ tự bạn chọn. Chạm vào một mục để chuyển, thay hoặc xóa.",
        "fr": "Votre propre liste de {N} au maximum, dans l'ordre de votre choix. Touchez un élément pour le déplacer, le remplacer ou le supprimer.",
        "es": "Su propia lista de hasta {N}, en el orden que elija. Pulse un elemento para moverlo, reemplazarlo o quitarlo.",
        "ru": "Ваш собственный список, не более {N}, в нужном вам порядке. Нажмите на пункт, чтобы переместить, заменить или удалить его.",
        "ar": "قائمتك الخاصة بما لا يزيد على {N}، بالترتيب الذي تختاره. المس عنصرًا لنقله أو استبداله أو إزالته.",
        "zh": "你自己的列表，最多{N}，顺序由你决定。点按某一项可移动、替换或删除。"}, m1f["function_upto"])),
    "mn_add_full": (1, join({
        "en": "The menu already has {N}, the maximum. Remove or replace one to add another.",
        "vi": "Trình đơn đã có {N}, là mức tối đa. Hãy xóa hoặc thay một mục để thêm chức năng khác.",
        "fr": "Le menu contient déjà {N}, le maximum. Retirez-en ou remplacez-en une pour en ajouter une autre.",
        "es": "El menú ya tiene {N}, el máximo. Quite o reemplace una para añadir otra.",
        "ru": "В меню уже {N} — это максимум. Удалите или замените одну, чтобы добавить другую.",
        "ar": "تحتوي القائمة بالفعل على {N}، وهو الحد الأقصى. أزل وظيفة أو استبدلها لإضافة أخرى.",
        "zh": "菜单已有{N}，已达上限。请删除或替换一项后再添加。"},
        {**m1f["function"], "ar": m1f["function_upto"]["ar"]})),
    "hp_menus_body": (1, join({
        "en": "The general menu has every function of the app, split into groups. The element menu is your own list of up to {N}: add, remove, reorder or replace them in Controls, Quick menus. Each menu opens with the gesture you assign to it.",
        "vi": "Trình đơn chung có đủ mọi chức năng của ứng dụng, chia thành các nhóm. Trình đơn phần tử là danh sách riêng của bạn, tối đa {N}: thêm, xóa, đổi thứ tự hoặc thay chức năng tại Điều khiển, Trình đơn nhanh. Mỗi trình đơn mở bằng cử chỉ bạn gán cho nó.",
        "fr": "Le menu général contient toutes les fonctions de l'application, réparties en groupes. Le menu de l'élément est votre propre liste de {N} au maximum : ajoutez, retirez, réordonnez ou remplacez-les dans Commandes, Menus rapides. Chaque menu s'ouvre avec le geste que vous lui attribuez.",
        "es": "El menú general contiene todas las funciones de la aplicación, repartidas en grupos. El menú del elemento es su propia lista de hasta {N}: añada, quite, reordene o reemplace funciones en Controles, Menús rápidos. Cada menú se abre con el gesto que usted le asigne.",
        "ru": "Общее меню содержит все функции приложения, разделённые на группы. Меню элемента — ваш собственный список не более чем из {N}: добавляйте, удаляйте, меняйте порядок или заменяйте функции в разделе «Управление», «Быстрые меню». Каждое меню открывается жестом, который вы ему назначите.",
        "ar": "تحتوي القائمة العامة على جميع وظائف التطبيق موزعة على مجموعات. أما قائمة العنصر فهي قائمتك الخاصة بما لا يزيد على {N}: أضف الوظائف أو أزلها أو أعد ترتيبها أو استبدلها من «التحكم» ثم «القوائم السريعة». تُفتح كل قائمة بالإيماءة التي تعيّنها لها.",
        "zh": "通用菜单包含应用的全部功能，分为若干分组。元素菜单是你自己的列表，最多{N}：可在“控制”中的“快捷菜单”里添加、删除、调整顺序或替换。每个菜单都用你为它指定的手势打开。"}, m1f["function_upto"])),
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
