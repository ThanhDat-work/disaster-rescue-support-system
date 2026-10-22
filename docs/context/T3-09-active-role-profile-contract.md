# T3-09 Active Role and User Profile Contract

- **Status:** Locked for future implementation
- **Task:** T3-09
- **Owner:** Đỗ Thành Đạt
- **Reviewers:** Bùi Đức Duy, Phan Nhật Vy
- **Implementation condition:** Bắt đầu sau khi T3-03 được review, merge và main sạch
- **UI condition:** Cài UI cơ bản trong T3-09; Vy có thể tinh chỉnh trong checkpoint UI sau

## 1. Mục tiêu

Cài cơ chế một tài khoản có nhiều role nhưng chỉ có một **active role** tại một thời điểm; bổ sung hồ sơ cá nhân, avatar, thống kê theo role và các ràng buộc chống tự xử lý yêu cầu, tự tăng quyền hoặc thao tác chéo role.

T3-09 phải bảo đảm role switch là thay đổi security context, không chỉ là thay đổi menu giao diện.

## 2. Nguồn sự thật

- Bám theo implementation đã merge của T2-01, T2-02, T2-03, T2-04, T2-05, T3-01 và T3-03.
- Các quyết định trong contract này là phạm vi được owner chấp thuận cho T3-09.
- Không tự mở rộng theo nội dung Notion chưa được team duyệt.
- Báo cáo và sơ đồ as-built phải phản ánh đúng implementation cuối cùng.

## 3. Mô hình identity và active role

- Một user có thể có nhiều role đang được cấp qua `user_roles`.
- Tại một thời điểm, một JWT chỉ chứa đúng một active role.
- Role switch không cấp role mới và không khôi phục role đã bị revoke.
- Backend không tin role gửi từ client nếu role đó không tồn tại trong active grants của user.
- Mọi route nghiệp vụ dùng active authority trong JWT, không dùng toàn bộ role từng được cấp.

### 3.1 Active role sau đăng nhập

- Tài khoản đăng ký mới được cấp `CITIZEN` và đăng nhập mặc định với `CITIZEN`.
- Nếu user có `CITIZEN`, login mới mặc định chọn `CITIZEN`.
- Nếu user không có `CITIZEN` và chỉ có một active role, role đó được chọn.
- Nếu user không có `CITIZEN` và có nhiều active role, sau khi xác thực mật khẩu thành công hệ thống chuyển tới trang chọn role ban đầu.
- Active role không được lưu lâu dài vào `users`; mỗi phiên đăng nhập mới áp dụng lại quy tắc trên.

### 3.2 JWT và cookie

- JWT có claim `active_role` và authorities chỉ chứa role đó.
- JWT tiếp tục chứa user ID, auth version và các claim tối thiểu hiện có.
- Không đưa toàn bộ role vào authorities của JWT.
- Migration V8 tăng `auth_version` của toàn bộ user hiện có để thu hồi các JWT cũ từng chứa nhiều authority trước khi cơ chế active role có hiệu lực.
- Role switch thành công cấp JWT mới và ghi đè HttpOnly cookie cũ.
- Cookie giữ `SameSite=Lax`, path `/`, và `Secure` theo environment.
- Role switch không làm mất CSRF session hợp lệ.
- Logout xóa JWT cookie như contract T2-01.

## 4. Hồ sơ cá nhân

### 4.1 Routes đề xuất

- `GET /account/profile`: xem hồ sơ.
- `GET /account/profile/edit`: form chỉnh sửa.
- `POST /account/profile`: cập nhật trường được phép.
- `POST /account/profile/avatar`: upload/thay avatar.
- `POST /account/profile/avatar/delete`: xóa avatar tùy chọn.
- `GET /account/role-switch`: trang chọn role và nhập mật khẩu.
- `POST /account/role-switch`: xác nhận chuyển role.

Tất cả route `/account/**` yêu cầu authenticated user, nhưng không phụ thuộc active role cụ thể.

### 4.2 Thông tin hiển thị

- Avatar.
- Display code.
- Họ tên.
- Email.
- Số điện thoại.
- Trạng thái tài khoản.
- Active role.
- Danh sách role hiện đang được cấp và chưa revoke.
- Ngày tạo tài khoản.
- Thống kê phù hợp với active role.

### 4.3 Sắp xếp role trên hồ sơ

- Active role hiển thị đầu tiên và có badge nổi bật.
- Các role còn lại sắp xếp cố định: `CITIZEN`, `COORDINATOR`, `RESCUER`, `ADMIN`.
- Không dùng dropdown trên trang hồ sơ để đổi role trực tiếp.
- Có nút **Thay đổi vai trò** dẫn tới `/account/role-switch`.
- Role đã revoke không được hiển thị như lựa chọn chuyển role.

