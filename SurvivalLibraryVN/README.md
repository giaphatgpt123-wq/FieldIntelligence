# SurvivalLibraryVN

Ứng dụng Android mới: **Thư viện Sinh tồn Việt Nam**.

## Tách biệt khỏi FieldIntelligence cũ
- applicationId mới: `vn.survivallibrary.app`
- có thể cài song song với `vn.fieldintel.app`
- không dùng Map/GPS
- không kế thừa UI cũ
- không coi dữ liệu demo là dữ liệu thư viện thật

## Luồng V1
Trang chủ sinh động -> Thư viện -> Quét nhận dạng -> Hồ sơ loài -> Đã lưu.

## Nguyên tắc thư viện
- tiếng Việt và ảnh nhận biết đặt trước
- mức dùng: Thường dùng / Hay dùng / Ít dùng / Hiếm dùng / Không có khả năng dùng / Chưa phân loại
- dữ liệu chỉ phát hành khi vượt cổng kiểm chứng
- AI không tự suy ra ăn được, độc tính, công dụng hoặc mức phổ biến từ taxonomy
- hồ sơ nguy cơ cao cần nguồn an toàn chuyên ngành
- nhận dạng phải hỗ trợ UNKNOWN khi chưa đủ bằng chứng

## Trạng thái hiện tại
Khung Android độc lập đã có UI V1 và bộ quy tắc thực thi. Các thẻ hồ sơ hiện tại là **dữ liệu mẫu giao diện**, được ghi nhãn rõ trong app. Camera/model và pipeline dữ liệu thật sẽ được nối ở các mốc tiếp theo sau khi build cơ sở ổn định.
