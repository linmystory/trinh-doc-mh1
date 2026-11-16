# Đọc Màn Hình Việt Pro

Trình đọc màn hình (screen reader) cho Android dành cho người khiếm thị, viết bằng Kotlin và Jetpack Compose.

## Tính năng
- Đọc nội dung bằng giọng tiếng Việt (Android TextToSpeech), ngắt lời tức thì, hàng đợi thông minh.
  Văn bản dài được tự chia nhỏ; nếu TTS chưa sẵn sàng lúc khởi động máy thì tự thử lại.
- Ngắt câu theo ngữ pháp tiếng Việt: không cắt sau từ treo (của, và, là...), không tách số/giờ/viết tắt.
- Đọc thêm trạng thái: vô hiệu hóa, đã chọn, tiêu đề, thanh trượt (%), lỗi nhập, vai trò do app khai báo.
- Xử lý sự kiện trên luồng nền (coroutines), lọc sự kiện trùng để giảm độ trễ.
- Khung viền xanh lá quanh phần tử đang chọn.
- Khám phá bằng cảm ứng: đặt ngón tay lên phần tử nào thì đọc phần tử đó; chạm hai lần (ở bất kỳ đâu) để nhấn
  phần tử vừa nghe.
- 45 cử chỉ chạm (1 ngón: vuốt 4 hướng, 12 cử chỉ hai bước, chạm hai lần, chạm hai lần và giữ; 2, 3, 4 ngón) và 8 cử chỉ phím cứng (phím âm lượng, nút tai nghe), gán chức năng tùy ý. Riêng bốn cú **hai ngón vuốt** do Android xử lý như ở các trình đọc màn hình lớn nên không gán lại được (xem bản 2.18.1).
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
| Vuốt xuống / lên | Tiến / lùi theo **điều khiển đọc** đang chọn (ban đầu: từng phần tử, như vuốt phải / trái). Trong **ô nhập**: dời **con trỏ thật** theo đơn vị của điều khiển (ban đầu: từng ký tự) và đọc đoạn chữ vừa đi qua. Đang chọn thanh trượt thì xuống = giảm, lên = tăng (xem bản 2.19.0 và 2.20.0) |
| Xuống rồi phải | **Trình đơn chung** |
| Lên rồi phải | **Trình đơn của phần tử đang chọn** |
| Lên rồi trái | Màn hình chính |
| Xuống rồi trái | Quay lại |
| Trái rồi lên | Ứng dụng gần đây |
| Phải rồi xuống | Thông báo |
| Trái rồi xuống | Tìm chữ trên màn hình |
| Lên rồi xuống | Điều khiển đọc **kế** (phần tử → ký tự → từ → câu → đoạn → tiêu đề → điều khiển → liên kết → tốc độ đọc → phần tử...) |
| Xuống rồi lên | Điều khiển đọc **trước** |
| Hai ngón chạm | Dừng đọc |
| Hai ngón chạm hai lần | Phát / tạm dừng nhạc hoặc video (phím media chuẩn) |
| Hai ngón chạm ba lần | Đọc liên tục từ phần tử đang chọn |
| Hai ngón vuốt lên / xuống | **Do Android xử lý như một ngón vuốt**: cuộn nội dung tại chỗ; khi dùng điều hướng bằng cử chỉ thì vuốt lên từ mép dưới = Màn hình chính (giữ = Ứng dụng gần đây), vuốt xuống từ mép trên = Thông báo |
| Hai ngón vuốt trái / phải | **Do Android xử lý**: chuyển trang ngang tại chỗ; từ mép trái/phải vào = Quay lại |
| Ba ngón chạm | Trình đơn chung |
| Ba ngón chạm hai lần / ba lần | Sao chép chữ của phần tử đang chọn / dán vào ô nhập |
| Ba ngón chạm hai lần và giữ | Cắt chữ ở ô nhập |
| Ba ngón vuốt xuống / lên | Điều khiển đọc **kế** / **trước** (cùng việc với lên-rồi-xuống / xuống-rồi-lên) |
| Ba ngón vuốt trái / phải | **Trang kế / trang trước** (chuyển trang ngang: ViewPager, tab vuốt ngang) |
| Bốn ngón chạm / chạm hai lần | Đổi chế độ rung và âm báo / cho biết giờ và pin |

**Từ bản 2.20.0 các cử chỉ hai bước và nhiều ngón theo bảng cử chỉ chuẩn của Bộ trợ năng Android** (xem phần "Bản 2.20.0" bên dưới: bảng so sánh, các hành động bị nhường chỗ và cách lấy lại). Mặc định mới chỉ tới những cử chỉ bạn **chưa đổi**: từ bản 2.6.0 cấu hình chỉ lưu những cử chỉ bạn thật sự đổi, nên cử chỉ đã đổi giữ nguyên và các cử chỉ còn lại nhận mặc định mới khi cập nhật; cấu hình lưu từ trước bản 2.6.0 (ghi đủ mọi cử chỉ) giữ nguyên hoàn toàn. Đổi cử chỉ làm mất hết cử chỉ của "phần tử kế", "phần tử trước" hay "nhấn" sẽ có cảnh báo. Tất cả đều đổi được trong mục **Cử chỉ**, nơi cũng có nút khôi phục mặc định. Android không cho dịch vụ hỗ trợ tiếp cận bắt cử chỉ vuốt từ cạnh màn hình.

## Trình đơn nhanh
Cử chỉ mở trình đơn hiển thị ở mục **Trình đơn** trong ứng dụng, lấy từ cấu hình thật của bạn.
- **Chung** (7 mục + "Khác"): đọc từ đầu, đọc từ phần tử hiện tại, lặp lại, tìm chữ, đọc chữ trên màn hình (OCR),
  thông báo, màn hình chính. Trình đơn con **Khác**: cài đặt nhanh, ứng dụng gần đây, đổi chế độ rung/âm báo,
  giờ và pin, phát/tạm dừng nhạc, chạy tiện ích, cài đặt ứng dụng, **Quay lại**.
- **Phần tử**: nhấn, nhấn giữ, đơn vị kế/trước, đổi đơn vị đọc, sao chép đơn vị, sao chép, đọc từng ký tự,
  gắn nhãn, mô tả hình ảnh, đọc chữ trong ảnh, cộng các hành động tùy chỉnh của ứng dụng. Riêng **ô nhập liệu**
  có thêm trình đơn con **Chỉnh sửa văn bản**: dán, cắt, chọn tất cả, đưa con trỏ về đầu/cuối, chọn đến đầu/cuối, **Quay lại**.
- Khi trình đơn mở: vuốt phải/trái (hoặc xuống/lên) chọn mục, chạm hai lần thực hiện, quay lại để lùi một cấp. Mở trình đơn là vào thẳng
  mục đầu tiên (không đọc tên và số mục trước); cử chỉ không dùng được trong trình đơn chỉ nhắc ngắn một lần.
- Cài đặt **Đọc hướng dẫn thao tác** (Đọc, mặc định bật): tắt thì trình đơn chỉ đọc tên mục, không đọc vị trí và lời nhắc.

## Bản 2.20.4: vuốt một ngón phải/trái tự cuộn tiếp khi hết màn hình

Lỗi được báo: vuốt một ngón **phải** (phần tử kế) / **trái** (phần tử trước) chỉ đi hết các phần tử đang hiện trên màn hình rồi dừng, không tự cuộn để đi tiếp dù phía dưới (hoặc phía trên) vẫn còn nội dung. Sửa dựa trên phân tích mã nguồn, **chưa thử được trên máy thật** (xem "Giới hạn" bên dưới):

- **Cuộn trước khi rời danh sách**: khi phần tử kế nằm NGOÀI vùng cuộn đang đứng (thanh dưới, nút nổi, tiêu đề cố định...) mà vùng đó còn cuộn được theo hướng vuốt, ứng dụng cuộn vùng đó trước rồi mới sang phần tử ngoài vùng. Trước đây chỉ cuộn khi cả cửa sổ đã hết phần tử, nên thanh dưới luôn bị tới trước và phần còn lại của danh sách bị bỏ qua.
- **Chờ nội dung dừng hẳn** thay vì chờ cố định 0,3 + 0,5 giây: thăm dò mỗi 0,12 giây (tối đa 1,5 giây mỗi lần cuộn), xong khi hai lần thăm dò liên tiếp cho cùng một danh sách khác trước khi cuộn. Danh sách trượt có hiệu ứng (~0,6 giây) và trang web cập nhật chậm không còn bị chọn nhầm lúc nội dung chưa kịp đổi.
- **Chọn phần tử mới theo vị trí + nhãn** (không chỉ nhãn): đo quãng nội dung đã trượt từ các phần tử giống nhau trước/sau, rồi chỉ coi là "đã thấy" những phần tử trùng nhãn VÀ đúng chỗ dự kiến. Nhờ vậy: mục trùng chữ (nút "Thích" ở mỗi bài, giá tiền) không bị bỏ sót; thanh dính đứng yên không bị chọn; đoạn văn dài bị cắt ở mép không bị đọc lại.
- **Hàng kiểu thẻ** (khung bấm được, chữ nằm ở phần tử con) trước đây coi là không nhãn nên không nhận ra hàng nào đã đọc; nay dùng chữ gộp của các con làm nhãn so khớp.
- Nếu một lần cuộn chưa lộ ra phần tử có nhãn nào (ví dụ chỉ thêm ảnh) thì cuộn tiếp, tối đa 3 lần.
- **Không cuộn nhầm**: chỉ cuộn khung nội dung dọc. Bộ chọn giá trị (NumberPicker, TimePicker, thanh trượt: cuộn là đổi giá trị) và băng chuyền/bộ chuyển trang chỉ cuộn ngang bị bỏ qua. Các lệnh cuộn do người dùng chủ động (hai ngón vuốt, cử chỉ "Cuộn") giữ nguyên.
- **Nhớ vùng đã hết** 5 giây: cuộn xong mà không có gì mới thì các lần vuốt kế đi thẳng sang phần tử kế, không chờ vô ích. Cuộn tay hoặc đổi màn hình thì xóa nhớ.
- Nhật ký chẩn đoán (Cài đặt → Đọc → "Sao chép nhật ký") có thêm dòng thẻ `AUTOSCROLL` ghi số liệu mỗi lần tự cuộn (số phần tử trước/sau, quãng trượt, số phần tử đã thấy, kết quả). **Không ghi nội dung chữ trên màn hình.** Nếu vẫn lỗi, gửi các dòng này kèm tên ứng dụng và màn hình đang dùng.
- Kiểm thử: 672 bài JVM đạt (34 bài mới cho phần chọn phần tử sau cuộn, nhận biết ổn định, nhớ vùng đã hết). Phần gọi hệ thống (đọc cây giao diện, thực hiện cuộn) **không kiểm thử được ngoài máy thật**.
- Giới hạn đã biết: danh sách toàn mục GIỐNG HỆT nhau (cùng chữ, cùng hộp) không đo được quãng trượt; tiêu đề dính ĐỔI chữ khi cuộn (tên mục hiện hành) vẫn bị coi là phần tử mới; ứng dụng cập nhật giao diện chậm hơn 1,5 giây sau khi cuộn sẽ bị coi là "chưa đổi"; khung báo cuộn được mà không phản hồi hành động cuộn thì vẫn dùng cú vuốt giả lập như trước (chỉ khi đã hết phần tử của cả cửa sổ).

