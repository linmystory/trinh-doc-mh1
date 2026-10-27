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
- 17 cử chỉ (vuốt 4 hướng, 12 cử chỉ hai bước, chạm hai lần), gán chức năng tùy ý.
- Hệ thống tiện ích: plugin dựng sẵn và script JSON nạp từ tệp.
- Giao diện độ tương phản cao (đen/vàng).

## Cử chỉ mặc định
| Cử chỉ | Chức năng |
|---|---|
| Vuốt phải / trái | Phần tử kế tiếp / trước đó |
| Chạm hai lần | Nhấn phần tử đang chọn |
| Vuốt lên / xuống | Cuộn xuống / cuộn lên |
| Xuống rồi phải | Mở thanh thông báo |
| Xuống rồi trái | Cài đặt nhanh |
| Lên rồi trái | Quay lại |
| Lên rồi phải | Màn hình chính |
| Phải rồi trái | Ứng dụng gần đây |
| Trái rồi phải | Dừng đọc |
| Xuống rồi lên | Chạy các tiện ích đã bật |

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
- `action`: `click` (mặc định) hoặc `focus`.
- Tiêu chí: `text`, `contentDescription`, `viewId` (mọi tiêu chí đã khai báo phải khớp).
- `packages`: để trống nghĩa là chạy với mọi ứng dụng.
- `id`: chỉ gồm `a-z`, `0-9`, `_` (tối đa 40 ký tự) và **không được trùng** id tiện ích dựng sẵn (`builtin_skip_ad`).
- Giới hạn an toàn: tệp ≤ 64 KB, JSON lồng ≤ 8 cấp, tối đa 20 rule. So khớp chữ không phân biệt hoa/thường và
  không phân biệt dạng Unicode dựng sẵn/tổ hợp của tiếng Việt.

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
| compileSdk / targetSdk / minSdk | 37 / 36 / 30 |
| JDK | 17 trở lên (CI dùng 21) |

Phiên bản phụ thuộc nằm tập trung tại `gradle/libs.versions.toml`.

## Yêu cầu
- Android 11 (API 30) trở lên, JDK 17 trở lên.
