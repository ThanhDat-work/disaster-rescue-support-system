# T2-05 Media and Realtime Contract

- **Status:** Approved for implementation
- **Approved date:** 2026-10-21
- **Task:** T2-05
- **Base commit:** `65b8bd3`
- **Dependencies:** T2-02, T2-03, T2-04
- **Artifact/Trace IDs:** `MEDIA-01`, `WS-01`, `SD-10`
- **Last clarified:** 2026-10-21 — Media persistence đã hoàn thiện; realtime dùng private user destinations, notification cùng transaction và WebSocket dispatch sau commit.

## 1. Nguồn quyết định

Tài liệu Word báo cáo mới nhất là nguồn mô tả chính. Các dòng requirement trên Notion chưa được nhóm duyệt chỉ dùng để đối chiếu, không tự động trở thành hợp đồng triển khai.

T2-05 hiện thực hóa phần media Cloudinary và notification WebSocket được mô tả trong báo cáo. Khi implementation khác mô tả cũ, contract và handoff phải ghi rõ as-built để cập nhật lại SD-10 và báo cáo.

## 2. Phạm vi

T2-05 bao gồm:

- Upload ảnh và video lên Cloudinary bằng server-side SDK.
- Lưu metadata media trong MySQL.
- Cleanup Cloudinary khi upload nhiều file hoặc transaction database thất bại.
- Soft-delete metadata và xóa Cloudinary sau commit.
- Retry cleanup idempotent cho media chưa xóa được trên Cloudinary.
- Notification record nằm trong transaction nghiệp vụ.
- Domain event chỉ phát tới WebSocket sau khi transaction commit.
- Authorization WebSocket theo user, role và active team membership.
- Kiểm thử rollback, cleanup và recipient.

T2-05 không bao gồm:

- Theo dõi/đóng yêu cầu; nội dung này thuộc T3-03.
- UI STOMP hoàn chỉnh, reconnect/error UX và evidence responsive; nội dung này được deferred sang T3-02 sau khi T2-05 và T3-03 đã review/merge.
- Client-side direct upload bằng Cloudinary API secret.

## 3. Loại media và giới hạn

- Ảnh: JPEG, PNG và WebP.
- Video: MP4.
- Tối đa 10 MiB cho mỗi file.
- Tối đa 5 file trong một thao tác.
- Tối đa 1 video trong một thao tác.
- File rỗng bị từ chối.
- MIME khai báo phải phù hợp với chữ ký byte thực tế.
- Không chỉ tin phần mở rộng tên file hoặc `Content-Type` do client gửi.
- Giới hạn nghiệp vụ do `app.media.max-file-size=10MB` và `MediaUploadValidator` thực thi.
- Transport multipart sử dụng `max-file-size=64MB`, `max-request-size=64MB`, `resolve-lazily=true` và `server.tomcat.max-swallow-size=64MB`.
- Transport lớn hơn giới hạn nghiệp vụ có chủ đích: request hợp lệ tối đa 5 × 10 MiB còn đủ multipart overhead; đồng thời request đơn khoảng 50 MiB vẫn đi tới validator server để bị từ chối an toàn thay vì bị container reset kết nối trước controller.

### 3.1 As-built xử lý upload vượt giới hạn

- Browser kiểm tra số file, dung lượng từng file và số video trước khi submit để phản hồi ngay tại form.
- Browser validation chỉ là UX; forged request hoặc client tắt JavaScript vẫn phải qua validator server.
- Tệp lớn hơn 10 MiB nhưng tổng request chưa vượt 64 MiB bị `MediaUploadValidator` từ chối trước Cloudinary và trước transaction database.
- Tổng multipart vượt giới hạn transport được xử lý bằng `@ControllerAdvice`, dùng thông báo an toàn và redirect về form tạo yêu cầu.
- Validation thất bại không tạo `rescue_requests`, `media_assets`, notification hoặc Cloudinary resource.
- Không hiển thị stack trace, giới hạn hạ tầng hoặc chi tiết Cloudinary cho người dùng.

## 4. Quyền media

- Citizen được đính kèm media khi tạo yêu cầu.
- Citizen chỉ được thay/xóa media của chính yêu cầu khi yêu cầu còn `PENDING`.
- Citizen không được thao tác media của user khác.
- Rescuer chỉ được tải evidence khi hoàn thành assignment mà mình còn active membership.
- Evidence hoàn thành không được sửa/xóa sau khi transaction hoàn tất.
- Coordinator được xem media của request trong luồng điều phối nhưng không thay đổi media.
- URL media không được đưa vào notification payload.

## 5. Cloudinary folders

