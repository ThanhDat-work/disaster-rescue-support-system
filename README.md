# Disaster Rescue Support System (DRSS)

DRSS là ứng dụng web hỗ trợ người dân gửi yêu cầu cứu nạn, điều phối viên xác minh và phân công, đội cứu hộ xử lý nhiệm vụ, và quản trị viên quản lý dữ liệu hệ thống. Ứng dụng chạy trên trình duyệt, được xây dựng bằng Java 17, Spring Boot, Thymeleaf và MySQL.

- **Bản demo:** https://drss-demo-dat.onrender.com/
- **Source:** https://github.com/ThanhDat-work/disaster-rescue-support-system
- **Mốc source dùng để cập nhật tài liệu:** `0cf27f89fc583a36305b67699edb32d6e0884563` (SHA do nhóm cung cấp; đối chiếu `git rev-parse HEAD` trên bản nộp cuối).

> URL demo là địa chỉ nhóm cung cấp, không phải cam kết uptime. Dịch vụ miễn phí có thể cần thời gian khởi động sau khi không có truy cập. Không đưa tài khoản, mật khẩu hay dữ liệu người dùng thật vào README hoặc GitHub công khai.

## 1. Chức năng hiện có

| Vai trò/khu vực | Chức năng chính | Trang bắt đầu |
| --- | --- | --- |
| Khách | Trang chủ, đăng ký, xác minh OTP, đăng nhập, quên/đặt lại mật khẩu | `/`, `/auth/register`, `/auth/login` |
| Người dân | Tạo, xem, theo dõi và hủy yêu cầu trong điều kiện cho phép; đính kèm ảnh/video | `/requests`, `/requests/new` |
| Điều phối viên | Xác minh/từ chối, phân công đội, theo dõi tiến độ và đóng yêu cầu | `/coordinator/pending`, `/coordinator/tracking` |
| Cứu hộ | Xem đội tham gia, nhận nhiệm vụ theo đội, bắt đầu và hoàn thành cứu hộ | `/team/my-teams`, `/team/tasks` |
| Quản trị viên | Quản lý loại thiên tai, tài khoản/vai trò, đội cứu hộ và thành viên | `/admin/disasters`, `/admin/users`, `/admin/teams` |
| Tài khoản đã đăng nhập | Hồ sơ, avatar, thống kê theo vai trò, chuyển vai trò được cấp, hộp thư thông báo | `/account/profile`, `/account/role-switch` |

Mỗi phiên xác thực chỉ có **một vai trò đang hoạt động**. Chuyển vai trò yêu cầu xác nhận mật khẩu; kiểm tra quyền ở backend không phụ thuộc vào việc ẩn nút trên giao diện. Hệ thống chặn người gửi tự điều phối/tự cứu hộ yêu cầu của mình và chặn admin tự thay đổi trạng thái hoặc quyền của chính mình. Hiện trạng giữ quy tắc **một đội được phân công cho một yêu cầu**; không mô tả hệ thống như một mạng lưới phân công nhiều đội.

## 2. Công nghệ và luồng xử lý

| Thành phần | Sử dụng trong hệ thống |
| --- | --- |
| Java 17, Spring Boot 4.0.9, Spring MVC | Controller nhận HTTP request; service xử lý nghiệp vụ/transaction; repository truy vấn dữ liệu. |
| Thymeleaf, Bootstrap 5.3.2, JavaScript | Render trang phía server; form, responsive, biểu đồ, điều hướng và các trạng thái loading/retry ở trình duyệt. |
| Spring Security, JWT, CSRF | JWT ký HS256 nằm trong cookie `HttpOnly`; server dựng lại xác thực theo từng request và kiểm tra active role, trạng thái tài khoản, version/quyền hiện hành. CSRF token gắn với HTTP session riêng; session không lưu trạng thái đăng nhập. |
| Spring Data JPA, MySQL, Flyway | Entity/repository lưu dữ liệu; Flyway quản lý schema bằng migration; Hibernate chỉ `validate`, không tự tạo bảng. |
| WebSocket + STOMP | `/ws` đẩy thông báo đúng người/vai trò/đội, cập nhật toast và badge; hộp thư trong MySQL là nguồn đọc lại sau reconnect. |
| Cloudinary | Lưu ảnh/video yêu cầu và avatar; MySQL lưu metadata. Upload ngoài DB transaction nên có luồng bù trừ/dọn dẹp khi thất bại. |
| Gmail SMTP hoặc Brevo HTTPS | Gửi OTP kích hoạt/đặt lại mật khẩu. Local mặc định dùng SMTP; bản deploy dùng Brevo API qua HTTPS. |
| Render + Aiven MySQL | Render chạy ứng dụng bằng `Dockerfile`; Aiven lưu MySQL của môi trường deploy. |

