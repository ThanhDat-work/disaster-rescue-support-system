# T2-04 — Rescuer Vertical Slice Handoff

- **Updated:** 2026-10-20
- **Branch:** `feat/t2-04-rescuer-vertical-slice`
- **Baseline commit:** `28cda55`
- **Implementation commit verified:** `8efe3a8`
- **Status:** Implementation, automated tests, MySQL verification, manual browser verification and GitHub Actions PASS.

## 1. Kết quả thực hiện

T2-04 hoàn thiện vertical slice dành cho đội cứu hộ:

- Xem danh sách nhiệm vụ thuộc đội mà rescuer đang là thành viên hoạt động.
- Xem chi tiết nhiệm vụ được phân công.
- Bắt đầu thực hiện nhiệm vụ.
- Hoàn thành nhiệm vụ cùng ghi chú kết quả.
- Kiểm soát quyền ở server bằng `ROLE_RESCUER`.
- Bảo vệ thao tác thay đổi trạng thái bằng POST và CSRF.
- Ghi lịch sử chuyển trạng thái.
- Chặn truy cập chéo đội, thao tác lặp và cập nhật cạnh tranh.

## 2. Routes

- `GET /team/tasks`
- `GET /team/tasks/{assignmentId}`
- `POST /team/tasks/{assignmentId}/start`
- `POST /team/tasks/{assignmentId}/complete`

Toàn bộ `/team/**` yêu cầu người dùng đã xác thực và có `ROLE_RESCUER`.

## 3. Quy tắc thành viên đội

Rescuer chỉ được xem hoặc thao tác trên assignment khi:

- Assignment thuộc một rescue team hợp lệ.
- User hiện tại có bản ghi `rescue_team_members` thuộc đúng team đó.
- Membership vẫn hoạt động, tức `left_at IS NULL`.

Implementation tái sử dụng `RescueTeamMember` và repository nền từ T1-07; không tạo entity hoặc repository membership trùng chức năng.

## 4. Chuyển trạng thái

### Bắt đầu nhiệm vụ

Điều kiện:

- Request đang ở `ASSIGNED`.
- Assignment đang ở `ASSIGNED`.
- Rescuer thuộc đúng team được phân công.

Kết quả:

- Request: `ASSIGNED -> IN_PROGRESS`.
- Assignment: `ASSIGNED -> ACCEPTED`.
- Tạo status history với người thực hiện là rescuer hiện tại.

### Hoàn thành nhiệm vụ

Điều kiện:

- Request đang ở `IN_PROGRESS`.
- Assignment đang ở `ACCEPTED`.
- Rescuer thuộc đúng team được phân công.
- Ghi chú hoàn thành không được để trống.

Kết quả:

- Request: `IN_PROGRESS -> RESCUED`.
- Assignment: `ACCEPTED -> COMPLETED`.
- Tạo status history với người thực hiện là rescuer hiện tại.

Thao tác lặp hoặc chuyển trạng thái không hợp lệ bị từ chối tại service, không chỉ bị ẩn trên giao diện.

## 5. Transaction và cạnh tranh dữ liệu

- Start và complete chạy trong transaction.
- Request, assignment và status history được cập nhật nhất quán.
- Optimistic locking sử dụng trường `version` đã có trong schema nền.
- Không thêm migration V4 trùng lặp.
- Dữ liệu phiên bản cũ bị từ chối thay vì ghi đè trạng thái mới.

## 6. Validation

- `StartRescueRequest`: ghi chú tùy chọn, tối đa 1000 ký tự.
- `CompleteRescueRequest`: ghi chú bắt buộc, tối đa 1000 ký tự.
- Controller thực thi validation bằng DTO và `BindingResult`.
- Validation thất bại không làm thay đổi trạng thái nghiệp vụ.

## 7. Security

- Principal được lấy từ `AuthenticatedUserPrincipal`.
- `/team/**` được bảo vệ bằng server-side RBAC.
- User không có `ROLE_RESCUER` nhận `403`.
- Start và complete chỉ sử dụng POST.
- Form POST chứa CSRF token.
- UI không phải lớp bảo vệ duy nhất.
- JWT cookie tiếp tục tuân thủ security contract T2-01.

## 8. Giao diện

Đã bổ sung:

- Danh sách nhiệm vụ và trạng thái rỗng.
- Thông tin đội được phân công.
- Trang chi tiết nhiệm vụ.
- Form bắt đầu nhiệm vụ.
- Form hoàn thành có validation.
- Success/error flash message.
- Thao tác phù hợp với lifecycle hiện tại.
- Responsive trên viewport mobile.

Các trang tái sử dụng layout và UI components từ T1-08.

## 9. Persistence

Repository đã được mở rộng để:

- Truy vấn assignment theo user và membership đang hoạt động.
- Fetch request và team cần thiết cho UI.
- Tìm membership hoạt động theo `userId` và `teamId`.
- Không cho truy cập assignment chỉ dựa trên ID nếu user không thuộc team.

