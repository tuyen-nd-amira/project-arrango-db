package com.example.demo.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.dto.BookingRequest;
import com.example.demo.dto.BookingResponse;
import com.example.demo.model.Booking;
import com.example.demo.model.User;
import com.example.demo.repository.BookingRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Value("${arangodb.hosts}")
    private String arangoHosts;

    @Value("${arangodb.database}")
    private String arangoDatabase;

    @Value("${arangodb.user}")
    private String arangoUser;

    @Value("${arangodb.password}")
    private String arangoPassword;

    @Value("${arangodb.foxx.service-mount:/booking-tx}")
    private String foxxServiceMount;

    public List<Booking> getUserBookings(String userId) {
        return bookingRepository.findByUserId(userId);
    }

    public Booking getBookingByIdForUser(String bookingId, String userId) {
        Booking booking = bookingRepository.findByKey(bookingId);
        if (booking == null) {
            throw new RuntimeException("Không tìm thấy booking");
        }
        if (booking.getUserId() == null || !booking.getUserId().equals(userId)) {
            throw new RuntimeException("Không có quyền truy cập booking này");
        }
        return booking;
    }

    public BookingResponse createBooking(BookingRequest request) {
        validateCreateBookingRequest(request);

        String bookingCode = generateBookingCode();
        String createdAt = LocalDateTime.now().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", request.getUserId());
        payload.put("screeningId", request.getScreeningId());
        payload.put("seatKeys", request.getSeatKeys());
        payload.put("bookingCode", bookingCode);
        payload.put("createdAt", createdAt);

        try {
            BookingTxResult txResult = callBookingFoxxService(payload);
            if (txResult == null || txResult.booking == null) {
                throw new RuntimeException("Kết quả transaction không hợp lệ");
            }

            Booking saved = txResult.booking;
            log.info("[TRANSACTION] Booking created by server-side function – bookingId: {}", saved.getKey());

            return new BookingResponse(saved, null, "Giữ vé thành công! Vui lòng hoàn tất thanh toán trong 5 phút.");

        } catch (Exception e) {
            throw new RuntimeException("Đặt vé thất bại: " + rootMessage(e), e);
        }
    }

    public BookingResponse completePayment(String bookingId, String userId) {
        if (bookingId == null || bookingId.isBlank()) {
            throw new RuntimeException("Thiếu bookingId");
        }
        if (userId == null || userId.isBlank()) {
            throw new RuntimeException("Thiếu userId");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("bookingId", bookingId);
        payload.put("userId", userId);

        try {
            BookingTxResult txResult = callBookingFoxxService("/complete-payment", payload);
            if (txResult == null || txResult.booking == null) {
                throw new RuntimeException("Kết quả transaction không hợp lệ");
            }

            userService.updateMemberRankTrigger(userId);
            User updatedUser = userRepository.findByKey(userId);
            return new BookingResponse(txResult.booking, updatedUser, "Thanh toán thành công! Đặt vé đã được xác nhận.");
        } catch (Exception e) {
            throw new RuntimeException("Thanh toán thất bại: " + rootMessage(e), e);
        }
    }

    public BookingResponse cancelHolding(String bookingId, String userId) {
        if (bookingId == null || bookingId.isBlank()) {
            throw new RuntimeException("Thiếu bookingId");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("bookingId", bookingId);
        payload.put("userId", userId);
        try {
            BookingTxResult txResult = callBookingFoxxService("/cancel-holding", payload);
            if (txResult == null || txResult.booking == null) {
                throw new RuntimeException("Kết quả không hợp lệ");
            }
            return new BookingResponse(txResult.booking, null, "Đã hủy giữ vé thành công. Ghế đã được mở lại.");
        } catch (Exception e) {
            throw new RuntimeException("Hủy giữ vé thất bại: " + rootMessage(e), e);
        }
    }

    private BookingTxResult callBookingFoxxService(Map<String, Object> payload) throws Exception {
        return callBookingFoxxService("/create-booking", payload);
    }

    private BookingTxResult callBookingFoxxService(String path, Map<String, Object> payload) throws Exception {
        String endpoint = buildFoxxEndpoint(path);
        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", buildBasicAuthHeader())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return objectMapper.readValue(response.body(), BookingTxResult.class);
        }

        throw new RuntimeException("Foxx service error (" + statusCode + "): " + extractFoxxErrorMessage(response.body()));
    }

    private String buildFoxxEndpoint(String path) {
        String hostPort = arangoHosts.split(",")[0].trim();
        String mount = foxxServiceMount.startsWith("/") ? foxxServiceMount : "/" + foxxServiceMount;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return "http://" + hostPort + "/_db/" + arangoDatabase + mount + suffix;
    }

    private String buildBasicAuthHeader() {
        String raw = arangoUser + ":" + arangoPassword;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private String extractFoxxErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode messageNode = root.path("error");
            if (!messageNode.isMissingNode() && !messageNode.asText().isBlank()) {
                return messageNode.asText();
            }
            JsonNode altNode = root.path("errorMessage");
            if (!altNode.isMissingNode() && !altNode.asText().isBlank()) {
                return altNode.asText();
            }
        } catch (Exception ignored) {
            // Fall back to raw body below.
        }
        return responseBody == null || responseBody.isBlank() ? "Unknown error" : responseBody;
    }

    private void validateCreateBookingRequest(BookingRequest request) {
        if (request == null) {
            throw new RuntimeException("Dữ liệu đặt vé không hợp lệ");
        }
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new RuntimeException("Thiếu userId");
        }
        if (request.getScreeningId() == null || request.getScreeningId().isBlank()) {
            throw new RuntimeException("Thiếu screeningId");
        }
        if (request.getSeatKeys() == null || request.getSeatKeys().isEmpty()) {
            throw new RuntimeException("Cần chọn ít nhất 1 ghế");
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return (message == null || message.isBlank()) ? throwable.getMessage() : message;
    }

    private static class BookingTxResult {
        public Booking booking;
    }

    private String generateBookingCode() {
        long now = System.currentTimeMillis();
        int random = java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000);
        return "BK" + now + random;
    }
}