## Bản 2.20.3: sửa 4 lỗi lint do CI báo

Báo cáo CI của bản 2.20.2: bản dựng đạt, **638/638 bài kiểm thử đạt**, nhưng `lintDebug` báo **4 lỗi**, 5 cảnh báo, 2 gợi ý (4 lỗi làm bước lint đỏ). **Không đổi hành vi ứng dụng**:

- **4 lỗi `LocalContextGetResourceValueCall`** (màn **Sao lưu**): các thông báo kết quả ("đã lưu", "đã khôi phục", "không đọc/ghi được tệp") lấy chuỗi bằng `LocalContext.current.getString()`, không đổi theo ngôn ngữ/cấu hình nếu cấu hình đổi lúc màn đang mở. Nay lấy qua `LocalResources.current` (đúng cách Compose khuyến nghị).
- Cảnh báo `LocalContextResourcesRead` (màn **Nhãn**): `context.resources` → `LocalResources.current`.
- Gợi ý `AutoboxingStateCreation` (màn **Đọc**): hai ô nhớ số dùng `mutableIntStateOf` / `mutableFloatStateOf` (không đóng gói số mỗi lần ghi).
- Cảnh báo `UseKtx`: hàm khôi phục cài đặt (`SettingsBackupIO.apply`) dùng `SharedPreferences.edit { }`; cùng hành vi (gom thay đổi từng tệp rồi `apply()` một lần).
- Cảnh báo `ObsoleteSdkInt`: thư mục biểu tượng `mipmap-anydpi-v26` đổi thành `mipmap-anydpi` (minSdk 35 nên hậu tố `-v26` là thừa). Nội dung hai tệp giữ nguyên. Nếu kho GitHub của bạn còn thư mục cũ thì có thể xóa; để lại không gây hại (cùng nội dung), chỉ còn đúng cảnh báo đó.
- **Android Gradle Plugin 9.5.0 → 9.5.1** (bản vá; lint báo có bản mới; trang phát hành chính thức ghi 9.5.1 không có lỗi công khai nào được sửa, yêu cầu tối thiểu như 9.5.0).
- Cố ý **giữ** cảnh báo `OldTargetApi` (`targetSdk = 36`): Google Play yêu cầu API 36; nâng 37 cần thử hành vi mới của Android 17 trên máy thật trước.
- Kiểm thử: 638 bài JVM vẫn đạt. Compose và lint **không chạy được** ở môi trường soạn thảo, nên các sửa ở `ui/` mới được đối chiếu với tài liệu API (`LocalResources: ProvidableCompositionLocal<Resources>`), chưa biên dịch; lần chạy CI kế tiếp là bước kiểm chứng thật.

## Bản 2.20.2: nâng công cụ build

Chỉ đổi cấu hình build, **không đổi mã ứng dụng** (638 bài kiểm thử JVM vẫn đạt; CI đã xác nhận bản dựng và 638/638 bài đạt với AGP 9.5.0 + Gradle 9.9.1):

- **Android Gradle Plugin 9.4.0 → 9.5.0** (ổn định từ 10/2026; yêu cầu tối thiểu Gradle 9.6, JDK 17, compileSdk tối đa 37: dự án đã thỏa).
- **Gradle 9.8.2 → 9.9.1**, giữ ghim checksum SHA-256 của bản phân phối `gradle-9.9.1-bin.zip`. 9.9.1 là bản vá bảo mật (ra 10/11/2026) theo ghi chú phát hành của Gradle.
- Chưa chạy Gradle ở môi trường soạn thảo nên chưa chứng minh được bản dựng; nếu CI báo lỗi, quay lại v51 hoặc đổi riêng từng dòng (`agp` trong `gradle/libs.versions.toml`, `distributionUrl` + `distributionSha256Sum` trong `gradle/wrapper/gradle-wrapper.properties`).

## Bản 2.20.1: rà soát toàn diện, sửa 3 lỗi

Rà lại toàn bộ mã dịch vụ, giọng đọc, AI/OCR, plugin, lưu trữ/sao lưu, nhật ký lỗi và phần giao diện đã đổi từ 2.18 (Compose không biên dịch được ở môi trường soạn thảo nên chỉ đọc mã). Chỉ sửa lỗi có bằng chứng trong mã:

- **Vuốt xuống/lên bị kẹt ở thanh tiến trình chỉ-đọc** (tải xuống, mức pin, vòng tải): trước đây phần tử nào có khoảng giá trị cũng bị coi là thanh trượt, nên vuốt một ngón xuống/lên chỉ đọc lại giá trị mà không sang phần tử khác (và cuộn trang cũng bị nuốt). Nay chỉ thanh **đang bật và có hành động chỉnh** (`ACTION_SET_PROGRESS` hoặc cuộn tới/lui) mới được chỉnh bằng cử chỉ (`SliderMath.isAdjustable`).
- **"Đưa con trỏ về đầu/cuối", "chọn tất cả" báo "không thực hiện được" khi đã ở đúng vị trí** (TextView trả false cho `ACTION_SET_SELECTION` nếu vùng chọn không đổi). Nay nhận ra trường hợp đã đúng sẵn (`SelectionMath.isNoOp`) và báo xong. Độ dài chữ lấy theo chữ THẬT, không tính gợi ý (hint) của ô trống; không gửi lại `ACTION_FOCUS` cho ô đang giữ tiêu điểm (như khi dời con trỏ), tránh ứng dụng chọn lại toàn bộ chữ rồi "dán" ghi đè cả ô.
- **Duyệt theo từ không tách dấu cách không ngắt** (`&nbsp;` U+00A0, U+202F, U+3000... hay gặp trên trang web): "Hà&nbsp;Nội" từng là một từ và cụm chỉ gồm &nbsp; thành một "từ" rỗng. Nay mọi khoảng trắng Unicode đều tách từ, giống đơn vị câu/đoạn.
- Kiểm thử: 638 bài JVM (thêm 3 bài hồi quy ở `SliderMathTest`, `SubmenuAndEditTest`, `TextUnitsTest`). **Chưa** chạy Gradle/Compose/lint và chưa thử trên máy thật. Lỗi thứ hai dựa trên hành vi TextView mà tôi nhớ từ mã nguồn Android (chưa kiểm chứng trên máy): phần kiểm tra "đã đúng vị trí" là logic thuần nên vô hại kể cả khi nhớ sai.

## Bản 2.20.0: cử chỉ theo bảng chuẩn mới nhất của Bộ trợ năng Android

Đối chiếu với bảng cử chỉ và danh sách "điều khiển đọc" trong tài liệu chính thức của Google (bản mới nhất, hai lần trích độc lập cho kết quả giống nhau) rồi đưa mặc định về đúng chuẩn. Mọi cử chỉ vẫn gán lại được trong mục **Cử chỉ**.

- **Mặc định đổi** (`GestureMapping.defaults`):

  | Cử chỉ | Bản 2.20.0 | Trước đó |
  |---|---|---|
  | Lên rồi trái | Màn hình chính | Quay lại |
  | Xuống rồi trái | Quay lại | Màn hình chính |
  | Trái rồi lên | Ứng dụng gần đây | (trống) |
  | Phải rồi xuống | Thông báo | (trống) |
  | Trái rồi xuống | Tìm chữ trên màn hình | (trống) |
  | Hai ngón chạm hai lần | Phát/tạm dừng nhạc hoặc video | Đọc lại câu vừa đọc |
  | Hai ngón chạm ba lần | Đọc từ phần tử đang chọn | Đổi đơn vị đọc |
  | Ba ngón chạm hai lần | Sao chép chữ của phần tử | Trình đơn phần tử |
  | Ba ngón chạm ba lần | Dán vào ô nhập | Đọc từ đầu màn hình |
  | Ba ngón chạm hai lần và giữ | Cắt chữ ở ô nhập | (trống) |
  | Ba ngón vuốt xuống / lên | Điều khiển đọc kế / trước | Đơn vị đọc kế / trước |

- **Hành động bị nhường chỗ** (không còn cử chỉ mặc định nhưng không mất): *Đọc lại câu vừa đọc* và *Đọc từ đầu* (trình đơn chung), *Đổi đơn vị đọc* và *Đơn vị kế/trước* (trình đơn phần tử). Trình đơn phần tử vẫn mở bằng lên-rồi-phải. Muốn ba ngón vuốt như cũ: mục Cử chỉ → bộ ba ngón vuốt → "Đọc theo đơn vị và chuyển trang". Muốn lấy lại các hành động khác: gán cho một cử chỉ bất kỳ trong mục Cử chỉ (ví dụ hai ngón chạm hai lần = *Đọc lại*). Ai đã lưu cử chỉ riêng thì các cử chỉ đó giữ nguyên.
- **Điều khiển đọc theo danh sách mới:** phần tử → ký tự → từ → câu → đoạn → tiêu đề → điều khiển → liên kết → tốc độ đọc → phần tử (`ReadingControl`). Trước: phần tử → tiêu đề → liên kết → đoạn → câu → từ → ký tự. Mới: **Điều khiển** (nhảy tới nút, ô nhập, công tắc... kế/trước, dùng lại chế độ đọc có sẵn qua `FocusNavigator.moveToMode`, không đổi chế độ đọc của vuốt phải/trái) và **Tốc độ đọc** (vuốt lên = nhanh hơn, xuống = chậm hơn, mỗi bước 10% trong khoảng 50%–200% của thanh trượt ở Cài đặt giọng đọc, áp ngay và đọc lại tốc độ mới; `SpeechRate`). "Phần tử" (mặc định) và "câu" là hai mục ứng dụng thêm so với bảng chuẩn.
- **Ô nhập dời con trỏ thật:** vuốt xuống/lên khi đang ở ô nhập (không phải mật khẩu) dời con trỏ bằng `ACTION_SET_SELECTION` theo đơn vị của điều khiển (với "Phần tử": từng ký tự; với ký tự/từ/câu/đoạn: đúng đơn vị đó) và đọc đoạn chữ con trỏ vừa đi qua; ô trống hoặc đã tới đầu/cuối chữ thì lần vuốt kế sang phần tử kế/trước để không kẹt trong ô. Ô mật khẩu: không dời con trỏ, không đọc chữ, chỉ sang phần tử kế/trước. Phép tính nằm ở `CaretMath` (hàm thuần: tiến tới CUỐI đơn vị kế, lùi tới ĐẦU đơn vị trước, ký tự theo cụm ký tự người dùng thấy nên không cắt dấu thanh rời hay emoji); `FocusNavigator.moveCaret` thực thi và ghi nhận `lastOwnSelectionMs` để sự kiện "vùng chọn đổi" dội lại không bị đọc thêm. Không gửi lại `ACTION_FOCUS` khi ô đã giữ tiêu điểm nhập.
- **Phát/tạm dừng nhạc:** hành động mới `MEDIA_PLAY_PAUSE` gửi cặp phím `KEYCODE_MEDIA_PLAY_PAUSE` bằng `AudioManager.dispatchMediaKeyEvent` (không cần quyền; không có trình phát thì không có gì xảy ra). Có trong trình đơn chung → Khác.
- **Kiểu ba ngón vuốt:** thêm "Đổi điều khiển đọc và chuyển trang" (mặc định mới, `SwipeProfile.READING_CONTROLS`); "Đọc theo đơn vị và chuyển trang" thành lựa chọn thường.
- **Chuỗi:** năm chuỗi mới (tên hành động nhạc, câu báo tốc độ đọc, tên kiểu ba ngón vuốt, tiêu đề và nội dung mục "Cử chỉ mặc định" trong Trợ giúp) và hai chuỗi sửa (nội dung mục "Điều khiển đọc" trong Trợ giúp, bỏ "(mặc định)" khỏi kiểu đơn vị). Đủ 7 ngôn ngữ, bản dịch do máy, cần người bản ngữ rà lại.
- **Cố ý khác bảng chuẩn / chưa làm:** hai ngón chạm = **dừng đọc** (chuẩn là tạm dừng/tiếp tục; bộ đọc của Android không có tạm dừng giữa chừng); chưa có điều khiển "Dòng" (cần đo dòng hiển thị thật) và "Ngôn ngữ"; "Tìm trên màn hình" là hành động riêng chứ chưa thành điều khiển đọc; chưa có chế độ chọn văn bản (hai ngón chạm hai lần và giữ), trả lời/cúp cuộc gọi bằng hai ngón chạm hai lần, lệnh giọng nói (phải-rồi-lên), luyện cử chỉ/trợ giúp/cử chỉ đi xuyên bằng bốn ngón, chỉnh thanh trượt bằng phải-rồi-trái / trái-rồi-phải. "Đọc từ phần tử đang chọn" gần với "đọc từ phần tử kế" của bảng chuẩn nhưng bắt đầu từ chính phần tử đang chọn. Một trang trợ giúp khác của Google nói ba ngón vuốt trái/phải đổi điều khiển đọc, còn trang bảng cử chỉ chính thức thì không; bản này theo trang bảng cử chỉ (ba ngón vuốt trái/phải vẫn chuyển trang). Ba ngón vuốt lên từ mép dưới vẫn thuộc về nút hỗ trợ tiếp cận của hệ thống.
- **Rủi ro cần biết:** trước đây ba cử chỉ hai bước bắt đầu bằng trái/phải để trống vì cú vuốt trái/phải hơi cong dễ bị nhận nhầm; nay chúng mở Ứng dụng gần đây, Thông báo và Tìm chữ (đúng chuẩn). Ai hay bị nhầm thì gán lại hoặc bỏ gán trong mục Cử chỉ.
- Kiểm thử: 635 bài JVM (mới: `CaretMathTest`, `SpeechRateTest`; cập nhật `GestureTest`, `ReadingControlTest`, `ReadingControlGestureTest`, `SwipeProfileTest`, `QuantityStringsTest`). **Chưa** chạy Gradle/Compose/lint và chưa thử trên máy thật. Cần thử trên máy: dời con trỏ bằng vuốt trong nhiều loại ô nhập (EditText, Compose, WebView), phím media với nhiều trình phát, vuốt ngang có bị nhận nhầm thành trái-rồi-lên / phải-rồi-xuống không, đổi tốc độ đọc nghe rõ, ba ngón vuốt xuống/lên đổi điều khiển.