T2-04 không thay đổi schema T1-07 và không thêm migration mới.

## 10. Automated verification

Kết quả trên implementation commit `8efe3a8`:

- Targeted rescuer tests: PASS — 17 tests.
- Full unit test suite: PASS — 155 tests.
- `RescuerFlowIntegrationIT`: PASS — 7 tests, 0 failures, 0 errors, 0 skipped.
- `clean verify` với profile `it`: PASS trên database MySQL hoàn toàn mới.
- Flyway chạy từ database rỗng: PASS.
- Hibernate schema validation: PASS.
- GitHub Actions trên commit `8efe3a8`: PASS.

Integration test bao phủ:

- Truy vấn theo membership.
- Rendering qua JWT HttpOnly cookie thực.
- Luồng start và complete.
- Trạng thái request, assignment và status history.
- Chặn complete lặp.
- Chặn user không thuộc team.
- JWT cũ bị từ chối khi role bị thu hồi.
- JWT cũ bị từ chối khi user bị vô hiệu hóa.
- Optimistic locking từ chối phiên bản assignment cũ.

## 11. Manual browser verification

Manual browser test đã PASS:

- Đăng nhập bằng rescuer.
- Danh sách chỉ hiển thị nhiệm vụ thuộc team đang tham gia.
- Mở chi tiết nhiệm vụ.
- Responsive ở viewport 390 × 844, không có global horizontal overflow.
- Start gửi POST kèm CSRF và chuyển `ASSIGNED -> IN_PROGRESS`.
- Validation chặn ghi chú hoàn thành rỗng.
- Complete gửi POST kèm CSRF và chuyển `IN_PROGRESS -> RESCUED`.
- Chặn complete lặp.
- User không có `ROLE_RESCUER` bị chặn với `403`.
- Logout thành công; route được bảo vệ yêu cầu đăng nhập lại.

## 12. File triển khai

- `src/main/java/vn/edu/hcmute/drss/controllers/RescuerController.java`
- `src/main/java/vn/edu/hcmute/drss/dtos/CompleteRescueRequest.java`
- `src/main/java/vn/edu/hcmute/drss/dtos/StartRescueRequest.java`
- `src/main/java/vn/edu/hcmute/drss/services/RescuerService.java`
- `src/main/java/vn/edu/hcmute/drss/repositories/RescueAssignmentRepository.java`
- `src/main/java/vn/edu/hcmute/drss/repositories/RescueTeamMemberRepository.java`
- `src/main/resources/templates/pages/rescuer/task-detail.html`
- `src/main/resources/templates/pages/rescuer/task-list.html`
- `src/test/java/vn/edu/hcmute/drss/RescuerFlowIntegrationIT.java`
- `src/test/java/vn/edu/hcmute/drss/controllers/RescuerControllerMvcTest.java`
- `src/test/java/vn/edu/hcmute/drss/services/RescuerServiceTest.java`
- `src/test/java/vn/edu/hcmute/drss/support/NoDatabaseRepositoryMocks.java`

## 13. Nội dung không thay đổi

- Auth contract, JWT cookie và CSRF strategy của T2-01.
- User, role và membership schema nền.
- Flyway V1, V2 và V3.
- Lifecycle trước trạng thái `ASSIGNED`.
- Coordinator assignment workflow.

## 14. Giới hạn đã ghi nhận

- T2-04 hiện xử lý trạng thái nhiệm vụ và ghi chú kết quả.
- Không bổ sung upload ảnh/video hoặc thay đổi Cloudinary/media contract.
- Nếu evidence upload trở thành yêu cầu bắt buộc độc lập, nội dung đó cần task và contract riêng; không tự mở rộng schema trong T2-04.
- Không triển khai WebSocket notification trong phạm vi này.

## 15. Hướng dẫn reviewer tái hiện

### Unit tests

    .\mvnw.cmd clean test

### MySQL integration verification

Nạp biến môi trường local, trỏ `DB_URL` tới một database MySQL rỗng rồi chạy:

    .\mvnw.cmd clean verify "-Dspring.profiles.active=it"

Kỳ vọng:

- Unit tests PASS.
- `RescuerFlowIntegrationIT` chạy 7 tests và PASS.
- Flyway migration PASS.
- Hibernate validation PASS.
- Maven kết thúc bằng `BUILD SUCCESS`.

### Manual route

    http://localhost:8080/team/tasks

Reviewer đăng nhập bằng tài khoản có `ROLE_RESCUER`, sau đó kiểm tra danh sách, chi tiết, start, validation complete, complete, RBAC và logout.

## 16. Kết luận

Implementation commit `8efe3a8` đã PASS automated tests, MySQL integration verification, manual browser verification và GitHub Actions.

T2-04 đủ điều kiện review cuối. Chỉ merge sau khi commit handoff cuối tiếp tục PASS GitHub Actions và các reviewer bắt buộc đã approve.
