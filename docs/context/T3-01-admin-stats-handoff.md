# T3-01 — Admin and Request Statistics Handoff

- **Status:** Ready for review
- **Updated:** 2026-10-21
- **Task:** T3-01
- **GitHub issue:** #10
- **Implementation branch:** `feat/t3-01-admin-stats`
- **Baseline commit:** `f65e400`
- **Implementation commit verified:** `3f50fbf`
- **Artifact/Trace ID:** `ADMIN-01`, `STATS-01`

## 1. Kết quả thực hiện

T3-01 đã hoàn thiện phạm vi quản trị và thống kê tối thiểu được khóa trong contract:

- Quản lý danh mục loại thiên tai.
- Quản lý đội cứu hộ.
- Quản lý trạng thái và vai trò người dùng.
- Thống kê yêu cầu cứu hộ theo trạng thái, mức độ khẩn cấp và khoảng ngày.
- Authorization phía server cho mọi route quản trị và thống kê.
- Thymeleaf UI có validation, flash message, empty/error state và responsive.
- Unit, MVC, security và MySQL integration tests.
- Ảnh bằng chứng chạy thật cho báo cáo.

Handoff chỉ ghi nhận chức năng đã có implementation và đã được kiểm tra.

## 2. Các commit chính

- `ee70249` — `docs: lock T3-01 admin statistics contract`
- `6f19963` — `feat: add rescue request statistics foundation`
- `923272f` — `feat: implement admin disaster and team management`
- `49a8998` — `feat: implement admin user and role management`
- `3f50fbf` — `feat: add rescue request statistics dashboard`

## 3. Quản lý loại thiên tai

Các chức năng đã cài:

- Danh sách, tạo mới và cập nhật loại thiên tai.
- Kích hoạt hoặc vô hiệu hóa thay cho xóa cứng.
- DTO validation và business validation.
- Lỗi form và flash message sau redirect.

Route chính:

- `GET /admin/disasters`
- `GET /admin/disasters/new`
- `POST /admin/disasters`
- `GET /admin/disasters/{id}/edit`
- `POST /admin/disasters/{id}`
- `POST /admin/disasters/{id}/status`

Chỉ `ROLE_ADMIN` được truy cập.

## 4. Quản lý đội cứu hộ

Các chức năng đã cài:

- Danh sách, tạo mới và cập nhật đội cứu hộ.
- Cập nhật trạng thái hoạt động thay cho xóa cứng.
- Kiểm tra mã đội và dữ liệu bắt buộc.
- Lỗi form và flash message sau redirect.

Route chính:

- `GET /admin/teams`
- `GET /admin/teams/new`
- `POST /admin/teams`
- `GET /admin/teams/{id}/edit`
- `POST /admin/teams/{id}`
- `POST /admin/teams/{id}/status`

Chỉ `ROLE_ADMIN` được truy cập. Quản lý thành viên đội không thuộc T3-01 và cần task dữ liệu riêng.

## 5. Quản lý người dùng và vai trò

Các chức năng đã cài:

- Danh sách, tìm kiếm, lọc và xem chi tiết người dùng.
- Cập nhật trạng thái tài khoản.
- Cập nhật tập vai trò từ role hợp lệ trong hệ thống.
- Tăng `users.auth_version` khi trạng thái hoặc quyền thay đổi.
- Thay đổi trạng thái, vai trò và `auth_version` trong transaction.

Route chính:

- `GET /admin/users`
- `GET /admin/users/{id}`
- `POST /admin/users/{id}/status`
- `POST /admin/users/{id}/roles`

Chỉ `ROLE_ADMIN` được truy cập. Việc tăng `auth_version` làm JWT cũ của tài khoản bị thay đổi không còn hợp lệ ở request tiếp theo.

## 6. Dashboard thống kê

Route dashboard:

- `GET /stats/requests`

Quyền truy cập:

- `ROLE_ADMIN`: được phép.
- `ROLE_COORDINATOR`: được phép.
- `ROLE_CITIZEN`: bị từ chối.
- `ROLE_RESCUER`: bị từ chối.
- Anonymous: chuyển tới đăng nhập.

Dashboard hiển thị:

- Tổng số yêu cầu trong khoảng ngày.
- Phân bố theo mọi trạng thái.
- Phân bố theo mọi mức độ khẩn cấp.
- Số lượng và tỷ lệ của từng nhóm.
- Các nhóm có giá trị bằng 0.
- Empty state khi không có dữ liệu.
- Validation state khi khoảng ngày không hợp lệ.

## 7. Quy tắc khoảng ngày

- Mặc định là 30 ngày gần nhất, tính cả hai đầu.
- `from` và `to` phải được cung cấp cùng nhau.
- `from` không được sau `to`.
- Không chấp nhận ngày tương lai.
- Khoảng tối đa là 366 ngày, tính cả hai đầu.
- Dữ liệu tính từ đầu ngày `from` đến trước đầu ngày sau `to`.
- Ngày sai định dạng trả validation an toàn, không làm lộ lỗi nội bộ.

Ví dụ: `/stats/requests?from=2026-10-01&to=2026-10-21`.

## 8. Security và RBAC

- Spring Security kiểm tra quyền phía server.
- Ẩn menu chỉ hỗ trợ UX, không thay thế authorization.
- Anonymous truy cập route bảo vệ được chuyển tới đăng nhập.
- Người dùng đã đăng nhập nhưng thiếu quyền nhận `403`.
- POST quản trị yêu cầu CSRF token.
- Admin routes chỉ dành cho `ROLE_ADMIN`.
- Statistics route dành cho `ROLE_ADMIN` và `ROLE_COORDINATOR`.
- Thay đổi role/status vô hiệu JWT cũ qua `auth_version`.

