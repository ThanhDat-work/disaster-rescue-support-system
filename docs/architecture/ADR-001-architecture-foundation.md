# ADR-001: Architecture Foundation

- **Status:** Accepted
- **Date:** 2026-10-05
- **Decision owners:** Đỗ Thành Đạt, Phan Nhật Vy, Bùi Đức Duy
- **Related task:** T1-06
- **Artifact/Trace ID:** ADR-001

## 1. Bối cảnh

DRSS là ứng dụng web hỗ trợ tiếp nhận, điều phối và theo dõi yêu cầu cứu hộ thiên tai. Dự án được thực hiện bởi nhóm ba thành viên và đồng thời phục vụ báo cáo Công nghệ phần mềm và Lập trình web.

Nhóm cần một nền tảng thống nhất để các thành viên có thể clone, build, kiểm thử và phát triển độc lập mà không phụ thuộc vào cấu hình bí mật trên máy của người khác.

## 2. Quyết định công nghệ

| Thành phần | Quyết định |
|---|---|
| Java | Java 17 |
| Framework | Spring Boot 4.0.8 |
| Build tool | Maven Wrapper 3.9.16 |
| Web | Spring MVC, Thymeleaf |
| Persistence | Spring Data JPA |
| Database | MySQL 8.x |
| Schema management | Flyway |
| Security foundation | Spring Security và OAuth2 Resource Server |
| Real-time foundation | WebSocket |
| Packaging | Executable JAR |
| Configuration | Biến môi trường, không commit bí mật |
| Architecture | Layered MVC |

Spring Boot được khóa ở phiên bản `4.0.8`. Việc nâng phiên bản phải được thực hiện trong task riêng và phải chạy lại toàn bộ build/test.

Hibernate sử dụng thuộc tính `spring.jpa.hibernate.ddl-auto=validate`.

Flyway là thành phần duy nhất được phép thay đổi cấu trúc database. Hibernate chỉ kiểm tra sự tương thích giữa entity và schema.

## 3. Cấu trúc package

Package gốc là `vn.edu.hcmute.drss`, gồm:

- `configs`: cấu hình framework và infrastructure.
- `controllers`: điểm vào Spring MVC/REST.
- `dtos`: request và response data transfer objects.
- `entities`: JPA persistence entities.
- `exceptions`: application exceptions và centralized exception handling.
- `repositories`: Spring Data repositories.
- `security`: authentication, authorization, JWT và current-user access.
- `services`: use case, nghiệp vụ, transaction và chuyển trạng thái.

Các package cho Cloudinary, WebSocket, event và listener chỉ được tạo khi bắt đầu triển khai tính năng tương ứng.

## 4. Quy tắc dependency

Luồng dependency chính:

- `controllers` phụ thuộc `services` và `dtos`.
- `services` phụ thuộc `repositories`, `entities` và `dtos`.
- `repositories` phụ thuộc `entities`.
- `security` có thể tích hợp với `services` hoặc `repositories` cho nhu cầu xác thực, nhưng không phụ thuộc `controllers`.

Quy tắc bắt buộc:

1. Controller chỉ tiếp nhận input, validation, mapping và gọi service.
2. Controller không truy cập repository trực tiếp.
3. Service sở hữu nghiệp vụ, transaction và quy tắc chuyển trạng thái.
4. Repository chỉ thực hiện truy cập dữ liệu.
5. Entity không được dùng trực tiếp làm request hoặc response.
6. DTO không chứa annotation JPA.
7. Package `security` xử lý authentication, authorization, JWT và current user.
8. Package `configs` chỉ chứa cấu hình framework hoặc infrastructure.
9. Không tạo package `utils` nếu chưa xác định được trách nhiệm rõ ràng.
10. Không cho phép dependency vòng giữa các layer.

## 5. Cấu hình và bí mật

Các giá trị sau được cung cấp bằng biến môi trường:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SERVER_PORT`

Tệp `.env.example` chỉ mô tả hợp đồng cấu hình. Tệp `.env` và các biến bí mật cục bộ không được commit.

## 6. Chiến lược kiểm thử nền tảng

Smoke test sử dụng profile `test` và không yêu cầu MySQL để CI có thể kiểm tra ApplicationContext mà không cần bí mật.

Các integration test liên quan persistence và Flyway phải được bổ sung trong task kiểm thử tương ứng với môi trường MySQL tương thích. Smoke test không được xem là bằng chứng thay thế integration test database.

## 7. Hệ quả

### 7.1. Tích cực

- Các thành viên dùng chung một baseline có thể tái tạo.
- Nghiệp vụ được tách khỏi controller và persistence.
- Thay đổi database có lịch sử bằng Flyway.
- Mật khẩu không bị đưa lên Git.
- Cấu trúc phù hợp quy mô nhóm và nội dung môn học.

### 7.2. Đánh đổi

- Layered architecture có thể tạo thêm DTO và mapping.
- Spring Boot 4 có một số khác biệt cấu hình so với tài liệu Spring Boot 3.
- Môi trường chạy thật yêu cầu MySQL và biến môi trường hợp lệ.

## 8. Phương án đã cân nhắc

- **Spring Boot 3.5.x:** Không chọn để giữ baseline do Spring Initializr hiện tại sinh ra và đã được kiểm chứng trên Spring Boot 4.0.8.
- **Hibernate tự tạo schema:** Không chọn vì khó kiểm soát lịch sử thay đổi.
- **Package-by-feature ngay từ đầu:** Chưa chọn vì nhóm nhỏ và cần thể hiện rõ mô hình MVC ba tầng; có thể xem xét lại khi số lượng module tăng.
- **Tách frontend thành ứng dụng riêng:** Không chọn vì baseline sử dụng Spring MVC và Thymeleaf.

## 9. Điều kiện xem xét lại

ADR này phải được xem xét lại nếu thay đổi một trong các nội dung sau:

- Phiên bản Java hoặc Spring Boot.
- Hệ quản trị cơ sở dữ liệu.
- Cơ chế schema migration.
- Kiến trúc frontend/backend.
- Mô hình phân lớp hoặc quy tắc dependency.
