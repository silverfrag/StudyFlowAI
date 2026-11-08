# Hợp đồng API và Schema chi tiết — StudyFlow AI (v1.0)

Tài liệu này xác định hợp đồng giao tiếp giữa **Android Client** và **FastAPI Backend**, bám sát `docs/architecture.md`, `docs/requirements.md` và `docs/use-cases.md`.

---

## 1. Quy ước chung

### 1.1 Môi trường và Base URL
- **Local Android Emulator:** `http://10.0.2.2:8000/api/v1`
- **Thiết bị thật / Local Network:** `http://<LAN_IP>:8000/api/v1`
- **Headers chuẩn:**
  - `Content-Type: application/json` (với các endpoint JSON)
  - `Content-Type: multipart/form-data` (với endpoint upload tài liệu)
  - `Accept: application/json`

### 1.2 Định dạng ID và Thời gian
- **ID:** Sử dụng chuỗi UUID v4 (ví dụ: `a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d`).
- **Thời gian:** Chuỗi ISO 8601 UTC (ví dụ: `2026-11-08T09:30:00Z`).

### 1.3 Cấu trúc phản hồi lỗi chuẩn (Standard Error Envelope)
Mọi phản hồi lỗi (HTTP 4xx, 5xx) tuân thủ định dạng sau:
```json
{
  "detail": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Không tìm thấy tài nguyên yêu cầu.",
    "field": null
  }
}
```
Các mã lỗi (`code`) chuẩn:
- `VALIDATION_ERROR`: Dữ liệu đầu vào không hợp lệ.
- `RESOURCE_NOT_FOUND`: Môn học, tài liệu hoặc quiz không tồn tại.
- `UNSUPPORTED_FILE_TYPE`: Chỉ chấp nhận file `.pdf` hoặc `.txt`.
- `FILE_TOO_LARGE`: Dung lượng vượt quá giới hạn 20MB.
- `AI_SERVICE_UNAVAILABLE`: Lỗi kết nối hoặc quota Gemini Developer API.
- `INTERNAL_SERVER_ERROR`: Lỗi hệ thống nội bộ backend.

---

## 2. Health & Hệ thống

### GET `/api/v1/health`
Kiểm tra trạng thái hoạt động của backend và kết nối cơ sở dữ liệu.
- **Response:** `200 OK`
```json
{
  "status": "healthy",
  "database": "connected",
  "version": "1.0.0"
}
```

---

## 3. Môn học (Courses - UC01)

### 3.1 GET `/api/v1/courses`
Lấy danh sách tất cả các môn học của người học.
- **Response:** `200 OK`
```json
[
  {
    "id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
    "name": "Cơ sở dữ liệu",
    "description": "Mô hình quan hệ, SQL, chuẩn hóa dữ liệu",
    "document_count": 3,
    "quiz_count": 2,
    "created_at": "2026-11-08T09:00:00Z"
  }
]
```

### 3.2 POST `/api/v1/courses`
Tạo môn học mới.
- **Request Body:**
```json
{
  "name": "Hệ điều hành",
  "description": "Quản lý tiến trình, bộ nhớ và hệ thống tập tin"
}
```
- **Response:** `201 Created` (Trả về Course Object như trên).

### 3.3 GET `/api/v1/courses/{course_id}`
Lấy thông tin chi tiết một môn học.
- **Response:** `200 OK` (Trả về Course Object).

### 3.4 PATCH `/api/v1/courses/{course_id}`
Cập nhật tên hoặc mô tả môn học.
- **Request Body:**
```json
{
  "name": "Cơ sở dữ liệu nâng cao",
  "description": "Tối ưu hóa câu truy vấn và đánh chỉ mục"
}
```
- **Response:** `200 OK` (Trả về Course Object đã cập nhật).

### 3.5 DELETE `/api/v1/courses/{course_id}`
Xóa môn học và xóa cascade các tài liệu, vector chunks và quiz liên quan.
- **Response:** `204 No Content`

---

## 4. Tài liệu của môn học (Documents - UC02)

### 4.1 POST `/api/v1/courses/{course_id}/documents`
Upload file tài liệu PDF hoặc TXT cho môn học.
- **Headers:** `Content-Type: multipart/form-data`
- **Form Data:**
  - `file`: Binary file (PDF hoặc TXT, tối đa 20MB).
