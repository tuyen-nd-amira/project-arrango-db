package com.example.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.model.Movie;
import com.example.demo.repository.MovieRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;

    public List<Movie> getAll() {
        return movieRepository.findAll();
    }

    public Movie getById(String id) {
        Movie movie = movieRepository.findByKey(id);
        if (movie == null) throw new RuntimeException("Không tìm thấy phim: " + id);
        return movie;
    }

    public List<Movie> getByGenre(String genre) {
        return movieRepository.findByGenre(genre);
    }

    public List<Movie> search(String keyword) {
        return movieRepository.searchByTitle(keyword);
    }

    public Movie create(Movie movie) {
        return movieRepository.save(movie);
    }

    public Movie update(String id, Movie movie) {
        movieRepository.update(id, movie);
        return movieRepository.findByKey(id);
    }

    public void delete(String id) {
        movieRepository.delete(id);
    }
}