Tạo yêu cầu dùng operation key theo tài khoản, kiểm tra fingerprint nội dung và hạn mức tạo mới (mặc định 5/60 giây/user). Sau khi mất phản hồi, client tra cứu trạng thái cùng key thay vì tạo key mới tùy tiện. Đây **không phải** cơ chế xóa mọi yêu cầu có nội dung giống nhau nhưng là các ý định tạo khác nhau. Thao tác POST khác không mặc nhiên có tính idempotent.

Thông báo realtime dùng STOMP heartbeat/reconnect; khi mất kết nối, client đọc lại hộp thư. Việc thấy `CONNECT` trong DevTools chưa đủ để khẳng định đã nhận `CONNECTED` hoặc nhận được tin. Simple broker hiện dùng không phải message broker bền vững. Mã `X-Request-ID` hỗ trợ tra log; audit lưu thay đổi nhạy cảm của quản trị viên, nhưng không thay thế cơ chế phân quyền.

## 3. Chuẩn bị chạy trên máy Windows

Cần có Git, **JDK 17**, MySQL 8.x, PowerShell và trình duyệt. Có thể dùng DBeaver/MySQL client để tạo schema; không cần cài Maven toàn cục vì đã có Maven Wrapper 3.9.16. Node.js chỉ cần khi chạy script kiểm tra JavaScript/STOMP.

```powershell
git clone https://github.com/ThanhDat-work/disaster-rescue-support-system.git
cd disaster-rescue-support-system
java -version
.\mvnw.cmd -version
git log -1 --oneline
```

Nếu cần tái tạo đúng bản nộp thay vì lấy `main` mới nhất, kiểm tra commit đã chốt và checkout commit đó trong một bản clone riêng. Không tự reset một working tree có thay đổi.

### 3.1. Tạo schema local

Trong MySQL client bằng tài khoản quản trị **trên máy local**, chạy:

```sql
CREATE DATABASE IF NOT EXISTS drss_local
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS drss_ltw_qa
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

`drss_local` để chạy ứng dụng/manual; `drss_ltw_qa` là schema riêng cho integration tests. Cấp quyền trên đúng hai schema cho user MySQL local của bạn; không dùng user hoặc dữ liệu Aiven để test tự động. Hai lệnh trên **không xóa dữ liệu đang có**. Nếu muốn một lần chạy từ trạng thái trống, tạo schema mới với tên khác và chỉnh `DB_URL` tương ứng, đừng `DROP DATABASE` khi chưa backup/xác nhận đích.

### 3.2. Cấu hình biến môi trường

Copy `scripts/local-env.ps1.example` thành `scripts/local-env.ps1`, sửa **trên máy cá nhân**. File đích được `.gitignore` loại trừ. Mẫu hiện có tên schema cũ của một thành viên, vì vậy bắt buộc đổi `DB_URL` thành schema local thật của bạn. Tối thiểu:

| Biến | Local | Ghi chú |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | JDBC URL như `jdbc:mysql://127.0.0.1:3306/drss_local`, tài khoản MySQL local | `DB_URL` phải bắt đầu bằng `jdbc:mysql://`, không dùng URL `mysql://`. |
| `SERVER_PORT`, `APP_BASE_URL` | `8080`, `http://localhost:8080` | Đổi cả hai nếu đổi port. |
| `JWT_SECRET`, `OTP_HMAC_SECRET`, `RATE_LIMIT_HMAC_SECRET` | Ba chuỗi ngẫu nhiên **khác nhau**, mỗi chuỗi ít nhất 32 byte UTF-8 | Không dùng giá trị ví dụ hoặc commit chúng. |
| `AUTH_COOKIE_SECURE` | `false` khi chỉ dùng HTTP localhost | Dùng `true` khi chạy HTTPS. |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Credential server-side riêng | Không đưa API secret vào JavaScript. `CLOUDINARY_FOLDER` nên tách khỏi folder production. |
| `MAIL_FROM` | Địa chỉ gửi hợp lệ | Xem lựa chọn SMTP/Brevo dưới đây. |