## Bản 2.19.0: "điều khiển đọc" cho vuốt một ngón lên/xuống (theo bảng cử chỉ chuẩn)

- **Vuốt một ngón xuống = tiến, lên = lùi, theo "điều khiển đọc" đang chọn** (`ReadingControl`, `FocusNavigator.readingControl`). Ban đầu là *Phần tử* (như vuốt phải/trái). Vuốt **lên rồi xuống** = điều khiển KẾ, **xuống rồi lên** = điều khiển TRƯỚC; ứng dụng đọc "Điều khiển đọc: <tên>". Các điều khiển xếp vòng tròn: phần tử → tiêu đề → liên kết → đoạn → câu → từ → ký tự → phần tử (thứ tự và danh sách đổi ở 2.20.0). Thứ tự lên-rồi-xuống / xuống-rồi-lên theo bảng cử chỉ chuẩn của Android; một bản mô tả trước đó đảo ngược hai cử chỉ này, ai quen kiểu đảo thì tự đổi trong mục Cử chỉ.
- **Phần tử / tiêu đề / liên kết** dùng lại `moveElement` và `quickMove` (trong web dùng hành động duyệt chuẩn của nền tảng; ngoài web tự cuộn tìm tiếp như trước). Chế độ đọc cũ (`ReadingMode`, đổi bằng hành động "chế độ đọc kế/trước") là trục riêng chỉ tác động vuốt phải/trái; điều khiển đọc không đụng tới nó.
- **Đoạn / câu / từ / ký tự:** mỗi lần vuốt đọc một đơn vị của phần tử đang chọn (`moveUnit`). Hết chữ (hoặc phần tử không có chữ) thì sang phần tử kế/trước và đọc cả phần tử đó như vuốt phải/trái (`FocusNavigator.moveToNeighbour`). Lùi sang phần tử trước thì con trỏ chữ đặt ở CUỐI phần tử (`TextCursor.parkAtEnd`) để vuốt lên tiếp đọc đơn vị cuối cùng thay vì nhảy qua cả phần tử. Ba ngón vuốt xuống/lên (đơn vị kế/trước) giữ như cũ: dừng và báo "hết văn bản" (từ 2.20.0 ba ngón vuốt xuống/lên đổi điều khiển đọc).
- **Đồng bộ với "đổi đơn vị đọc"** (hai ngón chạm ba lần): điều khiển đọc đi theo đơn vị mới; ngược lại chọn một điều khiển theo đơn vị chữ cũng đặt đơn vị của con trỏ chữ (`TextCursor.select`, giữ chỗ đang đứng) nên "sao chép đơn vị" và ba ngón vuốt dùng đúng đơn vị đang chọn. Con trỏ chữ nay luôn bắt đầu lại khi sang phần tử khác, kể cả hai phần tử có chữ giống hệt (danh sách nút "Xóa").
- **Thanh trượt:** đang chọn thanh trượt thì vuốt lên = tăng, xuống = giảm, bất kể điều khiển đang chọn (`adjustRange`). Công tắc "Cử chỉ cuộn chỉnh thanh trượt" chỉ còn tác dụng với hành động "Cuộn xuống/lên" do bạn tự gán.
- **Điều khiển đọc tự về "Phần tử" khi cửa sổ đổi** (`onWindowChanged`, cùng chỗ với chế độ đọc cũ). "Đọc trạng thái" (bốn ngón chạm hai lần) nhắc điều khiển đang chọn khi khác "Phần tử".
- **Trong trình đơn,** vuốt xuống/lên chuyển mục y như vuốt phải/trái (`MenuGestures.HANDLED`, `ReaderSession.handleMenuAction`); đổi điều khiển trong trình đơn chỉ nhận một lời nhắc ngắn.
- **Mặc định đổi** (`GestureMapping.defaults`): vuốt xuống/lên (trước là cuộn), lên-rồi-xuống / xuống-rồi-lên (trước là "sao chép đơn vị" / "chạy tiện ích"). **Cuộn trang nay bằng hai ngón vuốt lên/xuống** (Android xử lý), hoặc gán "Cuộn xuống/lên" cho cử chỉ bất kỳ. "Sao chép đơn vị đang đọc" và "Chạy các tiện ích đã bật" không còn gán sẵn: vẫn có trong trình đơn phần tử / trình đơn chung (mục Khác) và gán được trong Cử chỉ. Ai đã lưu cử chỉ riêng thì giữ nguyên các cử chỉ đó (cấu hình chỉ lưu phần khác mặc định); ai chưa chỉnh vuốt lên/xuống sẽ nhận hành vi mới ngay khi cập nhật.
- **Chuỗi:** bốn hành động mới ("Tiến theo điều khiển đọc", "Lùi theo điều khiển đọc", "Điều khiển đọc kế tiếp/trước đó") và lời báo "Điều khiển đọc: …"; mục "Điều khiển đọc" mới trong Trợ giúp (và trong tìm kiếm cài đặt); lời giới thiệu mục Tiện ích sửa theo việc không còn cử chỉ mặc định. Đủ 7 ngôn ngữ, bản dịch do máy, cần người bản ngữ rà lại.
- Kiểm thử: 610 bài JVM (mới: `ReadingControlTest`, `TextCursorControlTest`, `ReadingControlGestureTest`; sửa hai khẳng định mặc định trong `GestureTest`). **Chưa** chạy Gradle/Compose/lint và chưa thử trên máy thật. Cần thử trên máy: vuốt lên rồi xuống / xuống rồi lên đổi điều khiển và nghe tên; đi từng từ tới hết phần tử rồi sang phần tử kế (phần tử mới được đọc cả, vuốt xuống tiếp đọc từ đơn vị đầu của nó nên từ đầu có thể nghe lặp) và đi ngược qua phần tử trước; thanh trượt tăng/giảm đúng chiều; hai ngón vuốt vẫn cuộn; trình đơn vuốt xuống/lên.
- **Chưa làm ở bản này (cả ba việc đầu đã làm ở 2.20.0):** vuốt lên/xuống trong ô nhập chưa dời con trỏ thật (mới chỉ đọc theo đơn vị; dời con trỏ cần thử trên máy); ba ngón vuốt lên/xuống vẫn là đơn vị trước/kế (có thể đổi thành đổi điều khiển nếu muốn); mặc định Màn hình chính / Quay lại vẫn là xuống-rồi-trái / lên-rồi-trái, còn bảng chuẩn là lên-rồi-trái = Màn hình chính, xuống-rồi-trái = Quay lại (đổi được trong Cử chỉ; đổi mặc định nếu muốn đúng chuẩn).

## Bản 2.18.1: hai ngón vuốt theo chuẩn, bỏ lối thoát khẩn cấp, rà soát chống sập

