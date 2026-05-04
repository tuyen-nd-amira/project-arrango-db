package com.example.demo.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @JsonProperty("_key")
    private String key;

    @JsonProperty("_id")
    private String id;

    private String userId;
    private String screeningId;
    private String movieId;
    private String bookingCode;
    private List<String> seatKeys;
    private List<String> seatLabels;
    private double totalAmount;
    private String status;      // "confirmed" | "cancelled"
    private String createdAt;

    // Denormalized fields (snapshot at booking time)
    private String movieTitle;
    private String showTime;
    private String cinemaName;
}
