# T3-01 — Admin and Statistics Contract

- **Status:** Approved
- **Approved date:** 2026-10-20
- **Task:** T3-01
- **GitHub issue:** #10
- **Implementation branch:** `feat/t3-01-admin-stats`
- **Baseline commit:** `f65e400`
- **Artifact/Trace ID:** `ADMIN-01`, `STATS-01`
- **Requirements & Decisions:** `FR-ADM-01..03`, `FR-TEAM-01`, `FR-REQ-10`, `NFR-PERF-01`, `OD-12`, `PD-06`, `PD-08`, `PD-09`, `PD-14`

## 1. Mục đích

Tài liệu này khóa phạm vi, hành vi, quyền truy cập, validation, truy vấn thống kê, kiểm thử và cách bàn giao cho T3-01 trước khi triển khai.

T3-01 cung cấp hai nhóm chức năng:

- Quản trị tối thiểu cho loại thiên tai, người dùng/vai trò và đội cứu hộ.
- Thống kê yêu cầu cứu hộ theo trạng thái, mức độ khẩn cấp và khoảng thời gian.

Mọi thay đổi ngoài contract phải được thảo luận, ghi lại trong tài liệu này và được nhóm chấp thuận trước khi code.

## 2. Nguồn yêu cầu và mức độ chốt

Bảng yêu cầu hiện chỉ cung cấp tên/mã yêu cầu và trạng thái, chưa mô tả đầy đủ hành vi chi tiết:

- `FR-ADM-01`: Quản lý loại thiên tai.
- `FR-ADM-02`: Quản lý người dùng và vai trò — mục này trong bảng còn ghi “Cần xác nhận”.
- `FR-ADM-03`: Xem thống kê cơ bản.
- `FR-TEAM-01`: Quản lý đội cứu hộ.
- `FR-REQ-10`: Tìm kiếm và lọc.
- `NFR-PERF-01`: Phân trang dữ liệu.
- `OD-12`: Phạm vi thống kê tối thiểu — trạng thái `Closed`.
- `PD-06`, `PD-08`, `PD-09`, `PD-14`: quy tắc quản trị thay đổi và bàn giao.

Các quy tắc chi tiết trong contract này là quyết định của nhóm, được chấp thuận ngày 2026-10-20 để loại bỏ điểm mơ hồ trước khi triển khai. Contract không tự nhận rằng các chi tiết này đã tồn tại trong bảng yêu cầu gốc.

## 3. Nguyên tắc kiến trúc

- Giữ kiến trúc Spring Boot, Spring MVC, Thymeleaf, Spring Security, Spring Data JPA, Flyway và MySQL hiện có.
- Controller xử lý HTTP, binding, validation và điều hướng; không chứa truy vấn nghiệp vụ phức tạp.
- Service thực thi quy tắc nghiệp vụ, transaction và kiểm tra bất biến.
- Repository thực hiện truy vấn dữ liệu và aggregate tại database.
- Mọi mutation dùng `POST` và bắt buộc CSRF.
- Authorization phải được chặn phía server; việc ẩn nút trên UI chỉ hỗ trợ trải nghiệm.
- Không sửa migration đã chạy `V1`–`V3`.
- Không làm suy yếu các kiểm tra JWT, trạng thái user, `auth_version` và role hiện tại của T2-01.
- Không làm hỏng luồng rescuer của T2-04 hoặc UI component dùng chung của T1-08.

## 4. Ma trận quyền

| Phạm vi | ADMIN | COORDINATOR | RESCUER | CITIZEN | Anonymous |
| --- | --- | --- | --- | --- | --- |
| `/admin/**` | Cho phép | Từ chối `403` | Từ chối `403` | Từ chối `403` | Chuyển tới đăng nhập |
| `/stats/**` | Cho phép | Cho phép | Từ chối `403` | Từ chối `403` | Chuyển tới đăng nhập |

Cấu hình bảo mật phải bổ sung:

- `/stats/**` dùng `hasAnyAuthority("ROLE_ADMIN", "ROLE_COORDINATOR")`.
- `/admin/**` tiếp tục dùng `hasAuthority("ROLE_ADMIN")`.
- Không dựa vào navigation hoặc điều kiện Thymeleaf để bảo vệ endpoint.

