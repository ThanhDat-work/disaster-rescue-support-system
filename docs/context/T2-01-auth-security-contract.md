# T2-01 — Authentication and Security Contract

- **Status:** Approved
- **Approved date:** 2026-10-18
- **Last clarified:** 2026-10-19 — Password reset grant sử dụng `HttpSession`; rate-limit subject key sử dụng secret HMAC riêng; xác thực thành công chỉ xóa bộ đếm subject riêng, không xóa IP-wide; login T2-01 redirect về `/`; resend OTP hỗ trợ activation và password reset.
- **Task:** T2-01
- **GitHub issue:** #4
- **Baseline commit:** `4922092`
- **Implementation branch:** `feat/t2-01-auth-slice`
- **Artifact/Trace ID:** `AUTH-SLICE-01`, `SD-05`

## 1. Mục đích

Tài liệu này khóa các quyết định triển khai cho vertical slice xác thực của DRSS, bao gồm:

- Đăng ký tài khoản công dân.
- Kích hoạt tài khoản bằng OTP.
- Gửi lại OTP.
- Đăng nhập và đăng xuất.
- JWT trong HttpOnly cookie.
- Phân quyền RBAC phía server.
- Quên và đặt lại mật khẩu.
- CSRF, chống dò tìm tài khoản và rate limiting.
- Cấu hình Gmail, JWT và OTP bằng biến môi trường.
- Phạm vi migration và kiểm thử của T2-01.

Nếu code, giao diện hoặc tài liệu bàn giao của T2-01 khác với tài liệu này thì phải sửa code hoặc cập nhật quyết định thông qua review. Không tự thay đổi ngầm.

## 2. Nguồn yêu cầu

T2-01 triển khai theo:

- Issue `#4`.
- Bảng yêu cầu hiện hành của nhóm.
- Đặc tả Use Case đăng ký, đăng nhập, đăng xuất và quên mật khẩu.
- Sequence Diagram `SD-05`.
- Kiến trúc và quy ước đã khóa trong T1-06.
- Data Foundation và database contract của T1-07.
- UI components và layout foundation của T1-08.

Tài liệu này làm rõ chi tiết triển khai nhưng không thay đổi mục tiêu nghiệp vụ đã được nhóm thống nhất.

## 3. Nguyên tắc kiến trúc

- Controller chỉ tiếp nhận request, validation DTO, gọi service và chọn view/redirect.
- Controller không truy cập Repository trực tiếp.
- Service sở hữu nghiệp vụ, transaction và các kiểm tra bảo mật.
- Repository chỉ chịu trách nhiệm truy cập dữ liệu.
- Package `security` sở hữu JWT, cookie, authentication và authorization.
- Entity không được sử dụng trực tiếp làm request hoặc response.
- Mật khẩu, OTP, JWT và secret không được ghi vào log.
- Không sửa `V1__Init_Schema.sql` hoặc `V2__Insert_Seed_Data.sql`.
- Mọi thay đổi database của T2-01 phải nằm trong migration `V3`.
- Các trường persistence dùng `LocalDateTime` phải lấy thời gian theo cùng múi giờ JVM với Hibernate `@CreationTimestamp`.
- JWT tiếp tục dùng `Instant`; thay đổi múi giờ của bean `Clock` không thay đổi thời điểm tuyệt đối của `iat` hoặc `exp`.

## 4. Đăng ký tài khoản

### 4.1 Dữ liệu đầu vào

Form đăng ký gồm:

- `fullName`
- `email`
- `phoneNumber`
- `password`
- `confirmPassword`

`confirmPassword` chỉ tồn tại trong DTO và giao diện, không lưu vào database.

### 4.2 Chuẩn hóa dữ liệu

- Email được trim và chuyển thành chữ thường.
- Số điện thoại được trim.
- Số điện thoại phải chứa từ 10 đến 15 chữ số.
- `displayCode` do server sinh theo dạng `USR-` cộng 12 ký tự hexadecimal ngẫu nhiên.
- Client không được tự gửi `displayCode`, trạng thái hoặc role.

### 4.3 Quy tắc tạo tài khoản

