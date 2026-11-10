# Sổ tay Hướng dẫn Phát triển Backend & AI — Dành cho Thành viên 2 (StudyFlow AI)

Chào bạn! Tài liệu này được thiết kế riêng để bạn có thể mở dự án trong bất kỳ IDE nào (VS Code, PyCharm, Cursor, Windsurf,...) và bắt tay vào phát triển toàn bộ module **Backend (FastAPI + PostgreSQL/pgvector + Gemini AI)** một cách độc lập, rõ ràng và mượt mà nhất.

---

## 1. Ranh giới & Trách nhiệm công việc

- **Phạm vi sở hữu:** Bạn sở hữu và làm việc toàn quyền trong thư mục `backend/`.
- **Nhánh Git khuyến nghị:** Tạo và làm việc trên nhánh riêng `feature/backend-service` từ nhánh `develop`.
- **Mục tiêu 2 tuần:**
  1. Dựng FastAPI service dạng modular monolith.
  2. Quản lý PostgreSQL và tìm kiếm vector với pgvector.
  3. Upload và trích xuất file PDF/TXT cục bộ, chia chunk và tính embeddings.
  4. Trợ lý RAG hỏi đáp dựa trên tài liệu có trích dẫn nguồn xác thực (không bịa citation).
  5. Sinh bài kiểm tra trắc nghiệm (MCQ) bằng Gemini và chấm điểm xác định (deterministic grading).
  6. Thống kê kết quả và gợi ý topic yếu theo ngưỡng quy định.

