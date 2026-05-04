package com.example.demo.dto;

import java.util.List;

import com.example.demo.model.Cinema;
import com.example.demo.model.Movie;
import com.example.demo.model.Screening;
import com.example.demo.model.ScreeningSeat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapResponse {
    private Screening screening;
    private Movie movie;
    private Cinema cinema;
    private List<ScreeningSeat> seats;
}
