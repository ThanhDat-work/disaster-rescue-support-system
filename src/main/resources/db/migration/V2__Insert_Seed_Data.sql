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

-- Thêm Users (Dùng ID cố định để tránh lỗi Flyway JDBC Session)
INSERT INTO users (id, display_code, full_name, email, phone_number, password_hash, status, created_at, updated_at) VALUES 
(UNHEX('11111111111111111111111111111111'), 'USR-0001', 'Admin Demo', 'admin@demo.com', '0123456789', '$2a$10$30qDd6yof/J3OeCsatpwpu6sANvnwSvL3UN1J.gwJ6WATtvTSMUU2', 'ACTIVE', NOW(), NOW()),
(UNHEX('22222222222222222222222222222222'), 'USR-0002', 'Dieu Phoi Demo', 'dieuphoi@demo.com', '0988888888', '$2a$10$30qDd6yof/J3OeCsatpwpu6sANvnwSvL3UN1J.gwJ6WATtvTSMUU2', 'ACTIVE', NOW(), NOW()),
(UNHEX('33333333333333333333333333333333'), 'USR-0003', 'Nguyen Van Dan', 'dan@demo.com', '0987654321', '$2a$10$30qDd6yof/J3OeCsatpwpu6sANvnwSvL3UN1J.gwJ6WATtvTSMUU2', 'ACTIVE', NOW(), NOW()),
(UNHEX('44444444444444444444444444444444'), 'USR-0004', 'Tran Cuu Ho', 'cuuho@demo.com', '0912345678', '$2a$10$30qDd6yof/J3OeCsatpwpu6sANvnwSvL3UN1J.gwJ6WATtvTSMUU2', 'ACTIVE', NOW(), NOW());

-- Thêm User Roles
INSERT INTO user_roles (user_id, role_id, assigned_at) VALUES
(UNHEX('11111111111111111111111111111111'), (SELECT id FROM roles WHERE role_name = 'ROLE_ADMIN'), NOW()),
(UNHEX('22222222222222222222222222222222'), (SELECT id FROM roles WHERE role_name = 'ROLE_COORDINATOR'), NOW()),
(UNHEX('33333333333333333333333333333333'), (SELECT id FROM roles WHERE role_name = 'ROLE_CITIZEN'), NOW()),
(UNHEX('44444444444444444444444444444444'), (SELECT id FROM roles WHERE role_name = 'ROLE_RESCUER'), NOW());

-- Thêm Đội cứu hộ & Thành viên
INSERT INTO rescue_teams (id, display_code, team_name, leader_name, contact_number, status, created_at, updated_at) VALUES 
(UNHEX('55555555555555555555555555555555'), 'TEAM-001', 'Đội Phản Ứng Nhanh', 'Tran Cuu Ho', '0912345678', 'AVAILABLE', NOW(), NOW());

INSERT INTO rescue_team_members (team_id, user_id, role, joined_at) VALUES
(UNHEX('55555555555555555555555555555555'), UNHEX('44444444444444444444444444444444'), 'Leader', NOW());

-- Thêm Yêu cầu cứu nạn REQ-0001 (Chỉ VERIFIED, chưa phân công)
INSERT INTO rescue_requests (id, display_code, user_id, disaster_type_id, urgency, status, address, latitude, longitude, description, created_at, updated_at, version) VALUES 
(UNHEX('66666666666666666666666666666666'), 'REQ-0001', UNHEX('33333333333333333333333333333333'), (SELECT id FROM disaster_types WHERE disaster_code = 'DIS-001'), 'HIGH', 'VERIFIED', '123 Đường Demo', 10.762622, 106.660172, 'Nước ngập sâu 1m', NOW(), NOW(), 0);

INSERT INTO status_histories (id, request_id, old_status, new_status, changed_by_user_id, created_at) VALUES
(UNHEX('77777777777777777777777777777777'), UNHEX('66666666666666666666666666666666'), 'PENDING', 'VERIFIED', UNHEX('22222222222222222222222222222222'), NOW());

-- Thêm Yêu cầu cứu nạn REQ-0002 (Đã ASSIGNED)
INSERT INTO rescue_requests (id, display_code, user_id, disaster_type_id, urgency, status, address, latitude, longitude, description, created_at, updated_at, version) VALUES 
(UNHEX('88888888888888888888888888888888'), 'REQ-0002', UNHEX('33333333333333333333333333333333'), (SELECT id FROM disaster_types WHERE disaster_code = 'DIS-002'), 'CRITICAL', 'ASSIGNED', '456 Đường Sạt Lở', 10.8888, 106.9999, 'Sạt lở cắt ngang đường', NOW(), NOW(), 0);

-- Lịch sử cho REQ-0002 (Do Coordinator thực hiện)
INSERT INTO status_histories (id, request_id, old_status, new_status, changed_by_user_id, created_at) VALUES
(UNHEX('99999999999999999999999999999999'), UNHEX('88888888888888888888888888888888'), 'PENDING', 'VERIFIED', UNHEX('22222222222222222222222222222222'), NOW()),
(UNHEX('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'), UNHEX('88888888888888888888888888888888'), 'VERIFIED', 'ASSIGNED', UNHEX('22222222222222222222222222222222'), DATE_ADD(NOW(), INTERVAL 5 MINUTE));

-- Thêm Phân công nhiệm vụ (Gắn với REQ-0002)
INSERT INTO rescue_assignments (id, display_code, request_id, team_id, status, assigned_at, version, created_at, updated_at) VALUES
(UNHEX('BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB'), 'ASN-0001', UNHEX('88888888888888888888888888888888'), UNHEX('55555555555555555555555555555555'), 'ASSIGNED', NOW(), 0, NOW(), NOW());