## 5. Quản lý loại thiên tai

### 5.1 Route

- `GET /admin/disasters`
- `GET /admin/disasters/new`
- `POST /admin/disasters`
- `GET /admin/disasters/{id}/edit`
- `POST /admin/disasters/{id}`
- `POST /admin/disasters/{id}/status`

### 5.2 Chức năng

- Danh sách có tìm kiếm theo mã hoặc tên.
- Lọc theo trạng thái hoạt động.
- Phân trang theo quy tắc chung tại mục 9.
- Tạo loại thiên tai.
- Chỉnh sửa tên và mô tả.
- Kích hoạt hoặc vô hiệu hóa.
- Hiển thị empty state khi không có dữ liệu phù hợp.
- Hiển thị lỗi validation tại đúng trường và giữ lại dữ liệu người dùng đã nhập.

### 5.3 Validation và bất biến

- `code` bắt buộc, chuẩn hóa chữ hoa, tối đa 20 ký tự và duy nhất.
- `name` bắt buộc, tối đa 100 ký tự và duy nhất không phân biệt hoa thường.
- `description` là tùy chọn và phải tuân theo độ dài cột hiện có.
- `code` không được thay đổi sau khi tạo.
- Không hard-delete loại thiên tai.
- Nếu dữ liệu đã được tham chiếu, chỉ cho phép vô hiệu hóa.
- Duplicate phải trả lỗi nghiệp vụ dễ hiểu, không để lỗi constraint thành HTTP 500.

## 6. Quản lý người dùng và vai trò

### 6.1 Route

- `GET /admin/users`
- `GET /admin/users/{id}`
- `POST /admin/users/{id}/status`
- `POST /admin/users/{id}/roles`

### 6.2 Chức năng

- Danh sách hiển thị mã người dùng, họ tên, email, điện thoại, trạng thái và vai trò hiện tại.
- Tìm kiếm theo mã người dùng, họ tên, email hoặc điện thoại.
- Lọc theo trạng thái và vai trò.
- Phân trang theo quy tắc chung tại mục 9.
- Trang chi tiết hiển thị thông tin cần thiết cho quản trị, không hiển thị dữ liệu nhạy cảm.
- Admin được chuyển trạng thái giữa `ACTIVE` và `BANNED` đối với tài khoản đã kích hoạt.
- Admin được gán hoặc thu hồi các role đã tồn tại.

### 6.3 Role được phép

T3-01 chỉ dùng tập role cố định hiện có:

- `ROLE_ADMIN`
- `ROLE_COORDINATOR`
- `ROLE_RESCUER`
- `ROLE_CITIZEN`

Không triển khai tạo, đổi tên hoặc xóa định nghĩa role.

### 6.4 Quy tắc an toàn

- Không cho admin chuyển tài khoản `INACTIVE` thành `ACTIVE`; kích hoạt phải đi qua OTP của T2-01.
- Không cho admin sửa email, mật khẩu, OTP hoặc token của người dùng.
- Không hard-delete người dùng.
- Không hiển thị `password_hash`, JWT, OTP digest, `auth_version` hoặc dữ liệu bảo mật nội bộ.
- Admin không được tự chuyển tài khoản của mình sang `BANNED`.
- Admin không được tự thu hồi `ROLE_ADMIN` của chính mình.
- Không được vô hiệu hóa hoặc thu hồi quyền của admin hoạt động cuối cùng.
- Mỗi user phải còn ít nhất một role đang hoạt động sau cập nhật.
- Mọi thay đổi trạng thái hoặc role phải tăng `users.auth_version` trong cùng transaction để JWT cũ bị từ chối ở request tiếp theo.
- Thay đổi trạng thái/role thành công phải trả thông báo rõ ràng nhưng không lộ dữ liệu nhạy cảm.

### 6.5 Giới hạn mô hình dữ liệu hiện tại

