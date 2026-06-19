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
Customer tạo đơn đặt xe
- Path: POST http://localhost:8080/FleetFlow/api/v1/bookings
- Input:
{
  "customerId": 1,
  "vehicleId": 1,
  "bookingType": "DISTANCE",
  "tripDirection": "ONE_WAY",
  "pickupAddress": "123 Nguyễn Huệ Quận 1 HCM",
  "pickupLat": 10.776,
  "pickupLng": 106.700,
  "dropoffAddress": "Vũng Tàu",
  "dropoffLat": 10.346,
  "dropoffLng": 107.084,
  "departureTime": "2026-07-20T08:00:00"
}
- Output:
{
    "success": true,
    "bookingId": 22,
    "status": "PENDING",
    "message": "Đặt xe thành công, chờ Dispatcher duyệt"
}
Xem chi tiết đơn booking của customer
- Path: GET http://localhost:8080/FleetFlow/api/v1/bookings/22
- Input:
- Output:
{
    "bookingId": 22,
    "customerId": 1,
    "vehicleId": 1,
    "bookingType": "DISTANCE",
    "tripDirection": "ONE_WAY",
    "status": "PENDING",
    "detail": {
        "pickupAddress": string,
        "pickupLat": 10.776000,
        "pickupLng": 106.700000,
        "dropoffAddress": string,
        "dropoffLat": 10.346000,
        "dropoffLng": 107.084000,
        "departureTime": "2026-07-20 08:00:00.0"
    }
}
---------------------------------------------------------------------------------
                                BỔ SUNG LUỒNG BOOKING NGÀY 19/6/2026
Tạo đơn đặt xe — DISTANCE ROUND_TRIP
fe làm them giao điện để customer nhập them chiều ii và về

Path: POST http://localhost:8080/FleetFlow/api/v1/bookings
Input:

json{
  "customerId": 1,
  "vehicleId": 1,
  "bookingType": "DISTANCE",
  "tripDirection": "ROUND_TRIP",
  "pickupAddress": "123 Nguyễn Huệ Quận 1 HCM",
  "pickupLat": 10.776,
  "pickupLng": 106.700,
  "dropoffAddress": "Vũng Tàu",
  "dropoffLat": 10.346,
  "dropoffLng": 107.084,
  "departureTime": "2026-07-20T08:00:00",
  "returnTime": "2026-07-20T18:00:00",
  "returnPickupAddress": "Vũng Tàu",
  "returnPickupLat": 10.346,
  "returnPickupLng": 107.084,
  "returnDropoffAddress": "123 Nguyễn Huệ Quận 1 HCM",
  "returnDropoffLat": 10.776,
  "returnDropoffLng": 106.700
}

Output:

{
  "success": true,
  "bookingId": 23,
  "status": "PENDING",
  "message": "Đặt xe thành công, chờ Dispatcher duyệt"
}
Tạo đơn đặt xe — HOURLY

Path: POST http://localhost:8080/FleetFlow/api/v1/bookings
Input:

{
  "customerId": 1,
  "vehicleId": 1,
  "bookingType": "HOURLY",
  "tripDirection": "ONE_WAY",
  "pickupAddress": "123 Nguyễn Huệ Quận 1 HCM",
  "departureTime": "2026-07-20T08:00:00",
  "durationHours": 4
}

Output:

json{
  "success": true,
  "bookingId": 24,
  "status": "PENDING",
  "message": "Đặt xe thành công, chờ Dispatcher duyệt"
}
Tạo đơn đặt xe — DAILY

Path: POST http://localhost:8080/FleetFlow/api/v1/bookings
Input:

json{
  "customerId": 1,
  "vehicleId": 1,
  "bookingType": "DAILY",
  "tripDirection": "ONE_WAY",
  "pickupAddress": "123 Nguyễn Huệ Quận 1 HCM",
  "departureTime": "2026-07-20T08:00:00",
  "durationDays": 2
}

Output:

json{
  "success": true,
  "bookingId": 25,
  "status": "PENDING",
  "message": "Đặt xe thành công, chờ Dispatcher duyệt"
}

Tạo đơn đặt xe — INNER_CITY