## 9. Persistence và migration

Migration `V4__Add_Admin_Statistics_Index.sql` bổ sung index `(urgency, created_at)` phục vụ thống kê theo mức độ khẩn cấp và thời gian.

Index hiện có `(status, created_at)` tiếp tục phục vụ thống kê theo trạng thái. Không có thay đổi xóa bảng, xóa cột hoặc phá vỡ dữ liệu hiện tại.

## 10. Automated verification

Kết quả cuối trên implementation commit `3f50fbf`:

- Full unit/MVC test suite: PASS.
- Tests run: 209.
- Failures: 0.
- Errors: 0.
- Skipped: 0.
- MySQL integration suite: PASS.
- Integration tests run: 22.
- Integration failures: 0.
- Integration errors: 0.
- GitHub Actions trên `3f50fbf`: PASS.

Log nghiệm thu cuối:

- `T3-01-dashboard-unit.log`
- `T3-01-dashboard-integration-after-fix.log`

`T3-01-dashboard-integration.log` là lần chạy trước khi sửa assertion cũ và không phải kết quả cuối.

Assertion migration đã được sửa để kiểm tra cột `users.auth_version` là `NOT NULL` và có default `0`, thay vì giả định dữ liệu nghiệp vụ có thể thay đổi phải luôn giữ `auth_version = 0`.

## 11. Manual browser verification

Các luồng sau đã PASS:

- Anonymous bị chuyển tới đăng nhập khi mở statistics.
- Admin mở statistics và các trang quản trị thành công.
- Coordinator mở statistics nhưng bị từ chối ở admin routes.
- Citizen và Rescuer bị từ chối ở statistics.
- Khoảng mặc định và khoảng tùy chọn khớp truy vấn SQL.
- Nhóm có số lượng 0 vẫn hiển thị.
- Empty state và date validation hiển thị đúng.
- Tạo, sửa và đổi trạng thái loại thiên tai thành công.
- Tạo, sửa và đổi trạng thái đội cứu hộ thành công.
- Xem user, cập nhật trạng thái và vai trò thành công.
- Flash success/error hiển thị đúng sau redirect.
- Desktop, tablet và mobile responsive PASS.
- Không có global horizontal overflow.

## 12. Evidence

Ảnh được lưu tại:

- `evidence/t3-01/admin-disasters-1440x1100.png`
- `evidence/t3-01/admin-teams-1440x1100.png`
- `evidence/t3-01/admin-users-1440x1100.png`
- `evidence/t3-01/stats-desktop-1440x1100.png`
- `evidence/t3-01/stats-tablet-820x1180.png`
- `evidence/t3-01/stats-mobile-390x844.png`

Tên ảnh ghi viewport mục tiêu. Kích thước pixel thực tế có thể chênh lệch nhỏ do công cụ chụp, browser chrome hoặc tỉ lệ hiển thị của hệ điều hành; bằng chứng được đánh giá theo nội dung và breakpoint thể hiện.

`Export Image/PDF` của task được hiểu là bằng chứng cho báo cáo, không phải tính năng export trong ứng dụng.

## 13. Lệnh reviewer chạy lại

1. Fetch và switch sang `feat/t3-01-admin-stats`.
2. Xác nhận working tree sạch.
3. Nạp biến môi trường theo `docs/guidelines/local_auth_configuration.md`.
4. Chạy unit tests: `./mvnw.cmd clean test`.
5. Chạy MySQL verification: `./mvnw.cmd clean verify "-Dspring.profiles.active=it"`.

Không đưa secret, mật khẩu database hoặc JWT vào comment review hay log chia sẻ.

## 14. Flow reviewer kiểm tra thủ công

Kiểm tra các route:

- `/admin/disasters`
- `/admin/teams`
- `/admin/users`
- `/stats/requests`

Reviewer nghiệp vụ và security kiểm tra role matrix, CSRF, dữ liệu dashboard khớp SQL và JWT cũ bị từ chối sau khi role/status thay đổi.

Reviewer UI kiểm tra validation, flash message, empty/error state, navbar theo role, các breakpoint desktop/tablet/mobile và global overflow.

## 15. Giới hạn đã biết

Ngoài phạm vi T3-01:

- Quản lý thành viên đội cứu hộ.
- Xóa cứng danh mục, đội hoặc người dùng.
- Export báo cáo trực tiếp từ ứng dụng.
- Biểu đồ thời gian thực hoặc WebSocket dashboard.
- Phân tích bản đồ hoặc dữ liệu địa lý.
- Data warehouse và báo cáo nâng cao.

Các nội dung này chỉ được bổ sung qua task và quyết định kiến trúc sau.

## 16. Trace cho báo cáo

Chương 4 chỉ được bổ sung những màn hình và chức năng đã có implementation thật trong T3-01. Ảnh bằng chứng trong handoff là nguồn đối chiếu khi viết báo cáo.

Không mô tả quản lý thành viên đội, export trong ứng dụng, biểu đồ thời gian thực hoặc chức năng ngoài phạm vi như thể đã hoàn thành.

## 17. Điều kiện merge

T3-01 sẵn sàng merge khi:

- GitHub Actions trên commit mới nhất PASS.
- Unit/MVC và MySQL integration tests PASS.
- Manual browser review PASS.
- Evidence được commit.
- PR có `Closes #10`.
- Admin và statistics đúng quyền, đúng dữ liệu.
- Non-admin không truy cập được chức năng quản trị.
- Reviewer nghiệp vụ/quyền/query/test kết luận trực tiếp trên PR.
- Vy review dashboard/responsive nếu có thay đổi UI.