# LTW-02 — Backend hardening: handoff và review

- Nhánh: `feat/ltw-backend-hardening` → `main`.
- Base: `e8427f3ee2a863a8ec532a432c5636a32a297e8f`.
- Source đã xác nhận trước commit tài liệu: `e4a37b0` (gồm CP02–CP06 và sửa heartbeat/scan CP07).
- Owner: Đỗ Thành Đạt. Reviewer kỹ thuật/test: Bùi Đức Duy. Reviewer UI/UX: Phan Nhật Vy.
- Tình trạng: owner báo full unit + MySQL verify và manual heartbeat/reconnect PASS; chờ CI và reviewer độc lập. **Chưa merge/chưa đo production.**

## 1. As-built — thay đổi hành vi

| Khu vực | Đã cài | Giới hạn cần nói đúng |
| --- | --- | --- |
| Tạo yêu cầu | Một operation key theo tài khoản và fingerprint payload; claim tuần tự bằng khóa user trước upload; cùng key/payload replay một request; quota 5 lần tạo mới/60 giây/user, không tính replay; tối đa hai lần thử lại sau FAILED. POST mới `/api/request-creations`, GET trạng thái owner-scoped `/api/request-creations/{key}`; đường POST form cũ vẫn được phục vụ. | Không khử trùng mọi yêu cầu giống nhau được gửi từ hai ý định/key mới. `PROCESSING` quá hạn thành `UNKNOWN` để kiểm tra, không tự giả định đã rollback hoặc tự takeover tác vụ. |
| Media và kết quả không rõ | Chỉ upload khi claim là NEW; trạng thái SUCCEEDED và request/media/notification cùng transaction; rollback và Cloudinary compensation giữ theo cơ chế đã có. Client giữ key để hỏi kết quả sau timeout/mất response; không tự POST lại bằng key mới. | Browser không thể khôi phục file picker sau khi reload thật; người dùng có thể phải chọn lại file nếu server xác nhận cho thử lại. Không hứa Cloudinary nằm trong transaction MySQL. |
| Timeout/UX | GET bất đồng bộ và điều hướng nội bộ có chờ hữu hạn, overlay mờ và nút Thử lại; POST tạo request chờ 90 giây phía client rồi tra cứu kết quả, không báo thất bại khi chưa biết kết quả. Cloudinary HTTP connect/pool/response có timeout cấu hình. | Không chặn được timeout cấp trình duyệt/OS; không biến mọi POST của hệ thống thành thao tác idempotent. |
| Realtime | `/ws` dùng STOMP.js UMD 7.2.0 lưu local; CONNECT mang CSRF; simple broker có scheduler heartbeat 10s; client có heartbeat 10s, connection timeout 8s, reconnect exponential tối đa 30s. Personal/role/team subscriptions theo active role và server-validated membership. MESSAGE hợp lệ cập nhật toast, badge rồi reconcile inbox; reconnect tải lại inbox. | Simple broker không là durable broker/outbox; tin realtime có thể mất trong lúc offline nhưng DB inbox là nguồn khôi phục. Không tự cập nhật hàng trong danh sách request/task. |
| Trace/audit | Server tạo `X-Request-ID`, log HTTP method/status/duration không ghi body/URL, propagation sang notification event; audit thành công đổi role/status admin ghi actor/target/action/before/after/request ID cùng transaction. Thao tác bị từ chối ghi warning an toàn. | Audit DB không phải WORM/tamper-proof. Mã tham chiếu không cấp quyền truy cập dữ liệu. |
| Scan/config | Spring Boot 4.0.9, HTTP client 5.6.3/core 5.4.3; SQL logging mặc định tắt. Script OSV Docker pin image, không khóa tên branch. | Kết quả CP06 là 0 finding **trong phạm vi POM compile/runtime tại thời điểm quét**; test dependencies, JS/CSS local và Bootstrap CDN không được OSV POM quét. Bootstrap 5.3.2 có SRI; CDN inventory vẫn `MANUAL_REVIEW_REQUIRED`. Bundle STOMP.js mới cũng chưa có kết luận vulnerability scan riêng. |

