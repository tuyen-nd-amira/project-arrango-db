package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.MovieReport;
import com.example.demo.service.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // ---- Tổng quan hệ thống (Procedure: SP_SYSTEM_OVERVIEW) ----
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(reportService.getSystemOverview());
    }

    // ---- Doanh thu theo từng phim (Procedure: SP_MOVIE_REVENUE_REPORT) ----
    @GetMapping("/movies")
    public ResponseEntity<List<MovieReport>> getMovieReports() {
        return ResponseEntity.ok(reportService.getMovieRevenueReport());
    }

    // ---- Doanh thu một phim cụ thể (Procedure: SP_MOVIE_STATS) ----
    @GetMapping("/movies/{movieId}")
    public ResponseEntity<MovieReport> getMovieReport(@PathVariable String movieId) {
        return ResponseEntity.ok(reportService.getMovieReportById(movieId));
    }
}