Path: POST http://localhost:8080/FleetFlow/api/v1/bookings
Input:

json{
  "customerId": 1,
  "vehicleId": 1,
  "bookingType": "INNER_CITY",
  "tripDirection": "ONE_WAY",
  "pickupAddress": "123 Nguyễn Huệ Quận 1 HCM",
  "pickupLat": 10.776,
  "pickupLng": 106.700,
  "dropoffAddress": "Sân bay Tân Sơn Nhất",
  "dropoffLat": 10.818,
  "dropoffLng": 106.652,
  "departureTime": "2026-07-20T08:00:00"
}

Output:

json{
  "success": true,
  "bookingId": 26,
  "status": "PENDING",
  "message": "Đặt xe thành công, chờ Dispatcher duyệt"
}

Tạo đơn đặt xe — INTER_CITY

Path: POST http://localhost:8080/FleetFlow/api/v1/bookings
Input:

json{
  "customerId": 1,
  "vehicleId": 1,
  "bookingType": "INTER_CITY",
  "tripDirection": "ONE_WAY",
  "pickupAddress": "Bến xe Miền Đông HCM",
  "pickupLat": 10.814,
  "pickupLng": 106.711,
  "dropoffAddress": "Bến xe Đà Lạt",
  "dropoffLat": 11.940,
  "dropoffLng": 108.458,
  "departureTime": "2026-07-20T08:00:00"
}

Output:

{
  "success": true,
  "bookingId": 27,
  "status": "PENDING",
  "message": "Đặt xe thành công, chờ Dispatcher duyệt"
}
Xem chi tiết booking

Path: GET http://localhost:8080/FleetFlow/api/v1/bookings/{bookingId}
Output (ONE_WAY):

json{
  "bookingId": 22,
  "customerId": 1,
  "vehicleId": 1,
  "bookingType": "DISTANCE",
  "tripDirection": "ONE_WAY",
  "status": "PENDING",
  "detail": {
    "pickupAddress": "123 Nguyễn Huệ Quận 1 HCM",
    "pickupLat": 10.776000,
    "pickupLng": 106.700000,
    "dropoffAddress": "Vũng Tàu",
    "dropoffLat": 10.346000,
    "dropoffLng": 107.084000,
    "distanceKm": 120.5,
    "departureTime": "2026-07-20 08:00:00.0"
  }
}

Output (ROUND_TRIP):

json{
  "bookingId": 23,
  "customerId": 1,
  "vehicleId": 1,
  "bookingType": "DISTANCE",
  "tripDirection": "ROUND_TRIP",
  "status": "PENDING",
  "detail": {
    "pickupAddress": "123 Nguyễn Huệ Quận 1 HCM",
    "pickupLat": 10.776000,
    "pickupLng": 106.700000,
    "dropoffAddress": "Vũng Tàu",
    "dropoffLat": 10.346000,
    "dropoffLng": 107.084000,
    "distanceKm": 120.5,
    "departureTime": "2026-07-20 08:00:00.0",
    "returnTime": "2026-07-20 18:00:00.0",
    "returnPickupAddress": "Vũng Tàu",
    "returnPickupLat": 10.346000,
    "returnPickupLng": 107.084000,
    "returnDropoffAddress": "123 Nguyễn Huệ Quận 1 HCM",
    "returnDropoffLat": 10.776000,
    "returnDropoffLng": 106.700000,
    "returnDistanceKm": 118.0
  }
}
---------------------------------------------------------------------------
Xem lịch sử booking
- Path: GET http://localhost:8080/FleetFlow/api/v1/customer/bookings?customerId=1
- Input:
    customerId