Khóa hiện tại của `user_roles` biểu diễn trạng thái gán role hiện thời, không phải lịch sử gán/thu hồi đầy đủ. T3-01 không triển khai audit log role chuyên dụng.

Quy tắc “không mất admin cuối cùng” phải được kiểm tra trong service và transaction. Bảo đảm tuyệt đối trước hai thao tác quản trị đồng thời trên nhiều instance là hướng hardening sau T3-01.

## 7. Quản lý đội cứu hộ

### 7.1 Route

- `GET /admin/teams`
- `GET /admin/teams/new`
- `POST /admin/teams`
- `GET /admin/teams/{id}/edit`
- `POST /admin/teams/{id}`
- `POST /admin/teams/{id}/status`

### 7.2 Chức năng

- Danh sách có tìm kiếm theo mã đội, tên đội hoặc tên đội trưởng.
- Lọc theo trạng thái đội.
- Phân trang theo quy tắc chung tại mục 9.
- Tạo đội cứu hộ.
- Chỉnh sửa dữ liệu cơ bản và trạng thái.
- Không hard-delete đội.
- Hiển thị empty state và lỗi validation phù hợp.

### 7.3 Validation

- Mã đội bắt buộc, duy nhất và tối đa 20 ký tự.
- Tên đội bắt buộc, duy nhất không phân biệt hoa thường và tối đa 100 ký tự.
- Tên đội trưởng bắt buộc và tối đa 100 ký tự.
- Số điện thoại chỉ gồm chữ số và dài từ 10 đến 15 ký tự.
- Trạng thái chỉ nhận enum hiện có: `AVAILABLE`, `BUSY`, `OFFLINE`.
- Duplicate hoặc enum không hợp lệ không được trở thành HTTP 500.

### 7.4 Thành viên đội

T3-01 không quản lý thêm/xóa/chuyển thành viên đội. Lý do:

- `FR-TEAM-01` hiện chưa mô tả rõ vòng đời thành viên.
- Khóa dữ liệu hiện tại theo cặp team/user không lưu trọn vẹn nhiều giai đoạn tham gia của cùng một người.
- T2-04 đã có nghiệp vụ rescuer dựa trên quan hệ đội hiện tại; mở rộng vội có nguy cơ phá hợp đồng đó.

Quản lý thành viên đội phải được tách thành task và quyết định dữ liệu riêng.

## 8. Thống kê yêu cầu cứu hộ

### 8.1 Route và quyền

- `GET /stats/requests`
- Cho phép `ROLE_ADMIN` và `ROLE_COORDINATOR`.

### 8.2 Nội dung tối thiểu

Dashboard phải hiển thị trong khoảng ngày đã chọn:

- Tổng số yêu cầu cứu hộ.
- Số lượng theo từng trạng thái yêu cầu.
- Số lượng theo từng mức độ khẩn cấp.
- Bộ lọc `fromDate` và `toDate`.
- Empty state khi tổng bằng 0.
- Error state khi khoảng ngày không hợp lệ.

Tất cả trạng thái và mức độ khẩn cấp trong enum hiện có phải được hiển thị. Nhóm không có dữ liệu phải hiển thị `0`, không bị biến mất khỏi dashboard.

### 8.3 Khoảng thời gian

- Nếu cả hai ngày không được cung cấp, mặc định 30 ngày bao gồm hôm nay: từ `today - 29 ngày` đến `today`.
- Nếu chỉ có một trong hai ngày, trả lỗi validation.
- `fromDate` phải nhỏ hơn hoặc bằng `toDate`.
- Không chấp nhận ngày trong tương lai.
- Khoảng chọn tối đa 366 ngày, tính cả hai đầu.
- Ngày được hiểu theo `LocalDate`/`LocalDateTime` của ứng dụng hiện tại.
- Query dùng khoảng nửa mở: `[fromDate 00:00, toDate + 1 ngày 00:00)`.
- Hỗ trợ nhiều múi giờ nằm ngoài phạm vi T3-01.

### 8.4 Quy tắc truy vấn

