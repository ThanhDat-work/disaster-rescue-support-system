# T1-08 — UI Components Handoff

- **Cập nhật:** 2026-10-18
- **Nhánh:** `feat/t1-08-ui-components`
- **Commit nền hiện tại:** `7b948be6cf2e2a0f450a89d0cbec382419e223d8` (`handoff`). 
- **Route showcase:** `http://localhost:8080/dev/ui-showcase` (profile `dev`).
- **Pagination:** mặc định `page=0`; bộ dữ liệu có 5 phần tử, trang 0 đến 4; page âm/quá giới hạn được giới hạn về trang hợp lệ.

## Thay đổi và xử lý feedback

1. **Badge trong bảng:** `sample-ui.html` giữ `<td>` và thay nội dung bằng fragment bên trong `<span>`. Guideline dùng cùng cách; MVC test xác nhận cấu trúc cell.
2. **Form, modal và alerts:** form gửi đến endpoint demo trong `/dev/ui-showcase`; modal dùng endpoint demo riêng. Các POST demo redirect bằng flash attributes để hiển thị success/error alert. Test xác nhận submit, redirect, success alert, error alert và không dùng endpoint nghiệp vụ `/requests/reject/1`.
3. **Responsive CSS:** bổ sung layout cho `form-grid`, `form-panel`, `form-aside` và thành phần con; form chuyển một cột ở breakpoint tablet. `.table-responsive` cuộn ngang ở mọi viewport khi cần; bỏ `body overflow-x: hidden`. Đã chụp Edge desktop, tablet, mobile. Bảng rộng hơn màn hình nhỏ và cuộn bên trong vùng bảng.
4. **Security:** filter chain showcase chỉ match `/dev/**`, có `@Order(1)` và chỉ tồn tại trong profile `dev`. MVC test kiểm tra route demo; profile test xác nhận controller không được đăng ký khi thiếu `dev`. URL nghiệp vụ không bị chain showcase nhận.
5. **Pagination:** trang mặc định là 0; danh sách, `totalElements` và số trang thống nhất là 5. Test bao phủ trang đầu, giữa, cuối, page âm và page ngoài phạm vi.
6. **Tài nguyên và accessibility:** bỏ hero image hotlink và Google Fonts `@import`; thêm SRI/crossorigin cho Bootstrap CDN; nút đóng modal có `aria-label`; trang hiện tại có `aria-current="page"`; status không hợp lệ có badge trung tính.

Không sửa schema, migration, entity, repository hay cấu hình database của T1-07; không thêm H2 hoặc mật khẩu mặc định. Giữ nguyên `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, role `ROLE_RESCUER` và Thymeleaf `hasRole('RESCUER')`. CI workflow lấy `DB_PASSWORD` từ GitHub Actions secret.

## Kiểm tra đã chạy

Tất cả lệnh dùng Maven Wrapper và repository Maven cục bộ trong workspace.

| Lệnh | Kết quả |
|---|---|
| `MAVEN_OPTS="-Dmaven.repo.local=.maven-repository" .\mvnw.cmd "-Dtest=UiShowcaseMvcTest" test` | PASS — 5 tests, 0 failures/errors |
| `MAVEN_OPTS="-Dmaven.repo.local=.maven-repository" .\mvnw.cmd clean test` | PASS — 7 tests, 0 failures/errors |
| `MAVEN_OPTS="-Dmaven.repo.local=.maven-repository" .\mvnw.cmd clean verify "-Dspring.profiles.active=it"` | PASS — 9 tests tổng cộng; 2 MySQL integration tests, 0 failures/errors; Flyway schema version 2, không cần migration |

`MAVEN_OPTS` trỏ Maven đến cache dependency cục bộ của workspace trong môi trường này. Ba biến môi trường DB được nạp từ `.env` tại máy chạy, không ghi giá trị vào handoff.

## Ảnh trình duyệt

Chụp bằng Microsoft Edge `155.0.4283.45` (headless), theo kích thước ảnh/viewport dưới đây:

- Desktop, 1440×1100: [desktop-Edge-1440x1100.png](evidence/t1-08/desktop-Edge-1440x1100.png)
- Tablet, 820×1180: [tablet-Edge-820x1180.png](evidence/t1-08/tablet-Edge-820x1180.png)
- Mobile, 390×844: [mobile-Edge-390x844.png](evidence/t1-08/mobile-Edge-390x844.png)

POST demo, redirect, flash alert và phân trang đã được thực thi qua MockMvc; modal/form markup và endpoint đích được kiểm tra. Chưa thực hiện click thủ công trong cửa sổ trình duyệt.

## Còn cần hoàn tất trước khi approve/merge

- Thay đổi hiện ở workspace, chưa có commit mới do quyền ghi vào `.git/index` bị hệ thống từ chối (`Permission denied`). Commit nền vẫn là `7b948be6cf2e2a0f450a89d0cbec382419e223d8`.
- GitHub Actions chưa được xác nhận trên thay đổi này vì chưa có commit/push mới. Đảm bảo repository secret `DB_PASSWORD` đã được thiết lập, sau đó commit/push branch và kiểm tra workflow PASS.
- Có thể mở showcase trên Edge/Chrome và click thử form, modal, đóng alert, pagination; kiểm tra cuộn bảng ở mobile để xác nhận tương tác trực tiếp.

Không approve hoặc merge cho đến khi các xác minh GitHub và browser trên được hoàn thành.