- Tài khoản mới có trạng thái `INACTIVE`.
- Tài khoản mới được gán `ROLE_CITIZEN`.
- Việc tạo user và gán role phải nằm trong cùng transaction.
- Mật khẩu chỉ được lưu dưới dạng BCrypt hash.
- Sau khi lưu user và OTP thành công, hệ thống mới thực hiện gửi email.

### 4.4 Email đã tồn tại

- Nếu email thuộc tài khoản `ACTIVE` hoặc `BANNED`, không thay đổi dữ liệu tài khoản.
- Phản hồi bên ngoài vẫn sử dụng thông báo chung, không xác nhận email đã tồn tại.
- Nếu email thuộc tài khoản `INACTIVE`, hệ thống được phép cập nhật lại thông tin chưa xác minh, cập nhật password hash và tạo OTP mới nếu không vi phạm rate limit.
- Không tạo user trùng email.

## 5. Chính sách mật khẩu

Mật khẩu phải:

- Dài từ 12 đến 64 ký tự.
- Có ít nhất một chữ cái.
- Có ít nhất một chữ số.
- Được phép chứa khoảng trắng và ký tự đặc biệt.
- Khớp với `confirmPassword`.

Mật khẩu được mã hóa bằng:

- `BCryptPasswordEncoder`
- Strength/cost: `10`

Không được:

- Lưu mật khẩu rõ.
- Ghi mật khẩu vào log.
- Đưa mật khẩu vào URL.
- Trả password hash ra view hoặc response.

## 6. OTP

### 6.1 Định dạng và bảo vệ

- OTP gồm 6 chữ số.
- OTP được sinh bằng `SecureRandom`.
- OTP có hiệu lực trong 5 phút.
- Database không lưu OTP rõ.
- OTP được lưu dưới dạng HMAC-SHA256 bằng `OTP_HMAC_SECRET`.
- Secret dùng cho OTP phải khác secret dùng ký JWT.

### 6.2 Purpose

OTP có hai purpose:

- `ACCOUNT_ACTIVATION`
- `PASSWORD_RESET`

OTP của purpose này không được sử dụng cho purpose khác.

### 6.3 Vòng đời OTP

- OTP mới làm OTP cũ cùng user và purpose mất hiệu lực.
- OTP đã dùng không được dùng lại.
- OTP hết hạn không được dùng lại.
- Mỗi OTP được nhập sai tối đa 5 lần.
- Lần sai thứ 5 làm OTP đó mất hiệu lực.
- Muốn thử tiếp phải xin OTP mới và vẫn phải tuân thủ cooldown/rate limit.
- Không tự động chuyển tài khoản thành `BANNED` vì nhập sai OTP.

### 6.4 Gửi email thất bại

- User và token được lưu trước khi gọi Gmail.
- Gmail lỗi không rollback user đã tạo.
- Tài khoản vẫn giữ trạng thái `INACTIVE`.
- Người dùng có thể yêu cầu gửi lại OTP sau cooldown.
- Không ghi OTP hoặc Gmail App Password vào log lỗi.
- Phản hồi bên ngoài phải giống trường hợp đăng ký được tiếp nhận bình thường; không hiển thị trạng thái gửi Gmail để tránh bị lợi dụng dò tìm tài khoản.

## 7. Kích hoạt tài khoản

Luồng kích hoạt:

1. Người dùng đăng ký.
2. Server tạo tài khoản `INACTIVE`.
3. Server tạo OTP `ACCOUNT_ACTIVATION`.
4. Server gửi OTP qua Gmail.
5. Người dùng nhập email và OTP.
6. Server xác minh purpose, thời hạn, số lần sai và trạng thái token.
7. Nếu hợp lệ, server chuyển tài khoản thành `ACTIVE`.
8. Token được đánh dấu đã sử dụng trong cùng transaction.
9. Người dùng được chuyển tới trang đăng nhập.

Không tự động đăng nhập ngay sau khi kích hoạt.

## 8. Đăng nhập

Form đăng nhập gồm:

- `email`
- `password`

Quy trình:

1. Chuẩn hóa email.
2. Kiểm tra rate limit.
3. Tìm user.
4. So khớp mật khẩu bằng BCrypt.
5. Kiểm tra trạng thái user.
6. Lấy các role chưa bị thu hồi.
7. Phát hành JWT.
8. Ghi JWT vào HttpOnly cookie.
9. Điều hướng về trang chủ `/` theo mục 8.2.

