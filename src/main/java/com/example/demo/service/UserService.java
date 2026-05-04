package com.example.demo.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getById(String id) {
        User user = userRepository.findByKey(id);
        if (user == null) throw new RuntimeException("Không tìm thấy người dùng: " + id);
        return user;
    }

    public User register(Map<String, String> body) {
        String email = body.get("email");
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email đã được sử dụng: " + email);
        }
        User user = new User();
        String fullName = body.getOrDefault("full_name", body.getOrDefault("name", ""));
        user.setName(fullName);
        String requestedUsername = body.getOrDefault("username", "").trim();
        String baseUsername = requestedUsername.isBlank() ? buildUsernameFromEmail(email) : sanitizeUsername(requestedUsername);
        String uniqueUsername = ensureUniqueUsername(baseUsername);
        user.setUsername(uniqueUsername);
        user.setEmail(email);
        user.setPassword(body.get("password")); // production: hash password
        user.setPhone(body.getOrDefault("phone", ""));
        user.setTotalSpent(0);
        user.setMemberRank("user");
        user.setCreatedAt(LocalDateTime.now().toString());
        return userRepository.save(user);
    }

    public User login(Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        User user = userRepository.findByEmail(email);
        if (user == null || user.getPassword() == null || !user.getPassword().equals(password)) {
            throw new RuntimeException("Email hoặc mật khẩu không đúng");
        }
        user.setPassword(null); // không trả password về client
        return user;
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    /**
     * TRIGGER SIMULATION – Tự động cập nhật hạng thành viên.
     *
     * ============================================================
     * Đây mô phỏng hành vi AFTER UPDATE TRIGGER trong SQL Database.
     *
     * Trong ArangoDB, trigger thuần tuý có thể implement qua:
     *   • Foxx Microservices (JavaScript chạy server-side trong ArangoDB)
     *   • ArangoDB Enterprise Change Streams
     *
     * Ở đây chúng ta dùng application-level trigger – được gọi tự động
     * sau mỗi giao dịch đặt vé thành công từ BookingService.
     *
     * Quy tắc phân hạng thành viên:
     *   totalSpent >= 10.000.000 VND → 🏆 Premium
     *   totalSpent >=  5.000.000 VND → ⭐ VIP
     *   totalSpent  <  5.000.000 VND → 🎟  Normal
     * ============================================================
     */
    public void updateMemberRankTrigger(String userId) {
        User user = userRepository.findByKey(userId);
        if (user == null) return;
        if ("admin".equalsIgnoreCase(user.getMemberRank())) return;

        String newRank = userRepository.calculateRankByStoredProcedure(user.getTotalSpent());
        if (!newRank.equals(user.getMemberRank())) {
            userRepository.updateMemberRank(userId, newRank);
            log.info("[TRIGGER] User '{}' rank changed: {} → {} (totalSpent: {} VND)",
                    user.getName(), user.getMemberRank(), newRank, user.getTotalSpent());
        }
    }

    private String buildUsernameFromEmail(String email) {
        String localPart = email == null ? "user" : email.split("@")[0].trim();
        if (localPart.isBlank()) {
            localPart = "user";
        }
        return sanitizeUsername(localPart);
    }

    private String sanitizeUsername(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
    }

    private String ensureUniqueUsername(String base) {
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }
}
