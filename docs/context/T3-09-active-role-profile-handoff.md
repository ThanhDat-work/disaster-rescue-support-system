# T3-09 Active Role and User Profile Handoff

- **Status:** Ready for final verification and review
- **Task:** T3-09
- **Owner:** Đỗ Thành Đạt
- **Reviewers:** Bùi Đức Duy, Phan Nhật Vy
- **Implementation condition:** Bắt đầu sau khi T3-03 được review, merge và `main` sạch
- **Base commit:** `05cc04b`
- **Latest code commit:** `74ca73a`
- **Branch:** `feat/t3-09-profile-active-role`
- **Updated:** 2026-10-22

## 1. Kết quả thực hiện

T3-09 triển khai một active role cho mỗi phiên đăng nhập, hồ sơ cá nhân/avatar/thống kê theo role, và các ràng buộc bảo vệ khỏi tự xử lý request, tự tăng quyền hoặc thao tác sai phạm vi role.

Theo contract hiện tại, checkpoint 09.2, 09.3A và 09.3B có ghi nhận as-built. Chưa có thông tin as-built hoặc kết quả kiểm chứng cho checkpoint 09.1 và checkpoint 09.4 trong tài liệu nguồn. Cần đối chiếu implementation thực tế trước khi xác nhận toàn bộ task hoàn tất.

Contract nguồn: [T3-09-active-role-profile-contract.md](./T3-09-active-role-profile-contract.md).

## 2. Các commit của task

| Commit    | Nội dung                                                   |
| --------- | ---------------------------------------------------------- |
| `f771221` | `docs: lock T3-09 active role profile contract`            |
| `a4e73e4` | `feat: implement active-role authentication and switching` |
| `4742852` | `fix: prevent self-handling across active roles`           |
| `3d76832` | `feat: add active-role profile and avatar statistics`      |
| `74ca73a` | `feat: add rescue team membership and directory`           |

## 3. Active role, đăng nhập và role switch

### Contract yêu cầu

- Một user có thể có nhiều role grant, nhưng mỗi JWT chỉ mang một active role.
- Đăng nhập mới ưu tiên `CITIZEN`; nếu không có `CITIZEN` thì tự chọn role duy nhất hoặc yêu cầu chọn role ban đầu khi có nhiều role.
- JWT authorities chỉ chứa active role; các route nghiệp vụ phải kiểm tra active authority.
- Chuyển role yêu cầu mật khẩu hiện tại, CSRF, target role còn được cấp và account `ACTIVE`; thành công phát hành JWT mới qua HttpOnly cookie.
- Không chấp nhận redirect URL tùy ý; role switch không làm mất CSRF session hợp lệ.
- Cấp/revoke role và đổi account status phải tăng `auth_version`; role switch không đổi grants nên không tăng version.

### Kết quả implementation

- **Checkpoint 09.1 as-built:** Thực hiện thành công việc chuyển role hiện hành của 1 tài khoản người dùng và ngăn cách truy cập các mục giữa các role trên cùng 1 tài khoản
- **Checkpoint 09.2 as-built:** Xử lý cách trường hợp conflict khi 1 người dùng có nhiều tải khoản
- **Checkpoint 09.3A as-built:** Thiết kế trang hồ sơ cá nhân, avatar, chỉnh sửa hồ sơ và thống kê cơ bản cho từng loại role trong cùng trang `/profile`
- **Checkpoint 09.3B as-built:** Bổ sung/thiết kế các logic về đội cứu hộ bao gồm: tạo/sửa đội cứu hộ tại trang admin, các danh sách đội và danh sách thành viên trong đội tại mục Đội của tôi khi truy cập bằng role cứu hộ

## 4. Hồ sơ cá nhân, avatar và navbar