- Local Đạt: `drss/local/dat`
- Local Vy: `drss/local/vy`
- Local Duy: `drss/local/duy`
- CI: `drss/ci`
- Production: `drss/prod`

Production sử dụng một Cloudinary product environment chung. Local folder được tách để tránh xóa nhầm media giữa ba thành viên.

## 6. Upload và rollback

Cloudinary là hệ thống ngoài database nên không tham gia transaction MySQL.

Luồng upload chuẩn:

1. Kiểm tra count, size, MIME và byte signature.
2. Upload từng file lên Cloudinary.
3. Nếu một upload thất bại, xóa bù tất cả file đã upload trước đó.
4. Sau khi upload đủ, thực hiện transaction database lưu request/media/history/notification.
5. Nếu transaction database rollback, xóa bù media Cloudinary đã upload.
6. Cleanup là idempotent; kết quả `not found` được xem là thành công.
7. Nếu cleanup tức thời thất bại, metadata hoặc cleanup record phải còn đủ dữ liệu để retry; không ghi API secret hoặc secure URL vào log lỗi.

### 6.1 As-built của luồng citizen create

- Controller không trực tiếp gọi Cloudinary hoặc repository.
- Orchestration ngoài transaction xác thực toàn bộ file rồi upload tuần tự.
- Một service transaction riêng lưu request và toàn bộ `media_assets`.
- Upload lỗi giữa chừng hoặc transaction database lỗi đều xóa bù theo thứ tự ngược.
- Xóa bù lỗi tạo một `media_cleanup_tasks` bằng transaction `REQUIRES_NEW`.
- Cleanup task chỉ giữ `public_id`, `resource_type`, failure code và retry metadata; không giữ API secret hoặc `secure_url`.
- Notification chưa được tạo trong checkpoint này; nội dung đó thuộc checkpoint domain-event/after-commit tiếp theo.
- Mọi màn hình đọc `MediaAsset` phải phân nhánh theo `resource_type`; không được đưa URL video vào thẻ `<img>`.
- Ảnh dùng `<img>` và liên kết mở tài nguyên gốc; video dùng `<video controls>` với MIME type đã lưu.
- Trong phạm vi hiện tại, trang chi tiết của người dân và điều phối viên đều phải hiển thị đúng ảnh/video.
- Trang nhiệm vụ của cứu hộ chưa đọc `MediaAsset`; việc bổ sung media vào màn hình đó không được tuyên bố hoàn thành trong T2-05 nếu chưa có query, authorization và test tương ứng.

## 7. Xóa media

1. Transaction database đặt `deleted_at`.
2. Sau commit mới gọi Cloudinary destroy với đúng resource type.
3. Khi Cloudinary trả `ok` hoặc `not found`, đặt `cloud_deleted_at`.
4. Nếu Cloudinary lỗi, giữ `cloud_deleted_at = NULL` để retry.
5. Video luôn xóa với `resource_type=video`; ảnh dùng `resource_type=image`.
6. Xóa dùng cache invalidation.

## 8. Metadata database

Mỗi `media_assets` lưu tối thiểu:

- `request_id`
- `uploader_user_id`
- `public_id`
- `secure_url`
- `resource_type`: `IMAGE` hoặc `VIDEO`
- `mime_type`
- `format`
- `width`, `height`
- `bytes`
- `duration_seconds` cho video
- `created_at`, `deleted_at`, `cloud_deleted_at`

## 9. Notification transaction

- Notification record được tạo trong cùng transaction với thay đổi business state và status history.
- Transaction thất bại thì không còn notification record và không có WebSocket event.
- Listener sử dụng `AFTER_COMMIT`.
- WebSocket lỗi sau commit không rollback business transaction.
- Client luôn có thể tải lại notification từ database sau khi reconnect.

## 10. Recipient

- Yêu cầu mới: các user `ACTIVE` đang có role `ROLE_COORDINATOR` chưa bị revoke.
- Assignment mới: các user `ACTIVE` là thành viên hiện tại của team, `left_at IS NULL`.
- Thay đổi trạng thái: citizen sở hữu request.
- User bị vô hiệu hóa, role bị revoke hoặc membership đã rời đội không được nhận event mới.
- Mỗi recipient có một notification row riêng.

## 11. WebSocket authorization

