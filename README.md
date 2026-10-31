# Đọc Màn Hình Việt Pro

Trình đọc màn hình (screen reader) cho Android dành cho người khiếm thị, viết bằng Kotlin và Jetpack Compose, lấy cảm hứng từ JieShuo.

## Tính năng
- Đọc nội dung bằng giọng tiếng Việt (Android TextToSpeech), ngắt lời tức thì, hàng đợi thông minh.
  Văn bản dài được tự chia nhỏ; nếu TTS chưa sẵn sàng lúc khởi động máy thì tự thử lại.
- Ngắt câu theo ngữ pháp tiếng Việt: không cắt sau từ treo (của, và, là...), không tách số/giờ/viết tắt.
- Đọc thêm trạng thái: vô hiệu hóa, đã chọn, tiêu đề, thanh trượt (%), lỗi nhập, vai trò do app khai báo.
- Xử lý sự kiện trên luồng nền (coroutines), lọc sự kiện trùng để giảm độ trễ.
- Khung viền xanh lá quanh phần tử đang chọn.
- Khám phá bằng cảm ứng: đặt ngón tay lên phần tử nào thì đọc phần tử đó; chạm hai lần (ở bất kỳ đâu) để nhấn
  phần tử vừa nghe.
- 34 cử chỉ (1 ngón: vuốt 4 hướng, 12 cử chỉ hai bước, chạm hai lần, chạm hai lần và giữ; 2, 3, 4 ngón), gán chức năng tùy ý.
- **AI trên máy, không dùng mạng** (mô hình ML Kit đóng gói sẵn trong APK; ứng dụng không xin quyền INTERNET):
  - *Đọc chữ trên màn hình (OCR)* và *Mô tả hình ảnh của phần tử đang chọn* - gán vào cử chỉ bất kỳ trong thẻ "Cử chỉ".
  - Đọc thông báo mới đã được tóm tắt (bỏ trùng, rút gọn, gộp khi dồn dập); bật/tắt ở thẻ cài đặt chung.
- Phần tử không có nhãn (không cần chụp màn hình): nhãn ứng dụng → nhãn tự gán → chữ gộp của phần tử con →
  đoán theo tên view (báo "nhãn đoán") → "không có nhãn". Gắn nhãn tự gán bằng cử chỉ "Gắn nhãn cho phần tử đang chọn".
- Chế độ phản hồi: chỉ rung / chỉ âm báo / cả hai / tắt, đổi tức thì, không ảnh hưởng giọng đọc.
- Rung và âm báo (tổng hợp tại chỗ, không tệp âm thanh) khi chuyển phần tử, chạm đầu/cuối, nhấn.
- Chọn công cụ TTS (engine) ngay trong cài đặt, có hiệu lực tức thì.
- Cài đặt thiết kế lại theo bố cục thích ứng: máy rộng hiện danh sách + nội dung song song; máy hẹp mở từng mục.
- Hệ thống tiện ích: plugin dựng sẵn và script JSON nạp từ tệp.
- Giao diện độ tương phản cao (đen/vàng).

## Cử chỉ mặc định
| Cử chỉ | Chức năng |
|---|---|
| Vuốt phải / trái | Phần tử kế tiếp / trước đó |
| Chạm hai lần | Nhấn phần tử đang chọn |
| Chạm hai lần và giữ | Nhấn giữ phần tử đang chọn (nếu hệ thống gửi cử chỉ này tới app; kiểm tra bằng **Kiểm tra cử chỉ**) |
| Vuốt lên / xuống | Cuộn xuống / cuộn lên |
| Xuống rồi phải | **Trình đơn chung** |
| Lên rồi phải | **Trình đơn của phần tử đang chọn** |
| Xuống rồi trái | Màn hình chính |
| Lên rồi trái | Quay lại |
| Xuống rồi lên | Chạy các tiện ích đã bật |
| Lên rồi xuống | Sao chép đơn vị đang đọc |
| Hai ngón chạm / chạm hai lần | Dừng đọc / lặp lại câu vừa đọc |
| Hai ngón vuốt lên / xuống | Cuộn xuống / cuộn lên |
| Hai hoặc ba ngón vuốt trái / phải | **Trang kế / trang trước** (chuyển trang ngang: ViewPager, tab vuốt ngang) |
| Ba ngón chạm / chạm hai lần | Trình đơn chung / trình đơn phần tử |
| Ba ngón vuốt lên / xuống | Đơn vị đọc trước / kế |
| Ba ngón chạm ba lần | Đọc từ đầu màn hình |
| Bốn ngón chạm / chạm hai lần | Đổi chế độ rung và âm báo / cho biết giờ và pin |