- Aggregate phải được thực hiện trong database bằng query đếm/group, không tải toàn bộ request vào Java để đếm.
- Kết quả phải được map về đầy đủ enum, bổ sung `0` cho category không xuất hiện trong query.
- Dashboard tối thiểu không yêu cầu biểu đồ xu hướng theo ngày.
- Thống kê aggregate không phân trang.

## 9. Tìm kiếm, lọc và phân trang

Áp dụng cho danh sách loại thiên tai, người dùng và đội cứu hộ:

- Page nội bộ là zero-based; UI hiển thị one-based.
- Kích thước mặc định: 20.
- Lựa chọn UI: 10, 20 hoặc 50.
- Hard limit phía server: 100.
- Từ khóa được trim, tìm kiếm không phân biệt hoa thường và tối đa 100 ký tự.
- Không có từ khóa được hiểu là không lọc theo từ khóa.
- Filter không hợp lệ phải trả validation/error state, không trả HTTP 500.
- Khi filter thay đổi, UI quay về trang đầu.
- Giữ lại filter hợp lệ trong liên kết phân trang.

Trong T3-01, `FR-REQ-10` chỉ được trace cho các màn hình admin/stats thực sự triển khai. Không tuyên bố đã hoàn thành module tìm kiếm toàn bộ yêu cầu cứu hộ nếu chưa có implementation tương ứng.

## 10. Migration và dữ liệu

### 10.1 Migration V4

Tạo file:

`src/main/resources/db/migration/V4__Add_Admin_Statistics_Index.sql`

Nội dung tối thiểu:

```sql
CREATE INDEX idx_req_urgency_time
    ON rescue_requests(urgency, created_at);
```

Không sửa `V1`, `V2` hoặc `V3`.

Index hiện có `(status, created_at)` tiếp tục phục vụ thống kê theo trạng thái. V4 bổ sung `(urgency, created_at)` cho thống kê theo mức độ khẩn cấp.

### 10.2 Seed và fixture

- Không thêm demo seed lâu dài vào V4 chỉ để làm đẹp dashboard.
- V2 hiện đủ dữ liệu nền để ứng dụng khởi động và minh họa dữ liệu không rỗng.
- Integration test tự tạo fixture cần thiết và rollback sau test.
- Các category không có fixture phải vẫn hiển thị số `0`.

## 11. Giao diện

Các template dự kiến:

- `templates/pages/admin/disaster-list.html`
- `templates/pages/admin/disaster-form.html`
- `templates/pages/admin/user-list.html`
- `templates/pages/admin/user-detail.html`
- `templates/pages/admin/team-list.html`
- `templates/pages/admin/team-form.html`
- `templates/pages/stats/request-statistics.html`

Yêu cầu UI:

- Tái sử dụng `main-layout`, navigation, alert và component của T1-08.
- Không nhân bản layout hoặc tạo hệ thống style riêng không cần thiết.
- Navigation admin tiếp tục chỉ hiện cho `ROLE_ADMIN`.
- Thêm liên kết thống kê cho `ROLE_ADMIN` hoặc `ROLE_COORDINATOR`.
- Có loading-independent empty state, validation state và error state dễ hiểu.
- Responsive tại tối thiểu desktop, 820 × 1180 và 390 × 844.
- Không có global horizontal overflow; bảng rộng phải nằm trong vùng cuộn riêng.
- UI ẩn chức năng không thay thế kiểm tra quyền phía server.

## 12. Bảo mật và logging

- Mọi mutation sử dụng `POST` và CSRF.
- Không dùng `GET` để thay đổi trạng thái hoặc role.
- Non-admin truy cập `/admin/**` phải bị từ chối phía server.
- Citizen/Rescuer truy cập `/stats/**` phải bị từ chối phía server.
- Giữ nguyên cơ chế xác thực JWT HttpOnly cookie của T2-01.
- Sau thay đổi trạng thái hoặc role, JWT cũ bị vô hiệu thông qua `auth_version` và kiểm tra dữ liệu quyền hiện tại.
- Không ghi password, JWT, OTP, secret, hash nhạy cảm hoặc dữ liệu form nhạy cảm vào log.
- Không hiển thị stack trace, constraint name hoặc chi tiết nội bộ cho người dùng.

