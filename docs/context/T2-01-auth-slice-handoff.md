# T2-01 — Authentication Vertical Slice Handoff

- **Updated:** 2026-10-20
- **Task:** T2-01
- **GitHub issue:** #4
- **Branch:** `feat/t2-01-auth-slice`
- **Baseline commit:** `4922092`
- **Implementation commit verified:** `1001ceb`
- **Artifact/Trace ID:** `AUTH-SLICE-01`, `SD-05`
- **Status:** Ready for Pull Request review

## 1. Phạm vi đã hoàn thành

T2-01 đã triển khai một vertical slice xác thực hoàn chỉnh gồm:

- Đăng ký tài khoản.
- Gửi và xác minh OTP kích hoạt qua Gmail.
- Gửi lại OTP có cooldown và rate limit.
- Đăng nhập bằng email và mật khẩu.
- Phát hành JWT trong HttpOnly cookie.
- Xác thực request từ JWT cookie.
- CSRF protection cho request thay đổi dữ liệu.
- RBAC phía server theo nhiều role.
- Đăng xuất bằng `POST`.
- Quên và đặt lại mật khẩu qua ba màn hình riêng biệt.
- Thu hồi JWT cũ sau khi đổi mật khẩu bằng `users.auth_version`.
- Persistence cho OTP và rate limit.
- Giao diện Thymeleaf, validation và error state.
- Automated test và MySQL integration test.

## 2. Contract và quyết định kiến trúc

Nguồn quyết định chính:

- `docs/context/T2-01-auth-security-contract.md`
- Issue #4
- `SD-05`
- Các đặc tả use case xác thực đã được nhóm thống nhất.

Các quyết định quan trọng:

- OTP gồm 6 chữ số, được sinh bằng `SecureRandom`.
- Database chỉ lưu HMAC-SHA256 digest của OTP.
- OTP kích hoạt và OTP reset mật khẩu có purpose riêng.
- OTP có thời hạn, giới hạn số lần sai và không được dùng lại.
- OTP mới làm token cũ cùng user và purpose mất hiệu lực.
- Gmail lỗi không rollback tài khoản đã tạo.
- JWT sử dụng HS256 và có thời hạn 30 phút.
- JWT được lưu trong cookie `DRSS_ACCESS_TOKEN`.
- Cookie có `HttpOnly`, `SameSite=Lax` và không được đưa vào URL hoặc HTML.
- Logout sử dụng `POST`, yêu cầu CSRF và xóa JWT cookie.
- Không lưu JWT trong `localStorage` hoặc `sessionStorage`.
- Reset mật khẩu sử dụng server-side `HttpSession` grant.
- Reset thành công tăng `users.auth_version`, khiến JWT cũ bị từ chối.
- Quyền được kiểm tra phía server; việc ẩn nút trên UI không thay thế RBAC.
- Email và IP dùng cho rate limit được chuyển thành HMAC subject key.
- Secret và thông tin đăng nhập chỉ được cung cấp qua biến môi trường.

## 3. Persistence

Flyway migration được bổ sung:

- `V3__Add_Authentication_Support.sql`

Migration bổ sung:

- `users.auth_version`
- Bảng `verification_tokens`
- Bảng `auth_rate_limits`
- Index phục vụ tra cứu token và rate limit
- CHECK constraint cho purpose, thời gian và số lần thử
- Optimistic-lock version cho token và rate-limit record

Flyway đã được kiểm tra theo thứ tự:

```text
V1 → V2 → V3
```

Hibernate `ddl-auto=validate` đã PASS trên MySQL.

## 4. Đăng ký và kích hoạt tài khoản

Luồng thực hiện:

1. Người dùng nhập thông tin đăng ký.
2. Server chuẩn hóa và kiểm tra dữ liệu.
3. Tài khoản mới được lưu với trạng thái `INACTIVE`.
4. Tài khoản được gán `ROLE_CITIZEN`.
5. Server tạo OTP `ACCOUNT_ACTIVATION`.
6. OTP digest được lưu trước khi gọi Gmail.
7. OTP rõ chỉ được truyền tới dịch vụ gửi email và không ghi log.
8. Người dùng xác minh OTP.
9. Tài khoản chuyển thành `ACTIVE`.
10. OTP được consume trong transaction.
11. Người dùng được chuyển về đăng nhập và không tự động đăng nhập.

Phản hồi đăng ký không làm lộ việc email đã tồn tại hay chưa.

## 5. Đăng nhập, JWT và RBAC

Đăng nhập thực hiện:

- Chuẩn hóa email.
- Kiểm tra rate limit.
- So khớp mật khẩu bằng BCrypt.
- Kiểm tra trạng thái tài khoản.
- Đọc role hiện hành từ database.
- Phát hành JWT.
- Ghi JWT vào HttpOnly cookie.
- Điều hướng theo role.

JWT chứa tối thiểu:

- `sub`
- `roles`
- `iat`
- `exp`
- `jti`
- `iss`
- `ver`

Mỗi request đã xác thực kiểm tra lại:

