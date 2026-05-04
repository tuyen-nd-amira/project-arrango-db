package com.example.demo.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDatabase;
import com.example.demo.model.Screening;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ScreeningRepository {

    private final ArangoDatabase db;
    private static final String COL = "screenings";

    // ---- Lấy tất cả suất chiếu của một phim ----
    public List<Screening> findByMovieId(String movieId) {
        String aql =
            "FOR s IN screenings " +
            "FILTER s.movie_key == @movieId AND s.status == 'active' " +
            "SORT s.start_time ASC " +
            "RETURN { " +
            "  _key: s._key, _id: s._id, " +
            "  movieId: s.movie_key, cinemaId: s.room_key, " +
            "  showTime: s.start_time, price: s.price, status: s.status " +
            "}";
        Map<String, Object> bind = new HashMap<>();
        bind.put("movieId", movieId);
        ArangoCursor<Screening> cursor = db.query(aql, bind, null, Screening.class);
        return cursor.asListRemaining();
    }

    public Screening findByKey(String key) {
        String aql =
            "FOR s IN screenings FILTER s._key == @key LIMIT 1 " +
            "RETURN { " +
            "  _key: s._key, _id: s._id, " +
            "  movieId: s.movie_key, cinemaId: s.room_key, " +
            "  showTime: s.start_time, price: s.price, status: s.status " +
            "}";
        Map<String, Object> bind = new HashMap<>();
        bind.put("key", key);
        ArangoCursor<Screening> cursor = db.query(aql, bind, null, Screening.class);
        List<Screening> result = cursor.asListRemaining();
        return result.isEmpty() ? null : result.get(0);
    }

    // ---- Dùng trong transaction ----
    public Screening findByKeyWithTx(String key, String txId) {
        String aql =
            "FOR s IN screenings FILTER s._key == @key LIMIT 1 " +
            "RETURN { " +
            "  _key: s._key, _id: s._id, " +
            "  movieId: s.movie_key, cinemaId: s.room_key, " +
            "  showTime: s.start_time, price: s.price, status: s.status " +
            "}";
        Map<String, Object> bind = new HashMap<>();
        bind.put("key", key);
        ArangoCursor<Screening> cursor = db.query(
                aql,
                bind,
                new com.arangodb.model.AqlQueryOptions().streamTransactionId(txId),
                Screening.class
        );
        List<Screening> result = cursor.asListRemaining();
        return result.isEmpty() ? null : result.get(0);
    }

    public Screening save(Screening screening) {
        String aql =
            "INSERT { " +
            "  movie_key: @movieKey, room_key: @roomKey, " +
            "  start_time: @startTime, price: @price, status: @status " +
            "} INTO screenings RETURN NEW";
        Map<String, Object> bind = new HashMap<>();
        bind.put("movieKey", screening.getMovieId());
        bind.put("roomKey", screening.getCinemaId());
        bind.put("startTime", screening.getShowTime());
        bind.put("price", screening.getPrice());
        bind.put("status", screening.getStatus());
        ArangoCursor<Screening> cursor = db.query(aql, bind, null, Screening.class);
        Screening saved = cursor.next();
        screening.setKey(saved.getKey());
        screening.setId(saved.getId());
        return screening;
    }

    public List<Screening> findAll() {
        String aql =
            "FOR s IN screenings SORT s.start_time ASC " +
            "RETURN { " +
            "  _key: s._key, _id: s._id, " +
            "  movieId: s.movie_key, cinemaId: s.room_key, " +
            "  showTime: s.start_time, price: s.price, status: s.status " +
            "}";
        ArangoCursor<Screening> cursor = db.query(aql, null, null, Screening.class);
        return cursor.asListRemaining();
    }
}
