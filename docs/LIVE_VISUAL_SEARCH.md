# Live Visual Search — camera quét tìm đối tượng ngoài thực tế

## Mục tiêu
Cho phép người dùng nhập một mục tiêu cần tìm (ví dụ: "rau ABC"), mở camera và lia máy quanh môi trường. Ứng dụng phân tích liên tục nhiều khung hình, đánh dấu các đối tượng nghi ngờ, theo dõi chúng qua thời gian và báo khi có ứng viên phù hợp.

## Nguyên tắc sản phẩm
- Đây là chế độ **TÌM TRONG THỰC TẾ**, khác với chụp một ảnh rồi tra cứu.
- Không được tuyên bố nhận dạng chắc chắn khi mô hình/chứng cứ chưa đạt ngưỡng kiểm chứng.
- Với rau/cây/nấm/động vật có rủi ro an toàn, kết quả thị giác chỉ là **ứng viên nhận dạng**; tính ăn được, độc tính, công dụng và xử trí phải lấy từ lớp evidence riêng.
- Kết quả phải hỗ trợ `UNKNOWN` / `KHÔNG ĐỦ BẰNG CHỨNG`.
- Hoạt động offline sau khi model/index đã được cài trên máy.

## Luồng người dùng
1. Người dùng mở **Nhận dạng → Tìm trong thực tế**.
2. Nhập mục tiêu, ví dụ `rau ABC`, hoặc chọn một taxon từ Thư viện.
3. Ứng dụng resolve mục tiêu thành `targetId` / tên khoa học / aliases từ thư viện cục bộ.
4. Camera mở preview liên tục.
5. `ImageAnalysis` lấy khung hình với chiến lược giữ khung mới nhất.
6. Detector phát hiện nhiều vùng quan tâm trong cùng một frame.
7. Mỗi vùng được encoder/classifier tạo embedding / top-k candidate.
8. Candidate matcher so khớp với target đã chọn.
9. Temporal tracker giữ cùng một đối tượng qua nhiều frame, tránh nhấp nháy kết quả.
10. Khi một candidate ổn định vượt ngưỡng nhiều frame liên tiếp, UI đổi từ `ĐANG QUÉT` sang `PHÁT HIỆN ỨNG VIÊN`, rung nhẹ và vẽ bounding box.
11. Người dùng có thể bấm vào box để đóng băng frame, chụp bằng chứng và mở hồ sơ đối chiếu.
12. Hồ sơ hiển thị riêng: nhận dạng thị giác, taxonomy, specialist evidence, cảnh báo và mức độ chắc chắn.

## Pipeline kỹ thuật

### Camera
- Android CameraX `Preview` + `ImageAnalysis`.
- `STRATEGY_KEEP_ONLY_LATEST` để tránh backlog khi lia camera.
- Analyzer chạy ngoài main thread.
- Giới hạn tần suất inference theo thiết bị và nhiệt độ, ưu tiên độ trễ thấp hơn FPS camera.

### Nhận dạng nhiều đối tượng
Không dùng một classifier toàn ảnh làm pipeline chính. Cần hai tầng:
1. **Object / region detector**: tìm nhiều vùng có thể là lá, hoa, quả, nấm, côn trùng, động vật... trong frame.
2. **Visual encoder / classifier**: phân tích từng crop và trả candidate.

Điều này đáp ứng tình huống một ảnh có nhiều cây hoặc nhiều bộ phận khác nhau.

### Target search
Khi người dùng tìm `rau ABC`, không cần phân loại toàn bộ thế giới rồi mới lọc. Pipeline dùng target-aware matching:
- target metadata từ local library;
- target embedding/index nếu model hỗ trợ retrieval;
- aliases / tên Việt / tên khoa học chỉ dùng để resolve target, không dùng thay bằng chứng thị giác.

Kết quả mỗi detection:
- `trackId`
- bounding box
- top-k candidate IDs
- visual score
- temporal stability
- target match score
- frame timestamp

### Temporal confirmation
Không báo FOUND từ một frame duy nhất.
Gợi ý state machine:
- `SCANNING`
- `CANDIDATE`
- `STABLE_CANDIDATE`
- `NEEDS_CLOSER_VIEW`
- `UNKNOWN`

Một ứng viên chỉ lên `STABLE_CANDIDATE` khi xuất hiện ổn định qua nhiều frame và không mâu thuẫn lớn giữa các góc nhìn.

## UI camera
Overlay trên preview:
- thanh trên: `ĐANG TÌM: <mục tiêu>`;
- trạng thái `OFFLINE` / model version;
- bounding boxes cho candidate;
- màu trung tính cho candidate chưa chắc;
- nhãn `ỨNG VIÊN`, không ghi `CHẮC CHẮN`;
- nút `DỪNG`, `ĐỔI MỤC TIÊU`, `CHỤP BẰNG CHỨNG`;
- hướng dẫn động: `lia chậm`, `đến gần hơn`, `chụp mặt dưới lá`, `chụp hoa/quả nếu có`.

Khi có candidate ổn định:
- rung nhẹ một lần;
- giữ box trên đối tượng đang track;
- hiển thị tên candidate + score thị giác;
- CTA `ĐỐI CHIẾU CHI TIẾT`.

## Tích hợp với Scientific Library
- WFO/taxonomy chỉ xác nhận tên và phân loại.
- Specialist evidence chỉ hiển thị sau khi candidate đã map được về species/taxon.
- Không suy ra ăn được / độc / chữa bệnh từ taxonomy hoặc visual score.
- Với nhóm nguy cơ cao, UI phải hiện cảnh báo: `Nhận dạng bằng camera chưa đủ để quyết định ăn/sử dụng`.

## Dữ liệu/model cần có
Tách độc lập khỏi APK khi có thể:
- model detector;
- visual encoder/classifier;
- label/taxon mapping;
- embedding index;
- model manifest: version, SHA-256, input size, class/index count, source/license, validation metrics, supported domains.

Không kích hoạt model nếu thiếu manifest, SHA-256 sai, schema không tương thích hoặc validation profile không đạt yêu cầu của app.

## Kiểm thử bắt buộc trước khi gọi production recognition
- latency trên thiết bị thật;
- memory / thermal throttling;
- camera rotation/crop correctness;
- multi-object scenes;
- motion blur / thiếu sáng / nền phức tạp;
- near-species confusion set;
- UNKNOWN / out-of-distribution behavior;
- false positive rate cho high-risk species;
- model-version provenance.

## Giai đoạn triển khai
1. Live CameraX preview + ImageAnalysis + overlay/state machine, chưa gắn model giả.
2. Model runner interface + mock deterministic test source cho CI.
3. Cài model pack offline có manifest/SHA-256.
4. Detector thật + multi-box tracking.
5. Visual retrieval/classification + target matching.
6. Liên kết candidate với Scientific Library / Specialist Evidence.
7. Device validation và confusion-set testing trước khi đổi nhãn từ experimental sang verified.

## Không được làm
- Không dùng OCR/tên chữ trong cảnh để giả làm nhận dạng loài.
- Không coi nearest visual candidate là kết luận nếu score thấp.
- Không dùng một frame duy nhất để kết luận high-risk taxon.
- Không tự gắn nhãn `ăn được`, `không độc`, `dùng làm thuốc` từ output của model thị giác.