### 4.4 Trường được chỉnh sửa

Cho phép:

- họ tên;
- số điện thoại;
- avatar.

Chỉ đọc hoặc cấm sửa trực tiếp:

- email;
- display code;
- account status;
- role grants;
- active role;
- auth version;
- created time.

Đổi mật khẩu tiếp tục là flow bảo mật riêng, không gộp vào form profile.

### 4.5 Trạng thái tài khoản

- Hiển thị dưới dạng badge/pill, không phải control có thể thay đổi.
- Ví dụ: `Đang hoạt động`, `Chờ kích hoạt`, `Đã vô hiệu hóa`.
- Người dùng không thể tự kích hoạt hoặc vô hiệu hóa tài khoản.

## 5. Role switch

### 5.1 Giao diện

Trang role switch hiển thị:

- active role hiện tại;
- các role đang được cấp và chưa revoke;
- lựa chọn target role;
- ô nhập mật khẩu hiện tại;
- nút xác nhận;
- cảnh báo role switch áp dụng cho toàn bộ tab trong cùng trình duyệt.

### 5.2 Validation và security

- CSRF bắt buộc.
- Target role phải khác active role hiện tại.
- Target role phải đang được cấp và chưa revoke.
- Account phải ở trạng thái `ACTIVE`.
- Mật khẩu phải được xác minh bằng `PasswordEncoder`.
- Mỗi lần role switch đều yêu cầu nhập lại mật khẩu.
- Sai mật khẩu phải dùng generic error, không tiết lộ trạng thái role/account quá mức.
- Áp dụng rate limit cho các lần xác nhận thất bại.
- Không log password, JWT hoặc cookie.
- Không chấp nhận redirect URL tùy ý từ client.

### 5.3 Redirect sau khi chuyển

| Target role   | Redirect               |
| ------------- | ---------------------- |
| `CITIZEN`     | `/requests`            |
| `COORDINATOR` | `/coordinator/pending` |
| `RESCUER`     | `/team/tasks`          |
| `ADMIN`       | `/admin/users`         |

### 5.4 Multi-tab behavior

- JWT cookie dùng chung cho toàn trình duyệt.
- Chuyển role ở một tab làm role mới có hiệu lực cho các tab khác ở request tiếp theo.
- Form mở từ role cũ phải bị authorization từ chối khi submit.
- UI nên hiển thị thông báo: `Vai trò hoạt động đã thay đổi. Vui lòng tải lại trang để tiếp tục.`
- Không cài active role riêng theo tab.

## 6. Route isolation

| Active role   | Phạm vi chính     |
| ------------- | ----------------- |
| `CITIZEN`     | `/requests/**`    |
| `COORDINATOR` | `/coordinator/**` |
| `RESCUER`     | `/team/**`        |
| `ADMIN`       | `/admin/**`       |

- User được cấp nhiều role vẫn chỉ truy cập phạm vi của active role hiện tại.
- `/account/**`, logout và static resources áp dụng rule dùng chung phù hợp.
- Frontend ẩn menu không thay thế backend authorization.
- Truy cập URL của inactive role trả forbidden hoặc redirect an toàn theo security contract.

## 7. Chống xung đột lợi ích

### 7.1 Request owner

User tạo request với tư cách citizen không được dùng role khác để tự xử lý request đó.

Backend phải loại request có:

```text
request.user_id = current_user_id
```

khỏi:

- coordinator queue/tracking;
- coordinator detail;
- verify/reject;
- assignment;
- close;
- rescuer task list/detail;
- rescuer start/complete;
- coordinator/team realtime recipients liên quan tới request đó.

List, detail và command đều phải khóa. Không chỉ ẩn trên frontend.

### 7.2 Team assignment

- Không được assign request cho rescue team có request owner là active member.
- Kiểm tra dựa trên `user_id` và membership có `left_at IS NULL`.
- Assignment service phải kiểm tra trong cùng transaction trước khi tạo assignment.
- Team không phù hợp không được xuất hiện trong danh sách chọn và forged team ID vẫn phải bị backend từ chối.

### 7.3 Coordinator đồng thời là rescuer

- Không cấm coordinator assign request của người khác cho đội mà coordinator đang tham gia chỉ vì họ có nhiều role.
- Các ràng buộc owner, active role, team membership và state machine vẫn áp dụng.
- Nếu báo cáo Word sau này khóa separation-of-duty mạnh hơn, phải cập nhật contract trước khi đổi code.

## 8. Admin self-protection

