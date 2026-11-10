# StudyFlow AI — Android App

Ứng dụng Android cho **StudyFlow AI**, xây dựng bằng **Kotlin + Jetpack Compose + Material 3 + MVVM**, tuân thủ hợp đồng API tại [docs/api-contract.md](../docs/api-contract.md) và phác thảo giao diện tại [docs/ui-wireframes.md](../docs/ui-wireframes.md).

---

## 📱 Mở và chạy trong Android Studio

1. Mở **Android Studio**.
2. Chọn **File → Open...** và trỏ đến thư mục `E:\StudyFlowAI\android` (lưu ý mở thư mục `android/`, không mở thư mục gốc `StudyFlowAI`).
3. Chờ Gradle Sync hoàn tất (sử dụng Gradle `9.5.0`, AGP `9.3.3`, `compileSdk 37`, `targetSdk 36`).
4. Khởi động **Android Emulator** và nhấn **Run (Shift + F10)**.

---

## 🔗 Kết nối với Backend FastAPI
- **Trên Android Emulator:** Base URL mặc định được cấu hình trong `app/build.gradle.kts` là:
  `http://10.0.2.2:8000/api/v1/`
- **Trên Thiết bị Android thật (cùng mạng Wi-Fi):** Thay đổi `API_BASE_URL` thành IP LAN của máy chạy backend (ví dụ: `http://192.168.1.10:8000/api/v1/`).

---

## 🗂️ Cấu trúc tính năng đã triển khai
- **4 Tab điều hướng chính:** `Trang chủ` · `Môn học` · `AI` · `Tiến độ`
- **6 Màn hình theo Wireframe:**
  1. `HomeScreen`: Tóm tắt môn học, số quiz đã làm, tiếp tục học, truy cập nhanh.
  2. `CoursesScreen`: Danh sách môn học, tìm kiếm, thêm/xóa môn học.
  3. `CourseDetailScreen`: Quản lý tài liệu PDF/TXT (upload multipart, trạng thái xử lý), tạo bài Quiz MCQ.
  4. `AssistantScreen`: Trợ lý AI hỏi đáp theo ngữ cảnh môn học kèm nguồn trích dẫn (`citations`).
  5. `QuizScreen`: Làm bài trắc nghiệm, điều hướng giữa các câu, cảnh báo câu chưa làm.
  6. `ResultScreen` & `ProgressScreen`: Hiển thị điểm số `/10`, giải thích từng câu, phân tích topic yếu và gợi ý ôn tập.

