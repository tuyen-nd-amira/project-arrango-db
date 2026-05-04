package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningSeat {

    @JsonProperty("_key")
    private String key;

    @JsonProperty("_id")
    private String id;

    private String screeningId;
    private int row;
    private int col;
    private String seatLabel;   // e.g. "A1", "B3"
    private String status;      // "available" | "booked"
    private String bookingId;
}