### 8.1 Phản hồi đăng nhập

- Email không tồn tại và mật khẩu sai dùng chung một thông báo.
- Chỉ sau khi mật khẩu đúng mới được thông báo tài khoản chưa kích hoạt hoặc đã bị vô hiệu hóa.
- Không trả lời rằng một email cụ thể có tồn tại hay không.
- Không ghi nguyên nhân chi tiết nhạy cảm vào response phía client.

### 8.2 Điều hướng sau đăng nhập

- Trong T2-01, đăng nhập thành công điều hướng về trang chủ công khai `/`.
- Controller giữ quyết định redirect trong một method riêng; service đăng nhập không phụ thuộc route giao diện.
- Khi các màn hình nghiệp vụ theo role hoàn thiện, nhóm có thể bổ sung saved-request để quay lại route người dùng vừa truy cập hoặc chọn landing page theo role.
- T2-01 chưa bật saved-request; `NullRequestCache` tiếp tục được sử dụng để tránh thay đổi ngầm trạng thái session.

## 9. JWT

### 9.1 Thuật toán và thời hạn

- Thuật toán: `HS256`
- Access token TTL: 30 phút
- Issuer: `drss`
- T2-01 không triển khai refresh token.

JWT chứa tối thiểu:

- `sub`: UUID của user
- `roles`: danh sách role tại thời điểm phát hành
- `iat`: thời điểm phát hành
- `exp`: thời điểm hết hạn
- `jti`: định danh duy nhất của token
- `iss`: `drss`
- `ver`: `users.auth_version`

### 9.2 Cookie

Tên cookie:

- `DRSS_ACCESS_TOKEN`

Thuộc tính:

- `HttpOnly=true`
- `SameSite=Lax`
- `Path=/`
- `Max-Age=1800`
- `Secure=false` trong local/dev
- `Secure=true` trong production
- Không cấu hình `Domain` nếu chưa có yêu cầu triển khai đa subdomain.

Token không được:

- Lưu trong `localStorage`.
- Lưu trong `sessionStorage`.
- Đưa vào URL.
- Hiển thị trong HTML.
- Ghi vào log.

### 9.3 Kiểm tra mỗi request

Request đã xác thực phải kiểm tra:

- Chữ ký JWT hợp lệ.
- JWT chưa hết hạn.
- Issuer đúng.
- User vẫn tồn tại.
- User vẫn có trạng thái `ACTIVE`.
- Claim `ver` bằng `users.auth_version`.
- Role đang sử dụng chưa bị thu hồi.

Không chỉ tin vào role đã ghi trong JWT nếu dữ liệu quyền hiện tại trong database đã thay đổi.

### 9.4 Thu hồi JWT

JWT đã phát hành không thể bị xóa khỏi mọi bản sao chỉ bằng cách xóa cookie.

T2-01 giảm rủi ro bằng:

- Access token chỉ tồn tại 30 phút.
- Logout xóa cookie trên trình duyệt.
- Reset mật khẩu tăng `users.auth_version`.
- Vô hiệu hóa tài khoản khiến mọi request sau bị từ chối.
- Thu hồi role khiến quyền tương ứng không còn sử dụng được.
- Mỗi request so sánh `ver` trong JWT với database.

Giới hạn đã biết:

- Logout thông thường không đưa `jti` vào denylist.
- Nếu token đã bị sao chép trước logout, bản sao có thể còn hợp lệ tối đa 30 phút nếu không có thay đổi `auth_version`, trạng thái hoặc role.
- Denylist theo `jti` là hướng nâng cấp sau T2-01 nếu cần thu hồi riêng từng token ngay lập tức.

## 10. Đăng xuất

- Endpoint logout sử dụng `POST`.
- Request logout bắt buộc có CSRF token.
- Người dùng phải đang xác thực.
- Server trả cookie `DRSS_ACCESS_TOKEN` với giá trị rỗng và `Max-Age=0`.
- Sau logout, điều hướng về trang đăng nhập.
- Không dùng logout bằng `GET`.

## 11. Quên và đặt lại mật khẩu

