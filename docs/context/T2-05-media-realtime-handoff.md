# T2-05 Media and Realtime Handoff

- **Status:** Ready for review
- **Task:** T2-05
- **Branch:** `feat/t2-05-media-realtime`
- **Base commit:** `65b8bd3`
- **Latest code commit before handoff:** `89fe3c2`
- **Dependencies:** T2-02, T2-03, T2-04
- **Artifacts:** `MEDIA-01`, `WS-01`, `SD-10`
- **Primary implementation owner:** Đỗ Thành Đạt
- **Reviewer:** Bùi Đức Duy

## 1. Kết quả thực hiện

T2-05 đã hoàn thiện backend tích hợp Cloudinary cho ảnh/video và notification realtime theo transaction boundary của hệ thống.

Các luồng đã hoàn thành:

- Citizen tạo yêu cầu có thể đính kèm JPEG, PNG, WebP và MP4.
- Media được validate theo số lượng, dung lượng, MIME khai báo và chữ ký byte thực.
- Media hợp lệ được upload qua Cloudinary server-side SDK.
- Metadata media được lưu trong MySQL cùng request.
- Upload dở dang hoặc transaction database thất bại kích hoạt compensation cleanup.
- Cleanup Cloudinary lỗi được lưu bền trong `media_cleanup_tasks` và scheduled retry theo batch/backoff.
- Citizen và coordinator hiển thị đúng ảnh/video theo `resource_type`.
- Oversized upload được chặn ở browser và server, không gây `ERR_CONNECTION_RESET` trong luồng đã kiểm thử.
- Notification row được tạo cùng transaction nghiệp vụ.
- Realtime delivery chỉ chạy sau commit qua `AFTER_COMMIT` listener.
- Rollback không để lại notification row và không phát realtime event.
- Coordinator/team recipients được lọc lại từ trạng thái hiện hành trong database.
- WebSocket subscription kiểm tra personal/coordinator/team destinations và chặn forged destination.
- Notification có API reload và mark-read theo ownership, CSRF và idempotency.
- Payload realtime không chứa địa chỉ, tọa độ, số điện thoại, media URL, JWT hoặc secret.

## 2. As-built media flow

### 2.1 Validation

- Tối đa 5 file cho mỗi thao tác.
- Tối đa 1 video.
- Tối đa 10 MiB cho mỗi file.
- Hỗ trợ:
  - `image/jpeg`
  - `image/png`
  - `image/webp`
  - `video/mp4`
- File rỗng, MIME không hỗ trợ, MIME giả, chữ ký byte không khớp và file vượt giới hạn đều bị từ chối.
- Client-side validation chỉ phục vụ UX; server vẫn là nguồn quyết định cuối.

### 2.2 Upload và persistence

1. Controller nhận form và multipart files.
2. `MediaUploadValidator` kiểm tra toàn bộ file trước khi upload.
3. `CitizenRequestMediaService` upload tuần tự qua `MediaStorage`.
4. `CitizenRequestMediaPersistenceService` lưu request, media metadata và notification trong transaction database.
5. Thành công trả về request detail; ảnh và video được render theo đúng loại tài nguyên.

### 2.3 Compensation và retry

- Nếu một upload thất bại, các upload trước đó được xóa bù theo thứ tự ngược.
- Nếu transaction database thất bại sau khi Cloudinary đã upload, toàn bộ media đã upload được xóa bù.
- Nếu Cloudinary delete tức thời thất bại, hệ thống ghi `media_cleanup_tasks` bằng transaction `REQUIRES_NEW`.
- Scheduled worker lấy task chưa hoàn thành đã đến hạn, retry theo batch và backoff.
- Cloudinary `ok` và `not found` đều được xem là thành công idempotent.
- Cleanup task không lưu API secret hoặc secure URL.

### 2.4 Oversized upload

- Business limit vẫn là 10 MiB/file.
- Multipart transport dùng giới hạn 64 MiB để đủ cho request hợp lệ tối đa và cho phép server xử lý an toàn file đơn khoảng 50 MiB.
- JavaScript chặn sớm oversized file khi browser hoạt động bình thường.
- Khi bỏ qua JavaScript, server redirect về `/requests/new` với thông báo an toàn.
- Oversized request không tạo request, media row, notification hoặc Cloudinary resource.

## 3. Metadata media

`media_assets` lưu:

- request và uploader
- Cloudinary `public_id`
- HTTPS `secure_url`
- `resource_type`: `IMAGE` hoặc `VIDEO`
- MIME type và format
- width/height
- bytes
- duration cho video
- timestamps phục vụ lifecycle/cleanup

Migrations của T2-05:

- `V5__Add_Media_Integration_Support.sql`
- `V6__Add_Media_Cleanup_Tasks.sql`
- `V7__Add_Notification_Event_Type.sql`

## 4. Notification transaction và realtime

Notification được tạo tại các business transition sau:

- Request mới → active coordinators.
- Request đổi trạng thái → active citizen owner.
- Assignment mới → active members của team được phân công.

Quy tắc transaction:

- Notification record nằm trong cùng transaction với thay đổi business state/history.
- Event được publish trong transaction nhưng listener chỉ xử lý ở `AFTER_COMMIT`.
- Rollback xóa cả business change và notification row; gateway không được gọi.
- WebSocket delivery lỗi sau commit không rollback business transaction.
- Database notification API là nguồn reload sau reconnect.

## 5. Recipient và authorization

### 5.1 Coordinator

- Chỉ user `ACTIVE`.
- Có `ROLE_COORDINATOR` chưa revoke.
- Role hiện hành được kiểm tra lại khi subscribe coordinator destination.

### 5.2 Team

- Chỉ user `ACTIVE`.
- Có membership đúng team và `left_at IS NULL`.
- Forged team destination bị từ chối.