- **Hai ngón vuốt nay theo chuẩn các trình đọc màn hình lớn.** Trước đây dịch vụ bật cờ cử chỉ nhiều ngón nhưng KHÔNG bật `FLAG_REQUEST_2_FINGER_PASSTHROUGH`, nên hệ thống nuốt cả bốn cú hai ngón vuốt thành "cử chỉ" gửi cho dịch vụ (kể cả khi bắt đầu từ mép màn hình) và dịch vụ tự cuộn bằng hành động trợ năng; điều hướng bằng cử chỉ của Android bằng hai ngón (vuốt lên từ mép dưới = Màn hình chính, vuốt lên và giữ = Ứng dụng gần đây, vào từ mép trái/phải = Quay lại, vuốt xuống từ mép trên = Thông báo) không bao giờ tới hệ thống. Nay cờ này luôn bật (đặt lúc chạy trong `ReaderSession.applyServiceFlags`, không phụ thuộc tên thuộc tính XML của từng bản SDK): hai ngón vuốt được chuyển nguyên cho ứng dụng/Android như một ngón vuốt. Ba ngón vuốt lên từ mép dưới thuộc về nút hỗ trợ tiếp cận của hệ thống.
- **Hệ quả:** bốn cú hai ngón vuốt không còn gán lại được và không hiện trong "Cử chỉ" (`Gesture.passedToSystem`; chỉ còn một dòng giải thích). Cấu hình cũ vẫn đọc được; mặc định cũ (cuộn / chuyển trang) giữ làm dự phòng cho máy vẫn gửi các cử chỉ này tới dịch vụ. Bộ vuốt gán sẵn (Cuộn và chuyển trang, Duyệt nhanh...) chỉ còn ở nhóm ba ngón (`GestureAnalyzer.hasProfile`). Ai đã gán riêng cho hai ngón vuốt (ví dụ Duyệt nhanh = tiêu đề kế/trước) sẽ thấy các gán đó không còn chạy: dùng ba ngón vuốt thay thế.
- **Cuộn gốc của Android không còn đi qua `FocusNavigator.scroll`**, nên không còn lời "Đã cuộn xuống/lên"; thay vào đó có "Đọc phạm vi danh sách" (Cài đặt → Đọc). Khi danh sách ngừng cuộn 450 ms mà phần tử đang chọn đã khuất khỏi màn hình, `FocusNavigator.releaseIfScrolledAway` bỏ nó để lần vuốt kế bắt đầu từ phần tử đầu tiên đang hiện (phần tử vẫn hiện thì giữ nguyên, kể cả khi ứng dụng tự cuộn để đưa nó vào màn hình).
- **Đã bỏ lối thoát khẩn cấp** (`TOGGLE_TOUCH_EXPLORATION`: hai phím âm lượng cùng lúc, Alt+E, tự bật lại sau 10 phút, cờ khám phá cảm ứng bật/tắt lúc chạy). Lý do: không trình đọc lớn nào làm vậy; dễ bấm nhầm khi cầm máy và lúc đó mọi cú chạm của người khiếm thị thành bấm thật; và việc chiếm phím âm lượng làm hỏng chính phím tắt hỗ trợ tiếp cận của Android (giữ hai phím), vốn là lối thoát đáng tin hơn vì nằm ngoài dịch vụ. Hai phím cùng lúc (`VOLUME_BOTH`) vẫn là cử chỉ gán được nhưng không gán sẵn; cấu hình cũ nhắc tới hành động đã bỏ được bỏ qua mà không mất phần còn lại. Alt+P (tạm dừng phím) vẫn có.
- **Phím âm lượng làm phím tắt nay mặc định TẮT** (các bản 2.14.0–2.18.0 mặc định bật chỉ để có lối thoát khẩn cấp). Ai đã tự bật/tắt trong Cài đặt thì giữ nguyên; ai chưa đụng tới cần bật lại ở Cài đặt → Đọc nếu muốn. Mô tả trong ứng dụng nói rõ: khi bật, phím tắt hỗ trợ tiếp cận của Android (giữ hai phím âm lượng) không dùng được lúc màn hình sáng.
- **Chống sập (rà soát toàn bộ):** `ScreenReaderService` bọc try/catch mọi callback và việc tạo/đóng phiên; `ReaderSession.guardMain` bọc các điểm vào ở luồng chính; `VoiceEngine`/`SpeechEngine` bảo vệ callback TTS và watchdog, `isSpeaking` đúng ở chế độ một công cụ; `Texts.lookup` lùi về chuỗi tiếng Việt dự phòng (rồi tên khóa) thay vì ném lỗi khi thiếu tài nguyên; `Diag` đọc/xóa không ném lỗi và nút sao chép chỉ chép tối đa 100.000 ký tự cuối (clipboard có giới hạn Binder), báo thật khi chép thất bại; Sao lưu/Khôi phục chạy ở luồng nền và giới hạn dung lượng tệp; danh sách ứng dụng trong "Cử chỉ theo ứng dụng" nạp ở luồng nền; `VnApplication` cài bộ ghi lỗi trước tiên và bọc từng bước; bỏ các chỗ dùng `!!`.
- **Thêm:** mục "Cử chỉ hệ thống bằng hai hoặc ba ngón" và "Hai công cụ giọng nói" trong Trợ giúp (7 ngôn ngữ, bản dịch do máy, cần người bản ngữ rà lại).
- Kiểm thử: 584 bài JVM (mới: `SystemGestureTest`, bổ sung `DiagLogTest`, `TextsTest`). **Chưa** chạy Gradle/Compose/lint và chưa thử trên máy thật. Cần thử trên máy: hai ngón vuốt lên từ mép dưới về Màn hình chính (bật điều hướng bằng cử chỉ), hai ngón vuốt cuộn danh sách và chuyển trang, đọc phạm vi danh sách sau khi cuộn, hai công cụ giọng nói, đọc người gọi. Nếu cờ hai ngón không có tác dụng trên một dòng máy (nhà sản xuất tùy biến), cử chỉ hai ngón vẫn rơi về mặc định cũ (cuộn/chuyển trang).

## Bản 2.18.0: ghép dòng v31–v33 vào dòng hai công cụ giọng nói

- **Giữ nguyên** từ dòng hiện tại: hai công cụ giọng nói, chọn ngôn ngữ cho từng công cụ, cử chỉ đổi chế độ giọng, chế độ đọc theo loại, vùng chứa/cửa sổ kế/trước, mức chi tiết, nhật ký.
- **Thêm** từ dòng v31–v33: cử chỉ riêng theo ứng dụng, sao lưu và khôi phục cài đặt, tìm kiếm trong cài đặt, bộ lọc đọc thông báo (không làm phiền, đang gọi, đang phát nhạc, từ khóa chặn/luôn đọc), nhập phím bằng nhấc tay, đọc Toast, phím tắt bàn phím ngoài, phím âm lượng và nút tai nghe làm cử chỉ, thanh trượt, lối thoát khẩn cấp, tự chuyển tiêu điểm vào hộp thoại, nhớ vị trí theo cửa sổ, tùy chọn đọc khi gõ, đọc phạm vi danh sách, đọc người gọi đến, nói vị trí hiện tại, xóa/khôi phục chữ trong ô nhập.
- **Đọc người gọi đến chỉ còn MỘT công tắc** (Cài đặt → Đọc → Cuộc gọi và vị trí). Bật lên thì xin quyền cuộc gọi (bắt buộc), rồi xin thêm quyền danh bạ (tùy chọn): được cấp thì đọc tên trong danh bạ, không thì chỉ đọc số. Công tắc "tên từ danh bạ" cũ đã bỏ; khóa `call_names_contacts` cũ nếu có trong tệp sao lưu sẽ bị bỏ qua.
- **Đã bỏ** phần đọc mã xác thực (như dòng hiện tại), ngưỡng 64 ms cho Android 16 của phím âm lượng (không có nguồn kiểm chứng độc lập; tổ hợp hai phím nay luôn nhận), và mọi nhắc tới tên ứng dụng khác trong chuỗi giao diện.
- **Hòa giải xung đột:** `ReaderSession` (đường cử chỉ qua `GestureRouter` giữ nguyên, thêm `runAction` cho phím); `FocusNavigator.focusOn(node, prefix, interrupt)`; engine TTS đã chọn hỏng thì rơi về mặc định và báo một lần (chuyển vào `VoiceEngine`); trình đơn chỉ tính cử chỉ cảm ứng khi kiểm tra "còn cử chỉ cho phần tử kế" (phím âm lượng không được che mất lỗi kẹt trình đơn).
- `Gesture.fromSystemId` vẫn từ chối mã âm; mã âm của phím cứng chỉ dùng nội bộ.
- Chưa chạy Gradle/lint/Compose ở môi trường soạn thảo (chỉ có biên dịch và 569 bài kiểm thử JVM). Hãy chạy CI và gửi log nếu có lỗi.

## Bản 2.17.1: sửa lỗi build trên CI

- **Lỗi từ log CI:** thiếu `import androidx.compose.foundation.layout.padding` trong `GestureSettingsScreen.kt` (không báo ở bản kiểm tra offline vì không biên dịch Compose); đã thêm. Đã quét mọi tệp Compose khác tìm import thiếu tương tự: không còn.
- **Tệp cũ còn sót trong kho mã:** tải zip lên chỉ thêm/ghi đè tệp, KHÔNG xóa tệp đã bỏ, nên `ai/CaptchaReader.kt` cũ vẫn nằm đó và làm lỗi biên dịch. 9 tệp đã bỏ nay có mặt trong zip dưới dạng tệp RỖNG (chỉ có dòng `package`) để ghi đè bản cũ: `CaptchaImageMath`, `CaptchaImagePrep`, `CaptchaReader`, `CaptchaPolicy`, `NodeVerbosity` và 4 tệp test `Captcha*Test`. Có thể xóa hẳn chúng sau.

## Bản 2.17.0: chọn ngôn ngữ cho cả hai công cụ, thêm Pháp/Tây Ban Nha/Đức/Ý/Bồ Đào Nha

- **Công cụ thứ nhất chọn được ngôn ngữ** (Cài đặt → Giọng đọc): mặc định "Theo ngôn ngữ ứng dụng"; cũng áp dụng ở chế độ một công cụ.
- **Danh sách ngôn ngữ chung cho cả hai công cụ:** Anh, Việt, Pháp, Tây Ban Nha, Đức, Ý, Bồ Đào Nha, Trung, Nga, Ả Rập, Nhật, Hàn. Hai công cụ cùng một ngôn ngữ thì không tách văn bản.
- **Nhận ngôn ngữ (heuristic, có test):** khác hệ chữ → theo hệ chữ. Việt + ngôn ngữ Latin khác: chữ có dấu/ký tự chỉ có trong tiếng Việt là Việt; từ mà bỏ hết dấu vẫn không thành âm tiết tiếng Việt hợp lệ ("café", "señor", "Straße", "Bluetooth") thuộc ngôn ngữ kia. Pháp/Tây Ban Nha/Ý/Bồ Đào Nha có dấu trùng tiếng Việt nên dấu đơn thuần chưa đủ để kết luận. Hai ngôn ngữ Latin không có tiếng Việt: dùng danh sách từ chức năng thông dụng và chữ có dấu (khi một bên là tiếng Anh). Từ không có bằng chứng là trung tính, theo từ đứng trước.
- Từ trung tính ở ĐẦU văn bản nay thuộc ngôn ngữ thứ nhất (trước đây theo từ mạnh đầu tiên, gây sai với "Xin chào señor").
- 3 chuỗi mới (dịch máy). Chưa thử với engine thật; danh sách từ chức năng nhỏ nên câu rất ngắn dễ nhận sai.

## Bản 2.16.2: rà soát

- Khi ngôn ngữ ứng dụng trùng ngôn ngữ của công cụ thứ hai (ví dụ cả hai là tiếng Anh) thì không tách văn bản nữa, đọc hết bằng công cụ thứ nhất (trước đây có thể đẩy nhầm vài từ sang công cụ thứ hai). Có test.
- Đã đọc lại thứ tự khóa giữa `SpeechEngine` và `VoiceEngine` (không có đường khóa ngược nhau), thứ tự "đọc xong" và đồng hồ canh, việc áp chế độ từ luồng nền: không thấy lỗi khác. Chưa thử với hai engine thật.

## Bản 2.16.1: đơn giản hóa phần chọn công cụ đọc

- **Chỉ còn hai lựa chọn:** *Một công cụ* (chọn một công cụ duy nhất) hoặc *Hai công cụ* (chọn cả hai: công cụ thứ nhất đọc ngôn ngữ ứng dụng, công cụ thứ hai đọc ngôn ngữ đã chọn). Bỏ chế độ "Chỉ công cụ thứ hai" gây rối. Giá trị cũ `SECOND_ONLY` đã lưu từ bản 2.15 tự rơi về "Một công cụ".
- Màn hình: chọn chế độ trước, rồi hiện ngay các bộ chọn công cụ tương ứng (một hoặc hai). Cử chỉ "Đổi chế độ công cụ đọc" nay đổi qua lại giữa hai chế độ.
- Chưa thử với hai engine thật trên máy; chưa build Gradle/Compose.

## Bản 2.16.0: chia văn bản theo câu (như chế độ tự nhận ngôn ngữ của các bộ đọc nhiều engine)

