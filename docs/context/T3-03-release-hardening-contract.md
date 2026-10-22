# T3-03 Release Hardening Contract

- Status: Locked for implementation
- Base commit: `b602834`
- Owner: Đỗ Thành Đạt
- Reviewer: Bùi Đức Duy

## 1. Authoritative scope

Contract bám theo báo cáo Word đã chốt và implementation đã merge của T2-01, T2-02, T2-03, T2-04, T2-05. Nội dung Notion chưa duyệt không tự động mở rộng phạm vi.

## 2. Coordinator tracking

- Route: `GET /coordinator/tracking`.
- Chỉ `ROLE_COORDINATOR` truy cập được nhờ `/coordinator/**` trong security chain.
- Hiển thị mọi trạng thái: `PENDING`, `VERIFIED`, `ASSIGNED`, `IN_PROGRESS`, `RESCUED`, `CLOSED`, `REJECTED`, `CANCELLED`.
- Bộ lọc: keyword, status, urgency, disaster type, rescue team, from/to date.
- Kết quả có pagination và assignment gần nhất; tải assignment theo batch, không query từng dòng.
- Queue `/coordinator/pending` vẫn chỉ dành cho xác minh PENDING.

## 3. Close transition

- Route: `POST /coordinator/requests/{requestId}/close`.
- CSRF bắt buộc.
- Điều kiện đồng thời:
  - request hiện là `RESCUED`;
  - expected version khớp;
  - assignment gần nhất tồn tại và là `COMPLETED`;
  - assignment có `completion_time`;
  - báo cáo hoàn thành trong `assignment.notes` không rỗng;
  - ghi chú đóng dài 10–500 ký tự sau trim.
- Thành công:
  - request chuyển `RESCUED -> CLOSED`;
  - tạo đúng một `status_histories` với người thao tác và ghi chú đóng;
  - tạo notification cho chủ request trong cùng transaction;
  - realtime chỉ được dispatch sau commit theo contract T2-05.
- `CLOSED` là terminal; repeated close, stale version và forged ID đều bị chặn.

## 4. Concurrency and transaction

- Khóa pessimistic request trước khi kiểm tra state/version để tuần tự hóa double submit trên cùng request.
- `@Version` vẫn là lớp bảo vệ stale form.
- History, request status và notification thuộc cùng transaction.
- Nếu flush/history/notification lỗi, transaction rollback; không có realtime delivery vì listener dùng `AFTER_COMMIT`.

## 5. Existing decisions preserved

- Citizen chỉ hủy ở `PENDING` hoặc `VERIFIED`.
- Assignment không tự đổi team sang `BUSY`.
- Không thêm reopen flow.
- Không đưa STOMP bell/reconnect UI của T3-02 vào checkpoint này.

## 6. Deferred to checkpoint 03.3

- Audit forged IDs/ownership/team recipient trên toàn bộ flow.
- Rollback matrix cho assignment/state/history/notification/media.
- Double submit/concurrent assignment/transition integration tests.
- Audit secret, sensitive logging, cookie flags và SD-09 as-built.

## 7. Checkpoint 03.3 hardening as-built

### 7.1 Locking and idempotency

- Citizen cancel khóa request bằng query gồm đồng thời `request_id` và owner ID.
- Coordinator verify/reject, assignment và close cùng khóa pessimistic request trước khi kiểm tra state/version.
- Rescuer start/complete khóa assignment thuộc active membership trước khi kiểm tra trạng thái.
- `@Version` trên request và assignment vẫn là lớp bảo vệ stale entity/form.
- Hai submit cùng một transition chỉ có một transaction thắng; transaction đến sau đọc state/version mới và bị từ chối, không tạo history/notification trùng.
- Hai hành động khác nhau được tuần tự hóa theo lock. Nếu trạng thái sau hành động đầu vẫn cho phép hành động thứ hai, cả hai có thể thành công và mỗi transition phải có đúng một history/notification. Ví dụ hợp lệ: `PENDING -> VERIFIED -> CANCELLED`.

### 7.2 Transaction and rollback matrix

- Request/assignment state, status history và notification row nằm trong cùng transaction nghiệp vụ.
- Failure khi tạo notification làm rollback state, assignment và history.
- Notification realtime vẫn chỉ dispatch tại `AFTER_COMMIT`; rollback không phát realtime.
- Cloudinary upload nằm ngoài MySQL và tiếp tục dùng compensation cleanup + durable retry queue theo T2-05.

### 7.3 Authorization and forged identifiers

- Citizen request query ràng buộc owner trong database; ID không tồn tại và ID của owner khác cùng bị che bằng not-found chung.
- Rescuer assignment query ràng buộc active team membership; membership đã rời đội không thể đọc hoặc transition nhiệm vụ.
- Coordinator routes tiếp tục yêu cầu `ROLE_COORDINATOR`; admin không tự có quyền điều phối.
- Notification read query ràng buộc recipient; forged notification ID của user khác trả 404 chung.
- WebSocket coordinator/team subscription tiếp tục đối chiếu active role/membership trong database; payload không chứa location, contact, media URL hoặc credential.

### 7.4 Audit trail

- `status_histories` là business audit trail chính thức cho request lifecycle.
- Mỗi transition quan trọng lưu request, old/new status, actor, notes và server timestamp.
- Audit row nằm cùng transaction với state change; transaction thất bại không để lại audit row giả.
- Không tạo bảng audit thứ hai trùng dữ liệu trong T3-03.

### 7.5 Security configuration audit

- JWT chỉ nhận từ HttpOnly cookie; cookie giữ `SameSite=Lax`, path `/` và `Secure` theo environment.
- CSRF vẫn áp dụng cho mọi endpoint thay đổi nghiệp vụ và notification mark-read.
- JWT/OTP/rate-limit secrets phải khác nhau, tối thiểu 32 UTF-8 bytes và chỉ lấy từ environment.
- `.env` và `scripts/local-env.ps1` không được Git track.
- Không log JWT, cookie, password, OTP, Cloudinary secret, secure media URL, địa chỉ hoặc tọa độ.
