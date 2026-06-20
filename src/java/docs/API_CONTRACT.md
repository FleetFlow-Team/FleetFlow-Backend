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