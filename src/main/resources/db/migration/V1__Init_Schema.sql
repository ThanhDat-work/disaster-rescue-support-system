-- V1__Init_Schema.sql
-- Tạo 11 bảng, PK, FK, Constraints cho hệ thống DRSS

CREATE TABLE users (
    id BINARY(16) PRIMARY KEY,
    display_code VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone_number VARCHAR(15) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'BANNED')),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE roles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at DATETIME(6) NOT NULL
);

CREATE TABLE user_roles (
    user_id BINARY(16) NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE disaster_types (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    disaster_code VARCHAR(20) NOT NULL UNIQUE,
    disaster_name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL
);

CREATE TABLE rescue_requests (
    id BINARY(16) PRIMARY KEY,
    display_code VARCHAR(20) NOT NULL UNIQUE,
    user_id BINARY(16) NOT NULL,
    disaster_type_id BIGINT UNSIGNED NOT NULL,
    urgency VARCHAR(32) NOT NULL CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'VERIFIED', 'ASSIGNED', 'IN_PROGRESS', 'RESCUED', 'CLOSED', 'REJECTED', 'CANCELLED')),
    address VARCHAR(500),
    latitude DECIMAL(9,6) CHECK (latitude BETWEEN -90 AND 90),
    longitude DECIMAL(10,6) CHECK (longitude BETWEEN -180 AND 180),
    description TEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (disaster_type_id) REFERENCES disaster_types(id)
);

CREATE TABLE rescue_teams (
    id BINARY(16) PRIMARY KEY,
    display_code VARCHAR(20) NOT NULL UNIQUE,
    team_name VARCHAR(100) NOT NULL UNIQUE,
    leader_name VARCHAR(100) NOT NULL,
    contact_number VARCHAR(15) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('AVAILABLE', 'BUSY', 'OFFLINE')),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE rescue_team_members (
    team_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    role VARCHAR(50) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6),
    PRIMARY KEY (team_id, user_id),
    FOREIGN KEY (team_id) REFERENCES rescue_teams(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE rescue_assignments (
    id BINARY(16) PRIMARY KEY,
    display_code VARCHAR(20) NOT NULL UNIQUE,
    request_id BINARY(16) NOT NULL,
    team_id BINARY(16) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('ASSIGNED', 'ACCEPTED', 'COMPLETED', 'CANCELLED')),
    assigned_at DATETIME(6) NOT NULL,
    completion_time DATETIME(6),
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (request_id) REFERENCES rescue_requests(id),
    FOREIGN KEY (team_id) REFERENCES rescue_teams(id)
);

CREATE TABLE media_assets (
    id BINARY(16) PRIMARY KEY,
    request_id BINARY(16) NOT NULL,
    uploader_user_id BINARY(16) NOT NULL,
    public_id VARCHAR(255) NOT NULL UNIQUE,
    secure_url VARCHAR(500) NOT NULL,
    format VARCHAR(20) NOT NULL,
    width INT CHECK (width >= 0),
    height INT CHECK (height >= 0),
    bytes INT CHECK (bytes >= 0),
    created_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    FOREIGN KEY (request_id) REFERENCES rescue_requests(id),
    FOREIGN KEY (uploader_user_id) REFERENCES users(id)
);

CREATE TABLE status_histories (
    id BINARY(16) PRIMARY KEY,
    request_id BINARY(16) NOT NULL,
    old_status VARCHAR(32) NOT NULL,
    new_status VARCHAR(32) NOT NULL,
    changed_by_user_id BINARY(16) NOT NULL,
    notes TEXT,
    created_at DATETIME(6) NOT NULL,
    FOREIGN KEY (request_id) REFERENCES rescue_requests(id),
    FOREIGN KEY (changed_by_user_id) REFERENCES users(id)
);

CREATE TABLE notifications (
    id BINARY(16) PRIMARY KEY,
    recipient_user_id BINARY(16) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    reference_type VARCHAR(50),
    reference_id BINARY(16),
    is_read BOOLEAN DEFAULT FALSE,
    read_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    FOREIGN KEY (recipient_user_id) REFERENCES users(id)
);