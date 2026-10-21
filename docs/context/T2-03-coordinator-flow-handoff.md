# T2-03 — Coordinator Request Flow Handoff

- **Updated:** 2026-10-21
- **Branch:** `feat/t2-03-coordinator-flow`
- **Baseline commit:** `298cb3d`
- **Implementation commits:** `19ae735`, `e46bd06`, `bca5250`
- **Status:** Ready for final verification and review
- **Source of truth:** `T2-03-coordinator-flow-contract.md` and the approved Word report

## 1. Kết quả thực hiện

T2-03 hoàn thiện vertical slice dành cho điều phối viên:

- Xem queue các yêu cầu `PENDING`.
- Tìm kiếm, lọc và phân trang queue.
- Xem chi tiết liên hệ, vị trí, media hiện có và lịch sử.
- Xác minh `PENDING -> VERIFIED`.
- Từ chối `PENDING -> REJECTED` với lý do bắt buộc.
- Phân công đội `VERIFIED -> ASSIGNED`.
- Tạo `rescue_assignments` và status history trong cùng transaction.
- Chặn stale version, cancelled race, invalid team và active double assignment.
- Giữ team ở `AVAILABLE`; T2-03 không tự động chuyển team sang `BUSY`.

## 2. Routes

- `GET /coordinator/pending`
- `GET /coordinator/requests/{requestId}`
- `POST /coordinator/requests/{requestId}/decision`
- `POST /coordinator/requests/{requestId}/assign`

Toàn bộ `/coordinator/**` yêu cầu người dùng đã xác thực và có `ROLE_COORDINATOR`.

## 3. Authorization

- Anonymous được chuyển tới `/auth/login`.
- User đã đăng nhập nhưng không có `ROLE_COORDINATOR` nhận `403`.
- `ROLE_ADMIN` không mặc nhiên có quyền điều phối.
- POST yêu cầu CSRF.
- Principal lấy từ `AuthenticatedUserPrincipal`; client không được gửi coordinator ID.
- UI chỉ hỗ trợ trải nghiệm; service và security chain vẫn là lớp kiểm soát bắt buộc.

## 4. Queue và detail

Queue chỉ lấy request `PENDING`:

- Page size 10.
- Thứ tự `created_at ASC`, sau đó `id ASC`.
- Từ khóa tìm theo display code, họ tên, số điện thoại và địa chỉ.
- Lọc urgency, disaster type và khoảng ngày.
- Ngày kết thúc được tính trọn ngày.
- Trang âm chuẩn hóa về 0; trang vượt phạm vi quay về trang cuối có dữ liệu.

Detail gồm:

- Thông tin người dân.
- Địa chỉ và tọa độ.
- Loại thiên tai, urgency và mô tả.
- Media chưa bị xóa.
- History theo thứ tự thời gian tăng dần.

## 5. Verify và reject

Chỉ request `PENDING` được xử lý:

- `VERIFY` chuyển request sang `VERIFIED`.
- `REJECT` chuyển request sang `REJECTED`.
- Reject reason sau trim là bắt buộc và tối đa 2.000 ký tự.
- Request và history được ghi trong cùng transaction.
- Form mang request version; stale version bị từ chối.
- Nếu citizen hủy request trước khi coordinator submit, coordinator không ghi đè `CANCELLED`.
- Optimistic/constraint failure được đẩy ra trước khi controller trả response để transaction rollback.

## 6. Assignment

Chỉ request `VERIFIED` được phân công:

- Team phải tồn tại.
- Team phải ở `AVAILABLE`.
- Team phải có ít nhất một membership `left_at IS NULL`.
- Active assignment là assignment có trạng thái `ASSIGNED` hoặc `ACCEPTED`.
- Không tạo active assignment thứ hai cho cùng request.
- Mã assignment có dạng `ASN-` + 16 ký tự hex in hoa.
- Assignment mới có trạng thái `ASSIGNED`, `version = 0`, `completion_time = NULL` và `notes = NULL`.
- Request chuyển `VERIFIED -> ASSIGNED`.
- Ghi chú điều phối được lưu trong status history, không ghi vào `rescue_assignments.notes`.
- `rescue_assignments.notes` tiếp tục dành cho báo cáo hoàn thành của T2-04.

## 7. Transaction và concurrency

Assignment service thực hiện theo thứ tự:

1. Kiểm tra DTO và coordinator.
2. Khóa request bằng `PESSIMISTIC_WRITE`.
3. Kiểm tra version và trạng thái `VERIFIED`.
4. Kiểm tra active assignment.
5. Khóa team bằng `PESSIMISTIC_WRITE`.
6. Kiểm tra team status và active membership.
7. Tạo assignment, đổi request status và tạo history.
8. Flush khi transaction còn mở.

Nhờ khóa request, hai thao tác phân công cùng request được tuần tự hóa. Transaction đến sau phải đọc lại trạng thái và bị chặn thay vì tạo assignment thứ hai.

Nếu flush hoặc constraint/locking thất bại, request, assignment và history cùng rollback.

## 8. UI

Đã triển khai:

- Queue filter và pagination.
- Empty state.
- Detail contact/location/media/history.
- Form verify/reject.
- Reject reason validation.
- Form assignment và danh sách team hợp lệ.
- Empty state khi không có team phù hợp.
- Success/error flash message.
- Coordinator navigation entry.
- Responsive form trên desktop, tablet và mobile.

UI tái sử dụng layout, alert và component nền hiện có.

## 9. SD-02..04 as-built trace

### SD-02 — Xem queue và chi tiết

