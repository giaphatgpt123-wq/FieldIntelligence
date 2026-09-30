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

## Nạp Rau, Hoa, Cây gỗ, Cây ăn quả

1. Thu thập theo từng loài; giữ nguyên tên khoa học, tên Việt, nguồn trong nước,
   nguồn phân loại, xuất xứ và quyền sử dụng ảnh. Loài dễ nhầm phải tách hồ sơ.
2. Đối chiếu thủ công và điền `data/reviewed-collections.csv` theo các cột
   `collection_id,scientific_name,vietnamese_name,source_url,reviewed_by`.
   Các ID hợp lệ: `vegetables`, `flowers`, `timber-trees`, `fruit-crops`.
   Mỗi đợt có thể thêm 1, 50 hoặc nhiều loài; giữ các dòng đã duyệt từ đợt trước.
   Nếu có chỉ tiêu thực tế, điền `target_count` vào
   `data/reviewed-collection-goals.csv`. Không dùng số ví dụ làm chỉ tiêu thật.
3. CI tạo taxonomy SQLite, ghép các gói cá/nấm/dược liệu, rồi chạy
   `tools/attach_reviewed_collections.py`. Bản ghi chỉ vào danh mục khi tên khoa
   học trùng **chính xác một** bản ghi Thực vật đã có trong gói. Dòng thiếu nguồn
   HTTPS, người duyệt, hoặc sai tên làm cả đợt thất bại; dữ liệu cũ vẫn nguyên.
4. Mỗi đợt sinh gói SQLite riêng và kiểm kích thước, SHA-256, schema, tính
   toàn vẹn và liên kết danh mục trước khi thay thế. Trên máy, bảng tiến độ
   hiển thị số **đã nạp / mục tiêu** ngay sau khi cài, còn danh sách loài cho
   xem theo từng trang 80 hồ sơ. Không cần đợi đủ mục tiêu.
   Kho phát hành hiện là riêng tư và app không chứa thông tin đăng nhập, nên
   cập nhật Wi-Fi tự động chưa hoạt động. Người dùng chọn **NẠP GÓI DỮ LIỆU MỚI**
   ngay trong Thư viện để cài ZIP đã được cung cấp; không cài lại APK sau khi
   bản app chứa tính năng này được cài.
5. Ảnh cần quy trình license/đối chiếu riêng; công dụng và cách dùng cần nguồn
   chuyên ngành riêng. Không chuyển dữ liệu phân loại thành hướng dẫn sử dụng.
6. Với **Rau**, bản ghi đã đối chiếu tên/định danh có thể vào lớp reviewed để theo
   dõi và nạp theo đợt, nhưng **không đồng nghĩa đã đạt cổng phát hành V1**. Hồ sơ
   chỉ được đưa vào vùng ưu tiên khi ảnh, mức sử dụng, công dụng/cách dùng và cảnh
   báo an toàn (nếu có) đạt đúng điều kiện trong `docs/LIBRARY_RULES_V1.md`.

`data/reviewed-collections.csv` hiện đã có các hồ sơ được duyệt cho Hoa, Cây gỗ và
Cây ăn quả. Rau được mở pipeline từ P2; chỉ thêm từng hồ sơ sau khi nguồn trong nước
và định danh đã được đối chiếu. Cây gỗ lõi vẫn có thể là 0 nếu chưa có hồ sơ starter.
