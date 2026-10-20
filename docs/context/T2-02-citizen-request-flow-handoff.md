# T2-02 — Bàn giao luồng yêu cầu cứu nạn của người dân

- Cập nhật: 2026-10-21
- Nhánh: `feat/t2-02-citizen-request-flow`
- Trạng thái: đã hoàn tất kiểm tra local theo xác nhận của người thực hiện; 

## Contract đã triển khai

- Các route: `GET /requests`, `GET /requests/new`, `POST /requests`, `GET /requests/{requestId}`, `POST /requests/{requestId}/cancel`.
- Tất cả route tiếp tục nằm dưới matcher `/requests/**` hiện có, yêu cầu quyền `ROLE_CITIZEN`. Không thay đổi cấu hình authentication chain. Form làm thay đổi dữ liệu dùng POST và CSRF của Spring Security.
- `CreateCitizenRequest` chỉ nhận loại thiên tai, mức khẩn cấp, địa chỉ, tọa độ và mô tả. DTO không nhận owner, trạng thái hoặc mã hiển thị.
- `CitizenRequestController` lấy UUID chủ sở hữu từ `AuthenticatedUserPrincipal`; `CitizenRequestService` dùng UUID này khi tạo yêu cầu, kiểm tra quyền sở hữu và truy vấn danh sách có phân trang.
- Service đặt trạng thái yêu cầu mới là `PENDING` và sinh mã `REQ-` gồm 16 ký tự thập lục phân ngẫu nhiên. Ràng buộc duy nhất trong database là lớp bảo vệ cuối cùng khi mã bị trùng.
- Danh sách người dân sắp xếp theo `createdAt DESC, id DESC`, kích thước trang cố định là 10. Số trang âm được xem là trang 0; số trang vượt quá phạm vi được đưa về trang cuối. Repository lọc dữ liệu theo chủ sở hữu đã xác thực.
- Chi tiết, lịch sử và media chưa bị xóa chỉ được tải sau khi xác minh quyền sở hữu. Yêu cầu không tồn tại và yêu cầu thuộc người khác trả cùng một thông báo không tìm thấy.
- Yêu cầu mới không có dòng lịch sử trạng thái giả. Lịch sử chỉ ghi các lần chuyển trạng thái thực tế, phù hợp với state machine và dữ liệu seed hiện có.
- Người dân được hủy yêu cầu ở trạng thái `PENDING` hoặc `VERIFIED`, trước khi phân công đội cứu hộ. Thao tác ghi trạng thái cũ sang `CANCELLED`, người thực hiện đã xác thực và lý do hủy vào `StatusHistory.notes` trong cùng transaction. `RescueRequest.version` hỗ trợ optimistic locking và được flush trước khi transaction hoàn tất.

## Quyết định và điểm contract còn mở

- Chính sách hủy được chốt theo lựa chọn của người dùng ngày 2026-10-21: cho phép citizen hủy `PENDING` và `VERIFIED`; không cho tự hủy từ `ASSIGNED` trở đi. Khi đã phân công, người dân cần liên hệ điều phối viên để tránh tự giải phóng yêu cầu trong khi đội cứu hộ có thể đang được điều động. Nếu sau này cần hủy yêu cầu đã phân công, phải có luồng điều phối và cập nhật assignment đồng bộ.
- Chưa tìm thấy upload service hoặc contract liên kết media. Khi có sẵn, trang chi tiết sẽ hiển thị media của yêu cầu; thay đổi này không thêm upload hay lưu binary.
- Không thêm migration. Schema V1 hiện tại đã có các trường, constraint và `RescueRequest.version` được sử dụng. T2-01 bổ sung V3.
- Địa chỉ, tọa độ và mô tả vẫn là các trường không bắt buộc vì schema cho phép giá trị null và chưa có business contract được cung cấp quy định khác. Tọa độ được kiểm tra giới hạn nếu người dùng nhập.

## Các file đã thay đổi

- Thêm `CitizenRequestController`, `CitizenRequestService`, DTO tạo/hủy yêu cầu và các trang Thymeleaf tạo, danh sách, chi tiết.
- Thêm query phân trang yêu cầu theo chủ sở hữu, lấy loại thiên tai đang hoạt động, lấy lịch sử theo thứ tự thời gian và lấy media chưa bị xóa.
- Cập nhật liên kết menu người dân dùng chung để trỏ đến `/requests`.
- Thêm unit test cho service và MVC test; đăng ký các repository mới trong annotation mock repository dùng cho test không cần database.

## Kết quả xác minh

- Java: Temurin 17.0.20.1; Maven Wrapper cũng báo Java 17.
- `./mvnw.cmd test`: PASS — 164 test, 0 lỗi thất bại, 0 lỗi chạy.
- `git diff --check`: PASS.
- MySQL/Flyway/Hibernate `clean verify`: PASS theo xác nhận của người thực hiện trên database integration riêng `drss_t202_it`.
- Kiểm tra thủ công trình duyệt: PASS theo xác nhận của người thực hiện; luồng tạo, danh sách, chi tiết/lịch sử và hủy đã được kiểm tra.
- Responsive mobile/desktop: PASS theo xác nhận của người thực hiện.
- GitHub Actions: CHƯA XÁC NHẬN; cần kiểm tra workflow trên commit T2-02 đã push.
- Commit/push: người thực hiện đã commit. 

## Bàn giao cho T2-03/T2-04

- Chủ sở hữu yêu cầu luôn là UUID của principal đã xác thực; không nhận danh tính chủ sở hữu từ form hoặc query parameter.
- Tạo yêu cầu sinh trạng thái `PENDING` và không ghi sự kiện lịch sử khởi tạo giả.
- Mã hiển thị theo định dạng `REQ-` cộng 16 ký tự thập lục phân viết hoa; khóa duy nhất trong database đảm bảo không trùng.
- Danh sách và chi tiết của người dân phải giới hạn theo chủ sở hữu. Tái sử dụng `RescueRequestRepository.findByUser_IdOrderByCreatedAtDescIdDesc` cho danh sách phân trang.
- Mọi cập nhật trạng thái phải giữ nguyên cơ chế optimistic locking và thêm đúng một bản ghi `StatusHistory` trong cùng transaction.
- Người dân được hủy `PENDING` và `VERIFIED`; không được tự hủy từ `ASSIGNED` trở đi. Mọi thay đổi chính sách sau này cần đồng bộ việc giải phóng assignment và ghi lịch sử trong cùng transaction.
