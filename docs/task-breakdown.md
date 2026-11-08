# Phân công triển khai — StudyFlow AI

## Mục tiêu
Hoàn thành luồng MVP trong 2 tuần: môn học → tài liệu PDF/TXT → hỏi đáp có nguồn → tạo quiz → làm bài/chấm điểm → kết quả và gợi ý ôn tập. Chia theo module để hai thành viên làm song song mà không cùng sửa file hoặc cùng module.

## Vai trò

| Vai trò | Sở hữu | Trách nhiệm |
|---|---|---|
| Thành viên 1 — Android | `android/` (hoặc module Android đã có) | Kotlin, Compose, MVVM, điều hướng, màn hình, state, repository/client API, upload UI và luồng quiz/kết quả. |
| Thành viên 2 — Backend | `backend/` (hoặc module backend đã có) | FastAPI, PostgreSQL/pgvector, migrations, môn học/tài liệu, xử lý PDF/TXT, RAG, Gemini, quiz, chấm điểm, tiến độ/gợi ý. |
| Antigravity — điều phối/triển khai chính | Tài liệu hợp đồng và tích hợp liên module theo từng task được giao | Khảo sát repo, chốt hợp đồng cùng nhóm, tạo kế hoạch/task, dựng nền tảng ban đầu nếu chưa có, giao việc theo ranh giới, review PR, tích hợp và xử lý lỗi xuyên module. Antigravity chỉ sửa module của thành viên khi task được chuyển giao rõ ràng và chủ sở hữu module đã dừng sửa phần đó. |

**Người tích hợp:** Antigravity làm đầu mối tích hợp kỹ thuật; một trong hai thành viên review luồng demo end-to-end cùng Antigravity. Mỗi PR có một chủ sở hữu module; không có chỉnh sửa đồng thời.

## Quy tắc giao việc
- Mỗi thành viên làm trên branch riêng, chỉ commit file thuộc module được giao và mở PR; Antigravity review/merge theo nhịp tích hợp.
- Không tạo PR chạm đồng thời `android/` và `backend/`, trừ PR tích hợp do Antigravity sở hữu.
- Tài liệu API/schema là phần dùng chung, do Antigravity cập nhật sau khi hai thành viên thống nhất; không tự sửa hợp đồng ở nhánh module.
- Mọi đổi schema/migration, endpoint, request/response, validation, status/error phải được đồng thuận trước. Cập nhật tài liệu hợp đồng trong cùng PR với thay đổi đã thống nhất.
- Chỉ giao Jules việc độc lập, ví dụ rà soát parser PDF/TXT hoặc đánh giá JSON quiz bằng bộ mẫu, trên branch riêng; không giao trùng module đang có chủ sở hữu.
- Nếu repo chưa có skeleton, Antigravity khảo sát trước rồi tạo cấu trúc gọn cho `android/` và `backend/`; không sinh nhiều service hoặc framework thừa. Không ghi giá trị secret vào file mẫu.

## Hợp đồng cần thống nhất trước khi hai nhánh triển khai song song
Antigravity điều phối buổi chốt ngắn với cả hai thành viên và ghi kết quả vào `docs/architecture.md` hoặc tài liệu API riêng:

1. Base URL theo môi trường, format lỗi chung, timeout và quy tắc ID.
2. Endpoints môn học/tài liệu, upload multipart, trạng thái xử lý tài liệu và cách xóa.
3. JSON hỏi đáp: câu hỏi/ngữ cảnh, `answer`, citations (`document_title`, `page`/`chunk_id`) và uncertainty.
4. JSON quiz: số lượng câu/lựa chọn, `correct_option_id` chỉ giữ ở backend trước khi nộp, topic, giải thích và nguồn.
5. JSON submit/result: lựa chọn gửi lên, điểm, câu đúng/sai, thống kê theo topic và quy tắc một lần nộp.
6. Ngưỡng topic yếu, giới hạn dung lượng/loại file, quy tắc tên môn trùng và xóa môn/tài liệu.

Không cần thiết kế quá mức; chọn schema tối thiểu đủ để Android tích hợp ổn định.

## Các mốc trong 2 tuần

### Mốc 0 — Khảo sát và hợp đồng (ngày 1)
- Antigravity: kiểm tra repo, hướng dẫn hiện có, công cụ build và trạng thái skeleton; báo blockers trước khi ghi đè hoặc tạo cấu trúc lớn.
- Hai thành viên: thống nhất API/schema tối thiểu, file limits và định nghĩa xong MVP.
- Antigravity: cập nhật hợp đồng và mở task/branch theo module.

