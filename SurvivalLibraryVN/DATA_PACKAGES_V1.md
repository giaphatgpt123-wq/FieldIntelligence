# Kiến trúc gói dữ liệu offline V1

Mục tiêu của lớp này là tách **dữ liệu thư viện** khỏi APK. APK giữ giao diện, bộ quy tắc, cơ sở dữ liệu và trình cài gói. Nội dung thư viện được cập nhật theo gói riêng để tránh phải cài lại ứng dụng mỗi khi bổ sung hồ sơ.

## Nguyên tắc

1. Không có hồ sơ thật nào được APK tự coi là đã kiểm chứng.
2. Mỗi gói phải có `packageId`, `version`, `schemaVersion`, số hồ sơ, số hồ sơ đã kiểm chứng, SHA-256 và nguồn HTTPS.
3. Gói chỉ được nhận nếu vượt kiểm tra cấu trúc trước khi cài.
4. Hồ sơ bên trong gói vẫn phải vượt `LibraryRules`; việc gói hợp lệ không đồng nghĩa mọi hồ sơ được phép phát hành.
5. Ảnh, nguồn và trạng thái kiểm chứng được lưu tách theo từng hồ sơ.
6. Không dùng số mục tiêu hoặc số hồ sơ giả để làm đẹp tiến độ.
7. Cập nhật dữ liệu phải có khả năng thay thế theo phiên bản mà không đổi package Android hoặc chữ ký APK.

## Các gói lõi

- `plants-core`: rau, củ, quả, hoa, cây gỗ, cây thuốc.
- `mushrooms-core`: nấm.
- `aquatic-core`: cá nước ngọt, cá biển, thủy sản.
- `fauna-core`: động vật, côn trùng, nhóm nguy hiểm.
- `skills-core`: kỹ năng sinh tồn dùng offline.

## Cơ sở dữ liệu

Database V2 bổ sung:

- `content_packages`: phiên bản, trạng thái, checksum, nguồn và số hồ sơ của từng gói.
- `record_sources`: nguồn kiểm chứng theo hồ sơ.
- `field_verification`: trạng thái kiểm chứng theo từng trường dữ liệu, tránh một dấu hoàn thành chung cho cả hồ sơ.
- `record_media`: ảnh/video nhận biết, checksum và trạng thái xác minh.

Migration từ database V1 sang V2 là không phá hủy; mục đã lưu của người dùng được giữ nguyên.

## Luồng cài gói dự kiến

`Tải manifest -> kiểm tra packageId/schema/HTTPS/SHA-256 -> tải gói -> kiểm checksum -> đọc hồ sơ -> chạy LibraryRules -> transaction SQLite -> cập nhật content_packages -> app nhìn thấy dữ liệu mới`

Ở mốc hiện tại mới hoàn thành **nền tảng lưu trữ + cổng kiểm tra manifest**. Chưa bật tải gói từ Internet và chưa nạp dữ liệu sinh tồn thật.
