package com.example.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.model.Cinema;
import com.example.demo.repository.CinemaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CinemaService {

    private final CinemaRepository cinemaRepository;

    public List<Cinema> getAll() {
        return cinemaRepository.findAll();
    }

    public Cinema getById(String id) {
        Cinema cinema = cinemaRepository.findByKey(id);
        if (cinema == null) throw new RuntimeException("Không tìm thấy rạp: " + id);
        return cinema;
    }

    public Cinema create(Cinema cinema) {
        return cinemaRepository.save(cinema);
    }
}