- Controller: `CoordinatorRequestController`.
- Query service: `CoordinatorRequestQueryService`.
- Repository queries: `RescueRequestRepository`, `MediaAssetRepository`, `StatusHistoryRepository`.
- UI: `pending-list.html`, `request-detail.html`.
- Notification không tham gia luồng đọc.

### SD-03 — Verify hoặc reject

- Endpoint: `POST /coordinator/requests/{requestId}/decision`.
- DTO: `CoordinatorDecisionRequest` và `CoordinatorDecisionAction`.
- Service: `CoordinatorRequestCommandService`.
- Transaction ghi request và status history.
- Không phát notification trong T2-03.

### SD-04 — Assignment

- Endpoint: `POST /coordinator/requests/{requestId}/assign`.
- DTO: `CoordinatorAssignmentRequest`.
- Service: `CoordinatorAssignmentService`.
- Repository locking: request và team.
- Transaction ghi request, assignment và status history.
- WebSocket/notification sau commit thuộc T2-05 và chưa được tuyên bố hoàn tất trong T2-03.

## 10. Automated tests

Unit/MockMvc coverage:

- `CoordinatorRequestQueryServiceTest`
- `CoordinatorRequestCommandServiceTest`
- `CoordinatorAssignmentServiceTest`
- `CoordinatorRequestControllerMvcTest`

MySQL integration coverage:

- `CoordinatorFlowIntegrationIT`
  - Queue filter/order/pagination trên MySQL thật.
  - Detail contact/location/media/history.
  - Verify và reject qua JWT cookie + CSRF thật.
  - Reject reason được trim và lưu đúng.
  - Cancelled/stale race không để lại partial history.
  - Anonymous/citizen bị chặn và coordinator được phép.
- `CoordinatorAssignmentIntegrationIT`
  - Available team có active member.
  - Assignment thành công và dữ liệu transaction đúng.
  - Team không có active member bị chặn.
  - Active double assignment bị chặn, không có partial write.

Final verification bắt buộc trước commit handoff:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean verify "-Dspring.profiles.active=it"
```

## 11. Manual browser verification

Đã kiểm tra theo ba checkpoint nghiệp vụ:

- Queue, filter, pagination, detail và empty state.
- Verify thành công và history đúng một lần.
- Reject reason validation và reject thành công.
- Stale tab và citizen-cancel race bị chặn.
- Assignment thành công.
- Assignment code/status/timestamps đúng.
- Team vẫn `AVAILABLE`.
- Rescuer nhìn thấy nhiệm vụ mới.
- Double assignment bị chặn.
- Anonymous redirect; citizen/admin không có coordinator role nhận `403`.
- CSRF có mặt trong form POST.
- Desktop/tablet/mobile không có global horizontal overflow.

## 12. Phạm vi không triển khai

T2-03 không triển khai:

- Upload hoặc xóa Cloudinary media.
- Notification record.
- WebSocket/STOMP.
- Chuyển team sang `BUSY`.
- Monitor hoặc close request.
- Audit log mở rộng ngoài status history.

Các phần trên thuộc T2-05, T3-03 hoặc T3-02 theo dependency đã khóa.

## 13. Dữ liệu dùng chung và deployment

- T2-03 không thêm environment variable.
- Đạt, Vy và Duy tiếp tục dùng database local riêng.
- CI dùng database test riêng.
- Database deploy trung tâm chỉ được cấu hình khi deploy.
- Cloudinary project/folder, mail đại diện và WebSocket deployment contract được chốt ở T2-05.
- Không commit DB password, mail password, JWT/OTP/HMAC secret hoặc token.

## 14. Evidence và báo cáo

- Contract và handoff phản ánh implementation thật.
- SD-02..04 phải dùng trace tại mục 9 khi cập nhật Word.
- Không mô tả notification/WebSocket như đã hoàn thành ở T2-03.
- Screenshot release đồng nhất sẽ được chụp trong T3-02 sau khi T2-05 và T3-03 hoàn tất.
- Không tạo ảnh giả hoặc dùng dữ liệu vô nghĩa chỉ để đủ evidence.

## 15. File triển khai chính

- `CoordinatorRequestController.java`
- `CoordinatorRequestQueryService.java`
- `CoordinatorRequestCommandService.java`
- `CoordinatorAssignmentService.java`
- `CoordinatorRequestFilter.java`
- `CoordinatorDecisionRequest.java`
- `CoordinatorAssignmentRequest.java`
- `RescueRequestRepository.java`
- `RescueAssignmentRepository.java`
- `RescueTeamRepository.java`
- `RescueTeamMemberRepository.java`
- `pending-list.html`
- `request-detail.html`
- `CoordinatorFlowIntegrationIT.java`
- `CoordinatorAssignmentIntegrationIT.java`

## 16. Reviewer focus

Vy review:

- Queue/detail/action form trên desktop và mobile.
- Empty/error states.
- Filter/pagination và flash message.
- Verify/reject/assign flow thực tế.
- Không có horizontal overflow hoặc mojibake.

Duy review:

- State transitions.
- Transaction request/history/assignment.
- Lock order và double assignment protection.
- Team status/membership validation.
- Repository queries và MySQL integration tests.
- Rollback không để lại partial write.

Chỉ merge khi GitHub Actions trên final handoff commit PASS và cả Vy, Duy đều Approved.

## 17. Kết luận

T2-03 hoàn thiện queue/detail, verify/reject và assignment theo contract đã duyệt. Implementation không mở rộng sang media upload, notification/WebSocket hoặc monitor/close.

Final handoff commit chỉ được tạo sau khi clean test, MySQL clean verify, manual flow và format check đều PASS.