Không đổi lifecycle rescue, quy tắc close, role ownership, conflict-of-interest, assignment một team/request, Cloudinary cleanup/retry hoặc AFTER_COMMIT dispatch. Không có message broker ngoài hay giao diện quản trị audit mới.

## 2. Migration, cấu hình và vận hành

- Flyway `V12__Add_Request_Creation_Attempts.sql`: bảng attempt, owner/key unique, state và index quota.
- `V13__Add_Request_Creation_Retry_Count.sql`: số lần retry có giới hạn.
- `V14__Add_Admin_User_Audits.sql`: audit role/status admin. Migration là forward-only; không sửa/xóa bản migration đã deploy.
- Biến mới: `REQUEST_CREATE_MAX_PER_MINUTE` (mặc định 5), `CLOUDINARY_HTTP_TIMEOUT_SECONDS` (mặc định 60, chấp nhận 1–120). Các secret DB/JWT/OTP/Cloudinary và cookie HTTPS giữ cấu hình môi trường sẵn có; không ghi giá trị vào tài liệu/PR.
- Vendor local: `src/main/resources/static/js/vendor/stompjs-7.2.0.min.js` thay parser `stomp-protocol.js`. SHA256 do owner ghi nhận khi tải: `852A3AE9776EB3F68C997BEA8D06D72972ACF7698FEB800A50ACCFE7BBB20F8D`; Git có thể chuẩn hóa LF/CRLF trên checkout Windows, không dùng hash file working-copy khác máy làm điều kiện duy nhất.
- Chạy app manual trên local `drss_qa`; MySQL integration test trên schema local riêng `drss_ltw_qa` hoặc schema test riêng của reviewer. Không chạy test integration trên QA fixture, không restore vào Aiven deploy.
- CP06: full regression owner ghi nhận 413 unit + 79 MySQL integration PASS tại `dab824c`; CP07 owner báo full verify PASS tại `e4a37b0` nhưng chưa chuyển số đếm report cuối vào đây. Backup `drss_qa` đã restore thử vào `drss_ltw_restore` tách biệt: 18 bảng; kiểm tra users=12, requests=25, FK/orphan bằng 0 theo evidence owner giữ ngoài repo. Backup chứa dữ liệu cá nhân: không đính kèm PR hoặc public Drive.
- Rollback sau deploy: giữ image/commit ứng dụng trước deploy và bản sao DB có thời điểm rõ; **không** chạy jar cũ lên schema V12–V14 một cách mù quáng và **không** restore đè production đang có dữ liệu mới. Dừng ghi, chụp backup hiện tại, xác định compatibility của phiên bản trước và thời điểm cần khôi phục; thao tác DB production cần quyết định riêng của Đạt.

## 3. Kết quả và gap

| Gate | Trạng thái tại handoff |
| --- | --- |
| CP01 baseline | 389 unit + 73 MySQL integration PASS trên schema test riêng. |
| CP02 duplicate/key/quota/media | Owner báo automated + manual PASS; commit `7d936a3`. |
| CP03 unknown-result/shared retry | Owner báo automated + manual PASS; commit `34e062d`. |
| CP04 toast/badge/subscription/reconnect | Owner báo PASS; commit `8d6f26a`, heartbeat/client chuẩn được bổ sung tại `e4a37b0`. |
| CP05 correlation/admin audit | Owner báo full verify + bốn manual PASS; commit `388f8c6`. |
| CP06 scan/regression/restore | OSV POM scope 0 finding; full unit/MySQL, browser smoke và isolated restore PASS; commit `dab824c`. |
| CP07 correction | Owner báo full verify và manual heartbeat/reconnect PASS; commit `e4a37b0`. CI/reviewer độc lập **chưa xác nhận**. |
| Realtime latency ≥19/20 trong 2 giây | **CHƯA ĐO**, không ghi PASS. Quyết định ngày 25/10: Đạt chấp nhận hoãn phép đo định lượng để kịp bàn giao; owner Đạt đo trên Render sau merge/deploy nếu còn thời gian. Nếu chưa đo trước báo cáo, mô tả định tính “manual realtime PASS”, không tuyên bố SLA 2 giây. |
| Production Render/Aiven | **POST-MERGE, CHƯA KIỂM TRA** cho source mới. Không suy diễn từ local PASS. |
| JavaScript/CDN SCA | **CHƯA COVER** bởi OSV POM; Vy rà UI asset, Đạt quản lý việc scan bổ sung. Không ghi “toàn bộ dependency sạch”. |