- **Cài đặt → Giọng đọc → Cách chia văn bản** (hiện khi chọn "Hai công cụ"): *Theo từ* (đổi công cụ tại từng từ của ngôn ngữ kia, như bản 2.15) hoặc *Theo câu* (mỗi câu do MỘT công cụ đọc trọn, theo ngôn ngữ chiếm nhiều từ hơn trong câu; hòa thì theo câu trước). Theo câu mượt hơn và ít đổi giọng; "Bật Bluetooth để kết nối." vẫn đọc trọn bằng giọng tiếng Việt.
- Tách câu theo dấu `. ! ? … ؟` (cần khoảng trắng sau) và `。！？` (không cần). Ghép các đoạn lại luôn bằng đúng văn bản gốc (có test).
- Học từ mô tả công khai của các bộ đọc nhiều engine (chế độ theo từ và chế độ tự nhận theo câu); không dùng mã của ứng dụng nào.
- 4 chuỗi mới (dịch máy). Chưa thử với hai engine thật trên máy.

## Bản 2.15.1: rà soát hai công cụ đọc

- **Cảnh báo thiếu giọng:** trước đây công cụ này báo đủ giọng sẽ xóa cảnh báo thiếu giọng của công cụ kia. Nay trạng thái được gộp: thiếu ở bất kỳ công cụ nào đang dùng thì vẫn báo.
- **Câu báo khi đổi chế độ bằng cử chỉ:** áp chế độ ngay rồi mới đọc, nên câu báo dùng đúng công cụ mới và không bị cắt khi công cụ cũ đóng. `SpeechEngine.configure` gọi được từ luồng bất kỳ.
- **Tách ngôn ngữ:** dạng rút gọn tiếng Anh có dấu nháy ("don't", "it's") nay được coi là tiếng Anh thay vì trung tính.
- Test thêm: `LanguageRunsContractionTest`. Chưa thử với hai engine thật trên máy.

## Bản 2.15.0: hai công cụ đọc, mỗi công cụ một ngôn ngữ

- **Cài đặt → Giọng đọc → Chế độ công cụ đọc:** *Một công cụ* (mặc định, như cũ) / *Hai công cụ, mỗi công cụ một ngôn ngữ* / *Chỉ công cụ thứ hai*. Công cụ thứ nhất đọc ngôn ngữ ứng dụng (ví dụ tiếng Việt), công cụ thứ hai đọc ngôn ngữ bạn chọn (mặc định tiếng Anh; có thể chọn Trung, Nga, Ả Rập, Nhật, Hàn). Tắt bớt một công cụ bằng cách đổi chế độ: công cụ không dùng được đóng kết nối ngay.
- **Cử chỉ mới "Đổi chế độ công cụ đọc"** (không gán mặc định): xoay vòng ba chế độ và đọc tên chế độ.
- **Cách tách ngôn ngữ** (`LanguageRuns`, có test): khác hệ chữ thì tách theo hệ chữ (chắc chắn). Việt + Anh: từ có dấu là tiếng Việt; từ không dấu không khớp cấu trúc một âm tiết tiếng Việt ("Bluetooth", "wifi", "settings") là tiếng Anh; từ ngắn nhập nhằng ("to", "can") theo từ đứng trước. Đây là heuristic: từ rất ngắn hoặc tên riêng lạ có thể bị giao nhầm công cụ. Cặp Latin không có tiếng Anh (ví dụ Pháp + Tây Ban Nha) không tách.
- **Phát tuần tự** (`RunScheduler`, có test): hai TextToSpeech không có hàng đợi chung nên mỗi lúc chỉ một đoạn được phát; ngắt dừng cả hai công cụ; có đồng hồ canh để hàng đợi không kẹt nếu một công cụ chết giữa chừng. Chế độ một công cụ đi thẳng đường cũ, không qua bộ điều phối.
- Mã: `SpeechEngine` nay là lớp ghép; mỗi kết nối TTS nằm trong `VoiceEngine` (mã cũ của `SpeechEngine`, thêm ngôn ngữ riêng và báo "đọc xong").
- 11 chuỗi mới (dịch máy, cần người bản ngữ rà soát). Chưa chạy trên máy thật: cần thử với hai engine thật (ví dụ Google + một engine khác), kiểm tra không đọc chồng và độ trễ khi đổi giọng giữa câu.

## Bản 2.14.1

- Sửa test `GestureRouterTest` (kỳ vọng sai: trong trình đơn, vuốt phải là "mục kế", không phải hành động ngoài trình đơn). Mã chạy đúng, chỉ test sai nên CI sẽ đỏ. Đã đối chiếu mọi hằng số `AccessibilityService.GESTURE_*` với android.jar API 36: đều tồn tại.

## Bản 2.14.0: sửa lỗi tồn đọng (cử chỉ, TTS, ổn định) và phần ngôn ngữ

Rà soát bản 2.13.0 (đối chiếu hành vi với một trình đọc màn hình khác, không sao chép mã) và vá các lỗi tìm thấy.

- **Cử chỉ:** `ReaderSession.handleGesture` trước đây tính `inMenu` và hành động ngay trên luồng chính nhưng chạy sau trên luồng nền,
  nên vuốt nhanh hai lần có thể dùng trạng thái trình đơn đã cũ. Nay `GestureRouter` (thuần hàm, có test) được gọi hai lần: một lần
  trên luồng chính chỉ để báo "đã xử lý" và ngắt lời đọc, một lần trên luồng xử lý ngay trước khi chạy hành động (kết quả chính thức).
- **Mã cử chỉ hệ thống:** bỏ `SystemGestureIds` (tra tên bằng reflection, gõ sai tên thì cử chỉ lặng lẽ biến mất). minSdk 35 nên dùng
  thẳng hằng số `AccessibilityService.GESTURE_*`; sai tên sẽ báo lỗi biên dịch. Bỏ `Gesture.isSupported` và dòng nhật ký `GESTURES unsupported`.
- **TTS tự phục hồi:** engine bị tắt hoặc vừa cập nhật khiến mọi lệnh đọc trả `ERROR` và máy im lặng đến khi tắt/bật dịch vụ. Nay
  `SpeechEngine` tạo lại engine khi `speak` trả `ERROR` hoặc `onError(ERROR_SERVICE)`, đọc lại câu vừa lỡ, có giới hạn
  (`SpeechRecoveryPolicy`: tối đa 3 lần/phút, cách nhau ≥ 1,5 giây; đọc xong một câu thì xóa bộ đếm).
- **Ổn định:** mọi `CoroutineScope` của dịch vụ có `CoroutineExceptionHandler` (`CoroutineGuard`, ghi vào nhật ký lỗi). `runSafely` bắt thêm
  `VirtualMachineError` (hết bộ nhớ, tràn ngăn xếp) và báo "hành động thất bại". `AiAssistant` bắt `OutOfMemoryError` khi xử lý ảnh chụp.
- **Ngôn ngữ:**
  - Tiếng Trung: dấu `。！？` kết thúc câu không cần khoảng trắng phía sau; "từ" của cụm chữ Hán/Nhật/Thái được tách tiếp bằng `BreakIterator`
    (từ điển ICU của Android; trên JVM thử nghiệm không tách được nên test chỉ kiểm tra các tính chất bất biến). Thêm dấu `؟ ۔` của tiếng Ả Rập.
  - Ký tự theo cụm ký tự người dùng thấy (`Graphemes`): dấu thanh viết rời (NFD), emoji màu da/ZWJ, cờ, cặp surrogate không bị tách khi
    duyệt từng ký tự và khi đánh vần (`SpeechFormats.spell` trước đây duyệt từng `Char` UTF-16). Văn bản được chuẩn hóa NFC trước khi đọc.
  - Thiếu giọng cho ngôn ngữ ứng dụng: dịch vụ nhắc bằng lời một lần mỗi ngôn ngữ, và màn hình Giọng đọc hiện nút **Cài dữ liệu giọng**
    (`ACTION_INSTALL_TTS_DATA`). Trước đây chỉ ghi log.
  - `SpeechChunker` (văn bản > 4000 ký tự) cắt được sau dấu toàn chiều rộng và dấu hỏi tiếng Ả Rập; `LinkRoles` nhận vai trò "liên kết" ở cả
    es/fr/ru/ar/zh; `SkipAdPlugin` có từ khóa bằng es/fr/ru/zh/ar.
- **Nhỏ:** `DiagFile` giới hạn theo BYTE (trước đây tính theo số ký tự nên tệp tiếng Việt vượt ngân sách); sửa chú thích enum `Gesture`.
- **Chuỗi mới:** 5 chuỗi (`msg_tts_voice_missing`, `vo_voice_*`) thêm vào `tools/i18n/table_d.py` rồi chạy bộ sinh. Bản dịch là dịch máy, cần người bản ngữ rà soát.
- **Phiên bản:** `versionCode` 39, `versionName` 2.14.0.
- **Test mới:** `GestureRouterTest`, `GestureIdsTest`, `SpeechRecoveryPolicyTest`, `GraphemesTest`, `TextUnitsLanguageTest`, `SpeechFormatsSpellTest`,
  `DiagFileBytesTest`, `SpeechChunkerCjkTest`, `LinkRolesTest`.
- **Chưa làm / cần quyết định:** khi trình đơn đang mở, chạm hai ngón (`STOP_SPEECH`) vừa dừng đọc vừa đóng trình đơn; chưa đổi vì có thể là chủ ý.
  Các tính năng còn thiếu so với trình đọc khác (phím cứng, `NotificationListenerService`, trạng thái cuộc gọi/màn hình, hồ sơ theo ứng dụng,
  xuất/nhập cài đặt) chưa làm.
- **Chưa kiểm chứng:** môi trường soạn thảo không có trình biên dịch Kotlin, Gradle hay Android SDK nên chưa chạy build, lint hay
  `./gradlew :app:testDebugUnitTest`. Đã kiểm cân ngoặc, nhất quán chuỗi bằng bộ sinh, và đối chiếu logic tách câu/ký tự bằng bản thử Java trên JDK 21.
  Cần chạy build và thử trên máy thật (nhất là phục hồi TTS và tách từ tiếng Trung).

## Bản 2.13.0: hợp nhất nhánh 2.11.0 với nhánh 2.12.x

Lấy bản 2.11.0 làm nền (giữ nguyên các tính năng của nó) và đưa vào những phần còn thiếu:

- **Đã bỏ** toàn bộ phần đọc mã xác thực khỏi mã nguồn, chuỗi, cử chỉ và trình đơn; không còn mục nào liên quan.
- **Mức chi tiết và gợi ý thao tác:** dùng một bản duy nhất (`Verbosity`, `ActionHints`); bỏ bản trùng.
- **Thêm vào nền 2.11.0:** chế độ đọc theo loại, vùng chứa kế/trước, cửa sổ kế/trước, 11 cử chỉ mới (tra theo tên lúc chạy, máy không có thì ẩn), vuốt trang có kiểm tra màn hình đổi, nhật ký chẩn đoán tự động (chỉ siêu dữ liệu), trình đơn mở thẳng mục đầu, gợi ý cho ảnh không nhãn, giao diện gọn (mỗi cử chỉ một điểm dừng, không tiêu đề thừa).
- **Giữ của nhánh 2.11.0:** bám vết tiêu điểm, `ClickResolver`, duyệt nhanh tiêu đề/liên kết, cử chỉ 2-3 ngón theo bộ gán sẵn, nhật ký lỗi, màn hình Quyền, plurals.
- Chưa chạy Gradle/lint/Compose/ML Kit ở môi trường soạn thảo; cần chạy `./gradlew :app:testDebugUnitTest` và thử trên máy thật.