Admin có thể xem tài khoản của chính mình nhưng backend phải từ chối khi target user trùng current admin đối với:

- cấp role;
- revoke role;
- kích hoạt/vô hiệu hóa;
- xóa tài khoản;
- thay đổi auth/security fields;
- các thao tác tự tăng quyền khác.

Ngoài ra:

- Không được revoke hoặc vô hiệu hóa admin active cuối cùng.
- UI disable/ẩn action tự quản trị chỉ là hỗ trợ; service phải kiểm tra lại.
- Role switch sang `ADMIN` chỉ hợp lệ khi role `ADMIN` đã được admin khác cấp từ trước.

## 9. Audit

### 9.1 Role switch audit

Bổ sung audit bền vững gồm tối thiểu:

- user ID;
- from role;
- to role;
- server timestamp;
- kết quả thành công/thất bại ở mức không lộ mật khẩu;
- source IP đã chuẩn hóa nếu chính sách hiện tại cho phép.

Không lưu password, JWT, cookie hoặc raw secret.

### 9.2 Actor role trong business audit

- `status_histories` phải lưu thêm `actor_role` tại thời điểm transition.
- Không suy luận actor role từ grants hiện tại vì role có thể được thay đổi sau này.
- Migration phải cập nhật dữ liệu cũ bằng giá trị phù hợp hoặc nullable/`SYSTEM` theo quyết định migration được kiểm thử.
- Đây là phạm vi của checkpoint 09.2; checkpoint 09.1 chưa thay đổi `status_histories` và không được báo cáo yêu cầu này là đã hoàn thành.

### 9.3 Auth version

- Cấp/revoke role và thay đổi status phải tăng `auth_version` để JWT cũ mất hiệu lực.
- Role switch thông thường không thay đổi grants nên không cần tăng auth version; chỉ thay JWT active role.

## 10. Avatar

- Chỉ nhận ảnh hợp lệ; không nhận video.
- Giới hạn mặc định 2 MiB mỗi avatar.
- Cloudinary folder: `drss/{environment}/avatars/{userId}`.
- Public ID không dùng email.
- Database lưu tối thiểu public ID và secure URL cần thiết.
- Thay avatar phải cleanup avatar cũ sau khi dữ liệu mới được lưu an toàn.
- Upload thành công nhưng transaction DB fail phải cleanup ảnh mới.
- Delete lỗi phải dùng durable cleanup/retry foundation của T2-05.
- Profile dùng avatar mặc định khi chưa có ảnh.

## 11. Thống kê theo active role

Chỉ hiển thị dữ liệu có thể truy vết đúng tới user hoặc phạm vi đội hợp lệ; không dựng số liệu chưa được lưu.

### 11.1 Citizen

- Cards: tổng request, request đang xử lý, request đã cứu hộ/đóng.
- Doughnut chart: phân bố request theo trạng thái.
- Area hoặc line chart: số request của user theo tháng trong khoảng thời gian giới hạn.

### 11.2 Coordinator

- Cards: số verify/reject, assignment và close do user thực hiện.
- Stacked column chart: action theo tuần, phân nhóm verify/reject/assign/close.
- Dữ liệu actor phải dựa trên audit/history, không dựa vào trạng thái hiện tại đơn thuần.

### 11.3 Rescuer

- Cards: nhiệm vụ hiện tại của đội, đang thực hiện, đã hoàn thành.
- Bar chart: nhiệm vụ đội hoàn thành theo tuần/tháng.
- Doughnut chart tùy dữ liệu: phân bố trạng thái task mà user được phép truy cập.
- Nhãn phải ghi `Nhiệm vụ của đội`, không khẳng định cá nhân hoàn thành nếu schema chưa lưu người trực tiếp thực hiện.

### 11.4 Admin

- Cards: số active role của tài khoản, tuổi tài khoản, số lần role switch gần đây và trạng thái bảo mật.
- Activity timeline cho thao tác tài khoản có audit hợp lệ.
- Không hiển thị thống kê quản trị cá nhân nếu chưa có audit chứng minh actor.
- Không ép tạo biểu đồ chỉ để trang trí; chỉ dùng chart khi dữ liệu thật và có ý nghĩa.

### 11.5 Quy tắc trình bày

- Loại biểu đồ được chọn theo bản chất dữ liệu: doughnut cho tỷ trọng, line/area cho xu hướng, column/bar cho so sánh.
- Không dùng nhiều biểu đồ trùng ý nghĩa.
- Có empty state khi chưa đủ dữ liệu.
- Query phải aggregate tại database, có giới hạn thời gian và tránh N+1.
- Vy có thể tinh chỉnh layout, màu sắc, responsive và chart presentation sau khi contract dữ liệu đã ổn định.

