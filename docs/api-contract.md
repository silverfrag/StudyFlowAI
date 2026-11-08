# Hợp đồng API và Schema chi tiết — StudyFlow AI (v1.1)

Tài liệu này xác định hợp đồng giao tiếp giữa **Android Client** và **FastAPI Backend**, bám sát `docs/architecture.md`, `docs/requirements.md`, `docs/use-cases.md` và các quy định tại `AGENTS.md`.

---

## 1. Môi trường mạng & Giao thức

### 1.1 Phân biệt HTTP phát triển và HTTPS Production
- **Môi trường phát triển cục bộ (Local Development):**
  - Chạy giao thức **HTTP** (không mã hóa) để thuận tiện debug:
    - Android Emulator truy cập backend host: `http://10.0.2.2:8000/api/v1`
    - Thiết bị Android thật qua mạng LAN: `http://<LAN_IP>:8000/api/v1`
  - *Yêu cầu cấu hình Android:* Bật `android:usesCleartextTraffic="true"` trong `AndroidManifest.xml` (hoặc cấu hình `network_security_config.xml` cho phép domain local) ở môi trường debug.
- **Môi trường triển khai / Máy chủ thật (Staging / Production):**
  - Bắt buộc sử dụng giao thức **HTTPS** với chứng chỉ TLS/SSL hợp lệ.
  - Vô hiệu hóa cleartext traffic trên Android client release build.

### 1.2 Headers chuẩn
- Request JSON: `Content-Type: application/json`, `Accept: application/json`
- Upload file: `Content-Type: multipart/form-data`, `Accept: application/json`

---

## 2. Quy ước ID và Xử lý Lỗi

### 2.1 Chuẩn hóa ID
- **Thực thể (Entities):** Toàn bộ ID của các thực thể (`course_id`, `document_id`, `chunk_id`, `quiz_id`, `question_id`, `attempt_id`) bắt buộc dùng định dạng **UUID v4 chuẩn** (chuỗi 36 ký tự: `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`).
- **Lựa chọn đáp án (Options):** Dùng chuỗi định danh chữ cái cố định: `"A" | "B" | "C" | "D"`.
- **Thời gian:** Chuỗi ISO 8601 UTC kết thúc bằng `Z` (ví dụ: `2026-11-08T09:30:00Z`).

### 2.2 Cấu trúc phản hồi lỗi chuẩn (Standard Error Envelope)
Mọi phản hồi lỗi (HTTP 4xx, 5xx) tuân thủ envelope thống nhất:
```json
{
  "detail": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Không tìm thấy tài nguyên yêu cầu.",
    "field": null
  }
}
```

### 2.3 Xử lý lỗi Validation của FastAPI
Mặc định FastAPI trả về lỗi validation Pydantic với format mảng thô (HTTP 422). Backend StudyFlow AI sử dụng custom handler `RequestValidationError` để chuẩn hóa về envelope chung:
```json
{
  "detail": {
    "code": "VALIDATION_ERROR",
    "message": "Dữ liệu gửi lên không hợp lệ.",
    "field": "body.question_count",
    "errors": [
      {
        "field": "body.question_count",
        "message": "Giá trị phải nhỏ hơn hoặc bằng 10."
      }
    ]
  }
}
```
**Bảng mã lỗi chuẩn (`code`):**
- `VALIDATION_ERROR`: Dữ liệu đầu vào sai định dạng hoặc vi phạm ràng buộc schema.
- `RESOURCE_NOT_FOUND`: Môn học, tài liệu hoặc quiz không tồn tại.
- `UNSUPPORTED_FILE_TYPE`: Chỉ chấp nhận file `.pdf` hoặc `.txt`.
- `FILE_TOO_LARGE`: Dung lượng vượt quá giới hạn.
- `QUIZ_ALREADY_SUBMITTED`: Lượt làm quiz này đã được nộp trước đó.
- `AI_SERVICE_UNAVAILABLE`: Lỗi quota, timeout hoặc kết nối Gemini Developer API.
- `INTERNAL_SERVER_ERROR`: Lỗi hệ thống nội bộ backend.

---

## 3. Các điểm đề xuất — Chờ nhóm thống nhất