## Bản 2.12.3

- **Chế độ đọc theo loại** (tiêu đề, liên kết, điều khiển, ô nhập, ô tích, hình ảnh): hành động "chế độ đọc tiếp theo/trước đó"; khi khác "phần tử", vuốt phần tử kế/trước nhảy theo loại và tự cuộn tìm tiếp (tối đa 4 lần, 4 giây). Tìm không ra thì trả lựa chọn về chỗ cũ. Chế độ tự về mặc định khi cửa sổ đổi; trạng thái ("Đọc trạng thái") nhắc chế độ đang dùng.
- **Vùng chứa và cửa sổ kế/trước** (hành động mới). Lựa chọn cửa sổ chỉ mất khi cửa sổ thật sự đổi. Plugin vẫn chạy trên cửa sổ đang hoạt động.
- **11 cử chỉ mới** (nhấn giữ nhiều ngón, bốn ngón vuốt/nhấn ba lần), tra theo tên hằng số lúc chạy; máy không có thì ẩn (nhật ký ghi `GESTURES unsupported=...`). Không gán mặc định.
- **Giao diện gọn hơn:** mỗi cử chỉ một điểm dừng; không tiêu đề/giới thiệu thừa ở màn hình cử chỉ và trình đơn; trình đơn mở thẳng mục đầu.
- **Cài đặt Đọc:** mức chi tiết (đầy đủ/gọn/tối giản), đọc gợi ý thao tác, **nhật ký chẩn đoán tự động** (chỉ siêu dữ liệu, không có chữ trên màn hình; sao chép để gửi).
- **Chuyển trang/cuộn:** thử hành động trước, rồi vuốt giả lập (`canPerformGestures`) và kiểm tra màn hình có đổi không; bám vết tiêu điểm khi giao diện vẽ lại; nhấn không nhấn đúp hai lần; chỉ nhấn nút con khi nó duy nhất; nhấn giữ có dự phòng ngữ cảnh/chạm giữ.

## Bản 2.15.0: đọc người gọi, nói vị trí hiện tại, xóa tất cả trong ô nhập

Cả ba đều TẮT mặc định hoặc chỉ xin quyền khi bạn bật; tắt công tắc là ứng dụng ngừng dùng quyền đó.

- **Đọc người gọi đến** (Cài đặt → Đọc → Cuộc gọi và vị trí). Quyền: `READ_PHONE_STATE` + `READ_CALL_LOG` (từ Android 9, số gọi đến chỉ có khi có cả hai; theo tài liệu TelephonyManager). Khi có `READ_CALL_LOG` hệ thống gửi MỖI hồi chuông hai broadcast (một có số, một không, thứ tự không đảm bảo; số rỗng = số lạ/ẩn): `CallRingPolicy` chỉ đọc một lần, chờ tối đa 1,2 giây cho bản có số. Thiếu quyền số thì chỉ đọc "Có cuộc gọi đến". Công tắc riêng "đọc tên từ danh bạ" (`READ_CONTACTS`, tra bằng `PhoneLookup`, chạy ngoài luồng chính). Số được đọc từng chữ số theo nhóm 3. Bộ nhận chỉ được đăng ký khi công tắc bật VÀ có quyền. Số/tên không bị ghi nhật ký hay lưu. Lưu ý: Google Play hạn chế `READ_CALL_LOG`; bản này dành cho cài trực tiếp.
- **Nói vị trí hiện tại** (Alt+G, hoặc gán cử chỉ "Nói vị trí hiện tại của tôi"; có trong trình đơn chung → Khác): quyền `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`, công tắc "Cho phép nói vị trí của tôi". Chỉ hỏi vị trí MỘT lần khi bạn chạy hành động (không theo dõi, không lưu, không gửi): thử GPS tối đa 12 giây, rồi nhà cung cấp hợp nhất/mạng 6 giây, rồi vị trí cũ còn nhớ. Địa chỉ do `Geocoder` của hệ thống tra (dịch vụ vị trí của điện thoại có thể liên hệ nhà cung cấp qua internet; ứng dụng vẫn KHÔNG có quyền INTERNET); không có địa chỉ thì đọc vĩ độ/kinh độ 5 chữ số thập phân. Luôn đọc độ chính xác ("khoảng N mét"); sai số từ 50 m hoặc vị trí cũ hơn 2 phút thì nói rõ có thể chưa chính xác.
- **Xóa tất cả trong ô nhập** (Alt+X, trình đơn "Chỉnh sửa văn bản", hoặc gán cử chỉ) và **khôi phục chữ vừa xóa** (Alt+Shift+X): dùng `ACTION_SET_TEXT` rỗng, không đụng clipboard; chữ cũ giữ trong bộ nhớ (tối đa 20 000 ký tự) và chỉ khôi phục khi ô còn trống. Ô mật khẩu không bao giờ bị xóa hay được nhớ (theo `EditPolicy`).
- Mục "Quyền" nay liệt kê ba tính năng với trạng thái cấp quyền; `ManifestPermissionsTest` vẫn buộc quyền trong manifest khớp đúng `PermissionFeatures`.

Chưa làm: tự trả lời/từ chối cuộc gọi (cần `ANSWER_PHONE_CALLS`), đọc cuộc gọi khi tắt màn hình bằng nguồn khác ngoài broadcast, la bàn/hướng đi trong "nói vị trí", đọc đường phố giao nhau gần nhất, kiểm tra chính tả số theo từng quốc gia.

## Bản 2.14.0: phím âm lượng đúng kiểu, nút tai nghe, thanh trượt, lối thoát khẩn cấp (lối thoát khẩn cấp và mặc định bật phím âm lượng đã bỏ ở 2.18.1)

Phím âm lượng và cử chỉ:

- **Phím âm lượng không còn làm mất chỉnh âm lượng.** Phím bị chiếm thì chính trình đọc chỉnh âm lượng (`ReaderSession.adjustVolume`): trong cuộc gọi chỉnh âm cuộc gọi; đang đọc hoặc đang chạm màn hình chỉnh luồng trợ năng (đúng giọng đọc); còn lại chỉnh âm phương tiện (`VolumeStreamPolicy`). Không hiện bảng âm lượng hệ thống mà đọc phần trăm mới.
- **Phím cứng thành "cử chỉ" 0 ngón** (`Gesture.fingers = 0`, `systemId` âm, hệ thống không bao giờ gửi): `VOLUME_UP/DOWN_SHORT`, `VOLUME_UP/DOWN_LONG`, `VOLUME_BOTH`, `HEADSET_SHORT/DOUBLE/LONG`. Vì vậy gán lại được trong Cài đặt cử chỉ (nhóm "Phím âm lượng và nút tai nghe") và gán riêng cho từng ứng dụng bằng cơ chế sẵn có. Mặc định: bấm nhanh = tăng/giảm âm lượng; giữ tăng = phần tử trước, giữ giảm = phần tử kế; hai phím cùng lúc = lối thoát khẩn cấp; tai nghe: một lần = phần tử kế, hai lần = kích hoạt, giữ = đọc từ phần tử hiện tại.
- **Phím âm lượng bật mặc định** (trước là tắt) vì bấm nhanh không còn mất chức năng gốc và lối thoát khẩn cấp cần có sẵn. Tắt trong Cài đặt → Đọc → Phím nếu cần phím tắt trợ năng "giữ hai phím âm lượng" của hệ thống.
- **Lối thoát khẩn cấp** (`TOGGLE_TOUCH_EXPLORATION`): hai phím âm lượng cùng lúc, Alt+E hoặc một cử chỉ tạm dừng "duyệt bằng cách chạm" (bỏ cờ khám phá cảm ứng, cử chỉ nhiều ngón và nhấn đôi do dịch vụ xử lý) để dùng màn hình như bình thường khi trình đọc làm kẹt một màn hình. Trong lúc tạm dừng, phím âm lượng luôn được xử lý (bất kể cài đặt khác): bấm nhanh = chỉnh âm lượng, hai phím = bật lại; Alt+E và Alt+P luôn dùng được. Tự bật lại khi tắt màn hình, sau 10 phút, hoặc khi phiên đóng, nên không bao giờ kẹt vĩnh viễn.
- **Nút tai nghe** (`KEYCODE_HEADSETHOOK`, mặc định tắt): chỉ khi màn hình sáng và không trong cuộc gọi.
- **Thanh trượt**:
  - Cử chỉ cuộn (một/hai ngón vuốt lên/xuống, mặc định bật, có công tắc) đổi giá trị thanh trượt đang chọn ≈5% mỗi bước thay vì cuộn trang (`FocusNavigator.adjustRange`, dùng `ACTION_SET_PROGRESS`, không có thì cuộn tới/lui của chính nó). Từ 2.19.0 vuốt một ngón xuống/lên không còn là cuộn mà là "tiến/lùi theo điều khiển đọc", và luôn chỉnh thanh trượt đang chọn (không phụ thuộc công tắc này; công tắc chỉ còn tác dụng với hành động "Cuộn xuống/lên" do bạn tự gán).
  - Hành động riêng gán được: "Tăng/Giảm thanh trượt đang chọn", "Kéo thanh trượt đang chọn tới cuối". Thanh chuẩn thì đặt thẳng giá trị lớn nhất; núm kéo thì thực hiện một cú kéo từ tâm núm tới mép phải đường trượt (`dragToEnd`). Chỉ kéo thẳng theo lệnh của người dùng, KHÔNG giải mã xác thực ghép hình.

Chưa làm: bấm đúp/ba phím âm lượng, phím âm lượng điều khiển con trỏ khi sửa văn bản, hiện bảng âm lượng hệ thống, nhận lệnh phím khi tắt màn hình, kéo thanh trượt sang trái, thanh trượt dọc, trình đơn có sẵn mục thanh trượt.

## Bản 2.13.0: an toàn phím, màn hình tắt/bật, hộp thoại và nhớ vị trí, tùy chọn gõ phím, phạm vi danh sách

- **Lối thoát cho phím cứng/phím âm lượng**: Alt+P (hoặc cử chỉ "Tạm dừng/tiếp tục phím") tạm dừng cả hai, không lưu (khởi động lại dịch vụ là hoạt động lại). Alt+V (hoặc cử chỉ "Tắt/bật phím âm lượng cho ứng dụng này") ghi ứng dụng đang mở vào danh sách không dùng phím âm lượng (`volume_excluded_apps`). Hai hành động mới đã có trong danh sách cử chỉ, chưa gán mặc định.
- **Màn hình tắt/bật** (`ACTION_SCREEN_OFF/ON`): luôn dừng giọng đọc, bỏ trạng thái phím đang giữ dở, hủy việc AI/chuyển tiêu điểm đang chờ; công tắc "Báo khi bật và tắt màn hình" (mặc định tắt) báo thêm bằng lời.
- **Tự chuyển tiêu điểm vào hộp thoại** (mặc định bật): cửa sổ có lớp `*Dialog*`/`*BottomSheet*` mở ra thì đọc tiêu đề rồi chọn phần tử đầu tiên, nhưng chỉ khi người dùng chưa tự chọn gì và không đang trong trình đơn.
- **Nhớ vị trí theo cửa sổ** (mặc định tắt): nhớ phần tử đang chọn khi rời cửa sổ (khóa = gói + tiêu đề, tối đa 8 cửa sổ) và tìm lại bằng vết (mã/nhãn) khi quay về.
- **Gõ phím**: đọc từng ký tự (mặc định bật, như cũ), đọc từ vừa gõ xong sau dấu cách/dấu câu (mặc định tắt), đọc "Chữ hoa" trước chữ viết hoa (mặc định tắt). Ô mật khẩu không bao giờ đọc từ hay chữ hoa. Chưa làm: đọc từ ví dụ cho chữ cái (cần bảng theo từng ngôn ngữ).
- **Đọc phạm vi danh sách khi cuộn** (mặc định tắt): "Mục 5 đến 9 trên 30" sau khi danh sách ngừng cuộn 450 ms; chỉ khi ứng dụng báo đủ `fromIndex/toIndex/itemCount` hợp lý.