### 5.3 Personal notification

- Status notification chỉ tạo cho active owner của request.
- Notification reload chỉ trả row của user đang xác thực.
- Mark-read không nhận recipient ID từ client.
- Notification của user khác và ID không tồn tại cùng trả `404`.

## 6. Destinations và payload

Private user destinations:

- `/user/queue/notifications`
- `/user/queue/coordinator/notifications`
- `/user/queue/teams/{teamId}/notifications`

Payload chỉ gồm:

- notification ID
- event type
- reference ID
- title/message an toàn
- created timestamp

Client business `MESSAGE` và destination ngoài allowlist bị từ chối.

## 7. Notification reload API

- `GET /api/notifications`
  - tối đa 50 dòng mới nhất
  - sắp xếp `created_at DESC, id DESC`
  - trả unread count của chính user
- `POST /api/notifications/{notificationId}/read`
  - bắt buộc xác thực và CSRF
  - kiểm tra ownership
  - idempotent, không đổi lại `read_at` khi gọi lặp

## 8. Automated verification

Đã chạy thành công:

```powershell
. .\scripts\local-env.ps1
.\mvnw.cmd clean verify "-Dspring.profiles.active=it"
```

Kết quả cuối:

- Full unit tests: PASS
- Full MySQL integration verify: PASS
- Cloudinary media adapter/validator tests: PASS
- Citizen media persistence/rollback tests: PASS
- Notification transaction/after-commit tests: PASS
- WebSocket destination/recipient authorization tests: PASS
- Notification reload/ownership/idempotency tests: PASS
- Oversized-upload MVC/integration tests: PASS
- Cleanup retry worker tests: PASS
- MySQL recipient filtering tests: PASS

Các test trọng yếu bổ sung:

- `CitizenRequestMediaIntegrationIT`
- `NotificationTransactionIntegrationIT`
- `NotificationAccessIntegrationIT`
- `NotificationRecipientIntegrationIT`
- `MediaCleanupRetryServiceTest`
- `RealtimeSubscriptionAuthorizationInterceptorTest`
- `WebSocketRealtimeNotificationGatewayTest`
- `MultipartUploadExceptionHandlerTest`

## 9. Manual verification đã thực hiện

- Cloudinary live image upload/delete: PASS
- Cloudinary live video upload/delete: PASS
- Citizen create không media: PASS
- Citizen create với ảnh và video: PASS
- MySQL media metadata: PASS
- Citizen image rendering: PASS
- Citizen video playback: PASS
- Coordinator image rendering: PASS
- Coordinator video playback: PASS
- Refresh persistence: PASS
- Hai video bị từ chối: PASS
- Oversized browser validation: PASS
- Oversized forged/no-JavaScript request: PASS
- Oversized không tạo request/media/Cloudinary resource: PASS
- Notification reload đúng owner: PASS
- Mark-read idempotent: PASS
- Cross-owner notification ID trả `404`: PASS

## 10. Shared environment contract

Biến môi trường bắt buộc:

- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- `CLOUDINARY_FOLDER`

Folder convention:

- Đạt: `drss/local/dat`
- Vy: `drss/local/vy`
- Duy: `drss/local/duy`
- CI: `drss/ci`
- Production: `drss/prod`

Credential thật chỉ nằm trong ignored local file, GitHub Actions secrets hoặc deploy environment. Không commit credential và không gửi API secret xuống browser.

## 11. Phạm vi deferred và không được tuyên bố hoàn thành

Các phần sau chưa được cài trong T2-05:

- Bell/dropdown notification UI.
- STOMP client, reconnect và realtime error UX.
- Citizen thay/xóa media sau khi request đã tạo.
- Rescuer upload completion evidence.
- Monitor/close flow của T3-03.

STOMP UI/reconnect/error UX được chuyển sang T3-02 sau khi T2-05 và T3-03 đã review/merge. Các cột lifecycle media trong schema là nền tảng, không phải bằng chứng rằng end-user delete flow đã hoàn thành.

## 12. Commit map

- `42ebd12` — Cloudinary media storage foundation
- `75c0253` — citizen request media persistence and rollback cleanup
- `7523fae` — coordinator media rendering by resource type
- `677c3b7` — transactional realtime notifications
- `d1befc2` — notification reload and read tracking
- `4ec8e45` — safe oversized media rejection
- `89fe3c2` — cleanup retry and MySQL recipient verification

## 13. Reviewer focus

Duy cần tập trung kiểm tra:

1. Cloudinary cleanup khi upload/DB transaction thất bại.
2. Scheduled retry idempotency và không lưu secret/secure URL.
3. Notification row cùng transaction business state/history.
4. Không gateway call trước commit hoặc sau rollback.
5. Coordinator/team recipients theo role, status, membership hiện hành.
6. Private user destinations và forged subscriptions.
7. Payload không chứa dữ liệu riêng.
8. Notification ownership, CSRF và mark-read idempotency.
9. Migrations V5–V7 và full MySQL verify.

## 14. Trình tự sau merge

1. Merge T2-05 sau khi CI PASS và Duy Approved.
2. T3-03 tạo branch từ `main` đã chứa T2-05.
3. T3-03 hoàn thiện monitor/close/audit/security/concurrency.
4. Sau khi T3-03 merge, Vy tạo T3-02 từ `main` sạch để cài STOMP UI và hoàn thiện UI QA/evidence.

## 15. Kết luận

Backend T2-05 đã sẵn sàng review cho phạm vi media create/read, Cloudinary cleanup/retry, transactional notification, private WebSocket destinations và notification reload/access. Các phần UI realtime và media lifecycle chưa có implementation đã được ghi rõ deferred để báo cáo phản ánh đúng hệ thống chạy thật.
