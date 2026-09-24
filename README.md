# Field Intelligence — P0 Emergency Core

Ứng dụng Android offline-first cho thực địa, sinh tồn và sự cố.

## P0 hiện có
- `:domain:emergency`: Protocol Graph, Validator, deterministic Protocol Runner.
- `:data:emergency`: Room `emergency.db` nền tảng.
- `:feature:emergency`: giao diện Emergency tối giản.
- `:app`: Android launcher.
- Unit-test fixtures cho protocol hợp lệ và protocol bị thu hồi.
- GitHub Actions: test + build Debug APK + lưu artifact.

## Nguyên tắc an toàn
Emergency Core độc lập với AI nhận dạng. Protocol y khoa thực tế chỉ được kích hoạt sau khi có nguồn có thẩm quyền, version, review và validation. Recognition failure không được làm Emergency failure.

## Trạng thái
Source P0 đã tạo. CI sẽ xác nhận khả năng biên dịch; chưa được xem là FIELD-READY cho đến khi vượt kiểm thử thiết bị, recovery, GNSS, Golden Pack và fault injection.
