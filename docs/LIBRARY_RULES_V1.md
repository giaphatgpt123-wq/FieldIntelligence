# Bộ quy tắc thư viện V1

Phạm vi: toàn bộ thư viện của FieldIntelligence / VN Sinh tồn. Quy tắc này chi phối nhập liệu, AI, giao diện, nhận dạng và phát hành dữ liệu.

## 1. Ưu tiên Việt Nam
Chỉ ưu tiên bề mặt chính cho đối tượng thường gặp hoặc có giá trị sử dụng thực tế tại Việt Nam. Dữ liệu toàn cầu vẫn có thể tồn tại ở lớp tra cứu sâu nhưng không được làm loãng màn hình chính.

## 2. Phân cấp mức sử dụng
Mỗi hồ sơ muốn xuất hiện trong vùng ưu tiên phải được gắn một trong năm mức đã tuyển chọn: Thường dùng, Hay dùng, Ít dùng, Hiếm dùng, Không có khả năng dùng. Nếu chưa có căn cứ thì giữ trạng thái Chưa phân loại; AI không được tự suy ra.

## 3. Tên Việt trước
Tên tiếng Việt đã đối chiếu phải đứng trước tên khoa học. Tên khoa học, authority và nguồn được đặt trong phần chi tiết/Xem thêm.

## 4. Ảnh là dữ liệu nhận biết
Ảnh phải đúng đối tượng và có nguồn/quyền sử dụng phù hợp. Không dùng ảnh trang trí làm ảnh nhận biết. Hồ sơ chưa có ảnh đã kiểm tra nguồn không được vào vùng ưu tiên.

## 5. Không gộp sai loài/biến thể
Các đối tượng có định danh khác nhau phải có hồ sơ riêng. Không gom bằng một tên phổ thông nếu việc gom làm mất khả năng phân biệt, thay đổi công dụng hoặc thay đổi cảnh báo an toàn.

## 6. Hồ sơ tối thiểu
Tên Việt đã đối chiếu, định danh/taxonomy có nguồn, ảnh đã kiểm tra nguồn và trạng thái kiểm chứng. Nếu có công dụng/cách dùng thì phải có nguồn chuyên ngành tương ứng. Đối tượng nguy cơ cao phải có nguồn an toàn chuyên ngành.

## 7. Hiển thị ngắn trước, sâu sau
Thứ tự mặc định: Tên Việt -> Ảnh -> Mức sử dụng -> Công dụng/cách dùng -> Nhận biết -> Dễ nhầm -> Cảnh báo -> Tên khoa học/nguồn.

## 8. Thu thập có chọn lọc
Không lấy số lượng làm mục tiêu duy nhất. AI chỉ hỗ trợ tìm, chuẩn hóa, phát hiện trùng, thiếu trường và mâu thuẫn nguồn; không tự công bố.

## 9. Hoàn thành đến đâu hiển thị đến đó
Hồ sơ đã đạt cổng phát hành phải xuất hiện ngay trong app theo từng đợt dữ liệu; không chờ hoàn thành toàn danh mục.

## 10. Kiểm chứng theo từng trường
Trạng thái: Chưa có -> Đã thu thập -> Đã đối chiếu -> Đã kiểm chứng -> Đã phát hành. Không dùng một dấu hoàn thành chung để che việc còn thiếu ảnh, tên Việt, công dụng hoặc nguồn an toàn.

## 11. An toàn ưu tiên cao nhất
Không suy ra ăn được, độc tính, liều dùng, chữa bệnh hoặc xử trí y khoa từ taxonomy. Hồ sơ nguy cơ cao thiếu bằng chứng an toàn phải bị chặn khỏi phát hành nội dung có thể gây hiểu nhầm.

## 12. Nhận dạng ảnh không phải xác nhận tuyệt đối
Khi bằng chứng không đủ, kết quả phải là CHƯA XÁC ĐỊNH/UNKNOWN hoặc danh sách ứng viên để so sánh. Không chuyển xác suất model thành khẳng định loài.

## 13. Chuẩn hóa và chống trùng
Một loài có thể có nhiều tên địa phương nhưng chỉ có một hồ sơ chuẩn; các tên phụ dùng để tìm kiếm.

## 14. Bảng tiến độ bắt buộc
Mỗi danh mục phải theo dõi ít nhất: mục tiêu (nếu đã đặt), đã thu thập, đã có ảnh đúng, đã đối chiếu tên Việt, đã kiểm chứng, đã phát hành. Không bịa mục tiêu khi chưa thống nhất.

## 15. Áp dụng chung toàn hệ thống
Rau, củ, quả, cây ăn quả, hoa, cây gỗ, cây thuốc, nấm, cá, côn trùng, động vật và các nhóm mở rộng đều dùng cùng cổng chất lượng. Nhóm nguy cơ cao có thêm điều kiện an toàn, không được nới lỏng.

## Cổng phát hành V1
Một hồ sơ chỉ được coi là đủ chuẩn phát hành khi: liên quan Việt Nam đã xác nhận; tên Việt đã đối chiếu; định danh có nguồn; ảnh đúng đối tượng đã kiểm tra; trạng thái tối thiểu Đã kiểm chứng. Nếu có tuyên bố công dụng/cách dùng phải có nguồn chuyên ngành. Nếu là hồ sơ nguy cơ cao phải có nguồn an toàn chuyên ngành.

Mã thực thi tương ứng: `feature/emergency/src/main/java/vn/fieldintel/feature/emergency/LibraryRules.kt`.
