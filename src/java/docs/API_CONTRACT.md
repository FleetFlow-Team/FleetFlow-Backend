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
Chỉ được đặt xe sau thời gian chờ. Sau mỗi chuyến xe sẽ có 1 giờ nghỉ

{
    "success": false,
    "error": "Xe này đã có lịch chạy gần giờ bạn chọn. Vui lòng chọn thời gian khác hoặc xe khác (cần cách chuyến cũ ít nhất 60 phút)."
},

{
    "success": true,
    "bookingId": 22,
    "status": "PENDING",
    "message": "Đặt xe thành công, chờ Dispatcher duyệt"
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
            "bookingId": 24,
            "vehicleId": 1,
            "vehicleName": "Toyota Vios",
            "licensePlate": "51B-101.11",
            "bookingType": "HOURLY",
            "tripDirection": "ONE_WAY",
            "status": "PENDING",
            "pickupAddress": "",
            "dropoffAddress": "",
            "departureTime": "2026-08-20 08:00:00.0",
            "distanceKm": 0,
            "durationHours": 4,
            "durationDays": null,
            "createdAt": "2026-06-22 16:46:47.317"
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

Lọc xe theo khi Status = 'AVAILABLE':
- Path: GET /FleetFlow/api/v1/vehicles
- Input:
- Output:
{
    "data": [
        {
            "vehicleTypeId": 1,
            "licensePlate": "51B-101.11",
            "fuelType": "Xăng",
            "imagePath": "/assets/img/vehicles/vehicle-1.jpg",
            "description": "Toyota Vios 4 chỗ, đời 2023, phù hợp đường dài",
            "model": "Vios",
            "vehicleId": 1,
            "brand": "Toyota",
            "seatCount": 4,
            "vehicleType": "Sedan 4 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 1,
            "licensePlate": "51C-102.12",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-2.jpg",
            "description": "Honda City 4 chỗ, đời 2024, phù hợp đường dài",
            "model": "City",
            "vehicleId": 2,
            "brand": "Honda",
            "seatCount": 4,
            "vehicleType": "Sedan 4 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 1,
            "licensePlate": "51D-103.13",
            "fuelType": "Điện",
            "imagePath": "/assets/img/vehicles/vehicle-3.jpg",
            "description": "Hyundai Accent 4 chỗ, đời 2025, phù hợp đường dài",
            "model": "Accent",
            "vehicleId": 3,
            "brand": "Hyundai",
            "seatCount": 4,
            "vehicleType": "Sedan 4 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 1,
            "licensePlate": "51F-105.15",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-5.jpg",
            "description": "Kia Soluto 4 chỗ, đời 2023, phù hợp đường dài",
            "model": "Soluto",
            "vehicleId": 5,
            "brand": "Kia",
            "seatCount": 4,
            "vehicleType": "Sedan 4 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 1,
            "licensePlate": "51B-107.17",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-7.jpg",
            "description": "Honda City 4 chỗ, đời 2025, phù hợp đường dài",
            "model": "City",
            "vehicleId": 7,
            "brand": "Honda",
            "seatCount": 4,
            "vehicleType": "Sedan 4 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 1,
            "licensePlate": "51C-108.18",
            "fuelType": "Điện",
            "imagePath": "/assets/img/vehicles/vehicle-8.jpg",
            "description": "Hyundai Accent 4 chỗ, đời 2022, phù hợp đường dài",
            "model": "Accent",
            "vehicleId": 8,
            "brand": "Hyundai",
            "seatCount": 4,
            "vehicleType": "Sedan 4 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 1,
            "licensePlate": "51D-109.19",
            "fuelType": "Xăng",
            "imagePath": "/assets/img/vehicles/vehicle-9.jpg",
            "description": "Mazda Mazda3 4 chỗ, đời 2023, phù hợp đường dài",
            "model": "Mazda3",
            "vehicleId": 9,
            "brand": "Mazda",
            "seatCount": 4,
            "vehicleType": "Sedan 4 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 1,
            "licensePlate": "51E-110.20",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-10.jpg",
            "description": "Kia Soluto 4 chỗ, đời 2024, phù hợp đường dài",
            "model": "Soluto",
            "vehicleId": 10,
            "brand": "Kia",
            "seatCount": 4,
            "vehicleType": "Sedan 4 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 1,
            "licensePlate": "51F-111.21",
            "fuelType": "Xăng",
            "imagePath": "/assets/img/vehicles/vehicle-11.jpg",
            "description": "Toyota Vios 4 chỗ, đời 2025, phù hợp đường dài",
            "model": "Vios",
            "vehicleId": 11,
            "brand": "Toyota",
            "seatCount": 4,
            "vehicleType": "Sedan 4 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 1,
            "licensePlate": "51B-113.23",
            "fuelType": "Điện",
            "imagePath": "/assets/img/vehicles/vehicle-13.jpg",
            "description": "Hyundai Accent 4 chỗ, đời 2023, phù hợp đường dài",
            "model": "Accent",
            "vehicleId": 13,
            "brand": "Hyundai",
            "seatCount": 4,
            "vehicleType": "Sedan 4 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51D-115.25",
            "fuelType": "Xăng",
            "imagePath": "/assets/img/vehicles/vehicle-15.jpg",
            "description": "Toyota Innova 7 chỗ, đời 2025, phù hợp đường dài",
            "model": "Innova",
            "vehicleId": 15,
            "brand": "Toyota",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51E-116.26",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-16.jpg",
            "description": "Mitsubishi Xpander 7 chỗ, đời 2022, phù hợp đường dài",
            "model": "Xpander",
            "vehicleId": 16,
            "brand": "Mitsubishi",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51F-117.27",
            "fuelType": "Xăng",
            "imagePath": "/assets/img/vehicles/vehicle-17.jpg",
            "description": "Honda CR-V 7 chỗ, đời 2023, phù hợp đường dài",
            "model": "CR-V",
            "vehicleId": 17,
            "brand": "Honda",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51A-118.28",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-18.jpg",
            "description": "Hyundai Custin 7 chỗ, đời 2024, phù hợp đường dài",
            "model": "Custin",
            "vehicleId": 18,
            "brand": "Hyundai",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51B-119.29",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-19.jpg",
            "description": "Kia Carens 7 chỗ, đời 2025, phù hợp đường dài",
            "model": "Carens",
            "vehicleId": 19,
            "brand": "Kia",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51D-121.31",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-21.jpg",
            "description": "Mitsubishi Xpander 7 chỗ, đời 2023, phù hợp đường dài",
            "model": "Xpander",
            "vehicleId": 21,
            "brand": "Mitsubishi",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51F-123.33",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-23.jpg",
            "description": "Hyundai Custin 7 chỗ, đời 2025, phù hợp đường dài",
            "model": "Custin",
            "vehicleId": 23,
            "brand": "Hyundai",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51A-124.34",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-24.jpg",
            "description": "Kia Carens 7 chỗ, đời 2022, phù hợp đường dài",
            "model": "Carens",
            "vehicleId": 24,
            "brand": "Kia",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51B-125.35",
            "fuelType": "Xăng",
            "imagePath": "/assets/img/vehicles/vehicle-25.jpg",
            "description": "Toyota Innova 7 chỗ, đời 2023, phù hợp đường dài",
            "model": "Innova",
            "vehicleId": 25,
            "brand": "Toyota",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51C-126.36",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-26.jpg",
            "description": "Mitsubishi Xpander 7 chỗ, đời 2024, phù hợp đường dài",
            "model": "Xpander",
            "vehicleId": 26,
            "brand": "Mitsubishi",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51D-127.37",
            "fuelType": "Xăng",
            "imagePath": "/assets/img/vehicles/vehicle-27.jpg",
            "description": "Honda CR-V 7 chỗ, đời 2025, phù hợp đường dài",
            "model": "CR-V",
            "vehicleId": 27,
            "brand": "Honda",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 3,
            "licensePlate": "51F-129.39",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-29.jpg",
            "description": "Kia Carnival 9 chỗ, đời 2023, phù hợp đường dài",
            "model": "Carnival",
            "vehicleId": 29,
            "brand": "Kia",
            "seatCount": 9,
            "vehicleType": "Limousine 9 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 3,
            "licensePlate": "51B-131.41",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-31.jpg",
            "description": "Ford Tourneo 9 chỗ, đời 2025, phù hợp đường dài",
            "model": "Tourneo",
            "vehicleId": 31,
            "brand": "Ford",
            "seatCount": 9,
            "vehicleType": "Limousine 9 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 3,
            "licensePlate": "51C-132.42",
            "fuelType": "Xăng",
            "imagePath": "/assets/img/vehicles/vehicle-32.jpg",
            "description": "Kia Carnival 9 chỗ, đời 2022, phù hợp đường dài",
            "model": "Carnival",
            "vehicleId": 32,
            "brand": "Kia",
            "seatCount": 9,
            "vehicleType": "Limousine 9 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 3,
            "licensePlate": "51D-133.43",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-33.jpg",
            "description": "Hyundai Solati Limo 9 chỗ, đời 2023, phù hợp đường dài",
            "model": "Solati Limo",
            "vehicleId": 33,
            "brand": "Hyundai",
            "seatCount": 9,
            "vehicleType": "Limousine 9 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 3,
            "licensePlate": "51E-134.44",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-34.jpg",
            "description": "Ford Tourneo 9 chỗ, đời 2024, phù hợp đường dài",
            "model": "Tourneo",
            "vehicleId": 34,
            "brand": "Ford",
            "seatCount": 9,
            "vehicleType": "Limousine 9 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 3,
            "licensePlate": "51F-135.45",
            "fuelType": "Xăng",
            "imagePath": "/assets/img/vehicles/vehicle-35.jpg",
            "description": "Kia Carnival 9 chỗ, đời 2025, phù hợp đường dài",
            "model": "Carnival",
            "vehicleId": 35,
            "brand": "Kia",
            "seatCount": 9,
            "vehicleType": "Limousine 9 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 4,
            "licensePlate": "51B-137.47",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-37.jpg",
            "description": "Ford Transit 16 chỗ, đời 2023, phù hợp đường dài",
            "model": "Transit",
            "vehicleId": 37,
            "brand": "Ford",
            "seatCount": 16,
            "vehicleType": "Xe khách 16 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 4,
            "licensePlate": "51D-139.49",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-39.jpg",
            "description": "Mercedes Sprinter 16 chỗ, đời 2025, phù hợp đường dài",
            "model": "Sprinter",
            "vehicleId": 39,
            "brand": "Mercedes",
            "seatCount": 16,
            "vehicleType": "Xe khách 16 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 4,
            "licensePlate": "51E-140.50",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-40.jpg",
            "description": "Ford Transit 16 chỗ, đời 2022, phù hợp đường dài",
            "model": "Transit",
            "vehicleId": 40,
            "brand": "Ford",
            "seatCount": 16,
            "vehicleType": "Xe khách 16 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 4,
            "licensePlate": "51F-141.51",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-41.jpg",
            "description": "Hyundai Solati 16 chỗ, đời 2023, phù hợp đường dài",
            "model": "Solati",
            "vehicleId": 41,
            "brand": "Hyundai",
            "seatCount": 16,
            "vehicleType": "Xe khách 16 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 4,
            "licensePlate": "51A-142.52",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-42.jpg",
            "description": "Mercedes Sprinter 16 chỗ, đời 2024, phù hợp đường dài",
            "model": "Sprinter",
            "vehicleId": 42,
            "brand": "Mercedes",
            "seatCount": 16,
            "vehicleType": "Xe khách 16 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 5,
            "licensePlate": "51B-143.53",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-43.jpg",
            "description": "Thaco TB79 29 chỗ, đời 2025, phù hợp đường dài",
            "model": "TB79",
            "vehicleId": 43,
            "brand": "Thaco",
            "seatCount": 29,
            "vehicleType": "Xe khách 29 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 5,
            "licensePlate": "51D-145.55",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-45.jpg",
            "description": "Samco Felix 29 chỗ, đời 2023, phù hợp đường dài",
            "model": "Felix",
            "vehicleId": 45,
            "brand": "Samco",
            "seatCount": 29,
            "vehicleType": "Xe khách 29 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 6,
            "licensePlate": "51F-147.57",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-47.jpg",
            "description": "Thaco Universe 45 chỗ, đời 2025, phù hợp đường dài",
            "model": "Universe",
            "vehicleId": 47,
            "brand": "Thaco",
            "seatCount": 45,
            "vehicleType": "Xe khách 45 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 6,
            "licensePlate": "51A-148.58",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-48.jpg",
            "description": "Hyundai Universe 45 chỗ, đời 2022, phù hợp đường dài",
            "model": "Universe",
            "vehicleId": 48,
            "brand": "Hyundai",
            "seatCount": 45,
            "vehicleType": "Xe khách 45 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 6,
            "licensePlate": "51B-149.59",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-49.jpg",
            "description": "Samco Growin 45 chỗ, đời 2023, phù hợp đường dài",
            "model": "Growin",
            "vehicleId": 49,
            "brand": "Samco",
            "seatCount": 45,
            "vehicleType": "Xe khách 45 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 6,
            "licensePlate": "51C-150.60",
            "fuelType": "Dầu",
            "imagePath": "/assets/img/vehicles/vehicle-50.jpg",
            "description": "Thaco Universe 45 chỗ, đời 2024, phù hợp đường dài",
            "model": "Universe",
            "vehicleId": 50,
            "brand": "Thaco",
            "seatCount": 45,
            "vehicleType": "Xe khách 45 chỗ",
            "status": "AVAILABLE"
        }
    ],
    "success": true,
    "count": 38
}

Lọc xe 7 chỗ, Hybrid, theo giờ
- Path: GET /FleetFlow/api/v1/vehicles?seatCount=7&fuelType=Hybrid&bookingType=HOURLY
- Input:
{
    "seatCount": 7
    "fuelType": 'Hybrid'
    'bookingType": 'HOURLY'
}
- Output:
{
    "data": [
        {
            "vehicleTypeId": 2,
            "licensePlate": "51E-116.26",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-16.jpg",
            "description": "Mitsubishi Xpander 7 chỗ, đời 2022, phù hợp đường dài",
            "model": "Xpander",
            "vehicleId": 16,
            "brand": "Mitsubishi",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51B-119.29",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-19.jpg",
            "description": "Kia Carens 7 chỗ, đời 2025, phù hợp đường dài",
            "model": "Carens",
            "vehicleId": 19,
            "brand": "Kia",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51D-121.31",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-21.jpg",
            "description": "Mitsubishi Xpander 7 chỗ, đời 2023, phù hợp đường dài",
            "model": "Xpander",
            "vehicleId": 21,
            "brand": "Mitsubishi",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51A-124.34",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-24.jpg",
            "description": "Kia Carens 7 chỗ, đời 2022, phù hợp đường dài",
            "model": "Carens",
            "vehicleId": 24,
            "brand": "Kia",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        },
        {
            "vehicleTypeId": 2,
            "licensePlate": "51C-126.36",
            "fuelType": "Hybrid",
            "imagePath": "/assets/img/vehicles/vehicle-26.jpg",
            "description": "Mitsubishi Xpander 7 chỗ, đời 2024, phù hợp đường dài",
            "model": "Xpander",
            "vehicleId": 26,
            "brand": "Mitsubishi",
            "seatCount": 7,
            "vehicleType": "SUV/MPV 7 chỗ",
            "status": "AVAILABLE"
        }
    ],
    "success": true,
    "count": 5
}

=========== 22/06 =============
Xem chi tiết hóa đơn (Invoice minh bạch giá)
- Path: GET http://localhost:8080/FleetFlow/api/v1/customer/invoices/12
- Input: {bookingId} trên URL (VD: 12)
- Output:
{
    "success": true,
    "data": {
        "InvoiceID": 1,
        "BookingID": 12,
        "BaseFare": 410000.00,
        "WeekendSurcharge": 0.00,
        "TollSurchargeTotal": 35000.00,
        "DiscountAmount": 0.00,
        "TotalAmount": 445000.00,
        "Status": "ISSUED",
        "IssuedAt": "2025-03-22 18:05:00.0"
    }
}

Xem lịch sử ví/công nợ (CustomerWalletLedger)
- Path: GET http://localhost:8080/FleetFlow/api/v1/customer/wallet
- Input:
- Output:
{
    "success": true,
    "data": [
        {
            "TransactionID": 2,
            "CustomerID": 1,
            "Amount": -300000.00,
            "TransactionType": "PAYMENT",
            "BookingID": 1,
            "CreatedAt": "2025-03-20 10:00:00.0"
        },
        {
            "TransactionID": 1,
            "CustomerID": 1,
            "Amount": 500000.00,
            "TransactionType": "REFUND",
            "BookingID": null,
            "CreatedAt": "2025-03-20 10:00:00.0"
        }
    ]
}
------------------------------------------------------------------
Update notification 2/7/2026
Danh sách thông báo của customer
- Path: GET http://localhost:8080/FleetFlow/api/v1/customer/notifications
- Input:
- Output:
{
    "success": true,
    "data": [
-----update 11/7/2026
------Notification khi thanh toan thanh cong bang tien mat
        {
            "NotificationID": 126,
            "RecipientAccountID": 1,
            "BookingID": 41,
            "Title": "Thanh toán thành công",
            "Message": "Bạn đã thanh toán 73255.00đ tiền mặt cho booking #41. Cảm ơn!",
            "Type": "PAYMENT_CASH_CONFIRMED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jul 11, 2026 9:32:55 PM"
        },
        {
            "NotificationID": 125,
            "RecipientAccountID": 1,
            "BookingID": 41,
            "Title": "Chuyến đi đã hoàn thành - Yêu cầu thanh toán",
            "Message": "Chuyến đi #41 đã hoàn thành. Vui lòng thanh toán 73255.00đ còn lại (chuyển khoản hoặc tiền mặt cho tài xế). Cảm ơn bạn đã sử dụng dịch vụ!",
            "Type": "TRIP_COMPLETED_PAYMENT_REQUIRED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jul 11, 2026 8:55:24 PM"
        },
        

----update 8/7/2026
        {
            "NotificationID": 78,
            "RecipientAccountID": 2,
            "BookingID": 2,
            "Title": "Booking #2 đã bị hủy",
            "Message": "Chuyến đi của bạn đã được hủy thành công. Không mất phí hủy. Cọc 21000.00đ đã được hoàn lại vào ví của bạn.",
            "Type": "BOOKING_CANCELLED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jul 8, 2026 2:20:28 AM"
        },
---------------------------
        {
            "NotificationID": 67,
            "RecipientAccountID": 3,
            "BookingID": 29,
            "Title": "Đã tìm được tài xế cho bạn",
            "Message": "Booking #29 đã được gán cho tài xế Sơn Dương. Vui lòng chờ tài xế xác nhận.",
            "Type": "BOOKING_DRIVER_ASSIGNED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jul 5, 2026 12:37:23 AM"
        },
        {
            "NotificationID": 66,
            "RecipientAccountID": 3,
            "BookingID": 29,
            "Title": "Đang tìm tài xế cho bạn",
            "Message": "Booking #29 hiện chưa có tài xế phù hợp. Chúng tôi đang tiếp tục tìm kiếm, vui lòng chờ.",
            "Type": "BOOKING_UNASSIGNED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jul 5, 2026 12:33:45 AM"
        },
        {
            "NotificationID": 5,
            "RecipientAccountID": 1,
            "BookingID": 13,
            "Title": "Đã tìm thấy tài xế",
            "Message": "Tài xế Nguyễn Văn A đang di chuyển đến điểm đón.",
            "Type": "TRIP_UPDATE",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "2026-06-22 09:15:00.0"
        }
    ]
}
th customer cancel booking 

output:
{
    "data": [
        {
            "NotificationID": 15,
            "RecipientAccountID": 3,
            "BookingID": 17,
            "Title": "Booking #17 đã bị hủy",
            "Message": "Chuyến đi của bạn đã được hủy thành công. Bạn bị mất cọc 333120đ do hủy trong vòng 12h.",
            "Type": "BOOKING_CANCELLED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jul 2, 2026 11:11:18 PM"
        }
    ],
    "success": true
}

Notification khi success trip for customer
output:
{
    "data": [
        {
            "NotificationID": 14,
            "RecipientAccountID": 12,
            "BookingID": 18,
            "Title": "Chuyến đi đã hoàn thành",
            "Message": "Chuyến đi #18 đã hoàn thành. Cảm ơn bạn đã sử dụng dịch vụ!",
            "Type": "TRIP_COMPLETED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jul 2, 2026 11:06:58 PM"
        }
    ],
    "success": true
}
Đánh dấu đã đọc thông báo
- Path: POST http://localhost:8080/FleetFlow/api/v1/customer/notifications/5/read
- Input: {id} của Notification trên URL
- Output:
{
    "success": true
}

Khách hàng gửi khiếu nại
- Path: POST http://localhost:8080/FleetFlow/api/v1/complaints
- Input:
{
  "bookingId": 1,
  "customerId": 1,
  "content": "Tài xế đến trễ 15 phút và thái độ không tốt."
}
- Output:
{
    "success": true
}

- Path:
- Input:
- Output:

---

## PAYMENTS

Tạo yêu cầu thanh toán MoMo cho 1 invoice
- Path: POST http://localhost:8080/FleetFlow/api/v1/payments/momo/create
- Input:
{
  "invoiceId": 1,
  "paymentType": "DEPOSIT",
  "amount": 150000.00
}
- Output:
{
    "success": true,
    "paymentUrl": "https://test-payment.momo.vn/v2/gateway/api/create?orderId=15"
}

---------------------

Ðã có thanh toán cu?i chuyen bang CASH
Thanh toán cuối (Final Payment - Tiền mặt / Chuyển khoản)
- Path: POST http://localhost:8080/FleetFlow/api/v1/payments/final
- Input:
{
  "bookingId": 1,
  "paymentMethod": "CASH"
}
- Output:
{
    "success": true,
    "finalAmount": 59000.00
}   
---------------------

Tạo yêu cầu thanh toán MoMo cho 1 booking
- Path: POST http://localhost:8080/FleetFlow/api/v1/payments/momo/create
- Input:
{
  "bookingId": 3,
  "amount": "150000"
}
- Output:
{
    "success": true,
    "paymentUrl": "https://test-payment.momo.vn/v2/gateway/pay?t=TU9NT0JLVU4yMDE4..."
}

MoMo Callback Webhook (MoMo tự động gọi về khi thanh toán thành công)
- Path: POST http://localhost:8080/FleetFlow/api/v1/payments/momo/callback
- Input:
{
  "partnerCode": "MOMO",
  "orderId": "1",
  "requestId": "1_1719540000000",
  "amount": 150000,
  "orderInfo": "Thanh toan FleetFlow",
  "orderType": "momo_wallet",
  "transId": "253018274099",
  "resultCode": 0,
  "message": "Thành công",
  "payType": "qr",
  "signature": "chuoi-ma-hoa-bat-ky"
}
- Output: 
Status 204 No Content

Customer xem lịch sử ratings
- Path: GET http://localhost:8080/FleetFlow/api/v1/customer/ratings
- Input:
- Output:
{
    "data": [
        {
            "ratingId": 1,
            "bookingId": 1,
            "driverRating": 5,
            "carRating": 5,
            "comment": "Tài xế thân thiện, xe sạch sẽ",
            "vehicleName": "Toyota Vios",
            "licensePlate": "51B-101.11",
            "bookingType": "DISTANCE",
            "driverName": "Tuấn Ngô",
            "createdAt": "2025-03-25 10:00:00.0"
        }
    ],
    "success": true
}

Customer xem lích sử complaint
- Path: GET http://localhost:8080/FleetFlow/api/v1/customer/complaints
- Input:
- Output:
{
    "data": [],
    "success": true
}
//Do chưa có complaint nên trống, code đã chạy được nhé!

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

Xem danh sách khiếu nại (Dispatcher)
- Path: GET http://localhost:8080/FleetFlow/api/v1/dispatcher/complaints
- Input:
- Output:
[
    {
        "complaintId": 4,
        "customerId": 1,
        "bookingId": 1,
        "content": "Tài xế đến trễ 15 phút và thái độ không tốt.",
        "status": "PENDING"
    },
    {
        "complaintId": 3,
        "customerId": 10,
        "bookingId": 10,
        "content": "Tính phí phụ thu chưa rõ ràng",
        "status": "PENDING"
    },
    {
        "complaintId": 2,
        "customerId": 6,
        "bookingId": 6,
        "content": "Xe có mùi thuốc lá",
        "status": "RESOLVED"
    }
]

Dispatcher giải quyết khiếu nại
- Path: PUT http://localhost:8080/FleetFlow/api/v1/dispatcher/complaints/{complaintId}/resolve
- Input:
{
  "resolution": "Đã gọi điện xin lỗi khách hàng và tặng mã giảm giá 50k cho chuyến sau."
}
- Output:
{
    "success": true
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

========= 22/06 =======
Tạo voucher mới
- Path: POST http://localhost:8080/FleetFlow/api/v1/admin/vouchers
- Input:
{
  "code": "SUMMER2026",
  "discountType": "PERCENT",
  "discountValue": 10.00,
  "maxDiscountAmount": 50000.00,
  "minBookingValue": 200000.00,
  "applicableVehicleTypeId": 1,
  "maxUsagePerUser": 1,
  "validFrom": "2026-06-01T00:00:00",
  "validTo": "2026-06-30T23:59:59"
}
- Output:
{
    "success": true
}

Danh sách voucher (Có thể filter theo trạng thái)
- Path: GET http://localhost:8080/FleetFlow/api/v1/admin/vouchers?status=ACTIVE
- Input: Params `status` (Optional: ACTIVE / INACTIVE)
- Output:
{
    "success": true,
    "data": [
        {
            "VoucherID": 1,
            "CampaignID": null,
            "Code": "SUMMER2026",
            "DiscountType": "PERCENT",
            "DiscountValue": 10.00,
            "MaxDiscountAmount": 50000.00,
            "MinBookingValue": 200000.00,
            "ApplicableVehicleTypeID": 1,
            "MaxUsagePerUser": 1,
            "ValidFrom": "2026-06-01 00:00:00.0",
            "ValidTo": "2026-06-30 23:59:59.0",
            "Status": "ACTIVE",
            "CreatedBy": 20
        }
    ]
}

Chi tiết 1 voucher
- Path: GET http://localhost:8080/FleetFlow/api/v1/admin/vouchers/1
- Input: 
- Output:
{
    "success": true,
    "data": {
        "VoucherID": 1,
        "Code": "SUMMER2026",
        "DiscountType": "PERCENT",
        "DiscountValue": 10.00,
        "Status": "ACTIVE",
        "ValidTo": "2026-06-30 23:59:59.0"
    }
}

Cập nhật voucher (đổi status, hạn dùng...)
- Path: PUT http://localhost:8080/FleetFlow/api/v1/admin/vouchers/1
- Input:
{
  "validTo": "2026-12-31T23:59:59",
  "status": "INACTIVE"
}
- Output:
{
    "success": true
}

Xóa/Deactivate voucher
- Path: DELETE http://localhost:8080/FleetFlow/api/v1/admin/vouchers/1
- Input: 
- Output:
{
    "success": true
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
----------------------------------------------------------------------
Thêm luồng duyệt đơn customer và điều nhân viên của dispatcher
Bước 0 — Login lấy token
Dispatcher:
POST http://localhost:8080/FleetFlow/api/v1/auth/login
Lưu accessToken trả về → gọi là DISPATCHER_TOKEN
Driver: login tương tự, lưu token → DRIVER_TOKEN

1 — Customer tạo Booking mới (PENDING)
path: POST http://localhost:8080/FleetFlow/api/v1/bookings
input:
{
  "customerId": 1,
  "vehicleId": 1,
  "bookingType": "DISTANCE",
  "tripDirection": "ONE_WAY",
  "pickupAddress": "123 Nguyễn Huệ, Quận 1, HCM",
  "pickupLat": 10.776,
  "pickupLng": 106.700,
  "dropoffAddress": "Vũng Tàu",
  "dropoffLat": 10.346,
  "dropoffLng": 107.084,
  "departureTime": "2026-07-20T08:00:00"
}
→ Lấy bookingId trả về, gọi là {id}
{
    "success": true,
    "bookingId": 16,
    "status": "PENDING",
    "message": "Đặt xe thành công, chờ Dispatcher duyệt"
}

Bước 2.1 — Dispatcher duyệt Booking
POST http://localhost:8080/FleetFlow/api/v1/dispatcher/bookings/{id}/approve
Header: Authorization: Bearer DISPATCHER_TOKEN


Output: 200, Booking.Status chuyển PENDING → APPROVED
{
    "success": true,
    "message": "Đã duyệt booking #16"
}

Bước 2.2 — Dispatcher duyệt Booking
POST http://localhost:8080/FleetFlow/api/v1/dispatcher/bookings/{id}/reject
Header: Authorization: Bearer DISPATCHER_TOKEN
Input: 
{ "reason" : string; } 

Output: 200, Booking.Status chuyển PENDING → REJECTED
{
    "success": true,
    "message": string
}

Bước 3 — Dispatcher dispatch driver
POST http://localhost:8080/FleetFlow/api/v1/dispatcher/bookings/{id}/dispatch
Header: Authorization: Bearer DISPATCHER_TOKEN

Body:
json{ "driverId": 1 }
→ Lấy broadcastId trả về
Kỳ vọng: Booking.Status → DISPATCHED, tạo 1 row DriverJobBroadcast status PENDING
{
    "success": true,
    "broadcastId": 1,
    "message": "Đã dispatch driver #1 cho booking #16"
}

Bước 4 — Driver xem lệnh đang chờ
GET http://localhost:8080/FleetFlow/api/v1/driver/dispatch/pending
Header: Authorization: Bearer DRIVER_TOKEN
Output: thấy broadcastId vừa tạo ở bước 3
{
    "success": true,
    "data": [
        {
            "broadcastId": 1,
            "bookingId": 16,
            "dispatchedAt": "2026-06-20 14:29:16.902"
        }
    ]
}

Bước 5 — Driver accept
POST http://localhost:8080/FleetFlow/api/v1/driver/dispatch/{broadcastId}/accept
Header: Authorization: Bearer DRIVER_TOKEN

Output: Booking.Status → CONFIRMED
{
    "success": true,
    "message": "Đã nhận chuyến"
}
Bước 6 — Driver reject
POST http://localhost:8080/FleetFlow/api/v1/driver/dispatch/{broadcastId}/reject
Header: Authorization: Bearer DRIVER_TOKEN
Input: 
{ "reason": "Xe đang bận chuyến khác" }
Output: 
{
    "success": true,
    "message": "Đã từ chối chuyến"
}
Get all list customer booking for dispatcher
path: http://localhost:8080/FleetFlow/api/v1/dispatcher/bookings/pending
Header: Authorization: Bearer DISPATCHER_TOKEN
output: 
{
    "success": true,
    "count": 6,
    "data": [
        {
            "dropoffAddress": "Phố cổ Hội An, Quảng Nam",
            "departureTime": "2025-03-13 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": "Đơn đặt xe số 3",
            "vehicleName": "Hyundai Accent",
            "bookingId": 3,
            "customerName": "Cường Lê",
            "createdAt": "2025-03-13 07:30:00.0",
            "customerPhone": "0900000003",
            "licensePlate": "51D-103.13",
            "pickupAddress": "Cầu Rồng, Đà Nẵng",
            "bookingType": "DAILY",
            "customerId": 3,
            "vehicleId": 3,
            "status": "PENDING"
        },
        {
            "dropoffAddress": "Phố cổ Hội An, Quảng Nam",
            "departureTime": "2025-03-18 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": "Đơn đặt xe số 8",
            "vehicleName": "Hyundai Accent",
            "bookingId": 8,
            "customerName": "Linh Bùi",
            "createdAt": "2025-03-18 07:30:00.0",
            "customerPhone": "0900000008",
            "licensePlate": "51C-108.18",
            "pickupAddress": "Cầu Rồng, Đà Nẵng",
            "bookingType": "HOURLY",
            "customerId": 8,
            "vehicleId": 8,
            "status": "PENDING"
        },
        {
            "dropoffAddress": "Phố cổ Hội An, Quảng Nam",
            "departureTime": "2025-03-23 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": "Đơn đặt xe số 13",
            "vehicleName": "Hyundai Accent",
            "bookingId": 13,
            "customerName": "An Nguyễn",
            "createdAt": "2025-03-23 07:30:00.0",
            "customerPhone": "0900000001",
            "licensePlate": "51D-103.13",
            "pickupAddress": "Cầu Rồng, Đà Nẵng",
            "bookingType": "DISTANCE",
            "customerId": 1,
            "vehicleId": 3,
            "status": "PENDING"
        },
        {
            "dropoffAddress": "Vũng Tàu",
            "departureTime": "2026-07-22 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": null,
            "vehicleName": "Hyundai Accent",
            "bookingId": 19,
            "customerName": "Bình Trần",
            "createdAt": "2026-06-21 10:36:30.674",
            "customerPhone": "0900000002",
            "licensePlate": "51D-103.13",
            "pickupAddress": "123 Nguyễn Huệ, Quận 1, HCM",
            "bookingType": "DISTANCE",
            "customerId": 2,
            "vehicleId": 3,
            "status": "PENDING"
        },
        {
            "dropoffAddress": "Vũng Tàu",
            "departureTime": "2026-07-23 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": null,
            "vehicleName": "Toyota Vios",
            "bookingId": 20,
            "customerName": "Cường Lê",
            "createdAt": "2026-06-21 10:36:47.355",
            "customerPhone": "0900000003",
            "licensePlate": "51B-101.11",
            "pickupAddress": "123 Nguyễn Huệ, Quận 1, HCM",
            "bookingType": "DISTANCE",
            "customerId": 3,
            "vehicleId": 1,
            "status": "PENDING"
        },
        {
            "dropoffAddress": "Vũng Tàu",
            "departureTime": "2026-07-24 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": null,
            "vehicleName": "Toyota Vios",
            "bookingId": 21,
            "customerName": "Dung Phạm",
            "createdAt": "2026-06-21 10:36:57.268",
            "customerPhone": "0900000004",
            "licensePlate": "51B-101.11",
            "pickupAddress": "123 Nguyễn Huệ, Quận 1, HCM",
            "bookingType": "DISTANCE",
            "customerId": 4,
            "vehicleId": 1,
            "status": "PENDING"
        }
    ]
}
Get list booking customer theo status for dispatcher
path: http://localhost:8080/FleetFlow/api/v1/dispatcher/bookings?status=Rejected
Header: Authorization: Bearer DISPATCHER_TOKEN
output:
{
    "success": true,
    "count": 1,
    "data": [
        {
            "dropoffAddress": "Vũng Tàu",
            "departureTime": "2026-07-22 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": null,
            "vehicleName": "Toyota Vios",
            "bookingId": 17,
            "customerName": "An Nguyễn",
            "createdAt": "2026-06-20 23:56:44.613",
            "customerPhone": "0900000001",
            "licensePlate": "51B-101.11",
            "pickupAddress": "123 Nguyễn Huệ, Quận 1, HCM",
            "bookingType": "DISTANCE",
            "customerId": 1,
            "vehicleId": 1,
            "status": "REJECTED"
        }
    ]
}
-------------------------------------------------------------------------
Update 22/6/2026
Driver start trip
Header: Authorization: Bearer DRIVER_TOKEN
path: POST http://localhost:8080/FleetFlow/api/v1/driver/trips/{bookingId}/start
Output: 
{
    "success": true,
    "message": "Đã bắt đầu chuyến đi"
}
Track gps of driver 30s/time
Header: Authorization: Bearer DRIVER_TOKEN
path: POST http://localhost:8080/FleetFlow/api/v1/driver/trips/{bookingId}/gps
input: 
 { "latitude": 10.776, "longitude": 106.700 }
output:
{

    "success": true,
    "message": "Đã ghi nhận vị trí"
}
Follow the new gps of all booking with status ONGOING 
Header: Authorization: Bearer DISPATCHER_TOKEN
path: http://localhost:8080/FleetFlow/api/v1/dispatcher/map
output:
{
    "success": true,
    "data": [
        {
            "bookingId": 16,
            "latitude": 10.7760000,
            "longitude": 106.7000000,
            "recordedAt": "2026-06-22 17:07:33.329"
        }
    ]
}
Driver bấm hoàn thành chuyến
Header: Authorization: Bearer DRIVER_TOKEN
output:
{
    "success": true,
    "message": "Đã hoàn thành chuyến đi"
}

Admin dashboard booking
Header: Authorization: Bearer ADMIN_TOKEN
path: GET http://localhost:8080/FleetFlow/api/v1/admin/bookings
output:
{
    "success": true,
    "summary": {
        "byStatus": {
            "CANCELLED": 3,
            "COMPLETED": 7,
            "CONFIRMED": 3,
            "ONGOING": 0,
            "PENDING": 9,
            "APPROVED": 1,
            "DISPATCHED": 0,
            "REJECTED": 1
        },
        "totalRevenue": 1720000.00,
        "driverRejectCount": 1
    }
}
Dashboard Booking filter by Status
Header: Authorization: Bearer ADMIN_TOKEN   
path: GET http://localhost:8080/FleetFlow/api/v1/admin/bookings?status=
output:
{
    "success": true,
    "summary": {
        "byStatus": {
            "CANCELLED": 3,
            "COMPLETED": 7,
            "CONFIRMED": 3,
            "ONGOING": 0,
            "PENDING": 9,
            "APPROVED": 1,
            "DISPATCHED": 0,
            "REJECTED": 1
        },
        "totalRevenue": 1720000.00,
        "driverRejectCount": 1
    },
    "filteredStatus": "CANCELLED",
    "count": 3,
    "data": [
        {
            "tripDirection": "ONE_WAY",
            "note": "Đơn đặt xe số 14",
            "createdAt": "2025-03-24 07:30:00.0",
            "customerPhone": "0900000002",
            "vehicleName": "Mazda Mazda3",
            "licensePlate": "51E-104.14",
            "bookingType": "HOURLY",
            "customerId": 2,
            "vehicleId": 4,
            "bookingId": 14,
            "customerName": "Bình Trần",
            "status": "CANCELLED"
        },
        {
            "tripDirection": "ONE_WAY",
            "note": "Đơn đặt xe số 9",
            "createdAt": "2025-03-19 07:30:00.0",
            "customerPhone": "0900000009",
            "vehicleName": "Mazda Mazda3",
            "licensePlate": "51D-109.19",
            "bookingType": "DAILY",
            "customerId": 9,
            "vehicleId": 9,
            "bookingId": 9,
            "customerName": "Minh Đỗ",
            "status": "CANCELLED"
        },
        {
            "tripDirection": "ROUND_TRIP",
            "note": "Đơn đặt xe số 4",
            "createdAt": "2025-03-14 07:30:00.0",
            "customerPhone": "0900000004",
            "vehicleName": "Mazda Mazda3",
            "licensePlate": "51E-104.14",
            "bookingType": "DISTANCE",
            "customerId": 4,
            "vehicleId": 4,
            "bookingId": 4,
            "customerName": "Dung Phạm",
            "status": "CANCELLED"
        }
    ]
}

Dasboard filter by status and date
Header: Authorization: Bearer ADMIN_TOKEN 
path: GET http://localhost:8080/FleetFlow/api/v1/admin/bookings?status=COMPLETED&fromDate=2026-06-01&toDate=2026-07-30
output:
{
    "success": true,
    "summary": {
        "byStatus": {
            "CANCELLED": 0,
            "COMPLETED": 1,
            "CONFIRMED": 0,
            "ONGOING": 0,
            "PENDING": 6,
            "APPROVED": 1,
            "DISPATCHED": 0,
            "REJECTED": 1
        },
        "totalRevenue": 0,
        "driverRejectCount": 1
    },
    "filteredStatus": "COMPLETED",
    "count": 1,
    "data": [
        {
            "tripDirection": "ONE_WAY",
            "note": null,
            "createdAt": "2026-06-20 14:26:15.81",
            "customerPhone": "0900000001",
            "vehicleName": "Toyota Vios",
            "licensePlate": "51B-101.11",
            "bookingType": "DISTANCE",
            "customerId": 1,
            "vehicleId": 1,
            "bookingId": 16,
            "customerName": "An Nguyễn",
            "status": "COMPLETED"
        }
    ]
}
--------------------------------------------------
23/6/2026
Dashboard filter by Date
Header: Authorization: Bearer ADMIN_TOKEN   
path:GET http://localhost:8080/FleetFlow/api/v1/admin/bookings?fromDate=2025-03-01&toDate=2025-03-31
{
    "success": true,
    "summary": {
        "byStatus": {
            "CANCELLED": 3,
            "COMPLETED": 6,
            "CONFIRMED": 3,
            "ONGOING": 0,
            "PENDING": 3,
            "APPROVED": 0,
            "DISPATCHED": 0,
            "REJECTED": 0
        },
        "totalRevenue": 1720000.00,
        "driverRejectCount": 0
    }
}
------------------------------------------------------------
Update 24/6/2026
Customer cancel booking tính penalty(API cũ chỉ bổ sung them field ouput)
path : POST http://localhost:8080/FleetFlow/api/v1/customer/bookings/cancel
input:
    {
    "bookingId": 2,
    "customerId": 2,
    "reason": "test refund money"
    }
output:
    {
        "success": true,
        "bookingId": 2,
        "forfeitDeposit": false,
        "penaltyAmount": 0,
        "refundedAmount": 21000.00,
        "message": "Hủy booking thành công. Cọc đã được hoàn lại vào ví của bạn."
    }
Admin khóa tk customer th? công
Header: Authorization: Bearer ADMIN_TOKEN 
path POST 
http://localhost:8080/FleetFlow/api/v1/admin/customers/2/lock
output:
{
    "success": true,
    "message": "Đã khóa tài khoản customer #2"
}
Admin unlock tk customer th? công
Header: Authorization: Bearer ADMIN_TOKEN 
path POST 
http://localhost:8080/FleetFlow/api/v1/admin/customers/2/unlock
output:
{
    "success": true,
    "message": "Đã mở khóa tài khoản customer #2"
}
Có them thông báo khi công nợ account khách vượt quá 1 triệu vnđ và có thông báo khi admin lock tài khoản khách.
Bên cạnh đó còn xử lý chặn khách đặt chuyến mới khi công nợ chưa được thanh toán.
Admin get list Customer
Header: Authorization: Bearer ADMIN_TOKEN 
path: GET http://localhost:8080/FleetFlow/api/v1/admin/customers
output:
[
    {
        "customerId": 1,
        "email": "an1@example.com",
        "status": "ACTIVE",
        "debt": 200000.00(tiền nợ của customer)
    },
    {
        "customerId": 2,
        "email": "binh2@example.com",
        "status": "ACTIVE",
        "debt": 334000.00
    },
    {
        "customerId": 3,
        "email": "cuong3@example.com",
        "status": "ACTIVE",
        "debt": 200000.00
    },
    {
        "customerId": 4,
        "email": "dung4@example.com",
        "status": "ACTIVE",
        "debt": 0.00
    },
    {
        "customerId": 5,
        "email": "giang5@example.com",
        "status": "ACTIVE",
        "debt": 200000.00
    },
    {
        "customerId": 6,
        "email": "ha6@example.com",
        "status": "ACTIVE",
        "debt": 0.00
    },
    {
        "customerId": 7,
        "email": "khoa7@example.com",
        "status": "ACTIVE",
        "debt": 0.00
    },
    {
        "customerId": 8,
        "email": "linh8@example.com",
        "status": "ACTIVE",
        "debt": 0.00
    },
    {
        "customerId": 9,
        "email": "minh9@example.com",
        "status": "ACTIVE",
        "debt": 0.00
    },
    {
        "customerId": 10,
        "email": "nga10@example.com",
        "status": "ACTIVE",
        "debt": 0.00
    },
    {
        "customerId": 11,
        "email": "phuc11@example.com",
        "status": "ACTIVE",
        "debt": 0.00
    },
    {
        "customerId": 12,
        "email": "mai12@example.com",
        "status": "ACTIVE",
        "debt": 0.00
    }
]
---------------------------------------------------------------------------
Admin get Auditlog
Header: Authorization: Bearer ADMIN_TOKEN 
path: GET http://localhost:8080/FleetFlow/api/v1/admin/audit-log
output:
{
    "success": true,
    "page": 1,
    "pageSize": 50,
    "total": 9,
    "data": [
        {
            "auditLogId": 9,
            "accountId": 1,
            "email": "an1@example.com",
            "fullName": "An Nguyễn",
            "action": "COMPLETE_TRIP",
            "entityName": "Booking",
            "entityId": "16",
            "oldValue": "ONGOING",
            "newValue": "COMPLETED",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-22 17:14:10.793"
        },
        {
            "auditLogId": 8,
            "accountId": 1,
            "email": "an1@example.com",
            "fullName": "An Nguyễn",
            "action": "START_TRIP",
            "entityName": "Booking",
            "entityId": "16",
            "oldValue": "CONFIRMED",
            "newValue": "ONGOING",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-22 17:06:23.214"
        },
        {
            "auditLogId": 7,
            "accountId": 2,
            "email": "binh2@example.com",
            "fullName": "Bình Trần",
            "action": "DRIVER_REJECT",
            "entityName": "Booking",
            "entityId": "18",
            "oldValue": "DISPATCHED",
            "newValue": "APPROVED (lý do: Xe đang bận chuyến khác)",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-21 00:14:31.673"
        },
        {
            "auditLogId": 6,
            "accountId": 19,
            "email": "thao19@example.com",
            "fullName": "Thảo Mạc",
            "action": "DISPATCH_DRIVER",
            "entityName": "Booking",
            "entityId": "18",
            "oldValue": "APPROVED",
            "newValue": "DISPATCHED (driverId=2)",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-21 00:12:09.029"
        },
        {
            "auditLogId": 5,
            "accountId": 19,
            "email": "thao19@example.com",
            "fullName": "Thảo Mạc",
            "action": "APPROVE_BOOKING",
            "entityName": "Booking",
            "entityId": "18",
            "oldValue": "PENDING",
            "newValue": "APPROVED",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-21 00:11:19.571"
        },
        {
            "auditLogId": 4,
            "accountId": 19,
            "email": "thao19@example.com",
            "fullName": "Thảo Mạc",
            "action": "REJECT_BOOKING",
            "entityName": "Booking",
            "entityId": "17",
            "oldValue": "PENDING",
            "newValue": "REJECTED (KhÃ´ng Äá»§ xe)",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-20 23:58:55.053"
        },
        {
            "auditLogId": 3,
            "accountId": 1,
            "email": "an1@example.com",
            "fullName": "An Nguyễn",
            "action": "DRIVER_ACCEPT",
            "entityName": "Booking",
            "entityId": "16",
            "oldValue": "DISPATCHED",
            "newValue": "CONFIRMED",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-20 14:40:05.091"
        },
        {
            "auditLogId": 2,
            "accountId": 19,
            "email": "thao19@example.com",
            "fullName": "Thảo Mạc",
            "action": "DISPATCH_DRIVER",
            "entityName": "Booking",
            "entityId": "16",
            "oldValue": "APPROVED",
            "newValue": "DISPATCHED (driverId=1)",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-20 14:29:16.911"
        },
        {
            "auditLogId": 1,
            "accountId": 19,
            "email": "thao19@example.com",
            "fullName": "Thảo Mạc",
            "action": "APPROVE_BOOKING",
            "entityName": "Booking",
            "entityId": "16",
            "oldValue": "PENDING",
            "newValue": "APPROVED",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-20 14:28:03.897"
        }
    ]
}
Admin get Auditlog phân trang, theo status
Header: Authorization: Bearer ADMIN_TOKEN 
path: GET http://localhost:8080/FleetFlow/api/v1/admin/audit-log?action=APPROVE_BOOKING&entityName=Booking&page=1&pageSize=20"
output:
{
    "success": true,
    "page": 1,
    "pageSize": 50,
    "total": 2,
    "data": [
        {
            "auditLogId": 5,
            "accountId": 19,
            "email": "thao19@example.com",
            "fullName": "Thảo Mạc",
            "action": "APPROVE_BOOKING",
            "entityName": "Booking",
            "entityId": "18",
            "oldValue": "PENDING",
            "newValue": "APPROVED",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-21 00:11:19.571"
        },
        {
            "auditLogId": 1,
            "accountId": 19,
            "email": "thao19@example.com",
            "fullName": "Thảo Mạc",
            "action": "APPROVE_BOOKING",
            "entityName": "Booking",
            "entityId": "16",
            "oldValue": "PENDING",
            "newValue": "APPROVED",
            "ipAddress": "0:0:0:0:0:0:0:1",
            "createdAt": "2026-06-20 14:28:03.897"
        }
    ]
}
------------------------------------------------------
Update 25/6/2026
Xem chi tiết đơn booking của customer
- Path: GET http://localhost:8080/FleetFlow/api/v1/bookings/27
- Input:
- Output:
{
    "bookingId": 27,
    "customerId": 2,
    "customerName": "Bình Trần",
    "customerPhone": "0900000002",
    "vehicleId": 3,
    "bookingType": "HOURLY",
    "tripDirection": "ONE_WAY",
    "status": "CANCELLED",
    "detail": {
        "pickupAddress": "null",
        "pickupLat": null,
        "pickupLng": null,
        "dropoffAddress": "null",
        "dropoffLat": null,
        "dropoffLng": null,
        "distanceKm": null,
        "departureTime": "2026-06-24 20:37:00.0",
        "durationHours": 4
    },
    "pricing": {
        "ruleId": 3,
        "baseFare": 320000.00,
        "weekendSurcharge": 0.00,
        "discountAmount": 32000.00,
        "estimatedTotal": 288000.00
    }
}
---------------------------------
Sửa lại luồng booking
Sau khi khách booking xong trả bookingId và Status: pending
Dispatcher coi toàn bộ thông tin đặt xe(nên hiển thị danh sách cho dễ coi). Dispatcher bấm vào coi thông tin chi  tiết r confirm
http://localhost:8080/FleetFlow/api/v1/dispatcher/bookings/30/confirm
(Sau 60s k nhận phản hồi từ dispatcher tự động approve )
Sau khi approve booking xong tự động gán tài xê(Cần them notification cho tài xế biết)
Tài xế có quyền approve hoặc reject
Header: Authorization: Bearer DRIVER_TOKEN 
path: http://localhost:8080/FleetFlow/api/v1/driver/dispatch/{BroadcastID}/accept
{
    "success": true,
    "message": "Đã nhận chuyến"
}
Tài xế có quyền reject
Header: Authorization: Bearer DRIVER_TOKEN 
http://localhost:8080/FleetFlow/api/v1/driver/dispatch/{BroadcastID}/reject
input:
{
  "reason": "Bận việc cá nhân"
}
{
    "success": true,
    "message": "Đã từ chối chuyến"
}
Khi driver hủy chuyến hệ thống sẽ tự động tìm tài khác r gửi notification lại
Driver get notification
Header: Authorization: Bearer DRIVER_TOKEN 
path GET http://localhost:8080/FleetFlow/api/v1/driver/dispatch/notifications
output:
{
    "success": true,
    "notifications": [
//Notification nhac driver thu CASH c?a customer
        {
            "createdAt": "2026-07-11 21:32:55.225",
            "isRead": "false",
            "notificationId": 127,
            "title": "Nhắc thu tiền mặt",
            "message": "Khách chọn thanh toán tiền mặt cho chuyến #41 rồi nha. Nhờ bạn thu giúp FleetFlow 73255.00đ từ khách nhé!",
            "type": "PAYMENT_CASH_CONFIRMED",
            "bookingId": 41
        },
// notification when booking unassigned
output:
        {
            "createdAt": "2026-07-05 00:37:23.187",
            "isRead": "false",
            "notificationId": 68,
            "title": "Bạn được gán chuyến mới",
            "message": "Dispatcher đã gán booking #29 cho bạn. Vui lòng xác nhận nhận chuyến.",
            "type": "DISPATCH_ASSIGNED",
            "bookingId": 29
        },
        {
            "createdAt": "2026-06-25 23:49:19.247",
            "notificationId": 2,
            "title": "Chuyến mới được gán!",
            "message": "Khách: Cường Lê | Đón tại: 123 Nguyễn Huệ | Giờ đi: 2026-07-11 08:00:00.0",
            "bookingId": 38
        },
        notification when customer cancel booking
        {
            "createdAt": "2026-07-03 14:13:52.666",
            "isRead": "false",
            "notificationId": 37,
            "title": "Chuyến đi #20 bị hủy",
            "message": "Khách hàng đã hủy booking #20. Lý do: Bất ngờ tạo nên sự quyến rũ ng đàn ông",
            "type": "BOOKING_CANCELLED",
            "bookingId": 20
        }
    ]
}
Driver xem chi tiet chuyen trc khi approve or reject
Header: Authorization: Bearer DRIVER_TOKEN 
path: GET http://localhost:8080/FleetFlow/api/v1/driver/dispatch/pending
output:
{
    "success": true,
    "data": [
        {
            "dropoffAddress": "Vũng Tàu",
            "departureTime": "2026-07-11 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "dispatchedAt": "2026-06-25 23:49:19.129",
            "customerPhone": "0900000003",
            "pickupAddress": "123 Nguyễn Huệ",
            "broadcastId": 7,
            "bookingType": "DISTANCE",
            "distanceKm": "96.40",
            "bookingId": 38,
            "customerName": "Cường Lê"
        }
    ]
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
      "licensePlate": "51B-137.29"
    }
  ]
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
  "message": "Tạo xe thành công"
}

Xem toàn bộ bảng giá (Pricing Rules)
- Path: GET http://localhost:8080/FleetFlow/api/v1/admin/pricing-rules
- Input:
- Output:
[
    {
        "tripDirection": "ONE_WAY",
        "vehicleTypeId": 1,
        "weekendMultiplier": 1.20,
        "bookingType": "DISTANCE",
        "pricePerKm": 9000.00,
        "ruleId": 1,
        "basePrice": 50000.00
    },
    {
        "tripDirection": "ROUND_TRIP",
        "vehicleTypeId": 1,
        "weekendMultiplier": 1.20,
        "bookingType": "DISTANCE",
        "pricePerKm": 8100.00,
        "ruleId": 2,
        "basePrice": 50000.00
    }
]

Cập nhật bảng giá (Pricing Rule)
- Path: PUT http://localhost:8080/FleetFlow/api/v1/admin/pricing-rules/{ruleId}
- Input:
{
  "basePrice": 50000,
  "pricePerKm": 15000,
  "pricePerHour": 80000,
  "pricePerDay": 1000000,
  "weekendMultiplier": 1.15
}
- Output:
{
    "success": true
}

Thêm ngày lễ mới
- Path: POST http://localhost:8080/FleetFlow/api/v1/admin/holidays
- Input:
{
  "holidayDate": "2026-09-02",
  "description": "Quốc khánh Việt Nam"
}
- Output:
{
    "success": true
}

Xem danh sách ngày lễ
- Path: GET http://localhost:8080/FleetFlow/api/v1/admin/holidays
- Input:
- Output:
[
    {
        "description": "Quốc khánh Việt Nam",
        "holidayId": 1,
        "holidayDate": "2026-09-02"
    }
]

Xóa ngày lễ
- Path: DELETE http://localhost:8080/FleetFlow/api/v1/admin/holidays/{holidayId}
- Input:
- Output:
{
    "success": true
Dispatcher coi thông tin booking khi hết xe k tự auto gán đc nữa 
Header: Authorization: Bearer DISPATCHER_TOKEN
path: GET http://localhost:8080/FleetFlow/api/v1/dispatcher/bookings/unassigned
output:
{
    "success": true,
    "count": 1,
    "data": [
        {
            "dropoffAddress": "Vũng Tàu",
            "departureTime": "2026-07-02 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "customerPhone": "0900000003",
            "pickupAddress": "123 Nguyễn Huệ",
            "bookingType": "DISTANCE",
            "customerId": 3,
            "vehicleId": 17,
            "bookingId": 41,
            "customerName": "Cường Lê",
            "status": "UNASSIGNED",
            "updatedAt": "2026-06-26 13:38:12.028"
        }
    ]
} 
Admin xem tag của từng xe đang được gán
Header: Authorization: Bearer ADMIN_TOKEN
path GET http://localhost:8080/FleetFlow/api/v1/admin/vehicles/1/tags
{
    "success": true,
    "vehicleId": 1,
    "data": [
        {
          
  "tagId": 14,
            "tagName": "cốp rộng",
            "description": null
        },
        {
            "tagId": 13,
            "tagName": "êm ái",
            "description": null
        }
    ]
}
ADMIN UPDATE TAG FOR VEHICle
Header: Authorization: Bearer ADMIN_TOKEN
path: PUT http://localhost:8080/FleetFlow/api/v1/admin/vehicles/1/tags
input: 
{
  "tags": [
    { "tagName": "em ai", "description": "Cốp chứa được 1000 vali lớn" },
    { "tagName": "cốp rộng", "description": "Cốp chứa được 4 vali lớn" }
  ]
}
output:
{
    "success": true,
    "message": "Cập nhật tags thành công",
    "vehicleId": 1,
    "data": [
        {
            "tagId": 14,
            "tagName": "cốp rộng",
            "description": "Cốp chứa được 4 vali lớn"
        },
        {
            "tagId": 15,
            "tagName": "em ai",
            "description": "Cốp chứa được 1000 vali lớn"
        }
    ]
}
AI find car for customer when chat
path POST http://localhost:8080/FleetFlow/api/v1/ai/chat
{ "message": "tôi cần xe 7 chỗ đi Đà Lạt, cốp rộng" }
{
    "success": true,
    "source": "FALLBACK",
    "data": [
        
        {
            "vehicleId": 1,
            "brand": "Toyota",
            "model": "Vios",
            "vehicleType": "Sedan 4 chỗ",
            "seatCount": 4,
            "tags": "êm ái, cốp rộng",
            "reason": "Gợi ý theo từ khóa (AI tạm thời không khả dụng)",
            "source": "FALLBACK"
        },
        {
            "vehicleId": 2,
            "brand": "Honda",
            "model": "City",
            "vehicleType": "Sedan 4 chỗ",
            "seatCount": 4,
            "tags": "Ghế da, Ghế ngả, Hợp đường dài",
            "reason": "Gợi ý theo từ khóa (AI tạm thời không khả dụng)",
            "source": "FALLBACK"
        },
        {
            "vehicleId": 3,
            "brand": "Hyundai",
            "model": "Accent",
            "vehicleType": "Sedan 4 chỗ",
            "seatCount": 4,
            "tags": "Màn hình giải trí, Cửa sổ trời, Khoang rộng",
            "reason": "Gợi ý theo từ khóa (AI tạm thời không khả dụng)",
            "source": "FALLBACK"
        },
        {
            "vehicleId": 4,
            "brand": "Mazda",
            "model": "Mazda3",
            "vehicleType": "Sedan 4 chỗ",
            "seatCount": 4,
            "tags": "Wifi, Cảm biến lùi, Mới bảo dưỡng",
            "reason": "Gợi ý theo từ khóa (AI tạm thời không khả dụng)",
            "source": "FALLBACK"
        },
        {
            "vehicleId": 5,
            "brand": "Kia",
            "model": "Soluto",
            "vehicleType": "Sedan 4 chỗ",
            "seatCount": 4,
            "tags": "Đời mới, Ghế ngả, Bảo hiểm đầy đủ",
            "reason": "Gợi ý theo từ khóa (AI tạm thời không khả dụng)",
            "source": "FALLBACK"
        }
    ]
}
Khách tìm nội dung k liên quan đến xe
path POST http://localhost:8080/FleetFlow/api/v1/ai/chat
input:
{ "message": "xin chào bạn là ai" }
output:
{
    "success": true,
    "source": "OFF_TOPIC",
    "data": [],
    "message": "Tôi chỉ hỗ trợ tìm xe cho chuyến đi thôi nhé. Bạn mô tả nhu cầu chuyến đi (số chỗ, loại xe, điểm đến...) để tôi gợi ý xe phù hợp nha."
}
Khách muốn tìm xe độc lạ bình dương
path POST http://localhost:8080/FleetFlow/api/v1/ai/chat
input:
{ "message": "Tôi muốn tìm xe rồng xe phượng " }
output:
{
    "success": true,
    "source": "UNREALISTIC",
    "data": [],
    "message": "Hệ thống FleetFlow hiện chỉ phục vụ xe ô tô phổ thông (sedan/SUV/xe nhiều chỗ...) cho dịch vụ thuê xe có lái, chưa có loại phương tiện bạn yêu cầu. Bạn thử mô tả lại nhu cầu với các xe hiện có nhé."
}
Khach hoi khong dung topic khi AI không hat dong
path POST http://localhost:8080/FleetFlow/api/v1/ai/chat
input:
{ "message": "tôi cần tìm xe siêu nhân gao " }
{
    "success": true,
    "source": "FALLBACK_DEFAULT",
    "message": "Hệ thống gợi ý AI đang tạm gián đoạn nên tôi chưa thể hiểu chính xác yêu cầu của bạn. Dưới đây là vài xe phổ biến để bạn tham khảo tạm, bạn có thể xem thêm ở danh sách xe đầy đủ hoặc mô tả lại nhu cầu rõ hơn nhé.",
    "data": [
        
        {
            "vehicleId": 1,
            "brand": "Toyota",
            "model": "Vios",
            "vehicleType": "Sedan 4 chỗ",
            "seatCount": 4,
            "tags": "êm ái, cốp rộng",
            "reason": "Gợi ý theo từ khóa (AI tạm thời không khả dụng)",
            "source": "FALLBACK"
        },
        {
            "vehicleId": 2,
            "brand": "Honda",
            "model": "City",
            "vehicleType": "Sedan 4 chỗ",
            "seatCount": 4,
            "tags": "Ghế da, Ghế ngả, Hợp đường dài",
            "reason": "Gợi ý theo từ khóa (AI tạm thời không khả dụng)",
            "source": "FALLBACK"
        },
        {
            "vehicleId": 3,
            "brand": "Hyundai",
            "model": "Accent",
            "vehicleType": "Sedan 4 chỗ",
            "seatCount": 4,
            "tags": "Màn hình giải trí, Cửa sổ trời, Khoang rộng",
            "reason": "Gợi ý theo từ khóa (AI tạm thời không khả dụng)",
            "source": "FALLBACK"
        },
        {
            "vehicleId": 4,
            "brand": "Mazda",
            "model": "Mazda3",
            "vehicleType": "Sedan 4 chỗ",
            "seatCount": 4,
            "tags": "Wifi, Cảm biến lùi, Mới bảo dưỡng",
            "reason": "Gợi ý theo từ khóa (AI tạm thời không khả dụng)",
            "source": "FALLBACK"
        },
        {
            "vehicleId": 5,
            "brand": "Kia",
            "model": "Soluto",
            "vehicleType": "Sedan 4 chỗ",
            "seatCount": 4,
            "tags": "Đời mới, Ghế ngả, Bảo hiểm đầy đủ",
            "reason": "Gợi ý theo từ khóa (AI tạm thời không khả dụng)",
            "source": "FALLBACK"
        }
    ]
}
---------------------------------------------------------------------------
Dispatcher get notifications
Header: Authorization: Bearer DISPATCHER_TOKEN
path GET http://localhost:8080/FleetFlow/api/v1/dispatcher/notifications
output:
{
    "data": [
Notification khi cus thnh toan tien mat
        {
            "NotificationID": 128,
            "RecipientAccountID": 18,
            "BookingID": 41,
            "Title": "Booking #41 đã thanh toán tiền mặt",
            "Message": "Khách đã thanh toán 73255.00đ tiền mặt cho booking #41.",
            "Type": "PAYMENT_CASH_CONFIRMED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jul 11, 2026 9:32:55 PM"
        },
        {
            "NotificationID": 7,
            "RecipientAccountID": 18,
            "BookingID": 17,
            "Title": "Booking #17 đã được gán tài xế",
            "Message": "Hệ thống đã tự động gán booking #17 cho tài xế Tuấn Ngô (DriverID=1).",
            "Type": "BOOKING_DRIVER_ASSIGNED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jun 30, 2026 11:21:40 AM"
        },
        {
            "NotificationID": 4,
            "RecipientAccountID": 18,
            "BookingID": 17,
            "Title": "Booking #17 bị tài xế từ chối",
            "Message": "Tài xế Sơn Dương (DriverID=2) đã từ chối booking #17. Lý do: Bận việc cá nhân Hệ thống đang tự tìm tài xế khác.",
            "Type": "BOOKING_DRIVER_REJECTED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jun 30, 2026 11:21:39 AM"
        },
        {
            "NotificationID": 2,
            "RecipientAccountID": 18,
            "BookingID": 17,
            "Title": "Booking #17 đã được gán tài xế",
            "Message": "Hệ thống đã tự động gán booking #17 cho tài xế Sơn Dương (DriverID=2).",
            "Type": "BOOKING_DRIVER_ASSIGNED",
            "Channel": "IN_APP",
            "IsRead": true,
            "CreatedAt": "Jun 30, 2026 10:49:51 AM"
        }
    ],
    "success": true
}
Dispatcher confirm read notification
Header: Authorization: Bearer DISPATCHER_TOKEN
path: POST http://localhost:8080/FleetFlow/api/v1/dispatcher/notifications/2/read
output:
{
    "success": true
}
Driver get history trip 
Header: Authorization: Bearer DRIVER_TOKEN
path: GET http://localhost:8080/FleetFlow/api/v1/driver/dispatch/history
{
    "success": true,
    "data": [
        {
            "dropoffAddress": "Sân bay Tân Sơn Nhất, TP.HCM",
            "departureTime": "2025-03-21 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "distanceKm": "222.50",
            "bookingId": 11,
            "customerName": "Phúc Võ",
            "customerPhone": "0900000011",
            "pickupAddress": "123 Lê Lợi, Q.1, TP.HCM",
            "broadcastId": 6,
            "bookingType": "HOURLY",
            "bookingStatus": "COMPLETED",
            "estimatedTotal": "365000.00",
            "acceptedAt": "2025-03-21 06:33:00.0"
        },
        {
            "dropoffAddress": "Sân bay Tân Sơn Nhất, TP.HCM",
            "departureTime": "2025-03-11 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "distanceKm": "47.50",
            "bookingId": 1,
            "customerName": "An Nguyễn",
            "customerPhone": "0900000001",
            "pickupAddress": "123 Lê Lợi, Q.1, TP.HCM",
            "broadcastId": 1,
            "bookingType": "DISTANCE",
            "bookingStatus": "COMPLETED",
            "estimatedTotal": "85000.00",
            "acceptedAt": "2025-03-11 08:12:00.0"
        },
notification for dispatcher when customer cancel booking
        {
            "NotificationID": 16,
            "RecipientAccountID": 18,
            "BookingID": 17,
            "Title": "Booking #17 bị hủy bởi khách",
            "Message": "Khách hàng đã hủy booking #17. Bạn bị mất cọc 333120đ do hủy trong vòng 12h.",
            "Type": "BOOKING_CANCELLED",
            "Channel": "IN_APP",
            "IsRead": false,
            "CreatedAt": "Jul 2, 2026 11:11:18 PM"
        }
    ]
}
---------------------------------------------------------------
get vị trí hiện tại của khách
path: GET http://localhost:8080/FleetFlow/api/v1/maps/reverse-geocode?lat=10.774339199999986&lng=106.70287209999998
output:
{
    "lat": 10.774339199999986,
    "lng": 106.70287209999998,
    "address": "123-125 Nguyễn Huệ Phường Sài Gòn,Thành Phố Hồ Chí Minh",
    "display": "123-125 Nguyễn Huệ Phường Sài Gòn,Thành Phố Hồ Chí Minh"
}

---------------------------------------------------------------
# BE → FE: Cập nhật API sau đợt vá Lock/Unlock tài khoản (11/07/2026)

## CHUNG

Login bằng account đang bị Admin khóa (mọi role) — HTTP 403

- Path: POST http://localhost:8080/FleetFlow/api/v1/auth/login

- Input:

```json
{
  "email": "driver1@fleetflow.com",
  "password": "<mật khẩu đúng>"
}
```

- Output:

```json
{
    "success": false,
    "message": "Tài khoản của bạn đang bị tạm khóa. Vui lòng liên hệ Admin để được hỗ trợ."
}
```

---

## DRIVER

Driver bị khóa xem danh sách chuyến đang chờ nhận — HTTP 403

- Path: GET http://localhost:8080/FleetFlow/api/v1/driver/dispatch/pending

- Input:

```
Header: Authorization: Bearer <token Driver>
```

- Output:

```json
{
    "error": "Tài khoản của bạn đang bị tạm khóa, không thể thao tác với chuyến đi. Vui lòng liên hệ Admin."
}
```

---

Driver bị khóa xem lịch sử chuyến — HTTP 403

- Path: GET http://localhost:8080/FleetFlow/api/v1/driver/dispatch/history

- Input:

```
Header: Authorization: Bearer <token Driver>
```

- Output:

```json
{
    "error": "Tài khoản của bạn đang bị tạm khóa, không thể thao tác với chuyến đi. Vui lòng liên hệ Admin."
}
```

---

Driver bị khóa xem thông báo chuyến — HTTP 403

- Path: GET http://localhost:8080/FleetFlow/api/v1/driver/dispatch/notifications

- Input:

```
Header: Authorization: Bearer <token Driver>
```

- Output:

```json
{
    "error": "Tài khoản của bạn đang bị tạm khóa, không thể thao tác với chuyến đi. Vui lòng liên hệ Admin."
}
```

---

Driver bị khóa bấm nhận chuyến — HTTP 403

- Path: POST http://localhost:8080/FleetFlow/api/v1/driver/dispatch/{broadcastId}/accept

- Input:

```
Header: Authorization: Bearer <token Driver>
```

- Output:

```json
{
    "error": "Tài khoản của bạn đang bị tạm khóa, không thể thao tác với chuyến đi. Vui lòng liên hệ Admin."
}
```

---

Driver bị khóa từ chối chuyến — HTTP 403

- Path: POST http://localhost:8080/FleetFlow/api/v1/driver/dispatch/{broadcastId}/reject

- Input:

```json
{
  "reason": "Lý do từ chối (optional)"
}
```

- Output:

```json
{
    "error": "Tài khoản của bạn đang bị tạm khóa, không thể thao tác với chuyến đi. Vui lòng liên hệ Admin."
}
```

---

Driver bị khóa cập nhật hồ sơ / tự bật lại AVAILABLE — HTTP 403

- Path: POST http://localhost:8080/FleetFlow/api/v1/driver/profile/update

- Input:

```
accountID=3
fullName=Tài xế Tuấn
phoneNumber=0900000003
availabilityStatus=AVAILABLE
```

- Output:

```json
{
    "success": false,
    "message": "Tài khoản của bạn đang bị tạm khóa, không thể cập nhật hồ sơ hoặc trạng thái. Vui lòng liên hệ Admin."
}
```

---

## DISPATCHER

Dispatcher phân tài thủ công cho driver đang bị khóa — HTTP 400 (trước đây báo thành công, là bug đã báo)

- Path: POST http://localhost:8080/FleetFlow/api/v1/dispatcher/bookings/{bookingId}/dispatch

- Input:

```json
{
  "driverId": 1
}
```

- Output:

```json
{
    "error": "Driver #1 đang bị khóa tài khoản, không thể gán chuyến."
}
```

---

Dispatcher xem danh sách tài xế — HTTP 200, không đổi; FE dùng accountStatus để disable driver bị khóa khi phân tài

- Path: GET http://localhost:8080/FleetFlow/api/v1/dispatcher/drivers

- Input:

```
Header: Authorization: Bearer <token Dispatcher hoặc Admin>
```

- Output:

```json
{
    "success": true,
    "data": [
        {
            "accountId": 3,
            "driverId": 1,
            "fullName": "Tài xế Tuấn",
            "phoneNumber": "0900000003",
            "accountStatus": "LOCKED",
            "availabilityStatus": "AVAILABLE",
            "averageRating": 4.80,
            "acceptedTripCount": 2
        }
    ]
}
```

---

## ADMIN

Admin xem danh sách driver chờ duyệt — HTTP 200, field documents TẠM THỜI luôn rỗng

- Path: GET http://localhost:8080/FleetFlow/api/v1/admin/drivers/pending

- Input:

```
Header: Authorization: Bearer <token Admin>
```

- Output:

```json
{
    "success": true,
    "data": [
        {
            "accountId": 10,
            "fullName": "Nguyễn Văn A",
            "email": "a@example.com",
            "phone": "0900000010",
            "createdAt": "2026-07-01 10:00:00.0",
            "documents": []
        }
    ]
}
```

---

Admin khóa tài khoản driver — HTTP 200, không đổi (truyền accountId, không phải driverId)

- Path: POST http://localhost:8080/FleetFlow/api/v1/admin/drivers/{accountId}/lock

- Input:

```
Header: Authorization: Bearer <token Admin>
```

- Output:

```json
{
    "success": true,
    "message": "Đã khóa tài khoản driver #3"
}
```

---

Admin mở khóa tài khoản driver — HTTP 200, không đổi

- Path: POST http://localhost:8080/FleetFlow/api/v1/admin/drivers/{accountId}/unlock

- Input:

```
Header: Authorization: Bearer <token Admin>
```

- Output:

```json
{
    "success": true,
    "message": "Đã mở khóa tài khoản driver #3"
}
```

---

Admin khóa tài khoản dispatcher — HTTP 200, không đổi

- Path: POST http://localhost:8080/FleetFlow/api/v1/admin/dispatchers/{accountId}/lock

- Input:

```
Header: Authorization: Bearer <token Admin>
```

- Output:

```json
{
    "success": true,
    "message": "Đã khóa tài khoản dispatcher #2"
}
```

---

Admin mở khóa tài khoản dispatcher — HTTP 200, không đổi

- Path: POST http://localhost:8080/FleetFlow/api/v1/admin/dispatchers/{accountId}/unlock

- Input:

```
Header: Authorization: Bearer <token Admin>
```

- Output:

```json
{
    "success": true,
    "message": "Đã mở khóa tài khoản dispatcher #2"
}
```

---

## GHI CHÚ

- Login: input thực tế gửi dạng form x-www-form-urlencoded (email=..., password=...); sai mật khẩu vẫn trả HTTP 200 + "Incorrect email or password" như cũ — FE phân biệt case bị khóa bằng HTTP 403.

- Auto-dispatch (BE tự tìm tài xế) đã tự loại driver có account LOCKED — FE không cần làm gì.

- Đã thêm CORS + preflight OPTIONS cho /api/v1/dispatcher/drivers và /api/v1/admin/dispatchers/* — gọi từ Live Server (127.0.0.1:5500) không còn bị chặn.

- Toàn bộ Output ở trên là kết quả test thật (curl/Postman) trên Tomcat 9 + DB dev.

---

## CUSTOMER

Khách hàng đánh giá chuyến đi — giới hạn đúng 1 lần/booking, lần 2 trở đi bị từ chối HTTP 409

- Path: POST http://localhost:8080/FleetFlow/api/v1/ratings/customer

- Input:

```json
{
  "bookingId": 1,
  "driverRating": 5,
  "carRating": 5,
  "comment": "Tài xế thân thiện, xe sạch sẽ"
}
```

- Output (thành công):

```json
{
    "success": true,
    "message": "Cảm ơn bạn đã đánh giá chuyến đi!"
}
```

- Output (đã đánh giá booking này rồi) — HTTP 409:

```json
{
    "success": false,
    "message": "Bạn đã đánh giá chuyến đi này đủ số lần cho phép (tối đa 1 lần)."
}
```

---

Khách hàng gửi khiếu nại — giới hạn đúng 1 lần/booking (nếu không có bookingId thì tính theo phone/email), vượt giới hạn trả HTTP 429

- Path: POST http://localhost:8080/FleetFlow/api/v1/complaints

- Input:

```json
{
  "bookingId": 1,
  "customerId": 1,
  "type": "SERVICE_FEEDBACK",
  "issueType": "Thái độ tài xế / Chất lượng dịch vụ",
  "fullName": "Nguyễn Văn A",
  "phone": "0900000010",
  "email": "a@example.com"
}
```

- Output (thành công):

```json
{
    "success": true,
    "complaintId": 12,
    "message": "Đã gửi phản ánh thành công. Chúng tôi sẽ liên hệ sớm."
}
```

- Output (đã gửi đủ 1 lần cho booking/liên hệ này) — HTTP 429:

```json
{
    "success": false,
    "message": "Bạn đã gửi khiếu nại đủ số lần cho phép (tối đa 1 lần)."
}
```

---

Khách hàng gửi khiếu nại loại LOST_LUGGAGE/SERVICE_FEEDBACK khi chưa có booking hoàn thành — HTTP 400

- Path: POST http://localhost:8080/FleetFlow/api/v1/complaints

- Input:
{
  "type": "LOST_LUGGAGE",
  "content": "Bỏ quên vali trên xe",
  "fullName": "Nguyễn Văn A",
  "phone": "0900000010"
}
- Output:
{
    "success": false,
    "message": "Chỉ khách hàng đã hoàn thành chuyến đi mới được gửi khiếu nại loại này. Vui lòng chọn loại 'Khác' (OTHER) nếu chưa có chuyến đi hoàn thành liên quan."
}
---

Khách hàng xem lịch sử ví (giờ lấy từ Payment thật, không còn đọc bảng CustomerWallet)

- Path: GET http://localhost:8080/FleetFlow/api/v1/customer/wallet

- Input:

```
Header: Authorization: Bearer <token Customer>
```

- Output:

```json
{
    "success": true,
    "data": [
        {
            "TransactionID": 15,
            "TransactionType": "PAYMENT",
            "Amount": 78000.00,
            "BookingID": 1,
            "CreatedAt": "2026-07-17 10:00:00.0"
        },
        {
            "TransactionID": 9,
            "TransactionType": "REFUND",
            "Amount": 78000.00,
            "BookingID": 2,
            "CreatedAt": "2026-07-10 09:00:00.0"
        }
    ]
}
```

---

## DRIVER

Tài xế đánh giá khách hàng — chỉ được đánh giá 1 lần/booking (không đổi so với trước, chỉ thêm chặn lần 2) — HTTP 409 nếu gọi lại

- Path: POST http://localhost:8080/FleetFlow/api/v1/ratings/driver

- Input:

```json
{
  "bookingId": 1,
  "customerRating": 5,
  "comment": "Khách hàng lịch sự, đúng giờ"
}
```

- Output (đã đánh giá booking này rồi):

```json
{
    "success": false,
    "message": "Bạn đã đánh giá khách hàng của chuyến đi này rồi."
}
```

---

Tài xế xem điểm trung bình + danh sách đánh giá của khách hàng về mình (API MỚI)

- Path: GET http://localhost:8080/FleetFlow/api/v1/driver/ratings

- Input:

```
Header: Authorization: Bearer <token Driver>
```

- Output:

```json
{
    "success": true,
    "averageRating": 4.50,
    "ratingCount": 10,
    "data": [
        {
            "bookingId": 1,
            "comment": "Tài xế thân thiện, xe sạch sẽ",
            "driverRating": 5,
            "createdAt": "2025-03-25 10:00:00.0"
        }
    ]
}
```

---

## ADMIN

Admin xem & quản lý chất lượng qua rating (API MỚI)

- Path: GET http://localhost:8080/FleetFlow/api/v1/admin/ratings

- Query params (tất cả optional): `type` = `customer` (mặc định, khách đánh giá tài xế/xe) hoặc `driver` (tài xế đánh giá khách); `driverId`, `customerId`, `bookingId`; `lowOnly=true` (chỉ lấy rating ≤ 2 sao); `fromDate`, `toDate` (yyyy-MM-dd)

- Input:

```
Header: Authorization: Bearer <token Admin>
```

- Output (`type=customer`, mặc định):

```json
{
    "success": true,
    "type": "customer",
    "driverQuality": [
        {
            "driverId": 4,
            "driverName": "Tài xế Tuấn",
            "avgDriverRating": 3.20,
            "avgCarRating": 3.50,
            "ratingCount": 5,
            "lowRatingCount": 2
        }
    ],
    "summary": {
        "totalRatings": 42,
        "averageDriverRating": 4.35,
        "averageCarRating": 4.50,
        "lowRatingCount": 3
    },
    "count": 42,
    "data": [
        {
            "ratingId": 1,
            "bookingId": 1,
            "driverRating": 5,
            "carRating": 5,
            "comment": "Tài xế thân thiện, xe sạch sẽ",
            "customerId": 1,
            "customerName": "Nguyễn Văn A",
            "driverId": 4,
            "driverName": "Tài xế Tuấn",
            "vehicleName": "Toyota Vios",
            "licensePlate": "51B-101.11",
            "createdAt": "2025-03-25 10:00:00.0"
        }
    ]
}
```

- Output (`type=driver`): giữ nguyên `driverQuality` (luôn tính theo CustomerRating để xếp hạng tài xế), nhưng `summary`/`data` đổi sang thống kê DriverRating:

```json
{
    "success": true,
    "type": "driver",
    "driverQuality": [ "...": "như trên" ],
    "summary": {
        "totalRatings": 30,
        "averageCustomerRating": 4.70,
        "lowRatingCount": 1
    },
    "count": 30,
    "data": [
        {
            "ratingId": 1,
            "bookingId": 1,
            "customerRating": 5,
            "comment": "Khách hàng lịch sự, đúng giờ",
            "customerId": 1,
            "customerName": "Nguyễn Văn A",
            "driverId": 4,
            "driverName": "Tài xế Tuấn",
            "createdAt": "2025-03-25 10:00:00.0"
        }
    ]
}
```

---

Admin xem danh sách khách hàng — bổ sung field totalPaid (tổng tiền đã thanh toán qua Payment, tách riêng khỏi debt)

- Path: GET http://localhost:8080/FleetFlow/api/v1/admin/customers

- Input:

```
Header: Authorization: Bearer <token Admin>
```

- Output:

```json
[
    {
        "customerId": 1,
        "fullName": "Nguyễn Văn A",
        "email": "a@example.com",
        "phoneNumber": "0900000010",
        "status": "ACTIVE",
        "debt": 0,
        "totalPaid": 338000.00
    }
]
```

---

Admin dashboard — bổ sung field summary.revenueByDay (doanh thu 30 ngày gần nhất, zero-fill ngày không có giao dịch, dùng vẽ biểu đồ)

- Path: GET http://localhost:8080/FleetFlow/api/v1/admin/bookings

- Input:

```
Header: Authorization: Bearer <token Admin>
```

- Output:

```json
{
    "success": true,
    "summary": {
        "byStatus": { "PENDING": 0, "COMPLETED": 3, "CANCELLED": 1 },
        "totalRevenue": 1720000.00,
        "driverRejectCount": 0,
        "totalCustomers": 5,
        "newCustomersToday": 0,
        "revenueByDay": [
            { "date": "2026-06-18", "revenue": 0 },
            { "date": "2026-06-19", "revenue": 338000.00 }
        ]
    }
}
```

---

--------------------------------------------------------------------------------------
Danh sách các điểm đến cố định
path:  http://localhost:8080/FleetFlow/api/v1/landmarks
{
    "data": [
        {
            "name": "Sân bay quốc tế Nội Bài",
            "address": "Sân bay Nội Bài, Sóc Sơn, Hà Nội",
            "lat": 21.221200,
            "lng": 105.807200,
            "category": "AIRPORT",
            "createdBy": 0,
            "createdAt": "Jul 15, 2026 8:12:20 AM",
            "id": 4,
            "isDeleted": false
        },
        {
            "name": "Sân bay Tân Sơn Nhất",
            "address": "Sân bay Tân Sơn Nhất, Tân Bình, TP.HCM",
            "lat": 10.818000,
            "lng": 106.652000,
            "category": "AIRPORT",
            "createdBy": 0,
            "createdAt": "Jul 15, 2026 8:12:20 AM",
            "id": 3,
            "isDeleted": false
        },
        {
            "name": "Sân bay Vũng Tàu",
            "address": "Sân bay Vũng Tàu, Núi Lớn, TP. Vũng Tàu",
            "lat": 10.376600,
            "lng": 107.088800,
            "category": "AIRPORT",
            "createdBy": 0,
            "createdAt": "Jul 15, 2026 8:12:20 AM",
            "id": 5,
            "isDeleted": false
        },
        {
            "name": "Bến xe Miền Đông",
            "address": "292 Đinh Bộ Lĩnh, Bình Thạnh, TP.HCM",
            "lat": 10.814000,
            "lng": 106.711000,
            "category": "BUS_STATION",
            "createdBy": 0,
            "createdAt": "Jul 15, 2026 8:12:20 AM",
            "id": 2,
            "isDeleted": false
        },
        {
            "name": "Bến xe Miền Tây",
            "address": "395 Kinh Dương Vương, An Lạc, Bình Tân, TP.HCM",
            "lat": 10.740200,
            "lng": 106.617200,
            "category": "BUS_STATION",
            "createdBy": 0,
            "createdAt": "Jul 15, 2026 8:12:20 AM",
            "id": 1,
            "isDeleted": false
        }
    ],
    "success": true
}
Admin Landmark
Get list
path:  http://localhost:8080/FleetFlow/api/v1/admin/landmarks
output:
{
    "data": [
        {
            "name": "Sân bay quốc tế Nội Bài",
            "address": "Sân bay Nội Bài, Sóc Sơn, Hà Nội",
            "lat": 21.221200,
            "lng": 105.807200,
            "category": "AIRPORT",
            "createdBy": 0,
            "createdAt": "Jul 15, 2026 8:12:20 AM",
            "id": 4,
            "isDeleted": false
        },
        {
            "name": "Sân bay Tân Sơn Nhất",
            "address": "Sân bay Tân Sơn Nhất, Tân Bình, TP.HCM",
            "lat": 10.818000,
            "lng": 106.652000,
            "category": "AIRPORT",
            "createdBy": 0,
            "createdAt": "Jul 15, 2026 8:12:20 AM",
            "id": 3,
            "isDeleted": false
        },
        {
            "name": "Sân bay Vũng Tàu",
            "address": "Sân bay Vũng Tàu, Núi Lớn, TP. Vũng Tàu",
            "lat": 10.376600,
            "lng": 107.088800,
            "category": "AIRPORT",
            "createdBy": 0,
            "createdAt": "Jul 15, 2026 8:12:20 AM",
            "id": 5,
            "isDeleted": false
        },
        {
            "name": "Bến xe Miền Đông",
            "address": "292 Đinh Bộ Lĩnh, Bình Thạnh, TP.HCM",
            "lat": 10.814000,
            "lng": 106.711000,
            "category": "BUS_STATION",
            "createdBy": 0,
            "createdAt": "Jul 15, 2026 8:12:20 AM",
            "id": 2,
            "isDeleted": false
        },
        {
            "name": "Bến xe Miền Tây",
            "address": "395 Kinh Dương Vương, An Lạc, Bình Tân, TP.HCM",
            "lat": 10.740200,
            "lng": 106.617200,
            "category": "BUS_STATION",
            "createdBy": 0,
            "createdAt": "Jul 15, 2026 8:12:20 AM",
            "id": 1,
            "isDeleted": false
        }
    ],
    "success": true
}
Admin create new landmark
path: http://localhost:8080/FleetFlow/api/v1/admin/landmarks
input:
{"name":"Bến xe An Sương","address":"Test","lat":10.85,"lng":106.62,"category":"BUS_STATION"}
output:
{
    "landmarkId": 6,
    "success": true
}
Admin soft delete landmark
path:  http://localhost:8080/FleetFlow/api/v1/admin/landmarks/landmarkId
output:
{
    "success": true
}
Admin restore landmark when softed delete
path: http://localhost:8080/FleetFlow/api/v1/admin/landmarks/{id}/restore
output:
{
    "success": true
}
Update landmark
path: http://localhost:8080/FleetFlow/api/v1/admin/landmarks/{id}
input:
{"name":"Bến xe An Sương (đã sửa)"}
output:
{
    "success": true
}

Update flow login
path: http://localhost:8080/FleetFlow/api/v1/auth/login
output:
{
    "success": true,
    "message": "Login thành công",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ5ZW4xOEBleGFtcGxlLmNvbSIsInJvbGUiOiJEaXNwYXRjaGVyIiwiaWF0IjoxNzg0MDgxMDcyLCJleHAiOjE3ODQwODE5NzJ9.iortF7yLwE2333bjQ6ney0DFEgx0oXR0yn50mB8Y_vw",
    "user": {
        "accountId": 18,
        "roleName": "Dispatcher",
        "fullName": "Yến Trịnh",
        "email": "yen18@example.com"
    },
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ5ZW4xOEBleGFtcGxlLmNvbSIsImlhdCI6MTc4NDA4MTA3MiwiZXhwIjoxNzg0Njg1ODcyfQ.NSSsC_uiWgediMIzqez6FUmA_uVRvkZUoeDto82Zj-k"
}
refreshToken 
path: http://localhost:8080/FleetFlow/api/v1/auth/refresh
input:

{"refreshToken":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ5ZW4xOEBleGFtcGxlLmNvbSIsImlhdCI6MTc4NDA4MTA3MiwiZXhwIjoxNzg0Njg1ODcyfQ.NSSsC_uiWgediMIzqez6FUmA_uVRvkZUoeDto82Zj-k"}
output:
{
    "success": true,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ5ZW4xOEBleGFtcGxlLmNvbSIsInJvbGUiOiJEaXNwYXRjaGVyIiwiaWF0IjoxNzg0MDgxMDkwLCJleHAiOjE3ODQwODE5OTB9.VxpG1Q1p2MzhUaSOD769azd_uY20dhHeb1No0-M89_s",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ5ZW4xOEBleGFtcGxlLmNvbSIsImlhdCI6MTc4NDA4MTA5MCwiZXhwIjoxNzg0Njg1ODkwfQ.Ssqoa8QfIRFGjGz-N4ApryRlEgCbcHUla2QZLB7eqBU"
}
Complete chuyen kem image
path: http://localhost:8080/FleetFlow/api/v1/driver/trips/48/complete
input:
input sai dinh dang
output: error 400
{
    "error": "Phải gửi multipart/form-data kèm file ảnh field để hoàn thành chuyến"
}
Complete chuyen kem image
path: http://localhost:8080/FleetFlow/api/v1/driver/trips/48/complete
input:
input thieu field image
output: error 400
{
    "error": "Thiếu file ảnh xác nhận điểm đến "
}
Complete chuyen kem image
path: http://localhost:8080/FleetFlow/api/v1/driver/trips/48/complete
input:
completionPhoto : urlpath
output: 
{
    "success": true,
    "message": "Đã hoàn thành chuyến đi",
    "completionPhotoUrl": "uploads/trip-completion/trip_complete_265c8913-d44f-42e4-816a-c23247df5e48_Untitled3.png"
}
Dispatcher coi booking co kem anh endtrip
path: http://localhost:8080/FleetFlow/api/v1/dispatcher/bookings?status=COMPLETED
Header: Authorization: Bearer <token Dispatcher>

Img luu truc tiep trong src , dispatcher la actor lay duoc anh
{
    "success": true,
    "count": 10,
    "data": [
        {
            "dropoffAddress": "Sân bay Tân Sơn Nhất, TP.HCM",
            "departureTime": "2025-03-11 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": "Đơn đặt xe số 1",
            "vehicleName": "Toyota Vios",
            "bookingId": 1,
            "customerName": "An Nguyễn",
            "createdAt": "2025-03-11 07:30:00.0",
            "customerPhone": "0900000001",
            "licensePlate": "51B-101.11",
            "pickupAddress": "123 Lê Lợi, Q.1, TP.HCM",
            "bookingType": "DISTANCE",
            "customerId": 1,
            "completionPhotoUrl": null,
            "vehicleId": 1,
            "status": "COMPLETED"
        },
        {
            "dropoffAddress": "TP. Đà Lạt, Lâm Đồng",
            "departureTime": "2025-03-15 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": "Đơn đặt xe số 5",
            "vehicleName": "Kia Soluto",
            "bookingId": 5,
            "customerName": "Giang Hoàng",
            "createdAt": "2025-03-15 07:30:00.0",
            "customerPhone": "0900000005",
            "licensePlate": "51F-105.15",
            "pickupAddress": "Ga Sài Gòn, Q.3, TP.HCM",
            "bookingType": "HOURLY",
            "customerId": 5,
            "completionPhotoUrl": null,
            "vehicleId": 5,
            "status": "COMPLETED"
        },
        {
            "dropoffAddress": "Sân bay Tân Sơn Nhất, TP.HCM",
            "departureTime": "2025-03-16 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": "Đơn đặt xe số 6",
            "vehicleName": "Toyota Vios",
            "bookingId": 6,
            "customerName": "Hà Vũ",
            "createdAt": "2025-03-16 07:30:00.0",
            "customerPhone": "0900000006",
            "licensePlate": "51A-106.16",
            "pickupAddress": "123 Lê Lợi, Q.1, TP.HCM",
            "bookingType": "DAILY",
            "customerId": 6,
            "completionPhotoUrl": null,
            "vehicleId": 6,
            "status": "COMPLETED"
        },
        {
            "dropoffAddress": "TP. Đà Lạt, Lâm Đồng",
            "departureTime": "2025-03-20 08:00:00.0",
            "tripDirection": "ROUND_TRIP",
            "note": "Đơn đặt xe số 10",
            "vehicleName": "Kia Soluto",
            "bookingId": 10,
            "customerName": "Nga Phan",
            "createdAt": "2025-03-20 07:30:00.0",
            "customerPhone": "0900000010",
            "licensePlate": "51E-110.20",
            "pickupAddress": "Ga Sài Gòn, Q.3, TP.HCM",
            "bookingType": "DISTANCE",
            "customerId": 10,
            "completionPhotoUrl": null,
            "vehicleId": 10,
            "status": "COMPLETED"
        },
        {
            "dropoffAddress": "Sân bay Tân Sơn Nhất, TP.HCM",
            "departureTime": "2025-03-21 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": "Đơn đặt xe số 11",
            "vehicleName": "Toyota Vios",
            "bookingId": 11,
            "customerName": "Phúc Võ",
            "createdAt": "2025-03-21 07:30:00.0",
            "customerPhone": "0900000011",
            "licensePlate": "51B-101.11",
            "pickupAddress": "123 Lê Lợi, Q.1, TP.HCM",
            "bookingType": "HOURLY",
            "customerId": 11,
            "completionPhotoUrl": null,
            "vehicleId": 1,
            "status": "COMPLETED"
        },
        {
            "dropoffAddress": "TP. Đà Lạt, Lâm Đồng",
            "departureTime": "2025-03-25 08:00:00.0",
            "tripDirection": "ONE_WAY",
            "note": "Đơn đặt xe số 15",
            "vehicleName": "Kia Soluto",
            "bookingId": 15,
            "customerName": "Cường Lê",
            "createdAt": "2025-03-25 07:30:00.0",
            "customerPhone": "0900000003",
            "licensePlate": "51F-105.15",
            "pickupAddress": "Ga Sài Gòn, Q.3, TP.HCM",
            "bookingType": "DAILY",
            "customerId": 3,
            "completionPhotoUrl": null,
            "vehicleId": 5,
            "status": "COMPLETED"
        },
        {
            "dropoffAddress": null,
            "departureTime": "2026-07-11 12:25:00.0",
            "tripDirection": "ONE_WAY",
            "note": null,
            "vehicleName": "Hyundai Accent",
            "bookingId": 18,
            "customerName": "Mai Lý",
            "createdAt": "2026-07-01 12:26:18.558",
            "customerPhone": "0900000012",
            "licensePlate": "51D-103.13",
            "pickupAddress": "bhjnkb hbjnkml",
            "bookingType": "HOURLY",
            "customerId": 12,
            "completionPhotoUrl": null,
            "vehicleId": 3,
            "status": "COMPLETED"
        },
        {
            "dropoffAddress": null,
            "departureTime": "2026-07-26 23:27:00.0",
            "tripDirection": "ONE_WAY",
            "note": null,
            "vehicleName": "Ford Tourneo",
            "bookingId": 41,
            "customerName": "An Nguyễn",
            "createdAt": "2026-07-10 18:22:43.37",
            "customerPhone": "0900000001",
            "licensePlate": "51B-131.41",
            "pickupAddress": "45 Lý Thường Kiệt, Phường 7, Quận 10",
            "bookingType": "HOURLY",
            "customerId": 1,
            "completionPhotoUrl": null,
            "vehicleId": 31,
            "status": "COMPLETED"
        },
        {
            "dropoffAddress": null,
            "departureTime": "2026-07-31 05:04:00.0",
            "tripDirection": "ONE_WAY",
            "note": null,
            "vehicleName": "Mazda Mazda3",
            "bookingId": 42,
            "customerName": "An Nguyễn",
            "createdAt": "2026-07-12 00:00:38.63",
            "customerPhone": "0900000001",
            "licensePlate": "51D-109.19",
            "pickupAddress": "45 Lý Thường Kiệt, Phường 7, Quận 10",
            "bookingType": "DAILY",
            "customerId": 1,
            "completionPhotoUrl": null,
            "vehicleId": 9,
            "status": "COMPLETED"
        },
        {
            "dropoffAddress": null,
            "departureTime": "2026-07-18 17:15:00.0",
            "tripDirection": "ONE_WAY",
            "note": null,
            "vehicleName": "Kia Soluto",
            "bookingId": 49,
            "customerName": "An Nguyễn",
            "createdAt": "2026-07-16 17:15:31.047",
            "customerPhone": "0900000001",
            "licensePlate": "51E-110.20",
            "pickupAddress": "45 Lý Thường Kiệt, Phường 7, Quận 10",
            "bookingType": "HOURLY",
            "customerId": 1,
            "completionPhotoUrl": "uploads/trip-completion/trip_complete_265c8913-d44f-42e4-816a-c23247df5e48_Untitled3.png",
            "vehicleId": 10,
            "driverName": "Vu Th? Phu?ng",
            "driverPhone": "0900000026",
            "vehicleId": 1,
            "status": "COMPLETED"
        }
    ]
}
-------------------------------------------------------------------------------------
Tình huống Customer muốn gia hạn them h hoặc đi lố h
Trường hợp 1: Khách gia hạn h khi còn ongoing
- Notification cho khách và tài khi còn 30p nữa hết h thuê xe.
Cho phép khách gia hạn them h tối đa 2h nếu đằng sau xe trống lịch
path: http://localhost:8080/FleetFlow/api/v1/bookings/49/extend
input:
{
  "requestedByRole": "CUSTOMER",
  "requestedByAccountId": 1,
  "extraUnits": 1
}
output:
{
    "success": true,
    "extensionId": 1,
    "message": "Đã gửi yêu cầu gia hạn, chờ xác nhận trong 10 phút."
}
Chờ sự xác nhận của 2 bên dispatcher và driver trong 10p. Sau 10p thiếu 1 trong 2 xác nhận tự động hiểu là không cho phép gia hạn

Driver confirm gia hạn
path: http://localhost:8080/FleetFlow/api/v1/bookings/bookingId/extend/extensionId/respond
input:
{ "role": "DRIVER", "accountId": 29, "approve": true }
output:
{
    "success": true,
    "message": "Đã ghi nhận đồng ý."
}
Dispatcher confirm gia hạn
path: http://localhost:8080/FleetFlow/api/v1/bookings/49/extend/1/respond
input:
{ "role": "DISPATCHER", "accountId": 19, "approve": true }
output:
{
    "success": true,
    "message": "Đã ghi nhận đồng ý."
}
Sau khi gia hạn thành công. Cập nhật lại returntime trong bookingDetail và cộng them tiền vào cuối bill

Trường hợp 2: Lố h cho phép
Check gps tài xế mỗi 30s rồi notification cho 3 bên dispatcher + driver + customer khi đã quá h thuê.

Check db notification
189	18	49	Chuyến #49 đang quá giờ	Vehicle #10 đang chạy quá ReturnTime.	OVERTIME_STARTED
188	29	49	Chuyến đang kéo dài	Chuyến #49 đã quá ReturnTime. Nhắc khách nếu cần gia hạn chính thức.	OVERTIME_STARTED
187	1	49	Chuyến đang bị tính phí quá giờ	Chuyến #49 đã quá giờ trả xe, đang được tính phí theo giờ.	OVERTIME_STARTED