- Output:
{
    "success": true,
    "data": [
        {
            "bookingId": 21,
            "vehicleId": 1,
            "vehicleName": "Toyota Vios",
            "licensePlate": "51B-111.37",
            "bookingType": "DISTANCE",
            "tripDirection": "ONE_WAY",
            "status": "PENDING",
            "pickupAddress": string,
            "dropoffAddress": string,
            "departureTime": "2026-07-15 08:00:00.0",
            "distanceKm": 94.60,
            "createdAt": "2026-06-10 20:59:45.463"
        },
        {
            "bookingId": 13,
            "vehicleId": 1,
            "vehicleName": "Toyota Vios",
            "licensePlate": "51B-111.37",
            "bookingType": "Distance",
            "tripDirection": "OneWay",
            "status": "Completed",
            "pickupAddress": "82 Lê Lợi, Quận 3, TP.HCM",
            "dropoffAddress": "193 Nguyễn Thị Minh Khai, Quận 10, TP.HCM",
            "departureTime": "2026-05-15 10:05:00.0",
            "distanceKm": 258.40,
            "createdAt": "2026-05-14 10:05:00.0"
        },
        {
            "bookingId": 1,
            "vehicleId": 1,
            "vehicleName": "Toyota Vios",
            "licensePlate": "51B-111.37",
            "bookingType": "Distance",
            "tripDirection": "OneWay",
            "status": "Completed",
            "pickupAddress": "38 Lê Lợi, Quận 3, TP.HCM",
            "dropoffAddress": "149 Nguyễn Thị Minh Khai, Quận 10, TP.HCM",
            "departureTime": "2026-05-03 08:05:00.0",
            "distanceKm": 50.80,
            "createdAt": "2026-05-02 08:05:00.0"
        }
    ]
}
Tính cước phí chuyến đi
- Path: POST http://localhost:8080/FleetFlow/api/v1/customer/bookings/check-price
- Input:
{
  "vehicleId": 1,
  "bookingType": "DISTANCE",
  "tripDirection": "ONE_WAY",
  "distanceKm": 120.5,
  "durationHours": 0,
  "durationDays": 0,
  "departureTime": "2026-07-20T08:00:00"
}
- Output:
{
    "success": true,
    "ruleId": 1,
    "baseFare": 1857500.000,
    "weekendSurcharge": 0,
    "estimatedTotal": 1857500.000,
    "deposit30Percent": 557250
}
                    BỔ SUNG LUỒNG BOOKING NGÀY 19/6/2026
-------------------------------------------------------------------------------
Tính cước phí — DISTANCE ROUND_TRIP(Phí cước đi 2 chiều )
Path: POST http://localhost:8080/FleetFlow/api/v1/customer/bookings/check-price
Input:

json{
  "vehicleId": 1,
  "bookingType": "DISTANCE",
  "tripDirection": "ROUND_TRIP",
  "distanceKm": 120.5,
  "returnDistanceKm": 118.0,
  "departureTime": "2026-07-20T08:00:00"
}

Output:

json{
  "success": true,
  "ruleId": 1,
  "baseFare": 3672500.000,
  "weekendSurcharge": 0,
  "estimatedTotal": 3672500.000,
  "deposit30Percent": 1101750,
  "legDistanceKm": 120.5,
  "returnDistanceKm": 118.0,
  "totalDistanceKm": 238.5
}

Tính cước phí — HOURLY

Path: POST http://localhost:8080/FleetFlow/api/v1/customer/bookings/check-price
Input:

json{
  "vehicleId": 1,
  "bookingType": "HOURLY",
  "tripDirection": "ONE_WAY",
  "durationHours": 4,
  "departureTime": "2026-07-20T08:00:00"
}

Output:

{
  "success": true,
  "ruleId": 2,
  "baseFare": 600000.000,
  "weekendSurcharge": 0,
  "estimatedTotal": 600000.000,
  "deposit30Percent": 180000,
  "legDistanceKm": 0.0,
  "returnDistanceKm": 0.0,
  "totalDistanceKm": 0.0
}

Tính cước phí — DAILY(THUÊ XE THEO NGÀY)

Path: POST http://localhost:8080/FleetFlow/api/v1/customer/bookings/check-price
Input:

json{
  "vehicleId": 1,
  "bookingType": "DAILY",
  "tripDirection": "ONE_WAY",
  "durationDays": 2,
  "departureTime": "2026-07-20T08:00:00"
}

Output:

{
  "success": true,
  "ruleId": 3,
  "baseFare": 2000000.000,
  "weekendSurcharge": 0,
  "estimatedTotal": 2000000.000,
  "deposit30Percent": 600000,
  "legDistanceKm": 0.0,
  "returnDistanceKm": 0.0,
  "totalDistanceKm": 0.0
}

