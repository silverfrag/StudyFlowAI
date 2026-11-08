# Phác thảo giao diện — StudyFlow AI

## 1. Hướng thiết kế
- Mobile-first, Material 3; tối giản, khoảng trắng rõ, card bo góc vừa phải.
- Màu gợi ý: Indigo làm màu nhấn, nền trung tính; hỗ trợ theme hệ thống nếu không làm chậm MVP.
- Thanh điều hướng dưới cùng gồm 4 tab: **Trang chủ · Môn học · AI · Tiến độ**.
- Tạo quiz nằm trong chi tiết môn học. Màn làm quiz và kết quả là các bước trong luồng, không chiếm tab riêng.
- Cả 6 màn hình đều cần trạng thái loading, empty và error tối thiểu.

## 2. Điều hướng
```text
Trang chủ ─────────────┬──> Chi tiết môn / Tài liệu
Môn học ─> Chi tiết môn ├──> Tạo quiz ─> Làm quiz ─> Kết quả
AI ────────────────────┴──> Hỏi đáp theo ngữ cảnh + nguồn
Tiến độ ──────────────────> Kết quả gần đây + topic yếu + gợi ý
```

Tab Môn học mở màn hình danh sách; chọn môn mở chi tiết (có thể là một trạng thái/màn con). Từ chi tiết đi tới danh sách tài liệu và tạo quiz. AI tab cho phép chọn môn/ngữ cảnh trước khi hỏi. Hoàn thành quiz mở màn kết quả; quay lại tab Tiến độ được.

## 3. Sáu màn hình

### Màn hình 01 — Trang chủ
**Mục đích:** điểm vào nhanh, cho thấy việc học gần đây.  
**Thành phần:**
- App bar “StudyFlow AI”, lời chào và dòng “Hôm nay bạn muốn học gì?”.
- Thẻ tóm tắt số môn và số quiz đã hoàn thành.
- “Tiếp tục học”: môn/tài liệu gần nhất, tiến độ nếu có dữ liệu thật.
- Truy cập nhanh: Hỏi AI, Làm quiz, Tài liệu.
- Empty state khi chưa có môn: nút Tạo môn học.

**Phác thảo:**
```text
┌──────────────────────────┐
│ StudyFlow AI              │
│ Xin chào! Hôm nay học gì? │
│ [ 04 môn ] [ 12 quiz ]    │
│ Tiếp tục học              │
│ Cơ sở dữ liệu · Chương 1  │
│ Tiến độ gần đây       65% │
│ [Hỏi AI] [Làm quiz]       │
├──────────────────────────┤
│ Trang chủ Môn học AI Tiến độ│
└──────────────────────────┘
```

### Màn hình 02 — Danh sách môn học
**Mục đích:** tìm và mở môn.  
**Thành phần:** tiêu đề Môn học, tìm kiếm đơn giản nếu dễ triển khai, nút Thêm môn, danh sách card tên/mô tả ngắn/số tài liệu; empty state. Chọn card mở chi tiết với các mục Tổng quan, Tài liệu và Quiz.

### Màn hình 03 — Tài liệu của môn học
**Mục đích:** thêm và quản lý PDF/TXT gắn với môn.  
**Thành phần:** breadcrumb/tên môn, nút Tải tài liệu lên, nhắc định dạng PDF/TXT, danh sách tên file/trạng thái/ngày thêm; lỗi xử lý có hành động thử lại hoặc xóa. Không hiển thị “đã sẵn sàng” trước khi backend báo ready.

### Màn hình 04 — AI Assistant
**Mục đích:** hỏi đáp trên ngữ cảnh học đã chọn.  
**Thành phần:** app bar StudyFlow AI, bộ chọn môn/tài liệu hiện tại, vùng hội thoại, bong bóng hỏi/đáp, trạng thái trả lời, khối “Nguồn tham khảo” có tên file và trang/định danh đoạn, ô nhập và nút gửi. Khi không có nguồn phù hợp, hiển thị rõ chưa tìm thấy trong tài liệu.

### Màn hình 05 — Làm bài Quiz
**Mục đích:** trả lời câu hỏi MCQ.  
**Thành phần:** tiêu đề Kiểm tra kiến thức, tiến độ “05 / 10”, tên môn/chủ đề, một câu hỏi, nhóm lựa chọn, nút Câu trước/Câu tiếp, nút Nộp bài và cảnh báo câu chưa trả lời. Giữ lựa chọn khi đi qua lại các câu.

### Màn hình 06 — Kết quả và gợi ý ôn tập
**Mục đích:** phản hồi điểm và bước học tiếp.  
**Thành phần:** điểm `/10`, số đúng/sai, tóm tắt theo topic có đủ dữ liệu, topic yếu được xác định từ tỷ lệ đúng, gợi ý xem lại tài liệu hoặc làm quiz luyện tập, nút về Trang chủ/Chi tiết môn.

**Phác thảo:**
```text
┌──────────────────────────┐
│ Kết quả học tập           │
│         8.0 / 10          │
│       8 đúng · 2 sai      │
│ Phân tích theo chủ đề     │
│ Khóa chính/ngoại khóa 90% │
│ Phép JOIN             50% │
│ Gợi ý ôn tập              │
│ Ôn lại phép JOIN ở Ch. 2  │
│ [Làm quiz luyện tập]      │
└──────────────────────────┘
```

## 4. Hành vi UI và giới hạn
- Dùng dữ liệu động; số liệu ví dụ trên wireframe chỉ minh họa, không hard-code làm kết quả giả.
- Hỏi AI luôn thể hiện ngữ cảnh đã chọn và trích dẫn nguồn gần câu trả lời.
- Quiz chỉ cho nộp khi có thể tính điểm từ khóa đáp án hợp lệ; điểm không do LLM quyết định.
- Gợi ý học tập dựa trên dữ liệu quiz hiện có; khi thiếu dữ liệu, nói rõ thay vì hiển thị topic yếu giả.
- Giữ 4 tab ổn định; không thêm tab/cụm tính năng mới nếu chưa được xác nhận.