> [!IMPORTANT]
> **Hợp đồng API bất biến:** Mọi request/response JSON, tên trường (fields), và status codes phải tuân thủ chính xác theo tài liệu [docs/api-contract.md](file:///e:/StudyFlowAI/docs/api-contract.md). Phía Android sẽ dựa 100% vào hợp đồng này để kết nối. Nếu bạn muốn thay đổi bất kỳ trường nào, hãy báo cho nhóm để cập nhật hợp đồng trước khi sửa code.

---

## 2. Hướng dẫn thiết lập môi trường trong IDE

### Bước 2.1: Mở dự án trong IDE
- Mở thư mục gốc `E:\StudyFlowAI` (hoặc mở trực tiếp thư mục `E:\StudyFlowAI\backend`).
- Mở terminal trong IDE tại thư mục `backend/`.

### Bước 2.2: Khởi tạo Virtual Environment
Dự án sử dụng Python 3.11 đến 3.14:
```bash
cd backend
python -m venv .venv

# Kích hoạt trên Windows PowerShell:
.\.venv\Scripts\Activate.ps1

# (Nếu bị lỗi script execution policy trên PowerShell, chạy lệnh này trước:)
# Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
```

### Bước 2.3: Cài đặt thư viện phụ thuộc
```bash
pip install --upgrade pip
pip install -r requirements.txt
```

### Bước 2.4: Khởi động Database (PostgreSQL 16 + pgvector)
Đảm bảo bạn đã bật Docker Desktop trên máy, sau đó chạy:
```bash
docker compose up -d
```
Container `studyflow-postgres` sẽ chạy ở cổng `5432`, đã kích hoạt sẵn extension `vector`.

### Bước 2.5: Cấu hình biến môi trường `.env`
Tạo file `.env` từ `.env.example`:
```bash
cp .env.example .env
```
Mở `.env` và điền `GEMINI_API_KEY` của bạn (lấy tại [Google AI Studio](https://aistudio.google.com/)):
```env
APP_NAME="StudyFlow AI Backend"
APP_ENV="development"
APP_DEBUG=True
PORT=8000
HOST="0.0.0.0"

DATABASE_URL="postgresql+asyncpg://postgres:postgrespassword@localhost:5432/studyflow_db"
STORAGE_DIR="./storage"
MAX_UPLOAD_SIZE_MB=20

GEMINI_API_KEY="AIzaSy..." # Thay bằng API Key thật của bạn
GEMINI_MODEL="gemini-3.8-flash"
GEMINI_EMBEDDING_MODEL="gemini-embedding-001"
```

### Bước 2.6: Chạy thử Server Backend
```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```
- Mở trình duyệt truy cập tài liệu Swagger UI tự động: [`http://localhost:8000/docs`](http://localhost:8000/docs)
- Kiểm tra endpoint Health Check: [`http://localhost:8000/api/v1/health`](http://localhost:8000/api/v1/health)

---

## 3. Cấu trúc mã nguồn trong `backend/`

Cấu trúc đã được bố trí dạng **Modular Monolith** sạch sẽ, dễ mở rộng:

```text
backend/
├── app/
│   ├── api/
│   │   └── v1/
│   │       ├── health.py        # GET /api/v1/health
│   │       ├── courses.py       # CRUD Môn học (UC01)
│   │       ├── documents.py     # Upload & Quản lý PDF/TXT (UC02)
│   │       ├── assistant.py     # RAG Q&A có dẫn nguồn (UC03)
│   │       ├── quizzes.py       # Sinh Quiz & Chấm điểm (UC04, UC05)
│   │       └── progress.py      # Tiến độ & Gợi ý ôn tập (UC06, UC07)
│   ├── core/
│   │   ├── config.py            # Cấu hình Pydantic Settings từ .env
│   │   └── database.py          # Kết nối Async SQLAlchemy & pgvector
│   ├── models/                  # SQLAlchemy ORM Models
│   │   ├── course.py            # Bảng Course
│   │   ├── document.py          # Bảng Document, DocumentChunk (pgvector)
│   │   └── quiz.py              # Bảng Quiz, Question, QuizAttempt, Answer
│   ├── schemas/                 # Pydantic Schemas khớp 100% hợp đồng API
│   │   ├── common.py            # StandardErrorEnvelope
│   │   ├── course.py
│   │   ├── document.py
│   │   ├── assistant.py
│   │   ├── quiz.py
│   │   └── progress.py
│   ├── services/                # Tầng xử lý nghiệp vụ
│   │   ├── document_service.py  # Trích xuất PDF/TXT (pypdf) & chunking
│   │   ├── rag_service.py       # Vector search pgvector & build context
│   │   ├── gemini_service.py    # Gọi Gemini Developer API (text & embedding)
│   │   └── quiz_service.py      # Sinh MCQ & chấm điểm xác định
│   └── main.py                  # Entry point, CORS, Custom Error Handlers
├── storage/                     # Thư mục lưu file PDF/TXT vật lý (đã gitignore)
├── .env.example
├── docker-compose.yml           # PostgreSQL 16 + pgvector
├── requirements.txt
└── README.md
```

---

## 4. Kế hoạch triển khai chi tiết từng phần việc

### Phần 1: Database & Schema ORM (`app/models/`)
Tạo các bảng với trường dữ liệu chuẩn (sử dụng UUID v4 cho khóa chính):
1. **`Course`**: `id` (UUID), `name`, `description`, `created_at`.
2. **`Document`**: `id` (UUID), `course_id` (FK), `title`, `file_path`, `file_size`, `mime_type`, `status` (`processing` / `ready` / `error`), `error_message`, `created_at`.
3. **`DocumentChunk`**: `id` (UUID), `document_id` (FK), `course_id` (FK), `text`, `page_number` (int, có thể null), `ordinal` (thứ tự chunk), `embedding` (`Vector(768)`).
4. **`Quiz`**: `id` (UUID), `course_id` (FK), `title`, `total_questions`, `created_at`.
5. **`Question`**: `id` (UUID), `quiz_id` (FK), `prompt`, `topic`, `options` (JSONB: `[{"id": "A", "text": "..."}]`), `correct_option_id` (chuỗi `"A"`, `"B"`, `"C"`, `"D"`), `explanation`, `source_chunk_id` (FK, nullable).
6. **`QuizAttempt`**: `id` (UUID), `quiz_id` (FK), `score` (float), `total_questions`, `correct_count`, `incorrect_count`, `submitted_at`.
7. **`Answer`**: `id` (UUID), `attempt_id` (FK), `question_id` (FK), `selected_option_id` (nullable), `is_correct` (boolean).

---

### Phần 2: CRUD Môn học (`app/api/v1/courses.py`)
- Viết các hàm CRUD theo chuẩn: `GET /courses`, `POST /courses`, `GET /courses/{id}`, `PATCH /courses/{id}`, `DELETE /courses/{id}`.
- Khi truy vấn danh sách môn học, tính kèm `document_count` và `quiz_count` để trả về cho UI.

---

### Phần 3: Xử lý Upload Tài liệu & Vector Hóa (`app/services/document_service.py`)
1. **Kiểm tra file:** Chỉ cho phép file đuôi `.pdf` hoặc `.txt`; kích thước file `<= 20 MB`.
2. **Lưu file an toàn:** Sinh tên file ngẫu nhiên với UUID (ví dụ: `d3a2e104..._filename.pdf`), lưu vào thư mục `STORAGE_DIR` (mặc định `./storage`).
3. **Trích xuất nội dung:**
   - File TXT: Đọc trực tiếp bằng UTF-8 (xử lý fallback các encoding khác).
   - File PDF: Dùng thư viện `pypdf` để duyệt từng trang, trích xuất text kèm `page_number`.
4. **Chunking:** Chia nhỏ văn bản thành các đoạn (chunks) khoảng 300 - 500 từ (words) hoặc ~800 ký tự, có độ gối đầu (overlap ~50-100 từ) để không mất ngữ cảnh giữa các đoạn.
5. **Embeddings:** Gọi model `gemini-embedding-001` (`output_dimensionality=768`) của Gemini để tạo vector 768 chiều cho từng chunk.
6. **Lưu vào PostgreSQL:** Insert các chunks kèm vector vào bảng `DocumentChunk`.
7. **Cập nhật trạng thái:** Chuyển `Document.status` từ `"processing"` sang `"ready"` (hoặc `"error"` kèm lý do nếu gặp sự cố).

---

### Phần 4: Trợ lý RAG có trích dẫn nguồn (`app/services/rag_service.py` & `assistant.py`)
1. Nhận câu hỏi từ Android: `{ "course_id": "...", "document_id": "...", "question": "..." }`.
2. Tạo embedding cho câu hỏi với `gemini-embedding-001` (`task_type="RETRIEVAL_QUERY"`).
3. Tìm kiếm top $k$ chunks có độ tương đồng cosin cao nhất (sử dụng toán tử `<=>` của pgvector: `ORDER BY embedding <=> query_vector LIMIT 4`).
4. Xây dựng prompt gửi cho Gemini:
   - Truyền nội dung các chunks đã tìm được kèm `chunk_id`, `document_title`, `page_number`.
   - **Ràng buộc nghiêm ngặt:** Chỉ trả lời dựa trên ngữ cảnh được cung cấp. Nếu ngữ cảnh không có thông tin, trả về `found_in_context: false` và thông báo không tìm thấy. Tuyệt đối không tự bịa thông tin và trích dẫn.
5. Trả về đúng schema [docs/api-contract.md](file:///e:/StudyFlowAI/docs/api-contract.md): `answer`, `found_in_context`, và danh sách `citations`.

---

### Phần 5: Sinh Quiz & Chấm điểm xác định (`app/services/quiz_service.py` & `quizzes.py`)

#### 5.1 Sinh Quiz:
- Truy xuất các chunks ngẫu nhiên hoặc theo `topic` được chọn.
- Gửi prompt yêu cầu Gemini trả về JSON có cấu trúc (sử dụng tính năng `response_schema` hoặc structured JSON của Gemini):
  - Mỗi câu hỏi gồm: `prompt`, 4 `options` (A, B, C, D), `correct_option_id`, `topic`, `explanation`.
- Lưu bài quiz và các câu hỏi vào DB.
- **LƯU Ý BẢO MẬT:** Khi trả về response cho client, lọc bỏ trường `correct_option_id` và `explanation` để người học không xem được đáp án trước khi nộp bài.

#### 5.2 Nộp bài & Chấm điểm (Deterministic Grading):
- Client gửi danh sách lựa chọn: `[{"question_id": "...", "selected_option_id": "B"}]`.
- **Tuyệt đối không dùng LLM để chấm điểm!** Backend tự đối chiếu:
  - Nếu `selected_option_id == question.correct_option_id`: tính là đúng (`is_correct = True`), tăng `correct_count`.
  - Nếu `selected_option_id` là `null` (câu bỏ trống) hoặc khác đáp án chuẩn: tính là sai (`is_correct = False`).
- Tính điểm theo công thức chuẩn:
  $$\text{score} = \text{round}\left(\frac{\text{correct\_count}}{\text{total\_questions}} \times 10.0, 1\right)$$
- Lưu vào `QuizAttempt` và `Answer`.
- Trả về response chi tiết gồm điểm số, đúng/sai từng câu, giải thích và thống kê theo từng `topic`.

---

### Phần 6: Tiến độ & Gợi ý Topic yếu (`app/api/v1/progress.py`)
- `GET /api/v1/progress/summary`: Đếm tổng số môn, tổng số quiz đã hoàn thành, điểm trung bình và bài quiz gần nhất.
- `GET /api/v1/recommendations`:
  - Query nhóm theo `topic` từ bảng `Answer` của người học.
  - Tính tỷ lệ `accuracy = correct_count / total_answered`.
  - **Điều kiện topic yếu:** `total_answered >= 3` VÀ `accuracy < 0.60` (dưới 60%).
  - Trả về danh sách topic yếu kèm gợi ý tài liệu ôn tập tương ứng.

---

## 5. Quy tắc bất di bất dịch cần ghi nhớ

1. **Bảo mật tuyệt đối:** Không bao giờ commit file `.env` hoặc nhúng Gemini API Key vào code.
2. **Không tự ý đổi schema:** Tên các trường trong JSON (`course_id`, `found_in_context`, `selected_option_id`,...) phải giữ nguyên như trong [docs/api-contract.md](file:///e:/StudyFlowAI/docs/api-contract.md) để tránh làm hỏng kết nối với Android client.
3. **Xử lý lỗi validation:** Mọi lỗi nhập liệu 422 đều được trả về qua envelope chuẩn `{"detail": {"code": "VALIDATION_ERROR", "message": "...", ...}}`.
4. **Git Workflow:** 
   - Làm trên nhánh `feature/backend-service`.
   - Khi hoàn thành từng mốc, push lên GitHub và tạo Pull Request (PR) về nhánh `develop` để Antigravity review và tích hợp.

Chúc bạn phát triển thuận lợi! Nếu có bất kỳ thắc mắc nào về hợp đồng hoặc logic nghiệp vụ, hãy phản hồi để cùng thống nhất ngay nhé.

