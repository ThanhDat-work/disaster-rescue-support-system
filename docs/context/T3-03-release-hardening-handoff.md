# T3-03 Release Hardening Handoff

- **Status:** Ready for final verification and review
- **Task:** T3-03
- **Owner:** Đỗ Thành Đạt
- **Reviewer:** Bùi Đức Duy
- **Base commit:** `b602834`
- **Latest code commit:** `2fb859e`
- **Branch:** `feat/t3-03-release-hardening`
- **Updated:** 2026-10-22

## 1. Kết quả thực hiện

T3-03 khóa flow theo dõi/đóng yêu cầu và hardening các transition trong request lifecycle. Phạm vi bao gồm authorization, ownership, transaction rollback, optimistic/pessimistic locking, idempotency, concurrency, audit trail và security configuration audit.

Không bổ sung bảng audit thứ hai. `status_histories` là business audit trail chính thức của request lifecycle.

## 2. Các commit của task

| Commit | Nội dung |
|---|---|
| `8f641bb` | Thêm coordinator tracking và close flow |
| `2fb859e` | Hardening transition, transaction rollback và concurrency |

## 3. Coordinator tracking

- Route: `GET /coordinator/tracking`.
- Chỉ `ROLE_COORDINATOR` được truy cập qua security chain `/coordinator/**`.
- Hiển thị các trạng thái `PENDING`, `VERIFIED`, `ASSIGNED`, `IN_PROGRESS`, `RESCUED`, `CLOSED`, `REJECTED`, `CANCELLED`.
- Hỗ trợ keyword, status, urgency, disaster type, rescue team, date range và pagination.
- Assignment gần nhất được tải theo batch; không tạo query riêng cho từng dòng.
- `/coordinator/pending` tiếp tục chỉ là queue xác minh request `PENDING`.

## 4. Close transition

- Route: `POST /coordinator/requests/{requestId}/close`.
- CSRF bắt buộc.
- Request chỉ được đóng khi:
  - trạng thái hiện tại là `RESCUED`;
  - expected version khớp;
  - assignment gần nhất tồn tại và có status `COMPLETED`;
  - `completion_time` tồn tại;
  - completion report trong assignment notes không rỗng;
  - close note dài 10–500 ký tự sau trim.
- Thành công tạo đúng một transition `RESCUED -> CLOSED`, một history có actor/note/timestamp và notification cho owner trong cùng transaction.
- `CLOSED` là terminal; repeated close, stale version và forged ID bị chặn.

## 5. Locking và idempotency

- Citizen cancel khóa request bằng query ràng buộc đồng thời request ID và owner ID.
- Coordinator verify/reject, assignment và close khóa pessimistic request trước khi kiểm tra state/version.
- Rescuer start/complete khóa assignment chỉ khi user còn là active team member.
- `@Version` trên request và assignment tiếp tục chặn stale entity/form.
- Hai submit cùng một transition chỉ có một transaction thắng; transaction sau đọc state/version mới và bị từ chối.
- Hai action khác nhau được serialize theo lock. Nếu state sau action đầu vẫn cho phép action thứ hai, cả hai có thể hợp lệ; ví dụ `PENDING -> VERIFIED -> CANCELLED`.
- Mỗi transition thành công tạo đúng một history và notification; không tạo bản ghi trùng.

## 6. Transaction và rollback

- Request/assignment state, status history và notification row nằm trong cùng transaction nghiệp vụ.
- Lỗi khi ghi notification/history/flush làm rollback state và các row liên quan.
- Realtime gateway chỉ dispatch sau commit; rollback không phát realtime.
- Media upload nằm ngoài MySQL tiếp tục dùng compensation cleanup và durable retry queue của T2-05.

Rollback integration coverage:

1. Verify/reject rollback khi notification persistence fail.
2. Assignment rollback khi notification persistence fail.
3. Rescuer transition rollback khi notification persistence fail.
4. Close rollback khi notification persistence fail.

## 7. Concurrency coverage

Integration test bao phủ:

1. Double coordinator decision.
2. Citizen cancel chạy đồng thời coordinator verify.
3. Double assignment.
4. Double rescuer start.
5. Double close.

Mỗi test đối chiếu final state, history count, notification count và audit fields.

## 8. Authorization và forged identifiers