Luồng được tách thành ba trang để tránh bắt người dùng nhập mật khẩu mới trước khi email và OTP được xác minh.

### 11.1 Bước 1 — Nhập email

Route:

- `GET /auth/forgot-password`
- `POST /auth/forgot-password`

Quy tắc:

- Chỉ nhập email.
- Phản hồi luôn là thông báo chung.
- Không xác nhận email có tồn tại hay không.
- Nếu tài khoản hợp lệ, server tạo OTP `PASSWORD_RESET` và gửi Gmail.
- Dù email không tồn tại, luồng ngoài vẫn chuyển sang màn hình nhập OTP.

### 11.2 Bước 2 — Xác minh OTP

Route:

- `GET /auth/reset-password/verify`
- `POST /auth/reset-password/verify`

Quy tắc:

- Trang chỉ yêu cầu OTP.
- Email được giữ trong trạng thái reset phía server.
- Giao diện chỉ được hiển thị email đã che bớt nếu cần.
- OTP đúng tạo quyền đặt lại mật khẩu có hiệu lực 10 phút.
- Trạng thái xác minh được giữ phía server, không tin dữ liệu xác minh do client gửi.
- OTP sai, hết hạn, đã dùng hoặc quá số lần thử trả thông báo chung.

### 11.2.1 Trạng thái reset phía server

T2-01 sử dụng `HttpSession` để quản lý luồng đặt lại mật khẩu.

Trước khi xác minh OTP:

- Session giữ email đã chuẩn hóa để duy trì luồng reset.
- Email không được đưa vào URL hoặc hidden field làm nguồn xác thực.
- Với email không tồn tại, giao diện và điều hướng vẫn giống email tồn tại nhằm chống dò tìm tài khoản.

Sau khi OTP `PASSWORD_RESET` được xác minh thành công:

- Server đổi session ID để giảm nguy cơ session fixation.
- Session lưu UUID của `verification_tokens` đã được xác minh.
- Không lưu OTP rõ, OTP digest, mật khẩu hoặc JWT trong session.
- Email tạm dùng ở bước trước được xóa khỏi session khi không còn cần thiết.
- Quyền đặt lại mật khẩu tồn tại 10 phút kể từ `verified_at`.
- Mỗi lần mở hoặc gửi form mật khẩu mới, server phải đọc lại token từ database và kiểm tra:
  - Purpose là `PASSWORD_RESET`.
  - `verified_at` đã có giá trị.
  - Chưa có `consumed_at`.
  - Thời điểm hiện tại chưa vượt quá `verified_at + 10 phút`.
  - Token thuộc đúng user của luồng reset.

Khi đặt lại mật khẩu thành công:

- Cập nhật password hash.
- Tăng `users.auth_version`.
- Đặt `consumed_at` cho reset token.
- Ba thay đổi trên phải nằm trong cùng transaction.
- Xóa toàn bộ trạng thái reset khỏi session.
- Không tự động đăng nhập người dùng.

Khi grant hết hạn, token không hợp lệ hoặc session không còn tồn tại:

- Xóa trạng thái reset khỏi session.
- Không cho truy cập trang nhập mật khẩu mới.
- Yêu cầu người dùng bắt đầu lại luồng quên mật khẩu.
- Chỉ trả thông báo chung, không làm lộ trạng thái tài khoản hoặc token.

Giới hạn của T2-01:

- Session được quản lý bởi ứng dụng Spring Boot hiện tại.
- Nếu server khởi động lại, trạng thái reset đang thực hiện có thể mất và người dùng phải bắt đầu lại.
- Khi triển khai nhiều instance, session cần được chuyển sang kho dùng chung như Redis; nội dung này nằm ngoài phạm vi T2-01.

### 11.3 Bước 3 — Nhập mật khẩu mới

Route:

- `GET /auth/reset-password/new`
- `POST /auth/reset-password/new`

Form gồm:

- `newPassword`
- `confirmPassword`

Quy tắc:

- Không cho truy cập nếu chưa xác minh OTP.
- Không yêu cầu nhập lại email hoặc OTP.
- Password mới phải tuân thủ chính sách mật khẩu.
- Cập nhật password hash, tăng `auth_version` và consume reset token trong cùng transaction.
- Xóa trạng thái reset phía server sau khi hoàn thành.
- Không tự động đăng nhập.
- Điều hướng về trang đăng nhập sau khi reset thành công.

