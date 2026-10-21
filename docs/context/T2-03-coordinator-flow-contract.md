# T2-03 Coordinator Flow Contract

- **Status:** Approved for implementation
- **Approved date:** 2026-10-21
- **Task:** T2-03
- **Baseline commit:** `298cb3d`
- **Source of truth:** Báo cáo Word `Nhom07_BaoCaoCuoiKy.docx` và các contract/handoff đã merge

## 1. Phạm vi T2-03

T2-03 cài luồng điều phối viên:

1. Xem hàng đợi yêu cầu `PENDING` có tìm kiếm, lọc và phân trang.
2. Xem chi tiết yêu cầu gồm người gửi, vị trí, media hiện có và lịch sử.
3. Xác minh `PENDING -> VERIFIED` hoặc từ chối `PENDING -> REJECTED`.
4. Phân công đội từ `VERIFIED -> ASSIGNED` và tạo `rescue_assignments`.

Theo dõi/đóng yêu cầu thuộc T3-03. Upload/xóa media và notification/WebSocket thuộc T2-05.

## 2. Authorization

- Toàn bộ `/coordinator/**` chỉ dành cho `ROLE_COORDINATOR`.
- `ROLE_ADMIN` không mặc nhiên có quyền điều phối.
- Anonymous được chuyển tới `/auth/login`.
- User đã đăng nhập nhưng thiếu role nhận `403`.
- UI ẩn chức năng không thay thế kiểm tra phía server.

## 3. Hàng đợi và chi tiết

- Route hàng đợi: `GET /coordinator/pending`.
- Chỉ liệt kê yêu cầu có trạng thái `PENDING`.
- Kích thước trang: 10.
- Thứ tự: `created_at ASC`, sau đó `id ASC` để ưu tiên yêu cầu cũ và giữ thứ tự ổn định.
- Trang âm được chuẩn hóa thành trang 0; trang vượt phạm vi được đưa về trang cuối nếu có dữ liệu.
- Bộ lọc: từ khóa, urgency, disaster type, ngày bắt đầu và ngày kết thúc.
- Từ khóa tìm trong mã yêu cầu, họ tên, số điện thoại và địa chỉ.
- Khoảng ngày bao gồm trọn ngày kết thúc.
- Route chi tiết: `GET /coordinator/requests/{requestId}`.
- Chi tiết gồm liên hệ người dân, loại thiên tai, urgency, địa chỉ, tọa độ, mô tả, media chưa xóa và lịch sử tăng dần theo thời gian.
- Không tìm thấy request trả `404`.

## 4. Xác minh và từ chối

- Chỉ request `PENDING` được xác minh hoặc từ chối.
- POST phải có CSRF, `requestId`, action, version và reason khi từ chối.
- Xác minh chuyển sang `VERIFIED`; lý do không bắt buộc và history dùng ghi chú mặc định.
- Từ chối chuyển sang `REJECTED`; reason sau trim bắt buộc và tối đa 2000 ký tự.
- Cập nhật request và tạo một history nằm trong cùng transaction.
- Nếu request đã bị người dân hủy hoặc bị thay đổi đồng thời, thao tác bị từ chối và không tạo history sai.
- Dùng optimistic version để phát hiện dữ liệu cũ.

## 5. Phân công đội

- Chỉ request `VERIFIED` được phân công.
- Đội phải tồn tại, có trạng thái `AVAILABLE` và có ít nhất một membership chưa rời đội.
- Service phải đọc lại request, team, assignment hiện hành và version trong transaction.
- Tạo assignment trạng thái `ASSIGNED`, `version = 0`, `completion_time = NULL`.
- Request chuyển `VERIFIED -> ASSIGNED` và tạo history trong cùng transaction.
- Mã assignment có dạng `ASN-` cộng 16 ký tự hex in hoa.
- Không tự động chuyển `rescue_teams.status` sang `BUSY`, đúng thiết kế Chương 5.
- Không thêm unique đơn giản trên `request_id` vì mô hình còn chừa khả năng hủy assignment và phân công lại; service phải chặn active double assignment.
- Nếu team đổi trạng thái hoặc request đổi trạng thái đồng thời, trả conflict và rollback toàn bộ.

## 6. Ghi chú và dữ liệu liên quan

- `rescue_assignments.notes` được T2-04 sử dụng cho báo cáo hoàn thành.
- Ghi chú của điều phối viên khi phân công được lưu tại `status_histories.notes`; assignment mới để `notes = NULL`.
- T2-03 chỉ đọc media đã có. Không cài upload/delete hoặc Cloudinary tạm thời.
- T2-03 không phát notification/WebSocket tạm thời.
- T2-05 sẽ bổ sung notification record trong transaction nghiệp vụ và phát WebSocket sau commit.

## 7. HTTP và lỗi

- Validation đầu vào: `400` hoặc trả form có field error tùy loại request.
- Chưa đăng nhập: redirect tới login.
- Thiếu role: `403`.
- Không tìm thấy resource: `404`.
- State/version/double assignment conflict: `409` hoặc thông báo conflict tương đương trong POST-Redirect-GET.
- Response không lộ stack trace hoặc dữ liệu nội bộ.

## 8. Khác biệt và phần hoãn

- T2-02 đã duyệt cho người dân hủy khi `PENDING` hoặc `VERIFIED`; T2-03 giữ quyết định này và luôn đọc lại trạng thái trước transition/assignment.
- Báo cáo có mô tả media/notification trong flow tổng thể nhưng implementation hiện tại chưa có; T2-05 chịu trách nhiệm hoàn tất và cập nhật SD-10 as-built.
- SD-02, SD-03 và SD-04 phải phản ánh implementation thật; không tuyên bố phần notification đã hoàn tất trong T2-03.
- T3-03 chịu trách nhiệm monitor/close, audit và hardening bổ sung.

## 9. Dữ liệu dùng chung và triển khai

- Không commit DB password, Cloudinary secret, mail app password, OTP/JWT/HMAC secret.
- Local của Đạt, Vy và Duy dùng database riêng; CI dùng database riêng; môi trường deploy dùng database trung tâm.
- Cloudinary project/folder và mail đại diện được chốt ở T2-05/deploy rồi cập nhật guideline/handoff cho cả nhóm.
- T2-03 không tạo thêm biến môi trường dùng chung.

## 10. Kiểm thử bắt buộc

- Queue chỉ có `PENDING`, đúng thứ tự, filter và pagination.
- Detail có contact/location/media/history.
- Anonymous redirect và non-coordinator `403`.
- Verify/reject positive, reason validation, stale version và cancelled race.
- Assignment positive, invalid team, non-AVAILABLE team, no active member và double assignment.
- Transaction không để lại request/history/assignment dở dang khi thất bại.
- MySQL integration verify và manual desktop/mobile flow PASS trước handoff.
