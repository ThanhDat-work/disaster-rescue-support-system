-- V2__Insert_Seed_Data.sql
-- Thêm Roles
-- Thêm Roles
INSERT INTO roles (role_name, description, created_at) VALUES 
('ROLE_ADMIN', 'Quản trị viên hệ thống', NOW()),
('ROLE_COORDINATOR', 'Điều phối viên', NOW()),
('ROLE_CITIZEN', 'Người dân', NOW()),
('ROLE_RESCUER', 'Đội cứu hộ', NOW());

-- Thêm Disaster Types
INSERT INTO disaster_types (disaster_code, disaster_name, description, created_at) VALUES 
('DIS-001', 'Bão lũ', 'Thiên tai ngập lụt', NOW()),
('DIS-002', 'Sạt lở', 'Sạt lở đất', NOW());

-- Thêm Users (Pass: 123456)
-- Dùng dữ liệu hư cấu: John Doe, Jane Smith
SET @admin_id = UUID_TO_BIN(UUID());
SET @citizen_id = UUID_TO_BIN(UUID());
SET @rescuer_id = UUID_TO_BIN(UUID());

INSERT INTO users (id, display_code, full_name, email, phone_number, password_hash, status, created_at, updated_at) VALUES 
(@admin_id, 'USR-0001', 'Admin Demo', 'admin@demo.com', '0123456789', '$2a$12$L7R2QyI1uQ9.iA.u.vX0/eYy/G6f0Xn6t4oQf9b0jT9m3nZJqfTzG', 'ACTIVE', NOW(), NOW()),
(@citizen_id, 'USR-0002', 'Nguyen Van Dan', 'dan@demo.com', '0987654321', '$2a$12$L7R2QyI1uQ9.iA.u.vX0/eYy/G6f0Xn6t4oQf9b0jT9m3nZJqfTzG', 'ACTIVE', NOW(), NOW()),
(@rescuer_id, 'USR-0003', 'Tran Cuu Ho', 'cuuho@demo.com', '0912345678', '$2a$12$L7R2QyI1uQ9.iA.u.vX0/eYy/G6f0Xn6t4oQf9b0jT9m3nZJqfTzG', 'ACTIVE', NOW(), NOW());

-- Thêm User Roles
INSERT INTO user_roles (user_id, role_id, assigned_at) VALUES
(@admin_id, (SELECT id FROM roles WHERE role_name = 'ROLE_ADMIN'), NOW()),
(@citizen_id, (SELECT id FROM roles WHERE role_name = 'ROLE_CITIZEN'), NOW()),
(@rescuer_id, (SELECT id FROM roles WHERE role_name = 'ROLE_RESCUER'), NOW());

-- Thêm Đội cứu hộ & Thành viên
SET @team_id = UUID_TO_BIN(UUID());
INSERT INTO rescue_teams (id, display_code, team_name, leader_name, contact_number, status, created_at, updated_at) VALUES 
(@team_id, 'TEAM-001', 'Đội Phản Ứng Nhanh', 'Tran Cuu Ho', '0912345678', 'AVAILABLE', NOW(), NOW());

INSERT INTO rescue_team_members (team_id, user_id, role, joined_at) VALUES
(@team_id, @rescuer_id, 'Leader', NOW());

-- Thêm Yêu cầu cứu nạn (Request) & Lịch sử
SET @req_id = UUID_TO_BIN(UUID());
INSERT INTO rescue_requests (id, display_code, user_id, disaster_type_id, urgency, status, address, latitude, longitude, description, created_at, updated_at, version) VALUES 
(@req_id, 'REQ-0001', @citizen_id, (SELECT id FROM disaster_types WHERE disaster_code = 'DIS-001'), 'HIGH', 'VERIFIED', '123 Đường Demo', 10.762622, 106.660172, 'Nước ngập sâu 1m', NOW(), NOW(), 0);

INSERT INTO status_histories (id, request_id, old_status, new_status, changed_by_user_id, created_at) VALUES
(UUID_TO_BIN(UUID()), @req_id, 'PENDING', 'VERIFIED', @admin_id, NOW());