Tính cước phí — Cuối tuần (weekend surcharge +10%)

Path: POST http://localhost:8080/FleetFlow/api/v1/customer/bookings/check-price
Input:

json{
  "vehicleId": 1,
  "bookingType": "DISTANCE",
  "tripDirection": "ONE_WAY",
  "distanceKm": 120.5,
  "departureTime": "2026-07-18T08:00:00"
}
(2026-07-18 là thứ 7)

Output:

json{
  "success": true,
  "ruleId": 1,
  "baseFare": 1857500.000,
  "weekendSurcharge": 185750,
  "estimatedTotal": 2043250.000,
  "deposit30Percent": 612975,
  "legDistanceKm": 120.5,
  "returnDistanceKm": 0.0,
  "totalDistanceKm": 120.5
}
-------------------------------------------------------------------------------
Áp mã voucher
- Path: POST http://localhost:8080/FleetFlow/api/v1/customer/vouchers/apply
- Input:
{
  "code": "HE2026",
  "customerId": 1,
  "estimatedTotal": 1857500,
  "vehicleTypeId": 1
}
- Output:
{
    "success": true,
    "voucherId": 1,
    "code": "HE2026",
    "discountAmount": 20.00,
    "finalTotal": 1857480.00
}

Xem hồ sơ của khách hàng
- Path: GET /FleetFlow/api/v1/customers/profile
- Input:
- Output:
{
  "data": {
    "accountId": 1,
    "customerId": 1,
    "email": "annguyen1@example.com",
    "fullName": "Nguyễn Văn An",
    "phoneNumber": "0910131711",
    "roleName": "Customer",
    "status": "Active",
    "address": "38 Lê Lợi, Quận 3, TP.HCM",
    "debtBalance": 0.00,
    "bookingStatus": "Active",
    "createdAt": "2026-04-02 09:00:00"
  },
  "success": true
}

Cập nhật thông tin của khách hàng
- Path: POST /FleetFlow/api/v1/customers/profile/update
- Input:
{
  "fullName": "Nguyễn Văn An",
}
- Output:
{
  "data": {
    "accountId": 1,
    "customerId": 1,
    "email": "annguyen1@example.com",
    "fullName": "Nguyễn Văn An",
    "phoneNumber": "0910131711",
    "roleName": "Customer",
    "status": "Active",
    "address": "38 Lê Lợi, Quận 3, TP.HCM",
    "debtBalance": 0.00,
    "bookingStatus": "Active",
    "createdAt": "2026-04-02 09:00:00"
  },
  "success": true,
  "message": "Cập nhật hồ sơ thành công."
}

