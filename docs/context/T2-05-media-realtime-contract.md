# T2-05 Media and Realtime Contract

- **Status:** Approved for implementation
- **Approved date:** 2026-10-21
- **Task:** T2-05
- **Base commit:** `65b8bd3`
- **Dependencies:** T2-02, T2-03, T2-04
- **Artifact/Trace IDs:** `MEDIA-01`, `WS-01`, `SD-10`
- **Last clarified:** 2026-10-21 — Citizen create upload uses non-transactional orchestration, transactional DB persistence and durable compensating cleanup tasks.

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
- Transport multipart sử dụng `max-file-size=10MB`, `max-request-size=52MB` và `server.tomcat.max-swallow-size=52MB` để đồng bộ giới hạn HTTP với validator ứng dụng.

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
- Personal destination: `/user/queue/notifications`.
- Coordinator topic chỉ dành cho coordinator còn quyền hiện tại.
- Team topic chỉ dành cho active member của đúng team.
- Client không được gửi business command qua broker.
- Forged team destination phải bị từ chối.
- Payload chỉ chứa notification ID, loại sự kiện, reference ID, title/message an toàn và timestamp.
- Payload không chứa địa chỉ chi tiết, tọa độ, số điện thoại, media URL, JWT hoặc dữ liệu nhạy cảm.

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

## 14. Quyết định trình tự UI

Checklist ban đầu ghi Vy cài STOMP UI bằng commit riêng trong T2-05. Nhóm đã đổi thứ tự để tránh Vy phát triển trên backend chưa review:

1. Đạt hoàn thiện backend T2-05.
2. Duy review transaction, recipient và automated tests.
3. T2-05 merge.
4. Đạt hoàn thiện và merge T3-03.
5. Vy tạo branch T3-02 từ `main` sạch và cài STOMP UI/reconnect/error UX.

Handoff và báo cáo phải ghi đây là thay đổi trình tự có chủ đích, không đánh dấu STOMP UI hoàn thành trong T2-05.