> [!IMPORTANT]
> Các nội dung sau được đánh dấu là **[Đề xuất — Chờ nhóm thống nhất]** trước khi chốt cố định trong code:
> 1. **Quy tắc xóa môn học (Cascade Delete):** 
>    - *Phương án đề xuất:* Xóa môn học (`DELETE /api/v1/courses/{course_id}`) sẽ tự động cascade xóa tất cả tài liệu, file vật lý, vector chunks, quiz và lịch sử làm bài liên quan.
>    - *Phương án thay thế:* Chặn xóa nếu môn còn tài liệu/quiz; bắt buộc người dùng xóa hết tài liệu con trước.
> 2. **Giới hạn dung lượng và số lượng Upload tài liệu:**
>    - *Phương án đề xuất:* Định dạng hỗ trợ: `.pdf`, `.txt`. Dung lượng tối đa: **20 MB/file**. Mỗi lần upload 1 file. Không giới hạn tổng số tài liệu trong 1 môn ở bản MVP.
> 3. **Giới hạn số câu hỏi Quiz (Quiz Question Range):**
>    - *Phương án đề xuất:* Mỗi bài quiz được sinh có từ **3 đến 10 câu hỏi** (mặc định khuyến nghị: 5 câu).
> 4. **Ngưỡng xác định Topic yếu (Weak Topic Threshold):**
>    - *Phương án đề xuất:* Một topic được coi là yếu khi tỷ lệ đúng `accuracy < 0.60` (dưới 60%) VÀ người học đã làm tối thiểu `>= 3` câu hỏi thuộc topic đó.

---

## 4. Đặc tả Endpoints chi tiết

### 4.1 Health Check
- **`GET /api/v1/health`**
- **Response:** `200 OK`
```json
{
  "status": "healthy",
  "database": "connected",
  "version": "1.0.0"
}
```

---

### 4.2 Quản lý Môn học (Courses - UC01)

#### `GET /api/v1/courses`
Lấy danh sách tất cả các môn học.
- **Response:** `200 OK`
```json
[
  {
    "id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
    "name": "Cơ sở dữ liệu",
    "description": "Mô hình quan hệ, SQL, chuẩn hóa dữ liệu",
    "document_count": 2,
    "quiz_count": 1,
    "created_at": "2026-11-08T09:00:00Z"
  }
]
```

#### `POST /api/v1/courses`
Tạo môn học mới.
- **Request Body:**
```json
{
  "name": "Hệ điều hành",
  "description": "Quản lý tiến trình, bộ nhớ và tập tin"
}
```
- **Response:** `201 Created` (Trả về Course Object như trên).

#### `GET /api/v1/courses/{course_id}`
Chi tiết một môn học.
- **Response:** `200 OK` (Trả về Course Object).

#### `PATCH /api/v1/courses/{course_id}`
Cập nhật môn học.
- **Request Body:**
```json
{
  "name": "Cơ sở dữ liệu nâng cao",
  "description": "Tối ưu hóa truy vấn"
}
```
- **Response:** `200 OK` (Trả về Course Object đã cập nhật).

#### `DELETE /api/v1/courses/{course_id}`
Xóa môn học.
- *[Đề xuất — Chờ nhóm thống nhất: Cascade delete toàn bộ tài liệu, chunks, quizzes]*
- **Response:** `204 No Content`

---

### 4.3 Quản lý Tài liệu (Documents - UC02)

#### `POST /api/v1/courses/{course_id}/documents`
Upload file tài liệu PDF hoặc TXT.
- *[Đề xuất — Chờ nhóm thống nhất: Định dạng PDF/TXT, dung lượng tối đa 20MB]*
- **Headers:** `Content-Type: multipart/form-data`
- **Form Data:** `file`: Binary file
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

#### `GET /api/v1/courses/{course_id}/documents`
Danh sách tài liệu của môn học.
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

#### `GET /api/v1/documents/{document_id}/status`
Polling kiểm tra tiến độ xử lý tài liệu.
- **Response:** `200 OK`
```json
{
  "id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
  "status": "ready",
  "error_message": null
}
```

#### `DELETE /api/v1/documents/{document_id}`
Xóa tài liệu và các vector chunks liên quan.
- **Response:** `204 No Content`

---

### 4.4 Trợ lý học tập AI dẫn nguồn RAG (Assistant - UC03)

#### `POST /api/v1/assistant/ask`
Hỏi đáp theo tài liệu đã nạp.
- **Request Body:**
```json
{
  "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
  "document_id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
  "question": "Khóa chính là gì và điều kiện của nó?"
}
```
*(Nếu muốn hỏi trên toàn bộ tài liệu của môn học, truyền `document_id: null`)*

- **Response:** `200 OK`
```json
{
  "answer": "Khóa chính (Primary Key) là tập hợp một hoặc nhiều thuộc tính dùng để phân biệt duy nhất mỗi bộ trong quan hệ. Điều kiện tiên quyết: không được trùng lặp và tuyệt đối không nhận giá trị NULL.",
  "found_in_context": true,
  "citations": [
    {
      "document_id": "d3a2e104-5555-4666-8777-1a2b3c4d5e6f",
      "document_title": "Giao_trinh_CSDL_Chuong1.pdf",
      "page_number": 12,
      "chunk_id": "a9c8b765-2222-4333-8444-010203040506",
      "snippet": "...Khóa chính là thuộc tính có giá trị duy nhất trong quan hệ và không bao giờ được phép mang giá trị rỗng (NULL)..."
    }
  ]
}
```
*Trường hợp không tìm thấy căn cứ:* `found_in_context: false`, `citations: []`, `answer: "Nội dung này chưa được tìm thấy trong tài liệu đã nạp của môn học."`

