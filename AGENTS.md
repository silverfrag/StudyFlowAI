# Hướng dẫn cho agent và cộng tác viên — StudyFlow AI

## Mục tiêu dự án
- Hoàn thành MVP StudyFlow AI trong 2 tuần: quản lý môn học và tài liệu, hỏi đáp theo tài liệu có nguồn, tạo/làm quiz trắc nghiệm, xem kết quả và gợi ý ôn tập theo topic yếu.
- Bám theo `docs/requirements.md`, `docs/use-cases.md`, `docs/architecture.md` và `docs/ui-wireframes.md`. Nếu tài liệu mâu thuẫn với repo hoặc hợp đồng đã thống nhất, dừng phần liên quan và báo rõ; không tự chọn hướng khác.
- Chỉ triển khai phạm vi người dùng giao. Không tự mở rộng sang mạng xã hội, lớp nhiều người, đa tenant, analytics nâng cao, thanh toán, đồng bộ thời gian thực hoặc nhiều backend service.

## Kiến trúc đã chốt
- Android: Kotlin + Jetpack Compose + MVVM.
- Backend: một dịch vụ FastAPI duy nhất, dạng modular monolith; không thêm NestJS hoặc backend thứ hai.
- Database: PostgreSQL tự quản lý, dùng pgvector cho vector search.
- File storage: local storage trong môi trường phát triển và/hoặc trên máy chủ; metadata lưu ở PostgreSQL.
- AI: Gemini Developer API chỉ được gọi từ backend.
- Không dùng Supabase.
- Không diễn giải các lựa chọn trên như đề xuất cần xác nhận lại. Mọi thay đổi cần thảo luận và thống nhất trước khi sửa.

## Workflow Google AI Pro
Dùng các công cụ trong gói để hỗ trợ phát triển, không thay đổi kiến trúc runtime của StudyFlow:
1. **Antigravity** là agent điều phối và triển khai chính: đọc tài liệu, lập kế hoạch thay đổi, điều phối các phần việc, tích hợp và rà soát kết quả.
2. **Android Studio + Gemini** hỗ trợ công việc Android/Kotlin/Jetpack Compose, phân tích lỗi và chạy/kiểm tra ứng dụng trên emulator. Thay đổi vẫn phải theo hợp đồng API và tài liệu dự án.
3. **Google AI Studio** dùng để thử prompt, RAG, structured JSON, sinh quiz và đánh giá model. Kết quả thử nghiệm phải được xác thực trước khi đưa vào backend; không đặt key thử nghiệm trong Android hoặc commit key vào repo.
4. **Jules** là agent phụ tùy chọn cho một nhiệm vụ độc lập, có ranh giới file/module rõ và làm qua GitHub branch/PR. Không giao cho Jules công việc đồng thời chạm vào cùng file hoặc cùng module đang được sửa ở nơi khác.
5. Google AI Pro là quyền lợi thuê bao cho công cụ hỗ trợ phát triển. Quyền lợi này **không đồng nghĩa Gemini Developer API được miễn phí hoặc không giới hạn**; API quota, giá và billing được quản lý riêng theo điều kiện Developer API.

## Phân công và tích hợp
- Chia công việc theo module và hợp đồng API trước khi bắt đầu. Ví dụ: một người phụ trách Android app; người còn lại phụ trách FastAPI, PostgreSQL, pgvector và xử lý tài liệu/AI. Các phần dùng chung chỉ được giao sau khi xác định chủ sở hữu.
- Mỗi người/agent làm trên branch riêng và gửi PR để review/merge. Không sửa đồng thời cùng file hoặc cùng module; nếu cần sửa giao điểm, thống nhất trước ai thực hiện hoặc giao một người tích hợp xử lý.
- Chỉ định một người giữ vai trò tích hợp: chịu trách nhiệm merge, xử lý xung đột, đồng bộ tài liệu và giữ bản chạy được.
- Mọi thay đổi database schema, migration, endpoint, request/response JSON, validation hoặc lỗi API phải được thống nhất trước; sau đó cập nhật hợp đồng trong tài liệu dự án (đặc biệt `docs/architecture.md` và tài liệu API nếu có) trong cùng PR.
- Không tự ý đổi stack, tên/kiểu trường, semantics, authentication hoặc cách lưu file chỉ để thuận tiện cho một module. Nếu hợp đồng chưa đủ, ghi rõ câu hỏi/blocker và thống nhất trước khi triển khai phần phụ thuộc.

## Bảo mật và dữ liệu
- Mọi API key, token, mật khẩu database và secret chỉ nằm trong biến môi trường hoặc secret store của backend. Không commit secret; không nhúng vào APK, mã nguồn Android, cấu hình client, tài liệu công khai, log hoặc response API.
- `.env` cục bộ phải được ignore; chỉ commit mẫu không chứa giá trị bí mật (ví dụ `.env.example`). Backend là nơi duy nhất gọi Gemini Developer API.
- Không log API key, toàn văn tài liệu hoặc dữ liệu học tập nhạy cảm nếu không cần thiết để debug.
- Backend phải kiểm tra loại/MIME/kích thước file, tự sinh tên lưu trữ và kiểm soát quyền truy cập; không dùng đường dẫn tùy ý do client gửi.

## Quy tắc nghiệp vụ AI và MVP
- Chấm MCQ bằng đáp án chuẩn và logic xác định ở backend; không nhờ LLM quyết định đúng/sai hoặc điểm.
- RAG chỉ đưa ra câu trả lời có căn cứ từ đoạn truy xuất, hiển thị tài liệu và trang/định danh đoạn nguồn. Nếu không tìm thấy căn cứ, nói rõ điều đó; không bịa citation.
- Đầu ra JSON do model tạo phải được backend parse và validate theo schema trước khi lưu/trả về client.
- Gợi ý topic yếu dựa trên kết quả quiz đã lưu và ngưỡng dữ liệu đã thống nhất; không để LLM tự bịa điểm yếu.
- Làm theo lát cắt MVP nhỏ có thể chạy/demo. Giữ trạng thái loading/empty/error rõ ràng và theo wireframe.

## Trước mỗi phần triển khai
Tóm tắt mục tiêu và file/module dự kiến sửa. Trước khi sửa schema hoặc API, thống nhất hợp đồng với người tích hợp và cập nhật tài liệu liên quan. Sau khi làm, nêu thay đổi và cách chạy/demo. Không tự triển khai toàn bộ sản phẩm khi chỉ được giao một phần.