P0/P1 mới do review phát hiện phải có owner/fix PR/retest trước merge. Reviewer không bị yêu cầu tự nhận PASS cho phần chưa chạy.

## 4. Bảng tác động UI → test cho Vy/Duy

| Màn hình/thành phần | Trạng thái cần rà | Backend/quan sát | Reviewer |
| --- | --- | --- | --- |
| `/requests/new`, `request-form.html`, `request-create.js` | native invalid, idle, submitting, 429, unknown-result, kiểm tra key, retry cùng payload, success, bfcache | 1 request/media/notification cho một key; quota theo user; không nhập lại form khi đang ở trang | Vy UX + Duy data/concurrency |
| Điều hướng Thymeleaf dùng `main-layout.html`, `async-retry.js/css` | nền mờ nhìn thấy trang cũ; sau 10s nút Thử lại; back/forward, mobile, keyboard | GET tải lại; không biến POST thường thành POST lặp mù quáng | Vy |
| Navbar/hộp thư `navigation.html`, `main.js`, `realtime-notifications.css` | loading, empty, toast, badge, mark-read, reconnect, offline; role switch/logout | CONNECTED có heartbeat; inbox owner-scoped; no toast lặp; team scope hợp lệ | Vy + Duy |
| Coordinator pending/tracking/detail, team tasks | thông báo mới chỉ gợi ý kiểm tra danh sách; hàng không tự chèn | không kỳ vọng request/task tự xuất hiện trên list mà không tải lại | Vy + Duy |
| `/admin/users/{id}` | lỗi xác thực/validation có mã tham chiếu an toàn; success/denied rõ | audit success cùng transaction, denied log không chứa secret | Vy + Duy |
| `error/500.html` | thông báo chung + mã tham chiếu, responsive | server log tra được ID, không lộ stacktrace/PII | Vy + Duy |

Không dựng skeleton giả trên trang SSR không fetch. Vy rà cả mobile/desktop và focus/keyboard; Duy ghi mỗi lỗi theo route, role, precondition, expected/actual, screenshot đã che dữ liệu nhạy cảm, commit và retest.

## 5. Duy — lấy đúng code và automated

Chạy trong **clone review sạch**; không reset/stash thay đổi cá nhân. Không cần GitHub CLI. Sau commit tài liệu, Đạt gửi full SHA mới; đối chiếu với PR HEAD trước khi kết luận:

```powershell
$ErrorActionPreference = "Stop"
if (@(git status --porcelain).Count -ne 0) {
    git status --short
    throw "Working tree chưa sạch."
}
git fetch origin --prune
if ($LASTEXITCODE -ne 0) { throw "Fetch FAIL." }
git switch --detach origin/feat/ltw-backend-hardening
if ($LASTEXITCODE -ne 0) { throw "Switch FAIL." }
git log -1 --oneline --decorate
git diff --stat origin/main...HEAD
git diff --name-status origin/main...HEAD
git status --short
```

JDK 17; nạp env riêng của Duy trong PowerShell. Nếu bị Execution Policy chặn script local, chỉ đổi `-Scope Process` theo chính sách máy rồi nạp; không sửa policy toàn hệ thống. Chỉ dùng MySQL test local riêng và Cloudinary folder riêng khi test media. Không đăng credential trong comment.

