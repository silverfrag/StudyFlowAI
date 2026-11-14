# Thư viện ảnh chụp màn hình chức năng — StudyFlow AI (Media Showcase)

Toàn bộ ảnh chụp màn hình được kết xuất trực tiếp từ máy ảo **Google Pixel 8 Pro (Android 14/15, API 34+)** với độ phân giải gốc **1344 x 2992 pixels (480 dpi)** định dạng PNG không nén (lossless). Hình ảnh giữ nguyên chất lượng hiển thị gốc của bộ giao diện **UI Kit v2 (Pastel Bento + Tactile 3D Buttons)**, hoàn toàn sẵn sàng để chèn vào báo cáo đồ án, slide thuyết trình hoặc tài liệu hướng dẫn người dùng.

---

## Danh mục hình ảnh theo phân hệ chức năng

### 1. Phân hệ Trang chủ & Hồ sơ cá nhân (Overview & Profile)
| Tên tệp | Màn hình / Trạng thái | Ca sử dụng | Điểm nổi bật cho báo cáo |
| :--- | :--- | :--- | :--- |
| `01_home_light.png` | **Trang chủ (Light Mode)** | Tổng quan | Thẻ Hero tiếp tục học, lưới Bento 3 ô thống kê (Môn học, Quiz đã làm, Điểm TB), truy cập nhanh, thẻ Hồ sơ học tập, thanh điều hướng đáy nổi 4 tab. |
| `02_home_dark.png` | **Trang chủ (Dark Mode)** | Tổng quan | Chế độ Midnight Dark sâu (`#0D111E`), tương phản cao, các viền và bóng đổ 3D xúc giác hiển thị sắc nét. |
| `03_profile_dark.png` | **Hồ sơ học viên (Dark Mode)** | Cài đặt | Thẻ định danh học viên, avatar chữ viết tắt, mục tiêu học tập hàng ngày, bộ chuyển đổi theme Sáng/Tối. |
| `04_profile_overview_light.png` | **Hồ sơ học viên (Light Mode)** | Cài đặt | Thống kê số liệu thực tế từ backend, 3 phím tắt điều hướng nhanh tới Môn học, Trợ lý AI và Tiến độ. |
| `05_profile_edit_dialog.png` | **Hộp thoại chỉnh sửa thông tin** | Cài đặt | Cho phép đổi tên hiển thị, ngành/mục tiêu, chip chọn nhanh thời lượng học mỗi ngày (30p, 45p, 60p, 90p, 120p). |

---

### 2. Phân hệ Quản lý Môn học & Tài liệu học tập (Courses & Documents - UC01)
| Tên tệp | Màn hình / Trạng thái | Ca sử dụng | Điểm nổi bật cho báo cáo |
| :--- | :--- | :--- | :--- |
| `06_courses_list_light.png` | **Danh sách môn học (Light Mode)** | UC01 | Thanh tìm kiếm, các card môn học với bảng màu pastel riêng biệt, monogram viết tắt, số lượng tài liệu/quiz và nút `+ Thêm môn`. |
| `07_courses_add_dialog.png` | **Hộp thoại tạo môn học mới** | UC01 | Nhập tên môn, mã học phần/mô tả với nhãn hướng dẫn rõ ràng và nút bấm xúc giác. |
| `08_course_detail_docs_light.png` | **Chi tiết môn học — Tab Tài liệu** | UC01, UC02 | Banner thông tin môn, cặp nút CTA `Hỏi AI môn này` & `Tạo Quiz mới`, dải lọc tài liệu, danh sách file PDF kèm trạng thái `Sẵn sàng`, dung lượng, ngày tải lên. |
| `09_course_detail_quizzes_tab.png` | **Chi tiết môn học — Tab Bài quiz** | UC01, UC03 | Danh sách các đề quiz trắc nghiệm đã tạo theo môn học, điểm số đạt được, số câu, ngày làm bài và nút `Làm lại`. |
| `10_course_detail_create_quiz_dialog.png` | **Hộp thoại tạo đề Quiz mới** | UC03 | Cho phép chọn tài liệu nguồn cụ thể hoặc toàn bộ môn, nhập chủ đề trọng tâm, thanh trượt chọn số lượng câu hỏi (5/10/15/20 câu). |
| `11_courses_list_dark.png` | **Danh sách môn học (Dark Mode)** | UC01 | Giao diện tối sang trọng của danh sách học phần. |
| `12_course_detail_dark.png` | **Chi tiết môn học (Dark Mode)** | UC01 | Giao diện tối của màn hình quản lý chi tiết học phần. |
| `28_delete_doc_dialog_dark.png` | **Hộp thoại xác nhận xóa tài liệu** | UC01 | Hộp thoại cảnh báo an toàn kèm nhắc nhở xóa vector trích xuất liên quan. |

---

### 3. Phân hệ Đọc tài liệu PDF tích hợp trong App (Document Viewer)
| Tên tệp | Màn hình / Trạng thái | Ca sử dụng | Điểm nổi bật cho báo cáo |
| :--- | :--- | :--- | :--- |
| `13_document_viewer_page1.png` | **Trình đọc PDF — Trang 1** | UC01, UC02 | Trình đọc PDF native dựng bằng `PdfRenderer`, thanh công cụ bottom bar hiển thị số trang hiện tại/tổng trang (`Trang 1 / 47`), nút lật trang mượt mà. |
| `14_document_viewer_page2.png` | **Trình đọc PDF — Trang 2** | UC01, UC02 | Thao tác lật trang kế tiếp, hiển thị nội dung slide giáo trình độ nét cao. |
| `27_document_viewer_target_page24.png` | **Trình đọc PDF — Deep Link trang đích** | UC02, UC04 | Mở tự động đúng trang được trích dẫn (ví dụ Trang 24 từ Topic yếu) kèm banner tím `Trọng tâm cần ôn: Thành phần Test Case` và chip `Trang gợi ý: Trang 24`. |

