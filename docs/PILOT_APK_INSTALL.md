# Cài APK thử nghiệm OSM

APK debug dùng mã gói `vn.fieldintel.app.osmtest`, tách khỏi ứng dụng chính `vn.fieldintel.app` và bản thử nghiệm cũ `vn.fieldintel.app.pilot`. Có thể cài cạnh các bản này mà không gỡ chúng hay xóa dữ liệu đã lưu. Dữ liệu giữa các gói không tự đồng bộ.

Bản thử nghiệm dùng khóa ký cố định tại `app/pilot-test-signing.p12.b64` để các bản `.osmtest` tiếp theo có thể cập nhật nhau. Khóa và mật khẩu trong kho là danh tính thử nghiệm, không được xem là bí mật. Tuyệt đối không dùng khóa này cho bản phát hành, hoặc ký gói `vn.fieldintel.app` / `vn.fieldintel.app.pilot`.

Khi cài một APK mới, kiểm tra tên gói và chứng chỉ ký trước khi phát hành artifact. Nếu thiết bị vẫn báo xung đột, ghi lại nguyên văn thông báo hoặc kết quả `adb install -r <apk>` để xác định nguyên nhân; không gỡ ứng dụng đang giữ dữ liệu thực địa.