## 12. Navbar

- Navbar hiển thị badge active role ở vị trí gần user identity/avatar.
- Badge chỉ thể hiện trạng thái vai trò hiện tại; không phải link, button, dropdown hoặc control chuyển role.
- Nhấn vào badge không điều hướng sang trang khác.
- Việc chuyển role chỉ bắt đầu từ nút **Thay đổi vai trò** trên trang hồ sơ và dẫn tới `/account/role-switch`.
- Màu gợi ý:
  - citizen: xanh dương;
  - coordinator: cam;
  - rescuer: đỏ;
  - admin: tím.
- Mobile navbar vẫn phải thể hiện rõ active role.

## 13. Transaction và rollback

- Profile update dùng field whitelist và transaction.
- Role switch audit và JWT issuance phải có thứ tự an toàn; không để UI báo đổi thành công khi target role không hợp lệ.
- Avatar upload dùng compensation cleanup như T2-05.
- Admin role/status change, auth version và audit phải nhất quán trong cùng transaction.
- Conflict-of-interest check và assignment creation nằm trong cùng transaction/lock để chống forged ID và race.

## 14. Automated test bắt buộc

### 14.1 Authentication/role switch

- Login mới mặc định citizen khi citizen được cấp.
- Single non-citizen role được chọn đúng.
- Multi-role không có citizen đi qua initial role selection.
- JWT chỉ chứa active role.
- Switch đúng mật khẩu thành công và thay cookie.
- Sai mật khẩu, revoked role, forged role, inactive account bị chặn.
- Rate limit và CSRF hoạt động.
- Old-role URL bị chặn sau switch.

### 14.2 Conflict of interest

- Owner không xuất hiện trong coordinator list/detail/command.
- Owner không xuất hiện trong rescuer task access.
- Team chứa owner không thể được assign.
- Owner không nhận coordinator/team notification của request mình.
- Forged IDs không vượt qua service/repository checks.

### 14.3 Admin safety

- Admin không thể sửa status/role của chính mình.
- Frontend bypass vẫn bị service chặn.
- Last active admin không thể bị revoke hoặc vô hiệu hóa.
- Role/status change làm JWT cũ mất hiệu lực.

### 14.4 Profile/avatar/statistics

- Chỉ field cho phép được cập nhật.
- Email/status/role/display code không thể bị mass assignment.
- Avatar validation, rollback cleanup, replace và delete retry PASS.
- Statistics chỉ lấy dữ liệu của user/đội hợp lệ.
- Empty states và query count/performance được kiểm tra.

### 14.5 Full regression

- Full unit tests.
- MySQL integration verify.
- Auth, citizen, coordinator, rescuer, admin, media, notification và T3-03 regression.

## 15. Manual test bắt buộc

1. Multi-role login mặc định citizen.
2. Profile hiển thị active role và các role được cấp.
3. Nút **Thay đổi vai trò** mở trang riêng.
4. Sai mật khẩu không đổi role.
5. Đúng mật khẩu đổi role và redirect đúng.
6. Tab cũ không còn thao tác được bằng role trước.
7. Coordinator/rescuer không nhìn thấy request do chính user tạo.
8. Team chứa request owner không thể được assign.
9. Admin không tự thêm role hoặc vô hiệu hóa mình.
10. Profile update và avatar lifecycle hoạt động.
11. Role badge và statistics thay đổi theo active role.
12. Desktop/mobile không có lỗi UI P0.

## 16. Bốn checkpoint triển khai

### Checkpoint 09.1 — Context và active-role authentication

- Lấy context auth/JWT/security/user-role/navigation.
- Khóa migration và active-role claims.
- Cài login default, role selection, password-confirmed switch, JWT replacement và audit.
- Test route isolation và multi-tab stale role.

### Checkpoint 09.2 — Conflict-of-interest hardening

- Owner exclusion cho coordinator/rescuer.
- Team-owner assignment exclusion.
- Recipient exclusion.
- Admin self-protection và last-admin rule.
- Test forged IDs, transaction và regression.

### Checkpoint 09.3 — Profile, avatar và statistics

- Profile read/update whitelist.
- Cloudinary avatar lifecycle.
- Role list, active badge và role-specific statistics/charts.
- Basic responsive UI để Vy tinh chỉnh sau.

### Checkpoint 09.4 — Full verification và handoff

- Full unit/MySQL integration tests.
- Manual flow toàn bộ role.
- Security/data/secret audit.
- Cập nhật contract, handoff, report trace và reviewer instructions.
- CI PASS trước merge.