**Cố ý không gán** các cử chỉ hai bước bắt đầu bằng trái/phải (phải-rồi-xuống, trái-rồi-lên...): cú vuốt trái/phải hơi cong rất dễ bị nhận nhầm thành chúng và mở Màn hình chính hay Thông báo. Các chức năng đó có trong trình đơn chung. Bản này đổi hai cử chỉ mở trình đơn so với các bản trước (chung: xuống rồi phải; phần tử: lên rồi phải). Chỉ áp dụng cho người **chưa từng lưu cử chỉ riêng**; ai đã chỉnh cử chỉ thì giữ nguyên cấu hình cũ. Từ bản 2.6.0, cấu hình chỉ lưu những cử chỉ bạn thật sự đổi nên mặc định mới vẫn tới được các cử chỉ còn lại; bản đã lưu từ trước giữ nguyên. Đổi cử chỉ làm mất hết cử chỉ của "phần tử kế", "phần tử trước" hay "nhấn" sẽ có cảnh báo. Tất cả đều đổi được trong mục **Cử chỉ**. Android không cho dịch vụ hỗ trợ tiếp cận bắt cử chỉ vuốt từ cạnh màn hình.

## Trình đơn nhanh
Cử chỉ mở trình đơn hiển thị ở mục **Trình đơn** trong ứng dụng, lấy từ cấu hình thật của bạn.
- **Chung** (7 mục + "Khác"): đọc từ đầu, đọc từ phần tử hiện tại, lặp lại, tìm chữ, đọc chữ trên màn hình (OCR),
  thông báo, màn hình chính. Trình đơn con **Khác**: cài đặt nhanh, ứng dụng gần đây, đổi chế độ rung/âm báo,
  giờ và pin, chạy tiện ích, cài đặt ứng dụng, **Quay lại**.
- **Phần tử**: nhấn, nhấn giữ, đơn vị kế/trước, đổi đơn vị đọc, sao chép đơn vị, sao chép, đọc từng ký tự,
  gắn nhãn, mô tả hình ảnh, đọc chữ trong ảnh, cộng các hành động tùy chỉnh của ứng dụng. Riêng **ô nhập liệu**
  có thêm trình đơn con **Chỉnh sửa văn bản**: dán, cắt, chọn tất cả, đưa con trỏ về đầu/cuối, chọn đến đầu/cuối, **Quay lại**.
- Khi trình đơn mở: vuốt phải/trái chọn mục, chạm hai lần thực hiện, quay lại để lùi một cấp. Mở trình đơn đọc
  "Tên, N mục."; cử chỉ không dùng được trong trình đơn chỉ nhắc ngắn một lần.
- Cài đặt **Đọc hướng dẫn thao tác** (Đọc, mặc định bật): tắt thì trình đơn chỉ đọc tên mục, không đọc vị trí và lời nhắc.

## Bản 2.7.3

Dọn cảnh báo lint: icon ứng dụng (loa phát sóng âm, adaptive + monochrome), `dataExtractionRules` (không sao lưu), `AppRoot` dùng `LocalWindowInfo` thay `screenWidthDp`, `app/lint.xml` bỏ qua gợi ý chính tả/plurals của chuỗi sinh tự động.

## Bản 2.7.2

Sửa 408 lỗi lint `MissingTranslation`: `values-en-rGB` cố ý chỉ có 13 chuỗi nên lint đòi đủ bản dịch. Thêm `tools:ignore="MissingTranslation"` vào `values/strings.xml` (do `tools/i18n/gen.py` sinh); độ phủ ngôn ngữ vẫn được `TextsTest` kiểm.

## Bản 2.7.1

Sửa lỗi lint `LocalContextGetResourceValueCall` ở `PluginManagerScreen` (lấy chuỗi bằng `stringResource` thay vì `context.getString` trong Composable).

