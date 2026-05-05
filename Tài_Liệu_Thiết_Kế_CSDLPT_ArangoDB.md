# TÀI LIỆU PHÂN TÍCH, THIẾT KẾ VÀ TRIỂN KHAI CSDL PHÂN TÁN CHO HỆ THỐNG ĐẶT VÉ XEM PHIM (CINEMA BOOKING)

## 2. Viết tài liệu

### 2.1. Đặt vấn đề:
**Nhu cầu và tầm quan trọng của dự án:**
Trong bối cảnh ngành công nghiệp điện ảnh giải trí đang phát triển mạnh mẽ, số lượng khách hàng đặt vé xem phim qua các nền tảng trực tuyến (Web/App) tăng đột biến. Việc đảm bảo hệ thống có thể chịu tải cao, hoạt động ổn định và xử lý hàng ngàn giao dịch đặt chỗ cùng lúc là cực kỳ quan trọng. Hệ thống tập trung (Centralized) truyền thống bộc lộ nhiều điểm yếu như "nút thắt cổ chai" (bottleneck), một điểm lỗi duy nhất (Single Point of Failure - SPOF), và giới hạn về khả năng mở rộng (Scale-up). Do đó, dự án "Hệ thống đặt vé xem phim sử dụng CSDL phân tán (ArangoDB)" được ra đời để giải quyết các bài toán về hiệu suất, tính sẵn sàng và toàn vẹn dữ liệu.

**Sơ lược về dự án - Một số nhiệm vụ chính cần thực hiện:**
- Quản lý danh mục phim, lịch chiếu, phòng chiếu và sơ đồ ghế ngồi.
- Xử lý giao dịch đặt vé nhanh chóng, đảm bảo tính nhất quán (không xảy ra tình trạng đặt trùng ghế).
- Phân quyền người dùng (Khách hàng, Quản trị viên).
- Cung cấp API tích hợp cho Website và App di động.

**Sự cần thiết sử dụng CSDL phân tán (CSDLPT):**
- **Vị trí và nhiệm vụ:** Hệ thống có thể phục vụ khách hàng trên toàn quốc. Phân mảnh (Sharding) dữ liệu vé và lịch chiếu dựa trên khu vực hoặc mã rạp giúp các truy vấn tại chi nhánh cụ thể (hoặc theo cụm rạp) diễn ra nhanh chóng hơn do dữ liệu được phân tán tới các DBServer gần nhất.
- **Khả năng mở rộng (Scalability):** Hệ thống chia nhỏ dữ liệu thành các Shard (phân mảnh) lưu trên nhiều máy chủ (DBServers).
- **Tính chịu lỗi (Fault Tolerance):** Dữ liệu được nhân bản (Replication Factor = 2) đảm bảo nếu một Server gặp sự cố, hệ thống vẫn hoạt động bình thường, không gây gián đoạn quy trình mua vé của khách.
- **Các đối tượng tham gia:** 
  - *Khách hàng (Client/User):* Xem phim, đặt vé, thanh toán trực tuyến.
  - *Quản lý rạp (Admin/Manager):* Quản lý lịch chiếu, thống kê doanh thu theo thời gian thực.
  - *Hệ thống back-end:* Giao tiếp trực tiếp với các node Coordinator của ArangoDB.

---

### 2.2. Phân tích và Thiết kế

#### 2.2.1. Phân tích

**a/ Các chức năng chính truy cập vào dữ liệu và bảng tần suất:**
- **Tìm kiếm/Xem phim & Lịch chiếu:** (Tần suất rất cao - Đọc)
- **Kiểm tra trạng thái ghế trống:** (Tần suất cao - Đọc)
- **Giao dịch đặt vé (Booking):** (Tần suất trung bình/cao - Ghi/Cập nhật)
- **Thống kê doanh thu:** (Tần suất thấp - Đọc/Tính toán phức tạp)

**b/ Phân quyền cho các nhóm đối tượng:**
- **User (Khách hàng):** Read-only danh mục phim/lịch chiếu. Write cho giao dịch đặt vé của cá nhân.
- **Admin (Quản trị hệ thống):** Full quyền (Read/Write/Delete) trên tất cả các collections (phim, rạp, user).
- **Manager (Quản lý chi nhánh rạp):** Quyền Read/Write giới hạn tại cụm rạp quản lý.

**c/ Phân tích chức năng theo vị trí:**
- **Máy trạm (Client - Web/Mobile App):** Hiển thị UI, xử lý tương tác người dùng, gửi Request tới API Gateway, nhận và hiển thị kết quả. Không lưu trữ logic nghiệp vụ lõi.
- **Máy chủ (Server - Spring Boot App):** Cung cấp RESTful API, thực thi business logic (kiểm tra ghế, thanh toán, JWT Token), giao tiếp với hệ thống ArangoDB Cluster.
- **Máy chủ CSDL (Database Nodes):** Xử lý truy vấn AQL, quản lý khóa ghế (Foxx Microservices/Transactions) và đồng bộ phân tán.

**d/ Mô hình thực thể liên kết (Entity-Relationship):**
Hệ thống sử dụng mô hình Graph-Document của ArangoDB với các Collection:
- *Document Collections:* `users`, `movies`, `rooms`, `seats`, `screenings`, `bookings`, `audit_logs`
- *Edge Collection:* `booking_seats` (Biểu diễn mối quan hệ đặt chỗ từ `bookings` đến `seats`).
- Graph *cinema_graph* quản lý toàn trình quan hệ: Người dùng -> Đặt vé -> Ghế ngồi.

#### 2.2.2. Thiết kế