Local mặc định `OTP_MAIL_PROVIDER=smtp`: cấu hình `MAIL_USERNAME` và `MAIL_APP_PASSWORD` (Gmail App Password), `MAIL_FROM` tương ứng. Nếu chủ động dùng Brevo ở local, đặt `OTP_MAIL_PROVIDER=brevo`, `BREVO_API_KEY` và `MAIL_FROM` đã được xác nhận ở Brevo; mã vẫn có các placeholder `MAIL_USERNAME`/`MAIL_APP_PASSWORD` trong `application.yaml`, nên giữ hai biến được khai báo trong cấu hình môi trường nhưng không cần sử dụng mật khẩu Gmail thật cho nhánh Brevo. Không đưa key/password vào ảnh chụp, issue, log hoặc file nộp.

Trong **cùng terminal** dùng để chạy app:

```powershell
. .\scripts\local-env.ps1
$env:DB_URL
.\mvnw.cmd spring-boot:run
```

Nếu PowerShell chặn nạp script, sau khi kiểm tra nội dung file, có thể đặt execution policy **chỉ trong phiên terminal hiện tại** bằng `Set-ExecutionPolicy -Scope Process RemoteSigned`, rồi nạp lại; không đổi policy toàn hệ thống. Truy cập `http://localhost:8080/` hoặc `/auth/login`. Dừng bằng `Ctrl+C`. Một terminal mới phải nạp lại biến môi trường.

### 3.3. Migration và dữ liệu mẫu

Ứng dụng chạy Flyway từ `src/main/resources/db/migration/V1__Init_Schema.sql` đến `V14__Add_Admin_User_Audits.sql`. Có **14 migration**; không có một file schema `.sql` độc lập bắt buộc phải import trước. `V1` tạo schema, `V2__Insert_Seed_Data.sql` chèn roles, loại thiên tai và **một số user/team/request demo cũ**; V3–V14 bổ sung cấu trúc, constraint và index. Trên schema trống, Flyway chạy khi app khởi động, sau đó Hibernate `ddl-auto=validate` đối chiếu schema.

Vì `V2` có dữ liệu demo, **schema vừa migrate chưa thể gọi là dữ liệu sạch để mở public**. Gói `DATA_SEED_SACH` nhóm chuẩn bị riêng gồm `00_preflight.sql`, `01_replace_with_clean_demo.sql`, `02_verify_read_only.sql`, `DRSS-tai-khoan-va-luong-demo.md` và `validation.json`; gói này **không nằm trong source ZIP**. Chỉ người phụ trách dữ liệu dùng gói đó trên đúng database đã xác nhận, sau khi có backup, đọc preflight và nắm rõ tác động của bước `replace`. Không chạy `01_replace_with_clean_demo.sql` trong lúc có người dùng hoặc trên database không rõ đích.

File **dump** là bản xuất dữ liệu/schema tại một thời điểm, có thể tái tạo một snapshot trên database trống khi cần bàn giao/khôi phục. Đây là **phương án riêng** với khởi tạo bằng Flyway: phải kiểm tra dump có đủ bảng, dữ liệu và `flyway_schema_history` tương thích trước khi khởi động ứng dụng; không import chồng lên schema đã migrate/đang chạy. Không commit dump chứa thông tin tài khoản hoặc dữ liệu nhạy cảm lên repository public. Khi nộp bài, nhóm bàn giao migration `.sql`, dump phù hợp và hướng dẫn seed ở kênh nộp được kiểm soát; kiểm tra dump không chứa secret/PII không cần thiết.