## Bản 2.7.0: nút bị ứng dụng giấu khỏi trợ năng

Một số ứng dụng (ví dụ ô tích "Tôi đồng ý với điều khoản") đánh dấu nút là "không quan trọng với trợ năng" nên trình đọc
không thấy và không bấm được.

- **Tiêu điểm chi tiết** (Cài đặt → Đọc, hoặc trình đơn chung → Khác, hoặc gán cử chỉ): bật cờ
  `FLAG_INCLUDE_NOT_IMPORTANT_VIEWS` lúc đang chạy và duyệt cả nút bấm được không nhãn nằm trong khung cha. Mặc định tắt.
- **Thông tin kỹ thuật** (trình đơn phần tử, hoặc gán cử chỉ): đọc lớp view, bấm/tích được, quan trọng với trợ năng, mã. Dùng để
  chẩn đoán màn hình khó.
- **Nhấn điều khiển bên cạnh**: nhấn đúp vào dòng chữ không bấm được sẽ nhấn ô tích/nút cùng hàng, gần nhất (tối đa 300 px).
- Chưa kiểm chứng trên máy thật (đặc biệt màn hình EPAY). Nếu ô tích vẫn không tới được thì dùng "Thông tin kỹ thuật" và báo lại.

## Bản 2.6.4

- Hàng đọc đầy: lời ưu tiên thấp (thông báo, "đã chạm") bị bỏ thay vì xóa câu đang đọc.
- Trình đơn đang mở: bỏ qua sự kiện focus của ứng dụng. Đổi cửa sổ: hủy tác vụ AI đang chạy.
- Giọng đọc và chữ giao diện cùng một nguồn ngôn ngữ. "Bỏ qua quảng cáo": từ chung chỉ khớp khi nhãn đúng bằng từ đó.
- Khóa luồng cho `EarconPlayer`; Regex của `EmojiText.tidy` thành hằng; sửa KDoc cài đặt.

## Bản 2.6.3

Gộp bản "v17" bạn gửi (đọc emoji có khoảng lặng, bảng emoji đầy đủ, `tools/emoji`) với các sửa rà soát mô tả hình ảnh/OCR bên dưới. 260 test offline đạt.

## Rà soát mô tả hình ảnh và OCR (2.6.1)

- Hủy tác vụ nhận dạng không còn giải phóng bitmap khi ML Kit còn đang đọc (tránh crash khi vuốt nhanh).
- Ô mật khẩu đang hiển thị bị tô đen trên ảnh chụp trước khi OCR/mô tả (mặc định); bật "đọc mật khẩu" trong Cài đặt thì không che.
- Vùng ảnh lấy theo vị trí SAU khi chụp; đổi phần tử thì hủy mô tả của phần tử cũ.
- Nhãn và OCR chạy song song, một nguồn lỗi không làm mất nguồn kia. Nhãn chưa dịch không đọc thô bằng giọng sai ngôn ngữ.
- Ẩn khung viền có timeout 300 ms (không treo khi màn hình tắt). Ảnh chụp là bitmap mutable để che vùng mật khẩu.
- OCR: hai cột xa nhau không dính thành một câu, giữ ký hiệu đơn như %, ?, không cắt giữa cặp surrogate.
- Thông báo: bộ nhớ ứng dụng theo LRU, giới hạn 240 ký tự. Đọc lại câu cuối không transform hai lần. TTS ưu tiên "ngôn ngữ theo ứng dụng".
- Chưa kiểm chứng trên máy thật: ML Kit, tô đen mật khẩu, timeout ẩn khung, locale theo ứng dụng của TTS.
- Cố ý giữ: thông báo chứa mã OTP vẫn được đọc to (người khiếm thị cần nghe); chưa có công tắc tắt.

