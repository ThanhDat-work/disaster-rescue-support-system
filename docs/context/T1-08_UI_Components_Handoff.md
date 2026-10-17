# T1-08 — UI Components Handoff

- **Cập nhật:** 2026-10-17
- **Nhánh:** `feat/t1-08-ui-components`
- **Route xem giao diện:** `http://localhost:8080/dev/ui-showcase`
- **Điều kiện:** chạy ứng dụng với Spring profile `dev`.
- **Pagination:** thử `?page=0` (đầu), `?page=1` (giữa), `?page=2` (cuối).
- **Lưu ý:** đây là trang showcase phục vụ xem thử component, không phải trang chủ `/`.

## Nội dung đã cập nhật

- Làm mới showcase theo theme sáng, thêm banner ảnh có lớp phủ chữ, bố cục responsive, navbar, bảng, form và các trạng thái tương tác.
- Alert chỉ được render khi model có `successMsg` hoặc `errorMsg`; có nút đóng và tự ẩn sau 5 giây.
- Pagination đặt ở cuối nội dung và nhận URL/query từ controller/model.
- Route showcase và quyền truy cập tài nguyên tĩnh được cấu hình trong profile `dev`.
- Giữ cấu hình DB bằng `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; không thêm mật khẩu mặc định vào source.
- Role đội cứu hộ là `ROLE_RESCUER`; Thymeleaf dùng `hasRole('RESCUER')`.

## Fragment contracts

- `layouts/main-layout :: layout(title, content)` — layout native Thymeleaf; trang gọi fragment bằng `th:replace`, không dùng `layout:decorate`.
- `fragments/components :: status-badge(status)` — chỉ nhận request status; `CRITICAL` là urgency, không phải status.
- `fragments/components :: pagination(pageData, pageUrl, pageQuery)` — controller/model cung cấp URL và query prefix; fragment thêm tham số `page`, không đọc request URI.
- `fragments/components :: confirm-modal(modalId, title, message, formAction)` — modal xác nhận có form POST.
- `fragments/alerts :: flash-messages` — hiển thị Bootstrap Alert dạng floating khi có flash message; `main.js` cho phép đóng và tự ẩn sau 5 giây. Đây là Alert, không phải Toast component.

## Kiểm tra và bằng chứng

- Smoke test hiện có: `src/test/java/vn/edu/hcmute/drss/UiShowcaseMvcTest.java`.
- Trước các chỉnh sửa giao diện gần nhất, test này đã chạy thành công. Sau lần cập nhật theme sáng/banner và điều kiện chỉ render alert khi có message, **chưa chạy lại**; cần chạy `mvn -Dtest=UiShowcaseMvcTest test` để xác nhận trạng thái mới.
- Chưa có ảnh chụp Chrome/Edge ở desktop/mobile trong repo. Khi chụp, lưu tại `docs/context/evidence/t1-08/` và bổ sung tên file cùng kích thước viewport vào mục này.

## Cách mở showcase

Chạy ứng dụng với profile `dev`, sau đó mở `http://localhost:8080/dev/ui-showcase`. Nếu trang vẫn hiện giao diện cũ, tải lại không dùng cache bằng `Ctrl+F5`.

## Việc còn lại

1. Chạy lại `UiShowcaseMvcTest` sau các thay đổi mới nhất.
2. Chụp và lưu bốn ảnh Chrome/Edge ở desktop/mobile nếu môi trường có các trình duyệt đó.
3. Cập nhật handoff với kết quả kiểm tra, ảnh bằng chứng và commit ID sau khi tạo commit.
