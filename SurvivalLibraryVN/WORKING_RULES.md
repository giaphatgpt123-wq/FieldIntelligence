# BỘ QUY TẮC TRỌNG TÂM – THƯ VIỆN SINH TỒN

## 1. Quy tắc nội dung chính thức
Mỗi đối tượng trong bất kỳ danh mục nào chỉ tập trung vào 4 câu hỏi:

1. **Đây là gì?**
   - Tên tiếng Việt chính xác.
   - Tên gọi khác chỉ khi thực sự phổ biến.
   - Tên khoa học chỉ dùng để đối chiếu, không ưu tiên hiển thị.

2. **Thuộc danh mục gì?**
   - Xếp đúng một danh mục chính: Rau, Củ, Quả, Hoa, Cây gỗ, Cây ăn quả, Nấm, Cá, Côn trùng, Động vật, Cây thuốc, Nguy hiểm hoặc danh mục đã được thống nhất.
   - Không gộp tên mơ hồ nếu có nhiều loài khác nhau cần tách riêng.

3. **Ảnh nào giúp nhận biết, so sánh và tìm đúng đối tượng?**
   - Ảnh là thành phần bắt buộc.
   - Ưu tiên ảnh toàn thể và ảnh cận cảnh đặc điểm dễ nhận biết.
   - Thêm ảnh bộ phận quan trọng tùy đối tượng: lá, thân, hoa, quả, củ, đầu, vây, cánh, vỏ, hình dáng toàn thân...
   - Dùng nhiều góc khi cần để người dùng có thể nhìn ngoài thực tế rồi so sánh trong app.
   - Không dùng ảnh chỉ để trang trí; ảnh sai hoặc chưa chắc đúng đối tượng không được tính đạt chuẩn.

4. **Ứng dụng vào việc gì?**
   - Chỉ ghi ngắn gọn, thực tế và có căn cứ.
   - Ví dụ: ăn được; dùng làm thực phẩm; gia vị; cây thuốc; làm cảnh; lấy gỗ; thủy sản; có ích trong nông nghiệp; có nguy cơ gây hại; không có ứng dụng thực tế đáng kể; hoặc chưa đủ căn cứ xác định.

## 2. Cấu trúc hiển thị chuẩn
Màn hình chính của một hồ sơ chỉ ưu tiên:

**Tên đối tượng → Danh mục → Ảnh nhận biết → Ứng dụng chính**

Phần **Xem thêm** chỉ dùng cho tên khoa học, tên đồng nghĩa, nguồn kiểm chứng và thông tin phân loại bổ sung khi cần.

## 3. Không nạp thông tin dư thừa
Không ưu tiên thu thập hoặc hiển thị các nội dung không phục vụ trực tiếp 4 câu hỏi trên.

Ví dụ với danh mục Rau, không ưu tiên: kỹ thuật trồng, phân bón, tưới nước, sâu bệnh, năng suất, thời vụ, quy trình sản xuất hoặc hướng dẫn canh tác.

Quy tắc tương tự áp dụng cho mọi danh mục: không mở rộng sang nội dung chuyên môn không giúp người dùng nhận biết, phân loại hoặc hiểu ứng dụng thực tế của đối tượng.

## 4. Quy tắc nguồn và độ tin cậy
- Mọi tên, phân loại, ảnh và ứng dụng phải dựa trên nguồn có chứng cứ hoặc nguồn khoa học/chuyên ngành phù hợp.
- Không tự suy diễn từ trí nhớ nếu chưa có căn cứ.
- Nếu chưa chắc chắn, ghi rõ **Chưa đủ căn cứ** thay vì điền cho đủ.
- Ảnh phải kiểm đúng đối tượng và provenance/license khi cần sử dụng trong app.

## 5. Quy tắc AI quản lý
AI quản lý từng hồ sơ theo đúng 4 trường chính:

`NAME → CATEGORY → IDENTIFICATION_IMAGES → APPLICATION`

AI không được tự mở rộng hồ sơ sang dữ liệu ngoài phạm vi này trừ khi thông tin đó cần thiết để tránh nhận dạng sai hoặc tránh nguy hiểm trực tiếp.

Nếu một công việc kỹ thuật, dữ liệu hoặc nội dung không cải thiện một trong 4 trường trên thì không được coi là tiến độ nội dung chính.

## 6. Quy tắc phát hành
- Có bao nhiêu hồ sơ đạt chuẩn thì hiển thị bấy nhiêu; không chờ hoàn thiện toàn bộ danh mục.
- Hồ sơ chưa đủ tên đúng, danh mục đúng, ảnh nhận biết hoặc ứng dụng có căn cứ thì chưa được coi là hoàn chỉnh.
- Không hạ chuẩn chỉ để tăng số lượng.

## 7. Quy tắc báo cáo tiến độ
Khi báo cáo, ưu tiên:

1. Đã có thêm bao nhiêu loại.
2. Tên các loại là gì.
3. Loại nào đã có ảnh nhận biết đạt chuẩn.
4. Ứng dụng chính của từng loại.
5. Loại nào còn thiếu dữ liệu để hiển thị.

Không dùng PR, CI, branch, task count hoặc log kỹ thuật làm nội dung báo cáo chính trừ khi người dùng yêu cầu.

## 8. Nguyên tắc cuối cùng
Toàn bộ thư viện phải trả lời nhanh và rõ 4 câu hỏi:

**Đây là gì? → Thuộc nhóm nào? → Nhìn ảnh thế nào để nhận biết/tìm đúng? → Dùng vào việc gì?**

Thông tin không phục vụ 4 câu hỏi này không được ưu tiên load và không đưa lên giao diện chính.