---

### 4. Phân hệ Trợ lý AI hỏi đáp tài liệu có nguồn dẫn RAG (AI Assistant - UC02)
| Tên tệp | Màn hình / Trạng thái | Ca sử dụng | Điểm nổi bật cho báo cáo |
| :--- | :--- | :--- | :--- |
| `15_assistant_qa_with_citations_light.png` | **Hỏi đáp AI có trích dẫn nguồn (Light)** | UC02 | Phân tách ngữ cảnh môn học theo group, câu trả lời có cấu trúc rõ ràng, chip trích dẫn tài liệu + số trang nguồn và nút bấm mở tài liệu trực tiếp. |
| `16_assistant_history_sheet.png` | **Lịch sử các phiên chat theo môn** | UC02 | Bảng trượt đáy (`SessionHistoryBottomSheet`) liệt kê các đoạn chat đã lưu riêng biệt cho từng môn, hỗ trợ tạo đoạn chat mới để chống trôi ngữ cảnh (anti-drift). |
| `17_assistant_qa_dark.png` | **Trợ lý AI hỏi đáp (Dark Mode)** | UC02 | Giao diện chat AI ở chế độ Midnight Dark với bong bóng tin nhắn và thẻ trích dẫn nổi bật. |

---

### 5. Phân hệ Làm bài kiểm tra trắc nghiệm MCQ (Quiz - UC03)
| Tên tệp | Màn hình / Trạng thái | Ca sử dụng | Điểm nổi bật cho báo cáo |
| :--- | :--- | :--- | :--- |
| `18_quiz_question_q1_light.png` | **Màn hình làm Quiz — Câu 1/5** | UC03 | Thanh tiến trình số câu, huy hiệu chủ đề, nội dung câu hỏi, 4 thẻ phương án A/B/C/D 3D, nút `Câu trước` bị vô hiệu hóa an toàn, nút `Câu tiếp`. |
| `19_quiz_question_selected.png` | **Trạng thái đã chọn đáp án** | UC03 | Hiệu ứng viền tím lavender, icon dấu tích chọn, màu sắc tương phản rõ rệt. |
| `20_quiz_question_q5_submit.png` | **Câu hỏi cuối cùng & Nút nộp bài** | UC03 | Câu 5/5 hiển thị nút `Nộp bài` màu xanh ngọc (Emerald Green) nổi bật kèm bóng đổ xúc giác. |

---

### 6. Phân hệ Kết quả bài kiểm tra & Phân tích đáp án (Quiz Result - UC03 & UC04)
| Tên tệp | Màn hình / Trạng thái | Ca sử dụng | Điểm nổi bật cho báo cáo |
| :--- | :--- | :--- | :--- |
| `21_result_overview_hero_light.png` | **Tổng quan kết quả bài làm** | UC03, UC04 | Hero banner điểm số trên thang 10, biểu đồ tròn số câu đúng/sai, thông báo phân loại mức độ hoàn thành. |
| `22_result_weak_topic_advice.png` | **Gợi ý ôn tập chuyên sâu** | UC04 | Thẻ vàng `Gợi ý ôn tập` phân tích chính xác nguyên nhân câu sai từ bài thi, nút `Xem trang 9` mở thẳng giáo trình và nút `Mở Tiến độ`. |
| `23_result_answers_review.png` | **Chi tiết đáp án & Trích dẫn nguồn** | UC03, UC04 | Đối chiếu phương án học viên chọn với đáp án chuẩn từ hệ thống, giải thích cặn kẽ và ghim nút nguồn `Tr.X [↗]` mở ngay PDF. |

---

### 7. Phân hệ Tiến độ học tập & Flashcard ôn tập (Progress - UC04)
| Tên tệp | Màn hình / Trạng thái | Ca sử dụng | Điểm nổi bật cho báo cáo |
| :--- | :--- | :--- | :--- |
| `24_progress_overview_light.png` | **Tổng quan tiến độ (Light Mode)** | UC04 | Biểu đồ vòng điểm trung bình, thống kê tổng số bài quiz đã hoàn thành, tổng số topic yếu cần củng cố ngay (ngưỡng < 60%). |
| `25_progress_weak_topic_flashcards.png` | **Flashcard gợi ý ôn tập topic yếu** | UC04 | Danh sách thẻ topic yếu với tỷ lệ chính xác %, hướng dẫn kiến thức cụ thể, nút `Xem trang X` và nút `Luyện quiz` chủ đề tương ứng. |
| `26_progress_dark.png` | **Tiến độ học tập (Dark Mode)** | UC04 | Giao diện tối của màn hình theo dõi tiến độ và flashcard học tập. |

---

## Hướng dẫn sử dụng hình ảnh cho Báo cáo Đồ án

1. **Chèn vào Microsoft Word / Google Docs:**
   - Kéo thả trực tiếp tệp `.png` từ thư mục `docs/media/` vào tài liệu.
   - Nhờ độ phân giải 1344x2992, khi thu nhỏ lại kích thước hiển thị trong trang Word (khoảng 6–7 cm chiều rộng), hình ảnh sẽ tự động đạt độ sắc nét chuẩn in ấn 300+ DPI mà không bị vỡ hạt hay mờ chữ.
2. **Chèn vào Slide thuyết trình (PowerPoint / Canva / Figma):**
   - Đặt hình ảnh vào khung mockup điện thoại (Device Frame) hoặc hiển thị so sánh song song giữa Chế độ Sáng (Light) và Chế độ Tối (Dark) để tạo điểm nhấn trực quan ấn tượng cho hội đồng đánh giá.