```powershell
$ErrorActionPreference = "Stop"
java -version
& .\mvnw.cmd -version
. .\scripts\local-env.ps1
if ($env:DB_URL -notmatch '^jdbc:mysql://(localhost|127\.0\.0\.1)(:\d+)?/[^?]+') {
    throw "DB_URL không trỏ MySQL local."
}
if ($env:DB_URL -match '/(drss_qa|drss_ltw_restore)(\?|$)') {
    throw "Chọn integration DB riêng, không dùng QA/restore."
}
& node --check .\src\main\resources\static\js\main.js
if ($LASTEXITCODE -ne 0) { throw "JavaScript syntax FAIL." }
& node .\scripts\check-realtime-client.cjs
if ($LASTEXITCODE -ne 0) { throw "STOMP static check FAIL." }
& .\scripts\scan-dependencies.ps1 -InventoryOnly
if ($LASTEXITCODE -ne 0) { throw "Inventory FAIL." }
$nativeBefore = $PSNativeCommandUseErrorActionPreference
$PSNativeCommandUseErrorActionPreference = $false
try {
    & .\mvnw.cmd clean verify "-Dspring.profiles.active=it"
    $exit = $LASTEXITCODE
} finally {
    $PSNativeCommandUseErrorActionPreference = $nativeBefore
}
if ($exit -ne 0) { throw "MySQL full verify FAIL." }
Write-Host "LTW_02_DUY_AUTOMATED=PASS"
```

Nếu máy có Docker engine: chạy thêm `& .\scripts\scan-dependencies.ps1`, lưu metadata/findings/log ngoài repo; scan lỗi network/API thì ghi **BLOCKED**, không gọi PASS. Script scan chỉ quét POM compile/runtime. Lưu report Surefire/Failsafe và SHA HEAD; không paste raw log chứa thông tin riêng tư.

### Duy — manual và security review

1. Tạo/nhận fixture trên DB QA **của Duy**, không dùng Aiven. Citizen A tạo request không media; double-click hoặc hai POST cùng key/payload: một row request và một bộ notification. Key cũ nhưng payload khác phải từ chối. Dùng user B hỏi status key A: không được lộ request A. Sáu key mới trong 60s: key thứ sáu nhận giới hạn; replay key cũ không tiêu quota mới.
2. Upload một ảnh/video hợp lệ theo giới hạn, thử timeout/mất response sau POST: dùng chính key để GET trạng thái. `SUCCEEDED` mở cùng request; `PROCESSING/UNKNOWN` không gửi key mới; `FAILED` chỉ retry khi backend cho phép. Kiểm tra media, request và notification không nhân đôi; nếu mô phỏng lỗi Cloudinary, kiểm tra compensation/cleanup theo test hiện hữu.
3. Chrome/Edge song song Citizen, Coordinator, Rescuer team hợp lệ. Network → WS `/ws`: CONNECT có CSRF và heartbeat `10000,10000`, CONNECTED thương lượng heartbeat; không F5, tạo/verify/assign và đối chiếu toast + badge + inbox đúng người. Cắt/kết nối lại mạng: badge/inbox bù tin cũ, không toast lặp. Đăng xuất/chuyển role/rút membership: socket/subscription cũ không tiếp tục được dùng để xem tin trái quyền.
4. Admin đổi status/role một user khác: kiểm tra đúng một audit trước/sau/actor/request ID. Thử self-mutation, last admin và trường hợp validation: bị từ chối, không có audit success giả. Chỉ kiểm tra trang 500 khi Duy có fixture gây lỗi **đã được kiểm soát trên local**; nếu không có, ghi NOT RUN và dùng test MVC/log hiện có, không tự phá dữ liệu để tạo lỗi. Trang 500 có mã tham chiếu, không stacktrace; log tra được ID mà không có JWT/password/OTP/PII.
5. Xem `RequestCreateAttemptRepository`, `RequestCreationGateService`, `CitizenRequestMediaPersistenceService`, `RealtimeSubscriptionAuthorizationInterceptor`, `RealtimeWebSocketConfig`, `AdminUserService`, `RequestCorrelationFilter` và V12–V14. Đối chiếu transaction, ownership, forged key/team, quota race, rollback và after-commit; không chỉ dựa UI.