- Chữ ký và thời hạn JWT.
- Issuer.
- User còn tồn tại.
- User vẫn `ACTIVE`.
- `auth_version` trong JWT còn khớp database.
- Role hiện hành chưa bị thu hồi.

Không chỉ tin vào role đã ghi trong JWT.

## 6. CSRF, HttpSession và logout

Authentication hoạt động stateless theo hành vi:

- `SecurityContext` không được lưu trong `HttpSession`.
- Authentication được dựng lại từ JWT trên mỗi request.
- Chuỗi bảo mật sử dụng `NullSecurityContextRepository`.
- `SessionManagementConfigurer` bị tắt trong application security chain.
- `SessionManagementFilter` không được cài vào application security chain.

Việc tắt Spring Security session management không tắt `HttpSession`.

`HttpSession` vẫn được sử dụng độc lập cho:

- CSRF token.
- Password-reset grant.

Cách cấu hình này ngăn JWT authentication bị hiểu nhầm là một lần đăng nhập mới trên mỗi request, vốn từng làm thay session ID và CSRF token sau khi trang đã bắt đầu render.

Logout:

- Sử dụng `POST /auth/logout`.
- Bắt buộc CSRF token hợp lệ.
- Xóa `DRSS_ACCESS_TOKEN`.
- Cookie xóa có giá trị rỗng và `Max-Age=0`.
- Chuyển về `/auth/login?logout`.
- Không sử dụng logout bằng `GET`.

## 7. Quên và đặt lại mật khẩu

Luồng được chia thành ba trang:

1. Nhập email.
2. Xác minh OTP.
3. Nhập mật khẩu mới và xác nhận mật khẩu.

Quyền reset được lưu phía server trong `HttpSession`.

Sau khi OTP reset hợp lệ:

- Session giữ UUID của verification token đã xác minh.
- Không lưu OTP rõ, digest, mật khẩu hoặc JWT trong session.
- Grant chỉ có hiệu lực 10 phút.
- Mỗi lần mở hoặc submit trang mật khẩu mới đều kiểm tra lại token trong database.
- Session khác không thể sử dụng grant đã được xác minh ở session hiện tại.

Reset thành công thực hiện trong cùng transaction:

- Cập nhật BCrypt password hash.
- Tăng `users.auth_version`.
- Consume reset token.
- Xóa reset state khỏi session.

Hệ thống không tự động đăng nhập sau reset.

## 8. Rate limiting

Rate limit được triển khai cho:

- Đăng ký.
- Gửi lại OTP.
- Xác minh OTP kích hoạt.
- Đăng nhập.
- Quên mật khẩu.
- Xác minh OTP reset.

Các đặc tính đã triển khai:

- Giới hạn theo email + IP.
- Giới hạn riêng theo IP.
- Cooldown gửi lại OTP.
- Hình phạt tăng dần sau khi vượt ngưỡng.
- Request trong thời gian khóa không kéo dài thời gian khóa.
- Lần sai đầu tiên sau khi hết khóa tăng penalty level.
- Thời gian khóa tối đa 24 giờ.
- Thành công chỉ xóa đúng bộ đếm liên quan.
- Sau 24 giờ không có thất bại mới, penalty trở về mức ban đầu.
- Email và IP không được lưu rõ trong subject key.

## 9. Cấu hình và secret

Các biến môi trường liên quan gồm:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
OTP_HMAC_SECRET
RATE_LIMIT_HMAC_SECRET
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM
APP_BASE_URL
```

Quy tắc:

- Ba security secret phải đủ độ dài và khác nhau.
- Không commit `scripts/local-env.ps1`.
- Chỉ commit `scripts/local-env.ps1.example`.
- Không ghi secret, OTP rõ, mật khẩu rõ hoặc JWT vào log.
- Hướng dẫn local nằm tại `docs/guidelines/local_auth_configuration.md`.

Spring Boot không tự động đọc `.env`; biến phải được nạp vào PowerShell process trước khi chạy ứng dụng.

## 10. Automated verification

Các lệnh kiểm tra đã sử dụng:

```powershell
.\mvnw.cmd clean test
```

```powershell
. .\scripts\local-env.ps1

.\mvnw.cmd clean verify `
    "-Dspring.profiles.active=it"
```

Kết quả:

- Unit and MVC tests: PASS.
- Authentication service tests: PASS.
- CSRF and RBAC tests: PASS.
- JWT validation tests: PASS.
- OTP lifecycle tests: PASS.
- Rate-limit tests: PASS.
- Password-reset tests: PASS.
- Flyway V1 → V2 → V3: PASS.
- Hibernate schema validation: PASS.
- MySQL integration tests: PASS.
- Full authentication-flow integration test: PASS.

GitHub Actions:

- Workflow: `CI Workflow #54`
- Verified commit: `1001ceb`
- Result: PASS
- Duration: approximately 1 minute 29 seconds

## 11. Manual browser verification

### Đăng ký và kích hoạt

- Empty registration blocked without POST: PASS.
- Valid registration POST and redirect: PASS.
- Generic registration response: PASS.
- Gmail activation OTP received: PASS.
- Wrong activation OTP returns generic response: PASS.
- Correct activation OTP redirects to login: PASS.
- No automatic login after activation: PASS.

