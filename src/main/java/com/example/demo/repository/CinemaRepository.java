package com.example.demo.repository;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDatabase;
import com.example.demo.model.Cinema;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CinemaRepository {

    private final ArangoDatabase db;

    public List<Cinema> findAll() {
        String aql =
            "FOR r IN rooms SORT r.name ASC " +
            "RETURN { " +
            "  _key: r._key, _id: r._id, " +
            "  name: r.name, capacity: r.capacity, type: r.type " +
            "}";
        ArangoCursor<Cinema> cursor = db.query(aql, null, null, Cinema.class);
        return cursor.asListRemaining();
    }

    public Cinema findByKey(String key) {
        String aql =
            "FOR r IN rooms FILTER r._key == @key LIMIT 1 " +
            "RETURN { " +
            "  _key: r._key, _id: r._id, " +
            "  name: r.name, capacity: r.capacity, type: r.type " +
            "}";
        ArangoCursor<Cinema> cursor = db.query(aql, Map.of("key", key), null, Cinema.class);
        List<Cinema> result = cursor.asListRemaining();
        return result.isEmpty() ? null : result.get(0);
    }

    public Cinema save(Cinema cinema) {
        String aql =
            "INSERT { " +
            "  name: @name, capacity: @capacity, type: @type " +
            "} INTO rooms RETURN NEW";
        ArangoCursor<Cinema> cursor = db.query(
                aql,
                Map.of(
                        "name", cinema.getName(),
                "capacity", cinema.getCapacity(),
                "type", cinema.getType() == null ? "standard" : cinema.getType()
                ),
                null,
                Cinema.class
        );
        Cinema saved = cursor.next();
        cinema.setKey(saved.getKey());
        cinema.setId(saved.getId());
        return cinema;
    }
}