**Điều kiện qua mốc:** request/response cốt lõi và ranh giới module được ghi lại; mọi người biết file nào mình sở hữu.

### Mốc 1 — Nền tảng và luồng dữ liệu mẫu (ngày 2–4)
- Thành viên 1: khung app Compose, navigation 4 tab, state loading/empty/error và client API theo hợp đồng.
- Thành viên 2: FastAPI chạy được, cấu hình PostgreSQL/pgvector, migration nền và health endpoint; secret chỉ ở env.
- Antigravity: phối hợp cấu hình chạy local, ví dụ env an toàn và hướng dẫn khởi động; không tạo backend thứ hai.

### Mốc 2 — Tính năng lõi song song (ngày 5–9)
- Thành viên 1: môn học, tài liệu, hỏi đáp, tạo/làm quiz, kết quả và tiến độ UI; mock chỉ dùng tạm đến khi API module sẵn.
- Thành viên 2: CRUD môn/tài liệu, lưu/trích xuất PDF/TXT, chunks/embeddings, RAG citations, sinh quiz JSON đã validate, chấm điểm xác định, tiến độ/topic yếu.
- Antigravity: tích hợp từng hợp đồng/PR theo thứ tự, phát hiện lệch payload sớm; Jules chỉ làm một task nhỏ độc lập nếu có thời gian.

### Mốc 3 — Tích hợp và chuẩn bị demo (ngày 10–14)
- Hai thành viên cùng xử lý lỗi trong module mình sở hữu và review luồng end-to-end.
- Antigravity khóa scope, merge lần lượt, kiểm tra tài liệu chạy local/demo và lập danh sách lỗi còn lại.
- Ưu tiên luồng demo hoàn chỉnh trên dữ liệu thật hơn polish hoặc tính năng ngoài MVP.

## Thứ tự tích hợp bắt buộc
1. Health/config backend và cấu hình app trỏ tới môi trường phát triển.
2. CRUD môn học.
3. Upload tài liệu → trạng thái ready/error.
4. Hỏi đáp RAG → citation hợp lệ.
5. Sinh quiz → validate và bắt đầu quiz.
6. Submit → chấm điểm deterministic → kết quả theo topic → gợi ý ôn.

Mỗi bước chỉ tích hợp sau khi response schema ổn định; nếu hợp đồng đổi, cập nhật tài liệu và cả hai bên trước khi tiếp tục.

## Prompt khởi động Antigravity
Dán nguyên văn prompt sau vào Antigravity khi mở `E:\StudyFlowAI`:

> Đọc toàn bộ `AGENTS.md` và `docs/requirements.md`, `docs/use-cases.md`, `docs/ui-wireframes.md`, `docs/architecture.md`, `docs/task-breakdown.md` trước khi làm. Kiến trúc đã chốt: Android Kotlin + Jetpack Compose + MVVM; một FastAPI; PostgreSQL tự quản lý + pgvector; file local/server; Gemini Developer API chỉ gọi từ backend. Không dùng Supabase hoặc NestJS. Không để secret trong Android/repo. Google AI Pro không làm Gemini Developer API miễn phí/không giới hạn.
>
> Bước đầu chỉ khảo sát trạng thái repo, cấu trúc, công cụ build, file đã có và hướng dẫn con; tuyệt đối không ghi đè hoặc xóa file có giá trị. Báo ngắn: (1) hiện trạng repo, (2) các blockers, (3) file/module sẽ tạo hoặc sửa. Sau đó đề xuất hợp đồng API tối thiểu cho các luồng môn học, upload tài liệu, hỏi đáp citations, quiz, submit/result và recommendations để hai thành viên duyệt. Chưa triển khai tính năng trước khi hợp đồng được thống nhất.
>
> Sau khi nhóm xác nhận hợp đồng, điều phối hai nhánh độc lập: Thành viên 1 chỉ sở hữu module Android; Thành viên 2 chỉ sở hữu module FastAPI/database/AI. Branch riêng, PR riêng, không cùng sửa file/module. Bạn là đầu mối tích hợp và cập nhật tài liệu hợp đồng cùng PR khi schema/API được thống nhất. Chỉ giao Jules việc độc lập, ranh giới rõ. Thực hiện từng mốc trong `docs/task-breakdown.md`, ưu tiên MVP end-to-end; không mở rộng scope.
