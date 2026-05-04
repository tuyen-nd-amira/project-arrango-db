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
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + URLEncoder.encode(geminiModel, StandardCharsets.UTF_8)
                + ":generateContent?key="
                + URLEncoder.encode(geminiApiKey, StandardCharsets.UTF_8);

            String prompt = buildPrompt(movie);
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode generationConfig = root.putObject("generationConfig");
            generationConfig.put("temperature", 0.7);
            generationConfig.put("maxOutputTokens", 512);

            root.putArray("contents")
                .addObject()
                .putArray("parts")
                .addObject()
                .put("text", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String bodySnippet = response.body() == null ? "" : response.body();
                if (bodySnippet.length() > 200) {
                    bodySnippet = bodySnippet.substring(0, 200) + "...";
                }
                return Map.of(
                        "review", fallbackReview(movie),
                        "source", "fallback",
                        "model", geminiModel,
                        "note", "Gemini HTTP " + response.statusCode() + " - " + bodySnippet
                );
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

            String review = String.join("\n", segments).trim();

            if (review.isBlank()) {
                review = fallbackReview(movie);
                return Map.of(
                        "review", review,
                        "source", "fallback",
                        "model", geminiModel,
                        "note", "Gemini returned empty content"
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

    private String buildPrompt(Movie movie) {
        String description = movie.getDescription() == null ? "" : movie.getDescription();
        if (description.length() > 500) {
            description = description.substring(0, 500);
        }

        return "Bạn là nhà phê bình phim thân thiện với khán giả Việt Nam. "
                + "Hãy viết review ngắn 4-6 câu, dễ hiểu, trung lập, không spoiler quan trọng. "
                + "Kết thúc bằng khuyến nghị ngắn: nên xem ở rạp hay đợi online. "
                + "\n\nThông tin phim:"
                + "\n- Tên: " + safe(movie.getTitle())
                + "\n- Thời lượng: " + movie.getDuration() + " phút"
                + "\n- Mô tả: " + safe(description);
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