## 13. Transaction và lỗi đồng thời

- Tạo/cập nhật catalog và team dùng transaction tại service.
- Thay đổi user status, role và tăng `auth_version` phải nằm trong cùng transaction.
- Kiểm tra last-active-admin và cập nhật liên quan phải nằm trong cùng transaction.
- Lỗi constraint hoặc concurrent update phải được chuyển thành lỗi nghiệp vụ phù hợp, không trả raw exception.
- Không thêm optimistic-lock column vào các bảng admin chỉ vì T3-01 nếu chưa có migration và quyết định kiến trúc riêng.

## 14. Kiểm thử bắt buộc

### 14.1 Repository/query

- Đếm đúng tổng trong khoảng ngày.
- Đếm đúng theo mọi request status.
- Đếm đúng theo mọi urgency.
- Biên đầu ngày và cuối ngày tuân theo khoảng nửa mở.
- Request ngoài khoảng không được tính.
- Khoảng không có dữ liệu trả tổng `0` và mọi category `0`.
- Query aggregate không tải toàn bộ entity để đếm.
- Search/filter/pagination trả đúng dữ liệu và tổng trang.

### 14.2 Disaster type

- Tạo hợp lệ.
- Chuẩn hóa code.
- Trùng code hoặc name bị từ chối.
- Chỉnh sửa name/description hợp lệ.
- Không đổi code sau khi tạo.
- Kích hoạt/vô hiệu hóa thành công.
- Không có hard-delete endpoint.

### 14.3 Team

- Tạo và chỉnh sửa hợp lệ.
- Trùng code hoặc name bị từ chối.
- Phone không hợp lệ bị từ chối.
- Enum status không hợp lệ bị từ chối.
- Chuyển trạng thái hợp lệ.
- Không có hard-delete endpoint.

### 14.4 User và role

- Danh sách/search/filter/pagination đúng.
- Admin đổi `ACTIVE` ↔ `BANNED` theo đúng quy tắc.
- Không cho admin kích hoạt tài khoản `INACTIVE`.
- Gán và thu hồi role hợp lệ.
- Không cho user mất role cuối cùng.
- Không cho admin tự ban.
- Không cho admin tự thu hồi `ROLE_ADMIN`.
- Không cho vô hiệu hóa hoặc thu hồi quyền admin hoạt động cuối cùng.
- Cập nhật status hoặc role làm tăng `auth_version`.
- JWT cũ bị từ chối sau thay đổi.
- Không trả dữ liệu nhạy cảm ra view.

### 14.5 Security và CSRF

- Anonymous vào route bảo vệ được chuyển tới đăng nhập.
- `ROLE_ADMIN` truy cập được `/admin/**` và `/stats/**`.
- `ROLE_COORDINATOR` không truy cập được `/admin/**` nhưng truy cập được `/stats/**`.
- `ROLE_RESCUER` và `ROLE_CITIZEN` bị từ chối ở cả admin và stats.
- Mutation thiếu CSRF bị từ chối.
- UI ẩn menu đúng role nhưng test server-side vẫn là bằng chứng chính.

### 14.6 Validation thời gian thống kê

- Không truyền ngày dùng đúng mặc định 30 ngày bao gồm hôm nay.
- Chỉ truyền một đầu ngày bị từ chối.
- `fromDate > toDate` bị từ chối.
- Ngày tương lai bị từ chối.
- Khoảng 366 ngày được chấp nhận.
- Khoảng trên 366 ngày bị từ chối.

### 14.7 Integration và hồi quy

- Flyway chạy `V1 → V2 → V3 → V4` trên MySQL rỗng.
- Hibernate `ddl-auto=validate` PASS.
- Admin CRUD/status/role persistence PASS trên MySQL.
- Stats query PASS trên MySQL với fixture kiểm soát được.
- Luồng dùng JWT HttpOnly cookie thật kiểm tra quyền admin/stats.
- Role/status thay đổi làm JWT cũ bị từ chối.
- Toàn bộ test T1-08, T2-01 và T2-04 tiếp tục PASS.

## 15. Manual review bắt buộc

### Admin

