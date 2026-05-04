package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.Protocol;
import com.arangodb.entity.CollectionType;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.mapping.ArangoJack;
import com.arangodb.model.AqlFunctionCreateOptions;
import com.arangodb.model.CollectionCreateOptions;
import com.arangodb.model.GraphCreateOptions;
import com.arangodb.model.HashIndexOptions;
import com.arangodb.model.PersistentIndexOptions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ArangoConfig {

    @Value("${arangodb.hosts}")
    private String hosts;

    @Value("${arangodb.database}")
    private String database;

    @Value("${arangodb.user}")
    private String user;

    @Value("${arangodb.password}")
    private String password;

    // ----------------------------------------------------------------
    // Bean: ArangoDB connection (singleton driver instance)
    // ----------------------------------------------------------------
    @Bean
    public ArangoDB arangoDB() {
        String[] parts = hosts.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        return new ArangoDB.Builder()
                .host(host, port)
            .useProtocol(Protocol.HTTP_JSON)
                .user(user)
                .password(password)
                // Sử dụng Jackson làm serializer để dùng @JsonProperty trên models
                .serializer(new ArangoJack())
                .build();
    }

    // ----------------------------------------------------------------
    // Bean: ArangoDatabase – tạo DB + collections/indexes/graph/UDFs khi khởi động
    // ----------------------------------------------------------------
    @Bean
    public ArangoDatabase arangoDatabase(ArangoDB arangoDB) {
        ArangoDatabase db = arangoDB.db(database);

        if (!db.exists()) {
            db.create();
            log.info("Đã tạo database: {}", database);
        }

        initCollections(db);
        initIndexes(db);
        initGraph(db);

        // ============================================================
        // PROCEDURE SIMULATION:
        // Đăng ký AQL User-Defined Functions (UDFs) đóng vai trò như
        // Stored Procedures trong SQL. UDFs chạy server-side bên trong
        // ArangoDB engine và có thể gọi từ bất kỳ câu AQL nào.
        // ============================================================
        registerStoredProcedures(db);

        return db;
    }

    private void initCollections(ArangoDatabase db) {
        String[] cols = {"users", "movies", "rooms", "seats", "screenings", "bookings", "audit_logs"};
        for (String col : cols) {
            if (!db.collection(col).exists()) {
                db.createCollection(col, new CollectionCreateOptions().type(CollectionType.DOCUMENT));
                log.info("Đã tạo collection: {}", col);
            }
        }

        if (!db.collection("booking_seats").exists()) {
            db.createCollection("booking_seats", new CollectionCreateOptions().type(CollectionType.EDGES));
            log.info("Đã tạo edge collection: booking_seats");
        }
        }

        private void initIndexes(ArangoDatabase db) {
        db.collection("users").ensureHashIndex(
            java.util.List.of("email"),
            new HashIndexOptions().unique(true)
        );
        db.collection("users").ensureHashIndex(
            java.util.List.of("username"),
            new HashIndexOptions().unique(true)
        );

        db.collection("movies").ensureHashIndex(
            java.util.List.of("status"),
            new HashIndexOptions().unique(false)
        );

        db.collection("seats").ensurePersistentIndex(
            java.util.List.of("room_key", "seat_row", "seat_number"),
            new PersistentIndexOptions().unique(true)
        );

        db.collection("screenings").ensurePersistentIndex(
            java.util.List.of("movie_key", "start_time"),
            new PersistentIndexOptions().unique(false)
        );

        db.collection("bookings").ensurePersistentIndex(
            java.util.List.of("booking_code"),
            new PersistentIndexOptions().unique(true)
        );
        db.collection("bookings").ensureHashIndex(
            java.util.List.of("user_key"),
            new HashIndexOptions().unique(false)
        );
        db.collection("bookings").ensureHashIndex(
            java.util.List.of("screening_key"),
            new HashIndexOptions().unique(false)
        );
        db.collection("bookings").ensureHashIndex(
            java.util.List.of("status"),
            new HashIndexOptions().unique(false)
        );

        // Tránh đặt trùng ghế trong cùng 1 suất chiếu.
        db.collection("booking_seats").ensurePersistentIndex(
            java.util.List.of("_to", "screening_key"),
            new PersistentIndexOptions().unique(true)
        );
        }

        private void initGraph(ArangoDatabase db) {
        final String graphName = "cinema_graph";

        if (db.graph(graphName).exists()) {
            db.graph(graphName).drop();
            log.info("Đã xoá graph cũ: {}", graphName);
        }

        db.createGraph(
            graphName,
            java.util.List.of(new EdgeDefinition().collection("booking_seats").from("bookings").to("seats")),
            new GraphCreateOptions().orphanCollections("users", "movies", "rooms", "screenings", "audit_logs")
        );
        log.info("Đã tạo graph: {}", graphName);
    }

    /**
     * Đăng ký AQL User-Defined Functions như Stored Procedures.
     *
     * ArangoDB hỗ trợ UDFs thông qua JavaScript engine tích hợp.
     * Cú pháp: CINEMA::SP_MOVIE_STATS(movieId) có thể dùng trong AQL:
     *   FOR m IN movies RETURN CINEMA::SP_MOVIE_STATS(m._key)
     *
     * Tương đương CREATE PROCEDURE trong SQL.
     */
    private void registerStoredProcedures(ArangoDatabase db) {
        try {
            // Stored Procedure 1: SP_MOVIE_STATS – trả về doanh thu + số vé của 1 phim
            String spMovieStats =
                "function(movieId) {" +
                "  var db = require('@arangodb').db;" +
                "  var rows = db._query(" +
                "    'FOR b IN bookings FILTER b.movie_key == @mid AND b.status == \"confirmed\"" +
                "     RETURN { revenue: b.total_amount, tickets: LENGTH(b.seat_keys) }'," +
                "    { mid: movieId }" +
                "  ).toArray();" +
                "  return rows.reduce(function(acc, r) {" +
                "    return { totalRevenue: acc.totalRevenue + r.revenue, totalTickets: acc.totalTickets + r.tickets };" +
                "  }, { totalRevenue: 0, totalTickets: 0 });" +
                "}";

            db.createAqlFunction("CINEMA::SP_MOVIE_STATS", spMovieStats,
                    new AqlFunctionCreateOptions().isDeterministic(false));

            // Stored Procedure 2: SP_USER_RANK – trả về hạng thành viên dựa vào tổng chi tiêu
            String spUserRank =
                "function(totalSpent) {" +
                "  if (totalSpent >= 10000000) return 'premium';" +
                "  if (totalSpent >= 5000000)  return 'vip';" +
                "  return 'normal';" +
                "}";

            db.createAqlFunction("CINEMA::SP_USER_RANK", spUserRank,
                    new AqlFunctionCreateOptions().isDeterministic(true));

            // Stored Procedure 3: SP_SYSTEM_OVERVIEW – tổng quan hệ thống
            String spSystemOverview =
                "function() {" +
                "  var db = require('@arangodb').db;" +
                "  var totalRevenue = db._query('FOR b IN bookings FILTER b.status == \"confirmed\" COLLECT AGGREGATE s = SUM(b.total_amount) RETURN s').toArray()[0] || 0;" +
                "  var totalTickets = db._query('FOR b IN bookings FILTER b.status == \"confirmed\" COLLECT AGGREGATE s = SUM(LENGTH(b.seat_keys)) RETURN s').toArray()[0] || 0;" +
                "  var totalBookings = db._query('FOR b IN bookings FILTER b.status == \"confirmed\" COLLECT WITH COUNT INTO c RETURN c').toArray()[0] || 0;" +
                "  var totalUsers = db._query('FOR u IN users COLLECT WITH COUNT INTO c RETURN c').toArray()[0] || 0;" +
                "  return { totalRevenue: totalRevenue, totalTickets: totalTickets, totalBookings: totalBookings, totalUsers: totalUsers };" +
                "}";

            db.createAqlFunction("CINEMA::SP_SYSTEM_OVERVIEW", spSystemOverview,
                    new AqlFunctionCreateOptions().isDeterministic(false));

            log.info("Đã đăng ký AQL Stored Procedures (UDFs) thành công.");
        } catch (Exception e) {
            log.warn("Không thể đăng ký AQL UDFs (có thể đã tồn tại): {}", e.getMessage());
        }
    }
}
