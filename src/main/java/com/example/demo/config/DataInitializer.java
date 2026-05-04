package com.example.demo.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.arangodb.ArangoDatabase;
import com.example.demo.model.Cinema;
import com.example.demo.model.Movie;
import com.example.demo.model.Screening;
import com.example.demo.repository.CinemaRepository;
import com.example.demo.repository.MovieRepository;
import com.example.demo.repository.ScreeningRepository;
import com.example.demo.service.ScreeningService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ArangoDatabase arangoDatabase;
    private final MovieRepository movieRepository;
    private final CinemaRepository cinemaRepository;
    private final ScreeningRepository screeningRepository;
    private final ScreeningService screeningService;

    @Value("${app.seed.mode:smart}")
    private String seedMode;

    @Value("${app.seed.reset-users:false}")
    private boolean resetUsers;

    @Override
    public void run(String... args) {
        if ("reset".equalsIgnoreCase(seedMode)) {
            resetMockCollections();
            log.info("app.seed.mode=reset: đã xoá dữ liệu cũ để seed lại dữ liệu mẫu.");
        }

        boolean seeded = false;

        List<Movie> movies = movieRepository.findAll();
        if (movies.isEmpty()) {
            int seededMovies = seedMoviesFromCsv();
            log.info("Đã seed {} phim từ CSV", seededMovies);
            movies = movieRepository.findAll();
            seeded = true;
        }

        List<Cinema> rooms = cinemaRepository.findAll();
        if (rooms.isEmpty()) {
            rooms = seedRooms();
            seeded = true;
        }

        boolean hasAnyScreenings = !screeningRepository.findAll().isEmpty();
        boolean hasBookableScreenings = hasBookableScreenings(movies);

        if (!hasAnyScreenings || !hasBookableScreenings) {
            seedScreenings(rooms);
            if (!hasBookableScreenings) {
                log.info("Đã tự sửa dữ liệu: tạo mới suất chiếu hợp lệ để có thể đặt vé.");
            }
            seeded = true;
        }

        if (seeded) {
            log.info("Khởi tạo dữ liệu mẫu hoàn tất.");
        } else {
            log.info("Database đã có dữ liệu, bỏ qua seeding.");
        }
    }

    private void resetMockCollections() {
        // Truncate theo thứ tự để tránh dữ liệu mồ côi trong graph edge collection.
        List<String> collections = new ArrayList<>(List.of(
                "booking_seats", "bookings", "screenings", "seats",
                "rooms", "movies", "audit_logs"
        ));
        if (resetUsers) {
            collections.add("users");
        }
        for (String name : collections) {
            if (arangoDatabase.collection(name).exists()) {
                arangoDatabase.collection(name).truncate();
            }
        }

        if (!resetUsers) {
            log.info("Giữ lại dữ liệu users khi reset mock data (app.seed.reset-users=false)");
        }
    }

    private int seedMoviesFromCsv() {
        Path csvPath = Path.of("movies_metadata_encoded.csv");
        if (!Files.exists(csvPath)) {
            log.warn("Không tìm thấy file CSV: {}. Dùng fallback dữ liệu nhỏ.", csvPath.toAbsolutePath());
            seedFallbackMovies();
            return 6;
        }

        List<Movie> movies = new ArrayList<>();
        Random rnd = new Random(2026);

        try (Reader reader = Files.newBufferedReader(csvPath);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord row : parser) {
                String title = readAny(row, "title");
                if (title.isBlank()) {
                    continue;
                }

                String genre = readAny(row, "genre");
                String poster = readAny(row, "poster_url");
                String sourceUrl = readAny(row, "url");
                String country = readAny(row, "country");

                int duration = 90 + rnd.nextInt(71);

                Movie m = movie(
                        title,
                        buildDescription(title, country, sourceUrl),
                        genre.isBlank() ? "Drama" : genre,
                        duration,
                        poster.isBlank() ? "https://picsum.photos/seed/" + slugify(title) + "/400/600" : poster
                );
                movies.add(m);
            }
        } catch (IOException e) {
            throw new RuntimeException("Không đọc được file movies_metadata_encoded.csv", e);
        }

        if (movies.isEmpty()) {
            seedFallbackMovies();
            return 6;
        }

        movies.forEach(movieRepository::save);
        return movies.size();
    }

    private List<Cinema> seedRooms() {
        List<Cinema> rooms = List.of(
                new Cinema(null, null, "Phòng Standard 1", 60, "standard"),
                new Cinema(null, null, "Phòng VIP", 20, "vip"),
                new Cinema(null, null, "Phòng IMAX 1", 80, "imax")
        );
        rooms.forEach(cinemaRepository::save);
        log.info("Đã seed {} phòng chiếu (rooms)", rooms.size());
        return cinemaRepository.findAll();
    }

    private void seedScreenings(List<Cinema> rooms) {
        List<Movie> movies = movieRepository.findAll();
        if (rooms.isEmpty() || movies.isEmpty()) {
            return;
        }

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        int[] slots = {0, 4, 8, 12};

        for (int i = 0; i < movies.size(); i++) {
            Movie movie = movies.get(i);
            for (int t = 0; t < 2; t++) {
                Cinema room = rooms.get((i + t) % rooms.size());
                Screening s = new Screening();
                s.setMovieId(movie.getKey());
                s.setCinemaId(room.getKey());
                s.setShowTime(start.plusHours(i + slots[t]).toString());
                s.setPrice(defaultPriceForRoom(room));
                s.setStatus("active");
                screeningService.create(s);
            }
        }
        log.info("Đã seed {} suất chiếu", movies.size() * 2);
    }

    private boolean hasBookableScreenings(List<Movie> movies) {
        if (movies == null || movies.isEmpty()) {
            return false;
        }

        int sampleSize = Math.min(10, movies.size());
        for (int i = 0; i < sampleSize; i++) {
            Movie movie = movies.get(i);
            if (movie.getKey() == null || movie.getKey().isBlank()) {
                continue;
            }
            if (!screeningRepository.findByMovieId(movie.getKey()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Movie movie(String title, String desc, String genre, int dur, String posterUrl) {
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setDescription(desc);
        movie.setGenre(genre);
        movie.setDuration(dur);
        movie.setPosterUrl(posterUrl);
        movie.setStatus("showing");
        return movie;
    }

    private void seedFallbackMovies() {
        List<Movie> movies = List.of(
                movie("Avengers: Endgame", "Mẫu dữ liệu fallback", "Action", 181, "https://picsum.photos/seed/avengers/400/600"),
                movie("Inception", "Mẫu dữ liệu fallback", "Sci-Fi", 148, "https://picsum.photos/seed/inception/400/600"),
                movie("The Lion King", "Mẫu dữ liệu fallback", "Animation", 118, "https://picsum.photos/seed/lionking/400/600"),
                movie("Titanic", "Mẫu dữ liệu fallback", "Romance", 195, "https://picsum.photos/seed/titanic/400/600"),
                movie("Spider-Man: No Way Home", "Mẫu dữ liệu fallback", "Action", 148, "https://picsum.photos/seed/spiderman/400/600"),
                movie("The Dark Knight", "Mẫu dữ liệu fallback", "Action", 152, "https://picsum.photos/seed/darkknight/400/600")
        );
        movies.forEach(movieRepository::save);
    }

    private double defaultPriceForRoom(Cinema room) {
        return switch ((room.getType() == null ? "" : room.getType()).toLowerCase()) {
            case "vip" -> 150_000;
            case "imax" -> 200_000;
            default -> 100_000;
        };
    }

    private String buildDescription(String title, String country, String sourceUrl) {
        String c = (country == null || country.isBlank()) ? "Nhiều quốc gia" : country;
        String source = (sourceUrl == null || sourceUrl.isBlank()) ? "" : " Nguồn: " + sourceUrl;
        return "Phim " + title + " - dữ liệu mô phỏng từ CSV, quốc gia: " + c + "." + source;
    }

    private String readAny(CSVRecord row, String key) {
        String bomKey = "\uFEFF" + key;
        if (row.isMapped(key)) {
            return row.get(key).trim();
        }
        if (row.isMapped(bomKey)) {
            return row.get(bomKey).trim();
        }
        return "";
    }

    private String slugify(String text) {
        if (text == null || text.isBlank()) {
            return "movie";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