Lọc xe có sẵn
- Path: GET /FleetFlow/api/v1/vehicles/?seatCount=7
- Input:
{
    "seatCount": 7
}
- Output:
{
    "data": [
        {
            "vehicleId": 28,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51E-836.12",
            "chassisNumber": "CHS002805268",
            "engineNumber": "ENG002856844",
            "brand": "Ford",
            "model": "Everest",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 53400,
            "description": "Ford Everest 7 chỗ, đời 2019, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": null
        },
        {
            "vehicleId": 38,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51C-206.02",
            "chassisNumber": "CHS003878578",
            "engineNumber": "ENG003848574",
            "brand": "Ford",
            "model": "Everest",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 8400,
            "description": "Ford Everest 7 chỗ, đời 2022, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": null
        },
        {
            "vehicleId": 26,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51C-562.54",
            "chassisNumber": "CHS002690606",
            "engineNumber": "ENG002638498",
            "brand": "Hyundai",
            "model": "Custin",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 42800,
            "description": "Hyundai Custin 7 chỗ, đời 2024, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": null
        },
        {
            "vehicleId": 36,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51A-932.44",
            "chassisNumber": "CHS003663916",
            "engineNumber": "ENG003630228",
            "brand": "Hyundai",
            "model": "Custin",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 95800,
            "description": "Hyundai Custin 7 chỗ, đời 2020, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": null
        },
        {
            "vehicleId": 31,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51B-247.99",
            "chassisNumber": "CHS003127261",
            "engineNumber": "ENG003184363",
            "brand": "Mitsubishi",
            "model": "Xpander",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 69300,
            "description": "Mitsubishi Xpander 7 chỗ, đời 2022, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": null
        },
        {
            "vehicleId": 21,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51D-877.09",
            "chassisNumber": "CHS002153951",
            "engineNumber": "ENG002192633",
            "brand": "Mitsubishi",
            "model": "Xpander",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 16300,
            "description": "Mitsubishi Xpander 7 chỗ, đời 2019, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": "Ghế da, Có màn hình giải trí"
        },
        {
            "vehicleId": 23,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51F-151.67",
            "chassisNumber": "CHS002368613",
            "engineNumber": "ENG002310979",
            "brand": "Suzuki",
            "model": "XL7",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 26900,
            "description": "Suzuki XL7 7 chỗ, đời 2021, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": "Tiết kiệm nhiên liệu, Phù hợp gia đình"
        },
        {
            "vehicleId": 33,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51D-521.57",
            "chassisNumber": "CHS003341923",
            "engineNumber": "ENG003302709",
            "brand": "Suzuki",
            "model": "XL7",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 79900,
            "description": "Suzuki XL7 7 chỗ, đời 2024, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": null
        },
        {
            "vehicleId": 35,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51F-795.15",
            "chassisNumber": "CHS003556585",
            "engineNumber": "ENG003521055",
            "brand": "Toyota",
            "model": "Fortuner",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 90500,
            "description": "Toyota Fortuner 7 chỗ, đời 2019, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": null
        },
        {
            "vehicleId": 25,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51B-425.25",
            "chassisNumber": "CHS002583275",
            "engineNumber": "ENG002529325",
            "brand": "Toyota",
            "model": "Fortuner",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 37500,
            "description": "Toyota Fortuner 7 chỗ, đời 2023, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": null
        },
        {
            "vehicleId": 30,
            "vehicleTypeId": 2,
            "typeName": "Xe 7 chỗ",
            "licensePlate": "51A-110.70",
            "chassisNumber": "CHS003019930",
            "engineNumber": "ENG003075190",
            "brand": "Toyota",
            "model": "Innova",
            "seatCount": 7,
            "status": "Available",
            "accumulatedKm": 64000,
            "description": "Toyota Innova 7 chỗ, đời 2021, 7 chỗ rộng rãi, gầm cao, lý tưởng cho gia đình và nhóm.",
            "tags": null
        }
    ],
    "success": true,
    "count": 11
}

Lấy lịch sử đặt xe của khách hàng
- Path: GET /FleetFlow/api/v1/customers/bookings
- Input:
- Output:
{
  "data": [
    {
      "bookingId": 13,
      "status": "Completed",
      "bookingType": "DISTANCE",
      "tripDirection": "ONE_WAY",
      "createdAt": "2026-05-14 10:05:00",
      "brand": "Thaco",
      "model": "Iveco Daily",
      "licensePlate": "51B-891.47",
      "pickupAddress": "82 Lê Lợi, Quận 3, TP.HCM",
      "dropoffAddress": "193 Nguyễn Thị Minh Khai, Quận 10, TP.HCM",
      "distanceKm": 258.40,
      "departureTime": "2026-05-15 10:05:00",
      "estimatedTotal": 6580000.00
    }
  ]
}

Lấy danh sách xe
- Path: GET /FleetFlow/api/v1/vehicles
- Input:
- Output:
{
  "data": [
    {
      "vehicleId": 1,
      "vehicleTypeId": 1,
      "typeName": "Xe 4 chỗ",
      "licensePlate": "51B-137.29",
      "chassisNumber": "CHS000107331",
      "engineNumber": "ENG000109173",
      "brand": "Honda",
      "model": "City",
      "seatCount": 4,
      "status": "Available",
      "accumulatedKm": 8300,
      "description": "Honda City 4 chỗ, đời 2020, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
      "tags": "Ghế da, Có màn hình giải trí"
    }
  ]
}