Đối với mỗi FAIL: file/case, actor/role, key hoặc display code **không nhạy cảm**, bước tái hiện, expected/actual, log lỗi đầu tiên, owner và kết quả retest. Không ghi OTP/JWT/secret, địa chỉ người thật hoặc dump QA công khai.

## 6. Vy — checklist UI độc lập

- Dùng PR HEAD đúng như Duy. Rà navbar/toast/badge/hộp thư trên các trang Citizen, Coordinator, Rescuer, Admin; desktop và mobile; contrast, focus, Escape, aria-live. Không bấm notification mới bị hiện toast thì mới đúng — toast phải tự xuất hiện.
- Form request: validation không khóa nút; submit giữ input/file đang ở trang; 429 có thông báo; unknown-result có đường kiểm tra; không hiển thị “thất bại” khi chưa chắc; reload thật không giả vờ giữ file picker.
- Điều hướng trang `/account/profile`, `/coordinator/tracking` khi mạng chậm: overlay **trong mờ**, trang cũ còn nhìn thấy; quá ngưỡng có Thử lại; timeout hộp thư không làm loading vô hạn. Kiểm tra responsive và nút với bàn phím.
- Admin detail/error 500: mã tham chiếu đọc được nhưng không lộ exception; UI hiện có không bị CSS mới làm tràn. Ghi từng route/viewport/state với evidence, Approved hoặc Changes requested.

## 7. PR, CI, merge và deploy

**Tiêu đề PR đề xuất:** `fix(ltw): harden request creation, realtime delivery and audit`

**PR body ngắn (điền issue số thật nếu có, không tự bịa `Closes #`):**

```markdown
## Kết quả thực hiện / Handoff
- Chặn tạo request trùng bằng operation key, fingerprint và quota 5/60s; retry sau timeout tra cứu kết quả owner-scoped trước khi gửi tiếp.
- Thông báo STOMP có heartbeat/reconnect, toast + badge và subscription đúng role/team; audit admin và mã tham chiếu lỗi.
- Flyway V12–V14; SQL logging mặc định tắt; cập nhật dependency và script scan.
- Full unit + MySQL verify: PASS theo owner; manual submit/retry/realtime/heartbeat: PASS theo owner. CI và reviewer: chờ xác nhận.
- OSV: 0 finding tại CP06 trong POM compile/runtime; JS/CDN chưa được quét trong scope đó. Realtime 20-event/2s: CHƯA ĐO theo quyết định owner, không công bố SLA.
- Xem docs/context/LTW-02-backend-hardening-handoff.md để có matrix UI, test, review và rollback.
```

**CI:** GitHub Actions → PR → `CI Workflow / build` xanh trên đúng commit PR mới nhất. Nếu secret `DB_PASSWORD` hoặc dịch vụ MySQL của CI lỗi, ghi BLOCKED/FAIL và xử lý; không suy diễn từ local PASS. Duy comment phần quyền/transaction/scan/DB; Vy comment UI/UX. Chỉ merge khi CI xanh, hai người kết luận Approved hoặc mọi vấn đề P0/P1 đã có fix PR và retest. Không tự sửa code khi hai reviewer đang đọc mà không thông báo HEAD mới.

**Tiêu đề merge đề xuất:** `fix(ltw): complete request and realtime reliability hardening`

Sau merge: trên `main` đối chiếu commit merge với PR, deploy đúng image/commit đó lên Render, Flyway V12–V14 trên Aiven có backup và health check; smoke login/OTP, request không media, notification sống, role switch, request detail từ mạng khác. Đo/ghi latency Render nếu kịp; nếu chưa đo vẫn ghi `NOT MEASURED`. Không tải seed QA, dump cá nhân hoặc secret lên GitHub/Drive. Nếu deploy lỗi, giữ image cũ và demo local đã kiểm chứng; rollback DB theo mục 2, không thao tác phá dữ liệu production mù quáng.

Mẫu comment reviewer: `LTW-02 review: PASS — [phần đã kiểm tra]; phần không chạy: NOT RUN; Approved.` Nếu FAIL, chưa Approved, nêu case và evidence để Đạt sửa/retest.
