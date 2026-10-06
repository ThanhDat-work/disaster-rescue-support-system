-- V2__Insert_Seed_Data.sql
-- Thêm Roles
INSERT INTO roles (role_name, description, created_at) VALUES 
('ROLE_ADMIN', 'Quản trị viên hệ thống', NOW()),
('ROLE_COORDINATOR', 'Điều phối viên', NOW()),
('ROLE_CITIZEN', 'Người dân', NOW());

-- Thêm Disaster Types
INSERT INTO disaster_types (disaster_code, disaster_name, description, created_at) VALUES 
('DIS-001', 'Bão lũ', 'Thiên tai liên quan đến mưa bão, ngập lụt', NOW()),
('DIS-002', 'Sạt lở đất', 'Sạt lở do mưa lớn tại vùng núi', NOW());

-- Thêm Users (Mật khẩu mặc định có thể là hash của '123456')
-- Note: id được sinh bằng UUID_TO_BIN(UUID()) trong MySQL 8.x
INSERT INTO users (id, display_code, full_name, email, phone_number, password_hash, status, created_at, updated_at) VALUES 
(UUID_TO_BIN(UUID()), 'USR-0001', 'Admin System', 'admin@drss.com', '0123456789', 'hashed_pwd_here', 'ACTIVE', NOW(), NOW()),
(UUID_TO_BIN(UUID()), 'USR-0002', 'Bui Duc Duy ', 'ducduybui247@gmail.com', '0345675561', 'hashed_pwd_here', 'ACTIVE', NOW(), NOW());