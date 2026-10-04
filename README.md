# Disaster Rescue Support System (DRSS)

DRSS là ứng dụng web hỗ trợ tiếp nhận, điều phối và theo dõi yêu cầu cứu hộ trong tình huống thiên tai.

Dự án được xây dựng theo layered MVC với Spring Boot, Thymeleaf, Spring Data JPA, MySQL và Flyway. Repository này dùng chung cho quá trình phát triển, kiểm thử và tổng hợp tài liệu của nhóm.

## Trạng thái hiện tại

Foundation của dự án đã được thiết lập:

- Java và Maven Wrapper đã được khóa phiên bản.
- Spring Boot có thể build và chạy bằng executable JAR.
- MySQL, JPA, Flyway và HikariCP đã được kiểm chứng khi chạy thật.
- Cấu hình bí mật được cung cấp bằng biến môi trường.
- Smoke test không yêu cầu database secret.
- Layered package structure và ADR kiến trúc đã được thiết lập.

Các chức năng nghiệp vụ, JWT, WebSocket và Cloudinary được triển khai trong những task riêng.

## Công nghệ

| Thành phần | Phiên bản/trạng thái |
|---|---|
| Java | 17 |
| Spring Boot | 4.0.8 |
| Maven Wrapper | 3.9.16 |
| Spring MVC | Đã thêm dependency |
| Thymeleaf | Đã thêm dependency |
| Spring Data JPA | Đã thêm dependency |
| MySQL | 8.x |
| Flyway | Đã cấu hình |
| Spring Security | Foundation dependency |
| OAuth2 Resource Server | Foundation dependency cho JWT |
| WebSocket | Foundation dependency |
| Cloudinary | Triển khai trong task riêng |
| Bootstrap | Dùng cho UI trong task giao diện |

## Yêu cầu môi trường

Máy phát triển cần có:

- Git.
- JDK 17.
- MySQL 8.x.
- PowerShell trên Windows.
- DBeaver hoặc MySQL client tương đương.

Không cần cài Maven toàn cục vì repository đã có Maven Wrapper.

Kiểm tra Java:

    java -version

Kết quả phải hiển thị Java 17.

## Clone repository

    git clone https://github.com/ThanhDat-work/disaster-rescue-support-system.git
    cd disaster-rescue-support-system

Kiểm tra remote và branch:

    git remote -v
    git branch --show-current
    git status --short

## Khởi tạo MySQL cục bộ

Mở SQL Editor bằng tài khoản MySQL quản trị và chạy các lệnh sau. Thay `YOUR_LOCAL_PASSWORD` bằng mật khẩu riêng trên máy; không commit hoặc gửi mật khẩu này cho người khác.

    CREATE DATABASE IF NOT EXISTS drss
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_0900_ai_ci;

    CREATE USER IF NOT EXISTS 'drss_app'@'localhost'
        IDENTIFIED BY 'YOUR_LOCAL_PASSWORD';

    ALTER USER 'drss_app'@'localhost'
        IDENTIFIED BY 'YOUR_LOCAL_PASSWORD';

    GRANT ALL PRIVILEGES ON drss.*
        TO 'drss_app'@'localhost';

    SHOW GRANTS FOR 'drss_app'@'localhost';

Tài khoản `drss_app` chỉ được cấp quyền trên schema `drss`, không được cấp quyền quản trị toàn bộ MySQL server.

## Cấu hình biến môi trường trên PowerShell

Thiết lập URL và cổng:

    $env:DB_URL = "jdbc:mysql://localhost:3306/drss"
    $env:SERVER_PORT = "8080"

Nhập username/password thông qua credential prompt để password không nằm trong lịch sử lệnh:

    $credential = Get-Credential `
      -UserName "drss_app" `
      -Message "Nhập mật khẩu MySQL cục bộ"

    $env:DB_USERNAME = $credential.UserName
    $env:DB_PASSWORD = $credential.GetNetworkCredential().Password

Kiểm tra các biến không nhạy cảm:

    $env:DB_URL
    $env:DB_USERNAME
    $env:SERVER_PORT

Không in hoặc chụp màn hình giá trị `$env:DB_PASSWORD`.

Các biến trên chỉ tồn tại trong phiên PowerShell hiện tại. Khi mở terminal mới, cần thiết lập lại trước khi chạy ứng dụng.

Tệp `.env.example` mô tả hợp đồng cấu hình nhưng Spring Boot không tự động đọc tệp `.env`. Tệp `.env` thật bị Git ignore.

## Build và test

Chạy smoke test không cần MySQL secret:

    .\mvnw.cmd clean test

Build executable JAR:

    .\mvnw.cmd clean package

Artifact được tạo tại:

    target/drss-0.0.1-SNAPSHOT.jar

Smoke test sử dụng profile `test` và loại DataSource, Hibernate JPA, Flyway khỏi ApplicationContext. Nó chỉ kiểm tra foundation context; không thay thế integration test database.

## Chạy ứng dụng

Sau khi MySQL hoạt động và các biến môi trường đã được thiết lập:

    .\mvnw.cmd spring-boot:run

Kết quả đạt khi log có nội dung tương đương:

    Tomcat started on port 8080
    Started DrssApplication

Mở trình duyệt tại:

    http://localhost:8080/login

Ở giai đoạn foundation, Spring Security có thể tạo tài khoản và password tạm trong log. Đây không phải tài khoản MySQL và không phải cơ chế đăng nhập chính thức của DRSS.