- Profile route theo contract: `GET /account/profile`, `GET /account/profile/edit`, `POST /account/profile`, `POST /account/profile/avatar`; xóa avatar tùy chọn qua `POST /account/profile/avatar/delete`.
- Chỉ cho sửa họ tên, số điện thoại và avatar. Email, display code, status, role grants, active role, auth version và ngày tạo chỉ đọc.
- Avatar phải là ảnh hợp lệ, tối đa 2 MiB; cleanup khi thay/xóa và compensation khi transaction thất bại phải theo media foundation T2-05.
- Navbar hiển thị active role dưới dạng badge không tương tác; chuyển role chỉ bắt đầu từ nút **Thay đổi vai trò** trên trang hồ sơ.
- Profile và navbar cơ bản được contract ghi nhận là as-built trong checkpoint 09.3A.

## 5. Conflict-of-interest và admin self-protection

Theo contract, request owner không được nhìn thấy hoặc thao tác request của mình qua coordinator/rescuer; team có request owner là active member không được nhận assignment; request owner không được nhận notification liên quan đến request của mình.

Checkpoint 09.2 as-built được contract ghi nhận:

- Coordinator queue, tracking, detail và command loại request có owner trùng current user ngay tại repository query.
- Rescuer list, detail, start và complete loại assignment có request owner trùng current user.
- Team có request owner là active member bị loại khỏi lựa chọn và service từ chối forged team ID.
- Request owner bị loại khỏi coordinator/team notification recipients của request đó.
- Admin không thể cập nhật role hoặc status của chính mình; active admin cuối cùng được bảo vệ bằng pessimistic lock.

## 6. Audit

- `status_histories.actor_role` bắt buộc với dữ liệu mới; dữ liệu trước V9 được backfill `SYSTEM`.
- Actor role được ghi theo transition: `ROLE_CITIZEN`, `ROLE_COORDINATOR`, `ROLE_RESCUER`.
- Checkpoint 09.2 as-built ghi nhận các nội dung trên.
- Role switch audit theo contract cần tối thiểu user ID, from/to role, server timestamp, kết quả và source IP đã chuẩn hóa nếu chính sách cho phép; không lưu password/JWT/cookie/raw secret.

## 7. Rescue team membership — checkpoint 09.3B

Contract ghi nhận các nội dung as-built sau:

- User có thể tham gia nhiều đội; mỗi user chỉ có một membership đang hoạt động trong cùng một đội.
- `ROLE_RESCUER` là system role; `LEADER`/`MEMBER` là chức vụ trong team.
- Team form chọn một leader và nhiều member từ user `ACTIVE` có `ROLE_RESCUER` chưa revoke; backend xác thực lại toàn bộ ID.
- Thay đổi leader/member được lưu cùng team trong transaction cuối; leader không đồng thời là member của chính đội đó.
- `rescue_teams.leader_user_id` là liên kết chính; `leader_name` được đồng bộ để tương thích dữ liệu/report cũ.
- Migration V11 backfill leader từ membership `LEADER`; team legacy chưa có leader relation không được dùng để phân công cho tới khi admin sửa.
- Assignment, task access, notification recipient và WebSocket subscription chỉ chấp nhận membership còn hiệu lực của user `ACTIVE` có `ROLE_RESCUER` chưa revoke.
- Admin không thể revoke `ROLE_RESCUER` hoặc vô hiệu hóa user còn membership chưa rời đội.
- Rescuer có trang **Đội của tôi**; chi tiết team chỉ hiển thị avatar, display code, họ tên và chức vụ, không lộ email/điện thoại.
- Danh sách nhiệm vụ hiển thị team phụ trách.

## 8. Thống kê theo active role

Checkpoint 09.3A ghi nhận statistics được lấy theo active role và chỉ dùng dữ liệu audit/request/assignment thật của user hiện tại.

Contract yêu cầu:

- Citizen: request cá nhân theo trạng thái và theo thời gian.
- Coordinator: action do actor thực hiện, dựa trên audit/history.
- Rescuer: nhiệm vụ của đội; không gán thành tích cá nhân nếu schema không lưu người thực hiện.
- Admin: chỉ hiển thị activity có audit chứng minh actor.
- Query aggregate tại database, giới hạn thời gian, tránh N+1; có empty state và không dựng dữ liệu không tồn tại.

## 9. Automated verification

