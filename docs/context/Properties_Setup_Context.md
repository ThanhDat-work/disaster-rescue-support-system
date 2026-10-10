# T1-06 Shared AI Context — Repository and Architecture Foundation

- **Context version:** 1.5
- **Updated at:** 2026-10-05 23:50 UTC+7
- **Context owner:** Đỗ Thành Đạt
- **Task:** T1-06 — Khởi tạo repository và khóa architecture foundation
- **Baseline branch:** `main`
- **Current verified HEAD:** `7ab808b`
- **PR:** #1 — MERGED
- **Review:** Phan Nhật Vy và Bùi Đức Duy đã APPROVE
- **CI:** PASS trên Pull Request; local `main` chạy `clean verify` PASS
- **Conflict:** Không
- **Task status:** Done

## 1. Cách dùng context này với AI

Thành viên phải gửi toàn bộ context này cho AI trước khi yêu cầu AI sửa code, viết tài liệu, đề xuất commit hoặc hướng dẫn thao tác liên quan T1-06.

AI phải:

1. Đọc toàn bộ context trước khi đề xuất thay đổi.
2. Không đảo ngược các quyết định đã được chấp nhận nếu chưa nêu rõ lý do và tác động.
3. Làm theo từng checkpoint nhỏ, ghi rõ tệp cần sửa, lệnh kiểm tra và kết quả kỳ vọng.
4. Không tự ý đổi phiên bản Java, Spring Boot, database, package root hoặc chiến lược migration.
5. Không yêu cầu hoặc ghi lại password, token, secret, PAT hay nội dung `.env` thật.
6. Kiểm tra nguy cơ conflict với branch, commit và file ownership hiện tại trước khi đề xuất chỉnh sửa.
7. Nếu context có dấu hiệu cũ hơn repository, phải yêu cầu người dùng cung cấp `git branch --show-current`, `git log -1 --oneline` và `git status --short`.
8. Khi hoàn thành một checkpoint, phải đưa ra lệnh build/test và lệnh Git để người dùng tự xác nhận.
9. Không tiếp tục phát triển trên branch `chore/t1-06-foundation`; task mới phải đồng bộ từ `main` và tạo branch riêng theo task.

Phản hồi đầu tiên của AI nên nhắc lại ngắn gọn:

- Branch hiện tại.
- HEAD hiện tại.
- Việc đã hoàn thành.
- Việc tiếp theo dự kiến.
- File nào có nguy cơ conflict.

## 2. Thông tin dự án

- **Product name:** Disaster Rescue Support System.
- **Display name:** DRSS – Hỗ trợ tìm kiếm cứu nạn thiên tai.
- **Repository name:** `disaster-rescue-support-system`.
- **Maven group:** `vn.edu.hcmute`.
- **Artifact:** `drss`.
- **Base package:** `vn.edu.hcmute.drss`.
- **Main class:** `DrssApplication`.
- **Database name:** `drss`.
- **Local database user convention:** `drss_app`.

Không đưa số nhóm môn học vào repository name, artifact hoặc package. Số nhóm chỉ xuất hiện trong README hoặc báo cáo khi cần.

## 3. Thành viên

- Đỗ Thành Đạt — Lead.
- Phan Nhật Vy — UI/responsive và phần backend được phân công.
- Bùi Đức Duy — frontend/backend có giới hạn, test và tài liệu được phân công.

## 4. Baseline công nghệ đã khóa

| Thành phần  | Baseline                                                             |
| ----------- | -------------------------------------------------------------------- |
| Java        | Eclipse Adoptium 17; máy lead đã xác nhận 17.0.19                    |
| Spring Boot | 4.0.8                                                                |
| Maven       | Maven Wrapper 3.9.16                                                 |
| Packaging   | Executable JAR                                                       |
| Web         | Spring MVC, Thymeleaf                                                |
| Persistence | Spring Data JPA                                                      |
| Database    | MySQL 8.x                                                            |
| Migration   | Flyway và `ddl-auto=validate`                                        |
| Security    | Spring Security, OAuth2 Resource Server; JWT triển khai ở task riêng |
| Real-time   | WebSocket dependency đã có; triển khai ở task riêng                  |
| Media       | Cloudinary triển khai ở task riêng                                   |
| Tests       | JUnit/Spring Boot smoke test; integration test bổ sung sau           |

## 5. Dependencies hiện có

Production dependencies đã được Spring Initializr tạo và build thành công:

- Spring Data JPA.
- Flyway và Flyway MySQL.
- Spring Security.
- OAuth2 Resource Server.
- Thymeleaf và Thymeleaf Spring Security extras.
- Validation.
- Spring Web MVC.
- WebSocket.
- MySQL Connector/J ở runtime scope.

Spring Boot 4 tách nhiều test starter theo module. Các test dependency do Initializr sinh ra đang được giữ nguyên vì build đã xác nhận thành công.

