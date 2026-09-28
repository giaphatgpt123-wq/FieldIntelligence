# Tiến độ danh mục thư viện

Bảng **Xem tiến độ nhập liệu** trong thư viện đọc toàn bộ `LibraryCollections.items`.
Vì vậy mỗi danh mục được hiển thị đúng một dòng; một loài thuộc nhiều danh mục
được tính ở từng dòng, không cộng các dòng thành tổng loài duy nhất.

- **Bản ghi phân loại** (Thực vật WFO) là nguồn tra tên khoa học, không phải hồ sơ hoàn chỉnh.
- **Hồ sơ gắn nhãn** là số hồ sơ có thể mở theo danh mục; không chứng minh ảnh đúng,
  công dụng hay cách dùng.
- **Ảnh offline** chỉ đếm các ảnh mà bộ lưu ảnh cục bộ quan sát được. Không xem ảnh
  tham chiếu là bằng chứng nhận dạng cá thể ngoài thực địa.
- Những chỉ số chưa có bằng chứng được ghi **chưa đo**. Chưa có chỉ tiêu nên không tính %.

## Nạp Hoa, Cây gỗ, Cây ăn quả

1. Thu thập theo từng loài; giữ nguyên tên khoa học, tên Việt, nguồn trong nước,
   nguồn phân loại, xuất xứ và quyền sử dụng ảnh. Loài dễ nhầm phải tách hồ sơ.
2. Đối chiếu thủ công và điền `data/reviewed-collections.csv` theo các cột
   `collection_id,scientific_name,vietnamese_name,source_url,reviewed_by`.
   Các ID hợp lệ: `flowers`, `timber-trees`, `fruit-crops`.
3. CI tạo taxonomy SQLite, ghép các gói cá/nấm/dược liệu, rồi chạy
   `tools/attach_reviewed_collections.py`. Bản ghi chỉ vào danh mục khi tên khoa
   học trùng **chính xác một** bản ghi Thực vật đã có trong gói. Dòng thiếu nguồn
   HTTPS, người duyệt, hoặc sai tên làm cả đợt thất bại; dữ liệu cũ vẫn nguyên.
4. Gói SQLite qua cơ chế cập nhật Wi-Fi hiện có: kiểm kích thước, SHA-256,
   schema, tính toàn vẹn và liên kết danh mục trước khi thay thế. Trên máy,
   danh mục được nạp lại khi tệp cơ sở dữ liệu đổi. Không cần cài lại APK
   sau khi bản app chứa tính năng này đã được cài.
5. Ảnh cần quy trình license/đối chiếu riêng; công dụng và cách dùng cần nguồn
   chuyên ngành riêng. Không chuyển dữ liệu phân loại thành hướng dẫn sử dụng.

Tệp CSV hiện chỉ có tiêu đề. **Chưa có đợt hồ sơ mới được duyệt để nạp**;
các hồ sơ lõi đã có trong mã nguồn vẫn hiển thị. Cây gỗ đang có 0 hồ sơ lõi.
