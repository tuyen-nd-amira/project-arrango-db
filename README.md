# Cinema Booking Demo

## 1. Tong quan du an
Cinema Booking Demo la he thong dat ve xem phim xay dung bang:
- Backend: Spring Boot 3.5 + Java 17
- Database: ArangoDB (document + edge + graph)
- Frontend: HTML/CSS/JS (static)
- Auth: JWT Access Token

Tinh nang chinh:
- Dang ky, dang nhap nguoi dung
- Xem danh sach phim, loc theo the loai
- Xem suat chieu, dat ghe, dat ve
- Bao cao doanh thu theo phim va tong quan he thong
- Seed du lieu phim/room/screening tu CSV khi khoi dong

## 2. Kien truc du lieu ArangoDB
### Collections
- users
- movies
- rooms
- seats
- screenings
- bookings
- audit_logs

### Edge collection
- booking_seats (ket noi bookings -> seats)

### Graph
- cinema_graph
  - edge: booking_seats
  - orphan: users, movies, rooms, screenings, audit_logs

### Chi tiet schema theo collection

1) users
- Muc dich: thong tin nguoi dung + vai tro
- Field chinh:
  - _key
  - username (unique)
  - email (unique)
  - full_name
  - password
  - role (user | vip | premium | admin)
  - totalSpent
  - createdAt

2) movies
- Muc dich: thong tin phim de hien thi va dat ve
- Field chinh:
  - _key
  - title
  - description
  - duration
  - poster_url
  - status (showing | coming_soon | active)

3) rooms
- Muc dich: thong tin phong chieu
- Field chinh:
  - _key
  - name
  - capacity
  - type (standard | vip | imax)

4) seats
- Muc dich: danh sach ghe cua tung phong
- Field chinh:
  - _key
  - room_key
  - seat_row
  - seat_number
  - type

5) screenings
- Muc dich: suat chieu cua phim
- Field chinh:
  - _key
  - movie_key
  - room_key
  - start_time
  - price
  - status

6) bookings
- Muc dich: phieu dat ve
- Field chinh:
  - _key
  - booking_code (unique)
  - user_key
  - screening_key
  - movie_key
  - seat_keys
  - seat_labels
  - total_amount
  - status (confirmed | cancelled)
  - created_at
  - movie_title, show_time, cinema_name (snapshot de doc nhanh)

7) booking_seats (edge)
- Muc dich: lien ket bookings -> seats de truy van 1 ve da giu ghe nao
- Field chinh:
  - _from = bookings/<bookingKey>
  - _to = seats/<seatKey>
  - screening_key
  - booking_status
  - created_at

8) audit_logs
- Muc dich: nhat ky he thong cho hanh dong dat ve
- Field chinh:
  - _key
  - action (vi du: create_booking)
  - entity_key
  - payload
  - created_at

### Index quan trong
- users.email (unique hash)
- users.username (unique hash)
- seats(room_key, seat_row, seat_number) (unique persistent)
- screenings(movie_key, start_time) (persistent)
- bookings.booking_code (unique persistent)
- bookings.user_key (hash)
- bookings.screening_key (hash)
- bookings.status (hash)
- booking_seats(_to, screening_key) (unique persistent)

### AQL kiem tra nhanh du lieu
```aql
// 1) Dem so document theo collection
RETURN {
  users: LENGTH(users),
  movies: LENGTH(movies),
  rooms: LENGTH(rooms),
  seats: LENGTH(seats),
  screenings: LENGTH(screenings),
  bookings: LENGTH(bookings),
  booking_seats: LENGTH(booking_seats),
  audit_logs: LENGTH(audit_logs)
}
```

```aql
// 2) Xem 1 booking dang giu nhung ghe nao qua edge booking_seats
LET b = DOCUMENT('bookings', @bookingKey)
FOR e IN booking_seats
  FILTER e._from == b._id
  FOR s IN seats
    FILTER s._id == e._to
    RETURN {
      booking: b.booking_code,
      seat_key: s._key,
      room_key: s.room_key,
      seat_row: s.seat_row,
      seat_number: s.seat_number
    }
```

