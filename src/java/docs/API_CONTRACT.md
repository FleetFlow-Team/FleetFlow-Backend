Maps API
Base URL: http://localhost:8080/FleetFlow/api/v1/maps
1. Geocode — Convert địa chỉ → tọa độ
- Dùng để lấy tọa độ của khách hang từ địa chỉ khách nhập (vi độ, kinh độ)
    - Lấy vĩ độ trước, kinh độ sau
GET /geocode?address={địa chỉ}
address: string
VD: http://localhost:8080/FleetFlow/api/v1/maps/geocode?address= 123 Nguyễn Huệ Quận 1 HCM
Response khi thành công (200 OK):
{
    "lat": 10.774339199999986,
    "lng": 106.70287209999998
}
Response khi lỗi (400):

{
  "error": "......"
}
2. Distance — Tính khoảng cách + validate > 20km
Dùng khi: Sau khi khách chọn điểm đón/trả → validate trước khi cho đặt xe (BR-01)
GET /distance?pickupLat={lat}&pickupLng={lng}&dropoffLat={lat}&dropoffLng={lng}
Input: 
pickupLat : double
pickupLng : double
dropoffLat : double
dropoffLng : double
VD: http://localhost:8080/FleetFlow/api/v1/maps/distance?pickupLat=10.776&pickupLng=106.700&dropoffLat=10.346&dropoffLng=107.084
Response khi hợp lệ (200 OK):

{
  "valid": true,
  "distanceKm": 94.6
}
Response khi không hợp lệ — < 20km (400):
{
  "valid": false,
  "error": "Khoảng cách quá ngắn (...km). FleetFlow chỉ phục vụ chuyến đi từ 20km trở lên."
}
3. Route — Lấy đường đi để vẽ lên bản đồ
Dùng khi: Hiển thị đường đi dự kiến từ điểm này đến điểm kia 
GET /route?fromLat={lat}&fromLng={lng}&toLat={lat}&toLng={lng}
Input: 
fromLat : double
fromLng : double
toLat : double
toLng : double
VD: http://localhost:8080/FleetFlow/api/v1/maps/route?fromLat=10.776&fromLng=106.700&toLat=10.346&toLng=107.084
Response khi thành công (200):

{
  "points": "encoded_polyline_string_here",
  "distanceMeters": 94600,
  "distanceKm": 94.6,
  "durationMs": 5400000,
  "durationMinutes": 90,
  "instructions": [...]
}

Tile API Key (để hiển thị bản đồ): 9c63b68ed14a6f2327e9f9fa0170ce81f6f5e0678471c64d
Docs VietMap SDK: https://maps.vietmap.vn/docs/sdk-web-gl/map/example-map/simple-map/
# FleetFlow – API Contract

> Base URL: `http://localhost:8080/fleetflow/api/v1`
> Auth: `Authorization: Bearer <token>`

---

## AUTH

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

---

## CUSTOMER

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

---

## DRIVER

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

---

## DISPATCHER

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

---

## ADMIN
1.Xem toàn bô document cua các driver
- Path: GET http://localhost:8080/FleetFlow/api/v1/admin/drivers/pending
- Input:
- Output:
{
    "success": true,
    "data": [
        {
            "accountId": 17,
            "fullName": "Tạ Văn Sơn",
            "email": "sonta17@example.com",
            "phone": "0912239087",
            "createdAt": "2026-04-18 08:51:00.0",
            "documents": [
                {
                    "docId": 15,
                    "docType": "NationalID",
                    "fileUrl": "https://storage.fleetflow.vn/kyc/driver17_NationalID.jpg",
                    "status": "Pending",
                    "uploadedAt": "2026-04-06 11:00:00.0"
                },
                {
                    "docId": 16,
                    "docType": "DriverLicense",
                    "fileUrl": "https://storage.fleetflow.vn/kyc/driver17_DriverLicense.jpg",
                    "status": "Pending",
                    "uploadedAt": "2026-04-06 11:00:00.0"
                }
            ]
        }
    ]
}
2.Chấp nhận đơn apply của driver
- Path: POST http://localhost:8080/FleetFlow/api/v1/admin/drivers/accountID/approve
- Input:
- Output:{
    "success": true,
    "message": "......."
3.Reject đơn apply của driver
- Path: POST http://localhost:8080/FleetFlow/api/v1/admin/drivers/accountID/reject
- Input:
- Output:
{
    "success": true,
    "message": "Từ chối hồ sơ tài xế thành công"
}

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

---

## TECHNICIAN

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output:

- Path:
- Input:
- Output: