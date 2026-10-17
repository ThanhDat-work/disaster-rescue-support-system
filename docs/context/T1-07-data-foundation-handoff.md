# T1-07 — Data Foundation Handoff

## 1. Mục đích

Tài liệu này là nguồn bàn giao kỹ thuật của task **T1-07 — Data Foundation** cho các task phát triển và review tiếp theo của DRSS.

Tài liệu chỉ mô tả trạng thái đã được nghiệm thu, các contract phải giữ ổn định, cách kiểm chứng và những phần được chuyển giao cho Service Layer. Nội dung trao đổi trong các vòng review cũ không thay thế tài liệu này.

## 2. Baseline đã nghiệm thu

- **Owner:** Bùi Đức Duy
- **Reviewer:** Đỗ Thành Đạt
- **Trạng thái:** Approved và đã merge
- **Nhánh đích:** `main`
- **Merge strategy:** Squash and merge
- **Merge commit:** `cd94e7a` — `feat: establish data foundation`
- **Baseline công nghệ:** Java 17, Spring Boot 4.0.8, Maven Wrapper, MySQL 8, Spring Data JPA, Flyway, Lombok

Commit trên là mốc lịch sử của T1-07. Khi làm task mới, trạng thái hiện hành của `main` và code thực tế luôn được ưu tiên nếu repository đã có commit mới hơn.

## 3. Contract cấu hình

Ứng dụng nhận cấu hình database qua đúng ba biến môi trường:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Các thiết lập nền tảng:

- Flyway quản lý schema tại `src/main/resources/db/migration`.
- Hibernate dùng `spring.jpa.hibernate.ddl-auto=validate`.
- `spring.jpa.open-in-view=false`.
- Không đặt thông tin đăng nhập database thật trong repository, tài liệu handoff, commit hoặc PR.

## 4. Schema và migration

### 4.1 Migration đã có

- `V1__Init_Schema.sql`: tạo schema, khóa ngoại, CHECK constraint và index.
- `V2__Insert_Seed_Data.sql`: tạo dữ liệu demo phục vụ phát triển và integration test.

### 4.2 Mười một bảng nghiệp vụ

1. `users`
2. `roles`
3. `user_roles`
4. `disaster_types`
5. `rescue_requests`
6. `rescue_teams`
7. `rescue_team_members`
8. `rescue_assignments`
9. `media_assets`
10. `status_histories`
11. `notifications`

Ngoài ra, Flyway tự tạo bảng `flyway_schema_history`.

### 4.3 Quy tắc migration sau khi merge

`V1` và `V2` đã trở thành migration dùng chung. Không sửa nội dung hai file này cho yêu cầu mới.

Mọi thay đổi schema hoặc seed tiếp theo phải:

1. Tạo migration mới theo thứ tự `V3`, `V4`, ...
2. Có tên mô tả rõ mục đích.
3. Chạy được trên database đã áp dụng `V1` và `V2`.
4. Được kiểm tra trên một database rỗng và một database đã migrate trước đó.

Chỉ được reset hoặc viết lại baseline khi cả nhóm thống nhất rõ ràng và chưa có môi trường dùng chung cần bảo toàn lịch sử migration.

## 5. Contract JPA

- Khóa chính UUID được lưu ở MySQL bằng `BINARY(16)`.
- Các quan hệ sở hữu trọng yếu dùng `@ManyToOne(fetch = FetchType.LAZY)` thay vì giữ foreign key dưới dạng ID thuần.
- `UserRole` dùng khóa kép `UserRoleId` và `@MapsId`.
- `RescueTeamMember` dùng khóa kép `RescueTeamMemberId` và `@MapsId`.
- Không tự ý thêm `CascadeType.REMOVE` cho dữ liệu dùng chung.
- `disaster_types.is_active` và `notifications.is_read` là `NOT NULL`; entity tương ứng dùng `nullable = false`.
- Trường version của `RescueRequest` và `RescueAssignment` phục vụ optimistic locking.

Các repository nền tảng đã có:

- `UserRepository`
- `RoleRepository`
- `UserRoleRepository`
- `DisasterTypeRepository`
- `RescueRequestRepository`
- `RescueTeamRepository`
- `RescueTeamMemberRepository`
- `RescueAssignmentRepository`
- `MediaAssetRepository`
- `StatusHistoryRepository`
- `NotificationRepository`

Task downstream được bổ sung query method cần thiết, nhưng không được thay đổi mapping nền tảng chỉ để né việc viết service hoặc DTO.

## 6. Role và vòng đời yêu cầu

Các role chuẩn:

- `ROLE_ADMIN`
- `ROLE_COORDINATOR`
- `ROLE_CITIZEN`
- `ROLE_RESCUER`

Role của thành viên đội cứu hộ là `ROLE_RESCUER`; không dùng biến thể như `ROLE_RESCUE_TEAM` hoặc `ROLE_RESCUE_MEMBER` nếu chưa có quyết định thay đổi contract.

Vòng đời yêu cầu đã khóa:

`PENDING -> VERIFIED -> ASSIGNED -> IN_PROGRESS -> RESCUED -> CLOSED`

Các nhánh kết thúc/ngoại lệ được schema hỗ trợ:

- `REJECTED`
- `CANCELLED`

Việc cho phép chuyển trạng thái nào trong từng ngữ cảnh phải được Service Layer kiểm soát, không dựa riêng vào giá trị CHECK của database.

## 7. Dữ liệu demo có thể dựa vào khi phát triển

Mật khẩu chung của các tài khoản demo là `123456`; trong database chỉ lưu BCrypt hash.

Seed hiện có bốn vai trò chính: admin, coordinator, citizen và rescuer.

Các kịch bản tiêu biểu:

- `REQ-0001`: trạng thái `VERIFIED`, chưa có assignment.
- `REQ-0002`: trạng thái `ASSIGNED`, liên kết với `ASN-0001` và đội `TEAM-001`.
- Lịch sử `REQ-0002`: `PENDING -> VERIFIED -> ASSIGNED`.
- Hai lần chuyển trạng thái của `REQ-0002` do tài khoản mang `ROLE_COORDINATOR` thực hiện.
- Thời gian của `REQ-0002` theo thứ tự: tạo yêu cầu trước, xác minh sau, phân công cuối cùng; không có timestamp tương lai tại thời điểm migration.

Dữ liệu này chỉ dùng cho phát triển, demo và kiểm thử; không phải dữ liệu production.

## 8. Kiểm thử và bằng chứng

### 8.1 Smoke test không cần MySQL

Trên Windows PowerShell:

```powershell
.\mvnw.cmd clean test
```

Kết quả mong đợi:

- `DrssApplicationTests` chạy thành công.
- Kết thúc bằng `BUILD SUCCESS`.

Profile `test` loại trừ DataSource, JPA và Flyway để smoke test không phụ thuộc database.

### 8.2 Integration test với MySQL

Chuẩn bị một MySQL schema rỗng, đặt ba biến môi trường DB rồi chạy:

```powershell
.\mvnw.cmd clean verify "-Dspring.profiles.active=it"
```

`maven-failsafe-plugin` thực thi `DataFoundationIntegrationIT` trong hai phase `integration-test` và `verify`.

Integration test xác nhận:

- Flyway chạy thành công chính xác `V1` và `V2`.
- Có đủ 11 bảng nghiệp vụ và `flyway_schema_history`.
- Hibernate `ddl-auto=validate` khớp schema.
- BCrypt hash của tài khoản demo khớp mật khẩu `123456`.
- Có tài khoản coordinator.
- `REQ-0002` liên kết với `ASN-0001` và ở trạng thái `ASSIGNED`.
- Lịch sử `REQ-0002` đúng thứ tự và do người mang `ROLE_COORDINATOR` thực hiện.

Không kết luận data foundation đã PASS chỉ dựa trên `clean test`; kết quả đầy đủ cần `clean verify` với MySQL.

## 9. Nghĩa vụ của các task downstream

T1-07 cung cấp cấu trúc dữ liệu, mapping, repository cơ bản và dữ liệu kiểm thử. T1-07 chưa thực thi đầy đủ nghiệp vụ ở Service Layer.

Các task xử lý yêu cầu và điều phối phải bổ sung, tối thiểu:

- Dùng `@Transactional` cho thao tác thay đổi trạng thái hoặc phân công.
- Chỉ cho phép phân công yêu cầu đang ở trạng thái `VERIFIED`.
- Khi đổi trạng thái phải tạo `StatusHistory` tương ứng trong cùng transaction.
- Đồng bộ trạng thái yêu cầu và assignment theo state machine đã thống nhất.
- Xử lý optimistic locking thay vì ghi đè im lặng.
- Kiểm tra quyền theo role ở Service/Security Layer.
- Bổ sung unit/integration test cho cả happy path, trạng thái không hợp lệ và rollback.

Task upload media và notification phải dùng quan hệ entity hiện có, không tạo thêm cột foreign key trùng lặp dưới dạng ID thuần.

## 10. Checklist review cho PR phụ thuộc T1-07

Trước khi approve một PR liên quan dữ liệu, reviewer kiểm tra:

- [ ] Branch đã cập nhật từ `main` mới nhất.
- [ ] Không sửa lại `V1` hoặc `V2` ngoài một quyết định reset baseline đã được nhóm chấp thuận.
- [ ] Migration mới chạy được trên database rỗng và database đã có `V1/V2`.
- [ ] Không đưa secret hoặc `.env` vào Git.
- [ ] Dùng đúng role và trạng thái đã khóa.
- [ ] Thay đổi trạng thái/phân công nằm trong transaction.
- [ ] Có tạo status history khi nghiệp vụ yêu cầu.
- [ ] Không phá mapping `LAZY`, `@MapsId`, optimistic locking hoặc ràng buộc nullability.
- [ ] `clean test` PASS.
- [ ] `clean verify` với MySQL PASS khi PR có ảnh hưởng dữ liệu.
- [ ] PR ghi rõ migration, dữ liệu bị ảnh hưởng và cách rollback/khôi phục nếu có.

## 11. Lưu ý công cụ VS Code

Tại thời điểm 17/10/2026, extension `redhat.java@1.57.0` có lỗi chẩn đoán giả khi xử lý Lombok, thường báo:

```text
The Java feature 'Flexible Constructor Bodies' is only available with source level 25 and above
```

Nếu Maven Wrapper dùng Java 17 vẫn compile và test thành công, không sửa entity và không nâng source level lên Java 25 để né cảnh báo này.

Workaround tạm thời đã xác minh cho nhóm:

- Dùng `redhat.java@1.56.0`.
- Tắt Auto Update riêng extension Java cho đến khi lỗi upstream được sửa.
- Maven Wrapper và CI là nguồn xác nhận build chính thức.

Tham chiếu upstream: <https://github.com/redhat-developer/vscode-java/issues/4521>

## 12. Quy tắc cập nhật handoff

Chỉ cập nhật tài liệu này khi một PR đã được review và merge làm thay đổi contract nền tảng. Mỗi lần cập nhật phải ghi rõ trong PR:

- Contract nào thay đổi.
- Migration mới nào được thêm.
- Task downstream nào bị ảnh hưởng.
- Lệnh kiểm thử và kết quả.

Không tạo nhiều bản handoff song song cho cùng Data Foundation. File này là điểm bắt đầu; code và migration trên `main` là nguồn sự thật kỹ thuật cuối cùng.
