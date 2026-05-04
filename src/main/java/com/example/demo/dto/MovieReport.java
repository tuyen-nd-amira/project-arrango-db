package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieReport {
    private String movieId;
    private String movieTitle;
    private String genre;
    private double totalRevenue;
    private long totalTickets;
    private long totalBookings;
}