Không thêm Lombok, DevTools, H2 hoặc Cloudinary SDK trong T1-06 nếu chưa có quyết định/task tương ứng.

## 6. Cấu hình đã thống nhất

`application.yaml` lấy các giá trị sau từ biến môi trường:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SERVER_PORT`, mặc định `8080`

Quy tắc:

- `.env.example` chỉ là hợp đồng cấu hình; Spring Boot không tự động đọc file này.
- `.env` và `.env.*` bị Git ignore; `.env.example` được phép commit.
- Không commit mật khẩu MySQL.
- JPA dùng `open-in-view=false`.
- Hibernate dùng `ddl-auto=validate`.
- Flyway là thành phần duy nhất thay đổi schema.
- Spring Boot 4 dùng namespace `spring.servlet.encoding`, không dùng namespace cũ `server.servlet.encoding`.

## 7. Kiểm thử đã thiết lập

- `DrssApplicationTests.contextLoads()` là smoke test nền tảng.
- Test sử dụng profile `test`.
- `application-test.yaml` loại DataSource, Hibernate JPA và Flyway khỏi smoke test để CI không cần database secret.
- Đây không phải integration test database.
- Integration test cho persistence/Flyway phải được thêm trong task phù hợp sau này.

## 8. Package architecture đã tạo

Package gốc `vn.edu.hcmute.drss` hiện có:

- `configs`
- `controllers`
- `dtos`
- `entities`
- `exceptions`
- `repositories`
- `security`
- `services`

Mỗi package có `package-info.java` để mô tả trách nhiệm.

Quy tắc dependency:

- `controllers` gọi `services`, không gọi trực tiếp `repositories`.
- `services` sở hữu nghiệp vụ, transaction và chuyển trạng thái.
- `repositories` chỉ truy cập dữ liệu.
- `entities` không được dùng trực tiếp làm request/response.
- `dtos` không chứa annotation JPA.
- Không tạo `utils` nếu chưa xác định trách nhiệm rõ ràng.
- Cloudinary dự kiến ở `integrations.cloudinary` khi triển khai.
- WebSocket dự kiến ở `websockets` khi triển khai.

## 9. Các commit đã xác nhận

| Commit    | Nội dung                                         |
| --------- | ------------------------------------------------ |
| `78b8aa1` | Khởi tạo Spring Boot foundation                  |
| `5da9464` | Cấu hình project metadata và test profile        |
| `c9f5389` | Cấu hình MySQL bằng biến môi trường              |
| `2b9e9ac` | Tạo layered package structure                    |
| `4dbcd04` | record architecture foundation decision          |
| `cffff31` | document project setup and contribution workflow |
| `f743eb8` | add Java CI build gate                           |

Các commit trên thuộc lịch sử branch T1-06 và vẫn tra cứu được trong PR #1. PR đã được squash-merge vào `main` thành baseline chính thức:

| Commit    | Nội dung                                  |
| --------- | ----------------------------------------- |
| `7ab808b` | establish project foundation — PR #1     |

## 10. Bằng chứng đã đạt

- Java 17.0.19 hoạt động.
- Maven Wrapper 3.9.16 hoạt động.
- `clean package` và `clean test` đã BUILD SUCCESS.
- Smoke test chạy 1 test, 0 failure, 0 error.
- MySQL database `drss` và user cục bộ `drss_app` kết nối thành công.
- User `drss_app@localhost` chỉ có `USAGE` toàn cục và `ALL PRIVILEGES` trên `drss.*`.
- Flyway, JPA, Hikari và embedded Tomcat khởi động thành công.
- Ứng dụng nhận HTTP request ở cổng 8080.
- Graceful shutdown hoàn tất, không có exception.
- Không có secret được commit.
- Phan Nhật Vy và Bùi Đức Duy đã approve PR #1.
- PR #1 đã squash-merge thành công vào `main` tại commit `7ab808b`.
- Sau khi merge, local `main` đã chạy `clean verify`: 1 test, 0 failure, 0 error, BUILD SUCCESS.
- `main`, `origin/main` và `origin/HEAD` đã đồng bộ tại thời điểm nghiệm thu.

## 11. Trạng thái cuối của T1-06

Đã hoàn thành:

- Repository baseline.
- Maven Wrapper và dependency baseline.
- Project metadata và UTF-8.
- Externalized MySQL configuration.
- Test profile và smoke test.
- Runtime verification với MySQL.
- Layered package skeleton.
- `ADR-001-architecture-foundation.md`; chưa được xác nhận commit tại thời điểm context version 1.0.
- Review và commit ADR-001.
- Viết README: clone, prerequisites, configuration, build, test, run và troubleshooting cơ bản.
- Ghi quy tắc branch, Conventional Commit và Pull Request.
- Tạo CI/build gate không cần secret.
- Push branch và mở PR.
- Vy và Duy review và approve PR #1.
- CI trên Pull Request PASS.
- Squash-merge PR #1 vào `main`.
- Đồng bộ local `main` và chạy lại `clean verify` thành công.

T1-06 đã hoàn tất và không còn hạng mục triển khai. Evidence chính thức gồm PR #1, hai approval, CI PASS, kết quả local `clean verify` và commit `7ab808b`.

Task tiếp theo phải bắt đầu từ baseline `main` tại commit `7ab808b` hoặc commit mới hơn đã được merge hợp lệ. Không tiếp tục sửa trên branch T1-06.

Không triển khai entity nghiệp vụ, JWT, WebSocket hoặc Cloudinary trong T1-06.

## 12. Quy tắc phối hợp Git để tránh conflict

1. Trước khi làm, chạy `git status --short`, `git branch --show-current`, `git log -1 --oneline` và so sánh với context.
2. Working tree phải sạch trước khi pull, rebase hoặc chuyển branch.
3. Mỗi thành viên ưu tiên làm trên branch riêng và gửi Pull Request.
4. Không có hai người cùng chỉnh một file nếu chưa thống nhất file owner.
5. Không commit trực tiếp lên `main`.
6. Không force-push branch của người khác.
7. Trước khi push, chạy test/build phù hợp và `git diff --check`.
8. Commit theo Conventional Commits, ví dụ `feat:`, `fix:`, `docs:`, `test:`, `chore:`.
9. Một commit chỉ nên chứa một mục đích có thể review được.
10. Nếu nhận bàn giao branch chung, context phải ghi rõ HEAD bàn giao và file đang dở.
11. Nếu phát hiện repository mới hơn context, dừng chỉnh sửa và cập nhật context trước.

## 13. Rule bắt buộc cập nhật context sau khi làm việc chung

Một context trong Notion là nguồn bàn giao chính thức cho task dùng chung. Sau mỗi phiên làm việc có thay đổi vật chất, người thực hiện phải cập nhật context trước khi bàn giao.

Thay đổi vật chất gồm:

- Có commit hoặc push mới.
- Thay đổi dependency, version hoặc cấu hình.
- Thêm/sửa migration, entity, API contract hoặc package architecture.
- Thay đổi quyết định kỹ thuật.
- Thay đổi file ownership hoặc phạm vi thành viên.
- Phát sinh lỗi, blocker hoặc việc dở dang ảnh hưởng người sau.

Mỗi lần cập nhật phải sửa tối thiểu:

1. `Context version`.
2. `Updated at` và người cập nhật.
3. Branch và HEAD mới nhất.
4. Phần đã hoàn thành.
5. File đã thay đổi hoặc file đang được giữ quyền chỉnh sửa.
6. Lệnh test/build đã chạy và kết quả.
7. Quyết định mới cùng lý do.
8. Blocker hoặc rủi ro còn lại.
9. Việc tiếp theo và người nhận bàn giao.

Không đưa vào context:

- Password, token, cookie, secret, PAT hoặc nội dung `.env` thật.
- Dữ liệu cá nhân không cần thiết.
- Log dài không có kết luận; chỉ giữ kết quả và đường dẫn evidence.
- Giả định chưa được nhóm xác nhận nhưng lại viết như quyết định chính thức.

## 14. Mẫu handoff ngắn để nối vào cuối context

- **Context version:**
- **Updated at:**
- **Updated by:**
- **Branch:**
- **HEAD:**
- **Completed:**
- **Changed files:**
- **Verification:**
- **Decisions:**
- **Open issues/blockers:**
- **Next action:**
- **Next owner:**

Người tiếp theo chỉ bắt đầu sửa code khi phần handoff này phù hợp với trạng thái Git thực tế.

## 15. Handoff cuối T1-06

- **Context version:** 1.5
- **Updated at:** 2026-10-05 23:50 UTC+7
- **Updated by:** Đỗ Thành Đạt
- **Branch:** `main`
- **HEAD:** `7ab808b`
- **Completed:** Khởi tạo foundation, tài liệu kiến trúc, README, cấu hình ngoài, smoke test, CI, review và squash-merge PR #1.
- **Changed files:** 22 file được tích hợp qua PR #1; xem PR để tra cứu diff đầy đủ.
- **Verification:** PR CI PASS; local `main` chạy `clean verify` PASS với 1 test, 0 failure, 0 error.
- **Decisions:** Giữ nguyên baseline tại các mục 4–8; JWT, WebSocket, Cloudinary và entity nghiệp vụ được triển khai ở task riêng.
- **Open issues/blockers:** Không có blocker thuộc phạm vi T1-06.
- **Next action:** Đồng bộ `main`, đọc context của task kế tiếp và tạo branch mới theo task.
- **Next owner:** Theo thẻ task được phân công trên Notion.