## Chuyển trang, vuốt trái/phải và tự cuộn (2.6.0)
- **Trang kế / Trang trước** (hai hoặc ba ngón vuốt trái/phải): tìm vùng cuộn **ngang** (ViewPager, tab vuốt ngang, danh sách ngang) và chuyển trang. Chưa thử trên máy thật với từng loại giao diện.
- Vuốt phải/trái chuyển phần tử: nếu phần tử bạn chạm vào không phải điểm dừng (ví dụ con của khối đã gộp nhãn), app bắt đầu từ điểm dừng chứa nó thay vì nhảy về đầu/cuối danh sách.
- Tới phần tử cuối/đầu của màn hình: app tự cuộn một lần rồi chọn phần tử mới (như TalkBack); hết thật mới báo "phần tử cuối".
- Trình đơn tự đóng sau 30 giây không dùng; trong trình đơn, nếu bạn đã bỏ gán hết "phần tử kế/trước, nhấn, quay lại" thì cử chỉ mặc định của chúng được dùng tạm để không bị kẹt.

## Đọc biểu tượng cảm xúc (emoji)
Cài đặt → Đọc → **Đọc biểu tượng cảm xúc** (mặc định bật). Emoji được đổi thành tên trước khi giọng đọc ("😂" → "cười ra nước mắt", "👍" → "ngón tay cái giơ lên").

**Từ điển.** `core/EmojiNames.kt` và `core/EmojiData.kt` được **sinh tự động** bằng `python3 tools/emoji/build_emoji.py` từ `tools/emoji/` (dữ liệu Unicode `emoji-data.txt` bản Emoji 15.1, bảng tên đã duyệt `names_curated.tsv`, bảng tên bổ sung `names_vi_extra.txt` và các quy tắc trong chính tập lệnh: đồng hồ, cung hoàng đạo, nghề nghiệp, gia đình, cờ). Trình sinh **từ chối chạy** nếu có emoji thiếu tên Việt hoặc Anh, hoặc hai ký tự trùng tên (trùng tên làm "★★★★☆" bị đọc "ngôi sao, 5 lần"). Hiện phủ toàn bộ 1.410 ký tự có thuộc tính Emoji của Emoji 15.1 (trước đây 281, tức chỉ 23%) cộng 7 emoji của Emoji 16.0 bằng tiếng Việt và tiếng Anh. Ngôn ngữ khác đọc tên tiếng Anh. Emoji hoàn toàn mới chưa có trong bảng vẫn được đọc là "biểu tượng cảm xúc" thay vì im lặng.

**Nhận diện.** Dùng thuộc tính Unicode (Emoji, Emoji_Presentation) thay cho các dải mã rộng: các ký hiệu giao diện như ● ○ ✓ ➤ ☐ trước đây bị đọc nhầm là "biểu tượng cảm xúc"; nay có tên riêng (ví dụ ● "chấm tròn đen", ✓ "dấu tích") còn ký hiệu không có tên (→ ⇒ ™ © ▶ không kèm U+FE0F) được giữ nguyên. Hệ quả cần biết: dòng như "● Online" giờ đọc "chấm tròn đen, Online", và thanh đánh giá "★★★★☆" đọc "ngôi sao đặc, 4 lần, ngôi sao rỗng".

**Emoji ghép.** Hiểu kiểu hiển thị (U+FE0F), màu da, ZWJ, giới tính (♂ ♀ → hậu tố "nam"/"nữ"), khung phím ("phím số 1", "phím thăng"), cờ quốc gia (tên nước do hệ điều hành cung cấp theo ngôn ngữ: "cờ Việt Nam", "cờ Pháp"), cờ vùng (Anh, Scotland, Wales) và các tổ hợp quen thuộc (gia đình, nghề nghiệp, cờ cầu vồng, trái tim rực lửa...). Tổ hợp chưa có tên thì đọc từng thành phần cách nhau bằng dấu phẩy, không còn đọc dính "người đàn ông người phụ nữ".

**Chính sách ngắt nghỉ** (`EmojiRunPolicy`, `SpeechPlan`): nhiều giọng đọc gần như không nghỉ ở dấu phẩy nên tên các emoji dính vào nhau. Vì vậy:
- Một chuỗi emoji liền nhau (cho phép khoảng trắng đơn giữa các emoji) từ **hai mục trở lên** được đọc **từng mục như một đoạn riêng**, cách nhau khoảng lặng 160 ms (380 ms sau mỗi nhóm 4 mục) và 260 ms với chữ xung quanh. Khoảng lặng là "đoạn im lặng" của TTS nên chắc chắn có, và co lại theo tốc độ đọc (60–600 ms).
- Emoji giống hệt nhau **từ 4 lần trở lên** gộp "tên, N lần"; 2–3 lần đọc từng cái ("😂😂😂" → "cười ra nước mắt" ×3, có nhịp). Trước đây ngưỡng là 3.
- Một chuỗi đọc tối đa 12 mục, phần còn lại gộp "và N biểu tượng khác".
- Một emoji đơn lẻ vẫn nằm trong câu ("Hay quá, cười ra nước mắt") để không cắt vụn lời.

