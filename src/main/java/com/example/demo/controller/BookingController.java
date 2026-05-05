package com.example.demo.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.BookingRequest;
import com.example.demo.dto.BookingResponse;
import com.example.demo.service.BookingService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // ---- Lịch sử đặt vé của user ----
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserBookings(@PathVariable String userId, HttpServletRequest request) {
        String authUserId = (String) request.getAttribute("authUserId");
        if (authUserId == null || !authUserId.equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Không có quyền xem lịch sử của tài khoản khác"));
        }
        return ResponseEntity.ok(bookingService.getUserBookings(userId));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<?> getBookingDetail(@PathVariable String bookingId, HttpServletRequest request) {
        String authUserId = (String) request.getAttribute("authUserId");
        if (authUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Thiếu thông tin xác thực"));
        }
        try {
            return ResponseEntity.ok(bookingService.getBookingByIdForUser(bookingId, authUserId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ---- Đặt vé (có Transaction) ----
    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody BookingRequest request, HttpServletRequest httpRequest) {
        try {
            String authUserId = (String) httpRequest.getAttribute("authUserId");
            if (authUserId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Thiếu thông tin xác thực"));
            }
            if (request.getUserId() != null && !request.getUserId().isBlank() && !authUserId.equals(request.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Không thể đặt vé cho tài khoản khác"));
            }
            request.setUserId(authUserId);

            BookingResponse response = bookingService.createBooking(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{bookingId}/complete-payment")
    public ResponseEntity<?> completePayment(@PathVariable String bookingId, HttpServletRequest request) {
        String authUserId = (String) request.getAttribute("authUserId");
        if (authUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Thiếu thông tin xác thực"));
        }
        try {
            return ResponseEntity.ok(bookingService.completePayment(bookingId, authUserId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{bookingId}/cancel-holding")
    public ResponseEntity<?> cancelHolding(@PathVariable String bookingId, HttpServletRequest request) {
        String authUserId = (String) request.getAttribute("authUserId");
        if (authUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Thiếu thông tin xác thực"));
        }
        try {
            return ResponseEntity.ok(bookingService.cancelHolding(bookingId, authUserId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