Chưa build/chạy test trong môi trường soạn thảo: chạy `./gradlew :app:testDebugUnitTest :app:lintDebug` rồi thử trên máy thật.

## Bản 2.12.0: sửa lỗi + phím cứng và phím âm lượng

Các lỗi tìm thấy ở v2.11.0 và cách xử lý:

- **Hiện/ẩn bàn phím không còn xóa vị trí đang chọn**: `TYPE_WINDOW_STATE_CHANGED` của cửa sổ bàn phím (IME) hoặc lớp phủ trợ năng chỉ làm mới bộ đệm, không `reset()`, không đóng trình đơn, không ngắt lời đang đọc (`ReaderSession.handleWindowState`). Cửa sổ ứng dụng, hộp thoại, thanh thông báo vẫn đặt lại như trước.
- **Đọc vùng cập nhật trực tiếp (`liveRegion`), khung mới (`paneTitle`) và `announceForAccessibility`**: thêm `typeAnnouncement` và xử lý `TYPE_WINDOW_CONTENT_CHANGED` qua kênh riêng (`contentEvents`, tối đa 1 sự kiện/80 ms, chỉ các kiểu đổi chữ/mô tả/cây con/khung). Assertive ngắt lời, còn lại xếp hàng; `LiveRegionGate` chống đọc lặp. Các màn hình của chính ứng dụng dùng `liveRegion` nay được đọc.
- **Toast** đọc theo công tắc riêng (Đọc thông báo → Đọc thông báo nổi, mặc định bật), không phụ thuộc "đọc thông báo", không gộp "N thông báo mới", kể cả Toast của chính ứng dụng.
- **Sự kiện bổ sung**: `typeViewTextSelectionChanged` (đọc ký tự vừa đi qua / đoạn được chọn / dòng khi nhảy xa; `SelectionSpeech`; bỏ qua tiếng vọng của chính lệnh đặt con trỏ), `typeViewSelected` (chỉ tab), `typeViewLongClicked` (bỏ tiếng vọng của lệnh nhấn giữ của trình đọc), `typeWindowsChanged` (chỉ đánh dấu bộ đệm cũ).
- **Giọng đọc không còn câm vĩnh viễn**: engine đã chọn hỏng/bị gỡ → sau 5 lần thử lại tự rơi về engine mặc định và báo một lần (không đổi lựa chọn trong cài đặt); engine mặc định hỏng thì thử lại mỗi 60 giây. Lỗi dịch vụ TTS (`ERROR_SERVICE`, `ERROR_NOT_INSTALLED_YET`) hoặc 3 lần `speak()` trả `ERROR` liên tiếp → dựng lại engine (tối đa 1 lần/5 giây) và đọc lại câu đang dở.
- **`runSafely`** nay bắt mọi `Exception`, `StackOverflowError`, `OutOfMemoryError`; scope có `CoroutineExceptionHandler` nên lỗi lạ chỉ được ghi nhật ký, không làm sập tiến trình hay chết vòng xử lý. Hàng đợi sự kiện chính 32 → 128 để rê ngón nhanh không làm rơi sự kiện nhấc tay.
- Nhỏ: giới hạn độ dài `message` của script nay đúng 120 (trước đây bị chặn ở 100); KDoc `speakPasswords`/`announceActionHint` về đúng chỗ; `FocusNavigator.cachedNodes/cachedAtMs` là `@Volatile`.

**Phím cứng** (Cài đặt → Đọc → Phím; mặc định BẬT): giữ Alt hoặc Windows/Search rồi nhấn phím (bảng đầy đủ trong Trợ giúp và `KeyShortcuts`). Chỉ tổ hợp có Alt/Meta và không có Ctrl mới bị chặn, nên gõ văn bản và Ctrl+phím vẫn hoạt động; Ctrl bấm riêng = dừng đọc. Nhả phím của tổ hợp đã chặn cũng được chặn nốt. Cần `canRequestFilterKeyEvents` + `flagRequestFilterKeyEvents` (đã thêm vào cấu hình dịch vụ; không cần quyền runtime). Người dùng đã bật dịch vụ từ bản cũ có thể phải tắt rồi bật lại dịch vụ để hệ thống áp cấu hình mới.

**Phím âm lượng** (cùng mục, mặc định TẮT): giảm = phần tử kế, tăng = phần tử trước, nhấn cả hai = kích hoạt, giữ nửa giây = cuộn tới/lui (`VolumeKeyNav`, có khôi phục khi mất sự kiện nhả phím). Chỉ chiếm phím khi màn hình sáng và không trong cuộc gọi/chuông reo; tắt màn hình hoặc đang gọi thì chỉnh âm lượng như thường. Không có cử chỉ vân tay (theo yêu cầu).

Chưa làm (có chủ đích, cần máy thật): duyệt xuyên nhiều cửa sổ (`service.windows`) vì phải sửa bộ nhớ vết tiêu điểm theo cửa sổ; `speakPasswords` vẫn gộp hai việc (đọc ký tự gõ và không che OCR); audio ducking.
Chưa build/chạy test trong môi trường soạn thảo: chạy `./gradlew :app:testDebugUnitTest :app:lintDebug` rồi thử trên máy thật (bàn phím hiện/ẩn, Toast, Snackbar, bàn phím Bluetooth, phím âm lượng).

## Bản 2.11.0: bám vết tiêu điểm, nhấn liên kết web, cử chỉ 2-3 ngón, mức chi tiết, nhật ký lỗi

- **Không nhảy về đầu khi giao diện vẽ lại**: `FocusNavigator` giữ bộ đệm vết (`FocusTraceBuffer`: mã duy nhất, viewId, nhãn, hộp tọa độ, chỉ số danh sách) của vài phần tử gần nhất. Nút đang chọn bị tái chế thì tìm lại theo vết (`TraceLocator`), không khớp thì dùng khe theo vị trí (`FocusTracker`). Chỉ `reset()` (màn hình/cửa sổ mới, sau khi cuộn) mới xóa vết.
- **Nhấn liên kết/vùng tương tác trong WebView**: `ClickResolver` + `ClickTargetPolicy` chọn nút nhận `ACTION_CLICK`: chính nó hoặc tổ tiên gần nhất có hành động nhấn (kể cả khi cờ `isClickable` là false), không leo vào gốc WebView/vùng cuộn; không có thì thử con bấm được (ưu tiên thẻ liên kết); cuối cùng, chỉ trong nội dung web, giả lập một cú chạm vào giữa phần tử. `ActionDispatcher` đọc câu báo theo kết quả (`ActivateOutcome`). Vai trò "liên kết" được đọc khi nhận diện được.
- **Duyệt nhanh trong web** dùng hành động chuẩn của nền tảng (`ACTION_NEXT/PREVIOUS_HTML_ELEMENT`, loại LINK/HEADING) nên không cần cuộn; vòng cuộn dài dừng ngay khi có cử chỉ mới.
- **Cử chỉ hai và ba ngón**: `GestureAnalyzer` phân nhóm vuốt theo số ngón + hướng; `SwipeProfile` là bộ gán sẵn cho bốn hướng (cuộn/chuyển trang, đổi điều khiển đọc/chuyển trang, đọc theo đơn vị, duyệt nhanh tiêu đề/liên kết, cả trang). Chọn nhanh ở đầu nhóm hai/ba ngón trong màn hình Cử chỉ; từng cử chỉ vẫn gán riêng được cho bất kỳ chức năng nào. Mặc định không đổi. Chức năng mới: tiêu đề/liên kết kế-trước, cuộn tới đầu/cuối trang, nhảy phần tử đầu/cuối (cũng có trong trình đơn chung, mục Khác).
- **Gợi ý và mức chi tiết** (Cài đặt → Đọc): công tắc riêng "Đọc gợi ý thao tác" (câu gợi ý xếp hàng sau phần tử, không ngắt; tên cử chỉ lấy theo cấu hình hiện tại) và mức Gọn/Chuẩn/Chi tiết ("Mục 3 trên 12" khi ứng dụng cung cấp thông tin danh sách). Nội dung chính của phần tử luôn đọc đủ.
- **Nhật ký lỗi** (Cài đặt → Nhật ký lỗi): chỉ lưu trong `filesDir/crash_logs` (bộ nhớ riêng, không backup, không cần quyền). Tối đa 3 báo cáo sập (mỗi báo cáo ≤ 16.000 ký tự) + một tệp sự kiện lỗi nhẹ cuộn vòng ≤ 24.000 ký tự, tổng ≤ ~80 KB; lỗi lặp bị gộp (1 lần/phút/chữ ký, ≤ 20 lần/giờ). Thông điệp lỗi được che thư điện tử, địa chỉ web, chuỗi số dài. Xem, chia sẻ (hộp chia sẻ hệ thống, dạng văn bản) và xóa (có hỏi lại) ngay trong ứng dụng; có công tắc tắt. Không gửi đi đâu tự động.
- **Đối chiếu với trình đọc khác (học hỏi, không sao chép)**: dịch vụ nay nghe thêm `typeWindowContentChanged`, `typeViewScrolled` (chỉ đánh dấu bộ đệm điểm dừng đã cũ, không qua hàng đợi) và `typeViewAccessibilityFocused` (đồng bộ con trỏ khi ứng dụng/trình duyệt tự đặt tiêu điểm, không đọc lại); `notificationTimeout` = 0 để giảm trễ khi rê ngón tay. Thứ tự duyệt tôn trọng `accessibilityTraversalAfter/Before` (`TraversalOrder`). "Cuộn tới đầu/cuối" nhảy thẳng bằng `ACTION_SCROLL_TO_POSITION` khi danh sách biết số hàng.
- Lượt rà soát lần hai: gợi ý thao tác không còn duyệt con (tránh ~60 lần gọi hệ thống mỗi lần chọn); duyệt nhanh web xuất phát từ gốc WebView không báo sai "không còn" khi chỉ là chưa có điểm bắt đầu. Chưa có (khác biệt tính năng, không phải lỗi): nhập phím bằng nhấc tay trên bàn phím, phím tắt âm lượng/phím cứng, vân tay, quy tắc theo từng ứng dụng, chỉnh độ trễ chạm.
- **Nhập phím bằng nhấc tay** (Cài đặt → Đọc, mặc định tắt): rê tới phím rồi nhấc tay là gõ; `LiftToTypePolicy` chỉ cho nhấn một lần mỗi lần chạm, hủy khi lướt khỏi bàn phím, khi bắt đầu cử chỉ hoặc khi chạm quá 5 giây. **Độ trễ chạm** (0 đến 600 ms, mặc định 0): ngón tay phải dừng đủ lâu mới đọc; phím luôn đọc ngay khi bật nhập bằng nhấc tay. Độ trễ chạm đúp do hệ thống quyết định, ứng dụng không chỉnh được.
- **Cử chỉ theo ứng dụng** (Điều khiển → Cử chỉ theo ứng dụng): chọn ứng dụng có biểu tượng khởi chạy rồi sửa bằng chính màn hình cử chỉ chung; chỉ phần khác bản đồ chung được lưu (tối đa 50 ứng dụng). Ứng dụng đang mở lấy từ cửa sổ ứng dụng thật, nên bàn phím hay thanh thông báo không làm mất cử chỉ riêng. Trình đơn vẫn dùng bản đồ chung.
- Không làm, có chủ đích: cử chỉ vân tay (phím cứng và phím âm lượng đã làm từ 2.12.0, xem mục đó) (ít máy hỗ trợ, thêm quyền), vùng chạm đi xuyên (cấu hình sai có thể khóa vùng chạm). Mỗi mục cần thử trên máy thật trước khi bật.
- Nhận diện nội dung web theo khả năng (có hành động duyệt phần tử HTML) thay vì chỉ theo tên lớp WebView, để giả lập chạm cũng áp dụng cho trình duyệt không dùng WebView. - **Tìm cài đặt** (ô tìm ở đầu màn hình chính): không phân biệt hoa thường và dấu ("thong bao" ra "Đọc thông báo"); tên cài đặt xếp trước mô tả. Chỉ mục nằm ở `SettingsIndex.kt` (tạo từ các màn hình); thêm cài đặt mới thì nhớ thêm chuỗi vào đó.
- **Màn Đọc chia nhóm** (Nội dung được đọc / Gợi ý và mức chi tiết / Chạm và gõ phím). Công tắc đọc thông báo chuyển hẳn sang màn **Đọc thông báo** để mỗi cài đặt chỉ có một nơi.
- **Đọc thông báo** (mục mới): không đọc khi ngón tay đang chạm màn hình, đang gọi, bật không làm phiền, đang phát nhạc (đều mặc định tắt) cùng danh sách "không bao giờ đọc" và "luôn đọc" (không phân biệt hoa thường/dấu; danh sách chặn ưu tiên hơn; "luôn đọc" bỏ qua cả công tắc chính). Không cần thêm quyền.
- **Sao lưu và khôi phục** (Nâng cao): lưu/khôi phục cài đặt, cử chỉ, cử chỉ theo ứng dụng và nhãn tùy chỉnh bằng bộ chọn tệp của hệ thống (không cần quyền lưu trữ). Chỉ nhận khóa và kiểu hợp lệ theo `BackupSchema` (khóa sai kiểu sẽ làm sập dịch vụ ở lần đọc sau nên bị bỏ qua); không gồm tiện ích mở rộng, nhật ký lỗi, công cụ giọng đọc.
- **Chạm thẳng theo ứng dụng** (Cử chỉ theo ứng dụng → chọn ứng dụng): kích hoạt bằng cú chạm thật vào giữa phần tử thay vì `ACTION_CLICK`, cho ứng dụng tự vẽ giao diện. Trạng thái màn Trạng thái cảnh báo khi một trình đọc màn hình khác đang bật cùng lúc.
- Chưa làm có chủ đích: vùng cho cử chỉ/chạm đi xuyên (`setTouchExplorationPassthroughRegion`, `setGestureDetectionPassthroughRegion`). Cấu hình sai có thể khiến người dùng không chạm được vùng đó, nên cần thử trên máy thật (bàn phím, thanh điều hướng, cạnh màn hình) trước khi bật.

