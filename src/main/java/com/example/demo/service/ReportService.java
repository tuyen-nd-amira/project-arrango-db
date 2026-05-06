package com.example.demo.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.demo.dto.MovieReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public List<MovieReport> getMovieRevenueReport() {
        log.info("[FOXX PROCEDURE] Gọi GET /reports/movies từ Foxx Service");
        try {
            String json = bookingService.callFoxxGet("/reports/movies");
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            return raw.stream().map(this::mapToMovieReport).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Lỗi khi lấy báo cáo phim từ Foxx", e);
            throw new RuntimeException("Lỗi Foxx: " + e.getMessage());
        }
    }

    public MovieReport getMovieReportById(String movieId) {
        log.info("[FOXX PROCEDURE] Gọi GET /reports/movies/{} từ Foxx Service", movieId);
        try {
            String json = bookingService.callFoxxGet("/reports/movies/" + movieId);
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return mapToMovieReport(raw);
        } catch (Exception e) {
            log.error("Lỗi khi lấy báo cáo phim {} từ Foxx", movieId, e);
            throw new RuntimeException("Lỗi Foxx: " + e.getMessage());
        }
    }

    public Map<String, Object> getSystemOverview() {
        log.info("[FOXX PROCEDURE] Gọi GET /reports/overview từ Foxx Service");
        try {
            String json = bookingService.callFoxxGet("/reports/overview");
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Lỗi khi lấy tổng quan hệ thống từ Foxx", e);
            throw new RuntimeException("Lỗi Foxx: " + e.getMessage());
        }
    }

    private MovieReport mapToMovieReport(Map<String, Object> raw) {
        MovieReport r = new MovieReport();
        r.setMovieId(safe(raw, "movieId"));
        r.setMovieTitle(safe(raw, "movieTitle"));
        r.setTotalRevenue(toDouble(raw.get("totalRevenue")));
        r.setTotalTickets(toLong(raw.get("totalTickets")));
        r.setTotalBookings(toLong(raw.get("totalBookings")));
        return r;
    }

    private String safe(Map<String, Object> m, String key) {
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
