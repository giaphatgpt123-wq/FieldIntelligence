# VEGETABLES AI CONTENT PILOT V1

## Mục tiêu

AI quản lý danh mục Rau theo `WORKING_RULES.md`. AI không được xem việc thu thập xong là hoàn thành hồ sơ. Đơn vị kiểm soát nhỏ nhất là **một claim / một field / một nguồn**.

## Luồng quản lý bắt buộc

1. **Coverage Planner**
   - Lập danh sách rau ưu tiên tại Việt Nam.
   - P1: thường gặp/thường dùng; P2: khá thường; P3: ít/hiếm.
   - Tên nhóm mơ hồ như `rau dền`, `rau đay`, `cải` phải tách đến taxon/cultivar phù hợp trước khi tạo hồ sơ.

2. **Identity Resolver**
   - Xác nhận tên khoa học được chấp nhận, họ, synonym, tên Việt.
   - Ưu tiên Kew/WFO/GBIF cho taxonomy; nguồn Việt Nam chính thức/chuyên ngành cho tên Việt.
   - Không dùng tên seed làm bằng chứng.

3. **Evidence Collector**
   - Thu theo field độc lập: morphology, distribution, agronomy, edible parts, culinary use, nutrition, food safety, media.
   - Nguồn ưu tiên: cơ quan Việt Nam > FAO/WHO/Kew/GBIF/WFO > bài báo khoa học > nguồn mở có provenance.
   - Wikipedia/Wikimedia chỉ được dùng đúng vai trò: tham khảo/ảnh có license; không làm nguồn duy nhất cho claim an toàn, dinh dưỡng hoặc kỹ thuật canh tác.

4. **Claim Normalizer**
   - Chuẩn hóa tiếng Việt trước.
   - Mỗi claim lưu `sourceId`, phạm vi địa lý, ngày nguồn, mức tin cậy và giới hạn áp dụng.
   - Không chuyển số liệu địa phương thành kết luận toàn quốc.

5. **Conflict Resolver**
   - Nếu nguồn mâu thuẫn: giữ cả hai, đánh dấu conflict và chặn publish field.
   - Taxonomy ưu tiên authority mới hơn; kỹ thuật canh tác ưu tiên tài liệu chính thức phù hợp vùng/giống.
   - Không lấy trung bình hai số liệu khác điều kiện thí nghiệm.

6. **Media Curator**
   - Rau cần ít nhất 5 ảnh chẩn đoán đã xác minh.
   - Mặc định vai trò: WHOLE, LEAF, STEM, FLOWER/FRUIT, HABITAT; thay đổi theo loài khi cần.
   - Kiểm đúng loài, license, creator, source URI, checksum; không tính ảnh trùng là đa góc.

7. **Nutrition Curator**
   - Ưu tiên Bảng thành phần thực phẩm Việt Nam/Viện Dinh dưỡng/FAO INFOODS.
   - Không suy ra hàm lượng dinh dưỡng từ loài gần giống.
   - Không điền số nếu chỉ biết có bảng nhưng chưa trích đúng dòng thực phẩm.

8. **Food Safety Gate**
   - Tách `nguy cơ do bản thân loài` khỏi `nguy cơ do sản xuất/nước/vi sinh/thuốc BVTV`.
   - Claim sức khỏe, chữa bệnh hoặc độc tính yêu cầu nguồn chuyên ngành phù hợp.
   - Bằng chứng địa phương phải hiển thị phạm vi và không được ngoại suy.

9. **Publication Readiness**
   - `COLLECTING`: còn thiếu field nền.
   - `REVIEW`: có dữ liệu nhưng còn field chưa verified/xung đột.
   - `READY`: đủ field bắt buộc + evidence + media.
   - `PUBLISHED`: chỉ sau LibraryRules và package validator.
   - AI không được tự hạ gate để tăng số lượng.

10. **Continuous Monitor**
    - Theo dõi taxonomy, nguồn mới, thay đổi tiêu chuẩn an toàn và dữ liệu canh tác.
    - Nguồn mới không được tự ghi đè claim cũ; tạo revision, so sánh và tái đánh giá.

## Ma trận nguồn theo field

| Field | Nguồn ưu tiên | Tự verify? |
|---|---|---|
| Taxonomy | Kew/WFO/GBIF | Có nếu exact accepted match |
| Tên Việt | Bộ/Sở/Viện VN, tài liệu chuyên ngành VN | Có nếu curated + provenance |
| Phân bố Việt Nam | cơ quan VN + Kew/GBIF occurrence | Có điều kiện |
| Hình thái nhận biết | Flora/Kew/WFO/tài liệu chuyên ngành | Chỉ khi claim có nguồn |
| Canh tác | Bộ NN, Cục, Viện Nghiên cứu Rau quả, Khuyến nông | Không auto nếu khác vùng/giống |
| Dinh dưỡng | Bộ Y tế/Viện Dinh dưỡng/FAO INFOODS | Chỉ đúng food item/100 g basis |
| Công dụng/cách ăn | tài liệu thực phẩm/chuyên ngành | Không dùng folklore đơn độc |
| An toàn thực phẩm | Bộ Y tế, Cục ATTP, cơ quan nông nghiệp, nghiên cứu phù hợp | Không auto ngoài phạm vi nguồn |
| Ảnh | nguồn khoa học/open media có license | Phải kiểm license + taxon + role |

## Pilot Rau v1

- Danh sách khởi tạo: 20 đối tượng ưu tiên.
- Hồ sơ chuẩn đầu tiên: Rau muống (`Ipomoea aquatica Forssk.`).
- Mồng tơi là hồ sơ thứ hai để kiểm tra khả năng tái sử dụng quy trình.
- Các tên mơ hồ (`rau dền`, `rau đay`, một số nhóm cải) bị chặn ở bước identity cho tới khi tách đúng loài/cultivar.

## KPI mà AI phải báo

Không báo số task kỹ thuật làm KPI chính. Báo theo thứ tự:

1. số hồ sơ có tên Việt + taxonomy đã kiểm chứng;
2. số hồ sơ đủ 5+ ảnh chẩn đoán;
3. số hồ sơ đủ nội dung nhận biết;
4. số hồ sơ đủ canh tác;
5. số hồ sơ đủ dinh dưỡng;
6. số hồ sơ đủ an toàn;
7. số hồ sơ READY;
8. số hồ sơ PUBLISHED và người dùng thực sự nhìn thấy trên app;
9. blocker theo field, không chỉ lỗi pipeline.

## Nguyên tắc quyết định

AI chỉ được tăng tiến độ khi **chất lượng hồ sơ thực sự tăng**. Nếu một hành động chỉ làm CI xanh, tăng task count hoặc tăng số record mà không tăng độ đúng/đủ/hiển thị thì không được tính là tiến độ nội dung.