**Hàng đợi giọng nói.** Chính sách hàng đợi (`SpeechQueuePolicy`) đếm theo **câu**, không theo từng đoạn phát (`SpeechPendingTracker`): một câu emoji tách thành nhiều đoạn không còn làm hàng đợi trông "đầy" nên không bị lời kế tiếp cắt ngang. "Đọc lại" (`repeatLast`) giữ câu **gốc** nên lập lại đúng các khoảng lặng.

**Giới hạn, nói thẳng.**
- Tên emoji tiếng Việt do người soạn viết, **chưa được người bản ngữ rà soát**; một số tên có thể chưa tự nhiên.
- Ngôn ngữ giao diện khác tiếng Việt vẫn đọc tên emoji bằng tiếng Anh (chưa có bảng tên Pháp, Tây Ban Nha, Nga, Ả Rập, Trung).
- Tên nước trong cờ lấy từ dữ liệu ngôn ngữ của hệ điều hành; máy thiếu dữ liệu đó sẽ đọc "lá cờ".
- Khoảng lặng dùng `playSilentUtterance`; hành vi thật phụ thuộc engine TTS và **chưa được thử trên máy**.
- Chưa có kiểm thử trên thiết bị thật; chưa chạy được `assembleDebug`, `testDebugUnitTest`, `lintDebug`.

## Kiểm tra cử chỉ
Mục **Kiểm tra cử chỉ** trong cài đặt: khi bật, mọi cử chỉ hệ thống gửi tới ứng dụng chỉ được **đọc tên** (ví dụ
"Vuốt hai ngón lên"), không chạy chức năng đã gán. Cử chỉ nào không được đọc tên nghĩa là hệ thống không gửi tới
ứng dụng. Thoát bằng nút trên màn hình (chạm hai lần vẫn bấm được nút này) hoặc Back; tự tắt khi rời màn hình hoặc sau 30 giây không có cử chỉ. Không lưu lịch sử.

## Điều kiện hệ thống ảnh hưởng cử chỉ
- Cử chỉ nhiều ngón và cử chỉ hai bước chỉ hoạt động khi bật chế độ khám phá bằng cảm ứng và hệ thống gửi cử chỉ tới
  dịch vụ; một số hãng máy/phiên bản Android hoặc trình đọc màn hình khác đang bật có thể chiếm cử chỉ. Hãy dùng
  **Kiểm tra cử chỉ** để biết cử chỉ nào thật sự tới được ứng dụng trên máy của bạn.
- Cử chỉ vuốt từ cạnh màn hình (Back/Home của hệ thống) không bắt được.

## Cài đặt
1. Tải APK từ tab **Actions** (mục Artifacts) hoặc build bằng Android Studio.
   APK debug từ Actions được ký bằng khóa tạm của mỗi lần chạy, nên khi cài bản mới hãy **gỡ bản cũ trước**
   (nếu không sẽ báo "xung đột gói").
2. **Android 13 trở lên, APK cài ngoài Google Play:** hệ thống chặn bật dịch vụ hỗ trợ tiếp cận (công tắc bị mờ).
   Vào *Cài đặt → Ứng dụng → Đọc Màn Hình Việt Pro → ⋮ → Cho phép cài đặt bị hạn chế*, rồi làm bước 3.
3. Mở ứng dụng, chạm công tắc để vào Cài đặt Hỗ trợ tiếp cận và bật dịch vụ.
4. **Tắt TalkBack** trước khi dùng vì cả hai dùng chế độ khám phá bằng cảm ứng.
5. Cài giọng tiếng Việt (Google Text-to-Speech) nếu thiết bị chưa có.

## Script plugin
Script là JSON khai báo, không chứa mã thực thi. Xem mẫu trong `samples/close_popup_vn.json`.