---

### 4.5 Bài kiểm tra trắc nghiệm & Chấm điểm (Quizzes - UC04, UC05, UC06)

#### `POST /api/v1/quizzes` (Tạo Quiz)
- *[Đề xuất — Chờ nhóm thống nhất: Số lượng câu hỏi từ 3 đến 10]*
- **Request Body:**
```json
{
  "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
  "document_id": null,
  "topic": "Khóa chính và Ràng buộc toàn vẹn",
  "question_count": 3
}
```

- **Response:** `201 Created`
*(BẢO MẬT: Không bao giờ trả `correct_option_id` cho client trước khi nộp bài)*
```json
{
  "id": "q9f8e7d6-3333-4444-8555-666677778888",
  "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
  "title": "Quiz: Khóa chính và Ràng buộc toàn vẹn",
  "total_questions": 3,
  "questions": [
    {
      "id": "e1111111-1111-4111-8111-111111111111",
      "prompt": "Thuộc tính khóa chính có thể nhận giá trị NULL không?",
      "topic": "Ràng buộc toàn vẹn",
      "options": [
        { "id": "A", "text": "Có thể nhận NULL nếu có chỉ mục" },
        { "id": "B", "text": "Tuyệt đối không được phép nhận NULL" },
        { "id": "C", "text": "Tùy thuộc vào thiết kế bảng con" },
        { "id": "D", "text": "Chỉ khi kết hợp với khóa ngoại" }
      ]
    },
    {
      "id": "e2222222-2222-4222-8222-222222222222",
      "prompt": "Một bảng quan hệ có thể có tối đa bao nhiêu khóa chính?",
      "topic": "Khóa chính",
      "options": [
        { "id": "A", "text": "Duy nhất 1 khóa chính" },
        { "id": "B", "text": "2 khóa chính" },
        { "id": "C", "text": "Không giới hạn" },
        { "id": "D", "text": "Tối đa bằng số thuộc tính" }
      ]
    },
    {
      "id": "e3333333-3333-4333-8333-333333333333",
      "prompt": "Khóa ngoại (Foreign Key) dùng để làm gì?",
      "topic": "Ràng buộc tham chiếu",
      "options": [
        { "id": "A", "text": "Tăng tốc độ truy vấn SELECT" },
        { "id": "B", "text": "Liên kết dữ liệu và bảo đảm toàn vẹn tham chiếu" },
        { "id": "C", "text": "Thay thế khóa chính khi khóa chính bị đầy" },
        { "id": "D", "text": "Tự động mã hóa cột dữ liệu" }
      ]
    }
  ],
  "created_at": "2026-11-08T09:20:00Z"
}
```

#### `GET /api/v1/quizzes/{quiz_id}`
Lấy lại đề thi của một bài quiz đã tạo (không chứa đáp án).
- **Response:** `200 OK` (Cấu trúc tương tự như `POST /api/v1/quizzes`).

---

#### `POST /api/v1/quizzes/{quiz_id}/submit` (Nộp bài & Chấm điểm)

##### Quy tắc nghiệp vụ Nộp bài & Chấm điểm:
1. **Câu bỏ trống (Unanswered Questions):**
   - Người học không bắt buộc phải trả lời hết câu hỏi để nộp bài.
   - Đối với câu bỏ trống, client có thể gửi `"selected_option_id": null` hoặc bỏ qua câu hỏi đó trong danh sách `answers`.
   - Backend tự động so khớp: nếu `selected_option_id` là `null` hoặc không có trong payload, câu đó được tính là **sai** (`is_correct: false`), không được cộng điểm.
2. **Quy tắc Nộp lại (Resubmission / Retry Rule):**
   - Mỗi lần submit ứng với một lượt làm bài (`QuizAttempt`) độc lập được lưu trong DB.
   - Trong phạm vi MVP, mỗi bài quiz cho phép làm lại bằng cách tạo một attempt mới qua cùng endpoint này. Tuy nhiên, một `attempt_id` đã hoàn thành thì không thể nộp đè; mọi kết quả lần nộp mới sẽ ghi nhận thêm bản ghi attempt riêng biệt để theo dõi tiến độ học tập.
3. **Công thức tính điểm xác định (Deterministic Grading Formula):**
   - Thang điểm chuẩn: **10.0**.
   - Công thức: `score = round((correct_count / total_questions) * 10.0, 1)`
   - Chấm điểm tuyệt đối bằng logic so khớp chuỗi đáp án chuẩn tại backend (`selected_option_id == correct_option_id`). Tuyệt đối không dùng LLM để chấm điểm.