Dừng ứng dụng bằng `Ctrl+C`.

## Cấu trúc package

Package gốc: `vn.edu.hcmute.drss`.

- `configs`: cấu hình framework và infrastructure.
- `controllers`: điểm vào Spring MVC/REST.
- `dtos`: request và response data transfer objects.
- `entities`: JPA persistence entities.
- `exceptions`: application exceptions và centralized exception handling.
- `repositories`: Spring Data repositories.
- `security`: authentication, authorization, JWT và current-user access.
- `services`: use case, nghiệp vụ, transaction và chuyển trạng thái.

Quy tắc chính:

- Controller gọi service, không gọi trực tiếp repository.
- Service sở hữu nghiệp vụ và transaction.
- Repository chỉ truy cập dữ liệu.
- Entity không dùng trực tiếp làm request hoặc response.
- DTO không chứa annotation JPA.
- Không tạo package `utils` khi chưa xác định trách nhiệm rõ ràng.

Chi tiết quyết định kiến trúc xem tại [ADR-001](docs/architecture/ADR-001-architecture-foundation.md).

## Database migration

Flyway là thành phần duy nhất được phép thay đổi cấu trúc database.

Migration được đặt tại:

    src/main/resources/db/migration

Quy tắc đặt tên:

    V<version>__<description>.sql

Ví dụ:

    V1__create_users_and_roles.sql

Không chỉnh sửa migration đã được merge và chạy trên môi trường dùng chung. Khi cần thay đổi schema, tạo migration mới.

Hibernate sử dụng `ddl-auto=validate`, vì vậy Hibernate không tự tạo hoặc tự sửa bảng.

## Git workflow

### Branch

Không commit trực tiếp lên `main`.

Quy ước branch:

- `feat/<task-id>-<scope>` cho tính năng.
- `fix/<task-id>-<scope>` cho sửa lỗi.
- `docs/<task-id>-<scope>` cho tài liệu.
- `test/<task-id>-<scope>` cho kiểm thử.
- `chore/<task-id>-<scope>` cho foundation, build hoặc cấu hình.

Ví dụ:

    feat/t2-01-authentication
    docs/t1-06-readme
    chore/t1-06-foundation

### Commit

Sử dụng Conventional Commits:

- `feat:` tính năng mới.
- `fix:` sửa lỗi.
- `docs:` tài liệu.
- `test:` kiểm thử.
- `refactor:` tái cấu trúc không đổi hành vi.
- `chore:` build, dependency hoặc cấu hình.

Một commit chỉ nên chứa một mục đích có thể review được.

### Pull Request

Trước khi mở hoặc cập nhật Pull Request:

    .\mvnw.cmd clean test
    git diff --check
    git status --short

Pull Request cần ghi rõ:

- Task ID và phạm vi.
- Nội dung đã thay đổi.
- Cách kiểm tra.
- Kết quả build/test.
- Migration hoặc biến môi trường mới, nếu có.
- Screenshot hoặc evidence liên quan.
- Rủi ro, blocker hoặc phần chưa làm.

Không force-push branch của thành viên khác. Nếu nhiều người làm cùng task, phải thống nhất file owner và cập nhật context bàn giao trên Notion.

## Quy tắc bảo mật

- Không commit `.env`.
- Không commit password, token, cookie, JWT secret, Cloudinary secret hoặc GitHub PAT.
- Không ghi secret vào README, ADR, issue, Pull Request hoặc ảnh chụp màn hình.
- Nếu secret bị commit, phải thu hồi hoặc đổi secret; chỉ xóa khỏi commit là chưa đủ.
- Dùng biến môi trường hoặc secret store của nền tảng triển khai.

## Troubleshooting

### `Failed to determine a suitable driver class`

Nguyên nhân thường gặp: chưa thiết lập `DB_URL` hoặc đang chạy application context thật mà không có cấu hình database.

Kiểm tra:

    $env:DB_URL
    $env:DB_USERNAME

### `Access denied for user 'drss_app'`

Kiểm tra lại password, host `localhost` và quyền trên `drss.*`. Có thể đặt lại password bằng tài khoản quản trị:

    ALTER USER 'drss_app'@'localhost'
        IDENTIFIED BY 'NEW_LOCAL_PASSWORD';

### Cổng 8080 đang được sử dụng

Đổi cổng trong phiên PowerShell:

    $env:SERVER_PORT = "8081"

Sau đó chạy lại ứng dụng và truy cập `http://localhost:8081/login`.

### PowerShell hiển thị `LF will be replaced by CRLF`

Đây là cảnh báo chuẩn xuống dòng trên Windows, không phải lỗi build. Vẫn phải chạy `git diff --check` để phát hiện whitespace error thực tế.

### JVM hiển thị cảnh báo class sharing khi test

Cảnh báo `Sharing is only supported for boot loader classes...` không làm test thất bại. Kết luận dựa trên số test, failures, errors và trạng thái `BUILD SUCCESS`.

## Thành viên

- Đỗ Thành Đạt — Lead.
- Phan Nhật Vy — UI/responsive và phần backend được phân công.
- Bùi Đức Duy — frontend/backend có giới hạn, test và tài liệu được phân công.

## Bối cảnh môn học

- Công nghệ phần mềm: Nhóm 7.
- Lập trình web: Nhóm 3.

Tên repository, artifact và package được giữ trung lập để dùng chung cho cả hai môn.