## 12. Gửi lại OTP

Endpoint:

- `POST /auth/resend-otp`

Quy tắc:

- Bắt buộc CSRF.
- Cooldown tối thiểu 60 giây.
- Tối đa 3 yêu cầu cho cùng email trong 15 phút.
- Tối đa 10 yêu cầu từ cùng IP trong 15 phút.
- OTP mới làm OTP cũ mất hiệu lực.
- Phản hồi chung, không cho biết email có tồn tại hay không.

### 12.1 Xác định purpose và email

- Endpoint hỗ trợ cả `ACCOUNT_ACTIVATION` và `PASSWORD_RESET`.
- Với kích hoạt, email được nhận từ DTO và được chuẩn hóa ở server.
- Với reset mật khẩu, purpose có thể được gửi từ form nhưng email chỉ được lấy từ trạng thái reset trong `HttpSession`; server không tin email do client gửi cho luồng này.
- Purpose không hợp lệ hoặc session reset không tồn tại đều bị từ chối bằng thông báo chung.

## 13. Rate limiting

### 13.1 Giới hạn cơ bản

| Hành động              | Giới hạn theo chủ thể                      | Giới hạn theo IP  |
| ---------------------- | ------------------------------------------ | ----------------- |
| Đăng ký                | 3 lần/email + IP/15 phút                   | 10 lần/IP/15 phút |
| Gửi lại OTP            | Cooldown 60 giây; 3 lần/email + IP/15 phút | 10 lần/IP/15 phút |
| Xác minh OTP kích hoạt | Tối đa 5 lần sai/token                     | 20 lần/IP/15 phút |
| Đăng nhập              | 5 lần sai/email + IP/15 phút               | 20 lần/IP/15 phút |
| Quên mật khẩu          | 3 lần/email + IP/15 phút                   | 10 lần/IP/15 phút |
| Xác minh OTP reset     | Tối đa 5 lần sai/token                     | 20 lần/IP/15 phút |

### 13.2 Hình phạt tăng dần

Áp dụng cho đăng nhập sai và xác minh OTP sai:

1. Vượt ngưỡng lần đầu: khóa 15 phút.
2. Hết thời gian khóa, cho phép thử lại.
3. Nếu lần thử tiếp theo vẫn sai: khóa ngay 30 phút.
4. Tiếp tục sai sau lần mở tiếp theo: khóa 60 phút.
5. Các mức tiếp theo tăng gấp đôi.
6. Thời gian khóa tối đa là 24 giờ.
7. Request gửi trong lúc đang bị khóa bị từ chối nhưng không tiếp tục kéo dài thời gian khóa.
8. Đăng nhập hoặc xác minh thành công chỉ xóa bộ đếm subject riêng theo mục 13.5; không xóa bộ đếm IP-wide.
9. Nếu không có thất bại mới trong 24 giờ, mức phạt được đưa về ban đầu.

### 13.3 Chống khóa tài khoản bởi bên thứ ba

- Không khóa tăng dần chỉ dựa trên email.
- Khóa đăng nhập sử dụng cặp `normalizedEmail + IP`.
- Đồng thời duy trì giới hạn riêng cho IP.
- Không đổi trạng thái user thành `BANNED` vì rate limit.
- Khóa do bảo mật không được ghi đè quyết định quản trị tài khoản.

### 13.4 Bảo vệ dữ liệu rate limit

- Không cần lưu email hoặc IP rõ nếu không phục vụ báo cáo nghiệp vụ.
- Subject key được tạo từ dữ liệu chuẩn hóa và HMAC.
- Không ghi email, IP đầy đủ hoặc secret vào log cảnh báo.
- Rate limit phải có test cho ngưỡng, thời gian khóa, tăng mức phạt và reset sau thành công.
- `subject_key` được tạo bằng HMAC-SHA256 với `RATE_LIMIT_HMAC_SECRET`.
- `RATE_LIMIT_HMAC_SECRET` không được dùng chung với JWT hoặc OTP.
- Chuỗi đầu vào HMAC phải có version và domain separation, bao gồm action, scope và subject đã chuẩn hóa.
- Giá trị lưu trong database là chuỗi hexadecimal 64 ký tự; email và IP rõ không được lưu trong `auth_rate_limits`.

