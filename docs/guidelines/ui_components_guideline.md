# DRSS - Hướng dẫn sử dụng UI Components
**Người soạn:** Phan Nhật Vy (UI/UX)
**Dự án:** Disaster Rescue Support System (DRSS)

Tài liệu này cung cấp các đoạn mã HTML/Thymeleaf chuẩn mực để các thành viên (Backend) tái sử dụng giao diện, đảm bảo tính đồng bộ, hiện đại (Glassmorphism) và chuẩn Responsive trên toàn hệ thống.

---

## 1. Tích hợp Khung trang gốc (Layout & Navbar)
Tất cả các trang HTML mới đều phải kế thừa `main-layout.html`. Thanh điều hướng (Navbar) sẽ tự động hiển thị và phân quyền theo tác nhân đang đăng nhập.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layouts/main-layout}">
<head>
    <title>Tên Trang Của Bạn | DRSS</title>
</head>
<body>
    <div layout:fragment="content">
        <!-- Viết mã HTML riêng của trang vào đây -->
        
        <div class="card mb-4">
            <div class="card-header">
                Tiêu đề nội dung
            </div>
            <div class="card-body">
                Nội dung bên trong...
            </div>
        </div>
        
    </div>
</body>
</html>
```

## 2. Nút bấm tương tác (Buttons)
Các nút bấm đã được cấu hình hiệu ứng nảy (scale click) và bóng đổ mềm trong `style-tokens.css`. Tránh tự viết CSS inline cho nút.

*   **Hành động chính (Lưu, Xác nhận, Gửi):**
    ```html
    <button type="submit" class="btn btn-primary">Xác nhận gửi</button>
    ```
*   **Hành động phụ (Hủy, Đóng):**
    ```html
    <button type="button" class="btn btn-outline-secondary">Hủy bỏ</button>
    ```
*   **Hành động cảnh báo/Hủy bỏ (Từ chối, Xóa):**
    ```html
    <button type="button" class="btn btn-outline-danger">Từ chối</button>
    ```

## 3. Form và Bắt lỗi (Validation State)
Form đã được thiết kế tối giản. Khi backend trả về lỗi, hãy thêm class `is-invalid` vào thẻ `input`/`select` và cung cấp thẻ `div.invalid-feedback` ngay bên dưới.

```html
<div class="mb-3">
    <label for="disasterType" class="form-label">Loại thiên tai <span class="text-danger">*</span></label>
    <select class="form-select is-invalid" id="disasterType" name="disasterType">
        <option value="" disabled selected>-- Chọn một --</option>
        <option value="FLOOD">Bão lụt</option>
    </select>
    <div class="invalid-feedback">
        Vui lòng chọn loại thiên tai.
    </div>
</div>
```

## 4. Nhãn trạng thái (Status Badges)
Sử dụng các class và biến màu Semantic để hiển thị trạng thái của các Yêu cầu cứu nạn. Đã được bo tròn dạng Pill mềm mại.

*   **PENDING (Chờ xử lý - Vàng):**
    ```html
    <span class="badge" style="background-color: var(--urgency-medium); color: #fff;">PENDING</span>
    ```
*   **IN_PROGRESS (Đang xử lý - Cam):**
    ```html
    <span class="badge" style="background-color: var(--urgency-high); color: #fff;">IN_PROGRESS</span>
    ```
*   **CRITICAL (Khẩn cấp - Đỏ):**
    ```html
    <span class="badge" style="background-color: var(--urgency-critical); color: #fff;">CRITICAL</span>
    ```
*   **RESCUED/RESOLVED (Đã giải quyết - Xanh lá):**
    ```html
    <span class="badge" style="background-color: var(--urgency-low); color: #fff;">RESCUED</span>
    ```

## 5. Bảng dữ liệu (Data Table)
Luôn đặt table vào trong `div.table-responsive` và bọc trong một `.card` để tránh gãy layout trên giao diện điện thoại.

```html
<div class="card">
    <div class="table-responsive">
        <table class="table table-hover align-middle">
            <thead class="table-light">
                <tr>
                    <th scope="col">Mã yêu cầu</th>
                    <th scope="col">Người gửi</th>
                    <th scope="col">Trạng thái</th>
                    <th scope="col">Hành động</th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td>REQ-0001</td>
                    <td>Nguyễn Văn A</td>
                    <td>
                        <span class="badge" style="background-color: var(--urgency-medium); color: #fff;">PENDING</span>
                    </td>
                    <td>
                        <button class="btn btn-sm btn-outline-danger">Từ chối</button>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</div>
```