- Đăng nhập admin và mở được ba màn hình catalog/user/team.
- Search, filter, pagination hoạt động và giữ query hợp lệ.
- Tạo/sửa/vô hiệu hóa disaster type.
- Tạo/sửa/chuyển trạng thái team.
- Xem user, thay đổi status/role và thấy thông báo đúng.
- Các guard tự ban, tự mất admin, last admin và role cuối cùng được chặn.

### Statistics

- Admin và coordinator mở được dashboard.
- Default 30 ngày hiển thị đúng.
- Khoảng ngày hợp lệ cập nhật số liệu.
- Khoảng ngày sai hiển thị lỗi, không HTTP 500.
- Empty state hiển thị khi không có dữ liệu.

### Security và responsive

- Non-admin nhận `403` khi gọi admin route trực tiếp.
- Citizen/rescuer nhận `403` khi gọi stats route trực tiếp.
- POST thiếu CSRF bị từ chối.
- Desktop, tablet 820 × 1180 và mobile 390 × 844 không có global overflow.
- Chỉ dùng ảnh chạy thật của đúng commit được bàn giao.

## 16. File handoff cuối

Tạo:

`docs/context/T3-01-admin-stats-handoff.md`

Handoff phải ghi trung thực:

- Branch và implementation commit đã review.
- Phạm vi đã làm và phần ngoài phạm vi.
- Migration/index đã thêm.
- Route và role matrix.
- Lệnh test cùng số test/kết quả thực tế.
- MySQL integration/Flyway/Hibernate validation.
- Manual browser matrix và viewport.
- GitHub Actions của đúng commit.
- Link ảnh evidence thật.
- Known limitations và việc còn chờ, nếu có.

Không ghi PASS cho kiểm tra chưa thực hiện.

## 17. Ngoài phạm vi T3-01

- CRUD định nghĩa role.
- Hard delete disaster type, user hoặc team.
- Admin sửa email, password, OTP hoặc token.
- Quản lý thành viên đội.
- Audit log quản trị chuyên dụng.
- Export dashboard thành PDF/ảnh như một chức năng sản phẩm.
- WebSocket hoặc dashboard thời gian thực.
- Biểu đồ xu hướng nâng cao.
- Cache/Redis cho thống kê.
- Hỗ trợ nhiều múi giờ.
- Bảo đảm last-admin tuyệt đối trên nhiều instance trước mọi race condition phức tạp.

`Export Image/PDF` trong output của task được hiểu là ảnh/PDF bằng chứng dùng cho báo cáo, không phải tính năng export trong ứng dụng.

## 18. Tiêu chí hoàn thành

T3-01 chỉ hoàn thành khi:

- Admin catalog/team/user đúng quyền, validation và transaction.
- Dashboard thống kê đúng dữ liệu, khoảng thời gian và empty/error state.
- Non-admin bị chặn phía server.
- Query/security/service/integration tests bắt buộc PASS.
- Flyway `V1 → V4` và Hibernate validate PASS trên MySQL.
- Regression test của T1-08, T2-01 và T2-04 PASS.
- Manual browser test desktop/tablet/mobile PASS.
- Không lộ secret hoặc dữ liệu nhạy cảm trong source, log, view hay evidence.
- GitHub Actions PASS trên commit bàn giao.
- Handoff phản ánh đúng kết quả thực tế.
- PR liên kết issue bằng `Closes #<issue-number>`.
- Reviewer nghiệp vụ/quyền/query/test kết luận trực tiếp trên PR.
- Vy review dashboard và responsive nếu UI thay đổi.

## 19. Quy tắc thay đổi contract

Nếu trong lúc triển khai phát hiện schema, yêu cầu hoặc dependency buộc phải thay đổi một quyết định ở trên:

1. Dừng phần code bị ảnh hưởng.
2. Mô tả chênh lệch và tác động.
3. Nhóm chốt quyết định mới.
4. Cập nhật contract với ngày làm rõ.
5. Commit tài liệu trước hoặc cùng checkpoint code có liên quan.

Không tự mở rộng phạm vi chỉ vì implementation thuận tiện.
