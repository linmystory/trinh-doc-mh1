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
- 33 cử chỉ (1 ngón: vuốt 4 hướng, 12 cử chỉ hai bước, chạm hai lần; 2, 3, 4 ngón), gán chức năng tùy ý.
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
| Vuốt lên / xuống | Cuộn xuống / cuộn lên |
| Lên rồi phải | **Trình đơn chung** |
| Xuống rồi phải | **Trình đơn của phần tử đang chọn** |
| Xuống rồi trái | Cài đặt nhanh |
| Lên rồi trái | Quay lại |
| Phải rồi xuống | Mở thanh thông báo |
| Trái rồi xuống | Màn hình chính |
| Phải rồi trái | Ứng dụng gần đây |
| Trái rồi phải | Dừng đọc |
| Xuống rồi lên | Chạy các tiện ích đã bật |
| Hai ngón chạm / chạm hai lần | Dừng đọc / lặp lại câu vừa đọc |
| Hai ngón vuốt lên, xuống, trái, phải | Cuộn xuống, cuộn lên, quay lại, màn hình chính |
| Ba ngón chạm / chạm hai lần | Trình đơn chung / trình đơn phần tử |
| Ba ngón vuốt lên / xuống | Đọc từ đầu màn hình / đọc từ phần tử đang chọn |
| Ba ngón vuốt trái / phải | Thông báo / cài đặt nhanh |
| Bốn ngón chạm / chạm hai lần | Đổi chế độ rung và âm báo / cho biết giờ và pin |

Tất cả đều đổi được trong mục **Cử chỉ**. Android không cho dịch vụ hỗ trợ tiếp cận bắt cử chỉ vuốt từ cạnh màn hình.

## Trình đơn nhanh
- **Chung** (13 mục): đọc từ đầu, đọc từ phần tử hiện tại, lặp lại, tìm chữ, OCR, đổi chế độ rung/âm báo, giờ và pin,
  thông báo, cài đặt nhanh, màn hình chính, ứng dụng gần đây, chạy tiện ích, cài đặt ứng dụng.
- **Phần tử**: nhấn, nhấn giữ, sao chép chữ, đọc từng ký tự, gắn/sửa nhãn, mô tả hình ảnh, cộng các hành động
  tùy chỉnh do ứng dụng khai báo cho phần tử đó.
- Khi trình đơn mở: vuốt phải/trái chọn mục, chạm hai lần thực hiện, quay lại hoặc dừng đọc để đóng.

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

## Đa ngôn ngữ (v2.3.0)
- Giao diện và câu đọc bằng giọng nói có 7 ngôn ngữ: Tiếng Việt, English, Français, Español, Русский, العربية (RTL), 中文 — 6 ngôn ngữ chính thức của Liên hợp quốc và tiếng Việt. Mặc định (khi máy dùng ngôn ngữ khác) là English.
- Đổi ngôn ngữ: Cài đặt → Giọng nói (TTS) → "Chọn ngôn ngữ ứng dụng" (mở thẳng màn hình ngôn ngữ riêng của ứng dụng trong hệ thống). Giọng đọc đổi theo; cần cài giọng tương ứng trong công cụ TTS.
- Chuỗi nằm ở `app/src/main/res/values*/strings.xml`. Các tệp này, `core/S.kt`, `core/FallbackTexts.kt` và `i18n/ResourceTexts.kt` được SINH TỰ ĐỘNG từ một bảng dịch; khi thêm chuỗi phải cập nhật đủ 7 ngôn ngữ (unit test `TextsTest` kiểm tra đủ khóa và đúng tham số).
- Bản dịch do máy dịch, CHƯA được người bản ngữ rà soát. Từ điển mô tả hình ảnh và đoán nhãn từ tên view chỉ có tiếng Việt; ngôn ngữ khác đọc từ tiếng Anh gốc. OCR chỉ nhận chữ Latin.
- Bố cục dùng kích thước tối thiểu (`heightIn(min)`) và chữ tự xuống dòng nên không cắt chữ; bản debug bật pseudo-locale (en-XA, ar-XB) để soát lỗi vỡ chữ/đảo chiều.

## Giới hạn của phần AI (nói thẳng)
- OCR dùng bộ nhận dạng Latin của ML Kit: đọc chữ Việt ở mức cơ bản, dấu thanh có thể sai với chữ nhỏ/mờ.
- "Mô tả hình ảnh" gán nhãn (khoảng 400 loại, dịch sang tiếng Việt bằng từ điển), không viết thành câu văn như AI sinh văn bản.
  Mô hình sinh mô tả của Google (GenAI) hiện chỉ có tiếng Anh, cần Gemini Nano và tải qua mạng nên chưa dùng.
- Chụp màn hình bị Android chặn ở màn hình bảo mật (mật khẩu, ngân hàng); khi đó ứng dụng sẽ báo thay vì đọc.
- Phần chụp màn hình/ML Kit mới chỉ được biên dịch trên GitHub Actions, chưa thử trên máy thật.

## Build
- Android Studio (bản hỗ trợ AGP 9.4): mở thư mục dự án, chờ sync, chọn *Build → Build APK(s)*.
- Dòng lệnh: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  (Gradle wrapper đã ghim Gradle 9.8.2 kèm checksum SHA-256).
- GitHub Actions: workflow `.github/workflows/build.yml` tự chạy test, lint và build APK mỗi lần push.

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