```json
{
  "id": "ten_script",
  "name": "Tên hiển thị",
  "description": "Mô tả ngắn",
  "packages": [],
  "rules": [
    { "text": "Đóng", "match": "equals", "action": "click" }
  ]
}
```
- `match`: `contains` (mặc định) hoặc `equals`.
- Tiêu chí: `text`, `contentDescription`, `viewId`, `className` (mọi tiêu chí đã khai báo phải khớp).
- `action` mở rộng: `click`, `long_click`, `focus`, `scroll_forward`, `scroll_backward`.
- `message` (tùy chọn, tối đa 120 ký tự): câu đọc sau khi rule thực hiện thành công.
- `packages`: để trống nghĩa là chạy với mọi ứng dụng.
- `id`: chỉ gồm `a-z`, `0-9`, `_` (tối đa 40 ký tự) và **không được trùng** id tiện ích dựng sẵn (`builtin_skip_ad`).
- Giới hạn an toàn: tệp ≤ 64 KB, JSON lồng ≤ 8 cấp, tối đa 20 rule. So khớp chữ không phân biệt hoa/thường và
  không phân biệt dạng Unicode dựng sẵn/tổ hợp của tiếng Việt.

## Đọc theo đơn vị, sao chép, dán, cắt (v2.4.0)
- Đơn vị đọc: đoạn → câu → từ → ký tự. Mặc định: ba ngón vuốt xuống = đơn vị kế, ba ngón vuốt lên = đơn vị trước, vuốt lên rồi xuống = sao chép đơn vị đang đọc, hai ngón chạm ba lần = đổi đơn vị.
- Sao chép: nếu đang có vùng chọn trong ô nhập (kể cả chọn ngược) thì chỉ sao chép đoạn đã chọn.
- Thêm hành động: đưa con trỏ về đầu/cuối, chọn đến đầu/cuối. Ô mật khẩu chỉ cho phép dán; cắt, chọn, di chuyển con trỏ, sao chép đều bị chặn. Cắt và dán do hệ thống thực hiện, ứng dụng không đọc hay ghi clipboard.
- Dán, cắt, chọn tất cả và các mục trên có trong trình đơn con "Chỉnh sửa văn bản" của ô nhập; có thể gán cho cử chỉ bất kỳ trong mục Cử chỉ.
- Cuộn trang thử mọi vùng cuộn (vùng chứa phần tử đang chọn trước, rồi vùng lớn nhất) thay vì chỉ một vùng.

## Đa ngôn ngữ (v2.3.0)
- Giao diện và câu đọc bằng giọng nói có 7 ngôn ngữ: Tiếng Việt, English, Français, Español, Русский, العربية (RTL), 中文 — 6 ngôn ngữ chính thức của Liên hợp quốc và tiếng Việt. Có thêm biến thể: 中文 giản thể (zh-Hans) và phồn thể (zh-Hant, Đài Loan/Hồng Kông), English Mỹ (mặc định) và Anh (en-GB, chỉ khác chính tả). Bản phồn thể được chuyển từ giản thể bằng bảng ký tự tự lập, cần người bản ngữ rà soát. Mặc định (khi máy dùng ngôn ngữ khác) là English.
- Đổi ngôn ngữ: Cài đặt → Giọng nói (TTS) → "Chọn ngôn ngữ ứng dụng" (mở thẳng màn hình ngôn ngữ riêng của ứng dụng trong hệ thống). Giọng đọc đổi theo; cần cài giọng tương ứng trong công cụ TTS.
- Chuỗi nằm ở `app/src/main/res/values*/strings.xml`. Các tệp này, `core/S.kt`, `core/FallbackTexts.kt` và `i18n/ResourceTexts.kt` được SINH TỰ ĐỘNG từ bảng dịch trong `tools/i18n/` (xem `tools/README.md`, chạy `python3 tools/i18n/gen.py .`); khi thêm chuỗi phải cập nhật đủ 7 ngôn ngữ (unit test `TextsTest` kiểm tra đủ khóa và đúng tham số).
- Bản dịch do máy dịch, CHƯA được người bản ngữ rà soát. Từ điển mô tả hình ảnh và đoán nhãn từ tên view hiện chỉ có tiếng Việt (giao diện `LanguageDictionary`, thêm ngôn ngữ = thêm một lớp); ngôn ngữ khác đọc từ tiếng Anh gốc. Chuỗi có số đếm (số nhiều) dùng `plurals` đủ dạng theo từng ngôn ngữ (ru: one/few/many/other, ar: 6 dạng).
- Bố cục dùng kích thước tối thiểu (`heightIn(min)`) và chữ tự xuống dòng nên không cắt chữ; bản debug bật pseudo-locale (en-XA, ar-XB) để soát lỗi vỡ chữ/đảo chiều.