##### Ví dụ Đồng bộ (3 câu hỏi):
Người học trả lời:
- Câu 1 (`e1111...`): chọn `"B"` (Đúng)
- Câu 2 (`e2222...`): chọn `"C"` (Sai, đáp án chuẩn là `"A"`)
- Câu 3 (`e3333...`): bỏ trống (`null` hoặc không chọn) -> (Sai, đáp án chuẩn là `"B"`)

- **Request Body:**
```json
{
  "answers": [
    {
      "question_id": "e1111111-1111-4111-8111-111111111111",
      "selected_option_id": "B"
    },
    {
      "question_id": "e2222222-2222-4222-8222-222222222222",
      "selected_option_id": "C"
    },
    {
      "question_id": "e3333333-3333-4333-8333-333333333333",
      "selected_option_id": null
    }
  ]
}
```

- **Response:** `200 OK`
```json
{
  "attempt_id": "att-88888888-9999-4aaa-bbbb-ccccdddd0001",
  "quiz_id": "q9f8e7d6-3333-4444-8555-666677778888",
  "score": 3.3,
  "total_questions": 3,
  "correct_count": 1,
  "incorrect_count": 2,
  "submitted_at": "2026-11-08T09:25:00Z",
  "topic_stats": [
    {
      "topic": "Ràng buộc toàn vẹn",
      "total_questions": 1,
      "correct_count": 1,
      "accuracy": 1.0
    },
    {
      "topic": "Khóa chính",
      "total_questions": 1,
      "correct_count": 0,
      "accuracy": 0.0
    },
    {
      "topic": "Ràng buộc tham chiếu",
      "total_questions": 1,
      "correct_count": 0,
      "accuracy": 0.0
    }
  ],
  "details": [
    {
      "question_id": "e1111111-1111-4111-8111-111111111111",
      "prompt": "Thuộc tính khóa chính có thể nhận giá trị NULL không?",
      "selected_option_id": "B",
      "correct_option_id": "B",
      "is_correct": true,
      "explanation": "Theo quy tắc toàn vẹn thực thể, khóa chính không bao giờ được phép mang giá trị NULL.",
      "citation": {
        "document_title": "Giao_trinh_CSDL_Chuong1.pdf",
        "page_number": 12
      }
    },
    {
      "question_id": "e2222222-2222-4222-8222-222222222222",
      "prompt": "Một bảng quan hệ có thể có tối đa bao nhiêu khóa chính?",
      "selected_option_id": "C",
      "correct_option_id": "A",
      "is_correct": false,
      "explanation": "Mỗi quan hệ chỉ có duy nhất một khóa chính, dù khóa chính đó có thể gồm nhiều thuộc tính kết hợp (composite key).",
      "citation": {
        "document_title": "Giao_trinh_CSDL_Chuong1.pdf",
        "page_number": 15
      }
    },
    {
      "question_id": "e3333333-3333-4333-8333-333333333333",
      "prompt": "Khóa ngoại (Foreign Key) dùng để làm gì?",
      "selected_option_id": null,
      "correct_option_id": "B",
      "is_correct": false,
      "explanation": "Khóa ngoại dùng để thể hiện mối quan hệ giữa các bảng và đảm bảo tính toàn vẹn tham chiếu.",
      "citation": {
        "document_title": "Giao_trinh_CSDL_Chuong1.pdf",
        "page_number": 20
      }
    }
  ]
}
```

---

### 4.6 Tiến độ & Gợi ý ôn tập (Progress & Recommendations - UC06, UC07)

#### `GET /api/v1/progress/summary`
Lấy tổng quan tiến độ học tập cho Màn hình 01 (Trang chủ).
- **Response:** `200 OK`
```json
{
  "total_courses": 4,
  "total_quizzes_completed": 12,
  "average_score": 7.5,
  "recent_activity": {
    "course_id": "c1f7a28b-1111-4222-8333-d4e5f6a7b8c9",
    "course_name": "Cơ sở dữ liệu",
    "last_quiz_title": "Quiz: Khóa chính và Ràng buộc toàn vẹn",
    "last_score": 8.0,
    "last_active_at": "2026-11-08T09:25:00Z"
  }
}
```

#### `GET /api/v1/recommendations`
Lấy danh sách topic yếu và gợi ý học tập cho Màn hình 06 / Màn hình Tiến độ.
- *[Đề xuất — Chờ nhóm thống nhất: Ngưỡng topic yếu accuracy < 0.60 và số câu làm >= 3]*
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
*(Nếu chưa có topic nào thỏa mãn ngưỡng tối thiểu >= 3 câu, API trả về mảng rỗng `[]` để UI hiển thị empty state khuyến khích người học làm thêm quiz).*
