# Test case: Lock/Unlock Driver & Dispatcher + Dispatcher xem danh sách tài xế

Base URL: `http://localhost:8080/FleetFlow`

## Chuẩn bị: lấy token

### Admin token
```
POST /api/v1/auth/login
Body (x-www-form-urlencoded): email=testadmin@fleetflow.local, password=Admin@123
```
→ copy `accessToken` (role Admin). Dùng token này cho toàn bộ test case (vì API dispatcher cũng cho phép Admin gọi).

Danh sách account có sẵn để test (từ DB dev hiện tại):
- Driver: accountId=3 (driver1@fleetflow.com), 4, 5
- Dispatcher: accountId=2 (dispatcher@fleetflow.com)
- Customer: accountId=6 (dùng để test guard sai role)

---

## A. Admin lock/unlock Driver

| # | Case | Request | Kết quả mong đợi |
|---|------|---------|----|
| A1 | Lock driver hợp lệ | `POST /api/v1/admin/drivers/3/lock` + Bearer admin token | 200, `{"success":true,"message":"Đã khóa tài khoản driver #3"}`. Driver #3 nhận notification `ACCOUNT_LOCKED` |
| A2 | Verify trạng thái đã đổi | `GET /api/v1/admin/drivers/all` | Trong `data[]`, phần tử accountId=3 có `"status":"LOCKED"` |
| A3 | Unlock driver | `POST /api/v1/admin/drivers/3/unlock` | 200, `{"success":true,"message":"Đã mở khóa tài khoản driver #3"}` |
| A4 | Verify trạng thái trở lại | `GET /api/v1/admin/drivers/all` | accountId=3 có `"status":"ACTIVE"` |
| A5 | Lock nhầm account không phải Driver | `POST /api/v1/admin/drivers/6/lock` (6 là Customer) | 400, `{"error":"Account #6 không phải Driver (đang là Customer)"}` |
| A6 | Lock account không tồn tại | `POST /api/v1/admin/drivers/9999/lock` | 400, `{"error":"Không tìm thấy account #9999"}` |
| A7 | Không có token | `POST /api/v1/admin/drivers/3/lock` (không header Authorization) | 401 |
| A8 | Token không phải Admin | Dùng token Driver/Customer/Dispatcher | 403, `{"error":"Chỉ tài khoản Admin được truy cập chức năng này"}` |

## B. Admin lock/unlock Dispatcher

| # | Case | Request | Kết quả mong đợi |
|---|------|---------|----|
| B1 | Xem danh sách dispatcher | `GET /api/v1/admin/dispatchers` | 200, `data[]` chứa accountId=2, status ACTIVE |
| B2 | Lock dispatcher hợp lệ | `POST /api/v1/admin/dispatchers/2/lock` | 200, thành công. Dispatcher #2 nhận notification `ACCOUNT_LOCKED` |
| B3 | Verify đã LOCKED | `GET /api/v1/admin/dispatchers` | accountId=2 có `"status":"LOCKED"` |
| B4 | Unlock dispatcher | `POST /api/v1/admin/dispatchers/2/unlock` | 200, thành công, status quay lại ACTIVE |
| B5 | Lock nhầm account không phải Dispatcher | `POST /api/v1/admin/dispatchers/3/lock` (3 là Driver) | 400, `{"error":"Account #3 không phải Dispatcher (đang là Driver)"}` |
| B6 | Không có token | (không header Authorization) | 401 |

## C. Dispatcher xem danh sách tài xế (tình trạng + số chuyến đã nhận)

| # | Case | Request | Kết quả mong đợi |
|---|------|---------|----|
| C1 | Lấy danh sách tài xế | `GET /api/v1/dispatcher/drivers` + Bearer token (Dispatcher hoặc Admin) | 200, `data[]` gồm từng tài xế: `accountId, driverId, fullName, phoneNumber, accountStatus, availabilityStatus, averageRating, acceptedTripCount` |
| C2 | Kiểm tra số chuyến đã nhận đúng | So khớp `acceptedTripCount` với số dòng `DriverJobBroadcast` có `Status='ACCEPTED'` của driver đó trong DB | Khớp (VD sau khi seed: driver #1 = 2, driver #2 = 1, driver #3 = 1) |
| C3 | Tài xế đang bị khóa vẫn hiển thị trong danh sách kèm accountStatus=LOCKED | Lock driver #3 (case A1) rồi gọi lại `GET /api/v1/dispatcher/drivers` | accountId=3 xuất hiện với `"accountStatus":"LOCKED"` (không bị ẩn khỏi danh sách, dispatcher cần thấy để biết không phân công) |
| C4 | Không có token / token role khác | Gọi không có Authorization, hoặc dùng token role Customer | 401 (không có token) hoặc 403 (role không phải Dispatcher/Admin) |

---

## Ghi chú quan trọng
- Cả 2 tính năng **chưa có UI** — đây là API thuần backend, test qua Postman/curl là đủ, phía frontend sẽ tự gọi và dựng giao diện sau.
- Endpoint `GET /api/v1/admin/drivers/all` là **mới thêm** (trước đây `AdminDriverController` chỉ có `/pending` cho duyệt hồ sơ).
- Trong lúc code, phát hiện cột `Driver.ApprovalStatus` được tham chiếu trong `DriverVerificationDAO.java` (chức năng duyệt hồ sơ driver có sẵn — `approveDriver`/`rejectDriver`) **không tồn tại trong DB thật** hiện tại — đây là bug cũ có sẵn, không phải do thay đổi lần này, nhưng đáng để bạn biết vì tính năng duyệt hồ sơ tài xế (BE-13/14/15) đang bị lỗi 500 nếu gọi thử.