### 13.5 Reset bộ đếm sau xác thực thành công

- Đăng nhập thành công xóa bộ đếm và mức phạt `LOGIN + EMAIL_IP` của đúng email đã chuẩn hóa và IP hiện tại.
- Đăng nhập thành công không xóa bộ đếm `LOGIN + IP`.
- Xác minh OTP thành công xử lý hoặc consume token tương ứng nhưng không xóa bộ đếm `VERIFY_ACTIVATION + IP` hoặc `VERIFY_RESET + IP`.
- Bộ đếm IP-wide được giữ lại để một tài khoản thành công không thể xóa dấu vết tấn công đang diễn ra trên cùng IP.
- Bộ đếm IP-wide tự được xử lý theo cửa sổ 15 phút, `blocked_until`, hình phạt tăng dần và quy tắc không có thất bại mới trong 24 giờ.
- Reset rate limit không thay đổi trạng thái nghiệp vụ của user và không tự động chuyển user thành `BANNED`.

## 14. Chống dò tìm tài khoản

Các luồng sau phải trả phản hồi chung:

- Đăng ký bằng email đã tồn tại.
- Gửi lại OTP.
- Quên mật khẩu.
- Email không tồn tại khi đăng nhập.
- Mật khẩu sai.
- OTP sai, hết hạn, đã dùng hoặc vượt số lần thử.

Ngoại lệ:

- Nếu mật khẩu đã được xác minh đúng, hệ thống được phép thông báo tài khoản chưa kích hoạt hoặc bị vô hiệu hóa.
- Thông tin nội bộ chi tiết chỉ được ghi dưới dạng log an toàn, không chứa secret.

## 15. CSRF

- CSRF được bật cho mọi request thay đổi trạng thái.
- Mọi form `POST` phải chứa CSRF token.
- Không vô hiệu hóa CSRF toàn cục để làm cho test hoặc form chạy.
- JWT nằm trong cookie nên CSRF vẫn là yêu cầu bắt buộc.
- CSRF token được quản lý bằng session.
- Session chỉ phục vụ CSRF và trạng thái tạm của luồng reset; authentication chính vẫn sử dụng JWT cookie.
- Request thiếu hoặc sai CSRF token trả `403`.
- Authentication sử dụng `SessionCreationPolicy.STATELESS`; `SecurityContext` không được lưu trong `HttpSession`.
- `STATELESS` chỉ áp dụng cho authentication. `HttpSession` vẫn được sử dụng riêng cho `HttpSessionCsrfTokenRepository` và password-reset grant.
- Cấu hình này tránh việc JWT authentication được dựng lại trên mỗi request làm kích hoạt lại session-authentication strategy và vô hiệu CSRF token vừa render.

## 16. RBAC

### 16.1 Quyền theo route

| Route                                                             | Quyền                             |
| ----------------------------------------------------------------- | --------------------------------- |
| `/auth/register`, `/auth/verify-otp`, `/auth/resend-otp`          | Public                            |
| `/auth/login`, `/auth/forgot-password`, `/auth/reset-password/**` | Public                            |
| `POST /auth/logout`                                               | Authenticated                     |
| `/admin/**`                                                       | `ROLE_ADMIN`                      |
| `/coordinator/**`                                                 | `ROLE_COORDINATOR`                |
| `/team/**`                                                        | `ROLE_RESCUER`                    |
| `/requests/**`                                                    | `ROLE_CITIZEN`                    |
| `/profile/**`                                                     | Authenticated                     |
| `/dev/**`                                                         | Chỉ hoạt động trong profile `dev` |

### 16.2 Quy tắc role

- Một user có thể có nhiều role.
- Chỉ role có `revoked_at IS NULL` mới có hiệu lực.
- `ROLE_ADMIN` không tự động thay thế các role nghiệp vụ khác.
- UI có thể ẩn chức năng không có quyền nhưng server vẫn phải kiểm tra quyền.
- UI không phải lớp bảo vệ duy nhất.

### 16.3 Điều hướng sau đăng nhập

