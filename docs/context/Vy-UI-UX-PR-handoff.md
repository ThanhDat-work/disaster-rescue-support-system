# Bàn giao công việc UI/UX và thông báo

## Mục đích

Tài liệu này tóm tắt các thay đổi chưa commit trong workspace để chuẩn bị review và tạo pull request. Hiện chưa tạo nhánh hoặc commit.

## Các thay đổi đã thực hiện

### Thông báo

- Bấm vào thông báo sẽ mở yêu cầu hoặc nhiệm vụ cứu hộ liên quan nếu mã tham chiếu hợp lệ.
- Nếu trang đích cần vai trò đang hoạt động khác, người dùng được chuyển qua luồng đổi vai trò; đường dẫn quay lại trang chi tiết được kiểm tra ở backend.
- Hộp thư hỗ trợ đánh dấu đã đọc từng thông báo, tất cả thông báo chưa đọc hoặc các thông báo được chọn.
- Badge số thông báo chưa đọc hiển thị `99+` khi số lượng vượt quá 99.
- Danh sách mặc định dùng toàn bộ chiều ngang; checkbox chỉ xuất hiện sau khi bật chế độ chọn.
- API đánh dấu hàng loạt chỉ cập nhật thông báo thuộc người dùng đang đăng nhập.

### Điều hướng GET nội bộ và lớp phủ thử lại

- Khi bấm liên kết nội bộ hoặc gửi biểu mẫu lọc GET, skeleton hiện trong lúc kiểm tra/tải trang đích.
- Nếu request GET lỗi hoặc quá 10 giây, skeleton ẩn và lớp phủ “Thử lại” xuất hiện.
- Lớp phủ có nút đóng, nút thử lại; có thể đóng bằng nút ×, bấm bên ngoài thẻ hoặc nhấn Esc.
- Đóng lớp phủ sẽ hủy điều hướng đang chờ. Thử lại sẽ tải lại đúng địa chỉ đích.
- Skeleton chỉ áp dụng cho điều hướng GET nội bộ; không hiện khi gõ URL trực tiếp, tải lại trang hoặc gửi POST.

### Trang chủ và danh sách yêu cầu

- Cập nhật bố cục và giao diện trang chủ, gồm quả địa cầu Three.js/WebGL và hiệu ứng nền.
- Các số liệu tổng quan trang chủ lấy từ dữ liệu controller thay vì giá trị cố định.
- Đã bỏ thanh phân trang phía trên bị trùng trong danh sách yêu cầu của người dân.

### Xử lý quay lại sau khi đổi vai trò

- Endpoint đổi vai trò nhận vai trò đích và đường dẫn quay lại.
- Backend kiểm tra vai trò, giới hạn đường dẫn quay lại vào các trang chi tiết yêu cầu/nhiệm vụ hợp lệ có mã UUID để tránh chuyển hướng tùy ý.

## Các file chính có thay đổi

- **Backend:** `ActiveRoleController`, `HomeController`, `NotificationController`, `NotificationRepository`, `RescueTeamRepository` và `NotificationAccessService`.
- **Template:** thanh điều hướng dùng chung, layout chính, trang đổi vai trò, danh sách yêu cầu của người dân và trang chủ.
- **Frontend:** `main.js`, `async-retry.js`, `home-earth.js`, `page-skeleton.js` và các CSS liên quan (`custom.css`, `style-tokens.css`, `realtime-notifications.css`, `async-retry.css`, `home-page.css`, `page-skeleton.css`).

## Phạm vi và lưu ý khi review

- Tài liệu `LTW-02-Vy-UI-UX-impact-inventory.md` của leader ghi không tạo skeleton giả cho các trang SSR chỉ vì chúng dùng chung layout. Skeleton khi điều hướng GET ở trên theo yêu cầu UX mới nhất, nhưng khác với ghi chú đó. Cần nêu rõ đây là khác biệt có chủ đích để reviewer xác nhận.
- Biểu đồ thống kê của điều phối chưa được sửa trong diff hiện tại. `request-statistics.html` và `statistics-charts.js` không có thay đổi.
- Thiết kế lại trang chủ và luồng đổi vai trò rộng hơn phạm vi UI CP02–CP07 trong tài liệu. Nếu PR chỉ dành cho milestone đó, cân nhắc tách các phần này sang PR riêng.
- Cơ chế điều hướng hiện tải/kiểm tra toàn bộ HTML trước, rồi mới điều hướng trình duyệt bình thường. Mốc 10 giây áp dụng cho bước kiểm tra đó; cần xác nhận hành vi thực tế với mạng bị giới hạn trước khi merge.
- Chưa chạy kiểm thử tự động hoặc kiểm tra bố cục trên trình duyệt.

## Checklist review thủ công đề xuất

1. Bấm liên kết GET nội bộ khi mạng nhanh: skeleton hiện trong lúc chờ rồi mở trang đích.
2. Giới hạn tốc độ hoặc ngắt request GET: sau 10 giây lớp phủ thử lại xuất hiện; thử nút **Thử lại**, nút đóng, click bên ngoài và phím Esc.
3. Mở thông báo của yêu cầu/nhiệm vụ thuộc vai trò hiện tại và vai trò khác; xác nhận đổi vai trò xong quay lại đúng trang chi tiết.
4. Đánh dấu một tin, tất cả tin và các tin được chọn; xác nhận chỉ thông báo của người đăng nhập bị cập nhật và badge thay đổi đúng.
5. Kiểm tra badge với 0, 1, 99 và trên 99 thông báo chưa đọc.
6. Kiểm tra quả địa cầu và bố cục trang chủ ở kích thước desktop, tablet và mobile.

## Comment để đăng lên pull request

```text
Đã cập nhật hộp thông báo (mở chi tiết yêu cầu/nhiệm vụ, chuyển vai trò khi cần, đánh dấu đã đọc từng tin/tất cả/các tin được chọn và badge 99+), luồng điều hướng GET có skeleton chờ tối đa 10 giây trước khi hiện lớp phủ “Thử lại”, cùng giao diện trang chủ và quả địa cầu WebGL.

Lớp phủ có thể đóng bằng nút ×, click bên ngoài hoặc phím Esc; thao tác đóng hủy điều hướng đang chờ. Lưu ý: skeleton cho điều hướng GET khác ghi chú trong LTW-02 về việc không tạo skeleton giả cho trang SSR. Lỗi biểu đồ thống kê điều phối chưa được chỉnh trong diff này. Chưa chạy kiểm thử tự động hoặc kiểm tra giao diện trên trình duyệt; nhờ review và xác nhận phạm vi trước khi merge.
```