### Đăng nhập và đăng xuất

- Valid login and redirect: PASS.
- Authenticated home rendered: PASS.
- Logout uses `POST /auth/logout`: PASS.
- Logout response status `302`: PASS.
- Logout redirects to `/auth/login?logout`: PASS.
- Logout success alert rendered: PASS.
- JWT cookie deleted with empty value and `Max-Age=0`: PASS.
- Logout no longer returns CSRF `403`: PASS.

### Quên và đặt lại mật khẩu

- Forgot-password generic response: PASS.
- Gmail reset OTP received: PASS.
- Wrong reset OTP returns generic error: PASS.
- Correct reset OTP opens new-password page: PASS.
- Password mismatch rejected: PASS.
- Successful reset redirects to login: PASS.
- No automatic login after reset: PASS.
- JWT issued before reset rejected afterward: PASS.
- Old password rejected: PASS.
- New password login successful: PASS.
- Logout after new login: PASS.

## 12. Regression được phát hiện và xử lý

Manual browser review từng phát hiện logout trả `403` dù form có `_csrf`, JWT cookie và `JSESSIONID`.

Nguyên nhân:

- JWT filter dựng lại Authentication trên mỗi request.
- `SessionManagementFilter` hiểu đây là một lần authentication mới.
- Session ID và CSRF token bị thay sau khi request đã bắt đầu render.
- Form logout chứa token cũ nên bị `CsrfFilter` từ chối.

Cách xử lý:

- Không lưu `SecurityContext` trong session.
- Tắt `SessionManagementConfigurer` trên application security chain.
- Giữ CSRF và `HttpSession` cho đúng mục đích riêng.
- Thêm regression test xác nhận application chain không chứa `SessionManagementFilter`.
- Kiểm tra lại bằng automated test và trình duyệt thực tế.

## 13. Giới hạn đã biết

Ngoài phạm vi T2-01:

- Refresh token.
- JWT denylist theo `jti`.
- Social login hoặc OAuth login.
- MFA sau đăng nhập.
- Quản lý thiết bị và danh sách phiên.
- Redis/distributed rate limiting.
- CAPTCHA.
- SMTP provider khác Gmail.
- Session store dùng chung cho triển khai nhiều instance.
- API riêng cho mobile.

Logout xóa cookie trên trình duyệt nhưng không thể thu hồi một bản JWT đã bị sao chép trước đó.

Rủi ro này được giảm bằng:

- Access token TTL 30 phút.
- Kiểm tra trạng thái user mỗi request.
- Kiểm tra role hiện hành mỗi request.
- Kiểm tra `users.auth_version` mỗi request.
- Reset mật khẩu tăng `auth_version`.

Denylist theo `jti` là hướng nâng cấp nếu hệ thống cần thu hồi riêng từng token ngay lập tức.

## 14. Hướng dẫn reviewer

### Chuẩn bị

1. Sử dụng Java 17.
2. Chuẩn bị MySQL 8.
3. Sao chép `scripts/local-env.ps1.example` thành file local bị Git ignore `scripts/local-env.ps1`.
4. Điền biến môi trường bằng giá trị local, không đưa secret vào PR.
5. Nạp cấu hình:

```powershell
. .\scripts\local-env.ps1
```

### Chạy automated tests

```powershell
.\mvnw.cmd clean test
```

```powershell
.\mvnw.cmd clean verify `
    "-Dspring.profiles.active=it"
```

### Chạy ứng dụng

```powershell
.\mvnw.cmd spring-boot:run `
    "-Dspring-boot.run.profiles=dev"
```

Các route chính:

```text
/auth/register
/auth/verify-otp
/auth/login
/auth/forgot-password
/auth/reset-password/verify
/auth/reset-password/new
```

### Trọng tâm review

Vy review:

- Giao diện auth.
- Responsive.
- Validation và error state.
- Luồng đăng ký, kích hoạt, đăng nhập và reset mật khẩu.
- Trải nghiệm điều hướng và thông báo.

Duy review:

- Migration V3.
- Entity và repository.
- Transaction boundary.
- OTP lifecycle.
- Rate limiting.
- JWT, CSRF và RBAC.
- Negative/security tests.
- Không rò rỉ secret hoặc dữ liệu nhạy cảm.

Chỉ merge khi:

- Vy Approved.
- Duy Approved.
- GitHub Actions PASS trên PR head.
- PR liên kết issue bằng `Closes #4`.

## 15. Kết luận bàn giao

T2-01 đã hoàn thành phạm vi xác thực được thống nhất:

- Auth happy path và negative path đã PASS.
- Persistence và MySQL integration đã PASS.
- JWT HttpOnly cookie, CSRF và RBAC đã được kiểm tra phía server.
- Registration enumeration đã được xử lý.
- Password reset thu hồi JWT cũ bằng `auth_version`.
- Logout CSRF regression đã được sửa và kiểm tra thực tế.
- Không có secret, plain password, OTP rõ hoặc JWT được commit.
- Task sẵn sàng cho review của Vy và Duy.