## Giới hạn của phần AI (nói thẳng)
- OCR chạy hoàn toàn trên máy bằng mô hình ML Kit **đóng gói sẵn**, chọn theo ngôn ngữ ứng dụng: **Latin** (Việt, Anh,
  Pháp, Tây Ban Nha...) và **Trung** (chữ Hán). **Nga (Cyrillic) và Ả Rập: ML Kit chưa có mô hình gói sẵn nên ứng dụng
  báo "chưa hỗ trợ" thay vì đọc ra kết quả sai.** Chữ Việt đọc được ở mức cơ bản, dấu thanh có thể sai với chữ nhỏ/mờ.
  Kết quả có độ tin cậy thấp được thêm "có thể sai". Mô hình Trung làm APK nặng thêm khoảng 4 MB mỗi kiến trúc CPU (theo
  tài liệu ML Kit; chưa đo trên bản build thật).
- **Ảnh xác minh/CAPTCHA:** OCR thông thường **không đáng tin** với chữ méo, nhiễu, gạch chéo, và ứng dụng **không có chức
  năng giải hay vượt xác minh**. Nếu gặp ảnh xác minh, hãy dùng lựa chọn âm thanh của dịch vụ đó, kênh hỗ trợ dành cho
  người khiếm thị của họ, hoặc nhờ người nhìn giúp.
- **Tự đọc chữ trong ảnh không nhãn** (Cài đặt → Đọc, **mặc định tắt**): chỉ khi bạn chọn một ảnh không có chữ/mô tả/nhãn,
  mỗi lần một tác vụ, hủy khi chọn phần tử khác, không đọc lại cùng phần tử trong cùng cửa sổ, bỏ qua ô mật khẩu và
  cửa sổ bảo mật, không ghi gì ra đĩa. Cách này chụp màn hình mỗi lần chọn nên tốn pin hơn.
- "Mô tả hình ảnh" gán nhãn (khoảng 400 loại, dịch sang tiếng Việt bằng từ điển), không viết thành câu văn như AI sinh văn bản.
  Mô hình sinh mô tả của Google (GenAI) hiện chỉ có tiếng Anh, cần Gemini Nano và tải qua mạng nên chưa dùng.
- Chụp màn hình bị Android chặn ở màn hình bảo mật (mật khẩu, ngân hàng); khi đó ứng dụng sẽ báo thay vì đọc.
- Khi chụp màn hình, khung viền xanh được ẩn tạm (chờ 2 khung hình) rồi hiện lại, kể cả khi lỗi hoặc bị hủy.
- Phần chụp màn hình/ML Kit mới chỉ được biên dịch trên GitHub Actions, chưa thử trên máy thật.

## Quyết định thiết kế
- **Không đăng ký `typeWindowContentChanged` / `typeViewScrolled`:** hai loại sự kiện này phát dồn dập (mỗi lần cuộn, mỗi
  khung hoạt ảnh) gây bão sự kiện, tốn pin và làm trễ giọng đọc. Ứng dụng chỉ lấy lại cây giao diện khi cần (chọn, cuộn
  bằng cử chỉ, đổi cửa sổ) và làm mới nút (`refresh()`) trước khi dùng; nút đã cũ thì đọc "Phần tử đã thay đổi".
- **Giới hạn duyệt cây:** tối đa 1500 phần tử nhìn thấy (trang dài vẫn đọc được) và trần cứng 6000 nút để tránh treo.
- **Script không khai báo `packages`** chạy ở mọi ứng dụng: khi nạp, ứng dụng hỏi xác nhận trước khi cài.
- **Không dịch vụ nền (foreground service):** dịch vụ trợ năng đã được hệ thống giữ chạy.