## 17. Deferred

- Không cài active role riêng theo browser tab.
- Không cho user tự đăng ký role nghiệp vụ.
- Không cho sửa email trong T3-09.
- Không thêm social login hoặc MFA nếu chưa có task riêng.
- Không dựng thống kê không có dữ liệu audit thật.
- UI polish nâng cao thuộc checkpoint UI của Vy sau khi backend T3-09 ổn định.

## 18. Điều kiện nghiệm thu

- Mỗi JWT chỉ có một active role.
- Role switch luôn yêu cầu password và chỉ chọn active grant.
- Inactive-role URLs bị chặn ở backend.
- Không user nào tự xử lý request của chính mình qua role khác.
- Admin không thể tự tăng quyền hoặc tự vô hiệu hóa.
- Profile chỉ sửa field cho phép; avatar cleanup/rollback đúng.
- Statistics đúng user/đội, đúng nhãn và có biểu đồ phù hợp.
- Audit lưu được actor role và role switch.
- Full regression, MySQL integration, manual flow và CI đều PASS.

## 19. Checkpoint 09.2 as-built

- Coordinator queue, tracking, detail và command đều loại request có owner trùng current user ngay tại repository query.
- Rescuer list, detail, start và complete đều loại assignment có request owner trùng current user.
- Team chứa request owner với membership còn hiệu lực không xuất hiện trong danh sách chọn và bị service từ chối nếu forge team ID.
- Request owner bị loại khỏi coordinator/team notification recipients của chính request đó.
- Admin không thể cập nhật role hoặc status của chính mình; active admin cuối cùng được bảo vệ bằng pessimistic lock.
- `status_histories.actor_role` là bắt buộc cho dữ liệu mới; dữ liệu trước V9 được backfill `SYSTEM`.
- Các actor role được ghi theo transition: `ROLE_CITIZEN`, `ROLE_COORDINATOR`, `ROLE_RESCUER`.
- Checkpoint 09.2 không triển khai profile/avatar/statistics; các phần đó thuộc checkpoint 09.3.

## 20. Checkpoint 09.3A as-built

- Hồ sơ cá nhân chỉ cho sửa họ tên, số điện thoại và avatar; email, display code, status và role là read-only.
- Avatar dùng Cloudinary riêng theo user, có validation, replace/delete và cleanup theo contract media hiện có.
- Statistics lấy theo active role và chỉ dùng dữ liệu audit/request/assignment thật của user hiện tại.
- Navbar hiển thị active role bằng badge trạng thái không click; đổi role chỉ đi qua nút **Thay đổi vai trò** trong hồ sơ.

## 21. Checkpoint 09.3B — Rescue team membership as-built

- Một người cứu hộ có thể tham gia nhiều đội; trong cùng một đội chỉ có một membership đang hoạt động cho mỗi user.
- `ROLE_RESCUER` là quyền hệ thống; `LEADER` và `MEMBER` là chức vụ membership trong một đội cụ thể.
- Form tạo/sửa đội chọn đúng một đội trưởng và nhiều thành viên từ các user `ACTIVE` có grant `ROLE_RESCUER` chưa revoke.
- Đội trưởng không thể đồng thời nằm trong danh sách thành viên của cùng đội; backend kiểm tra lại toàn bộ ID, không tin dữ liệu client.
- Nút xác nhận đội trưởng/thành viên chỉ cập nhật bản nháp trên trình duyệt; chỉ submit cuối mới lưu team và membership trong cùng transaction.
- `rescue_teams.leader_user_id` là nguồn liên kết đội trưởng; `leader_name` được đồng bộ để giữ tương thích dữ liệu/report cũ.
- Migration V11 backfill đội trưởng từ membership `LEADER` hiện có. Team legacy không có leader relation không được dùng để phân công cho đến khi admin sửa.
- Assignment, rescuer task access, notification recipient và WebSocket team subscription chỉ chấp nhận membership còn hiệu lực của user `ACTIVE` có `ROLE_RESCUER` chưa revoke.
- Admin không thể thu hồi `ROLE_RESCUER` hoặc vô hiệu hóa tài khoản khi user còn membership chưa rời đội.
- Admin user detail chỉ hiển thị khu vực đội cứu hộ khi user có `ROLE_RESCUER`; có empty state nếu chưa tham gia đội.
- Rescuer có `Đội của tôi`; trang chi tiết chỉ lộ avatar, display code, họ tên và chức vụ team của các thành viên, không lộ email/điện thoại và không link sang profile người khác.
- Danh sách nhiệm vụ tiếp tục hiển thị team phụ trách để user đa đội biết nhiệm vụ thuộc đội nào.