Lọc xe theo bookingType:
- Path: GET /FleetFlow/api/v1/vehicles?bookingType=DAILY&seatCount=4
- Input:
{
    bookingType: "DAILY",
    seatCount: "4"
}
- Output:
{
    "data": [
        {
            "vehicleId": 1,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51B-137.29",
            "chassisNumber": "CHS000107331",
            "engineNumber": "ENG000109173",
            "brand": "Honda",
            "model": "City",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 8300,
            "description": "Honda City 4 chỗ, đời 2020, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Ghế da, Có màn hình giải trí"
        },
        {
            "vehicleId": 11,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51F-507.19",
            "chassisNumber": "CHS001180641",
            "engineNumber": "ENG001100903",
            "brand": "Honda",
            "model": "City",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 61300,
            "description": "Honda City 4 chỗ, đời 2023, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Cách âm tốt, Hỗ trợ trẻ em"
        },
        {
            "vehicleId": 13,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51B-781.77",
            "chassisNumber": "CHS001395303",
            "engineNumber": "ENG001319249",
            "brand": "Hyundai",
            "model": "Accent",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 71900,
            "description": "Hyundai Accent 4 chỗ, đời 2025, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Xe không hút thuốc, Tài xế kinh nghiệm"
        },
        {
            "vehicleId": 16,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51E-192.64",
            "chassisNumber": "CHS001617296",
            "engineNumber": "ENG001646768",
            "brand": "Kia",
            "model": "Soluto",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 87800,
            "description": "Kia Soluto 4 chỗ, đời 2021, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Tài xế kinh nghiệm, Cửa sổ trời"
        },
        {
            "vehicleId": 6,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51A-822.74",
            "chassisNumber": "CHS000643986",
            "engineNumber": "ENG000655038",
            "brand": "Kia",
            "model": "Soluto",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 34800,
            "description": "Kia Soluto 4 chỗ, đời 2025, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Phù hợp gia đình, Cổng sạc USB"
        },
        {
            "vehicleId": 8,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51C-096.32",
            "chassisNumber": "CHS000858648",
            "engineNumber": "ENG000873384",
            "brand": "Mitsubishi",
            "model": "Attrage",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 45400,
            "description": "Mitsubishi Attrage 4 chỗ, đời 2020, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Wifi miễn phí, Cách âm tốt"
        },
        {
            "vehicleId": 18,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51A-466.22",
            "chassisNumber": "CHS001831958",
            "engineNumber": "ENG001865114",
            "brand": "Mitsubishi",
            "model": "Attrage",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 98400,
            "description": "Mitsubishi Attrage 4 chỗ, đời 2023, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Ghế da, Khử mùi thơm mát"
        },
        {
            "vehicleId": 20,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51C-740.80",
            "chassisNumber": "CHS002046620",
            "engineNumber": "ENG002083460",
            "brand": "Toyota",
            "model": "Vios",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 11000,
            "description": "Toyota Vios 4 chỗ, đời 2025, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Khoang hành lý rộng, Tiết kiệm nhiên liệu"
        },
        {
            "vehicleId": 15,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51D-055.35",
            "chassisNumber": "CHS001509965",
            "engineNumber": "ENG001537595",
            "brand": "Toyota",
            "model": "Wigo",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 82500,
            "description": "Toyota Wigo 4 chỗ, đời 2020, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Gầm cao, Khử mùi thơm mát"
        },
        {
            "vehicleId": 10,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51E-370.90",
            "chassisNumber": "CHS001073310",
            "engineNumber": "ENG001091730",
            "brand": "Toyota",
            "model": "Vios",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 56000,
            "description": "Toyota Vios 4 chỗ, đời 2022, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Ghế ngả, Xe không hút thuốc"
        },
        {
            "vehicleId": 5,
            "vehicleTypeId": 1,
            "typeName": "Xe 4 chỗ",
            "licensePlate": "51F-685.45",
            "chassisNumber": "CHS000536655",
            "engineNumber": "ENG000545865",
            "brand": "Toyota",
            "model": "Wigo",
            "seatCount": 4,
            "status": "Available",
            "accumulatedKm": 29500,
            "description": "Toyota Wigo 4 chỗ, đời 2024, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
            "tags": "Mới (đời 2023+), Wifi miễn phí"
        }
    ],
    "success": true,
    "count": 11
}

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
- Input: RejectReason: string
- Output:
{
    "success": true,
    "message": "Từ chối hồ sơ tài xế thành công"
}

