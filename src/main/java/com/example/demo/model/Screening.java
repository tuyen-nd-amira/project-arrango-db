package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Screening {

    @JsonProperty("_key")
    private String key;

    @JsonProperty("_id")
    private String id;

    private String movieId;
    private String cinemaId;
    private String showTime;    // ISO datetime, e.g. "2026-05-03T14:00:00"
    private double price;       // VND / vé
    private String status;      // "active" | "cancelled" | "completed"
}
