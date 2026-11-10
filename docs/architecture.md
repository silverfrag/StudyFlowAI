# Kiến trúc MVP — StudyFlow AI

## 1. Mục tiêu và quyết định kiến trúc
Mục tiêu là hoàn thành MVP Android trong 2 tuần với một luồng end-to-end: nạp tài liệu, hỏi đáp có dẫn nguồn, tạo/làm quiz, lưu kết quả và gợi ý ôn tập. Kiến trúc đã chốt:

| Thành phần | Quyết định |
|---|---|
| Android | Kotlin + Jetpack Compose + MVVM |
| Giao tiếp | REST API, HTTPS, JSON; upload file qua multipart |
| Backend | Một ứng dụng FastAPI duy nhất, modular monolith |
| Database | PostgreSQL tự quản lý |
| Vector search | pgvector trong PostgreSQL |
| File storage | Local storage trong môi trường phát triển/máy chủ; PostgreSQL lưu metadata và storage reference |
| AI runtime | Gemini Developer API, chỉ gọi từ backend |

Không dùng Supabase hoặc NestJS; không tách thêm backend/service cho MVP. Android không gọi Gemini trực tiếp.

## 2. Thành phần và trách nhiệm
1. **Android app:** Compose UI, ViewModel/state theo màn hình, repository và DTO cho API; quản lý luồng môn học, tài liệu (kèm trình xem PDF/TXT tích hợp mở thẳng tới trang gợi ý/trích dẫn), trợ lý học tập, quiz và tiến độ.
2. **FastAPI backend:** API, xác thực/kiểm tra input, nghiệp vụ môn học/tài liệu/quiz/kết quả, upload và xử lý tài liệu, truy xuất RAG, gọi Gemini, xác thực đầu ra model.
3. **PostgreSQL + pgvector:** dữ liệu nghiệp vụ, chunks, embeddings, quiz và kết quả; dùng vector search khi tạo embedding tương thích. Schema và migration được quản lý trong repo.
4. **Local/server file storage:** file PDF/TXT gốc. Backend sinh tên lưu trữ, kiểm tra loại/kích thước, kiểm soát quyền truy cập và giữ storage reference trong DB.
5. **Gemini Developer API:** backend dùng cho embedding, hỏi đáp và sinh quiz theo cấu hình model. Model ID, timeout và cấu hình liên quan đặt ở backend env/config.

```text
Android (Kotlin + Compose + MVVM)
        | HTTPS / REST JSON; multipart upload
        v
FastAPI (một backend duy nhất)
   |             |                    |
   v             v                    v
PostgreSQL    Local/server files    Gemini Developer API
+ pgvector      (PDF/TXT)            (backend only)
```

## 3. Workflow Google AI Pro cho phát triển
Các công cụ này hỗ trợ quá trình xây dựng, không phải thành phần runtime của ứng dụng:

- **Antigravity:** agent điều phối/triển khai chính, đọc tài liệu, chia phần việc, tích hợp branch/PR và rà soát thay đổi.
- **Android Studio + Gemini:** hỗ trợ Android, Kotlin, Compose và kiểm tra ứng dụng trên emulator.
- **Google AI Studio:** thử nghiệm prompt, RAG, structured JSON, sinh quiz và đánh giá model. Chỉ chuyển prompt/schema đã kiểm tra vào backend; không đưa API key vào client hoặc repo.
- **Jules:** tùy chọn cho nhiệm vụ độc lập qua GitHub branch/PR, với ranh giới module/file cụ thể.
- **Giới hạn thuê bao/API:** Google AI Pro có thể cung cấp quyền lợi/hạn mức cho các công cụ phát triển, nhưng không có nghĩa Gemini Developer API miễn phí hoặc không giới hạn. Quota, billing và điều khoản API được quản lý riêng.

## 4. Luồng nghiệp vụ chính

### Nạp tài liệu
1. Android gửi file PDF/TXT và `course_id` lên API.
2. Backend kiểm tra MIME/nội dung/kích thước, tạo tên lưu trữ an toàn và lưu file.
3. Backend trích xuất văn bản, chia thành chunks; lưu `document_id`, `course_id`, thứ tự, số trang nếu có, nội dung và embedding vào PostgreSQL/pgvector.
4. Trạng thái tài liệu là `processing`, `ready` hoặc `error`; app chỉ cho hỏi/tạo quiz khi tài liệu sẵn sàng.

### Hỏi đáp theo tài liệu (RAG)
1. Android gửi `course_id`/`document_id` tùy chọn và câu hỏi.
2. Backend truy xuất chunks liên quan trong phạm vi được phép, dùng pgvector.
3. Backend gửi câu hỏi và ngữ cảnh đến Gemini, yêu cầu đầu ra JSON theo schema gồm answer, citations và uncertainty.
4. Backend parse/validate JSON và xác minh citation chỉ trỏ đến chunk đã truy xuất; trả `document_title`, trang nếu có hoặc `chunk_id`.
5. Android hiển thị câu trả lời cùng nguồn. Nếu không có căn cứ, nói rõ chưa tìm thấy trong tài liệu; không chấp nhận citation bịa.