- Handshake xác thực bằng JWT HttpOnly cookie hiện tại.
- Personal destination của citizen: `/user/queue/notifications`.
- Coordinator destination: `/user/queue/coordinator/notifications`, chỉ user còn `ACTIVE` và còn `ROLE_COORDINATOR` được subscribe.
- Team destination: `/user/queue/teams/{teamId}/notifications`, chỉ active member của đúng team được subscribe.
- Coordinator/team destination đều được Spring phân giải thành private user queue; không broadcast notification ID qua shared topic.
- Client không được gửi business command qua broker.
- Forged team destination phải bị từ chối.
- Payload chỉ chứa notification ID, loại sự kiện, reference ID, title/message an toàn và timestamp.
- Payload không chứa địa chỉ chi tiết, tọa độ, số điện thoại, media URL, JWT hoặc dữ liệu nhạy cảm.

### 11.1 As-built checkpoint 05.3

- `Notification` có `event_type` riêng; `reference_type` tiếp tục biểu diễn loại aggregate.
- Recipient được truy vấn lại từ database trong transaction tạo notification.
- Mỗi recipient có một row và một immutable delivery event.
- Listener dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- `SimpMessagingTemplate.convertAndSendToUser(...)` sử dụng UUID user làm principal name.
- Client chỉ được subscribe destination đã cho phép; mọi STOMP `MESSAGE` từ client bị từ chối.
- Subscription coordinator kiểm tra role hiện tại; subscription team kiểm tra `user.status = ACTIVE` và `left_at IS NULL`.
- Payload không chứa địa chỉ, tọa độ, số điện thoại, URL media, JWT hoặc secret.

### 11.2 Notification access và reload

- `GET /api/notifications` chỉ trả tối đa 50 notification mới nhất của user đang xác thực, sắp xếp theo `created_at DESC, id DESC`.
- Response gồm danh sách an toàn và tổng số notification chưa đọc của chính user.
- `POST /api/notifications/{notificationId}/read` đánh dấu một notification thuộc chính user là đã đọc.
- Mark-read là idempotent: notification đã đọc không bị đổi lại `read_at`.
- Notification không tồn tại và notification thuộc user khác cùng trả `404` để không làm lộ ID hợp lệ.
- Endpoint thay đổi trạng thái bắt buộc CSRF và không nhận `recipientUserId` từ client.
- Database là nguồn tải lại sau reconnect; WebSocket chỉ là kênh báo có thay đổi.
- Checkpoint này chưa cài bell/dropdown, STOMP client, reconnect hoặc error UX; các phần đó vẫn deferred sang T3-02.

## 12. Cấu hình và secret

Các biến bắt buộc:

- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- `CLOUDINARY_FOLDER`

Secret thật chỉ nằm trong local ignored file, GitHub Actions secret hoặc môi trường deploy. Không commit, không ghi log và không gửi xuống browser.

## 13. Kiểm thử bắt buộc

- JPEG, PNG, WebP và MP4 hợp lệ.
- Sai MIME, giả chữ ký file, file rỗng và file lớn hơn 10 MiB bị chặn.
- Quá 5 file hoặc quá 1 video bị chặn.
- Upload nhiều file thất bại giữa chừng cleanup file trước đó.
- Database rollback cleanup toàn bộ Cloudinary upload.
- Delete sau commit và retry idempotent.
- Notification row rollback cùng business transaction.
- Không phát WebSocket trước commit hoặc khi rollback.
- Recipient user/team chính xác.
- Role revoked, inactive user và left membership không nhận event.
- Forged subscription bị từ chối.
- Không lộ dữ liệu riêng trong payload.
- Reload chỉ trả notification của user đang đăng nhập và giới hạn tối đa 50 dòng.
- Unread count không tính notification của user khác.
- Mark-read đúng ownership, idempotent và bắt buộc CSRF.
- Forged notification ID của user khác bị che bằng phản hồi `404` chung.
- Browser chặn tệp lớn hơn 10 MiB trước submit và hiển thị lỗi ngay tại input media.
- Forged/no-JavaScript request có tệp lớn hơn 10 MiB vẫn bị server từ chối, không gọi Cloudinary và không tạo request/media.
- Multipart vượt giới hạn transport nhận phản hồi redirect an toàn thay vì trang lỗi kỹ thuật trong tầng MVC.

## 14. Quyết định trình tự UI

Checklist ban đầu ghi Vy cài STOMP UI bằng commit riêng trong T2-05. Nhóm đã đổi thứ tự để tránh Vy phát triển trên backend chưa review:

1. Đạt hoàn thiện backend T2-05.
2. Duy review transaction, recipient và automated tests.
3. T2-05 merge.
4. Đạt hoàn thiện và merge T3-03.
5. Vy tạo branch T3-02 từ `main` sạch và cài STOMP UI/reconnect/error UX.

Handoff và báo cáo phải ghi đây là thay đổi trình tự có chủ đích, không đánh dấu STOMP UI hoàn thành trong T2-05.