**a/ Thiết kế CSDL của hệ thống:**
ArangoDB là CSDL đa mô hình (Document & Graph). Thiết kế bao gồm:
- **users:** `_key`, `username`, `email`, `password`, `role`.
- **movies:** `_key`, `title`, `description`, `duration`, `status`.
- **rooms:** `_key`, `name`, `capacity`.
- **seats:** `_key`, `room_key`, `row`, `number`, `type`.
- **screenings:** `_key`, `movie_key`, `room_key`, `start_time`, `price`.
- **bookings:** `_key`, `user_key`, `screening_key`, `status`, `total_amount`.
- **booking_seats (Edge):** `_from` (trỏ tới `bookings`), `_to` (trỏ tới `seats`), đại diện cho việc một vé đã giữ chỗ một ghế nhất định.

*Thuộc tính phân tán:* Các collection được thiết lập `numberOfShards: 3` (3 phân mảnh dữ liệu) và `replicationFactor: 2` (nhân bản dữ liệu lên 2 server để dự phòng rủi ro).

**b/ Thiết kế Kiến trúc của hệ thống (QTLPT):**
- **Mô hình kiến trúc:** Client/Server (với Database Cluster).
- **Kiến trúc CSDL phân tán ArangoDB Cluster:**
  - *Agency (Agents):* Quản lý trạng thái và cấu hình của cluster (bầu chọn Leader).
  - *Coordinators:* Điểm tiếp nhận request từ Backend (Spring Boot). Điều phối truy vấn (AQL) đến các DBServer và gom kết quả.
  - *DBServers:* Lưu trữ dữ liệu thực tế (Shards). Dữ liệu được băm (Hash) theo `_key` để phân phối đều trên 3 Shard.
- **Đường đồng bộ hóa / LinkServer:** Các DBServer tự động đồng bộ hóa với nhau thông qua giao thức của ArangoDB (RocksDB engine) theo cơ chế Replication. Coordinator ẩn giấu hoàn toàn sự phức tạp này với tầng ứng dụng.
- **Mô hình triển khai nhánh (Front-end & Back-end):**
  - **Front-end:** SPA (React/Vue/Angular) triển khai trên CDN.
  - **Back-end:** Stateless Spring Boot REST API triển khai trên Docker/Kubernetes, gọi tới Load Balancer của cụm ArangoDB Coordinators.

---

### 3. Cài đặt vật lý thực tế

#### 3.3. Cài đặt hệ quản trị CSDL (ArangoDB Cluster)
Trong dự án này, ArangoDB được cấu hình triển khai cụm phân tán (Cluster) sử dụng tính năng **Local Starter (`--starter.local`)** thông qua Docker Compose.

**Cấu hình Docker Compose (Cluster Mode):**
```yaml
  arangodb:
    image: arangodb:3.12
    container_name: cinema_arangodb
    environment:
      ARANGODB_DEFAULT_ROOT_PASSWORD: ${ARANGO_PASSWORD:-123456}
      ARANGO_ROOT_PASSWORD: ${ARANGO_PASSWORD:-123456}
    command: >
      arangodb
      --starter.local
      --all.log.level=warning
    ports:
      - "8528:8528" # Starter / Master
      - "8529:8529" # Coordinator endpoint
```
*Ghi chú:* Chế độ `--starter.local` tự động tạo ra một cụm CSDL thu nhỏ gồm 3 Agents, 3 DBServers, và 3 Coordinators chạy bên trong cùng một Container, mô phỏng hoàn hảo môi trường phân tán thực tế.

**Tích hợp trên Java (Spring Boot):**
Trong `ArangoConfig.java`, quy tắc phân tán được định nghĩa khi khởi tạo DB:
```java
db.createCollection("bookings", new CollectionCreateOptions()
    .type(CollectionType.DOCUMENT)
    .replicationFactor(2)  // Nhân bản dữ liệu 2 bản
    .numberOfShards(3));   // Chia làm 3 phân mảnh

db.createGraph(
    "cinema_graph",
    List.of(new EdgeDefinition().collection("booking_seats").from("bookings").to("seats")),
    new GraphCreateOptions()
        .replicationFactor(2)
        .numberOfShards(3)
);
```

*(Sinh viên có thể chụp màn hình (Print Screen): 1. Giao diện Web ArangoDB tại `http://localhost:8529` ở tab `Nodes` hoặc `Cluster` để thấy các server phân tán. 2. Tab `Collections` -> Settings của collection `bookings` để thấy Shards = 3, Replication = 2).*

---

### 4. Phân tích thiết kế và xây dựng app or web trên hệ thống CSDL đã xây dựng
- **Backend (Spring Boot):** Sử dụng `arangodb-java-driver` cấu hình kết nối TCP tới điểm cuối của Coordinator (`localhost:8529`). 
- **Bảo mật:** Triển khai JWT Token cho việc xác thực (Authentication).
- **Toàn vẹn giao dịch (ACID):** Do môi trường phân tán có độ trễ đồng bộ, ứng dụng sử dụng ArangoDB **Foxx Microservices** (script JavaScript chạy trực tiếp trên Coordinator) để quản lý Transaction giữ chỗ (Booking Transaction), đảm bảo hai khách hàng không thể đặt trùng 1 ghế trong cùng 1 khoảng thời gian.
- **Frontend/Mobile:** Xây dựng giao diện Web lấy API từ backend. Giao diện thiết kế hướng tới luồng (Flow): Chọn Phim -> Chọn Suất Chiếu -> Chọn Ghế (thời gian thực) -> Thanh toán.