## 4. Kiểm thử

Unit/MVC test có thể chạy trước:

```powershell
.\mvnw.cmd clean test
```

Integration test cần **MySQL local riêng** (ví dụ `drss_ltw_qa`) và biến môi trường DB trong terminal hiện tại. Sau khi nạp `local-env.ps1`, đổi `DB_URL` của terminal test để trỏ đúng schema test, xác nhận rồi chạy:

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/drss_ltw_qa'
if ($env:DB_URL -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost)(:\d+)?/drss_ltw_qa(\?|$)') {
    throw 'Integration DB phải là drss_ltw_qa trên MySQL local.'
}
.\mvnw.cmd clean verify '-Dspring.profiles.active=it'
```

Không chạy integration test trên `drss_local`, database manual QA, Aiven deploy hay bản dump cần giữ nguyên: test có thể thêm/sửa/xóa fixture. Kết luận theo `BUILD SUCCESS` và report Surefire/Failsafe, không chỉ theo `clean test`. Các test với profile `it` dùng cấu hình thử nghiệm trong `src/test/resources/application-it.yaml`, nhưng vẫn cần MySQL thật qua `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

Smoke thủ công tối thiểu: đăng ký → nhận OTP → đăng nhập người dân → gửi yêu cầu → điều phối xác minh/phân công → cứu hộ bắt đầu/hoàn thành → điều phối đóng → người dân xem trạng thái. Kiểm tra riêng chuyển vai trò và cách ly quyền, media Cloudinary, thông báo realtime **không F5**, badge/toast và đọc lại hộp thư sau reconnect. Dùng tài khoản/fixture demo được bàn giao ngoài README, không dùng người thật.

Chạy kiểm tra bổ sung khi cần:

```powershell
node .\scripts\check-realtime-client.cjs
.\scripts\scan-dependencies.ps1 -InventoryOnly
git diff --check
```

`scan-dependencies.ps1` cần Docker khi quét OSV thực tế; `-InventoryOnly` chỉ liệt kê thành phần. Kết quả OSV theo POM không đồng nghĩa đã quét toàn bộ JS/CSS/CDN. CI ở `.github/workflows/ci.yml` dùng MySQL 8.0 riêng và chạy `clean verify`.

## 5. Bản deploy Render + Aiven

Bản triển khai do nhóm xác nhận chạy **Render (Dockerfile) + Aiven MySQL + Cloudinary + Brevo HTTPS**. Đây là cấu hình khác local SMTP. Không có endpoint Actuator/health chuyên dụng được khai báo trong source; kiểm tra trang chủ và một luồng có truy cập DB/đăng nhập thực tế, không coi HTTP 200 của trang chủ là bằng chứng toàn bộ dịch vụ ngoài đều khỏe.

Biến môi trường cần cấu hình **trong dashboard deploy/secret store**, không đặt giá trị thật trong Git:

