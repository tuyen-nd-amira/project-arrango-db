package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Movie {

    @JsonProperty("_key")
    private String key;

    @JsonProperty("_id")
    private String id;

    private String title;
    private String description;
    private String genre;
    private int duration;        // phút

    @JsonProperty("poster_url")
    private String posterUrl;

    private String imageUrl;
    private double basePrice;    // VND
    private String releaseDate;
    private String director;
    private String cast;
    private double rating;
    private String status;      // "active" | "inactive"

    public String getImageUrl() {
        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl;
        }
        return posterUrl;
    }
}
