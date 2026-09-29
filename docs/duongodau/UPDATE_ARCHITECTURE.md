# Đường ở đâu — kiến trúc cài đặt, cập nhật và liên thông

## Mục tiêu

- Một package stable duy nhất: `vn.duongodau.app`.
- Debug dùng package riêng `vn.duongodau.app.dev`, không cài đè stable.
- Mọi stable release dùng cùng signing key alias `duongodau`.
- `versionCode` bắt buộc tăng dần.
- Updater chỉ mở trình cài sau khi xác minh package, SHA-256 và signing certificate.
- Không dùng repository-wide `latest` vì repo còn chứa release của ứng dụng khác.

## Kênh phát hành

Updater stable đọc manifest từ tag cố định `duongodau-stable`:

- `duong-o-dau.apk`
- `duongodau-update.json`

Mỗi phiên bản còn có archive bất biến `duongodau-v<version>`.

## Chuỗi cập nhật

1. Kiểm manifest qua HTTPS.
2. Thử lại tối đa 3 lần nếu mạng lỗi.
3. Tải vào `.apk.part`.
4. Kiểm đủ dữ liệu/Content-Length nếu server cung cấp.
5. Đổi sang `.apk` chỉ khi tải xong.
6. Kiểm SHA-256.
7. Kiểm package = `vn.duongodau.app`.
8. Kiểm certificate SHA-256 đúng kênh stable.
9. Kiểm xung đột chữ ký với bản stable đang cài.
10. Mở Android package installer bằng FileProvider.

Nếu bản P0 cũ cùng package nhưng ký khác, ứng dụng báo rõ và chỉ yêu cầu gỡ một lần. Các bản stable sau đó cài đè bình thường nếu cùng signing key và `versionCode` tăng.

## Liên thông app khác

Ứng dụng nhận:

- `duongodau://road?...`
- `geo:` intents
- Android Share `text/plain`
- Google Maps URL
- Waze URL
- tọa độ dạng văn bản

Dữ liệu nhận được chuẩn hóa thành `label + latitude + longitude + source` trước khi sử dụng. Khi mở app ngoài, luồng dùng native/generic handler trước và web fallback sau.

## Trạng thái ký stable

Workflow build có gate `Stable signing readiness`, chỉ báo trạng thái mà không lộ secrets. Stable release workflow từ chối chạy nếu thiếu hoặc sai signing secrets.

Secrets bắt buộc:

- `DUONGODAU_KEYSTORE_B64`
- `DUONGODAU_STORE_PASSWORD`
- `DUONGODAU_KEY_PASSWORD`

Private keystore tuyệt đối không commit vào repository.
