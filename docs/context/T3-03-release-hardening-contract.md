# T3-03 Release Hardening Contract

- Status: Locked for implementation
- Base commit: `b602834`
- Owner: Đỗ Thành Đạt
- Reviewer: Bùi Đức Duy

## 1. Authoritative scope

Contract bám theo báo cáo Word đã chốt và implementation đã merge của T2-01, T2-02, T2-03, T2-04, T2-05. Nội dung Notion chưa duyệt không tự động mở rộng phạm vi.

## 2. Coordinator tracking

- Route: `GET /coordinator/tracking`.
- Chỉ `ROLE_COORDINATOR` truy cập được nhờ `/coordinator/**` trong security chain.
- Hiển thị mọi trạng thái: `PENDING`, `VERIFIED`, `ASSIGNED`, `IN_PROGRESS`, `RESCUED`, `CLOSED`, `REJECTED`, `CANCELLED`.
- Bộ lọc: keyword, status, urgency, disaster type, rescue team, from/to date.
- Kết quả có pagination và assignment gần nhất; tải assignment theo batch, không query từng dòng.
- Queue `/coordinator/pending` vẫn chỉ dành cho xác minh PENDING.

## 3. Close transition

- Route: `POST /coordinator/requests/{requestId}/close`.
- CSRF bắt buộc.
- Điều kiện đồng thời:
  - request hiện là `RESCUED`;
  - expected version khớp;
  - assignment gần nhất tồn tại và là `COMPLETED`;
  - assignment có `completion_time`;
  - báo cáo hoàn thành trong `assignment.notes` không rỗng;
  - ghi chú đóng dài 10–500 ký tự sau trim.
- Thành công:
  - request chuyển `RESCUED -> CLOSED`;
  - tạo đúng một `status_histories` với người thao tác và ghi chú đóng;
  - tạo notification cho chủ request trong cùng transaction;
  - realtime chỉ được dispatch sau commit theo contract T2-05.
- `CLOSED` là terminal; repeated close, stale version và forged ID đều bị chặn.

## 4. Concurrency and transaction

- Khóa pessimistic request trước khi kiểm tra state/version để tuần tự hóa double submit trên cùng request.
- `@Version` vẫn là lớp bảo vệ stale form.
- History, request status và notification thuộc cùng transaction.
- Nếu flush/history/notification lỗi, transaction rollback; không có realtime delivery vì listener dùng `AFTER_COMMIT`.

## 5. Existing decisions preserved

- Citizen chỉ hủy ở `PENDING` hoặc `VERIFIED`.
- Assignment không tự đổi team sang `BUSY`.
- Không thêm reopen flow.
- Không đưa STOMP bell/reconnect UI của T3-02 vào checkpoint này.

## 6. Deferred to checkpoint 03.3

- Audit forged IDs/ownership/team recipient trên toàn bộ flow.
- Rollback matrix cho assignment/state/history/notification/media.
- Double submit/concurrent assignment/transition integration tests.
- Audit secret, sensitive logging, cookie flags và SD-09 as-built.