- T2-01 điều hướng mọi đăng nhập thành công về trang chủ công khai `/` theo mục 8.2.
- Thứ tự ưu tiên landing page theo role chưa được triển khai trong T2-01.
- Khi các trang nghiệp vụ hoàn thiện, nhóm có thể bổ sung saved-request hoặc landing page theo role bằng một task riêng mà không thay đổi service đăng nhập.
- Nếu bổ sung landing page theo role, thứ tự dự kiến là `ROLE_ADMIN`, `ROLE_COORDINATOR`, `ROLE_RESCUER`, rồi `ROLE_CITIZEN`.

## 17. Gmail

T2-01 sử dụng Gmail SMTP làm nhà cung cấp gửi email mặc định.

Cấu hình cố định:

- Host: `smtp.gmail.com`
- Port: `587`
- SMTP authentication: bật
- STARTTLS: bật

Quy tắc:

- Sử dụng Gmail App Password.
- Không sử dụng mật khẩu Gmail thông thường.
- Không commit App Password.
- Gmail là nhà cung cấp gửi; người nhận có thể sử dụng bất kỳ địa chỉ email hợp lệ nào.
- Automated test sử dụng fake email sender, không gửi email thật.
- Việc hỗ trợ nhà cung cấp SMTP khác nằm ngoài phạm vi T2-01.

## 18. Biến môi trường

Các secret bắt buộc:

- `MAIL_USERNAME`
- `MAIL_APP_PASSWORD`
- `MAIL_FROM`
- `JWT_SECRET`
- `OTP_HMAC_SECRET`
- `RATE_LIMIT_HMAC_SECRET`

Cấu hình môi trường:

- `APP_BASE_URL`

Quy tắc:

- `JWT_SECRET`, `OTP_HMAC_SECRET` và `RATE_LIMIT_HMAC_SECRET` phải khác nhau từng đôi một.
- Secret phải có tối thiểu 32 byte ngẫu nhiên.
- Không có secret mặc định trong profile chạy thật.
- Ứng dụng phải fail fast nếu thiếu secret bắt buộc ở môi trường không phải test.
- Test sử dụng secret giả dành riêng cho test.
- Không ghi giá trị secret vào tài liệu, commit, log, issue hoặc PR.

Các file cấu hình:

- `.env.example`: chỉ chứa tên biến và giá trị mẫu giả.
- `scripts/local-env.ps1.example`: mẫu lệnh PowerShell.
- `scripts/local-env.ps1`: chứa secret local và phải bị Git ignore.
- `docs/setup/T2-01-local-auth-config.md`: hướng dẫn thiết lập.
- GitHub Actions sử dụng repository secrets nếu cần secret thật.

Spring Boot không tự động đọc file `.env`. Trên Windows, người phát triển phải nạp biến vào PowerShell trước khi chạy ứng dụng.

## 19. Migration V3

T2-01 phải tạo:

- `V3__Add_Authentication_Support.sql`

Migration tối thiểu gồm:

### 19.1 Bổ sung users

- `auth_version INT NOT NULL DEFAULT 0`

### 19.2 Bảng verification_tokens

Lưu tối thiểu:

- ID
- User
- Purpose
- OTP digest
- Thời điểm hết hạn
- Số lần nhập sai
- Số lần tối đa
- Thời điểm xác minh
- Thời điểm sử dụng
- Thời điểm tạo
- Optimistic-lock version để ngăn xác minh hoặc consume token đồng thời.

### 19.3 Bảng auth_rate_limits

Lưu tối thiểu:

- Action
- Subject key đã bảo vệ
- Số lần thất bại
- Mức phạt
- Thời điểm bắt đầu window
- Thời điểm bị khóa đến
- Thời điểm thất bại gần nhất
- Thời điểm cập nhật
- Optimistic-lock version để tránh mất lượt đếm khi có request đồng thời.

Migration phải có:

- Foreign key phù hợp.
- Unique constraint phù hợp.
- Index cho truy vấn token và rate limit.
- `NOT NULL` tại các cột bắt buộc.
- Kiểu thời gian thống nhất với schema hiện tại.

Không sửa migration V1 hoặc V2 đã merge.

## 20. Giao diện

Các trang xác thực phải:

