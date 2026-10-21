-- V4__Add_Admin_Statistics_Index.sql
-- Bổ sung index phục vụ thống kê yêu cầu theo mức độ khẩn cấp và thời gian.

CREATE INDEX idx_req_urgency_time
    ON rescue_requests(urgency, created_at);