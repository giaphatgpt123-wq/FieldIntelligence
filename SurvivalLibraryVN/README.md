# SurvivalLibraryVN

Ứng dụng Android mới: **Thư viện Sinh tồn Việt Nam**.

## Tách biệt khỏi FieldIntelligence cũ
- applicationId beta ổn định từ v0.2.1: `vn.survivallibrary.vn`
- namespace mã nguồn: `vn.survivallibrary.app`
- không dùng Map/GPS
- không kế thừa UI cũ
- dữ liệu demo không được coi là dữ liệu thư viện thật

## Luồng hiện tại
Trang chủ sinh động -> Danh mục -> Quét ảnh -> Hồ sơ -> Đã lưu -> Tiến độ thư viện.

## Quy tắc dữ liệu
- tiếng Việt và ảnh nhận biết đặt trước
- mức dùng: Thường dùng / Hay dùng / Ít dùng / Hiếm dùng / Không có khả năng dùng / Chưa phân loại
- hồ sơ chỉ phát hành khi vượt cổng kiểm chứng
- AI không tự suy ra ăn được, độc tính, công dụng hoặc mức phổ biến từ taxonomy
- hồ sơ nguy cơ cao cần nguồn an toàn chuyên ngành
- nhận dạng phải hỗ trợ UNKNOWN khi chưa đủ bằng chứng
- dữ liệu hoàn thành đến đâu có thể phát hành đến đó; không chờ đủ toàn danh mục

## Bảng tiến độ
Mỗi danh mục theo dõi: mục tiêu (nếu đã đặt), đã thu thập, có ảnh đúng, tên Việt đã đối chiếu, đã kiểm chứng và đã phát hành. Mục tiêu chưa xác định phải để trống, không bịa số.

## Cơ chế cài đặt beta từ v0.2.1
Các APK v0.1.0 và v0.2.0 trước đây dùng chữ ký debug phát sinh theo từng CI runner, có thể gây lỗi xung đột khi cài đè. Từ v0.2.1:
- dùng package beta mới `vn.survivallibrary.vn` để không va chạm với gói beta cũ;
- GitHub Release ký APK bằng cùng một AOSP development test certificate qua mọi lần build;
- APK phát hành được kiểm tra chữ ký và package trước khi upload.

Cơ chế ký này **chỉ dành cho beta/test**, không dùng làm khóa phát hành production hoặc Play Store.

## Trạng thái v0.2.1
Đã có UI mới sinh động hơn, 12 danh mục lõi, bảng tiến độ, bộ lọc mức sử dụng và màn hình camera chuẩn bị cho AI offline. Các thẻ hiện tại vẫn là **dữ liệu mẫu giao diện** và được ghi nhãn DEMO rõ ràng. Model nhận dạng thật và pipeline dữ liệu khoa học sẽ được nối sau khi nền UI/data này ổn định.