Contract yêu cầu full unit tests, MySQL integration verify và regression cho auth, citizen, coordinator, rescuer, admin, media, notification và T3-03.

## 10. Manual verification

Contract yêu cầu kiểm thử các flow sau. Điền kết quả thực tế trước khi handoff review:

1. Multi-role login mặc định `CITIZEN`: PASS
2. Profile hiển thị active role và các role chưa revoke: PASS
3. Nút **Thay đổi vai trò** mở trang riêng: PASS
4. Sai mật khẩu không đổi role: PASS
5. Đúng mật khẩu đổi role và redirect đúng: PASS
6. Tab cũ không thể tiếp tục thao tác bằng role trước: PASS
7. Coordinator/rescuer không nhìn thấy request do chính user tạo: PASS
8. Team chứa request owner không thể được assign: PASS
9. Admin không tự thêm role hoặc vô hiệu hóa mình: PASS
10. Profile update và avatar lifecycle: PASS
11. Role badge và statistics thay đổi theo active role: PASS
12. Desktop/mobile không có lỗi UI P0: PASS

## 11. Review focus

1. Mỗi JWT chỉ mang một active role; không có đường nghiệp vụ nào dùng toàn bộ grants để bypass route isolation.
2. Switch role xác thực lại password, active grant, account status, CSRF và rate limit; cookie mới không làm mất CSRF session.
3. Các truy vấn list/detail/command và realtime đều loại request owner; service vẫn chặn forged IDs.
4. Kiểm tra team-owner conflict nằm cùng transaction/lock với assignment creation.
5. Admin self-protection và last-active-admin guard được thực thi ở service/backend, không chỉ ở UI.
6. Audit lưu actor role tại thời điểm transition và role switch audit không chứa secret.
7. Avatar replace/delete/rollback dùng cleanup/retry bền vững theo T2-05.
8. Statistics phản ánh đúng user/team, có nguồn dữ liệu thật, giới hạn thời gian và không N+1.
9. Migration V8/V9/V11 và dữ liệu legacy được kiểm thử, không làm mất quyền hợp lệ hoặc tạo đường truy cập sai.

## 12. Deferred / ngoài phạm vi

- Không lưu active role riêng theo browser tab.
- Không cho user tự đăng ký role nghiệp vụ.
- Không cho sửa email trong T3-09.
- Không thêm social login hoặc MFA nếu chưa có task riêng.
- Không dựng thống kê không có audit/data thật.
- UI polish nâng cao thuộc checkpoint UI của Vy sau khi backend và contract dữ liệu ổn định.

## 13. Environment và shared data

Không ghi password, JWT, cookie, Cloudinary secret hoặc credential vào handoff, source, PR hay test log.

## 14. Checkpoint và trạng thái handoff

| Checkpoint                                   | Trạng thái theo contract                            | Thông tin cần xác nhận                                            |
| -------------------------------------------- | --------------------------------------------------- | ----------------------------------------------------------------- |
| 09.1 — Context và active-role authentication | Chưa có as-built trong phần checkpoint của contract | Implementation, migration/auth tests, role switch audit           |
| 09.2 — Conflict-of-interest hardening        | Có ghi nhận as-built                                | Kết quả test/CI và ngoại lệ nếu có                                |
| 09.3A — Profile, avatar và statistics        | Có ghi nhận as-built                                | Test, manual UI và chi tiết các statistics                        |
| 09.3B — Rescue team membership               | Có ghi nhận as-built                                | Test/migration/manual verification                                |
| 09.4 — Full verification và handoff          | Chưa có kết quả được ghi nhận                       | Full unit/MySQL integration, manual flow, audit, report trace, CI |

## 15. Kết luận

Contract ghi nhận implementation as-built cho conflict-of-interest/admin safety (09.2), profile/avatar/statistics và navbar (09.3A), cùng rescue team membership (09.3B). Chưa đủ dữ liệu trong contract để xác nhận active-role authentication checkpoint 09.1, full verification checkpoint 09.4, commit/branch hoặc trạng thái CI.
