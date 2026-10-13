-- V2__Insert_Seed_Data.sql
-- Thêm Roles
INSERT INTO roles (role_name, description, created_at) VALUES 
('ROLE_ADMIN', 'Quản trị viên hệ thống', NOW()),
('ROLE_COORDINATOR', 'Điều phối viên', NOW()),
('ROLE_CITIZEN', 'Người dân', NOW()),
('ROLE_RESCUER', 'Đội cứu hộ', NOW());

-- Thêm Disaster Types
INSERT INTO disaster_types (disaster_code, disaster_name, description, is_active, created_at) VALUES 
('DIS-001', 'Bão lũ', 'Thiên tai ngập lụt', TRUE, NOW()),
('DIS-002', 'Sạt lở', 'Sạt lở đất', TRUE, NOW());

-- Khởi tạo UUIDs
SET @admin_id = UUID_TO_BIN(UUID());
SET @coordinator_id = UUID_TO_BIN(UUID());
SET @citizen_id = UUID_TO_BIN(UUID());
SET @rescuer_id = UUID_TO_BIN(UUID());

-- Thêm Users (Mật khẩu chuẩn BCrypt cho chuỗi '123456')
INSERT INTO users (id, display_code, full_name, email, phone_number, password_hash, status, created_at, updated_at) VALUES 
(@admin_id, 'USR-0001', 'Admin Demo', 'admin@demo.com', '0123456789', '$2a$12$vwf5v9xS7GZ4v62yRzJv7eRz6G9h8hY1gHhH3Y/LhE.B9x7.j1T3a', 'ACTIVE', NOW(), NOW()),
(@coordinator_id, 'USR-0002', 'Dieu Phoi Demo', 'dieuphoi@demo.com', '0988888888', '$2a$12$vwf5v9xS7GZ4v62yRzJv7eRz6G9h8hY1gHhH3Y/LhE.B9x7.j1T3a', 'ACTIVE', NOW(), NOW()),
(@citizen_id, 'USR-0003', 'Nguyen Van Dan', 'dan@demo.com', '0987654321', '$2a$12$vwf5v9xS7GZ4v62yRzJv7eRz6G9h8hY1gHhH3Y/LhE.B9x7.j1T3a', 'ACTIVE', NOW(), NOW()),
(@rescuer_id, 'USR-0004', 'Tran Cuu Ho', 'cuuho@demo.com', '0912345678', '$2a$12$vwf5v9xS7GZ4v62yRzJv7eRz6G9h8hY1gHhH3Y/LhE.B9x7.j1T3a', 'ACTIVE', NOW(), NOW());

-- Thêm User Roles
INSERT INTO user_roles (user_id, role_id, assigned_at) VALUES
(@admin_id, (SELECT id FROM roles WHERE role_name = 'ROLE_ADMIN'), NOW()),
(@coordinator_id, (SELECT id FROM roles WHERE role_name = 'ROLE_COORDINATOR'), NOW()),
(@citizen_id, (SELECT id FROM roles WHERE role_name = 'ROLE_CITIZEN'), NOW()),
(@rescuer_id, (SELECT id FROM roles WHERE role_name = 'ROLE_RESCUER'), NOW());

-- Thêm Đội cứu hộ & Thành viên
SET @team_id = UUID_TO_BIN(UUID());
INSERT INTO rescue_teams (id, display_code, team_name, leader_name, contact_number, status, created_at, updated_at) VALUES 
(@team_id, 'TEAM-001', 'Đội Phản Ứng Nhanh', 'Tran Cuu Ho', '0912345678', 'AVAILABLE', NOW(), NOW());

INSERT INTO rescue_team_members (team_id, user_id, role, joined_at) VALUES
(@team_id, @rescuer_id, 'Leader', NOW());

-- Thêm Yêu cầu cứu nạn
SET @req_id = UUID_TO_BIN(UUID());
INSERT INTO rescue_requests (id, display_code, user_id, disaster_type_id, urgency, status, address, latitude, longitude, description, created_at, updated_at, version) VALUES 
(@req_id, 'REQ-0001', @citizen_id, (SELECT id FROM disaster_types WHERE disaster_code = 'DIS-001'), 'HIGH', 'VERIFIED', '123 Đường Demo', 10.762622, 106.660172, 'Nước ngập sâu 1m', NOW(), NOW(), 0);

-- Thêm Lịch sử (Điều phối viên xác minh)
INSERT INTO status_histories (id, request_id, old_status, new_status, changed_by_user_id, created_at) VALUES
(UUID_TO_BIN(UUID()), @req_id, 'PENDING', 'VERIFIED', @coordinator_id, NOW());

-- Thêm Phân công nhiệm vụ (Assignments)
INSERT INTO rescue_assignments (id, display_code, request_id, team_id, status, assigned_at, version, created_at, updated_at) VALUES
(UUID_TO_BIN(UUID()), 'ASN-0001', @req_id, @team_id, 'ASSIGNED', NOW(), 0, NOW(), NOW());