- **Quy tắc:** Backend kiểm tra MIME type và extension, lưu file an toàn tại local storage, tạo bản ghi trạng thái `processing`, sau đó kích hoạt trích xuất văn bản + chunking + tạo embeddings (pgvector).
- **Response:** `202 Accepted`
```json
{
  "id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
  "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
  "title": "Giao_trinh_CSDL_Chuong1.pdf",
  "file_size": 1048576,
  "mime_type": "application/pdf",
  "status": "processing",
  "error_message": null,
  "created_at": "2026-11-08T09:10:00Z"
}
```

### 4.2 GET `/api/v1/courses/{course_id}/documents`
Lấy danh sách tài liệu của một môn học.
- **Response:** `200 OK`
```json
[
  {
    "id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
    "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
    "title": "Giao_trinh_CSDL_Chuong1.pdf",
    "file_size": 1048576,
    "mime_type": "application/pdf",
    "status": "ready",
    "error_message": null,
    "created_at": "2026-11-08T09:10:00Z"
  }
]
```
*Ghi chú trạng thái:* `status` gồm `"processing"` (đang trích xuất/vector hóa), `"ready"` (đã sẵn sàng để hỏi đáp/tạo quiz), `"error"` (lỗi xử lý).

### 4.3 GET `/api/v1/documents/{document_id}/status`
Polling kiểm tra trạng thái xử lý của tài liệu.
- **Response:** `200 OK`
```json
{
  "id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
  "status": "ready",
  "error_message": null
}
```

### 4.4 DELETE `/api/v1/documents/{document_id}`
Xóa tài liệu, file vật lý và toàn bộ vector chunk tương ứng trong cơ sở dữ liệu.
- **Response:** `204 No Content`

---

## 5. Trợ lý học tập AI & Dẫn nguồn RAG (Assistant - UC03)

### 5.1 POST `/api/v1/assistant/ask`
Đặt câu hỏi kiến thức theo ngữ cảnh môn học hoặc một tài liệu cụ thể.
- **Request Body:**
```json
{
  "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
  "document_id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
  "question": "Khóa chính là gì và điều kiện của nó?"
}
```
*Ghi chú:* `document_id` có thể là `null` nếu người học muốn hỏi trên toàn bộ tài liệu đã `ready` của môn học.

- **Response:** `200 OK`
```json
{
  "answer": "Khóa chính (Primary Key) là một hoặc tập hợp các thuộc tính dùng để phân biệt duy nhất mỗi bộ (bản ghi) trong quan hệ. Điều kiện tiên quyết của khóa chính là giá trị không được trùng lặp và không được phép nhận giá trị NULL (tính toàn vẹn thực thể).",
  "found_in_context": true,
  "citations": [
    {
      "document_id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
      "document_title": "Giao_trinh_CSDL_Chuong1.pdf",
      "page_number": 12,
      "chunk_id": "a9c8b7-chunk-01",
      "snippet": "...Khóa chính là thuộc tính có giá trị duy nhất trong quan hệ và không bao giờ được phép mang giá trị rỗng (NULL)..."
    }
  ]
}
```
- **Quy tắc khi không tìm thấy căn cứ:**
  - `found_in_context: false`
  - `citations: []`
  - `answer: "Nội dung này chưa được tìm thấy trong các tài liệu đã nạp của môn học."`
  - Tuyệt đối không sinh citation giả mạo.

---

## 6. Bài kiểm tra trắc nghiệm (Quizzes - UC04, UC05, UC06)

### 6.1 POST `/api/v1/quizzes`
Sinh bài kiểm tra trắc nghiệm (MCQ) từ tài liệu nạp của môn học qua Gemini Developer API.
- **Request Body:**
```json
{
  "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
  "document_id": null,
  "topic": "Khóa chính và Ràng buộc toàn vẹn",
  "question_count": 5
}
```
*Giới hạn:* `question_count` từ 3 đến 10 câu.