```aql
// 3) Kiem tra ghe da bi dat trong 1 screening
FOR e IN booking_seats
  FILTER e.screening_key == @screeningKey AND e.booking_status == 'confirmed'
  RETURN PARSE_IDENTIFIER(e._to).key
```

## 3. Chay du an bang Docker Compose
### 3.1. Yeu cau
- Docker
- Docker Compose

### 3.2. Cau hinh moi truong
Tao file .env (co the copy tu .env.example):

```bash
cp .env.example .env
```

Bien quan trong:
- ARANGO_PASSWORD
- ARANGO_DATABASE
- ARANGO_USER
- APP_SEED_MODE=reset|smart
- APP_SEED_RESET_USERS=false|true
- JWT_SECRET
- JWT_ACCESS_TTL_SECONDS

### 3.3. Khoi dong
```bash
docker compose up -d --build
```

### 3.4. Truy cap
- App: http://localhost:8080
- ArangoDB UI: http://localhost:8529

### 3.5. Xem log
```bash
docker compose logs -f app
docker compose logs -f arangodb
```

### 3.6. Dung he thong
```bash
docker compose down
```

## 4. Co che seed du lieu mock
DataInitializer ho tro 2 che do:
- smart: chi seed khi thieu du lieu
- reset: xoa du lieu mock va seed lai

Tu dong giu users khi reset (de khong mat tai khoan da dang ky):
- APP_SEED_RESET_USERS=false (mac dinh)

Neu muon reset luon users:
- APP_SEED_RESET_USERS=true

## 5. Transaction duoc dung nhu the nao
Dat ve su dung ArangoDB Stream Transaction trong BookingService:
1. Begin transaction voi cac collection read/write can thiet
2. Kiem tra suat chieu con active
3. Kiem tra ghe thuoc room va chua duoc dat
4. Insert booking
5. Insert edge booking_seats
6. Update tong chi tieu user
7. Insert audit_log
8. Commit neu thanh cong, hoac abort neu co loi

Muc tieu:
- Dam bao ACID
- Tranh double booking cung 1 ghe
- Rollback toan bo neu bat ky buoc nao that bai

## 6. Procedure (AQL UDF) duoc dung nhu the nao
Tai startup, ArangoConfig dang ky cac AQL UDF:
- CINEMA::SP_MOVIE_STATS(movieId)
  - Tinh tong doanh thu va tong so ve cua 1 phim
- CINEMA::SP_USER_RANK(totalSpent)
  - Tra ve rank: normal | vip | premium
- CINEMA::SP_SYSTEM_OVERVIEW()
  - Tong hop totalRevenue, totalTickets, totalBookings, totalUsers

Cac UDF nay duoc goi trong repository de lam bao cao va tinh rank, thay vi viet logic SQL procedure truyen thong.

## 7. Trigger duoc mo phong nhu the nao
Du an khong dung database trigger native. Thay vao do dung application-level trigger:
- Sau khi dat ve commit thanh cong
- BookingService goi UserService.updateMemberRankTrigger(userId)
- UserService goi procedure CINEMA::SP_USER_RANK(totalSpent) de cap nhat hang thanh vien

Y nghia:
- Tuong duong AFTER UPDATE trigger tren users trong he thong SQL
- De quan sat, de test, de bao tri trong code Java

## 8. Auth JWT trong du an
- Dang ky/dang nhap tra ve accessToken (Bearer)
- Frontend gan Authorization: Bearer <token> cho API request
- API nhay cam (bookings, users profile) duoc interceptor kiem tra token
- Thoi gian song token duoc cau hinh qua JWT_ACCESS_TTL_SECONDS

## 9. API chinh
- POST /api/users/register
- POST /api/users/login
- GET /api/users/{id}
- GET /api/movies
- GET /api/screenings?movieId=<id>
- POST /api/bookings
- GET /api/bookings/user/{userId}
- GET /api/reports/overview
- GET /api/reports/movies

## 10. Luu y van hanh
- Moi truong demo hien tai luu mat khau plain text (khong phu hop production)
- Nen doi JWT_SECRET trong production
- Khuyen nghi bo sung hash mat khau (BCrypt) neu dua len moi truong that