- Sử dụng layout và component foundation từ T1-08.
- Responsive trên desktop, tablet và mobile.
- Có label rõ ràng.
- Hiển thị lỗi theo từng trường.
- Không dựa hoàn toàn vào validation trình duyệt.
- Validation phải được thực hiện lại phía server.
- Không hiển thị JWT, OTP digest hoặc thông tin nội bộ.
- Thông báo chống enumeration phải giữ nội dung chung.

## 21. Kiểm thử bắt buộc

Tối thiểu phải có automated test cho:

### Đăng ký và kích hoạt

- Đăng ký hợp lệ.
- Email ACTIVE đã tồn tại.
- Email INACTIVE đăng ký lại.
- Gán `ROLE_CITIZEN`.
- OTP đúng.
- OTP sai.
- OTP hết hạn.
- OTP đã dùng.
- OTP vượt 5 lần.
- Resend cooldown.
- OTP cũ mất hiệu lực sau resend.
- Gmail lỗi không rollback user.

### Đăng nhập và JWT

- Đăng nhập hợp lệ.
- Email không tồn tại và mật khẩu sai có cùng phản hồi.
- Tài khoản `INACTIVE`.
- Tài khoản `BANNED`.
- JWT hợp lệ.
- JWT hết hạn.
- JWT sai chữ ký.
- JWT có `auth_version` cũ.
- User bị vô hiệu hóa sau khi token đã phát hành.
- Role bị thu hồi sau khi token đã phát hành.

### CSRF và RBAC

- POST thiếu CSRF bị từ chối.
- Mỗi role chỉ truy cập được route được phép.
- UI ẩn chức năng không thay thế kiểm tra phía server.
- Truy cập thiếu quyền trả `403`.
- `/dev/**` không hoạt động ngoài profile `dev`.

### Quên mật khẩu

- Phản hồi chung cho email tồn tại và không tồn tại.
- OTP reset đúng, sai, hết hạn và đã dùng.
- Không mở được trang nhập mật khẩu mới nếu chưa xác minh OTP.
- Password và confirm password không khớp.
- Reset thành công tăng `auth_version`.
- JWT cũ bị từ chối sau reset.
- Không tự động đăng nhập sau reset.
- Session khác không sử dụng được quyền reset đã xác minh.
- Reset grant hết hiệu lực sau 10 phút.
- Reset grant bị xóa khỏi session sau khi hoàn thành.
- Token đã verified nhưng chưa consumed không được cấp quyền cho session khác.
- Server restart hoặc session mất yêu cầu người dùng bắt đầu lại luồng reset.

### Rate limiting

- Đúng ngưỡng ban đầu.
- Bị chặn trong thời gian khóa.
- Request khi đang khóa không kéo dài khóa.
- Sai ngay sau khi hết khóa làm tăng mức phạt.
- Thời gian phạt tăng dần và không vượt 24 giờ.
- Thành công xóa bộ đếm liên quan.
- Không có thất bại trong 24 giờ đưa mức phạt về ban đầu.

## 22. Tiêu chí hoàn thành

T2-01 chỉ được coi là hoàn thành khi:

- Auth happy path và negative path đều PASS.
- Flyway chạy V1 → V2 → V3 trên database rỗng.
- Hibernate `ddl-auto=validate` PASS.
- Không có secret, mật khẩu rõ, OTP rõ hoặc JWT trong source/log.
- Server-side RBAC PASS.
- CSRF PASS.
- Manual browser test PASS.
- GitHub Actions PASS.
- Handoff ghi đúng commit và kết quả thực tế.
- PR có `Closes #4`.
- Vy review UI/flow và approve.
- Duy review test/data/security và approve.

## 23. Ngoài phạm vi T2-01

Các nội dung chưa triển khai trong task này:

- Refresh token.
- Social login hoặc OAuth login.
- Xác thực đa yếu tố sau đăng nhập.
- JWT denylist theo `jti`.
- Quản lý thiết bị và danh sách phiên đăng nhập.
- Gửi email bằng nhà cung cấp khác Gmail.
- CAPTCHA.
- Redis/distributed rate limiting.
- Chức năng quản trị để khóa/mở tài khoản.
- API mobile riêng.

Các nội dung này chỉ được bổ sung qua task và quyết định kiến trúc sau.
