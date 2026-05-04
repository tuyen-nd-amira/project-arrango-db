package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.dto.SeatMapResponse;
import com.example.demo.model.Cinema;
import com.example.demo.model.Movie;
import com.example.demo.model.Screening;
import com.example.demo.model.ScreeningSeat;
import com.example.demo.repository.CinemaRepository;
import com.example.demo.repository.MovieRepository;
import com.example.demo.repository.ScreeningRepository;
import com.example.demo.repository.ScreeningSeatRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScreeningService {

    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final CinemaRepository cinemaRepository;

    public List<Screening> getByMovieId(String movieId) {
        return screeningRepository.findByMovieId(movieId);
    }

    public Screening getById(String id) {
        Screening s = screeningRepository.findByKey(id);
        if (s == null) throw new RuntimeException("Không tìm thấy suất chiếu: " + id);
        return s;
    }

    // ---- Lấy sơ đồ ghế ngồi (screening + movie + cinema + seats) ----
    public SeatMapResponse getSeatMap(String screeningId) {
        Screening screening = getById(screeningId);
        Movie movie = movieRepository.findByKey(screening.getMovieId());
        Cinema cinema = cinemaRepository.findByKey(screening.getCinemaId());
        List<ScreeningSeat> seats = seatRepository.findByScreeningId(screeningId);
        return new SeatMapResponse(screening, movie, cinema, seats);
    }

    // ---- Tạo suất chiếu mới + khởi tạo ghế ngồi ----
    public Screening create(Screening screening) {
        if (screening.getStatus() == null || screening.getStatus().isBlank()) {
            screening.setStatus("active");
        }
        Screening saved = screeningRepository.save(screening);

        Cinema cinema = cinemaRepository.findByKey(screening.getCinemaId());
        if (cinema == null) throw new RuntimeException("Không tìm thấy rạp: " + screening.getCinemaId());

        // Schema mới: seats thuộc room, không tạo theo từng screening.
        if (seatRepository.countByRoom(cinema.getKey()) == 0) {
            List<ScreeningSeat> seats = new ArrayList<>();
            int totalRows = cinema.getTotalRows();
            int totalCols = cinema.getTotalCols();
            for (int row = 0; row < totalRows; row++) {
                for (int col = 0; col < totalCols; col++) {
                    ScreeningSeat seat = new ScreeningSeat();
                    seat.setScreeningId(cinema.getKey());
                    seat.setRow(row);
                    seat.setCol(col);
                    seat.setSeatLabel(String.valueOf((char) ('A' + row)) + (col + 1));
                    seats.add(seat);
                }
            }
            seatRepository.saveAll(seats);
        }
        return saved;
    }
}
