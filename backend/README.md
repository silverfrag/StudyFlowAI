# StudyFlow AI — Backend Service

Dịch vụ backend duy nhất của hệ thống **StudyFlow AI**, xây dựng bằng **FastAPI**, lưu trữ dữ liệu với **PostgreSQL + pgvector**, và tích hợp mô hình **Gemini** (thông qua official `google-genai` SDK) cho các tác vụ RAG và sinh câu hỏi trắc nghiệm.

---

## 🚀 Khởi động nhanh (Quick Start)

### 1. Chuẩn bị môi trường
- Yêu cầu Python 3.11+.
- Khởi tạo và kích hoạt virtual environment:
  ```bash
  python -m venv .venv
  .\.venv\Scripts\Activate.ps1   # Trên Windows PowerShell
  ```
- Cài đặt các thư viện cần thiết:
  ```bash
  pip install --upgrade pip
  pip install -r requirements.txt
  ```

### 2. Khởi động PostgreSQL + pgvector
Đảm bảo Docker Desktop đang chạy, sau đó gõ:
```bash
docker compose up -d
```
Container `studyflow-postgres` sẽ chạy tại cổng `5432`.

### 3. Cấu hình biến môi trường
Tạo file `.env` từ file mẫu:
```bash
cp .env.example .env
```
Mở `.env` và cập nhật `GEMINI_API_KEY="AIzaSy..."` với API key lấy từ [Google AI Studio](https://aistudio.google.com/).

### 4. Khởi chạy Backend Server
```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```
- Truy cập Swagger API Docs tại: [http://localhost:8000/docs](http://localhost:8000/docs)
- Kiểm tra Health check tại: [http://localhost:8000/api/v1/health](http://localhost:8000/api/v1/health)

---

## 📖 Hướng dẫn chi tiết cho Thành viên 2
Vui lòng đọc tài liệu hướng dẫn từng bước tại:
👉 [docs/member-2-backend-guide.md](../docs/member-2-backend-guide.md)  
👉 [docs/api-contract.md](../docs/api-contract.md)