- **Response:** `201 Created`
*(BẢO MẬT: Không bao giờ trả `correct_option_id` cho client trước khi nộp bài)*
```json
{
  "id": "q9f8e7d6-3333-4444-8555-666677778888",
  "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
  "title": "Quiz: Khóa chính và Ràng buộc toàn vẹn",
  "total_questions": 5,
  "questions": [
    {
      "id": "ques-001",
      "prompt": "Thuộc tính khóa chính có thể nhận giá trị NULL không?",
      "topic": "Ràng buộc toàn vẹn",
      "options": [
        { "id": "A", "text": "Có thể nhận NULL nếu có chỉ mục" },
        { "id": "B", "text": "Tuyệt đối không được phép nhận NULL" },
        { "id": "C", "text": "Tùy thuộc vào thiết kế bảng con" },
        { "id": "D", "text": "Chỉ khi kết hợp với khóa ngoại" }
      ]
    }
  ],
  "created_at": "2026-11-08T09:20:00Z"
}
```

### 6.2 GET `/api/v1/quizzes/{quiz_id}`
Lấy lại câu hỏi của một quiz đã tạo (vẫn không trả `correct_option_id`).
- **Response:** `200 OK` (Cấu trúc tương tự như 6.1).

### 6.3 POST `/api/v1/quizzes/{quiz_id}/submit`
Nộp bài làm, backend chấm điểm xác định (deterministic) dựa trên khóa đáp án lưu trong database.
- **Request Body:**
```json
{
  "answers": [
    {
      "question_id": "ques-001",
      "selected_option_id": "B"
    }
  ]
}
```
- **Response:** `200 OK`
```json
{
  "attempt_id": "att-8888-9999-aaaa-bbbbccccdddd",
  "quiz_id": "q9f8e7d6-3333-4444-8555-666677778888",
  "score": 10.0,
  "total_questions": 1,
  "correct_count": 1,
  "incorrect_count": 0,
  "submitted_at": "2026-11-08T09:25:00Z",
  "topic_stats": [
    {
      "topic": "Ràng buộc toàn vẹn",
      "total_questions": 1,
      "correct_count": 1,
      "accuracy": 1.0
    }
  ],
  "details": [
    {
      "question_id": "ques-001",
      "prompt": "Thuộc tính khóa chính có thể nhận giá trị NULL không?",
      "selected_option_id": "B",
      "correct_option_id": "B",
      "is_correct": true,
      "explanation": "Theo quy tắc toàn vẹn thực thể, khóa chính không bao giờ được phép mang giá trị NULL.",
      "citation": {
        "document_title": "Giao_trinh_CSDL_Chuong1.pdf",
        "page_number": 12
      }
    }
  ]
}
```

---

## 7. Tiến độ & Gợi ý ôn tập (Progress & Recommendations - UC06, UC07)

### 7.1 GET `/api/v1/progress/summary`
Lấy số liệu tổng quan hiển thị trên Màn hình 01 (Trang chủ).
- **Response:** `200 OK`
```json
{
  "total_courses": 4,
  "total_quizzes_completed": 12,
  "average_score": 7.8,
  "recent_activity": {
    "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
    "course_name": "Cơ sở dữ liệu",
    "last_quiz_title": "Quiz: Khóa chính và Ràng buộc toàn vẹn",
    "last_score": 8.0,
    "last_active_at": "2026-11-08T09:25:00Z"
  }
}
```

### 7.2 GET `/api/v1/recommendations`
Lấy danh sách các chủ đề (topic) có kết quả thấp cần ôn tập.
- **Quy tắc lọc Topic yếu:**
  - Người dùng đã trả lời tối thiểu `>= 3` câu hỏi liên quan đến topic.
  - Tỷ lệ trả lời đúng `accuracy < 0.60` (dưới 60%).
- **Response:** `200 OK`
```json
[
  {
    "topic": "Phép JOIN trong quan hệ",
    "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
    "course_name": "Cơ sở dữ liệu",
    "total_answered": 6,
    "correct_count": 2,
    "accuracy": 0.33,
    "recommended_action": "Ôn lại lý thuyết phép nối (INNER, LEFT, RIGHT JOIN) và làm quiz luyện tập bổ sung.",
    "suggested_document": {
      "id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
      "title": "Giao_trinh_CSDL_Chuong2_JOIN.pdf"
    }
  }
]
```
*(Nếu chưa có đủ dữ liệu >= 3 câu hỏi cho bất kỳ topic nào, trả về mảng rỗng `[]`, app sẽ hiển thị thông báo khuyến khích làm thêm bài quiz).*