Lấy danh sách xe (phía Admin)
- Path: GET /FleetFlow/api/v1/admin/vehicles
- Input:
- Output:
{
  "success": true,
  "count": 50,
  "data": [
    {
      "vehicleId": 1,
      "vehicleTypeId": 1,
      "typeName": "Xe 4 chỗ",
      "licensePlate": "51B-137.29",
      "chassisNumber": "CHS000107331",
      "engineNumber": "ENG000109173",
      "brand": "Honda",
      "model": "City",
      "seatCount": 4,
      "status": "Available",
      "accumulatedKm": 8300,
      "description": "Honda City 4 chỗ, đời 2020, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường..."
    }
  ]
}

Xóa xe (phía Admin)
- Path: DELETE /FleetFlow/api/v1/admin/vehicles/52
- Input: 
{
    "vehicleId": 52
}
- Output:
{
  "success": true,
  "message": "Xóa xe thành công"
}

Lấy chi tiết 1 xe cụ thể (Phía Admin)
- Path: GET /FleetFlow/api/v1/admin/vehicles/3
- Input: 
{
    "vehicleId": 3
}
- Output:
{
  "success": true,
  "data": {
    "vehicleId": 3,
    "vehicleTypeId": 1,
    "typeName": "Xe 4 chỗ",
    "licensePlate": "51D-411.87",
    "chassisNumber": "CHS000321993",
    "engineNumber": "ENG000327519",
    "brand": "Hyundai",
    "model": "Accent",
    "seatCount": 4,
    "status": "Unavailable",
    "accumulatedKm": 15000,
    "description": "Hyundai Accent 4 chỗ, đời 2022, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
    "tags": "Tiết kiệm nhiên liệu, Phù hợp gia đình"
  }
}

Thêm xe mới (Phía Admin)
- Path: POST /FleetFlow/api/v1/admin/vehicles/
- Input:
{
  "vehicleTypeId": 2,
  "licensePlate": "51F-999.99",
  "chassisNumber": "CHS-TEST-001",
  "engineNumber": "ENG-TEST-001",
  "brand": "Toyota",
  "model": "Innova 2024",
  "seatCount": 7,
  "status": "Available",
  "accumulatedKm": 0,
  "description": "Xe test tạo bằng Postman"
}
- Output:
{
  "success": true,
  "message": "Tạo xe thành công",
  "vehicleId": 52,
  "data": {
    "vehicleId": 52,
    "vehicleTypeId": 2,
    "typeName": "Xe 7 chỗ",
    "licensePlate": "51F-999.99",
    "chassisNumber": "CHS-TEST-001",
    "engineNumber": "ENG-TEST-001",
    "brand": "Toyota",
    "model": "Innova 2024",
    "seatCount": 7,
    "status": "Available",
    "accumulatedKm": 0,
    "description": "Xe test tạo bằng Postman"
  }
}

Cập nhật thông tin xe (Phía Admin)
- Path: PUT /FleetFlow/api/v1/admin/vehicles/2
- Input: 
{
  "status": "Unavailable",
  "accumulatedKm": 15000
}
- Output:
{
  "success": true,
  "message": "Cập nhật xe thành công",
  "data": {
    "vehicleId": 2,
    "vehicleTypeId": 1,
    "typeName": "Xe 4 chỗ",
    "licensePlate": "51C-274.58",
    "chassisNumber": "CHS000214662",
    "engineNumber": "ENG000218346",
    "brand": "Kia",
    "model": "Morning",
    "seatCount": 4,
    "status": "Unavailable",
    "accumulatedKm": 15000,
    "description": "Kia Morning 4 chỗ, đời 2021, máy xăng, tiết kiệm nhiên liệu, phù hợp đi phố và đường ngắn.",
    "tags": "Êm ái, Mới (đời 2023+)"
  }
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