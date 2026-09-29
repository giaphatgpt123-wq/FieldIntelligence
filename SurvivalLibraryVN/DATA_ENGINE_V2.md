# SurvivalLibraryVN — Data Engine V2

## Mục tiêu

Data Engine V2 tách **thu thập** khỏi **phát hành**. AI/collector có thể thu thập nhanh, nhiều nguồn, lưu dữ liệu từng phần và tự tạo công việc bổ sung; `LibraryRules` là cổng quyết định cuối cùng trước khi dữ liệu được phát hành.

## Nguyên tắc bắt buộc

1. Một đối tượng sinh học có **một canonical ID**.
2. Một canonical ID có thể có nhiều tên: tên chính, tên khác, tên vùng miền, dân gian, thương mại, tên cũ và synonym khoa học.
3. Mỗi trường dữ liệu có trạng thái và evidence riêng; không đánh đồng “hồ sơ chưa hoàn chỉnh” với “dữ liệu vô giá trị”.
4. Nguồn Việt Nam được ưu tiên cho tên Việt, tên địa phương, phân bố tại Việt Nam, công dụng thực tế và ảnh Việt Nam khi quyền sử dụng rõ ràng.
5. Nguồn khoa học quốc tế được dùng cho taxonomy, đối chiếu, phân bố và bổ sung media/evidence.
6. Một nguồn lỗi/rate-limit chỉ làm task tương ứng retry; không được dừng toàn bộ loài, danh mục hoặc pipeline.
7. AI không được tự publish. AI chỉ tạo/ưu tiên task; `LibraryRules.publicationDecision()` mới quyết định hồ sơ có đủ chuẩn phát hành hay không.
8. Mức `Thường dùng/Hay dùng/Ít dùng/Hiếm dùng/Không có khả năng dùng` chỉ có giá trị khi có nguồn đối chiếu riêng.
9. Đối tượng nguy cơ cao phải ưu tiên safety task và nguồn chính thức/chuyên ngành.
10. Tiến độ phải phản ánh từng trường đã có dữ liệu/đã kiểm chứng, không chỉ PASS/FAIL toàn hồ sơ.

## Luồng xử lý

```text
Source Registry
    ↓
AI Source Router
    ↓
Collector Tasks (nhỏ, độc lập, idempotent)
    ↓
Staging DB
    ├── canonical entity
    ├── aliases/tên vùng miền
    ├── field evidence
    ├── media evidence
    ├── source health
    └── task queue
    ↓
AI Library Manager
    ├── xác định trường còn thiếu
    ├── ưu tiên nguồn phù hợp
    ├── cooldown nguồn lỗi
    ├── không thu lại trường đã verified
    └── tạo task bổ sung
    ↓
Rule Engine (`LibraryRules`)
    ↓
Published package / incremental update
```

## Trạng thái dữ liệu

Một hồ sơ có thể ở trạng thái từng phần, ví dụ:

```text
Cá rô đồng
- canonical identity: VERIFIED
- tên Việt: VERIFIED
- aliases: PARTIAL
- phân bố Việt Nam: VERIFIED
- ảnh chính: VERIFIED
- ảnh chẩn đoán: PARTIAL
- nhận biết: VERIFIED
- dễ nhầm: PENDING
- usage level: PENDING
- usage content: PENDING
```

AI chỉ tạo task cho các mục `PARTIAL/PENDING`; các mục đã `VERIFIED` không bị tải lại.

## Source routing

| Trường | Ưu tiên nguồn |
|---|---|
| Tên Việt / tên khác / tên vùng | Chính thức VN → chuyên ngành VN → quốc tế → open science |
| Taxonomy | Global authority → chính thức VN → chuyên ngành VN |
| Phân bố Việt Nam | Chính thức VN → chuyên ngành VN → global authority / open science |
| Ảnh | Chính thức VN → chuyên ngành VN → open science → quốc tế → community reference |
| Nhận biết / dễ nhầm | Chuyên ngành VN → chính thức VN → global authority |
| Công dụng / mức sử dụng | Chính thức VN → chuyên ngành VN → global authority |
| Safety | Chính thức VN → global authority → chuyên ngành VN |

Nguồn community/reference không được tự động nâng claim y tế, độc tính hoặc safety qua gate.

## Thành phần trong code

- `LibraryDataEngineV2.kt`: model, alias normalizer, source policy, `AiLibraryManager`.
- `DataEngineStore.kt`: staging SQLite độc lập, task queue và source cooldown.
- `LibraryRules.kt`: cổng phát hành deterministic.
- `LibraryDataEngineV2Test.kt`: kiểm thử chống reload thừa, chống AI bypass rule, alias dedupe, source priority và retry độc lập.

## Giai đoạn triển khai

**V2-A (hiện tại):** khóa schema + task planner + staging + rule tests.

**V2-B:** adapter collector đa nguồn, ưu tiên nguồn Việt Nam và bulk ingest.

**V2-C:** progress dashboard đọc trực tiếp từ staging/task queue.

**V2-D:** incremental publisher: hồ sơ đạt core gate được phát hành ngay; enrichment tiếp tục chạy mà không phải build lại toàn danh mục.

Không mở collector hàng loạt trước khi V2-A có bằng chứng unit test/build PASS.
