# Yêu cầu sản phẩm — StudyFlow AI

**Tên đề tài đề xuất:** Nghiên cứu và phát triển ứng dụng hỗ trợ học tập cá nhân hóa tích hợp AI trên Android  
**Tên ứng dụng:** StudyFlow AI  
**Nền tảng:** Android  
**Thời hạn MVP:** 2 tuần  
**Trạng thái công nghệ:** Đã chốt theo `docs/architecture.md`; mọi thay đổi cần được nhóm thống nhất trước.

## 1. Mục tiêu
Giúp một sinh viên tổ chức môn học và tài liệu, hỏi đáp kiến thức dựa trên tài liệu có dẫn nguồn, luyện tập bằng câu hỏi trắc nghiệm và nhận gợi ý ôn lại topic có kết quả thấp. MVP cần có một luồng demo hoàn chỉnh, không hướng đến triển khai quy mô thương mại.

## 2. Nhóm người dùng
- **Sinh viên (người học):** người dùng chính; quản lý môn học/tài liệu cá nhân, hỏi AI, luyện quiz và xem tiến độ.
- **Dịch vụ AI/LLM:** hệ thống bên ngoài được backend gọi để tạo câu trả lời, câu hỏi MCQ và lời giải thích ngắn. Đây là actor hỗ trợ hệ thống, không phải người dùng giao diện.

MVP giả định một người học/demo cục bộ hoặc một tài khoản đơn giản nếu repo đã có sẵn cơ chế phù hợp. Đăng ký, đăng nhập xã hội và quản trị nhiều người dùng không nằm trong MVP.

## 3. Use case UC01–UC07
| Mã | Use case | Kết quả mong muốn |
|---|---|---|
| UC01 | Quản lý môn học | Tạo, xem danh sách, sửa tên/mô tả, mở chi tiết và xóa môn học demo. |
| UC02 | Quản lý tài liệu | Thêm PDF/TXT vào môn học, xem danh sách/trạng thái xử lý và xóa tài liệu. Nội dung được trích xuất để tra cứu. |
| UC03 | Hỏi đáp AI theo tài liệu | Đặt câu hỏi trong ngữ cảnh môn học/tài liệu; nhận câu trả lời có nguồn trích dẫn. |
| UC04 | Tạo bài kiểm tra | Chọn môn/chủ đề và số lượng phù hợp; tạo quiz MCQ dựa trên tài liệu, có lựa chọn và đáp án chuẩn. |
| UC05 | Làm bài kiểm tra | Trả lời câu hỏi, di chuyển qua câu, nộp bài và nhận điểm được tính xác định. |
| UC06 | Xem kết quả học tập | Xem điểm, câu đúng/sai và tóm tắt kết quả theo topic từ các lần làm quiz đã lưu. |
| UC07 | Xem gợi ý ôn tập | Xem topic có tỷ lệ đúng thấp và gợi ý tài liệu/quiz nên ôn tiếp. |

Chi tiết tiền điều kiện, luồng và ngoại lệ ở `docs/use-cases.md`.

## 4. Phạm vi MVP
- Android app với 6 màn hình cốt lõi được mô tả trong `docs/ui-wireframes.md`.
- Điều hướng chính 4 tab: **Trang chủ, Môn học, AI, Tiến độ**; màn làm quiz và kết quả mở từ luồng môn học/quiz.
- CRUD tối thiểu cho môn học và tài liệu PDF/TXT; xử lý lỗi tệp không hỗ trợ hoặc không đọc được.
- Hỏi đáp RAG trên nội dung đã nạp, trả về một hoặc vài trích dẫn có tên tài liệu và số trang nếu trích xuất được.
- Sinh quiz MCQ có cấu trúc; kiểm tra dữ liệu sinh ra trước khi hiển thị.
- Chấm điểm theo khóa đáp án bằng logic xác định; lưu kết quả theo topic ở mức đơn giản.
- Gợi ý ôn tập theo topic yếu dựa trên kết quả MCQ; không yêu cầu mô hình dự đoán nâng cao.
- Dữ liệu demo có thể dùng một sinh viên; trạng thái lỗi/loading/empty cơ bản.

## 5. Ngoài phạm vi
- Nhiều tổ chức/lớp, giảng viên, phân quyền chi tiết, chat nhóm hoặc mạng xã hội.
- Đồng bộ thời gian thực đa thiết bị, offline-first hoàn chỉnh, thông báo đẩy.
- Video/audio, OCR ảnh, DOCX/PPTX và các định dạng ngoài PDF/TXT.
- Tự động lập lịch học dài hạn, spaced repetition nâng cao hoặc mô hình đánh giá năng lực phức tạp.
- Dashboard analytics nâng cao, triển khai production nhiều môi trường, SLA, billing hoặc scale thương mại.
- Nhiều microservice, orchestration phức tạp, AI agent tự hành.

## 6. Tiêu chí hoàn thành demo
1. Tạo/chọn môn học và thêm tài liệu PDF/TXT đọc được.
2. Hỏi một câu có thể tìm trong tài liệu và thấy câu trả lời kèm nguồn.
3. Tạo quiz MCQ từ môn/tài liệu, hoàn thành và xem điểm tính đúng theo đáp án.
4. Kết quả tổng hợp được topic yếu và đưa ra gợi ý ôn lại hợp lý.
5. Người dùng có thể đi qua 4 tab chính; các màn hình quiz/kết quả có đường vào và quay lại rõ ràng.

## 7. Quyết định và ràng buộc kỹ thuật
- Android đã chốt: **Kotlin + Jetpack Compose + MVVM**.
- Backend duy nhất: **FastAPI**; database: **PostgreSQL tự quản lý + pgvector**; file storage: local/server; AI runtime: **Gemini Developer API gọi từ backend**. Không dùng Supabase hoặc NestJS.
- API key, database credentials và mọi secret chỉ lưu trong backend env/secret store; không commit, không nhúng Android.
- Dùng pgvector cho vector search trong PostgreSQL; Gemini Developer API chỉ được backend gọi.
- Google AI Pro hỗ trợ công cụ phát triển, không đồng nghĩa Gemini Developer API miễn phí hoặc không giới hạn.
- Thay đổi stack, schema hoặc API phải thống nhất trước và cập nhật tài liệu hợp đồng cùng PR.
- Thời hạn 2 tuần ưu tiên hoàn thành luồng demo có dữ liệu thật, không mở rộng độ sâu tính năng.



