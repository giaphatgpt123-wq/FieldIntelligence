# BỘ QUY TẮC TRỌNG TÂM – THƯ VIỆN SINH TỒN

## 1. Mục tiêu cao nhất
Xây dựng Thư viện sinh tồn có dữ liệu thật, dễ hiểu bằng tiếng Việt, có hình ảnh nhận biết rõ, nội dung hữu ích và được hiển thị trực tiếp trong ứng dụng. Mọi công việc kỹ thuật chỉ là phương tiện phục vụ mục tiêu này.

## 2. Không lệch sang kỹ thuật nền
Không biến CI, GitHub, PR, branch, workflow, Data Engine, log hoặc test thành nội dung chính. Chỉ xử lý và báo kỹ thuật khi nó làm dữ liệu không nạp/hiển thị được, gây sai dữ liệu, gây lỗi ứng dụng hoặc ảnh hưởng trực tiếp đến chất lượng thư viện.

## 3. Caravan/Canary chỉ là kiểm chứng
Bộ 12 hồ sơ ban đầu chỉ dùng để kiểm tra cấu trúc, luồng nạp, hiển thị và quy tắc chất lượng. Sau khi đạt yêu cầu phải chuyển sang nạp dữ liệu thật với số lượng lớn, không tiếp tục xoay quanh 12 hồ sơ.

## 4. Nạp theo từng danh mục
Mỗi danh mục được quản lý độc lập: Rau, Củ, Quả, Hoa, Cây gỗ, Cây ăn quả, Cây thuốc, Nấm, Cá nước ngọt, Sinh vật biển, Côn trùng, Động vật, Loài nguy hiểm và các nhóm đã thống nhất khác. Không nạp hỗn hợp không kiểm soát.

## 5. Ưu tiên loài thường gặp tại Việt Nam
Thứ tự: thường dùng/hay gặp → khá thường gặp → ít gặp → hiếm → gần như không có khả năng sử dụng hoặc gặp. Không chạy theo “càng nhiều loài càng tốt” nếu dữ liệu ít giá trị thực tế.

## 6. Tiếng Việt là lớp hiển thị chính
Ưu tiên: tên tiếng Việt → ảnh → công dụng/cách dùng → cảnh báo → thông tin bổ sung. Tên khoa học và nội dung chuyên sâu để trong phần mở rộng hoặc “Xem thêm”.

## 7. Không gom nhiều loài vào tên chung
Phải tách đúng loài/biến thể thực tế, ví dụ nghệ vàng, nghệ đen; cá rô đồng và các loài khác; các loại nấm, rau, củ, quả phải có hồ sơ riêng khi có nguy cơ nhầm lẫn.

## 8. Ảnh là thành phần bắt buộc
Ảnh phục vụ nhận biết thực tế, không chỉ trang trí. Ưu tiên toàn thể; cận cảnh đặc điểm quan trọng; lá/thân/rễ/quả/hoa khi cần; con non/con trưởng thành khi khác biệt; nhiều góc nhìn; ảnh dễ so sánh ngoài thực địa.

## 9. Không dùng ảnh sai hoặc không chắc chắn
Ảnh chưa chắc đúng loài không được coi là ảnh chuẩn. Ảnh đẹp nhưng có nguy cơ nhầm loài không được ưu tiên hơn ảnh xác định đúng.

## 10. Nội dung hồ sơ phải hữu dụng
Mỗi hồ sơ hướng tới tối thiểu: tên tiếng Việt, tên khoa học, danh mục, ảnh nhận biết, đặc điểm nhận dạng, nơi thường gặp, mức độ thường gặp/sử dụng, công dụng, cách dùng nếu phù hợp, cảnh báo, loài dễ nhầm và nguồn kiểm chứng.

## 11. Nhóm nguy hiểm áp dụng chuẩn cao hơn
Cây độc, nấm độc, rắn, côn trùng nguy hiểm, sinh vật biển nguy hiểm và loài có thể gây ngộ độc/chấn thương phải có thêm: dấu hiệu nhận biết, loài dễ nhầm, mức độ nguy hiểm, điều không được làm, xử trí ban đầu và khuyến cáo hỗ trợ y tế khi phù hợp. Không tự động xuất bản nội dung an toàn chưa kiểm chứng.

## 12. Dữ liệu đạt đến đâu hiển thị đến đó
Không chờ cả thư viện hoàn tất. Có bao nhiêu hồ sơ đạt chuẩn thì hiển thị bấy nhiêu trên app và cập nhật theo từng đợt nhỏ.

## 13. Ba trạng thái bắt buộc
Mỗi hồ sơ thuộc một trong ba trạng thái: Đang thu thập → Đủ dữ liệu nhưng đang kiểm tra → Đạt chuẩn và hiển thị. “Đã thu thập” không đồng nghĩa “được phép hiển thị”.

## 14. Bảng tiến độ phải phản ánh thư viện thật
Theo từng danh mục phải có tối thiểu: tổng hồ sơ dự kiến, đã thu thập, đủ tên tiếng Việt, đủ ảnh, đủ nội dung, đã kiểm chứng, đang bị chặn, đã hiển thị trên app, tỷ lệ hoàn thành. Không chỉ báo số task kỹ thuật.

## 15. Không chạy số lượng bằng cách hạ chuẩn
Không giảm số ảnh, chấp nhận nguồn yếu, bỏ cảnh báo, bỏ kiểm tra loài dễ nhầm hoặc tự đánh dấu hoàn thành chỉ để tăng số lượng.

## 16. Mỗi đợt mở rộng phải có kết quả nhìn thấy được
Sau mỗi đợt phải trả lời được: thư viện tăng thêm bao nhiêu hồ sơ đạt chuẩn; danh mục nào được cải thiện; người dùng mở app sẽ nhìn thấy thêm gì.

## 17. Kỹ thuật phải phục vụ khả năng mở rộng
Hệ thống phải giúp nạp nhiều dữ liệu hơn, cập nhật từng phần, tránh cài lại app mỗi khi dữ liệu thay đổi, chống trùng lặp, giữ nguồn/lịch sử, kiểm soát chất lượng và không làm chậm app khi thư viện lớn.

## 18. Quy tắc báo cáo tiến độ
Thứ tự ưu tiên: dữ liệu đã tăng → dữ liệu đang thiếu → dữ liệu đã hiển thị → lỗi ảnh hưởng thực tế → hành động tiếp theo. Không mở đầu bằng commit/SHA/PR/log CI trừ khi người dùng yêu cầu.

## 19. Quy tắc chống lệch trọng tâm
Trước mỗi bước phải tự hỏi: “Việc này có làm thư viện nhiều hơn, đúng hơn, dễ nhận biết hơn hoặc hiển thị tốt hơn không?”. Nếu không, phải dừng hoặc chuyển về nhiệm vụ chính.

## 20. Nguyên tắc cuối cùng
Chất lượng hồ sơ + khả năng nhận biết thực tế + giá trị sử dụng cho người Việt quan trọng hơn số lượng dữ liệu, kỹ thuật nền hoặc báo cáo đẹp.
