# Công cụ sinh chuỗi đa ngôn ngữ

Nguồn chuỗi nằm ở `tools/i18n/table_*.py` (mỗi dòng: khóa, en, vi, fr, es, ru, ar, zh) và `table_plurals.py`
(chuỗi có số đếm, đủ dạng số nhiều theo từng ngôn ngữ). KHÔNG sửa tay các tệp sinh ra:

- `app/src/main/res/values*/strings.xml` (gồm `values-en-rGB` và `values-b+zh+Hant`)
- `core/S.kt`, `core/FallbackTexts.kt` (tiếng Việt dự phòng cho unit test), `i18n/ResourceTexts.kt`

## Chạy

```bash
# tại thư mục gốc của repo (cần Python 3.9+, không cần thư viện ngoài)
python3 tools/i18n/gen.py .
```

Bộ sinh tự kiểm: đủ 7 ngôn ngữ, cùng placeholder (`%1$s`, `%2$d`...) ở mọi ngôn ngữ, không có khoảng trắng đầu/cuối
(aapt cắt bỏ), đủ dạng số nhiều từng ngôn ngữ. Bản tiếng Trung phồn thể (`zh_hant.py`) và Anh-Anh được sinh bằng bảng
chuyển đổi riêng; bản dịch là dịch máy, cần người bản ngữ rà soát.

## Thêm / sửa chuỗi

1. Thêm dòng vào `table_d.py` (hoặc sửa dòng sẵn có). Chuỗi cần số nhiều: thêm vào `table_plurals.py`.
2. Chạy lệnh trên, rồi `./gradlew :app:testDebugUnitTest`.
3. Các test `TextsTest`, `FallbackSyncTest` sẽ đỏ nếu quên chạy bộ sinh hoặc thiếu khóa/dạng số nhiều.