### Tạo quiz và chấm điểm
1. Android yêu cầu quiz theo môn/topic và số câu đã được giới hạn.
2. Backend lấy chunks phù hợp, gọi Gemini sinh MCQ theo JSON schema; validate số lựa chọn, ID và đúng một đáp án chuẩn, topic, giải thích và nguồn nếu có.
3. Backend lưu quiz/câu hỏi/đáp án chuẩn. Không gửi đáp án đúng trước khi người học nộp bài.
4. Khi submit, backend so sánh đáp án bằng logic xác định, tính điểm theo quy tắc thống nhất và lưu attempt/answers. LLM không chấm điểm.

### Gợi ý topic yếu
Tổng hợp `correct_count / answered_count` theo topic từ kết quả đã lưu. Chỉ đánh dấu yếu sau ngưỡng số câu tối thiểu đã thống nhất; gợi ý cơ bản là xem lại tài liệu và làm quiz luyện tập. LLM chỉ có thể diễn đạt gợi ý, không tự suy ra điểm yếu ngoài dữ liệu.

## 5. Hợp đồng API sơ bộ
Danh sách dưới đây là phạm vi khái niệm để chia việc; trước khi triển khai phải thống nhất path, field, validation, status/error và schema JSON chi tiết. Thay đổi phải được thống nhất trước và cập nhật tài liệu cùng PR.

- `GET/POST/PATCH/DELETE /courses`
- `GET/POST/DELETE /courses/{courseId}/documents`, `GET /documents/{documentId}/file` & `POST /documents/{documentId}/retry` (upload multipart; trả trạng thái xử lý; tải/stream tệp gốc cho trình xem tài liệu; cho phép thử xử lý lại khi lỗi)
- `POST /assistant/ask` (question + course/document context → answer + citations)
- `GET /courses/{courseId}/quizzes` & `POST /quizzes` (liệt kê quiz theo môn; course/topic/count → validated quiz)
- `POST /quizzes/{quizId}/submit` (answers → deterministic result)
- `GET /progress` và `GET /recommendations`

## 6. Dữ liệu MVP
- `Course(id, name, description, created_at)`
- `Document(id, course_id, title, storage_ref, mime_type, status, created_at)`
- `DocumentChunk(id, document_id, text, page_number?, ordinal, embedding)`
- `Quiz(id, course_id, title, created_at)`
- `Question(id, quiz_id, prompt, options, correct_option_id, topic, explanation, source_chunk_id?)`
- `QuizAttempt(id, quiz_id, score, submitted_at)`
- `Answer(id, attempt_id, question_id, selected_option_id, is_correct)`

Schema/migration chi tiết phải được cả người phụ trách backend và người tích hợp thống nhất, sau đó phản ánh trong tài liệu trước khi triển khai phụ thuộc.

## 7. Bảo mật và cấu hình
- Gemini API key, thông tin kết nối PostgreSQL, token và mọi secret chỉ tồn tại trong backend environment/secret store. Không commit secret, không nhúng vào APK/Android, không ghi log hoặc trả trong response.
- File `.env` cục bộ phải được ignore; chỉ commit `.env.example` không chứa secret thật.
- Backend kiểm tra file, sinh tên lưu trữ và xác thực quyền truy cập; không tin đường dẫn do client gửi.
- Log lỗi mạng/provider/parse vừa đủ để debug, không ghi API key hoặc toàn văn nội dung học tập nếu không cần thiết.
- Google AI Pro không bao gồm quyền sử dụng Gemini Developer API không giới hạn/miễn phí; theo dõi quota và billing API riêng.

## 8. Quy tắc chia việc và tích hợp
- Chia theo module và hợp đồng API; ví dụ Android app và backend/database/AI là hai nhánh trách nhiệm chính.
- Mỗi người/agent dùng branch riêng và gửi PR. Không cùng sửa một file hoặc cùng module đồng thời. Công việc Jules phải độc lập, giới hạn file/module rõ.
- Chỉ định một người tích hợp chịu trách nhiệm review/merge, xử lý xung đột và giữ luồng end-to-end chạy được.
- Schema, migration, endpoint, request/response, lỗi API và validation chỉ thay đổi sau khi nhóm thống nhất; cập nhật tài liệu/hợp đồng trong cùng PR.

## 9. MVP và xử lý lỗi
- Giới hạn loại/kích thước file; trạng thái tài liệu và lỗi parse rõ ràng.
- Xử lý timeout/lỗi Gemini, JSON sai schema, lỗi DB/storage và lỗi mạng bằng phản hồi ổn định; không trả dữ liệu dở dang như quiz hợp lệ.
- Giao diện có loading/empty/error states; không xin quyền Android ngoài nhu cầu MVP.
- Chấm quiz bằng đáp án chuẩn; thống kê tiến độ từ kết quả lưu trữ. Không dựng framework analytics hoặc phân quyền đa tổ chức.
