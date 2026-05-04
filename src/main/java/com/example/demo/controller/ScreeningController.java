package com.example.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.SeatMapResponse;
import com.example.demo.model.Screening;
import com.example.demo.service.ScreeningService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/screenings")
@RequiredArgsConstructor
public class ScreeningController {

    private final ScreeningService screeningService;

    @GetMapping
    public ResponseEntity<List<Screening>> getByMovie(@RequestParam String movieId) {
        return ResponseEntity.ok(screeningService.getByMovieId(movieId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Screening> getById(@PathVariable String id) {
        return ResponseEntity.ok(screeningService.getById(id));
    }

    // ---- Sơ đồ ghế ngồi: screening + movie + cinema + trạng thái ghế ----
    @GetMapping("/{id}/seats")
    public ResponseEntity<SeatMapResponse> getSeatMap(@PathVariable String id) {
        return ResponseEntity.ok(screeningService.getSeatMap(id));
    }

    @PostMapping
    public ResponseEntity<Screening> create(@RequestBody Screening screening) {
        return ResponseEntity.ok(screeningService.create(screening));
    }
}
