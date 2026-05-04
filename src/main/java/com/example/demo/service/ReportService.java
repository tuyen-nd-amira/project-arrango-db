package com.example.demo.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.demo.dto.MovieReport;
import com.example.demo.repository.BookingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final BookingRepository bookingRepository;

    /**
     * PROCEDURE SIMULATION – Báo cáo doanh thu và số vé theo từng phim.
     *
     * ============================================================
     * Đây mô phỏng Stored Procedure trong ArangoDB.
     *
     * ArangoDB hỗ trợ "stored procedure" qua hai cơ chế:
     *   1. AQL User-Defined Functions (UDFs): hàm JS đăng ký server-side,
     *      gọi được từ AQL (xem ArangoConfig.registerStoredProcedures).
     *      Ví dụ: FOR m IN movies RETURN CINEMA::SP_MOVIE_STATS(m._key)
     *
     *   2. Encapsulated AQL complex queries (cách này):
     *      Đóng gói câu truy vấn phức tạp trong một method Java tái sử dụng,
     *      tương đương CREATE PROCEDURE trong SQL.
     *
     * Câu AQL dưới đây dùng COLLECT AGGREGATE – tương đương
     * GROUP BY + SUM + COUNT trong SQL.
     * ============================================================
     */
    public List<MovieReport> getMovieRevenueReport() {
        log.info("[PROCEDURE] SP_MOVIE_REVENUE_REPORT – Tính doanh thu theo phim");
        List<Map> raw = bookingRepository.getRevenueByMovie();
        return raw.stream().map(this::mapToMovieReport).collect(Collectors.toList());
    }

    public MovieReport getMovieReportById(String movieId) {
        log.info("[PROCEDURE] SP_MOVIE_STATS({}) – Tính doanh thu phim", movieId);
        Map raw = bookingRepository.getRevenueByMovieId(movieId);
        return mapToMovieReport(raw);
    }

    public Map<String, Object> getSystemOverview() {
        log.info("[PROCEDURE] SP_SYSTEM_OVERVIEW – Tổng quan hệ thống");
        return bookingRepository.getSystemOverview();
    }

    private MovieReport mapToMovieReport(Map raw) {
        MovieReport r = new MovieReport();
        r.setMovieId(safe(raw, "movieId"));
        r.setMovieTitle(safe(raw, "movieTitle"));
        r.setGenre(safe(raw, "genre"));
        r.setTotalRevenue(toDouble(raw.get("totalRevenue")));
        r.setTotalTickets(toLong(raw.get("totalTickets")));
        r.setTotalBookings(toLong(raw.get("totalBookings")));
        return r;
    }

    private String safe(Map m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0;
    }

    private long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).longValue();
        return 0;
    }
}