- Citizen query ràng buộc owner tại database; ID không tồn tại và ID của owner khác cùng được che bằng not-found chung.
- Rescuer query ràng buộc active team membership; thành viên đã rời đội không thể xem hoặc transition assignment.
- Coordinator routes yêu cầu `ROLE_COORDINATOR`; `ROLE_ADMIN` không tự động có quyền điều phối.
- Notification mark-read ràng buộc recipient; forged notification ID của user khác trả not-found.
- WebSocket subscription đối chiếu active role/team membership.
- Realtime payload không chứa địa chỉ, tọa độ, số điện thoại, media URL, JWT hoặc credential.

## 9. Audit trail và SD-09 as-built

`status_histories` là audit trail chính thức, lưu request, old/new status, actor, business note/reason và server timestamp.

Audit row nằm cùng transaction với state change. Transaction rollback không để lại history giả. SD-09 trong báo cáo cần phản ánh đúng cơ chế này và không mô tả một bảng audit riêng chưa được cài đặt.

## 10. Security audit

- JWT chỉ được nhận từ HttpOnly cookie.
- Cookie giữ `SameSite=Lax`, path `/`, và `Secure` theo environment.
- CSRF áp dụng cho các endpoint thay đổi state và notification mark-read.
- JWT/OTP/rate-limit secrets chỉ lấy từ environment và phải đạt độ dài tối thiểu theo auth contract.
- `.env` và `scripts/local-env.ps1` không được Git track.
- Không log JWT, cookie, password, OTP, Cloudinary secret, secure media URL, địa chỉ hoặc tọa độ.

## 11. Automated verification

Đã PASS:

- targeted unit suites: 38 tests;
- release rollback/concurrency integration suites: 9 tests;
- full Maven `clean verify` với profile `it`;
- UTF-8, conflict marker, trailing whitespace và `git diff --check`;
- local secret tracking scan;
- sensitive logging scan.

Lệnh tái hiện:

```powershell
$ErrorActionPreference = "Continue"

if (Get-Variable `
        PSNativeCommandUseErrorActionPreference `
        -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

& .\mvnw.cmd clean verify `
    "-Dspring.profiles.active=it"

if ($LASTEXITCODE -ne 0) {
    throw "T3-03 full verify FAIL."
}

Write-Host "T3_03_FULL_VERIFY=PASS"
```

## 12. Manual verification đã thực hiện

- Cancel request khi `PENDING`: PASS.
- Verify rồi cancel tại `VERIFIED`: PASS.
- Assignment đến close lifecycle: PASS.
- Cross-owner forged request URL: PASS.
- Repeated close bị chặn: PASS.
- Không có history/notification trùng: PASS.
- Tracking filters, pagination và request detail: PASS.

## 13. Review focus cho Duy

1. Pessimistic lock được lấy trước state/version validation.
2. Owner/team membership được ràng buộc ngay trong repository query.
3. Rollback không để lại state/history/notification một phần.
4. Realtime không dispatch khi transaction fail.
5. Double submit và race case không tạo assignment/history/notification trùng.
6. `PENDING -> VERIFIED -> CANCELLED` là chuỗi hợp lệ nếu verify lấy lock trước cancel.
7. Close chỉ từ `RESCUED` và assignment `COMPLETED` có report.
8. Cookie, CSRF, secret và sensitive logging không bị regression.

## 14. Deferred / không thuộc T3-03

- STOMP bell/reconnect/error UX thuộc T3-02.
- Không có reopen flow.
- Không tự động đổi rescue team sang `BUSY` khi assignment.
- Không tạo bảng audit tổng quát thứ hai.
- Deployment-specific database, Cloudinary folder và representative email sẽ được khóa tại release/deployment checkpoint chung.

## 15. Environment và shared data

T3-03 không thêm environment variable, migration hay shared credential mới.

Khi deploy, team vẫn phải đồng bộ một production database, một Cloudinary account/folder dùng chung, một representative Gmail/mail sender, production JWT/OTP/rate-limit secrets, HTTPS và cookie `Secure=true`.

Không ghi các giá trị secret vào handoff, source, PR hoặc test log.

## 16. Kết luận

T3-03 đã đạt scope monitor/close/audit và release hardening. Branch sẵn sàng cho CI, review của Duy và merge sau khi reviewer `Approved`.
