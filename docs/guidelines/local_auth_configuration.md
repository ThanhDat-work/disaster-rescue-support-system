# Local Authentication Configuration

## Mục đích

Tài liệu này hướng dẫn thiết lập biến môi trường local cho database,
JWT, OTP và Gmail của DRSS mà không đưa secret vào Git.

## Quy tắc bảo mật

- Không commit `scripts/local-env.ps1`.
- Không gửi file này qua nhóm, email, Notion, Issue hoặc Pull Request.
- Không ghi secret vào log, ảnh chụp màn hình hoặc tài liệu bàn giao.
- Mỗi thành viên tự tạo JWT secret và OTP HMAC secret riêng.
- Hai secret JWT và OTP phải khác nhau và có ít nhất 32 byte.
- Không sử dụng mật khẩu đăng nhập Gmail làm SMTP password.
- Môi trường triển khai phải dùng nơi lưu secret của nền tảng hosting.

## Thiết lập lần đầu

Từ thư mục gốc repository:

    Copy-Item `
        "scripts/local-env.ps1.example" `
        "scripts/local-env.ps1"

Mở `scripts/local-env.ps1` và thay toàn bộ placeholder bằng cấu hình
local của cá nhân.

File thật đã được `.gitignore` loại trừ.

## Sinh secret

Chạy trong PowerShell:

    function New-DrssSecret {
        $bytes = [byte[]]::new(32)
        [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
        return [Convert]::ToBase64String($bytes)
    }

    $jwtSecret = New-DrssSecret
    $otpSecret = New-DrssSecret

Không đưa kết quả của hai biến này vào Git hoặc nội dung bàn giao.

## Cấu hình Gmail

- Dùng tài khoản Gmail dành cho dự án nếu có thể.
- Bật xác minh hai bước.
- Tạo Gmail App Password.
- `MAIL_USERNAME` và `MAIL_FROM` là địa chỉ Gmail gửi OTP.
- `MAIL_APP_PASSWORD` là App Password, không phải mật khẩu Gmail.

## Nạp biến môi trường

Mỗi khi mở PowerShell mới, chạy:

    . .\scripts\local-env.ps1

Biến chỉ tồn tại trong tiến trình PowerShell hiện tại và các tiến trình
được khởi chạy từ nó.

## Kiểm tra an toàn

Chỉ kiểm tra biến đã tồn tại, không in giá trị:

    $requiredVariables = @(
        'DB_URL',
        'DB_USERNAME',
        'DB_PASSWORD',
        'MAIL_USERNAME',
        'MAIL_APP_PASSWORD',
        'MAIL_FROM',
        'JWT_SECRET',
        'OTP_HMAC_SECRET',
        'APP_BASE_URL',
        'AUTH_COOKIE_SECURE'
    )

    foreach ($name in $requiredVariables) {
        $state = if (Test-Path "Env:$name") { 'SET' } else { 'MISSING' }
        Write-Host "$name = $state"
    }

## Chạy ứng dụng

Sau khi nạp biến:

    .\mvnw.cmd spring-boot:run

Thiết lập local sử dụng:

- `APP_BASE_URL=http://localhost:8080`
- `AUTH_COOKIE_SECURE=false`

Môi trường HTTPS triển khai thật phải dùng:

- `AUTH_COOKIE_SECURE=true`

## Kiểm thử

Profile `test` và `it` sử dụng secret giả nằm trong test resources.
CI không cần Gmail hoặc secret production để chạy automated tests.

## Bàn giao cho thành viên khác

Chỉ bàn giao:

- Danh sách tên biến trong `.env.example`.
- File `scripts/local-env.ps1.example`.
- Tài liệu này.
- Hướng dẫn tự tạo secret và Gmail App Password.

Không bàn giao giá trị secret local của bất kỳ thành viên nào.
