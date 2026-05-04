package com.example.demo.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.DocumentCreateEntity;
import com.arangodb.model.DocumentReadOptions;
import com.arangodb.model.DocumentUpdateOptions;
import com.example.demo.model.Movie;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MovieRepository {

    private final ArangoDatabase db;
    private static final String COL = "movies";

    // ---- SELECT ALL ----
    public List<Movie> findAll() {
        String aql =
            "FOR m IN movies " +
            "FILTER m.status == null OR m.status IN ['active', 'showing', 'coming_soon'] " +
            "SORT m.title ASC RETURN m";
        ArangoCursor<Movie> cursor = db.query(aql, null, null, Movie.class);
        return cursor.asListRemaining();
    }

    // ---- SELECT BY KEY ----
    public Movie findByKey(String key) {
        return db.collection(COL).getDocument(key, Movie.class);
    }

    // ---- SELECT BY KEY within a transaction ----
    public Movie findByKeyWithTx(String key, String txId) {
        return db.collection(COL).getDocument(key, Movie.class,
                new DocumentReadOptions().streamTransactionId(txId));
    }

    // ---- SELECT BY GENRE ----
    public List<Movie> findByGenre(String genre) {
        String aql =
            "FOR m IN movies " +
            "FILTER (m.status == null OR m.status IN ['active', 'showing', 'coming_soon']) " +
            "  AND CONTAINS(LOWER(m.genre), LOWER(@genre)) " +
            "SORT m.title ASC RETURN m";
        Map<String, Object> bind = new HashMap<>();
        bind.put("genre", genre);
        ArangoCursor<Movie> cursor = db.query(aql, bind, null, Movie.class);
        return cursor.asListRemaining();
    }

    // ---- SEARCH by title (LIKE %keyword%) ----
    public List<Movie> searchByTitle(String keyword) {
        String aql =
            "FOR m IN movies " +
            "FILTER (m.status == null OR m.status IN ['active', 'showing', 'coming_soon']) " +
            "  AND CONTAINS(LOWER(m.title), LOWER(@kw)) " +
            "SORT m.title ASC RETURN m";
        Map<String, Object> bind = new HashMap<>();
        bind.put("kw", keyword);
        ArangoCursor<Movie> cursor = db.query(aql, bind, null, Movie.class);
        return cursor.asListRemaining();
    }

    // ---- INSERT ----
    public Movie save(Movie movie) {
        if (movie.getStatus() == null || movie.getStatus().isBlank()) {
            movie.setStatus("showing");
        }
        if ((movie.getPosterUrl() == null || movie.getPosterUrl().isBlank()) && movie.getImageUrl() != null) {
            movie.setPosterUrl(movie.getImageUrl());
        }
        DocumentCreateEntity<Movie> result = db.collection(COL).insertDocument(movie);
        movie.setKey(result.getKey());
        movie.setId(result.getId());
        return movie;
    }

    // ---- UPDATE ----
    public void update(String key, Movie movie) {
        db.collection(COL).updateDocument(key, movie,
                new DocumentUpdateOptions().keepNull(false));
    }

    // ---- DELETE ----
    public void delete(String key) {
        db.collection(COL).deleteDocument(key);
    }
}
