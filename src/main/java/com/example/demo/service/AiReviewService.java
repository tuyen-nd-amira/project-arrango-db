package com.example.demo.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.model.Movie;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

        @Value("${gemini.api-key:}")
        private String geminiApiKey;

        @Value("${gemini.model:gemini-2.5-flash}")
        private String geminiModel;

        @Value("${gemini.base-url:https://api.shopaikey.com}")
        private String geminiBaseUrl;

    public Map<String, String> generateMovieReview(Movie movie) {
        if (movie == null) {
            return Map.of("review", "Không tìm thấy thông tin phim để tạo AI review.", "source", "fallback");
        }

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return Map.of(
                    "review", fallbackReview(movie),
                    "source", "fallback",
                "model", geminiModel,
                "note", "GEMINI_API_KEY is empty"
            );
        }

        try {
            String prompt = buildPrompt(movie);
            String review = fetchReviewFromGemini(prompt);
            if (isLikelyIncomplete(review)) {
                String retryPrompt = prompt + "\n\nYEU CAU BO SUNG BAT BUOC: Tra loi lai day du 3-5 cau, khong duoc bo do cau, cau cuoi phai ket thuc bang dau cham/cham than/cham hoi.";
                String retried = fetchReviewFromGemini(retryPrompt);
                if (!retried.isBlank()) {
                    review = retried;
                }
            }

            if (review.isBlank()) {
                return Map.of(
                        "review", fallbackReview(movie),
                        "source", "fallback",
                        "model", geminiModel,
                        "note", "ShopAIKey returned empty content"
                );
            }

            return Map.of("review", review, "source", "gemini", "model", geminiModel);

        } catch (Exception e) {
            return Map.of(
                    "review", fallbackReview(movie),
                    "source", "fallback",
                    "model", geminiModel,
                    "note", e.getMessage()
            );
        }
    }

    private String fetchReviewFromGemini(String prompt) throws Exception {
        String baseUrl = geminiBaseUrl == null ? "" : geminiBaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String url = baseUrl
                + "/v1beta/models/"
                + URLEncoder.encode(geminiModel, StandardCharsets.UTF_8)
                + ":generateContent";

        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 512);
        generationConfig.putObject("thinkingConfig").put("thinkingBudget", 0);

        root.putObject("systemInstruction")
                .putArray("parts")
                .addObject()
                .put("text", buildSystemInstruction());

        root.putArray("contents")
                .addObject()
                .putArray("parts")
                .addObject()
                .put("text", prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", geminiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String bodySnippet = response.body() == null ? "" : response.body();
            if (bodySnippet.length() > 200) {
                bodySnippet = bodySnippet.substring(0, 200) + "...";
            }
            throw new RuntimeException("ShopAIKey HTTP " + response.statusCode() + " - " + bodySnippet);
        }

        JsonNode json = objectMapper.readTree(response.body());
        JsonNode parts = json.path("candidates")
                .path(0)
                .path("content")
                .path("parts");

        List<String> segments = new ArrayList<>();
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                String text = part.path("text").asText("").trim();
                if (!text.isBlank()) {
                    segments.add(text);
                }
            }
        }

        return String.join("\n", segments).trim();
    }

    private boolean isLikelyIncomplete(String review) {
        if (review == null || review.isBlank()) {
            return true;
        }
        String trimmed = review.trim();
        char end = trimmed.charAt(trimmed.length() - 1);
        return end != '.' && end != '!' && end != '?';
    }

    private String buildSystemInstruction() {
        return "Bạn là một trợ lý AI chuyên về điện ảnh và review phim cho khán giả Việt Nam.";
    }

    private String buildPrompt(Movie movie) {
        String description = movie.getDescription() == null ? "" : movie.getDescription();
        if (description.length() > 500) {
            description = description.substring(0, 500);
        }

        return """
ROLE: Bạn là một người đam mê điện ảnh và một nhà phê bình phim có khả năng thuyết phục cực kỳ xuất sắc.
TASK: Khi tôi cung cấp tên một bộ phim là [Tên phim], hãy viết một đoạn review thật ngắn gọn, xúc tích và đầy cuốn hút để giới thiệu về phim đó. Kích thích sự tò mò tột độ và lôi kéo/thuyết phục người đọc phải đi tìm xem bộ phim này ngay lập tức.

REQUIREMENT:
1. Độ dài: Tuyệt đối ngắn gọn, chỉ từ 3 đến 5 câu (dưới 100 chữ). Không tóm tắt nội dung dài dòng.
2. Giọng điệu: Năng lượng cao, bí ẩn, lôi cuốn và chắc nịch. Sử dụng các tính từ mạnh, giàu hình ảnh và cảm xúc (ví dụ: bùng nổ, ám ảnh, mãn nhãn, cú twist lật ngửa ván bài, không thể rời mắt...).
3. Cấu trúc:
3.1. Mở đầu bằng một câu Hook (câu mồi) gây sốc hoặc đánh trúng tâm lý người xem.
3.2. Nêu bật 1-2 điểm ăn tiền nhất của phim (diễn xuất, kỹ xảo, cốt truyện, thông điệp) NHƯNG tuyệt đối KHÔNG tiết lộ nội dung quan trọng (No spoilers).
3.3. Chốt lại bằng một lời kêu gọi hành động (Call to action) thúc giục mạnh mẽ.
4. Bỏ hết các dấu * để in đậm hay in nghiêng.
5. Câu trả lời phải là đoạn hoàn chỉnh, không bị cụt; câu cuối bắt buộc kết thúc bằng dấu chấm, dấu chấm than hoặc dấu chấm hỏi.

Thông tin phim:
- Tên: %s
- Thời lượng: %d phút
- Mô tả: %s
""".formatted(safe(movie.getTitle()), movie.getDuration(), safe(description));
    }

    private String fallbackReview(Movie movie) {
        return "Phim " + safe(movie.getTitle())
                + " có thời lượng " + movie.getDuration() + " phút. "
                + "Nội dung có nhịp độ vừa phải, phù hợp cho buổi xem giải trí. "
                + "Nếu bạn thích thể loại này thì đây là lựa chọn đáng cân nhắc tại rạp.";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