- Chưa build/chạy test trong môi trường soạn thảo (không có Gradle/Kotlin); chạy `./gradlew :app:testDebugUnitTest` và thử trên máy thật, nhất là nhấn liên kết Google trong Chrome/WebView (khóa extras của Chromium không thuộc API công khai nên chỉ dùng để nhận diện thêm).

## Bản 2.10.0: sửa lỗi tồn đọng
Các lỗi tồn đọng sau khi rà soát:
- **`HighlightOverlay`**: `suspendCancellableCoroutine<Unit>` có kiểu tường minh; huỷ chờ khung hình dùng `Choreographer` đã giữ sẵn thay vì gọi `getInstance()` ở luồng không có Looper (có thể ném `IllegalStateException`).
- **Tự đọc chữ trong ảnh**: ảnh chỉ bị đánh dấu "đã đọc" SAU khi người dùng đã dừng đủ lâu và đã thử chụp. Trước đây đánh dấu ngay lúc chọn nên vuốt nhanh qua ảnh làm mất lần đọc. Bộ nhớ này được xóa khi đổi cửa sổ (`AiAssistant.onWindowChanged`) và an toàn đa luồng.
- **Chụp màn hình**: quá hạn 5 giây thì báo lỗi thay vì treo; ảnh đến muộn hoặc khi tác vụ đã bị hủy được `recycle()` (trước đây bị rò, mỗi ảnh vài MB).
- **Trình đơn bị đóng vì đổi cửa sổ** (bàn phím, hộp thoại nhỏ): nay báo "Đã đóng trình đơn" thay vì đóng im lặng.
- **Đọc theo đơn vị** (`moveUnit`): làm mới nút trước khi đọc; nút đã đổi thì báo "Phần tử đã thay đổi" (không còn đọc dữ liệu cũ).
- **Script áp dụng mọi ứng dụng**: lớp lưu trữ từ chối cài khi chưa xác nhận (`AllAppsConfirmationRequired`), không chỉ giao diện.
- **Chuỗi số lượng**: `sp_state_percent`, `script_err_depth`, `script_err_rules_count`, `lb_limits` chuyển sang `<plurals>` (tiếng Nga và Ả Rập đúng ngữ pháp). Test `QuantityStringsTest` buộc mọi chuỗi có số lượng mới phải là plurals.
- **Mục "Quyền"** (Cài đặt → Nâng cao): luồng xin quyền theo tính năng (`PermissionFlow`: giải thích trước, từ chối thì dừng, "không hỏi lại" thì có nút mở Cài đặt) và `rememberPermissionRequest`. **Hiện chưa có tính năng nào cần quyền** (đọc địa điểm gần bạn, tìm danh bạ, gọi điện, mô tả ảnh trong bộ nhớ: chưa làm) nên danh sách `PermissionFeatures.all` rỗng và manifest chỉ khai báo rung. `ManifestPermissionsTest` buộc quyền khai báo đúng bằng quyền mà tính năng cần.

Chưa sửa (cần quyết định): trình đơn chung có 7 mục + "Khác" = 8 mục ở cấp đầu (yêu cầu ghi "tối đa 7").

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
- Tới phần tử cuối/đầu của màn hình: app tự cuộn một lần rồi chọn phần tử mới; hết thật mới báo "phần tử cuối".
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
4. **Tắt trình đọc màn hình khác** trước khi dùng vì cả hai dùng chế độ khám phá bằng cảm ứng.
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
- Đơn vị đọc: đoạn → câu → từ → ký tự. Mặc định: ba ngón vuốt xuống = đơn vị kế, ba ngón vuốt lên = đơn vị trước, hai ngón chạm ba lần = đổi đơn vị. (Từ 2.20.0 ba ngón vuốt xuống/lên đổi điều khiển đọc và hai ngón chạm ba lần đọc từ phần tử đang chọn; "đơn vị kế/trước" và "đổi đơn vị đọc" còn trong trình đơn phần tử, và muốn ba ngón vuốt như cũ thì chọn kiểu "Đọc theo đơn vị và chuyển trang" trong mục Cử chỉ.) (Từ 2.19.0 "sao chép đơn vị đang đọc" không còn gán sẵn cho "lên rồi xuống": dùng trình đơn phần tử hoặc gán trong mục Cử chỉ; "lên rồi xuống" và "xuống rồi lên" nay đổi điều khiển đọc.)
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

**Mục "Quyền" trong ứng dụng.** Hiển thị theo từng tính năng cần quyền (tên, trạng thái "Đã cấp"/"Chưa cấp", nút mở đúng màn hình quyền của ứng dụng) và cập nhật khi quay lại từ Cài đặt hệ thống. Mở mục này không bao giờ tự xin quyền. Khi chưa có tính năng nào cần quyền, mục chỉ hiện lời giải thích. Luồng xin quyền: có quyền thì làm ngay; chưa có thì đọc lời giải thích rồi mới hiện hộp thoại hệ thống; bị từ chối thì đọc thông báo và kết thúc (không hỏi lại trong cùng một lần bấm); "không hỏi lại" thì kèm nút "Mở Cài đặt". Với gọi điện sẽ dùng `ACTION_DIAL` (không xin `CALL_PHONE`); với ảnh trong bộ nhớ sẽ dùng `PickVisualMedia` (không xin quyền đọc ảnh).

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
- Android Studio (bản hỗ trợ AGP 9.5): mở thư mục dự án, chờ sync, chọn *Build → Build APK(s)*.
- Dòng lệnh: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  (Gradle wrapper đã ghim Gradle 9.9.1 kèm checksum SHA-256).
- GitHub Actions: workflow `.github/workflows/build.yml` tự chạy test, lint và build APK mỗi lần push.
- **Bản release đã ký:** khóa KHÔNG nằm trong repo. `app/build.gradle.kts` chỉ ký khi đủ 4 biến môi trường
  `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` (thiếu thì ra APK
  chưa ký). Trên GitHub: tạo keystore (`keytool -genkeypair -v -keystore release.jks -alias key -keyalg RSA -keysize 4096 -validity 10000`),
  mã hóa `base64 -w0 release.jks`, lưu vào Secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
  `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, rồi chạy tay workflow **Build release** (`release.yml`) để lấy APK/AAB đã ký.
  Giữ kỹ keystore: mất khóa là không cập nhật được ứng dụng.

## Công nghệ (cập nhật 11/2026)
| Thành phần | Phiên bản |
|---|---|
| Android Gradle Plugin | 9.5.1 (Kotlin tích hợp sẵn, không còn plugin `kotlin-android`) |
| Gradle | 9.9.1 |
| Kotlin / Compose compiler plugin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 35 (Android 15+) |
| JDK | 17 trở lên (CI dùng 21) |

Phiên bản phụ thuộc nằm tập trung tại `gradle/libs.versions.toml`.

**Đối chiếu 13/11/2026 với trang phát hành chính thức:** Kotlin 2.4.21, Compose BOM 2026.09.00 (ui 1.12.1, Material3 1.4.0), core-ktx 1.19.1, activity-compose 1.13.0, lifecycle 2.11.0, coroutines 1.11.0, ML Kit (text-recognition 16.0.1, image-labeling 17.0.9), JUnit 4.13.2 (bản cuối của nhánh 4) và các action CI (checkout v7, setup-java v6, setup-android v4, setup-gradle v6, upload-artifact v7) đều là bản ổn định mới nhất. Cố ý **chưa** nâng: Compose 1.13.0-rc01 / Material3 1.5.0-rc01 (bản thử nghiệm; chờ BOM ổn định kế tiếp), activity 1.14 và lifecycle 2.12 (alpha), targetSdk 37 (Google Play mới yêu cầu API 36 từ 31/8/2026; nâng 37 cần thử hành vi mới trên máy thật), JUnit 6 (cần plugin ngoài cho AGP, không có lợi ích rõ).

## Yêu cầu
- Android 15 (API 35) trở lên, JDK 17 trở lên.
