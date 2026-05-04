package com.example.demo.dto;

import java.util.List;

import lombok.Data;

@Data
public class BookingRequest {
    private String userId;
    private String screeningId;
    private List<String> seatKeys;  // danh sách _key của ScreeningSeat đã chọn
}
