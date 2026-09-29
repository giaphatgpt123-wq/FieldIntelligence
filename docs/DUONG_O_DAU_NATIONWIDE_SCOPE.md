# Đường ở đâu — Nationwide Data Scope

## Nguyên tắc

Dữ liệu được thu thập và lập chỉ mục cho **toàn quốc**, nhưng không chạy toàn bộ luồng nặng cùng lúc.

### 1. Nationwide cold index
Luôn có thể tra cứu:
- 34 tỉnh/thành;
- 3.321 xã/phường/đặc khu;
- mã hành chính;
- alias cũ/mới;
- manifest gói dữ liệu;
- chỉ mục tên/mã tuyến mức nhẹ.

Phần này nằm local/on-disk và không kích hoạt Road Live hay Road Discovery toàn quốc.

### 2. Chọn tỉnh
Khi chọn một tỉnh:
- giữ metadata tỉnh và danh sách xã hoạt động;
- có thể hiển thị ranh tỉnh ở mức nhẹ;
- **không** tự bật Road Discovery/Road Live cho toàn bộ xã trong tỉnh;
- các tỉnh khác OFF ở lớp dữ liệu nặng.

### 3. Chọn xã
Khi chọn một xã:
- xã được chọn = `ACTIVE_FULL`;
- các xã có chung ranh giới trực tiếp = `ACTIVE_EDGE`;
- mọi xã khác = `OFF`.

`ACTIVE_FULL` gồm:
- Road Graph;
- Road Attributes;
- cầu/phà/đò/hầm;
- Road Live;
- Road Discovery;
- Search Index chi tiết;
- Route Guard.

`ACTIVE_EDGE` chỉ giữ:
- ranh giới;
- road graph gần biên;
- đường/cầu/phà ảnh hưởng liên thông qua biên;
- Road Live liên quan biên;
- search index mức biên.

Road Discovery ở xã giáp ranh mặc định OFF để tránh tải nền không cần thiết.

### 4. Xác định xã giáp ranh
Không xác định bằng tên. Quan hệ giáp ranh phải sinh từ polygon hành chính:
- `TOUCHES` / shared-boundary;
- kiểm tra topology;
- lưu `source_id`, `confidence`, `effective_from`.

Nếu ranh hành chính thay đổi, adjacency phải được tạo lại theo phiên bản địa giới mới.

### 5. Route Corridor exception
Chỉ khi người dùng yêu cầu hành trình ra ngoài scope hiện tại, app mới tạm bật `ROUTE_CORRIDOR` cho các xã mà tuyến đi qua.

Không bật toàn tỉnh/toàn quốc. Scope corridor phải tự đóng sau khi hành trình kết thúc hoặc TTL hết hạn.

### 6. Mục tiêu hiệu năng
- Toàn quốc: searchable/indexed.
- Tỉnh đang chọn: metadata focus.
- Xã đang chọn: full detail.
- Xã giáp ranh: edge detail.
- Phần còn lại: OFF/cold storage.

Mục tiêu là giữ app nhẹ, giảm RAM/CPU/network/I/O nhưng không đánh mất khả năng tra cứu toàn quốc.