| Nhóm | Tên biến/cách đặt |
| --- | --- |
| MySQL | `DB_URL=jdbc:mysql://<AIVEN_HOST>:<PORT>/<DB_NAME>?sslMode=REQUIRED`, `DB_USERNAME`, `DB_PASSWORD`. Không nhúng user/password vào URL. |
| URL/HTTPS | `APP_BASE_URL=https://drss-demo-dat.onrender.com`, `AUTH_COOKIE_SECURE=true`, `SERVER_PORT` khớp port web service (Dockerfile khai báo `10000`). |
| Auth | `JWT_SECRET`, `OTP_HMAC_SECRET`, `RATE_LIMIT_HMAC_SECRET` là ba secret khác nhau, mỗi secret ít nhất 32 byte. |
| OTP deploy | `OTP_MAIL_PROVIDER=brevo`, `BREVO_API_KEY`, `MAIL_FROM` là sender đã xác nhận. YAML vẫn tham chiếu `MAIL_USERNAME` và `MAIL_APP_PASSWORD`; cấu hình hai biến này theo môi trường đang chạy, không dùng Gmail SMTP để gửi trên Render. |
| Media | `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, folder riêng qua `CLOUDINARY_FOLDER` nếu cấu hình. |
| Tùy chọn | `REQUEST_CREATE_MAX_PER_MINUTE` (mặc định 5), `CLOUDINARY_HTTP_TIMEOUT_SECONDS` (mặc định 60), các `MEDIA_CLEANUP_*` trong `application.yaml`. |

Trước khi redeploy: chốt SHA/image, lưu backup DB có thời điểm rõ, kiểm tra migration mới và khả năng tương thích của bản app trước đó; chạy CI/full test trên DB thử nghiệm. Sau deploy: xem log Flyway, kiểm tra `flyway_schema_history`, smoke login/OTP/Brevo, request, ảnh/video, hộp thư STOMP từ hai trình duyệt và role isolation từ một mạng khác. Không đăng log có secret/OTP lên issue.

Nếu deploy lỗi, cân nhắc quay lại image/commit trước **chỉ khi** app cũ tương thích schema hiện tại. Không xóa migration đã chạy, không import dump hoặc restore đè production đang có dữ liệu mới một cách tự động. Dừng thao tác ghi, backup trạng thái hiện tại, đánh giá sự cố rồi mới quyết định khôi phục. Render/Aiven/Cloudinary/Brevo là các dịch vụ tách biệt: khôi phục DB không tự khôi phục media trên Cloudinary.

## 6. Cấu trúc và tài liệu

```text
src/main/java/vn/edu/hcmute/drss/
  configs/ controllers/ dtos/ entities/ repositories/
  security/ services/ realtime/ media/ trace/
src/main/resources/
  db/migration/    # Flyway V1–V14
  templates/       # Thymeleaf pages, layouts, fragments
  static/          # CSS, JS, ảnh và vendor asset
src/test/           # Unit, MVC và integration tests
scripts/            # Kiểm tra client/dependencies, env example
docs/               # ADR, contract, handoff, guideline, evidence
```

Source giải thích luồng ở các `controllers` → `services` → `repositories` và `entities`; phần quyền nằm ở `ApplicationSecurityConfig`, `JwtAuthenticationService` và interceptor STOMP. Đọc `docs/architecture/ADR-001-architecture-foundation.md` để biết quyết định nền tảng và `docs/context/` cho contract/handoff theo từng chức năng. Tài liệu handoff cũ ghi trạng thái **tại thời điểm viết**, có thể không phản ánh merge/deploy cuối; khi mâu thuẫn, đối chiếu source ở commit bàn giao và kết quả kiểm thử cuối.

## 7. Bảo mật và đóng góp

- Không commit `.env`, `scripts/local-env.ps1`, cookie/JWT, OTP, Brevo/Cloudinary key, DB password hoặc bản dump có dữ liệu nhạy cảm. Lộ secret thì thu hồi/đổi secret; xóa dòng trong Git không đủ.
- Không sửa migration đã chạy trên môi trường dùng chung; thêm migration phiên bản kế tiếp và kiểm tra trên MySQL thật.
- Không tự nâng quyền theo thông tin do trình duyệt gửi. Quyền vai trò và quyền truy cập bản ghi phải được backend kiểm tra.
- Thay đổi source qua branch/PR; trước khi merge chạy `clean verify` trên DB test riêng, kiểm tra `git diff --check`, review và smoke luồng bị tác động.

**Thành viên:** Đỗ Thành Đạt (nhóm trưởng, backend/triển khai), Phan Nhật Vy (giao diện/UX), Bùi Đức Duy (kiểm thử và các phần được phân công). Repository dùng cho môn Công nghệ phần mềm (Nhóm 7) và Lập trình web (Nhóm 3).
