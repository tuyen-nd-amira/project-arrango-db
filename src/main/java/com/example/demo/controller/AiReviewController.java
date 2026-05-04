package com.example.demo.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.Movie;
import com.example.demo.service.AiReviewService;
import com.example.demo.service.MovieService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiReviewController {

    private final MovieService movieService;
    private final AiReviewService aiReviewService;

    @GetMapping("/review/{movieId}")
    public ResponseEntity<?> reviewMovie(@PathVariable String movieId) {
        try {
            Movie movie = movieService.getById(movieId);
            Map<String, String> result = aiReviewService.generateMovieReview(movie);
            return ResponseEntity.ok(Map.of(
                    "movieId", movieId,
                    "movieTitle", movie.getTitle(),
                    "review", result.getOrDefault("review", "Không có review"),
                    "source", result.getOrDefault("source", "fallback"),
                    "model", result.getOrDefault("model", "unknown")
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
