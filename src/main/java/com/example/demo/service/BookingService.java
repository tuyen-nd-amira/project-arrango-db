package com.example.demo.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.arangodb.ArangoDatabase;
import com.arangodb.entity.StreamTransactionEntity;
import com.arangodb.model.StreamTransactionOptions;
import com.example.demo.dto.BookingRequest;
import com.example.demo.dto.BookingResponse;
import com.example.demo.model.Booking;
import com.example.demo.model.Cinema;
import com.example.demo.model.Movie;
import com.example.demo.model.Screening;
import com.example.demo.model.ScreeningSeat;
import com.example.demo.model.User;
import com.example.demo.repository.BookingRepository;
import com.example.demo.repository.CinemaRepository;
import com.example.demo.repository.MovieRepository;
import com.example.demo.repository.ScreeningRepository;
import com.example.demo.repository.ScreeningSeatRepository;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final ArangoDatabase db;
    private final BookingRepository bookingRepository;
    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final CinemaRepository cinemaRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public List<Booking> getUserBookings(String userId) {
        return bookingRepository.findByUserId(userId);
    }

    /**
     * Đặt vé xem phim.
     *
     * ============================================================
     * TRANSACTION – ArangoDB Stream Transaction
     * ============================================================
     * Sử dụng Stream Transaction để đảm bảo tính ACID khi đặt vé:
     *
     *  1. Kiểm tra ghế còn trống (READ trong tx)
     *  2. Tạo document Booking        (WRITE trong tx)
     *  3. Cập nhật trạng thái ghế    (WRITE trong tx)
     *  4. Cộng tổng chi tiêu user    (WRITE trong tx)
     *
     * Nếu bất kỳ bước nào thất bại → abortStreamTransaction()
     *   → toàn bộ thay đổi bị rollback, dữ liệu giữ nguyên.
     * Nếu tất cả thành công       → commitStreamTransaction()
     *
     * Đây là cách ArangoDB đảm bảo: không thể xảy ra tình huống
     * 2 user cùng đặt 1 ghế, hoặc tiền bị trừ nhưng vé không được tạo.
     * ============================================================
     */
    public BookingResponse createBooking(BookingRequest request) {
        StreamTransactionEntity tx = null;
        try {
            // Khai báo collections sẽ đọc/ghi trong transaction
            tx = db.beginStreamTransaction(
                new StreamTransactionOptions()
                    .readCollections("screenings", "movies", "users", "seats", "booking_seats")
                    .writeCollections("bookings", "users", "booking_seats", "audit_logs")
            );
            String txId = tx.getId();
            log.info("[TRANSACTION] Started – txId: {}", txId);

            // ── Bước 1: Kiểm tra suất chiếu ──────────────────────────
            Screening screening = screeningRepository.findByKeyWithTx(request.getScreeningId(), txId);
            if (screening == null) {
                throw new RuntimeException("Suất chiếu không tồn tại");
            }
            if (!"active".equals(screening.getStatus())) {
                throw new RuntimeException("Suất chiếu đã kết thúc hoặc bị huỷ");
            }

            // ── Bước 2: Kiểm tra từng ghế còn trống ──────────────────
            List<ScreeningSeat> selectedSeats = new ArrayList<>();
            for (String seatKey : request.getSeatKeys()) {
                ScreeningSeat seat = seatRepository.findByKeyWithTx(seatKey, txId);
                if (seat == null) {
                    throw new RuntimeException("Ghế không tồn tại: " + seatKey);
                }
                if (!seatRepository.seatBelongsToRoomWithTx(seatKey, screening.getCinemaId(), txId)) {
                    throw new RuntimeException("Ghế không thuộc phòng chiếu của suất này: " + seatKey);
                }
                if (seatRepository.isSeatBookedForScreeningWithTx(seatKey, request.getScreeningId(), txId)) {
                    throw new RuntimeException("Ghế " + seat.getSeatLabel() + " đã có người đặt");
                }
                selectedSeats.add(seat);
            }

            // ── Bước 3: Tạo Booking document ─────────────────────────
            Movie movie = movieRepository.findByKeyWithTx(screening.getMovieId(), txId);
            double totalAmount = screening.getPrice() * selectedSeats.size();

            Booking booking = new Booking();
            booking.setUserId(request.getUserId());
            booking.setScreeningId(request.getScreeningId());
            booking.setMovieId(screening.getMovieId());
            booking.setBookingCode(generateBookingCode());
            booking.setSeatKeys(request.getSeatKeys());
            booking.setSeatLabels(selectedSeats.stream()
                    .map(ScreeningSeat::getSeatLabel).collect(Collectors.toList()));
            booking.setTotalAmount(totalAmount);
            booking.setStatus("confirmed");
            booking.setCreatedAt(LocalDateTime.now().toString());
            booking.setMovieTitle(movie != null ? movie.getTitle() : "");
            booking.setShowTime(screening.getShowTime());
            Cinema room = cinemaRepository.findByKey(screening.getCinemaId());
            booking.setCinemaName(room != null ? room.getName() : "");

            Booking saved = bookingRepository.saveWithTx(booking, txId);
            log.info("[TRANSACTION] Booking created – bookingId: {}", saved.getKey());

            // ── Bước 4: Tạo edge bookings -> seats trong booking_seats ──
            bookingRepository.createSeatEdgesWithTx(saved.getKey(), request.getScreeningId(), request.getSeatKeys(), txId);
            log.info("[TRANSACTION] {} booking_seats edges created", request.getSeatKeys().size());

            // ── Bước 5: Cộng tổng chi tiêu của user ──────────────────
            userRepository.addSpendingWithTx(request.getUserId(), totalAmount, txId);

                bookingRepository.createAuditLogWithTx(
                    "BOOKING_CREATED",
                    saved.getKey(),
                    java.util.Map.of(
                        "userKey", request.getUserId(),
                        "screeningKey", request.getScreeningId(),
                        "seatCount", request.getSeatKeys().size(),
                        "totalAmount", totalAmount
                    ),
                    txId
                );

            // ── Commit – xác nhận toàn bộ thay đổi ───────────────────
            db.commitStreamTransaction(txId);
            log.info("[TRANSACTION] Committed – txId: {}", txId);

            // ============================================================
            // TRIGGER SIMULATION – Cập nhật hạng thành viên
            // ============================================================
            // Sau khi transaction commit xong, gọi trigger để kiểm tra và
            // nâng hạng thành viên nếu đủ điều kiện.
            // Trong SQL: đây là AFTER UPDATE TRIGGER trên bảng users.
            userService.updateMemberRankTrigger(request.getUserId());

            User updatedUser = userRepository.findByKey(request.getUserId());
            return new BookingResponse(saved, updatedUser, "Đặt vé thành công! Chúc bạn xem phim vui vẻ 🎬");

        } catch (Exception e) {
            // ── Rollback nếu có lỗi ───────────────────────────────────
            if (tx != null) {
                try {
                    db.abortStreamTransaction(tx.getId());
                    log.warn("[TRANSACTION] Aborted – txId: {}", tx.getId());
                } catch (Exception ex) {
                    log.error("[TRANSACTION] Error aborting transaction", ex);
                }
            }
            throw new RuntimeException("Đặt vé thất bại: " + e.getMessage(), e);
        }
    }

    private String generateBookingCode() {
        long now = System.currentTimeMillis();
        int random = java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000);
        return "BK" + now + random;
    }
}
