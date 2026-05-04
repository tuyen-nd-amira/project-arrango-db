package com.example.demo.dto;

import com.example.demo.model.Booking;
import com.example.demo.model.User;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Booking booking;
    private User user;          // user sau khi cập nhật rank
    private String message;
}
