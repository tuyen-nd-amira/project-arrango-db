package com.example.demo.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDatabase;
import com.arangodb.model.AqlQueryOptions;
import com.example.demo.model.Booking;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BookingRepository {

    private final ArangoDatabase db;

    private static class RevenueStats {
        public String movieId;
        public String movieTitle;
        public double totalRevenue;
        public long totalTickets;
        public long totalBookings;
    }

    private static class SystemOverview {
        public double totalRevenue;
        public long totalTickets;
        public long totalBookings;
        public long totalUsers;
    }

    // ---- Lấy lịch sử đặt vé của user ----
    public List<Booking> findByUserId(String userId) {
        String aql =
            "FOR b IN bookings FILTER b.user_key == @uid " +
            "SORT b.created_at DESC " +
            "RETURN { " +
            "  _key: b._key, _id: b._id, " +
            "  userId: b.user_key, screeningId: b.screening_key, movieId: b.movie_key, " +
            "  bookingCode: b.booking_code, " +
            "  seatKeys: b.seat_keys, seatLabels: b.seat_labels, " +
            "  totalAmount: b.total_amount, status: b.status, createdAt: b.created_at, holdExpiresAt: b.hold_expires_at, " +
            "  movieTitle: b.movie_title, showTime: b.show_time, cinemaName: b.cinema_name " +
            "}";
        Map<String, Object> bind = new HashMap<>();
        bind.put("uid", userId);
        ArangoCursor<Booking> cursor = db.query(aql, bind, null, Booking.class);
        return cursor.asListRemaining();
    }

    public Booking findByKey(String key) {
        String aql =
            "FOR b IN bookings FILTER b._key == @key LIMIT 1 " +
            "RETURN { " +
            "  _key: b._key, _id: b._id, " +
            "  userId: b.user_key, screeningId: b.screening_key, movieId: b.movie_key, " +
            "  bookingCode: b.booking_code, " +
            "  seatKeys: b.seat_keys, seatLabels: b.seat_labels, " +
            "  totalAmount: b.total_amount, status: b.status, createdAt: b.created_at, holdExpiresAt: b.hold_expires_at, " +
            "  movieTitle: b.movie_title, showTime: b.show_time, cinemaName: b.cinema_name " +
            "}";
        Map<String, Object> bind = new HashMap<>();
        bind.put("key", key);
        ArangoCursor<Booking> cursor = db.query(aql, bind, null, Booking.class);
        List<Booking> result = cursor.asListRemaining();
        return result.isEmpty() ? null : result.get(0);
    }

    // ---- INSERT trong transaction ----
    public Booking saveWithTx(Booking booking, String txId) {
        String aql =
            "INSERT { " +
            "  booking_code: @bookingCode, " +
            "  user_key: @userKey, screening_key: @screeningKey, movie_key: @movieKey, " +
            "  seat_keys: @seatKeys, seat_labels: @seatLabels, " +
            "  total_amount: @totalAmount, status: @status, created_at: @createdAt, " +
            "  movie_title: @movieTitle, show_time: @showTime, cinema_name: @cinemaName " +
            "} INTO bookings RETURN NEW";
        Map<String, Object> bind = new HashMap<>();
        bind.put("bookingCode", booking.getBookingCode());
        bind.put("userKey", booking.getUserId());
        bind.put("screeningKey", booking.getScreeningId());
        bind.put("movieKey", booking.getMovieId());
        bind.put("seatKeys", booking.getSeatKeys());
        bind.put("seatLabels", booking.getSeatLabels());
        bind.put("totalAmount", booking.getTotalAmount());
        bind.put("status", booking.getStatus());
        bind.put("createdAt", booking.getCreatedAt());
        bind.put("movieTitle", booking.getMovieTitle());
        bind.put("showTime", booking.getShowTime());
        bind.put("cinemaName", booking.getCinemaName());

        ArangoCursor<Booking> cursor = db.query(
                aql,
                bind,
                new AqlQueryOptions().streamTransactionId(txId),
                Booking.class
        );
        Booking saved = cursor.next();
        booking.setKey(saved.getKey());
        booking.setId(saved.getId());
        return booking;
    }

    public void createSeatEdgesWithTx(String bookingKey, String screeningKey, List<String> seatKeys, String txId) {
        String aql =
            "FOR seatKey IN @seatKeys " +
            "INSERT { " +
            "  _from: CONCAT('bookings/', @bookingKey), " +
            "  _to: CONCAT('seats/', seatKey), " +
            "  screening_key: @screeningKey, " +
            "  booking_status: 'confirmed', " +
            "  created_at: @createdAt " +
            "} INTO booking_seats";
        Map<String, Object> bind = new HashMap<>();
        bind.put("seatKeys", seatKeys);
        bind.put("bookingKey", bookingKey);
        bind.put("screeningKey", screeningKey);
        bind.put("createdAt", java.time.LocalDateTime.now().toString());
        db.query(aql, bind, new AqlQueryOptions().streamTransactionId(txId), Void.class);
    }

    public void createAuditLogWithTx(String action, String entityKey, Map<String, Object> payload, String txId) {
        String aql =
            "INSERT { " +
            "  action: @action, entity_key: @entityKey, payload: @payload, created_at: @createdAt " +
            "} INTO audit_logs";
        Map<String, Object> bind = new HashMap<>();
        bind.put("action", action);
        bind.put("entityKey", entityKey);
        bind.put("payload", payload);
        bind.put("createdAt", java.time.LocalDateTime.now().toString());
        db.query(aql, bind, new AqlQueryOptions().streamTransactionId(txId), Void.class);
    }

    // ---- Tổng doanh thu theo phim (dùng Stored Procedure: CINEMA::SP_MOVIE_STATS) ----
    public List<Map<String, Object>> getRevenueByMovie() {
        String aql =
            "FOR m IN movies " +
            "FILTER m.status == null OR m.status IN ['active', 'showing', 'coming_soon'] " +
            "LET stats = CINEMA::SP_MOVIE_STATS(m._key) " +
            "LET totalBookings = LENGTH(" +
            "  FOR b IN bookings " +
            "  FILTER b.movie_key == m._key AND b.status == 'confirmed' " +
            "  RETURN 1" +
            ") " +
            "SORT stats.totalRevenue DESC " +
            "RETURN { " +
            "  movieId: m._key, " +
            "  movieTitle: m.title, " +
            "  totalRevenue: stats.totalRevenue, " +
            "  totalTickets: stats.totalTickets, " +
            "  totalBookings: totalBookings " +
            "}";
        ArangoCursor<RevenueStats> cursor = db.query(aql, null, null, RevenueStats.class);
        return cursor.asListRemaining().stream()
            .map(this::toRevenueMap)
                .toList();
    }

    // ---- Doanh thu + vé của một phim cụ thể (Stored Procedure) ----
    public Map<String, Object> getRevenueByMovieId(String movieId) {
        String aql =
            "LET movie = DOCUMENT('movies', @mid) " +
            "LET stats = CINEMA::SP_MOVIE_STATS(@mid) " +
            "LET totalBookings = LENGTH(" +
            "  FOR b IN bookings " +
            "  FILTER b.movie_key == @mid AND b.status == 'confirmed' " +
            "  RETURN 1" +
            ") " +
            "RETURN { " +
            "  movieId: @mid, " +
            "  movieTitle: movie == null ? '' : movie.title, " +
            "  totalRevenue: stats.totalRevenue, " +
            "  totalTickets: stats.totalTickets, " +
            "  totalBookings: totalBookings " +
            "}";
        Map<String, Object> bind = new HashMap<>();
        bind.put("mid", movieId);
        ArangoCursor<RevenueStats> cursor = db.query(aql, bind, null, RevenueStats.class);
        List<Map<String, Object>> result = cursor.asListRemaining().stream()
            .map(this::toRevenueMap)
                .toList();
        return result.isEmpty() ? new HashMap<>() : result.get(0);
    }

    // ---- Tổng quan hệ thống (Stored Procedure: CINEMA::SP_SYSTEM_OVERVIEW) ----
    public Map<String, Object> getSystemOverview() {
        String aql = "RETURN CINEMA::SP_SYSTEM_OVERVIEW()";
        ArangoCursor<SystemOverview> cursor = db.query(aql, null, null, SystemOverview.class);
        if (!cursor.hasNext()) {
            return new HashMap<>();
        }
        return toOverviewMap(cursor.next());
    }

    private Map<String, Object> toRevenueMap(RevenueStats row) {
        Map<String, Object> mapped = new HashMap<>();
        mapped.put("movieId", row.movieId);
        mapped.put("movieTitle", row.movieTitle);
        mapped.put("totalRevenue", row.totalRevenue);
        mapped.put("totalTickets", row.totalTickets);
        mapped.put("totalBookings", row.totalBookings);
        return mapped;
    }

    private Map<String, Object> toOverviewMap(SystemOverview row) {
        Map<String, Object> mapped = new HashMap<>();
        mapped.put("totalRevenue", row.totalRevenue);
        mapped.put("totalTickets", row.totalTickets);
        mapped.put("totalBookings", row.totalBookings);
        mapped.put("totalUsers", row.totalUsers);
        return mapped;
    }
}