## Quyền (manifest) và Google Play
| Quyền / khai báo | Lý do |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` (dịch vụ) | Đọc và điều khiển giao diện để làm trình đọc màn hình |
| `canTakeScreenshot` (cấu hình dịch vụ) | OCR/mô tả ảnh; ảnh chỉ nằm trong RAM, giải phóng ngay |
| `VIBRATE` | Rung phản hồi khi chuyển phần tử |
| `<queries>`: TTS_SERVICE, MAIN/LAUNCHER | Liệt kê công cụ TTS; tra tên ứng dụng khi đọc thông báo (không dùng `QUERY_ALL_PACKAGES`) |
| Không có INTERNET / ACCESS_NETWORK_STATE | Mọi xử lý trên máy, không gửi dữ liệu đi đâu |

Khi đưa lên Google Play: chỉ khai báo các quyền trên, khai báo mục đích dịch vụ hỗ trợ tiếp cận (Accessibility API
declaration) và chính sách quyền riêng tư nêu rõ **không thu thập, không gửi dữ liệu ra ngoài thiết bị**.

## Rà soát bố cục (5.5) — đã đọc mã, CHƯA xem trên máy
Kiểm theo mã nguồn (không có thiết bị/emulator ở môi trường làm việc):
| Màn hình | Cuộn dọc | Ghi chú |
|---|---|---|
| Danh sách mục + Trạng thái, Giọng nói, Rung/âm báo, Đọc, Nhãn, Trình đơn, Kiểm tra cử chỉ, Trợ giúp | Có (`ScreenColumn`) | chữ tự xuống dòng, hàng công tắc cao tối thiểu 80dp |
| Cử chỉ, Tiện ích | Có (`LazyColumn`) | hộp chọn chức năng cuộn được |
| Gắn nhãn, Tìm chữ (Activity riêng) | **Đã thêm** `verticalScroll` + `imePadding` | trước đó cột cố định, bàn phím/ chữ lớn/xoay ngang có thể che nút |
| Hộp thoại xác nhận (xóa tiện ích, script mọi ứng dụng) | **Đã thêm** cuộn | chữ dài ở ngôn ngữ khác không bị cắt |
| Màn hình rộng/xoay ngang | Danh sách trái + nội dung phải, mỗi bên cuộn riêng | ngưỡng 600dp |
| RTL (ar, ar-XB) | Dùng `start/end` của Compose | cần xem thật |
Việc còn lại: chạy bản debug với en-XA/ar-XB, cỡ chữ lớn nhất, màn hình nhỏ/ngang/lớn.

## Build
- Android Studio (bản hỗ trợ AGP 9.4): mở thư mục dự án, chờ sync, chọn *Build → Build APK(s)*.
- Dòng lệnh: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  (Gradle wrapper đã ghim Gradle 9.8.2 kèm checksum SHA-256).
- GitHub Actions: workflow `.github/workflows/build.yml` tự chạy test, lint và build APK mỗi lần push.
- **Bản release đã ký:** khóa KHÔNG nằm trong repo. `app/build.gradle.kts` chỉ ký khi đủ 4 biến môi trường
  `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` (thiếu thì ra APK
  chưa ký). Trên GitHub: tạo keystore (`keytool -genkeypair -v -keystore release.jks -alias key -keyalg RSA -keysize 4096 -validity 10000`),
  mã hóa `base64 -w0 release.jks`, lưu vào Secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
  `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, rồi chạy tay workflow **Build release** (`release.yml`) để lấy APK/AAB đã ký.
  Giữ kỹ keystore: mất khóa là không cập nhật được ứng dụng.

## Công nghệ (cập nhật 10/2026)
| Thành phần | Phiên bản |
|---|---|
| Android Gradle Plugin | 9.4.0 (Kotlin tích hợp sẵn, không còn plugin `kotlin-android`) |
| Gradle | 9.8.2 |
| Kotlin / Compose compiler plugin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 35 (Android 15+) |
| JDK | 17 trở lên (CI dùng 21) |

Phiên bản phụ thuộc nằm tập trung tại `gradle/libs.versions.toml`.

## Yêu cầu
- Android 15 (API 35) trở lên, JDK 17 trở lên.
